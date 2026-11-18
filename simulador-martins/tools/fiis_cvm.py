#!/usr/bin/env python3
"""Enriquece os FIIs com o informe mensal oficial da CVM (dados abertos, desde 2016).

Uso: python3 tools/fiis_cvm.py PASTA_CACHE   ->  PASTA_CACHE/fiis.json

Para cada FII (ligado ao ticker pelo código ISIN, ex.: BRMXRFCTF008 -> MXRF):
- mandato: Títulos e Valores Mobiliários (papel), Renda (tijolo), Híbrido, Desenvolvimento
- segmento: Títulos e Val. Mob., Logística, Shoppings, Lajes Corporativas, Híbrido...
  (usa a classificação mais recente que não seja vazia nem "Multicategoria": depois da
   Resolução CVM 175 muitos fundos passaram a vir sem mandato ou como "Multicategoria")
- início de funcionamento, patrimônio líquido, número de cotistas, valor patrimonial da cota,
  dividend yield dos últimos 12 meses e taxa de administração anualizada.
"""
import csv, io, json, sys, time, urllib.request, zipfile, datetime as dt
from pathlib import Path

URL = "https://dados.cvm.gov.br/dados/FII/DOC/INF_MENSAL/DADOS/inf_mensal_fii_{ano}.zip"


def baixar(url, destino):
    if destino.exists() and destino.stat().st_size > 0 and str(dt.date.today().year) not in destino.name:
        return destino
    for i in range(5):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=120) as r:
                destino.write_bytes(r.read())
            return destino
        except Exception:
            if i == 4:
                raise
            time.sleep(4 * (i + 1))


def ler(z, prefixo):
    for nome in z.namelist():
        if nome.startswith(prefixo):
            with z.open(nome) as f:
                yield from csv.DictReader(io.TextIOWrapper(f, encoding="latin1"), delimiter=";")


def f(x):
    try:
        return float(x)
    except (TypeError, ValueError):
        return None


def main():
    cache = Path(sys.argv[1]); cache.mkdir(parents=True, exist_ok=True)
    cnpj_isin, cad, mensal = {}, {}, {}
    for ano in range(2016, dt.date.today().year + 1):
        z = zipfile.ZipFile(baixar(URL.format(ano=ano), cache / f"inf_mensal_fii_{ano}.zip"))
        for r in ler(z, "inf_mensal_fii_geral"):
            cnpj = r["CNPJ_Fundo_Classe"] if "CNPJ_Fundo_Classe" in r else r.get("CNPJ_Fundo")
            isin = (r.get("Codigo_ISIN") or "").strip()
            if not cnpj or len(isin) < 6:
                continue
            cnpj_isin[cnpj] = isin
            d = r["Data_Referencia"]
            c = cad.setdefault(isin[2:6], {"mandato": "", "segmento": "", "d": "", "nome": "", "inicio": ""})
            if d >= c["d"]:
                c["d"] = d; c["nome"] = r.get("Nome_Fundo_Classe") or r.get("Nome_Fundo") or c["nome"]
                c["inicio"] = r.get("Data_Funcionamento") or c["inicio"]
            m, s = (r.get("Mandato") or "").strip(), (r.get("Segmento_Atuacao") or "").strip()
            if m and d >= c.get("dm", ""):
                c["mandato"] = m; c["dm"] = d
            if s and s != "Multicategoria" and d >= c.get("ds", ""):
                c["segmento"] = s; c["ds"] = d
        for r in ler(z, "inf_mensal_fii_complemento"):
            cnpj = r["CNPJ_Fundo_Classe"] if "CNPJ_Fundo_Classe" in r else r.get("CNPJ_Fundo")
            isin = cnpj_isin.get(cnpj)
            if not isin:
                continue
            mensal.setdefault(isin[2:6], {})[r["Data_Referencia"][:7]] = {
                "pl": f(r.get("Patrimonio_Liquido")), "vp": f(r.get("Valor_Patrimonial_Cotas")),
                "cot": f(r.get("Total_Numero_Cotistas")), "dy": f(r.get("Percentual_Dividend_Yield_Mes")),
                "adm": f(r.get("Percentual_Despesas_Taxa_Administracao")),
            }
        print(f"  {ano}: ok ({len(cad)} FIIs)", flush=True)

    saida = {}
    for raiz, c in cad.items():
        m = mensal.get(raiz, {})
        meses = sorted(k for k, v in m.items() if v["vp"])
        if not meses:
            continue
        ult = m[meses[-1]]
        ult12 = [m[k] for k in meses[-12:]]
        dy12 = sum(x["dy"] or 0 for x in ult12) if len(ult12) == 12 else None
        adm = sum(x["adm"] or 0 for x in ult12) * 12 / len(ult12) if ult12 else None
        mand = c["mandato"]
        papel = mand == "Títulos e Valores Mobiliários" or c["segmento"] == "Títulos e Val. Mob."
        saida[raiz] = {
            "mandato": mand, "segmento": c["segmento"], "papel": papel, "inicio": c["inicio"],
            "pl": ult["pl"], "vp": ult["vp"], "cotistas": int(ult["cot"] or 0), "dy12": dy12,
            "adm": adm, "ref": meses[-1], "meses_cvm": len(meses),
        }
    (cache / "fiis.json").write_text(json.dumps(saida, ensure_ascii=False), encoding="utf-8")
    print(f"OK: {len(saida)} FIIs com dados da CVM", flush=True)


if __name__ == "__main__":
    main()
