package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// Uma música ou vídeo baixado.
type Item struct {
	ID         string    `json:"id"`      // ex.: dQw4w9WgXcQ-a (a = áudio, v = vídeo)
	VideoID    string    `json:"videoId"` // id do YouTube
	Kind       string    `json:"kind"`    // audio | video
	Title      string    `json:"title"`
	Artist     string    `json:"artist"`
	Album      string    `json:"album,omitempty"`
	Duration   float64   `json:"duration"`
	Thumb      string    `json:"thumb"`
	File       string    `json:"file"`
	Size       int64     `json:"size"`
	Ext        string    `json:"ext"`
	Width      int       `json:"width,omitempty"`
	Height     int       `json:"height,omitempty"`
	Playlist   string    `json:"playlist,omitempty"`
	Added      time.Time `json:"added"`
	Transcript string    `json:"transcript,omitempty"` // "" | lyrics | transcript
	Lang       string    `json:"lang,omitempty"`
	Missing    bool      `json:"missing,omitempty"` // o arquivo foi apagado/movido
}

var (
	libMu   sync.Mutex
	library = map[string]*Item{}
)

func libPath() string { return filepath.Join(configDir(), "biblioteca.json") }

func loadLibrary() {
	libMu.Lock()
	defer libMu.Unlock()
	b, err := os.ReadFile(libPath())
	if err != nil {
		return
	}
	var items []*Item
	if json.Unmarshal(b, &items) == nil {
		for _, it := range items {
			library[it.ID] = it
		}
	}
}

func saveLibraryLocked() {
	items := make([]*Item, 0, len(library))
	for _, it := range library {
		items = append(items, it)
	}
	sort.Slice(items, func(i, j int) bool { return items[i].Added.After(items[j].Added) })
	b, _ := json.MarshalIndent(items, "", " ")
	tmp := libPath() + ".tmp"
	if os.WriteFile(tmp, b, 0o600) == nil {
		_ = os.Rename(tmp, libPath())
	}
}

func putItem(it *Item) {
	libMu.Lock()
	library[it.ID] = it
	saveLibraryLocked()
	libMu.Unlock()
	broadcast(map[string]any{"type": "library"})
}

func getItem(id string) *Item {
	libMu.Lock()
	defer libMu.Unlock()
	it := library[id]
	if it == nil {
		return nil
	}
	c := *it
	return &c
}

func updateItem(id string, f func(*Item)) {
	libMu.Lock()
	if it := library[id]; it != nil {
		f(it)
		saveLibraryLocked()
	}
	libMu.Unlock()
	broadcast(map[string]any{"type": "library"})
}

func deleteItem(id string, removeFile bool) bool {
	libMu.Lock()
	it := library[id]
	if it == nil {
		libMu.Unlock()
		return false
	}
	delete(library, id)
	saveLibraryLocked()
	libMu.Unlock()
	if removeFile && it.File != "" {
		_ = os.Remove(it.File)
		base := it.File[:len(it.File)-len(filepath.Ext(it.File))]
		_ = os.Remove(base + ".lrc")
		_ = os.Remove(base + ".srt")
	}
	_ = os.Remove(transcriptPath(id))
	broadcast(map[string]any{"type": "library"})
	return true
}

func listLibrary() []Item {
	libMu.Lock()
	defer libMu.Unlock()
	out := make([]Item, 0, len(library))
	for _, it := range library {
		c := *it
		if _, err := os.Stat(c.File); err != nil {
			c.Missing = true
		}
		out = append(out, c)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Added.After(out[j].Added) })
	return out
}
