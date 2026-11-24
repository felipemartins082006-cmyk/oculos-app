package main

import (
	"bytes"
	"fmt"
	"strings"
	"testing"
)

// Gera um JSON igual ao do whisper-cli -ojf, cortando letras acentuadas no meio
// dos tokens (como as versões antigas fazem) para testar a recuperação.
func fakeWhisperJSON(segs [][3]any) []byte {
	var b bytes.Buffer
	b.WriteString("{\n\t\"systeminfo\": \"x\",\n\t\"result\": {\n\t\t\"language\": \"pt\"\n\t},\n\t\"transcription\": [\n")
	for si, s := range segs {
		t0 := int64(s[0].(float64) * 1000)
		t1 := int64(s[1].(float64) * 1000)
		text := s[2].(string)
		fmt.Fprintf(&b, "\t\t{\n\t\t\t\"offsets\": {\"from\": %d, \"to\": %d},\n\t\t\t\"text\": \" %s\",\n\t\t\t\"tokens\": [\n", t0, t1, text)
		b.WriteString("\t\t\t\t{\"text\": \"[_BEG_]\", \"offsets\": {\"from\": 0, \"to\": 0}, \"p\": 1},\n")
		words := strings.Fields(text)
		dur := (t1 - t0) / int64(len(words))
		for wi, w := range words {
			wb := []byte(" " + w)
			// corta o token no meio de um caractere de 2 bytes, se houver
			cut := -1
			for i := 1; i < len(wb)-1; i++ {
				if wb[i] >= 0xC0 {
					cut = i + 1
					break
				}
			}
			ws := t0 + int64(wi)*dur
			parts := [][]byte{wb}
			if cut > 0 {
				parts = [][]byte{wb[:cut], wb[cut:]}
			}
			for pi, p := range parts {
				pt0 := ws + int64(pi)*dur/int64(len(parts))
				pt1 := pt0 + dur/int64(len(parts))
				b.WriteString("\t\t\t\t{\"text\": \"")
				b.Write(p)
				fmt.Fprintf(&b, "\", \"offsets\": {\"from\": %d, \"to\": %d}, \"p\": 0.9},\n", pt0, pt1)
			}
		}
		b.WriteString("\t\t\t\t{\"text\": \"[_TT_500]\", \"offsets\": {\"from\": 0, \"to\": 0}, \"p\": 1}\n\t\t\t]\n\t\t}")
		if si < len(segs)-1 {
			b.WriteString(",")
		}
		b.WriteString("\n")
	}
	b.WriteString("\t]\n}\n")
	return b.Bytes()
}

func TestLyricsStructure(t *testing.T) {
	verse1 := []string{"Acordei cedo com o sol na janela", "O café quente e a canção dela", "Saí andando sem saber pra onde", "A cidade inteira se esconde"}
	chorus := []string{"E eu vou cantar, vou cantar até o fim", "Coração aberto, vem dançar assim", "Ninguém segura essa emoção", "É o som que bate no coração"}
	verse2 := []string{"Na esquina encontrei um velho amigo", "Falou de sonhos que levou consigo", "A noite chega devagar", "E a gente aprende a recomeçar"}
	bridge := []string{"Se o mundo parar, eu vou continuar", "Ninguém vai tirar a vontade de sonhar"}
	var segs [][3]any
	t0 := 12.0 // introdução instrumental
	add := func(ls []string) {
		for _, l := range ls {
			segs = append(segs, [3]any{t0, t0 + 3.2, l})
			t0 += 3.5
		}
		t0 += 4 // pausa entre estrofes
	}
	add(verse1)
	add(chorus)
	add(verse2)
	add(chorus)
	t0 += 10 // solo
	add(bridge)
	add(chorus)
	segs = append(segs, [3]any{t0, t0 + 2, "Legendas pela comunidade Amara.org"})

	lang, raw, err := parseWhisper(fakeWhisperJSON(segs))
	if err != nil {
		t.Fatal(err)
	}
	if lang != "pt" {
		t.Fatalf("idioma %q", lang)
	}
	if raw[0].Words[0].W != "Acordei" {
		t.Fatalf("primeira palavra %q", raw[0].Words[0].W)
	}
	// "canção" foi cortada no meio do "ç": tem que voltar inteira
	found := false
	for _, w := range raw[1].Words {
		if w.W == "canção" {
			found = true
		}
	}
	if !found {
		t.Fatalf("palavra com acento quebrada: %+v", raw[1].Words)
	}
	tr := buildTranscript(raw, "lyrics", t0+15)
	var labels []string
	for _, s := range tr.Sections {
		labels = append(labels, s.Label)
	}
	got := strings.Join(labels, " | ")
	want := "Introdução | Verso 1 | Refrão | Verso 2 | Refrão | Instrumental | Ponte | Refrão | Final instrumental"
	if got != want {
		t.Fatalf("estrutura:\n got %s\nwant %s", got, want)
	}
	for _, s := range tr.Sections {
		for _, l := range s.Lines {
			if strings.Contains(strings.ToLower(l.Text), "amara") {
				t.Fatal("frase inventada não foi removida")
			}
			if strings.HasSuffix(l.Text, ",") {
				t.Fatalf("vírgula no fim: %q", l.Text)
			}
		}
	}
	lrc := exportLRC(&Item{Title: "Teste", Artist: "Eu"}, tr)
	if !strings.Contains(lrc, "[00:12.00]Acordei cedo com o sol na janela") {
		t.Fatalf("lrc:\n%s", lrc)
	}
	txt := exportTXT(nil, tr)
	if !strings.Contains(txt, "[Refrão]\nE eu vou cantar") {
		t.Fatalf("txt:\n%s", txt)
	}
}

func TestTranscriptAndSubs(t *testing.T) {
	segs := [][3]any{
		{0.0, 4.0, "Olá pessoal, hoje eu vou mostrar como fazer um bolo de cenoura bem fofinho e rápido para o café da tarde."},
		{4.2, 7.0, "Primeiro separe os ingredientes."},
		{9.5, 12.0, "Agora bata tudo no liquidificador."},
	}
	_, raw, err := parseWhisper(fakeWhisperJSON(segs))
	if err != nil {
		t.Fatal(err)
	}
	tr := buildTranscript(raw, "transcript", 12)
	if len(tr.Sections) != 2 {
		t.Fatalf("parágrafos: %d", len(tr.Sections))
	}
	if tr.Sections[1].Label != "0:09" {
		t.Fatalf("rótulo %q", tr.Sections[1].Label)
	}
	vtt := exportVTT(tr)
	if !strings.HasPrefix(vtt, "WEBVTT") || !strings.Contains(vtt, "00:00:09.500 --> ") {
		t.Fatalf("vtt:\n%s", vtt)
	}
	for _, c := range cues(tr) {
		for _, l := range strings.Split(c.Text, "\n") {
			if len([]rune(l)) > 60 {
				t.Fatalf("linha de legenda longa demais: %q", l)
			}
		}
	}
	srt := exportSRT(tr)
	if !strings.Contains(srt, "1\n00:00:00,000 --> ") {
		t.Fatalf("srt:\n%s", srt)
	}
	// edição: mesmo número de palavras mantém tempos
	before := tr.Sections[1].Lines[0].Words[0]
	applyEdits(tr, map[string]string{"1.0": "Agora bata tudo no mixer."}, nil)
	l := tr.Sections[1].Lines[0]
	if l.Text != "Agora bata tudo no mixer." || l.Words[0].T0 != before.T0 {
		t.Fatalf("edição: %+v", l)
	}
}
