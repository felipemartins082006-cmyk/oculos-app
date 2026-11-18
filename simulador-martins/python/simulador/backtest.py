"""Backtest histórico e métricas de risco/retorno."""
from __future__ import annotations

import numpy as np
import pandas as pd


def metricas(r: pd.Series, cdi: pd.Series, bench: pd.Series | None = None) -> dict:
    r, cdi = r.dropna(), cdi.reindex(r.dropna().index)
    n = len(r)
    nivel = np.log1p(r).cumsum()
    pico = np.maximum.accumulate(np.r_[0.0, nivel.to_numpy()])[1:]
    dd = np.expm1(nivel.to_numpy() - pico)
    abaixo, maior = 0, 0
    for x in dd:
        abaixo = abaixo + 1 if x < 0 else 0
        maior = max(maior, abaixo)
    cagr = np.exp(np.log1p(r).mean() * 12) - 1
    cdi_a = np.exp(np.log1p(cdi).mean() * 12) - 1
    vol = r.std(ddof=1) * np.sqrt(12)
    down = np.sqrt(np.mean(np.minimum(r - cdi, 0) ** 2)) * np.sqrt(12)
    var95 = np.quantile(r, 0.05)
    out = {
        "retorno ao ano": cagr, "volatilidade ao ano": vol, "sharpe (retorno por risco)": (cagr - cdi_a) / vol if vol else np.nan,
        "sortino (só quedas)": (cagr - cdi_a) / down if down else np.nan, "queda máxima": dd.min(),
        "maior tempo abaixo do topo (meses)": maior, "pior mês": r.min(), "melhor mês": r.max(),
        "VaR 95% mensal (perda superada em 5% dos meses)": var95, "CVaR 95% mensal (perda média nos piores 5%)": r[r <= var95].mean(), "meses positivos": (r > 0).mean(),
        "meses": n,
    }
    if bench is not None:
        b = bench.reindex(r.index)
        if b.notna().all():
            c = np.cov(r, b)
            out["beta vs Ibovespa (sensibilidade à bolsa)"] = c[0, 1] / c[1, 1]
            out["correlação c/ Ibovespa"] = np.corrcoef(r, b)[0, 1]
    return out


def backtest(base, J, taxa: float = 0.0, benchmarks=("CDI", "IPCA", "IBOV", "SPY"), cambio: bool = True) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Retornos mensais históricos das carteiras (rebalanceamento mensal) e dos benchmarks na janela J."""
    taxa_m = (1 + taxa / 100) ** (-1 / 12)
    P = pd.DataFrame(J.P, index=J.meses, columns=J.nomes)
    P = (1 + P) * taxa_m - 1
    for b in benchmarks:
        if b in ("CDI", "IPCA"):
            s = base.retornos[b]
        else:
            s = base.retornos_brl([b], cambio)[b]
        s = s.reindex(J.meses)
        if s.notna().all():
            nome = {"CDI": "CDI", "IPCA": "IPCA", "IBOV": "Ibovespa", "SPY": "S&P 500 (SPY, em R$)"}.get(b, b)
            P[nome] = s
    ibov = base.retornos["IBOV"].reindex(J.meses)
    cdi = base.retornos["CDI"].reindex(J.meses)
    tab = pd.DataFrame({c: metricas(P[c], cdi, ibov) for c in P.columns})
    return P, tab


def por_ano(P: pd.DataFrame) -> pd.DataFrame:
    g = (1 + P).groupby(P.index.year).prod() - 1
    cont = P.groupby(P.index.year).size()
    g.index = [f"{a}{'*' if cont[a] < 12 else ''}" for a in g.index]
    return g.iloc[::-1]
