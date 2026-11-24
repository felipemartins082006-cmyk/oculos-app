package main

import (
	"math"
	"testing"
	"time"
)

func TestAUCKnownValues(t *testing.T) {
	// separação perfeita
	if a := aucScore([]float64{0.1, 0.2, 0.8, 0.9}, []bool{false, false, true, true}); a != 1 {
		t.Fatalf("AUC perfeita = %v", a)
	}
	// invertida
	if a := aucScore([]float64{0.9, 0.8, 0.2, 0.1}, []bool{false, false, true, true}); a != 0 {
		t.Fatalf("AUC invertida = %v", a)
	}
	// todos empatados: 0,5
	if a := aucScore([]float64{0.5, 0.5, 0.5, 0.5}, []bool{false, true, false, true}); a != 0.5 {
		t.Fatalf("AUC com empate = %v", a)
	}
	// exemplo à mão: positivos {0.35, 0.8}, negativos {0.1, 0.4}: pares ganhos 3 de 4
	if a := aucScore([]float64{0.1, 0.4, 0.35, 0.8}, []bool{false, false, true, true}); math.Abs(a-0.75) > 1e-12 {
		t.Fatalf("AUC = %v, esperava 0,75", a)
	}
}

func mlTestParams(kind string) Params {
	p := defaultParams()
	p.Strategy, p.MLModel, p.MLLags, p.MLTrainDays, p.MLRetrainDays = "ml", kind, 2, 60, 7
	return p
}

func TestDiagnoseFindsEdgeOnMeanReversion(t *testing.T) {
	ks := meanReverting(6000, -0.3, 3)
	o := BTOptions{FeePct: 0.1, TradeFrom: ks[2000].OpenTime, Interval: time.Hour}
	for _, kind := range []string{"logit", "ar"} {
		d, err := mlDiagnose(ks, mlTestParams(kind), o)
		if err != nil {
			t.Fatal(err)
		}
		if d.TP+d.FP+d.TN+d.FN != d.Candles {
			t.Fatalf("%s: a matriz de confusão não soma o total de velas", kind)
		}
		if d.Candles < 3900 {
			t.Fatalf("%s: só avaliou %d velas", kind, d.Candles)
		}
		if d.AUC < 0.55 || d.Accuracy < 55 || d.EVGross <= 0 || d.ExcessPred <= 0 {
			t.Fatalf("%s: deveria achar a reversão à média: AUC %.3f acerto %.1f EV %.4f excesso %.2f", kind, d.AUC, d.Accuracy, d.EVGross, d.ExcessPred)
		}
		if d.NegLag1Share < 90 {
			t.Fatalf("%s: o peso do último retorno deveria ser negativo em quase todos os treinos (%.0f%%)", kind, d.NegLag1Share)
		}
		if d.EVNet >= d.EVGross {
			t.Fatalf("%s: as taxas deveriam reduzir o valor esperado", kind)
		}
		// fechar e reabrir a cada vela com 0,1% por lado custa ~0,2% por vela: mais que a vantagem deste exemplo
		if math.Abs(d.EVGross-d.EVNet-0.2) > 0.01 {
			t.Fatalf("%s: custo por vela %.4f%%, esperava 0,2%%", kind, d.EVGross-d.EVNet)
		}
		if d.NoDiscrimination {
			t.Fatalf("%s: o modelo prevê alta e baixa, não deveria ser marcado como sem discriminação", kind)
		}
	}
}

func TestDiagnoseRandomWalkHasNoEdgeAndIsDeterministic(t *testing.T) {
	ks := meanReverting(6000, 0, 4)
	o := BTOptions{FeePct: 0.1, TradeFrom: ks[2000].OpenTime, Interval: time.Hour}
	d, err := mlDiagnose(ks, mlTestParams("logit"), o)
	if err != nil {
		t.Fatal(err)
	}
	if d.AUC < 0.46 || d.AUC > 0.54 {
		t.Fatalf("passeio aleatório: AUC %.3f deveria ficar perto de 0,5", d.AUC)
	}
	if d.EVNet >= 0 {
		t.Fatalf("passeio aleatório com taxas: valor esperado líquido %.4f deveria ser negativo", d.EVNet)
	}
	d2, _ := mlDiagnose(ks, mlTestParams("logit"), o)
	if d2.RandomTotal != d.RandomTotal || d2.ExcessPred != d.ExcessPred {
		t.Fatal("a comparação com o acaso deveria dar o mesmo resultado sempre (semente fixa)")
	}
}

func TestDiagnoseRequiresML(t *testing.T) {
	ks := meanReverting(500, 0, 1)
	if _, err := mlDiagnose(ks, defaultParams(), BTOptions{Interval: time.Hour}); err == nil {
		t.Fatal("deveria recusar estratégias que não são o modelo de previsão")
	}
	if _, err := mlCrossValidate(ks, defaultParams(), BTOptions{Interval: time.Hour}, 30); err == nil {
		t.Fatal("deveria recusar estratégias que não são o modelo de previsão")
	}
}

func TestCrossValidationRespectsTime(t *testing.T) {
	ks := meanReverting(8000, -0.3, 5)
	o := BTOptions{FeePct: 0.1, TradeFrom: ks[0].OpenTime, Interval: time.Hour}
	res, err := mlCrossValidate(ks, mlTestParams("logit"), o, 30)
	if err != nil {
		t.Fatal(err)
	}
	count := map[string]int{}
	var prevRollingTest, prevExpandingTest int64 = -1, -1
	for _, f := range res.Folds {
		count[f.Method]++
		if f.TrainFrom >= f.TestFrom || f.TestFrom >= f.TestTo {
			t.Fatalf("%s %s: o treino precisa vir antes do teste", f.Method, f.Label)
		}
		// o treino termina antes do teste começar: o modelo nunca vê o período em que é avaliado
		if f.TrainRows <= 0 || f.TestRows <= 0 {
			t.Fatalf("%s %s: janela vazia", f.Method, f.Label)
		}
		switch f.Method {
		case "rolling":
			if f.TestFrom <= prevRollingTest {
				t.Fatalf("janelas rolantes de teste sobrepostas")
			}
			prevRollingTest = f.TestTo
			if f.TrainRows != 60*24 {
				t.Fatalf("janela rolante deveria treinar com 60 dias (%d linhas)", f.TrainRows)
			}
		case "expanding":
			if f.TestFrom <= prevExpandingTest {
				t.Fatalf("janelas crescentes de teste sobrepostas")
			}
			prevExpandingTest = f.TestTo
			if f.TrainFrom != res.Folds[0].TrainFrom {
				t.Fatalf("janela crescente deveria começar sempre no início")
			}
		}
	}
	if count["split"] != 6 || count["rolling"] < 5 || count["expanding"] < 5 {
		t.Fatalf("janelas por método: %v", count)
	}
	for _, s := range res.Summary {
		if s.MeanEV <= 0 || s.Positive < s.Total*8/10 {
			t.Fatalf("%s: na série com reversão à média a vantagem deveria aparecer em quase todas as janelas (%+v)", s.Method, s)
		}
	}
	// os testes da janela crescente têm sempre mais dados de treino que os anteriores
	var lastRows int
	for _, f := range res.Folds {
		if f.Method == "expanding" {
			if f.TrainRows <= lastRows {
				t.Fatal("a janela crescente deveria aumentar o treino a cada passo")
			}
			lastRows = f.TrainRows
		}
	}
}
