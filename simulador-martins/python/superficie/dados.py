"""Ingestão e limpeza de candles de 15 minutos.

Fontes
- Binance Data Vision (arquivos oficiais públicos, data.binance.vision): pares de cripto à vista com
  OHLC, volume em moeda base e cotada, número de negócios e volume comprado por agressores
  (taker buy). Daí sai o saldo líquido de agressão real: comprador agressor − vendedor agressor.
- TradingView (mesmo protocolo de websocket dos projetos abertos tvdatafeed / TradingView-API): B3
  (WIN, WDO, ações) e outros mercados. Sem conta, só os ~5.000 candles mais recentes (~6 meses em
  15 min) e sem agressão. O volume financeiro é volume × preço típico (H+L+C)/3.
Nenhum candle é inventado: lacunas ficam como lacunas e são contadas no relatório de qualidade.
"""
from __future__ import annotations

import datetime as dt
import io
import json
import random
import re
import string
import time
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

VISION = "https://data.binance.vision/data/spot"
COLS = ["abre", "open", "high", "low", "close", "volume", "fecha", "vol_fin", "negocios", "compra_agr_base", "compra_agr_fin", "_"]
APELIDOS = {"WIN": "BMFBOVESPA:WIN1!", "WDO": "BMFBOVESPA:WDO1!", "IND": "BMFBOVESPA:IND1!", "DOL": "BMFBOVESPA:DOL1!"}
CRIPTO_PADRAO = ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "LINKUSDT", "LTCUSDT"]


def fonte_de(simbolo: str) -> tuple[str, str]:
    """('binance', 'BTCUSDT') ou ('tradingview', 'BMFBOVESPA:WIN1!')."""
    s = simbolo.strip().upper().replace("/", "")
    if s in APELIDOS:
        return "tradingview", APELIDOS[s]
    if ":" in s:
        bolsa, t = s.split(":", 1)
        return ("binance", t) if bolsa == "BINANCE" else ("tradingview", s)
    if re.fullmatch(r"[A-Z0-9]{2,12}(USDT|USDC|FDUSD|BTC|ETH|BRL)", s):
        return "binance", s
    if re.fullmatch(r"[A-Z]{4}\d{1,2}", s):           # ticker da B3 (PETR4, BOVA11...)
        return "tradingview", f"BMFBOVESPA:{s}"
    return "tradingview", s


def _baixar(url: str, tentativas: int = 4) -> bytes | None:
    for i in range(tentativas):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=120) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(2 * (i + 1))
        except Exception:
            time.sleep(2 * (i + 1))
    return None


def _ler_zip(conteudo: bytes) -> pd.DataFrame:
    with zipfile.ZipFile(io.BytesIO(conteudo)) as z:
        nome = [n for n in z.namelist() if n.endswith(".csv")][0]
        bruto = z.read(nome).decode()
    linhas = bruto.splitlines()
    if linhas and not linhas[0][:1].isdigit():  # alguns arquivos têm cabeçalho
        linhas = linhas[1:]
    df = pd.read_csv(io.StringIO("\n".join(linhas)), header=None, names=COLS)
    t = df["abre"].astype("int64")
    unidade = np.where(t > 10**14, 1_000, 1)          # desde 2025 a Binance grava em microssegundos
    df.index = pd.to_datetime(t // unidade, unit="ms", utc=True)
    return df.drop(columns=["abre", "fecha", "_"])


def binance(simbolo: str, meses: int, cache: Path, intervalo: str = "15m") -> pd.DataFrame:
    """Candles da Binance Data Vision: arquivos mensais + diários do mês corrente (até ontem)."""
    cache = Path(cache) / "binance" / simbolo; cache.mkdir(parents=True, exist_ok=True)
    hoje = dt.date.today()
    inicio = (pd.Timestamp(hoje) - pd.DateOffset(months=meses)).date().replace(day=1)
    partes = []
    m = inicio
    while m < hoje.replace(day=1):
        arq = cache / f"{m:%Y-%m}.pkl"
        if arq.exists():
            partes.append(pd.read_pickle(arq))
        else:
            c = _baixar(f"{VISION}/monthly/klines/{simbolo}/{intervalo}/{simbolo}-{intervalo}-{m:%Y-%m}.zip")
            if c:
                d = _ler_zip(c); d.to_pickle(arq); partes.append(d)
        m = (pd.Timestamp(m) + pd.DateOffset(months=1)).date()
    d = hoje.replace(day=1)
    while d < hoje:                                     # mês corrente: arquivos diários
        arq = cache / f"{d:%Y-%m-%d}.pkl"
        if arq.exists():
            partes.append(pd.read_pickle(arq))
        else:
            c = _baixar(f"{VISION}/daily/klines/{simbolo}/{intervalo}/{simbolo}-{intervalo}-{d:%Y-%m-%d}.zip")
            if c:
                x = _ler_zip(c); x.to_pickle(arq); partes.append(x)
        d += dt.timedelta(days=1)
    if not partes:
        raise ValueError(f"A Binance Data Vision não tem dados de {simbolo} em {intervalo}.")
    df = pd.concat(partes)
    df["venda_agr_fin"] = df["vol_fin"] - df["compra_agr_fin"]
    df["agressao_fin"] = df["compra_agr_fin"] - df["venda_agr_fin"]   # saldo líquido de agressão (moeda cotada)
    return df[["open", "high", "low", "close", "volume", "vol_fin", "negocios", "agressao_fin"]]


def _frame(f, p):
    m = json.dumps({"m": f, "p": p}, separators=(",", ":"))
    return f"~m~{len(m)}~m~{m}"


def tradingview(simbolo: str, n: int = 5000, resolucao: str = "15", cache: Path | None = None) -> pd.DataFrame:
    """Candles do TradingView (sem conta: ~5.000 mais recentes). Junta com o que já estava em cache,
    então rodar de tempos em tempos vai acumulando histórico."""
    from websocket import create_connection
    barras = []
    for tentativa in range(3):
        try:
            ws = create_connection("wss://data.tradingview.com/socket.io/websocket", header=["Origin: https://data.tradingview.com"], timeout=30)
            cs = "cs_" + "".join(random.choices(string.ascii_lowercase, k=12))
            ws.send(_frame("set_auth_token", ["unauthorized_user_token"]))
            ws.send(_frame("chart_create_session", [cs, ""]))
            ws.send(_frame("resolve_symbol", [cs, "sym", "=" + json.dumps({"symbol": simbolo, "adjustment": "splits", "session": "regular"})]))
            ws.send(_frame("create_series", [cs, "s1", "s1", "sym", resolucao, n]))
            t0, fim = time.time(), False
            while not fim and time.time() - t0 < 90:
                msg = ws.recv()
                for hb in re.findall(r"~m~\d+~m~(~h~\d+)", msg):
                    ws.send(f"~m~{len(hb)}~m~{hb}")
                for parte in re.split(r"~m~\d+~m~", msg):
                    if not parte.startswith("{"):
                        continue
                    d = json.loads(parte)
                    if d.get("m") in ("timescale_update", "du"):
                        barras += [b["v"] for b in d["p"][1].get("s1", {}).get("s", [])]
                    elif d.get("m") in ("series_completed", "symbol_error", "series_error", "critical_error"):
                        fim = True
            ws.close()
            if barras:
                break
        except Exception:
            time.sleep(3 * (tentativa + 1))
    if not barras:
        raise ValueError(f"O TradingView não devolveu dados de {simbolo}.")
    df = pd.DataFrame([b[:6] for b in barras], columns=["t", "open", "high", "low", "close", "volume"])
    df.index = pd.to_datetime(df.pop("t"), unit="s", utc=True)
    df["vol_fin"] = df["volume"] * (df["high"] + df["low"] + df["close"]) / 3   # volume × preço típico
    df["negocios"] = np.nan
    df["agressao_fin"] = np.nan                                                 # a fonte não informa agressão
    if cache is not None:
        arq = Path(cache) / "tradingview" / (re.sub(r"[^A-Z0-9]", "_", simbolo) + ".pkl")
        arq.parent.mkdir(parents=True, exist_ok=True)
        if arq.exists():
            df = pd.concat([pd.read_pickle(arq), df])
            df = df[~df.index.duplicated(keep="last")].sort_index()
        df.to_pickle(arq)
    return df


def limpar(df: pd.DataFrame, minutos: int = 15) -> tuple[pd.DataFrame, dict]:
    """Ordena, tira duplicatas e candles inconsistentes. Não preenche lacunas (só conta)."""
    q = {"brutos": len(df)}
    df = df.sort_index()
    dup = df.index.duplicated(keep="last"); q["duplicados"] = int(dup.sum()); df = df[~dup]
    precos = df[["open", "high", "low", "close"]]
    ruim = (precos <= 0).any(axis=1) | precos.isna().any(axis=1)
    ruim |= df["high"] < precos[["open", "close", "low"]].max(axis=1) - 1e-12
    ruim |= df["low"] > precos[["open", "close", "high"]].min(axis=1) + 1e-12
    ruim |= df["volume"] < 0
    q["inconsistentes"] = int(ruim.sum()); df = df[~ruim].copy()
    passo = pd.Timedelta(minutes=minutos)
    dif = df.index.to_series().diff()
    q["lacunas"] = int((dif > passo).sum())
    q["candles_faltando"] = int(((dif[dif > passo] / passo) - 1).sum()) if len(dif) else 0
    q["limpos"] = len(df)
    q["inicio"], q["fim"] = (str(df.index[0]), str(df.index[-1])) if len(df) else ("", "")
    return df, q


def carregar(simbolo: str, meses: int, cache: Path) -> tuple[pd.DataFrame, dict]:
    fonte, s = fonte_de(simbolo)
    df = binance(s, meses, cache) if fonte == "binance" else tradingview(s, cache=cache)
    if fonte == "tradingview":
        corte = df.index[-1] - pd.DateOffset(months=meses)
        df = df[df.index >= corte]
    df, q = limpar(df)
    q.update(fonte=fonte, simbolo=s, meses_pedidos=meses,
             meses_obtidos=round((df.index[-1] - df.index[0]).days / 30.44, 1) if len(df) else 0,
             tem_agressao=bool(df["agressao_fin"].notna().any()))
    return df, q
