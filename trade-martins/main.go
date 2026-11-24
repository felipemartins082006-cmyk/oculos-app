// TradeMartins — robô de trade de cripto (Binance Spot) com painel no navegador.
//
// Começa sempre no modo SIMULADO (dinheiro de mentira, preços reais).
// O painel só abre neste computador (127.0.0.1), então a chave da API não sai daqui.
package main

import (
	"embed"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
)

//go:embed web
var webFS embed.FS

const addr = "127.0.0.1:47900"
const port = 47900

var version = "2.4" // var (e não const) para os testes poderem gerar outra versão com -ldflags

func configDir() string {
	base, err := os.UserConfigDir()
	if err != nil {
		base = "."
	}
	d := filepath.Join(base, "TradeMartins")
	_ = os.MkdirAll(d, 0o700)
	return d
}

func openBrowser(url string) {
	if os.Getenv("TRADEMARTINS_NO_BROWSER") != "" {
		return
	}
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case "darwin":
		cmd = exec.Command("open", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	_ = cmd.Start()
}

// compareVersions devolve -1, 0 ou 1 (ex.: "1.10" > "1.9").
func compareVersions(a, b string) int {
	pa, pb := strings.Split(a, "."), strings.Split(b, ".")
	for i := 0; i < len(pa) || i < len(pb); i++ {
		var x, y int
		if i < len(pa) {
			x, _ = strconv.Atoi(pa[i])
		}
		if i < len(pb) {
			y, _ = strconv.Atoi(pb[i])
		}
		if x != y {
			if x < y {
				return -1
			}
			return 1
		}
	}
	return 0
}

// takeOver: se uma versão MAIS ANTIGA do TradeMartins estiver aberta, fecha ela e assume o lugar.
// Assim, atualizar é só abrir o .exe novo.
func takeOver() (net.Listener, string) {
	cl := &http.Client{Timeout: 3 * time.Second}
	resp, err := cl.Get("http://" + addr + "/api/status")
	if err != nil {
		return nil, "A porta do painel está ocupada por outro programa."
	}
	var st struct {
		Version string `json:"version"`
	}
	_ = json.NewDecoder(resp.Body).Decode(&st)
	resp.Body.Close()
	switch {
	case st.Version == "":
		return nil, "A porta do painel está ocupada por outro programa."
	case compareVersions(version, st.Version) == 0:
		return nil, "O TradeMartins já está rodando. Abrindo o painel…"
	case compareVersions(version, st.Version) < 0:
		return nil, fmt.Sprintf("Já está aberta uma versão MAIS NOVA (%s) do TradeMartins. Abrindo o painel dela…", st.Version)
	}

	fmt.Printf("Fechando a versão antiga (%s) para abrir a %s…\n", st.Version, version)
	req, _ := http.NewRequest("POST", "http://"+addr+"/api/quit", strings.NewReader("{}"))
	req.Header.Set("X-TradeMartins", "1")
	req.Header.Set("Content-Type", "application/json")
	resp, err = cl.Do(req)
	graceful := err == nil && resp.StatusCode == 200
	if resp != nil {
		resp.Body.Close()
	}
	if !graceful {
		// versões 1.0 e 1.1 não sabem fechar sozinhas
		if err := killPortOwner(port); err != nil {
			return nil, "Não consegui fechar a versão antiga. Feche a janela preta dela e abra esta de novo."
		}
	}
	for i := 0; i < 60; i++ {
		time.Sleep(250 * time.Millisecond)
		if ln, err := net.Listen("tcp", addr); err == nil {
			return ln, ""
		}
	}
	return nil, "A versão antiga não fechou. Feche a janela preta dela e abra esta de novo."
}

func main() {
	auto := len(os.Args) > 1 && os.Args[1] == "--auto" // aberto pelo Windows ao ligar o PC
	dir := configDir()
	logPath := filepath.Join(dir, "trade.log")
	if st, err := os.Stat(logPath); err == nil && st.Size() > 2<<20 {
		_ = os.Rename(logPath, logPath+".old") // guarda só o último pedaço
	}
	if f, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600); err == nil {
		log.SetOutput(io.MultiWriter(f, os.Stdout))
	}
	url := "http://" + addr + "/"

	ln, err := net.Listen("tcp", addr)
	if err != nil {
		var msg string
		if ln, msg = takeOver(); ln == nil {
			fmt.Println(msg)
			if !auto {
				openBrowser(url)
			}
			time.Sleep(3 * time.Second)
			return
		}
	}

	fmt.Println("==============================================")
	fmt.Println("  TradeMartins", version, "— robô de cripto")
	fmt.Println("  Painel:", url)
	fmt.Println("  Dados em:", dir)
	fmt.Println("  Deixe esta janela ABERTA (pode minimizar).")
	fmt.Println("  Fechar esta janela = robô para.")
	fmt.Println("==============================================")

	bot := NewBot(dir)
	bot.mu.Lock()
	bot.applyPlatformLocked()
	bot.mu.Unlock()
	go bot.Run()
	go bot.TelegramCommands(nil)

	srv := &http.Server{Handler: newServer(bot), ReadHeaderTimeout: 10 * time.Second}
	if auto {
		minimizeConsole()
	} else {
		go func() { time.Sleep(400 * time.Millisecond); openBrowser(url) }()
	}
	log.Fatal(srv.Serve(ln))
}
