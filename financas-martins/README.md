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

- **Importar extratos** de conta corrente, cartão de crédito, vale-refeição e vale-alimentação.
  Para cada extrato você escolhe a instituição (Nubank, Inter, Santander, Itaú, Bradesco, BB, Caixa,
  C6, PicPay, Mercado Pago, BTG, VR, Alelo, Ticket, Pluxee, Flash, Caju, iFood Benefícios, Swile ou
  outra) e o tipo. Cada combinação vira uma conta separada (ex.: "Nubank · Crédito", "VR · VR").
  - Formatos: PDF, OFX, CSV, TXT, XLS/XLSX. Vários arquivos por conta.
  - Dica de como baixar o extrato em cada caso e uma planilha modelo para quem não consegue exportar
    (comum em app de benefício).
  - Fatura de cartão: as compras viram saídas automaticamente. Extrato com coluna "Tipo"
    (Débito/Crédito) também é entendido. Quando o extrato traz saldo, o saldo da conta já vem certo.
  - Tela de conferência antes de salvar, com lançamentos repetidos desmarcados.
- **Registro manual** com teclado numérico próprio: valor, descrição, categoria e conta. Dia e hora
  são anotados sozinhos.
- **Início**: patrimônio total, cartões de cada conta (toque para filtrar), destaques, e gráficos:
  para onde vai o dinheiro (rosca), gastos por conta (rosca), fixos × variáveis (pizza), quanto você
  guardou (medidor), de onde vem o dinheiro (pizza), entradas × saídas por mês, patrimônio no tempo,
  categorias mês a mês, gasto por dia da semana, onde você mais gasta e gastos fixos/assinaturas.
- **Extrato com seleção**: pressione e segure um lançamento (ou toque em "Selecionar") para marcar
  vários. Na barra de baixo: **Desligar/Ligar** (fica salvo, mas sai de saldos, gráficos e totais),
  **Atualizar** (categoria, conta, data ou inverter entrada/saída de todos de uma vez), **Editar**
  (quando só um está marcado) e **Excluir**. Tocar normalmente continua abrindo a edição.
- **Exportar tudo em um arquivo**: planilha Excel com a aba Resumo, uma aba para cada conta, cartão
  e vale, e uma aba com todos os lançamentos.
- **Mais**: contas (nome, tipo, saldo inicial), backup, restaurar, CSV simples, apagar tudo.

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
