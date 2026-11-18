# Simulador Martins para Windows

Programa de computador com a mesma interface do app (Carteiras, Simulação, Histórico, Análise,
Otimizar, Metodologia), numa janela própria, sem internet, usando **todos os núcleos do processador**
no Monte Carlo (um Web Worker por núcleo).

## Usar

Baixe `SimuladorMartins-Portatil-<versão>.zip`, extraia a pasta e abra **Simulador Martins.exe**.
Não precisa instalar. Na primeira vez o Windows pode mostrar "O Windows protegeu o computador"
(o programa não tem assinatura digital paga): clique em **Mais informações → Executar assim mesmo**.
As carteiras e os ajustes ficam salvos no próprio computador.

## Gerar de novo

Com Node.js 20+:

```
cd desktop
npm install
npm run build        # gera dist/SimuladorMartins-Portatil-<versão>.zip (e o instalador, se houver Windows/Wine)
npm start            # abre o programa direto, para testar
```

`copiar-web.js` copia a versão web (index.html, app.js, data.js...) para dentro do programa;
`icone.js` grava o ícone e a versão no .exe sem precisar do Windows.

Para o motor Python (milhões de cenários, relatórios), use `python/Simulador Python (Windows).bat`.
