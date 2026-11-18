"""Extensão de históricos curtos por modelo de fatores (backfill).

Nenhuma base tem retornos de um ativo antes de ele existir. Para não perder a história longa
(crises de 2015-16, 2008...), o ativo é explicado por fatores com histórico longo:

    r_ativo − base = α + Σ β_k · (fator_k − base) + ε

base = CDI para ativos brasileiros (0 para ativos em dólar). Os meses anteriores ao início do ativo
recebem  base + α_enc + Σ β_k·(fator_k − base) + ε*, onde ε* é sorteado dos resíduos reais do ativo
(mantém a volatilidade própria) e α_enc é o α encolhido para zero (α · n/(n+120)), porque um α
estimado com poucos anos tende a ser sorte. Os meses estimados ficam marcados.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

FATORES = {
    "fii": ["IFIX", "IPCA"], "fiagro": ["IFIX", "IPCA"], "fiinfra": ["IFIX", "IPCA"],
    "acao": ["IBOV", "SMLL"], "fundo_acoes": ["IBOV", "SMLL"], "etf": ["IBOV", "USDBRL"],
    "fundo_rf": ["IPCA"], "fundo_mm": ["IBOV", "USDBRL", "IPCA"], "fundo_cambial": ["USDBRL"],
    "acao_us": ["SPY", "QQQ"], "etf_us": ["SPY", "TLT"],
}
FATORES_RAW = {"IPCA", "USDBRL"}  # entram como nível, não como excesso sobre o CDI


def _matriz(base, cod: str, fatores: list[str]):
    tipo = base.meta.loc[cod, "tipo"]
    usd = base.meta.loc[cod, "moeda"] == "USD"
    R = base.retornos
    y = R[cod]
    ref = pd.Series(0.0, index=R.index) if usd else R["CDI"]
    F = pd.DataFrame({f: (R[f] if f in FATORES_RAW else R[f] - ref) for f in fatores})
    return y, ref, F, tipo


def ajustar(base, cod: str, fatores: list[str] | None = None, meses: slice | None = None):
    tipo = base.meta.loc[cod, "tipo"]
    fatores = fatores or FATORES.get(tipo)
    if not fatores:
        return None
    y, ref, F, _ = _matriz(base, cod, fatores)
    dados = pd.concat([y - ref, F], axis=1).dropna()
    if meses is not None:
        dados = dados.loc[meses]
    if len(dados) < 24:
        return None
    Y = dados.iloc[:, 0].to_numpy(); X = np.column_stack([np.ones(len(dados)), dados.iloc[:, 1:].to_numpy()])
    coef, *_ = np.linalg.lstsq(X, Y, rcond=None)
    res = Y - X @ coef
    r2 = 1 - res.var() / Y.var() if Y.var() > 0 else 0.0
    return {"fatores": fatores, "alfa": coef[0], "betas": coef[1:], "residuos": res, "n": len(dados), "r2": r2}


def estender(base, cod: str, semente: int = 7, ate: pd.Period | None = None, modelo: dict | None = None) -> tuple[pd.Series, int]:
    """Série de retornos com os meses anteriores ao início preenchidos pelo modelo. Devolve (série, nº de meses estimados)."""
    m = modelo or ajustar(base, cod)
    y = base.retornos[cod]
    if m is None:
        return y, 0
    _, ref, F, _ = _matriz(base, cod, m["fatores"])
    inicio = y.first_valid_index()
    validos = F.dropna().index
    alvo = validos[validos < inicio]
    if ate is not None:
        alvo = alvo[alvo >= ate]
    if len(alvo) == 0:
        return y, 0
    rng = np.random.default_rng(semente + sum(map(ord, cod)))
    alfa = m["alfa"] * m["n"] / (m["n"] + 120)
    eps = rng.choice(m["residuos"], size=len(alvo), replace=True)
    est = ref.loc[alvo].to_numpy() + alfa + F.loc[alvo].to_numpy() @ m["betas"] + eps
    s = y.copy()
    s.loc[alvo] = np.maximum(est, -0.9)
    return s, len(alvo)


def validar(base, codigos: list[str], meses_ajuste: int = 60) -> pd.DataFrame:
    """Teste fora da amostra: ajusta só nos últimos `meses_ajuste` meses, reconstrói os anteriores e compara com o real."""
    linhas = []
    for cod in codigos:
        y = base.retornos[cod].dropna()
        if len(y) < meses_ajuste + 36:
            continue
        corte = y.index[-meses_ajuste]
        m = ajustar(base, cod, meses=slice(corte, None))
        if m is None:
            continue
        _, ref, F, _ = _matriz(base, cod, m["fatores"])
        antes = y.index[y.index < corte]
        antes = antes[F.loc[antes].notna().all(axis=1)]
        if len(antes) < 24:
            continue
        sem_ruido = ref.loc[antes].to_numpy() + m["alfa"] * m["n"] / (m["n"] + 120) + F.loc[antes].to_numpy() @ m["betas"]
        real = y.loc[antes].to_numpy()
        anual = lambda r: np.exp(np.log1p(r).mean() * 12) - 1
        vol_est = np.sqrt(sem_ruido.var() + m["residuos"].var()) * np.sqrt(12)
        linhas.append({
            "ativo": cod, "meses_testados": len(antes), "r2_ajuste": m["r2"],
            "ret_real": anual(real), "ret_estimado": anual(sem_ruido), "erro_ret": anual(sem_ruido) - anual(real),
            "vol_real": real.std() * np.sqrt(12), "vol_estimada": vol_est,
            "correlacao": np.corrcoef(sem_ruido, real)[0, 1],
        })
    return pd.DataFrame(linhas).set_index("ativo") if linhas else pd.DataFrame()
