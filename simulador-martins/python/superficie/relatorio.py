"""Relatório HTML interativo (Plotly) + métricas em JSON.

Cada superfície 3D tem, para os eixos X, Y e Z, uma barra para aumentar/diminuir, uma lista fixa de
porcentagens e um campo para digitar a porcentagem que quiser. Arraste para girar, role para aproximar.
"""
from __future__ import annotations

import html
import json
from dataclasses import asdict
from pathlib import Path

import numpy as np
import pandas as pd
import plotly.io as pio
from plotly.offline import get_plotlyjs

from . import visual

PLOTLY_CDN = "https://cdn.jsdelivr.net/npm/plotly.js-dist-min@3.0.1/plotly.min.js"
PRESETS = [10, 25, 50, 75, 100, 125, 150, 200, 300, 400, 500]

CSS = """
/* Relatório técnico: coluna de leitura com faixas de figuras largas; números em monoespaçada */
:root {
  --bg: #f5f6f8; --painel: #ffffff; --tinta: #14202e; --suave: #5a6677; --regua: #dde2e8;
  --acento: #1f5a8f; --acento-tinta: #ffffff; --pos: #1d7a4f; --neg: #b23b3b; --aviso: #8a5a00; --aviso-bg: #fbf3e2;
  --sans: "IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif;
  --mono: "IBM Plex Mono", ui-monospace, "SFMono-Regular", Menlo, Consolas, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #0f151c; --painel: #161e28; --tinta: #e3e9f0; --suave: #95a3b4; --regua: #2a3644;
  --acento: #7fb2d8; --acento-tinta: #0f151c; --pos: #5cc28f; --neg: #ef8a86; --aviso: #f0c46a; --aviso-bg: #2a2416; color-scheme: dark; } }
:root[data-theme="dark"] {
  --bg: #0f151c; --painel: #161e28; --tinta: #e3e9f0; --suave: #95a3b4; --regua: #2a3644;
  --acento: #7fb2d8; --acento-tinta: #0f151c; --pos: #5cc28f; --neg: #ef8a86; --aviso: #f0c46a; --aviso-bg: #2a2416; color-scheme: dark; }
body { background: var(--bg); color: var(--tinta); font-family: var(--sans); font-size: 15px; line-height: 1.55;
  padding-left: max(16px, env(safe-area-inset-left, 0px)); padding-right: max(16px, env(safe-area-inset-right, 0px)); padding-block: 24px 48px; }
main { max-width: 1180px; margin: 0 auto; display: grid; gap: 28px; }
h1 { font-size: 1.75rem; line-height: 1.2; margin: 0; text-wrap: balance; letter-spacing: -0.01em; }
h2 { font-size: 1.2rem; margin: 0 0 6px; text-wrap: balance; }
h3 { font-size: 0.78rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--suave); margin: 0 0 8px; font-weight: 600; }
p { margin: 0; max-width: 72ch; }
.sub { color: var(--suave); }
header { display: grid; gap: 8px; }
.selo { display: inline-flex; gap: 8px; flex-wrap: wrap; }
.selo span { font-family: var(--mono); font-size: 12px; border: 1px solid var(--regua); border-radius: 4px; padding: 2px 8px; background: var(--painel); }
section { display: grid; gap: 14px; min-width: 0; }
.bloco { background: var(--painel); border: 1px solid var(--regua); border-radius: 6px; padding: 16px; display: grid; gap: 12px; min-width: 0; }
.kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 1px; background: var(--regua); border: 1px solid var(--regua); border-radius: 6px; overflow: hidden; }
.kpi { background: var(--painel); padding: 12px 14px; display: grid; gap: 2px; }
.kpi .k { font-size: 11px; text-transform: uppercase; letter-spacing: .07em; color: var(--suave); font-weight: 600; }
.kpi .v { font-family: var(--mono); font-size: 1.35rem; font-weight: 600; font-variant-numeric: tabular-nums; }
.kpi .s { font-size: 12px; color: var(--suave); }
.pos { color: var(--pos); } .neg { color: var(--neg); }
.tabela { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; font-size: 13px; font-variant-numeric: tabular-nums; }
th, td { padding: 6px 10px; border-bottom: 1px solid var(--regua); text-align: right; white-space: nowrap; }
th { font-size: 11px; text-transform: uppercase; letter-spacing: .06em; color: var(--suave); font-weight: 600; }
td:first-child, th:first-child { text-align: left; }
td { font-family: var(--mono); }
.aviso { background: var(--aviso-bg); color: var(--aviso); border-radius: 6px; padding: 10px 12px; font-size: 14px; }
.fig { width: 100%; min-height: 320px; }
.duas { display: grid; gap: 16px; grid-template-columns: minmax(0, 1fr); }
@media (min-width: 900px) { .duas { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); } }
.eixos { display: grid; gap: 8px; border-top: 1px solid var(--regua); padding-top: 10px; }
.eixo { display: grid; grid-template-columns: 9.5em minmax(0, 1fr) auto auto 3.6em; gap: 10px; align-items: center; }
.eixo .nome { font-size: 13px; font-weight: 600; }
.eixo input[type=range] { width: 100%; accent-color: var(--acento); }
.eixo select, .eixo input[type=number] { font: 13px var(--mono); background: var(--painel); color: var(--tinta); border: 1px solid var(--regua); border-radius: 4px; padding: 4px 6px; }
.eixo input[type=number] { width: 5.5em; }
.eixo output { font: 600 13px var(--mono); text-align: right; }
.eixos .rodape { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; font-size: 12px; color: var(--suave); }
button { font: 600 13px var(--sans); border: 1px solid var(--regua); background: var(--painel); color: var(--tinta); border-radius: 4px; padding: 5px 10px; cursor: pointer; }
button:focus-visible, select:focus-visible, input:focus-visible { outline: 2px solid var(--acento); outline-offset: 2px; }
@media (max-width: 640px) { .eixo { grid-template-columns: 1fr auto auto 3.4em; } .eixo .nome { grid-column: 1 / -1; } }
ul.lista { margin: 0; padding-left: 1.2em; display: grid; gap: 4px; max-width: 80ch; }
code { font-family: var(--mono); font-size: 0.92em; }
"""

JS = r"""
const PRESETS = %PRESETS%;
const tok = (n) => getComputedStyle(document.documentElement).getPropertyValue(n).trim();
function corTema(div) {
  const ink = tok('--tinta'), grade = tok('--regua'), sub = tok('--suave');
  const eixo = { color: sub, gridcolor: grade, zerolinecolor: grade };
  const u = { 'font.color': ink, 'title.font.color': ink };
  if (div.layout && div.layout.scene) Object.assign(u, { 'scene.xaxis.color': sub, 'scene.yaxis.color': sub, 'scene.zaxis.color': sub,
    'scene.xaxis.gridcolor': grade, 'scene.yaxis.gridcolor': grade, 'scene.zaxis.gridcolor': grade });
  else Object.assign(u, { 'xaxis.color': sub, 'yaxis.color': sub, 'xaxis.gridcolor': grade, 'yaxis.gridcolor': grade });
  Plotly.relayout(div, u);
}
function desenhar() {
  for (const el of document.querySelectorAll('script[type="application/json"][data-fig]')) {
    const div = document.getElementById(el.dataset.fig), f = JSON.parse(el.textContent);
    Plotly.newPlot(div, f.data, f.layout, { responsive: true, displaylogo: false, modeBarButtonsToRemove: ['toImage'] }).then(() => corTema(div));
  }
  for (const box of document.querySelectorAll('.eixos')) ligarEixos(box);
}
function ligarEixos(box) {
  const div = document.getElementById(box.dataset.fig), base = { ...(div.layout.scene.aspectratio || { x: 1, y: 1, z: 1 }) }, pct = { x: 100, y: 100, z: 100 };
  const aplica = () => Plotly.relayout(div, { 'scene.aspectmode': 'manual', 'scene.aspectratio': { x: base.x * pct.x / 100, y: base.y * pct.y / 100, z: base.z * pct.z / 100 } });
  const mostra = (e) => { const l = box.querySelector(`[data-e="${e}"]`), v = pct[e];
    l.querySelector('input[type=range]').value = Math.min(500, Math.max(10, v));
    const s = l.querySelector('select'); s.value = PRESETS.includes(v) ? String(v) : '';
    l.querySelector('output').textContent = `${v}%`; };
  for (const l of box.querySelectorAll('.eixo')) {
    const e = l.dataset.e;
    l.querySelector('input[type=range]').addEventListener('input', (ev) => { pct[e] = +ev.target.value; mostra(e); aplica(); });
    l.querySelector('select').addEventListener('change', (ev) => { if (ev.target.value) { pct[e] = +ev.target.value; mostra(e); aplica(); } });
    l.querySelector('input[type=number]').addEventListener('change', (ev) => { const v = Math.round(+ev.target.value); if (v >= 1 && v <= 2000) { pct[e] = v; mostra(e); aplica(); } });
  }
  box.querySelector('button.todos').addEventListener('click', () => { for (const e of 'xyz') { pct[e] = 100; mostra(e); } aplica(); });
}
const tema = () => document.querySelectorAll('.fig.js-plotly-plot').forEach(corTema);
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', tema);
new MutationObserver(tema).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
if (window.Plotly) desenhar(); else window.addEventListener('load', desenhar);
"""


def _fig(fig, fid, eixos=None):
    j = pio.to_json(fig, validate=False)
    out = f'<div class="fig" id="{fid}" role="img" aria-label="{html.escape(fig.layout.title.text or "gráfico")}"></div>\n<script type="application/json" data-fig="{fid}">{j}</script>'
    if eixos:
        ops = "".join(f'<option value="{p}">{p}%</option>' for p in PRESETS)
        linhas = "".join(
            f'<div class="eixo" data-e="{e}"><span class="nome">{e.upper()} · {html.escape(n)}</span>'
            f'<input type="range" min="10" max="500" step="5" value="100" id="{fid}-{e}-barra" aria-label="Aumentar ou diminuir o eixo {e.upper()}">'
            f'<select id="{fid}-{e}-lista" aria-label="Porcentagem fixa do eixo {e.upper()}"><option value="">lista</option>{ops}</select>'
            f'<input type="number" min="1" max="2000" step="1" placeholder="outro %" id="{fid}-{e}-livre" aria-label="Porcentagem livre do eixo {e.upper()}">'
            f'<output>100%</output></div>' for e, n in zip("xyz", eixos))
        out += (f'<div class="eixos" data-fig="{fid}">{linhas}<div class="rodape"><button type="button" class="todos">Voltar a 100% nos três eixos</button>'
                '<span>Barra: 10% a 500% · lista fixa · ou digite qualquer % de 1 a 2000. Arraste o gráfico para girar e role para aproximar.</span></div></div>')
    return out


def _n(x, c=2, pct=False):
    if x is None or (isinstance(x, float) and not np.isfinite(x)):
        return "—"
    return f"{x * 100:.{c}f}%".replace(".", ",") if pct else f"{x:,.{c}f}".replace(",", "X").replace(".", ",").replace("X", ".")


def _tabela(df: pd.DataFrame) -> str:
    cab = "".join(f"<th>{html.escape(str(c))}</th>" for c in df.columns)
    lin = "".join("<tr>" + "".join(f"<td>{html.escape(str(v))}</td>" for v in row) + "</tr>" for row in df.itertuples(index=False))
    return f'<div class="tabela"><table><thead><tr>{cab}</tr></thead><tbody>{lin}</tbody></table></div>'


def _kpi(k, v, s="", cls=""):
    return f'<div class="kpi"><span class="k">{k}</span><span class="v {cls}">{v}</span><span class="s">{s}</span></div>'


def secao_ativo(r: dict, cfg, n: int) -> tuple[str, dict]:
    b = r["base"]; q = b["qual"]; g = b["grade"]; cubo = b["cubo"]; X = b["X"]
    h0 = 0 if b["fonte"] == "binance" else 9
    t = r["teste"]; v = r["val"]
    ses = cubo["sessoes"]; D = len(ses)
    corte = int(D * cfg.fracoes[0]) if cfg.validacao == "temporal" else int(D * 0.5)
    partes = [f'<section id="ativo-{n}"><h2>{html.escape(r["base"]["simbolo"])}</h2>',
              f'<p class="sub">Fonte: {"Binance Data Vision (arquivos oficiais, com saldo de agressão)" if b["fonte"] == "binance" else "TradingView (sem agressão; volume financeiro = volume × preço típico)"} · '
              f'{q["inicio"][:10]} a {q["fim"][:10]} ({q["meses_obtidos"]} meses) · {q["limpos"]:,} candles de 15 min · {q["lacunas"]} lacunas ({q["candles_faltando"]} candles faltando, não preenchidos) · '
              f'{q["duplicados"]} duplicados e {q["inconsistentes"]} inconsistentes removidos.</p>'.replace(",", ".")]
    if q["meses_obtidos"] < cfg.meses * 0.9:
        partes.append(f'<p class="aviso">A fonte só tem {q["meses_obtidos"]} meses de candles de 15 min para este ativo (pedido: {cfg.meses}). A análise seguiu com o que existe; para {cfg.meses} meses é preciso um feed com histórico intradiário maior.</p>')
    s = t["sim"]; c = t["cls"]; sm = t["sem_modelo"]
    partes.append('<div class="kpis">' + "".join([
        _kpi("Sharpe bruto (teste)", _n(s["sharpe_bruto"]), f'sem modelo: {_n(sm["sharpe_bruto"])}', "pos" if (s["sharpe_bruto"] or 0) > 0 else "neg"),
        _kpi("Sharpe líquido (teste)", _n(s["sharpe_liquido"]), f'custo {_n(cfg.custo)}% por operação', "pos" if (s["sharpe_liquido"] or 0) > 0 else "neg"),
        _kpi("Precisão", _n(c["precisao"], 1, True), f'taxa base {_n(c["taxa_base"], 1, True)}'),
        _kpi("Recall", _n(c["recall"], 1, True), f'{c["sinais"]:,} sinais'.replace(",", ".")),
        _kpi("AUC (teste)", _n(c["auc"], 3), f'validação {_n(v["cls"]["auc"], 3)}'),
        _kpi("Operações", f'{s["n"]:,}'.replace(",", "."), f'acerto {_n(s["acerto"], 1, True)}'),
        _kpi("Resultado acumulado", _n(s["ret_total"] / 100, 1, True), f'queda máx. {_n(s["queda_max"] / 100, 1, True)}', "pos" if s["ret_total"] > 0 else "neg"),
    ]) + "</div>")
    if not (np.isfinite(v["sim"]["sharpe_liquido"]) and v["sim"]["sharpe_liquido"] > 0):
        partes.append(f'<p class="aviso">Nenhuma configuração testada teve Sharpe líquido positivo na validação: com custo de {_n(cfg.custo)}% por operação, o sinal ainda não paga os custos neste ativo. O modelo separa bem os cenários (AUC acima de 0,5 indica poder de ordenação), mas o ganho por operação é pequeno. Caminhos: alvos maiores ou horizontes mais longos (lista em --alvo/--horizonte), custo menor (corretagem/maker) ou outro ativo (--ativos auto).</p>')
    blocos = r.get("blocos")
    desc = (f'Configuração escolhida na validação: alvo <b>{r["alvo"]}%</b>, stop <b>{r["stop"]}%</b>, horizonte <b>{r["horizonte"]} min</b>, lado <b>{cfg.lado}</b>, '
            f'modelo <b>{r["tipo"]}</b> {html.escape(json.dumps(r["hiper"]))}, limiar de probabilidade <b>{_n(r["limiar"], 3)}</b>. ')
    if blocos:
        desc += "Blocos (sem embaralhar, com embargo do horizonte): " + "; ".join(f'{k} {a[:10]} a {b_[:10]} ({m:,} candles)'.replace(",", ".") for k, (a, b_, m) in blocos.items()) + "."
    partes.append(f'<p>{desc}</p>')
    figs = {}
    # superfícies 3D (treino: auditoria sem olhar o teste)
    M = visual.superficie_media(cubo["S"], 0, corte, cfg.sigma) if hasattr(visual, "superficie_media") else None
    eixos = ("horário", "oscilação %", "volume")
    partes.append('<div class="bloco"><h3>Superfície 3D média do período de treino</h3><p class="sub">X: horário em candles de 15 min · Y: oscilação % desde a abertura da sessão · Z: volume financeiro médio negociado em cada faixa, suavizado (gaussiana). Só sessões de treino.</p>' +
                  _fig(visual.superficie(M, g, h0, f'{b["simbolo"]}: volume médio por horário e oscilação (treino)'), f"a{n}-sup", eixos) + "</div>")
    if q["tem_agressao"]:
        Ag = visual.superficie_media(cubo["A"], 0, corte, cfg.sigma)
        partes.append('<div class="bloco"><h3>Superfície 3D do saldo líquido de agressão</h3><p class="sub">Z: compras agressoras − vendas agressoras (em moeda cotada), média do treino. Azul = venda agressora domina; vermelho = compra.</p>' +
                      _fig(visual.superficie(Ag, g, h0, f'{b["simbolo"]}: saldo de agressão médio', "Saldo de agressão", visual.DIVERGENTE, centro=0), f"a{n}-agr", ("horário", "oscilação %", "agressão")) + "</div>")
    d = D - 1
    df_d = X[X["sessao"] == ses[d]]
    partes.append('<div class="bloco"><h3>Última sessão e anomalia contra o histórico</h3><p class="sub">À esquerda, o volume da última sessão por horário e faixa, com o caminho do preço. À direita, volume acumulado do dia menos a superfície histórica das sessões anteriores (vermelho = acima do normal).</p><div class="duas">' +
                  "<div>" + _fig(visual.superficie_sessao(cubo["S"][d], cubo["C"][d], cubo["Hm"][d], df_d, g, h0, f'Sessão {str(ses[d])[:10]}'), f"a{n}-ses", eixos) + "</div>" +
                  "<div>" + _fig(visual.anomalia(cubo["C"][d], cubo["Hm"][d], g, h0, "Anomalia do dia vs. histórico"), f"a{n}-ano", ("horário", "oscilação %", "diferença")) + "</div></div></div>")
    partes.append('<div class="bloco"><h3>Vista de cima (auditoria de distribuição)</h3>' + _fig(visual.mapa_calor(M, g, h0, "Volume médio por horário e oscilação (treino)"), f"a{n}-cal") + "</div>")
    # modelo
    rot = r["rot"]
    te = r["mascaras"][2] if "mascaras" in r else None
    partes.append('<div class="duas">' + '<div class="bloco">' + _fig(visual.matriz(c["matriz"]), f"a{n}-cm") + "</div>" +
                  ('<div class="bloco">' + _fig(visual.importancias(r["importancias"]), f"a{n}-imp") + "</div>" if "importancias" in r else "") + "</div>")
    curvas = {"validação": (v["sim"]["curva"], "#7fb2d8"), "teste (fora da amostra)": (s["curva"], "#e0b04c"), "teste sem modelo": (sm["curva"], "#888888")}
    partes.append('<div class="bloco">' + _fig(visual.curvas(curvas), f"a{n}-cur") + "</div>")
    if te is not None:
        partes.append('<div class="bloco">' + _fig(visual.calibracao(rot.loc[te, "y"].to_numpy(), t["p"]), f"a{n}-cal2") + "</div>")
    tab = pd.DataFrame([
        ["Validação", _n(v["cls"]["precisao"], 1, True), _n(v["cls"]["recall"], 1, True), _n(v["cls"]["f1"], 3), _n(v["cls"]["auc"], 3), _n(v["sim"]["sharpe_bruto"]), _n(v["sim"]["sharpe_liquido"]), v["sim"]["n"], _n(v["sim"]["acerto"], 1, True)],
        ["Teste", _n(c["precisao"], 1, True), _n(c["recall"], 1, True), _n(c["f1"], 3), _n(c["auc"], 3), _n(s["sharpe_bruto"]), _n(s["sharpe_liquido"]), s["n"], _n(s["acerto"], 1, True)],
        ["Teste sem modelo", "—", "—", "—", "—", _n(sm["sharpe_bruto"]), _n(sm["sharpe_liquido"]), sm["n"], _n(sm["acerto"], 1, True)]],
        columns=["Bloco", "Precisão", "Recall", "F1", "AUC", "Sharpe bruto", "Sharpe líquido", "Operações", "Acerto"])
    partes.append('<div class="bloco"><h3>Métricas</h3>' + _tabela(tab) + "</div>")
    if r.get("folds"):
        fd = pd.DataFrame(r["folds"])
        partes.append('<div class="bloco"><h3>Walk-forward por bloco</h3>' + _tabela(fd.round(3)) + "</div>")
    if isinstance(r.get("placar"), pd.DataFrame) and len(r["placar"]):
        pl = r["placar"].copy().round(3)
        partes.append('<div class="bloco"><h3>Otimização de alvo, stop e horizonte (só validação)</h3><p class="sub">Ordenado pelo Sharpe líquido da validação. O teste foi usado uma única vez, na configuração vencedora.</p>' + _tabela(pl) + "</div>")
    partes.append("</section>")
    metr = {"ativo": b["simbolo"], "fonte": b["fonte"], "qualidade": q, "alvo": r["alvo"], "stop": r["stop"], "horizonte": r["horizonte"], "modelo": r["tipo"], "hiper": r["hiper"],
            "limiar": r["limiar"], "teste": {**c, **{k: s[k] for k in ("n", "sharpe_bruto", "sharpe_liquido", "acerto", "ret_medio", "ret_total", "queda_max")}},
            "validacao": {**v["cls"], **{k: v["sim"][k] for k in ("n", "sharpe_bruto", "sharpe_liquido", "acerto")}}, "teste_sem_modelo": {k: sm[k] for k in ("n", "sharpe_bruto", "sharpe_liquido", "acerto")},
            "importancias": r.get("importancias", pd.Series(dtype=float)).round(4).to_dict()}
    return "\n".join(partes), metr


def gerar(res: dict, destino: Path, plotly: str = "inline", completo: bool = True) -> Path:
    cfg = res["cfg"]
    destino = Path(destino); destino.parent.mkdir(parents=True, exist_ok=True)
    secoes, metricas = [], []
    for n, r in enumerate(res["resultados"]):
        s, m = secao_ativo(r, cfg, n); secoes.append(s); metricas.append(m)
    nomes = ", ".join(m["ativo"] for m in metricas) or "—"
    resumo = ""
    if len(metricas) > 1:
        resumo = '<section><h2>Comparação entre ativos (teste fora da amostra)</h2>' + _tabela(pd.DataFrame([[m["ativo"], m["modelo"], f'{m["alvo"]}% / {m["stop"]}% / {m["horizonte"]} min', _n(m["teste"]["sharpe_bruto"]), _n(m["teste"]["sharpe_liquido"]), _n(m["teste"]["precisao"], 1, True), _n(m["teste"]["recall"], 1, True), m["teste"]["n"]] for m in metricas],
                   columns=["Ativo", "Modelo", "Alvo/stop/horizonte", "Sharpe bruto", "Sharpe líquido", "Precisão", "Recall", "Operações"])) + "</section>"
    if res.get("ranking") is not None and len(res["ranking"]):
        resumo += '<section><h2>Escolha automática do ativo (só validação)</h2><p class="sub">Cada candidato foi avaliado com a configuração pedida; vence o maior Sharpe líquido na validação. O teste do vencedor está abaixo.</p>' + _tabela(res["ranking"].round(3)) + "</section>"
    metodo = f"""<section><h2>Como foi feito e como se evita o vazamento de dados</h2><div class="bloco"><ul class="lista">
<li><b>Dados:</b> candles de 15 min limpos (duplicados e candles inconsistentes removidos; lacunas contadas, nunca preenchidas). Nada é estimado ou inventado.</li>
<li><b>Superfície:</b> X = horário da sessão, Y = oscilação % desde a abertura da sessão (preço conhecido desde o início do dia), Z = volume financeiro do candle alocado na faixa do preço de {"fechamento" if cfg.alocacao == "fechamento" else "toda a faixa percorrida (aproximação)"}; também saldo de agressão quando a fonte informa.</li>
<li><b>Ponto no tempo:</b> o candle t só vê o próprio dia até t (superfície parcial) e a superfície histórica das {cfg.janela_hist} sessões ANTERIORES. Volume relativo, agressão relativa e anomalias comparam com o mesmo horário das sessões passadas. Nenhuma estatística do conjunto inteiro entra nos atributos.</li>
<li><b>Suavização:</b> gaussiana (σ tempo {cfg.sigma[0]}, σ oscilação {cfg.sigma[1]}) na superfície histórica, que só tem sessões completas do passado; na parcial do dia, só no eixo da oscilação, porque suavizar no tempo puxaria candles futuros.</li>
<li><b>Alvo:</b> entrando no fechamento do candle, o preço anda {"a favor do movimento do dia" if cfg.lado == "continuacao" else ("para cima" if cfg.lado == "long" else "para baixo")} o alvo antes de tocar o stop dentro do horizonte. Alvo e stop no mesmo candle contam como stop.</li>
<li><b>Validação:</b> {"divisão temporal 70% treino, 15% validação, 15% teste" if cfg.validacao == "temporal" else f"walk-forward com {cfg.folds} blocos de janela crescente"}, sem embaralhar, com embargo do horizonte entre blocos. Modelo, hiperparâmetros, limiar, alvo/stop/horizonte e ativo são escolhidos na validação; o teste é usado uma vez.</li>
<li><b>Simulação:</b> uma posição por vez, saída no alvo, no stop ou no fim do horizonte. Sharpe anualizado com o resultado diário (365 dias por ano para cripto, 252 para B3). "Bruto" = antes de custos; "líquido" = com {_n(cfg.custo)}% por operação (ida e volta).</li>
</ul></div></section>"""
    plotly_tag = f'<script src="{PLOTLY_CDN}"></script>' if plotly == "cdn" else f'<script>{get_plotlyjs()}</script>'
    fontes = '<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;600&family=IBM+Plex+Sans:wght@400;600;700&display=swap">'
    corpo = (f'<title>Superfícies Intradiárias</title>\n<meta name="claude:viewport-fit-sides" content="cover">\n{fontes}\n<style>{CSS}</style>\n'
             f'<main><header><h3>Pipeline de superfícies 3D · fase 1 (MVP)</h3><h1>Predição intradiária: {html.escape(nomes)}</h1>'
             f'<p class="sub">Candles de 15 min · alvo {", ".join(map(str, cfg.alvo))}% · stop {", ".join(map(str, cfg.stop))}% · horizonte {", ".join(map(str, cfg.horizonte))} min · lado {cfg.lado} · {cfg.meses} meses pedidos · {"temporal 70/15/15" if cfg.validacao == "temporal" else "walk-forward"} · modelos {", ".join(cfg.modelos)}.</p>'
             f'</header>{resumo}{"".join(secoes)}{metodo}</main>\n{plotly_tag}\n<script>{JS.replace("%PRESETS%", json.dumps(PRESETS))}</script>')
    if completo:   # arquivo para abrir direto no navegador
        corpo = '<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"></head><body>' + corpo + "</body></html>"
    destino.write_text(corpo, encoding="utf-8")
    (destino.with_suffix(".json")).write_text(json.dumps({"config": {k: (list(v) if isinstance(v, tuple) else v) for k, v in asdict(cfg).items()}, "ativos": metricas, "segundos": res["segundos"]},
                                                        ensure_ascii=False, indent=2, default=lambda o: None if isinstance(o, float) and not np.isfinite(o) else (o.item() if hasattr(o, "item") else str(o))), encoding="utf-8")
    return destino
