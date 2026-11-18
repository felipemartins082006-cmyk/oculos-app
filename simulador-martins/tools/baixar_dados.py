#!/usr/bin/env python3
"""Baixa o histórico mensal de todo o universo de ativos e gera ../data.js.

Uso (com internet):  python3 tools/baixar_dados.py [PASTA_CACHE]
  (antes, rode tools/fundos_cvm.py PASTA_CACHE para incluir os fundos de investimento)

Fontes
- Lista de ativos: scanner do TradingView (B3 inteira com liquidez mínima, maiores ações e ETFs dos EUA).
- Histórico: TradingView, barras mensais com ajuste de dividendos (retorno total: proventos
  reinvestidos e desdobramentos ajustados). Mesmo protocolo de websocket usado pelos projetos
  open source tvdatafeed (Python) e TradingView-API (JavaScript).
- CDI e IPCA: Banco Central (SGS 4391 e 433).
- Fundos de investimento: dados abertos da CVM (ver tools/fundos_cvm.py).

Formato de saída (compacto): para cada série, o mês inicial e os retornos mensais como inteiros
(retorno × escala), separados por vírgula; vazio = mês sem dado.
"""
import json, math, random, re, string, sys, threading, time, urllib.request, datetime as dt
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

try:
    from websocket import create_connection  # pip install websocket-client
except ImportError:
    sys.exit("Instale: pip install websocket-client")

INICIO = "2000-01"
UA = {"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"}
LIQ_MIN_BR = 100_000      # R$ negociados por dia (média de 30 dias)
N_ACOES_US = 600          # maiores empresas americanas por valor de mercado
N_ETFS_US = 220

SETOR = {
    "Finance": "Financeiro", "Utilities": "Utilidade pública", "Energy Minerals": "Petróleo e gás",
    "Non-Energy Minerals": "Mineração e siderurgia", "Process Industries": "Indústria de base",
    "Producer Manufacturing": "Bens industriais", "Retail Trade": "Varejo", "Consumer Non-Durables": "Consumo básico",
    "Consumer Durables": "Bens duráveis", "Consumer Services": "Serviços ao consumidor", "Health Services": "Saúde",
    "Health Technology": "Saúde (farma e equip.)", "Technology Services": "Tecnologia (software)",
    "Electronic Technology": "Tecnologia (hardware)", "Communications": "Telecom", "Transportation": "Transporte",
    "Industrial Services": "Serviços industriais", "Commercial Services": "Serviços comerciais",
    "Distribution Services": "Distribuição", "Miscellaneous": "Diversos", "Government": "Governo",
}

# Ativos que entram sempre (alguns ETFs/índices de referência)
ETFS_US_FIXOS = ["SPY", "VOO", "IVV", "VTI", "QQQ", "DIA", "IWM", "VT", "VXUS", "VEA", "VWO", "EFA", "EEM", "ACWI",
                 "BND", "AGG", "TLT", "IEF", "SHY", "TIP", "LQD", "HYG", "EMB", "BNDX", "GLD", "IAU", "SLV", "DBC",
                 "VNQ", "SCHD", "VIG", "VYM", "DGRO", "NOBL", "XLK", "XLF", "XLE", "XLV", "XLY", "XLP", "XLI", "XLU",
                 "XLB", "XLRE", "XLC", "SMH", "SOXX", "ARKK", "MTUM", "QUAL", "USMV", "VUG", "VTV", "IJH", "IJR",
                 "EWZ", "EWJ", "FXI", "INDA", "IBIT", "VGT", "SCHG", "JEPI", "JEPQ", "SGOV", "BIL"]
EXTRAS = [  # (símbolo TradingView, código, nome, tipo, setor, mercado, moeda)
    ("BMFBOVESPA:IBOV", "IBOV", "Ibovespa", "indice", "Índice B3", "BR", "BRL"),
    ("BMFBOVESPA:IFIX", "IFIX", "IFIX (fundos imobiliários)", "indice", "Índice B3", "BR", "BRL"),
    ("BMFBOVESPA:SMLL", "SMLL", "Small Caps (SMLL)", "indice", "Índice B3", "BR", "BRL"),
    ("BMFBOVESPA:IDIV", "IDIV", "Índice Dividendos (IDIV)", "indice", "Índice B3", "BR", "BRL"),
    ("SP:SPX", "SPX", "S&P 500 (índice, sem dividendos)", "indice", "Índice EUA", "US", "USD"),
    ("NASDAQ:NDX", "NDX", "Nasdaq-100 (índice, sem dividendos)", "indice", "Índice EUA", "US", "USD"),
    ("DJ:DJI", "DJI", "Dow Jones (índice, sem dividendos)", "indice", "Índice EUA", "US", "USD"),
    ("TVC:GOLD", "OURO", "Ouro (onça, em dólar)", "commodity", "Metais", "Global", "USD"),
    ("TVC:SILVER", "PRATA", "Prata (onça, em dólar)", "commodity", "Metais", "Global", "USD"),
    ("TVC:USOIL", "PETROLEO", "Petróleo WTI (em dólar)", "commodity", "Energia", "Global", "USD"),
    ("FX_IDC:USDBRL", "USDBRL", "Dólar (USD/BRL)", "moeda", "Câmbio", "Global", "BRL"),
    ("FX_IDC:EURBRL", "EURBRL", "Euro (EUR/BRL)", "moeda", "Câmbio", "Global", "BRL"),
    ("BITSTAMP:BTCUSD", "BTC", "Bitcoin", "cripto", "Criptoativo", "Global", "USD"),
    ("BITSTAMP:ETHUSD", "ETH", "Ethereum", "cripto", "Criptoativo", "Global", "USD"),
    ("BITSTAMP:XRPUSD", "XRP", "XRP", "cripto", "Criptoativo", "Global", "USD"),
    ("BITSTAMP:LTCUSD", "LTC", "Litecoin", "cripto", "Criptoativo", "Global", "USD"),
    ("COINBASE:SOLUSD", "SOL", "Solana", "cripto", "Criptoativo", "Global", "USD"),
    ("BINANCE:BNBUSDT", "BNB", "BNB", "cripto", "Criptoativo", "Global", "USD"),
    ("COINBASE:ADAUSD", "ADA", "Cardano", "cripto", "Criptoativo", "Global", "USD"),
    ("COINBASE:DOGEUSD", "DOGE", "Dogecoin", "cripto", "Criptoativo", "Global", "USD"),
]


# ------------------------------------------------------------------ utilidades
def http_json(url, corpo=None, tentativas=4):
    for i in range(tentativas):
        try:
            dados = json.dumps(corpo).encode() if corpo is not None else None
            req = urllib.request.Request(url, data=dados, headers=UA)
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception:
            if i == tentativas - 1:
                raise
            time.sleep(3 * (i + 1))


def mes_atual():
    h = dt.date.today()
    return f"{h.year:04d}-{h.month:02d}"


def idx(m):
    y, mm = map(int, m.split("-"))
    return y * 12 + mm - 1


def nome_mes(i):
    return f"{i // 12:04d}-{i % 12 + 1:02d}"


# ------------------------------------------------------------------ universo (scanner do TradingView)
def scan(mercado, filtro, ordem, quantos, colunas):
    corpo = {"columns": colunas, "filter": filtro, "range": [0, quantos]}
    if ordem:
        corpo["sort"] = {"sortBy": ordem, "sortOrder": "desc"}
    r = http_json(f"https://scanner.tradingview.com/{mercado}/scan", corpo)
    return [dict(zip(colunas, x["d"]), s=x["s"]) for x in r["data"]]


def tipo_b3(x):
    desc = (x["description"] or "")
    spec = x["typespecs"] or []
    if x["name"].endswith("11") and re.search(r"imobili|receb[ií]veis|valora re", desc, re.I):
        return "fii"
    if re.search(r"\bunits?\b|dep[oó]sito de a[cç][oõ]es", desc, re.I):
        return "acao"
    if x["type"] == "stock":
        return "acao"
    if "etf" in spec:
        return "etf"
    d = desc.lower()
    if "agro" in d:
        return "fiagro"
    if "infra" in d:
        return "fiinfra"
    return "fii"


def limpa_nome(s):
    s = re.sub(r"\s+", " ", s or "").strip()
    s = re.sub(r"Fundo de Investimento nas Cadeias Produtivas Agroindustriais|Fundo de Investimento nas Cadeias Produtivas do Agronegocio", "Fiagro", s, flags=re.I)
    s = re.sub(r"Fundo de Investimento em Cotas de Fundos Incentivados de Investimento em Infraestrutura", "FIC Infra", s, flags=re.I)
    for a in [" Fundo de Investimento Imobiliario", " Fundo De Investimento Imobiliario", " Fundo de Investimento",
              " Fundo De Investimento", " Responsabilidade Limitada", " Resp Lim", " - Fii", " FII"]:
        s = s.replace(a, "")
    return s[:70]


def universo():
    cols = ["name", "description", "type", "typespecs", "sector", "close", "average_volume_30d_calc", "market_cap_basic", "currency"]
    ativos = []
    # B3
    br = scan("brazil", [{"left": "type", "operation": "in_range", "right": ["stock", "fund"]}], None, 4000, cols)
    for x in br:
        spec = x["typespecs"] or []
        if "odd" in spec or "otc" in spec or x["name"].endswith("F"):
            continue
        liq = (x["close"] or 0) * (x["average_volume_30d_calc"] or 0)
        if liq < LIQ_MIN_BR:
            continue
        t = tipo_b3(x)
        setor = SETOR.get(x["sector"] or "", x["sector"] or "")
        if t in ("fii", "fiagro", "fiinfra"):
            setor = {"fii": "Fundo imobiliário", "fiagro": "Fiagro", "fiinfra": "Infraestrutura"}[t]
        elif t == "etf":
            setor = "ETF na B3"
        ativos.append({"sym": x["s"], "t": x["name"], "n": limpa_nome(x["description"]), "tipo": t, "setor": setor,
                       "mercado": "BR", "moeda": "BRL", "liq": round(liq)})
    # EUA: ações
    us = scan("america", [{"left": "type", "operation": "equal", "right": "stock"},
                          {"left": "exchange", "operation": "in_range", "right": ["NYSE", "NASDAQ", "AMEX"]},
                          {"left": "typespecs", "operation": "has_none_of", "right": ["preferred"]}],
              "market_cap_basic", N_ACOES_US, cols)
    for x in us:
        ativos.append({"sym": x["s"], "t": x["name"].replace(".", "-"), "n": limpa_nome(x["description"]), "tipo": "acao_us",
                       "setor": SETOR.get(x["sector"] or "", x["sector"] or ""), "mercado": "US", "moeda": "USD",
                       "liq": round(x["market_cap_basic"] or 0)})
    # EUA: ETFs (sem alavancados/inversos)
    etf = scan("america", [{"left": "type", "operation": "equal", "right": "fund"},
                           {"left": "typespecs", "operation": "has", "right": ["etf"]}],
               "average_volume_30d_calc", 1500, cols)
    ruim = re.compile(r"\b(2x|3x|-1x|-2x|-3x|ultra|ultrapro|short|inverse|bear|bull|leveraged|daily|yieldmax|volatility|vix)\b", re.I)
    etf = [x for x in etf if not ruim.search(x["description"] or "")]
    etf.sort(key=lambda x: -(x["close"] or 0) * (x["average_volume_30d_calc"] or 0))
    escolhidos = {x["name"] for x in etf[:N_ETFS_US]} | set(ETFS_US_FIXOS)
    vistos = set()
    for x in etf:
        if x["name"] in escolhidos and x["name"] not in vistos:
            vistos.add(x["name"])
            ativos.append({"sym": x["s"], "t": x["name"], "n": limpa_nome(x["description"]), "tipo": "etf_us",
                           "setor": "ETF nos EUA", "mercado": "US", "moeda": "USD",
                           "liq": round((x["close"] or 0) * (x["average_volume_30d_calc"] or 0))})
    for sym, t, n, tipo, setor, mercado, moeda in EXTRAS:
        ativos.append({"sym": sym, "t": t, "n": n, "tipo": tipo, "setor": setor, "mercado": mercado, "moeda": moeda, "liq": 0})
    unicos, seen = [], set()
    for a in ativos:
        if a["t"] in seen:
            continue
        seen.add(a["t"]); unicos.append(a)
    return unicos


# ------------------------------------------------------------------ histórico (websocket do TradingView)
def _frame(func, params):
    m = json.dumps({"m": func, "p": params}, separators=(",", ":"))
    return f"~m~{len(m)}~m~{m}"


def tv_mensal(symbol, tentativas=3):
    """{'AAAA-MM': fechamento ajustado por dividendos} só com meses completos."""
    for i in range(tentativas):
        try:
            ws = create_connection("wss://data.tradingview.com/socket.io/websocket",
                                   header=["Origin: https://data.tradingview.com"], timeout=25)
            cs = "cs_" + "".join(random.choices(string.ascii_lowercase, k=12))
            ws.send(_frame("set_auth_token", ["unauthorized_user_token"]))
            ws.send(_frame("chart_create_session", [cs, ""]))
            ws.send(_frame("resolve_symbol", [cs, "sym", "=" + json.dumps({"symbol": symbol, "adjustment": "dividends", "session": "regular"})]))
            ws.send(_frame("create_series", [cs, "s1", "s1", "sym", "1M", 400]))
            barras, t0, erro, fim = [], time.time(), None, False
            while not fim and time.time() - t0 < 40:
                msg = ws.recv()
                for hb in re.findall(r"~m~\d+~m~(~h~\d+)", msg):
                    ws.send(f"~m~{len(hb)}~m~{hb}")
                for parte in re.split(r"~m~\d+~m~", msg):
                    if not parte.startswith("{"):
                        continue
                    d = json.loads(parte)
                    m = d.get("m")
                    if m in ("timescale_update", "du"):
                        barras += [b["v"] for b in d["p"][1].get("s1", {}).get("s", [])]
                    elif m in ("symbol_error", "series_error", "critical_error", "protocol_error"):
                        erro = m; fim = True
                    elif m == "series_completed":
                        fim = True
            ws.close()
            if erro:
                return None
            out = {}
            for b in barras:
                d = dt.datetime.fromtimestamp(b[0] + 12 * 3600, dt.timezone.utc)
                if b[4] and b[4] > 0:
                    out[f"{d.year:04d}-{d.month:02d}"] = b[4]
            agora = mes_atual()
            return {k: v for k, v in out.items() if k < agora}
        except Exception:
            time.sleep(2 * (i + 1))
    return None


def retornos(precos, tipo):
    """Retornos mensais com filtro de dado quebrado (desdobramento sem ajuste, pico isolado)."""
    meses = sorted(precos)
    out = {}
    lim_alto, lim_baixo = (20.0, -0.95) if tipo == "cripto" else (2.5, -0.85)
    pula = None
    for i in range(1, len(meses)):
        a, b = meses[i - 1], meses[i]
        if idx(b) - idx(a) != 1 or b < INICIO or b == pula:
            continue
        r = precos[b] / precos[a] - 1
        if r > lim_alto or r < lim_baixo:
            continue
        if tipo != "cripto" and i + 1 < len(meses) and idx(meses[i + 1]) - idx(b) == 1:
            l1 = math.log(precos[b] / precos[a]); l2 = math.log(precos[meses[i + 1]] / precos[b])
            if abs(l1) > 0.5 and abs(l2) > 0.5 and l1 * l2 < 0 and abs(l1 + l2) < 0.25:
                pula = meses[i + 1]
                continue
        out[b] = r
    ks = sorted(out); i = 0  # 3+ meses seguidos com retorno exatamente zero = sem negociação
    while i < len(ks):
        j = i
        while j < len(ks) and out[ks[j]] == 0:
            j += 1
        if j - i >= 3:
            for k in ks[i:j]:
                del out[k]
        i = max(j, i + 1)
    return out


def bcb_mensal(serie):
    url = f"https://api.bcb.gov.br/dados/serie/bcdata.sgs.{serie}/dados?formato=json&dataInicial=01/01/{INICIO[:4]}"
    out = {}
    for row in http_json(url):
        d, m, y = row["data"].split("/")
        k = f"{y}-{m}"
        if INICIO <= k < mes_atual():
            out[k] = float(row["valor"]) / 100
    return out


SIGLAS = {"Bb": "BB", "Di": "DI", "Cic": "CIC", "Fic": "FIC", "Fif": "FIF", "Fi": "FI", "Cp": "CP", "Xp": "XP", "Btg": "BTG",
          "Ie": "IE", "Rf": "RF", "Ii": "II", "Iii": "III", "Iv": "IV", "Lp": "LP", "Ipca": "IPCA", "Ima": "IMA", "Imab": "IMA-B",
          "Bnp": "BNP", "Sp": "SP", "Esg": "ESG", "Usd": "USD", "Ltda": "", "Jgp": "JGP", "Spx": "SPX", "Arx": "ARX", "Fia": "FIA",
          "Cdi": "CDI", "Bdr": "BDR", "Etf": "ETF", "Hb": "HB", "Kp": "KP", "Az": "AZ", "Vc": "VC"}


def limpa_gestor(g):
    g = re.sub(r"\s+(Gestão|Gestao)?\s*(E|De|Do|Da|E Consultoria|E Consultoria De|Gestão De|S/A)$", "", g).strip()
    g = re.sub(r"\s+(De|E|Do|Da)$", "", g).strip()
    return g.replace(" De ", " de ").replace(" Do ", " do ").replace(" Da ", " da ").replace(" E ", " e ")


def nome_fundo(n):
    return re.sub(r"\b([A-Za-zÀ-ú]+)\b", lambda m: SIGLAS.get(m.group(1), m.group(1)), n).replace("  ", " ").strip()


def compacta(rets, escala):
    meses = sorted(rets)
    i0, i1 = idx(meses[0]), idx(meses[-1])
    vals = []
    for i in range(i0, i1 + 1):
        v = rets.get(nome_mes(i))
        vals.append("" if v is None else str(int(round(v * escala))))
    out = [i0 - idx(INICIO), ",".join(vals)]
    if escala != 10000:
        out.append(escala)
    return out


# ------------------------------------------------------------------ principal
def main():
    base = Path(__file__).resolve().parent.parent
    cache = Path(sys.argv[1]) if len(sys.argv) > 1 else base / "tools" / "cache"
    print("Montando o universo de ativos pelo scanner do TradingView...", flush=True)
    ativos = universo()
    print(f"  {len(ativos)} ativos candidatos", flush=True)

    series, meta, falhas = {}, [], []
    lock = threading.Lock()

    def um(a):
        p = tv_mensal(a["sym"])
        return a, (retornos(p, a["tipo"]) if p else None)

    feitos = 0
    with ThreadPoolExecutor(max_workers=6) as ex:
        for f in as_completed([ex.submit(um, a) for a in ativos]):
            a, rets = f.result()
            feitos += 1
            if feitos % 100 == 0:
                print(f"  {feitos}/{len(ativos)}", flush=True)
            if not rets or len(rets) < 12:
                falhas.append(a["t"]); continue
            with lock:
                series[a["t"]] = compacta(rets, 10000)
                meta.append(a)

    for cod, serie in (("CDI", 4391), ("IPCA", 433)):
        series[cod] = compacta(bcb_mensal(serie), 1000000)

    # fundos de investimento (CVM)
    fj = cache / "fundos.json"
    nf = 0
    if fj.exists():
        classe_tipo = {"Renda Fixa": "fundo_rf", "Multimercado": "fundo_mm", "Ações": "fundo_acoes", "Cambial": "fundo_cambial"}
        for f in json.loads(fj.read_text(encoding="utf-8")):
            rets = retornos(f["cotas"], "fundo")
            if len(rets) < 24:
                continue
            cod = "FI" + f["cnpj"][:8]
            if cod in series:
                cod = "FI" + f["cnpj"]
            series[cod] = compacta(rets, 100000 if f["classe"] == "Renda Fixa" else 10000)
            gestor = re.sub(r"\b(LTDA|S\.?A|GESTÃO DE RECURSOS|GESTAO DE RECURSOS|GESTÃO DE INVESTIMENTOS|ASSET MANAGEMENT|INVESTIMENTOS|GESTORA DE RECURSOS|GESTORA)\b\.?", "", (f["gestor"] or "").upper())
            gestor = re.sub(r"\s+", " ", gestor).strip(" .-")
            meta.append({"t": cod, "n": nome_fundo(f["nome"]), "tipo": classe_tipo[f["classe"]], "setor": f["anbima"],
                         "mercado": "BR", "moeda": "BRL", "liq": f["pl"], "gestor": limpa_gestor(nome_fundo(gestor.title()).replace("Dtvm", "DTVM"))[:40],
                         "cnpj": f["cnpj"], "cot": f["cotistas"]})
            nf += 1

    ordem_tipo = ["acao", "fii", "fiagro", "fiinfra", "etf", "fundo_rf", "fundo_mm", "fundo_acoes", "fundo_cambial",
                  "acao_us", "etf_us", "cripto", "indice", "commodity", "moeda"]
    meta.sort(key=lambda a: (ordem_tipo.index(a["tipo"]), -(a.get("liq") or 0)))
    lista = []
    for a in meta:
        item = [a["t"], a["n"], a["tipo"], a["setor"], a["mercado"], a["moeda"], a.get("liq") or 0]
        if a.get("cnpj"):
            item += [a.get("gestor", ""), a.get("cnpj", ""), a.get("cot", 0)]
        lista.append(item)

    data = {"atualizado": dt.date.today().isoformat(), "base": INICIO,
            "campos": ["codigo", "nome", "tipo", "setor", "mercado", "moeda", "tamanho", "gestor", "cnpj", "cotistas"],
            "ativos": lista, "series": series}
    js = ("// Gerado por tools/baixar_dados.py — não edite à mão.\n"
          "window.DADOS = " + json.dumps(data, ensure_ascii=False, separators=(",", ":")) + ";\n")
    (base / "data.js").write_text(js, encoding="utf-8")
    if (cache / "fiis.json").exists():  # segmento, mandato, DY e P/VP dos FIIs (tools/fiis_cvm.py)
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        from enriquecer import enriquecer
        print(f"  FIIs enriquecidos com a CVM: {len(enriquecer(base, cache))}", flush=True)
    from collections import Counter
    print("Por tipo:", dict(Counter(a["tipo"] for a in meta)), flush=True)
    print(f"Falharam ({len(falhas)}): {' '.join(sorted(falhas)[:80])}", flush=True)
    print(f"OK: {len(meta)} ativos ({nf} fundos CVM), {len(js) // 1024} KB em data.js", flush=True)


if __name__ == "__main__":
    main()
