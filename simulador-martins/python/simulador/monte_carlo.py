"""Monte Carlo vetorizado em lotes (milhões de cenários) com plano completo.

Todas as carteiras e o CDI de referência usam exatamente os mesmos sorteios (números aleatórios
comuns), então a diferença entre elas não é sorte.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from . import modelos as M
from . import proxy

QS = [0.01, 0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95, 0.99]


@dataclass
class Plano:
    inicial: float = 10_000
    aporte: float = 1_000
    anos: int = 20
    meta: float = 0
    corrige: bool = True          # aporte corrigido pelo IPCA
    reaj: float = 0.0             # aumento real do aporte (% a.a.)
    saque_ano: float | None = None  # começa a sacar depois deste ano (aportes param)
    saque: float = 0.0            # saque mensal
    saque_corrige: bool = True
    taxa: float = 0.0             # custo extra (% a.a.)
    ir: float = 0.0               # IR sobre o ganho nominal no resgate final (%)
    real: bool = True             # resultados em reais de hoje


@dataclass
class Config:
    modelo: str = "bootstrap"     # bootstrap, blocos, estacionario, normal, t, garch, regimes
    bloco: int = 12
    cenarios: int = 100_000
    desde: int = 2010
    rebal: int = 1                # meses entre rebalanceamentos (0 = nunca)
    cambio: bool = True
    ajuste: float = 0.0           # p.p. a.a. somados ao retorno dos ativos de risco
    estender: bool = False        # estender históricos curtos por modelo de fatores
    semente: int | None = 42
    caminhos_grafico: int = 20_000
    gl: float = 5
    estados: int = 2


@dataclass
class Janela:
    codigos: list
    R: pd.DataFrame               # retornos em reais dos ativos (meses × ativos)
    W: np.ndarray                 # pesos (carteiras × ativos)
    nomes: list
    cdi: np.ndarray
    ipca: np.ndarray
    meses: pd.PeriodIndex
    limitante: str | None
    estimados: dict = field(default_factory=dict)

    @property
    def P(self) -> np.ndarray:    # retornos mensais das carteiras (rebalanceamento mensal)
        return self.R.to_numpy() @ self.W.T


def preparar(base, carteiras: dict[str, dict[str, float]], desde: int = 2010, cambio: bool = True,
             estender: bool = False, semente: int = 7) -> Janela:
    nomes = list(carteiras)
    codigos = list(dict.fromkeys(c for cart in carteiras.values() for c, p in cart.items() if p > 0))
    R = base.retornos_brl(codigos, cambio)
    estimados = {}
    if estender:
        fx = base.retornos["USDBRL"]
        for c in codigos:
            if c.startswith("RF:"):
                continue
            s, n = proxy.estender(base, c, semente)
            if n:
                estimados[c] = n
                R[c] = (1 + s) * (1 + fx) - 1 if cambio and base.meta.loc[c, "moeda"] == "USD" else s
    R = R.join(base.retornos[["CDI", "IPCA"]].add_suffix("_m"))
    precisa = codigos + ["CDI_m", "IPCA_m"]
    inicio = {c: R[c].first_valid_index() for c in precisa}
    lim = max(inicio, key=lambda c: inicio[c])
    R = R.loc[pd.Period(f"{desde}-01", "M"):, precisa].dropna()
    if len(R) < 24:
        raise ValueError(f"Só {len(R)} meses de histórico em comum (limitado por {lim}).")
    W = np.zeros((len(nomes), len(codigos)))
    for i, n in enumerate(nomes):
        tot = sum(p for p in carteiras[n].values() if p > 0)
        for c, p in carteiras[n].items():
            if p > 0:
                W[i, codigos.index(c)] += p / tot
    lim = lim.replace("_m", "") if inicio[lim] > pd.Period(f"{desde}-01", "M") else None
    return Janela(codigos, R[codigos], W, nomes, R["CDI_m"].to_numpy(), R["IPCA_m"].to_numpy(), R.index, lim, estimados)


@dataclass
class Resultado:
    nomes: list
    plano: Plano
    cfg: Config
    janela: Janela
    finais: np.ndarray            # cenários × carteiras
    finais_cdi: np.ndarray
    investido: np.ndarray
    anual: np.ndarray             # retorno anual da carteira (sem aportes) por cenário
    queda: np.ndarray             # queda máxima por cenário
    ruina: np.ndarray             # mês em que o dinheiro acabou (0 = não acabou)
    pontos: np.ndarray            # meses registrados no leque
    caminhos: np.ndarray          # cenários do gráfico × pontos × carteiras
    cdi_medio: np.ndarray
    inv_medio: np.ndarray
    segundos: float
    modelo_desc: str

    def resumo(self) -> pd.DataFrame:
        linhas = {}
        for i, n in enumerate(self.nomes):
            f = self.finais[:, i]
            d = {f"P{int(q * 100)}": np.quantile(f, q) for q in QS}
            d["média"] = f.mean()
            d["ret. a.a. P50"] = np.median(self.anual[:, i])
            d["ret. a.a. P10"] = np.quantile(self.anual[:, i], 0.10)
            d["queda máx. P50"] = np.median(self.queda[:, i])
            d["queda máx. P10"] = np.quantile(self.queda[:, i], 0.10)
            d["prob. perda"] = (f < self.investido).mean()
            d["prob. > CDI"] = (f > self.finais_cdi).mean()
            if self.plano.meta:
                d["prob. meta"] = (f >= self.plano.meta).mean()
            if self.plano.saque_ano is not None:
                d["prob. dinheiro acabar"] = (self.ruina[:, i] > 0).mean()
            # erro de Monte Carlo da mediana (intervalo de 95% por bootstrap da ordem)
            n_ = len(f); k = 1.96 * np.sqrt(n_ * 0.25)
            o = np.sort(f)
            d["IC95 P50"] = f"{o[max(0, int(n_ / 2 - k))]:,.0f} – {o[min(n_ - 1, int(n_ / 2 + k))]:,.0f}"
            linhas[n] = d
        cdi = {f"P{int(q * 100)}": np.quantile(self.finais_cdi, q) for q in QS}
        cdi["média"] = self.finais_cdi.mean()
        linhas["CDI (mesmo plano)"] = cdi
        return pd.DataFrame(linhas).T

    def percentis_tempo(self, i: int) -> pd.DataFrame:
        c = self.caminhos[:, :, i]
        return pd.DataFrame({f"P{int(q * 100)}": np.quantile(c, q, axis=0) for q in (0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95)},
                            index=self.pontos)


def simular(base, carteiras: dict[str, dict[str, float]], plano: Plano = Plano(), cfg: Config = Config(),
            janela: Janela | None = None, progresso: bool = True) -> Resultado:
    t0 = time.time()
    J = janela or preparar(base, carteiras, cfg.desde, cfg.cambio, cfg.estender)
    rng = np.random.default_rng(cfg.semente)
    C, K, L, N, S = len(J.nomes), len(J.codigos), len(J.meses), int(plano.anos * 12), int(cfg.cenarios)
    risco = np.array([not c.startswith("RF:") for c in J.codigos])
    R = J.R.to_numpy().copy()
    if cfg.ajuste:
        R[:, risco] = (1 + R[:, risco]) * (1 + cfg.ajuste / 100) ** (1 / 12) - 1
    P = R @ J.W.T                                         # L × C
    X = np.column_stack([P, J.cdi, J.ipca])               # L × (C+2)
    mod = M.criar(cfg.modelo, bloco=cfg.bloco, gl=cfg.gl, estados=cfg.estados, fixas=(C, C + 1)).ajustar(X)
    por_ativo = mod.reamostragem and cfg.rebal != 1
    taxa_m = (1 + plano.taxa / 100) ** (-1 / 12)
    m_saque = int(round(plano.saque_ano * 12)) if plano.saque_ano is not None else N + 1
    passo = max(1, -(-N // 120))
    pontos = np.unique(np.r_[np.arange(0, N + 1, passo), N])
    G = min(cfg.caminhos_grafico, S)
    D = C + 2
    lote = max(1000, min(50_000, int(1.2e7 / (N * (D if not mod.reamostragem else 1)))))

    finais = np.empty((S, C)); fin_cdi = np.empty(S); inv = np.empty(S)
    anual = np.empty((S, C)); queda = np.empty((S, C)); ruina = np.zeros((S, C), dtype=np.int32)
    cam = np.empty((G, len(pontos), C), dtype=np.float32)
    soma_cdi = np.zeros(len(pontos)); soma_inv = np.zeros(len(pontos))
    ks = [np.flatnonzero(J.W[c] > 0) for c in range(C)]

    feito = 0
    while feito < S:
        B = min(lote, S - feito)
        if mod.reamostragem:
            idx = mod.indices(B, N, rng)
        else:
            Xs = mod.gerar(B, N, rng)
        V = np.full((B, C), float(plano.inicial)); vc = np.full(B, float(plano.inicial))
        inf = np.ones(B); inv_nom = np.full(B, float(plano.inicial)); inv_real = inv_nom.copy(); saq = np.zeros(B)
        nivel = np.zeros((B, C)); topo = np.zeros((B, C)); qd = np.zeros((B, C)); cresce = np.zeros((B, C))
        morto = np.zeros((B, C), dtype=np.int32); morto_c = np.zeros(B, dtype=bool)
        if por_ativo:
            Hs = [plano.inicial * np.tile(J.W[c, ks[c]], (B, 1)) for c in range(C)]
        rec = 1
        reg = slice(feito, feito + B)
        if feito < G:
            g = min(B, G - feito); cam[feito:feito + g, 0, :] = plano.inicial
        soma_cdi[0] += plano.inicial * B; soma_inv[0] += plano.inicial * B
        for t in range(1, N + 1):
            if mod.reamostragem:
                j = idx[:, t - 1]
                rc, ri = J.cdi[j], J.ipca[j]
            else:
                x = Xs[:, t - 1, :]
                rc, ri = x[:, C], x[:, C + 1]
            inf *= 1 + ri
            retirando = t > m_saque
            ap = 0.0 if retirando else plano.aporte * (inf if plano.corrige else 1.0) * (1 + plano.reaj / 100) ** ((t - 1) // 12)
            sq = (plano.saque * (inf if plano.saque_corrige else 1.0)) if retirando else 0.0
            ap_v = np.broadcast_to(ap, (B,)); sq_v = np.broadcast_to(sq, (B,))
            inv_nom += ap_v; inv_real += ap_v / inf; saq += sq_v
            # CDI com o mesmo plano
            vc = np.where(morto_c, 0.0, vc * (1 + rc) + ap_v)
            if retirando:
                acabou = (vc < sq_v) & ~morto_c
                morto_c |= acabou
                vc = np.where(morto_c, 0.0, vc - sq_v)
            # carteiras
            if por_ativo:
                r = np.empty((B, C))
                for c in range(C):
                    H = Hs[c]
                    t0_ = H.sum(1)
                    H *= 1 + R[j][:, ks[c]]
                    t1_ = H.sum(1)
                    r[:, c] = np.where(t0_ > 0, t1_ / np.where(t0_ > 0, t0_, 1) - 1, 0.0)
                    H *= taxa_m
                    if not retirando:
                        H += ap_v[:, None] * J.W[c, ks[c]]
                    tot = H.sum(1)
                    if retirando:
                        ok = tot >= sq_v
                        H *= np.where(ok, (tot - sq_v) / np.where(tot > 0, tot, 1), 0.0)[:, None]
                        novo = ~ok & (morto[:, c] == 0)
                        morto[novo, c] = t
                        tot = H.sum(1)
                    if cfg.rebal > 0 and t % cfg.rebal == 0:
                        H[:] = tot[:, None] * J.W[c, ks[c]]
                    V[:, c] = tot
            else:
                r = P[j] if mod.reamostragem else x[:, :C]
                V = V * (1 + r) * taxa_m + ap_v[:, None]
                if retirando:
                    ok = V >= sq_v[:, None]
                    novo = ~ok & (morto == 0)
                    morto[novo] = t
                    V = np.where(ok, V - sq_v[:, None], 0.0)
                V = np.where(morto > 0, 0.0, V)
            lr = np.log1p(r) + np.log(taxa_m)
            nivel += lr; cresce += lr - (np.log1p(ri)[:, None] if plano.real else 0.0)
            topo = np.maximum(topo, nivel); qd = np.minimum(qd, nivel - topo)
            if rec < len(pontos) and t == pontos[rec]:
                d = inf if plano.real else np.ones(B)
                if feito < G:
                    g = min(B, G - feito); cam[feito:feito + g, rec, :] = (V[:g] / d[:g, None]).astype(np.float32)
                soma_cdi[rec] += (vc / d).sum(); soma_inv[rec] += (inv_real if plano.real else inv_nom).sum()
                rec += 1
        base_ir = np.maximum(0.0, inv_nom - saq)
        ir = plano.ir / 100
        liq = (lambda v: v - ir * np.maximum(0.0, v - base_ir[:, None])) if ir else (lambda v: v)
        d = inf if plano.real else np.ones(B)
        finais[reg] = liq(V) / d[:, None]
        fin_cdi[reg] = (liq(vc[:, None])[:, 0]) / d
        inv[reg] = inv_real if plano.real else inv_nom
        anual[reg] = np.exp(cresce / N * 12) - 1
        queda[reg] = np.expm1(qd)
        ruina[reg] = morto
        feito += B
        if progresso:
            print(f"\r  {feito:,}/{S:,} cenários".replace(",", "."), end="", flush=True)
    if progresso:
        print()
    return Resultado(J.nomes, plano, cfg, J, finais, fin_cdi, inv, anual, queda, ruina, pontos, cam,
                     soma_cdi / S, soma_inv / S, time.time() - t0, mod.descricao())
