/* Finanças Martins — panorama financeiro sem login, dados salvos no aparelho. */
(() => {
  'use strict';

  /* ============ constantes ============ */
  const STORE_KEY = 'financas-martins-v1';
  const BANK_COLORS = {
    light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
    dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#0ca30c', '#9085e9', '#e66767'],
  };
  const COMMON_BANKS = ['Nubank', 'Itaú', 'Bradesco', 'Banco do Brasil', 'Caixa', 'Santander', 'Inter', 'C6 Bank', 'BTG Pactual', 'PicPay', 'Mercado Pago', 'Sicoob', 'Sicredi', 'Neon', 'PagBank', 'XP', 'Carteira (dinheiro)'];

  // neutral: movimentações que não são gasto nem renda (não entram nas estatísticas de gasto)
  const CATS = [
    { id: 'alimentacao', name: 'Alimentação', ic: '🍔', kw: ['ifood', 'restaurante', 'lanchonete', 'padaria', 'burger', 'pizza', 'mcdonald', 'mc donald', 'bk ', 'subway', 'rappi', 'cafe', 'café', 'bar ', 'sushi', 'acai', 'açaí', 'starbucks', 'outback', 'habib', 'giraffas', 'lanche', 'almoço', 'almoco', 'jantar', 'zé delivery', 'ze delivery', 'cafeteria', 'sorvete', 'churrasc', 'boteco'] },
    { id: 'mercado', name: 'Mercado', ic: '🛒', kw: ['mercado', 'supermerc', 'carrefour', 'pao de acucar', 'pão de açúcar', 'assai', 'assaí', 'atacad', 'hortifruti', 'sacolao', 'sacolão', 'dia %', 'big bompreco', 'zaffari', 'savegnago', 'sams club', 'makro', 'oba ', 'st marche', 'feira', 'açougue', 'acougue', 'minuto pa', 'hirota'] },
    { id: 'transporte', name: 'Transporte', ic: '🚗', kw: ['uber', '99app', '99 ', '99pop', 'posto', 'combust', 'gasolina', 'shell', 'ipiranga', 'petrobras', 'br mania', 'estaciona', 'sem parar', 'conectcar', 'veloe', 'metro', 'metrô', 'onibus', 'ônibus', 'cabify', 'pedagio', 'pedágio', 'bilhete unico', 'cptm', 'zul ', 'mecanic', 'oficina', 'ipva', 'detran', 'lavagem', 'indriver'] },
    { id: 'moradia', name: 'Moradia', ic: '🏠', kw: ['aluguel', 'condominio', 'condomínio', 'energia', 'enel', 'light ', 'cemig', 'copel', 'celesc', 'coelba', 'sabesp', 'cedae', 'copasa', 'agua', 'água', 'comgas', 'comgás', 'iptu', 'quintoandar', 'imobiliaria', 'imobiliária', 'leroy', 'telhanorte', 'material de constr'] },
    { id: 'contas', name: 'Contas e serviços', ic: '📱', kw: ['vivo', 'claro', 'tim ', 'oi fibra', 'internet', 'net servi', 'telefone', 'celular', 'recarga', 'sky ', 'correios', 'cartorio', 'cartório', 'lavanderia'] },
    { id: 'assinaturas', name: 'Assinaturas', ic: '🔁', kw: ['netflix', 'spotify', 'prime video', 'amazon prime', 'disney', 'hbo', 'max.com', 'youtube', 'apple.com', 'apple ', 'google one', 'google storage', 'icloud', 'deezer', 'globoplay', 'chatgpt', 'openai', 'claude.ai', 'anthropic', 'paramount', 'crunchyroll', 'xbox game pass', 'playstation plus', 'ps plus', 'microsoft 365', 'adobe', 'canva', 'smart fit', 'smartfit', 'academia', 'gympass', 'wellhub', 'totalpass', 'kindle'] },
    { id: 'saude', name: 'Saúde', ic: '💊', kw: ['farmacia', 'farmácia', 'drogaria', 'droga raia', 'drogasil', 'raia', 'pague menos', 'panvel', 'pacheco', 'hospital', 'clinica', 'clínica', 'laboratorio', 'laboratório', 'unimed', 'amil', 'bradesco saude', 'sulamerica', 'hapvida', 'dentista', 'odonto', 'medico', 'médico', 'psicolog', 'exame', 'otica', 'ótica'] },
    { id: 'compras', name: 'Compras', ic: '🛍️', kw: ['amazon', 'mercado livre', 'mercadolivre', 'mercadolibre', 'shopee', 'magalu', 'magazine', 'americanas', 'aliexpress', 'shein', 'renner', 'riachuelo', 'c&a', 'zara', 'centauro', 'netshoes', 'kabum', 'casas bahia', 'ponto frio', 'fast shop', 'decathlon', 'havaianas', 'nike', 'adidas', 'boticario', 'boticário', 'natura', 'sephora', 'temu', 'loja', 'shopping'] },
    { id: 'lazer', name: 'Lazer e viagens', ic: '🎉', kw: ['cinema', 'cinemark', 'ingresso', 'sympla', 'eventim', 'show', 'steam', 'playstation', 'nintendo', 'xbox', 'viagem', 'hotel', 'airbnb', 'booking', 'decolar', 'latam', 'gol linhas', 'azul linhas', 'azul ', 'passagem', 'teatro', 'parque', 'balada', 'festa'] },
    { id: 'educacao', name: 'Educação', ic: '📚', kw: ['escola', 'colegio', 'colégio', 'faculdade', 'universidade', 'curso', 'udemy', 'alura', 'coursera', 'livraria', 'livro', 'mensalidade', 'material escolar', 'duolingo'] },
    { id: 'taxas', name: 'Tarifas e juros', ic: '🏦', kw: ['tarifa', 'iof', 'juros', 'anuidade', 'multa', 'encargo', 'cesta de servi', 'mora'] },
    { id: 'pessoas', name: 'Pix e transferências', ic: '↗️', kw: ['pix', 'ted', 'doc ', 'transferencia', 'transferência', 'transf'] },
    { id: 'outros', name: 'Outros gastos', ic: '•' },
    { id: 'receitas', name: 'Salário e receitas', ic: '💰', income: true, kw: ['salario', 'salário', 'proventos', 'folha', 'pagamento recebido', 'pro labore', 'pró-labore', 'rendimento', 'cashback', 'reembolso', 'estorno', 'freela', 'venda'] },
    { id: 'invest', name: 'Investimentos', ic: '📈', neutral: true, kw: ['aplicacao', 'aplicação', 'resgate', 'cdb', 'tesouro', 'invest', 'poupanca', 'poupança', 'lci', 'lca', 'corretora', 'caixinha', 'cofrinho', 'renda fixa', 'fundo'] },
    { id: 'fatura', name: 'Pagamento de fatura', ic: '💳', neutral: true, kw: ['pagamento de fatura', 'pagto fatura', 'pgto fatura', 'fatura cartao', 'fatura cartão', 'pag fatura', 'pagamento fatura', 'pagamento recebido - obrigado'] },
  ];
  const CAT = Object.fromEntries(CATS.map(c => [c.id, c]));
  const MES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
  const MES_LONGO = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];
  const SEMANA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
  const SEMANA_LONGO = ['domingo', 'segunda', 'terça', 'quarta', 'quinta', 'sexta', 'sábado'];

  /* ============ utilidades ============ */
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
  const monthLabel = k => { const [y, m] = k.split('-'); return `${MES[+m - 1]}/${y.slice(2)}`; };
  const dayLabel = s => {
    if (s === todayStr()) return 'Hoje';
    if (s === addDays(todayStr(), -1)) return 'Ontem';
    const d = pd(s);
    return `${SEMANA_LONGO[d.getDay()]}, ${d.getDate()} de ${MES_LONGO[d.getMonth()]}${d.getFullYear() !== new Date().getFullYear() ? ' de ' + d.getFullYear() : ''}`;
  };
  const shortDate = s => { const d = pd(s); return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${String(d.getFullYear()).slice(2)}`; };
  const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
  const norm = s => String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const isDark = () => {
    const t = document.documentElement.dataset.theme;
    return t ? t === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  };
  const css = v => getComputedStyle(document.documentElement).getPropertyValue(v).trim();

  /* ============ dados ============ */
  function load() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (raw) { const d = JSON.parse(raw); d.rules ||= {}; return d; }
    } catch (e) { /* armazenamento indisponível */ }
    return { version: 1, banks: [], txs: [], rules: {} };
  }
  let db = load();
  function save() {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(db)); }
    catch (e) { toast('Não consegui salvar neste navegador (modo anônimo?). Baixe um backup em Ajustes.'); }
  }
  const bankById = id => db.banks.find(b => b.id === id);
  const bankColor = id => {
    const i = db.banks.findIndex(b => b.id === id);
    const pal = BANK_COLORS[isDark() ? 'dark' : 'light'];
    return pal[(i < 0 ? 0 : i) % pal.length];
  };
  function ensureBank(name) {
    name = (name || '').trim() || 'Conta';
    let b = db.banks.find(x => norm(x.name) === norm(name));
    if (!b) { b = { id: uid(), name, initial: 0 }; db.banks.push(b); }
    return b;
  }

  const AGGREGATORS = ['ifood', 'uber', '99app', 'rappi', 'amazon', 'mercadolivre', 'mercado livre', 'shopee', 'netflix', 'spotify', 'apple', 'google', 'paypal', 'aliexpress', 'shein', 'steam', 'airbnb', 'cabify', 'magalu'];
  function merchant(desc) {
    let s = norm(desc);
    s = s.replace(/^(compra( no)?( cartao)?( de)?( debito| credito)?( internacional)?|pagamento( de)?( boleto)?|pagto|pgto|pag|pix (enviado|recebido|transf\w*|qr ?code)?( para| de)?|transferencia (enviada|recebida)( pelo pix)?( para| de)?|transf(erencia)?( pix)?( enviada| recebida)?|ted( enviada| recebida)?|doc|deb(ito)?( aut(omatico)?)?|cp|elo|visa|master(card)?|compra aprovada|debito em conta)\b\s*[-:]?\s*/g, '');
    s = s.replace(/\d{2}\/\d{2}(\/\d{2,4})?/g, ' ').replace(/\d{2}:\d{2}/g, ' ');
    s = s.split(/\bcpf\b|\bcnpj\b|•|\*{2,}/)[0];
    // "pagamento efetuado - imobiliaria": pula pedaços genéricos e fica com o nome
    const GENERIC = /^(efetuad[oa]|enviad[oa]|recebid[oa]|realizad[oa]|aprovad[oa]|agendad[oa]|compra|pagamento|transferencia|pix|ted|doc|debito|credito|boleto|online|no debito|no credito)?$/;
    const parts = s.split(/ - /).map(p => p.replace(/[*_\-–—|#.,;:/\\()]/g, ' ').replace(/\b\d+\b/g, ' ').replace(/\s+/g, ' ').trim());
    s = parts.find(p => !GENERIC.test(p) && !p.split(' ').every(w => GENERIC.test(w))) || parts[0];
    s = s.replace(/[*_\-–—|#.,;:/\\()]/g, ' ').replace(/\b\d+\b/g, ' ').replace(/\s+/g, ' ').trim();
    if (!s) return 'Outros';
    const agg = AGGREGATORS.find(a => s.startsWith(a));
    const out = agg || s.split(' ').filter(w => w.length > 1 || /\d/.test(w)).slice(0, 3).join(' ') || s;
    return out.replace(/\b\w/g, c => c.toUpperCase());
  }

  function guessCat(desc, amount) {
    const key = norm(merchant(desc));
    if (db.rules[key] && CAT[db.rules[key]]) return db.rules[key];
    const s = ' ' + norm(desc) + ' ';
    const hit = id => CAT[id].kw.some(k => s.includes(norm(k)));
    if (hit('fatura')) return 'fatura';
    if (hit('invest')) return 'invest';
    if (amount > 0) return 'receitas';
    for (const c of CATS) {
      if (c.income || c.neutral || !c.kw || c.id === 'pessoas') continue;
      if (hit(c.id)) return c.id;
    }
    if (hit('pessoas')) return 'pessoas';
    return 'outros';
  }

  /* ============ estado de tela ============ */
  const ui = {
    view: 'panorama',
    period: '6m',
    bank: '',
    monthPick: null,
    list: { q: '', cat: '', type: '', month: null, merchant: null, limit: 120 },
  };
  try { const p = localStorage.getItem(STORE_KEY + ':period'); if (p) ui.period = p; } catch (e) { /* */ }
  const charts = {};

  /* ============ navegação ============ */
  function go(view) {
    ui.view = view;
    $$('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    $$('.view').forEach(v => v.classList.toggle('active', v.id === 'view-' + view));
    $('.fab').classList.toggle('hidden', view === 'importar' || view === 'ajustes');
    window.scrollTo({ top: 0 });
    render();
  }
  $$('.tabs button').forEach(b => b.addEventListener('click', () => go(b.dataset.view)));
  document.addEventListener('click', e => {
    const g = e.target.closest('[data-go]'); if (g) go(g.dataset.go);
    const a = e.target.closest('[data-action="new"]'); if (a) openTxSheet();
  });

  function render() {
    if (ui.view === 'panorama') renderDashboard();
    else if (ui.view === 'lancamentos') renderList();
    else if (ui.view === 'importar') renderImport();
    else if (ui.view === 'ajustes') renderSettings();
  }

  /* ============ cálculo do período ============ */
  const scoped = () => db.txs.filter(t => !ui.bank || t.bank === ui.bank);
  function dataBounds(txs) {
    let min = null, max = null;
    for (const t of txs) { if (!min || t.date < min) min = t.date; if (!max || t.date > max) max = t.date; }
    return { min, max };
  }
  function getRange() {
    const txs = scoped();
    const { min, max } = dataBounds(txs);
    const today = todayStr();
    if (ui.monthPick) {
      const [y, m] = ui.monthPick.split('-').map(Number);
      const from = `${ui.monthPick}-01`, end = ds(new Date(y, m, 0));
      return { from, to: end < today ? end : (max && max > today ? max : today), months: 1, picked: true,
        prev: { from: ds(new Date(y, m - 2, 1)), to: ds(new Date(y, m - 1, 0)) } };
    }
    const anchor = max && max > today ? max : (max && max.slice(0, 7) < today.slice(0, 7) ? max : today);
    const a = pd(anchor);
    const n = { month: 1, '3m': 3, '6m': 6, '12m': 12 }[ui.period];
    if (!n) return { from: min || anchor, to: anchor, months: null, prev: null };
    const from = ds(new Date(a.getFullYear(), a.getMonth() - (n - 1), 1));
    const endOfMonth = ds(new Date(a.getFullYear(), a.getMonth() + 1, 0));
    const to = endOfMonth < anchor ? endOfMonth : anchor;
    const prevFrom = ds(new Date(a.getFullYear(), a.getMonth() - (2 * n - 1), 1));
    return { from, to, months: n, prev: { from: prevFrom, to: addDays(from, -1) } };
  }
  const inRange = (t, r) => t.date >= r.from && t.date <= r.to;
  const isNeutral = t => CAT[t.cat]?.neutral;
  function totals(txs) {
    let inc = 0, out = 0;
    for (const t of txs) { if (isNeutral(t)) continue; if (t.amount > 0) inc += t.amount; else out -= t.amount; }
    return { inc, out, net: inc - out };
  }
  function monthsIn(r) {
    const out = []; const a = pd(r.from), b = pd(r.to);
    const d = new Date(a.getFullYear(), a.getMonth(), 1);
    while (d <= b) { out.push(`${d.getFullYear()}-${pad(d.getMonth() + 1)}`); d.setMonth(d.getMonth() + 1); }
    return out;
  }
  const daysBetween = (a, b) => Math.round((pd(b) - pd(a)) / 86400000) + 1;

  /* ============ painel ============ */
  function chartTheme() {
    return {
      text: css('--text'), text2: css('--text-2'), muted: css('--muted'), grid: css('--grid'), axis: css('--axis'),
      s1: css('--s1'), s2: css('--s2'), s1soft: css('--s1-soft'), surface: css('--surface'),
    };
  }
  function baseOpts(th) {
    return {
      responsive: true, maintainAspectRatio: false, animation: { duration: 350 },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: th.text, titleColor: th.surface, bodyColor: th.surface, padding: 10, cornerRadius: 8,
          displayColors: true, boxPadding: 4, usePointStyle: true,
          callbacks: { label: c => ` ${c.dataset.label ? c.dataset.label + ': ' : ''}${fmt(c.parsed.y ?? c.parsed)}` },
        },
      },
      scales: {
        x: { grid: { display: false }, border: { color: th.axis }, ticks: { color: th.muted, maxRotation: 0, autoSkipPadding: 12 } },
        y: { grid: { color: th.grid }, border: { display: false }, ticks: { color: th.muted, callback: v => fmtShort(v), maxTicksLimit: 5 } },
      },
    };
  }
  const crosshair = {
    id: 'crosshair',
    afterDatasetsDraw(chart) {
      const act = chart.tooltip?.getActiveElements?.();
      if (!act || !act.length) return;
      const x = act[0].element.x, { top, bottom } = chart.chartArea, ctx = chart.ctx;
      ctx.save(); ctx.strokeStyle = css('--axis'); ctx.lineWidth = 1; ctx.setLineDash([3, 3]);
      ctx.beginPath(); ctx.moveTo(x, top); ctx.lineTo(x, bottom); ctx.stroke(); ctx.restore();
    },
  };
  function setChart(key, canvas, config) {
    if (charts[key]) charts[key].destroy();
    charts[key] = new Chart(canvas, config);
  }

  function renderDashboard() {
    const has = db.txs.length > 0;
    $('#empty-state').classList.toggle('hidden', has);
    $('#dashboard').classList.toggle('hidden', !has);
    if (!has) return;
    if (typeof Chart === 'undefined') { toast('Sem internet: os gráficos não carregaram.'); }

    // filtros
    $$('#period-chips button').forEach(b => b.classList.toggle('active', !ui.monthPick && b.dataset.period === ui.period));
    const bf = $('#bank-filter');
    bf.innerHTML = `<option value="">Todas as contas</option>` + db.banks.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');
    bf.value = ui.bank;
    bf.classList.toggle('hidden', db.banks.length < 2);

    const r = getRange();
    const all = scoped();
    const txs = all.filter(t => inRange(t, r));
    // só compara se havia dados cobrindo o período anterior inteiro
    const prevCovered = r.prev && dataBounds(all).min <= addDays(r.prev.from, 7);
    const prevTxs = prevCovered ? all.filter(t => inRange(t, r.prev)) : null;
    const T = totals(txs), P = prevTxs && prevTxs.length ? totals(prevTxs) : null;

    const lbl = `${shortDate(r.from)} a ${shortDate(r.to)} · ${txs.length} lançamento${txs.length === 1 ? '' : 's'}`;
    $('#period-label').innerHTML = ui.monthPick
      ? `Mostrando <b>${MES_LONGO[+ui.monthPick.slice(5) - 1]} de ${ui.monthPick.slice(0, 4)}</b> · ${txs.length} lançamentos · <button class="link" id="clear-month">ver período todo</button>`
      : esc(lbl);
    $('#clear-month')?.addEventListener('click', () => { ui.monthPick = null; renderDashboard(); });

    // patrimônio
    const initial = db.banks.filter(b => !ui.bank || b.id === ui.bank).reduce((s, b) => s + (+b.initial || 0), 0);
    const byDay = {};
    // aplicação em investimento só troca o dinheiro de lugar: não altera o patrimônio
    for (const t of all) if (t.cat !== 'invest') byDay[t.date] = (byDay[t.date] || 0) + t.amount;
    const { min } = dataBounds(all);
    let bal = initial;
    const series = [];
    let startBal = null;
    for (let d = min; d <= r.to; d = addDays(d, 1)) {
      if (d === r.from || (startBal === null && d > r.from)) startBal = bal;
      bal += byDay[d] || 0;
      if (d >= r.from) series.push({ d, v: Math.round(bal * 100) / 100 });
    }
    if (startBal === null) startBal = bal;
    const patrimonio = bal;

    // KPIs
    const delta = (cur, prev, upIsGood) => {
      if (!P || !prev) return `<div class="delta">&nbsp;</div>`;
      const ch = (cur - prev) / prev;
      if (!isFinite(ch)) return `<div class="delta">&nbsp;</div>`;
      const good = upIsGood ? ch >= 0 : ch <= 0;
      return `<div class="delta ${Math.abs(ch) < 0.005 ? '' : good ? 'good' : 'bad'}">${ch >= 0 ? '▲' : '▼'} ${pct(Math.abs(ch))} vs. período anterior</div>`;
    };
    const savingRate = T.inc > 0 ? T.net / T.inc : null;
    $('#kpis').innerHTML = `
      <div class="kpi"><div class="label">Entradas</div><div class="value">${fmt(T.inc)}</div>${delta(T.inc, P?.inc, true)}</div>
      <div class="kpi"><div class="label">Saídas</div><div class="value">${fmt(T.out)}</div>${delta(T.out, P?.out, false)}</div>
      <div class="kpi"><div class="label">Sobrou no período</div><div class="value" style="color:${T.net >= 0 ? 'var(--pos)' : 'var(--neg)'}">${fmt(T.net)}</div>
        <div class="delta">${savingRate !== null ? (savingRate >= 0 ? `Você guardou ${pct(savingRate)} do que entrou` : `Gastou ${pct(-savingRate)} a mais do que entrou`) : '&nbsp;'}</div></div>
      <div class="kpi"><div class="label">Patrimônio</div><div class="value">${fmt(patrimonio)}</div>
        <div class="delta ${patrimonio - startBal >= 0 ? 'good' : 'bad'}">${patrimonio - startBal >= 0 ? '▲' : '▼'} ${fmt(Math.abs(patrimonio - startBal))} no período</div></div>`;
    $('#patrimonio-now').textContent = fmt(patrimonio);

    // gasto por dia da semana
    const wk = [0, 0, 0, 0, 0, 0, 0], wkN = [0, 0, 0, 0, 0, 0, 0];
    const effFrom = (min > r.from) ? min : r.from;
    for (let d = effFrom; d <= r.to; d = addDays(d, 1)) wkN[pd(d).getDay()]++;
    for (const t of txs) if (t.amount < 0 && !isNeutral(t)) wk[pd(t.date).getDay()] -= t.amount;

    const th = chartTheme();
    if (typeof Chart !== 'undefined') {
      // linha do patrimônio
      const ctx = $('#chart-patrimonio').getContext('2d');
      const grad = ctx.createLinearGradient(0, 0, 0, 260);
      grad.addColorStop(0, th.s1soft); grad.addColorStop(1, 'rgba(0,0,0,0)');
      const o = baseOpts(th);
      o.interaction = { mode: 'index', intersect: false };
      o.plugins.tooltip.callbacks.title = items => dayLabel(series[items[0].dataIndex].d);
      o.plugins.tooltip.callbacks.label = c => ` Patrimônio: ${fmt(c.parsed.y)}`;
      o.plugins.tooltip.displayColors = false;
      o.scales.x.ticks.maxTicksLimit = 6;
      setChart('pat', ctx, {
        type: 'line',
        data: {
          labels: series.map(p => { const d = pd(p.d); return `${d.getDate()} ${MES[d.getMonth()]}`; }),
          datasets: [{ label: 'Patrimônio', data: series.map(p => p.v), borderColor: th.s1, backgroundColor: grad, fill: true, borderWidth: 2, pointRadius: 0, pointHoverRadius: 5, pointHoverBackgroundColor: th.s1, pointHoverBorderColor: th.surface, pointHoverBorderWidth: 2, tension: 0.25 }],
        },
        options: o, plugins: [crosshair],
      });

      // entradas x saídas por mês
      const months = r.months === 1 && !r.picked ? monthsIn({ from: r.prev ? r.prev.from : r.from, to: r.to }) : monthsIn(r);
      const mInc = {}, mOut = {};
      for (const t of all) {
        if (isNeutral(t)) continue;
        const k = t.date.slice(0, 7);
        if (t.amount > 0) mInc[k] = (mInc[k] || 0) + t.amount; else mOut[k] = (mOut[k] || 0) - t.amount;
      }
      const mo = baseOpts(th);
      mo.interaction = { mode: 'index', intersect: false };
      mo.plugins.tooltip.callbacks.title = items => { const k = months[items[0].dataIndex]; return `${MES_LONGO[+k.slice(5) - 1]} de ${k.slice(0, 4)}`; };
      mo.plugins.tooltip.callbacks.footer = items => { const k = months[items[0].dataIndex]; const n = (mInc[k] || 0) - (mOut[k] || 0); return `Saldo do mês: ${fmt(n)}`; };
      mo.onClick = (e, els, chart) => {
        const pts = chart.getElementsAtEventForMode(e, 'index', { intersect: false }, false);
        if (!pts.length) return;
        const k = months[pts[0].index];
        ui.monthPick = ui.monthPick === k ? null : k;
        renderDashboard();
        $('#dashboard').scrollIntoView({ behavior: 'smooth' });
      };
      mo.onHover = (e, els) => { e.native.target.style.cursor = els.length ? 'pointer' : 'default'; };
      const dim = (c, k) => (!ui.monthPick || ui.monthPick === k) ? c : c + '55';
      setChart('meses', $('#chart-meses'), {
        type: 'bar',
        data: {
          labels: months.map(monthLabel),
          datasets: [
            { label: 'Entradas', data: months.map(k => mInc[k] || 0), backgroundColor: months.map(k => dim(th.s1, k)), borderRadius: 4, borderSkipped: 'start', maxBarThickness: 22, categoryPercentage: 0.7, barPercentage: 0.9 },
            { label: 'Saídas', data: months.map(k => mOut[k] || 0), backgroundColor: months.map(k => dim(th.s2, k)), borderRadius: 4, borderSkipped: 'start', maxBarThickness: 22, categoryPercentage: 0.7, barPercentage: 0.9 },
          ],
        },
        options: mo,
      });

      const order = [1, 2, 3, 4, 5, 6, 0];
      const wo = baseOpts(th);
      wo.plugins.tooltip.displayColors = false;
      wo.plugins.tooltip.callbacks.title = items => SEMANA_LONGO[order[items[0].dataIndex]];
      wo.plugins.tooltip.callbacks.label = c => ` Média: ${fmt(c.parsed.y)} por dia`;
      setChart('semana', $('#chart-semana'), {
        type: 'bar',
        data: { labels: order.map(i => SEMANA[i]), datasets: [{ data: order.map(i => wkN[i] ? wk[i] / wkN[i] : 0), backgroundColor: th.s2, borderRadius: 4, borderSkipped: 'start', maxBarThickness: 28 }] },
        options: wo,
      });
    }

    // categorias
    const catTot = {};
    for (const t of txs) if (t.amount < 0 && !isNeutral(t)) catTot[t.cat] = (catTot[t.cat] || 0) - t.amount;
    const cats = Object.entries(catTot).sort((a, b) => b[1] - a[1]);
    const maxCat = cats[0]?.[1] || 1;
    $('#cat-bars').innerHTML = cats.length ? cats.map(([id, v]) => `
      <button class="hbar" data-cat="${id}">
        <span class="ic">${CAT[id]?.ic || '•'}</span>
        <span class="name">${esc(CAT[id]?.name || id)}</span>
        <span class="val">${fmt(v)}<span class="pct">${pct(v / T.out)}</span></span>
        <span class="track"><span class="fill" style="width:${Math.max(2, v / maxCat * 100)}%"></span></span>
      </button>`).join('') : `<p class="empty-note">Nenhum gasto neste período.</p>`;
    $$('#cat-bars .hbar').forEach(b => b.addEventListener('click', () => openDrill(b.dataset.cat)));

    // estabelecimentos
    const mer = {};
    for (const t of txs) {
      if (t.amount >= 0 || isNeutral(t)) continue;
      const k = merchant(t.desc);
      (mer[k] ||= { v: 0, n: 0, cat: t.cat }).v -= t.amount; mer[k].n++;
    }
    const topM = Object.entries(mer).sort((a, b) => b[1].v - a[1].v).slice(0, 8);
    $('#top-merchants').innerHTML = topM.length ? topM.map(([k, m]) => `
      <li data-merchant="${esc(k)}"><div class="grow"><div>${esc(k)}</div><div class="meta">${CAT[m.cat]?.ic || ''} ${m.n} compra${m.n > 1 ? 's' : ''} · média ${fmt(m.v / m.n)}</div></div><span class="v">${fmt(m.v)}</span></li>`).join('')
      : `<p class="empty-note">Nada por aqui ainda.</p>`;
    $$('#top-merchants li').forEach(li => li.addEventListener('click', () => {
      ui.list = { ...ui.list, merchant: li.dataset.merchant, q: '', cat: '', type: '', month: null, limit: 120 };
      go('lancamentos');
    }));

    // recorrentes
    const rec = findRecurring(all);
    const recTotal = rec.reduce((s, x) => s + x.avg, 0);
    $('#recurring').innerHTML = rec.length ? rec.slice(0, 8).map(x => `
      <li data-merchant="${esc(x.name)}"><span class="ic">${CAT[x.cat]?.ic || '🔁'}</span><div class="grow"><div>${esc(x.name)}</div><div class="meta">${x.months} meses seguidos · todo dia ~${x.day}</div></div><span class="v">${fmt(x.avg)}/mês</span></li>`).join('')
      + (rec.length ? `<li class="meta" style="cursor:default">Total estimado: <b>&nbsp;${fmt(recTotal)}/mês</b>&nbsp;(${fmt(recTotal * 12)} por ano)</li>` : '')
      : `<p class="empty-note">Com pelo menos 2 meses de dados eu encontro cobranças que se repetem.</p>`;
    $$('#recurring li[data-merchant]').forEach(li => li.addEventListener('click', () => {
      ui.list = { ...ui.list, merchant: li.dataset.merchant, q: '', cat: '', type: '', month: null, limit: 120 };
      go('lancamentos');
    }));

    // por conta
    const banks = db.banks.filter(b => !ui.bank || b.id === ui.bank);
    $('#by-bank').innerHTML = banks.map(b => {
      const bt = db.txs.filter(t => t.bank === b.id);
      const now = (+b.initial || 0) + bt.reduce((s, t) => s + t.amount, 0);
      const tt = totals(bt.filter(t => inRange(t, r)));
      return `<div class="bank-row"><span class="dot" style="background:${bankColor(b.id)}"></span><b>${esc(b.name)}</b><span class="num">${fmt(now)}</span>
        <span class="flow">entrou ${fmt(tt.inc)} · saiu ${fmt(tt.out)} no período</span></div>`;
    }).join('') || `<p class="empty-note">Nenhuma conta.</p>`;

    // insights
    renderInsights({ r, txs, T, P, cats, rec, recTotal, wk });
  }

  function findRecurring(all) {
    const g = {};
    for (const t of all) {
      if (t.amount >= 0 || isNeutral(t)) continue;
      const k = merchant(t.desc);
      (g[k] ||= []).push(t);
    }
    const out = [];
    for (const [name, list] of Object.entries(g)) {
      const byMonth = {};
      list.forEach(t => { const m = t.date.slice(0, 7); (byMonth[m] ||= []).push(t); });
      const ms = Object.keys(byMonth).sort();
      if (ms.length < 2) continue;
      if (list.length > ms.length * 1.5) continue; // compra frequente, não cobrança fixa
      const vals = ms.map(m => -byMonth[m].reduce((s, t) => s + t.amount, 0));
      const mn = Math.min(...vals), mx = Math.max(...vals);
      if (mx / mn > 1.3) continue;
      // meses consecutivos até o último
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

  function renderInsights({ r, txs, T, P, cats, rec, recTotal, wk }) {
    const items = [];
    if (cats.length) {
      const [id, v] = cats[0];
      items.push(['🎯', `Sua maior categoria de gasto é <b>${CAT[id].name}</b>: ${fmt(v)}, ou <b>${pct(v / T.out)}</b> de tudo que saiu.`]);
    }
    if (P && P.out > 0 && T.out > 0 && !(r.months == null)) {
      const ch = (T.out - P.out) / P.out;
      if (Math.abs(ch) >= 0.03) items.push([ch > 0 ? '📈' : '📉', `Você gastou <b>${pct(Math.abs(ch))} ${ch > 0 ? 'a mais' : 'a menos'}</b> que no período anterior (${fmt(P.out)} → ${fmt(T.out)}).`]);
    }
    if (T.out > 0) {
      const { min } = dataBounds(scoped());
      const from = min > r.from ? min : r.from;
      const days = Math.max(1, daysBetween(from, r.to));
      items.push(['📅', `Em média você gasta <b>${fmt(T.out / days)} por dia</b>, cerca de ${fmt(T.out / days * 30)} por mês.`]);
    }
    const biggest = txs.filter(t => t.amount < 0 && !isNeutral(t)).sort((a, b) => a.amount - b.amount)[0];
    if (biggest) items.push(['💸', `Maior gasto único: <b>${fmt(-biggest.amount)}</b> em ${esc(merchant(biggest.desc))} (${shortDate(biggest.date)}).`]);
    if (rec.length) items.push(['🔁', `Encontrei <b>${rec.length} cobrança${rec.length > 1 ? 's' : ''} fixa${rec.length > 1 ? 's' : ''}</b> somando ${fmt(recTotal)} por mês — ${fmt(recTotal * 12)} por ano.`]);
    const wkMax = wk.indexOf(Math.max(...wk));
    if (T.out > 0 && wk[wkMax] > 0) items.push(['🗓️', `<b>${SEMANA_LONGO[wkMax][0].toUpperCase() + SEMANA_LONGO[wkMax].slice(1)}</b> é o dia em que mais sai dinheiro (${pct(wk[wkMax] / T.out)} dos gastos).`]);
    const fees = txs.filter(t => t.cat === 'taxas').reduce((s, t) => s - t.amount, 0);
    if (fees > 0) items.push(['🏦', `Você pagou <b>${fmt(fees)}</b> em tarifas, juros e IOF neste período.`]);
    const food = txs.filter(t => t.cat === 'alimentacao' && t.amount < 0);
    const delivery = food.filter(t => /ifood|rappi|delivery|ze /i.test(norm(t.desc)));
    if (delivery.length >= 3) items.push(['🛵', `Foram <b>${delivery.length} pedidos de delivery</b>, total de ${fmt(-delivery.reduce((s, t) => s + t.amount, 0))}.`]);
    if (T.inc > 0 && T.net < 0) items.push(['⚠️', `Saiu <b>${fmt(-T.net)}</b> a mais do que entrou neste período.`]);
    else if (T.inc > 0 && T.net / T.inc >= 0.2) items.push(['✅', `Ótimo: você guardou <b>${pct(T.net / T.inc)}</b> do que entrou.`]);
    $('#insights').innerHTML = items.slice(0, 8).map(([ic, h]) => `<li><span class="ic">${ic}</span><span>${h}</span></li>`).join('') || `<li>Registre alguns gastos para ver os destaques.</li>`;
  }

  // filtros do painel
  $('#period-chips').addEventListener('click', e => {
    const b = e.target.closest('button[data-period]'); if (!b) return;
    ui.period = b.dataset.period; ui.monthPick = null;
    try { localStorage.setItem(STORE_KEY + ':period', ui.period); } catch (err) { /* */ }
    renderDashboard();
  });
  $('#bank-filter').addEventListener('change', e => { ui.bank = e.target.value; renderDashboard(); });
  $('#load-demo').addEventListener('click', () => { loadDemo(); toast('Dados de exemplo carregados. Apague em Ajustes quando quiser.'); renderDashboard(); });

  /* ============ detalhe de categoria ============ */
  function openDrill(catId) {
    const r = getRange();
    const all = scoped().filter(t => t.cat === catId && !isNeutral(t) && t.amount < 0);
    const list = all.filter(t => inRange(t, r)).sort((a, b) => (b.date + (b.time || '')).localeCompare(a.date + (a.time || '')));
    const total = -list.reduce((s, t) => s + t.amount, 0);
    const T = totals(scoped().filter(t => inRange(t, r)));
    $('#drill-title').textContent = `${CAT[catId].ic} ${CAT[catId].name}`;
    $('#drill-sub').textContent = `${fmt(total)} · ${pct(T.out ? total / T.out : 0)} dos gastos · ${list.length} lançamento${list.length === 1 ? '' : 's'}`;
    const months = monthsIn(r.months === 1 && !r.picked && r.prev ? { from: r.prev.from, to: r.to } : r);
    const per = {}; all.forEach(t => { const k = t.date.slice(0, 7); per[k] = (per[k] || 0) - t.amount; });
    $('#drill-sheet').classList.remove('hidden'); $('#drill-backdrop').classList.remove('hidden');
    if (typeof Chart !== 'undefined') {
      const th = chartTheme(); const o = baseOpts(th);
      o.plugins.tooltip.displayColors = false;
      o.plugins.tooltip.callbacks.title = items => { const k = months[items[0].dataIndex]; return `${MES_LONGO[+k.slice(5) - 1]} de ${k.slice(0, 4)}`; };
      setChart('drill', $('#chart-drill'), { type: 'bar', data: { labels: months.map(monthLabel), datasets: [{ label: CAT[catId].name, data: months.map(k => per[k] || 0), backgroundColor: th.s2, borderRadius: 4, borderSkipped: 'start', maxBarThickness: 26 }] }, options: o });
    }
    $('#drill-list').innerHTML = txRows(list.slice(0, 60));
    bindTxRows($('#drill-list'));
  }
  const closeDrill = () => { $('#drill-sheet').classList.add('hidden'); $('#drill-backdrop').classList.add('hidden'); };
  $('#drill-close').addEventListener('click', closeDrill);
  $('#drill-backdrop').addEventListener('click', closeDrill);

  /* ============ lista de lançamentos ============ */
  function txRows(list, grouped) {
    let html = '', lastDay = null;
    const dayTot = {};
    if (grouped) list.forEach(t => { dayTot[t.date] = (dayTot[t.date] || 0) + (isNeutral(t) ? 0 : t.amount); });
    for (const t of list) {
      if (grouped && t.date !== lastDay) {
        lastDay = t.date;
        html += `<div class="day-head"><span>${dayLabel(t.date)}</span><span class="num">${fmt(dayTot[t.date])}</span></div>`;
      }
      const c = CAT[t.cat] || CAT.outros; const b = bankById(t.bank);
      html += `<div class="tx" data-id="${t.id}" tabindex="0" role="button">
        <span class="ic">${c.ic}</span>
        <div style="min-width:0"><div class="d">${esc(t.desc)}</div>
        <div class="m">${grouped ? '' : shortDate(t.date) + ' · '}${t.time ? t.time + ' · ' : ''}${esc(c.name)}${b ? ' · ' + esc(b.name) : ''}${t.source === 'manual' ? ' · manual' : ''}</div></div>
        <span class="a ${t.amount > 0 ? 'in' : ''}">${t.amount > 0 ? '+' : ''}${fmt(t.amount)}</span></div>`;
    }
    return html || `<p class="empty-note" style="padding:16px">Nenhum lançamento encontrado.</p>`;
  }
  function bindTxRows(root) {
    root.querySelectorAll('.tx').forEach(el => {
      const open = () => { closeDrill(); openTxSheet(db.txs.find(t => t.id === el.dataset.id)); };
      el.addEventListener('click', open);
      el.addEventListener('keydown', e => { if (e.key === 'Enter') open(); });
    });
  }
  function renderList() {
    const L = ui.list;
    const fc = $('#f-cat');
    fc.innerHTML = `<option value="">Todas as categorias</option>` + CATS.map(c => `<option value="${c.id}">${c.ic} ${c.name}</option>`).join('');
    fc.value = L.cat; $('#f-type').value = L.type; $('#search').value = L.q;
    const q = norm(L.q);
    const list = scoped().filter(t =>
      (!L.cat || t.cat === L.cat) &&
      (!L.type || (L.type === 'in' ? t.amount > 0 : t.amount < 0)) &&
      (!L.merchant || merchant(t.desc) === L.merchant) &&
      (!q || norm(t.desc).includes(q) || norm(CAT[t.cat]?.name).includes(q) || String(Math.abs(t.amount)).includes(q.replace(',', '.')))
    ).sort((a, b) => (b.date + (b.time || '')).localeCompare(a.date + (a.time || '')));
    const af = $('#active-filter');
    af.classList.toggle('hidden', !L.merchant && !ui.bank);
    af.innerHTML = [L.merchant && `<span class="pill">${esc(L.merchant)} <button data-clear="merchant" aria-label="Limpar">×</button></span>`,
      ui.bank && `<span class="pill">${esc(bankById(ui.bank)?.name)} <button data-clear="bank" aria-label="Limpar">×</button></span>`].filter(Boolean).join('');
    af.querySelectorAll('[data-clear]').forEach(b => b.addEventListener('click', () => {
      if (b.dataset.clear === 'merchant') L.merchant = null; else ui.bank = '';
      renderList();
    }));
    const T = totals(list);
    $('#list-summary').textContent = `${list.length} lançamento${list.length === 1 ? '' : 's'} · entradas ${fmt(T.inc)} · saídas ${fmt(T.out)}`;
    const box = $('#tx-list');
    box.innerHTML = txRows(list.slice(0, L.limit), true) + (list.length > L.limit ? `<button class="load-more" id="load-more">Mostrar mais (${list.length - L.limit} restantes)</button>` : '');
    bindTxRows(box);
    $('#load-more')?.addEventListener('click', () => { L.limit += 200; renderList(); });
  }
  let searchT;
  $('#search').addEventListener('input', e => { clearTimeout(searchT); searchT = setTimeout(() => { ui.list.q = e.target.value; ui.list.limit = 120; renderList(); }, 150); });
  $('#f-cat').addEventListener('change', e => { ui.list.cat = e.target.value; renderList(); });
  $('#f-type').addEventListener('change', e => { ui.list.type = e.target.value; renderList(); });

  /* ============ novo lançamento / edição ============ */
  const sheet = { editing: null, type: 'out', cat: null, catTouched: false, autoWhen: true };
  function openTxSheet(tx) {
    closeDrill();
    sheet.editing = tx || null;
    sheet.type = tx ? (tx.amount > 0 ? 'in' : 'out') : 'out';
    sheet.cat = tx ? tx.cat : null;
    sheet.catTouched = !!tx;
    sheet.autoWhen = !tx;
    $('#sheet-title').textContent = tx ? 'Editar lançamento' : 'Novo lançamento';
    $('#tx-amount').value = tx ? Math.abs(tx.amount).toFixed(2).replace('.', ',') : '';
    $('#tx-desc').value = tx ? tx.desc : '';
    $('#tx-date').value = tx ? tx.date : todayStr();
    $('#tx-time').value = tx ? (tx.time || '') : nowTime();
    $('#when-inputs').classList.add('hidden');
    $('#tx-delete').classList.toggle('hidden', !tx);
    const sel = $('#tx-bank');
    const banks = db.banks.length ? db.banks : [{ id: '__new', name: 'Carteira (dinheiro)' }];
    sel.innerHTML = banks.map(b => `<option value="${b.id}">${esc(b.name)}</option>`).join('');
    let lastManual = null;
    try { lastManual = localStorage.getItem(STORE_KEY + ':lastBank'); } catch (e) { /* */ }
    sel.value = tx ? tx.bank : (banks.some(b => b.id === lastManual) ? lastManual : banks[0].id);
    syncSheet();
    $('#tx-sheet').classList.remove('hidden'); $('#sheet-backdrop').classList.remove('hidden');
    setTimeout(() => $('#tx-amount').focus(), 60);
  }
  function syncSheet() {
    $$('#tx-sheet .seg button').forEach(b => b.classList.toggle('active', b.dataset.type === sheet.type));
    if (!sheet.catTouched) {
      const d = $('#tx-desc').value;
      sheet.cat = d.trim() ? guessCat(d, sheet.type === 'in' ? 1 : -1) : (sheet.type === 'in' ? 'receitas' : null);
    }
    const options = CATS.filter(c => sheet.type === 'in' ? (c.income || c.neutral || c.id === 'pessoas') : !c.income);
    $('#cat-picker').innerHTML = options.map(c => `<button type="button" data-cat="${c.id}" class="${sheet.cat === c.id ? 'active' : ''}">${c.ic} ${c.name}</button>`).join('');
    updateWhen();
  }
  function updateWhen() {
    const d = $('#tx-date').value, t = $('#tx-time').value;
    $('#when-text').textContent = sheet.autoWhen ? `${dayLabel(todayStr())}, ${nowTime()} — registrado automaticamente` : `${d ? dayLabel(d) : ''}${t ? ', ' + t : ''}`;
  }
  $('#tx-sheet .seg').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; sheet.type = b.dataset.type; if (sheet.cat && (CAT[sheet.cat].income !== (sheet.type === 'in'))) sheet.catTouched = false; syncSheet(); });
  $('#cat-picker').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; sheet.cat = b.dataset.cat; sheet.catTouched = true; syncSheet(); });
  $('#tx-desc').addEventListener('input', () => { if (!sheet.catTouched) syncSheet(); });
  $('#when-edit').addEventListener('click', () => { sheet.autoWhen = false; $('#when-inputs').classList.toggle('hidden'); updateWhen(); });
  $('#tx-date').addEventListener('input', updateWhen); $('#tx-time').addEventListener('input', updateWhen);
  const closeSheet = () => { $('#tx-sheet').classList.add('hidden'); $('#sheet-backdrop').classList.add('hidden'); };
  $('#tx-cancel').addEventListener('click', closeSheet);
  $('#sheet-backdrop').addEventListener('click', closeSheet);
  document.addEventListener('keydown', e => { if (e.key === 'Escape') { closeSheet(); closeDrill(); } });
  $('#tx-delete').addEventListener('click', () => {
    if (!sheet.editing || !confirm('Excluir este lançamento?')) return;
    db.txs = db.txs.filter(t => t.id !== sheet.editing.id); save(); closeSheet(); render(); toast('Lançamento excluído.');
  });
  $('#tx-sheet').addEventListener('submit', e => {
    e.preventDefault();
    const raw = $('#tx-amount').value.trim();
    const v = Math.abs(Parsers.parseAmount(raw));
    if (!v || !isFinite(v)) { $('#tx-amount').focus(); toast('Digite o valor.'); return; }
    const amount = sheet.type === 'in' ? v : -v;
    const desc = $('#tx-desc').value.trim() || (CAT[sheet.cat]?.name ?? (sheet.type === 'in' ? 'Entrada' : 'Gasto'));
    let bankId = $('#tx-bank').value;
    if (bankId === '__new') bankId = ensureBank('Carteira (dinheiro)').id;
    try { localStorage.setItem(STORE_KEY + ':lastBank', bankId); } catch (err) { /* */ }
    const cat = sheet.cat || guessCat(desc, amount);
    const when = sheet.autoWhen ? { date: todayStr(), time: nowTime() } : { date: $('#tx-date').value || todayStr(), time: $('#tx-time').value || null };
    if (sheet.editing) {
      const t = db.txs.find(x => x.id === sheet.editing.id);
      const catChanged = t.cat !== cat;
      Object.assign(t, { amount, desc, bank: bankId, cat, ...when });
      if (catChanged) {
        const key = merchant(desc);
        const others = db.txs.filter(x => x.id !== t.id && merchant(x.desc) === key && x.cat !== cat && Math.sign(x.amount) === Math.sign(amount));
        db.rules[norm(key)] = cat;
        if (others.length && confirm(`Mudar também os outros ${others.length} lançamento(s) de “${key}” para ${CAT[cat].name}?`)) others.forEach(x => { x.cat = cat; });
      }
      toast('Lançamento atualizado.');
    } else {
      db.txs.push({ id: uid(), date: when.date, time: when.time, amount, desc, cat, bank: bankId, source: 'manual', createdAt: new Date().toISOString() });
      toast(`${sheet.type === 'in' ? 'Entrada' : 'Gasto'} de ${fmt(v)} registrado às ${when.time || ''}.`);
    }
    save(); closeSheet(); render();
  });

  /* ============ importar ============ */
  let slots = null;
  function initSlots() {
    const named = db.banks.filter(b => !/carteira/i.test(b.name));
    slots = (named.length ? named : [{ name: '' }]).map(b => ({ name: b.name, files: [] }));
  }
  function renderImport() {
    if (!slots) initSlots();
    $('#import-step1').classList.remove('hidden'); $('#import-step2').classList.add('hidden');
    drawSlots();
  }
  function drawSlots() {
    $('#bank-count').textContent = slots.length;
    const pal = BANK_COLORS[isDark() ? 'dark' : 'light'];
    $('#bank-slots').innerHTML = slots.map((s, i) => `
      <div class="slot" data-i="${i}">
        <div class="slot-head"><span class="n" style="background:${pal[i % pal.length]}">${i + 1}</span>
          <input class="slot-name" list="bank-names" placeholder="Nome do banco (ex.: Nubank)" value="${esc(s.name)}" aria-label="Nome do banco ${i + 1}"></div>
        <label class="drop"><input type="file" multiple accept=".pdf,.ofx,.qfx,.csv,.txt,.xls,.xlsx,.ods,application/pdf,text/csv,text/plain" hidden>
          <span>📄</span><span><strong>Toque para escolher os arquivos</strong></span><span class="sub">PDF, OFX, CSV, TXT ou Excel · pode escolher vários</span></label>
        <ul class="files">${s.files.map((f, j) => `<li><span>${esc(f.name)}</span><em>${(f.size / 1024).toFixed(0)} KB</em><button data-rm="${j}" aria-label="Remover">×</button></li>`).join('')}</ul>
      </div>`).join('') + `<datalist id="bank-names">${COMMON_BANKS.map(b => `<option value="${b}">`).join('')}</datalist>`;
    $$('#bank-slots .slot').forEach(el => {
      const s = slots[+el.dataset.i];
      el.querySelector('.slot-name').addEventListener('input', e => { s.name = e.target.value; updateReadBtn(); });
      const input = el.querySelector('input[type=file]');
      input.addEventListener('change', () => { addFiles(s, input.files); input.value = ''; });
      const drop = el.querySelector('.drop');
      drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('over'); });
      drop.addEventListener('dragleave', () => drop.classList.remove('over'));
      drop.addEventListener('drop', e => { e.preventDefault(); drop.classList.remove('over'); addFiles(s, e.dataTransfer.files); });
      el.querySelectorAll('[data-rm]').forEach(b => b.addEventListener('click', () => { s.files.splice(+b.dataset.rm, 1); drawSlots(); }));
    });
    updateReadBtn();
  }
  function addFiles(slot, files) {
    for (const f of files) if (!slot.files.some(x => x.name === f.name && x.size === f.size)) slot.files.push(f);
    drawSlots();
  }
  function updateReadBtn() {
    const n = slots.reduce((s, x) => s + x.files.length, 0);
    $('#files-total').textContent = n ? `${n} arquivo${n > 1 ? 's' : ''} de ${slots.filter(s => s.files.length).length} banco${slots.filter(s => s.files.length).length > 1 ? 's' : ''} pronto${n > 1 ? 's' : ''} para ler.` : 'Escolha ao menos um arquivo.';
    $('#read-files').disabled = !n;
  }
  $('#bank-plus').addEventListener('click', () => { if (slots.length < 12) { slots.push({ name: '', files: [] }); drawSlots(); } });
  $('#bank-minus').addEventListener('click', () => {
    if (slots.length <= 1) return;
    const last = slots[slots.length - 1];
    if (last.files.length && !confirm(`Remover o banco ${slots.length} e seus ${last.files.length} arquivo(s)?`)) return;
    slots.pop(); drawSlots();
  });

  let review = [];
  $('#read-files').addEventListener('click', async () => {
    const btn = $('#read-files');
    btn.disabled = true;
    review = [];
    const existing = {};
    const keyOf = (bankName, t) => `${norm(bankName)}|${t.date}|${t.amount.toFixed(2)}|${norm(merchant(t.desc))}`;
    for (const t of db.txs) { const k = keyOf(bankById(t.bank)?.name, t); existing[k] = (existing[k] || 0) + 1; }
    const total = slots.reduce((s, x) => s + x.files.length, 0);
    let done = 0;
    for (const [i, s] of slots.entries()) {
      const bankName = s.name.trim() || `Banco ${i + 1}`;
      for (const f of s.files) {
        btn.textContent = `Lendo ${++done} de ${total}…`;
        const entry = { bankName, slot: i, file: f.name, kind: '', txs: [], error: null, balance: null };
        try {
          const res = await Parsers.parseFile(f, { askPassword: async (name, retry) => prompt(`${retry ? 'Senha incorreta. ' : ''}O PDF “${name}” tem senha (em muitos bancos é parte do CPF). Digite a senha:`) });
          entry.kind = res.kind; entry.balance = res.balance;
          const seen = {};
          entry.txs = res.transactions.map(t => {
            const tt = { ...t, desc: t.description, cat: guessCat(t.description, t.amount) };
            const k = keyOf(bankName, tt);
            seen[k] = (seen[k] || 0) + 1;
            tt.dup = (existing[k] || 0) >= seen[k];
            tt.include = !tt.dup;
            return tt;
          });
          for (const [k, n] of Object.entries(seen)) existing[k] = Math.max(existing[k] || 0, n);
          if (!entry.txs.length) entry.error = 'Não encontrei lançamentos neste arquivo. Se for um PDF, confira se é o extrato (e não um comprovante). Tente exportar em OFX ou CSV pelo app do banco.';
        } catch (e) {
          entry.error = e.message || String(e);
        }
        review.push(entry);
      }
    }
    btn.textContent = 'Ler extratos'; btn.disabled = false;
    drawReview();
  });

  function drawReview() {
    $('#import-step1').classList.add('hidden'); $('#import-step2').classList.remove('hidden');
    window.scrollTo({ top: 0 });
    const catOpts = sel => CATS.map(c => `<option value="${c.id}" ${c.id === sel ? 'selected' : ''}>${c.ic} ${c.name}</option>`).join('');
    $('#review-files').innerHTML = review.map((f, fi) => {
      const on = f.txs.filter(t => t.include);
      const T = totals(on);
      const dups = f.txs.filter(t => t.dup).length;
      return `<div class="card review-file" data-f="${fi}">
        <div class="card-head"><div><h2>${esc(f.bankName)} · ${esc(f.file)}</h2>
          <p class="sub">${f.error ? '' : `${f.kind} · ${f.txs.length} lançamentos encontrados · entradas ${fmt(T.inc)} · saídas ${fmt(T.out)}${dups ? ` · ${dups} já existiam (desmarcados)` : ''}`}</p></div>
          ${f.error ? '' : `<div class="row-btns" style="margin:0"><button class="btn small" data-flip="${fi}">Inverter sinais</button><button class="btn small" data-all="${fi}">${on.length === f.txs.length ? 'Desmarcar todos' : 'Marcar todos'}</button></div>`}</div>
        ${f.error ? `<p class="err">⚠️ ${esc(f.error)}</p>` : `<div class="review-table">${f.txs.map((t, ti) => `
          <div class="rv ${t.include ? '' : 'off'}" data-t="${ti}">
            <input type="checkbox" ${t.include ? 'checked' : ''} aria-label="Importar">
            <span class="dt num">${shortDate(t.date)}</span>
            <span class="dsc" title="${esc(t.desc)}">${esc(t.desc)}${t.dup ? '<span class="dup">já importado antes</span>' : ''}</span>
            <select aria-label="Categoria">${catOpts(t.cat)}</select>
            <span class="amt ${t.amount > 0 ? 'in' : ''}">${t.amount > 0 ? '+' : ''}${fmt(t.amount)}</span>
          </div>`).join('')}</div>`}
      </div>`;
    }).join('');
    const n = review.reduce((s, f) => s + f.txs.filter(t => t.include).length, 0);
    $('#review-save').textContent = `Importar ${n} lançamento${n === 1 ? '' : 's'}`;
    $('#review-save').disabled = !n;
    $$('#review-files .review-file').forEach(el => {
      const f = review[+el.dataset.f];
      el.querySelectorAll('.rv').forEach(row => {
        const t = f.txs[+row.dataset.t];
        row.querySelector('input').addEventListener('change', e => { t.include = e.target.checked; drawReview(); });
        row.querySelector('select').addEventListener('change', e => { t.cat = e.target.value; });
      });
    });
    $$('[data-flip]').forEach(b => b.addEventListener('click', () => {
      const f = review[+b.dataset.flip];
      f.txs.forEach(t => { t.amount = -t.amount; t.cat = guessCat(t.desc, t.amount); });
      drawReview();
    }));
    $$('[data-all]').forEach(b => b.addEventListener('click', () => {
      const f = review[+b.dataset.all]; const all = f.txs.every(t => t.include);
      f.txs.forEach(t => { t.include = !all; }); drawReview();
    }));
  }
  $('#review-back').addEventListener('click', () => { $('#import-step2').classList.add('hidden'); $('#import-step1').classList.remove('hidden'); });
  $('#review-save').addEventListener('click', () => {
    let n = 0;
    for (const f of review) {
      const chosen = f.txs.filter(t => t.include);
      if (!chosen.length) continue;
      const bank = ensureBank(f.bankName);
      for (const t of chosen) {
        db.txs.push({ id: uid(), date: t.date, time: null, amount: t.amount, desc: t.desc, cat: t.cat, bank: bank.id, source: 'import', file: f.file, createdAt: new Date().toISOString() });
        n++;
      }
      // OFX traz o saldo final: usamos para acertar o saldo inicial da conta
      if (f.balance != null && !(+bank.initial)) {
        const sum = db.txs.filter(t => t.bank === bank.id).reduce((s, t) => s + t.amount, 0);
        bank.initial = Math.round((f.balance - sum) * 100) / 100;
      }
    }
    save();
    slots = null; review = [];
    toast(`${n} lançamentos importados.`);
    ui.monthPick = null;
    go('panorama');
  });

  /* ============ ajustes ============ */
  function renderSettings() {
    $('#banks-edit').innerHTML = db.banks.map(b => `
      <div class="bank-edit" data-id="${b.id}"><span class="dot" style="background:${bankColor(b.id)}"></span>
        <input class="bn" value="${esc(b.name)}" aria-label="Nome da conta">
        <input class="ib" inputmode="decimal" value="${(+b.initial || 0).toFixed(2).replace('.', ',')}" aria-label="Saldo inicial">
        <button class="btn small danger" data-del="${b.id}">Excluir</button></div>`).join('') || `<p class="empty-note">Nenhuma conta ainda. Elas aparecem quando você importa extratos ou registra um gasto.</p>`;
    $$('#banks-edit .bank-edit').forEach(row => {
      const b = bankById(row.dataset.id);
      row.querySelector('.bn').addEventListener('change', e => { b.name = e.target.value.trim() || b.name; save(); });
      row.querySelector('.ib').addEventListener('change', e => { const v = Parsers.parseAmount(e.target.value); b.initial = isNaN(v) ? 0 : v; save(); toast('Saldo inicial salvo.'); });
      row.querySelector('[data-del]').addEventListener('click', () => {
        const n = db.txs.filter(t => t.bank === b.id).length;
        if (!confirm(`Excluir a conta “${b.name}”${n ? ` e seus ${n} lançamentos` : ''}?`)) return;
        db.txs = db.txs.filter(t => t.bank !== b.id); db.banks = db.banks.filter(x => x.id !== b.id);
        if (ui.bank === b.id) ui.bank = '';
        save(); renderSettings();
      });
    });
  }
  $('#add-bank').addEventListener('click', () => {
    const name = prompt('Nome da conta (ex.: Nubank, Carteira):'); if (!name) return;
    ensureBank(name); save(); renderSettings();
  });
  function download(name, content, type) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(new Blob([content], { type }));
    a.download = name; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 2000);
  }
  $('#export-json').addEventListener('click', () => download(`financas-backup-${todayStr()}.json`, JSON.stringify(db, null, 1), 'application/json'));
  $('#export-csv').addEventListener('click', () => {
    const rows = [['Data', 'Hora', 'Descrição', 'Categoria', 'Conta', 'Valor', 'Origem']].concat(
      [...db.txs].sort((a, b) => a.date.localeCompare(b.date)).map(t => [t.date, t.time || '', t.desc, CAT[t.cat]?.name || t.cat, bankById(t.bank)?.name || '', t.amount.toFixed(2).replace('.', ','), t.source]));
    download(`financas-${todayStr()}.csv`, '﻿' + rows.map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(';')).join('\n'), 'text/csv');
  });
  $('#import-json').addEventListener('change', async e => {
    const f = e.target.files[0]; if (!f) return;
    try {
      const d = JSON.parse(await f.text());
      if (!Array.isArray(d.txs) || !Array.isArray(d.banks)) throw new Error();
      if (!confirm(`Restaurar backup com ${d.txs.length} lançamentos? Os dados atuais serão substituídos.`)) return;
      db = { version: 1, rules: {}, ...d }; save(); toast('Backup restaurado.'); render();
    } catch (err) { toast('Arquivo de backup inválido.'); }
    e.target.value = '';
  });
  $('#clear-all').addEventListener('click', () => {
    if (!confirm('Apagar todos os lançamentos e contas deste aparelho? Isso não pode ser desfeito.')) return;
    db = { version: 1, banks: [], txs: [], rules: {} }; save(); ui.bank = ''; slots = null; toast('Tudo apagado.'); go('panorama');
  });

  /* ============ dados de exemplo ============ */
  function loadDemo() {
    let seed = 7;
    const rnd = () => { seed |= 0; seed = seed + 0x6D2B79F5 | 0; let t = Math.imul(seed ^ seed >>> 15, 1 | seed); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; };
    const pick = a => a[Math.floor(rnd() * a.length)];
    const nu = ensureBank('Nubank'), it = ensureBank('Itaú'), cart = ensureBank('Carteira (dinheiro)');
    it.initial = 5200; nu.initial = 1450; cart.initial = 150;
    const add = (date, amount, desc, bank, time) => db.txs.push({ id: uid(), date, time: time || null, amount: Math.round(amount * 100) / 100, desc, cat: guessCat(desc, amount), bank: bank.id, source: time ? 'manual' : 'import', createdAt: new Date().toISOString() });
    const end = new Date(); const start = new Date(end.getFullYear(), end.getMonth() - 6, 1);
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const s = ds(d), day = d.getDate(), wd = d.getDay();
      if (day === 5) add(s, 7400, 'SALARIO EMPRESA XYZ LTDA', it);
      if (day === 20 && rnd() < 0.6) add(s, 450 + rnd() * 900, 'PIX RECEBIDO - FREELA DESIGN', nu);
      if (day === 6) add(s, -1850, 'PAGAMENTO ALUGUEL - IMOBILIARIA CENTRO', it);
      if (day === 6) add(s, -2000, 'APLICACAO CDB LIQUIDEZ', it);
      if (day === 10) add(s, -(160 + rnd() * 60), 'ENEL DISTRIBUICAO ENERGIA', it);
      if (day === 12) add(s, -99.9, 'VIVO FIBRA INTERNET', it);
      if (day === 8) add(s, -55.9, 'NETFLIX.COM', nu);
      if (day === 9) add(s, -21.9, 'SPOTIFY', nu);
      if (day === 15) add(s, -119.9, 'SMART FIT MENSALIDADE', nu);
      if (day === 18) add(s, -12.9, 'APPLE.COM/BILL ICLOUD', nu);
      if (day === 1 && d.getMonth() % 2 === 0) add(s, -24.5, 'TARIFA PACOTE SERVICOS', it);
      if (wd === 6 || (wd === 3 && rnd() < 0.3)) add(s, -(110 + rnd() * 170), pick(['SUPERMERCADO EXTRA', 'CARREFOUR HIPER', 'ASSAI ATACADISTA', 'PAO DE ACUCAR']), it);
      if (rnd() < (wd === 5 || wd === 6 ? 0.55 : 0.25)) add(s, -(35 + rnd() * 70), pick(['IFOOD *RESTAURANTE SABOR', 'IFOOD *PIZZARIA BELLA', 'IFOOD *SUSHI HOUSE', 'IFOOD *BURGER KING']), nu);
      if (rnd() < 0.35) add(s, -(14 + rnd() * 32), pick(['UBER *TRIP', 'UBER *TRIP', '99APP *CORRIDA']), nu);
      if (rnd() < 0.05) add(s, -(160 + rnd() * 70), 'POSTO IPIRANGA COMBUSTIVEL', it);
      if (rnd() < 0.3 && wd > 0 && wd < 6) add(s, -(8 + rnd() * 22), pick(['Café da manhã', 'Padaria', 'Almoço no centro', 'Lanche']), cart, `${pad(8 + Math.floor(rnd() * 10))}:${pad(Math.floor(rnd() * 60))}`);
      if (rnd() < 0.06) add(s, -(60 + rnd() * 280), pick(['AMAZON MARKETPLACE', 'MERCADO LIVRE', 'SHOPEE', 'RENNER LOJA']), nu);
      if (rnd() < 0.05) add(s, -(30 + rnd() * 120), pick(['DROGASIL', 'DROGA RAIA']), nu);
      if (rnd() < 0.04) add(s, -(40 + rnd() * 120), pick(['CINEMARK', 'SYMPLA INGRESSO', 'BAR DO ZE']), nu);
      if (rnd() < 0.05) add(s, -(50 + rnd() * 250), 'PIX ENVIADO - MARIA SOUZA', it);
      if (day === 7) add(s, 1500, 'TRANSFERENCIA RECEBIDA - REEMBOLSO EMPRESA', nu);
    }
    save();
  }

  /* ============ toast ============ */
  let toastT;
  function toast(msg) {
    const el = $('#toast'); el.textContent = msg; el.classList.remove('hidden');
    clearTimeout(toastT); toastT = setTimeout(() => el.classList.add('hidden'), 3200);
  }

  /* instalar na tela inicial */
  let installEvt = null;
  const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone;
  const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const installDismissed = () => { try { return localStorage.getItem(STORE_KEY + ':noinstall') === '1'; } catch (e) { return false; } };
  function showInstall() {
    if (standalone || installDismissed()) return;
    if (installEvt) $('#install').classList.remove('hidden');
    else if (isIOS) {
      $('#install-text').textContent = 'No Safari, toque em Compartilhar e depois em “Adicionar à Tela de Início”.';
      $('#install-btn').classList.add('hidden');
      $('#install').classList.remove('hidden');
    }
  }
  window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); installEvt = e; showInstall(); });
  $('#install-btn').addEventListener('click', async () => {
    if (!installEvt) return;
    installEvt.prompt(); await installEvt.userChoice; installEvt = null; $('#install').classList.add('hidden');
  });
  $('#install-x').addEventListener('click', () => {
    $('#install').classList.add('hidden');
    try { localStorage.setItem(STORE_KEY + ':noinstall', '1'); } catch (e) { /* */ }
  });
  showInstall();

  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => render());
  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});
  render();
})();
