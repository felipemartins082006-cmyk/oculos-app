"""Baixa do arquivo público da Binance (data.binance.vision) o histórico de funding e as velas de 8h dos
contratos perpétuos USDT-M, e as velas de 8h do Spot (data-api), para os pares dos universos da pesquisa."""
import csv, io, json, os, sys, time, urllib.request, zipfile
from concurrent.futures import ThreadPoolExecutor

SP = os.environ["RESEARCH_DIR"].rstrip("/") + "/"  # a mesma pasta do research_test.go
OUT = SP + "fut/"
os.makedirs(OUT, exist_ok=True)
u = json.load(open(SP + "universe.json"))
syms = sorted(set(u["desenho"][:10] + u["confirmação"][:10] + ["BTCUSDT", "ETHUSDT"]))
months = [f"{y}-{m:02d}" for y in range(2020, 2027) for m in range(1, 13) if (y, m) >= (2020, 1) and (y, m) <= (2026, 10)]


def get(url):
    for a in range(3):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(1 + a)
        except Exception:
            time.sleep(1 + a)
    return None


def rows(data):
    z = zipfile.ZipFile(io.BytesIO(data))
    txt = z.read(z.namelist()[0]).decode()
    out = []
    for r in csv.reader(io.StringIO(txt)):
        if r and r[0][:1].isdigit():
            out.append(r)
    return out


def funding(sym):
    res = []
    for m in months:
        d = get(f"https://data.binance.vision/data/futures/um/monthly/fundingRate/{sym}/{sym}-fundingRate-{m}.zip")
        if d:
            for r in rows(d):
                res.append([int(r[0]), float(r[2])])  # calc_time, funding rate
    return res


def perp8h(sym):
    res = []
    for m in months:
        d = get(f"https://data.binance.vision/data/futures/um/monthly/klines/{sym}/8h/{sym}-8h-{m}.zip")
        if d:
            for r in rows(d):
                res.append([int(r[0]), float(r[1]), float(r[2]), float(r[3]), float(r[4])])
    return res


def spot8h(sym):
    res, start = [], 1577836800000
    while True:
        d = get(f"https://data-api.binance.vision/api/v3/klines?symbol={sym}&interval=8h&limit=1000&startTime={start}")
        if not d:
            break
        ks = json.loads(d)
        if not ks:
            break
        res += [[k[0], float(k[1]), float(k[2]), float(k[3]), float(k[4])] for k in ks]
        start = ks[-1][0] + 8 * 3600000
        if len(ks) < 1000:
            break
    return res


def job(sym):
    f = OUT + sym + ".json"
    if os.path.exists(f):
        return sym, "cache"
    data = {"funding": funding(sym), "perp": perp8h(sym), "spot": spot8h(sym)}
    json.dump(data, open(f, "w"))
    return sym, f"funding {len(data['funding'])} perp {len(data['perp'])} spot {len(data['spot'])}"


with ThreadPoolExecutor(6) as ex:
    for s, msg in ex.map(job, syms):
        print(s, msg, file=sys.stderr)
