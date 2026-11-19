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

**Carteiras** — quantas carteiras quiser, comparadas lado a lado, montadas a partir de 11.387 ativos
(só dados reais: nada é estimado, completado por modelo ou trocado por índice):

| Classe | Ativos |
|---|---|
| Ações da B3 (todas com negociação; ON, PN e units) | 381 |
| BDRs (empresas de fora negociadas na B3, em reais) | 826 |
| FIIs, Fiagro e FI-Infra (todos com negociação) | 400 |
| ETFs da B3 (ações, renda fixa, Tesouro, S&P, cripto, ouro) | 212 |
| Tesouro Direto (Selic, IPCA+, Prefixado, Renda+, Educa+; preço oficial) | 42 |
| Fundos da CVM, cotas desde 2000 (816 renda fixa, 926 multimercado, 659 ações, 35 cambial) | 2.436 |
| Ações dos EUA (até ~US$ 150 milhões de valor de mercado) | 4.407 |
| ETFs dos EUA (sem alavancados nem inversos) | 1.980 |
| Criptoativos (índice agregado do TradingView, sem stablecoins nem tokens "embrulhados") | 623 |
| Índices (B3, EUA e 29 outras bolsas em dólar, mercado cripto total) | 57 |
| Commodities (metais, energia, agrícolas, boi gordo) e moedas | 23 |

**Explorar** — todos os ativos num mapa interativo antes de montar a carteira: mapa 2D (com zoom,
arrasto e retícula que mostra os valores nos eixos), nuvem 3D girável (três métricas ao mesmo tempo)
e ranking ordenável. Eixos e cor à escolha (retorno, volatilidade, Sharpe, queda máxima, anos de
histórico, correlação com Ibovespa e S&P 500, pior/melhor mês, meses positivos, tamanho). Passe o
cursor ou o dedo para ver cada ativo; toque para abrir o gráfico dele, comparar com outros e
adicionar à carteira.

Ativos novos entram a partir de 3 meses de histórico: na simulação cada um passa a contar no mês
em que começa (antes disso, o peso dele vai para os demais).

Mais renda fixa por modelo: CDI × %, IPCA + taxa e prefixado. Há 11 modelos prontos (Ibovespa,
S&P 500, Global 60/40, Permanente, Big Techs, FIIs, top multimercados etc.).

**Simulação (Monte Carlo)**
- 5 modelos de retorno: bootstrap mensal, bootstrap em blocos (qualquer tamanho, em meses), bootstrap
  estacionário (blocos de tamanho aleatório), normal multivariada
  e t-Student (caudas gordas).
- Mesmos sorteios para todas as carteiras (comparação justa) e para o CDI de referência.
- Plano: aporte inicial e mensal, correção pelo IPCA, aumento real do aporte, meta.
- Aposentadoria: saques mensais a partir de um ano, com chance de o dinheiro acabar.
- Rebalanceamento mensal, trimestral, anual ou nunca; câmbio convertido ou ignorado;
  ajuste no retorno esperado; custo anual extra; IR no resgate; valores reais ou nominais.
- Resultados: percentis, chance de perda / de bater o CDI / de atingir a meta, retorno anual e
  queda máxima por cenário, leque de futuros, distribuição final e **montanha 3D de probabilidades**
  (a distribuição do patrimônio ano a ano, feita das trajetórias simuladas, girável, com a fração de
  cenários de cada faixa sob o cursor). Aviso automático quando a janela histórica é curta.
- Todos os gráficos seguem o cursor ou o dedo (valores do mês, da faixa ou do ponto apontado).

**Histórico (backtest)** — evolução real das carteiras contra CDI, IPCA, Ibovespa e S&P 500 em
reais; drawdown; retorno, volatilidade, Sharpe, Sortino, VaR/CVaR, beta, correlação; retorno ano a ano.

**Análise** — contribuição de cada ativo para o risco, matriz de correlação e fronteira eficiente
(máximo Sharpe, mínimo risco e "mesmo risco, mais retorno"), com botão para aplicar os pesos.

**Otimizar** — escolhe a melhor carteira num universo filtrado (ex.: todos os FIIs de papel com 5+
anos) ou nos ativos de uma carteira: máximo Sharpe, mínimo risco, paridade de risco ou risco-alvo;
médias James-Stein (encolhidas para a média da própria classe), covariância Ledoit-Wolf, reamostragem
50× em todos os núcleos, limite de peso e de nº de ativos, retorno ao ano (acima de / igual a / entre)
e queda máxima tolerada; com várias classes marcadas, **pelo menos 1 ativo de cada classe** (o melhor
dela para o objetivo e os limites); teste fora da amostra; salva o resultado como carteira.

**FIIs** — mandato (papel, tijolo, híbrido), segmento, P/VP e cotistas pelo informe mensal da CVM;
filtros "FIIs de papel/tijolo/híbridos" no seletor e no otimizador.

**Monte Carlo** — de 10 mil a 100 milhões de cenários (em segundo plano, com progresso, tempo
restante e botão Parar). Até 200 mil os percentis são exatos; acima, vêm de um histograma de 20 mil
faixas (erro menor que 0,1%), então a memória não cresce. Horizonte padrão de 40 anos (até 60).

**Janela histórica** — em anos (qualquer número, ou o máximo), com dados desde 1995 (Plano Real). Ativos mais novos que a
janela: por padrão a contagem começa no ativo mais antigo de cada carteira e os mais novos **entram
quando passam a existir** (antes disso, o peso deles é dividido entre os demais). O app avisa quais
ativos são mais novos, desde quando, e com que parte dos pesos cada carteira começa. Alternativa:
cortar a janela no ativo mais novo. **Só dados reais**: nada é estimado, completado por modelo ou
substituído por índice; meses sem cotação de nenhum ativo de uma carteira ficam fora da amostra, e a
análise segue com os meses que existem, por menos que sejam.

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
