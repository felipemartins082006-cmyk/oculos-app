#!/usr/bin/env python3
"""Junta os dados do informe mensal de FIIs da CVM (tools/fiis_cvm.py) ao data.js.

Uso: python3 tools/enriquecer.py PASTA_CACHE

Grava em DADOS.fii: {ticker: [mandato, segmento, dy12, p_vp, cotistas, inicio, taxa_adm, pl]}
  (dy12 vem do campo de dividend yield do informe, que é inconsistente para vários fundos: o app não o exibe)
  mandato: "papel" | "tijolo" | "hibrido" | "desenvolvimento" | ""
O preço atual vem do scanner do TradingView (para o P/VP).
"""
import json, sys, urllib.request
from pathlib import Path

MANDATO = {"Títulos e Valores Mobiliários": "papel", "Renda": "tijolo", "Híbrido": "hibrido",
           "Desenvolvimento para Renda": "desenvolvimento", "Desenvolvimento para Venda": "desenvolvimento"}
SEG_OK = {"Títulos e Val. Mob.": "Recebíveis (CRI)", "Logística": "Logística", "Shoppings": "Shoppings",
          "Lajes Corporativas": "Lajes corporativas", "Escritórios": "Escritórios", "Residencial": "Residencial",
          "Hospital": "Hospital", "Hotel": "Hotel", "Varejo": "Varejo", "Educacional": "Educacional", "Agências": "Agências"}


def precos_b3():
    corpo = {"columns": ["name", "close"], "filter": [{"left": "type", "operation": "in_range", "right": ["fund", "stock"]}], "range": [0, 5000]}
    req = urllib.request.Request("https://scanner.tradingview.com/brazil/scan", data=json.dumps(corpo).encode(),
                                 headers={"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return {x["d"][0]: x["d"][1] for x in json.load(r)["data"]}


def enriquecer(base, cache):
    p = base / "data.js"
    pre, js = p.read_text(encoding="utf-8").split("window.DADOS = ", 1)
    D = json.loads(js.rstrip().rstrip(";"))
    fiis = json.loads((cache / "fiis.json").read_text(encoding="utf-8"))
    try:
        precos = precos_b3()
    except Exception:
        precos = {}
    out = {}
    for a in D["ativos"]:
        if a[2] not in ("fii", "fiagro", "fiinfra"):
            continue
        f = fiis.get(a[0][:4])
        if not f:
            continue
        mand = "papel" if f["papel"] else MANDATO.get(f["mandato"], "")
        seg = SEG_OK.get(f["segmento"], "")
        if mand == "papel":
            seg = "Recebíveis (CRI)"
        elif mand == "hibrido":
            seg = ""  # segmento de fundos híbridos vem inconsistente na CVM
        preco = precos.get(a[0])
        pvp = round(preco / f["vp"], 3) if preco and f["vp"] else None
        out[a[0]] = [mand, seg, round(f["dy12"], 4) if f["dy12"] else None, pvp, f["cotistas"], f["inicio"],
                     round(f["adm"], 4) if f["adm"] else None, round(f["pl"] or 0)]
        if seg:
            a[3] = f"FII · {seg}" if a[2] == "fii" else a[3]
        elif mand:
            a[3] = {"tijolo": "FII · Tijolo", "hibrido": "FII · Híbrido", "desenvolvimento": "FII · Desenvolvimento"}.get(mand, a[3]) if a[2] == "fii" else a[3]
    D["fii"] = out
    p.write_text(pre + "window.DADOS = " + json.dumps(D, ensure_ascii=False, separators=(",", ":")) + ";\n", encoding="utf-8")
    return out


if __name__ == "__main__":
    base = Path(__file__).resolve().parent.parent
    out = enriquecer(base, Path(sys.argv[1]))
    from collections import Counter
    print(f"OK: {len(out)} FIIs enriquecidos", Counter(v[0] for v in out.values()))
