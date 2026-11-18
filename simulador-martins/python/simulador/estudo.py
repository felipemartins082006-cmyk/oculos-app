"""Estudo completo a partir de um arquivo de configuração JSON:
otimização → Monte Carlo → robustez entre modelos → backtest → relatório HTML."""
from __future__ import annotations

import json
import time
from pathlib import Path

import numpy as np
import pandas as pd

from . import backtest as BT
from . import otimizacao as O
from . import proxy
from . import relatorio as REL
from .dados import Base
from .monte_carlo import Config, Plano, preparar, simular

NOMES_OBJ = {
    "max_sharpe": "máximo Sharpe (retorno acima do CDI por unidade de risco)", "min_variancia": "mínima variância", "risco_alvo": "máximo retorno com risco-alvo",
    "paridade_risco": "paridade de risco", "min_cvar": "mínimo CVaR 95% (perda média nos 5% piores meses)", "max_sortino": "máximo Sortino",
    "max_utilidade": "máxima utilidade média-variância", "por_meta": "melhor para o objetivo do plano (Monte Carlo)",
}


def _pesos_txt(cart: dict, base) -> str:
    itens = sorted(cart.items(), key=lambda x: -x[1])
    return ", ".join(f"{c} {p:.0f}%" for c, p in itens)


def otimizar_bloco(base, op: dict, plano: Plano, cfg: Config):
    u = op.get("universo", {})
    R = O.universo(base, tipos=u.get("tipos"), mandato=u.get("mandato"), min_meses=u.get("min_meses", 60),
                   janela_meses=u.get("janela_meses"), codigos=u.get("codigos"), max_ativos_universo=u.get("max_ativos_universo", 120),
                   cambio=cfg.cambio, estender=u.get("estender", False))
    rf = base.retornos["CDI"].reindex(R.index).mean()
    X = R.to_numpy()
    kw = dict(rf_m=rf, minimo=op.get("minimo", 0.0), maximo=op.get("maximo", 1.0), vol_alvo=op.get("vol_alvo"),
              media=op.get("media", "encolhida"), cov=op.get("cov", "ledoit_wolf"), max_ativos=op.get("max_ativos"))
    obj = op.get("objetivo", "max_sharpe")
    fr = None
    if obj == "por_meta":
        sub = Config(**{**cfg.__dict__, "desde": 1995, "janela_anos": None, "curto": "cortar", "estender": False})
        fr, w = O.por_meta(base, list(R.columns), X, plano, sub, op.get("criterio", "prob_meta"), op.get("pontos", 15), rf,
                           op.get("cenarios", 20_000), queda_max=op.get("queda_max"), p10_min=op.get("p10_min"),
                           minimo=kw["minimo"], maximo=kw["maximo"], media=kw["media"], cov=kw["cov"])
        if kw["max_ativos"] and (w > 1e-4).sum() > kw["max_ativos"]:
            corte = np.sort(w)[-kw["max_ativos"]]; w = np.where(w >= corte, w, 0); w /= w.sum()
    elif op.get("reamostragem"):
        w = O.reamostrada(X, obj, n=op["reamostragem"], **kw)
    else:
        w = O.otimizar(X, obj, **kw)
    cart = {c: round(p * 100, 1) for c, p in zip(R.columns, w) if p >= 0.005}
    tot = sum(cart.values()); cart = {c: round(p * 100 / tot, 1) for c, p in cart.items()}
    mu, C = O.estimar(X, kw["media"], kw["cov"])
    e = O.estat(w, mu, C, rf)
    if fr is None:
        fr = O.fronteira(X, rf, 20, minimo=kw["minimo"], maximo=kw["maximo"], media=kw["media"], cov=kw["cov"])
    isolados = pd.DataFrame([O.estat(np.eye(len(mu))[i], mu, C, rf) for i in range(len(mu))], index=R.columns)
    wf = None
    if obj != "por_meta" and len(R) >= 60:
        try:
            wf = O.fora_da_amostra(R, obj, teste=min(36, len(R) // 3), reamostragem=min(op.get("reamostragem", 0), 50),
                                   outras=op.get("_comparar"), **kw)
        except Exception:
            wf = None
    return cart, {"universo": R, "estat": e, "fronteira": fr, "isolados": isolados, "rf": rf, "fora": wf}


def executar(caminho: str | Path, base: Base | None = None, verbose: bool = True) -> Path:
    t0 = time.time()
    caminho = Path(caminho)
    cfgj = json.loads(caminho.read_text(encoding="utf-8"))
    base = base or Base.do_app()
    plano = Plano(**cfgj.get("plano", {}))
    cfg = Config(**cfgj.get("simulacao", {}))
    log = (lambda *a: print(*a, flush=True)) if verbose else (lambda *a: None)

    carteiras = dict(cfgj.get("carteiras", {}))
    otim_info = {}
    for op in cfgj.get("otimizacoes", []):
        op["_comparar"] = cfgj.get("carteiras", {})
        log(f"• Otimizando: {op['nome']}")
        cart, info = otimizar_bloco(base, op, plano, cfg)
        carteiras[op["nome"]] = cart
        otim_info[op["nome"]] = (op, info)
    carteiras.update(cfgj.get("comparar_com", {}))

    log(f"• Monte Carlo: {cfg.cenarios:,} cenários × {len(carteiras)} carteiras ({cfg.modelo})".replace(",", "."))
    J = preparar(base, carteiras, cfg.inicio(base.retornos.index[-1]), cfg.cambio, modo=cfg.modo())
    res = simular(base, carteiras, plano, cfg, janela=J, progresso=verbose)

    rob = {}
    for m in cfgj.get("robustez", []):
        log(f"• Robustez: {m}")
        c2 = Config(**{**cfg.__dict__, "modelo": m, "cenarios": min(cfg.cenarios, cfgj.get("cenarios_robustez", 50_000)), "caminhos_grafico": 100})
        r2 = simular(base, carteiras, plano, c2, janela=J, progresso=False)
        s2 = r2.resumo()
        rob[r2.modelo_desc.split(" (")[0].split(":")[0]] = s2

    log("• Backtest")
    P, met = BT.backtest(base, J, plano.taxa, cambio=cfg.cambio)
    anos = BT.por_ano(P)

    # ------------------------------------------------------------------ relatório
    s = res.resumo()
    carts_nomes = [n for n in res.nomes]
    secoes = []
    crit = "prob. meta" if plano.meta else "P50"
    melhor = s.loc[carts_nomes, crit].idxmax()
    menor_risco = s.loc[carts_nomes, "P10"].idxmax()
    com_risco = [n for n in carts_nomes if not all(c.startswith("RF:") for c in carteiras[n])]
    kp = [(f"Maior {'chance de atingir a meta' if plano.meta else 'patrimônio mediano'}", melhor,
           _pct_or_brl(s.loc[melhor, crit], plano.meta)),
          ("Melhor cenário ruim (P10)", menor_risco, REL._brl(s.loc[menor_risco, "P10"])),
          ("Menor queda máxima (P50)", s.loc[com_risco, "queda máx. P50"].idxmax(), REL._pct(s.loc[com_risco, "queda máx. P50"].max(), 0)),
          ("Mesmo plano no CDI (P50)", "referência", REL._brl(s.loc["CDI (mesmo plano)", "P50"]))]
    html_kp = '<div class="kpis">' + "".join(f'<div class="k"><span>{t}</span><b>{v}</b><small>{n}</small></div>' for t, n, v in kp) + "</div>"
    texto = (f"<p>Plano: aporte inicial de {REL._brl(plano.inicial, False)}, {REL._brl(plano.aporte, False)} por mês"
             f"{' corrigidos pelo IPCA' if plano.corrige else ''}, por {plano.anos} anos"
             f"{f', meta de {REL._brl(plano.meta, False)}' if plano.meta else ''}"
             f"{f', saques de {REL._brl(plano.saque, False)}/mês a partir do ano {plano.saque_ano:g}' if plano.saque_ano is not None else ''}. "
             f"Valores {'em reais de hoje' if plano.real else 'nominais'}.</p>"
             f"<p>{cfg.cenarios:_} cenários com {res.modelo_desc}, janela histórica de {J.meses[0]} a {J.meses[-1]} ({len(J.meses)} meses)"
             f"{f', contagem a partir de {J.limitante}' if J.limitante else ''}. Processado em {_tempo(res.segundos)}.</p>").replace("_", ".")
    if J.pedido is not None and J.meses[0] > J.pedido:
        texto += f'<p class="aviso">Você pediu histórico desde {J.pedido.year}, mas os ativos só permitem contar a partir de {J.meses[0]}.</p>'
    if J.jovens:
        texto += ('<p class="aviso">Ativos mais novos que a janela: entram quando passam a existir; antes disso, o peso deles é dividido entre os demais ativos da carteira: '
                  + ", ".join(f"{c} desde {m}" for c, m in J.jovens) + ".</p>")
        R0 = J.R.iloc[0]
        for i, n in enumerate(J.nomes):
            disp = [c for k, c in enumerate(J.codigos) if J.W[i, k] > 0 and not np.isnan(R0[c])]
            soma = sum(J.W[i, J.codigos.index(c)] for c in disp)
            if soma < 0.999:
                texto += (f'<p class="aviso">Em {J.meses[0]}, <b>{n}</b> começa com só {soma:.0%} dos pesos-alvo existindo ({", ".join(disp)}); '
                          'esses ativos valem 100% da carteira até os outros começarem. Se tiveram um período excepcional, isso pesa no resultado.</p>')
    if len(J.meses) < 216:
        texto += f'<p class="aviso">Janela de {len(J.meses) / 12:.0f} anos: os resultados refletem muito esse período. Veja a tabela de robustez e o backtest.</p>'
    if J.estimados:
        texto += f'<p class="aviso">Histórico estendido por modelo de fatores (meses estimados): {", ".join(f"{c} {n}" for c, n in J.estimados.items())}.</p>'
    secoes.append(("Resposta", "", html_kp + texto))

    pesos = pd.DataFrame({"composição": {n: _pesos_txt(c, base) for n, c in carteiras.items()}})
    linhas_ot = ""
    for n, (op, info) in otim_info.items():
        u = op.get("universo", {})
        restr = ", ".join(x for x in [f"queda máx. mediana até {op['queda_max']:.0%}" if op.get("queda_max") is not None else "",
                                       f"P10 ≥ {REL._brl(op['p10_min'])}" if op.get("p10_min") else ""] if x)
        linhas_ot += (f"<li><b>{n}</b>: {NOMES_OBJ.get(op.get('objetivo', 'max_sharpe'))}{f' ({restr})' if restr else ''}"
                      f"{f', reamostrada {op.get('reamostragem')}×' if op.get('reamostragem') else ''}"
                      f" sobre {info['universo'].shape[1]} ativos ({info['universo'].index[0]} a {info['universo'].index[-1]}), "
                      f"peso máx. {op.get('maximo', 1):.0%}{f', até {op.get('max_ativos')} ativos' if op.get('max_ativos') else ''}. "
                      f"Histórico: retorno {REL._pct(info['estat']['retorno'])}, vol. {REL._pct(info['estat']['volatilidade'])}, Sharpe {info['estat']['sharpe']:.2f}.</li>")
    secoes.append(("Carteiras", f"{len(carteiras)} comparadas", (f"<ul>{linhas_ot}</ul>" if linhas_ot else "") + REL.tabela(pesos)))

    cols = [c for c in ["P5", "P10", "P25", "P50", "P75", "P90", "P95", "ret. a.a. P50", "queda máx. P50", "prob. perda", "prob. > CDI", "prob. meta", "prob. dinheiro acabar", "IC95 P50"] if c in s.columns]
    fmt = {c: (REL._pct if (c.startswith("prob") or c.startswith("ret") or c.startswith("queda")) else REL._brl) for c in cols}
    NOMES_COL = {"P5": "P5 (5% abaixo)", "P10": "P10 (pessimista)", "P25": "P25", "P50": "P50 (mediana)", "P75": "P75", "P90": "P90 (otimista)",
                 "P95": "P95 (5% acima)", "ret. a.a. P50": "Retorno ao ano (mediana)", "queda máx. P50": "Queda máxima (mediana)",
                 "prob. perda": "Prob. de perda", "prob. > CDI": "Prob. de superar o CDI", "prob. meta": "Prob. de atingir a meta",
                 "prob. dinheiro acabar": "Prob. de o dinheiro acabar", "IC95 P50": "Intervalo de confiança 95% da mediana"}
    secoes.append(("Monte Carlo", f"{cfg.cenarios:,} cenários".replace(",", "."),
                   REL.tabela(s[cols].astype(object).where(s[cols].notna(), np.nan).rename(columns=NOMES_COL), {NOMES_COL[c]: f for c, f in fmt.items()}) + REL.leque(res) + REL.histograma(res) +
                   "<p>P5, P10... P95 são percentis: ordenando todos os cenários, P10 é o valor que só 10% ficam abaixo e P50 é a mediana. "
                   "O intervalo de confiança mostra o erro da própria simulação (quanto a mediana poderia mudar rodando de novo).</p>"))

    if rob:
        tab = pd.DataFrame({m: v["P50"] for m, v in rob.items()}).T
        tab2 = pd.DataFrame({m: v["prob. meta" if plano.meta else "prob. > CDI"] for m, v in rob.items()}).T.drop(columns=["CDI (mesmo plano)"], errors="ignore")
        secoes.append(("Robustez entre modelos", "mesmo plano, mesma janela",
                       "<p>Patrimônio mediano (P50) em cada modelo de retornos:</p>" + REL.tabela(tab, {"*": REL._brl}) +
                       f"<p>{'Chance de atingir a meta' if plano.meta else 'Chance de superar o CDI'} em cada modelo:</p>" + REL.tabela(tab2, {"*": REL._pct}) +
                       "<p>Se a ordem entre as carteiras se mantém em todos os modelos, a conclusão é robusta. Se muda, ela depende de hipóteses do modelo.</p>"))

    fmt_m = {c: REL._pct for c in met.index}
    met_t = met.T
    fm = {}
    for c in met_t.columns:
        fm[c] = (lambda v: f"{v:.2f}") if c in ("sharpe (retorno por risco)", "sortino (só quedas)", "beta vs Ibovespa (sensibilidade à bolsa)", "correlação c/ Ibovespa") else (lambda v: f"{v:.0f}") if c in ("maior tempo abaixo do topo (meses)", "meses") else REL._pct
    secoes.append(("Backtest", f"{J.meses[0]} a {J.meses[-1]}, rebalanceamento mensal, em R$",
                   REL.linhas_backtest(P) + REL.tabela(met_t, fm) + "<p>Retorno por ano:</p>" + REL.tabela(anos, {"*": REL._pct})))

    for n, (op, info) in list(otim_info.items())[:2]:
        fr = info["fronteira"]
        pts = {}
        for nome_c in [n] + [x for x in carteiras if x not in otim_info][:2]:
            cols_c = [c for c in carteiras[nome_c] if c in info["universo"].columns]
            if len(cols_c) == len(carteiras[nome_c]):
                w = np.array([carteiras[nome_c].get(c, 0) / 100 for c in info["universo"].columns])
                mu, C = O.estimar(info["universo"].to_numpy(), op.get("media", "encolhida"), op.get("cov", "ledoit_wolf"))
                e = O.estat(w, mu, C, info["rf"]); pts[nome_c] = (e["volatilidade"], e["retorno"])
        extra = ""
        if "prob_meta" in fr or "P50" in fr:
            t = fr[[c for c in ["retorno", "volatilidade", "sharpe", "queda_p50", "P10", "P50", "prob_cdi", "prob_meta", "prob_acabar", "aceitável", "escolhida"] if c in fr]].copy()
            for c in ("aceitável", "escolhida"):
                if c in t:
                    t[c] = t[c].map({True: "sim", False: ""})
            t.index = [f"ponto {i + 1}" for i in range(len(t))]
            fmt_fr = {c: (REL._brl if c in ("P10", "P50") else (lambda v: f"{v:.2f}") if c == "sharpe" else REL._pct) for c in t.columns}
            extra = "<p>Cada ponto da fronteira testado por Monte Carlo com o seu plano:</p>" + REL.tabela(t, fmt_fr)
        if info.get("fora") is not None:
            wf = info["fora"]
            extra += (f"<p><b>Teste fora da amostra</b>: otimizada só com {wf.attrs['treino']} e medida em {wf.attrs['teste']}, "
                      "sem olhar esse período. Se o retorno \"depois\" cai muito em relação ao \"treino\", a otimização estava se ajustando a ruído.</p>"
                      + REL.tabela(wf, {"*": REL._pct}))
        secoes.append((f"Otimização: {n}", f"{info['universo'].shape[1]} ativos no universo",
                       REL.grafico_fronteira(fr, pts, info["isolados"] if info["universo"].shape[1] <= 40 else None) + extra))

    if J.estimados:
        val = proxy.validar(base, [c for c in base.buscar(tipos=["fii"], min_meses=120, limite=40).index], 60)
        if len(val):
            secoes.append(("Validação da extensão por fatores", "fora da amostra",
                           f"<p>Ajustado nos últimos 5 anos e testado nos anos anteriores de {len(val)} FIIs com 10+ anos: "
                           f"erro mediano do retorno anual {REL._pct(val['erro_ret'].abs().median())}, erro médio {REL._pct(val['erro_ret'].mean())}, "
                           f"correlação mediana {val['correlacao'].median():.2f}.</p>"))

    secoes.append(("Glossário", "siglas e termos", REL.GLOSSARIO))
    secoes.append(("Metodologia", "", """<ul>
<li>Dados: TradingView (preços com dividendos reinvestidos), complemento Yahoo + Fundamentus validado, CVM (fundos e informe de FIIs) e Banco Central (CDI, IPCA). Ativos em dólar convertidos pelo câmbio do mês.</li>
<li>Monte Carlo com números aleatórios comuns: todas as carteiras e o CDI usam os mesmos sorteios.</li>
<li>Modelos: bootstrap, blocos, bootstrap estacionário, normal, t-Student, GARCH(1,1) com simulação histórica filtrada e mudança de regime (cadeia de Markov oculta).</li>
<li>Otimização com média encolhida (James-Stein) e covariância Ledoit-Wolf; reamostragem de Michaud para estabilidade; CVaR por programação linear.</li>
<li>Limitações: o passado não garante o futuro; IR e custos simplificados; otimização pelo passado tende a superestimar o resultado da carteira ótima.</li></ul>"""))

    saida = Path(cfgj.get("saida", caminho.with_suffix(".html")))
    if not saida.is_absolute():
        saida = caminho.parent / saida
    saida.parent.mkdir(parents=True, exist_ok=True)
    saida.write_text(REL.montar(cfgj.get("titulo", "Estudo de carteiras"), secoes, cfgj.get("subtitulo", "")), encoding="utf-8")
    log(f"• Relatório: {saida} ({time.time() - t0:.0f} s)")
    return saida


def _tempo(seg: float) -> str:
    return f"{seg:.0f} s" if seg < 120 else f"{seg / 60:.1f} min" if seg < 7200 else f"{seg / 3600:.1f} h"


def _pct_or_brl(v, meta):
    return REL._pct(v, 0) if meta else REL._brl(v)
