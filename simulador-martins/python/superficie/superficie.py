"""Superfície 3D de mercado: X = tempo da sessão (candles), Y = faixa de oscilação %, Z = volume financeiro.

Para cada sessão d, candle t e faixa b: S[d, t, b] = volume financeiro negociado no candle t com o
preço na faixa b. Alocação 'fechamento' (padrão): o volume do candle vai para a faixa do preço de
fechamento (sem supor como ele se distribuiu dentro do candle). Alocação 'faixa': dividido igualmente
entre as faixas que o candle percorreu (aproximação de perfil de volume, só se pedida).
A[d, t, b] = saldo líquido de agressão no mesmo formato (quando a fonte informa).

Ponto no tempo (sem vazamento):
- superfície parcial do dia até o candle t: C[d, t, :] = soma de S[d, 0..t, :] (só o passado do dia);
- superfície histórica de referência da sessão d: média e desvio de C nas N sessões ANTERIORES a d;
- suavização gaussiana: na histórica, em tempo e oscilação (só usa sessões passadas, completas); na
  parcial do dia, só no eixo da oscilação (suavizar no tempo puxaria candles futuros do mesmo dia).
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy.ndimage import gaussian_filter, gaussian_filter1d


@dataclass
class Grade:
    faixa: float = 5.0     # ± % em torno da abertura
    passo: float = 0.1     # largura de cada faixa, em %

    @property
    def n(self) -> int:
        return int(round(2 * self.faixa / self.passo))

    def banda(self, osc: np.ndarray) -> np.ndarray:
        return np.clip(np.floor((np.asarray(osc) + self.faixa) / self.passo).astype(int), 0, self.n - 1)

    @property
    def centros(self) -> np.ndarray:
        return -self.faixa + self.passo * (np.arange(self.n) + 0.5)


def cubos(df: pd.DataFrame, grade: Grade, T: int, alocacao: str = "fechamento"):
    """S (volume) e A (agressão): arrays (sessões × T × faixas) e a lista de sessões."""
    sessoes = np.sort(df["sessao"].unique())
    pos = {s: i for i, s in enumerate(sessoes)}
    d = df["sessao"].map(pos).to_numpy(); t = df["idx"].to_numpy()
    D, B = len(sessoes), grade.n
    S = np.zeros((D, T, B), np.float32); A = np.zeros((D, T, B), np.float32)
    v = df["vol_fin"].to_numpy(np.float64); ag = np.nan_to_num(df["agressao_fin"].to_numpy(np.float64))
    if alocacao == "fechamento":
        b = grade.banda(df["osc"].to_numpy())
        np.add.at(S, (d, t, b), v); np.add.at(A, (d, t, b), ag)
    else:
        abert = df["abertura_sessao"].to_numpy()
        b0 = grade.banda((df["low"].to_numpy() / abert - 1) * 100); b1 = grade.banda((df["high"].to_numpy() / abert - 1) * 100)
        for i in range(len(df)):
            k = b1[i] - b0[i] + 1
            S[d[i], t[i], b0[i]:b1[i] + 1] += v[i] / k; A[d[i], t[i], b0[i]:b1[i] + 1] += ag[i] / k
    return S, A, sessoes


def historica(C: np.ndarray, janela: int = 20, sigma: tuple[float, float] = (1.0, 1.5)):
    """Média e desvio de C nas `janela` sessões anteriores a cada sessão (a própria sessão fica fora).
    Sessões sem `janela` anteriores usam as que houver (mín. 3); antes disso, NaN."""
    D = C.shape[0]
    c64 = C.astype(np.float64)
    cs = np.concatenate([np.zeros((1,) + C.shape[1:]), np.cumsum(c64, axis=0)])
    cq = np.concatenate([np.zeros((1,) + C.shape[1:]), np.cumsum(c64 ** 2, axis=0)])
    media = np.full(C.shape, np.nan, np.float32); desvio = np.full(C.shape, np.nan, np.float32)
    for d in range(D):
        a = max(0, d - janela); n = d - a
        if n < 3:
            continue
        m = (cs[d] - cs[a]) / n; q = (cq[d] - cq[a]) / n
        media[d] = m; desvio[d] = np.sqrt(np.maximum(q - m * m, 0))
    if sigma and (sigma[0] > 0 or sigma[1] > 0):
        ok = ~np.isnan(media[:, 0, 0])
        media[ok] = gaussian_filter(media[ok], sigma=(0, sigma[0], sigma[1]))   # 0 no eixo das sessões
        desvio[ok] = gaussian_filter(desvio[ok], sigma=(0, sigma[0], sigma[1]))
    return media, desvio


def superficie_media(S: np.ndarray, ini: int = 0, fim: int | None = None, sigma=(1.0, 1.5)) -> np.ndarray:
    """Média de S num intervalo de sessões (para visualização/auditoria), suavizada."""
    M = S[ini:fim].mean(axis=0)
    return gaussian_filter(M, sigma=sigma) if sigma else M
