# Martins Burger

App de pedidos de lanchonete **para celular**, no estilo dos apps de fast-food: escolhe o restaurante,
monta o lanche, pega cupons, junta pontos ("brasas") e acompanha o pedido até a retirada.

Versão 1.0 de **demonstração**: os pedidos e o pagamento são simulados e tudo fica salvo só no próprio celular
(sem login, sem servidor).

## Abrir no celular

App web instalável, igual ao Finanças Martins:

- **Android (Chrome)**: abra o endereço e toque em **Instalar app** / **Adicionar à tela inicial**.
- **iPhone (Safari)**: **Compartilhar › Adicionar à Tela de Início**.

Endereço (depois que a pasta estiver no branch publicado do GitHub Pages):
https://felipemartins082006-cmyk.github.io/oculos-app/burger-martins/

## Telas

- **Início**: restaurante escolhido (aberto/fechado, distância), pedido em andamento, atalhos de ofertas
  (R$ 1,99 · Sexta Brasa · R$ 5 · R$ 15), carrossel de banners, "Mais pedidos" e o cardápio completo por categoria.
- **Produto**: tamanho, sabor, combo (médio/grande + bebida), tirar ingredientes, adicionais e quantidade.
- **Cupons**: busca, destaques em carrossel e seções por faixa de preço. "Pegar cupom" coloca na sacola.
- **Clube**: saldo de brasas, extrato, abas que acompanham a rolagem (Experiências, Sugestões, Até 3.000/6.000/10.000)
  e troca de brasas por produtos, sorteio ou brasas em dobro. Missões dão brasas extras.
- **Minha conta**: "Olá, nome", ID com copiar, QR code (toque para ampliar), perfil, missões, meus pedidos,
  restaurantes, perguntas frequentes, fale conosco e apagar dados.
- **Sacola**: editar itens, retirada no balcão / drive-thru / mesa, pagamento (Pix, cartão ou no restaurante — simulado),
  descontos e brasas que vai ganhar.
- **Seu pedido**: andamento (recebido → preparando → pronto → entregue) com linha do tempo, número de retirada,
  nº do pedido com copiar, itens, local de retirada, ajuda e "Pedir de novo".

## Regras de pontos

- 10 brasas por R$ 1 pago + bônus de cada cupom. 2.000 brasas de boas-vindas.
- Na demonstração o pedido fica "Preparando" depois de ~12 s e "Pronto" depois de ~75 s.

## Trocar cardápio, preços e lojas

Tudo está em `data.js` (restaurantes, produtos, cupons, banners, prêmios, missões, perguntas).
Os desenhos dos lanches são SVG próprios, em `art.js`.
