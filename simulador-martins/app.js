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
  const anuais = new Float64Array(nSim), quedas = new Float64Array(nSim);

  for (let s = 0; s < nSim; s++) {
    let v = o.inicial, vc = o.inicial, inf = 1, inv = o.inicial, invReal = o.inicial, cresce = 0;
    let pos = 0, resta = 0, k = 1, nivel = 0, topo = 0, queda = 0;
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
      nivel += Math.log1p(H.port[pos]);
      if (nivel > topo) topo = nivel; else if (nivel - topo < queda) queda = nivel - topo;
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
    quedas[s] = Math.expm1(queda);
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
  const ordQueda = Float64Array.from(quedas).sort();
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
    q: (p) => quantil(ord, p), qa: (p) => quantil(ordAnual, p), qd: (p) => quantil(ordQueda, p),
  };
}
function quantil(arr, q) {
  const x = q * (arr.length - 1), i = Math.floor(x), f = x - i;
  return i + 1 < arr.length ? arr[i] * (1 - f) + arr[i + 1] * f : arr[i];
}

// ---------------------------------------------------------------- carteira (tela)
const CLASSES = [['acao', 'Ações'], ['fii', 'FIIs'], ['etf', 'ETFs'], ['indice', 'Índice']];
function desenharCarteira() {
  const h = $('holdings');
  h.innerHTML = S.carteira.length ? `<thead><tr><th>Ativo</th><th>Classe</th><th>Peso</th><th></th></tr></thead><tbody>` +
    S.carteira.map((c, i) => {
      const a = ATIVOS[c.t];
      return `<tr><td class="t"><span class="tk">${esc(a.t)}</span><span class="nm">${esc(a.n)}</span></td>
        <td><span class="cls"><i class="sw" style="background:var(--c-${a.tipo})"></i>${TIPO_NOME[a.tipo]}</span></td>
        <td><label class="w"><input type="number" inputmode="decimal" min="0" max="100" step="1" id="w-${esc(a.t)}" data-i="${i}" value="${+c.p.toFixed(2)}" aria-label="Peso de ${esc(a.t)} em %"><span>%</span></label></td>
        <td><button class="rm" type="button" data-rm="${i}" aria-label="Remover ${esc(a.t)}"><svg class="ic" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg></button></td></tr>`;
    }).join('') + '</tbody>'
    : '<tbody><tr><td class="empty">Carteira vazia. Escolha um modelo acima ou adicione ativos.</td></tr></tbody>';
  $('cart-count').textContent = `${S.carteira.length} ${S.carteira.length === 1 ? 'ativo' : 'ativos'}`;
  desenharSoma();
  desenharPresets();
}
function desenharAlocacao() {
  const soma = S.carteira.reduce((a, c) => a + c.p, 0) || 1;
  const por = {};
  for (const c of S.carteira) { const t = ATIVOS[c.t].tipo; por[t] = (por[t] || 0) + c.p / soma; }
  const cls = CLASSES.filter(([t]) => por[t] > 0);
  $('alloc').innerHTML = cls.map(([t]) => `<i style="width:${(por[t] * 100).toFixed(2)}%;background:var(--c-${t})"></i>`).join('');
  $('alloc-leg').innerHTML = cls.map(([t, n]) => `<span><i class="sw" style="background:var(--c-${t})"></i>${n} <b>${pct(por[t], 0)}</b></span>`).join('');
}
function desenharSoma() {
  const soma = S.carteira.reduce((a, c) => a + c.p, 0);
  const ok = Math.abs(soma - 100) < 0.5;
  $('sum').innerHTML = !S.carteira.length ? '' :
    `<span>Soma dos pesos <span class="v ${ok ? 'ok' : 'bad'}">${soma.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</span></span>` +
    (!ok && soma > 0 ? '<button class="link" type="button" id="btn-fix">Normalizar para 100%</button>' : '');
  desenharAlocacao();
}
function desenharPresets() {
  const atual = JSON.stringify(Object.fromEntries(S.carteira.map((c) => [c.t, c.p])));
  $('presets').innerHTML = PRESETS.map((p, i) =>
    `<button type="button" class="preset${JSON.stringify(p.c) === atual ? ' on' : ''}" data-preset="${i}" aria-pressed="${JSON.stringify(p.c) === atual}">${esc(p.nome)}</button>`).join('');
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
  $('list').innerHTML = `<thead><tr><th>Ativo</th><th class="hide-s">Classe · setor</th><th>Ret. a.a.</th><th>Vol.</th><th>Desde</th></tr></thead><tbody>` +
    (itens.map((a) => {
      const e = ESTAT[a.t], on = sel.has(a.t);
      return `<tr class="${on ? 'sel' : ''}" data-t="${esc(a.t)}" tabindex="0" role="checkbox" aria-checked="${on}">
        <td class="t"><span class="ck"><svg viewBox="0 0 24 24"><path d="M5 12l5 5 9-10"/></svg></span><span class="tk">${esc(a.t)}</span><span class="nm" style="padding-left:24px">${esc(a.n)}</span></td>
        <td class="t hide-s"><span class="cls"><i class="sw" style="background:var(--c-${a.tipo})"></i>${TIPO_NOME[a.tipo]}</span><span class="nm">${esc(a.setor)}</span></td>
        <td class="${e.anual < 0 ? 'neg' : ''}">${pct(e.anual)}</td><td>${pct(e.vol, 0)}</td><td>${e.desde}</td></tr>`;
    }).join('') || '<tr><td colspan="5" class="empty">Nenhum ativo encontrado.</td></tr>') + '</tbody>';
}
$('list').addEventListener('keydown', (e) => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.dataset.t) { e.preventDefault(); e.target.click(); }
});

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
  btn.disabled = true; $('btn-sim-label').textContent = 'Processando…';
  setTimeout(() => {
    const t0 = performance.now();
    const R = simular(H, S);
    ultimo = { H, R, o: { ...S }, cart, ms: performance.now() - t0 };
    mostrarResultado();
    btn.disabled = false; $('btn-sim-label').textContent = 'Rodar novamente';
    if (rolar && window.innerWidth < 1000) $('res').scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, 30);
}

// ---------------------------------------------------------------- resultado
function mostrarResultado() {
  const { H, R, o } = ultimo;
  const invFinal = R.inv[R.P - 1];
  const base = o.real ? 'R$ de hoje' : 'R$ nominais';
  $('runline').innerHTML = [
    ['Cenários', R.nSim.toLocaleString('pt-BR')],
    ['Horizonte', `${R.N} meses`],
    ['Janela', `${rotulo(H.ini)}–${rotulo(H.fim)} (${H.meses.length} m)`],
    ['Reamostragem', o.bloco > 1 ? `blocos de ${o.bloco} m` : 'mensal'],
    ['Base', o.real ? 'real (IPCA)' : 'nominal'],
  ].map(([k, v]) => `<span>${k}: <b>${v}</b></span>`).join('');

  const cor = (p, bomSeAlto) => (bomSeAlto ? p >= 0.6 : p <= 0.1) ? 'pos' : (bomSeAlto ? p < 0.4 : p > 0.25) ? 'neg' : 'amb';
  const ra = R.qa(0.5), dd = R.qd(0.5);
  const kpi = (k, v, s, cls = '', extra = '') => `<div class="kpi ${extra}"><span class="k">${k}</span><span class="v ${cls}">${v}</span><span class="s">${s}</span></div>`;
  $('kpis').innerHTML =
    kpi('Patrimônio mediano (P50)', brl(R.q(0.5)), `em ${o.anos} ${o.anos === 1 ? 'ano' : 'anos'} · ${base}`, '', 'lead') +
    kpi('Intervalo P10–P90', `${curto(R.q(0.1))} – ${curto(R.q(0.9))}`, '80% dos cenários', '', 'lead sm') +
    kpi('Total aportado', curto(invFinal), o.real ? 'em R$ de hoje' : 'nominal') +
    kpi('Retorno a.a. (P50)', pct(ra), o.real ? 'acima do IPCA' : 'nominal', ra < 0 ? 'neg' : '') +
    kpi('Queda máxima (P50)', pct(dd, 0), `pior 10%: ${pct(R.qd(0.1), 0)}`, 'neg') +
    kpi('Prob. de perda', pct(R.pPerde, 0), o.real ? 'abaixo do aportado, real' : 'abaixo do aportado', cor(R.pPerde, false)) +
    kpi('Prob. de superar o CDI', pct(R.pCdi, 0), 'mesmos aportes', cor(R.pCdi, true)) +
    kpi('Mesmo plano no CDI', curto(R.cdi[R.P - 1]), 'média dos cenários, sem IR') +
    kpi('Volatilidade a.a.', pct(estatisticas(Array.from(H.port)).vol), 'da carteira, na janela') +
    (R.pMeta !== null ? kpi('Prob. de atingir a meta', pct(R.pMeta, 0), `meta ${curto(o.meta)}`, cor(R.pMeta, true))
      : kpi('Prob. de atingir a meta', '—', 'defina uma meta'));

  $('fan-meta').textContent = base;
  desenharLeque();
  desenharHistograma();
  desenharTabelas();
}

function desenharTabelas() {
  const { H, R, o, cart } = ultimo;
  const linhas = [['P5', 0.05], ['P10', 0.1], ['P25', 0.25], ['P50', 0.5], ['P75', 0.75], ['P90', 0.9], ['P95', 0.95]];
  $('tbl-pct').innerHTML = `<thead><tr><th>Pctl.</th><th>Patrimônio (R$)</th><th>Ret. a.a.</th><th>Queda máx.</th></tr></thead><tbody>` +
    linhas.map(([n, q]) => {
      const a = R.qa(q), d = R.qd(1 - q);
      return `<tr class="${q === 0.5 ? 'tot' : ''}"><td class="t">${n}</td><td>${Math.round(R.q(q)).toLocaleString('pt-BR')}</td><td class="${a < 0 ? 'neg' : ''}">${pct(a)}</td><td>${pct(d, 0)}</td></tr>`;
    }).join('') + '</tbody>';
  $('pct-meta').textContent = o.real ? 'R$ de hoje' : 'R$ nominais';
  $('pct-help').textContent = `Cada coluna é ordenada separadamente. Retorno a.a. é o da carteira${o.real ? ', acima do IPCA' : ''}, sem efeito dos aportes. Queda máxima é a maior perda do topo ao fundo; nos percentis altos aparecem as menores quedas.`;

  const linhasAtivos = cart.map((c, j) => ({ t: c.t, p: H.pesos[j], e: estatisticas(H.porAtivo[j]) }));
  const eCart = estatisticas(Array.from(H.port)), eCdi = estatisticas(Array.from(H.cdi)), eIpca = estatisticas(Array.from(H.ipca));
  const linha = (nome, peso, e, cls = '') => `<tr class="${cls}"><td class="t">${nome}</td><td>${peso}</td><td class="${e.anual < 0 ? 'neg' : ''}">${pct(e.anual)}</td><td>${pct(e.vol)}</td><td class="${e.pior < -0.005 ? 'neg' : ''}">${pct(e.pior)}</td></tr>`;
  $('tbl-ativos').innerHTML = `<thead><tr><th>Ativo</th><th>Peso</th><th>Ret. a.a.</th><th>Vol. a.a.</th><th>Pior mês</th></tr></thead><tbody>` +
    linhasAtivos.map((x) => linha(`<span class="tk">${esc(x.t)}</span>`, pct(x.p, 1), x.e)).join('') +
    linha('Carteira', '100,0%', eCart, 'tot') + linha('CDI', '—', eCdi, 'ref') + linha('IPCA', '—', eIpca, 'ref') + '</tbody>';
  $('win-meta').textContent = `${rotulo(H.ini)}–${rotulo(H.fim)} · ${H.meses.length} m`;
  const lim = H.limitante ? ` A janela começa em ${rotulo(H.ini)}, início do histórico de ${H.limitante}.` : '';
  $('win-help').textContent = `Valores nominais com proventos reinvestidos (JCP líquido de 15% de IR), calculados só nos meses usados no sorteio.${lim}`;
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
    svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--rule)" stroke-width="1"/>`;
    svg += `<text x="${ml - 8}" y="${y(v) + 4}" text-anchor="end">${esc(curtoEixo(v))}</text>`;
  }
  const passoAno = o.anos <= 6 ? 1 : o.anos <= 12 ? 2 : o.anos <= 25 ? 5 : 10;
  for (let a = 0; a <= o.anos; a += passoAno) {
    svg += `<text x="${x(a * 12)}" y="${Hh - 6}" text-anchor="middle">${a === 0 ? 'hoje' : `ano ${a}`}</text>`;
  }
  svg += `<g clip-path="url(#clip)">`;
  // alguns futuros sorteados, para mostrar o que é cada "futuro"
  const nCam = Math.min(30, R.nSim);
  for (let s = 0; s < nCam; s++) {
    const arr = R.caminhos.subarray(s * R.P, (s + 1) * R.P);
    svg += `<polyline points="${linhaPts(Array.from(arr))}" fill="none" stroke="var(--path)" stroke-width="1"/>`;
  }
  svg += `<polygon points="${banda(p10, p90)}" fill="var(--band-90)"/>`;
  svg += `<polygon points="${banda(p25, p75)}" fill="var(--band-50)"/>`;
  svg += `<polyline points="${linhaPts(R.inv)}" fill="none" stroke="var(--muted)" stroke-width="1.5" stroke-dasharray="2 4"/>`;
  svg += `<polyline points="${linhaPts(R.cdi)}" fill="none" stroke="var(--cdi)" stroke-width="1.75" stroke-dasharray="6 4"/>`;
  svg += `<polyline points="${linhaPts(Array.from(p50))}" fill="none" stroke="var(--median)" stroke-width="2.25"/>`;
  svg += `</g>`;
  svg += `<circle cx="${x(R.N)}" cy="${y(p50[R.P - 1])}" r="3.5" fill="var(--median)"/>`;
  svg += `<g id="cursor" style="display:none"><line id="cur-l" y1="${mt}" y2="${mt + ih}" stroke="var(--rule-2)" stroke-width="1"/><circle id="cur-c" r="4.5" fill="var(--median)" stroke="var(--panel)" stroke-width="2"/></g>`;
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
    const quando = mes === 0 ? 'Hoje' : `${anos ? `${anos} ${anos === 1 ? 'ano' : 'anos'}` : ''}${anos && resto ? ' e ' : ''}${resto ? `${resto} ${resto === 1 ? 'mês' : 'meses'}` : ''}`;
    $('readout').innerHTML = `<b>${quando}</b> · P50 <b>${curto(p50[k])}</b> · P10–P90 ${curto(p10[k])}–${curto(p90[k])} · aportado ${curto(R.inv[k])} · CDI ${curto(R.cdi[k])}`;
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
    const cor = v0 + larg / 2 < inv ? 'var(--neg)' : 'var(--accent)';
    svg += `<rect x="${(x(v0) + 0.5).toFixed(1)}" y="${(mt + ih - bh).toFixed(1)}" width="${Math.max(1, iw / nb - 1.5).toFixed(1)}" height="${bh.toFixed(1)}" rx="2" fill="${cor}" opacity="0.85"/>`;
  });
  svg += `<line x1="${ml}" x2="${W - mr}" y1="${mt + ih}" y2="${mt + ih}" stroke="var(--rule-2)"/>`;
  const marca = (v, cor, txt, ancora) => {
    if (v < lo || v > hi) return '';
    const xx = x(v);
    const a = ancora || (xx > W * 0.75 ? 'end' : xx < W * 0.25 ? 'start' : 'middle');
    return `<line x1="${xx}" x2="${xx}" y1="${mt - 2}" y2="${mt + ih}" stroke="${cor}" stroke-width="2" stroke-dasharray="4 3"/>` +
      `<text x="${xx}" y="${mt - 5}" text-anchor="${a}" style="fill:${cor}">${esc(txt)}</text>`;
  };
  svg += marca(R.q(0.5), 'var(--ink)', 'P50');
  if (Math.abs(x(inv) - x(R.q(0.5))) > 60) svg += marca(inv, 'var(--muted)', 'aportado');
  if (o.meta > 0 && Math.abs(x(o.meta) - x(R.q(0.5))) > 50) svg += marca(o.meta, 'var(--amber)', 'meta');
  svg += `<text x="${ml}" y="${Hh - 6}" text-anchor="start">${esc(curtoEixo(lo))}</text>`;
  svg += `<text x="${W / 2}" y="${Hh - 6}" text-anchor="middle">P50 ${esc(curtoEixo(R.q(0.5)))}</text>`;
  svg += `<text x="${W - mr}" y="${Hh - 6}" text-anchor="end">${esc(curtoEixo(hi))}</text>`;
  svg += `</svg>`;
  box.innerHTML = svg;
  $('hist-meta').textContent = `P1–P99 · ${nb} faixas`;
  $('hist-help').textContent = `Frequência dos cenários por patrimônio final. Em vermelho, os cenários que terminaram abaixo do total aportado. Os 1% extremos de cada lado ficam fora do gráfico.`;
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
  $('stamp').textContent = `Base ${rotulo(fimDados)}`;
  $('foot').textContent = `${D.ativos.length} ativos · dados até ${rotulo(fimDados)} (baixados em ${d}/${m}/${y}). Preços: Yahoo Finance. Proventos: Fundamentus. CDI e IPCA: Banco Central.`;
  desenharCarteira();
  rodar(false);
})();
