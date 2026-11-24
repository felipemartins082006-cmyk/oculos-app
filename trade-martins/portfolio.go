package main

// Backtest de carteira: vários pares com o MESMO dinheiro, exatamente como o robô ao vivo opera.
//   - cada par tem sua posição, seus sinais, seu stop e seu stop móvel;
//   - em cada vela: primeiro as vendas (liberam dinheiro), depois as compras;
//   - cada compra usa no máximo patrimônio ÷ número de pares (× % máximo ou volatilidade-alvo),
//     limitado ao dinheiro livre e ao valor máximo por compra.
// Com um par só, dá exatamente o mesmo resultado que o backtest normal (há um teste para isso).

import (
	"math"
	"sort"
)

type PortSym struct {
	Symbol  string  `json:"symbol"`
	Trades  int     `json:"trades"`
	Wins    int     `json:"wins"`
	PnL     float64 `json:"pnl"`
	Missing bool    `json:"missing,omitempty"` // sem dados no período
}

type PortResult struct {
	Symbols     []string  `json:"symbols"`
	From        int64     `json:"from"`
	To          int64     `json:"to"`
	Candles     int       `json:"candles"`
	StartEquity float64   `json:"startEquity"`
	EndEquity   float64   `json:"endEquity"`
	ReturnPct   float64   `json:"returnPct"`
	FeesPaid    float64   `json:"feesPaid"`
	Metrics     Metrics   `json:"metrics"`
	Bench       Metrics   `json:"bench"`
	PerSymbol   []PortSym `json:"perSymbol"`
	Trades      []Trade   `json:"trades"`
	Equity      []Point   `json:"equity"`
	BenchEq     []Point   `json:"benchEq"`
	Drawdown    []Point   `json:"drawdown"`

	eq, low, bench []float64
	times          []int64
	tradeRets      []float64
}

type simSym struct {
	kl                                          []Kline
	pr                                          *Prepared
	first                                       int
	idx                                         map[int64]int
	in                                          bool
	qty, cost, stop, take, entry, high, eqEntry float64
	entryT                                      int64
	pending, pendingAt                          int
	lastC                                       float64
	inC, cand                                   int
	bQty                                        float64
	bOn                                         bool
}

func runPortfolio(syms []string, kls [][]Kline, p Params, o BTOptions) PortResult {
	n := len(syms)
	res := PortResult{Symbols: syms, StartEquity: o.Start, PerSymbol: make([]PortSym, n)}
	fee, slip := o.FeePct/100, o.StopSlipPct/100
	var dur = o.Interval
	for _, kl := range kls {
		if dur == 0 && len(kl) > 1 {
			dur = intervalOf(kl, o)
		}
	}
	if dur == 0 {
		dur = intervalOf(nil, o)
	}

	ss := make([]*simSym, n)
	tset := map[int64]bool{}
	prs := make([]*Prepared, n)
	for k, kl := range kls {
		prs[k] = p.prepare(kl, dur)
		prs[k].applyMarket(o.Market)
	}
	slots := float64(n)
	for k, kl := range kls {
		res.PerSymbol[k].Symbol = syms[k]
		s := &simSym{kl: kl, pr: prs[k], idx: make(map[int64]int, len(kl))}
		s.first = p.warmup(dur)
		for s.first < len(kl) && kl[s.first].OpenTime < o.TradeFrom {
			s.first++
		}
		for i := s.first; i < len(kl); i++ {
			s.idx[kl[i].OpenTime] = i
			tset[kl[i].OpenTime] = true
		}
		if s.first >= len(kl) {
			res.PerSymbol[k].Missing = true
		}
		ss[k] = s
	}
	timeline := make([]int64, 0, len(tset))
	for t := range tset {
		timeline = append(timeline, t)
	}
	sort.Slice(timeline, func(a, b int) bool { return timeline[a] < timeline[b] })
	if len(timeline) == 0 {
		return res
	}
	durMs := int64(dur.Milliseconds())
	res.From, res.To, res.Candles = timeline[0], timeline[len(timeline)-1]+durMs-1, len(timeline)

	cash, benchCash := o.Start, o.Start
	res.eq, res.low, res.bench, res.times = []float64{o.Start}, []float64{o.Start}, []float64{o.Start}, []int64{timeline[0]}
	benchLow := []float64{o.Start}

	sell := func(k int, s *simSym, t int64, price float64, reason string) {
		gross := s.qty * price
		f := gross * fee
		res.FeesPaid += f
		net := gross - f
		pnl := net - s.cost
		cash += net
		res.Trades = append(res.Trades, Trade{Time: t, Side: "VENDA", Symbol: syms[k], Price: price, Qty: s.qty, Quote: net, Reason: reason,
			PnL: pnl, PnLPct: pnl / s.cost * 100, EntryTime: s.entryT, EntryPrice: s.entry})
		res.tradeRets = append(res.tradeRets, pnl/s.eqEntry)
		ps := &res.PerSymbol[k]
		ps.Trades++
		ps.PnL += pnl
		if pnl > 0 {
			ps.Wins++
		}
		s.qty, s.cost, s.in = 0, 0, false
	}

	for _, t := range timeline {
		// candle de cada par neste instante (-1 = o par não tem vela agora)
		at := make([]int, n)
		for k, s := range ss {
			at[k] = -1
			if i, ok := s.idx[t]; ok {
				at[k] = i
			}
		}
		// "comprar e segurar": cada par recebe 1/n do dinheiro na primeira vela que ele tem
		for k, s := range ss {
			if at[k] >= 0 && !s.bOn {
				s.bOn = true
				slot := o.Start / float64(n)
				benchCash -= slot
				s.bQty = slot * (1 - fee) / s.kl[at[k]].O
			}
		}
		// 0) par que saiu da corretora (as velas acabaram antes do fim do teste): vende pelo último preço
		for k, s := range ss {
			if at[k] < 0 && s.in && len(s.kl) > 0 && t > s.kl[len(s.kl)-1].OpenTime {
				sell(k, s, t, s.lastC, "saiu da corretora")
			}
		}
		// 1) vendas pendentes na abertura
		for k, s := range ss {
			if at[k] >= 0 && s.pending == sigSell && s.in {
				sell(k, s, t, s.kl[at[k]].O, "sinal")
			}
			if at[k] >= 0 && s.pending == sigSell {
				s.pending = sigNone
			}
		}
		// 2) compras pendentes na abertura (patrimônio com os preços de agora)
		for k, s := range ss {
			if at[k] < 0 || s.pending != sigBuy {
				continue
			}
			s.pending = sigNone
			if s.in {
				continue
			}
			equity := cash
			for j, x := range ss {
				if x.in {
					px := x.lastC
					if at[j] >= 0 {
						px = x.kl[at[j]].O
					}
					equity += x.qty * px
				}
			}
			spend := math.Min(cash, equity/slots*s.pr.fraction(s.pendingAt, o.TradePct/100))
			if o.MaxBuy > 0 && spend > o.MaxBuy {
				spend = o.MaxBuy
			}
			if spend <= 1e-9 || (o.MinOrder > 0 && spend < o.MinOrder) {
				continue
			}
			k0 := s.kl[at[k]]
			f := spend * fee
			res.FeesPaid += f
			s.entry = k0.O
			s.qty = (spend - f) / s.entry
			s.cost = spend
			s.eqEntry = equity
			cash -= spend
			s.entryT = k0.OpenTime
			s.in = true
			s.stop, s.take, s.high = s.pr.initialStop(s.pendingAt, s.entry), p.take(s.entry), s.entry
			res.Trades = append(res.Trades, Trade{Time: k0.OpenTime, Side: "COMPRA", Symbol: syms[k], Price: s.entry, Qty: s.qty, Quote: spend, Reason: "sinal"})
		}
		// 3) stop / take dentro da vela, 4) fechamento: stop móvel e sinais
		for k, s := range ss {
			i := at[k]
			if i < 0 {
				continue
			}
			c := s.kl[i]
			if s.in {
				switch {
				case s.stop > 0 && c.L <= s.stop:
					sell(k, s, c.OpenTime, math.Min(s.stop, c.O)*(1-slip), "stop-loss")
				case s.take > 0 && c.H >= s.take:
					sell(k, s, c.OpenTime, math.Max(s.take, c.O), "take-profit")
				}
			}
			s.cand++
			if s.in {
				s.inC++
				s.high = math.Max(s.high, c.C)
				if ts := s.pr.trailStop(i, s.high); ts > s.stop {
					s.stop = ts
				}
			}
			if sg := s.pr.signal(i, s.in); sg != sigNone {
				s.pending, s.pendingAt = sg, i
			}
			s.lastC = c.C
		}
		// 5) patrimônio no fechamento (depois de todas as vendas da vela)
		eq, low, bv, bl := cash, cash, benchCash, benchCash
		for k, s := range ss {
			if i := at[k]; i >= 0 {
				eq += s.qty * s.kl[i].C
				low += s.qty * s.kl[i].L
				bv += s.bQty * s.kl[i].C
				bl += s.bQty * s.kl[i].L
			} else {
				eq += s.qty * s.lastC
				low += s.qty * s.lastC
				bv += s.bQty * s.lastC
				bl += s.bQty * s.lastC
			}
		}
		res.eq = append(res.eq, eq)
		res.low = append(res.low, low)
		res.bench = append(res.bench, bv)
		benchLow = append(benchLow, bl)
		res.times = append(res.times, t+durMs-1)
	}
	// fim: vende o que estiver comprado; o "segurar" também paga a taxa de venda
	held := 0.0
	for k, s := range ss {
		if s.in {
			last := s.kl[len(s.kl)-1]
			sell(k, s, last.CloseTime, last.C, "fim do teste")
		}
		held += s.bQty * s.lastC
	}
	res.eq[len(res.eq)-1] = cash
	res.bench[len(res.bench)-1] = benchCash + held*(1-fee)

	res.EndEquity = cash
	res.ReturnPct = (cash/o.Start - 1) * 100
	ppy := candlesPerDay(dur) * 365
	res.Metrics = calcMetrics(res.eq, res.low, res.times, ppy)
	res.Metrics.addTrades(res.Trades)
	inC, cand := 0, 0
	for _, s := range ss {
		inC += s.inC
		cand += s.cand
	}
	if cand > 0 {
		res.Metrics.ExposurePct = float64(inC) / float64(cand) * 100
	}
	res.Bench = calcMetrics(res.bench, benchLow, res.times, ppy)
	res.Bench.ExposurePct = 100
	tmp := BTResult{eq: res.eq, bench: res.bench, times: res.times, StartEquity: o.Start}
	tmp.sample()
	res.Equity, res.BenchEq, res.Drawdown = tmp.Equity, tmp.BenchEq, tmp.Drawdown
	return res
}
