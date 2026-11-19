"""Superfícies 3D de mercado e predição intradiária (MVP).

Eixo X: tempo da sessão em candles de 15 minutos; eixo Y: oscilação % desde a abertura da sessão;
eixo Z: volume financeiro (e saldo líquido de agressão, quando a fonte informa) em cada faixa.
Modelo: classificação (LightGBM / XGBoost) da chance de atingir um alvo antes do stop, validada no
tempo (70/15/15 ou walk-forward), sem embaralhar e sem dados do futuro.
"""
__version__ = "0.1.0"
