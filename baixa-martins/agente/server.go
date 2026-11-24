package main

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"log"
	"mime"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	baixamartins "baixamartins"
)

const version = "1.0"

func hostname() string {
	h, err := os.Hostname()
	if err != nil || h == "" {
		return "Computador"
	}
	return h
}

// ---------- Limite de tentativas do código ----------

type limiter struct {
	mu    sync.Mutex
	fails map[string][]time.Time
	all   []time.Time
}

var pairLimit = &limiter{fails: map[string][]time.Time{}}

func recent(ts []time.Time, now time.Time) []time.Time {
	out := ts[:0]
	for _, t := range ts {
		if now.Sub(t) < 10*time.Minute {
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

// ---------- Eventos ao vivo (Server-Sent Events) ----------

var (
	hubMu      sync.Mutex
	hub        = map[chan []byte]bool{}
	jobsDirty  bool
	toolsDirty bool
)

func broadcast(v any) {
	b, _ := json.Marshal(v)
	hubMu.Lock()
	defer hubMu.Unlock()
	for c := range hub {
		select {
		case c <- b:
		default: // cliente lento: pula
		}
	}
}

func jobsChanged() {
	hubMu.Lock()
	jobsDirty = true
	hubMu.Unlock()
}

func broadcastTools() {
	hubMu.Lock()
	toolsDirty = true
	hubMu.Unlock()
}

func eventPump() {
	for range time.Tick(250 * time.Millisecond) {
		hubMu.Lock()
		j, t := jobsDirty, toolsDirty
		jobsDirty, toolsDirty = false, false
		hubMu.Unlock()
		if j {
			broadcast(map[string]any{"type": "jobs", "jobs": jobSnapshot()})
		}
		if t {
			broadcast(map[string]any{"type": "tools", "tools": toolSnapshot()})
		}
	}
}

func handleEvents(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "sem streaming", 500)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("X-Accel-Buffering", "no")
	c := make(chan []byte, 64)
	hubMu.Lock()
	hub[c] = true
	hubMu.Unlock()
	defer func() {
		hubMu.Lock()
		delete(hub, c)
		hubMu.Unlock()
	}()
	send := func(b []byte) bool {
		if _, err := fmt.Fprintf(w, "data: %s\n\n", b); err != nil {
			return false
		}
		fl.Flush()
		return true
	}
	first, _ := json.Marshal(map[string]any{"type": "jobs", "jobs": jobSnapshot()})
	if !send(first) {
		return
	}
	ping := time.NewTicker(20 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case b := <-c:
			if !send(b) {
				return
			}
		case <-ping.C:
			if _, err := fmt.Fprint(w, ": ping\n\n"); err != nil {
				return
			}
			fl.Flush()
		}
	}
}

// ---------- HTTP ----------

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	_ = json.NewEncoder(w).Encode(v)
}

func fail(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]string{"erro": msg})
}

func remoteIP(r *http.Request) string {
	h, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return h
}

func isLocal(r *http.Request) bool {
	ip := net.ParseIP(remoteIP(r))
	return ip != nil && ip.IsLoopback()
}

func tokenOf(r *http.Request) string {
	if t := r.Header.Get("X-Token"); t != "" {
		return t
	}
	return r.URL.Query().Get("t")
}

func authed(r *http.Request) bool { return isLocal(r) || checkToken(tokenOf(r)) }

// O app do celular roda em outra origem (dentro do APK): libera chamadas de qualquer origem.
// A segurança vem da chave de acesso, não de cookies.
func cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, X-Token, Range")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		w.Header().Set("Access-Control-Expose-Headers", "Content-Length, Content-Range")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func need(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !authed(r) {
			fail(w, 401, "Este aparelho não tem acesso. Conecte de novo com o código.")
			return
		}
		h(w, r)
	}
}

func localOnly(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !isLocal(r) {
			fail(w, 403, "Isso só funciona no próprio computador.")
			return
		}
		h(w, r)
	}
}

func post(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			fail(w, 405, "use POST")
			return
		}
		h(w, r)
	}
}

func readJSON(r *http.Request, v any) error {
	return json.NewDecoder(io.LimitReader(r.Body, 4<<20)).Decode(v)
}

func handleInfo(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, map[string]any{"app": "baixa-martins", "name": hostname(), "version": version,
		"authed": authed(r), "local": isLocal(r)})
}

func handlePair(w http.ResponseWriter, r *http.Request) {
	ip := remoteIP(r)
	if pairLimit.blocked(ip) {
		fail(w, 429, "Muitas tentativas erradas. Espere 10 minutos e tente de novo.")
		return
	}
	var req struct{ Code, Name string }
	if readJSON(r, &req) != nil || normCode(req.Code) == "" {
		fail(w, 400, "Digite o código que aparece no computador.")
		return
	}
	if normCode(req.Code) != normCode(currentCode()) {
		pairLimit.fail(ip)
		fail(w, 403, "Código errado. Confira no computador.")
		return
	}
	name := strings.TrimSpace(req.Name)
	if name == "" {
		name = "Celular"
	}
	if len(name) > 40 {
		name = name[:40]
	}
	tok := addDevice(name)
	updateTrayStatus()
	log.Printf("aparelho conectado: %s (%s)", name, ip)
	writeJSON(w, 200, map[string]any{"token": tok, "name": hostname(), "version": version})
}

func handleUnpair(w http.ResponseWriter, r *http.Request) {
	removeDevice(tokenOf(r))
	updateTrayStatus()
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func handleState(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, map[string]any{"jobs": jobSnapshot(), "tools": toolSnapshot(), "settings": getSettings(),
		"local": isLocal(r), "version": version, "name": hostname(), "os": osName()})
}

func handlePairInfo(w http.ResponseWriter, r *http.Request) {
	ts, lan := splitAddresses()
	ts, lan = append([]string{}, ts...), append([]string{}, lan...)
	writeJSON(w, 200, map[string]any{"code": currentCode(), "port": cfg.Port, "tailscale": ts, "lan": lan,
		"devices": deviceCount()})
}

func handleNewCode(w http.ResponseWriter, r *http.Request) {
	regenerateCode()
	handlePairInfo(w, r)
}

func handleClearDevices(w http.ResponseWriter, r *http.Request) {
	clearDevices()
	updateTrayStatus()
	handlePairInfo(w, r)
}

func handleResolve(w http.ResponseWriter, r *http.Request) {
	var req struct{ Q string }
	if readJSON(r, &req) != nil {
		fail(w, 400, "pedido inválido")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 3*time.Minute)
	defer cancel()
	res, err := resolve(ctx, req.Q)
	if err != nil {
		fail(w, 422, err.Error())
		return
	}
	if res.Entries == nil {
		res.Entries = []Entry{}
	}
	writeJSON(w, 200, res)
}

func handleDownload(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Entries    []Entry `json:"entries"`
		Kind       string  `json:"kind"`
		Quality    string  `json:"quality"`
		Format     string  `json:"format"`
		Kbps       int     `json:"kbps"`
		Playlist   string  `json:"playlist"`
		Transcribe *bool   `json:"transcribe"`
		Lang       string  `json:"lang"`
		Translate  bool    `json:"translate"`
	}
	if readJSON(r, &req) != nil || len(req.Entries) == 0 {
		fail(w, 400, "nada para baixar")
		return
	}
	if req.Kind != "video" {
		req.Kind = "audio"
	}
	s := getSettings()
	tr := s.AutoLyrics
	if req.Kind == "video" {
		tr = s.AutoSubs
	}
	if req.Transcribe != nil {
		tr = *req.Transcribe
	}
	mode := "lyrics"
	if req.Kind == "video" {
		mode = "transcript"
	}
	n := 0
	for _, e := range req.Entries {
		if !videoIDRe.MatchString(e.ID) {
			continue
		}
		addJob(&Job{Type: "download", Title: e.Title, Channel: e.Channel, VideoID: e.ID, Kind: req.Kind,
			Playlist: req.Playlist, Quality: req.Quality, Format: req.Format, Kbps: req.Kbps, Duration: e.Duration,
			Transcribe: tr, Mode: mode, Lang: req.Lang, Translate: req.Translate})
		n++
	}
	writeJSON(w, 200, map[string]int{"added": n})
}

func handleJobAction(w http.ResponseWriter, r *http.Request) {
	var req struct{ ID, Action string }
	_ = readJSON(r, &req)
	switch req.Action {
	case "cancel":
		cancelJob(req.ID)
	case "clear":
		clearJobs(req.ID)
	case "retry":
		retryJob(req.ID)
	case "cancelAll":
		for _, j := range jobSnapshot() {
			if j.State == "fila" || j.State == "ativo" {
				cancelJob(j.ID)
			}
		}
	}
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func handleLibrary(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, 200, listLibrary())
}

func handleLibDelete(w http.ResponseWriter, r *http.Request) {
	var req struct {
		ID   string `json:"id"`
		File bool   `json:"file"`
	}
	_ = readJSON(r, &req)
	if !deleteItem(req.ID, req.File) {
		fail(w, 404, "não achei")
		return
	}
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func handleTranscribe(w http.ResponseWriter, r *http.Request) {
	var req struct {
		ID, Mode, Lang, Model string
		Translate             bool
	}
	_ = readJSON(r, &req)
	if req.Mode != "" && req.Mode != "lyrics" && req.Mode != "transcript" {
		req.Mode = ""
	}
	if _, ok := models[req.Model]; !ok {
		req.Model = ""
	}
	j := queueTranscribe(req.ID, req.Mode, req.Lang, req.Translate, req.Model)
	if j == nil {
		fail(w, 404, "não achei esse arquivo")
		return
	}
	writeJSON(w, 200, map[string]string{"job": j.ID})
}

func handleTranscript(w http.ResponseWriter, r *http.Request) {
	id := r.URL.Query().Get("id")
	t, err := loadTranscript(id)
	if err != nil {
		fail(w, 404, "ainda não tem transcrição")
		return
	}
	writeJSON(w, 200, t)
}

func handleTranscriptEdit(w http.ResponseWriter, r *http.Request) {
	var req struct {
		ID     string            `json:"id"`
		Edits  map[string]string `json:"edits"`
		Labels map[string]string `json:"labels"`
		Offset *float64          `json:"offset"`
	}
	if readJSON(r, &req) != nil {
		fail(w, 400, "pedido inválido")
		return
	}
	t, err := loadTranscript(req.ID)
	if err != nil {
		fail(w, 404, "não achei a transcrição")
		return
	}
	applyEdits(t, req.Edits, req.Labels)
	if req.Offset != nil {
		t.Offset = clamp(*req.Offset, -10, 10)
	}
	if err := saveTranscript(t); err != nil {
		fail(w, 500, "não consegui salvar")
		return
	}
	if it := getItem(req.ID); it != nil {
		writeSidecar(it, t)
	}
	writeJSON(w, 200, t)
}

func handleTranscriptDelete(w http.ResponseWriter, r *http.Request) {
	var req struct{ ID string }
	_ = readJSON(r, &req)
	_ = os.Remove(transcriptPath(req.ID))
	if it := getItem(req.ID); it != nil {
		base := strings.TrimSuffix(it.File, filepath.Ext(it.File))
		_ = os.Remove(base + ".lrc")
		_ = os.Remove(base + ".srt")
	}
	updateItem(req.ID, func(i *Item) { i.Transcript, i.Lang = "", "" })
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func attachName(w http.ResponseWriter, name string) {
	w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+url.PathEscape(name))
}

func fileBase(it *Item) string {
	return strings.TrimSuffix(filepath.Base(it.File), filepath.Ext(it.File))
}

func handleExport(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()
	id, f := q.Get("id"), q.Get("fmt")
	t, err := loadTranscript(id)
	if err != nil {
		fail(w, 404, "ainda não tem transcrição")
		return
	}
	it := getItem(id)
	var body, ctype string
	switch f {
	case "lrc":
		body, ctype = exportLRC(it, t), "text/plain"
	case "srt":
		body, ctype = exportSRT(t), "application/x-subrip"
	case "vtt":
		body, ctype = exportVTT(t), "text/vtt"
	default:
		f = "txt"
		body, ctype = exportTXT(it, t), "text/plain"
	}
	w.Header().Set("Content-Type", ctype+"; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	if q.Get("dl") == "1" {
		name := "transcricao"
		if it != nil {
			name = fileBase(it)
		}
		attachName(w, name+"."+f)
	}
	_, _ = io.WriteString(w, body)
}

var mediaTypes = map[string]string{"mp3": "audio/mpeg", "m4a": "audio/mp4", "opus": "audio/ogg", "ogg": "audio/ogg",
	"webm": "video/webm", "mp4": "video/mp4", "mkv": "video/x-matroska", "flac": "audio/flac", "wav": "audio/wav"}

func handleMedia(w http.ResponseWriter, r *http.Request) {
	it := getItem(r.URL.Query().Get("id"))
	if it == nil {
		http.NotFound(w, r)
		return
	}
	f, err := os.Open(it.File)
	if err != nil {
		http.Error(w, "arquivo não encontrado no computador", 404)
		return
	}
	defer f.Close()
	st, _ := f.Stat()
	ext := strings.ToLower(strings.TrimPrefix(filepath.Ext(it.File), "."))
	if ct := mediaTypes[ext]; ct != "" {
		w.Header().Set("Content-Type", ct)
	} else if ct := mime.TypeByExtension("." + ext); ct != "" {
		w.Header().Set("Content-Type", ct)
	}
	if r.URL.Query().Get("dl") == "1" {
		attachName(w, filepath.Base(it.File))
	}
	http.ServeContent(w, r, "", st.ModTime(), f)
}

// ---------- Capas (guardadas no PC para funcionar sem internet) ----------

func thumbDir() string { return filepath.Join(configDir(), "capas") }

var thumbMu sync.Mutex

func cacheThumb(vid string) (string, error) {
	if !videoIDRe.MatchString(vid) {
		return "", fmt.Errorf("id inválido")
	}
	p := filepath.Join(thumbDir(), vid+".jpg")
	if _, err := os.Stat(p); err == nil {
		return p, nil
	}
	thumbMu.Lock()
	defer thumbMu.Unlock()
	if _, err := os.Stat(p); err == nil {
		return p, nil
	}
	_ = os.MkdirAll(thumbDir(), 0o755)
	cl := &http.Client{Timeout: 15 * time.Second}
	for _, name := range []string{"maxresdefault", "hq720", "sddefault", "hqdefault", "mqdefault"} {
		resp, err := cl.Get("https://i.ytimg.com/vi/" + vid + "/" + name + ".jpg")
		if err != nil {
			return "", err
		}
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
		resp.Body.Close()
		if resp.StatusCode == 200 && len(b) > 2000 {
			if err := os.WriteFile(p, b, 0o644); err != nil {
				return "", err
			}
			return p, nil
		}
	}
	return "", fmt.Errorf("sem capa")
}

func handleThumb(w http.ResponseWriter, r *http.Request) {
	vid := r.URL.Query().Get("v")
	if !videoIDRe.MatchString(vid) {
		http.NotFound(w, r)
		return
	}
	// resultados de busca: manda direto para o YouTube (rápido); baixados: capa guardada
	p := filepath.Join(thumbDir(), vid+".jpg")
	if _, err := os.Stat(p); err != nil {
		if r.URL.Query().Get("keep") != "1" {
			http.Redirect(w, r, "https://i.ytimg.com/vi/"+vid+"/mqdefault.jpg", http.StatusFound)
			return
		}
		if p, err = cacheThumb(vid); err != nil {
			http.NotFound(w, r)
			return
		}
	}
	w.Header().Set("Cache-Control", "public, max-age=604800")
	http.ServeFile(w, r, p)
}

func handleSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodPost {
		var s Settings
		if readJSON(r, &s) != nil {
			fail(w, 400, "pedido inválido")
			return
		}
		if !isLocal(r) { // pastas do PC só mudam no próprio PC
			cur := getSettings()
			s.AudioDir, s.VideoDir = cur.AudioDir, cur.VideoDir
		}
		setSettings(s)
		schedule()
	}
	writeJSON(w, 200, getSettings())
}

func handleOpen(w http.ResponseWriter, r *http.Request) {
	var req struct{ ID, Folder string }
	_ = readJSON(r, &req)
	var err error
	switch {
	case req.ID != "":
		it := getItem(req.ID)
		if it == nil {
			fail(w, 404, "não achei")
			return
		}
		err = revealFile(it.File)
	case req.Folder == "audio":
		_ = os.MkdirAll(getSettings().AudioDir, 0o755)
		err = openPath(getSettings().AudioDir)
	case req.Folder == "video":
		_ = os.MkdirAll(getSettings().VideoDir, 0o755)
		err = openPath(getSettings().VideoDir)
	default:
		err = openPath(configDir())
	}
	if err != nil {
		fail(w, 500, err.Error())
		return
	}
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func handleUpdateTools(w http.ResponseWriter, r *http.Request) {
	go func() {
		setTool("ytdlp", func(s *ToolState) { s.Busy = true })
		out, err := updateYtdlp(context.Background())
		setTool("ytdlp", func(s *ToolState) {
			s.Busy = false
			if err != nil {
				s.Error = "não atualizou: " + err.Error()
			} else {
				s.Error = ""
			}
		})
		log.Printf("atualizar yt-dlp: %v %s", err, out)
	}()
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func handlePrepare(w http.ResponseWriter, r *http.Request) {
	var req struct{ What string }
	_ = readJSON(r, &req)
	go func() {
		ctx := context.Background()
		if strings.HasPrefix(req.What, "modelo-") {
			_, _ = ensureModel(ctx, strings.TrimPrefix(req.What, "modelo-"))
			return
		}
		_ = ensureTools(ctx, req.What)
	}()
	writeJSON(w, 200, map[string]bool{"ok": true})
}

func handleLog(w http.ResponseWriter, r *http.Request) {
	b, _ := os.ReadFile(filepath.Join(configDir(), "agente.log"))
	if len(b) > 64<<10 {
		b = b[len(b)-64<<10:]
	}
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	_, _ = w.Write(b)
}

func handleIcon(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	_, _ = w.Write(iconPNG(192))
}

func runServer(port int) error {
	go eventPump()
	webFS, _ := fs.Sub(baixamartins.Web, "web")
	static := http.FileServer(http.FS(webFS))
	mux := http.NewServeMux()
	mux.HandleFunc("/api/info", handleInfo)
	mux.HandleFunc("/api/pair", post(handlePair))
	mux.HandleFunc("/api/unpair", post(need(handleUnpair)))
	mux.HandleFunc("/api/events", need(handleEvents))
	mux.HandleFunc("/api/state", need(handleState))
	mux.HandleFunc("/api/pairinfo", localOnly(handlePairInfo))
	mux.HandleFunc("/api/newcode", post(localOnly(handleNewCode)))
	mux.HandleFunc("/api/cleardevices", post(localOnly(handleClearDevices)))
	mux.HandleFunc("/api/resolve", post(need(handleResolve)))
	mux.HandleFunc("/api/download", post(need(handleDownload)))
	mux.HandleFunc("/api/job", post(need(handleJobAction)))
	mux.HandleFunc("/api/library", need(handleLibrary))
	mux.HandleFunc("/api/library/delete", post(need(handleLibDelete)))
	mux.HandleFunc("/api/transcribe", post(need(handleTranscribe)))
	mux.HandleFunc("/api/transcript", need(handleTranscript))
	mux.HandleFunc("/api/transcript/edit", post(need(handleTranscriptEdit)))
	mux.HandleFunc("/api/transcript/delete", post(need(handleTranscriptDelete)))
	mux.HandleFunc("/api/export", need(handleExport))
	mux.HandleFunc("/api/settings", need(handleSettings))
	mux.HandleFunc("/api/open", post(localOnly(handleOpen)))
	mux.HandleFunc("/api/tools/update", post(need(handleUpdateTools)))
	mux.HandleFunc("/api/tools/prepare", post(need(handlePrepare)))
	mux.HandleFunc("/api/log", localOnly(handleLog))
	mux.HandleFunc("/media", need(handleMedia))
	mux.HandleFunc("/api/thumb", handleThumb) // capas não são segredo
	mux.HandleFunc("/icon.png", handleIcon)
	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache")
		static.ServeHTTP(w, r)
	}))
	// no celular só o próprio aparelho acessa
	srv := &http.Server{Addr: fmt.Sprintf("%s:%d", os.Getenv("BM_LISTEN"), port), Handler: cors(mux), ReadHeaderTimeout: 15 * time.Second}
	return srv.ListenAndServe()
}
