# Grava Tela

App Android simples para **gravar a tela em vídeo** e **tirar prints**.

- **Gravar a tela**: grava tudo o que aparece no celular em vídeo (MP4), com ou sem o som do microfone.
- **Tirar prints**: liga o modo de prints. Cada toque no botão flutuante (ou em **Print** na notificação)
  salva uma foto da tela.
- Botão flutuante por cima dos outros apps: 📷 print, 🔴 gravar, ✕ fechar. Durante a gravação ele mostra o
  tempo e o botão ⏹ parar. Dá para arrastar para qualquer canto.
- Os mesmos botões ficam na notificação (Print, Gravar, Fechar / Parar e salvar).
- Contagem de 3 segundos antes de começar a gravar (dá para desligar).
- A tela do app lista os últimos vídeos e prints: toque para abrir, compartilhar ou apagar.

Os arquivos ficam na **Galeria**:

| O quê | Pasta |
|---|---|
| Vídeos | `Filmes/GravaTela` (Movies/GravaTela) |
| Prints | `Imagens/GravaTela` (Pictures/GravaTela) |

Funciona no Android 10 ou mais novo.

## Instalar

1. Passe o **`GravaTela.apk`** para o celular, abra e toque em **Instalar**
   (na primeira vez o Android pede para permitir "instalar apps desconhecidos").
2. Abra o **Grava Tela**.

## Usar

1. Toque em **Gravar a tela** ou em **Tirar prints**.
2. Na primeira vez o app pede:
   - **Sobrepor a outros apps** — para o botão flutuante. Ative para o Grava Tela e volte.
     (Se preferir sem o botão, toque em "Usar sem o botão": aí use os botões da notificação.)
   - **Notificações** — para os botões Print/Gravar/Parar na barra de notificações.
   - **Microfone** — só se a opção "Gravar com som do microfone" estiver ligada.
3. O Android mostra o aviso **"Começar a gravar ou transmitir?"**. Toque em **Começar**
   (em alguns celulares: **Iniciar agora**). Esse aviso aparece toda vez que você começa; é regra do Android.
4. O app vai para trás e aparece o botão flutuante.
   - Gravando: toque em ⏹ para parar. O vídeo é salvo e aparece a notificação **Vídeo salvo**.
   - Modo de prints: toque em 📷 quantas vezes quiser. Quando terminar, toque em ✕.

## Bom saber

- O botão flutuante **aparece no vídeo** (no print não: ele some na hora da foto). Arraste para um canto
  ou desligue a opção "Botão flutuante" e pare pela notificação.
- O som gravado é o do **microfone** (sua voz e o que sai do alto-falante). O som interno dos apps
  ainda não é gravado.
- Durante a gravação não dá para tirar print; pare a gravação antes.
- Apps de banco e alguns apps de vídeo (Netflix etc.) bloqueiam a gravação: nessas telas sai tudo preto.
  Isso é proteção do próprio app.

## Compilar

Abra esta pasta (`grava-martins`) no Android Studio e aperte **Run**, ou:

    ./gradlew assembleRelease

O APK fica em `app/build/outputs/apk/release/app-release.apk`.

A chave `app/grava-debug.keystore` é só para instalar versões novas por cima das antigas no seu celular.
Não use para publicar na Play Store.
