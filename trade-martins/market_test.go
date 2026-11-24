package main

import (
	"testing"
	"time"
)

// velas diárias com fechamentos dados, começando em t0
func dailyKs(t0 int64, closes []float64) []Kline {
	ks := make([]Kline, len(closes))
	for i, c := range closes {
		ks[i] = Kline{OpenTime: t0 + int64(i)*dayMs, CloseTime: t0 + int64(i+1)*dayMs - 1, O: c, H: c, L: c, C: c}
	}
	return ks
}

// velas de 4h com fechamentos dados, começando em t0
func h4Ks(t0 int64, closes []float64) []Kline {
	step := int64(4 * 3600000)
	ks := make([]Kline, len(closes))
	for i, c := range closes {
		ks[i] = Kline{OpenTime: t0 + int64(i)*step, CloseTime: t0 + int64(i+1)*step - 1, O: c, H: c, L: c, C: c}
	}
	return ks
}

func TestMarketStateUsesSimpleAverage(t *testing.T) {
	st := marketState(dailyKs(0, []float64{10, 10, 10, 13, 7}), 3)
	// médias: -, -, 10, 11, 10 → acima? f, f, f(10>10 não), t(13>11), f(7<10)
	want := []bool{false, false, false, true, false}
	for i := range want {
		if st[i] != want[i] {
			t.Fatalf("dia %d: %v, esperava %v", i, st[i], want[i])
		}
	}
}

// BTC sobe 60 dias e depois cai 60 dias. O par (4h) sobe o tempo todo: o momentum quer comprar sempre.
func marketScenario() (pair, btc []Kline) {
	var bc []float64
	for i := 0; i < 60; i++ {
		bc = append(bc, 100+float64(i))
	}
	for i := 0; i < 60; i++ {
		bc = append(bc, 159-2*float64(i))
	}
	btc = dailyKs(0, bc)
	pair = h4Ks(0, rising(120*6, 10, 0.01))
	return
}

func TestMarketFilterBlocksBuysAndForcesExit(t *testing.T) {
	pair, btc := marketScenario()
	p := defaultParams()
	p.Strategy, p.MomDays, p.StopLossPct, p.MarketDays, p.MarketExit = "tsmom", 1, 0, 20, true
	pr := p.prepare(pair, 4*time.Hour)
	pr.applyMarket(btc)
	day := func(d int) int { return d*6 + 5 } // última vela de 4h do dia d
	if s := pr.signal(day(10), false); s != sigNone {
		t.Fatalf("antes de ter 20 dias de BTC, não deveria comprar (sinal %d)", s)
	}
	if s := pr.signal(day(40), false); s != sigBuy {
		t.Fatalf("BTC acima da média: deveria comprar (sinal %d)", s)
	}
	if s := pr.signal(day(100), false); s != sigNone {
		t.Fatalf("BTC abaixo da média: não deveria comprar (sinal %d)", s)
	}
	if s := pr.signal(day(100), true); s != sigSell {
		t.Fatalf("BTC abaixo da média com \"vender também\": deveria vender (sinal %d)", s)
	}
	p.MarketExit = false
	pr = p.prepare(pair, 4*time.Hour)
	pr.applyMarket(btc)
	if s := pr.signal(day(100), true); s != sigNone {
		t.Fatalf("sem \"vender também\", a posição fica (sinal %d)", s)
	}
	if ok, on := pr.marketOKAt(day(100)); ok || !on {
		t.Fatalf("o painel deveria mostrar o filtro ligado e o BTC em baixa")
	}
}

// O filtro nunca usa um dia do BTC que ainda não tinha fechado: mudar o futuro não muda o passado.
func TestMarketFilterNoLookAhead(t *testing.T) {
	pair, btc := marketScenario()
	p := defaultParams()
	p.Strategy, p.MomDays, p.MarketDays, p.MarketExit = "tsmom", 1, 20, true
	full := p.prepare(pair, 4*time.Hour)
	full.applyMarket(btc)
	for cut := 25; cut < 115; cut += 7 {
		fut := append([]Kline{}, btc...)
		for j := cut; j < len(fut); j++ { // inventa um futuro completamente diferente
			fut[j].C, fut[j].O, fut[j].H, fut[j].L = 1, 1, 1, 1
		}
		pr := p.prepare(pair, 4*time.Hour)
		pr.applyMarket(fut)
		for i, k := range pair {
			if k.CloseTime >= btc[cut].CloseTime {
				break
			}
			if pr.gate[i] != full.gate[i] || pr.exit[i] != full.exit[i] {
				t.Fatalf("vela %d (antes do dia %d) mudou quando o futuro do BTC mudou", i, cut)
			}
		}
	}
	// a vela de 4h que fecha no meio do dia usa o dia ANTERIOR do BTC
	i := 40*6 + 2
	j := 39
	if pair[i].CloseTime >= btc[40].CloseTime || pair[i].CloseTime < btc[j].CloseTime {
		t.Fatal("cenário errado")
	}
	if full.gate[i] != marketState(btc, 20)[j] {
		t.Fatal("deveria usar o último dia fechado do BTC")
	}
}

// Sem dados recentes do BTC (conexão caiu por dias): não compra, mas também não vende tudo à toa.
func TestMarketFilterStaleData(t *testing.T) {
	pair, btc := marketScenario()
	p := defaultParams()
	p.Strategy, p.MomDays, p.MarketDays, p.MarketExit = "tsmom", 1, 20, true
	pr := p.prepare(pair, 4*time.Hour)
	pr.applyMarket(btc[:50]) // BTC parou no dia 49 (em alta)
	i := 100*6 + 5
	if s := pr.signal(i, false); s != sigNone {
		t.Fatalf("dados velhos do BTC: não deveria comprar (sinal %d)", s)
	}
	if s := pr.signal(i, true); s == sigSell {
		t.Fatalf("dados velhos do BTC: não deveria forçar venda")
	}
	pr = p.prepare(pair, 4*time.Hour)
	pr.applyMarket(nil)
	if s := pr.signal(40*6+5, false); s != sigNone {
		t.Fatalf("sem velas do BTC: nenhuma compra deveria ser liberada")
	}
}

// Backtest de carteira e backtest de um par usam o mesmo filtro.
func TestMarketFilterInBacktests(t *testing.T) {
	pair, btc := marketScenario()
	p := defaultParams()
	p.Strategy, p.MomDays, p.StopLossPct, p.MarketDays, p.MarketExit = "tsmom", 1, 0, 20, true
	o := BTOptions{TradePct: 95, FeePct: 0.1, Start: 1000, Interval: 4 * time.Hour, Market: btc}
	r := runBacktest(pair, p, o)
	pr := runPortfolio([]string{"ETHUSDT"}, [][]Kline{pair}, p, o)
	if r.Metrics.Trades != 1 || pr.Metrics.Trades != 1 {
		t.Fatalf("deveria comprar quando o BTC passa a média e vender quando perde: %d / %d operações", r.Metrics.Trades, pr.Metrics.Trades)
	}
	if r.EndEquity != pr.EndEquity {
		t.Fatalf("carteira de um par deveria dar o mesmo que o backtest: %v × %v", r.EndEquity, pr.EndEquity)
	}
	var sold Trade
	for _, tr := range r.Trades {
		if tr.Side == "VENDA" {
			sold = tr
		}
	}
	// a média de 20 dias é passada para baixo poucos dias depois do pico (dia 59)
	if d := sold.Time / dayMs; d < 60 || d > 75 {
		t.Fatalf("venda no dia %d; esperava logo depois do pico do BTC", d)
	}
}

// Ao vivo: o robô baixa as velas diárias do BTC e respeita o filtro.
func TestLiveMarketFilter(t *testing.T) {
	for _, up := range []bool{true, false} {
		f := newFake(t)
		f.prices["ETHUSDT"] = 3000
		eth := rising(1000, 2000, 1) // momentum positivo: quer comprar
		f.closesOf = map[string][]float64{"ETHUSDT": eth}
		f.prices["ETHUSDT"] = eth[999]
		if up {
			f.daily = rising(150, 30000, 100)
		} else {
			f.daily = rising(150, 60000, -100)
		}
		b := NewBot(t.TempDir())
		b.bn = &Binance{TradeHost: f.srv.URL, DataHosts: []string{f.srv.URL}, client: f.srv.Client()}
		b.cfg.Symbols, b.cfg.Interval, b.cfg.TradePct = []string{"ETHUSDT"}, "1h", 50
		b.cfg.Params.Strategy, b.cfg.Params.MomDays, b.cfg.Params.StopLossPct = "tsmom", 1, 0
		b.cfg.Params.MarketDays, b.cfg.Params.MarketExit = 100, true
		b.st.Running = true
		b.tick()
		b.mu.Lock()
		got := b.st.Positions["ETHUSDT"] != nil
		info := b.live.Pairs["ETHUSDT"].Info
		b.mu.Unlock()
		if got != up {
			t.Fatalf("BTC em alta=%v: comprou=%v (registro: %+v)", up, got, b.st.Logs)
		}
		found := false
		for _, it := range info {
			found = found || it.Label == "Mercado (BTC)"
		}
		if !found {
			t.Fatalf("o painel deveria mostrar o filtro de mercado: %+v", info)
		}
	}
}
