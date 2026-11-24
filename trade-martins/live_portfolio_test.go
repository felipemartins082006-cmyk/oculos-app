package main

import (
	"encoding/json"
	"math"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// O estado salvo pela versão 2.0 (um par, uma posição) é convertido sem perder nada.
func TestStateMigrationFromSinglePair(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "config.json"), []byte(`{"symbol":"ETHUSDT","interval":"4h","mode":"simulado","tradePct":95,"paperStart":1000,"params":{"strategy":"ema"}}`), 0o600)
	os.WriteFile(filepath.Join(dir, "estado.json"), []byte(`{"running":true,"paperQuote":500,"paperBase":0.01,"paperSymbol":"ETHUSDT",
		"position":{"symbol":"ETHUSDT","mode":"simulado","qty":0.01,"entry":50000,"cost":500,"time":1,"stop":47000},
		"lastCandle":12345,"pendingSell":true,"trades":[{"time":1,"side":"COMPRA","symbol":"BTCUSDT","mode":"simulado","price":50000,"qty":0.01,"quote":500}]}`), 0o600)
	b := NewBot(dir)
	// o robô operava ETH: a carteira tem que ser só ETH (antes virava BTC, o padrão!)
	if len(b.cfg.pairs()) != 1 || b.cfg.pairs()[0] != "ETHUSDT" {
		t.Fatalf("carteira deveria ser só ETHUSDT (o par da versão antiga): %v", b.cfg.pairs())
	}
	p := b.st.Positions["ETHUSDT"]
	if p == nil || p.Stop != 47000 || b.st.PaperBases["ETHUSDT"] != 0.01 || b.st.LastCandles["ETHUSDT"] != 12345 || !b.st.PendingSells["ETHUSDT"] {
		t.Fatalf("migração incompleta: %+v %+v", p, b.st)
	}
	b.mu.Lock()
	b.saveLocked()
	b.mu.Unlock()
	data, _ := os.ReadFile(filepath.Join(dir, "estado.json"))
	if strings.Contains(string(data), `"position":`) || strings.Contains(string(data), `"paperBase":`) {
		t.Fatalf("campos antigos deveriam sumir do arquivo salvo")
	}
	var st State
	json.Unmarshal(data, &st)
	if st.Positions["ETHUSDT"] == nil {
		t.Fatalf("posição não foi salva no formato novo")
	}
}

// Posição aberta num par que não está na lista: volta para a carteira (nunca fica sem cuidado).
func TestOrphanPositionRejoinsPortfolio(t *testing.T) {
	dir := t.TempDir()
	os.WriteFile(filepath.Join(dir, "config.json"), []byte(`{"symbols":["BTCUSDT"],"interval":"4h","mode":"simulado","tradePct":95,"paperStart":1000}`), 0o600)
	os.WriteFile(filepath.Join(dir, "estado.json"), []byte(`{"positions":{"SOLUSDT":{"symbol":"SOLUSDT","mode":"simulado","qty":1,"entry":100,"cost":100}}}`), 0o600)
	b := NewBot(dir)
	if got := strings.Join(b.cfg.pairs(), ","); got != "BTCUSDT,SOLUSDT" {
		t.Fatalf("SOLUSDT deveria voltar para a carteira: %s", got)
	}
}

func rising(n int, from, step float64) []float64 {
	c := make([]float64, n)
	for i := range c {
		c[i] = from + float64(i)*step
	}
	return c
}

// Na mesma checagem, o par que vende libera dinheiro ANTES do par que compra.
func TestLiveSellsBeforeBuys(t *testing.T) {
	f := newFake(t)
	f.prices["ETHUSDT"] = 3000
	btc := rising(1000, 40000, 10)
	for i := 975; i < 1000; i++ { // cai nas últimas velas: momentum de 1 dia negativo → vende
		btc[i] = btc[974] - float64(i-974)*100
	}
	eth := rising(1000, 2000, 1) // subindo: momentum positivo → compra
	f.closesOf = map[string][]float64{"BTCUSDT": btc, "ETHUSDT": eth}
	f.price, f.prices["ETHUSDT"] = btc[999], eth[999]

	b := NewBot(t.TempDir())
	b.bn = &Binance{TradeHost: f.srv.URL, DataHosts: []string{f.srv.URL}, client: f.srv.Client()}
	b.cfg.Symbols, b.cfg.Interval, b.cfg.TradePct = []string{"ETHUSDT", "BTCUSDT"}, "1h", 100 // ETH primeiro: só dá certo se as vendas vierem antes
	b.cfg.Params.Strategy, b.cfg.Params.MomDays, b.cfg.Params.StopLossPct = "tsmom", 1, 0
	// todo o dinheiro está em BTC
	qty := 1000 / btc[900]
	b.st.Positions["BTCUSDT"] = &Position{Symbol: "BTCUSDT", Mode: "simulado", Qty: qty, Entry: btc[900], Cost: 1000, Time: 1}
	b.st.PaperBases["BTCUSDT"], b.st.PaperQuote, b.st.Running = qty, 0, true

	b.tick()
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.st.Positions["BTCUSDT"] != nil {
		t.Fatalf("BTC deveria ter sido vendido; registro: %+v", b.st.Logs)
	}
	e := b.st.Positions["ETHUSDT"]
	if e == nil {
		t.Fatalf("ETH deveria ter sido comprado com o dinheiro da venda do BTC; registro: %+v", b.st.Logs)
	}
	equity := b.st.PaperQuote + e.Cost
	if math.Abs(e.Cost-equity/2) > 1e-6 {
		t.Fatalf("ETH deveria usar metade do patrimônio (%v), usou %v", equity/2, e.Cost)
	}
	n := len(b.st.Trades)
	if b.st.Trades[n-2].Side != "VENDA" || b.st.Trades[n-2].Symbol != "BTCUSDT" || b.st.Trades[n-1].Side != "COMPRA" || b.st.Trades[n-1].Symbol != "ETHUSDT" {
		t.Fatalf("ordem errada: %+v", b.st.Trades[n-2:])
	}
	if pl := b.live.Pairs["ETHUSDT"]; pl == nil || pl.Price != eth[999] || len(pl.Info) == 0 {
		t.Fatalf("painel do ETH não atualizou: %+v", pl)
	}
}

// Dois pares na Binance: cada um com sua ordem de stop; o stop de um executar não mexe no outro.
func TestExchangeTwoPairsIndependentStops(t *testing.T) {
	f := newFake(t)
	f.prices["ETHUSDT"] = 3000
	b := newExchangeBot(t, f)
	b.cfg.Symbols, b.cfg.TradePct = []string{"BTCUSDT", "ETHUSDT"}, 100
	eth, err := b.bn.Symbol("ETHUSDT")
	if err != nil {
		t.Fatal(err)
	}
	b.infos["ETHUSDT"] = eth
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked("BTCUSDT", 50000)
	b.buyLocked("ETHUSDT", 3000)
	pb, pe := b.st.Positions["BTCUSDT"], b.st.Positions["ETHUSDT"]
	if pb == nil || pe == nil || math.Abs(pb.Cost-500) > 1e-6 || pe.Cost > 500 || pe.Cost < 495 {
		t.Fatalf("cada par deveria usar ~metade: BTC %+v ETH %+v", pb, pe)
	}
	stops := f.openStops()
	if len(stops) != 2 || pb.StopOrderID == pe.StopOrderID {
		t.Fatalf("deveria haver uma ordem de stop por par: %+v", stops)
	}
	for _, o := range stops {
		want := map[string]float64{"BTCUSDT": 47000, "ETHUSDT": 2820}[o.Symbol]
		if o.StopPrice != want {
			t.Fatalf("stop de %s a %v, quer %v", o.Symbol, o.StopPrice, want)
		}
	}
	// ETH despenca e o stop dele executa na Binance
	f.setPriceOf("ETHUSDT", 2800)
	b.syncStopLocked("ETHUSDT", 2800)
	b.syncStopLocked("BTCUSDT", 50000)
	if b.st.Positions["ETHUSDT"] != nil || b.st.Positions["BTCUSDT"] == nil || b.st.Positions["BTCUSDT"].StopOrderID != pb.StopOrderID {
		t.Fatalf("só o ETH deveria ter saído: %+v", b.st.Positions)
	}
	if tr := lastTrade(b); tr.Symbol != "ETHUSDT" || tr.Reason != "stop-loss na Binance" {
		t.Fatalf("venda errada: %+v", tr)
	}
	// vender tudo
	b.live.Pairs["BTCUSDT"] = &PairLive{Symbol: "BTCUSDT", Price: 50000}
	b.mu.Unlock()
	err = b.SellNow("")
	b.mu.Lock()
	if err != nil || len(b.st.Positions) != 0 || len(f.openStops()) != 0 || f.free["BTC"] > 0.00001 {
		t.Fatalf("vender tudo falhou: %v %+v", err, b.st.Positions)
	}
}

func TestPortfolioSettingsRules(t *testing.T) {
	b := NewBot(t.TempDir())
	b.st.Positions["BTCUSDT"] = &Position{Symbol: "BTCUSDT", Mode: "simulado", Qty: 1, Entry: 100, Cost: 100}
	nc := b.cfg
	nc.Symbols = []string{"ETHUSDT"}
	if err := b.UpdateConfig(nc); err == nil || !strings.Contains(err.Error(), "BTCUSDT") {
		t.Fatalf("não pode tirar da carteira um par com posição aberta: %v", err)
	}
	nc.Symbols = []string{"BTCUSDT", "ETHBTC"}
	if err := b.UpdateConfig(nc); err == nil || !strings.Contains(err.Error(), "mesma moeda") {
		t.Fatalf("pares com moedas diferentes não podem: %v", err)
	}
	nc.Symbols = []string{"btcusdt", " ethusdt", "SOLUSDT, ETHUSDT"}
	if err := b.UpdateConfig(nc); err != nil {
		t.Fatal(err)
	}
	if got := strings.Join(b.cfg.Symbols, ","); got != "BTCUSDT,ETHUSDT,SOLUSDT" || b.cfg.Symbol != "BTCUSDT" {
		t.Fatalf("lista de pares mal lida: %s", got)
	}
}

func TestTelegramSellPerPair(t *testing.T) {
	b := paperBot(t)
	b.cfg.Symbols = []string{"BTCUSDT", "ETHUSDT"}
	b.infos["ETHUSDT"] = &SymbolInfo{Symbol: "ETHUSDT", Base: "ETH", Quote: "USDT"}
	b.live.Pairs["ETHUSDT"] = &PairLive{Symbol: "ETHUSDT", Base: "ETH", Price: 3000}
	b.mu.Lock()
	b.buyLocked("BTCUSDT", 60000)
	b.buyLocked("ETHUSDT", 3000)
	b.mu.Unlock()
	r := b.handleTelegramUpdate(upd(42, "/vender", 0), myChat)
	if !strings.Contains(r, "BTCUSDT") || !strings.Contains(r, "ETHUSDT") || !strings.Contains(r, "/vender tudo sim") {
		t.Fatalf("com 2 posições, /vender deveria listar: %q", r)
	}
	if r := b.handleTelegramUpdate(upd(42, "/vender sim", 0), myChat); !strings.Contains(r, "Posições abertas") || len(b.st.Positions) != 2 {
		t.Fatalf("/vender sim com 2 posições é ambíguo e não pode vender: %q", r)
	}
	b.handleTelegramUpdate(upd(42, "/vender eth sim", 0), myChat)
	if b.st.Positions["ETHUSDT"] != nil || b.st.Positions["BTCUSDT"] == nil {
		t.Fatalf("/vender eth sim deveria vender só o ETH")
	}
	if r := b.handleTelegramUpdate(upd(42, "/status", 0), myChat); !strings.Contains(r, "ETHUSDT 3000.00") || !strings.Contains(r, "comprado a 60000.00") {
		t.Fatalf("status da carteira: %q", r)
	}
	_ = time.Now
}

// Depois de comprar ou vender, o painel mostra o dinheiro novo na hora (não só na próxima checagem).
func TestPanelCashUpdatesRightAfterTrades(t *testing.T) {
	b := paperBot(t)
	b.st.Positions["BTCUSDT"] = &Position{Symbol: "BTCUSDT", Mode: "simulado", Qty: 0.01, Entry: 60000, Cost: 600}
	b.st.PaperBases["BTCUSDT"], b.st.PaperQuote = 0.01, 400
	b.live.QuoteBal, b.live.Equity = 400, 1000
	if err := b.SellNow("BTCUSDT"); err != nil {
		t.Fatal(err)
	}
	want := 400 + 600*(1-paperFeePct/100)
	if math.Abs(b.live.QuoteBal-want) > 1e-9 || math.Abs(b.live.Equity-want) > 1e-9 {
		t.Fatalf("painel deveria mostrar %v em dinheiro já, mostra %v (patrimônio %v)", want, b.live.QuoteBal, b.live.Equity)
	}
}
