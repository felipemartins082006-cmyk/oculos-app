'use strict';
// Controle do jogo: câmera de deus, poderes, primeira pessoa, interface, salvamento e desempenho.
(() => {
  const $ = id => document.getElementById(id);
  const cv = $('gl');
  const { N, NC, CH, HS, SEA, ERAS } = W;
  const SAVE = 'mundo-martins-save', CFG = 'mundo-martins-cfg', DAY = 240;

  const QUAL = {
    leve: { nome: 'Leve', dpr: 0.8, fps: 30, units: 350, far: 34, clouds: false, rebuild: 2 },
    normal: { nome: 'Normal', dpr: 1.3, fps: 60, units: 600, far: 52, clouds: true, rebuild: 3 },
    bonito: { nome: 'Bonito', dpr: 2, fps: 60, units: 900, far: 80, clouds: true, rebuild: 4 },
  };
  const store = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); return true; } catch (e) { return false; } },
  };
  const cfg = Object.assign({ q: 'normal', dia: true, fps: false, sens: 1 }, (() => { try { return JSON.parse(store.get(CFG)) || {}; } catch (e) { return {}; } })());
  if (!QUAL[cfg.q]) cfg.q = 'normal';
  let Q = QUAL[cfg.q];
  W.maxUnits = Q.units;
  const saveCfg = () => store.set(CFG, JSON.stringify(cfg));

  // ---------- início ----------
  let ok = false;
  try { ok = R.init(cv); } catch (e) { console.error(e); }
  if (!ok) {
    $('erro').classList.remove('hidden');
    $('erro').innerHTML = '<b>Seu navegador não liberou o WebGL2.</b><br>Atualize o Chrome/Safari ou ative a aceleração de hardware para jogar.';
    return;
  }
  let loaded = false;
  try { const s = store.get(SAVE); if (s) loaded = W.carregar(s); } catch (e) { console.warn('save inválido', e); loaded = false; }
  if (!loaded) W.novo();

  let chunks = new Array(NC * NC).fill(null);
  let dyn = R.mesh();
  let ocean = makeOcean();
  function makeOcean() { // mar em volta do mapa até o horizonte
    const w = new MeshW(64), y = SEA * HS - 0.02, c = rgb(22, 70, 140, F_AGUA), A = -600, B = N + 600;
    w.tri(A, y, A, B, y, A, B, y, 0, c); w.tri(A, y, A, B, y, 0, A, y, 0, c);
    w.tri(A, y, N, B, y, N, B, y, B, c); w.tri(A, y, N, B, y, B, A, y, B, c);
    w.tri(A, y, 0, 0, y, 0, 0, y, N, c); w.tri(A, y, 0, 0, y, N, A, y, N, c);
    w.tri(N, y, 0, B, y, 0, B, y, N, c); w.tri(N, y, 0, B, y, N, N, y, N, c);
    const m = R.mesh(); R.upload(m, w, false); return m;
  }
  let mode = 'deus', tool = 'olhar', speed = 1, sel = null;
  const cam = { x: N / 2, z: N / 2, d: 46, yaw: 0.7 };
  const fp = { u: null, yaw: 0, pitch: -0.08, vy: 0, jy: 0, gy: 0, joy: null, look: null, ctx: null, ctxT: 0, busy: null, cd: 0 };
  const keys = {};
  const fx = [];
  let shake = 0, flash = 0;

  function focusStart() {
    const c = W.cities.find(k => k.alive);
    if (c) { cam.x = c.x + 0.5; cam.z = c.z + 0.5; cam.d = 30; }
  }
  focusStart();

  cv.addEventListener('webglcontextlost', e => { e.preventDefault(); });
  cv.addEventListener('webglcontextrestored', () => {
    R.init(cv); chunks = new Array(NC * NC).fill(null); dyn = R.mesh(); ocean = makeOcean(); W.dirty.fill(1);
  });

  // ---------- tela ----------
  let dynScale = 1;
  function resize() {
    const dpr = Math.min(window.devicePixelRatio || 1, Q.dpr) * dynScale;
    const w = innerWidth, h = innerHeight;
    cv.width = Math.max(1, Math.round(w * dpr)); cv.height = Math.max(1, Math.round(h * dpr));
  }
  addEventListener('resize', resize); resize();

  // ---------- câmera ----------
  const V3 = {};
  function setupCamera() {
    let eye, target, fov, near, far;
    if (mode === 'fp') {
      const u = fp.u;
      const y = fp.gy + (u.age < 12 ? 0.25 : 0.38) + fp.jy;
      eye = [u.x, y, u.z];
      const cp = Math.cos(fp.pitch);
      target = [u.x + Math.sin(fp.yaw) * cp, y + Math.sin(fp.pitch), u.z + Math.cos(fp.yaw) * cp];
      fov = 1.2; near = 0.03; far = Q.far + 8;
    } else {
      const p = 0.62 + (cam.d - 6) / (170 - 6) * 0.62;
      const ty = Math.max(W.hAt(cam.x, cam.z), SEA * HS);
      eye = [cam.x + Math.sin(cam.yaw) * Math.cos(p) * cam.d, ty + Math.sin(p) * cam.d, cam.z + Math.cos(cam.yaw) * Math.cos(p) * cam.d];
      const gy = W.hAt(eye[0], eye[2]) + 1.2; if (eye[1] < gy) eye[1] = gy;
      if (shake > 0) { eye[0] += (Math.random() - 0.5) * shake; eye[1] += (Math.random() - 0.5) * shake; }
      target = [cam.x, ty, cam.z];
      fov = 1.0; near = Math.max(0.2, cam.d * 0.02); far = cam.d * 4 + 400;
    }
    let fx_ = target[0] - eye[0], fy = target[1] - eye[1], fz = target[2] - eye[2];
    const l = Math.hypot(fx_, fy, fz); fx_ /= l; fy /= l; fz /= l;
    let rx = -fz, rz = fx_; const rl = Math.hypot(rx, rz) || 1; rx /= rl; rz /= rl;
    const ux = -rz * fy, uy = rz * fx_ - rx * fz, uz = rx * fy;
    const asp = cv.width / cv.height;
    Object.assign(V3, { eye, f: [fx_, fy, fz], r: [rx, 0, rz], u: [ux, uy, uz], tanH: Math.tan(fov / 2), asp, far });
    V3.vp = M4.mul(M4.persp(fov, asp, near, far), M4.look(eye, target));
  }
  function pick(px, py) {
    const nx = px / innerWidth * 2 - 1, ny = 1 - py / innerHeight * 2;
    const { eye, f, r, u, tanH, asp } = V3;
    let dx = f[0] + r[0] * nx * tanH * asp + u[0] * ny * tanH, dy = f[1] + r[1] * nx * tanH * asp + u[1] * ny * tanH, dz = f[2] + r[2] * nx * tanH * asp + u[2] * ny * tanH;
    const l = Math.hypot(dx, dy, dz); dx /= l; dy /= l; dz /= l;
    let t = 0;
    for (let i = 0; i < 2500 && t < 600; i++) {
      const x = eye[0] + dx * t, y = eye[1] + dy * t, z = eye[2] + dz * t;
      if (x >= 0 && z >= 0 && x < N && z < N) { if (y <= W.hAt(x, z)) return [x, z]; }
      else if (y < 0) return null;
      t += 0.2 + t * 0.004;
    }
    return null;
  }

  // ---------- luz do dia ----------
  const ss = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
  const mx3 = (a, b, k) => [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k];
  function lighting(time) {
    const ph = cfg.dia ? (W.time / DAY + 0.32) % 1 : 0.42;
    const el = -Math.cos(ph * 6.2832);
    const sun = [-Math.sin(ph * 6.2832) * 0.75, el, 0.45], sl = Math.hypot(...sun);
    const day = ss(-0.18, 0.25, el), night = 1 - ss(-0.12, 0.12, el);
    const L = el > -0.04 ? sun.map(v => v / sl) : [0.3 / 0.94, 0.8 / 0.94, -0.4 / 0.94];
    const sunCol = mx3([0.13, 0.15, 0.26], mx3([0.95, 0.55, 0.32], [0.8, 0.76, 0.68], ss(0.0, 0.45, el)), day);
    let amb = mx3([0.27, 0.29, 0.42], [0.42, 0.45, 0.52], day);
    const sky = mx3([0.03, 0.05, 0.12], mx3([0.95, 0.56, 0.36], [0.56, 0.76, 0.95], ss(0.04, 0.4, el)), day);
    if (flash > 0) amb = amb.map(v => v + flash * 0.9);
    const fog = mode === 'fp' ? [Q.far * 0.45, Q.far] : [cam.d * 1.6 + 50, cam.d * 4 + 380];
    return { vp: V3.vp, sun: L, sunCol, amb, sky, cam: V3.eye, fog, night, time, ph };
  }

  // ---------- pedaços ----------
  function rebuildChunks(max) {
    let n = 0;
    const order = [];
    for (let i = 0; i < NC * NC; i++) if (W.dirty[i] || !chunks[i]) order.push(i);
    if (!order.length) return;
    const cx0 = mode === 'fp' ? fp.u.x : cam.x, cz0 = mode === 'fp' ? fp.u.z : cam.z;
    order.sort((a, b) => dist2c(a, cx0, cz0) - dist2c(b, cx0, cz0));
    for (const i of order) {
      if (n >= max && chunks[i]) break;
      const w = G.buildChunk(i % NC, (i / NC) | 0);
      if (!chunks[i]) chunks[i] = R.mesh();
      R.upload(chunks[i], w, false);
      W.dirty[i] = 0; n++;
    }
  }
  function dist2c(i, x, z) { const cx = (i % NC) * CH + CH / 2, cz = ((i / NC) | 0) * CH + CH / 2; return (cx - x) ** 2 + (cz - z) ** 2; }

  // ---------- efeitos ----------
  function fxRaio(x, z, now) {
    const y0 = W.hAt(x, z), segs = []; let px = x, pz = z;
    for (let i = 0; i < 10; i++) { segs.push([px, pz]); px += (Math.random() - 0.5) * 1.2; pz += (Math.random() - 0.5) * 1.2; }
    fx.push({ end: now + 0.4, draw(w) { for (let i = 0; i < 10; i++) w.box(segs[i][0], y0 + 2.6 * i, segs[i][1], 0.12, 2.7, 0.12, rgb(235, 240, 255, F_EMIS)); } });
    flash = 0.6;
  }
  function fxMeteoro(x, z, now) {
    const y1 = W.hAt(x, z), sx = x + 28, sy = y1 + 60, sz = z - 18, t0 = now;
    fx.push({
      end: now + 1.0, draw(w, t) {
        const k = Math.min(1, (t - t0) / 1.0);
        for (let i = 0; i < 6; i++) {
          const kk = Math.max(0, k - i * 0.025);
          w.box(sx + (x - sx) * kk, sy + (y1 - sy) * kk, sz + (z - sz) * kk, 1.2 - i * 0.15, 1.2 - i * 0.15, 1.2 - i * 0.15, i ? rgb(255, 150 - i * 15, 40, F_EMIS) : rgb(255, 230, 160, F_EMIS));
        }
      },
      done() {
        W.meteoro(x, z); shake = 1.6; flash = 0.8;
        const t1 = performance.now() / 1000, yy = W.hAt(x, z);
        fx.push({ end: t1 + 0.8, draw(w, t) { const k = (t - t1) / 0.8, s = 2 + k * 12; w.box(x, yy - 1, z, s, (1 - k) * 4 + 0.2, s, rgb(255, 160 - k * 100, 50, F_EMIS)); } });
      },
    });
  }

  // ---------- ferramentas ----------
  const TOOLS = [
    { id: 'olhar', ic: '✋', nm: 'Olhar' },
    { id: 'fp', ic: '👁️', nm: '1ª pessoa' },
    { id: 'povo', ic: '🧑', nm: 'Povo' },
    { id: 'erguer', ic: '⛰️', nm: 'Erguer', cont: true },
    { id: 'afundar', ic: '🌊', nm: 'Afundar', cont: true },
    { id: 'arvore', ic: '🌳', nm: 'Floresta', cont: true },
    { id: 'inspirar', ic: '💡', nm: 'Inspirar' },
    { id: 'raio', ic: '⚡', nm: 'Raio' },
    { id: 'meteoro', ic: '☄️', nm: 'Meteoro' },
  ];
  const DICAS = {
    olhar: 'Arraste para mover · pinça para zoom · gire com 2 dedos · toque para ver detalhes',
    fp: 'Toque em uma pessoa para viver como ela',
    povo: 'Toque na terra para criar um povo (perto de uma cidade, chegam moradores)',
    erguer: 'Arraste na terra para levantar montanhas',
    afundar: 'Arraste para cavar lagos e mares',
    arvore: 'Arraste para plantar florestas',
    inspirar: 'Toque em uma cidade para acelerar a ciência do povo',
    raio: 'Toque para lançar um raio (incendeia árvores e casas)',
    meteoro: 'Toque para jogar um meteoro',
  };
  const toolsEl = $('tools');
  toolsEl.innerHTML = TOOLS.map(t => `<button data-t="${t.id}"${t.id === tool ? ' class="on"' : ''}><span>${t.ic}</span><small>${t.nm}</small></button>`).join('');
  toolsEl.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    tool = b.dataset.t;
    for (const k of toolsEl.children) k.classList.toggle('on', k === b);
    hint(DICAS[tool]);
  });
  let hintT = 0;
  function hint(t) { const h = $('dica'); h.textContent = t; h.classList.add('show'); clearTimeout(hintT); hintT = setTimeout(() => h.classList.remove('show'), 3200); }

  function tapTool(px, py) {
    const p = pick(px, py);
    if (!p) return;
    const [x, z] = p, now = performance.now() / 1000;
    switch (tool) {
      case 'olhar': select(x, z); break;
      case 'fp': {
        const u = nearestUnit(x, z, 3);
        if (u) enterFP(u); else hint('Toque perto de uma pessoa');
        break;
      }
      case 'povo': {
        const r = W.addPessoas(x, z);
        if (r === 'agua') hint('Pessoas precisam de terra firme');
        else if (r === 'cheio') hint('O mundo já tem povos demais');
        else if (r === 'cidade') hint('Chegaram novos moradores');
        break;
      }
      case 'inspirar': {
        const r = W.inspirar(x, z);
        if (!r) hint('Toque perto de uma cidade');
        else if (r.max) toastMsg({ txt: `📱 ${r.civ.nm} já está na era atual`, color: r.civ.color });
        else toastMsg({ txt: `💡 O povo ${r.civ.nm} teve uma grande ideia!`, color: r.civ.color });
        break;
      }
      case 'raio': W.raio(x, z); fxRaio(x, z, now); break;
      case 'meteoro': fxMeteoro(x, z, now); break;
      case 'erguer': W.brush(x, z, 3.2, 0.05); break;
      case 'afundar': W.brush(x, z, 3.2, -0.05); break;
      case 'arvore': W.plantar(x, z, 2.2); break;
    }
  }
  function nearestUnit(x, z, r) {
    let best = null, bd = r;
    W.near(x, z, r, o => { const d = Math.hypot(o.x - x, o.z - z); if (o.alive && d < bd) { bd = d; best = o; } });
    return best;
  }
  function select(x, z) {
    // pessoa bem embaixo do dedo > prédio/centro da cidade > pessoa um pouco mais longe
    const close = nearestUnit(x, z, cam.d > 60 ? 1.2 : 0.45);
    if (close) { sel = { k: 'u', u: close }; renderPanel(true); return; }
    let best = null, bd = 2.2;
    for (const c of W.cities) if (c.alive && c.founded) { const d = Math.hypot(c.x + 0.5 - x, c.z + 0.5 - z); if (d < bd) { bd = d; best = c; } }
    const t = W.ti(x, z);
    if (!best && W.bld[t]) { const b = W.B.get(W.bld[t]); if (b) best = W.cities[b.city]; }
    if (best) { sel = { k: 'c', c: best }; renderPanel(true); return; }
    const u = nearestUnit(x, z, cam.d > 60 ? 2.5 : 1.3);
    sel = u ? { k: 'u', u } : null; renderPanel(true);
  }

  // ---------- toque: modo deus ----------
  const ptrs = new Map();
  let gest = null, brush = null, drag = false, downT = 0;
  cv.addEventListener('pointerdown', e => {
    cv.setPointerCapture(e.pointerId);
    ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY });
    if (mode === 'fp') { fpDown(e); return; }
    if (ptrs.size === 1) {
      drag = false; downT = performance.now(); gest = null;
      const t = TOOLS.find(k => k.id === tool);
      brush = t.cont ? { x: e.clientX, y: e.clientY, acc: 1 } : null;
    } else if (ptrs.size === 2) {
      brush = null; drag = true;
      const [a, b] = [...ptrs.values()];
      gest = { d: Math.hypot(a.x - b.x, a.y - b.y), ang: Math.atan2(b.y - a.y, b.x - a.x), mx: (a.x + b.x) / 2, my: (a.y + b.y) / 2 };
    }
  });
  cv.addEventListener('pointermove', e => {
    const p = ptrs.get(e.pointerId); if (!p) return;
    const dx = e.clientX - p.x, dy = e.clientY - p.y;
    p.x = e.clientX; p.y = e.clientY;
    if (mode === 'fp') { fpMove(e, dx, dy); return; }
    if (ptrs.size >= 2 && gest) {
      const [a, b] = [...ptrs.values()];
      const d = Math.hypot(a.x - b.x, a.y - b.y), ang = Math.atan2(b.y - a.y, b.x - a.x), mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
      cam.d = Math.max(6, Math.min(170, cam.d * gest.d / Math.max(d, 1)));
      let da = ang - gest.ang; if (da > Math.PI) da -= 2 * Math.PI; if (da < -Math.PI) da += 2 * Math.PI;
      cam.yaw -= da;
      pan(mx - gest.mx, my - gest.my);
      gest = { d, ang, mx, my };
      return;
    }
    if (Math.hypot(p.x - p.sx, p.y - p.sy) > 8) drag = true;
    if (brush) { brush.x = e.clientX; brush.y = e.clientY; return; }
    if (drag) pan(dx, dy);
  });
  const up = e => {
    const p = ptrs.get(e.pointerId); if (!p) return;
    ptrs.delete(e.pointerId);
    if (mode === 'fp') { fpUp(e); return; }
    if (ptrs.size === 0) {
      const t = TOOLS.find(k => k.id === tool);
      if (!drag && !gest && performance.now() - downT < 600 && !t.cont) tapTool(e.clientX, e.clientY);
      brush = null; gest = null;
    } else gest = null;
  };
  cv.addEventListener('pointerup', up); cv.addEventListener('pointercancel', up);
  cv.addEventListener('wheel', e => { e.preventDefault(); if (mode === 'deus') cam.d = Math.max(6, Math.min(170, cam.d * (e.deltaY > 0 ? 1.12 : 1 / 1.12))); }, { passive: false });
  cv.addEventListener('contextmenu', e => e.preventDefault());

  function pan(dx, dy) {
    const p = 0.62 + (cam.d - 6) / (170 - 6) * 0.62;
    const wpp = 2 * cam.d * V3.tanH / innerHeight;
    const fl = Math.hypot(V3.f[0], V3.f[2]) || 1, fgx = V3.f[0] / fl, fgz = V3.f[2] / fl;
    cam.x += -V3.r[0] * dx * wpp + fgx * dy * wpp / Math.sin(p);
    cam.z += -V3.r[2] * dx * wpp + fgz * dy * wpp / Math.sin(p);
    cam.x = Math.max(0, Math.min(N, cam.x)); cam.z = Math.max(0, Math.min(N, cam.z));
  }

  addEventListener('keydown', e => {
    keys[e.code] = true;
    if (mode === 'fp') {
      if (e.code === 'Space') { fpJump(); e.preventDefault(); }
      if (e.code === 'KeyE') fpAction();
      if (e.code === 'Escape') exitFP();
    }
  });
  addEventListener('keyup', e => { keys[e.code] = false; });

  // ---------- primeira pessoa ----------
  function enterFP(u) {
    if (!u || !u.alive) return;
    if (fp.u) fp.u.ctrl = false;
    W.abandon(u); u.st = 'idle'; u.ctrl = true;
    fp.u = u; fp.yaw = u.dir; fp.pitch = -0.08; fp.vy = 0; fp.jy = 0; fp.gy = W.hAt(u.x, u.z); fp.busy = null; fp.ctxT = 0;
    if (mode !== 'fp') { mode = 'fp'; if (speed > 1) setSpeed(1); }
    sel = null; renderPanel(true);
    document.body.classList.add('modo-fp');
    $('fp-hud').classList.remove('hidden');
    fpMsg(`Agora você é ${u.nm}`);
    updateFpCard();
  }
  function exitFP(msg) {
    const u = fp.u;
    if (u) { u.ctrl = false; u.st = 'idle'; u.t = 0.5; cam.x = u.x; cam.z = u.z; }
    cam.d = Math.min(cam.d, 18);
    fp.u = null; fp.joy = null; fp.look = null; mode = 'deus';
    document.body.classList.remove('modo-fp');
    $('fp-hud').classList.add('hidden'); $('joy').classList.add('hidden');
    if (msg) toastMsg({ txt: msg });
  }
  function fpDown(e) {
    if (e.clientX < innerWidth * 0.45 && !fp.joy) {
      fp.joy = { id: e.pointerId, ox: e.clientX, oy: e.clientY, vx: 0, vy: 0 };
      const j = $('joy'); j.style.left = e.clientX + 'px'; j.style.top = e.clientY + 'px'; j.classList.remove('hidden');
      j.firstElementChild.style.transform = 'translate(-50%,-50%)';
      $('fp-dica').classList.add('hide');
    } else if (!fp.look) fp.look = { id: e.pointerId };
  }
  function fpMove(e, dx, dy) {
    if (fp.joy && fp.joy.id === e.pointerId) {
      let vx = (e.clientX - fp.joy.ox) / 55, vy = (e.clientY - fp.joy.oy) / 55;
      const l = Math.hypot(vx, vy); if (l > 1) { vx /= l; vy /= l; }
      fp.joy.vx = vx; fp.joy.vy = vy;
      $('joy').firstElementChild.style.transform = `translate(calc(-50% + ${vx * 40}px), calc(-50% + ${vy * 40}px))`;
    } else if (fp.look && fp.look.id === e.pointerId) {
      const s = 0.0055 * cfg.sens;
      fp.yaw -= dx * s; fp.pitch = Math.max(-1.35, Math.min(1.35, fp.pitch - dy * s));
    }
  }
  function fpUp(e) {
    if (fp.joy && fp.joy.id === e.pointerId) { fp.joy = null; $('joy').classList.add('hidden'); }
    if (fp.look && fp.look.id === e.pointerId) fp.look = null;
  }
  function fpJump() { if (fp.jy <= 0.001 && !fp.busy) fp.vy = 3.1; }
  function passable(x, z, u) {
    if (x < 0.1 || z < 0.1 || x > N - 0.1 || z > N - 0.1) return false;
    const t = W.ti(x, z);
    if (W.bio[t] === 0) return false;
    if (W.bld[t] && t !== W.ti(u.x, u.z)) return false;
    return true;
  }
  function fpUpdate(dt) {
    const u = fp.u;
    if (!u || !u.alive) { exitFP(`💀 ${u ? u.nm : 'Seu personagem'} morreu. Você voltou a ser deus.`); return; }
    let mx = 0, mz = 0;
    if (fp.joy) { mx = fp.joy.vx; mz = -fp.joy.vy; }
    if (keys.KeyW || keys.ArrowUp) mz += 1; if (keys.KeyS || keys.ArrowDown) mz -= 1;
    if (keys.KeyD) mx += 1; if (keys.KeyA) mx -= 1;
    if (keys.ArrowLeft) fp.yaw += dt * 2; if (keys.ArrowRight) fp.yaw -= dt * 2;
    const l = Math.hypot(mx, mz); if (l > 1) { mx /= l; mz /= l; }
    u.mv = false;
    if (fp.busy) {
      fp.busy.t -= dt; u.st = 'work';
      if (fp.busy.t <= 0) { const b = fp.busy; fp.busy = null; u.st = 'idle'; b.fn(); fp.ctxT = 0; }
    } else if (l > 0.05) {
      const t = W.ti(u.x, u.z), water = W.bio[t] <= 1;
      const sp = (l > 0.9 || keys.ShiftLeft ? 2.5 : 1.5) * (water ? 0.45 : 1);
      const fx = Math.sin(fp.yaw), fz = Math.cos(fp.yaw), rx = -Math.cos(fp.yaw), rz = Math.sin(fp.yaw);
      const dx = (fx * mz + rx * mx) * sp * dt, dz = (fz * mz + rz * mx) * sp * dt;
      if (passable(u.x + dx, u.z, u)) u.x += dx;
      if (passable(u.x, u.z + dz, u)) u.z += dz;
      u.mv = true; u.dir = Math.atan2(dx, dz);
    }
    fp.vy -= 12 * dt; fp.jy += fp.vy * dt; if (fp.jy <= 0) { fp.jy = 0; fp.vy = 0; }
    fp.gy += (W.hAt(u.x, u.z) - fp.gy) * Math.min(1, dt * 14);
    fp.cd -= dt;
    fp.ctxT -= dt;
    if (fp.ctxT <= 0) { fp.ctxT = 0.2; fp.ctx = fpContext(); renderAction(); }
  }
  function fpContext() {
    const u = fp.u, c = W.cities[u.city], civ = W.civs[c.civ];
    const fx = Math.sin(fp.yaw), fz = Math.cos(fp.yaw);
    let foe = null, bd = 1.7;
    W.near(u.x, u.z, 2, o => {
      if (o === u || !o.alive || !W.enemies(u.cv, o.cv)) return;
      const dx = o.x - u.x, dz = o.z - u.z, d = Math.hypot(dx, dz);
      if (d < bd && (dx * fx + dz * fz) / (d || 1) > 0.35) { bd = d; foe = o; }
    });
    if (foe) return { k: 'atacar', txt: `⚔️ Atacar ${foe.nm}`, foe, mark: [foe.x, foe.z, 0.45] };
    const dc = Math.hypot(c.x + 0.5 - u.x, c.z + 0.5 - u.z);
    if (u.carry > 0 && dc < 2.4) return { k: 'entregar', txt: `📦 Entregar ${u.ct === 'w' ? 'madeira' : 'comida'}` };
    for (const s of [0.45, 0.95]) {
      const x = u.x + fx * s, z = u.z + fz * s; if (!W.inside(x, z)) continue;
      const t = W.ti(x, z);
      if (W.tree[t] && !(u.carry > 0 && u.ct === 'f')) return { k: 'cortar', txt: '🪓 Cortar árvore', t, mark: [(t % N) + 0.5, ((t / N) | 0) + 0.5, 1.1] };
    }
    const bx = Math.floor(u.x + fx * 1.1), bz = Math.floor(u.z + fz * 1.1);
    if (W.inside(bx, bz) && c.founded) {
      const tt = W.ti(bx, bz), cost = W.costOf('casa', civ.era);
      if (Math.hypot(bx - c.x, bz - c.z) < W.cityRadius(c) + 3 && W.siteOk(tt) && c.wood >= cost && tt !== W.ti(u.x, u.z))
        return { k: 'construir', txt: `🔨 Construir casa (${cost} 🪵)`, x: bx, z: bz, mark: [bx + 0.5, bz + 0.5, 0] };
    }
    const t = W.ti(u.x, u.z);
    if (!u.carry && (W.field[t] || W.bio[t] === 3 || W.bio[t] === 4)) return { k: 'colher', txt: W.field[t] ? '🌾 Colher' : '🫐 Coletar frutos' };
    if (u.carry > 0) return { k: 'info', txt: `Leve até o centro de ${c.nm}` };
    return null;
  }
  function renderAction() {
    const b = $('fp-acao'), c = fp.ctx;
    if (fp.busy) { b.textContent = fp.busy.txt; b.classList.add('busy'); b.disabled = true; return; }
    b.classList.remove('busy');
    if (!c) { b.textContent = '…'; b.disabled = true; b.classList.add('off'); return; }
    b.textContent = c.txt; b.disabled = c.k === 'info'; b.classList.toggle('off', c.k === 'info');
  }
  function fpAction() {
    const c = fp.ctx, u = fp.u; if (!c || !u || fp.busy || fp.cd > 0) return;
    const city = W.cities[u.city], civ = W.civs[city.civ], e = civ.era;
    switch (c.k) {
      case 'atacar': {
        const o = c.foe; if (!o.alive) break;
        o.hp -= W.dmg(u) * 1.8; fp.cd = 0.45;
        if (!o.ctrl && o.st !== 'melee') { W.abandon(o); o.st = 'melee'; o.foe = u; }
        if (o.hp <= 0) fpMsg(`Você venceu ${o.nm}`);
        break;
      }
      case 'entregar':
        if (u.ct === 'w') city.wood += u.carry; else city.food += u.carry;
        fpMsg(`+${u.carry} ${u.ct === 'w' ? 'madeira' : 'comida'} para ${city.nm}`);
        u.carry = 0; u.ct = ''; break;
      case 'cortar':
        fp.busy = { t: 1.3, txt: '🪓 Cortando…', fn: () => {
          if (W.tree[c.t]) { W.tree[c.t] = 0; W.dirtyTile(c.t); }
          u.carry = Math.round(((u.ct === 'w' ? u.carry : 0) + 3 + e * 0.5) * 10) / 10; u.ct = 'w';
          fpMsg(`Você pegou madeira (${u.carry}). Leve ao centro da cidade.`);
        } };
        break;
      case 'colher':
        fp.busy = { t: 1.1, txt: '🧺 Colhendo…', fn: () => {
          const f = W.field[W.ti(u.x, u.z)];
          u.carry = Math.round((f ? 4 + e * 0.9 : 2 + e * 0.4) * 10) / 10; u.ct = 'f';
          fpMsg(`Você pegou comida (${u.carry}). Leve ao centro da cidade.`);
        } };
        break;
      case 'construir':
        fp.busy = { t: 1.6, txt: '🔨 Construindo…', fn: () => {
          if (W.buildNow(city, c.x, c.z, 'casa')) fpMsg(`Nova casa em ${city.nm}!`); else fpMsg('Não deu para construir aqui');
        } };
        break;
    }
    fp.ctxT = 0; renderAction();
  }
  let fpMsgT = 0;
  function fpMsg(t) { const m = $('fp-msg'); m.textContent = t; m.classList.add('show'); clearTimeout(fpMsgT); fpMsgT = setTimeout(() => m.classList.remove('show'), 2600); }
  $('fp-acao').addEventListener('click', fpAction);
  $('fp-pular').addEventListener('click', fpJump);
  $('fp-sair').addEventListener('click', () => exitFP());
  $('fp-trocar').addEventListener('click', () => {
    const u = fp.u; if (!u) return;
    let best = null, bd = 1e9;
    for (const o of W.units) if (o.alive && o !== u && o.city === u.city && o.age >= 12) { const d = Math.hypot(o.x - u.x, o.z - u.z) + Math.random() * 3; if (d < bd) { bd = d; best = o; } }
    if (best) enterFP(best); else fpMsg('Ninguém por perto para trocar');
  });
  function updateFpCard() {
    const u = fp.u; if (!u) return;
    const c = W.cities[u.city], civ = W.civs[c.civ], era = ERAS[civ.era];
    const war = Object.keys(civ.war).map(k => W.civs[k] && W.civs[k].nm).filter(Boolean);
    $('fp-card').innerHTML = `<b>${u.nm}</b>, ${Math.floor(u.age)} anos${u.sold ? ' · soldado' : ''}
      <div class="sub"><i class="dot" style="background:${css(civ.color)}"></i>${c.nm} · povo ${civ.nm}</div>
      <div class="sub">${era.ic} ${era.nome}</div>
      <div class="bar"><i style="width:${Math.max(0, u.hp / u.max * 100)}%"></i></div>
      <div class="sub">🍖 ${Math.floor(c.food)} · 🪵 ${Math.floor(c.wood)} · 🧑 ${c.pop}/${c.cap}${u.carry ? ` · levando ${u.carry} ${u.ct === 'w' ? '🪵' : '🍖'}` : ''}</div>
      ${war.length ? `<div class="sub guerra">⚔️ Guerra com ${war.join(', ')}</div>` : ''}`;
  }

  // ---------- velocidade ----------
  const speedEl = $('speed');
  function setSpeed(s) { speed = s; for (const b of speedEl.children) b.classList.toggle('on', +b.dataset.s === s); }
  speedEl.addEventListener('click', e => { const b = e.target.closest('button'); if (b) setSpeed(+b.dataset.s); });

  // ---------- avisos ----------
  const css = c => `rgb(${c[0]},${c[1]},${c[2]})`;
  function toastMsg(ev) {
    const box = $('toasts'), d = document.createElement('div');
    d.className = 'toast'; d.textContent = ev.txt; if (ev.color) d.style.borderLeftColor = css(ev.color);
    box.prepend(d);
    while (box.children.length > 3) box.lastChild.remove();
    setTimeout(() => { d.classList.add('out'); setTimeout(() => d.remove(), 400); }, 4200);
  }

  // ---------- painel de seleção ----------
  const JOBS = { food: 'coletando comida', wood: 'cortando lenha', deliver: 'levando recursos', build: 'construindo', settle: 'indo fundar uma cidade', fight: 'em batalha', siege: 'atacando a cidade inimiga', wander: 'passeando' };
  let panelT = 0;
  function renderPanel(force) {
    const el = $('painel');
    if (!sel || mode === 'fp') { el.classList.add('hidden'); return; }
    if (sel.k === 'u' && !sel.u.alive) { sel = null; el.classList.add('hidden'); return; }
    if (sel.k === 'c' && !sel.c.alive) { sel = null; el.classList.add('hidden'); return; }
    el.classList.remove('hidden');
    let h = '';
    if (sel.k === 'u') {
      const u = sel.u, c = W.cities[u.city], civ = W.civs[c.civ];
      const job = u.st === 'melee' ? 'lutando' : u.age < 12 ? 'brincando' : JOBS[u.job] || 'descansando';
      h = `<div class="p-head"><i class="dot" style="background:${css(civ.color)}"></i><b>${u.nm}</b>, ${Math.floor(u.age)} anos<button class="x" data-a="fechar">✕</button></div>
        <div class="sub">${c.nm} · povo ${civ.nm} · ${ERAS[civ.era].ic} ${ERAS[civ.era].curto}</div>
        <div class="sub">${u.sold ? '🛡️ Soldado, ' : ''}${job}</div>
        <div class="bar"><i style="width:${Math.max(0, u.hp / u.max * 100)}%"></i></div>
        <div class="p-btns"><button class="pri" data-a="viver">👁️ Viver como ${u.nm}</button></div>`;
    } else {
      const c = sel.c, civ = W.civs[c.civ], e = civ.era, nx = ERAS[e + 1];
      const prog = nx ? (civ.sci - ERAS[e].custo) / (nx.custo - ERAS[e].custo) : 1;
      const war = Object.keys(civ.war).map(k => W.civs[k] && W.civs[k].nm).filter(Boolean);
      h = `<div class="p-head"><i class="dot" style="background:${css(civ.color)}"></i><b>${c.nm}</b><span class="muted"> · povo ${civ.nm}</span><button class="x" data-a="fechar">✕</button></div>
        <div class="sub">${ERAS[e].ic} ${ERAS[e].nome}${nx ? ` → ${nx.ic} ${nx.curto}` : ' (era final)'}</div>
        <div class="bar sci"><i style="width:${Math.min(100, prog * 100)}%"></i></div>
        <div class="stats"><span>🧑 ${c.pop}/${c.cap}</span><span>🍖 ${Math.floor(c.food)}</span><span>🪵 ${Math.floor(c.wood)}</span>
        <span>🏠 ${c.n.casa}</span>${c.n.fazenda ? `<span>🌾 ${c.n.fazenda}</span>` : ''}${c.n.escola ? `<span>🎓 ${c.n.escola}</span>` : ''}${c.n.fabrica ? `<span>🏭 ${c.n.fabrica}</span>` : ''}</div>
        ${war.length ? `<div class="sub guerra">⚔️ Em guerra com ${war.join(', ')}</div>` : ''}
        ${c.hungry ? '<div class="sub guerra">🍂 A cidade está passando fome</div>' : ''}
        <div class="p-btns"><button class="pri" data-a="morador">👁️ Viver aqui</button><button data-a="inspirar">💡 Inspirar</button></div>`;
    }
    if (force || el.dataset.h !== h) { el.innerHTML = h; el.dataset.h = h; }
  }
  $('painel').addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b || !sel) return;
    const a = b.dataset.a;
    if (a === 'fechar') { sel = null; renderPanel(true); }
    else if (a === 'viver' && sel.k === 'u') enterFP(sel.u);
    else if (a === 'morador' && sel.k === 'c') {
      const c = sel.c; const us = W.units.filter(o => o.alive && o.city === c.id && o.age >= 14);
      if (us.length) enterFP(us[(Math.random() * us.length) | 0]); else hint('Ninguém mora aqui agora');
    } else if (a === 'inspirar' && sel.k === 'c') {
      const r = W.inspirar(sel.c.x, sel.c.z); if (r && !r.max) toastMsg({ txt: `💡 O povo ${r.civ.nm} teve uma grande ideia!`, color: r.civ.color });
      renderPanel(true);
    }
  });

  // ---------- folhas (eras / ajustes) ----------
  const sheet = $('sheet'), card = $('sheet-card');
  function openSheet(html) { card.innerHTML = html; sheet.classList.remove('hidden'); }
  function closeSheet() { sheet.classList.add('hidden'); }
  sheet.addEventListener('click', e => { if (e.target === sheet) closeSheet(); });
  function erasHtml() {
    const civs = W.civs.filter(c => c.alive).sort((a, b) => b.sci - a.sci);
    const maxE = civs.reduce((m, c) => Math.max(m, c.era), 0);
    const rows = civs.map(civ => {
      const nx = ERAS[civ.era + 1], prog = nx ? (civ.sci - ERAS[civ.era].custo) / (nx.custo - ERAS[civ.era].custo) : 1;
      let pop = 0, nc = 0; for (const u of W.units) if (u.alive && u.cv === civ.id) pop++;
      for (const c of W.cities) if (c.alive && c.civ === civ.id) nc++;
      return `<button class="civ" data-civ="${civ.id}"><i class="dot" style="background:${css(civ.color)}"></i><div class="grow"><b>${civ.nm}</b>
        <div class="sub">${ERAS[civ.era].ic} ${ERAS[civ.era].nome} · 🧑 ${pop} · 🏙️ ${nc}</div><div class="bar sci"><i style="width:${Math.min(100, prog * 100)}%"></i></div></div></button>`;
    }).join('') || '<p class="muted">Nenhum povo vivo. Use a ferramenta 🧑 Povo para começar.</p>';
    const tl = ERAS.map((e, i) => `<li class="${i <= maxE ? 'ok' : ''}"><span>${e.ic}</span><div><b>${e.nome}</b><div class="sub">${e.desc}</div></div></li>`).join('');
    return `<div class="sh-head"><h2>Povos e eras</h2><button class="x" data-a="fechar">✕</button></div>${rows}<h3>Linha do tempo</h3><ol class="tl">${tl}</ol>`;
  }
  function cfgHtml() {
    const q = Object.entries(QUAL).map(([k, v]) => `<button data-q="${k}" class="${cfg.q === k ? 'on' : ''}">${v.nome}</button>`).join('');
    return `<div class="sh-head"><h2>Ajustes</h2><button class="x" data-a="fechar">✕</button></div>
      <label class="lbl">Desempenho</label><div class="seg" id="seg-q">${q}</div>
      <p class="muted small">Leve: 30 fps, menos pessoas e névoa mais perto (bom para celular fraco). A resolução também se ajusta sozinha quando o jogo pesa.</p>
      <div class="row"><span>Ciclo de dia e noite</span><button class="tg ${cfg.dia ? 'on' : ''}" data-tg="dia"></button></div>
      <div class="row"><span>Mostrar FPS</span><button class="tg ${cfg.fps ? 'on' : ''}" data-tg="fps"></button></div>
      <div class="row"><span>Sensibilidade da 1ª pessoa</span><input type="range" min="0.4" max="2.2" step="0.1" value="${cfg.sens}" id="sens"></div>
      <div class="p-btns col"><button data-a="salvar">💾 Salvar agora</button><button data-a="novo" class="perigo">🌍 Criar um novo mundo</button></div>
      <p class="muted small">O mundo é salvo sozinho no celular a cada minuto.</p>`;
  }
  $('btn-eras').addEventListener('click', () => openSheet(erasHtml()));
  $('btn-cfg').addEventListener('click', () => openSheet(cfgHtml()));
  card.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    if (b.dataset.a === 'fechar') closeSheet();
    else if (b.dataset.civ) {
      const c = W.cities.find(k => k.alive && k.civ === +b.dataset.civ);
      if (c) { if (mode === 'fp') exitFP(); cam.x = c.x + 0.5; cam.z = c.z + 0.5; cam.d = 26; sel = { k: 'c', c }; renderPanel(true); }
      closeSheet();
    } else if (b.dataset.q) {
      cfg.q = b.dataset.q; Q = QUAL[cfg.q]; W.maxUnits = Q.units; dynScale = 1; resize(); saveCfg();
      for (const k of b.parentNode.children) k.classList.toggle('on', k === b);
    } else if (b.dataset.tg) {
      cfg[b.dataset.tg] = !cfg[b.dataset.tg]; b.classList.toggle('on', cfg[b.dataset.tg]); saveCfg();
      $('fps').classList.toggle('hidden', !cfg.fps);
    } else if (b.dataset.a === 'salvar') { save(); toastMsg({ txt: '💾 Mundo salvo' }); }
    else if (b.dataset.a === 'novo') {
      openSheet(`<h2>Criar um novo mundo?</h2><p class="muted">O mundo atual e todos os povos serão apagados.</p>
        <div class="p-btns"><button data-a="fechar">Cancelar</button><button class="perigo" data-a="novo-sim">Criar novo mundo</button></div>`);
    } else if (b.dataset.a === 'novo-sim') {
      if (mode === 'fp') exitFP();
      W.novo(); chunks.forEach(m => R.free(m)); chunks = new Array(NC * NC).fill(null);
      sel = null; renderPanel(true); focusStart(); closeSheet(); save();
      toastMsg({ txt: '🌍 Um novo mundo nasceu com 3 povos' });
    }
  });
  card.addEventListener('input', e => { if (e.target.id === 'sens') { cfg.sens = +e.target.value; saveCfg(); } });
  $('fps').classList.toggle('hidden', !cfg.fps);

  // ---------- salvar ----------
  function save() { try { store.set(SAVE, W.salvar()); } catch (e) { console.warn(e); } }
  let saveT = 0;
  document.addEventListener('visibilitychange', () => { if (document.hidden) save(); });
  addEventListener('pagehide', save);

  // ---------- laço principal ----------
  let last = performance.now(), hudT = 0, perfEma = 16, perfT = 0, fpsN = 0, fpsT = 0, plantT = 0;
  function frame(nowMs) {
    requestAnimationFrame(frame);
    const minDt = 1000 / Q.fps;
    if (nowMs - last < minDt - 3) return;
    const dt = Math.min(0.1, (nowMs - last) / 1000); last = nowMs;
    const now = nowMs / 1000;

    // simulação (passos de no máximo 0,1 s de jogo)
    if (speed > 0) { let g = dt * speed; while (g > 1e-4) { const s = Math.min(g, 0.1); W.step(s); g -= s; } }
    while (W.events.length) toastMsg(W.events.shift());

    if (mode === 'fp') fpUpdate(dt);
    else {
      const k = dt * cam.d * 0.9;
      if (keys.KeyW || keys.ArrowUp) pan(0, k * 20); if (keys.KeyS || keys.ArrowDown) pan(0, -k * 20);
      if (keys.KeyA || keys.ArrowLeft) pan(k * 20, 0); if (keys.KeyD || keys.ArrowRight) pan(-k * 20, 0);
      if (keys.KeyQ) cam.yaw += dt * 1.5; if (keys.KeyE) cam.yaw -= dt * 1.5;
    }
    if (shake > 0) shake = Math.max(0, shake - dt * 2.5);
    if (flash > 0) flash = Math.max(0, flash - dt * 2.2);

    setupCamera();
    if (brush && mode === 'deus') {
      const p = pick(brush.x, brush.y);
      if (p) {
        if (tool === 'erguer') W.brush(p[0], p[1], 3.2, dt * 0.32);
        else if (tool === 'afundar') W.brush(p[0], p[1], 3.2, -dt * 0.32);
        else if (tool === 'arvore') { plantT -= dt; if (plantT <= 0) { plantT = 0.08; W.plantar(p[0], p[1], 2.2); } }
      }
    }
    rebuildChunks(Q.rebuild);

    const env = lighting(now);
    R.begin(cv.width, cv.height, env);
    // pedaços: na 1ª pessoa, só os que estão perto e à frente
    if (mode !== 'fp') R.draw(ocean);
    const fpm = mode === 'fp', fw = fpm ? [Math.sin(fp.yaw), Math.cos(fp.yaw)] : null;
    for (let i = 0; i < chunks.length; i++) {
      if (fpm) {
        const cx = (i % NC) * CH + CH / 2 - V3.eye[0], cz = ((i / NC) | 0) * CH + CH / 2 - V3.eye[2];
        const d = Math.hypot(cx, cz);
        if (d - 11.4 > Q.far) continue;
        if (d > 11.4 && (cx * fw[0] + cz * fw[1]) < -11.4) continue;
      }
      R.draw(chunks[i]);
    }
    for (let i = fx.length - 1; i >= 0; i--) if (now > fx[i].end) { const f = fx[i]; fx.splice(i, 1); if (f.done) f.done(); }
    let mark = null;
    if (fpm && fp.ctx && fp.ctx.mark) mark = fp.ctx.mark;
    else if (!fpm && sel) mark = sel.k === 'u' ? [sel.u.x, sel.u.z, 0.5] : [sel.c.x + 0.5, sel.c.z + 0.5, 1.6];
    const dw = G.buildDynamic({
      time: now, dt, speed, cam: V3.eye, fp: fpm, fwd: fw, hide: fpm ? fp.u : null,
      target: [cam.x, cam.z], range: fpm ? Q.far : cam.d * 1.9 + 30, far: !fpm && cam.d > 42, lod: fpm ? 14 : 1e9,
      clouds: Q.clouds, mark, fx,
    });
    R.upload(dyn, dw, true);
    R.draw(dyn);

    // interface (4x por segundo)
    hudT -= dt;
    if (hudT <= 0) {
      hudT = 0.25;
      const st = W.stats();
      $('info-ano').textContent = `${env.night > 0.5 ? '🌙' : '☀️'} Ano ${st.ano}`;
      $('info-pop').textContent = `🧑 ${st.pop} · 🏙️ ${st.cities}`;
      if (mode === 'fp') updateFpCard(); else renderPanel(false);
    }
    // FPS e resolução automática
    perfEma = perfEma * 0.9 + dt * 1000 * 0.1; perfT += dt; fpsN++; fpsT += dt;
    if (perfT > 2) {
      perfT = 0;
      if (perfEma > minDt * 1.3 && dynScale > 0.55) { dynScale = Math.max(0.55, dynScale - 0.1); resize(); }
      else if (perfEma < minDt * 1.08 && dynScale < 1) { dynScale = Math.min(1, dynScale + 0.05); resize(); }
    }
    if (fpsT > 0.5) {
      if (cfg.fps) $('fps').textContent = `${Math.round(fpsN / fpsT)} fps · ${Math.round(dynScale * 100)}% · ${(dw.n / 1000) | 0}k`;
      fpsN = 0; fpsT = 0;
    }
    saveT += dt; if (saveT > 60) { saveT = 0; save(); }
  }
  // monta todos os pedaços antes do primeiro quadro (sem "buracos" no começo)
  setupCamera(); rebuildChunks(NC * NC);
  requestAnimationFrame(frame);
  if (!loaded) setTimeout(() => toastMsg({ txt: '🌍 Um novo mundo nasceu com 3 povos. Toque em ✋ e explore!' }), 400);
  else setTimeout(() => toastMsg({ txt: '💾 Seu mundo foi carregado' }), 400);
  hint(DICAS.olhar);

  window.__jogo = { W, cam, fp, enterFP, exitFP, setSpeed, get mode() { return mode; } }; // para testes

  if ('serviceWorker' in navigator && location.protocol === 'https:') navigator.serviceWorker.register('sw.js').catch(() => {});
})();
