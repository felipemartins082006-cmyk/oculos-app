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
  bdr: ['BDR', 'us'], tesouro: ['Tesouro Direto', 'rf'],
};
const GRUPOS = [ // chips do seletor
  ['', 'Todos'], ['acao', 'Ações BR'], ['fii', 'FIIs (fundos imobiliários)'], ['fii:papel', 'FIIs de papel (recebíveis)'], ['fii:tijolo', 'FIIs de tijolo (imóveis)'], ['fii:hibrido', 'FIIs híbridos'],
  ['fiagro|fiinfra', 'Fiagro (agro) e FI-Infra (infraestrutura)'], ['etf', 'ETFs B3 (fundos de índice)'],
  ['fundo_rf', 'Fundos de renda fixa'], ['fundo_mm', 'Multimercados'], ['fundo_acoes|fundo_cambial', 'Fundos de ações e cambiais'],
  ['tesouro', 'Tesouro Direto'], ['bdr', 'BDRs (empresas de fora, na B3)'],
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
  ot: { fonte: 'filtro', classes: ['fii:papel'], minAnos: 5, maxUniv: 60, janela: 6, cart: 0, obj: 'sharpe', volAlvo: 8, pesoMax: 20, maxAtivos: 8, reamostra: true, estender: false, ddMax: 0, retModo: 'livre', retA: 12, retB: 18, retBase: 'est', cadaClasse: true },
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
        x.curto = 'redistribuir';
        x.janela = 40; x.btJanela = 40; x.n = Math.max(x.n || 0, 100000); x.versao = 3;
        delete x.desde; delete x.btDesde; delete x.estender;
      }
      if (x.curto === 'estimar') x.curto = 'redistribuir'; // dados estimados não existem mais
      const r = { ...p, ...x, ot: { ...p.ot, ...(x.ot || {}) } }; r.ot.estender = false;
      return r;
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
//   'cortar': a janela começa no ativo mais novo (só meses em que todos existem).
// Só dados reais: nenhum mês é estimado ou substituído por índice. Um mês em que alguma carteira não tem
// nenhum ativo com cotação fica de fora da amostra (a análise continua com os meses que existem).
function preparar(carts, desde, cambio = 1, modo = 'cortar') {
  if (modo === true || modo === 'estimar') modo = 'redistribuir'; if (modo === false) modo = 'cortar';
  const estender = false, redis = modo === 'redistribuir';
  const sr = serie;
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
    ini = Math.max(ini, novo);
  } else {
    for (const t of reais) { const s = sr(t); if (s.i0 > novo) { novo = s.i0; limitante = t; } ini = Math.max(ini, s.i0); }
  }
  const ss = reais.map(sr);
  // séries de cada carteira (null = tem renda fixa por modelo, que existe em todo mês)
  const porCart = cs.map((c) => (c.itens.some((i) => i.t.startsWith('RF:')) ? null : c.itens.map((i) => sr(i.t))));
  let pulados = 0;
  const meses = [];
  for (let mi = ini; mi <= fim; mi++) {
    if (isNaN(val(sc, mi)) || isNaN(val(si, mi)) || isNaN(val(sf, mi))) continue;
    if (!redis && ss.some((s) => isNaN(val(s, mi)))) continue;
    if (redis && !porCart.every((lista) => lista === null || lista.some((s) => !isNaN(val(s, mi))))) { if (mi >= ini) pulados++; continue; }
    meses.push(mi);
  }
  const L = meses.length;
  if (L < 24 && !redis) {
    // pouco histórico em comum para cortar a janela: os ativos entram quando começam (sem recusar a carteira)
    const H2 = preparar(carts, desde, cambio, 'redistribuir'); if (!H2.erro) H2.trocouModo = modo; return H2;
  }
  if (L < 1) return { erro: 'Nenhum mês com cotação real para estes ativos na janela escolhida. Aumente a janela.', meses };
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
  const P = W.map((w, c) => {
    const p = new Float64Array(L);
    for (let j = 0; j < L; j++) {
      let s = 0, sw = 0;
      for (let k = 0; k < uni.length; k++) if (w[k] && !isNaN(R[k][j])) { s += w[k] * R[k][j]; sw += w[k]; }
      p[j] = sw > 0 ? s / sw : 0; // sw = 0 não acontece: esses meses ficam fora da amostra
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
    proxies: [], pulados,
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
  if (H.pulados) a.push(`${H.pulados} ${H.pulados === 1 ? 'mês ficou' : 'meses ficaram'} fora da amostra porque nenhum ativo de alguma carteira teve cotação neles (sem negociação). Só entram meses com dados reais.`);
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
        if (resta === 0) { pos = (Math.random() * L) | 0; resta = o.metodo === 'bloco' ? o.bloco : o.metodo === 'estac' ? 1 + Math.floor(-Math.log(1 - Math.random()) * (o.bloco - 0.5)) : 1; } else pos = (pos + 1) % L;
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
const VIEWS = ['carteiras', 'explorar', 'simulacao', 'historico', 'analise', 'otimizar', 'metodo'];
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
  if (v === 'explorar') abrirExplorador();
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
  $('cart-aviso').innerHTML = novos.length ? `<b>Atenção:</b> ${novos.length === 1 ? 'este ativo é mais novo' : 'estes ativos são mais novos'} que a janela de ${rotuloJanela(S.janela)} (desde ${desdeDe(S.janela)}): ${novos.map((n) => `${esc(rotuloAtivo(n.t))} (desde ${rotulo(n.desde)})`).join(', ')}. ` +
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
// campo numérico livre com botão "Máximo"/"Todos": valor ≥ cheio = máximo
function ligarLivre(id, { obter, definir, min = 1, max = 1e9, cheio, inteiro = true, aoMudar }) {
  const box = $(id), el = box.querySelector('input'), bt = box.querySelector('[data-max]');
  const mostra = () => {
    const v = obter(), noMax = v >= cheio();
    bt.classList.toggle('on', noMax); bt.setAttribute('aria-pressed', noMax);
    if (document.activeElement !== el) el.value = noMax ? '' : String(v).replace('.', ',');
    el.placeholder = noMax ? bt.textContent.toLowerCase() : '';
  };
  el.addEventListener('input', () => {
    if (el.value.trim() === '') return;
    let v = lerDec(el.value); if (inteiro) v = Math.round(v);
    definir(Math.max(min, Math.min(max, v))); mostra(); aoMudar();
  });
  el.addEventListener('blur', mostra);
  bt.addEventListener('click', () => { definir(bt.dataset.valor ? +bt.dataset.valor : max); el.value = ''; mostra(); aoMudar(); });
  mostra();
  return mostra;
}
const ANOS_BASE = () => Math.floor(FIM_DADOS / 12) - Math.floor(BASE / 12) + 1; // anos com dados (desde 1995)
const rotuloJanela = (v) => (v >= ANOS_BASE() ? `toda a base (desde ${Math.floor(BASE / 12)})` : `${v} ${v === 1 ? 'ano' : 'anos'}`);
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
ligarSeg('seg-metodo', 'metodo'); ligarSeg('seg-curto', 'curto', () => { mudouParam(); desenharCarteira(); agendarResumo(); }); ligarSeg('seg-rebal', 'rebal');
ligarSeg('seg-cambio', 'cambio');
$('in-bloco').value = S.bloco;
$('in-bloco').addEventListener('input', () => { const v = Math.round(lerDec($('in-bloco').value)); if (v >= 1) { S.bloco = Math.min(v, 240); mudouParam(); } });
ligarLivre('nj-janela', { obter: () => S.janela, definir: (v) => { S.janela = v; }, max: 99, cheio: ANOS_BASE, aoMudar: () => { mudouParam(); desenharCarteira(); } });
// cenários: atalhos ou qualquer número (até 1 bilhão)
const mostraN = () => { if (document.activeElement !== $('in-n')) $('in-n').value = S.n.toLocaleString('pt-BR'); [...$('seg-n').children].forEach((b) => { const on = +b.dataset.v === S.n; b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); }); };
ligarSeg('seg-n', 'n', () => { mostraN(); mudouParam(); });
$('in-n').addEventListener('input', () => { const v = parseInt($('in-n').value.replace(/\D/g, ''), 10); if (v >= 100) { S.n = Math.min(v, 1e9); mostraN(); mudouParam(); } });
$('in-n').addEventListener('blur', mostraN); mostraN();
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
    estac: 'Bootstrap estacionário (Politis-Romano): sorteia trechos seguidos do passado com tamanho aleatório (média = tamanho do bloco). Preserva crises e tendências sem impor um tamanho fixo de bloco.',
    normal: 'Distribuição normal com média e covariância históricas. Gera cenários que nunca aconteceram, mas subestima eventos extremos.',
    t: 'Como a normal, mas com caudas gordas (t-Student, 5 graus de liberdade): mais meses muito bons e muito ruins.',
  };
  $('metodo-help').textContent = txt[S.metodo];
  $('f-bloco').classList.toggle('dim', S.metodo !== 'bloco' && S.metodo !== 'estac');
  const param = S.metodo === 'normal' || S.metodo === 't';
  $('seg-rebal').classList.toggle('dim', param);
  $('rebal-help').textContent = param ? 'Nos modelos Normal e t-Student a carteira é rebalanceada todo mês.' : S.rebal === 0 ? 'Os pesos derivam com o mercado; os aportes seguem os pesos-alvo.' : '';
  $('saque-body').classList.toggle('dim', !S.saqueOn);
  const d0 = desdeDe(S.janela);
  $('janela-help').textContent = `Usa o passado desde ${d0}${d0 > Math.floor(FIM_DADOS / 12) - S.janela + 1 ? ' (os dados começam em 1995, início do Plano Real)' : ''}. Se nenhum ativo da carteira for tão antigo, a contagem começa no ativo mais antigo.`;
  $('curto-help').textContent = {
    redistribuir: 'A contagem começa no ativo mais antigo da carteira. Um ativo mais novo entra no mês em que passa a existir; antes disso, o peso dele é dividido entre os outros ativos. Assim você vê a performance de todos.',
    cortar: 'Só usa os meses em que todos os ativos existiam: a janela começa no ativo mais novo (mais curta, mas sem redistribuição).',
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
  const metodoNome = { boot: 'bootstrap mensal', bloco: `blocos de ${o.bloco} m`, estac: `bootstrap estacionário (média ${o.bloco} m)`, normal: 'normal multivariada', t: 't-Student (ν=5)' }[o.metodo];
  $('runline').innerHTML = [
    ['Cenários', R.nS.toLocaleString('pt-BR')], ['Horizonte', `${R.N} meses`],
    ['Janela', `${rotulo(H.ini)}–${rotulo(H.fim)} (${H.L} m)`], ['Modelo', metodoNome],
    ['Rebal.', R.parametrico || o.rebal === 1 ? 'mensal' : o.rebal ? `${o.rebal} m` : 'nunca'],
    ['Base', o.real ? 'real (descontada a inflação, IPCA)' : 'nominal'], ['Tempo', ultimo.ms < 60e3 ? `${(ultimo.ms / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} s` : `${Math.round(ultimo.ms / 60e3)} min`],
    ['Ativos novos', NOME_CURTO[H.modo] || ''], ['Percentis', R.exato ? 'exatos' : 'histograma de 20 mil faixas'], ['Núcleos do processador', `${R.nucleos} de ${NUCLEOS}`],
  ].map(([k, v]) => `<span>${k}: <b>${v}</b></span>`).join('') + (H.limitante ? `<span>Contagem começa em ${rotulo(H.ini)}, por: <b>${esc(rotuloAtivo(H.limitante))}</b></span>` : '');
  if (Object.keys(H.estimados || {}).length) $('runline').innerHTML += `<span>Meses estimados por fatores: <b>${Object.entries(H.estimados).map(([t, n]) => `${esc(rotuloAtivo(t))} ${n}`).join(', ')}</b></span>`;
  const anosJ = H.L / 12, avisos = [];
  if (H.ini > idx(`${desdeDe(o.janela)}-01`)) avisos.push(`Você pediu ${o.janela >= ANOS_BASE() ? 'toda a base (desde 1995)' : `${o.janela} anos de passado`}, mas as carteiras escolhidas só têm dados reais em comum a partir de ${rotulo(H.ini)} (${anosJ < 1 ? `${H.L} meses` : `${num(anosJ, anosJ < 3 ? 1 : 0)} anos`}). Cada carteira começa no seu ativo mais antigo; a mais nova define o início.`);
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
  desenharLeque(); desenharHistograma(); desenharPercentis(); desenharMontanha();
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
  { const tot = r.fin.length || 1; let ac = 0; for (const v of r.fin) if (v < lo) ac++;
    dicaPontos(box, W, cont.map((c, i) => { ac += c; const v0 = lo + i * lg; return { x: x(v0) + iw / nb / 2, y: mt + ih - (c / cmax) * ih / 2, html: `<span class="k">entre</span> ${curto(v0)} <span class="k">e</span> ${curto(v0 + lg)}<br><b>${pct(c / tot, 1)}</b> <span class="k">dos cenários</span> · <span class="k">abaixo:</span> ${pct(ac / tot, 0)}` }; }), iw / nb, true); }
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
ligarSeg('seg-bt-modo', 'btModo', desenharHistorico); ligarSeg('seg-bt-escala', 'btEscala', desenharHistorico); ligarLivre('nj-bt-janela', { obter: () => S.btJanela, definir: (v) => { S.btJanela = v; salvar(); }, max: 99, cheio: ANOS_BASE, aoMudar: desenharHistorico });

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
  desenharSuperficieRetornos(H, lst);
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
  // se todos forem novos, a análise usa os próprios meses que existem (nunca recusa)
  const cartA = cart.itens.some((i) => !novos.includes(i)) ? { ...cart, itens: cart.itens.filter((i) => !novos.includes(i)) } : cart;
  if (cartA === cart) novos.length = 0;
  const H = cartA.itens.length ? preparar([cartA], Math.floor(BASE / 12), S.cambio, 'cortar') : { erro: 'Adicione ativos à carteira.' };
  const vazio = (msg) => { ['risk-bars', 'ef-chart'].forEach((id) => { $(id).innerHTML = `<p class="empty-note">${esc(msg)}</p>`; }); ['tbl-corr', 'tbl-ef'].forEach((id) => { $(id).innerHTML = ''; }); $('an-runline').innerHTML = ''; $('ef-leg').innerHTML = ''; };
  if (H.erro) return vazio(H.erro);
  const K = H.uni.length, L = H.L, w = H.W[0];
  $('an-runline').innerHTML = `<span>Janela: <b>${rotulo(H.ini)}–${rotulo(H.fim)} (${L} m)</b></span><span>Ativos: <b>${K}</b></span><span>Valores: <b>em reais</b></span>` +
    (novos.length ? `<span>Fora desta análise (menos de 2 anos de dados): <b>${novos.map((i) => esc(rotuloAtivo(i.t))).join(', ')}</b></span>` : '') +
    (H.trocouModo ? '<span>Ativos com pouco histórico em comum: cada um entra quando começa</span>' : '');
  // meses em que um ativo não existia: usa o retorno da carteira nesse mês (mesma regra de redistribuir o peso)
  const RR = H.Rc ? H.R.map((r, k) => H.Rc[0][k] || r) : H.R;
  const mu = RR.map((r) => media(r));
  const cov = []; for (let i = 0; i < K; i++) cov.push(new Float64Array(K));
  for (let i = 0; i < K; i++) for (let j = 0; j <= i; j++) { let s = 0; for (let t = 0; t < L; t++) s += (RR[i][t] - mu[i]) * (RR[j][t] - mu[j]); cov[i][j] = cov[j][i] = s / (L - 1); }
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
  dicaPontos(box, W, [...pontos.map((p) => ({ x: x(p.vol), y: y(p.ret), html: p.so !== undefined ? dicaRV(`100% em ${esc(rotuloAtivo(H.uni[p.so]))}`, p) : dicaRV('Combinação aleatória', p, p.ws ? dicaPesos(p.ws, H.uni) : '') })),
    ...[[minV, 'Mínimo risco'], [maxS, 'Máximo Sharpe'], [mesmoRisco, 'Mesmo risco, mais retorno'], [atual, `${esc(cart.nome)} (atual)`]].map(([p, n]) => ({ x: x(p.vol), y: y(p.ret), html: dicaRV(n, p, p.ws ? dicaPesos(p.ws, H.uni) : '') }))]);
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
function estimarRobusto(R, g) { // R: lista de colunas (Float64Array por ativo); g: classe de cada ativo (opcional)
  const K = R.length, L = R[0].length;
  const mu = R.map((r) => media(r));
  // James-Stein: médias puxadas para a média da própria classe (cripto não puxa a renda fixa para cima)
  const grupos = new Map();
  for (let k = 0; k < K; k++) { const c = g && g.length === K ? g[k] : 0; if (!grupos.has(c)) grupos.set(c, []); grupos.get(c).push(k); }
  for (const ix of grupos.values()) {
    if (ix.length < 3) continue;
    const m0 = ix.map((k) => mu[k]), alvo = media(m0);
    const varMedia = media(ix.map((k) => { let v = 0; for (const x of R[k]) v += (x - mu[k]) ** 2; return v / Math.max(1, L - 1); })) / L;
    let dist = 0; for (const m of m0) dist += (m - alvo) ** 2;
    const f = dist > 0 ? Math.max(0, 1 - (ix.length - 2) * varMedia / dist) : 0;
    for (const k of ix) mu[k] = alvo + f * (mu[k] - alvo);
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
function projetar(v, cap, piso) { // projeção no simplex com teto e piso: piso_i ≤ w_i ≤ cap, Σw = 1
  const p0 = (i) => (piso ? Math.min(piso[i], cap) : 0);
  let lo = Math.min(...v) - cap - 1, hi = Math.max(...v) + 1;
  for (let it = 0; it < 70; it++) { const tau = (lo + hi) / 2; let s = 0; for (let i = 0; i < v.length; i++) s += Math.min(cap, Math.max(p0(i), v[i] - tau)); if (s > 1) lo = tau; else hi = tau; }
  const tau = (lo + hi) / 2; const w = v.map((x, i) => Math.min(cap, Math.max(p0(i), x - tau))); const s = w.reduce((a, b) => a + b, 0) || 1;
  return w.map((x) => x / s);
}
function anualGeo(m, v) { return Math.pow(1 + m, 12) * Math.exp(-12 * v / (2 * (1 + m) ** 2)) - 1; }
function estatPesos(w, mu, C, rfm) { const m = dot(w, mu), v = dot(w, mulCv(C, w)); const ret = anualGeo(m, v), vol = Math.sqrt(Math.max(v, 0) * 12); return { ret, vol, sharpe: vol > 0 ? (ret - (Math.pow(1 + rfm, 12) - 1)) / vol : NaN }; }
function otimizarPesos(mu, C, obj, cap, rfm, volAlvo, piso) {
  const K = mu.length; cap = Math.max(cap, 1 / K + 1e-9);
  let lmax = 0; for (let i = 0; i < K; i++) lmax = Math.max(lmax, C[i].reduce((a, b) => a + Math.abs(b), 0));
  const passo = 1 / (2 * lmax + 1e-12);
  const minUtil = (lam, w0) => { // máx  w·μ − λ wCw  (gradiente projetado)
    let w = w0 ? w0.slice() : projetar(new Array(K).fill(1 / K), cap, piso);
    const eta = 1 / (2 * lam * lmax + 1e-12);
    for (let it = 0; it < 600; it++) { const g = mulCv(C, w); w = projetar(w.map((x, i) => x + eta * (mu[i] - 2 * lam * g[i])), cap, piso); }
    return w;
  };
  if (obj === 'minvar') { let w = projetar(new Array(K).fill(1 / K), cap, piso); for (let it = 0; it < 1500; it++) { const g = mulCv(C, w); w = projetar(w.map((x, i) => x - passo * g[i]), cap, piso); } return w; }
  if (obj === 'paridade') {
    let w = new Array(K).fill(1 / K);
    for (let it = 0; it < 400; it++) { const g = mulCv(C, w), v = dot(w, g); w = w.map((x, i) => x * Math.sqrt((v / K) / Math.max(x * g[i], 1e-16))); w = projetar(w.map((x) => x / w.reduce((a, b) => a + b, 0)), cap, piso); }
    return w;
  }
  if (obj === 'alvo') { // maior retorno com vol ≤ alvo: busca em λ
    const alvo = (volAlvo / 100) ** 2 / 12; let lo = 1e-3, hi = 1e5, melhor = null;
    for (let it = 0; it < 40; it++) { const lam = Math.sqrt(lo * hi), w = minUtil(lam, melhor); if (dot(w, mulCv(C, w)) > alvo) lo = lam; else { hi = lam; melhor = w; } }
    return melhor || minUtil(hi);
  }
  // máximo Sharpe. Se nenhum ativo tem retorno esperado acima do CDI, a carteira tangente não existe
  // (maximizar um Sharpe negativo levaria ao maior risco): usa mínima variância.
  if (Math.max(...mu) <= rfm) return otimizarPesos(mu, C, 'minvar', cap, rfm, volAlvo, piso);
  let melhor = null, mS = -Infinity;
  for (const lam of [0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512]) { const w = minUtil(lam); const e = estatPesos(w, mu, C, rfm); if (e.sharpe > mS) { mS = e.sharpe; melhor = w; } }
  let w = melhor;
  for (let it = 0; it < 800; it++) {
    const g = mulCv(C, w), m = dot(w, mu) - rfm, v = Math.max(dot(w, g), 1e-14), sd = Math.sqrt(v);
    const grad = mu.map((x, i) => x / sd - m * g[i] / (sd * v));
    const cand = projetar(w.map((x, i) => x + passo * sd * 0.5 * grad[i]), cap, piso);
    if (estatPesos(cand, mu, C, rfm).sharpe >= estatPesos(w, mu, C, rfm).sharpe - 1e-12) w = cand; else break;
  }
  return w;
}
// ---- restrições: retorno ao ano (acima de / igual a / entre) e queda máxima histórica
function limitesOt(op) {
  const a = (+op.retA || 0) / 100, b = (+op.retB || 0) / 100;
  const L = { rmin: -Infinity, rmax: Infinity, dd: op.ddMax > 0 ? op.ddMax / 100 : null, hist: op.retBase === 'hist' };
  if (op.retModo === 'min') L.rmin = a;
  else if (op.retModo === 'igual') { L.rmin = a - 0.0025; L.rmax = a + 0.0025; }
  else if (op.retModo === 'entre') { L.rmin = Math.min(a, b); L.rmax = Math.max(a, b); }
  L.ativo = L.dd !== null || isFinite(L.rmin) || isFinite(L.rmax);
  return L;
}
function quedaPesos(w, R) { // queda máxima do caminho histórico e gradiente de log(vale/pico)
  const K = R.length, T = R[0].length, rp = new Float64Array(T);
  let nivel = 0, pico = 0, iPico = -1, pior = 0, a = -1, b = -1, soma = 0;
  for (let t = 0; t < T; t++) {
    let r = 0; for (let k = 0; k < K; k++) if (w[k]) r += w[k] * R[k][t];
    rp[t] = Math.max(r, -0.999); const l = Math.log1p(rp[t]); nivel += l; soma += l;
    if (nivel > pico) { pico = nivel; iPico = t; }
    if (nivel - pico < pior) { pior = nivel - pico; a = iPico; b = t; }
  }
  return { dd: -Math.expm1(pior), pior, a, b, rp, cagr: Math.exp(soma / T * 12) - 1 };
}
function gradLog(R, rp, t0, t1, K) { const g = new Float64Array(K); for (let t = t0; t <= t1; t++) { const d = 1 / (1 + rp[t]); for (let k = 0; k < K; k++) g[k] += R[k][t] * d; } return g; }
function avaliarLimites(w, mu, C, R, lim) { // violações (em fração ao ano) e gradientes
  const K = w.length, Cw = mulCv(C, w), m = dot(w, mu), v = Math.max(dot(w, Cw), 1e-14);
  const q = quedaPesos(w, R), T = R[0].length;
  let ret, gRet;
  if (lim.hist) { ret = q.cagr; const g = gradLog(R, q.rp, 0, T - 1, K); gRet = Array.from(g, (x) => (1 + ret) * 12 / T * x); }
  else {
    ret = anualGeo(m, v); const E = Math.pow(1 + m, 12) * Math.exp(-6 * v / (1 + m) ** 2);
    const dm = E * (12 / (1 + m) + 12 * v / (1 + m) ** 3), dv = E * (-6 / (1 + m) ** 2);
    gRet = mu.map((x, i) => dm * x + dv * 2 * Cw[i]);
  }
  const viol = [], grads = [];
  if (ret < lim.rmin) { viol.push(lim.rmin - ret); grads.push(gRet.map((x) => -x)); }
  if (ret > lim.rmax) { viol.push(ret - lim.rmax); grads.push(gRet); }
  if (lim.dd !== null && q.dd > lim.dd && q.b >= 0) { const g = gradLog(R, q.rp, q.a + 1, q.b, K), e = Math.exp(q.pior); viol.push(q.dd - lim.dd); grads.push(Array.from(g, (x) => -e * x)); }
  if (lim.vol) { const vol = Math.sqrt(12 * v); if (vol > lim.vol) { viol.push(vol - lim.vol); grads.push(Cw.map((x) => 12 * x / vol)); } }
  return { ret, dd: q.dd, viol, grads, Cw, m, v };
}
// melhor carteira do objetivo que respeite os limites: gradiente projetado com penalidade crescente
function ajustarLimites(w0, mu, C, R, lim, obj, cap, rfm, piso) {
  if (!lim.ativo) return w0;
  const K = w0.length, ref = w0.slice();
  const pontua = (w, e) => { // objetivo (normalizado) e gradiente
    if (obj === 'sharpe') { const sd = Math.sqrt(e.v), m = e.m - rfm; return [m / sd, mu.map((x, i) => x / sd - m * e.Cw[i] / (sd * e.v))]; }
    if (obj === 'minvar') return [-e.v, e.Cw.map((x) => -2 * x)];
    if (obj === 'alvo') return [e.m, mu.slice()];
    let d = 0; const g = new Array(K); for (let i = 0; i < K; i++) { d += (w[i] - ref[i]) ** 2; g[i] = -2 * (w[i] - ref[i]); } return [-d, g];
  };
  let w = w0.slice(), e0 = avaliarLimites(w, mu, C, R, lim), [s0] = pontua(w, e0);
  const escala = Math.max(Math.abs(s0), 1e-6);
  const J = (w, rho) => { const e = avaliarLimites(w, mu, C, R, lim), [sc, gs] = pontua(w, e); let P = 0; for (const x of e.viol) P += x * x; return { e, j: sc / escala - rho * P, gs, P }; };
  for (const rho of [10, 100, 1e3, 1e4, 1e5, 1e6, 1e7]) {
    let atual = J(w, rho), eta = 0.05;
    for (let it = 0; it < 160 && eta > 1e-7; it++) {
      const g = atual.gs.map((x) => x / escala);
      atual.e.viol.forEach((vv, c) => { const gg = atual.e.grads[c]; for (let i = 0; i < K; i++) g[i] -= 2 * rho * vv * gg[i]; });
      let gmax = 0; for (const x of g) gmax = Math.max(gmax, Math.abs(x)); if (!(gmax > 0)) break;
      const cand = projetar(w.map((x, i) => x + eta * g[i] / gmax), cap, piso), nv = J(cand, rho);
      if (nv.j > atual.j) { w = cand; atual = nv; eta = Math.min(eta * 1.4, 0.5); } else eta /= 2;
    }
    if (atual.P < 1e-10) break;
  }
  return w;
}
// melhor ativo de uma classe para o objetivo e os limites pedidos (usado para garantir 1 ativo por classe)
function melhorDaClasse(cands, mu, C, R, op, rfm, lim) {
  const nota = (k) => { const sd = Math.sqrt(Math.max(C[k][k], 1e-14)); return op.obj === 'minvar' || op.obj === 'paridade' ? -sd : (mu[k] - rfm) / sd; };
  const respeita = (k) => {
    const ret = lim.hist ? quedaPesos(cands.map((j) => (j === k ? 1 : 0)), cands.map((j) => R[j])).cagr : anualGeo(mu[k], C[k][k]);
    if (ret < lim.rmin || ret > lim.rmax) return false;
    if (lim.dd !== null) { const e = new Array(R.length).fill(0); e[k] = 1; if (quedaPesos(e, R).dd > lim.dd) return false; }
    if (lim.vol && Math.sqrt(12 * C[k][k]) > lim.vol) return false;
    return true;
  };
  const ok = cands.filter(respeita);
  return (ok.length ? ok : cands).reduce((a, k) => (nota(k) > nota(a) ? k : a));
}
function otimizarCompleto(R, op, rfm) {
  const lim = limitesOt(op); if (op.obj === 'alvo') lim.vol = op.volAlvo / 100;
  const temLim = lim.ativo;
  const K = R.length, g = op.cadaClasse && op.grupos && op.grupos.length === K ? op.grupos : null;
  const classes = g ? [...new Set(g.filter((x) => x >= 0))] : [];
  const PISO = 0.02;
  const resolve = (cols, forc) => {
    const sub = cols.map((k) => R[k]); const { mu, C } = estimarRobusto(sub, op.classe ? cols.map((k) => op.classe[k]) : null);
    const cap = Math.max(op.pesoMax / 100, 1 / cols.length + 1e-9);
    const piso = forc ? cols.map((k) => (forc.has(k) ? Math.min(PISO, cap, 1 / Math.max(1, forc.size)) : 0)) : null;
    let w = otimizarPesos(mu, C, op.obj, op.pesoMax / 100, rfm, op.volAlvo, piso);
    if (temLim) w = ajustarLimites(w, mu, C, sub, { ...lim, ativo: true }, op.obj, cap, rfm, piso);
    return w;
  };
  let idxs = [...Array(K).keys()], w = resolve(idxs, null), forc = null;
  // pelo menos 1 ativo de cada classe marcada: a classe que ficou sem peso recebe o seu melhor ativo
  if (classes.length > 1) {
    const faltam = classes.filter((c) => !idxs.some((k) => g[k] === c && w[k] > 1e-4));
    if (faltam.length) {
      const { mu, C } = estimarRobusto(R, op.classe);
      forc = new Set();
      for (const c of faltam) forc.add(melhorDaClasse(idxs.filter((k) => g[k] === c), mu, C, R, op, rfm, lim));
      w = resolve(idxs, forc);
    }
  }
  const limite = op.maxAtivos ? Math.max(op.maxAtivos, classes.length) : 0;
  if (limite && w.filter((x) => x > 1e-4).length > limite) {
    const ord = idxs.slice().sort((a, b) => w[b] - w[a]), manter = new Set();
    if (classes.length > 1) for (const c of classes) { const k = ord.find((j) => g[j] === c); if (k !== undefined) manter.add(k); }
    for (const k of ord) { if (manter.size >= limite) break; manter.add(k); }
    idxs = [...manter].sort((a, b) => a - b);
    const f2 = classes.length > 1 ? new Set(classes.map((c) => idxs.filter((k) => g[k] === c).sort((a, b) => w[b] - w[a])[0]).filter((k) => k !== undefined)) : null;
    const ws = resolve(idxs, f2); w = new Array(K).fill(0); idxs.forEach((k, i) => { w[k] = ws[i]; });
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
// etapa final (pesos, limites, fronteira, ativos isolados e teste fora da amostra) — roda num núcleo separado
function finalOtimizacao(d) {
  const { R, op, rfm, rfmTreino, T } = d, K = R.length, L = R[0].length;
  const lim = limitesOt(op);
  const { mu, C, delta } = estimarRobusto(R, op.classe);
  let w = d.wMedia ? d.wMedia.slice() : otimizarCompleto(R, op, rfm);
  if (d.wMedia) {
    const g = op.cadaClasse && op.grupos && op.grupos.length === K ? op.grupos : null, classes = g ? [...new Set(g.filter((x) => x >= 0))] : [];
    const limite = op.maxAtivos ? Math.max(op.maxAtivos, classes.length) : 0;
    if (limite && w.filter((x) => x > 1e-4).length > limite) {
      const ord = [...w.keys()].sort((a, b) => w[b] - w[a]), manter = new Set();
      if (classes.length > 1) for (const c of classes) { const k = ord.find((j) => g[j] === c && w[j] > 0); if (k !== undefined) manter.add(k); }
      for (const k of ord) { if (manter.size >= limite) break; manter.add(k); }
      w = w.map((x, k) => (manter.has(k) ? x : 0));
    }
    let s = w.reduce((a, b) => a + b, 0); w = w.map((x) => x / s);
    if (lim.ativo || op.obj === 'alvo') { // a média das reamostragens pode sair dos limites: ajuste mínimo, só nos ativos escolhidos
      const ix = [...w.keys()].filter((k) => w[k] > 0), sub = ix.map((k) => R[k]), e = estimarRobusto(sub, op.classe ? ix.map((k) => op.classe[k]) : null);
      const l2 = { ...lim, ativo: true }; if (op.obj === 'alvo') l2.vol = op.volAlvo / 100;
      const pisoG = g && classes.length > 1 ? ix.map((k) => (ix.filter((j) => g[j] === g[k]).every((j) => w[j] <= w[k]) && g[k] >= 0 ? 0.01 : 0)) : null;
      const ws = ajustarLimites(ix.map((k) => w[k]), e.mu, e.C, sub, l2, 'perto', Math.max(op.pesoMax / 100, 1 / ix.length + 1e-9), rfm, pisoG);
      w = new Array(K).fill(0); ix.forEach((k, i) => { w[k] = ws[i] < 1e-4 ? 0 : ws[i]; }); s = w.reduce((a, b) => a + b, 0); w = w.map((x) => x / s);
    }
  }
  const cap = Math.max(op.pesoMax / 100, 1 / K + 1e-9);
  let lmax = 0; for (let i = 0; i < K; i++) lmax = Math.max(lmax, C[i].reduce((a, b) => a + Math.abs(b), 0));
  const fronteira = [];
  for (const lam of [0.2, 0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024]) {
    let ww = projetar(new Array(K).fill(1 / K), cap); const eta = 1 / (2 * lam * lmax);
    for (let it = 0; it < (K > 400 ? 250 : 500); it++) { const g = mulCv(C, ww); ww = projetar(ww.map((x, i) => x + eta * (mu[i] - 2 * lam * g[i])), cap); }
    fronteira.push(estatPesos(ww, mu, C, rfm));
  }
  fronteira.sort((a, b) => a.vol - b.vol);
  const isolados = mu.map((m, k) => ({ ret: anualGeo(m, C[k][k]), vol: Math.sqrt(C[k][k] * 12) }));
  const est = estatPesos(w, mu, C, rfm), lw = avaliarLimites(w, mu, C, R, { ...lim, hist: lim.hist });
  let wT = null;
  if (L >= 48) wT = otimizarCompleto(R.map((c) => c.slice(0, L - T)), { ...op, reamostra: false }, rfmTreino);
  return { w, delta, fronteira, isolados, est, wT, retLim: lw.ret, ddLim: lw.dd, semPremio: op.obj === 'sharpe' && Math.max(...mu) <= rfm };
}
function otimizarRobusto(R, op, rfm, aoProgresso) { // versão síncrona (sem núcleos paralelos)
  if (!op.reamostra) return otimizarCompleto(R, op, rfm);
  const K = R.length, soma = new Array(K).fill(0);
  for (let i = 0; i < 50; i++) { const w = otimizarCompleto(reamostrar(R, 1, 6, 1000 + i)[0], op, rfm); for (let k = 0; k < K; k++) soma[k] += w[k] / 50; if (aoProgresso) aoProgresso(i / 50); }
  return soma;
}
// núcleos paralelos para o otimizador
let otWorkerURL = null;
function criarWorkerOt() {
  try {
    if (!otWorkerURL) {
      const fns = [media, aleatorio, estimarRobusto, projetar, anualGeo, estatPesos, otimizarPesos, limitesOt, quedaPesos, gradLog, avaliarLimites, ajustarLimites, melhorDaClasse, otimizarCompleto, reamostrar, finalOtimizacao, otimizarCombos];
      const src = `const mulCv = ${mulCv.toString()};\nconst dot = ${dot.toString()};\n` + fns.map((f) => f.toString()).join('\n') +
        `\nonmessage = (e) => { const d = e.data;
  if (d.tipo === 'amostras') { const ws = []; for (let i = d.de; i < d.ate; i++) { ws.push(otimizarCompleto(reamostrar(d.R, 1, 6, 1000 + i)[0], d.op, d.rfm)); postMessage({ p: 1 }); } postMessage({ ws }); }
  else if (d.tipo === 'combos') postMessage({ res: otimizarCombos(d) });
  else postMessage({ fim: finalOtimizacao(d) }); };`;
      otWorkerURL = URL.createObjectURL(new Blob([src], { type: 'text/javascript' }));
    }
    return new Worker(otWorkerURL);
  } catch (e) { return null; }
}
let otCancelar = null;
function noWorkerOt(msg, aoPasso) {
  return new Promise((ok, falha) => {
    const w = semWorker ? null : criarWorkerOt();
    if (!w) {
      setTimeout(() => { try {
        if (msg.tipo === 'amostras') { const ws = []; for (let i = msg.de; i < msg.ate; i++) { ws.push(otimizarCompleto(reamostrar(msg.R, 1, 6, 1000 + i)[0], msg.op, msg.rfm)); aoPasso(); } ok({ ws }); } else ok({ fim: finalOtimizacao(msg) });
      } catch (e) { falha(e); } }, 20);
      return;
    }
    const antes = otCancelar;
    otCancelar = () => { w.terminate(); falha(new Error('cancelado')); if (antes) antes(); };
    w.onmessage = (e) => { if (e.data.p) { aoPasso(); return; } w.terminate(); ok(e.data); };
    w.onerror = (e) => { e.preventDefault(); w.terminate(); falha(new Error(e.message || 'erro no núcleo de cálculo')); };
    w.postMessage(msg);
  });
}
async function otimizarParalelo(R, op, rfm, rfmTreino, T, aoProgresso) {
  let wMedia = null, feitos = 0;
  const total = op.reamostra ? 50 : 0;
  if (op.reamostra) {
    const n = Math.max(1, Math.min(NUCLEOS, 50)), por = Math.ceil(50 / n), tarefas = [];
    for (let de = 0; de < 50; de += por) tarefas.push(noWorkerOt({ tipo: 'amostras', R, op, rfm, de, ate: Math.min(50, de + por) }, () => { feitos++; aoProgresso(feitos / (total + 3)); }));
    const partes = await Promise.all(tarefas);
    wMedia = new Array(R.length).fill(0);
    for (const pt of partes) for (const w of pt.ws) for (let k = 0; k < w.length; k++) wMedia[k] += w[k] / 50;
  }
  const r = await noWorkerOt({ tipo: 'final', R, op, rfm, rfmTreino, T, wMedia }, () => {});
  otCancelar = null;
  return r.fim;
}

// interface
const OT_CLASSES = [['acao', 'Ações BR'], ['fii', 'Todos os FIIs'], ['fii:papel', 'FIIs de papel'], ['fii:tijolo', 'FIIs de tijolo'], ['fii:hibrido', 'FIIs híbridos'],
  ['fiagro|fiinfra', 'Fiagro e Infra'], ['etf', 'ETFs B3'], ['tesouro', 'Tesouro Direto'], ['bdr', 'BDRs'], ['fundo_rf', 'Fundos de renda fixa'], ['fundo_mm', 'Multimercados'], ['fundo_acoes', 'Fundos de ações'],
  ['acao_us', 'Ações EUA'], ['etf_us', 'ETFs EUA'], ['cripto', 'Cripto'], ['indice|commodity|moeda', 'Índices, ouro e moedas']];
let otPronto = false, otUltimo = null; const otMostra = [];
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
    const ligaU = (id, chave, conv) => { const el = $(id); el.value = String(O_[chave] || '').replace('.', ','); el.addEventListener('input', () => { O_[chave] = conv(el.value); salvar(); otUltimo = null; prepararOtimizador(); }); };
    ligaU('ot-min', 'minAnos', (v) => Math.max(0, Math.min(40, lerDec(v))));
    ligaU('ot-dd', 'ddMax', (v) => (String(v).trim() === '' ? 0 : Math.max(0, Math.min(100, lerDec(v)))));
    ligaU('ot-ret-a', 'retA', (v) => lerDec(v)); ligaU('ot-ret-b', 'retB', (v) => lerDec(v));
    otMostra.push(ligarLivre('nj-ot-max', { obter: () => O_.maxUniv, definir: (v) => { O_.maxUniv = v; salvar(); }, min: 2, max: 1e6, cheio: () => 1e6, aoMudar: () => { otUltimo = null; prepararOtimizador(); } }));
    otMostra.push(ligarLivre('nj-ot-janela', { obter: () => O_.janela, definir: (v) => { O_.janela = v; salvar(); }, min: 1, max: 99, cheio: () => 99, aoMudar: () => { otUltimo = null; prepararOtimizador(); } }));
    for (const [id, chave] of [['ot-reamostra', 'reamostra'], ['ot-cada', 'cadaClasse']]) { $(id).checked = O_[chave]; $(id).addEventListener('change', () => { O_[chave] = $(id).checked; salvar(); prepararOtimizador(); }); }
    const seg = (id, chave) => $(id).addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; O_[chave] = isNaN(+b.dataset.v) ? b.dataset.v : +b.dataset.v; salvar(); prepararOtimizador(); });
    seg('seg-ot-fonte', 'fonte'); seg('seg-ot-obj', 'obj'); seg('seg-ot-cart', 'cart'); seg('seg-ot-ret', 'retModo'); seg('seg-ot-retbase', 'retBase');
    $('btn-ot').addEventListener('click', rodarOtimizador);
    $('ot-salvar').addEventListener('click', salvarOtimizada);
  }
  otMostra.forEach((f) => f());
  prepararCombos(); contarCombos();
  if (O_.cart >= S.carteiras.length) O_.cart = 0;
  $('seg-ot-cart').innerHTML = S.carteiras.map((c, i) => `<button type="button" data-v="${i}">${esc(c.nome.slice(0, 16))}</button>`).join('');
  const marca = (id, v) => [...$(id).children].forEach((b) => { const on = String(b.dataset.v) === String(v); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  marca('seg-ot-fonte', O_.fonte); marca('seg-ot-obj', O_.obj); marca('seg-ot-cart', O_.cart); marca('seg-ot-ret', O_.retModo); marca('seg-ot-retbase', O_.retBase);
  $('ot-ret-campos').hidden = O_.retModo === 'livre'; $('seg-ot-retbase').hidden = O_.retModo === 'livre'; $('f-ot-ret-b').hidden = O_.retModo !== 'entre';
  $('ot-ret-a-lbl').textContent = { min: 'Pelo menos', igual: 'Igual a', entre: 'De' }[O_.retModo] || 'Mínimo'; $('f-ot-ret-b').querySelector('label').textContent = 'Até';
  $('ot-ret-help').textContent = O_.retModo === 'livre' ? 'Sem exigência de retorno: vale só o objetivo.' :
    (O_.retBase === 'est' ? 'Estimado: média de cada ativo puxada para a média geral (James-Stein), mais conservador e menos sujeito a sorte. ' : 'Histórico: o retorno ao ano que a carteira teve na janela (pode ser otimista: o passado não se repete igual). ') +
    'Em reais, antes do IR. "Igual a" aceita ±0,25 p.p.';
  [...$('ot-classes').children].forEach((b) => { const on = O_.classes.includes(b.dataset.k); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  $('ot-filtro').hidden = O_.fonte !== 'filtro'; $('ot-cart').hidden = O_.fonte !== 'cart';
  $('f-ot-vol').classList.toggle('dim', O_.obj !== 'alvo');
  $('ot-obj-help').textContent = {
    sharpe: 'Maior retorno acima do CDI por unidade de risco.', minvar: 'A combinação que menos oscila.',
    paridade: 'Cada ativo contribui igualmente para o risco total (não depende das médias, mais estável).', alvo: 'O maior retorno sem passar da volatilidade escolhida.',
  }[O_.obj];
  const U = universoOtimizador();
  $('ot-univ-meta').textContent = U.erro ? '' : `${U.codigos.length} ativos`;
  if (!otUltimo) $('ot-runline').innerHTML = U.erro ? `<span>${esc(U.erro)}</span>` : `<span>Universo: <b>${U.codigos.length} ativos</b> com dados de ${rotulo(U.ini)} a ${rotulo(FIM_DADOS)}${U.codigos.length > 300 ? ' (acima de 300 ativos é feita uma pré-seleção de ~250)' : ''}. Toque em Otimizar.</span>` +
    (U.avisos && U.avisos.length ? `<span class="stale" style="display:block;margin-top:6px">Ajustes automáticos: ${esc(U.avisos.join('; '))}.</span>` : '');
}
function universoOtimizador() {
  const O_ = S.ot, maxU = O_.maxUniv >= 1e6 ? Infinity : O_.maxUniv;
  const sr = serie;
  const avisos = [];
  let cods;
  if (O_.fonte === 'cart') cods = S.carteiras[O_.cart].itens.map((i) => i.t);
  else {
    if (!O_.classes.length) return { erro: 'Marque pelo menos uma classe.' };
    // cada classe marcada entra com a sua parte do limite do universo (as maiores de cada uma),
    // para nenhuma classe sumir porque outra tem ativos "maiores"
    const porClasse = O_.classes.map((k) => [...ATIVOS.values()].filter((a) => noGrupo(a, k) && serie(a.t).v.length >= O_.minAnos * 12)
      .sort((a, b) => (b.tam || 0) - (a.tam || 0)).map((a) => a.t));
    cods = []; const vistos = new Set(); let i = 0;
    while (porClasse.some((l) => i < l.length)) { for (const l of porClasse) if (i < l.length && !vistos.has(l[i])) { vistos.add(l[i]); cods.push(l[i]); } i++; }
  }
  if (!cods.length) return { erro: 'Nenhum ativo nessas classes com esse histórico mínimo. Reduza o histórico mínimo.' };
  const vivo = (t) => t.startsWith('RF:') || sr(t).i1 >= FIM_DADOS - 1; // ainda com dados no fim da base
  const lacunas = (t, ini) => { if (t.startsWith('RF:')) return 0; const s_ = sr(t); let n = 0; for (let mi = Math.max(ini, s_.i0); mi <= s_.i1; mi++) if (isNaN(val(s_, mi))) n++; return n; };
  const inicio = (t) => (t.startsWith('RF:') ? -Infinity : sr(t).i0);
  cods = cods.filter(vivo);
  const lim = O_.fonte === 'cart' ? Infinity : maxU;
  const maximo = (lista) => { // janela máxima: começa no ativo mais novo, mas nunca menos de 3 anos
    let l = lista.filter((t) => inicio(t) <= FIM_DADOS - 35).slice(0, lim);
    const i0 = Math.max(serie('USDBRL').i0, BASE, ...l.map(inicio));
    return { l, i0 };
  };
  let ini, sel;
  if (O_.janela >= 99) { const m = maximo(cods); sel = m.l; ini = m.i0; }
  else {
    ini = Math.max(BASE, FIM_DADOS - O_.janela * 12 + 1);
    sel = cods.filter((t) => inicio(t) <= ini).slice(0, lim);
    if (sel.length < 2) { // poucos ativos tão antigos: a janela recua até onde há pelo menos 2
      const ord = cods.slice().sort((a, b) => inicio(a) - inicio(b)).slice(0, Math.max(2, Math.min(lim, 30)));
      const m = maximo(ord); sel = m.l; ini = m.i0;
      avisos.push(`poucos ativos têm ${O_.janela} anos de dados; a janela foi ajustada para começar em ${rotulo(ini)}`);
    }
  }
  // ativos com muitos meses sem negociação na janela (> 5%) ficam de fora; os poucos meses que faltam são preenchidos
  const n0 = sel.length; sel = sel.filter((t) => lacunas(t, ini) <= 0.05 * (FIM_DADOS - ini + 1));
  if (sel.length < n0) avisos.push(`${n0 - sel.length} ${n0 - sel.length === 1 ? 'ativo ficou' : 'ativos ficaram'} de fora por ter muitos meses sem negociação`);
  // garante as classes marcadas no universo (o ativo de cada uma com mais histórico, se nenhum coube)
  if (O_.fonte !== 'cart' && O_.classes.length > 1) for (const k of O_.classes) {
    if (sel.some((t) => info(t) && noGrupo(info(t), k))) continue;
    const c = cods.filter((t) => noGrupo(info(t), k) && lacunas(t, ini) <= 0.05 * (FIM_DADOS - ini + 1)).sort((a, b) => inicio(a) - inicio(b))[0];
    if (c) { sel.push(c); if (inicio(c) > ini) { ini = inicio(c); avisos.push(`a janela começou em ${rotulo(ini)} para incluir ${OT_CLASSES.find((x) => x[0] === k)[1]}`); } }
    else avisos.push(`nenhum ativo de ${OT_CLASSES.find((x) => x[0] === k)[1]} tem cotação contínua nesta janela`);
  }
  if (sel.length < 2) { sel = [...sel, 'RF:CDI:100'].filter((t, i, a) => a.indexOf(t) === i); if (!info('RF:CDI:100')) return { erro: 'Menos de 2 ativos com dados.' }; avisos.push('o CDI entrou no universo para completar 2 ativos'); }
  const grupos = sel.map((t) => (O_.fonte === 'cart' || !info(t) ? -1 : O_.classes.findIndex((k) => noGrupo(info(t), k))));
  return { codigos: sel, ini, avisos, grupos };
}
function rodarOtimizador() {
  const O_ = S.ot, err = (m) => { $('ot-err').textContent = m; $('ot-err').hidden = !m; };
  if (otCancelar) { otCancelar(); otCancelar = null; return; }
  const U = universoOtimizador(); if (U.erro) return err(U.erro);
  const H = preparar([{ nome: 'U', itens: U.codigos.map((t) => ({ t, p: 1 })) }], Math.floor(U.ini / 12), S.cambio, 'redistribuir');
  if (H.erro) return err(H.erro);
  err('');
  const i0 = Math.max(0, H.meses.findIndex((mi) => mi >= U.ini)), sel = (arr) => arr.slice(i0);
  const base = H.Rc ? H.Rc[0] : H.R;
  const R = base.map((r) => Float64Array.from(sel(r), (x) => (isFinite(x) ? x : 0))), cdi = sel(H.cdi), meses = sel(H.meses), uni = H.uni;
  if (R[0].length < 6) return err('A base tem menos de 6 meses em comum para esses ativos.');
  const K = R.length, L = R[0].length, T = Math.min(36, Math.floor(L / 3)), rfm = media(cdi);
  const gU = uni.map((t) => U.grupos[U.codigos.indexOf(t)] ?? -1);
  // universos muito grandes: pré-seleção (ativos usados por uma otimização rápida + os de melhor retorno por risco)
  let Rx = R, uniX = uni, pre = null;
  if (K > 300) {
    const { mu, C } = estimarRobusto(R, uni.map((t) => (t.startsWith('RF:') ? 'rf' : (TIPOS[info(t).tipo] || ['', info(t).tipo])[1])));
    const ordem = [...Array(K).keys()].sort((a, b) => (mu[b] - rfm) / Math.sqrt(C[b][b]) - (mu[a] - rfm) / Math.sqrt(C[a][a]));
    const minv = [...Array(K).keys()].sort((a, b) => C[a][a] - C[b][b]);
    const nG = new Set(gU.filter((x) => x >= 0)).size, porG = [];
    if (nG > 1) for (const c of new Set(gU)) if (c >= 0) porG.push(...ordem.filter((k) => gU[k] === c).slice(0, 15), ...minv.filter((k) => gU[k] === c).slice(0, 5));
    const ix = [...new Set([...ordem.slice(0, 200), ...minv.slice(0, 60), ...porG])].sort((a, b) => a - b);
    Rx = ix.map((k) => R[k]); uniX = ix.map((k) => uni[k]); pre = { de: K, para: ix.length };
  }
  const gX = uniX.map((t) => gU[uni.indexOf(t)]);
  const classeDe = (t) => (t.startsWith('RF:') ? 'rf' : (TIPOS[info(t).tipo] || ['', info(t).tipo])[1]);
  const opW = { ...O_, grupos: gX, cadaClasse: O_.cadaClasse && O_.fonte !== 'cart', classe: uniX.map(classeDe) };
  $('btn-ot-label').textContent = 'Otimizando… (toque para parar)';
  const t0 = performance.now();
  otimizarParalelo(Rx, opW, rfm, media(cdi.slice(0, L - T)), T, (f) => { $('btn-ot-label').textContent = `Otimizando… ${Math.round(f * 100)}% (toque para parar)`; })
    .then((F) => {
      const KX = Rx.length, w = F.w;
      const rp = Rx[0].map((_, t) => Rx.reduce((acc, col, k) => acc + w[k] * col[t], 0));
      const mh = metricas(rp, cdi);
      const ref = S.carteiras[S.ativa];
      const wRef = ref.itens.length && ref.itens.every((i) => uniX.includes(i.t)) ? (() => { const s_ = ref.itens.reduce((a, i) => a + i.p, 0); return uniX.map((t) => (ref.itens.find((i) => i.t === t)?.p || 0) / s_); })() : null;
      let wf = null;
      if (L >= 48 && F.wT) {
        const treino = Rx.map((c) => c.slice(0, L - T)), prova = Rx.map((c) => c.slice(L - T));
        const igual = new Array(KX).fill(1 / KX);
        const med = (ww, b_) => { const r = b_[0].map((_, t) => b_.reduce((acc, c, k) => acc + ww[k] * c[t], 0)); return metricas(r, cdi.slice(b_ === treino ? 0 : L - T, b_ === treino ? L - T : L)); };
        wf = [['Otimizada no passado', F.wT], ['Pesos iguais no universo', igual], ...(wRef ? [[`${ref.nome} (atual)`, wRef]] : [])].map(([n, ww]) => ({ n, treino: med(ww, treino).cagr, depois: med(ww, prova) }));
        wf.meta = `treino ${rotulo(meses[0])}–${rotulo(meses[L - T - 1])} · teste ${rotulo(meses[L - T])}–${rotulo(meses[L - 1])}`;
      }
      let estRef = null;
      if (wRef) { const e = estimarRobusto(Rx, opW.classe); estRef = estatPesos(wRef, e.mu, e.C, rfm); }
      const isolados = F.isolados.map((x, k) => ({ t: uniX[k], ...x }));
      const ddIso = Rx.map((col) => { let n = 0, pico = 0, dd = 0; for (const x of col) { n += Math.log1p(Math.max(x, -0.999)); if (n > pico) pico = n; if (n - pico < dd) dd = n - pico; } return Math.expm1(dd); });
      otUltimo = { semPremio: F.semPremio, w, uni: uniX, est: F.est, mh, fronteira: F.fronteira, isolados, wRef: estRef, refNome: ref.nome, wf, meses, delta: F.delta,
        ms: performance.now() - t0, K: KX, L, pre, avisos: U.avisos, ddIso, lim: limitesOt(O_), retLim: F.retLim, ddLim: F.ddLim,
        classes: opW.cadaClasse && O_.classes.length > 1 ? O_.classes.map((k, c) => ({ nome: OT_CLASSES.find((x) => x[0] === k)[1], n: w.filter((x, j) => x > 0 && gX[j] === c).length })) : null };
      desenharOtimizacao();
    })
    .catch((e) => { if (e.message !== 'cancelado') err(`Não foi possível otimizar: ${e.message}`); })
    .finally(() => { otCancelar = null; $('btn-ot-label').textContent = otUltimo ? 'Otimizar de novo' : 'Otimizar'; });
}
function desenharOtimizacao() {
  const o = otUltimo, O_ = S.ot; if (!o) return;
  const nomeObj = { sharpe: 'máximo Sharpe', minvar: 'mínimo risco', paridade: 'paridade de risco', alvo: `risco-alvo ${num(O_.volAlvo, 0)}%` }[O_.obj];
  $('ot-runline').innerHTML = [['Objetivo', nomeObj + (O_.reamostra ? ' · reamostrado 50×' : '')], ['Universo', `${o.K} ativos`], ['Janela', `${rotulo(o.meses[0])}–${rotulo(o.meses[o.L - 1])} (${o.L} m)`],
    ['Limites', `peso ≤ ${num(O_.pesoMax, 0)}%${O_.maxAtivos ? `, até ${O_.maxAtivos} ativos` : ''}`], ['Encolhimento da covariância (Ledoit-Wolf)', pct(o.delta, 0)], ['Tempo', o.ms < 2000 ? `${Math.round(o.ms)} ms` : `${num(o.ms / 1000, 1)} s`]]
    .map(([k, v]) => `<span>${k}: <b>${v}</b></span>`).join('') +
    (o.classes ? `<span style="display:block;margin-top:6px">Classes na carteira: ${o.classes.map((c) => `${esc(c.nome)} <b class="${c.n ? 'pos' : 'neg'}">${c.n ? `${c.n} ✓` : '0 ✗'}</b>`).join(' · ')}</span>` : '') +
    (o.pre ? `<span>Pré-seleção: <b>${o.pre.para} de ${o.pre.de} ativos</b> (melhor retorno por risco e menor risco)</span>` : '') +
    (o.avisos && o.avisos.length ? `<span class="stale" style="display:block;margin-top:6px">Ajustes automáticos: ${esc(o.avisos.join('; '))}.</span>` : '') +
    (() => { // restrições pedidas: atendidas ou o mais perto possível
      const L_ = o.lim, it = [], ok = (b) => (b ? '<b class="pos">✓</b>' : '<b class="neg">✗</b>');
      if (isFinite(L_.rmin) || isFinite(L_.rmax)) {
        const pedido = O_.retModo === 'min' ? `≥ ${num(O_.retA, 1)}%` : O_.retModo === 'igual' ? `= ${num(O_.retA, 1)}%` : `entre ${num(Math.min(O_.retA, O_.retB), 1)}% e ${num(Math.max(O_.retA, O_.retB), 1)}%`;
        const atende = o.retLim >= L_.rmin - 5e-4 && o.retLim <= L_.rmax + 5e-4;
        it.push(`Retorno ${L_.hist ? 'histórico' : 'estimado'} ${pedido}: ${pct(o.retLim)} ${ok(atende)}${atende ? '' : ' (o mais perto possível neste universo)'}`);
      }
      if (L_.dd !== null) { const atende = o.ddLim <= L_.dd + 5e-4; it.push(`Queda máxima ≤ ${num(O_.ddMax, 0)}%: ${pct(-o.ddLim, 0)} ${ok(atende)}${atende ? '' : ' (o mais perto possível neste universo)'}`); }
      return it.length ? `<span style="display:block;margin-top:6px">${it.join(' · ')}</span>` : '';
    })() +
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
  dicaPontos(box, W, [...dentro.map((p) => ({ x: x(p.vol), y: y(p.ret), html: dicaRV(`${esc(rotuloAtivo(p.t))} <span class="k">${esc(TIPOS[info(p.t).tipo][0])}</span>`, p, `<br><span class="k">${esc(info(p.t).n.slice(0, 40))}</span>`) })),
    ...o.fronteira.map((p) => ({ x: x(p.vol), y: y(p.ret), html: dicaRV('Ponto da fronteira eficiente', p) })),
    { x: x(o.est.vol), y: y(o.est.ret), html: dicaRV('Carteira otimizada', o.est, dicaPesos(o.w, o.uni)) }, ...(o.wRef ? [{ x: x(o.wRef.vol), y: y(o.wRef.ret), html: dicaRV(esc(o.refNome), o.wRef) }] : [])]);
  $('ot-leg').innerHTML = '<span><i class="ln" style="border-color:var(--accent)"></i>Fronteira eficiente</span><span><i style="background:var(--rule-2)"></i>Ativos isolados</span>' +
    `<span>Retornos estimados com encolhimento (James-Stein)${fora ? ` · ${fora} ${fora === 1 ? 'ativo' : 'ativos'} de risco muito alto fora da escala` : ''}</span>`;
  // fora da amostra
  if (o.wf) {
    $('ot-wf-meta').textContent = o.wf.meta;
    $('ot-wf').innerHTML = `<thead><tr><th></th><th>Treino</th><th>Depois</th><th class="hide-s">Volatilidade depois</th><th>Queda depois</th></tr></thead><tbody>` +
      o.wf.map((l) => `<tr><td class="t" style="white-space:normal">${esc(l.n)}</td><td>${pct(l.treino)}</td><td class="${l.depois.cagr < 0 ? 'neg' : 'pos'}"><b>${pct(l.depois.cagr)}</b></td><td class="hide-s">${pct(l.depois.vol)}</td><td>${pct(l.depois.maxdd)}</td></tr>`).join('') + '</tbody>';
  } else { $('ot-wf').innerHTML = '<tbody><tr><td class="empty">Janela curta demais para o teste (precisa de 48+ meses).</td></tr></tbody>'; $('ot-wf-meta').textContent = ''; }
  desenharFronteira3d(o);
}
function salvarOtimizada(e) {
  const b = e.target.closest('[data-slot]'); if (!b || !otUltimo) return;
  const o = otUltimo, itens = o.uni.map((t, k) => ({ t, p: Math.round(o.w[k] * 1000) / 10 })).filter((i) => i.p >= 0.5);
  fecha100(itens);
  const nome = `Otimizada (${{ sharpe: 'Sharpe', minvar: 'mín. risco', paridade: 'paridade', alvo: 'risco-alvo' }[S.ot.obj]}${S.ot.retModo !== 'livre' ? ', retorno-alvo' : ''}${S.ot.ddMax > 0 ? `, queda ≤ ${num(S.ot.ddMax, 0)}%` : ''})`;
  if (b.dataset.slot === 'novo') { S.carteiras.push({ nome, itens }); S.ativa = S.carteiras.length - 1; } else { S.carteiras[+b.dataset.slot] = { nome, itens }; S.ativa = +b.dataset.slot; }
  mudouCarteiras(); desenharCarteira(); prepararOtimizador(); desenharOtimizacao();
  b.textContent = 'Salva ✓ — veja em Simulação';
}

// ---------------------------------------------------------------- dicas que seguem o cursor (gráficos SVG)
// pontos: [{ x, y, html }] em coordenadas do SVG. Mostra o mais próximo do cursor/dedo, com mira.
function dicaPontos(box, W, pontos, raio = 22, soX = false) {
  const svg = box.querySelector('svg'); if (!svg || !pontos.length) return;
  box.classList.add('hov');
  let tip = box.querySelector('.tip-svg'); if (!tip) { tip = document.createElement('div'); tip.className = 'tip-svg'; tip.hidden = true; box.appendChild(tip); }
  const ns = 'http://www.w3.org/2000/svg', anel = document.createElementNS(ns, 'circle');
  anel.setAttribute('r', 7); anel.setAttribute('fill', 'none'); anel.setAttribute('stroke', 'var(--ink)'); anel.setAttribute('stroke-width', 2); anel.setAttribute('opacity', 0); anel.classList.add('hov-line');
  svg.appendChild(anel);
  const mover = (e) => {
    const r = svg.getBoundingClientRect(), k = W / r.width, px = (e.clientX - r.left) * k, py = (e.clientY - r.top) * k;
    let m = null, dm = (raio * k) ** 2; for (const p of pontos) { const d = (p.x - px) ** 2 + (soX ? 0 : (p.y - py) ** 2); if (d < dm) { dm = d; m = p; } }
    if (!m) { tip.hidden = true; anel.setAttribute('opacity', 0); return; }
    anel.setAttribute('cx', m.x); anel.setAttribute('cy', m.y); anel.setAttribute('opacity', 0.9);
    tip.innerHTML = m.html; tip.hidden = false;
    const bx = box.getBoundingClientRect(), sx = m.x / k + (r.left - bx.left), sy = m.y / k + (r.top - bx.top);
    let lx = sx + 12, ly = sy + 12; if (lx + tip.offsetWidth > bx.width - 4) lx = sx - tip.offsetWidth - 12; if (ly + tip.offsetHeight > bx.height) ly = sy - tip.offsetHeight - 12;
    tip.style.left = `${Math.max(2, lx)}px`; tip.style.top = `${Math.max(2, ly)}px`;
  };
  svg.addEventListener('pointermove', mover); svg.addEventListener('pointerdown', mover);
  svg.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') { tip.hidden = true; anel.setAttribute('opacity', 0); } });
}
const dicaRV = (titulo, p, extra = '') => `<b>${titulo}</b><br><span class="k">retorno</span> ${pct(p.ret)} · <span class="k">volat.</span> ${pct(p.vol)}${isFinite(p.sharpe) ? ` · <span class="k">Sharpe</span> ${num(p.sharpe)}` : ''}${extra}`;
const dicaPesos = (ws, uni) => { const o = [...ws.keys()].filter((i) => ws[i] >= 0.01).sort((a, b) => ws[b] - ws[a]).slice(0, 4); return o.length ? `<br>${o.map((i) => `${esc(rotuloAtivo(uni[i]).slice(0, 9))} ${pct(ws[i], 0)}`).join(' · ')}` : ''; };

// ---------------------------------------------------------------- escala por eixo (X, Y, Z): barra, lista fixa de % e % livre
const ESCALAS = [10, 25, 50, 75, 100, 125, 150, 200, 300, 400, 500];
function controleEixos(box, estado, eixos, aoMudar) {
  // eixos: [['x', 'horário'], ['y', '...'], ['z', '...']]; estado: { x: 100, y: 100, z: 100 }
  box.classList.add('eixos-ctl');
  box.innerHTML = eixos.map(([e, nome]) => `<div class="eixo-ctl" data-e="${e}"><span class="nm">${e.toUpperCase()} · ${esc(nome)}</span>` +
    `<input type="range" min="10" max="500" step="5" aria-label="Aumentar ou diminuir o eixo ${e.toUpperCase()}">` +
    `<select aria-label="Porcentagem fixa do eixo ${e.toUpperCase()}"><option value="">lista</option>${ESCALAS.map((p) => `<option value="${p}">${p}%</option>`).join('')}</select>` +
    `<input type="number" inputmode="numeric" min="1" max="2000" placeholder="outro %" aria-label="Porcentagem livre do eixo ${e.toUpperCase()}"><output></output></div>`).join('') +
    '<div class="eixo-rod"><button type="button" class="btn" data-zera>100% nos eixos</button><span>Barra de 10% a 500%, lista fixa ou qualquer % de 1 a 2000.</span></div>';
  const mostra = () => { for (const [e] of eixos) { const l = box.querySelector(`[data-e="${e}"]`), v = estado[e] || 100;
    l.querySelector('input[type=range]').value = Math.min(500, Math.max(10, v)); l.querySelector('select').value = ESCALAS.includes(v) ? String(v) : ''; l.querySelector('output').textContent = `${v}%`; } };
  const muda = (e, v) => { if (!(v >= 1 && v <= 2000)) return; estado[e] = Math.round(v); mostra(); aoMudar(); };
  for (const [e] of eixos) {
    const l = box.querySelector(`[data-e="${e}"]`);
    l.querySelector('input[type=range]').addEventListener('input', (ev) => muda(e, +ev.target.value));
    l.querySelector('select').addEventListener('change', (ev) => { if (ev.target.value) muda(e, +ev.target.value); });
    const livre = l.querySelector('input[type=number]');
    livre.addEventListener('change', () => muda(e, +livre.value)); livre.addEventListener('keydown', (ev) => { if (ev.key === 'Enter') muda(e, +livre.value); });
  }
  box.querySelector('[data-zera]').addEventListener('click', () => { for (const [e] of eixos) estado[e] = 100; mostra(); aoMudar(); });
  mostra();
  return { mostra, mostrarEixo: (e, on) => { const l = box.querySelector(`[data-e="${e}"]`); if (l) l.hidden = !on; } };
}

// ---------------------------------------------------------------- navegação livre nos gráficos 3D (W A S D)
// W/S: avança e recua para onde a câmera olha · A/D: para os lados · E/Q: sobe e desce ·
// setas: gira · +/−: aproxima e afasta · Shift: mais rápido · R: volta ao início.
// No celular: o controle na tela faz o mesmo (segure o botão).
const NAV_TECLAS = { w: 'w', a: 'a', s: 's', d: 'd', q: 'q', e: 'e', arrowup: 'cima', arrowdown: 'baixo', arrowleft: 'esq', arrowright: 'dir', '+': 'mais', '=': 'mais', '-': 'menos', _: 'menos' };
function ligarNav3d(canvas, obj, pedir, op = {}) {
  if (!obj.alvo) obj.alvo = { x: 0, y: 0, z: 0 };
  canvas.tabIndex = 0;
  canvas.setAttribute('aria-keyshortcuts', 'W A S D Q E ArrowUp ArrowDown ArrowLeft ArrowRight + - R');
  const segurando = new Set(); let rapido = false, ultimo = 0, laco = 0;
  const passo = (t) => {
    if (!segurando.size) { laco = 0; ultimo = 0; return; }
    const dt = ultimo ? Math.min(0.05, (t - ultimo) / 1000) : 0.016; ultimo = t;
    if (op.plano && op.plano()) { op.mover2d(segurando, dt * (rapido ? 3 : 1)); pedir(); laco = requestAnimationFrame(passo); return; }
    const v = (rapido ? 2.4 : 0.8) * dt / Math.max(0.5, obj.rot.zoom), yaw = obj.rot.yaw, A = obj.alvo;
    const fx = Math.sin(yaw), fz = Math.cos(yaw), rx = Math.cos(yaw), rz = -Math.sin(yaw);
    if (segurando.has('w')) { A.x += fx * v; A.z += fz * v; }
    if (segurando.has('s')) { A.x -= fx * v; A.z -= fz * v; }
    if (segurando.has('d')) { A.x += rx * v; A.z += rz * v; }
    if (segurando.has('a')) { A.x -= rx * v; A.z -= rz * v; }
    if (segurando.has('e')) A.y += v;
    if (segurando.has('q')) A.y -= v;
    const g = (rapido ? 2.2 : 1.2) * dt;
    if (segurando.has('esq')) obj.rot.yaw -= g;
    if (segurando.has('dir')) obj.rot.yaw += g;
    if (segurando.has('cima')) obj.rot.pitch = Math.min(1.5, obj.rot.pitch + g);
    if (segurando.has('baixo')) obj.rot.pitch = Math.max(-1.5, obj.rot.pitch - g);
    if (segurando.has('mais')) obj.rot.zoom = Math.min(8, obj.rot.zoom * (1 + dt * 1.5));
    if (segurando.has('menos')) obj.rot.zoom = Math.max(0.3, obj.rot.zoom / (1 + dt * 1.5));
    pedir(); laco = requestAnimationFrame(passo);
  };
  const inicia = () => { if (!laco) laco = requestAnimationFrame(passo); };
  const reinicia = () => { obj.alvo.x = obj.alvo.y = obj.alvo.z = 0; if (op.reiniciar) op.reiniciar(); pedir(); };
  canvas.addEventListener('keydown', (e) => {
    const k = e.key.toLowerCase(); rapido = e.shiftKey;
    if (k === 'r' || k === '0') { e.preventDefault(); reinicia(); return; }
    const n = NAV_TECLAS[k]; if (!n) return;
    e.preventDefault(); segurando.add(n); inicia();
  });
  canvas.addEventListener('keyup', (e) => { rapido = e.shiftKey; const n = NAV_TECLAS[e.key.toLowerCase()]; if (n) segurando.delete(n); });
  canvas.addEventListener('blur', () => segurando.clear());
  canvas.addEventListener('pointerdown', () => canvas.focus({ preventScroll: true }));
  // controle na tela (celular e quem prefere o mouse)
  const box = canvas.parentElement, pad = document.createElement('div');
  pad.className = 'nav3d';
  pad.innerHTML = '<span class="nav-dica">Clique no gráfico e use <kbd>W</kbd><kbd>A</kbd><kbd>S</kbd><kbd>D</kbd> · <kbd>Q</kbd>/<kbd>E</kbd> desce/sobe · setas giram · <kbd>R</kbd> volta</span>' +
    '<div class="nav-pad" role="group" aria-label="Navegar no gráfico 3D">' +
    [['q', 'Descer', '▾'], ['w', 'Avançar', 'W'], ['e', 'Subir', '▴'], ['a', 'Esquerda', 'A'], ['s', 'Recuar', 'S'], ['d', 'Direita', 'D']]
      .map(([k, t, l]) => `<button type="button" data-nav="${k}" title="${t}" aria-label="${t}">${l}</button>`).join('') +
    '<button type="button" data-nav="r" class="nav-r" title="Voltar ao início" aria-label="Voltar ao início">↺</button></div>';
  box.appendChild(pad);
  const solta = () => { for (const k of ['w', 'a', 's', 'd', 'q', 'e']) segurando.delete(k); };
  pad.addEventListener('pointerdown', (e) => { const b = e.target.closest('[data-nav]'); if (!b) return; e.preventDefault(); if (b.dataset.nav === 'r') { reinicia(); return; } segurando.add(b.dataset.nav); inicia(); });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => pad.addEventListener(ev, solta));
  return { reinicia };
}

// ---------------------------------------------------------------- cena 3D genérica (canvas): pontos e superfícies
// Coordenadas normalizadas em [-1, 1]; Y é a altura. Arraste para girar, role/pince para aproximar,
// passe o cursor para a dica. A escala de cada eixo (%) estica ou encolhe o eixo.
class Cena3D {
  constructor(canvas, tip, op = {}) {
    Object.assign(this, { cv: canvas, tip, rot: { yaw: -0.7, pitch: 0.45, zoom: 1, ...(op.rot || {}) }, escala: op.escala || { x: 100, y: 100, z: 100 },
      rotulos: op.rotulos || { x: '', y: '', z: '' }, ticks: { x: [], y: [], z: [] }, pontos: [], sup: null, hover: null, mouse: null, quadro: 0, girando: false, linhas: [] });
    const pos = (e) => { const r = canvas.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; };
    let ini = null, pinca = null; const ptrs = new Map();
    canvas.addEventListener('pointerdown', (e) => { e.preventDefault(); canvas.setPointerCapture(e.pointerId); const q = pos(e); ptrs.set(e.pointerId, q); ini = { ...q, rot: { ...this.rot }, mov: 0 };
      if (ptrs.size === 2) { const [a, b] = [...ptrs.values()]; pinca = { d: Math.hypot(a.x - b.x, a.y - b.y), zoom: this.rot.zoom }; } });
    canvas.addEventListener('pointermove', (e) => {
      const q = pos(e); if (ptrs.has(e.pointerId)) ptrs.set(e.pointerId, q);
      if (pinca && ptrs.size === 2) { const [a, b] = [...ptrs.values()]; this.rot.zoom = Math.max(0.4, Math.min(6, pinca.zoom * Math.hypot(a.x - b.x, a.y - b.y) / (pinca.d || 1))); this.pedir(); return; }
      if (ini && ptrs.size === 1) { const dx = q.x - ini.x, dy = q.y - ini.y; ini.mov = Math.max(ini.mov, Math.abs(dx) + Math.abs(dy));
        if (ini.mov > 4) { this.arrastando = true; this.rot.yaw = ini.rot.yaw + dx * 0.009; this.rot.pitch = Math.max(-1.45, Math.min(1.45, ini.rot.pitch + dy * 0.009)); this.tip.hidden = true; this.hover = null; this.pedir(); return; } }
      this.mouse = q; this.pedir();
    });
    const fim = (e) => {
      if (this.aoClicar && ini && ini.mov <= 4 && ptrs.size === 1) { const q = pos(e); let m = null, dm = 24 * 24; for (const a of this.alvos || []) { const dd = (a.x - q.x) ** 2 + (a.y - q.y) ** 2; if (dd < dm) { dm = dd; m = a; } } if (m) this.aoClicar(m); }
      ptrs.delete(e.pointerId); if (ptrs.size < 2) pinca = null; if (ptrs.size === 0) { ini = null; this.arrastando = false; } this.pedir(); };
    canvas.addEventListener('pointerup', fim); canvas.addEventListener('pointercancel', fim);
    canvas.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') { this.mouse = null; this.hover = null; this.tip.hidden = true; this.pedir(); } });
    canvas.addEventListener('wheel', (e) => { e.preventDefault(); this.rot.zoom = Math.max(0.4, Math.min(6, this.rot.zoom * (e.deltaY > 0 ? 0.9 : 1.1))); this.pedir(); }, { passive: false });
    canvas.addEventListener('dblclick', () => { this.rot = { yaw: -0.7, pitch: 0.45, zoom: 1, ...(op.rot || {}) }; this.alvo = { x: 0, y: 0, z: 0 }; this.pedir(); });
    this.alvo = { x: 0, y: 0, z: 0 };
    ligarNav3d(canvas, this, () => this.pedir(), { reiniciar: () => { this.rot = { yaw: -0.7, pitch: 0.45, zoom: 1, ...(op.rot || {}) }; } });
    new ResizeObserver(() => this.pedir()).observe(canvas);
  }
  girar(on) { this.girando = on; const passo = () => { if (!this.girando) return; if (!this.arrastando && this.cv.offsetParent) { this.rot.yaw += 0.005; this.desenhar(); } requestAnimationFrame(passo); }; if (on) passo(); }
  pedir() { if (this.quadro) return; this.quadro = requestAnimationFrame(() => { this.quadro = 0; this.desenhar(); }); }
  proj(W, H) {
    const { yaw, pitch, zoom } = this.rot, cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
    const kx = this.escala.x / 100, ky = this.escala.y / 100, kz = this.escala.z / 100, f = Math.min(W, H * 1.3) * 0.27 * zoom, d = 3.8;
    const A = this.alvo || { x: 0, y: 0, z: 0 };
    return (X, Y, Z) => { X = (X - A.x) * kx; Y = (Y - A.y) * ky; Z = (Z - A.z) * kz; const x1 = X * cy - Z * sy, z1 = X * sy + Z * cy, y2 = Y * cp - z1 * sp, z2 = Y * sp + z1 * cp;
      if (d + z2 < 0.25) return { x: NaN, y: NaN, z: z2, s: 0 };   // atrás da câmera
      const s = f * d / (d + z2); return { x: W / 2 + x1 * s, y: H / 2 + 14 - y2 * s, z: z2, s: s / f }; };
  }
  desenhar() {
    const cv = this.cv, W = cv.clientWidth, H = cv.clientHeight, dpr = window.devicePixelRatio || 1; if (!W || !H) return;
    if (cv.width !== Math.round(W * dpr) || cv.height !== Math.round(H * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
    const ctx = cv.getContext('2d'); ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, W, H);
    const C = { ink: exCor('--ink'), muted: exCor('--muted'), rule: exCor('--rule'), rule2: exCor('--rule-2'), accent: exCor('--accent'), panel: exCor('--panel') };
    const P = this.proj(W, H);
    // chão quadriculado, caixa e rótulos
    ctx.lineWidth = 1; ctx.strokeStyle = C.rule;
    for (let i = -1; i <= 1.001; i += 0.5) { const a = P(i, -1, -1), b = P(i, -1, 1), c = P(-1, -1, i), e = P(1, -1, i); ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.moveTo(c.x, c.y); ctx.lineTo(e.x, e.y); ctx.stroke(); }
    ctx.strokeStyle = C.rule2;
    const cantos = []; for (const X of [-1, 1]) for (const Y of [-1, 1]) for (const Z of [-1, 1]) cantos.push([X, Y, Z]);
    for (let i = 0; i < 8; i++) for (let j = i + 1; j < 8; j++) { const a = cantos[i], b = cantos[j]; if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) !== 2) continue; const pa = P(...a), pb = P(...b); ctx.beginPath(); ctx.moveTo(pa.x, pa.y); ctx.lineTo(pb.x, pb.y); ctx.stroke(); }
    ctx.font = '10px "IBM Plex Mono", monospace'; ctx.fillStyle = C.muted; ctx.textAlign = 'center';
    for (const t of this.ticks.x) { const q = P(t.v, -1, 1.15); ctx.fillText(t.txt, q.x, q.y + 4); }
    for (const t of this.ticks.y) { const q = P(-1.16, t.v, -1); ctx.fillText(t.txt, q.x, q.y + 3); }
    for (const t of this.ticks.z) { const q = P(1.17, -1, t.v); ctx.fillText(t.txt, q.x, q.y + 4); }
    ctx.font = '600 11px "IBM Plex Sans", sans-serif'; ctx.fillStyle = C.ink;
    { const a = P(0, -1, 1.42), b = P(-1.22, 1.22, -1), c = P(1.45, -1, 0); ctx.fillText(this.rotulos.x, a.x, a.y + 6); ctx.fillText(this.rotulos.y, b.x, b.y); ctx.fillText(this.rotulos.z, c.x, c.y + 6); }
    const alvos = [];
    // superfície: quadriláteros do fundo para a frente, sombreados pela inclinação
    if (this.sup) {
      const { nx, nz, Y, X, Z, cor, html } = this.sup, quads = [];
      for (let i = 0; i < nx - 1; i++) for (let j = 0; j < nz - 1; j++) {
        const h = [Y[i][j], Y[i + 1][j], Y[i + 1][j + 1], Y[i][j + 1]]; if (h.some((v) => !isFinite(v))) continue;
        const v = [P(X[i], h[0], Z[j]), P(X[i + 1], h[1], Z[j]), P(X[i + 1], h[2], Z[j + 1]), P(X[i], h[3], Z[j + 1])];
        if (v.some((q) => !isFinite(q.x))) continue;
        const luz = 0.6 + 0.4 * Math.max(0, Math.min(1, 0.5 + ((h[0] + h[3]) - (h[1] + h[2])) * 0.9 + ((h[0] + h[1]) - (h[2] + h[3])) * 0.5));
        quads.push({ v, z: (v[0].z + v[1].z + v[2].z + v[3].z) / 4, i, j, hm: (h[0] + h[1] + h[2] + h[3]) / 4, luz });
      }
      quads.sort((a, b) => b.z - a.z);
      for (const q of quads) {
        ctx.beginPath(); ctx.moveTo(q.v[0].x, q.v[0].y); for (let k = 1; k < 4; k++) ctx.lineTo(q.v[k].x, q.v[k].y); ctx.closePath();
        ctx.globalAlpha = 0.95; ctx.fillStyle = cor(q.i, q.j, q.hm); ctx.fill();
        ctx.globalAlpha = (1 - q.luz) * 0.6; ctx.fillStyle = '#000'; ctx.fill();
        ctx.globalAlpha = 0.3; ctx.strokeStyle = C.panel; ctx.lineWidth = 0.6; ctx.stroke();
        alvos.push({ x: (q.v[0].x + q.v[2].x) / 2, y: (q.v[0].y + q.v[2].y) / 2, z: q.z, html: () => html(q.i, q.j), quad: q });
      }
      ctx.globalAlpha = 1;
    }
    // pontos (com neblina pela profundidade)
    const pts = this.pontos.map((p) => ({ p, ...P(p.X, p.Y, p.Z) })).filter((q) => isFinite(q.x)).sort((a, b) => b.z - a.z);
    for (const d of pts) {
      ctx.globalAlpha = d.p.destaque ? 1 : Math.max(0.2, Math.min(1, 0.78 - d.z * 0.3));
      ctx.fillStyle = d.p.cor; ctx.beginPath(); ctx.arc(d.x, d.y, (d.p.r || 3) * d.s, 0, 7); ctx.fill();
      if (d.p.destaque) { ctx.strokeStyle = C.panel; ctx.lineWidth = 2; ctx.stroke(); ctx.fillStyle = C.ink; ctx.font = '600 11px "IBM Plex Sans", sans-serif'; ctx.textAlign = 'left'; ctx.fillText(d.p.rotulo || '', d.x + 9, d.y - 7); }
      alvos.push({ x: d.x, y: d.y, z: d.z, html: () => d.p.html, ponto: d });
    }
    ctx.globalAlpha = 1;
    for (const l of this.linhas) { ctx.strokeStyle = l.cor; ctx.lineWidth = l.w || 2; ctx.setLineDash(l.tracos || []); ctx.beginPath(); l.pts.forEach((q, k) => { const r = P(q[0], q[1], q[2]); k ? ctx.lineTo(r.x, r.y) : ctx.moveTo(r.x, r.y); }); ctx.stroke(); ctx.setLineDash([]); }
    this.alvos = alvos;
    // cursor
    this.hover = null;
    if (this.mouse && !this.arrastando) {
      let m = null, dm = 22 * 22;
      for (let k = alvos.length - 1; k >= 0; k--) { const a = alvos[k], dd = (a.x - this.mouse.x) ** 2 + (a.y - this.mouse.y) ** 2; if (dd < dm) { dm = dd; m = a; } }
      if (m) {
        this.hover = m; ctx.strokeStyle = C.ink; ctx.lineWidth = 2;
        if (m.quad) { const v = m.quad.v; ctx.beginPath(); ctx.moveTo(v[0].x, v[0].y); for (let k = 1; k < 4; k++) ctx.lineTo(v[k].x, v[k].y); ctx.closePath(); ctx.stroke(); }
        else { ctx.beginPath(); ctx.arc(m.x, m.y, 8, 0, 7); ctx.stroke(); const p = m.ponto.p, ch = P(p.X, -1, p.Z); ctx.strokeStyle = C.accent; ctx.setLineDash([3, 3]); ctx.beginPath(); ctx.moveTo(m.x, m.y); ctx.lineTo(ch.x, ch.y); ctx.stroke(); ctx.setLineDash([]); }
        const tip = this.tip; tip.innerHTML = m.html(); tip.hidden = false;
        let lx = this.mouse.x + 14, ly = this.mouse.y + 14; if (lx + tip.offsetWidth > W - 4) lx = this.mouse.x - tip.offsetWidth - 14; if (ly + tip.offsetHeight > H) ly = this.mouse.y - tip.offsetHeight - 14;
        tip.style.left = `${Math.max(4, lx)}px`; tip.style.top = `${Math.max(4, ly)}px`;
        if (this.aoLer) this.aoLer(m.html());
      } else this.tip.hidden = true;
    }
    ctx.fillStyle = C.muted; ctx.font = '10.5px "IBM Plex Sans", sans-serif'; ctx.textAlign = 'left';
    ctx.textAlign = 'right'; ctx.fillText('Arraste para girar · role para aproximar · W A S D para andar', W - 10, H - 8);
  }
}
const ticks3d = (lo, hi, fmt, n = 3) => Array.from({ length: n }, (_, i) => { const v = -1 + 2 * i / (n - 1); return { v, txt: fmt(lo + (hi - lo) * i / (n - 1)) }; });
const norm3d = (lo, hi) => (v) => Math.max(-1.1, Math.min(1.1, ((v - lo) / (hi - lo || 1)) * 2 - 1));

// ---------------------------------------------------------------- Histórico: superfície de retornos 3D
// X = mês/ano de início, Z = prazo em anos, altura = retorno ao ano que a carteira de fato entregou.
let SR = null;
function desenharSuperficieRetornos(H, lst) {
  if (!SR) {
    SR = { escala: { x: 100, y: 100, z: 100 } };
    SR.cena = new Cena3D($('sr-canvas'), $('sr-tip'), { escala: SR.escala, rot: { yaw: -0.55, pitch: 0.5 } });
    SR.cena.aoLer = (h) => { $('sr-read').innerHTML = h.replace(/<br>/g, ' · '); };
    controleEixos($('sr-eixos'), SR.escala, [['x', 'ano de início'], ['y', 'retorno ao ano'], ['z', 'prazo (anos)']], () => SR.cena.pedir());
    $('sr-girar').addEventListener('click', (e) => { const on = !SR.cena.girando; e.target.classList.toggle('primary', on); SR.cena.girar(on); });
    $('seg-sr').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; S.sr = +b.dataset.v; salvar(); desenharHistorico(); });
  }
  const carts = lst.filter((s) => s.cart);
  if (!(S.sr < carts.length)) S.sr = 0;
  $('seg-sr').innerHTML = carts.map((c, i) => `<button type="button" data-v="${i}" class="${i === S.sr ? 'on' : ''}"><span style="display:inline-block;width:8px;height:8px;border-radius:2px;margin-right:6px;background:${c.cor}"></span>${esc(c.nome.slice(0, 18))}</button>`).join('');
  const r = carts[S.sr].r, L = r.length, real = S.real;
  const rr = r.map((x, j) => (real ? (1 + x) / (1 + H.ipca[j]) - 1 : x));
  const passo = L >= 60 ? 12 : L >= 12 ? 3 : 1, nIni = Math.floor((L - passo) / passo) + 1, nPrazo = Math.floor(L / passo);
  if (nIni < 2 || nPrazo < 2) { SR.cena.sup = null; SR.cena.ticks = { x: [], y: [], z: [] }; $('sr-meta').textContent = 'histórico curto demais para a superfície'; SR.cena.pedir(); return; }
  const acum = [1]; for (const x of rr) acum.push(acum[acum.length - 1] * (1 + x));
  const V = [], todos = [];
  for (let i = 0; i < nIni; i++) { const lin = []; for (let j = 0; j < nPrazo; j++) { const a = i * passo, b = a + (j + 1) * passo; const v = b <= L ? Math.pow(acum[b] / acum[a], 12 / (b - a)) - 1 : NaN; lin.push(v); if (isFinite(v)) todos.push(v); } V.push(lin); }
  todos.sort((a, b) => a - b);
  const lo = quantil(todos, 0.02), hi = quantil(todos, 0.98), ny = norm3d(lo, hi);
  const X = Array.from({ length: nIni }, (_, i) => -1 + 2 * i / Math.max(1, nIni - 1)), Z = Array.from({ length: nPrazo }, (_, j) => -1 + 2 * j / Math.max(1, nPrazo - 1));
  const un = passo === 12 ? 'anos' : 'meses', prazoTxt = (j) => (passo === 12 ? `${j + 1} ${j ? 'anos' : 'ano'}` : `${(j + 1) * passo} meses`);
  const mesIni = (i) => rotulo(H.meses[i * passo]);
  const cor = (i, j) => { const v = V[i][j]; const t = Math.max(-1, Math.min(1, v / Math.max(Math.abs(lo), Math.abs(hi), 1e-9))); return t >= 0 ? `hsl(205 ${40 + 35 * t}% ${62 - 22 * t}%)` : `hsl(4 ${40 - 30 * t}% ${62 + 18 * t}%)`; };
  SR.cena.sup = { nx: nIni, nz: nPrazo, X, Z, Y: V.map((l) => l.map((v) => (isFinite(v) ? ny(v) : NaN))), cor,
    html: (i, j) => `<b>Início ${mesIni(i)} · ${prazoTxt(j)}</b><br><span class="k">retorno ${real ? 'real ' : ''}ao ano</span> <b>${pct(V[i][j])}</b><br><span class="k">R$ 10 mil →</span> ${brl(10000 * Math.pow(1 + V[i][j], ((j + 1) * passo) / 12))}` };
  SR.cena.rotulos = { x: 'ano de início →', y: `retorno ${real ? 'real ' : ''}a.a.`, z: `prazo (${un}) →` };
  SR.cena.ticks = { x: [0, Math.floor((nIni - 1) / 2), nIni - 1].map((i) => ({ v: X[i], txt: mesIni(i) })), y: ticks3d(lo, hi, (v) => pct(v, 0)), z: [0, Math.floor((nPrazo - 1) / 2), nPrazo - 1].map((j) => ({ v: Z[j], txt: prazoTxt(j) })) };
  const neg = todos.filter((v) => v < 0).length / todos.length;
  $('sr-meta').textContent = `${esc(carts[S.sr].nome)} · ${todos.length} combinações de início e prazo`;
  $('sr-leg').innerHTML = `<span><i style="background:hsl(205 75% 40%)"></i>retorno positivo</span><span><i style="background:hsl(4 70% 70%)"></i>retorno negativo</span><span>${pct(neg, 0)} das combinações perderam ${real ? 'para a inflação' : 'dinheiro'}</span>`;
  SR.cena.pedir();
}

// ---------------------------------------------------------------- Otimizar: fronteira em 3D (volatilidade × retorno × queda)
let O3 = null;
function desenharFronteira3d(o) {
  if (!o || !o.ddIso) return;
  if (!O3) {
    O3 = { escala: { x: 100, y: 100, z: 100 } };
    O3.cena = new Cena3D($('o3-canvas'), $('o3-tip'), { escala: O3.escala, rot: { yaw: -0.6, pitch: 0.4 } });
    O3.cena.aoLer = (h) => { $('o3-read').innerHTML = h.replace(/<br>/g, ' · '); };
    controleEixos($('o3-eixos'), O3.escala, [['x', 'volatilidade'], ['y', 'retorno'], ['z', 'queda máxima']], () => O3.cena.pedir());
    $('o3-girar').addEventListener('click', (e) => { const on = !O3.cena.girando; e.target.classList.toggle('primary', on); O3.cena.girar(on); });
  }
  const cores = exPaleta();
  const pts = o.isolados.map((p, k) => ({ t: p.t, vol: p.vol, ret: p.ret, dd: -o.ddIso[k], w: o.w[k] }));
  const otm = { t: 'Otimizada', vol: o.est.vol, ret: o.est.ret, dd: -o.mh.maxdd };
  const q = (k, a, b) => { const v = [...pts.map((p) => p[k]), otm[k]].filter(isFinite).sort((x, y) => x - y); return [quantil(v, a), quantil(v, b)]; };
  const [vx0, vx1] = q('vol', 0.02, 0.97), [vy0, vy1] = q('ret', 0.02, 0.98), [vz0, vz1] = q('dd', 0.02, 0.97);
  const nx = norm3d(0, vx1 * 1.05), ny = norm3d(Math.min(0, vy0), vy1 * 1.05), nz = norm3d(0, vz1 * 1.05);
  O3.cena.pontos = pts.filter((p) => isFinite(p.vol) && isFinite(p.ret) && isFinite(p.dd)).map((p) => {
    const a = info(p.t), g = a ? (TIPOS[a.tipo] || ['', 'indice'])[1] : 'rf';
    return { X: nx(p.vol), Y: ny(p.ret), Z: nz(p.dd), cor: cores[g] || cores.indice, r: p.w > 0 ? 5 : 3, destaque: p.w > 0.08, rotulo: rotuloAtivo(p.t).slice(0, 9),
      html: `<b>${esc(rotuloAtivo(p.t))}</b>${a ? ` <span class="k">${esc(TIPOS[a.tipo][0])}</span>` : ''}<br><span class="k">retorno</span> ${pct(p.ret)} · <span class="k">volat.</span> ${pct(p.vol)} · <span class="k">queda</span> ${pct(-p.dd, 0)}${p.w > 0 ? `<br><span class="k">peso na otimizada</span> <b>${pct(p.w, 1)}</b>` : ''}` };
  });
  O3.cena.pontos.push({ X: nx(otm.vol), Y: ny(otm.ret), Z: nz(otm.dd), cor: exCor('--amber'), r: 8, destaque: true, rotulo: 'Otimizada',
    html: `<b>Carteira otimizada</b><br><span class="k">retorno</span> ${pct(otm.ret)} · <span class="k">volat.</span> ${pct(otm.vol)} · <span class="k">queda</span> ${pct(-otm.dd, 0)}` });
  O3.cena.rotulos = { x: 'volatilidade →', y: 'retorno a.a.', z: 'queda máxima →' };
  O3.cena.ticks = { x: ticks3d(0, vx1 * 1.05, (v) => pct(v, 0)), y: ticks3d(Math.min(0, vy0), vy1 * 1.05, (v) => pct(v, 0)), z: ticks3d(0, vz1 * 1.05, (v) => pct(v, 0)) };
  const cont = {}; for (const p of pts) { const a = info(p.t); const g = a ? TIPOS[a.tipo][1] : 'rf'; cont[g] = (cont[g] || 0) + 1; }
  $('o3-leg').innerHTML = CLASSES.filter(([k]) => cont[k]).map(([k, n]) => `<span><i style="background:${cores[k]}"></i>${n} (${cont[k]})</span>`).join('') + '<span><i style="background:var(--amber)"></i>Otimizada</span><span>pontos maiores = ativos na carteira</span>';
  O3.cena.pedir();
}

// ---------------------------------------------------------------- Otimizar: mapa 3D das combinações de classes
// Cada combinação das classes marcadas (1, 2, 3... classes) é otimizada com o objetivo e os limites
// escolhidos; o resultado vira um ponto: retorno, volatilidade, Sharpe e queda máxima de uma vez.
function otimizarCombos(d) {
  const out = [];
  for (const ix of d.combos) {
    const sub = ix.map((k) => d.R[k]), cl = ix.map((k) => d.classe[k]), g = ix.map((k) => d.grupos[k]);
    let w;
    if (ix.length === 1) w = [1];
    else w = otimizarCompleto(sub, { ...d.op, grupos: g, cadaClasse: true, reamostra: false, classe: cl }, d.rfm);
    const e = estimarRobusto(sub, cl), est = estatPesos(w, e.mu, e.C, d.rfm), q = quedaPesos(w, sub);
    const pesos = ix.map((k, j) => [k, w[j]]).filter((x) => x[1] > 0.005).sort((a, b) => b[1] - a[1]).slice(0, 6);
    out.push({ ret: est.ret, vol: est.vol, sharpe: est.sharpe, dd: q.dd, cagr: q.cagr, pesos });
    if (out.length % 5 === 0) postMessage({ p: 5 });
  }
  return out;
}
const CB_MET = {
  vol: ['Volatilidade ao ano', (v) => pct(v)], ret: ['Retorno ao ano (estimado)', (v) => pct(v)], sharpe: ['Sharpe (retorno por risco)', (v) => num(v)],
  dd: ['Queda máxima (histórica)', (v) => pct(-v, 0)], cagr: ['Retorno ao ano (histórico)', (v) => pct(v)], n: ['Nº de classes', (v) => num(v, 0)],
};
const CB_CURTO = { ret: 'retorno estimado', cagr: 'retorno histórico', vol: 'volat.', sharpe: 'Sharpe', dd: 'queda máx.', n: 'classes' };
const CB = { escala: { x: 100, y: 100, z: 100 }, cena: null, res: null, eixos: { x: 'vol', y: 'ret', z: 'dd', cor: 'sharpe' }, k: 3, porClasse: 8 };
let cbCancelar = null;
function prepararCombos() {
  if (CB.cena) return;
  CB.cena = new Cena3D($('cb-canvas'), $('cb-tip'), { escala: CB.escala, rot: { yaw: -0.6, pitch: 0.42 } });
  CB.cena.aoLer = (h) => { $('cb-read').innerHTML = h.replace(/<br>/g, ' · '); };
  CB.cena.aoClicar = (alvo) => { if (alvo.ponto && alvo.ponto.p.combo) usarCombo(alvo.ponto.p.combo); };
  controleEixos($('cb-eixos'), CB.escala, [['x', 'eixo horizontal'], ['y', 'eixo vertical'], ['z', 'profundidade']], () => CB.cena.pedir());
  $('cb-girar').addEventListener('click', (e) => { const on = !CB.cena.girando; e.target.classList.toggle('primary', on); CB.cena.girar(on); });
  const opts = Object.entries(CB_MET).map(([k, [n]]) => `<option value="${k}">${n}</option>`).join('');
  for (const e of ['x', 'y', 'z', 'cor']) { const el = $(`cb-${e}`); el.innerHTML = opts; el.value = CB.eixos[e]; el.addEventListener('change', () => { CB.eixos[e] = el.value; desenharCombos(); }); }
  ligarLivre('nj-cb-k', { obter: () => CB.k, definir: (v) => { CB.k = v; }, min: 1, max: 30, cheio: () => 30, aoMudar: contarCombos });
  $('cb-n').value = CB.porClasse; $('cb-n').addEventListener('input', () => { const v = Math.round(lerDec($('cb-n').value)); if (v >= 1) { CB.porClasse = Math.min(v, 60); contarCombos(); } });
  $('btn-cb').addEventListener('click', rodarCombos);
  $('cb-tab').addEventListener('click', (e) => { const b = e.target.closest('[data-i]'); if (b && CB.res) usarCombo(CB.res.pontos[+b.dataset.i].combo); });
}
const cbPool = () => { const base = OT_CLASSES.map((x) => x[0]).filter((k) => !k.startsWith('fii:')); const sel = S.ot.classes.filter((k) => OT_CLASSES.some((x) => x[0] === k)); return sel.length >= 2 ? sel : base; };
const binom = (n, k) => { let r = 1; for (let i = 1; i <= k; i++) r = r * (n - k + i) / i; return Math.round(r); };
function contarCombos() {
  const pool = cbPool(), k = Math.min(CB.k, pool.length); let n = 0; for (let i = 1; i <= k; i++) n += binom(pool.length, i);
  $('cb-help').textContent = `${S.ot.classes.filter((c) => OT_CLASSES.some((x) => x[0] === c)).length >= 2 ? 'Classes marcadas no Universo' : 'Nenhuma ou só 1 classe marcada: usando todas'} (${pool.length}): ${pool.map((c) => OT_CLASSES.find((x) => x[0] === c)[1]).join(', ')}. ` +
    `${n.toLocaleString('pt-BR')} combinações de até ${k} ${k === 1 ? 'classe' : 'classes'}, cada uma otimizada com o objetivo, os limites e a janela escolhidos acima (${CB.porClasse} maiores ativos por classe).`;
  return n;
}
function* combinacoes(n, k) { const a = []; function* rec(i) { if (a.length) yield a.slice(); if (a.length === k) return; for (let j = i; j < n; j++) { a.push(j); yield* rec(j + 1); a.pop(); } } yield* rec(0); }
function rodarCombos() {
  if (cbCancelar) { cbCancelar(); cbCancelar = null; return; }
  const pool = cbPool(), salvo = S.ot;
  S.ot = { ...salvo, fonte: 'filtro', classes: pool, maxUniv: CB.porClasse * pool.length, cadaClasse: true };
  let U; try { U = universoOtimizador(); } finally { S.ot = salvo; }
  if (U.erro) { $('cb-read').textContent = U.erro; return; }
  const H = preparar([{ nome: 'U', itens: U.codigos.map((t) => ({ t, p: 1 })) }], Math.floor(U.ini / 12), S.cambio, 'redistribuir');
  if (H.erro) { $('cb-read').textContent = H.erro; return; }
  const i0 = Math.max(0, H.meses.findIndex((mi) => mi >= U.ini)), base = H.Rc ? H.Rc[0] : H.R;
  const R = base.map((r) => Float64Array.from(r.slice(i0), (x) => (isFinite(x) ? x : 0))), cdi = H.cdi.slice(i0), uni = H.uni, rfm = media(cdi);
  const gU = uni.map((t) => U.grupos[U.codigos.indexOf(t)] ?? -1);
  const classe = uni.map((t) => (t.startsWith('RF:') ? 'rf' : (TIPOS[info(t).tipo] || ['', info(t).tipo])[1]));
  // grupos do universo → índices das classes do pool que têm ativos
  const presentes = [...new Set(gU.filter((g) => g >= 0))].sort((a, b) => a - b);
  const k = Math.min(CB.k, presentes.length), combos = [];
  for (const c of combinacoes(presentes.length, k)) { const gs = c.map((i) => presentes[i]); const ix = uni.map((_, j) => j).filter((j) => gs.includes(gU[j])); if (ix.length) combos.push({ gs, ix }); }
  const nomes = combos.map((c) => c.gs.map((g) => OT_CLASSES.find((x) => x[0] === pool[g])[1]));
  const op = { ...S.ot, maxAtivos: S.ot.maxAtivos ? Math.max(S.ot.maxAtivos, k) : 0 };
  const total = combos.length, t0 = performance.now(); let feitos = 0;
  $('btn-cb-label').textContent = `Calculando 0 de ${total.toLocaleString('pt-BR')}… (toque para parar)`;
  const partes = Math.max(1, Math.min(NUCLEOS, Math.ceil(total / 10))), por = Math.ceil(total / partes), ws = [];
  let cancelado = false;
  cbCancelar = () => { cancelado = true; ws.forEach((w) => w && w.terminate()); $('btn-cb-label').textContent = 'Gerar mapa'; };
  const tarefas = [];
  for (let a = 0; a < total; a += por) {
    const msg = { tipo: 'combos', R, combos: combos.slice(a, a + por).map((c) => c.ix), op, rfm, classe, grupos: gU };
    tarefas.push(new Promise((ok, falha) => {
      const w = semWorker ? null : criarWorkerOt(); ws.push(w);
      if (!w) { setTimeout(() => { try { ok(otimizarCombosLocal(msg)); } catch (e) { falha(e); } }, 20); return; }
      w.onmessage = (e) => { if (e.data.p) { feitos += e.data.p; $('btn-cb-label').textContent = `Calculando ${Math.min(feitos, total).toLocaleString('pt-BR')} de ${total.toLocaleString('pt-BR')}… (toque para parar)`; return; } w.terminate(); ok(e.data.res); };
      w.onerror = (e) => { e.preventDefault(); w.terminate(); falha(new Error(e.message || 'erro no núcleo de cálculo')); };
      w.postMessage(msg);
    }));
  }
  Promise.all(tarefas).then((rs) => {
    if (cancelado) return;
    const lista = rs.flat();
    CB.res = { pontos: lista.map((r, i) => ({ ...r, n: combos[i].gs.length, nomes: nomes[i], combo: combos[i].gs.map((g) => pool[g]), pesos: r.pesos.map(([k2, w]) => [uni[k2], w]) })),
      janela: `${rotulo(H.meses[i0])}–${rotulo(H.fim)}`, ms: performance.now() - t0 };
    desenharCombos();
  }).catch((e) => { if (!cancelado) $('cb-read').textContent = `Não foi possível calcular: ${e.message}`; })
    .finally(() => { cbCancelar = null; $('btn-cb-label').textContent = CB.res ? 'Gerar de novo' : 'Gerar mapa'; });
}
function otimizarCombosLocal(msg) { const pm = window.postMessage; window.postMessage = () => {}; try { return otimizarCombos(msg); } finally { window.postMessage = pm; } }
function desenharCombos() {
  if (!CB.res) return;
  const P = CB.res.pontos.filter((p) => ['x', 'y', 'z', 'cor'].every((e) => isFinite(p[CB.eixos[e]])));
  if (!P.length) return;
  const fx = (k) => { const v = P.map((p) => p[k]).sort((a, b) => a - b); let lo = quantil(v, 0.01), hi = quantil(v, 0.99); if (hi - lo < 1e-9) { lo -= 0.5; hi += 0.5; } return [lo, hi]; };
  const E = CB.eixos, fX = fx(E.x), fY = fx(E.y), fZ = fx(E.z), fC = fx(E.cor), nX = norm3d(...fX), nY = norm3d(...fY), nZ = norm3d(...fZ);
  const melhor = P.reduce((a, p) => (p.sharpe > a.sharpe ? p : a), P[0]);
  const cor = (v) => { const t = Math.max(0, Math.min(1, (v - fC[0]) / (fC[1] - fC[0] || 1))); return `hsl(${215 - t * 175} 72% ${46 + t * 8}%)`; };
  const html = (p) => `<b>${esc(p.nomes.join(' + '))}</b><br>${Object.entries(CB_CURTO).map(([k, n]) => `<span class="k">${n}</span> ${CB_MET[k][1](p[k])}`).join(' · ')}<br><span class="k">pesos:</span> ${p.pesos.map(([t, w]) => `${esc(rotuloAtivo(t).slice(0, 10))} ${pct(w, 0)}`).join(' · ')}<br><span class="k">toque/clique para otimizar esta combinação</span>`;
  CB.cena.pontos = P.map((p) => ({ X: nX(p[E.x]), Y: nY(p[E.y]), Z: nZ(p[E.z]), cor: cor(p[E.cor]), r: 2.4 + p.n * 1.1, destaque: p === melhor, rotulo: p === melhor ? `Maior Sharpe: ${p.nomes.join(' + ').slice(0, 40)}` : '', html: html(p), combo: p.combo }));
  CB.cena.rotulos = { x: `${CB_MET[E.x][0].replace(/ \(.*\)/, '')} →`, y: CB_MET[E.y][0].replace(/ \(.*\)/, ''), z: `${CB_MET[E.z][0].replace(/ \(.*\)/, '')} →` };
  CB.cena.ticks = { x: ticks3d(...fX, CB_MET[E.x][1]), y: ticks3d(...fY, CB_MET[E.y][1]), z: ticks3d(...fZ, CB_MET[E.z][1]) };
  CB.cena.pedir();
  $('cb-meta').textContent = `${P.length.toLocaleString('pt-BR')} combinações · ${CB.res.janela} · ${num(CB.res.ms / 1000, 1)} s`;
  $('cb-leg').innerHTML = `<span><i style="background:hsl(215 72% 46%)"></i>${CB_MET[E.cor][0]} baixo</span><span><i style="background:hsl(40 72% 54%)"></i>alto</span><span>ponto maior = mais classes na combinação</span><span>retorno e volatilidade estimados; queda máxima e retorno histórico na janela</span>`;
  $('cb-read').innerHTML = `Maior Sharpe: <b>${esc(melhor.nomes.join(' + '))}</b> · ${Object.entries(CB_MET).slice(0, 4).map(([k, [n, f]]) => `${n.replace(/ \(.*\)/, '')} ${f(melhor[k])}`).join(' · ')}. Passe o cursor nos pontos; toque ou clique para otimizar a combinação.`;
  const top = CB.res.pontos.map((p, i) => ({ p, i })).filter((x) => isFinite(x.p.sharpe)).sort((a, b) => b.p.sharpe - a.p.sharpe).slice(0, 12);
  $('cb-tab').innerHTML = `<thead><tr><th>Combinação (maiores Sharpe)</th><th>Retorno</th><th>Volat.</th><th>Sharpe</th><th>Queda</th><th></th></tr></thead><tbody>` +
    top.map(({ p, i }) => `<tr><td class="t" style="white-space:normal">${esc(p.nomes.join(' + '))}</td><td>${pct(p.ret)}</td><td>${pct(p.vol, 0)}</td><td><b>${num(p.sharpe)}</b></td><td>${pct(-p.dd, 0)}</td><td><button type="button" class="btn" data-i="${i}">Usar</button></td></tr>`).join('') + '</tbody>';
}
function usarCombo(combo) {
  S.ot.fonte = 'filtro'; S.ot.classes = combo.slice(); salvar(); otUltimo = null; prepararOtimizador();
  $('ot-res').scrollIntoView({ behavior: 'smooth', block: 'start' });
  rodarOtimizador();
}

// ---------------------------------------------------------------- montanha 3D de probabilidades (simulação)
// Superfície feita das trajetórias simuladas guardadas: para cada ano, que fração dos cenários termina em
// cada faixa de patrimônio. Arraste para girar; passe o cursor para ler a faixa.
const MT = { escala: { x: 100, y: 100, z: 100 }, rot: { yaw: -0.42, pitch: 0.62, zoom: 1 }, girar: false, malha: null, pronto: false, quadro: 0, hover: null, arr: false };
function proj3d(W, H, rot, esc0 = 0.3, k = { x: 100, y: 100, z: 100 }, A = { x: 0, y: 0, z: 0 }) {
  const cy = Math.cos(rot.yaw), sy = Math.sin(rot.yaw), cp = Math.cos(rot.pitch), sp = Math.sin(rot.pitch), f = Math.min(W, H * 1.25) * esc0 * rot.zoom, d = 3.6;
  return (X, Y, Z) => { X = (X - A.x) * k.x / 100; Y = (Y - A.y) * k.y / 100; Z = (Z - A.z) * k.z / 100; const x1 = X * cy - Z * sy, z1 = X * sy + Z * cy, y2 = Y * cp - z1 * sp, z2 = Y * sp + z1 * cp;
    if (d + z2 < 0.25) return { x: NaN, y: NaN, z: z2 };
    const s = f * d / (d + z2); return { x: W / 2 + x1 * s, y: H / 2 + 10 - y2 * s, z: z2 }; };
}
function montarMontanha() {
  if (!ultimo) return null;
  const { R } = ultimo, r = R.res[S.ver], Pn = R.Pn, nCam = R.nCam, cam = r.cam;
  if (!nCam || Pn < 3) return null;
  // fatias em anos inteiros (até 30), do ano 1 ao último
  let ks = []; R.pontos.forEach((m, k) => { if (k > 0 && m % 12 === 0) ks.push(k); });
  if (ks.length < 3) ks = Array.from({ length: Math.min(28, Pn - 1) }, (_, i) => 1 + Math.round(i * (Pn - 2) / Math.max(1, Math.min(28, Pn - 1) - 1)));
  if (ks.length > 30) { const passo = ks.length / 30; ks = Array.from({ length: 30 }, (_, i) => ks[Math.min(ks.length - 1, Math.round(i * passo))]); ks[29] = R.pontos.length - 1; ks = [...new Set(ks)]; }
  const T = ks.length;
  const amostra = [];
  for (const k of [ks[0], ks[Math.floor(T / 2)], ks[T - 1]]) for (let s = 0; s < nCam; s += Math.max(1, Math.floor(nCam / 3000))) { const v = cam[s * Pn + k]; if (v > 0) amostra.push(Math.log(v)); }
  amostra.sort((a, b) => a - b);
  if (amostra.length < 10) return null;
  const lo = quantil(amostra, 0.01), hi = quantil(amostra, 0.99), B = 34, lg = (hi - lo) / B || 1;
  const dens = ks.map((k) => {
    const c = new Float64Array(B); let n = 0;
    for (let s = 0; s < nCam; s++) { const v = cam[s * Pn + k]; n++; if (!(v > 0)) { c[0]++; continue; } const b = Math.floor((Math.log(v) - lo) / lg); c[Math.max(0, Math.min(B - 1, b))]++; }
    const fr = Array.from(c, (x) => x / n), acum = []; let a = 0; for (const f of fr) { a += f; acum.push(a); }
    // suavização leve só no desenho (a dica mostra a fração real de cada faixa)
    const sm = fr.map((_, b) => { let s = 0, w = 0; for (let j = -2; j <= 2; j++) { const q = fr[b + j]; if (q === undefined) continue; const pe = [0.1, 0.2, 0.4, 0.2, 0.1][j + 2]; s += q * pe; w += pe; } return s / w; });
    return { k, fr, sm, acum };
  });
  // altura: concentração em relação ao maior pico de todos os anos (raiz, para os primeiros anos não esconderem os últimos)
  const gmax = Math.max(...dens.map((d) => Math.max(...d.sm))) || 1;
  for (const d of dens) d.alt = d.sm.map((x) => Math.sqrt(x / gmax));
  return { ks, dens, lo, hi, B, lg, nCam, mes: ks.map((k) => R.pontos[k]) };
}
function desenharMontanha() {
  const cv = $('mt-canvas'); if (!cv || !ultimo) return;
  if (!MT.pronto) {
    MT.pronto = true;
    const pos = (e) => { const r = cv.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; };
    let ini = null;
    cv.addEventListener('pointerdown', (e) => { e.preventDefault(); cv.setPointerCapture(e.pointerId); ini = { ...pos(e), rot: { ...MT.rot }, mov: 0 }; });
    cv.addEventListener('pointermove', (e) => {
      const q = pos(e);
      if (ini) { const dx = q.x - ini.x, dy = q.y - ini.y; ini.mov = Math.max(ini.mov, Math.abs(dx) + Math.abs(dy));
        if (ini.mov > 4) { MT.arr = true; MT.rot.yaw = ini.rot.yaw + dx * 0.009; MT.rot.pitch = Math.max(0.05, Math.min(1.45, ini.rot.pitch + dy * 0.009)); $('mt-tip').hidden = true; MT.hover = null; pedirMontanha(); return; } }
      MT.mouse = q; pedirMontanha();
    });
    const fim = () => { ini = null; MT.arr = false; pedirMontanha(); };
    cv.addEventListener('pointerup', fim); cv.addEventListener('pointercancel', fim);
    cv.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') { MT.mouse = null; MT.hover = null; $('mt-tip').hidden = true; pedirMontanha(); } });
    cv.addEventListener('wheel', (e) => { e.preventDefault(); MT.rot.zoom = Math.max(0.5, Math.min(4, MT.rot.zoom * (e.deltaY > 0 ? 0.9 : 1.1))); pedirMontanha(); }, { passive: false });
    $('mt-girar').addEventListener('click', () => { MT.girar = !MT.girar; $('mt-girar').classList.toggle('primary', MT.girar); if (MT.girar) girarMontanha(); });
    new ResizeObserver(() => pedirMontanha()).observe(cv);
    ligarNav3d(cv, MT, pedirMontanha, { reiniciar: () => { MT.rot = { yaw: -0.42, pitch: 0.62, zoom: 1 }; } });
    controleEixos($('mt-eixos'), MT.escala, [['x', 'tempo (anos)'], ['y', 'concentração'], ['z', 'patrimônio']], pedirMontanha);
  }
  MT.malha = montarMontanha();
  const r = ultimo.R.res[S.ver];
  $('mt-meta').textContent = MT.malha ? `${MT.malha.nCam.toLocaleString('pt-BR')} trajetórias · ${esc(r.nome)}` : '';
  $('mt-leg').innerHTML = `<span><i style="background:${CORES_CART[S.ver]}"></i>Concentração dos cenários (mais alto = mais cenários naquela faixa de patrimônio)</span><span><i class="ln" style="border-color:var(--amber)"></i>Meta</span><span><i class="ln" style="border-color:var(--muted);border-top-style:dotted"></i>Total aportado</span>`;
  pedirMontanha();
}
function pedirMontanha() { if (MT.quadro) return; MT.quadro = requestAnimationFrame(() => { MT.quadro = 0; pintarMontanha(); }); }
function girarMontanha() { if (!MT.girar || vista !== 'simulacao') return; if (!MT.arr) { MT.rot.yaw += 0.005; pintarMontanha(); } requestAnimationFrame(girarMontanha); }
function pintarMontanha() {
  const cv = $('mt-canvas'), M = MT.malha; if (!cv || vista !== 'simulacao') return;
  const W = cv.clientWidth, H = cv.clientHeight, dpr = window.devicePixelRatio || 1; if (!W || !H) return;
  if (cv.width !== Math.round(W * dpr) || cv.height !== Math.round(H * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
  const ctx = cv.getContext('2d'); ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, W, H);
  const C = { ink: exCor('--ink'), muted: exCor('--muted'), rule: exCor('--rule'), rule2: exCor('--rule-2'), amber: exCor('--amber'), panel: exCor('--panel') };
  if (!M) { ctx.fillStyle = C.muted; ctx.font = '12px "IBM Plex Sans", sans-serif'; ctx.fillText('Rode a simulação para ver a montanha.', 16, 28); return; }
  const P = proj3d(W, H, MT.rot, 0.3, MT.escala, MT.alvo), T = M.ks.length, B = M.B;
  const X = (t) => -1 + 2 * t / Math.max(1, T - 1), Z = (b) => -1 + 2 * b / (B - 1), Y = (h) => -1 + 1.7 * h;
  const zVal = (v) => (v > 0 ? -1 + 2 * ((Math.log(v) - M.lo) / (M.hi - M.lo) * B - 0.5) / (B - 1) : -1);
  // chão: grade, eixos e linhas de meta e aportado
  ctx.strokeStyle = C.rule; ctx.lineWidth = 1;
  for (let i = 0; i <= 4; i++) { const a = P(-1 + i / 2, -1, -1), b = P(-1 + i / 2, -1, 1), c = P(-1, -1, -1 + i / 2), d = P(1, -1, -1 + i / 2); ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.moveTo(c.x, c.y); ctx.lineTo(d.x, d.y); ctx.stroke(); }
  const linhaChao = (vals, cor, tracos) => { ctx.strokeStyle = cor; ctx.lineWidth = 1.6; ctx.setLineDash(tracos); ctx.beginPath(); vals.forEach((v, t) => { const z = Math.max(-1, Math.min(1, zVal(v))), q = P(X(t), -1, z); t ? ctx.lineTo(q.x, q.y) : ctx.moveTo(q.x, q.y); }); ctx.stroke(); ctx.setLineDash([]); };
  const o = ultimo.o, R = ultimo.R;
  if (o.meta > 0 && Math.log(o.meta) > M.lo && Math.log(o.meta) < M.hi) linhaChao(M.ks.map(() => o.meta), C.amber, [6, 4]);
  linhaChao(M.ks.map((k) => R.inv[k]), C.muted, [2, 4]);
  ctx.fillStyle = C.muted; ctx.font = '10px "IBM Plex Mono", monospace'; ctx.textAlign = 'center';
  const anosTot = M.mes[T - 1] / 12, pa = anosTot <= 10 ? 2 : anosTot <= 25 ? 5 : 10;
  M.mes.forEach((m, t) => { if (m % (12 * pa) === 0 || t === T - 1) { const q = P(X(t), -1, 1.18); ctx.fillText(`ano ${Math.round(m / 12)}`, q.x, q.y + 4); } });
  for (let i = 0; i <= 4; i++) { const v = Math.exp(M.lo + (M.hi - M.lo) * i / 4), q = P(-1.28, -1, -1 + i / 2); ctx.fillText(eixoBRL(v), q.x, q.y + 4); }
  ctx.font = '600 11px "IBM Plex Sans", sans-serif'; ctx.fillStyle = C.ink;
  { const a = P(0, -1, 1.42), b = P(-1.55, -1, 0); ctx.fillText('tempo →', a.x, a.y + 6); ctx.fillText('patrimônio →', b.x, b.y - 8); }
  ctx.font = '10px "IBM Plex Mono", monospace'; ctx.fillStyle = C.muted;
  // superfície: quadriláteros do fundo para a frente, cor pela altura e pela inclinação (luz)
  const base = exCor(CORES_CART[S.ver].replace(/^var\((.*)\)$/, '$1')) || C.ink;
  const quads = [];
  for (let t = 0; t < T - 1; t++) for (let b = 0; b < B - 1; b++) {
    const h = [M.dens[t].alt[b], M.dens[t + 1].alt[b], M.dens[t + 1].alt[b + 1], M.dens[t].alt[b + 1]];
    const v = [P(X(t), Y(h[0]), Z(b)), P(X(t + 1), Y(h[1]), Z(b)), P(X(t + 1), Y(h[2]), Z(b + 1)), P(X(t), Y(h[3]), Z(b + 1))];
    if (v.some((q) => !isFinite(q.x))) continue;
    const hm = (h[0] + h[1] + h[2] + h[3]) / 4, luz = 0.55 + 0.45 * Math.max(0, Math.min(1, 0.5 + ((h[0] + h[3]) - (h[1] + h[2])) * 1.2 + ((h[0] + h[1]) - (h[2] + h[3])) * 0.6));
    quads.push({ v, z: (v[0].z + v[1].z + v[2].z + v[3].z) / 4, hm, luz, t, b });
  }
  quads.sort((a, b) => b.z - a.z);
  for (const q of quads) {
    ctx.beginPath(); ctx.moveTo(q.v[0].x, q.v[0].y); for (let i = 1; i < 4; i++) ctx.lineTo(q.v[i].x, q.v[i].y); ctx.closePath();
    ctx.globalAlpha = 0.18 + 0.82 * Math.min(1, q.hm * 1.4); ctx.fillStyle = base; ctx.fill();
    ctx.globalAlpha = (1 - q.luz) * 0.55; ctx.fillStyle = '#000'; ctx.fill();
    ctx.globalAlpha = 0.35; ctx.strokeStyle = C.panel; ctx.lineWidth = 0.6; ctx.stroke();
  }
  ctx.globalAlpha = 1;
  // cursor: a faixa (ano × patrimônio) mais próxima
  if (MT.mouse && !MT.arr) {
    let m = null, dm = 26 * 26;
    for (let i = quads.length - 1; i >= 0; i--) { const q = quads[i], cx = (q.v[0].x + q.v[2].x) / 2, cy = (q.v[0].y + q.v[2].y) / 2, d = (cx - MT.mouse.x) ** 2 + (cy - MT.mouse.y) ** 2; if (d < dm) { dm = d; m = q; } }
    const tip = $('mt-tip');
    if (m) {
      ctx.beginPath(); ctx.moveTo(m.v[0].x, m.v[0].y); for (let i = 1; i < 4; i++) ctx.lineTo(m.v[i].x, m.v[i].y); ctx.closePath(); ctx.strokeStyle = C.ink; ctx.lineWidth = 2; ctx.stroke();
      const dd = M.dens[m.t], a = Math.exp(M.lo + m.b * M.lg), b2 = Math.exp(M.lo + (m.b + 1) * M.lg), mes = M.mes[m.t];
      const txt = `<b>Ano ${num(mes / 12, mes % 12 ? 1 : 0)}</b><br><span class="k">entre</span> ${curto(a)} <span class="k">e</span> ${curto(b2)}: <b>${pct(dd.fr[m.b], 1)}</b> <span class="k">dos cenários</span><br><span class="k">abaixo de</span> ${curto(b2)}: ${pct(dd.acum[m.b], 0)} · <span class="k">acima:</span> ${pct(1 - dd.acum[m.b], 0)}`;
      tip.innerHTML = txt; tip.hidden = false;
      let lx = MT.mouse.x + 14, ly = MT.mouse.y + 14; if (lx + tip.offsetWidth > W - 4) lx = MT.mouse.x - tip.offsetWidth - 14; if (ly + tip.offsetHeight > H) ly = MT.mouse.y - tip.offsetHeight - 14;
      tip.style.left = `${Math.max(4, lx)}px`; tip.style.top = `${Math.max(4, ly)}px`;
      $('mt-read').innerHTML = txt.replace(/<br>/g, ' · ');
    } else tip.hidden = true;
  }
  ctx.fillStyle = C.muted; ctx.font = '10.5px "IBM Plex Sans", sans-serif'; ctx.textAlign = 'left';
  ctx.textAlign = 'right'; ctx.fillText('Arraste para girar · role para aproximar · W A S D para andar', W - 10, H - 8);
}

// ---------------------------------------------------------------- EXPLORAR: mapa 2D, nuvem 3D e ranking de todos os ativos
// Só dados reais: cada ponto usa os meses com cotação do ativo dentro da janela escolhida (em reais).
const EX_MET = {
  ret: ['Retorno ao ano', (v) => pct(v)], vol: ['Volatilidade ao ano', (v) => pct(v)], sharpe: ['Sharpe (retorno por risco)', (v) => num(v)],
  dd: ['Queda máxima', (v) => pct(v, 0)], anos: ['Anos de histórico', (v) => num(v, 1)], ci: ['Correlação com Ibovespa', (v) => num(v)],
  cs: ['Correlação com S&P 500', (v) => num(v)], pior: ['Pior mês', (v) => pct(v)], melhor: ['Melhor mês', (v) => pct(v)], pos: ['Meses positivos', (v) => pct(v, 0)],
  tam: ['Tamanho (log₁₀ de valor de mercado, PL ou liquidez)', (v) => num(v, 1)],
};
const EX_PADRAO = { modo: '3d', escala: { x: 100, y: 100, z: 100 }, x: 'vol', y: 'ret', z: 'dd', cor: 'classe', classes: [], janela: 10, min: 24, ordem: 'sharpe', desc: true };
const EX = { stats: null, chave: '', vis: [], view: null, rot: { yaw: -0.65, pitch: 0.38, zoom: 1 }, girar: false, sel: null, hover: null, fixos: [], ocultas: new Set(),
  proj: [], mouse: null, pronto: false, quadro: 0, faixas: null };
function exCor(nome) { return getComputedStyle(document.documentElement).getPropertyValue(nome).trim() || '#888'; }
function exCalcular() {
  const E = S.ex, ini = E.janela >= 99 ? BASE : Math.max(BASE, FIM_DADOS - E.janela * 12 + 1), chave = `${ini}|${S.cambio}`;
  if (EX.chave === chave && EX.stats) return EX.stats;
  const cdi = serie('CDI'), fx = serie('USDBRL'), ib = serie('IBOV'), sp = serie('SPY'), spA = info('SPY');
  const conv = (s, a, mi) => { const x = val(s, mi); if (isNaN(x)) return NaN; return a && a.moeda === 'USD' && S.cambio ? (1 + x) * (1 + val(fx, mi)) - 1 : x; };
  const acum = (c, x, y) => { c[0]++; c[1] += x; c[2] += y; c[3] += x * x; c[4] += y * y; c[5] += x * y; };
  const corr = (c) => { const n = c[0]; if (n < 6) return NaN; const vx = c[3] - c[1] * c[1] / n, vy = c[4] - c[2] * c[2] / n; return vx > 0 && vy > 0 ? (c[5] - c[1] * c[2] / n) / Math.sqrt(vx * vy) : NaN; };
  const out = [];
  for (const a of ATIVOS.values()) {
    const s = serie(a.t); if (!s) continue;
    const m0 = Math.max(ini, s.i0), m1 = Math.min(FIM_DADOS, s.i1);
    let n = 0, sl = 0, sm = 0, sq = 0, slc = 0, nc = 0, nivel = 0, pico = 0, dd = 0, pior = Infinity, melhor = -Infinity, pos = 0, prim = null;
    const ci = [0, 0, 0, 0, 0, 0], cs = [0, 0, 0, 0, 0, 0];
    for (let mi = m0; mi <= m1; mi++) {
      const x = conv(s, a, mi); if (!isFinite(x)) continue;
      if (prim === null) prim = mi;
      const l = Math.log1p(Math.max(x, -0.999)); n++; sl += l; sm += x; sq += x * x;
      const c = val(cdi, mi); if (!isNaN(c)) { slc += Math.log1p(c); nc++; }
      nivel += l; if (nivel > pico) pico = nivel; if (nivel - pico < dd) dd = nivel - pico;
      if (x < pior) pior = x; if (x > melhor) melhor = x; if (x > 0) pos++;
      const y1 = val(ib, mi); if (!isNaN(y1) && a.t !== 'IBOV') acum(ci, x, y1);
      const y2 = conv(sp, spA, mi); if (isFinite(y2) && a.t !== 'SPY') acum(cs, x, y2);
    }
    if (n < 2) continue;
    const ret = Math.exp(sl / n * 12) - 1, cdiA = nc ? Math.exp(slc / nc * 12) - 1 : 0, mm = sm / n;
    const vol = Math.sqrt(Math.max(0, (sq - n * mm * mm) / (n - 1)) * 12);
    out.push({ t: a.t, a, g: (TIPOS[a.tipo] || ['', 'indice'])[1], n, ret, vol, sharpe: vol > 0 ? (ret - cdiA) / vol : NaN, dd: Math.expm1(dd), anos: n / 12,
      ci: a.t === 'IBOV' ? 1 : corr(ci), cs: a.t === 'SPY' ? 1 : corr(cs), pior, melhor, pos: pos / n, tam: a.tam > 0 ? Math.log10(a.tam) : NaN, ini: prim, txt: `${a.t} ${a.n} ${a.gestor || ''} ${a.setor || ''}`.toLowerCase() });
  }
  let lc = 0, nc = 0; for (let mi = ini; mi <= FIM_DADOS; mi++) { const c = val(cdi, mi); if (!isNaN(c)) { lc += Math.log1p(c); nc++; } }
  EX.cdiRet = nc ? Math.exp(lc / nc * 12) - 1 : null;
  EX.stats = out; EX.chave = chave; EX.view = null; EX.faixas = null;
  return out;
}
function exFiltrar() {
  const E = S.ex, st = exCalcular(), b = (S.ex.busca || '').trim().toLowerCase();
  const partes = b ? b.split(/\s+/) : [];
  EX.vis = st.filter((p) => p.n >= E.min && (!E.classes.length || E.classes.some((k) => noGrupo(p.a, k))) && !EX.ocultas.has(p.g));
  for (const p of EX.vis) p.achou = partes.length > 0 && partes.every((w) => p.txt.includes(w));
  EX.nAchou = partes.length ? EX.vis.filter((p) => p.achou).length : 0;
  EX.faixas = null;
}
function exFaixa(k, q0 = 0.01, q1 = 0.99) { // faixa robusta do eixo (os extremos ficam na borda, contados)
  const v = EX.vis.map((p) => p[k]).filter(isFinite).sort((a, b) => a - b);
  if (!v.length) return [0, 1];
  let lo = quantil(v, q0), hi = quantil(v, q1); if (hi - lo < 1e-9) { lo -= 0.5; hi += 0.5; }
  const pad = (hi - lo) * 0.06; return [lo - pad, hi + pad];
}
function exCorDe(p, cores, escala) {
  if (S.ex.cor === 'classe') return cores[p.g] || cores.indice;
  const v = p[S.ex.cor]; if (!isFinite(v)) return 'rgba(140,140,140,.5)';
  const f = Math.max(0, Math.min(1, (v - escala[0]) / (escala[1] - escala[0] || 1)));
  const h = 215 - f * 175; return `hsl(${h} 70% ${48 + f * 6}%)`; // azul (baixo) → âmbar (alto)
}
function exPaleta() { const o = {}; for (const [k] of CLASSES) o[k] = exCor(`--c-${k}`); return o; }
function abrirExplorador() {
  if (!S.ex) S.ex = { ...EX_PADRAO };
  S.ex = { ...EX_PADRAO, ...S.ex };
  if (!S.ex.v51) { S.ex.v51 = true; S.ex.modo = '3d'; S.ex.escala = { x: 100, y: 100, z: 100 }; salvar(); } // 3D em destaque
  const E = S.ex;
  if (!EX.pronto) {
    EX.pronto = true;
    const opts = Object.entries(EX_MET).map(([k, [n]]) => `<option value="${k}">${n}</option>`).join('');
    for (const id of ['ex-x', 'ex-y', 'ex-z']) $(id).innerHTML = opts;
    $('ex-cor').innerHTML = '<option value="classe">Classe do ativo</option>' + opts;
    for (const [id, k] of [['ex-x', 'x'], ['ex-y', 'y'], ['ex-z', 'z'], ['ex-cor', 'cor']]) {
      $(id).value = E[k]; $(id).addEventListener('change', () => { E[k] = $(id).value; salvar(); EX.view = null; EX.faixas = null; exAtualizar(); });
    }
    $('ex-classes').innerHTML = GRUPOS.filter(([k]) => k && k !== 'rf').map(([k, n]) => `<button type="button" class="chip" data-k="${k}">${n}</button>`).join('');
    $('ex-classes').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; const k = b.dataset.k;
      E.classes = E.classes.includes(k) ? E.classes.filter((x) => x !== k) : [...E.classes, k]; salvar(); EX.view = null; exFiltrar(); exAtualizar(); });
    $('ex-busca').addEventListener('input', () => { E.busca = $('ex-busca').value; exFiltrar(); exAtualizar(); });
    $('ex-busca').addEventListener('keydown', (e) => { if (e.key === 'Enter') { const p = EX.vis.find((x) => x.achou); if (p) exSelecionar(p.t); } });
    $('ex-min').value = E.min; $('ex-min').addEventListener('input', () => { E.min = Math.max(2, Math.round(lerDec($('ex-min').value)) || 2); salvar(); exFiltrar(); exAtualizar(); });
    ligarLivre('nj-ex-janela', { obter: () => E.janela, definir: (v) => { E.janela = v; salvar(); }, max: 99, cheio: ANOS_BASE, aoMudar: () => { exFiltrar(); exAtualizar(); } });
    ligarSegEx();
    $('ex-reset').addEventListener('click', () => { EX.view = null; EX.rot = { yaw: -0.65, pitch: 0.38, zoom: 1 }; exPedir(); });
    $('ex-girar').addEventListener('click', () => { EX.girar = !EX.girar; $('ex-girar').classList.toggle('primary', EX.girar); if (EX.girar) exGirar(); });
    $('ex-leg').addEventListener('click', (e) => { const b = e.target.closest('[data-g]'); if (!b) return; const g = b.dataset.g;
      EX.ocultas.has(g) ? EX.ocultas.delete(g) : EX.ocultas.add(g); exFiltrar(); exAtualizar(); });
    $('ex-rank').addEventListener('click', (e) => {
      const th = e.target.closest('th[data-k]'); if (th) { const k = th.dataset.k; E.desc = E.ordem === k ? !E.desc : k !== 'vol' && k !== 'anos' ? true : k === 'anos'; E.ordem = k; salvar(); exRanking(); return; }
      const ad = e.target.closest('[data-add]'); if (ad) { exAdicionar(ad.dataset.add, ad); return; }
      const tr = e.target.closest('tr[data-t]'); if (tr) exSelecionar(tr.dataset.t, true);
    });
    exInteracao();
    EX.eixos = controleEixos($('ex-eixos'), E.escala, [['x', 'eixo horizontal'], ['y', 'eixo vertical'], ['z', 'profundidade (3D)']], () => { salvar(); exAplicaEscala2d(); exPedir(); });
    new ResizeObserver(() => { if (vista === 'explorar') exPedir(); }).observe($('ex-box'));
  }
  $('ex-busca').value = E.busca || '';
  [...$('ex-classes').children].forEach((b) => { const on = E.classes.includes(b.dataset.k); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  exFiltrar(); exAtualizar();
}
function ligarSegEx() {
  $('seg-ex-modo').addEventListener('click', (e) => { const b = e.target.closest('button'); if (!b) return; S.ex.modo = b.dataset.v; salvar(); exAtualizar(); if (S.ex.modo === '3d' && EX.girar) exGirar(); });
}
function exAtualizar() {
  const E = S.ex;
  [...$('seg-ex-modo').children].forEach((b) => { const on = b.dataset.v === E.modo; b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  [...$('ex-classes').children].forEach((b) => { const on = E.classes.includes(b.dataset.k); b.classList.toggle('on', on); b.setAttribute('aria-pressed', on); });
  $('f-ex-z').classList.toggle('dim', E.modo !== '3d'); $('ex-girar').hidden = E.modo !== '3d';
  if (EX.eixos) { EX.eixos.mostrarEixo('z', E.modo === '3d'); $('ex-eixos').hidden = E.modo === 'rank'; }
  $('ex-box').hidden = E.modo === 'rank'; $('ex-leg').hidden = E.modo === 'rank';
  $('ex-titulo').textContent = E.modo === '3d' ? 'Nuvem 3D' : E.modo === 'rank' ? 'Ranking' : 'Mapa';
  const st = EX.stats || [];
  $('ex-meta').textContent = `${st.length.toLocaleString('pt-BR')} ativos com dados`;
  $('ex-conta').textContent = `${EX.vis.length.toLocaleString('pt-BR')} no ${E.modo === '3d' ? 'espaço' : 'mapa'}${EX.nAchou ? ` · ${EX.nAchou} achados` : ''}`;
  const cores = exPaleta(), cont = {};
  for (const p of st) if (p.n >= E.min && (!E.classes.length || E.classes.some((k) => noGrupo(p.a, k)))) cont[p.g] = (cont[p.g] || 0) + 1;
  $('ex-leg').innerHTML = E.cor === 'classe' ? CLASSES.filter(([k]) => cont[k]).map(([k, n]) => `<button type="button" class="lg${EX.ocultas.has(k) ? ' off' : ''}" data-g="${k}"><i style="background:${cores[k]}"></i>${n} (${cont[k].toLocaleString('pt-BR')})</button>`).join('') + '<span>toque numa classe para esconder/mostrar</span>'
    : `<span><i style="background:hsl(215 70% 48%)"></i>${EX_MET[E.cor][0]} baixo</span><span><i style="background:hsl(40 70% 54%)"></i>alto</span>`;
  exRanking(); exPedir();
}
function exPedir() { if (EX.quadro) return; EX.quadro = requestAnimationFrame(() => { EX.quadro = 0; exDesenhar(); }); }
function exGirar() {
  if (!EX.girar || vista !== 'explorar' || S.ex.modo !== '3d') return;
  if (!EX.arrastando) { EX.rot.yaw += 0.006; exDesenhar(); }
  requestAnimationFrame(exGirar);
}
function exDesenhar() {
  if (vista !== 'explorar' || S.ex.modo === 'rank') return;
  const cv = $('ex-canvas'), W = cv.clientWidth, H = cv.clientHeight, dpr = window.devicePixelRatio || 1;
  if (!W || !H) return;
  if (cv.width !== Math.round(W * dpr) || cv.height !== Math.round(H * dpr)) { cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr); }
  const ctx = cv.getContext('2d'); ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, W, H);
  const C = { ink: exCor('--ink'), muted: exCor('--muted'), rule: exCor('--rule'), rule2: exCor('--rule-2'), accent: exCor('--accent'), amber: exCor('--amber'), panel: exCor('--panel') };
  const cores = exPaleta(), E = S.ex;
  const escala = E.cor !== 'classe' ? (() => { const v = EX.vis.map((p) => p[E.cor]).filter(isFinite).sort((a, b) => a - b); return v.length ? [quantil(v, 0.05), quantil(v, 0.95)] : [0, 1]; })() : null;
  ctx.font = '11px "IBM Plex Mono", monospace';
  if (!EX.vis.length) { ctx.fillStyle = C.muted; ctx.fillText('Nenhum ativo com esses filtros.', 20, 30); return; }
  if (E.modo === '3d') return exDesenhar3d(ctx, W, H, C, cores, escala);
  const ml = 58, mr = 14, mt = 14, mb = 40, iw = W - ml - mr, ih = H - mt - mb;
  if (!EX.view) { const fx = exFaixa(E.x), fy = exFaixa(E.y); EX.vista0 = { x0: fx[0], x1: fx[1], y0: fy[0], y1: fy[1] }; EX.view = { ...EX.vista0 }; exAplicaEscala2d(); }
  const V = EX.view, sx = (v) => ml + (v - V.x0) / (V.x1 - V.x0) * iw, sy = (v) => mt + ih - (v - V.y0) / (V.y1 - V.y0) * ih;
  EX.mapa = { ml, mt, iw, ih, sx, sy, V };
  const fmt = (k, v) => EX_MET[k][1](v);
  ctx.strokeStyle = C.rule; ctx.fillStyle = C.muted; ctx.lineWidth = 1;
  const px = passoBonito(V.x1 - V.x0, 6), py = passoBonito(V.y1 - V.y0, 5);
  ctx.textAlign = 'center';
  for (let v = Math.ceil(V.x0 / px) * px; v <= V.x1; v += px) { const x = sx(v); ctx.beginPath(); ctx.moveTo(x, mt); ctx.lineTo(x, mt + ih); ctx.stroke(); ctx.fillText(fmt(E.x, v), x, mt + ih + 14); }
  ctx.textAlign = 'right';
  for (let v = Math.ceil(V.y0 / py) * py; v <= V.y1; v += py) { const y = sy(v); ctx.beginPath(); ctx.moveTo(ml, y); ctx.lineTo(ml + iw, y); ctx.stroke(); ctx.fillText(fmt(E.y, v), ml - 6, y + 4); }
  ctx.strokeStyle = C.rule2; ctx.strokeRect(ml, mt, iw, ih);
  ctx.fillStyle = C.ink; ctx.textAlign = 'center'; ctx.font = '600 11px "IBM Plex Sans", sans-serif';
  ctx.fillText(`${EX_MET[E.x][0]} →`, ml + iw / 2, H - 6);
  ctx.save(); ctx.translate(12, mt + ih / 2); ctx.rotate(-Math.PI / 2); ctx.fillText(`${EX_MET[E.y][0]} →`, 0, 0); ctx.restore();
  ctx.font = '11px "IBM Plex Mono", monospace';
  // linha do CDI no eixo de retorno
  if (E.y === 'ret' || E.x === 'ret') {
    const c = EX.cdiRet;
    if (c !== null && c > (E.y === 'ret' ? V.y0 : V.x0) && c < (E.y === 'ret' ? V.y1 : V.x1)) { ctx.setLineDash([4, 4]); ctx.strokeStyle = C.muted; ctx.beginPath(); if (E.y === 'ret') { ctx.moveTo(ml, sy(c)); ctx.lineTo(ml + iw, sy(c)); } else { ctx.moveTo(sx(c), mt); ctx.lineTo(sx(c), mt + ih); } ctx.stroke(); ctx.setLineDash([]);
      ctx.fillStyle = C.muted; ctx.textAlign = 'left'; if (E.y === 'ret') ctx.fillText('CDI', ml + 4, sy(c) - 4); else ctx.fillText('CDI', sx(c) + 4, mt + 12); }
  }
  ctx.save(); ctx.beginPath(); ctx.rect(ml, mt, iw, ih); ctx.clip();
  const r = EX.vis.length > 3000 ? 2.1 : EX.vis.length > 600 ? 2.7 : 3.6, busca = EX.nAchou > 0;
  EX.proj = []; let fora = 0;
  const desenhaPonto = (p, x, y, rr, alfa) => { ctx.globalAlpha = alfa; ctx.fillStyle = exCorDe(p, cores, escala); ctx.beginPath(); ctx.arc(x, y, rr, 0, 7); ctx.fill(); };
  const destaque = [];
  for (const p of EX.vis) {
    const vx = p[E.x], vy = p[E.y]; if (!isFinite(vx) || !isFinite(vy)) continue;
    let x = sx(vx), y = sy(vy), borda = false;
    if (x < ml || x > ml + iw || y < mt || y > mt + ih) { fora++; borda = true; x = Math.max(ml + 2, Math.min(ml + iw - 2, x)); y = Math.max(mt + 2, Math.min(mt + ih - 2, y)); }
    EX.proj.push({ p, x, y });
    if (p.achou || EX.fixos.includes(p.t) || p.t === EX.sel) { destaque.push({ p, x, y }); continue; }
    desenhaPonto(p, x, y, borda ? r * 0.8 : r, busca ? 0.18 : borda ? 0.45 : 0.78);
  }
  ctx.globalAlpha = 1;
  for (const d of destaque) { desenhaPonto(d.p, d.x, d.y, r + 2.4, 1); ctx.strokeStyle = C.panel; ctx.lineWidth = 1.5; ctx.stroke(); }
  ctx.globalAlpha = 1; ctx.fillStyle = C.ink; ctx.textAlign = 'left'; ctx.font = '600 10.5px "IBM Plex Mono", monospace';
  const rotular = destaque.length <= 60 ? destaque : destaque.slice(0, 60);
  if (EX.proj.length <= 45) rotular.push(...EX.proj.filter((d) => !destaque.includes(d)));
  for (const d of rotular) ctx.fillText(rotuloAtivo(d.p.t).slice(0, 10), d.x + 6, d.y - 5);
  ctx.restore();
  EX.fora = fora;
  if (fora) { ctx.fillStyle = C.muted; ctx.textAlign = 'right'; ctx.font = '10.5px "IBM Plex Sans", sans-serif'; ctx.fillText(`${fora} na borda (fora da escala; arraste ou "Ver tudo")`, ml + iw - 4, mt + ih - 6); }
  // retícula que segue o cursor, com os valores nos eixos
  const m = EX.mouse;
  if (m && m.x >= ml && m.x <= ml + iw && m.y >= mt && m.y <= mt + ih) {
    ctx.strokeStyle = C.accent; ctx.globalAlpha = 0.55; ctx.setLineDash([3, 3]); ctx.beginPath(); ctx.moveTo(m.x, mt); ctx.lineTo(m.x, mt + ih); ctx.moveTo(ml, m.y); ctx.lineTo(ml + iw, m.y); ctx.stroke(); ctx.setLineDash([]); ctx.globalAlpha = 1;
    const vx = V.x0 + (m.x - ml) / iw * (V.x1 - V.x0), vy = V.y0 + (mt + ih - m.y) / ih * (V.y1 - V.y0);
    exEtiqueta(ctx, fmt(E.x, vx), m.x, mt + ih + 2, 'baixo', C); exEtiqueta(ctx, fmt(E.y, vy), ml - 2, m.y, 'esq', C);
  }
  exMarcaHover(ctx, C, r);
}
// no mapa 2D, a escala de cada eixo aproxima (mais de 100%) ou afasta (menos de 100%) em torno do centro atual
function exAplicaEscala2d() {
  if (!EX.view || !EX.vista0) return;
  const e = S.ex.escala, V = EX.view, B = EX.vista0, cx = (V.x0 + V.x1) / 2, cy = (V.y0 + V.y1) / 2;
  const hx = (B.x1 - B.x0) / 2 * 100 / (e.x || 100), hy = (B.y1 - B.y0) / 2 * 100 / (e.y || 100);
  EX.view = { x0: cx - hx, x1: cx + hx, y0: cy - hy, y1: cy + hy };
}
function exEtiqueta(ctx, txt, x, y, lado, C) {
  ctx.font = '600 10.5px "IBM Plex Mono", monospace'; const w = ctx.measureText(txt).width + 8, h = 16;
  const bx = lado === 'baixo' ? x - w / 2 : x - w, by = lado === 'baixo' ? y : y - h / 2;
  ctx.fillStyle = C.accent; ctx.fillRect(bx, by, w, h); ctx.fillStyle = C.panel; ctx.textAlign = 'center'; ctx.fillText(txt, bx + w / 2, by + 11.5);
}
function exMarcaHover(ctx, C, r) {
  const h = EX.hover; if (!h) return;
  ctx.strokeStyle = C.ink; ctx.lineWidth = 2; ctx.beginPath(); ctx.arc(h.x, h.y, r + 5, 0, 7); ctx.stroke();
}
function exProjetar3d(W, H) {
  const E = S.ex, { yaw, pitch, zoom } = EX.rot, cy = Math.cos(yaw), sy_ = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
  const f = Math.min(W, H) * 0.235 * zoom, d = 3.6, cx = W / 2, cyy = H / 2 + 18;
  const k = S.ex.escala || { x: 100, y: 100, z: 100 }, kx = k.x / 100, ky = k.y / 100, kz = k.z / 100;
  const A = EX.alvo || { x: 0, y: 0, z: 0 };
  return (X, Y, Z) => { X = (X - A.x) * kx; Y = (Y - A.y) * ky; Z = (Z - A.z) * kz; const x1 = X * cy - Z * sy_, z1 = X * sy_ + Z * cy, y2 = Y * cp - z1 * sp, z2 = Y * sp + z1 * cp;
    if (d + z2 < 0.25) return { x: NaN, y: NaN, z: z2, s: 0 };
    const s = f * d / (d + z2); return { x: cx + x1 * s, y: cyy - y2 * s, z: z2, s }; };
}
function exDesenhar3d(ctx, W, H, C, cores, escala) {
  const E = S.ex;
  if (!EX.faixas) EX.faixas = { x: exFaixa(E.x, 0.02, 0.98), y: exFaixa(E.y, 0.02, 0.98), z: exFaixa(E.z, 0.02, 0.98) };
  const F = EX.faixas, nrm = (k, v) => Math.max(-1.08, Math.min(1.08, ((v - F[k][0]) / (F[k][1] - F[k][0])) * 2 - 1));
  const P = exProjetar3d(W, H);
  // caixa, chão quadriculado e eixos
  const cantos = []; for (const X of [-1, 1]) for (const Y of [-1, 1]) for (const Z of [-1, 1]) cantos.push([X, Y, Z]);
  ctx.lineWidth = 1; ctx.strokeStyle = C.rule;
  for (let i = -1; i <= 1.001; i += 0.5) { const a = P(i, -1, -1), b = P(i, -1, 1), c = P(-1, -1, i), e = P(1, -1, i); ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.moveTo(c.x, c.y); ctx.lineTo(e.x, e.y); ctx.stroke(); }
  ctx.strokeStyle = C.rule2;
  for (let i = 0; i < 8; i++) for (let j = i + 1; j < 8; j++) { const a = cantos[i], b = cantos[j]; if (Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]) !== 2) continue; const pa = P(...a), pb = P(...b); ctx.beginPath(); ctx.moveTo(pa.x, pa.y); ctx.lineTo(pb.x, pb.y); ctx.stroke(); }
  const fmt = (k, v) => EX_MET[k][1](v);
  ctx.font = '10px "IBM Plex Mono", monospace'; ctx.fillStyle = C.muted; ctx.textAlign = 'center';
  for (const t of [-1, 0, 1]) {
    const vx = F.x[0] + (t + 1) / 2 * (F.x[1] - F.x[0]), vy = F.y[0] + (t + 1) / 2 * (F.y[1] - F.y[0]), vz = F.z[0] + (t + 1) / 2 * (F.z[1] - F.z[0]);
    const a = P(t, -1, 1.12); ctx.fillText(fmt(E.x, vx), a.x, a.y + 4);
    const b = P(-1.14, t, -1); ctx.fillText(fmt(E.y, vy), b.x, b.y + 3);
    const c = P(1.14, -1, t); ctx.fillText(fmt(E.z, vz), c.x, c.y + 4);
  }
  ctx.font = '600 11px "IBM Plex Sans", sans-serif'; ctx.fillStyle = C.ink;
  const lx = P(0, -1, 1.38), ly = P(-1.2, 1.2, -1), lz = P(1.42, -1, 0);
  ctx.fillText(EX_MET[E.x][0], lx.x, lx.y + 6); ctx.fillText(EX_MET[E.y][0], ly.x, ly.y); ctx.fillText(EX_MET[E.z][0], lz.x, lz.y + 6);
  // pontos: do mais longe para o mais perto, com neblina
  const pts = [];
  for (const p of EX.vis) { const vx = p[E.x], vy = p[E.y], vz = p[E.z]; if (!isFinite(vx) || !isFinite(vy) || !isFinite(vz)) continue;
    const q = P(nrm('x', vx), nrm('y', vy), nrm('z', vz)); if (!isFinite(q.x)) continue; pts.push({ p, x: q.x, y: q.y, z: q.z, s: q.s, X: nrm('x', vx), Z: nrm('z', vz) }); }
  pts.sort((a, b) => b.z - a.z);
  const r0 = EX.vis.length > 3000 ? 1.9 : EX.vis.length > 600 ? 2.5 : 3.4, busca = EX.nAchou > 0;
  for (const d of pts) {
    const dest = d.p.achou || EX.fixos.includes(d.p.t) || d.p.t === EX.sel;
    const nevoa = Math.max(0.18, Math.min(1, 0.75 - d.z * 0.35));
    ctx.globalAlpha = dest ? 1 : busca ? nevoa * 0.25 : nevoa;
    ctx.fillStyle = exCorDe(d.p, cores, escala);
    ctx.beginPath(); ctx.arc(d.x, d.y, (dest ? r0 + 2.4 : r0) * d.s / (Math.min(W, H) * 0.235 * EX.rot.zoom), 0, 7); ctx.fill();
    if (dest) { ctx.strokeStyle = C.panel; ctx.lineWidth = 1.5; ctx.stroke(); ctx.fillStyle = C.ink; ctx.font = '600 10.5px "IBM Plex Mono", monospace'; ctx.textAlign = 'left'; ctx.fillText(rotuloAtivo(d.p.t).slice(0, 10), d.x + 6, d.y - 5); }
  }
  ctx.globalAlpha = 1;
  EX.proj = pts;
  // ponto sob o cursor: linha até o chão (mostra a posição na profundidade)
  const h = EX.hover;
  if (h && h.X !== undefined) { const ch = P(h.X, -1, h.Z); ctx.strokeStyle = C.accent; ctx.setLineDash([3, 3]); ctx.beginPath(); ctx.moveTo(h.x, h.y); ctx.lineTo(ch.x, ch.y); ctx.stroke(); ctx.setLineDash([]); ctx.beginPath(); ctx.arc(ch.x, ch.y, 3, 0, 7); ctx.fillStyle = C.accent; ctx.fill(); }
  exMarcaHover(ctx, C, r0);
  ctx.fillStyle = C.muted; ctx.font = '10.5px "IBM Plex Sans", sans-serif'; ctx.textAlign = 'left';
  ctx.textAlign = 'right'; ctx.fillText('Arraste para girar · role para aproximar · W A S D para andar', W - 10, H - 8);
}
function exMaisPerto(x, y, raio = 14) {
  let melhor = null, dm = raio * raio;
  for (let i = EX.proj.length - 1; i >= 0; i--) { const d = EX.proj[i], dd = (d.x - x) ** 2 + (d.y - y) ** 2; if (dd < dm) { dm = dd; melhor = d; } }
  return melhor;
}
function exDica(d, x, y) {
  const tip = $('ex-tip');
  if (!d) { tip.hidden = true; return; }
  const p = d.p, a = p.a, E = S.ex, ks = [...new Set([E.x, E.y, ...(E.modo === '3d' ? [E.z] : []), 'ret', 'vol', 'sharpe', 'dd'])];
  tip.innerHTML = `<b>${esc(rotuloAtivo(p.t))}</b> · ${esc(TIPOS[a.tipo][0])}<span class="nm">${esc((a.tipo.startsWith('fundo') ? `${a.n} — ${a.gestor}` : a.n).slice(0, 70))}</span>` +
    `<div class="g">${ks.map((k) => `<span>${EX_MET[k][0].replace(/ \(.*\)/, '')}</span><span>${EX_MET[k][1](p[k])}</span>`).join('')}<span>Dados desde</span><span>${rotulo(p.ini)} (${p.n} m)</span></div>`;
  tip.hidden = false;
  const box = $('ex-box').getBoundingClientRect(), tw = tip.offsetWidth, th = tip.offsetHeight;
  let lx = x + 14, ly = y + 14; if (lx + tw > box.width - 4) lx = x - tw - 14; if (ly + th > box.height - 4) ly = y - th - 14;
  tip.style.left = `${Math.max(4, lx)}px`; tip.style.top = `${Math.max(4, ly)}px`;
}
function exInteracao() {
  const cv = $('ex-canvas'), ptrs = new Map();
  let ini = null, pinca = null, ultimoToque = 0;
  const pos = (e) => { const r = cv.getBoundingClientRect(); return { x: e.clientX - r.left, y: e.clientY - r.top }; };
  cv.addEventListener('pointerdown', (e) => { e.preventDefault(); cv.setPointerCapture(e.pointerId); const q = pos(e); ptrs.set(e.pointerId, q); ini = { ...q, view: EX.view && { ...EX.view }, rot: { ...EX.rot }, mov: 0, tipo: e.pointerType };
    if (ptrs.size === 2) { const [a, b] = [...ptrs.values()]; pinca = { d: Math.hypot(a.x - b.x, a.y - b.y), view: EX.view && { ...EX.view }, zoom: EX.rot.zoom, cx: (a.x + b.x) / 2, cy: (a.y + b.y) / 2 }; } });
  cv.addEventListener('pointermove', (e) => {
    const q = pos(e);
    if (ptrs.has(e.pointerId)) ptrs.set(e.pointerId, q);
    if (pinca && ptrs.size === 2) { const [a, b] = [...ptrs.values()], f = Math.hypot(a.x - b.x, a.y - b.y) / (pinca.d || 1); exZoom(1 / f, pinca.cx, pinca.cy, pinca); return; }
    if (ini && ptrs.size === 1) {
      const dx = q.x - ini.x, dy = q.y - ini.y; ini.mov = Math.max(ini.mov, Math.abs(dx) + Math.abs(dy));
      if (ini.mov > 4) {
        EX.arrastando = true; $('ex-tip').hidden = true; EX.hover = null;
        if (S.ex.modo === '3d') { EX.rot.yaw = ini.rot.yaw + dx * 0.009; EX.rot.pitch = Math.max(-1.45, Math.min(1.45, ini.rot.pitch + dy * 0.009)); }
        else if (ini.view && EX.mapa) { const M = EX.mapa, ux = (ini.view.x1 - ini.view.x0) / M.iw, uy = (ini.view.y1 - ini.view.y0) / M.ih;
          EX.view = { x0: ini.view.x0 - dx * ux, x1: ini.view.x1 - dx * ux, y0: ini.view.y0 + dy * uy, y1: ini.view.y1 + dy * uy }; }
        exPedir(); return;
      }
    }
    if (e.pointerType === 'mouse' || ini) { EX.mouse = q; const d = exMaisPerto(q.x, q.y); EX.hover = d; exDica(d, q.x, q.y); exPedir(); }
  });
  const fim = (e) => {
    const q = pos(e); ptrs.delete(e.pointerId);
    if (ptrs.size < 2) pinca = null;
    if (ini && ini.mov <= 4 && ptrs.size === 0) { // toque/clique: abre o ativo
      const d = exMaisPerto(q.x, q.y, e.pointerType === 'mouse' ? 14 : 22);
      const agora = Date.now();
      if (!d && agora - ultimoToque < 350) { EX.view = null; EX.rot = { yaw: -0.65, pitch: 0.38, zoom: 1 }; }
      ultimoToque = agora;
      if (d) { EX.hover = d; exDica(d, q.x, q.y); exSelecionar(d.p.t); }
    }
    if (ptrs.size === 0) { ini = null; EX.arrastando = false; }
    exPedir();
  };
  cv.addEventListener('pointerup', fim); cv.addEventListener('pointercancel', fim);
  cv.addEventListener('pointerleave', (e) => { if (e.pointerType === 'mouse') { EX.mouse = null; EX.hover = null; $('ex-tip').hidden = true; exPedir(); } });
  cv.addEventListener('wheel', (e) => { e.preventDefault(); const q = pos(e); exZoom(e.deltaY > 0 ? 1.15 : 1 / 1.15, q.x, q.y); }, { passive: false });
  cv.addEventListener('dblclick', () => { EX.view = null; EX.rot = { yaw: -0.65, pitch: 0.38, zoom: 1 }; EX.alvo = { x: 0, y: 0, z: 0 }; exPedir(); });
  ligarNav3d(cv, EX, exPedir, {
    reiniciar: () => { EX.view = null; EX.rot = { yaw: -0.65, pitch: 0.38, zoom: 1 }; },
    plano: () => S.ex.modo !== '3d',
    mover2d: (k, dt) => { // no mapa 2D: W/A/S/D movem a vista, Q/E e +/− aproximam
      const V = EX.view; if (!V) return; const wx = V.x1 - V.x0, wy = V.y1 - V.y0, v = 0.6 * dt;
      const dx = (k.has('d') || k.has('dir') ? 1 : 0) - (k.has('a') || k.has('esq') ? 1 : 0), dy = (k.has('w') || k.has('cima') ? 1 : 0) - (k.has('s') || k.has('baixo') ? 1 : 0);
      const z = k.has('e') || k.has('mais') ? 1 - dt * 1.2 : k.has('q') || k.has('menos') ? 1 + dt * 1.2 : 1;
      const cx = (V.x0 + V.x1) / 2 + dx * wx * v, cy = (V.y0 + V.y1) / 2 + dy * wy * v;
      EX.view = { x0: cx - wx * z / 2, x1: cx + wx * z / 2, y0: cy - wy * z / 2, y1: cy + wy * z / 2 };
    },
  });
}
function exZoom(f, x, y, base) {
  if (S.ex.modo === '3d') { EX.rot.zoom = Math.max(0.4, Math.min(8, (base ? base.zoom : EX.rot.zoom) / f)); exPedir(); return; }
  const V = base && base.view ? base.view : EX.view, M = EX.mapa; if (!V || !M) return;
  const vx = V.x0 + (x - M.ml) / M.iw * (V.x1 - V.x0), vy = V.y0 + (M.mt + M.ih - y) / M.ih * (V.y1 - V.y0);
  EX.view = { x0: vx - (vx - V.x0) * f, x1: vx + (V.x1 - vx) * f, y0: vy - (vy - V.y0) * f, y1: vy + (V.y1 - vy) * f };
  exPedir();
}
function exRanking() {
  const E = S.ex, k = E.ordem, lista = EX.vis.filter((p) => isFinite(p[k])).sort((a, b) => (E.desc ? b[k] - a[k] : a[k] - b[k]));
  const achados = EX.nAchou ? lista.filter((p) => p.achou) : lista, top = achados.slice(0, 150);
  $('ex-rank-t').textContent = `Ranking por ${EX_MET[k][0].toLowerCase()} (${top.length} de ${achados.length.toLocaleString('pt-BR')}; histórico ≥ ${E.min} meses)`;
  const cols = [['ret', 'Retorno'], ['vol', 'Volat.'], ['sharpe', 'Sharpe'], ['dd', 'Queda máx.'], ['anos', 'Anos']];
  $('ex-rank').innerHTML = `<thead><tr><th>#</th><th>Ativo</th><th class="hide-s">Classe</th>${cols.map(([c, n]) => `<th data-k="${c}" class="${c === k ? 'on' : ''}">${n}${c === k ? (E.desc ? ' ↓' : ' ↑') : ''}</th>`).join('')}<th></th></tr></thead><tbody>` +
    top.map((p, i) => `<tr data-t="${esc(p.t)}"><td>${i + 1}</td><td class="t"><span class="tk">${esc(rotuloAtivo(p.t))}</span><span class="nm">${esc((p.a.tipo.startsWith('fundo') ? p.a.gestor : p.a.n).slice(0, 34))}</span></td>` +
      `<td class="hide-s"><span class="cls"><i class="sw" style="background:${corClasse[p.g]}"></i>${TIPOS[p.a.tipo][0]}</span></td><td class="${p.ret < 0 ? 'neg' : ''}">${pct(p.ret)}</td><td>${pct(p.vol, 0)}</td><td>${num(p.sharpe)}</td><td>${pct(p.dd, 0)}</td><td>${num(p.anos, 1)}</td>` +
      `<td><button type="button" class="btn" data-add="${esc(p.t)}" title="Adicionar à carteira ${esc(cartAtiva().nome)}">+</button></td></tr>`).join('') + '</tbody>';
}
function exAdicionar(t, botao) {
  const it = cartAtiva().itens;
  if (!it.some((i) => i.t === t)) it.push({ t, p: it.length ? 10 : 100 });
  mudouCarteiras(); desenharCarteira();
  if (botao) { const o = botao.textContent; botao.textContent = '✓'; setTimeout(() => { botao.textContent = o; }, 1200); }
}
function exSelecionar(t, rolar) {
  EX.sel = t; exPedir(); exDetalhe(t);
  if (rolar) $('ex-det-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
}
function exDetalhe(t) {
  const p = (EX.stats || []).find((x) => x.t === t); if (!p) return;
  const a = p.a; $('ex-det-panel').hidden = false;
  $('ex-det-t').textContent = `${rotuloAtivo(t)} · ${a.tipo.startsWith('fundo') ? a.n : a.n}`.slice(0, 80);
  $('ex-det-m').textContent = `${TIPOS[a.tipo][0]}${a.setor ? ` · ${a.setor}` : ''} · dados desde ${rotulo(p.ini)}`;
  const kpi = (k) => `<div class="kpi"><span class="k">${EX_MET[k][0].replace(/ \(.*\)/, '')}</span><span class="v ${(k === 'ret' || k === 'pior' || k === 'dd') && p[k] < 0 ? 'neg' : ''}">${EX_MET[k][1](p[k])}</span></div>`;
  $('ex-det-kpis').innerHTML = ['ret', 'vol', 'sharpe', 'dd', 'pior', 'pos', 'ci', 'anos'].map(kpi).join('');
  const fixado = EX.fixos.includes(t);
  $('ex-det-btns').innerHTML = `<button type="button" class="btn primary" data-a="add">Adicionar à carteira ${esc(LETRAS[S.ativa])} (${esc(cartAtiva().nome.slice(0, 16))})</button>` +
    `<button type="button" class="btn" data-a="fixar">${fixado ? 'Tirar da comparação' : 'Comparar no gráfico'}</button>` + (EX.fixos.length ? '<button type="button" class="btn" data-a="limpar">Limpar comparação</button>' : '');
  $('ex-det-btns').onclick = (e) => { const b = e.target.closest('[data-a]'); if (!b) return;
    if (b.dataset.a === 'add') exAdicionar(t, b);
    if (b.dataset.a === 'fixar') { EX.fixos = fixado ? EX.fixos.filter((x) => x !== t) : [...EX.fixos, t].slice(-6); exDetalhe(t); exPedir(); }
    if (b.dataset.a === 'limpar') { EX.fixos = []; exDetalhe(t); exPedir(); } };
  exGraficoAtivos([...new Set([t, ...EX.fixos])]);
}
// crescimento de R$ 10 mil (em reais) de um ou mais ativos, com cursor que segue o mouse/dedo
function exGraficoAtivos(lista) {
  const E = S.ex, ini = E.janela >= 99 ? BASE : Math.max(BASE, FIM_DADOS - E.janela * 12 + 1), fx = serie('USDBRL');
  const cores = ['var(--accent)', 'var(--pB)', 'var(--pC)', 'var(--bm1)', 'var(--bm3)', 'var(--bm4)', 'var(--bm2)'];
  const linhas = lista.map((t, i) => {
    const a = info(t), s = serie(t), m0 = Math.max(ini, s.i0), pts = []; let v = 10000, pico = 10000, comecou = false;
    for (let mi = m0; mi <= Math.min(FIM_DADOS, s.i1); mi++) {
      let x = val(s, mi); if (isNaN(x)) { if (comecou) pts.push({ mi, v, dd: v / pico - 1, sem: true }); continue; }
      if (a.moeda === 'USD' && S.cambio) x = (1 + x) * (1 + val(fx, mi)) - 1;
      if (!comecou) { comecou = true; pts.push({ mi: mi - 1, v: 10000, dd: 0 }); }
      v *= 1 + x; pico = Math.max(pico, v); pts.push({ mi, v, dd: v / pico - 1 });
    }
    return { t, cor: cores[i % cores.length], pts };
  }).filter((l) => l.pts.length > 1);
  const box = $('ex-det-chart'); box.classList.add('hov');
  if (!linhas.length) { box.innerHTML = '<p class="empty-note">Sem meses com cotação nesta janela.</p>'; return; }
  const W = largura(box), H = 230, ml = 62, mr = 12, mt = 10, mb = 26, iw = W - ml - mr, ih = H - mt - mb;
  const m0 = Math.min(...linhas.map((l) => l.pts[0].mi)), m1 = Math.max(...linhas.map((l) => l.pts[l.pts.length - 1].mi));
  const vs = linhas.flatMap((l) => l.pts.map((q) => q.v)), lmin = Math.log(Math.min(...vs) * 0.95), lmax = Math.log(Math.max(...vs) * 1.05);
  const x = (mi) => ml + (mi - m0) / Math.max(1, m1 - m0) * iw, y = (v) => mt + ih - (Math.log(v) - lmin) / (lmax - lmin || 1) * ih;
  let svg = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Crescimento de R$ 10 mil">`;
  for (const v of [2500, 5000, 10000, 20000, 40000, 80000, 160000, 320000, 640000, 1280000]) if (Math.log(v) > lmin && Math.log(v) < lmax) svg += `<line x1="${ml}" x2="${W - mr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--rule)"/><text x="${ml - 6}" y="${y(v) + 4}" text-anchor="end">${brl(v)}</text>`;
  const anos = Math.max(1, Math.round((m1 - m0) / 12 / 6));
  for (let mi = Math.ceil(m0 / 12) * 12; mi <= m1; mi += 12 * anos) svg += `<text x="${x(mi)}" y="${H - 8}" text-anchor="middle">${Math.floor(mi / 12)}</text>`;
  for (const l of linhas) svg += `<polyline points="${l.pts.map((q) => `${x(q.mi).toFixed(1)},${y(q.v).toFixed(1)}`).join(' ')}" fill="none" stroke="${l.cor}" stroke-width="${l === linhas[0] ? 2.2 : 1.6}"/>`;
  svg += `<line class="hov-line" id="ex-cur" x1="0" x2="0" y1="${mt}" y2="${mt + ih}" stroke="var(--ink)" stroke-dasharray="3 3" opacity="0"/>` + linhas.map((l, i) => `<circle class="hov-line" id="ex-cur-${i}" r="4" fill="${l.cor}" stroke="var(--panel)" stroke-width="1.5" opacity="0"/>`).join('') + '</svg>';
  box.innerHTML = svg;
  $('ex-det-leg').innerHTML = linhas.map((l) => `<span><i class="ln" style="border-color:${l.cor}"></i>${esc(rotuloAtivo(l.t))}</span>`).join('') + '<span>R$ 10 mil investidos, em reais, escala log</span>';
  const ler = (cx) => {
    const mi = Math.round(m0 + (cx - ml) / iw * (m1 - m0)); if (mi < m0 || mi > m1) return;
    const el = box.querySelector('#ex-cur'); el.setAttribute('x1', x(mi)); el.setAttribute('x2', x(mi)); el.setAttribute('opacity', 0.6);
    const partes = linhas.map((l, i) => { const q = l.pts.find((z) => z.mi === mi), c = box.querySelector(`#ex-cur-${i}`);
      if (!q) { c.setAttribute('opacity', 0); return ''; } c.setAttribute('cx', x(mi)); c.setAttribute('cy', y(q.v)); c.setAttribute('opacity', 1);
      return `<b style="color:${l.cor}">${esc(rotuloAtivo(l.t))}</b> ${brl(q.v)} (${q.v >= 10000 ? '+' : ''}${pct(q.v / 10000 - 1, 0)}${q.dd < -0.005 ? `, ${pct(q.dd, 0)} do topo` : ''}${q.sem ? ', sem negociação' : ''})`; }).filter(Boolean);
    $('ex-det-read').innerHTML = `<b>${rotulo(mi)}</b> · ${partes.join(' · ')}`;
  };
  const svgEl = box.querySelector('svg');
  const mover = (e) => { const r = svgEl.getBoundingClientRect(); ler((e.clientX - r.left) * (W / r.width)); };
  svgEl.addEventListener('pointermove', mover); svgEl.addEventListener('pointerdown', mover);
  ler(ml + iw);
}

// ---------------------------------------------------------------- início
let redim;
window.addEventListener('resize', () => {
  clearTimeout(redim);
  redim = setTimeout(() => {
    if (vista === 'simulacao' && ultimo) { desenharLeque(); desenharHistograma(); pedirMontanha(); }
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
