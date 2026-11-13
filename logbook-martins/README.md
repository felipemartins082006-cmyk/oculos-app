# Logbook (LogbookMartins)

App Android para registrar o treino da academia, no lugar do canal do Discord.
Tudo fica salvo só no celular: sem conta, sem internet e sem permissões.

O app começa vazio: quem usa é que vai inserindo os treinos.

## Três jeitos de inserir

**1. Treinando agora (aba Treino):** o dia de hoje já vem aberto. Digite uma série por vez,
do mesmo jeito do Discord:

    squat 160kg x 4
    bench press -> 94kg x 4 reps (1 RIR)
    squat 160 x 4, 4, 3          (três séries de uma vez)
    lower-back extension 20kg x 3 sets x 8 reps
    pull ups bw x 10             (só o peso do corpo)

Enquanto você digita o nome, aparecem os seus exercícios como sugestão, e embaixo
aparece o que você fez da última vez e o seu recorde. Quando bate um recorde, aparece um aviso 🏆.

**2. Esqueceu de registrar (aba Treino):** toque em "Outro dia" (ou ‹) e escolha a data.
Uma faixa laranja mostra que você está registrando num dia passado. Lance as séries uma
por uma, ou toque em "Colar treino" e escreva/cole o treino inteiro, uma série por linha.
Linhas que o app não entender ficam no campo para você corrigir.

**3. Já tem treinos anotados (compartilhar ou aba Importar):** o jeito mais fácil é, no app de
arquivos (ou Discord, WhatsApp, e-mail), segurar o arquivo → Compartilhar → Logbook (ou tocar no
arquivo → Abrir com → Logbook). O app abre na aba Importar já com a prévia. Também dá para usar
"Escolher arquivo do celular" ou colar o texto. Aceita PDF (o app lê o texto de dentro), .txt, .csv e .json.
Formatos de texto aceitos: cole as mensagens do canal (com as linhas de
data, ex.: "milkinaa — 21/7/25, 7:21") ou escolha o arquivo exportado (TXT, CSV ou JSON do
DiscordChatExporter). O app mostra uma prévia (quantas séries, quais dias, o que é novo e o
que já existe, linhas ignoradas) antes de importar. Dá para importar em partes: o que já
está no app não entra repetido. Se algo vier errado, "Desfazer importação".
As datas são lidas como dia/mês/ano; se o texto estiver em mês/dia, o app detecta ou você toca em "Trocar".

### Exportar o canal inteiro pelo Chrome

`ferramentas/exportar-discord.js` exporta o canal aberto no Discord (no Chrome do computador)
para um `logbook-discord.pdf` que o app importa pelo Compartilhar (ou `.txt` com `logbookSalvarTxt()`). Ele só rola o canal até o começo e
lê o que aparece na tela: não usa token, senha nem a API, e não envia nada para lugar nenhum.

1. Abra o canal do logbook no Chrome e aperte F12 → Console.
2. Cole o conteúdo do arquivo e aperte Enter (se o Chrome pedir, digite `allow pasting` antes).
3. Ele rola sozinho até o começo do canal e baixa o PDF. Se a rolagem automática parar antes
   do começo, role para cima com o mouse até a primeira mensagem (o script continua lendo) e
   digite `logbookSalvar()` no console para baixar o PDF completo.
4. Mande o arquivo para o celular e use Importar → Escolher arquivo.

## O resto

**Histórico**: todos os dias de treino, com filtro por exercício. Toque num dia para editar.

**Evolução**: gráfico por exercício (carga, reps, 1RM estimado e volume), recordes e todos
os treinos. Dá para renomear um exercício ou juntar dois nomes (ex.: erro de digitação).

**Backup** (aba Importar): salva tudo num .txt no formato do Discord, que dá para importar de volta.

## Instalar

Baixe `apk/LogbookMartins.apk` no celular e abra. O Android pede para permitir
"instalar apps desconhecidos". As versões novas instalam por cima, sem perder os registros.

## Compilar

    ./gradlew testDebugUnitTest assembleRelease

O APK sai em `app/build/outputs/apk/release/app-release.apk`. Os testes também tiram
fotos de cada tela em `app/build/telas/`.
