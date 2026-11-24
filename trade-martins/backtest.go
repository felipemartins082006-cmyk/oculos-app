package main

// Backtest: simula a estratégia sobre velas fechadas, com as mesmas regras do robô ao vivo.
//   - o sinal aparece no fechamento da vela i e a ordem sai na abertura da vela i+1;
//   - taxa em cada compra e venda;
//   - stop e take-profit conferidos na máxima/mínima de cada vela (se os dois forem tocados, conta o stop);
//   - o stop vende um pouco abaixo do preço de stop (derrapagem), e abaixo da abertura se a vela abriu caindo;
//   - o stop móvel sobe no fechamento de cada vela e vale a partir da vela seguinte.

import (
	"math"
	"time"
)

type Point struct {
	T int64   `json:"t"`
	V float64 `json:"v"`
}

// BTOptions = como simular o dinheiro.
type BTOptions struct {
	TradePct    float64       // % máximo do saldo por compra
	MaxBuy      float64       // valor máximo por compra (0 = sem limite), igual ao robô
	MinOrder    float64       // compra mínima aceita pela corretora (0 = sem mínimo); abaixo disso não compra
	FeePct      float64       // taxa por lado (0,1% na Binance)
	Start       float64       // saldo inicial
	StopSlipPct float64       // quanto abaixo do stop a venda sai, em média
	TradeFrom   int64         // só opera a partir desta data; as velas antes servem para "aquecer" os indicadores
	Interval    time.Duration // tamanho da vela (0 = descobre pelas velas)
	Market      []Kline       // velas DIÁRIAS fechadas do BTCUSDT, para o filtro de mercado
}

const backtestStopSlipPct = 0.25

type BTResult struct {
	Symbol      string  `json:"symbol"`
	Interval    string  `json:"interval"`
	Strategy    string  `json:"strategy"`
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
	Metrics     Metrics `json:"metrics"`
	Bench       Metrics `json:"bench"` // comprar e segurar no mesmo período
	Trades      []Trade `json:"trades"`
	Equity      []Point `json:"equity"`
	BenchEq     []Point `json:"benchEq"`
	Drawdown    []Point `json:"drawdown"`
	Price       []Point `json:"price"`

	// séries completas (uma por vela, começando pelo saldo inicial) para o laboratório
	eq, low, bench []float64
	times          []int64
	tradeRets      []float64 // resultado de cada operação sobre o patrimônio total (para o Monte Carlo)
	ppy            float64
	inCandles      int // velas em que estava comprado
}

func intervalOf(kl []Kline, o BTOptions) time.Duration {
	if o.Interval > 0 {
		return o.Interval
	}
	if len(kl) > 1 {
		if d := time.Duration(kl[1].OpenTime-kl[0].OpenTime) * time.Millisecond; d > 0 {
			return d
		}
	}
	return time.Hour
}

func runBacktest(kl []Kline, p Params, o BTOptions) BTResult {
	res := BTResult{StartEquity: o.Start, Strategy: p.Strategy}
	dur := intervalOf(kl, o)
	pr := p.prepare(kl, dur)
	pr.applyMarket(o.Market)
	res.ppy = pr.ppy
	fee := o.FeePct / 100
	slip := o.StopSlipPct / 100

	first := p.warmup(dur)
	for first < len(kl) && kl[first].OpenTime < o.TradeFrom {
		first++
	}
	if first >= len(kl) {
		return res
	}
	res.From, res.To, res.Candles = kl[first].OpenTime, kl[len(kl)-1].CloseTime, len(kl)-first

	cash := o.Start
	var qty, cost, stop, take, entry, highest, eqBefore float64
	var entryT int64
	in := false
	pending, pendingAt := sigNone, 0
	inCandles := 0

	sell := func(t int64, price float64, reason string) {
		gross := qty * price
		f := gross * fee
		res.FeesPaid += f
		net := gross - f
		pnl := net - cost
		cash += net
		res.Trades = append(res.Trades, Trade{Time: t, Side: "VENDA", Price: price, Qty: qty, Quote: net, Reason: reason,
			PnL: pnl, PnLPct: pnl / cost * 100, EntryTime: entryT, EntryPrice: entry})
		res.tradeRets = append(res.tradeRets, pnl/eqBefore)
		if pnl > 0 {
			res.Wins++
		} else {
			res.Losses++
		}
		qty, cost, in = 0, 0, false
	}

	// o primeiro ponto é o saldo inicial, na abertura da primeira vela
	o0 := kl[first].O
	res.eq, res.low, res.bench, res.times = []float64{o.Start}, []float64{o.Start}, []float64{o.Start}, []int64{kl[first].OpenTime}
	benchQty := o.Start * (1 - fee) / o0
	var benchLow []float64 = []float64{o.Start}

	for i := first; i < len(kl); i++ {
		k := kl[i]
		// 1) executa o sinal da vela anterior na abertura desta.
		switch {
		case pending == sigBuy && !in:
			spend := cash * pr.fraction(pendingAt, o.TradePct/100)
			if o.MaxBuy > 0 && spend > o.MaxBuy {
				spend = o.MaxBuy
			}
			if spend > 1e-9 && (o.MinOrder <= 0 || spend >= o.MinOrder) {
				f := spend * fee
				res.FeesPaid += f
				entry = k.O
				qty = (spend - f) / entry
				cost = spend
				eqBefore = cash
				cash -= spend
				entryT = k.OpenTime
				in = true
				stop, take, highest = pr.initialStop(pendingAt, entry), p.take(entry), entry
				res.Trades = append(res.Trades, Trade{Time: k.OpenTime, Side: "COMPRA", Price: entry, Qty: qty, Quote: spend, Reason: "sinal"})
			}
		case pending == sigSell && in:
			sell(k.OpenTime, k.O, "sinal")
		}
		pending = sigNone

		// 2) stop-loss / take-profit dentro da vela.
		if in {
			switch {
			case stop > 0 && k.L <= stop:
				sell(k.OpenTime, math.Min(stop, k.O)*(1-slip), "stop-loss")
			case take > 0 && k.H >= take:
				sell(k.OpenTime, math.Max(take, k.O), "take-profit")
			}
		}

		// 3) no fechamento: stop móvel sobe, e o sinal da vela.
		if in {
			inCandles++
			highest = math.Max(highest, k.C)
			if ts := pr.trailStop(i, highest); ts > stop {
				stop = ts
			}
		}
		if s := pr.signal(i, in); s != sigNone {
			pending, pendingAt = s, i
		}

		res.eq = append(res.eq, cash+qty*k.C)
		res.low = append(res.low, cash+qty*k.L)
		res.bench = append(res.bench, benchQty*k.C)
		benchLow = append(benchLow, benchQty*k.L)
		res.times = append(res.times, k.CloseTime)
	}
	if in {
		last := kl[len(kl)-1]
		sell(last.CloseTime, last.C, "fim do teste")
		res.eq[len(res.eq)-1] = cash
	}
	res.bench[len(res.bench)-1] *= 1 - fee // venda final do "comprar e segurar"

	res.EndEquity = cash
	res.ReturnPct = (cash/o.Start - 1) * 100
	res.BuyHoldPct = (res.bench[len(res.bench)-1]/o.Start - 1) * 100
	res.Metrics = calcMetrics(res.eq, res.low, res.times, pr.ppy)
	res.Metrics.addTrades(res.Trades)
	res.inCandles = inCandles
	res.Metrics.ExposurePct = fin(float64(inCandles) / float64(res.Candles) * 100)
	res.Bench = calcMetrics(res.bench, benchLow, res.times, pr.ppy)
	res.Bench.ExposurePct = 100
	res.MaxDDPct = res.Metrics.MaxDDPct
	if n := res.Wins + res.Losses; n > 0 {
		res.WinRate = float64(res.Wins) / float64(n) * 100
	}
	res.sample()
	return res
}

// sample monta as séries resumidas (até ~600 pontos) que vão para o gráfico.
func (r *BTResult) sample() {
	n := len(r.eq)
	step := n/600 + 1
	r.Equity, r.BenchEq, r.Drawdown, r.Price = nil, nil, nil, nil
	peak := 0.0
	for i := 0; i < n; i++ {
		peak = math.Max(peak, r.eq[i])
		if i%step == 0 || i == n-1 {
			r.Equity = append(r.Equity, Point{r.times[i], r.eq[i]})
			r.BenchEq = append(r.BenchEq, Point{r.times[i], r.bench[i]})
			r.Drawdown = append(r.Drawdown, Point{r.times[i], fin(-(peak - r.eq[i]) / peak * 100)})
			if r.StartEquity > 0 {
				r.Price = append(r.Price, Point{r.times[i], r.bench[i]})
			}
		}
	}
}
