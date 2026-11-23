//go:build windows

package main

import (
	"errors"
	"fmt"
	"image"
	"log"
	"os"
	"os/exec"
	"sort"
	"strings"
	"sync"
	"syscall"
	"unicode/utf16"
	"unsafe"

	"github.com/getlantern/systray"
	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

var (
	user32  = windows.NewLazySystemDLL("user32.dll")
	gdi32   = windows.NewLazySystemDLL("gdi32.dll")
	shell32 = windows.NewLazySystemDLL("shell32.dll")

	pSetProcessDpiAwarenessContext = user32.NewProc("SetProcessDpiAwarenessContext")
	pSetProcessDPIAware            = user32.NewProc("SetProcessDPIAware")
	pEnumDisplayMonitors           = user32.NewProc("EnumDisplayMonitors")
	pGetMonitorInfoW               = user32.NewProc("GetMonitorInfoW")
	pGetDC                         = user32.NewProc("GetDC")
	pReleaseDC                     = user32.NewProc("ReleaseDC")
	pGetCursorInfo                 = user32.NewProc("GetCursorInfo")
	pGetIconInfo                   = user32.NewProc("GetIconInfo")
	pDrawIconEx                    = user32.NewProc("DrawIconEx")
	pSetCursorPos                  = user32.NewProc("SetCursorPos")
	pSendInput                     = user32.NewProc("SendInput")
	pLockWorkStation               = user32.NewProc("LockWorkStation")
	pPostMessageW                  = user32.NewProc("PostMessageW")
	pMessageBoxW                   = user32.NewProc("MessageBoxW")
	pOpenInputDesktop              = user32.NewProc("OpenInputDesktop")
	pSetThreadDesktop              = user32.NewProc("SetThreadDesktop")
	pCloseDesktop                  = user32.NewProc("CloseDesktop")

	pCreateCompatibleDC = gdi32.NewProc("CreateCompatibleDC")
	pCreateDIBSection   = gdi32.NewProc("CreateDIBSection")
	pSelectObject       = gdi32.NewProc("SelectObject")
	pDeleteObject       = gdi32.NewProc("DeleteObject")
	pDeleteDC           = gdi32.NewProc("DeleteDC")
	pStretchBlt         = gdi32.NewProc("StretchBlt")
	pSetStretchBltMode  = gdi32.NewProc("SetStretchBltMode")
	pSetBrushOrgEx      = gdi32.NewProc("SetBrushOrgEx")
	pGdiFlush           = gdi32.NewProc("GdiFlush")

	pShellExecuteW = shell32.NewProc("ShellExecuteW")
)

func platformInit() {
	// Coordenadas reais de pixel, mesmo com zoom de 125%/150% no Windows
	if pSetProcessDpiAwarenessContext.Find() == nil {
		r, _, _ := pSetProcessDpiAwarenessContext.Call(^uintptr(3)) // -4 = PER_MONITOR_AWARE_V2
		if r != 0 {
			return
		}
	}
	pSetProcessDPIAware.Call()
}

// ---------------- Monitores ----------------

type rect struct{ L, T, R, B int32 }
type monitorInfo struct {
	Size  uint32
	Mon   rect
	Work  rect
	Flags uint32
}

var (
	monMu   sync.Mutex
	monList []Monitor
	monCB   = windows.NewCallback(func(h, hdc, r, lp uintptr) uintptr {
		var mi monitorInfo
		mi.Size = uint32(unsafe.Sizeof(mi))
		pGetMonitorInfoW.Call(h, uintptr(unsafe.Pointer(&mi)))
		monList = append(monList, Monitor{
			X: int(mi.Mon.L), Y: int(mi.Mon.T),
			W: int(mi.Mon.R - mi.Mon.L), H: int(mi.Mon.B - mi.Mon.T),
			Primary: mi.Flags&1 != 0,
		})
		return 1
	})
)

func monitors() []Monitor {
	monMu.Lock()
	defer monMu.Unlock()
	monList = nil
	pEnumDisplayMonitors.Call(0, 0, monCB, 0)
	out := append([]Monitor(nil), monList...)
	// principal primeiro, depois da esquerda para a direita
	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Primary != out[j].Primary {
			return out[i].Primary
		}
		return out[i].X < out[j].X
	})
	return out
}

// ---------------- Captura de tela (GDI) ----------------

type bitmapInfoHeader struct {
	Size          uint32
	Width         int32
	Height        int32
	Planes        uint16
	BitCount      uint16
	Compression   uint32
	SizeImage     uint32
	XPelsPerMeter int32
	YPelsPerMeter int32
	ClrUsed       uint32
	ClrImportant  uint32
}

type cursorInfo struct {
	Size   uint32
	Flags  uint32
	Cursor uintptr
	X, Y   int32
}

type iconInfo struct {
	FIcon    int32
	XHotspot uint32
	YHotspot uint32
	Mask     uintptr
	Color    uintptr
}

type gdiCap struct {
	mem, bmp, old uintptr
	bits          unsafe.Pointer
	w, h          int
	img           *image.RGBA
}

func newCapturer() *gdiCap { return &gdiCap{} }

func (g *gdiCap) free() {
	if g.mem != 0 {
		pSelectObject.Call(g.mem, g.old)
		pDeleteObject.Call(g.bmp)
		pDeleteDC.Call(g.mem)
	}
	g.mem, g.bmp, g.old, g.bits = 0, 0, 0, nil
}

func (g *gdiCap) Close() { g.free() }

func (g *gdiCap) ensure(w, h int) error {
	if g.mem != 0 && g.w == w && g.h == h {
		return nil
	}
	g.free()
	screen, _, _ := pGetDC.Call(0)
	if screen == 0 {
		return errors.New("GetDC falhou")
	}
	defer pReleaseDC.Call(0, screen)
	mem, _, _ := pCreateCompatibleDC.Call(screen)
	if mem == 0 {
		return errors.New("CreateCompatibleDC falhou")
	}
	bi := bitmapInfoHeader{Size: 40, Width: int32(w), Height: -int32(h), Planes: 1, BitCount: 32}
	var bits unsafe.Pointer
	// BITMAPINFO = header + 1 cor (não usada em 32 bits)
	var biFull struct {
		H bitmapInfoHeader
		C [4]byte
	}
	biFull.H = bi
	bmp, _, _ := pCreateDIBSection.Call(screen, uintptr(unsafe.Pointer(&biFull)), 0, uintptr(unsafe.Pointer(&bits)), 0, 0)
	if bmp == 0 || bits == nil {
		pDeleteDC.Call(mem)
		return errors.New("CreateDIBSection falhou")
	}
	old, _, _ := pSelectObject.Call(mem, bmp)
	g.mem, g.bmp, g.old, g.bits, g.w, g.h = mem, bmp, old, bits, w, h
	g.img = image.NewRGBA(image.Rect(0, 0, w, h))
	return nil
}

// Acompanha a área de trabalho ativa (ex.: janela de permissão do Windows),
// quando o Windows deixa.
func followInputDesktop() {
	d, _, _ := pOpenInputDesktop.Call(0, 0, 0x10000000) // GENERIC_ALL
	if d == 0 {
		return
	}
	if r, _, _ := pSetThreadDesktop.Call(d); r == 0 {
		pCloseDesktop.Call(d)
	}
}

func (g *gdiCap) Grab(m Monitor, w int) (*image.RGBA, error) {
	if w < 16 {
		w = 16
	}
	h := m.H * w / m.W
	if err := g.ensure(w, h); err != nil {
		return nil, err
	}
	screen, _, _ := pGetDC.Call(0)
	if screen == 0 {
		followInputDesktop()
		return nil, errors.New("sem acesso à tela")
	}
	defer pReleaseDC.Call(0, screen)
	pSetStretchBltMode.Call(g.mem, 4) // HALFTONE: reduz sem serrilhar
	pSetBrushOrgEx.Call(g.mem, 0, 0, 0)
	r, _, _ := pStretchBlt.Call(g.mem, 0, 0, uintptr(w), uintptr(h),
		screen, uintptr(int32(m.X)), uintptr(int32(m.Y)), uintptr(m.W), uintptr(m.H), 0x00CC0020) // SRCCOPY
	if r == 0 {
		followInputDesktop()
		return nil, errors.New("StretchBlt falhou")
	}
	// desenha o cursor do mouse na imagem
	ci := cursorInfo{Size: uint32(unsafe.Sizeof(cursorInfo{}))}
	if ok, _, _ := pGetCursorInfo.Call(uintptr(unsafe.Pointer(&ci))); ok != 0 && ci.Flags&1 != 0 && ci.Cursor != 0 {
		var ii iconInfo
		hx, hy := int32(0), int32(0)
		if ok, _, _ := pGetIconInfo.Call(ci.Cursor, uintptr(unsafe.Pointer(&ii))); ok != 0 {
			hx, hy = int32(ii.XHotspot), int32(ii.YHotspot)
			if ii.Mask != 0 {
				pDeleteObject.Call(ii.Mask)
			}
			if ii.Color != 0 {
				pDeleteObject.Call(ii.Color)
			}
		}
		cx := int32((int(ci.X)-m.X)*w/m.W) - hx
		cy := int32((int(ci.Y)-m.Y)*h/m.H) - hy
		pDrawIconEx.Call(g.mem, uintptr(cx), uintptr(cy), ci.Cursor, 0, 0, 0, 0, 3) // DI_NORMAL
	}
	pGdiFlush.Call()
	src := unsafe.Slice((*byte)(g.bits), w*h*4)
	dst := g.img.Pix
	for i := 0; i < len(src); i += 4 {
		dst[i] = src[i+2]
		dst[i+1] = src[i+1]
		dst[i+2] = src[i]
		dst[i+3] = 255
	}
	return g.img, nil
}

// ---------------- Mouse e teclado (SendInput) ----------------

type mouseInput struct {
	Dx, Dy    int32
	MouseData uint32
	Flags     uint32
	Time      uint32
	Extra     uintptr
}

type keybdInput struct {
	Vk    uint16
	Scan  uint16
	Flags uint32
	Time  uint32
	Extra uintptr
}

type input struct {
	Type uint32
	_    uint32
	U    [32]byte
}

func mouseIn(mi mouseInput) input {
	var in input
	in.Type = 0
	*(*mouseInput)(unsafe.Pointer(&in.U[0])) = mi
	return in
}

func keyIn(ki keybdInput) input {
	var in input
	in.Type = 1
	*(*keybdInput)(unsafe.Pointer(&in.U[0])) = ki
	return in
}

func sendInputs(ins ...input) {
	if len(ins) == 0 {
		return
	}
	pSendInput.Call(uintptr(len(ins)), uintptr(unsafe.Pointer(&ins[0])), unsafe.Sizeof(ins[0]))
}

func mouseMoveRel(dx, dy int) {
	sendInputs(mouseIn(mouseInput{Dx: int32(dx), Dy: int32(dy), Flags: 0x0001}))
}

func mouseMoveAbs(x, y int) {
	pSetCursorPos.Call(uintptr(int32(x)), uintptr(int32(y)))
}

func mouseButton(b string, down bool) {
	var f uint32
	switch b {
	case "right":
		f = 0x0008
	case "middle":
		f = 0x0020
	default:
		f = 0x0002
	}
	if !down {
		f <<= 1 // UP = DOWN * 2
	}
	sendInputs(mouseIn(mouseInput{Flags: f}))
}

func mouseWheel(dy, dx int) {
	var ins []input
	if dy != 0 {
		ins = append(ins, mouseIn(mouseInput{MouseData: uint32(int32(dy)), Flags: 0x0800}))
	}
	if dx != 0 {
		ins = append(ins, mouseIn(mouseInput{MouseData: uint32(int32(dx)), Flags: 0x1000}))
	}
	sendInputs(ins...)
}

func typeText(s string) {
	var ins []input
	for _, r := range s {
		if r == '\n' || r == '\r' {
			ins = append(ins, keyIn(keybdInput{Vk: 0x0D}), keyIn(keybdInput{Vk: 0x0D, Flags: 2}))
			continue
		}
		for _, u := range utf16.Encode([]rune{r}) {
			ins = append(ins,
				keyIn(keybdInput{Scan: u, Flags: 0x0004}),     // KEYEVENTF_UNICODE
				keyIn(keybdInput{Scan: u, Flags: 0x0004 | 2})) // + KEYUP
		}
	}
	sendInputs(ins...)
}

func vkInput(v vk, up bool) input {
	var f uint32
	if v.ext {
		f |= 1
	}
	if up {
		f |= 2
	}
	return keyIn(keybdInput{Vk: v.code, Flags: f})
}

func keyPress(k string, mods []string) error {
	key, ok := keyVK(k)
	if !ok {
		return fmt.Errorf("tecla desconhecida: %s", k)
	}
	var ms []vk
	for _, m := range mods {
		if v, ok := keyTable[m]; ok && (m == "ctrl" || m == "alt" || m == "shift" || m == "win") {
			ms = append(ms, v)
		}
	}
	var ins []input
	for _, m := range ms {
		ins = append(ins, vkInput(m, false))
	}
	ins = append(ins, vkInput(key, false), vkInput(key, true))
	for i := len(ms) - 1; i >= 0; i-- {
		ins = append(ins, vkInput(ms[i], true))
	}
	sendInputs(ins...)
	return nil
}

// ---------------- Ações ----------------

func hiddenCmd(name string, args ...string) *exec.Cmd {
	c := exec.Command(name, args...)
	c.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
	return c
}

func action(a string) error {
	switch a {
	case "bloquear":
		pLockWorkStation.Call()
	case "tela_off":
		// HWND_BROADCAST, WM_SYSCOMMAND, SC_MONITORPOWER, 2 = desligar
		pPostMessageW.Call(0xFFFF, 0x0112, 0xF170, 2)
	case "desligar":
		log.Printf("desligando pelo celular")
		return hiddenCmd("shutdown", "/s", "/t", "0").Start()
	case "reiniciar":
		log.Printf("reiniciando pelo celular")
		return hiddenCmd("shutdown", "/r", "/t", "0").Start()
	case "volup", "voldown", "mute", "play", "next", "prev", "stop":
		return keyPress(a, nil)
	default:
		return fmt.Errorf("ação desconhecida: %s", a)
	}
	return nil
}

func shellOpen(file, params string) error {
	f, _ := windows.UTF16PtrFromString(file)
	verb, _ := windows.UTF16PtrFromString("open")
	var p *uint16
	if params != "" {
		p, _ = windows.UTF16PtrFromString(params)
	}
	r, _, _ := pShellExecuteW.Call(0, uintptr(unsafe.Pointer(verb)), uintptr(unsafe.Pointer(f)), uintptr(unsafe.Pointer(p)), 0, 1)
	if r <= 32 {
		return fmt.Errorf("o Windows não encontrou \"%s\"", file)
	}
	return nil
}

// Abre site, programa ou arquivo. Aceita "programa argumentos" e caminhos com espaço.
func openCmd(c string) error {
	c = strings.TrimSpace(c)
	if c == "" {
		return errors.New("nada para abrir")
	}
	if strings.HasPrefix(c, "\"") {
		if end := strings.Index(c[1:], "\""); end >= 0 {
			return shellOpen(c[1:end+1], strings.TrimSpace(c[end+2:]))
		}
	}
	if _, err := os.Stat(c); err == nil || strings.Contains(c, "://") || !strings.Contains(c, " ") {
		return shellOpen(c, "")
	}
	i := strings.Index(c, " ")
	if err := shellOpen(c[:i], strings.TrimSpace(c[i+1:])); err == nil {
		return nil
	}
	return shellOpen(c, "")
}

// ---------------- Mensagens, iniciar com o Windows, ícone ----------------

func notify(title, text string) {
	t, _ := windows.UTF16PtrFromString(title)
	m, _ := windows.UTF16PtrFromString(text)
	// MB_OK | MB_ICONINFORMATION | MB_SETFOREGROUND | MB_TOPMOST
	pMessageBoxW.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x40|0x10000|0x40000)
}

const runKey = `Software\Microsoft\Windows\CurrentVersion\Run`
const runName = "ControleMartins"

func setAutostart(on bool) {
	k, err := registry.OpenKey(registry.CURRENT_USER, runKey, registry.SET_VALUE)
	if err != nil {
		return
	}
	defer k.Close()
	if on {
		exe, err := os.Executable()
		if err == nil {
			_ = k.SetStringValue(runName, `"`+exe+`"`)
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
	n := deviceCount()
	tip := "Controle Martins — ativo"
	if n == 1 {
		tip += " · 1 celular pareado"
	} else if n > 1 {
		tip += fmt.Sprintf(" · %d celulares pareados", n)
	}
	systray.SetTooltip(tip)
}

func runTray() {
	systray.Run(func() {
		systray.SetIcon(trayIcon())
		systray.SetTitle("Controle Martins")
		trayReady = true
		updateTrayStatus()

		mShow := systray.AddMenuItem("Ver código de pareamento e endereços", "")
		mNew := systray.AddMenuItem("Gerar novo código", "O código antigo deixa de funcionar para novos celulares")
		mClear := systray.AddMenuItem("Desconectar todos os celulares", "Todos vão precisar parear de novo")
		systray.AddSeparator()
		mAuto := systray.AddMenuItemCheckbox("Iniciar junto com o Windows", "", autostartOn())
		mLog := systray.AddMenuItem("Abrir pasta de configurações", "")
		systray.AddSeparator()
		mQuit := systray.AddMenuItem("Sair (o celular para de controlar)", "")

		go func() {
			for {
				select {
				case <-mShow.ClickedCh:
					go notify("Controle Martins", pairingText())
				case <-mNew.ClickedCh:
					regenerateCode()
					go notify("Controle Martins", "Código novo criado.\n\n"+pairingText())
				case <-mClear.ClickedCh:
					clearDevices()
					updateTrayStatus()
					go notify("Controle Martins", "Pronto: nenhum celular tem mais acesso.\nPara usar de novo, pareie com o código.\n\nDica: use também \"Gerar novo código\".")
				case <-mAuto.ClickedCh:
					if mAuto.Checked() {
						setAutostart(false)
						mAuto.Uncheck()
					} else {
						setAutostart(true)
						mAuto.Check()
					}
				case <-mLog.ClickedCh:
					_ = shellOpen(configDir(), "")
				case <-mQuit.ClickedCh:
					systray.Quit()
					return
				}
			}
		}()
	}, func() {
		os.Exit(0)
	})
}
