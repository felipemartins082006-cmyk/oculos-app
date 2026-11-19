"""Sessões e posição de cada candle no dia.

Cripto (24 h): sessão = dia em UTC começando na hora escolhida (padrão 00:00 UTC).
B3: sessão = dia no horário de Brasília, contando a partir das 09:00.
O preço de abertura da sessão é o open do primeiro candle dela: conhecido desde o início do dia.
"""
from __future__ import annotations

import numpy as np
import pandas as pd


def marcar(df: pd.DataFrame, fonte: str, hora_inicio: int | None = None, minutos: int = 15) -> pd.DataFrame:
    df = df.copy()
    if fonte == "binance":
        tz, h0 = "UTC", 0 if hora_inicio is None else hora_inicio
    else:
        tz, h0 = "America/Sao_Paulo", 9 if hora_inicio is None else hora_inicio
    local = df.index.tz_convert(tz)
    desloc = local - pd.Timedelta(hours=h0)
    df["sessao"] = desloc.normalize().tz_localize(None)
    df["idx"] = ((desloc.hour * 60 + desloc.minute) // minutos).astype(int)
    df["dia_semana"] = df["sessao"].dt.dayofweek
    abert = df.groupby("sessao")["open"].transform("first")
    df["abertura_sessao"] = abert
    df["osc"] = (df["close"] / abert - 1) * 100            # eixo Y: oscilação % desde a abertura
    df["osc_max"] = (df.groupby("sessao")["high"].cummax() / abert - 1) * 100
    df["osc_min"] = (df.groupby("sessao")["low"].cummin() / abert - 1) * 100
    return df


def n_slots(df: pd.DataFrame) -> int:
    return int(df["idx"].max()) + 1


def tempos(idx) -> np.ndarray:
    """Índice de tempo como datetime64 em UTC (sem fuso), para contas rápidas e sem ambiguidade."""
    idx = pd.DatetimeIndex(idx)
    if idx.tz is not None:
        idx = idx.tz_convert("UTC").tz_localize(None)
    return idx.to_numpy(dtype="datetime64[ns]")
