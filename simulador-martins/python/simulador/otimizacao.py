"""Otimização de carteiras.

Estimadores
- média: histórica ou encolhida (James-Stein para a média geral), que reduz o erro de estimação;
- covariância: amostral ou Ledoit-Wolf (encolhida para correlação constante).

Objetivos (todos long-only, soma 100%, com peso mínimo/máximo por ativo e limite de nº de ativos)
- max_sharpe, min_variancia, risco_alvo (máx. retorno com volatilidade ≤ alvo), paridade_risco,
  min_cvar (programação linear sobre os cenários históricos), max_sortino, max_utilidade.
- reamostrada (Michaud): repete a otimização em reamostras do histórico e tira a média dos pesos;
  carteiras mais estáveis e diversificadas, menos sensíveis a ruído.
- por_meta: varre a fronteira eficiente e escolhe, por Monte Carlo com o plano do usuário, a carteira
  que maximiza a chance de atingir a meta (ou o patrimônio mediano, ou minimiza a chance de o dinheiro acabar).
"""
from __future__ import annotations

import numpy as np
import pandas as pd
from scipy import optimize

ANO = 12


# ---------------------------------------------------------------- estimadores
def media_encolhida(R: np.ndarray) -> np.ndarray:
    """James-Stein: puxa as médias para a média geral, na proporção do ruído."""
    mu = R.mean(0); L, K = R.shape
    if K < 3:
        return mu
    alvo = mu.mean()
    var_media = R.var(0, ddof=1).mean() / L
    dist = ((mu - alvo) ** 2).sum()
    fator = max(0.0, 1 - (K - 2) * var_media / dist) if dist > 0 else 0.0
    return alvo + fator * (mu - alvo)


def ledoit_wolf(R: np.ndarray) -> np.ndarray:
    """Ledoit-Wolf (2004) com alvo de correlação constante."""
    X = R - R.mean(0); L, K = X.shape
    S = X.T @ X / L
    if K == 1:
        return S
    s = np.sqrt(np.maximum(np.diag(S), 1e-14))       # ativos sem variância (ex.: prefixado por modelo)
    corr = S / np.outer(s, s)
    rbar = (corr.sum() - K) / (K * (K - 1))
    F = rbar * np.outer(s, s); np.fill_diagonal(F, np.diag(S))
    Y = X ** 2
    pi_mat = Y.T @ Y / L - S ** 2
    pi = pi_mat.sum()
    theta_ii = (X ** 3).T @ X / L - np.diag(S)[:, None] * S
    theta_jj = theta_ii.T
    rho_off = (rbar / 2) * ((s[None, :] / s[:, None]) * theta_ii + (s[:, None] / s[None, :]) * theta_jj)
    np.fill_diagonal(rho_off, 0)
    rho = np.trace(pi_mat) + rho_off.sum()
    gamma = ((F - S) ** 2).sum()
    delta = max(0.0, min(1.0, (pi - rho) / gamma / L)) if gamma > 0 else 1.0
    out = delta * F + (1 - delta) * S
    return np.nan_to_num(out) + 1e-12 * np.eye(K)


def estimar(R: np.ndarray, media: str = "encolhida", cov: str = "ledoit_wolf"):
    mu = media_encolhida(R) if media == "encolhida" else R.mean(0)
    C = ledoit_wolf(R) if cov == "ledoit_wolf" else np.cov(R.T, ddof=1) + 1e-12 * np.eye(R.shape[1])
    return mu, np.atleast_2d(C)


# ---------------------------------------------------------------- utilidades
def anual(mu_m: float, var_m: float) -> float:
    """Retorno anual geométrico aproximado a partir de média e variância mensais."""
    return (1 + mu_m) ** ANO * np.exp(-ANO * var_m / (2 * (1 + mu_m) ** 2)) - 1


def estat(w, mu, C, rf_m):
    m, v = w @ mu, w @ C @ w
    vol = np.sqrt(max(v, 0) * ANO)
    ret = anual(m, v)
    return {"retorno": ret, "volatilidade": vol, "sharpe": (ret - ((1 + rf_m) ** ANO - 1)) / vol if vol > 0 else np.nan}


def _resolver_base(f, K, minimo, maximo, w0=None, restr=()):
    lims = [(minimo, maximo)] * K
    w0 = np.full(K, 1 / K) if w0 is None else w0
    cons = [{"type": "eq", "fun": lambda w: w.sum() - 1}, *restr]
    melhor = None
    for tentativa in range(3):
        ini = w0 if tentativa == 0 else np.random.default_rng(tentativa).dirichlet(np.ones(K))
        ini = np.clip(ini, minimo, maximo); ini /= ini.sum()
        r = optimize.minimize(f, ini, method="SLSQP", bounds=lims, constraints=cons, options={"maxiter": 500, "ftol": 1e-12})
        if r.success and (melhor is None or r.fun < melhor.fun):
            melhor = r
    w = (melhor or r).x
    w = np.clip(w, 0, None)
    return w / w.sum()


# ---------------------------------------------------------------- objetivos
def otimizar(R: np.ndarray, objetivo: str = "max_sharpe", rf_m: float = 0.0, minimo: float = 0.0, maximo: float = 1.0,
             vol_alvo: float | None = None, aversao: float = 4.0, media: str = "encolhida", cov: str = "ledoit_wolf",
             max_ativos: int | None = None, alfa_cvar: float = 0.05, ret_min: float | None = None, ret_max: float | None = None,
             queda_max: float | None = None, ret_base: str = "estimado") -> np.ndarray:
    """ret_min/ret_max: retorno ao ano exigido (fração; ret_min == ret_max = "igual a", aceita ±0,25 p.p.);
    ret_base: "estimado" (médias encolhidas) ou "historico" (retorno que a carteira teve na janela);
    queda_max: maior queda histórica tolerada (fração positiva, ex.: 0.25). Se impossível, fica o mais perto."""
    L, K = R.shape
    if ret_min is not None and ret_max is not None and abs(ret_min - ret_max) < 1e-9:
        ret_min, ret_max = ret_min - 0.0025, ret_max + 0.0025
    maximo = max(maximo, 1 / K + 1e-9)
    mu, C = estimar(R, media, cov)

    def resolve(idx):
        k = len(idx); mu_, C_, R_ = mu[idx], C[np.ix_(idx, idx)], R[:, idx]
        mx = max(maximo, 1 / k + 1e-9)

        def ret_w(w):
            if ret_base == "historico":
                return np.exp(np.log1p(np.maximum(R_ @ w, -0.999)).mean() * ANO) - 1
            return anual(w @ mu_, w @ C_ @ w)

        def queda_w(w):
            nivel = np.cumsum(np.log1p(np.maximum(R_ @ w, -0.999)))
            return -np.expm1(np.min(nivel - np.maximum.accumulate(np.r_[0.0, nivel])[1:]))

        extra = []
        if ret_min is not None:
            extra.append({"type": "ineq", "fun": lambda w: (ret_w(w) - ret_min) * 100})
        if ret_max is not None:
            extra.append({"type": "ineq", "fun": lambda w: (ret_max - ret_w(w)) * 100})
        if queda_max is not None:
            extra.append({"type": "ineq", "fun": lambda w: (queda_max - queda_w(w)) * 100})

        def _resolver(f, K_, mn, mx_, w0=None, restr=()):
            w = _resolver_base(f, K_, mn, mx_, w0, (*restr, *extra))
            falta = lambda x: sum(min(0.0, c["fun"](x)) ** 2 for c in extra)
            if extra and falta(w) > 1e-4:   # limites impossíveis: o mais perto possível
                livre = _resolver_base(f, K_, mn, mx_, w0, restr)
                cands = [w, livre]
                for rho in (1e2, 1e4, 1e6):
                    cands.append(_resolver_base(lambda x, r=rho: f(x) + r * falta(x), K_, mn, mx_, cands[-1], restr))
                w = min(cands, key=lambda x: (round(falta(x), 6), f(x)))
            return w

        if objetivo == "min_variancia":
            return _resolver(lambda w: w @ C_ @ w * 1e4, k, minimo, mx)
        if objetivo == "max_sharpe":
            if mu_.max() <= rf_m:   # nenhum ativo acima do CDI: não há carteira tangente; usa mínima variância
                return _resolver(lambda w: w @ C_ @ w * 1e4, k, minimo, mx)
            def f(w):
                v = w @ C_ @ w
                return -(anual(w @ mu_, v) - ((1 + rf_m) ** ANO - 1)) / np.sqrt(max(v * ANO, 1e-12))
            return _resolver(f, k, minimo, mx)
        if objetivo == "risco_alvo":
            alvo = (vol_alvo or 0.10) ** 2 / ANO
            return _resolver(lambda w: -anual(w @ mu_, w @ C_ @ w), k, minimo, mx,
                             restr=[{"type": "ineq", "fun": lambda w: alvo - w @ C_ @ w}])
        if objetivo == "max_utilidade":
            return _resolver(lambda w: -(w @ mu_ - aversao / 2 * (w @ C_ @ w)) * 1e3, k, minimo, mx)
        if objetivo == "paridade_risco":
            def f(w):
                s = C_ @ w; v = w @ s
                rc = w * s / v
                return ((rc - 1 / k) ** 2).sum() * 1e3
            return _resolver(f, k, max(minimo, 1e-4), mx)
        if objetivo == "max_sortino":
            def f(w):
                r = R_ @ w; ex = r - rf_m
                dd = np.sqrt(np.mean(np.minimum(ex, 0) ** 2)) * np.sqrt(ANO)
                return -(np.exp(np.log1p(r).mean() * ANO) - (1 + rf_m) ** ANO) / max(dd, 1e-9)
            return _resolver(f, k, minimo, mx)
        if objetivo == "min_cvar":
            # Rockafellar-Uryasev: min  VaR + 1/(αL) Σ u_t ;  u_t ≥ −r_t·w − VaR, u ≥ 0
            Lr = R_.shape[0]
            c = np.r_[np.zeros(k), 1.0, np.full(Lr, 1 / (alfa_cvar * Lr))]
            A = np.hstack([-R_, -np.ones((Lr, 1)), -np.eye(Lr)])
            b = np.zeros(Lr)
            Aeq = np.r_[np.ones(k), 0, np.zeros(Lr)][None, :]
            lims = [(minimo, mx)] * k + [(None, None)] + [(0, None)] * Lr
            res = optimize.linprog(c, A_ub=A, b_ub=b, A_eq=Aeq, b_eq=[1], bounds=lims, method="highs")
            w = np.clip(res.x[:k], 0, None)
            return w / w.sum()
        raise ValueError(f"Objetivo desconhecido: {objetivo}")

    idx = np.arange(K)
    w = resolve(idx)
    if max_ativos and (w > 1e-4).sum() > max_ativos:
        for _ in range(10):                      # poda iterativa: fica com os maiores pesos e reotimiza
            idx = idx[np.argsort(-w)[:max_ativos]] if len(w) == len(idx) else idx
            w = resolve(idx)
            if (w > 1e-4).sum() <= max_ativos:
                break
    out = np.zeros(K); out[idx] = w
    out[out < 1e-4] = 0
    return out / out.sum()


def reamostrada(R: np.ndarray, objetivo: str = "max_sharpe", n: int = 100, bloco: int = 6, semente: int = 1, **kw) -> np.ndarray:
    """Otimização reamostrada (Michaud): média dos pesos ótimos em `n` reamostras em blocos do histórico."""
    rng = np.random.default_rng(semente)
    L, K = R.shape
    soma = np.zeros(K)
    for _ in range(n):
        nb = -(-L // bloco)
        ini = rng.integers(0, L, nb)
        idx = ((ini[:, None] + np.arange(bloco)) % L).ravel()[:L]
        soma += otimizar(R[idx], objetivo, **kw)
    w = soma / n
    if kw.get("max_ativos") and (w > 1e-4).sum() > kw["max_ativos"]:
        corte = np.sort(w)[-kw["max_ativos"]]
        w = np.where(w >= corte, w, 0)
    return w / w.sum()


def fronteira(R: np.ndarray, rf_m: float = 0.0, pontos: int = 25, **kw) -> pd.DataFrame:
    """Fronteira eficiente: mínima variância para cada retorno-alvo."""
    mu, C = estimar(R, kw.get("media", "encolhida"), kw.get("cov", "ledoit_wolf"))
    K = len(mu)
    minimo, maximo = kw.get("minimo", 0.0), max(kw.get("maximo", 1.0), 1 / K + 1e-9)
    w_min = otimizar(R, "min_variancia", rf_m, minimo, maximo, media=kw.get("media", "encolhida"), cov=kw.get("cov", "ledoit_wolf"))
    m_lo = w_min @ mu
    # maior retorno possível com os limites de peso
    ordem = np.argsort(-mu); w_max = np.full(K, minimo); resta = 1 - w_max.sum()
    for i in ordem:
        add = min(maximo - w_max[i], resta); w_max[i] += add; resta -= add
    m_hi = w_max @ mu
    linhas = []
    for alvo in np.linspace(m_lo, m_hi, pontos):
        w = _resolver(lambda w: w @ C @ w * 1e4, K, minimo, maximo, w0=w_min,
                      restr=[{"type": "ineq", "fun": lambda w, a=alvo: w @ mu - a}])
        e = estat(w, mu, C, rf_m)
        linhas.append({**e, "pesos": w})
    return pd.DataFrame(linhas)


def por_meta(base, codigos: list[str], R: np.ndarray, plano, cfg, criterio: str = "prob_meta", pontos: int = 15, rf_m: float = 0.0,
             cenarios: int = 20_000, queda_max: float | None = None, p10_min: float | None = None, **kw) -> tuple[pd.DataFrame, np.ndarray]:
    """Escolhe na fronteira eficiente a carteira que melhor atende o objetivo do plano (Monte Carlo),
    respeitando o risco aceitável: queda máxima mediana ≥ queda_max (ex.: -0.20) e P10 ≥ p10_min."""
    from .monte_carlo import simular, Config
    fr = fronteira(R, rf_m, pontos, **kw)
    carts = {f"F{i:02d}": {c: w for c, w in zip(codigos, p) if w > 1e-4} for i, p in enumerate(fr["pesos"])}
    c2 = Config(**{**cfg.__dict__, "cenarios": cenarios, "caminhos_grafico": 100})
    res = simular(base, carts, plano, c2, progresso=False)
    s = res.resumo().drop(index="CDI (mesmo plano)")
    fr["P50"] = s["P50"].to_numpy(); fr["P10"] = s["P10"].to_numpy(); fr["queda_p50"] = s["queda máx. P50"].to_numpy()
    fr["prob_cdi"] = s["prob. > CDI"].to_numpy()
    if plano.meta:
        fr["prob_meta"] = s["prob. meta"].to_numpy()
    if plano.saque_ano is not None:
        fr["prob_acabar"] = s["prob. dinheiro acabar"].to_numpy()
    chave = {"prob_meta": ("prob_meta", False), "mediana": ("P50", False), "p10": ("P10", False),
             "sem_ruina": ("prob_acabar", True)}[criterio]
    if chave[0] not in fr:
        raise ValueError(f"O critério {criterio} precisa de meta/saque no plano.")
    ok = pd.Series(True, index=fr.index)
    if queda_max is not None:
        ok &= fr["queda_p50"] >= queda_max
    if p10_min is not None:
        ok &= fr["P10"] >= p10_min
    fr["aceitável"] = ok
    cand = fr[ok] if ok.any() else fr
    melhor = cand[chave[0]].idxmin() if chave[1] else cand[chave[0]].idxmax()
    fr["escolhida"] = fr.index == melhor
    return fr, fr.loc[melhor, "pesos"]


def fora_da_amostra(R: pd.DataFrame, objetivo: str, teste: int = 36, reamostragem: int = 0, outras: dict | None = None, **kw) -> pd.DataFrame:
    """Walk-forward: otimiza só com os meses anteriores aos últimos `teste` meses e mede o que a carteira
    rendeu de fato depois (sem olhar o futuro). Compara com pesos iguais no universo e com `outras` carteiras."""
    treino, prova = R.iloc[:-teste], R.iloc[-teste:]
    X = treino.to_numpy()
    w = reamostrada(X, objetivo, n=reamostragem, **kw) if reamostragem else otimizar(X, objetivo, **kw)
    carts = {"Otimizada no passado": w, "Pesos iguais no universo": np.full(R.shape[1], 1 / R.shape[1])}
    for n, c in (outras or {}).items():
        if all(k in R.columns for k in c):
            v = np.array([c.get(k, 0) for k in R.columns], float); carts[n] = v / v.sum()
    linhas = {}
    for n, ww in carts.items():
        r = prova.to_numpy() @ ww
        r_tr = X @ ww
        linhas[n] = {"retorno a.a. no treino": np.exp(np.log1p(r_tr).mean() * 12) - 1,
                     "retorno a.a. depois": np.exp(np.log1p(r).mean() * 12) - 1,
                     "volatilidade depois": r.std(ddof=1) * np.sqrt(12),
                     "queda máx. depois": float(np.min(np.exp(np.cumsum(np.log1p(r))) / np.maximum.accumulate(np.exp(np.cumsum(np.log1p(r)))) - 1))}
    out = pd.DataFrame(linhas).T
    out.attrs["treino"] = f"{treino.index[0]} a {treino.index[-1]}"; out.attrs["teste"] = f"{prova.index[0]} a {prova.index[-1]}"
    return out


# ---------------------------------------------------------------- universo
def universo(base, tipos: list[str] | None = None, mandato: str | None = None, min_meses: int = 60,
             janela_meses: int | None = None, codigos: list[str] | None = None, max_ativos_universo: int = 120,
             cambio: bool = True, estender: bool = False) -> pd.DataFrame:
    """Retornos (em reais) do universo filtrado, numa janela em que todos têm dados."""
    from . import proxy
    if codigos is None:
        m = base.buscar(tipos=tipos, mandato=mandato, min_meses=min_meses, limite=10_000)
        codigos = m.index.tolist()[:max_ativos_universo]
    R = base.retornos_brl(codigos, cambio)
    if estender:
        for c in codigos:
            if not c.startswith("RF:"):
                s, n = proxy.estender(base, c)
                if n:
                    R[c] = s if base.meta.loc[c, "moeda"] != "USD" or not cambio else (1 + s) * (1 + base.retornos["USDBRL"]) - 1
    if janela_meses:
        R = R.iloc[-janela_meses - 1:]
        R = R.loc[:, R.notna().all()]
    else:
        R = R.dropna(axis=0, how="any")
    return R.dropna()
