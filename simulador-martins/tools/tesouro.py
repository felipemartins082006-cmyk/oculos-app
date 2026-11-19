#!/usr/bin/env python3
"""Títulos do Tesouro Direto a partir dos preços históricos oficiais (Tesouro Transparente).

Uso: python3 tools/tesouro.py PASTA_CACHE   ->  PASTA_CACHE/tesouro.json

Usa o PU de venda (preço de recompra pelo Tesouro, marcado a mercado) do último dia de cada mês.
Só entram títulos sem cupom (Selic, IPCA+, Prefixado, Renda+ e Educa+) ainda não vencidos: neles o
preço já é o retorno total. Os "com juros semestrais" ficam de fora porque o arquivo não traz o valor
dos cupons pagos (sem ele o retorno ficaria errado, e não estimamos dados).
"""
import csv, io, json, sys, time, urllib.request, datetime as dt
from pathlib import Path

URL = ("https://www.tesourotransparente.gov.br/ckan/dataset/df56aa42-484a-4a59-8184-7676580c81e3/"
       "resource/796d2059-14e9-44e3-80c9-2d9e30b405c1/download/PrecoTaxaTesouroDireto.csv")
TIPOS = {"Tesouro Selic": ("SELIC", "Tesouro Selic"), "Tesouro IPCA+": ("IPCA", "Tesouro IPCA+"),
         "Tesouro Prefixado": ("PRE", "Tesouro Prefixado"), "Tesouro Renda+ Aposentadoria Extra": ("RENDA", "Tesouro Renda+"),
         "Tesouro Educa+": ("EDUCA", "Tesouro Educa+")}


def baixar():
    for i in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(URL, headers={"User-Agent": "Mozilla/5.0"}), timeout=300) as r:
                return r.read().decode("latin1")
        except Exception:
            if i == 4:
                raise
            time.sleep(5 * (i + 1))


def main():
    cache = Path(sys.argv[1]); cache.mkdir(parents=True, exist_ok=True)
    hoje = dt.date.today()
    mes_atual = f"{hoje.year:04d}-{hoje.month:02d}"
    titulos = {}
    for row in csv.DictReader(io.StringIO(baixar()), delimiter=";"):
        tipo = TIPOS.get(row["Tipo Titulo"])
        if not tipo:
            continue
        d, m, y = row["Data Vencimento"].split("/")
        venc = dt.date(int(y), int(m), int(d))
        if venc <= hoje:
            continue
        bd, bm, by = row["Data Base"].split("/")
        mes = f"{by}-{bm}"
        pu = float((row["PU Venda Manha"] or "0").replace(",", "."))
        if pu <= 0 or mes >= mes_atual:
            continue
        cod = f"{tipo[0]}{venc.year}" + (f"-{venc.month:02d}" if tipo[0] == "IPCA" and venc.month != 8 else "")
        t = titulos.setdefault(cod, {"cod": cod, "nome": f"{tipo[1]} {venc.strftime('%m/%Y')}", "venc": venc.isoformat(),
                                     "tipo": tipo[1], "_m": {}})
        atual = t["_m"].get(mes)
        if atual is None or int(bd) > atual[0]:
            t["_m"][mes] = (int(bd), pu)
    saida = []
    for t in titulos.values():
        t["cotas"] = {m: v[1] for m, v in sorted(t.pop("_m").items())}
        if len(t["cotas"]) >= 4:
            saida.append(t)
    saida.sort(key=lambda t: (t["tipo"], t["venc"]))
    (cache / "tesouro.json").write_text(json.dumps(saida, ensure_ascii=False), encoding="utf-8")
    print(f"OK: {len(saida)} títulos: " + ", ".join(f"{t['cod']} (desde {min(t['cotas'])})" for t in saida), flush=True)


if __name__ == "__main__":
    main()
