// Package baixamartins guarda as telas do app (pasta web/).
// Elas vão dentro do programa do PC (para abrir pelo navegador) e dentro do APK.
package baixamartins

import "embed"

//go:embed web
var Web embed.FS
