// Cardápio, restaurantes, cupons, prêmios e missões.
// Para usar com uma lanchonete de verdade, é só trocar os dados deste arquivo.

const BRAND = { name: 'Martins Burger', points: 'brasas', pointsOne: 'brasa', perReal: 10 };

const STORES = [
  { id: 'paulista', name: 'MARTINS BURGER · PAULISTA', addr: 'Av. Paulista, 1500 · Loja 12', area: 'Bela Vista · São Paulo · SP', lat: -23.5614, lng: -46.6559, open: '10:00', close: '23:00' },
  { id: 'moema', name: 'MARTINS BURGER · MOEMA', addr: 'Av. Ibirapuera, 2200 · Piso Térreo', area: 'Moema · São Paulo · SP', lat: -23.6015, lng: -46.6640, open: '11:00', close: '22:00' },
  { id: 'pinheiros', name: 'MARTINS BURGER · PINHEIROS', addr: 'R. dos Pinheiros, 800', area: 'Pinheiros · São Paulo · SP', lat: -23.5650, lng: -46.6880, open: '11:00', close: '24:00' },
  { id: 'tatuape', name: 'MARTINS BURGER · TATUAPÉ 24H', addr: 'R. Tuiuti, 1200', area: 'Tatuapé · São Paulo · SP', lat: -23.5400, lng: -46.5760, open: '00:00', close: '24:00' }
];

const CATEGORIES = [
  { id: 'lanches', name: 'Lanches' },
  { id: 'frango', name: 'Frango' },
  { id: 'acomp', name: 'Acompanhamentos' },
  { id: 'bebidas', name: 'Bebidas' },
  { id: 'sobremesas', name: 'Sobremesas' }
];

const DRINK_FLAVORS = [
  { id: 'cola', name: 'Cola' },
  { id: 'zero', name: 'Cola Zero' },
  { id: 'guarana', name: 'Guaraná' },
  { id: 'laranja', name: 'Laranja' },
  { id: 'limao', name: 'Limão' }
];

const COMBOS = [
  { id: 'none', name: 'Só o lanche', add: 0 },
  { id: 'm', name: 'Combo médio', desc: 'Batata média + refri 500 ml', add: 14 },
  { id: 'g', name: 'Combo grande', desc: 'Batata grande + refri 700 ml', add: 18 }
];

const EXTRAS = {
  carne: { id: 'carne', name: 'Carne extra', add: 7 },
  queijo: { id: 'queijo', name: 'Queijo extra', add: 3 },
  bacon: { id: 'bacon', name: 'Bacon extra', add: 4 },
  molho: { id: 'molho', name: 'Molho da casa extra', add: 2 }
};

// art: [tipo, ...parâmetros] — ver art.js
const PRODUCTS = [
  { id: 'classico', cat: 'lanches', name: 'Brasa Clássico', price: 19.9, kcal: 560,
    desc: 'Hambúrguer de 120 g grelhado na brasa, queijo prato, alface, tomate e molho da casa no pão com gergelim.',
    art: ['burger', ['patty', 'cheese', 'tomato', 'lettuce', 'sauce']],
    ingredients: ['Queijo prato', 'Alface', 'Tomate', 'Molho da casa'], extras: ['carne', 'queijo', 'bacon', 'molho'], combo: true, best: true },
  { id: 'duplo', cat: 'lanches', name: 'Duplo Queijo', price: 14.9, kcal: 450,
    desc: 'Dois hambúrgueres, duas fatias de queijo, picles, cebola, ketchup e mostarda.',
    art: ['burger', ['patty', 'cheese', 'patty', 'cheese', 'pickle', 'onion']],
    ingredients: ['Queijo', 'Picles', 'Cebola', 'Ketchup', 'Mostarda'], extras: ['carne', 'queijo', 'bacon'], combo: true },
  { id: 'bacon', cat: 'lanches', name: 'Brasa Bacon', price: 26.9, kcal: 720,
    desc: 'Hambúrguer de 150 g, cheddar derretido, bacon crocante, alface, tomate e maionese defumada.',
    art: ['burger', ['patty', 'cheese', 'bacon', 'tomato', 'lettuce']],
    ingredients: ['Cheddar', 'Bacon', 'Alface', 'Tomate', 'Maionese defumada'], extras: ['carne', 'queijo', 'bacon'], combo: true, best: true },
  { id: 'smash', cat: 'lanches', name: 'Smash Martins', price: 22.9, kcal: 640,
    desc: 'Dois smash burgers de 80 g bem selados, queijo cheddar, cebola caramelizada e molho da casa.',
    art: ['burger', ['patty', 'cheese', 'patty', 'cheese', 'onion', 'sauce']],
    ingredients: ['Cheddar', 'Cebola caramelizada', 'Molho da casa'], extras: ['carne', 'queijo', 'bacon', 'molho'], combo: true },
  { id: 'cheese', cat: 'lanches', name: 'Cheese Simples', price: 9.9, kcal: 300,
    desc: 'Hambúrguer, queijo, picles, ketchup e mostarda. O clássico que cabe no bolso.',
    art: ['burger', ['patty', 'cheese', 'pickle']],
    ingredients: ['Queijo', 'Picles', 'Ketchup', 'Mostarda'], extras: ['carne', 'queijo', 'bacon'], combo: true },
  { id: 'veggie', cat: 'lanches', name: 'Veggie da Horta', price: 21.9, kcal: 480,
    desc: 'Hambúrguer de grão-de-bico e legumes, queijo, alface, tomate e maionese de ervas.',
    art: ['burger', ['veg', 'cheese', 'tomato', 'lettuce']],
    ingredients: ['Queijo', 'Alface', 'Tomate', 'Maionese de ervas'], extras: ['queijo', 'molho'], combo: true },
  { id: 'crispy', cat: 'frango', name: 'Crispy Chicken', price: 21.9, kcal: 590,
    desc: 'Filé de frango empanado e crocante, alface, tomate e maionese no pão com gergelim.',
    art: ['burger', ['chicken', 'tomato', 'lettuce', 'sauce']],
    ingredients: ['Alface', 'Tomate', 'Maionese'], extras: ['queijo', 'bacon', 'molho'], combo: true, best: true },
  { id: 'nug6', cat: 'frango', name: 'Nuggets 6 unidades', price: 14.9, kcal: 270,
    desc: 'Pedaços de frango empanados. Acompanha um molho à sua escolha.',
    art: ['nuggets', 6], combo: true },
  { id: 'nug10', cat: 'frango', name: 'Nuggets 10 unidades', price: 21.9, kcal: 450,
    desc: 'Dez pedaços de frango empanados, crocantes por fora e macios por dentro. Acompanha dois molhos.',
    art: ['nuggets', 10], combo: true },
  { id: 'batata', cat: 'acomp', name: 'Batata Frita', price: 8.9, kcal: 230,
    desc: 'Batatas fritas douradas e sequinhas, com sal na medida.',
    art: ['fries'], sizes: [{ id: 'p', name: 'Pequena', add: 0 }, { id: 'm', name: 'Média', add: 3 }, { id: 'g', name: 'Grande', add: 5 }], best: true },
  { id: 'rings', cat: 'acomp', name: 'Onion Rings', price: 12.9, kcal: 330,
    desc: 'Anéis de cebola empanados e crocantes.', art: ['rings'] },
  { id: 'salada', cat: 'acomp', name: 'Salada Fresh', price: 15.9, kcal: 120,
    desc: 'Mix de folhas, tomate-cereja e molho de mostarda e mel.', art: ['salad'] },
  { id: 'refri', cat: 'bebidas', name: 'Refrigerante', price: 7.9, kcal: 130,
    desc: 'Escolha o sabor e o tamanho.', art: ['drink', 'cola'],
    sizes: [{ id: '300', name: '300 ml', add: 0 }, { id: '500', name: '500 ml', add: 2 }, { id: '700', name: '700 ml', add: 4 }], flavors: true },
  { id: 'suco', cat: 'bebidas', name: 'Suco de Laranja', price: 10.9, kcal: 140,
    desc: 'Suco natural de laranja, feito na hora.', art: ['juice'] },
  { id: 'agua', cat: 'bebidas', name: 'Água Mineral', price: 4.9, kcal: 0,
    desc: 'Água mineral sem gás, 500 ml.', art: ['water'] },
  { id: 'cafe', cat: 'bebidas', name: 'Café Espresso', price: 6.9, kcal: 5,
    desc: 'Café espresso encorpado, de grãos 100% arábica.', art: ['coffee'] },
  { id: 'casquinha', cat: 'sobremesas', name: 'Casquinha Baunilha', price: 4.5, kcal: 160,
    desc: 'Sorvete de baunilha na casquinha crocante.', art: ['cone'], best: true },
  { id: 'casqmista', cat: 'sobremesas', name: 'Casquinha Mista', price: 4.9, kcal: 170,
    desc: 'Metade baunilha, metade chocolate.', art: ['cone', true] },
  { id: 'sundae', cat: 'sobremesas', name: 'Sundae Chocolate', price: 11.9, kcal: 330,
    desc: 'Sorvete de baunilha com calda quente de chocolate.', art: ['sundae'] },
  { id: 'shake', cat: 'sobremesas', name: 'Milk-shake Morango', price: 15.9, kcal: 420,
    desc: 'Milk-shake cremoso de morango, 400 ml.', art: ['shake'] },
  { id: 'torta', cat: 'sobremesas', name: 'Torta de Maçã', price: 7.9, kcal: 250,
    desc: 'Massa crocante recheada com maçã e canela.', art: ['pie'] }
];

// Ofertas da tela inicial (os atalhos em cima dos banners).
const OFFER_GROUPS = [
  { id: 'r199', label: 'Oferta por', big: 'R$1,99', title: 'Ofertas por R$ 1,99' },
  { id: 'sexta', label: 'Sexta', big: 'Brasa', title: 'Sexta Brasa 🔥' },
  { id: 'r5', label: 'Ofertas por', big: 'R$5', title: 'Ofertas por R$ 5' },
  { id: 'r15', label: 'Ofertas por', big: 'R$15', title: 'Ofertas por R$ 15' }
];

// Cupons: cada cupom coloca itens na sacola por um preço fechado.
// bonus = brasas extras que o cupom dá. bg = cores do banner.
const COUPONS = [
  { id: 'c-duplo199', tags: ['r199', 'destaque'], title: 'Duplo Queijo', price: 1.99, from: 14.9, bonus: 199,
    items: [{ pid: 'duplo', opts: {} }], art: ['burger', ['patty', 'cheese', 'patty', 'cheese', 'pickle', 'onion']],
    rule: 'Válido das 18h às 20h. 1 por pedido. Não acumula com outras ofertas.', bg: ['#2B0F3A', '#B3261E'] },
  { id: 'c-casq199', tags: ['r199'], title: 'Casquinha Baunilha', price: 1.99, from: 4.5, bonus: 50,
    items: [{ pid: 'casquinha', opts: {} }], art: ['cone'], rule: '1 por pedido.', bg: ['#3A1A5E', '#E2557A'] },
  { id: 'c-sexta25', tags: ['sexta', 'destaque'], title: 'Brasa Clássico + Refri 300 ml', price: 25, from: 27.8, bonus: 250,
    items: [{ pid: 'classico', opts: {} }, { pid: 'refri', opts: { size: '300', flavor: 'cola' } }], art: ['burger', ['patty', 'cheese', 'tomato', 'lettuce', 'sauce']],
    rule: 'Toda sexta-feira. 1 por pedido.', bg: ['#1E1033', '#F26A1B'] },
  { id: 'c-sexta2smash', tags: ['sexta'], title: '2 Smash Martins', price: 34.9, from: 45.8, bonus: 350,
    items: [{ pid: 'smash', opts: {} }, { pid: 'smash', opts: {} }], art: ['burger', ['patty', 'cheese', 'patty', 'cheese', 'onion', 'sauce']],
    rule: 'Toda sexta-feira. 1 por pedido.', bg: ['#14213D', '#E85D04'] },
  { id: 'c-sextacombo', tags: ['sexta', 'destaque'], title: 'Combo Brasa Bacon médio', price: 32.9, from: 40.9, bonus: 400,
    items: [{ pid: 'bacon', opts: { combo: 'm', comboDrink: 'cola' } }], art: ['burger', ['patty', 'cheese', 'bacon', 'tomato', 'lettuce']],
    rule: 'Toda sexta-feira. Bebida pode ser trocada no balcão.', bg: ['#22092C', '#BE3144'] },
  { id: 'c-batata5', tags: ['r5'], title: 'Batata Média', price: 5, from: 11.9, bonus: 50,
    items: [{ pid: 'batata', opts: { size: 'm' } }], art: ['fries'], rule: '1 por pedido.', bg: ['#3D0C02', '#F26A1B'] },
  { id: 'c-torta5', tags: ['r5'], title: 'Torta de Maçã', price: 5, from: 7.9, bonus: 50,
    items: [{ pid: 'torta', opts: {} }], art: ['pie'], rule: '1 por pedido.', bg: ['#2C1A0E', '#D9822B'] },
  { id: 'c-sundae5', tags: ['r5'], title: 'Sundae Chocolate', price: 5, from: 11.9, bonus: 50,
    items: [{ pid: 'sundae', opts: {} }], art: ['sundae'], rule: '1 por pedido.', bg: ['#1B1B3A', '#8B5E34'] },
  { id: 'c-nug15', tags: ['r15'], title: 'Nuggets 10 + Refri 500 ml', price: 15, from: 31.8, bonus: 150,
    items: [{ pid: 'nug10', opts: {} }, { pid: 'refri', opts: { size: '500', flavor: 'guarana' } }], art: ['nuggets', 10],
    rule: '1 por pedido.', bg: ['#0F2A1D', '#F26A1B'] },
  { id: 'c-bacon15', tags: ['r15', 'destaque'], title: 'Brasa Bacon', price: 15, from: 26.9, bonus: 150,
    items: [{ pid: 'bacon', opts: {} }], art: ['burger', ['patty', 'cheese', 'bacon', 'tomato', 'lettuce']],
    rule: 'De segunda a quinta. 1 por pedido.', bg: ['#2B0F3A', '#E85D04'] },
  { id: 'c-crispy15', tags: ['r15'], title: 'Crispy Chicken + Batata P', price: 15, from: 30.8, bonus: 150,
    items: [{ pid: 'crispy', opts: {} }, { pid: 'batata', opts: { size: 'p' } }], art: ['burger', ['chicken', 'tomato', 'lettuce', 'sauce']],
    rule: '1 por pedido.', bg: ['#102A43', '#F0B429'] }
];

// Banners da tela inicial (apontam para um cupom ou produto).
const BANNERS = [
  { kicker: 'SEXTA BRASA', title: 'Brasa Clássico\n+ Refri 300 ml', coupon: 'c-sexta25', cta: 'Peça já' },
  { kicker: 'OFERTA DO DIA', title: 'Duplo Queijo', coupon: 'c-duplo199', cta: 'Pegar cupom' },
  { kicker: 'NOVIDADE', title: 'Smash Martins', product: 'smash', cta: 'Experimentar', bg: ['#1A1A1A', '#3D3D3D'] },
  { kicker: 'POR R$ 15', title: 'Brasa Bacon', coupon: 'c-bacon15', cta: 'Pegar cupom' },
  { kicker: 'SOBREMESA', title: 'Casquinha', coupon: 'c-casq199', cta: 'Pegar cupom' }
];

// Prêmios do clube: troque brasas por produtos ou experiências.
const REWARDS = [
  { id: 'x-sorteio', sec: 'exp', kind: 'raffle', title: 'Concorra a 1 ano de Brasa Clássico', cost: 100,
    desc: 'Troque 100 brasas por um número da sorte. O sorteio é no último dia do mês.', art: ['ticket'], bg: ['#1E1033', '#F26A1B'] },
  { id: 'x-dobro', sec: 'exp', kind: 'double', title: 'Brasas em dobro no próximo pedido', cost: 500,
    desc: 'Seu próximo pedido pago vale o dobro de brasas.', art: ['double'], bg: ['#3A1A5E', '#F0B429'] },
  { id: 'r-casq', sec: 'sug', pid: 'casquinha', cost: 1500, from: 3000, art: ['cone'] },
  { id: 'r-torta', sec: 'sug', pid: 'torta', cost: 2500, from: 4000, art: ['pie'] },
  { id: 'r-batm', sec: 'sug', pid: 'batata', opts: { size: 'm' }, title: 'Batata Média', cost: 3000, from: 5000, art: ['fries'] },
  { id: 'r-cafe', sec: 'r3', pid: 'cafe', cost: 1800, art: ['coffee'] },
  { id: 'r-refri', sec: 'r3', pid: 'refri', opts: { size: '300', flavor: 'cola' }, title: 'Refri 300 ml', cost: 2000, art: ['drink', 'cola'] },
  { id: 'r-batp', sec: 'r3', pid: 'batata', opts: { size: 'p' }, title: 'Batata Pequena', cost: 2800, art: ['fries'] },
  { id: 'r-sundae', sec: 'r6', pid: 'sundae', cost: 4000, art: ['sundae'] },
  { id: 'r-cheese', sec: 'r6', pid: 'cheese', cost: 4000, art: ['burger', ['patty', 'cheese', 'pickle']] },
  { id: 'r-nug6', sec: 'r6', pid: 'nug6', cost: 4500, art: ['nuggets', 6] },
  { id: 'r-shake', sec: 'r6', pid: 'shake', cost: 5500, art: ['shake'] },
  { id: 'r-classico', sec: 'r10', pid: 'classico', cost: 7000, art: ['burger', ['patty', 'cheese', 'tomato', 'lettuce', 'sauce']] },
  { id: 'r-bacon', sec: 'r10', pid: 'bacon', cost: 8500, art: ['burger', ['patty', 'cheese', 'bacon', 'tomato', 'lettuce']] },
  { id: 'r-combo', sec: 'r10', pid: 'classico', opts: { combo: 'm', comboDrink: 'cola' }, title: 'Combo Brasa Clássico médio', cost: 10000, art: ['burger', ['patty', 'cheese', 'tomato', 'lettuce', 'sauce']] }
];

const REWARD_SECTIONS = [
  { id: 'exp', name: 'Experiências' },
  { id: 'sug', name: 'Sugestões para você :)' },
  { id: 'r3', name: 'Até 3.000 brasas' },
  { id: 'r6', name: 'Até 6.000 brasas' },
  { id: 'r10', name: 'Até 10.000 brasas' }
];

// Missões: cumpra e resgate brasas extras.
const MISSIONS = [
  { id: 'perfil', title: 'Complete seu perfil', desc: 'Informe seu nome e celular em Meu perfil.', reward: 200, icon: 'user',
    progress: s => [s.name && s.phone ? 1 : 0, 1] },
  { id: 'primeiro', title: 'Faça seu primeiro pedido', desc: 'Peça qualquer coisa pelo app.', reward: 500, icon: 'bag',
    progress: s => [Math.min(s.orders.length, 1), 1] },
  { id: 'cupom', title: 'Use um cupom', desc: 'Pegue um cupom e finalize o pedido.', reward: 300, icon: 'tag',
    progress: s => [Math.min(s.stats.coupons, 1), 1] },
  { id: 'doce', title: 'Hora da sobremesa', desc: 'Peça uma sobremesa.', reward: 300, icon: 'cone',
    progress: s => [Math.min(s.stats.desserts, 1), 1] },
  { id: 'troca', title: 'Troque brasas', desc: 'Resgate um prêmio do clube.', reward: 300, icon: 'gift',
    progress: s => [Math.min(s.stats.rewards, 1), 1] },
  { id: 'tres', title: 'Freguês da casa', desc: 'Faça 3 pedidos.', reward: 1000, icon: 'rocket',
    progress: s => [Math.min(s.orders.length, 3), 3] }
];

const FAQ = [
  ['Como funcionam as brasas?', `A cada R$ 1 pago você ganha ${BRAND.perReal} brasas. Cupons podem dar brasas extras. Troque brasas por produtos e experiências na aba Clube.`],
  ['Como retiro meu pedido?', 'Depois de fazer o pedido, acompanhe o andamento na tela do pedido ou no Início. Quando ficar pronto, retire no balcão ao ver seu número no painel, informe o número no drive-thru ou aguarde na mesa.'],
  ['Posso usar mais de um cupom?', 'Pode, desde que sejam cupons diferentes. Cada cupom vale 1 vez por pedido.'],
  ['Para que serve o QR code da minha conta?', 'Mostre o QR code no caixa quando pedir no balcão, assim a compra também soma brasas.'],
  ['As brasas vencem?', 'As brasas valem por 12 meses a partir de quando você ganhou.'],
  ['O pagamento é de verdade?', 'Nesta versão de demonstração, o pagamento é simulado: nenhum valor é cobrado.'],
  ['Meus dados ficam onde?', 'Tudo fica salvo só neste celular. Você pode apagar tudo em Minha conta › Apagar meus dados.']
];
