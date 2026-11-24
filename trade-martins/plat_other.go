//go:build !windows

package main

import "errors"

// Fora do Windows (Linux/macOS, usado em testes e desenvolvimento) estas funções não fazem nada.

func setAutostart(on bool) error { return nil }
func keepAwake(on bool)          {}
func minimizeConsole()           {}
func killPortOwner(port int) error {
	return errors.New("fechar a versão antiga à força só funciona no Windows")
}

const platformSupportsAutostart = false
