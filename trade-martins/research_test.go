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
	run  func(syms []string, kls [][]Kline, p Params, o BTOptions) PortResult // nil = runPortfolio
}

func rsRun(t *testing.T, iv string, vs []rsVariant) {
	dur := intervalDur(iv)
	all := make([][]Kline, len(rsPairs))
	for k, s := range rsPairs {
		all[k] = rsData(t, s, iv)
	}
	btcD := rsData(t, "BTCUSDT", "1d")
	for _, per := range rsPeriods {
		kls := make([][]Kline, len(rsPairs))
		for k := range all {
			kls[k] = rsCut(all[k], per.to)
		}
		o := BTOptions{TradePct: 95, FeePct: 0.1, Start: 1000, StopSlipPct: backtestStopSlipPct, TradeFrom: per.from.UnixMilli(), Interval: dur, Market: rsCut(btcD, per.to)}
		fmt.Printf("\n=== %s %s (%s → %s) ===\n", iv, per.name, per.from.Format("2006-01"), per.to.Format("2006-01"))
		fmt.Printf("%-42s %9s %7s %7s %7s %6s %6s %5s\n", "variante", "retorno", "CAGR", "Sharpe", "queda", "t", "oper", "expo")
		var bench Metrics
		for _, v := range vs {
			run := v.run
			if run == nil {
				run = runPortfolio
			}
			r := run(rsPairs, kls, v.p, o)
			m := r.Metrics
			bench = r.Bench
			fmt.Printf("%-42s %8.0f%% %6.1f%% %7.2f %6.1f%% %6.2f %6d %4.0f%%\n", v.name, m.ReturnPct, m.CAGRPct, m.Sharpe, m.MaxDDPct, m.SharpeT, m.Trades, m.ExposurePct)
		}
		fmt.Printf("%-42s %8.0f%% %6.1f%% %7.2f %6.1f%% %6.2f\n", "segurar (1/8 em cada)", bench.ReturnPct, bench.CAGRPct, bench.Sharpe, bench.MaxDDPct, bench.SharpeT)
	}
}

func rsBase() []rsVariant {
	d := defaultParams()
	return []rsVariant{
		{"EMA 9/21 + tendência 200, stop 6% (padrão)", d, nil},
		{"Donchian 20/10 + tendência 200", with(d, func(p *Params) { p.Strategy = "donchian" }), nil},
		{"Momentum 30 dias, sem stop", with(d, func(p *Params) { p.Strategy, p.StopLossPct = "tsmom", 0 }), nil},
		{"Momentum 30 dias + vol-alvo 40%", with(d, func(p *Params) {
			p.Strategy, p.StopLossPct, p.SizeMode, p.TargetVol, p.VolDays = "tsmom", 0, "vol", 40, 30
		}), nil},
		{"RSI2 + tendência 200", with(d, func(p *Params) { p.Strategy = "rsi2" }), nil},
	}
}

func TestResearchBaseline(t *testing.T) {
	for _, iv := range []string{"4h", "1d"} {
		rsRun(t, iv, rsBase())
	}
}

func TestResearchImprovements(t *testing.T) {
	d := defaultParams()
	ema := with(d, func(p *Params) { p.MarketDays = 0 })
	don := with(ema, func(p *Params) { p.Strategy = "donchian" })
	mv := with(ema, func(p *Params) {
		p.Strategy, p.StopLossPct, p.SizeMode, p.TargetVol, p.VolDays = "tsmom", 0, "vol", 40, 30
	})
	mom := with(ema, func(p *Params) { p.Strategy, p.StopLossPct = "tsmom", 0 })
	mkt := func(b Params, days int, exit bool) Params {
		return with(b, func(p *Params) { p.MarketDays, p.MarketExit = days, exit })
	}
	var vs []rsVariant
	for _, b := range []struct {
		n string
		p Params
	}{{"EMA", ema}, {"Donchian", don}, {"Mom30", mom}, {"Mom30+vol40", mv}} {
		vs = append(vs, rsVariant{b.n, b.p, nil},
			rsVariant{b.n + " + BTC>média 100d", mkt(b.p, 100, false), nil},
			rsVariant{b.n + " + BTC>média 100d (sai)", mkt(b.p, 100, true), nil},
			rsVariant{b.n + " + BTC>média 50d (sai)", mkt(b.p, 50, true), nil},
			rsVariant{b.n + " + BTC>média 200d (sai)", mkt(b.p, 200, true), nil})
	}
	ivs := []string{"4h", "1d"}
	if s := os.Getenv("RESEARCH_IV"); s != "" {
		ivs = []string{s}
	}
	for _, iv := range ivs {
		rsRun(t, iv, vs)
	}
}

func TestResearchVolSizing(t *testing.T) {
	d := with(defaultParams(), func(p *Params) { p.MarketDays = 0 })
	vol := func(b Params) Params {
		return with(b, func(p *Params) { p.SizeMode, p.TargetVol, p.VolDays = "vol", 40, 30 })
	}
	don := with(d, func(p *Params) { p.Strategy = "donchian" })
	m := func(b Params) Params { return with(b, func(p *Params) { p.MarketDays, p.MarketExit = 100, true }) }
	vs := []rsVariant{{"EMA", d, nil}, {"EMA + vol40", vol(d), nil}, {"Donchian", don, nil}, {"Donchian + vol40", vol(don), nil},
		{"Donchian + vol40 + BTC 100d", m(vol(don)), nil}}
	for _, iv := range []string{"4h", "1d"} {
		rsRun(t, iv, vs)
	}
}
