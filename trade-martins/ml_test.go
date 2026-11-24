package main

import (
	"math"
	"math/rand/v2"
	"testing"
	"time"
)

func TestLogitRecoversKnownCoefficients(t *testing.T) {
	rng := rand.New(rand.NewPCG(7, 8))
	var Z [][]float64
	var y []float64
	for i := 0; i < 40000; i++ {
		z := rng.NormFloat64()
		p := sigmoid(1.5*z - 0.3)
		up := 0.0
		if rng.Float64() < p {
			up = 1
		}
		Z = append(Z, []float64{z})
		y = append(y, up)
	}
	w, b := fitLogit(Z, y)
	if math.Abs(w[0]-1.5) > 0.06 || math.Abs(b+0.3) > 0.04 {
		t.Fatalf("logística deveria achar peso 1,5 e viés −0,3: %v %v", w, b)
	}
}

func TestHuberIgnoresOutliers(t *testing.T) {
	rng := rand.New(rand.NewPCG(3, 4))
	var Z [][]float64
	var y []float64
	for i := 0; i < 5000; i++ {
		z := rng.NormFloat64()
		v := 0.8*z + 0.1 + rng.NormFloat64()*0.2
		if i%50 == 0 { // 2% de movimentos absurdos
			v += 40
		}
		Z = append(Z, []float64{z})
		y = append(y, v)
	}
	w, b := fitHuber(Z, y)
	if math.Abs(w[0]-0.8) > 0.03 || math.Abs(b-0.1) > 0.05 {
		t.Fatalf("Huber deveria achar 0,8 e 0,1 apesar dos absurdos: %v %v", w, b)
	}
	ols := weightedLS(Z, y, nil, 0)
	if math.Abs(ols[0]-0.1) < 0.3 {
		t.Fatalf("pré-condição: mínimos quadrados comuns deveriam errar o viés (%v)", ols[0])
	}
}

// Série que "reverte à média": o modelo tem que aprender peso negativo no último retorno e acertar mais
// que 55% fora da amostra.
func meanReverting(n int, phi float64, seed uint64) []Kline {
	rng := rand.New(rand.NewPCG(seed, seed+9))
	ks := make([]Kline, n)
	p, prev := 100.0, 0.0
	for i := range ks {
		r := phi*prev + rng.NormFloat64()*0.01
		o := p
		p *= math.Exp(r)
		prev = r
		ks[i] = Kline{OpenTime: int64(i) * 3600000, CloseTime: int64(i+1)*3600000 - 1, O: o, H: math.Max(o, p), L: math.Min(o, p), C: p}
	}
	return ks
}

func TestMLLearnsMeanReversion(t *testing.T) {
	ks := meanReverting(6000, -0.3, 1)
	for _, kind := range []string{"logit", "ar"} {
		p := defaultParams()
		p.Strategy, p.MLModel, p.MLLags, p.MLTrainDays, p.MLRetrainDays = "ml", kind, 2, 60, 7
		pr := p.prepare(ks, time.Hour)
		hits, n := 0, 0
		var last *MLModel
		for i := 0; i < len(ks)-1; i++ {
			if math.IsNaN(pr.mlP[i]) {
				continue
			}
			next := math.Log(ks[i+1].C / ks[i].C)
			if (pr.mlR[i] > 0) == (next > 0) {
				hits++
			}
			n++
			last = pr.mlModels[i]
		}
		if n < 3000 {
			t.Fatalf("%s: poucas previsões (%d)", kind, n)
		}
		if acc := float64(hits) / float64(n); acc < 0.55 {
			t.Fatalf("%s: acerto fora da amostra %.3f, esperava > 0,55", kind, acc)
		}
		if last.W[0] >= 0 {
			t.Fatalf("%s: o peso do último retorno deveria ser negativo (reversão à média): %v", kind, last.W)
		}
	}
}

// Num passeio aleatório não há o que prever: acerto perto de 50%.
func TestMLOnRandomWalkHasNoEdge(t *testing.T) {
	ks := meanReverting(6000, 0, 5)
	p := defaultParams()
	p.Strategy, p.MLLags, p.MLTrainDays, p.MLRetrainDays = "ml", 3, 60, 7
	pr := p.prepare(ks, time.Hour)
	hits, n := 0, 0
	for i := 0; i < len(ks)-1; i++ {
		if math.IsNaN(pr.mlP[i]) {
			continue
		}
		if (pr.mlP[i] >= 0.5) == (ks[i+1].C > ks[i].C) {
			hits++
		}
		n++
	}
	if acc := float64(hits) / float64(n); math.Abs(acc-0.5) > 0.03 {
		t.Fatalf("passeio aleatório: acerto %.3f deveria ficar perto de 0,5", acc)
	}
}

// O modelo usado numa vela é o mesmo com qualquer começo de série (o robô ao vivo usa só as últimas
// velas): os re-treinos acontecem sempre nas mesmas datas.
func TestMLSameModelLiveAndBacktest(t *testing.T) {
	ks := meanReverting(5000, -0.2, 2)
	p := defaultParams()
	p.Strategy, p.MLLags, p.MLTrainDays, p.MLRetrainDays = "ml", 3, 20, 3
	full := p.prepare(ks, time.Hour)
	live := p.prepare(ks[4000:], time.Hour) // como o robô: só as últimas 1000 velas
	for _, i := range []int{4700, 4850, 4999} {
		a, b := full.mlP[i], live.mlP[i-4000]
		if math.IsNaN(b) || math.Abs(a-b) > 1e-12 {
			t.Fatalf("vela %d: previsão do backtest %v ≠ do robô ao vivo %v", i, a, b)
		}
	}
}
