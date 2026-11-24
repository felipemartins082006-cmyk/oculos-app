package main

import (
	"math"
	"testing"
	"time"
)

func closedSeries(start int64, closes ...float64) []Kline {
	ks := make([]Kline, len(closes))
	for i, c := range closes {
		t := start + int64(i)*3600000
		ks[i] = Kline{OpenTime: t, CloseTime: t + 3599999, O: c, H: c, L: c, C: c}
	}
	return ks
}

func TestLiveTrailingStopOnlyGoesUp(t *testing.T) {
	b := paperBot(t)
	b.cfg.Interval = "1h"
	b.cfg.Params.StopLossPct, b.cfg.Params.Trailing = 6, true
	entryT := time.Now().Add(-10 * time.Hour).UnixMilli()
	b.st.Position = &Position{Symbol: "BTCUSDT", Mode: "simulado", Qty: 1, Entry: 100, Cost: 100, Time: entryT, Stop: 94, Highest: 100}

	ks := closedSeries(entryT-5*3600000, 90, 95, 99, 100, 101, 105, 110, 120, 115, 108)
	pr := b.cfg.Params.prepare(ks, time.Hour)
	b.mu.Lock()
	b.updateStopLocked(ks, pr, len(ks)-1)
	b.mu.Unlock()
	p := b.st.Position
	if p.Highest != 120 || math.Abs(p.Stop-120*0.94) > 1e-9 {
		t.Fatalf("stop deveria subir para 6%% abaixo do maior fechamento (120): highest=%v stop=%v", p.Highest, p.Stop)
	}
	// velas antes da compra não contam (houve um fechamento de 90..99 antes)
	ks2 := closedSeries(entryT-4*3600000, 200, 95, 99, 100, 101) // a última (101) fecha depois da compra
	b.st.Position.Highest, b.st.Position.Stop = 100, 94
	b.mu.Lock()
	b.updateStopLocked(ks2, b.cfg.Params.prepare(ks2, time.Hour), len(ks2)-1)
	b.mu.Unlock()
	if b.st.Position.Highest != 101 {
		t.Fatalf("o fechamento de 200 foi ANTES da compra e não pode contar: %v", b.st.Position.Highest)
	}
	// preço caindo não abaixa o stop
	b.st.Position.Stop = 112.8
	ks3 := closedSeries(entryT, 100, 95, 90)
	b.mu.Lock()
	b.updateStopLocked(ks3, b.cfg.Params.prepare(ks3, time.Hour), len(ks3)-1)
	b.mu.Unlock()
	if b.st.Position.Stop != 112.8 {
		t.Fatalf("stop não pode descer: %v", b.st.Position.Stop)
	}
}

func TestLiveATRStopRecalculatedAfterSettingsChange(t *testing.T) {
	b := paperBot(t)
	b.cfg.Interval = "1h"
	b.st.Position = &Position{Symbol: "BTCUSDT", Mode: "simulado", Qty: 1, Entry: 100, Cost: 100, Time: time.Now().UnixMilli(), Stop: 94, Highest: 100}
	nc := b.cfg
	nc.Params.StopMode, nc.Params.ATRPeriod, nc.Params.ATRMult = "atr", 14, 2
	if err := b.UpdateConfig(nc); err != nil {
		t.Fatal(err)
	}
	if !b.st.Position.StopDirty {
		t.Fatalf("stop por ATR precisa ser recalculado com as velas")
	}
	// velas com amplitude 4 → ATR 4 → stop = 100 − 2×4 = 92
	ks := make([]Kline, 40)
	for i := range ks {
		ks[i] = Kline{OpenTime: int64(i) * 3600000, CloseTime: int64(i+1)*3600000 - 1, O: 100, H: 102, L: 98, C: 100}
	}
	b.mu.Lock()
	b.updateStopLocked(ks, b.cfg.Params.prepare(ks, time.Hour), len(ks)-1)
	b.mu.Unlock()
	if p := b.st.Position; p.StopDirty || math.Abs(p.Stop-92) > 1e-9 {
		t.Fatalf("stop por ATR deveria ser 92: %+v", p)
	}
}

func TestExchangeTrailingStopMovesOrder(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.cfg.Interval = "1h"
	b.cfg.Params.Strategy, b.cfg.Params.TrendEMA = "donchian", 0
	b.cfg.Params.StopLossPct, b.cfg.Params.Trailing = 5, true

	b.mu.Lock()
	b.buyLocked(50000)
	firstID := b.st.Position.StopOrderID
	b.st.Running = true
	entryT := b.st.Position.Time
	b.mu.Unlock()
	if firstID == 0 {
		t.Fatalf("stop deveria estar na Binance")
	}

	// depois da compra o preço sobe até 60.000 (sem romper para baixo o canal: não há sinal de venda)
	closes := make([]float64, 1000)
	for i := range closes {
		closes[i] = 50000
	}
	for i := 990; i < 999; i++ {
		closes[i] = 50000 + float64(i-989)*1000 // 51000 … 59000
	}
	closes[999] = 60000 // vela aberta
	f.mu.Lock()
	f.closes, f.price = closes, 60000
	f.mu.Unlock()
	// as velas do fake terminam agora; a compra foi "antes" das velas que sobem
	b.mu.Lock()
	b.st.Position.Time = entryT - 20*3600000
	b.mu.Unlock()

	b.tick()
	b.mu.Lock()
	p := *b.st.Position
	b.mu.Unlock()
	if math.Abs(p.Stop-59000*0.95) > 1e-6 {
		t.Fatalf("stop deveria ter subido para 5%% abaixo de 59.000: %v", p.Stop)
	}
	stops := f.openStops()
	if len(stops) != 1 || stops[0].ID == firstID || math.Abs(stops[0].StopPrice-56050) > 0.01 {
		t.Fatalf("a ordem de stop na Binance deveria ter sido trocada pela nova (56.050): %+v", stops)
	}
}
