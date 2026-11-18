"""Monte Carlo vetorizado em lotes (de mil a centenas de milhões de cenários) com plano completo.

Todas as carteiras e o CDI de referência usam exatamente os mesmos sorteios (números aleatórios
comuns), então a diferença entre elas não é sorte.

Até 2 milhões de cenários os percentis são exatos (todos os valores ficam guardados). Acima disso,
as distribuições vão para histogramas de 50 mil faixas (escala logarítmica para o patrimônio),
montados a partir do primeiro lote: a memória fica constante e o erro dos percentis é desprezível.
Probabilidades (perda, superar o CDI, meta, dinheiro acabar) são contadas cenário a cenário, sempre exatas.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from . import modelos as M
from . import proxy

QS = [0.01, 0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95, 0.99]
EXATO = 2_000_000
AMOSTRA = 1_000_000
FAIXAS = 50_000


@dataclass
class Plano:
    inicial: float = 10_000
    aporte: float = 1_000
    anos: int = 40
    meta: float = 0
    corrige: bool = True          # aporte corrigido pelo IPCA (inflação)
    reaj: float = 0.0             # aumento real do aporte (% ao ano)
    saque_ano: float | None = None  # começa a sacar depois deste ano (aportes param)
    saque: float = 0.0            # saque mensal
    saque_corrige: bool = True
    taxa: float = 0.0             # custo extra (% ao ano)
    ir: float = 0.0               # IR (Imposto de Renda) sobre o ganho nominal no resgate final (%)
    real: bool = True             # resultados em reais de hoje


@dataclass
class Config:
    modelo: str = "bootstrap"     # bootstrap, blocos, estacionario, normal, t, garch, regimes
    bloco: int = 12
    cenarios: int = 100_000
    desde: int = 1995             # início máximo da janela histórica (dados desde 1995, Plano Real)
    janela_anos: int | None = None  # alternativa a `desde`: quantos anos de passado usar
    curto: str = "redistribuir"   # ativos mais novos que a janela: redistribuir | cortar | estimar
    rebal: int = 1                # meses entre rebalanceamentos (0 = nunca)
    cambio: bool = True
    ajuste: float = 0.0           # p.p. (pontos percentuais) ao ano somados aos ativos de risco
    estender: bool = False        # (compatibilidade) = curto="estimar"
    semente: int | None = 42
    caminhos_grafico: int = 20_000
    gl: float = 5
    estados: int = 2

    def inicio(self, ultimo: pd.Period) -> int:
        if self.janela_anos:
            return max(1995, ultimo.year - self.janela_anos + 1)
        return self.desde

    def modo(self) -> str:
        return "estimar" if self.estender else self.curto


# ---------------------------------------------------------------- distribuição acumulada
class Acum:
    """Guarda todos os valores até EXATO; acima disso, histograma fino. Sempre guarda uma amostra."""

    def __init__(self, tipo: str, total: int):
        self.tipo, self.total = tipo, total          # tipo: 'log' (≥ 0), 'log1p' (> −1), 'lin'
        self.exato = total <= EXATO
        self.partes, self.n, self.soma = [], 0, 0.0
        self.hist = None; self.zeros = 0; self.lo = self.hi = 0.0
        self.amostra = None

    def _tf(self, x):
        return np.log(x) if self.tipo == "log" else np.log1p(x) if self.tipo == "log1p" else x

    def _inv(self, y):
        return np.exp(y) if self.tipo == "log" else np.expm1(y) if self.tipo == "log1p" else y

    def add(self, x: np.ndarray):
        x = np.asarray(x, float)
        self.n += len(x); self.soma += float(x.sum())
        if self.exato or sum(len(p) for p in self.partes) < AMOSTRA:
            self.partes.append(x[: max(0, (self.total if self.exato else AMOSTRA) - sum(len(p) for p in self.partes))].copy())
        if self.exato:
            return
        if self.hist is None:
            pos = x[x > 0] if self.tipo == "log" else x
            y = self._tf(pos) if len(pos) else np.array([0.0, 1.0])
            span = max(float(y.max() - y.min()), 1e-6)
            self.lo, self.hi = float(y.min() - span), float(y.max() + span)
            if self.tipo == "lin":
                self.lo, self.hi = min(self.lo, -1.0), max(self.hi, 0.0)
            self.hist = np.zeros(FAIXAS)
        if self.tipo == "log":
            z = x <= 0; self.zeros += int(z.sum()); x = x[~z]
        y = np.clip(self._tf(x), self.lo, self.hi - 1e-12)
        self.hist += np.bincount(((y - self.lo) / (self.hi - self.lo) * FAIXAS).astype(np.int64), minlength=FAIXAS)[:FAIXAS]

    def valores(self) -> np.ndarray:
        if self.amostra is None:
            self.amostra = np.concatenate(self.partes) if self.partes else np.array([])
        return self.amostra

    def quantil(self, q: float) -> float:
        if self.exato:
            return float(np.quantile(self.valores(), q))
        alvo = q * (self.n - 1)
        if alvo < self.zeros:
            return 0.0
        acum = np.cumsum(self.hist); alvo -= self.zeros
        i = int(np.searchsorted(acum, alvo, side="right"))
        i = min(i, FAIXAS - 1)
        antes = acum[i - 1] if i else 0.0
        frac = (alvo - antes + 0.5) / max(self.hist[i], 1)
        return float(self._inv(self.lo + (i + frac) / FAIXAS * (self.hi - self.lo)))

    def media(self) -> float:
        return self.soma / max(self.n, 1)


# ---------------------------------------------------------------- janela histórica
@dataclass
class Janela:
    codigos: list
    R: pd.DataFrame               # retornos em reais dos ativos (meses × ativos), NaN antes de o ativo existir
    W: np.ndarray                 # pesos (carteiras × ativos)
    nomes: list
    cdi: np.ndarray
    ipca: np.ndarray
    meses: pd.PeriodIndex
    limitante: str | None
    estimados: dict = field(default_factory=dict)
    jovens: list = field(default_factory=list)   # (ativo, mês de início) dos que entram depois do início da janela
    modo: str = "cortar"
    pedido: pd.Period | None = None

    @property
    def P(self) -> np.ndarray:    # retornos mensais das carteiras (rebalanceamento mensal, pesos redistribuídos)
        return compor(self.R.to_numpy(), self.W)[0]


def compor(R: np.ndarray, W: np.ndarray):
    """Retorno de cada carteira com os pesos dos ativos que ainda não existiam redistribuídos entre os
    demais, e (por carteira) a matriz de retornos com esses meses preenchidos pelo retorno da carteira."""
    ok = ~np.isnan(R)
    R0 = np.where(ok, R, 0.0)
    pesos_ok = ok.astype(float) @ W.T                        # L × C
    P = (R0 @ W.T) / np.where(pesos_ok > 0, pesos_ok, 1.0)
    Rc = [np.where(ok, R, P[:, [c]]) for c in range(W.shape[0])]
    return P, Rc


def preparar(base, carteiras: dict[str, dict[str, float]], desde: int = 1995, cambio: bool = True,
             estender: bool = False, semente: int = 7, modo: str | None = None) -> Janela:
    modo = modo or ("estimar" if estender else "cortar")
    nomes = list(carteiras)
    codigos = list(dict.fromkeys(c for cart in carteiras.values() for c, p in cart.items() if p > 0))
    R = base.retornos_brl(codigos, cambio)
    estimados = {}
    if modo == "estimar":
        fx = base.retornos["USDBRL"]
        for c in codigos:
            if c.startswith("RF:"):
                continue
            s, n = proxy.estender(base, c, semente)
            if n:
                estimados[c] = n
                R[c] = (1 + s) * (1 + fx) - 1 if cambio and base.meta.loc[c, "moeda"] == "USD" else s
    macro = base.retornos[["CDI", "IPCA", "USDBRL"]].add_suffix("_m")
    R = R.join(macro)
    pedido = pd.Period(f"{desde}-01", "M")
    inicio = {c: R[c].first_valid_index() for c in codigos}
    ini_macro = max(R[m].first_valid_index() for m in macro.columns)
    if modo == "redistribuir":
        # cada carteira começa no seu ativo mais antigo; a janela comum começa na carteira mais nova
        ini_cart = {}
        for n_, cart in carteiras.items():
            cods = [c for c, p in cart.items() if p > 0]
            ini_cart[n_] = ini_macro if any(c.startswith("RF:") for c in cods) else min(inicio[c] for c in cods)
        lim_cart = max(ini_cart, key=lambda k: ini_cart[k])
        ini = max(pedido, ini_macro, ini_cart[lim_cart])
        cods_lim = [c for c, p in carteiras[lim_cart].items() if p > 0 and not c.startswith("RF:")]
        lim = min(cods_lim, key=lambda c: inicio[c]) if cods_lim else None
        R = R.loc[ini:, codigos + list(macro.columns)].dropna(subset=list(macro.columns))
        R = R.dropna(axis=0, how="all", subset=[c for c in codigos])
    else:
        lim = max(inicio, key=lambda c: inicio[c])
        R = R.loc[pedido:, codigos + list(macro.columns)].dropna()
    if len(R) < 24:
        raise ValueError(f"Só {len(R)} meses de histórico na janela (limitado por {lim}).")
    W = np.zeros((len(nomes), len(codigos)))
    for i, n in enumerate(nomes):
        tot = sum(p for p in carteiras[n].values() if p > 0)
        for c, p in carteiras[n].items():
            if p > 0:
                W[i, codigos.index(c)] += p / tot
    jovens = [(c, inicio[c]) for c in codigos if not c.startswith("RF:") and inicio[c] > R.index[0]] if modo == "redistribuir" else []
    jovens.sort(key=lambda x: x[1], reverse=True)
    lim = lim if R.index[0] > pedido else None
    return Janela(codigos, R[codigos], W, nomes, R["CDI_m"].to_numpy(), R["IPCA_m"].to_numpy(), R.index, lim,
                  estimados, jovens, modo, pedido)


# ---------------------------------------------------------------- resultado
@dataclass
class Resultado:
    nomes: list
    plano: Plano
    cfg: Config
    janela: Janela
    fin: list                     # Acum por carteira (patrimônio final)
    fin_cdi: Acum
    anual_: list                  # Acum por carteira (retorno ao ano sem efeito dos aportes)
    queda_: list                  # Acum por carteira (queda máxima)
    contagem: dict                # por carteira: perda, cdi, meta, ruina
    ruina_cdi: int
    pontos: np.ndarray
    caminhos: np.ndarray          # cenários do gráfico × pontos × carteiras
    cdi_medio: np.ndarray
    inv_medio: np.ndarray
    segundos: float
    modelo_desc: str
    cenarios: int

    @property
    def investido(self) -> float:
        return float(self.inv_medio[-1])

    def resumo(self) -> pd.DataFrame:
        S = self.cenarios
        linhas = {}
        for i, n in enumerate(self.nomes):
            f = self.fin[i]
            d = {f"P{int(q * 100)}": f.quantil(q) for q in QS}
            d["média"] = f.media()
            d["ret. a.a. P50"] = self.anual_[i].quantil(0.5)
            d["ret. a.a. P10"] = self.anual_[i].quantil(0.10)
            d["queda máx. P50"] = self.queda_[i].quantil(0.5)
            d["queda máx. P10"] = self.queda_[i].quantil(0.10)
            c = self.contagem[n]
            d["prob. perda"] = c["perda"] / S
            d["prob. > CDI"] = c["cdi"] / S
            if self.plano.meta:
                d["prob. meta"] = c["meta"] / S
            if self.plano.saque_ano is not None:
                d["prob. dinheiro acabar"] = c["ruina"] / S
            if f.exato:  # erro de Monte Carlo da mediana (intervalo de 95% pela estatística de ordem)
                o = np.sort(f.valores()); n_ = len(o); k = 1.96 * np.sqrt(n_ * 0.25)
                d["IC95 P50"] = f"{o[max(0, int(n_ / 2 - k))]:,.0f} – {o[min(n_ - 1, int(n_ / 2 + k))]:,.0f}".replace(",", ".")
            else:
                d["IC95 P50"] = "± <0,01% (histograma)"
            linhas[n] = d
        cdi = {f"P{int(q * 100)}": self.fin_cdi.quantil(q) for q in QS}
        cdi["média"] = self.fin_cdi.media()
        if self.plano.saque_ano is not None:
            cdi["prob. dinheiro acabar"] = self.ruina_cdi / S
        linhas["CDI (mesmo plano)"] = cdi
        return pd.DataFrame(linhas).T

    def percentis_tempo(self, i: int) -> pd.DataFrame:
        c = self.caminhos[:, :, i]
        return pd.DataFrame({f"P{int(q * 100)}": np.quantile(c, q, axis=0) for q in (0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95)},
                            index=self.pontos)


# ---------------------------------------------------------------- simulação
def simular(base, carteiras: dict[str, dict[str, float]], plano: Plano = Plano(), cfg: Config = Config(),
            janela: Janela | None = None, progresso: bool = True) -> Resultado:
    t0 = time.time()
    J = janela or preparar(base, carteiras, cfg.inicio(base.retornos.index[-1]), cfg.cambio, modo=cfg.modo())
    rng = np.random.default_rng(cfg.semente)
    C, K, L, N, S = len(J.nomes), len(J.codigos), len(J.meses), int(plano.anos * 12), int(cfg.cenarios)
    risco = np.array([not c.startswith("RF:") for c in J.codigos])
    R = J.R.to_numpy().copy()
    if cfg.ajuste:
        R[:, risco] = (1 + R[:, risco]) * (1 + cfg.ajuste / 100) ** (1 / 12) - 1
    P, Rc = compor(R, J.W)                                # L × C ; por carteira L × K
    X = np.column_stack([P, J.cdi, J.ipca])
    mod = M.criar(cfg.modelo, bloco=cfg.bloco, gl=cfg.gl, estados=cfg.estados, fixas=(C, C + 1)).ajustar(X)
    por_ativo = mod.reamostragem and cfg.rebal != 1
    taxa_m = (1 + plano.taxa / 100) ** (-1 / 12)
    m_saque = int(round(plano.saque_ano * 12)) if plano.saque_ano is not None else N + 1
    passo = max(1, -(-N // 120))
    pontos = np.unique(np.r_[np.arange(0, N + 1, passo), N])
    G = min(cfg.caminhos_grafico, S)
    D = C + 2
    lote = max(1000, min(50_000, int(1.2e7 / (N * (D if not mod.reamostragem else 1)))))

    fin = [Acum("log", S) for _ in range(C)]; fin_cdi = Acum("log", S)
    anual = [Acum("log1p", S) for _ in range(C)]; queda = [Acum("lin", S) for _ in range(C)]
    cont = {n: {"perda": 0, "cdi": 0, "meta": 0, "ruina": 0} for n in J.nomes}; ruina_cdi = 0
    cam = np.empty((G, len(pontos), C), dtype=np.float32)
    soma_cdi = np.zeros(len(pontos)); soma_inv = np.zeros(len(pontos))
    ks = [np.flatnonzero(J.W[c] > 0) for c in range(C)]

    feito, t_ult = 0, time.time()
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
            vc = np.where(morto_c, 0.0, vc * (1 + rc) + ap_v)
            if retirando:
                morto_c |= (vc < sq_v) & ~morto_c
                vc = np.where(morto_c, 0.0, vc - sq_v)
            if por_ativo:
                r = np.empty((B, C))
                for c in range(C):
                    H = Hs[c]
                    t0_ = H.sum(1)
                    H *= 1 + Rc[c][j][:, ks[c]]
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
        F = liq(V) / d[:, None]
        Fc = liq(vc[:, None])[:, 0] / d
        inv = inv_real if plano.real else inv_nom
        for c, n in enumerate(J.nomes):
            fin[c].add(F[:, c]); anual[c].add(np.exp(cresce[:, c] / N * 12) - 1); queda[c].add(np.expm1(qd[:, c]))
            k = cont[n]
            k["perda"] += int((F[:, c] < inv).sum()); k["cdi"] += int((F[:, c] > Fc).sum())
            k["meta"] += int((F[:, c] >= plano.meta).sum()) if plano.meta else 0
            k["ruina"] += int((morto[:, c] > 0).sum())
        fin_cdi.add(Fc); ruina_cdi += int(morto_c.sum())
        feito += B
        if progresso and (time.time() - t_ult > 1 or feito == S):
            t_ult = time.time(); dec = t_ult - t0
            falta = dec * (S - feito) / feito if feito else 0
            print(f"\r  {feito:,}/{S:,} cenários ({feito / S:.1%}) · faltam ~{falta / 60:.1f} min   ".replace(",", "."), end="", flush=True)
    if progresso:
        print()
    return Resultado(J.nomes, plano, cfg, J, fin, fin_cdi, anual, queda, cont, ruina_cdi, pontos, cam,
                     soma_cdi / S, soma_inv / S, time.time() - t0, mod.descricao(), S)
