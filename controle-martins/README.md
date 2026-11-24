# Controle Martins

Controle o seu computador Windows pelo celular, de casa ou de qualquer lugar:

- **Tela**: vê a tela do PC ao vivo e mexe direto nela (toque = clique, segurar = botão direito,
  arrastar, dois dedos = rolar, pinça = zoom). O modo **Cursor** usa a imagem como touchpad.
- **Mouse**: o celular vira touchpad, com botões esquerdo, meio e direito e uma faixa para rolar.
- **Teclado**: digite com o teclado do celular. Tem Esc, Tab, Ctrl, Alt, Win, Shift, setas, F1–F12…
- **Atalhos**: volume, música (tocar/pausar/próxima), trocar e fechar janelas, copiar/colar,
  abrir programas e sites (a lista é editável), bloquear, desligar a tela, reiniciar e desligar.
- Vários computadores no mesmo app.

São duas partes:

| Arquivo | Onde vai |
|---|---|
| `ControleMartins.exe` | no computador (Windows 10/11) |
| `ControlePC.apk` | no celular Android |

O computador fica **ligado 24 horas**. Se ele for desligado, alguém precisa ligar de novo no botão.

---

## 1. Instalar no computador

1. Copie o **`ControleMartins.exe`** para uma pasta fixa, por exemplo `C:\ControleMartins\`.
   Não deixe em Downloads: ele abre de lá toda vez que o Windows iniciar.
2. Dê dois cliques nele.
   - Se aparecer **"O Windows protegeu o computador"**, clique em **Mais informações › Executar assim mesmo**.
     Esse aviso aparece porque o programa não tem assinatura paga da Microsoft.
   - Se o Windows perguntar sobre o **Firewall**, marque **Redes privadas** e **Redes públicas** e clique em **Permitir**.
     Sem isso, o celular não consegue conectar.
3. Aparece uma janela com o **código de pareamento** (ex.: `K7MQ-3XPA`) e os **endereços** do PC.
4. Também abre no navegador uma página (**painel**) com o código e o endereço.
5. Pronto. O programa fica num ícone azul perto do relógio e abre sozinho junto com o Windows.
   - **Para ver o código e o endereço de novo:** abra o `ControleMartins.exe` outra vez (ele abre o painel no
     navegador) ou digite `localhost:47800/painel` no navegador do PC.
   - O ícone azul pode ficar escondido na **setinha ^** ao lado do relógio. Clique nele para gerar outro
     código, desconectar celulares ou sair.

## 2. Para usar fora de casa: Tailscale (grátis)

O Tailscale liga o celular e o PC como se estivessem na mesma rede, de qualquer lugar (4G, outra
Wi-Fi), com criptografia e sem mexer no roteador.

1. No PC: baixe em **tailscale.com/download**, instale e entre com uma conta (Google, Microsoft…).
2. No celular: instale **Tailscale** pela Play Store e entre com **a mesma conta**. Deixe ligado.
3. No PC, clique no ícone do Controle Martins › **Ver código de pareamento**. Agora aparece um
   endereço que começa com **100.** É esse que você usa no celular.

Dica: no painel do Tailscale (login.tailscale.com) dá para desativar "Key expiry" do PC, assim ele nunca
pede para entrar de novo.

## 3. Instalar no celular

1. Passe o **`ControlePC.apk`** para o celular, abra e toque em **Instalar**
   (na primeira vez o Android pede para permitir "instalar apps desconhecidos").
2. Abra o **Controle PC** › **Adicionar computador**.
3. Digite o endereço **100.x.x.x** e o **código de pareamento** que apareceram no PC › **Parear**.

O PC avisa na tela sempre que um celular novo é pareado. Se não foi você, clique no ícone ›
**Desconectar todos os celulares** e depois em **Gerar novo código**.

Sem o APK (iPhone, tablet, outro PC): com o Tailscale ligado, abra no navegador
`http://100.x.x.x:47800` e pareie do mesmo jeito.

## 4. Deixar o PC pronto para ficar ligado 24h

- **Não deixar dormir**: Configurações › Sistema › Energia › "Quando conectado, colocar o dispositivo em
  suspensão após" › **Nunca**. A tela pode desligar sem problema: o controle continua funcionando.
- **Depois de reiniciar** (por você ou por atualização do Windows), o Controle Martins só volta quando o
  Windows entra na sua conta. Para isso acontecer sem ninguém digitar a senha, ative o **login
  automático**: aperte `Win + R`, digite `netplwiz`, desmarque "Os usuários devem digitar um nome de
  usuário e uma senha…" e confirme a senha. (No Windows 11, se essa opção não aparecer: Configurações ›
  Contas › Opções de entrada › desligue "Para maior segurança, permitir somente a entrada do Windows
  Hello…".)
- Se o PC ficar **bloqueado** (tela de senha), a imagem não aparece no celular até desbloquear.
  Isso é proteção do Windows.

## Problemas comuns

| Problema | O que fazer |
|---|---|
| Não aparece o ícone azul nem o painel | O Windows Defender pode ter bloqueado o programa: Segurança do Windows › Proteção contra vírus e ameaças › Histórico de proteção › Permitir. Depois abra o `.exe` de novo. |
| "Não achei o computador nesse endereço" | PC ligado? Ícone do Controle Martins perto do relógio? Tailscale ligado no celular **e** no PC? Liberou no Firewall? |
| Imagem travando ou atrasada no 4G | No app: ⚙ › Qualidade › **Economia**. |
| Mouse rápido ou lento demais | ⚙ › Velocidade do mouse. |
| Não clica em janelas de administrador | O Windows bloqueia controle remoto em janelas "Executar como administrador". Abra o `ControleMartins.exe` como administrador, se precisar. |
| "Este celular não está mais autorizado" | Alguém desconectou os celulares no PC. Pareie de novo com o código. |

---

## Para quem for mexer no código

```
controle-martins/
  web/        telas do app (HTML/CSS/JS). Vão no APK e também dentro do .exe
  agente/     programa do PC em Go (servidor HTTP + WebSocket na porta 47800)
  android/    app Android (WebView que mostra a pasta web/)
```

- **Programa do PC** (precisa do Go 1.26+):
  `GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-H windowsgui -s -w" -o ControleMartins.exe ./agente`
  - Em Linux/Mac, `go run ./agente` roda uma versão de teste com uma tela de mentira, para testar o app.
  - As configurações ficam em `%APPDATA%\ControleMartins\config.json` (código, celulares pareados) e o
    registro em `agente.log`.
- **APK**: `cd android && ./gradlew assembleRelease` gera `app/build/outputs/apk/release/app-release.apk`.
  A chave `controle-debug.keystore` serve só para as versões novas instalarem por cima das antigas.

**Segurança:** cada celular recebe uma chave própria no pareamento (o PC guarda só o hash). Sem a
chave, ninguém controla o PC. O código de pareamento bloqueia por 10 minutos depois de 5 tentativas
erradas. Use pelo **Tailscale** e não abra a porta 47800 no roteador: a conexão em si não tem
criptografia própria, e o Tailscale é quem criptografa.
