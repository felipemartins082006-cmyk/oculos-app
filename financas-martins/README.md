# Finanças Martins

App web de finanças pessoais. Sem login: os dados ficam salvos só no navegador do aparelho.

## O que faz

- **Importar extratos** de vários bancos de uma vez: diga quantos bancos você tem, dê o nome de
  cada um e escolha um ou mais arquivos por banco (PDF, OFX, CSV, TXT, XLS/XLSX).
  Antes de salvar, uma tela de conferência mostra tudo o que foi lido: dá para desmarcar linhas,
  trocar a categoria e inverter os sinais (útil em fatura de cartão). Lançamentos que já existiam
  vêm desmarcados, para não duplicar.
  - PDF com senha (comum em banco) pede a senha na hora.
  - PDF escaneado (imagem) não tem texto e não dá para ler. Nesse caso, exporte OFX ou CSV no app do banco.
- **Registro manual**: botão "Registrar", digite o valor e o que foi. O dia e a hora são anotados
  automaticamente, e a categoria é sugerida pela descrição.
- **Panorama interativo**:
  - entradas, saídas, quanto sobrou e patrimônio, comparados com o período anterior;
  - destaques automáticos (maior categoria, gasto médio por dia, maior compra, delivery, tarifas…);
  - evolução do patrimônio;
  - para onde vai o dinheiro (toque numa categoria para ver o detalhe mês a mês);
  - entradas × saídas por mês (toque num mês para filtrar o painel inteiro);
  - estabelecimentos onde você mais gasta, gasto por dia da semana;
  - gastos fixos e assinaturas detectados sozinhos;
  - filtro por período e por conta.
- **Ajustes**: saldo inicial de cada conta, backup (JSON), exportar planilha (CSV), apagar tudo.

Ao mudar a categoria de um lançamento, o app oferece aplicar a mesma categoria aos outros do
mesmo estabelecimento e passa a usá-la nas próximas importações.

## Rodar

São só arquivos estáticos. Abra com qualquer servidor:

    cd financas-martins && python3 -m http.server 8000

e acesse http://localhost:8000. No GitHub Pages, fica em `/financas-martins/`.

## Arquivos

- `index.html`, `styles.css`: telas e visual (tema claro e escuro, feito para celular primeiro)
- `app.js`: dados, painel, registro manual, importação e ajustes
- `parsers.js`: leitura dos extratos (OFX, CSV/TXT, Excel via SheetJS, PDF via pdf.js)
