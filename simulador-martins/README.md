# Simulador Martins

Simulação **Monte Carlo** de carteiras com ações, FIIs e ETFs da B3, feita para celular.
Funciona sem internet depois da primeira abertura e sem login. A carteira e o plano ficam salvos só no aparelho.

Endereço (depois de publicado no GitHub Pages):
https://felipemartins082006-cmyk.github.io/oculos-app/simulador-martins/

## O que faz

1. Você monta a carteira (81 ativos: 43 ações, 33 FIIs, 4 ETFs e o Ibovespa) e os pesos de cada um.
2. Define o valor inicial, o aporte mensal, o prazo e uma meta opcional.
3. O app sorteia, para cada mês do futuro, um mês real do passado (o mesmo para todos os ativos)
   e repete isso 1.000, 5.000 ou 10.000 vezes.
4. Mostra o resultado mediano, a faixa de "8 em cada 10 futuros", a chance de perder dinheiro, de
   ganhar do CDI e de bater a meta, o leque de futuros ano a ano e a distribuição final.

Opções: valores em dinheiro de hoje (descontando o IPCA), aporte corrigido pela inflação, janela do
histórico (tudo / desde 2015 / desde 2020) e sorteio mês a mês ou em blocos de 12 meses.

## Dados

- **Preços**: Yahoo Finance, fechamento mensal desde 2010.
- **Proventos** (dividendos, JCP, rendimentos de FII): Fundamentus. O Yahoo perde muitos proventos:
  FIIs não têm nenhum antes de 2022, e o Itaú aparece sem dividendos em 2024-25. Por isso o retorno
  total é montado no script. JCP entra líquido de 15% de IR.
- **CDI e IPCA**: Banco Central (SGS 4391 e 433).
- FIIs só têm proventos confiáveis desde 2017, então carteiras com FIIs usam essa janela.
- Meses com dado claramente quebrado (desdobramento não ajustado) são descartados.

Para atualizar os dados (no computador, com internet):

```
python3 tools/baixar_dados.py
```

O script reescreve `data.js`. Para incluir um ativo, acrescente na lista `ATIVOS` do script.
Depois de atualizar, troque a versão do cache em `sw.js` (`simulador-v1` → `simulador-v2`).

`python3 tools/montar_pagina_unica.py saida.html` gera uma versão em arquivo único da página.

## Próximo passo: EUA

Cada ativo em `data.js` já tem `mercado` e `moeda`. Para abrir para os EUA, falta baixar os
tickers americanos (sem `.SA` no Yahoo, com os dividendos já ajustados) e converter os retornos para
reais com o dólar (PTAX, SGS 3698) ou simular em dólar com a inflação americana.

## Limites

O futuro pode ser diferente do passado. A simulação não considera impostos, taxas ou custos dos
fundos e rebalanceia a carteira todo mês. Não é recomendação de investimento.
