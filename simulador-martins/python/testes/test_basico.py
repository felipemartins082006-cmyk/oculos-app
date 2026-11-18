"""Testes rápidos: python -m pytest testes  (ou python testes/test_basico.py)"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from simulador.dados import Base  # noqa: E402
from simulador.monte_carlo import Config, Plano, simular  # noqa: E402
from simulador import modelos, otimizacao as O  # noqa: E402

BASE = Base.do_app()
CART = {"A": {"BOVA11": 50, "SPY": 50}, "B": {"RF:CDI:100": 100}}


def test_base():
    assert len(BASE.meta) > 1500
    assert abs(BASE.retornos["CDI"].loc["2020-01":"2020-12"].add(1).prod() - 1 - 0.0277) < 0.003


def test_cdi_deterministico():
    r = simular(BASE, CART, Plano(inicial=10000, aporte=0, anos=5, real=False), Config(cenarios=2000, desde=2010), progresso=False)
    s = r.resumo()
    assert abs(s.loc["B", "P50"] / s.loc["CDI (mesmo plano)", "P50"] - 1) < 1e-9   # mesmos sorteios


def test_todos_os_modelos():
    for m in modelos.MODELOS:
        r = simular(BASE, CART, Plano(anos=10), Config(modelo=m, cenarios=2000, desde=2010), progresso=False)
        for i in range(len(r.nomes)):
            f = r.fin[i].valores()
            assert np.isfinite(f).all() and (f >= 0).all()


def test_histograma_bate_com_exato():
    import simulador.monte_carlo as MC
    plano = Plano(anos=10)
    ex = simular(BASE, CART, plano, Config(cenarios=300_000, semente=1), progresso=False).resumo()
    MC.EXATO, antigo = 100_000, MC.EXATO          # força o modo histograma
    try:
        hi = simular(BASE, CART, plano, Config(cenarios=300_000, semente=1), progresso=False).resumo()
    finally:
        MC.EXATO = antigo
    for q in ("P10", "P50", "P90"):
        assert abs(hi.loc["A", q] / ex.loc["A", q] - 1) < 0.002


def test_redistribuir_ativo_novo():
    from simulador.monte_carlo import preparar
    J = preparar(BASE, {"X": {"SPY": 50, "CPTS11": 50}}, 1995, modo="redistribuir")
    assert J.meses[0].year == 1995 and any(c == "CPTS11" for c, _ in J.jovens)
    Jc = preparar(BASE, {"X": {"SPY": 50, "CPTS11": 50}}, 1995, modo="cortar")
    assert Jc.meses[0].year >= 2020


def test_ativo_muito_novo_nao_recusa():
    from simulador.monte_carlo import preparar
    J = preparar(BASE, {"So novo": {"RBFY11": 100}}, 1995, modo="redistribuir")
    assert len(J.meses) >= 36 and J.proxies and J.proxies[0][0] == "So novo"
    J2 = preparar(BASE, {"X": {"RBFY11": 50, "KNCR11": 50}}, 1995, modo="cortar")
    assert J2.trocou_modo == "cortar" and len(J2.meses) > 100
    r = simular(BASE, {"So novo": {"RBFY11": 100}}, Plano(anos=10), Config(cenarios=2000), progresso=False)
    assert np.isfinite(r.resumo().loc["So novo", "P50"])


def test_aposentadoria():
    r = simular(BASE, CART, Plano(inicial=100000, aporte=0, anos=30, saque_ano=0, saque=5000), Config(cenarios=2000), progresso=False)
    assert "prob. dinheiro acabar" in r.resumo().columns


def test_otimizadores_somam_um():
    R = O.universo(BASE, codigos=["BOVA11", "SPY", "GLD", "TLT", "IFIX", "RF:CDI:100"]).to_numpy()
    for obj in ["max_sharpe", "min_variancia", "paridade_risco", "min_cvar", "max_sortino", "risco_alvo"]:
        w = O.otimizar(R, obj, maximo=0.5, vol_alvo=0.08)
        assert abs(w.sum() - 1) < 1e-6 and (w >= -1e-9).all() and w.max() <= 0.5 + 1e-6


if __name__ == "__main__":
    for nome, f in list(globals().items()):
        if nome.startswith("test_"):
            f(); print("ok", nome)
