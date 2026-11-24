package main

// Estratégias e regras de risco. O MESMO código é usado pelo robô ao vivo, pelo backtest e pelo
// laboratório, então os três nunca discordam.
//
// Estratégias (só compradas, Spot):
//   ema      Cruzamento de médias: compra quando a EMA rápida cruza para cima da lenta (com filtros de RSI
//            e tendência); vende no cruzamento para baixo.
//   donchian Rompimento (canal de Donchian, estilo "Turtle"): compra quando o fechamento passa da maior
//            máxima das últimas N velas; vende quando fecha abaixo da menor mínima das últimas M velas.
//   tsmom    Momentum de série temporal: fica comprado enquanto o preço de hoje está acima do de N dias atrás.
//   rsi2     Reversão à média: compra quedas fortes e curtas (RSI curto muito baixo) dentro de uma tendência
//            de alta; vende quando o RSI curto se recupera.
//
// Risco (vale para todas):
//   tamanho  "fixo": % do saldo; "vol": volatilidade-alvo (compra menos quando o mercado está mais agitado).
//   stop     "pct": % abaixo da compra; "atr": N × ATR abaixo da compra. Com "móvel", o stop sobe junto com
//            o maior fechamento desde a compra (nunca desce).

import (
	"fmt"
	"math"
	"time"
)

type Params struct {
	Strategy string `json:"strategy"` // ema | donchian | tsmom | rsi2

	// ema
	FastEMA   int     `json:"fastEMA"`
	SlowEMA   int     `json:"slowEMA"`
	TrendEMA  int     `json:"trendEMA"` // filtro de tendência (ema, donchian, rsi2); 0 = desligado
	RSIPeriod int     `json:"rsiPeriod"`
	RSIMaxBuy float64 `json:"rsiMaxBuy"`
	// donchian
	BreakoutN int `json:"breakoutN"`
	ExitN     int `json:"exitN"`
	// tsmom
	MomDays int `json:"momDays"`
	// rsi2
	MRRSI       int     `json:"mrRsi"`
	MRBuyBelow  float64 `json:"mrBuyBelow"`
	MRSellAbove float64 `json:"mrSellAbove"`

	// risco
	StopMode      string  `json:"stopMode"`    // pct | atr
	StopLossPct   float64 `json:"stopLossPct"` // 0 = sem stop (no modo pct)
	TakeProfitPct float64 `json:"takeProfitPct"`
	ATRPeriod     int     `json:"atrPeriod"`
	ATRMult       float64 `json:"atrMult"`
	Trailing      bool    `json:"trailing"`
	SizeMode      string  `json:"sizeMode"`  // fixo | vol
	TargetVol     float64 `json:"targetVol"` // % ao ano
	VolDays       int     `json:"volDays"`

	// filtro de mercado (cross.go)
	MarketDays int  `json:"marketDays"` // só compra com o BTC acima da média de N dias (0 = desliga)
	MarketExit bool `json:"marketExit"` // ... e vende quando o BTC perde a média
}

var strategyNames = map[string]string{
	"ema":      "Cruzamento de médias",
	"donchian": "Rompimento (Donchian)",
	"tsmom":    "Momentum",
	"rsi2":     "Reversão à média (RSI curto)",
}

func defaultParams() Params {
	p := Params{Strategy: "ema", FastEMA: 9, SlowEMA: 21, TrendEMA: 200, RSIPeriod: 14, RSIMaxBuy: 70,
		StopMode: "pct", StopLossPct: 6}
	p.normalize()
	return p
}

// normalize preenche o que estiver faltando (ajustes salvos por versões antigas) com valores padrão.
// Só mexe em campos onde zero não faz sentido.
func (p *Params) normalize() {
	def := func(v *int, d int) {
		if *v <= 0 {
			*v = d
		}
	}
	deff := func(v *float64, d float64) {
		if *v <= 0 {
			*v = d
		}
	}
	if _, ok := strategyNames[p.Strategy]; !ok { // vazio ou de outra versão (ex.: "ml" da 2.2)
		p.Strategy = "ema"
	}
	def(&p.FastEMA, 9)
	def(&p.SlowEMA, 21)
	def(&p.RSIPeriod, 14)
	deff(&p.RSIMaxBuy, 70)
	def(&p.BreakoutN, 20)
	def(&p.ExitN, 10)
	def(&p.MomDays, 30)
	def(&p.MRRSI, 2)
	deff(&p.MRBuyBelow, 10)
	deff(&p.MRSellAbove, 70)
	if p.StopMode == "" {
		p.StopMode = "pct"
	}
	def(&p.ATRPeriod, 14)
	deff(&p.ATRMult, 3)
	if p.SizeMode == "" {
		p.SizeMode = "fixo"
	}
	deff(&p.TargetVol, 50)
	def(&p.VolDays, 30)
}

func candlesPerDay(dur time.Duration) float64 { return float64(24*time.Hour) / float64(dur) }

func daysToCandles(days int, dur time.Duration) int {
	return int(math.Round(float64(days) * candlesPerDay(dur)))
}

// warmup = quantas velas fechadas são necessárias antes do primeiro sinal válido.
func (p Params) warmup(dur time.Duration) int {
	w := 2
	max := func(v int) {
		if v > w {
			w = v
		}
	}
	switch p.Strategy {
	case "ema":
		max(p.SlowEMA)
		max(p.FastEMA)
		max(p.RSIPeriod + 1)
	case "donchian":
		max(p.BreakoutN + 1)
		max(p.ExitN + 1)
	case "tsmom":
		max(daysToCandles(p.MomDays, dur) + 1)
	case "rsi2":
		max(p.MRRSI + 1)
	}
	if p.Strategy != "tsmom" {
		max(p.TrendEMA)
	}
	if p.StopMode == "atr" {
		max(p.ATRPeriod + 1)
	}
	if p.SizeMode == "vol" {
		max(daysToCandles(p.VolDays, dur) + 1)
	}
	return w + 1
}

// liveMaxWarmup: o robô ao vivo trabalha com as últimas 1000 velas. Deixa uma folga para as médias "assentarem".
const liveMaxWarmup = 900

func (p Params) validate(dur time.Duration, live bool) error {
	switch p.Strategy {
	case "ema":
		if p.FastEMA < 2 || p.SlowEMA <= p.FastEMA {
			return fmt.Errorf("a EMA lenta precisa ser maior que a rápida (e a rápida pelo menos 2)")
		}
		if p.RSIPeriod < 2 || p.RSIMaxBuy <= 0 || p.RSIMaxBuy > 100 {
			return fmt.Errorf("RSI inválido")
		}
	case "donchian":
		if p.BreakoutN < 2 || p.ExitN < 1 {
			return fmt.Errorf("canal de rompimento inválido")
		}
	case "tsmom":
		if p.MomDays < 1 || daysToCandles(p.MomDays, dur) < 2 {
			return fmt.Errorf("período do momentum curto demais para velas de %s", fmtInterval(dur))
		}
	case "rsi2":
		if p.MRRSI < 2 || p.MRBuyBelow <= 0 || p.MRSellAbove <= p.MRBuyBelow || p.MRSellAbove > 100 {
			return fmt.Errorf("reversão à média: o RSI de venda precisa ser maior que o de compra")
		}
	default:
		return fmt.Errorf("estratégia desconhecida")
	}
	if p.Strategy == "ema" && p.TrendEMA != 0 && p.TrendEMA <= p.SlowEMA {
		return fmt.Errorf("a EMA de tendência precisa ser maior que a lenta (ou 0 para desligar)")
	}
	if p.TrendEMA < 0 || p.TrendEMA > 800 {
		return fmt.Errorf("EMA de tendência inválida (0 a 800)")
	}
	if p.StopMode != "pct" && p.StopMode != "atr" {
		return fmt.Errorf("tipo de stop inválido")
	}
	if p.StopLossPct < 0 || p.StopLossPct >= 50 || p.TakeProfitPct < 0 || p.TakeProfitPct > 500 {
		return fmt.Errorf("stop-loss ou take-profit inválido")
	}
	if p.StopMode == "atr" && (p.ATRPeriod < 2 || p.ATRMult <= 0 || p.ATRMult > 20) {
		return fmt.Errorf("stop por ATR inválido")
	}
	if p.SizeMode != "fixo" && p.SizeMode != "vol" {
		return fmt.Errorf("tipo de tamanho inválido")
	}
	if p.SizeMode == "vol" && (p.TargetVol <= 0 || p.TargetVol > 500 || p.VolDays < 2) {
		return fmt.Errorf("volatilidade-alvo inválida")
	}
	if p.MarketDays < 0 || p.MarketDays == 1 || p.MarketDays > 400 {
		return fmt.Errorf("filtro de mercado: use de 2 a 400 dias (ou 0 para desligar)")
	}
	if w := p.warmup(dur); live && w > liveMaxWarmup {
		return fmt.Errorf("com velas de %s estes ajustes precisam de %d velas de histórico, mas o robô ao vivo trabalha com as últimas 1000 velas (até %d de preparo). Use velas maiores ou períodos menores", fmtInterval(dur), w, liveMaxWarmup)
	}
	return nil
}

func fmtInterval(d time.Duration) string {
	switch {
	case d >= 24*time.Hour:
		return fmt.Sprintf("%dd", int(d/(24*time.Hour)))
	case d >= time.Hour:
		return fmt.Sprintf("%dh", int(d/time.Hour))
	}
	return fmt.Sprintf("%dm", int(d/time.Minute))
}

// Prepared = indicadores calculados uma vez para uma série de velas fechadas.
type Prepared struct {
	p                      Params
	ks                     []Kline
	c                      []float64
	fast, slow, trend, rsi []float64
	mr                     []float64
	hi, lo                 []float64
	atr, vol, mom          []float64
	ppy                    float64
	momN                   int
	gate, exit             []bool // filtro de mercado (cross.go): pode comprar? precisa sair? nil = desligado
}

func (p Params) prepare(ks []Kline, dur time.Duration) *Prepared {
	p.normalize()
	c := make([]float64, len(ks))
	for i, k := range ks {
		c[i] = k.C
	}
	pr := &Prepared{p: p, ks: ks, c: c, ppy: candlesPerDay(dur) * 365}
	n := len(c)
	pr.fast, pr.slow, pr.trend, pr.rsi, pr.mr = nanSeries(n), nanSeries(n), nanSeries(n), nanSeries(n), nanSeries(n)
	pr.hi, pr.lo, pr.atr, pr.vol, pr.mom = nanSeries(n), nanSeries(n), nanSeries(n), nanSeries(n), nanSeries(n)
	switch p.Strategy {
	case "ema":
		pr.fast, pr.slow, pr.rsi = ema(c, p.FastEMA), ema(c, p.SlowEMA), rsi(c, p.RSIPeriod)
	case "donchian":
		pr.hi, pr.lo = prevHigh(ks, p.BreakoutN), prevLow(ks, p.ExitN)
	case "tsmom":
		pr.momN = daysToCandles(p.MomDays, dur)
		pr.mom = momentum(c, pr.momN)
	case "rsi2":
		pr.mr = rsi(c, p.MRRSI)
	}
	if p.TrendEMA > 0 && p.Strategy != "tsmom" {
		pr.trend = ema(c, p.TrendEMA)
	}
	if p.StopMode == "atr" {
		pr.atr = atr(ks, p.ATRPeriod)
	}
	if p.SizeMode == "vol" {
		pr.vol = volatility(c, daysToCandles(p.VolDays, dur), pr.ppy)
	}
	return pr
}

func (pr *Prepared) trendOK(i int) bool {
	if pr.p.TrendEMA == 0 || pr.p.Strategy == "tsmom" {
		return true
	}
	t := pr.trend[i]
	return !math.IsNaN(t) && pr.c[i] > t
}

const (
	sigNone = 0
	sigBuy  = 1
	sigSell = -1
)

// signal avalia o fechamento da vela i. in = já está comprado. O filtro de mercado pode bloquear uma
// compra ou forçar a venda.
func (pr *Prepared) signal(i int, in bool) int {
	if in && pr.exit != nil && i >= 0 && i < len(pr.exit) && pr.exit[i] {
		return sigSell
	}
	s := pr.rawSignal(i, in)
	if s == sigBuy && pr.gate != nil && (i < 0 || i >= len(pr.gate) || !pr.gate[i]) {
		return sigNone
	}
	return s
}

func (pr *Prepared) rawSignal(i int, in bool) int {
	p, c := pr.p, pr.c
	if i < 1 || i >= len(c) {
		return sigNone
	}
	nan := math.IsNaN
	switch p.Strategy {
	case "ema":
		f0, s0, f1, s1 := pr.fast[i-1], pr.slow[i-1], pr.fast[i], pr.slow[i]
		if nan(f0) || nan(s0) || nan(f1) || nan(s1) {
			return sigNone
		}
		if in && f0 >= s0 && f1 < s1 {
			return sigSell
		}
		if !in && f0 <= s0 && f1 > s1 && !nan(pr.rsi[i]) && pr.rsi[i] < p.RSIMaxBuy && pr.trendOK(i) {
			return sigBuy
		}
	case "donchian":
		if !in && !nan(pr.hi[i]) && c[i] > pr.hi[i] && pr.trendOK(i) {
			return sigBuy
		}
		if in && !nan(pr.lo[i]) && c[i] < pr.lo[i] {
			return sigSell
		}
	case "tsmom":
		m := pr.mom[i]
		if nan(m) {
			return sigNone
		}
		if !in && m > 0 {
			return sigBuy
		}
		if in && m <= 0 {
			return sigSell
		}
	case "rsi2":
		r := pr.mr[i]
		if nan(r) {
			return sigNone
		}
		if !in && r < p.MRBuyBelow && pr.trendOK(i) {
			return sigBuy
		}
		if in && r > p.MRSellAbove {
			return sigSell
		}
	}
	return sigNone
}

// fraction = que fração do saldo usar numa compra decidida no fechamento da vela i (no máximo maxFrac).
// Volatilidade-alvo: se o mercado oscila 80% ao ano e o alvo é 40%, usa metade do máximo.
func (pr *Prepared) fraction(i int, maxFrac float64) float64 {
	if pr.p.SizeMode != "vol" {
		return maxFrac
	}
	v := pr.annualVol(i)
	if math.IsNaN(v) || v <= 0 {
		return maxFrac
	}
	return math.Min(maxFrac, pr.p.TargetVol/100/v)
}

func (pr *Prepared) annualVol(i int) float64 {
	if i < 0 || i >= len(pr.vol) {
		return math.NaN()
	}
	return pr.vol[i]
}

// initialStop = stop logo depois de comprar a `entry`, com os dados da vela i (a do sinal).
func (pr *Prepared) initialStop(i int, entry float64) float64 {
	p := pr.p
	if p.StopMode == "atr" && i >= 0 && i < len(pr.atr) && !math.IsNaN(pr.atr[i]) {
		return math.Max(0, entry-p.ATRMult*pr.atr[i])
	}
	return pctStop(p, entry)
}

func pctStop(p Params, entry float64) float64 {
	if p.StopLossPct > 0 {
		return entry * (1 - p.StopLossPct/100)
	}
	return 0
}

// trailStop = stop móvel a partir do maior fechamento desde a compra (0 = sem stop móvel).
func (pr *Prepared) trailStop(i int, highest float64) float64 {
	p := pr.p
	if !p.Trailing {
		return 0
	}
	if p.StopMode == "atr" && i >= 0 && i < len(pr.atr) && !math.IsNaN(pr.atr[i]) {
		return math.Max(0, highest-p.ATRMult*pr.atr[i])
	}
	return pctStop(p, highest)
}

func (p Params) take(entry float64) float64 {
	if p.TakeProfitPct > 0 {
		return entry * (1 + p.TakeProfitPct/100)
	}
	return 0
}

// ---------- o que o painel mostra ----------

type ChartLine struct {
	Name   string    `json:"name"`
	Color  string    `json:"color"` // variável de cor do CSS
	Values []float64 `json:"values"`
}

func (pr *Prepared) lines(from, to int) []ChartLine {
	cut := func(v []float64) []float64 {
		out := make([]float64, 0, to-from)
		for i := from; i < to; i++ {
			out = append(out, nz(v[i]))
		}
		return out
	}
	var ls []ChartLine
	switch pr.p.Strategy {
	case "ema":
		ls = append(ls, ChartLine{fmt.Sprintf("EMA %d", pr.p.FastEMA), "--fast", cut(pr.fast)},
			ChartLine{fmt.Sprintf("EMA %d", pr.p.SlowEMA), "--slow", cut(pr.slow)})
	case "donchian":
		ls = append(ls, ChartLine{fmt.Sprintf("Máxima de %d velas", pr.p.BreakoutN), "--fast", cut(pr.hi)},
			ChartLine{fmt.Sprintf("Mínima de %d velas", pr.p.ExitN), "--slow", cut(pr.lo)})
	}
	if pr.p.TrendEMA > 0 && pr.p.Strategy != "tsmom" {
		ls = append(ls, ChartLine{fmt.Sprintf("Tendência (EMA %d)", pr.p.TrendEMA), "--trendc", cut(pr.trend)})
	}
	return ls
}

type InfoTile struct {
	Label string `json:"label"`
	Value string `json:"value"`
	Sub   string `json:"sub"`
	Tone  string `json:"tone"` // pos | neg | ""
}

// info = dois quadrinhos do painel explicando o que a estratégia está vendo agora (vela fechada i).
func (pr *Prepared) info(i int) []InfoTile {
	p, c := pr.p, pr.c
	if i < 1 || i >= len(c) {
		return nil
	}
	tone := func(ok bool) string {
		if ok {
			return "pos"
		}
		return "neg"
	}
	pick := func(ok bool, yes, no string) string {
		if ok {
			return yes
		}
		return no
	}
	var t []InfoTile
	switch p.Strategy {
	case "ema":
		up := pr.fast[i] > pr.slow[i]
		t = append(t,
			InfoTile{"RSI", fmt.Sprintf("%.1f", nz(pr.rsi[i])), pick(pr.rsi[i] >= p.RSIMaxBuy, "acima do limite: não compra", "ok para comprar"), ""},
			InfoTile{"Médias", pick(up, "Alta ▲", "Baixa ▼"), pick(up, "EMA rápida acima da lenta", "EMA rápida abaixo da lenta"), tone(up)})
	case "donchian":
		dist := (nz(pr.hi[i])/c[i] - 1) * 100
		t = append(t,
			InfoTile{"Canal", fmt.Sprintf("%s – %s", fmtPrice(nz(pr.lo[i])), fmtPrice(nz(pr.hi[i]))), "vende abaixo do 1º, compra acima do 2º", ""},
			InfoTile{"Até o rompimento", fmt.Sprintf("%+.1f%%", dist), "quanto falta subir para comprar", tone(dist <= 0)})
	case "tsmom":
		m := nz(pr.mom[i]) * 100
		t = append(t, InfoTile{fmt.Sprintf("Momentum %d dias", p.MomDays), fmt.Sprintf("%+.1f%%", m), pick(m > 0, "positivo: fica comprado", "negativo: fica de fora"), tone(m > 0)})
	case "rsi2":
		t = append(t, InfoTile{fmt.Sprintf("RSI de %d", p.MRRSI), fmt.Sprintf("%.1f", nz(pr.mr[i])), fmt.Sprintf("compra abaixo de %.0f, vende acima de %.0f", p.MRBuyBelow, p.MRSellAbove), ""})
	}
	if p.TrendEMA > 0 && p.Strategy != "tsmom" && len(t) < 2 {
		ok := pr.trendOK(i)
		t = append(t, InfoTile{"Tendência", pick(ok, "Alta ▲", "Baixa ▼"), fmt.Sprintf("preço %s da EMA %d", pick(ok, "acima", "abaixo"), p.TrendEMA), tone(ok)})
	}
	if ok, on := pr.marketOKAt(i); on {
		t = append(t, InfoTile{"Mercado (BTC)", pick(ok, "Alta ▲", "Baixa ▼"),
			pick(ok, fmt.Sprintf("BTC acima da média de %d dias: compras liberadas", p.MarketDays),
				fmt.Sprintf("BTC abaixo da média de %d dias: %s", p.MarketDays, pick(p.MarketExit, "fica de fora", "sem novas compras"))), tone(ok)})
	}
	if p.SizeMode == "vol" && len(t) < 2 {
		if v := pr.annualVol(i) * 100; v > 0 {
			t = append(t, InfoTile{"Volatilidade", fmt.Sprintf("%.0f%% ao ano", v), fmt.Sprintf("alvo %.0f%%: compra %.0f%% do máximo", p.TargetVol, math.Min(1, p.TargetVol/v)*100), ""})
		}
	}
	return t
}

func dayKey(t time.Time) string { return t.Format("2006-01-02") }
