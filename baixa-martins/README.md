# Baixa Martins

App de celular (Android) para baixar músicas, vídeos e playlists inteiras do YouTube, com letras e
legendas feitas por inteligência artificial que ouve o áudio. **Tudo funciona só no celular**:
não precisa de computador nem de conta.

## Instalar

1. Passe o **`BaixaMartins.apk`** para o celular, abra e toque em **Instalar**
   (na primeira vez o Android pede para permitir "instalar apps desconhecidos").
2. Abra o **Baixa Martins**. Na primeira vez ele leva alguns segundos preparando.
3. Se ele pedir, permita as **notificações** (para baixar e tocar com a tela desligada).

Pronto. Precisa de um celular Android 8 ou mais novo, de 64 bits (quase todos desde 2017).

## Usar

- **Baixar**: cole um link (vídeo, música, Shorts, YouTube Music ou playlist) ou pesquise pelo nome.
  Escolha **Música** (MP3 até 320 kbps, M4A ou Opus) ou **Vídeo** (360p até 4K), marque um ou vários e toque em **Baixar**.
  - Mais fácil ainda: no app do **YouTube**, toque em **Compartilhar › Baixa Martins**.
  - As músicas vão para a pasta **Música/BaixaMartins** e os vídeos para **Filmes/BaixaMartins**
    (aparecem também nos outros players e na galeria). Playlists ganham uma pasta própria.
- **Fila**: progresso, velocidade e tempo restante. Continua baixando com a tela desligada ou com outro app aberto
  (o progresso aparece na notificação).
- **Letra com IA (músicas)**: a IA ouve a música e escreve a letra **sincronizada**: separa as estrofes,
  marca **Refrão**, **Pré-refrão**, **Ponte**, **Introdução** e partes **instrumentais**, e acende palavra por
  palavra enquanto toca (karaokê). Toque numa linha para pular para ela.
- **Legenda e transcrição com IA (vídeos)**: legenda automática por cima do vídeo e, num campo à parte,
  a transcrição completa em parágrafos com o tempo de cada um.
- **Aba Transcrições**: todas as letras e transcrições num lugar só, com busca por palavra em todas,
  copiar texto e exportar (`.srt`, `.vtt`, `.txt`, `.lrc`).
- **Corrigir**: se a IA errar uma palavra, toque no lápis e corrija. Dá para **ajustar a sincronia**.
- **Segundo plano**: a música continua com a tela desligada, com capa, botões e a **linha da letra que está
  tocando** na notificação e na tela bloqueada. Funciona com fones bluetooth.
- **Mini tela**: o vídeo (com legenda) ou a letra da música ficam numa janelinha por cima dos outros apps.
  O vídeo vai para a mini tela sozinho quando você sai do app.
- **Só áudio**: num vídeo, ouça só o som (gasta menos bateria).
- **⋯ › Compartilhar / Abrir com outro app**: mande a música ou o vídeo para quem quiser.

A IA (Whisper) roda **no próprio celular**: nada é enviado para a internet. Na primeira letra/legenda
o app baixa o modelo de IA (488 MB na qualidade *Boa*). Use Wi-Fi.

> Use só para conteúdo que você tem o direito de baixar.

## Dicas e problemas comuns

| Situação | O que fazer |
|---|---|
| A letra demora | Normal no celular: alguns minutos por música. Pode sair do app que continua. A qualidade *Rápida* (Ajustes) é bem mais rápida, mas erra mais. |
| Letra muito errada | Escolha o **idioma** certo (em vez de "Automático") e toque em **Refazer**. Corrija palavras com o lápis. |
| Downloads começaram a falhar | **Ajustes › Ferramentas › Atualizar o yt-dlp** (o YouTube muda sempre; ele também se atualiza sozinho uma vez por dia). |
| "O YouTube bloqueou temporariamente" | Espere um pouco e tente de novo; baixe playlists grandes em partes. |
| Para de baixar/tocar com a tela desligada | Permita as notificações e tire o app da economia de bateria: Configurações › Apps › Baixa Martins › Bateria › **Sem restrições**. |
| Celular sem espaço | Vídeos em 4K são grandes. Escolha 720p ou 1080p. |

---

## Versão para computador (opcional)

`BaixaMartins.exe` é o mesmo app para Windows: abre no navegador do PC (`localhost:47900`), usa o
processador do PC para a IA (mais rápido, com a qualidade *Ótima*) e também pode ser usado pelo celular
na mesma rede/Tailscale (Ajustes › Usar no celular). Não é necessário para o APK.

## Para quem for mexer no código

```
baixa-martins/
  web/        telas do app (HTML/CSS/JS), as mesmas no celular e no PC
  agente/     o "motor" em Go: servidor HTTP (porta 47900), fila, yt-dlp, Whisper, montagem da letra
  android/    app Android: WebView + motor rodando dentro do celular + mini tela + serviços em segundo plano
    native/   Whisper (whisper.cpp 1.8.5, licença MIT) para compilar para o celular
```

Como o APK funciona: ao abrir, ele liga o motor (`libbaixa.so`, o mesmo programa Go do PC compilado para
Android) em `127.0.0.1:47900` — só o próprio celular acessa. O motor usa o yt-dlp, o Python, o QuickJS e o
ffmpeg da biblioteca [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) e o Whisper
compilado em `libwhispercli.so` (celulares novos, ARMv8.2) / `libwhispercli_v8.so` (antigos).

- **Partes nativas** (Android NDK + Go 1.26+): `cd android && ./build-native.sh`
  (gera `app/src/main/jniLibs/arm64-v8a/*.so`).
- **APK**: `cd android && ./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
  A chave `baixa-debug.keystore` serve só para as versões novas instalarem por cima das antigas.
- **Windows**: `GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-H windowsgui -s -w" -o BaixaMartins.exe ./agente`
- **Testes**: `go test ./agente/` (montagem da letra: estrofes, refrão, acentos; legendas).
