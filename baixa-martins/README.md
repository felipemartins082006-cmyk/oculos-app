# Baixa Martins

Baixe músicas, vídeos e playlists inteiras do YouTube, com letras e legendas feitas por
inteligência artificial que ouve o áudio.

- **Baixar**: cole um link (vídeo, música, Shorts, YouTube Music ou playlist) ou pesquise pelo nome.
  Escolha **Música** (MP3 até 320 kbps, M4A ou Opus) ou **Vídeo** (360p até 4K), marque um ou vários e toque em **Baixar**.
  A capa e os dados (título, artista) já vão dentro do arquivo.
- **Fila**: progresso, velocidade e tempo restante de cada download. Dá para cancelar e tentar de novo.
- **Letra com IA (músicas)**: a IA ouve a música e escreve a letra **sincronizada**:
  separa em estrofes, marca **Refrão**, **Pré-refrão**, **Ponte**, **Introdução** e partes **instrumentais**,
  e acende palavra por palavra enquanto toca (karaokê). Toque numa linha para pular para ela.
  Também salva um arquivo `.lrc` ao lado da música (Poweramp, MusicBee, foobar mostram sozinhos).
- **Legenda e transcrição com IA (vídeos)**: legenda automática por cima do vídeo e, num campo à parte,
  a transcrição completa em parágrafos com o tempo de cada um. Salva um `.srt` ao lado do vídeo (VLC mostra sozinho).
- **Aba Transcrições**: todas as transcrições e letras num lugar só, com busca por palavra em todas,
  copiar texto e exportar (`.srt`, `.vtt`, `.txt`, `.lrc`).
- **Corrigir**: se a IA errar uma palavra, toque no lápis e corrija. Dá para **ajustar a sincronia** (±0,1 s).
- **Segundo plano**: a música continua com a tela desligada ou em outro app, com capa, botões e a
  **linha da letra que está tocando** na notificação e na tela bloqueada. Funciona com fones bluetooth.
- **Mini tela (picture-in-picture)**: o vídeo (com legenda) ou a letra da música ficam numa janelinha
  por cima dos outros apps. No celular, o vídeo vai para a mini tela sozinho ao sair do app.
- **Do app do YouTube**: Compartilhar › **Baixa Martins** já abre o vídeo pronto para baixar.
- **Só áudio**: num vídeo, ouça só o som (gasta menos bateria e dados).

A IA (Whisper) roda **no seu computador**: nada é enviado para a internet.

> Use só para conteúdo que você tem o direito de baixar (seus vídeos, conteúdo livre, uso pessoal permitido).

São duas partes:

| Arquivo | Onde vai |
|---|---|
| `BaixaMartins.exe` | no computador (Windows 10/11). Faz os downloads e a IA. |
| `BaixaMartins.apk` | no celular Android (opcional). |

No próprio computador, o app abre no navegador (não precisa do celular).

---

## 1. Instalar no computador

1. Copie o **`BaixaMartins.exe`** para uma pasta fixa, por exemplo `C:\BaixaMartins\`.
2. Dê dois cliques nele.
   - **"O Windows protegeu o computador"**: clique em **Mais informações › Executar assim mesmo**
     (o programa não tem assinatura paga da Microsoft).
   - **Firewall**: marque **Redes privadas** e **Redes públicas** › **Permitir** (para o celular conseguir conectar).
3. O app abre no navegador (`localhost:47900`). Fica um ícone perto do relógio e ele abre sozinho com o Windows.
4. Na **primeira vez** ele baixa as ferramentas (uns 270 MB: yt-dlp, Deno e FFmpeg). Acompanhe em **Fila**.
   A IA (Whisper + modelo de 574 MB) é baixada na primeira vez que você pedir uma letra/legenda.

As músicas vão para **Músicas\BaixaMartins** e os vídeos para **Vídeos\BaixaMartins** (dá para trocar em **Ajustes**).
Playlists ganham uma pasta com o nome da playlist.

## 2. Usar no celular

1. No PC: **Ajustes › Usar no celular** mostra o **código de acesso** e o **endereço** do computador.
2. Passe o **`BaixaMartins.apk`** para o celular, abra e toque em **Instalar**.
3. Abra o **Baixa Martins**, digite o endereço e o código › **Conectar**.

Sem o APK (iPhone, tablet): abra no navegador `http://ENDEREÇO:47900` e digite o código.

**Fora de casa**: instale o **Tailscale** (grátis, tailscale.com) no PC e no celular com a mesma conta.
Aí aparece um endereço `100.x.x.x` que funciona de qualquer lugar. As músicas tocam direto do PC; para ter no
celular sem internet, use **⋯ › Salvar neste aparelho** (vai para `Download/BaixaMartins`).

## Letras e legendas: dicas

- **Qualidade da IA** (Ajustes): *Ótima* é a recomendada. *Rápida* serve para computadores fracos.
- Em músicas, escolher o **idioma** certo (em vez de "Automático") melhora o resultado.
- Música com muito instrumento alto pode sair com erros: corrija com o lápis.
- **Traduzir para o inglês**: a legenda sai em inglês (a IA só traduz para inglês).
- Uma música de 4 minutos leva de 30 s a 2 min, dependendo do computador.

## Problemas comuns

| Problema | O que fazer |
|---|---|
| Downloads começaram a falhar | **Ajustes › Ferramentas › Atualizar o yt-dlp** (o YouTube muda sempre; ele também se atualiza sozinho uma vez por dia). |
| "O YouTube bloqueou temporariamente" | Espere um pouco e tente de novo; baixe playlists grandes em partes. |
| Celular não conecta | PC ligado com o ícone do Baixa Martins? Liberou no Firewall? Tailscale ligado nos dois? |
| Letra muito errada | Escolha o idioma certo e a qualidade *Ótima* e toque em **Refazer**. |
| Música para quando desligo a tela | Permita as notificações do app e tire o Baixa Martins da economia de bateria (Configurações › Apps › Baixa Martins › Bateria › Sem restrições). |

---

## Para quem for mexer no código

```
baixa-martins/
  web/        telas do app (HTML/CSS/JS). Vão dentro do .exe e do APK
  agente/     programa do PC em Go (servidor HTTP na porta 47900, fila, yt-dlp, Whisper)
  android/    app Android (WebView + mini tela + serviço de mídia em segundo plano)
```

- **Programa do PC** (Go 1.26+):
  `GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-H windowsgui -s -w" -o BaixaMartins.exe ./agente`
  - Em Linux/Mac, `go run ./agente` usa o `yt-dlp`, `ffmpeg`, `deno` e `whisper-cli` instalados no sistema.
  - `go test ./agente/` testa a montagem da letra (estrofes, refrão, acentos) e as legendas.
  - Dados em `%APPDATA%\BaixaMartins`: `config.json`, `biblioteca.json`, `transcricoes\`, `capas\`,
    `ferramentas\`, `modelos\` e o registro `agente.log`.
- **APK**: `cd android && ./gradlew assembleRelease` gera `app/build/outputs/apk/release/app-release.apk`.
  A chave `baixa-debug.keystore` serve só para as versões novas instalarem por cima das antigas.

**Segurança:** no próprio PC o app abre sem código. Outros aparelhos precisam do código de acesso; cada um
recebe uma chave própria (o PC guarda só o hash). O código bloqueia por 10 minutos depois de 5 tentativas erradas.
Use pelo Tailscale e não abra a porta 47900 no roteador.
