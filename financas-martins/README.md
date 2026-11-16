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

## Instalar no iPhone (iOS)

No iPhone o app funciona como app web instalável (não precisa de App Store nem de conta de desenvolvedor):

1. Abra no **Safari**: https://felipemartins082006-cmyk.github.io/oculos-app/financas-martins/
2. Toque em **Compartilhar** (quadrado com a seta para cima) › **Adicionar à Tela de Início**.
3. Abra pelo ícone "Finanças": ele roda em tela cheia, sem a barra do Safari, e funciona sem internet
   depois da primeira abertura.

Diferenças em relação ao Android:
- **Salvar backup** e **Exportar tudo** abrem a folha de compartilhar do iPhone: dá para escolher
  "Salvar em Arquivos", Google Drive, WhatsApp, e-mail etc.
- O **backup automático semanal** vira um **lembrete semanal** (o iPhone não deixa um app web gravar
  sozinho numa pasta).
- Use sempre pelo ícone da Tela de Início: no Safari comum, o iOS pode apagar dados de sites que você
  não abre há 7 dias. Mesmo assim, faça backup.

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
- **Sua renda** (atalho "Renda" no Início, ou em Mais): cadastre salário, outras fontes, rendimento
  dos investimentos (valor investido × % ao mês) e VR/VA. Cada fonte escolhe como conta: **todo dia**
  (valor do mês ÷ dias daquele mês, 30 ou 31), **no dia do pagamento** (inteiro, num dia escolhido) ou
  **não contar** (só registrado, ex.: rendimento que fica investido). As entradas aparecem sozinhas no
  Extrato (marcadas "automática") e entram nos gráficos, no patrimônio e na planilha. Se a mesma renda
  também vier num extrato importado, o app avisa e pergunta qual manter, para não contar duas vezes.
- **Formas de pagamento**: ao registrar um gasto, o app pergunta "Como você pagou?" (Pix, Débito,
  Crédito, Dinheiro, Vale-refeição, Vale-alimentação, Boleto, TED, Desconto em folha) e, conforme a
  forma, "De qual banco?", "Qual cartão de crédito?" ou "Qual vale?", com "+ Novo" para cadastrar na
  hora. Ele lembra a última forma e o último banco usados. Em Mais › Formas de pagamento você liga só
  as que usa e cria as suas (ex.: "Contracheque"), dizendo se elas perguntam banco, cartão ou vale.
- **Saldo do dia (cada renda separada)**: salário, VR, rendimentos e outras fontes viram saldos
  separados no Início. Todo dia entra a parte de cada um e saem os gastos daquele saldo; o que sobra
  (ou falta) passa para o dia seguinte. Ex.: ganha 70, gasta 50 → sobra 20, e amanhã fica 70 + 20 = 90.
  Gastos na conta/cartão do VR saem do saldo do VR; os demais saem do Salário. Dá para escolher o saldo
  em cada lançamento (ou em vários de uma vez). Tocar num saldo mostra o dia a dia: veio de ontem,
  entrou, saiu e fechou.
- **Painéis**: toque no nome no topo (ex.: "Meu painel ▾") para trocar de painel ou criar um novo do
  zero. Cada painel tem suas próprias contas, lançamentos e gráficos (ex.: um para você, outro para a
  namorada). Dá para renomear e excluir.
- **Backup**: "Salvar backup agora" gera um arquivo com tudo (lançamentos manuais e importados, contas
  e todos os painéis). "Restaurar um backup" volta tudo num celular novo. Com o **backup automático
  semanal** ligado, no APK você escolhe uma pasta uma vez e o app grava um backup novo nela a cada 7
  dias (ao abrir o app). No navegador, ele lembra de salvar a cada 7 dias. Sem backup há mais de 7
  dias, aparece um aviso no Início.
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
