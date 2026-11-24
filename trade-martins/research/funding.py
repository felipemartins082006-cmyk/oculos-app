"""Arbitragem de funding (cash-and-carry): comprado no Spot + vendido no perpétuo, mesmo tamanho.
O preço se cancela; ganha-se (ou paga-se) o funding a cada 8h (ou 4h), menos as taxas, mais/menos a variação da
diferença entre os preços (basis). Capital = Spot + margem do vendido (alavancagem L no perpétuo)."""
import os, json, math, sys
from datetime import datetime, timezone

SP = os.environ["RESEARCH_DIR"].rstrip("/") + "/"  # a mesma pasta do research_test.go
U = json.load(open(SP + "universe.json"))
H8 = 8 * 3600000
COST = 0.003  # ida e volta: Spot 0,1% x2 + perpétuo 0,05% x2 (taker)
PERIODS = [("desenho", "2020-11-24", "2023-11-24"), ("confirmação", "2023-11-24", "2026-11-24")]


def ms(d):
    return int(datetime.strptime(d, "%Y-%m-%d").replace(tzinfo=timezone.utc).timestamp() * 1000)


def load(sym):
    try:
        d = json.load(open(SP + "fut/" + sym + ".json"))
    except FileNotFoundError:
        return None
    spot = {k[0]: k[4] for k in d["spot"]}
    perp = {k[0]: k[4] for k in d["perp"]}
    fund = sorted((t, r) for t, r in d["funding"])
    return spot, perp, fund


def sim(sym, a, b, mode, L=1.0, enter=0.10, window=9):
    """Devolve a série de patrimônio (começa em 1) a cada 8h e o número de entradas."""
    x = load(sym)
    if not x:
        return None
    spot, perp, fund = x
    steps = [t for t in range(a - a % H8, b, H8) if t in spot and t in perp]
    if len(steps) < 30:
        return None
    w = 1.0 / (1 + 1 / L)  # fração do capital em cada perna
    eq, held, n_in, fi = 1.0, False, 0, 0
    hist = []  # funding recebido recentemente (para a regra seletiva)
    curve = [(steps[0], 1.0)]
    for k in range(len(steps) - 1):
        t0, t1 = steps[k], steps[k + 1]
        if t1 - t0 != H8:
            continue
        # funding pago neste intervalo (t0, t1]
        f = 0.0
        while fi < len(fund) and fund[fi][0] <= t1 + 60000:
            if fund[fi][0] > t0 + 60000:
                f += fund[fi][1]
                hist.append(fund[fi][1])
            fi += 1
        if held:
            s_ret = spot[t1] / spot[t0] - 1
            p_ret = perp[t1] / perp[t0] - 1
            eq *= 1 + w * (s_ret - p_ret + f)
        # decisão no fim do intervalo, com o funding conhecido até agora
        if mode == "sempre":
            want = True
        else:
            recent = hist[-window:]
            avg = sum(recent) / len(recent) if recent else 0
            per_year = avg * 3 * 365  # aproximação: eventos de 8h
            want = per_year > enter if not held else avg > 0
        if want != held:
            eq *= 1 - w * COST / 2
            held = want
            n_in += want
        curve.append((t1, eq))
    if held:
        eq *= 1 - w * COST / 2
        curve[-1] = (curve[-1][0], eq)
    return curve, n_in


def stats(curve):
    v = [e for _, e in curve]
    yrs = (curve[-1][0] - curve[0][0]) / (365.25 * 86400000)
    cagr = v[-1] ** (1 / yrs) - 1 if v[-1] > 0 else -1
    peak, dd = v[0], 0
    for e in v:
        peak = max(peak, e)
        dd = max(dd, 1 - e / peak)
    rets = [v[i] / v[i - 1] - 1 for i in range(1, len(v))]
    m = sum(rets) / len(rets)
    sd = math.sqrt(sum((r - m) ** 2 for r in rets) / (len(rets) - 1))
    sh = m / sd * math.sqrt(3 * 365) if sd > 0 else 0
    return (v[-1] - 1) * 100, cagr * 100, sh, dd * 100


def portfolio(curves):
    """Divide igual entre as moedas (sem rebalancear), alinhando pelo tempo."""
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
    print(f"\n=== {pname} ({a} → {b}) ===")
    print(f"{'moeda / regra':34s} {'retorno':>8s} {'ao ano':>7s} {'Sharpe':>7s} {'queda':>6s} {'entradas':>8s}")
    for L in (1.0, 2.0):
        for mode in ("sempre", "seletiva"):
            curves = []
            for sym in U[pname][:10]:
                r = sim(sym, A, B, mode, L)
                if r is None:
                    continue
                c, n = r
                curves.append(c)
                if L == 1.0 and sym in ("BTCUSDT", "ETHUSDT"):
                    ret, cagr, sh, dd = stats(c)
                    print(f"{sym + ' ' + mode:34s} {ret:7.1f}% {cagr:6.1f}% {sh:7.2f} {dd:5.1f}% {n:8d}")
            ret, cagr, sh, dd = stats(portfolio(curves))
            print(f"{'carteira top10 ' + mode + f' (perp {L:.0f}x)':34s} {ret:7.1f}% {cagr:6.1f}% {sh:7.2f} {dd:5.1f}%   ({len(curves)} moedas)")
