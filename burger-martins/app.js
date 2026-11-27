// Martins Burger — app de pedidos (tudo salvo no próprio celular).
'use strict';

/* ---------- utilidades ---------- */
const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const brl = v => 'R$ ' + v.toFixed(2).replace('.', ',').replace(/\B(?=(\d{3})+(?!\d))/g, '.');
const pts = n => Math.round(n).toLocaleString('pt-BR');
const rnd = n => Math.floor(Math.random() * n);
const hex = n => Array.from({ length: n }, () => '0123456789abcdef'[rnd(16)]).join('');
const byId = (list, id) => list.find(x => x.id === id);
const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

const I = {
  pin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/></svg>',
  walk: '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="13" cy="4" r="2"/><path d="M9.8 8.9 7 23h2.1l1.8-8 2.1 2v6h2v-7.5l-2.1-2 .6-3A7.3 7.3 0 0 0 19 13v-2c-1.9 0-3.5-1-4.3-2.4l-1-1.6c-.4-.6-1-1-1.7-1-.3 0-.5.1-.8.1L6 8.3V13h2V9.6l1.8-.7"/></svg>',
  clock: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>',
  right: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m9 5 7 7-7 7"/></svg>',
  left: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m15 4-8 8 8 8"/></svg>',
  down: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m5 9 7 7 7-7"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round"><path d="M6 6l12 12M18 6 6 18"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>',
  copy: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="8" y="8" width="12" height="12" rx="2"/><path d="M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"/></svg>',
  check: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="m5 12 5 5 9-10"/></svg>',
  info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 11v6M12 7.5v.5" stroke-linecap="round"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>',
  minus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M5 12h14"/></svg>',
  trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
  home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M3 11 12 4l9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/></svg>',
  homeF: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 11 12 4l9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/></svg>',
  tag: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9z"/><circle cx="8" cy="8" r="1.8"/></svg>',
  tagF: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9zM8 9.8a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z" fill-rule="evenodd"/></svg>',
  flame: '<svg viewBox="-20 -24 40 48" fill="none" stroke="currentColor" stroke-width="3.2" stroke-linejoin="round"><path d="M0 -20C6 -11 15 -5 15 6C15 15 8 21 0 21C-8 21 -15 15 -15 6C-15 -2 -10 -5 -8 -11C-6 -5 -2 -3 0 -3C2 -9 -2 -15 0 -20Z"/></svg>',
  flameF: '<svg viewBox="-20 -24 40 48" fill="currentColor"><path d="M0 -20C6 -11 15 -5 15 6C15 15 8 21 0 21C-8 21 -15 15 -15 6C-15 -2 -10 -5 -8 -11C-6 -5 -2 -3 0 -3C2 -9 -2 -15 0 -20Z"/></svg>',
  user: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7z" stroke-linejoin="round"/></svg>',
  userF: '<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="8" r="4.5"/><path d="M3.5 21c0-4.8 3.8-7.5 8.5-7.5s8.5 2.7 8.5 7.5z"/></svg>',
  bag: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M5 8h14l-1 13H6z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/></svg>',
  rocket: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M12 15 9 12c1.5-5 5-9 11-9 0 6-4 9.5-9 11z"/><circle cx="15" cy="9" r="1.6"/><path d="M9 12H5l3-4h4M12 15v4l4-3v-4M6 16c-2 1-2 4-2 4s3 0 4-2"/></svg>',
  gift: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><rect x="3" y="8" width="18" height="5" rx="1"/><path d="M5 13v8h14v-8M12 8v13M12 8S10 3 7.5 4.5 9 8 12 8zm0 0s2-5 4.5-3.5S15 8 12 8z"/></svg>',
  cone: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M7 11h10l-5 11z"/><path d="M7 11a5 5 0 0 1 10 0"/></svg>',
  list: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h5"/></svg>',
  store: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M4 10v10h16V10M3 4h18l-1 6H4z"/><path d="M10 20v-5h4v5"/></svg>',
  help: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6M12 17v.5" stroke-linecap="round"/></svg>',
  chat: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="M4 4h16v12H9l-5 4z"/><path d="M8 9h8M8 12h5" stroke-linecap="round"/></svg>',
  trashU: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/></svg>',
  counter: '<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><circle cx="15" cy="6" r="2.4" fill="currentColor"/><path d="M11 15v-3a4 4 0 0 1 8 0v3"/><path d="M4 15h24M7 15v12h18V15"/></svg>',
  car: '<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"><path d="M5 20v-5l3-7h16l3 7v5zM5 20v4h4v-4M23 20v4h4v-4M5 15h22"/><circle cx="10" cy="18" r="1" fill="currentColor"/><circle cx="22" cy="18" r="1" fill="currentColor"/></svg>',
  table: '<svg viewBox="0 0 32 32" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round"><path d="M4 12h24M8 12v14M24 12v14M6 20h20"/><path d="M12 8h8"/></svg>',
  pix: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"><path d="m12 3 9 9-9 9-9-9z"/><path d="m7.5 7.5 4.5 4.5 4.5-4.5M7.5 16.5l4.5-4.5 4.5 4.5"/></svg>',
  card: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/></svg>',
  cash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/></svg>',
  pinF: '<svg viewBox="0 0 40 48"><ellipse cx="20" cy="45" rx="9" ry="2.5" fill="#000" opacity=".12"/><path d="M20 44S4 30 4 18a16 16 0 0 1 32 0c0 12-16 26-16 26z" fill="#F26A1B"/><g transform="translate(20 18) scale(.42)"><path d="M0 -20C6 -11 15 -5 15 6C15 15 8 21 0 21C-8 21 -15 15 -15 6C-15 -2 -10 -5 -8 -11C-6 -5 -2 -3 0 -3C2 -9 -2 -15 0 -20Z" fill="#FFF8EC"/></g></svg>',
  logo: '<svg viewBox="0 0 64 64"><rect width="64" height="64" rx="16" fill="#F26A1B"/><g transform="translate(32 33) scale(1)"><path d="M0 -20C6 -11 15 -5 15 6C15 15 8 21 0 21C-8 21 -15 15 -15 6C-15 -2 -10 -5 -8 -11C-6 -5 -2 -3 0 -3C2 -9 -2 -15 0 -20Z" fill="#FFF8EC"/></g></svg>'
};

const DIAS = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];
function fmtDate(t, withTime = true) {
  const d = new Date(t);
  const dd = String(d.getDate()).padStart(2, '0'), mm = String(d.getMonth() + 1).padStart(2, '0');
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return `${dd}/${mm}/${d.getFullYear()}${withTime ? ' · ' + time : ''}`;
}

/* ---------- dados salvos ---------- */
const KEY = 'martins-burger-v1';
function freshState() {
  const letters = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  return {
    name: '', phone: '', email: '', birth: '',
    id: Array.from({ length: 6 }, () => letters[rnd(letters.length)]).join(''),
    storeId: 'paulista', points: 0, moves: [], bag: [], orders: [], claimed: {},
    stats: { coupons: 0, desserts: 0, rewards: 0 }, raffle: [], doubleNext: false,
    messages: [], onboarded: false, geo: null, seenMissions: 0
  };
}
let S;
try { S = Object.assign(freshState(), JSON.parse(localStorage.getItem(KEY) || 'null') || {}); } catch { S = freshState(); }
function save() { try { localStorage.setItem(KEY, JSON.stringify(S)); } catch { } }

function addPoints(n, desc) {
  S.points += n;
  S.moves.unshift({ at: Date.now(), desc, n });
}

/* ---------- restaurantes ---------- */
const store = () => byId(STORES, S.storeId) || STORES[0];
function isOpen(st, d = new Date()) {
  const m = d.getHours() * 60 + d.getMinutes();
  const toMin = s => { const [h, mm] = s.split(':').map(Number); return h * 60 + mm; };
  return m >= toMin(st.open) && m < toMin(st.close);
}
function hoursText(st) {
  if (st.open === '00:00' && st.close === '24:00') return 'Aberto 24 horas';
  if (isOpen(st)) return `Fecha às ${st.close === '24:00' ? '00:00' : st.close}`;
  return `Abre às ${st.open}`;
}
function distKm(st) {
  if (!S.geo) return null;
  const R = 6371, r = x => x * Math.PI / 180;
  const dLat = r(st.lat - S.geo.lat), dLng = r(st.lng - S.geo.lng);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(r(S.geo.lat)) * Math.cos(r(st.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}
const kmText = k => k < 1 ? `${Math.round(k * 1000)} m` : `${k.toFixed(1).replace('.', ',')} km`;

/* ---------- produtos e preços ---------- */
function defaultOpts(p) {
  return {
    size: p.sizes ? p.sizes[0].id : undefined,
    flavor: p.flavors ? 'cola' : undefined,
    remove: [], extras: [], combo: 'none', comboDrink: 'cola'
  };
}
function fullOpts(p, o) { return Object.assign(defaultOpts(p), o || {}); }
function unitPrice(p, o) {
  o = fullOpts(p, o);
  let v = p.price;
  if (p.sizes) v += (byId(p.sizes, o.size) || p.sizes[0]).add;
  for (const e of o.extras) v += EXTRAS[e].add;
  if (p.combo) v += byId(COMBOS, o.combo).add;
  return Math.round(v * 100) / 100;
}
function itemName(p, o) {
  o = fullOpts(p, o);
  let n = p.name;
  if (p.flavors) n = `${byId(DRINK_FLAVORS, o.flavor).name}`;
  if (p.sizes) n += ` ${p.flavors ? '' : ''}${(byId(p.sizes, o.size) || p.sizes[0]).name}`;
  if (p.combo && o.combo !== 'none') n = `${byId(COMBOS, o.combo).name.replace('Combo', 'Combo')} ${p.name}`;
  return n;
}
function optsText(p, o) {
  o = fullOpts(p, o);
  const t = [];
  if (p.combo && o.combo !== 'none') {
    const c = byId(COMBOS, o.combo);
    t.push(`${c.desc.replace('refri', byId(DRINK_FLAVORS, o.comboDrink).name)}`);
  }
  for (const r of o.remove) t.push(`Sem ${r.toLowerCase()}`);
  for (const e of o.extras) t.push(`+ ${EXTRAS[e].name}`);
  return t.join(' · ');
}
function artFor(p, o) {
  if (p.flavors) return ART.drink(fullOpts(p, o).flavor);
  return artOf(p.art);
}

/* ---------- sacola ---------- */
const bagCount = () => S.bag.reduce((a, l) => a + l.qty, 0);
const bagMoney = () => Math.round(S.bag.reduce((a, l) => a + (l.price || 0) * l.qty, 0) * 100) / 100;
const bagPts = () => S.bag.reduce((a, l) => a + (l.pts || 0) * l.qty, 0);
const bagFrom = () => S.bag.reduce((a, l) => a + (l.from || l.price || 0) * l.qty, 0);

function addProductToBag(p, o, qty, editKey) {
  o = fullOpts(p, o);
  const line = { key: editKey || hex(8), type: 'product', pid: p.id, opts: o, qty, price: unitPrice(p, o), title: itemName(p, o), sub: optsText(p, o) };
  if (editKey) {
    const i = S.bag.findIndex(l => l.key === editKey);
    if (i >= 0) S.bag[i] = line; else S.bag.push(line);
  } else {
    const same = S.bag.find(l => l.type === 'product' && l.pid === p.id && JSON.stringify(l.opts) === JSON.stringify(o));
    if (same) same.qty += qty; else S.bag.push(line);
  }
  save();
}
function addCouponToBag(c) {
  if (S.bag.some(l => l.type === 'coupon' && l.cid === c.id)) { toast('Esse cupom já está na sua sacola'); return false; }
  const parts = c.items.map(it => { const p = byId(PRODUCTS, it.pid); return itemName(p, it.opts); });
  S.bag.push({ key: hex(8), type: 'coupon', cid: c.id, qty: 1, price: c.price, from: c.from, title: c.title, sub: 'Cupom · ' + parts.join(' + ') });
  save();
  return true;
}
function addRewardToBag(r) {
  const p = byId(PRODUCTS, r.pid);
  if (S.points - bagPts() < r.cost) { toast(`Faltam ${pts(r.cost - (S.points - bagPts()))} brasas para esse prêmio`); return false; }
  S.bag.push({ key: hex(8), type: 'reward', rid: r.id, qty: 1, price: 0, pts: r.cost, title: r.title || p.name, sub: `Troca de ${pts(r.cost)} brasas` });
  save();
  return true;
}

/* ---------- pedidos ---------- */
const STAGES = [
  { t: 'Pedido recebido', d: 'O restaurante recebeu seu pedido.' },
  { t: 'Preparando seu pedido', d: 'Seu pedido está sendo preparado.' },
  { t: 'Pronto! Pode retirar', d: 'Seu pedido está esperando por você.' },
  { t: 'Pedido entregue', d: 'Bom apetite!' }
];
const MODES = {
  balcao: { short: 'Balcão', name: 'Retirada no balcão', hint: 'Retire seu pedido quando seu número aparecer no painel', icon: 'counter' },
  drive: { short: 'Drive-thru', name: 'Drive-thru', hint: 'Informe o número do pedido no alto-falante do drive-thru', icon: 'car' },
  mesa: { short: 'Na mesa', name: 'Entrega na mesa', hint: 'Fique na sua mesa: levamos o pedido até você', icon: 'table' }
};
function stageOf(o) {
  if (o.done) return 3;
  const s = (Date.now() - o.at) / 1000;
  if (s > 2 * 3600) return 3;
  return s < 12 ? 0 : s < 75 ? 1 : 2;
}
const activeOrders = () => S.orders.filter(o => stageOf(o) < 3);

function placeOrder({ mode, table, pay }) {
  const money = bagMoney(), spend = bagPts();
  const num = 100 + rnd(900);
  const items = S.bag.map(l => ({ ...l }));
  const order = { id: hex(24), num, at: Date.now(), storeId: S.storeId, mode, table, pay, items, money, spend, earned: 0, done: false };
  if (spend) addPoints(-spend, `Troca: ${items.filter(l => l.type === 'reward').map(l => l.title).join(', ')}`);
  let earn = Math.floor(money) * BRAND.perReal;
  for (const l of items) if (l.type === 'coupon') earn += (byId(COUPONS, l.cid)?.bonus || 0) * l.qty;
  if (earn > 0 && S.doubleNext && money > 0) { earn *= 2; S.doubleNext = false; }
  if (earn > 0) addPoints(earn, `Pedido ${num}`);
  order.earned = earn;
  S.stats.coupons += items.filter(l => l.type === 'coupon').length;
  S.stats.rewards += items.filter(l => l.type === 'reward').length;
  const isDessert = l => {
    if (l.type === 'product') return byId(PRODUCTS, l.pid)?.cat === 'sobremesas';
    if (l.type === 'reward') return byId(PRODUCTS, byId(REWARDS, l.rid)?.pid)?.cat === 'sobremesas';
    if (l.type === 'coupon') return byId(COUPONS, l.cid).items.some(it => byId(PRODUCTS, it.pid).cat === 'sobremesas');
  };
  if (items.some(isDessert)) S.stats.desserts += 1;
  S.orders.unshift(order);
  S.bag = [];
  save();
  return order;
}

/* ---------- missões ---------- */
const missionState = m => { const [cur, max] = m.progress(S); return { cur, max, done: cur >= max, claimed: !!S.claimed[m.id] }; };
const claimableCount = () => MISSIONS.filter(m => { const s = missionState(m); return s.done && !s.claimed; }).length;

/* ---------- navegação ---------- */
const TABS = ['home', 'coupons', 'club', 'account'];
let stack = [{ name: S.onboarded ? 'home' : 'welcome', params: {} }];
let sheetOpen = false, sheetThen = null;
const cur = () => stack[stack.length - 1];

function go(name, params = {}) {
  cur().scroll = window.scrollY;
  stack.push({ name, params });
  history.pushState({ n: stack.length }, '');
  render(true);
}
function replaceTop(name, params = {}) { stack[stack.length - 1] = { name, params }; render(true); }
function back() { history.back(); }
function setTab(name) {
  if (cur().name === name && stack.length === 1) { window.scrollTo({ top: 0, behavior: 'smooth' }); return; }
  const extra = stack.length - 1;
  stack = [{ name, params: {} }];
  render(true);
  if (extra > 0) { ignorePops += extra; history.go(-extra); }
}
let ignorePops = 0;
window.addEventListener('popstate', () => {
  if (ignorePops > 0) { ignorePops--; return; }
  if (sheetOpen) { hideSheet(); const t = sheetThen; sheetThen = null; if (t) t(); return; }
  if (stack.length > 1) { stack.pop(); render(false); return; }
  if (cur().name !== 'home' && cur().name !== 'welcome') {
    stack = [{ name: 'home', params: {} }]; history.pushState({ n: 1 }, ''); render(true);
  }
});

/* ---------- folha de baixo (sheet) e avisos ---------- */
function openSheet(html, cls = '') {
  const sh = $('#sheet');
  sh.querySelector('.sheet-body').innerHTML = html;
  sh.className = 'sheet-wrap show ' + cls;
  sheetOpen = true;
  history.pushState({ sheet: 1 }, '');
}
function hideSheet() { $('#sheet').className = 'sheet-wrap'; sheetOpen = false; }
function closeSheet(then) { if (!sheetOpen) { then && then(); return; } sheetThen = then || null; history.back(); }

let toastTimer;
function toast(msg, action) {
  const t = $('#toast');
  t.innerHTML = `<span>${esc(msg)}</span>${action ? `<button data-a="${action.a}" ${action.data || ''}>${esc(action.label)}</button>` : ''}`;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}
async function copyText(txt, msg = 'Copiado') {
  try { await navigator.clipboard.writeText(txt); }
  catch {
    const ta = document.createElement('textarea'); ta.value = txt; document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); } catch { } ta.remove();
  }
  toast(msg);
}

/* ---------- pedaços de tela reutilizados ---------- */
function storeCard() {
  const st = store(), open = isOpen(st), k = distKm(st);
  return `<div class="pickhead"><span class="pinF">${I.pinF}</span><b>Retirar em</b></div>
  <button class="storecard" data-a="stores">
    <div class="sc-main">
      <div class="sc-name">${esc(st.name)}</div>
      <div class="sc-row">${I.pin}<span>${esc(st.addr)}<br>${esc(st.area)}</span></div>
      ${k != null ? `<div class="sc-row">${I.walk}<span>${kmText(k)}</span></div>` : ''}
      <div class="sc-row">${I.clock}<span><b class="${open ? 'ok' : 'bad'}">${open ? 'Aberto' : 'Fechado'}</b>&nbsp;&nbsp;${hoursText(st)}</span></div>
    </div>
    <span class="chev">${I.right}</span>
  </button>`;
}

function activeOrderCard() {
  const o = activeOrders()[0];
  if (!o) return '';
  const st = byId(STORES, o.storeId) || store(), sg = stageOf(o);
  return `<button class="ordercard" data-a="order" data-id="${o.id}">
    <span class="track"><i class="dot ${sg >= 2 ? 'ready' : ''}">${I.check}</i></span>
    <span class="oc-main">
      <span class="oc-top">${I.pin}<b>Retirar em</b> <span class="ell">${esc(st.addr)}</span></span>
      <span class="oc-title">${STAGES[sg].t}</span>
      <span class="oc-sub">${MODES[o.mode].name}${o.mode === 'mesa' ? ` · Mesa ${esc(o.table)}` : ''} <b>${o.num}</b></span>
    </span>
    <span class="chev">${I.right}</span>
  </button>`;
}

function priceBig(v) {
  const [r, c] = v.toFixed(2).split('.');
  return `<span class="pbig"><small>R$</small><b>${r}</b><sup>,${c}</sup></span>`;
}

function couponCard(c, big = false) {
  const [a, b] = c.bg;
  return `<div class="coupon ${big ? 'big' : ''}" style="--a:${a};--b:${b}">
    <button class="cp-hit" data-a="coupon" data-id="${c.id}" aria-label="${esc(c.title)}"></button>
    <svg class="cp-notch" viewBox="0 0 100 10" preserveAspectRatio="none"><path d="M0 0h52c6 0 8 8 16 8s10-8 16-8h16v0H0z" fill="var(--bg)"/></svg>
    <span class="cp-badge">${I.flameF}+${pts(c.bonus)} brasas</span>
    <div class="cp-text">
      <div class="cp-title">${esc(c.title)}</div>
      ${c.from ? `<div class="cp-from">de ${brl(c.from)} por</div>` : ''}
      ${priceBig(c.price)}
      <button class="cp-cta" data-a="takeCoupon" data-id="${c.id}">Pegar cupom</button>
    </div>
    <div class="cp-art">${artOf(c.art)}</div>
    <button class="cp-info" data-a="couponInfo" data-id="${c.id}" aria-label="Regras">${I.info}</button>
  </div>`;
}

function carousel(id, slides) {
  return `<div class="carousel" data-car="${id}">
    <div class="car-track">${slides.map(s => `<div class="car-slide">${s}</div>`).join('')}</div>
    <div class="dots">${slides.map((_, i) => `<i class="${i === 0 ? 'on' : ''}"></i>`).join('')}</div>
  </div>`;
}

function bannerSlide(b) {
  const c = b.coupon ? byId(COUPONS, b.coupon) : null;
  const p = b.product ? byId(PRODUCTS, b.product) : null;
  const [a, bb] = b.bg || (c ? c.bg : ['#1E1033', '#F26A1B']);
  const price = c ? c.price : p.price;
  const art = c ? artOf(c.art) : artOf(p.art);
  const act = c ? `data-a="takeCoupon" data-id="${c.id}"` : `data-a="product" data-id="${p.id}"`;
  return `<div class="banner" style="--a:${a};--b:${bb}">
    <button class="cp-hit" ${c ? `data-a="coupon" data-id="${c.id}"` : `data-a="product" data-id="${p.id}"`} aria-label="${esc(b.title)}"></button>
    <svg class="cp-notch" viewBox="0 0 100 10" preserveAspectRatio="none"><path d="M0 0h52c6 0 8 8 16 8s10-8 16-8h16v0H0z" fill="var(--bg)"/></svg>
    ${c ? `<span class="cp-badge">${I.flameF}+${pts(c.bonus)} brasas</span>` : '<span class="cp-badge">Novo</span>'}
    <div class="bn-rays"></div>
    <div class="cp-text">
      <div class="bn-kick">${esc(b.kicker)}</div>
      <div class="bn-title">${esc(b.title).replace('\n', '<br>')}</div>
      ${priceBig(price)}
      <button class="cp-cta" ${act}>${esc(b.cta)}</button>
    </div>
    <div class="cp-art bn-art">${art}</div>
    ${c ? `<button class="cp-info" data-a="couponInfo" data-id="${c.id}" aria-label="Regras">${I.info}</button>` : ''}
  </div>`;
}

function productRow(p) {
  return `<button class="prow" data-a="product" data-id="${p.id}">
    <span class="prow-art">${artOf(p.art)}</span>
    <span class="prow-main"><b>${esc(p.name)}</b><span>${esc(p.desc)}</span><em>${p.sizes ? 'a partir de ' : ''}${brl(p.price)}</em></span>
    <span class="prow-add">${I.plus}</span>
  </button>`;
}

function topbar(title, opts = {}) {
  return `<header class="topbar ${opts.cls || ''}"><button class="iconbtn" data-a="back" aria-label="Voltar">${I.left}</button><h1>${esc(title)}</h1>${opts.right || ''}</header>`;
}

/* ---------- telas ---------- */
const SCREENS = {};

SCREENS.welcome = () => `<div class="welcome">
  <div class="wl-logo">${I.logo}</div>
  <h1>Bem-vindo ao<br>${BRAND.name}</h1>
  <p>Peça pelo app, retire sem fila e junte <b>brasas</b> para trocar por lanches.</p>
  <div class="wl-art">${artOf(['burger', ['patty', 'cheese', 'tomato', 'lettuce', 'sauce']])}</div>
  <label class="field"><span>Como podemos te chamar?</span><input id="wlName" autocomplete="given-name" placeholder="Seu nome" maxlength="30"></label>
  <button class="btn primary block" data-a="welcomeGo">Começar</button>
  <p class="small">Ganhe 2.000 brasas de boas-vindas.</p>
</div>`;

SCREENS.home = () => `<div class="pad">
  ${storeCard()}
  ${activeOrderCard()}
  <div class="offerrow">
    ${OFFER_GROUPS.map(g => `<button class="offer" data-a="offers" data-id="${g.id}"><span class="of-big">${g.id === 'sexta' ? `<i class="of-flame">${I.flameF}</i>` : esc(g.big)}</span><span class="of-lbl">${esc(g.label)}<br>${g.id === 'sexta' ? 'Brasa' : esc(g.big.replace('R$', 'R$ '))}</span></button>`).join('')}
  </div>
  ${carousel('home', BANNERS.map(bannerSlide))}
  <h2 class="sec">Mais pedidos</h2>
  <div class="hscroll">${PRODUCTS.filter(p => p.best).map(p => `<button class="pcard" data-a="product" data-id="${p.id}"><span class="pc-art">${artOf(p.art)}</span><b>${esc(p.name)}</b><em>${brl(p.price)}</em></button>`).join('')}</div>
  <h2 class="sec" id="cardapio">Cardápio</h2>
  <div class="cats" id="cats">${CATEGORIES.map((c, i) => `<button class="chip ${i === 0 ? 'on' : ''}" data-a="cat" data-id="${c.id}">${esc(c.name)}</button>`).join('')}</div>
  ${CATEGORIES.map(c => `<section class="catsec" id="cat-${c.id}"><h3>${esc(c.name)}</h3>${PRODUCTS.filter(p => p.cat === c.id).map(productRow).join('')}</section>`).join('')}
  <p class="foot">Pagamento simulado · versão de demonstração</p>
</div>`;

SCREENS.coupons = () => {
  const sections = [
    { t: 'Sexta Brasa 🔥', f: c => c.tags.includes('sexta') },
    { t: 'Por R$ 1,99', f: c => c.tags.includes('r199') },
    { t: 'Por R$ 5', f: c => c.tags.includes('r5') },
    { t: 'Por R$ 15', f: c => c.tags.includes('r15') }
  ];
  return `<div class="pad">
    ${storeCard()}
    <label class="search">${I.search}<input id="cpSearch" placeholder="Buscar cupons" autocomplete="off"></label>
    <div id="cpResults" hidden></div>
    <div id="cpMain">
      ${carousel('coupons', COUPONS.filter(c => c.tags.includes('destaque')).map(c => couponCard(c, true)))}
      ${sections.map(s => `<h2 class="sec">${s.t}</h2><div class="hscroll cps">${COUPONS.filter(s.f).map(c => couponCard(c)).join('')}</div>`).join('')}
    </div>
  </div>`;
};

function rewardCard(r) {
  const p = r.pid ? byId(PRODUCTS, r.pid) : null;
  const title = r.title || p.name;
  if (r.sec === 'exp') {
    const [a, b] = r.bg;
    return `<button class="xcard" data-a="reward" data-id="${r.id}">
      <span class="xc-img" style="--a:${a};--b:${b}"><span class="xc-big">${pts(r.cost)}</span><span class="xc-small">brasas</span><span class="xc-art">${artOf(r.art)}</span></span>
      <span class="xc-info"><span class="ptag">${I.flameF}${pts(r.cost)} brasas</span><span class="xc-title">${esc(title)}</span></span>
    </button>`;
  }
  return `<button class="rcard" data-a="reward" data-id="${r.id}">
    <span class="rc-img"><span class="rc-ring"></span>${artOf(r.art)}
      <span class="rc-tag">${r.from ? `De ${pts(r.from)} por ${pts(r.cost)}` : `${pts(r.cost)} brasas`}</span></span>
    <span class="rc-title">${esc(title)}</span>
    <span class="rc-cost">${I.flameF}${pts(r.cost)}</span>
  </button>`;
}

SCREENS.club = () => `<div class="pad">
  ${storeCard()}
  <div class="balance">
    <div class="bal-n">${pts(S.points)} <small>brasas</small></div>
    <button class="link" data-a="moves">Extrato ${I.right}</button>
  </div>
  ${S.doubleNext ? `<div class="notice">${I.flameF}<span>Seu próximo pedido pago vale <b>brasas em dobro</b>.</span></div>` : ''}
  <div class="tabs" id="clubTabs">${REWARD_SECTIONS.map((s, i) => `<button class="${i === 0 ? 'on' : ''}" data-a="clubTab" data-id="${s.id}">${esc(s.name)}</button>`).join('')}</div>
  ${REWARD_SECTIONS.map(s => `<section class="rsec" id="rs-${s.id}"><h2 class="sec big">${esc(s.name)}</h2>
    <div class="hscroll">${REWARDS.filter(r => r.sec === s.id).map(rewardCard).join('')}</div>
    ${s.id === 'exp' && S.raffle.length ? `<p class="small">Seus números da sorte: <b>${S.raffle.join(', ')}</b></p>` : ''}
  </section>`).join('')}
  <button class="mission-banner" data-a="missions">${I.rocket}<span><b>Missões</b><br>Cumpra desafios e ganhe brasas extras</span>${claimableCount() ? `<i class="badge">${claimableCount()}</i>` : ''}<span class="chev">${I.right}</span></button>
</div>`;

function qrSvg(text) {
  try {
    const q = qrcode(0, 'M');
    q.addData(text); q.make();
    return q.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
  } catch { return ''; }
}
function seeds() {
  const pos = [[6, 8, 30], [14, 30, -20], [4, 58, 60], [10, 84, 10], [20, 70, -50], [88, 6, -30], [94, 26, 40], [84, 46, 10], [96, 64, -20], [86, 88, 50], [30, 92, 20], [70, 94, -40], [50, 4, 70], [24, 10, -60], [76, 14, 30]];
  return pos.map(([x, y, r]) => `<i style="left:${x}%;top:${y}%;transform:rotate(${r}deg)"></i>`).join('');
}

SCREENS.account = () => {
  const cm = claimableCount();
  const items = [
    ['profile', 'user', 'Meu perfil'],
    ['missions', 'rocket', 'Missões', cm ? `<i class="newb">${cm} nova${cm > 1 ? 's' : ''}</i>` : ''],
    ['orders', 'list', 'Meus pedidos'],
    ['stores', 'store', 'Restaurantes'],
    ['faq', 'help', 'Perguntas frequentes'],
    ['contact', 'chat', 'Fale conosco'],
    ['wipe', 'trashU', 'Apagar meus dados']
  ];
  return `<div class="acc">
    <div class="pad">
      <h1 class="hello"><b>Olá</b> ${esc(S.name || 'você')}</h1>
      <button class="mbid" data-a="copyId">ID ${esc(BRAND.name.split(' ')[0])}. ${esc(S.id)} ${I.copy}</button>
      <h3 class="qr-h">Mostre o QR code no caixa e ganhe brasas</h3>
      <p class="qr-p">Peça, junte brasas e troque por mais lanches para aproveitar.</p>
    </div>
    <div class="qrband">${seeds()}
      <button class="qrbox" data-a="qrBig" aria-label="Ampliar QR code">${qrSvg('MARTINSBURGER:' + S.id)}</button>
      <p>Toque no código para ampliar</p>
    </div>
    <div class="menu">${items.map(([a, ic, t, extra]) => `<button class="mi" data-a="${a}">${I[ic]}<span>${t}</span>${extra || ''}</button>`).join('')}</div>
    <p class="foot">${BRAND.name} · versão 1.0 de demonstração</p>
  </div>`;
};

SCREENS.offers = ({ id }) => {
  const g = byId(OFFER_GROUPS, id);
  const list = COUPONS.filter(c => c.tags.includes(id));
  return `${topbar(g.title)}<div class="pad list-cp">${list.map(c => couponCard(c, true)).join('') || '<p class="empty">Nenhuma oferta agora.</p>'}</div>`;
};

// rascunho do produto que está sendo montado
let draft = null;
SCREENS.product = ({ id, edit }) => {
  const p = byId(PRODUCTS, id);
  if (!draft || draft.pid !== id || draft.edit !== edit) {
    const line = edit ? S.bag.find(l => l.key === edit) : null;
    draft = { pid: id, edit, qty: line ? line.qty : 1, opts: line ? JSON.parse(JSON.stringify(line.opts)) : defaultOpts(p) };
  }
  return productHTML(p);
};
function productHTML(p) {
  const o = draft.opts;
  const opt = (name, val, label, extra = '', type = 'radio') => {
    const on = type === 'radio' ? o[name] === val : (o[name] || []).includes(val);
    return `<button class="opt ${on ? 'on' : ''}" data-a="opt" data-k="${name}" data-v="${esc(val)}" data-t="${type}"><i class="${type}">${on ? I.check : ''}</i><span>${label}</span>${extra}</button>`;
  };
  const total = unitPrice(p, o) * draft.qty;
  return `<div class="prod">
    <div class="prod-hero"><button class="iconbtn float" data-a="back" aria-label="Fechar">${I.close}</button><div class="prod-art" id="prodArt">${artFor(p, o)}</div></div>
    <div class="pad">
      <h1 class="prod-name">${esc(p.name)}</h1>
      <div class="prod-price">${brl(p.price)}${p.sizes ? ' <small>a partir de</small>' : ''}</div>
      <p class="prod-desc">${esc(p.desc)}</p>
      <p class="kcal">${p.kcal} kcal</p>
      ${p.flavors ? `<h3 class="opt-h">Sabor</h3>${DRINK_FLAVORS.map(f => opt('flavor', f.id, f.name)).join('')}` : ''}
      ${p.sizes ? `<h3 class="opt-h">Tamanho</h3>${p.sizes.map(s => opt('size', s.id, s.name, s.add ? `<em>+ ${brl(s.add)}</em>` : '')).join('')}` : ''}
      ${p.combo ? `<h3 class="opt-h">Quer fazer um combo?</h3>${COMBOS.map(c => opt('combo', c.id, c.desc ? `${c.name}<small>${c.desc}</small>` : c.name, c.add ? `<em>+ ${brl(c.add)}</em>` : '')).join('')}` : ''}
      ${p.combo && o.combo !== 'none' ? `<h3 class="opt-h">Bebida do combo</h3>${DRINK_FLAVORS.map(f => opt('comboDrink', f.id, f.name)).join('')}` : ''}
      ${p.ingredients ? `<h3 class="opt-h">Ingredientes <small>toque para tirar</small></h3>${p.ingredients.map(n => `<button class="opt ${o.remove.includes(n) ? '' : 'on'}" data-a="ingr" data-v="${esc(n)}"><i class="box">${o.remove.includes(n) ? '' : I.check}</i><span>${esc(n)}</span>${o.remove.includes(n) ? '<em class="muted">sem</em>' : ''}</button>`).join('')}` : ''}
      ${p.extras ? `<h3 class="opt-h">Adicionais</h3>${p.extras.map(e => opt('extras', e, EXTRAS[e].name, `<em>+ ${brl(EXTRAS[e].add)}</em>`, 'box')).join('')}` : ''}
    </div>
    <div class="buybar">
      <div class="qty"><button data-a="dqty" data-d="-1" aria-label="Menos">${I.minus}</button><b>${draft.qty}</b><button data-a="dqty" data-d="1" aria-label="Mais">${I.plus}</button></div>
      <button class="btn primary grow" data-a="addDraft">${draft.edit ? 'Atualizar' : 'Adicionar'} · ${brl(total)}</button>
    </div>
  </div>`;
}

SCREENS.bag = () => {
  if (!S.bag.length) return `${topbar('Sacola')}<div class="emptybox">${I.bag}<p>Sua sacola está vazia</p><button class="btn primary" data-a="toMenu">Ver cardápio</button></div>`;
  const money = bagMoney(), spend = bagPts(), from = bagFrom();
  const disc = Math.max(0, from - money - 0.001);
  const st = store();
  const ck = checkout;
  let earn = Math.floor(money) * BRAND.perReal + S.bag.filter(l => l.type === 'coupon').reduce((a, l) => a + (byId(COUPONS, l.cid)?.bonus || 0), 0);
  if (S.doubleNext && money > 0) earn *= 2;
  const lines = S.bag.map(l => {
    const p = l.pid ? byId(PRODUCTS, l.pid) : null;
    const art = l.type === 'product' ? artFor(p, l.opts) : l.type === 'coupon' ? artOf(byId(COUPONS, l.cid).art) : artOf(byId(REWARDS, l.rid).art);
    return `<div class="bline">
      <span class="bl-art">${art}</span>
      <div class="bl-main">
        <b>${esc(l.title)}</b>
        ${l.sub ? `<span>${esc(l.sub)}</span>` : ''}
        <div class="bl-bottom">
          ${l.type === 'product'
            ? `<div class="qty sm"><button data-a="lqty" data-key="${l.key}" data-d="-1" aria-label="Menos">${l.qty === 1 ? I.trash : I.minus}</button><b>${l.qty}</b><button data-a="lqty" data-key="${l.key}" data-d="1" aria-label="Mais">${I.plus}</button></div>
               <button class="link sm" data-a="editLine" data-key="${l.key}">Editar</button>`
            : `<button class="link sm danger" data-a="rmLine" data-key="${l.key}">Remover</button>`}
          <em>${l.type === 'reward' ? `${I.flameF}${pts(l.pts)}` : brl(l.price * l.qty)}</em>
        </div>
      </div>
    </div>`;
  }).join('');
  const modeBtn = (id) => `<button class="mode ${ck.mode === id ? 'on' : ''}" data-a="mode" data-id="${id}">${I[MODES[id].icon]}<span>${MODES[id].short}</span></button>`;
  const payBtn = (id, icon, label) => `<button class="opt ${ck.pay === id ? 'on' : ''}" data-a="pay" data-id="${id}"><i class="radio">${ck.pay === id ? I.check : ''}</i>${I[icon]}<span>${label}</span></button>`;
  return `${topbar('Sacola')}
  <div class="pad bag">
    <div class="bag-store">${I.store}<span>Retirar em <b>${esc(st.name)}</b></span><button class="link sm" data-a="stores">Trocar</button></div>
    ${lines}
    <button class="link addmore" data-a="toMenu">${I.plus} Adicionar mais itens</button>
    <h3 class="opt-h">Como você quer receber?</h3>
    <div class="modes">${modeBtn('balcao')}${modeBtn('drive')}${modeBtn('mesa')}</div>
    ${ck.mode === 'mesa' ? `<label class="field"><span>Número da mesa</span><input id="tableNum" inputmode="numeric" maxlength="3" value="${esc(ck.table)}" placeholder="Ex.: 12"></label>` : ''}
    ${money > 0 ? `<h3 class="opt-h">Pagamento</h3>
      ${payBtn('pix', 'pix', 'Pix')}${payBtn('card', 'card', 'Cartão de crédito ou débito')}${payBtn('local', 'cash', 'Pagar no restaurante')}
      <p class="small">Pagamento simulado: nada é cobrado nesta versão.</p>` : ''}
    <div class="summary">
      <div><span>Subtotal</span><span>${brl(from)}</span></div>
      ${disc > 0.009 ? `<div class="ok"><span>Descontos de cupons</span><span>− ${brl(disc)}</span></div>` : ''}
      ${spend ? `<div><span>Brasas usadas</span><span>${pts(spend)}</span></div>` : ''}
      <div class="tot"><span>Total</span><span>${brl(money)}</span></div>
      ${earn > 0 ? `<div class="earn">${I.flameF}<span>Você vai ganhar <b>${pts(earn)} brasas</b>${S.doubleNext && money > 0 ? ' (em dobro!)' : ''}</span></div>` : ''}
    </div>
  </div>
  <div class="buybar"><button class="btn primary grow" data-a="placeOrder">Fazer pedido · ${money > 0 ? brl(money) : pts(spend) + ' brasas'}</button></div>`;
};
const checkout = { mode: 'balcao', pay: 'pix', table: '' };

SCREENS.order = ({ id }) => {
  const o = S.orders.find(x => x.id === id);
  if (!o) return `${topbar('Seu pedido')}<p class="empty">Pedido não encontrado.</p>`;
  const sg = stageOf(o), st = byId(STORES, o.storeId) || store(), m = MODES[o.mode];
  const ui = orderUI[o.id] || (orderUI[o.id] = { tl: false, items: true });
  const count = o.items.reduce((a, l) => a + l.qty, 0);
  return `<div class="ordertop">
    ${topbar('Seu pedido', { cls: 'plain' })}
    <button class="status" data-a="toggleTl" data-id="${o.id}">
      <span class="track"><i class="dot ${sg >= 2 ? 'ready' : ''}">${I.check}</i></span>
      <span class="st-title">${STAGES[sg].t}</span>
      <span class="chev ${ui.tl ? 'up' : ''}">${I.down}</span>
    </button>
    ${ui.tl ? `<ol class="timeline">${STAGES.map((s, i) => `<li class="${i < sg ? 'past' : i === sg ? 'now' : ''}"><i>${i <= sg ? I.check : ''}</i><div><b>${s.t}</b><span>${i === 0 ? fmtDate(o.at) : s.d}</span></div></li>`).join('')}</ol>` : ''}
  </div>
  <div class="pad orderpage">
    <div class="pickcard ${sg === 2 ? 'ready' : ''}">
      <span class="pk-ic">${I[m.icon]}</span>
      <div><b>${m.name}${o.mode === 'mesa' ? ` · Mesa ${esc(o.table)}` : ''}</b><span>${m.hint}</span></div>
      <span class="pk-num">${o.num}</span>
    </div>
    ${sg === 2 ? `<button class="btn primary block" data-a="pickedUp" data-id="${o.id}">Já peguei meu pedido</button>` : ''}
    <button class="ordno" data-a="copyOrder" data-id="${o.id}">Pedido nº ${o.id.slice(0, -4)}<b>${o.id.slice(-4)}</b> ${I.copy}</button>
    <button class="sumrow" data-a="toggleItems" data-id="${o.id}">
      <span class="bagic">${I.bag}</span><span><b>Seu pedido</b><small>${plural(count, 'produto', 'produtos')}</small></span><span class="chev ${ui.items ? 'up' : ''}">${I.down}</span>
    </button>
    ${ui.items ? `<div class="oitems">${o.items.map(l => `<div class="oi"><b>${l.qty}x</b><span>${esc(l.title)}${l.sub ? `<small>${esc(l.sub)}</small>` : ''}</span><em>${l.type === 'reward' ? `${I.flameF} ${pts(l.pts * l.qty)} brasas` : brl(l.price * l.qty)}</em></div>`).join('')}
      <div class="oi tot"><b></b><span>Total${o.pay ? ` · ${{ pix: 'Pix', card: 'Cartão', local: 'Pagar no restaurante' }[o.pay] || ''}` : ''}</span><em>${o.money > 0 ? brl(o.money) : pts(o.spend) + ' brasas'}</em></div>
      ${o.earned ? `<div class="earn">${I.flameF}<span>Você ganhou <b>${pts(o.earned)} brasas</b> com este pedido</span></div>` : ''}
    </div>` : ''}
    <div class="pickat"><span class="pinF">${I.pinF}</span><div><b>Retirar em</b><strong>${esc(st.name)}</strong><span>${esc(st.addr)}<br>${esc(st.area)}</span></div></div>
    <div class="helprow">
      <div><b>Alguma dúvida?</b><button class="link" data-a="faq">Perguntas frequentes</button></div>
      <div><b>Precisa de ajuda?</b><button class="link" data-a="contact">${I.chat} Fale conosco</button></div>
    </div>
    ${sg === 3 ? `<button class="btn ghost block" data-a="reorder" data-id="${o.id}">Pedir de novo</button>` : ''}
  </div>`;
};
const orderUI = {};

SCREENS.orders = () => `${topbar('Meus pedidos')}<div class="pad">
  ${S.orders.length ? S.orders.map(o => {
    const sg = stageOf(o);
    return `<button class="olist" data-a="order" data-id="${o.id}">
      <span class="ol-num">${o.num}</span>
      <span class="ol-main"><b>${esc(o.items.map(l => (l.qty > 1 ? l.qty + 'x ' : '') + l.title).join(', '))}</b><span>${fmtDate(o.at)} · ${o.money > 0 ? brl(o.money) : pts(o.spend) + ' brasas'}</span><em class="${sg < 3 ? 'live' : ''}">${STAGES[sg].t}</em></span>
      <span class="chev">${I.right}</span></button>`;
  }).join('') : `<div class="emptybox">${I.list}<p>Você ainda não fez pedidos.</p><button class="btn primary" data-a="toMenu">Fazer o primeiro</button></div>`}
</div>`;

SCREENS.stores = () => {
  const list = [...STORES];
  if (S.geo) list.sort((a, b) => distKm(a) - distKm(b));
  return `${topbar('Restaurantes')}<div class="pad">
    <button class="btn ghost block" data-a="geo">${I.pin} ${S.geo ? 'Atualizar minha localização' : 'Usar minha localização'}</button>
    ${list.map(st => {
      const k = distKm(st), open = isOpen(st);
      return `<button class="storecard pick ${st.id === S.storeId ? 'sel' : ''}" data-a="pickStore" data-id="${st.id}">
        <div class="sc-main"><div class="sc-name">${esc(st.name)}</div>
          <div class="sc-row">${I.pin}<span>${esc(st.addr)}<br>${esc(st.area)}</span></div>
          ${k != null ? `<div class="sc-row">${I.walk}<span>${kmText(k)}</span></div>` : ''}
          <div class="sc-row">${I.clock}<span><b class="${open ? 'ok' : 'bad'}">${open ? 'Aberto' : 'Fechado'}</b>&nbsp;&nbsp;${hoursText(st)}</span></div></div>
        <span class="radio-l">${st.id === S.storeId ? I.check : ''}</span>
      </button>`;
    }).join('')}
  </div>`;
};

SCREENS.moves = () => `${topbar('Extrato de brasas')}<div class="pad">
  <div class="balance solo"><div class="bal-n">${pts(S.points)} <small>brasas</small></div></div>
  ${S.moves.length ? S.moves.map(m => `<div class="move"><div><b>${esc(m.desc)}</b><span>${fmtDate(m.at)}</span></div><em class="${m.n >= 0 ? 'ok' : 'neg'}">${m.n >= 0 ? '+' : '−'}${pts(Math.abs(m.n))}</em></div>`).join('') : '<p class="empty">Nenhuma movimentação ainda.</p>'}
</div>`;

SCREENS.missions = () => `${topbar('Missões')}<div class="pad">
  <p class="lead">Cumpra as missões e resgate brasas extras.</p>
  ${MISSIONS.map(m => {
    const s = missionState(m);
    return `<div class="mission ${s.claimed ? 'claimed' : ''}">
      <span class="ms-ic">${I[m.icon] || I.rocket}</span>
      <div class="ms-main"><b>${esc(m.title)}</b><span>${esc(m.desc)}</span>
        <div class="bar"><i style="width:${(s.cur / s.max) * 100}%"></i></div><small>${s.cur}/${s.max} · ${I.flameF}+${pts(m.reward)} brasas</small></div>
      ${s.claimed ? `<span class="done">${I.check}</span>` : s.done ? `<button class="btn primary sm" data-a="claim" data-id="${m.id}">Resgatar</button>` : ''}
    </div>`;
  }).join('')}
</div>`;

SCREENS.profile = () => `${topbar('Meu perfil')}<div class="pad form">
  <label class="field"><span>Nome</span><input id="pfName" value="${esc(S.name)}" maxlength="30" autocomplete="given-name"></label>
  <label class="field"><span>Celular</span><input id="pfPhone" value="${esc(S.phone)}" inputmode="tel" placeholder="(11) 90000-0000" autocomplete="tel"></label>
  <label class="field"><span>E-mail (opcional)</span><input id="pfEmail" value="${esc(S.email)}" inputmode="email" autocomplete="email"></label>
  <label class="field"><span>Aniversário (opcional)</span><input id="pfBirth" type="date" value="${esc(S.birth)}"></label>
  <button class="btn primary block" data-a="saveProfile">Salvar</button>
</div>`;

SCREENS.faq = () => `${topbar('Perguntas frequentes')}<div class="pad">
  ${FAQ.map(([q, a]) => `<details class="faq"><summary>${esc(q)}${I.down}</summary><p>${esc(a)}</p></details>`).join('')}
</div>`;

SCREENS.contact = () => `${topbar('Fale conosco')}<div class="pad chat">
  <div class="msg them">Oi${S.name ? ', ' + esc(S.name) : ''}! Como podemos ajudar? Conte o que aconteceu e, se for sobre um pedido, diga o número dele.</div>
  ${S.messages.map(m => `<div class="msg ${m.me ? 'me' : 'them'}">${esc(m.t)}<small>${fmtDate(m.at)}</small></div>`).join('')}
</div>
<div class="buybar chatbar"><input id="chatIn" placeholder="Escreva sua mensagem" maxlength="500"><button class="btn primary" data-a="sendMsg">Enviar</button></div>`;

/* ---------- desenhar ---------- */
function render(resetScroll) {
  const c = cur();
  const view = $('#view');
  view.className = 'view s-' + c.name;
  view.innerHTML = SCREENS[c.name](c.params || {});
  const isTab = TABS.includes(c.name);
  $('#nav').hidden = !isTab;
  document.body.classList.toggle('has-nav', isTab);
  $('#nav').innerHTML = navHTML(c.name);
  const showBag = isTab && S.bag.length > 0;
  $('#bagbar').hidden = !showBag;
  document.body.classList.toggle('has-bag', showBag);
  if (showBag) $('#bagbar').innerHTML = `<button data-a="bag"><i class="bb-n">${bagCount()}</i><span>Ver sacola</span><b>${bagMoney() > 0 ? brl(bagMoney()) : pts(bagPts()) + ' brasas'}</b></button>`;
  window.scrollTo(0, resetScroll ? 0 : (c.scroll || 0));
  setupCarousels();
  if (c.name === 'home') setupCatSpy();
  if (c.name === 'club') setupClubSpy();
  if (c.name === 'contact') window.scrollTo(0, document.body.scrollHeight);
  lastStages = JSON.stringify(S.orders.map(stageOf));
}
function navHTML(active) {
  const cm = claimableCount();
  const b = (id, ic, icF, label, badge = '') => `<button class="${active === id ? 'on' : ''}" data-a="tab" data-id="${id}">${badge}<span class="ni">${active === id ? icF : ic}</span><span>${label}</span></button>`;
  return b('home', I.home, I.homeF, 'Início') + b('coupons', I.tag, I.tagF, 'Cupons') +
    b('club', `<span class="ring">${I.flame}</span>`, `<span class="ring on">${I.flameF}</span>`, 'Clube') +
    b('account', I.user, I.userF, 'Minha conta', cm ? '<i class="newb navb">Novo</i>' : '');
}

/* ---------- carrosséis ---------- */
let carTimers = [];
function setupCarousels() {
  carTimers.forEach(clearInterval); carTimers = [];
  document.querySelectorAll('.carousel').forEach(car => {
    const tr = car.querySelector('.car-track'), dots = [...car.querySelectorAll('.dots i')];
    let touching = false, idle = 0;
    const idx = () => Math.round(tr.scrollLeft / tr.clientWidth);
    tr.addEventListener('scroll', () => { const i = idx(); dots.forEach((d, j) => d.classList.toggle('on', i === j)); idle = 0; }, { passive: true });
    tr.addEventListener('touchstart', () => { touching = true; }, { passive: true });
    tr.addEventListener('touchend', () => { touching = false; }, { passive: true });
    carTimers.push(setInterval(() => {
      if (touching || sheetOpen || !document.body.contains(tr)) return;
      if (++idle < 2) return;
      const n = (idx() + 1) % dots.length;
      tr.scrollTo({ left: n * tr.clientWidth, behavior: 'smooth' });
      idle = 0;
    }, 2500));
  });
}

/* ---------- abas do cardápio e do clube acompanham a rolagem ---------- */
let spyHandler = null;
function setSpy(fn) { if (spyHandler) window.removeEventListener('scroll', spyHandler); spyHandler = fn; if (fn) window.addEventListener('scroll', fn, { passive: true }); }
function setupCatSpy() {
  setSpy(() => {
    const secs = [...document.querySelectorAll('.catsec')];
    if (!secs.length) return;
    let on = secs[0].id;
    for (const s of secs) if (s.getBoundingClientRect().top < 140) on = s.id;
    document.querySelectorAll('#cats .chip').forEach(ch => {
      const is = 'cat-' + ch.dataset.id === on;
      if (is && !ch.classList.contains('on')) ch.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
      ch.classList.toggle('on', is);
    });
  });
}
function setupClubSpy() {
  setSpy(() => {
    const secs = [...document.querySelectorAll('.rsec')];
    let on = secs[0]?.id;
    for (const s of secs) if (s.getBoundingClientRect().top < 160) on = s.id;
    document.querySelectorAll('#clubTabs button').forEach(b => {
      const is = 'rs-' + b.dataset.id === on;
      if (is && !b.classList.contains('on')) b.scrollIntoView({ inline: 'center', block: 'nearest', behavior: 'smooth' });
      b.classList.toggle('on', is);
    });
  });
}
function scrollToSection(el, offset) {
  if (!el) return;
  window.scrollTo({ top: el.getBoundingClientRect().top + window.scrollY - offset, behavior: 'smooth' });
}

/* ---------- ações (toques) ---------- */
const A = {};
A.tab = d => setTab(d.id);
A.back = () => back();
A.stores = () => go('stores');
A.order = d => go('order', { id: d.id });
A.offers = d => go('offers', { id: d.id });
A.product = d => { draft = null; go('product', { id: d.id }); };
A.bag = () => go('bag');
A.moves = () => go('moves');
A.missions = () => go('missions');
A.profile = () => go('profile');
A.orders = () => go('orders');
A.faq = () => go('faq');
A.contact = () => go('contact');
A.toMenu = () => {
  stack = [{ name: 'home', params: {} }];
  const extra = history.state && history.state.n ? 0 : 0;
  render(true);
  history.pushState({ n: 1 }, '');
  setTimeout(() => scrollToSection($('#cardapio'), 8), 60);
};
A.cat = d => scrollToSection($('#cat-' + d.id), 64);
A.clubTab = d => scrollToSection($('#rs-' + d.id), 60);

A.welcomeGo = () => {
  S.name = ($('#wlName').value || '').trim();
  S.onboarded = true;
  addPoints(2000, 'Bônus de boas-vindas');
  save();
  stack = [{ name: 'home', params: {} }];
  render(true);
  toast('Você ganhou 2.000 brasas de boas-vindas!');
};

A.pickStore = d => { S.storeId = d.id; save(); back(); toast('Restaurante alterado'); };
A.geo = () => {
  if (!navigator.geolocation) { toast('Seu celular não permite localização aqui'); return; }
  toast('Buscando sua localização…');
  navigator.geolocation.getCurrentPosition(p => {
    S.geo = { lat: p.coords.latitude, lng: p.coords.longitude }; save(); render(false); toast('Localização atualizada');
  }, () => toast('Não foi possível pegar a localização'), { enableHighAccuracy: false, timeout: 10000 });
};

A.coupon = d => {
  const c = byId(COUPONS, d.id);
  openSheet(`<div class="sheet-coupon">${couponCard(c, true)}
    <h2>${esc(c.title)}</h2>
    <p>${c.items.map(it => esc(itemName(byId(PRODUCTS, it.pid), it.opts))).join(' + ')}</p>
    <p class="muted">${esc(c.rule)}</p>
    <button class="btn primary block" data-a="takeCoupon" data-id="${c.id}">Pegar cupom · ${brl(c.price)}</button></div>`);
};
A.couponInfo = d => {
  const c = byId(COUPONS, d.id);
  openSheet(`<h2>Regras da oferta</h2><p><b>${esc(c.title)}</b> por ${brl(c.price)}${c.from ? ` (de ${brl(c.from)})` : ''}.</p><p>${esc(c.rule)}</p><p>Ganhe +${pts(c.bonus)} brasas extras ao usar este cupom.</p><button class="btn ghost block" data-a="closeSheet">Entendi</button>`);
};
A.takeCoupon = d => {
  const c = byId(COUPONS, d.id);
  const ok = addCouponToBag(c);
  closeSheet(() => {
    render(false);
    if (ok) toast('Cupom adicionado à sacola', { a: 'bag', label: 'Ver sacola' });
  });
};
A.closeSheet = () => closeSheet();

A.reward = d => {
  const r = byId(REWARDS, d.id);
  const p = r.pid ? byId(PRODUCTS, r.pid) : null;
  const title = r.title || p.name;
  const can = S.points - bagPts() >= r.cost;
  openSheet(`<div class="sheet-reward">
    <div class="sr-art">${artOf(r.art)}</div>
    <h2>${esc(title)}</h2>
    <p class="muted">${esc(r.desc || p.desc)}</p>
    <div class="sr-cost">${I.flameF}<b>${pts(r.cost)}</b> brasas ${r.from ? `<s>${pts(r.from)}</s>` : ''}</div>
    <p class="small">Você tem ${pts(S.points)} brasas${bagPts() ? ` (${pts(bagPts())} já reservadas na sacola)` : ''}.</p>
    <button class="btn primary block" data-a="redeem" data-id="${r.id}" ${can ? '' : 'disabled'}>${can ? (r.kind ? 'Trocar agora' : 'Adicionar à sacola') : `Faltam ${pts(r.cost - (S.points - bagPts()))} brasas`}</button>
  </div>`);
};
A.redeem = d => {
  const r = byId(REWARDS, d.id);
  if (r.kind) {
    if (S.points - bagPts() < r.cost) return;
    addPoints(-r.cost, r.title);
    S.stats.rewards += 1;
    let msg;
    if (r.kind === 'raffle') { const n = String(rnd(10000)).padStart(4, '0'); S.raffle.push(n); msg = `Boa sorte! Seu número é ${n}`; }
    if (r.kind === 'double') { S.doubleNext = true; msg = 'Pronto! Seu próximo pedido vale brasas em dobro'; }
    save();
    closeSheet(() => { render(false); toast(msg); });
    return;
  }
  const ok = addRewardToBag(r);
  closeSheet(() => { render(false); if (ok) toast('Prêmio adicionado à sacola', { a: 'bag', label: 'Ver sacola' }); });
};

// montar produto
function refreshProduct() {
  const y = window.scrollY;
  const p = byId(PRODUCTS, draft.pid);
  $('#view').innerHTML = productHTML(p);
  window.scrollTo(0, y);
}
A.opt = d => {
  const o = draft.opts;
  if (d.t === 'radio') o[d.k] = d.v;
  else { const arr = o[d.k] || (o[d.k] = []); const i = arr.indexOf(d.v); i >= 0 ? arr.splice(i, 1) : arr.push(d.v); }
  refreshProduct();
};
A.ingr = d => { const r = draft.opts.remove; const i = r.indexOf(d.v); i >= 0 ? r.splice(i, 1) : r.push(d.v); refreshProduct(); };
A.dqty = d => { draft.qty = Math.max(1, Math.min(20, draft.qty + Number(d.d))); refreshProduct(); };
A.addDraft = () => {
  const p = byId(PRODUCTS, draft.pid);
  const editing = draft.edit;
  addProductToBag(p, draft.opts, draft.qty, editing);
  draft = null;
  back();
  setTimeout(() => toast(editing ? 'Item atualizado' : `${p.name} na sacola`, editing ? null : { a: 'bag', label: 'Ver sacola' }), 50);
};

// sacola
A.lqty = d => {
  const l = S.bag.find(x => x.key === d.key);
  if (!l) return;
  l.qty += Number(d.d);
  if (l.qty <= 0) S.bag = S.bag.filter(x => x !== l);
  else l.qty = Math.min(20, l.qty);
  save(); render(false);
};
A.rmLine = d => { S.bag = S.bag.filter(x => x.key !== d.key); save(); render(false); };
A.editLine = d => { const l = S.bag.find(x => x.key === d.key); draft = null; go('product', { id: l.pid, edit: l.key }); };
A.mode = d => { checkout.mode = d.id; render(false); };
A.pay = d => { checkout.pay = d.id; render(false); };
A.placeOrder = () => {
  const st = store();
  if (!isOpen(st)) { toast(`${st.name.replace('MARTINS BURGER · ', '')} está fechado agora. ${hoursText(st)}.`); return; }
  if (checkout.mode === 'mesa') {
    checkout.table = ($('#tableNum')?.value || '').trim();
    if (!checkout.table) { toast('Informe o número da mesa'); $('#tableNum')?.focus(); return; }
  }
  if (S.points < bagPts()) { toast('Você não tem brasas suficientes para os prêmios da sacola'); return; }
  const money = bagMoney();
  const finish = () => {
    const o = placeOrder({ mode: checkout.mode, table: checkout.table, pay: money > 0 ? checkout.pay : null });
    orderUI[o.id] = { tl: false, items: true };
    stack = [{ name: 'home', params: {} }, { name: 'order', params: { id: o.id } }];
    render(true);
    if (o.earned) setTimeout(() => toast(`Pedido feito! +${pts(o.earned)} brasas`), 300);
  };
  if (money > 0 && checkout.pay !== 'local') {
    openSheet(`<div class="paying"><div class="spinner"></div><h2>Processando pagamento…</h2><p class="muted">${checkout.pay === 'pix' ? 'Pix' : 'Cartão'} · ${brl(money)}</p></div>`, 'locked');
    setTimeout(() => {
      $('#sheet .sheet-body').innerHTML = `<div class="paying"><div class="okmark">${I.check}</div><h2>Pagamento aprovado</h2></div>`;
      setTimeout(() => closeSheet(finish), 700);
    }, 1400);
  } else finish();
};

// pedido
A.toggleTl = d => { const u = orderUI[d.id]; u.tl = !u.tl; render(false); };
A.toggleItems = d => { const u = orderUI[d.id]; u.items = !u.items; render(false); };
A.copyOrder = d => copyText(d.id, 'Número do pedido copiado');
A.pickedUp = d => { const o = S.orders.find(x => x.id === d.id); o.done = true; save(); render(false); toast('Bom apetite! 🍔'); };
A.reorder = d => {
  const o = S.orders.find(x => x.id === d.id);
  let skipped = 0;
  for (const l of o.items) {
    if (l.type === 'product') addProductToBag(byId(PRODUCTS, l.pid), l.opts, l.qty);
    else if (l.type === 'coupon') { if (!addCouponToBag(byId(COUPONS, l.cid))) skipped++; }
    else skipped++;
  }
  save();
  go('bag');
  if (skipped) setTimeout(() => toast('Prêmios trocados com brasas não foram repetidos'), 100);
};

// conta
A.copyId = () => copyText(S.id, 'ID copiado');
A.qrBig = () => openSheet(`<div class="qrfull"><h2>Seu QR code</h2><div class="qrbig">${qrSvg('MARTINSBURGER:' + S.id)}</div><p>ID <b>${esc(S.id)}</b></p><p class="muted">Aumente o brilho da tela para o caixa ler mais fácil.</p><button class="btn ghost block" data-a="closeSheet">Fechar</button></div>`);
A.saveProfile = () => {
  S.name = $('#pfName').value.trim(); S.phone = $('#pfPhone').value.trim();
  S.email = $('#pfEmail').value.trim(); S.birth = $('#pfBirth').value;
  save(); back(); setTimeout(() => toast(claimableCount() ? 'Perfil salvo. Você tem missão para resgatar!' : 'Perfil salvo'), 50);
};
A.claim = d => {
  const m = byId(MISSIONS, d.id);
  if (S.claimed[m.id] || !missionState(m).done) return;
  S.claimed[m.id] = true;
  addPoints(m.reward, `Missão: ${m.title}`);
  save(); render(false); toast(`+${pts(m.reward)} brasas!`);
};
A.wipe = () => openSheet(`<h2>Apagar meus dados?</h2><p>Isso apaga seu nome, brasas, pedidos e a sacola deste celular. Não dá para desfazer.</p>
  <button class="btn danger block" data-a="wipeYes">Apagar tudo</button><button class="btn ghost block" data-a="closeSheet">Cancelar</button>`);
A.wipeYes = () => { try { localStorage.removeItem(KEY); } catch { } S = freshState(); closeSheet(() => { stack = [{ name: 'welcome', params: {} }]; render(true); }); };
A.sendMsg = () => {
  const inp = $('#chatIn'), t = inp.value.trim();
  if (!t) return;
  S.messages.push({ me: true, t, at: Date.now() });
  save(); render(false);
  setTimeout(() => {
    S.messages.push({ me: false, t: 'Recebemos sua mensagem! Nossa equipe responde em até 1 dia útil por aqui. 🧡', at: Date.now() });
    save(); if (cur().name === 'contact') render(false);
  }, 1500);
};

document.addEventListener('click', e => {
  const el = e.target.closest('[data-a]');
  if (!el || el.disabled) return;
  if (el.closest('.sheet-wrap') && !sheetOpen) return;
  const fn = A[el.dataset.a];
  if (fn) { e.preventDefault(); e.stopPropagation(); fn({ ...el.dataset }, el, e); }
});
$('#sheet').addEventListener('click', e => { if (e.target.id === 'sheet' && !$('#sheet').classList.contains('locked')) closeSheet(); });
document.addEventListener('keydown', e => {
  if (e.key === 'Enter' && e.target.id === 'chatIn') A.sendMsg();
  if (e.key === 'Enter' && e.target.id === 'wlName') A.welcomeGo();
});
document.addEventListener('input', e => {
  if (e.target.id === 'tableNum') checkout.table = e.target.value;
  if (e.target.id === 'cpSearch') {
    const q = e.target.value.trim().toLowerCase();
    const norm = s => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
    const res = $('#cpResults'), main = $('#cpMain');
    if (!q) { res.hidden = true; main.hidden = false; return; }
    const found = COUPONS.filter(c => norm(c.title + ' ' + c.items.map(it => byId(PRODUCTS, it.pid).name).join(' ')).includes(norm(q)));
    res.innerHTML = found.length ? `<div class="list-cp">${found.map(c => couponCard(c, true)).join('')}</div>` : '<p class="empty">Nenhum cupom encontrado.</p>';
    res.hidden = false; main.hidden = true;
  }
});

/* ---------- andamento dos pedidos ---------- */
let lastStages = '';
setInterval(() => {
  const now = JSON.stringify(S.orders.map(stageOf));
  if (now === lastStages) return;
  const before = lastStages ? JSON.parse(lastStages) : [];
  S.orders.forEach((o, i) => {
    if (stageOf(o) === 2 && before[i] !== 2 && before[i] !== undefined) {
      toast(`Pedido ${o.num} pronto! Pode retirar 🎉`, { a: 'order', label: 'Ver', data: `data-id="${o.id}"` });
      try { navigator.vibrate && navigator.vibrate([200, 100, 200]); } catch { }
    }
  });
  lastStages = now;
  const n = cur().name;
  if (['home', 'order', 'orders'].includes(n) && !sheetOpen && document.activeElement?.tagName !== 'INPUT') render(false);
}, 2000);

/* ---------- início ---------- */
history.replaceState({ n: 1 }, '');
render(true);
if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => { });
