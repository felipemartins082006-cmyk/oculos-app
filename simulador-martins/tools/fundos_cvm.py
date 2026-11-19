#!/usr/bin/env python3
"""Fundos de investimento (renda fixa, multimercado, ações, cambial) a partir dos dados abertos da CVM.

Uso: python3 tools/fundos_cvm.py PASTA_CACHE   ->  gera PASTA_CACHE/fundos.json

1. Escolhe os fundos: classes FIF abertas, não exclusivas, público geral ou qualificado, fora de
   previdência, com 100+ cotistas, os maiores em patrimônio de cada classificação.
2. Lê o informe diário (cota de cada dia) de 2000 até o último mês e guarda a última cota de cada mês.
   A cota já vem líquida de taxa de administração e performance: o retorno é o que o cotista recebeu
   (antes do IR e do come-cotas).
"""
import csv, io, json, re, sys, time, urllib.request, zipfile, datetime as dt
from pathlib import Path

BASE = "https://dados.cvm.gov.br/dados/FI"
INICIO = 2000
QUANTOS = {"Renda Fixa": 900, "Multimercado": 1000, "Ações": 700, "Cambial": 80}
csv.field_size_limit(10**8)


def baixar(url, destino, tentativas=5):
    if destino.exists() and destino.stat().st_size > 0:
        return destino
    for i in range(tentativas):
        try:
            tmp = destino.with_suffix(".part")
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=300) as r, open(tmp, "wb") as f:
                while True:
                    b = r.read(1 << 20)
                    if not b:
                        break
                    f.write(b)
            tmp.rename(destino)
            return destino
        except Exception as e:
            if i == tentativas - 1:
                raise
            time.sleep(5 * (i + 1))


def so_digitos(s):
    return re.sub(r"\D", "", s or "")


def nome_curto(s):
    s = s.upper()
    for a, b in [("FUNDO DE INVESTIMENTO FINANCEIRO", ""), ("FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO", "FIC"),
                 ("FUNDO DE INVESTIMENTO EM COTAS DE FUNDO DE INVESTIMENTO", "FIC"), ("FUNDO DE INVESTIMENTO", "FI"),
                 ("RESPONSABILIDADE LIMITADA", ""), ("RESP LIMITADA", ""), ("RESP. LIMITADA", ""), ("CLASSE DE INVESTIMENTO", ""),
                 ("CLASSE DE COTAS", ""), ("CRÉDITO PRIVADO", "CP"), ("CREDITO PRIVADO", "CP"), ("MULTIMERCADO", "MULTIM."),
                 ("INVESTIMENTO NO EXTERIOR", "IE"), ("REFERENCIADO", "REF."), (" DE ", " "), (" EM ", " ")]:
        s = s.replace(a, b)
    s = re.sub(r"\s+", " ", s).strip(" -")
    return s.title().replace(" Fi ", " FI ").replace(" Fic ", " FIC ").replace(" Cp", " CP").replace(" Ie", " IE").replace(" Fif", " FIF")


def ler_csv_zip(caminho):
    with zipfile.ZipFile(caminho) as z:
        for nome in z.namelist():
            if not nome.endswith(".csv"):
                continue
            with z.open(nome) as f:
                yield from csv.DictReader(io.TextIOWrapper(f, encoding="latin1"), delimiter=";")


def main():
    cache = Path(sys.argv[1]); cache.mkdir(parents=True, exist_ok=True)
    hoje = dt.date.today()
    # último mês completo
    ultimo = (hoje.replace(day=1) - dt.timedelta(days=1))
    ult = f"{ultimo.year}{ultimo.month:02d}"

    # ---- cadastro
    reg = baixar(f"{BASE}/CAD/DADOS/registro_fundo_classe.zip", cache / "registro_fundo_classe.zip")
    classes, gestores = {}, {}
    with zipfile.ZipFile(reg) as z:
        with z.open("registro_fundo.csv") as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="latin1"), delimiter=";"):
                gestores[r["ID_Registro_Fundo"]] = (r.get("Gestor") or "").strip()
        with z.open("registro_classe.csv") as f:
            for r in csv.DictReader(io.TextIOWrapper(f, encoding="latin1"), delimiter=";"):
                if r["Situacao"] != "Em Funcionamento Normal" or "FIF" not in r["Tipo_Classe"]:
                    continue
                if r["Forma_Condominio"] != "Aberto" or r["Exclusivo"] == "S":
                    continue
                if r["Publico_Alvo"] not in ("Público Geral", "Qualificado"):
                    continue
                if r["Classificacao"] not in QUANTOS or "Previd" in (r["Classificacao_Anbima"] or ""):
                    continue
                classes[so_digitos(r["CNPJ_Classe"])] = {
                    "nome": r["Denominacao_Social"], "classe": r["Classificacao"],
                    "anbima": r["Classificacao_Anbima"] or r["Classificacao"],
                    "gestor": gestores.get(r["ID_Registro_Fundo"], ""), "publico": r["Publico_Alvo"],
                }
    print(f"cadastro: {len(classes)} classes candidatas", flush=True)

    # ---- tamanho atual (último mês)
    z = baixar(f"{BASE}/DOC/INF_DIARIO/DADOS/inf_diario_fi_{ult}.zip", cache / f"inf_diario_fi_{ult}.zip")
    atual = {}
    for r in ler_csv_zip(z):
        c = so_digitos(r.get("CNPJ_FUNDO_CLASSE") or r.get("CNPJ_FUNDO"))
        if c not in classes:
            continue
        d = r["DT_COMPTC"]
        a = atual.setdefault(c, {"d": "", "pl": 0.0, "cot": 0})
        if d > a["d"]:
            a.update(d=d, pl=0.0, cot=0)
        if d == a["d"]:
            a["pl"] += float(r["VL_PATRIM_LIQ"] or 0)
            a["cot"] += int(float(r["NR_COTST"] or 0))
    escolhidos = {}
    for cl, n in QUANTOS.items():
        cand = [(a["pl"], c) for c, a in atual.items() if classes[c]["classe"] == cl and a["cot"] >= 20 and a["pl"] >= 5e6]
        for pl, c in sorted(cand, reverse=True)[:n]:
            escolhidos[c] = {**classes[c], "pl": pl, "cotistas": atual[c]["cot"]}
    print(f"escolhidos: {len(escolhidos)} fundos", flush=True)

    # ---- histórico: última cota de cada mês (linha sem subclasse; senão a primeira subclasse)
    cotas = {c: {} for c in escolhidos}
    arquivos = [(f"{BASE}/DOC/INF_DIARIO/DADOS/HIST/inf_diario_fi_{a}.zip", f"inf_diario_fi_{a}.zip") for a in range(INICIO, 2021)]
    m = dt.date(2021, 1, 1)
    while f"{m.year}{m.month:02d}" <= ult:
        k = f"{m.year}{m.month:02d}"
        arquivos.append((f"{BASE}/DOC/INF_DIARIO/DADOS/inf_diario_fi_{k}.zip", f"inf_diario_fi_{k}.zip"))
        m = (m.replace(day=28) + dt.timedelta(days=4)).replace(day=1)
    for url, nome in arquivos:
        caminho = baixar(url, cache / nome)
        ultimo_dia = {}
        for r in ler_csv_zip(caminho):
            c = so_digitos(r.get("CNPJ_FUNDO_CLASSE") or r.get("CNPJ_FUNDO"))
            if c not in cotas:
                continue
            sub = (r.get("ID_SUBCLASSE") or "").strip()
            d = r["DT_COMPTC"]; mes = d[:7]
            q = float(r["VL_QUOTA"] or 0)
            if q <= 0:
                continue
            chave = (c, mes)
            atual_ = ultimo_dia.get(chave)
            # prioridade: dia mais recente; no mesmo dia, linha sem subclasse
            if atual_ is None or d > atual_[0] or (d == atual_[0] and sub == "" and atual_[1] != ""):
                ultimo_dia[chave] = (d, sub, q)
        for (c, mes), (_, _, q) in ultimo_dia.items():
            cotas[c][mes] = q
        print(f"  {nome}: ok", flush=True)
        if caminho.stat().st_size > 30e6 or "HIST" in url:
            caminho.unlink()  # libera disco: os arquivos anuais são grandes

    saida = []
    for c, info in escolhidos.items():
        cq = cotas[c]
        if len(cq) < 4:
            continue
        saida.append({"cnpj": c, "nome": nome_curto(info["nome"]), "classe": info["classe"], "anbima": info["anbima"],
                      "gestor": info["gestor"], "pl": round(info["pl"]), "cotistas": info["cotistas"], "cotas": cq})
    (cache / "fundos.json").write_text(json.dumps(saida, ensure_ascii=False), encoding="utf-8")
    print(f"OK: {len(saida)} fundos com 2+ anos de histórico", flush=True)


if __name__ == "__main__":
    main()
