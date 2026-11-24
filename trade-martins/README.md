# Trade Martins

Robô de trade de cripto para a **Binance (Spot)**, com painel no navegador.

- **Modo simulado** (padrão): usa os preços reais da Binance com dinheiro de mentira (começa com 1.000 USDT).
  Não precisa de conta nem de chave.
- **Modo real**: compra e vende de verdade na sua conta da Binance, usando uma chave de API.
- **Testar estratégia** (backtest): roda a estratégia sobre os preços dos últimos meses ou anos e mostra
  quanto teria ganhado ou perdido, comparando com "só comprar e segurar".

> ⚠️ **Robô de trade não garante lucro.** Nos testes com os últimos 2 a 3 anos, a estratégia padrão
> **perdeu menos que segurar a moeda nas quedas, mas ganhou menos nas altas**, e em vários pares e períodos
> terminou no negativo. Use o modo simulado por um bom tempo antes de pensar no modo real, e no modo real
> coloque só o que você aceita perder.

---

## 1. Instalar e abrir

1. Copie o **`TradeMartins.exe`** para uma pasta fixa, por exemplo `C:\TradeMartins\`.
2. Dê dois cliques nele.
   - Se aparecer **"O Windows protegeu o computador"**, clique em **Mais informações › Executar assim mesmo**.
3. Abre uma **janela preta** (o robô) e o **painel** no navegador (`http://127.0.0.1:47900`).
   - **Deixe a janela preta aberta.** Fechar a janela desliga o robô.
   - O PC não pode dormir/hibernar enquanto o robô roda
     (Configurações › Sistema › Energia › Suspensão: **Nunca**).
   - Para ver o painel de novo, abra o `TradeMartins.exe` outra vez ou acesse `127.0.0.1:47900` no navegador.
4. Se o robô estava ligado e você fechar e abrir o programa, ele **volta a operar sozinho**.

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

## 3. Testar a estratégia (backtest)

Na aba **Testar estratégia**, escolha o par, o tamanho das velas, quantos dias para trás e os parâmetros,
e clique em **Rodar teste**. Você vê:

- **Resultado do robô** e o resultado de **só comprar e segurar** no mesmo período.
- **Maior queda (drawdown)**: o pior tombo do patrimônio entre um pico e um fundo.
- Quantas operações deram lucro, quanto foi pago em taxas (0,1% por operação) e o gráfico.

Se gostar do resultado, clique em **Usar estes ajustes no robô**.

Dicas:
- **Velas curtas (1m, 5m, 15m, 1h) quase sempre perdem**: o robô opera demais e as taxas comem o lucro.
- Teste em **vários períodos e pares**. Um ajuste que só funciona num período específico provavelmente
  foi sorte e não vai se repetir.

## 4. Modo real (dinheiro de verdade)

1. Na Binance: **Perfil › Gerenciamento de API › Criar API** (tipo "gerada pelo sistema").
2. Permissões: marque **somente** "Ativar leitura" e "Ativar negociação Spot e de margem".
   **NUNCA ative saques.** Assim, mesmo que alguém pegue a chave, não consegue tirar seu dinheiro.
3. Em restrição de IP, coloque o IP da sua internet (opcional, mas mais seguro).
4. No painel: **Ajustes › Chave de API**, cole a API Key e a Secret Key, clique em **Testar chave** e depois em **Salvar ajustes**.
5. Em **Modo**, escolha **Real** e salve. No Painel, clique em **Ligar robô** e digite `REAL` para confirmar.

Observações:
- Tenha saldo na moeda de cotação do par (USDT para BTCUSDT, BRL para BTCBRL).
- A Binance exige um valor mínimo por ordem (cerca de 5 a 10 USDT).
- O robô só vende o que **ele mesmo comprou**. Cripto que você já tinha na conta fica intacta.
- Com o robô desligado, uma posição aberta **não tem stop-loss vigiado**.
  Use **Vender agora** no Painel se quiser sair antes de desligar.
- Para trocar o par ou o modo, primeiro feche a posição aberta.

## 5. Para quem for mexer no código

Go puro, sem dependências externas. O painel (`web/`) vai embutido no `.exe`.

| Arquivo | O que faz |
|---|---|
| `main.go` | Abre o servidor em `127.0.0.1:47900` e o navegador |
| `bot.go` | Laço do robô, compra/venda (simulada e real), ajustes, estado salvo em JSON |
| `strategy.go` | EMA, RSI, sinal de compra/venda e backtest |
| `binance.go` | API da Binance: velas, regras do par, saldo, ordens a mercado (assinatura HMAC-SHA256) |
| `server.go` | API do painel (só aceita pedidos do próprio painel) |
| `web/` | Painel (HTML/CSS/JS, gráficos em canvas) |

```sh
go test ./...
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w" -o TradeMartins.exe .
```

Também roda em Linux ou macOS com `go run .`.
