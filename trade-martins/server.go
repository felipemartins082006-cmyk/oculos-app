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

	mux.HandleFunc("GET /api/status", func(w http.ResponseWriter, r *http.Request) {
		reply(w, b.Status(strings.ToUpper(r.URL.Query().Get("pair"))), nil)
	})
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
	mux.HandleFunc("POST /api/sell", func(w http.ResponseWriter, r *http.Request) {
		var body struct {
			Symbol string `json:"symbol"` // vazio = vende todas as posições
		}
		_ = json.NewDecoder(r.Body).Decode(&body)
		reply(w, nil, b.SellNow(strings.ToUpper(strings.TrimSpace(body.Symbol))))
	})
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
	mux.HandleFunc("POST /api/lab", func(w http.ResponseWriter, r *http.Request) {
		var req LabRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			reply(w, nil, fmt.Errorf("dados inválidos"))
			return
		}
		v, err := b.Lab(req)
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
	Lines []ChartLine  `json:"lines"`
}

// PairStatus = um par da carteira, para o painel.
type PairStatus struct {
	*PairLive
	Position    *Position `json:"position"`
	PnLNow      float64   `json:"pnlNow"`    // resultado da posição agora (já descontando a taxa de venda)
	PnLNowPct   float64   `json:"pnlNowPct"` // idem, em %
	PendingSell bool      `json:"pendingSell"`
}

// Status = tudo o que o painel mostra. pair = par cujo gráfico vai junto ("" = o primeiro).
func (b *Bot) Status(pair string) map[string]any {
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

	// pares da carteira (e posições abertas em pares que não estão mais na lista, se houver)
	list := append([]string{}, cfg.pairs()...)
	for s := range b.st.Positions {
		if !cfg.hasPair(s) {
			list = append(list, s)
		}
	}
	if !cfg.hasPair(pair) {
		pair = ""
	}
	if pair == "" && len(list) > 0 {
		pair = list[0]
	}
	pairs := []PairStatus{}
	for _, s := range list {
		pl := b.live.Pairs[s]
		if pl == nil {
			pl = &PairLive{Symbol: s}
		}
		ps := PairStatus{PairLive: pl, Position: b.st.Positions[s], PendingSell: b.st.PendingSells[s]}
		if p := ps.Position; p != nil && pl.Price > 0 && p.Cost > 0 {
			ps.PnLNow = p.Qty*pl.Price*(1-paperFeePct/100) - p.Cost
			ps.PnLNowPct = ps.PnLNow / p.Cost * 100
		}
		pairs = append(pairs, ps)
	}

	// gráfico do par escolhido
	var ch *ChartData
	var stopTake []float64
	if pl := b.live.Pairs[pair]; pl != nil && len(pl.Candles) > 0 {
		ks := pl.Candles
		pr := b.cfg.Params.prepare(ks, intervalDur(b.cfg.Interval))
		from := len(ks) - 120
		if from < 0 {
			from = 0
		}
		ch = &ChartData{Lines: pr.lines(from, len(ks))}
		for i := from; i < len(ks); i++ {
			k := ks[i]
			ch.T = append(ch.T, k.OpenTime)
			ch.OHLC = append(ch.OHLC, [4]float64{k.O, k.H, k.L, k.C})
		}
	}
	if p := b.st.Positions[pair]; p != nil {
		stopTake = []float64{p.Stop, p.Take}
	}

	// resumo das operações do modo atual
	pnl, wins, losses := b.summaryLocked(cfg.Mode)
	trades := []Trade{}
	for i := len(b.st.Trades) - 1; i >= 0 && len(trades) < 200; i-- {
		if t := b.st.Trades[i]; t.Mode == cfg.Mode {
			trades = append(trades, t)
		}
	}
	logs := append([]LogLine{}, b.st.Logs...) // nunca null no JSON (primeira vez que abre)
	if len(logs) > 150 {
		logs = logs[len(logs)-150:]
	}
	return map[string]any{
		"version": version, "running": b.st.Running, "config": cfg, "keys": keys,
		"live": b.live, "pairs": pairs, "pair": pair, "openPositions": len(b.st.Positions),
		"stopTake": stopTake, "trades": trades, "logs": logs,
		"chart": ch, "dayBlocked": b.st.DayBlocked, "dayStartEquity": b.st.DayStartEquity,
		"summary":      map[string]any{"pnl": pnl, "wins": wins, "losses": losses},
		"strategyName": strategyNames[b.cfg.Params.Strategy], "strategies": strategyNames,
		"now": time.Now().UnixMilli(),
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

// ---------- laboratório (backtest, walk-forward, mapa, Monte Carlo, vários pares) ----------

const maxBacktestCandles = 60000

type LabRequest struct {
	Kind      string   `json:"kind"` // backtest | walkforward | heatmap | montecarlo | multi
	Symbol    string   `json:"symbol"`
	Symbols   []string `json:"symbols"`
	Interval  string   `json:"interval"`
	Days      int      `json:"days"`
	Params    Params   `json:"params"`
	TradePct  float64  `json:"tradePct"`
	MaxBuy    float64  `json:"maxBuy"`
	Start     float64  `json:"start"`
	TrainDays int      `json:"trainDays"`
	TestDays  int      `json:"testDays"`
	Objective string   `json:"objective"`
}

func (b *Bot) Lab(req LabRequest) (any, error) {
	req.Symbol = strings.ToUpper(strings.TrimSpace(req.Symbol))
	req.Params.normalize()
	dur := intervalDur(req.Interval)
	if req.Days < 1 || req.Days > 3000 {
		return nil, fmt.Errorf("escolha entre 1 e 3000 dias")
	}
	if err := req.Params.validate(dur, false); err != nil {
		return nil, err
	}
	if req.TradePct <= 0 || req.TradePct > 100 {
		req.TradePct = 95
	}
	if req.Start <= 0 {
		req.Start = 1000
	}
	if req.MaxBuy < 0 {
		req.MaxBuy = 0
	}
	if maxDays := int(time.Duration(maxBacktestCandles) * dur / (24 * time.Hour)); req.Days > maxDays {
		return nil, fmt.Errorf("com velas de %s dá para testar no máximo %d dias (limite de %d velas)", req.Interval, maxDays, maxBacktestCandles)
	}

	// Velas a mais ANTES do período, só para "aquecer" os indicadores (3× o necessário: a EMA "lembra" de onde
	// começou). No walk-forward e no mapa, o aquecimento cobre a combinação mais exigente da grade.
	warm := req.Params.warmup(dur)
	switch req.Kind {
	case "walkforward":
		warm = maxWarmup(append(gridFor(req.Params, dur), req.Params), dur)
	case "heatmap":
		warm = maxWarmup(heatGrid(req.Params, dur), dur)
	}
	extra := 3 * warm
	extraDays := int(time.Duration(extra)*dur/(24*time.Hour)) + 1
	from := time.Now().Add(-time.Duration(req.Days) * 24 * time.Hour)
	o := BTOptions{TradePct: req.TradePct, MaxBuy: req.MaxBuy, FeePct: paperFeePct, Start: req.Start,
		StopSlipPct: backtestStopSlipPct, TradeFrom: from.UnixMilli(), Interval: dur}

	if req.Kind == "multi" {
		syms := parseSymbols(req.Symbols)
		if len(syms) < 2 {
			return nil, fmt.Errorf("informe pelo menos 2 pares (ex.: BTCUSDT, ETHUSDT, SOLUSDT)")
		}
		return b.multi(syms, req.Interval, req.Days, extraDays, maxBacktestCandles+extra, req.Params, o), nil
	}

	ks, err := b.history(req.Symbol, req.Interval, req.Days+extraDays, maxBacktestCandles+extra)
	if err != nil {
		return nil, err
	}
	switch req.Kind {
	case "walkforward":
		if req.TrainDays < 30 || req.TestDays < 7 {
			return nil, fmt.Errorf("use pelo menos 30 dias de treino e 7 de teste")
		}
		if req.Days < req.TrainDays+req.TestDays {
			return nil, fmt.Errorf("o período (%d dias) precisa ser maior que treino + teste (%d dias)", req.Days, req.TrainDays+req.TestDays)
		}
		return walkForward(ks, req.Params, o, req.TrainDays, req.TestDays, req.Objective)
	case "heatmap":
		return heatmap(ks, req.Params, o), nil
	}
	res := runBacktest(ks, req.Params, o)
	if res.Candles < 10 {
		return nil, fmt.Errorf("poucos dados para esse período: aumente os dias ou diminua os períodos dos indicadores")
	}
	res.Symbol, res.Interval = req.Symbol, req.Interval
	if req.Kind == "montecarlo" {
		return monteCarlo(res, 2000)
	}
	return &res, nil
}

// heatGrid = todas as combinações do mapa (para saber quanto histórico baixar).
func heatGrid(p Params, dur time.Duration) []Params {
	_, _, xs, ys, set, _ := heatmapAxes(p)
	var g []Params
	for _, y := range ys {
		for _, x := range xs {
			q := p
			set(&q, x, y)
			if q.validate(dur, false) == nil {
				g = append(g, q)
			}
		}
	}
	return append(g, p)
}
