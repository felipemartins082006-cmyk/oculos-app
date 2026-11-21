'use strict';
// Simulação do mundo: terreno, povos, cidades, eras, guerras e fogo.
// Não usa nada do navegador, então também roda no Node (para testes e ajustes de ritmo).
(function (root) {
  const N = 128, V = N + 1, HS = 14, SEA = 0.34, CH = 16, NC = N / CH, YEAR = 20, GC = 4, GN = N / GC;

  const ERAS = [
    { nome: 'Idade da Pedra', curto: 'Pedra', custo: 0, ic: '🪨', desc: 'Cabanas, fogueiras, coleta e caça com lanças.' },
    { nome: 'Idade do Bronze', curto: 'Bronze', custo: 120, ic: '🏺', desc: 'Agricultura: fazendas, celeiros e casas de barro.' },
    { nome: 'Antiguidade', curto: 'Antiguidade', custo: 420, ic: '🏛️', desc: 'Casas de pedra, templos, palácios e as primeiras estradas.' },
    { nome: 'Idade Média', curto: 'Medieval', custo: 1100, ic: '🏰', desc: 'Castelos, igrejas, casas de enxaimel e espadas.' },
    { nome: 'Renascimento', curto: 'Renascimento', custo: 2400, ic: '📜', desc: 'Casas de tijolo, bibliotecas, cúpulas e ruas de pedra.' },
    { nome: 'Era Industrial', curto: 'Industrial', custo: 4500, ic: '🏭', desc: 'Fábricas com chaminés, sobrados e lampiões a gás.' },
    { nome: 'Era Moderna', curto: 'Moderna', custo: 7800, ic: '🚗', desc: 'Prédios, carros, asfalto, postes e luz elétrica.' },
    { nome: 'Era Contemporânea', curto: 'Atual', custo: 12500, ic: '📱', desc: 'Arranha-céus de vidro, celulares e energia limpa.' },
  ];

  const BT = {
    centro: { nome: 'Centro', cost: 0, time: 1.5, hp: 220 },
    casa: { nome: 'Casa', cost: 6, time: 3, hp: 50 },
    fazenda: { nome: 'Fazenda', cost: 8, time: 4, hp: 40 },
    escola: { nome: 'Escola', cost: 16, time: 6, hp: 60 },
    fabrica: { nome: 'Fábrica', cost: 26, time: 8, hp: 90 },
  };
  const CAP = [4, 5, 6, 6, 8, 10, 16, 24];
  const CORES = [[226, 64, 58], [52, 112, 232], [245, 196, 40], [58, 180, 92], [168, 82, 206], [245, 134, 40],
    [40, 192, 204], [232, 92, 164], [150, 104, 62], [236, 236, 236], [90, 90, 104], [150, 210, 60]];
  const SIL = ['ka', 'ta', 'mi', 'ru', 'lo', 'an', 'be', 'do', 'sa', 'ri', 'na', 'to', 'vel', 'mar', 'tin', 'zu', 'ki', 'ra',
    'nor', 'el', 'ia', 'ul', 'or', 'gu', 'pe', 'li', 'sol', 'ba', 'ju', 'ma', 'ti', 'co', 'ar', 'ven', 'dor', 'ga', 'ne', 'xo'];
  const NOMES = ['Ana', 'João', 'Maria', 'Pedro', 'Lucas', 'Júlia', 'Gabriel', 'Beatriz', 'Rafael', 'Larissa', 'Mateus', 'Camila',
    'Felipe', 'Sofia', 'Bruno', 'Laura', 'Diego', 'Alice', 'Tiago', 'Helena', 'Caio', 'Lívia', 'Igor', 'Marina', 'Davi', 'Clara',
    'Enzo', 'Yara', 'Iara', 'Raí', 'Tainá', 'Jaci', 'Moacir', 'Luana', 'Otávio', 'Bianca', 'Renato', 'Paula', 'Vítor', 'Cecília'];

  const rnd = Math.random;
  const clamp = (v, a, b) => v < a ? a : v > b ? b : v;

  const W = { N, V, HS, SEA, CH, NC, YEAR, GN, ERAS, BT, CAP, CORES, maxUnits: 600, events: [] };

  // ---------- ruído ----------
  function hash(x, z, s) {
    let h = Math.imul(x | 0, 374761393) ^ Math.imul(z | 0, 668265263) ^ Math.imul(s | 0, 1442695041);
    h = Math.imul(h ^ (h >>> 13), 1274126177); h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }
  function vnoise(x, z, s) {
    const xi = Math.floor(x), zi = Math.floor(z), xf = x - xi, zf = z - zi;
    const u = xf * xf * (3 - 2 * xf), v = zf * zf * (3 - 2 * zf);
    const a = hash(xi, zi, s), b = hash(xi + 1, zi, s), c = hash(xi, zi + 1, s), d = hash(xi + 1, zi + 1, s);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
  }
  function fbm(x, z, s, oct) {
    let t = 0, amp = 1, sum = 0, f = 1;
    for (let i = 0; i < oct; i++) { t += vnoise(x * f, z * f, s + i * 31) * amp; sum += amp; amp *= 0.5; f *= 2; }
    return t / sum;
  }
  W.hash = hash;

  const pick = a => a[(rnd() * a.length) | 0];
  function nome(n) { let s = ''; for (let i = 0; i < n; i++) s += pick(SIL); return s[0].toUpperCase() + s.slice(1); }

  // ---------- terreno ----------
  const ti = (x, z) => (z | 0) * N + (x | 0);
  W.ti = ti;
  W.inside = (x, z) => x >= 0 && z >= 0 && x < N && z < N;
  function calcBio(t) {
    const x = t % N, z = (t / N) | 0, i = z * V + x, h = W.h;
    const a = (h[i] + h[i + 1] + h[i + V] + h[i + V + 1]) * 0.25;
    return a < 0.30 ? 0 : a < SEA ? 1 : a < 0.37 ? 2 : a < 0.58 ? 3 : a < 0.70 ? 4 : a < 0.83 ? 5 : 6;
  }
  const land = t => W.bio[t] >= 2;
  W.land = land;
  W.walk = (x, z) => x > 0.05 && z > 0.05 && x < N - 0.05 && z < N - 0.05 && W.bio[ti(x, z)] >= 2;
  W.hAt = function (x, z) {
    x = clamp(x, 0, N - 0.001); z = clamp(z, 0, N - 0.001);
    const h = W.h, xi = x | 0, zi = z | 0, fx = x - xi, fz = z - zi, i = zi * V + xi;
    const a = Math.max(h[i], SEA), b = Math.max(h[i + 1], SEA), c = Math.max(h[i + V], SEA), d = Math.max(h[i + V + 1], SEA);
    return (fx > fz ? a + (b - a) * fx + (d - b) * fz : a + (d - c) * fx + (c - a) * fz) * HS;
  };
  function cornerRange(t) {
    const x = t % N, z = (t / N) | 0, i = z * V + x, h = W.h;
    const a = Math.max(h[i], SEA), b = Math.max(h[i + 1], SEA), c = Math.max(h[i + V], SEA), d = Math.max(h[i + V + 1], SEA);
    return [Math.min(a, b, c, d), Math.max(a, b, c, d)];
  }
  W.cornerRange = cornerRange;
  function dirtyTile(t) { const x = t % N, z = (t / N) | 0; W.dirty[((z / CH) | 0) * NC + ((x / CH) | 0)] = 1; }
  W.dirtyTile = dirtyTile;
  function lineLand(x0, z0, x1, z1) {
    const d = Math.hypot(x1 - x0, z1 - z0), n = Math.ceil(d / 0.5);
    for (let i = 1; i <= n; i++) { const k = i / n; if (!land(ti(x0 + (x1 - x0) * k, z0 + (z1 - z0) * k))) return false; }
    return true;
  }
  function siteOk(t) {
    if (!land(t) || W.bio[t] > 5 || W.bld[t] || W.field[t] || W.road[t] || W.burn[t] > 0) return false;
    const r = cornerRange(t); return r[1] - r[0] < 0.05;
  }

  // ---------- criação do mundo ----------
  W.novo = function (seed) {
    seed = (seed >>> 0) || ((rnd() * 1e9) | 0);
    W.seed = seed;
    W.h = new Float32Array(V * V);
    W.tree = new Uint8Array(N * N); W.road = new Uint8Array(N * N); W.field = new Uint8Array(N * N);
    W.bld = new Int32Array(N * N); W.burn = new Float32Array(N * N); W.bio = new Uint8Array(N * N);
    W.B = new Map(); W.cities = []; W.civs = []; W.units = []; W.burnList = [];
    W.time = 0; W.nid = 1; W.acc = { slow: 0, war: 0, trees: 0, rebel: 0 };
    W.dirty = new Uint8Array(NC * NC).fill(1);
    W.events.length = 0;
    const ox = hash(1, 2, seed) * 500, oz = hash(3, 4, seed) * 500;
    for (let z = 0; z < V; z++) for (let x = 0; x < V; x++) {
      const nx = x / N * 2 - 1, nz = z / N * 2 - 1, d = Math.sqrt(nx * nx + nz * nz);
      const e = fbm((x + ox) / 34, (z + oz) / 34, seed, 5);
      const cont = vnoise((x + ox) / 70, (z + oz) / 70, seed + 7);
      const ridge = 1 - Math.abs(vnoise((x + ox) / 22, (z + oz) / 22, seed + 13) * 2 - 1);
      let v = e * 0.72 + cont * 0.38 - d * d * 0.46 + 0.04;
      if (v > 0.52) v += (v - 0.52) * ridge * 1.5;
      W.h[z * V + x] = clamp(v, 0, 1);
    }
    for (let t = 0; t < N * N; t++) W.bio[t] = calcBio(t);
    for (let t = 0; t < N * N; t++) {
      const x = t % N, z = (t / N) | 0, b = W.bio[t];
      const f = vnoise((x + ox) / 9, (z + oz) / 9, seed + 99);
      const p = b === 3 ? (f > 0.55 ? 0.7 : 0.04) : b === 4 ? (f > 0.45 ? 0.75 : 0.1) : b === 5 ? 0.12 : b === 2 ? 0.03 : b === 6 ? 0.04 : 0;
      if (rnd() < p) W.tree[t] = 1 + ((rnd() * 3) | 0);
    }
    const spots = [];
    for (let k = 0; k < 3; k++) {
      for (let tries = 0; tries < 400; tries++) {
        const x = 8 + ((rnd() * (N - 16)) | 0), z = 8 + ((rnd() * (N - 16)) | 0), t = ti(x, z);
        if (W.bio[t] !== 3 || spots.some(s => Math.hypot(s[0] - x, s[1] - z) < 30 - tries / 20)) continue;
        spots.push([x, z]); W.novoPovo(x + 0.5, z + 0.5, 7, true); break;
      }
    }
  };

  // ---------- povos e cidades ----------
  function mkCiv(era, sci) {
    const used = new Set(W.civs.filter(c => c.alive).map(c => c.ci));
    let ci = 0; while (used.has(ci) && ci < CORES.length - 1) ci++;
    const c = { id: W.civs.length, nm: nome(2 + (rnd() < 0.5 ? 1 : 0)), ci, color: CORES[ci], era: era || 0, sci: sci || 0, alive: true, war: {}, born: W.time };
    W.civs.push(c); return c;
  }
  function mkCity(civ, x, z, founded) {
    const c = { id: W.cities.length, civ: civ.id, nm: nome(2 + (rnd() < 0.4 ? 1 : 0)), x: x | 0, z: z | 0, food: 20, wood: 12, pop: 0, cap: 6,
      n: { casa: 0, fazenda: 0, escola: 0, fabrica: 0 }, nb: 0, fields: [], site: null, alive: true, center: 0, founded,
      planT: 1, birthT: 3, emptyT: 0, settleCD: 70 + rnd() * 50, hungry: false, threat: 0, born: W.time };
    W.cities.push(c); return c;
  }
  function mkUnit(c, x, z, age) {
    const civ = W.civs[c.civ];
    const u = { id: W.nid++, x, z, city: c.id, cv: c.civ, age, hp: 0, max: 30 + civ.era * 7, st: 'idle', t: rnd(), job: null,
      tx: x, tz: z, carry: 0, ct: '', sold: false, nm: pick(NOMES), skin: (rnd() * 4) | 0, dir: rnd() * 6.28, mv: false, car: false,
      foe: null, bt: 0, stuck: 0, alive: true, ctrl: false, chk: 0, work: 0 };
    u.hp = u.max; W.units.push(u); return u;
  }
  W.novoPovo = function (x, z, n, quiet) {
    const t = ti(x, z); if (!W.inside(x, z) || !land(t)) return null;
    const civ = mkCiv(0, 0), c = mkCity(civ, x, z, true);
    c.food = 25; c.wood = 15;
    placeAt(c, c.x, c.z, 'centro', 0);
    for (let i = 0; i < n; i++) mkUnit(c, x + (rnd() - 0.5) * 2, z + (rnd() - 0.5) * 2, 16 + rnd() * 14);
    if (!quiet) toast(`🧑 Nasceu o povo ${civ.nm}!`, civ);
    return civ;
  };
  W.addPessoas = function (x, z) {
    if (!W.inside(x, z) || !land(ti(x, z))) return 'agua';
    let best = null, bd = 9;
    for (const c of W.cities) if (c.alive) { const d = Math.hypot(c.x - x, c.z - z); if (d < bd) { bd = d; best = c; } }
    if (best) { for (let i = 0; i < 4; i++) if (W.units.length < W.maxUnits) mkUnit(best, x + rnd() - 0.5, z + rnd() - 0.5, 16 + rnd() * 10); return 'cidade'; }
    if (W.civs.filter(c => c.alive).length >= CORES.length) return 'cheio';
    W.novoPovo(x, z, 7); return 'povo';
  };

  function toast(txt, civ) { W.events.push({ txt, color: civ ? civ.color : null }); if (W.events.length > 30) W.events.shift(); }
  W.toast = toast;

  function cityRadius(c) { return 2.5 + Math.sqrt(c.nb) * 1.15; }
  W.cityRadius = cityRadius;

  function recount(c) {
    const n = { casa: 0, fazenda: 0, escola: 0, fabrica: 0 }; let cap = 0, nb = 0;
    for (const b of W.B.values()) if (b.city === c.id) {
      nb++; if (n[b.type] !== undefined) n[b.type]++;
      if (b.type === 'casa') cap += CAP[b.era]; else if (b.type === 'centro') cap += 6;
    }
    c.n = n; c.cap = Math.max(cap, 2); c.nb = nb;
  }
  W.recount = recount;

  function placeAt(c, x, z, type, era) {
    const t = ti(x, z), bt = BT[type];
    const b = { id: W.nid++, x: x | 0, z: z | 0, type, era, city: c.id, hp: 0, max: Math.round(bt.hp * (1 + era * 0.3)) };
    b.hp = b.max;
    W.B.set(b.id, b); W.bld[t] = b.id; W.tree[t] = 0; W.field[t] = 0; W.road[t] = 0; W.burn[t] = 0;
    if (type === 'centro') c.center = b.id;
    if (type === 'fazenda') {
      for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
        const fx = b.x + dx, fz = b.z + dz; if (!W.inside(fx, fz)) continue;
        const f = ti(fx, fz);
        if (f !== t && land(f) && W.bio[f] <= 4 && !W.bld[f] && !W.road[f] && !W.field[f]) { W.field[f] = 1; W.tree[f] = 0; c.fields.push(f); dirtyTile(f); }
      }
    }
    dirtyTile(t);
    if (type !== 'centro') layRoad(c, b.x, b.z, W.civs[c.civ].era);
    recount(c);
    return b;
  }
  W.placeAt = placeAt;

  function layRoad(c, x, z, era) {
    if (era < 2) return;
    const v = era + 1;
    const mark = (px, pz) => {
      const t = ti(px, pz);
      if (land(t) && !W.bld[t] && !W.field[t] && W.road[t] !== v) { W.road[t] = v; W.tree[t] = 0; dirtyTile(t); }
    };
    let px = x, pz = z;
    // caminho em "L": primeiro na horizontal, depois na vertical (forma quarteirões)
    const sx = Math.sign(c.x - x), sz = Math.sign(c.z - z);
    pz = z + sz; if (sz) mark(px, pz); else { px = x + sx; mark(px, pz); }
    while (pz !== c.z) { pz += sz; if (pz !== c.z || px !== c.x) mark(px, pz); }
    while (px !== c.x) { px += sx; if (px !== c.x || pz !== c.z) mark(px, pz); }
  }

  function destroyBuilding(b, byCiv) {
    if (!W.B.has(b.id)) return;
    W.B.delete(b.id);
    const t = ti(b.x, b.z); W.bld[t] = 0; dirtyTile(t);
    const c = W.cities[b.city];
    for (const k of W.cities) if (k.site && k.site.renov === b.id) releaseSite(k);
    if (b.type === 'centro') {
      c.center = 0;
      if (byCiv != null && byCiv !== c.civ && c.alive) capture(c, byCiv);
    }
    recount(c);
  }
  W.destroyBuilding = destroyBuilding;

  function capture(c, civId) {
    const old = W.civs[c.civ], nw = W.civs[civId];
    c.civ = civId; c.site = null;
    for (const u of W.units) if (u.city === c.id) { u.cv = civId; u.sold = false; u.st = 'idle'; u.t = 0.5; u.foe = null; }
    let k = 0; // alguns soldados vencedores passam a morar na cidade conquistada
    near(c.x + 0.5, c.z + 0.5, 9, o => { if (k < 4 && o.alive && o.cv === civId && o.city !== c.id && !o.ctrl) { abandon(o); o.city = c.id; o.sold = false; o.st = 'idle'; o.t = 0.5; k++; } });
    placeAt(c, c.x, c.z, 'centro', nw.era);
    for (const b of W.B.values()) if (b.city === c.id) dirtyTile(ti(b.x, b.z));
    toast(`⚔️ ${nw.nm} conquistou a cidade ${c.nm} dos ${old.nm}`, nw);
    checkCiv(old);
  }

  function checkCiv(civ) {
    if (!civ.alive) return;
    if (!W.cities.some(c => c.alive && c.civ === civ.id)) {
      civ.alive = false;
      for (const o of W.civs) { delete o.war[civ.id]; }
      civ.war = {};
      toast(`💀 O povo ${civ.nm} desapareceu`, civ);
    }
  }

  function abandonCity(c) {
    c.alive = false;
    for (const b of [...W.B.values()]) if (b.city === c.id) destroyBuilding(b, null);
    if (c.founded) toast(`🏚️ A cidade ${c.nm} foi abandonada`, W.civs[c.civ]);
    checkCiv(W.civs[c.civ]);
  }

  // ---------- planejamento das cidades ----------
  function costOf(type, era) { return Math.round(BT[type].cost * (1 + era * 0.25)); }
  W.costOf = costOf;
  function releaseSite(c) {
    if (!c.site) return;
    if (c.site.by) { c.wood += c.site.cost; const u = W.units.find(k => k.id === c.site.by); if (u && u.job === 'build') { u.job = null; u.st = 'idle'; u.t = 0.3; } }
    c.site = null;
  }
  function findSite(c, r) {
    for (let i = 0; i < 40; i++) {
      const a = rnd() * 6.283, d = 1 + Math.sqrt(rnd()) * r;
      const x = Math.round(c.x + Math.cos(a) * d), z = Math.round(c.z + Math.sin(a) * d);
      if (!W.inside(x, z)) continue;
      const t = ti(x, z);
      if (!siteOk(t)) continue;
      let far = true;
      for (const o of W.cities) if (o.alive && o !== c && Math.hypot(o.x - x, o.z - z) < 4) { far = false; break; }
      if (far && lineLand(c.x + 0.5, c.z + 0.5, x + 0.5, z + 0.5)) return [x, z];
    }
    return null;
  }
  function plan(c, civ) {
    if (c.site) { if (W.time - c.site.t0 > 45) releaseSite(c); return; }
    if (!c.founded || c.pop === 0) return;
    const e = civ.era;
    if (!c.center) {
      const t = ti(c.x, c.z);
      if (W.bld[t]) { const b = W.B.get(W.bld[t]); if (b) destroyBuilding(b, null); }
      placeAt(c, c.x, c.z, 'centro', e); return;
    }
    let type = null, renov = 0;
    if (c.pop + 2 >= c.cap) type = 'casa';
    else if (e >= 1 && c.n.fazenda < Math.ceil(c.pop / 8)) type = 'fazenda';
    else if (e >= 2 && c.n.escola < 1 + Math.floor(c.pop / 18)) type = 'escola';
    else if (e >= 5 && c.n.fabrica < Math.floor(c.pop / 16)) type = 'fabrica';
    else {
      for (const b of W.B.values()) if (b.city === c.id && b.era < e && b.type !== 'centro') { renov = b.id; type = b.type; break; }
      if (!renov) {
        const cb = W.B.get(c.center);
        if (cb && cb.era < e) { renov = cb.id; type = 'centro'; }
      }
      if (!type && rnd() < 0.3 && c.nb < 60) type = 'casa';
    }
    if (!type) return;
    let x, z;
    if (renov) { const b = W.B.get(renov); x = b.x; z = b.z; }
    else { const s = findSite(c, cityRadius(c)); if (!s) { c.planT = 6; return; } x = s[0]; z = s[1]; }
    const cost = Math.round(costOf(type === 'centro' ? 'casa' : type, e) * (renov ? 0.6 : 1));
    c.site = { x, z, type, era: e, cost, renov, by: 0, t0: W.time, time: BT[type].time };
  }
  function completeSite(c) {
    const s = c.site; c.site = null; if (!s) return;
    const civ = W.civs[c.civ];
    if (s.renov) {
      const b = W.B.get(s.renov); if (!b) return;
      b.era = civ.era; b.max = Math.round(BT[b.type].hp * (1 + b.era * 0.3)); b.hp = b.max;
      dirtyTile(ti(b.x, b.z)); recount(c);
      if (b.type !== 'centro') layRoad(c, b.x, b.z, civ.era);
      return;
    }
    const t = ti(s.x, s.z);
    if (!siteOk(t) && !(W.tree[t] && land(t) && !W.bld[t])) { c.wood += s.cost; return; }
    placeAt(c, s.x, s.z, s.type, civ.era);
  }
  W.buildNow = function (c, x, z, type) { // usado na primeira pessoa
    const civ = W.civs[c.civ], t = ti(x, z);
    if (!W.inside(x, z) || !siteOk(t)) return false;
    const cost = costOf(type, civ.era); if (c.wood < cost) return false;
    c.wood -= cost; placeAt(c, x, z, type, civ.era); return true;
  };
  W.siteOk = siteOk;

  function trySettle(c, civ) {
    if (c.pop < 20) return;
    let n = 0; for (const k of W.cities) if (k.alive && k.civ === civ.id) n++;
    if (n >= 10) return;
    for (let i = 0; i < 30; i++) {
      const a = rnd() * 6.283, d = 14 + rnd() * 12;
      const x = Math.round(c.x + Math.cos(a) * d), z = Math.round(c.z + Math.sin(a) * d);
      if (x < 3 || z < 3 || x > N - 4 || z > N - 4) continue;
      const t = ti(x, z);
      if (!(W.bio[t] === 3 || W.bio[t] === 4) || !siteOk(t)) continue;
      if (W.cities.some(o => o.alive && Math.hypot(o.x - x, o.z - z) < 12)) continue;
      if (!lineLand(c.x + 0.5, c.z + 0.5, x + 0.5, z + 0.5)) continue;
      const nc = mkCity(civ, x, z, false);
      nc.food = 12; nc.wood = 10; c.food = Math.max(0, c.food - 12); c.wood = Math.max(0, c.wood - 10);
      let k = 0;
      for (const u of W.units) {
        if (k >= 5) break;
        if (u.city === c.id && u.age >= 15 && u.age < 45 && !u.sold && !u.ctrl && u.job !== 'build') {
          abandon(u); u.city = nc.id; u.job = 'settle'; goTo(u, x + 0.5 + rnd() - 0.5, z + 0.5 + rnd() - 0.5); k++;
        }
      }
      toast(`🏕️ Colonos de ${c.nm} partiram para fundar ${nc.nm}`, civ);
      return;
    }
  }

  // ---------- pessoas ----------
  function goTo(u, x, z) {
    u.tx = clamp(x, 0.1, N - 0.1); u.tz = clamp(z, 0.1, N - 0.1); u.st = 'go'; u.stuck = 0;
    const civ = W.civs[u.cv];
    u.car = civ.era >= 6 && u.age >= 18 && !u.sold && Math.hypot(u.tx - u.x, u.tz - u.z) > 7;
  }
  W.goTo = goTo;
  function abandon(u) {
    if (u.job === 'build') { const c = W.cities[u.city]; if (c && c.site && c.site.by === u.id) { c.site.by = 0; c.wood += c.site.cost; } }
    u.job = null; u.foe = null;
  }
  function kill(u) {
    if (!u.alive) return;
    abandon(u); u.alive = false; u.hp = 0;
  }
  W.kill = kill;
  W.abandon = abandon;
  function wander(u, c, r) {
    u.job = 'wander';
    for (let i = 0; i < 6; i++) {
      const x = c.x + 0.5 + (rnd() - 0.5) * 2 * r, z = c.z + 0.5 + (rnd() - 0.5) * 2 * r;
      if (W.walk(x, z)) { goTo(u, x, z); return; }
    }
    u.st = 'idle'; u.t = 1 + rnd() * 2;
  }
  function foodJob(u, c) {
    let t = -1;
    if (c.fields.length) {
      for (let i = 0; i < 4; i++) { const f = pick(c.fields); if (W.field[f]) { t = f; break; } }
    }
    if (t < 0) {
      for (let i = 0; i < 14; i++) {
        const x = c.x + Math.round((rnd() - 0.5) * 14), z = c.z + Math.round((rnd() - 0.5) * 14);
        if (!W.inside(x, z)) continue;
        const k = ti(x, z); const b = W.bio[k];
        if ((b === 3 || b === 4) && !W.bld[k] && lineLand(u.x, u.z, x + 0.5, z + 0.5)) { t = k; break; }
      }
    }
    if (t < 0) return false;
    u.job = 'food'; goTo(u, t % N + 0.2 + rnd() * 0.6, ((t / N) | 0) + 0.2 + rnd() * 0.6); return true;
  }
  function woodJob(u, c) {
    for (let i = 0; i < 40; i++) {
      const r = 2 + i * 0.35, a = rnd() * 6.283;
      const x = Math.round(c.x + Math.cos(a) * r), z = Math.round(c.z + Math.sin(a) * r);
      if (!W.inside(x, z)) continue;
      const k = ti(x, z);
      if (W.tree[k] && !W.burn[k] && lineLand(u.x, u.z, x + 0.5, z + 0.5)) { u.job = 'wood'; u.bt = k; goTo(u, x + 0.5 + (rnd() - 0.5) * 0.3, z + 0.5 + (rnd() - 0.5) * 0.3); return true; }
    }
    return false;
  }
  function atWar(civ) { for (const k in civ.war) return true; return false; }
  W.atWar = atWar;
  function enemies(a, b) { return a !== b && W.civs[a].war[b] !== undefined; }
  W.enemies = enemies;
  function dmg(u) { return 7 + W.civs[u.cv].era * 2.5 * (u.sold ? 1 : 0.6); }
  W.dmg = dmg;

  // grade espacial: acha vizinhos rápido sem comparar todo mundo com todo mundo
  const grid = []; for (let i = 0; i < GN * GN; i++) grid.push([]);
  W.grid = grid;
  function rebuildGrid() {
    for (let i = 0; i < grid.length; i++) grid[i].length = 0;
    for (const u of W.units) if (u.alive) grid[clamp((u.z / GC) | 0, 0, GN - 1) * GN + clamp((u.x / GC) | 0, 0, GN - 1)].push(u);
  }
  function near(x, z, r, fn) {
    const x0 = clamp(((x - r) / GC) | 0, 0, GN - 1), x1 = clamp(((x + r) / GC) | 0, 0, GN - 1);
    const z0 = clamp(((z - r) / GC) | 0, 0, GN - 1), z1 = clamp(((z + r) / GC) | 0, 0, GN - 1);
    for (let gz = z0; gz <= z1; gz++) for (let gx = x0; gx <= x1; gx++) for (const o of grid[gz * GN + gx]) fn(o);
  }
  W.near = near;
  function nearestEnemy(u, r) {
    let best = null, bd = r;
    near(u.x, u.z, r, o => {
      if (!o.alive || !enemies(u.cv, o.cv)) return;
      const d = Math.hypot(o.x - u.x, o.z - u.z); if (d < bd) { bd = d; best = o; }
    });
    return best;
  }
  W.nearestEnemy = nearestEnemy;

  function soldierThink(u, c, civ) {
    const e = nearestEnemy(u, 9);
    if (e) { u.job = 'fight'; u.foe = e; goTo(u, e.x, e.z); return true; }
    let best = null, bd = 90;
    for (const ec of W.cities) if (ec.alive && ec.founded && enemies(civ.id, ec.civ)) {
      const d = Math.hypot(ec.x - u.x, ec.z - u.z); if (d < bd && lineLand(u.x, u.z, ec.x + 0.5, ec.z + 0.5)) { bd = d; best = ec; }
    }
    if (!best) return false;
    if (bd < 7) {
      let tb = null, tbd = 1e9;
      for (const b of W.B.values()) if (b.city === best.id) {
        const d = Math.hypot(b.x + 0.5 - u.x, b.z + 0.5 - u.z) + (b.type === 'centro' ? 2.5 : 0);
        if (d < tbd) { tbd = d; tb = b; }
      }
      if (tb) { u.job = 'siege'; u.bt = tb.id; goTo(u, tb.x + 0.5 + (rnd() - 0.5) * 0.8, tb.z + 0.5 + (rnd() - 0.5) * 0.8); return true; }
    }
    u.job = 'fight'; u.foe = null; goTo(u, best.x + 0.5 + (rnd() - 0.5) * 4, best.z + 0.5 + (rnd() - 0.5) * 4);
    return true;
  }

  function think(u, c, civ) {
    if (u.age < 12) { wander(u, c, 2.5); return; }
    if (u.sold && atWar(civ) && soldierThink(u, c, civ)) return;
    if (c.site && !c.site.by && c.wood >= c.site.cost) {
      c.site.by = u.id; c.wood -= c.site.cost; u.job = 'build';
      goTo(u, c.site.x + 0.5 + (rnd() - 0.5) * 0.4, c.site.z + 0.5 + (rnd() - 0.5) * 0.4); return;
    }
    const needFood = c.food < 12 + c.pop * 1.6, needWood = c.wood < 16 + c.pop * 0.6;
    const r = rnd();
    let ok = false;
    if (needFood && (r < 0.62 || !needWood)) ok = foodJob(u, c) || woodJob(u, c);
    else if (needWood) ok = woodJob(u, c) || foodJob(u, c);
    else if (r < 0.75) ok = r < 0.45 ? foodJob(u, c) : woodJob(u, c);
    if (!ok) wander(u, c, cityRadius(c) + 1);
  }

  function arrive(u, c, civ) {
    switch (u.job) {
      case 'food': u.st = 'work'; u.t = W.field[ti(u.x, u.z)] ? 1.6 : 2.6; break;
      case 'wood': if (W.tree[u.bt]) { u.st = 'work'; u.t = 2.6; } else { u.st = 'idle'; u.t = 0.2; } break;
      case 'deliver':
        if (u.ct === 'f') c.food += u.carry; else c.wood += u.carry;
        u.carry = 0; u.ct = ''; u.job = null; u.st = 'idle'; u.t = 0.3 + rnd() * 0.8; break;
      case 'build':
        if (c.site && c.site.by === u.id) { u.st = 'work'; u.t = c.site.time; } else { u.job = null; u.st = 'idle'; u.t = 0.3; }
        break;
      case 'settle':
        if (!c.founded) {
          let x = c.x, z = c.z;
          if (!siteOk(ti(x, z)) && !(W.tree[ti(x, z)] && !W.bld[ti(x, z)] && land(ti(x, z)))) {
            const s = findSite(c, 3); if (s) { x = s[0]; z = s[1]; c.x = x; c.z = z; }
          }
          c.founded = true; placeAt(c, x, z, 'centro', civ.era);
        }
        u.job = null; u.st = 'idle'; u.t = 0.5; break;
      case 'siege': u.st = 'work'; u.t = 99; break;
      default: u.job = null; u.st = 'idle'; u.t = 0.6 + rnd() * 2.4;
    }
  }

  function workDone(u, c, civ) {
    const e = civ.era;
    if (u.job === 'food') {
      const f = W.field[ti(u.x, u.z)];
      u.carry = Math.round((f ? 4 + e * 0.9 : 2 + e * 0.4) * 10) / 10; u.ct = 'f';
      u.job = 'deliver'; goTo(u, c.x + 0.5 + (rnd() - 0.5) * 1.4, c.z + 0.5 + (rnd() - 0.5) * 1.4);
    } else if (u.job === 'wood') {
      if (W.tree[u.bt]) { if (rnd() < 0.55) { W.tree[u.bt] = 0; dirtyTile(u.bt); } }
      u.carry = Math.round((3 + e * 0.5) * 10) / 10; u.ct = 'w';
      u.job = 'deliver'; goTo(u, c.x + 0.5 + (rnd() - 0.5) * 1.4, c.z + 0.5 + (rnd() - 0.5) * 1.4);
    } else if (u.job === 'build') {
      if (c.site && c.site.by === u.id) completeSite(c);
      u.job = null; u.st = 'idle'; u.t = 0.4;
    } else { u.job = null; u.st = 'idle'; u.t = 0.3; }
  }

  function move(u, dt) {
    const dx = u.tx - u.x, dz = u.tz - u.z, d = Math.hypot(dx, dz);
    if (d < 0.08) { u.x = u.tx; u.z = u.tz; return true; }
    const sp = (u.car ? 3.4 : 1.15) * (u.age < 12 ? 0.8 : 1) * (u.sold ? 1.15 : 1);
    const s = Math.min(d, sp * dt);
    let nx = u.x + dx / d * s, nz = u.z + dz / d * s;
    if (!W.walk(nx, nz)) {
      const a = Math.atan2(dz, dx); let ok = false;
      for (const off of [0.8, -0.8, 1.6, -1.6, 2.4, -2.4]) {
        const ax = u.x + Math.cos(a + off) * s, az = u.z + Math.sin(a + off) * s;
        if (W.walk(ax, az)) { nx = ax; nz = az; ok = true; break; }
      }
      if (!ok) { u.stuck += dt; if (u.stuck > 1) { abandon(u); u.st = 'idle'; u.t = 0.5; } return false; }
    }
    u.dir = Math.atan2(nx - u.x, nz - u.z); u.x = nx; u.z = nz; u.mv = true;
    return false;
  }

  function updUnit(u, dt) {
    u.mv = false;
    const t = ti(u.x, u.z);
    if (!land(t)) { // caiu na água: nada até a terra mais próxima ou se afoga
      u.hp -= dt * 6;
      if (!u.ctrl) {
        let best = null, bd = 9;
        for (let dz = -3; dz <= 3; dz++) for (let dx = -3; dx <= 3; dx++) {
          const x = (u.x | 0) + dx, z = (u.z | 0) + dz; if (!W.inside(x, z) || !land(ti(x, z))) continue;
          const d = dx * dx + dz * dz; if (d < bd) { bd = d; best = [x + 0.5, z + 0.5]; }
        }
        if (best) { const a = Math.atan2(best[1] - u.z, best[0] - u.x); u.x += Math.cos(a) * dt * 0.8; u.z += Math.sin(a) * dt * 0.8; }
      }
    }
    if (W.burn[t] > 0) u.hp -= dt * 14;
    let c = W.cities[u.city];
    if (!c.alive) { // cidade sumiu: procura outra do mesmo povo; se não houver, funda uma nova
      let best = null, bd = 1e9;
      for (const k of W.cities) if (k.alive && k.civ === u.cv) { const d = Math.hypot(k.x - u.x, k.z - u.z); if (d < bd) { bd = d; best = k; } }
      if (!best && land(t) && W.civs[u.cv]) {
        const civ = W.civs[u.cv]; civ.alive = true; best = mkCity(civ, u.x, u.z, false);
      }
      if (!best) { kill(u); return; }
      abandon(u); u.city = best.id; c = best; if (!best.founded) { u.job = 'settle'; goTo(u, best.x + 0.5, best.z + 0.5); }
    }
    u.cv = c.civ;
    const civ = W.civs[c.civ];
    if (c.hungry && !u.ctrl) u.hp -= dt * 0.7; else if (u.hp < u.max) u.hp = Math.min(u.max, u.hp + dt * 1.2);
    if (u.hp <= 0) { kill(u); return; }
    if (u.ctrl) return;
    u.age += dt / YEAR;
    if (u.age > 58 && rnd() < dt * 0.0012 * (u.age - 58)) { kill(u); return; }

    // luta corpo a corpo
    if (u.st === 'melee') {
      const o = u.foe;
      if (!o || !o.alive || Math.hypot(o.x - u.x, o.z - u.z) > 1.4 || !enemies(u.cv, o.cv)) { u.foe = null; u.st = 'idle'; u.t = 0.1; return; }
      u.dir = Math.atan2(o.x - u.x, o.z - u.z);
      o.hp -= dmg(u) * dt * (0.8 + rnd() * 0.4);
      if (!o.ctrl && o.st !== 'melee') { abandon(o); o.st = 'melee'; o.foe = u; }
      return;
    }
    if ((u.sold || c.threat > 0) && u.age >= 12) {
      u.chk -= dt;
      if (u.chk <= 0) {
        u.chk = 0.4 + rnd() * 0.3;
        const e = nearestEnemy(u, u.sold ? 1.1 : 0.9);
        if (e) { abandon(u); u.st = 'melee'; u.foe = e; return; }
        if (u.sold && u.job === 'fight' && u.foe && u.foe.alive && u.st === 'go') { u.tx = u.foe.x; u.tz = u.foe.z; }
      }
    }
    switch (u.st) {
      case 'idle':
        u.t -= dt; if (u.t <= 0) think(u, c, civ); break;
      case 'go':
        if (move(u, dt)) arrive(u, c, civ); break;
      case 'work':
        if (u.job === 'siege') {
          const b = W.B.get(u.bt);
          if (!b || !enemies(u.cv, W.cities[b.city].civ)) { u.job = null; u.st = 'idle'; u.t = 0.2; break; }
          u.work += dt; b.hp -= dmg(u) * dt * 0.7;
          if (b.hp <= 0) { destroyBuilding(b, u.cv); u.job = null; u.st = 'idle'; u.t = 0.2; }
          break;
        }
        u.work += dt; u.t -= dt; if (u.t <= 0) workDone(u, c, civ); break;
    }
  }

  // ---------- poderes ----------
  W.brush = function (x, z, r, amt) {
    const x0 = Math.max(0, Math.floor(x - r)), x1 = Math.min(N, Math.ceil(x + r));
    const z0 = Math.max(0, Math.floor(z - r)), z1 = Math.min(N, Math.ceil(z + r));
    for (let vz = z0; vz <= z1; vz++) for (let vx = x0; vx <= x1; vx++) {
      const d = Math.hypot(vx - x, vz - z); if (d >= r) continue;
      const k = 1 - d / r, i = vz * V + vx;
      W.h[i] = clamp(W.h[i] + amt * k * k, 0, 1);
    }
    for (let tz = Math.max(0, z0 - 1); tz < Math.min(N, z1 + 1); tz++) for (let tx = Math.max(0, x0 - 1); tx < Math.min(N, x1 + 1); tx++) {
      const t = tz * N + tx, b = calcBio(t);
      if (b !== W.bio[t]) {
        W.bio[t] = b;
        if (b < 2) {
          W.tree[t] = 0; W.road[t] = 0; W.field[t] = 0;
          if (W.bld[t]) { const bb = W.B.get(W.bld[t]); if (bb) destroyBuilding(bb, null); }
        }
      }
      dirtyTile(t);
    }
  };
  W.plantar = function (x, z, r) {
    for (let tz = Math.floor(z - r); tz <= z + r; tz++) for (let tx = Math.floor(x - r); tx <= x + r; tx++) {
      if (!W.inside(tx, tz) || Math.hypot(tx + 0.5 - x, tz + 0.5 - z) > r) continue;
      const t = ti(tx, tz);
      if (land(t) && !W.tree[t] && !W.bld[t] && !W.road[t] && !W.field[t] && rnd() < 0.3) { W.tree[t] = 1 + ((rnd() * 3) | 0); dirtyTile(t); }
    }
  };
  function ignite(t) {
    if (!land(t) || W.burn[t] > 0) return;
    W.burn[t] = 4 + rnd() * 5; W.burnList.push(t); dirtyTile(t);
  }
  W.ignite = ignite;
  W.raio = function (x, z) {
    near(x, z, 1.5, o => { if (Math.hypot(o.x - x, o.z - z) < 1.3) o.hp -= 200; });
    for (let dz = -1; dz <= 1; dz++) for (let dx = -1; dx <= 1; dx++) {
      const px = (x | 0) + dx, pz = (z | 0) + dz; if (!W.inside(px, pz)) continue;
      const t = ti(px, pz);
      if ((dx === 0 && dz === 0) || W.tree[t] || W.bld[t]) if (rnd() < 0.8) ignite(t);
      if (W.bld[t]) { const b = W.B.get(W.bld[t]); if (b) { b.hp -= 40; if (b.hp <= 0) destroyBuilding(b, null); } }
    }
  };
  W.meteoro = function (x, z) {
    const R = 5;
    near(x, z, R + 1, o => { if (Math.hypot(o.x - x, o.z - z) < R + 0.5) o.hp -= 999; });
    for (const b of [...W.B.values()]) if (Math.hypot(b.x + 0.5 - x, b.z + 0.5 - z) < R - 0.5) destroyBuilding(b, null);
    W.brush(x, z, R, -0.16);
    for (let i = 0; i < 40; i++) {
      const a = rnd() * 6.283, d = rnd() * (R + 3), px = x + Math.cos(a) * d, pz = z + Math.sin(a) * d;
      if (!W.inside(px, pz)) continue;
      const t = ti(px, pz); if (W.tree[t] || W.bld[t] || rnd() < 0.2) ignite(t);
    }
  };
  W.inspirar = function (x, z) {
    let best = null, bd = 25;
    for (const c of W.cities) if (c.alive) { const d = Math.hypot(c.x - x, c.z - z); if (d < bd) { bd = d; best = c; } }
    if (!best) return null;
    const civ = W.civs[best.civ], nx = ERAS[civ.era + 1];
    if (!nx) return { civ, max: true };
    civ.sci += (nx.custo - ERAS[civ.era].custo) * 0.3;
    return { civ };
  };

  // ---------- passo da simulação ----------
  function eraUp(civ) {
    civ.era++;
    const e = ERAS[civ.era];
    toast(`${e.ic} O povo ${civ.nm} entrou na ${e.nome}!`, civ);
    for (const u of W.units) if (u.alive && u.cv === civ.id) { const m = 30 + civ.era * 7; u.hp += m - u.max; u.max = m; }
    if (civ.era >= 2) for (const c of W.cities) if (c.alive && c.civ === civ.id) {
      const r = Math.ceil(cityRadius(c)) + 2;
      for (let z = c.z - r; z <= c.z + r; z++) for (let x = c.x - r; x <= c.x + r; x++) {
        if (!W.inside(x, z)) continue; const t = ti(x, z);
        if (W.road[t] && W.road[t] < civ.era + 1) { W.road[t] = civ.era + 1; dirtyTile(t); }
      }
    }
  }

  function updWars() {
    const alive = W.civs.filter(c => c.alive);
    for (let i = 0; i < alive.length; i++) for (let j = i + 1; j < alive.length; j++) {
      const a = alive[i], b = alive[j];
      if (a.war[b.id] !== undefined) {
        if (W.time > a.war[b.id]) {
          delete a.war[b.id]; delete b.war[a.id];
          toast(`🕊️ Paz entre ${a.nm} e ${b.nm}`, a);
          for (const u of W.units) if ((u.cv === a.id && !atWar(a)) || (u.cv === b.id && !atWar(b))) { u.sold = false; if (u.job === 'fight' || u.job === 'siege' || u.st === 'melee') { u.job = null; u.st = 'idle'; u.foe = null; } }
        }
        continue;
      }
      if (W.time < 90) continue;
      let md = 1e9;
      for (const ca of W.cities) if (ca.alive && ca.civ === a.id) for (const cb of W.cities) if (cb.alive && cb.civ === b.id) md = Math.min(md, Math.hypot(ca.x - cb.x, ca.z - cb.z));
      if (md > 46) continue;
      const peace = Math.max(a.era, b.era) >= 6 ? 0.35 : 1;
      if (rnd() < 0.02 * peace) {
        const end = W.time + 60 + rnd() * 100;
        a.war[b.id] = end; b.war[a.id] = end;
        toast(`⚔️ Guerra entre ${a.nm} e ${b.nm}!`, a);
        for (const u of W.units) if (u.alive && (u.cv === a.id || u.cv === b.id) && u.age >= 15 && u.age < 50 && !u.ctrl && rnd() < 0.35) { abandon(u); u.sold = true; u.st = 'idle'; u.t = rnd(); }
      }
    }
  }

  function updFire(dt) {
    const L = W.burnList;
    for (let i = L.length - 1; i >= 0; i--) {
      const t = L[i];
      W.burn[t] -= dt;
      if (W.bld[t]) { const b = W.B.get(W.bld[t]); if (b) { b.hp -= dt * 9; if (b.hp <= 0) destroyBuilding(b, null); } }
      if (rnd() < dt * 0.45) {
        const x = t % N, z = (t / N) | 0, dx = ((rnd() * 3) | 0) - 1, dz = ((rnd() * 3) | 0) - 1;
        if (W.inside(x + dx, z + dz)) { const n = ti(x + dx, z + dz); if (W.tree[n] || W.bld[n]) ignite(n); }
      }
      if (W.burn[t] <= 0) {
        W.burn[t] = 0; if (W.tree[t]) W.tree[t] = 0;
        dirtyTile(t); L[i] = L[L.length - 1]; L.pop();
      }
    }
  }

  function growTrees() {
    for (let i = 0; i < 16; i++) {
      const t = (rnd() * N * N) | 0, b = W.bio[t];
      if (b < 3 || b > 5 || W.tree[t] || W.bld[t] || W.road[t] || W.field[t] || W.burn[t]) continue;
      const x = t % N, z = (t / N) | 0;
      const nb = (x > 0 && W.tree[t - 1]) || (x < N - 1 && W.tree[t + 1]) || (z > 0 && W.tree[t - N]) || (z < N - 1 && W.tree[t + N]);
      if (nb ? rnd() < 0.35 : rnd() < 0.01) { W.tree[t] = 1 + ((rnd() * 3) | 0); dirtyTile(t); }
    }
  }

  function rebellion() {
    for (const civ of W.civs) {
      if (!civ.alive) continue;
      const cs = W.cities.filter(c => c.alive && c.civ === civ.id && c.founded);
      if (cs.length < 5 || W.civs.filter(c => c.alive).length >= CORES.length) continue;
      const cap = cs[0];
      let far = null, fd = 30;
      for (const c of cs) { const d = Math.hypot(c.x - cap.x, c.z - cap.z); if (d > fd && c.pop >= 12) { fd = d; far = c; } }
      if (!far || rnd() > 0.3) continue;
      const nc = mkCiv(civ.era, civ.sci);
      far.civ = nc.id; far.site = null;
      for (const u of W.units) if (u.city === far.id) { u.cv = nc.id; u.sold = false; }
      for (const b of W.B.values()) if (b.city === far.id) dirtyTile(ti(b.x, b.z));
      toast(`🚩 ${far.nm} declarou independência: nasce o povo ${nc.nm}!`, nc);
      if (rnd() < 0.5) { const end = W.time + 60 + rnd() * 60; civ.war[nc.id] = end; nc.war[civ.id] = end; toast(`⚔️ ${civ.nm} luta para recuperar ${far.nm}!`, civ); }
      return;
    }
  }

  W.step = function (dt) {
    W.time += dt;
    rebuildGrid();
    const cities = W.cities;
    for (const c of cities) { c.pop = 0; c.threat = 0; }
    for (const u of W.units) if (u.alive) cities[u.city].pop++;
    // ameaça: soldados inimigos perto do centro da cidade
    for (const c of cities) if (c.alive && c.founded && atWar(W.civs[c.civ])) {
      near(c.x + 0.5, c.z + 0.5, 6, o => { if (o.sold && enemies(o.cv, c.civ)) c.threat++; });
    }
    const units = W.units;
    for (let i = 0; i < units.length; i++) { const u = units[i]; if (u.alive) updUnit(u, dt); }
    let k = 0; for (let i = 0; i < units.length; i++) if (units[i].alive) units[k++] = units[i];
    units.length = k;

    const sci = new Float32Array(W.civs.length);
    for (const c of cities) {
      if (!c.alive) continue;
      const civ = W.civs[c.civ];
      c.food -= c.pop * 0.03 * dt;
      c.hungry = c.food < 0; if (c.food < 0) c.food = 0;
      c.wood += c.n.fabrica * 0.3 * dt;
      if (c.food > 60 + c.pop * 6) c.food = 60 + c.pop * 6; // celeiros e depósitos têm limite
      if (c.wood > 80 + c.pop * 5) c.wood = 80 + c.pop * 5;
      sci[c.civ] += c.pop * 0.035 + c.n.escola * 0.55 + c.n.fabrica * 0.4;
      c.birthT -= dt;
      if (c.birthT <= 0) {
        c.birthT = 3 + rnd() * 3;
        if (c.founded && c.pop >= 2 && c.food > 8 + c.pop * 0.4 && c.pop < c.cap && W.units.length < W.maxUnits) {
          mkUnit(c, c.x + 0.5 + (rnd() - 0.5), c.z + 0.5 + (rnd() - 0.5), 0); c.food -= 4;
        }
      }
      c.planT -= dt; if (c.planT <= 0) { c.planT = 2 + rnd(); plan(c, civ); }
      c.settleCD -= dt; if (c.settleCD <= 0) { c.settleCD = 50 + rnd() * 40; if (c.founded) trySettle(c, civ); }
      if (c.pop === 0) { c.emptyT += dt; if (c.emptyT > 25) abandonCity(c); } else c.emptyT = 0;
    }
    for (const civ of W.civs) {
      if (!civ.alive) continue;
      civ.sci += sci[civ.id] * dt;
      const nx = ERAS[civ.era + 1]; if (nx && civ.sci >= nx.custo) eraUp(civ);
    }
    W.acc.war += dt; if (W.acc.war > 5) { W.acc.war = 0; updWars(); }
    W.acc.trees += dt; if (W.acc.trees > 1) { W.acc.trees = 0; growTrees(); }
    W.acc.rebel += dt; if (W.acc.rebel > 30) { W.acc.rebel = 0; rebellion(); }
    if (W.burnList.length) updFire(dt);
  };

  W.stats = function () {
    let pop = 0, cid = 0; for (const u of W.units) if (u.alive) pop++;
    for (const c of W.cities) if (c.alive) cid++;
    return { pop, cities: cid, civs: W.civs.filter(c => c.alive).length, ano: Math.floor(W.time / YEAR) };
  };

  // ---------- salvar / carregar ----------
  function b64(arr) {
    const u8 = new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength);
    if (typeof Buffer !== 'undefined' && typeof btoa === 'undefined') return Buffer.from(u8).toString('base64');
    let s = ''; for (let i = 0; i < u8.length; i += 8192) s += String.fromCharCode.apply(null, u8.subarray(i, i + 8192));
    return btoa(s);
  }
  function unb64(s, Type) {
    let u8;
    if (typeof Buffer !== 'undefined' && typeof atob === 'undefined') u8 = new Uint8Array(Buffer.from(s, 'base64'));
    else { const b = atob(s); u8 = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) u8[i] = b.charCodeAt(i); }
    return new Type(u8.buffer);
  }
  W.salvar = function () {
    const hq = new Uint16Array(V * V); for (let i = 0; i < hq.length; i++) hq[i] = Math.round(W.h[i] * 65535);
    const UK = ['id', 'x', 'z', 'city', 'cv', 'age', 'hp', 'max', 'carry', 'ct', 'sold', 'nm', 'skin'];
    return JSON.stringify({
      v: 1, seed: W.seed, time: W.time, nid: W.nid,
      h: b64(hq), tree: b64(W.tree), road: b64(W.road), field: b64(W.field),
      civs: W.civs, cities: W.cities.map(c => Object.assign({}, c, { site: null })),
      B: [...W.B.values()].map(b => [b.id, b.x, b.z, b.type, b.era, b.city, Math.round(b.hp), b.max]),
      U: W.units.filter(u => u.alive).map(u => UK.map(k => typeof u[k] === 'number' ? Math.round(u[k] * 100) / 100 : u[k])), UK,
    });
  };
  W.carregar = function (str) {
    const d = JSON.parse(str); if (!d || d.v !== 1) return false;
    W.seed = d.seed; W.time = d.time; W.nid = d.nid;
    const hq = unb64(d.h, Uint16Array); W.h = new Float32Array(V * V); for (let i = 0; i < hq.length; i++) W.h[i] = hq[i] / 65535;
    W.tree = unb64(d.tree, Uint8Array); W.road = unb64(d.road, Uint8Array); W.field = unb64(d.field, Uint8Array);
    W.bld = new Int32Array(N * N); W.burn = new Float32Array(N * N); W.bio = new Uint8Array(N * N); W.burnList = [];
    for (let t = 0; t < N * N; t++) W.bio[t] = calcBio(t);
    W.civs = d.civs; W.cities = d.cities;
    W.B = new Map();
    for (const a of d.B) { const b = { id: a[0], x: a[1], z: a[2], type: a[3], era: a[4], city: a[5], hp: a[6], max: a[7] }; W.B.set(b.id, b); W.bld[ti(b.x, b.z)] = b.id; }
    W.units = [];
    for (const a of d.U) {
      const u = { st: 'idle', t: rnd(), job: null, tx: 0, tz: 0, dir: 0, mv: false, car: false, foe: null, bt: 0, stuck: 0, alive: true, ctrl: false, chk: 0, work: 0 };
      d.UK.forEach((k, i) => { u[k] = a[i]; }); u.tx = u.x; u.tz = u.z;
      if (u.carry > 0) { u.job = 'deliver'; const c = W.cities[u.city]; if (c) goTo(u, c.x + 0.5, c.z + 0.5); }
      W.units.push(u);
    }
    for (const c of W.cities) if (c.alive) recount(c);
    W.acc = { slow: 0, war: 0, trees: 0, rebel: 0 };
    W.dirty = new Uint8Array(NC * NC).fill(1);
    W.events.length = 0;
    return true;
  };

  root.W = W;
  if (typeof module !== 'undefined') module.exports = W;
})(typeof window !== 'undefined' ? window : globalThis);
