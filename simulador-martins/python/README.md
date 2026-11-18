# Simulador Martins — versão Python

A versão "macro" do simulador do celular: a mesma base de dados (1.942 ativos), mas com Monte Carlo
de **milhões de cenários**, **7 modelos de retorno**, **otimizador de carteiras** com métodos robustos,
teste fora da amostra e **relatório HTML** completo.

## Instalar

**No Windows, o jeito fácil:** dê dois cliques em `Simulador Python (Windows).bat`. Na primeira vez ele
prepara o ambiente (precisa do Python de python.org, com "Add python.exe to PATH" marcado) e depois
mostra um menu: rodar os estudos de exemplo, um estudo seu, uma simulação gigante ou atualizar os dados.
O relatório abre no navegador ao terminar.

Manualmente:

Python 3.10 ou mais novo.

```
cd simulador-martins/python
pip install -r requirements.txt
```

A base de dados é o `../data.js` (o mesmo arquivo do app). Para baixar tudo de novo:
`python -m simulador atualizar` (TradingView, CVM, Banco Central, Yahoo + Fundamentus; uns 15 minutos).

## Usar

```
python -m simulador info                                  # o que tem na base
python -m simulador buscar --tipo fii --mandato papel     # FIIs de papel
python -m simulador buscar logística --tipo fii
python -m simulador simular exemplos/fiis_papel.json --cenarios 100000000   # 100 milhões (~1 h)
python -m simulador estudo exemplos/fiis_papel.json       # otimiza, simula, testa e gera o relatório
python -m simulador validar-proxy                         # qualidade da extensão de histórico
```

O **estudo** lê um JSON com: as suas carteiras, as otimizações a fazer, o plano (aportes, meta,
saques), a simulação (modelo, nº de cenários, janela) e os modelos de robustez. Veja `exemplos/`:

- `fiis_papel.json`: qual a melhor carteira de FIIs de papel (universo: todos os FIIs de papel com
  5+ anos), comparada com a sua, com o IFIX e com o CDI.
- `melhor_carteira_global.json`: qual a carteira com mais chance de chegar a R$ 1 milhão, com queda
  aceitável de até 20%, num universo de ETFs da B3 e dos EUA, ouro, Treasuries, IFIX e CDI.

## O que tem dentro

| Módulo | O que faz |
|---|---|
| `dados.py` | Carrega a base (retornos mensais com proventos), metadados, busca; acrescenta qualquer símbolo do TradingView na hora |
| `coleta.py` | Acesso direto ao TradingView (histórico mensal/diário com ajuste de dividendos, scanner) e ao SGS do Banco Central |
| `modelos.py` | Bootstrap, blocos, bootstrap estacionário (Politis-Romano), normal, t-Student, GARCH(1,1) + simulação histórica filtrada, mudança de regime (cadeia de Markov oculta, EM) |
| `monte_carlo.py` | Simulação vetorizada em lotes; aportes, correção pelo IPCA, aumento real, saques/aposentadoria, custos, IR, rebalanceamento, câmbio, ajuste de retorno; números aleatórios comuns entre carteiras |
| `otimizacao.py` | Máx. Sharpe, mín. variância, risco-alvo, paridade de risco, mín. CVaR (programação linear), máx. Sortino, utilidade; média James-Stein e covariância Ledoit-Wolf; reamostragem de Michaud; escolha por meta com Monte Carlo e restrições de risco; teste fora da amostra |
| `proxy.py` | Estende históricos curtos por modelo de fatores (IFIX, CDI, IPCA, Ibovespa, S&P) com validação fora da amostra |
| `backtest.py` | Backtest com Sharpe, Sortino, drawdown, VaR/CVaR, beta, retorno por ano |
| `relatorio.py` | Relatório HTML de arquivo único com gráficos |

Desempenho medido (bootstrap, 3 carteiras, 40 anos): 1 milhão de cenários em 39 s com 1 núcleo e
10 s com 4 núcleos — a simulação usa todos os núcleos do PC (`processos=` limita). Num PC de 8 a 16
núcleos, 100 milhões levam de alguns minutos a ~15 min. Até 2 milhões os percentis são exatos; acima, vêm de histogramas de 50 mil
faixas (diferença medida contra o exato: menos de 0,2%), com memória constante. As probabilidades
(perda, superar o CDI, meta) são contadas cenário a cenário. Estudo completo com 200 mil cenários,
6 modelos de robustez e otimizações em ~1 min.

**Janela e ativos novos** (`"simulacao"`): `"desde": 1995` ou `"janela_anos": 20`; `"curto"`:
`"redistribuir"` (padrão: cada ativo entra quando passa a existir e, antes disso, o peso dele vai
para os demais), `"cortar"` (só meses em que todos existem) ou `"estimar"` (modelo de fatores).
O relatório avisa quais ativos são mais novos e com que parte dos pesos cada carteira começa.

## Cuidados

- Otimizar com o passado favorece a carteira escolhida. Por isso o relatório traz o **teste fora da
  amostra** (otimiza só com os anos antigos e mede nos recentes) e a robustez entre modelos.
- Renda fixa "por modelo" (prefixado, IPCA+) não tem marcação a mercado, então parece sem risco:
  não use em otimização por Sharpe. Prefira ETFs/fundos reais de renda fixa.
- A janela histórica comum é limitada pelo ativo mais novo. A extensão por fatores (`"estender": true`)
  ajuda, mas os meses estimados não são dados reais (erro mediano de ~4 p.p. no retorno anual).
- Material educativo, não é recomendação de investimento.
