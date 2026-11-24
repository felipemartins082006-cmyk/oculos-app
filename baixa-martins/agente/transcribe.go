package main

// Transcrição pelo Whisper: ouve o áudio e escreve o que é cantado/falado, com o tempo de cada palavra.

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"unicode/utf8"
)

func tmpDir() string        { return filepath.Join(configDir(), "tmp") }
func transcriptDir() string { return filepath.Join(configDir(), "transcricoes") }
func transcriptPath(id string) string {
	return filepath.Join(transcriptDir(), id+".json")
}

// ---------- Saída do whisper-cli (-ojf) ----------

type wTok struct {
	Text    string `json:"text"`
	Offsets *struct {
		From int64 `json:"from"`
		To   int64 `json:"to"`
	} `json:"offsets"`
	P float64 `json:"p"`
}

type wSeg struct {
	Offsets struct {
		From int64 `json:"from"`
		To   int64 `json:"to"`
	} `json:"offsets"`
	Text   string `json:"text"`
	Tokens []wTok `json:"tokens"`
}

type wOut struct {
	Result struct {
		Language string `json:"language"`
	} `json:"result"`
	Transcription []wSeg `json:"transcription"`
}

// Versões antigas do Whisper cortam letras acentuadas no meio (bytes inválidos no JSON).
// Guardamos cada byte inválido como um caractere especial e juntamos de volta depois.
func sanitizeJSON(b []byte) []byte {
	var out bytes.Buffer
	out.Grow(len(b) + 64)
	for len(b) > 0 {
		r, n := utf8.DecodeRune(b)
		if r == utf8.RuneError && n <= 1 {
			out.WriteRune(rune(0xF700 + int(b[0])))
			b = b[1:]
			continue
		}
		if r < 0x20 { // tab/quebra de linha dentro do texto quebram o JSON
			out.WriteByte(' ')
		} else {
			out.Write(b[:n])
		}
		b = b[n:]
	}
	return out.Bytes()
}

func restoreBytes(s string) string {
	if !strings.ContainsFunc(s, func(r rune) bool { return r >= 0xF700 && r <= 0xF7FF }) {
		return s
	}
	var b []byte
	for _, r := range s {
		if r >= 0xF700 && r <= 0xF7FF {
			b = append(b, byte(r-0xF700))
		} else {
			b = utf8.AppendRune(b, r)
		}
	}
	return strings.ToValidUTF8(string(b), "")
}

type rawSeg struct {
	T0, T1 float64
	Text   string
	Words  []Word
}

func parseWhisper(b []byte) (string, []rawSeg, error) {
	var w wOut
	if err := json.Unmarshal(sanitizeJSON(b), &w); err != nil {
		return "", nil, fmt.Errorf("não entendi o resultado do Whisper: %w", err)
	}
	var segs []rawSeg
	for _, s := range w.Transcription {
		rs := rawSeg{T0: float64(s.Offsets.From) / 1000, T1: float64(s.Offsets.To) / 1000, Text: strings.TrimSpace(restoreBytes(s.Text))}
		// tokens -> palavras (token que começa com espaço começa palavra nova)
		var cur *Word
		var curRaw string
		flush := func() {
			if cur != nil {
				cur.W = strings.TrimSpace(restoreBytes(curRaw))
				if cur.W != "" {
					rs.Words = append(rs.Words, *cur)
				}
			}
			cur, curRaw = nil, ""
		}
		for _, t := range s.Tokens {
			txt := t.Text
			if strings.HasPrefix(txt, "[_") || strings.HasPrefix(txt, "<|") {
				continue
			}
			var t0, t1 float64 = -1, -1
			if t.Offsets != nil {
				t0, t1 = float64(t.Offsets.From)/1000, float64(t.Offsets.To)/1000
			}
			if cur == nil || strings.HasPrefix(txt, " ") {
				flush()
				cur = &Word{T0: t0, T1: t1}
			} else if t1 >= 0 {
				cur.T1 = t1
			}
			if cur.T0 < 0 {
				cur.T0 = t0
			}
			curRaw += txt
		}
		flush()
		fixWordTimes(&rs)
		segs = append(segs, rs)
	}
	return w.Result.Language, segs, nil
}

// Palavras sem tempo (ou fora do trecho): espalha pelo tempo do trecho, proporcional ao tamanho.
func fixWordTimes(s *rawSeg) {
	bad := len(s.Words) == 0
	for _, w := range s.Words {
		if w.T0 < 0 || w.T1 < w.T0 || w.T0 < s.T0-1 || w.T1 > s.T1+1 {
			bad = true
		}
	}
	if !bad {
		for i := range s.Words {
			s.Words[i].T0 = clamp(s.Words[i].T0, s.T0, s.T1)
			s.Words[i].T1 = clamp(s.Words[i].T1, s.Words[i].T0, s.T1)
		}
		return
	}
	if len(s.Words) == 0 {
		for _, f := range strings.Fields(s.Text) {
			s.Words = append(s.Words, Word{W: f})
		}
	}
	spreadWords(s.Words, s.T0, s.T1)
}

func spreadWords(ws []Word, t0, t1 float64) {
	total := 0
	for _, w := range ws {
		total += utf8.RuneCountInString(w.W) + 1
	}
	if total == 0 {
		return
	}
	t := t0
	for i := range ws {
		d := (t1 - t0) * float64(utf8.RuneCountInString(ws[i].W)+1) / float64(total)
		ws[i].T0, ws[i].T1 = t, t+d
		t += d
	}
}

func clamp(v, a, b float64) float64 {
	if v < a {
		return a
	}
	if v > b {
		return b
	}
	return v
}

// ---------- Rodar ----------

var (
	tsLineRe = regexp.MustCompile(`^\[(\d+):(\d+):(\d+)[.,](\d+) --> (\d+):(\d+):(\d+)[.,](\d+)\]\s*(.*)$`)
	ppRe     = regexp.MustCompile(`progress =\s*(\d+)%`)
)

func hms(h, m, s, ms string) float64 {
	a, _ := strconv.Atoi(h)
	b, _ := strconv.Atoi(m)
	c, _ := strconv.Atoi(s)
	d, _ := strconv.Atoi((ms + "000")[:3])
	return float64(a*3600+b*60+c) + float64(d)/1000
}

func threads() int {
	if isAndroid() {
		// celular: os núcleos rápidos (os outros são econômicos e atrasam)
		return min(4, runtime.NumCPU())
	}
	n := runtime.NumCPU() - 1
	if n < 1 {
		n = 1
	}
	if n > 12 {
		n = 12
	}
	return n
}

func runTranscribe(j *Job) error {
	ctx := j.ctx
	it := getItem(j.ItemID)
	if it == nil {
		return errors.New("esse arquivo não está mais na biblioteca")
	}
	if _, err := os.Stat(it.File); err != nil {
		return errors.New("não achei o arquivo no computador (foi apagado ou movido?)")
	}
	updJob(j, func(j *Job) { j.Phase = "Preparando a inteligência artificial…" })
	if err := ensureTools(ctx, "ffmpeg", "whisper"); err != nil {
		return err
	}
	updJob(j, func(j *Job) { j.Phase = "Baixando o modelo de IA (só na primeira vez)…" })
	modelFile, err := ensureModel(ctx, j.Model)
	if err != nil {
		return err
	}
	_ = os.MkdirAll(tmpDir(), 0o755)
	_ = os.MkdirAll(transcriptDir(), 0o755)

	// 1) áudio em 16 kHz mono (o formato que o Whisper entende)
	updJob(j, func(j *Job) { j.Phase, j.Progress = "Preparando o áudio…", 0.02 })
	wav := filepath.Join("tmp", j.ID+".wav")
	defer os.Remove(filepath.Join(configDir(), wav))
	ff := []string{"-nostdin", "-hide_banner", "-loglevel", "error", "-y", "-i", it.File, "-vn", "-ac", "1", "-ar", "16000"}
	if j.Mode == "lyrics" {
		// realça a faixa da voz (tira grave e chiado), ajuda a IA a ouvir o cantor
		ff = append(ff, "-af", "highpass=f=90,lowpass=f=8000,dynaudnorm=f=250:g=15")
	}
	ff = append(ff, "-c:a", "pcm_s16le", wav)
	c := hiddenCmdCtx(ctx, toolPath("ffmpeg"), ff...)
	c.Dir = configDir()
	if out, err := c.CombinedOutput(); err != nil {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		log.Printf("ffmpeg: %v %s", err, out)
		return errors.New("não consegui ler o áudio desse arquivo")
	}

	// 2) Whisper. Caminhos relativos (pasta de configurações): funciona mesmo com acentos no nome do usuário.
	outBase := filepath.Join("tmp", j.ID)
	defer os.Remove(filepath.Join(configDir(), outBase+".json"))
	rel, _ := filepath.Rel(configDir(), modelFile)
	args := []string{"-m", rel, "-f", wav, "-of", outBase, "-ojf", "-pp", "-t", strconv.Itoa(threads()), "-mc", "0"}
	lang := j.Lang
	if lang == "" {
		lang = "auto"
	}
	args = append(args, "-l", lang)
	if j.Translate {
		args = append(args, "-tr")
	}
	if j.Mode == "lyrics" {
		args = append(args, "-sns")
	}
	updJob(j, func(j *Job) { j.Phase, j.Progress, j.Lines = "Ouvindo e escrevendo…", 0.05, nil })
	if err := runWhisper(j, args); err != nil {
		return err
	}
	b, err := os.ReadFile(filepath.Join(configDir(), outBase+".json"))
	if err != nil {
		return errors.New("o Whisper não gerou o resultado")
	}
	detected, segs, err := parseWhisper(b)
	if err != nil {
		return err
	}
	if j.Translate {
		detected = "en"
	}
	t := buildTranscript(segs, j.Mode, it.Duration)
	t.ItemID, t.Lang, t.Model, t.Translated = it.ID, detected, j.Model, j.Translate
	if len(t.Sections) == 0 || countLines(t) == 0 {
		if j.Mode == "lyrics" {
			return errors.New("não ouvi nenhuma voz nessa música (é instrumental?)")
		}
		return errors.New("não ouvi ninguém falando nesse vídeo")
	}
	if err := saveTranscript(t); err != nil {
		return err
	}
	writeSidecar(it, t)
	updateItem(it.ID, func(i *Item) { i.Transcript, i.Lang = j.Mode, detected })
	broadcast(map[string]any{"type": "transcript", "id": it.ID})
	return nil
}

func runWhisper(j *Job, args []string) error {
	c := hiddenCmdCtx(j.ctx, toolPath("whisper-cli"), args...)
	c.Dir = configDir()
	c.Env = toolEnv()
	stdout, _ := c.StdoutPipe()
	stderr, _ := c.StderrPipe()
	if err := c.Start(); err != nil {
		return fmt.Errorf("não consegui abrir o Whisper: %w", err)
	}
	stop := context.AfterFunc(j.ctx, func() { killTree(c) })
	defer stop()
	var errTail []string
	var mu sync.Mutex
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		sc := bufio.NewScanner(stderr)
		sc.Buffer(make([]byte, 64<<10), 1<<20)
		for sc.Scan() {
			l := sc.Text()
			if m := ppRe.FindStringSubmatch(l); m != nil {
				p, _ := strconv.Atoi(m[1])
				updJob(j, func(j *Job) { j.Progress = maxf(j.Progress, 0.05+0.93*float64(p)/100) })
				continue
			}
			mu.Lock()
			errTail = append(errTail, l)
			if len(errTail) > 30 {
				errTail = errTail[1:]
			}
			mu.Unlock()
		}
	}()
	sc := bufio.NewScanner(stdout)
	sc.Buffer(make([]byte, 64<<10), 1<<20)
	for sc.Scan() {
		m := tsLineRe.FindStringSubmatch(strings.TrimSpace(sc.Text()))
		if m == nil {
			continue
		}
		ll := LiveLine{hms(m[1], m[2], m[3], m[4]), hms(m[5], m[6], m[7], m[8]), strings.TrimSpace(m[9])}
		if ll.Text == "" || isJunk(ll.Text, j.Mode) {
			continue
		}
		updJob(j, func(j *Job) {
			j.Lines = append(j.Lines, ll)
			if len(j.Lines) > 6 {
				j.Lines = j.Lines[len(j.Lines)-6:]
			}
			if j.Duration > 0 {
				j.Progress = maxf(j.Progress, 0.05+0.93*clamp(ll.T1/j.Duration, 0, 1))
			}
		})
	}
	wg.Wait()
	err := c.Wait()
	if j.ctx.Err() != nil {
		return j.ctx.Err()
	}
	if err != nil {
		mu.Lock()
		tail := strings.Join(errTail, "\n")
		mu.Unlock()
		log.Printf("whisper: %v\n%s", err, tail)
		low := strings.ToLower(tail)
		if strings.Contains(low, "failed to load model") || strings.Contains(low, "invalid model") {
			return errors.New("o modelo de IA está corrompido. Apague a pasta 'modelos' nas configurações e tente de novo")
		}
		if strings.Contains(low, "unknown language") {
			return errors.New("idioma não reconhecido pelo Whisper")
		}
		return errors.New("o Whisper parou com erro (veja o registro em Configurações)")
	}
	return nil
}

func countLines(t *Transcript) int {
	n := 0
	for _, s := range t.Sections {
		n += len(s.Lines)
	}
	return n
}

func saveTranscript(t *Transcript) error {
	for i := range t.Sections { // o app espera listas, não null
		if t.Sections[i].Lines == nil {
			t.Sections[i].Lines = []Line{}
		}
	}
	_ = os.MkdirAll(transcriptDir(), 0o755)
	b, _ := json.Marshal(t)
	tmp := transcriptPath(t.ItemID) + ".tmp"
	if err := os.WriteFile(tmp, b, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, transcriptPath(t.ItemID))
}

func loadTranscript(id string) (*Transcript, error) {
	b, err := os.ReadFile(transcriptPath(id))
	if err != nil {
		return nil, err
	}
	var t Transcript
	if err := json.Unmarshal(b, &t); err != nil {
		return nil, err
	}
	return &t, nil
}

// .lrc ao lado da música / .srt ao lado do vídeo: outros players (VLC, Poweramp…) mostram sozinhos.
func writeSidecar(it *Item, t *Transcript) {
	base := strings.TrimSuffix(it.File, filepath.Ext(it.File))
	if it.Kind == "audio" {
		_ = os.WriteFile(base+".lrc", []byte(exportLRC(it, t)), 0o644)
	} else {
		_ = os.WriteFile(base+".srt", []byte(exportSRT(t)), 0o644)
	}
}
