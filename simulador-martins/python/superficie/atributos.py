"""Atributos (features) ponto no tempo: tudo que entra na linha do candle t usa só informação
disponível no fechamento de t. Nenhuma normalização usa estatística do conjunto inteiro."""
from __future__ import annotations

import numpy as np
import pandas as pd
from scipy.ndimage import gaussian_filter1d

from .superficie import Grade, cubos, historica

ATRIBUTOS = [
    "osc", "osc_max", "osc_min", "pos_no_dia", "idx", "dia_semana",
    "ret_1", "ret_2", "ret_4", "ret_8", "ret_16", "vol_16", "vol_96", "vol_relativa_volat",
    "vol_rel", "vol_acum_rel", "agressao", "agressao_acum", "agressao_rel_hist",
    "poc_dist", "vwap_dist", "frac_acima", "concentracao", "anomalia_celula", "anomalia_volume", "anomalia_forma",
]


def calcular(df: pd.DataFrame, grade: Grade, T: int, janela: int = 20, sigma=(1.0, 1.5), alocacao: str = "fechamento"):
    df = df.copy()
    lc = np.log(df["close"])
    for k in (1, 2, 4, 8, 16):
        df[f"ret_{k}"] = (lc - lc.shift(k)) * 100
    r1 = lc.diff()
    df["vol_16"] = r1.rolling(16, min_periods=8).std() * 100
    df["vol_96"] = r1.rolling(96, min_periods=48).std() * 100
    df["vol_relativa_volat"] = df["vol_16"] / df["vol_96"]
    amp = (df["osc_max"] - df["osc_min"]).replace(0, np.nan)
    df["pos_no_dia"] = (df["osc"] - df["osc_min"]) / amp
    # volume do candle e acumulado no dia vs. o mesmo horário nas sessões anteriores (só passado)
    piv = df.pivot_table(index="sessao", columns="idx", values="vol_fin", aggfunc="sum")
    base = piv.rolling(janela, min_periods=3).mean().shift(1)
    acum = piv.cumsum(axis=1); base_ac = acum.rolling(janela, min_periods=3).mean().shift(1)
    chave = pd.MultiIndex.from_arrays([df["sessao"], df["idx"]])
    df["vol_rel"] = df["vol_fin"].to_numpy() / base.stack().reindex(chave).to_numpy()
    df["vol_acum_rel"] = df.groupby("sessao")["vol_fin"].cumsum().to_numpy() / base_ac.stack().reindex(chave).to_numpy()
    tem_ag = df["agressao_fin"].notna().any()
    if tem_ag:
        df["agressao"] = df["agressao_fin"] / df["vol_fin"].replace(0, np.nan)
        ag_ac = df.groupby("sessao")["agressao_fin"].cumsum(); vol_ac = df.groupby("sessao")["vol_fin"].cumsum()
        df["agressao_acum"] = ag_ac / vol_ac.replace(0, np.nan)
        pa = df.pivot_table(index="sessao", columns="idx", values="agressao_fin", aggfunc="sum").cumsum(axis=1)
        pv = acum
        razao = (pa / pv).rolling(janela, min_periods=3).mean().shift(1)
        df["agressao_rel_hist"] = df["agressao_acum"].to_numpy() - razao.stack().reindex(chave).to_numpy()
    else:
        df["agressao"] = df["agressao_acum"] = df["agressao_rel_hist"] = np.nan
    # superfície: parcial do dia (até t) e histórica (sessões anteriores)
    S, A, sessoes = cubos(df, grade, T, alocacao)
    C = np.cumsum(S, axis=1)                          # C[d, t, :] = volume do dia até o candle t, por faixa
    Hm, Hs = historica(C, janela, sigma)
    pos = {s: i for i, s in enumerate(sessoes)}
    d = df["sessao"].map(pos).to_numpy(); t = df["idx"].to_numpy(); b = grade.banda(df["osc"].to_numpy())
    P = C[d, t, :].astype(np.float64)
    if sigma and sigma[1] > 0:
        P = gaussian_filter1d(P, sigma[1], axis=1)    # suaviza só na oscilação (nunca no tempo)
    tot = P.sum(axis=1); tot_s = np.where(tot > 0, tot, np.nan)
    cen = grade.centros
    df["poc_dist"] = cen[P.argmax(axis=1)] - df["osc"].to_numpy()
    df["vwap_dist"] = (P @ cen) / tot_s - df["osc"].to_numpy()
    acima = np.cumsum(P[:, ::-1], axis=1)[:, ::-1]
    df["frac_acima"] = np.take_along_axis(np.concatenate([acima[:, 1:], np.zeros((len(P), 1))], axis=1), b[:, None], 1)[:, 0] / tot_s
    df["concentracao"] = P.max(axis=1) / tot_s
    hm = Hm[d, t, :].astype(np.float64); hs = Hs[d, t, :].astype(np.float64)
    cel = C[d, t, b]
    sh = hm.sum(axis=1)
    eps = 1e-3 * np.where(sh > 0, sh, 1.0)          # piso relativo: faixas quase sem histórico não explodem
    df["anomalia_celula"] = np.log((cel + eps) / (hm[np.arange(len(b)), b] + eps))   # log-razão vs. histórico
    df["anomalia_volume"] = tot / np.where(sh > 0, sh, np.nan) - 1
    # diferença de forma (distribuição do dia vs. histórica, distância L1 entre perfis normalizados)
    pn = P / tot_s[:, None]; hn = hm / np.where(sh > 0, sh, np.nan)[:, None]
    df["anomalia_forma"] = np.nansum(np.abs(pn - hn), axis=1) / 2
    df.loc[np.isnan(hm[:, 0]), ["anomalia_celula", "anomalia_volume", "anomalia_forma"]] = np.nan
    return df, {"S": S, "A": A, "C": C, "Hm": Hm, "Hs": Hs, "sessoes": sessoes}
