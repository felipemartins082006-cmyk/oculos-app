"""Relatório em HTML (arquivo único, gráficos embutidos) com o mesmo visual sóbrio do app."""
from __future__ import annotations

import base64
import datetime as dt
import html
import io

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import matplotlib.ticker as mticker  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402

CORES = ["#1d3b66", "#c0504d", "#2f8f6f", "#9a5b06", "#6b5ca5", "#3f8fbf", "#8b95a3"]
plt.rcParams.update({
    "font.family": "DejaVu Sans", "font.size": 9, "axes.edgecolor": "#c8ced7", "axes.labelcolor": "#3b4656",
    "xtick.color": "#6b7584", "ytick.color": "#6b7584", "axes.grid": True, "grid.color": "#e1e4e9", "grid.linewidth": 0.6,
    "axes.spines.top": False, "axes.spines.right": False, "legend.frameon": False, "figure.dpi": 110,
})


def _brl(v, curto=True):
    if not np.isfinite(v):
        return "—"
    a = abs(v); s = "−" if v < 0 else ""
    if curto and a >= 1e6:
        return f"{s}R$ {a / 1e6:,.2f} mi".replace(",", "X").replace(".", ",").replace("X", ".")
    if curto and a >= 1e4:
        return f"{s}R$ {a / 1e3:,.0f} mil".replace(",", ".")
    return f"{s}R$ {a:,.0f}".replace(",", ".")


def _pct(v, c=1):
    return "—" if v is None or not np.isfinite(v) else f"{v * 100:.{c}f}%".replace(".", ",")


def _fig(fig) -> str:
    buf = io.BytesIO(); fig.savefig(buf, format="png", bbox_inches="tight", dpi=150); plt.close(fig)
    return f'<img alt="" src="data:image/png;base64,{base64.b64encode(buf.getvalue()).decode()}">'


def _eixo_brl(ax):
    ax.yaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: _brl(v)))


def leque(res) -> str:
    fig, ax = plt.subplots(figsize=(9, 4.2))
    x = res.pontos / 12
    for i, n in enumerate(res.nomes):
        p = res.percentis_tempo(i)
        cor = CORES[i % len(CORES)]
        if i == 0:
            ax.fill_between(x, p["P10"], p["P90"], color=cor, alpha=0.12, lw=0, label=f"{n}: P10–P90")
            ax.fill_between(x, p["P25"], p["P75"], color=cor, alpha=0.22, lw=0, label=f"{n}: P25–P75")
        ax.plot(x, p["P50"], color=cor, lw=2.2 if i == 0 else 1.6, label=f"{n}: mediana")
    ax.plot(x, res.cdi_medio, color="#a0711f", ls="--", lw=1.4, label="CDI (média)")
    ax.plot(x, res.inv_medio, color="#8b95a3", ls=":", lw=1.4, label="Total aportado")
    _eixo_brl(ax); ax.set_xlabel("anos"); ax.legend(fontsize=7.5, ncol=2, loc="upper left")
    return _fig(fig)


def histograma(res) -> str:
    fig, ax = plt.subplots(figsize=(9, 3.2))
    for i, n in enumerate(res.nomes):
        f = res.fin[i].valores()
        lo, hi = np.quantile(f, [0.005, 0.995])
        ax.hist(np.clip(f, lo, hi), bins=80, alpha=0.45, color=CORES[i % len(CORES)], label=n, density=True)
    ax.axvline(res.investido, color="#8b95a3", ls=":", lw=1.2, label="aportado")
    if res.plano.meta:
        ax.axvline(res.plano.meta, color="#9a5b06", ls="--", lw=1.2, label="meta")
    ax.xaxis.set_major_formatter(mticker.FuncFormatter(lambda v, _: _brl(v))); ax.set_yticks([])
    ax.legend(fontsize=7.5)
    return _fig(fig)


def linhas_backtest(P: pd.DataFrame, log=True) -> str:
    fig, (a1, a2) = plt.subplots(2, 1, figsize=(9, 5.6), height_ratios=[2.2, 1], sharex=True)
    x = P.index.to_timestamp()
    for i, c in enumerate(P.columns):
        v = 10_000 * (1 + P[c]).cumprod()
        estilo = {"CDI": ("#a0711f", "--"), "IPCA": ("#8b95a3", ":")}.get(c, (CORES[i % len(CORES)], "-"))
        a1.plot(x, v, color=estilo[0], ls=estilo[1], lw=2 if estilo[1] == "-" else 1.3, label=c)
        if c not in ("CDI", "IPCA"):
            n = (1 + P[c]).cumprod(); a2.plot(x, n / n.cummax() - 1, color=estilo[0], lw=1.2)
    if log:
        a1.set_yscale("log")
    _eixo_brl(a1); a1.legend(fontsize=7.5, ncol=3); a1.set_title("R$ 10 mil investidos", fontsize=9, loc="left")
    a2.yaxis.set_major_formatter(mticker.PercentFormatter(1.0)); a2.set_title("Queda a partir do topo", fontsize=9, loc="left")
    return _fig(fig)


def grafico_fronteira(fr: pd.DataFrame, pontos: dict, nuvem: pd.DataFrame | None = None) -> str:
    fig, ax = plt.subplots(figsize=(9, 4))
    if nuvem is not None:
        ax.scatter(nuvem["volatilidade"], nuvem["retorno"], s=4, color="#c8ced7", label="ativos isolados")
        for c, r in nuvem.iterrows():
            ax.annotate(c, (r["volatilidade"], r["retorno"]), fontsize=6, color="#6b7584", xytext=(2, 2), textcoords="offset points")
    ax.plot(fr["volatilidade"], fr["retorno"], color="#1d3b66", lw=2, label="fronteira eficiente")
    for i, (n, (vol, ret)) in enumerate(pontos.items()):
        ax.scatter([vol], [ret], s=55, color=CORES[(i + 1) % len(CORES)], zorder=5, edgecolor="white", label=n)
    ax.xaxis.set_major_formatter(mticker.PercentFormatter(1.0)); ax.yaxis.set_major_formatter(mticker.PercentFormatter(1.0))
    ax.set_xlabel("volatilidade a.a."); ax.set_ylabel("retorno a.a. (histórico, em R$)"); ax.legend(fontsize=7.5)
    return _fig(fig)


def tabela(df: pd.DataFrame, fmt: dict | None = None, indice=True) -> str:
    fmt = fmt or {}
    cab = ("<th></th>" if indice else "") + "".join(f"<th>{html.escape(str(c))}</th>" for c in df.columns)
    corpo = []
    for i, row in df.iterrows():
        cels = []
        for c, v in row.items():
            f = fmt.get(c) or fmt.get("*")
            txt = f(v) if f and isinstance(v, (int, float, np.floating, np.integer)) else html.escape(str(v))
            cels.append(f"<td>{txt}</td>")
        corpo.append("<tr>" + (f'<td class="t">{html.escape(str(i))}</td>' if indice else "") + "".join(cels) + "</tr>")
    return f'<div class="tw"><table><thead><tr>{cab}</tr></thead><tbody>{"".join(corpo)}</tbody></table></div>'


GLOSSARIO = """<ul>
<li><b>a.a.</b>: ao ano. <b>p.p.</b>: pontos percentuais.</li>
<li><b>CDI</b> (Certificado de Depósito Interbancário): taxa entre bancos, referência da renda fixa. <b>IPCA</b> (Índice Nacional de Preços ao Consumidor Amplo): a inflação oficial. <b>IR</b>: Imposto de Renda.</li>
<li><b>B3</b>: a bolsa brasileira. <b>CVM</b> (Comissão de Valores Mobiliários): o regulador. <b>Ibovespa</b>: principal índice de ações da B3. <b>IFIX</b>: índice de fundos imobiliários. <b>S&amp;P 500</b>: 500 maiores empresas dos EUA.</li>
<li><b>FII</b> (Fundo de Investimento Imobiliário): <i>de papel</i> investe em CRI (Certificados de Recebíveis Imobiliários); <i>de tijolo</i>, em imóveis. <b>Fiagro</b>: fundo do agronegócio. <b>FI-Infra</b>: fundo de debêntures de infraestrutura. <b>ETF</b> (Exchange Traded Fund): fundo de índice negociado em bolsa.</li>
<li><b>Percentil</b> (P5, P10, P50, P90...): ordenando os cenários, P10 é o valor que só 10% ficam abaixo; P50 é a mediana.</li>
<li><b>Volatilidade</b>: quanto oscila ao ano. <b>Queda máxima</b> (drawdown): maior perda do topo ao fundo. <b>Sharpe</b>: retorno acima do CDI por unidade de volatilidade. <b>Sortino</b>: como o Sharpe, só com as quedas.</li>
<li><b>VaR 95%</b> (Value at Risk, valor em risco): perda mensal superada só em 5% dos meses. <b>CVaR</b> (VaR condicional): perda média nesses piores meses. <b>Beta</b>: sensibilidade ao Ibovespa.</li>
<li><b>Monte Carlo</b>: simular muitos futuros possíveis por sorteio. <b>Bootstrap</b>: sorteio de meses reais do passado. <b>GARCH</b> (heterocedasticidade condicional autorregressiva generalizada): volatilidade que muda com o tempo. <b>Mudança de regime</b>: alternância entre mercado calmo e em crise.</li>
<li><b>Fronteira eficiente</b>: maior retorno para cada nível de risco. <b>Paridade de risco</b>: cada ativo contribui igual para o risco. <b>James-Stein / Ledoit-Wolf</b>: encolhimento de médias e covariâncias para reduzir erro de estimação. <b>Reamostragem (Michaud)</b>: média de otimizações em versões sorteadas do histórico. <b>Fora da amostra</b>: teste num período não usado para escolher a carteira.</li>
</ul>"""

CSS = """
:root{--bg:#eef0f3;--panel:#fff;--panel2:#f6f7f9;--ink:#0e1726;--ink2:#3b4656;--muted:#6b7584;--rule:#e1e4e9;--accent:#1d3b66;--pos:#16794a;--neg:#b42318}
@media (prefers-color-scheme:dark){:root:not([data-theme=light]){--bg:#0a0e13;--panel:#11171f;--panel2:#161e28;--ink:#e6eaf0;--ink2:#b6c0cc;--muted:#7d8896;--rule:#212a35;--accent:#8db4e8;color-scheme:dark}}
:root[data-theme=dark]{--bg:#0a0e13;--panel:#11171f;--panel2:#161e28;--ink:#e6eaf0;--ink2:#b6c0cc;--muted:#7d8896;--rule:#212a35;--accent:#8db4e8;color-scheme:dark}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:14px/1.5 "IBM Plex Sans",system-ui,sans-serif}
.w{max-width:1100px;margin:0 auto;padding-block:20px 40px;padding-left:max(16px,env(safe-area-inset-left,0px));padding-right:max(16px,env(safe-area-inset-right,0px));display:flex;flex-direction:column;gap:16px}
h1{margin:0;font-size:22px;letter-spacing:-.01em;text-wrap:balance}h2{margin:0;font-size:11.5px;text-transform:uppercase;letter-spacing:.08em;color:var(--ink2)}
.sub{color:var(--muted);margin:2px 0 0;font-size:13px}.p{background:var(--panel);border:1px solid var(--rule);border-radius:4px;overflow:hidden}
.ph{padding:10px 14px;border-bottom:1px solid var(--rule);display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap}.pb{padding:14px;display:flex;flex-direction:column;gap:10px;min-width:0}
.pb p,.pb li{margin:0;color:var(--ink2);max-width:75ch}.pb ul{margin:0;padding-left:18px;display:flex;flex-direction:column;gap:4px}
img{max-width:100%;height:auto;display:block;background:#fff;border-radius:3px}
.tw{overflow-x:auto}table{border-collapse:collapse;width:100%;font:12.5px/1.4 "IBM Plex Mono",ui-monospace,monospace;font-variant-numeric:tabular-nums}
th,td{padding:6px 10px;text-align:right;border-bottom:1px solid var(--rule);white-space:nowrap}th{font:600 10.5px "IBM Plex Sans",sans-serif;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);background:var(--panel2)}
td.t,th:first-child{text-align:left;font-family:"IBM Plex Sans",sans-serif}.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:1px;background:var(--rule)}
.k{background:var(--panel);padding:12px 14px}.k span{display:block;font-size:10.5px;text-transform:uppercase;letter-spacing:.07em;color:var(--muted);font-weight:600}.k b{font:600 19px "IBM Plex Mono",monospace}
.k small{display:block;color:var(--muted);font-size:11.5px}.aviso{border-left:3px solid #9a5b06;padding:8px 12px;background:var(--panel2);color:var(--ink2)}
"""


def montar(titulo: str, secoes: list[tuple[str, str, str]], subtitulo: str = "") -> str:
    corpo = "".join(f'<section class="p"><div class="ph"><h2>{html.escape(t)}</h2><span class="sub">{html.escape(m)}</span></div><div class="pb">{c}</div></section>'
                    for t, m, c in secoes)
    return (f'<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
            f'<title>{html.escape(titulo)}</title><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;600&family=IBM+Plex+Mono:wght@400;600&display=swap">'
            f'<style>{CSS}</style></head><body><div class="w"><header><h1>{html.escape(titulo)}</h1><p class="sub">{html.escape(subtitulo)}</p></header>{corpo}'
            f'<p class="sub">Gerado em {dt.datetime.now():%d/%m/%Y %H:%M} pelo Simulador Martins (Python). Material educativo, não é recomendação de investimento.</p></div></body></html>')
