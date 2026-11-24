package main

// Testes do modo real/teste contra uma Binance falsa (o servidor imita a API Spot da Binance).

import (
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"
)

const fkKey, fkSecret = "chave-teste-123456", "segredo-teste-abcdef"

type fakeOrder struct {
	ID          int64
	ClientID    string
	Type        string
	Status      string
	Qty         float64
	StopPrice   float64
	Limit       float64
	ExecutedQty float64
	QuoteQty    float64
	CommQuote   float64
}

type fakeBinance struct {
	mu        sync.Mutex
	t         *testing.T
	price     float64
	free      map[string]float64
	locked    map[string]float64
	orders    map[int64]*fakeOrder
	nextID    int64
	dropNext  bool // executa a próxima ordem a mercado mas derruba a conexão sem responder
	noFill    bool // ordens de stop não executam (simula preço que despenca e pula o limite)
	srv       *httptest.Server
	badSigned int
	perms     map[string]bool // permissões da chave (apiRestrictions)
}

func newFake(t *testing.T) *fakeBinance {
	f := &fakeBinance{t: t, price: 50000, free: map[string]float64{"USDT": 1000}, locked: map[string]float64{},
		orders: map[int64]*fakeOrder{}, nextID: 100,
		perms: map[string]bool{"enableReading": true, "enableSpotAndMarginTrading": true}}
	f.srv = httptest.NewServer(http.HandlerFunc(f.handle))
	t.Cleanup(f.srv.Close)
	return f
}

func apiErr(w http.ResponseWriter, code int, msg string) {
	w.WriteHeader(400)
	json.NewEncoder(w).Encode(map[string]any{"code": code, "msg": msg})
}

func (f *fakeBinance) orderJSON(o *fakeOrder) map[string]any {
	return map[string]any{"orderId": o.ID, "clientOrderId": o.ClientID, "status": o.Status, "type": o.Type,
		"executedQty": strconv.FormatFloat(o.ExecutedQty, 'f', 8, 64), "cummulativeQuoteQty": strconv.FormatFloat(o.QuoteQty, 'f', 8, 64)}
}

func (f *fakeBinance) handle(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	defer f.mu.Unlock()
	q := r.URL.Query()
	path := r.URL.Path
	if path == "/api/v3/account" || path == "/api/v3/order" || path == "/api/v3/myTrades" || path == "/sapi/v1/account/apiRestrictions" {
		raw := r.URL.RawQuery
		i := strings.LastIndex(raw, "&signature=")
		if i < 0 || sign(fkSecret, raw[:i]) != raw[i+len("&signature="):] || r.Header.Get("X-MBX-APIKEY") != fkKey {
			f.badSigned++
			apiErr(w, -1022, "Signature for this request is not valid.")
			return
		}
	}
	switch {
	case path == "/sapi/v1/account/apiRestrictions":
		json.NewEncoder(w).Encode(f.perms)
	case path == "/api/v3/exchangeInfo":
		json.NewEncoder(w).Encode(map[string]any{"symbols": []any{map[string]any{
			"symbol": "BTCUSDT", "status": "TRADING", "baseAsset": "BTC", "quoteAsset": "USDT", "quoteAssetPrecision": 8,
			"filters": []any{
				map[string]any{"filterType": "PRICE_FILTER", "tickSize": "0.01000000"},
				map[string]any{"filterType": "LOT_SIZE", "minQty": "0.00001000", "stepSize": "0.00001000"},
				map[string]any{"filterType": "NOTIONAL", "minNotional": "5.00000000"},
			}}}})
	case path == "/api/v3/klines":
		n, _ := strconv.Atoi(q.Get("limit"))
		var out [][]any
		t0 := time.Now().Add(-time.Duration(n) * time.Hour).UnixMilli()
		for i := 0; i < n; i++ {
			p := strconv.FormatFloat(f.price, 'f', 2, 64)
			out = append(out, []any{t0 + int64(i)*3600000, p, p, p, p, "1", t0 + int64(i+1)*3600000 - 1})
		}
		json.NewEncoder(w).Encode(out)
	case path == "/api/v3/account":
		var bal []any
		for a, v := range f.free {
			bal = append(bal, map[string]any{"asset": a, "free": strconv.FormatFloat(v, 'f', 8, 64), "locked": strconv.FormatFloat(f.locked[a], 'f', 8, 64)})
		}
		json.NewEncoder(w).Encode(map[string]any{"balances": bal, "canTrade": true})
	case path == "/api/v3/order" && r.Method == "POST":
		f.newOrder(w, r, q)
	case path == "/api/v3/order" && r.Method == "GET":
		o := f.find(q)
		if o == nil {
			apiErr(w, -2013, "Order does not exist.")
			return
		}
		json.NewEncoder(w).Encode(f.orderJSON(o))
	case path == "/api/v3/order" && r.Method == "DELETE":
		o := f.find(q)
		if o == nil || o.Status != "NEW" {
			apiErr(w, -2011, "Unknown order sent.")
			return
		}
		o.Status = "CANCELED"
		f.locked["BTC"] -= o.Qty
		f.free["BTC"] += o.Qty
		json.NewEncoder(w).Encode(f.orderJSON(o))
	case path == "/api/v3/myTrades":
		id, _ := strconv.ParseInt(q.Get("orderId"), 10, 64)
		o := f.orders[id]
		if o == nil || o.ExecutedQty == 0 {
			json.NewEncoder(w).Encode([]any{})
			return
		}
		json.NewEncoder(w).Encode([]any{map[string]any{"commission": strconv.FormatFloat(o.CommQuote, 'f', 8, 64), "commissionAsset": "USDT"}})
	default:
		http.NotFound(w, r)
	}
}

func (f *fakeBinance) find(q map[string][]string) *fakeOrder {
	if id, err := strconv.ParseInt(first(q["orderId"]), 10, 64); err == nil {
		return f.orders[id]
	}
	cid := first(q["origClientOrderId"])
	for _, o := range f.orders {
		if o.ClientID == cid {
			return o
		}
	}
	return nil
}

func first(v []string) string {
	if len(v) == 0 {
		return ""
	}
	return v[0]
}

func (f *fakeBinance) newOrder(w http.ResponseWriter, r *http.Request, q map[string][]string) {
	pf := func(k string) float64 { v, _ := strconv.ParseFloat(first(q[k]), 64); return v }
	f.nextID++
	o := &fakeOrder{ID: f.nextID, ClientID: first(q["newClientOrderId"]), Type: first(q["type"])}
	side := first(q["side"])
	switch o.Type {
	case "MARKET":
		if side == "BUY" {
			quote := pf("quoteOrderQty")
			if quote > f.free["USDT"] {
				apiErr(w, -2010, "Account has insufficient balance for requested action.")
				return
			}
			qty := quote / f.price
			f.free["USDT"] -= quote
			f.free["BTC"] += qty * 0.999 // taxa cobrada em BTC
			o.ExecutedQty, o.QuoteQty, o.Status = qty, quote, "FILLED"
			f.orders[o.ID] = o
			if f.dropNext {
				f.dropNext = false
				hj, _ := w.(http.Hijacker)
				c, _, _ := hj.Hijack()
				c.Close()
				return
			}
			json.NewEncoder(w).Encode(map[string]any{"orderId": o.ID, "status": "FILLED", "executedQty": strconv.FormatFloat(qty, 'f', 8, 64),
				"cummulativeQuoteQty": strconv.FormatFloat(quote, 'f', 8, 64),
				"fills":               []any{map[string]any{"commission": strconv.FormatFloat(qty*0.001, 'f', 8, 64), "commissionAsset": "BTC"}}})
			return
		}
		qty := pf("quantity")
		if qty > f.free["BTC"]+1e-12 {
			apiErr(w, -2010, "Account has insufficient balance for requested action.")
			return
		}
		gross := qty * f.price
		f.free["BTC"] -= qty
		f.free["USDT"] += gross * 0.999
		o.ExecutedQty, o.QuoteQty, o.CommQuote, o.Status = qty, gross, gross*0.001, "FILLED"
		f.orders[o.ID] = o
		json.NewEncoder(w).Encode(map[string]any{"orderId": o.ID, "status": "FILLED", "executedQty": first(q["quantity"]),
			"cummulativeQuoteQty": strconv.FormatFloat(gross, 'f', 8, 64),
			"fills":               []any{map[string]any{"commission": strconv.FormatFloat(gross*0.001, 'f', 8, 64), "commissionAsset": "USDT"}}})
	case "STOP_LOSS_LIMIT":
		qty := pf("quantity")
		if first(q["timeInForce"]) != "GTC" || pf("stopPrice") <= 0 || pf("price") <= 0 {
			apiErr(w, -1102, "Mandatory parameter missing.")
			return
		}
		if qty > f.free["BTC"]+1e-12 {
			apiErr(w, -2010, "Account has insufficient balance for requested action.")
			return
		}
		if pf("stopPrice") >= f.price {
			apiErr(w, -2010, "Stop price would trigger immediately.")
			return
		}
		f.free["BTC"] -= qty
		f.locked["BTC"] += qty
		o.Qty, o.StopPrice, o.Limit, o.Status = qty, pf("stopPrice"), pf("price"), "NEW"
		f.orders[o.ID] = o
		json.NewEncoder(w).Encode(f.orderJSON(o))
	default:
		apiErr(w, -1116, "Invalid orderType.")
	}
}

// setPrice muda o preço e executa as ordens de stop atingidas.
func (f *fakeBinance) setPrice(p float64) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.price = p
	if f.noFill {
		return
	}
	for _, o := range f.orders {
		if o.Status == "NEW" && o.Type == "STOP_LOSS_LIMIT" && p <= o.StopPrice {
			gross := o.Qty * o.Limit
			f.locked["BTC"] -= o.Qty
			f.free["USDT"] += gross * 0.999
			o.ExecutedQty, o.QuoteQty, o.CommQuote, o.Status = o.Qty, gross, gross*0.001, "FILLED"
		}
	}
}

func (f *fakeBinance) openStops() []*fakeOrder {
	f.mu.Lock()
	defer f.mu.Unlock()
	var out []*fakeOrder
	for _, o := range f.orders {
		if o.Type == "STOP_LOSS_LIMIT" && o.Status == "NEW" {
			out = append(out, o)
		}
	}
	return out
}

func newExchangeBot(t *testing.T, f *fakeBinance) *Bot {
	b := NewBot(t.TempDir())
	b.cfg.Mode = "teste"
	b.cfg.TestAPIKey, b.cfg.TestAPISecret = fkKey, fkSecret
	b.cfg.TradePct = 50
	b.cfg.Params.StopLossPct = 6
	b.bn = &Binance{Key: fkKey, Secret: fkSecret, TradeHost: f.srv.URL, DataHosts: []string{f.srv.URL}, client: &http.Client{Timeout: 5 * time.Second}}
	info, err := b.bn.Symbol("BTCUSDT")
	if err != nil {
		t.Fatal(err)
	}
	b.info = info
	return b
}

func lastTrade(b *Bot) Trade { return b.st.Trades[len(b.st.Trades)-1] }

func TestExchangeBuyRegistersStopThatFillsWithPCOff(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	defer b.mu.Unlock()

	b.buyLocked(50000)
	p := b.st.Position
	if p == nil || math.Abs(p.Cost-500) > 1e-6 || math.Abs(p.Entry-50000) > 1e-6 {
		t.Fatalf("compra errada: %+v", p)
	}
	if p.StopOrderID == 0 || p.StopOrderPrice != 47000 {
		t.Fatalf("stop não foi registrado na Binance: %+v", p)
	}
	stops := f.openStops()
	if len(stops) != 1 || stops[0].StopPrice != 47000 || stops[0].Limit != 46765 {
		t.Fatalf("ordem de stop errada: %+v", stops)
	}

	// O preço cai com o "PC desligado": a Binance executa o stop sozinha.
	f.setPrice(46900)
	// Quando o robô volta, ele descobre e registra a venda.
	b.syncStopLocked(46900)
	if b.st.Position != nil {
		t.Fatalf("posição deveria ter sido encerrada pelo stop")
	}
	tr := lastTrade(b)
	if tr.Side != "VENDA" || tr.Reason != "stop-loss na Binance" || tr.PnL >= 0 || math.Abs(tr.Price-46765) > 1e-6 {
		t.Fatalf("venda pelo stop registrada errado: %+v", tr)
	}
	if f.badSigned != 0 {
		t.Fatalf("%d pedidos com assinatura inválida", f.badSigned)
	}
}

func TestExchangeSellCancelsStopFirst(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked(50000)
	f.setPrice(52000)
	b.sellLocked(52000, "sinal")
	if b.st.Position != nil {
		t.Fatalf("posição deveria estar fechada; registro: %+v", b.st.Logs)
	}
	if len(f.openStops()) != 0 {
		t.Fatalf("o stop deveria ter sido cancelado")
	}
	tr := lastTrade(b)
	if tr.Reason != "sinal" || tr.PnL <= 0 {
		t.Fatalf("venda errada: %+v", tr)
	}
	if f.free["BTC"] > 0.00001 || f.locked["BTC"] != 0 {
		t.Fatalf("sobrou BTC: livre=%v travado=%v", f.free["BTC"], f.locked["BTC"])
	}
}

func TestExchangeStopIsReplacedWhenSettingsChange(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	b.buyLocked(50000)
	oldID := b.st.Position.StopOrderID
	nc := b.cfg
	nc.Params.StopLossPct = 3
	b.mu.Unlock()
	if err := b.UpdateConfig(nc); err != nil {
		t.Fatal(err)
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	b.syncStopLocked(50000)
	p := b.st.Position
	if p.StopOrderID == oldID || p.StopOrderPrice != 48500 {
		t.Fatalf("stop não foi trocado: %+v", p)
	}
	if s := f.openStops(); len(s) != 1 || s[0].StopPrice != 48500 {
		t.Fatalf("deveria haver só a ordem nova: %+v", s)
	}
}

func TestExchangeStopCanceledOutsideIsRegisteredAgain(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked(50000)
	id := b.st.Position.StopOrderID
	// alguém cancela a ordem no app da Binance
	f.mu.Lock()
	f.orders[id].Status = "CANCELED"
	f.locked["BTC"] -= f.orders[id].Qty
	f.free["BTC"] += f.orders[id].Qty
	f.mu.Unlock()

	b.syncStopLocked(50000)
	if p := b.st.Position; p == nil || p.StopOrderID == 0 || p.StopOrderID == id {
		t.Fatalf("stop deveria ter sido registrado de novo: %+v", p)
	}
}

func TestExchangeSoldOutsideClosesPosition(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked(50000)
	id := b.st.Position.StopOrderID
	// a pessoa cancela o stop e vende tudo pelo app
	f.mu.Lock()
	f.orders[id].Status = "CANCELED"
	f.locked["BTC"], f.free["BTC"] = 0, 0
	f.mu.Unlock()
	b.syncStopLocked(50000)
	if b.st.Position != nil {
		t.Fatalf("posição deveria ter sido encerrada no robô")
	}
}

func TestExchangeFastDropSellsAtMarket(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked(50000)
	f.noFill = true
	f.setPrice(45000) // pulou o limite da ordem de stop (46.765)
	b.syncStopLocked(45000)
	if b.st.Position != nil {
		t.Fatalf("deveria ter vendido a mercado")
	}
	if tr := lastTrade(b); tr.Reason != "stop-loss (queda rápida)" || math.Abs(tr.Price-45000) > 1e-6 {
		t.Fatalf("venda errada: %+v", tr)
	}
	if len(f.openStops()) != 0 {
		t.Fatalf("a ordem de stop deveria ter sido cancelada")
	}
}

func TestExchangeConnectionDropDuringBuyIsReconciled(t *testing.T) {
	if testing.Short() {
		t.Skip("espera alguns segundos")
	}
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.bn.client.Timeout = 2 * time.Second
	f.dropNext = true
	b.mu.Lock()
	defer b.mu.Unlock()
	b.buyLocked(50000)
	p := b.st.Position
	if p == nil || math.Abs(p.Cost-500) > 1e-6 {
		t.Fatalf("a compra executada deveria ter sido reconhecida: %+v\n%+v", p, b.st.Logs)
	}
	if p.StopOrderID == 0 {
		t.Fatalf("stop deveria ter sido registrado")
	}
}

func TestExchangeTickWithBotOff(t *testing.T) {
	f := newFake(t)
	b := newExchangeBot(t, f)
	b.mu.Lock()
	b.buyLocked(50000)
	b.st.Running = false
	b.mu.Unlock()
	f.setPrice(46000) // stop executa na Binance
	b.tick()          // robô desligado, mas confere o stop
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.st.Position != nil {
		t.Fatalf("mesmo desligado, o robô deveria registrar a venda feita pelo stop")
	}
	if b.live.Price != 46000 || b.live.QuoteBal <= 0 {
		t.Fatalf("painel não atualizou: %+v", b.live)
	}
}

func TestTelegramAlerts(t *testing.T) {
	got := make(chan string, 10)
	tg := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasSuffix(r.URL.Path, "/sendMessage"):
			if !strings.HasPrefix(r.URL.Path, "/botTOKEN123/") || r.FormValue("chat_id") != "42" {
				w.WriteHeader(401)
				fmt.Fprint(w, `{"ok":false,"description":"Unauthorized"}`)
				return
			}
			got <- r.FormValue("text")
			fmt.Fprint(w, `{"ok":true,"result":{}}`)
		case strings.HasSuffix(r.URL.Path, "/getUpdates"):
			fmt.Fprint(w, `{"ok":true,"result":[{"message":{"chat":{"id":42,"first_name":"Ana"}}}]}`)
		}
	}))
	defer tg.Close()
	old := telegramAPI
	telegramAPI = tg.URL
	defer func() { telegramAPI = old }()

	id, name, err := telegramFindChat(http.DefaultClient, "TOKEN123")
	if err != nil || id != "42" || name != "Ana" {
		t.Fatalf("findChat: %v %v %v", id, name, err)
	}
	if err := telegramSend(http.DefaultClient, "ERRADO", "42", "x"); err == nil {
		t.Fatalf("token errado deveria dar erro")
	}

	f := newFake(t)
	b := newExchangeBot(t, f)
	b.tgToken, b.tgChat = "TOKEN123", "42"
	b.mu.Lock()
	b.buyLocked(50000)
	b.mu.Unlock()
	timeout := time.After(5 * time.Second)
	var msgs []string
	for len(msgs) < 1 {
		select {
		case m := <-got:
			msgs = append(msgs, m)
		case <-timeout:
			t.Fatalf("nenhum aviso chegou")
		}
	}
	if !strings.Contains(msgs[0], "COMPRA") || !strings.Contains(msgs[0], "Stop-loss: 47000.00") {
		t.Fatalf("aviso de compra estranho: %q", msgs[0])
	}
}
