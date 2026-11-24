# Trade Martins

Robô de trade de cripto para a **Binance (Spot)**, com painel no navegador.

- **Modo simulado** (padrão): usa os preços reais da Binance com dinheiro de mentira (começa com 1.000 USDT).
  Não precisa de conta nem de chave.
- **Modo Binance de teste**: manda ordens de verdade para a **Binance de teste** (testnet), que usa dinheiro de
  mentira. Serve para conferir que compras, vendas e o stop funcionam antes de arriscar dinheiro.
- **Modo real**: compra e vende de verdade na sua conta da Binance, usando uma chave de API.
- **Stop-loss registrado na Binance** (modos real e de teste): logo depois da compra, o robô deixa uma ordem de
  stop na própria Binance, então a proteção funciona **mesmo com o PC desligado**.
- **Avisos no celular pelo Telegram**: compras, vendas, erros, robô ligado/desligado e quando ele volta depois de
  ficar fora do ar.
- **Carteira de vários pares** (ex.: BTC, ETH, SOL…) com o mesmo dinheiro: cada par tem sua posição, seus
  sinais e seu stop, e cada compra usa no máximo a parte daquele par do patrimônio.
- **4 estratégias** (cruzamento de médias, rompimento, momentum, reversão à média) com risco quantitativo:
  tamanho por **volatilidade-alvo**, stop por **ATR**, **stop móvel** e **filtro de mercado** (só compra com o Bitcoin acima da média).
- **Laboratório quantitativo**: backtest com métricas profissionais (Sharpe, Sortino, Calmar…), **walk-forward**,
  mapa de parâmetros, **Monte Carlo** e teste em vários pares com carteira.

> ⚠️ **Robô de trade não garante lucro.** Nos testes com os últimos 2 a 3 anos, a estratégia padrão
> **perdeu menos que segurar a moeda nas quedas, mas ganhou menos nas altas**, e em vários pares e períodos
> terminou no negativo. Use o modo simulado por um bom tempo, depois a Binance de teste, e só então pense
> no modo real, colocando só o que você aceita perder.

---

## 1. Instalar e abrir

1. Copie o **`TradeMartins.exe`** para uma pasta fixa, por exemplo `C:\TradeMartins\`.
2. Dê dois cliques nele.
   - Se aparecer **"O Windows protegeu o computador"**, clique em **Mais informações › Executar assim mesmo**.
3. Abre uma **janela preta** (o robô) e o **painel** no navegador (`http://127.0.0.1:47900`).
   - **Deixe a janela preta aberta** (pode minimizar). Fechar a janela desliga o robô.
   - Para ver o painel de novo, abra o `TradeMartins.exe` outra vez ou acesse `127.0.0.1:47900` no navegador.
4. Se o robô estava ligado e você fechar e abrir o programa, ele **volta a operar sozinho**.
5. **Enquanto o robô estiver ligado**, ele:
   - **abre sozinho junto com o Windows** (minimizado), por exemplo depois de uma atualização do Windows;
   - **impede o PC de entrar em suspensão** (a tela pode apagar normalmente).

   Isso não impede que alguém desligue o PC, que falte luz ou que a tampa do notebook seja fechada. Para isso
   existem o stop-loss registrado na Binance (seção 4) e o aviso de PC desligado (seção 6).
   Dá para desligar o início automático em **Ajustes › Mercado › Abrir junto com o Windows**.

**Para atualizar:** baixe o `.exe` novo e abra (pode ficar em outra pasta ou com outro nome). Se a versão antiga
estiver aberta, a nova fecha ela sozinha e continua de onde parou: robô ligado continua ligado, posição aberta
continua aberta, e o início com o Windows passa a abrir a versão nova. Depois, se quiser, apague o `.exe` antigo.
(Funciona também a partir das versões 1.0 e 1.1.)

O painel só abre **neste computador**. Ninguém de fora consegue acessar, e a chave da Binance fica só no PC
(em `%APPDATA%\TradeMartins`).

## 2. Como o robô decide

### A carteira

Em **Ajustes › Mercado › Pares da carteira**, coloque um ou mais pares separados por vírgula (até 12), todos na
mesma moeda — por exemplo `BTCUSDT, ETHUSDT, SOLUSDT, BNBUSDT`. Com um par só, o robô funciona como antes.

- **O dinheiro é dividido igualmente:** com 4 pares, cada compra usa no máximo **1/4 do patrimônio**
  (× o "% máximo" ou a volatilidade-alvo), limitada ao dinheiro livre e ao valor máximo por compra.
- **Cada par é independente:** tem sua posição, seus sinais, seu stop (registrado na Binance) e seu stop móvel.
- **Em cada checagem, primeiro as vendas, depois as compras**, para o dinheiro de uma venda já poder ser usado.
- No **Painel**, a tabela **Pares** mostra o preço, a variação em 24h, o que a estratégia está vendo e a posição
  de cada par (resultado agora e onde está o stop), com um botão **Vender** por par e **Vender tudo**. Clique num
  par para ver o gráfico dele.
- Para tirar um par da lista, feche antes a posição nele.

### As estratégias

O robô olha as **velas** de cada par (por padrão velas de 4 horas) e segue **uma de quatro estratégias**,
escolhida em **Ajustes › Estratégia** (a mesma para todos os pares). Só opera **comprado** (Spot): ou o dinheiro
está em USDT, ou está na cripto. Nunca usa alavancagem.

| Estratégia | Compra quando… | Vende quando… |
|---|---|---|
| **Cruzamento de médias** (padrão) | a EMA rápida (9) cruza para cima da lenta (21), com RSI abaixo de 70 e preço acima da EMA de tendência (200) | a EMA rápida cruza para baixo da lenta |
| **Rompimento (Donchian)** | o fechamento passa da maior máxima das últimas 20 velas (estilo "Turtle") | o fechamento fica abaixo da menor mínima das últimas 10 velas |
| **Momentum** | o preço está acima do de N dias atrás (padrão 30) | o preço fica abaixo do de N dias atrás |
| **Reversão à média** | o RSI de 2 períodos cai abaixo de 10 (queda forte e curta) dentro de uma tendência de alta | o RSI de 2 períodos passa de 70 |

**Risco** (vale para todas, em **Ajustes › Risco**):

- **Tamanho da compra:** "% fixo do saldo" ou **volatilidade-alvo**. Na volatilidade-alvo, o robô compra
  menos quando o mercado está agitado: se a cripto oscila 80% ao ano e o alvo é 40%, usa metade do % máximo.
- **Stop:** "% abaixo da compra" ou **ATR** (N vezes o tamanho médio das velas, que se adapta ao mercado).
- **Stop móvel:** o stop sobe junto com o maior fechamento desde a compra e nunca desce. Protege o lucro.
- **Take-profit** opcional, **valor máximo por compra** e **perda máxima do dia** (padrão 5%).
- **Filtro de mercado (novo na 2.3):** só compra quando o **Bitcoin está acima da média dos últimos N dias**
  (recomendado: 100) e, se marcado, **vende quando o Bitcoin fecha o dia abaixo da média**. As criptos caem
  juntas: quando o BTC entra em queda, as compras nos outros pares costumam dar errado. Usa as velas
  **diárias e já fechadas** do BTC, qualquer que seja o tamanho de vela da estratégia (há teste automático de
  que o filtro nunca usa um dia que ainda não fechou). Se o robô ficar 3 dias sem conseguir os dados do BTC,
  ele para de comprar, mas não vende nada por causa disso.

O sinal é conferido quando a vela **fecha**; o stop-loss é vigiado a cada 30 segundos (e fica registrado na
Binance nos modos real e de teste). **O robô ao vivo, o backtest e o laboratório usam exatamente o mesmo código
de estratégia**, então nunca discordam.

### As contas

- **EMA de N velas:** começa com a média simples das N primeiras velas; depois, a cada vela,
  `EMA = preço × k + EMA anterior × (1 − k)`, com `k = 2 / (N + 1)` (na EMA de 9, k = 0,2).
  O robô calcula sobre as últimas 1000 velas fechadas.
- **RSI de 14 (método de Wilder):** médias suavizadas das altas e das baixas;
  `RSI = 100 − 100 / (1 + média das altas / média das baixas)`.
- **ATR de N velas:** média (suavizada) da amplitude de cada vela: máxima − mínima, incluindo saltos desde o
  fechamento anterior.
- **Volatilidade:** desvio padrão dos retornos diários das velas dos últimos N dias, convertido para "% ao ano".
- **Quanto compra:** saldo livre × "% máximo do saldo" (ou × alvo ÷ volatilidade, se menor), limitado ao
  "valor máximo por compra".
- **Taxas:** 0,1% na compra e na venda (no modo real, usa a taxa que a Binance cobrou, inclusive a paga em BNB).
- **Stop-loss:** preço médio de compra × (1 − stop%), ou preço de compra − N × ATR. Com stop móvel, a mesma
  conta é feita a partir do maior fechamento desde a compra, e o stop só sobe. Take-profit: × (1 + take%).
- **Resultado de uma operação:** valor líquido da venda − custo total da compra (as duas taxas incluídas).
  Exemplo: compra 100 USDT a 60.000 → taxa 0,10 → 0,001665 BTC. Vende a 63.000 → 104,90 − 0,10 de taxa
  = 104,79 → **+4,79 USDT (+4,79%)**.
- **Patrimônio:** moeda (USDT…) + cripto × preço atual. A perda do dia compara com o patrimônio da primeira
  checagem do dia.

## 3. Laboratório quantitativo

A aba **Laboratório** testa as estratégias com os preços reais da Binance. A ideia é a de um gestor quantitativo:
**tentar provar que a estratégia NÃO funciona** antes de colocar dinheiro.

| Teste | O que responde |
|---|---|
| **Backtest** | Como a estratégia teria ido no período, comparada a só comprar e segurar |
| **Walk-forward** | **O teste que importa.** Otimiza os parâmetros num pedaço do passado (treino, ex.: 365 dias) e mede nos meses seguintes (teste, ex.: 90 dias), que o otimizador não viu. Anda no tempo e emenda os testes. |
| **Mapa de parâmetros** | O resultado numa grade de parâmetros. Robusto = região grande boa; frágil = uma casa boa isolada |
| **Monte Carlo** | Sorteia 2.000 vezes a ordem das operações: faixa de resultados e de quedas possíveis só por sorte |
| **Vários pares** | A mesma estratégia em várias criptos, e numa carteira dividida igualmente entre elas |

**Métricas:** retorno total e ao ano (CAGR), volatilidade, **Sharpe** (retorno por unidade de risco), Sortino,
maior queda (medida na mínima das velas), Calmar (retorno ao ano ÷ maior queda), número de operações, acerto,
fator de lucro, média por operação, tempo comprado e se o Sharpe é **estatisticamente significativo**
(t = Sharpe × √anos acima de 1,96). Cada resultado vem com uma explicação em português do que ele quer dizer.

**Como a simulação funciona:** o sinal aparece no fechamento da vela e a ordem sai na abertura da seguinte;
0,1% de taxa na compra e na venda; o stop vende, em média, 0,25% abaixo do preço de stop; se stop e take-profit
forem tocados na mesma vela, conta o stop; velas a mais antes do período servem só para "aquecer" os indicadores.
Um teste automático garante que **nenhuma estratégia usa dados do futuro**, e outro que o walk-forward escolhe
os parâmetros de cada janela **só com dados anteriores a ela**.

### O que os testes mostraram (novembro de 2026, velas de 4h, 3 anos)

Para ser honesto sobre o que esperar:

- **Nenhuma estratégia, em nenhum par, foi estatisticamente significativa** (nem só segurar a moeda).
  Três anos de cripto é pouco tempo para provar alguma coisa.
- **Walk-forward:** em todas as estratégias, otimizar os parâmetros no passado deu Sharpe ≈ 1 no treino e
  **Sharpe negativo nos meses seguintes**. É overfitting: não otimize os parâmetros procurando o melhor resultado.
  Os parâmetros "escolhidos" mudavam de janela para janela (ex.: momentum de 90 dias, depois 14, depois 180).
- **No BTC sozinho**, nenhuma estratégia rendeu mais que só segurar. A melhor por risco foi o momentum com
  volatilidade-alvo (sem stop fixo): mesmo Sharpe de segurar (0,57), mas maior queda de 35% em vez de 56%.
- **O que mais ajudou foi diversificar.** Carteira com 8 criptos (BTC, ETH, SOL, BNB, XRP, ADA, DOGE, LINK),
  parâmetros padrão (sem otimizar), simulada **exatamente como o robô ao vivo opera**:

  | Carteira de 8 pares, 3 anos | Retorno | Sharpe | Maior queda | t |
  |---|---|---|---|---|
  | Cruzamento de médias | +85% | 0,95 | 26% | 1,65 |
  | Momentum + volatilidade-alvo 40% | +93% | 0,86 | 37% | 1,48 |
  | Rompimento + stop ATR | +19% | 0,34 | 45% | 0,59 |
  | Dividir igual e só segurar | +13% | 0,38 | 69% | 0,65 |

  Ainda assim **não é significativo** (t abaixo de 1,96), o resultado dependeu muito de poucas criptos (DOGE e
  XRP fizeram boa parte do ganho do momentum) e há viés de sobrevivência (a lista tem criptos grandes hoje).
- **Monte Carlo** do momentum no BTC: a mesma estratégia, com as operações em outra ordem, teria 28% de chance
  de terminar no prejuízo e maior queda típica de 32%.

### Testando melhorias sem se enganar (versão 2.3)

Para não escolher uma "melhoria" só porque deu certo no passado, cada ideia foi fixada **antes** e testada em
dois períodos separados, com os 8 pares e a simulação igual ao robô ao vivo (taxas, vendas antes das compras):
**desenho** (nov/2020 a nov/2023, usado para decidir) e **confirmação** (nov/2023 a nov/2026, só olhado no fim).

| Ideia | Desenho | Confirmação | Entrou? |
|---|---|---|---|
| **Filtro de mercado** (BTC acima da média de 100 dias, vende abaixo) | melhorou o momentum e o rompimento | melhorou de novo | **Sim**, como opção |
| Momentum votando em 3 prazos (15/30/90 dias) | pior | igual ou pior | Não |
| Rodízio: só os 3 ou 4 pares de maior momentum | retorno maior, queda bem maior, Sharpe igual ou menor | idem | Não |
| Volatilidade-alvo também na EMA e no rompimento | menos retorno, menos queda | Sharpe melhor | Já existia (é escolha de risco) |

Efeito do filtro de mercado (carteira de 8 pares, velas de 4h; Sharpe e maior queda):

| Estratégia | Desenho sem → com | Confirmação sem → com |
|---|---|---|
| Momentum 30 dias + volatilidade-alvo 40% | 1,59 / 29% → 1,67 / 29% | 0,86 / 37% → **1,12 / 27%** |
| Momentum 30 dias, sem stop | 1,69 / 45% → 1,87 / 44% | 0,49 / 51% → **0,87 / 37%** |
| Rompimento (Donchian) | 1,28 / 39% → 1,35 / 38% | 0,79 / 28% → 0,89 / 31% |
| Cruzamento de médias (já tem o filtro da EMA 200) | 1,74 / 45% → 1,74 / 35% | 0,98 / 26% → 0,86 / 24% |

Em velas diárias o resultado foi parecido (momentum + volatilidade-alvo na confirmação: Sharpe 0,93 → 1,21,
queda 35% → 25%). **Recomendação:** use o filtro com o Momentum ou o Rompimento; no Cruzamento de médias ele
não ajudou de forma clara. A **reversão à média (RSI curto) perdeu dinheiro nos dois períodos** (−32% e −32% em 4h): evite. Mesmo melhor, nada disso é garantia: continuam sendo só 6 anos de cripto. O teste
pode ser refeito com `RESEARCH_DIR=pasta go test -tags research -run Research -v` (arquivo `research_test.go`).

### Mais pares, sem viés de sobrevivência (versão 2.4)

A lista de 8 pares usada acima tem um problema: são criptos que são grandes **hoje**. Quem escolhe os pares
olhando o presente já sabe quem sobreviveu. Para corrigir, a carteira de cada período foi escolhida **só com o
que se sabia na época**: os 10 ou 20 pares USDT com mais volume nos 30 dias antes do início, entre **todos** os
que existiam então, inclusive os que depois saíram da Binance (YFII, SXP, EOS, WAVES, FTT, MATIC…). Quando um
par sai da corretora no meio do teste, a simulação vende pelo último preço.

| Carteira (velas de 4h) | Período | Só segurar | Melhor estratégia: Momentum + vol-alvo 40% + filtro BTC |
|---|---|---|---|
| 8 pares de hoje (otimista) | 2023–2026 | +17%, queda 69% | +138%, Sharpe 1,12, queda 27% |
| 10 de maior volume na época | 2020–2023 | +107%, queda 81% | +186%, Sharpe 1,32, queda 25% |
| 10 de maior volume na época | 2023–2026 | **−1%**, queda 68% | **+93%, Sharpe 0,97, queda 24%** |
| 20 de maior volume na época | 2023–2026 | **−34%**, queda 74% | +69%, Sharpe 0,81, queda 28% |

O que isso mostra:

- **Os números honestos são menores** que os da lista de hoje, mas a estratégia continua bem melhor que segurar:
  a maior parte da vantagem vem de **ficar de fora nas quedas grandes**.
- **Mais pares não melhorou:** 20 pares foi pior que 10 (as moedas extras são piores). Use os **10 de maior
  volume** (botão "10 de maior volume" em Ajustes e no laboratório) e reveja a lista de tempos em tempos.
- **Momentum + volatilidade-alvo 40% + filtro BTC 100 dias** foi a melhor ou quase a melhor em todos os 12
  testes (3 carteiras × 2 períodos × velas de 4h e diárias). Misturar estratégias (dividir o dinheiro entre
  EMA, rompimento e momentum) diminuiu um pouco a queda, mas também o Sharpe.

### Com pouco dinheiro

A Binance só aceita ordens a partir de **5 USDT**. Com 10 USDT, só cabe **1 posição por vez** (2 × 5 = 10 não
sobra nada para a taxa). Testado com os 20 pares de maior volume e uma vaga só: o resultado virou loteria
(de −59% a +1.659%, dependendo da estratégia e do período). Num dos testes, a única compra foi XRP em nov/2020,
que caiu 62% com o processo da SEC; o saldo ficou abaixo de 5 USDT e **o robô nunca mais conseguiu comprar**.

Para a carteira diversificada funcionar: **10 pares × 5 USDT ÷ 95%** ≈ 53 USDT no mínimo, com folga para
quedas de ~25%: **uns 70 USDT** com "% fixo do saldo", ou **uns 150 USDT** com volatilidade-alvo (que compra
menos que o máximo). Com menos que isso, use o **modo simulado** para acompanhar a estratégia. O painel agora
avisa quando o saldo é pequeno demais para o número de pares, e o modo simulado respeita o mínimo de 5 USDT
(antes ele aceitava compras de 1 USDT e parecia possível operar com pouco).

### E o mercado futuro? (vender a descoberto e arbitragem de funding)

Testado com o mesmo método (10 de maior volume na época, dois períodos), usando o histórico público da Binance
(data.binance.vision): funding de cada 8h, preços do perpétuo e do Spot, taxas e liquidação.

**Ganhar na queda (vender a descoberto)** com as mesmas regras (vendido quando o momentum é negativo e o BTC
está abaixo da média de 100 dias), sem alavancagem:

| Velas de 8h, 10 de maior volume na época | 2020–2023 | 2023–2026 |
|---|---|---|
| Spot, só comprado (como o robô) | +147%, Sharpe 1,43, queda 17% | **+99%, Sharpe 1,09, queda 22%** |
| Perpétuo, só comprado (paga funding) | +114%, Sharpe 1,22, queda 19% | +87%, Sharpe 1,02, queda 22% |
| Perpétuo, só vendido | −4%, Sharpe 0,06, queda 33% | −10%, Sharpe −0,09, queda 31% |
| Perpétuo, comprado **e** vendido | +104%, Sharpe 0,88, queda 30% | +65%, Sharpe 0,69, queda 34% |

Vender a descoberto **não deu lucro em nenhum dos dois períodos**: as quedas de cripto são rápidas e as
recuperações violentas, e o vendido é pego nelas. Somar o vendido ao comprado **piorou** o Sharpe e aumentou a
queda. Por isso o robô continua só no Spot (sem risco de liquidação).

**Arbitragem de funding** (comprado no Spot + vendido no perpétuo, o preço se cancela e se recebe o funding):
muito pouco risco (queda abaixo de 1–3%), mas **pouco retorno**: 7–10% ao ano em 2020–2023 (mercado em alta,
funding alto) e **2–4% ao ano em 2023–2026**, parecido com deixar USDT rendendo na própria Binance. Precisa de
dinheiro nas duas pontas (o perpétuo do BTC exige no mínimo 100 USDT). Não entrou no robô.

Os scripts estão em `research/` (Python, sem dependências): `universe.py` (pares de maior volume na época),
`futdl.py` (baixa funding e velas), `funding.py` e `longshort.py`.

Resumo: as estratégias **reduzem as quedas** em relação a segurar, principalmente numa carteira de vários pares,
mas **nenhuma mostrou lucro comprovado**. No teste "Vários pares", o botão **Usar esta carteira no robô** aplica
os pares e os ajustes testados.

## 4. Stop-loss registrado na Binance

Nos modos **real** e **Binance de teste**, logo depois de cada compra o robô registra na Binance uma ordem de
**stop-loss** (tipo "stop limit"): se o preço cair até o stop, a própria Binance vende, **mesmo que o seu PC
esteja desligado, sem internet ou com o robô fechado**. No Painel, embaixo do stop aparece
"registrado na Binance ✔".

- A ordem vende até 0,5% abaixo do stop, para garantir que execute. Se o preço despencar tão rápido que passe desse
  limite e a ordem não execute, o robô (se estiver ligado) vende a mercado.
- As moedas da posição ficam "travadas" na ordem de stop no app da Binance. Isso é normal.
- Quando o robô vende por outro motivo (sinal, take-profit, "Vender agora"), ele cancela o stop antes.
- Se você mudar o stop-loss em Ajustes, a ordem na Binance é trocada sozinha.
- Se alguém cancelar a ordem pelo app da Binance, o robô registra outra. Se você vender tudo pelo app,
  o robô percebe e encerra a posição.
- Se a Binance recusar a ordem de stop (posição pequena demais, por exemplo), o robô avisa e passa a vigiar o
  stop pelo PC, como no modo simulado.
- O take-profit continua sendo vigiado pelo PC.

Se a internet cair bem no meio de uma compra ou venda, o robô pergunta à Binance se a ordem foi executada antes
de seguir. Se não conseguir confirmar, avisa para você conferir no app da Binance.

## 5. Binance de teste (antes do modo real)

A Binance tem um ambiente de testes com dinheiro de mentira, que aceita ordens como a Binance de verdade.
Use para ver o robô comprando, registrando o stop e vendendo, sem risco:

1. Abra **testnet.binance.vision** e entre com uma conta do **GitHub** (é grátis criar uma).
2. Clique em **Generate HMAC_SHA256 Key**, dê um nome e copie a **API Key** e a **Secret Key**
   (a secreta só aparece uma vez).
3. No painel: **Ajustes › Chave da Binance de teste**, cole as duas, clique em **Testar chave** e em **Salvar ajustes**.
4. Em **Modo**, escolha **Binance de teste**, salve e ligue o robô.

Observações: os preços da Binance de teste podem ser bem diferentes dos da Binance normal, e de tempos em
tempos ela é zerada (o saldo de mentira volta ao começo). Ela serve para testar se tudo funciona, não para medir
lucro. Para medir lucro, use o modo simulado e o backtest.

## 6. Avisos no celular (Telegram)

1. No Telegram, abra o **@BotFather**, mande `/newbot`, escolha um nome e um usuário para o bot e copie o
   **token** que ele mostra.
2. No painel: **Ajustes › Avisos no celular**, cole o token.
3. Abra o seu bot novo no Telegram (o BotFather dá o link) e mande `/start`.
4. Clique em **Descobrir meu chat**, depois em **Enviar teste**. Chegou a mensagem? Clique em **Salvar ajustes**.

Você recebe aviso de: compra (com o stop), venda (com o resultado), erros (o mesmo erro no máximo a cada
30 minutos), robô ligado/desligado/retomado e, quando o robô volta depois de ficar fora do ar, por quanto tempo
ele ficou parado.

**Comandos** (mande para o seu bot no Telegram; o menu aparece no botão "/" do Telegram):

| Comando | O que faz |
|---|---|
| `/status` | Preço, patrimônio, posição aberta (com resultado agora e onde está o stop) e resultado das vendas |
| `/vender` | Mostra as posições e pede confirmação. `/vender BTCUSDT sim` (ou `/vender btc sim`) vende um par; `/vender tudo sim` vende todos. Com uma posição só, `/vender sim` também serve |
| `/parar` | Desliga o robô (no modo real/teste, o stop continua na Binance) |
| `/ligar` | Liga o robô. No modo real, é preciso mandar `/ligar real` |
| `/ajuda` | Lista os comandos |

Segurança: o robô **só obedece ao chat configurado** (mensagens de outras pessoas são ignoradas e você recebe
um aviso). Comandos mandados há mais de 2 minutos (por exemplo, com o robô fechado) **não são executados**: o
robô avisa e pede para mandar de novo.

**Avisar se o PC desligar (opcional):** o robô não consegue avisar que morreu. Para isso, crie uma checagem
grátis em **healthchecks.io** (período 5 minutos, tolerância 10 minutos), ative lá os avisos por Telegram ou
e-mail e cole o endereço de ping em **Ajustes › Avisos no celular › Avisar se o PC desligar**. Enquanto o robô
estiver ligado, ele manda sinal de vida a cada checagem. Se o sinal parar, o healthchecks.io avisa você.

## 7. Modo real (dinheiro de verdade)

1. Na Binance: **Perfil › Gerenciamento de API › Criar API** (tipo "gerada pelo sistema").
2. Permissões: marque **somente** "Ativar leitura" e "Ativar negociação Spot e de margem".
   **NUNCA ative saques nem transferências.** Assim, mesmo que alguém pegue a chave, não consegue tirar seu
   dinheiro. O robô confere isso: **se a chave permitir saque ou transferência, ele se recusa a operar** e o botão
   "Testar chave" mostra o problema.
3. Em restrição de IP, coloque o IP da sua internet (opcional, mas mais seguro).
4. No painel: **Ajustes › Chave da Binance**, cole a API Key e a Secret Key, clique em **Testar chave** e depois
   em **Salvar ajustes**.
5. Em **Ajustes › Risco**, preencha o **Valor máximo por compra** (obrigatório no modo real). Por exemplo, com
   100 o robô nunca compra mais que 100 USDT de uma vez, mesmo que a conta tenha 5.000 USDT. **Atenção na
   carteira:** o limite vale para cada compra, então com 4 pares o robô pode ter até 4 × 100 = 400 USDT investidos.
6. Em **Modo**, escolha **Real** e salve. No Painel, clique em **Ligar robô** e digite `REAL` para confirmar.

Observações:
- Tenha saldo na moeda de cotação do par (USDT para BTCUSDT, BRL para BTCBRL).
- A Binance exige um valor mínimo por ordem (cerca de 5 a 10 USDT).
- O robô só vende o que **ele mesmo comprou**. Cripto que você já tinha na conta fica intacta.
- Com o robô desligado, o **stop-loss continua na Binance**, mas o take-profit e a venda pelo sinal param.
- Para trocar o par ou o modo, primeiro feche a posição aberta.

## 8. Para quem for mexer no código

Go, com uma única dependência (`golang.org/x/sys`, para o registro e a energia do Windows).
O painel (`web/`) vai embutido no `.exe`.

| Arquivo | O que faz |
|---|---|
| `main.go` | Abre o servidor em `127.0.0.1:47900` e o navegador; fecha a versão antiga ao atualizar |
| `plat_windows.go` | Início com o Windows, impedir suspensão, minimizar a janela |
| `bot.go` | Laço do robô, compra/venda simulada, ajustes, estado salvo em JSON |
| `exchange.go` | Ordens de verdade: stop-loss registrado na Binance, cancelamento e conferência quando a conexão cai |
| `notify.go` | Avisos pelo Telegram e sinal de vida (healthchecks.io) |
| `indicators.go` | EMA, RSI, ATR, canal de Donchian, volatilidade, momentum (sem olhar o futuro) |
| `strategy.go` | As 4 estratégias, tamanho da compra, stop por % / ATR / móvel (usado pelo robô, backtest e laboratório) |
| `backtest.go` | Simulação vela a vela |
| `metrics.go` | Sharpe, Sortino, Calmar, maior queda, fator de lucro, significância… |
| `lab.go` | Walk-forward, mapa de parâmetros, Monte Carlo e vários pares |
| `portfolio.go` | Backtest de carteira: vários pares com o mesmo dinheiro, igual ao robô ao vivo |
| `cross.go` | Filtro de mercado (BTC acima da média, com velas diárias fechadas) |
| `topvol.go` | Os N pares de maior volume (sem stablecoins, ações tokenizadas e tokens alavancados) |
| `research_test.go` | Pesquisa de melhorias em dois períodos (só roda com `-tags research`) |
| `telegram_cmd.go` | Comandos pelo Telegram |
| `binance.go` | API da Binance (normal e de teste): velas, regras do par, saldo, ordens (assinatura HMAC-SHA256) |
| `server.go` | API do painel (só aceita pedidos do próprio painel) |
| `web/` | Painel (HTML/CSS/JS, gráficos em canvas); `web/lab.js` é o laboratório |

Os testes de `exchange_test.go` rodam o modo real contra uma **Binance falsa** (imita a API, confere a
assinatura, trava as moedas da ordem de stop, executa o stop quando o preço cai e derruba a conexão no meio de
uma ordem).

```sh
go test ./...
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w" -o TradeMartins.exe .
```

Também roda em Linux ou macOS com `go run .`.
