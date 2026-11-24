package main

// Monta a letra a partir do que o Whisper ouviu: linhas curtas, estrofes, refrão,
// partes instrumentais; e a transcrição dos vídeos em parágrafos. Também exporta LRC/SRT/VTT/TXT.

import (
	"fmt"
	"math"
	"regexp"
	"sort"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"golang.org/x/text/unicode/norm"
)

type Word struct {
	T0 float64 `json:"s"`
	T1 float64 `json:"e"`
	W  string  `json:"w"`
}

type Line struct {
	T0    float64 `json:"s"`
	T1    float64 `json:"e"`
	Text  string  `json:"t"`
	Words []Word  `json:"w,omitempty"`
}

type Section struct {
	Kind  string  `json:"kind"` // intro | verse | prechorus | chorus | bridge | outro | instrumental | para
	Label string  `json:"label"`
	T0    float64 `json:"s"`
	T1    float64 `json:"e"`
	Lines []Line  `json:"lines"`
}

type Transcript struct {
	ItemID     string    `json:"itemId"`
	Mode       string    `json:"mode"` // lyrics | transcript
	Lang       string    `json:"lang"`
	Model      string    `json:"model"`
	Translated bool      `json:"translated,omitempty"`
	Created    time.Time `json:"created"`
	Duration   float64   `json:"duration"`
	Offset     float64   `json:"offset"` // ajuste de sincronia (segundos)
	Edited     bool      `json:"edited,omitempty"`
	Sections   []Section `json:"sections"`
}

// Frases que o Whisper "inventa" em música/silêncio.
var junkRe = regexp.MustCompile(`(?i)(amara\.org|legendas? (pela|por) comunidade|legendado por|legenda(s)? adriana|subtitles? by|subtítulos? (por|realizados)|transcription by|^[\pP\pS\s]*$)`)

// Em música também: chamadas de canal que o Whisper imagina no fim da faixa.
var junkLyricsRe = regexp.MustCompile(`(?i)(obrigad[oa] por assistir|inscreva-se|deixe seu like|ative o sininho|thanks for watching|thank you for watching|please subscribe|www\.|\.com\b)`)
var bracketOnlyRe = regexp.MustCompile(`^[\[\(（【♪*\s]*(m[úu]sica|music|musique|música de fundo|instrumental|aplausos|applause|risos|laughter|silêncio|silence|blank_audio|inaudível|inaudible|canto|singing|cantando)?[\]\)）】♪*\s.]*$`)

func isJunk(s, mode string) bool {
	s = strings.TrimSpace(s)
	if s == "" || junkRe.MatchString(s) || bracketOnlyRe.MatchString(strings.ToLower(s)) {
		return true
	}
	return mode == "lyrics" && junkLyricsRe.MatchString(s)
}

func cleanWord(w string) string {
	w = strings.ReplaceAll(w, "♪", "")
	return strings.TrimSpace(w)
}

func buildTranscript(segs []rawSeg, mode string, duration float64) *Transcript {
	t := &Transcript{Mode: mode, Created: time.Now(), Duration: duration}
	maxLen := 46
	if mode != "lyrics" {
		maxLen = 84
	}
	var lines []Line
	repeat := 0
	for i, s := range segs {
		if isJunk(s.Text, mode) {
			continue
		}
		// o Whisper às vezes entra em loop repetindo a mesma frase
		if i > 0 && strings.EqualFold(s.Text, segs[i-1].Text) {
			repeat++
			if repeat >= 4 {
				continue
			}
		} else {
			repeat = 0
		}
		var ws []Word
		for _, w := range s.Words {
			w.W = cleanWord(w.W)
			if w.W != "" {
				ws = append(ws, w)
			}
		}
		if len(ws) == 0 {
			continue
		}
		lines = append(lines, splitLine(ws, maxLen, mode == "lyrics")...)
	}
	if duration == 0 && len(lines) > 0 {
		t.Duration = lines[len(lines)-1].T1
	}
	if mode == "lyrics" {
		for i := range lines {
			lines[i] = lyricStyle(lines[i])
		}
		t.Sections = makeStanzas(lines, t.Duration)
	} else {
		t.Sections = makeParagraphs(lines)
	}
	return t
}

func joinWords(ws []Word) string {
	p := make([]string, len(ws))
	for i, w := range ws {
		p[i] = w.W
	}
	return strings.Join(p, " ")
}

func endsPhrase(w string) bool {
	return strings.HasSuffix(w, ",") || strings.HasSuffix(w, ".") || strings.HasSuffix(w, "!") ||
		strings.HasSuffix(w, "?") || strings.HasSuffix(w, ";") || strings.HasSuffix(w, ":")
}

// Quebra frases longas em linhas: na pontuação, numa pausa ou no tamanho máximo.
func splitLine(ws []Word, maxLen int, lyrics bool) []Line {
	var out []Line
	phraseMin := maxLen / 2
	if !lyrics {
		phraseMin = maxLen * 2 / 3 // legenda: linhas mais cheias
	}
	start := 0
	n := 0
	for i := range ws {
		n += utf8.RuneCountInString(ws[i].W) + 1
		last := i == len(ws)-1
		cut := last
		if !last {
			gap := ws[i+1].T0 - ws[i].T1
			nextLen := n + utf8.RuneCountInString(ws[i+1].W)
			switch {
			case gap > 0.9 && n >= 8:
				cut = true
			case endsPhrase(ws[i].W) && n >= phraseMin:
				cut = true
			case lyrics && (strings.HasSuffix(ws[i].W, ".") || strings.HasSuffix(ws[i].W, "?") || strings.HasSuffix(ws[i].W, "!")) && n >= 10:
				cut = true
			case nextLen > maxLen:
				cut = true
			}
		}
		if cut {
			seg := append([]Word(nil), ws[start:i+1]...)
			out = append(out, Line{T0: seg[0].T0, T1: seg[len(seg)-1].T1, Text: joinWords(seg), Words: seg})
			start, n = i+1, 0
		}
	}
	// junta uma sobra de 1 palavra com a linha anterior
	if len(out) >= 2 && len(out[len(out)-1].Words) == 1 && out[len(out)-1].T0-out[len(out)-2].T1 < 0.5 {
		a, b := out[len(out)-2], out[len(out)-1]
		ws := append(append([]Word(nil), a.Words...), b.Words...)
		out = append(out[:len(out)-2], Line{T0: a.T0, T1: b.T1, Text: joinWords(ws), Words: ws})
	}
	return out
}

// Letra de música: começa com maiúscula e sem ponto/vírgula no fim da linha.
func lyricStyle(l Line) Line {
	if len(l.Words) == 0 {
		return l
	}
	first := l.Words[0].W
	r, sz := utf8.DecodeRuneInString(first)
	l.Words[0].W = string(unicode.ToUpper(r)) + first[sz:]
	lw := &l.Words[len(l.Words)-1]
	if !strings.HasSuffix(lw.W, "...") {
		lw.W = strings.TrimRight(lw.W, ",.;:")
	}
	if lw.W == "" && len(l.Words) > 1 {
		l.Words = l.Words[:len(l.Words)-1]
	}
	l.Text = joinWords(l.Words)
	return l
}

func median(v []float64) float64 {
	if len(v) == 0 {
		return 0
	}
	c := append([]float64(nil), v...)
	sort.Float64s(c)
	return c[len(c)/2]
}

func normText(s string) []string {
	s = norm.NFD.String(strings.ToLower(s))
	var b strings.Builder
	for _, r := range s {
		switch {
		case unicode.Is(unicode.Mn, r):
		case unicode.IsLetter(r) || unicode.IsDigit(r):
			b.WriteRune(r)
		default:
			b.WriteRune(' ')
		}
	}
	return strings.Fields(b.String())
}

// Parecido entre duas estrofes (0 a 1), pelas palavras em comum.
func similarity(a, b []string) float64 {
	if len(a) == 0 || len(b) == 0 {
		return 0
	}
	ca := map[string]int{}
	for _, w := range a {
		ca[w]++
	}
	common := 0
	for _, w := range b {
		if ca[w] > 0 {
			ca[w]--
			common++
		}
	}
	return 2 * float64(common) / float64(len(a)+len(b))
}

func linesText(ls []Line) string {
	p := make([]string, len(ls))
	for i, l := range ls {
		p[i] = l.Text
	}
	return strings.Join(p, " ")
}

// Divide a letra em estrofes pelas pausas, e marca refrão, pré-refrão, ponte e partes instrumentais.
func makeStanzas(lines []Line, duration float64) []Section {
	if len(lines) == 0 {
		return nil
	}
	var gaps []float64
	for i := 1; i < len(lines); i++ {
		gaps = append(gaps, lines[i].T0-lines[i-1].T1)
	}
	breakGap := clamp(median(gaps)*3, 1.6, 4.0)

	var groups [][]Line
	cur := []Line{lines[0]}
	for i := 1; i < len(lines); i++ {
		if lines[i].T0-lines[i-1].T1 >= breakGap {
			groups = append(groups, cur)
			cur = nil
		}
		cur = append(cur, lines[i])
	}
	groups = append(groups, cur)
	// estrofe muito grande: divide na maior pausa
	var split func([]Line) [][]Line
	split = func(g []Line) [][]Line {
		if len(g) <= 8 {
			return [][]Line{g}
		}
		best, bi := -1.0, len(g)/2
		for i := 3; i <= len(g)-3; i++ {
			gap := g[i].T0 - g[i-1].T1
			// prefere dividir perto do meio
			score := gap - math.Abs(float64(i)-float64(len(g))/2)*0.05
			if score > best {
				best, bi = score, i
			}
		}
		return append(split(g[:bi]), split(g[bi:])...)
	}
	var stanzas [][]Line
	for _, g := range groups {
		stanzas = append(stanzas, split(g)...)
	}

	// refrão: estrofes que se repetem
	n := len(stanzas)
	words := make([][]string, n)
	for i, s := range stanzas {
		words[i] = normText(linesText(s))
	}
	cluster := make([]int, n)
	var reps []int
	for i := range stanzas {
		cluster[i] = -1
		for c, r := range reps {
			if similarity(words[i], words[r]) >= 0.55 {
				cluster[i] = c
				break
			}
		}
		if cluster[i] < 0 {
			cluster[i] = len(reps)
			reps = append(reps, i)
		}
	}
	count := map[int]int{}
	size := map[int]int{}
	for i, c := range cluster {
		count[c]++
		size[c] += len(stanzas[i])
	}
	chorus := -1
	for c := range reps {
		if count[c] >= 2 && (chorus < 0 || count[c]*size[c] > count[chorus]*size[chorus] ||
			(count[c]*size[c] == count[chorus]*size[chorus] && c < chorus)) {
			chorus = c
		}
	}
	firstChorus, lastChorus := -1, -1
	for i, c := range cluster {
		if c == chorus {
			if firstChorus < 0 {
				firstChorus = i
			}
			lastChorus = i
		}
	}
	kinds := make([]string, n)
	for i, c := range cluster {
		switch {
		case c == chorus:
			kinds[i] = "chorus"
		case count[c] >= 2 && chorus >= 0:
			// repete sempre logo antes do refrão?
			pre := true
			for k, c2 := range cluster {
				if c2 == c && (k+1 >= n || cluster[k+1] != chorus) {
					pre = false
				}
			}
			if pre {
				kinds[i] = "prechorus"
			} else {
				kinds[i] = "verse"
			}
		case chorus >= 0 && i > firstChorus && i < lastChorus && i > 0 && cluster[i-1] == chorus && countKind(cluster[:i], chorus) >= 2:
			kinds[i] = "bridge"
		case chorus >= 0 && i > lastChorus && i == n-1 && len(stanzas[i]) <= 3:
			kinds[i] = "outro"
		default:
			kinds[i] = "verse"
		}
	}

	var out []Section
	verse := 0
	addInstr := func(kind, label string, t0, t1 float64) {
		out = append(out, Section{Kind: kind, Label: label, T0: t0, T1: t1})
	}
	if lines[0].T0 >= 6 {
		addInstr("intro", "Introdução", 0, lines[0].T0)
	}
	for i, s := range stanzas {
		if i > 0 {
			prev := stanzas[i-1]
			gap := s[0].T0 - prev[len(prev)-1].T1
			if gap >= 9 {
				addInstr("instrumental", "Instrumental", prev[len(prev)-1].T1, s[0].T0)
			}
		}
		label := ""
		switch kinds[i] {
		case "chorus":
			label = "Refrão"
		case "prechorus":
			label = "Pré-refrão"
		case "bridge":
			label = "Ponte"
		case "outro":
			label = "Final"
		default:
			verse++
			label = fmt.Sprintf("Verso %d", verse)
		}
		out = append(out, Section{Kind: kinds[i], Label: label, T0: s[0].T0, T1: s[len(s)-1].T1, Lines: s})
	}
	last := lines[len(lines)-1].T1
	if duration-last >= 8 {
		addInstr("instrumental", "Final instrumental", last, duration)
	}
	return out
}

func countKind(cl []int, c int) int {
	n := 0
	for _, v := range cl {
		if v == c {
			n++
		}
	}
	return n
}

// Transcrição de vídeo: parágrafos pelas pausas ou a cada poucas frases.
func makeParagraphs(lines []Line) []Section {
	var out []Section
	var cur []Line
	flush := func() {
		if len(cur) > 0 {
			out = append(out, Section{Kind: "para", Label: clock(cur[0].T0), T0: cur[0].T0, T1: cur[len(cur)-1].T1, Lines: cur})
		}
		cur = nil
	}
	for i, l := range lines {
		if len(cur) > 0 {
			gap := l.T0 - lines[i-1].T1
			prevEnd := strings.TrimSpace(lines[i-1].Text)
			sentence := strings.HasSuffix(prevEnd, ".") || strings.HasSuffix(prevEnd, "?") || strings.HasSuffix(prevEnd, "!")
			if gap >= 1.8 || len(cur) >= 7 || (sentence && len(cur) >= 4) {
				flush()
			}
		}
		cur = append(cur, l)
	}
	flush()
	return out
}

func clock(t float64) string {
	if t < 0 {
		t = 0
	}
	s := int(t)
	if s >= 3600 {
		return fmt.Sprintf("%d:%02d:%02d", s/3600, s/60%60, s%60)
	}
	return fmt.Sprintf("%d:%02d", s/60, s%60)
}

// ---------- Edição pelo app ----------

// O app manda o texto novo de cada linha. Se o número de palavras for o mesmo, mantém o tempo de cada uma.
func applyEdits(t *Transcript, edits map[string]string, labels map[string]string) {
	for si := range t.Sections {
		s := &t.Sections[si]
		if v, ok := labels[fmt.Sprint(si)]; ok && strings.TrimSpace(v) != "" {
			s.Label = strings.TrimSpace(v)
		}
		for li := range s.Lines {
			key := fmt.Sprintf("%d.%d", si, li)
			txt, ok := edits[key]
			if !ok {
				continue
			}
			txt = strings.Join(strings.Fields(txt), " ")
			l := &s.Lines[li]
			if txt == l.Text {
				continue
			}
			t.Edited = true
			f := strings.Fields(txt)
			if len(f) == len(l.Words) {
				for i := range f {
					l.Words[i].W = f[i]
				}
			} else {
				ws := make([]Word, len(f))
				for i := range f {
					ws[i].W = f[i]
				}
				spreadWords(ws, l.T0, l.T1)
				l.Words = ws
			}
			l.Text = txt
		}
		// linhas apagadas (texto vazio) saem
		kept := s.Lines[:0]
		for _, l := range s.Lines {
			if strings.TrimSpace(l.Text) != "" {
				kept = append(kept, l)
			}
		}
		s.Lines = kept
	}
	secs := t.Sections[:0]
	for _, s := range t.Sections {
		if len(s.Lines) > 0 || s.Kind == "instrumental" || s.Kind == "intro" {
			secs = append(secs, s)
		}
	}
	t.Sections = secs
}

// ---------- Exportar ----------

func allLines(t *Transcript) []Line {
	var out []Line
	for _, s := range t.Sections {
		for _, l := range s.Lines {
			l.T0 += t.Offset
			l.T1 += t.Offset
			out = append(out, l)
		}
	}
	return out
}

func lrcTime(t float64) string {
	if t < 0 {
		t = 0
	}
	cs := int(math.Round(t * 100))
	return fmt.Sprintf("%02d:%02d.%02d", cs/6000, cs/100%60, cs%100)
}

func exportLRC(it *Item, t *Transcript) string {
	var b strings.Builder
	if it != nil {
		fmt.Fprintf(&b, "[ti:%s]\n[ar:%s]\n", it.Title, it.Artist)
		if it.Album != "" {
			fmt.Fprintf(&b, "[al:%s]\n", it.Album)
		}
	}
	if t.Duration > 0 {
		fmt.Fprintf(&b, "[length:%s]\n", lrcTime(t.Duration)[:5])
	}
	b.WriteString("[by:Baixa Martins (Whisper)]\n\n")
	for _, s := range t.Sections {
		if len(s.Lines) == 0 {
			fmt.Fprintf(&b, "[%s]♪\n", lrcTime(s.T0+t.Offset))
			continue
		}
		for _, l := range s.Lines {
			fmt.Fprintf(&b, "[%s]%s\n", lrcTime(l.T0+t.Offset), l.Text)
		}
		// linha vazia no fim da estrofe, se houver pausa antes da próxima
		fmt.Fprintf(&b, "[%s]\n", lrcTime(s.T1+t.Offset+0.3))
	}
	return b.String()
}

func subTime(t float64, sep string) string {
	if t < 0 {
		t = 0
	}
	ms := int(math.Round(t * 1000))
	return fmt.Sprintf("%02d:%02d:%02d%s%03d", ms/3600000, ms/60000%60, ms/1000%60, sep, ms%1000)
}

// Legenda: até 2 linhas de ~42 letras, duração mínima e sem sobrepor a próxima.
type cue struct {
	T0, T1 float64
	Text   string
}

func cues(t *Transcript) []cue {
	ls := allLines(t)
	var out []cue
	for i, l := range ls {
		end := l.T1
		if end-l.T0 < 1.0 {
			end = l.T0 + 1.0
		}
		if i+1 < len(ls) && end > ls[i+1].T0 {
			end = ls[i+1].T0 - 0.01
		}
		if end <= l.T0 {
			end = l.T0 + 0.3
		}
		out = append(out, cue{l.T0, end, wrap2(l.Text, 42)})
	}
	return out
}

func wrap2(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	f := strings.Fields(s)
	best, bi := math.MaxInt, 0
	left := 0
	total := utf8.RuneCountInString(s)
	for i := 0; i < len(f)-1; i++ {
		left += utf8.RuneCountInString(f[i]) + 1
		d := int(math.Abs(float64(total - 2*left)))
		if d < best {
			best, bi = d, i+1
		}
	}
	if bi == 0 {
		return s
	}
	return strings.Join(f[:bi], " ") + "\n" + strings.Join(f[bi:], " ")
}

func exportSRT(t *Transcript) string {
	var b strings.Builder
	for i, c := range cues(t) {
		fmt.Fprintf(&b, "%d\n%s --> %s\n%s\n\n", i+1, subTime(c.T0, ","), subTime(c.T1, ","), c.Text)
	}
	return b.String()
}

func exportVTT(t *Transcript) string {
	var b strings.Builder
	b.WriteString("WEBVTT\n\n")
	for _, c := range cues(t) {
		fmt.Fprintf(&b, "%s --> %s\n%s\n\n", subTime(c.T0, "."), subTime(c.T1, "."), c.Text)
	}
	return b.String()
}

func exportTXT(it *Item, t *Transcript) string {
	var b strings.Builder
	if it != nil {
		b.WriteString(it.Title)
		if it.Artist != "" {
			b.WriteString(" — " + it.Artist)
		}
		b.WriteString("\n\n")
	}
	for _, s := range t.Sections {
		if t.Mode == "lyrics" {
			if len(s.Lines) == 0 {
				fmt.Fprintf(&b, "[%s]\n\n", s.Label)
				continue
			}
			fmt.Fprintf(&b, "[%s]\n", s.Label)
			for _, l := range s.Lines {
				b.WriteString(l.Text + "\n")
			}
			b.WriteString("\n")
		} else {
			fmt.Fprintf(&b, "[%s] %s\n\n", clock(s.T0+t.Offset), linesText(s.Lines))
		}
	}
	return strings.TrimRight(b.String(), "\n") + "\n"
}
