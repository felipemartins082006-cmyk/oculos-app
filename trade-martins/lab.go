package main

// Laboratório quantitativo: testes que tentam mostrar se a estratégia funciona de verdade ou se o
// resultado do backtest foi sorte / ajuste demais aos dados ("overfitting").
//
//   walk-forward  otimiza os parâmetros num pedaço do passado (treino) e mede o resultado no pedaço
//                 seguinte, que o otimizador não viu (teste). Repete andando no tempo. Só o resultado
//                 "fora da amostra" vale como estimativa honesta.
//   mapa          resultado para uma grade de parâmetros: estratégia robusta = região larga boa, não um pico.
//   monte carlo   sorteia a ordem/repetição das operações milhares de vezes: faixa de resultados e quedas
//                 possíveis só por sorte da sequência.
//   vários pares  a mesma estratégia em várias criptos, e numa carteira com peso igual entre elas.

import (
	"fmt"
	"math"
	"math/rand/v2"
	"sort"
	"strings"
	"sync"
	"time"
)

// ---------- cache de histórico (para não baixar tudo de novo a cada teste) ----------

type histEntry struct {
	ks   []Kline
	days int
	at   time.Time
}

var histCache = struct {
	sync.Mutex
	m map[string]histEntry
}{m: map[string]histEntry{}}

func (b *Bot) history(symbol, interval string, days, maxCandles int) ([]Kline, error) {
	key := symbol + "|" + interval
	histCache.Lock()
	e, ok := histCache.m[key]
	histCache.Unlock()
	if !ok || e.days < days || time.Since(e.at) > 15*time.Minute || len(e.ks) > maxCandles*2 {
		ks, err := b.data.History(symbol, interval, days, maxCandles)
		if err != nil {
			return nil, err
		}
		e = histEntry{ks: ks, days: days, at: time.Now()}
		histCache.Lock()
		histCache.m[key] = e
		histCache.Unlock()
	}
	from := time.Now().Add(-time.Duration(days) * 24 * time.Hour).UnixMilli()
	i := sort.Search(len(e.ks), func(i int) bool { return e.ks[i].OpenTime >= from })
	out := e.ks[i:]
	if len(out) > maxCandles {
		out = out[len(out)-maxCandles:]
	}
	return out, nil
}

// ---------- grades de parâmetros ----------

func with(base Params, f func(p *Params)) Params {
	p := base
	f(&p)
	return p
}

// gridFor = combinações testadas pelo walk-forward (os ajustes de risco ficam como estão).
func gridFor(base Params, dur time.Duration) []Params {
	var g []Params
	add := func(p Params) {
		if p.validate(dur, false) == nil {
			g = append(g, p)
		}
	}
	switch base.Strategy {
	case "ema":
		for _, f := range []int{5, 9, 12, 20} {
			for _, s := range []int{21, 30, 50, 100} {
				for _, t := range []int{0, 200} {
					add(with(base, func(p *Params) { p.FastEMA, p.SlowEMA, p.TrendEMA = f, s, t }))
				}
			}
		}
	case "donchian":
		for _, n := range []int{10, 20, 30, 55, 80} {
			for _, m := range []int{5, 10, 20, 30} {
				for _, t := range []int{0, 200} {
					if m < n {
						add(with(base, func(p *Params) { p.BreakoutN, p.ExitN, p.TrendEMA = n, m, t }))
					}
				}
			}
		}
	case "tsmom":
		for _, d := range []int{7, 14, 30, 60, 90, 120, 180} {
			add(with(base, func(p *Params) { p.MomDays = d }))
		}
	case "rsi2":
		for _, lo := range []float64{5, 10, 15, 25} {
			for _, hi := range []float64{50, 65, 80} {
				for _, t := range []int{0, 200} {
					add(with(base, func(p *Params) { p.MRBuyBelow, p.MRSellAbove, p.TrendEMA = lo, hi, t }))
				}
			}
		}
	}
	return g
}

func maxWarmup(ps []Params, dur time.Duration) int {
	w := 0
	for _, p := range ps {
		if x := p.warmup(dur); x > w {
			w = x
		}
	}
	return w
}

// paramLabel = resumo curto dos parâmetros da estratégia (para as tabelas).
func paramLabel(p Params) string {
	t := ""
	if p.TrendEMA > 0 && p.Strategy != "tsmom" {
		t = fmt.Sprintf(", tendência %d", p.TrendEMA)
	}
	switch p.Strategy {
	case "ema":
		return fmt.Sprintf("EMA %d/%d%s", p.FastEMA, p.SlowEMA, t)
	case "donchian":
		return fmt.Sprintf("rompe %d, sai %d%s", p.BreakoutN, p.ExitN, t)
	case "tsmom":
		return fmt.Sprintf("%d dias", p.MomDays)
	case "rsi2":
		return fmt.Sprintf("RSI%d <%.0f / >%.0f%s", p.MRRSI, p.MRBuyBelow, p.MRSellAbove, t)
	}
	return p.Strategy
}

func objectiveScore(m Metrics, objective string, minTrades int) float64 {
	if m.Trades < minTrades {
		return math.Inf(-1)
	}
	switch objective {
	case "calmar":
		return m.Calmar
	case "retorno":
		return m.ReturnPct
	}
	return m.Sharpe
}

func indexAt(ks []Kline, t int64) int {
	return sort.Search(len(ks), func(i int) bool { return ks[i].OpenTime >= t })
}

// ---------- walk-forward ----------

type WFFold struct {
	TrainFrom int64   `json:"trainFrom"`
	TestFrom  int64   `json:"testFrom"`
	TestTo    int64   `json:"testTo"`
	Chosen    string  `json:"chosen"`
	ISSharpe  float64 `json:"isSharpe"`
	ISReturn  float64 `json:"isReturn"`
	OOSSharpe float64 `json:"oosSharpe"`
	OOSReturn float64 `json:"oosReturn"`
	BenchRet  float64 `json:"benchReturn"`
	Trades    int     `json:"trades"`
}

type WFResult struct {
	Folds        []WFFold `json:"folds"`
	OOS          Metrics  `json:"oos"`
	Bench        Metrics  `json:"bench"`
	ISSharpeAvg  float64  `json:"isSharpeAvg"`
	OOSSharpeAvg float64  `json:"oosSharpeAvg"`
	Combos       int      `json:"combos"`
	Equity       []Point  `json:"equity"`
	BenchEq      []Point  `json:"benchEq"`
	Drawdown     []Point  `json:"drawdown"`
}

func walkForward(ks []Kline, base Params, o BTOptions, trainDays, testDays int, objective string) (*WFResult, error) {
	dur := intervalOf(ks, o)
	grid := gridFor(base, dur)
	if len(grid) == 0 {
		return nil, fmt.Errorf("nenhuma combinação de parâmetros válida para testar")
	}
	w := maxWarmup(grid, dur)
	if len(ks) <= w {
		return nil, fmt.Errorf("poucos dados")
	}
	day := int64(24 * time.Hour / time.Millisecond)
	start := ks[w].OpenTime
	if o.TradeFrom > start {
		start = o.TradeFrom
	}
	end := ks[len(ks)-1].CloseTime

	res := &WFResult{Combos: len(grid)}
	equity := o.Start
	eq, low, times := []float64{equity}, []float64{equity}, []int64{}
	var trades []Trade
	var isSum, oosSum float64
	inCandles, candles := 0, 0

	for trainFrom := start; ; trainFrom += int64(testDays) * day {
		testFrom := trainFrom + int64(trainDays)*day
		testTo := testFrom + int64(testDays)*day
		if testFrom >= end || (testTo > end && end-testFrom < int64(testDays)*day/3) {
			break // sobra pequena demais para um teste justo
		}
		if testTo > end {
			testTo = end
		}
		trainEnd := indexAt(ks, testFrom) // treino usa só velas ANTES do teste
		testEnd := indexAt(ks, testTo)

		best, bestScore := grid[0], math.Inf(-1)
		var bestM Metrics
		for _, cand := range grid {
			r := runBacktest(ks[:trainEnd], cand, BTOptions{TradePct: o.TradePct, MaxBuy: 0, FeePct: o.FeePct,
				Start: 1000, StopSlipPct: o.StopSlipPct, TradeFrom: trainFrom, Interval: dur})
			if s := objectiveScore(r.Metrics, objective, 3); s > bestScore {
				best, bestScore, bestM = cand, s, r.Metrics
			}
		}
		if math.IsInf(bestScore, -1) {
			// nenhuma combinação operou o suficiente no treino: usa a configuração atual
			best = base
		}

		r := runBacktest(ks[:testEnd], best, BTOptions{TradePct: o.TradePct, MaxBuy: o.MaxBuy, FeePct: o.FeePct,
			Start: equity, StopSlipPct: o.StopSlipPct, TradeFrom: testFrom, Interval: dur})
		if len(r.eq) < 2 {
			break
		}
		if len(times) == 0 {
			times = append(times, r.times[0])
		}
		eq = append(eq, r.eq[1:]...)
		low = append(low, r.low[1:]...)
		times = append(times, r.times[1:]...)
		trades = append(trades, r.Trades...)
		inCandles += r.inCandles
		candles += r.Candles
		equity = r.EndEquity

		f := WFFold{TrainFrom: trainFrom, TestFrom: testFrom, TestTo: testTo, Chosen: paramLabel(best),
			ISSharpe: bestM.Sharpe, ISReturn: bestM.ReturnPct, OOSSharpe: r.Metrics.Sharpe, OOSReturn: r.ReturnPct,
			BenchRet: r.BuyHoldPct, Trades: r.Metrics.Trades}
		res.Folds = append(res.Folds, f)
		isSum += f.ISSharpe
		oosSum += f.OOSSharpe
		if testTo >= end {
			break
		}
	}
	if len(res.Folds) == 0 {
		return nil, fmt.Errorf("período curto demais: precisa de pelo menos %d dias de treino + %d de teste", trainDays, testDays)
	}
	n := float64(len(res.Folds))
	res.ISSharpeAvg, res.OOSSharpeAvg = fin(isSum/n), fin(oosSum/n)
	ppy := candlesPerDay(dur) * 365
	res.OOS = calcMetrics(eq, low, times, ppy)
	res.OOS.addTrades(trades)
	if candles > 0 {
		res.OOS.ExposurePct = float64(inCandles) / float64(candles) * 100
	}

	// comprar e segurar no mesmo período fora da amostra
	i0 := indexAt(ks, res.Folds[0].TestFrom)
	qty := o.Start * (1 - o.FeePct/100) / ks[i0].O
	bench, blow, btimes := []float64{o.Start}, []float64{o.Start}, []int64{ks[i0].OpenTime}
	for i := i0; i < len(ks) && ks[i].OpenTime < res.Folds[len(res.Folds)-1].TestTo; i++ {
		bench = append(bench, qty*ks[i].C)
		blow = append(blow, qty*ks[i].L)
		btimes = append(btimes, ks[i].CloseTime)
	}
	bench[len(bench)-1] *= 1 - o.FeePct/100
	res.Bench = calcMetrics(bench, blow, btimes, ppy)
	res.Bench.ExposurePct = 100

	tmp := BTResult{eq: eq, bench: resample(bench, len(eq)), times: times, StartEquity: o.Start}
	tmp.sample()
	res.Equity, res.BenchEq, res.Drawdown = tmp.Equity, tmp.BenchEq, tmp.Drawdown
	return res, nil
}

// resample ajusta uma série para n pontos (para desenhar duas curvas com tamanhos um pouco diferentes).
func resample(v []float64, n int) []float64 {
	if len(v) == n || len(v) == 0 {
		return v
	}
	out := make([]float64, n)
	for i := range out {
		j := int(float64(i) * float64(len(v)-1) / float64(max(n-1, 1)))
		out[i] = v[j]
	}
	return out
}

// ---------- mapa de parâmetros ----------

type HeatCell struct {
	Sharpe    float64 `json:"sharpe"`
	ReturnPct float64 `json:"returnPct"`
	MaxDDPct  float64 `json:"maxDDPct"`
	Trades    int     `json:"trades"`
}

type Heatmap struct {
	XName   string        `json:"xName"`
	YName   string        `json:"yName"`
	XVals   []float64     `json:"xVals"`
	YVals   []float64     `json:"yVals"`
	Cells   [][]*HeatCell `json:"cells"` // [y][x]; nil = combinação inválida
	CurX    float64       `json:"curX"`
	CurY    float64       `json:"curY"`
	BenchSR float64       `json:"benchSharpe"`
}

func heatmapAxes(p Params) (xn, yn string, xs, ys []float64, set func(*Params, float64, float64), cur [2]float64) {
	switch p.Strategy {
	case "ema":
		return "EMA rápida", "EMA lenta", []float64{5, 7, 9, 12, 15, 20}, []float64{21, 30, 40, 50, 75, 100},
			func(q *Params, x, y float64) { q.FastEMA, q.SlowEMA = int(x), int(y) }, [2]float64{float64(p.FastEMA), float64(p.SlowEMA)}
	case "donchian":
		return "Rompe (velas)", "Sai (velas)", []float64{10, 15, 20, 30, 40, 55, 80}, []float64{5, 10, 15, 20, 30},
			func(q *Params, x, y float64) { q.BreakoutN, q.ExitN = int(x), int(y) }, [2]float64{float64(p.BreakoutN), float64(p.ExitN)}
	case "rsi2":
		return "Compra abaixo de", "Vende acima de", []float64{5, 10, 15, 20, 25, 30}, []float64{50, 60, 70, 80, 90},
			func(q *Params, x, y float64) { q.MRBuyBelow, q.MRSellAbove = x, y }, [2]float64{p.MRBuyBelow, p.MRSellAbove}
	}
	// tsmom: período × stop
	xs = []float64{7, 14, 21, 30, 45, 60, 90, 120, 180}
	if p.StopMode == "atr" {
		return "Momentum (dias)", "Stop (× ATR)", xs, []float64{1.5, 2, 3, 4, 6},
			func(q *Params, x, y float64) { q.MomDays, q.ATRMult = int(x), y }, [2]float64{float64(p.MomDays), p.ATRMult}
	}
	return "Momentum (dias)", "Stop (%)", xs, []float64{0, 4, 6, 8, 12, 16},
		func(q *Params, x, y float64) { q.MomDays, q.StopLossPct = int(x), y }, [2]float64{float64(p.MomDays), p.StopLossPct}
}

func heatmap(ks []Kline, base Params, o BTOptions) *Heatmap {
	dur := intervalOf(ks, o)
	xn, yn, xs, ys, set, cur := heatmapAxes(base)
	h := &Heatmap{XName: xn, YName: yn, XVals: xs, YVals: ys, CurX: cur[0], CurY: cur[1]}
	h.Cells = make([][]*HeatCell, len(ys))
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)
	for yi, y := range ys {
		h.Cells[yi] = make([]*HeatCell, len(xs))
		for xi, x := range xs {
			p := base
			set(&p, x, y)
			if p.validate(dur, false) != nil {
				continue
			}
			wg.Add(1)
			sem <- struct{}{}
			go func(yi, xi int, p Params) {
				defer wg.Done()
				defer func() { <-sem }()
				r := runBacktest(ks, p, o)
				h.Cells[yi][xi] = &HeatCell{Sharpe: r.Metrics.Sharpe, ReturnPct: fin(r.ReturnPct), MaxDDPct: r.Metrics.MaxDDPct, Trades: r.Metrics.Trades}
				if yi == 0 && xi == 0 {
					h.BenchSR = r.Bench.Sharpe
				}
			}(yi, xi, p)
		}
	}
	wg.Wait()
	if h.BenchSR == 0 {
		h.BenchSR = runBacktest(ks, base, o).Bench.Sharpe
	}
	return h
}

// ---------- Monte Carlo ----------

type MCResult struct {
	Runs        int       `json:"runs"`
	Trades      int       `json:"trades"`
	RetP        []float64 `json:"retP"` // percentis 5, 25, 50, 75, 95 do retorno final
	DDP         []float64 `json:"ddP"`  // percentis 5, 25, 50, 75, 95 da maior queda
	ProbLoss    float64   `json:"probLoss"`
	ProbDD30    float64   `json:"probDD30"` // chance de uma queda de 30% ou mais
	Hist        []int     `json:"hist"`     // histograma do retorno final
	HistFrom    float64   `json:"histFrom"`
	HistStep    float64   `json:"histStep"`
	OriginalRet float64   `json:"originalRet"`
	OriginalDD  float64   `json:"originalDD"`
}

// monteCarlo sorteia, com reposição, a sequência de operações (bootstrap). Semente fixa: o mesmo teste dá
// sempre o mesmo resultado.
func monteCarlo(r BTResult, runs int) (*MCResult, error) {
	n := len(r.tradeRets)
	if n < 5 {
		return nil, fmt.Errorf("poucas operações (%d) para um Monte Carlo útil; use um período maior", n)
	}
	rng := rand.New(rand.NewPCG(1, 2))
	rets, dds := make([]float64, runs), make([]float64, runs)
	for k := 0; k < runs; k++ {
		eq, peak, dd := 1.0, 1.0, 0.0
		for j := 0; j < n; j++ {
			eq *= 1 + r.tradeRets[rng.IntN(n)]
			peak = math.Max(peak, eq)
			dd = math.Max(dd, (peak-eq)/peak)
		}
		rets[k], dds[k] = (eq-1)*100, dd*100
	}
	sort.Float64s(rets)
	sort.Float64s(dds)
	pct := func(v []float64, q float64) float64 { return v[int(q*float64(len(v)-1))] }
	res := &MCResult{Runs: runs, Trades: n, OriginalRet: r.ReturnPct, OriginalDD: r.Metrics.MaxDDPct}
	for _, q := range []float64{0.05, 0.25, 0.5, 0.75, 0.95} {
		res.RetP = append(res.RetP, fin(pct(rets, q)))
		res.DDP = append(res.DDP, fin(pct(dds, q)))
	}
	for k := range rets {
		if rets[k] < 0 {
			res.ProbLoss++
		}
		if dds[k] >= 30 {
			res.ProbDD30++
		}
	}
	res.ProbLoss = res.ProbLoss / float64(runs) * 100
	res.ProbDD30 = res.ProbDD30 / float64(runs) * 100
	// histograma entre os percentis 1 e 99
	lo, hi := pct(rets, 0.01), pct(rets, 0.99)
	bins := 24
	res.HistFrom, res.HistStep = fin(lo), fin((hi-lo)/float64(bins))
	res.Hist = make([]int, bins)
	for _, v := range rets {
		if res.HistStep <= 0 {
			break
		}
		b := int((v - lo) / res.HistStep)
		if b >= 0 && b < bins {
			res.Hist[b]++
		}
	}
	return res, nil
}

// ---------- vários pares ----------

type MultiRow struct {
	Symbol string  `json:"symbol"`
	M      Metrics `json:"m"`
	Bench  Metrics `json:"bench"`
	Error  string  `json:"error,omitempty"`
}

type MultiResult struct {
	Rows       []MultiRow `json:"rows"`
	Portfolio  Metrics    `json:"portfolio"`
	PortBench  Metrics    `json:"portBench"`
	PortAssets int        `json:"portAssets"`
	Equity     []Point    `json:"equity"`
	BenchEq    []Point    `json:"benchEq"`
	Drawdown   []Point    `json:"drawdown"`
}

func (b *Bot) multi(symbols []string, interval string, days, extraDays, maxCandles int, p Params, o BTOptions) *MultiResult {
	res := &MultiResult{Rows: make([]MultiRow, len(symbols))}
	runs := make([]*BTResult, len(symbols))
	var wg sync.WaitGroup
	sem := make(chan struct{}, 3) // no máximo 3 downloads ao mesmo tempo
	for i, s := range symbols {
		wg.Add(1)
		go func(i int, s string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			res.Rows[i].Symbol = s
			ks, err := b.history(s, interval, days+extraDays, maxCandles)
			if err != nil {
				res.Rows[i].Error = err.Error()
				return
			}
			r := runBacktest(ks, p, o)
			if r.Candles < 10 {
				res.Rows[i].Error = "poucos dados (par novo?)"
				return
			}
			res.Rows[i].M, res.Rows[i].Bench = r.Metrics, r.Bench
			runs[i] = &r
		}(i, s)
	}
	wg.Wait()

	// carteira com peso igual: em cada vela, o retorno médio dos pares (só nas datas que todos têm)
	var ok []*BTResult
	for _, r := range runs {
		if r != nil {
			ok = append(ok, r)
		}
	}
	res.PortAssets = len(ok)
	if len(ok) < 2 {
		return res
	}
	idx := make([]map[int64]int, len(ok))
	for k, r := range ok {
		idx[k] = make(map[int64]int, len(r.times))
		for i, t := range r.times {
			idx[k][t] = i
		}
	}
	var common []int64
	for _, t := range ok[0].times {
		all := true
		for k := 1; k < len(ok); k++ {
			if _, has := idx[k][t]; !has {
				all = false
				break
			}
		}
		if all {
			common = append(common, t)
		}
	}
	if len(common) < 3 {
		return res
	}
	eq, bench := []float64{o.Start}, []float64{o.Start}
	for j := 1; j < len(common); j++ {
		var rs, rb float64
		for k, r := range ok {
			i0, i1 := idx[k][common[j-1]], idx[k][common[j]]
			rs += r.eq[i1]/r.eq[i0] - 1
			rb += r.bench[i1]/r.bench[i0] - 1
		}
		eq = append(eq, eq[len(eq)-1]*(1+rs/float64(len(ok))))
		bench = append(bench, bench[len(bench)-1]*(1+rb/float64(len(ok))))
	}
	res.Portfolio = calcMetrics(eq, nil, common, ok[0].ppy)
	res.PortBench = calcMetrics(bench, nil, common, ok[0].ppy)
	res.PortBench.ExposurePct = 100
	var trades []Trade
	inC, allC := 0, 0
	for _, r := range ok {
		trades = append(trades, r.Trades...)
		inC += r.inCandles
		allC += r.Candles
	}
	res.Portfolio.addTrades(trades)
	if allC > 0 {
		res.Portfolio.ExposurePct = float64(inC) / float64(allC) * 100
	}
	tmp := BTResult{eq: eq, bench: bench, times: common, StartEquity: o.Start}
	tmp.sample()
	res.Equity, res.BenchEq, res.Drawdown = tmp.Equity, tmp.BenchEq, tmp.Drawdown
	return res
}

func parseSymbols(s []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, x := range s {
		for _, y := range strings.FieldsFunc(strings.ToUpper(x), func(r rune) bool { return r == ',' || r == ' ' || r == ';' || r == '\n' }) {
			if !seen[y] && len(out) < 12 {
				seen[y] = true
				out = append(out, y)
			}
		}
	}
	return out
}
