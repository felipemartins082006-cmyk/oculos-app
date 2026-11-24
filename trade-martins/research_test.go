//go:build research

package main

// Pesquisa (não entra no build nem nos testes normais):
//   RESEARCH_DIR=/algum/lugar go test -tags research -run Research -v -timeout 60m
// Baixa ~8 anos de velas de 8 pares (guarda em RESEARCH_DIR) e compara variantes de estratégia em dois
// períodos separados: DESENHO (para escolher) e CONFIRMAÇÃO (só olhado no fim).

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"
)

var rsPairs = []string{"BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT", "LINKUSDT"}

func rsData(t *testing.T, sym, iv string) []Kline {
	dir := os.Getenv("RESEARCH_DIR")
	if dir == "" {
		t.Skip("RESEARCH_DIR não definido")
	}
	fn := filepath.Join(dir, sym+"_"+iv+".json")
	if b, err := os.ReadFile(fn); err == nil {
		var ks []Kline
		if json.Unmarshal(b, &ks) == nil && len(ks) > 0 {
			return ks
		}
	}
	ks, err := NewBinance().History(sym, iv, 2900, 20000)
	if err != nil {
		t.Fatal(sym, iv, err)
	}
	b, _ := json.Marshal(ks)
	os.WriteFile(fn, b, 0o644)
	return ks
}

type rsPeriod struct {
	name     string
	from, to time.Time
}

var rsPeriods = []rsPeriod{
	{"desenho", time.Date(2020, 11, 24, 0, 0, 0, 0, time.UTC), time.Date(2023, 11, 24, 0, 0, 0, 0, time.UTC)},
	{"confirmação", time.Date(2023, 11, 24, 0, 0, 0, 0, time.UTC), time.Date(2026, 11, 24, 0, 0, 0, 0, time.UTC)},
}

func rsCut(ks []Kline, to time.Time) []Kline {
	end := to.UnixMilli()
	for i, k := range ks {
		if k.OpenTime >= end {
			return ks[:i]
		}
	}
	return ks
}

type rsVariant struct {
	name string
	p    Params
	mix  []Params // várias estratégias, cada uma com uma parte igual do dinheiro (p é ignorado)
}

// rsUniverse: "fixa8" (os 8 de sempre, com viés de sobrevivência) ou "topN" = os N pares de maior volume
// nos 30 dias antes do início do período, entre os que existiam então (inclusive os que saíram depois).
func rsUniverse(t *testing.T, name, period string) []string {
	if name == "fixa8" {
		return rsPairs
	}
	var n int
	fmt.Sscanf(name, "top%d", &n)
	b, err := os.ReadFile(filepath.Join(os.Getenv("RESEARCH_DIR"), "universe.json"))
	if err != nil {
		t.Skip("rode universe.py antes")
	}
	var u map[string][]string
	json.Unmarshal(b, &u)
	if len(u[period]) < n {
		t.Fatalf("universo %s pequeno", period)
	}
	return u[period][:n]
}

// rsMix roda cada estratégia com 1/n do dinheiro e soma os patrimônios.
func rsMix(syms []string, kls [][]Kline, ps []Params, o BTOptions, dur time.Duration) PortResult {
	o.Start /= float64(len(ps))
	var out PortResult
	for j, p := range ps {
		r := runPortfolio(syms, kls, p, o)
		if j == 0 {
			out = r
			out.eq, out.low, out.bench = append([]float64{}, r.eq...), append([]float64{}, r.low...), append([]float64{}, r.bench...)
			out.Trades = append([]Trade{}, r.Trades...)
			out.Metrics.ExposurePct = r.Metrics.ExposurePct / float64(len(ps))
			continue
		}
		for i := range out.eq {
			out.eq[i] += r.eq[i]
			out.low[i] += r.low[i]
			out.bench[i] += r.bench[i]
		}
		out.Trades = append(out.Trades, r.Trades...)
		out.Metrics.ExposurePct += r.Metrics.ExposurePct / float64(len(ps))
	}
	expo := out.Metrics.ExposurePct
	out.Metrics = calcMetrics(out.eq, out.low, out.times, candlesPerDay(dur)*365)
	out.Metrics.addTrades(out.Trades)
	out.Metrics.ExposurePct = expo
	return out
}

type rsOpts struct {
	universe string
	start    float64 // patrimônio inicial (padrão 1000)
	minOrder float64
}

func rsRun(t *testing.T, iv string, vs []rsVariant, opt ...rsOpts) {
	op := rsOpts{universe: "fixa8"}
	if len(opt) > 0 {
		op = opt[0]
	}
	if op.start == 0 {
		op.start = 1000
	}
	dur := intervalDur(iv)
	btcD := rsData(t, "BTCUSDT", "1d")
	for _, per := range rsPeriods {
		syms := rsUniverse(t, op.universe, per.name)
		kls := make([][]Kline, len(syms))
		for k, s := range syms {
			kls[k] = rsCut(rsData(t, s, iv), per.to)
		}
		o := BTOptions{TradePct: 95, FeePct: 0.1, Start: op.start, MinOrder: op.minOrder, StopSlipPct: backtestStopSlipPct,
			TradeFrom: per.from.UnixMilli(), Interval: dur, Market: rsCut(btcD, per.to)}
		fmt.Printf("\n=== %s %s, %s (%s → %s), início %.0f ===\n", iv, per.name, op.universe, per.from.Format("2006-01"), per.to.Format("2006-01"), op.start)
		fmt.Printf("%-46s %9s %7s %7s %7s %6s %6s %5s\n", "variante", "retorno", "CAGR", "Sharpe", "queda", "t", "oper", "expo")
		var bench Metrics
		for _, v := range vs {
			var r PortResult
			if v.mix != nil {
				r = rsMix(syms, kls, v.mix, o, dur)
			} else {
				r = runPortfolio(syms, kls, v.p, o)
			}
			m := r.Metrics
			bench = r.Bench
			fmt.Printf("%-46s %8.0f%% %6.1f%% %7.2f %6.1f%% %6.2f %6d %4.0f%%\n", v.name, m.ReturnPct, m.CAGRPct, m.Sharpe, m.MaxDDPct, m.SharpeT, m.Trades, m.ExposurePct)
		}
		fmt.Printf("%-46s %8.0f%% %6.1f%% %7.2f %6.1f%% %6.2f\n", fmt.Sprintf("segurar (1/%d em cada)", len(syms)), bench.ReturnPct, bench.CAGRPct, bench.Sharpe, bench.MaxDDPct, bench.SharpeT)
	}
}

// Ideias fixadas ANTES de olhar os resultados: universos maiores e escolhidos na época, mistura de
// estratégias e carteira com poucas vagas (capital pequeno).
func rsStrategies() map[string]Params {
	d := with(defaultParams(), func(p *Params) { p.MarketDays = 0 })
	mkt := func(b Params) Params { return with(b, func(p *Params) { p.MarketDays, p.MarketExit = 100, true }) }
	return map[string]Params{
		"ema": d,
		"don": mkt(with(d, func(p *Params) { p.Strategy = "donchian" })),
		"mom": mkt(with(d, func(p *Params) { p.Strategy, p.StopLossPct = "tsmom", 0 })),
		"momvol": mkt(with(d, func(p *Params) {
			p.Strategy, p.StopLossPct, p.SizeMode, p.TargetVol, p.VolDays = "tsmom", 0, "vol", 40, 30
		})),
	}
}

func rsIntervals() []string {
	if s := os.Getenv("RESEARCH_IV"); s != "" {
		return []string{s}
	}
	return []string{"4h", "1d"}
}

func TestResearchUniverse(t *testing.T) {
	st := rsStrategies()
	vs := []rsVariant{
		{name: "EMA (padrão)", p: st["ema"]},
		{name: "Rompimento + BTC100", p: st["don"]},
		{name: "Momentum + BTC100", p: st["mom"]},
		{name: "Momentum + vol40 + BTC100", p: st["momvol"]},
		{name: "Mistura: EMA + Rompimento + Mom+vol", mix: []Params{st["ema"], st["don"], st["momvol"]}},
		{name: "Mistura: Rompimento + Mom+vol", mix: []Params{st["don"], st["momvol"]}},
	}
	for _, iv := range rsIntervals() {
		for _, u := range []string{"fixa8", "top10", "top20"} {
			rsRun(t, iv, vs, rsOpts{universe: u})
		}
	}
}
