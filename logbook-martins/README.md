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

## Minimalista, backup diário e alarme (versão 3.2)

- **Backup automático a cada 24 horas**: o Android agenda uma cópia completa (treinos, diário, figuras) em Downloads/Logbook, mesmo com o app fechado.
- **Gráfico de calorias junto do peso**: Evolução → Peso mostra kcal por dia (médias de 7 e 30 dias) e quanto o peso mudou no período.
- **Dia sem treino = descanso**: todo dia passado em que você não registrou treino conta como descanso automático (no Dia, no Treino e no calendário).
- **Fim do timer fora do app**: tela cheia "Tempo!" (aparece até com o celular bloqueado) e notificação com +30s / Fechar. No Android 14+, se a tela cheia não abrir, permita "Notificações em tela cheia" para o Logbook nas configurações.
- **Estilo minimalista** como padrão: cores chapadas, sem brilhos e cartões mais limpos. O estilo vibrante continua em Aparência → Minimalista (desligar).

## Temas e temporizador (versão 3.1)

**Aparência** (aba Dados → Aparência e tema, ou o 🎨 no topo do Treino): temas prontos (Rubi, Brasa, Oceano,
Aurora, Esmeralda, Âmbar, Grafite), foto de fundo da galeria com "escurecer" e "desfoque", "usar as cores da
foto" (o app tira a cor principal da imagem) e a opacidade dos cartões de vidro. Fica salvo.

**Temporizador:** abre em pop-up por cima de tudo, com − / + de minutos e segundos e atalhos. Enquanto conta,
uma bolha flutuante mostra o tempo em todas as abas (toque para abrir). O estado é salvo: não zera ao sair do
app nem se o Android fechar o app. No fim, um alarme do sistema vibra, toca e notifica, mesmo com o app
fechado; enquanto conta, a notificação mostra a contagem regressiva.

## Backup e atualizações (versão 3.0)

**Dados → Baixar backup completo:** salva direto em `Downloads/Logbook/logbook-backup-AAAA-MM-DD.json` um
arquivo com tudo (séries com a hora de cada uma, diário, cronômetro, escolhas de figuras e as suas fotos).
"Enviar backup" manda o mesmo arquivo pelo WhatsApp/Drive/e-mail; "Planilha (.csv)" exporta as séries para
Excel/Google Planilhas. Todo dia, ao abrir o app, ele atualiza sozinho `Downloads/Logbook/logbook-backup-automatico.json`.

**Restaurar / importar:** Dados → Escolher arquivo, ou Compartilhar o arquivo com o Logbook. Aceita backup de
qualquer versão do app (campos que faltam ficam vazios, campos novos são ignorados), planilhas CSV (com `,`
ou `;`, nomes de coluna em português ou inglês, inclusive exportações do Strong), JSON de outros apps
(ex.: Hevy, treino → exercícios → séries), texto, PDF e Discord. Nada entra duplicado.

**Atualizar o app:** instale a versão nova por cima; os dados continuam (o backup é a garantia extra).

## Coeficiente de eficiência (versão 2.3)

Um número de 0 a 100 por dia (aba Dia, Histórico e Evolução → Insights):
- **Prontidão** (como você chegou): sono 25, qualidade do sono 15, nota do dia (0–10) 15, alimentação 15
  (comer antes do treino), humor 10, estresse 10 e recuperação 10 (dias de descanso antes). Só entra o que
  foi preenchido.
- **Desempenho** (como rendeu): rendimento vs. as últimas sessões (0% = 50, +10% = 100, −10% = 0).
- **Coeficiente** = 60% prontidão + 40% desempenho; no dia de descanso, a prontidão.

Os Insights mostram o coeficiente ao longo do tempo e quanto o rendimento muda quando a prontidão sobe.
Dias de treino e de descanso: marque na aba Dia ou no Treino ("Marcar descanso"). Em Números: músculos
específicos (quadríceps, posterior, glúteos, lombar, bíceps, tríceps…) com os exercícios de cada um, e um
calendário das últimas 16 semanas (treino × descanso), tocável.

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
