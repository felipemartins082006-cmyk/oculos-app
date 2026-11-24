//go:build windows

package main

// Coisas que só existem no Windows: abrir junto com o Windows, impedir o PC de dormir,
// minimizar a janela preta e fechar a versão antiga do programa.

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"runtime"
	"syscall"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

var (
	kernel32                 = windows.NewLazySystemDLL("kernel32.dll")
	user32                   = windows.NewLazySystemDLL("user32.dll")
	pSetThreadExecutionState = kernel32.NewProc("SetThreadExecutionState")
	pGetConsoleWindow        = kernel32.NewProc("GetConsoleWindow")
	pShowWindow              = user32.NewProc("ShowWindow")
)

const runKey = `Software\Microsoft\Windows\CurrentVersion\Run`
const runName = "TradeMartins"

// setAutostart liga/desliga o início junto com o Windows, sempre apontando para ESTE .exe
// (assim, ao abrir uma versão nova, o início automático passa a usar a nova).
func setAutostart(on bool) error {
	k, err := registry.OpenKey(registry.CURRENT_USER, runKey, registry.SET_VALUE|registry.QUERY_VALUE)
	if err != nil {
		return err
	}
	defer k.Close()
	if !on {
		if _, _, err := k.GetStringValue(runName); err == nil {
			return k.DeleteValue(runName)
		}
		return nil
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	want := `"` + exe + `" --auto`
	if cur, _, err := k.GetStringValue(runName); err == nil && cur == want {
		return nil
	}
	return k.SetStringValue(runName, want)
}

const (
	esContinuous     = 0x80000000
	esSystemRequired = 0x00000001
)

var awakeCh = make(chan bool, 4)

func init() {
	// SetThreadExecutionState vale para a thread que chamou: uma goroutine presa a uma thread cuida disso.
	go func() {
		runtime.LockOSThread()
		for on := range awakeCh {
			flags := uintptr(esContinuous)
			if on {
				flags |= esSystemRequired
			}
			pSetThreadExecutionState.Call(flags)
		}
	}()
}

// keepAwake impede (ou volta a permitir) que o Windows suspenda o PC. A tela pode apagar normalmente.
func keepAwake(on bool) {
	select {
	case awakeCh <- on:
	default:
	}
}

func minimizeConsole() {
	if h, _, _ := pGetConsoleWindow.Call(); h != 0 {
		pShowWindow.Call(h, 7) // SW_SHOWMINNOACTIVE
	}
}

// killPortOwner fecha à força o processo que está usando a porta (versões antigas sem /api/quit).
func killPortOwner(port int) error {
	ps := fmt.Sprintf("Get-NetTCPConnection -LocalPort %d -State Listen | ForEach-Object { if ($_.OwningProcess -ne %d) { Stop-Process -Id $_.OwningProcess -Force } }", port, os.Getpid())
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	out, err := cmd.CombinedOutput()
	if err != nil {
		log.Printf("não consegui fechar a versão antiga: %v %s", err, out)
	}
	return err
}

const platformSupportsAutostart = true
