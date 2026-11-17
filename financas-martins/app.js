/* Finanças — panorama financeiro sem login, dados salvos só no celular. */
(() => {
  'use strict';

  /* =================== constantes =================== */
  const STORE_KEY = 'financas-martins-v1';

  const IC = {
    food: '<path d="M7 3v8M4 3v5a3 3 0 0 0 6 0V3M7 11v10M17 21V3c-2 1-3 4-3 7v3h3"/>',
    cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l2.5 12h11L21 7H6"/>',
    car: '<path d="M3 13l2-6a2 2 0 0 1 2-1h10a2 2 0 0 1 2 1l2 6v4H3zM5 17v2M19 17v2M3 13h18"/>',
    home: '<path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
    phone: '<rect x="6" y="2" width="12" height="20" rx="3"/><path d="M11 18h2"/>',
    repeat: '<path d="M17 2l3 3-3 3"/><path d="M4 11V9a4 4 0 0 1 4-4h12M7 22l-3-3 3-3"/><path d="M20 13v2a4 4 0 0 1-4 4H4"/>',
    health: '<path d="M12 21s-8-5-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 10c0 6-8 11-8 11z"/><path d="M12 9v5M9.5 11.5h5"/>',
    bag: '<path d="M5 8h14l-1 13H6z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/>',
    ticket: '<path d="M3 9a2 2 0 0 0 0 6v3a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-3a2 2 0 0 0 0-6V6a1 1 0 0 0-1-1H4a1 1 0 0 0-1 1z"/><path d="M14 6v2M14 11v2M14 16v2"/>',
    book: '<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 21V5M8 7h7"/>',
    percent: '<path d="M19 5L5 19"/><circle cx="7" cy="7" r="2.5"/><circle cx="17" cy="17" r="2.5"/>',
    swap: '<path d="M7 7h13l-4-4M17 17H4l4 4"/>',
    dots: '<circle cx="6" cy="12" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="18" cy="12" r="1.5"/>',
    wallet: '<path d="M3 7a2 2 0 0 1 2-2h13v4"/><path d="M3 7v11a2 2 0 0 0 2 2h15V9H5a2 2 0 0 1-2-2z"/><circle cx="16" cy="14.5" r="1.2"/>',
    gift: '<rect x="3" y="8" width="18" height="13" rx="2"/><path d="M12 8v13M3 12h18M12 8S10 3 7.5 4.5 9 8 12 8zm0 0s2-5 4.5-3.5S15 8 12 8z"/>',
    trend: '<path d="M3 17l6-6 4 4 8-8"/><path d="M15 7h6v6"/>',
    card: '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M2 10h20M6 15h4"/>',
    bank: '<path d="M3 10l9-6 9 6M5 10v8M9.5 10v8M14.5 10v8M19 10v8M3 20h18"/>',
    cash: '<rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/>',
    file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M12 18v-6M9 15l3 3 3-3"/>',
    ok: '<path d="M5 12.5l4.5 4.5L19 7"/>',
    power: '<path d="M12 3v8"/><path d="M6.3 7.3a8 8 0 1 0 11.4 0"/>',
    edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/><path d="M13 7l4 4"/>',
    sliders: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
    pix: '<path d="M12 3l9 9-9 9-9-9z"/><path d="M8.5 12h7"/>',
    receipt: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/>',
    trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  };
  const IS_IOS = /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const IS_STANDALONE = matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  if (IS_IOS) document.documentElement.classList.add('ios');
  const svg = k => `<svg viewBox="0 0 24 24">${IC[k] || IC.dots}</svg>`;
  document.querySelectorAll('[data-ic]').forEach(el => { el.insertAdjacentHTML('afterbegin', svg(el.dataset.ic)); });

  // neutral: não é gasto nem renda (fica fora das estatísticas de gasto)
  const CATS = [
    { id: 'alimentacao', name: 'Alimentação', ic: 'food', color: '#ff8a4c', kw: ['ifood', 'restaurante', 'lanchonete', 'padaria', 'burger', 'pizza', 'mcdonald', 'mc donald', 'bk ', 'subway', 'rappi', 'cafe', 'café', 'bar ', 'sushi', 'acai', 'açaí', 'starbucks', 'outback', 'habib', 'giraffas', 'lanche', 'almoço', 'almoco', 'jantar', 'zé delivery', 'ze delivery', 'cafeteria', 'sorvete', 'churrasc', 'boteco', 'refeic', 'refeiç', 'self service', 'marmita', 'quilo'] },
    { id: 'mercado', name: 'Mercado', ic: 'cart', color: '#ffd166', kw: ['mercado', 'supermerc', 'carrefour', 'pao de acucar', 'pão de açúcar', 'assai', 'assaí', 'atacad', 'hortifruti', 'sacolao', 'sacolão', 'big bompreco', 'zaffari', 'savegnago', 'sams club', 'makro', 'oba ', 'st marche', 'feira', 'açougue', 'acougue', 'minuto pa', 'hirota', 'mercearia', 'emporio', 'empório'] },
    { id: 'transporte', name: 'Transporte', ic: 'car', color: '#3dd6d0', kw: ['uber', '99app', '99 ', '99pop', 'posto', 'combust', 'gasolina', 'shell', 'ipiranga', 'petrobras', 'br mania', 'estaciona', 'sem parar', 'conectcar', 'veloe', 'metro', 'metrô', 'onibus', 'ônibus', 'cabify', 'pedagio', 'pedágio', 'bilhete unico', 'cptm', 'zul ', 'mecanic', 'oficina', 'ipva', 'detran', 'lavagem', 'indriver'] },
    { id: 'moradia', name: 'Moradia', ic: 'home', color: '#a78bfa', kw: ['aluguel', 'condominio', 'condomínio', 'energia', 'enel', 'light ', 'cemig', 'copel', 'celesc', 'coelba', 'sabesp', 'cedae', 'copasa', 'agua', 'água', 'comgas', 'comgás', 'iptu', 'quintoandar', 'imobiliaria', 'imobiliária', 'leroy', 'telhanorte', 'material de constr'] },
    { id: 'contas', name: 'Contas e serviços', ic: 'phone', color: '#5aa9ff', kw: ['vivo', 'claro', 'tim ', 'oi fibra', 'internet', 'net servi', 'telefone', 'celular', 'recarga cel', 'sky ', 'correios', 'cartorio', 'cartório', 'lavanderia'] },
    { id: 'assinaturas', name: 'Assinaturas', ic: 'repeat', color: '#ff7eb6', kw: ['netflix', 'spotify', 'prime video', 'amazon prime', 'disney', 'hbo', 'max.com', 'youtube', 'apple.com', 'apple ', 'google one', 'google storage', 'icloud', 'deezer', 'globoplay', 'chatgpt', 'openai', 'claude.ai', 'anthropic', 'paramount', 'crunchyroll', 'xbox game pass', 'playstation plus', 'ps plus', 'microsoft 365', 'adobe', 'canva', 'smart fit', 'smartfit', 'academia', 'gympass', 'wellhub', 'totalpass', 'kindle'] },
    { id: 'saude', name: 'Saúde', ic: 'health', color: '#6ee7a8', kw: ['farmacia', 'farmácia', 'drogaria', 'droga raia', 'drogasil', 'raia', 'pague menos', 'panvel', 'pacheco', 'hospital', 'clinica', 'clínica', 'laboratorio', 'laboratório', 'unimed', 'amil', 'bradesco saude', 'sulamerica', 'hapvida', 'dentista', 'odonto', 'medico', 'médico', 'psicolog', 'exame', 'otica', 'ótica'] },
    { id: 'compras', name: 'Compras', ic: 'bag', color: '#e599f7', kw: ['amazon', 'mercado livre', 'mercadolivre', 'mercadolibre', 'shopee', 'magalu', 'magazine', 'americanas', 'aliexpress', 'shein', 'renner', 'riachuelo', 'c&a', 'zara', 'centauro', 'netshoes', 'kabum', 'casas bahia', 'ponto frio', 'fast shop', 'decathlon', 'havaianas', 'nike', 'adidas', 'boticario', 'boticário', 'natura', 'sephora', 'temu', 'loja', 'shopping'] },
    { id: 'lazer', name: 'Lazer e viagens', ic: 'ticket', color: '#ffa8a8', kw: ['cinema', 'cinemark', 'ingresso', 'sympla', 'eventim', 'show', 'steam', 'playstation', 'nintendo', 'xbox', 'viagem', 'hotel', 'airbnb', 'booking', 'decolar', 'latam', 'gol linhas', 'azul linhas', 'passagem', 'teatro', 'parque', 'balada', 'festa'] },
    { id: 'educacao', name: 'Educação', ic: 'book', color: '#b5e655', kw: ['escola', 'colegio', 'colégio', 'faculdade', 'universidade', 'curso', 'udemy', 'alura', 'coursera', 'livraria', 'livro', 'mensalidade', 'material escolar', 'duolingo'] },
    { id: 'taxas', name: 'Tarifas e juros', ic: 'percent', color: '#ff6b6b', kw: ['tarifa', 'iof', 'juros', 'anuidade', 'multa', 'encargo', 'cesta de servi', 'mora'] },
    { id: 'pessoas', name: 'Pix e transferências', ic: 'swap', color: '#8fb8ff', kw: ['pix', 'ted', 'doc ', 'transferencia', 'transferência', 'transf'] },
    { id: 'outros', name: 'Outros gastos', ic: 'dots', color: '#8a90a2' },
    { id: 'receitas', name: 'Salário e receitas', ic: 'wallet', color: '#d4fc79', income: true, kw: ['salario', 'salário', 'proventos', 'folha', 'pro labore', 'pró-labore', 'rendimento', 'cashback', 'reembolso', 'estorno', 'freela', 'venda'] },
    { id: 'rendimentos', name: 'Rendimentos', ic: 'trend', color: '#38d9a9', income: true, kw: [] },
    { id: 'beneficios', name: 'Benefícios (VR/VA)', ic: 'gift', color: '#5ee49a', income: true, kw: ['recarga', 'credito beneficio', 'crédito benefício', 'disponibiliza', 'beneficio', 'benefício'] },
    { id: 'invest', name: 'Investimentos', ic: 'trend', color: '#38d9a9', neutral: true, kw: ['aplicacao', 'aplicação', 'resgate', 'cdb', 'tesouro', 'invest', 'poupanca', 'poupança', 'lci', 'lca', 'corretora', 'caixinha', 'cofrinho', 'renda fixa', 'fundo'] },
    { id: 'fatura', name: 'Pagamento de fatura', ic: 'card', color: '#9aa1b3', neutral: true, kw: ['pagamento de fatura', 'pagto fatura', 'pgto fatura', 'fatura cartao', 'fatura cartão', 'pag fatura', 'pagamento fatura', 'pagamento recebido', 'pgto cartao', 'pagamento cartao', 'pagamento cartão'] },
  ];
  const CAT = Object.fromEntries(CATS.map(c => [c.id, c]));

  const TYPES = {
    conta: { name: 'Conta corrente', short: 'Conta', ic: 'bank' },
    credito: { name: 'Cartão de crédito', short: 'Crédito', ic: 'card' },
    vr: { name: 'Vale-refeição', short: 'VR', ic: 'food' },
    va: { name: 'Vale-alimentação', short: 'VA', ic: 'cart' },
    dinheiro: { name: 'Dinheiro', short: 'Dinheiro', ic: 'cash' },
  };

  const INSTS = [
    { id: 'nubank', name: 'Nubank', mono: 'Nu', color: '#8a2be2', kind: 'bank' },
    { id: 'inter', name: 'Inter', mono: 'In', color: '#ff7a00', kind: 'bank' },
    { id: 'santander', name: 'Santander', mono: 'S', color: '#e1251b', kind: 'bank' },
    { id: 'itau', name: 'Itaú', mono: 'It', color: '#ec7000', kind: 'bank' },
    { id: 'bradesco', name: 'Bradesco', mono: 'B', color: '#cc092f', kind: 'bank' },
    { id: 'bb', name: 'Banco do Brasil', mono: 'BB', color: '#1f4aa8', kind: 'bank' },
    { id: 'caixa', name: 'Caixa', mono: 'Cx', color: '#1c6fc4', kind: 'bank' },
    { id: 'c6', name: 'C6 Bank', mono: 'C6', color: '#3a3a3a', kind: 'bank' },
    { id: 'picpay', name: 'PicPay', mono: 'PP', color: '#1fae55', kind: 'bank' },
    { id: 'mercadopago', name: 'Mercado Pago', mono: 'MP', color: '#0a9bd6', kind: 'bank' },
    { id: 'btg', name: 'BTG', mono: 'BT', color: '#16346e', kind: 'bank' },
    { id: 'outro', name: 'Outro', mono: '+', color: '#4b5263', kind: 'bank' },
    { id: 'vr', name: 'VR', mono: 'VR', color: '#00a650', kind: 'benefit' },
    { id: 'alelo', name: 'Alelo', mono: 'Al', color: '#1b7f4a', kind: 'benefit' },
    { id: 'ticket', name: 'Ticket', mono: 'Tk', color: '#d81b3c', kind: 'benefit' },
    { id: 'pluxee', name: 'Pluxee', mono: 'Px', color: '#3c3cc8', kind: 'benefit' },
    { id: 'flash', name: 'Flash', mono: 'Fl', color: '#e5276f', kind: 'benefit' },
    { id: 'caju', name: 'Caju', mono: 'Cj', color: '#ef5a2a', kind: 'benefit' },
    { id: 'ifoodben', name: 'iFood Benefícios', mono: 'iF', color: '#d6202e', kind: 'benefit' },
    { id: 'swile', name: 'Swile', mono: 'Sw', color: '#6a3cf0', kind: 'benefit' },
    { id: 'carteira', name: 'Carteira', mono: 'R$', color: '#2f7d5b', kind: 'cash' },
  ];
  const INST = Object.fromEntries(INSTS.map(i => [i.id, i]));

  const MES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
  const MES_LONGO = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
  const SEMANA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
  const SEMANA_LONGO = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
  const PERIODS = { month: 'Este mês', '3m': '3 meses', '6m': '6 meses', '12m': '12 meses', all: 'Tudo' };

  /* =================== utilidades =================== */
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];
  const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
  const fmt = v => BRL.format(v || 0);
  const fmtShort = v => {
    const a = Math.abs(v);
    if (a >= 1e6) return 'R$ ' + (v / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mi';
    if (a >= 1e4) return 'R$ ' + (v / 1e3).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil';
    return 'R$ ' + Math.round(v).toLocaleString('pt-BR');
  };
  const pct = v => (v * 100).toLocaleString('pt-BR', { maximumFractionDigits: v !== 0 && Math.abs(v) < 0.1 ? 1 : 0 }) + '%';
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = n => String(n).padStart(2, '0');
  const ds = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  const pd = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
  const todayStr = () => ds(new Date());
  const nowTime = () => { const d = new Date(); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
  const addDays = (s, n) => { const d = pd(s); d.setDate(d.getDate() + n); return ds(d); };
  const monthLabel = k => MES[+k.split('-')[1] - 1];
  const dayLabel = s => {
    if (s === todayStr()) return 'Hoje';
    if (s === addDays(todayStr(), -1)) return 'Ontem';
    const d = pd(s);
    const w = SEMANA_LONGO[d.getDay()];
    return `${w[0].toUpperCase() + w.slice(1)}, ${d.getDate()} de ${MES_LONGO[d.getMonth()]}${d.getFullYear() !== new Date().getFullYear() ? ' de ' + d.getFullYear() : ''}`;
  };
  const shortDate = s => { const d = pd(s); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${String(d.getFullYear()).slice(2)}`; };
  const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  const norm = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const shade = (hex, f) => {
    const n = parseInt(hex.slice(1), 16);
    const ch = s => Math.round(((n >> s) & 255) * (1 - f));
    return `rgb(${ch(16)},${ch(8)},${ch(0)})`;
  };
  const alpha = (hex, a) => { const n = parseInt(hex.slice(1), 16); return `rgba(${n >> 16 & 255},${n >> 8 & 255},${n & 255},${a})`; };
  const tile = (catId, cls = '', id = '') => { const c = CAT[catId] || CAT.outros; return `<span class="tile ${cls}" ${id ? `id="${id}"` : ''} style="background:${alpha(c.color, .14)};color:${c.color}">${svg(c.ic)}</span>`; };

  /* =================== dados =================== */
  // Painéis: cada um guarda seus próprios dados. O primeiro usa a chave antiga (compatível com versões anteriores).
  const SPACES_KEY = STORE_KEY + ':spaces';
  const SPACE_COLORS = ['#5ee49a', '#ff7eb6', '#9db8ff', '#ffd166', '#ff8a4c', '#c4a8ff', '#3dd6d0'];
  const spaceKey = id => id === 'main' ? STORE_KEY : `${STORE_KEY}:ws:${id}`;
  const emptyDb = () => ({ version: 2, banks: [], txs: [], rules: {}, incomes: [], methods: { off: [], custom: [] } });
  function loadSpaces() {
    try { const s = JSON.parse(localStorage.getItem(SPACES_KEY)); if (s?.list?.length) return s; } catch (e) { /* */ }
    return { active: 'main', list: [{ id: 'main', name: 'Meu painel', color: SPACE_COLORS[0] }] };
  }
  let spaces = loadSpaces();
  if (!spaces.list.some(s => s.id === spaces.active)) spaces.active = spaces.list[0].id;
  const saveSpaces = () => { try { localStorage.setItem(SPACES_KEY, JSON.stringify(spaces)); } catch (e) { /* */ } };
  const currentSpace = () => spaces.list.find(s => s.id === spaces.active);
  function load(id = spaces.active) {
    try {
      const raw = localStorage.getItem(spaceKey(id));
      if (raw) return migrate(JSON.parse(raw));
    } catch (e) { /* armazenamento indisponível */ }
    return emptyDb();
  }
  function migrate(d) {
    d.rules ||= {}; d.banks ||= []; d.txs ||= []; d.incomes ||= [];
    d.methods ||= { off: [], custom: [] };
    d.invest ||= { holdings: [], rates: { cdi: 14.9, selic: 15, ipca: 4.5 }, count: { on: false, pct: 100 }, addToWealth: false };
    d.invest.holdings ||= []; d.invest.rates ||= { cdi: 14.9, selic: 15, ipca: 4.5 }; d.invest.count ||= { on: false, pct: 100 };
    for (const b of d.banks) {
      if (!b.type) b.type = /carteira|dinheiro/i.test(b.name) ? 'dinheiro' : 'conta';
      if (!b.inst) {
        const n = norm(b.name);
        b.inst = (INSTS.find(i => i.id !== 'outro' && n.includes(norm(i.name).split(' ')[0])) || (b.type === 'dinheiro' ? INST.carteira : INST.outro)).id;
        b.name = b.name.replace(/\s*\(dinheiro\)\s*/i, '');
      }
    }
    d.version = 2;
    return d;
  }
  let db = load();
  function save() {
    try { localStorage.setItem(spaceKey(spaces.active), JSON.stringify(db)); }
    catch (e) { toast('Não consegui salvar. Faça um backup em Mais.'); }
  }
  const bankById = id => db.banks.find(b => b.id === id);
  const instOf = b => INST[b?.inst] || INST.outro;
  const acctColor = b => instOf(b).color;
  const acctLabel = b => b ? `${b.name} · ${TYPES[b.type]?.short || ''}` : '';
  const monoText = b => instOf(b).id === 'outro' ? (b?.name || '?').slice(0, 2) : instOf(b).mono;
  const mono = (b, extra = '', id = '') => `<span class="mono ${extra}" ${id ? `id="${id}"` : ''} style="background:${acctColor(b)}">${esc(monoText(b))}</span>`;
  function ensureAccount({ name, inst, type }) {
    name = (name || '').trim() || INST[inst]?.name || 'Conta';
    type ||= 'conta';
    let b = db.banks.find(x => norm(x.name) === norm(name) && x.type === type);
    if (!b) { b = { id: uid(), name, inst: inst || 'outro', type, initial: 0 }; db.banks.push(b); }
    return b;
  }
  const hasCard = () => db.banks.some(b => b.type === 'credito');

  // Formas de pagamento: a lista padrão + as que você criar. Cada uma diz de que tipo de conta ela sai.
  const BASE_METHODS = [
    { id: 'pix', name: 'Pix', ic: 'pix', bank: 'conta' },
    { id: 'debito', name: 'Débito', ic: 'card', bank: 'conta' },
    { id: 'credito', name: 'Crédito', ic: 'card', bank: 'credito' },
    { id: 'dinheiro', name: 'Dinheiro', ic: 'cash', bank: 'dinheiro' },
    { id: 'vr', name: 'Vale-refeição', ic: 'food', bank: 'vr' },
    { id: 'va', name: 'Vale-alimentação', ic: 'cart', bank: 'va' },
    { id: 'boleto', name: 'Boleto', ic: 'receipt', bank: 'conta' },
    { id: 'transfer', name: 'TED / transferência', ic: 'swap', bank: 'conta' },
    { id: 'folha', name: 'Desconto em folha', ic: 'wallet', bank: null },
  ];
  const BANK_ASK = { conta: 'De qual banco?', credito: 'Qual cartão de crédito?', vr: 'Qual vale-refeição?', va: 'Qual vale-alimentação?', dinheiro: 'Onde estava o dinheiro?' };
  const methodsCfg = () => (db.methods ||= { off: [], custom: [] });
  const allMethods = () => BASE_METHODS.concat(methodsCfg().custom.map(m => ({ ...m, ic: 'dots', custom: true })));
  const enabledMethods = () => allMethods().filter(m => !methodsCfg().off.includes(m.id));
  const methodById = id => allMethods().find(m => m.id === id);
  const methodFromBank = b => ({ credito: 'credito', vr: 'vr', va: 'va', dinheiro: 'dinheiro', conta: 'debito' }[b?.type] || null);
  const banksFor = m => !m?.bank ? [] : db.banks.filter(b => b.type === m.bank);

  const AGGREGATORS = ['ifood', 'uber', '99app', 'rappi', 'amazon', 'mercadolivre', 'mercado livre', 'shopee', 'netflix', 'spotify', 'apple', 'google', 'paypal', 'aliexpress', 'shein', 'steam', 'airbnb', 'cabify', 'magalu'];
  function merchant(desc) {
    let s = norm(desc);
    s = s.replace(/^(compra( no)?( cartao)?( de)?( debito| credito)?( internacional)?|pagamento( de)?( boleto)?|pagto|pgto|pag|pix (enviado|recebido|transf\w*|qr ?code)?( para| de)?|transferencia (enviada|recebida)( pelo pix)?( para| de)?|transf(erencia)?( pix)?( enviada| recebida)?|ted( enviada| recebida)?|doc|deb(ito)?( aut(omatico)?)?|cp|elo|visa|master(card)?|compra aprovada|debito em conta)\b\s*[-:]?\s*/g, '');
    s = s.replace(/\d{2}\/\d{2}(\/\d{2,4})?/g, ' ').replace(/\d{2}:\d{2}/g, ' ');
    s = s.split(/\bcpf\b|\bcnpj\b|•|\*{2,}/)[0];
    const GENERIC = /^(efetuad[oa]|enviad[oa]|recebid[oa]|realizad[oa]|aprovad[oa]|agendad[oa]|compra|pagamento|transferencia|pix|ted|doc|debito|credito|boleto|online|no debito|no credito)?$/;
    const parts = s.split(/ - /).map(p => p.replace(/[*_\-–—|#.,;:/\\()]/g, ' ').replace(/\b\d+\b/g, ' ').replace(/\s+/g, ' ').trim());
    s = parts.find(p => !GENERIC.test(p) && !p.split(' ').every(w => GENERIC.test(w))) || parts[0];
    if (!s) return 'Outros';
    const agg = AGGREGATORS.find(a => s.startsWith(a));
    const out = agg || s.split(' ').filter(w => w.length > 1 || /\d/.test(w)).slice(0, 3).join(' ') || s;
    return out.replace(/\b\w/g, c => c.toUpperCase());
  }

  function guessCat(desc, amount, acctType) {
    const key = norm(merchant(desc));
    if (db.rules[key] && CAT[db.rules[key]]) return db.rules[key];
    const s = ' ' + norm(desc) + ' ';
    const hit = id => (CAT[id].kw || []).some(k => s.includes(norm(k)));
    if (acctType === 'credito' && amount > 0 && /pagamento|pagto|pgto/.test(s)) return 'fatura';
    if (hit('fatura') && !(acctType === 'credito' && amount < 0)) return 'fatura';
    if (hit('invest')) return 'invest';
    if (amount > 0) {
      if (acctType === 'vr' || acctType === 'va' || hit('beneficios')) return 'beneficios';
      return 'receitas';
    }
    for (const c of CATS) {
      if (c.income || c.neutral || !c.kw || c.id === 'pessoas') continue;
      if (hit(c.id)) return c.id;
    }
    if (hit('pessoas')) return 'pessoas';
    if (acctType === 'vr') return 'alimentacao';
    if (acctType === 'va') return 'mercado';
    return 'outros';
  }

  /* =================== estado e navegação =================== */
  const ui = { view: 'panorama', period: '6m', bank: '', monthPick: null, list: { q: '', cat: '', type: '', bank: '', merchant: null, limit: 120 } };
  try { const p = localStorage.getItem(STORE_KEY + ':period'); if (PERIODS[p]) ui.period = p; } catch (e) { /* */ }
  const charts = {};

  function go(view) {
    if (sel.on) endSelect();
    ui.view = view;
    $$('.dock button[data-view]').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    $$('.view').forEach(v => v.classList.toggle('active', v.id === 'view-' + view));
    window.scrollTo({ top: 0 });
    render();
    renderTop();
  }
  function renderTop() {
    const d = new Date();
    const wd = SEMANA_LONGO[d.getDay()];
    const sp = currentSpace();
    $('#space-name').textContent = sp.name;
    $('#space-dot').style.background = sp.color;
    $('#space-dot').textContent = sp.name.trim()[0]?.toUpperCase() || '?';
    $('#space-btn').title = `${wd}, ${d.getDate()} de ${MES_LONGO[d.getMonth()]}`;
    const h = d.getHours();
    $('#top-title').textContent = { panorama: h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite', lancamentos: 'Extrato', importar: 'Importar extratos', ajustes: 'Mais opções', renda: 'Sua renda', invest: 'Investimentos' }[ui.view];
    $('#period-btn').classList.toggle('hidden', ui.view !== 'panorama' || !hasData());
    $('#period-btn-label').textContent = ui.monthPick ? MES_LONGO[+ui.monthPick.slice(5) - 1] : PERIODS[ui.period];
  }
  $$('.dock button[data-view]').forEach(b => b.addEventListener('click', () => go(b.dataset.view)));
  document.addEventListener('click', e => {
    const g = e.target.closest('[data-go]'); if (g) { go(g.dataset.go); return; }
    const a = e.target.closest('[data-action]');
    if (!a) return;
    if (a.dataset.action === 'new') openTxSheet(null, a.dataset.type);
    if (a.dataset.action === 'export') exportAll();
  });
  function render() {
    if (ui.view === 'panorama') renderDashboard();
    else if (ui.view === 'lancamentos') renderList();
    else if (ui.view === 'importar') renderImport();
    else if (ui.view === 'ajustes') renderSettings();
    else if (ui.view === 'renda') renderIncome();
    else if (ui.view === 'invest') renderInvest();
  }

  /* =================== período =================== */
  // lançamento "desligado" (t.off) continua salvo, mas fica fora de saldos, gráficos e totais
  // renda automática (salário, outras fontes, rendimentos, VR): vira entradas calculadas na hora, sem gravar lançamentos
  const INC_TYPES = {
    salario: { name: 'Salário', ic: 'wallet', cat: 'receitas', def: 'Salário', hint: 'Seu salário fixo, o que cai todo mês' },
    extra: { name: 'Outras fontes', ic: 'cash', cat: 'receitas', def: 'Renda extra', hint: 'Freela, aluguel recebido, pensão, bico…' },
    invest: { name: 'Investimentos', ic: 'trend', cat: 'rendimentos', def: 'Rendimento dos investimentos', hint: 'Renda passiva: quanto rende o que você tem investido' },
    vr: { name: 'Vale-refeição / alimentação', ic: 'food', cat: 'beneficios', def: 'Vale-refeição', hint: 'O valor do VR ou VA que cai todo mês' },
  };
  const incomes = () => db.incomes || [];
  const dimOf = s => { const d = pd(s); return new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate(); };
  const r2 = v => Math.round(v * 100) / 100;
  let vCache = null;
  // parte do salário que vai para investimento (em % ou em R$) e o que sobra livre para o dia a dia
  const investPart = s => {
    if (!s?.invest?.on || !(s.monthly > 0)) return 0;
    const v = +s.invest.value || 0;
    return r2(s.invest.mode === 'amount' ? Math.min(s.monthly, Math.max(0, v)) : s.monthly * Math.min(100, Math.max(0, v)) / 100);
  };
  const freeMonthly = s => r2((+s.monthly || 0) - investPart(s));
  // fonte automática "Rendimentos dos investimentos" (só quando a chave está ligada)
  function investSource() {
    const iv = db.invest; if (!iv?.count?.on) return null;
    const m = r2(invTotals().monthly * (iv.count.pct ?? 100) / 100);
    if (!(m > 0)) return null;
    return { id: '__inv', type: 'invest', name: 'Rendimentos dos investimentos', monthly: m, mode: 'daily', payday: 1, bank: null, start: iv.count.start || todayStr(), end: null, synthetic: true };
  }
  const incomeSources = () => { const s = investSource(); return s ? incomes().concat([s]) : incomes(); };
  function virtualIncome() {
    const today = todayStr();
    const key = today + JSON.stringify(incomeSources());
    if (vCache && vCache.db === db && vCache.key === key) return vCache.list;
    const out = [];
    for (const src0 of incomeSources()) {
      const src = { ...src0, monthly: freeMonthly(src0) };
      if (src.mode === 'off' || !(src.monthly > 0) || !src.start) continue;
      const end = src.end && src.end < today ? src.end : today;
      const base = { time: null, desc: src.name, cat: INC_TYPES[src.type]?.cat || 'receitas', bank: src.bank || null, source: 'renda', virtual: true, inc: src.id };
      if (src.mode === 'daily') {
        // valor do mês ÷ dias daquele mês, com arredondamento que fecha o mês exato
        for (let d = src.start; d <= end; d = addDays(d, 1)) {
          const dim = dimOf(d), day = +d.slice(8);
          out.push({ ...base, id: `inc:${src.id}:${d}`, date: d, amount: r2(r2(src.monthly * day / dim) - r2(src.monthly * (day - 1) / dim)) });
        }
      } else {
        const s0 = pd(src.start); const m = new Date(s0.getFullYear(), s0.getMonth(), 1);
        while (ds(m) <= end) {
          const dim = new Date(m.getFullYear(), m.getMonth() + 1, 0).getDate();
          const pay = ds(new Date(m.getFullYear(), m.getMonth(), Math.min(+src.payday || 1, dim)));
          if (pay >= src.start && pay <= end) out.push({ ...base, id: `inc:${src.id}:${pay}`, date: pay, amount: r2(src.monthly) });
          m.setMonth(m.getMonth() + 1);
        }
      }
    }
    vCache = { db, key, list: out };
    return out;
  }
  // agrupa as entradas automáticas por fonte e mês (para planilhas)
  function groupVirtual(list) {
    const g = {};
    for (const t of list) { const k = `${t.inc}|${t.date.slice(0, 7)}`; (g[k] ||= { ...t, amount: 0 }); g[k].amount = r2(g[k].amount + t.amount); g[k].date = t.date > g[k].date ? t.date : g[k].date; }
    return Object.values(g).map(t => ({ ...t, desc: `${t.desc} · renda automática (${MES_LONGO[+t.date.slice(5, 7) - 1]})` }));
  }
  const hasData = () => db.txs.length > 0 || virtualIncome().length > 0;
  const active = () => db.txs.filter(t => !t.off).concat(virtualIncome());
  const scoped = () => active().filter(t => !ui.bank || t.bank === ui.bank);
  function dataBounds(txs) {
    let min = null, max = null;
    for (const t of txs) { if (!min || t.date < min) min = t.date; if (!max || t.date > max) max = t.date; }
    return { min, max };
  }
  function getRange() {
    const { min, max } = dataBounds(scoped());
    const today = todayStr();
    if (ui.monthPick) {
      const [y, m] = ui.monthPick.split('-').map(Number);
      const end = ds(new Date(y, m, 0));
      return { from: `${ui.monthPick}-01`, to: end < today ? end : (max && max > today ? max : today), months: 1, picked: true,
        prev: { from: ds(new Date(y, m - 2, 1)), to: ds(new Date(y, m - 1, 0)) } };
    }
    const anchor = max && max > today ? max : (max && max.slice(0, 7) < today.slice(0, 7) ? max : today);
    const a = pd(anchor);
    const n = { month: 1, '3m': 3, '6m': 6, '12m': 12 }[ui.period];
    if (!n) return { from: min || anchor, to: anchor, months: null, prev: null };
    const from = ds(new Date(a.getFullYear(), a.getMonth() - (n - 1), 1));
    const end = ds(new Date(a.getFullYear(), a.getMonth() + 1, 0));
    return { from, to: end < anchor ? end : anchor, months: n,
      prev: { from: ds(new Date(a.getFullYear(), a.getMonth() - (2 * n - 1), 1)), to: addDays(from, -1) } };
  }
  const inRange = (t, r) => t.date >= r.from && t.date <= r.to;
  const isNeutral = t => CAT[t.cat]?.neutral;
  const isSpend = t => t.amount < 0 && !isNeutral(t) && !t.off;
  function totals(txs) {
    let inc = 0, out = 0;
    for (const t of txs) { if (isNeutral(t) || t.off) continue; if (t.amount > 0) inc += t.amount; else out -= t.amount; }
    return { inc, out, net: inc - out };
  }
  function monthsIn(r) {
    const out = []; const b = pd(r.to);
    const d = new Date(pd(r.from).getFullYear(), pd(r.from).getMonth(), 1);
    while (d <= b) { out.push(`${d.getFullYear()}-${pad(d.getMonth() + 1)}`); d.setMonth(d.getMonth() + 1); }
    return out;
  }
  const daysBetween = (a, b) => Math.round((pd(b) - pd(a)) / 86400000) + 1;
  // patrimônio: investimento só troca o dinheiro de lugar; pagamento de fatura já está nas compras do cartão
  const countsForWealth = t => t.cat !== 'invest' && !(t.cat === 'fatura' && hasCard());

  /* =================== gráficos =================== */
  if (window.Chart) {
    Chart.defaults.font.family = 'Jakarta, system-ui, sans-serif';
    Chart.defaults.font.weight = 600;
    Chart.defaults.color = '#6c7284';
    Chart.defaults.borderColor = 'rgba(255,255,255,0.06)';
  }
  const tooltip = {
    backgroundColor: '#f3f4f7', titleColor: '#0a0b0f', bodyColor: '#0a0b0f', footerColor: '#4b5263',
    padding: 10, cornerRadius: 12, displayColors: false, titleFont: { weight: 800 }, bodyFont: { weight: 600 },
  };
  const axes = () => ({
    x: { grid: { display: false }, border: { display: false }, ticks: { maxRotation: 0, autoSkipPadding: 10 } },
    y: { grid: { color: 'rgba(255,255,255,0.05)' }, border: { display: false }, ticks: { callback: v => fmtShort(v), maxTicksLimit: 4 } },
  });
  const vGrad = (color, a0 = 0.15) => c => {
    const { ctx, chartArea } = c.chart;
    if (!chartArea) return color;
    const g = ctx.createLinearGradient(0, chartArea.bottom, 0, chartArea.top);
    g.addColorStop(0, alpha(color, a0)); g.addColorStop(1, color);
    return g;
  };
  const fillGrad = (color, a = 0.35) => c => {
    const { ctx, chartArea } = c.chart;
    if (!chartArea) return 'transparent';
    const g = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
    g.addColorStop(0, alpha(color, a)); g.addColorStop(1, alpha(color, 0));
    return g;
  };
  function setChart(key, canvas, config) {
    if (!window.Chart) return;
    if (charts[key]) charts[key].destroy();
    charts[key] = new Chart(canvas, config);
  }
  const crosshair = {
    id: 'crosshair',
    afterDatasetsDraw(chart) {
      const act = chart.tooltip?.getActiveElements?.();
      if (!act?.length) return;
      const x = act[0].element.x, { top, bottom } = chart.chartArea, ctx = chart.ctx;
      ctx.save(); ctx.strokeStyle = 'rgba(255,255,255,0.25)'; ctx.setLineDash([3, 4]);
      ctx.beginPath(); ctx.moveTo(x, top); ctx.lineTo(x, bottom); ctx.stroke(); ctx.restore();
    },
  };
  function donut(key, canvas, items, { cutout = '72%', onPick, type = 'doughnut' } = {}) {
    const pie = type === 'pie';
    setChart(key, canvas, {
      type,
      data: { labels: items.map(i => i.name), datasets: [{ data: items.map(i => i.value), backgroundColor: items.map(i => i.color), borderWidth: pie ? 2 : 0, borderColor: '#12141b', spacing: pie ? 0 : 3, borderRadius: pie ? 0 : 8, hoverOffset: 8 }] },
      options: {
        responsive: true, maintainAspectRatio: false, cutout: pie ? 0 : cutout, layout: { padding: 6 },
        animation: { animateRotate: true, duration: 700 },
        plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: { label: c => ` ${fmt(c.parsed)}` } } },
        onClick: (e, els) => { if (els.length && onPick) onPick(items[els[0].index]); },
      },
    });
  }
  function legendList(el, items, total, onPick) {
    el.innerHTML = items.map((it, i) => `<li data-i="${i}"><i style="background:${it.color}"></i><span class="n">${esc(it.name)}</span><span class="p">${pct(total ? it.value / total : 0)}</span><span class="v">${fmt(it.value)}</span></li>`).join('');
    el.querySelectorAll('li').forEach(li => li.addEventListener('click', () => onPick && onPick(items[+li.dataset.i])));
  }

  /* =================== início =================== */
  function renderDashboard() {
    const has = hasData();
    renderNag();
    renderIncomeCard();
    $('#empty-state').classList.toggle('hidden', has);
    $('#dashboard').classList.toggle('hidden', !has);
    renderTop();
    if (!has) return;

    const r = getRange();
    const all = scoped();
    const txs = all.filter(t => inRange(t, r));
    const { min } = dataBounds(all);
    const prevOk = r.prev && min <= addDays(r.prev.from, 7);
    const T = totals(txs), P = prevOk ? totals(all.filter(t => inRange(t, r.prev))) : null;

    // patrimônio dia a dia
    const initial = db.banks.filter(b => !ui.bank || b.id === ui.bank).reduce((s, b) => s + (+b.initial || 0), 0);
    const byDay = {};
    for (const t of all) if (countsForWealth(t)) byDay[t.date] = (byDay[t.date] || 0) + t.amount;
    let bal = initial, startBal = null;
    const series = [];
    for (let d = min; d <= r.to; d = addDays(d, 1)) {
      if (startBal === null && d >= r.from) startBal = bal;
      bal += byDay[d] || 0;
      if (d >= r.from) series.push({ d, v: Math.round(bal * 100) / 100 });
    }
    if (startBal === null) startBal = bal;
    const invAdd = db.invest?.addToWealth && !ui.bank ? invTotals().amount : 0;
    if (invAdd) series.forEach(p => { p.v = r2(p.v + invAdd); });
    const wealth = bal + invAdd, wDelta = wealth - (startBal + invAdd);
    $('#hero-label').textContent = invAdd ? 'Patrimônio total · com investimentos' : 'Patrimônio total';

    // cartão principal
    const [int, cents] = fmt(Math.abs(wealth)).replace('R$', '').trim().split(',');
    $('#hero-value').innerHTML = `<small>${wealth < 0 ? '−R$' : 'R$'}</small>${esc(int)}<span style="opacity:.6;font-size:24px">,${esc(cents || '00')}</span>`;
    $('#hero-delta').textContent = `${wDelta >= 0 ? '▲' : '▼'} ${fmtShort(Math.abs(wDelta))} no período`;
    $('#hero-in').textContent = fmtShort(T.inc);
    $('#hero-out').textContent = fmtShort(T.out);
    $('#hero-net').textContent = fmtShort(T.net);
    setChart('spark', $('#chart-spark'), {
      type: 'line',
      data: { labels: series.map(p => p.d), datasets: [{ data: series.map(p => p.v), borderColor: 'rgba(255,255,255,0.95)', borderWidth: 2.2, pointRadius: 0, tension: 0.35, fill: true, backgroundColor: fillGrad('#ffffff', 0.28) }] },
      options: { responsive: true, maintainAspectRatio: false, animation: { duration: 800 }, plugins: { legend: { display: false }, tooltip: { enabled: false } }, scales: { x: { display: false }, y: { display: false } }, events: [] },
    });

    renderAccounts();
    renderStories({ r, txs, T, P, all });

    // categorias (rosca)
    const catTot = {};
    for (const t of txs) if (isSpend(t)) catTot[t.cat] = (catTot[t.cat] || 0) - t.amount;
    const sorted = Object.entries(catTot).sort((a, b) => b[1] - a[1]);
    const top = sorted.slice(0, 6).map(([id, v]) => ({ id, name: CAT[id].name, value: v, color: CAT[id].color }));
    const rest = sorted.slice(6).reduce((s, [, v]) => s + v, 0);
    if (rest > 0) top.push({ id: null, name: 'Outras', value: rest, color: '#3a3f4d' });
    $('#cats-total').textContent = fmtShort(T.out);
    const pickCat = it => { if (it.id) openDrill({ cat: it.id }); else { ui.list = { ...ui.list, type: 'out', cat: '', merchant: null, q: '' }; go('lancamentos'); } };
    donut('cats', $('#chart-cats'), top.length ? top : [{ name: 'Sem gastos', value: 1, color: '#232733' }], { onPick: top.length ? pickCat : null });
    legendList($('#cats-legend'), top, T.out, pickCat);

    // por conta (rosca)
    const accTot = {};
    for (const t of txs) if (isSpend(t)) accTot[t.bank] = (accTot[t.bank] || 0) - t.amount;
    const accItems = Object.entries(accTot).sort((a, b) => b[1] - a[1]).map(([id, v]) => { const b = bankById(id); return { id, name: acctLabel(b) || 'Sem conta', value: v, color: acctColor(b) }; });
    $('#card-accts').classList.toggle('hidden', accItems.length < 2);
    if (accItems.length >= 2) {
      $('#accts-count').textContent = accItems.length;
      const pickAcc = it => openDrill({ bank: it.id });
      donut('accts', $('#chart-accts'), accItems, { onPick: pickAcc, cutout: '64%' });
      legendList($('#accts-legend'), accItems, T.out, pickAcc);
    }

    // fixos x variáveis (pizza)
    const rec = findRecurring(all);
    const recSet = new Set(rec.map(x => x.name));
    let fixed = 0, variable = 0;
    for (const t of txs) if (isSpend(t)) { if (recSet.has(merchant(t.desc))) fixed -= t.amount; else variable -= t.amount; }
    const fv = [{ name: 'Fixos', value: fixed, color: '#9db8ff' }, { name: 'Variáveis', value: variable, color: '#ff9a6b' }];
    donut('fixed', $('#chart-fixed'), fixed + variable ? fv : [{ name: '-', value: 1, color: '#232733' }], { type: 'pie' });
    $('#fixed-legend').innerHTML = fv.map(i => `<li><i style="background:${i.color}"></i>${i.name}<b>${pct((fixed + variable) ? i.value / (fixed + variable) : 0)}</b></li>`).join('');

    // quanto guardou (medidor)
    const rate = T.inc > 0 ? T.net / T.inc : 0;
    const shown = Math.max(0, Math.min(1, rate));
    const A1 = themeAccent(), A2 = themeAccent2();
    const gaugeFill = c => { const { ctx, chartArea } = c.chart; if (!chartArea) return A2; const g = ctx.createLinearGradient(chartArea.left, 0, chartArea.right, 0); g.addColorStop(0, A2); g.addColorStop(1, A1); return g; };
    setChart('gauge', $('#chart-gauge'), {
      type: 'doughnut',
      data: { datasets: [{ data: [shown, 1 - shown], backgroundColor: c => c.dataIndex === 0 ? gaugeFill(c) : '#232733', borderWidth: 0, borderRadius: 10 }] },
      options: { responsive: true, maintainAspectRatio: false, rotation: -90, circumference: 180, cutout: '78%', plugins: { legend: { display: false }, tooltip: { enabled: false } }, events: [] },
    });
    $('#gauge-value').textContent = T.inc > 0 ? pct(rate) : '—';
    $('#gauge-value').style.color = rate < 0 ? 'var(--out)' : '';
    $('#gauge-note').textContent = T.inc > 0 ? (T.net >= 0 ? `${fmtShort(T.net)} sobraram` : `${fmtShort(-T.net)} além do que entrou`) : 'Sem entradas no período';

    // de onde vem (pizza)
    const src = {};
    for (const t of txs) if (t.amount > 0 && !isNeutral(t)) { const k = merchant(t.desc); src[k] = (src[k] || 0) + t.amount; }
    const srcSorted = Object.entries(src).sort((a, b) => b[1] - a[1]);
    const SRC_COLORS = ['#d4fc79', '#5ee49a', '#38d9a9', '#9db8ff', '#c4a8ff'];
    const srcItems = srcSorted.slice(0, 4).map(([k, v], i) => ({ name: k, value: v, color: SRC_COLORS[i] }));
    const srcRest = srcSorted.slice(4).reduce((s, [, v]) => s + v, 0);
    if (srcRest > 0) srcItems.push({ name: 'Outras', value: srcRest, color: '#3a3f4d' });
    $('#card-sources').classList.toggle('hidden', !srcItems.length);
    if (srcItems.length) {
      const pickSrc = it => { if (it.name !== 'Outras') { ui.list = { ...ui.list, merchant: it.name, type: 'in', cat: '', q: '' }; go('lancamentos'); } };
      donut('sources', $('#chart-sources'), srcItems, { type: 'pie', onPick: pickSrc });
      legendList($('#sources-legend'), srcItems, T.inc, pickSrc);
    }

    // entradas e saídas por mês
    const months = r.months === 1 && !r.picked && r.prev ? monthsIn({ from: r.prev.from, to: r.to }) : monthsIn(r);
    const mInc = {}, mOut = {};
    for (const t of all) {
      if (isNeutral(t)) continue;
      const k = t.date.slice(0, 7);
      if (t.amount > 0) mInc[k] = (mInc[k] || 0) + t.amount; else mOut[k] = (mOut[k] || 0) - t.amount;
    }
    const lit = k => !ui.monthPick || ui.monthPick === k;
    setChart('months', $('#chart-months'), {
      type: 'bar',
      data: {
        labels: months.map(monthLabel),
        datasets: [
          { label: 'Entradas', data: months.map(k => mInc[k] || 0), backgroundColor: c => lit(months[c.dataIndex]) ? vGrad('#5ee49a')(c) : 'rgba(94,228,154,0.18)', borderRadius: 8, borderSkipped: false, maxBarThickness: 16, categoryPercentage: 0.62, barPercentage: 0.9 },
          { label: 'Saídas', data: months.map(k => mOut[k] || 0), backgroundColor: c => lit(months[c.dataIndex]) ? vGrad('#ff7a6b')(c) : 'rgba(255,122,107,0.18)', borderRadius: 8, borderSkipped: false, maxBarThickness: 16, categoryPercentage: 0.62, barPercentage: 0.9 },
        ],
      },
      options: {
        responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: {
          title: it => { const k = months[it[0].dataIndex]; return `${MES_LONGO[+k.slice(5) - 1]} de ${k.slice(0, 4)}`; },
          label: c => `${c.dataset.label}: ${fmt(c.parsed.y)}`,
          footer: it => { const k = months[it[0].dataIndex]; return `Saldo: ${fmt((mInc[k] || 0) - (mOut[k] || 0))}`; } } } },
        scales: axes(),
        onClick: (e, els, chart) => {
          const pts = chart.getElementsAtEventForMode(e, 'index', { intersect: false }, false);
          if (!pts.length) return;
          const k = months[pts[0].index];
          ui.monthPick = ui.monthPick === k ? null : k;
          renderDashboard(); window.scrollTo({ top: 0, behavior: 'smooth' });
        },
      },
    });

    // patrimônio (linha)
    setChart('wealth', $('#chart-wealth'), {
      type: 'line',
      data: { labels: series.map(p => { const d = pd(p.d); return `${d.getDate()} ${MES[d.getMonth()]}`; }), datasets: [{ data: series.map(p => p.v), borderColor: A1, borderWidth: 2.4, pointRadius: 0, pointHoverRadius: 6, pointHoverBackgroundColor: A1, pointHoverBorderColor: '#12141b', pointHoverBorderWidth: 3, tension: 0.3, fill: true, backgroundColor: fillGrad(A2, 0.3) }] },
      options: {
        responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: { title: it => dayLabel(series[it[0].dataIndex].d), label: c => fmt(c.parsed.y) } } },
        scales: { ...axes(), x: { ...axes().x, ticks: { maxTicksLimit: 5, maxRotation: 0 } } },
      },
      plugins: [crosshair],
    });

    // categorias mês a mês (barras empilhadas)
    const stackCats = sorted.slice(0, 5).map(([id]) => id);
    const per = {};
    for (const t of all) if (isSpend(t)) { const k = t.date.slice(0, 7); const c = stackCats.includes(t.cat) ? t.cat : '_o'; per[k] ||= {}; per[k][c] = (per[k][c] || 0) - t.amount; }
    const stackSets = [...stackCats, '_o'].map(c => ({
      label: c === '_o' ? 'Outras' : CAT[c].name,
      data: months.map(k => per[k]?.[c] || 0),
      backgroundColor: c === '_o' ? '#3a3f4d' : CAT[c].color,
      borderColor: '#12141b', borderWidth: { top: 2 }, borderSkipped: false, borderRadius: 4, maxBarThickness: 26,
    }));
    $('#stack-legend').innerHTML = stackSets.map(s => `<span><i style="background:${s.backgroundColor}"></i>${esc(s.label)}</span>`).join('');
    setChart('stack', $('#chart-stack'), {
      type: 'bar', data: { labels: months.map(monthLabel), datasets: stackSets },
      options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { ...tooltip, displayColors: true, boxPadding: 4, usePointStyle: true, callbacks: { label: c => ` ${c.dataset.label}: ${fmt(c.parsed.y)}` }, filter: i => i.parsed.y > 0 } },
        scales: { x: { ...axes().x, stacked: true }, y: { ...axes().y, stacked: true } } },
    });

    // dia da semana
    const wk = [0, 0, 0, 0, 0, 0, 0], wkN = [0, 0, 0, 0, 0, 0, 0];
    for (let d = min > r.from ? min : r.from; d <= r.to; d = addDays(d, 1)) wkN[pd(d).getDay()]++;
    for (const t of txs) if (isSpend(t)) wk[pd(t.date).getDay()] -= t.amount;
    const order = [1, 2, 3, 4, 5, 6, 0];
    const avg = order.map(i => wkN[i] ? wk[i] / wkN[i] : 0);
    const maxAvg = Math.max(...avg);
    setChart('week', $('#chart-week'), {
      type: 'bar',
      data: { labels: order.map(i => SEMANA[i]), datasets: [{ data: avg, backgroundColor: c => avg[c.dataIndex] === maxAvg ? vGrad('#ff7a6b', 0.3)(c) : vGrad('#5a6075', 0.3)(c), borderRadius: 10, borderSkipped: false, maxBarThickness: 24 }] },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: { title: it => SEMANA_LONGO[order[it[0].dataIndex]], label: c => `Média: ${fmt(c.parsed.y)} por dia` } } }, scales: axes() },
    });

    // onde mais gasta
    const mer = {};
    for (const t of txs) { if (!isSpend(t)) continue; const k = merchant(t.desc); (mer[k] ||= { v: 0, n: 0, cat: t.cat }).v -= t.amount; mer[k].n++; }
    const topM = Object.entries(mer).sort((a, b) => b[1].v - a[1].v).slice(0, 7);
    const maxM = topM[0]?.[1].v || 1;
    $('#top-merchants').innerHTML = topM.length ? topM.map(([k, m]) => `
      <li data-merchant="${esc(k)}">${tile(m.cat)}<div class="t"><div>${esc(k)}</div><small>${m.n} ${m.n > 1 ? 'vezes' : 'vez'} · média ${fmt(m.v / m.n)}</small><div class="bar"><i style="width:${m.v / maxM * 100}%"></i></div></div><span class="v">${fmt(m.v)}</span></li>`).join('')
      : `<p class="empty-note">Nada por aqui ainda.</p>`;

    // recorrentes
    const recTotal = rec.reduce((s, x) => s + x.avg, 0);
    $('#recurring').innerHTML = rec.length ? rec.slice(0, 8).map(x => `
      <li data-merchant="${esc(x.name)}">${tile(x.cat)}<div class="t"><div>${esc(x.name)}</div><small>todo dia ~${x.day} · há ${x.months} meses</small></div><span class="v">${fmt(x.avg)}</span></li>`).join('')
      + `<li class="total"><span>Total por mês</span><b>${fmt(recTotal)} <span style="color:var(--muted);font-weight:600">· ${fmtShort(recTotal * 12)}/ano</span></b></li>`
      : `<p class="empty-note">Com 2 meses de dados eu encontro as cobranças que se repetem.</p>`;
    $$('#top-merchants li[data-merchant], #recurring li[data-merchant]').forEach(li => li.addEventListener('click', () => {
      ui.list = { ...ui.list, merchant: li.dataset.merchant, q: '', cat: '', type: '', limit: 120 };
      go('lancamentos');
    }));
  }

  function renderAccounts() {
    const strip = $('#acct-strip');
    const cards = db.banks.map(b => {
      const now = (+b.initial || 0) + active().filter(t => t.bank === b.id).reduce((s, t) => s + t.amount, 0);
      const isCard = b.type === 'credito';
      const c = acctColor(b);
      return `<button class="acct ${ui.bank && ui.bank !== b.id ? 'dim' : ''} ${ui.bank === b.id ? 'sel' : ''}" data-bank="${b.id}" style="background:linear-gradient(140deg, ${c}, ${shade(c, .55)})">
        <div class="a-top">${mono(b)}<div style="min-width:0"><div class="a-name">${esc(b.name)}</div><div class="a-type">${TYPES[b.type]?.name || ''}</div></div></div>
        <div><div class="a-type">${isCard ? 'Fatura em aberto' : 'Saldo'}</div><div class="a-val">${fmt(isCard ? Math.max(0, -now) : now)}</div></div></button>`;
    }).join('');
    strip.innerHTML = `<button class="acct all ${ui.bank ? 'dim' : 'sel'}" data-bank="">
      <div class="a-top"><span class="mono" style="background:var(--bg-4)">${svg('dots')}</span><div class="a-name">Todas</div></div>
      <div><div class="a-type">${db.banks.length} conta${db.banks.length === 1 ? '' : 's'}</div><div class="a-val">Visão geral</div></div></button>` + cards;
    strip.querySelectorAll('.acct').forEach(el => el.addEventListener('click', () => {
      ui.bank = ui.bank === el.dataset.bank ? '' : el.dataset.bank;
      renderDashboard();
    }));
    $('#acct-note').textContent = ui.bank ? 'Toque de novo para ver todas' : 'Toque para filtrar';
  }

  function findRecurring(all) {
    const g = {};
    for (const t of all) { if (!isSpend(t)) continue; (g[merchant(t.desc)] ||= []).push(t); }
    const out = [];
    for (const [name, list] of Object.entries(g)) {
      const byMonth = {};
      list.forEach(t => { (byMonth[t.date.slice(0, 7)] ||= []).push(t); });
      const ms = Object.keys(byMonth).sort();
      if (ms.length < 2 || list.length > ms.length * 1.5) continue;
      const vals = ms.map(m => -byMonth[m].reduce((s, t) => s + t.amount, 0));
      if (Math.max(...vals) / Math.min(...vals) > 1.3) continue;
      let streak = 1;
      for (let i = ms.length - 1; i > 0; i--) {
        const [y1, m1] = ms[i - 1].split('-').map(Number), [y2, m2] = ms[i].split('-').map(Number);
        if ((y2 * 12 + m2) - (y1 * 12 + m1) === 1) streak++; else break;
      }
      if (streak < 2) continue;
      const days = list.map(t => +t.date.slice(8)).sort((a, b) => a - b);
      out.push({ name, cat: list[0].cat, months: streak, avg: vals.slice(-streak).reduce((s, v) => s + v, 0) / streak, day: days[Math.floor(days.length / 2)] });
    }
    return out.sort((a, b) => b.avg - a.avg);
  }

  function renderStories({ r, txs, T, P, all }) {
    const s = [];
    const catTot = {};
    for (const t of txs) if (isSpend(t)) catTot[t.cat] = (catTot[t.cat] || 0) - t.amount;
    const topCat = Object.entries(catTot).sort((a, b) => b[1] - a[1])[0];
    if (topCat) s.push(['g1', 'Maior categoria', CAT[topCat[0]].name, `${fmt(topCat[1])} · ${pct(topCat[1] / T.out)} de tudo que saiu`]);
    if (T.out > 0) {
      const { min } = dataBounds(all);
      const days = Math.max(1, daysBetween(min > r.from ? min : r.from, r.to));
      s.push(['g2', 'Gasto médio', `${fmt(T.out / days)}/dia`, `Cerca de ${fmtShort(T.out / days * 30)} por mês`]);
    }
    if (P && P.out > 0 && T.out > 0) {
      const ch = (T.out - P.out) / P.out;
      if (Math.abs(ch) >= 0.03) s.push([ch > 0 ? 'g5' : 'g6', 'Comparado ao período anterior', `${ch > 0 ? '+' : '−'}${pct(Math.abs(ch))}`, `Você gastou ${ch > 0 ? 'mais' : 'menos'}: ${fmtShort(P.out)} → ${fmtShort(T.out)}`]);
    }
    const big = txs.filter(isSpend).sort((a, b) => a.amount - b.amount)[0];
    if (big) s.push(['g3', 'Maior gasto', fmt(-big.amount), `${merchant(big.desc)} · ${shortDate(big.date)}`]);
    const vrAcc = db.banks.filter(b => b.type === 'vr' || b.type === 'va').map(b => b.id);
    const vr = txs.filter(t => vrAcc.includes(t.bank) && isSpend(t));
    if (vr.length) s.push(['g6', 'Vale-refeição e alimentação', fmt(-vr.reduce((a, t) => a + t.amount, 0)), `gastos em ${vr.length} compras com o benefício`]);
    const delivery = txs.filter(t => isSpend(t) && /ifood|rappi|delivery|ze delivery/.test(norm(t.desc)));
    if (delivery.length >= 3) s.push(['g5', 'Delivery', `${delivery.length} pedidos`, `${fmt(-delivery.reduce((a, t) => a + t.amount, 0))} no período`]);
    const fees = txs.filter(t => t.cat === 'taxas').reduce((a, t) => a - t.amount, 0);
    if (fees > 0) s.push(['g4', 'Tarifas e juros', fmt(fees), 'pagos ao banco neste período']);
    $('#stories').innerHTML = s.map(([g, k, b, p]) => `<div class="story ${g}"><span class="kick">${esc(k)}</span><div><div class="big">${esc(b)}</div><p>${esc(p)}</p></div></div>`).join('')
      || `<div class="story g2"><span class="kick">Dica</span><div><div class="big">Comece</div><p>Registre gastos ou importe um extrato.</p></div></div>`;
  }

  /* =================== período (sheet) =================== */
  function openPeriod() {
    $$('#period-list button').forEach(b => b.classList.toggle('on', !ui.monthPick && b.dataset.period === ui.period));
    $('#period-sheet').classList.remove('hidden'); $('#period-backdrop').classList.remove('hidden');
  }
  const closePeriod = () => { $('#period-sheet').classList.add('hidden'); $('#period-backdrop').classList.add('hidden'); };
  $('#period-btn').addEventListener('click', openPeriod);
  $('#period-backdrop').addEventListener('click', closePeriod);
  $('#period-list').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    ui.period = b.dataset.period; ui.monthPick = null;
    try { localStorage.setItem(STORE_KEY + ':period', ui.period); } catch (err) { /* */ }
    closePeriod(); renderDashboard();
  });

  /* =================== detalhe =================== */
  function openDrill({ cat, bank }) {
    const r = getRange();
    const base = (bank ? active().filter(t => t.bank === bank) : scoped()).filter(t => isSpend(t) && (!cat || t.cat === cat));
    const list = base.filter(t => inRange(t, r)).sort((a, b) => (b.date + (b.time || '')).localeCompare(a.date + (a.time || '')));
    const total = -list.reduce((s, t) => s + t.amount, 0);
    const T = totals(scoped().filter(t => inRange(t, r)));
    const b = bankById(bank);
    const color = cat ? CAT[cat].color : acctColor(b);
    $('#drill-icon').outerHTML = cat ? tile(cat, 'big', 'drill-icon') : mono(b, 'tile big', 'drill-icon');
    $('#drill-title').textContent = cat ? CAT[cat].name : acctLabel(b);
    $('#drill-sub').textContent = `${fmt(total)} · ${pct(T.out ? total / T.out : 0)} dos gastos · ${list.length} ${list.length === 1 ? 'lançamento' : 'lançamentos'}`;
    const months = monthsIn(r.months === 1 && !r.picked && r.prev ? { from: r.prev.from, to: r.to } : r);
    const per = {}; base.forEach(t => { const k = t.date.slice(0, 7); per[k] = (per[k] || 0) - t.amount; });
    $('#drill-sheet').classList.remove('hidden'); $('#drill-backdrop').classList.remove('hidden');
    setChart('drill', $('#chart-drill'), {
      type: 'bar', data: { labels: months.map(monthLabel), datasets: [{ data: months.map(k => per[k] || 0), backgroundColor: vGrad(color, 0.25), borderRadius: 10, borderSkipped: false, maxBarThickness: 26 }] },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: { label: c => fmt(c.parsed.y) } } }, scales: axes() },
    });
    $('#drill-list').innerHTML = txRows(list.slice(0, 80));
    bindTxRows($('#drill-list'));
  }
  const closeDrill = () => { $('#drill-sheet').classList.add('hidden'); $('#drill-backdrop').classList.add('hidden'); };
  $('#drill-close').addEventListener('click', closeDrill);
  $('#drill-backdrop').addEventListener('click', closeDrill);

  /* =================== extrato =================== */
  function txRows(list, grouped) {
    let html = '', last = null;
    const dayTot = {};
    if (grouped) list.forEach(t => { dayTot[t.date] = r2((dayTot[t.date] || 0) + (isNeutral(t) || t.off ? 0 : t.amount)); });
    for (const t of list) {
      if (grouped && t.date !== last) { last = t.date; html += `<div class="day-head"><span>${dayLabel(t.date)}</span><span>${fmt(dayTot[t.date])}</span></div>`; }
      const c = CAT[t.cat] || CAT.outros; const b = bankById(t.bank);
      html += `<div class="tx ${t.off ? 'off' : ''} ${t.virtual ? 'auto' : ''} ${sel.ids.has(t.id) ? 'sel' : ''}" data-id="${t.id}" role="button" tabindex="0"><span class="check">${svg('ok')}</span>${tile(t.cat)}
        <div class="t"><div class="d">${t.off ? '<span class="off-tag">desligado</span>' : ''}${t.virtual ? '<span class="auto-tag">automática</span>' : ''}${esc(t.desc)}</div><div class="m">${grouped ? '' : shortDate(t.date) + ' · '}${t.time ? t.time + ' · ' : ''}${esc(c.name)}${t.method && methodById(t.method) ? ' · ' + esc(methodById(t.method).name) + (b ? ' ' + esc(b.name) : '') : b ? ' · ' + esc(acctLabel(b)) : ''}</div></div>
        <span class="a ${t.amount > 0 ? 'in' : ''}">${t.amount > 0 ? '+' : '−'} ${fmt(Math.abs(t.amount))}</span></div>`;
    }
    return html || `<p class="empty-note" style="padding:20px 4px">Nenhum lançamento encontrado.</p>`;
  }
  function bindTxRows(root, selectable) {
    root.querySelectorAll('.tx').forEach(el => {
      let timer = null, x0 = 0, y0 = 0, longFired = false;
      const cancel = () => { clearTimeout(timer); timer = null; };
      const isAuto = el.dataset.id.startsWith('inc:');
      if (selectable && !isAuto) {
        // pressionar e segurar: entra no modo de seleção
        el.addEventListener('pointerdown', e => {
          longFired = false; x0 = e.clientX; y0 = e.clientY;
          timer = setTimeout(() => { longFired = true; if (navigator.vibrate) navigator.vibrate(25); startSelect(el.dataset.id); }, 450);
        });
        el.addEventListener('pointermove', e => { if (timer && Math.hypot(e.clientX - x0, e.clientY - y0) > 10) cancel(); });
        el.addEventListener('pointerup', cancel); el.addEventListener('pointercancel', cancel); el.addEventListener('pointerleave', cancel);
        el.addEventListener('contextmenu', e => e.preventDefault());
      }
      el.addEventListener('click', () => {
        if (longFired) { longFired = false; return; }
        if (isAuto) { if (!sel.on) { const sid = el.dataset.id.split(':')[1]; if (sid === '__inv') go('invest'); else openInc(incomes().find(s => s.id === sid)); } return; }
        if (selectable && sel.on) { toggleSel(el.dataset.id); return; }
        closeDrill(); openTxSheet(db.txs.find(t => t.id === el.dataset.id));
      });
    });
  }

  /* ---------- seleção de vários lançamentos ---------- */
  const sel = { on: false, ids: new Set(), visible: [] };
  function startSelect(id) {
    sel.on = true;
    if (id) sel.ids.add(id);
    syncSel();
  }
  function toggleSel(id) {
    if (sel.ids.has(id)) sel.ids.delete(id); else sel.ids.add(id);
    syncSel();
  }
  function endSelect() {
    sel.on = false; sel.ids.clear();
    syncSel();
  }
  function syncSel() {
    document.body.classList.toggle('selecting', sel.on);
    $('#selbar').classList.toggle('hidden', !sel.on);
    $$('#tx-list .tx').forEach(el => el.classList.toggle('sel', sel.ids.has(el.dataset.id)));
    const n = sel.ids.size;
    const chosen = db.txs.filter(t => sel.ids.has(t.id));
    $('#sel-count').textContent = n ? `${n} selecionado${n > 1 ? 's' : ''}` : 'Toque para selecionar';
    const sum = chosen.filter(t => !t.off).reduce((s, t) => s + t.amount, 0);
    $('#sel-sum').textContent = n ? `soma ${fmt(sum)}` : '';
    const allOff = n && chosen.every(t => t.off);
    $('#sel-off span').textContent = allOff ? 'Ligar' : 'Desligar';
    $('#sel-all').textContent = sel.visible.length && sel.visible.every(id => sel.ids.has(id)) ? 'Limpar' : 'Todos';
    ['#sel-off', '#sel-update', '#sel-del'].forEach(k => { $(k).disabled = !n; });
    $('#sel-edit').disabled = n !== 1;
  }
  $('#sel-close').addEventListener('click', endSelect);
  $('#select-btn').addEventListener('click', () => startSelect());
  $('#sel-all').addEventListener('click', () => {
    const all = sel.visible.every(id => sel.ids.has(id));
    sel.visible.forEach(id => all ? sel.ids.delete(id) : sel.ids.add(id));
    syncSel();
  });
  $('#sel-off').addEventListener('click', () => {
    const chosen = db.txs.filter(t => sel.ids.has(t.id));
    const turnOn = chosen.every(t => t.off);
    chosen.forEach(t => { if (turnOn) delete t.off; else t.off = true; });
    save(); toast(`${chosen.length} lançamento${chosen.length > 1 ? 's' : ''} ${turnOn ? 'ligado' : 'desligado'}${chosen.length > 1 ? 's' : ''}`);
    endSelect(); renderList();
  });
  $('#sel-edit').addEventListener('click', () => {
    const t = db.txs.find(x => sel.ids.has(x.id));
    endSelect(); if (t) openTxSheet(t);
  });
  $('#sel-del').addEventListener('click', async () => {
    const n = sel.ids.size;
    if (!(await ask({ title: `Excluir ${n} lançamento${n > 1 ? 's' : ''}?`, text: 'Não dá para desfazer. Se quiser só tirar das contas, use “Desligar”.', ok: 'Excluir', danger: true }))) return;
    db.txs = db.txs.filter(t => !sel.ids.has(t.id));
    save(); toast(`${n} lançamento${n > 1 ? 's' : ''} excluído${n > 1 ? 's' : ''}`);
    endSelect(); renderList();
  });
  // atualizar em lote: categoria, conta ou inverter entrada/saída
  function openBulk() {
    const n = sel.ids.size;
    $('#bulk-title').textContent = `Atualizar ${n} lançamento${n > 1 ? 's' : ''}`;
    $('#bulk-cats').innerHTML = CATS.map(c => `<button type="button" class="pick" data-cat="${c.id}">${tile(c.id)}${c.name}</button>`).join('');
    const srcs = pocketSources();
    $('#bulk-pockets-lbl').classList.toggle('hidden', !srcs.length);
    $('#bulk-pockets').innerHTML = srcs.map(s => `<button type="button" class="pick" data-pocket="${s.id}">${incTile(s.type)}${esc(s.name)}</button>`).join('');
    $('#bulk-accts').innerHTML = db.banks.map(b => `<button type="button" class="pick" data-bank="${b.id}">${mono(b)}${esc(acctLabel(b))}</button>`).join('');
    $('#bulk-sheet').classList.remove('hidden'); $('#bulk-backdrop').classList.remove('hidden');
  }
  const closeBulk = () => { $('#bulk-sheet').classList.add('hidden'); $('#bulk-backdrop').classList.add('hidden'); };
  function applyBulk(fn, msg) {
    const chosen = db.txs.filter(t => sel.ids.has(t.id));
    chosen.forEach(fn);
    save(); closeBulk(); toast(`${msg} em ${chosen.length} lançamento${chosen.length > 1 ? 's' : ''}`);
    endSelect(); renderList();
  }
  $('#sel-update').addEventListener('click', openBulk);
  $('#bulk-backdrop').addEventListener('click', closeBulk);
  $('#bulk-close').addEventListener('click', closeBulk);
  $('#bulk-cats').addEventListener('click', e => { const b = e.target.closest('button'); if (b) applyBulk(t => { t.cat = b.dataset.cat; }, `Categoria “${CAT[b.dataset.cat].name}” aplicada`); });
  $('#bulk-pockets').addEventListener('click', e => { const b = e.target.closest('button'); if (b) applyBulk(t => { t.pocket = b.dataset.pocket; }, `Saldo “${incomes().find(s => s.id === b.dataset.pocket)?.name}” aplicado`); });
  $('#bulk-accts').addEventListener('click', e => { const b = e.target.closest('button'); if (b) applyBulk(t => { t.bank = b.dataset.bank; }, `Conta “${acctLabel(bankById(b.dataset.bank))}” aplicada`); });
  $('#bulk-flip').addEventListener('click', () => applyBulk(t => { t.amount = -t.amount; }, 'Entrada e saída invertidas'));
  $('#bulk-date').addEventListener('change', e => { if (e.target.value) applyBulk(t => { t.date = e.target.value; }, 'Data alterada'); e.target.value = ''; });
  function renderList() {
    const L = ui.list;
    const chip = (key, val, label, extra = '') => `<button class="chip ${L[key] === val ? 'on' : ''}" data-k="${key}" data-v="${esc(val)}">${extra}${esc(label)}</button>`;
    $('#list-chips').innerHTML = (L.merchant ? `<button class="chip on" data-k="merchant" data-v="">${esc(L.merchant)} ✕</button>` : '')
      + chip('type', '', 'Tudo') + chip('type', 'out', 'Saídas') + chip('type', 'in', 'Entradas')
      + db.banks.map(b => chip('bank', b.id, acctLabel(b), `<i style="background:${acctColor(b)}"></i>`)).join('');
    $('#list-cats').innerHTML = chip('cat', '', 'Todas as categorias') + CATS.filter(c => db.txs.some(t => t.cat === c.id)).map(c => chip('cat', c.id, c.name, `<i style="background:${c.color}"></i>`)).join('');
    $$('#list-chips .chip, #list-cats .chip').forEach(b => b.addEventListener('click', () => {
      const k = b.dataset.k, v = b.dataset.v;
      if (k === 'merchant') L.merchant = null; else L[k] = (L[k] === v && v !== '') ? '' : v;
      L.limit = 120; renderList();
    }));
    $('#search').value = L.q;
    const q = norm(L.q);
    const list = db.txs.concat(virtualIncome()).filter(t =>
      (!L.bank || t.bank === L.bank) && (!L.cat || t.cat === L.cat) &&
      (!L.type || (L.type === 'in' ? t.amount > 0 : t.amount < 0)) &&
      (!L.merchant || merchant(t.desc) === L.merchant) &&
      (!q || norm(t.desc).includes(q) || norm(CAT[t.cat]?.name).includes(q) || String(Math.abs(t.amount)).includes(q.replace(',', '.')))
    ).sort((a, b) => (b.date + (b.time || '')).localeCompare(a.date + (a.time || '')));
    const T = totals(list);
    $('#list-summary').innerHTML = `<div><small>Entradas</small><b style="color:var(--in)">${fmt(T.inc)}</b></div><div><small>Saídas</small><b style="color:var(--out)">${fmt(T.out)}</b></div>`;
    const box = $('#tx-list');
    box.innerHTML = txRows(list.slice(0, L.limit), true) + (list.length > L.limit ? `<button class="ghost load-more" id="load-more">Mostrar mais (${list.length - L.limit})</button>` : '');
    sel.visible = list.slice(0, L.limit).filter(t => !t.virtual).map(t => t.id);
    bindTxRows(box, true);
    if (sel.on) syncSel();
    const offN = list.filter(t => t.off).length;
    $('#off-note').textContent = offN ? `${offN} desligado${offN > 1 ? 's' : ''} (fora das contas)` : '';
    $('#load-more')?.addEventListener('click', () => { L.limit += 200; renderList(); });
  }
  let searchT;
  $('#search').addEventListener('input', e => { clearTimeout(searchT); searchT = setTimeout(() => { ui.list.q = e.target.value; ui.list.limit = 120; renderList(); }, 150); });

  /* =================== novo lançamento (teclado próprio) =================== */
  const sheet = { editing: null, type: 'out', cat: null, catTouched: false, autoWhen: true, amt: '', bank: null, pocket: null, method: null };
  const PAY_KEY = () => spaceKey(spaces.active) + ':pay';
  const lastPay = () => { try { return JSON.parse(localStorage.getItem(PAY_KEY())) || {}; } catch (e) { return {}; } };
  // escolhe um banco válido para a forma de pagamento (o último usado com ela, se houver)
  function fitBank() {
    const m = methodById(sheet.method);
    if (!m?.bank) { sheet.bank = null; return; }
    const list = banksFor(m);
    if (list.some(b => b.id === sheet.bank)) return;
    const remembered = lastPay().banks?.[m.id];
    sheet.bank = list.find(b => b.id === remembered)?.id || list[0]?.id || (m.bank === 'dinheiro' ? '__new' : null);
  }
  function amountText() {
    if (!sheet.amt) return '0';
    const [i, d] = sheet.amt.split(',');
    const int = (+i || 0).toLocaleString('pt-BR');
    return d !== undefined ? `${int},${d}` : int;
  }
  function openTxSheet(tx, type) {
    closeDrill(); closePeriod();
    sheet.editing = tx || null;
    sheet.type = tx ? (tx.amount > 0 ? 'in' : 'out') : (type || 'out');
    sheet.cat = tx ? tx.cat : null;
    sheet.catTouched = !!tx;
    sheet.autoWhen = !tx;
    sheet.amt = tx ? Math.abs(tx.amount).toFixed(2).replace('.', ',').replace(/,00$/, '') : '';
    let last = null;
    try { last = localStorage.getItem(spaceKey(spaces.active) + ':lastBank'); } catch (e) { /* */ }
    sheet.pocket = tx?.pocket || null;
    sheet.bank = tx ? tx.bank : (bankById(last) ? last : (db.banks.find(b => b.type !== 'credito')?.id || db.banks[0]?.id || null));
    const enabled = enabledMethods();
    sheet.method = tx ? (tx.method || methodFromBank(bankById(tx.bank)) || enabled[0]?.id) : (enabled.some(m => m.id === lastPay().method) ? lastPay().method : enabled[0]?.id);
    if (!tx) { sheet.bank = null; fitBank(); }
    $('#tx-desc').value = tx ? tx.desc : '';
    $('#tx-date').value = tx ? tx.date : todayStr();
    $('#tx-time').value = tx ? (tx.time || '') : nowTime();
    $('#when-inputs').classList.add('hidden');
    $('#tx-delete').classList.toggle('hidden', !tx);
    $('#tx-off').classList.toggle('hidden', !tx);
    $('#tx-off').textContent = tx?.off ? 'Ligar' : 'Desligar';
    syncSheet();
    $('#tx-sheet').classList.remove('hidden'); $('#sheet-backdrop').classList.remove('hidden');
  }
  function syncSheet() {
    const f = $('#tx-sheet');
    f.classList.toggle('is-in', sheet.type === 'in'); f.classList.toggle('is-out', sheet.type === 'out');
    $$('#tx-sheet .seg button').forEach(b => b.classList.toggle('active', b.dataset.type === sheet.type));
    $('#amount-text').textContent = amountText();
    const acctType = bankById(sheet.bank)?.type;
    if (!sheet.catTouched) {
      const d = $('#tx-desc').value;
      sheet.cat = d.trim() ? guessCat(d, sheet.type === 'in' ? 1 : -1, acctType) : (sheet.type === 'in' ? (acctType === 'vr' || acctType === 'va' ? 'beneficios' : 'receitas') : null);
    }
    const opts = CATS.filter(c => sheet.type === 'in' ? (c.income || c.neutral || c.id === 'pessoas') : !c.income);
    if (sheet.cat) opts.sort((a, b) => (b.id === sheet.cat) - (a.id === sheet.cat));
    $('#cat-picker').innerHTML = opts.map(c => `<button type="button" class="pick ${sheet.cat === c.id ? 'on' : ''}" data-cat="${c.id}">${tile(c.id)}${c.name}</button>`).join('');
    // como pagou → de qual banco/cartão
    const methods = enabledMethods();
    if (sheet.method && !methods.some(m => m.id === sheet.method) && methodById(sheet.method)) methods.push(methodById(sheet.method));
    $('#method-lbl').textContent = sheet.type === 'in' ? 'Como você recebeu?' : 'Como você pagou?';
    $('#method-picker').innerHTML = methods.map(m => `<button type="button" class="pick ${sheet.method === m.id ? 'on' : ''}" data-method="${m.id}"><span class="tile" style="background:var(--bg-4);color:var(--text-2)">${svg(m.ic)}</span>${esc(m.name)}</button>`).join('')
      + `<button type="button" class="pick add" data-method="__manage">+ Formas</button>`;
    const m = methodById(sheet.method);
    const showBank = !!m?.bank;
    $('#bank-lbl').classList.toggle('hidden', !showBank);
    $('#acct-picker').classList.toggle('hidden', !showBank);
    if (showBank) {
      $('#bank-lbl').textContent = sheet.type === 'in' && m.bank === 'conta' ? 'Em qual banco?' : BANK_ASK[m.bank];
      let list = banksFor(m);
      if (!list.length && m.bank === 'dinheiro') list = [{ id: '__new', name: 'Carteira', inst: 'carteira', type: 'dinheiro' }];
      $('#acct-picker').innerHTML = list.map(b => `<button type="button" class="pick ${sheet.bank === b.id ? 'on' : ''}" data-bank="${b.id}">${mono(b)}${esc(b.name)}</button>`).join('')
        + (list.length ? '' : `<span class="pick-hint">Nenhum cadastrado ainda (opcional)</span>`)
        + `<button type="button" class="pick add" data-bank="__add">+ ${m.bank === 'credito' ? 'Novo cartão' : m.bank === 'vr' || m.bank === 'va' ? 'Novo vale' : m.bank === 'dinheiro' ? 'Outra carteira' : 'Novo banco'}</button>`;
    }
    const srcs = pocketSources();
    $('#pocket-pick').classList.toggle('hidden', !srcs.length);
    if (srcs.length) {
      const auto = pocketOf({ bank: sheet.bank, amount: -1 }, srcs);
      const cur = sheet.pocket || (sheet.type === 'in' ? null : auto);
      $('#pocket-pick-lbl').textContent = sheet.type === 'in' ? 'Entra no saldo' : 'Sai do saldo';
      $('#pocket-picker').innerHTML = (sheet.type === 'in' ? `<button type="button" class="pick ${!cur ? 'on' : ''}" data-pocket="">Nenhum</button>` : '') +
        srcs.map(s => `<button type="button" class="pick ${cur === s.id ? 'on' : ''}" data-pocket="${s.id}">${incTile(s.type)}${esc(s.name)}</button>`).join('');
    }
    updateWhen();
  }
  function updateWhen() {
    const d = $('#tx-date').value, t = $('#tx-time').value;
    $('#when-text').textContent = sheet.autoWhen ? `Hoje, ${nowTime()} · anotado automaticamente` : `${d ? dayLabel(d) : ''}${t ? ', ' + t : ''}`;
  }
  $('#keypad').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    if (navigator.vibrate) navigator.vibrate(8);
    const k = b.dataset.k === 'back' ? 'back' : b.textContent.trim();
    let a = sheet.amt;
    if (k === 'back') a = a.slice(0, -1);
    else if (k === ',') { if (!a.includes(',')) a = (a || '0') + ','; }
    else {
      const [i, d] = a.split(',');
      if (d !== undefined) { if (d.length < 2) a += k; }
      else if ((i || '').replace(/^0+/, '').length < 9) a = (a === '0' ? '' : a) + k;
    }
    sheet.amt = a;
    $('#amount-text').textContent = amountText();
  });
  $('#tx-sheet .seg').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; sheet.type = b.dataset.type; if (sheet.cat && !!CAT[sheet.cat].income !== (sheet.type === 'in')) sheet.catTouched = false; syncSheet(); });
  $('#cat-picker').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; sheet.cat = b.dataset.cat; sheet.catTouched = true; syncSheet(); });
  $('#acct-picker').addEventListener('click', async e => {
    const b = e.target.closest('button'); if (!b) return;
    if (b.dataset.bank === '__add') {
      const m = methodById(sheet.method);
      const name = await askText({
        title: m.bank === 'credito' ? 'Novo cartão de crédito' : m.bank === 'vr' || m.bank === 'va' ? 'Novo vale' : m.bank === 'dinheiro' ? 'Novo lugar para o dinheiro' : 'Novo banco',
        icon: m.bank === 'credito' ? 'card' : m.bank === 'vr' ? 'food' : m.bank === 'va' ? 'cart' : m.bank === 'dinheiro' ? 'cash' : 'bank', ok: 'Adicionar',
        input: { placeholder: m.bank === 'credito' ? 'ex.: Nubank, Itaú' : m.bank === 'vr' || m.bank === 'va' ? 'ex.: VR, Alelo, Ticket' : m.bank === 'dinheiro' ? 'ex.: Carteira, Cofrinho' : 'ex.: Nubank, Santander' } });
      if (!name?.trim()) return;
      const n = norm(name);
      const inst = INSTS.find(i => i.id !== 'outro' && n.includes(norm(i.name).split(' ')[0]));
      const acc = ensureAccount({ name: name.trim(), inst: inst?.id || (m.bank === 'dinheiro' ? 'carteira' : 'outro'), type: m.bank });
      save(); sheet.bank = acc.id; syncSheet(); return;
    }
    sheet.bank = b.dataset.bank; syncSheet();
  });
  $('#method-picker').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    if (b.dataset.method === '__manage') { closeSheet(); go('ajustes'); setTimeout(() => $('#methods-edit').scrollIntoView({ behavior: 'smooth', block: 'center' }), 200); return; }
    sheet.method = b.dataset.method; sheet.bank = null; fitBank(); syncSheet();
  });
  $('#pocket-picker').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; sheet.pocket = b.dataset.pocket || null; syncSheet(); });
  $('#tx-desc').addEventListener('input', () => { if (!sheet.catTouched) syncSheet(); });
  $('#when-edit').addEventListener('click', () => { sheet.autoWhen = false; $('#when-inputs').classList.toggle('hidden'); updateWhen(); });
  $('#tx-date').addEventListener('input', updateWhen); $('#tx-time').addEventListener('input', updateWhen);
  const closeSheet = () => { $('#tx-sheet').classList.add('hidden'); $('#sheet-backdrop').classList.add('hidden'); };
  $('#tx-cancel').addEventListener('click', closeSheet);
  $('#sheet-backdrop').addEventListener('click', closeSheet);
  $('#tx-off').addEventListener('click', () => {
    const t = sheet.editing && db.txs.find(x => x.id === sheet.editing.id);
    if (!t) return;
    if (t.off) delete t.off; else t.off = true;
    save(); closeSheet(); render(); toast(t.off ? 'Lançamento desligado: fica salvo, mas fora das contas' : 'Lançamento ligado de novo');
  });
  $('#tx-delete').addEventListener('click', async () => {
    if (!sheet.editing || !(await ask({ title: 'Excluir este lançamento?', text: `${sheet.editing.desc} · ${fmt(Math.abs(sheet.editing.amount))}. Não dá para desfazer.`, ok: 'Excluir', danger: true }))) return;
    db.txs = db.txs.filter(t => t.id !== sheet.editing.id); save(); closeSheet(); render(); toast('Lançamento excluído');
  });
  $('#tx-sheet').addEventListener('submit', async e => {
    e.preventDefault();
    const v = Math.abs(Parsers.parseAmount(sheet.amt || '0'));
    if (!v || !isFinite(v)) {
      const el = $('#amount-display'); el.classList.remove('shake'); void el.offsetWidth; el.classList.add('shake');
      toast('Digite o valor no teclado'); return;
    }
    const amount = sheet.type === 'in' ? v : -v;
    const desc = $('#tx-desc').value.trim() || (CAT[sheet.cat]?.name ?? (sheet.type === 'in' ? 'Entrada' : 'Gasto'));
    const meth = methodById(sheet.method);
    let bankId = sheet.bank;
    if (bankId === '__new' || (meth?.bank === 'dinheiro' && !bankId)) bankId = ensureAccount({ name: 'Carteira', inst: 'carteira', type: 'dinheiro' }).id;
    if (meth?.bank && !bankId && banksFor(meth).length) { toast(`${BANK_ASK[meth.bank]} Escolha um ou toque em “+ Novo”.`); return; }
    if (!meth?.bank) bankId = null;
    try {
      if (bankId) localStorage.setItem(spaceKey(spaces.active) + ':lastBank', bankId);
      const lp = lastPay(); lp.method = sheet.method; lp.banks ||= {}; if (bankId) lp.banks[sheet.method] = bankId;
      localStorage.setItem(PAY_KEY(), JSON.stringify(lp));
    } catch (err) { /* */ }
    const cat = sheet.cat || guessCat(desc, amount, bankById(bankId)?.type);
    const when = sheet.autoWhen ? { date: todayStr(), time: nowTime() } : { date: $('#tx-date').value || todayStr(), time: $('#tx-time').value || null };
    if (sheet.editing) {
      const t = db.txs.find(x => x.id === sheet.editing.id);
      const changed = t.cat !== cat;
      Object.assign(t, { amount, desc, bank: bankId, cat, method: sheet.method, ...when });
      if (sheet.pocket) t.pocket = sheet.pocket; else delete t.pocket;
      if (changed) {
        const key = merchant(desc);
        const others = db.txs.filter(x => x.id !== t.id && merchant(x.desc) === key && x.cat !== cat && Math.sign(x.amount) === Math.sign(amount));
        db.rules[norm(key)] = cat;
        if (others.length && await ask({ title: 'Mudar os parecidos também?', text: `Há mais ${others.length} lançamento${others.length > 1 ? 's' : ''} de “${key}”. Colocar todos em ${CAT[cat].name}?`, ok: 'Mudar todos', cancel: 'Só este', icon: 'sliders' })) others.forEach(x => { x.cat = cat; });
      }
      toast('Lançamento atualizado');
    } else {
      db.txs.push({ id: uid(), date: when.date, time: when.time, amount, desc, cat, bank: bankId, method: sheet.method, source: 'manual', createdAt: new Date().toISOString(), ...(sheet.pocket ? { pocket: sheet.pocket } : {}) });
      toast(`${sheet.type === 'in' ? 'Entrada' : 'Gasto'} de ${fmt(v)} registrado`);
    }
    save(); closeSheet(); render();
  });

  /* =================== importar =================== */
  const TIP_GENERIC = 'Os nomes dos menus mudam um pouco de versão para versão, mas o caminho costuma ser parecido.';
  function tipFor(inst, type) {
    const i = INST[inst];
    if (!i || inst === 'outro') return 'No app ou no site do banco, abra o extrato, escolha o período e procure por “Exportar”, “Compartilhar” ou “Baixar”. Prefira OFX, CSV ou Excel; PDF também funciona.';
    if (i.kind === 'benefit') return `No app ${i.name}, abra o cartão do benefício e entre em “Extrato”. Se houver opção de exportar ou compartilhar, salve em PDF ou planilha. Se o app não deixar exportar, preencha a planilha modelo (aqui embaixo) ou registre os gastos pelo botão +.`;
    if (type === 'credito') return `No app ${i.name}, vá em Cartão de crédito › Faturas, abra a fatura do mês e procure “Exportar”, “Baixar PDF” ou “Enviar por e-mail”. Alguns bancos mandam a fatura em CSV por e-mail. Importe uma fatura por mês.`;
    const extra = { nubank: ' No Nubank, o extrato pode ser pedido pelo app e chega por e-mail (PDF, CSV ou OFX).', santander: ' No Santander, o internet banking costuma oferecer Excel e OFX, além de PDF.', inter: ' No Inter, o extrato costuma poder ser exportado em PDF, CSV ou OFX.' }[inst] || '';
    return `No app ${i.name}, abra a Conta › Extrato, escolha o período e toque em “Exportar” ou “Compartilhar”. Prefira OFX, CSV ou Excel; PDF também funciona.${extra}`;
  }

  let slots = null;
  function initSlots() {
    const accs = db.banks.filter(b => b.type !== 'dinheiro');
    slots = (accs.length ? accs : [null]).map(b => b ? { inst: b.inst, name: b.name, type: b.type, files: [] } : { inst: '', name: '', type: 'conta', files: [] });
  }
  function renderImport() {
    if (!slots) initSlots();
    $('#import-step1').classList.remove('hidden'); $('#import-step2').classList.add('hidden');
    drawSlots();
  }
  function drawSlots() {
    $('#bank-count').textContent = slots.length;
    const instBtn = (s, i) => `<button type="button" class="inst ${s.inst === i.id ? 'on' : ''}" data-inst="${i.id}"><span class="mono" style="background:${i.color}">${esc(i.mono)}</span><span>${esc(i.name.replace(' Benefícios', ''))}</span></button>`;
    $('#bank-slots').innerHTML = slots.map((s, n) => {
      const inst = INST[s.inst];
      const b = { name: s.name || inst?.name || `${n + 1}`, inst: s.inst || 'outro' };
      return `<div class="slot" data-i="${n}">
        <div class="slot-head">${mono(b)}<div class="grow"><b>${esc(s.name || inst?.name || `Conta ${n + 1}`)}</b><small>${TYPES[s.type].name}${s.files.length ? ` · ${s.files.length} arquivo${s.files.length > 1 ? 's' : ''}` : ''}</small></div></div>
        ${s.inst && !s.picking ? `<button type="button" class="ghost small change-inst" style="margin-top:0">Trocar banco ou benefício</button>` : `
        <label class="lbl">Banco</label>
        <div class="inst-grid">${INSTS.filter(i => i.kind === 'bank').map(i => instBtn(s, i)).join('')}</div>
        <label class="lbl">Vale-refeição / alimentação</label>
        <div class="inst-grid">${INSTS.filter(i => i.kind === 'benefit').map(i => instBtn(s, i)).join('')}</div>`}
        ${s.inst === 'outro' ? `<input class="slot-name" style="margin-top:10px" placeholder="Nome (ex.: Sicredi, Neon)" value="${esc(s.name)}">` : ''}
        <label class="lbl">Tipo de extrato</label>
        <div class="types">${['conta', 'credito', 'vr', 'va'].map(t => `<button type="button" class="type ${s.type === t ? 'on' : ''}" data-type="${t}">${svg(TYPES[t].ic)}${TYPES[t].name}</button>`).join('')}</div>
        <details class="tip"><summary>Como baixar este extrato</summary><p>${esc(tipFor(s.inst, s.type))}</p><p style="opacity:.7">${TIP_GENERIC}</p></details>
        <label class="drop"><input type="file" multiple accept=".pdf,.ofx,.qfx,.csv,.txt,.xls,.xlsx,.ods,.json" hidden>
          <span class="tile">${svg('file')}</span><span><b>Escolher arquivos</b><small>PDF, OFX, CSV, Excel ou TXT · vários de uma vez</small></span></label>
        <ul class="files">${s.files.map((f, j) => `<li><span>${esc(f.name)}</span><em>${Math.max(1, Math.round(f.size / 1024))} KB</em><button data-rm="${j}" aria-label="Remover">×</button></li>`).join('')}</ul>
      </div>`;
    }).join('');
    $$('#bank-slots .slot').forEach(el => {
      const s = slots[+el.dataset.i];
      el.querySelectorAll('.inst').forEach(b => b.addEventListener('click', () => {
        const i = INST[b.dataset.inst];
        s.inst = i.id; s.name = i.id === 'outro' ? '' : i.name; s.picking = false;
        if (i.kind === 'benefit' && !['vr', 'va'].includes(s.type)) s.type = 'vr';
        if (i.kind === 'bank' && ['vr', 'va'].includes(s.type)) s.type = 'conta';
        drawSlots();
      }));
      el.querySelector('.change-inst')?.addEventListener('click', () => { s.picking = true; drawSlots(); });
      el.querySelector('.slot-name')?.addEventListener('input', e => { s.name = e.target.value; updateReadBtn(); });
      el.querySelectorAll('.type').forEach(b => b.addEventListener('click', () => { s.type = b.dataset.type; drawSlots(); }));
      const input = el.querySelector('input[type=file]');
      input.addEventListener('change', async () => {
        const picked = [...input.files]; input.value = '';
        for (const f of picked) {
          const bkData = await looksLikeBackupFile(f);
          if (bkData) { toast('Esse arquivo é um backup do app, não um extrato'); await restoreBackup(bkData); return; }
          if (!s.files.some(x => x.name === f.name && x.size === f.size)) s.files.push(f);
        }
        drawSlots();
      });
      el.querySelectorAll('[data-rm]').forEach(b => b.addEventListener('click', e => { e.preventDefault(); s.files.splice(+b.dataset.rm, 1); drawSlots(); }));
    });
    updateReadBtn();
  }
  function updateReadBtn() {
    const n = slots.reduce((a, x) => a + x.files.length, 0);
    const missing = slots.some(s => s.files.length && !s.inst);
    $('#files-total').textContent = !n ? 'Escolha a instituição e os arquivos de cada conta.' : missing ? 'Escolha o banco ou benefício de cada conta com arquivos.' : `${n} arquivo${n > 1 ? 's' : ''} pronto${n > 1 ? 's' : ''} para ler`;
    $('#read-files').disabled = !n || missing;
  }
  $('#bank-plus').addEventListener('click', () => { if (slots.length < 12) { slots.push({ inst: '', name: '', type: 'conta', files: [] }); drawSlots(); } });
  $('#bank-minus').addEventListener('click', async () => {
    if (slots.length <= 1) return;
    const last = slots[slots.length - 1];
    if (last.files.length && !(await ask({ title: `Remover a conta ${slots.length}?`, text: `Ela tem ${last.files.length} arquivo${last.files.length > 1 ? 's' : ''} escolhido${last.files.length > 1 ? 's' : ''} para importar.`, ok: 'Remover', danger: true }))) return;
    slots.pop(); drawSlots();
  });
  $('#template-btn').addEventListener('click', () => {
    const d = new Date(); const dd = `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`;
    download('planilha-modelo-extrato.csv', '﻿Data;Descrição;Valor\n' + `${dd};Almoço no restaurante;-32,50\n${dd};Mercado;-120,00\n${dd};Recarga do benefício;800,00\n`, 'text/csv');
  });

  let review = [];
  $('#read-files').addEventListener('click', async () => {
    const btn = $('#read-files');
    btn.disabled = true;
    review = [];
    const keyOf = (acc, t) => `${acc}|${t.date}|${t.amount.toFixed(2)}|${norm(merchant(t.desc))}`;
    const existing = {};
    for (const t of db.txs) { const b = bankById(t.bank); const k = keyOf(`${norm(b?.name)}:${b?.type}`, t); existing[k] = (existing[k] || 0) + 1; }
    const total = slots.reduce((a, x) => a + x.files.length, 0);
    let done = 0;
    for (const [i, s] of slots.entries()) {
      const name = (s.name || INST[s.inst]?.name || `Conta ${i + 1}`).trim();
      const accKey = `${norm(name)}:${s.type}`;
      for (const f of s.files) {
        btn.textContent = `Lendo ${++done} de ${total}…`;
        const entry = { name, inst: s.inst, type: s.type, file: f.name, kind: '', txs: [], error: null, balance: null, opening: null };
        try {
          const res = await Parsers.parseFile(f, { accountType: s.type, askPassword: (fname, retry) => askText({ title: retry ? 'Senha incorreta' : 'PDF com senha', icon: 'file', ok: 'Abrir', text: `“${fname}” está protegido. Em muitos bancos a senha são números do CPF.`, input: { type: 'password', placeholder: 'Senha do PDF' } }) });
          entry.kind = res.kind; entry.balance = res.balance; entry.opening = res.opening ?? null;
          const seen = {};
          entry.txs = res.transactions.map(t => {
            const tt = { ...t, desc: t.description, cat: guessCat(t.description, t.amount, s.type) };
            const k = keyOf(accKey, tt);
            seen[k] = (seen[k] || 0) + 1;
            tt.dup = (existing[k] || 0) >= seen[k];
            tt.include = !tt.dup;
            return tt;
          });
          for (const [k, n] of Object.entries(seen)) existing[k] = Math.max(existing[k] || 0, n);
          if (!entry.txs.length) entry.error = 'Não encontrei lançamentos neste arquivo. Confira se é o extrato (e não um comprovante) ou tente exportar em OFX, CSV ou Excel.';
        } catch (e) { entry.error = e.message || String(e); }
        review.push(entry);
      }
    }
    btn.textContent = 'Ler extratos'; btn.disabled = false;
    drawReview();
  });

  function drawReview() {
    $('#import-step1').classList.add('hidden'); $('#import-step2').classList.remove('hidden');
    const catOpts = sel => CATS.map(c => `<option value="${c.id}" ${c.id === sel ? 'selected' : ''}>${c.name}</option>`).join('');
    $('#review-files').innerHTML = review.map((f, fi) => {
      const on = f.txs.filter(t => t.include);
      const T = totals(on);
      const dups = f.txs.filter(t => t.dup).length;
      return `<div class="review-file" data-f="${fi}">
        <div class="slot-head" style="margin:0">${mono({ name: f.name, inst: f.inst })}<div class="grow"><h3>${esc(f.name)} · ${TYPES[f.type].short}</h3><p class="rs" style="margin:0">${esc(f.file)}</p></div></div>
        ${f.error ? `<p class="err">${esc(f.error)}</p>` : `
        <p class="rs" style="margin-top:8px">${f.kind} · ${f.txs.length} lançamentos · entrou ${fmt(T.inc)} · saiu ${fmt(T.out)}${dups ? ` · ${dups} já existiam` : ''}</p>
        <div class="rv-actions"><button class="ghost" data-flip="${fi}">Inverter sinais</button><button class="ghost" data-all="${fi}">${on.length === f.txs.length ? 'Desmarcar todos' : 'Marcar todos'}</button></div>
        <div class="review-table">${f.txs.map((t, ti) => `
          <div class="rv ${t.include ? '' : 'off'}" data-t="${ti}">
            <input type="checkbox" ${t.include ? 'checked' : ''} aria-label="Importar">
            <span class="dt">${shortDate(t.date)}</span>
            <span class="dsc">${esc(t.desc)}${t.dup ? '<span class="dup">já importado antes</span>' : ''}</span>
            <span class="amt ${t.amount > 0 ? 'in' : ''}">${t.amount > 0 ? '+' : '−'} ${fmt(Math.abs(t.amount))}</span>
            <select aria-label="Categoria">${catOpts(t.cat)}</select>
          </div>`).join('')}</div>`}
      </div>`;
    }).join('');
    const n = review.reduce((a, f) => a + f.txs.filter(t => t.include).length, 0);
    $('#review-save').textContent = `Importar ${n} lançamento${n === 1 ? '' : 's'}`;
    $('#review-save').disabled = !n;
    $$('#review-files .review-file').forEach(el => {
      const f = review[+el.dataset.f];
      el.querySelectorAll('.rv').forEach(row => {
        const t = f.txs[+row.dataset.t];
        row.querySelector('input').addEventListener('change', e => { t.include = e.target.checked; const y = window.scrollY; drawReview(); window.scrollTo({ top: y }); });
        row.querySelector('select').addEventListener('change', e => { t.cat = e.target.value; });
      });
    });
    $$('[data-flip]').forEach(b => b.addEventListener('click', () => { const f = review[+b.dataset.flip]; f.txs.forEach(t => { t.amount = -t.amount; t.cat = guessCat(t.desc, t.amount, f.type); }); drawReview(); }));
    $$('[data-all]').forEach(b => b.addEventListener('click', () => { const f = review[+b.dataset.all]; const all = f.txs.every(t => t.include); f.txs.forEach(t => { t.include = !all; }); drawReview(); }));
  }
  $('#read-files').addEventListener('click', () => window.scrollTo({ top: 0 }));
  $('#review-back').addEventListener('click', () => { $('#import-step2').classList.add('hidden'); $('#import-step1').classList.remove('hidden'); });
  $('#review-save').addEventListener('click', () => {
    let n = 0;
    for (const f of review) {
      const chosen = f.txs.filter(t => t.include);
      if (!chosen.length) continue;
      const acc = ensureAccount({ name: f.name, inst: f.inst, type: f.type });
      const isNew = !db.txs.some(t => t.bank === acc.id);
      if (isNew && f.opening != null && !(+acc.initial)) acc.initial = f.opening;
      for (const t of chosen) { db.txs.push({ id: uid(), date: t.date, time: null, amount: t.amount, desc: t.desc, cat: t.cat, bank: acc.id, source: 'import', file: f.file, createdAt: new Date().toISOString() }); n++; }
      if (f.balance != null && !(+acc.initial)) {
        const sum = db.txs.filter(t => t.bank === acc.id).reduce((s, t) => s + t.amount, 0);
        acc.initial = Math.round((f.balance - sum) * 100) / 100;
      }
    }
    save(); slots = null; review = []; ui.monthPick = null;
    toast(`${n} lançamentos importados`);
    go('panorama');
  });

  /* =================== mais / exportar =================== */
  function renderMethods() {
    const off = methodsCfg().off;
    $('#methods-edit').innerHTML = allMethods().map(m => `
      <div class="method-row">
        <span class="tile sm" style="background:var(--bg-4);color:var(--text-2)">${svg(m.ic)}</span>
        <div class="grow"><b>${esc(m.name)}</b><small>${m.bank ? BANK_ASK[m.bank].replace('?', '') : 'Não pergunta banco'} · ${m.bank ? banksFor(m).length + ' cadastrado(s)' : ''}</small></div>
        ${m.custom ? `<button class="del" data-mdel="${m.id}" aria-label="Excluir">${svg('trash')}</button>` : ''}
        <button class="switch ${off.includes(m.id) ? '' : 'on'}" data-mtoggle="${m.id}" role="switch" aria-checked="${!off.includes(m.id)}" aria-label="Usar ${esc(m.name)}"><i></i></button>
      </div>`).join('');
    $$('#methods-edit [data-mtoggle]').forEach(b => b.addEventListener('click', () => {
      const id = b.dataset.mtoggle, i = off.indexOf(id);
      if (i >= 0) off.splice(i, 1); else {
        if (enabledMethods().length <= 1) { toast('Deixe pelo menos uma forma ligada'); return; }
        off.push(id);
      }
      save(); renderMethods();
    }));
    $$('#methods-edit [data-mdel]').forEach(b => b.addEventListener('click', async () => {
      const m = methodById(b.dataset.mdel);
      if (!(await ask({ title: `Excluir “${m.name}”?`, text: 'Os lançamentos que já usaram essa forma de pagamento continuam salvos.', ok: 'Excluir', danger: true }))) return;
      methodsCfg().custom = methodsCfg().custom.filter(x => x.id !== m.id); save(); renderMethods();
    }));
  }
  $('#method-new').addEventListener('submit', e => {
    e.preventDefault();
    const name = $('#method-new-name').value.trim();
    if (!name) { $('#method-new-name').focus(); return; }
    methodsCfg().custom.push({ id: 'm' + uid(), name: name.slice(0, 30), bank: $('#method-new-bank').value || null });
    save(); $('#method-new-name').value = ''; renderMethods(); toast(`“${name}” adicionada às formas de pagamento`);
  });
  function renderSettings() {
    renderBackup();
    renderLook();
    renderMethods();
    $('#banks-edit').innerHTML = db.banks.map(b => `
      <div class="bank-edit" data-id="${b.id}">${mono(b)}
        <input class="bn" value="${esc(b.name)}" aria-label="Nome da conta">
        <button class="del" data-del="${b.id}" aria-label="Excluir conta">${svg('trash')}</button>
        <div class="meta"><select class="bt" aria-label="Tipo">${Object.entries(TYPES).map(([k, t]) => `<option value="${k}" ${b.type === k ? 'selected' : ''}>${t.name}</option>`).join('')}</select>
        <input class="ib" inputmode="decimal" value="${(+b.initial || 0).toFixed(2).replace('.', ',')}" aria-label="Saldo inicial"></div>
      </div>`).join('') || `<p class="empty-note">As contas aparecem quando você importa extratos ou registra um gasto.</p>`;
    $$('#banks-edit .bank-edit').forEach(row => {
      const b = bankById(row.dataset.id);
      row.querySelector('.bn').addEventListener('change', e => { b.name = e.target.value.trim() || b.name; save(); renderSettings(); });
      row.querySelector('.bt').addEventListener('change', e => { b.type = e.target.value; save(); toast('Tipo atualizado'); });
      row.querySelector('.ib').addEventListener('change', e => { const v = Parsers.parseAmount(e.target.value); b.initial = isNaN(v) ? 0 : v; save(); toast('Saldo inicial salvo'); });
      row.querySelector('[data-del]').addEventListener('click', async () => {
        const n = db.txs.filter(t => t.bank === b.id).length;
        if (!(await ask({ title: `Excluir “${acctLabel(b)}”?`, text: n ? `Os ${n} lançamentos dessa conta também serão apagados. Não dá para desfazer.` : 'Não dá para desfazer.', ok: 'Excluir', danger: true }))) return;
        db.txs = db.txs.filter(t => t.bank !== b.id); db.banks = db.banks.filter(x => x.id !== b.id);
        incomes().forEach(s => { if (s.bank === b.id) s.bank = null; });
        if (ui.bank === b.id) ui.bank = '';
        save(); renderSettings();
      });
    });
  }
  $('#add-bank').addEventListener('click', async () => {
    const name = await askText({ title: 'Nova conta', icon: 'bank', ok: 'Adicionar', input: { placeholder: 'ex.: Santander, VR, Carteira' } }); if (!name) return;
    const n = norm(name);
    const inst = INSTS.find(i => i.id !== 'outro' && n.includes(norm(i.name).split(' ')[0]));
    ensureAccount({ name, inst: inst?.id || 'outro', type: inst?.kind === 'benefit' ? 'vr' : /carteira|dinheiro/.test(n) ? 'dinheiro' : 'conta' });
    save(); renderSettings();
  });

  // iPhone/iPad: salvar abre a folha de compartilhar (Salvar em Arquivos, Google Drive, WhatsApp, e-mail…)
  async function saveBlob(name, blob) {
    if (IS_IOS && navigator.canShare) {
      const file = new File([blob], name, { type: blob.type });
      if (navigator.canShare({ files: [file] })) {
        try { await navigator.share({ files: [file], title: name }); return true; }
        catch (e) { if (e.name === 'AbortError') return false; /* sem permissão de compartilhar: baixa do jeito normal */ }
      }
    }
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
    return true;
  }
  function download(name, content, type) {
    if (window.Android?.saveFile) { window.Android.saveFile(name, type, content); return Promise.resolve(null); }
    return saveBlob(name, new Blob([content], { type }));
  }
  function downloadBase64(name, b64, type) {
    if (window.Android?.saveBase64) { window.Android.saveBase64(name, type, b64); return Promise.resolve(null); }
    const bin = atob(b64); const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return saveBlob(name, new Blob([bytes], { type }));
  }

  // Um arquivo só: resumo + uma aba por conta/cartão/vale + todos os lançamentos
  async function exportAll() {
    if (!hasData()) { toast('Ainda não há lançamentos para exportar'); return; }
    toast('Montando a planilha…');
    try { await Parsers.loadXLSX(); } catch (e) { toast('Não consegui carregar o gerador de planilhas'); return; }
    const X = window.XLSX;
    const MONEY = '"R$" #,##0.00;[Red]-"R$" #,##0.00';
    const toDate = s => { const d = pd(s); return new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate(), 12)); };
    const sheetFrom = (rows, widths, moneyCols = [], dateCols = [], headerRow = 0) => {
      const ws = X.utils.aoa_to_sheet(rows, { cellDates: true });
      ws['!cols'] = widths.map(w => ({ wch: w }));
      const range = X.utils.decode_range(ws['!ref']);
      for (let R = headerRow + 1; R <= range.e.r; R++) {
        for (const C of moneyCols) { const c = ws[X.utils.encode_cell({ r: R, c: C })]; if (c && c.t === 'n') c.z = MONEY; }
        for (const C of dateCols) { const c = ws[X.utils.encode_cell({ r: R, c: C })]; if (c && (c.t === 'd' || c.t === 'n')) c.z = 'dd/mm/yyyy'; }
      }
      return ws;
    };
    const wb = X.utils.book_new();
    const now = new Date();
    const everything = [...db.txs].sort((a, b) => (a.date + (a.time || '')).localeCompare(b.date + (b.time || '')));
    const sorted = db.txs.filter(t => !t.off).concat(groupVirtual(virtualIncome())).sort((a, b) => (a.date + (a.time || '')).localeCompare(b.date + (b.time || '')));

    // Resumo
    const resumo = [[`Finanças · ${currentSpace().name} · exportado em ${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear()} às ${nowTime()}`], [],
      ['Conta', 'Instituição', 'Tipo', 'Lançamentos', 'Entradas', 'Saídas', 'Saldo atual']];
    for (const b of db.banks) {
      const bt = sorted.filter(t => t.bank === b.id);
      const inc = bt.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0);
      const out = -bt.filter(t => t.amount < 0).reduce((s, t) => s + t.amount, 0);
      resumo.push([b.name, instOf(b).id === 'outro' ? b.name : instOf(b).name, TYPES[b.type]?.name || '', bt.length, inc, out, (+b.initial || 0) + inc - out]);
    }
    resumo.push([], ['Gastos por categoria', '', '', '', 'Total', '% dos gastos']);
    const catTot = {}; let spent = 0;
    for (const t of db.txs) if (isSpend(t)) { catTot[t.cat] = (catTot[t.cat] || 0) - t.amount; spent -= t.amount; }
    Object.entries(catTot).sort((a, b) => b[1] - a[1]).forEach(([c, v]) => resumo.push([CAT[c]?.name || c, '', '', '', v, spent ? `${(v / spent * 100).toFixed(1).replace('.', ',')}%` : '']));
    if (incomes().length) {
      resumo.push([], ['Renda cadastrada', 'Tipo', 'Como conta', 'Conta', 'Por mês', 'Contado até hoje']);
      for (const src of incomes()) {
        const counted = virtualIncome().filter(t => t.inc === src.id).reduce((s, t) => s + t.amount, 0);
        resumo.push([src.name, INC_TYPES[src.type]?.name || '', { daily: 'Todo dia', payday: `Dia ${src.payday}`, off: 'Não conta' }[src.mode], acctLabel(bankById(src.bank)) || '—', src.monthly, r2(counted)]);
      }
    }
    X.utils.book_append_sheet(wb, sheetFrom(resumo, [26, 18, 20, 13, 16, 16, 16], [4, 5, 6], [], 2), 'Resumo');
    if (db.invest?.holdings?.length) {
      const rows = [['Aplicação', 'Tipo', 'Rende pelo', 'Taxa', 'Valor investido', 'Renda por mês (estimada)']];
      for (const h of db.invest.holdings) rows.push([h.name, h.kind === 'fixa' ? 'Renda fixa' : 'Renda variável', h.kind === 'fixa' ? INDEXES[h.index]?.name || '' : 'Média de proventos', h.kind === 'fixa' ? `${String(h.rate).replace('.', ',')}${INDEXES[h.index]?.unit || ''}` : '', h.amount || 0, r2(holdingMonthly(h))]);
      const tt = invTotals(); rows.push([], ['Total', '', '', '', tt.amount, tt.monthly]);
      const prov = db.invest.holdings.filter(h => h.kind === 'variavel').flatMap(h => (h.history || []).map(x => [h.name, x.m, x.v]));
      if (prov.length) { rows.push([], ['Proventos lançados', 'Mês', 'Valor']); prov.sort((a, b) => a[1].localeCompare(b[1])).forEach(r => rows.push([r[0], r[1], r[2]])); }
      X.utils.book_append_sheet(wb, sheetFrom(rows, [28, 16, 20, 14, 18, 22], [2, 4, 5]), 'Investimentos');
    }

    // Uma aba por conta
    const used = new Set(['Resumo', 'Todos os lançamentos']);
    for (const b of db.banks) {
      const bt = sorted.filter(t => t.bank === b.id);
      if (!bt.length) continue;
      let run = +b.initial || 0;
      const rows = [['Data', 'Hora', 'Descrição', 'Categoria', 'Entrada', 'Saída', 'Valor', 'Saldo']];
      for (const t of bt) { run += t.amount; rows.push([toDate(t.date), t.time || '', t.desc, CAT[t.cat]?.name || '', t.amount > 0 ? t.amount : '', t.amount < 0 ? -t.amount : '', t.amount, Math.round(run * 100) / 100]); }
      rows.push([], ['', '', 'Total', '', bt.filter(t => t.amount > 0).reduce((s, t) => s + t.amount, 0), -bt.filter(t => t.amount < 0).reduce((s, t) => s + t.amount, 0), bt.reduce((s, t) => s + t.amount, 0), '']);
      let name = `${b.name} - ${TYPES[b.type]?.short || ''}`.replace(/[\\/?*[\]:]/g, ' ').slice(0, 31).trim();
      let k = 2; while (used.has(name)) name = `${name.slice(0, 28)} ${k++}`;
      used.add(name);
      X.utils.book_append_sheet(wb, sheetFrom(rows, [12, 7, 42, 22, 14, 14, 14, 14], [4, 5, 6, 7], [0]), name);
    }

    // Todos
    const allRows = [['Data', 'Hora', 'Conta', 'Tipo de conta', 'Descrição', 'Categoria', 'Valor', 'Origem', 'Situação']];
    for (const t of everything.concat(groupVirtual(virtualIncome())).sort((a, b) => a.date.localeCompare(b.date))) { const b = bankById(t.bank); allRows.push([toDate(t.date), t.time || '', b?.name || '', TYPES[b?.type]?.name || '', t.desc, CAT[t.cat]?.name || '', t.amount, t.virtual ? 'Renda automática' : t.source === 'manual' ? 'Manual' : 'Extrato', t.off ? 'Desligado' : 'Ativo']); }
    X.utils.book_append_sheet(wb, sheetFrom(allRows, [12, 7, 18, 18, 42, 22, 14, 10, 11], [6], [0]), 'Todos os lançamentos');

    const b64 = X.write(wb, { bookType: 'xlsx', type: 'base64', cellDates: true });
    downloadBase64(`financas-${norm(currentSpace().name).replace(/[^a-z0-9]+/g, '-')}-${todayStr()}.xlsx`, b64, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    if (!window.Android && !IS_IOS) toast('Planilha baixada');
  }

  $('#export-csv').addEventListener('click', () => {
    const rows = [['Data', 'Hora', 'Conta', 'Tipo', 'Descrição', 'Categoria', 'Valor']].concat(
      [...db.txs].sort((a, b) => a.date.localeCompare(b.date)).map(t => { const b = bankById(t.bank); return [shortDate(t.date), t.time || '', b?.name || '', TYPES[b?.type]?.name || '', t.desc, CAT[t.cat]?.name || t.cat, t.amount.toFixed(2).replace('.', ',')]; }));
    download(`financas-${todayStr()}.csv`, '﻿' + rows.map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(';')).join('\n'), 'text/csv');
  });
  $('#clear-all').addEventListener('click', async () => {
    if (!(await ask({ title: 'Apagar este painel?', text: `Todas as contas e lançamentos de “${currentSpace().name}” serão apagados. Não dá para desfazer.`, ok: 'Apagar tudo', danger: true }))) return;
    db = emptyDb(); save(); ui.bank = ''; slots = null; toast('Painel apagado'); go('panorama');
  });

  /* =================== sua renda =================== */
  const incTile = k => { const t = INC_TYPES[k], c = CAT[t.cat].color; return `<span class="tile" style="background:${alpha(c, .14)};color:${c}">${svg(t.ic)}</span>`; };
  const modeText = s => s.mode === 'daily' ? `todo dia (${fmt(freeMonthly(s) / dimOf(todayStr()))}/dia este mês)` : s.mode === 'payday' ? `${fmt(freeMonthly(s))} no dia ${s.payday}` : 'não entra nas contas';
  const isLive = s => s.mode !== 'off' && freeMonthly(s) > 0 && s.start <= todayStr() && (!s.end || s.end >= todayStr());
  function incomeNumbers() {
    const today = todayStr(), month = today.slice(0, 7);
    const v = virtualIncome();
    const perDay = incomes().filter(s => isLive(s) && s.mode === 'daily').reduce((a, s) => a + s.monthly / dimOf(today), 0);
    return {
      today: v.filter(t => t.date === today).reduce((a, t) => a + t.amount, 0),
      perDay,
      month: v.filter(t => t.date.startsWith(month)).reduce((a, t) => a + t.amount, 0),
      monthly: incomes().filter(isLive).reduce((a, s) => a + s.monthly, 0),
      offTotal: incomes().filter(s => s.mode === 'off').reduce((a, s) => a + (s.monthly || 0), 0),
    };
  }
  // entradas dos extratos que parecem a mesma renda cadastrada aqui (contaria duas vezes)
  function findDupIncome() {
    const out = new Set();
    for (const src of incomes()) {
      if (src.mode === 'off' || !(src.monthly > 0) || src.type === 'invest') continue;
      const cat = INC_TYPES[src.type].cat;
      for (const t of db.txs) {
        if (t.off || t.dupOk || t.amount <= 0 || t.cat !== cat || t.date < src.start || (src.end && t.date > src.end)) continue;
        if (Math.abs(t.amount - src.monthly) / src.monthly <= 0.2) out.add(t);
      }
    }
    return [...out];
  }
  /* ---------- saldos separados por renda, com virada de dia ----------
     Cada fonte (salário, VR, rendimentos, outras) é um saldo próprio:
     saldo de hoje = o que sobrou (ou faltou) ontem + o que entrou hoje − o que saiu hoje. */
  const pocketSources = () => incomeSources().filter(s => s.mode !== 'off' && freeMonthly(s) > 0 && s.start);
  function pocketOf(t, srcs = pocketSources()) {
    if (t.pocket && srcs.some(s => s.id === t.pocket)) return t.pocket;
    if (t.method === 'vr' || t.method === 'va') { const v = srcs.find(s => s.type === 'vr'); if (v) return v.id; }
    if (t.method === 'folha') { const s = srcs.find(x => x.type === 'salario'); if (s) return s.id; }
    const bySrc = srcs.find(s => s.bank && s.bank === t.bank);
    if (bySrc) return bySrc.id;
    const b = bankById(t.bank);
    if (b && (b.type === 'vr' || b.type === 'va')) { const v = srcs.find(s => s.type === 'vr'); if (v) return v.id; }
    return (srcs.find(s => s.type === 'salario') || srcs.find(s => s.type === 'extra') || srcs[0])?.id || null;
  }
  let pCache = null;
  function pockets() {
    const srcs = pocketSources();
    const today = todayStr();
    const key = today + JSON.stringify(incomeSources()) + db.txs.length + JSON.stringify(db.txs.map(t => [t.amount, t.pocket, t.off, t.bank, t.date, t.cat]));
    if (pCache && pCache.db === db && pCache.key === key) return pCache.list;
    const inc = {}, out = {};
    for (const t of virtualIncome()) { const k = `${t.inc}|${t.date}`; inc[k] = r2((inc[k] || 0) + t.amount); }
    for (const t of db.txs) {
      if (t.off || isNeutral(t)) continue;
      if (t.amount > 0 && !t.pocket) continue; // entradas dos extratos só entram num saldo se você escolher
      const p = pocketOf(t, srcs); if (!p) continue;
      const k = `${p}|${t.date}`;
      if (t.amount > 0) inc[k] = r2((inc[k] || 0) + t.amount); else out[k] = r2((out[k] || 0) - t.amount);
    }
    const list = srcs.map(s => {
      const end = s.end && s.end < today ? s.end : today;
      let bal = 0; const days = [];
      for (let d = s.start; d <= end; d = addDays(d, 1)) {
        const i = inc[`${s.id}|${d}`] || 0, o = out[`${s.id}|${d}`] || 0;
        const open = bal; bal = r2(bal + i - o);
        days.push({ d, open, inc: i, out: o, bal });
      }
      const last = days[days.length - 1] || { d: today, open: 0, inc: 0, out: 0, bal: 0 };
      return { src: s, days, today: last, ended: !!(s.end && s.end < today) };
    });
    pCache = { db, key, list };
    return list;
  }
  const POCKET_GRAD = { salario: ['#0d8f68', '#17306f'], vr: ['#1f7a3a', '#6b5a12'], invest: ['#0b6e73', '#1b3b8f'], extra: ['#5b2f6e', '#17306f'] };
  const sign = v => (v > 0 ? '+' : v < 0 ? '−' : '') + fmt(Math.abs(v));
  function pocketCard(p, big) {
    const t = p.today, ty = INC_TYPES[p.src.type], g = POCKET_GRAD[p.src.type] || POCKET_GRAD.extra;
    return `<button class="pocket ${big ? 'big' : ''}" data-pocket="${p.src.id}" style="background:linear-gradient(145deg, ${g[0]}, ${g[1]})">
      <div class="pk-top"><span class="pk-ic">${svg(ty.ic)}</span><span class="pk-name">${esc(p.src.name)}</span>${p.ended ? '<span class="pk-tag">encerrada</span>' : ''}</div>
      <div class="pk-lbl">${p.ended ? 'Saldo final' : 'Disponível hoje'}</div>
      <div class="pk-val ${t.bal < 0 ? 'neg' : ''}">${t.bal < 0 ? '−' : ''}${fmt(Math.abs(t.bal))}</div>
      <div class="pk-flow"><span>Ontem ${sign(t.open)}</span><span>Entrou +${fmt(t.inc)}</span><span>Saiu −${fmt(t.out)}</span></div>
      ${big ? `<div class="pk-spark"><canvas id="spark-${p.src.id}"></canvas></div>` : ''}
    </button>`;
  }
  function bindPockets(root) {
    root.querySelectorAll('.pocket').forEach(el => el.addEventListener('click', () => openPocket(el.dataset.pocket)));
  }
  function drawPocketSparks(list) {
    for (const p of list) {
      const c = document.getElementById(`spark-${p.src.id}`); if (!c) continue;
      const days = p.days.slice(-30);
      setChart(`spark-${p.src.id}`, c, {
        type: 'bar',
        data: { labels: days.map(d => d.d), datasets: [{ data: days.map(d => d.bal), backgroundColor: days.map(d => d.bal < 0 ? 'rgba(255,168,168,0.9)' : 'rgba(255,255,255,0.85)'), borderRadius: 3, borderSkipped: false, barPercentage: 0.7, categoryPercentage: 0.9, maxBarThickness: 9, minBarLength: 2 }] },
        options: { responsive: true, maintainAspectRatio: false, animation: { duration: 500 }, plugins: { legend: { display: false }, tooltip: { enabled: false } }, scales: { x: { display: false }, y: { display: false } }, events: [] },
      });
    }
  }
  function openPocket(id) {
    const p = pockets().find(x => x.src.id === id); if (!p) return;
    const ty = INC_TYPES[p.src.type];
    const c = CAT[ty.cat].color;
    $('#pocket-icon').style.cssText = `background:${alpha(c, .14)};color:${c}`;
    $('#pocket-icon').innerHTML = svg(ty.ic);
    $('#pocket-title').textContent = p.src.name;
    $('#pocket-sub').textContent = `Hoje: ${p.today.bal < 0 ? '−' : ''}${fmt(Math.abs(p.today.bal))} · ${p.src.mode === 'daily' ? `${fmt(p.src.monthly / dimOf(todayStr()))} por dia` : `${fmt(p.src.monthly)} no dia ${p.src.payday}`}`;
    const days = p.days.slice(-31);
    setChart('pocket', $('#chart-pocket'), {
      type: 'bar',
      data: { labels: days.map(d => `${+d.d.slice(8)}/${+d.d.slice(5, 7)}`), datasets: [{ data: days.map(d => d.bal), backgroundColor: days.map(d => d.bal < 0 ? '#ff7a6b' : '#5ee49a'), borderRadius: 5, borderSkipped: false, maxBarThickness: 14 }] },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: { title: it => dayLabel(days[it[0].dataIndex].d), label: c => `Fechou o dia em ${fmt(c.parsed.y)}` } } }, scales: { ...axes(), x: { ...axes().x, ticks: { maxTicksLimit: 6, maxRotation: 0 } } } },
    });
    $('#pocket-days').innerHTML = `<div class="dt-row dt-head"><span>Dia</span><span>Veio de ontem</span><span>Entrou</span><span>Saiu</span><span>Fechou</span></div>` +
      [...p.days].reverse().slice(0, 62).map(d => `<div class="dt-row"><span>${d.d === todayStr() ? 'Hoje' : `${pad(+d.d.slice(8))}/${d.d.slice(5, 7)}`}</span><span class="${d.open < 0 ? 'neg' : ''}">${sign(d.open)}</span><span class="pos">+${fmt(d.inc)}</span><span class="neg">${d.out ? '−' + fmt(d.out) : '—'}</span><b class="${d.bal < 0 ? 'neg' : ''}">${d.bal < 0 ? '−' : ''}${fmt(Math.abs(d.bal))}</b></div>`).join('');
    $('#pocket-sheet').classList.remove('hidden'); $('#pocket-backdrop').classList.remove('hidden');
  }
  const closePocket = () => { $('#pocket-sheet').classList.add('hidden'); $('#pocket-backdrop').classList.add('hidden'); };
  $('#pocket-close').addEventListener('click', closePocket);
  $('#pocket-backdrop').addEventListener('click', closePocket);

  function renderIncomeCard() {
    const el = $('#income-home');
    if (!incomes().length) {
      el.innerHTML = `<button class="income-card" data-go="renda"><span class="ic-ic">${svg('wallet')}</span><span class="grow"><b>Cadastre sua renda</b><small>Salário, VR, rendimentos e outras rendas viram saldos separados que entram todo dia</small></span><svg class="chev" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg></button>`;
      return;
    }
    const list = pockets().filter(p => !p.ended);
    const dups = findDupIncome().length;
    el.innerHTML = `<div class="sec-head"><h3>Saldo do dia</h3><button class="link" data-go="renda">Sua renda</button></div>
      <div class="pocket-strip">${list.map(p => pocketCard(p)).join('') || '<p class="empty-note">Nenhuma renda contando agora.</p>'}</div>
      ${dups ? `<button class="dup-mini" data-go="renda">Atenção: pode estar contando o salário duas vezes. Toque para resolver.</button>` : ''}`;
    bindPockets(el);
  }
  function renderIncome() {
    const plist = pockets();
    $('#inc-hero').innerHTML = plist.length ? plist.map(p => pocketCard(p, true)).join('')
      : `<p class="empty-note">Cadastre uma renda abaixo. Cada uma vira um saldo separado que entra todo dia (ou no dia do pagamento) e passa o que sobrou para o dia seguinte.</p>`;
    bindPockets($('#inc-hero'));
    drawPocketSparks(plist);
    const dups = findDupIncome();
    const dw = $('#dup-warn');
    dw.classList.toggle('hidden', !dups.length);
    if (dups.length) {
      const ex = dups[0];
      dw.innerHTML = `<b>Pode estar contando duas vezes</b><p>A renda cadastrada aqui também aparece nos extratos importados: ${dups.length} entrada${dups.length > 1 ? 's' : ''} parecida${dups.length > 1 ? 's' : ''} (ex.: ${esc(ex.desc)}, ${fmt(ex.amount)} em ${shortDate(ex.date)}). Escolha qual manter:</p>
        <div class="nag-actions"><button class="nag-btn" id="dup-off-imported">Desligar as dos extratos</button><button class="nag-btn soft" id="dup-keep">Manter as duas</button></div>`;
      $('#dup-off-imported').addEventListener('click', async () => {
        if (!(await ask({ title: 'Desligar as entradas dos extratos?', text: `${dups.length} entrada${dups.length > 1 ? 's continuam salvas' : ' continua salva'}, mas sai${dups.length > 1 ? 'em' : ''} das contas. Dá para religar no Extrato.`, ok: 'Desligar', icon: 'power' }))) return;
        dups.forEach(t => { t.off = true; }); save(); toast('Entradas dos extratos desligadas'); renderIncome();
      });
      $('#dup-keep').addEventListener('click', () => { dups.forEach(t => { t.dupOk = true; }); save(); renderIncome(); });
    }
    $('#inc-groups').innerHTML = Object.entries(INC_TYPES).map(([k, ty]) => {
      const items = incomes().filter(s => s.type === k);
      return `<div class="inc-group">
        <div class="sec-head"><h3>${ty.name}</h3><button class="link" data-add="${k}">+ Adicionar</button></div>
        ${items.length ? items.map(s => `<button class="inc-item ${s.mode === 'off' ? 'is-off' : ''}" data-id="${s.id}">
            ${incTile(k)}<span class="grow"><b>${esc(s.name)}</b><small>${fmt(s.monthly)}/mês${investPart(s) ? ` · ${fmt(investPart(s))} para investir` : ''} · ${modeText(s)}${s.bank ? ' · ' + esc(acctLabel(bankById(s.bank))) : ''}${s.end && s.end < todayStr() ? ' · encerrada' : ''}</small></span>
            <span class="mode-pill ${s.mode}">${{ daily: 'diário', payday: `dia ${s.payday}`, off: 'não conta' }[s.mode]}</span></button>`).join('')
          : `<button class="inc-empty" data-add="${k}">${ty.hint}</button>`}
      </div>`;
    }).join('');
    $$('#inc-groups [data-add]').forEach(b => b.addEventListener('click', () => openInc(null, b.dataset.add)));
    $$('#inc-groups .inc-item').forEach(b => b.addEventListener('click', () => openInc(incomes().find(s => s.id === b.dataset.id))));
    renderConsol($('#consol-renda'));
  }

  const inc = { editing: null, type: 'salario', mode: 'daily', split: { on: false, mode: 'pct', value: '' } };
  function openInc(src, type) {
    if (!src && !type) return;
    inc.editing = src || null;
    inc.type = src?.type || type;
    inc.mode = src?.mode || (inc.type === 'invest' ? 'daily' : 'daily');
    $('#inc-title').textContent = src ? 'Editar renda' : 'Nova fonte de renda';
    $('#inc-name').value = src?.name || INC_TYPES[inc.type].def;
    const money = v => v ? (+v).toFixed(2).replace('.', ',') : '';
    $('#inc-amount').value = money(src?.monthly);
    $('#inc-principal').value = money(src?.principal);
    $('#inc-rate').value = src?.rate ? String(src.rate).replace('.', ',') : '';
    $('#inc-payday').value = src?.payday || 5;
    const first = todayStr().slice(0, 8) + '01';
    $('#inc-start').value = src?.start || first;
    $('#inc-end').value = src?.end || '';
    const pref = inc.type === 'vr' ? db.banks.find(b => b.type === 'vr' || b.type === 'va') : db.banks.find(b => b.type === 'conta');
    $('#inc-bank').innerHTML = `<option value="">Nenhuma (conta só no total)</option>` + db.banks.map(b => `<option value="${b.id}">${esc(acctLabel(b))}</option>`).join('');
    $('#inc-bank').value = src ? (src.bank || '') : (pref?.id || '');
    $('#inc-delete').classList.toggle('hidden', !src);
    inc.split = { on: !!src?.invest?.on, mode: src?.invest?.mode || 'pct', value: src?.invest?.value ?? '' };
    $('#split-value').value = inc.split.value === '' ? '' : String(inc.split.value).replace('.', ',');
    syncInc();
    $('#inc-sheet').classList.remove('hidden'); $('#inc-backdrop').classList.remove('hidden');
  }
  const closeInc = () => { $('#inc-sheet').classList.add('hidden'); $('#inc-backdrop').classList.add('hidden'); };
  function incMonthly() {
    if (inc.type === 'invest') {
      const p = Parsers.parseAmount($('#inc-principal').value), r = Parsers.parseAmount($('#inc-rate').value);
      if (p > 0 && r > 0 && document.activeElement !== $('#inc-amount')) $('#inc-amount').value = r2(p * r / 100).toFixed(2).replace('.', ',');
    }
    const v = Parsers.parseAmount($('#inc-amount').value);
    return isFinite(v) && v > 0 ? v : 0;
  }
  function syncInc() {
    $('#inc-types').innerHTML = Object.entries(INC_TYPES).map(([k, t]) => `<button type="button" class="pick ${inc.type === k ? 'on' : ''}" data-t="${k}">${incTile(k)}${t.name.replace(' / alimentação', '/VA')}</button>`).join('');
    $('#inc-invest').classList.toggle('hidden', inc.type !== 'invest');
    $('#inc-amount-lbl').textContent = inc.type === 'invest' ? 'Renda por mês (calculada ou digite)' : 'Quanto você recebe por mês';
    $$('#inc-modes button').forEach(b => b.classList.toggle('on', b.dataset.mode === inc.mode));
    // parte para investir (salário e outras fontes)
    const canSplit = inc.type === 'salario' || inc.type === 'extra';
    $('#inc-split').classList.toggle('hidden', !canSplit);
    $('#split-toggle').classList.toggle('on', canSplit && inc.split.on);
    $('#split-body').classList.toggle('hidden', !(canSplit && inc.split.on));
    $$('#split-mode button').forEach(b => b.classList.toggle('active', b.dataset.m === inc.split.mode));
    $('#split-value').placeholder = inc.split.mode === 'pct' ? 'ex.: 60 (%)' : 'ex.: 3.000,00';
    const gross = incMonthly();
    const sv = Parsers.parseAmount($('#split-value').value);
    const part = canSplit && inc.split.on ? investPart({ monthly: gross, invest: { on: true, mode: inc.split.mode, value: isFinite(sv) ? sv : 0 } }) : 0;
    const m = r2(gross - part), today = todayStr(), dim = dimOf(today);
    $('#split-preview').innerHTML = canSplit && inc.split.on && gross ? `<b>${fmt(part)}</b> vai para investimentos (${pct(gross ? part / gross : 0)}) · <b>${fmt(m)}</b> fica livre para o dia a dia` : '';
    $('#inc-preview').textContent = !m ? '' : inc.mode === 'daily'
      ? `Em ${MES_LONGO[new Date().getMonth()]} (${dim} dias): ${fmt(m / dim)} por dia. Num mês de ${dim === 31 ? 30 : 31} dias: ${fmt(m / (dim === 31 ? 30 : 31))} por dia.`
      : inc.mode === 'payday' ? `Entra ${fmt(m)} todo dia ${$('#inc-payday').value || 1} (ou no último dia, em meses mais curtos).`
      : 'Não entra nas contas. Fica só registrado aqui.';
  }
  $('#inc-types').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    const prevDef = INC_TYPES[inc.type].def;
    inc.type = b.dataset.t;
    if (!$('#inc-name').value.trim() || $('#inc-name').value === prevDef) $('#inc-name').value = INC_TYPES[inc.type].def;
    syncInc();
  });
  $('#inc-modes').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; inc.mode = b.dataset.mode; syncInc(); });
  $('#inc-payday').addEventListener('click', e => { e.stopPropagation(); inc.mode = 'payday'; syncInc(); });
  ['#inc-amount', '#inc-principal', '#inc-rate', '#inc-payday', '#split-value'].forEach(k => $(k).addEventListener('input', syncInc));
  $('#split-toggle').addEventListener('click', () => { inc.split.on = !inc.split.on; syncInc(); if (inc.split.on) setTimeout(() => $('#split-value').focus(), 50); });
  $('#split-mode').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b || b.dataset.m === inc.split.mode) return;
    // converte o valor digitado para a outra forma
    const gross = incMonthly(), v = Parsers.parseAmount($('#split-value').value);
    if (gross > 0 && isFinite(v) && v > 0) $('#split-value').value = (b.dataset.m === 'amount' ? r2(gross * v / 100) : r2(v / gross * 100)).toFixed(2).replace('.', ',').replace(/,00$/, '');
    inc.split.mode = b.dataset.m; syncInc();
  });
  $('#inc-close').addEventListener('click', closeInc);
  $('#inc-backdrop').addEventListener('click', closeInc);
  $('#inc-delete').addEventListener('click', async () => {
    if (!inc.editing || !(await ask({ title: `Excluir “${inc.editing.name}”?`, text: 'As entradas automáticas dessa renda saem das contas.', ok: 'Excluir', danger: true }))) return;
    db.incomes = incomes().filter(s => s.id !== inc.editing.id); save(); closeInc(); render(); toast('Renda excluída');
  });
  $('#inc-sheet').addEventListener('submit', e => {
    e.preventDefault();
    const monthly = incMonthly();
    if (!monthly && inc.mode !== 'off') { $('#inc-amount').focus(); toast('Digite quanto você recebe por mês'); return; }
    const payday = Math.min(31, Math.max(1, parseInt($('#inc-payday').value, 10) || 1));
    const start = $('#inc-start').value || todayStr().slice(0, 8) + '01';
    const end = $('#inc-end').value || null;
    if (end && end < start) { toast('A data final é antes do começo'); return; }
    const data = {
      type: inc.type, name: $('#inc-name').value.trim() || INC_TYPES[inc.type].def, monthly: r2(monthly), mode: inc.mode, payday,
      bank: $('#inc-bank').value || null, start, end,
      principal: inc.type === 'invest' ? (Parsers.parseAmount($('#inc-principal').value) || null) : null,
      rate: inc.type === 'invest' ? (Parsers.parseAmount($('#inc-rate').value) || null) : null,
      invest: (inc.type === 'salario' || inc.type === 'extra') && inc.split.on ? { on: true, mode: inc.split.mode, value: Parsers.parseAmount($('#split-value').value) || 0 } : null,
    };
    if (data.invest && !(investPart({ monthly: data.monthly, invest: data.invest }) > 0)) { $('#split-value').focus(); toast('Digite quanto vai para investimentos'); return; }
    db.incomes ||= [];
    if (inc.editing) Object.assign(db.incomes.find(s => s.id === inc.editing.id), data);
    else db.incomes.push({ id: uid(), ...data });
    save(); closeInc(); render();
    const freeM = freeMonthly(data);
    toast(inc.mode === 'off' ? 'Renda registrada (não conta nas entradas)' : `Pronto: ${fmt(freeM)}/mês entrando ${inc.mode === 'daily' ? 'todo dia' : 'no dia ' + payday}${data.invest ? ` e ${fmt(investPart(data))} para investir` : ''}`);
  });

  /* =================== investimentos =================== */
  const INDEXES = {
    cdi: { name: 'CDI', lbl: '% do CDI', unit: '% do CDI', def: 100 },
    selic: { name: 'Selic', lbl: '% da Selic', unit: '% da Selic', def: 100 },
    ipca: { name: 'IPCA +', lbl: 'IPCA + (% ao ano)', unit: '% + IPCA', def: 6 },
    pre: { name: 'Prefixado', lbl: 'Taxa (% ao ano)', unit: '% a.a.', def: 12 },
  };
  const invCfg = () => (db.invest ||= { holdings: [], rates: { cdi: 14.9, selic: 15, ipca: 4.5 }, count: { on: false, pct: 100 }, addToWealth: false });
  function holdingAnnual(h) {
    const r = invCfg().rates, x = (+h.rate || 0) / 100;
    if (h.index === 'cdi') return (+r.cdi / 100) * x;
    if (h.index === 'selic') return (+r.selic / 100) * x;
    if (h.index === 'ipca') return (1 + +r.ipca / 100) * (1 + x) - 1;
    return x;
  }
  // renda variável: média dos proventos lançados nos últimos 12 meses (do 1º mês lançado até agora)
  function provAvg(h) {
    const hist = (h.history || []).filter(x => x.v > 0);
    if (!hist.length) return +h.avg || 0;
    const now = todayStr().slice(0, 7);
    const y = +now.slice(0, 4), m = +now.slice(5, 7);
    const from = `${m === 12 ? y : y - 1}-${pad(m === 12 ? 1 : m + 1)}`;
    const recent = hist.filter(x => x.m >= from && x.m <= now);
    if (!recent.length) return +h.avg || 0;
    const first = recent.map(x => x.m).sort()[0];
    const span = (y * 12 + m) - (+first.slice(0, 4) * 12 + +first.slice(5, 7)) + 1;
    return recent.reduce((a, x) => a + x.v, 0) / Math.max(1, span);
  }
  const pct1 = v => (v * 100).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }) + '%';
  const holdingMonthly = h => h.kind === 'fixa' ? (+h.amount || 0) * (Math.pow(1 + holdingAnnual(h), 1 / 12) - 1) : provAvg(h);
  function invTotals() {
    const hs = invCfg().holdings;
    const fixa = hs.filter(h => h.kind === 'fixa'), vari = hs.filter(h => h.kind === 'variavel');
    const sum = (l, f) => l.reduce((a, h) => a + f(h), 0);
    const fm = r2(sum(fixa, holdingMonthly)), vm = r2(sum(vari, holdingMonthly));
    return { amount: r2(sum(hs, h => +h.amount || 0)), fixaAmt: r2(sum(fixa, h => +h.amount || 0)), varAmt: r2(sum(vari, h => +h.amount || 0)), fixaM: fm, varM: vm, monthly: r2(fm + vm) };
  }
  const aportes = () => incomes().filter(s => investPart(s) > 0 && (!s.end || s.end >= todayStr()));

  // Consolidação: tudo o que entra por mês, separado, e o que conta no dia a dia
  function renderConsol(el) {
    if (!el) return;
    const iv = invCfg(), tt = invTotals();
    const srcs = incomes().filter(s => (!s.end || s.end >= todayStr()) && s.monthly > 0);
    if (!srcs.length && !iv.holdings.length) { el.innerHTML = ''; return; }
    const row = (ic, color, name, sub, value, tag) => `<div class="cs-row"><span class="tile sm" style="background:${alpha(color, .14)};color:${color}">${svg(ic)}</span><div class="grow"><b>${esc(name)}</b>${sub ? `<small>${sub}</small>` : ''}</div><div class="cs-v"><b>${fmt(value)}</b>${tag ? `<span class="cs-tag ${tag[0]}">${tag[1]}</span>` : ''}</div></div>`;
    let total = 0, counted = 0;
    const lines = srcs.map(s => {
      total += s.monthly;
      const live = s.mode !== 'off';
      if (live) counted += freeMonthly(s);
      const part = investPart(s);
      const ty = INC_TYPES[s.type];
      return row(ty.ic, CAT[ty.cat].color, s.name, part ? `${fmt(part)} vai para investir · ${fmt(freeMonthly(s))} livre` : '', s.monthly, live ? ['on', s.mode === 'daily' ? 'no dia a dia' : `dia ${s.payday}`] : ['off', 'não conta']);
    });
    const cnt = iv.count?.on ? r2(tt.monthly * (iv.count.pct ?? 100) / 100) : 0;
    counted += cnt; total += tt.monthly;
    if (iv.holdings.some(h => h.kind === 'fixa')) lines.push(row('trend', '#38d9a9', 'Renda fixa', `rendimento estimado de ${fmt(tt.fixaAmt)}`, tt.fixaM, null));
    if (iv.holdings.some(h => h.kind === 'variavel')) lines.push(row('wallet', '#d4fc79', 'Renda variável', `média de proventos de ${fmt(tt.varAmt)}`, tt.varM, null));
    const pctv = iv.count?.pct ?? 100;
    el.innerHTML = `<div class="card consol">
      <div class="card-head"><div><h3>Quanto você recebe por mês</h3><p>Tudo junto, mas cada coisa no seu lugar</p></div></div>
      ${lines.join('')}
      <div class="cs-total"><span>Total por mês</span><b>${fmt(total)}</b></div>
      ${iv.holdings.length ? `<div class="cs-count">
        <div class="toggle-row" style="margin-top:0">
          <div class="grow"><b>Contar os rendimentos no dia a dia</b><small>${iv.count?.on ? `${pctv}% dos rendimentos (${fmt(cnt)}/mês) viram o saldo “Rendimentos”, ${fmt(cnt / dimOf(todayStr()))} por dia` : 'Desligado: os rendimentos só aparecem aqui'}</small></div>
          <button class="switch ${iv.count?.on ? 'on' : ''}" data-cs="toggle" role="switch" aria-checked="${!!iv.count?.on}" aria-label="Contar rendimentos no dia a dia"><i></i></button>
        </div>
        <label class="cs-slider ${iv.count?.on ? '' : 'hidden'}"><span>Quanto contar <b data-cs="pctv">${pctv}%</b></span><input type="range" min="0" max="100" step="5" value="${pctv}" data-cs="pct"></label>
      </div>` : ''}
      <div class="cs-day"><span>Entra no dia a dia</span><b>${fmt(counted)}/mês · ${fmt(counted / dimOf(todayStr()))}/dia</b></div>
    </div>`;
    el.querySelector('[data-cs="toggle"]')?.addEventListener('click', () => {
      iv.count.on = !iv.count.on;
      if (iv.count.on) iv.count.start = todayStr();
      save(); render(); toast(iv.count.on ? 'Rendimentos contando no dia a dia, num saldo separado' : 'Rendimentos fora do dia a dia');
    });
    const sl = el.querySelector('[data-cs="pct"]');
    sl?.addEventListener('input', () => { el.querySelector('[data-cs="pctv"]').textContent = sl.value + '%'; });
    sl?.addEventListener('change', () => { iv.count.pct = +sl.value; save(); render(); });
  }

  function renderInvest() {
    const iv = invCfg(), tt = invTotals();
    const fixaShare = tt.amount ? tt.fixaAmt / tt.amount : 0;
    const ap = aportes(), apM = ap.reduce((a, s) => a + investPart(s), 0);
    $('#inv-hero').innerHTML = `
      <span class="hero-label">Total investido</span>
      <div class="inc-big">${fmt(tt.amount)}</div>
      <div class="inv-split"><i style="width:${fixaShare * 100}%"></i></div>
      <div class="inv-split-lbl"><span>Renda fixa ${fmtShort(tt.fixaAmt)}</span><span>Renda variável ${fmtShort(tt.varAmt)}</span></div>
      <div class="hero-row">
        <div><small>Rende por mês</small><b>${fmt(tt.monthly)}</b></div>
        <div><small>Por ano</small><b>${fmtShort(tt.monthly * 12)}</b></div>
        <div><small>Aporte por mês</small><b>${apM ? fmtShort(apM) : '—'}</b></div>
      </div>`;
    renderConsol($('#consol-inv'));
    const item = h => {
      const m = holdingMonthly(h);
      const sub = h.kind === 'fixa' ? `${String(h.rate ?? '').replace('.', ',')}${INDEXES[h.index]?.unit || ''} · ${pct1(holdingAnnual(h))} ao ano` : `${(h.history || []).length ? `média de ${(h.history || []).length} lançamento(s)` : 'média informada'}`;
      return `<button class="inc-item" data-inv="${h.id}"><span class="tile" style="background:${alpha(h.kind === 'fixa' ? '#38d9a9' : '#d4fc79', .14)};color:${h.kind === 'fixa' ? '#38d9a9' : '#d4fc79'}">${svg(h.kind === 'fixa' ? 'trend' : 'wallet')}</span>
        <span class="grow"><b>${esc(h.name)}</b><small>${fmt(+h.amount || 0)} · ${sub}</small></span><span class="inv-m"><b>${fmt(m)}</b><small>por mês</small></span></button>`;
    };
    const hs = iv.holdings;
    $('#inv-fixa').innerHTML = hs.filter(h => h.kind === 'fixa').map(item).join('') || `<button class="inc-empty" data-addinv="fixa">CDB, Tesouro, LCI/LCA, poupança… O rendimento é calculado pela taxa (CDI, Selic, IPCA+ ou prefixado).</button>`;
    $('#inv-variavel').innerHTML = hs.filter(h => h.kind === 'variavel').map(item).join('') || `<button class="inc-empty" data-addinv="variavel">Ações, FIIs, ETFs… Lance os proventos de cada mês e o app mostra a média e a evolução.</button>`;
    $$('#view-invest [data-addinv]').forEach(b => { b.onclick = () => openInv(null, b.dataset.addinv); });
    $$('#view-invest [data-inv]').forEach(b => b.addEventListener('click', () => openInv(hs.find(h => h.id === b.dataset.inv))));
    $('#rate-cdi').value = String(iv.rates.cdi).replace('.', ',');
    $('#rate-selic').value = String(iv.rates.selic).replace('.', ',');
    $('#rate-ipca').value = String(iv.rates.ipca).replace('.', ',');
    $('#inv-wealth').classList.toggle('on', !!iv.addToWealth);
    // gráfico: renda fixa estimada + proventos lançados, últimos 12 meses
    const months = []; const now = new Date();
    for (let i = 11; i >= 0; i--) { const d = new Date(now.getFullYear(), now.getMonth() - i, 1); months.push(`${d.getFullYear()}-${pad(d.getMonth() + 1)}`); }
    const prov = {}; hs.filter(h => h.kind === 'variavel').forEach(h => (h.history || []).forEach(x => { prov[x.m] = (prov[x.m] || 0) + x.v; }));
    const A1 = themeAccent();
    setChart('inv', $('#chart-inv'), {
      type: 'bar',
      data: { labels: months.map(monthLabel), datasets: [
        { label: 'Renda fixa (estimado)', data: months.map(() => tt.fixaM), backgroundColor: 'rgba(56,217,169,0.75)', borderRadius: 4, borderSkipped: false, maxBarThickness: 22 },
        { label: 'Proventos', data: months.map(k => r2(prov[k] || 0)), backgroundColor: A1, borderRadius: 4, borderSkipped: false, maxBarThickness: 22 },
      ] },
      options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false },
        plugins: { legend: { display: false }, tooltip: { ...tooltip, callbacks: { title: it => { const k = months[it[0].dataIndex]; return `${MES_LONGO[+k.slice(5) - 1]} de ${k.slice(0, 4)}`; }, label: c => `${c.dataset.label}: ${fmt(c.parsed.y)}`, footer: it => `Total: ${fmt(it.reduce((a, c) => a + c.parsed.y, 0))}` } } },
        scales: { x: { ...axes().x, stacked: true }, y: { ...axes().y, stacked: true } } },
    });
  }
  ['cdi', 'selic', 'ipca'].forEach(k => $(`#rate-${k}`).addEventListener('change', e => {
    const v = Parsers.parseAmount(e.target.value);
    if (!isFinite(v) || v < 0) { toast('Taxa inválida'); renderInvest(); return; }
    invCfg().rates[k] = v; save(); renderInvest(); toast('Taxa atualizada');
  }));
  $('#inv-wealth').addEventListener('click', () => { const iv = invCfg(); iv.addToWealth = !iv.addToWealth; save(); renderInvest(); toast(iv.addToWealth ? 'Investimentos somados ao patrimônio' : 'Patrimônio sem os investimentos'); });

  const invEd = { editing: null, kind: 'fixa', index: 'cdi', history: [] };
  function openInv(h, kind) {
    invEd.editing = h || null;
    invEd.kind = h?.kind || kind || 'fixa';
    invEd.index = h?.index || 'cdi';
    invEd.history = (h?.history || []).map(x => ({ ...x }));
    $('#inv-title').textContent = h ? 'Editar aplicação' : 'Nova aplicação';
    $('#inv-name').value = h?.name || '';
    const money = v => v ? (+v).toFixed(2).replace('.', ',') : '';
    $('#inv-amount').value = money(h?.amount);
    $('#inv-rate').value = h?.rate != null ? String(h.rate).replace('.', ',') : String(INDEXES[invEd.index].def);
    $('#inv-avg').value = money(h?.avg);
    const d = new Date(); $('#prov-month').value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; $('#prov-value').value = '';
    $('#inv-delete').classList.toggle('hidden', !h);
    syncInv();
    $('#inv-sheet').classList.remove('hidden'); $('#inv-backdrop').classList.remove('hidden');
  }
  const closeInv = () => { $('#inv-sheet').classList.add('hidden'); $('#inv-backdrop').classList.add('hidden'); };
  function draftHolding() {
    return { kind: invEd.kind, index: invEd.index, amount: Parsers.parseAmount($('#inv-amount').value) || 0, rate: Parsers.parseAmount($('#inv-rate').value) || 0, avg: Parsers.parseAmount($('#inv-avg').value) || 0, history: invEd.history };
  }
  function syncInv() {
    $$('#inv-kind button').forEach(b => b.classList.toggle('active', b.dataset.k === invEd.kind));
    $('#inv-fixa-box').classList.toggle('hidden', invEd.kind !== 'fixa');
    $('#inv-var-box').classList.toggle('hidden', invEd.kind !== 'variavel');
    $('#inv-index').innerHTML = Object.entries(INDEXES).map(([k, x]) => `<button type="button" class="pick ${invEd.index === k ? 'on' : ''}" data-ix="${k}">${x.name}</button>`).join('');
    $('#inv-rate-lbl').textContent = INDEXES[invEd.index].lbl;
    const hist = [...invEd.history].sort((a, b) => b.m.localeCompare(a.m));
    $('#prov-list').innerHTML = hist.map(x => `<div class="prov-row"><span>${MES_LONGO[+x.m.slice(5) - 1].replace(/^./, c => c.toUpperCase())} de ${x.m.slice(0, 4)}</span><b>${fmt(x.v)}</b><button type="button" data-rmprov="${x.m}" aria-label="Remover">×</button></div>`).join('') || `<p class="empty-note" style="padding:4px 2px">Nenhum provento lançado ainda.</p>`;
    $$('#prov-list [data-rmprov]').forEach(b => b.addEventListener('click', () => { invEd.history = invEd.history.filter(x => x.m !== b.dataset.rmprov); syncInv(); }));
    const h = draftHolding(), m = holdingMonthly(h);
    $('#inv-preview').textContent = invEd.kind === 'fixa'
      ? (h.amount ? `Rende cerca de ${fmt(m)} por mês (${pct1(holdingAnnual(h))} ao ano, bruto, antes do IR).` : '')
      : (m ? `Média de ${fmt(m)} por mês${invEd.history.length ? ` (${invEd.history.length} mês(es) lançado(s))` : ' (informada)'}${h.amount ? ` · ${pct1(m * 12 / h.amount)} ao ano sobre o valor investido` : ''}.` : 'Lance os proventos de cada mês ou informe a média.');
  }
  $('#inv-kind').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; invEd.kind = b.dataset.k; syncInv(); });
  $('#inv-index').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    const prevDef = INDEXES[invEd.index].def;
    invEd.index = b.dataset.ix;
    if (!$('#inv-rate').value || Parsers.parseAmount($('#inv-rate').value) === prevDef) $('#inv-rate').value = String(INDEXES[invEd.index].def);
    syncInv();
  });
  ['#inv-amount', '#inv-rate', '#inv-avg'].forEach(k => $(k).addEventListener('input', syncInv));
  $('#prov-add').addEventListener('click', () => {
    const m = $('#prov-month').value, v = Parsers.parseAmount($('#prov-value').value);
    if (!m) { toast('Escolha o mês'); return; }
    if (!isFinite(v) || v <= 0) { $('#prov-value').focus(); toast('Digite o valor recebido'); return; }
    const ex = invEd.history.find(x => x.m === m);
    if (ex) ex.v = r2(ex.v + v); else invEd.history.push({ m, v: r2(v) });
    $('#prov-value').value = ''; syncInv();
  });
  $('#inv-close').addEventListener('click', closeInv);
  $('#inv-backdrop').addEventListener('click', closeInv);
  $('#inv-delete').addEventListener('click', async () => {
    if (!invEd.editing || !(await ask({ title: `Excluir “${invEd.editing.name}”?`, text: 'Os proventos lançados nela também saem.', ok: 'Excluir', danger: true }))) return;
    invCfg().holdings = invCfg().holdings.filter(h => h.id !== invEd.editing.id); save(); closeInv(); render(); toast('Aplicação excluída');
  });
  $('#inv-sheet').addEventListener('submit', e => {
    e.preventDefault();
    const h = draftHolding();
    const name = $('#inv-name').value.trim() || (h.kind === 'fixa' ? `Renda fixa (${INDEXES[h.index].name})` : 'Renda variável');
    if (!(h.amount > 0) && !(h.kind === 'variavel' && (h.history.length || h.avg))) { $('#inv-amount').focus(); toast('Digite quanto tem investido'); return; }
    const data = { name, kind: h.kind, amount: r2(h.amount), index: h.kind === 'fixa' ? h.index : null, rate: h.kind === 'fixa' ? h.rate : null, avg: h.kind === 'variavel' ? h.avg : null, history: h.kind === 'variavel' ? h.history : [] };
    const iv = invCfg();
    if (invEd.editing) Object.assign(iv.holdings.find(x => x.id === invEd.editing.id), data); else iv.holdings.push({ id: uid(), ...data });
    save(); closeInv(); render(); toast(`${name}: ${fmt(holdingMonthly(data))} por mês`);
  });

  /* =================== painéis =================== */
  function openSpaces() {
    const list = $('#space-list');
    list.innerHTML = spaces.list.map(s => {
      const d = s.id === spaces.active ? db : load(s.id);
      const on = s.id === spaces.active;
      return `<div class="space-item ${on ? 'on' : ''}" data-id="${s.id}">
        <button class="space-main" data-act="open"><span class="space-av" style="background:${s.color}">${esc(s.name.trim()[0]?.toUpperCase() || '?')}</span>
          <span class="grow"><b>${esc(s.name)}</b><small>${d.txs.length} lançamento${d.txs.length === 1 ? '' : 's'} · ${d.banks.length} conta${d.banks.length === 1 ? '' : 's'}${on ? ' · aberto agora' : ''}</small></span>
          ${on ? `<span class="space-check">${svg('ok')}</span>` : `<svg class="chev" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></svg>`}</button>
        <div class="space-tools"><button data-act="rename">Renomear</button>${on ? '' : '<button data-act="delete" class="danger">Excluir</button>'}</div>
      </div>`;
    }).join('');
    list.querySelectorAll('.space-item').forEach(el => {
      const id = el.dataset.id, s = spaces.list.find(x => x.id === id);
      el.querySelectorAll('[data-act]').forEach(b => b.addEventListener('click', async () => {
        if (b.dataset.act === 'open') { if (id !== spaces.active) switchSpace(id); else closeSpaces(); }
        if (b.dataset.act === 'rename') {
          const n = await askText({ title: 'Renomear painel', ok: 'Salvar', input: { value: s.name, placeholder: 'Nome do painel' } }); if (!n) return;
          s.name = n.trim().slice(0, 40); saveSpaces(); renderTop(); openSpaces();
        }
        if (b.dataset.act === 'delete') {
          const d = load(id);
          if (!(await ask({ title: `Excluir o painel “${s.name}”?`, text: `${d.txs.length ? `Ele tem ${d.txs.length} lançamento${d.txs.length > 1 ? 's' : ''}. ` : ''}Não dá para desfazer.`, ok: 'Excluir', danger: true }))) return;
          try { localStorage.removeItem(spaceKey(id)); } catch (e) { /* */ }
          spaces.list = spaces.list.filter(x => x.id !== id); saveSpaces(); openSpaces();
          toast('Painel excluído');
        }
      }));
    });
    $('#space-new-name').value = '';
    $('#spaces-sheet').classList.remove('hidden'); $('#spaces-backdrop').classList.remove('hidden');
  }
  const closeSpaces = () => { $('#spaces-sheet').classList.add('hidden'); $('#spaces-backdrop').classList.add('hidden'); };
  function switchSpace(id) {
    save();
    spaces.active = id; saveSpaces();
    db = load();
    ui.bank = ''; ui.monthPick = null; ui.list = { q: '', cat: '', type: '', bank: '', merchant: null, limit: 120 };
    slots = null; review = [];
    applyTheme();
    closeSpaces(); go('panorama');
    toast(`Painel “${currentSpace().name}”`);
  }
  $('#space-btn').addEventListener('click', openSpaces);
  $('#manage-spaces').addEventListener('click', openSpaces);
  $('#spaces-close').addEventListener('click', closeSpaces);
  $('#spaces-backdrop').addEventListener('click', closeSpaces);
  $('#space-new').addEventListener('submit', e => {
    e.preventDefault();
    const name = $('#space-new-name').value.trim();
    if (!name) { $('#space-new-name').focus(); toast('Dê um nome ao painel'); return; }
    const id = uid();
    spaces.list.push({ id, name: name.slice(0, 40), color: SPACE_COLORS[spaces.list.length % SPACE_COLORS.length] });
    try { localStorage.setItem(spaceKey(id), JSON.stringify(emptyDb())); } catch (err) { /* */ }
    saveSpaces(); switchSpace(id);
  });

  /* =================== backup =================== */
  const BK_KEY = STORE_KEY + ':backup';
  let bk = {};
  try { bk = JSON.parse(localStorage.getItem(BK_KEY)) || {}; } catch (e) { /* */ }
  const saveBk = () => { try { localStorage.setItem(BK_KEY, JSON.stringify(bk)); } catch (e) { /* */ } };
  const daysSince = iso => iso ? Math.floor((Date.now() - new Date(iso)) / 86400000) : Infinity;
  const fmtWhen = iso => { const d = new Date(iso); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()} às ${pad(d.getHours())}:${pad(d.getMinutes())}`; };
  const canAutoFolder = () => !!(window.Android?.pickBackupFolder && window.Android?.writeBackup);
  const totalTxs = () => spaces.list.reduce((n, s) => n + (s.id === spaces.active ? db : load(s.id)).txs.length, 0);
  function backupPayload() {
    save();
    return {
      app: 'financas-martins', version: 3, exportedAt: new Date().toISOString(), active: spaces.active,
      spaces: spaces.list.map(s => ({ id: s.id, name: s.name, color: s.color, data: s.id === spaces.active ? db : load(s.id), theme: readTheme(s.id), wall: wallCache[s.id] || null })),
    };
  }
  const backupName = () => `financas-backup-${todayStr()}.json`;
  let markOnSave = false;
  function backupNow() {
    markOnSave = !!window.Android;
    const p = download(backupName(), JSON.stringify(backupPayload()), 'application/json');
    if (!window.Android) {
      p.then(ok => { if (ok === false) { toast('Backup cancelado'); return; } bk.last = new Date().toISOString(); saveBk(); toast('Backup salvo'); render(); });
    }
  }
  // grava sozinho na pasta escolhida (só no app Android), uma vez a cada 7 dias
  function runAutoBackup(force) {
    if (!bk.auto || !bk.folder || !canAutoFolder() || !totalTxs()) return;
    if (!force && daysSince(bk.lastAuto) < 7) return;
    let ok = false;
    try { ok = window.Android.writeBackup(backupName(), JSON.stringify(backupPayload())); } catch (e) { ok = false; }
    if (ok) { bk.lastAuto = bk.last = new Date().toISOString(); saveBk(); toast(`Backup semanal salvo em “${bk.folder}”`); }
    else { bk.folder = null; saveBk(); toast('Não consegui gravar o backup na pasta. Escolha a pasta de novo em Mais.'); }
  }
  function enableAuto() {
    if (canAutoFolder()) { window.Android.pickBackupFolder(); return; } // resposta chega em appFolderPicked
    bk.auto = true; saveBk(); toast('Pronto: vou te lembrar de salvar o backup a cada 7 dias'); render();
  }
  window.appFolderPicked = name => {
    if (!name) { toast('Nenhuma pasta escolhida'); render(); return; }
    bk.auto = true; bk.folder = name; saveBk();
    runAutoBackup(true); render();
  };
  function renderNag() {
    const has = db.txs.length > 0;
    const autoOk = bk.auto && bk.folder && canAutoFolder();
    const due = daysSince(bk.last) >= 7 && daysSince(bk.snooze) >= 3;
    const show = has && !autoOk && due;
    $('#backup-nag').classList.toggle('hidden', !show);
    if (!show) return;
    $('#nag-title').textContent = bk.last ? `Seu último backup foi há ${daysSince(bk.last)} dias` : 'Você ainda não fez nenhum backup';
    $('#nag-text').textContent = 'Salve uma cópia de tudo para não perder nada se trocar ou perder o celular.';
    $('#nag-auto').classList.toggle('hidden', !!bk.auto && !canAutoFolder());
  }
  function renderBackup() {
    const autoOk = bk.auto && (bk.folder || !canAutoFolder());
    $('#bk-status').innerHTML = bk.last
      ? `<span class="bk-dot ${daysSince(bk.last) < 7 ? 'ok' : 'late'}"></span>Último backup: <b>${fmtWhen(bk.last)}</b>${autoOk && bk.folder ? `<br><small>Próximo automático: em ${Math.max(0, 7 - daysSince(bk.lastAuto))} dia(s)</small>` : ''}`
      : `<span class="bk-dot late"></span>Nenhum backup salvo ainda`;
    const t = $('#auto-toggle');
    t.classList.toggle('on', !!autoOk); t.setAttribute('aria-checked', String(!!autoOk));
    $('#auto-desc').textContent = canAutoFolder()
      ? (autoOk ? `Salvando sozinho a cada 7 dias na pasta “${bk.folder}”.` : 'Escolha uma pasta uma vez e o app grava um backup novo nela a cada 7 dias.')
      : (autoOk ? 'Ligado: vou te lembrar a cada 7 dias de salvar o backup.' : 'Te lembro a cada 7 dias de salvar o backup.');
    $('#auto-folder').classList.toggle('hidden', !(canAutoFolder() && autoOk));
  }
  $('#backup-now').addEventListener('click', backupNow);
  $('#nag-save').addEventListener('click', backupNow);
  $('#nag-auto').addEventListener('click', enableAuto);
  $('#nag-x').addEventListener('click', () => { bk.snooze = new Date().toISOString(); saveBk(); renderNag(); });
  $('#auto-toggle').addEventListener('click', () => {
    if (bk.auto && (bk.folder || !canAutoFolder())) { bk.auto = false; saveBk(); toast('Backup automático desligado'); render(); }
    else enableAuto();
  });
  $('#auto-folder').addEventListener('click', () => window.Android?.pickBackupFolder());
  // Lê qualquer arquivo: o que importa é o conteúdo (WhatsApp, Drive e e-mail às vezes mudam o nome ou a extensão)
  function parseBackup(text) {
    try {
      const d = JSON.parse(String(text).replace(/^\uFEFF/, '').trim());
      if (Array.isArray(d?.spaces) && d.spaces.length) return d;
      if (Array.isArray(d?.txs) && Array.isArray(d?.banks)) return d;
    } catch (e) { /* não é backup */ }
    return null;
  }
  async function looksLikeBackupFile(f) {
    if (f.size > 40 * 1024 * 1024) return null;
    const head = await f.slice(0, 64).text().catch(() => '');
    if (!/^\uFEFF?\s*\{/.test(head)) return null;
    return parseBackup(await f.text());
  }
  window.appOpenFile = async (name, text) => {
    const d = parseBackup(text);
    if (d) return restoreBackup(d);
    toast(`“${name}” não é um backup do app. Para extratos, use Importar.`);
    go('importar');
  };
  $$('.restore-input').forEach(inp => inp.addEventListener('change', async e => {
    const f = e.target.files[0]; if (!f) return;
    e.target.value = '';
    const d = parseBackup(await f.text().catch(() => ''));
    if (!d) { toast('Esse arquivo não é um backup do app'); return; }
    restoreBackup(d);
  }));
  async function restoreBackup(d) {
    if (Array.isArray(d.spaces) && d.spaces.length) {
      const n = d.spaces.reduce((a, s) => a + (s.data?.txs?.length || 0), 0);
      const when = d.exportedAt ? ` de ${fmtWhen(d.exportedAt)}` : '';
      if (!(await ask({ title: 'Restaurar este backup?', text: `Backup${when}: ${d.spaces.length} painel${d.spaces.length > 1 ? 'éis' : ''} e ${n} lançamentos. Tudo o que está neste celular agora será substituído.`, ok: 'Restaurar', danger: true, icon: 'file' }))) return;
      for (const s of spaces.list) { try { localStorage.removeItem(spaceKey(s.id)); } catch (err) { /* */ } }
      spaces = { active: d.spaces.some(s => s.id === d.active) ? d.active : d.spaces[0].id, list: d.spaces.map(s => ({ id: s.id, name: s.name, color: s.color || SPACE_COLORS[0] })) };
      for (const s of d.spaces) {
        try { localStorage.setItem(spaceKey(s.id), JSON.stringify(migrate(s.data || emptyDb()))); } catch (err) { /* */ }
        if (s.theme) writeTheme(s.id, s.theme); else { try { localStorage.removeItem(themeKey(s.id)); } catch (err) { /* */ } }
        if (s.wall) { wallCache[s.id] = s.wall; idbWall.set(s.id, s.wall).catch(() => {}); }
      }
      saveSpaces();
    } else if (Array.isArray(d.txs) && Array.isArray(d.banks)) {
      if (!(await ask({ title: 'Restaurar este backup?', text: `Backup antigo com ${d.txs.length} lançamentos. Os dados do painel “${currentSpace().name}” serão substituídos.`, ok: 'Restaurar', danger: true, icon: 'file' }))) return;
      try { localStorage.setItem(spaceKey(spaces.active), JSON.stringify(migrate(d))); } catch (err) { /* */ }
    } else { toast('Arquivo de backup inválido'); return; }
    db = load(); ui.bank = ''; ui.monthPick = null; slots = null;
    bk.last = bk.last || new Date().toISOString(); saveBk();
    applyTheme();
    toast('Backup restaurado'); go('panorama');
  }

  /* =================== dados de exemplo =================== */
  function loadDemo() {
    let seed = 11;
    const rnd = () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
    const pick = a => a[Math.floor(rnd() * a.length)];
    const san = ensureAccount({ name: 'Santander', inst: 'santander', type: 'conta' });
    const nuc = ensureAccount({ name: 'Nubank', inst: 'nubank', type: 'credito' });
    const inter = ensureAccount({ name: 'Inter', inst: 'inter', type: 'conta' });
    const vr = ensureAccount({ name: 'VR', inst: 'vr', type: 'vr' });
    const cart = ensureAccount({ name: 'Carteira', inst: 'carteira', type: 'dinheiro' });
    san.initial = 4200; inter.initial = 1800; vr.initial = 120; cart.initial = 150;
    const add = (date, amount, desc, acc, time) => db.txs.push({ id: uid(), date, time: time || null, amount: Math.round(amount * 100) / 100, desc, cat: guessCat(desc, amount, acc.type), bank: acc.id, source: time ? 'manual' : 'import', createdAt: new Date().toISOString() });
    const end = new Date(); const start = new Date(end.getFullYear(), end.getMonth() - 6, 1);
    let cardMonth = 0;
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const s = ds(d), day = d.getDate(), wd = d.getDay();
      if (day === 5) add(s, 7400, 'SALARIO EMPRESA XYZ LTDA', san);
      if (day === 1) add(s, 880, 'RECARGA BENEFICIO VR', vr);
      if (day === 20 && rnd() < 0.6) add(s, 450 + rnd() * 900, 'PIX RECEBIDO - FREELA DESIGN', inter);
      if (day === 6) add(s, -1850, 'PAGAMENTO ALUGUEL - IMOBILIARIA CENTRO', san);
      if (day === 6) add(s, -1500, 'APLICACAO CDB LIQUIDEZ', inter);
      if (day === 10) add(s, -(160 + rnd() * 60), 'ENEL DISTRIBUICAO ENERGIA', san);
      if (day === 12) add(s, -99.9, 'VIVO FIBRA INTERNET', san);
      if (day === 1 && d.getMonth() % 2 === 0) add(s, -24.5, 'TARIFA PACOTE SERVICOS', san);
      if (day === 3 && cardMonth > 0) { add(s, -cardMonth, 'PAGAMENTO DE FATURA NUBANK', san); add(s, cardMonth, 'Pagamento recebido', nuc); cardMonth = 0; }
      const card = (amt, desc) => { add(s, amt, desc, nuc); cardMonth -= amt; };
      if (day === 8) card(-55.9, 'NETFLIX.COM');
      if (day === 9) card(-21.9, 'SPOTIFY');
      if (day === 15) card(-119.9, 'SMART FIT MENSALIDADE');
      if (day === 18) card(-12.9, 'APPLE.COM/BILL ICLOUD');
      if (wd === 6 || (wd === 3 && rnd() < 0.3)) card(-(110 + rnd() * 170), pick(['SUPERMERCADO EXTRA', 'CARREFOUR HIPER', 'ASSAI ATACADISTA', 'PAO DE ACUCAR']));
      if (wd > 0 && wd < 6 && rnd() < 0.75) add(s, -(26 + rnd() * 18), pick(['RESTAURANTE SABOR CASEIRO', 'SELF SERVICE BOM PRATO', 'LANCHONETE CENTRAL', 'PADARIA REAL']), vr);
      if (rnd() < (wd === 5 || wd === 6 ? 0.5 : 0.2)) card(-(35 + rnd() * 70), pick(['IFOOD *RESTAURANTE SABOR', 'IFOOD *PIZZARIA BELLA', 'IFOOD *SUSHI HOUSE', 'IFOOD *BURGER KING']));
      if (rnd() < 0.35) card(-(14 + rnd() * 32), pick(['UBER *TRIP', 'UBER *TRIP', '99APP *CORRIDA']));
      if (rnd() < 0.05) card(-(160 + rnd() * 70), 'POSTO IPIRANGA COMBUSTIVEL');
      if (rnd() < 0.2 && wd > 0 && wd < 6) add(s, -(6 + rnd() * 12), pick(['Café', 'Pão de queijo', 'Estacionamento']), cart, `${pad(8 + Math.floor(rnd() * 10))}:${pad(Math.floor(rnd() * 60))}`);
      if (rnd() < 0.06) card(-(60 + rnd() * 280), pick(['AMAZON MARKETPLACE', 'MERCADO LIVRE', 'SHOPEE', 'RENNER LOJA']));
      if (rnd() < 0.05) card(-(30 + rnd() * 120), pick(['DROGASIL', 'DROGA RAIA']));
      if (rnd() < 0.04) card(-(40 + rnd() * 120), pick(['CINEMARK', 'SYMPLA INGRESSO', 'BAR DO ZE']));
      if (rnd() < 0.05) add(s, -(50 + rnd() * 250), 'PIX ENVIADO - MARIA SOUZA', san);
    }
    save();
  }
  $('#load-demo').addEventListener('click', () => { loadDemo(); toast('Dados de exemplo carregados'); renderDashboard(); });

  /* =================== botão voltar (Android) =================== */
  window.appBack = () => {
    if (dlgDone) { dlgDone(null); return true; }
    if (!$('#bulk-sheet').classList.contains('hidden')) { closeBulk(); return true; }
    if (!$('#spaces-sheet').classList.contains('hidden')) { closeSpaces(); return true; }
    if (sel.on) { endSelect(); return true; }
    if (!$('#tx-sheet').classList.contains('hidden')) { closeSheet(); return true; }
    if (!$('#drill-sheet').classList.contains('hidden')) { closeDrill(); return true; }
    if (!$('#period-sheet').classList.contains('hidden')) { closePeriod(); return true; }
    if (ui.view === 'importar' && !$('#import-step2').classList.contains('hidden')) { $('#review-back').click(); return true; }
    if (ui.view === 'panorama' && (ui.monthPick || ui.bank)) { ui.monthPick = null; ui.bank = ''; renderDashboard(); return true; }
    if (!$('#inc-sheet').classList.contains('hidden')) { closeInc(); return true; }
    if (!$('#inv-sheet').classList.contains('hidden')) { closeInv(); return true; }
    if (!$('#pocket-sheet').classList.contains('hidden')) { closePocket(); return true; }
    if (ui.view !== 'panorama') { go('panorama'); return true; }
    return false;
  };
  window.appSaved = ok => {
    if (ok && markOnSave) { bk.last = new Date().toISOString(); saveBk(); render(); }
    markOnSave = false;
    toast(ok ? 'Arquivo salvo' : 'Arquivo não foi salvo');
  };
  document.addEventListener('keydown', e => { if (e.key === 'Escape') window.appBack(); });

  /* =================== aparência (tema e imagem de fundo) =================== */
  const themeKey = id => `${spaceKey(id)}:theme`;
  const readTheme = (id = spaces.active) => { try { return JSON.parse(localStorage.getItem(themeKey(id))) || { mode: 'default' }; } catch (e) { return { mode: 'default' }; } };
  const writeTheme = (id, t) => { try { localStorage.setItem(themeKey(id), JSON.stringify(t)); } catch (e) { toast('Não consegui salvar a aparência'); } };
  // imagens ficam no IndexedDB (cabem fotos grandes); a cópia em memória serve para o backup
  const idbWall = (() => {
    let dbp = null;
    const open = () => (dbp ||= new Promise((res, rej) => {
      const r = indexedDB.open('financas-media', 1);
      r.onupgradeneeded = () => r.result.createObjectStore('wall');
      r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error);
    }));
    const run = (mode, fn) => open().then(d => new Promise((res, rej) => {
      const t = d.transaction('wall', mode); const rq = fn(t.objectStore('wall'));
      t.oncomplete = () => res(rq.result); t.onerror = () => rej(t.error);
    }));
    return { get: k => run('readonly', s => s.get(k)), set: (k, v) => run('readwrite', s => s.put(v, k)), del: k => run('readwrite', s => s.delete(k)) };
  })();
  const wallCache = {};
  const PRESETS = {
    oceano: { name: 'Oceano', a1: '#7dd3fc', a2: '#38bdf8', h: ['#0e7490', '#155e75', '#1e3a8a'], base: '#06111c' },
    sol: { name: 'Pôr do sol', a1: '#fdba74', a2: '#fb7185', h: ['#c2410c', '#9f1239', '#3b0764'], base: '#140a0c' },
    lavanda: { name: 'Lavanda', a1: '#d8b4fe', a2: '#f0abfc', h: ['#7c3aed', '#5b21b6', '#1e1b4b'], base: '#0e0b1a' },
  };
  const hex2rgb = h => { const n = parseInt(h.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; };
  const rgbStr = h => hex2rgb(h).join(', ');
  function hsl2hex(h, s, l) {
    h = ((h % 360) + 360) % 360; const c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
    const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return '#' + [r, g, b].map(v => Math.round((v + m) * 255).toString(16).padStart(2, '0')).join('');
  }
  function rgb2hsl(r, g, b) {
    r /= 255; g /= 255; b /= 255; const mx = Math.max(r, g, b), mn = Math.min(r, g, b), l = (mx + mn) / 2;
    if (mx === mn) return [0, 0, l];
    const d = mx - mn, s = l > .5 ? d / (2 - mx - mn) : d / (mx + mn);
    const h = mx === r ? (g - b) / d + (g < b ? 6 : 0) : mx === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [h * 60, s, l];
  }
  // tira a paleta da imagem: as cores mais vivas viram destaque e degradês; a média vira o tom do fundo
  function paletteFrom(img) {
    const c = document.createElement('canvas'); c.width = c.height = 48;
    const x = c.getContext('2d'); x.drawImage(img, 0, 0, 48, 48);
    const px = x.getImageData(0, 0, 48, 48).data;
    const bins = new Array(24).fill(0).map(() => ({ w: 0, s: 0, n: 0 }));
    let ar = 0, ag = 0, ab = 0, n = 0;
    for (let i = 0; i < px.length; i += 4) {
      const [h, s, l] = rgb2hsl(px[i], px[i + 1], px[i + 2]);
      ar += px[i]; ag += px[i + 1]; ab += px[i + 2]; n++;
      if (s < 0.18 || l < 0.12 || l > 0.92) continue;
      const w = s * (1 - Math.abs(l - 0.5) * 1.4);
      const bin = bins[Math.floor(h / 15) % 24]; bin.w += w; bin.s += s; bin.n++; bin.h = (bin.h || 0) + h;
    }
    const ranked = bins.map((b, i) => ({ ...b, hue: b.n ? b.h / b.n : i * 15 + 7.5, sat: b.n ? b.s / b.n : 0 })).filter(b => b.w > 0).sort((a, b) => b.w - a.w);
    const [bh, bs, bl] = rgb2hsl(ar / n, ag / n, ab / n);
    const p1 = ranked[0] || { hue: bh, sat: Math.max(bs, .4) };
    const p2 = ranked.find(b => Math.min(Math.abs(b.hue - p1.hue), 360 - Math.abs(b.hue - p1.hue)) > 35) || { hue: p1.hue + 40, sat: p1.sat };
    const sat = v => Math.min(0.92, Math.max(0.55, v + 0.15));
    return {
      a1: hsl2hex(p1.hue, sat(p1.sat), 0.74),
      a2: hsl2hex(p2.hue, sat(p2.sat), 0.64),
      h: [hsl2hex(p1.hue, 0.55, 0.34), hsl2hex((p1.hue + p2.hue) / 2, 0.5, 0.26), hsl2hex(p2.hue, 0.5, 0.18)],
      base: hsl2hex(bh, Math.min(bs, 0.35), Math.min(0.08, bl * 0.25 + 0.03)),
    };
  }
  const css2 = v => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
  const themeAccent = () => css2('--lime') || '#d4fc79';
  const themeAccent2 = () => css2('--accent2') || '#5ee49a';
  const THEME_VARS = ['--lime', '--accent', '--accent2', '--accent-rgb', '--grad-cta', '--grad-hero', '--grad-card', '--grad-card2', '--base-rgb', '--dim', '--blur', '--bg', '--cta-ink'];
  function applyTheme() {
    const t = readTheme(), root = document.documentElement;
    THEME_VARS.forEach(v => root.style.removeProperty(v));
    root.classList.remove('has-wall');
    $('#wall').style.backgroundImage = '';
    const pal = t.mode === 'preset' ? PRESETS[t.preset] : t.mode === 'image' ? t.palette : null;
    if (pal) {
      const set = (k, v) => root.style.setProperty(k, v);
      set('--lime', pal.a1); set('--accent', pal.a1); set('--accent2', pal.a2); set('--accent-rgb', rgbStr(pal.a1));
      set('--grad-cta', `linear-gradient(135deg, ${pal.a1} 0%, ${pal.a2} 100%)`);
      set('--cta-ink', hsl2hex(rgb2hsl(...hex2rgb(pal.a1))[0], 0.6, 0.1));
      set('--grad-hero', `radial-gradient(120% 90% at 100% 0%, ${alpha(pal.a1, .38)} 0%, transparent 45%), radial-gradient(90% 80% at 0% 100%, ${alpha(pal.h[2], .7)} 0%, transparent 60%), linear-gradient(150deg, ${pal.h[0]} 0%, ${pal.h[1]} 50%, ${pal.h[2]} 100%)`);
      set('--grad-card', `linear-gradient(135deg, ${pal.h[2]}, ${pal.h[1]} 55%, ${pal.h[0]})`);
      set('--grad-card2', `linear-gradient(135deg, ${pal.h[1]}, ${pal.h[0]} 60%, ${alpha(pal.a2, .85)})`);
      set('--base-rgb', rgbStr(pal.base)); set('--bg', pal.base);
    }
    if (t.mode === 'image' && wallCache[spaces.active]) {
      root.classList.add('has-wall');
      $('#wall').style.backgroundImage = `url("${wallCache[spaces.active]}")`;
      root.style.setProperty('--dim', String((t.dim ?? 55) / 100));
      root.style.setProperty('--blur', `${t.blur ?? 0}px`);
    }
    const meta = document.querySelector('meta[name="theme-color"]'); if (meta) meta.content = pal ? pal.base : '#0a0b0f';
    if (window.Chart) Object.values(charts).forEach(c => { try { c.update('none'); } catch (e) { /* */ } });
  }
  async function loadWalls() {
    await Promise.all(spaces.list.map(async s => {
      if (readTheme(s.id).mode !== 'image' || wallCache[s.id]) return;
      try { const v = await idbWall.get(s.id); if (v) wallCache[s.id] = v; } catch (e) { /* */ }
    }));
    applyTheme();
    if (ui.view === 'panorama') renderDashboard();
  }
  // reduz a foto (até 1440 px) para caber bem no celular e no backup
  function readImage(file) {
    return new Promise((res, rej) => {
      const url = URL.createObjectURL(file); const img = new Image();
      img.onload = () => {
        const max = 1440, k = Math.min(1, max / Math.max(img.width, img.height));
        const c = document.createElement('canvas'); c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        URL.revokeObjectURL(url);
        res({ data: c.toDataURL('image/jpeg', 0.82), palette: paletteFrom(c) });
      };
      img.onerror = () => { URL.revokeObjectURL(url); rej(new Error('img')); };
      img.src = url;
    });
  }
  function renderLook() {
    const t = readTheme(), has = !!wallCache[spaces.active];
    $('#look-sub').textContent = `Painel “${currentSpace().name}”: escolha um tema ou uma imagem sua, e as cores do app se ajustam a ela`;
    const tile = (id, name, prev, on) => `<button class="theme-tile ${on ? 'on' : ''}" data-theme="${id}"><span class="theme-prev" style="${prev}">${id === 'image' && !has ? svg('file') : '<span class="tp-hero" style="background:var(--tp-hero)"></span><span class="tp-row"><i></i><i></i></span>'}</span>${name}</button>`;
    const heroOf = p => `linear-gradient(150deg, ${p.h[0]}, ${p.h[1]} 55%, ${p.h[2]})`;
    $('#themes').innerHTML =
      tile('default', 'Padrão', `background:#0a0b0f;--tp-hero:linear-gradient(150deg,#0d8f68,#0b6e73 50%,#17306f)`, t.mode === 'default')
      + Object.entries(PRESETS).map(([k, p]) => tile(k, p.name, `background:${p.base};--tp-hero:${heroOf(p)}`, t.mode === 'preset' && t.preset === k)).join('')
      + (has
        ? tile('image', 'Sua imagem', `background-image:linear-gradient(rgba(0,0,0,.25),rgba(0,0,0,.45)),url('${wallCache[spaces.active]}');--tp-hero:${t.palette ? `linear-gradient(150deg, ${alpha(t.palette.h[0], .9)}, ${alpha(t.palette.h[2], .9)})` : 'rgba(255,255,255,.2)'}`, t.mode === 'image')
        : `<button class="theme-tile" data-theme="image"><span class="theme-prev add">${svg('file')}</span>Sua imagem</button>`);
    $$('#themes .theme-tile').forEach(b => b.addEventListener('click', () => pickTheme(b.dataset.theme)));
    const img = t.mode === 'image' && has;
    $('#wall-ctrl').classList.toggle('hidden', !img);
    if (img) {
      $('#wall-dim').value = t.dim ?? 55; $('#dim-val').textContent = `${t.dim ?? 55}%`;
      $('#wall-blur').value = t.blur ?? 0; $('#blur-val').textContent = `${t.blur ?? 0}px`;
      const p = t.palette;
      $('#palette-row').innerHTML = p ? `Cores tiradas da imagem: ${[p.a1, p.a2, ...p.h].map(c => `<i style="background:${c}"></i>`).join('')}` : '';
    }
  }
  function pickTheme(id) {
    const t = readTheme();
    if (id === 'default') writeTheme(spaces.active, { ...t, mode: 'default' });
    else if (PRESETS[id]) writeTheme(spaces.active, { ...t, mode: 'preset', preset: id });
    else if (id === 'image') {
      if (wallCache[spaces.active] && t.palette) writeTheme(spaces.active, { ...t, mode: 'image' });
      else { $('#wall-input').click(); return; }
    }
    applyTheme(); renderLook();
  }
  $('#wall-input').addEventListener('change', async e => {
    const f = e.target.files[0]; e.target.value = ''; if (!f) return;
    if (f.type && !f.type.startsWith('image/')) { toast('Escolha uma imagem (foto)'); return; }
    toast('Ajustando as cores à imagem…');
    try {
      const { data, palette } = await readImage(f);
      wallCache[spaces.active] = data;
      await idbWall.set(spaces.active, data);
      const t = readTheme();
      writeTheme(spaces.active, { ...t, mode: 'image', palette, dim: t.dim ?? 55, blur: t.blur ?? 0 });
      applyTheme(); renderLook(); toast('Pronto! Ajuste o escurecer e o desfoque se quiser');
    } catch (err) { toast('Não consegui abrir essa imagem'); }
  });
  $('#wall-change').addEventListener('click', () => $('#wall-input').click());
  $('#wall-remove').addEventListener('click', async () => {
    if (!(await ask({ title: 'Remover a imagem de fundo?', text: 'O painel volta para o visual padrão. Você pode escolher outra imagem quando quiser.', ok: 'Remover', danger: true }))) return;
    delete wallCache[spaces.active]; idbWall.del(spaces.active).catch(() => {});
    const t = readTheme(); writeTheme(spaces.active, { mode: 'default', dim: t.dim, blur: t.blur });
    applyTheme(); renderLook();
  });
  const liveCtl = (inp, key, lbl, unit) => inp.addEventListener('input', () => {
    const t = readTheme(); t[key] = +inp.value; writeTheme(spaces.active, t);
    $(lbl).textContent = inp.value + unit;
    if (key === 'dim') document.documentElement.style.setProperty('--dim', String(+inp.value / 100));
    else document.documentElement.style.setProperty('--blur', `${inp.value}px`);
  });
  liveCtl($('#wall-dim'), 'dim', '#dim-val', '%');
  liveCtl($('#wall-blur'), 'blur', '#blur-val', 'px');

  /* =================== janelas de confirmação =================== */
  let dlgDone = null;
  function openDlg({ title, text = '', ok = 'OK', cancel = 'Cancelar', danger = false, icon = danger ? 'trash' : 'ok', input = null }) {
    if (dlgDone) dlgDone(null);
    $('#dlg-title').textContent = title;
    $('#dlg-text').textContent = text;
    $('#dlg-text').classList.toggle('hidden', !text);
    $('#dlg-ic').innerHTML = svg(icon);
    $('#dlg-ic').className = 'dlg-ic' + (danger ? ' danger' : '');
    $('#dlg-ok').textContent = ok;
    $('#dlg-ok').classList.toggle('danger-cta', danger);
    $('#dlg-cancel').textContent = cancel;
    const inp = $('#dlg-input');
    inp.classList.toggle('hidden', !input);
    if (input) {
      inp.type = input.type || 'text'; inp.value = input.value || ''; inp.placeholder = input.placeholder || '';
      inp.setAttribute('inputmode', input.type === 'password' ? 'text' : 'text');
    }
    $('#dlg').classList.remove('hidden'); $('#dlg-backdrop').classList.remove('hidden');
    if (input) setTimeout(() => { inp.focus(); inp.select(); }, 80);
    return new Promise(res => { dlgDone = v => { dlgDone = null; $('#dlg').classList.add('hidden'); $('#dlg-backdrop').classList.add('hidden'); res(v); }; });
  }
  const ask = opts => openDlg(opts).then(v => v === true);
  const askText = opts => openDlg({ icon: 'edit', ...opts, input: opts.input || {} }).then(v => (typeof v === 'string' && v.trim()) ? v.trim() : null);
  $('#dlg').addEventListener('submit', e => {
    e.preventDefault();
    if (!dlgDone) return;
    const inp = $('#dlg-input');
    if (!inp.classList.contains('hidden')) { if (!inp.value.trim()) { inp.focus(); return; } dlgDone(inp.value); }
    else dlgDone(true);
  });
  $('#dlg-cancel').addEventListener('click', () => dlgDone && dlgDone(null));
  $('#dlg-backdrop').addEventListener('click', () => dlgDone && dlgDone(null));

  /* =================== aviso =================== */
  let toastT;
  function toast(msg) {
    const el = $('#toast'); el.textContent = msg; el.classList.remove('hidden');
    clearTimeout(toastT); toastT = setTimeout(() => el.classList.add('hidden'), 2800);
  }

  if ('serviceWorker' in navigator && location.protocol === 'https:' && !window.Android) navigator.serviceWorker.register('sw.js').catch(() => {});
  if (document.fonts?.ready) document.fonts.ready.then(() => { if (ui.view === 'panorama' && db.txs.length) renderDashboard(); });
  applyTheme();
  go('panorama');
  loadWalls();
  setTimeout(() => runAutoBackup(false), 1200);
  // iPhone: aviso para instalar na Tela de Início (só no Safari, fora do app instalado)
  try {
    if (IS_IOS && !IS_STANDALONE && !window.Android && localStorage.getItem(STORE_KEY + ':iosHint') !== 'off') $('#ios-install').classList.remove('hidden');
  } catch (e) { /* */ }
  $('#ios-install-x').addEventListener('click', () => { $('#ios-install').classList.add('hidden'); try { localStorage.setItem(STORE_KEY + ':iosHint', 'off'); } catch (e) { /* */ } });
  // iPhone: deixa o gerador de planilhas pronto, para o “Exportar” abrir o compartilhar sem atraso
  if (IS_IOS) setTimeout(() => Parsers.loadXLSX().catch(() => {}), 2500);
})();
