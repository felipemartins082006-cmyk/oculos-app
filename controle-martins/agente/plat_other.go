//go:build !windows

// Versão de teste para Linux/Mac: tela de mentira e comandos só no log.
// O programa de verdade é o do Windows (plat_windows.go).
package main

import (
	"fmt"
	"image"
	"image/color"
	"log"
	"os"
	"os/signal"
	"time"
)

func platformInit() {}

func monitors() []Monitor {
	return []Monitor{{0, 0, 1920, 1080, true}, {1920, 0, 1280, 1024, false}}
}

type fakeCap struct{ mx, my int }

var fakeMouse struct{ x, y int }

func newCapturer() *fakeCap { return &fakeCap{} }
func (f *fakeCap) Close()   {}
func (f *fakeCap) Grab(m Monitor, w int) (*image.RGBA, error) {
	h := m.H * w / m.W
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	t := time.Now().UnixMilli() / 40
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			img.SetRGBA(x, y, color.RGBA{uint8(x * 255 / w), uint8(y * 255 / h), 120, 255})
		}
	}
	// quadrado andando
	bx := int(t % int64(w-60))
	for y := 40; y < 100; y++ {
		for x := bx; x < bx+60; x++ {
			img.SetRGBA(x, y, color.RGBA{255, 255, 255, 255})
		}
	}
	// "cursor"
	cx := (fakeMouse.x - m.X) * w / m.W
	cy := (fakeMouse.y - m.Y) * h / m.H
	for y := cy; y < cy+14 && y < h; y++ {
		for x := cx; x < cx+(y-cy)/2+1 && x < w; x++ {
			if x >= 0 && y >= 0 {
				img.SetRGBA(x, y, color.RGBA{0, 0, 0, 255})
			}
		}
	}
	return img, nil
}

func mouseMoveRel(dx, dy int) {
	fakeMouse.x += dx
	fakeMouse.y += dy
	log.Printf("mouse rel %d %d", dx, dy)
}
func mouseMoveAbs(x, y int)           { fakeMouse.x, fakeMouse.y = x, y; log.Printf("mouse abs %d %d", x, y) }
func mouseButton(b string, down bool) { log.Printf("botão %s down=%v", b, down) }
func mouseWheel(dy, dx int)           { log.Printf("rodinha %d %d", dy, dx) }
func typeText(s string)               { log.Printf("texto %q", s) }
func keyPress(k string, mods []string) error {
	if _, ok := keyVK(k); !ok {
		return fmt.Errorf("tecla desconhecida: %s", k)
	}
	log.Printf("tecla %s %v", k, mods)
	return nil
}
func action(a string) error     { log.Printf("ação %s", a); return nil }
func openCmd(c string) error    { log.Printf("abrir %q", c); return nil }
func notify(title, text string) { fmt.Println("==", title, "==\n"+text) }
func setAutostart(on bool)      {}
func updateTrayStatus()         {}
func runTray() {
	ch := make(chan os.Signal, 1)
	signal.Notify(ch, os.Interrupt)
	<-ch
}
func openURL(u string) error { fmt.Println("abrir", u); return nil }
