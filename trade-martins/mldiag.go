package main

// Diagnóstico do modelo de previsão e validação cruzada, como no curso "Quant Trading Accelerator":
//
//   aula 5  vantagem estatística = valor esperado (média) do retorno de cada operação > 0; acerto (DA) não é
//           tudo; bruto × líquido (com taxas); Sharpe anualizado.
//   aula 6  balanço do alvo; distribuição das previsões ("sem poder de discriminação = sem poder de previsão":
//           um modelo que só prevê alta é só "comprar e segurar"); matriz de confusão; precisão e acerto de
//           alta e de baixa; AUC; "excesso de previsibilidade" contra apostar ao acaso (estatística de
//           Gerko); excesso de lucro contra comprar e segurar; retorno = sinal × tamanho.
//   aula 7  validação cruzada que respeita o tempo: divisão com vários tamanhos de treino, janela rolante e
//           janela crescente; a média do valor esperado mostra se o resultado é robusto.
//
// Todas as previsões avaliadas aqui são FORA DA AMOSTRA: cada uma foi feita por um modelo treinado só com o
// passado (veja mlSeries).

import (
	"fmt"
	"math"
	"math/rand/v2"
	"sort"
	"time"
)

type MLDiag struct {
	Model    string  `json:"model"`
	Lags     int     `json:"lags"`
	Interval string  `json:"interval"`
	Candles  int     `json:"candles"`
	From     int64   `json:"from"`
	To       int64   `json:"to"`
	FeePct   float64 `json:"feePct"`

	// aula 6: balanço e poder de discriminação
	UpShare          float64 `json:"upShare"`     // % das velas que fecharam em alta
	PredUpShare      float64 `json:"predUpShare"` // % das previsões de alta
	NoDiscrimination bool    `json:"noDiscrimination"`
	TP               int     `json:"tp"`
	FP               int     `json:"fp"`
	TN               int     `json:"tn"`
	FN               int     `json:"fn"`
	Accuracy         float64 `json:"accuracy"`
	PrecisionUp      float64 `json:"precisionUp"`
	RecallUp         float64 `json:"recallUp"`
	PrecisionDown    float64 `json:"precisionDown"`
	RecallDown       float64 `json:"recallDown"`
	AUC              float64 `json:"auc"`

	// aula 5: vantagem estatística, como no curso (comprado ou vendido a cada vela, fecha e reabre)
	EVGross      float64   `json:"evGross"`      // média do log-retorno por vela, em %
	EVNet        float64   `json:"evNet"`        // descontando ida e volta (2 × taxa) a cada vela
	TotalGross   float64   `json:"totalGross"`   // soma dos log-retornos (%)
	TotalNet     float64   `json:"totalNet"`     //
	SharpeGross  float64   `json:"sharpeGross"`  // anualizado
	AvgWin       float64   `json:"avgWin"`       // % médio das velas ganhas
	AvgLoss      float64   `json:"avgLoss"`      // % médio das velas perdidas
	SpotTotalNet float64   `json:"spotTotalNet"` // no Spot: comprado se prevê alta, fora se prevê baixa, com taxas
	SpotSwitches int       `json:"spotSwitches"` // quantas vezes entrou ou saiu
	HoldTotal    float64   `json:"holdTotal"`    // comprar e segurar (soma dos log-retornos, %)
	RandomTotal  float64   `json:"randomTotal"`  // apostar ao acaso (semente fixa), bruto
	ExcessPred   float64   `json:"excessPred"`   // modelo − acaso, no fim (pontos de %)
	ExcessPosPct float64   `json:"excessPosPct"` // % do tempo em que o modelo estava à frente do acaso
	ExcessVsHold float64   `json:"excessVsHold"` // modelo (bruto) − comprar e segurar
	NegLag1Share float64   `json:"negLag1Share"` // % dos re-treinos com peso negativo no último retorno
	Retrains     int       `json:"retrains"`     //
	LastWeights  []float64 `json:"lastWeights"`  // pesos do último modelo (entradas padronizadas)
	LastBias     float64   `json:"lastBias"`
	MagCorr      float64   `json:"magCorr"` // correlação entre o tamanho previsto e o tamanho real

	Curves map[string][]Point `json:"curves"` // gross, net, spot, random, hold (log-retorno acumulado, %)
}

// aucScore: área sob a curva ROC pelo método dos postos (empates contam meio).
func aucScore(score []float64, label []bool) float64 {
	type pair struct {
		s float64
		y bool
	}
	ps := make([]pair, len(score))
	for i := range score {
		ps[i] = pair{score[i], label[i]}
	}
	sort.Slice(ps, func(a, b int) bool { return ps[a].s < ps[b].s })
	var pos, neg, rankSum float64
	for i := 0; i < len(ps); {
		j := i
		for j < len(ps) && ps[j].s == ps[i].s {
			j++
		}
		avg := float64(i+j+1) / 2 // posições i+1..j
		for k := i; k < j; k++ {
			if ps[k].y {
				rankSum += avg
				pos++
			} else {
				neg++
			}
		}
		i = j
	}
	if pos == 0 || neg == 0 {
		return 0.5
	}
	return (rankSum - pos*(pos+1)/2) / (pos * neg)
}

func corr(a, b []float64) float64 {
	n := float64(len(a))
	if n < 3 {
		return 0
	}
	var ma, mb float64
	for i := range a {
		ma += a[i]
		mb += b[i]
	}
	ma, mb = ma/n, mb/n
	var sab, saa, sbb float64
	for i := range a {
		sab += (a[i] - ma) * (b[i] - mb)
		saa += (a[i] - ma) * (a[i] - ma)
		sbb += (b[i] - mb) * (b[i] - mb)
	}
	if saa == 0 || sbb == 0 {
		return 0
	}
	return sab / math.Sqrt(saa*sbb)
}

// direction: +1 se o modelo prevê alta, −1 se prevê baixa (limite de 50%, como no curso).
func direction(model string, pUp, ret float64) float64 {
	if model == "ar" {
		if ret > 0 {
			return 1
		}
		return -1
	}
	if pUp >= 0.5 {
		return 1
	}
	return -1
}

func mlDiagnose(ks []Kline, p Params, o BTOptions) (*MLDiag, error) {
	if p.Strategy != "ml" {
		return nil, fmt.Errorf("escolha a estratégia \"Modelo de previsão (ML)\" para este teste")
	}
	dur := intervalOf(ks, o)
	pr := p.prepare(ks, dur)
	r := logReturns(pr.c)
	fee := math.Log(1 - o.FeePct/100) // custo de uma ponta, em log (negativo)

	d := &MLDiag{Model: p.MLModel, Lags: p.MLLags, Interval: fmtInterval(dur), FeePct: o.FeePct,
		Curves: map[string][]Point{}}
	var scores, mags, absNext []float64
	var labels []bool
	var gross []float64
	var cg, cn, cs, crd, ch float64
	rng := rand.New(rand.NewPCG(42, 42))
	prevPos := 0.0
	wins, losses := 0, 0
	var sumWin, sumLoss float64
	ahead := 0
	seen := map[*MLModel]bool{}
	neg := 0
	type pt struct {
		t              int64
		g, n, s, rd, h float64
	}
	var pts []pt

	for i := 0; i < len(ks)-1; i++ {
		if ks[i].OpenTime < o.TradeFrom || math.IsNaN(pr.mlP[i]) || math.IsNaN(r[i+1]) {
			continue
		}
		if d.Candles == 0 {
			d.From = ks[i+1].OpenTime
		}
		d.To = ks[i+1].CloseTime
		d.Candles++
		next := r[i+1]
		up := next > 0
		s := direction(p.MLModel, pr.mlP[i], pr.mlR[i])
		if m := pr.mlModels[i]; m != nil && !seen[m] {
			seen[m] = true
			if len(m.W) > 0 && m.W[0] < 0 {
				neg++
			}
			d.LastWeights, d.LastBias = m.W, m.B
		}
		// matriz de confusão
		switch {
		case s > 0 && up:
			d.TP++
		case s > 0 && !up:
			d.FP++
		case s < 0 && !up:
			d.TN++
		default:
			d.FN++
		}
		if up {
			d.UpShare++
		}
		if s > 0 {
			d.PredUpShare++
		}
		score := pr.mlP[i]
		if p.MLModel == "ar" {
			score = pr.mlR[i]
		}
		scores = append(scores, score)
		labels = append(labels, up)
		mags = append(mags, pr.mlM[i])
		absNext = append(absNext, math.Abs(next))

		// como no curso: comprado (+1) ou vendido (−1) a cada vela, abre e fecha
		tr := s * next
		gross = append(gross, tr)
		cg += tr
		cn += tr + 2*fee
		if tr > 0 {
			wins++
			sumWin += tr
		} else {
			losses++
			sumLoss += tr
		}
		// no Spot (sem vender a descoberto): comprado se prevê alta, fora se prevê baixa
		pos := 0.0
		if s > 0 {
			pos = 1
		}
		if pos != prevPos {
			cs += fee
			d.SpotSwitches++
		}
		cs += pos * next
		prevPos = pos
		// apostar ao acaso (semente fixa: o mesmo teste dá sempre o mesmo resultado)
		rs := float64(rng.IntN(2)*2 - 1)
		crd += rs * next
		ch += next
		if cg > crd {
			ahead++
		}
		pts = append(pts, pt{ks[i+1].CloseTime, cg, cn, cs, crd, ch})
	}
	if d.Candles < 30 {
		return nil, fmt.Errorf("poucas previsões fora da amostra (%d): aumente o período ou diminua a janela de treino", d.Candles)
	}
	n := float64(d.Candles)
	d.UpShare = d.UpShare / n * 100
	d.PredUpShare = d.PredUpShare / n * 100
	d.NoDiscrimination = d.PredUpShare > 90 || d.PredUpShare < 10
	d.Accuracy = float64(d.TP+d.TN) / n * 100
	pct := func(a, b int) float64 {
		if b == 0 {
			return 0
		}
		return float64(a) / float64(b) * 100
	}
	d.PrecisionUp, d.RecallUp = pct(d.TP, d.TP+d.FP), pct(d.TP, d.TP+d.FN)
	d.PrecisionDown, d.RecallDown = pct(d.TN, d.TN+d.FN), pct(d.TN, d.TN+d.FP)
	d.AUC = aucScore(scores, labels)
	d.EVGross, d.EVNet = cg/n*100, cn/n*100
	d.TotalGross, d.TotalNet = cg*100, cn*100
	if wins > 0 {
		d.AvgWin = sumWin / float64(wins) * 100
	}
	if losses > 0 {
		d.AvgLoss = sumLoss / float64(losses) * 100
	}
	var mean, ss float64
	for _, g := range gross {
		mean += g
	}
	mean /= n
	for _, g := range gross {
		ss += (g - mean) * (g - mean)
	}
	if sd := math.Sqrt(ss / (n - 1)); sd > 0 {
		d.SharpeGross = mean / sd * math.Sqrt(pr.ppy)
	}
	d.SpotTotalNet, d.HoldTotal, d.RandomTotal = cs*100, ch*100, crd*100
	d.ExcessPred, d.ExcessPosPct = (cg-crd)*100, float64(ahead)/n*100
	d.ExcessVsHold = (cg - ch) * 100
	d.Retrains = len(seen)
	if d.Retrains > 0 {
		d.NegLag1Share = float64(neg) / float64(d.Retrains) * 100
	}
	d.MagCorr = corr(mags, absNext)

	step := len(pts)/500 + 1
	for k, q := range pts {
		if k%step == 0 || k == len(pts)-1 {
			d.Curves["gross"] = append(d.Curves["gross"], Point{q.t, q.g * 100})
			d.Curves["net"] = append(d.Curves["net"], Point{q.t, q.n * 100})
			d.Curves["spot"] = append(d.Curves["spot"], Point{q.t, q.s * 100})
			d.Curves["random"] = append(d.Curves["random"], Point{q.t, q.rd * 100})
			d.Curves["hold"] = append(d.Curves["hold"], Point{q.t, q.h * 100})
		}
	}
	return d, nil
}

// ---------- validação cruzada (aula 7) ----------

type CVFold struct {
	Method    string  `json:"method"`
	Label     string  `json:"label"`
	TrainFrom int64   `json:"trainFrom"`
	TestFrom  int64   `json:"testFrom"`
	TestTo    int64   `json:"testTo"`
	TrainRows int     `json:"trainRows"`
	TestRows  int     `json:"testRows"`
	EV        float64 `json:"ev"`    // % por vela, bruto (como no curso)
	EVNet     float64 `json:"evNet"` // descontando ida e volta a cada vela
	Accuracy  float64 `json:"accuracy"`
	AUC       float64 `json:"auc"`
}

type CVSummary struct {
	Method    string  `json:"method"`
	Name      string  `json:"name"`
	MeanEV    float64 `json:"meanEV"`
	MeanEVNet float64 `json:"meanEVNet"`
	MeanAcc   float64 `json:"meanAcc"`
	MeanAUC   float64 `json:"meanAUC"`
	Positive  int     `json:"positive"` // janelas com valor esperado bruto > 0
	Total     int     `json:"total"`
}

type CVResult struct {
	Folds   []CVFold    `json:"folds"`
	Summary []CVSummary `json:"summary"`
	Rows    int         `json:"rows"`
}

// evalFold treina com as linhas [trainFrom, trainTo] e mede nas linhas [testFrom, testTo] (índices de vela).
func evalFold(r []float64, ks []Kline, p Params, trainFrom, trainTo, testFrom, testTo int, fee float64) (CVFold, bool) {
	f := CVFold{TrainFrom: ks[trainFrom].OpenTime, TestFrom: ks[testFrom].OpenTime, TestTo: ks[testTo].CloseTime}
	m := trainML(r, trainFrom, trainTo, p.MLLags, p.MLModel)
	if m == nil {
		return f, false
	}
	f.TrainRows = m.Rows
	var sum, net float64
	var scores []float64
	var labels []bool
	hits := 0
	for j := testFrom; j <= testTo && j+1 < len(r); j++ {
		x := mlRow(r, j, p.MLLags, false)
		if x == nil || math.IsNaN(r[j+1]) {
			continue
		}
		pu, ret, _ := m.predict(x, mlRow(r, j, p.MLLags, true))
		s := direction(p.MLModel, pu, ret)
		tr := s * r[j+1]
		sum += tr
		net += tr + 2*fee
		if (s > 0) == (r[j+1] > 0) {
			hits++
		}
		score := pu
		if p.MLModel == "ar" {
			score = ret
		}
		scores = append(scores, score)
		labels = append(labels, r[j+1] > 0)
		f.TestRows++
	}
	if f.TestRows < 10 {
		return f, false
	}
	n := float64(f.TestRows)
	f.EV, f.EVNet, f.Accuracy, f.AUC = sum/n*100, net/n*100, float64(hits)/n*100, aucScore(scores, labels)
	return f, true
}

func mlCrossValidate(ks []Kline, p Params, o BTOptions, testDays int) (*CVResult, error) {
	if p.Strategy != "ml" {
		return nil, fmt.Errorf("escolha a estratégia \"Modelo de previsão (ML)\" para este teste")
	}
	dur := intervalOf(ks, o)
	c := make([]float64, len(ks))
	for i, k := range ks {
		c[i] = k.C
	}
	r := logReturns(c)
	fee := math.Log(1 - o.FeePct/100)
	start := p.MLLags + 1
	for start < len(ks) && ks[start].OpenTime < o.TradeFrom {
		start++
	}
	last := len(ks) - 2 // a última linha precisa do retorno da vela seguinte
	N := last - start + 1
	if N < 200 {
		return nil, fmt.Errorf("poucos dados para validação cruzada (%d velas): aumente o período", N)
	}
	res := &CVResult{Rows: N}
	ms := int64(dur / time.Millisecond)
	W := p.mlWindow(ms)
	T := int(math.Round(float64(testDays) * float64(24*time.Hour/time.Millisecond) / float64(ms)))
	if T < 20 {
		T = 20
	}
	add := func(method, label string, a, b, c2, d int) {
		if f, ok := evalFold(r, ks, p, a, b, c2, d, fee); ok {
			f.Method, f.Label = method, label
			res.Folds = append(res.Folds, f)
		}
	}
	// 1) divisão no tempo com vários tamanhos de treino (40% a 90%)
	for _, frac := range []float64{0.4, 0.5, 0.6, 0.7, 0.8, 0.9} {
		cut := start + int(frac*float64(N))
		add("split", fmt.Sprintf("treino %.0f%%", frac*100), start, cut-1, cut, last)
	}
	// 2) janela rolante: treino de W velas, teste de T velas, andando T por vez
	if W+T <= N {
		k := 0
		for a := start; a+W+T-1 <= last && k < 60; a += T {
			add("rolling", fmt.Sprintf("janela %d", k+1), a, a+W-1, a+W, a+W+T-1)
			k++
		}
	}
	// 3) janela crescente: o treino começa sempre no início e cresce T por vez
	if W+T <= N {
		k := 0
		for end := start + W - 1; end+T <= last && k < 60; end += T {
			add("expanding", fmt.Sprintf("janela %d", k+1), start, end, end+1, end+T)
			k++
		}
	}
	names := map[string]string{"split": "Divisão no tempo (vários tamanhos de treino)", "rolling": "Janela rolante", "expanding": "Janela crescente"}
	for _, m := range []string{"split", "rolling", "expanding"} {
		s := CVSummary{Method: m, Name: names[m]}
		for _, f := range res.Folds {
			if f.Method != m {
				continue
			}
			s.Total++
			s.MeanEV += f.EV
			s.MeanEVNet += f.EVNet
			s.MeanAcc += f.Accuracy
			s.MeanAUC += f.AUC
			if f.EV > 0 {
				s.Positive++
			}
		}
		if s.Total > 0 {
			t := float64(s.Total)
			s.MeanEV, s.MeanEVNet, s.MeanAcc, s.MeanAUC = s.MeanEV/t, s.MeanEVNet/t, s.MeanAcc/t, s.MeanAUC/t
			res.Summary = append(res.Summary, s)
		}
	}
	if len(res.Folds) == 0 {
		return nil, fmt.Errorf("nenhuma janela de validação pôde ser avaliada: aumente o período")
	}
	return res, nil
}
