package main

import (
	"encoding/json"
	"math"
	"net"
	"net/http"
	"strings"
	"testing"
)

func realBot(t *testing.T, f *fakeBinance) *Bot {
	b := newExchangeBot(t, f)
	b.cfg.Mode = "real"
	b.cfg.APIKey, b.cfg.APISecret = fkKey, fkSecret
	b.cfg.MaxBuy = 100
	return b
}

func TestRealModeNeedsMaxBuy(t *testing.T) {
	f := newFake(t)
	b := realBot(t, f)
	b.cfg.MaxBuy = 0
	err := b.Start(true)
	if err == nil || !strings.Contains(err.Error(), "valor máximo por compra") {
		t.Fatalf("deveria exigir o valor máximo por compra, veio: %v", err)
	}
	b.cfg.MaxBuy = 100
	if err := b.Start(true); err != nil {
		t.Fatalf("com limite e chave boa deveria ligar: %v", err)
	}
}

func TestRealModeRefusesKeyWithWithdrawals(t *testing.T) {
	f := newFake(t)
	b := realBot(t, f)
	f.perms["enableWithdrawals"] = true
	err := b.Start(true)
	if err == nil || !strings.Contains(err.Error(), "SAQUES") {
		t.Fatalf("deveria recusar chave com saque, veio: %v", err)
	}
	if b.st.Running {
		t.Fatalf("não deveria ter ligado")
	}

	f.perms["enableWithdrawals"] = false
	f.perms["permitsUniversalTransfer"] = true
	if err := b.Start(true); err == nil || !strings.Contains(err.Error(), "TRANSFERÊNCIAS") {
		t.Fatalf("deveria recusar chave com transferência, veio: %v", err)
	}

	f.perms["permitsUniversalTransfer"] = false
	f.perms["enableSpotAndMarginTrading"] = false
	if err := b.Start(true); err == nil || !strings.Contains(err.Error(), "negociação Spot") {
		t.Fatalf("deveria recusar chave sem Spot, veio: %v", err)
	}
}

func TestResumeRefusedIfKeyAllowsWithdrawals(t *testing.T) {
	f := newFake(t)
	b := realBot(t, f)
	b.st.Running = true
	f.perms["enableWithdrawals"] = true
	b.mu.Lock()
	b.resumeLocked()
	running := b.st.Running
	b.mu.Unlock()
	if running {
		t.Fatalf("o robô não deveria ser retomado com chave que permite saque")
	}
}

func TestMaxBuyCapsPurchases(t *testing.T) {
	// modo real: conta tem 1000 USDT, 50% = 500, mas o limite é 100
	f := newFake(t)
	b := realBot(t, f)
	b.mu.Lock()
	b.buyLocked("BTCUSDT", 50000)
	p := b.st.Positions["BTCUSDT"]
	b.mu.Unlock()
	if p == nil || math.Abs(p.Cost-100) > 1e-6 {
		t.Fatalf("deveria gastar só 100, gastou %+v", p)
	}
	if f.free["USDT"] < 899.99 {
		t.Fatalf("saldo na Binance deveria ficar em 900, ficou %v", f.free["USDT"])
	}

	// modo simulado também respeita o limite
	s := NewBot(t.TempDir())
	s.infos["BTCUSDT"] = &SymbolInfo{Symbol: "BTCUSDT", Base: "BTC", Quote: "USDT"}
	s.cfg.MaxBuy = 250
	s.mu.Lock()
	s.buyLocked("BTCUSDT", 100)
	s.mu.Unlock()
	if math.Abs(s.st.Positions["BTCUSDT"].Cost-250) > 1e-9 || math.Abs(s.st.PaperQuote-750) > 1e-9 {
		t.Fatalf("simulado deveria gastar 250: %+v saldo %v", s.st.Positions["BTCUSDT"], s.st.PaperQuote)
	}
}

func TestCompareVersions(t *testing.T) {
	cases := []struct {
		a, b string
		want int
	}{{"1.2", "1.1", 1}, {"1.1", "1.2", -1}, {"1.2", "1.2", 0}, {"1.10", "1.9", 1}, {"2.0", "1.99", 1}, {"1.2", "1.2.0", 0}}
	for _, c := range cases {
		if got := compareVersions(c.a, c.b); got != c.want {
			t.Errorf("compareVersions(%s,%s)=%d quer %d", c.a, c.b, got, c.want)
		}
	}
}

// fakeOld simula um TradeMartins já aberto na porta do painel.
func fakeOld(t *testing.T, ver string, canQuit bool) {
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		t.Skipf("porta %s ocupada: %v", addr, err)
	}
	srv := &http.Server{}
	srv.Handler = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/api/status":
			w.Write([]byte(`{"version":"` + ver + `"}`))
		case "/api/quit":
			if !canQuit || r.Header.Get("X-TradeMartins") != "1" {
				http.NotFound(w, r)
				return
			}
			w.Write([]byte(`{"ok":true}`))
			go srv.Close()
		}
	})
	go srv.Serve(ln)
	t.Cleanup(func() { srv.Close() })
}

func TestNewVersionReplacesOldOne(t *testing.T) {
	fakeOld(t, "1.1", true)
	ln, msg := takeOver()
	if ln == nil {
		t.Fatalf("a versão nova deveria assumir: %s", msg)
	}
	ln.Close()
}

func TestSameOrNewerVersionIsKept(t *testing.T) {
	for _, v := range []string{version, "9.0"} {
		t.Run(v, func(t *testing.T) {
			fakeOld(t, v, true)
			ln, msg := takeOver()
			if ln != nil {
				ln.Close()
				t.Fatalf("não deveria fechar a versão %s", v)
			}
			if msg == "" {
				t.Fatalf("deveria explicar o que houve")
			}
		})
	}
}

// Na primeira vez que o programa abre não há registro nem operações: o painel precisa receber listas vazias, não null.
func TestStatusOnFirstRunHasEmptyLists(t *testing.T) {
	b := NewBot(t.TempDir())
	st := b.Status("")
	data, _ := json.Marshal(st)
	for _, k := range []string{`"logs":[]`, `"trades":[]`} {
		if !strings.Contains(string(data), k) {
			t.Fatalf("status na primeira vez deveria ter %s: %s", k, data)
		}
	}
}
