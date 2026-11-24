package main

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"net/http"
	"strings"
	"time"
)

func newServer(b *Bot) http.Handler {
	mux := http.NewServeMux()
	sub, _ := fs.Sub(webFS, "web")
	mux.Handle("/", http.FileServer(http.FS(sub)))

	mux.HandleFunc("GET /api/status", func(w http.ResponseWriter, r *http.Request) { reply(w, b.Status(), nil) })
	mux.HandleFunc("POST /api/start", func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			ConfirmReal bool `json:"confirmReal"`
		}
		_ = json.NewDecoder(r.Body).Decode(&body)
		reply(w, nil, b.Start(body.ConfirmReal))
	})
	mux.HandleFunc("POST /api/quit", func(w http.ResponseWriter, r *http.Request) {
		reply(w, nil, nil)
		if f, ok := w.(http.Flusher); ok {
			f.Flush()
		}
		b.Quit()
	})
	mux.HandleFunc("POST /api/stop", func(w http.ResponseWriter, r *http.Request) { b.Stop(); reply(w, nil, nil) })
	mux.HandleFunc("POST /api/sell", func(w http.ResponseWriter, r *http.Request) { reply(w, nil, b.SellNow()) })
	mux.HandleFunc("POST /api/reset-paper", func(w http.ResponseWriter, r *http.Request) { reply(w, nil, b.ResetPaper()) })
	mux.HandleFunc("POST /api/config", func(w http.ResponseWriter, r *http.Request) {
		var c Config
		if err := json.NewDecoder(r.Body).Decode(&c); err != nil {
			reply(w, nil, fmt.Errorf("dados inválidos"))
			return
		}
		reply(w, nil, b.UpdateConfig(c))
	})
	mux.HandleFunc("POST /api/test-keys", func(w http.ResponseWriter, r *http.Request) {
		var c struct{ Which, APIKey, APISecret string }
		_ = json.NewDecoder(r.Body).Decode(&c)
		v, err := b.TestKeys(c.Which, c.APIKey, c.APISecret)
		reply(w, v, err)
	})
	mux.HandleFunc("POST /api/telegram-find", func(w http.ResponseWriter, r *http.Request) {
		var c struct{ Token string }
		_ = json.NewDecoder(r.Body).Decode(&c)
		tok := b.telegramToken(c.Token)
		id, name, err := telegramFindChat(b.httpc, tok)
		reply(w, map[string]string{"chat": id, "name": name}, err)
	})
	mux.HandleFunc("POST /api/telegram-test", func(w http.ResponseWriter, r *http.Request) {
		var c struct{ Token, Chat string }
		_ = json.NewDecoder(r.Body).Decode(&c)
		tok := b.telegramToken(c.Token)
		if tok == "" || strings.TrimSpace(c.Chat) == "" {
			reply(w, nil, fmt.Errorf("preencha o token e o chat"))
			return
		}
		reply(w, nil, telegramSend(b.httpc, tok, strings.TrimSpace(c.Chat), "TradeMartins: ✅ Teste de aviso. Se você recebeu isto, os avisos estão funcionando!"))
	})
	mux.HandleFunc("POST /api/backtest", func(w http.ResponseWriter, r *http.Request) {
		var req BTRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			reply(w, nil, fmt.Errorf("dados inválidos"))
			return
		}
		v, err := b.Backtest(req)
		reply(w, v, err)
	})
	return guard(mux)
}

// guard: o painel só aceita pedidos vindos dele mesmo.
// Bloqueia sites abertos no navegador que tentem mandar comandos para o robô (DNS rebinding / CSRF).
func guard(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		host := r.Host
		if host != addr && host != strings.Replace(addr, "127.0.0.1", "localhost", 1) {
			http.Error(w, "acesso negado", http.StatusForbidden)
			return
		}
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			if r.Header.Get("X-TradeMartins") != "1" {
				http.Error(w, "acesso negado", http.StatusForbidden)
				return
			}
			if o := r.Header.Get("Origin"); o != "" && o != "http://"+host {
				http.Error(w, "acesso negado", http.StatusForbidden)
				return
			}
		}
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Frame-Options", "DENY")
		h.ServeHTTP(w, r)
	})
}

func reply(w http.ResponseWriter, v any, err error) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		_ = json.NewEncoder(w).Encode(map[string]string{"error": err.Error()})
		return
	}
	if v == nil {
		v = map[string]bool{"ok": true}
	}
	_ = json.NewEncoder(w).Encode(v)
}

// ---------- status ----------

type ChartData struct {
	T     []int64      `json:"t"`
	OHLC  [][4]float64 `json:"ohlc"`
	Fast  []float64    `json:"fast"`
	Slow  []float64    `json:"slow"`
	Trend []float64    `json:"trend,omitempty"`
}

func (b *Bot) Status() map[string]any {
	b.mu.Lock()
	defer b.mu.Unlock()
	cfg := b.cfg
	hint := func(k string) string {
		if len(k) > 8 {
			return k[:4] + "…" + k[len(k)-4:]
		}
		return ""
	}
	keys := map[string]any{
		"real":     map[string]any{"has": cfg.APIKey != "", "hint": hint(cfg.APIKey)},
		"teste":    map[string]any{"has": cfg.TestAPIKey != "", "hint": hint(cfg.TestAPIKey)},
		"telegram": map[string]any{"has": cfg.TelegramToken != "", "hint": hint(cfg.TelegramToken)},
	}
	cfg.APIKey, cfg.APISecret, cfg.TestAPIKey, cfg.TestAPISecret, cfg.TelegramToken = "", "", "", "", ""

	// gráfico com as últimas velas
	var ch *ChartData
	if ks := b.live.Candles; len(ks) > 0 {
		closes := make([]float64, len(ks))
		for i, k := range ks {
			closes[i] = k.C
		}
		ind := compute(closes, b.cfg.Params)
		from := len(ks) - 120
		if from < 0 {
			from = 0
		}
		ch = &ChartData{}
		for i := from; i < len(ks); i++ {
			k := ks[i]
			ch.T = append(ch.T, k.OpenTime)
			ch.OHLC = append(ch.OHLC, [4]float64{k.O, k.H, k.L, k.C})
			ch.Fast = append(ch.Fast, nz(ind.Fast[i]))
			ch.Slow = append(ch.Slow, nz(ind.Slow[i]))
			if ind.Trend != nil {
				ch.Trend = append(ch.Trend, nz(ind.Trend[i]))
			}
		}
	}

	// resumo das operações do modo atual
	var pnl float64
	var wins, losses int
	trades := []Trade{}
	for i := len(b.st.Trades) - 1; i >= 0; i-- {
		t := b.st.Trades[i]
		if t.Mode != cfg.Mode {
			continue
		}
		if t.Side == "VENDA" {
			pnl += t.PnL
			if t.PnL > 0 {
				wins++
			} else {
				losses++
			}
		}
		if len(trades) < 200 {
			trades = append(trades, t)
		}
	}
	logs := append([]LogLine{}, b.st.Logs...) // nunca null no JSON (primeira vez que abre)
	if len(logs) > 150 {
		logs = logs[len(logs)-150:]
	}
	var stopTake []float64
	if p := b.st.Position; p != nil {
		stopTake = []float64{p.Stop, p.Take}
	}
	return map[string]any{
		"version": version, "running": b.st.Running, "config": cfg, "keys": keys,
		"live": b.live, "position": b.st.Position, "stopTake": stopTake, "trades": trades, "logs": logs,
		"chart": ch, "dayBlocked": b.st.DayBlocked, "dayStartEquity": b.st.DayStartEquity,
		"summary": map[string]any{"pnl": pnl, "wins": wins, "losses": losses},
		"now":     time.Now().UnixMilli(),
	}
}

func (b *Bot) telegramToken(t string) string {
	if t = strings.TrimSpace(t); t != "" {
		return t
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.cfg.TelegramToken
}

func (b *Bot) TestKeys(which, key, secret string) (any, error) {
	if which != "teste" {
		which = "real"
	}
	b.mu.Lock()
	k0, s0 := b.cfg.keys(which)
	if key == "" {
		key = k0
	}
	if secret == "" {
		secret = s0
	}
	sym := b.cfg.Symbol
	b.mu.Unlock()
	if key == "" || secret == "" {
		return nil, fmt.Errorf("preencha a chave e a chave secreta")
	}
	c := newFor(which)
	c.Key, c.Secret = key, secret
	info, err := c.Symbol(sym)
	if err != nil {
		return nil, err
	}
	_, total, err := c.Balances()
	if err != nil {
		return nil, err
	}
	res := map[string]any{"ok": true, "quote": info.Quote, "base": info.Base, "quoteBal": total[info.Quote], "baseBal": total[info.Base]}
	if which == "real" {
		perms, err := c.APIRestrictions()
		if err != nil {
			res["permsError"] = err.Error()
		} else {
			res["perms"] = perms
			res["problem"] = perms.problem()
		}
	}
	return res, nil
}

// ---------- backtest ----------

type BTRequest struct {
	Symbol   string  `json:"symbol"`
	Interval string  `json:"interval"`
	Days     int     `json:"days"`
	Params   Params  `json:"params"`
	TradePct float64 `json:"tradePct"`
	Start    float64 `json:"start"`
}

func (b *Bot) Backtest(req BTRequest) (*BTResult, error) {
	req.Symbol = strings.ToUpper(strings.TrimSpace(req.Symbol))
	if req.Days < 1 || req.Days > 1500 {
		return nil, fmt.Errorf("escolha entre 1 e 1500 dias")
	}
	if req.Params.FastEMA < 2 || req.Params.SlowEMA <= req.Params.FastEMA {
		return nil, fmt.Errorf("a EMA lenta precisa ser maior que a rápida")
	}
	if req.Params.TrendEMA != 0 && req.Params.TrendEMA <= req.Params.SlowEMA {
		return nil, fmt.Errorf("a EMA de tendência precisa ser maior que a lenta (ou 0)")
	}
	if req.Params.RSIPeriod < 2 {
		return nil, fmt.Errorf("RSI inválido")
	}
	if req.TradePct <= 0 || req.TradePct > 100 {
		req.TradePct = 95
	}
	if req.Start <= 0 {
		req.Start = 1000
	}
	ks, err := b.data.History(req.Symbol, req.Interval, req.Days, 60000)
	if err != nil {
		return nil, err
	}
	if len(ks) < warmup(req.Params)+10 {
		return nil, fmt.Errorf("poucos dados para esse período: aumente os dias ou diminua as médias (precisa de pelo menos %d velas, vieram %d)", warmup(req.Params)+10, len(ks))
	}
	res := runBacktest(ks, req.Params, req.TradePct, paperFeePct, req.Start)
	res.Symbol, res.Interval = req.Symbol, req.Interval
	return &res, nil
}
