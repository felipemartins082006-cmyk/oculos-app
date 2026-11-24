"""Monta universos de pares 'na época' (sem viés de sobrevivência): os N pares USDT de maior volume em
negociação (USDT) nos 30 dias ANTES do início de cada período, entre todos os que existiam então,
inclusive os que depois saíram da Binance."""
import os, json, re, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor

SP = os.environ["RESEARCH_DIR"].rstrip("/") + "/"  # a mesma pasta do research_test.go
info = json.load(open(SP + "exinfo.json"))  # baixe antes: curl -o $RESEARCH_DIR/exinfo.json 'https://data-api.binance.vision/api/v3/exchangeInfo?permissions=SPOT'
syms = [s["symbol"] for s in info["symbols"] if s["quoteAsset"] == "USDT"]

STABLE = {"USDC", "BUSD", "TUSD", "PAX", "USDP", "USDS", "USDSB", "FDUSD", "DAI", "UST", "USTC", "EUR", "GBP", "AUD",
          "TRY", "BRL", "RUB", "UAH", "NGN", "ZAR", "BIDR", "IDRT", "BKRW", "BVND", "AEUR", "EURI", "USDE", "XUSD",
          "RLUSD", "USD1", "BFUSD", "SUSD", "PAXG", "WBTC", "BCHABC", "BCHSV", "USDSOLD", "JPY", "MXN", "ARS", "COP",
          "PLN", "RON", "CZK", "VAI", "LUSD", "FRAX", "PYUSD", "USDJ", "GUSD", "HUSD", "XAUT", "WBETH", "BETH", "STETH"}
LEV = re.compile(r"(UP|DOWN|BULL|BEAR)USDT$")


def base(s):
    return s[:-4]


cands = [s for s in syms if base(s) not in STABLE and not LEV.search(s)]
print(len(cands), "candidatos", file=sys.stderr)


def vol(sym, start_ms, days=30):
    url = f"https://data-api.binance.vision/api/v3/klines?symbol={sym}&interval=1d&limit={days}&startTime={start_ms}"
    for a in range(4):
        try:
            with urllib.request.urlopen(url, timeout=30) as r:
                ks = json.loads(r.read())
            # precisa ter os 30 dias completos (o par já existia e negociava antes do período)
            if len(ks) < days or ks[0][0] > start_ms + 86400000:
                return 0.0
            return sum(float(k[7]) for k in ks)
        except Exception as e:
            time.sleep(1 + a)
    return 0.0


out = {}
for name, start in [("desenho", "2020-10-25"), ("confirmação", "2023-10-25")]:
    ms = int(time.mktime(time.strptime(start, "%Y-%m-%d")) * 1000) - time.timezone * 1000
    with ThreadPoolExecutor(8) as ex:
        vs = list(ex.map(lambda s: (s, vol(s, ms)), cands))
    vs = sorted([v for v in vs if v[1] > 0], key=lambda x: -x[1])
    out[name] = [s for s, _ in vs[:30]]
    print(name, len(vs), "com dados;", ", ".join(f"{s}" for s, v in vs[:30]), file=sys.stderr)
json.dump(out, open(SP + "universe.json", "w"), indent=1, ensure_ascii=False)
