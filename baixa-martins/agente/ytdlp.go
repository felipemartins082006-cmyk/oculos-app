package main

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Entry struct {
	ID       string  `json:"id"`
	Title    string  `json:"title"`
	Channel  string  `json:"channel"`
	Duration float64 `json:"duration"`
	Views    int64   `json:"views,omitempty"`
	Live     bool    `json:"live,omitempty"`
}

type Resolved struct {
	Kind    string  `json:"kind"` // video | playlist | search
	Title   string  `json:"title"`
	Channel string  `json:"channel"`
	Entries []Entry `json:"entries"`
	Focus   string  `json:"focus,omitempty"` // o vídeo do link (quando o link é vídeo + playlist)
}

var videoIDRe = regexp.MustCompile(`^[A-Za-z0-9_-]{6,20}$`)

func ytdlpArgs(extra ...string) []string {
	a := []string{"--ignore-config", "--no-warnings", "--no-color"}
	if runtime.GOOS == "windows" {
		a = append(a, "--ffmpeg-location", binDir())
	}
	return append(a, extra...)
}

type ytInfo struct {
	Type       string   `json:"_type"`
	ID         string   `json:"id"`
	Title      string   `json:"title"`
	Uploader   string   `json:"uploader"`
	Channel    string   `json:"channel"`
	Artist     string   `json:"artist"`
	Artists    []string `json:"artists"`
	Track      string   `json:"track"`
	Album      string   `json:"album"`
	Duration   float64  `json:"duration"`
	Views      int64    `json:"view_count"`
	LiveStatus string   `json:"live_status"`
	IEKey      string   `json:"ie_key"`
	URL        string   `json:"url"`
	Entries    []ytInfo `json:"entries"`
	Ext        string   `json:"ext"`
	Width      int      `json:"width"`
	Height     int      `json:"height"`
}

func (y ytInfo) channel() string {
	for _, s := range []string{y.Channel, y.Uploader} {
		if s != "" {
			return strings.TrimSuffix(s, " - Topic")
		}
	}
	return ""
}

func runJSON(ctx context.Context, args ...string) (*ytInfo, error) {
	if err := ensureTools(ctx, "ytdlp", "deno"); err != nil {
		return nil, err
	}
	c := hiddenCmdCtx(ctx, toolPath("yt-dlp"), ytdlpArgs(args...)...)
	c.Env = toolEnv()
	var stderr strings.Builder
	c.Stderr = &stderr
	out, err := c.Output()
	if err != nil {
		return nil, friendlyError(stderr.String(), err)
	}
	var info ytInfo
	if err := json.Unmarshal(out, &info); err != nil {
		return nil, errors.New("resposta estranha do yt-dlp")
	}
	return &info, nil
}

func looksLikeURL(q string) bool {
	q = strings.ToLower(q)
	return strings.Contains(q, "://") || strings.HasPrefix(q, "www.") || strings.HasPrefix(q, "youtu") ||
		strings.HasPrefix(q, "music.youtube") || strings.HasPrefix(q, "m.youtube")
}

func resolve(ctx context.Context, q string) (*Resolved, error) {
	q = strings.TrimSpace(q)
	if q == "" {
		return nil, errors.New("digite um nome ou cole um link")
	}
	if !looksLikeURL(q) {
		info, err := runJSON(ctx, "-J", "--flat-playlist", "ytsearch24:"+q)
		if err != nil {
			return nil, err
		}
		r := &Resolved{Kind: "search", Title: q}
		for _, e := range info.Entries {
			if !videoIDRe.MatchString(e.ID) || e.LiveStatus == "is_live" {
				continue
			}
			r.Entries = append(r.Entries, Entry{e.ID, e.Title, e.channel(), e.Duration, e.Views, false})
		}
		return r, nil
	}
	if !strings.Contains(q, "://") {
		q = "https://" + q
	}
	focus := ""
	if u, err := url.Parse(q); err == nil {
		focus = u.Query().Get("v")
		if focus == "" && strings.Contains(u.Host, "youtu.be") {
			focus = strings.Trim(u.Path, "/")
		}
	}
	info, err := runJSON(ctx, "-J", "--flat-playlist", "--yes-playlist", q)
	if err != nil {
		return nil, err
	}
	if info.Type == "playlist" || len(info.Entries) > 0 {
		r := &Resolved{Kind: "playlist", Title: info.Title, Channel: info.channel(), Focus: focus}
		addEntries(r, info.Entries)
		if len(r.Entries) == 0 {
			return nil, errors.New("essa playlist está vazia ou é privada")
		}
		return r, nil
	}
	if !videoIDRe.MatchString(info.ID) {
		return nil, errors.New("esse link não parece ser do YouTube")
	}
	title := info.Title
	if info.Track != "" {
		title = info.Track
	}
	return &Resolved{Kind: "video", Title: title, Channel: info.channel(),
		Entries: []Entry{{info.ID, title, artistOf(*info), info.Duration, info.Views, info.LiveStatus == "is_live"}}}, nil
}

// Playlists de canal vêm com abas (Vídeos, Shorts…): achata tudo.
func addEntries(r *Resolved, es []ytInfo) {
	seen := map[string]bool{}
	var walk func([]ytInfo)
	walk = func(es []ytInfo) {
		for _, e := range es {
			if len(e.Entries) > 0 {
				walk(e.Entries)
				continue
			}
			if !videoIDRe.MatchString(e.ID) || seen[e.ID] || e.Title == "[Private video]" || e.Title == "[Deleted video]" {
				continue
			}
			seen[e.ID] = true
			r.Entries = append(r.Entries, Entry{e.ID, e.Title, e.channel(), e.Duration, e.Views, e.LiveStatus == "is_live"})
		}
	}
	walk(es)
}

func artistOf(y ytInfo) string {
	if len(y.Artists) > 0 {
		return strings.Join(y.Artists, ", ")
	}
	if y.Artist != "" {
		return y.Artist
	}
	return y.channel()
}

// Mensagens do yt-dlp em português.
func friendlyError(stderr string, err error) error {
	s := strings.ToLower(stderr)
	switch {
	case strings.Contains(s, "sign in to confirm your age"):
		return errors.New("o YouTube pede login para esse vídeo (restrição de idade)")
	case strings.Contains(s, "sign in to confirm you"):
		return errors.New("o YouTube bloqueou temporariamente (pede para confirmar que não é robô). Tente de novo mais tarde")
	case strings.Contains(s, "private video"):
		return errors.New("esse vídeo é privado")
	case strings.Contains(s, "video unavailable"), strings.Contains(s, "not available"):
		return errors.New("esse vídeo não está disponível")
	case strings.Contains(s, "members-only"), strings.Contains(s, "join this channel"):
		return errors.New("esse vídeo é só para membros do canal")
	case strings.Contains(s, "is not a valid url"), strings.Contains(s, "unsupported url"):
		return errors.New("não reconheci esse link")
	case strings.Contains(s, "unable to download") && strings.Contains(s, "getaddrinfo"),
		strings.Contains(s, "failed to resolve"), strings.Contains(s, "network is unreachable"):
		return errors.New("sem internet no computador")
	case strings.Contains(s, "no space left"):
		return errors.New("o disco do computador está cheio")
	case strings.Contains(s, "requested format is not available"):
		return errors.New("essa qualidade não existe para esse vídeo. Escolha outra")
	}
	// última linha de erro do yt-dlp
	lines := strings.Split(strings.TrimSpace(stderr), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		l := strings.TrimSpace(lines[i])
		if strings.HasPrefix(l, "ERROR:") {
			return errors.New(strings.TrimSpace(strings.TrimPrefix(l, "ERROR:")))
		}
	}
	if err != nil {
		return fmt.Errorf("o yt-dlp falhou (%v)", err)
	}
	return errors.New("o yt-dlp falhou")
}

// Nome de pasta seguro no Windows.
func safeName(s string) string {
	s = strings.Map(func(r rune) rune {
		if strings.ContainsRune(`<>:"/\|?*`, r) || r < 32 {
			return '_'
		}
		return r
	}, s)
	s = strings.Trim(strings.TrimSpace(s), ". ")
	if len([]rune(s)) > 80 {
		s = string([]rune(s)[:80])
	}
	if s == "" {
		s = "Playlist"
	}
	return s
}

func formatArgs(kind, quality, format string, kbps int) []string {
	if kind == "audio" {
		switch format {
		case "m4a":
			return []string{"-f", "bestaudio[ext=m4a]/bestaudio/best", "-x", "--audio-format", "m4a"}
		case "opus":
			return []string{"-f", "bestaudio[acodec=opus]/bestaudio/best", "-x", "--audio-format", "opus"}
		default:
			return []string{"-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", fmt.Sprintf("%dK", kbps)}
		}
	}
	a := []string{"-f", "bv*+ba/b", "--merge-output-format", "mp4"}
	if quality == "" || quality == "best" {
		return append(a, "-S", "res,fps,acodec:m4a")
	}
	// até 1080p: prefere H.264 (toca em qualquer aparelho)
	if h, _ := strconv.Atoi(quality); h > 0 && h <= 1080 {
		return append(a, "-S", "res:"+quality+",vcodec:h264,acodec:m4a")
	}
	return append(a, "-S", "res:"+quality+",fps,acodec:m4a")
}

var progRe = regexp.MustCompile(`^BMP (\S+) (\S+) (\S+) (\S+) (\S+)`)

func num(s string) float64 {
	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		return 0
	}
	return f
}

func runDownload(j *Job) error {
	ctx := j.ctx
	updJob(j, func(j *Job) { j.Phase = "Preparando ferramentas…" })
	if err := ensureTools(ctx, "ytdlp", "deno", "ffmpeg"); err != nil {
		return err
	}
	s := getSettings()
	dir := s.AudioDir
	if j.Kind == "video" {
		dir = s.VideoDir
	}
	if j.Playlist != "" {
		dir = filepath.Join(dir, safeName(j.Playlist))
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return fmt.Errorf("não consegui criar a pasta %s", dir)
	}
	format := j.Format
	if format == "" {
		format = s.AudioFmt
	}
	quality := j.Quality
	if quality == "" {
		quality = s.VideoQual
	}
	_ = os.MkdirAll(tmpDir(), 0o755)
	fFile := filepath.Join(tmpDir(), j.ID+".arquivo.txt")
	jFile := filepath.Join(tmpDir(), j.ID+".info.json")
	_ = os.Remove(fFile)
	_ = os.Remove(jFile)
	defer os.Remove(fFile)
	defer os.Remove(jFile)
	args := ytdlpArgs("--no-playlist", "--newline", "--no-mtime", "--no-part",
		"--embed-metadata", "--embed-thumbnail", "--convert-thumbnails", "jpg",
		"--progress-template", "download:BMP %(progress.downloaded_bytes)s %(progress.total_bytes)s %(progress.total_bytes_estimate)s %(progress.speed)s %(progress.eta)s",
		"--print-to-file", "after_move:%(filepath)s", fFile,
		"--print-to-file", "after_move:%(.{id,title,uploader,channel,artist,artists,track,album,duration,width,height,ext})j", jFile,
		"-P", dir, "-o", "%(title).150B.%(ext)s",
	)
	if runtime.GOOS == "windows" {
		args = append(args, "--windows-filenames")
	}
	kbps := s.AudioKbps
	if j.Kbps == 128 || j.Kbps == 192 || j.Kbps == 256 || j.Kbps == 320 {
		kbps = j.Kbps
	}
	args = append(args, formatArgs(j.Kind, quality, format, kbps)...)
	args = append(args, "https://www.youtube.com/watch?v="+j.VideoID)

	c := hiddenCmdCtx(ctx, toolPath("yt-dlp"), args...)
	c.Env = toolEnv()
	stdout, _ := c.StdoutPipe()
	stderrP, _ := c.StderrPipe()
	if err := c.Start(); err != nil {
		return err
	}
	stopKill := context.AfterFunc(ctx, func() { killTree(c) })
	defer stopKill()

	var (
		errBuf   strings.Builder
		errMu    sync.Mutex
		final    string
		meta     ytInfo
		phase    = 0
		lastDone float64
		wg       sync.WaitGroup
	)
	wg.Add(1)
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stderrP)
		sc.Buffer(make([]byte, 64<<10), 1<<20)
		for sc.Scan() {
			errMu.Lock()
			if errBuf.Len() < 16<<10 {
				errBuf.WriteString(sc.Text() + "\n")
			}
			errMu.Unlock()
		}
	}()
	sc := bufio.NewScanner(stdout)
	sc.Buffer(make([]byte, 64<<10), 4<<20)
	for sc.Scan() {
		line := sc.Text()
		switch {
		case strings.HasPrefix(line, "BMP "):
			m := progRe.FindStringSubmatch(line)
			if m == nil {
				continue
			}
			done, total := num(m[1]), num(m[2])
			if total == 0 {
				total = num(m[3])
			}
			if done < lastDone*0.5 && lastDone > 0 {
				phase++ // segundo arquivo (áudio do vídeo)
			}
			lastDone = done
			p := 0.0
			if total > 0 {
				p = done / total
			}
			lbl := "Baixando"
			if j.Kind == "video" {
				if phase == 0 {
					p, lbl = p*0.88, "Baixando o vídeo"
				} else {
					p, lbl = 0.88+p*0.1, "Baixando o áudio"
				}
			} else {
				p *= 0.95
			}
			updJob(j, func(j *Job) {
				j.Progress, j.Speed, j.ETA, j.Phase = p, num(m[4]), int(num(m[5])), lbl
			})
		case strings.HasPrefix(line, "[ExtractAudio]"), strings.HasPrefix(line, "[Merger]"),
			strings.HasPrefix(line, "[EmbedThumbnail]"), strings.HasPrefix(line, "[Metadata]"):
			lbl := "Finalizando…"
			if strings.HasPrefix(line, "[ExtractAudio]") {
				lbl = "Convertendo para " + strings.ToUpper(format)
			} else if strings.HasPrefix(line, "[Merger]") {
				lbl = "Juntando vídeo e áudio"
			}
			updJob(j, func(j *Job) { j.Phase, j.Speed, j.ETA = lbl, 0, 0; j.Progress = maxf(j.Progress, 0.96) })
		}
	}
	wg.Wait()
	err := c.Wait()
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if err != nil {
		errMu.Lock()
		defer errMu.Unlock()
		log.Printf("download %s: %v\n%s", j.VideoID, err, errBuf.String())
		return friendlyError(errBuf.String(), err)
	}
	if b, err := os.ReadFile(fFile); err == nil {
		ls := strings.Split(strings.TrimSpace(string(b)), "\n")
		final = strings.TrimSpace(ls[len(ls)-1])
	}
	if b, err := os.ReadFile(jFile); err == nil {
		ls := strings.Split(strings.TrimSpace(string(b)), "\n")
		_ = json.Unmarshal([]byte(ls[len(ls)-1]), &meta)
	}
	if final == "" {
		return errors.New("o yt-dlp terminou mas não disse onde salvou")
	}
	st, err := os.Stat(final)
	if err != nil {
		return errors.New("o arquivo baixado sumiu")
	}
	title := meta.Title
	if j.Kind == "audio" && meta.Track != "" {
		title = meta.Track
	}
	if title == "" {
		title = j.Title
	}
	suffix := "-a"
	if j.Kind == "video" {
		suffix = "-v"
	}
	id := j.VideoID + suffix
	old := getItem(id)
	it := &Item{ID: id, VideoID: j.VideoID, Kind: j.Kind, Title: title, Artist: artistOf(meta), Album: meta.Album,
		Duration: meta.Duration, File: final, Size: st.Size(), Ext: strings.TrimPrefix(filepath.Ext(final), "."),
		Width: meta.Width, Height: meta.Height, Playlist: j.Playlist, Added: time.Now()}
	if it.Artist == "" {
		it.Artist = j.Channel
	}
	if it.Duration == 0 {
		it.Duration = j.Duration
	}
	if old != nil && old.Transcript != "" {
		if _, err := os.Stat(transcriptPath(id)); err == nil {
			it.Transcript, it.Lang = old.Transcript, old.Lang
			if old.File != final { // baixou de novo em outro lugar: recria o .lrc/.srt
				if t, err := loadTranscript(id); err == nil {
					writeSidecar(it, t)
				}
			}
		}
	}
	putItem(it)
	updJob(j, func(j *Job) { j.ItemID = id })
	go cacheThumb(j.VideoID)
	return nil
}

func maxf(a, b float64) float64 {
	if a > b {
		return a
	}
	return b
}
