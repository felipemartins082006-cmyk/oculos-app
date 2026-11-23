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

// Celular pareado. Guardamos só o hash da chave, nunca a chave.
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
	Welcome bool     `json:"welcome"` // já mostrou a mensagem de boas-vindas
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
	return filepath.Join(base, "ControleMartins")
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
		cfg.Port = 47800
	}
	if cfg.Code == "" {
		cfg.Code = newCode()
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

// Código de pareamento: 8 letras/números sem os que confundem (0/O, 1/I/L).
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

func newToken() string {
	b := make([]byte, 32)
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
	tok := newToken()
	cfgMu.Lock()
	defer cfgMu.Unlock()
	now := time.Now()
	cfg.Devices = append(cfg.Devices, Device{Name: name, Hash: hashToken(tok), Created: now, Last: now})
	saveLocked()
	return tok
}

// Confere a chave do celular. Devolve o nome do celular.
func checkToken(tok string) (string, bool) {
	if tok == "" {
		return "", false
	}
	h := hashToken(tok)
	cfgMu.Lock()
	defer cfgMu.Unlock()
	for i := range cfg.Devices {
		if cfg.Devices[i].Hash == h {
			cfg.Devices[i].Last = time.Now()
			saveLocked()
			return cfg.Devices[i].Name, true
		}
	}
	return "", false
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
