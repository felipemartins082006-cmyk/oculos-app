# Finanças Martins

App de finanças pessoais **só para celular**. Qualquer pessoa usa sem login nem cadastro: os dados ficam salvos só no próprio celular.

É um app web instalável: abra o endereço no celular e toque em **Instalar** (Android/Chrome) ou em
**Compartilhar → Adicionar à Tela de Início** (iPhone/Safari). Depois ele abre pelo ícone, em tela cheia, como aplicativo.

## Instalar no Android (APK)

O arquivo **`FinancasMartins.apk`** desta pasta é o app pronto. Passe para o celular, abra e toque em
Instalar (o Android pede para permitir "instalar apps desconhecidos" na primeira vez).

- Não usa internet: o app nem tem permissão de internet. Tudo fica no celular.
- As bibliotecas de PDF, Excel e gráficos vão dentro do APK.
- Os dados entram no backup do Google do próprio celular. Em Ajustes também dá para salvar um
  backup manual (o Android pergunta onde guardar).
- Versões novas instalam por cima da anterior sem perder os dados (a chave de assinatura é fixa).

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

## Compilar o APK

O projeto Android fica em `android/`. Ele mostra as telas desta pasta num WebView e, a cada
compilação, copia `index.html`, `styles.css`, `app.js`, `parsers.js` e `vendor/` para dentro do APK.
Ou seja: mexeu na versão web, é só compilar de novo.

    cd android && ./gradlew assembleRelease

O APK sai em `android/app/build/outputs/apk/release/app-release.apk`.

## Rodar no navegador (para testar)

São só arquivos estáticos. Abra com qualquer servidor:

    cd financas-martins && python3 -m http.server 8000

e acesse http://localhost:8000. No GitHub Pages, fica em `/financas-martins/`.

## Arquivos

- `index.html`, `styles.css`: telas e visual (só celular: abas embaixo, uma coluna, tema claro e escuro)
- `manifest.webmanifest`, `sw.js`, `icon-*.png`: instalação na tela inicial e abertura sem internet
- `app.js`: dados, painel, registro manual, importação e ajustes
- `parsers.js`: leitura dos extratos (OFX, CSV/TXT, Excel via SheetJS, PDF via pdf.js)
- `vendor/`: Chart.js, pdf.js e SheetJS embutidos (funciona sem internet)
- `android/`: projeto do APK (Kotlin + WebView)
