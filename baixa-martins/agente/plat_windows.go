//go:build windows

package main

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"syscall"
	"unsafe"

	"github.com/getlantern/systray"
	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

var (
	user32  = windows.NewLazySystemDLL("user32.dll")
	shell32 = windows.NewLazySystemDLL("shell32.dll")

	pMessageBoxW   = user32.NewProc("MessageBoxW")
	pShellExecuteW = shell32.NewProc("ShellExecuteW")
)

func platformInit() {}

func osName() string { return "windows" }

func hiddenCmd(name string, args ...string) *exec.Cmd {
	c := exec.Command(name, args...)
	c.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	return c
}

func hiddenCmdCtx(ctx context.Context, name string, args ...string) *exec.Cmd {
	c := exec.CommandContext(ctx, name, args...)
	c.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000}
	c.Cancel = func() error { killTree(c); return nil }
	return c
}

// Fecha o programa e tudo que ele abriu (o yt-dlp chama o ffmpeg).
func killTree(c *exec.Cmd) {
	if c.Process == nil {
		return
	}
	_ = hiddenCmd("taskkill", "/T", "/F", "/PID", strconv.Itoa(c.Process.Pid)).Run()
	_ = c.Process.Kill()
}

func knownFolder(id *windows.KNOWNFOLDERID, fallback string) string {
	p, err := windows.KnownFolderPath(id, 0)
	if err != nil || p == "" {
		return fallback
	}
	return p
}

func musicDir(home string) string  { return knownFolder(windows.FOLDERID_Music, home+`\Music`) }
func videosDir(home string) string { return knownFolder(windows.FOLDERID_Videos, home+`\Videos`) }

func shellOpen(file, params string) error {
	f, _ := windows.UTF16PtrFromString(file)
	verb, _ := windows.UTF16PtrFromString("open")
	var p *uint16
	if params != "" {
		p, _ = windows.UTF16PtrFromString(params)
	}
	r, _, _ := pShellExecuteW.Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(f)), uintptr(unsafe.Pointer(p)), 0, 1)
	if r <= 32 {
		return fmt.Errorf("o Windows não conseguiu abrir \"%s\"", file)
	}
	return nil
}

func openURL(u string) error  { return shellOpen(u, "") }
func openPath(p string) error { return shellOpen(p, "") }

// Abre o Explorador com o arquivo selecionado.
func revealFile(p string) error {
	if _, err := os.Stat(p); err != nil {
		return fmt.Errorf("o arquivo não está mais lá")
	}
	return shellOpen("explorer.exe", `/select,"`+p+`"`)
}

func notify(title, text string) {
	t, _ := windows.UTF16PtrFromString(title)
	m, _ := windows.UTF16PtrFromString(text)
	// MB_OK | MB_ICONINFORMATION | MB_SETFOREGROUND | MB_TOPMOST
	pMessageBoxW.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x40|0x10000|0x40000)
}

const runKey = `Software\Microsoft\Windows\CurrentVersion\Run`
const runName = "BaixaMartins"

func setAutostart(on bool) {
	k, err := registry.OpenKey(registry.CURRENT_USER, runKey, registry.SET_VALUE)
	if err != nil {
		return
	}
	defer k.Close()
	if on {
		exe, err := os.Executable()
		if err == nil {
			_ = k.SetStringValue(runName, `"`+exe+`" --fundo`)
		}
	} else {
		_ = k.DeleteValue(runName)
	}
}

func autostartOn() bool {
	k, err := registry.OpenKey(registry.CURRENT_USER, runKey, registry.QUERY_VALUE)
	if err != nil {
		return false
	}
	defer k.Close()
	_, _, err = k.GetStringValue(runName)
	return err == nil
}

var trayReady bool

func updateTrayStatus() {
	if !trayReady {
		return
	}
	systray.SetTooltip("Baixa Martins — ativo")
}

func runTray() {
	systray.Run(func() {
		systray.SetIcon(trayIcon())
		systray.SetTitle("Baixa Martins")
		trayReady = true
		updateTrayStatus()

		mOpen := systray.AddMenuItem("Abrir o Baixa Martins", "")
		mMusic := systray.AddMenuItem("Abrir pasta das músicas", "")
		mVideo := systray.AddMenuItem("Abrir pasta dos vídeos", "")
		systray.AddSeparator()
		mPhone := systray.AddMenuItem("Usar no celular (código e endereço)", "")
		mAuto := systray.AddMenuItemCheckbox("Iniciar junto com o Windows", "", autostartOn())
		mCfg := systray.AddMenuItem("Abrir pasta de configurações", "")
		systray.AddSeparator()
		mQuit := systray.AddMenuItem("Sair (para downloads e transcrições)", "")

		go func() {
			for {
				select {
				case <-mOpen.ClickedCh:
					_ = openURL(appURL())
				case <-mMusic.ClickedCh:
					d := getSettings().AudioDir
					_ = os.MkdirAll(d, 0o755)
					_ = openPath(d)
				case <-mVideo.ClickedCh:
					d := getSettings().VideoDir
					_ = os.MkdirAll(d, 0o755)
					_ = openPath(d)
				case <-mPhone.ClickedCh:
					_ = openURL(appURL() + "#celular")
				case <-mAuto.ClickedCh:
					if mAuto.Checked() {
						setAutostart(false)
						mAuto.Uncheck()
					} else {
						setAutostart(true)
						mAuto.Check()
					}
				case <-mCfg.ClickedCh:
					_ = openPath(configDir())
				case <-mQuit.ClickedCh:
					for _, j := range jobSnapshot() {
						if j.State == "ativo" || j.State == "fila" {
							cancelJob(j.ID)
						}
					}
					systray.Quit()
					return
				}
			}
		}()
	}, func() {
		os.Exit(0)
	})
}

// Fecha o processo que está usando a porta (a versão antiga do Baixa Martins).
func killPortOwner(port int) error {
	ps := fmt.Sprintf("Get-NetTCPConnection -LocalPort %d -State Listen | ForEach-Object { if ($_.OwningProcess -ne %d) { Stop-Process -Id $_.OwningProcess -Force } }", port, os.Getpid())
	return hiddenCmd("powershell", "-NoProfile", "-NonInteractive", "-Command", ps).Run()
}
