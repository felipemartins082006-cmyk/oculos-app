"""Linha de comando.

Exemplos
  python -m superficie                                   # BTCUSDT, 24 meses, alvo 0,5%, stop 0,3%, 30 min
  python -m superficie --ativos BTCUSDT,ETHUSDT,WIN      # vários ativos (WIN/WDO pelo TradingView)
  python -m superficie --ativos auto                     # escolhe o melhor entre 10 criptos (na validação)
  python -m superficie --alvo 0.3,0.5,0.8 --stop 0.3,0.5 --horizonte 15,30,60   # otimiza a combinação
  python -m superficie --validacao walkforward --folds 6
"""
from __future__ import annotations

import argparse
import webbrowser
from pathlib import Path

from .pipeline import Config, rodar
from .relatorio import gerar


def _lista(s, tipo=float):
    return [tipo(x) for x in str(s).replace(";", ",").split(",") if x.strip()]


def main(argv=None):
    ap = argparse.ArgumentParser(prog="python -m superficie", description="Superfícies 3D de mercado e predição intradiária (MVP)")
    ap.add_argument("--ativos", default="BTCUSDT", help="um ou mais, separados por vírgula (ex.: BTCUSDT,ETHUSDT,WIN) ou 'auto'")
    ap.add_argument("--meses", type=int, default=24)
    ap.add_argument("--alvo", default="0.5", help="% (lista = otimiza)")
    ap.add_argument("--stop", default="0.3", help="% (lista = otimiza)")
    ap.add_argument("--horizonte", default="30", help="minutos (lista = otimiza)")
    ap.add_argument("--lado", default="continuacao", choices=["continuacao", "long", "short"])
    ap.add_argument("--validacao", default="temporal", choices=["temporal", "walkforward"])
    ap.add_argument("--folds", type=int, default=5)
    ap.add_argument("--modelos", default="lightgbm,xgboost")
    ap.add_argument("--sem-hiper", action="store_true", help="não otimizar hiperparâmetros")
    ap.add_argument("--custo", type=float, default=0.10, help="% por operação (ida e volta) para o Sharpe líquido")
    ap.add_argument("--faixa", type=float, default=5.0, help="± % da grade de oscilação")
    ap.add_argument("--passo", type=float, default=0.1, help="largura de cada faixa (%)")
    ap.add_argument("--alocacao", default="fechamento", choices=["fechamento", "faixa"])
    ap.add_argument("--janela-hist", type=int, default=20)
    ap.add_argument("--cache", default="dados_intradia")
    ap.add_argument("--saida", default="saida_superficie/relatorio.html")
    ap.add_argument("--plotly", default="inline", choices=["inline", "cdn"], help="inline = abre sem internet")
    ap.add_argument("--abrir", action="store_true", help="abre o relatório no navegador no fim")
    a = ap.parse_args(argv)
    cfg = Config(ativos=[x.strip() for x in a.ativos.split(",") if x.strip()], meses=a.meses, alvo=_lista(a.alvo), stop=_lista(a.stop),
                 horizonte=_lista(a.horizonte, int), lado=a.lado, validacao=a.validacao, folds=a.folds, modelos=[m.strip() for m in a.modelos.split(",")],
                 otimizar_hiper=not a.sem_hiper, custo=a.custo, faixa=a.faixa, passo=a.passo, alocacao=a.alocacao, janela_hist=a.janela_hist,
                 cache=a.cache, saida=a.saida)
    res = rodar(cfg)
    if not res["resultados"]:
        print("Nenhum ativo pôde ser analisado.")
        return 1
    caminho = gerar(res, Path(a.saida), plotly=a.plotly)
    print(f"Relatório: {caminho.resolve()}  (métricas em {caminho.with_suffix('.json').name}) · {res['segundos']:.0f} s")
    if a.abrir:
        webbrowser.open(caminho.resolve().as_uri())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
