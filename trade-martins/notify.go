package main

// Avisos no celular pelo Telegram, e "sinal de vida" opcional para um serviço de monitoramento
// (ex.: healthchecks.io), que avisa quando o robô para de mandar sinal (PC desligou, internet caiu).

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"
)

var telegramAPI = "https://api.telegram.org"

type Notifier struct {
	ch     chan string
	creds  func() (token, chat string)
	client *http.Client
}

func NewNotifier(creds func() (string, string)) *Notifier {
	n := &Notifier{ch: make(chan string, 50), creds: creds, client: &http.Client{Timeout: 15 * time.Second}}
	go n.loop()
	return n
}

// Send entra na fila e nunca trava o robô.
func (n *Notifier) Send(msg string) {
	if tok, chat := n.creds(); tok == "" || chat == "" {
		return
	}
	select {
	case n.ch <- msg:
	default: // fila cheia: descarta
	}
}

func (n *Notifier) loop() {
	for msg := range n.ch {
		tok, chat := n.creds()
		if tok == "" || chat == "" {
			continue
		}
		for try := 0; try < 3; try++ {
			if err := telegramSend(n.client, tok, chat, msg); err == nil {
				break
			} else {
				log.Printf("[telegram] %v", err)
				time.Sleep(time.Duration(5*(try+1)) * time.Second)
			}
		}
	}
}

type tgResp struct {
	OK          bool            `json:"ok"`
	Description string          `json:"description"`
	Result      json.RawMessage `json:"result"`
}

func telegramCall(c *http.Client, token, method string, q url.Values) (json.RawMessage, error) {
	token = strings.TrimSpace(token)
	if token == "" || strings.ContainsAny(token, "/?#") {
		return nil, fmt.Errorf("token do Telegram inválido")
	}
	resp, err := c.PostForm(telegramAPI+"/bot"+token+"/"+method, q)
	if err != nil {
		return nil, fmt.Errorf("sem conexão com o Telegram")
	}
	defer resp.Body.Close()
	var r tgResp
	if err := json.NewDecoder(resp.Body).Decode(&r); err != nil {
		return nil, fmt.Errorf("resposta estranha do Telegram (HTTP %d)", resp.StatusCode)
	}
	if !r.OK {
		switch resp.StatusCode {
		case 401, 404:
			return nil, fmt.Errorf("token do Telegram recusado: confira o token que o @BotFather deu")
		case 400, 403:
			return nil, fmt.Errorf("o Telegram recusou a mensagem: mande /start para o seu bot e confira o chat (%s)", r.Description)
		}
		return nil, fmt.Errorf("Telegram: %s", r.Description)
	}
	return r.Result, nil
}

func telegramSend(c *http.Client, token, chat, text string) error {
	_, err := telegramCall(c, token, "sendMessage", url.Values{"chat_id": {chat}, "text": {text}, "disable_web_page_preview": {"true"}})
	return err
}

// telegramFindChat procura a última conversa que mandou mensagem para o bot.
func telegramFindChat(c *http.Client, token string) (id, name string, err error) {
	raw, err := telegramCall(c, token, "getUpdates", url.Values{"limit": {"50"}})
	if err != nil {
		return "", "", err
	}
	var ups []struct {
		Message *struct {
			Chat struct {
				ID        int64  `json:"id"`
				FirstName string `json:"first_name"`
				Title     string `json:"title"`
				Username  string `json:"username"`
			} `json:"chat"`
		} `json:"message"`
	}
	if err := json.Unmarshal(raw, &ups); err != nil {
		return "", "", err
	}
	for i := len(ups) - 1; i >= 0; i-- {
		if m := ups[i].Message; m != nil {
			n := m.Chat.FirstName
			if n == "" {
				n = m.Chat.Title
			}
			if m.Chat.Username != "" {
				n += " (@" + m.Chat.Username + ")"
			}
			return fmt.Sprint(m.Chat.ID), n, nil
		}
	}
	return "", "", fmt.Errorf("nenhuma mensagem encontrada: abra o seu bot no Telegram, mande /start e tente de novo")
}

// ping avisa o serviço de monitoramento que o robô está vivo.
func ping(c *http.Client, u string) {
	if u == "" {
		return
	}
	resp, err := c.Get(u)
	if err != nil {
		log.Printf("[ping] %v", err)
		return
	}
	resp.Body.Close()
}

func validPingURL(u string) bool {
	p, err := url.Parse(u)
	return err == nil && p.Scheme == "https" && p.Host != ""
}
