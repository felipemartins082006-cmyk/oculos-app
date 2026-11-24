package main

import (
	"context"
	"sort"
	"sync"
	"time"
)

// Linha que o Whisper acabou de escrever (aparece ao vivo no app).
type LiveLine struct {
	T0   float64 `json:"s"`
	T1   float64 `json:"e"`
	Text string  `json:"t"`
}

// Tarefa da fila: baixar ou transcrever.
type Job struct {
	ID       string     `json:"id"`
	Type     string     `json:"type"`  // download | transcribe
	State    string     `json:"state"` // fila | ativo | pronto | erro | cancelado
	Phase    string     `json:"phase"` // texto do que está fazendo agora
	Title    string     `json:"title"`
	Channel  string     `json:"channel"`
	VideoID  string     `json:"videoId"`
	Kind     string     `json:"kind"` // audio | video
	Playlist string     `json:"playlist,omitempty"`
	Progress float64    `json:"progress"`
	Speed    float64    `json:"speed,omitempty"`
	ETA      int        `json:"eta,omitempty"`
	Error    string     `json:"error,omitempty"`
	ItemID   string     `json:"itemId,omitempty"`
	Lines    []LiveLine `json:"lines,omitempty"`
	Created  time.Time  `json:"created"`
	Finished time.Time  `json:"finished,omitempty"`

	// opções
	Quality    string  `json:"quality,omitempty"`
	Format     string  `json:"format,omitempty"`
	Kbps       int     `json:"kbps,omitempty"`
	Transcribe bool    `json:"transcribe,omitempty"` // depois de baixar, transcrever
	Mode       string  `json:"mode,omitempty"`       // lyrics | transcript
	Lang       string  `json:"lang,omitempty"`
	Translate  bool    `json:"translate,omitempty"`
	Model      string  `json:"model,omitempty"`
	Duration   float64 `json:"duration,omitempty"`

	ctx    context.Context
	cancel context.CancelFunc
}

var (
	jobMu sync.Mutex
	jobs  = map[string]*Job{}
	order []string
)

func addJob(j *Job) *Job {
	j.ID = randHex(6)
	j.State = "fila"
	j.Phase = "Na fila"
	j.Created = time.Now()
	j.ctx, j.cancel = context.WithCancel(context.Background())
	jobMu.Lock()
	jobs[j.ID] = j
	order = append(order, j.ID)
	jobMu.Unlock()
	jobsChanged()
	schedule()
	return j
}

func updJob(j *Job, f func(*Job)) {
	jobMu.Lock()
	f(j)
	jobMu.Unlock()
	jobsChanged()
}

func jobSnapshot() []Job {
	jobMu.Lock()
	defer jobMu.Unlock()
	out := make([]Job, 0, len(order))
	for _, id := range order {
		if j := jobs[id]; j != nil {
			c := *j
			c.Lines = append([]LiveLine(nil), j.Lines...)
			out = append(out, c)
		}
	}
	return out
}

func cancelJob(id string) bool {
	jobMu.Lock()
	j := jobs[id]
	jobMu.Unlock()
	if j == nil {
		return false
	}
	j.cancel()
	updJob(j, func(j *Job) {
		if j.State == "fila" || j.State == "ativo" {
			j.State, j.Phase, j.Finished = "cancelado", "Cancelado", time.Now()
		}
	})
	schedule()
	return true
}

// Tira da lista o que já terminou (ou só um, se id != "").
func clearJobs(id string) {
	jobMu.Lock()
	out := order[:0]
	for _, jid := range order {
		j := jobs[jid]
		done := j.State == "pronto" || j.State == "erro" || j.State == "cancelado"
		if done && (id == "" || id == jid) {
			delete(jobs, jid)
			continue
		}
		out = append(out, jid)
	}
	order = out
	jobMu.Unlock()
	jobsChanged()
}

func retryJob(id string) {
	jobMu.Lock()
	j := jobs[id]
	jobMu.Unlock()
	if j == nil || (j.State != "erro" && j.State != "cancelado") {
		return
	}
	n := &Job{Type: j.Type, Title: j.Title, Channel: j.Channel, VideoID: j.VideoID, Kind: j.Kind,
		Playlist: j.Playlist, Quality: j.Quality, Format: j.Format, Kbps: j.Kbps, Transcribe: j.Transcribe, Mode: j.Mode,
		Lang: j.Lang, Translate: j.Translate, Model: j.Model, ItemID: j.ItemID, Duration: j.Duration}
	clearJobs(id)
	addJob(n)
}

// Começa as tarefas da fila: downloads em paralelo (configurável), transcrição uma por vez (usa muito o processador).
func schedule() {
	parallel := getSettings().Parallel
	jobMu.Lock()
	runningDL, runningTR := 0, 0
	for _, id := range order {
		j := jobs[id]
		if j.State == "ativo" {
			if j.Type == "download" {
				runningDL++
			} else {
				runningTR++
			}
		}
	}
	var start []*Job
	for _, id := range order {
		j := jobs[id]
		if j.State != "fila" {
			continue
		}
		if j.Type == "download" && runningDL < parallel {
			runningDL++
		} else if j.Type == "transcribe" && runningTR < 1 {
			runningTR++
		} else {
			continue
		}
		j.State, j.Phase = "ativo", "Começando…"
		start = append(start, j)
	}
	jobMu.Unlock()
	for _, j := range start {
		go runJob(j)
	}
	if len(start) > 0 {
		jobsChanged()
	}
}

func runJob(j *Job) {
	var err error
	switch j.Type {
	case "download":
		err = runDownload(j)
	case "transcribe":
		err = runTranscribe(j)
	}
	updJob(j, func(j *Job) {
		j.Finished = time.Now()
		if j.State == "cancelado" || j.ctx.Err() != nil {
			j.State, j.Phase = "cancelado", "Cancelado"
		} else if err != nil {
			j.State, j.Phase, j.Error = "erro", "Deu erro", err.Error()
		} else {
			j.State, j.Phase, j.Progress = "pronto", "Pronto", 1
		}
	})
	if err == nil && j.Type == "download" && j.Transcribe && j.ItemID != "" && j.ctx.Err() == nil {
		queueTranscribe(j.ItemID, j.Mode, j.Lang, j.Translate, j.Model)
	}
	schedule()
	pruneJobs()
}

// Guarda só as últimas 200 tarefas terminadas.
func pruneJobs() {
	jobMu.Lock()
	var done []*Job
	for _, id := range order {
		j := jobs[id]
		if j.State == "pronto" || j.State == "erro" || j.State == "cancelado" {
			done = append(done, j)
		}
	}
	if len(done) <= 200 {
		jobMu.Unlock()
		return
	}
	sort.Slice(done, func(a, b int) bool { return done[a].Finished.Before(done[b].Finished) })
	drop := map[string]bool{}
	for _, j := range done[:len(done)-200] {
		drop[j.ID] = true
		delete(jobs, j.ID)
	}
	out := order[:0]
	for _, id := range order {
		if !drop[id] {
			out = append(out, id)
		}
	}
	order = out
	jobMu.Unlock()
	jobsChanged()
}

func queueTranscribe(itemID, mode, lang string, translate bool, model string) *Job {
	it := getItem(itemID)
	if it == nil {
		return nil
	}
	if mode == "" {
		if it.Kind == "audio" {
			mode = "lyrics"
		} else {
			mode = "transcript"
		}
	}
	s := getSettings()
	if lang == "" {
		lang = s.Language
	}
	if model == "" {
		model = s.Model
	}
	// já tem uma igual na fila?
	jobMu.Lock()
	for _, id := range order {
		o := jobs[id]
		if o.Type == "transcribe" && o.ItemID == itemID && (o.State == "fila" || o.State == "ativo") {
			jobMu.Unlock()
			return o
		}
	}
	jobMu.Unlock()
	return addJob(&Job{Type: "transcribe", Title: it.Title, Channel: it.Artist, VideoID: it.VideoID, Kind: it.Kind,
		ItemID: itemID, Mode: mode, Lang: lang, Translate: translate, Model: model, Duration: it.Duration})
}
