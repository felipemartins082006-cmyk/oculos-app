"""Linha de comando do Simulador Martins.

  python -m simulador info                         resumo da base
  python -m simulador buscar papel --tipo fii      procura ativos
  python -m simulador estudo exemplos/x.json       otimização + Monte Carlo + backtest + relatório HTML
  python -m simulador simular exemplos/x.json      só o Monte Carlo (tabela no terminal)
  python -m simulador validar-proxy                testa a extensão de histórico por fatores
  python -m simulador atualizar                    baixa tudo de novo (TradingView, CVM, BCB)
"""
import argparse
import json
import sys
import warnings

import pandas as pd

warnings.filterwarnings("ignore")
pd.set_option("display.width", 220); pd.set_option("display.max_columns", 40); pd.set_option("display.max_rows", 200)


def main(argv=None):
    ap = argparse.ArgumentParser(prog="simulador", description="Simulador Martins: Monte Carlo, otimização e backtest de carteiras.")
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("info")
    b = sub.add_parser("buscar"); b.add_argument("texto", nargs="?", default=""); b.add_argument("--tipo", action="append")
    b.add_argument("--mandato"); b.add_argument("--min-meses", type=int, default=0); b.add_argument("--limite", type=int, default=40)
    e = sub.add_parser("estudo"); e.add_argument("config")
    s = sub.add_parser("simular"); s.add_argument("config"); s.add_argument("--cenarios", type=int)
    sub.add_parser("validar-proxy")
    sub.add_parser("atualizar")
    a = ap.parse_args(argv)

    if a.cmd == "atualizar":
        from .coleta import atualizar_base
        return atualizar_base()
    from .dados import Base
    base = Base.do_app()
    if a.cmd == "info":
        print(f"Base atualizada em {base.atualizado}: {len(base.meta) - 2} ativos, {base.retornos.index[0]} a {base.retornos.index[-1]}")
        print(base.resumo().to_string())
    elif a.cmd == "buscar":
        r = base.buscar(a.texto, a.tipo, a.mandato, a.min_meses, a.limite)
        print(r[["nome", "tipo", "setor", "mandato", "meses", "p_vp"]].to_string())
    elif a.cmd == "estudo":
        from .estudo import executar
        executar(a.config, base)
    elif a.cmd == "simular":
        from .monte_carlo import Config, Plano, simular
        c = json.load(open(a.config, encoding="utf-8"))
        cfg = Config(**c.get("simulacao", {}))
        if a.cenarios:
            cfg.cenarios = a.cenarios
        r = simular(base, c["carteiras"], Plano(**c.get("plano", {})), cfg)
        print(r.modelo_desc, f"| janela {r.janela.meses[0]}–{r.janela.meses[-1]} | {r.segundos:.1f} s")
        print(r.resumo().round(3).to_string())
    elif a.cmd == "validar-proxy":
        from . import proxy
        for mand in ("papel", "tijolo"):
            l = base.buscar(tipos=["fii"], mandato=mand, min_meses=120, limite=60).index.tolist()
            v = proxy.validar(base, l, 60)
            print(f"\n{mand}: {len(v)} FIIs | erro mediano {v['erro_ret'].abs().median():.2%} | correlação mediana {v['correlacao'].median():.2f}")
            print(v.round(3).to_string())


if __name__ == "__main__":
    sys.exit(main())
