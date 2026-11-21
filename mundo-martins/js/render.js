'use strict';
// Motor gráfico leve em WebGL2.
// - Cada vértice tem só posição (12 bytes) + cor (4 bytes): o sombreamento facetado é calculado no shader
//   a partir das derivadas da tela, sem precisar guardar normais (metade da memória de vídeo).
// - O quarto byte da cor é um "tipo": normal, janela que acende à noite, água com brilho, ou emissivo (fogo).

const M4 = {
  persp(fov, a, n, f) {
    const t = 1 / Math.tan(fov / 2), nf = 1 / (n - f);
    return new Float32Array([t / a, 0, 0, 0, 0, t, 0, 0, 0, 0, (f + n) * nf, -1, 0, 0, 2 * f * n * nf, 0]);
  },
  look(e, c) {
    let zx = e[0] - c[0], zy = e[1] - c[1], zz = e[2] - c[2], l = Math.hypot(zx, zy, zz);
    zx /= l; zy /= l; zz /= l;
    let xx = zz, xy = 0, xz = -zx; l = Math.hypot(xx, xz) || 1; xx /= l; xz /= l; // up = (0,1,0)
    const yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
    return new Float32Array([xx, yx, zx, 0, xy, yy, zy, 0, xz, yz, zz, 0,
      -(xx * e[0] + xy * e[1] + xz * e[2]), -(yx * e[0] + yy * e[1] + yz * e[2]), -(zx * e[0] + zy * e[1] + zz * e[2]), 1]);
  },
  mul(a, b) {
    const o = new Float32Array(16);
    for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) {
      o[i * 4 + j] = a[j] * b[i * 4] + a[4 + j] * b[i * 4 + 1] + a[8 + j] * b[i * 4 + 2] + a[12 + j] * b[i * 4 + 3];
    }
    return o;
  },
};

// Tipos de vértice (vão no canal alfa)
const F_NORMAL = 255, F_NOITE = 204, F_AGUA = 153, F_EMIS = 51;
function rgb(r, g, b, f) { return (((f === undefined ? 255 : f) << 24) | ((b & 255) << 16) | ((g & 255) << 8) | (r & 255)) >>> 0; }
function rgbm(c, m, f) { return rgb(Math.min(255, c[0] * m) | 0, Math.min(255, c[1] * m) | 0, Math.min(255, c[2] * m) | 0, f); }
function mix(a, b, k) { return [a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k]; }

// índices dos 30 vértices de uma caixa: bits 0-1 = canto (a,b,c,d), bit 2 = em cima
const BOXX = new Float64Array(4), BOXZ = new Float64Array(4), BOXY = new Float64Array(2);
const BOXI = (() => {
  const T = 4, a = [];
  a.push(0 | T, 1 | T, 2 | T, 0 | T, 2 | T, 3 | T);
  for (let s = 0; s < 4; s++) { const p = s, q = (s + 1) & 3; a.push(p, q, q | T, p, q | T, p | T); }
  return new Uint8Array(a);
})();

class MeshW {
  constructor(cap) { this.alloc(cap || 4096); this.n = 0; }
  alloc(cap) { this.buf = new ArrayBuffer(cap * 16); this.f = new Float32Array(this.buf); this.u = new Uint32Array(this.buf); this.cap = cap; }
  reset() { this.n = 0; }
  need(k) {
    if (this.n + k <= this.cap) return;
    const old = new Uint8Array(this.buf, 0, this.n * 16);
    this.alloc(Math.max(this.cap * 2, this.n + k));
    new Uint8Array(this.buf).set(old);
  }
  v(x, y, z, c) { const i = this.n * 4; this.f[i] = x; this.f[i + 1] = y; this.f[i + 2] = z; this.u[i + 3] = c; this.n++; }
  tri(ax, ay, az, bx, by, bz, cx, cy, cz, c) { this.need(3); this.v(ax, ay, az, c); this.v(bx, by, bz, c); this.v(cx, cy, cz, c); }
  // caixa sem a face de baixo (nunca aparece), com giro opcional em torno do eixo vertical
  box(x, y, z, sx, sy, sz, c, yaw) {
    this.need(30);
    const hx = sx / 2, hz = sz / 2, y1 = y + sy;
    let cs = 1, sn = 0; if (yaw) { cs = Math.cos(yaw); sn = Math.sin(yaw); }
    const ax = x - hx * cs + hz * sn, az = z - hx * sn - hz * cs;
    const bx = x + hx * cs + hz * sn, bz = z + hx * sn - hz * cs;
    const cx = x + hx * cs - hz * sn, cz = z + hx * sn + hz * cs;
    const dx = x - hx * cs - hz * sn, dz = z - hx * sn + hz * cs;
    // escreve direto no buffer (sem criar objetos temporários: menos pausas do coletor de lixo no celular)
    const f = this.f, u = this.u; let i = this.n * 4;
    const X = BOXX, Y = BOXY, Z = BOXZ;
    X[0] = ax; Z[0] = az; X[1] = bx; Z[1] = bz; X[2] = cx; Z[2] = cz; X[3] = dx; Z[3] = dz; Y[0] = y; Y[1] = y1;
    for (let k = 0; k < 30; k++) { const q = BOXI[k]; f[i] = X[q & 3]; f[i + 1] = Y[q >> 2]; f[i + 2] = Z[q & 3]; u[i + 3] = c; i += 4; }
    this.n += 30;
  }
  // pirâmide de base retangular
  pyr(x, y, z, sx, sz, h, c, yaw) {
    this.need(12);
    const hx = sx / 2, hz = sz / 2, ty = y + h;
    let cs = 1, sn = 0; if (yaw) { cs = Math.cos(yaw); sn = Math.sin(yaw); }
    const P = [[-hx, -hz], [hx, -hz], [hx, hz], [-hx, hz]].map(([lx, lz]) => [x + lx * cs - lz * sn, z + lx * sn + lz * cs]);
    for (let i = 0; i < 4; i++) { const a = P[i], b = P[(i + 1) & 3]; this.v(a[0], y, a[1], c); this.v(b[0], y, b[1], c); this.v(x, ty, z, c); }
  }
  // telhado de duas águas (cumeeira no eixo x local)
  roof(x, y, z, sx, sz, h, c, yaw) {
    this.need(18);
    const hx = sx / 2, hz = sz / 2, ty = y + h;
    let cs = 1, sn = 0; if (yaw) { cs = Math.cos(yaw); sn = Math.sin(yaw); }
    const p = (lx, lz) => [x + lx * cs - lz * sn, z + lx * sn + lz * cs];
    const a = p(-hx, -hz), b = p(hx, -hz), cc = p(hx, hz), d = p(-hx, hz), r0 = p(-hx, 0), r1 = p(hx, 0);
    const v = (q, py) => this.v(q[0], py, q[1], c);
    v(a, y); v(b, y); v(r1, ty); v(a, y); v(r1, ty); v(r0, ty);
    v(d, y); v(cc, y); v(r1, ty); v(d, y); v(r1, ty); v(r0, ty);
    v(a, y); v(d, y); v(r0, ty); v(b, y); v(cc, y); v(r1, ty);
  }
}

const R = (() => {
  let gl = null, prog = null, U = {};
  const VS = `#version 300 es
precision highp float;
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aCol;
uniform mat4 uVP;
out vec3 vW; out vec4 vC;
void main(){ vW = aPos; vC = aCol; gl_Position = uVP * vec4(aPos, 1.0); }`;
  const FS = `#version 300 es
precision highp float;
in vec3 vW; in vec4 vC;
uniform vec3 uSun, uSunCol, uAmb, uFog, uCam;
uniform vec2 uFogR;
uniform float uNight, uTime;
out vec4 o;
void main(){
  vec3 n = normalize(cross(dFdx(vW), dFdy(vW)));
  float f = vC.a, d = max(dot(n, uSun), 0.0);
  vec3 lit = vC.rgb * (uAmb + uSunCol * d), col;
  if (f > 0.9) col = lit;
  else if (f > 0.7) col = mix(lit, vC.rgb * 0.35 + vec3(1.0, 0.8, 0.42) * 0.75, uNight);
  else if (f > 0.45) {
    float s = sin(vW.x * 2.3 + uTime * 1.6) * sin(vW.z * 1.9 - uTime * 1.2);
    vec3 v = normalize(uCam - vW), h = normalize(v + uSun);
    float sp = pow(max(h.y, 0.0), 80.0) * 0.7;
    col = vC.rgb * (1.0 + 0.07 * s) * (uAmb + uSunCol * max(uSun.y, 0.0)) + uSunCol * sp;
  } else col = vC.rgb;
  float fog = smoothstep(uFogR.x, uFogR.y, length(uCam - vW));
  o = vec4(mix(col, uFog, fog), 1.0);
}`;

  function sh(type, src) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    return s;
  }
  function init(canvas) {
    gl = canvas.getContext('webgl2', { antialias: false, alpha: false, depth: true, stencil: false, powerPreference: 'high-performance', preserveDrawingBuffer: false });
    if (!gl) return false;
    prog = gl.createProgram();
    gl.attachShader(prog, sh(gl.VERTEX_SHADER, VS)); gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, FS));
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));
    for (const k of ['uVP', 'uSun', 'uSunCol', 'uAmb', 'uFog', 'uCam', 'uFogR', 'uNight', 'uTime']) U[k] = gl.getUniformLocation(prog, k);
    gl.enable(gl.DEPTH_TEST);
    gl.disable(gl.CULL_FACE);
    return true;
  }
  function mesh() {
    const vao = gl.createVertexArray(), vbo = gl.createBuffer();
    gl.bindVertexArray(vao); gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.FLOAT, false, 16, 0);
    gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 4, gl.UNSIGNED_BYTE, true, 16, 12);
    gl.bindVertexArray(null);
    return { vao, vbo, n: 0, cap: 0 };
  }
  function upload(m, w, dynamic) {
    gl.bindBuffer(gl.ARRAY_BUFFER, m.vbo);
    const bytes = w.n * 16, view = new Uint8Array(w.buf, 0, bytes);
    if (!dynamic) gl.bufferData(gl.ARRAY_BUFFER, view, gl.STATIC_DRAW);
    else {
      if (bytes > m.cap) { m.cap = Math.max(bytes * 1.5 | 0, 1 << 18); gl.bufferData(gl.ARRAY_BUFFER, m.cap, gl.DYNAMIC_DRAW); }
      if (bytes) gl.bufferSubData(gl.ARRAY_BUFFER, 0, view);
    }
    m.n = w.n;
  }
  function free(m) { if (!m) return; gl.deleteBuffer(m.vbo); gl.deleteVertexArray(m.vao); }
  function begin(w, h, env) {
    gl.viewport(0, 0, w, h);
    gl.clearColor(env.sky[0], env.sky[1], env.sky[2], 1);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
    gl.useProgram(prog);
    gl.uniformMatrix4fv(U.uVP, false, env.vp);
    gl.uniform3fv(U.uSun, env.sun); gl.uniform3fv(U.uSunCol, env.sunCol); gl.uniform3fv(U.uAmb, env.amb);
    gl.uniform3fv(U.uFog, env.sky); gl.uniform3fv(U.uCam, env.cam); gl.uniform2fv(U.uFogR, env.fog);
    gl.uniform1f(U.uNight, env.night); gl.uniform1f(U.uTime, env.time);
  }
  function draw(m) { if (!m || !m.n) return; gl.bindVertexArray(m.vao); gl.drawArrays(gl.TRIANGLES, 0, m.n); }
  return { init, mesh, upload, free, begin, draw, get gl() { return gl; } };
})();
