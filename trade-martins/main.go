// TradeMartins — robô de trade de cripto (Binance Spot) com painel no navegador.
//
// Começa sempre no modo SIMULADO (dinheiro de mentira, preços reais).
// O painel só abre neste computador (127.0.0.1), então a chave da API não sai daqui.
package main

import (
	"embed"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"time"
)

//go:embed web
var webFS embed.FS

const addr = "127.0.0.1:47900"
const version = "1.0"

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

func main() {
	dir := configDir()
	if f, err := os.OpenFile(filepath.Join(dir, "trade.log"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600); err == nil {
		log.SetOutput(io.MultiWriter(f, os.Stdout))
	}
	url := "http://" + addr + "/"

	ln, err := net.Listen("tcp", addr)
	if err != nil {
		// Já está aberto: só mostra o painel de novo.
		fmt.Println("O TradeMartins já está rodando. Abrindo o painel…")
		openBrowser(url)
		time.Sleep(time.Second)
		return
	}

	fmt.Println("==============================================")
	fmt.Println("  TradeMartins", version, "— robô de cripto")
	fmt.Println("  Painel:", url)
	fmt.Println("  Dados em:", dir)
	fmt.Println("  Deixe esta janela ABERTA. Fechar = robô para.")
	fmt.Println("==============================================")

	bot := NewBot(dir)
	go bot.Run()

	srv := &http.Server{Handler: newServer(bot), ReadHeaderTimeout: 10 * time.Second}
	if os.Getenv("TRADEMARTINS_NO_BROWSER") == "" {
		go func() { time.Sleep(400 * time.Millisecond); openBrowser(url) }()
	}
	log.Fatal(srv.Serve(ln))
}
