package main

import (
	"encoding/json"
	"math"
	"math/rand/v2"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"
)

// randomWalk = velas de 1h com tendências e ruído (determinístico).
func randomWalk(n int, seed uint64) []Kline {
	rng := rand.New(rand.NewPCG(seed, seed+1))
	ks := make([]Kline, n)
	p := 100.0
	drift := 0.0
	for i := range ks {
		if i%200 == 0 {
			drift = (rng.Float64() - 0.45) * 0.004
		}
		o := p
		p *= 1 + drift + rng.NormFloat64()*0.01
		h := math.Max(o, p) * (1 + rng.Float64()*0.004)
		l := math.Min(o, p) * (1 - rng.Float64()*0.004)
		ks[i] = Kline{OpenTime: int64(i) * 3600000, CloseTime: int64(i+1)*3600000 - 1, O: o, H: h, L: l, C: p}
	}
	return ks
}

func allStrategies() []Params {
	var out []Params
	for _, s := range []string{"ema", "donchian", "tsmom", "rsi2"} {
		p := defaultParams()
		p.Strategy = s
		p.MomDays = 5
		out = append(out, p)
	}
	return out
}

// A regra de ouro: o sinal no fechamento da vela i não pode depender de velas depois de i.
func TestNoLookAhead(t *testing.T) {
	ks := randomWalk(1500, 7)
	for _, base := range allStrategies() {
		for _, risk := range []func(*Params){func(p *Params) {}, func(p *Params) { p.StopMode, p.SizeMode, p.Trailing = "atr", "vol", true }} {
			p := base
			risk(&p)
			full := p.prepare(ks, time.Hour)
			for _, i := range []int{300, 600, 901, 1200, 1498} {
				cut := p.prepare(ks[:i+1], time.Hour)
				for _, in := range []bool{false, true} {
					if full.signal(i, in) != cut.signal(i, in) {
						t.Fatalf("%s: o sinal na vela %d muda quando existem velas futuras", p.Strategy, i)
					}
				}
				if full.fraction(i, 1) != cut.fraction(i, 1) || full.initialStop(i, 100) != cut.initialStop(i, 100) || full.trailStop(i, 120) != cut.trailStop(i, 120) {
					t.Fatalf("%s: tamanho/stop na vela %d usam o futuro", p.Strategy, i)
				}
			}
		}
	}
}

func TestIndicators(t *testing.T) {
	// velas com amplitude constante de 2 e sem gaps: ATR = 2
	ks := make([]Kline, 30)
	for i := range ks {
		ks[i] = Kline{O: 100, H: 101, L: 99, C: 100}
	}
	if a := atr(ks, 14); math.Abs(a[29]-2) > 1e-9 || !math.IsNaN(a[13]) {
		t.Fatalf("ATR errado: %v", a[29])
	}
	// canal de Donchian usa só velas anteriores
	ks[20].H = 150
	if hi := prevHigh(ks, 5); hi[20] != 101 || hi[21] != 150 || hi[26] != 101 {
		t.Fatalf("máxima anterior errada: %v %v %v", hi[20], hi[21], hi[26])
	}
	// volatilidade: retornos que alternam +1% e −1%
	c := []float64{100}
	for i := 0; i < 60; i++ {
		c = append(c, c[len(c)-1]*map[bool]float64{true: 1.01, false: 1 / 1.01}[i%2 == 0])
	}
	v := volatility(c, 20, 365)
	want := math.Log(1.01) * math.Sqrt(20.0/19) * math.Sqrt(365)
	if math.Abs(v[60]-want) > 1e-9 {
		t.Fatalf("volatilidade %v, quer %v", v[60], want)
	}
	if m := momentum([]float64{100, 110, 121}, 2); math.Abs(m[2]-0.21) > 1e-12 {
		t.Fatalf("momentum %v", m[2])
	}
}

func TestStrategySignals(t *testing.T) {
	flat := func(n int) []Kline {
		ks := make([]Kline, n)
		for i := range ks {
			ks[i] = Kline{OpenTime: int64(i) * 3600000, O: 100, H: 101, L: 99, C: 100}
		}
		return ks
	}
	// rompimento: fecha acima da máxima das 20 velas anteriores → compra; abaixo da mínima de 10 → vende
	p := defaultParams()
	p.Strategy, p.TrendEMA = "donchian", 0
	ks := flat(40)
	ks[30].C, ks[30].H = 102, 102
	pr := p.prepare(ks, time.Hour)
	if pr.signal(30, false) != sigBuy || pr.signal(29, false) != sigNone {
		t.Fatalf("rompimento não comprou")
	}
	ks[35].C, ks[35].L = 98, 98
	pr = p.prepare(ks, time.Hour)
	if pr.signal(35, true) != sigSell {
		t.Fatalf("rompimento não vendeu")
	}
	// momentum de 1 dia (24 velas de 1h): acima do preço de 24 velas atrás → comprado
	p = defaultParams()
	p.Strategy, p.MomDays = "tsmom", 1
	ks = flat(60)
	ks[50].C = 105
	pr = p.prepare(ks, time.Hour)
	if pr.signal(50, false) != sigBuy || pr.signal(49, true) != sigSell {
		t.Fatalf("momentum: sinais errados")
	}
	// reversão: queda forte e curta → RSI(2) baixo → compra; recuperação → vende
	p = defaultParams()
	p.Strategy, p.TrendEMA = "rsi2", 0
	ks = flat(40)
	ks[30].C, ks[31].C = 97, 94
	ks[32].C, ks[33].C = 99, 103
	pr = p.prepare(ks, time.Hour)
	if pr.signal(31, false) != sigBuy || pr.signal(33, true) != sigSell {
		t.Fatalf("reversão: compra %d / venda %d (RSI %v %v)", pr.signal(31, false), pr.signal(33, true), pr.mr[31], pr.mr[33])
	}
}

func TestVolTargetSizing(t *testing.T) {
	p := defaultParams()
	p.SizeMode, p.TargetVol, p.VolDays = "vol", 40, 1
	pr := &Prepared{p: p, vol: []float64{0.8, 0.2, math.NaN()}}
	if f := pr.fraction(0, 0.95); math.Abs(f-0.5) > 1e-12 {
		t.Fatalf("vol 80%% e alvo 40%% deveria usar metade: %v", f)
	}
	if f := pr.fraction(1, 0.95); f != 0.95 {
		t.Fatalf("vol baixa: limitado ao máximo, veio %v", f)
	}
	if f := pr.fraction(2, 0.95); f != 0.95 {
		t.Fatalf("sem dado de vol: usa o máximo, veio %v", f)
	}
}

func TestTrailingStopLocksProfit(t *testing.T) {
	// sobe de 100 a 130 e depois cai: com stop móvel de 5%, sai perto de 130×0,95, com lucro
	ks := series([][2]float64{{40, -0.5}, {5, 1}, {25, 1}, {30, -2}})
	p := defaultParams()
	p.FastEMA, p.SlowEMA, p.TrendEMA, p.RSIMaxBuy = 3, 8, 0, 101
	p.StopLossPct, p.Trailing = 5, true
	r := runBacktest(ks, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	var exit *Trade
	for i := range r.Trades {
		if r.Trades[i].Side == "VENDA" {
			exit = &r.Trades[i]
			break
		}
	}
	if exit == nil || exit.Reason != "stop-loss" || exit.PnL <= 0 {
		t.Fatalf("o stop móvel deveria ter saído com lucro: %+v", r.Trades)
	}
	high := 0.0
	for _, k := range ks {
		high = math.Max(high, k.C)
	}
	if want := high * 0.95; math.Abs(exit.Price-want)/want > 0.03 {
		t.Fatalf("saída a %v, esperava perto de %v", exit.Price, want)
	}
	// sem stop móvel, o mesmo stop de 5% fica lá embaixo e a saída é pelo sinal
	p.Trailing = false
	r = runBacktest(ks, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	for _, tr := range r.Trades {
		if tr.Side == "VENDA" && tr.Reason == "stop-loss" && tr.PnL > 0 {
			t.Fatalf("sem stop móvel não deveria sair com lucro pelo stop: %+v", tr)
		}
	}
}

func TestMetrics(t *testing.T) {
	day := int64(24 * 3600 * 1000)
	eq := []float64{100, 110, 99, 120}
	times := []int64{0, day, 2 * day, 365 * day}
	m := calcMetrics(eq, nil, times, 365)
	if math.Abs(m.ReturnPct-20) > 1e-9 {
		t.Fatalf("retorno %v", m.ReturnPct)
	}
	if math.Abs(m.MaxDDPct-10) > 1e-9 { // 110 → 99
		t.Fatalf("maior queda %v", m.MaxDDPct)
	}
	r := []float64{0.1, -0.1, 120.0/99 - 1}
	mean := (r[0] + r[1] + r[2]) / 3
	var ss float64
	for _, x := range r {
		ss += (x - mean) * (x - mean)
	}
	if want := mean / math.Sqrt(ss/2) * math.Sqrt(365); math.Abs(m.Sharpe-want) > 1e-9 {
		t.Fatalf("Sharpe %v, quer %v", m.Sharpe, want)
	}
	// queda medida na mínima da vela
	m = calcMetrics([]float64{100, 100}, []float64{100, 70}, []int64{0, day}, 365)
	if math.Abs(m.MaxDDPct-30) > 1e-9 {
		t.Fatalf("queda pela mínima %v", m.MaxDDPct)
	}
	// operações
	m.addTrades([]Trade{{Side: "VENDA", PnL: 30, PnLPct: 3}, {Side: "VENDA", PnL: -10, PnLPct: -1}, {Side: "COMPRA"}})
	if m.Trades != 2 || m.ProfitFactor != 3 || m.WinRate != 50 || m.AvgTradePct != 1 {
		t.Fatalf("métricas das operações erradas: %+v", m)
	}
	// nada pode virar NaN (o JSON quebraria)
	if _, err := json.Marshal(calcMetrics([]float64{100, 100, 100}, nil, []int64{0, 0, 0}, 365)); err != nil {
		t.Fatalf("métricas com série parada viraram inválidas: %v", err)
	}
}

func TestBacktestBenchAndExposure(t *testing.T) {
	ks := randomWalk(3000, 3)
	r := runBacktest(ks, defaultParams(), BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	first := r.From
	var o0 float64
	for _, k := range ks {
		if k.OpenTime == first {
			o0 = k.O
		}
	}
	want := (ks[len(ks)-1].C/o0*0.999*0.999 - 1) * 100
	if math.Abs(r.BuyHoldPct-want) > 1e-6 || math.Abs(r.Bench.ReturnPct-want) > 1e-6 {
		t.Fatalf("comprar e segurar: %v / %v, quer %v", r.BuyHoldPct, r.Bench.ReturnPct, want)
	}
	if r.Metrics.ExposurePct <= 0 || r.Metrics.ExposurePct >= 100 {
		t.Fatalf("tempo comprado estranho: %v", r.Metrics.ExposurePct)
	}
	if math.Abs(r.Metrics.ReturnPct-r.ReturnPct) > 1e-6 {
		t.Fatalf("retorno das métricas difere do resultado: %v x %v", r.Metrics.ReturnPct, r.ReturnPct)
	}
	if _, err := json.Marshal(r); err != nil {
		t.Fatalf("resultado não vira JSON: %v", err)
	}
}

// O walk-forward escolhe os parâmetros de cada janela só com dados anteriores a ela.
func TestWalkForwardDoesNotPeek(t *testing.T) {
	ks := randomWalk(24*400, 11)
	o := BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000, StopSlipPct: 0.25, Interval: time.Hour}
	p := defaultParams()
	p.Strategy = "donchian"
	a, err := walkForward(ks, p, o, 120, 30, "sharpe")
	if err != nil {
		t.Fatal(err)
	}
	if len(a.Folds) < 5 {
		t.Fatalf("poucas janelas: %d", len(a.Folds))
	}
	// estraga tudo DEPOIS do início do 3º teste: as escolhas das janelas 1 a 3 não podem mudar
	cut := a.Folds[2].TestFrom
	bad := append([]Kline{}, ks...)
	for i := range bad {
		if bad[i].OpenTime >= cut {
			bad[i].O, bad[i].H, bad[i].L, bad[i].C = bad[i].O*3, bad[i].H*3, bad[i].L*3, bad[i].C*3
		}
	}
	b, err := walkForward(bad, p, o, 120, 30, "sharpe")
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 3; i++ {
		if a.Folds[i].Chosen != b.Folds[i].Chosen || a.Folds[i].ISSharpe != b.Folds[i].ISSharpe {
			t.Fatalf("janela %d mudou com dados do futuro: %s x %s", i+1, a.Folds[i].Chosen, b.Folds[i].Chosen)
		}
	}
	for i := 0; i < 2; i++ {
		if a.Folds[i].OOSReturn != b.Folds[i].OOSReturn {
			t.Fatalf("resultado fora da amostra da janela %d mudou com dados do futuro", i+1)
		}
	}
	if _, err := json.Marshal(a); err != nil {
		t.Fatal(err)
	}
}

func TestMonteCarlo(t *testing.T) {
	ks := randomWalk(6000, 5)
	p := defaultParams()
	p.Strategy, p.TrendEMA = "donchian", 0
	r := runBacktest(ks, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	a, err := monteCarlo(r, 500)
	if err != nil {
		t.Fatal(err)
	}
	b, _ := monteCarlo(r, 500)
	for i := range a.RetP {
		if a.RetP[i] != b.RetP[i] {
			t.Fatalf("Monte Carlo deveria ser reproduzível")
		}
		if i > 0 && (a.RetP[i] < a.RetP[i-1] || a.DDP[i] < a.DDP[i-1]) {
			t.Fatalf("percentis fora de ordem: %v %v", a.RetP, a.DDP)
		}
	}
	// com reposição, o produto dos retornos sorteados em média fica perto do original (mesmo conjunto)
	prod := 1.0
	for _, x := range r.tradeRets {
		prod *= 1 + x
	}
	if math.Abs((prod-1)*100-r.ReturnPct) > 1e-6 {
		t.Fatalf("retornos por operação não recompõem o resultado: %v x %v", (prod-1)*100, r.ReturnPct)
	}
}

func TestPortfolioOfIdenticalAssetsEqualsSingle(t *testing.T) {
	ks := randomWalk(3000, 9)
	srv := fakeKlineServer(t, map[string][]Kline{"AAAUSDT": ks, "BBBUSDT": ks})
	b := NewBot(t.TempDir())
	b.data = &Binance{DataHosts: []string{srv}, client: &http.Client{Timeout: 5 * time.Second}}
	p := defaultParams()
	o := BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000, Interval: time.Hour}
	m := b.multi([]string{"AAAUSDT", "BBBUSDT"}, "1h", 3000, 0, 100000, p, o)
	if m.PortAssets != 2 {
		t.Fatalf("carteira deveria ter 2 pares: %+v", m.Rows)
	}
	// quase igual: a 2ª compra acontece com a 1ª já paga (taxa), então a divisão não é perfeitamente igual
	single := runBacktest(ks, p, o)
	if math.Abs(m.Portfolio.ReturnPct-single.ReturnPct) > 0.1 {
		t.Fatalf("carteira de dois pares iguais = um par: %v x %v", m.Portfolio.ReturnPct, single.ReturnPct)
	}
}

func TestOldConfigMigratesAndLiveLimits(t *testing.T) {
	var c Config
	old := `{"symbol":"BTCUSDT","interval":"4h","params":{"fastEMA":9,"slowEMA":21,"trendEMA":200,"rsiPeriod":14,"rsiMaxBuy":70,"stopLossPct":6,"takeProfitPct":0},"tradePct":95}`
	if err := json.Unmarshal([]byte(old), &c); err != nil {
		t.Fatal(err)
	}
	c.Params.normalize()
	if c.Params.Strategy != "ema" || c.Params.StopMode != "pct" || c.Params.SizeMode != "fixo" || c.Params.Trailing {
		t.Fatalf("ajuste antigo deveria continuar igual: %+v", c.Params)
	}
	if err := c.Params.validate(4*time.Hour, true); err != nil {
		t.Fatalf("ajuste antigo deveria ser válido: %v", err)
	}
	p := defaultParams()
	p.Strategy, p.MomDays = "tsmom", 180
	if err := p.validate(time.Hour, true); err == nil || !strings.Contains(err.Error(), "1000") {
		t.Fatalf("momentum de 180 dias em velas de 1h não cabe nas 1000 velas do robô: %v", err)
	}
	if err := p.validate(time.Hour, false); err != nil {
		t.Fatalf("no laboratório pode (baixa mais histórico): %v", err)
	}
}

// fakeKlineServer serve /api/v3/klines (com startTime e limit) para alguns pares; as velas são
// deslocadas para terminar agora.
func fakeKlineServer(t *testing.T, data map[string][]Kline) string {
	shifted := map[string][]Kline{}
	for s, ks := range data {
		off := time.Now().Add(-time.Hour).UnixMilli() - ks[len(ks)-1].OpenTime
		off -= off % 3600000
		cp := make([]Kline, len(ks))
		for i, k := range ks {
			k.OpenTime += off
			k.CloseTime += off
			cp[i] = k
		}
		shifted[s] = cp
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		ks, ok := shifted[q.Get("symbol")]
		if !ok {
			w.WriteHeader(400)
			w.Write([]byte(`{"code":-1121,"msg":"Invalid symbol."}`))
			return
		}
		start, _ := strconv.ParseInt(q.Get("startTime"), 10, 64)
		limit, _ := strconv.Atoi(q.Get("limit"))
		var out [][]any
		for _, k := range ks {
			if k.OpenTime >= start && len(out) < limit {
				f := func(v float64) string { return strconv.FormatFloat(v, 'f', 8, 64) }
				out = append(out, []any{k.OpenTime, f(k.O), f(k.H), f(k.L), f(k.C), "1", k.CloseTime})
			}
		}
		json.NewEncoder(w).Encode(out)
	}))
	t.Cleanup(srv.Close)
	return srv.URL
}
