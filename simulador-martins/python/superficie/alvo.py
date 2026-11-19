"""Variável-alvo: o preço atinge `alvo` % antes de tocar o stop de `stop` %, nos próximos
`horizonte` minutos, entrando no fechamento do candle t.

lado: 'long' (alta), 'short' (baixa) ou 'continuacao' (a favor do movimento do dia até t: se o dia
está positivo, alvo para cima; se negativo, para baixo). y = 1 = continuação/alvo atingido.
Se alvo e stop caem no mesmo candle, conta como stop (conservador, sem dado de dentro do candle).
ret_trade: resultado bruto da operação (alvo, −stop, ou o fechamento ao fim do horizonte).
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .sessao import tempos


def rotular(df: pd.DataFrame, alvo: float, stop: float, horizonte: int, lado: str = "continuacao",
            minutos: int = 15, mesma_sessao: bool = False) -> pd.DataFrame:
    h = max(1, int(round(horizonte / minutos)))
    n = len(df)
    c = df["close"].to_numpy(); hi = df["high"].to_numpy(); lo = df["low"].to_numpy()
    ts = tempos(df.index); ses = df["sessao"].to_numpy()
    if lado == "long":
        dirc = np.ones(n)
    elif lado == "short":
        dirc = -np.ones(n)
    else:
        dirc = np.where(df["osc"].to_numpy() >= 0, 1.0, -1.0)
    y = np.zeros(n, np.int8); ret = np.full(n, np.nan); saida = np.full(n, h, np.int16)
    decidido = np.zeros(n, bool); valido = np.ones(n, bool)
    passo = np.timedelta64(minutos, "m")
    for j in range(1, h + 1):
        idx = np.arange(n) + j
        fora = idx >= n
        idx = np.minimum(idx, n - 1)
        contiguo = (ts[idx] - ts) == j * passo           # sem lacuna até o candle j
        if mesma_sessao:
            contiguo &= ses[idx] == ses
        valido &= ~fora & contiguo | decidido
        sobe = (hi[idx] / c - 1) * 100; desce = (lo[idx] / c - 1) * 100
        fav = np.where(dirc > 0, sobe, -desce); contra = np.where(dirc > 0, -desce, sobe)
        bate_stop = (contra >= stop) & ~decidido & valido
        bate_alvo = (fav >= alvo) & ~decidido & valido & ~bate_stop
        ret[bate_stop] = -stop; saida[bate_stop] = j
        ret[bate_alvo] = alvo; y[bate_alvo] = 1; saida[bate_alvo] = j
        decidido |= bate_stop | bate_alvo
        if j == h:
            resto = ~decidido & valido
            ret[resto] = dirc[resto] * (c[idx][resto] / c[resto] - 1) * 100
    out = pd.DataFrame({"y": y, "ret_trade": ret, "saida": saida, "valido": valido & ~np.isnan(ret), "direcao": dirc}, index=df.index)
    return out
