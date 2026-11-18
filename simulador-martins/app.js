// Simulador Martins — Monte Carlo com dados reais da B3.
// Ideia: sorteia meses reais do passado (o mesmo mês para todos os ativos) para montar
// milhares de futuros possíveis e mostra a distribuição do resultado.
'use strict';

const D = window.DADOS;
const $ = (id) => document.getElementById(id);
const CHAVE = 'simulador-martins-v1';
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const TIPO_NOME = { acao: 'Ação', fii: 'FII', etf: 'ETF', indice: 'Índice' };

const PRESETS = [
  { nome: 'Exemplo equilibrado', c: { BOVA11: 25, ITUB4: 10, TAEE11: 10, WEGE3: 10, HGLG11: 15, KNRI11: 10, MXRF11: 10, KNCR11: 10 } },
  { nome: 'Só Ibovespa', c: { BOVA11: 100 } },
  { nome: 'Dividendos', c: { ITUB4: 20, BBAS3: 20, TAEE11: 20, BBSE3: 20, VIVT3: 20 } },
  { nome: 'FIIs variados', c: { HGLG11: 20, KNRI11: 20, MXRF11: 20, KNCR11: 20, BTLG11: 20 } },
  { nome: 'FIIs de papel', c: { MXRF11: 20, KNCR11: 20, KNIP11: 20, CPTS11: 20, RBRR11: 20 } },
  { nome: 'Brasil + S&P 500', c: { BOVA11: 50, IVVB11: 50 } },
];

const PADRAO = {
  carteira: Object.entries(PRESETS[0].c).map(([t, p]) => ({ t, p })),
  inicial: 10000, aporte: 1000, anos: 20, meta: 1000000,
  n: 5000, desde: 2010, bloco: 1, real: true, corrige: true,
};

// ---------------------------------------------------------------- utilidades
const idx = (s) => { const [y, m] = s.split('-').map(Number); return y * 12 + m - 1; };
const rotulo = (i) => `${MESES[i % 12]}/${Math.floor(i / 12)}`;
const fmtBRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
const fmtCompacto = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1 });
const brl = (v) => fmtBRL.format(Math.round(v));
const curto = (v) => Math.abs(v) < 10000 ? brl(v) : fmtCompacto.format(v);
const pct = (x, casas = 1) => (x * 100).toLocaleString('pt-BR', { minimumFractionDigits: casas, maximumFractionDigits: casas }) + '%';
const semAcento = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

const ATIVOS = {};
for (const a of D.ativos) ATIVOS[a.t] = a;

function valor(t, mi) {
  const s = D.series[t];
  const v = s.r[mi - idx(s.inicio)];
  return v === undefined ? null : v;
}

// Estatística de cada ativo no histórico inteiro dele (para a lista de escolha)
const ESTAT = {};
for (const a of D.ativos) {
  const r = D.series[a.t].r.filter((x) => x !== null);
  ESTAT[a.t] = { ...estatisticas(r), desde: D.series[a.t].inicio.slice(0, 4) };
}
function estatisticas(r) {
  let g = 0, s = 0, s2 = 0, pior = 0;
  for (const x of r) { g += Math.log1p(x); s += x; s2 += x * x; if (x < pior) pior = x; }
  const n = r.length, m = s / n;
  return { anual: Math.exp(g / n * 12) - 1, vol: Math.sqrt(Math.max(0, (s2 - n * m * m) / (n - 1)) * 12), pior, n };
}

// ---------------------------------------------------------------- estado
let S = carregar();
function carregar() {
  try {
    const x = JSON.parse(localStorage.getItem(CHAVE));
    if (x && Array.isArray(x.carteira)) {
      x.carteira = x.carteira.filter((c) => ATIVOS[c.t]);
      return { ...PADRAO, ...x };
    }
  } catch (e) { /* sem armazenamento: usa o padrão */ }
  return JSON.parse(JSON.stringify(PADRAO));
}
function salvar() {
  try { localStorage.setItem(CHAVE, JSON.stringify(S)); } catch (e) { /* ignora */ }
}

// ---------------------------------------------------------------- preparação dos dados
// Monta a lista de meses em que TODOS os ativos (e CDI/IPCA) têm dado, a partir de "desde".
function preparar(carteira, desde) {
  const ts = carteira.map((c) => c.t);
  const todos = [...ts, 'CDI', 'IPCA'];
  let ini = idx(`${desde}-01`), fim = Infinity, limitante = null, iniProprio = -Infinity;
  for (const t of todos) {
    const s = D.series[t], i0 = idx(s.inicio);
    if (i0 > iniProprio) { iniProprio = i0; limitante = t; }
    ini = Math.max(ini, i0);
    fim = Math.min(fim, i0 + s.r.length - 1);
  }
  const soma = carteira.reduce((a, c) => a + c.p, 0);
  const w = carteira.map((c) => c.p / soma);
  const meses = [], port = [], cdi = [], ipca = [], porAtivo = ts.map(() => []);
  for (let mi = ini; mi <= fim; mi++) {
    const vs = todos.map((t) => valor(t, mi));
    if (vs.some((v) => v === null)) continue;
    let p = 0;
    for (let j = 0; j < ts.length; j++) { p += w[j] * vs[j]; porAtivo[j].push(vs[j]); }
    meses.push(mi); port.push(p); cdi.push(vs[ts.length]); ipca.push(vs[ts.length + 1]);
  }
  return {
    meses, port: Float64Array.from(port), cdi: Float64Array.from(cdi), ipca: Float64Array.from(ipca),
    porAtivo, pesos: w, ini: meses[0], fim: meses[meses.length - 1],
    limitante: iniProprio > idx(`${desde}-01`) ? limitante : null,
  };
}

// ---------------------------------------------------------------- Monte Carlo
function simular(H, o) {
  const L = H.port.length, N = o.anos * 12, nSim = o.n;
  // guarda no máximo ~121 pontos por futuro (para o gráfico)
  const passo = Math.max(1, Math.ceil(N / 120));
  const pontos = [];
  for (let m = 0; m <= N; m += passo) pontos.push(m);
  if (pontos[pontos.length - 1] !== N) pontos.push(N);
  const P = pontos.length;
  const caminhos = new Float32Array(nSim * P);
  const somaCdi = new Float64Array(P), somaInv = new Float64Array(P);
  const finais = new Float64Array(nSim), finaisCdi = new Float64Array(nSim), investidos = new Float64Array(nSim);
  const anuais = new Float64Array(nSim);

  for (let s = 0; s < nSim; s++) {
    let v = o.inicial, vc = o.inicial, inf = 1, inv = o.inicial, invReal = o.inicial, cresce = 0;
    let pos = 0, resta = 0, k = 1;
    const base = s * P;
    caminhos[base] = v; somaCdi[0] += vc; somaInv[0] += inv;
    for (let m = 1; m <= N; m++) {
      if (resta === 0) { pos = (Math.random() * L) | 0; resta = o.bloco; }
      else pos = (pos + 1) % L;
      resta--;
      inf *= 1 + H.ipca[pos];
      const ap = o.corrige ? o.aporte * inf : o.aporte;
      v = v * (1 + H.port[pos]) + ap;
      vc = vc * (1 + H.cdi[pos]) + ap;
      inv += ap; invReal += ap / inf;
      cresce += Math.log1p(H.port[pos]) - (o.real ? Math.log1p(H.ipca[pos]) : 0);
      if (m === pontos[k]) {
        const d = o.real ? inf : 1;
        caminhos[base + k] = v / d;
        somaCdi[k] += vc / d;
        somaInv[k] += o.real ? invReal : inv;
        k++;
      }
    }
    const d = o.real ? inf : 1;
    finais[s] = v / d; finaisCdi[s] = vc / d; investidos[s] = o.real ? invReal : inv;
    anuais[s] = Math.exp(cresce / N * 12) - 1;
  }

  // percentis por ponto do tempo
  const QS = [0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95];
  const faixas = QS.map(() => new Float64Array(P));
  const col = new Float32Array(nSim);
  for (let k = 0; k < P; k++) {
    for (let s = 0; s < nSim; s++) col[s] = caminhos[s * P + k];
    col.sort();
    QS.forEach((q, i) => { faixas[i][k] = quantil(col, q); });
  }
  const ord = Float64Array.from(finais).sort();
  const ordAnual = Float64Array.from(anuais).sort();
  let perde = 0, bateCdi = 0, meta = 0;
  for (let s = 0; s < nSim; s++) {
    if (finais[s] < investidos[s]) perde++;
    if (finais[s] > finaisCdi[s]) bateCdi++;
    if (o.meta > 0 && finais[s] >= o.meta) meta++;
  }
  return {
    N, P, pontos, caminhos, nSim, faixas, QS,
    cdi: Array.from(somaCdi, (x) => x / nSim), inv: Array.from(somaInv, (x) => x / nSim),
    ord, ordAnual, finais,
    pPerde: perde / nSim, pCdi: bateCdi / nSim, pMeta: o.meta > 0 ? meta / nSim : null,
    q: (p) => quantil(ord, p), qa: (p) => quantil(ordAnual, p),
  };
}
function quantil(arr, q) {
  const x = q * (arr.length - 1), i = Math.floor(x), f = x - i;
  return i + 1 < arr.length ? arr[i] * (1 - f) + arr[i + 1] * f : arr[i];
}

// ---------------------------------------------------------------- carteira (tela)
function desenharCarteira() {
  const h = $('holdings');
  h.innerHTML = S.carteira.map((c, i) => {
    const a = ATIVOS[c.t];
    return `<div class="hold">
      <div class="who"><div class="tk">${esc(a.t)} <span class="tag ${a.tipo}">${TIPO_NOME[a.tipo]}</span></div>
      <div class="nm">${esc(a.n)} · ${esc(a.setor)}</div></div>
      <label class="pct"><input type="number" inputmode="decimal" min="0" max="100" step="1" id="w-${esc(a.t)}" data-i="${i}" value="${+c.p.toFixed(2)}" aria-label="Peso de ${esc(a.t)} em %"><span>%</span></label>
      <button class="rm" type="button" data-rm="${i}" aria-label="Tirar ${esc(a.t)}"><svg class="ic" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg></button>
    </div>`;
  }).join('') || '<p class="lead">Nenhum ativo ainda. Escolha um exemplo acima ou adicione ativos.</p>';
  $('cart-count').textContent = S.carteira.length ? `${S.carteira.length} ${S.carteira.length === 1 ? 'ativo' : 'ativos'}` : '';
  desenharSoma();
  desenharPresets();
}
function desenharSoma() {
  const soma = S.carteira.reduce((a, c) => a + c.p, 0);
  const ok = Math.abs(soma - 100) < 0.5;
  $('sum').innerHTML = !S.carteira.length ? '' : ok
    ? `<span class="ok">Total: 100%</span>`
    : `<span class="bad">Total: ${soma.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</span>${soma > 0 ? '<button class="link" type="button" id="btn-fix">Ajustar para 100%</button>' : ''}`;
}
function desenharPresets() {
  const atual = JSON.stringify(Object.fromEntries(S.carteira.map((c) => [c.t, c.p])));
  $('presets').innerHTML = PRESETS.map((p, i) =>
    `<button type="button" class="chip${JSON.stringify(p.c) === atual ? ' on' : ''}" data-preset="${i}">${esc(p.nome)}</button>`).join('');
}

$('presets').addEventListener('click', (e) => {
  const b = e.target.closest('[data-preset]'); if (!b) return;
  S.carteira = Object.entries(PRESETS[+b.dataset.preset].c).map(([t, p]) => ({ t, p }));
  salvar(); desenharCarteira();
});
$('holdings').addEventListener('input', (e) => {
  const i = e.target.dataset.i; if (i === undefined) return;
  const v = parseFloat(String(e.target.value).replace(',', '.'));
  S.carteira[+i].p = isFinite(v) && v > 0 ? Math.min(v, 100) : 0;
  salvar(); desenharSoma(); desenharPresets();
});
$('holdings').addEventListener('click', (e) => {
  const b = e.target.closest('[data-rm]'); if (!b) return;
  S.carteira.splice(+b.dataset.rm, 1); salvar(); desenharCarteira();
});
$('sum').addEventListener('click', (e) => {
  if (e.target.id !== 'btn-fix') return;
  const soma = S.carteira.reduce((a, c) => a + c.p, 0);
  S.carteira.forEach((c) => { c.p = Math.round(c.p / soma * 1000) / 10; });
  arredondaPara100(); salvar(); desenharCarteira();
});
$('btn-equal').addEventListener('click', () => {
  if (!S.carteira.length) return;
  const p = Math.floor(1000 / S.carteira.length) / 10;
  S.carteira.forEach((c) => { c.p = p; });
  arredondaPara100(); salvar(); desenharCarteira();
});
function arredondaPara100() {
  const soma = S.carteira.reduce((a, c) => a + c.p, 0);
  if (S.carteira.length) S.carteira[0].p = Math.round((S.carteira[0].p + 100 - soma) * 10) / 10;
}

// ---------------------------------------------------------------- escolher ativos
let filtroTipo = '';
$('btn-add').addEventListener('click', abrirLista);
$('sheet-ok').addEventListener('click', fecharLista);
$('sheet').addEventListener('click', (e) => { if (e.target.id === 'sheet') fecharLista(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('sheet').hidden) fecharLista(); });
$('q').addEventListener('input', desenharLista);
$('seg-tipo').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  filtroTipo = b.dataset.v;
  [...$('seg-tipo').children].forEach((x) => x.classList.toggle('on', x === b));
  desenharLista();
});
$('list').addEventListener('click', (e) => {
  const b = e.target.closest('[data-t]'); if (!b) return;
  const t = b.dataset.t, i = S.carteira.findIndex((c) => c.t === t);
  if (i >= 0) S.carteira.splice(i, 1);
  else S.carteira.push({ t, p: S.carteira.length ? 10 : 100 });
  salvar(); desenharLista(); desenharCarteira();
});
function abrirLista() {
  $('sheet').hidden = false; $('q').value = ''; desenharLista();
  document.body.style.overflow = 'hidden';
}
function fecharLista() { $('sheet').hidden = true; document.body.style.overflow = ''; }
function desenharLista() {
  const q = semAcento($('q').value.trim());
  const sel = new Set(S.carteira.map((c) => c.t));
  const itens = D.ativos.filter((a) => {
    if (filtroTipo && !(a.tipo === filtroTipo || (filtroTipo === 'etf' && a.tipo === 'indice'))) return false;
    return !q || semAcento(`${a.t} ${a.n} ${a.setor}`).includes(q);
  });
  $('list').innerHTML = itens.map((a) => {
    const e = ESTAT[a.t];
    return `<button type="button" class="opt${sel.has(a.t) ? ' sel' : ''}" data-t="${esc(a.t)}" aria-pressed="${sel.has(a.t)}">
      <div class="who"><div class="tk">${esc(a.t)} <span class="tag ${a.tipo}">${TIPO_NOME[a.tipo]}</span>${sel.has(a.t) ? ' <svg class="ic" viewBox="0 0 24 24" style="color:var(--lime);width:16px;height:16px"><path d="M5 12l5 5 9-10"/></svg>' : ''}</div>
      <div class="nm">${esc(a.n)} · ${esc(a.setor)}</div></div>
      <div class="st"><b class="${e.anual < 0 ? 'neg' : ''}">${pct(e.anual)} ao ano</b>oscila ${pct(e.vol, 0)} · desde ${e.desde}</div>
    </button>`;
  }).join('') || '<p class="empty-list">Nenhum ativo encontrado.</p>';
}

// ---------------------------------------------------------------- plano (tela)
function ligarDinheiro(id, chave) {
  const el = $(id);
  const mostrar = () => { el.value = S[chave] ? S[chave].toLocaleString('pt-BR') : ''; };
  mostrar();
  el.addEventListener('input', () => {
    const n = parseInt(el.value.replace(/\D/g, ''), 10) || 0;
    S[chave] = Math.min(n, 1e12); salvar();
    el.value = n ? S[chave].toLocaleString('pt-BR') : '';
  });
}
ligarDinheiro('in-inicial', 'inicial');
ligarDinheiro('in-aporte', 'aporte');
ligarDinheiro('in-meta', 'meta');

const anosEl = $('in-anos');
anosEl.value = S.anos;
const mostraAnos = () => { $('out-anos').textContent = `${S.anos} ${S.anos === 1 ? 'ano' : 'anos'}`; };
mostraAnos();
anosEl.addEventListener('input', () => { S.anos = +anosEl.value; mostraAnos(); salvar(); });

function ligarSeg(id, chave) {
  const seg = $(id);
  const marca = () => [...seg.children].forEach((b) => {
    const on = +b.dataset.v === S[chave];
    b.classList.toggle('on', on); b.setAttribute('aria-pressed', on);
  });
  marca();
  seg.addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    S[chave] = +b.dataset.v; marca(); salvar();
  });
}
ligarSeg('seg-n', 'n');
ligarSeg('seg-desde', 'desde');
ligarSeg('seg-bloco', 'bloco');
for (const [id, chave] of [['in-real', 'real'], ['in-corrige', 'corrige']]) {
  $(id).checked = S[chave];
  $(id).addEventListener('change', () => { S[chave] = $(id).checked; salvar(); });
}

// ---------------------------------------------------------------- rodar
let ultimo = null;
$('btn-sim').addEventListener('click', () => rodar(true));

function rodar(rolar) {
  const erro = (msg) => { $('err').textContent = msg; $('err').hidden = !msg; };
  const cart = S.carteira.filter((c) => c.p > 0);
  if (!cart.length) return erro('Adicione pelo menos um ativo com peso maior que zero.');
  if (S.inicial <= 0 && S.aporte <= 0) return erro('Coloque um valor inicial ou um aporte mensal.');
  const H = preparar(cart, S.desde);
  if (H.meses.length < 24) {
    return erro(`Esses ativos só têm ${H.meses.length} meses de histórico em comum. Escolha ativos mais antigos ou use "Tudo" no histórico.`);
  }
  erro('');
  const btn = $('btn-sim');
  btn.disabled = true; $('btn-sim-label').textContent = 'Simulando…';
  setTimeout(() => {
    const t0 = performance.now();
    const R = simular(H, S);
    ultimo = { H, R, o: { ...S }, cart, ms: performance.now() - t0 };
    mostrarResultado();
    btn.disabled = false; $('btn-sim-label').textContent = 'Simular de novo';
    if (rolar) $('res').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, 30);
}

// ---------------------------------------------------------------- resultado
function mostrarResultado() {
  const { H, R, o, cart } = ultimo;
  $('res').hidden = false;
  const termo = o.real ? 'em dinheiro de hoje' : 'em valores da época';
  const invFinal = R.inv[R.P - 1];
  $('r-k').textContent = `Em ${o.anos} ${o.anos === 1 ? 'ano' : 'anos'}, no futuro do meio (${termo})`;
  $('r-med').textContent = brl(R.q(0.5));
  $('r-faixa').textContent = `8 em cada 10 futuros terminaram entre ${curto(R.q(0.1))} e ${curto(R.q(0.9))}.`;
  $('r-p10').textContent = curto(R.q(0.1));
  $('r-inv').textContent = curto(invFinal);
  $('r-p90').textContent = curto(R.q(0.9));

  const tom = (p, bomSeAlto) => (bomSeAlto ? p >= 0.6 : p <= 0.1) ? 'good' : (bomSeAlto ? p < 0.4 : p > 0.25) ? 'bad' : 'mid';
  const odds = [
    { v: R.pPerde, t: o.real ? 'de terminar com menos do que investiu (já descontada a inflação)' : 'de terminar com menos do que investiu', c: tom(R.pPerde, false) },
    { v: R.pCdi, t: 'de ganhar do CDI com os mesmos aportes', c: tom(R.pCdi, true) },
  ];
  if (R.pMeta !== null) odds.push({ v: R.pMeta, t: `de chegar na meta de ${curto(o.meta)}`, c: tom(R.pMeta, true) });
  $('odds').innerHTML = odds.map((x) => `<div class="odd ${x.c}"><b>${pct(x.v, 0)}</b><span>chance ${esc(x.t)}</span></div>`).join('')
    + `<div class="odd"><b>${curto(R.q(0.5) * 0.005)}<small style="font-size:13px;font-weight:700">/mês</small></b><span>de renda, se a mediana render 0,5% ao mês</span></div>`;

  $('fan-note').textContent = `${R.nSim.toLocaleString('pt-BR')} futuros`;
  desenharLeque();
  desenharHistograma();
  desenharTabelas();
}

function desenharTabelas() {
  const { H, R, o, cart } = ultimo;
  const linhas = [
    ['Muito pessimista', 0.05], ['Pessimista', 0.1], ['Abaixo do meio', 0.25], ['Mediana', 0.5],
    ['Acima do meio', 0.75], ['Otimista', 0.9], ['Muito otimista', 0.95],
  ];
  $('tbl-pct').innerHTML = `<thead><tr><th>Cenário</th><th>Valor final</th><th>Ao ano</th></tr></thead><tbody>` +
    linhas.map(([n, q]) => {
      const a = R.qa(q);
      return `<tr class="${q === 0.5 ? 'med' : ''}"><td>${n}<small class="sub">${q === 0.5 ? 'metade abaixo, metade acima' : `${pct(q, 0)} dos futuros abaixo`}</small></td><td>${brl(R.q(q))}</td><td class="${a < 0 ? 'neg' : 'pos'}">${pct(a)}</td></tr>`;
    }).join('') + '</tbody>';
  $('pct-help').textContent = `"Ao ano" é quanto a carteira rendeu por ano em cada cenário${o.real ? ', acima da inflação' : ''}, sem contar os aportes. Cada coluna foi ordenada separadamente.`;

  $('hist-range').textContent = `${H.meses.length} meses`;
  const linhasAtivos = cart.map((c, j) => ({ t: c.t, p: H.pesos[j], e: estatisticas(H.porAtivo[j]) }));
  const eCart = estatisticas(Array.from(H.port)), eCdi = estatisticas(Array.from(H.cdi)), eIpca = estatisticas(Array.from(H.ipca));
  const linha = (nome, peso, e, cls = '') => `<tr class="${cls}"><td>${nome}</td><td>${peso}</td><td class="${e.anual < 0 ? 'neg' : 'pos'}">${pct(e.anual)}</td><td>${pct(e.vol, 0)}</td><td class="${e.pior < -0.005 ? 'neg' : ''}">${pct(e.pior, 0)}</td></tr>`;
  $('tbl-ativos').innerHTML = `<thead><tr><th>Ativo</th><th>Peso</th><th>Ao ano</th><th>Oscila</th><th>Pior mês</th></tr></thead><tbody>` +
    linhasAtivos.map((x) => linha(esc(x.t), pct(x.p, 0), x.e)).join('') +
    linha('Carteira', '100%', eCart, 'med') + linha('CDI', '—', eCdi) + linha('IPCA', '—', eIpca) + '</tbody>';
  const lim = H.limitante ? ` A janela começa em ${rotulo(H.ini)} porque é quando começa o histórico de ${H.limitante}.` : '';
  $('hist-note').textContent = '';
  ultimo.notaJanela = `Sorteio feito com os meses de ${rotulo(H.ini)} a ${rotulo(H.fim)}.${lim}`;
  $('tbl-ativos').closest('.card').querySelector('.help').textContent =
    `${ultimo.notaJanela} Valores sem descontar a inflação, com proventos reinvestidos (JCP líquido de 15% de IR). "Oscila" é a volatilidade anual: quanto maior, mais larga fica a faixa de futuros.`;
}

// ---------------------------------------------------------------- gráficos (SVG feito à mão)
const NS = 'http://www.w3.org/2000/svg';
function passoBonito(max, n) {
  const bruto = max / n, mag = Math.pow(10, Math.floor(Math.log10(bruto)));
  for (const m of [1, 2, 2.5, 5, 10]) if (bruto <= m * mag) return m * mag;
  return 10 * mag;
}

function desenharLeque() {
  const { R, o } = ultimo;
  const box = $('fan');
  const W = Math.max(300, box.clientWidth || 340), Hh = 250;
  const ml = 58, mr = 12, mt = 12, mb = 26;
  const iw = W - ml - mr, ih = Hh - mt - mb;
  const [p05, p10, p25, p50, p75, p90, p95] = R.faixas;
  let ymax = 0;
  for (let k = 0; k < R.P; k++) ymax = Math.max(ymax, p90[k], R.cdi[k], R.inv[k]);
  ymax *= 1.15;
  const passoY = passoBonito(ymax, 4);
  ymax = Math.ceil(ymax / passoY) * passoY;
  const x = (m) => ml + (m / R.N) * iw;
  const y = (v) => mt + ih - Math.min(Math.max(v / ymax, -0.02), 1.05) * ih;
  const linhaPts = (arr) => arr.map((v, k) => `${x(R.pontos[k]).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const banda = (a, b) => `${linhaPts(Array.from(b))} ${Array.from(a).map((v, k) => `${x(R.pontos[k]).toFixed(1)},${y(v).toFixed(1)}`).reverse().join(' ')}`;

  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Leque de futuros: mediana e faixas de percentis ao longo de ${o.anos} anos">`;
  svg += `<defs><clipPath id="clip"><rect x="${ml}" y="${mt - 4}" width="${iw}" height="${ih + 4}"/></clipPath></defs>`;
  for (let v = 0; v <= ymax + 1e-6; v += passoY) {
    svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--line)" stroke-width="1"/>`;
    svg += `<text x="${ml - 8}" y="${y(v) + 4}" text-anchor="end">${esc(curtoEixo(v))}</text>`;
  }
  const passoAno = o.anos <= 6 ? 1 : o.anos <= 12 ? 2 : o.anos <= 25 ? 5 : 10;
  for (let a = 0; a <= o.anos; a += passoAno) {
    svg += `<text x="${x(a * 12)}" y="${Hh - 6}" text-anchor="middle">${a === 0 ? 'hoje' : a + (a === passoAno && a < 2 ? ' ano' : 'a')}</text>`;
  }
  svg += `<g clip-path="url(#clip)">`;
  // alguns futuros sorteados, para mostrar o que é cada "futuro"
  const nCam = Math.min(30, R.nSim);
  for (let s = 0; s < nCam; s++) {
    const arr = R.caminhos.subarray(s * R.P, (s + 1) * R.P);
    svg += `<polyline points="${linhaPts(Array.from(arr))}" fill="none" stroke="var(--path)" stroke-width="1"/>`;
  }
  svg += `<polygon points="${banda(p10, p90)}" fill="var(--fan-90)"/>`;
  svg += `<polygon points="${banda(p25, p75)}" fill="var(--fan-50)"/>`;
  svg += `<polyline points="${linhaPts(R.inv)}" fill="none" stroke="var(--text-2)" stroke-width="1.5" stroke-dasharray="2 4"/>`;
  svg += `<polyline points="${linhaPts(R.cdi)}" fill="none" stroke="var(--blue)" stroke-width="2" stroke-dasharray="6 4"/>`;
  svg += `<polyline points="${linhaPts(Array.from(p50))}" fill="none" stroke="var(--fan-line)" stroke-width="2.5"/>`;
  svg += `</g>`;
  svg += `<circle cx="${x(R.N)}" cy="${y(p50[R.P - 1])}" r="4" fill="var(--fan-line)"/>`;
  svg += `<g id="cursor" style="display:none"><line id="cur-l" y1="${mt}" y2="${mt + ih}" stroke="var(--text-2)" stroke-width="1"/><circle id="cur-c" r="5" fill="var(--fan-line)" stroke="var(--bg)" stroke-width="2"/></g>`;
  svg += `<rect x="${ml}" y="${mt}" width="${iw}" height="${ih}" fill="transparent" id="hit"/>`;
  svg += `</svg>`;
  box.innerHTML = svg;

  const el = box.querySelector('svg');
  const mover = (ev) => {
    const r = el.getBoundingClientRect();
    const px = (ev.clientX - r.left) / r.width * W;
    const m = Math.max(0, Math.min(R.N, ((px - ml) / iw) * R.N));
    let k = 0, melhor = Infinity;
    R.pontos.forEach((pm, i) => { const d = Math.abs(pm - m); if (d < melhor) { melhor = d; k = i; } });
    const cx = x(R.pontos[k]);
    el.querySelector('#cursor').style.display = '';
    el.querySelector('#cur-l').setAttribute('x1', cx); el.querySelector('#cur-l').setAttribute('x2', cx);
    el.querySelector('#cur-c').setAttribute('cx', cx); el.querySelector('#cur-c').setAttribute('cy', y(p50[k]));
    const mes = R.pontos[k], anos = Math.floor(mes / 12), resto = mes % 12;
    const quando = mes === 0 ? 'Hoje' : `Em ${anos ? `${anos} ${anos === 1 ? 'ano' : 'anos'}` : ''}${anos && resto ? ' e ' : ''}${resto ? `${resto} ${resto === 1 ? 'mês' : 'meses'}` : ''}`;
    $('readout').innerHTML = `<b>${quando}:</b> mediana <b>${brl(p50[k])}</b> · 8 em cada 10 entre ${curto(p10[k])} e ${curto(p90[k])} · investido ${curto(R.inv[k])} · no CDI ${curto(R.cdi[k])}`;
  };
  el.addEventListener('pointerdown', mover);
  el.addEventListener('pointermove', mover);
}
function curtoEixo(v) {
  if (v === 0) return 'R$ 0';
  if (v >= 1e6) return `R$ ${(v / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`;
  if (v >= 1e3) return `R$ ${(v / 1e3).toLocaleString('pt-BR', { maximumFractionDigits: 0 })} mil`;
  return `R$ ${Math.round(v)}`;
}

function desenharHistograma() {
  const { R, o } = ultimo;
  const box = $('hist');
  const W = Math.max(300, box.clientWidth || 340), Hh = 170;
  const ml = 12, mr = 12, mt = 16, mb = 26;
  const iw = W - ml - mr, ih = Hh - mt - mb;
  const lo = R.q(0.01), hi = R.q(0.99), nb = 32;
  const larg = (hi - lo) / nb || 1;
  const cont = new Array(nb).fill(0);
  for (const v of R.finais) {
    if (v < lo || v > hi) continue;
    cont[Math.min(nb - 1, Math.floor((v - lo) / larg))]++;
  }
  const cmax = Math.max(...cont, 1);
  const inv = R.inv[R.P - 1];
  const x = (v) => ml + ((v - lo) / (hi - lo)) * iw;
  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Distribuição do valor final">`;
  cont.forEach((c, i) => {
    const v0 = lo + i * larg, bh = (c / cmax) * ih;
    const cor = v0 + larg / 2 < inv ? 'var(--out)' : 'var(--in)';
    svg += `<rect x="${(x(v0) + 0.5).toFixed(1)}" y="${(mt + ih - bh).toFixed(1)}" width="${Math.max(1, iw / nb - 1.5).toFixed(1)}" height="${bh.toFixed(1)}" rx="2" fill="${cor}" opacity="0.8"/>`;
  });
  svg += `<line x1="${ml}" x2="${W - mr}" y1="${mt + ih}" y2="${mt + ih}" stroke="var(--line-2)"/>`;
  const marca = (v, cor, txt, ancora) => {
    if (v < lo || v > hi) return '';
    const xx = x(v);
    const a = ancora || (xx > W * 0.75 ? 'end' : xx < W * 0.25 ? 'start' : 'middle');
    return `<line x1="${xx}" x2="${xx}" y1="${mt - 2}" y2="${mt + ih}" stroke="${cor}" stroke-width="2" stroke-dasharray="4 3"/>` +
      `<text x="${xx}" y="${mt - 5}" text-anchor="${a}" style="fill:${cor}">${esc(txt)}</text>`;
  };
  svg += marca(R.q(0.5), 'var(--fan-line)', 'mediana');
  if (Math.abs(x(inv) - x(R.q(0.5))) > 60) svg += marca(inv, 'var(--text-2)', 'investido');
  if (o.meta > 0 && Math.abs(x(o.meta) - x(R.q(0.5))) > 50) svg += marca(o.meta, 'var(--warn)', 'meta');
  svg += `<text x="${ml}" y="${Hh - 6}" text-anchor="start">${esc(curtoEixo(lo))}</text>`;
  svg += `<text x="${W / 2}" y="${Hh - 6}" text-anchor="middle">mediana ${esc(curtoEixo(R.q(0.5)))}</text>`;
  svg += `<text x="${W - mr}" y="${Hh - 6}" text-anchor="end">${esc(curtoEixo(hi))}</text>`;
  svg += `</svg>`;
  box.innerHTML = svg;
  $('hist-note').textContent = '';
  $('hist-help').textContent = `Cada barra junta futuros que terminaram com valores parecidos. Em vermelho, os que terminaram abaixo do que foi investido. Os 1% mais extremos de cada lado ficaram de fora.`;
}

let redim;
window.addEventListener('resize', () => {
  clearTimeout(redim);
  redim = setTimeout(() => { if (ultimo) { desenharLeque(); desenharHistograma(); } }, 150);
});

// ---------------------------------------------------------------- início
(function inicio() {
  let fimDados = 0;
  for (const t in D.series) fimDados = Math.max(fimDados, idx(D.series[t].inicio) + D.series[t].r.length - 1);
  const [y, m, d] = D.atualizado.split('-');
  $('foot').textContent = `${D.ativos.length} ativos · dados até ${rotulo(fimDados)} (baixados em ${d}/${m}/${y}). Preços: Yahoo Finance. Proventos: Fundamentus. CDI e IPCA: Banco Central.`;
  desenharCarteira();
  rodar(false);
})();
