"""Divisão temporal sem embaralhar, com embargo: as linhas cujo resultado (horizonte do alvo)
cruzaria para o bloco seguinte saem do bloco anterior."""
from __future__ import annotations

import numpy as np
import pandas as pd

from .sessao import tempos


def _embargo(mask: np.ndarray, ts: np.ndarray, limite, h: int, minutos: int) -> np.ndarray:
    if limite is None:
        return mask
    return mask & (ts + np.timedelta64(h * minutos, "m") < limite)


def temporal(df: pd.DataFrame, fracs=(0.70, 0.15, 0.15), h: int = 2, minutos: int = 15):
    ses = np.sort(df["sessao"].unique()); D = len(ses)
    c1 = ses[int(round(D * fracs[0]))]; c2 = ses[int(round(D * (fracs[0] + fracs[1])))]
    s = df["sessao"].to_numpy(); ts = tempos(df.index)
    tr = s < c1; va = (s >= c1) & (s < c2); te = s >= c2
    ini_va = ts[va].min() if va.any() else None; ini_te = ts[te].min() if te.any() else None
    tr = _embargo(tr, ts, ini_va, h, minutos); va = _embargo(va, ts, ini_te, h, minutos)
    return tr, va, te


def walk_forward(df: pd.DataFrame, folds: int = 5, inicial: float = 0.5, frac_val: float = 0.15, h: int = 2, minutos: int = 15):
    """Janela crescente: treina no passado, testa no bloco seguinte. Dentro do treino, o fim (frac_val)
    serve de validação (parada antecipada e escolha do limiar)."""
    ses = np.sort(df["sessao"].unique()); D = len(ses)
    cortes = np.linspace(int(D * inicial), D, folds + 1).astype(int)
    s = df["sessao"].to_numpy(); ts = tempos(df.index)
    for k in range(folds):
        a, b = cortes[k], cortes[k + 1]
        if b <= a:
            continue
        cv = ses[int(a * (1 - frac_val))]
        tr = s < cv; va = (s >= cv) & (s < ses[a]); te = (s >= ses[a]) & (s < (ses[b] if b < D else np.datetime64("2262-01-01")))
        ini_va = ts[va].min() if va.any() else None; ini_te = ts[te].min() if te.any() else None
        yield k, _embargo(tr, ts, ini_va, h, minutos), _embargo(va, ts, ini_te, h, minutos), te
