"use strict";

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const INTERVALS = ["1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d"];
const PARAM_KEYS = ["fastEMA", "slowEMA", "trendEMA", "rsiPeriod", "rsiMaxBuy", "stopLossPct", "takeProfitPct"];

let S = null;          // último status
let cfgLoaded = false; // formulário de ajustes já preenchido
let lastBT = null;     // último backtest + parâmetros usados

// ---------- utilidades ----------
async function api(path, body) {
  const opt = body === undefined ? {} : {
    method: "POST", headers: { "Content-Type": "application/json", "X-TradeMartins": "1" }, body: JSON.stringify(body),
  };
  const r = await fetch("/api/" + path, opt);
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || "erro " + r.status);
  return j;
}

const nf = (d) => new Intl.NumberFormat("pt-BR", { minimumFractionDigits: d, maximumFractionDigits: d });
function num(v, d = 2) { return v == null || isNaN(v) ? "—" : nf(d).format(v); }
function priceFmt(v) { return v >= 100 ? num(v, 2) : v >= 1 ? num(v, 4) : num(v, 8); }
function qtyFmt(v) { return new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 8 }).format(v); }
function money(v, q) {
  if (v == null || isNaN(v)) return "—";
  if (q === "BRL") return "R$ " + num(v, 2);
  return num(v, 2) + " " + (q || "");
}
function signed(v, q) { return (v > 0 ? "+" : "") + money(v, q); }
function pct(v) { return (v > 0 ? "+" : "") + num(v, 2) + "%"; }
function cls(v) { return v > 0 ? "pos" : v < 0 ? "neg" : ""; }
function dt(ms) {
  return new Date(ms).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "2-digit", hour: "2-digit", minute: "2-digit" });
}
function hm(ms) { return new Date(ms).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" }); }
function esc(s) { return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])); }

let toastT;
function toast(msg, err) {
  const t = $("#toast");
  t.textContent = msg;
  t.className = "toast" + (err ? " err" : "");
  t.hidden = false;
  clearTimeout(toastT);
  toastT = setTimeout(() => (t.hidden = true), err ? 6000 : 3000);
}

// Caixa de confirmação no visual do app. requireText: palavra que precisa ser digitada.
function ask(title, html, { okText = "OK", danger = false, requireText = "" } = {}) {
  return new Promise((resolve) => {
    const d = $("#dlg");
    $("#dlg-title").textContent = title;
    $("#dlg-body").innerHTML = html + (requireText ? `<input id="dlg-input" autocomplete="off" placeholder="digite ${esc(requireText)}">` : "");
    const ok = $("#dlg-ok");
    ok.textContent = okText;
    ok.className = "btn " + (danger ? "danger" : "primary");
    const inp = $("#dlg-input");
    if (inp) {
      ok.disabled = true;
      inp.oninput = () => (ok.disabled = inp.value.trim().toUpperCase() !== requireText);
    } else ok.disabled = false;
    d.onclose = () => resolve(d.returnValue === "ok");
    d.returnValue = "";
    d.showModal();
    (inp || ok).focus();
  });
}

async function act(btn, fn) {
  if (btn) btn.disabled = true;
  try { return await fn(); }
  catch (e) { toast(e.message, true); }
  finally { if (btn) btn.disabled = false; }
}

// ---------- abas ----------
$$(".tabs button").forEach((b) => b.addEventListener("click", () => {
  $$(".tabs button").forEach((x) => x.classList.toggle("on", x === b));
  $$(".tab").forEach((x) => x.classList.toggle("on", x.id === b.dataset.tab));
  if (b.dataset.tab === "painel" && S) drawChart();
  if (b.dataset.tab === "testar" && lastBT) drawBT();
}));
$$("select[name=interval]").forEach((s) => (s.innerHTML = INTERVALS.map((i) => `<option>${i}</option>`).join("")));

// ---------- status ----------
async function refresh() {
  try {
    S = await api("status");
    render();
  } catch (e) {
    $("#updated").textContent = "sem contato com o robô (a janela do TradeMartins foi fechada?)";
    $("#dot").className = "dot";
  }
}

function render() {
  const c = S.config, L = S.live, q = L.quote || "USDT", b = L.base || "";
  const real = c.mode === "real";
  $("#mode-badge").textContent = { real: "REAL", teste: "BINANCE DE TESTE", simulado: "SIMULADO" }[c.mode];
  $("#mode-badge").className = "badge " + c.mode;
  $("#warn-real").hidden = !real;
  $("#warn-teste").hidden = c.mode !== "teste";
  document.title = (L.price ? priceFmt(L.price) + " · " : "") + "TradeMartins";

  $("#pair-label").textContent = `${c.symbol} · velas de ${c.interval}`;
  $("#price").textContent = L.price ? priceFmt(L.price) + " " + q : "—";
  $("#updated").textContent = L.updated ? "atualizado às " + hm(L.updated) : "conectando…";
  const eb = $("#err-banner");
  eb.hidden = !L.lastError;
  eb.textContent = L.lastError ? "Problema: " + L.lastError : "";

  $("#dot").className = "dot" + (S.running ? " on" : "");
  $("#status-text").textContent = S.running ? (S.dayBlocked ? "Ligado (sem compras hoje)" : "Ligado") : "Desligado";
  const tb = $("#btn-toggle");
  tb.textContent = S.running ? "Desligar robô" : "Ligar robô";
  tb.className = "btn big " + (S.running ? "" : "primary");

  $("#equity").textContent = money(L.equity, q);
  $("#equity-sub").textContent = L.quote ? `${money(L.quoteBal, q)} + ${qtyFmt(L.baseBal)} ${b}` : "";
  const sm = S.summary;
  $("#pnl").textContent = signed(sm.pnl, q);
  $("#pnl").className = cls(sm.pnl);
  const n = sm.wins + sm.losses;
  $("#pnl-sub").textContent = n ? `${n} vendas · ${sm.wins} com lucro (${num((sm.wins / n) * 100, 0)}%)` : "nenhuma venda ainda";

  const I = L.inds || {};
  $("#rsi").textContent = I.rsi ? num(I.rsi, 1) : "—";
  $("#rsi-sub").textContent = !I.rsi ? "" : I.rsi >= c.params.rsiMaxBuy ? "acima do limite: não compra" : I.rsi < 30 ? "sobrevendido" : "ok para comprar";
  if (I.fast && I.slow) {
    const up = I.fast > I.slow;
    $("#trend").textContent = up ? "Alta ▲" : "Baixa ▼";
    $("#trend").className = up ? "pos" : "neg";
    let sub = up ? "EMA rápida acima da lenta" : "EMA rápida abaixo da lenta";
    if (I.trend) sub += L.price > I.trend ? " · acima da longa" : " · abaixo da longa";
    $("#trend-sub").textContent = sub;
  }

  const p = S.position;
  $("#pos-card").hidden = !p;
  if (p) {
    const val = p.qty * L.price * 0.999;
    const r = val - p.cost;
    $("#pos-qty").textContent = qtyFmt(p.qty) + " " + b;
    $("#pos-entry").textContent = priceFmt(p.entry);
    $("#pos-pnl").innerHTML = !L.price ? "—" : `<span class="${cls(r)}">${signed(r, q)} (${pct((r / p.cost) * 100)})</span>`;
    $("#pos-stop").textContent = p.stop ? priceFmt(p.stop) : "desligado";
    $("#pos-stop-where").textContent = !p.stop ? "" : p.stopOrderId ? "registrado na Binance ✔" : p.mode === "simulado" ? "vigiado pelo robô" : "vigiado pelo PC (sem ordem na Binance)";
    $("#pos-take").textContent = p.take ? priceFmt(p.take) : "desligado";
  }

  $("#logs").innerHTML = (S.logs || []).slice().reverse().map((l) =>
    `<li class="${l.kind}"><time>${dt(l.t)}</time><span>${esc(l.msg)}</span></li>`).join("") ||
    `<li class="muted">Nada ainda.</li>`;

  renderHistory();
  if ($("#painel").classList.contains("on")) drawChart();
  if (!cfgLoaded) { fillConfig(); cfgLoaded = true; }
  for (const k of ["real", "teste", "telegram"]) {
    const v = S.keys[k];
    $("#key-status-" + k).innerHTML = v.has ? `Salvo: <b>${esc(v.hint)}</b>` : `<span class="muted">Nada salvo ainda.</span>`;
  }
}

function tradeRows(trades, q) {
  return `<tr><th>Data</th><th>Lado</th><th>Preço</th><th>Quantidade</th><th>Valor</th><th>Motivo</th><th>Resultado</th></tr>` +
    trades.map((t) => `<tr>
      <td>${dt(t.time)}</td>
      <td><span class="tag ${t.side === "COMPRA" ? "buy" : "sell"}">${t.side}</span></td>
      <td>${priceFmt(t.price)}</td><td>${qtyFmt(t.qty)}</td><td>${money(t.quote, q)}</td>
      <td>${esc(t.reason)}</td>
      <td class="${cls(t.pnl)}">${t.side === "VENDA" ? `${signed(t.pnl, q)} (${pct(t.pnlPct)})` : ""}</td>
    </tr>`).join("");
}

function renderHistory() {
  S.trades = S.trades || [];
  const q = S.live.quote || "USDT";
  $("#hist-title").textContent = `Operações (${S.config.mode})`;
  $("#hist").innerHTML = S.trades.length ? tradeRows(S.trades, q) : "";
  $("#hist-empty").hidden = S.trades.length > 0;
}

// ---------- gráficos ----------
function setupCanvas(cv) {
  const dpr = window.devicePixelRatio || 1;
  const w = cv.clientWidth, h = cv.clientHeight || +cv.getAttribute("height");
  cv.width = w * dpr;
  cv.height = h * dpr;
  const g = cv.getContext("2d");
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, w, h);
  return { g, w, h };
}
const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();

function axis(g, w, h, pad, lo, hi, fmt) {
  g.font = "11px system-ui, sans-serif";
  g.fillStyle = css("--muted");
  g.strokeStyle = css("--line");
  g.lineWidth = 1;
  for (let i = 0; i <= 4; i++) {
    const v = lo + ((hi - lo) * i) / 4;
    const y = pad.t + (h - pad.t - pad.b) * (1 - i / 4);
    g.beginPath(); g.moveTo(pad.l, y); g.lineTo(w - pad.r, y); g.stroke();
    g.fillText(fmt(v), w - pad.r + 6, y + 4);
  }
}

function line(g, xs, ys, color, width = 1.6, dash) {
  g.strokeStyle = color; g.lineWidth = width; g.setLineDash(dash || []);
  g.beginPath();
  let started = false;
  ys.forEach((y, i) => {
    if (y == null || isNaN(y)) return;
    started ? g.lineTo(xs(i), y) : g.moveTo(xs(i), y);
    started = true;
  });
  g.stroke(); g.setLineDash([]);
}

function drawChart() {
  const ch = S && S.chart;
  const cv = $("#chart");
  const { g, w, h } = setupCanvas(cv);
  if (!ch || !ch.t || !ch.t.length) {
    g.fillStyle = css("--muted"); g.font = "14px system-ui"; g.fillText("Carregando preços…", 16, 30);
    return;
  }
  const pad = { l: 8, r: 78, t: 10, b: 22 };
  const n = ch.t.length;
  let lo = Infinity, hi = -Infinity;
  ch.ohlc.forEach((k) => { lo = Math.min(lo, k[2]); hi = Math.max(hi, k[1]); });
  [ch.fast, ch.slow, ch.trend || []].forEach((a) => a.forEach((v) => { if (v) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }));
  const st = S.stopTake || [];
  st.forEach((v) => { if (v) { lo = Math.min(lo, v); hi = Math.max(hi, v); } });
  const m = (hi - lo) * 0.06 || 1; lo -= m; hi += m;
  const cw = (w - pad.l - pad.r) / n;
  const X = (i) => pad.l + cw * (i + 0.5);
  const Y = (v) => pad.t + (h - pad.t - pad.b) * (1 - (v - lo) / (hi - lo));
  axis(g, w, h, pad, lo, hi, priceFmt);

  // datas no eixo x
  g.fillStyle = css("--muted");
  const nl = Math.max(2, Math.floor((w - pad.r) / 120));
  for (let i = 0; i < n; i += Math.ceil(n / nl)) {
    const d = new Date(ch.t[i]);
    g.fillText(d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" }), Math.max(pad.l, X(i) - 30), h - 6);
  }

  const up = css("--ok"), dn = css("--danger");
  ch.ohlc.forEach((k, i) => {
    const [o, hh, l, c] = k, col = c >= o ? up : dn;
    g.strokeStyle = col; g.fillStyle = col; g.lineWidth = 1;
    g.beginPath(); g.moveTo(X(i), Y(hh)); g.lineTo(X(i), Y(l)); g.stroke();
    const bw = Math.max(1, cw * 0.62);
    const y1 = Y(Math.max(o, c)), y2 = Y(Math.min(o, c));
    g.fillRect(X(i) - bw / 2, y1, bw, Math.max(1, y2 - y1));
  });
  if (ch.trend) line(g, X, ch.trend.map((v) => (v ? Y(v) : null)), css("--trendc"), 1.4);
  line(g, X, ch.slow.map((v) => (v ? Y(v) : null)), css("--slow"), 1.8);
  line(g, X, ch.fast.map((v) => (v ? Y(v) : null)), css("--fast"), 1.8);

  // stop / take da posição aberta
  [[st[0], dn, "stop"], [st[1], up, "alvo"]].forEach(([v, col, lb]) => {
    if (!v) return;
    g.strokeStyle = col; g.setLineDash([5, 4]); g.lineWidth = 1;
    g.beginPath(); g.moveTo(pad.l, Y(v)); g.lineTo(w - pad.r, Y(v)); g.stroke(); g.setLineDash([]);
    g.fillStyle = col; g.fillText(lb, pad.l + 4, Y(v) - 4);
  });

  // compras e vendas
  const iv = n > 1 ? ch.t[1] - ch.t[0] : 3600e3;
  S.trades.forEach((t) => {
    if (t.symbol && t.symbol !== S.config.symbol) return;
    const i = Math.floor((t.time - ch.t[0]) / iv);
    if (i < 0 || i >= n) return;
    const buy = t.side === "COMPRA";
    const y = Y(t.price);
    g.fillStyle = buy ? up : dn;
    g.beginPath();
    if (buy) { g.moveTo(X(i), y + 4); g.lineTo(X(i) - 6, y + 14); g.lineTo(X(i) + 6, y + 14); }
    else { g.moveTo(X(i), y - 4); g.lineTo(X(i) - 6, y - 14); g.lineTo(X(i) + 6, y - 14); }
    g.fill();
  });

  // preço atual
  const last = ch.ohlc[n - 1][3];
  g.fillStyle = css("--accent");
  g.fillRect(w - pad.r + 2, Y(last) - 9, pad.r - 4, 18);
  g.fillStyle = "#fff"; g.font = "bold 11px system-ui";
  g.fillText(priceFmt(last), w - pad.r + 6, Y(last) + 4);
}

function drawBT() {
  const r = lastBT.res;
  const cv = $("#bt-chart");
  const { g, w, h } = setupCanvas(cv);
  const eq = r.equity, pr = r.price;
  if (!eq.length) return;
  const bh = pr.map((p) => (r.startEquity * p.v) / pr[0].v);
  let lo = Infinity, hi = -Infinity;
  eq.forEach((p, i) => { lo = Math.min(lo, p.v, bh[i]); hi = Math.max(hi, p.v, bh[i]); });
  const m = (hi - lo) * 0.06 || 1; lo -= m; hi += m;
  const pad = { l: 8, r: 78, t: 10, b: 22 };
  const X = (i) => pad.l + ((w - pad.l - pad.r) * i) / Math.max(1, eq.length - 1);
  const Y = (v) => pad.t + (h - pad.t - pad.b) * (1 - (v - lo) / (hi - lo));
  axis(g, w, h, pad, lo, hi, (v) => num(v, 0));
  g.fillStyle = css("--muted");
  const nl = Math.max(2, Math.floor((w - pad.r) / 90));
  for (let i = 0; i < eq.length; i += Math.ceil(eq.length / nl)) {
    g.fillText(new Date(eq[i].t).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "2-digit" }), Math.max(pad.l, X(i) - 22), h - 6);
  }
  g.strokeStyle = css("--line"); g.setLineDash([3, 3]);
  g.beginPath(); g.moveTo(pad.l, Y(r.startEquity)); g.lineTo(w - pad.r, Y(r.startEquity)); g.stroke(); g.setLineDash([]);
  line(g, X, bh.map(Y), css("--muted"), 1.4);
  line(g, X, eq.map((p) => Y(p.v)), css("--accent"), 2);
}

window.addEventListener("resize", () => {
  if (S && $("#painel").classList.contains("on")) drawChart();
  if (lastBT && $("#testar").classList.contains("on")) drawBT();
});

// ---------- botões do painel ----------
$("#btn-toggle").addEventListener("click", (e) => act(e.target, async () => {
  if (S.running) {
    const p = S.position;
    const stopMsg = p && p.stopOrderId ? "O stop-loss <b>continua registrado na Binance</b>, mas o take-profit e a venda pelo sinal param." : "Com o robô desligado, o stop-loss e o take-profit não são vigiados.";
    if (p && !(await ask("Desligar o robô?", `<p>A posição aberta <b>continua aberta</b>. ${stopMsg}</p>`, { okText: "Desligar" }))) return;
    await api("stop", {});
    toast("Robô desligado");
  } else {
    let confirmReal = false;
    if (S.config.mode === "real") {
      confirmReal = await ask("Operar com dinheiro REAL?",
        `<p>O robô vai comprar e vender <b>${esc(S.config.symbol)}</b> na sua conta da Binance usando até <b>${num(S.config.tradePct, 0)}%</b> do saldo em ${esc(S.live.quote || "")} por compra, <b>no máximo ${money(S.config.maxBuy, S.live.quote)}</b>.</p><p>Robôs também perdem dinheiro. Use só o que você aceita perder.</p><p>Para confirmar, digite <b>REAL</b>:</p>`,
        { okText: "Ligar no modo real", danger: true, requireText: "REAL" });
      if (!confirmReal) return;
    }
    await api("start", { confirmReal });
    toast("Robô ligado");
  }
  await refresh();
}));

$("#btn-sell").addEventListener("click", (e) => act(e.target, async () => {
  if (!(await ask("Vender agora?", `<p>Vende toda a posição de ${esc(S.live.base)} a preço de mercado.</p>`, { okText: "Vender", danger: true }))) return;
  await api("sell", {});
  toast("Posição vendida");
  await refresh();
}));

// ---------- ajustes ----------
function fillForm(form, c) {
  const set = (k, v) => { const el = form.elements[k]; if (el && v !== undefined) el.value = v; };
  set("symbol", c.symbol); set("interval", c.interval); set("tradePct", c.tradePct); set("maxBuy", c.maxBuy);
  PARAM_KEYS.forEach((k) => set(k, c.params[k]));
}

function fillConfig() {
  const f = $("#cfg-form"), c = S.config;
  fillForm(f, c);
  f.elements.checkSeconds.value = c.checkSeconds;
  f.elements.maxDailyLossPct.value = c.maxDailyLossPct;
  f.elements.paperStart.value = c.paperStart;
  $$("input[name=mode]", f).forEach((r) => (r.checked = r.value === c.mode));
  ["apiKey", "apiSecret", "testApiKey", "testApiSecret", "telegramToken"].forEach((k) => (f.elements[k].value = ""));
  f.elements.telegramChat.value = c.telegramChat || "";
  f.elements.autostart.checked = !c.noAutostart;
  f.elements.pingUrl.value = c.pingUrl || "";
  fillForm($("#bt-form"), c);
}

function readParams(form) {
  const p = {};
  PARAM_KEYS.forEach((k) => (p[k] = +form.elements[k].value));
  return p;
}

$("#cfg-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const f = e.target;
  act(f.querySelector("[type=submit]"), async () => {
    const mode = f.querySelector("input[name=mode]:checked").value;
    if (mode === "real" && S.config.mode !== "real" &&
      !(await ask("Mudar para o modo real?", "<p>No modo real o robô usa o seu dinheiro na Binance. Recomendado: antes, deixar rodando alguns dias no modo <b>Binance de teste</b> e conferir as compras, vendas e o stop.</p>", { okText: "Mudar para real", danger: true }))) return;
    await api("config", {
      symbol: f.elements.symbol.value, interval: f.elements.interval.value, params: readParams(f),
      tradePct: +f.elements.tradePct.value, maxBuy: +f.elements.maxBuy.value || 0, noAutostart: !f.elements.autostart.checked,
      maxDailyLossPct: +f.elements.maxDailyLossPct.value,
      checkSeconds: +f.elements.checkSeconds.value, mode, paperStart: +f.elements.paperStart.value,
      apiKey: f.elements.apiKey.value.trim(), apiSecret: f.elements.apiSecret.value.trim(),
      testApiKey: f.elements.testApiKey.value.trim(), testApiSecret: f.elements.testApiSecret.value.trim(),
      telegramToken: f.elements.telegramToken.value.trim(), telegramChat: f.elements.telegramChat.value.trim(),
      pingUrl: f.elements.pingUrl.value.trim() || "-",
    });
    toast("Ajustes salvos");
    await refresh();
    fillConfig();
  });
});

$("#btn-reset").addEventListener("click", (e) => act(e.target, async () => {
  if (!(await ask("Zerar a simulação?", "<p>Apaga as operações simuladas e volta o saldo simulado para o valor inicial. Operações reais não são apagadas.</p>", { okText: "Zerar", danger: true }))) return;
  await api("reset-paper", {});
  toast("Simulação zerada");
  await refresh();
}));

$$("[data-testkeys]").forEach((btn) => btn.addEventListener("click", () => act(btn, async () => {
  const f = $("#cfg-form"), which = btn.dataset.testkeys, t = which === "teste";
  const r = await api("test-keys", {
    Which: which,
    APIKey: f.elements[t ? "testApiKey" : "apiKey"].value.trim(),
    APISecret: f.elements[t ? "testApiSecret" : "apiSecret"].value.trim(),
  });
  let perms = "";
  if (r.problem) perms = `<p class="neg"><b>⚠️ Não é seguro usar esta chave:</b> ${esc(r.problem)}.</p><p class="muted">O robô se recusa a ligar no modo real com ela.</p>`;
  else if (r.perms) perms = `<p class="pos">✔ Sem permissão de saque nem de transferência.</p>` + (r.perms.ipRestrict ? `<p class="pos">✔ Restrita ao IP da sua internet.</p>` : `<p class="muted">Dica: restringir a chave ao IP da sua internet deixa ela ainda mais segura.</p>`);
  else if (r.permsError) perms = `<p class="muted">Não deu para conferir as permissões da chave (${esc(r.permsError)}). Confira na Binance que ela NÃO permite saques.</p>`;
  await ask(r.problem ? "Chave com permissão perigosa" : "Chave funcionando ✔", `<p>Saldo na Binance${t ? " de teste" : ""}:</p><p><b>${money(r.quoteBal, r.quote)}</b> e <b>${qtyFmt(r.baseBal)} ${esc(r.base)}</b></p>${perms}<p class="muted">Lembre de clicar em <b>Salvar ajustes</b> para guardar a chave.</p>`);
})));

// salva só uma alteração, mantendo o resto dos ajustes como estão
async function patchConfig(extra) {
  await api("config", { ...S.config, apiKey: "", apiSecret: "", testApiKey: "", testApiSecret: "", telegramToken: "", pingUrl: S.config.pingUrl || "-", ...extra });
  await refresh();
}

$$("[data-delkeys]").forEach((btn) => btn.addEventListener("click", () => act(btn, async () => {
  const t = btn.dataset.delkeys === "teste";
  if (!(await ask("Apagar a chave?", `<p>A chave da Binance${t ? " de teste" : ""} será apagada deste computador.</p>`, { okText: "Apagar", danger: true }))) return;
  await patchConfig(t ? { testApiKey: "-", testApiSecret: "-" } : { apiKey: "-", apiSecret: "-" });
  toast("Chave apagada");
})));

$("#btn-tg-find").addEventListener("click", (e) => act(e.target, async () => {
  const f = $("#cfg-form");
  const r = await api("telegram-find", { Token: f.elements.telegramToken.value.trim() });
  f.elements.telegramChat.value = r.chat;
  toast(`Chat encontrado: ${r.name}`);
}));

$("#btn-tg-test").addEventListener("click", (e) => act(e.target, async () => {
  const f = $("#cfg-form");
  await api("telegram-test", { Token: f.elements.telegramToken.value.trim(), Chat: f.elements.telegramChat.value.trim() });
  toast("Mensagem enviada. Confira o Telegram e clique em Salvar ajustes.");
}));

$("#btn-tg-del").addEventListener("click", (e) => act(e.target, async () => {
  if (!(await ask("Apagar o Telegram?", "<p>Os avisos no celular param de ser enviados.</p>", { okText: "Apagar", danger: true }))) return;
  await patchConfig({ telegramToken: "-", telegramChat: "" });
  fillConfig();
  toast("Avisos desligados");
}));

// ---------- backtest ----------
$("#bt-fill").addEventListener("click", () => S && fillForm($("#bt-form"), S.config));

$("#bt-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const f = e.target, btn = $("#bt-run");
  const req = {
    symbol: f.elements.symbol.value, interval: f.elements.interval.value, days: +f.elements.days.value,
    params: readParams(f), tradePct: +f.elements.tradePct.value, maxBuy: +f.elements.maxBuy.value || 0, start: +f.elements.start.value,
  };
  btn.textContent = "Baixando preços…";
  act(btn, async () => {
    const res = await api("backtest", req);
    lastBT = { req, res };
    showBT();
  }).finally(() => (btn.textContent = "Rodar teste"));
});

function showBT() {
  const { req, res: r } = lastBT;
  const q = r.symbol.endsWith("BRL") ? "BRL" : r.symbol.replace(/^.*(USDT|USDC|FDUSD|BTC|ETH|BNB|EUR|TRY)$/, "$1");
  $("#bt-out").hidden = false;
  $("#bt-ret").textContent = pct(r.returnPct);
  $("#bt-ret").className = cls(r.returnPct);
  $("#bt-end").textContent = `${money(r.startEquity, q)} → ${money(r.endEquity, q)}`;
  $("#bt-bh").textContent = pct(r.buyHoldPct);
  $("#bt-bh").className = cls(r.buyHoldPct);
  $("#bt-dd").textContent = "-" + num(r.maxDDPct, 2) + "%";
  const n = r.wins + r.losses;
  $("#bt-n").textContent = n;
  $("#bt-wr").textContent = n ? `${r.wins} com lucro (${num(r.winRate, 0)}%) · taxas ${money(r.feesPaid, q)}` : "nenhuma operação";
  $("#bt-title").textContent = `Patrimônio — ${r.symbol} ${r.interval}, ${dt(r.from)} a ${dt(r.to)} (${r.candles} velas)`;
  const tr = r.trades.slice().reverse();
  $("#bt-trades").innerHTML = tradeRows(tr.slice(0, 100), q) +
    (tr.length > 100 ? `<tr><td colspan="7" class="muted">… e mais ${tr.length - 100} operações mais antigas</td></tr>` : "");
  drawBT();
}

$("#bt-apply").addEventListener("click", (e) => act(e.target, async () => {
  if (!lastBT) return;
  const { req } = lastBT, c = S.config;
  if (!(await ask("Usar estes ajustes?", `<p>O robô passa a usar <b>${esc(req.symbol.toUpperCase())}</b>, velas de <b>${req.interval}</b> e os parâmetros do teste.</p>`, { okText: "Usar" }))) return;
  await patchConfig({ symbol: req.symbol, interval: req.interval, params: req.params, tradePct: req.tradePct, maxBuy: req.maxBuy });
  toast("Ajustes aplicados ao robô");
  fillConfig();
}));

refresh();
setInterval(refresh, 3000);
