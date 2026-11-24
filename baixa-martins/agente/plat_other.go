//go:build !windows

// Versão para Linux/Mac (testes). O programa de verdade é o do Windows (plat_windows.go).
package main

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"syscall"
)

func platformInit() {}

func hiddenCmdCtx(ctx context.Context, name string, args ...string) *exec.Cmd {
	c := exec.CommandContext(ctx, name, args...)
	c.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
	c.Cancel = func() error { killTree(c); return nil }
	return c
}

// Mata o programa e os filhos (o yt-dlp chama o ffmpeg).
func killTree(c *exec.Cmd) {
	if c.Process != nil {
		_ = syscall.Kill(-c.Process.Pid, syscall.SIGKILL)
	}
}

func notify(title, text string)    { fmt.Println("==", title, "==\n"+text) }
func setAutostart(on bool)         {}
func autostartOn() bool            { return false }
func updateTrayStatus()            {}
func openURL(u string) error       { fmt.Println("abrir", u); return nil }
func openPath(p string) error      { fmt.Println("abrir pasta", p); return nil }
func revealFile(p string) error    { return openPath(filepath.Dir(p)) }
func killPortOwner(port int) error { return fmt.Errorf("não suportado") }
func musicDir(home string) string  { return filepath.Join(home, "Music") }
func videosDir(home string) string { return filepath.Join(home, "Videos") }
func osName() string {
	if isAndroid() {
		return "android"
	}
	return runtime.GOOS
}
func runTray() {
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt, syscall.SIGTERM)
	<-ch
}
