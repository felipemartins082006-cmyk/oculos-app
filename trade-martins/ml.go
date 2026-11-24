package main

// Modelos de previsão, seguindo o curso "Quant Trading Accelerator" (MemLabs), aulas 4 a 9:
//
//   logit  classificação logística: probabilidade de a próxima vela fechar em ALTA (aulas 6 e 8)
//   ar     regressão auto-regressiva com perda de Huber: prevê o próximo log-retorno (aulas 4, 5 e 9)
//   + um modelo do TAMANHO do movimento |retorno| (exercício da aula 6: retorno = sinal × tamanho),
//     usado para estimar o ganho esperado e compará-lo com as taxas.
//
// Entradas ("features"): os últimos N log-retornos (os "lags" do curso).
//
// Diferenças em relação ao curso, de propósito:
//   - a padronização (média/desvio) é calculada SÓ com os dados de treino e aplicada depois — usar os dados
//     de teste para padronizar vaza informação do futuro;
//   - o treino usa métodos exatos (Newton/IRLS) em vez de gradiente com semente: mesmo dado → mesmo modelo,
//     sem depender de taxa de aprendizado e número de épocas;
//   - o modelo é re-treinado periodicamente com uma janela móvel do passado (a "janela rolante" da aula 7),
//     sempre nas mesmas datas, então o robô ao vivo e o backtest usam exatamente os mesmos modelos.

import (
	"math"
	"sort"
)

type MLModel struct {
	Kind      string    // logit | ar
	W         []float64 // pesos (das entradas padronizadas)
	B         float64   // viés
	Mean, Std []float64 // padronização das entradas (do treino)
	// modelo do tamanho do movimento (|r|)
	MW          []float64
	MB          float64
	MMean, MStd []float64
	AvgAbs      float64 // tamanho médio do movimento no treino
	Rows        int     // linhas de treino
	TrainedAt   int64   // data (abertura da vela) em que foi treinado
}

func sigmoid(z float64) float64 {
	if z >= 0 {
		return 1 / (1 + math.Exp(-z))
	}
	e := math.Exp(z)
	return e / (1 + e)
}

// standardize calcula média e desvio de cada coluna.
func standardize(X [][]float64) (mean, std []float64) {
	if len(X) == 0 {
		return nil, nil
	}
	k := len(X[0])
	mean, std = make([]float64, k), make([]float64, k)
	for _, r := range X {
		for j, v := range r {
			mean[j] += v
		}
	}
	for j := range mean {
		mean[j] /= float64(len(X))
	}
	for _, r := range X {
		for j, v := range r {
			d := v - mean[j]
			std[j] += d * d
		}
	}
	for j := range std {
		std[j] = math.Sqrt(std[j] / float64(len(X)))
		if std[j] < 1e-12 {
			std[j] = 1
		}
	}
	return mean, std
}

func applyStd(x, mean, std []float64) []float64 {
	z := make([]float64, len(x))
	for j := range x {
		z[j] = (x[j] - mean[j]) / std[j]
	}
	return z
}

// solve resolve A·x = b (eliminação de Gauss com pivotamento). A é pequena (≤ 11×11).
func solve(A [][]float64, b []float64) []float64 {
	n := len(b)
	M := make([][]float64, n)
	for i := range M {
		M[i] = append(append([]float64{}, A[i]...), b[i])
	}
	for c := 0; c < n; c++ {
		p := c
		for r := c + 1; r < n; r++ {
			if math.Abs(M[r][c]) > math.Abs(M[p][c]) {
				p = r
			}
		}
		M[c], M[p] = M[p], M[c]
		if math.Abs(M[c][c]) < 1e-14 {
			return nil
		}
		for r := c + 1; r < n; r++ {
			f := M[r][c] / M[c][c]
			for k := c; k <= n; k++ {
				M[r][k] -= f * M[c][k]
			}
		}
	}
	x := make([]float64, n)
	for r := n - 1; r >= 0; r-- {
		s := M[r][n]
		for k := r + 1; k < n; k++ {
			s -= M[r][k] * x[k]
		}
		x[r] = s / M[r][r]
	}
	return x
}

// weightedLS: mínimos quadrados ponderados com intercepto e um pouco de ridge (λ) nos pesos.
// Devolve [viés, pesos...].
func weightedLS(Z [][]float64, y, w []float64, lambda float64) []float64 {
	k := len(Z[0]) + 1
	A := make([][]float64, k)
	for i := range A {
		A[i] = make([]float64, k)
	}
	b := make([]float64, k)
	row := make([]float64, k)
	for i, z := range Z {
		row[0] = 1
		copy(row[1:], z)
		wi := 1.0
		if w != nil {
			wi = w[i]
		}
		for a := 0; a < k; a++ {
			b[a] += wi * row[a] * y[i]
			for c := a; c < k; c++ {
				A[a][c] += wi * row[a] * row[c]
			}
		}
	}
	for a := 0; a < k; a++ {
		for c := 0; c < a; c++ {
			A[a][c] = A[c][a]
		}
		if a > 0 {
			A[a][a] += lambda
		}
	}
	return solve(A, b)
}

// fitLogit: regressão logística por Newton (IRLS). y ∈ {0,1}. Z já padronizado.
func fitLogit(Z [][]float64, y []float64) (w []float64, bias float64) {
	k := len(Z[0])
	beta := make([]float64, k+1) // [viés, pesos]
	lambda := 1.0                // ridge pequeno: evita pesos infinitos se os dados forem separáveis
	for it := 0; it < 30; it++ {
		// gradiente e hessiana da log-verossimilhança (com ridge)
		H := make([][]float64, k+1)
		for i := range H {
			H[i] = make([]float64, k+1)
		}
		g := make([]float64, k+1)
		row := make([]float64, k+1)
		for i, z := range Z {
			row[0] = 1
			copy(row[1:], z)
			eta := beta[0]
			for j := 0; j < k; j++ {
				eta += beta[j+1] * z[j]
			}
			p := sigmoid(eta)
			wi := p * (1 - p)
			for a := 0; a <= k; a++ {
				g[a] += (y[i] - p) * row[a]
				for c := a; c <= k; c++ {
					H[a][c] += wi * row[a] * row[c]
				}
			}
		}
		for a := 0; a <= k; a++ {
			for c := 0; c < a; c++ {
				H[a][c] = H[c][a]
			}
			if a > 0 {
				g[a] -= lambda * beta[a]
				H[a][a] += lambda
			}
		}
		step := solve(H, g)
		if step == nil {
			break
		}
		maxStep := 0.0
		for a := range beta {
			beta[a] += step[a]
			maxStep = math.Max(maxStep, math.Abs(step[a]))
		}
		if maxStep < 1e-9 {
			break
		}
	}
	return beta[1:], beta[0]
}

// fitHuber: regressão linear com perda de Huber (robusta a movimentos extremos), por IRLS.
func fitHuber(Z [][]float64, y []float64) (w []float64, bias float64) {
	beta := weightedLS(Z, y, nil, 1e-6)
	if beta == nil {
		return make([]float64, len(Z[0])), 0
	}
	wts := make([]float64, len(y))
	res := make([]float64, len(y))
	for it := 0; it < 30; it++ {
		for i, z := range Z {
			p := beta[0]
			for j := range z {
				p += beta[j+1] * z[j]
			}
			res[i] = y[i] - p
		}
		// escala robusta: desvio absoluto mediano
		abs := make([]float64, len(res))
		for i, r := range res {
			abs[i] = math.Abs(r)
		}
		scale := median(abs) / 0.6745
		if scale < 1e-15 {
			break
		}
		delta := 1.345 * scale
		for i, r := range res {
			if a := math.Abs(r); a <= delta {
				wts[i] = 1
			} else {
				wts[i] = delta / a
			}
		}
		nb := weightedLS(Z, y, wts, 1e-6)
		if nb == nil {
			break
		}
		diff := 0.0
		for a := range nb {
			diff = math.Max(diff, math.Abs(nb[a]-beta[a]))
		}
		beta = nb
		if diff < 1e-12 {
			break
		}
	}
	return beta[1:], beta[0]
}

func median(v []float64) float64 {
	c := append([]float64{}, v...)
	sort.Float64s(c)
	n := len(c)
	if n == 0 {
		return 0
	}
	if n%2 == 1 {
		return c[n/2]
	}
	return (c[n/2-1] + c[n/2]) / 2
}

// ---------- features ----------

// logReturns: r[i] = ln(c[i]/c[i-1]) (r[0] = NaN).
func logReturns(c []float64) []float64 {
	r := nanSeries(len(c))
	for i := 1; i < len(c); i++ {
		if c[i] > 0 && c[i-1] > 0 {
			r[i] = math.Log(c[i] / c[i-1])
		}
	}
	return r
}

// mlRow: entradas no fechamento da vela i = os últimos L log-retornos (lag 1 = o da própria vela i).
func mlRow(r []float64, i, lags int, abs bool) []float64 {
	if i-lags+1 < 1 {
		return nil
	}
	x := make([]float64, lags)
	for k := 0; k < lags; k++ {
		v := r[i-k]
		if math.IsNaN(v) {
			return nil
		}
		if abs {
			v = math.Abs(v)
		}
		x[k] = v
	}
	return x
}

// trainML treina com as linhas j em [from, to] (inclusive): entradas no fechamento de j, alvo = r[j+1].
func trainML(r []float64, from, to, lags int, kind string) *MLModel {
	var X, XA [][]float64
	var yRet, yUp, yAbs []float64
	for j := from; j <= to; j++ {
		if j+1 >= len(r) {
			break
		}
		x := mlRow(r, j, lags, false)
		if x == nil || math.IsNaN(r[j+1]) {
			continue
		}
		X = append(X, x)
		XA = append(XA, mlRow(r, j, lags, true))
		yRet = append(yRet, r[j+1])
		up := 0.0
		if r[j+1] > 0 {
			up = 1
		}
		yUp = append(yUp, up)
		yAbs = append(yAbs, math.Abs(r[j+1]))
	}
	if len(X) < 30 {
		return nil
	}
	m := &MLModel{Kind: kind, Rows: len(X)}
	m.Mean, m.Std = standardize(X)
	Z := make([][]float64, len(X))
	for i, x := range X {
		Z[i] = applyStd(x, m.Mean, m.Std)
	}
	if kind == "ar" {
		m.W, m.B = fitHuber(Z, yRet)
	} else {
		m.W, m.B = fitLogit(Z, yUp)
	}
	// tamanho do movimento
	m.MMean, m.MStd = standardize(XA)
	ZA := make([][]float64, len(XA))
	for i, x := range XA {
		ZA[i] = applyStd(x, m.MMean, m.MStd)
	}
	if beta := weightedLS(ZA, yAbs, nil, 1e-6); beta != nil {
		m.MB, m.MW = beta[0], beta[1:]
	}
	for _, v := range yAbs {
		m.AvgAbs += v
	}
	m.AvgAbs /= float64(len(yAbs))
	return m
}

// predict devolve (probabilidade de alta, retorno previsto, tamanho previsto) para a entrada x.
// Para o modelo "ar", a "probabilidade" é só 1 se o retorno previsto for positivo e 0 se negativo.
func (m *MLModel) predict(x, xa []float64) (pUp, ret, mag float64) {
	z := applyStd(x, m.Mean, m.Std)
	eta := m.B
	for j := range z {
		eta += m.W[j] * z[j]
	}
	mag = m.AvgAbs
	if m.MW != nil {
		za := applyStd(xa, m.MMean, m.MStd)
		mm := m.MB
		for j := range za {
			mm += m.MW[j] * za[j]
		}
		if mm > 0 {
			mag = mm
		}
	}
	if m.Kind == "ar" {
		ret = eta
		pUp = 0.5
		if ret > 0 {
			pUp = 1
		} else if ret < 0 {
			pUp = 0
		}
		return
	}
	pUp = sigmoid(eta)
	ret = (2*pUp - 1) * mag // ganho esperado = sinal esperado × tamanho esperado
	return
}

// mlSeries calcula, para cada vela fechada i, a previsão para a vela i+1 usando SÓ modelos treinados com
// dados anteriores (sem olhar o futuro). O modelo é re-treinado a cada `retrain` velas, sempre nas mesmas
// datas (múltiplos de retrain×duração da vela desde 1970), com as `window` linhas anteriores.
func mlSeries(ks []Kline, c []float64, p Params, durMs int64) (prob, ret, mag []float64, models []*MLModel) {
	n := len(c)
	prob, ret, mag = nanSeries(n), nanSeries(n), nanSeries(n)
	models = make([]*MLModel, n)
	r := logReturns(c)
	window := p.mlWindow(durMs)
	every := p.mlEvery(durMs)
	period := int64(every) * durMs
	var m *MLModel
	lastBlock := int64(math.MinInt64)
	for i := 0; i < n; i++ {
		block := ks[i].OpenTime / period
		if block != lastBlock {
			lastBlock = block
			// treina com as linhas cujo alvo já é conhecido no fechamento da vela i: j+1 ≤ i
			if i-window >= p.MLLags {
				m = trainML(r, i-window, i-1, p.MLLags, p.MLModel)
				if m != nil {
					m.TrainedAt = ks[i].OpenTime
				}
			} else {
				m = nil // janela de treino ainda incompleta
			}
		}
		if m == nil {
			continue
		}
		x := mlRow(r, i, p.MLLags, false)
		if x == nil {
			continue
		}
		prob[i], ret[i], mag[i] = m.predict(x, mlRow(r, i, p.MLLags, true))
		models[i] = m
	}
	return
}
