package main

// Métricas de desempenho, as mesmas usadas por gestores quantitativos.

import (
	"math"
	"time"
)

type Metrics struct {
	ReturnPct    float64 `json:"returnPct"`    // retorno total
	CAGRPct      float64 `json:"cagrPct"`      // retorno anualizado (composto)
	VolPct       float64 `json:"volPct"`       // volatilidade anualizada
	Sharpe       float64 `json:"sharpe"`       // retorno médio / volatilidade (anualizado, juro zero)
	Sortino      float64 `json:"sortino"`      // como o Sharpe, mas só pune as quedas
	MaxDDPct     float64 `json:"maxDDPct"`     // maior queda do pico ao fundo (fundo medido na mínima da vela)
	Calmar       float64 `json:"calmar"`       // retorno anualizado / maior queda
	Years        float64 `json:"years"`        // tamanho do período
	SharpeT      float64 `json:"sharpeT"`      // estatística t do Sharpe ≈ Sharpe × √anos
	Significant  bool    `json:"significant"`  // t > 1,96 (95%), ignorando caudas gordas e testes múltiplos
	Trades       int     `json:"trades"`       // operações fechadas
	WinRate      float64 `json:"winRate"`      // % com lucro
	ProfitFactor float64 `json:"profitFactor"` // lucro bruto / prejuízo bruto (999 = nenhum prejuízo)
	AvgTradePct  float64 `json:"avgTradePct"`  // resultado médio por operação (expectativa)
	AvgWinPct    float64 `json:"avgWinPct"`
	AvgLossPct   float64 `json:"avgLossPct"`
	AvgHoldDays  float64 `json:"avgHoldDays"`
	ExposurePct  float64 `json:"exposurePct"` // % do tempo comprado
}

// fin troca NaN/infinito por 0 (o JSON não aceita NaN).
func fin(x float64) float64 {
	if math.IsNaN(x) || math.IsInf(x, 0) {
		return 0
	}
	return x
}

const yearMs = 365.25 * 24 * 3600 * 1000

// calcMetrics: eq = patrimônio no fechamento de cada vela (o primeiro valor é o saldo inicial, antes de
// operar); low = patrimônio na mínima de cada vela (para a maior queda); ppy = velas por ano.
func calcMetrics(eq, low []float64, times []int64, ppy float64) Metrics {
	var m Metrics
	if len(eq) < 2 {
		return m
	}
	m.ReturnPct = (eq[len(eq)-1]/eq[0] - 1) * 100
	m.Years = float64(times[len(times)-1]-times[0]) / yearMs

	n := len(eq) - 1
	var sum, sum2, down2 float64
	for i := 1; i < len(eq); i++ {
		r := eq[i]/eq[i-1] - 1
		sum += r
		sum2 += r * r
		if r < 0 {
			down2 += r * r
		}
	}
	mean := sum / float64(n)
	if n > 1 {
		std := math.Sqrt(math.Max(0, (sum2-float64(n)*mean*mean)/float64(n-1)))
		m.VolPct = std * math.Sqrt(ppy) * 100
		if std > 0 {
			m.Sharpe = mean / std * math.Sqrt(ppy)
		}
	}
	if dd := math.Sqrt(down2 / float64(n)); dd > 0 {
		m.Sortino = mean / dd * math.Sqrt(ppy)
	}
	if m.Years > 0 && eq[len(eq)-1] > 0 {
		m.CAGRPct = (math.Pow(eq[len(eq)-1]/eq[0], 1/m.Years) - 1) * 100
	}

	peak := eq[0]
	for i := range eq {
		trough := eq[i]
		if low != nil && i < len(low) {
			trough = math.Min(trough, low[i])
		}
		if dd := (peak - trough) / peak * 100; dd > m.MaxDDPct {
			m.MaxDDPct = dd
		}
		if eq[i] > peak {
			peak = eq[i]
		}
	}
	if m.MaxDDPct > 0 {
		m.Calmar = m.CAGRPct / m.MaxDDPct
	}
	m.SharpeT = m.Sharpe * math.Sqrt(math.Max(m.Years, 0))
	m.Significant = m.SharpeT > 1.96
	return m.clean()
}

// addTrades completa as métricas por operação (só as vendas contam como operação fechada).
func (m *Metrics) addTrades(trades []Trade) {
	var grossWin, grossLoss, sumPct, sumWin, sumLoss, hold float64
	var wins, losses int
	for _, t := range trades {
		if t.Side != "VENDA" {
			continue
		}
		sumPct += t.PnLPct
		hold += float64(t.Time-t.EntryTime) / float64(24*time.Hour/time.Millisecond)
		if t.PnL > 0 {
			wins++
			grossWin += t.PnL
			sumWin += t.PnLPct
		} else {
			losses++
			grossLoss -= t.PnL
			sumLoss += t.PnLPct
		}
	}
	n := wins + losses
	m.Trades = n
	if n == 0 {
		return
	}
	m.WinRate = float64(wins) / float64(n) * 100
	m.AvgTradePct = sumPct / float64(n)
	m.AvgHoldDays = hold / float64(n)
	if wins > 0 {
		m.AvgWinPct = sumWin / float64(wins)
	}
	if losses > 0 {
		m.AvgLossPct = sumLoss / float64(losses)
	}
	switch {
	case grossLoss > 0:
		m.ProfitFactor = grossWin / grossLoss
	case grossWin > 0:
		m.ProfitFactor = 999
	}
	*m = m.clean()
}

func (m Metrics) clean() Metrics {
	for _, p := range []*float64{&m.ReturnPct, &m.CAGRPct, &m.VolPct, &m.Sharpe, &m.Sortino, &m.MaxDDPct, &m.Calmar,
		&m.Years, &m.SharpeT, &m.WinRate, &m.ProfitFactor, &m.AvgTradePct, &m.AvgWinPct, &m.AvgLossPct, &m.AvgHoldDays, &m.ExposurePct} {
		*p = fin(*p)
	}
	return m
}
