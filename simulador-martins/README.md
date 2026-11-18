# Simulador Martins

Simulação **Monte Carlo**, backtest e análise de carteiras com ativos da B3, fundos de investimento,
ações e ETFs dos EUA, cripto e renda fixa. Feito para celular, funciona também no computador.
Funciona sem internet depois da primeira abertura e sem login. A carteira e o plano ficam salvos só no aparelho.

Endereço (depois de publicado no GitHub Pages):
https://felipemartins082006-cmyk.github.io/oculos-app/simulador-martins/

## Programa para Windows

A pasta `desktop/` gera o **Simulador Martins.exe** (Electron): mesma interface, janela própria, sem
internet, Monte Carlo em todos os núcleos do processador. Veja `desktop/README.md`.

## Instalar no Android (APK)

O arquivo **`SimuladorMartins.apk`** desta pasta é o app pronto. Passe para o celular, abra e toque em
Instalar (o Android pede para permitir "instalar apps desconhecidos" na primeira vez).

- Não usa internet: o app não tem nenhuma permissão. O histórico de preços vai dentro do APK.
- Segue o tema claro/escuro do celular.
- Versões novas instalam por cima da anterior sem perder a carteira salva (a chave de assinatura é fixa).

Para gerar o APK de novo (por exemplo, depois de atualizar os dados), com o Android SDK instalado:

```
cd android && ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ../SimuladorMartins.apk
```

Antes, aumente `versionCode` e `versionName` em `android/app/build.gradle.kts`.

## O que faz

**Carteiras** — quantas carteiras quiser, comparadas lado a lado, montadas a partir de 1.942 ativos:

| Classe | Ativos |
|---|---|
| Ações da B3 (negociação média ≥ R$ 100 mil/dia) | 261 |
| FIIs, Fiagro e FI-Infra | 234 |
| ETFs da B3 (inclui Tesouro, IMA-B, S&P, cripto) | 131 |
| Fundos de investimento da CVM (renda fixa, multimercado, ações, cambial) | 486 |
| Ações dos EUA (600 maiores) | 590 |
| ETFs dos EUA (sem alavancados) | 220 |
| Cripto, índices, ouro, prata, petróleo, dólar e euro | 20 |

Mais renda fixa por modelo: CDI × %, IPCA + taxa e prefixado. Há 11 modelos prontos (Ibovespa,
S&P 500, Global 60/40, Permanente, Big Techs, FIIs, top multimercados etc.).

**Simulação (Monte Carlo)**
- 4 modelos de retorno: bootstrap mensal, bootstrap em blocos (3 a 24 meses), normal multivariada
  e t-Student (caudas gordas).
- Mesmos sorteios para todas as carteiras (comparação justa) e para o CDI de referência.
- Plano: aporte inicial e mensal, correção pelo IPCA, aumento real do aporte, meta.
- Aposentadoria: saques mensais a partir de um ano, com chance de o dinheiro acabar.
- Rebalanceamento mensal, trimestral, anual ou nunca; câmbio convertido ou ignorado;
  ajuste no retorno esperado; custo anual extra; IR no resgate; valores reais ou nominais.
- Resultados: percentis, chance de perda / de bater o CDI / de atingir a meta, retorno anual e
  queda máxima por cenário, leque de futuros e distribuição final. Aviso automático quando a janela
  histórica é curta.

**Histórico (backtest)** — evolução real das carteiras contra CDI, IPCA, Ibovespa e S&P 500 em
reais; drawdown; retorno, volatilidade, Sharpe, Sortino, VaR/CVaR, beta, correlação; retorno ano a ano.

**Análise** — contribuição de cada ativo para o risco, matriz de correlação e fronteira eficiente
(máximo Sharpe, mínimo risco e "mesmo risco, mais retorno"), com botão para aplicar os pesos.

**Otimizar** — escolhe a melhor carteira num universo filtrado (ex.: todos os FIIs de papel com 5+
anos) ou nos ativos de uma carteira: máximo Sharpe, mínimo risco, paridade de risco ou risco-alvo;
médias James-Stein, covariância Ledoit-Wolf, reamostragem 50×, limite de peso e de nº de ativos;
teste fora da amostra; salva o resultado como carteira.

**FIIs** — mandato (papel, tijolo, híbrido), segmento, P/VP e cotistas pelo informe mensal da CVM;
filtros "FIIs de papel/tijolo/híbridos" no seletor e no otimizador.

**Monte Carlo** — de 10 mil a 100 milhões de cenários (em segundo plano, com progresso, tempo
restante e botão Parar). Até 200 mil os percentis são exatos; acima, vêm de um histograma de 20 mil
faixas (erro menor que 0,1%), então a memória não cresce. Horizonte padrão de 40 anos (até 60).

**Janela histórica** — em anos (5 a 40), com dados desde 1995 (Plano Real). Ativos mais novos que a
janela: por padrão a contagem começa no ativo mais antigo de cada carteira e os mais novos **entram
quando passam a existir** (antes disso, o peso deles é dividido entre os demais). O app avisa quais
ativos são mais novos, desde quando, e com que parte dos pesos cada carteira começa. Alternativas:
cortar a janela no ativo mais novo ou estimar os anos faltantes por modelo de fatores.

**Siglas** — explicadas entre parênteses nos rótulos e num glossário na aba Metodologia.

**Versão Python** (`python/`) — milhões de cenários, 7 modelos (inclui GARCH e mudança de regime),
otimização por meta com restrição de queda, mínimo CVaR, relatório HTML. Veja `python/README.md`.

## Dados

- **TradingView**: lista de ativos pelo scanner e histórico mensal com ajuste de dividendos
  (mesmo protocolo de websocket dos projetos open source tvdatafeed e TradingView-API).
- **CVM (dados abertos)**: cota diária dos fundos desde 2012 (`tools/fundos_cvm.py`).
- **Banco Central**: CDI (SGS 4391) e IPCA (SGS 433).

Para atualizar (no computador, com internet; leva uns 10 minutos):

```
pip install websocket-client
python3 tools/fundos_cvm.py tools/cache              # fundos (baixa ~1,3 GB da CVM)
python3 tools/fiis_cvm.py tools/cache                # mandato, segmento e P/VP dos FIIs
python3 tools/baixar_dados.py tools/cache            # resto do universo e monta data.js
python3 tools/complementar_historico.py tools/cache  # começo do histórico pelo Yahoo + Fundamentus
```

Depois troque a versão do cache em `sw.js` e gere o APK de novo.

## Limites

O futuro pode ser diferente do passado. A simulação não considera impostos, taxas ou custos dos
fundos e rebalanceia a carteira todo mês. Não é recomendação de investimento.
