"""Momentum nos perpétuos, comprado E vendido, com as mesmas regras do robô:
  comprado: momentum de 30 dias > 0 e BTC acima da média de 100 dias (sai quando um dos dois falha)
  vendido:  momentum de 30 dias < 0 e BTC abaixo da média de 100 dias (sai quando um dos dois falha)
Volatilidade-alvo 40% (fração do espaço de cada moeda), sem alavancagem (1x). Taxa 0,05% por lado (taker do
perpétuo), funding pago/recebido a cada evento, liquidação se o preço andar 100% contra (1x) dentro da vela.
Cada moeda recebe 1/N do capital (contas separadas, sem rebalancear). Comparado com só comprado no Spot."""
import os, json, math, sys
from datetime import datetime, timezone

SP = os.environ["RESEARCH_DIR"].rstrip("/") + "/"  # a mesma pasta do research_test.go
U = json.load(open(SP + "universe.json"))
H8, DAY = 8 * 3600000, 86400000
PERIODS = [("desenho", "2020-11-24", "2023-11-24"), ("confirmação", "2023-11-24", "2026-11-24")]
MOM = 90  # 30 dias em velas de 8h
VOLN = 90


def ms(d):
    return int(datetime.strptime(d, "%Y-%m-%d").replace(tzinfo=timezone.utc).timestamp() * 1000)


btc = json.load(open(SP + "BTCUSDT_1d.json"))
bc = [k["C"] for k in btc]
btc_ok = {}  # dia (CloseTime) -> acima da média de 100 dias
for i in range(99, len(bc)):
    btc_ok[btc[i]["CloseTime"]] = bc[i] > sum(bc[i - 99:i + 1]) / 100
btc_days = sorted(btc_ok)


def market_at(t_close):
    # último dia do BTC fechado até t_close
    import bisect
    j = bisect.bisect_right(btc_days, t_close) - 1
    return btc_ok[btc_days[j]] if j >= 0 else None


def sim(sym, a, b, mode, venue):
    d = json.load(open(SP + "fut/" + sym + ".json"))
    ks = d["perp"] if venue == "perp" else d["spot"]
    fund = sorted((t, r) for t, r in d["funding"])
    fee = 0.0005 if venue == "perp" else 0.001
    if len(ks) < MOM + 10:
        return None
    t = [k[0] for k in ks]
    o = [k[1] for k in ks]
    h = [k[2] for k in ks]
    l = [k[3] for k in ks]
    c = [k[4] for k in ks]
    eq, pos, frac, entry, fi = 1.0, 0, 0.0, 0.0, 0
    curve, n_tr = [], 0
    pend = None
    started = False
    for i in range(1, len(ks)):
        if t[i] >= b:
            break
        if t[i] < a:
            continue
        if not started:
            curve.append((t[i], eq))
            started = True
        # executa a ordem decidida no fechamento anterior, na abertura desta vela
        if pend is not None and pend != pos:
            if pos != 0:
                eq *= 1 + frac * pos * (o[i] / c[i - 1] - 1) - frac * fee  # do último fechamento até a abertura
                eq = max(eq, 0)
            pos = pend
            if pos != 0:
                v = vol(c, i - 1)
                frac = min(1.0, 0.40 / v) if v > 0 else 1.0
                entry = o[i]
                eq *= 1 - frac * fee
                n_tr += 1
            # marca a posição na abertura como base
            base = o[i]
        elif pos != 0:
            base = c[i - 1]
        pend = None
        if pos != 0:
            # liquidação (1x): preço anda 100% contra a partir da entrada (vendido) ou cai a zero (comprado)
            if pos < 0 and h[i] >= entry * 2:
                eq *= 1 - frac
                pos = 0
            else:
                eq *= 1 + frac * pos * (c[i] / base - 1)  # fração constante do patrimônio da moeda
            # funding nesta vela: comprado paga, vendido recebe (taxa positiva)
            while fi < len(fund) and fund[fi][0] <= t[i] + H8:
                if fund[fi][0] > t[i] and venue == "perp" and pos != 0:
                    eq *= 1 - frac * pos * fund[fi][1]
                fi += 1
        else:
            while fi < len(fund) and fund[fi][0] <= t[i] + H8:
                fi += 1
        # sinal no fechamento
        if i >= MOM:
            m = c[i] / c[i - MOM] - 1
            mk = market_at(t[i] + H8 - 1)
            want = 0
            if mk is not None:
                if m > 0 and mk:
                    want = 1
                elif m < 0 and not mk and mode == "longshort":
                    want = -1
                elif m < 0 and not mk and mode == "short":
                    want = -1
                if mode == "short" and want == 1:
                    want = 0
            pend = want
        curve.append((t[i] + H8, eq))
    return curve, n_tr


def vol(c, i):
    if i < VOLN + 1:
        return 0
    r = [math.log(c[j] / c[j - 1]) for j in range(i - VOLN + 1, i + 1)]
    m = sum(r) / len(r)
    return math.sqrt(sum((x - m) ** 2 for x in r) / (len(r) - 1)) * math.sqrt(3 * 365)


def stats(curve):
    v = [e for _, e in curve]
    yrs = (curve[-1][0] - curve[0][0]) / (365.25 * DAY)
    cagr = v[-1] ** (1 / yrs) - 1 if v[-1] > 0 else -1
    peak, dd = v[0], 0
    for e in v:
        peak = max(peak, e)
        dd = max(dd, 1 - e / peak)
    rets = [v[i] / v[i - 1] - 1 for i in range(1, len(v)) if v[i - 1] > 0]
    m = sum(rets) / len(rets)
    sd = math.sqrt(sum((r - m) ** 2 for r in rets) / (len(rets) - 1))
    sh = m / sd * math.sqrt(3 * 365) if sd > 0 else 0
    return (v[-1] - 1) * 100, cagr * 100, sh, dd * 100, sh * math.sqrt(yrs)


def portfolio(curves):
    times = sorted(set(t for c in curves for t, _ in c))
    last = [1.0] * len(curves)
    idx = [dict(c) for c in curves]
    out = []
    for t in times:
        for j, d in enumerate(idx):
            if t in d:
                last[j] = d[t]
        out.append((t, sum(last) / len(last)))
    return out


for pname, a, b in PERIODS:
    A, B = ms(a), ms(b)
    print(f"\n=== {pname} ({a} → {b}), top10 da época ===")
    print(f"{'regra':42s} {'retorno':>8s} {'ao ano':>7s} {'Sharpe':>7s} {'queda':>6s} {'t':>6s} {'oper':>5s}")
    for venue, mode, name in [("spot", "long", "Spot, só comprado (taxa 0,1%)"), ("perp", "long", "Perpétuo, só comprado (0,05% + funding)"),
                              ("perp", "short", "Perpétuo, só vendido"), ("perp", "longshort", "Perpétuo, comprado E vendido")]:
        curves, trades = [], 0
        for sym in U[pname][:10]:
            try:
                r = sim(sym, A, B, mode, venue)
            except FileNotFoundError:
                r = None
            if r:
                curves.append(r[0])
                trades += r[1]
        ret, cagr, sh, dd, tt = stats(portfolio(curves))
        print(f"{name:42s} {ret:7.0f}% {cagr:6.1f}% {sh:7.2f} {dd:5.1f}% {tt:6.2f} {trades:5d}  ({len(curves)} moedas)")
