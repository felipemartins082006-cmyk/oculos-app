// Simulador Martins 2 — Monte Carlo, backtest e análise de carteiras com ativos da B3, fundos (CVM),
// ações/ETFs dos EUA, cripto e renda fixa por modelo.
'use strict';

const D = window.DADOS;
const $ = (id) => document.getElementById(id);
const CHAVE = 'simulador-martins-v2';
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
const idx = (s) => { const [y, m] = s.split('-').map(Number); return y * 12 + m - 1; };
const rotulo = (i) => `${MESES[i % 12]}/${Math.floor(i / 12)}`;
const BASE = idx(D.base);
// número ilimitado de carteiras: as 3 primeiras usam as cores do tema; as seguintes, outras cores
// da paleta e depois tons gerados pelo ângulo áureo (bem distintos entre si). Letras: A–Z, AA, AB…
const PALETA_EXTRA = ['var(--bm4)', 'var(--c-fundo)', 'var(--c-etf)', 'var(--c-fii)', 'var(--c-cripto)', 'var(--bm1)', 'var(--c-us)'];
function corCart(i) {
  if (i < 3) return ['var(--pA)', 'var(--pB)', 'var(--pC)'][i];
  if (i < 3 + PALETA_EXTRA.length) return PALETA_EXTRA[i - 3];
  return `hsl(${Math.round((i * 137.508) % 360)} 55% 50%)`;
}
function letra(i) { let s = ''; i += 1; while (i > 0) { const r = (i - 1) % 26; s = String.fromCharCode(65 + r) + s; i = Math.floor((i - 1) / 26); } return s; }
const indice = (f) => new Proxy([], { get: (t, k) => (typeof k === 'string' && /^\d+$/.test(k) ? f(+k) : t[k]) });
const CORES_CART = indice(corCart);
const LETRAS = indice(letra);

// ---------------------------------------------------------------- formatação
const fmtBRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL', maximumFractionDigits: 0 });
const brl = (v) => fmtBRL.format(Math.round(v));
function curto(v) {
  const a = Math.abs(v), s = v < 0 ? '−' : '';
  if (a >= 1e9) return `${s}R$ ${(a / 1e9).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} bi`;
  if (a >= 1e6) return `${s}R$ ${(a / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} mi`;
  if (a >= 1e4) return `${s}R$ ${(a / 1e3).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`;
  return brl(v);
}
const pct = (x, c = 1) => (isFinite(x) ? (x * 100).toLocaleString('pt-BR', { minimumFractionDigits: c, maximumFractionDigits: c }) + '%' : '—');
const num = (x, c = 2) => (isFinite(x) ? x.toLocaleString('pt-BR', { minimumFractionDigits: c, maximumFractionDigits: c }) : '—');
const semAcento = (s) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const lerDec = (s) => { const v = parseFloat(String(s).replace(',', '.')); return isFinite(v) ? v : 0; };

// ---------------------------------------------------------------- catálogo de ativos
const TIPOS = {
  acao: ['Ação BR', 'acao'], fii: ['FII', 'fii'], fiagro: ['Fiagro', 'fii'], fiinfra: ['FI-Infra', 'fii'], etf: ['ETF B3', 'etf'],
  fundo_rf: ['Fundo RF', 'fundo'], fundo_mm: ['Multimercado', 'fundo'], fundo_acoes: ['Fundo ações', 'fundo'], fundo_cambial: ['Fundo cambial', 'fundo'],
  acao_us: ['Ação EUA', 'us'], etf_us: ['ETF EUA', 'us'], cripto: ['Cripto', 'cripto'], indice: ['Índice', 'indice'],
  commodity: ['Commodity', 'indice'], moeda: ['Moeda', 'indice'], rf: ['Renda fixa', 'rf'],
};
const GRUPOS = [ // chips do seletor
  ['', 'Todos'], ['acao', 'Ações BR'], ['fii', 'FIIs (fundos imobiliários)'], ['fii:papel', 'FIIs de papel (recebíveis)'], ['fii:tijolo', 'FIIs de tijolo (imóveis)'], ['fii:hibrido', 'FIIs híbridos'],
  ['fiagro|fiinfra', 'Fiagro (agro) e FI-Infra (infraestrutura)'], ['etf', 'ETFs B3 (fundos de índice)'],
  ['fundo_rf', 'Fundos de renda fixa'], ['fundo_mm', 'Multimercados'], ['fundo_acoes|fundo_cambial', 'Fundos de ações e cambiais'],
  ['acao_us', 'Ações EUA'], ['etf_us', 'ETFs EUA'], ['cripto', 'Cripto'], ['indice|commodity|moeda', 'Índices, ouro e moedas'], ['rf', 'Renda fixa (modelo)'],
];
const CLASSES = [ // barra de alocação
  ['acao', 'Ações BR', 'var(--c-acao)'], ['fii', 'Imobiliário/agro/infra', 'var(--c-fii)'], ['etf', 'ETFs B3', 'var(--c-etf)'],
  ['fundo', 'Fundos', 'var(--c-fundo)'], ['us', 'EUA', 'var(--c-us)'], ['cripto', 'Cripto', 'var(--c-cripto)'],
  ['rf', 'Renda fixa', 'var(--c-rf)'], ['indice', 'Índices e outros', 'var(--c-indice)'],
];
const corClasse = Object.fromEntries(CLASSES.map(([k, , c]) => [k, c]));
const FII = D.fii || {};
// filtro de grupo: "acao|etf" (tipos) ou "fii:papel" (FII por mandato, dado da CVM)
function noGrupo(a, k) {
  if (!k) return true;
  return k.split('|').some((g) => { const [tp, mand] = g.split(':'); return a.tipo === tp && (!mand || (FII[a.t] && FII[a.t][0] === mand)); });
}

const ATIVOS = new Map();
for (const a of D.ativos) {
  ATIVOS.set(a[0], { t: a[0], n: a[1], tipo: a[2], setor: a[3], mercado: a[4], moeda: a[5], tam: a[6] || 0, gestor: a[7] || '', cnpj: a[8] || '', cot: a[9] || 0 });
}
function info(t) {
  if (ATIVOS.has(t)) return ATIVOS.get(t);
  const m = /^RF:(CDI|IPCA|PRE):([\d.]+)$/.exec(t);
  if (!m) return null;
  const v = +m[2];
  const n = m[1] === 'CDI' ? `CDI ${num(v, v % 1 ? 1 : 0)}%` : m[1] === 'IPCA' ? `IPCA + ${num(v, 1)}% a.a.` : `Prefixado ${num(v, 1)}% a.a.`;
  return { t, n, tipo: 'rf', setor: 'Renda fixa (modelo)', mercado: 'BR', moeda: 'BRL', tam: 0, rf: m[1], taxa: v, gestor: '' };
}
const rotuloAtivo = (t) => { const a = info(t); return !a ? t : (a.tipo === 'rf' || a.tipo.startsWith('fundo')) ? a.n.slice(0, 26) : t; };

// séries decodificadas sob demanda
const SERIES = new Map();
function serie(t) {
  if (SERIES.has(t)) return SERIES.get(t);
  const s = D.series[t];
  if (!s) return null;
  const e = s[2] || 10000, partes = s[1].split(',');
  const v = new Float64Array(partes.length);
  for (let i = 0; i < partes.length; i++) v[i] = partes[i] === '' ? NaN : +partes[i] / e;
  const o = { i0: BASE + s[0], v, i1: BASE + s[0] + partes.length - 1 };
  SERIES.set(t, o);
  return o;
}
const val = (s, mi) => (mi < s.i0 || mi > s.i1 ? NaN : s.v[mi - s.i0]);
const FIM_DADOS = Math.max(...['CDI', 'IPCA', 'IBOV'].map((t) => serie(t).i1));

// ---------------------------------------------------------------- extensão de histórico por fatores
// r − base = α + Σβ(fator − base) + ε ; base = CDI (0 em dólar). Meses antes do início = base + α·n/(n+120) + Σβf + ε sorteado.
const FATORES = {
  fii: ['IFIX', 'IPCA'], fiagro: ['IFIX', 'IPCA'], fiinfra: ['IFIX', 'IPCA'], acao: ['IBOV', 'SMLL'], fundo_acoes: ['IBOV', 'SMLL'],
  etf: ['IBOV', 'USDBRL'], fundo_rf: ['IPCA'], fundo_mm: ['IBOV', 'USDBRL', 'IPCA'], fundo_cambial: ['USDBRL'], acao_us: ['SPY', 'QQQ'], etf_us: ['SPY', 'TLT'],
};
const FAT_NIVEL = new Set(['IPCA', 'USDBRL']);
const ESTENDIDAS = new Map();
function aleatorio(semente) { let a = semente >>> 0; return () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }
function resolverSistema(A, b) { // eliminação de Gauss (sistemas pequenos)
  const n = b.length, M = A.map((r, i) => [...r, b[i]]);
  for (let c = 0; c < n; c++) {
    let p = c; for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
    [M[c], M[p]] = [M[p], M[c]];
    if (Math.abs(M[c][c]) < 1e-14) return null;
    for (let r = c + 1; r < n; r++) { const f = M[r][c] / M[c][c]; for (let k = c; k <= n; k++) M[r][k] -= f * M[c][k]; }
  }
  const x = new Array(n).fill(0);
  for (let r = n - 1; r >= 0; r--) { let t = M[r][n]; for (let k = r + 1; k < n; k++) t -= M[r][k] * x[k]; x[r] = t / M[r][r]; }
  return x;
}
function serieEstendida(t) {
  if (ESTENDIDAS.has(t)) return ESTENDIDAS.get(t);
  const a = info(t), s = serie(t), fs = FATORES[a.tipo];
  let out = s;
  if (fs) {
    const usd = a.moeda === 'USD', cdi = serie('CDI'), F = fs.map(serie);
    const ref = (mi) => (usd ? 0 : val(cdi, mi));
    const fv = (k, mi) => (FAT_NIVEL.has(fs[k]) ? val(F[k], mi) : val(F[k], mi) - ref(mi));
    const Xs = [], Ys = [];
    for (let mi = s.i0; mi <= s.i1; mi++) {
      const y = val(s, mi); if (isNaN(y) || isNaN(ref(mi))) continue;
      const linha = [1]; let ok = true;
      for (let k = 0; k < fs.length; k++) { const v = fv(k, mi); if (isNaN(v)) { ok = false; break; } linha.push(v); }
      if (ok) { Xs.push(linha); Ys.push(y - ref(mi)); }
    }
    const n = Ys.length, d = fs.length + 1;
    if (n >= 24) {
      const XtX = Array.from({ length: d }, (_, i) => Array.from({ length: d }, (_, j) => Xs.reduce((acc, r) => acc + r[i] * r[j], 0)));
      const XtY = Array.from({ length: d }, (_, i) => Xs.reduce((acc, r, k) => acc + r[i] * Ys[k], 0));
      const b = resolverSistema(XtX, XtY);
      if (b) {
        const res = Ys.map((y, k) => y - Xs[k].reduce((acc, x, i) => acc + x * b[i], 0));
        const ini = Math.max(...F.map((f) => f.i0), usd ? 0 : cdi.i0);
        if (ini < s.i0) {
          let h = 0; for (const ch of t) h = (h * 31 + ch.charCodeAt(0)) | 0;
          const rnd = aleatorio(h), alfa = b[0] * n / (n + 120);
          const v = new Float64Array(s.i1 - ini + 1); let est = 0;
          for (let mi = ini; mi <= s.i1; mi++) {
            if (mi >= s.i0) { v[mi - ini] = val(s, mi); continue; }
            let x = ref(mi) + alfa, ok = !isNaN(x);
            for (let k = 0; k < fs.length && ok; k++) { const f = fv(k, mi); if (isNaN(f)) ok = false; else x += b[k + 1] * f; }
            v[mi - ini] = ok ? Math.max(-0.9, x + res[(rnd() * n) | 0]) : NaN; if (ok) est++;
          }
          out = { i0: ini, i1: s.i1, v, estimados: est };
        }
      }
    }
  }
  ESTENDIDAS.set(t, out);
  return out;
}

// estatística de cada ativo (em reais) para o seletor
const ESTAT = new Map();
function estatAtivo(t) {
  if (ESTAT.has(t)) return ESTAT.get(t);
  const a = info(t);
  let e = { anual: NaN, vol: NaN, sharpe: NaN, n: 0, desde: '' };
  if (a.tipo !== 'rf') {
    const s = serie(t), fx = serie('USDBRL'), cdi = serie('CDI');
    const r = [], c = [];
    for (let mi = s.i0; mi <= s.i1; mi++) {
      let x = val(s, mi);
      if (isNaN(x)) continue;
      if (a.moeda === 'USD') { const f = val(fx, mi); if (isNaN(f)) continue; x = (1 + x) * (1 + f) - 1; }
      r.push(x); c.push(val(cdi, mi) || 0);
    }
    if (r.length >= 12) {
      const m = metricas(r, c);
      e = { anual: m.cagr, vol: m.vol, sharpe: m.sharpe, n: r.length, desde: String(Math.floor(s.i0 / 12)) };
    }
  }
  ESTAT.set(t, e);
  return e;
}

// ---------------------------------------------------------------- estatística
function media(a) { let s = 0; for (const x of a) s += x; return s / a.length; }
function desvio(a, m = media(a)) { let s = 0; for (const x of a) s += (x - m) ** 2; return Math.sqrt(s / Math.max(1, a.length - 1)); }
function quantil(arr, q) { // arr ordenado
  if (!arr.length) return NaN;
  const x = q * (arr.length - 1), i = Math.floor(x), f = x - i;
  return i + 1 < arr.length ? arr[i] * (1 - f) + arr[i + 1] * f : arr[i];
}
function metricas(r, cdi, bench) {
  const n = r.length;
  let g = 0, gc = 0, pos = 0, pico = 0, nivel = 0, dd = 0, durMax = 0, dur = 0, down = 0;
  for (let i = 0; i < n; i++) {
    g += Math.log1p(r[i]); gc += Math.log1p(cdi[i]); if (r[i] > 0) pos++;
    nivel += Math.log1p(r[i]);
    if (nivel >= pico) { pico = nivel; dur = 0; } else { dur++; if (dur > durMax) durMax = dur; if (nivel - pico < dd) dd = nivel - pico; }
    const ex = Math.min(0, r[i] - cdi[i]); down += ex * ex;
  }
  const cagr = Math.exp(g / n * 12) - 1, cdiA = Math.exp(gc / n * 12) - 1;
  const vol = desvio(r) * Math.sqrt(12);
  const ord = Float64Array.from(r).sort();
  const var95 = quantil(ord, 0.05);
  let cv = 0, nc = 0; for (const x of ord) { if (x <= var95) { cv += x; nc++; } }
  const out = {
    cagr, vol, sharpe: vol > 0 ? (cagr - cdiA) / vol : NaN,
    sortino: down > 0 ? (cagr - cdiA) / (Math.sqrt(down / n) * Math.sqrt(12)) : NaN,
    maxdd: Math.expm1(dd), durdd: durMax, pior: ord[0], melhor: ord[n - 1], var95, cvar95: nc ? cv / nc : NaN,
    pos: pos / n, n,
  };
  if (bench) {
    const mb = media(bench), mr = media(r);
    let cov = 0, vb = 0, vr = 0;
    for (let i = 0; i < n; i++) { cov += (r[i] - mr) * (bench[i] - mb); vb += (bench[i] - mb) ** 2; vr += (r[i] - mr) ** 2; }
    out.beta = vb > 0 ? cov / vb : NaN; out.corr = vb > 0 && vr > 0 ? cov / Math.sqrt(vb * vr) : NaN;
  }
  return out;
}
function cholesky(A, n) {
  const L = new Float64Array(n * n);
  for (let i = 0; i < n; i++) {
    for (let j = 0; j <= i; j++) {
      let s = A[i * n + j];
      for (let k = 0; k < j; k++) s -= L[i * n + k] * L[j * n + k];
      if (i === j) L[i * n + i] = Math.sqrt(Math.max(s, 1e-14));
      else L[i * n + j] = s / L[j * n + j];
    }
  }
  return L;
}
let gaussExtra = null;
function gauss() {
  if (gaussExtra !== null) { const g = gaussExtra; gaussExtra = null; return g; }
  let u = 0; while (u === 0) u = Math.random();
  const v = Math.random(), r = Math.sqrt(-2 * Math.log(u)), th = 2 * Math.PI * v;
  gaussExtra = r * Math.sin(th);
  return r * Math.cos(th);
}

// ---------------------------------------------------------------- estado
const PRESETS = [
  { nome: 'Equilibrado BR', c: { BOVA11: 25, ITUB4: 10, TAEE11: 10, WEGE3: 10, HGLG11: 15, KNRI11: 10, MXRF11: 10, KNCR11: 10 } },
  { nome: 'Ibovespa', c: { BOVA11: 100 } },
  { nome: 'S&P 500', c: { SPY: 100 } },
  { nome: 'Global 60/40', c: { VT: 40, BOVA11: 20, 'RF:IPCA:6': 25, 'RF:CDI:100': 15 } },
  { nome: 'Dividendos BR', c: { ITUB4: 20, BBAS3: 20, TAEE11: 20, BBSE3: 20, VIVT3: 20 } },
  { nome: 'FIIs', c: { HGLG11: 20, KNRI11: 20, MXRF11: 20, XPML11: 20, KNCR11: 20 } },
  { nome: 'Big Techs EUA', c: { AAPL: 15, MSFT: 15, NVDA: 15, GOOGL: 15, AMZN: 15, META: 15, TSLA: 10 } },
  { nome: 'Permanente', c: { BOVA11: 25, IMAB11: 25, OURO: 25, 'RF:CDI:100': 25 } },
  { nome: 'S&P + Bitcoin', c: { SPY: 90, BTC: 10 } },
  { nome: 'Renda fixa', c: { 'RF:CDI:100': 50, 'RF:IPCA:6': 30, 'RF:PRE:12': 20 } },
  { nome: 'Top multimercados', c: null },
];
const PADRAO = {
  carteiras: [
    { nome: 'Equilibrado BR', itens: Object.entries(PRESETS[0].c).map(([t, p]) => ({ t, p })) },
    { nome: 'S&P 500', itens: [{ t: 'SPY', p: 100 }] },
  ],
  ativa: 0, ver: 0, an: 0,
  inicial: 10000, aporte: 1000, anos: 40, meta: 2000000, reaj: 0, corrige: true, versao: 3,
  saqueOn: false, saqueAno: 25, saque: 5000, saqueCorrige: true,
  metodo: 'boot', bloco: 12, janela: 40, curto: 'redistribuir', rebal: 1, cambio: 1, ajuste: 0, taxa: 0, ir: 0, n: 100000, real: true,
  btModo: 'unit', btEscala: 'log', btJanela: 40, btBench: ['CDI', 'IPCA', 'IBOV', 'SPY'],
  ot: { fonte: 'filtro', classes: ['fii:papel'], minAnos: 5, maxUniv: 60, janela: 6, cart: 0, obj: 'sharpe', volAlvo: 8, pesoMax: 20, maxAtivos: 8, reamostra: true, estender: false },
};
let S = carregar();
function carregar() {
  try {
    const x = JSON.parse(localStorage.getItem(CHAVE));
    if (x && Array.isArray(x.carteiras) && x.carteiras.length) {
      x.carteiras.forEach((c) => { c.itens = (c.itens || []).filter((i) => info(i.t)); });
      const p = JSON.parse(JSON.stringify(PADRAO));
      if (x.versao !== 3) { // ajustes da versão 4: horizonte de 40 anos, janela em anos, ativos novos entram quando começam
        if (!x.anos || x.anos === 20) x.anos = 40;
        if (x.meta === 1000000) x.meta = 2000000;
        x.curto = x.estender ? 'estimar' : 'redistribuir';
        x.janela = 40; x.btJanela = 40; x.n = Math.max(x.n || 0, 100000); x.versao = 3;
        delete x.desde; delete x.btDesde; delete x.estender;
      }
      return { ...p, ...x, ot: { ...p.ot, ...(x.ot || {}) } };
    }
  } catch (e) { /* sem armazenamento */ }
  return JSON.parse(JSON.stringify(PADRAO));
}
let salvarT;
function salvar() {
  clearTimeout(salvarT);
  salvarT = setTimeout(() => { try { localStorage.setItem(CHAVE, JSON.stringify(S)); } catch (e) { /* ignora */ } }, 200);
}
const cartAtiva = () => S.carteiras[S.ativa];

// ---------------------------------------------------------------- preparação dos dados
// Junta os ativos das carteiras numa janela e devolve retornos mensais em reais.
// modo para ativos mais novos que a janela:
//   'redistribuir' (padrão): a janela começa no ativo mais antigo de cada carteira; enquanto um ativo
//                  ainda não existia, o peso dele é dividido proporcionalmente entre os que existiam;
//   'cortar': a janela começa no ativo mais novo (só meses em que todos existem);
//   'estimar': os anos que faltam são estimados por modelo de fatores.
function preparar(carts, desde, cambio = 1, modo = 'cortar') {
  if (modo === true) modo = 'estimar'; if (modo === false) modo = 'cortar';
  const estender = modo === 'estimar', redis = modo === 'redistribuir';
  const sr = (t) => (estender && !['CDI', 'IPCA', 'USDBRL'].includes(t) ? serieEstendida(t) : serie(t));
  const cs = carts.map((c) => ({ ...c, itens: c.itens.filter((i) => i.p > 0 && info(i.t)) })).filter((c) => c.itens.length);
  if (!cs.length) return { erro: 'Adicione ativos com peso maior que zero.' };
  const uni = [...new Set(cs.flatMap((c) => c.itens.map((i) => i.t)))];
  const reais = uni.filter((t) => !t.startsWith('RF:'));
  const sc = serie('CDI'), si = serie('IPCA'), sf = serie('USDBRL');
  const desdeI = idx(`${desde}-01`);
  let ini = Math.max(desdeI, sc.i0, si.i0, sf.i0), fim = Math.min(sc.i1, si.i1, sf.i1), limitante = null, novo = -Infinity;
  // o fim da janela é sempre o último mês da base: um ativo que parou de ter dados sai da carteira
  // naquele mês e o peso dele vai para os demais (igual a quando um ativo ainda não existia)
  if (redis) {
    // cada carteira precisa ter pelo menos um ativo existindo: começa no ativo mais antigo dela
    for (const c of cs) {
      const rf = c.itens.some((i) => i.t.startsWith('RF:'));
      const c0 = rf ? -Infinity : Math.min(...c.itens.map((i) => sr(i.t).i0));
      if (c0 > novo) { novo = c0; limitante = c.itens.reduce((a, i) => (sr(i.t).i0 < sr(a).i0 ? i.t : a), c.itens.find((i) => !i.t.startsWith('RF:'))?.t || c.itens[0].t); }
    }
    // nunca menos de 3 anos: se o ativo mais antigo for mais novo que isso, a janela recua e os meses
    // em que nenhum ativo da carteira existia usam o índice de referência da classe (ver indiceClasse)
    if (novo > fim - 35) { novo = fim - 35; limitante = null; }
    ini = Math.max(ini, novo);
    ini = Math.max(Math.min(ini, fim - 35), sc.i0, si.i0, sf.i0); // janela de no mínimo 3 anos
  } else {
    for (const t of reais) { const s = sr(t); if (s.i0 > novo) { novo = s.i0; limitante = t; } ini = Math.max(ini, s.i0); }
  }
  const ss = reais.map(sr);
  const meses = [];
  for (let mi = ini; mi <= fim; mi++) {
    if (isNaN(val(sc, mi)) || isNaN(val(si, mi)) || isNaN(val(sf, mi))) continue;
    if (!redis && ss.some((s) => isNaN(val(s, mi)))) continue;
    meses.push(mi);
  }
  const L = meses.length;
  if (L < 24 && !redis) {
    // pouco histórico em comum para cortar a janela: os ativos entram quando começam (sem recusar a carteira)
    const H2 = preparar(carts, desde, cambio, 'redistribuir'); if (!H2.erro) H2.trocouModo = modo; return H2;
  }
  if (L < 2) return { erro: 'A base de dados não tem meses suficientes. Atualize os dados.', meses };
  const cdi = new Float64Array(L), ipca = new Float64Array(L), fx = new Float64Array(L);
  meses.forEach((mi, j) => { cdi[j] = val(sc, mi); ipca[j] = val(si, mi); fx[j] = val(sf, mi); });
  const R = uni.map((t) => {
    const a = info(t), r = new Float64Array(L);
    if (a.tipo === 'rf') {
      for (let j = 0; j < L; j++) r[j] = a.rf === 'CDI' ? cdi[j] * a.taxa / 100 : a.rf === 'IPCA' ? (1 + ipca[j]) * Math.pow(1 + a.taxa / 100, 1 / 12) - 1 : Math.pow(1 + a.taxa / 100, 1 / 12) - 1;
    } else {
      const s = sr(t);
      for (let j = 0; j < L; j++) { const x = val(s, meses[j]); r[j] = isNaN(x) ? NaN : a.moeda === 'USD' && cambio ? (1 + x) * (1 + fx[j]) - 1 : x; }
    }
    return r;
  });
  const risco = uni.map((t) => info(t).tipo !== 'rf');
  const W = cs.map((c) => {
    const w = new Float64Array(uni.length), soma = c.itens.reduce((a, i) => a + i.p, 0);
    for (const i of c.itens) w[uni.indexOf(i.t)] += i.p / soma;
    return w;
  });
  // retorno mensal de cada carteira (pesos redistribuídos entre os ativos que existiam no mês);
  // num mês em que nenhum ativo dela existia, usa o índice de referência da classe de cada ativo
  const proxies = cs.map(() => ({ n: 0, de: null, ate: null, indices: new Set() }));
  const P = W.map((w, c) => {
    const p = new Float64Array(L);
    for (let j = 0; j < L; j++) {
      let s = 0, sw = 0;
      for (let k = 0; k < uni.length; k++) if (w[k] && !isNaN(R[k][j])) { s += w[k] * R[k][j]; sw += w[k]; }
      if (sw > 0) { p[j] = s / sw; continue; }
      let sp = 0;
      for (let k = 0; k < uni.length; k++) if (w[k]) { const [nome, r] = indiceClasse(uni[k], meses[j], cdi[j], fx[j], cambio); sp += w[k] * r; proxies[c].indices.add(nome); }
      p[j] = sp;
      const pr = proxies[c]; pr.n++; if (pr.de === null) pr.de = meses[j]; pr.ate = meses[j];
    }
    return p;
  });
  // por carteira: retorno de cada ativo com os meses faltantes preenchidos pelo retorno da própria carteira
  // (equivale a redistribuir o peso; usado no rebalanceamento por ativo)
  const Rc = redis ? W.map((w, c) => R.map((r, k) => (w[k] ? Float64Array.from(r, (x, j) => (isNaN(x) ? P[c][j] : x)) : null))) : null;
  const jovens = [];
  if (redis) for (const t of reais) { const s0 = sr(t).i0; if (s0 > meses[0]) { let falta = 0; for (const mi of meses) if (mi < s0) falta++; jovens.push({ t, desde: s0, falta }); } }
  jovens.sort((a, b) => b.desde - a.desde);
  const encerrados = [];
  if (redis) for (const t of reais) { const s1 = sr(t).i1; if (s1 < meses[L - 1]) encerrados.push({ t, ate: s1 }); }
  const estimados = {};
  if (estender) for (const t of reais) { const e = sr(t); if (e.estimados) { let n = 0; for (const mi of meses) if (mi < serie(t).i0) n++; if (n) estimados[t] = n; } }
  return {
    cs, uni, R, Rc, risco, W, P, meses, cdi, ipca, fx, L, ini: meses[0], fim: meses[L - 1], modo,
    limitante: meses[0] > desdeI ? limitante : null, estimados, jovens, encerrados,
    proxies: proxies.map((pr, c) => ({ nome: cs[c].nome, n: pr.n, de: pr.de, ate: pr.ate, indices: [...pr.indices] })).filter((x) => x.n),
  };
}
// índice de referência da classe (usado só nos meses em que nenhum ativo da carteira existia)
function indiceClasse(t, mi, cdi, fx, cambio) {
  const a = info(t);
  if (a.tipo === 'rf') return [a.n, a.rf === 'CDI' ? cdi * a.taxa / 100 : NaN];
  const ref = { fii: 'IFIX', fiagro: 'IFIX', fiinfra: 'IFIX', acao: 'IBOV', etf: 'IBOV', fundo_acoes: 'IBOV', acao_us: 'SPY', etf_us: 'SPY', cripto: 'BTC', fundo_cambial: 'USDBRL' }[a.tipo];
  if (ref) {
    const x = val(serie(ref), mi);
    if (!isNaN(x)) {
      const usd = info(ref) && info(ref).moeda === 'USD';
      return [{ IFIX: 'IFIX', IBOV: 'Ibovespa', SPY: 'S&P 500', BTC: 'Bitcoin', USDBRL: 'dólar' }[ref], usd && cambio ? (1 + x) * (1 + fx) - 1 : x];
    }
  }
  return ['CDI', cdi];
}
// avisos sobre trechos cobertos por índice de classe e troca automática de modo
function avisoProxies(H) {
  const a = [];
  if (H.encerrados && H.encerrados.length) a.push(`Ativos sem dados depois de certa data (fundo encerrado, ticker trocado ou sem negociação); saem da carteira nesse mês e o peso vai para os demais: ${H.encerrados.map((e) => `${esc(rotuloAtivo(e.t))} até ${rotulo(e.ate)}`).join(', ')}.`);
  if (H.trocouModo) a.push(`Os ativos tinham pouco histórico em comum para ${H.trocouModo === 'estimar' ? 'estimar por fatores' : 'cortar a janela'}; por isso cada ativo entra quando começa.`);
  for (const x of H.proxies || []) a.push(`${esc(x.nome)}: em ${x.n} ${x.n === 1 ? 'mês' : 'meses'} entre ${rotulo(x.de)} e ${rotulo(x.ate)} nenhum ativo da carteira tinha dados; esses meses usam o índice de referência da classe (${x.indices.join(', ')}).`);
  return a.join(' ');
}
// como cada carteira começa a janela: que parte dos pesos-alvo já existia (o resto foi redistribuído)
function inicioCarteiras(H) {
  if (!H.jovens || !H.jovens.length) return '';
  const partes = H.cs.map((c, ci) => {
    const disp = H.uni.map((t, k) => (H.W[ci][k] > 0 && !isNaN(H.R[k][0]) ? k : -1)).filter((k) => k >= 0);
    const soma = disp.reduce((a, k) => a + H.W[ci][k], 0);
    if (soma > 0.999 || soma === 0) return '';
    return `${esc(c.nome)} começa com ${pct(soma, 0)} dos pesos-alvo existindo (${disp.map((k) => esc(rotuloAtivo(H.uni[k]))).join(', ')}), que valem 100% da carteira até os outros ativos começarem`;
  }).filter(Boolean);
  return partes.length ? `Em ${rotulo(H.ini)}: ${partes.join('; ')}. Se esses primeiros ativos tiveram um período muito bom ou muito ruim, isso pesa no resultado; compare com "Cortar a janela".` : '';
}
// lista de ativos de uma carteira mais novos que o início pedido (para avisos)
function ativosNovos(itens, desde) {
  const d0 = idx(`${desde}-01`);
  return itens.filter((i) => !i.t.startsWith('RF:') && info(i.t) && serie(i.t).i0 > d0).map((i) => ({ t: i.t, desde: serie(i.t).i0 })).sort((a, b) => b.desde - a.desde);
}

// ---------------------------------------------------------------- acumuladores de distribuição
// Até EXATO cenários: guarda todos os valores (percentis exatos). Acima: histograma de NB faixas
// montado a partir dos primeiros PILOTO cenários; erro dos percentis bem menor que 0,1%.
const EXATO = 200000, PILOTO = 20000, NB = 20000;
// total = cenários da simulação inteira (somando todos os núcleos); faixa = [lo, hi] fixos do histograma (iguais em todos os núcleos)
function acumNovo(tipo, n, total = n, faixa = null) {
  const a = { tipo, n: 0, exato: total <= EXATO, v: new Float64Array(Math.min(n, EXATO)), h: null, lo: 0, hi: 0, zeros: 0, soma: 0, ord: null };
  if (!a.exato && faixa) { a.lo = faixa[0]; a.hi = faixa[1]; a.h = new Float64Array(NB); }
  return a;
}
function acumTf(a, x) { return a.tipo === 'log' ? Math.log(x) : a.tipo === 'log1p' ? Math.log1p(x) : x; }
function acumInv(a, y) { return a.tipo === 'log' ? Math.exp(y) : a.tipo === 'log1p' ? Math.expm1(y) : y; }
function acumBin(a, x) {
  if (a.tipo === 'log' && x <= 0) { a.zeros++; return; }
  const y = acumTf(a, x); let i = Math.floor((y - a.lo) / (a.hi - a.lo) * NB);
  if (i < 0) i = 0; else if (i >= NB) i = NB - 1;
  a.h[i]++;
}
function acumAdd(a, x) {
  a.soma += x;
  if (a.n < a.v.length) a.v[a.n] = x;
  a.n++;
  if (a.exato) return;
  if (a.h) { acumBin(a, x); return; }
  if (a.n === PILOTO) {
    let mn = Infinity, mx = -Infinity;
    for (let i = 0; i < PILOTO; i++) { const v = a.v[i]; if (a.tipo === 'log' && v <= 0) continue; const y = acumTf(a, v); if (y < mn) mn = y; if (y > mx) mx = y; }
    if (!isFinite(mn)) { mn = 0; mx = 1; }
    const span = Math.max(mx - mn, 1e-6);
    a.lo = mn - span * 0.6; a.hi = mx + span * 0.6;
    if (a.tipo === 'lin') { a.lo = Math.min(a.lo, -1); a.hi = Math.max(a.hi, 0); }
    a.h = new Float64Array(NB);
    for (let i = 0; i < PILOTO; i++) acumBin(a, a.v[i]);
  }
}
function acumFecha(a) { if (a.exato) a.ord = a.v.slice(0, a.n).sort(); return a; }
function acumQ(a, q) {
  if (a.exato) return quantil(a.ord, q);
  let alvo = q * (a.n - 1);
  if (alvo < a.zeros) return 0;
  alvo -= a.zeros;
  for (let i = 0; i < NB; i++) {
    if (alvo < a.h[i]) { const y = a.lo + (i + (alvo + 0.5) / a.h[i]) / NB * (a.hi - a.lo); return acumInv(a, y); }
    alvo -= a.h[i];
  }
  return acumInv(a, a.hi);
}

// ---------------------------------------------------------------- Monte Carlo
function simular(H, o, progresso) {
  const nC = H.cs.length, K = H.uni.length, L = H.L, N = o.anos * 12, nS = o.n;
  const nCam = Math.min(nS, o.nCam || 10000); // caminhos guardados para o gráfico do leque
  const total = o.nTotal || nS, fx = o.faixas || null;
  const ajM = Math.pow(1 + o.ajuste / 100, 1 / 12);
  const taxaM = Math.pow(1 + o.taxa / 100, -1 / 12);
  const parametrico = o.metodo === 'normal' || o.metodo === 't';
  const porAtivo = !parametrico && o.rebal !== 1;
  const ks = H.W.map((w) => { const l = []; for (let k = 0; k < K; k++) if (w[k] > 0) l.push(k); return l; });
  // retornos ajustados por carteira (ajuste só nos ativos de risco; meses faltantes já redistribuídos)
  const R2 = H.cs.map((_, c) => H.R.map((r, k) => {
    if (!H.W[c][k]) return null;
    const base = H.Rc ? H.Rc[c][k] : r;
    return H.risco[k] && o.ajuste ? base.map((x) => (1 + x) * ajM - 1) : base;
  }));
  const P2 = H.cs.map((_, c) => {
    const p = new Float64Array(L);
    for (const k of ks[c]) for (let j = 0; j < L; j++) p[j] += H.W[c][k] * R2[c][k][j];
    return p;
  });
  let mu, Lc, Dm = 1;
  if (parametrico) {
    const cols = [...P2, H.cdi, H.ipca]; Dm = cols.length;
    mu = cols.map((c) => media(c));
    const cov = new Float64Array(Dm * Dm);
    for (let a = 0; a < Dm; a++) for (let b = 0; b <= a; b++) {
      let s = 0; for (let j = 0; j < L; j++) s += (cols[a][j] - mu[a]) * (cols[b][j] - mu[b]);
      cov[a * Dm + b] = cov[b * Dm + a] = s / (L - 1);
    }
    Lc = cholesky(cov, Dm);
  }
  const passo = Math.max(1, Math.ceil(N / 120));
  const pontos = []; for (let m = 0; m <= N; m += passo) pontos.push(m);
  if (pontos[pontos.length - 1] !== N) pontos.push(N);
  const Pn = pontos.length;
  const cam = H.cs.map(() => new Float32Array(nCam * Pn));
  const somaCdi = new Float64Array(Pn), somaInv = new Float64Array(Pn);
  const aFin = H.cs.map((_, c) => acumNovo('log', nS, total, fx && fx.fin[c])), aAnu = H.cs.map((_, c) => acumNovo('log1p', nS, total, fx && fx.anu[c])),
    aQd = H.cs.map((_, c) => acumNovo('lin', nS, total, fx && fx.qd[c]));
  const aCdi = acumNovo('log', nS, total, fx && fx.cdi);
  const perde = new Float64Array(nC), bate = new Float64Array(nC), meta = new Float64Array(nC), ruin = new Float64Array(nC);
  let ruinCdi = 0;
  const mSaque = o.saqueOn ? Math.max(1, Math.round(o.saqueAno * 12)) : Infinity;
  const x = new Float64Array(Dm), z = new Float64Array(Dm);
  const hold = H.cs.map(() => new Float64Array(K));
  const V = new Float64Array(nC), nivel = new Float64Array(nC), topo = new Float64Array(nC), queda = new Float64Array(nC), cresce = new Float64Array(nC);
  const morto = new Int32Array(nC);
  const ir = o.ir / 100, lTaxa = Math.log(taxaM);
  const passoProg = Math.max(500, Math.min(20000, Math.floor(nS / 400)));

  for (let s = 0; s < nS; s++) {
    let vc = o.inicial, inf = 1, invNom = o.inicial, invReal = o.inicial, saqNom = 0, pos = 0, resta = 0, kk = 1, mortoCdi = 0;
    for (let c = 0; c < nC; c++) {
      V[c] = o.inicial; nivel[c] = 0; topo[c] = 0; queda[c] = 0; cresce[c] = 0; morto[c] = 0;
      if (porAtivo) for (const k of ks[c]) hold[c][k] = o.inicial * H.W[c][k];
    }
    const base = s * Pn, guarda = s < nCam;
    if (progresso && s % passoProg === 0) progresso(s / nS);
    if (guarda) for (let c = 0; c < nC; c++) cam[c][base] = V[c];
    somaCdi[0] += vc; somaInv[0] += o.inicial;
    for (let m = 1; m <= N; m++) {
      let rc, ri;
      if (parametrico) {
        let e = 1;
        if (o.metodo === 't') { let q = 0; for (let i = 0; i < 5; i++) { const g = gauss(); q += g * g; } e = Math.sqrt(3 / q); }
        for (let a = 0; a < Dm; a++) z[a] = gauss();
        for (let a = 0; a < Dm; a++) { let sum = 0; for (let b = 0; b <= a; b++) sum += Lc[a * Dm + b] * z[b]; x[a] = Math.max(-0.95, mu[a] + sum * e); }
        rc = x[nC]; ri = x[nC + 1];
      } else {
        if (resta === 0) { pos = (Math.random() * L) | 0; resta = o.metodo === 'bloco' ? o.bloco : 1; } else pos = (pos + 1) % L;
        resta--;
        rc = H.cdi[pos]; ri = H.ipca[pos];
      }
      inf *= 1 + ri;
      const retirando = m > mSaque;
      const ap = retirando ? 0 : o.aporte * (o.corrige ? inf : 1) * Math.pow(1 + o.reaj / 100, Math.floor((m - 1) / 12));
      const sq = retirando ? o.saque * (o.saqueCorrige ? inf : 1) : 0;
      invNom += ap; invReal += ap / inf; saqNom += sq;
      if (!mortoCdi) {
        vc = vc * (1 + rc) + ap;
        if (sq) { if (vc >= sq) vc -= sq; else { vc = 0; mortoCdi = m; } }
      }
      for (let c = 0; c < nC; c++) {
        if (morto[c]) continue;
        let r;
        if (porAtivo) {
          const h = hold[c], Rcc = R2[c]; let t0 = 0, t1 = 0;
          for (const k of ks[c]) { t0 += h[k]; h[k] *= 1 + Rcc[k][pos]; t1 += h[k]; }
          r = t0 > 0 ? t1 / t0 - 1 : 0;
          let tot = t1 * taxaM;
          for (const k of ks[c]) h[k] *= taxaM;
          if (ap) for (const k of ks[c]) h[k] += ap * H.W[c][k];
          tot += ap;
          if (sq) {
            if (tot >= sq) { const f = (tot - sq) / tot; for (const k of ks[c]) h[k] *= f; tot -= sq; }
            else { tot = 0; morto[c] = m; for (const k of ks[c]) h[k] = 0; }
          }
          if (o.rebal > 0 && m % o.rebal === 0 && tot > 0) for (const k of ks[c]) h[k] = tot * H.W[c][k];
          V[c] = tot;
        } else {
          r = parametrico ? x[c] : P2[c][pos];
          let v = V[c] * (1 + r) * taxaM + ap;
          if (sq) { if (v >= sq) v -= sq; else { v = 0; morto[c] = m; } }
          V[c] = v;
        }
        const lr = Math.log1p(r) + lTaxa;
        nivel[c] += lr; cresce[c] += lr - (o.real ? Math.log1p(ri) : 0);
        if (nivel[c] > topo[c]) topo[c] = nivel[c]; else if (nivel[c] - topo[c] < queda[c]) queda[c] = nivel[c] - topo[c];
      }
      if (m === pontos[kk]) {
        const d = o.real ? inf : 1;
        if (guarda) for (let c = 0; c < nC; c++) cam[c][base + kk] = V[c] / d;
        somaCdi[kk] += vc / d; somaInv[kk] += o.real ? invReal : invNom;
        kk++;
      }
    }
    const baseIR = Math.max(0, invNom - saqNom);
    const liq = (v) => (ir > 0 ? v - ir * Math.max(0, v - baseIR) : v);
    const d = o.real ? inf : 1, inv = o.real ? invReal : invNom, fc = liq(vc) / d;
    for (let c = 0; c < nC; c++) {
      const f = liq(V[c]) / d;
      acumAdd(aFin[c], f); acumAdd(aAnu[c], Math.exp(cresce[c] / N * 12) - 1); acumAdd(aQd[c], Math.expm1(queda[c]));
      if (f < inv) perde[c]++; if (f > fc) bate[c]++; if (o.meta > 0 && f >= o.meta) meta[c]++; if (morto[c]) ruin[c]++;
    }
    acumAdd(aCdi, fc); if (mortoCdi) ruinCdi++;
  }
  // parte bruta (cada núcleo devolve a sua; combinar() junta tudo)
  return {
    nomes: H.cs.map((c) => c.nome), cam, nCam, Pn, pontos, N, nS, aFin, aAnu, aQd, aCdi,
    perde: Array.from(perde), bate: Array.from(bate), meta: Array.from(meta), ruin: Array.from(ruin), ruinCdi,
    somaCdi, somaInv, porAtivo, parametrico, temMeta: o.meta > 0,
  };
}

// ---------------------------------------------------------------- juntar os núcleos
function acumUnir(lista) {
  const a0 = lista[0], n = lista.reduce((s, a) => s + a.n, 0), soma = lista.reduce((s, a) => s + a.soma, 0);
  const amostra = Math.min(n, EXATO), v = new Float64Array(amostra);
  let k = 0;
  for (const a of lista) { const m = Math.min(a.n, a.v.length, amostra - k); if (m > 0) { v.set(a.v.subarray(0, m), k); k += m; } }
  const u = { tipo: a0.tipo, n, soma, exato: lista.every((a) => a.exato), v: v.subarray(0, k), h: null, lo: a0.lo, hi: a0.hi, zeros: 0, ord: null };
  if (!u.exato) { u.h = new Float64Array(NB); for (const a of lista) { for (let i = 0; i < NB; i++) u.h[i] += a.h[i]; u.zeros += a.zeros; } }
  return acumFecha(u);
}
function faixaDe(a) { // faixa do histograma a partir dos valores de uma rodada-piloto
  let mn = Infinity, mx = -Infinity;
  for (let i = 0; i < Math.min(a.n, a.v.length); i++) { const v = a.v[i]; if (a.tipo === 'log' && v <= 0) continue; const y = acumTf(a, v); if (y < mn) mn = y; if (y > mx) mx = y; }
  if (!isFinite(mn)) { mn = 0; mx = 1; }
  const span = Math.max(mx - mn, 1e-6); let lo = mn - span * 0.6, hi = mx + span * 0.6;
  if (a.tipo === 'lin') { lo = Math.min(lo, -1); hi = Math.max(hi, 0); }
  return [lo, hi];
}
function combinar(partes) {
  const p0 = partes[0], nC = p0.nomes.length, Pn = p0.Pn, nS = partes.reduce((s, p) => s + p.nS, 0);
  const nCam = partes.reduce((s, p) => s + p.nCam, 0);
  const QS = [0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 0.95];
  const somaCdi = new Float64Array(Pn), somaInv = new Float64Array(Pn);
  for (const p of partes) for (let k = 0; k < Pn; k++) { somaCdi[k] += p.somaCdi[k]; somaInv[k] += p.somaInv[k]; }
  const col = new Float32Array(nCam);
  const res = p0.nomes.map((nome, c) => {
    const cam = new Float32Array(nCam * Pn); let off = 0;
    for (const p of partes) { cam.set(p.cam[c], off); off += p.nCam * Pn; }
    const faixas = QS.map(() => new Float64Array(Pn));
    for (let k = 0; k < Pn; k++) {
      for (let s = 0; s < nCam; s++) col[s] = cam[s * Pn + k];
      col.sort();
      QS.forEach((q, i) => { faixas[i][k] = quantil(col, q); });
    }
    const soma = (f) => partes.reduce((s, p) => s + p[f][c], 0) / nS;
    return {
      nome, faixas, cam, aFin: acumUnir(partes.map((p) => p.aFin[c])), aAnu: acumUnir(partes.map((p) => p.aAnu[c])), aQd: acumUnir(partes.map((p) => p.aQd[c])),
      pPerde: soma('perde'), pCdi: soma('bate'), pMeta: p0.temMeta ? soma('meta') : null, pRuina: soma('ruin'),
    };
  });
  return {
    res, N: p0.N, Pn, pontos: p0.pontos, nS, nCam, QS, cdi: Array.from(somaCdi, (v) => v / nS), inv: Array.from(somaInv, (v) => v / nS),
    aCdi: acumUnir(partes.map((p) => p.aCdi)), pRuinaCdi: partes.reduce((s, p) => s + p.ruinCdi, 0) / nS,
    porAtivo: p0.porAtivo, parametrico: p0.parametrico, exato: nS <= EXATO, nucleos: partes.length,
  };
}
// funções de leitura (não viajam do worker)
function hidratar(R) {
  for (const r of R.res) {
    r.q = (p) => acumQ(r.aFin, p); r.qa = (p) => acumQ(r.aAnu, p); r.qd = (p) => acumQ(r.aQd, p);
    r.fin = r.aFin.v.subarray(0, Math.min(r.aFin.n, r.aFin.v.length)); // amostra (até 200 mil) para o histograma
  }
  R.cdiQ = (p) => acumQ(R.aCdi, p);
  return R;
}

// Monte Carlo em vários Web Workers, um por núcleo do processador (a tela continua respondendo e dá
// para parar). Sem Worker, roda direto numa parte só.
const NUCLEOS = Math.max(1, Math.min(32, (typeof navigator !== 'undefined' && navigator.hardwareConcurrency) || 4));
let workerURL = null, cancelar = null, semWorker = false;
function criarWorker() {
  try {
    if (!workerURL) {
      const src = [quantil, media, cholesky, gauss, acumNovo, acumTf, acumInv, acumBin, acumAdd, acumFecha, simular].map((f) => f.toString()).join('\n') +
        `\nconst EXATO = ${EXATO}, PILOTO = ${PILOTO}, NB = ${NB};\nlet gaussExtra = null;\nonmessage = (e) => { const { H, o } = e.data; const R = simular(H, o, (p) => postMessage({ p })); postMessage({ R }); };`;
      workerURL = URL.createObjectURL(new Blob([src], { type: 'text/javascript' }));
    }
    return new Worker(workerURL);
  } catch (e) { return null; }
}
function rodarPartes(Hw, lista, aoProgresso) {
  // lista: [{ o, peso }] — cada item vira um worker; o progresso é a média ponderada
  return new Promise((ok, falha) => {
    const ws = [], prog = lista.map(() => 0), out = new Array(lista.length);
    let restam = lista.length, acabou = false;
    const fim = (erro) => { if (acabou) return; acabou = true; ws.forEach((w) => w && w.terminate()); cancelar = null; erro ? falha(erro) : ok(out); };
    cancelar = () => fim(new Error('cancelado'));
    lista.forEach((item, i) => {
      const w = semWorker ? null : criarWorker();
      if (!w) { semWorker = true; }
      ws.push(w);
      if (!w) {
        setTimeout(() => { try { out[i] = simular(Hw, item.o, (p) => { prog[i] = p; aoProgresso(prog.reduce((s, x, k) => s + x * lista[k].peso, 0)); }); if (--restam === 0) fim(); } catch (e) { fim(e); } }, 20);
        return;
      }
      w.onmessage = (e) => {
        if (e.data.p !== undefined) { prog[i] = e.data.p; aoProgresso(prog.reduce((s, x, k) => s + x * lista[k].peso, 0)); return; }
        out[i] = e.data.R; if (--restam === 0) fim();
      };
      w.onerror = (e) => { e.preventDefault(); fim(new Error(e.message || 'erro no núcleo de cálculo')); };
      w.postMessage({ H: Hw, o: item.o });
    });
  });
}
async function simularAssincrono(H, o, aoProgresso) {
  const Hw = { cs: H.cs.map((c) => ({ nome: c.nome })), uni: H.uni, R: H.R, Rc: H.Rc, risco: H.risco, W: H.W, cdi: H.cdi, ipca: H.ipca, L: H.L };
  const nW = o.n >= 20000 ? NUCLEOS : 1;
  let faixas = null, pesoPiloto = 0;
  if (o.n > EXATO && nW > 1) {
    // rodada-piloto curta só para fixar as faixas dos histogramas (iguais em todos os núcleos)
    pesoPiloto = PILOTO / o.n;
    const [pil] = await rodarPartes(Hw, [{ o: { ...o, n: PILOTO, nCam: 1 }, peso: pesoPiloto }], aoProgresso);
    faixas = { fin: pil.aFin.map(faixaDe), anu: pil.aAnu.map(faixaDe), qd: pil.aQd.map(faixaDe), cdi: faixaDe(pil.aCdi) };
  }
  const base = Math.floor(o.n / nW), extra = o.n % nW, camPor = Math.ceil(10000 / nW);
  const lista = Array.from({ length: nW }, (_, i) => {
    const n = base + (i < extra ? 1 : 0);
    return { o: { ...o, n, nTotal: o.n, faixas, nCam: camPor }, peso: (1 - pesoPiloto) * n / o.n };
  }).filter((x) => x.o.n > 0);
  const partes = await rodarPartes(Hw, lista, (p) => aoProgresso(pesoPiloto + p));
  return combinar(partes);
}

// ---------------------------------------------------------------- navegação (abas)
const VIEWS = ['carteiras', 'simulacao', 'historico', 'analise', 'otimizar', 'metodo'];
let vista = '';
function irPara(v, push = true) {
  if (!VIEWS.includes(v)) v = 'carteiras';
  vista = v;
  VIEWS.forEach((x) => { $(`v-${x}`).classList.toggle('on', x === v); });
  document.querySelectorAll('.tabs button').forEach((b) => b.setAttribute('aria-selected', b.dataset.v === v));
  if (push && location.hash !== `#${v}`) { try { history.replaceState(null, '', `#${v}`); } catch (e) { /* ignora */ } }
  if (v === 'simulacao') { if (!ultimo) rodar(false); else desenharSimulacao(); }
  if (v === 'historico') desenharHistorico();
  if (v === 'analise') desenharAnalise();
  if (v === 'carteiras') desenharResumo();
  if (v === 'otimizar') prepararOtimizador();
  window.scrollTo(0, 0);
}
document.querySelector('.tabs').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) irPara(b.dataset.v); });
window.addEventListener('hashchange', () => irPara(location.hash.slice(1), false));

// ---------------------------------------------------------------- CARTEIRAS
function desenharAbasCarteiras() {
  $('cart-tabs').innerHTML = S.carteiras.map((c, i) =>
    `<button type="button" class="cart-tab${i === S.ativa ? ' on' : ''}" data-i="${i}" aria-pressed="${i === S.ativa}"><span class="dot" style="background:${CORES_CART[i]}"></span><b>${LETRAS[i]}</b><span class="nm2">${esc(c.nome)}</span></button>`).join('') +
    '<button type="button" class="cart-tab add" data-novo="1">+ Nova carteira</button>';
  if (document.activeElement !== $('cart-nome')) $('cart-nome').value = cartAtiva().nome;
  $('btn-del').disabled = S.carteiras.length < 2;
  $('btn-dup').disabled = false;
}
function desenharCarteira() {
  const c = cartAtiva();
  desenharAbasCarteiras();
  const d0 = idx(`${desdeDe(S.janela)}-01`);
  $('holdings').innerHTML = c.itens.length ? `<thead><tr><th>Ativo</th><th class="hide-s">Classe</th><th>Desde</th><th>Peso</th><th></th></tr></thead><tbody>` +
    c.itens.map((it, i) => {
      const a = info(it.t), cl = TIPOS[a.tipo], fundo = a.tipo === 'rf' || a.tipo.startsWith('fundo');
      const i0 = a.tipo === 'rf' ? serie('CDI').i0 : serie(it.t).i0, novo = i0 > d0;
      return `<tr><td class="t"><span class="tk">${esc(fundo ? a.n.slice(0, 30) : a.t)}</span><span class="nm">${esc(a.tipo === 'rf' ? 'Renda fixa por modelo' : fundo ? (a.gestor || a.setor) : a.n)}</span></td>
        <td class="hide-s"><span class="cls"><i class="sw" style="background:${corClasse[cl[1]]}"></i>${cl[0]}</span></td>
        <td><span class="desde${novo ? ' novo' : ''}" title="${novo ? 'Mais novo que a janela escolhida' : 'Cobre a janela escolhida'}">${novo ? '⚠ ' : ''}${Math.floor(i0 / 12)}</span></td>
        <td><label class="w"><input type="number" inputmode="decimal" min="0" max="100" step="1" data-i="${i}" value="${+it.p.toFixed(2)}" aria-label="Peso de ${esc(a.n)} em %"><span>%</span></label></td>
        <td><button class="rm" type="button" data-rm="${i}" aria-label="Remover ${esc(a.n)}"><svg class="ic" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg></button></td></tr>`;
    }).join('') + '</tbody>'
    : '<tbody><tr><td class="empty">Carteira vazia. Escolha um modelo pronto ou adicione ativos.</td></tr></tbody>';
  const novos = ativosNovos(c.itens, desdeDe(S.janela));
  $('cart-aviso').hidden = !novos.length;
  $('cart-aviso').innerHTML = novos.length ? `<b>Atenção:</b> ${novos.length === 1 ? 'este ativo é mais novo' : 'estes ativos são mais novos'} que a janela de ${S.janela} anos (desde ${desdeDe(S.janela)}): ${novos.map((n) => `${esc(rotuloAtivo(n.t))} (desde ${rotulo(n.desde)})`).join(', ')}. ` +
    ({ redistribuir: 'Na simulação e no histórico, a contagem começa no ativo mais antigo e eles entram quando passam a existir; antes disso, o peso deles é dividido entre os outros.', cortar: 'Com "Cortar a janela", a contagem começa no mais novo deles.', estimar: 'Com "Estimar por fatores", os anos que faltam são estimados (não são dados reais).' }[S.curto]) : '';
  desenharSoma();
  desenharPresets();
}
function desenharSoma() {
  const c = cartAtiva(), soma = c.itens.reduce((a, i) => a + i.p, 0), ok = Math.abs(soma - 100) < 0.5;
  $('sum').innerHTML = !c.itens.length ? '' :
    `<span>${c.itens.length} ${c.itens.length === 1 ? 'ativo' : 'ativos'} · soma dos pesos <span class="v ${ok ? 'ok' : 'bad'}">${soma.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</span></span>` +
    (!ok && soma > 0 ? '<button class="link" type="button" id="btn-fix">Normalizar para 100%</button>' : '');
  const por = {};
  for (const i of c.itens) { const k = TIPOS[info(i.t).tipo][1]; por[k] = (por[k] || 0) + i.p / (soma || 1); }
  const cls = CLASSES.filter(([k]) => por[k] > 0);
  $('alloc').innerHTML = cls.map(([k, , cor]) => `<i style="width:${(por[k] * 100).toFixed(2)}%;background:${cor}"></i>`).join('');
  $('alloc-leg').innerHTML = cls.map(([k, n, cor]) => `<span><i class="sw" style="background:${cor}"></i>${n} <b>${pct(por[k], 0)}</b></span>`).join('');
}
function presetCarteira(p) {
  if (p.c) return p.c;
  // os 5 maiores multimercados com 5+ anos de histórico
  const l = D.ativos.filter((a) => a[2] === 'fundo_mm' && serie(a[0]).v.length >= 60).slice(0, 5);
  return Object.fromEntries(l.map((a) => [a[0], 20]));
}
function desenharPresets() {
  const atual = JSON.stringify(Object.fromEntries(cartAtiva().itens.map((i) => [i.t, i.p])));
  $('presets').innerHTML = PRESETS.map((p, i) => {
    const on = JSON.stringify(presetCarteira(p)) === atual;
    return `<button type="button" class="preset${on ? ' on' : ''}" data-preset="${i}" aria-pressed="${on}">${esc(p.nome)}</button>`;
  }).join('');
}
function mudouCarteiras() { salvar(); marcarVelho(); agendarResumo(); }

$('cart-tabs').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  if (b.dataset.novo) { S.carteiras.push({ nome: `Carteira ${LETRAS[S.carteiras.length]}`, itens: [] }); S.ativa = S.carteiras.length - 1; mudouCarteiras(); }
  else S.ativa = +b.dataset.i;
  salvar(); desenharCarteira();
});
$('cart-nome').addEventListener('input', () => { cartAtiva().nome = $('cart-nome').value.trim() || `Carteira ${LETRAS[S.ativa]}`; desenharAbasCarteiras(); mudouCarteiras(); });
$('presets').addEventListener('click', (e) => {
  const b = e.target.closest('[data-preset]'); if (!b) return;
  const p = PRESETS[+b.dataset.preset];
  cartAtiva().itens = Object.entries(presetCarteira(p)).filter(([t]) => info(t)).map(([t, w]) => ({ t, p: w }));
  cartAtiva().nome = p.nome;
  $('cart-nome').value = p.nome;
  mudouCarteiras(); desenharCarteira();
});
$('holdings').addEventListener('input', (e) => {
  const i = e.target.dataset.i; if (i === undefined) return;
  const v = lerDec(e.target.value);
  cartAtiva().itens[+i].p = v > 0 ? Math.min(v, 100) : 0;
  mudouCarteiras(); desenharSoma(); desenharPresets();
});
$('holdings').addEventListener('click', (e) => {
  const b = e.target.closest('[data-rm]'); if (!b) return;
  cartAtiva().itens.splice(+b.dataset.rm, 1); mudouCarteiras(); desenharCarteira();
});
$('sum').addEventListener('click', (e) => {
  if (e.target.id !== 'btn-fix') return;
  const it = cartAtiva().itens, soma = it.reduce((a, i) => a + i.p, 0);
  it.forEach((i) => { i.p = Math.round(i.p / soma * 1000) / 10; }); fecha100(it); mudouCarteiras(); desenharCarteira();
});
$('btn-equal').addEventListener('click', () => {
  const it = cartAtiva().itens; if (!it.length) return;
  it.forEach((i) => { i.p = Math.floor(1000 / it.length) / 10; }); fecha100(it); mudouCarteiras(); desenharCarteira();
});
$('btn-dup').addEventListener('click', () => {
  const c = JSON.parse(JSON.stringify(cartAtiva())); c.nome = `${c.nome} (cópia)`.slice(0, 28);
  S.carteiras.push(c); S.ativa = S.carteiras.length - 1; mudouCarteiras(); desenharCarteira();
});
let confirmaDel = 0;
$('btn-del').addEventListener('click', () => {
  if (S.carteiras.length < 2) return;
  if (Date.now() - confirmaDel > 3000) { confirmaDel = Date.now(); $('btn-del').textContent = 'Toque de novo'; setTimeout(() => { $('btn-del').textContent = 'Excluir'; }, 3000); return; }
  S.carteiras.splice(S.ativa, 1); S.ativa = Math.max(0, S.ativa - 1); S.ver = 0; S.an = 0;
  $('btn-del').textContent = 'Excluir'; mudouCarteiras(); desenharCarteira();
});
function fecha100(it) { const soma = it.reduce((a, i) => a + i.p, 0); if (it.length) it[0].p = Math.round((it[0].p + 100 - soma) * 10) / 10; }

let resumoT;
function agendarResumo() { clearTimeout(resumoT); resumoT = setTimeout(desenharResumo, 250); }
function desenharResumo() {
  const linhas = S.carteiras.map((c, i) => {
    const H = preparar([c], Math.floor(BASE / 12), 1, S.curto === 'estimar' ? 'cortar' : S.curto);
    if (H.erro) return `<tr><td class="t"><span class="pdot" style="background:${CORES_CART[i]}"></span>${esc(c.nome)}</td><td colspan="6" class="t" style="color:var(--muted);white-space:normal">${esc(H.erro)}</td></tr>`;
    const m = metricas(Array.from(H.P[0]), Array.from(H.cdi));
    return `<tr><td class="t"><span class="pdot" style="background:${CORES_CART[i]}"></span>${esc(c.nome)}</td><td class="${m.cagr < 0 ? 'neg' : ''}">${pct(m.cagr)}</td><td>${pct(m.vol)}</td><td>${num(m.sharpe)}</td><td class="neg">${pct(m.maxdd, 0)}</td><td>${rotulo(H.ini)}</td><td>${H.L}</td></tr>`;
  });
  $('tbl-resumo').innerHTML = `<thead><tr><th>Carteira</th><th>Retorno ao ano</th><th>Volatilidade</th><th>Sharpe (retorno por risco)</th><th>Queda máxima</th><th>Desde</th><th>Meses</th></tr></thead><tbody>${linhas.join('')}</tbody>`;
  $('resumo-meta').textContent = `até ${rotulo(FIM_DADOS)}`;
}
function desenharBase() {
  const cont = {};
  for (const a of D.ativos) cont[a[2]] = (cont[a[2]] || 0) + 1;
  const linhas = [
    ['Ações da B3', cont.acao], ['Fundos imobiliários, Fiagro e FI-Infra', (cont.fii || 0) + (cont.fiagro || 0) + (cont.fiinfra || 0)], ['ETFs da B3', cont.etf],
    ['Fundos de renda fixa (CVM)', cont.fundo_rf], ['Multimercados (CVM)', cont.fundo_mm], ['Fundos de ações e cambiais (CVM)', (cont.fundo_acoes || 0) + (cont.fundo_cambial || 0)],
    ['Ações dos EUA', cont.acao_us], ['ETFs dos EUA', cont.etf_us], ['Criptoativos', cont.cripto], ['Índices, ouro, petróleo e moedas', (cont.indice || 0) + (cont.commodity || 0) + (cont.moeda || 0)],
  ];
  $('tbl-base').innerHTML = `<tbody>${linhas.map(([n, v]) => `<tr><td class="t">${n}</td><td>${(v || 0).toLocaleString('pt-BR')}</td></tr>`).join('')}<tr class="tot"><td class="t">Total</td><td>${D.ativos.length.toLocaleString('pt-BR')}</td></tr></tbody>`;
  const [y, m, d] = D.atualizado.split('-');
  $('base-help').textContent = `Retornos mensais de ${rotulo(BASE)} a ${rotulo(FIM_DADOS)} (baixados em ${d}/${m}/${y}). Mais a renda fixa por modelo: CDI × %, IPCA + taxa e prefixado.`;
}

// ---------------------------------------------------------------- seletor de ativos
let filtro = '', limite = 150;
$('chips-tipo').innerHTML = GRUPOS.map(([k, n], i) => {
  const qtd = k === 'rf' ? 3 : [...ATIVOS.values()].filter((a) => noGrupo(a, k)).length;
  return `<button type="button" class="chip${i === 0 ? ' on' : ''}" data-k="${k}">${n}<small>${qtd.toLocaleString('pt-BR')}</small></button>`;
}).join('');
$('btn-add').addEventListener('click', () => { $('sheet').hidden = false; $('q').value = ''; limite = 150; desenharLista(); document.body.style.overflow = 'hidden'; });
function fecharLista() { $('sheet').hidden = true; document.body.style.overflow = ''; desenharCarteira(); }
$('sheet-ok').addEventListener('click', fecharLista);
$('sheet').addEventListener('click', (e) => { if (e.target.id === 'sheet') fecharLista(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && !$('sheet').hidden) fecharLista(); });
let qT; $('q').addEventListener('input', () => { clearTimeout(qT); qT = setTimeout(() => { limite = 150; desenharLista(); }, 120); });
$('sel-ordem').addEventListener('change', () => { limite = 150; desenharLista(); });
$('btn-more').addEventListener('click', () => { limite += 300; desenharLista(); });
$('chips-tipo').addEventListener('click', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  filtro = b.dataset.k; limite = 150;
  [...$('chips-tipo').children].forEach((x) => x.classList.toggle('on', x === b));
  desenharLista();
});
$('rf-box').addEventListener('click', (e) => {
  const b = e.target.closest('[data-rf]'); if (!b) return;
  const tipo = b.dataset.rf, campo = { CDI: 'rf-cdi', IPCA: 'rf-ipca', PRE: 'rf-pre' }[tipo];
  const v = Math.round(lerDec($(campo).value) * 10) / 10;
  if (!(v > 0 && v < 500)) return;
  const t = `RF:${tipo}:${v}`;
  if (!cartAtiva().itens.some((i) => i.t === t)) cartAtiva().itens.push({ t, p: cartAtiva().itens.length ? 10 : 100 });
  b.textContent = 'Adicionado ✓'; setTimeout(() => { b.textContent = 'Adicionar'; }, 1200);
  mudouCarteiras();
});
$('list').addEventListener('click', (e) => {
  const tr = e.target.closest('[data-t]'); if (!tr) return;
  const t = tr.dataset.t, it = cartAtiva().itens, i = it.findIndex((x) => x.t === t);
  if (i >= 0) it.splice(i, 1); else it.push({ t, p: it.length ? 10 : 100 });
  const on = i < 0;
  tr.classList.toggle('sel', on); tr.setAttribute('aria-checked', on);
  mudouCarteiras();
});
$('list').addEventListener('keydown', (e) => { if ((e.key === 'Enter' || e.key === ' ') && e.target.dataset.t) { e.preventDefault(); e.target.click(); } });
function tamanhoTexto(a) {
  const f = FII[a.t];
  if (f) {
    const m = { papel: 'papel', tijolo: 'tijolo', hibrido: 'híbrido', desenvolvimento: 'desenvolvimento' }[f[0]] || '';
    return [m && `mandato ${m}`, f[3] ? `P/VP (preço ÷ valor patrimonial) ${num(f[3])}` : '', f[4] ? `${f[4].toLocaleString('pt-BR')} cotistas` : '', a.tam ? `negocia ${curto(a.tam)}/dia` : ''].filter(Boolean).join(' · ');
  }
  if (!a.tam) return '';
  if (a.tipo.startsWith('fundo')) return `PL (patrimônio líquido) ${curto(a.tam)} · ${a.cot.toLocaleString('pt-BR')} cotistas`;
  if (a.tipo === 'acao_us') return `valor de mercado US$ ${(a.tam / 1e9).toLocaleString('pt-BR', { maximumFractionDigits: 0 })} bi`;
  return `negocia ${a.moeda === 'USD' ? 'US$ ' + (a.tam / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 0 }) + ' mi' : curto(a.tam)}/dia`;
}
function desenharLista() {
  $('rf-box').hidden = filtro !== 'rf';
  if (filtro === 'rf') { $('list').innerHTML = ''; $('btn-more').hidden = true; $('list-count').textContent = ''; return; }
  const q = semAcento($('q').value.trim());
  const sel = new Set(cartAtiva().itens.map((i) => i.t));
  const itens = [];
  for (const a of ATIVOS.values()) {
    if (filtro && !noGrupo(a, filtro)) continue;
    if (q && !semAcento(`${a.t} ${a.n} ${a.setor} ${a.gestor}`).includes(q)) continue;
    itens.push(a);
  }
  const ordem = $('sel-ordem').value;
  if (ordem !== 'tam') {
    const chave = { ret: (a) => -estatAtivo(a.t).anual, vol: (a) => estatAtivo(a.t).vol, sharpe: (a) => -estatAtivo(a.t).sharpe, hist: (a) => -estatAtivo(a.t).n }[ordem];
    if (chave) itens.sort((x, y) => { const a = chave(x), b = chave(y); return (isNaN(a) ? 1e9 : a) - (isNaN(b) ? 1e9 : b); });
    else itens.sort((x, y) => x.t.localeCompare(y.t));
  }
  if (q) itens.sort((x, y) => (semAcento(y.t) === q) - (semAcento(x.t) === q) || (semAcento(y.t).startsWith(q) - semAcento(x.t).startsWith(q)));
  $('list-count').textContent = `${itens.length.toLocaleString('pt-BR')} ativos`;
  const mostra = itens.slice(0, limite);
  $('btn-more').hidden = itens.length <= limite;
  $('list').innerHTML = `<thead><tr><th>Ativo</th><th class="hide-s">Classe · setor</th><th>Retorno ao ano</th><th>Volatilidade</th><th>Desde</th></tr></thead><tbody>` +
    (mostra.map((a) => {
      const e = estatAtivo(a.t), on = sel.has(a.t), cl = TIPOS[a.tipo], fundo = a.tipo.startsWith('fundo');
      return `<tr class="${on ? 'sel' : ''}" data-t="${esc(a.t)}" tabindex="0" role="checkbox" aria-checked="${on}">
        <td class="t"><span class="ck"><svg viewBox="0 0 24 24"><path d="M5 12l5 5 9-10"/></svg></span><span class="tk">${esc(fundo ? a.n.slice(0, 34) : a.t)}</span><span class="nm" style="padding-left:24px;max-width:230px">${esc(fundo ? (a.gestor || a.setor) : a.n)}</span><span class="sub2" style="padding-left:24px">${esc(tamanhoTexto(a))}</span></td>
        <td class="t hide-s"><span class="cls"><i class="sw" style="background:${corClasse[cl[1]]}"></i>${cl[0]}</span><span class="nm">${esc(a.setor)}</span></td>
        <td class="${e.anual < 0 ? 'neg' : ''}">${pct(e.anual)}</td><td>${pct(e.vol, 0)}</td><td>${e.desde}</td></tr>`;
    }).join('') || '<tr><td colspan="5" class="empty">Nenhum ativo encontrado.</td></tr>') + '</tbody>';
}

// ---------------------------------------------------------------- SIMULAÇÃO: controles
function ligarDinheiro(id, chave) {
  const el = $(id);
  el.value = S[chave] ? S[chave].toLocaleString('pt-BR') : '';
  el.addEventListener('input', () => {
    const n = parseInt(el.value.replace(/\D/g, ''), 10) || 0;
    S[chave] = Math.min(n, 1e12); el.value = n ? S[chave].toLocaleString('pt-BR') : ''; mudouParam();
  });
}
function ligarDecimal(id, chave, min, max) {
  const el = $(id);
  el.value = String(S[chave]).replace('.', ',');
  el.addEventListener('input', () => { S[chave] = Math.max(min, Math.min(max, lerDec(el.value))); mudouParam(); });
}
function ligarSeg(id, chave, aoMudar) {
  const seg = $(id);
  const marca = () => [...seg.children].forEach((b) => {
    const v = isNaN(+b.dataset.v) ? b.dataset.v : +b.dataset.v, on = v === S[chave];
    b.classList.toggle('on', on); b.setAttribute('aria-pressed', on);
  });
  marca();
  seg.addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    S[chave] = isNaN(+b.dataset.v) ? b.dataset.v : +b.dataset.v; marca(); salvar(); (aoMudar || mudouParam)();
  });
}
function ligarCheck(id, chave, aoMudar) {
  $(id).checked = !!S[chave];
  $(id).addEventListener('change', () => { S[chave] = $(id).checked; salvar(); (aoMudar || mudouParam)(); });
}
function mudouParam() { salvar(); marcarVelho(); atualizarAjudas(); }
function marcarVelho() { if (ultimo) $('stale').hidden = false; }

ligarDinheiro('in-inicial', 'inicial'); ligarDinheiro('in-aporte', 'aporte'); ligarDinheiro('in-meta', 'meta'); ligarDinheiro('in-saque', 'saque');
ligarDecimal('in-reaj', 'reaj', -10, 20); ligarDecimal('in-taxa', 'taxa', 0, 10); ligarDecimal('in-ir', 'ir', 0, 50);
ligarDecimal('in-saque-ano', 'saqueAno', 0, 50);
ligarCheck('in-corrige', 'corrige'); ligarCheck('in-real', 'real'); ligarCheck('in-saque-corrige', 'saqueCorrige'); ligarCheck('in-saque-on', 'saqueOn');
ligarSeg('seg-metodo', 'metodo'); ligarSeg('seg-bloco', 'bloco'); ligarSeg('seg-janela', 'janela', () => { mudouParam(); desenharCarteira(); }); ligarSeg('seg-curto', 'curto', () => { mudouParam(); desenharCarteira(); agendarResumo(); }); ligarSeg('seg-rebal', 'rebal');
ligarSeg('seg-cambio', 'cambio'); ligarSeg('seg-n', 'n');
$('in-anos').value = S.anos; $('in-ajuste').value = S.ajuste;
// janela em anos -> ano inicial (os dados começam em 1995; a contagem nunca vai antes do ativo mais antigo)
const desdeDe = (anos) => Math.max(Math.floor(BASE / 12), Math.floor(FIM_DADOS / 12) - anos + 1);
const NOME_CURTO = { redistribuir: 'ativos novos entram quando começam', cortar: 'janela cortada no ativo mais novo', estimar: 'anos faltantes estimados por fatores' };
let velocidade = 0; // cenários × meses × carteiras por ms (medido na última simulação)
const mostraAnos = () => { $('out-anos').textContent = `${S.anos} ${S.anos === 1 ? 'ano' : 'anos'}`; };
const mostraAjuste = () => { $('out-ajuste').textContent = `${S.ajuste > 0 ? '+' : ''}${num(S.ajuste, 1)} p.p.`; };
mostraAnos(); mostraAjuste();
$('in-anos').addEventListener('input', () => { S.anos = +$('in-anos').value; mostraAnos(); mudouParam(); });
$('in-ajuste').addEventListener('input', () => { S.ajuste = +$('in-ajuste').value; mostraAjuste(); mudouParam(); });
function atualizarAjudas() {
  const txt = {
    boot: 'Sorteia meses reais do passado, um por vez. Mantém as caudas e as correlações reais.',
    bloco: 'Sorteia trechos seguidos do passado. Preserva crises longas, tendências e a memória dos juros.',
    normal: 'Distribuição normal com média e covariância históricas. Gera cenários que nunca aconteceram, mas subestima eventos extremos.',
    t: 'Como a normal, mas com caudas gordas (t-Student, 5 graus de liberdade): mais meses muito bons e muito ruins.',
  };
  $('metodo-help').textContent = txt[S.metodo];
  $('f-bloco').classList.toggle('dim', S.metodo !== 'bloco');
  const param = S.metodo === 'normal' || S.metodo === 't';
  $('seg-rebal').classList.toggle('dim', param);
  $('rebal-help').textContent = param ? 'Nos modelos Normal e t-Student a carteira é rebalanceada todo mês.' : S.rebal === 0 ? 'Os pesos derivam com o mercado; os aportes seguem os pesos-alvo.' : '';
  $('saque-body').classList.toggle('dim', !S.saqueOn);
  const d0 = desdeDe(S.janela);
  $('janela-help').textContent = `Usa o passado desde ${d0}${d0 > Math.floor(FIM_DADOS / 12) - S.janela + 1 ? ' (os dados começam em 1995, início do Plano Real)' : ''}. Se nenhum ativo da carteira for tão antigo, a contagem começa no ativo mais antigo.`;
  $('curto-help').textContent = {
    redistribuir: 'A contagem começa no ativo mais antigo da carteira. Um ativo mais novo entra no mês em que passa a existir; antes disso, o peso dele é dividido entre os outros ativos. Assim você vê a performance de todos.',
    cortar: 'Só usa os meses em que todos os ativos existiam: a janela começa no ativo mais novo (mais curta, mas sem redistribuição).',
    estimar: 'Estima os anos que faltam de cada ativo por modelo de fatores (IFIX, CDI, IPCA, Ibovespa, S&P). Os meses estimados não são dados reais.',
  }[S.curto];
  const carga = S.n * S.anos * 12 * Math.max(1, S.carteiras.length), ms = velocidade ? carga / velocidade : carga / 3e5;
  const tempo = ms < 60e3 ? `${Math.max(1, Math.round(ms / 1000))} s` : ms < 3600e3 ? `${Math.round(ms / 60e3)} min` : `${(ms / 3600e3).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h`;
  $('n-help').textContent = `Mais cenários reduzem o erro da própria simulação. Estimativa neste aparelho: ~${tempo}${velocidade ? '' : ' (medida após a primeira simulação)'}. Acima de 200 mil, os percentis vêm de um histograma fino (erro menor que 0,1%). Dá para parar a qualquer momento.`;
}
atualizarAjudas();
$('btn-sim').addEventListener('click', () => rodar(true));
$('btn-rerun').addEventListener('click', () => rodar(false));

let ultimo = null;
function rodar(rolar) {
  const erro = (m) => { $('err').textContent = m; $('err').hidden = !m; };
  if (S.inicial <= 0 && S.aporte <= 0) return erro('Coloque um aporte inicial ou mensal.');
  const H = preparar(S.carteiras, desdeDe(S.janela), S.cambio, S.curto);
  if (H.erro) return erro(H.erro);
  erro('');
  const btn = $('btn-sim');
  btn.disabled = true; $('btn-sim-label').textContent = 'Processando… 0%';
  $('prog').hidden = false; $('btn-parar').hidden = false; $('prog-barra').style.width = '0%';
  const t0 = performance.now(), o = JSON.parse(JSON.stringify(S));
  const fmtT = (ms) => (ms < 60e3 ? `${Math.round(ms / 1000)} s` : ms < 3600e3 ? `${Math.round(ms / 60e3)} min` : `${(ms / 3600e3).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} h`);
  simularAssincrono(H, o, (p) => {
    const dec = performance.now() - t0, falta = p > 0.005 ? dec * (1 - p) / p : 0;
    $('btn-sim-label').textContent = `Processando… ${(p * 100).toLocaleString('pt-BR', { maximumFractionDigits: p < 0.1 ? 1 : 0 })}%${falta > 3000 ? ` · faltam ~${fmtT(falta)}` : ''}`;
    $('prog-barra').style.width = `${(p * 100).toFixed(2)}%`;
  })
    .then((R) => {
      ultimo = { H, R: hidratar(R), o, ms: performance.now() - t0 };
      velocidade = (o.n * o.anos * 12 * H.cs.length) / Math.max(1, ultimo.ms); atualizarAjudas();
      if (S.ver >= H.cs.length) S.ver = 0;
      $('stale').hidden = true;
      desenharSimulacao();
      if (rolar && window.innerWidth < 1000) $('res').scrollIntoView({ behavior: 'smooth', block: 'start' });
    })
    .catch((e) => erro(e.message === 'cancelado' ? 'Simulação interrompida.' : `Não foi possível simular: ${e.message || e}`))
    .finally(() => { btn.disabled = false; $('btn-sim-label').textContent = 'Rodar novamente'; $('prog').hidden = true; $('btn-parar').hidden = true; });
}
$('btn-parar').addEventListener('click', () => { if (cancelar) cancelar(); });

// ---------------------------------------------------------------- SIMULAÇÃO: resultados
function desenharSimulacao() {
  if (!ultimo) return;
  const { H, R, o } = ultimo;
  const base = o.real ? 'R$ de hoje' : 'R$ nominais';
  const metodoNome = { boot: 'bootstrap mensal', bloco: `blocos de ${o.bloco} m`, normal: 'normal multivariada', t: 't-Student (ν=5)' }[o.metodo];
  $('runline').innerHTML = [
    ['Cenários', R.nS.toLocaleString('pt-BR')], ['Horizonte', `${R.N} meses`],
    ['Janela', `${rotulo(H.ini)}–${rotulo(H.fim)} (${H.L} m)`], ['Modelo', metodoNome],
    ['Rebal.', R.parametrico || o.rebal === 1 ? 'mensal' : o.rebal ? `${o.rebal} m` : 'nunca'],
    ['Base', o.real ? 'real (descontada a inflação, IPCA)' : 'nominal'], ['Tempo', ultimo.ms < 60e3 ? `${(ultimo.ms / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s` : `${Math.round(ultimo.ms / 60e3)} min`],
    ['Ativos novos', NOME_CURTO[H.modo] || ''], ['Percentis', R.exato ? 'exatos' : 'histograma de 20 mil faixas'], ['Núcleos do processador', `${R.nucleos} de ${NUCLEOS}`],
  ].map(([k, v]) => `<span>${k}: <b>${v}</b></span>`).join('') + (H.limitante ? `<span>Contagem começa em ${rotulo(H.ini)}, por: <b>${esc(rotuloAtivo(H.limitante))}</b></span>` : '');
  if (Object.keys(H.estimados || {}).length) $('runline').innerHTML += `<span>Meses estimados por fatores: <b>${Object.entries(H.estimados).map(([t, n]) => `${esc(rotuloAtivo(t))} ${n}`).join(', ')}</b></span>`;
  const anosJ = H.L / 12, avisos = [];
  if (H.ini > idx(`${desdeDe(o.janela)}-01`)) avisos.push(`Você pediu ${o.janela} anos de passado, mas os ativos só permitem contar a partir de ${rotulo(H.ini)} (${num(anosJ, 0)} anos).`);
  if (H.jovens && H.jovens.length) avisos.push(`Ativos mais novos que a janela (entram quando começam; antes disso o peso deles vai para os demais): ${H.jovens.map((j) => `${esc(rotuloAtivo(j.t))} desde ${rotulo(j.desde)}`).join(', ')}.`, inicioCarteiras(H));
  avisos.push(avisoProxies(H));
  if (anosJ < 18) avisos.push(`O sorteio usa só ${num(anosJ, 0)} anos de história: o resultado reflete muito esse período. Para um cenário mais prudente, use ajuste negativo no retorno, o modelo t-Student ou ativos mais antigos.`);
  const av2 = avisos.filter(Boolean);
  $('aviso').hidden = !av2.length;
  $('aviso').innerHTML = av2.length ? `<b>Atenção.</b> ${av2.join(' ')}` : '';

  const nC = H.cs.length;
  const linhas = [
    ['Patrimônio mediano (P50: metade acima, metade abaixo)', (r) => r.q(0.5), curto, 'max', () => curto(R.cdiQ(0.5))],
    ['P10, pessimista (só 10% terminam abaixo)', (r) => r.q(0.1), curto, 'max', () => curto(R.cdiQ(0.1))],
    ['P90, otimista (só 10% terminam acima)', (r) => r.q(0.9), curto, 'max', () => curto(R.cdiQ(0.9))],
    ['Retorno ao ano, mediano', (r) => r.qa(0.5), (v) => pct(v), 'max', null],
    ['Queda máxima do topo ao fundo, mediana', (r) => r.qd(0.5), (v) => pct(v, 0), 'max', null],
    o.saqueOn ? ['Probabilidade de o dinheiro acabar', (r) => r.pRuina, (v) => pct(v, 0), 'min', () => pct(R.pRuinaCdi, 0)]
      : ['Probabilidade de perda (terminar abaixo do aportado)', (r) => r.pPerde, (v) => pct(v, 0), 'min', null],
    ['Probabilidade de superar o CDI (renda fixa de referência)', (r) => r.pCdi, (v) => pct(v, 0), 'max', null],
  ];
  if (o.meta > 0) linhas.push(['Probabilidade de atingir a meta', (r) => r.pMeta, (v) => pct(v, 0), 'max', null]);
  $('tbl-cmp').innerHTML = `<thead><tr><th></th>${R.res.map((r, c) => `<th><span class="pdot" style="background:${CORES_CART[c]}"></span>${esc(r.nome.slice(0, 16))}</th>`).join('')}<th>CDI</th></tr></thead><tbody>` +
    linhas.map(([n, f, fmt, melhor, cdiF]) => {
      const vs = R.res.map((r) => f(r)), alvo = nC > 1 ? (melhor === 'max' ? Math.max(...vs) : Math.min(...vs)) : null;
      return `<tr><td class="t">${n}</td>${vs.map((v) => `<td class="${alvo !== null && v === alvo ? 'best' : ''}">${fmt(v)}</td>`).join('')}<td style="color:var(--muted)">${cdiF ? cdiF() : '—'}</td></tr>`;
    }).join('') + `<tr><td class="t">Total aportado</td>${R.res.map(() => `<td>${curto(R.inv[R.Pn - 1])}</td>`).join('')}<td style="color:var(--muted)">${curto(R.inv[R.Pn - 1])}</td></tr></tbody>`;

  $('seg-ver').innerHTML = R.res.map((r, c) => `<button type="button" data-v="${c}" class="${c === S.ver ? 'on' : ''}"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;margin-right:6px;background:${CORES_CART[c]}"></span>${esc(r.nome.slice(0, 18))}</button>`).join('');
  $('seg-ver').hidden = nC < 2;
  const r = R.res[S.ver];
  const cor = (p, alto) => (alto ? p >= 0.6 : p <= 0.1) ? 'pos' : (alto ? p < 0.4 : p > 0.25) ? 'neg' : 'amb';
  const kpi = (k, v, s, cls = '', extra = '') => `<div class="kpi ${extra}"><span class="k">${k}</span><span class="v ${cls}">${v}</span><span class="s">${s}</span></div>`;
  $('kpis').innerHTML =
    kpi('Patrimônio mediano (P50, cenário do meio)', brl(r.q(0.5)), `em ${o.anos} ${o.anos === 1 ? 'ano' : 'anos'} · ${base}${o.ir ? ' · após IR (Imposto de Renda)' : ''}`, '', 'lead') +
    kpi('Intervalo P10–P90 (percentis 10 e 90)', `${curto(r.q(0.1))} – ${curto(r.q(0.9))}`, '8 em cada 10 cenários ficam nessa faixa', '', 'lead sm') +
    kpi('Total aportado', curto(R.inv[R.Pn - 1]), base) +
    kpi('Retorno ao ano (a.a.), mediano', pct(r.qa(0.5)), o.real ? 'acima da inflação (IPCA), após custos' : 'nominal, após custos', r.qa(0.5) < 0 ? 'neg' : '') +
    kpi('Queda máxima, mediana', pct(r.qd(0.5), 0), `maior perda do topo ao fundo; nos 10% piores: ${pct(r.qd(0.1), 0)}`, 'neg') +
    (o.saqueOn ? kpi('Probabilidade de o dinheiro acabar', pct(r.pRuina, 0), `saque de ${curto(o.saque)}/mês a partir do ano ${o.saqueAno}`, cor(r.pRuina, false))
      : kpi('Probabilidade de perda', pct(r.pPerde, 0), 'terminar abaixo do aportado', cor(r.pPerde, false))) +
    kpi('Probabilidade de superar o CDI', pct(r.pCdi, 0), 'mesmos aportes e saques no CDI (Certificado de Depósito Interbancário)', cor(r.pCdi, true)) +
    kpi('Cenário ruim (P5, percentil 5)', curto(r.q(0.05)), '1 em cada 20 cenários termina abaixo') +
    kpi('Mesmo plano no CDI (mediana)', curto(R.cdiQ(0.5)), o.ir ? 'após IR (Imposto de Renda)' : 'sem IR (Imposto de Renda)') +
    (r.pMeta !== null ? kpi('Probabilidade de atingir a meta', pct(r.pMeta, 0), `meta ${curto(o.meta)}`, cor(r.pMeta, true)) : kpi('Probabilidade de atingir a meta', '—', 'defina uma meta'));
  $('fan-meta').textContent = base;
  desenharLeque(); desenharHistograma(); desenharPercentis();
}
$('seg-ver').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; S.ver = +b.dataset.v; salvar(); desenharSimulacao(); });

function desenharPercentis() {
  const { R, o } = ultimo, r = R.res[S.ver];
  const linhas = [['P5 (5% abaixo)', 0.05], ['P10 (10% abaixo)', 0.1], ['P25 (25% abaixo)', 0.25], ['P50 (mediana)', 0.5], ['P75 (75% abaixo)', 0.75], ['P90 (90% abaixo)', 0.9], ['P95 (95% abaixo)', 0.95]];
  $('tbl-pct').innerHTML = `<thead><tr><th>Percentil</th><th>Patrimônio (R$)</th><th>Retorno ao ano</th><th>Queda máxima</th></tr></thead><tbody>` +
    linhas.map(([n, q]) => { const a = r.qa(q); return `<tr class="${q === 0.5 ? 'tot' : ''}"><td class="t">${n}</td><td>${Math.round(r.q(q)).toLocaleString('pt-BR')}</td><td class="${a < 0 ? 'neg' : ''}">${pct(a)}</td><td>${pct(r.qd(1 - q), 0)}</td></tr>`; }).join('') + '</tbody>';
  $('pct-meta').textContent = o.real ? 'R$ de hoje' : 'R$ nominais';
  $('pct-help').textContent = `Cada coluna é ordenada separadamente. Retorno a.a. é o da carteira${o.real ? ' acima do IPCA' : ''}, após custos e sem efeito dos aportes. Nos percentis altos aparecem as menores quedas.`;
}

// ---------------------------------------------------------------- gráficos (SVG)
function passoBonito(max, n) {
  const bruto = max / n, mag = Math.pow(10, Math.floor(Math.log10(bruto)));
  for (const m of [1, 2, 2.5, 5, 10]) if (bruto <= m * mag) return m * mag;
  return 10 * mag;
}
function eixoBRL(v) {
  if (v === 0) return 'R$ 0';
  const a = Math.abs(v), s = v < 0 ? '−' : '';
  if (a >= 1e9) return `${s}R$ ${(a / 1e9).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} bi`;
  if (a >= 1e6) return `${s}R$ ${(a / 1e6).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mi`;
  if (a >= 1e3) return `${s}R$ ${(a / 1e3).toLocaleString('pt-BR', { maximumFractionDigits: 0 })} mil`;
  return `${s}R$ ${Math.round(a)}`;
}
const largura = (el) => Math.max(300, el.clientWidth || 340);

function desenharLeque() {
  const { R, o } = ultimo, c0 = S.ver, r = R.res[c0];
  const box = $('fan'), W = largura(box), Hh = 260, ml = 62, mr = 12, mt = 12, mb = 26, iw = W - ml - mr, ih = Hh - mt - mb;
  const [, p10, p25, p50, p75, p90] = r.faixas;
  let ymax = 0;
  for (let k = 0; k < R.Pn; k++) { ymax = Math.max(ymax, p90[k], R.cdi[k], R.inv[k]); for (const x2 of R.res) ymax = Math.max(ymax, x2.faixas[3][k]); }
  ymax = ymax * 1.12 || 1;
  const pY = passoBonito(ymax, 4); ymax = Math.ceil(ymax / pY) * pY;
  const x = (m) => ml + (m / R.N) * iw, y = (v) => mt + ih - Math.min(Math.max(v / ymax, -0.02), 1.05) * ih;
  const pts = (arr) => Array.from(arr, (v, k) => `${x(R.pontos[k]).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const banda = (a, b) => `${pts(b)} ${Array.from(a, (v, k) => `${x(R.pontos[k]).toFixed(1)},${y(v).toFixed(1)}`).reverse().join(' ')}`;
  const corA = CORES_CART[c0];
  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Projeção do patrimônio: mediana e faixas de percentis">`;
  svg += `<defs><clipPath id="clipf"><rect x="${ml}" y="${mt - 4}" width="${iw}" height="${ih + 4}"/></clipPath></defs>`;
  for (let v = 0; v <= ymax + 1e-6; v += pY) svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--rule)"/><text x="${ml - 8}" y="${y(v) + 4}" text-anchor="end">${esc(eixoBRL(v))}</text>`;
  const pa = o.anos <= 6 ? 1 : o.anos <= 12 ? 2 : o.anos <= 25 ? 5 : 10;
  for (let a = 0; a <= o.anos; a += pa) svg += `<text x="${x(a * 12)}" y="${Hh - 6}" text-anchor="middle">${a === 0 ? 'hoje' : `ano ${a}`}</text>`;
  if (o.saqueOn && o.saqueAno < o.anos) svg += `<line x1="${x(o.saqueAno * 12)}" x2="${x(o.saqueAno * 12)}" y1="${mt}" y2="${mt + ih}" stroke="var(--amber)" stroke-dasharray="3 3"/><text x="${x(o.saqueAno * 12) + 4}" y="${mt + 10}" style="fill:var(--amber)">saques</text>`;
  svg += `<g clip-path="url(#clipf)">`;
  for (let s = 0; s < Math.min(25, R.nS); s++) svg += `<polyline points="${pts(r.cam.subarray(s * R.Pn, (s + 1) * R.Pn))}" fill="none" stroke="var(--path)" stroke-width="1"/>`;
  svg += `<polygon points="${banda(p10, p90)}" fill="${corA}" fill-opacity="0.12"/><polygon points="${banda(p25, p75)}" fill="${corA}" fill-opacity="0.22"/>`;
  svg += `<polyline points="${pts(R.inv)}" fill="none" stroke="var(--muted)" stroke-width="1.5" stroke-dasharray="2 4"/>`;
  svg += `<polyline points="${pts(R.cdi)}" fill="none" stroke="var(--bm1)" stroke-width="1.75" stroke-dasharray="6 4"/>`;
  R.res.forEach((x2, c) => { if (c !== c0) svg += `<polyline points="${pts(x2.faixas[3])}" fill="none" stroke="${CORES_CART[c]}" stroke-width="1.75" stroke-opacity="0.9"/>`; });
  svg += `<polyline points="${pts(p50)}" fill="none" stroke="${corA}" stroke-width="2.5"/></g>`;
  svg += `<g class="fcur" style="display:none"><line class="fcur-l" y1="${mt}" y2="${mt + ih}" stroke="var(--rule-2)"/><circle class="fcur-c" r="4.5" fill="${corA}" stroke="var(--panel)" stroke-width="2"/></g>`;
  svg += `<rect x="${ml}" y="${mt}" width="${iw}" height="${ih}" fill="transparent"/></svg>`;
  box.innerHTML = svg;
  $('fan-leg').innerHTML = `<span><i class="ln" style="border-color:${corA}"></i>${esc(r.nome)} (mediana)</span><span><i style="background:${corA};opacity:.35"></i>P25–P75 (metade central dos cenários)</span><span><i style="background:${corA};opacity:.15"></i>P10–P90 (8 em cada 10 cenários)</span>` +
    R.res.map((x2, c) => (c === c0 ? '' : `<span><i class="ln" style="border-color:${CORES_CART[c]}"></i>${esc(x2.nome)} (mediana)</span>`)).join('') +
    `<span><i class="ln" style="border-color:var(--bm1);border-top-style:dashed"></i>CDI (média)</span><span><i class="ln" style="border-color:var(--muted);border-top-style:dotted"></i>Total aportado</span>`;
  const el = box.querySelector('svg'), cur = el.querySelector('.fcur'), cl = el.querySelector('.fcur-l'), cc = el.querySelector('.fcur-c');
  const mover = (ev) => {
    const rc = el.getBoundingClientRect(), px = (ev.clientX - rc.left) / rc.width * W;
    const m = Math.max(0, Math.min(R.N, ((px - ml) / iw) * R.N));
    let k = 0, b = Infinity; R.pontos.forEach((pm, i) => { const d = Math.abs(pm - m); if (d < b) { b = d; k = i; } });
    cur.style.display = '';
    cl.setAttribute('x1', x(R.pontos[k])); cl.setAttribute('x2', x(R.pontos[k]));
    cc.setAttribute('cx', x(R.pontos[k])); cc.setAttribute('cy', y(p50[k]));
    const mes = R.pontos[k], an = Math.floor(mes / 12), re = mes % 12;
    const q = mes === 0 ? 'Hoje' : `${an ? `${an} ${an === 1 ? 'ano' : 'anos'}` : ''}${an && re ? ' e ' : ''}${re ? `${re} ${re === 1 ? 'mês' : 'meses'}` : ''}`;
    $('readout').innerHTML = `<b>${q}</b> · mediana (P50) <b>${curto(p50[k])}</b> · 8 em 10 entre ${curto(p10[k])} e ${curto(p90[k])}` +
      R.res.map((x2, c) => (c === c0 ? '' : ` · ${esc(x2.nome.slice(0, 12))} ${curto(x2.faixas[3][k])}`)).join('') + ` · CDI ${curto(R.cdi[k])} · aportado ${curto(R.inv[k])}`;
  };
  el.addEventListener('pointerdown', mover); el.addEventListener('pointermove', mover);
}

function desenharHistograma() {
  const { R, o } = ultimo, r = R.res[S.ver];
  const box = $('hist'), W = largura(box), Hh = 170, ml = 12, mr = 12, mt = 16, mb = 26, iw = W - ml - mr, ih = Hh - mt - mb;
  const lo = r.q(0.01), hi = r.q(0.99), nb = 32, lg = (hi - lo) / nb || 1, cont = new Array(nb).fill(0);
  for (const v of r.fin) { if (v < lo || v > hi) continue; cont[Math.min(nb - 1, Math.floor((v - lo) / lg))]++; }
  const cmax = Math.max(...cont, 1), inv = R.inv[R.Pn - 1], x = (v) => ml + ((v - lo) / (hi - lo || 1)) * iw;
  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Distribuição do patrimônio final">`;
  cont.forEach((c, i) => {
    const v0 = lo + i * lg, bh = (c / cmax) * ih;
    svg += `<rect x="${(x(v0) + 0.5).toFixed(1)}" y="${(mt + ih - bh).toFixed(1)}" width="${Math.max(1, iw / nb - 1.5).toFixed(1)}" height="${bh.toFixed(1)}" rx="1" fill="${v0 + lg / 2 < inv ? 'var(--neg)' : CORES_CART[S.ver]}" opacity="0.85"/>`;
  });
  svg += `<line x1="${ml}" x2="${W - mr}" y1="${mt + ih}" y2="${mt + ih}" stroke="var(--rule-2)"/>`;
  const marca = (v, cor, t) => {
    if (v < lo || v > hi) return '';
    const xx = x(v), a = xx > W * 0.75 ? 'end' : xx < W * 0.25 ? 'start' : 'middle';
    return `<line x1="${xx}" x2="${xx}" y1="${mt - 2}" y2="${mt + ih}" stroke="${cor}" stroke-width="1.5" stroke-dasharray="4 3"/><text x="${xx}" y="${mt - 5}" text-anchor="${a}" style="fill:${cor}">${esc(t)}</text>`;
  };
  svg += marca(r.q(0.5), 'var(--ink)', 'mediana');
  if (Math.abs(x(inv) - x(r.q(0.5))) > 50) svg += marca(inv, 'var(--muted)', 'aportado');
  if (o.meta > 0 && Math.abs(x(o.meta) - x(r.q(0.5))) > 40) svg += marca(o.meta, 'var(--amber)', 'meta');
  svg += `<text x="${ml}" y="${Hh - 6}">${esc(eixoBRL(lo))}</text><text x="${W / 2}" y="${Hh - 6}" text-anchor="middle">mediana ${esc(eixoBRL(r.q(0.5)))}</text><text x="${W - mr}" y="${Hh - 6}" text-anchor="end">${esc(eixoBRL(hi))}</text></svg>`;
  box.innerHTML = svg;
  $('hist-meta').textContent = `P1–P99 · ${nb} faixas`;
  $('hist-help').textContent = 'Frequência dos cenários por patrimônio final. Em vermelho, os que terminaram abaixo do total aportado. Os 1% extremos de cada lado ficam fora.';
}

// gráfico de linhas genérico (histórico e drawdown)
function graficoLinhas(box, series, meses, op) {
  const W = largura(box), Hh = op.altura || 260, ml = 62, mr = 12, mt = 12, mb = 26, iw = W - ml - mr, ih = Hh - mt - mb;
  const n = meses.length;
  let lo = Infinity, hi = -Infinity;
  for (const s of series) for (const v of s.v) { if (isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
  const log = op.log && lo > 0;
  const tf = (v) => (log ? Math.log(v) : v);
  let a = tf(lo), b = op.zeroTopo ? 0 : tf(hi);
  if (a === b) { a -= 1; b += 1; }
  const pad = (b - a) * 0.06; a -= pad; if (!op.zeroTopo) b += pad;
  const x = (i) => ml + (i / Math.max(1, n - 1)) * iw, y = (v) => mt + ih - ((tf(v) - a) / (b - a)) * ih;
  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="${esc(op.titulo || 'Gráfico')}">`;
  let ticks = [];
  if (log) {
    const vmin = Math.exp(a), vmax = Math.exp(b);
    for (let e = Math.floor(Math.log10(vmin)); e <= Math.ceil(Math.log10(vmax)); e++) for (const m of [1, 2, 5]) { const v = m * 10 ** e; if (v >= vmin && v <= vmax) ticks.push(v); }
    if (ticks.length > 7) ticks = ticks.filter((v) => /^1/.test(String(v)));
  } else {
    const p = passoBonito(b - a, 4);
    for (let v = Math.ceil(a / p) * p; v <= b + 1e-9; v += p) ticks.push(v);
  }
  for (const v of ticks) svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--rule)"/><text x="${ml - 8}" y="${y(v) + 4}" text-anchor="end">${esc(op.fmtY(v))}</text>`;
  const anos = Math.max(1, Math.round(n / 12)), pa = anos <= 6 ? 1 : anos <= 12 ? 2 : anos <= 25 ? 5 : 10;
  meses.forEach((mi, i) => { if (mi % 12 === 0 && Math.floor(mi / 12) % pa === 0) svg += `<text x="${x(i)}" y="${Hh - 6}" text-anchor="middle">${Math.floor(mi / 12)}</text>`; });
  for (const s of series) {
    const pts = []; s.v.forEach((v, i) => { if (isFinite(v)) pts.push(`${x(i).toFixed(1)},${y(v).toFixed(1)}`); });
    svg += `<polyline points="${pts.join(' ')}" fill="none" stroke="${s.cor}" stroke-width="${s.w || 1.75}" ${s.dash ? `stroke-dasharray="${s.dash}"` : ''}/>`;
  }
  svg += `<g class="cur" style="display:none"><line y1="${mt}" y2="${mt + ih}" stroke="var(--rule-2)"/></g><rect x="${ml}" y="${mt}" width="${iw}" height="${ih}" fill="transparent"/></svg>`;
  box.innerHTML = svg;
  if (op.readout) {
    const el = box.querySelector('svg'), cur = el.querySelector('.cur'), ln = cur.querySelector('line');
    const mover = (ev) => {
      const rc = el.getBoundingClientRect(), px = (ev.clientX - rc.left) / rc.width * W;
      const i = Math.max(0, Math.min(n - 1, Math.round(((px - ml) / iw) * (n - 1))));
      cur.style.display = ''; ln.setAttribute('x1', x(i)); ln.setAttribute('x2', x(i));
      op.readout(i);
    };
    el.addEventListener('pointerdown', mover); el.addEventListener('pointermove', mover);
  }
}

// ---------------------------------------------------------------- HISTÓRICO (backtest)
const BENCH = [['CDI', 'CDI (renda fixa)', 'var(--bm1)'], ['IPCA', 'IPCA (inflação)', 'var(--bm2)'], ['IBOV', 'Ibovespa (bolsa BR)', 'var(--bm3)'], ['SPY', 'S&P 500 em R$ (bolsa EUA)', 'var(--bm4)']];
$('bt-bench').innerHTML = BENCH.map(([k, n, c]) => `<label><input type="checkbox" value="${k}" ${S.btBench.includes(k) ? 'checked' : ''}><span class="swl" style="background:${c}"></span>${n}</label>`).join('');
$('bt-bench').addEventListener('change', () => { S.btBench = [...$('bt-bench').querySelectorAll('input:checked')].map((i) => i.value); salvar(); desenharHistorico(); });
ligarSeg('seg-bt-modo', 'btModo', desenharHistorico); ligarSeg('seg-bt-escala', 'btEscala', desenharHistorico); ligarSeg('seg-bt-janela', 'btJanela', desenharHistorico);

function desenharHistorico() {
  const H = preparar(S.carteiras, desdeDe(S.btJanela), S.cambio, S.curto === 'estimar' ? 'cortar' : S.curto);
  const av = [];
  if (!H.erro && H.ini > idx(`${desdeDe(S.btJanela)}-01`)) av.push(`A contagem começa em ${rotulo(H.ini)}, quando começa o ativo mais antigo de cada carteira.`);
  if (!H.erro && H.jovens && H.jovens.length) av.push(`Entram depois, quando passam a existir (antes disso o peso deles fica com os demais): ${H.jovens.map((j) => `${esc(rotuloAtivo(j.t))} em ${rotulo(j.desde)}`).join(', ')}.`, inicioCarteiras(H));
  if (!H.erro) av.push(avisoProxies(H));
  const av2 = av.filter(Boolean);
  $('bt-aviso').hidden = !av2.length; $('bt-aviso').innerHTML = av2.length ? `<b>Atenção.</b> ${av2.join(' ')}` : '';
  if (H.erro) {
    ['bt-chart', 'dd-chart'].forEach((id) => { $(id).innerHTML = `<p class="empty-note">${esc(H.erro)}</p>`; });
    $('tbl-met').innerHTML = ''; $('tbl-anos').innerHTML = ''; $('bt-leg').innerHTML = ''; return;
  }
  const L = H.L, taxaM = Math.pow(1 + S.taxa / 100, -1 / 12);
  const lst = H.cs.map((c, i) => ({ nome: c.nome, cor: CORES_CART[i], r: Array.from(H.P[i], (x) => (1 + x) * taxaM - 1), w: 2.25, cart: true }));
  const ibov = serie('IBOV'), spy = serie('SPY');
  const rIbov = H.meses.map((mi) => val(ibov, mi));
  for (const [k, n, cor] of BENCH) {
    if (!S.btBench.includes(k)) continue;
    let r;
    if (k === 'CDI') r = Array.from(H.cdi);
    else if (k === 'IPCA') r = Array.from(H.ipca);
    else if (k === 'IBOV') r = rIbov;
    else r = H.meses.map((mi, j) => { const x = val(spy, mi); return S.cambio ? (1 + x) * (1 + H.fx[j]) - 1 : x; });
    if (r.some((x) => isNaN(x))) continue;
    lst.push({ nome: n, cor, r, w: 1.5, dash: k === 'CDI' ? '6 4' : k === 'IPCA' ? '2 3' : '' });
  }
  const plano = S.btModo === 'plano';
  let inf = 1; const aportes = [];
  for (let j = 0; j < L; j++) { inf *= 1 + H.ipca[j]; aportes.push(plano ? S.aporte * (S.corrige ? inf : 1) : 0); }
  const v0 = plano ? S.inicial : 10000;
  for (const s of lst) {
    const v = [v0]; let x = v0;
    for (let j = 0; j < L; j++) { x = x * (1 + s.r[j]) + aportes[j]; v.push(x); }
    s.v = v;
    const dd = [0]; let pico = 1, nivel = 1;
    for (let j = 0; j < L; j++) { nivel *= 1 + s.r[j]; pico = Math.max(pico, nivel); dd.push(nivel / pico - 1); }
    s.dd = dd;
  }
  const meses = [H.ini - 1, ...H.meses];
  const invTot = [v0]; { let t = v0; for (let j = 0; j < L; j++) { t += aportes[j]; invTot.push(t); } }
  const series = lst.map((s) => ({ v: s.v, cor: s.cor, w: s.w, dash: s.dash }));
  if (plano) series.push({ v: invTot, cor: 'var(--muted)', w: 1.25, dash: '2 4' });
  graficoLinhas($('bt-chart'), series, meses, {
    log: S.btEscala === 'log', fmtY: eixoBRL, titulo: 'Evolução histórica',
    readout: (i) => { $('bt-readout').innerHTML = `<b>${rotulo(meses[i])}</b> · ` + lst.map((s) => `${esc(s.nome.slice(0, 16))} <b>${curto(s.v[i])}</b>`).join(' · ') + (plano ? ` · aportado ${curto(invTot[i])}` : ''); },
  });
  $('bt-leg').innerHTML = lst.map((s) => `<span><i class="ln" style="border-color:${s.cor};${s.dash ? 'border-top-style:dashed' : ''}"></i>${esc(s.nome)}</span>`).join('') + (plano ? '<span><i class="ln" style="border-color:var(--muted);border-top-style:dotted"></i>Total aportado</span>' : '');
  $('bt-meta').textContent = `${rotulo(H.ini)}–${rotulo(H.fim)} · ${L} meses`;
  $('bt-readout').textContent = 'Toque ou passe o cursor no gráfico para ver cada mês.';
  graficoLinhas($('dd-chart'), lst.filter((s) => !/IPCA|CDI/.test(s.nome)).map((s) => ({ v: s.dd, cor: s.cor, w: s.w })), meses, {
    altura: 180, zeroTopo: true, fmtY: (v) => pct(v, 0), titulo: 'Drawdown',
  });
  const cdi = Array.from(H.cdi), okIbov = !rIbov.some(isNaN);
  const ms = lst.map((s) => ({ s, m: metricas(s.r, cdi, okIbov ? rIbov : null) }));
  const linhas = [
    ['Retorno ao ano (a.a.)', (m) => pct(m.cagr)], ['Volatilidade ao ano (quanto oscila)', (m) => pct(m.vol)], ['Sharpe (retorno acima do CDI por unidade de risco)', (m) => num(m.sharpe)], ['Sortino (como o Sharpe, só contando quedas)', (m) => num(m.sortino)],
    ['Queda máxima', (m) => pct(m.maxdd)], ['Maior tempo abaixo do topo', (m) => `${m.durdd} m`], ['Pior mês', (m) => pct(m.pior)], ['Melhor mês', (m) => pct(m.melhor)],
    ['VaR 95% (perda mensal superada só em 5% dos meses)', (m) => pct(m.var95)], ['CVaR 95% (perda média nos 5% piores meses)', (m) => pct(m.cvar95)], ['Meses positivos', (m) => pct(m.pos, 0)],
    ['Beta vs Ibovespa (sensibilidade à bolsa: 1 = igual)', (m) => num(m.beta)], ['Correlação c/ Ibovespa', (m) => num(m.corr)],
  ];
  $('tbl-met').innerHTML = `<thead><tr><th></th>${ms.map(({ s }) => `<th><span class="pdot" style="background:${s.cor}"></span>${esc(s.nome.slice(0, 14))}</th>`).join('')}</tr></thead><tbody>` +
    linhas.map(([n, f]) => `<tr><td class="t">${n}</td>${ms.map(({ s, m }) => `<td style="${s.cart ? '' : 'color:var(--muted)'}">${f(m)}</td>`).join('')}</tr>`).join('') + '</tbody>';
  $('met-meta').textContent = `${L} meses · em reais`;
  const anos = [...new Set(H.meses.map((mi) => Math.floor(mi / 12)))].reverse();
  $('tbl-anos').innerHTML = `<thead><tr><th>Ano</th>${lst.map((s) => `<th><span class="pdot" style="background:${s.cor}"></span>${esc(s.nome.slice(0, 14))}</th>`).join('')}</tr></thead><tbody>` +
    anos.map((ano) => {
      const js = []; H.meses.forEach((mi, j) => { if (Math.floor(mi / 12) === ano) js.push(j); });
      return `<tr><td class="t">${ano}${js.length < 12 ? '*' : ''}</td>${lst.map((s) => { let g = 1; for (const j of js) g *= 1 + s.r[j]; const v = g - 1; return `<td class="${s.cart ? (v < 0 ? 'neg' : 'pos') : ''}" style="${s.cart ? '' : 'color:var(--muted)'}">${pct(v)}</td>`; }).join('')}</tr>`;
    }).join('') + '</tbody>';
}

// ---------------------------------------------------------------- ANÁLISE
$('seg-an').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; S.an = +b.dataset.v; salvar(); desenharAnalise(); });
let efOtimos = null;
function desenharAnalise() {
  if (S.an >= S.carteiras.length) S.an = 0;
  $('seg-an').innerHTML = S.carteiras.map((c, i) => `<button type="button" data-v="${i}" class="${i === S.an ? 'on' : ''}"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;margin-right:6px;background:${CORES_CART[i]}"></span>${esc(c.nome.slice(0, 18))}</button>`).join('');
  const cart = S.carteiras[S.an];
  // correlação e fronteira precisam de todos os ativos no mesmo período: os com menos de 2 anos ficam de fora daqui
  const novos = cart.itens.filter((i) => !i.t.startsWith('RF:') && info(i.t) && serie(i.t).i1 - serie(i.t).i0 < 23);
  const cartA = { ...cart, itens: cart.itens.filter((i) => !novos.includes(i)) };
  const H = cartA.itens.length ? preparar([cartA], Math.floor(BASE / 12), S.cambio, 'cortar') : { erro: 'Todos os ativos desta carteira têm menos de 2 anos de histórico: a análise de correlação e a fronteira precisam de mais dados. A simulação e o histórico funcionam normalmente.' };
  const vazio = (msg) => { ['risk-bars', 'ef-chart'].forEach((id) => { $(id).innerHTML = `<p class="empty-note">${esc(msg)}</p>`; }); ['tbl-corr', 'tbl-ef'].forEach((id) => { $(id).innerHTML = ''; }); $('an-runline').innerHTML = ''; $('ef-leg').innerHTML = ''; };
  if (H.erro) return vazio(H.erro);
  const K = H.uni.length, L = H.L, w = H.W[0];
  $('an-runline').innerHTML = `<span>Janela: <b>${rotulo(H.ini)}–${rotulo(H.fim)} (${L} m)</b></span><span>Ativos: <b>${K}</b></span><span>Valores: <b>em reais</b></span>` +
    (novos.length ? `<span>Fora desta análise (menos de 2 anos de dados): <b>${novos.map((i) => esc(rotuloAtivo(i.t))).join(', ')}</b></span>` : '') +
    (H.trocouModo ? '<span>Ativos com pouco histórico em comum: cada um entra quando começa</span>' : '');
  const mu = H.R.map((r) => media(r));
  const cov = []; for (let i = 0; i < K; i++) cov.push(new Float64Array(K));
  for (let i = 0; i < K; i++) for (let j = 0; j <= i; j++) { let s = 0; for (let t = 0; t < L; t++) s += (H.R[i][t] - mu[i]) * (H.R[j][t] - mu[j]); cov[i][j] = cov[j][i] = s / (L - 1); }
  // contribuição para o risco
  const sw = cov.map((row) => row.reduce((a, c, j) => a + c * w[j], 0));
  const varP = w.reduce((a, wi, i) => a + wi * sw[i], 0);
  const contrib = Array.from(w, (wi, i) => (varP > 0 ? wi * sw[i] / varP : 0));
  const ordem = [...Array(K).keys()].sort((a, b) => contrib[b] - contrib[a]);
  const maxv = Math.max(...contrib, ...w, 0.01);
  $('risk-bars').innerHTML = `<div class="rbar" style="font-size:11px;color:var(--muted)"><span></span><span><i class="sw" style="background:var(--rule-2)"></i> peso &nbsp; <i class="sw" style="background:${CORES_CART[S.an]}"></i> risco</span><span style="text-align:right">peso / risco</span></div>` +
    ordem.map((i) => `<div class="rbar"><span class="tk" title="${esc(info(H.uni[i]).n)}">${esc(rotuloAtivo(H.uni[i]).slice(0, 12))}</span><span class="tracks"><span class="tr"><i style="width:${(w[i] / maxv * 100).toFixed(1)}%;background:var(--rule-2)"></i></span><span class="tr"><i style="width:${(Math.max(0, contrib[i]) / maxv * 100).toFixed(1)}%;background:${CORES_CART[S.an]}"></i></span></span><span class="vals">${pct(w[i], 0)} / ${pct(contrib[i], 0)}</span></div>`).join('');
  // correlação (até 16 ativos de maior peso)
  const top = [...Array(K).keys()].sort((a, b) => w[b] - w[a]).slice(0, 16);
  const corr = (i, j) => { const d = Math.sqrt(cov[i][i] * cov[j][j]); return d > 0 ? cov[i][j] / d : 0; };
  const cel = (c) => { const p = Math.round(Math.abs(c) * 70); const cor = c >= 0 ? 'var(--accent)' : 'var(--neg)'; return `<td style="background:color-mix(in srgb, ${cor} ${p}%, transparent);${Math.abs(c) > 0.6 ? 'color:var(--accent-ink)' : ''}">${num(c, 2)}</td>`; };
  $('tbl-corr').innerHTML = `<thead><tr><th></th>${top.map((i) => `<th>${esc(rotuloAtivo(H.uni[i]).slice(0, 7))}</th>`).join('')}</tr></thead><tbody>` +
    top.map((i) => `<tr><td class="t"><span class="tk">${esc(rotuloAtivo(H.uni[i]).slice(0, 10))}</span></td>${top.map((j) => cel(corr(i, j))).join('')}</tr>`).join('') + '</tbody>';
  $('corr-meta').textContent = K > 16 ? '16 maiores pesos' : `${K} ativos`;
  // fronteira eficiente
  if (K < 2) { $('ef-chart').innerHTML = '<p class="empty-note">A fronteira precisa de pelo menos 2 ativos na carteira.</p>'; $('tbl-ef').innerHTML = ''; $('ef-leg').innerHTML = ''; $('ef-meta').textContent = ''; return; }
  const cdiA = Math.pow(1 + media(Array.from(H.cdi)), 12) - 1;
  const aval = (ws) => {
    let m = 0, v = 0;
    for (let i = 0; i < K; i++) { if (!ws[i]) continue; m += ws[i] * mu[i]; for (let j = 0; j < K; j++) if (ws[j]) v += ws[i] * ws[j] * cov[i][j]; }
    const ret = Math.pow(1 + m, 12) - 1 - 6 * v, vol = Math.sqrt(v * 12); // retorno geométrico aproximado
    return { ret, vol, sharpe: vol > 0 ? (ret - cdiA) / vol : -Infinity };
  };
  const pontos = [];
  const dir = () => { const g = Array.from({ length: K }, () => -Math.log(Math.random() || 1e-12)); const s = g.reduce((a, b) => a + b, 0); return g.map((x) => x / s); };
  for (let n = 0; n < 2500; n++) { let ws = dir(); if (n % 3 === 0) { ws = ws.map((x) => x ** 3); const s = ws.reduce((a, b) => a + b, 0); ws = ws.map((x) => x / s); } pontos.push({ ws, ...aval(ws) }); }
  for (let i = 0; i < K; i++) { const ws = new Array(K).fill(0); ws[i] = 1; pontos.push({ ws, ...aval(ws), so: i }); }
  const refina = (inicio, obj) => {
    let melhor = inicio, f = obj(melhor);
    for (let it = 0; it < 1500; it++) {
      const ws = melhor.ws.slice(), i = (Math.random() * K) | 0, j = (Math.random() * K) | 0;
      if (i === j) continue;
      const d = Math.min(ws[i], (Math.random() * 0.1) * (1 - it / 1500) + 0.002);
      ws[i] -= d; ws[j] += d;
      const c = { ws, ...aval(ws) }, fc = obj(c);
      if (fc > f) { melhor = c; f = fc; }
    }
    return melhor;
  };
  const maxS = refina(pontos.reduce((a, b) => (b.sharpe > a.sharpe ? b : a)), (p) => p.sharpe);
  const minV = refina(pontos.reduce((a, b) => (b.vol < a.vol ? b : a)), (p) => -p.vol);
  const atual = { ws: Array.from(w), ...aval(Array.from(w)) };
  const mesmoRisco = refina(atual, (p) => p.ret - Math.max(0, p.vol - atual.vol) * 50);
  efOtimos = { maxS, minV, mesmoRisco, uni: H.uni };
  const box = $('ef-chart'), W = largura(box), Hh = 280, ml = 50, mr = 14, mt = 12, mb = 30, iw = W - ml - mr, ih = Hh - mt - mb;
  const todos = [...pontos, maxS, minV, atual, mesmoRisco];
  const xmax = Math.max(...todos.map((p) => p.vol)) * 1.05, ymin = Math.min(0, ...todos.map((p) => p.ret)), ymax = Math.max(cdiA, ...todos.map((p) => p.ret)) * 1.08;
  const x = (v) => ml + (v / xmax) * iw, y = (v) => mt + ih - ((v - ymin) / (ymax - ymin || 1)) * ih;
  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Fronteira eficiente: risco versus retorno">`;
  const pY = passoBonito(ymax - ymin, 4); for (let v = Math.ceil(ymin / pY) * pY; v <= ymax; v += pY) svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--rule)"/><text x="${ml - 6}" y="${y(v) + 4}" text-anchor="end">${pct(v, 0)}</text>`;
  const pX = passoBonito(xmax, 5); for (let v = 0; v <= xmax; v += pX) svg += `<text x="${x(v)}" y="${Hh - 14}" text-anchor="middle">${pct(v, 0)}</text>`;
  svg += `<text x="${ml + iw / 2}" y="${Hh - 1}" text-anchor="middle">volatilidade a.a. →</text>`;
  svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(cdiA)}" y2="${y(cdiA)}" stroke="var(--bm1)" stroke-dasharray="5 4"/><text x="${W - mr}" y="${y(cdiA) - 4}" text-anchor="end" style="fill:var(--bm1)">CDI ${pct(cdiA, 1)}</text>`;
  for (const p of pontos) svg += `<circle cx="${x(p.vol).toFixed(1)}" cy="${y(p.ret).toFixed(1)}" r="${p.so !== undefined ? 3.5 : 1.6}" fill="${p.so !== undefined ? 'var(--ink-2)' : 'var(--rule-2)'}"/>`;
  if (K <= 12) for (const p of pontos.filter((q) => q.so !== undefined)) svg += `<text x="${x(p.vol) + 5}" y="${y(p.ret) + 3}" style="font-size:9.5px">${esc(rotuloAtivo(H.uni[p.so]).slice(0, 8))}</text>`;
  const marca = (p, cor, t) => `<circle cx="${x(p.vol)}" cy="${y(p.ret)}" r="6" fill="${cor}" stroke="var(--panel)" stroke-width="2"/><text x="${x(p.vol) + 9}" y="${y(p.ret) - 7}" style="fill:${cor};font-weight:600">${t}</text>`;
  svg += marca(minV, 'var(--pC)', 'Mín. risco') + marca(maxS, 'var(--amber)', 'Máx. Sharpe') + marca(mesmoRisco, 'var(--pB)', 'Mesmo risco') + marca(atual, CORES_CART[S.an], 'Atual') + '</svg>';
  box.innerHTML = svg;
  $('ef-leg').innerHTML = '<span><i style="background:var(--rule-2)"></i>Combinações aleatórias</span><span><i style="background:var(--ink-2)"></i>100% em um ativo</span>';
  $('ef-meta').textContent = `${pontos.length.toLocaleString('pt-BR')} carteiras testadas`;
  const cols = [['Atual', atual, null], ['Mesmo risco, mais retorno', mesmoRisco, 'mesmoRisco'], ['Máximo Sharpe', maxS, 'maxS'], ['Mínimo risco', minV, 'minV']];
  const mostrar = [...Array(K).keys()].filter((i) => cols.some(([, p]) => p.ws[i] >= 0.005)).sort((a, b) => w[b] - w[a]);
  $('tbl-ef').innerHTML = `<thead><tr><th></th>${cols.map(([n]) => `<th>${n}</th>`).join('')}</tr></thead><tbody>` +
    `<tr><td class="t">Retorno a.a.</td>${cols.map(([, p]) => `<td>${pct(p.ret)}</td>`).join('')}</tr>` +
    `<tr><td class="t">Volatilidade a.a.</td>${cols.map(([, p]) => `<td>${pct(p.vol)}</td>`).join('')}</tr>` +
    `<tr class="tot"><td class="t">Sharpe</td>${cols.map(([, p]) => `<td>${num(p.sharpe)}</td>`).join('')}</tr>` +
    mostrar.map((i) => `<tr><td class="t"><span class="tk">${esc(rotuloAtivo(H.uni[i]).slice(0, 18))}</span></td>${cols.map(([, p]) => `<td>${p.ws[i] >= 0.005 ? pct(p.ws[i], 0) : '—'}</td>`).join('')}</tr>`).join('') +
    `<tr><td class="t"></td>${cols.map(([, , k]) => `<td>${k ? `<button class="link" type="button" data-aplicar="${k}">Usar estes pesos</button>` : ''}</td>`).join('')}</tr></tbody>`;
}
$('tbl-ef').addEventListener('click', (e) => {
  const b = e.target.closest('[data-aplicar]'); if (!b || !efOtimos) return;
  const p = efOtimos[b.dataset.aplicar], cart = S.carteiras[S.an];
  cart.itens = efOtimos.uni.map((t, i) => ({ t, p: Math.round(p.ws[i] * 1000) / 10 })).filter((x) => x.p >= 0.5);
  fecha100(cart.itens);
  mudouCarteiras(); desenharCarteira(); desenharAnalise();
});


// ---------------------------------------------------------------- OTIMIZADOR
// estimadores robustos
function estimarRobusto(R) { // R: lista de colunas (Float64Array por ativo)
  const K = R.length, L = R[0].length;
  const mu = R.map((r) => media(r));
  // James-Stein: médias puxadas para a média geral
  if (K >= 3) {
    const alvo = media(mu), varMedia = media(R.map((r, k) => { let v = 0; for (const x of r) v += (x - mu[k]) ** 2; return v / (L - 1); })) / L;
    let dist = 0; for (const m of mu) dist += (m - alvo) ** 2;
    const f = dist > 0 ? Math.max(0, 1 - (K - 2) * varMedia / dist) : 0;
    for (let k = 0; k < K; k++) mu[k] = alvo + f * (mu[k] - alvo);
  }
  // Ledoit-Wolf (alvo: correlação constante)
  const X = R.map((r) => { const m = media(r); return Float64Array.from(r, (x) => x - m); });
  const S = Array.from({ length: K }, () => new Float64Array(K));
  for (let i = 0; i < K; i++) for (let j = 0; j <= i; j++) { let v = 0; for (let t = 0; t < L; t++) v += X[i][t] * X[j][t]; S[i][j] = S[j][i] = v / L; }
  const sd = S.map((r, i) => Math.sqrt(Math.max(r[i], 1e-14)));
  let rbar = 0; for (let i = 0; i < K; i++) for (let j = 0; j < K; j++) if (i !== j) rbar += S[i][j] / (sd[i] * sd[j]);
  rbar = K > 1 ? rbar / (K * (K - 1)) : 0;
  let pi = 0, gamma = 0, rhoD = 0, rhoOff = 0;
  for (let i = 0; i < K; i++) for (let j = 0; j < K; j++) {
    let p = 0, ti = 0, tj = 0;
    for (let t = 0; t < L; t++) { const a = X[i][t], b = X[j][t]; p += (a * b - S[i][j]) ** 2; ti += (a * a - S[i][i]) * (a * b - S[i][j]); tj += (b * b - S[j][j]) * (a * b - S[i][j]); }
    p /= L; pi += p;
    const F = i === j ? S[i][i] : rbar * sd[i] * sd[j];
    gamma += (F - S[i][j]) ** 2;
    if (i === j) rhoD += p; else rhoOff += (rbar / 2) * ((sd[j] / sd[i]) * ti / L + (sd[i] / sd[j]) * tj / L);
  }
  const delta = gamma > 0 ? Math.max(0, Math.min(1, (pi - rhoD - rhoOff) / gamma / L)) : 1;
  const C = S.map((r, i) => Float64Array.from(r, (v, j) => delta * (i === j ? S[i][i] : rbar * sd[i] * sd[j]) + (1 - delta) * v + (i === j ? 1e-12 : 0)));
  return { mu, C, delta };
}
const mulCv = (C, w) => C.map((r) => { let v = 0; for (let j = 0; j < w.length; j++) v += r[j] * w[j]; return v; });
const dot = (a, b) => { let v = 0; for (let i = 0; i < a.length; i++) v += a[i] * b[i]; return v; };
function projetar(v, cap) { // projeção no simplex com teto: 0 ≤ w ≤ cap, Σw = 1
  let lo = Math.min(...v) - 1, hi = Math.max(...v);
  for (let it = 0; it < 60; it++) { const tau = (lo + hi) / 2; let s = 0; for (const x of v) s += Math.min(cap, Math.max(0, x - tau)); if (s > 1) lo = tau; else hi = tau; }
  const tau = (lo + hi) / 2; const w = v.map((x) => Math.min(cap, Math.max(0, x - tau))); const s = w.reduce((a, b) => a + b, 0) || 1;
  return w.map((x) => x / s);
}
function anualGeo(m, v) { return Math.pow(1 + m, 12) * Math.exp(-12 * v / (2 * (1 + m) ** 2)) - 1; }
function estatPesos(w, mu, C, rfm) { const m = dot(w, mu), v = dot(w, mulCv(C, w)); const ret = anualGeo(m, v), vol = Math.sqrt(Math.max(v, 0) * 12); return { ret, vol, sharpe: vol > 0 ? (ret - (Math.pow(1 + rfm, 12) - 1)) / vol : NaN }; }
function otimizarPesos(mu, C, obj, cap, rfm, volAlvo) {
  const K = mu.length; cap = Math.max(cap, 1 / K + 1e-9);
  let lmax = 0; for (let i = 0; i < K; i++) lmax = Math.max(lmax, C[i].reduce((a, b) => a + Math.abs(b), 0));
  const passo = 1 / (2 * lmax + 1e-12);
  const minUtil = (lam, w0) => { // máx  w·μ − λ wCw  (gradiente projetado)
    let w = w0 ? w0.slice() : projetar(new Array(K).fill(1 / K), cap);
    const eta = 1 / (2 * lam * lmax + 1e-12);
    for (let it = 0; it < 600; it++) { const g = mulCv(C, w); w = projetar(w.map((x, i) => x + eta * (mu[i] - 2 * lam * g[i])), cap); }
    return w;
  };
  if (obj === 'minvar') { let w = projetar(new Array(K).fill(1 / K), cap); for (let it = 0; it < 1500; it++) { const g = mulCv(C, w); w = projetar(w.map((x, i) => x - passo * g[i]), cap); } return w; }
  if (obj === 'paridade') {
    let w = new Array(K).fill(1 / K);
    for (let it = 0; it < 400; it++) { const g = mulCv(C, w), v = dot(w, g); w = w.map((x, i) => x * Math.sqrt((v / K) / Math.max(x * g[i], 1e-16))); w = projetar(w.map((x) => x / w.reduce((a, b) => a + b, 0)), cap); }
    return w;
  }
  if (obj === 'alvo') { // maior retorno com vol ≤ alvo: busca em λ
    const alvo = (volAlvo / 100) ** 2 / 12; let lo = 1e-3, hi = 1e5, melhor = null;
    for (let it = 0; it < 40; it++) { const lam = Math.sqrt(lo * hi), w = minUtil(lam, melhor); if (dot(w, mulCv(C, w)) > alvo) lo = lam; else { hi = lam; melhor = w; } }
    return melhor || minUtil(hi);
  }
  // máximo Sharpe. Se nenhum ativo tem retorno esperado acima do CDI, a carteira tangente não existe
  // (maximizar um Sharpe negativo levaria ao maior risco): usa mínima variância.
  if (Math.max(...mu) <= rfm) return otimizarPesos(mu, C, 'minvar', cap, rfm, volAlvo);
  let melhor = null, mS = -Infinity;
  for (const lam of [0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512]) { const w = minUtil(lam); const e = estatPesos(w, mu, C, rfm); if (e.sharpe > mS) { mS = e.sharpe; melhor = w; } }
  let w = melhor;
  for (let it = 0; it < 800; it++) {
    const g = mulCv(C, w), m = dot(w, mu) - rfm, v = Math.max(dot(w, g), 1e-14), sd = Math.sqrt(v);
    const grad = mu.map((x, i) => x / sd - m * g[i] / (sd * v));
    const cand = projetar(w.map((x, i) => x + passo * sd * 0.5 * grad[i]), cap);
    if (estatPesos(cand, mu, C, rfm).sharpe >= estatPesos(w, mu, C, rfm).sharpe - 1e-12) w = cand; else break;
  }
  return w;
}
function otimizarCompleto(R, op, rfm) {
  const resolve = (cols) => {
    const sub = cols.map((k) => R[k]); const { mu, C } = estimarRobusto(sub);
    return otimizarPesos(mu, C, op.obj, op.pesoMax / 100, rfm, op.volAlvo);
  };
  const K = R.length; let idxs = [...Array(K).keys()], w = resolve(idxs);
  if (op.maxAtivos && w.filter((x) => x > 1e-4).length > op.maxAtivos) {
    idxs = idxs.slice().sort((a, b) => w[b] - w[a]).slice(0, op.maxAtivos);
    const ws = resolve(idxs); w = new Array(K).fill(0); idxs.forEach((k, i) => { w[k] = ws[i]; });
  }
  return w.map((x) => (x < 1e-4 ? 0 : x));
}
function reamostrar(R, n, bloco, semente) {
  const L = R[0].length, rnd = aleatorio(semente), out = [];
  for (let r = 0; r < n; r++) {
    const ix = []; while (ix.length < L) { const s0 = (rnd() * L) | 0; for (let b = 0; b < bloco && ix.length < L; b++) ix.push((s0 + b) % L); }
    out.push(R.map((col) => Float64Array.from(ix, (t) => col[t])));
  }
  return out;
}
function otimizarRobusto(R, op, rfm, aoProgresso) {
  if (!op.reamostra) return otimizarCompleto(R, op, rfm);
  const K = R.length, soma = new Array(K).fill(0), amostras = reamostrar(R, 50, 6, 11);
  amostras.forEach((Rs, i) => { const w = otimizarCompleto(Rs, op, rfm); for (let k = 0; k < K; k++) soma[k] += w[k] / 50; if (aoProgresso) aoProgresso(i / 50); });
  let w = soma;
  if (op.maxAtivos && w.filter((x) => x > 1e-4).length > op.maxAtivos) { const corte = w.slice().sort((a, b) => b - a)[op.maxAtivos - 1]; w = w.map((x) => (x >= corte ? x : 0)); }
  const s = w.reduce((a, b) => a + b, 0); return w.map((x) => x / s);
}

// interface
const OT_CLASSES = [['acao', 'Ações BR'], ['fii', 'Todos os FIIs'], ['fii:papel', 'FIIs de papel'], ['fii:tijolo', 'FIIs de tijolo'], ['fii:hibrido', 'FIIs híbridos'],
  ['fiagro|fiinfra', 'Fiagro e Infra'], ['etf', 'ETFs B3'], ['fundo_rf', 'Fundos de renda fixa'], ['fundo_mm', 'Multimercados'], ['fundo_acoes', 'Fundos de ações'],
  ['acao_us', 'Ações EUA'], ['etf_us', 'ETFs EUA'], ['cripto', 'Cripto'], ['indice|commodity|moeda', 'Índices, ouro e moedas']];
let otPronto = false, otUltimo = null;
function prepararOtimizador() {
  const O_ = S.ot;
  if (!otPronto) {
    otPronto = true;
    $('ot-classes').innerHTML = OT_CLASSES.map(([k, n]) => `<button type="button" class="chip" data-k="${k}">${n}</button>`).join('');
    $('ot-classes').addEventListener('click', (e) => {
      const b = e.target.closest('button'); if (!b) return; const k = b.dataset.k;
      O_.classes = O_.classes.includes(k) ? O_.classes.filter((x) => x !== k) : [...O_.classes, k];
      salvar(); prepararOtimizador();
    });
    const liga = (id, chave, conv) => { const el = $(id); el.value = String(O_[chave]).replace('.', ','); el.addEventListener('input', () => { O_[chave] = conv(el.value); salvar(); }); };
    liga('ot-vol', 'volAlvo', (v) => Math.max(1, lerDec(v))); liga('ot-peso', 'pesoMax', (v) => Math.max(1, Math.min(100, lerDec(v))));
    liga('ot-nmax', 'maxAtivos', (v) => Math.max(0, parseInt(v, 10) || 0));
    for (const [id, chave] of [['ot-min', 'minAnos'], ['ot-max', 'maxUniv']]) { $(id).value = O_[chave]; $(id).addEventListener('change', () => { O_[chave] = +$(id).value; salvar(); prepararOtimizador(); }); }
    for (const [id, chave] of [['ot-estender', 'estender'], ['ot-reamostra', 'reamostra']]) { $(id).checked = O_[chave]; $(id).addEventListener('change', () => { O_[chave] = $(id).checked; salvar(); prepararOtimizador(); }); }
    const seg = (id, chave) => $(id).addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; O_[chave] = isNaN(+b.dataset.v) ? b.dataset.v : +b.dataset.v; salvar(); prepararOtimizador(); });
    seg('seg-ot-fonte', 'fonte'); seg('seg-ot-janela', 'janela'); seg('seg-ot-obj', 'obj'); seg('seg-ot-cart', 'cart');
    $('btn-ot').addEventListener('click', rodarOtimizador);
    $('ot-salvar').addEventListener('click', salvarOtimizada);
  }
  if (O_.cart >= S.carteiras.length) O_.cart = 0;
  $('seg-ot-cart').innerHTML = S.carteiras.map((c, i) => `<button type="button" data-v="${i}">${esc(c.nome.slice(0, 16))}</button>`).join('');
  const marca = (id, v) => [...$(id).children].forEach((b) => { const on = String(b.dataset.v) === String(v); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  marca('seg-ot-fonte', O_.fonte); marca('seg-ot-janela', O_.janela); marca('seg-ot-obj', O_.obj); marca('seg-ot-cart', O_.cart);
  [...$('ot-classes').children].forEach((b) => { const on = O_.classes.includes(b.dataset.k); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  $('ot-filtro').hidden = O_.fonte !== 'filtro'; $('ot-cart').hidden = O_.fonte !== 'cart';
  $('f-ot-vol').classList.toggle('dim', O_.obj !== 'alvo');
  $('ot-obj-help').textContent = {
    sharpe: 'Maior retorno acima do CDI por unidade de risco.', minvar: 'A combinação que menos oscila.',
    paridade: 'Cada ativo contribui igualmente para o risco total (não depende das médias, mais estável).', alvo: 'O maior retorno sem passar da volatilidade escolhida.',
  }[O_.obj];
  const U = universoOtimizador();
  $('ot-univ-meta').textContent = U.erro ? '' : `${U.codigos.length} ativos`;
  if (!otUltimo) $('ot-runline').innerHTML = U.erro ? `<span>${esc(U.erro)}</span>` : `<span>Universo: <b>${U.codigos.length} ativos</b> com dados de ${rotulo(U.ini)} a ${rotulo(FIM_DADOS)}. Toque em Otimizar.</span>`;
}
function universoOtimizador() {
  const O_ = S.ot, ini = O_.janela >= 99 ? BASE : FIM_DADOS - O_.janela * 12 + 1;
  const sr = (t) => (O_.estender ? serieEstendida(t) : serie(t));
  let cods;
  if (O_.fonte === 'cart') cods = S.carteiras[O_.cart].itens.map((i) => i.t);
  else {
    if (!O_.classes.length) return { erro: 'Marque pelo menos uma classe.' };
    cods = [...ATIVOS.values()].filter((a) => O_.classes.some((k) => noGrupo(a, k))).filter((a) => serie(a.t).v.length >= O_.minAnos * 12 || O_.estender)
      .sort((a, b) => (b.tam || 0) - (a.tam || 0)).map((a) => a.t);
  }
  let ini2 = ini;
  if (O_.janela >= 99) { // janela máxima: começa no ativo mais novo (limitado a 120 ativos)
    cods = cods.slice(0, O_.fonte === 'cart' ? 999 : O_.maxUniv);
    ini2 = Math.max(...cods.filter((t) => !t.startsWith('RF:')).map((t) => sr(t).i0), serie('USDBRL').i0);
  }
  cods = cods.filter((t) => t.startsWith('RF:') || (sr(t).i0 <= ini2 && sr(t).i1 >= FIM_DADOS - 1)).slice(0, O_.fonte === 'cart' ? 999 : O_.maxUniv);
  if (cods.length < 2) return { erro: 'Menos de 2 ativos com dados na janela. Aumente a janela, reduza o histórico mínimo ou estenda os históricos.' };
  return { codigos: cods, ini: ini2 };
}
function rodarOtimizador() {
  const O_ = S.ot, err = (m) => { $('ot-err').textContent = m; $('ot-err').hidden = !m; };
  const U = universoOtimizador(); if (U.erro) return err(U.erro);
  const H = preparar([{ nome: 'U', itens: U.codigos.map((t) => ({ t, p: 1 })) }], Math.floor(U.ini / 12), S.cambio, O_.estender);
  if (H.erro) return err(H.erro);
  err('');
  const i0 = H.meses.findIndex((mi) => mi >= U.ini), sel = (arr) => arr.slice(Math.max(0, i0));
  const R = H.R.map(sel), cdi = sel(H.cdi), meses = sel(H.meses), uni = H.uni;
  if (R[0].length < 24) return err('Janela com menos de 24 meses.');
  $('btn-ot').disabled = true; $('btn-ot-label').textContent = 'Otimizando…';
  setTimeout(() => {
    try {
      const t0 = performance.now(), rfm = media(cdi);
      const w = otimizarRobusto(R, O_, rfm);
      const { mu, C, delta } = estimarRobusto(R);
      // fronteira (λ) e ativos isolados
      const fronteira = [];
      const K = R.length; let lmax = 0; for (let i = 0; i < K; i++) lmax = Math.max(lmax, C[i].reduce((a, b) => a + Math.abs(b), 0));
      for (const lam of [0.2, 0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024]) {
        let ww = projetar(new Array(K).fill(1 / K), Math.max(O_.pesoMax / 100, 1 / K + 1e-9)); const eta = 1 / (2 * lam * lmax);
        for (let it = 0; it < 500; it++) { const g = mulCv(C, ww); ww = projetar(ww.map((x, i) => x + eta * (mu[i] - 2 * lam * g[i])), Math.max(O_.pesoMax / 100, 1 / K + 1e-9)); }
        fronteira.push(estatPesos(ww, mu, C, rfm));
      }
      fronteira.sort((a, b) => a.vol - b.vol);
      const isolados = R.map((_, k) => { const e = new Array(K).fill(0); e[k] = 1; return { t: uni[k], ...estatPesos(e, mu, C, rfm) }; });
      const est = estatPesos(w, mu, C, rfm);
      // histórico real dos pesos na janela
      const rp = R[0].map((_, t) => R.reduce((acc, col, k) => acc + w[k] * col[t], 0));
      const mh = metricas(rp, cdi);
      // carteira de referência (ativa) se couber no universo
      const ref = S.carteiras[S.ativa];
      const wRef = ref.itens.length && ref.itens.every((i) => uni.includes(i.t)) ? (() => { const s = ref.itens.reduce((a, i) => a + i.p, 0); return uni.map((t) => (ref.itens.find((i) => i.t === t)?.p || 0) / s); })() : null;
      // teste fora da amostra
      const L = R[0].length, T = Math.min(36, Math.floor(L / 3));
      let wf = null;
      if (L >= 48) {
        const treino = R.map((c) => c.slice(0, L - T)), prova = R.map((c) => c.slice(L - T));
        const wT = otimizarRobusto(treino, { ...O_, reamostra: false }, media(cdi.slice(0, L - T)));
        const igual = new Array(K).fill(1 / K);
        const med = (ww, base) => { const r = base[0].map((_, t) => base.reduce((acc, c, k) => acc + ww[k] * c[t], 0)); return metricas(r, cdi.slice(base === treino ? 0 : L - T, base === treino ? L - T : L)); };
        wf = [['Otimizada no passado', wT], ['Pesos iguais no universo', igual], ...(wRef ? [[`${ref.nome} (atual)`, wRef]] : [])]
          .map(([n, ww]) => ({ n, treino: med(ww, treino).cagr, depois: med(ww, prova), }));
        wf.meta = `treino ${rotulo(meses[0])}–${rotulo(meses[L - T - 1])} · teste ${rotulo(meses[L - T])}–${rotulo(meses[L - 1])}`;
      }
      const semPremio = O_.obj === 'sharpe' && Math.max(...mu) <= rfm;
      otUltimo = { semPremio, w, uni, est, mh, fronteira, isolados, wRef: wRef ? estatPesos(wRef, mu, C, rfm) : null, refNome: ref.nome, wf, meses, delta, ms: performance.now() - t0, K, L };
      desenharOtimizacao();
    } catch (e) { err(`Não foi possível otimizar: ${e.message}`); }
    $('btn-ot').disabled = false; $('btn-ot-label').textContent = 'Otimizar de novo';
  }, 30);
}
function desenharOtimizacao() {
  const o = otUltimo, O_ = S.ot; if (!o) return;
  const nomeObj = { sharpe: 'máximo Sharpe', minvar: 'mínimo risco', paridade: 'paridade de risco', alvo: `risco-alvo ${num(O_.volAlvo, 0)}%` }[O_.obj];
  $('ot-runline').innerHTML = [['Objetivo', nomeObj + (O_.reamostra ? ' · reamostrado 50×' : '')], ['Universo', `${o.K} ativos`], ['Janela', `${rotulo(o.meses[0])}–${rotulo(o.meses[o.L - 1])} (${o.L} m)`],
    ['Limites', `peso ≤ ${num(O_.pesoMax, 0)}%${O_.maxAtivos ? `, até ${O_.maxAtivos} ativos` : ''}`], ['Encolhimento da covariância (Ledoit-Wolf)', pct(o.delta, 0)], ['Tempo', `${Math.round(o.ms)} ms`]]
    .map(([k, v]) => `<span>${k}: <b>${v}</b></span>`).join('') +
    (o.semPremio ? '<span class="stale" style="display:block;margin-top:6px">Nenhum ativo do universo tem retorno esperado acima do CDI nesta janela, então não existe carteira de máximo Sharpe: foi usada a de mínimo risco. Considere incluir o CDI ou outras classes no universo.</span>' : '');
  const n = o.w.filter((x) => x > 0).length;
  const kpi = (k, v, s_, cls = '') => `<div class="kpi"><span class="k">${k}</span><span class="v ${cls}">${v}</span><span class="s">${s_}</span></div>`;
  $('ot-kpis').innerHTML = kpi('Retorno ao ano (estimado)', pct(o.est.ret), 'médias encolhidas (James-Stein)') + kpi('Volatilidade ao ano', pct(o.est.vol), 'quanto oscila; covariância Ledoit-Wolf') +
    kpi('Sharpe (retorno por risco)', num(o.est.sharpe), o.wRef ? `${esc(o.refNome)}: ${num(o.wRef.sharpe)}` : 'vs CDI') + kpi('Queda máxima (histórico)', pct(o.mh.maxdd, 0), `${n} ativos`, 'neg');
  const ord = [...o.w.keys()].filter((k) => o.w[k] > 0).sort((a, b) => o.w[b] - o.w[a]);
  $('ot-pesos').innerHTML = `<thead><tr><th>Ativo</th><th class="hide-s">Classe</th><th>Peso</th><th>Retorno ao ano</th><th>Volatilidade</th></tr></thead><tbody>` +
    ord.map((k) => { const a = info(o.uni[k]), is = o.isolados[k]; return `<tr><td class="t"><span class="tk">${esc(rotuloAtivo(o.uni[k]))}</span><span class="nm">${esc(a.tipo.startsWith('fundo') ? a.gestor : a.n)}</span></td><td class="hide-s"><span class="cls"><i class="sw" style="background:${corClasse[TIPOS[a.tipo][1]]}"></i>${TIPOS[a.tipo][0]}</span></td><td><b>${pct(o.w[k], 1)}</b></td><td>${pct(is.ret)}</td><td>${pct(is.vol, 0)}</td></tr>`; }).join('') + '</tbody>';
  $('ot-pesos-meta').textContent = `${n} ativos`;
  $('ot-salvar').innerHTML = S.carteiras.map((c, i) => `<button class="btn" type="button" data-slot="${i}">Substituir ${LETRAS[i]} (${esc(c.nome.slice(0, 12))})</button>`).join('') +
    `<button class="btn primary" type="button" data-slot="novo">Salvar como nova carteira</button>`;
  // gráfico
  const box = $('ot-chart'), W = largura(box), Hh = 280, ml = 50, mr = 14, mt = 12, mb = 30, iw = W - ml - mr, ih = Hh - mt - mb;
  // escala: fronteira, carteiras e a maior parte dos ativos (outliers extremos ficam fora e são contados)
  const volsIso = o.isolados.map((p) => p.vol).sort((a, b) => a - b);
  const xlim = Math.max(...o.fronteira.map((p) => p.vol), o.est.vol, o.wRef ? o.wRef.vol : 0, quantil(volsIso, 0.85)) * 1.25;
  const dentro = o.isolados.filter((p) => p.vol <= xlim), fora = o.isolados.length - dentro.length;
  const pts = [...dentro, ...o.fronteira, o.est, ...(o.wRef ? [o.wRef] : [])];
  const xmax = Math.max(...pts.map((p) => p.vol)) * 1.06 || 0.1, ymin = Math.min(0, ...pts.map((p) => p.ret)), ymax = Math.max(...pts.map((p) => p.ret)) * 1.08 || 0.1;
  const x = (v) => ml + (v / xmax) * iw, y = (v) => mt + ih - ((v - ymin) / (ymax - ymin || 1)) * ih;
  let svg = `<svg viewBox="0 0 ${W} ${Hh}" role="img" aria-label="Fronteira eficiente do universo">`;
  const pY = passoBonito(ymax - ymin, 4); for (let v = Math.ceil(ymin / pY) * pY; v <= ymax; v += pY) svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--rule)"/><text x="${ml - 6}" y="${y(v) + 4}" text-anchor="end">${pct(v, 0)}</text>`;
  const pX = passoBonito(xmax, 5); for (let v = 0; v <= xmax; v += pX) svg += `<text x="${x(v)}" y="${Hh - 14}" text-anchor="middle">${pct(v, 0)}</text>`;
  svg += `<text x="${ml + iw / 2}" y="${Hh - 1}" text-anchor="middle">volatilidade a.a. →</text>`;
  for (const p of dentro) svg += `<circle cx="${x(p.vol).toFixed(1)}" cy="${y(p.ret).toFixed(1)}" r="2.6" fill="var(--rule-2)"/>`;
  if (dentro.length <= 25) for (const p of dentro) svg += `<text x="${x(p.vol) + 4}" y="${y(p.ret) + 3}" style="font-size:9px">${esc(rotuloAtivo(p.t).slice(0, 8))}</text>`;
  svg += `<polyline points="${o.fronteira.map((p) => `${x(p.vol).toFixed(1)},${y(p.ret).toFixed(1)}`).join(' ')}" fill="none" stroke="var(--accent)" stroke-width="2"/>`;
  const marca = (p, cor, t) => `<circle cx="${x(p.vol)}" cy="${y(p.ret)}" r="6" fill="${cor}" stroke="var(--panel)" stroke-width="2"/><text x="${x(p.vol) + 9}" y="${y(p.ret) - 7}" style="fill:${cor};font-weight:600">${t}</text>`;
  if (o.wRef) svg += marca(o.wRef, 'var(--pB)', esc(o.refNome.slice(0, 14)));
  svg += marca(o.est, 'var(--amber)', 'Otimizada') + '</svg>';
  box.innerHTML = svg;
  $('ot-leg').innerHTML = '<span><i class="ln" style="border-color:var(--accent)"></i>Fronteira eficiente</span><span><i style="background:var(--rule-2)"></i>Ativos isolados</span>' +
    `<span>Retornos estimados com encolhimento (James-Stein)${fora ? ` · ${fora} ${fora === 1 ? 'ativo' : 'ativos'} de risco muito alto fora da escala` : ''}</span>`;
  // fora da amostra
  if (o.wf) {
    $('ot-wf-meta').textContent = o.wf.meta;
    $('ot-wf').innerHTML = `<thead><tr><th></th><th>Treino</th><th>Depois</th><th class="hide-s">Volatilidade depois</th><th>Queda depois</th></tr></thead><tbody>` +
      o.wf.map((l) => `<tr><td class="t" style="white-space:normal">${esc(l.n)}</td><td>${pct(l.treino)}</td><td class="${l.depois.cagr < 0 ? 'neg' : 'pos'}"><b>${pct(l.depois.cagr)}</b></td><td class="hide-s">${pct(l.depois.vol)}</td><td>${pct(l.depois.maxdd)}</td></tr>`).join('') + '</tbody>';
  } else { $('ot-wf').innerHTML = '<tbody><tr><td class="empty">Janela curta demais para o teste (precisa de 48+ meses).</td></tr></tbody>'; $('ot-wf-meta').textContent = ''; }
}
function salvarOtimizada(e) {
  const b = e.target.closest('[data-slot]'); if (!b || !otUltimo) return;
  const o = otUltimo, itens = o.uni.map((t, k) => ({ t, p: Math.round(o.w[k] * 1000) / 10 })).filter((i) => i.p >= 0.5);
  fecha100(itens);
  const nome = `Otimizada (${{ sharpe: 'Sharpe', minvar: 'mín. risco', paridade: 'paridade', alvo: 'risco-alvo' }[S.ot.obj]})`;
  if (b.dataset.slot === 'novo') { S.carteiras.push({ nome, itens }); S.ativa = S.carteiras.length - 1; } else { S.carteiras[+b.dataset.slot] = { nome, itens }; S.ativa = +b.dataset.slot; }
  mudouCarteiras(); desenharCarteira(); prepararOtimizador(); desenharOtimizacao();
  b.textContent = 'Salva ✓ — veja em Simulação';
}

// ---------------------------------------------------------------- início
let redim;
window.addEventListener('resize', () => {
  clearTimeout(redim);
  redim = setTimeout(() => {
    if (vista === 'simulacao' && ultimo) { desenharLeque(); desenharHistograma(); }
    if (vista === 'historico') desenharHistorico();
    if (vista === 'analise') desenharAnalise();
    if (vista === 'otimizar') desenharOtimizacao();
  }, 200);
});
(function inicio() {
  $('stamp').textContent = `${D.ativos.length.toLocaleString('pt-BR')} ativos · base ${rotulo(FIM_DADOS)}`;
  const [y, m, d] = D.atualizado.split('-');
  $('foot').textContent = `Dados até ${rotulo(FIM_DADOS)} (baixados em ${d}/${m}/${y}). Fontes: TradingView, CVM e Banco Central. Material educativo, não é recomendação de investimento.`;
  desenharCarteira(); desenharBase();
  irPara(location.hash.slice(1) || 'carteiras', false);
})();
