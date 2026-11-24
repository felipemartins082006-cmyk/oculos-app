package main

// Comandos pelo Telegram: o robô obedece SÓ ao chat configurado em Ajustes.
//   /status          preço, posição, resultado
//   /parar  /ligar   desliga / liga o robô (no modo real: /ligar real)
//   /vender          pergunta; /vender sim  vende a posição inteira a mercado
//   /ajuda           lista os comandos

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const tgMaxAge = 2 * time.Minute // comandos mais velhos que isso (ex.: mandados com o robô fechado) são ignorados

type tgUpdate struct {
	UpdateID int64 `json:"update_id"`
	Message  *struct {
		Date int64  `json:"date"`
		Text string `json:"text"`
		Chat struct {
			ID int64 `json:"id"`
		} `json:"chat"`
	} `json:"message"`
}

// TelegramCommands fica ouvindo o Telegram (long polling) até `stop` ser fechado (nil = para sempre).
func (b *Bot) TelegramCommands(stop <-chan struct{}) {
	client := &http.Client{Timeout: 70 * time.Second}
	var offset int64
	registered := ""
	wait := func(d time.Duration) bool {
		select {
		case <-stop:
			return false
		case <-time.After(d):
			return true
		}
	}
	for {
		select {
		case <-stop:
			return
		default:
		}
		b.tgMu.Lock()
		tok, chat := b.tgToken, b.tgChat
		b.tgMu.Unlock()
		if tok == "" || chat == "" {
			if !wait(10 * time.Second) {
				return
			}
			continue
		}
		if registered != tok {
			b.registerTelegramMenu(client, tok)
			registered = tok
		}
		ups, err := telegramUpdates(client, tok, offset, 50)
		if err != nil {
			log.Printf("[telegram] comandos: %v", err)
			if !wait(15 * time.Second) {
				return
			}
			continue
		}
		for _, u := range ups {
			offset = u.UpdateID + 1
			if reply := b.handleTelegramUpdate(u, chat); reply != "" {
				if err := telegramSend(client, tok, chat, reply); err != nil {
					log.Printf("[telegram] resposta: %v", err)
				}
			}
		}
	}
}

func telegramUpdates(c *http.Client, token string, offset int64, timeout int) ([]tgUpdate, error) {
	raw, err := telegramCall(c, token, "getUpdates", url.Values{
		"offset": {strconv.FormatInt(offset, 10)}, "timeout": {strconv.Itoa(timeout)}, "allowed_updates": {`["message"]`}})
	if err != nil {
		return nil, err
	}
	var ups []tgUpdate
	return ups, json.Unmarshal(raw, &ups)
}

func (b *Bot) registerTelegramMenu(c *http.Client, token string) {
	cmds, _ := json.Marshal([]map[string]string{
		{"command": "status", "description": "Preço, posição e resultado"},
		{"command": "vender", "description": "Vender a posição aberta"},
		{"command": "parar", "description": "Desligar o robô"},
		{"command": "ligar", "description": "Ligar o robô"},
		{"command": "ajuda", "description": "Lista de comandos"},
	})
	if _, err := telegramCall(c, token, "setMyCommands", url.Values{"commands": {string(cmds)}}); err != nil {
		log.Printf("[telegram] menu: %v", err)
	}
}

// handleTelegramUpdate decide a resposta para uma mensagem. "" = não responde.
func (b *Bot) handleTelegramUpdate(u tgUpdate, chat string) string {
	m := u.Message
	if m == nil || strings.TrimSpace(m.Text) == "" {
		return ""
	}
	if strconv.FormatInt(m.Chat.ID, 10) != chat {
		b.mu.Lock()
		b.logf("warn", "Mensagem no Telegram de um chat que não é o seu (%d) foi ignorada.", m.Chat.ID)
		b.mu.Unlock()
		return ""
	}
	if age := time.Since(time.Unix(m.Date, 0)); age > tgMaxAge {
		return fmt.Sprintf("⌛ Ignorei \"%s\": foi mandado há %s (talvez com o robô fechado). Mande de novo se ainda quiser.", m.Text, fmtDur(age))
	}

	f := strings.Fields(strings.ToLower(m.Text))
	cmd := strings.TrimPrefix(f[0], "/")
	if i := strings.Index(cmd, "@"); i >= 0 {
		cmd = cmd[:i] // "/status@MeuBot"
	}
	arg := ""
	if len(f) > 1 {
		arg = f[1]
	}

	switch cmd {
	case "status", "s":
		return b.statusText()
	case "parar", "desligar", "stop":
		b.mu.Lock()
		running := b.st.Running
		b.mu.Unlock()
		if !running {
			return "O robô já está desligado."
		}
		b.Stop()
		return "" // o próprio Stop já manda o aviso
	case "ligar":
		b.mu.Lock()
		mode, running := b.cfg.Mode, b.st.Running
		b.mu.Unlock()
		if running {
			return "O robô já está ligado."
		}
		if mode == "real" && arg != "real" {
			return "⚠️ O robô está no modo REAL (dinheiro de verdade). Para confirmar, mande:\n/ligar real"
		}
		if err := b.Start(mode == "real"); err != nil {
			return "❌ Não liguei: " + err.Error()
		}
		return ""
	case "vender", "sell":
		b.mu.Lock()
		p, price, base, quote := b.st.Position, b.live.Price, b.live.Base, b.live.Quote
		var qty, now float64
		if p != nil {
			qty = p.Qty
			now = p.Qty*price*(1-paperFeePct/100) - p.Cost
		}
		b.mu.Unlock()
		if p == nil {
			return "Não há posição aberta."
		}
		if arg != "sim" {
			return fmt.Sprintf("Vender %s %s a preço de mercado (resultado agora: %+.2f %s)?\nPara confirmar, mande:\n/vender sim", fmtQty(qty), base, now, quote)
		}
		if err := b.SellNow(); err != nil {
			return "❌ Não vendi: " + err.Error()
		}
		return "" // a venda já manda o aviso com o resultado
	case "start", "ajuda", "help", "menu": // /start = primeira mensagem para o bot
		return helpText
	}
	return "Não entendi. " + helpText
}

const helpText = `Comandos do TradeMartins:
/status — preço, posição e resultado
/vender — vender a posição aberta (pede confirmação)
/parar — desligar o robô
/ligar — ligar o robô
/ajuda — esta lista`

// statusText = resumo para o Telegram.
func (b *Bot) statusText() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	c, L := b.cfg, b.live
	q := L.Quote
	var sb strings.Builder
	fmt.Fprintf(&sb, "📊 %s — velas de %s%s\nEstratégia: %s\n", c.Symbol, c.Interval, modeTag(c.Mode), strategyNames[c.Params.Strategy])
	switch {
	case b.st.Running && b.st.DayBlocked:
		sb.WriteString("Robô: LIGADO (sem novas compras hoje: limite de perda do dia)\n")
	case b.st.Running:
		sb.WriteString("Robô: LIGADO ✅\n")
	default:
		sb.WriteString("Robô: DESLIGADO ⏸️\n")
	}
	if L.Price > 0 {
		fmt.Fprintf(&sb, "Preço: %s %s\n", fmtPrice(L.Price), q)
		fmt.Fprintf(&sb, "Patrimônio: %.2f %s\n", L.Equity, q)
	} else {
		sb.WriteString("Preço: ainda carregando\n")
	}
	if p := b.st.Position; p != nil {
		now := p.Qty*L.Price*(1-paperFeePct/100) - p.Cost
		fmt.Fprintf(&sb, "\nPosição: %s %s comprada a %s\n", fmtQty(p.Qty), L.Base, fmtPrice(p.Entry))
		if L.Price > 0 && p.Cost > 0 {
			fmt.Fprintf(&sb, "Resultado agora: %+.2f %s (%+.2f%%)\n", now, q, now/p.Cost*100)
		}
		switch {
		case p.Stop > 0 && p.StopOrderID != 0:
			fmt.Fprintf(&sb, "Stop-loss: %s (registrado na Binance ✔)\n", fmtPrice(p.Stop))
		case p.Stop > 0:
			fmt.Fprintf(&sb, "Stop-loss: %s (vigiado pelo robô)\n", fmtPrice(p.Stop))
		}
		if p.Take > 0 {
			fmt.Fprintf(&sb, "Take-profit: %s\n", fmtPrice(p.Take))
		}
	} else {
		sb.WriteString("\nSem posição aberta.\n")
	}
	pnl, wins, losses := b.summaryLocked(c.Mode)
	if n := wins + losses; n > 0 {
		fmt.Fprintf(&sb, "\nVendas: %d (%d com lucro), resultado total %+.2f %s\n", n, wins, pnl, q)
	}
	if L.LastError != "" {
		fmt.Fprintf(&sb, "\n⚠️ Problema: %s\n", L.LastError)
	}
	return strings.TrimRight(sb.String(), "\n")
}

// summaryLocked soma o resultado das vendas de um modo.
func (b *Bot) summaryLocked(mode string) (pnl float64, wins, losses int) {
	for _, t := range b.st.Trades {
		if t.Mode != mode || t.Side != "VENDA" {
			continue
		}
		pnl += t.PnL
		if t.PnL > 0 {
			wins++
		} else {
			losses++
		}
	}
	return
}
