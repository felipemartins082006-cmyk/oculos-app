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

## Cronômetro, temporizador e números (versão 2.1)

**Cronômetro do treino:** no alto do Treino de hoje, "⏱ Iniciar treino" (ou a primeira série do dia) liga o
cronômetro, que continua contando mesmo com o app fechado. Botões: ⏸ Pausar / ▶ Retomar (a pausa não
conta), ✓ Concluir treino (guarda o tempo), ▶ Continuar treino e "Cancelar cronômetro" (pede confirmação e
apaga o tempo; as séries ficam).

**Apagar e editar:** toque numa série (✎) para editar ou apagar; no ⋮ de cada exercício, apague todas as
séries dele no dia; "🗑 Apagar treino deste dia" no fim do Treino ou o 🗑 de cada dia no Histórico apaga o
treino inteiro. Tudo pede confirmação. Cada série
guarda a hora em que foi anotada, e o app mostra "Descansando 1:32 desde a última série".

**Temporizador:** "⏲ Timer" com atalhos (0:45 a 5:00) ou minutos e segundos à mão; pausar, +15s, zerar.
Ao terminar, o celular vibra e toca.

**Números (Evolução → Números):** por período (30 dias, 3 meses, 12 meses, tudo): treinos, média por
semana, séries, volume, sequência de semanas, tempo médio de treino e de descanso, pizza dos grupos
musculares, treinos por mês e por dia da semana, recordes, maiores progressões de carga e melhores treinos.

## Diário, peso e insights (versão 2.0)

**Peso:** no começo de cada treino aparece "Peso de hoje", com a escolha *Em jejum* (ao acordar, de cueca)
ou *Na sessão* (na academia, com roupa). O gráfico (Evolução → Peso) mostra cada tipo separado, com a
média dos últimos 7 dias, e quanto a pesagem na sessão costuma ser maior que em jejum.

**Aba Dia:** sono (horas e qualidade), cabeça (humor, estresse, como foi o dia, eventos como "Prova / TCC"),
alimentação (refeições com horário e itens do jeito que você pesa: `arroz 150g 195kcal`, com ícones) e a
hora em que o treino começou (marcada sozinha na primeira série do dia).

**Insights (Evolução → Insights):** cada treino é comparado com as suas últimas sessões dos mesmos
exercícios (1RM estimado) e o app mostra como você rende com e sem cada fator (sono, comer antes do treino,
café da manhã, calorias, humor, estresse, eventos), além dos treinos acima e abaixo do normal com o
contexto do dia. São padrões nos seus registros, não diagnóstico.

O backup agora é completo (séries e diário) em `.json`.

## Figuras dos exercícios

Ao digitar o nome (ex.: `squat`), aparece a ilustração do exercício, alternando entre o início e o
fim do movimento. O app reconhece os nomes usados na academia, em inglês e em português (stiff, larsen,
scott, pulldown, agachamento, supino, puxada, cadeira extensora…). Na página do exercício (aba Evolução)
dá para colocar uma foto sua (do aparelho, por exemplo), escolher outra ilustração ou ficar sem imagem.

Ilustrações: [Everkinetic](https://github.com/everkinetic/data) (CC BY-SA 4.0) e
[wger](https://wger.de) (CC BY-SA 3.0), adaptadas em tons de cinza — ver `app/src/main/assets/ilustracoes/LICENCA.txt`.

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
