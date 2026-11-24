package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"hash/crc32"
	"html/template"
	"image/jpeg"
	"io/fs"
	"log"
	"net"
	"net/http"
	"os"
	"runtime"
	"strings"
	"sync"
	"time"

	"github.com/coder/websocket"

	controlemartins "controlemartins"
)

const version = "1.1"

func hostname() string {
	h, err := os.Hostname()
	if err != nil || h == "" {
		return "Computador"
	}
	return h
}

// ---------- Limite de tentativas de pareamento ----------

type limiter struct {
	mu    sync.Mutex
	fails map[string][]time.Time
	all   []time.Time
}

var pairLimit = &limiter{fails: map[string][]time.Time{}}

const limitWindow = 10 * time.Minute

func recent(ts []time.Time, now time.Time) []time.Time {
	out := ts[:0]
	for _, t := range ts {
		if now.Sub(t) < limitWindow {
			out = append(out, t)
		}
	}
	return out
}

func (l *limiter) blocked(ip string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	l.fails[ip] = recent(l.fails[ip], now)
	l.all = recent(l.all, now)
	return len(l.fails[ip]) >= 5 || len(l.all) >= 20
}

func (l *limiter) fail(ip string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	l.fails[ip] = append(l.fails[ip], now)
	l.all = append(l.all, now)
}

// ---------- HTTP ----------

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

// O app do celular roda em outra origem: libera chamadas de qualquer origem.
// A segurança vem da chave de pareamento, não de cookies.
func cors(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		h(w, r)
	}
}

func remoteIP(r *http.Request) string {
	h, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return h
}

func handleInfo(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, map[string]any{"app": "controle-martins", "name": hostname(), "version": version})
}

func handlePair(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeJSON(w, 405, map[string]string{"erro": "use POST"})
		return
	}
	ip := remoteIP(r)
	if pairLimit.blocked(ip) {
		writeJSON(w, 429, map[string]string{"erro": "Muitas tentativas erradas. Espere 10 minutos e tente de novo."})
		return
	}
	var req struct {
		Code   string `json:"code"`
		Device string `json:"device"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&req); err != nil {
		writeJSON(w, 400, map[string]string{"erro": "pedido inválido"})
		return
	}
	if normCode(req.Code) == "" || normCode(req.Code) != normCode(currentCode()) {
		pairLimit.fail(ip)
		writeJSON(w, 403, map[string]string{"erro": "Código errado. Confira no ícone do Controle Martins no computador."})
		return
	}
	name := strings.TrimSpace(req.Device)
	if len([]rune(name)) > 40 {
		name = string([]rune(name)[:40])
	}
	if name == "" {
		name = "Celular"
	}
	tok := addDevice(name)
	log.Printf("novo celular pareado: %q (%s)", name, ip)
	go notify("Controle Martins", fmt.Sprintf("Novo celular pareado: %s\n(%s)\n\nSe não foi você, use \"Desconectar todos os celulares\" no ícone do Controle Martins.", name, ip))
	updateTrayStatus()
	writeJSON(w, 200, map[string]string{"token": tok, "name": hostname()})
}

func runServer(port int) error {
	sub, err := fs.Sub(controlemartins.Web, "web")
	if err != nil {
		return err
	}
	files := http.FileServer(http.FS(sub))
	mux := http.NewServeMux()
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache")
		files.ServeHTTP(w, r)
	}))
	mux.HandleFunc("/api/info", cors(handleInfo))
	mux.HandleFunc("/api/parear", cors(handlePair))
	mux.HandleFunc("/ws", handleWS)
	mux.HandleFunc("/painel", handlePanel)
	srv := &http.Server{Addr: fmt.Sprintf(":%d", port), Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	return srv.ListenAndServe()
}

// ---------- Sessão (WebSocket) ----------

type msg struct {
	T    string   `json:"t"`
	Tok  string   `json:"token,omitempty"`
	On   bool     `json:"on,omitempty"`
	Mon  int      `json:"mon,omitempty"`
	W    int      `json:"w,omitempty"`
	Q    int      `json:"q,omitempty"`
	FPS  int      `json:"fps,omitempty"`
	X    float64  `json:"x,omitempty"`
	Y    float64  `json:"y,omitempty"`
	DX   float64  `json:"dx,omitempty"`
	DY   float64  `json:"dy,omitempty"`
	B    string   `json:"b,omitempty"`
	A    string   `json:"a,omitempty"`
	S    string   `json:"s,omitempty"`
	K    string   `json:"k,omitempty"`
	Mods []string `json:"mods,omitempty"`
	ID   int64    `json:"id,omitempty"`
}

type streamCfg struct {
	on   bool
	mon  int
	w, q int
	fps  int
	gen  int // muda a cada ajuste: força mandar uma imagem nova
}

type session struct {
	c      *websocket.Conn
	mu     sync.Mutex
	st     streamCfg
	wake   chan struct{}
	ack    chan struct{}
	scroll struct{ x, y float64 } // sobra de rolagem fracionada
}

func (s *session) send(ctx context.Context, v any) {
	b, _ := json.Marshal(v)
	wctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	_ = s.c.Write(wctx, websocket.MessageText, b)
}

func poke(ch chan struct{}) {
	select {
	case ch <- struct{}{}:
	default:
	}
}

func monitorsJSON() []map[string]any {
	var out []map[string]any
	for i, m := range monitors() {
		out = append(out, map[string]any{"i": i, "w": m.W, "h": m.H, "primary": m.Primary})
	}
	return out
}

func handleWS(w http.ResponseWriter, r *http.Request) {
	c, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		InsecureSkipVerify: true, // qualquer origem; quem manda é a chave
		CompressionMode:    websocket.CompressionDisabled,
	})
	if err != nil {
		return
	}
	defer c.CloseNow()
	c.SetReadLimit(1 << 20)
	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()

	// Primeira mensagem: a chave do celular
	actx, acancel := context.WithTimeout(ctx, 10*time.Second)
	_, data, err := c.Read(actx)
	acancel()
	if err != nil {
		return
	}
	var first msg
	_ = json.Unmarshal(data, &first)
	device, ok := checkToken(first.Tok)
	if first.T != "auth" || !ok {
		c.Close(4001, "nao autorizado")
		return
	}
	log.Printf("conectou: %s (%s)", device, remoteIP(r))

	s := &session{c: c, wake: make(chan struct{}, 1), ack: make(chan struct{}, 1)}
	s.st = streamCfg{w: 1280, q: 60, fps: 15}
	s.send(ctx, map[string]any{"t": "info", "name": hostname(), "version": version, "monitors": monitorsJSON()})
	go s.stream(ctx)

	for {
		_, data, err := c.Read(ctx)
		if err != nil {
			log.Printf("desconectou: %s", device)
			return
		}
		var m msg
		if json.Unmarshal(data, &m) != nil {
			continue
		}
		if m.T == "sair" { // o celular pediu para esquecer este PC
			removeDevice(first.Tok)
			updateTrayStatus()
			c.Close(websocket.StatusNormalClosure, "")
			return
		}
		s.handle(ctx, m)
	}
}

func clamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

func (s *session) monitor() (Monitor, bool) {
	ms := monitors()
	if len(ms) == 0 {
		return Monitor{}, false
	}
	s.mu.Lock()
	i := s.st.mon
	s.mu.Unlock()
	if i < 0 || i >= len(ms) {
		i = 0
	}
	return ms[i], true
}

func (s *session) handle(ctx context.Context, m msg) {
	switch m.T {
	case "ping":
		s.send(ctx, map[string]any{"t": "pong", "id": m.ID})
	case "tela":
		s.mu.Lock()
		s.st.on = m.On
		if m.W > 0 {
			s.st.w = clamp(m.W, 320, 3840)
		}
		if m.Q > 0 {
			s.st.q = clamp(m.Q, 20, 95)
		}
		if m.FPS > 0 {
			s.st.fps = clamp(m.FPS, 1, 30)
		}
		s.st.mon = m.Mon
		s.st.gen++
		s.mu.Unlock()
		poke(s.wake)
	case "ack":
		poke(s.ack)
	case "mv":
		mouseMoveRel(int(m.DX), int(m.DY))
	case "pos":
		if mon, ok := s.monitor(); ok {
			x := mon.X + int(m.X*float64(mon.W))
			y := mon.Y + int(m.Y*float64(mon.H))
			mouseMoveAbs(clamp(x, mon.X, mon.X+mon.W-1), clamp(y, mon.Y, mon.Y+mon.H-1))
		}
	case "btn":
		switch m.A {
		case "down":
			mouseButton(m.B, true)
		case "up":
			mouseButton(m.B, false)
		case "dbl":
			mouseButton(m.B, true)
			mouseButton(m.B, false)
			mouseButton(m.B, true)
			mouseButton(m.B, false)
		default:
			mouseButton(m.B, true)
			mouseButton(m.B, false)
		}
	case "scroll":
		// dy/dx em "cliques de rodinha" (podem ser frações)
		s.scroll.y += m.DY
		s.scroll.x += m.DX
		wy := int(s.scroll.y * 120)
		wx := int(s.scroll.x * 120)
		if wy != 0 || wx != 0 {
			s.scroll.y -= float64(wy) / 120
			s.scroll.x -= float64(wx) / 120
			mouseWheel(wy, wx)
		}
	case "txt":
		if len(m.S) <= 4000 {
			typeText(m.S)
		}
	case "key":
		if err := keyPress(m.K, m.Mods); err != nil {
			s.send(ctx, map[string]any{"t": "erro", "msg": err.Error()})
		}
	case "acao":
		if err := action(m.A); err != nil {
			s.send(ctx, map[string]any{"t": "erro", "msg": err.Error()})
		}
	case "abrir":
		if err := openCmd(m.S); err != nil {
			s.send(ctx, map[string]any{"t": "erro", "msg": "Não abriu: " + err.Error()})
		}
	}
}

// Manda a tela como imagens JPEG. Só manda a próxima quando o celular avisa
// que desenhou a anterior (ack): assim nunca acumula atraso em rede lenta.
func (s *session) stream(ctx context.Context) {
	runtime.LockOSThread()
	defer runtime.UnlockOSThread()
	cap := newCapturer()
	defer cap.Close()

	var buf bytes.Buffer
	var lastCRC uint32
	lastGen := -1
	for {
		s.mu.Lock()
		st := s.st
		s.mu.Unlock()
		if !st.on {
			select {
			case <-ctx.Done():
				return
			case <-s.wake:
			}
			continue
		}
		start := time.Now()
		mon, ok := s.monitor()
		if !ok {
			if !sleepCtx(ctx, s.wake, time.Second) {
				return
			}
			continue
		}
		w := st.w
		if w > mon.W {
			w = mon.W
		}
		img, err := cap.Grab(mon, w)
		if err != nil {
			// tela bloqueada / troca de área de trabalho: tenta de novo
			if !sleepCtx(ctx, s.wake, 500*time.Millisecond) {
				return
			}
			continue
		}
		crc := crc32.ChecksumIEEE(img.Pix)
		if crc == lastCRC && st.gen == lastGen {
			if !sleepCtx(ctx, s.wake, 70*time.Millisecond) {
				return
			}
			continue
		}
		buf.Reset()
		if err := jpeg.Encode(&buf, img, &jpeg.Options{Quality: st.q}); err != nil {
			continue
		}
		// esvazia ack antigo antes de mandar
		select {
		case <-s.ack:
		default:
		}
		wctx, cancel := context.WithTimeout(ctx, 10*time.Second)
		err = s.c.Write(wctx, websocket.MessageBinary, buf.Bytes())
		cancel()
		if err != nil {
			if errors.Is(ctx.Err(), context.Canceled) {
				return
			}
			continue
		}
		lastCRC, lastGen = crc, st.gen
		select {
		case <-ctx.Done():
			return
		case <-s.ack:
		case <-s.wake:
		case <-time.After(3 * time.Second):
		}
		minGap := time.Second / time.Duration(st.fps)
		if el := time.Since(start); el < minGap {
			if !sleepCtx(ctx, s.wake, minGap-el) {
				return
			}
		}
	}
}

// Espera d; acorda antes se mudar a configuração. Devolve false se a conexão acabou.
func sleepCtx(ctx context.Context, wake chan struct{}, d time.Duration) bool {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-wake: // a configuração é lida de novo no laço principal
		return true
	case <-t.C:
		return true
	}
}

// ---------- Painel local (só abre no próprio computador) ----------

var panelTpl = template.Must(template.New("p").Parse(`<!doctype html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Controle Martins</title>
<style>body{font:17px/1.5 system-ui,Segoe UI,sans-serif;background:#0b0d12;color:#eef1f6;max-width:640px;margin:0 auto;padding:32px 20px}
h1{font-size:26px;margin:0 0 4px}.ok{color:#3ecf8e;font-weight:600}.box{background:#161a22;border-radius:16px;padding:18px 20px;margin:18px 0}
.code{font:700 40px/1.2 Consolas,monospace;letter-spacing:4px;color:#7fb0ff}.addr{font:600 22px Consolas,monospace}
.muted{color:#8b93a3;font-size:14px}li{margin:6px 0}</style></head><body>
<h1>Controle Martins</h1><div class="ok">● Ativo neste computador ({{.Name}})</div>
<div class="box"><div class="muted">CÓDIGO DE PAREAMENTO</div><div class="code">{{.Code}}</div></div>
<div class="box"><div class="muted">ENDEREÇO PARA COLOCAR NO CELULAR</div>
{{if .TS}}{{range .TS}}<div class="addr">{{.}}</div>{{end}}<div class="muted">Tailscale: funciona de qualquer lugar.</div>{{else}}
<p><b>O Tailscale não está ligado neste PC.</b> Sem ele, só funciona no Wi-Fi de casa.<br>
Instale em <b>tailscale.com/download</b>, entre com a mesma conta do celular e atualize esta página: o endereço que começa com <b>100.</b> vai aparecer aqui.</p>{{end}}
{{if .LAN}}<p class="muted">Só na rede de casa: {{range $i, $a := .LAN}}{{if $i}}, {{end}}{{$a}}{{end}}</p>{{end}}</div>
<div class="box"><b>No celular:</b> app Controle PC › Adicionar computador › digite o endereço e o código.<br>
<span class="muted">Celulares pareados: {{.N}} · Porta {{.Port}} · Versão {{.Ver}}</span></div>
<p class="muted">Esta página só abre no próprio computador. Para abrir de novo: endereço <b>localhost:{{.Port}}/painel</b> no navegador, ou abra o ControleMartins.exe outra vez.</p>
</body></html>`))

func handlePanel(w http.ResponseWriter, r *http.Request) {
	ip := net.ParseIP(remoteIP(r))
	if ip == nil || !ip.IsLoopback() {
		http.NotFound(w, r)
		return
	}
	ts, lan := splitAddresses()
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	_ = panelTpl.Execute(w, map[string]any{
		"Name": hostname(), "Code": currentCode(), "TS": ts, "LAN": lan,
		"N": deviceCount(), "Port": cfg.Port, "Ver": version,
	})
}
