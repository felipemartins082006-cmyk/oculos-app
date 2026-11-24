"use strict";
// Laboratório quantitativo: backtest, walk-forward, mapa de parâmetros, Monte Carlo e vários pares.
// Usa as funções de app.js ($, api, num, pct, money, esc, setupCanvas, axis, line, css, tradeRows, readParams...).

let LAB = null; // { kind, req, res }

const fmtDD = (v) => "−" + num(v, 1) + "%";
const fmtPF = (m) => (m.trades ? (m.profitFactor >= 999 ? "∞" : num(m.profitFactor, 2)) : "—");

// linhas da tabela de métricas: [nome, valor, explicação, qual é melhor (1 = maior, -1 = menor)]
const MROWS = [
  ["Retorno total", (m) => pct(m.returnPct), "", 1, (m) => m.returnPct],
  ["Retorno ao ano (CAGR)", (m) => pct(m.cagrPct), "crescimento composto por ano", 1, (m) => m.cagrPct],
  ["Volatilidade ao ano", (m) => num(m.volPct, 1) + "%", "quanto o patrimônio oscila", -1, (m) => m.volPct],
  ["Sharpe", (m) => num(m.sharpe, 2), "retorno por unidade de risco; acima de 1 é bom", 1, (m) => m.sharpe],
  ["Sortino", (m) => num(m.sortino, 2), "como o Sharpe, mas só pune as quedas", 1, (m) => m.sortino],
  ["Maior queda", (m) => fmtDD(m.maxDDPct), "do pico ao fundo", -1, (m) => m.maxDDPct],
  ["Calmar", (m) => num(m.calmar, 2), "retorno ao ano ÷ maior queda", 1, (m) => m.calmar],
  ["Operações", (m) => (m.trades ? m.trades : "—"), "", 0],
  ["Acerto", (m) => (m.trades ? num(m.winRate, 0) + "%" : "—"), "% das operações com lucro", 0],
  ["Fator de lucro", fmtPF, "lucro bruto ÷ prejuízo bruto; acima de 1 = ganha mais do que perde", 0],
  ["Média por operação", (m) => (m.trades ? pct(m.avgTradePct) : "—"), "expectativa de cada operação", 0],
  ["Ganho médio / perda média", (m) => (m.trades ? `${pct(m.avgWinPct)} / ${pct(m.avgLossPct)}` : "—"), "", 0],
  ["Duração média", (m) => (m.trades ? num(m.avgHoldDays, 1) + " dias" : "—"), "", 0],
  ["Tempo comprado", (m) => num(m.exposurePct, 0) + "%", "no resto do tempo, fica em dinheiro", 0],
  ["Significativo?", (m) => `${m.significant ? "sim" : "não"} (t = ${num(m.sharpeT, 2)})`, "t acima de 1,96: pouca chance de ser só sorte (aproximação otimista)", 0],
];

function metricsTable(cols) {
  return `<div class="table-wrap"><table class="mtable"><tr><th></th>${cols.map(([n]) => `<th>${esc(n)}</th>`).join("")}</tr>` +
    MROWS.map(([name, f, help, better, val]) => {
      let best = -1;
      if (better && cols.length > 1) {
        const vs = cols.map(([, m]) => val(m));
        best = vs.indexOf(better > 0 ? Math.max(...vs) : Math.min(...vs));
      }
      return `<tr><td>${name}${help ? `<small class="muted">${help}</small>` : ""}</td>` +
        cols.map(([, m], i) => `<td class="${i === best ? "best" : ""}">${f(m)}</td>`).join("") + `</tr>`;
    }).join("") + `</table></div>`;
}

function chartCard(id, title, legend, h = 280) {
  return `<div class="card"><div class="row between"><h3>${title}</h3><div class="legend">${legend}</div></div><canvas id="${id}" style="height:${h}px"></canvas></div>`;
}
const LEG_EQ = `<i style="--c:var(--accent)"></i>Robô <i style="--c:var(--muted)"></i>Comprar e segurar`;

// ---------- gráficos ----------
function drawSeries(id, series, fmtY, base) {
  const cv = document.getElementById(id);
  if (!cv) return;
  const { g, w, h } = setupCanvas(cv);
  const pts = series[0].pts;
  if (!pts || pts.length < 2) return;
  let lo = Infinity, hi = -Infinity;
  series.forEach((s) => s.pts.forEach((p) => { lo = Math.min(lo, p.v); hi = Math.max(hi, p.v); }));
  if (base !== undefined) { lo = Math.min(lo, base); hi = Math.max(hi, base); }
  const m = (hi - lo) * 0.06 || 1; lo -= m; hi += m;
  const pad = { l: 8, r: 78, t: 10, b: 22 };
  const X = (i, n) => pad.l + ((w - pad.l - pad.r) * i) / Math.max(1, n - 1);
  const Y = (v) => pad.t + (h - pad.t - pad.b) * (1 - (v - lo) / (hi - lo));
  axis(g, w, h, pad, lo, hi, fmtY);
  g.fillStyle = css("--muted");
  const nl = Math.max(2, Math.floor((w - pad.r) / 90));
  for (let i = 0; i < pts.length; i += Math.ceil(pts.length / nl)) {
    g.fillText(new Date(pts[i].t).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "2-digit" }), Math.max(pad.l, X(i, pts.length) - 22), h - 6);
  }
  if (base !== undefined) {
    g.strokeStyle = css("--line"); g.setLineDash([3, 3]);
    g.beginPath(); g.moveTo(pad.l, Y(base)); g.lineTo(w - pad.r, Y(base)); g.stroke(); g.setLineDash([]);
  }
  series.forEach((s) => {
    if (s.fill) {
      g.fillStyle = s.fill;
      g.beginPath(); g.moveTo(X(0, s.pts.length), Y(0));
      s.pts.forEach((p, i) => g.lineTo(X(i, s.pts.length), Y(p.v)));
      g.lineTo(X(s.pts.length - 1, s.pts.length), Y(0)); g.closePath(); g.fill();
    }
    line(g, (i) => X(i, s.pts.length), s.pts.map((p) => Y(p.v)), s.color, s.w || 1.6);
  });
}

function drawEquity(id, eq, bench, start) {
  drawSeries(id, [{ pts: bench, color: css("--muted"), w: 1.4 }, { pts: eq, color: css("--accent"), w: 2 }], (v) => num(v, 0), start);
}
function drawDD(id, dd) {
  drawSeries(id, [{ pts: dd, color: css("--danger"), w: 1.2, fill: "rgba(255,93,93,.18)" }], (v) => num(v, 0) + "%", 0);
}

function drawHist(id, mc) {
  const cv = document.getElementById(id);
  if (!cv) return;
  const { g, w, h } = setupCanvas(cv);
  const pad = { l: 8, r: 8, t: 10, b: 22 };
  const maxC = Math.max(...mc.hist, 1);
  const bw = (w - pad.l - pad.r) / mc.hist.length;
  mc.hist.forEach((c, i) => {
    const from = mc.histFrom + i * mc.histStep;
    const bh = (h - pad.t - pad.b) * (c / maxC);
    g.fillStyle = from + mc.histStep <= 0 ? css("--danger") : from >= 0 ? css("--ok") : css("--warn");
    g.globalAlpha = 0.75;
    g.fillRect(pad.l + i * bw + 1, h - pad.b - bh, bw - 2, bh);
  });
  g.globalAlpha = 1;
  g.fillStyle = css("--muted"); g.font = "11px system-ui";
  for (let i = 0; i <= mc.hist.length; i += 4) {
    g.fillText(num(mc.histFrom + i * mc.histStep, 0) + "%", pad.l + i * bw - 8, h - 6);
  }
}

// ---------- pedido ----------
function labRequest(kind) {
  const f = $("#lab-form");
  return {
    kind, symbol: f.elements.symbol.value, interval: f.elements.interval.value, days: +f.elements.days.value,
    start: +f.elements.start.value, tradePct: +f.elements.tradePct.value, maxBuy: +f.elements.maxBuy.value || 0,
    params: readParams(f),
    trainDays: +$("#wf-train").value, testDays: +$("#wf-test").value, objective: $("#wf-obj").value,
    symbols: [$("#multi-syms").value],
  };
}

const KIND_NAMES = { backtest: "Backtest", walkforward: "Walk-forward", heatmap: "Mapa de parâmetros", montecarlo: "Monte Carlo", multi: "Vários pares" };

$$(".lab-act button[data-kind]").forEach((btn) => btn.addEventListener("click", () => {
  const kind = btn.dataset.kind;
  const req = labRequest(kind);
  const old = btn.textContent;
  btn.textContent = "Calculando…";
  $("#lab-out").innerHTML = `<div class="card muted">${KIND_NAMES[kind]}: baixando preços e calculando… (pode levar alguns segundos)</div>`;
  act(btn, async () => {
    const res = await api("lab", req);
    LAB = { kind, req, res };
    showLab();
    $("#lab-out").scrollIntoView({ behavior: "smooth", block: "start" });
  }).finally(() => {
    btn.textContent = old;
    if (!LAB || LAB.req !== req) $("#lab-out").innerHTML = "";
  });
}));

$("#lab-fill").addEventListener("click", () => {
  if (!S) return;
  fillForm($("#lab-form"), S.config);
  const ps = S.config.symbols && S.config.symbols.length ? S.config.symbols : [S.config.symbol];
  if (ps.length > 1) $("#multi-syms").value = ps.join(", ");
});

function stratLabel(req) {
  return `${(STRATS[req.params.strategy] || {}).name} · ${esc(req.symbol.toUpperCase())} ${req.interval} · ${req.days} dias`;
}

function showLab() {
  const { kind } = LAB;
  ({ backtest: showBacktest, walkforward: showWF, heatmap: showHeat, montecarlo: showMC, multi: showMulti })[kind]();
  window.labRedraw();
}

window.labRedraw = () => {
  if (!LAB) return;
  const r = LAB.res;
  if (LAB.kind === "backtest" || LAB.kind === "walkforward" || LAB.kind === "multi") {
    drawEquity("lab-eq", r.equity, r.benchEq, LAB.req.start);
    drawDD("lab-dd", r.drawdown);
  }
  if (LAB.kind === "montecarlo") drawHist("lab-hist", r);
};

// ---------- backtest ----------
function showBacktest() {
  const { req, res: r } = LAB, m = r.metrics, b = r.bench;
  const q = req.symbol.toUpperCase().endsWith("BRL") ? "BRL" : "USDT";
  const v = [
    `Retorno por risco (Sharpe): <b>${num(m.sharpe, 2)}</b> contra <b>${num(b.sharpe, 2)}</b> de só comprar e segurar. Maior queda: <b>${num(m.maxDDPct, 0)}%</b> contra <b>${num(b.maxDDPct, 0)}%</b>.`,
    m.significant
      ? `O Sharpe passou no teste estatístico (t = ${num(m.sharpeT, 2)}). Mas se você testou várias estratégias e ajustes até chegar nesta, a chance de ser sorte é bem maior que parece.`
      : `Estatisticamente, <b>não dá para distinguir de sorte</b> (t = ${num(m.sharpeT, 2)}; precisaria passar de 1,96). É comum em cripto: os períodos são curtos e o preço oscila muito.`,
    `Este teste é <b>dentro da amostra</b>. O próximo passo é o <b>walk-forward</b>, que mostra se a estratégia aguenta dados que ela não viu.`,
  ];
  $("#lab-out").innerHTML = `
    <div class="card"><h3>Backtest — ${stratLabel(req)}</h3>
      <p class="muted small">${dt(r.from)} a ${dt(r.to)} · ${r.candles} velas · ${money(r.startEquity, q)} → ${money(r.endEquity, q)} · taxas pagas ${money(r.feesPaid, q)}</p>
      <ul class="verdict">${v.map((x) => `<li>${x}</li>`).join("")}</ul>
      ${metricsTable([["Robô", m], ["Comprar e segurar", b]])}
      <div class="row end"><button class="btn primary" id="lab-apply">Usar estes ajustes no robô</button></div>
    </div>
    ${chartCard("lab-eq", "Patrimônio", LEG_EQ)}
    ${chartCard("lab-dd", "Queda desde o pico (drawdown)", `<i style="--c:var(--danger)"></i>Robô`, 160)}
    <div class="card"><h3>Operações</h3><div class="table-wrap"><table>${tradeRows(r.trades.slice().reverse().slice(0, 100), q)}${r.trades.length > 100 ? `<tr><td colspan="7" class="muted">… e mais ${r.trades.length - 100} mais antigas</td></tr>` : ""}</table></div></div>`;
  $("#lab-apply").addEventListener("click", (e) => applyToRobot(e.target));
}

async function applyToRobot(btn) {
  const { req } = LAB;
  await act(btn, async () => {
    const ps = (S.config.symbols || [S.config.symbol]).map(esc).join(", ");
    if (!(await ask("Usar estes ajustes?", `<p>O robô passa a usar <b>${(STRATS[req.params.strategy] || {}).name}</b>, velas de <b>${req.interval}</b>, com os ajustes de risco deste teste.</p><p class="muted">Os pares da carteira continuam os mesmos (${ps}). Para trocar os pares, use o teste "Vários pares" ou Ajustes.</p>`, { okText: "Usar" }))) return;
    await patchConfig({ interval: req.interval, params: req.params, tradePct: req.tradePct, maxBuy: req.maxBuy });
    fillConfig();
    toast("Ajustes aplicados ao robô");
  });
}

// ---------- walk-forward ----------
function showWF() {
  const { req, res: r } = LAB, o = r.oos, b = r.bench;
  const changes = r.folds.filter((f, i) => i > 0 && f.chosen !== r.folds[i - 1].chosen).length;
  const v = [];
  v.push(`<b>Dentro da amostra</b> (onde o otimizador escolheu os parâmetros), o Sharpe médio foi <b>${num(r.isSharpeAvg, 2)}</b>. <b>Fora da amostra</b> (nos meses seguintes, que ele não viu), foi <b>${num(r.oosSharpeAvg, 2)}</b>.`);
  if (r.isSharpeAvg > 0.3 && r.oosSharpeAvg <= 0) v.push(`<b>Queda forte: sinal clássico de overfitting.</b> A otimização se ajustou ao ruído do passado, e isso não se repete em dados novos.`);
  else if (r.oosSharpeAvg > 0 && r.oosSharpeAvg >= r.isSharpeAvg * 0.5) v.push(`Boa parte da qualidade se manteve em dados novos. É um bom sinal (não uma garantia).`);
  else v.push(`Parte da qualidade se perdeu em dados novos.`);
  v.push(`Fora da amostra, o robô fez <b>${pct(o.returnPct)}</b> (Sharpe ${num(o.sharpe, 2)}, maior queda ${num(o.maxDDPct, 0)}%) contra <b>${pct(b.returnPct)}</b> de só segurar (Sharpe ${num(b.sharpe, 2)}, queda ${num(b.maxDDPct, 0)}%).`);
  v.push(`Os parâmetros escolhidos mudaram em ${changes} de ${r.folds.length - 1} trocas de janela${changes > r.folds.length / 2 ? ": <b>instáveis</b>, sinal de que não há um ajuste \"certo\", só ruído" : ""}.`);
  $("#lab-out").innerHTML = `
    <div class="card"><h3>Walk-forward — ${stratLabel(req)}</h3>
      <p class="muted small">Treino de ${req.trainDays} dias, teste de ${req.testDays} dias, otimizando ${$("#wf-obj").selectedOptions[0].text} entre ${r.combos} combinações. Os ajustes de risco ficam fixos. A posição é fechada no fim de cada janela.</p>
      <ul class="verdict">${v.map((x) => `<li>${x}</li>`).join("")}</ul>
      ${metricsTable([["Robô (fora da amostra)", o], ["Comprar e segurar", b]])}
    </div>
    ${chartCard("lab-eq", "Patrimônio fora da amostra (as janelas de teste emendadas)", LEG_EQ)}
    ${chartCard("lab-dd", "Queda desde o pico", `<i style="--c:var(--danger)"></i>Robô`, 160)}
    <div class="card"><h3>Janelas</h3><div class="table-wrap"><table>
      <tr><th>Teste</th><th>Parâmetros escolhidos no treino</th><th>Sharpe no treino</th><th>Sharpe no teste</th><th>Robô no teste</th><th>Segurar no teste</th><th>Operações</th></tr>
      ${r.folds.map((f) => `<tr><td>${new Date(f.testFrom).toLocaleDateString("pt-BR")} a ${new Date(f.testTo).toLocaleDateString("pt-BR")}</td>
        <td>${esc(f.chosen)}</td><td>${num(f.isSharpe, 2)}</td><td class="${cls(f.oosSharpe)}">${num(f.oosSharpe, 2)}</td>
        <td class="${cls(f.oosReturn)}">${pct(f.oosReturn)}</td><td class="${cls(f.benchReturn)}">${pct(f.benchReturn)}</td><td>${f.trades}</td></tr>`).join("")}
    </table></div></div>`;
}

// ---------- mapa ----------
const HEAT_KEYS = {
  ema: ["fastEMA", "slowEMA"], donchian: ["breakoutN", "exitN"], rsi2: ["mrBuyBelow", "mrSellAbove"],
  tsmom: (p) => ["momDays", p.stopMode === "atr" ? "atrMult" : "stopLossPct"],
};

function showHeat() {
  const { req, res: h } = LAB;
  let good = 0, total = 0;
  h.cells.forEach((row) => row.forEach((c) => { if (c) { total++; if (c.sharpe > h.benchSharpe) good++; } }));
  const color = (s) => {
    const d = s - h.benchSharpe;
    return d >= 0 ? `rgba(62,207,142,${Math.min(0.85, 0.15 + d * 0.7)})` : `rgba(255,93,93,${Math.min(0.85, 0.15 - d * 0.7)})`;
  };
  const share = total ? good / total : 0;
  const v = [
    `<b>${good} de ${total}</b> combinações tiveram Sharpe acima do de só segurar (${num(h.benchSharpe, 2)}).`,
    share > 0.6 ? `Uma região grande e verde é sinal de <b>robustez</b>: o resultado não depende de um número mágico.`
      : share > 0.25 ? `Resultado misto: depende bastante dos parâmetros. Prefira o meio de uma região verde, nunca uma casa isolada.`
        : `A maioria das combinações perde de só segurar. Se aparecer uma casa boa isolada, desconfie: provavelmente é sorte.`,
    `Clique numa casa para colocar esses parâmetros no formulário. Atenção: escolher a melhor casa deste mapa também é otimizar no passado. Confirme com o walk-forward.`,
  ];
  $("#lab-out").innerHTML = `
    <div class="card"><h3>Mapa de parâmetros — ${stratLabel(req)}</h3>
      <ul class="verdict">${v.map((x) => `<li>${x}</li>`).join("")}</ul>
      <div class="table-wrap"><table class="heat">
        <tr><th>${esc(h.yName)} ↓ / ${esc(h.xName)} →</th>${h.xVals.map((x) => `<th>${num(x, x % 1 ? 1 : 0)}</th>`).join("")}</tr>
        ${h.yVals.map((y, yi) => `<tr><th>${num(y, y % 1 ? 1 : 0)}</th>${h.xVals.map((x, xi) => {
          const c = h.cells[yi][xi];
          if (!c) return `<td class="na">—</td>`;
          const cur = x === h.curX && y === h.curY ? " cur" : "";
          return `<td class="hc${cur}" data-x="${x}" data-y="${y}" style="background:${color(c.sharpe)}" title="Retorno ${pct(c.returnPct)} · queda ${num(c.maxDDPct, 0)}% · ${c.trades} operações">${num(c.sharpe, 2)}<small>${pct(c.returnPct)}</small></td>`;
        }).join("")}</tr>`).join("")}
      </table></div>
      <p class="muted small">Cada casa: Sharpe (em cima) e retorno total (embaixo). Verde = melhor que só segurar; vermelho = pior. Contorno = ajuste atual do formulário.</p>
    </div>`;
  $$(".heat td.hc").forEach((td) => td.addEventListener("click", () => {
    const f = $("#lab-form"), p = readParams(f);
    const keys = typeof HEAT_KEYS[p.strategy] === "function" ? HEAT_KEYS[p.strategy](p) : HEAT_KEYS[p.strategy];
    f.elements[keys[0]].value = td.dataset.x;
    f.elements[keys[1]].value = td.dataset.y;
    $$(".heat td.cur").forEach((x) => x.classList.remove("cur"));
    td.classList.add("cur");
    toast(`Parâmetros ${td.dataset.x} / ${td.dataset.y} colocados no formulário`);
  }));
}

// ---------- Monte Carlo ----------
function showMC() {
  const { req, res: r } = LAB;
  const P = ["5%", "25%", "50% (mediana)", "75%", "95%"];
  const v = [
    `Sorteamos ${num(r.runs, 0)} vezes a sequência das ${r.trades} operações do backtest (com reposição). O resultado real foi ${pct(r.originalRet)}, com queda de ${num(r.originalDD, 0)}%.`,
    `Em <b>5%</b> dos sorteios, o resultado foi pior que <b>${pct(r.retP[0])}</b>; em metade, melhor que ${pct(r.retP[2])}. <b>Chance de terminar no prejuízo: ${num(r.probLoss, 0)}%.</b>`,
    `Maior queda típica: ${num(r.ddP[2], 0)}%. Em 5% dos casos, passou de <b>${num(r.ddP[4], 0)}%</b>. Chance de uma queda de 30% ou mais: ${num(r.probDD30, 0)}%. Planeje o dinheiro pensando nesse pior caso.`,
    `Limite do método: supõe que as operações são independentes entre si e que o futuro se parece com o passado.`,
  ];
  $("#lab-out").innerHTML = `
    <div class="card"><h3>Monte Carlo — ${stratLabel(req)}</h3>
      <ul class="verdict">${v.map((x) => `<li>${x}</li>`).join("")}</ul>
      <div class="table-wrap"><table class="mtable"><tr><th>Em quantos % dos sorteios ficou abaixo de…</th>${P.map((p) => `<th>${p}</th>`).join("")}</tr>
        <tr><td>Retorno final</td>${r.retP.map((x) => `<td class="${cls(x)}">${pct(x)}</td>`).join("")}</tr>
        <tr><td>Maior queda</td>${r.ddP.map((x) => `<td>${fmtDD(x)}</td>`).join("")}</tr></table></div>
    </div>
    ${chartCard("lab-hist", "Distribuição do retorno final", `<i style="--c:var(--danger)"></i>prejuízo <i style="--c:var(--ok)"></i>lucro`, 200)}`;
}

// ---------- vários pares ----------
function showMulti() {
  const { req, res: r } = LAB;
  const ok = r.rows.filter((x) => !x.error);
  const better = ok.filter((x) => x.m.sharpe > x.bench.sharpe).length;
  const p = r.portfolio, pb = r.portBench;
  const v = [
    `Sharpe do robô melhor que o de só segurar em <b>${better} de ${ok.length}</b> pares.`,
    r.portAssets >= 2 ? `Na <b>carteira</b> com os ${r.portAssets} pares, simulada <b>exatamente como o robô ao vivo opera</b> (o mesmo dinheiro para todos, cada compra até 1/${r.portAssets} do patrimônio, vendas antes das compras, taxas): robô ${pct(p.returnPct)} (Sharpe ${num(p.sharpe, 2)}, queda ${num(p.maxDDPct, 0)}%) contra ${pct(pb.returnPct)} de dividir igualmente e só segurar (Sharpe ${num(pb.sharpe, 2)}, queda ${num(pb.maxDDPct, 0)}%).` : "",
    r.portAssets >= 2 && !p.trades && req.start / r.portAssets * req.tradePct / 100 < 5.1
      ? `<b class="neg">A carteira não fez nenhuma compra:</b> com ${num(req.start, 2)} divididos em ${r.portAssets} pares, cada compra seria de ${num(req.start / r.portAssets * req.tradePct / 100, 2)}, menos que o mínimo de 5 por ordem da Binance. Use menos pares ou mais dinheiro.` : "",
    `Cuidado: a lista tem criptos que são grandes <b>hoje</b> (as que sumiram não entram, e isso melhora o passado: viés de sobrevivência).`,
  ].filter(Boolean);
  const contrib = {};
  (r.perSymbol || []).forEach((x) => (contrib[x.symbol] = x));
  const q = (ok[0] && ok[0].symbol.endsWith("BRL")) ? "BRL" : "USDT";
  const row = (name, m, b, extra = "", sym = "") => `<tr class="${extra}"><td>${name}</td><td class="${cls(m.returnPct)}">${pct(m.returnPct)}</td><td>${num(m.sharpe, 2)}</td><td>${fmtDD(m.maxDDPct)}</td><td>${m.trades || "—"}</td>
    <td class="sep ${cls(b.returnPct)}">${pct(b.returnPct)}</td><td>${num(b.sharpe, 2)}</td><td>${fmtDD(b.maxDDPct)}</td><td>${m.sharpe > b.sharpe ? "✔" : ""}</td>
    <td class="sep ${sym && contrib[sym] ? cls(contrib[sym].pnl) : ""}">${sym && contrib[sym] ? signed(contrib[sym].pnl, q) : extra ? signed(r.portfolio.returnPct * req.start / 100, q) : ""}</td></tr>`;
  $("#lab-out").innerHTML = `
    <div class="card"><h3>Vários pares — ${(STRATS[req.params.strategy] || {}).name} · ${req.interval} · ${req.days} dias</h3>
      <ul class="verdict">${v.map((x) => `<li>${x}</li>`).join("")}</ul>
      <div class="table-wrap"><table class="mtable">
        <tr><th></th><th colspan="4">Robô (par sozinho, todo o dinheiro)</th><th colspan="3" class="sep">Comprar e segurar</th><th></th><th class="sep">Na carteira</th></tr>
        <tr><th>Par</th><th>Retorno</th><th>Sharpe</th><th>Maior queda</th><th>Operações</th><th class="sep">Retorno</th><th>Sharpe</th><th>Maior queda</th><th>Robô melhor?</th><th class="sep">Resultado do par</th></tr>
        ${r.rows.map((x) => (x.error ? `<tr><td>${esc(x.symbol)}</td><td colspan="9" class="neg">${esc(x.error)}</td></tr>` : row(esc(x.symbol), x.m, x.bench, "", x.symbol))).join("")}
        ${r.portAssets >= 2 ? row("<b>Carteira</b>", p, pb, "total") : ""}
      </table></div>
      <p class="muted small">"Resultado do par" = quanto cada par ganhou ou perdeu dentro da carteira, começando com ${money(req.start, q)}.</p>
      ${r.portAssets >= 2 ? `<div class="row end"><button class="btn primary" id="lab-apply-multi">Usar esta carteira no robô</button></div>` : ""}
    </div>
    ${r.portAssets >= 2 ? chartCard("lab-eq", "Patrimônio da carteira", LEG_EQ) + chartCard("lab-dd", "Queda da carteira desde o pico", `<i style="--c:var(--danger)"></i>Robô`, 160) : ""}`;
}

async function applyPortfolio(btn) {
  const { req, res: r } = LAB;
  const syms = r.rows.filter((x) => !x.error).map((x) => x.symbol);
  await act(btn, async () => {
    if (!(await ask("Usar esta carteira?", `<p>O robô passa a operar <b>${syms.map(esc).join(", ")}</b> com <b>${(STRATS[req.params.strategy] || {}).name}</b>, velas de <b>${req.interval}</b> e os ajustes de risco deste teste. Cada compra usa no máximo 1/${syms.length} do patrimônio.</p>`, { okText: "Usar" }))) return;
    await patchConfig({ symbols: syms, interval: req.interval, params: req.params, tradePct: req.tradePct, maxBuy: req.maxBuy });
    fillConfig();
    toast("Carteira aplicada ao robô");
  });
}

document.addEventListener("click", (e) => {
  if (e.target.id === "lab-apply-multi") applyPortfolio(e.target);
});
