"""Testes do pipeline de superfícies: ausência de vazamento, alvo e divisão temporal.
Usa uma série sintética (passeio aleatório) só como fixture de teste: nada disso vai para o produto."""
import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from superficie import alvo, atributos, divisao, sessao  # noqa: E402
from superficie.superficie import Grade  # noqa: E402


def _serie(dias=60, semente=1):
    rng = np.random.default_rng(semente)
    idx = pd.date_range("2025-01-01", periods=dias * 96, freq="15min", tz="UTC")
    r = rng.normal(0, 0.002, len(idx))
    c = 100 * np.exp(np.cumsum(r)); o = np.r_[100, c[:-1]]
    hi = np.maximum(o, c) * (1 + rng.uniform(0, 0.002, len(idx))); lo = np.minimum(o, c) * (1 - rng.uniform(0, 0.002, len(idx)))
    v = rng.uniform(1, 3, len(idx)) * 1e6; ag = v * rng.uniform(-0.3, 0.3, len(idx))
    return pd.DataFrame({"open": o, "high": hi, "low": lo, "close": c, "volume": v / c, "vol_fin": v, "negocios": 1, "agressao_fin": ag}, index=idx)


def _feats(df):
    d = sessao.marcar(df, "binance")
    X, _ = atributos.calcular(d, Grade(), sessao.n_slots(d))
    return X


def test_sem_vazamento_do_futuro():
    df = _serie()
    corte = df.index[int(len(df) * 0.6)]
    alt = df.copy()
    fut = alt.index > corte
    rng = np.random.default_rng(9)
    for c in ("open", "high", "low", "close"):
        alt.loc[fut, c] *= 1 + rng.normal(0, 0.05, fut.sum())
    alt.loc[fut, "high"] = alt.loc[fut, ["open", "high", "low", "close"]].max(axis=1)
    alt.loc[fut, "low"] = alt.loc[fut, ["open", "high", "low", "close"]].min(axis=1)
    alt.loc[fut, ["vol_fin", "agressao_fin"]] *= 7
    A = _feats(df); B = _feats(alt)
    passado = A.index <= corte
    for f in atributos.ATRIBUTOS:
        a, b = A.loc[passado, f].to_numpy(float), B.loc[passado, f].to_numpy(float)
        assert np.allclose(a, b, equal_nan=True, rtol=1e-6, atol=1e-9), f"vazamento em {f}"


def test_alvo_alvo_e_stop():
    idx = pd.date_range("2025-01-01", periods=6, freq="15min", tz="UTC")
    df = pd.DataFrame({"open": 100.0, "close": [100, 100, 100, 100, 100, 100.0], "high": [100, 100.2, 100.6, 100, 100, 100.0],
                       "low": [100, 99.9, 99.9, 99.5, 100, 100.0], "volume": 1.0, "vol_fin": 1.0, "negocios": 1, "agressao_fin": 0.0}, index=idx)
    d = sessao.marcar(df, "binance")
    r = alvo.rotular(d, 0.5, 0.3, 15, "long")
    assert r["y"].iloc[0] == 0 and r["ret_trade"].iloc[0] == 0          # 1 candle (+0,2% no máximo): fim do horizonte
    r2 = alvo.rotular(d, 0.5, 0.3, 30, "long")
    assert r2["y"].iloc[0] == 1 and r2["ret_trade"].iloc[0] == 0.5      # 2º candle chega a +0,6% sem tocar −0,3%
    r3 = alvo.rotular(d, 0.5, 0.3, 30, "short")
    assert r3["y"].iloc[1] == 0 and r3["ret_trade"].iloc[1] == -0.3     # vendido: o candle seguinte sobe 0,6% → stop


def test_divisao_sem_sobreposicao():
    df = _serie(40)
    d = sessao.marcar(df, "binance")
    tr, va, te = divisao.temporal(d, h=2)
    ts = d.index
    assert ts[tr].max() < ts[va].min() < ts[va].max() < ts[te].min()
    assert (ts[va].min() - ts[tr].max()) >= pd.Timedelta(minutes=30)     # embargo do horizonte
    for k, a, b, c in divisao.walk_forward(d, 3, h=2):
        assert ts[a].max() < ts[b].min() and ts[b].max() < ts[c].min()


if __name__ == "__main__":
    for nome, f in list(globals().items()):
        if nome.startswith("test_"):
            f(); print("ok", nome)
