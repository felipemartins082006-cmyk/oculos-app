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
- **Testar estratégia** (backtest): roda a estratégia sobre os preços dos últimos meses ou anos e mostra
  quanto teria ganhado ou perdido, comparando com "só comprar e segurar".

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

Ele olha as **velas** do par (por padrão BTCUSDT, velas de 4 horas) e usa três indicadores:

| Indicador | Para quê |
|---|---|
| **EMA rápida (9)** e **EMA lenta (21)** | Médias dos preços. Quando a rápida cruza **para cima** da lenta, o preço está ganhando força. |
| **EMA de tendência (200)** | Só compra se o preço estiver **acima** dela, ou seja, em tendência de alta. 0 desliga. |
| **RSI (14)** | Não compra se o RSI estiver acima de 70 (preço "esticado" demais). |

- **Compra** quando a EMA rápida cruza para cima da lenta e os filtros deixam.
- **Vende** quando a EMA rápida cruza para baixo da lenta, **ou** se cair o **stop-loss** (padrão 6%)
  desde a compra, **ou** se subir o **take-profit** (padrão desligado).
- Só opera **comprado** (Spot): ou o dinheiro está em USDT, ou está na cripto. Nunca usa alavancagem.
- **Perda máxima do dia** (padrão 5%): se o patrimônio cair isso no dia, não faz novas compras até o dia seguinte.
- O sinal é conferido quando a vela **fecha**. O stop-loss é vigiado a cada 30 segundos.

Tudo isso pode ser mudado em **Ajustes**. Antes de mudar, teste em **Testar estratégia**.

### As contas

- **EMA de N velas:** começa com a média simples das N primeiras velas; depois, a cada vela,
  `EMA = preço × k + EMA anterior × (1 − k)`, com `k = 2 / (N + 1)` (na EMA de 9, k = 0,2).
  O robô calcula sobre as últimas 1000 velas fechadas.
- **RSI de 14 (método de Wilder):** médias suavizadas das altas e das baixas;
  `RSI = 100 − 100 / (1 + média das altas / média das baixas)`.
- **Quanto compra:** saldo livre × "% do saldo por compra", limitado ao "valor máximo por compra".
- **Taxas:** 0,1% na compra e na venda (no modo real, usa a taxa que a Binance cobrou, inclusive a paga em BNB).
- **Stop-loss:** preço médio de compra × (1 − stop%). Take-profit: × (1 + take%).
- **Resultado de uma operação:** valor líquido da venda − custo total da compra (as duas taxas incluídas).
  Exemplo: compra 100 USDT a 60.000 → taxa 0,10 → 0,001665 BTC. Vende a 63.000 → 104,90 − 0,10 de taxa
  = 104,79 → **+4,79 USDT (+4,79%)**.
- **Patrimônio:** moeda (USDT…) + cripto × preço atual. A perda do dia compara com o patrimônio da primeira
  checagem do dia.

## 3. Testar a estratégia (backtest)

Na aba **Testar estratégia**, escolha o par, o tamanho das velas, quantos dias para trás e os parâmetros,
e clique em **Rodar teste**. A simulação:

- usa o preço de fechamento de cada vela; o sinal aparece quando a vela fecha e a ordem sai na abertura da
  vela seguinte (igual ao robô);
- desconta 0,1% de taxa na compra e na venda;
- considera que o stop-loss vende, em média, 0,25% abaixo do preço de stop (como a ordem de stop na Binance);
- se o stop e o take-profit forem tocados na mesma vela, conta o stop (a hipótese pior);
- respeita o "% do saldo" e o "valor máximo por compra";
- baixa velas a mais antes do período só para "aquecer" as médias, então os valores das médias batem com os do
  robô ao vivo (que usa as últimas 1000 velas).

Você vê:

- **Resultado do robô** e o resultado de **só comprar e segurar** no mesmo período.
- **Maior queda (drawdown)**: o pior tombo do patrimônio entre um pico e um fundo.
- Quantas operações deram lucro, quanto foi pago em taxas (0,1% por operação) e o gráfico.

Se gostar do resultado, clique em **Usar estes ajustes no robô**.

Dicas:
- **Velas curtas (1m, 5m, 15m, 1h) quase sempre perdem**: o robô opera demais e as taxas comem o lucro.
- Teste em **vários períodos e pares**. Um ajuste que só funciona num período específico provavelmente
  foi sorte e não vai se repetir.

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
| `/vender` | Pergunta se quer vender; `/vender sim` vende a posição inteira a mercado |
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
   100 o robô nunca compra mais que 100 USDT de uma vez, mesmo que a conta tenha 5.000 USDT.
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
| `strategy.go` | EMA, RSI, sinal de compra/venda e backtest |
| `binance.go` | API da Binance (normal e de teste): velas, regras do par, saldo, ordens (assinatura HMAC-SHA256) |
| `server.go` | API do painel (só aceita pedidos do próprio painel) |
| `web/` | Painel (HTML/CSS/JS, gráficos em canvas) |

Os testes de `exchange_test.go` rodam o modo real contra uma **Binance falsa** (imita a API, confere a
assinatura, trava as moedas da ordem de stop, executa o stop quando o preço cai e derruba a conexão no meio de
uma ordem).

```sh
go test ./...
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w" -o TradeMartins.exe .
```

Também roda em Linux ou macOS com `go run .`.
