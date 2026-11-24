package main

// Filtro de mercado: só compra quando o BTC está acima da sua média de N dias, e (opcional) vende quando ele
// perde a média. As criptos caem juntas: quando o "líder" está em queda, as compras dos outros pares costumam
// dar errado. Nos testes com 8 pares (veja o README), isso reduziu as quedas e melhorou o Sharpe do momentum
// e do rompimento tanto no período usado para escolher a regra quanto nos 3 anos seguintes.
//
// O filtro usa velas DIÁRIAS e FECHADAS do BTCUSDT, qualquer que seja o tamanho de vela da estratégia: para
// a vela de um par que fecha no instante T, vale o último dia do BTC que fechou até T. Assim funciona igual
// em velas de 1h, 4h ou 1d, e nunca olha o futuro.

import (
	"math"
	"sort"
)

const marketSymbol = "BTCUSDT"

// marketCandles = quantas velas diárias do BTC o filtro precisa (a média + folga).
func marketCandles(days int) int { return days + 5 }

func sma(v []float64, n int) []float64 {
	out := nanSeries(len(v))
	if n < 1 {
		return out
	}
	s := 0.0
	for i := range v {
		s += v[i]
		if i >= n {
			s -= v[i-n]
		}
		if i >= n-1 {
			out[i] = s / float64(n)
		}
	}
	return out
}

// marketState[j] = o BTC fechou o dia j acima da média de `days` dias? (false enquanto não há dias suficientes)
func marketState(btc []Kline, days int) []bool {
	c := make([]float64, len(btc))
	for i, k := range btc {
		c[i] = k.C
	}
	m := sma(c, days)
	out := make([]bool, len(btc))
	for i := range btc {
		out[i] = !math.IsNaN(m[i]) && c[i] > m[i]
	}
	return out
}

// applyMarket liga o filtro de mercado num par já preparado. btc = velas diárias fechadas do BTCUSDT
// (em ordem). Sem velas do BTC, nenhuma compra é liberada.
func (pr *Prepared) applyMarket(btc []Kline) {
	p := pr.p
	if p.MarketDays <= 0 {
		return
	}
	st := marketState(btc, p.MarketDays)
	n := len(pr.ks)
	pr.gate, pr.exit = make([]bool, n), make([]bool, n)
	for i, k := range pr.ks {
		// último dia do BTC que já tinha fechado quando esta vela fechou
		j := sort.Search(len(btc), func(j int) bool { return btc[j].CloseTime > k.CloseTime }) - 1
		fresh := j >= 0 && k.CloseTime-btc[j].CloseTime < 3*dayMs // dados do BTC velhos: não compra, mas também não força venda
		pr.gate[i] = fresh && st[j]
		pr.exit[i] = fresh && !st[j] && p.MarketExit
	}
}

const dayMs = int64(24 * 60 * 60 * 1000)

// marketOKAt: estado do filtro na vela i (para o painel). on = o filtro está ligado.
func (pr *Prepared) marketOKAt(i int) (ok, on bool) {
	if pr.p.MarketDays <= 0 || pr.gate == nil || i < 0 || i >= len(pr.gate) {
		return true, false
	}
	return pr.gate[i], true
}
