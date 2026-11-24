"use strict";

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const INTERVALS = ["1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d"];

// ---------- parâmetros da estratégia (os mesmos campos em Ajustes e no Laboratório) ----------
const STRATS = {
  ema: { name: "Cruzamento de médias", help: "Compra quando a EMA rápida cruza para cima da lenta (com RSI abaixo do limite e preço acima da EMA de tendência). Vende no cruzamento para baixo." },
  donchian: { name: "Rompimento (Donchian)", help: "Estilo \"Turtle\": compra quando o fechamento passa da maior máxima das últimas N velas; vende quando fecha abaixo da menor mínima das últimas M velas. Segue tendências fortes." },
  tsmom: { name: "Momentum", help: "Momentum de série temporal: fica comprado enquanto o preço está acima do de N dias atrás, e de fora quando está abaixo. A estratégia de tendência mais estudada." },
  rsi2: { name: "Reversão à média (RSI curto)", help: "Compra quedas fortes e curtas (RSI de 2 períodos muito baixo) dentro de uma tendência de alta e vende quando o RSI se recupera. Muitas operações curtas." },
};
const SFIELDS = [
  { k: "fastEMA", l: "EMA rápida", s: ["ema"], int: true },
  { k: "slowEMA", l: "EMA lenta", s: ["ema"], int: true },
  { k: "rsiPeriod", l: "Período do RSI", s: ["ema"], int: true },
  { k: "rsiMaxBuy", l: "Não comprar com RSI acima de", s: ["ema"] },
  { k: "breakoutN", l: "Compra ao romper a máxima de (velas)", s: ["donchian"], int: true },
  { k: "exitN", l: "Vende ao perder a mínima de (velas)", s: ["donchian"], int: true },
  { k: "momDays", l: "Comparar com o preço de quantos dias atrás", s: ["tsmom"], int: true },
  { k: "mrRsi", l: "Período do RSI curto", s: ["rsi2"], int: true },
  { k: "mrBuyBelow", l: "Compra com RSI abaixo de", s: ["rsi2"] },
  { k: "mrSellAbove", l: "Vende com RSI acima de", s: ["rsi2"] },
  { k: "trendEMA", l: "Filtro de tendência: EMA de (0 = desliga)", s: ["ema", "donchian", "rsi2"], int: true, min: 0 },
];
const RFIELDS = [
  { k: "sizeMode", l: "Tamanho da compra", sel: { fixo: "% fixo do saldo", vol: "Volatilidade-alvo" },
    hint: "Volatilidade-alvo compra menos quando o mercado está agitado e mais quando está calmo (sempre até o % máximo)." },
  { k: "targetVol", l: "Volatilidade-alvo (% ao ano)", show: (p) => p.sizeMode === "vol", hint: "Cripto costuma oscilar 50–90% ao ano. Alvo 40% = posição menor que o máximo na maior parte do tempo." },
  { k: "volDays", l: "Medir a volatilidade em (dias)", int: true, show: (p) => p.sizeMode === "vol" },
  { k: "stopMode", l: "Tipo de stop", sel: { pct: "% abaixo da compra", atr: "ATR (tamanho médio das velas)" },
    hint: "O ATR se adapta: em mercado agitado o stop fica mais longe, em mercado calmo mais perto." },
  { k: "stopLossPct", l: "Stop-loss % (0 = desliga)", show: (p) => p.stopMode === "pct" },
  { k: "atrMult", l: "Stop a quantos ATR da compra", show: (p) => p.stopMode === "atr" },
  { k: "atrPeriod", l: "Período do ATR (velas)", int: true, show: (p) => p.stopMode === "atr" },
  { k: "trailing", l: "Stop móvel", check: "O stop sobe junto com o maior fechamento desde a compra (nunca desce)" },
  { k: "takeProfitPct", l: "Take-profit % (0 = desliga)" },
  { k: "marketDays", l: "Filtro de mercado: BTC acima da média de (dias, 0 = desliga)", int: true,
    hint: "Só compra quando o Bitcoin está acima da média dos últimos N dias (as criptos caem juntas). Recomendado: 100 dias com Momentum ou Rompimento." },
  { k: "marketExit", l: "Vender quando o BTC perder a média", check: "Com o filtro ligado, sai das posições quando o Bitcoin fecha o dia abaixo da média", show: (p) => p.marketDays > 0 },
];

// monta os campos dentro de cada .pbox de um formulário
function buildParamBoxes(form) {
  const sbox = $('[data-pbox="strategy"]', form), rbox = $('[data-pbox="risk"]', form);
  sbox.innerHTML = `<label class="wide">Estratégia<select name="strategy">${Object.entries(STRATS).map(([k, v]) => `<option value="${k}">${v.name}</option>`).join("")}</select></label>
    <p class="muted small strat-help"></p><div class="grid">` +
    SFIELDS.map((f) => `<label data-s="${f.s.join(" ")}">${f.l}<input name="${f.k}" type="number" step="${f.int ? 1 : "any"}" min="${f.min ?? 0}"></label>`).join("") + `</div>`;
  rbox.innerHTML = `<div class="grid">` + RFIELDS.map((f) => {
    if (f.sel) return `<label data-r="${f.k}">${f.l}<select name="${f.k}">${Object.entries(f.sel).map(([k, v]) => `<option value="${k}">${v}</option>`).join("")}</select>${f.hint ? `<small class="muted">${f.hint}</small>` : ""}</label>`;
    if (f.check) return `<label class="check" data-r="${f.k}"><input type="checkbox" name="${f.k}"><span><b>${f.l}</b><small class="muted">${f.check}</small></span></label>`;
    return `<label data-r="${f.k}">${f.l}<input name="${f.k}" type="number" step="${f.int ? 1 : "any"}" min="0">${f.hint ? `<small class="muted">${f.hint}</small>` : ""}</label>`;
  }).join("") + `</div>`;
  form.addEventListener("change", (e) => { if (["strategy", "sizeMode", "stopMode", "marketDays"].includes(e.target.name)) syncParamVisibility(form); });
  syncParamVisibility(form);
}

function syncParamVisibility(form) {
  const p = readParams(form);
  $$("[data-s]", form).forEach((el) => (el.hidden = !el.dataset.s.split(" ").includes(p.strategy)));
  RFIELDS.forEach((f) => { const el = $(`[data-r="${f.k}"]`, form); if (el) el.hidden = f.show ? !f.show(p) : false; });
  const h = $(".strat-help", form);
  if (h) h.textContent = (STRATS[p.strategy] || {}).help || "";
}

function readParams(form) {
  const p = { strategy: form.elements.strategy.value };
  for (const f of [...SFIELDS, ...RFIELDS]) {
    const el = form.elements[f.k];
    if (!el) continue;
    p[f.k] = f.check ? el.checked : f.sel ? el.value : +el.value;
  }
  return p;
}

function fillParams(form, p) {
  if (!p) return;
  form.elements.strategy.value = p.strategy || "ema";
  for (const f of [...SFIELDS, ...RFIELDS]) {
    const el = form.elements[f.k];
    if (!el || p[f.k] === undefined) continue;
    if (f.check) el.checked = !!p[f.k]; else el.value = p[f.k];
  }
  syncParamVisibility(form);
}

let S = null;          // último status
let selPair = "";      // par escolhido para o gráfico
try { selPair = localStorage.getItem("tm-pair") || ""; } catch (e) {}
let cfgLoaded = false; // formulário de ajustes já preenchido

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
  if (b.dataset.tab === "testar" && window.labRedraw) window.labRedraw();
}));
$$("select[name=interval]").forEach((s) => (s.innerHTML = INTERVALS.map((i) => `<option>${i}</option>`).join("")));

// ---------- status ----------
async function refresh() {
  try {
    S = await api("status" + (selPair ? "?pair=" + encodeURIComponent(selPair) : ""));
    render();
  } catch (e) {
    $("#updated").textContent = "sem contato com o robô (a janela do TradeMartins foi fechada?)";
    $("#dot").className = "dot";
  }
}

function render() {
  const c = S.config, L = S.live, q = L.quote || "USDT";
  const real = c.mode === "real";
  $("#mode-badge").textContent = { real: "REAL", teste: "BINANCE DE TESTE", simulado: "SIMULADO" }[c.mode];
  $("#mode-badge").className = "badge " + c.mode;
  $("#warn-real").hidden = !real;
  $("#warn-teste").hidden = c.mode !== "teste";
  const pairs = S.pairs || [];
  document.title = (L.equity ? money(L.equity, q) + " · " : "") + "TradeMartins";

  $("#port-label").textContent = pairs.length > 1 ? `Carteira · ${pairs.length} pares · velas de ${c.interval}` : `${pairs[0] ? pairs[0].symbol : ""} · velas de ${c.interval}`;
  $("#equity").textContent = L.equity ? money(L.equity, q) : "—";
  $("#updated").textContent = L.updated ? "patrimônio · atualizado às " + hm(L.updated) : "conectando…";
  const errs = pairs.filter((p) => p.lastError).map((p) => `${p.symbol}: ${p.lastError}`);
  if (L.lastError) errs.unshift(L.lastError);
  const eb = $("#err-banner");
  eb.hidden = !errs.length;
  eb.textContent = errs.length ? "Problema: " + errs.join(" · ") : "";

  $("#dot").className = "dot" + (S.running ? " on" : "");
  $("#status-text").textContent = S.running ? (S.dayBlocked ? "Ligado (sem compras hoje)" : "Ligado") : "Desligado";
  const tb = $("#btn-toggle");
  tb.textContent = S.running ? "Desligar robô" : "Ligar robô";
  tb.className = "btn big " + (S.running ? "" : "primary");

  const open = pairs.filter((p) => p.position);
  const invested = open.reduce((s, p) => s + p.position.cost, 0), now = open.reduce((s, p) => s + p.pnlNow, 0);
  $("#cash").textContent = money(L.quoteBal, q);
  $("#cash-sub").textContent = L.equity ? `${num((L.quoteBal / L.equity) * 100, 0)}% do patrimônio` : "";
  $("#open-n").textContent = `${open.length} de ${pairs.length}`;
  $("#open-sub").innerHTML = open.length ? `${money(invested, q)} investidos · agora <span class="${cls(now)}">${signed(now, q)}</span>` : "nenhuma";
  const sm = S.summary;
  $("#pnl").textContent = signed(sm.pnl, q);
  $("#pnl").className = cls(sm.pnl);
  const n = sm.wins + sm.losses;
  $("#pnl-sub").textContent = n ? `${n} vendas · ${sm.wins} com lucro (${num((sm.wins / n) * 100, 0)}%)` : "nenhuma venda ainda";
  $("#strat-name").textContent = S.strategyName || "—";
  const pr = c.params || {};
  $("#strat-sub").textContent = [pr.sizeMode === "vol" ? `volatilidade-alvo ${num(pr.targetVol, 0)}%` : `até ${num(c.tradePct, 0)}% da parte`,
    pr.stopMode === "atr" ? `stop ${num(pr.atrMult, 1)}× ATR` : pr.stopLossPct ? `stop ${num(pr.stopLossPct, 1)}%` : "sem stop", pr.trailing ? "móvel" : ""].filter(Boolean).join(" · ");
  $("#slot-text").textContent = pairs.length > 1 ? `1/${pairs.length}` : "todo";
  $("#btn-sell-all").hidden = open.length < 2;

  renderPairs(pairs, q);
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

function stopWhere(p) {
  if (!p.stop) return "sem stop";
  return `stop ${priceFmt(p.stop)}` + (p.stopOrderId ? " (na Binance ✔)" : p.mode === "simulado" ? "" : " (vigiado pelo PC)");
}

function renderPairs(pairs, q) {
  $("#pairs").innerHTML = `<tr><th>Par</th><th>Preço</th><th>24h</th><th>Estratégia vê</th><th>Posição</th><th></th></tr>` +
    pairs.map((p) => {
      const t = (p.info || [])[0];
      const pos = p.position;
      return `<tr data-pair="${esc(p.symbol)}" class="${p.symbol === S.pair ? "sel" : ""}">
        <td><b>${esc(p.symbol)}</b>${p.lastError ? ` <span class="neg" title="${esc(p.lastError)}">⚠</span>` : ""}</td>
        <td>${p.price ? priceFmt(p.price) : "—"}</td>
        <td class="${cls(p.changePct)}">${p.price ? pct(p.changePct) : ""}</td>
        <td>${t ? `<span class="${t.tone || ""}">${esc(t.label)}: ${esc(t.value)}</span><small class="muted">${esc(t.sub)}</small>` : ""}</td>
        <td>${pos ? `<span class="${cls(p.pnlNow)}">${signed(p.pnlNow, q)} (${pct(p.pnlNowPct)})</span><small class="muted">${qtyFmt(pos.qty)} ${esc(p.base || "")} a ${priceFmt(pos.entry)} · ${stopWhere(pos)}${p.pendingSell ? " · venda pendente" : ""}</small>` : `<span class="muted">—</span>`}</td>
        <td>${pos ? `<button class="btn danger small-btn" data-sell="${esc(p.symbol)}">Vender</button>` : ""}</td>
      </tr>`;
    }).join("");
  $("#chips").innerHTML = pairs.length > 1 ? pairs.map((p) => `<button class="chip ${p.symbol === S.pair ? "on" : ""}" data-pair="${esc(p.symbol)}">${esc(p.symbol)}${p.position ? " ●" : ""}</button>`).join("") : "";
  const cur = pairs.find((p) => p.symbol === S.pair);
  $("#chart-title").textContent = "Gráfico — " + (S.pair || "");
  $("#pair-info").innerHTML = cur && cur.info ? cur.info.map((t) => `<span><span class="muted">${esc(t.label)}:</span> <b class="${t.tone || ""}">${esc(t.value)}</b> <small class="muted">${esc(t.sub)}</small></span>`).join("") : "";
}

function choosePair(sym) {
  if (!sym || sym === selPair) return;
  selPair = sym;
  try { localStorage.setItem("tm-pair", sym); } catch (e) {}
  refresh();
}

document.addEventListener("click", (e) => {
  const sellBtn = e.target.closest("[data-sell]");
  if (sellBtn) {
    const sym = sellBtn.dataset.sell;
    act(sellBtn, async () => {
      if (!(await ask(`Vender ${esc(sym)}?`, `<p>Vende toda a posição de <b>${esc(sym)}</b> a preço de mercado. Os outros pares não são afetados.</p>`, { okText: "Vender", danger: true }))) return;
      await api("sell", { symbol: sym });
      toast(`${sym} vendido`);
      await refresh();
    });
    return;
  }
  const row = e.target.closest("[data-pair]");
  if (row && (row.closest("#pairs") || row.closest("#chips"))) choosePair(row.dataset.pair);
});

$("#btn-sell-all").addEventListener("click", (e) => act(e.target, async () => {
  const open = (S.pairs || []).filter((p) => p.position).map((p) => p.symbol);
  if (!(await ask("Vender TODAS as posições?", `<p>Vende a preço de mercado: <b>${open.map(esc).join(", ")}</b>.</p>`, { okText: "Vender tudo", danger: true }))) return;
  await api("sell", { symbol: "" });
  toast("Posições vendidas");
  await refresh();
}));

function tradeRows(trades, q) {
  return `<tr><th>Data</th><th>Par</th><th>Lado</th><th>Preço</th><th>Quantidade</th><th>Valor</th><th>Motivo</th><th>Resultado</th></tr>` +
    trades.map((t) => `<tr>
      <td>${dt(t.time)}</td><td>${esc(t.symbol || "")}</td>
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
  const lines = ch.lines || [];
  lines.forEach((ln) => ln.values.forEach((v) => { if (v) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }));
  $("#chart-legend").innerHTML = lines.map((ln) => `<i style="--c:var(${ln.color})"></i>${esc(ln.name)}`).join(" ");
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
  lines.slice().reverse().forEach((ln) => line(g, X, ln.values.map((v) => (v ? Y(v) : null)), css(ln.color), ln.color === "--trendc" ? 1.4 : 1.8));

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
    if (t.symbol && t.symbol !== S.pair) return;
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

window.addEventListener("resize", () => {
  if (S && $("#painel").classList.contains("on")) drawChart();
  if (window.labRedraw && $("#testar").classList.contains("on")) window.labRedraw();
});

// ---------- botões do painel ----------
$("#btn-toggle").addEventListener("click", (e) => act(e.target, async () => {
  if (S.running) {
    const open = (S.pairs || []).filter((x) => x.position);
    const onEx = open.filter((x) => x.position.stopOrderId).length;
    const stopMsg = open.length && onEx === open.length ? "Os stop-loss <b>continuam registrados na Binance</b>, mas o take-profit e as vendas pelo sinal param." : "Com o robô desligado, os stops que não estão na Binance, o take-profit e as vendas pelo sinal não são vigiados.";
    if (open.length && !(await ask("Desligar o robô?", `<p>${open.length === 1 ? "A posição aberta <b>continua aberta</b>" : `As ${open.length} posições abertas <b>continuam abertas</b>`}. ${stopMsg}</p>`, { okText: "Desligar" }))) return;
    await api("stop", {});
    toast("Robô desligado");
  } else {
    let confirmReal = false;
    if (S.config.mode === "real") {
      confirmReal = await ask("Operar com dinheiro REAL?",
        `<p>O robô vai comprar e vender <b>${(S.config.symbols || [S.config.symbol]).map(esc).join(", ")}</b> na sua conta da Binance, cada par usando no máximo ${S.config.symbols && S.config.symbols.length > 1 ? `1/${S.config.symbols.length} do patrimônio` : "o saldo"} por compra, e <b>nunca mais de ${money(S.config.maxBuy, S.live.quote)}</b> numa compra.</p><p>Robôs também perdem dinheiro. Use só o que você aceita perder.</p><p>Para confirmar, digite <b>REAL</b>:</p>`,
        { okText: "Ligar no modo real", danger: true, requireText: "REAL" });
      if (!confirmReal) return;
    }
    await api("start", { confirmReal });
    toast("Robô ligado");
  }
  await refresh();
}));



// ---------- ajustes ----------
function fillForm(form, c) {
  const set = (k, v) => { const el = form.elements[k]; if (el && v !== undefined) el.value = v; };
  set("symbol", c.symbol); set("interval", c.interval); set("tradePct", c.tradePct); set("maxBuy", c.maxBuy);
  fillParams(form, c.params);
}

function fillConfig() {
  const f = $("#cfg-form"), c = S.config;
  fillForm(f, c);
  f.elements.symbols.value = (c.symbols && c.symbols.length ? c.symbols : [c.symbol]).join(", ");
  f.elements.checkSeconds.value = c.checkSeconds;
  f.elements.maxDailyLossPct.value = c.maxDailyLossPct;
  f.elements.paperStart.value = c.paperStart;
  $$("input[name=mode]", f).forEach((r) => (r.checked = r.value === c.mode));
  ["apiKey", "apiSecret", "testApiKey", "testApiSecret", "telegramToken"].forEach((k) => (f.elements[k].value = ""));
  f.elements.telegramChat.value = c.telegramChat || "";
  f.elements.autostart.checked = !c.noAutostart;
  f.elements.pingUrl.value = c.pingUrl || "";
  fillForm($("#lab-form"), c);
}


$("#cfg-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const f = e.target;
  act(f.querySelector("[type=submit]"), async () => {
    const mode = f.querySelector("input[name=mode]:checked").value;
    if (mode === "real" && S.config.mode !== "real" &&
      !(await ask("Mudar para o modo real?", "<p>No modo real o robô usa o seu dinheiro na Binance. Recomendado: antes, deixar rodando alguns dias no modo <b>Binance de teste</b> e conferir as compras, vendas e o stop.</p>", { okText: "Mudar para real", danger: true }))) return;
    await api("config", {
      symbols: [f.elements.symbols.value], interval: f.elements.interval.value, params: readParams(f),
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

buildParamBoxes($("#cfg-form"));
buildParamBoxes($("#lab-form"));

refresh();
setInterval(refresh, 3000);
