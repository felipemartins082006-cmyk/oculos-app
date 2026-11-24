package main

import "strings"

// Nome da tecla (como o app manda) → código virtual do Windows.
// ext = tecla "estendida" (setas, Home/End, Win, mídia…).
type vk struct {
	code uint16
	ext  bool
}

var keyTable = map[string]vk{
	"enter": {0x0D, false}, "backspace": {0x08, false}, "tab": {0x09, false}, "esc": {0x1B, false},
	"space": {0x20, false}, "pgup": {0x21, true}, "pgdn": {0x22, true}, "end": {0x23, true}, "home": {0x24, true},
	"left": {0x25, true}, "up": {0x26, true}, "right": {0x27, true}, "down": {0x28, true},
	"printscreen": {0x2C, true}, "insert": {0x2D, true}, "delete": {0x2E, true},
	"win": {0x5B, true}, "menu": {0x5D, true},
	"shift": {0x10, false}, "ctrl": {0x11, false}, "alt": {0x12, false},
	"capslock": {0x14, false},
	"mute":     {0xAD, true}, "voldown": {0xAE, true}, "volup": {0xAF, true},
	"next": {0xB0, true}, "prev": {0xB1, true}, "stop": {0xB2, true}, "play": {0xB3, true},
	"f1": {0x70, false}, "f2": {0x71, false}, "f3": {0x72, false}, "f4": {0x73, false},
	"f5": {0x74, false}, "f6": {0x75, false}, "f7": {0x76, false}, "f8": {0x77, false},
	"f9": {0x78, false}, "f10": {0x79, false}, "f11": {0x7A, false}, "f12": {0x7B, false},
}

func keyVK(k string) (vk, bool) {
	k = strings.ToLower(k)
	if v, ok := keyTable[k]; ok {
		return v, true
	}
	if len(k) == 1 {
		c := k[0]
		if c >= 'a' && c <= 'z' {
			return vk{uint16(c - 'a' + 'A'), false}, true
		}
		if c >= '0' && c <= '9' {
			return vk{uint16(c), false}, true
		}
	}
	return vk{}, false
}
