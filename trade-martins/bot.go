package main

// O robô ao vivo: uma carteira de um ou mais pares (ex.: BTCUSDT, ETHUSDT, SOLUSDT) com o mesmo dinheiro.
// Cada par tem sua posição, seus sinais, seu stop (registrado na Binance nos modos real/teste) e seu
// stop móvel. Cada compra usa no máximo patrimônio ÷ número de pares. Em cada checagem: primeiro as vendas
// (liberam dinheiro), depois as compras — igual ao backtest de carteira do laboratório.

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

const maxPairs = 12

type Config struct {
	Symbol          string   `json:"symbol"`  // o primeiro par (versões antigas só tinham este)
	Symbols         []string `json:"symbols"` // a carteira
	Interval        string   `json:"interval"`
	Params          Params   `json:"params"`
	TradePct        float64  `json:"tradePct"`        // % máximo da parte de cada par usado numa compra
	MaxBuy          float64  `json:"maxBuy"`          // valor máximo por compra (em USDT/BRL…); 0 = sem limite
	MaxDailyLossPct float64  `json:"maxDailyLossPct"` // 0 = sem limite
	CheckSeconds    int      `json:"checkSeconds"`
	Mode            string   `json:"mode"` // "simulado" | "teste" (Binance de teste) | "real"
	PaperStart      float64  `json:"paperStart"`
	APIKey          string   `json:"apiKey"`
	APISecret       string   `json:"apiSecret"`
	TestAPIKey      string   `json:"testApiKey"`
	TestAPISecret   string   `json:"testApiSecret"`
	TelegramToken   string   `json:"telegramToken"`
	TelegramChat    string   `json:"telegramChat"`
	PingURL         string   `json:"pingUrl"`
	NoAutostart     bool     `json:"noAutostart"` // true = não abrir junto com o Windows
}

func defaultConfig() Config {
	return Config{
		Symbol: "BTCUSDT", Interval: "4h", // Symbols vem do arquivo ou vira [Symbol] (veja NewBot)
		Params:   defaultParams(),
		TradePct: 95, MaxDailyLossPct: 5, CheckSeconds: 30,
		Mode: "simulado", PaperStart: 1000,
	}
}

// pairs = os pares da carteira, na ordem.
func (c Config) pairs() []string {
	if len(c.Symbols) > 0 {
		return c.Symbols
	}
	if c.Symbol != "" {
		return []string{c.Symbol}
	}
	return nil
}

func (c Config) hasPair(s string) bool {
	for _, p := range c.pairs() {
		if p == s {
			return true
		}
	}
	return false
}

// isExchange: o modo manda ordens de verdade para uma Binance (a real ou a de teste).
func isExchange(mode string) bool { return mode == "real" || mode == "teste" }

func (c Config) keys(mode string) (string, string) {
	if mode == "teste" {
		return c.TestAPIKey, c.TestAPISecret
	}
	return c.APIKey, c.APISecret
}

var knownQuotes = []string{"FDUSD", "USDT", "USDC", "BRL", "EUR", "TRY", "BTC", "ETH", "BNB"}

// quoteOf adivinha a moeda de cotação pelo nome do par (BTCUSDT → USDT). "" = desconhecida.
func quoteOf(sym string) string {
	for _, q := range knownQuotes {
		if strings.HasSuffix(sym, q) && len(sym) > len(q) {
			return q
		}
	}
	return ""
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
	Running        bool                 `json:"running"`
	PaperQuote     float64              `json:"paperQuote"`
	PaperBases     map[string]float64   `json:"paperBases"` // cripto simulada de cada par
	Positions      map[string]*Position `json:"positions"`
	Trades         []Trade              `json:"trades"`
	Logs           []LogLine            `json:"logs"`
	LastCandles    map[string]int64     `json:"lastCandles"` // última vela avaliada de cada par
	DayKey         string               `json:"dayKey"`
	DayStartEquity float64              `json:"dayStartEquity"`
	DayBlocked     bool                 `json:"dayBlocked"`
	LastAlive      int64                `json:"lastAlive"`    // última checagem (para saber se o robô ficou fora do ar)
	PendingSells   map[string]bool      `json:"pendingSells"` // sinal de venda cuja venda falhou: tenta de novo

	// campos das versões até 2.0 (um par só); convertidos em migrate()
	OldPosition    *Position `json:"position,omitempty"`
	OldPaperBase   float64   `json:"paperBase,omitempty"`
	OldPaperSymbol string    `json:"paperSymbol,omitempty"`
	OldLastCandle  int64     `json:"lastCandle,omitempty"`
	OldPendingSell bool      `json:"pendingSell,omitempty"`
}

func (s *State) migrate() {
	if s.Positions == nil {
		s.Positions = map[string]*Position{}
	}
	if s.PaperBases == nil {
		s.PaperBases = map[string]float64{}
	}
	if s.LastCandles == nil {
		s.LastCandles = map[string]int64{}
	}
	if s.PendingSells == nil {
		s.PendingSells = map[string]bool{}
	}
	if p := s.OldPosition; p != nil {
		s.Positions[p.Symbol] = p
		if s.OldPendingSell {
			s.PendingSells[p.Symbol] = true
		}
		s.LastCandles[p.Symbol] = s.OldLastCandle
	}
	if s.OldPaperBase > 0 && s.OldPaperSymbol != "" {
		s.PaperBases[s.OldPaperSymbol] += s.OldPaperBase
	}
	s.OldPosition, s.OldPaperBase, s.OldPaperSymbol, s.OldLastCandle, s.OldPendingSell = nil, 0, "", 0, false
}

// PairLive = o que o painel mostra de cada par (não é salvo).
type PairLive struct {
	Symbol    string     `json:"symbol"`
	Base      string     `json:"base"`
	Price     float64    `json:"price"`
	ChangePct float64    `json:"changePct"` // variação nas últimas 24h
	Info      []InfoTile `json:"info"`
	Updated   int64      `json:"updated"`
	LastError string     `json:"lastError"`
	Candles   []Kline    `json:"-"`
}

// Live = o que o painel mostra da carteira (não é salvo).
type Live struct {
	Equity    float64              `json:"equity"`
	QuoteBal  float64              `json:"quoteBal"`
	Quote     string               `json:"quote"`
	Updated   int64                `json:"updated"`
	LastError string               `json:"lastError"`
	NextCheck int64                `json:"nextCheck"`
	Pairs     map[string]*PairLive `json:"-"`
}

type Bot struct {
	mu    sync.Mutex
	cfg   Config
	st    State
	live  Live
	bn    *Binance // Binance do modo atual (normal ou de teste)
	data  *Binance // Binance normal, só dados públicos (laboratório)
	infos map[string]*SymbolInfo
	preps map[string]*Prepared // indicadores da última checagem de cada par
	lasts map[string]int       // índice da última vela fechada em preps
	mkt   []Kline              // velas diárias fechadas do BTC (filtro de mercado), da última checagem que deu certo
	wake  chan struct{}
	dir   string

	notify     *Notifier
	tgMu       sync.Mutex
	tgToken    string
	tgChat     string
	httpc      *http.Client
	stopRetry  map[string]time.Time
	lastErrMsg string
	lastErrAt  time.Time
}

func NewBot(dir string) *Bot {
	b := &Bot{dir: dir, cfg: defaultConfig(), wake: make(chan struct{}, 1), data: NewBinance(),
		httpc: &http.Client{Timeout: 10 * time.Second}, infos: map[string]*SymbolInfo{}, preps: map[string]*Prepared{},
		lasts: map[string]int{}, stopRetry: map[string]time.Time{}}
	readJSON(filepath.Join(dir, "config.json"), &b.cfg)
	b.cfg.Params.normalize() // ajustes de versões antigas ganham os campos novos
	if len(b.cfg.Symbols) == 0 && b.cfg.Symbol != "" {
		b.cfg.Symbols = []string{b.cfg.Symbol}
	}
	if readJSON(filepath.Join(dir, "estado.json"), &b.st) != nil {
		b.st.PaperQuote = b.cfg.PaperStart
	}
	b.st.migrate()
	b.live.Pairs = map[string]*PairLive{}
	b.bn = newFor(b.cfg.Mode)
	b.bn.Key, b.bn.Secret = b.cfg.keys(b.cfg.Mode)
	b.tgToken, b.tgChat = b.cfg.TelegramToken, b.cfg.TelegramChat
	b.notify = NewNotifier(func() (string, string) {
		b.tgMu.Lock()
		defer b.tgMu.Unlock()
		return b.tgToken, b.tgChat
	})
	// Proteção: posição aberta num par fora da carteira volta para a carteira, para o robô continuar
	// cuidando dela (stop, sinais). Não deveria acontecer, mas se acontecer, ninguém fica sem stop.
	for s, p := range b.st.Positions {
		if p.Mode == b.cfg.Mode && !b.cfg.hasPair(s) {
			b.cfg.Symbols = append(b.cfg.Symbols, s)
			b.logf("warn", "A posição aberta em %s estava fora da lista de pares e foi incluída de volta na carteira.", s)
		}
	}
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

// ---------- a checagem ----------

type pairData struct {
	sym  string
	info *SymbolInfo
	ks   []Kline
	err  error
}

func (b *Bot) tick() {
	b.mu.Lock()
	cfg, bn := b.cfg, b.bn
	pairs := append([]string{}, cfg.pairs()...)
	infos := make([]*SymbolInfo, len(pairs))
	for i, s := range pairs {
		infos[i] = b.infos[s]
	}
	b.mu.Unlock()

	// 1) baixa as velas de cada par (até 4 ao mesmo tempo). Sempre 1000 velas: a EMA "lembra" de onde
	//    começou, e com poucas velas a média longa ficava diferente da do backtest.
	got := make([]pairData, len(pairs))
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)
	for i, s := range pairs {
		wg.Add(1)
		go func(i int, s string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			d := pairData{sym: s, info: infos[i]}
			if d.info == nil {
				if d.info, d.err = bn.Symbol(s); d.err != nil {
					got[i] = d
					return
				}
			}
			d.ks, d.err = bn.Klines(s, cfg.Interval, 1000, 0)
			if d.err == nil && len(d.ks) < 3 {
				d.err = fmt.Errorf("poucos dados de preço")
			}
			got[i] = d
		}(i, s)
	}
	// filtro de mercado: velas diárias do BTC (só as fechadas)
	var mkt []Kline
	var mktErr error
	if days := cfg.Params.MarketDays; days > 0 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			ks, err := bn.Klines(marketSymbol, "1d", min(1000, marketCandles(days)+1), 0)
			now := time.Now().UnixMilli()
			for len(ks) > 0 && ks[len(ks)-1].CloseTime > now {
				ks = ks[:len(ks)-1]
			}
			if err == nil && len(ks) < days {
				err = fmt.Errorf("poucos dias de histórico do BTC (%d)", len(ks))
			}
			mkt, mktErr = ks, err
		}()
	}
	wg.Wait()

	// 2) saldo (uma vez para a carteira toda)
	var total map[string]float64
	var balErr error
	if isExchange(cfg.Mode) {
		_, total, balErr = bn.Balances()
	}

	b.mu.Lock()
	defer b.mu.Unlock()
	if b.bn != bn || b.cfg.Params != cfg.Params || b.cfg.Interval != cfg.Interval || strings.Join(b.cfg.pairs(), ",") != strings.Join(pairs, ",") {
		return // ajustes mudaram durante a checagem; a próxima usa os novos
	}
	now := time.Now().UnixMilli()
	b.live.Updated = now
	b.live.LastError = ""
	if balErr != nil {
		b.live.LastError = balErr.Error()
		b.logf("err", "%s", balErr)
	}

	if cfg.Params.MarketDays > 0 {
		if mktErr != nil {
			// mantém as velas da última checagem; se ficarem velhas (3 dias), o filtro não libera compras
			b.logf("err", "Filtro de mercado: %v", mktErr)
		} else {
			b.mkt = mkt
		}
	}

	dur := intervalDur(cfg.Interval)
	price := map[string]float64{}
	ok := map[string]bool{}
	closedOf := map[string][]Kline{}
	for _, d := range got {
		pl := b.pairLiveLocked(d.sym)
		pl.Updated = now
		if d.err != nil {
			if pl.LastError != d.err.Error() {
				b.logf("err", "%s: %v", d.sym, d.err)
			}
			pl.LastError = d.err.Error()
			delete(b.preps, d.sym)
			continue
		}
		if want := b.quoteLocked(); want != "" && d.info.Quote != want {
			pl.LastError = fmt.Sprintf("a moeda de %s (%s) é diferente da dos outros pares", d.sym, d.info.Quote)
			continue
		}
		pl.LastError = ""
		b.infos[d.sym] = d.info
		closed := d.ks[:len(d.ks)-1]
		p := d.ks[len(d.ks)-1].C // vela atual (ainda aberta) = preço agora
		pr := cfg.Params.prepare(closed, dur)
		pr.applyMarket(b.mkt)
		last := len(closed) - 1
		b.preps[d.sym], b.lasts[d.sym] = pr, last
		pl.Base, pl.Price, pl.Candles, pl.Info = d.info.Base, p, d.ks, pr.info(last)
		if back := int(24 * time.Hour / dur); len(d.ks) > back {
			pl.ChangePct = (p/d.ks[len(d.ks)-1-back].C - 1) * 100
		}
		price[d.sym], ok[d.sym], closedOf[d.sym] = p, true, closed
		b.updateStopLocked(d.sym, closed, pr, last)
	}

	// patrimônio da carteira = dinheiro + posições do robô ao preço de agora
	b.live.Quote = b.quoteLocked()
	if isExchange(cfg.Mode) {
		if balErr == nil {
			b.live.QuoteBal = total[b.live.Quote]
		}
	} else {
		b.live.QuoteBal = b.st.PaperQuote
	}
	b.live.Equity = b.equityLocked(b.live.QuoteBal, nil)

	today := dayKey(time.Now())
	if b.st.DayKey != today && balErr == nil {
		b.st.DayKey, b.st.DayStartEquity, b.st.DayBlocked = today, b.live.Equity, false
	}

	// O stop na Binance é acompanhado mesmo com o robô desligado.
	for _, s := range pairs {
		if p := b.st.Positions[s]; p != nil && ok[s] && isExchange(p.Mode) && p.Mode == cfg.Mode {
			b.syncStopLocked(s, price[s])
		}
	}

	if !b.st.Running {
		b.saveLocked()
		return
	}
	b.st.LastAlive = now
	if cfg.PingURL != "" {
		go ping(b.httpc, cfg.PingURL)
	}
	if cfg.MaxDailyLossPct > 0 && !b.st.DayBlocked && b.st.DayStartEquity > 0 && balErr == nil &&
		b.live.Equity < b.st.DayStartEquity*(1-cfg.MaxDailyLossPct/100) {
		b.st.DayBlocked = true
		b.logf("warn", "Perda do dia passou de %.1f%%. Sem novas compras até amanhã.", cfg.MaxDailyLossPct)
	}

	// 3) VENDAS de todos os pares: stop / take-profit, vendas pendentes e sinais de venda
	var buys []string
	for _, s := range pairs {
		if !ok[s] {
			continue
		}
		px := price[s]
		if pos := b.st.Positions[s]; pos != nil && pos.Mode == cfg.Mode {
			switch {
			case pos.StopOrderID == 0 && pos.Stop > 0 && px <= pos.Stop:
				// sem ordem na Binance (modo simulado ou falha ao registrar): o próprio robô vende
				b.sellLocked(s, px, "stop-loss")
			case pos.Take > 0 && px >= pos.Take:
				b.sellLocked(s, px, "take-profit")
			}
		}
		if b.st.PendingSells[s] {
			if b.st.Positions[s] != nil {
				b.sellLocked(s, px, "sinal")
			}
			b.setPending(s, b.st.Positions[s] != nil)
		}
		closed := closedOf[s]
		lastT := closed[len(closed)-1].OpenTime
		if lastT == b.st.LastCandles[s] {
			continue // esta vela já foi avaliada
		}
		b.st.LastCandles[s] = lastT
		switch sig := b.preps[s].signal(b.lasts[s], b.st.Positions[s] != nil); {
		case sig == sigSell && b.st.Positions[s] != nil:
			b.sellLocked(s, px, "sinal")
			b.setPending(s, b.st.Positions[s] != nil)
		case sig == sigBuy && b.st.Positions[s] == nil:
			buys = append(buys, s)
		}
	}
	// 4) COMPRAS, depois de todas as vendas
	tradesBefore := len(b.st.Trades)
	defer func() {
		if len(b.st.Trades) != tradesBefore {
			b.refreshCashLocked()
		}
	}()
	for _, s := range buys {
		if b.st.DayBlocked {
			b.logf("info", "Sinal de compra em %s ignorado: limite de perda do dia atingido.", s)
		} else if retry := b.buyLocked(s, price[s]); retry {
			// a compra não chegou a ser feita (falha de conexão): tenta de novo enquanto esta vela valer
			b.st.LastCandles[s] = 0
		}
	}
	b.saveLocked()
}

func (b *Bot) setPending(s string, on bool) {
	if on {
		b.st.PendingSells[s] = true
	} else {
		delete(b.st.PendingSells, s)
	}
}

func (b *Bot) pairLiveLocked(s string) *PairLive {
	if b.live.Pairs == nil {
		b.live.Pairs = map[string]*PairLive{}
	}
	pl := b.live.Pairs[s]
	if pl == nil {
		pl = &PairLive{Symbol: s}
		b.live.Pairs[s] = pl
	}
	return pl
}

// quoteLocked = moeda da carteira (USDT, BRL…), pelo primeiro par conhecido.
func (b *Bot) quoteLocked() string {
	for _, s := range b.cfg.pairs() {
		if in := b.infos[s]; in != nil {
			return in.Quote
		}
	}
	if ps := b.cfg.pairs(); len(ps) > 0 {
		return quoteOf(ps[0])
	}
	return ""
}

// priceLocked = último preço conhecido de um par (ou o preço de compra, se ainda não houver).
func (b *Bot) priceLocked(s string) float64 {
	if pl := b.live.Pairs[s]; pl != nil && pl.Price > 0 {
		return pl.Price
	}
	if p := b.st.Positions[s]; p != nil {
		return p.Entry
	}
	return 0
}

// equityLocked = dinheiro + valor das posições do robô. override: preços mais novos de alguns pares.
func (b *Bot) equityLocked(quote float64, override map[string]float64) float64 {
	eq := quote
	for s, p := range b.st.Positions {
		if p.Mode != b.cfg.Mode {
			continue
		}
		px := b.priceLocked(s)
		if v, ok := override[s]; ok {
			px = v
		}
		if isExchange(p.Mode) {
			eq += p.Qty * px
		} else {
			eq += b.st.PaperBases[s] * px
		}
	}
	return eq
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

// ---------- compra e venda ----------

// slotLocked = quanto uma compra em `sym` pode usar: (patrimônio ÷ número de pares) × fração
// (% máximo ou volatilidade-alvo), limitado ao dinheiro livre e ao valor máximo por compra.
func (b *Bot) slotLocked(sym string, free, equity float64) float64 {
	n := len(b.cfg.pairs())
	if n < 1 {
		n = 1
	}
	frac := b.cfg.TradePct / 100
	if pr := b.preps[sym]; pr != nil {
		frac = pr.fraction(b.lasts[sym], frac)
	}
	spend := math.Min(free, equity/float64(n)*frac)
	if b.cfg.MaxBuy > 0 && spend > b.cfg.MaxBuy {
		spend = b.cfg.MaxBuy
	}
	return spend
}

// sizeWarningLocked avisa quando o patrimônio é pequeno demais para o número de pares: cada compra seria
// menor que o mínimo por ordem da Binance e seria recusada.
func (b *Bot) sizeWarningLocked() string {
	n := len(b.cfg.pairs())
	eq := b.live.Equity
	if n == 0 || eq <= 0 {
		return ""
	}
	minN := 0.0
	for _, s := range b.cfg.pairs() {
		if in := b.infos[s]; in != nil && in.MinNotional > minN {
			minN = in.MinNotional
		}
	}
	if minN <= 0 {
		return ""
	}
	need := minN * 1.02
	slot := eq / float64(n) * b.cfg.TradePct / 100
	if slot >= need {
		return ""
	}
	q := b.live.Quote
	fit := int(eq * b.cfg.TradePct / 100 / need)
	msg := fmt.Sprintf("Com %.2f %s e %d pares, cada compra seria de no máximo %.2f %s, mas a Binance só aceita ordens a partir de %.2f %s.", eq, q, n, slot, q, minN, q)
	if fit == 0 {
		return msg + " O saldo é pequeno demais para operar."
	}
	extra := ""
	if b.cfg.Params.SizeMode == "vol" {
		extra = " A volatilidade-alvo deixa as compras ainda menores: com pouco dinheiro, use \"% fixo do saldo\"."
	}
	return msg + fmt.Sprintf(" Use no máximo %d par(es), ou aumente o saldo para uns %.0f %s.%s", fit, need*float64(n)/(b.cfg.TradePct/100)*1.3, q, extra)
}

// buyLocked compra `sym`. Devolve retry=true quando a compra com certeza NÃO foi feita por falha de conexão
// (pode tentar de novo sem risco de comprar duas vezes).
func (b *Bot) buyLocked(sym string, price float64) (retry bool) {
	cfg := b.cfg
	info := b.infos[sym]
	if info == nil {
		return false
	}
	now := time.Now().UnixMilli()
	pos := &Position{Symbol: sym, Mode: cfg.Mode, Time: now}

	if isExchange(cfg.Mode) {
		free, total, err := b.bn.Balances()
		if err != nil {
			b.logf("err", "Compra de %s não feita: %v. Tento de novo na próxima checagem.", sym, err)
			_, refused := err.(*APIError)
			return !refused
		}
		spend := b.slotLocked(sym, free[info.Quote], b.equityLocked(total[info.Quote], map[string]float64{sym: price}))
		if spend < info.MinNotional*1.02 || spend <= 0 {
			b.logf("err", "Compra de %s cancelada: valor disponível pequeno demais (mínimo da Binance: %.2f %s).", sym, info.MinNotional, info.Quote)
			return false
		}
		r, err := b.marketOrderLocked(sym, "BUY", "", floorDec(spend, info.QuotePrecision))
		if err != nil {
			if ne, ok := err.(*notExecutedErr); ok && ne.network {
				b.logf("err", "Compra de %s não feita: %v. Tento de novo na próxima checagem.", sym, ne.err)
				return true
			}
			b.logf("err", "Compra de %s recusada: %v", sym, err)
			return false
		}
		pos.Qty = r.ExecutedQty - r.Commission[info.Base]
		pos.Cost = r.QuoteQty + r.Commission[info.Quote] + b.otherFeesLocked(sym, r.Commission)
		pos.Entry = r.QuoteQty / r.ExecutedQty
	} else {
		spend := b.slotLocked(sym, b.st.PaperQuote, b.equityLocked(b.st.PaperQuote, map[string]float64{sym: price}))
		if spend < 1 {
			b.logf("err", "Compra de %s cancelada: saldo simulado acabou. Use \"Zerar simulação\".", sym)
			return false
		}
		if spend < info.MinNotional*1.02 {
			// a simulação respeita o mínimo da Binance: senão pareceria possível operar com pouco dinheiro
			b.logf("err", "Compra de %s cancelada: %.2f %s é menos que o mínimo por ordem da Binance (%.2f %s).", sym, spend, info.Quote, info.MinNotional, info.Quote)
			return false
		}
		fee := spend * paperFeePct / 100
		pos.Qty = (spend - fee) / price
		pos.Cost = spend
		pos.Entry = price
		b.st.PaperQuote -= spend
		b.st.PaperBases[sym] += pos.Qty
	}
	pos.Stop, pos.Take, pos.Highest = b.entryStopLocked(sym, pos.Entry), cfg.Params.take(pos.Entry), pos.Entry
	b.st.Positions[sym] = pos
	b.st.Trades = append(b.st.Trades, Trade{Time: now, Side: "COMPRA", Symbol: sym, Mode: cfg.Mode,
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
		delete(b.stopRetry, sym)
		b.syncStopLocked(sym, price) // registra o stop na Binance na hora
	}
	return false
}

// sellLocked vende a posição inteira de `sym` a preço de mercado.
func (b *Bot) sellLocked(sym string, price float64, reason string) {
	pos := b.st.Positions[sym]
	if pos == nil {
		return
	}
	if isExchange(pos.Mode) {
		b.sellExchangeLocked(sym, price, reason)
		return
	}
	qty := pos.Qty
	gross := qty * price
	proceeds := gross - gross*paperFeePct/100
	b.st.PaperBases[sym] -= qty
	if b.st.PaperBases[sym] < 1e-12 {
		delete(b.st.PaperBases, sym)
	}
	b.st.PaperQuote += proceeds
	b.recordSaleLocked(sym, qty, proceeds, price, reason, true)
}

// recordSaleLocked registra uma venda. closeAll=false = venda parcial (parte da posição continua aberta).
func (b *Bot) recordSaleLocked(sym string, qty, proceeds, avg float64, reason string, closeAll bool) {
	pos := b.st.Positions[sym]
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
	t := Trade{Time: time.Now().UnixMilli(), Side: "VENDA", Symbol: sym, Mode: pos.Mode, Price: avg, Qty: qty, Quote: proceeds,
		Reason: reason, PnL: pnl, EntryTime: pos.Time, EntryPrice: pos.Entry}
	if cost > 0 {
		t.PnLPct = pnl / cost * 100
	}
	b.st.Trades = append(b.st.Trades, t)
	if len(b.st.Trades) > 2000 {
		b.st.Trades = b.st.Trades[len(b.st.Trades)-2000:]
	}
	base, quote := sym, ""
	if in := b.infos[sym]; in != nil {
		base, quote = in.Base, in.Quote
	}
	part := ""
	if closeAll {
		delete(b.st.Positions, sym)
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
	for s, p := range b.st.Positions {
		if p.Mode != b.cfg.Mode || !b.cfg.hasPair(s) {
			return fmt.Errorf("existe uma posição aberta em %s (%s) fora da carteira/modo atual. Volte esse par/modo ou feche a posição", s, p.Mode)
		}
	}
	if err := b.checkKeyLocked(); err != nil {
		return err
	}
	b.st.Running = true
	for s := range b.st.PendingSells {
		if b.st.Positions[s] == nil {
			delete(b.st.PendingSells, s)
		}
	}
	b.applyPlatformLocked()
	b.st.LastCandles = map[string]int64{}
	b.st.LastAlive = time.Now().UnixMilli()
	desc := b.descLocked()
	b.logf("ok", "Robô LIGADO: %s, modo %s.", desc, strings.ToUpper(b.cfg.Mode))
	b.alert(fmt.Sprintf("▶️ Robô ligado: %s%s", desc, modeTag(b.cfg.Mode)))
	b.saveLocked()
	go b.poke()
	return nil
}

// descLocked = "BTCUSDT, ETHUSDT e SOLUSDT, velas de 4h".
func (b *Bot) descLocked() string {
	ps := b.cfg.pairs()
	s := strings.Join(ps, ", ")
	if len(ps) > 1 {
		s = strings.Join(ps[:len(ps)-1], ", ") + " e " + ps[len(ps)-1]
	}
	return fmt.Sprintf("%s, velas de %s", s, b.cfg.Interval)
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
	if n := len(b.st.Positions); n > 0 {
		onEx := 0
		for _, p := range b.st.Positions {
			if p.StopOrderID != 0 {
				onEx++
			}
		}
		switch {
		case onEx == n:
			msg += fmt.Sprintf(" %d posição(ões) continua(m) aberta(s), com o stop-loss registrado na Binance.", n)
		default:
			msg += fmt.Sprintf(" %d posição(ões) continua(m) aberta(s); sem stop vigiado enquanto o robô estiver desligado (exceto os registrados na Binance).", n)
		}
	}
	b.logf("info", "%s", msg)
	b.alert("⏸️ " + msg)
	b.saveLocked()
}

// SellNow vende a posição de `sym` a mercado ("" = todas).
func (b *Bot) SellNow(sym string) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	targets := []string{sym}
	if sym == "" {
		targets = nil
		for s := range b.st.Positions {
			targets = append(targets, s)
		}
	}
	if len(targets) == 0 || (sym != "" && b.st.Positions[sym] == nil) {
		return fmt.Errorf("não há posição aberta")
	}
	var failed []string
	for _, s := range targets {
		px := b.priceLocked(s)
		if b.infos[s] == nil || px == 0 || b.live.Pairs[s] == nil || b.live.Pairs[s].Price == 0 {
			failed = append(failed, s+" (sem preço ainda)")
			continue
		}
		b.sellLocked(s, px, "manual")
		if b.st.Positions[s] != nil {
			failed = append(failed, s)
		} else {
			delete(b.st.PendingSells, s)
		}
	}
	b.refreshCashLocked()
	if len(failed) > 0 {
		return fmt.Errorf("não vendi %s; veja o registro", strings.Join(failed, ", "))
	}
	return nil
}

// refreshCashLocked atualiza o dinheiro e o patrimônio do painel logo depois de compras/vendas
// (sem esperar a próxima checagem).
func (b *Bot) refreshCashLocked() {
	if isExchange(b.cfg.Mode) {
		_, total, err := b.bn.Balances()
		if err != nil {
			return
		}
		b.live.QuoteBal = total[b.quoteLocked()]
	} else {
		b.live.QuoteBal = b.st.PaperQuote
	}
	b.live.Equity = b.equityLocked(b.live.QuoteBal, nil)
}

func (b *Bot) ResetPaper() error {
	b.mu.Lock()
	defer b.mu.Unlock()
	for s, p := range b.st.Positions {
		if p.Mode == "simulado" {
			delete(b.st.Positions, s)
			delete(b.st.PendingSells, s)
		}
	}
	b.st.PaperQuote, b.st.PaperBases = b.cfg.PaperStart, map[string]float64{}
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

// normalizePairs limpa a lista de pares (maiúsculas, sem repetidos) e confere que usam a mesma moeda.
func normalizePairs(syms []string, single string) ([]string, error) {
	in := syms
	if len(in) == 0 && single != "" {
		in = []string{single}
	}
	out := parseSymbols(in)
	if len(out) == 0 {
		return nil, fmt.Errorf("informe pelo menos um par (ex.: BTCUSDT)")
	}
	if len(parseSymbolsAll(in)) > maxPairs {
		return nil, fmt.Errorf("no máximo %d pares", maxPairs)
	}
	q := quoteOf(out[0])
	for _, s := range out[1:] {
		if quoteOf(s) != q {
			return nil, fmt.Errorf("todos os pares precisam usar a mesma moeda (%s e %s são diferentes)", out[0], s)
		}
	}
	return out, nil
}

func (b *Bot) UpdateConfig(nc Config) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	pairs, err := normalizePairs(nc.Symbols, strings.ToUpper(strings.TrimSpace(nc.Symbol)))
	if err != nil {
		return err
	}
	nc.Symbols, nc.Symbol = pairs, pairs[0]
	nc.PingURL = strings.TrimSpace(nc.PingURL)
	nc.TelegramChat = strings.TrimSpace(nc.TelegramChat)
	nc.Params.normalize()
	switch {
	case nc.TradePct <= 0 || nc.TradePct > 100:
		return fmt.Errorf("o tamanho da compra precisa ficar entre 1%% e 100%%")
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
	if b.st.Running && nc.Mode != b.cfg.Mode {
		return fmt.Errorf("desligue o robô antes de trocar o modo")
	}
	for s, p := range b.st.Positions {
		if nc.Mode != p.Mode {
			return fmt.Errorf("feche as posições abertas antes de trocar o modo (há posição em %s)", s)
		}
		if !nc.hasPair(s) {
			return fmt.Errorf("há uma posição aberta em %s: feche-a antes de tirar esse par da carteira", s)
		}
	}
	nc.APIKey, nc.APISecret = keep(nc.APIKey, b.cfg.APIKey), keep(nc.APISecret, b.cfg.APISecret)
	nc.TestAPIKey, nc.TestAPISecret = keep(nc.TestAPIKey, b.cfg.TestAPIKey), keep(nc.TestAPISecret, b.cfg.TestAPISecret)
	nc.TelegramToken = keep(nc.TelegramToken, b.cfg.TelegramToken)
	if nc.PingURL == "-" {
		nc.PingURL = ""
	}
	// pares que saíram da carteira: esquece os dados deles (e o resíduo simulado)
	for _, s := range b.cfg.pairs() {
		if !nc.hasPair(s) {
			delete(b.infos, s)
			delete(b.preps, s)
			delete(b.live.Pairs, s)
			delete(b.st.LastCandles, s)
			delete(b.st.PaperBases, s)
		}
	}
	if nc.Mode != b.cfg.Mode {
		b.bn = newFor(nc.Mode)
		b.infos, b.preps = map[string]*SymbolInfo{}, map[string]*Prepared{}
		b.live = Live{Pairs: map[string]*PairLive{}}
		b.st.DayKey = ""
	}
	b.bn.Key, b.bn.Secret = nc.keys(nc.Mode)
	if b.cfg.Params != nc.Params || b.cfg.Interval != nc.Interval {
		for s, pos := range b.st.Positions {
			if nc.Params.StopMode == "pct" {
				// dá para recalcular já (não precisa do ATR)
				pos.Stop = math.Max(pctStop(nc.Params, pos.Entry), (&Prepared{p: nc.Params}).trailStop(-1, math.Max(pos.Highest, pos.Entry)))
				pos.StopDirty = false
			} else {
				pos.StopDirty = true // stop por ATR: recalcula na próxima checagem, com as velas
			}
			pos.Take = nc.Params.take(pos.Entry)
			delete(b.stopRetry, s) // a ordem de stop na Binance é atualizada na próxima checagem
		}
		b.preps = map[string]*Prepared{}
	}
	b.cfg = nc
	b.tgMu.Lock()
	b.tgToken, b.tgChat = nc.TelegramToken, nc.TelegramChat
	b.tgMu.Unlock()
	b.saveConfigLocked()
	b.saveLocked()
	b.applyPlatformLocked()
	b.logf("info", "Ajustes salvos (%s, modo %s).", b.descLocked(), nc.Mode)
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
		if n := len(b.st.Positions); n > 0 {
			onEx := 0
			for _, p := range b.st.Positions {
				if p.StopOrderID != 0 {
					onEx++
				}
			}
			if onEx == n {
				msg += " Os stop-loss continuaram registrados na Binance."
			} else {
				msg += fmt.Sprintf(" %d posição(ões) aberta(s) ficou(aram) sem stop vigiado.", n-onEx)
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
		desc := b.descLocked()
		b.logf("info", "Robô retomado automaticamente (versão %s: %s, modo %s).", version, desc, b.cfg.Mode)
		b.alert(fmt.Sprintf("▶️ Robô retomado (versão %s): %s%s", version, desc, modeTag(b.cfg.Mode)))
	}
}

// entryStopLocked = stop logo depois da compra (por % ou por ATR, com os indicadores da última checagem).
func (b *Bot) entryStopLocked(sym string, entry float64) float64 {
	pr := b.preps[sym]
	if pr == nil {
		return pctStop(b.cfg.Params, entry)
	}
	return pr.initialStop(b.lasts[sym], entry)
}

// updateStopLocked: stop móvel (sobe com o maior fechamento desde a compra) e recálculo depois de mudar ajustes.
// Roda a cada checagem; o stop nunca desce, a não ser que os ajustes tenham mudado.
func (b *Bot) updateStopLocked(sym string, closed []Kline, pr *Prepared, last int) {
	pos := b.st.Positions[sym]
	if pos == nil || pos.Mode != b.cfg.Mode {
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
		b.logf("info", "Stop-loss de %s ajustado: %s → %s.", sym, fmtPrice(old), fmtPrice(pos.Stop))
		delete(b.stopRetry, sym)
	}
}
