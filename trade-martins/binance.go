package main

// Cliente mínimo da API Spot da Binance: velas, regras do par, saldo e ordens a mercado.

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Dados públicos (velas, preço). Se o endereço principal falhar, usa o espelho oficial só de dados.
var mainDataHosts = []string{"https://api.binance.com", "https://data-api.binance.vision"}

const (
	mainTradeHost = "https://api.binance.com"
	testnetHost   = "https://testnet.binance.vision" // Binance de teste: ordens de verdade com dinheiro de mentira
)

type Binance struct {
	Key, Secret string
	TradeHost   string
	DataHosts   []string

	mu         sync.Mutex
	timeOffset int64 // diferença (ms) entre o relógio da Binance e o do PC
	goodHost   string
	client     *http.Client
}

func NewBinance() *Binance {
	return &Binance{TradeHost: mainTradeHost, DataHosts: mainDataHosts, client: &http.Client{Timeout: 20 * time.Second}}
}

func NewBinanceTestnet() *Binance {
	return &Binance{TradeHost: testnetHost, DataHosts: []string{testnetHost}, client: &http.Client{Timeout: 20 * time.Second}}
}

// newFor devolve o cliente certo para o modo: "teste" usa a Binance de teste, o resto a Binance normal.
func newFor(mode string) *Binance {
	if mode == "teste" {
		return NewBinanceTestnet()
	}
	return NewBinance()
}

type APIError struct {
	Code int    `json:"code"`
	Msg  string `json:"msg"`
}

func (e *APIError) Error() string {
	pt := map[int]string{
		-1121: "par inválido (não existe na Binance)",
		-1021: "relógio do PC fora de hora; ajuste a data/hora do Windows",
		-1022: "assinatura inválida: confira a chave secreta",
		-2014: "chave de API inválida",
		-2015: "chave de API recusada: confira a chave, a permissão de Spot e a restrição de IP",
		-2010: "ordem recusada (saldo insuficiente ou valor pequeno demais)",
		-1013: "quantidade fora das regras do par (valor pequeno demais?)",
		-2011: "ordem não encontrada (já executada ou cancelada)",
		-2013: "ordem não encontrada",
	}
	if m, ok := pt[e.Code]; ok {
		return fmt.Sprintf("Binance: %s (%d %s)", m, e.Code, e.Msg)
	}
	return fmt.Sprintf("Binance: %s (código %d)", e.Msg, e.Code)
}

func decodeResp(resp *http.Response, out any) error {
	body, err := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
	if err != nil {
		return err
	}
	if resp.StatusCode >= 400 {
		var ae APIError
		if json.Unmarshal(body, &ae) == nil && ae.Msg != "" {
			return &ae
		}
		return fmt.Errorf("Binance respondeu HTTP %d", resp.StatusCode)
	}
	if out == nil {
		return nil
	}
	return json.Unmarshal(body, out)
}

// public faz GET em dados públicos, tentando os endereços em ordem.
func (b *Binance) public(path string, q url.Values, out any) error {
	b.mu.Lock()
	hosts := append([]string{}, b.DataHosts...)
	if b.goodHost != "" {
		hosts = append([]string{b.goodHost}, hosts...)
	}
	b.mu.Unlock()

	var lastErr error
	tried := map[string]bool{}
	for _, h := range hosts {
		if tried[h] {
			continue
		}
		tried[h] = true
		resp, err := b.client.Get(h + path + "?" + q.Encode())
		if err != nil {
			lastErr = err
			continue
		}
		// 451/403 = região bloqueada; 5xx = instabilidade. Tenta o próximo endereço.
		if resp.StatusCode == 451 || resp.StatusCode == 403 || resp.StatusCode >= 500 {
			resp.Body.Close()
			lastErr = fmt.Errorf("Binance respondeu HTTP %d", resp.StatusCode)
			if resp.StatusCode == 451 {
				lastErr = fmt.Errorf("a Binance bloqueia o acesso a partir deste país/rede (HTTP 451)")
			}
			continue
		}
		err = decodeResp(resp, out)
		resp.Body.Close()
		if err == nil {
			b.mu.Lock()
			b.goodHost = h
			b.mu.Unlock()
		}
		return err
	}
	if lastErr == nil {
		lastErr = fmt.Errorf("sem conexão com a Binance")
	}
	return fmt.Errorf("sem conexão com a Binance: %w", lastErr)
}

func sign(secret, payload string) string {
	m := hmac.New(sha256.New, []byte(secret))
	m.Write([]byte(payload))
	return hex.EncodeToString(m.Sum(nil))
}

func (b *Binance) syncTime() error {
	var r struct {
		ServerTime int64 `json:"serverTime"`
	}
	t0 := time.Now().UnixMilli()
	if err := b.public("/api/v3/time", url.Values{}, &r); err != nil {
		return err
	}
	t1 := time.Now().UnixMilli()
	b.mu.Lock()
	b.timeOffset = r.ServerTime - (t0+t1)/2
	b.mu.Unlock()
	return nil
}

// signed faz uma chamada autenticada (saldo, ordens).
func (b *Binance) signed(method, path string, q url.Values, out any) error {
	if b.Key == "" || b.Secret == "" {
		return fmt.Errorf("chave de API não configurada")
	}
	do := func() error {
		b.mu.Lock()
		off := b.timeOffset
		b.mu.Unlock()
		qq := url.Values{}
		for k, v := range q {
			qq[k] = v
		}
		qq.Set("timestamp", strconv.FormatInt(time.Now().UnixMilli()+off, 10))
		qq.Set("recvWindow", "10000")
		payload := qq.Encode()
		full := b.TradeHost + path + "?" + payload + "&signature=" + sign(b.Secret, payload)
		req, err := http.NewRequest(method, full, nil)
		if err != nil {
			return err
		}
		req.Header.Set("X-MBX-APIKEY", b.Key)
		resp, err := b.client.Do(req)
		if err != nil {
			return fmt.Errorf("sem conexão com a Binance: %w", err)
		}
		defer resp.Body.Close()
		if resp.StatusCode == 451 {
			return &APIError{Code: 451, Msg: "a Binance bloqueia operações a partir deste país/servidor"}
		}
		return decodeResp(resp, out)
	}
	err := do()
	if ae, ok := err.(*APIError); ok && ae.Code == -1021 {
		if b.syncTime() == nil {
			err = do()
		}
	}
	return err
}

type Kline struct {
	OpenTime, CloseTime int64
	O, H, L, C, V       float64
}

func (b *Binance) Klines(symbol, interval string, limit int, startTime int64) ([]Kline, error) {
	q := url.Values{"symbol": {symbol}, "interval": {interval}, "limit": {strconv.Itoa(limit)}}
	if startTime > 0 {
		q.Set("startTime", strconv.FormatInt(startTime, 10))
	}
	var raw [][]any
	if err := b.public("/api/v3/klines", q, &raw); err != nil {
		return nil, err
	}
	out := make([]Kline, 0, len(raw))
	for _, r := range raw {
		if len(r) < 7 {
			continue
		}
		f := func(i int) float64 {
			s, _ := r[i].(string)
			v, _ := strconv.ParseFloat(s, 64)
			return v
		}
		ot, _ := r[0].(float64)
		ct, _ := r[6].(float64)
		out = append(out, Kline{OpenTime: int64(ot), CloseTime: int64(ct), O: f(1), H: f(2), L: f(3), C: f(4), V: f(5)})
	}
	return out, nil
}

// History baixa velas dos últimos `days` dias (paginando de 1000 em 1000).
func (b *Binance) History(symbol, interval string, days int, maxCandles int) ([]Kline, error) {
	dur := intervalDur(interval)
	start := time.Now().Add(-time.Duration(days) * 24 * time.Hour).UnixMilli()
	var all []Kline
	for len(all) < maxCandles {
		ks, err := b.Klines(symbol, interval, 1000, start)
		if err != nil {
			return nil, err
		}
		if len(ks) == 0 {
			break
		}
		all = append(all, ks...)
		start = ks[len(ks)-1].OpenTime + dur.Milliseconds()
		if len(ks) < 1000 || start > time.Now().UnixMilli() {
			break
		}
	}
	// A última vela ainda está aberta: fica de fora.
	if n := len(all); n > 0 && all[n-1].CloseTime > time.Now().UnixMilli() {
		all = all[:n-1]
	}
	if len(all) > maxCandles {
		all = all[len(all)-maxCandles:]
	}
	return all, nil
}

type SymbolInfo struct {
	Symbol, Base, Quote string
	Status              string
	StepSize, MinQty    float64
	TickSize            float64
	MinNotional         float64
	QuotePrecision      int
}

func (b *Binance) Symbol(symbol string) (*SymbolInfo, error) {
	var r struct {
		Symbols []struct {
			Symbol              string           `json:"symbol"`
			Status              string           `json:"status"`
			BaseAsset           string           `json:"baseAsset"`
			QuoteAsset          string           `json:"quoteAsset"`
			QuoteAssetPrecision int              `json:"quoteAssetPrecision"`
			Filters             []map[string]any `json:"filters"`
		} `json:"symbols"`
	}
	if err := b.public("/api/v3/exchangeInfo", url.Values{"symbol": {symbol}}, &r); err != nil {
		return nil, err
	}
	if len(r.Symbols) == 0 {
		return nil, fmt.Errorf("par %s não encontrado na Binance", symbol)
	}
	s := r.Symbols[0]
	info := &SymbolInfo{Symbol: s.Symbol, Base: s.BaseAsset, Quote: s.QuoteAsset, Status: s.Status, QuotePrecision: s.QuoteAssetPrecision}
	num := func(m map[string]any, k string) float64 {
		v, _ := strconv.ParseFloat(fmt.Sprint(m[k]), 64)
		return v
	}
	for _, f := range s.Filters {
		switch f["filterType"] {
		case "LOT_SIZE":
			info.StepSize, info.MinQty = num(f, "stepSize"), num(f, "minQty")
		case "PRICE_FILTER":
			info.TickSize = num(f, "tickSize")
		case "NOTIONAL", "MIN_NOTIONAL":
			if v := num(f, "minNotional"); v > info.MinNotional {
				info.MinNotional = v
			}
		}
	}
	if info.QuotePrecision <= 0 || info.QuotePrecision > 8 {
		info.QuotePrecision = 8
	}
	return info, nil
}

// Balances devolve saldo (livre + travado) por moeda.
func (b *Binance) Balances() (free map[string]float64, total map[string]float64, err error) {
	var r struct {
		Balances []struct {
			Asset  string `json:"asset"`
			Free   string `json:"free"`
			Locked string `json:"locked"`
		} `json:"balances"`
		CanTrade bool `json:"canTrade"`
	}
	if err = b.signed("GET", "/api/v3/account", url.Values{"omitZeroBalances": {"true"}}, &r); err != nil {
		return nil, nil, err
	}
	free, total = map[string]float64{}, map[string]float64{}
	for _, x := range r.Balances {
		f, _ := strconv.ParseFloat(x.Free, 64)
		l, _ := strconv.ParseFloat(x.Locked, 64)
		free[x.Asset], total[x.Asset] = f, f+l
	}
	return free, total, nil
}

type OrderResult struct {
	OrderID     int64
	ExecutedQty float64
	QuoteQty    float64
	Commission  map[string]float64 // taxa cobrada por moeda
}

// MarketOrder manda uma ordem a mercado. clientID identifica a ordem: se a conexão cair no meio,
// dá para perguntar à Binance se ela foi executada (veja Bot.marketOrder).
func (b *Binance) MarketOrder(symbol, side, quantity, quoteQty, clientID string) (*OrderResult, error) {
	q := url.Values{"symbol": {symbol}, "side": {side}, "type": {"MARKET"}, "newOrderRespType": {"FULL"}}
	if clientID != "" {
		q.Set("newClientOrderId", clientID)
	}
	if quantity != "" {
		q.Set("quantity", quantity)
	} else {
		q.Set("quoteOrderQty", quoteQty)
	}
	var r struct {
		OrderID             int64  `json:"orderId"`
		ExecutedQty         string `json:"executedQty"`
		CummulativeQuoteQty string `json:"cummulativeQuoteQty"`
		Fills               []struct {
			Commission      string `json:"commission"`
			CommissionAsset string `json:"commissionAsset"`
		} `json:"fills"`
	}
	if err := b.signed("POST", "/api/v3/order", q, &r); err != nil {
		return nil, err
	}
	res := &OrderResult{OrderID: r.OrderID, Commission: map[string]float64{}}
	res.ExecutedQty, _ = strconv.ParseFloat(r.ExecutedQty, 64)
	res.QuoteQty, _ = strconv.ParseFloat(r.CummulativeQuoteQty, 64)
	for _, f := range r.Fills {
		c, _ := strconv.ParseFloat(f.Commission, 64)
		res.Commission[f.CommissionAsset] += c
	}
	if res.ExecutedQty <= 0 {
		return nil, fmt.Errorf("a ordem não foi executada")
	}
	return res, nil
}

// floorStep arredonda para baixo no múltiplo de `step` e devolve como texto aceito pela Binance.
func floorStep(v, step float64) string {
	if step <= 0 {
		return strconv.FormatFloat(v, 'f', -1, 64)
	}
	dec := 0
	if s := strconv.FormatFloat(step, 'f', -1, 64); strings.Contains(s, ".") {
		dec = len(s) - strings.Index(s, ".") - 1
	}
	n := math.Floor(v/step + 1e-9)
	return strconv.FormatFloat(n*step, 'f', dec, 64)
}

func floorDec(v float64, dec int) string {
	p := math.Pow(10, float64(dec))
	return strconv.FormatFloat(math.Floor(v*p+1e-9)/p, 'f', dec, 64)
}

func intervalDur(iv string) time.Duration {
	m := map[string]time.Duration{
		"1m": time.Minute, "3m": 3 * time.Minute, "5m": 5 * time.Minute, "15m": 15 * time.Minute,
		"30m": 30 * time.Minute, "1h": time.Hour, "2h": 2 * time.Hour, "4h": 4 * time.Hour,
		"6h": 6 * time.Hour, "8h": 8 * time.Hour, "12h": 12 * time.Hour, "1d": 24 * time.Hour,
	}
	if d, ok := m[iv]; ok {
		return d
	}
	return time.Hour
}

// ---------- ordens de stop ----------

type OrderStatus struct {
	OrderID     int64   `json:"orderId"`
	Status      string  `json:"status"` // NEW, PARTIALLY_FILLED, FILLED, CANCELED, EXPIRED, REJECTED
	ExecutedQty float64 `json:"-"`
	QuoteQty    float64 `json:"-"`
}

func parseOrder(r map[string]any) *OrderStatus {
	f := func(k string) float64 {
		v, _ := strconv.ParseFloat(fmt.Sprint(r[k]), 64)
		return v
	}
	id, _ := r["orderId"].(float64)
	st, _ := r["status"].(string)
	return &OrderStatus{OrderID: int64(id), Status: st, ExecutedQty: f("executedQty"), QuoteQty: f("cummulativeQuoteQty")}
}

// StopLossLimit registra na Binance uma venda que dispara quando o preço cai até stopPrice.
func (b *Binance) StopLossLimit(symbol, qty, stopPrice, limitPrice, clientID string) (*OrderStatus, error) {
	q := url.Values{"symbol": {symbol}, "side": {"SELL"}, "type": {"STOP_LOSS_LIMIT"}, "timeInForce": {"GTC"},
		"quantity": {qty}, "stopPrice": {stopPrice}, "price": {limitPrice}, "newOrderRespType": {"RESULT"}}
	if clientID != "" {
		q.Set("newClientOrderId", clientID)
	}
	var r map[string]any
	if err := b.signed("POST", "/api/v3/order", q, &r); err != nil {
		return nil, err
	}
	return parseOrder(r), nil
}

// GetOrder consulta uma ordem pelo número (orderID) ou pelo identificador que o robô deu (clientID).
func (b *Binance) GetOrder(symbol string, orderID int64, clientID string) (*OrderStatus, error) {
	q := url.Values{"symbol": {symbol}}
	if orderID != 0 {
		q.Set("orderId", strconv.FormatInt(orderID, 10))
	} else {
		q.Set("origClientOrderId", clientID)
	}
	var r map[string]any
	if err := b.signed("GET", "/api/v3/order", q, &r); err != nil {
		return nil, err
	}
	return parseOrder(r), nil
}

func (b *Binance) CancelOrder(symbol string, orderID int64) (*OrderStatus, error) {
	q := url.Values{"symbol": {symbol}, "orderId": {strconv.FormatInt(orderID, 10)}}
	var r map[string]any
	if err := b.signed("DELETE", "/api/v3/order", q, &r); err != nil {
		return nil, err
	}
	return parseOrder(r), nil
}

// Commissions soma as taxas cobradas nas execuções de uma ordem.
func (b *Binance) Commissions(symbol string, orderID int64) (map[string]float64, error) {
	q := url.Values{"symbol": {symbol}, "orderId": {strconv.FormatInt(orderID, 10)}}
	var r []struct {
		Commission      string `json:"commission"`
		CommissionAsset string `json:"commissionAsset"`
	}
	if err := b.signed("GET", "/api/v3/myTrades", q, &r); err != nil {
		return nil, err
	}
	out := map[string]float64{}
	for _, t := range r {
		c, _ := strconv.ParseFloat(t.Commission, 64)
		out[t.CommissionAsset] += c
	}
	return out, nil
}

// floorTick arredonda um preço para baixo no passo de preço do par.
func floorTick(v, tick float64) string { return floorStep(v, tick) }

// KeyPerms = o que a chave de API tem permissão de fazer.
type KeyPerms struct {
	Reading           bool `json:"enableReading"`
	SpotTrading       bool `json:"enableSpotAndMarginTrading"`
	Withdrawals       bool `json:"enableWithdrawals"`
	InternalTransfer  bool `json:"enableInternalTransfer"`
	UniversalTransfer bool `json:"permitsUniversalTransfer"`
	IPRestrict        bool `json:"ipRestrict"`
}

// APIRestrictions consulta as permissões da chave (só existe na Binance normal, não na de teste).
func (b *Binance) APIRestrictions() (*KeyPerms, error) {
	var p KeyPerms
	if err := b.signed("GET", "/sapi/v1/account/apiRestrictions", url.Values{}, &p); err != nil {
		return nil, err
	}
	return &p, nil
}

// problem devolve o motivo para NÃO operar com esta chave ("" = pode).
func (p *KeyPerms) problem() string {
	switch {
	case p.Withdrawals:
		return "a chave de API permite SAQUES. Por segurança o robô não opera com ela: na Binance, edite a chave e desmarque \"Ativar saques\" (ou crie outra sem saque)"
	case p.InternalTransfer || p.UniversalTransfer:
		return "a chave de API permite TRANSFERÊNCIAS entre contas. Por segurança o robô não opera com ela: desmarque as transferências na Binance (ou crie outra chave só com leitura e negociação Spot)"
	case !p.SpotTrading:
		return "a chave de API não tem permissão de negociação Spot: na Binance, edite a chave e marque \"Ativar negociação Spot e de margem\""
	}
	return ""
}
