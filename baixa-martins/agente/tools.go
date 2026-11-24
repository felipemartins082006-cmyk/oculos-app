package main

// Ferramentas que o Baixa Martins usa por baixo:
//   yt-dlp  — baixa do YouTube (músicas, vídeos, playlists)
//   deno    — o yt-dlp precisa dele para entender o YouTube
//   ffmpeg  — converte para MP3, junta vídeo + áudio, prepara o áudio para a transcrição
//   whisper — inteligência artificial que ouve o áudio e escreve a letra/legenda
// No Windows elas são baixadas sozinhas na primeira vez (pasta %APPDATA%\BaixaMartins\ferramentas).

import (
	"archive/zip"
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"time"
)

type toolSpec struct {
	Key   string
	Label string
	Urls  []string          // tenta na ordem
	Zip   bool              // é um .zip (extrai só os arquivos que interessam)
	Pick  func(string) bool // quais arquivos do zip extrair (nome base)
	Exe   string            // arquivo principal (sem .exe)
	Size  string
}

var toolSpecs = []toolSpec{
	{Key: "ytdlp", Label: "yt-dlp (baixar do YouTube)", Exe: "yt-dlp", Size: "23 MB",
		Urls: []string{"https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"}},
	{Key: "deno", Label: "Deno (o YouTube precisa)", Exe: "deno", Size: "43 MB", Zip: true,
		Urls: []string{"https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip"},
		Pick: func(n string) bool { return strings.EqualFold(n, "deno.exe") }},
	{Key: "ffmpeg", Label: "FFmpeg (converter áudio e vídeo)", Exe: "ffmpeg", Size: "200 MB", Zip: true,
		Urls: []string{
			"https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
			"https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
		},
		Pick: func(n string) bool { return strings.EqualFold(n, "ffmpeg.exe") || strings.EqualFold(n, "ffprobe.exe") }},
	{Key: "whisper", Label: "Whisper (transcrição por IA)", Exe: "whisper-cli", Size: "10 MB", Zip: true,
		Urls: []string{
			"https://github.com/ggml-org/whisper.cpp/releases/latest/download/whisper-bin-x64.zip",
			"https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.2/whisper-bin-x64.zip",
			"https://github.com/ggml-org/whisper.cpp/releases/download/v1.8.5/whisper-bin-x64.zip",
		},
		Pick: func(n string) bool {
			n = strings.ToLower(n)
			return n == "whisper-cli.exe" || (strings.HasSuffix(n, ".dll") && n != "sdl2.dll")
		}},
}

type modelSpec struct {
	Key, Label, File, Url, Size string
}

var models = map[string]modelSpec{
	"rapido": {"rapido", "Rápida (menos precisa)", "ggml-base.bin",
		"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin", "148 MB"},
	"bom": {"bom", "Boa", "ggml-small.bin",
		"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin", "488 MB"},
	"otimo": {"otimo", "Ótima (recomendada)", "ggml-large-v3-turbo-q5_0.bin",
		"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin", "574 MB"},
}

// Estado mostrado no app ("Preparando: baixando FFmpeg 40%").
type ToolState struct {
	Key      string  `json:"key"`
	Label    string  `json:"label"`
	Size     string  `json:"size"`
	Ready    bool    `json:"ready"`
	Busy     bool    `json:"busy"`
	Progress float64 `json:"progress"`
	Error    string  `json:"error,omitempty"`
}

var (
	toolMu     sync.Mutex
	toolStates = map[string]*ToolState{}
	toolLocks  = map[string]*sync.Mutex{}
)

func binDir() string   { return filepath.Join(configDir(), "ferramentas") }
func modelDir() string { return filepath.Join(configDir(), "modelos") }
func exeName(n string) string {
	if runtime.GOOS == "windows" {
		return n + ".exe"
	}
	return n
}

// No celular (APK) os programas vêm dentro do app; o Android passa onde estão.
func isAndroid() bool { return os.Getenv("BM_PYTHON") != "" }

func envTool(exe string) string {
	switch exe {
	case "yt-dlp":
		return os.Getenv("BM_YTDLP")
	case "ffmpeg":
		return os.Getenv("BM_FFMPEG")
	case "deno":
		return os.Getenv("BM_QJS") // QuickJS faz o papel do Deno no celular
	case "whisper-cli":
		if v8 := os.Getenv("BM_WHISPER_V8"); v8 != "" && !cpuFast() {
			return v8
		}
		return os.Getenv("BM_WHISPER")
	}
	return ""
}

// Processador ARM com instruções novas (dotprod): usa o Whisper mais rápido.
func cpuFast() bool {
	b, err := os.ReadFile("/proc/cpuinfo")
	if err != nil {
		return true
	}
	return strings.Contains(string(b), "asimddp")
}

// Comando do yt-dlp. No celular ele é um script Python.
func ytdlpCmd(ctx context.Context, args ...string) *exec.Cmd {
	if py := os.Getenv("BM_PYTHON"); py != "" {
		return hiddenCmdCtx(ctx, py, append([]string{os.Getenv("BM_YTDLP")}, args...)...)
	}
	return hiddenCmdCtx(ctx, toolPath("yt-dlp"), args...)
}

// Caminho do programa. Fora do Windows (testes), usa o que estiver instalado no sistema.
func toolPath(exe string) string {
	if p := envTool(exe); p != "" {
		return p
	}
	p := filepath.Join(binDir(), exeName(exe))
	if _, err := os.Stat(p); err == nil {
		return p
	}
	if runtime.GOOS != "windows" {
		if lp, err := exec.LookPath(exe); err == nil {
			return lp
		}
	}
	return ""
}

func specFor(key string) *toolSpec {
	for i := range toolSpecs {
		if toolSpecs[i].Key == key {
			return &toolSpecs[i]
		}
	}
	return nil
}

func stateFor(key string) *ToolState {
	toolMu.Lock()
	defer toolMu.Unlock()
	st := toolStates[key]
	if st == nil {
		st = &ToolState{Key: key}
		toolStates[key] = st
		toolLocks[key] = &sync.Mutex{}
	}
	return st
}

func setTool(key string, f func(*ToolState)) {
	toolMu.Lock()
	st := toolStates[key]
	f(st)
	toolMu.Unlock()
	broadcastTools()
}

func toolSnapshot() []ToolState {
	var out []ToolState
	for _, s := range toolSpecs {
		st := stateFor(s.Key)
		toolMu.Lock()
		c := *st
		toolMu.Unlock()
		c.Label, c.Size = s.Label, s.Size
		if isAndroid() {
			c.Size = "já vem no app"
			if s.Key == "deno" {
				c.Label = "QuickJS (o YouTube precisa)"
			}
		}
		c.Ready = toolPath(s.Exe) != ""
		out = append(out, c)
	}
	for _, k := range []string{"rapido", "bom", "otimo"} {
		m := models[k]
		st := stateFor("modelo-" + k)
		toolMu.Lock()
		c := *st
		toolMu.Unlock()
		c.Label, c.Size = "Modelo de IA: "+m.Label, m.Size
		if isAndroid() {
			c.Label = "Modelo de IA: " + map[string]string{"rapido": "Rápida", "bom": "Boa (recomendada)", "otimo": "Ótima (lenta no celular)"}[k]
		}
		_, err := os.Stat(filepath.Join(modelDir(), m.File))
		c.Ready = err == nil
		out = append(out, c)
	}
	return out
}

// Garante que as ferramentas existem (baixa se precisar). Bloqueia até terminar.
func ensureTools(ctx context.Context, keys ...string) error {
	for _, k := range keys {
		s := specFor(k)
		if s == nil {
			continue
		}
		stateFor(k)
		toolMu.Lock()
		lk := toolLocks[k]
		toolMu.Unlock()
		lk.Lock()
		err := func() error {
			if toolPath(s.Exe) != "" {
				return nil
			}
			if runtime.GOOS != "windows" {
				return fmt.Errorf("%s não está instalado neste computador", s.Exe)
			}
			return installTool(ctx, s)
		}()
		lk.Unlock()
		if err != nil {
			return err
		}
	}
	return nil
}

func ensureModel(ctx context.Context, key string) (string, error) {
	m, ok := models[key]
	if !ok {
		m = models["otimo"]
		key = "otimo"
	}
	sk := "modelo-" + key
	stateFor(sk)
	toolMu.Lock()
	lk := toolLocks[sk]
	toolMu.Unlock()
	lk.Lock()
	defer lk.Unlock()
	dst := filepath.Join(modelDir(), m.File)
	if _, err := os.Stat(dst); err == nil {
		return dst, nil
	}
	_ = os.MkdirAll(modelDir(), 0o755)
	setTool(sk, func(s *ToolState) { s.Busy, s.Progress, s.Error = true, 0, "" })
	err := downloadFile(ctx, m.Url, dst, func(p float64) { setTool(sk, func(s *ToolState) { s.Progress = p }) })
	setTool(sk, func(s *ToolState) {
		s.Busy = false
		if err != nil {
			s.Error = err.Error()
		}
	})
	if err != nil {
		return "", fmt.Errorf("não consegui baixar o modelo de IA: %w", err)
	}
	return dst, nil
}

func installTool(ctx context.Context, s *toolSpec) error {
	_ = os.MkdirAll(binDir(), 0o755)
	setTool(s.Key, func(st *ToolState) { st.Busy, st.Progress, st.Error = true, 0, "" })
	var lastErr error
	for _, u := range s.Urls {
		lastErr = installFrom(ctx, s, u)
		if lastErr == nil {
			break
		}
		log.Printf("ferramenta %s: %s falhou: %v", s.Key, u, lastErr)
	}
	setTool(s.Key, func(st *ToolState) {
		st.Busy = false
		if lastErr != nil {
			st.Error = lastErr.Error()
		}
	})
	if lastErr != nil {
		return fmt.Errorf("não consegui baixar %s (precisa de internet): %w", s.Label, lastErr)
	}
	log.Printf("ferramenta %s instalada", s.Key)
	return nil
}

func installFrom(ctx context.Context, s *toolSpec, u string) error {
	prog := func(p float64) { setTool(s.Key, func(st *ToolState) { st.Progress = p }) }
	if !s.Zip {
		return downloadFile(ctx, u, filepath.Join(binDir(), exeName(s.Exe)), prog)
	}
	tmp := filepath.Join(binDir(), s.Key+".zip")
	defer os.Remove(tmp)
	if err := downloadFile(ctx, u, tmp, prog); err != nil {
		return err
	}
	zr, err := zip.OpenReader(tmp)
	if err != nil {
		return err
	}
	defer zr.Close()
	found := false
	for _, f := range zr.File {
		base := path.Base(strings.ReplaceAll(f.Name, "\\", "/"))
		if f.FileInfo().IsDir() || !s.Pick(base) {
			continue
		}
		if err := extractOne(f, filepath.Join(binDir(), base)); err != nil {
			return err
		}
		if strings.EqualFold(base, exeName(s.Exe)) {
			found = true
		}
	}
	if !found {
		return errors.New("o arquivo baixado não tem o programa esperado")
	}
	return nil
}

func extractOne(f *zip.File, dst string) error {
	rc, err := f.Open()
	if err != nil {
		return err
	}
	defer rc.Close()
	tmp := dst + ".part"
	out, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, rc); err != nil {
		out.Close()
		return err
	}
	out.Close()
	_ = os.Remove(dst)
	return os.Rename(tmp, dst)
}

var dlClient = &http.Client{Timeout: 0}

func downloadFile(ctx context.Context, u, dst string, progress func(float64)) error {
	req, err := http.NewRequestWithContext(ctx, "GET", u, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "BaixaMartins/"+version)
	r, err := dlClient.Do(req)
	if err != nil {
		return err
	}
	defer r.Body.Close()
	if r.StatusCode != 200 {
		return fmt.Errorf("o servidor respondeu %d", r.StatusCode)
	}
	tmp := dst + ".part"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	total := r.ContentLength
	var done int64
	buf := make([]byte, 256<<10)
	last := time.Now()
	for {
		n, rerr := r.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				f.Close()
				return werr
			}
			done += int64(n)
			if total > 0 && time.Since(last) > 400*time.Millisecond {
				last = time.Now()
				progress(float64(done) / float64(total))
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			f.Close()
			os.Remove(tmp)
			return rerr
		}
	}
	f.Close()
	if total > 0 && done != total {
		os.Remove(tmp)
		return errors.New("download incompleto")
	}
	progress(1)
	_ = os.Remove(dst)
	return os.Rename(tmp, dst)
}

// Ambiente para os programas: a pasta de ferramentas vem primeiro no PATH
// (assim o yt-dlp acha o deno e o ffmpeg).
func toolEnv() []string {
	env := os.Environ()
	for i, e := range env {
		if strings.HasPrefix(strings.ToUpper(e), "PATH=") {
			env[i] = e[:5] + binDir() + string(os.PathListSeparator) + e[5:]
			return env
		}
	}
	return append(env, "PATH="+binDir())
}

// Atualiza o yt-dlp (o YouTube muda sempre; versão velha para de funcionar).
func updateYtdlp(ctx context.Context) (string, error) {
	p := toolPath("yt-dlp")
	if p == "" || (!isAndroid() && !strings.HasPrefix(p, binDir())) {
		return "", errors.New("yt-dlp não foi instalado pelo Baixa Martins")
	}
	c := ytdlpCmd(ctx, "-U")
	c.Env = toolEnv()
	out, err := c.CombinedOutput()
	return strings.TrimSpace(string(out)), err
}

// Na abertura: baixa o que falta para baixar músicas/vídeos e atualiza o yt-dlp uma vez por dia.
func prepareAtStartup() {
	if runtime.GOOS != "windows" && !isAndroid() {
		return
	}
	ctx := context.Background()
	if err := ensureTools(ctx, "ytdlp", "deno", "ffmpeg"); err != nil {
		log.Printf("preparar: %v", err)
		return
	}
	_ = os.MkdirAll(binDir(), 0o755)
	st, err := os.Stat(filepath.Join(binDir(), ".atualizado"))
	if err == nil && time.Since(st.ModTime()) < 20*time.Hour {
		return
	}
	setTool("ytdlp", func(s *ToolState) { s.Busy = true })
	out, err := updateYtdlp(ctx)
	setTool("ytdlp", func(s *ToolState) { s.Busy = false })
	log.Printf("atualizar yt-dlp: %v %s", err, out)
	if err == nil {
		_ = os.WriteFile(filepath.Join(binDir(), ".atualizado"), []byte(time.Now().String()), 0o644)
	}
}
