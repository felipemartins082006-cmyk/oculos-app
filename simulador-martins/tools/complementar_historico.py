#!/usr/bin/env python3
"""Completa o começo do histórico de ativos da B3 com dados reais de outras fontes.

Uso: python3 tools/complementar_historico.py PASTA_CACHE   (depois de baixar_dados.py)

O TradingView às vezes começa a série depois do início real (ex.: CPTS11 só a partir de 2020 após
troca de código, embora negocie desde 2014). Para esses casos:
  preço mensal do Yahoo Finance (ajustado por desdobramentos)
  + proventos do Fundamentus (dividendos, JCP líquido de 15%, rendimentos de FII)
  = retorno total dos meses anteriores ao início no TradingView.
Só é usado se, nos meses em que as duas fontes se sobrepõem, os retornos baterem
(correlação ≥ 0,85 e diferença de retorno anual ≤ 3 p.p.) — garantia de que é o mesmo ativo
e de que os proventos estão completos. Proventos do Fundamentus para FIIs só existem desde ~2017.
"""
import datetime as dt
import json
import re
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np

UA = {"User-Agent": "Mozilla/5.0"}
BASE_DIR = Path(__file__).resolve().parent.parent


def get(url, tentativas=4):
    for i in range(tentativas):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40) as r:
                return r.read()
        except Exception:
            if i == tentativas - 1:
                return None
            time.sleep(2 * (i + 1))


def yahoo(ticker):
    p1 = int(dt.datetime(1994, 12, 1).timestamp()); p2 = int(time.time())
    b = get(f"https://query1.finance.yahoo.com/v8/finance/chart/{ticker}.SA?period1={p1}&period2={p2}&interval=1mo&events=split")
    if not b:
        return None, []
    try:
        r = json.loads(b)["chart"]["result"][0]
    except Exception:
        return None, []
    precos = {}
    fech = (r.get("indicators", {}).get("quote") or [{}])[0].get("close") or []
    for t, c in zip(r.get("timestamp") or [], fech):
        if c and c > 0:
            d = dt.datetime.fromtimestamp(t - 10800 + 2 * 86400, dt.timezone.utc)
            precos[f"{d.year}-{d.month:02d}"] = c
    splits = [(dt.date.fromtimestamp(v["date"]), v["numerator"] / v["denominator"]) for v in (r.get("events", {}).get("splits") or {}).values()]
    return precos, splits


def fundamentus(ticker, fii):
    b = get(f"https://www.fundamentus.com.br/{'fii_' if fii else ''}proventos.php?papel={ticker}&tipo=2")
    if not b:
        return []
    out = []
    for linha in re.findall(r"<tr[^>]*>(.*?)</tr>", b.decode("latin1"), re.S):
        c = [re.sub(r"<[^>]+>", "", x).strip() for x in re.findall(r"<td[^>]*>(.*?)</td>", linha, re.S)]
        try:
            if fii and len(c) == 4:
                d, tipo, v, por = c[0], c[1], c[3], "1"
            elif not fii and len(c) == 5:
                d, v, tipo, _, por = c
            else:
                continue
            data = dt.datetime.strptime(d, "%d/%m/%Y").date()
            val = float(v.replace(".", "").replace(",", ".")) / float(por.replace(".", "") or 1)
        except ValueError:
            continue
        if "JRS" in tipo.upper() or "JUROS" in tipo.upper():
            val *= 0.85
        out.append((data, val))
    return out


def retorno_total(precos, splits, prov):
    meses = sorted(precos)
    pos = {m: i for i, m in enumerate(meses)}
    rend = {}
    for d, v in prov:
        ex = d + dt.timedelta(days=1); k = f"{ex.year}-{ex.month:02d}"
        i = pos.get(k)
        if not i:
            continue
        fator = 1.0
        for ds, razao in splits:
            if ds > d:
                fator *= razao
        rend[k] = rend.get(k, 0) + v / (precos[meses[i - 1]] * fator)
    out = {}
    for a, b in zip(meses, meses[1:]):
        y1, m1 = map(int, a.split("-")); y2, m2 = map(int, b.split("-"))
        if (y2 * 12 + m2) - (y1 * 12 + m1) != 1:
            continue
        r = precos[b] / precos[a] * (1 + rend.get(b, 0)) - 1
        if -0.6 < r < 1.0:
            out[b] = r
    primeiro_prov = min((d for d, _ in prov), default=None)
    return out, primeiro_prov


def main():
    cache = Path(sys.argv[1]) if len(sys.argv) > 1 else BASE_DIR / "tools" / "cache"
    p = BASE_DIR / "data.js"
    pre, js = p.read_text(encoding="utf-8").split("window.DADOS = ", 1)
    D = json.loads(js.rstrip().rstrip(";"))
    base_y, base_m = map(int, D["base"].split("-"))
    i_base = base_y * 12 + base_m - 1
    nome = lambda i: f"{i // 12:04d}-{i % 12 + 1:02d}"

    candidatos = [a for a in D["ativos"] if a[2] in ("acao", "fii", "fiagro", "fiinfra")]

    def um(a):
        t, tipo = a[0], a[2]
        s = D["series"][t]
        ini_tv = i_base + s[0]
        precos, splits = yahoo(t)
        if not precos:
            return t, None, "sem Yahoo"
        prim_y = min(precos)
        if (int(prim_y[:4]) * 12 + int(prim_y[5:]) - 1) > ini_tv - 6:
            return t, None, "Yahoo não tem histórico anterior"
        prov = fundamentus(t, tipo != "acao")
        rets, prim_prov = retorno_total(precos, splits, prov)
        if prim_prov is None:
            return t, None, "sem proventos no Fundamentus"
        # sobreposição com o TradingView
        vals = s[1].split(",")
        tv = {nome(ini_tv + i): int(v) / 10000 for i, v in enumerate(vals) if v != ""}
        comum = [k for k in tv if k in rets]
        if len(comum) < 24:
            return t, None, f"só {len(comum)} meses em comum"
        x = np.array([tv[k] for k in comum]); y = np.array([rets[k] for k in comum])
        corr = np.corrcoef(x, y)[0, 1]
        dif = (np.exp(np.log1p(y).mean() * 12) - np.exp(np.log1p(x).mean() * 12))
        if corr < 0.85 or abs(dif) > 0.03:
            return t, None, f"fontes não batem (corr {corr:.2f}, dif {dif:+.1%})"
        # meses novos: antes do TradingView, depois do 1º provento conhecido (FII) ou 12 meses antes (ação)
        lim = prim_prov.year * 12 + prim_prov.month - 1 - (12 if tipo == "acao" else 0)
        novos = {k: v for k, v in rets.items() if lim <= int(k[:4]) * 12 + int(k[5:]) - 1 < ini_tv and k >= D["base"]}
        if not novos:
            return t, None, "nada a acrescentar"
        return t, (novos, corr, dif), "ok"

    feitos, relatorio = 0, []
    with ThreadPoolExecutor(max_workers=4) as ex:
        for t, res, msg in ex.map(um, candidatos):
            if not res:
                continue
            novos, corr, dif = res
            s = D["series"][t]
            ini_tv = i_base + s[0]
            i_novo = min(int(k[:4]) * 12 + int(k[5:]) - 1 for k in novos)
            antes = [str(int(round(novos[nome(i)] * 10000))) if nome(i) in novos else "" for i in range(i_novo, ini_tv)]
            s[1] = ",".join(antes) + "," + s[1]
            s[0] = i_novo - i_base
            feitos += 1
            relatorio.append((t, len(novos), round(corr, 2), round(dif, 4)))
    D.setdefault("complementos", {})
    for t, n, c, d in relatorio:
        D["complementos"][t] = n
    p.write_text(pre + "window.DADOS = " + json.dumps(D, ensure_ascii=False, separators=(",", ":")) + ";\n", encoding="utf-8")
    relatorio.sort(key=lambda x: -x[1])
    print(f"OK: {feitos} ativos ganharam histórico real anterior (Yahoo + Fundamentus)")
    for t, n, c, d in relatorio[:40]:
        print(f"  {t:8s} +{n:3d} meses  (correlação na sobreposição {c}, dif. retorno {d:+.2%})")


if __name__ == "__main__":
    main()
