package main

// Estratégia: cruzamento de médias móveis exponenciais (EMA) com filtro de RSI e de tendência.
//
//   COMPRA quando a EMA rápida cruza para CIMA da EMA lenta,
//          o RSI está abaixo do limite (não compra no "esticado") e,
//          se o filtro de tendência estiver ligado, o preço está acima da EMA longa.
//   VENDE  quando a EMA rápida cruza para BAIXO da lenta, ou no stop-loss / take-profit.
//
// Só opera comprado (Spot): ou está em moeda (USDT) ou está na cripto.

import (
	"math"
	"time"
)

type Params struct {
	FastEMA       int     `json:"fastEMA"`
	SlowEMA       int     `json:"slowEMA"`
	TrendEMA      int     `json:"trendEMA"` // 0 = sem filtro de tendência
	RSIPeriod     int     `json:"rsiPeriod"`
	RSIMaxBuy     float64 `json:"rsiMaxBuy"`     // não compra com RSI acima disso
	StopLossPct   float64 `json:"stopLossPct"`   // 0 = sem stop
	TakeProfitPct float64 `json:"takeProfitPct"` // 0 = sem alvo
}

type Indicators struct {
	Fast, Slow, Trend, RSI []float64
}

func ema(v []float64, n int) []float64 {
	out := make([]float64, len(v))
	for i := range out {
		out[i] = math.NaN()
	}
	if n <= 0 || len(v) < n {
		return out
	}
	sum := 0.0
	for i := 0; i < n; i++ {
		sum += v[i]
	}
	out[n-1] = sum / float64(n)
	k := 2.0 / float64(n+1)
	for i := n; i < len(v); i++ {
		out[i] = v[i]*k + out[i-1]*(1-k)
	}
	return out
}

// rsi de Wilder.
func rsi(v []float64, n int) []float64 {
	out := make([]float64, len(v))
	for i := range out {
		out[i] = math.NaN()
	}
	if n <= 0 || len(v) <= n {
		return out
	}
	var gain, loss float64
	for i := 1; i <= n; i++ {
		d := v[i] - v[i-1]
		if d > 0 {
			gain += d
		} else {
			loss -= d
		}
	}
	gain /= float64(n)
	loss /= float64(n)
	val := func() float64 {
		if loss == 0 {
			return 100
		}
		return 100 - 100/(1+gain/loss)
	}
	out[n] = val()
	for i := n + 1; i < len(v); i++ {
		d := v[i] - v[i-1]
		g, l := 0.0, 0.0
		if d > 0 {
			g = d
		} else {
			l = -d
		}
		gain = (gain*float64(n-1) + g) / float64(n)
		loss = (loss*float64(n-1) + l) / float64(n)
		out[i] = val()
	}
	return out
}

func compute(closes []float64, p Params) Indicators {
	ind := Indicators{Fast: ema(closes, p.FastEMA), Slow: ema(closes, p.SlowEMA), RSI: rsi(closes, p.RSIPeriod)}
	if p.TrendEMA > 0 {
		ind.Trend = ema(closes, p.TrendEMA)
	}
	return ind
}

// warmup = quantas velas são necessárias antes do primeiro sinal válido.
func warmup(p Params) int {
	w := p.SlowEMA
	if p.FastEMA > w {
		w = p.FastEMA
	}
	if p.TrendEMA > w {
		w = p.TrendEMA
	}
	if p.RSIPeriod+1 > w {
		w = p.RSIPeriod + 1
	}
	return w + 1
}

const (
	sigNone = 0
	sigBuy  = 1
	sigSell = -1
)

// signalAt avalia o fechamento da vela i.
func signalAt(ind Indicators, closes []float64, i int, p Params) int {
	if i < 1 || i >= len(closes) {
		return sigNone
	}
	f0, s0, f1, s1 := ind.Fast[i-1], ind.Slow[i-1], ind.Fast[i], ind.Slow[i]
	if math.IsNaN(f0) || math.IsNaN(s0) || math.IsNaN(f1) || math.IsNaN(s1) {
		return sigNone
	}
	if f0 >= s0 && f1 < s1 {
		return sigSell
	}
	if f0 <= s0 && f1 > s1 {
		r := ind.RSI[i]
		if math.IsNaN(r) || r >= p.RSIMaxBuy {
			return sigNone
		}
		if p.TrendEMA > 0 {
			t := ind.Trend[i]
			if math.IsNaN(t) || closes[i] <= t {
				return sigNone
			}
		}
		return sigBuy
	}
	return sigNone
}

func (p Params) stops(entry float64) (stop, take float64) {
	if p.StopLossPct > 0 {
		stop = entry * (1 - p.StopLossPct/100)
	}
	if p.TakeProfitPct > 0 {
		take = entry * (1 + p.TakeProfitPct/100)
	}
	return
}

// ---------- Backtest ----------

type Point struct {
	T int64   `json:"t"`
	V float64 `json:"v"`
}

type BTResult struct {
	Symbol      string  `json:"symbol"`
	Interval    string  `json:"interval"`
	From        int64   `json:"from"`
	To          int64   `json:"to"`
	Candles     int     `json:"candles"`
	StartEquity float64 `json:"startEquity"`
	EndEquity   float64 `json:"endEquity"`
	ReturnPct   float64 `json:"returnPct"`
	BuyHoldPct  float64 `json:"buyHoldPct"`
	MaxDDPct    float64 `json:"maxDDPct"`
	WinRate     float64 `json:"winRate"`
	Wins        int     `json:"wins"`
	Losses      int     `json:"losses"`
	FeesPaid    float64 `json:"feesPaid"`
	Trades      []Trade `json:"trades"`
	Equity      []Point `json:"equity"`
	Price       []Point `json:"price"`
}

// BTOptions = como simular o dinheiro no backtest.
type BTOptions struct {
	TradePct    float64 // % do saldo por compra
	MaxBuy      float64 // valor máximo por compra (0 = sem limite), igual ao robô
	FeePct      float64 // taxa por lado (0,1% na Binance)
	Start       float64 // saldo inicial
	StopSlipPct float64 // quanto abaixo do stop a venda sai, em média (a ordem de stop na Binance vende até 0,5% abaixo)
	TradeFrom   int64   // só opera a partir desta data; as velas antes servem só para "aquecer" as médias
}

const backtestStopSlipPct = 0.25

// runBacktest simula a estratégia sobre velas fechadas.
// O sinal aparece no fechamento da vela i e a ordem sai na abertura da vela i+1 (como no robô ao vivo).
func runBacktest(kl []Kline, p Params, o BTOptions) BTResult {
	res := BTResult{StartEquity: o.Start}
	closes := make([]float64, len(kl))
	for i, k := range kl {
		closes[i] = k.C
	}
	ind := compute(closes, p)
	fee := o.FeePct / 100
	slip := o.StopSlipPct / 100

	// primeira vela em que pode operar: depois do aquecimento das médias e a partir de TradeFrom
	first := warmup(p)
	for first < len(kl) && kl[first].OpenTime < o.TradeFrom {
		first++
	}
	if first >= len(kl) {
		return res
	}
	res.From, res.To, res.Candles = kl[first].OpenTime, kl[len(kl)-1].CloseTime, len(kl)-first

	cash := o.Start
	var qty, cost, stop, take, entry float64
	var entryT int64
	in := false
	pending := sigNone
	peak, maxDD := o.Start, 0.0

	sell := func(t int64, price float64, reason string) {
		gross := qty * price
		f := gross * fee
		res.FeesPaid += f
		net := gross - f
		pnl := net - cost
		cash += net
		res.Trades = append(res.Trades, Trade{Time: t, Side: "VENDA", Price: price, Qty: qty, Quote: net, Reason: reason,
			PnL: pnl, PnLPct: pnl / cost * 100, EntryTime: entryT, EntryPrice: entry})
		if pnl > 0 {
			res.Wins++
		} else {
			res.Losses++
		}
		qty, cost, in = 0, 0, false
	}

	step := res.Candles/600 + 1
	for i := first; i < len(kl); i++ {
		k := kl[i]
		// 1) executa o sinal da vela anterior na abertura desta.
		switch {
		case pending == sigBuy && !in:
			spend := cash * o.TradePct / 100
			if o.MaxBuy > 0 && spend > o.MaxBuy {
				spend = o.MaxBuy
			}
			if spend > 0 {
				f := spend * fee
				res.FeesPaid += f
				entry = k.O
				qty = (spend - f) / entry
				cost = spend
				cash -= spend
				entryT = k.OpenTime
				in = true
				stop, take = p.stops(entry)
				res.Trades = append(res.Trades, Trade{Time: k.OpenTime, Side: "COMPRA", Price: entry, Qty: qty, Quote: spend, Reason: "sinal"})
			}
		case pending == sigSell && in:
			sell(k.OpenTime, k.O, "sinal")
		}
		pending = sigNone

		// 2) stop-loss / take-profit dentro da vela (se os dois forem tocados, conta o stop: visão conservadora).
		//    O stop vende um pouco abaixo do preço de stop (derrapagem), e abaixo da abertura se a vela já abriu caindo.
		if in {
			switch {
			case stop > 0 && k.L <= stop:
				sell(k.OpenTime, math.Min(stop, k.O)*(1-slip), "stop-loss")
			case take > 0 && k.H >= take:
				sell(k.OpenTime, math.Max(take, k.O), "take-profit")
			}
		}

		// 3) sinal no fechamento.
		s := signalAt(ind, closes, i, p)
		if (s == sigBuy && !in) || (s == sigSell && in) {
			pending = s
		}

		// maior queda: o pico é medido no fechamento e o fundo na mínima da vela
		if eq := cash + qty*k.C; eq > peak {
			peak = eq
		}
		if dd := (peak - (cash + qty*k.L)) / peak * 100; dd > maxDD {
			maxDD = dd
		}
		if (i-first)%step == 0 || i == len(kl)-1 {
			res.Equity = append(res.Equity, Point{k.CloseTime, cash + qty*k.C})
			res.Price = append(res.Price, Point{k.CloseTime, k.C})
		}
	}
	if in {
		last := kl[len(kl)-1]
		sell(last.CloseTime, last.C, "fim do teste")
	}
	res.EndEquity = cash
	res.ReturnPct = (cash/o.Start - 1) * 100
	res.BuyHoldPct = (kl[len(kl)-1].C/kl[first].O*(1-fee)*(1-fee) - 1) * 100
	res.MaxDDPct = maxDD
	if n := res.Wins + res.Losses; n > 0 {
		res.WinRate = float64(res.Wins) / float64(n) * 100
	}
	return res
}

func dayKey(t time.Time) string { return t.Format("2006-01-02") }
