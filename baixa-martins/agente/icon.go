package main

import (
	"bytes"
	"encoding/binary"
	"image"
	"image/color"
	"image/png"
	"math"
)

// Desenha o ícone (quadrado roxo/rosa com uma nota musical e uma seta de baixar) em qualquer tamanho.
func drawIcon(n int) *image.RGBA {
	img := image.NewRGBA(image.Rect(0, 0, n, n))
	s := float64(n) / 64
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
			// degradê roxo -> rosa
			t := (x + y) / 128
			c := color.RGBA{uint8(124 + t*(255-124)), uint8(77 - t*(77-61)), uint8(255 - t*(255-127)), 255}
			// nota musical: cabeça (elipse) + haste + bandeira
			if math.Pow((x-24)/7.5, 2)+math.Pow((y-41)/5.8, 2) <= 1 {
				c = white
			}
			if x >= 28.5 && x <= 32 && y >= 14 && y <= 41 {
				c = white
			}
			if x >= 28.5 && x <= 40 && y >= 14 && y <= 14+(40-x)*0.55+3 && y <= 22 {
				c = white
			}
			// seta para baixo à direita
			if x >= 44 && x <= 48 && y >= 28 && y <= 42 {
				c = white
			}
			if y >= 40 && y <= 48 && math.Abs(x-46) <= (48-y)*1.0 {
				c = white
			}
			if x >= 38 && x <= 54 && y >= 50 && y <= 53 {
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

func iconPNG(n int) []byte {
	var b bytes.Buffer
	_ = png.Encode(&b, drawIcon(n))
	return b.Bytes()
}
