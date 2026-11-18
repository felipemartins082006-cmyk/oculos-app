"""Base de dados: retornos mensais de todos os ativos (em moeda original) + metadados.

A base vem do mesmo data.js que o app usa (gerado por tools/baixar_dados.py). Também dá para
acrescentar qualquer símbolo do TradingView na hora com Base.adicionar_tradingview().
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd

RAIZ = Path(__file__).resolve().parents[2]          # pasta simulador-martins/
DATA_JS = RAIZ / "data.js"

TIPOS = {
    "acao": "Ação BR", "fii": "FII", "fiagro": "Fiagro", "fiinfra": "FI-Infra", "etf": "ETF B3",
    "fundo_rf": "Fundo RF", "fundo_mm": "Multimercado", "fundo_acoes": "Fundo de ações", "fundo_cambial": "Fundo cambial",
    "acao_us": "Ação EUA", "etf_us": "ETF EUA", "cripto": "Cripto", "indice": "Índice", "commodity": "Commodity",
    "moeda": "Moeda", "rf": "Renda fixa (modelo)",
}


@dataclass
class Base:
    """retornos: DataFrame (índice = meses pd.Period, colunas = códigos), em moeda original.
    meta: DataFrame indexado pelo código (nome, tipo, setor, mercado, moeda, tamanho, ... e dados de FII)."""
    retornos: pd.DataFrame
    meta: pd.DataFrame
    atualizado: str = ""
    estendidos: dict = field(default_factory=dict)   # código -> nº de meses estimados por proxy

    # ------------------------------------------------------------------ carregar
    @classmethod
    def do_app(cls, caminho: Path | str = DATA_JS) -> "Base":
        texto = Path(caminho).read_text(encoding="utf-8")
        D = json.loads(texto.split("window.DADOS = ", 1)[1].rstrip().rstrip(";"))
        base = pd.Period(D["base"], freq="M")
        cols = {}
        for cod, s in D["series"].items():
            esc = s[2] if len(s) > 2 else 10000
            vals = [np.nan if x == "" else int(x) / esc for x in s[1].split(",")]
            idx = pd.period_range(base + s[0], periods=len(vals), freq="M")
            cols[cod] = pd.Series(vals, index=idx, dtype="float64")
        ret = pd.DataFrame(cols).sort_index()
        campos = ["nome", "tipo", "setor", "mercado", "moeda", "tamanho", "gestor", "cnpj", "cotistas"]
        linhas = []
        for a in D["ativos"]:
            d = dict(zip(["codigo"] + campos, a))
            linhas.append(d)
        meta = pd.DataFrame(linhas).set_index("codigo")
        for c in ["gestor", "cnpj"]:
            meta[c] = meta[c].fillna("")
        meta["cotistas"] = meta["cotistas"].fillna(0)
        # dados de FII (CVM)
        fii = D.get("fii", {})
        meta["mandato"] = [fii.get(c, [""])[0] for c in meta.index]
        meta["p_vp"] = [fii.get(c, [None] * 4)[3] for c in meta.index]
        meta["taxa_adm"] = [fii.get(c, [None] * 7)[6] for c in meta.index]
        # macro (CDI, IPCA) também entram como "ativos" para facilitar
        for cod, nome in (("CDI", "CDI"), ("IPCA", "IPCA")):
            meta.loc[cod, ["nome", "tipo", "setor", "mercado", "moeda", "tamanho"]] = [nome, "macro", "Macro", "BR", "BRL", 0]
        return cls(ret, meta, D.get("atualizado", ""))

    # ------------------------------------------------------------------ consultas
    def info(self, cod: str) -> dict:
        if cod.startswith("RF:"):
            tipo, taxa = cod.split(":")[1], float(cod.split(":")[2])
            nome = {"CDI": f"CDI {taxa:g}%", "IPCA": f"IPCA + {taxa:g}% a.a.", "PRE": f"Prefixado {taxa:g}% a.a."}[tipo]
            return {"nome": nome, "tipo": "rf", "setor": "Renda fixa (modelo)", "moeda": "BRL", "mercado": "BR"}
        return self.meta.loc[cod].to_dict()

    def buscar(self, texto: str = "", tipos: list[str] | None = None, mandato: str | None = None,
               min_meses: int = 0, limite: int = 50) -> pd.DataFrame:
        m = self.meta.copy()
        m["meses"] = self.retornos.reindex(columns=m.index).notna().sum().reindex(m.index).fillna(0).astype(int)
        if tipos:
            m = m[m["tipo"].isin(tipos)]
        if mandato:
            m = m[m["mandato"] == mandato]
        if min_meses:
            m = m[m["meses"] >= min_meses]
        if texto:
            t = texto.lower()
            alvo = (m.index.str.lower() + " " + m["nome"].str.lower() + " " + m["setor"].str.lower() + " " + m["gestor"].str.lower())
            m = m[alvo.str.contains(t, regex=False)]
        return m.sort_values("tamanho", ascending=False).head(limite)

    # ------------------------------------------------------------------ retornos em reais
    def retornos_brl(self, codigos: list[str], cambio: bool = True) -> pd.DataFrame:
        """Retornos mensais em reais (ativos em dólar convertidos pelo câmbio do mês) + renda fixa por modelo."""
        fx = self.retornos["USDBRL"]
        cdi, ipca = self.retornos["CDI"], self.retornos["IPCA"]
        out = {}
        for c in codigos:
            if c.startswith("RF:"):
                tipo, taxa = c.split(":")[1], float(c.split(":")[2]) / 100
                if tipo == "CDI":
                    out[c] = cdi * taxa
                elif tipo == "IPCA":
                    out[c] = (1 + ipca) * (1 + taxa) ** (1 / 12) - 1
                else:
                    out[c] = pd.Series((1 + taxa) ** (1 / 12) - 1, index=cdi.index)
                continue
            r = self.retornos[c]
            if cambio and self.meta.loc[c, "moeda"] == "USD":
                r = (1 + r) * (1 + fx) - 1
            out[c] = r
        return pd.DataFrame(out)

    # ------------------------------------------------------------------ TradingView sob demanda
    def adicionar_tradingview(self, simbolo: str, codigo: str | None = None, tipo: str = "acao",
                              moeda: str = "BRL", nome: str | None = None) -> str:
        """Baixa um símbolo qualquer do TradingView (ex.: 'BMFBOVESPA:ABCD11', 'NASDAQ:XYZ') e inclui na base."""
        from .coleta import tv_mensal
        precos = tv_mensal(simbolo)
        if not precos:
            raise ValueError(f"Sem dados para {simbolo}")
        s = pd.Series(precos)
        s.index = pd.PeriodIndex(s.index, freq="M")
        r = s.sort_index().pct_change().dropna()
        codigo = codigo or simbolo.split(":")[-1]
        self.retornos[codigo] = r
        self.meta.loc[codigo, ["nome", "tipo", "setor", "mercado", "moeda", "tamanho"]] = [nome or codigo, tipo, "", "BR" if moeda == "BRL" else "US", moeda, 0]
        return codigo

    def resumo(self) -> pd.DataFrame:
        cont = self.meta[self.meta["tipo"] != "macro"].groupby("tipo").size().rename("ativos")
        return cont.rename(index=TIPOS).sort_values(ascending=False).to_frame()
