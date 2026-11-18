#!/usr/bin/env python3
"""Baixa o histórico mensal dos ativos e gera ../data.js para o Simulador Martins.

Uso (no computador, com internet):  python3 tools/baixar_dados.py

- Preço: Yahoo Finance (fechamento mensal, já ajustado por desdobramentos e bonificações).
- Proventos de ações e FIIs: Fundamentus. O Yahoo perde muitos proventos (FIIs antes de 2022,
  Itaú sem nada em 2024-25 etc.), então o retorno total é montado aqui:
      retorno(m) = preço(m)/preço(m-1) × (1 + proventos com data-ex no mês / preço do mês anterior) - 1
  JCP entra líquido de 15% de IR. ETFs (BOVA11, IVVB11...) reinvestem sozinhos: só o preço.
- CDI e IPCA: Banco Central (SGS, séries 4391 e 433), em % ao mês.

Para incluir um ativo novo, basta acrescentar na lista ATIVOS e rodar de novo.
O campo "mercado" já prepara a abertura para os EUA (mercado "US", moeda USD).
"""
import json, math, re, sys, time, urllib.request, datetime as dt
from pathlib import Path

INICIO = 2010  # primeiro ano usado (antes disso o Yahoo tem muitos dados quebrados)

# (ticker, nome curto, tipo, setor)
ATIVOS = [
    # ---- Ações
    ("PETR4", "Petrobras PN", "acao", "Petróleo"),
    ("PETR3", "Petrobras ON", "acao", "Petróleo"),
    ("PRIO3", "PetroRio", "acao", "Petróleo"),
    ("VBBR3", "Vibra Energia", "acao", "Petróleo"),
    ("UGPA3", "Ultrapar", "acao", "Petróleo"),
    ("CSAN3", "Cosan", "acao", "Petróleo"),
    ("VALE3", "Vale", "acao", "Mineração"),
    ("GGBR4", "Gerdau", "acao", "Siderurgia"),
    ("CSNA3", "CSN", "acao", "Siderurgia"),
    ("SUZB3", "Suzano", "acao", "Papel e celulose"),
    ("KLBN11", "Klabin", "acao", "Papel e celulose"),
    ("ITUB4", "Itaú Unibanco", "acao", "Bancos"),
    ("BBDC4", "Bradesco", "acao", "Bancos"),
    ("BBAS3", "Banco do Brasil", "acao", "Bancos"),
    ("SANB11", "Santander", "acao", "Bancos"),
    ("BPAC11", "BTG Pactual", "acao", "Bancos"),
    ("ITSA4", "Itaúsa", "acao", "Bancos"),
    ("B3SA3", "B3", "acao", "Financeiro"),
    ("BBSE3", "BB Seguridade", "acao", "Seguros"),
    ("CXSE3", "Caixa Seguridade", "acao", "Seguros"),
    ("AXIA3", "Axia Energia (ex-Eletrobras)", "acao", "Energia"),
    ("EGIE3", "Engie Brasil", "acao", "Energia"),
    ("TAEE11", "Taesa", "acao", "Energia"),
    ("CMIG4", "Cemig", "acao", "Energia"),
    ("EQTL3", "Equatorial", "acao", "Energia"),
    ("CPLE3", "Copel", "acao", "Energia"),
    ("ISAE4", "ISA Energia (Transmissão Paulista)", "acao", "Energia"),
    ("SBSP3", "Sabesp", "acao", "Saneamento"),
    ("SAPR11", "Sanepar", "acao", "Saneamento"),
    ("VIVT3", "Vivo (Telefônica)", "acao", "Telecom"),
    ("TIMS3", "TIM", "acao", "Telecom"),
    ("WEGE3", "WEG", "acao", "Indústria"),
    ("EMBJ3", "Embraer", "acao", "Indústria"),
    ("RAIL3", "Rumo", "acao", "Logística"),
    ("RENT3", "Localiza", "acao", "Aluguel de carros"),
    ("ABEV3", "Ambev", "acao", "Bebidas"),
    ("RADL3", "Raia Drogasil", "acao", "Saúde"),
    ("HYPE3", "Hypera", "acao", "Saúde"),
    ("LREN3", "Lojas Renner", "acao", "Varejo"),
    ("MGLU3", "Magazine Luiza", "acao", "Varejo"),
    ("TOTS3", "Totvs", "acao", "Tecnologia"),
    ("CYRE3", "Cyrela", "acao", "Construção"),
    ("MULT3", "Multiplan", "acao", "Shoppings"),
    ("ALOS3", "Allos", "acao", "Shoppings"),
    # ---- Fundos imobiliários
    ("MXRF11", "Maxi Renda", "fii", "Papel (recebíveis)"),
    ("KNCR11", "Kinea Rendimentos", "fii", "Papel (recebíveis)"),
    ("KNIP11", "Kinea Índices de Preços", "fii", "Papel (recebíveis)"),
    ("CPTS11", "Capitânia Securities", "fii", "Papel (recebíveis)"),
    ("RECR11", "REC Recebíveis", "fii", "Papel (recebíveis)"),
    ("HCTR11", "Hectare", "fii", "Papel (recebíveis)"),
    ("VGIP11", "Valora IP", "fii", "Papel (recebíveis)"),
    ("MCCI11", "Mauá Capital", "fii", "Papel (recebíveis)"),
    ("RBRR11", "RBR Rendimento High Grade", "fii", "Papel (recebíveis)"),
    ("KNSC11", "Kinea Securities", "fii", "Papel (recebíveis)"),
    ("HGLG11", "CSHG Logística", "fii", "Logística"),
    ("BTLG11", "BTG Logística", "fii", "Logística"),
    ("XPLG11", "XP Log", "fii", "Logística"),
    ("VILG11", "Vinci Logística", "fii", "Logística"),
    ("LVBI11", "VBI Logístico", "fii", "Logística"),
    ("XPML11", "XP Malls", "fii", "Shoppings"),
    ("VISC11", "Vinci Shopping Centers", "fii", "Shoppings"),
    ("HGBS11", "Hedge Brasil Shopping", "fii", "Shoppings"),
    ("HSML11", "HSI Malls", "fii", "Shoppings"),
    ("KNRI11", "Kinea Renda Imobiliária", "fii", "Híbrido"),
    ("ALZR11", "Alianza Trust", "fii", "Híbrido"),
    ("TRXF11", "TRX Real Estate", "fii", "Varejo / renda urbana"),
    ("HGRU11", "CSHG Renda Urbana", "fii", "Varejo / renda urbana"),
    ("HGRE11", "CSHG Real Estate", "fii", "Lajes corporativas"),
    ("PVBI11", "VBI Prime Properties", "fii", "Lajes corporativas"),
    ("JSRE11", "JS Real Estate", "fii", "Lajes corporativas"),
    ("BRCR11", "BTG Corporate Office", "fii", "Lajes corporativas"),
    ("RBRF11", "RBR Alpha (fundo de fundos)", "fii", "Fundo de fundos"),
    ("GGRC11", "GGR Covepi", "fii", "Logística"),
    ("TGAR11", "TG Ativo Real", "fii", "Desenvolvimento"),
    ("VGHF11", "Valora Hedge Fund", "fii", "Papel (recebíveis)"),
    ("BTCI11", "BTG Crédito Imobiliário", "fii", "Papel (recebíveis)"),
    # ---- ETFs e índices
    ("BOVA11", "ETF Ibovespa", "etf", "Índice amplo"),
    ("SMAL11", "ETF Small Caps", "etf", "Índice amplo"),
    ("DIVO11", "ETF Dividendos", "etf", "Índice amplo"),
    ("IVVB11", "ETF S&P 500 (em reais)", "etf", "Exterior"),
    ("^BVSP", "Ibovespa (índice, sem dividendos)", "indice", "Índice amplo"),
]

UA = {"User-Agent": "Mozilla/5.0"}

# Ações que de fato não pagavam proventos antes do 1º registro (não cortar o histórico delas).
# As outras com histórico curto no Fundamentus mudaram de ticker (TIMP3->TIMS3, RUMO3->RAIL3...).
SEM_CORTE = {"PRIO3"}


def get_json(url, tentativas=4):
    for i in range(tentativas):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception as e:  # rede instável: tenta de novo
            if i == tentativas - 1:
                raise
            time.sleep(2 * (i + 1))


def mes_atual():
    hoje = dt.date.today()
    return f"{hoje.year:04d}-{hoje.month:02d}"


def yahoo_mensal(ticker):
    """Preço mensal (ajustado só por desdobramentos), preço ajustado por tudo e desdobramentos."""
    sym = ticker if ticker.startswith("^") else ticker + ".SA"
    p1 = int(dt.datetime(INICIO - 1, 12, 1).timestamp())
    p2 = int(time.time())
    url = (f"https://query1.finance.yahoo.com/v8/finance/chart/{urllib.request.quote(sym)}"
           f"?period1={p1}&period2={p2}&interval=1mo&events=div,split")
    res = get_json(url)["chart"]["result"][0]
    ts = res.get("timestamp") or []
    ind = res["indicators"]
    close = ind["quote"][0]["close"]
    adj = (ind.get("adjclose") or [{}])[0].get("adjclose") or close
    gmt = res["meta"].get("gmtoffset", -10800)
    preco, ajustado = {}, {}
    for t, c, a in zip(ts, close, adj):
        if c is None or c <= 0:
            continue
        # As barras mensais começam no dia 1º, mas com o antigo horário de verão algumas caem
        # às 23h do último dia do mês anterior. Somar 2 dias põe cada barra no mês certo.
        d = dt.datetime.fromtimestamp(t + gmt + 2 * 86400, dt.timezone.utc)
        k = f"{d.year:04d}-{d.month:02d}"
        preco[k] = c
        ajustado[k] = a if a else c
    splits = [(dt.date.fromtimestamp(v["date"]), v["numerator"] / v["denominator"])
              for v in (res.get("events", {}).get("splits") or {}).values()]
    return preco, ajustado, splits, res["meta"].get("regularMarketPrice")


def fundamentus_proventos(ticker, fii):
    """Lista de (data-com, valor por cota/ação como foi pago). JCP já líquido de 15% de IR."""
    url = f"https://www.fundamentus.com.br/{'fii_' if fii else ''}proventos.php?papel={ticker}&tipo=2"
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=30) as r:
        html = r.read().decode("latin1")
    out = []
    for linha in re.findall(r"<tr[^>]*>(.*?)</tr>", html, re.S):
        c = [re.sub(r"<[^>]+>", "", x).strip() for x in re.findall(r"<td[^>]*>(.*?)</td>", linha, re.S)]
        if fii and len(c) == 4:
            data, tipo, valor, por = c[0], c[1], c[3], "1"
        elif not fii and len(c) == 5:
            data, valor, tipo, _, por = c
        else:
            continue
        try:
            d = dt.datetime.strptime(data, "%d/%m/%Y").date()
            v = float(valor.replace(".", "").replace(",", ".")) / float(por.replace(".", "") or 1)
        except ValueError:
            continue
        if "JRS" in tipo.upper() or "JUROS" in tipo.upper():
            v *= 0.85
        out.append((d, v))
    return out


def com_proventos(preco, splits, proventos):
    """Preço mensal -> índice de retorno total (proventos reinvestidos no mês da data-ex)."""
    meses = sorted(preco)
    idx = {m: i for i, m in enumerate(meses)}
    rendimento = {}
    for d, v in proventos:
        ex = d + dt.timedelta(days=1)
        k = f"{ex.year:04d}-{ex.month:02d}"
        i = idx.get(k)
        if not i:
            continue
        # o Yahoo dá o preço já dividido pelos desdobramentos futuros; o provento está como foi pago
        fator = 1.0
        for ds, razao in splits:
            if ds > d:
                fator *= razao
        rendimento[k] = rendimento.get(k, 0) + v / (preco[meses[i - 1]] * fator)
    total, nivel = {}, 1.0
    for i, m in enumerate(meses):
        if i:
            nivel *= preco[m] / preco[meses[i - 1]] * (1 + rendimento.get(m, 0))
        total[m] = nivel
    return total


def retornos(por_mes):
    """Retornos mensais por mês ("AAAA-MM" -> retorno). Meses com dado quebrado ficam de fora.

    O mês corrente (incompleto) não entra. Como a simulação sorteia meses avulsos do histórico,
    descartar um mês ruim não estraga o resto da série.
    """
    meses = sorted(m for m in por_mes if m < mes_atual())
    out = {}
    def consecutivos(a, b):
        ya, ma = map(int, a.split("-")); yb, mb = map(int, b.split("-"))
        return (yb * 12 + mb) - (ya * 12 + ma) == 1
    for i in range(1, len(meses)):
        a, b = meses[i - 1], meses[i]
        if not consecutivos(a, b):
            continue
        r = por_mes[b] / por_mes[a] - 1
        if r > 0.9 or r < -0.75:  # desdobramento/grupamento não ajustado
            print(f"    descarta {b}: {r:+.0%}", file=sys.stderr)
            continue
        # pico isolado: sobe muito e volta no mês seguinte (ou o contrário) = preço errado num mês
        if i + 1 < len(meses) and consecutivos(b, meses[i + 1]):
            l1 = math.log(por_mes[b] / por_mes[a]); l2 = math.log(por_mes[meses[i + 1]] / por_mes[b])
            if abs(l1) > 0.5 and abs(l2) > 0.5 and l1 * l2 < 0 and abs(l1 + l2) < 0.3:
                print(f"    descarta pico em {b}: {r:+.0%} e volta", file=sys.stderr)
                out["_pula"] = meses[i + 1]
                continue
        if out.get("_pula") == b:
            del out["_pula"]
            continue
        out[b] = r
    out.pop("_pula", None)
    # sequências de 3+ meses com retorno exatamente zero = ativo sem negociação no Yahoo
    ks = sorted(out)
    i = 0
    while i < len(ks):
        j = i
        while j < len(ks) and out[ks[j]] == 0:
            j += 1
        if j - i >= 3:
            for k in ks[i:j]:
                del out[k]
        i = max(j, i + 1)
    return out


def mes_seguinte(m):
    y, mm = map(int, m.split("-"))
    y, mm = (y + 1, 1) if mm == 12 else (y, mm + 1)
    return f"{y:04d}-{mm:02d}"


def serie_alinhada(d, casas):
    """{'inicio': primeiro mês, 'r': [retorno ou null, mês a mês]}"""
    meses = sorted(d)
    lista, m = [], meses[0]
    while m <= meses[-1]:
        lista.append(round(d[m], casas) if m in d else None)
        m = mes_seguinte(m)
    return {"inicio": meses[0], "r": lista}


def bcb_mensal(serie):
    url = f"https://api.bcb.gov.br/dados/serie/bcdata.sgs.{serie}/dados?formato=json&dataInicial=01/01/{INICIO}"
    out = {}
    for row in get_json(url):
        d, m, y = row["data"].split("/")
        k = f"{y}-{m}"
        if k < mes_atual():
            out[k] = float(row["valor"]) / 100
    return out


def main():
    base = Path(__file__).resolve().parent.parent
    series = {}
    ativos = []
    for tk, nome, tipo, setor in ATIVOS:
        try:
            preco, ajustado, splits, ultimo = yahoo_mensal(tk)
            if tipo in ("acao", "fii"):
                prov = fundamentus_proventos(tk, tipo == "fii")
                if not prov:
                    raise ValueError("sem proventos no Fundamentus")
                serie = com_proventos(preco, splits, prov)
                # Sem histórico de proventos antes da 1ª data, o retorno sairia baixo demais.
                # FII paga todo mês: começa no 1º provento. Ação: até 12 meses antes dele.
                primeiro = min(d for d, _ in prov)
                y, m = primeiro.year, primeiro.month
                if tipo == "acao":
                    y -= 1
                corte = f"{y:04d}-{m:02d}"
                if tk not in SEM_CORTE:
                    serie = {k: v for k, v in serie.items() if k >= corte}
            else:
                serie = ajustado
            rets = retornos(serie)
        except Exception as e:
            print(f"  ! {tk}: {e}", file=sys.stderr)
            continue
        if len(rets) < 24:
            print(f"  ! {tk}: só {len(rets)} meses, ignorado", file=sys.stderr)
            continue
        meses = sorted(rets)
        cod = tk.replace("^BVSP", "IBOV")
        series[cod] = serie_alinhada(rets, 5)
        ativos.append({"t": cod, "n": nome, "tipo": tipo, "setor": setor, "mercado": "BR",
                       "moeda": "BRL", "preco": round(ultimo, 2) if ultimo else None})
        print(f"  {cod:7s} {len(meses):3d} meses desde {meses[0]}")
        time.sleep(0.3)

    for cod, serie in (("CDI", 4391), ("IPCA", 433)):
        d = bcb_mensal(serie)
        meses = sorted(d)
        series[cod] = serie_alinhada(d, 6)
        print(f"  {cod:7s} {len(meses):3d} meses desde {meses[0]}")

    data = {"atualizado": dt.date.today().isoformat(), "ativos": ativos, "series": series}
    js = ("// Gerado por tools/baixar_dados.py — não edite à mão.\n"
          "window.DADOS = " + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";\n")
    (base / "data.js").write_text(js, encoding="utf-8")
    print(f"OK: {len(ativos)} ativos, {len(js)//1024} KB em data.js")


if __name__ == "__main__":
    main()
