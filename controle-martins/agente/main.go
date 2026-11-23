// Controle Martins — programa que fica rodando no computador (perto do relógio)
// e recebe os comandos do app do celular.
package main

import (
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

type Monitor struct {
	X, Y, W, H int
	Primary    bool
}

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
func addresses() []string {
	var ts, lan []string
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
	var out []string
	for _, a := range ts {
		out = append(out, a+"   (Tailscale: funciona de qualquer lugar)")
	}
	for _, a := range lan {
		out = append(out, a+"   (só na rede de casa)")
	}
	return out
}

func pairingText() string {
	var b strings.Builder
	b.WriteString("Controle Martins está ativo neste computador.\n\n")
	fmt.Fprintf(&b, "CÓDIGO DE PAREAMENTO:   %s\n\n", currentCode())
	b.WriteString("No app do celular, toque em \"Adicionar computador\" e use um destes endereços:\n\n")
	addrs := addresses()
	if len(addrs) == 0 {
		b.WriteString("  (nenhuma rede encontrada)\n")
	}
	hasTS := false
	for _, a := range addrs {
		if strings.Contains(a, "Tailscale") {
			hasTS = true
		}
		b.WriteString("  • " + a + "\n")
	}
	if !hasTS {
		b.WriteString("\nPara usar fora de casa, instale o Tailscale neste PC e no celular\n(mesma conta nos dois). Depois o endereço 100.x aparece aqui.\n")
	}
	fmt.Fprintf(&b, "\nNome deste PC: %s   •   Porta: %d", hostname(), cfg.Port)
	return b.String()
}

func main() {
	loadConfig()
	setupLog()
	platformInit()
	log.Printf("Controle Martins %s iniciando na porta %d", version, cfg.Port)

	// só um por vez: se a porta já estiver em uso, outro já está rodando
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", cfg.Port))
	if err != nil {
		notify("Controle Martins", fmt.Sprintf("O Controle Martins já está rodando (ou a porta %d está ocupada).\n\nProcure o ícone perto do relógio.", cfg.Port))
		os.Exit(1)
	}
	ln.Close()

	go func() {
		if err := runServer(cfg.Port); err != nil {
			log.Printf("servidor parou: %v", err)
			notify("Controle Martins", "O Controle Martins parou: "+err.Error())
			os.Exit(1)
		}
	}()

	cfgMu.Lock()
	first := !cfg.Welcome
	cfg.Welcome = true
	saveLocked()
	cfgMu.Unlock()
	if first {
		setAutostart(true)
		go notify("Controle Martins", pairingText()+"\n\nEle vai abrir sozinho junto com o Windows.\nPara ver este código de novo, clique no ícone do Controle Martins perto do relógio.")
	}
	runTray()
}
