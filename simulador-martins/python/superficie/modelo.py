"""Modelos de árvore (LightGBM / XGBoost), escolha do limiar na validação, métricas e simulação."""
from __future__ import annotations

import warnings

import numpy as np
import pandas as pd
from sklearn.metrics import (brier_score_loss, confusion_matrix, f1_score, precision_score, recall_score,
                             roc_auc_score)

from .sessao import tempos

warnings.filterwarnings("ignore", category=UserWarning)
warnings.filterwarnings("ignore", message=".*eval_set.*")


def treinar(Xtr, ytr, Xva, yva, tipo: str = "lightgbm", params: dict | None = None, semente: int = 7):
    params = dict(params or {})
    if tipo == "lightgbm":
        import lightgbm as lgb
        p = dict(n_estimators=2000, learning_rate=0.03, num_leaves=31, min_child_samples=100, subsample=0.8,
                 subsample_freq=1, colsample_bytree=0.8, reg_lambda=1.0, random_state=semente, verbose=-1, n_jobs=-1)
        p.update(params)
        m = lgb.LGBMClassifier(**p)
        m.fit(Xtr, ytr, eval_set=[(Xva, yva)], eval_metric="binary_logloss", callbacks=[lgb.early_stopping(100, verbose=False)])
        return m
    if tipo == "xgboost":
        import xgboost as xgb
        p = dict(n_estimators=2000, learning_rate=0.03, max_depth=5, min_child_weight=20, subsample=0.8, colsample_bytree=0.8,
                 reg_lambda=1.0, random_state=semente, n_jobs=-1, eval_metric="logloss", early_stopping_rounds=100, tree_method="hist")
        p.update(params)
        m = xgb.XGBClassifier(**p)
        m.fit(Xtr, ytr, eval_set=[(Xva, yva)], verbose=False)
        return m
    raise ValueError(f"modelo desconhecido: {tipo}")


def importancias(m, nomes) -> pd.Series:
    if hasattr(m, "booster_"):
        v = m.booster_.feature_importance(importance_type="gain")
    else:
        g = m.get_booster().get_score(importance_type="total_gain"); v = [g.get(n, g.get(f"f{i}", 0.0)) for i, n in enumerate(nomes)]
    s = pd.Series(v, index=nomes, dtype=float)
    return (s / s.sum()).sort_values(ascending=False) if s.sum() > 0 else s


def simular(p: np.ndarray, rot: pd.DataFrame, limiar: float, minutos: int = 15, custo: float = 0.0, dias_ano: int = 365) -> dict:
    """Uma posição por vez: entra no fechamento do candle quando p ≥ limiar, sai no alvo, no stop
    ou no fim do horizonte. Retornos em %; custo = ida e volta em %."""
    ts = tempos(rot.index); ret = rot["ret_trade"].to_numpy(); sai = rot["saida"].to_numpy(); ok = rot["valido"].to_numpy()
    livre = ts[0] - np.timedelta64(1, "m") if len(ts) else None
    trades = []
    for i in range(len(ts)):
        if ts[i] < livre or not ok[i] or p[i] < limiar:
            continue
        fim = ts[i] + np.timedelta64(int(sai[i]) * minutos, "m")
        trades.append((ts[i], fim, ret[i], rot["direcao"].iat[i]))
        livre = fim
    if not trades:
        return {"n": 0, "sharpe_bruto": np.nan, "sharpe_liquido": np.nan, "acerto": np.nan, "ret_medio": np.nan, "ret_total": 0.0, "queda_max": 0.0, "curva": pd.Series(dtype=float), "trades": pd.DataFrame()}
    tr = pd.DataFrame(trades, columns=["entrada", "saida", "ret_bruto", "direcao"]); tr["ret_liq"] = tr["ret_bruto"] - custo
    dias = pd.date_range(pd.Timestamp(ts[0]).normalize(), pd.Timestamp(ts[-1]).normalize(), freq="D")
    def sharpe(col):
        d = tr.groupby(pd.to_datetime(tr["saida"]).dt.normalize())[col].sum().reindex(dias, fill_value=0.0) / 100
        return float(d.mean() / d.std() * np.sqrt(dias_ano)) if d.std() > 0 else np.nan
    curva = (1 + tr.set_index("saida")["ret_liq"] / 100).cumprod()
    pico = curva.cummax()
    return {"n": len(tr), "sharpe_bruto": sharpe("ret_bruto"), "sharpe_liquido": sharpe("ret_liq"), "acerto": float((tr["ret_bruto"] > 0).mean()),
            "ret_medio": float(tr["ret_bruto"].mean()), "ret_total": float(curva.iloc[-1] - 1) * 100, "queda_max": float((curva / pico - 1).min()) * 100,
            "curva": curva, "trades": tr}


def escolher_limiar(p, rot, minutos=15, custo=0.0, dias_ano=365, min_trades=30, criterio="sharpe_bruto"):
    """Limiar de probabilidade escolhido SÓ na validação (maior Sharpe com pelo menos min_trades)."""
    cands = np.unique(np.quantile(p, np.linspace(0.5, 0.995, 60)))
    melhor = (None, -np.inf, None)
    for l in cands:
        r = simular(p, rot, l, minutos, custo, dias_ano)
        v = r[criterio]
        if r["n"] >= min_trades and np.isfinite(v) and v > melhor[1]:
            melhor = (float(l), v, r)
    if melhor[0] is None:   # poucos sinais: usa o quantil 90%
        l = float(np.quantile(p, 0.9)); melhor = (l, np.nan, simular(p, rot, l, minutos, custo, dias_ano))
    return melhor[0], melhor[2]


def classificacao(y, p, limiar) -> dict:
    yh = (p >= limiar).astype(int)
    cm = confusion_matrix(y, yh, labels=[0, 1])
    out = {"matriz": cm.tolist(), "precisao": float(precision_score(y, yh, zero_division=0)), "recall": float(recall_score(y, yh, zero_division=0)),
           "f1": float(f1_score(y, yh, zero_division=0)), "taxa_base": float(np.mean(y)), "brier": float(brier_score_loss(y, p)),
           "sinais": int(yh.sum()), "n": int(len(y))}
    try:
        out["auc"] = float(roc_auc_score(y, p))
    except ValueError:
        out["auc"] = np.nan
    return out
