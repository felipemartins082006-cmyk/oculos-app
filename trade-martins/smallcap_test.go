package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"
)

// Compra abaixo do mínimo da corretora não acontece, no backtest de um par e na carteira.
func TestMinOrderBlocksSmallBuys(t *testing.T) {
	ks := h4Ks(0, rising(400, 10, 0.01))
	p := defaultParams()
	p.Strategy, p.MomDays, p.StopLossPct = "tsmom", 1, 0
	for _, start := range []float64{4, 50} {
		o := BTOptions{TradePct: 95, FeePct: 0.1, Start: start, MinOrder: 5, Interval: 4 * time.Hour}
		r := runBacktest(ks, p, o)
		pr := runPortfolio([]string{"ETHUSDT"}, [][]Kline{ks}, p, o)
		bought := len(r.Trades) > 0
		if bought != (start == 50) || (len(pr.Trades) > 0) != bought {
			t.Fatalf("início %v: backtest comprou=%v, carteira comprou=%v", start, bought, len(pr.Trades) > 0)
		}
	}
	// carteira de 4 pares com 12: cada compra seria de ~2,85, abaixo de 5
	kls := [][]Kline{ks, ks, ks, ks}
	r := runPortfolio([]string{"A", "B", "C", "D"}, kls, p, BTOptions{TradePct: 95, FeePct: 0.1, Start: 12, MinOrder: 5, Interval: 4 * time.Hour})
	if len(r.Trades) != 0 {
		t.Fatalf("nenhuma compra deveria passar do mínimo: %d operações", len(r.Trades))
	}
}

// Par que sai da corretora no meio do teste é vendido pelo último preço (e libera o dinheiro).
func TestDelistedPairIsSold(t *testing.T) {
	a := h4Ks(0, rising(600, 10, 0.01))
	b := h4Ks(0, rising(300, 10, 0.01)) // as velas acabam na metade
	p := defaultParams()
	p.Strategy, p.MomDays, p.StopLossPct = "tsmom", 1, 0
	r := runPortfolio([]string{"A", "B"}, [][]Kline{a, b}, p, BTOptions{TradePct: 95, FeePct: 0.1, Start: 1000, Interval: 4 * time.Hour})
	found := false
	for _, tr := range r.Trades {
		if tr.Symbol == "B" && tr.Reason == "saiu da corretora" {
			found = true
			if tr.Time <= b[len(b)-1].OpenTime || tr.Price != b[len(b)-1].C {
				t.Fatalf("venda do par que saiu: %+v", tr)
			}
		}
	}
	if !found {
		t.Fatalf("o par B deveria ser vendido quando as velas acabam: %+v", r.Trades)
	}
}

// Modo simulado respeita o mínimo por ordem da Binance, e o painel avisa quando o saldo é pequeno.
func TestPaperRespectsMinNotionalAndWarns(t *testing.T) {
	f := newFake(t)
	eth := rising(1000, 2000, 1)
	f.closesOf = map[string][]float64{"ETHUSDT": eth, "BTCUSDT": rising(1000, 40000, 10)}
	f.prices["ETHUSDT"], f.price = eth[999], 40000+9990
	b := NewBot(t.TempDir())
	b.bn = &Binance{TradeHost: f.srv.URL, DataHosts: []string{f.srv.URL}, client: f.srv.Client()}
	b.cfg.Symbols, b.cfg.Interval, b.cfg.TradePct = []string{"ETHUSDT", "BTCUSDT"}, "1h", 95
	b.cfg.Params.Strategy, b.cfg.Params.MomDays, b.cfg.Params.StopLossPct = "tsmom", 1, 0
	b.st.PaperQuote, b.st.Running = 8, true // 8 USDT ÷ 2 pares × 95% = 3,80 por compra
	b.tick()
	b.mu.Lock()
	defer b.mu.Unlock()
	if len(b.st.Positions) != 0 {
		t.Fatalf("compras de 3,80 USDT não deveriam acontecer (mínimo 5): %+v", b.st.Positions)
	}
	w := b.sizeWarningLocked()
	if !strings.Contains(w, "Use no máximo 1 par") {
		t.Fatalf("aviso de saldo pequeno: %q", w)
	}
	b.st.PaperQuote = 1000
	b.live.Equity = 1000
	if w := b.sizeWarningLocked(); w != "" {
		t.Fatalf("com 1000 não deveria avisar: %q", w)
	}
}

func TestTopByVolume(t *testing.T) {
	vol := map[string]float64{"BTCUSDT": 900, "ETHUSDT": 800, "USDCUSDT": 5000, "BTCUPUSDT": 700, "SOLUSDT": 300,
		"DOGEUSDT": 200, "NEWUSDT": 10000, "ETHBTC": 99999, "XRPUSDT": 100, "NVDABUSDT": 850, "BNBUSDT": 250}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/v3/ticker/24hr":
			var out []map[string]any
			for s := range vol {
				out = append(out, map[string]any{"symbol": s, "quoteVolume": "1", "count": 5})
			}
			json.NewEncoder(w).Encode(out)
		case "/api/v3/klines":
			s := r.URL.Query().Get("symbol")
			days := 31
			if s == "NEWUSDT" {
				days = 10 // moeda nova: menos de 30 dias de história, fica de fora
			}
			var out [][]any
			for i := 0; i < days; i++ {
				out = append(out, []any{0, "1", "1", "1", "1", "1", 0, strconv.FormatFloat(vol[s], 'f', -1, 64), 0})
			}
			json.NewEncoder(w).Encode(out)
		}
	}))
	defer srv.Close()
	bn := &Binance{DataHosts: []string{srv.URL}, client: srv.Client()}
	got, err := bn.TopByVolume("USDT", 4)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Join(got, ",") != "BTCUSDT,ETHUSDT,SOLUSDT,BNBUSDT" {
		t.Fatalf("top 4: %v (sem stablecoin, ação tokenizada, token alavancado, moeda nova ou outro par de cotação)", got)
	}
}
