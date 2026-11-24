package main

import (
	"math"
	"testing"
)

func TestSignBinanceDocVector(t *testing.T) {
	// Exemplo oficial da documentação da Binance.
	secret := "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"
	q := "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559"
	want := "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71"
	if got := sign(secret, q); got != want {
		t.Fatalf("assinatura = %s, quer %s", got, want)
	}
}

func TestFloorStep(t *testing.T) {
	cases := []struct {
		v, step float64
		want    string
	}{
		{0.123456789, 0.00001, "0.12345"},
		{1.99999, 0.01, "1.99"},
		{0.3, 0.1, "0.3"},
		{12.7, 1, "12"},
	}
	for _, c := range cases {
		if got := floorStep(c.v, c.step); got != c.want {
			t.Errorf("floorStep(%v,%v)=%s quer %s", c.v, c.step, got, c.want)
		}
	}
	if got := floorDec(99.999, 2); got != "99.99" {
		t.Errorf("floorDec = %s", got)
	}
}

func TestEMAandRSI(t *testing.T) {
	v := []float64{1, 2, 3, 4, 5, 6}
	e := ema(v, 3)
	if !math.IsNaN(e[1]) || e[2] != 2 || e[3] != 3 {
		t.Fatalf("ema errada: %v", e)
	}
	r := rsi([]float64{1, 2, 3, 4, 5, 6, 7}, 3)
	if r[3] != 100 || r[6] != 100 {
		t.Fatalf("rsi de série só subindo deveria ser 100: %v", r)
	}
	r = rsi([]float64{5, 4, 3, 2, 1}, 3)
	if r[4] != 0 {
		t.Fatalf("rsi de série só caindo deveria ser 0: %v", r)
	}
}

// series monta velas a partir de trechos {quantidade de velas, variação por vela}.
func series(segs [][2]float64) []Kline {
	var ks []Kline
	p := 100.0
	for _, sg := range segs {
		for i := 0; i < int(sg[0]); i++ {
			o := p
			p += sg[1]
			ks = append(ks, Kline{OpenTime: int64(len(ks)) * 3600000, CloseTime: int64(len(ks)+1)*3600000 - 1,
				O: o, H: math.Max(o, p) + 0.1, L: math.Min(o, p) - 0.1, C: p})
		}
	}
	return ks
}

// série que cai, sobe forte e cai de novo: deve gerar uma compra e uma venda.
func synth() []Kline {
	var ks []Kline
	p := 100.0
	add := func(n int, d float64) {
		for i := 0; i < n; i++ {
			o := p
			p += d
			ks = append(ks, Kline{OpenTime: int64(len(ks)) * 3600000, CloseTime: int64(len(ks)+1)*3600000 - 1,
				O: o, H: math.Max(o, p) + 0.1, L: math.Min(o, p) - 0.1, C: p})
		}
	}
	add(40, -0.5)
	add(40, 1)
	add(40, -1)
	return ks
}

func TestBacktestCrossover(t *testing.T) {
	p := Params{FastEMA: 3, SlowEMA: 8, RSIPeriod: 5, RSIMaxBuy: 101}
	res := runBacktest(synth(), p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	if len(res.Trades) != 2 || res.Trades[0].Side != "COMPRA" || res.Trades[1].Side != "VENDA" {
		t.Fatalf("esperava 1 compra e 1 venda, veio %+v", res.Trades)
	}
	if res.EndEquity <= 1000 {
		t.Fatalf("a alta deveria dar lucro, ficou %.2f", res.EndEquity)
	}
	// com stop-loss bem curto e take-profit, a saída deve ser pelo take-profit na alta
	p.TakeProfitPct = 5
	res = runBacktest(synth(), p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	if res.Trades[1].Reason != "take-profit" {
		t.Fatalf("esperava saída por take-profit, veio %s", res.Trades[1].Reason)
	}
	if res.MaxDDPct < 0 || res.MaxDDPct > 100 {
		t.Fatalf("drawdown estranho: %v", res.MaxDDPct)
	}
}

func TestTrendFilterBlocksBuy(t *testing.T) {
	// cruzamento para cima abaixo da EMA longa não deve comprar
	p := Params{FastEMA: 3, SlowEMA: 8, TrendEMA: 60, RSIPeriod: 5, RSIMaxBuy: 101}
	ks := synth()[:70] // corta logo depois do início da alta, ainda abaixo da média de 60
	res := runBacktest(ks, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000})
	for _, tr := range res.Trades {
		if tr.Side == "COMPRA" && tr.Price < 90 {
			t.Fatalf("comprou abaixo da tendência: %+v", tr)
		}
	}
}

func TestPaperBuySell(t *testing.T) {
	b := NewBot(t.TempDir())
	b.infos["BTCUSDT"] = &SymbolInfo{Symbol: "BTCUSDT", Base: "BTC", Quote: "USDT"}
	b.cfg.TradePct = 50
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked("BTCUSDT", 100)
	p := b.st.Positions["BTCUSDT"]
	if p == nil || b.st.PaperQuote != 500 || math.Abs(p.Qty-4.995) > 1e-9 || p.Stop != 94 {
		t.Fatalf("compra simulada errada: %+v quote=%v", p, b.st.PaperQuote)
	}
	b.sellLocked("BTCUSDT", 110, "teste")
	if b.st.Positions["BTCUSDT"] != nil || b.st.PaperBases["BTCUSDT"] != 0 {
		t.Fatalf("posição deveria estar fechada")
	}
	want := 500 + 4.995*110*0.999
	if math.Abs(b.st.PaperQuote-want) > 1e-9 {
		t.Fatalf("saldo = %v, quer %v", b.st.PaperQuote, want)
	}
	last := b.st.Trades[len(b.st.Trades)-1]
	if math.Abs(last.PnL-(4.995*110*0.999-500)) > 1e-9 {
		t.Fatalf("resultado errado: %+v", last)
	}
}

func TestBacktestRespectsWarmupMaxBuyAndSlippage(t *testing.T) {
	ks := synth()
	p := Params{FastEMA: 3, SlowEMA: 8, RSIPeriod: 5, RSIMaxBuy: 101, StopLossPct: 2}
	// só opera a partir da vela 60: a compra da alta (que começa na vela 40) não pode acontecer antes
	from := ks[60].OpenTime
	res := runBacktest(ks, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000, TradeFrom: from})
	if res.From != from {
		t.Fatalf("o teste deveria começar na vela 60")
	}
	for _, tr := range res.Trades {
		if tr.Time < from {
			t.Fatalf("operou durante o aquecimento: %+v", tr)
		}
	}
	// valor máximo por compra
	res = runBacktest(ks, p, BTOptions{TradePct: 100, MaxBuy: 200, FeePct: 0.1, Start: 1000})
	if len(res.Trades) == 0 || res.Trades[0].Quote != 200 {
		t.Fatalf("deveria comprar só 200: %+v", res.Trades)
	}
	// derrapagem do stop: série que sobe (gera compra) e despenca (bate o stop antes do sinal de venda)
	crash := series([][2]float64{{40, -0.5}, {4, 1}, {1, -10}, {5, -1}})
	res = runBacktest(crash, p, BTOptions{TradePct: 100, FeePct: 0.1, Start: 1000, StopSlipPct: 0.25})
	for i, tr := range res.Trades {
		if tr.Reason == "stop-loss" {
			stop := res.Trades[i-1].Price * 0.98 // stop de 2% sobre o preço de compra
			if want := stop * (1 - 0.0025); math.Abs(tr.Price-want) > 1e-9 {
				t.Fatalf("stop deveria vender a %v (0,25%% abaixo de %v), vendeu a %v", want, stop, tr.Price)
			}
			return
		}
	}
	t.Fatalf("esperava ao menos uma saída por stop-loss: %+v", res.Trades)
}

// Com 1000 velas, a EMA de 200 fica praticamente igual à calculada com o histórico todo.
func TestEMA200With1000CandlesMatchesFullHistory(t *testing.T) {
	v := make([]float64, 5000)
	x := 100.0
	for i := range v {
		x *= 1 + 0.02*math.Sin(float64(i)*0.37)*math.Cos(float64(i)*0.011)
		v[i] = x
	}
	full := ema(v, 200)
	win := ema(v[len(v)-999:], 200)
	diff := math.Abs(win[len(win)-1]-full[len(full)-1]) / full[len(full)-1] * 100
	if diff > 0.01 {
		t.Fatalf("diferença de %.4f%% com 1000 velas", diff)
	}
	short := ema(v[len(v)-350:], 200)
	if d := math.Abs(short[len(short)-1]-full[len(full)-1]) / full[len(full)-1] * 100; d < diff {
		t.Fatalf("com 350 velas deveria errar mais (%.4f%%) que com 1000 (%.4f%%)", d, diff)
	}
}
