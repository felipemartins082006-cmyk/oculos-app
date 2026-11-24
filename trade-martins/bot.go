package main

import (
	"encoding/json"
	"fmt"
	"log"
	"math"
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
	MaxDailyLossPct float64 `json:"maxDailyLossPct"` // 0 = sem limite
	CheckSeconds    int     `json:"checkSeconds"`
	Mode            string  `json:"mode"` // "simulado" | "real"
	PaperStart      float64 `json:"paperStart"`
	APIKey          string  `json:"apiKey"`
	APISecret       string  `json:"apiSecret"`
}

func defaultConfig() Config {
	return Config{
		Symbol: "BTCUSDT", Interval: "4h",
		Params:   Params{FastEMA: 9, SlowEMA: 21, TrendEMA: 200, RSIPeriod: 14, RSIMaxBuy: 70, StopLossPct: 6, TakeProfitPct: 0},
		TradePct: 95, MaxDailyLossPct: 5, CheckSeconds: 30,
		Mode: "simulado", PaperStart: 1000,
	}
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
}

type LogLine struct {
	T    int64  `json:"t"`
	Msg  string `json:"msg"`
	Kind string `json:"kind"` // info | ok | warn | err | trade
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
}

// Live = o que o painel mostra e não precisa ser salvo.
type Live struct {
	Price     float64            `json:"price"`
	Equity    float64            `json:"equity"`
	QuoteBal  float64            `json:"quoteBal"`
	BaseBal   float64            `json:"baseBal"`
	Base      string             `json:"base"`
	Quote     string             `json:"quote"`
	Inds      map[string]float64 `json:"inds"`
	Updated   int64              `json:"updated"`
	LastError string             `json:"lastError"`
	Candles   []Kline            `json:"-"`
	NextCheck int64              `json:"nextCheck"`
}

type Bot struct {
	mu   sync.Mutex
	cfg  Config
	st   State
	live Live
	bn   *Binance
	info *SymbolInfo
	wake chan struct{}
	dir  string
}

func NewBot(dir string) *Bot {
	b := &Bot{dir: dir, bn: NewBinance(), cfg: defaultConfig(), wake: make(chan struct{}, 1)}
	readJSON(filepath.Join(dir, "config.json"), &b.cfg)
	if readJSON(filepath.Join(dir, "estado.json"), &b.st) != nil {
		b.st.PaperQuote = b.cfg.PaperStart
	}
	b.bn.Key, b.bn.Secret = b.cfg.APIKey, b.cfg.APISecret
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

func (b *Bot) logf(kind, format string, a ...any) {
	msg := fmt.Sprintf(format, a...)
	log.Printf("[%s] %s", kind, msg)
	b.st.Logs = append(b.st.Logs, LogLine{T: time.Now().UnixMilli(), Msg: msg, Kind: kind})
	if len(b.st.Logs) > 300 {
		b.st.Logs = b.st.Logs[len(b.st.Logs)-300:]
	}
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
	if b.st.Running {
		b.logf("info", "Robô retomado automaticamente (%s, %s, modo %s).", b.cfg.Symbol, b.cfg.Interval, b.cfg.Mode)
	}
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

func (b *Bot) tick() {
	b.mu.Lock()
	cfg := b.cfg
	b.mu.Unlock()

	if b.info == nil || b.info.Symbol != cfg.Symbol {
		info, err := b.bn.Symbol(cfg.Symbol)
		if err != nil {
			b.setErr(err)
			return
		}
		b.mu.Lock()
		b.info = info
		b.mu.Unlock()
	}

	need := warmup(cfg.Params) + 150
	if need > 1000 {
		need = 1000
	}
	ks, err := b.bn.Klines(cfg.Symbol, cfg.Interval, need, 0)
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
	closes := make([]float64, len(closed))
	for i, k := range closed {
		closes[i] = k.C
	}
	ind := compute(closes, cfg.Params)
	last := len(closed) - 1

	// saldo
	var quoteBal, baseBal float64
	if cfg.Mode == "real" {
		_, total, err := b.bn.Balances()
		if err != nil {
			b.setErr(err)
		} else {
			quoteBal, baseBal = total[b.info.Quote], total[b.info.Base]
		}
	}

	b.mu.Lock()
	defer b.mu.Unlock()
	if cfg.Mode != "real" {
		quoteBal, baseBal = b.st.PaperQuote, b.st.PaperBase
	}
	b.live.Price, b.live.QuoteBal, b.live.BaseBal = price, quoteBal, baseBal
	b.live.Base, b.live.Quote = b.info.Base, b.info.Quote
	b.live.Equity = quoteBal + baseBal*price
	b.live.Candles = ks
	b.live.Inds = map[string]float64{"fast": nz(ind.Fast[last]), "slow": nz(ind.Slow[last]), "rsi": nz(ind.RSI[last])}
	if ind.Trend != nil {
		b.live.Inds["trend"] = nz(ind.Trend[last])
	}
	b.live.Updated = time.Now().UnixMilli()
	b.live.LastError = ""

	// limite de perda do dia
	today := dayKey(time.Now())
	if b.st.DayKey != today {
		b.st.DayKey, b.st.DayStartEquity, b.st.DayBlocked = today, b.live.Equity, false
		b.saveLocked()
	}
	if !b.st.Running {
		return
	}
	if cfg.MaxDailyLossPct > 0 && !b.st.DayBlocked && b.st.DayStartEquity > 0 &&
		b.live.Equity < b.st.DayStartEquity*(1-cfg.MaxDailyLossPct/100) {
		b.st.DayBlocked = true
		b.logf("warn", "Perda do dia passou de %.1f%%. Sem novas compras até amanhã.", cfg.MaxDailyLossPct)
		b.saveLocked()
	}

	pos := b.st.Position
	if pos != nil && pos.Symbol == cfg.Symbol {
		switch {
		case pos.Stop > 0 && price <= pos.Stop:
			b.sellLocked(price, "stop-loss")
			return
		case pos.Take > 0 && price >= pos.Take:
			b.sellLocked(price, "take-profit")
			return
		}
	}

	if closed[last].OpenTime == b.st.LastCandle {
		return // esta vela já foi avaliada
	}
	b.st.LastCandle = closed[last].OpenTime
	sig := signalAt(ind, closes, last, cfg.Params)
	switch {
	case sig == sigBuy && b.st.Position == nil:
		if b.st.DayBlocked {
			b.logf("warn", "Sinal de compra ignorado: limite de perda do dia atingido.")
		} else {
			b.buyLocked(price)
		}
	case sig == sigSell && b.st.Position != nil:
		b.sellLocked(price, "sinal")
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

func (b *Bot) buyLocked(price float64) {
	cfg := b.cfg
	info := b.info
	now := time.Now().UnixMilli()
	pos := &Position{Symbol: cfg.Symbol, Mode: cfg.Mode, Time: now}

	if cfg.Mode == "real" {
		spend := b.live.QuoteBal * cfg.TradePct / 100
		free, _, err := b.bn.Balances()
		if err != nil {
			b.logf("err", "Compra cancelada: %v", err)
			return
		}
		if f := free[info.Quote] * cfg.TradePct / 100; f < spend {
			spend = f
		}
		if spend < info.MinNotional*1.02 || spend <= 0 {
			b.logf("err", "Compra cancelada: saldo de %s pequeno demais (mínimo da Binance: %.2f).", info.Quote, info.MinNotional)
			return
		}
		r, err := b.bn.MarketOrder(cfg.Symbol, "BUY", "", floorDec(spend, info.QuotePrecision))
		if err != nil {
			b.logf("err", "Compra recusada: %v", err)
			return
		}
		pos.Qty = r.ExecutedQty - r.Commission[info.Base]
		pos.Cost = r.QuoteQty + r.Commission[info.Quote]
		pos.Entry = r.QuoteQty / r.ExecutedQty
	} else {
		spend := b.st.PaperQuote * cfg.TradePct / 100
		if spend < 1 {
			b.logf("err", "Compra cancelada: saldo simulado acabou. Use \"Zerar simulação\".")
			return
		}
		fee := spend * paperFeePct / 100
		pos.Qty = (spend - fee) / price
		pos.Cost = spend
		pos.Entry = price
		b.st.PaperQuote -= spend
		b.st.PaperBase += pos.Qty
		b.st.PaperSymbol = cfg.Symbol
	}
	pos.Stop, pos.Take = cfg.Params.stops(pos.Entry)
	b.st.Position = pos
	b.st.Trades = append(b.st.Trades, Trade{Time: now, Side: "COMPRA", Symbol: cfg.Symbol, Mode: cfg.Mode,
		Price: pos.Entry, Qty: pos.Qty, Quote: pos.Cost, Reason: "sinal"})
	b.logf("trade", "COMPRA %s %s a %s (%s %.2f)%s", fmtQty(pos.Qty), info.Base, fmtPrice(pos.Entry), info.Quote, pos.Cost, modeTag(cfg.Mode))
	b.saveLocked()
}

func (b *Bot) sellLocked(price float64, reason string) {
	pos := b.st.Position
	if pos == nil {
		return
	}
	info := b.info
	now := time.Now().UnixMilli()
	var proceeds, qty, avg float64

	if pos.Mode == "real" {
		free, _, err := b.bn.Balances()
		if err != nil {
			b.logf("err", "Venda (%s) não enviada: %v. Tento de novo na próxima checagem.", reason, err)
			return
		}
		q := math.Min(pos.Qty, free[info.Base])
		qs := floorStep(q, info.StepSize)
		var qv float64
		fmt.Sscan(qs, &qv)
		if qv < info.MinQty || qv*price < info.MinNotional {
			b.logf("warn", "Quantidade de %s pequena demais para vender (%s). Posição encerrada no robô.", info.Base, qs)
			b.st.Position = nil
			b.saveLocked()
			return
		}
		r, err := b.bn.MarketOrder(pos.Symbol, "SELL", qs, "")
		if err != nil {
			b.logf("err", "Venda (%s) recusada: %v. Tento de novo na próxima checagem.", reason, err)
			return
		}
		qty = r.ExecutedQty
		proceeds = r.QuoteQty - r.Commission[info.Quote]
		avg = r.QuoteQty / r.ExecutedQty
	} else {
		qty = pos.Qty
		gross := qty * price
		proceeds = gross - gross*paperFeePct/100
		avg = price
		b.st.PaperBase -= qty
		if b.st.PaperBase < 1e-12 {
			b.st.PaperBase = 0
		}
		b.st.PaperQuote += proceeds
	}
	pnl := proceeds - pos.Cost
	t := Trade{Time: now, Side: "VENDA", Symbol: pos.Symbol, Mode: pos.Mode, Price: avg, Qty: qty, Quote: proceeds,
		Reason: reason, PnL: pnl, PnLPct: pnl / pos.Cost * 100, EntryTime: pos.Time, EntryPrice: pos.Entry}
	b.st.Trades = append(b.st.Trades, t)
	if len(b.st.Trades) > 2000 {
		b.st.Trades = b.st.Trades[len(b.st.Trades)-2000:]
	}
	b.st.Position = nil
	kind := "ok"
	if pnl < 0 {
		kind = "warn"
	}
	b.logf(kind, "VENDA (%s) %s %s a %s → resultado %+.2f %s (%+.2f%%)%s", reason, fmtQty(qty), info.Base, fmtPrice(avg), pnl, info.Quote, t.PnLPct, modeTag(pos.Mode))
	b.saveLocked()
}

func modeTag(m string) string {
	if m == "real" {
		return " [REAL]"
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
	if b.cfg.Mode == "real" {
		if b.cfg.APIKey == "" || b.cfg.APISecret == "" {
			return fmt.Errorf("configure a chave de API da Binance antes de usar o modo real")
		}
		if !confirmReal {
			return fmt.Errorf("confirme que quer operar com dinheiro real")
		}
	}
	if b.st.Position != nil && (b.st.Position.Symbol != b.cfg.Symbol || b.st.Position.Mode != b.cfg.Mode) {
		return fmt.Errorf("existe uma posição aberta em %s (%s). Volte para esse par/modo ou feche a posição", b.st.Position.Symbol, b.st.Position.Mode)
	}
	b.st.Running = true
	b.st.LastCandle = 0
	b.logf("ok", "Robô LIGADO: %s, velas de %s, modo %s.", b.cfg.Symbol, b.cfg.Interval, strings.ToUpper(b.cfg.Mode))
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
	msg := "Robô DESLIGADO."
	if b.st.Position != nil {
		msg += " A posição aberta continua aberta (sem stop automático enquanto desligado)."
	}
	b.logf("info", "%s", msg)
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
	if b.st.Position != nil && b.st.Position.Mode != "real" {
		b.st.Position = nil
	}
	b.st.PaperQuote, b.st.PaperBase = b.cfg.PaperStart, 0
	kept := b.st.Trades[:0]
	for _, t := range b.st.Trades {
		if t.Mode == "real" {
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

func (b *Bot) UpdateConfig(nc Config) error {
	b.mu.Lock()
	defer b.mu.Unlock()
	nc.Symbol = strings.ToUpper(strings.TrimSpace(nc.Symbol))
	p := nc.Params
	switch {
	case nc.Symbol == "":
		return fmt.Errorf("informe o par (ex.: BTCUSDT)")
	case p.FastEMA < 2 || p.SlowEMA <= p.FastEMA:
		return fmt.Errorf("a EMA lenta precisa ser maior que a rápida (e a rápida pelo menos 2)")
	case p.TrendEMA != 0 && p.TrendEMA <= p.SlowEMA:
		return fmt.Errorf("a EMA de tendência precisa ser maior que a lenta (ou 0 para desligar)")
	case p.TrendEMA > 800 || p.SlowEMA > 800:
		return fmt.Errorf("médias acima de 800 velas não são suportadas")
	case p.RSIPeriod < 2 || p.RSIMaxBuy <= 0 || p.RSIMaxBuy > 100:
		return fmt.Errorf("RSI inválido")
	case p.StopLossPct < 0 || p.StopLossPct >= 50 || p.TakeProfitPct < 0 || p.TakeProfitPct > 500:
		return fmt.Errorf("stop-loss ou take-profit inválido")
	case nc.TradePct <= 0 || nc.TradePct > 100:
		return fmt.Errorf("o tamanho da compra precisa ficar entre 1%% e 100%% do saldo")
	case nc.Mode != "simulado" && nc.Mode != "real":
		return fmt.Errorf("modo inválido")
	case nc.PaperStart <= 0:
		return fmt.Errorf("saldo inicial da simulação inválido")
	}
	if _, ok := map[string]bool{"1m": true, "3m": true, "5m": true, "15m": true, "30m": true, "1h": true, "2h": true, "4h": true, "6h": true, "8h": true, "12h": true, "1d": true}[nc.Interval]; !ok {
		return fmt.Errorf("intervalo inválido")
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
	// chave em branco = manter a atual
	if nc.APIKey == "" {
		nc.APIKey = b.cfg.APIKey
	}
	if nc.APISecret == "" {
		nc.APISecret = b.cfg.APISecret
	}
	if nc.APIKey == "-" {
		nc.APIKey, nc.APISecret = "", ""
	}
	if nc.Symbol != b.cfg.Symbol {
		b.info = nil
		if b.st.Position == nil && b.st.PaperBase > 0 {
			b.st.PaperBase = 0 // resíduo do par anterior
		}
	}
	if b.cfg.Params != nc.Params && b.st.Position != nil {
		b.st.Position.Stop, b.st.Position.Take = nc.Params.stops(b.st.Position.Entry)
	}
	b.cfg = nc
	b.bn.Key, b.bn.Secret = nc.APIKey, nc.APISecret
	b.saveConfigLocked()
	b.logf("info", "Configurações salvas (%s, %s, modo %s).", nc.Symbol, nc.Interval, nc.Mode)
	go b.poke()
	return nil
}
