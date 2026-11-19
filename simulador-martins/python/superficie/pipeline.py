"""Orquestração: dados → sessões → superfície/atributos → alvo → divisão temporal → modelos → métricas.

Regras de ouro (sem vazamento):
- atributos de cada candle só usam o que existia no fechamento dele (ver atributos.py);
- treino, validação e teste são blocos de sessões em ordem cronológica, com embargo do horizonte;
- tudo que é escolhido (modelo, hiperparâmetros, limiar, alvo/stop/horizonte, ativo) é escolhido
  na VALIDAÇÃO; o TESTE é usado uma única vez, no fim, para a configuração escolhida.
"""
from __future__ import annotations

import itertools
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd

from . import alvo as alvo_mod
from . import atributos, divisao, modelo, sessao
from .dados import CRIPTO_PADRAO, carregar
from .superficie import Grade


@dataclass
class Config:
    ativos: list = field(default_factory=lambda: ["BTCUSDT"])   # um ou mais; "auto" = escolhe o melhor
    meses: int = 24
    alvo: list = field(default_factory=lambda: [0.5])            # % (lista = otimiza entre os valores)
    stop: list = field(default_factory=lambda: [0.3])
    horizonte: list = field(default_factory=lambda: [30])        # minutos
    lado: str = "continuacao"                                    # continuacao | long | short
    faixa: float = 5.0
    passo: float = 0.1
    alocacao: str = "fechamento"
    janela_hist: int = 20                                        # sessões passadas na superfície de referência
    sigma: tuple = (1.0, 1.5)                                    # suavização (tempo, oscilação)
    validacao: str = "temporal"                                  # temporal (70/15/15) | walkforward
    fracoes: tuple = (0.70, 0.15, 0.15)
    folds: int = 5
    modelos: list = field(default_factory=lambda: ["lightgbm", "xgboost"])
    otimizar_hiper: bool = True
    custo: float = 0.10                                          # % ida e volta (para o Sharpe líquido)
    min_trades: int = 30
    hora_inicio: int | None = None
    cache: str = "dados_intradia"
    saida: str = "saida_superficie"


GRADE_HIPER = {
    "lightgbm": [dict(num_leaves=15, min_child_samples=200), dict(num_leaves=31, min_child_samples=100), dict(num_leaves=63, min_child_samples=50)],
    "xgboost": [dict(max_depth=3, min_child_weight=50), dict(max_depth=5, min_child_weight=20), dict(max_depth=7, min_child_weight=10)],
}


def preparar_ativo(simbolo: str, cfg: Config, log=print) -> dict:
    df, qual = carregar(simbolo, cfg.meses, Path(cfg.cache))
    if qual["meses_obtidos"] < cfg.meses * 0.9:
        log(f"  aviso: {simbolo} tem só {qual['meses_obtidos']} meses de 15 min na fonte (pedido: {cfg.meses}); segue com o que existe")
    df = sessao.marcar(df, qual["fonte"], cfg.hora_inicio)
    T = sessao.n_slots(df)
    grade = Grade(cfg.faixa, cfg.passo)
    X, cubo = atributos.calcular(df, grade, T, cfg.janela_hist, cfg.sigma, cfg.alocacao)
    ses = np.sort(X["sessao"].unique())
    aquec = ses[min(cfg.janela_hist, len(ses) - 1)]                # sessões sem histórico suficiente ficam fora
    return {"simbolo": simbolo, "fonte": qual["fonte"], "qual": qual, "X": X, "cubo": cubo, "grade": grade, "T": T,
            "aquecimento": aquec, "dias_ano": 365 if qual["fonte"] == "binance" else 252, "mesma_sessao": qual["fonte"] != "binance"}


def _avaliar_bloco(base, rot, tr, va, te, cfg, tipos, hiper, final=True):
    """Treina cada tipo/hiperparâmetro em tr, escolhe na validação; se final, mede no teste."""
    F = atributos.ATRIBUTOS
    X = base["X"]
    melhor = None
    for tipo in tipos:
        for hp in (hiper.get(tipo) or [{}]):
            m = modelo.treinar(X.loc[tr, F], rot.loc[tr, "y"], X.loc[va, F], rot.loc[va, "y"], tipo, hp)
            pv = m.predict_proba(X.loc[va, F])[:, 1]
            lim, sim_v = modelo.escolher_limiar(pv, rot.loc[va], custo=cfg.custo, dias_ano=base["dias_ano"], min_trades=cfg.min_trades, criterio="sharpe_liquido")
            cls_v = modelo.classificacao(rot.loc[va, "y"].to_numpy(), pv, lim)
            nota = (np.nan_to_num(sim_v["sharpe_liquido"], nan=-9), cls_v["auc"])
            if melhor is None or nota > melhor["nota"]:
                melhor = {"nota": nota, "tipo": tipo, "hiper": hp, "modelo": m, "limiar": lim, "val": {"sim": sim_v, "cls": cls_v, "p": pv}}
    if final:
        pt = melhor["modelo"].predict_proba(X.loc[te, F])[:, 1]
        melhor["teste"] = {"sim": modelo.simular(pt, rot.loc[te], melhor["limiar"], custo=cfg.custo, dias_ano=base["dias_ano"]),
                           "cls": modelo.classificacao(rot.loc[te, "y"].to_numpy(), pt, melhor["limiar"]), "p": pt}
        # referência sem modelo: entrar em toda oportunidade (uma posição por vez)
        melhor["teste"]["sem_modelo"] = modelo.simular(np.ones(te.sum()), rot.loc[te], 0.5, custo=cfg.custo, dias_ano=base["dias_ano"])
        melhor["importancias"] = modelo.importancias(melhor["modelo"], F)
    return melhor


def avaliar(base: dict, cfg: Config, a: float, s: float, h: int, tipos=None, hiper=None, final=True, log=print) -> dict:
    X = base["X"]
    rot = alvo_mod.rotular(X, a, s, h, cfg.lado, mesma_sessao=base["mesma_sessao"])
    ok = rot["valido"].to_numpy() & (X["sessao"] >= base["aquecimento"]).to_numpy()
    Xo, ro = X[ok], rot[ok]
    hc = max(1, int(round(h / 15)))
    tipos = tipos or cfg.modelos
    hiper = hiper or {}
    b = {**base, "X": Xo}
    if cfg.validacao == "walkforward":
        folds, ps, idx_te = [], [], []
        for k, tr, va, te in divisao.walk_forward(Xo, cfg.folds, h=hc):
            if tr.sum() < 500 or va.sum() < 100 or te.sum() < 50:
                continue
            r = _avaliar_bloco(b, ro, tr, va, te, cfg, tipos, hiper, final)
            folds.append({"fold": k + 1, "tipo": r["tipo"], "limiar": r["limiar"], "teste_ini": str(Xo.index[te][0]), "teste_fim": str(Xo.index[te][-1]),
                          **{f"val_{x}": r["val"]["sim"][x] for x in ("sharpe_liquido", "n")}, "val_auc": r["val"]["cls"]["auc"],
                          **({f"teste_{x}": r["teste"]["sim"][x] for x in ("sharpe_bruto", "sharpe_liquido", "n", "acerto")} | {"teste_auc": r["teste"]["cls"]["auc"]} if final else {})})
            if final:
                ps.append((r["teste"]["p"] >= r["limiar"]).astype(float)); idx_te.append(np.flatnonzero(te))
            ultimo = r
        res = {"folds": folds, **ultimo}
        if final and idx_te:
            ix = np.concatenate(idx_te); sinal = np.concatenate(ps)
            r_te = ro.iloc[ix]
            res["teste"] = {"sim": modelo.simular(sinal, r_te, 0.5, custo=cfg.custo, dias_ano=base["dias_ano"]),
                            "cls": modelo.classificacao(r_te["y"].to_numpy(), sinal, 0.5),
                            "sem_modelo": modelo.simular(np.ones(len(ix)), r_te, 0.5, custo=cfg.custo, dias_ano=base["dias_ano"])}
            res["teste"]["cls"]["auc"] = float(np.nanmean([f["teste_auc"] for f in folds]))
    else:
        tr, va, te = divisao.temporal(Xo, cfg.fracoes, h=hc)
        res = _avaliar_bloco(b, ro, tr, va, te, cfg, tipos, hiper, final)
        res["blocos"] = {n: (str(Xo.index[m][0]), str(Xo.index[m][-1]), int(m.sum())) for n, m in (("treino", tr), ("validacao", va), ("teste", te)) if m.any()}
        res["mascaras"] = (tr, va, te)
    res.update(alvo=a, stop=s, horizonte=h, rot=ro, Xo=Xo, taxa_y=float(ro["y"].mean()))
    return res


def otimizar_ativo(base: dict, cfg: Config, log=print) -> dict:
    """Escolhe alvo/stop/horizonte e modelo na validação; depois hiperparâmetros; teste uma vez só."""
    combos = list(itertools.product(cfg.alvo, cfg.stop, cfg.horizonte))
    placar = []
    if len(combos) > 1:
        for a, s, h in combos:
            r = avaliar(base, cfg, a, s, h, final=False, log=log)
            placar.append({"alvo": a, "stop": s, "horizonte": h, "modelo": r["tipo"], "val_sharpe_liquido": r["val"]["sim"]["sharpe_liquido"],
                           "val_sharpe_bruto": r["val"]["sim"]["sharpe_bruto"], "val_trades": r["val"]["sim"]["n"], "val_auc": r["val"]["cls"]["auc"], "taxa_y": r["taxa_y"]})
            log(f"    alvo {a}% stop {s}% {h} min: Sharpe validação {placar[-1]['val_sharpe_liquido']:.2f} ({placar[-1]['val_trades']} trades, AUC {placar[-1]['val_auc']:.3f})")
        pl = pd.DataFrame(placar).sort_values(["val_sharpe_liquido", "val_auc"], ascending=False, na_position="last")
        a, s, h = pl.iloc[0][["alvo", "stop", "horizonte"]]
    else:
        pl = pd.DataFrame(); a, s, h = combos[0]
    hiper = GRADE_HIPER if cfg.otimizar_hiper else {}
    r = avaliar(base, cfg, float(a), float(s), int(h), hiper=hiper, log=log)
    r["placar"] = pl
    return r


def rodar(cfg: Config, log=print) -> dict:
    t0 = time.time()
    ativos = list(cfg.ativos)
    ranking = None
    if any(x.lower() == "auto" for x in ativos):
        cand = [x for x in ativos if x.lower() != "auto"] or CRIPTO_PADRAO
        if all(x.lower() == "auto" for x in ativos):
            cand = CRIPTO_PADRAO
        log(f"Modo automático: avaliando {len(cand)} ativos na validação ({', '.join(cand)})")
        linhas = []
        for sbl in cand:
            try:
                b = preparar_ativo(sbl, cfg, log)
                r = avaliar(b, cfg, cfg.alvo[0], cfg.stop[0], cfg.horizonte[0], tipos=["lightgbm"], final=False, log=log)
                linhas.append({"ativo": sbl, "val_sharpe_liquido": r["val"]["sim"]["sharpe_liquido"], "val_auc": r["val"]["cls"]["auc"],
                               "val_trades": r["val"]["sim"]["n"], "meses": b["qual"]["meses_obtidos"], "agressao": b["qual"]["tem_agressao"]})
                log(f"  {sbl}: Sharpe validação {linhas[-1]['val_sharpe_liquido']:.2f}, AUC {linhas[-1]['val_auc']:.3f}")
            except Exception as e:
                log(f"  {sbl}: sem dados ({e})")
        ranking = pd.DataFrame(linhas).sort_values(["val_sharpe_liquido", "val_auc"], ascending=False, na_position="last")
        ativos = [ranking.iloc[0]["ativo"]]
        log(f"Melhor na validação: {ativos[0]}")
    resultados = []
    for sbl in ativos:
        log(f"Ativo {sbl}")
        try:
            b = preparar_ativo(sbl, cfg, log)
        except Exception as e:
            log(f"  {sbl}: não foi possível carregar ({e})"); continue
        r = otimizar_ativo(b, cfg, log)
        r["base"] = b
        t = r["teste"]
        log(f"  teste fora da amostra: Sharpe bruto {t['sim']['sharpe_bruto']:.2f}, líquido {t['sim']['sharpe_liquido']:.2f}, "
            f"{t['sim']['n']} trades, precisão {t['cls']['precisao']:.2f}, recall {t['cls']['recall']:.2f}, AUC {t['cls']['auc']:.3f}")
        resultados.append(r)
    return {"cfg": cfg, "resultados": resultados, "ranking": ranking, "segundos": time.time() - t0}
