package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const paperFeePct = 0.1 // taxa padrão da Binance Spot por lado

type Config struct {
	Symbol          string  `json:"symbol"`
	Interval        string  `json:"interval"`
	Params          Params  `json:"params"`
	TradePct        float64 `json:"tradePct"`        // % do saldo em moeda usado por compra
	MaxBuy          float64 `json:"maxBuy"`          // valor máximo por compra (em USDT/BRL…); 0 = sem limite
	MaxDailyLossPct float64 `json:"maxDailyLossPct"` // 0 = sem limite
	CheckSeconds    int     `json:"checkSeconds"`
	Mode            string  `json:"mode"` // "simulado" | "teste" (Binance de teste) | "real"
	PaperStart      float64 `json:"paperStart"`
	APIKey          string  `json:"apiKey"`
	APISecret       string  `json:"apiSecret"`
	TestAPIKey      string  `json:"testApiKey"`
	TestAPISecret   string  `json:"testApiSecret"`
	TelegramToken   string  `json:"telegramToken"`
	TelegramChat    string  `json:"telegramChat"`
	PingURL         string  `json:"pingUrl"`
	NoAutostart     bool    `json:"noAutostart"` // true = não abrir junto com o Windows
}

func defaultConfig() Config {
	return Config{
		Symbol: "BTCUSDT", Interval: "4h",
		Params:   defaultParams(),
		TradePct: 95, MaxDailyLossPct: 5, CheckSeconds: 30,
		Mode: "simulado", PaperStart: 1000,
	}
}

// isExchange: o modo manda ordens de verdade para uma Binance (a real ou a de teste).
func isExchange(mode string) bool { return mode == "real" || mode == "teste" }

func (c Config) keys(mode string) (string, string) {
	if mode == "teste" {
		return c.TestAPIKey, c.TestAPISecret
	}
	return c.APIKey, c.APISecret
}

type Trade struct {
	Time       int64   `json:"time"`
	Side       string  `json:"side"` // COMPRA | VENDA
	Symbol     string  `json:"symbol,omitempty"`
	Mode       string  `json:"mode,omitempty"`
	Price      float64 `json:"price"`
	Qty        float64 `json:"qty"`
	Quote      float64 `json:"quote"` // valor em moeda (gasto na compra / recebido na venda)
	Reason     string  `json:"reason"`
	PnL        float64 `json:"pnl,omitempty"`
	PnLPct     float64 `json:"pnlPct,omitempty"`
	EntryTime  int64   `json:"entryTime,omitempty"`
	EntryPrice float64 `json:"entryPrice,omitempty"`
}

type Position struct {
	Symbol string  `json:"symbol"`
	Mode   string  `json:"mode"`
	Qty    float64 `json:"qty"`
	Entry  float64 `json:"entry"`
	Cost   float64 `json:"cost"`
	Time   int64   `json:"time"`
	Stop   float64 `json:"stop"`
	Take   float64 `json:"take"`
	// Ordem de stop-loss registrada na Binance (modos real e teste). 0 = nenhuma.
	StopOrderID    int64   `json:"stopOrderId,omitempty"`
	StopOrderPrice float64 `json:"stopOrderPrice,omitempty"`
	// Maior fechamento desde a compra (para o stop móvel) e "stop precisa ser recalculado" (ajustes mudaram).
	Highest   float64 `json:"highest,omitempty"`
	StopDirty bool    `json:"stopDirty,omitempty"`
}

type LogLine struct {
	T    int64  `json:"t"`
	Msg  string `json:"msg"`
	Kind string `json:"kind"` // info | ok | warn | err | trade | sale
}

type State struct {
	Running        bool      `json:"running"`
	PaperQuote     float64   `json:"paperQuote"`
	PaperBase      float64   `json:"paperBase"`
	PaperSymbol    string    `json:"paperSymbol"`
	Position       *Position `json:"position"`
	Trades         []Trade   `json:"trades"`
	Logs           []LogLine `json:"logs"`
	LastCandle     int64     `json:"lastCandle"`
	DayKey         string    `json:"dayKey"`
	DayStartEquity float64   `json:"dayStartEquity"`
	DayBlocked     bool      `json:"dayBlocked"`
	LastAlive      int64     `json:"lastAlive"`   // última checagem feita (para saber se o robô ficou fora do ar)
	PendingSell    bool      `json:"pendingSell"` // veio sinal de venda mas a venda falhou: tenta de novo a cada checagem
}

// Live = o que o painel mostra e não precisa ser salvo.
type Live struct {
	Price     float64    `json:"price"`
	Equity    float64    `json:"equity"`
	QuoteBal  float64    `json:"quoteBal"`
	BaseBal   float64    `json:"baseBal"`
	Base      string     `json:"base"`
	Quote     string     `json:"quote"`
	Info      []InfoTile `json:"info"`
	Updated   int64      `json:"updated"`
	LastError string     `json:"lastError"`
	Candles   []Kline    `json:"-"`
	NextCheck int64      `json:"nextCheck"`
}

type Bot struct {
	mu   sync.Mutex
	cfg  Config
	st   State
	live Live
	bn   *Binance // Binance do modo atual (normal ou de teste)
	data *Binance // Binance normal, só dados públicos (backtest)
	info *SymbolInfo
	prep *Prepared // indicadores da última checagem (usados na compra: tamanho e stop)
	last int       // índice da última vela fechada em prep
	wake chan struct{}
	dir  string

	notify      *Notifier
	tgMu        sync.Mutex
	tgToken     string
	tgChat      string
	httpc       *http.Client
	stopRetryAt time.Time
	lastErrMsg  string
	lastErrAt   time.Time
}

func NewBot(dir string) *Bot {
	b := &Bot{dir: dir, cfg: defaultConfig(), wake: make(chan struct{}, 1), data: NewBinance(),
		httpc: &http.Client{Timeout: 10 * time.Second}}
	readJSON(filepath.Join(dir, "config.json"), &b.cfg)
	b.cfg.Params.normalize() // ajustes de versões antigas ganham os campos novos
	if readJSON(filepath.Join(dir, "estado.json"), &b.st) != nil {
		b.st.PaperQuote = b.cfg.PaperStart
	}
	b.bn = newFor(b.cfg.Mode)
	b.bn.Key, b.bn.Secret = b.cfg.keys(b.cfg.Mode)
	b.tgToken, b.tgChat = b.cfg.TelegramToken, b.cfg.TelegramChat
	b.notify = NewNotifier(func() (string, string) {
		b.tgMu.Lock()
		defer b.tgMu.Unlock()
		return b.tgToken, b.tgChat
	})
	return b
}

func readJSON(p string, v any) error {
	data, err := os.ReadFile(p)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, v)
}

func writeJSON(p string, v any) {
	data, _ := json.MarshalIndent(v, "", "  ")
	tmp := p + ".tmp"
	if err := os.WriteFile(tmp, data, 0o600); err == nil {
		_ = os.Rename(tmp, p)
	}
}

func (b *Bot) saveLocked() {
	writeJSON(filepath.Join(b.dir, "estado.json"), b.st)
}

func (b *Bot) saveConfigLocked() {
	writeJSON(filepath.Join(b.dir, "config.json"), b.cfg)
}

// logf grava no registro. Avisos (warn) e erros (err) também vão para o Telegram.
func (b *Bot) logf(kind, format string, a ...any) {
	msg := fmt.Sprintf(format, a...)
	now := time.Now().UnixMilli()
	// a mesma mensagem repetida em seguida (ex.: tentando de novo a cada checagem) vira uma linha só
	if n := len(b.st.Logs); n > 0 && b.st.Logs[n-1].Msg == msg && now-b.st.Logs[n-1].T < 30*60*1000 {
		b.st.Logs[n-1].T = now
		return
	}
	log.Printf("[%s] %s", kind, msg)
	b.st.Logs = append(b.st.Logs, LogLine{T: now, Msg: msg, Kind: kind})
	if len(b.st.Logs) > 300 {
		b.st.Logs = b.st.Logs[len(b.st.Logs)-300:]
	}
	switch kind {
	case "warn":
		b.alert("⚠️ " + msg)
	case "err":
		// o mesmo erro só é avisado de novo depois de 30 minutos
		if msg != b.lastErrMsg || time.Since(b.lastErrAt) > 30*time.Minute {
			b.lastErrMsg, b.lastErrAt = msg, time.Now()
			b.alert("❌ " + msg)
		}
	}
}

func (b *Bot) alert(msg string) {
	b.notify.Send("TradeMartins: " + msg)
}

// applyPlatformLocked: enquanto o robô está ligado, ele abre junto com o Windows e impede o PC de dormir.
func (b *Bot) applyPlatformLocked() {
	if err := setAutostart(b.st.Running && !b.cfg.NoAutostart); err != nil {
		log.Printf("início automático: %v", err)
	}
	keepAwake(b.st.Running)
}

// checkKeyLocked confere as permissões da chave da Binance normal. Devolve erro se não for seguro operar.
func (b *Bot) checkKeyLocked() error {
	if b.cfg.Mode != "real" {
		return nil
	}
	perms, err := b.bn.APIRestrictions()
	if err != nil {
		if ae, ok := err.(*APIError); ok && (ae.Code == -2014 || ae.Code == -2015 || ae.Code == -1022) {
			return err
		}
		b.logf("warn", "Não consegui conferir as permissões da chave da Binance (%v). Confira na Binance que ela NÃO permite saques.", err)
		return nil
	}
	if msg := perms.problem(); msg != "" {
		return fmt.Errorf("%s", msg)
	}
	return nil
}

func (b *Bot) poke() {
	select {
	case b.wake <- struct{}{}:
	default:
	}
}

// Run é o laço principal: confere o mercado a cada CheckSeconds.
func (b *Bot) Run() {
	b.mu.Lock()
	b.resumeLocked()
	b.mu.Unlock()
	for {
		b.tick()
		b.mu.Lock()
		wait := time.Duration(b.cfg.CheckSeconds) * time.Second
		if wait < 5*time.Second {
			wait = 5 * time.Second
		}
		b.live.NextCheck = time.Now().Add(wait).UnixMilli()
		b.mu.Unlock()
		select {
		case <-time.After(wait):
		case <-b.wake:
		}
	}
}

func fmtDur(d time.Duration) string {
	if d < time.Hour {
		return fmt.Sprintf("%d min", int(d.Minutes()))
	}
	if d < 48*time.Hour {
		return fmt.Sprintf("%dh%02d", int(d.Hours()), int(d.Minutes())%60)
	}
	return fmt.Sprintf("%d dias", int(d.Hours()/24))
}

func (b *Bot) tick() {
	b.mu.Lock()
	cfg, bn, info := b.cfg, b.bn, b.info
	b.mu.Unlock()

	if info == nil || info.Symbol != cfg.Symbol {
		var err error
		if info, err = bn.Symbol(cfg.Symbol); err != nil {
			b.setErr(err)
			return
		}
		b.mu.Lock()
		if b.bn != bn { // o modo mudou no meio do caminho
			b.mu.Unlock()
			return
		}
		b.info = info
		b.mu.Unlock()
	}

	// Sempre 1000 velas (o máximo da Binance): a EMA "lembra" de onde começou, e com poucas velas
	// a média longa (200) ficava até 0,4% diferente da calculada com o histórico todo (e do backtest).
	ks, err := bn.Klines(cfg.Symbol, cfg.Interval, 1000, 0)
	if err != nil {
		b.setErr(err)
		return
	}
	if len(ks) < 3 {
		b.setErr(fmt.Errorf("poucos dados de preço"))
		return
	}
	price := ks[len(ks)-1].C // vela atual (ainda aberta) = preço agora
	closed := ks[:len(ks)-1]
	pr := cfg.Params.prepare(closed, intervalDur(cfg.Interval))
	last := len(closed) - 1

	// saldo
	var quoteBal, baseBal float64
	balErr := false
	if isExchange(cfg.Mode) {
		_, total, err := bn.Balances()
		if err != nil {
			b.setErr(err)
			balErr = true
		} else {
			quoteBal, baseBal = total[info.Quote], total[info.Base]
		}
	}

	b.mu.Lock()
	defer b.mu.Unlock()
	if b.bn != bn || b.cfg.Symbol != cfg.Symbol {
		return // ajustes mudaram durante a checagem; a próxima usa os novos
	}
	if !isExchange(cfg.Mode) {
		quoteBal, baseBal = b.st.PaperQuote, b.st.PaperBase
	}
	b.live.Price, b.live.QuoteBal, b.live.BaseBal = price, quoteBal, baseBal
	b.live.Base, b.live.Quote = info.Base, info.Quote
	b.live.Equity = quoteBal + baseBal*price
	b.live.Candles = ks
	b.live.Info = pr.info(last)
	b.prep, b.last = pr, last
	if b.cfg.Params != cfg.Params { // ajustes mudaram durante a checagem: não decide com indicadores velhos
		b.prep = nil
		return
	}
	b.updateStopLocked(closed, pr, last)
	b.live.Updated = time.Now().UnixMilli()
	if !balErr {
		b.live.LastError = ""
	}

	// limite de perda do dia
	today := dayKey(time.Now())
	if b.st.DayKey != today && !balErr {
		b.st.DayKey, b.st.DayStartEquity, b.st.DayBlocked = today, b.live.Equity, false
	}

	// O stop na Binance é acompanhado mesmo com o robô desligado.
	if p := b.st.Position; p != nil && isExchange(p.Mode) && p.Mode == cfg.Mode && p.Symbol == cfg.Symbol {
		b.syncStopLocked(price)
	}

	if !b.st.Running {
		b.saveLocked()
		return
	}
	b.st.LastAlive = time.Now().UnixMilli()
	if cfg.PingURL != "" {
		go ping(b.httpc, cfg.PingURL)
	}
	if cfg.MaxDailyLossPct > 0 && !b.st.DayBlocked && b.st.DayStartEquity > 0 && !balErr &&
		b.live.Equity < b.st.DayStartEquity*(1-cfg.MaxDailyLossPct/100) {
		b.st.DayBlocked = true
		b.logf("warn", "Perda do dia passou de %.1f%%. Sem novas compras até amanhã.", cfg.MaxDailyLossPct)
	}

	if pos := b.st.Position; pos != nil && pos.Symbol == cfg.Symbol && pos.Mode == cfg.Mode {
		switch {
		case pos.StopOrderID == 0 && pos.Stop > 0 && price <= pos.Stop:
			// sem ordem na Binance (modo simulado ou falha ao registrar): o próprio robô vende
			b.sellLocked(price, "stop-loss")
		case pos.Take > 0 && price >= pos.Take:
			b.sellLocked(price, "take-profit")
		}
	}

	// venda pelo sinal que falhou antes: tenta de novo (mesmo que outra vela já tenha fechado)
	if b.st.PendingSell {
		if b.st.Position != nil {
			b.sellLocked(price, "sinal")
		}
		b.st.PendingSell = b.st.Position != nil
	}

	if closed[last].OpenTime != b.st.LastCandle {
		b.st.LastCandle = closed[last].OpenTime
		sig := pr.signal(last, b.st.Position != nil)
		switch {
		case sig == sigBuy && b.st.Position == nil:
			if b.st.DayBlocked {
				b.logf("info", "Sinal de compra ignorado: limite de perda do dia atingido.")
			} else if retry := b.buyLocked(price); retry {
				// a compra não chegou a ser feita (falha de conexão): tenta de novo enquanto esta vela valer
				b.st.LastCandle = 0
			}
		case sig == sigSell && b.st.Position != nil:
			b.sellLocked(price, "sinal")
			b.st.PendingSell = b.st.Position != nil
		}
	}
	b.saveLocked()
}

func nz(v float64) float64 {
	if math.IsNaN(v) || math.IsInf(v, 0) {
		return 0
	}
	return v
}

func (b *Bot) setErr(err error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	msg := err.Error()
	if b.live.LastError != msg {
		b.logf("err", "%s", msg)
	}
	b.live.LastError = msg
	b.live.Updated = time.Now().UnixMilli()
}

// buyLocked compra. Devolve retry=true quando a compra com certeza NÃO foi feita por falha de conexão
// (pode tentar de novo sem risco de comprar duas vezes).
func (b *Bot) buyLocked(price float64) (retry bool) {
	cfg := b.cfg
	info := b.info
	now := time.Now().UnixMilli()
	pos := &Position{Symbol: cfg.Symbol, Mode: cfg.Mode, Time: now}

	if isExchange(cfg.Mode) {
		free, _, err := b.bn.Balances()
		if err != nil {
			b.logf("err", "Compra não feita: %v. Tento de novo na próxima checagem.", err)
			_, refused := err.(*APIError)
			return !refused
		}
		spend := free[info.Quote] * b.sizeFractionLocked()
		if cfg.MaxBuy > 0 && spend > cfg.MaxBuy {
			spend = cfg.MaxBuy
		}
		if spend < info.MinNotional*1.02 || spend <= 0 {
			b.logf("err", "Compra cancelada: saldo de %s pequeno demais (mínimo da Binance: %.2f).", info.Quote, info.MinNotional)
			return false
		}
		r, err := b.marketOrderLocked(cfg.Symbol, "BUY", "", floorDec(spend, info.QuotePrecision))
		if err != nil {
			if ne, ok := err.(*notExecutedErr); ok && ne.network {
				b.logf("err", "Compra não feita: %v. Tento de novo na próxima checagem.", ne.err)
				return true
			}
			b.logf("err", "Compra recusada: %v", err)
			return false
		}
		pos.Qty = r.ExecutedQty - r.Commission[info.Base]
		pos.Cost = r.QuoteQty + r.Commission[info.Quote] + b.otherFeesLocked(r.Commission)
		pos.Entry = r.QuoteQty / r.ExecutedQty
	} else {
		spend := b.st.PaperQuote * b.sizeFractionLocked()
		if cfg.MaxBuy > 0 && spend > cfg.MaxBuy {
			spend = cfg.MaxBuy
		}
		if spend < 1 {
			b.logf("err", "Compra cancelada: saldo simulado acabou. Use \"Zerar simulação\".")
			return false
		}
		fee := spend * paperFeePct / 100
		pos.Qty = (spend - fee) / price
		pos.Cost = spend
		pos.Entry = price
		b.st.PaperQuote -= spend
		b.st.PaperBase += pos.Qty
		b.st.PaperSymbol = cfg.Symbol
	}
	pos.Stop, pos.Take, pos.Highest = b.entryStopLocked(pos.Entry), cfg.Params.take(pos.Entry), pos.Entry
	b.st.Position = pos
	b.st.Trades = append(b.st.Trades, Trade{Time: now, Side: "COMPRA", Symbol: cfg.Symbol, Mode: cfg.Mode,
		Price: pos.Entry, Qty: pos.Qty, Quote: pos.Cost, Reason: "sinal"})
	b.logf("trade", "COMPRA %s %s a %s (%s %.2f)%s", fmtQty(pos.Qty), info.Base, fmtPrice(pos.Entry), info.Quote, pos.Cost, modeTag(cfg.Mode))
	msg := fmt.Sprintf("🟢 COMPRA%s\n%s %s a %s\nValor: %.2f %s", modeTag(cfg.Mode), fmtQty(pos.Qty), info.Base, fmtPrice(pos.Entry), pos.Cost, info.Quote)
	if pos.Stop > 0 {
		msg += "\nStop-loss: " + fmtPrice(pos.Stop)
	}
	if pos.Take > 0 {
		msg += "\nTake-profit: " + fmtPrice(pos.Take)
	}
	b.alert(msg)
	b.saveLocked()
	if isExchange(cfg.Mode) {
		b.stopRetryAt = time.Time{}
		b.syncStopLocked(price) // registra o stop na Binance na hora
	}
	return false
}

// sellLocked vende a posição inteira a preço de mercado.
func (b *Bot) sellLocked(price float64, reason string) {
	pos := b.st.Position
	if pos == nil {
		return
	}
	if isExchange(pos.Mode) {
		b.sellExchangeLocked(price, reason)
		return
	}
	qty := pos.Qty
	gross := qty * price
	proceeds := gross - gross*paperFeePct/100
	b.st.PaperBase -= qty
	if b.st.PaperBase < 1e-12 {
		b.st.PaperBase = 0
	}
	b.st.PaperQuote += proceeds
	b.recordSaleLocked(qty, proceeds, price, reason, true)
}

// recordSaleLocked registra uma venda. closeAll=false = venda parcial (parte da posição continua aberta).
func (b *Bot) recordSaleLocked(qty, proceeds, avg float64, reason string, closeAll bool) {
	pos := b.st.Position
	if pos == nil || qty <= 0 {
		return
	}
	frac := math.Min(qty/pos.Qty, 1)
	if frac > 0.995 {
		closeAll = true
	}
	cost := pos.Cost * frac
	if closeAll {
		cost = pos.Cost
	}
	pnl := proceeds - cost
	t := Trade{Time: time.Now().UnixMilli(), Side: "VENDA", Symbol: pos.Symbol, Mode: pos.Mode, Price: avg, Qty: qty, Quote: proceeds,
		Reason: reason, PnL: pnl, EntryTime: pos.Time, EntryPrice: pos.Entry}
	if cost > 0 {
		t.PnLPct = pnl / cost * 100
	}
	b.st.Trades = append(b.st.Trades, t)
	if len(b.st.Trades) > 2000 {
		b.st.Trades = b.st.Trades[len(b.st.Trades)-2000:]
	}
	base, quote := pos.Symbol, ""
	if b.info != nil {
		base, quote = b.info.Base, b.info.Quote
	}
	part := ""
	if closeAll {
		b.st.Position = nil
	} else {
		pos.Qty -= qty
		pos.Cost -= cost
		part = " (parcial)"
	}
	b.logf("sale", "VENDA%s (%s) %s %s a %s → resultado %+.2f %s (%+.2f%%)%s", part, reason, fmtQty(qty), base, fmtPrice(avg), pnl, quote, t.PnLPct, modeTag(pos.Mode))
	icon := "✅"
	if pnl < 0 {
		icon = "🔻"
	}
	b.alert(fmt.Sprintf("%s VENDA%s%s — %s\n%s %s a %s\nResultado: %+.2f %s (%+.2f%%)", icon, part, modeTag(pos.Mode), reason, fmtQty(qty), base, fmtPrice(avg), pnl, quote, t.PnLPct))
	b.saveLocked()
}

func modeTag(m string) string {
	switch m {
	case "real":
		return " [REAL]"
	case "teste":
		return " [Binance de teste]"
	}
	return " [simulado]"
}

func fmtQty(v float64) string {
	s := fmt.Sprintf("%.8f", v)
	s = strings.TrimRight(strings.TrimRight(s, "0"), ".")
	return s
}

func fmtPrice(v float64) string {
	switch {
	case v >= 100:
		return fmt.Sprintf("%.2f", v)
	case v >= 1:
		return fmt.Sprintf("%.4f", v)
	default:
		return fmt.Sprintf("%.8f", v)
	}
}

// ---------- ações do painel ----------

func (b *Bot) Start(confirmReal bool) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.st.Running {
		return nil
	}
	if isExchange(b.cfg.Mode) {
		if k, s := b.cfg.keys(b.cfg.Mode); k == "" || s == "" {
			if b.cfg.Mode == "teste" {
				return fmt.Errorf("configure a chave da Binance de TESTE (testnet.binance.vision) em Ajustes")
			}
			return fmt.Errorf("configure a chave de API da Binance antes de usar o modo real")
		}
	}
	if b.cfg.Mode == "real" && b.cfg.MaxBuy <= 0 {
		return fmt.Errorf("no modo real, defina o \"valor máximo por compra\" (Ajustes › Risco) antes de ligar o robô")
	}
	if b.cfg.Mode == "real" && !confirmReal {
		return fmt.Errorf("confirme que quer operar com dinheiro real")
	}
	if b.st.Position != nil && (b.st.Position.Symbol != b.cfg.Symbol || b.st.Position.Mode != b.cfg.Mode) {
		return fmt.Errorf("existe uma posição aberta em %s (%s). Volte para esse par/modo ou feche a posição", b.st.Position.Symbol, b.st.Position.Mode)
	}
	if err := b.checkKeyLocked(); err != nil {
		return err
	}
	b.st.Running = true
	b.st.PendingSell = b.st.PendingSell && b.st.Position != nil
	b.applyPlatformLocked()
	b.st.LastCandle = 0
	b.st.LastAlive = time.Now().UnixMilli()
	b.logf("ok", "Robô LIGADO: %s, velas de %s, modo %s.", b.cfg.Symbol, b.cfg.Interval, strings.ToUpper(b.cfg.Mode))
	b.alert(fmt.Sprintf("▶️ Robô ligado: %s, velas de %s%s", b.cfg.Symbol, b.cfg.Interval, modeTag(b.cfg.Mode)))
	b.saveLocked()
	go b.poke()
	return nil
}

func (b *Bot) Stop() {
	b.mu.Lock()
	defer b.mu.Unlock()
	if !b.st.Running {
		return
	}
	b.st.Running = false
	b.applyPlatformLocked()
	msg := "Robô DESLIGADO."
	if p := b.st.Position; p != nil {
		if p.StopOrderID != 0 {
			msg += " A posição continua aberta e o stop-loss continua registrado na Binance."
		} else {
			msg += " A posição continua aberta, sem stop vigiado enquanto o robô estiver desligado."
		}
	}
	b.logf("info", "%s", msg)
	b.alert("⏸️ " + msg)
	b.saveLocked()
}

func (b *Bot) SellNow() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.st.Position == nil {
		return fmt.Errorf("não há posição aberta")
	}
	if b.info == nil || b.live.Price == 0 || b.info.Symbol != b.st.Position.Symbol {
		return fmt.Errorf("aguarde o preço atualizar e tente de novo")
	}
	b.sellLocked(b.live.Price, "manual")
	if b.st.Position != nil {
		return fmt.Errorf("a venda não foi feita; veja o registro")
	}
	return nil
}

func (b *Bot) ResetPaper() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.st.Position != nil && b.st.Position.Mode == "simulado" {
		b.st.Position = nil
	}
	b.st.PaperQuote, b.st.PaperBase = b.cfg.PaperStart, 0
	kept := b.st.Trades[:0]
	for _, t := range b.st.Trades {
		if t.Mode != "simulado" && t.Mode != "" {
			kept = append(kept, t)
		}
	}
	b.st.Trades = kept
	b.st.DayKey = ""
	b.logf("info", "Simulação zerada: saldo de %.2f.", b.cfg.PaperStart)
	b.saveLocked()
	go b.poke()
	return nil
}

// keep: texto vazio = manter o valor atual; "-" = apagar.
func keep(newV, oldV string) string {
	switch strings.TrimSpace(newV) {
	case "":
		return oldV
	case "-":
		return ""
	}
	return strings.TrimSpace(newV)
}

func (b *Bot) UpdateConfig(nc Config) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	nc.Symbol = strings.ToUpper(strings.TrimSpace(nc.Symbol))
	nc.PingURL = strings.TrimSpace(nc.PingURL)
	nc.TelegramChat = strings.TrimSpace(nc.TelegramChat)
	nc.Params.normalize()
	switch {
	case nc.Symbol == "":
		return fmt.Errorf("informe o par (ex.: BTCUSDT)")
	case nc.TradePct <= 0 || nc.TradePct > 100:
		return fmt.Errorf("o tamanho da compra precisa ficar entre 1%% e 100%% do saldo")
	case nc.Mode != "simulado" && nc.Mode != "teste" && nc.Mode != "real":
		return fmt.Errorf("modo inválido")
	case nc.MaxBuy < 0:
		return fmt.Errorf("valor máximo por compra inválido")
	case nc.PaperStart <= 0:
		return fmt.Errorf("saldo inicial da simulação inválido")
	case nc.PingURL != "" && nc.PingURL != "-" && !validPingURL(nc.PingURL):
		return fmt.Errorf("o endereço de monitoramento precisa começar com https://")
	}
	if _, ok := map[string]bool{"1m": true, "3m": true, "5m": true, "15m": true, "30m": true, "1h": true, "2h": true, "4h": true, "6h": true, "8h": true, "12h": true, "1d": true}[nc.Interval]; !ok {
		return fmt.Errorf("intervalo inválido")
	}
	if err := nc.Params.validate(intervalDur(nc.Interval), true); err != nil {
		return err
	}
	if nc.CheckSeconds < 5 {
		nc.CheckSeconds = 5
	}
	if b.st.Running && (nc.Symbol != b.cfg.Symbol || nc.Mode != b.cfg.Mode) {
		return fmt.Errorf("desligue o robô antes de trocar o par ou o modo")
	}
	if b.st.Position != nil && (nc.Symbol != b.st.Position.Symbol || nc.Mode != b.st.Position.Mode) {
		return fmt.Errorf("feche a posição aberta em %s antes de trocar o par ou o modo", b.st.Position.Symbol)
	}
	nc.APIKey, nc.APISecret = keep(nc.APIKey, b.cfg.APIKey), keep(nc.APISecret, b.cfg.APISecret)
	nc.TestAPIKey, nc.TestAPISecret = keep(nc.TestAPIKey, b.cfg.TestAPIKey), keep(nc.TestAPISecret, b.cfg.TestAPISecret)
	nc.TelegramToken = keep(nc.TelegramToken, b.cfg.TelegramToken)
	if nc.PingURL == "-" {
		nc.PingURL = ""
	}
	if nc.Symbol != b.cfg.Symbol || nc.Mode != b.cfg.Mode {
		b.info = nil
		if b.st.Position == nil && b.st.PaperBase > 0 {
			b.st.PaperBase = 0 // resíduo do par anterior
		}
	}
	if nc.Mode != b.cfg.Mode {
		b.bn = newFor(nc.Mode)
		b.live = Live{}
		b.st.DayKey = ""
	}
	b.bn.Key, b.bn.Secret = nc.keys(nc.Mode)
	if b.cfg.Params != nc.Params && b.st.Position != nil {
		pos := b.st.Position
		if nc.Params.StopMode == "pct" {
			// dá para recalcular já (não precisa do ATR)
			pos.Stop = math.Max(pctStop(nc.Params, pos.Entry), (&Prepared{p: nc.Params}).trailStop(-1, math.Max(pos.Highest, pos.Entry)))
			pos.StopDirty = false
		} else {
			pos.StopDirty = true // stop por ATR: recalcula na próxima checagem, com as velas
		}
		pos.Take = nc.Params.take(pos.Entry)
		b.stopRetryAt = time.Time{} // a ordem de stop na Binance é atualizada na próxima checagem
		b.prep = nil
	}
	b.cfg = nc
	b.tgMu.Lock()
	b.tgToken, b.tgChat = nc.TelegramToken, nc.TelegramChat
	b.tgMu.Unlock()
	b.saveConfigLocked()
	b.saveLocked()
	b.applyPlatformLocked()
	b.logf("info", "Ajustes salvos (%s, %s, modo %s).", nc.Symbol, nc.Interval, nc.Mode)
	go b.poke()
	return nil
}

// Quit fecha o programa para a versão nova assumir. Espera qualquer ordem em andamento terminar
// (pega o cadeado) e não solta mais, para nada novo começar.
func (b *Bot) Quit() {
	b.mu.Lock()
	b.logf("info", "Fechando para abrir a versão nova…")
	b.saveLocked()
	go func() {
		time.Sleep(300 * time.Millisecond)
		os.Exit(0)
	}()
}

// resumeLocked: ao abrir o programa com o robô ligado, avisa se ele ficou fora do ar e retoma
// (no modo real, só se a chave continuar segura).
func (b *Bot) resumeLocked() {
	if !b.st.Running {
		return
	}
	wait := time.Duration(b.cfg.CheckSeconds) * time.Second
	gap := time.Since(time.UnixMilli(b.st.LastAlive))
	if b.st.LastAlive > 0 && gap > 3*wait+90*time.Second {
		msg := fmt.Sprintf("O robô ficou FORA DO AR por %s (desde %s).", fmtDur(gap), time.UnixMilli(b.st.LastAlive).Format("02/01 15:04"))
		if p := b.st.Position; p != nil {
			if p.StopOrderID != 0 {
				msg += " O stop-loss continuou registrado na Binance."
			} else {
				msg += " A posição aberta ficou sem stop vigiado."
			}
		}
		b.logf("warn", "%s", msg)
	}
	if err := b.checkKeyLocked(); err != nil {
		b.st.Running = false
		b.applyPlatformLocked()
		b.logf("err", "Robô NÃO foi retomado: %v", err)
		b.saveLocked()
	} else {
		b.logf("info", "Robô retomado automaticamente (versão %s: %s, %s, modo %s).", version, b.cfg.Symbol, b.cfg.Interval, b.cfg.Mode)
		b.alert(fmt.Sprintf("▶️ Robô retomado (versão %s): %s, velas de %s%s", version, b.cfg.Symbol, b.cfg.Interval, modeTag(b.cfg.Mode)))
	}
}

// sizeFractionLocked = que fração do saldo usar na compra (volatilidade-alvo ou % fixo).
func (b *Bot) sizeFractionLocked() float64 {
	max := b.cfg.TradePct / 100
	if b.prep == nil {
		return max
	}
	return b.prep.fraction(b.last, max)
}

// entryStopLocked = stop logo depois da compra (por % ou por ATR, com os indicadores da última checagem).
func (b *Bot) entryStopLocked(entry float64) float64 {
	if b.prep == nil {
		return pctStop(b.cfg.Params, entry)
	}
	return b.prep.initialStop(b.last, entry)
}

// updateStopLocked: stop móvel (sobe com o maior fechamento desde a compra) e recálculo depois de mudar ajustes.
// Roda a cada checagem; o stop nunca desce, a não ser que os ajustes tenham mudado.
func (b *Bot) updateStopLocked(closed []Kline, pr *Prepared, last int) {
	pos := b.st.Position
	if pos == nil || pos.Symbol != b.cfg.Symbol || pos.Mode != b.cfg.Mode {
		return
	}
	// maior fechamento desde a compra (vale também depois de o robô ficar fora do ar)
	for i := len(closed) - 1; i >= 0 && closed[i].OpenTime >= pos.Time-int64(intervalDur(b.cfg.Interval)/time.Millisecond); i-- {
		if closed[i].CloseTime > pos.Time && closed[i].C > pos.Highest {
			pos.Highest = closed[i].C
		}
	}
	old := pos.Stop
	if pos.StopDirty {
		pos.Stop = pr.initialStop(last, pos.Entry)
		pos.StopDirty = false
	}
	if ts := pr.trailStop(last, math.Max(pos.Highest, pos.Entry)); ts > pos.Stop {
		pos.Stop = ts
	}
	if pos.Stop != old {
		b.logf("info", "Stop-loss ajustado: %s → %s.", fmtPrice(old), fmtPrice(pos.Stop))
		b.stopRetryAt = time.Time{}
	}
}
