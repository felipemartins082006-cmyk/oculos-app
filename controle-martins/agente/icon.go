package main

import (
	"bytes"
	"encoding/binary"
	"image"
	"image/color"
	"image/png"
	"math"
)

// Desenha o ícone (quadrado azul com um monitor branco) em qualquer tamanho.
func drawIcon(n int) *image.RGBA {
	img := image.NewRGBA(image.Rect(0, 0, n, n))
	s := float64(n) / 64
	blue := color.RGBA{0x2f, 0x6f, 0xe8, 255}
	white := color.RGBA{255, 255, 255, 255}
	inRound := func(x, y, x0, y0, x1, y1, r float64) bool {
		if x < x0 || x > x1 || y < y0 || y > y1 {
			return false
		}
		cx := math.Max(x0+r, math.Min(x, x1-r))
		cy := math.Max(y0+r, math.Min(y, y1-r))
		return math.Hypot(x-cx, y-cy) <= r
	}
	for py := 0; py < n; py++ {
		for px := 0; px < n; px++ {
			x, y := (float64(px)+0.5)/s, (float64(py)+0.5)/s
			if !inRound(x, y, 0, 0, 64, 64, 15) {
				continue
			}
			c := blue
			// moldura do monitor
			outer := inRound(x, y, 11, 14, 53, 43, 5)
			inner := inRound(x, y, 15, 18, 49, 39, 2)
			if outer && !inner {
				c = white
			}
			// pé
			if (x >= 30 && x <= 34 && y >= 43 && y <= 50) || (x >= 22 && x <= 42 && y >= 48 && y <= 52) {
				c = white
			}
			// "play" no meio
			if x >= 28 && x <= 38 && math.Abs(y-28.5) <= (38-x)/2 {
				c = white
			}
			img.SetRGBA(px, py, c)
		}
	}
	return img
}

// Arquivo .ico com imagens PNG dentro (Windows Vista em diante aceita).
func makeICO(sizes ...int) []byte {
	var pngs [][]byte
	for _, n := range sizes {
		var b bytes.Buffer
		_ = png.Encode(&b, drawIcon(n))
		pngs = append(pngs, b.Bytes())
	}
	var out bytes.Buffer
	binary.Write(&out, binary.LittleEndian, [3]uint16{0, 1, uint16(len(sizes))})
	off := 6 + 16*len(sizes)
	for i, n := range sizes {
		dim := byte(n)
		if n >= 256 {
			dim = 0
		}
		out.Write([]byte{dim, dim, 0, 0})
		binary.Write(&out, binary.LittleEndian, uint16(1))
		binary.Write(&out, binary.LittleEndian, uint16(32))
		binary.Write(&out, binary.LittleEndian, uint32(len(pngs[i])))
		binary.Write(&out, binary.LittleEndian, uint32(off))
		off += len(pngs[i])
	}
	for _, p := range pngs {
		out.Write(p)
	}
	return out.Bytes()
}

func trayIcon() []byte { return makeICO(16, 32, 48) }
