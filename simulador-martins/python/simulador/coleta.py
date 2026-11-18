"""Coleta direta das fontes (para símbolos fora da base ou dados diários).

- TradingView: histórico com ajuste de dividendos pelo websocket público (mesmo protocolo dos
  projetos tvdatafeed e TradingView-API), e o scanner para listar ativos.
- Banco Central: séries do SGS.
A atualização completa da base continua sendo feita por tools/baixar_dados.py.
"""
from __future__ import annotations

import json
import random
import re
import string
import sys
import time
import urllib.request
import datetime as dt
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[2] / "tools"


def _frame(func, params):
    m = json.dumps({"m": func, "p": params}, separators=(",", ":"))
    return f"~m~{len(m)}~m~{m}"


def tv_barras(simbolo: str, intervalo: str = "1M", n: int = 400, ajuste: str = "dividends") -> list[list[float]]:
    """Barras [timestamp, abertura, máxima, mínima, fechamento, volume]. intervalo: '1D', '1W', '1M'."""
    from websocket import create_connection  # pip install websocket-client
    ws = create_connection("wss://data.tradingview.com/socket.io/websocket",
                           header=["Origin: https://data.tradingview.com"], timeout=25)
    cs = "cs_" + "".join(random.choices(string.ascii_lowercase, k=12))
    ws.send(_frame("set_auth_token", ["unauthorized_user_token"]))
    ws.send(_frame("chart_create_session", [cs, ""]))
    ws.send(_frame("resolve_symbol", [cs, "sym", "=" + json.dumps({"symbol": simbolo, "adjustment": ajuste, "session": "regular"})]))
    ws.send(_frame("create_series", [cs, "s1", "s1", "sym", intervalo, n]))
    barras, t0 = [], time.time()
    while time.time() - t0 < 60:
        msg = ws.recv()
        for hb in re.findall(r"~m~\d+~m~(~h~\d+)", msg):
            ws.send(f"~m~{len(hb)}~m~{hb}")
        fim = False
        for parte in re.split(r"~m~\d+~m~", msg):
            if not parte.startswith("{"):
                continue
            d = json.loads(parte)
            if d.get("m") in ("timescale_update", "du"):
                barras += [b["v"] for b in d["p"][1].get("s1", {}).get("s", [])]
            elif d.get("m") in ("symbol_error", "series_error", "critical_error"):
                ws.close(); return []
            elif d.get("m") == "series_completed":
                fim = True
        if fim:
            break
    ws.close()
    return barras


def tv_mensal(simbolo: str) -> dict[str, float]:
    """{'AAAA-MM': fechamento ajustado} só com meses completos."""
    out = {}
    for b in tv_barras(simbolo, "1M", 400):
        d = dt.datetime.fromtimestamp(b[0] + 12 * 3600, dt.timezone.utc)
        if b[4]:
            out[f"{d.year:04d}-{d.month:02d}"] = b[4]
    hoje = dt.date.today()
    agora = f"{hoje.year:04d}-{hoje.month:02d}"
    return {k: v for k, v in out.items() if k < agora}


def tv_scanner(mercado: str = "brazil", filtro: list | None = None, colunas: list | None = None, n: int = 5000) -> list[dict]:
    colunas = colunas or ["name", "description", "type", "typespecs", "sector", "close", "average_volume_30d_calc", "market_cap_basic"]
    corpo = {"columns": colunas, "filter": filtro or [], "range": [0, n]}
    req = urllib.request.Request(f"https://scanner.tradingview.com/{mercado}/scan", data=json.dumps(corpo).encode(),
                                 headers={"User-Agent": "Mozilla/5.0", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return [dict(zip(colunas, x["d"]), simbolo=x["s"]) for x in json.load(r)["data"]]


def bcb_sgs(serie: int, inicio: str = "01/01/2000") -> dict[str, float]:
    url = f"https://api.bcb.gov.br/dados/serie/bcdata.sgs.{serie}/dados?formato=json&dataInicial={inicio}"
    with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=60) as r:
        return {f"{x['data'][6:]}-{x['data'][3:5]}": float(x["valor"]) / 100 for x in json.load(r)}


def atualizar_base(cache: str | Path = TOOLS / "cache") -> None:
    """Roda a coleta completa (fundos CVM, FIIs CVM, TradingView, BCB) e regrava data.js."""
    import subprocess
    for script in ("fundos_cvm.py", "fiis_cvm.py", "baixar_dados.py"):
        print(f"== {script}", flush=True)
        subprocess.run([sys.executable, str(TOOLS / script), str(cache)], check=True)
