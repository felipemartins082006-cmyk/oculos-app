package main

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Celular (ou outro aparelho) com acesso. Guardamos só o hash da chave.
type Device struct {
	Name    string    `json:"name"`
	Hash    string    `json:"hash"`
	Created time.Time `json:"created"`
	Last    time.Time `json:"last"`
}

type Config struct {
	Port    int      `json:"port"`
	Code    string   `json:"code"`
	Devices []Device `json:"devices"`
	Welcome bool     `json:"welcome"`

	AudioDir   string `json:"audioDir"`   // onde as músicas ficam
	VideoDir   string `json:"videoDir"`   // onde os vídeos ficam
	AudioFmt   string `json:"audioFmt"`   // mp3 | m4a | opus
	AudioKbps  int    `json:"audioKbps"`  // 128 | 192 | 256 | 320 (só mp3)
	VideoQual  string `json:"videoQual"`  // best | 1080 | 720 | 480 | 360
	Model      string `json:"model"`      // rapido | bom | otimo
	Language   string `json:"language"`   // auto | pt | en | es ...
	AutoLyrics bool   `json:"autoLyrics"` // fazer a letra sozinho depois de baixar música
	AutoSubs   bool   `json:"autoSubs"`   // fazer a legenda sozinho depois de baixar vídeo
	Parallel   int    `json:"parallel"`   // downloads ao mesmo tempo
}

// Configurações que o app pode ler e mudar.
type Settings struct {
	AudioDir   string `json:"audioDir"`
	VideoDir   string `json:"videoDir"`
	AudioFmt   string `json:"audioFmt"`
	AudioKbps  int    `json:"audioKbps"`
	VideoQual  string `json:"videoQual"`
	Model      string `json:"model"`
	Language   string `json:"language"`
	AutoLyrics bool   `json:"autoLyrics"`
	AutoSubs   bool   `json:"autoSubs"`
	Parallel   int    `json:"parallel"`
}

var (
	cfgMu   sync.Mutex
	cfg     Config
	cfgPath string
)

func configDir() string {
	base, err := os.UserConfigDir()
	if err != nil {
		base = "."
	}
	if d := os.Getenv("BAIXA_MARTINS_DIR"); d != "" { // para testes
		return d
	}
	return filepath.Join(base, "BaixaMartins")
}

func loadConfig() {
	dir := configDir()
	_ = os.MkdirAll(dir, 0o700)
	cfgPath = filepath.Join(dir, "config.json")
	cfgMu.Lock()
	defer cfgMu.Unlock()
	if b, err := os.ReadFile(cfgPath); err == nil {
		_ = json.Unmarshal(b, &cfg)
	}
	if cfg.Port == 0 {
		cfg.Port = 47900
	}
	if cfg.Code == "" {
		cfg.Code = newCode()
	}
	home, _ := os.UserHomeDir()
	if cfg.AudioDir == "" {
		cfg.AudioDir = filepath.Join(musicDir(home), "BaixaMartins")
		if d := os.Getenv("BM_AUDIO_DIR"); d != "" {
			cfg.AudioDir = d
		}
	}
	if cfg.VideoDir == "" {
		cfg.VideoDir = filepath.Join(videosDir(home), "BaixaMartins")
		if d := os.Getenv("BM_VIDEO_DIR"); d != "" {
			cfg.VideoDir = d
		}
	}
	if cfg.AudioFmt == "" {
		cfg.AudioFmt = "mp3"
	}
	if cfg.AudioKbps == 0 {
		cfg.AudioKbps = 320
	}
	if cfg.VideoQual == "" {
		cfg.VideoQual = "1080"
	}
	if cfg.Model == "" {
		cfg.Model = "otimo"
		if os.Getenv("BM_PYTHON") != "" {
			cfg.Model = "bom" // no celular a "Ótima" fica lenta demais
		}
	}
	if cfg.Language == "" {
		cfg.Language = "auto"
	}
	if cfg.Parallel == 0 {
		cfg.Parallel = 2
	}
	saveLocked()
}

func saveLocked() {
	b, _ := json.MarshalIndent(cfg, "", "  ")
	tmp := cfgPath + ".tmp"
	if os.WriteFile(tmp, b, 0o600) == nil {
		_ = os.Rename(tmp, cfgPath)
	}
}

func getSettings() Settings {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	return Settings{cfg.AudioDir, cfg.VideoDir, cfg.AudioFmt, cfg.AudioKbps, cfg.VideoQual, cfg.Model,
		cfg.Language, cfg.AutoLyrics, cfg.AutoSubs, cfg.Parallel}
}

func oneOf(v string, opts ...string) bool {
	for _, o := range opts {
		if v == o {
			return true
		}
	}
	return false
}

func setSettings(s Settings) {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	if strings.TrimSpace(s.AudioDir) != "" {
		cfg.AudioDir = filepath.Clean(strings.TrimSpace(s.AudioDir))
	}
	if strings.TrimSpace(s.VideoDir) != "" {
		cfg.VideoDir = filepath.Clean(strings.TrimSpace(s.VideoDir))
	}
	if oneOf(s.AudioFmt, "mp3", "m4a", "opus") {
		cfg.AudioFmt = s.AudioFmt
	}
	if s.AudioKbps == 128 || s.AudioKbps == 192 || s.AudioKbps == 256 || s.AudioKbps == 320 {
		cfg.AudioKbps = s.AudioKbps
	}
	if oneOf(s.VideoQual, "best", "2160", "1440", "1080", "720", "480", "360") {
		cfg.VideoQual = s.VideoQual
	}
	if _, ok := models[s.Model]; ok {
		cfg.Model = s.Model
	}
	if s.Language != "" && len(s.Language) <= 5 {
		cfg.Language = s.Language
	}
	cfg.AutoLyrics = s.AutoLyrics
	cfg.AutoSubs = s.AutoSubs
	if s.Parallel >= 1 && s.Parallel <= 4 {
		cfg.Parallel = s.Parallel
	}
	saveLocked()
}

// Código de acesso: 8 letras/números sem os que confundem (0/O, 1/I/L).
func newCode() string {
	const abc = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	out := make([]byte, 0, 9)
	for i, v := range b {
		if i == 4 {
			out = append(out, '-')
		}
		out = append(out, abc[int(v)%len(abc)])
	}
	return string(out)
}

func normCode(s string) string {
	var b strings.Builder
	for _, r := range strings.ToUpper(s) {
		if (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	return b.String()
}

func hashToken(t string) string {
	h := sha256.Sum256([]byte(t))
	return hex.EncodeToString(h[:])
}

func randHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func currentCode() string {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	return cfg.Code
}

func regenerateCode() string {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	cfg.Code = newCode()
	saveLocked()
	return cfg.Code
}

func addDevice(name string) string {
	tok := randHex(32)
	cfgMu.Lock()
	defer cfgMu.Unlock()
	now := time.Now()
	cfg.Devices = append(cfg.Devices, Device{Name: name, Hash: hashToken(tok), Created: now, Last: now})
	saveLocked()
	return tok
}

var lastSeenSave = map[string]time.Time{}

func checkToken(tok string) bool {
	if tok == "" {
		return false
	}
	h := hashToken(tok)
	cfgMu.Lock()
	defer cfgMu.Unlock()
	for i := range cfg.Devices {
		if cfg.Devices[i].Hash == h {
			now := time.Now()
			cfg.Devices[i].Last = now
			if now.Sub(lastSeenSave[h]) > 10*time.Minute { // não grava o arquivo a cada pedaço de música
				lastSeenSave[h] = now
				saveLocked()
			}
			return true
		}
	}
	return false
}

func removeDevice(tok string) {
	h := hashToken(tok)
	cfgMu.Lock()
	defer cfgMu.Unlock()
	out := cfg.Devices[:0]
	for _, d := range cfg.Devices {
		if d.Hash != h {
			out = append(out, d)
		}
	}
	cfg.Devices = out
	saveLocked()
}

func clearDevices() {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	cfg.Devices = nil
	saveLocked()
}

func deviceCount() int {
	cfgMu.Lock()
	defer cfgMu.Unlock()
	return len(cfg.Devices)
}
