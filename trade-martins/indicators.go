package main

// Indicadores técnicos. Todos devolvem uma série do mesmo tamanho da entrada, com NaN onde ainda
// não há dados suficientes. Nenhum usa dados do futuro: o valor em i depende só de i e antes.

import "math"

func nanSeries(n int) []float64 {
	out := make([]float64, n)
	for i := range out {
		out[i] = math.NaN()
	}
	return out
}

// ema: média móvel exponencial. Começa com a média simples das n primeiras velas.
func ema(v []float64, n int) []float64 {
	out := nanSeries(len(v))
	if n <= 0 || len(v) < n {
		return out
	}
	sum := 0.0
	for i := 0; i < n; i++ {
		sum += v[i]
	}
	out[n-1] = sum / float64(n)
	k := 2.0 / float64(n+1)
	for i := n; i < len(v); i++ {
		out[i] = v[i]*k + out[i-1]*(1-k)
	}
	return out
}

// rsi de Wilder.
func rsi(v []float64, n int) []float64 {
	out := nanSeries(len(v))
	if n <= 0 || len(v) <= n {
		return out
	}
	var gain, loss float64
	for i := 1; i <= n; i++ {
		d := v[i] - v[i-1]
		if d > 0 {
			gain += d
		} else {
			loss -= d
		}
	}
	gain /= float64(n)
	loss /= float64(n)
	val := func() float64 {
		if loss == 0 {
			return 100
		}
		return 100 - 100/(1+gain/loss)
	}
	out[n] = val()
	for i := n + 1; i < len(v); i++ {
		d := v[i] - v[i-1]
		g, l := 0.0, 0.0
		if d > 0 {
			g = d
		} else {
			l = -d
		}
		gain = (gain*float64(n-1) + g) / float64(n)
		loss = (loss*float64(n-1) + l) / float64(n)
		out[i] = val()
	}
	return out
}

// atr: amplitude média das velas (Average True Range, suavização de Wilder).
func atr(ks []Kline, n int) []float64 {
	out := nanSeries(len(ks))
	if n <= 0 || len(ks) <= n {
		return out
	}
	tr := func(i int) float64 {
		h, l, pc := ks[i].H, ks[i].L, ks[i-1].C
		return math.Max(h-l, math.Max(math.Abs(h-pc), math.Abs(l-pc)))
	}
	sum := 0.0
	for i := 1; i <= n; i++ {
		sum += tr(i)
	}
	out[n] = sum / float64(n)
	for i := n + 1; i < len(ks); i++ {
		out[i] = (out[i-1]*float64(n-1) + tr(i)) / float64(n)
	}
	return out
}

// prevHigh[i] = maior máxima das n velas ANTERIORES a i (canal de Donchian, sem incluir a vela i).
func prevHigh(ks []Kline, n int) []float64 {
	out := nanSeries(len(ks))
	for i := n; i < len(ks) && n > 0; i++ {
		m := ks[i-n].H
		for j := i - n + 1; j < i; j++ {
			m = math.Max(m, ks[j].H)
		}
		out[i] = m
	}
	return out
}

// prevLow[i] = menor mínima das n velas ANTERIORES a i.
func prevLow(ks []Kline, n int) []float64 {
	out := nanSeries(len(ks))
	for i := n; i < len(ks) && n > 0; i++ {
		m := ks[i-n].L
		for j := i - n + 1; j < i; j++ {
			m = math.Min(m, ks[j].L)
		}
		out[i] = m
	}
	return out
}

// volatility: desvio padrão dos retornos (log) das últimas n velas, anualizado com ppy velas por ano.
func volatility(c []float64, n int, ppy float64) []float64 {
	out := nanSeries(len(c))
	if n < 2 {
		return out
	}
	r := make([]float64, len(c))
	for i := 1; i < len(c); i++ {
		r[i] = math.Log(c[i] / c[i-1])
	}
	for i := n; i < len(c); i++ {
		var s, s2 float64
		for j := i - n + 1; j <= i; j++ {
			s += r[j]
			s2 += r[j] * r[j]
		}
		mean := s / float64(n)
		v := s2/float64(n) - mean*mean
		if v < 0 {
			v = 0
		}
		out[i] = math.Sqrt(v*float64(n)/float64(n-1)) * math.Sqrt(ppy)
	}
	return out
}

// momentum[i] = retorno das últimas n velas (c[i]/c[i-n] − 1).
func momentum(c []float64, n int) []float64 {
	out := nanSeries(len(c))
	for i := n; i < len(c) && n > 0; i++ {
		out[i] = c[i]/c[i-n] - 1
	}
	return out
}
