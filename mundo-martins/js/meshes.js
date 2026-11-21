'use strict';
// Monta a geometria do mundo.
// - Pedaços estáticos (16x16 quadrados): terreno + árvores + estradas + prédios. Só são remontados quando algo muda neles.
// - Geometria dinâmica (todo quadro): pessoas, carros, fogo, fumaça, nuvens e efeitos.

const G = (() => {
  const { N, V, HS, SEA, CH, NC } = W;
  const hash = W.hash;

  const COR = {
    areia: [222, 205, 140], grama: [96, 168, 68], morro: [92, 140, 62], pedra: [128, 122, 115], neve: [240, 244, 248],
    raso: [74, 168, 200], fundo: [22, 70, 140], terra: [150, 118, 78], calcada: [146, 142, 134], asfalto: [62, 64, 70],
    campo1: [214, 186, 84], campo2: [176, 168, 72], cinza: [150, 150, 145], queimado: [60, 52, 44],
  };
  const PELE = [[241, 200, 160], [214, 160, 110], [160, 108, 70], [100, 66, 44]];
  const CALCA = [[120, 90, 60], [130, 100, 70], [110, 100, 90], [90, 80, 110], [70, 70, 90], [60, 60, 70], [52, 80, 140], [40, 60, 110]];

  // ---------- terreno ----------
  function tileColor(t, x, z, bio, avg) {
    const v = 0.93 + hash(x, z, 7) * 0.14;
    let c;
    if (W.burn[t] > 0) c = COR.queimado;
    else if (W.road[t]) { const e = W.road[t] - 1; c = e >= 6 ? COR.asfalto : e >= 4 ? COR.calcada : COR.terra; return rgbm(c, 0.97 + hash(x, z, 3) * 0.06); }
    else if (W.field[t]) c = (x + z) & 1 ? COR.campo1 : COR.campo2;
    else switch (bio) {
      case 2: c = COR.areia; break;
      case 3: c = mix(COR.grama, [130, 180, 80], hash(x >> 2, z >> 2, 5) * 0.5); break;
      case 4: c = mix(COR.morro, [120, 128, 70], (avg - 0.58) * 6); break;
      case 5: c = mix(COR.pedra, [110, 104, 98], hash(x, z, 9)); break;
      default: c = COR.neve;
    }
    return rgbm(c, v);
  }

  function tree(w, t, x, z, bio) {
    const s = 0.8 + hash(x, z, 11) * 0.45, jx = (hash(x, z, 12) - 0.5) * 0.35, jz = (hash(x, z, 13) - 0.5) * 0.35;
    const px = x + 0.5 + jx, pz = z + 0.5 + jz, y = W.hAt(px, pz) - 0.02, v = 0.85 + hash(x, z, 14) * 0.3;
    const burning = W.burn[t] > 0, trunk = rgb(110, 78, 50);
    if (bio === 2) { // coqueiro
      w.box(px, y, pz, 0.07, 0.75 * s, 0.07, trunk);
      const top = y + 0.75 * s, leaf = rgbm([70, 150, 60], v);
      w.box(px, top, pz, 0.7 * s, 0.04, 0.14, leaf, 0.4); w.box(px, top, pz, 0.7 * s, 0.04, 0.14, leaf, 1.97);
      return;
    }
    if (bio >= 4) { // pinheiro
      w.box(px, y, pz, 0.08, 0.25 * s, 0.08, trunk);
      const g = bio === 6 ? [235, 240, 245] : [44, 106 * v, 58];
      const g2 = rgbm(burning ? [40, 30, 20] : g, 1);
      w.pyr(px, y + 0.2 * s, pz, 0.6 * s, 0.6 * s, 0.55 * s, rgbm(burning ? [40, 30, 20] : [44, 106 * v, 58], 1));
      w.pyr(px, y + 0.5 * s, pz, 0.45 * s, 0.45 * s, 0.5 * s, g2);
      return;
    }
    // árvore de folhas (blocos)
    w.box(px, y, pz, 0.09, 0.3 * s, 0.09, trunk);
    const g = burning ? [50, 40, 30] : [58 * v, 140 * v, 48 * v];
    w.box(px, y + 0.28 * s, pz, 0.52 * s, 0.38 * s, 0.52 * s, rgbm(g, 1));
    w.box(px, y + 0.62 * s, pz, 0.32 * s, 0.2 * s, 0.32 * s, rgbm(g, 1.08));
  }

  // ---------- prédios por era ----------
  function windowsRing(w, x, y, z, sx, sz, floors, fh, c, yaw) {
    for (let k = 0; k < floors; k++) w.box(x, y + k * fh + fh * 0.35, z, sx + 0.02, fh * 0.32, sz + 0.02, c, yaw);
  }
  function flag(w, x, y, z, civC, h) {
    w.box(x, y, z, 0.03, h, 0.03, rgb(90, 80, 70));
    w.box(x + 0.09, y + h - 0.12, z, 0.18, 0.11, 0.02, rgbm(civC, 1));
  }
  function building(w, b) {
    const civ = W.civs[W.cities[b.city].civ], cc = civ.color, e = b.era, hs = hash(b.id, 1, 3);
    const x = b.x + 0.5, z = b.z + 0.5, cr = W.cornerRange(W.ti(b.x, b.z));
    const y0 = cr[0] * HS, y = cr[1] * HS;
    if (y - y0 > 0.02) w.box(x, y0 - 0.05, z, 0.92, y - y0 + 0.05, 0.92, rgb(120, 112, 100));
    const yaw = hs < 0.5 ? 0 : Math.PI / 2;
    const roofC = (m, k) => rgbm(mix(m, cc, k === undefined ? 0.35 : k), 1);
    const NL = (r, g, b) => rgb(r, g, b, F_NOITE);
    switch (b.type) {
      case 'casa':
        if (e === 0) { w.pyr(x, y, z, 0.72, 0.72, 0.72, roofC([176, 146, 90], 0.25)); w.box(x, y, z + 0.3, 0.16, 0.22, 0.06, rgb(60, 40, 25)); }
        else if (e === 1) { w.box(x, y, z, 0.68, 0.4, 0.68, rgb(172, 128, 86)); w.pyr(x, y + 0.4, z, 0.84, 0.84, 0.3, roofC([200, 170, 100])); }
        else if (e === 2) { w.box(x, y, z, 0.72, 0.5, 0.66, rgb(206, 200, 188)); w.roof(x, y + 0.5, z, 0.82, 0.78, 0.28, roofC([186, 92, 60]), yaw); }
        else if (e === 3) {
          w.box(x, y, z, 0.68, 0.6, 0.62, rgb(232, 226, 210)); w.box(x, y + 0.28, z, 0.7, 0.05, 0.64, rgb(100, 70, 48));
          w.roof(x, y + 0.6, z, 0.78, 0.72, 0.45, roofC([104, 72, 52]), yaw); w.box(x, y + 0.36, z, 0.7, 0.1, 0.2, NL(80, 70, 60), yaw);
        } else if (e === 4) {
          w.box(x, y, z, 0.78, 0.9, 0.74, rgb(172, 84, 62)); windowsRing(w, x, y, z, 0.78, 0.74, 2, 0.45, NL(70, 70, 80));
          w.pyr(x, y + 0.9, z, 0.84, 0.8, 0.26, roofC([82, 82, 92]));
        } else if (e === 5) {
          w.box(x, y, z, 0.84, 1.0, 0.78, rgb(132, 66, 52)); windowsRing(w, x, y, z, 0.84, 0.78, 2, 0.5, NL(60, 60, 70));
          w.roof(x, y + 1.0, z, 0.88, 0.82, 0.3, roofC([74, 74, 82]), yaw); w.box(x + 0.25, y + 1.0, z + 0.2, 0.12, 0.38, 0.12, rgb(90, 50, 40));
        } else if (e === 6) {
          const fl = 3 + ((hs * 4) | 0), fh = 0.38;
          w.box(x, y, z, 0.82, fl * fh, 0.82, rgb(206, 200, 188)); windowsRing(w, x, y, z, 0.82, 0.82, fl, fh, NL(70, 92, 112));
          w.box(x, y + fl * fh, z, 0.5, 0.12, 0.5, roofC([120, 120, 120], 0.6));
        } else {
          const fl = 6 + ((hs * 9) | 0), fh = 0.36, H = fl * fh;
          w.box(x, y, z, 0.8, H, 0.8, NL(66, 116, 160));
          for (let k = 1; k <= fl; k++) w.box(x, y + k * fh - 0.06, z, 0.83, 0.06, 0.83, rgb(222, 226, 232));
          w.box(x, y + H, z, 0.55, 0.1, 0.55, roofC([70, 150, 70], 0.5));
        }
        break;
      case 'centro':
        if (e === 0) {
          for (let i = 0; i < 6; i++) { const a = i * 1.047; w.box(x + Math.cos(a) * 0.22, y, z + Math.sin(a) * 0.22, 0.1, 0.07, 0.1, rgb(120, 118, 112)); }
          w.box(x, y, z, 0.2, 0.05, 0.2, rgb(80, 50, 30));
          w.pyr(x, y + 0.04, z, 0.22, 0.22, 0.34, rgb(255, 150, 40, F_EMIS)); w.pyr(x, y + 0.04, z, 0.12, 0.12, 0.42, rgb(255, 230, 120, F_EMIS));
          w.box(x + 0.34, y, z - 0.3, 0.1, 1.1, 0.1, rgb(120, 86, 56)); w.box(x + 0.34, y + 1.1, z - 0.3, 0.16, 0.16, 0.16, rgbm(cc, 1));
        } else if (e === 1) {
          w.box(x, y, z, 0.86, 0.5, 0.86, rgb(168, 124, 84)); w.pyr(x, y + 0.5, z, 1.0, 1.0, 0.45, roofC([206, 176, 104], 0.45));
          flag(w, x + 0.4, y + 0.5, z + 0.4, cc, 0.9);
        } else if (e === 2) {
          w.box(x, y, z, 0.95, 0.12, 0.95, rgb(190, 184, 172));
          w.box(x, y + 0.12, z - 0.1, 0.8, 0.55, 0.55, rgb(236, 232, 222));
          for (let i = 0; i < 5; i++) w.box(x - 0.36 + i * 0.18, y + 0.12, z + 0.3, 0.07, 0.55, 0.07, rgb(240, 238, 230));
          w.roof(x, y + 0.67, z, 0.9, 0.85, 0.22, roofC([190, 96, 64]), 0); flag(w, x, y + 0.89, z, cc, 0.5);
        } else if (e === 3) {
          const st = rgb(150, 150, 144), cap = roofC([60, 70, 110], 0.6);
          w.box(x, y, z, 0.58, 1.2, 0.58, st); w.box(x, y + 0.7, z, 0.6, 0.12, 0.6, NL(70, 60, 50));
          for (const [dx, dz] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
            w.box(x + dx * 0.36, y, z + dz * 0.36, 0.22, 1.0, 0.22, st); w.pyr(x + dx * 0.36, y + 1.0, z + dz * 0.36, 0.28, 0.28, 0.32, cap);
          }
          flag(w, x, y + 1.2, z, cc, 0.6);
        } else if (e === 4) {
          w.box(x, y, z, 0.92, 0.8, 0.72, rgb(234, 220, 190)); windowsRing(w, x, y, z, 0.92, 0.72, 2, 0.4, NL(80, 80, 90));
          w.box(x, y + 0.8, z, 0.5, 0.18, 0.5, rgb(90, 160, 140)); w.box(x, y + 0.98, z, 0.36, 0.14, 0.36, rgb(90, 160, 140));
          w.box(x, y + 1.12, z, 0.2, 0.1, 0.2, rgb(90, 160, 140)); flag(w, x, y + 1.22, z, cc, 0.45);
        } else if (e === 5) {
          w.box(x, y, z, 0.9, 0.9, 0.8, rgb(150, 74, 56)); windowsRing(w, x, y, z, 0.9, 0.8, 2, 0.45, NL(60, 60, 70));
          w.box(x, y + 0.9, z, 0.3, 0.9, 0.3, rgb(160, 84, 62)); w.box(x, y + 1.45, z, 0.32, 0.18, 0.32, NL(230, 230, 210));
          w.pyr(x, y + 1.8, z, 0.36, 0.36, 0.35, roofC([70, 80, 90], 0.5)); flag(w, x + 0.35, y + 0.9, z + 0.3, cc, 0.5);
        } else if (e === 6) {
          const fl = 5, fh = 0.38;
          w.box(x, y, z, 0.9, fl * fh, 0.9, rgb(220, 216, 206)); windowsRing(w, x, y, z, 0.9, 0.9, fl, fh, NL(70, 90, 110));
          w.box(x, y + fl * fh, z, 0.6, 0.1, 0.6, rgbm(cc, 1)); flag(w, x + 0.3, y + fl * fh, z, cc, 0.5);
        } else {
          const fl = 16, fh = 0.36, H = fl * fh;
          w.box(x, y, z, 0.9, H * 0.6, 0.9, rgb(70, 120, 170, F_NOITE));
          w.box(x, y + H * 0.6, z, 0.7, H * 0.4, 0.7, rgb(80, 130, 180, F_NOITE));
          for (let k = 1; k <= fl; k++) w.box(x, y + k * fh - 0.07, z, k * fh < H * 0.6 ? 0.93 : 0.73, 0.07, k * fh < H * 0.6 ? 0.93 : 0.73, rgb(226, 230, 236));
          w.box(x, y + H, z, 0.35, 0.15, 0.35, rgbm(cc, 1)); w.box(x, y + H + 0.15, z, 0.04, 1.0, 0.04, rgb(200, 200, 200));
          w.box(x, y + H + 1.15, z, 0.08, 0.08, 0.08, rgb(255, 40, 40, F_EMIS));
        }
        break;
      case 'fazenda':
        if (e <= 4) { w.box(x, y, z, 0.6, 0.42, 0.48, rgb(160, 52, 42)); w.roof(x, y + 0.42, z, 0.68, 0.56, 0.24, roofC([90, 70, 60]), yaw); }
        else { w.box(x - 0.15, y, z, 0.5, 0.45, 0.5, rgb(170, 60, 50)); w.roof(x - 0.15, y + 0.45, z, 0.56, 0.56, 0.22, roofC([80, 80, 88]), 0); w.box(x + 0.28, y, z + 0.2, 0.22, e >= 6 ? 1.0 : 0.75, 0.22, rgb(196, 196, 200)); }
        break;
      case 'escola':
        if (e <= 2) {
          w.box(x, y, z, 0.9, 0.1, 0.8, rgb(220, 216, 206));
          for (let i = 0; i < 4; i++) for (const dz of [-0.3, 0.3]) w.box(x - 0.33 + i * 0.22, y + 0.1, z + dz, 0.08, 0.5, 0.08, rgb(244, 242, 236));
          w.box(x, y + 0.1, z, 0.5, 0.45, 0.4, rgb(230, 226, 214)); w.roof(x, y + 0.6, z, 0.88, 0.78, 0.2, roofC([236, 232, 222], 0.2), 0);
        } else if (e === 3) {
          w.box(x, y, z + 0.08, 0.5, 0.7, 0.76, rgb(210, 204, 190)); w.roof(x, y + 0.7, z + 0.08, 0.76, 0.56, 0.32, roofC([100, 80, 70]), Math.PI / 2);
          w.box(x, y, z - 0.32, 0.26, 1.4, 0.26, rgb(200, 194, 180)); w.pyr(x, y + 1.4, z - 0.32, 0.3, 0.3, 0.55, roofC([80, 70, 70]));
          w.box(x, y + 0.35, z + 0.08, 0.52, 0.14, 0.4, NL(90, 70, 90));
        } else if (e <= 5) {
          w.box(x, y, z, 0.88, 0.8, 0.7, rgb(e === 4 ? 230 : 170, e === 4 ? 214 : 90, e === 4 ? 180 : 64)); windowsRing(w, x, y, z, 0.88, 0.7, 2, 0.4, NL(70, 70, 80));
          w.box(x, y + 0.8, z, 0.44, 0.2, 0.44, rgb(110, 150, 170)); w.box(x, y + 1.0, z, 0.26, 0.14, 0.26, rgb(110, 150, 170));
        } else {
          w.box(x, y, z, 0.92, 1.0, 0.8, rgb(230, 232, 236)); windowsRing(w, x, y, z, 0.92, 0.8, 3, 0.33, NL(80, 120, 160));
          w.box(x, y + 1.0, z, 0.92, 0.06, 0.8, rgbm(cc, 1));
        }
        break;
      case 'fabrica':
        if (e <= 5) {
          w.box(x, y, z, 0.92, 0.7, 0.8, rgb(122, 100, 84)); windowsRing(w, x, y, z, 0.92, 0.8, 1, 0.6, NL(70, 60, 50));
          w.box(x - 0.25, y + 0.7, z - 0.2, 0.14, 1.2, 0.14, rgb(90, 60, 50)); w.box(x + 0.2, y + 0.7, z - 0.2, 0.14, 1.0, 0.14, rgb(90, 60, 50));
        } else if (e === 6) {
          w.box(x, y, z, 0.92, 0.6, 0.9, rgb(150, 160, 170));
          for (let i = 0; i < 3; i++) w.roof(x - 0.3 + i * 0.3, y + 0.6, z, 0.3, 0.9, 0.18, rgb(120, 130, 140), Math.PI / 2);
          w.box(x + 0.32, y + 0.6, z + 0.3, 0.12, 0.9, 0.12, rgb(170, 170, 175));
        } else {
          w.box(x, y, z, 0.92, 0.5, 0.9, rgb(238, 240, 242)); windowsRing(w, x, y, z, 0.92, 0.9, 1, 0.45, NL(80, 120, 160));
          for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) w.box(x - 0.2 + i * 0.4, y + 0.5, z - 0.2 + j * 0.4, 0.34, 0.04, 0.34, rgb(40, 60, 120));
          w.box(x + 0.4, y, z + 0.4, 0.05, 1.8, 0.05, rgb(235, 235, 235));
          for (let i = 0; i < 3; i++) w.box(x + 0.4, y + 1.8, z + 0.4, 0.06, 0.04, 0.55, rgb(245, 245, 245), i * 2.094);
        }
        break;
    }
  }

  // ---------- pedaços estáticos ----------
  const cw = new MeshW(60000);
  function buildChunk(cx, cz) {
    cw.reset();
    const h = W.h;
    for (let z = cz * CH; z < (cz + 1) * CH; z++) for (let x = cx * CH; x < (cx + 1) * CH; x++) {
      const t = z * N + x, i = z * V + x, bio = W.bio[t];
      const a = h[i], b = h[i + 1], c = h[i + V], d = h[i + V + 1];
      const ya = Math.max(a, SEA) * HS, yb = Math.max(b, SEA) * HS, yc = Math.max(c, SEA) * HS, yd = Math.max(d, SEA) * HS;
      let col;
      if (bio <= 1) {
        const avg = (a + b + c + d) / 4, k = Math.min(1, Math.max(0, (SEA - avg) / 0.16));
        col = rgbm(mix(COR.raso, COR.fundo, k), 0.97 + hash(x, z, 2) * 0.06, F_AGUA);
      } else col = tileColor(t, x, z, bio, (a + b + c + d) / 4);
      cw.tri(x, ya, z, x + 1, yb, z, x + 1, yd, z + 1, col);
      cw.tri(x, ya, z, x + 1, yd, z + 1, x, yc, z + 1, col);
      if (bio <= 1) continue;
      if (W.tree[t]) tree(cw, t, x, z, bio);
      if (W.bld[t]) { const bb = W.B.get(W.bld[t]); if (bb) building(cw, bb); }
      if (W.road[t] >= 7 && hash(x, z, 21) < 0.18) { // poste de luz
        const px = x + 0.15, pz = z + 0.15, py = W.hAt(px, pz);
        cw.box(px, py, pz, 0.04, 0.5, 0.04, rgb(90, 90, 95)); cw.box(px, py + 0.5, pz, 0.1, 0.05, 0.1, rgb(250, 240, 200, F_NOITE));
      }
      if (W.burn[t] > 0 && !W.tree[t] && !W.bld[t]) cw.box(x + 0.5, W.hAt(x + 0.5, z + 0.5), z + 0.5, 0.3, 0.05, 0.3, rgb(40, 30, 25));
    }
    return cw;
  }

  // ---------- geometria dinâmica ----------
  const dw = new MeshW(120000);
  const smoke = [];
  let chimneys = [], chimT = 0;

  function person(w, u, time, far) {
    const civ = W.civs[u.cv]; if (!civ) return;
    const cc = civ.color, y = W.hAt(u.x, u.z), e = civ.era, yaw = -u.dir;
    if (far) { w.box(u.x, y, u.z, 0.34, 0.5, 0.34, rgbm(cc, 1)); return; }
    if (u.car && u.mv) {
      w.box(u.x, y + 0.05, u.z, 0.28, 0.14, 0.52, rgbm(cc, 1), yaw);
      w.box(u.x, y + 0.19, u.z, 0.24, 0.12, 0.28, rgb(40, 50, 62, F_NOITE), yaw);
      const fx = Math.sin(u.dir) * 0.26, fz = Math.cos(u.dir) * 0.26;
      w.box(u.x + fx, y + 0.08, u.z + fz, 0.22, 0.05, 0.02, rgb(255, 250, 220, F_NOITE), yaw);
      return;
    }
    const s = u.age < 12 ? 0.62 : 1;
    let bob = u.mv ? Math.abs(Math.sin(time * 11 + u.id)) * 0.035 : 0;
    if (u.st === 'work' || u.st === 'melee') bob = Math.abs(Math.sin(time * 8 + u.id)) * 0.03;
    const shirt = rgbm(cc, 1), skin = rgbm(PELE[u.skin], 1);
    w.box(u.x, y + bob, u.z, 0.14 * s, 0.16 * s, 0.09 * s, rgbm(CALCA[e], 1), yaw);
    w.box(u.x, y + 0.16 * s + bob, u.z, 0.19 * s, 0.17 * s, 0.11 * s, shirt, yaw);
    w.box(u.x, y + 0.33 * s + bob, u.z, 0.11 * s, 0.11 * s, 0.11 * s, skin, yaw);
    const rx = Math.cos(u.dir), rz = -Math.sin(u.dir), fx = Math.sin(u.dir), fz = Math.cos(u.dir);
    if (u.sold) {
      if (e >= 3) w.box(u.x, y + 0.42 + bob, u.z, 0.13, 0.04, 0.13, rgb(e >= 6 ? 70 : 140, e >= 6 ? 90 : 140, e >= 6 ? 60 : 145), yaw);
      if (e <= 2) w.box(u.x + rx * 0.12, y + 0.05, u.z + rz * 0.12, 0.03, 0.55, 0.03, rgb(140, 110, 70));
      else if (e <= 4) w.box(u.x + rx * 0.12, y + 0.14, u.z + rz * 0.12, 0.03, 0.3, 0.03, rgb(200, 200, 210));
      else w.box(u.x + rx * 0.1 + fx * 0.08, y + 0.24, u.z + rz * 0.1 + fz * 0.08, 0.04, 0.04, 0.22, rgb(40, 40, 45), yaw);
    } else if (u.st === 'work' && u.job === 'wood') {
      const sw = Math.sin(time * 9 + u.id) * 0.06;
      w.box(u.x + rx * 0.12 + fx * sw, y + 0.2, u.z + rz * 0.12 + fz * sw, 0.03, 0.22, 0.03, rgb(120, 90, 60));
    }
    if (u.carry > 0) w.box(u.x - fx * 0.09, y + 0.2 * s + bob, u.z - fz * 0.09, 0.15, 0.1, 0.08, u.ct === 'w' ? rgb(130, 90, 50) : rgb(230, 200, 80), yaw);
    if (e >= 7 && u.age >= 14 && !u.sold && !u.mv && u.st === 'idle') w.box(u.x + fx * 0.08, y + 0.28, u.z + fz * 0.08, 0.05, 0.07, 0.015, rgb(150, 200, 255, F_EMIS), yaw);
  }

  function buildDynamic(o) {
    const w = dw; w.reset();
    const time = o.time, cam = o.cam;
    const range = o.range, r2 = range * range, lod2 = o.lod * o.lod;
    for (const u of W.units) {
      if (!u.alive || u === o.hide) continue;
      const dx = u.x - cam[0], dz = u.z - cam[2];
      const d2 = dx * dx + dz * dz;
      if (o.fp) {
        if (d2 > r2) continue;
        if (d2 > 4 && dx * o.fwd[0] + dz * o.fwd[1] < -1) continue;
      } else {
        const tx = u.x - o.target[0], tz = u.z - o.target[1]; if (tx * tx + tz * tz > r2) continue;
      }
      // nível de detalhe: de longe, cada pessoa vira um único bloco colorido (1/4 dos vértices)
      person(w, u, time, o.far || d2 > lod2);
    }
    // fogo
    for (const t of W.burnList) {
      const x = t % N, z = (t / N) | 0, px = x + 0.5, pz = z + 0.5;
      if (o.fp && (px - cam[0]) ** 2 + (pz - cam[2]) ** 2 > r2) continue;
      const y = W.hAt(px, pz) + (W.bld[t] ? 0.4 : W.tree[t] ? 0.25 : 0);
      const f = 0.8 + Math.sin(time * 17 + t) * 0.25;
      w.pyr(px, y, pz, 0.5, 0.5, 0.6 * f, rgb(255, 110, 30, F_EMIS), time * 2 + t);
      w.pyr(px, y, pz, 0.28, 0.28, 0.85 * f, rgb(255, 210, 90, F_EMIS), -time * 3);
    }
    // fumaça das fábricas
    chimT -= o.dt;
    if (chimT <= 0) {
      chimT = 1; chimneys = [];
      for (const b of W.B.values()) if (b.type === 'fabrica' && b.era <= 6) chimneys.push(b);
      if (chimneys.length > 40) chimneys.length = 40;
    }
    if (o.speed > 0) for (const b of chimneys) if (Math.random() < o.dt * 1.5) {
      const cr = W.cornerRange(W.ti(b.x, b.z));
      smoke.push({ x: b.x + 0.5 - (b.era === 6 ? -0.32 : 0.25), y: cr[1] * HS + (b.era === 6 ? 1.5 : 1.9), z: b.z + 0.5 + (b.era === 6 ? 0.3 : -0.2), l: 0 });
    }
    for (let i = smoke.length - 1; i >= 0; i--) {
      const p = smoke[i]; p.l += o.dt * (o.speed > 0 ? 1 : 0); p.y += o.dt * 0.5 * (o.speed > 0 ? 1 : 0); p.x += o.dt * 0.2;
      if (p.l > 4 || smoke.length > 300) { smoke[i] = smoke[smoke.length - 1]; smoke.pop(); continue; }
      const s = 0.15 + p.l * 0.12, g = 120 + p.l * 20;
      w.box(p.x, p.y, p.z, s, s, s, rgb(g, g, g));
    }
    // nuvens (só na visão de cima)
    if (!o.fp && o.clouds) {
      for (let i = 0; i < 14; i++) {
        const cx = ((hash(i, 1, 77) * N + time * 0.6) % (N + 30)) - 15, cz = hash(i, 2, 77) * N;
        const s = 4 + hash(i, 3, 77) * 6, cy = 22 + hash(i, 4, 77) * 6;
        w.box(cx, cy, cz, s, 0.8, s * 0.6, rgb(245, 247, 250)); w.box(cx + s * 0.2, cy + 0.6, cz, s * 0.6, 0.7, s * 0.4, rgb(250, 251, 253));
      }
    }
    // marcador de seleção
    if (o.mark) {
      const m = o.mark, y = W.hAt(m[0], m[1]) + m[2] + 0.25 + Math.sin(time * 4) * 0.08;
      w.pyr(m[0], y + 0.3, m[1], 0.22, 0.22, -0.3, rgb(255, 220, 60, F_EMIS), time * 2);
    }
    for (const f of o.fx) f.draw(w, time);
    return w;
  }

  return { buildChunk, buildDynamic, COR };
})();
