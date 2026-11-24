/* Baixa Martins — app (roda no navegador do PC, no navegador do celular e dentro do APK). */
'use strict';

// ---------------------------------------------------------------- utilidades
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (n, cls = '') => `<svg class="${cls}"><use href="#i-${n}"/></svg>`;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const Android = window.Android || null;
const isApk = !!Android || location.hostname === 'appassets.androidplatform.net';

function fmtTime(s) {
  if (!isFinite(s) || s < 0) s = 0;
  s = Math.floor(s);
  const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60, x = s % 60;
  return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(x).padStart(2, '0');
}
function fmtSize(b) {
  if (!b) return '';
  if (b > 1 << 30) return (b / (1 << 30)).toFixed(1).replace('.', ',') + ' GB';
  return Math.max(1, Math.round(b / (1 << 20))) + ' MB';
}
function fmtViews(n) {
  if (!n) return '';
  if (n >= 1e9) return (n / 1e9).toFixed(1).replace('.', ',') + ' bi de views';
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace('.', ',') + ' mi de views';
  if (n >= 1e3) return Math.round(n / 1e3) + ' mil views';
  return n + ' views';
}
function fmtSpeed(b) { return b ? (b > 1 << 20 ? (b / (1 << 20)).toFixed(1).replace('.', ',') + ' MB/s' : Math.round(b / 1024) + ' KB/s') : ''; }
const LANGS = { auto: 'Automático', pt: 'Português', en: 'Inglês', es: 'Espanhol', fr: 'Francês', it: 'Italiano', de: 'Alemão', ja: 'Japonês', ko: 'Coreano', zh: 'Chinês', ru: 'Russo', ar: 'Árabe', hi: 'Hindi', nl: 'Holandês', pl: 'Polonês', tr: 'Turco' };
const langName = l => LANGS[l] || (l ? l.toUpperCase() : '');
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { } },
};

let toastT;
function toast(msg, err) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast on' + (err ? ' err' : '');
  clearTimeout(toastT);
  toastT = setTimeout(() => t.className = 'toast', err ? 4200 : 2600);
}

// janela de baixo (menus, perguntas)
let sheetClose = null;
function openSheet(html, onMount) {
  const w = $('#sheetWrap'), s = $('#sheet');
  s.innerHTML = html;
  w.classList.remove('hidden');
  const close = () => { w.classList.add('hidden'); s.innerHTML = ''; sheetClose = null; };
  sheetClose = close;
  w.onclick = e => { if (e.target === w) close(); };
  onMount && onMount(s, close);
  return close;
}
function ask(title, text, okLabel = 'Confirmar', danger = false) {
  return new Promise(res => {
    openSheet(`<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="actions">
      <button class="btn ghost" data-a="no">Cancelar</button>
      <button class="btn ${danger ? 'danger' : 'primary'}" data-a="ok">${esc(okLabel)}</button></div>`, (s, close) => {
      s.onclick = e => { const a = e.target.closest('[data-a]')?.dataset.a; if (!a) return; close(); res(a === 'ok'); };
    });
    const prev = sheetClose;
    sheetClose = () => { prev(); res(false); };
  });
}

// ---------------------------------------------------------------- conexão
const S = {
  base: '', token: '', local: false, name: '', version: '',
  jobs: [], tools: [], settings: {}, lib: [], libById: {},
  tab: 'baixar', resolved: null, sel: new Set(), resolving: false,
  dlKind: store.get('bm.kind', 'audio'), dlFmt: store.get('bm.fmt', ''), dlQual: store.get('bm.qual', ''), dlLang: 'auto', dlAI: null,
  libKind: 'audio', libQ: '', libGroup: '', trKind: 'video', trQ: '', trOpen: null, tCache: {},
};

async function api(path, body, opts = {}) {
  const init = { method: body === undefined ? 'GET' : 'POST', headers: {} };
  if (S.token) init.headers['X-Token'] = S.token;
  if (body !== undefined) { init.headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(body); }
  if (opts.signal) init.signal = opts.signal;
  let r;
  try { r = await fetch(S.base + path, init); } catch (e) {
    if (e.name === 'AbortError') throw e;
    throw new Error('Sem conexão com o computador.');
  }
  const ct = r.headers.get('content-type') || '';
  const data = ct.includes('json') ? await r.json() : await r.text();
  if (r.status === 401 && !opts.noAuthRedirect) { showPair('Este aparelho perdeu o acesso. Conecte de novo com o código.'); throw new Error('sem acesso'); }
  if (!r.ok) throw new Error((data && data.erro) || ('Erro ' + r.status));
  return data;
}
const tq = () => S.token ? '&t=' + encodeURIComponent(S.token) : '';
const mediaURL = (id, dl) => `${S.base}/media?id=${encodeURIComponent(id)}${dl ? '&dl=1' : ''}${tq()}`;
const thumbURL = (vid, keep) => vid ? `${S.base}/api/thumb?v=${encodeURIComponent(vid)}${keep ? '&keep=1' : ''}` : '';
const exportURL = (id, fmt, dl) => `${S.base}/api/export?id=${encodeURIComponent(id)}&fmt=${fmt}${dl ? '&dl=1' : ''}${tq()}`;

function normBase(a) {
  a = a.trim().replace(/\/+$/, '');
  if (!a) return '';
  if (!/^https?:\/\//i.test(a)) a = 'http://' + a;
  try {
    const u = new URL(a);
    if (!u.port) u.port = '47900';
    return u.origin;
  } catch { return ''; }
}

async function boot() {
  const saved = store.get('bm.conn', null);
  if (!isApk && /^https?:$/.test(location.protocol)) {
    S.base = location.origin;
    if (saved && saved.base === S.base) S.token = saved.token;
  } else if (saved) {
    S.base = saved.base; S.token = saved.token;
  } else {
    return showPair();
  }
  try {
    const info = await api('/api/info', undefined, { noAuthRedirect: true });
    if (!info.authed) return showPair();
    start();
  } catch (e) {
    if (isApk) showPair('Não achei o computador. Ele está ligado, com o Baixa Martins aberto (e o Tailscale ligado)?');
    else { start(); }
  }
}

function showPair(msg) {
  $('#app').classList.add('hidden');
  $('#player').classList.add('hidden');
  $('#pair').classList.remove('hidden');
  $('#pairAddrRow').classList.toggle('hidden', !isApk);
  const saved = store.get('bm.conn', null);
  if (isApk && saved) $('#pairAddr').value = saved.base.replace(/^http:\/\//, '').replace(/:47900$/, '');
  if (msg) $('#pairErr').textContent = msg;
}

$('#pairForm').onsubmit = async e => {
  e.preventDefault();
  const err = $('#pairErr'), btn = $('#pairBtn');
  err.textContent = '';
  if (isApk) {
    S.base = normBase($('#pairAddr').value);
    if (!S.base) { err.textContent = 'Digite o endereço do computador (ex.: 100.101.102.103).'; return; }
  }
  btn.disabled = true; btn.textContent = 'Conectando…';
  try {
    S.token = '';
    const name = isApk ? 'Celular (app)' : (/(android|iphone|ipad)/i.exec(navigator.userAgent)?.[1] || 'Navegador');
    const r = await api('/api/pair', { code: $('#pairCode').value, name }, { noAuthRedirect: true });
    S.token = r.token;
    store.set('bm.conn', { base: S.base, token: S.token, name: r.name });
    $('#pair').classList.add('hidden');
    start();
  } catch (ex) {
    err.textContent = ex.message === 'Sem conexão com o computador.'
      ? 'Não achei o computador nesse endereço. Confira o endereço, se o Baixa Martins está aberto no PC e se o Tailscale está ligado.'
      : ex.message;
  } finally { btn.disabled = false; btn.textContent = 'Conectar'; }
};

let started = false, es = null;
async function start() {
  $('#pair').classList.add('hidden');
  $('#app').classList.remove('hidden');
  if (started) { connectEvents(); loadState(); loadLibrary(); return; }
  started = true;
  const h = location.hash.slice(1);
  if (h === 'celular') { goTab('ajustes'); }
  await loadState();
  loadLibrary();
  connectEvents();
  if (pendingShared) { const t = pendingShared; pendingShared = null; window.sharedLink(t); }
}

async function loadState() {
  try {
    const st = await api('/api/state');
    S.jobs = st.jobs; S.tools = st.tools; S.settings = st.settings; S.local = st.local; S.name = st.name; S.version = st.version;
    $('#offline').classList.add('hidden');
    $('#navFoot').innerHTML = `${icon('pc')}<b>${esc(S.name)}</b><br>Baixa Martins ${esc(S.version)}`;
    renderQueue(); renderSettings();
  } catch (e) {
    $('#offline').classList.remove('hidden');
  }
}

let libT = null;
async function loadLibrary() {
  try {
    const lib = await api('/api/library');
    S.lib = lib;
    S.libById = Object.fromEntries(lib.map(i => [i.id, i]));
    renderLibrary(); renderTrList();
  } catch { }
}
function loadLibrarySoon() { clearTimeout(libT); libT = setTimeout(loadLibrary, 300); }

function connectEvents() {
  if (es) es.close();
  es = new EventSource(`${S.base}/api/events?x=1${tq()}`);
  es.onopen = () => { $('#offline').classList.add('hidden'); };
  es.onerror = () => {
    $('#offline').classList.remove('hidden');
    if (es.readyState === 2) setTimeout(connectEvents, 3000);
  };
  es.onmessage = e => {
    const m = JSON.parse(e.data);
    $('#offline').classList.add('hidden');
    if (m.type === 'jobs') onJobs(m.jobs);
    else if (m.type === 'tools') { S.tools = m.tools; renderToolsBanner(); renderSettingsTools(); }
    else if (m.type === 'library') loadLibrarySoon();
    else if (m.type === 'transcript') onTranscriptReady(m.id);
  };
}

function onJobs(jobs) {
  const before = Object.fromEntries(S.jobs.map(j => [j.id, j.state]));
  S.jobs = jobs;
  for (const j of jobs) {
    if (before[j.id] && before[j.id] !== j.state) {
      if (j.state === 'pronto' && j.type === 'download') toast('Baixado: ' + j.title);
      if (j.state === 'erro') toast(j.title + ': ' + j.error, true);
    }
  }
  renderQueue();
  renderLyrJob();
}

// ---------------------------------------------------------------- abas
function goTab(t) {
  S.tab = t;
  $$('#nav button').forEach(b => b.classList.toggle('on', b.dataset.tab === t));
  $$('.tab').forEach(s => s.classList.toggle('on', s.id === 'tab-' + t));
  $('#main').scrollTop = 0;
  if (t === 'transcricoes') renderTrList();
  if (t === 'ajustes') renderSettings();
}
$$('#nav button').forEach(b => b.onclick = () => goTab(b.dataset.tab));

// ---------------------------------------------------------------- baixar
$('#pasteBtn').onclick = async () => {
  try {
    const t = Android?.readClipboard ? Android.readClipboard() : await navigator.clipboard.readText();
    if (t) { $('#q').value = t.trim(); doResolve(); }
  } catch { toast('Não consegui ler a área de transferência. Cole com o teclado.', true); }
};
$('#searchForm').onsubmit = e => { e.preventDefault(); doResolve(); };

let resolveCtl = null;
async function doResolve() {
  const q = $('#q').value.trim();
  if (!q) return $('#q').focus();
  $('#q').blur();
  resolveCtl?.abort();
  resolveCtl = new AbortController();
  S.resolved = null; S.sel.clear();
  $('#resolveArea').innerHTML = `<div class="spinner"></div><p class="empty" style="padding-top:0">${/^(https?:|www\.|youtu|music\.)/i.test(q) ? 'Lendo o link…' : 'Procurando no YouTube…'}</p>`;
  try {
    const r = await api('/api/resolve', { q }, { signal: resolveCtl.signal });
    S.resolved = r;
    if (r.kind === 'video') S.sel.add(r.entries[0].id);
    else if (r.kind === 'playlist') {
      if (r.focus && r.entries.some(e => e.id === r.focus)) S.sel.add(r.focus);
      else r.entries.forEach(e => S.sel.add(e.id));
    }
    renderResolved();
  } catch (e) {
    if (e.name === 'AbortError') return;
    $('#resolveArea').innerHTML = `<div class="empty">${icon('close')}<b>Não deu certo</b>${esc(e.message)}</div>`;
  }
}

function optsBar(count) {
  const k = S.dlKind;
  const ai = S.dlAI ?? (k === 'audio' ? S.settings.autoLyrics : S.settings.autoSubs);
  const fmt = S.dlFmt || `${S.settings.audioFmt || 'mp3'}-${S.settings.audioKbps || 320}`;
  const qual = S.dlQual || S.settings.videoQual || '1080';
  return `<div class="opts">
    <div class="seg" id="kindSeg">
      <button data-k="audio" class="${k === 'audio' ? 'on' : ''}">${icon('music')}Música</button>
      <button data-k="video" class="${k === 'video' ? 'on' : ''}">${icon('video')}Vídeo</button>
    </div>
    ${k === 'audio' ? `<select class="select" id="fmtSel">
      ${[['mp3-320', 'MP3 · 320 kbps'], ['mp3-256', 'MP3 · 256 kbps'], ['mp3-192', 'MP3 · 192 kbps'], ['mp3-128', 'MP3 · 128 kbps'], ['m4a-0', 'M4A (original)'], ['opus-0', 'Opus (original)']]
        .map(([v, l]) => `<option value="${v}" ${v === fmt || (fmt.startsWith(v.split('-')[0]) && v.endsWith('-0')) ? 'selected' : ''}>${l}</option>`).join('')}
    </select>` : `<select class="select" id="qualSel">
      ${[['best', 'Melhor possível'], ['2160', '4K (2160p)'], ['1440', '1440p'], ['1080', 'Full HD (1080p)'], ['720', 'HD (720p)'], ['480', '480p'], ['360', '360p (leve)']]
        .map(([v, l]) => `<option value="${v}" ${v === qual ? 'selected' : ''}>${l}</option>`).join('')}
    </select>`}
    <label class="check"><input type="checkbox" id="aiChk" ${ai ? 'checked' : ''}><i></i><span class="ai">${icon('ai')}${k === 'audio' ? 'Fazer a letra com IA' : 'Fazer legenda e transcrição com IA'}</span></label>
    <select class="select" id="langSel" title="Idioma falado/cantado" ${ai ? '' : 'disabled style="opacity:.5"'}>
      ${Object.entries(LANGS).slice(0, 10).map(([v, l]) => `<option value="${v}" ${v === S.dlLang ? 'selected' : ''}>${v === 'auto' ? 'Idioma: automático' : l}</option>`).join('')}
    </select>
    <span class="grow"></span>
    <button class="btn primary" id="dlBtn" ${count ? '' : 'disabled'}>${icon('down')}${count > 1 ? `Baixar ${count}` : 'Baixar'}</button>
  </div>`;
}

function bindOpts() {
  $$('#kindSeg button').forEach(b => b.onclick = () => { S.dlKind = b.dataset.k; S.dlAI = null; store.set('bm.kind', S.dlKind); renderResolved(); });
  const f = $('#fmtSel'); if (f) f.onchange = () => { S.dlFmt = f.value; store.set('bm.fmt', f.value); };
  const q = $('#qualSel'); if (q) q.onchange = () => { S.dlQual = q.value; store.set('bm.qual', q.value); };
  $('#aiChk').onchange = e => { S.dlAI = e.target.checked; renderResolved(); };
  $('#langSel').onchange = e => { S.dlLang = e.target.value; };
  $('#dlBtn').onclick = startDownload;
}

async function startDownload() {
  const r = S.resolved;
  if (!r) return;
  const entries = r.entries.filter(e => S.sel.has(e.id));
  if (!entries.length) return;
  const [fmt, kbps] = (S.dlFmt || `${S.settings.audioFmt || 'mp3'}-${S.settings.audioKbps || 320}`).split('-');
  const ai = S.dlAI ?? (S.dlKind === 'audio' ? S.settings.autoLyrics : S.settings.autoSubs);
  const body = {
    entries, kind: S.dlKind, transcribe: !!ai, lang: S.dlLang,
    playlist: r.kind === 'playlist' && entries.length > 1 ? r.title : '',
  };
  if (S.dlKind === 'audio') { body.format = fmt; body.kbps = +kbps || 0; } else body.quality = S.dlQual || S.settings.videoQual;
  try {
    const res = await api('/api/download', body);
    toast(res.added > 1 ? `${res.added} itens na fila` : 'Na fila! Acompanhe em “Fila”.');
    updateQBadge();
    S.sel.clear(); renderResolved();
  } catch (e) { toast(e.message, true); }
}

function entryCard(e, selectable) {
  const on = S.sel.has(e.id);
  return `<div class="card ${on ? 'sel' : ''}" data-id="${esc(e.id)}">
    <div class="thumb"><img loading="lazy" src="${thumbURL(e.id)}" alt="" onerror="this.style.opacity=0">
      ${e.duration ? `<span class="dur">${fmtTime(e.duration)}</span>` : ''}${selectable ? '<span class="tick"></span>' : ''}</div>
    <div class="meta"><div class="t">${esc(e.title)}</div><div class="s">${esc(e.channel || '')}${e.views ? ' · ' + fmtViews(e.views) : ''}</div></div>
  </div>`;
}

function renderResolved() {
  const r = S.resolved, area = $('#resolveArea');
  if (!r) { area.innerHTML = ''; return; }
  if (!r.entries.length) { area.innerHTML = `<div class="empty">${icon('search')}<b>Nada encontrado</b>Tente outro nome ou cole o link.</div>`; return; }
  if (r.kind === 'video') {
    const e = r.entries[0];
    const have = S.libById[e.id + (S.dlKind === 'audio' ? '-a' : '-v')];
    area.innerHTML = `<div class="big-one">
      <div class="thumb"><img src="${thumbURL(e.id)}" alt="">${e.duration ? `<span class="dur">${fmtTime(e.duration)}</span>` : ''}</div>
      <div><h3>${esc(e.title)}</h3><div class="muted">${esc(e.channel)}${e.views ? ' · ' + fmtViews(e.views) : ''}</div>
      ${e.live ? '<p class="err">É uma transmissão ao vivo: espere terminar para baixar.</p>' : ''}
      ${have ? `<p class="muted small">${icon('check')} Você já baixou este ${S.dlKind === 'audio' ? 'áudio' : 'vídeo'}. Baixar de novo substitui.</p>` : ''}
      </div></div>${optsBar(1).replace('class="opts"', 'class="opts" style="position:static;margin-top:14px"')}`;
    bindOpts();
    return;
  }
  const n = S.sel.size, all = n === r.entries.length;
  area.innerHTML = `<div class="res-head">
      <div class="rh-txt"><h3>${r.kind === 'search' ? 'Resultados para “' + esc(r.title) + '”' : esc(r.title || 'Playlist')}</h3>
      <p class="muted">${r.kind === 'playlist' ? `Playlist${r.channel ? ' de ' + esc(r.channel) : ''} · ${r.entries.length} vídeos` : 'Toque para escolher um ou vários'}</p></div>
      <button class="btn small ghost" id="selAll">${all ? 'Desmarcar todos' : 'Marcar todos'}</button>
    </div>${optsBar(n)}<div class="grid" id="resGrid">${r.entries.map(e => entryCard(e, true)).join('')}</div>`;
  bindOpts();
  $('#selAll').onclick = () => { if (all) S.sel.clear(); else r.entries.forEach(e => S.sel.add(e.id)); renderResolved(); };
  $('#resGrid').onclick = ev => {
    const c = ev.target.closest('.card'); if (!c) return;
    const id = c.dataset.id;
    S.sel.has(id) ? S.sel.delete(id) : S.sel.add(id);
    c.classList.toggle('sel', S.sel.has(id));
    const b = $('#dlBtn'); const k = S.sel.size;
    b.disabled = !k; b.innerHTML = icon('down') + (k > 1 ? `Baixar ${k}` : 'Baixar');
    $('#selAll').textContent = k === r.entries.length ? 'Desmarcar todos' : 'Marcar todos';
  };
}

// ---------------------------------------------------------------- fila
function updateQBadge() {
  const n = S.jobs.filter(j => j.state === 'fila' || j.state === 'ativo').length;
  const b = $('#qBadge'); b.textContent = n; b.classList.toggle('hidden', !n);
}

function renderToolsBanner() {
  const busy = S.tools.filter(t => t.busy);
  const el = $('#toolsBanner');
  if (!busy.length) { el.innerHTML = ''; return; }
  el.innerHTML = busy.map(t => `<div class="tools-banner"><b>Preparando:</b> baixando ${esc(t.label)} (${esc(t.size)}) — só na primeira vez
    <div class="bar"><i style="width:${Math.round(t.progress * 100)}%"></i></div></div>`).join('');
}

function jobStatus(j) {
  const pct = Math.round(j.progress * 100);
  if (j.state === 'fila') return `<span>${j.type === 'transcribe' ? 'Aguardando a IA' : 'Na fila'}</span>`;
  if (j.state === 'pronto') return `<span class="st-ok">${icon('check')} ${j.type === 'transcribe' ? (j.mode === 'lyrics' ? 'Letra pronta' : 'Transcrição pronta') : 'Baixado'}</span>`;
  if (j.state === 'erro') return `<span class="st-err">${esc(j.error || 'Erro')}</span>`;
  if (j.state === 'cancelado') return '<span>Cancelado</span>';
  const bits = [`<span class="${j.type === 'transcribe' ? 'st-ai' : ''}">${esc(j.phase)}</span>`];
  if (pct > 0) bits.push(`<span>${pct}%</span>`);
  if (j.speed) bits.push(`<span>${fmtSpeed(j.speed)}</span>`);
  if (j.eta) bits.push(`<span>faltam ${fmtTime(j.eta)}</span>`);
  return bits.join('');
}

function renderQueue() {
  updateQBadge();
  renderToolsBanner();
  const q = $('#queue');
  if (!S.jobs.length) {
    q.innerHTML = `<div class="empty">${icon('down')}<b>Nada na fila</b>Procure uma música ou cole um link em “Baixar”.</div>`;
    return;
  }
  const rank = { ativo: 0, fila: 1, erro: 2, cancelado: 3, pronto: 4 };
  const list = [...S.jobs].sort((a, b) => (rank[a.state] - rank[b.state]) || (a.state === 'fila' ? a.created.localeCompare(b.created) : (b.finished || b.created).localeCompare(a.finished || a.created)));
  q.innerHTML = list.map(j => {
    const indet = j.state === 'ativo' && j.progress < 0.01;
    return `<div class="job ${j.state}" data-id="${j.id}">
      <div class="jt" style="background-image:url('${thumbURL(j.videoId)}')"><span class="kind">${icon(j.type === 'transcribe' ? 'ai' : j.kind === 'video' ? 'video' : 'music')}</span></div>
      <div class="jb">
        <div class="jn">${esc(j.title)}</div>
        <div class="jp">${j.type === 'transcribe' ? `<span class="tag">${icon('ai')} ${j.mode === 'lyrics' ? 'Letra' : 'Legenda'}</span>` : ''}${j.playlist ? `<span>${esc(j.playlist)}</span>` : ''}${jobStatus(j)}</div>
        ${j.state === 'ativo' || j.state === 'fila' ? `<div class="bar ${indet ? 'indet' : ''}"><i style="width:${Math.round(j.progress * 100)}%"></i></div>` : ''}
        ${j.type === 'transcribe' && j.state === 'ativo' && j.lines?.length ? `<div class="live">${j.lines.slice().reverse().map(l => `<div>${esc(l.t)}</div>`).join('')}</div>` : ''}
      </div>
      <div class="ja">
        ${j.state === 'pronto' && j.itemId ? `<button class="icon-btn small" data-a="play" title="Tocar">${icon('play')}</button>` : ''}
        ${j.state === 'erro' || j.state === 'cancelado' ? `<button class="icon-btn small" data-a="retry" title="Tentar de novo">${icon('retry')}</button>` : ''}
        ${j.state === 'ativo' || j.state === 'fila' ? `<button class="icon-btn small ghost" data-a="cancel" title="Cancelar">${icon('close')}</button>`
        : `<button class="icon-btn small ghost" data-a="clear" title="Tirar da lista">${icon('close')}</button>`}
      </div></div>`;
  }).join('');
}
$('#queue').onclick = e => {
  const b = e.target.closest('[data-a]'); if (!b) return;
  const id = b.closest('.job').dataset.id, a = b.dataset.a;
  if (a === 'play') {
    const j = S.jobs.find(x => x.id === id);
    const it = S.libById[j.itemId];
    if (it) playList([it], 0, true); else loadLibrary().then(() => S.libById[j.itemId] && playList([S.libById[j.itemId]], 0, true));
    return;
  }
  api('/api/job', { id, action: a }).catch(err => toast(err.message, true));
};
$('#clearDone').onclick = () => api('/api/job', { action: 'clear', id: '' });
$('#cancelAll').onclick = async () => { if (await ask('Cancelar tudo?', 'Os downloads e transcrições em andamento param.', 'Cancelar tudo', true)) api('/api/job', { action: 'cancelAll' }); };

// ---------------------------------------------------------------- biblioteca
function activeTrJob(id) { return S.jobs.find(j => j.type === 'transcribe' && j.itemId === id && (j.state === 'ativo' || j.state === 'fila')); }

function libFiltered(kind = S.libKind) {
  const q = S.libQ.toLowerCase();
  return S.lib.filter(i => i.kind === kind &&
    (!q || (i.title + ' ' + i.artist + ' ' + (i.playlist || '')).toLowerCase().includes(q)) &&
    (!S.libGroup || (S.libGroup === '#letra' ? !!i.transcript : S.libGroup === '#semletra' ? !i.transcript : i.playlist === S.libGroup)));
}

function itemTags(i) {
  const tags = [];
  if (i.missing) tags.push('<span class="tag warn">arquivo sumiu</span>');
  const tj = activeTrJob(i.id);
  if (tj) tags.push(`<span class="tag">${icon('ai')} ${tj.state === 'ativo' ? Math.round(tj.progress * 100) + '%' : 'na fila'}</span>`);
  else if (i.transcript) tags.push(`<span class="tag">${icon('text')} ${i.kind === 'audio' ? 'Letra' : 'Legenda'}${i.lang ? ' · ' + esc(i.lang.toUpperCase()) : ''}</span>`);
  return tags.join('');
}

function renderLibrary() {
  // grupos (playlists)
  const groups = [...new Set(S.lib.filter(i => i.kind === S.libKind && i.playlist).map(i => i.playlist))].sort();
  const gs = $('#libGroup');
  gs.innerHTML = `<option value="">Tudo</option><option value="#letra">${S.libKind === 'audio' ? 'Com letra' : 'Com legenda'}</option><option value="#semletra">${S.libKind === 'audio' ? 'Sem letra' : 'Sem legenda'}</option>` +
    groups.map(g => `<option value="${esc(g)}">${esc(g)}</option>`).join('');
  gs.value = groups.includes(S.libGroup) || S.libGroup.startsWith('#') ? S.libGroup : '';
  const list = libFiltered();
  const el = $('#lib');
  el.className = 'lib' + (S.libKind === 'video' ? ' videos' : '');
  if (!list.length) {
    el.innerHTML = `<div class="empty">${icon(S.libKind === 'audio' ? 'music' : 'video')}<b>${S.lib.some(i => i.kind === S.libKind) ? 'Nada com esse filtro' : S.libKind === 'audio' ? 'Nenhuma música ainda' : 'Nenhum vídeo ainda'}</b>${S.lib.some(i => i.kind === S.libKind) ? '' : 'Tudo que você baixar aparece aqui.'}</div>`;
    return;
  }
  const cur = P.item?.id, paused = !P.playing;
  if (S.libKind === 'audio') {
    el.innerHTML = list.map(i => `<div class="song ${i.id === cur ? 'now' : ''} ${paused ? 'paused' : ''}" data-id="${esc(i.id)}">
      <div class="cv"><img loading="lazy" src="${thumbURL(i.videoId, 1)}" alt="" onerror="this.style.opacity=0"><span class="eq"><i></i><i></i><i></i></span></div>
      <div class="sb"><div class="st">${esc(i.title)}</div><div class="ss">${itemTags(i)}<span>${esc(i.artist)}</span></div></div>
      <span class="sd">${fmtTime(i.duration)}</span>
      <button class="icon-btn small ghost" data-a="more" title="Mais">${icon('more')}</button></div>`).join('');
  } else {
    el.innerHTML = list.map(i => `<div class="card ${i.id === cur ? 'now' : ''} ${paused ? 'paused' : ''}" data-id="${esc(i.id)}">
      <div class="thumb"><img loading="lazy" src="${thumbURL(i.videoId, 1)}" alt="" onerror="this.style.opacity=0"><span class="dur">${fmtTime(i.duration)}</span><span class="eq"><i></i><i></i><i></i></span></div>
      <div class="meta"><div class="t">${esc(i.title)}</div><div class="s" style="display:flex;gap:6px;align-items:center">${itemTags(i)}<span style="overflow:hidden;text-overflow:ellipsis">${esc(i.artist)}</span>
      <button class="icon-btn small ghost" data-a="more" title="Mais" style="margin-left:auto">${icon('more')}</button></div></div></div>`).join('');
  }
}
$('#lib').onclick = e => {
  const row = e.target.closest('[data-id]'); if (!row) return;
  const it = S.libById[row.dataset.id];
  if (e.target.closest('[data-a=more]')) return itemMenu(it);
  const list = libFiltered();
  playList(list, list.indexOf(it), true);
};
$$('#libSeg button').forEach(b => b.onclick = () => {
  S.libKind = b.dataset.k; S.libGroup = '';
  $$('#libSeg button').forEach(x => x.classList.toggle('on', x === b));
  renderLibrary();
});
$('#libQ').oninput = e => { S.libQ = e.target.value; renderLibrary(); };
$('#libGroup').onchange = e => { S.libGroup = e.target.value; renderLibrary(); };
$('#playAll').onclick = () => { const l = libFiltered(); if (l.length) { P.shuffle = false; playList(l, 0, true); } };
$('#shuffleAll').onclick = () => { const l = libFiltered(); if (l.length) { P.shuffle = true; playList(l, Math.floor(Math.random() * l.length), true); } };

function saveToDevice(it) {
  const url = mediaURL(it.id, true);
  const name = it.file.split(/[\\/]/).pop();
  if (Android?.download) { Android.download(url, name); toast('Baixando para o celular… (veja nas notificações)'); return; }
  const a = document.createElement('a');
  a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
}

function itemMenu(it) {
  if (!it) return;
  const tj = activeTrJob(it.id);
  const isA = it.kind === 'audio';
  openSheet(`<div class="s-head"><div class="cv" style="background-image:url('${thumbURL(it.videoId, 1)}')"></div>
    <div><b>${esc(it.title)}</b><span class="muted small">${esc(it.artist)} · ${fmtTime(it.duration)} · ${esc((it.ext || '').toUpperCase())} ${fmtSize(it.size)}</span></div></div>
    <div class="menu">
      <button data-a="play">${icon('play')}Tocar</button>
      <button data-a="next">${icon('list')}Tocar a seguir</button>
      ${it.transcript ? `<button data-a="read">${icon('text')}${isA ? 'Ver a letra' : 'Ver a transcrição'}</button>` : ''}
      ${tj ? `<button data-a="none" disabled>${icon('ai')}A IA está trabalhando (${Math.round(tj.progress * 100)}%)…</button>`
      : `<button data-a="tr">${icon('ai')}${it.transcript ? (isA ? 'Refazer a letra com IA' : 'Refazer a legenda com IA') : (isA ? 'Fazer a letra com IA' : 'Fazer legenda e transcrição com IA')}</button>`}
      ${S.local ? `<button data-a="reveal">${icon('folder')}Mostrar no computador</button>` : `<button data-a="save">${icon('save')}Salvar neste aparelho</button>`}
      <button data-a="del" class="danger">${icon('trash')}Excluir</button>
    </div>`, (s, close) => {
    s.onclick = async e => {
      const a = e.target.closest('[data-a]')?.dataset.a; if (!a || a === 'none') return;
      close();
      if (a === 'play') playList([it], 0, true);
      else if (a === 'next') playNext(it);
      else if (a === 'read') { goTab('transcricoes'); S.trKind = it.kind; openTr(it.id); }
      else if (a === 'tr') transcribeDialog(it);
      else if (a === 'reveal') api('/api/open', { id: it.id }).catch(er => toast(er.message, true));
      else if (a === 'save') saveToDevice(it);
      else if (a === 'del') deleteDialog(it);
    };
  });
}

function deleteDialog(it) {
  openSheet(`<h3>Excluir “${esc(it.title)}”?</h3><p>Você pode tirar só da biblioteca (o arquivo continua no computador) ou apagar o arquivo também.</p>
    <div class="menu"><button data-a="lib">${icon('list')}Tirar só da biblioteca</button>
    <button data-a="file" class="danger">${icon('trash')}Apagar o arquivo do computador</button></div>
    <div class="actions"><button class="btn ghost" data-a="no">Cancelar</button></div>`, (s, close) => {
    s.onclick = async e => {
      const a = e.target.closest('[data-a]')?.dataset.a; if (!a) return;
      close();
      if (a === 'no') return;
      try {
        await api('/api/library/delete', { id: it.id, file: a === 'file' });
        if (P.item?.id === it.id) stopPlayer();
        toast('Excluído');
      } catch (er) { toast(er.message, true); }
    };
  });
}

function transcribeDialog(it) {
  const isA = it.kind === 'audio';
  const st = S.settings;
  openSheet(`<h3>${icon('ai', 'grad')} ${isA ? 'Fazer a letra com IA' : 'Legenda e transcrição com IA'}</h3>
    <p>A inteligência artificial ouve ${isA ? 'a música' : 'o vídeo'} no computador e escreve ${isA ? 'a letra sincronizada, separando versos e refrão' : 'tudo o que é falado, com o tempo de cada frase'}. Leva de alguns segundos a poucos minutos.</p>
    <div class="field">Idioma ${isA ? 'cantado' : 'falado'}<select class="select" id="tLang">${Object.entries(LANGS).map(([v, l]) => `<option value="${v}" ${v === (st.language || 'auto') ? 'selected' : ''}>${l}</option>`).join('')}</select></div>
    <div class="field">Qualidade da IA<select class="select" id="tModel">
      <option value="otimo" ${st.model === 'otimo' ? 'selected' : ''}>Ótima (recomendada)</option>
      <option value="bom" ${st.model === 'bom' ? 'selected' : ''}>Boa</option>
      <option value="rapido" ${st.model === 'rapido' ? 'selected' : ''}>Rápida (menos precisa)</option></select></div>
    <div class="field">Tipo<select class="select" id="tMode">
      <option value="lyrics" ${isA ? 'selected' : ''}>Letra de música (versos, refrão, karaokê)</option>
      <option value="transcript" ${isA ? '' : 'selected'}>Fala (parágrafos e legenda)</option></select></div>
    <label class="check"><input type="checkbox" id="tTr"><i></i>Traduzir para o inglês</label>
    <div class="actions"><button class="btn ghost" data-a="no">Cancelar</button><button class="btn primary" data-a="ok">${icon('ai')}Começar</button></div>`, (s, close) => {
    s.onclick = async e => {
      const a = e.target.closest('[data-a]')?.dataset.a; if (!a) return;
      if (a === 'no') return close();
      const body = { id: it.id, lang: $('#tLang').value, model: $('#tModel').value, mode: $('#tMode').value, translate: $('#tTr').checked };
      close();
      try { await api('/api/transcribe', body); toast('A IA começou. Você pode continuar usando o app.'); } catch (er) { toast(er.message, true); }
    };
  });
}

// ---------------------------------------------------------------- transcrições
async function getTranscript(id, force) {
  if (!force && S.tCache[id]) return S.tCache[id];
  const t = fixT(await api('/api/transcript?id=' + encodeURIComponent(id)));
  S.tCache[id] = t;
  return t;
}
function fixT(t) {
  t.sections = t.sections || [];
  t.sections.forEach(s => { s.lines = s.lines || []; s.lines.forEach(l => { l.w = l.w || []; }); });
  return t;
}
function plainText(t) { return t.sections.map(s => s.lines.map(l => l.t).join(' ')).join(' '); }

async function onTranscriptReady(id) {
  delete S.tCache[id];
  await loadLibrary();
  if (P.item?.id === id) { await loadPlayerTranscript(); toast(P.item.kind === 'audio' ? 'A letra ficou pronta!' : 'A legenda ficou pronta!'); }
  if (S.trOpen === id) openTr(id);
}

let trSearchT = null;
async function renderTrList() {
  $$('#trSeg button').forEach(b => b.classList.toggle('on', b.dataset.k === S.trKind));
  const items = S.lib.filter(i => i.kind === S.trKind && i.transcript);
  const el = $('#trList');
  const q = S.trQ.trim().toLowerCase();
  if (!items.length) {
    el.innerHTML = `<div class="empty">${icon('text')}<b>${S.trKind === 'video' ? 'Nenhuma transcrição' : 'Nenhuma letra'}</b>Na Biblioteca, toque em ⋯ › ${S.trKind === 'video' ? '“Fazer legenda e transcrição com IA”' : '“Fazer a letra com IA”'}, ou marque a opção de IA ao baixar.</div>`;
    if (!S.trOpen) $('#trView').innerHTML = '';
    return;
  }
  let rows = items.map(i => ({ i, snip: '' }));
  if (q) {
    await Promise.all(items.map(i => getTranscript(i.id).catch(() => null)));
    rows = rows.filter(r => {
      const t = S.tCache[r.i.id];
      const txt = t ? plainText(t) : '';
      const k = txt.toLowerCase().indexOf(q);
      if (k >= 0) { r.snip = '…' + esc(txt.slice(Math.max(0, k - 40), k)) + '<mark>' + esc(txt.slice(k, k + q.length)) + '</mark>' + esc(txt.slice(k + q.length, k + q.length + 60)) + '…'; return true; }
      return r.i.title.toLowerCase().includes(q);
    });
  }
  el.innerHTML = rows.length ? rows.map(({ i, snip }) => `<div class="tr-item ${i.kind} ${S.trOpen === i.id ? 'on' : ''}" data-id="${esc(i.id)}">
      <div class="cv" style="background-image:url('${thumbURL(i.videoId, 1)}')"></div>
      <div style="min-width:0"><b>${esc(i.title)}</b><span>${snip || esc(i.artist) + ' · ' + fmtTime(i.duration) + (i.lang ? ' · ' + langName(i.lang) : '')}</span></div></div>`).join('')
    : '<div class="empty">Nada com essa palavra.</div>';
  if (!S.trOpen && window.innerWidth >= 900 && rows[0]) openTr(rows[0].i.id);
  else if (!S.trOpen) $('#trView').innerHTML = `<div class="empty">${icon('text')}<b>Escolha uma transcrição</b></div>`;
}
$('#trList').onclick = e => { const r = e.target.closest('[data-id]'); if (r) openTr(r.dataset.id); };
$('#trQ').oninput = e => { S.trQ = e.target.value; clearTimeout(trSearchT); trSearchT = setTimeout(renderTrList, 250); };
$$('#trSeg button').forEach(b => b.onclick = () => { S.trKind = b.dataset.k; S.trOpen = null; $('.tr-layout').classList.remove('reading'); renderTrList(); });

let trIdx = null;
async function openTr(id) {
  const it = S.libById[id]; if (!it) return;
  S.trOpen = id; S.trKind = it.kind;
  $('.tr-layout').classList.add('reading');
  $$('.tr-item').forEach(x => x.classList.toggle('on', x.dataset.id === id));
  const v = $('#trView');
  v.innerHTML = '<div class="spinner"></div>';
  let t;
  try { t = await getTranscript(id); } catch (e) { v.innerHTML = `<div class="empty">${esc(e.message)}</div>`; return; }
  const isL = t.mode === 'lyrics';
  v.innerHTML = `<div class="tv-head">
      <button class="icon-btn small" id="trBack" title="Voltar" style="display:${window.innerWidth < 900 ? 'grid' : 'none'}">${icon('chev')}</button>
      <div class="cv" style="background-image:url('${thumbURL(it.videoId, 1)}');${it.kind === 'audio' ? 'width:64px;aspect-ratio:1' : ''}"></div>
      <div style="flex:1;min-width:180px"><h3>${esc(it.title)}</h3><div class="muted small">${esc(it.artist)} · ${fmtTime(it.duration)} · ${langName(t.lang)}${t.translated ? ' (traduzido)' : ''}${t.edited ? ' · corrigido' : ''}</div></div></div>
    <div class="tv-actions">
      <button class="btn small primary" data-a="play">${icon('play')}Tocar</button>
      <button class="btn small" data-a="copy">${icon('copy')}Copiar texto</button>
      <button class="btn small" data-a="exp">${icon('save')}Exportar</button>
      <button class="btn small ghost" data-a="redo">${icon('ai')}Refazer</button>
      <button class="btn small ghost danger" data-a="del">${icon('trash')}</button>
    </div>
    <div class="doc ${isL ? 'lyrics' : ''}" id="trDoc"></div>`;
  trIdx = renderDoc($('#trDoc'), t, isL);
  $('#trDoc').onclick = e => {
    const ln = e.target.closest('[data-s]'); if (!ln) return;
    const at = +ln.dataset.s + (t.offset || 0);
    if (P.item?.id === id) { seek(at); P.media.play(); } else playList([it], 0, false, at);
  };
  $('#trBack') && ($('#trBack').onclick = () => { S.trOpen = null; $('.tr-layout').classList.remove('reading'); renderTrList(); });
  v.querySelector('.tv-actions').onclick = async e => {
    const a = e.target.closest('[data-a]')?.dataset.a; if (!a) return;
    if (a === 'play') { if (P.item?.id !== id) playList([it], 0, true); else openPlayer(); }
    else if (a === 'copy') copyText(isL ? txtLyrics(t) : txtPlain(t));
    else if (a === 'exp') exportMenu(it, t);
    else if (a === 'redo') transcribeDialog(it);
    else if (a === 'del') {
      if (!await ask('Apagar a transcrição?', 'O arquivo de música/vídeo continua. Você pode fazer de novo depois.', 'Apagar', true)) return;
      await api('/api/transcript/delete', { id }); delete S.tCache[id]; S.trOpen = null;
      $('.tr-layout').classList.remove('reading'); $('#trView').innerHTML = '';
      if (P.item?.id === id) loadPlayerTranscript();
      loadLibrary();
    }
  };
}

function renderDoc(el, t, isL) {
  const idx = [];
  el.innerHTML = t.sections.map(s => {
    if (isL) {
      if (!s.lines.length) return `<div class="sec">${esc(s.label)}</div>`;
      return `<div class="sec">${esc(s.label)}</div>` + s.lines.map(l => `<span class="ln" data-s="${l.s}">${esc(l.t)}</span>`).join('');
    }
    return `<div class="p"><span class="ts" data-s="${s.s}">${fmtTime(s.s + (t.offset || 0))}</span><div>${s.lines.map(l => `<span class="ln" data-s="${l.s}">${esc(l.t)}</span>`).join(' ')}</div></div>`;
  }).join('');
  $$('.ln', el).forEach((n, i) => idx.push({ el: n }));
  let k = 0;
  for (const s of t.sections) for (const l of s.lines) { if (idx[k]) { idx[k].s = l.s; idx[k].e = l.e; } k++; }
  return idx;
}
const txtLyrics = t => t.sections.map(s => `[${s.label}]` + (s.lines.length ? '\n' + s.lines.map(l => l.t).join('\n') : '')).join('\n\n');
const txtPlain = t => t.sections.map(s => `[${fmtTime(s.s + (t.offset || 0))}] ` + s.lines.map(l => l.t).join(' ')).join('\n\n');
function copyText(s) {
  (navigator.clipboard?.writeText(s) || Promise.reject()).then(() => toast('Texto copiado'))
    .catch(() => { if (Android?.copy) { Android.copy(s); toast('Texto copiado'); } else toast('Não consegui copiar', true); });
}
function exportMenu(it, t) {
  const isL = t.mode === 'lyrics';
  const opts = isL
    ? [['lrc', 'Letra sincronizada (.lrc)', 'Para players como Poweramp, foobar, MusicBee'], ['txt', 'Texto (.txt)', 'Letra com versos e refrão'], ['srt', 'Legenda (.srt)', ''], ['vtt', 'Legenda web (.vtt)', '']]
    : [['srt', 'Legenda (.srt)', 'Funciona no VLC, players de vídeo e editores'], ['vtt', 'Legenda web (.vtt)', 'Para sites e players da web'], ['txt', 'Transcrição (.txt)', 'Texto com o tempo de cada parágrafo'], ['lrc', 'Sincronizado (.lrc)', '']];
  openSheet(`<h3>Exportar</h3><p>${S.local ? 'O arquivo vai para a pasta Downloads do computador.' : 'O arquivo é salvo neste aparelho.'}${isL ? ' O .lrc já fica salvo ao lado da música.' : ' O .srt já fica salvo ao lado do vídeo.'}</p>
    <div class="menu">${opts.map(([f, l, d]) => `<button data-f="${f}">${icon('save')}<span>${l}${d ? `<br><small class="muted">${d}</small>` : ''}</span></button>`).join('')}</div>`, (s, close) => {
    s.onclick = e => {
      const f = e.target.closest('[data-f]')?.dataset.f; if (!f) return;
      close();
      const url = exportURL(it.id, f, true), name = it.file.split(/[\\/]/).pop().replace(/\.[^.]+$/, '') + '.' + f;
      if (Android?.download) { Android.download(url, name); toast('Salvando no celular…'); return; }
      const a = document.createElement('a'); a.href = url; a.download = name; document.body.appendChild(a); a.click(); a.remove();
    };
  });
}

// ---------------------------------------------------------------- ajustes
let setT = null;
function saveSettingsSoon() {
  clearTimeout(setT);
  setT = setTimeout(async () => {
    try { S.settings = await api('/api/settings', S.settings); toast('Ajustes salvos'); } catch (e) { toast(e.message, true); }
  }, 400);
}

function renderSettingsTools() {
  const el = $('#toolsBox'); if (!el) return;
  el.innerHTML = S.tools.map(t => `<div class="tool"><div class="tn">${esc(t.label)}<small>${t.busy ? 'Baixando… ' + Math.round(t.progress * 100) + '%' : t.error ? esc(t.error) : t.ready ? 'Pronto' : 'Ainda não baixado · ' + esc(t.size)}</small>
      ${t.busy ? `<div class="bar"><i style="width:${Math.round(t.progress * 100)}%"></i></div>` : ''}</div>
      ${t.ready ? `<span class="ok">${icon('check')}</span>` : t.busy ? '' : `<button class="btn tiny" data-prep="${esc(t.key)}">Baixar</button>`}</div>`).join('') +
    `<div class="row gap" style="margin-top:12px"><button class="btn small" id="updYt">${icon('retry')}Atualizar o yt-dlp</button></div>
     <p class="muted small" style="margin:10px 0 0">Se um download começar a falhar, atualize o yt-dlp: o YouTube muda sempre.</p>`;
  el.onclick = e => {
    const p = e.target.closest('[data-prep]')?.dataset.prep;
    if (p) api('/api/tools/prepare', { what: p }).then(() => toast('Baixando… acompanhe aqui.'));
    if (e.target.closest('#updYt')) api('/api/tools/update', {}).then(() => toast('Atualizando o yt-dlp…'));
  };
}

async function renderSettings() {
  const st = S.settings;
  if (!st || !st.audioDir) return;
  const sel = (id, opts, v) => `<select class="select" id="${id}">${opts.map(([k, l]) => `<option value="${k}" ${String(k) === String(v) ? 'selected' : ''}>${l}</option>`).join('')}</select>`;
  $('#settings').innerHTML = `
    <div class="box"><h3>${icon('down')}Downloads</h3><p>Valem como padrão. Dá para trocar em cada download.</p>
      <div class="field">Formato das músicas${sel('sFmt', [['mp3', 'MP3 (toca em tudo)'], ['m4a', 'M4A (qualidade original)'], ['opus', 'Opus (qualidade original, menor)']], st.audioFmt)}</div>
      <div class="field">Qualidade do MP3${sel('sKbps', [[320, '320 kbps (máxima)'], [256, '256 kbps'], [192, '192 kbps'], [128, '128 kbps (menor)']], st.audioKbps)}</div>
      <div class="field">Qualidade dos vídeos${sel('sQual', [['best', 'Melhor possível'], ['2160', '4K'], ['1440', '1440p'], ['1080', 'Full HD 1080p'], ['720', 'HD 720p'], ['480', '480p'], ['360', '360p']], st.videoQual)}</div>
      <div class="field">Downloads ao mesmo tempo${sel('sPar', [[1, '1'], [2, '2'], [3, '3'], [4, '4']], st.parallel)}</div>
      <div class="field">Pasta das músicas<div class="row"><input type="text" id="sAudioDir" value="${esc(st.audioDir)}" ${S.local ? '' : 'disabled'}>${S.local ? `<button class="icon-btn small" data-open="audio" title="Abrir">${icon('folder')}</button>` : ''}</div></div>
      <div class="field">Pasta dos vídeos<div class="row"><input type="text" id="sVideoDir" value="${esc(st.videoDir)}" ${S.local ? '' : 'disabled'}>${S.local ? `<button class="icon-btn small" data-open="video" title="Abrir">${icon('folder')}</button>` : ''}</div></div>
    </div>
    <div class="box"><h3>${icon('ai')}Letras e legendas com IA</h3><p>O Whisper (IA) roda no seu computador, sem enviar nada para a internet.</p>
      <div class="field">Qualidade da IA${sel('sModel', [['otimo', 'Ótima (recomendada · 574 MB)'], ['bom', 'Boa (488 MB)'], ['rapido', 'Rápida (148 MB, menos precisa)']], st.model)}</div>
      <div class="field">Idioma${sel('sLang', Object.entries(LANGS), st.language)}</div>
      <label class="check" style="margin-bottom:12px"><input type="checkbox" id="sAutoL" ${st.autoLyrics ? 'checked' : ''}><i></i>Fazer a letra sozinho ao baixar música</label>
      <label class="check"><input type="checkbox" id="sAutoS" ${st.autoSubs ? 'checked' : ''}><i></i>Fazer legenda e transcrição ao baixar vídeo</label>
    </div>
    <div class="box"><h3>${icon('gear')}Ferramentas</h3><p>Baixadas sozinhas no computador na primeira vez.</p><div id="toolsBox"></div></div>
    ${S.local ? `<div class="box" id="phoneBox"><h3>${icon('phone')}Usar no celular</h3><p>Baixe e ouça pelo celular, de casa ou de qualquer lugar.</p><div class="spinner"></div></div>`
      : `<div class="box"><h3>${icon('phone')}Este aparelho</h3><p>Conectado ao computador <b>${esc(S.name)}</b>.</p>
        <button class="btn small danger" id="unpair">Desconectar este aparelho</button></div>`}
    <div class="box"><h3>${icon('music')}Sobre</h3><p>Baixa Martins ${esc(S.version)} · use só para conteúdo que você tem direito de baixar.</p>
      ${S.local ? `<div class="row gap"><button class="btn small" data-open="config">${icon('folder')}Pasta de configurações</button><a class="btn small ghost" href="${S.base}/api/log" target="_blank">Ver registro</a></div>` : ''}</div>`;
  renderSettingsTools();
  const bind = (id, key, num) => { const e = $('#' + id); e.onchange = () => { S.settings[key] = e.type === 'checkbox' ? e.checked : num ? +e.value : e.value; saveSettingsSoon(); }; };
  bind('sFmt', 'audioFmt'); bind('sKbps', 'audioKbps', 1); bind('sQual', 'videoQual'); bind('sPar', 'parallel', 1);
  bind('sAudioDir', 'audioDir'); bind('sVideoDir', 'videoDir'); bind('sModel', 'model'); bind('sLang', 'language');
  bind('sAutoL', 'autoLyrics'); bind('sAutoS', 'autoSubs');
  $$('[data-open]').forEach(b => b.onclick = () => api('/api/open', { folder: b.dataset.open }).catch(e => toast(e.message, true)));
  $('#unpair') && ($('#unpair').onclick = async () => {
    if (!await ask('Desconectar?', 'Para usar de novo, vai precisar do código do computador.', 'Desconectar', true)) return;
    try { await api('/api/unpair', {}); } catch { }
    store.set('bm.conn', null); S.token = ''; es?.close(); stopPlayer(); showPair();
  });
  if (S.local) renderPhoneBox();
}

async function renderPhoneBox(action) {
  const box = $('#phoneBox'); if (!box) return;
  let info;
  try { info = await api(action ? '/api/' + action : '/api/pairinfo', action ? {} : undefined); } catch (e) { return; }
  const addr = [...(info.tailscale || []).map(a => [a, 'Tailscale · funciona de qualquer lugar']), ...(info.lan || []).map(a => [a, 'só na rede de casa'])];
  box.innerHTML = `<h3>${icon('phone')}Usar no celular</h3><p>Baixe e ouça pelo celular, de casa ou de qualquer lugar.</p>
    <div class="muted small">Código de acesso</div><div class="code">${esc(info.code)}</div>
    <div class="muted small" style="margin-bottom:6px">Endereço do computador</div>
    ${addr.length ? addr.map(([a, d]) => `<div class="addr">${esc(a)}<small>${d}</small></div>`).join('') : '<p class="muted">Nenhuma rede encontrada.</p>'}
    <ol class="muted small" style="padding-left:18px;margin:12px 0">
      <li>Instale o <b>BaixaMartins.apk</b> no celular e abra.</li>
      <li>Digite o endereço e o código acima.</li>
      <li>Sem o app: no navegador do celular abra <b>http://${esc(addr[0]?.[0] || 'endereço')}:${info.port}</b></li>
      ${(info.tailscale || []).length ? '' : '<li>Para usar fora de casa: instale o <b>Tailscale</b> neste PC e no celular, com a mesma conta.</li>'}
    </ol>
    <div class="row gap" style="flex-wrap:wrap"><button class="btn small" id="newCode">Gerar código novo</button>
    <button class="btn small ghost danger" id="clearDev">Desconectar ${info.devices === 1 ? 'o aparelho' : `os ${info.devices} aparelhos`}</button></div>`;
  $('#newCode').onclick = () => renderPhoneBox('newcode');
  $('#clearDev').onclick = async () => { if (await ask('Desconectar todos?', 'Os celulares vão precisar do código de novo.', 'Desconectar', true)) renderPhoneBox('cleardevices'); };
  $('#clearDev').classList.toggle('hidden', !info.devices);
}

// ---------------------------------------------------------------- player
const aud = $('#aud'), vid = $('#vid');
const P = {
  queue: [], idx: -1, item: null, media: aud, playing: false, t: null, idx2: null, flat: [], instr: [],
  shuffle: false, repeat: store.get('bm.repeat', 'off'), audioOnly: false, cc: store.get('bm.cc', true), lyrMode: true,
  cur: -1, userScroll: 0, editing: false, pendingSeek: null, history: [],
};

function playList(list, i, open, at) {
  P.queue = list.map(x => x.id);
  P.history = [];
  load(Math.max(0, i), at);
  if (open) openPlayer();
}
function playNext(it) {
  if (!P.item) return playList([it], 0, false);
  P.queue.splice(P.idx + 1, 0, it.id);
  toast('Vai tocar depois desta');
}

function stopPlayer() {
  aud.pause(); vid.pause(); aud.removeAttribute('src'); vid.removeAttribute('src'); aud.load(); vid.load();
  P.item = null; P.queue = []; P.idx = -1; P.playing = false;
  $('#mini').classList.add('hidden'); closePlayer();
  Android?.mediaState?.(JSON.stringify({ stop: true }));
  if ('mediaSession' in navigator) navigator.mediaSession.metadata = null;
}

function load(i, at = 0) {
  const it = S.libById[P.queue[i]];
  if (!it) return;
  if (P.item && P.item.id !== it.id) P.history.push(P.idx);
  P.idx = i; P.item = it;
  const useVid = it.kind === 'video' && !P.audioOnly;
  const el = useVid ? vid : aud, other = useVid ? aud : vid;
  other.pause(); other.removeAttribute('src');
  P.media = el;
  el.src = mediaURL(it.id);
  P.pendingSeek = at > 0 ? at : null;
  el.play().catch(() => { });
  const pl = $('#player');
  pl.dataset.kind = it.kind;
  $('#plTitle').textContent = it.title;
  $('#plArtist').textContent = it.artist + (it.playlist ? ' · ' + it.playlist : '');
  $('#plSrc').textContent = it.playlist ? 'Tocando de ' + it.playlist : (it.kind === 'audio' ? 'Tocando música' : 'Tocando vídeo');
  const cover = thumbURL(it.videoId, 1);
  $('#plCover').src = cover;
  $('#plBg').style.backgroundImage = `url('${cover}')`;
  $('.pl-media').style.setProperty('--cover', `url('${cover}')`);
  $('#miniCover').src = cover;
  $('#miniTitle').textContent = it.title;
  $('#miniLine').textContent = it.artist; $('#miniLine').classList.remove('lyric');
  $('#mini').classList.remove('hidden');
  $('#aAudio').classList.toggle('on', P.audioOnly);
  if (it.missing) toast('O arquivo não está mais no computador.', true);
  setupMediaSession(it, cover);
  placeLyrPanel();
  loadPlayerTranscript();
  renderLibrary();
}

async function loadPlayerTranscript() {
  const it = P.item; if (!it) return;
  P.t = null; P.flat = []; P.instr = []; P.cur = -1;
  setEditing(false);
  $('#syncBar').classList.add('hidden');
  const isL = it.kind === 'audio';
  $('#lyrTitle').textContent = isL ? 'Letra' : 'Transcrição';
  $('#aLyrics span').textContent = isL ? 'Letra' : 'Transcrição';
  $('#lyrSearchBox').classList.toggle('hidden', isL);
  $('#lyrMeta').textContent = '';
  if (it.transcript) {
    try { P.t = await getTranscript(it.id); } catch { P.t = null; }
    if (P.item !== it) return;
  }
  renderPlayerLyrics();
}

function renderPlayerLyrics() {
  const el = $('#lyr'), it = P.item, t = P.t;
  const hasT = !!(t && t.sections.length);
  $('#lyrTools').querySelectorAll('.icon-btn').forEach(b => b.classList.toggle('hidden', !hasT));
  $('#subs').classList.toggle('hidden', !(hasT && P.cc && it.kind === 'video'));
  $('#aCC').classList.toggle('on', hasT && P.cc);
  if (!hasT) {
    el.className = 'lyr';
    renderLyrJob();
    return;
  }
  const isL = t.mode === 'lyrics';
  $('#lyrMeta').textContent = '· ' + langName(t.lang) + (t.translated ? ' (traduzido)' : '') + ' · IA';
  el.className = 'lyr ' + (isL ? 'lyrics' : 'transcript');
  P.flat = []; P.instr = [];
  let html = '';
  t.sections.forEach((s, si) => {
    if (isL) {
      html += `<div class="sec" data-kind="${s.kind}" data-si="${si}">${esc(s.label)}</div>`;
      if (!s.lines.length) { html += `<div class="instr" data-ii="${P.instr.length}"><i>♪</i> <i>♪</i> <i>♪</i></div>`; P.instr.push({ s: s.s, e: s.e }); return; }
      s.lines.forEach((l, li) => {
        html += `<div class="ln" data-k="${P.flat.length}" data-key="${si}.${li}">${(l.w && l.w.length ? l.w : [{ w: l.t, s: l.s, e: l.e }]).map(w => `<span class="w">${esc(w.w)}</span>`).join(' ')}</div>`;
        P.flat.push({ s: l.s, e: l.e, t: l.t, w: l.w && l.w.length ? l.w : [{ w: l.t, s: l.s, e: l.e }] });
      });
    } else {
      html += `<div class="p"><span class="ts" data-at="${s.s}">${fmtTime(s.s + (t.offset || 0))}</span><div>` +
        s.lines.map((l, li) => { const k = P.flat.length; P.flat.push({ s: l.s, e: l.e, t: l.t, w: [] }); return `<span class="ln" data-k="${k}" data-key="${si}.${li}">${esc(l.t)}</span>`; }).join(' ') + '</div></div>';
    }
  });
  el.innerHTML = html;
  P.lnEls = $$('.ln', el);
  P.wEls = P.lnEls.map(n => $$('.w', n));
  P.instrEls = $$('.instr', el);
  P.cur = -1;
  $('#syncVal').textContent = fmtOffset(t.offset || 0);
  tick(true);
}

function renderLyrJob() {
  if (!P.item || (P.t && P.t.sections.length)) return;
  const el = $('#lyr'), it = P.item, isA = it.kind === 'audio';
  const j = activeTrJob(it.id);
  if (j) {
    el.innerHTML = `<div class="lyr-empty">${icon('ai')}<b>${isA ? 'A IA está escrevendo a letra…' : 'A IA está transcrevendo…'}</b>
      <span>${esc(j.state === 'fila' ? 'Na fila, logo começa' : j.phase)}</span>
      <div class="bar ${j.progress < 0.01 ? 'indet' : ''}"><i style="width:${Math.round(j.progress * 100)}%"></i></div>
      <div class="live">${(j.lines || []).map(l => `<div>${esc(l.t)}</div>`).join('')}</div></div>`;
  } else if (!el.querySelector('.lyr-empty [data-a=tr]')) {
    el.innerHTML = `<div class="lyr-empty">${icon(isA ? 'mic' : 'text')}<b>${isA ? 'Ainda sem letra' : 'Ainda sem transcrição'}</b>
      <span>${isA ? 'A IA ouve a música e escreve a letra sincronizada, com versos e refrão.' : 'A IA escreve tudo o que é falado e cria a legenda automática.'}</span><br>
      <button class="btn primary" data-a="tr">${icon('ai')}${isA ? 'Fazer a letra com IA' : 'Gerar transcrição e legenda'}</button></div>`;
    el.querySelector('[data-a=tr]').onclick = () => transcribeDialog(it);
  }
}

function fmtOffset(o) { return (o > 0 ? '+' : o < 0 ? '−' : '') + Math.abs(o).toFixed(1).replace('.', ',') + 's'; }

// tempo atual -> linha atual
function findLine(t) {
  const f = P.flat; if (!f.length) return -1;
  let lo = 0, hi = f.length - 1, ans = -1;
  while (lo <= hi) { const m = (lo + hi) >> 1; if (f[m].s <= t) { ans = m; lo = m + 1; } else hi = m - 1; }
  if (ans < 0) return -1;
  const l = f[ans], next = f[ans + 1];
  const hold = next ? Math.min(next.s, l.e + 2.5) : l.e + 2.5;
  return t <= hold ? ans : -1;
}

let lastTick = 0;
function tick(force) {
  const m = P.media, it = P.item;
  if (!it) return;
  const ct = m.currentTime || 0, dur = m.duration || it.duration || 0;
  // barra
  const now = performance.now();
  if (force || now - lastTick > 200) {
    lastTick = now;
    if (!seeking) { const sk = $('#plSeek'); sk.value = dur ? Math.round(ct / dur * 1000) : 0; sk.style.setProperty('--v', (dur ? ct / dur * 100 : 0) + '%'); }
    $('#plCur').textContent = fmtTime(ct); $('#plDur').textContent = fmtTime(dur);
    $('#miniProg').style.width = (dur ? ct / dur * 100 : 0) + '%';
  }
  if (!P.t) { updatePip(null); return; }
  const off = P.t.offset || 0, t = ct - off;
  const k = findLine(t);
  const isL = P.t.mode === 'lyrics';
  if (k !== P.cur) {
    const prev = P.lnEls?.[P.cur];
    if (prev) { prev.classList.remove('now'); P.wEls[P.cur]?.forEach(w => { w.classList.remove('done'); w.style.removeProperty('--p'); }); }
    P.cur = k;
    if (P.lnEls) P.lnEls.forEach((n, i) => n.classList.toggle('past', k >= 0 ? i < k : P.flat[i].e < t));
    const cur = P.lnEls?.[k];
    if (cur) { cur.classList.add('now'); autoScroll(cur); }
    // legenda e mini player
    const line = k >= 0 ? P.flat[k].t : '';
    $('#subs span').textContent = line;
    const ml = $('#miniLine');
    if (line) { ml.textContent = line; ml.classList.add('lyric'); } else { ml.textContent = it.artist; ml.classList.remove('lyric'); }
    // transcrição aberta na aba Transcrições
    if (S.trOpen === it.id && trIdx) {
      trIdx.forEach((x, i) => x.el.classList.toggle('now', i === k));
      const n = trIdx[k]?.el;
      if (n && S.tab === 'transcricoes') { const r = n.getBoundingClientRect(); if (r.top < 80 || r.bottom > innerHeight - 160) n.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
    }
    Android?.lyric?.(line);
  }
  // karaokê: preenche cada palavra
  if (isL && k >= 0 && P.wEls?.[k]) {
    const ws = P.flat[k].w, els = P.wEls[k];
    for (let i = 0; i < els.length && i < ws.length; i++) {
      const w = ws[i], d = Math.max(0.05, w.e - w.s);
      const p = clamp((t - w.s) / d, 0, 1);
      if (p >= 1) { if (!els[i].classList.contains('done')) els[i].classList.add('done'); }
      else { els[i].classList.remove('done'); els[i].style.setProperty('--p', p.toFixed(3)); }
    }
  }
  // instrumental
  if (P.instrEls) P.instr.forEach((x, i) => P.instrEls[i].classList.toggle('now', t >= x.s && t < x.e));
  updatePip(k >= 0 ? P.flat[k].t : '', P.flat[k + 1]?.t || '');
}

function autoScroll(n) {
  if (P.editing || performance.now() - P.userScroll < 3000) return;
  const box = n.closest('.lyr');
  if (!box || !box.offsetParent) return;
  const top = n.offsetTop - box.clientHeight * (P.t?.mode === 'lyrics' ? 0.32 : 0.4);
  box.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
}
['wheel', 'touchmove'].forEach(ev => $('#lyr').addEventListener(ev, () => { P.userScroll = performance.now(); }, { passive: true }));

let rafOn = false;
function loop() {
  if (!P.playing) { rafOn = false; return; }
  tick();
  if (document.hidden) { rafOn = false; return; }
  requestAnimationFrame(loop);
}
function startLoop() { if (!rafOn) { rafOn = true; requestAnimationFrame(loop); } }
setInterval(() => { if (P.playing && document.hidden) tick(); }, 250); // segundo plano / mini tela

function onPlayState() {
  P.playing = !P.media.paused;
  const ic = P.playing ? 'pause' : 'play';
  $('#plPlay').innerHTML = icon(ic); $('#miniPlay').innerHTML = icon(ic);
  $('#player').classList.toggle('paused', !P.playing);
  $$('.song.now, .card.now').forEach(n => n.classList.toggle('paused', !P.playing));
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = P.playing ? 'playing' : 'paused';
  if (P.playing) startLoop();
  pushAndroidState();
}
for (const el of [aud, vid]) {
  el.addEventListener('play', () => el === P.media && onPlayState());
  el.addEventListener('pause', () => el === P.media && onPlayState());
  el.addEventListener('loadedmetadata', () => {
    if (el !== P.media) return;
    if (P.pendingSeek != null) { el.currentTime = P.pendingSeek; P.pendingSeek = null; }
    updatePositionState(); tick(true);
  });
  el.addEventListener('seeked', () => { if (el === P.media) { updatePositionState(); tick(true); pushAndroidState(); } });
  el.addEventListener('timeupdate', () => { if (el === P.media && (document.hidden || !P.playing)) tick(); });
  el.addEventListener('ended', () => el === P.media && next(true));
  el.addEventListener('error', () => {
    if (el !== P.media || !el.getAttribute('src')) return;
    toast(P.item?.missing ? 'O arquivo não está mais no computador.' : 'Não consegui tocar este arquivo.', true);
  });
}

function togglePlay() { if (!P.item) return; P.media.paused ? P.media.play().catch(() => { }) : P.media.pause(); }
function seek(t) { const m = P.media; m.currentTime = clamp(t, 0, (m.duration || P.item?.duration || 0) - 0.05); }
function next(auto) {
  if (!P.queue.length) return;
  if (auto && P.repeat === 'one') { seek(0); P.media.play(); return; }
  let i;
  if (P.shuffle && P.queue.length > 1) { do { i = Math.floor(Math.random() * P.queue.length); } while (i === P.idx); }
  else i = P.idx + 1;
  if (i >= P.queue.length) {
    if (P.repeat === 'all') i = 0;
    else { if (auto) { P.media.pause(); seek(0); } return; }
  }
  load(i);
}
function prev() {
  if (P.media.currentTime > 3 || !P.queue.length) return seek(0);
  let i = P.shuffle && P.history.length ? P.history.pop() : P.idx - 1;
  if (i < 0) i = P.repeat === 'all' ? P.queue.length - 1 : 0;
  load(i);
  if (P.shuffle) P.history.pop();
}

function setupMediaSession(it, cover) {
  if (!('mediaSession' in navigator)) return;
  const abs = new URL(cover, location.href).href;
  navigator.mediaSession.metadata = new MediaMetadata({ title: it.title, artist: it.artist, album: it.album || it.playlist || 'Baixa Martins', artwork: [{ src: abs, sizes: '512x512', type: 'image/jpeg' }] });
  const h = {
    play: () => P.media.play(), pause: () => P.media.pause(), previoustrack: prev, nexttrack: () => next(),
    seekbackward: d => seek(P.media.currentTime - (d.seekOffset || 10)), seekforward: d => seek(P.media.currentTime + (d.seekOffset || 10)),
    seekto: d => seek(d.seekTime), enterpictureinpicture: () => enterPip(),
  };
  for (const [k, f] of Object.entries(h)) { try { navigator.mediaSession.setActionHandler(k, f); } catch { } }
}
function updatePositionState() {
  try { const m = P.media; if (m.duration && 'setPositionState' in navigator.mediaSession) navigator.mediaSession.setPositionState({ duration: m.duration, position: Math.min(m.currentTime, m.duration), playbackRate: m.playbackRate }); } catch { }
}

// ponte com o APK: notificação, tela bloqueada, fones bluetooth, mini tela
function pushAndroidState() {
  if (!Android?.mediaState || !P.item) return;
  const it = P.item;
  Android.mediaState(JSON.stringify({
    title: it.title, artist: it.artist, playing: P.playing, kind: P.media === vid ? 'video' : 'audio',
    pos: Math.round((P.media.currentTime || 0) * 1000), dur: Math.round((P.media.duration || it.duration || 0) * 1000),
    art: new URL(thumbURL(it.videoId, 1), location.href).href, w: it.width || 16, h: it.height || 9,
  }));
}
window.mediaCmd = c => {
  if (c === 'toggle') togglePlay();
  else if (c === 'play') P.media.play();
  else if (c === 'pause') P.media.pause();
  else if (c === 'next') next();
  else if (c === 'prev') prev();
  else if (c.startsWith('seek:')) seek(+c.slice(5) / 1000);
  return true;
};
window.onPip = on => {
  document.body.classList.toggle('pip', !!on);
  if (on && P.item) { openPlayer(); }
  const pl = document.querySelector('.pip-lyric');
  if (on && !pl) $('.pl-media').insertAdjacentHTML('beforeend', '<div class="pip-lyric"><div class="pt"></div><div class="pc"></div><div class="pn"></div></div>');
  tick(true);
};
// link compartilhado pelo app do YouTube (Compartilhar › Baixa Martins)
let pendingShared = null;
window.sharedLink = t => {
  if (!started) { pendingShared = t; return; }
  goTab('baixar'); closePlayer();
  $('#q').value = t; doResolve();
};
// botão voltar do Android
window.appBack = () => {
  if (sheetClose) { sheetClose(); return true; }
  if (!$('#player').classList.contains('hidden')) { closePlayer(); return true; }
  if (S.tab === 'transcricoes' && S.trOpen && innerWidth < 900) { S.trOpen = null; $('.tr-layout').classList.remove('reading'); renderTrList(); return true; }
  if (S.tab !== 'baixar') { goTab('baixar'); return true; }
  return false;
};

// ---- abrir/fechar
function openPlayer() {
  if (!P.item) return;
  $('#player').classList.remove('hidden');
  placeLyrPanel();
  P.cur = -1; tick(true);
  history.pushState({ player: 1 }, '');
}
function closePlayer() {
  if ($('#player').classList.contains('hidden')) return;
  $('#player').classList.add('hidden');
  setEditing(false);
  if (history.state?.player) history.back();
}
addEventListener('popstate', () => { if (!$('#player').classList.contains('hidden')) { $('#player').classList.add('hidden'); setEditing(false); } });

// letra: no celular, no lugar da capa; no PC, ao lado
function placeLyrPanel() {
  const panel = $('#lyrPanel'), mobile = innerWidth < 900;
  const target = mobile && P.item?.kind === 'audio' ? $('#lyrMobileHost') : $('.pl-grid');
  if (panel.parentElement !== target) target.appendChild(panel);
  $('#player').classList.toggle('lyr-mode', P.lyrMode);
  $('#aLyrics').classList.toggle('on', P.lyrMode && (mobile || P.item?.kind === 'audio'));
}
addEventListener('resize', () => { placeLyrPanel(); });

$('#mini').onclick = e => { if (!e.target.closest('button')) openPlayer(); };
$('#miniPlay').onclick = togglePlay; $('#miniNext').onclick = () => next(); $('#miniPrev').onclick = prev;
$('#plClose').onclick = closePlayer;
$('#plPlay').onclick = togglePlay;
$('#plNext').onclick = () => next(); $('#plPrev').onclick = prev;
$('#plBack').onclick = () => seek(P.media.currentTime - 10);
$('#plFwd').onclick = () => seek(P.media.currentTime + 10);
$('#plShuffle').onclick = () => { P.shuffle = !P.shuffle; $('#plShuffle').classList.toggle('on', P.shuffle); toast(P.shuffle ? 'Aleatório ligado' : 'Aleatório desligado'); };
function renderRepeat() { const b = $('#plRepeat'); b.classList.toggle('on', P.repeat === 'all'); b.classList.toggle('one', P.repeat === 'one'); }
$('#plRepeat').onclick = () => { P.repeat = { off: 'all', all: 'one', one: 'off' }[P.repeat]; store.set('bm.repeat', P.repeat); renderRepeat(); toast({ off: 'Repetir desligado', all: 'Repetir a lista', one: 'Repetir esta' }[P.repeat]); };
renderRepeat();
let seeking = false;
const sk = $('#plSeek');
sk.oninput = () => { seeking = true; const d = P.media.duration || P.item?.duration || 0; $('#plCur').textContent = fmtTime(sk.value / 1000 * d); sk.style.setProperty('--v', sk.value / 10 + '%'); };
sk.onchange = () => { const d = P.media.duration || P.item?.duration || 0; seek(sk.value / 1000 * d); seeking = false; };
$('#plMore').onclick = () => P.item && itemMenu(P.item);
$('#aSave').onclick = () => { if (!P.item) return; if (S.local) api('/api/open', { id: P.item.id }).catch(e => toast(e.message, true)); else saveToDevice(P.item); };
$('#aSave span').textContent = 'Salvar';
$('#aLyrics').onclick = () => {
  P.lyrMode = !P.lyrMode || innerWidth >= 900;
  placeLyrPanel();
  if (innerWidth >= 900 || P.item?.kind === 'video') { const p = $('#lyrPanel'); p.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  P.cur = -1; tick(true);
};
$('#aCC').onclick = () => {
  if (!P.t) { P.item && transcribeDialog(P.item); return; }
  P.cc = !P.cc; store.set('bm.cc', P.cc);
  $('#aCC').classList.toggle('on', P.cc);
  $('#subs').classList.toggle('hidden', !P.cc);
  toast(P.cc ? 'Legenda ligada' : 'Legenda desligada');
};
// vídeo -> só áudio (para ouvir com a tela desligada, gastando menos)
$('#aAudio').onclick = () => {
  if (!P.item || P.item.kind !== 'video') return;
  const t = P.media.currentTime, was = !P.media.paused;
  P.audioOnly = !P.audioOnly;
  load(P.idx, t);
  if (!was) setTimeout(() => P.media.pause(), 50);
  toast(P.audioOnly ? 'Só o áudio: pode desligar a tela' : 'Vídeo de volta');
};
$('#vidFull').onclick = () => {
  const box = $('#plVideoBox');
  if (document.fullscreenElement) document.exitFullscreen();
  else (box.requestFullscreen ? box.requestFullscreen() : vid.webkitEnterFullscreen?.());
  Android?.fullscreen?.(!document.fullscreenElement);
};
vid.addEventListener('dblclick', () => $('#vidFull').click());
vid.addEventListener('click', togglePlay);

// cliques na letra/transcrição
$('#lyr').onclick = e => {
  if (P.editing) return;
  const ts = e.target.closest('[data-at]');
  if (ts) { seek(+ts.dataset.at + (P.t?.offset || 0)); P.media.play(); return; }
  const ln = e.target.closest('.ln'); if (!ln || !P.flat.length) return;
  const l = P.flat[+ln.dataset.k];
  seek(l.s + (P.t?.offset || 0)); P.media.play();
  P.userScroll = 0;
};

// procurar dentro da transcrição
$('#lyrSearch').oninput = e => {
  const q = e.target.value.trim().toLowerCase();
  P.lnEls?.forEach((n, i) => {
    const t = P.flat[i].t;
    const k = q ? t.toLowerCase().indexOf(q) : -1;
    n.innerHTML = k >= 0 ? esc(t.slice(0, k)) + '<mark>' + esc(t.slice(k, k + q.length)) + '</mark>' + esc(t.slice(k + q.length)) : esc(t);
  });
  const first = $('#lyr mark');
  if (first) { P.userScroll = performance.now(); first.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
};

// sincronia
let syncT = null;
$('#lyrSync').onclick = () => $('#syncBar').classList.toggle('hidden');
$('#syncBar').onclick = e => {
  const d = e.target.closest('[data-d]')?.dataset.d; if (!d || !P.t) return;
  P.t.offset = Math.round(((P.t.offset || 0) + +d) * 10) / 10;
  $('#syncVal').textContent = fmtOffset(P.t.offset);
  P.cur = -1; tick(true);
  clearTimeout(syncT);
  const id = P.item.id, off = P.t.offset;
  syncT = setTimeout(() => api('/api/transcript/edit', { id, offset: off }).then(t => { S.tCache[id] = fixT(t); }).catch(er => toast(er.message, true)), 700);
};

// corrigir a letra
function setEditing(on) {
  P.editing = on;
  $('#editBar').classList.toggle('hidden', !on);
  const el = $('#lyr');
  el.classList.toggle('edit', on);
  $$('.ln', el).forEach(n => { n.contentEditable = on ? 'plaintext-only' : 'false'; if (on) n.textContent = P.flat[+n.dataset.k]?.t || n.textContent; });
  $$('.sec', el).forEach(n => n.contentEditable = on ? 'plaintext-only' : 'false');
}
$('#lyrEdit').onclick = () => { if (!P.t) return; if (P.editing) { setEditing(false); renderPlayerLyrics(); } else { setEditing(true); toast('Toque na linha para corrigir'); } };
$('#editCancel').onclick = () => { setEditing(false); renderPlayerLyrics(); };
$('#editSave').onclick = async () => {
  const edits = {}, labels = {};
  $$('#lyr .ln').forEach(n => { edits[n.dataset.key] = n.textContent.replace(/\s+/g, ' ').trim(); });
  $$('#lyr .sec').forEach(n => { labels[n.dataset.si] = n.textContent.trim(); });
  try {
    const t = await api('/api/transcript/edit', { id: P.item.id, edits, labels });
    S.tCache[P.item.id] = fixT(t); P.t = S.tCache[P.item.id];
    setEditing(false); renderPlayerLyrics(); toast('Correção salva');
    if (S.trOpen === P.item.id) openTr(P.item.id);
  } catch (e) { toast(e.message, true); }
};
$('#lyrExport').onclick = () => P.t && exportMenu(P.item, P.t);

// ---------------------------------------------------------------- mini tela (picture-in-picture)
let docPip = null, canvasPip = false, pipTimer = null;
const pipCanvas = $('#pipCanvas'), pipVideo = $('#pipVideo');
const pipImg = new Image(); pipImg.crossOrigin = 'anonymous';

async function enterPip() {
  if (!P.item) return;
  // APK: a janela inteira vira mini tela (vídeo ou letra)
  if (Android?.enterPip) { Android.enterPip(P.media === vid ? (P.item.width || 16) : 1, P.media === vid ? (P.item.height || 9) : 1); return; }
  try {
    if (P.media === vid) {
      if (document.pictureInPictureElement === vid) return document.exitPictureInPicture();
      await vid.requestPictureInPicture();
      return;
    }
    // música no PC (Chrome/Edge): janelinha com a letra ao vivo
    if (window.documentPictureInPicture) {
      if (docPip) { docPip.close(); return; }
      docPip = await documentPictureInPicture.requestWindow({ width: 380, height: 420 });
      buildDocPip(docPip);
      docPip.addEventListener('pagehide', () => { docPip = null; });
      tick(true);
      return;
    }
    // música no celular (Chrome Android): vídeo desenhado com capa + letra
    if (document.pictureInPictureElement === pipVideo) return document.exitPictureInPicture();
    pipImg.src = thumbURL(P.item.videoId, 1);
    drawPipCanvas();
    if (!pipVideo.srcObject) pipVideo.srcObject = pipCanvas.captureStream(15);
    await pipVideo.play();
    await pipVideo.requestPictureInPicture();
    canvasPip = true;
    clearInterval(pipTimer); pipTimer = setInterval(drawPipCanvas, 200);
  } catch (e) {
    toast('Este navegador não deixa abrir a mini tela para música. Use o vídeo, o app (APK) ou o Chrome.', true);
  }
}
pipVideo.addEventListener('leavepictureinpicture', () => { canvasPip = false; clearInterval(pipTimer); });
pipVideo.addEventListener('pause', () => { if (canvasPip) { P.media.pause(); pipVideo.play().catch(() => { }); } });
$('#aPip').onclick = enterPip;
vid.addEventListener('enterpictureinpicture', () => $('#aPip').classList.add('on'));
vid.addEventListener('leavepictureinpicture', () => $('#aPip').classList.remove('on'));

function buildDocPip(w) {
  const d = w.document;
  [...document.styleSheets].forEach(ss => { try { const st = d.createElement('style'); st.textContent = [...ss.cssRules].map(r => r.cssText).join('\n'); d.head.appendChild(st); } catch { const l = d.createElement('link'); l.rel = 'stylesheet'; l.href = ss.href; d.head.appendChild(l); } });
  d.head.insertAdjacentHTML('beforeend', `<style>
    body{margin:0;background:#0c0a14;overflow:hidden} .dp{position:fixed;inset:0;display:flex;flex-direction:column;padding:16px;gap:10px}
    .dp-bg{position:fixed;inset:-40px;background:center/cover;filter:blur(40px) brightness(.4)} .dp>*{position:relative}
    .dp-top{display:flex;gap:10px;align-items:center} .dp-top img{width:52px;height:52px;border-radius:10px;object-fit:cover}
    .dp-top b{display:block;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis} .dp-top span{font-size:12px;color:#a49dbf}
    .dp-l{flex:1;display:flex;flex-direction:column;justify-content:center;gap:10px;text-align:center}
    .dp-c{font-size:24px;font-weight:800;line-height:1.25} .dp-n{font-size:16px;font-weight:700;color:rgba(245,243,255,.4)}
    .dp-ctl{display:flex;justify-content:center;gap:10px;align-items:center}
    .dp-bar{height:4px;border-radius:2px;background:rgba(255,255,255,.15)} .dp-bar i{display:block;height:100%;background:linear-gradient(90deg,#8b5cff,#ff3d7f);border-radius:2px}</style>`);
  d.body.innerHTML = document.querySelector('body > svg').outerHTML + `<div class="dp"><div class="dp-bg"></div>
    <div class="dp-top"><img><div style="min-width:0"><b></b><span></span></div></div>
    <div class="dp-l"><div class="dp-c"></div><div class="dp-n"></div></div>
    <div class="dp-bar"><i></i></div>
    <div class="dp-ctl"><button class="icon-btn" data-c="prev">${icon('prev')}</button><button class="play-btn" data-c="toggle" style="width:54px;height:54px">${icon('pause')}</button><button class="icon-btn" data-c="next">${icon('next')}</button></div></div>`;
  d.body.onclick = e => { const c = e.target.closest('[data-c]')?.dataset.c; if (c) window.mediaCmd(c); };
}

function updatePip(cur, nxt = '') {
  const it = P.item; if (!it) return;
  if (document.body.classList.contains('pip')) {
    const b = $('.pip-lyric');
    if (b) { b.querySelector('.pt').textContent = it.title; b.querySelector('.pc').textContent = cur || (P.t ? '♪' : it.title); b.querySelector('.pn').textContent = cur ? nxt : it.artist; }
  }
  if (docPip) {
    const d = docPip.document;
    const img = d.querySelector('.dp-top img'); const src = thumbURL(it.videoId, 1);
    if (img && img.getAttribute('src') !== src) { img.src = src; d.querySelector('.dp-bg').style.backgroundImage = `url('${src}')`; }
    const set = (s, v) => { const n = d.querySelector(s); if (n && n.textContent !== v) n.textContent = v; };
    set('.dp-top b', it.title); set('.dp-top span', it.artist);
    set('.dp-c', cur || (P.t ? '♪' : it.title)); set('.dp-n', cur ? nxt : (P.t ? '' : it.artist));
    const bar = d.querySelector('.dp-bar i'); if (bar) bar.style.width = (P.media.duration ? P.media.currentTime / P.media.duration * 100 : 0) + '%';
    const pb = d.querySelector('[data-c=toggle]'); const want = P.playing ? 'pause' : 'play';
    if (pb && pb.dataset.ic !== want) { pb.dataset.ic = want; pb.innerHTML = icon(want); }
  }
  P.pipLine = [cur, nxt];
}

function wrapText(ctx, text, maxW) {
  const words = String(text).split(/\s+/); const lines = []; let cur = '';
  for (const w of words) { const t = cur ? cur + ' ' + w : w; if (ctx.measureText(t).width > maxW && cur) { lines.push(cur); cur = w; } else cur = t; }
  if (cur) lines.push(cur);
  return lines;
}
function drawPipCanvas() {
  const c = pipCanvas, ctx = c.getContext('2d'), W = c.width, H = c.height, it = P.item;
  if (!it) return;
  ctx.fillStyle = '#0c0a14'; ctx.fillRect(0, 0, W, H);
  if (pipImg.complete && pipImg.naturalWidth) {
    ctx.save(); ctx.filter = 'blur(28px) brightness(0.45)';
    const s = Math.max(W / pipImg.naturalWidth, H / pipImg.naturalHeight) * 1.2;
    ctx.drawImage(pipImg, (W - pipImg.naturalWidth * s) / 2, (H - pipImg.naturalHeight * s) / 2, pipImg.naturalWidth * s, pipImg.naturalHeight * s);
    ctx.restore();
    const cs = 120, sw = Math.min(pipImg.naturalWidth, pipImg.naturalHeight);
    ctx.save(); ctx.beginPath(); ctx.roundRect(36, 36, cs, cs, 18); ctx.clip();
    ctx.drawImage(pipImg, (pipImg.naturalWidth - sw) / 2, (pipImg.naturalHeight - sw) / 2, sw, sw, 36, 36, cs, cs); ctx.restore();
  }
  ctx.fillStyle = '#fff'; ctx.font = '700 30px system-ui, sans-serif';
  ctx.fillText(wrapText(ctx, it.title, W - 210)[0] || '', 176, 86);
  ctx.fillStyle = '#a49dbf'; ctx.font = '500 24px system-ui, sans-serif';
  ctx.fillText(wrapText(ctx, it.artist, W - 210)[0] || '', 176, 124);
  const [cur, nxt] = P.pipLine || ['', ''];
  ctx.textAlign = 'center';
  ctx.fillStyle = '#fff'; ctx.font = '800 46px system-ui, sans-serif';
  const ls = wrapText(ctx, cur || (P.t ? '♪' : ''), W - 70).slice(0, 3);
  let y = 330 - (ls.length - 1) * 28;
  ls.forEach(l => { ctx.fillText(l, W / 2, y); y += 56; });
  ctx.fillStyle = 'rgba(245,243,255,.45)'; ctx.font = '700 30px system-ui, sans-serif';
  wrapText(ctx, nxt || '', W - 70).slice(0, 2).forEach(l => { ctx.fillText(l, W / 2, y + 14); y += 38; });
  ctx.textAlign = 'left';
  const d = P.media.duration || 0, p = d ? P.media.currentTime / d : 0;
  ctx.fillStyle = 'rgba(255,255,255,.18)'; ctx.fillRect(36, H - 50, W - 72, 8);
  const g = ctx.createLinearGradient(36, 0, W - 36, 0); g.addColorStop(0, '#8b5cff'); g.addColorStop(1, '#ff3d7f');
  ctx.fillStyle = g; ctx.fillRect(36, H - 50, (W - 72) * p, 8);
}

// atalhos de teclado (PC)
addEventListener('keydown', e => {
  if (e.target.closest('input, textarea, select, [contenteditable=true], [contenteditable=plaintext-only]')) return;
  if (!P.item) return;
  if (e.code === 'Space') { e.preventDefault(); togglePlay(); }
  else if (e.code === 'ArrowRight') seek(P.media.currentTime + 5);
  else if (e.code === 'ArrowLeft') seek(P.media.currentTime - 5);
  else if (e.key === 'n') next();
  else if (e.key === 'p') enterPip();
  else if (e.key === 'Escape' && !sheetClose) closePlayer();
});
document.addEventListener('visibilitychange', () => { if (!document.hidden && P.playing) startLoop(); });

boot();
