package main

import (
	"math"
	"testing"
	"time"
)

// Com um par só, a carteira tem que dar exatamente o mesmo resultado que o backtest normal.
func TestPortfolioOfOneEqualsBacktest(t *testing.T) {
	for _, base := range allStrategies() {
		for _, risk := range []func(*Params){func(p *Params) {}, func(p *Params) { p.StopMode, p.SizeMode, p.Trailing, p.TakeProfitPct = "atr", "vol", true, 15 }} {
			p := base
			risk(&p)
			ks := randomWalk(4000, 21)
			o := BTOptions{TradePct: 90, MaxBuy: 0, FeePct: 0.1, Start: 1000, StopSlipPct: 0.25, TradeFrom: ks[500].OpenTime, Interval: time.Hour}
			a := runBacktest(ks, p, o)
			b := runPortfolio([]string{"X"}, [][]Kline{ks}, p, o)
			if math.Abs(a.EndEquity-b.EndEquity) > 1e-6 || len(a.Trades) != len(b.Trades) {
				t.Fatalf("%s: carteira de 1 par %.6f (%d ops) x backtest %.6f (%d ops)", p.Strategy, b.EndEquity, len(b.Trades), a.EndEquity, len(a.Trades))
			}
			if math.Abs(a.Metrics.Sharpe-b.Metrics.Sharpe) > 1e-9 || math.Abs(a.Metrics.MaxDDPct-b.Metrics.MaxDDPct) > 1e-9 ||
				math.Abs(a.Bench.ReturnPct-b.Bench.ReturnPct) > 1e-9 || math.Abs(a.Metrics.ExposurePct-b.Metrics.ExposurePct) > 1e-9 {
				t.Fatalf("%s: métricas diferentes: %+v x %+v", p.Strategy, b.Metrics, a.Metrics)
			}
		}
	}
}

func TestPortfolioSharesCashAndRespectsSlots(t *testing.T) {
	syms := []string{"A", "B", "C", "D"}
	var kls [][]Kline
	for i := range syms {
		kls = append(kls, randomWalk(3000, uint64(100+i)))
	}
	p := defaultParams()
	p.Strategy, p.MomDays = "tsmom", 3
	o := BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000, Interval: time.Hour}
	r := runPortfolio(syms, kls, p, o)
	if r.Metrics.Trades < 20 {
		t.Fatalf("poucas operações para testar: %d", r.Metrics.Trades)
	}
	// reconstrói o dinheiro livre ao longo das operações: nunca pode ficar negativo, e nenhuma compra pode
	// passar de 1/4 do patrimônio (valorizado ao preço de abertura de cada par naquele instante)
	openPx := func(sym string, at int64) float64 {
		for k, s := range syms {
			if s == sym {
				for _, c := range kls[k] {
					if c.OpenTime == at {
						return c.O
					}
				}
			}
		}
		t.Fatalf("sem vela de %s em %d", sym, at)
		return 0
	}
	cash, open := 1000.0, map[string]Trade{}
	for _, tr := range r.Trades {
		if tr.Side == "COMPRA" {
			equity := cash
			for sym, o := range open {
				equity += o.Qty * openPx(sym, tr.Time)
			}
			if tr.Quote > equity/4+1e-6 {
				t.Fatalf("compra de %.4f passou do limite de 1/4 de %.4f", tr.Quote, equity)
			}
			cash -= tr.Quote
			open[tr.Symbol] = tr
		} else {
			cash += tr.Quote
			delete(open, tr.Symbol)
		}
		if cash < -1e-6 {
			t.Fatalf("dinheiro ficou negativo: %v", cash)
		}
	}
	if math.Abs(cash-r.EndEquity) > 1e-6 {
		t.Fatalf("dinheiro final %v não bate com o patrimônio %v", cash, r.EndEquity)
	}
	got := 0.0
	for _, ps := range r.PerSymbol {
		got += ps.PnL
	}
	if math.Abs(got-(r.EndEquity-1000)) > 1e-6 {
		t.Fatalf("soma do resultado por par %v ≠ resultado total %v", got, r.EndEquity-1000)
	}
}

// Par que só começa a existir no meio do período: a carteira continua funcionando e a parte dele fica em dinheiro.
func TestPortfolioWithLateListing(t *testing.T) {
	a := randomWalk(3000, 1)
	b := randomWalk(3000, 2)[1500:]
	p := defaultParams()
	r := runPortfolio([]string{"A", "NEW"}, [][]Kline{a, b}, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000, Interval: time.Hour})
	if r.Candles < 2500 || r.EndEquity <= 0 {
		t.Fatalf("carteira com par novo falhou: %+v", r.Metrics)
	}
	for _, tr := range r.Trades {
		if tr.Symbol == "NEW" && tr.Time < b[0].OpenTime {
			t.Fatalf("operou o par antes de ele existir")
		}
	}
}
