'use strict';
/* Controle Martins — app do celular.
   Fala com o programa do PC por WebSocket (ws://endereço:47800/ws). */

const $ = (s) => document.querySelector(s);
const $$ = (s) => Array.from(document.querySelectorAll(s));
const DEFAULT_PORT = 47800;
const vibrate = (ms = 12) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };

// ---------------- Armazenamento ----------------
const store = {
  get(k, def) { try { const v = localStorage.getItem('cm.' + k); return v ? JSON.parse(v) : def; } catch (e) { return def; } },
  set(k, v) { try { localStorage.setItem('cm.' + k, JSON.stringify(v)); } catch (e) {} },
};
let pcs = store.get('pcs', []);
const savePcs = () => store.set('pcs', pcs);
const DEFAULT_PROGS = [
  { name: 'Navegador', cmd: 'https://www.google.com', icon: '🌐' },
  { name: 'YouTube', cmd: 'https://www.youtube.com', icon: '▶' },
  { name: 'Arquivos', cmd: 'explorer', icon: '📁' },
  { name: 'Bloco de notas', cmd: 'notepad', icon: '📝' },
  { name: 'Calculadora', cmd: 'calc', icon: '🧮' },
  { name: 'Configurações', cmd: 'ms-settings:', icon: '⚙' },
];
let progs = store.get('progs', DEFAULT_PROGS);
const saveProgs = () => store.set('progs', progs);

const QUALITY = {
  eco: { w: 960, q: 45, fps: 8 },
  normal: { w: 1280, q: 60, fps: 15 },
  alta: { w: 1920, q: 75, fps: 20 },
};

// Rodando direto no navegador, aberto pelo endereço do PC (não no APK)
const inBrowserOnPc = location.protocol.startsWith('http') && location.hostname !== 'appassets.androidplatform.net' && !!location.port;

function hostFor(h) { return h.includes(':') && !h.startsWith('[') ? `[${h}]` : h; }
function baseUrl(pc) { return `http://${hostFor(pc.host)}:${pc.port || DEFAULT_PORT}`; }
function wsUrl(pc) { return `ws://${hostFor(pc.host)}:${pc.port || DEFAULT_PORT}/ws`; }

// ---------------- Navegação ----------------
let current = null; // computador aberto
const views = ['home', 'add', 'pc'];
function show(id) {
  views.forEach((v) => $('#' + v).classList.toggle('active', v === id));
}

let toastT;
function toast(msg, ms = 2600) {
  const t = $('#toast');
  t.textContent = msg; t.hidden = false;
  clearTimeout(toastT); toastT = setTimeout(() => (t.hidden = true), ms);
}

// ---------------- Sheets ----------------
let openSheet = null;
function sheet(id) {
  closeSheet();
  openSheet = $('#' + id);
  openSheet.hidden = false; $('#sheetBg').hidden = false;
}
function closeSheet() {
  if (openSheet) openSheet.hidden = true;
  openSheet = null; $('#sheetBg').hidden = true;
}
$('#sheetBg').addEventListener('click', closeSheet);

function confirmBox(title, text, yesLabel, danger) {
  return new Promise((res) => {
    $('#cTitle').textContent = title; $('#cText').textContent = text;
    const y = $('#cYes');
    y.textContent = yesLabel || 'Confirmar';
    y.style.background = danger ? 'var(--danger)' : '';
    sheet('confirm');
    const done = (v) => { y.onclick = $('#cNo').onclick = null; closeSheet(); res(v); };
    y.onclick = () => done(true);
    $('#cNo').onclick = () => done(false);
  });
}

// ---------------- Lista ----------------
const PC_ICON = '<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/></svg>';
function esc(s) { return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

function renderHome() {
  const list = $('#pcList');
  list.innerHTML = '';
  pcs.forEach((pc) => {
    const b = document.createElement('button');
    b.className = 'pc-card';
    b.innerHTML = `<div class="ic">${PC_ICON}</div><div class="info"><div class="nm">${esc(pc.name)}</div><div class="ad">${esc(pc.host)}</div></div><span class="dot"></span>`;
    b.onclick = () => openPc(pc);
    list.appendChild(b);
    // está ligado?
    fetch(baseUrl(pc) + '/api/info', { signal: AbortSignal.timeout ? AbortSignal.timeout(4000) : undefined })
      .then((r) => r.json()).then((j) => { if (j.app === 'controle-martins') b.querySelector('.dot').classList.add('ok'); })
      .catch(() => b.querySelector('.dot').classList.add('off'));
  });
  $('#emptyHome').hidden = pcs.length > 0;
}

// ---------------- Adicionar / parear ----------------
function guessDevice() {
  const m = navigator.userAgent.match(/Android [\d.]+; ([^;)]+)/);
  if (m && m[1] && m[1] !== 'K' && !/wv/.test(m[1])) return m[1].trim();
  return 'Celular';
}

let repairPc = null;
function openAdd(prefill) {
  repairPc = prefill || null;
  $('#fHost').value = prefill ? prefill.host : (inBrowserOnPc ? location.hostname : '');
  $('#fPort').value = prefill ? (prefill.port || DEFAULT_PORT) : (inBrowserOnPc ? location.port : DEFAULT_PORT);
  $('#fCode').value = '';
  $('#fDevice').value = store.get('device', guessDevice());
  $('#addErr').hidden = true;
  show('add');
  setTimeout(() => ($('#fHost').value ? $('#fCode') : $('#fHost')).focus(), 50);
}
$('#addBtn').onclick = () => openAdd();

$('#addForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const err = $('#addErr'); err.hidden = true;
  let host = $('#fHost').value.trim().replace(/^https?:\/\//, '').replace(/\/.*$/, '');
  let port = parseInt($('#fPort').value, 10) || DEFAULT_PORT;
  const hp = host.match(/^([^:\]]+):(\d+)$/);
  if (hp) { host = hp[1]; port = parseInt(hp[2], 10); }
  const code = $('#fCode').value.trim();
  const device = $('#fDevice').value.trim() || 'Celular';
  if (!host || !code) return;
  store.set('device', device);
  const pcTmp = { host, port };
  const btn = $('#addSubmit'); btn.disabled = true; btn.textContent = 'Procurando o computador…';
  const fail = (msg) => { err.textContent = msg; err.hidden = false; btn.disabled = false; btn.textContent = 'Parear'; };
  try {
    const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), 7000);
    let info;
    try {
      info = await (await fetch(baseUrl(pcTmp) + '/api/info', { signal: ctl.signal })).json();
    } finally { clearTimeout(t); }
    if (info.app !== 'controle-martins') return fail('Nesse endereço tem outra coisa, não o Controle Martins.');
    btn.textContent = 'Pareando…';
    const r = await fetch(baseUrl(pcTmp) + '/api/parear', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ code, device }),
    });
    const j = await r.json();
    if (!r.ok) return fail(j.erro || 'Não deu certo.');
    let pc = repairPc && pcs.find((p) => p.id === repairPc.id);
    if (pc) { pc.token = j.token; pc.host = host; pc.port = port; }
    else {
      pc = { id: Date.now().toString(36), name: j.name || info.name || host, host, port, token: j.token, quality: 'normal', sens: 1.6 };
      pcs.push(pc);
    }
    savePcs();
    btn.disabled = false; btn.textContent = 'Parear';
    openPc(pc);
  } catch (ex) {
    fail('Não achei o computador nesse endereço.\n\n• Confira se o PC está ligado e o Controle Martins aberto (ícone perto do relógio).\n• Fora de casa: o Tailscale precisa estar ligado no celular e no PC.\n• Na primeira vez, o Windows pode perguntar se libera o acesso à rede: clique em Permitir.');
  }
});

// ---------------- Conexão ----------------
let ws = null, wsTimer = null, pingTimer = null, retry = 0, lastPong = 0;
let info = null, monIndex = 0;

function setStatus(kind, text) {
  const s = $('#pcStatus');
  s.className = 'status ' + (kind || '');
  s.querySelector('span').textContent = text;
}

function send(obj) {
  if (ws && ws.readyState === 1) { ws.send(JSON.stringify(obj)); return true; }
  return false;
}

function connect() {
  disconnect(true);
  const pc = current; if (!pc) return;
  setStatus('', 'Conectando…');
  let sock;
  try { sock = new WebSocket(wsUrl(pc)); } catch (e) { scheduleRetry(); return; }
  ws = sock;
  sock.binaryType = 'blob';
  sock.onopen = () => {
    sock.send(JSON.stringify({ t: 'auth', token: pc.token }));
  };
  sock.onmessage = (ev) => {
    if (sock !== ws) return;
    if (typeof ev.data !== 'string') { onFrame(ev.data); return; }
    let m; try { m = JSON.parse(ev.data); } catch (e) { return; }
    if (m.t === 'info') {
      retry = 0; info = m;
      setStatus('ok', 'Conectado');
      if (m.name && m.name !== pc.name && !pc.renamed) { pc.name = m.name; savePcs(); $('#pcName').textContent = pc.name; }
      $('#monBtn').hidden = !(m.monitors && m.monitors.length > 1);
      if (monIndex >= (m.monitors || []).length) monIndex = 0;
      $('#monBtn').textContent = 'Monitor ' + (monIndex + 1);
      lastPong = Date.now();
      startPing();
      updateStream(true);
    } else if (m.t === 'pong') {
      lastPong = Date.now();
      const ms = Date.now() - m.id;
      setStatus('ok', `Conectado · ${ms} ms`);
    } else if (m.t === 'erro') {
      toast(m.msg);
    }
  };
  sock.onclose = (ev) => {
    if (sock !== ws) return;
    ws = null; stopPing();
    if (ev.code === 4001) {
      setStatus('off', 'Este celular não está mais autorizado');
      screenMsg('Este celular foi desconectado do computador.\nToque aqui para parear de novo.', () => openAdd(current));
      return;
    }
    scheduleRetry();
  };
  sock.onerror = () => {};
}

function scheduleRetry() {
  if (!current) return;
  const wait = Math.min(10000, 1000 * Math.pow(2, retry++));
  setStatus('off', retry > 2 ? 'Sem conexão. Tentando de novo…' : 'Reconectando…');
  if (retry > 2) screenMsg('Sem conexão com o computador.\n\nConfira se ele está ligado, com o Controle Martins aberto,\ne se o Tailscale está ligado no celular.');
  clearTimeout(wsTimer);
  wsTimer = setTimeout(connect, wait);
}

function disconnect(keepCurrent) {
  clearTimeout(wsTimer); stopPing();
  if (ws) { const s = ws; ws = null; try { s.close(); } catch (e) {} }
  frameBusy = false;
  if (!keepCurrent) current = null;
}

function startPing() {
  stopPing();
  const tick = () => {
    if (Date.now() - lastPong > 12000 && ws) { try { ws.close(); } catch (e) {} ws = null; scheduleRetry(); return; }
    send({ t: 'ping', id: Date.now() });
  };
  tick();
  pingTimer = setInterval(tick, 4000);
}
function stopPing() { clearInterval(pingTimer); pingTimer = null; }

// ---------------- Abrir um PC ----------------
let tab = 'pTela';
function openPc(pc) {
  current = pc;
  $('#pcName').textContent = pc.name;
  show('pc');
  setTab(store.get('tab', 'pTela'));
  resetZoom();
  frameSeen = false;
  screenMsg('Carregando a tela…');
  keepScreenOn(true);
  connect();
}

function leavePc() {
  closeKbd(); exitFull();
  disconnect();
  keepScreenOn(false);
  show('home'); renderHome();
}

function keepScreenOn(on) { try { window.Android && Android.keepScreenOn(on); } catch (e) {} }

function setTab(id) {
  tab = id; store.set('tab', id);
  $$('.tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === id));
  $$('.panel').forEach((p) => p.classList.toggle('active', p.id === id));
  updateStream();
  if (id === 'pTela') requestAnimationFrame(layoutScreen);
}
$$('.tabs button').forEach((b) => (b.onclick = () => setTab(b.dataset.tab)));

function updateStream(force) {
  if (!current) return;
  const on = tab === 'pTela' && !document.hidden;
  const q = QUALITY[current.quality] || QUALITY.normal;
  // largura nunca maior do que a tela do celular precisa (economiza internet)
  const dpr = Math.min(window.devicePixelRatio || 1, 3);
  const wrap = $('#screenWrap');
  const need = Math.max(wrap.clientWidth, wrap.clientHeight) * dpr * 1.25;
  const w = Math.round(Math.min(q.w, Math.max(800, need)));
  send({ t: 'tela', on, mon: monIndex, w, q: q.q, fps: q.fps });
  if (force) frameBusy = false;
}
document.addEventListener('visibilitychange', () => updateStream());

// ---------------- Imagem da tela ----------------
const canvas = $('#screen');
const ctx2d = canvas.getContext('2d');
let frameBusy = false, frameSeen = false;
let imgW = 0, imgH = 0;

function screenMsg(text, onTap) {
  const m = $('#screenMsg');
  m.textContent = text || ''; m.hidden = !text;
  m.style.pointerEvents = onTap ? 'auto' : 'none';
  m.onclick = onTap || null;
  m.style.whiteSpace = 'pre-line';
}

async function onFrame(blob) {
  try {
    const bmp = await createImageBitmap(blob);
    if (bmp.width !== imgW || bmp.height !== imgH) {
      imgW = canvas.width = bmp.width; imgH = canvas.height = bmp.height;
      layoutScreen();
    }
    ctx2d.drawImage(bmp, 0, 0);
    bmp.close && bmp.close();
    if (!frameSeen) { frameSeen = true; screenMsg(''); }
  } catch (e) {}
  send({ t: 'ack' });
}

// Zoom / posição da imagem
let fit = 1, zoom = 1, panX = 0, panY = 0;
function layoutScreen() {
  const wrap = $('#screenWrap');
  const W = wrap.clientWidth, H = wrap.clientHeight;
  if (!imgW || !W || !H) return;
  fit = Math.min(W / imgW, H / imgH);
  canvas.style.width = imgW * fit + 'px';
  canvas.style.height = imgH * fit + 'px';
  clampPan();
  applyTransform();
}
function clampPan() {
  const wrap = $('#screenWrap');
  const W = wrap.clientWidth, H = wrap.clientHeight;
  const cw = imgW * fit * zoom, ch = imgH * fit * zoom;
  // centraliza quando sobra espaço; senão não deixa sair da borda
  panX = cw <= W ? (W - cw) / 2 : Math.min(0, Math.max(W - cw, panX));
  panY = ch <= H ? (H - ch) / 2 : Math.min(0, Math.max(H - ch, panY));
}
function applyTransform() {
  canvas.style.transform = `translate(${panX}px, ${panY}px) scale(${zoom})`;
  $('#zoomReset').hidden = zoom <= 1.01;
}
function resetZoom() { zoom = 1; panX = panY = 0; layoutScreen(); }
$('#zoomReset').onclick = resetZoom;
window.addEventListener('resize', () => { layoutScreen(); clearTimeout(window._rs); window._rs = setTimeout(() => updateStream(), 300); });

// ponto do dedo → posição (0..1) na tela do PC
function toScreen(cx, cy) {
  const r = canvas.getBoundingClientRect();
  return { x: Math.min(1, Math.max(0, (cx - r.left) / r.width)), y: Math.min(1, Math.max(0, (cy - r.top) / r.height)) };
}

// ---------------- Toque direto na imagem ----------------
let touchMode = store.get('touchMode', 'toque');
function setTouchMode(m) {
  touchMode = m; store.set('touchMode', m);
  $$('#touchMode button').forEach((b) => b.classList.toggle('on', b.dataset.mode === m));
  $('#telaHint').textContent = m === 'toque'
    ? 'Toque = clique · Segurar = botão direito · Arrastar = arrastar · Dois dedos = rolar ou dar zoom'
    : 'Deslize para mover o cursor · Toque = clique · Dois dedos = rolar · Pinça = zoom';
}
$$('#touchMode button').forEach((b) => (b.onclick = () => setTouchMode(b.dataset.mode)));
setTouchMode(touchMode);

$('#monBtn').onclick = () => {
  const n = (info && info.monitors && info.monitors.length) || 1;
  monIndex = (monIndex + 1) % n;
  $('#monBtn').textContent = 'Monitor ' + (monIndex + 1);
  resetZoom(); updateStream(true);
};

function dist(a, b) { return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY); }
function mid(a, b) { return { x: (a.clientX + b.clientX) / 2, y: (a.clientY + b.clientY) / 2 }; }

const wrapEl = $('#screenWrap');
const direct = { state: null };
// dois dedos: zoom/arrastar a imagem ou rolar
const two = { active: false, mode: null, d0: 0, z0: 1, m0: null, pan0: null, last: null, scrollAcc: 0 };

function twoStart(e) {
  const [a, b] = e.touches;
  Object.assign(two, { active: true, mode: zoom > 1.01 ? 'zoom' : null, d0: dist(a, b), z0: zoom, m0: mid(a, b), pan0: { x: panX, y: panY }, last: mid(a, b) });
}
function twoMove(e) {
  const [a, b] = e.touches;
  const m = mid(a, b), d = dist(a, b);
  if (!two.mode) {
    if (Math.abs(d / two.d0 - 1) > 0.12) two.mode = 'zoom';
    else if (Math.abs(m.y - two.m0.y) > 12 || Math.abs(m.x - two.m0.x) > 12) two.mode = 'scroll';
  }
  if (two.mode === 'zoom') {
    const wrapR = wrapEl.getBoundingClientRect();
    const nz = Math.min(6, Math.max(1, two.z0 * d / two.d0));
    // mantém o ponto entre os dedos parado
    const ox = two.m0.x - wrapR.left, oy = two.m0.y - wrapR.top;
    const ix = (ox - two.pan0.x) / two.z0, iy = (oy - two.pan0.y) / two.z0;
    zoom = nz;
    panX = (m.x - wrapR.left) - ix * nz;
    panY = (m.y - wrapR.top) - iy * nz;
    clampPan(); applyTransform();
  } else if (two.mode === 'scroll') {
    const dy = m.y - two.last.y, dx = m.x - two.last.x;
    send({ t: 'scroll', dy: dy / 40, dx: -dx / 40 });
  }
  two.last = m;
}

wrapEl.addEventListener('touchstart', (e) => {
  e.preventDefault();
  if (touchMode === 'cursor') return padStart(e);
  if (e.touches.length === 2) {
    cancelDirect();
    twoStart(e);
    return;
  }
  if (e.touches.length > 2) return;
  const t = e.touches[0];
  const p = toScreen(t.clientX, t.clientY);
  direct.state = { x0: t.clientX, y0: t.clientY, drag: false, done: false, t0: Date.now() };
  send({ t: 'pos', x: p.x, y: p.y });
  direct.state.timer = setTimeout(() => {
    const s = direct.state;
    if (s && !s.drag && !s.done) { s.done = true; vibrate(25); send({ t: 'btn', b: 'right', a: 'click' }); }
  }, 550);
}, { passive: false });

wrapEl.addEventListener('touchmove', (e) => {
  e.preventDefault();
  if (touchMode === 'cursor') return padMove(e);
  if (two.active && e.touches.length >= 2) return twoMove(e);
  const s = direct.state; if (!s || s.done) return;
  const t = e.touches[0];
  if (!s.drag && Math.hypot(t.clientX - s.x0, t.clientY - s.y0) > 10) {
    s.drag = true; clearTimeout(s.timer);
    send({ t: 'btn', b: 'left', a: 'down' });
  }
  if (s.drag) { const p = toScreen(t.clientX, t.clientY); send({ t: 'pos', x: p.x, y: p.y }); }
}, { passive: false });

function directEnd(e) {
  e.preventDefault();
  if (touchMode === 'cursor') return padEnd(e);
  if (two.active) { if (e.touches.length === 0) two.active = false; return; }
  const s = direct.state; direct.state = null;
  if (!s) return;
  clearTimeout(s.timer);
  if (s.done) return;
  if (s.drag) send({ t: 'btn', b: 'left', a: 'up' });
  else { send({ t: 'btn', b: 'left', a: 'click' }); vibrate(8); }
}
function cancelDirect() {
  const s = direct.state; direct.state = null;
  if (!s) return;
  clearTimeout(s.timer);
  if (s.drag) send({ t: 'btn', b: 'left', a: 'up' });
}
wrapEl.addEventListener('touchend', directEnd, { passive: false });
wrapEl.addEventListener('touchcancel', directEnd, { passive: false });

// mouse de verdade (navegador no PC/notebook): clique direto
wrapEl.addEventListener('mousedown', (e) => { if (e.sourceCapabilities && e.sourceCapabilities.firesTouchEvents) return; const p = toScreen(e.clientX, e.clientY); send({ t: 'pos', x: p.x, y: p.y }); send({ t: 'btn', b: ['left', 'middle', 'right'][e.button] || 'left', a: 'down' }); });
wrapEl.addEventListener('mousemove', (e) => { if (e.buttons) { const p = toScreen(e.clientX, e.clientY); send({ t: 'pos', x: p.x, y: p.y }); } });
wrapEl.addEventListener('mouseup', (e) => { if (e.sourceCapabilities && e.sourceCapabilities.firesTouchEvents) return; send({ t: 'btn', b: ['left', 'middle', 'right'][e.button] || 'left', a: 'up' }); });
wrapEl.addEventListener('contextmenu', (e) => e.preventDefault());
wrapEl.addEventListener('wheel', (e) => { e.preventDefault(); send({ t: 'scroll', dy: -e.deltaY / 100, dx: e.deltaX / 100 }); }, { passive: false });

// ---------------- Touchpad ----------------
const pad = { t: null, moved: false, n: 0, acc: { x: 0, y: 0 }, last: null, pendingTap: null, dragging: false, t0: 0, maxN: 0, startPts: null };

function sens() { return (current && current.sens) || 1.6; }

function padStart(e) {
  const n = e.touches.length;
  pad.maxN = Math.max(pad.maxN, n);
  if (n === 1) {
    pad.t0 = Date.now(); pad.moved = false; pad.maxN = 1;
    const t = e.touches[0];
    pad.last = { x: t.clientX, y: t.clientY }; pad.startPts = { x: t.clientX, y: t.clientY };
    // segundo toque logo depois de um toque: arrastar (ou duplo clique)
    if (pad.pendingTap) {
      clearTimeout(pad.pendingTap); pad.pendingTap = null;
      pad.dragArmed = true;
    } else pad.dragArmed = false;
  } else if (n === 2) {
    const m = mid(e.touches[0], e.touches[1]);
    pad.last2 = m; pad.d0 = dist(e.touches[0], e.touches[1]); pad.z0 = zoom;
    pad.m0 = m; pad.pan0 = { x: panX, y: panY }; pad.mode2 = null;
  }
  e.currentTarget.classList && e.currentTarget.classList.add('active');
}

function padMove(e) {
  const n = e.touches.length;
  if (n === 1 && pad.maxN === 1) {
    const t = e.touches[0];
    let dx = t.clientX - pad.last.x, dy = t.clientY - pad.last.y;
    pad.last = { x: t.clientX, y: t.clientY };
    if (!pad.moved && Math.hypot(t.clientX - pad.startPts.x, t.clientY - pad.startPts.y) < 6) return;
    if (!pad.moved) {
      pad.moved = true;
      if (pad.dragArmed) { pad.dragging = true; send({ t: 'btn', b: 'left', a: 'down' }); vibrate(15); }
    }
    const sp = Math.hypot(dx, dy);
    const k = sens() * (1 + Math.min(sp / 8, 2.5));
    pad.acc.x += dx * k; pad.acc.y += dy * k;
    const ix = Math.trunc(pad.acc.x), iy = Math.trunc(pad.acc.y);
    if (ix || iy) { pad.acc.x -= ix; pad.acc.y -= iy; send({ t: 'mv', dx: ix, dy: iy }); }
  } else if (n === 2) {
    pad.moved = true;
    const m = mid(e.touches[0], e.touches[1]);
    const d = dist(e.touches[0], e.touches[1]);
    // na tela (modo cursor) a pinça dá zoom na imagem
    if (e.currentTarget === wrapEl && !pad.mode2 && Math.abs(d / pad.d0 - 1) > 0.12) pad.mode2 = 'zoom';
    if (pad.mode2 === 'zoom') {
      const wrapR = wrapEl.getBoundingClientRect();
      const nz = Math.min(6, Math.max(1, pad.z0 * d / pad.d0));
      const ix = (pad.m0.x - wrapR.left - pad.pan0.x) / pad.z0, iy = (pad.m0.y - wrapR.top - pad.pan0.y) / pad.z0;
      zoom = nz; panX = m.x - wrapR.left - ix * nz; panY = m.y - wrapR.top - iy * nz;
      clampPan(); applyTransform();
    } else {
      send({ t: 'scroll', dy: (m.y - pad.last2.y) / 25, dx: -(m.x - pad.last2.x) / 25 });
    }
    pad.last2 = m;
  }
}

function padEnd(e) {
  if (e.touches.length > 0) return;
  e.currentTarget.classList && e.currentTarget.classList.remove('active');
  const quick = Date.now() - pad.t0 < 300;
  if (pad.dragging) { pad.dragging = false; send({ t: 'btn', b: 'left', a: 'up' }); }
  else if (!pad.moved && quick && pad.maxN === 2) { send({ t: 'btn', b: 'right', a: 'click' }); vibrate(20); }
  else if (!pad.moved && quick && pad.maxN === 1) {
    if (pad.dragArmed) { send({ t: 'btn', b: 'left', a: 'click' }); send({ t: 'btn', b: 'left', a: 'click' }); vibrate(8); }
    else {
      // espera um pouco: pode ser o começo de um "dois toques e arrastar"
      pad.pendingTap = setTimeout(() => { pad.pendingTap = null; send({ t: 'btn', b: 'left', a: 'click' }); }, 220);
      vibrate(8);
    }
  }
  pad.dragArmed = false; pad.maxN = 0;
}

const padEl = $('#pad');
padEl.addEventListener('touchstart', (e) => { e.preventDefault(); padStart(e); }, { passive: false });
padEl.addEventListener('touchmove', (e) => { e.preventDefault(); padMove(e); }, { passive: false });
padEl.addEventListener('touchend', (e) => { e.preventDefault(); padEnd(e); }, { passive: false });
padEl.addEventListener('touchcancel', (e) => { e.preventDefault(); padEnd(e); }, { passive: false });

// faixa de rolar (um dedo)
const strip = $('#scrollStrip');
let stripY = null;
strip.addEventListener('touchstart', (e) => { e.preventDefault(); stripY = e.touches[0].clientY; strip.classList.add('active'); }, { passive: false });
strip.addEventListener('touchmove', (e) => { e.preventDefault(); const y = e.touches[0].clientY; send({ t: 'scroll', dy: (y - stripY) / 22 }); stripY = y; }, { passive: false });
strip.addEventListener('touchend', () => strip.classList.remove('active'));

// botões do mouse: segurar = manter apertado (dá para arrastar com o outro dedo no touchpad)
[['#mbL', 'left'], ['#mbM', 'middle'], ['#mbR', 'right']].forEach(([sel, b]) => {
  const el = $(sel);
  el.addEventListener('touchstart', (e) => { e.preventDefault(); el.classList.add('down'); vibrate(10); send({ t: 'btn', b, a: 'down' }); }, { passive: false });
  const up = (e) => { e.preventDefault(); if (!el.classList.contains('down')) return; el.classList.remove('down'); send({ t: 'btn', b, a: 'up' }); };
  el.addEventListener('touchend', up, { passive: false });
  el.addEventListener('touchcancel', up, { passive: false });
  el.addEventListener('click', () => { if (!('ontouchstart' in window)) send({ t: 'btn', b, a: 'click' }); });
});

// ---------------- Teclado ----------------
const kbd = $('#kbd'), kin = $('#kbdInput');
const BASE = '    ';
let prevVal = BASE, composing = false;
const mods = new Set();

function openKbd() {
  kbd.hidden = false;
  resetInput();
  kin.style.pointerEvents = 'auto';
  kin.focus({ preventScroll: true });
  kin.style.pointerEvents = 'none';
}
function closeKbd() { kbd.hidden = true; kin.blur(); clearMods(); }
function resetInput() { kin.value = BASE; prevVal = BASE; try { kin.setSelectionRange(BASE.length, BASE.length); } catch (e) {} }
$$('.kbd-open').forEach((b) => (b.onclick = () => (kbd.hidden ? openKbd() : closeKbd())));
$('#kbdClose').onclick = closeKbd;
$('#kbdMore').onclick = () => { $('#kbdFn').hidden = !$('#kbdFn').hidden; $('#kbdMore').classList.toggle('on', !$('#kbdFn').hidden); kin.focus({ preventScroll: true }); };
// tocar numa tecla da barra não fecha o teclado do celular
kbd.addEventListener('mousedown', (e) => { if (e.target !== kin) e.preventDefault(); });
kbd.addEventListener('touchend', (e) => {
  const b = e.target.closest('button'); if (!b) return;
  e.preventDefault(); b.click();
  if (b.id !== 'kbdClose') kin.focus({ preventScroll: true });
});

function clearMods() { mods.clear(); $$('[data-mod]').forEach((b) => b.classList.remove('on')); }
$$('[data-mod]').forEach((b) => (b.onclick = () => {
  const m = b.dataset.mod;
  if (mods.has(m)) mods.delete(m); else mods.add(m);
  b.classList.toggle('on', mods.has(m)); vibrate(8);
}));
function pressKey(k) {
  send({ t: 'key', k, mods: Array.from(mods) });
  clearMods(); vibrate(8);
}
$$('[data-k]').forEach((b) => (b.onclick = () => pressKey(b.dataset.k)));

kin.addEventListener('compositionstart', () => (composing = true));
kin.addEventListener('compositionend', () => { composing = false; setTimeout(onKbdInput, 0); });
kin.addEventListener('input', () => onKbdInput());
function onKbdInput() {
  const v = kin.value;
  if (v === prevVal) return;
  let p = 0;
  while (p < v.length && p < prevVal.length && v[p] === prevVal[p]) p++;
  const removed = prevVal.length - p;
  const added = v.slice(p);
  prevVal = v;
  for (let i = 0; i < removed; i++) send({ t: 'key', k: 'backspace' });
  if (added) {
    if (mods.size) {
      // Ctrl+C etc.: manda como atalho
      for (const ch of added) send({ t: 'key', k: ch.toLowerCase(), mods: Array.from(mods) });
      clearMods();
    } else {
      const parts = added.replace(/ /g, ' ').split('\n');
      parts.forEach((part, i) => {
        if (i > 0) send({ t: 'key', k: 'enter' });
        if (part) send({ t: 'txt', s: part });
      });
    }
  }
  if (!composing && (v.length < BASE.length || !v.startsWith(BASE) || v.length > 80 || added.includes('\n') || mods.size)) resetInput();
}
// teclado físico / Enter do Gboard sem texto
kin.addEventListener('keydown', (e) => {
  const map = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', Escape: 'esc', Tab: 'tab', Delete: 'delete', Home: 'home', End: 'end', PageUp: 'pgup', PageDown: 'pgdn' };
  if (map[e.key]) { e.preventDefault(); pressKey(map[e.key]); }
  else if (e.key === 'Enter' && !composing) { e.preventDefault(); pressKey('enter'); resetInput(); }
  else if (e.key === 'Backspace' && kin.value.length <= BASE.length && !composing) { e.preventDefault(); pressKey('backspace'); resetInput(); }
});

// ---------------- Atalhos ----------------
$$('[data-key]').forEach((b) => (b.onclick = () => {
  vibrate(10);
  send({ t: 'key', k: b.dataset.key, mods: b.dataset.mods ? b.dataset.mods.split(',') : [] });
}));

const ACT_CONFIRM = {
  desligar: ['Desligar o computador?', 'Depois de desligado, não dá para ligar de novo pelo celular. Alguém vai precisar apertar o botão do PC.', 'Desligar', true],
  reiniciar: ['Reiniciar o computador?', 'O Controle Martins volta sozinho quando o Windows entrar na sua conta. Se o Windows pedir senha ao ligar, alguém vai precisar digitar no PC (veja como ativar o login automático no guia).', 'Reiniciar', true],
  bloquear: ['Bloquear o computador?', 'O Windows vai para a tela de senha. Enquanto estiver bloqueado, a tela não aparece no celular.', 'Bloquear', false],
};
$$('[data-act]').forEach((b) => (b.onclick = async () => {
  const a = b.dataset.act;
  if (ACT_CONFIRM[a]) {
    const [t, x, y, d] = ACT_CONFIRM[a];
    if (!(await confirmBox(t, x, y, d))) return;
  }
  vibrate(10);
  if (send({ t: 'acao', a })) {
    if (a === 'tela_off') toast('Tela do PC desligada. Mexa o mouse para ligar de novo.');
    if (a === 'desligar') toast('Desligando o computador…');
    if (a === 'reiniciar') toast('Reiniciando… o app reconecta sozinho quando o PC voltar.', 4000);
  } else toast('Sem conexão com o computador');
}));

let progEditing = false, progEditIdx = -1;
function renderProgs() {
  const g = $('#progGrid'); g.innerHTML = '';
  progs.forEach((p, i) => {
    const b = document.createElement('button');
    b.className = 'tile';
    b.innerHTML = `<b>${esc(p.icon || '▶')}</b>${esc(p.name)}${progEditing ? '<span class="x">editar</span>' : ''}`;
    b.onclick = () => {
      if (progEditing) return editProg(i);
      vibrate(10);
      if (send({ t: 'abrir', s: p.cmd })) toast('Abrindo ' + p.name + '…', 1500);
      else toast('Sem conexão com o computador');
    };
    g.appendChild(b);
  });
  const add = document.createElement('button');
  add.className = 'tile add'; add.innerHTML = '<b>+</b>Adicionar';
  add.onclick = () => editProg(-1);
  g.appendChild(add);
  $('#progEdit').textContent = progEditing ? 'Pronto' : 'Editar';
}
$('#progEdit').onclick = () => { progEditing = !progEditing; renderProgs(); };
function editProg(i) {
  progEditIdx = i;
  const p = progs[i] || { name: '', cmd: '' };
  $('#pgTitle').textContent = i < 0 ? 'Novo atalho' : 'Editar atalho';
  $('#pgName').value = p.name; $('#pgCmd').value = p.cmd;
  $('#pgDel').hidden = i < 0;
  sheet('progSheet');
}
$('#pgSave').onclick = () => {
  const name = $('#pgName').value.trim(), cmd = $('#pgCmd').value.trim();
  if (!name || !cmd) return toast('Preencha o nome e o que abrir');
  const icon = /^https?:/i.test(cmd) ? '🌐' : '▶';
  if (progEditIdx < 0) progs.push({ name, cmd, icon });
  else progs[progEditIdx] = { ...progs[progEditIdx], name, cmd, icon: progs[progEditIdx].cmd === cmd ? progs[progEditIdx].icon : icon };
  saveProgs(); closeSheet(); renderProgs();
};
$('#pgDel').onclick = () => { progs.splice(progEditIdx, 1); saveProgs(); closeSheet(); renderProgs(); };
renderProgs();

// ---------------- Ajustes ----------------
$('#settingsBtn').onclick = () => {
  const pc = current; if (!pc) return;
  $('#sName').value = pc.name;
  $$('#sQuality button').forEach((b) => b.classList.toggle('on', b.dataset.q === (pc.quality || 'normal')));
  $('#sSens').value = pc.sens || 1.6; $('#sSensOut').textContent = (+$('#sSens').value).toFixed(1) + '×';
  $('#sAddr').textContent = `Endereço: ${pc.host}:${pc.port || DEFAULT_PORT}` + (info && info.version ? ` · Controle Martins ${info.version} no PC` : '');
  sheet('settings');
};
$$('#sQuality button').forEach((b) => (b.onclick = () => {
  current.quality = b.dataset.q; savePcs();
  $$('#sQuality button').forEach((x) => x.classList.toggle('on', x === b));
  updateStream(true);
}));
$('#sSens').oninput = () => { current.sens = +$('#sSens').value; $('#sSensOut').textContent = current.sens.toFixed(1) + '×'; savePcs(); };
$('#sDone').onclick = () => {
  const n = $('#sName').value.trim();
  if (n && n !== current.name) { current.name = n; current.renamed = true; $('#pcName').textContent = n; }
  savePcs(); closeSheet();
};
$('#sRemove').onclick = async () => {
  const pc = current;
  closeSheet();
  if (!(await confirmBox('Remover ' + pc.name + '?', 'Este celular deixa de ter acesso a esse computador. Para usar de novo, vai precisar do código de pareamento.', 'Remover', true))) return;
  send({ t: 'sair' });
  setTimeout(() => {
    pcs = pcs.filter((p) => p.id !== pc.id); savePcs();
    leavePc();
  }, 300);
};

// ---------------- Tela cheia ----------------
function enterFull() { $('#pc').classList.add('full'); $('#fullExit').hidden = false; closeKbd(); setTimeout(layoutScreen, 50); try { window.Android && Android.fullscreen(true); } catch (e) {} }
function exitFull() { $('#pc').classList.remove('full'); $('#fullExit').hidden = true; setTimeout(layoutScreen, 50); try { window.Android && Android.fullscreen(false); } catch (e) {} }
$('#fullBtn').onclick = enterFull;
$('#fullExit').onclick = exitFull;

// ---------------- Voltar ----------------
function goBack() {
  if (openSheet) { closeSheet(); return true; }
  if (!kbd.hidden) { closeKbd(); return true; }
  if ($('#pc').classList.contains('full')) { exitFull(); return true; }
  if ($('#pc').classList.contains('active')) { leavePc(); return true; }
  if ($('#add').classList.contains('active')) { show(current ? 'pc' : 'home'); if (!current) renderHome(); return true; }
  return false;
}
window.appBack = goBack; // chamado pelo botão voltar do Android
$$('[data-back]').forEach((b) => (b.onclick = goBack));

// ---------------- Início ----------------
renderHome();
show('home');
// aberto direto pelo endereço do PC: já vai para o pareamento
if (inBrowserOnPc && !pcs.some((p) => p.host === location.hostname)) openAdd();
