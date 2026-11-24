package main

// Ordens de verdade (modo real e modo Binance de teste):
//   - compra/venda a mercado, conferindo com a Binance se a conexão cair no meio;
//   - stop-loss REGISTRADO NA BINANCE logo depois da compra, que funciona mesmo com o PC desligado.

import (
	"fmt"
	"math"
	"time"
)

const (
	stopLimitGap = 0.005 // a ordem de stop vende até 0,5% abaixo do preço de stop (para garantir a execução)
	stopGapSell  = 0.015 // se o preço furar o stop em mais de 1,5% e a ordem não executar, o robô vende a mercado
)

// marketOrderLocked manda a ordem e, se a conexão cair, pergunta à Binance se ela foi executada.
func (b *Bot) marketOrderLocked(symbol, side, qty, quoteQty string) (*OrderResult, error) {
	cid := fmt.Sprintf("tm%s%d", map[string]string{"BUY": "b", "SELL": "s"}[side], time.Now().UnixMilli())
	r, err := b.bn.MarketOrder(symbol, side, qty, quoteQty, cid)
	if err == nil {
		return r, nil
	}
	if _, refused := err.(*APIError); refused {
		return nil, err // a Binance recusou: a ordem não existe
	}
	// Sem resposta: a ordem pode ou não ter entrado.
	for i := 0; i < 4; i++ {
		time.Sleep(time.Duration(i+1) * 2 * time.Second)
		o, e := b.bn.GetOrder(symbol, 0, cid)
		if ae, ok := e.(*APIError); ok && ae.Code == -2013 {
			return nil, err // a Binance não conhece a ordem: não entrou
		}
		if e != nil {
			continue
		}
		if o.ExecutedQty <= 0 {
			return nil, fmt.Errorf("a ordem não foi executada")
		}
		comm, ce := b.bn.Commissions(symbol, o.OrderID)
		if ce != nil {
			comm = map[string]float64{}
		}
		b.logf("info", "A conexão caiu durante a ordem, mas a Binance confirmou que ela foi executada.")
		return &OrderResult{OrderID: o.OrderID, ExecutedQty: o.ExecutedQty, QuoteQty: o.QuoteQty, Commission: comm}, nil
	}
	b.logf("warn", "A conexão caiu durante uma ordem de %s e não deu para confirmar se ela foi executada. CONFIRA NA BINANCE.", map[string]string{"BUY": "compra", "SELL": "venda"}[side])
	return nil, fmt.Errorf("sem confirmação da Binance (%v)", err)
}

func (b *Bot) sellExchangeLocked(price float64, reason string) {
	pos, info := b.st.Position, b.info
	if pos.StopOrderID != 0 {
		closed, err := b.cancelStopLocked()
		if err != nil {
			b.logf("err", "Venda (%s) não enviada: não consegui cancelar o stop na Binance (%v). Tento de novo na próxima checagem.", reason, err)
			return
		}
		if closed {
			return // o stop da Binance já tinha vendido
		}
	}
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
		b.logf("warn", "Só restam %s %s na conta (pequeno demais para vender; vendido fora do robô?). Posição encerrada no robô.", fmtQty(free[info.Base]), info.Base)
		b.st.Position = nil
		b.saveLocked()
		return
	}
	r, err := b.marketOrderLocked(pos.Symbol, "SELL", qs, "")
	if err != nil {
		b.logf("err", "Venda (%s) recusada: %v. Tento de novo na próxima checagem.", reason, err)
		b.stopRetryAt = time.Time{}
		return
	}
	b.recordSaleLocked(r.ExecutedQty, r.QuoteQty-r.Commission[info.Quote], r.QuoteQty/r.ExecutedQty, reason, true)
}

// cancelStopLocked tira a ordem de stop da Binance. closed=true se descobriu que ela já tinha vendido tudo.
func (b *Bot) cancelStopLocked() (closed bool, err error) {
	pos := b.st.Position
	o, err := b.bn.CancelOrder(pos.Symbol, pos.StopOrderID)
	if err != nil {
		// pode ter executado agora há pouco
		o2, err2 := b.bn.GetOrder(pos.Symbol, pos.StopOrderID, "")
		if err2 != nil {
			return false, err
		}
		o = o2
		if o.Status == "NEW" || o.Status == "PARTIALLY_FILLED" {
			return false, err // continua ativa e não cancelou
		}
	}
	if o.Status == "FILLED" {
		b.recordStopFillLocked(o, true)
		return true, nil
	}
	if o.ExecutedQty > 0 {
		b.recordStopFillLocked(o, false)
	}
	if b.st.Position != nil {
		b.st.Position.StopOrderID, b.st.Position.StopOrderPrice = 0, 0
	}
	return b.st.Position == nil, nil
}

// recordStopFillLocked registra a venda feita pela ordem de stop da Binance.
func (b *Bot) recordStopFillLocked(o *OrderStatus, closeAll bool) {
	info := b.info
	comm, err := b.bn.Commissions(b.st.Position.Symbol, o.OrderID)
	proceeds := o.QuoteQty * (1 - paperFeePct/100) // estimativa se não der para ler a taxa
	if err == nil {
		proceeds = o.QuoteQty - comm[info.Quote]
	}
	avg := 0.0
	if o.ExecutedQty > 0 {
		avg = o.QuoteQty / o.ExecutedQty
	}
	b.recordSaleLocked(o.ExecutedQty, proceeds, avg, "stop-loss na Binance", closeAll)
}

// syncStopLocked confere a ordem de stop na Binance e a mantém igual ao stop da posição.
func (b *Bot) syncStopLocked(price float64) {
	pos, info := b.st.Position, b.info
	if pos == nil || info == nil {
		return
	}

	// 1) A ordem que já está lá: executou? foi cancelada por alguém?
	if pos.StopOrderID != 0 {
		o, err := b.bn.GetOrder(pos.Symbol, pos.StopOrderID, "")
		if err != nil {
			b.logf("err", "Não consegui conferir o stop na Binance: %v", err)
			return
		}
		switch o.Status {
		case "FILLED":
			b.recordStopFillLocked(o, true)
			return
		case "NEW", "PARTIALLY_FILLED":
			// preço caiu rápido e passou do limite da ordem: ela pode nunca executar. Vende a mercado.
			if price < pos.StopOrderPrice*(1-stopGapSell) {
				b.logf("warn", "O preço caiu rápido e passou do limite da ordem de stop. Vendendo a mercado.")
				b.sellExchangeLocked(price, "stop-loss (queda rápida)")
				return
			}
		default: // CANCELED, EXPIRED, REJECTED...
			if o.ExecutedQty > 0 {
				b.recordStopFillLocked(o, false)
				if b.st.Position == nil {
					return
				}
			}
			b.logf("warn", "A ordem de stop na Binance foi cancelada fora do robô (%s). Registrando de novo.", o.Status)
			pos = b.st.Position
			pos.StopOrderID, pos.StopOrderPrice = 0, 0
			b.saveLocked()
		}
	}

	// 2) A ordem está igual ao stop desejado?
	want := pos.Stop
	if pos.StopOrderID != 0 && math.Abs(pos.StopOrderPrice-want) < 1e-12 {
		return
	}
	if time.Now().Before(b.stopRetryAt) {
		return
	}
	if pos.StopOrderID != 0 { // stop mudou (ajustes): troca a ordem
		closed, err := b.cancelStopLocked()
		if err != nil {
			b.logf("err", "Não consegui trocar o stop na Binance: %v", err)
			b.stopRetryAt = time.Now().Add(2 * time.Minute)
			return
		}
		if closed {
			return
		}
		b.saveLocked()
	}
	if want <= 0 {
		return // stop-loss desligado
	}
	if price <= want*1.001 {
		// já está no stop ou abaixo: a Binance recusaria a ordem; vende direto
		b.sellExchangeLocked(price, "stop-loss")
		return
	}

	// 3) Registra a ordem de stop.
	free, _, err := b.bn.Balances()
	if err != nil {
		b.logf("err", "Não consegui registrar o stop na Binance: %v", err)
		b.stopRetryAt = time.Now().Add(2 * time.Minute)
		return
	}
	q := math.Min(pos.Qty, free[info.Base])
	qs := floorStep(q, info.StepSize)
	var qv float64
	fmt.Sscan(qs, &qv)
	limit := want * (1 - stopLimitGap)
	if qv < info.MinQty || qv*limit < info.MinNotional {
		if free[info.Base] < pos.Qty*0.5 {
			b.logf("warn", "Só restam %s %s na conta: parece que a posição foi vendida fora do robô. Posição encerrada no robô.", fmtQty(free[info.Base]), info.Base)
			b.st.Position = nil
			b.saveLocked()
			return
		}
		b.logf("warn", "Posição pequena demais para uma ordem de stop na Binance (mínimo %.2f %s). O robô vigia o stop pelo PC.", info.MinNotional, info.Quote)
		b.stopRetryAt = time.Now().Add(6 * time.Hour)
		return
	}
	o, err := b.bn.StopLossLimit(pos.Symbol, qs, floorTick(want, info.TickSize), floorTick(limit, info.TickSize),
		fmt.Sprintf("tmstop%d", time.Now().UnixMilli()))
	if err != nil {
		b.logf("err", "Não consegui registrar o stop-loss na Binance: %v. Enquanto isso, o robô vigia o stop pelo PC.", err)
		b.stopRetryAt = time.Now().Add(5 * time.Minute)
		return
	}
	pos.StopOrderID, pos.StopOrderPrice = o.OrderID, want
	b.logf("ok", "Stop-loss registrado na Binance a %s (ordem %d). Ele funciona mesmo com o PC desligado.", fmtPrice(want), o.OrderID)
	b.saveLocked()
}
