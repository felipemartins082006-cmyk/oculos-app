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
		return b.tgSell(f[1:])
	case "start", "ajuda", "help", "menu": // /start = primeira mensagem para o bot
		return helpText
	}
	return "Não entendi. " + helpText
}

const helpText = `Comandos do TradeMartins:
/status — preços, posições e resultado
/vender — vender (pede confirmação). Ex.: /vender BTCUSDT sim  ou  /vender tudo sim
/parar — desligar o robô
/ligar — ligar o robô
/ajuda — esta lista`

// tgSell trata "/vender", "/vender sim", "/vender BTC", "/vender BTCUSDT sim", "/vender tudo sim".
func (b *Bot) tgSell(args []string) string {
	confirm := len(args) > 0 && args[len(args)-1] == "sim"
	if confirm {
		args = args[:len(args)-1]
	}
	b.mu.Lock()
	open := []string{}
	for _, s := range append(b.cfg.pairs(), extraPositions(b)...) {
		if b.st.Positions[s] != nil {
			open = append(open, s)
		}
	}
	line := func(s string) string {
		p, px := b.st.Positions[s], b.priceLocked(s)
		now := p.Qty*px*(1-paperFeePct/100) - p.Cost
		base := s
		if in := b.infos[s]; in != nil {
			base = in.Base
		}
		return fmt.Sprintf("• %s: %s %s, resultado agora %+.2f %s", s, fmtQty(p.Qty), base, now, b.quoteLocked())
	}
	var lines []string
	for _, s := range open {
		lines = append(lines, line(s))
	}
	b.mu.Unlock()

	if len(open) == 0 {
		return "Não há posição aberta."
	}
	target := ""
	switch {
	case len(args) == 0 && len(open) == 1:
		target = open[0]
	case len(args) == 0:
		return "Posições abertas:\n" + strings.Join(lines, "\n") + "\n\nPara vender uma, mande por exemplo:\n/vender " + open[0] + " sim\nPara vender todas:\n/vender tudo sim"
	case args[0] == "tudo" || args[0] == "todas":
		target = "*"
	default:
		want := strings.ToUpper(args[0])
		for _, s := range open {
			if s == want || strings.TrimSuffix(s, b.quoteForTG()) == want {
				target = s
			}
		}
		if target == "" {
			return fmt.Sprintf("Não há posição aberta em %s.\nPosições abertas:\n%s", want, strings.Join(lines, "\n"))
		}
	}
	if !confirm {
		if target == "*" {
			return "Vender TODAS as posições a preço de mercado?\n" + strings.Join(lines, "\n") + "\n\nPara confirmar, mande:\n/vender tudo sim"
		}
		for i, s := range open {
			if s == target {
				return "Vender a preço de mercado?\n" + lines[i] + "\n\nPara confirmar, mande:\n/vender " + target + " sim"
			}
		}
	}
	sym := target
	if target == "*" {
		sym = ""
	}
	if err := b.SellNow(sym); err != nil {
		return "❌ Não vendi: " + err.Error()
	}
	return "" // cada venda já manda o aviso com o resultado
}

func (b *Bot) quoteForTG() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.quoteLocked()
}

// extraPositions = posições abertas em pares que não estão mais na carteira.
func extraPositions(b *Bot) []string {
	var out []string
	for s := range b.st.Positions {
		if !b.cfg.hasPair(s) {
			out = append(out, s)
		}
	}
	return out
}

// statusText = resumo para o Telegram.
func (b *Bot) statusText() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	c, L := b.cfg, b.live
	q := b.quoteLocked()
	var sb strings.Builder
	fmt.Fprintf(&sb, "📊 %s%s\nEstratégia: %s\n", b.descLocked(), modeTag(c.Mode), strategyNames[c.Params.Strategy])
	switch {
	case b.st.Running && b.st.DayBlocked:
		sb.WriteString("Robô: LIGADO (sem novas compras hoje: limite de perda do dia)\n")
	case b.st.Running:
		sb.WriteString("Robô: LIGADO ✅\n")
	default:
		sb.WriteString("Robô: DESLIGADO ⏸️\n")
	}
	if L.Equity > 0 {
		fmt.Fprintf(&sb, "Patrimônio: %.2f %s (em dinheiro: %.2f)\n", L.Equity, q, L.QuoteBal)
	}
	sb.WriteString("\n")
	for _, s := range append(c.pairs(), extraPositions(b)...) {
		pl := L.Pairs[s]
		if pl == nil || pl.Price == 0 {
			fmt.Fprintf(&sb, "%s: carregando\n", s)
			continue
		}
		fmt.Fprintf(&sb, "%s %s (%+.1f%% em 24h)", s, fmtPrice(pl.Price), pl.ChangePct)
		if p := b.st.Positions[s]; p != nil {
			now := p.Qty*pl.Price*(1-paperFeePct/100) - p.Cost
			fmt.Fprintf(&sb, "\n   comprado a %s → %+.2f %s (%+.1f%%)", fmtPrice(p.Entry), now, q, now/p.Cost*100)
			switch {
			case p.Stop > 0 && p.StopOrderID != 0:
				fmt.Fprintf(&sb, "\n   stop %s (na Binance ✔)", fmtPrice(p.Stop))
			case p.Stop > 0:
				fmt.Fprintf(&sb, "\n   stop %s (vigiado pelo robô)", fmtPrice(p.Stop))
			}
		} else {
			sb.WriteString(" — fora")
		}
		if pl.LastError != "" {
			fmt.Fprintf(&sb, "\n   ⚠️ %s", pl.LastError)
		}
		sb.WriteString("\n")
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
