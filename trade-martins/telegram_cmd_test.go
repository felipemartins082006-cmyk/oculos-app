package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

const myChat = "42"

func upd(chat int64, text string, age time.Duration) tgUpdate {
	var u tgUpdate
	u.Message = &struct {
		Date int64  `json:"date"`
		Text string `json:"text"`
		Chat struct {
			ID int64 `json:"id"`
		} `json:"chat"`
	}{Date: time.Now().Add(-age).Unix(), Text: text}
	u.Message.Chat.ID = chat
	return u
}

func paperBot(t *testing.T) *Bot {
	b := NewBot(t.TempDir())
	b.infos["BTCUSDT"] = &SymbolInfo{Symbol: "BTCUSDT", Base: "BTC", Quote: "USDT"}
	b.live.Pairs["BTCUSDT"] = &PairLive{Symbol: "BTCUSDT", Base: "BTC", Price: 60000}
	b.live.Quote = "USDT"
	return b
}

func TestTelegramIgnoresOtherChatsAndOldMessages(t *testing.T) {
	b := paperBot(t)
	if r := b.handleTelegramUpdate(upd(999, "/ligar", 0), myChat); r != "" {
		t.Fatalf("não deveria responder a outro chat: %q", r)
	}
	if r := b.handleTelegramUpdate(upd(42, "/ligar", 10*time.Minute), myChat); !strings.Contains(r, "Ignorei") {
		t.Fatalf("deveria ignorar mensagem antiga: %q", r)
	}
	if b.st.Running {
		t.Fatalf("nenhum desses comandos podia ligar o robô")
	}
}

func TestTelegramStartOnlyShowsHelp(t *testing.T) {
	b := paperBot(t)
	r := b.handleTelegramUpdate(upd(42, "/start", 0), myChat)
	if !strings.Contains(r, "/status") || b.st.Running {
		t.Fatalf("/start deveria só mostrar a ajuda (e NÃO ligar o robô): %q running=%v", r, b.st.Running)
	}
}

func TestTelegramCommandsFlow(t *testing.T) {
	b := paperBot(t)
	if r := b.handleTelegramUpdate(upd(42, "/status@MeuRoboBot", 0), myChat); !strings.Contains(r, "DESLIGADO") || !strings.Contains(r, "— fora") {
		t.Fatalf("status errado: %q", r)
	}
	b.handleTelegramUpdate(upd(42, "/ligar", 0), myChat)
	if !b.st.Running {
		t.Fatalf("/ligar deveria ligar no simulado")
	}
	if r := b.handleTelegramUpdate(upd(42, "/vender", 0), myChat); !strings.Contains(r, "Não há posição") {
		t.Fatalf("sem posição: %q", r)
	}

	b.mu.Lock()
	b.buyLocked("BTCUSDT", 60000)
	b.mu.Unlock()
	r := b.handleTelegramUpdate(upd(42, "/vender", 0), myChat)
	if !strings.Contains(r, "/vender BTCUSDT sim") || b.st.Positions["BTCUSDT"] == nil {
		t.Fatalf("/vender sozinho deveria só pedir confirmação: %q", r)
	}
	if r := b.handleTelegramUpdate(upd(42, "/status", 0), myChat); !strings.Contains(r, "comprado a 60000.00") || !strings.Contains(r, "stop 56400.00") {
		t.Fatalf("status com posição errado: %q", r)
	}
	b.handleTelegramUpdate(upd(42, "/vender sim", 0), myChat)
	if b.st.Positions["BTCUSDT"] != nil {
		t.Fatalf("/vender sim deveria vender")
	}
	b.handleTelegramUpdate(upd(42, "/parar", 0), myChat)
	if b.st.Running {
		t.Fatalf("/parar deveria desligar")
	}
	if r := b.handleTelegramUpdate(upd(42, "oi", 0), myChat); !strings.Contains(r, "Não entendi") {
		t.Fatalf("texto qualquer: %q", r)
	}
}

func TestTelegramRealModeNeedsConfirmation(t *testing.T) {
	f := newFake(t)
	b := realBot(t, f)
	r := b.handleTelegramUpdate(upd(42, "/ligar", 0), myChat)
	if b.st.Running || !strings.Contains(r, "/ligar real") {
		t.Fatalf("no modo real, /ligar sozinho não pode ligar: %q", r)
	}
	b.handleTelegramUpdate(upd(42, "/ligar real", 0), myChat)
	if !b.st.Running {
		t.Fatalf("/ligar real deveria ligar")
	}
}

// O laço de verdade, contra um Telegram falso.
func TestTelegramLoop(t *testing.T) {
	var mu sync.Mutex
	var sent []string
	served := false
	menu := false
	tg := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		mu.Lock()
		defer mu.Unlock()
		switch {
		case strings.HasSuffix(r.URL.Path, "/setMyCommands"):
			menu = true
			fmt.Fprint(w, `{"ok":true,"result":true}`)
		case strings.HasSuffix(r.URL.Path, "/getUpdates"):
			if served {
				fmt.Fprint(w, `{"ok":true,"result":[]}`)
				return
			}
			served = true
			now := time.Now().Unix()
			ups := []map[string]any{
				{"update_id": 1, "message": map[string]any{"date": now, "text": "/parar", "chat": map[string]any{"id": 777}}},
				{"update_id": 2, "message": map[string]any{"date": now - 3600, "text": "/vender sim", "chat": map[string]any{"id": 42}}},
				{"update_id": 3, "message": map[string]any{"date": now, "text": "/status", "chat": map[string]any{"id": 42}}},
			}
			json.NewEncoder(w).Encode(map[string]any{"ok": true, "result": ups})
		case strings.HasSuffix(r.URL.Path, "/sendMessage"):
			if r.FormValue("chat_id") != "42" {
				t.Errorf("respondeu para outro chat: %s", r.FormValue("chat_id"))
			}
			sent = append(sent, r.FormValue("text"))
			fmt.Fprint(w, `{"ok":true,"result":{}}`)
		}
	}))
	defer tg.Close()
	old := telegramAPI
	telegramAPI = tg.URL
	defer func() { telegramAPI = old }()

	b := paperBot(t)
	b.st.Running = true
	b.tgMu.Lock()
	b.tgToken, b.tgChat = "TOKEN", "42"
	b.tgMu.Unlock()
	stop, done := make(chan struct{}), make(chan struct{})
	go func() { b.TelegramCommands(stop); close(done) }()
	defer func() { close(stop); <-done }() // para o laço antes de restaurar o endereço do Telegram

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		mu.Lock()
		n := len(sent)
		mu.Unlock()
		if n >= 3 {
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	mu.Lock()
	defer mu.Unlock()
	if !menu {
		t.Fatalf("deveria registrar o menu de comandos")
	}
	all := strings.Join(sent, "\n---\n")
	if !strings.Contains(all, "Ignorei") || !strings.Contains(all, "📊") || !strings.Contains(all, "não é o seu (777)") {
		t.Fatalf("respostas erradas: %q", sent)
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	if !b.st.Running {
		t.Fatalf("o /parar de outro chat não podia desligar o robô")
	}
}
