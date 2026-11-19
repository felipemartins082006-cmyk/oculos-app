"""Figuras Plotly: superfícies 3D (auditoria visual) e gráficos do modelo."""
from __future__ import annotations

import numpy as np
import pandas as pd
import plotly.graph_objects as go

from .superficie import superficie_media  # reexportado para o relatório

ESCALA = [[0, "#0d1b2a"], [0.2, "#1b3a5c"], [0.45, "#2f6690"], [0.7, "#7fb2d8"], [0.88, "#e0b04c"], [1, "#f6e3a1"]]
DIVERGENTE = [[0, "#2166ac"], [0.5, "#f2f2f2"], [1, "#b2182b"]]


def _horas(T, minutos=15, h0=0):
    return [f"{(h0 * 60 + i * minutos) // 60 % 24:02d}:{(i * minutos) % 60:02d}" for i in range(T)]


def _cena(xt, yt, zt):
    eixo = dict(showbackground=False, gridcolor="rgba(128,128,128,.25)", zerolinecolor="rgba(128,128,128,.4)")
    return dict(xaxis=dict(title=xt, **eixo), yaxis=dict(title=yt, **eixo), zaxis=dict(title=zt, **eixo),
                aspectmode="manual", aspectratio=dict(x=1.6, y=1.0, z=0.7), camera=dict(eye=dict(x=-1.6, y=-1.5, z=0.9)))


def _layout(fig, altura=560):
    fig.update_layout(height=altura, margin=dict(l=0, r=0, t=30, b=0), paper_bgcolor="rgba(0,0,0,0)", plot_bgcolor="rgba(0,0,0,0)",
                      font=dict(family="IBM Plex Sans, system-ui, sans-serif", size=12))
    return fig


def superficie(M: np.ndarray, grade, h0=0, titulo="", z="Volume financeiro médio", escala=ESCALA, minutos=15, centro=None):
    """M: (T × faixas). Eixo X = horário (candles de 15 min), Y = oscilação % desde a abertura, Z = valor."""
    T = M.shape[0]
    hrs = _horas(T, minutos, h0)
    fig = go.Figure(go.Surface(x=np.arange(T), y=grade.centros, z=M.T, colorscale=escala, cmid=centro,
                               colorbar=dict(title=dict(text=z, side="right"), thickness=12, len=0.7),
                               hovertemplate="horário %{customdata}<br>oscilação %{y:.2f}%<br>" + z + " %{z:,.3s}<extra></extra>",
                               customdata=np.tile(np.array(hrs)[None, :], (len(grade.centros), 1)),
                               contours=dict(z=dict(show=True, usecolormap=True, project_z=True, highlightcolor="#fff"))))
    passo = max(1, T // 8)
    cena = _cena("horário (candles de 15 min)", "oscilação desde a abertura (%)", z)
    cena["xaxis"].update(tickvals=list(range(0, T, passo)), ticktext=hrs[::passo])
    fig.update_layout(scene=cena, title=dict(text=titulo, x=0.01, font=dict(size=13)))
    return _layout(fig)


def superficie_sessao(S_d, C_d, Hm_d, df_d, grade, h0=0, titulo=""):
    """Sessão escolhida: volume por (horário, faixa) + caminho do preço por cima."""
    fig = superficie(S_d, grade, h0, titulo, "Volume financeiro")
    T = S_d.shape[0]
    zmax = float(np.nanmax(S_d)) if np.nanmax(S_d) > 0 else 1
    fig.add_trace(go.Scatter3d(x=df_d["idx"], y=df_d["osc"], z=np.full(len(df_d), zmax * 1.05), mode="lines",
                               line=dict(color="#e0b04c", width=5), name="preço (oscilação %)",
                               hovertemplate="oscilação %{y:.2f}%<extra>preço</extra>"))
    return fig


def anomalia(C_d, Hm_d, grade, h0=0, titulo=""):
    """Diferença entre o volume acumulado do dia e o histórico das sessões anteriores (mesmo horário/faixa)."""
    D = (np.nan_to_num(C_d) - np.nan_to_num(Hm_d))
    lim = float(np.nanpercentile(np.abs(D), 99)) or 1
    return superficie(np.clip(D, -lim, lim), grade, h0, titulo, "Volume acumulado − histórico", DIVERGENTE, centro=0)


def mapa_calor(M, grade, h0=0, titulo="", z="Volume financeiro médio"):
    T = M.shape[0]; hrs = _horas(T, 15, h0)
    fig = go.Figure(go.Heatmap(x=hrs, y=grade.centros, z=M.T, colorscale=ESCALA, colorbar=dict(title=dict(text=z, side="right"), thickness=12),
                               hovertemplate="%{x} · %{y:.2f}%<br>%{z:,.3s}<extra></extra>"))
    fig.update_layout(title=dict(text=titulo, x=0.01, font=dict(size=13)), xaxis_title="horário", yaxis_title="oscilação (%)")
    return _layout(fig, 380)


def matriz(cm, titulo="Matriz de confusão (teste)"):
    cm = np.array(cm); tot = cm.sum() or 1
    txt = [[f"{cm[i, j]:,}<br>{cm[i, j] / tot:.1%}" for j in range(2)] for i in range(2)]
    fig = go.Figure(go.Heatmap(z=cm, x=["previsto: não", "previsto: sim"], y=["real: não", "real: sim"], text=txt, texttemplate="%{text}",
                               colorscale=[[0, "#e8eef5"], [1, "#2f6690"]], showscale=False, hoverinfo="skip"))
    fig.update_layout(title=dict(text=titulo, x=0.01, font=dict(size=13)), yaxis=dict(autorange="reversed"))
    return _layout(fig, 320)


def importancias(s: pd.Series, n=15):
    s = s.head(n)[::-1]
    fig = go.Figure(go.Bar(x=s.values, y=s.index, orientation="h", marker_color="#2f6690", hovertemplate="%{y}: %{x:.1%}<extra></extra>"))
    fig.update_layout(title=dict(text="Importância dos atributos (ganho)", x=0.01, font=dict(size=13)), xaxis_tickformat=".0%")
    return _layout(fig, 420)


def curvas(series: dict, titulo="Curva de capital (uma posição por vez, líquida de custos)"):
    fig = go.Figure()
    for nome, (c, cor) in series.items():
        if len(c):
            fig.add_trace(go.Scatter(x=c.index, y=(c - 1) * 100, name=nome, line=dict(color=cor, width=2), hovertemplate="%{x|%d/%m/%Y %H:%M}<br>%{y:.2f}%<extra>" + nome + "</extra>"))
    fig.update_layout(title=dict(text=titulo, x=0.01, font=dict(size=13)), yaxis_title="resultado acumulado (%)", hovermode="x unified")
    return _layout(fig, 360)


def calibracao(y, p, n=10):
    q = pd.qcut(p, n, duplicates="drop")
    g = pd.DataFrame({"y": y, "p": p, "q": q}).groupby("q", observed=True).mean()
    fig = go.Figure([go.Scatter(x=[0, g["p"].max()], y=[0, g["p"].max()], mode="lines", line=dict(dash="dash", color="#888"), name="ideal"),
                     go.Scatter(x=g["p"], y=g["y"], mode="lines+markers", line=dict(color="#2f6690", width=2), name="modelo",
                                hovertemplate="prevista %{x:.1%} · realizada %{y:.1%}<extra></extra>")])
    fig.update_layout(title=dict(text="Calibração (teste): probabilidade prevista × realizada", x=0.01, font=dict(size=13)),
                      xaxis_tickformat=".0%", yaxis_tickformat=".0%", xaxis_title="prevista", yaxis_title="realizada")
    return _layout(fig, 340)
