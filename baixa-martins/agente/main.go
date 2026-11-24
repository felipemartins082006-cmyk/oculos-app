// Baixa Martins — programa que fica rodando no computador (perto do relógio):
// baixa músicas, vídeos e playlists do YouTube e faz letras e legendas com IA.
// O app abre no navegador do PC e também no celular (APK ou navegador).
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"time"
)

func setupLog() {
	p := filepath.Join(configDir(), "agente.log")
	if st, err := os.Stat(p); err == nil && st.Size() > 1<<20 {
		_ = os.Rename(p, p+".old")
	}
	f, err := os.OpenFile(p, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return
	}
	log.SetOutput(io.MultiWriter(f, os.Stderr))
}

// Endereços deste PC. Os do Tailscale (100.x) vêm primeiro: funcionam de qualquer lugar.
func splitAddresses() (ts, lan []string) {
	ifaces, _ := net.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			ipn, ok := a.(*net.IPNet)
			if !ok || ipn.IP.To4() == nil {
				continue
			}
			ip := ipn.IP.To4()
			if ip[0] == 169 && ip[1] == 254 {
				continue
			}
			if ip[0] == 100 && ip[1] >= 64 && ip[1] < 128 {
				ts = append(ts, ip.String())
			} else {
				lan = append(lan, ip.String())
			}
		}
	}
	sort.Strings(ts)
	sort.Strings(lan)
	return ts, lan
}

func appURL() string { return fmt.Sprintf("http://localhost:%d/", cfg.Port) }

func main() {
	loadConfig()
	setupLog()
	platformInit()
	loadLibrary()
	log.Printf("Baixa Martins %s iniciando na porta %d", version, cfg.Port)

	// só um por vez: se a porta já estiver em uso, outro já está rodando
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", cfg.Port))
	if err != nil && replaceOldVersion(cfg.Port) {
		ln, err = net.Listen("tcp", fmt.Sprintf(":%d", cfg.Port))
	}
	if err != nil {
		// já está rodando: só abre o app no navegador
		if openURL(appURL()) != nil {
			notify("Baixa Martins", fmt.Sprintf("O Baixa Martins já está rodando (ou a porta %d está ocupada).\n\nAbra no navegador: %s", cfg.Port, appURL()))
		}
		os.Exit(0)
	}
	ln.Close()

	go func() {
		if err := runServer(cfg.Port); err != nil {
			log.Printf("servidor parou: %v", err)
			notify("Baixa Martins", "O Baixa Martins parou: "+err.Error())
			os.Exit(1)
		}
	}()
	go prepareAtStartup()

	cfgMu.Lock()
	first := !cfg.Welcome
	cfg.Welcome = true
	saveLocked()
	cfgMu.Unlock()
	if first || autostartOn() {
		setAutostart(true) // aponta para este .exe
	}
	// abrir o .exe pela mão abre o app; iniciar com o Windows (--fundo) fica só no ícone
	if !hasArg("--fundo") {
		go func() {
			time.Sleep(600 * time.Millisecond)
			_ = openURL(appURL())
		}()
	}
	runTray()
}

func hasArg(a string) bool {
	for _, v := range os.Args[1:] {
		if v == a {
			return true
		}
	}
	return false
}

// Se uma versão diferente já estiver rodando, fecha ela e assume no lugar.
func replaceOldVersion(port int) bool {
	cl := &http.Client{Timeout: 2 * time.Second}
	r, err := cl.Get(fmt.Sprintf("http://127.0.0.1:%d/api/info", port))
	if err != nil {
		return false
	}
	var info struct{ App, Version string }
	_ = json.NewDecoder(r.Body).Decode(&info)
	r.Body.Close()
	if info.App != "baixa-martins" || info.Version == version {
		return false
	}
	log.Printf("fechando a versão antiga %s", info.Version)
	if killPortOwner(port) != nil {
		return false
	}
	for i := 0; i < 20; i++ {
		time.Sleep(250 * time.Millisecond)
		if l, err := net.Listen("tcp", fmt.Sprintf(":%d", port)); err == nil {
			l.Close()
			return true
		}
	}
	return false
}
