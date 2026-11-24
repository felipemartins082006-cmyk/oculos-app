package main

// Pares de maior volume: a mesma regra usada na pesquisa (README) para escolher a carteira sem viés de
// sobrevivência — os N pares com mais dinheiro negociado nos últimos 30 dias, sem stablecoins, moedas
// "embrulhadas" e tokens alavancados.

import (
	"fmt"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"sync"
)

var notCrypto = map[string]bool{
	"USDC": true, "BUSD": true, "TUSD": true, "PAX": true, "USDP": true, "FDUSD": true, "DAI": true, "UST": true,
	"USTC": true, "EUR": true, "GBP": true, "AUD": true, "TRY": true, "BRL": true, "AEUR": true, "EURI": true,
	"USDE": true, "XUSD": true, "RLUSD": true, "USD1": true, "BFUSD": true, "PAXG": true, "XAUT": true,
	"WBTC": true, "WBETH": true, "BETH": true, "PYUSD": true, "USDS": true, "SUSD": true, "LUSD": true, "FRAX": true,
}

// A Binance lista ações e fundos "tokenizados" com um B no fim (NVDAB, TSLAB, SPYB…) e a API não diz quais
// são. Fora os criptoativos conhecidos abaixo, um nome terminado em B fica de fora (no pior caso perde uma
// cripto pequena, nunca uma grande).
var cryptoEndingB = map[string]bool{"BNB": true, "SHIB": true, "ARB": true, "CKB": true, "TRB": true, "DGB": true, "BB": true}

func excludedBase(base string) bool {
	if notCrypto[base] {
		return true
	}
	if strings.HasSuffix(base, "B") && !cryptoEndingB[base] {
		return true
	}
	for _, s := range []string{"UP", "DOWN", "BULL", "BEAR"} {
		if strings.HasSuffix(base, s) && len(base) > len(s)+1 {
			return true
		}
	}
	return false
}

// TopByVolume = os n pares em `quote` com maior volume (em `quote`) nos últimos 30 dias.
func (b *Binance) TopByVolume(quote string, n int) ([]string, error) {
	var tk []struct {
		Symbol      string `json:"symbol"`
		QuoteVolume string `json:"quoteVolume"`
		Count       int64  `json:"count"`
	}
	if err := b.public("/api/v3/ticker/24hr", url.Values{}, &tk); err != nil {
		return nil, err
	}
	type sv struct {
		s string
		v float64
	}
	var c []sv
	for _, t := range tk {
		if !strings.HasSuffix(t.Symbol, quote) || t.Count == 0 || excludedBase(strings.TrimSuffix(t.Symbol, quote)) {
			continue
		}
		v, _ := strconv.ParseFloat(t.QuoteVolume, 64)
		c = append(c, sv{t.Symbol, v})
	}
	sort.Slice(c, func(i, j int) bool { return c[i].v > c[j].v })
	// pré-seleção pelo volume de 24h; a ordem final usa 30 dias (um dia agitado não decide sozinho)
	if len(c) > 3*n {
		c = c[:3*n]
	}
	var mu sync.Mutex
	var wg sync.WaitGroup
	sem := make(chan struct{}, 4)
	for i := range c {
		wg.Add(1)
		go func(i int) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()
			var raw [][]any
			q := url.Values{"symbol": {c[i].s}, "interval": {"1d"}, "limit": {"31"}}
			v := 0.0
			if err := b.public("/api/v3/klines", q, &raw); err == nil && len(raw) >= 30 {
				for _, r := range raw[:len(raw)-1] { // sem o dia de hoje (ainda aberto)
					if s, ok := r[7].(string); ok {
						f, _ := strconv.ParseFloat(s, 64)
						v += f
					}
				}
			}
			mu.Lock()
			c[i].v = v // par com menos de 30 dias de história fica com 0 (fora da lista)
			mu.Unlock()
		}(i)
	}
	wg.Wait()
	sort.Slice(c, func(i, j int) bool { return c[i].v > c[j].v })
	var out []string
	for _, x := range c {
		if x.v > 0 && len(out) < n {
			out = append(out, x.s)
		}
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("nenhum par encontrado em %s", quote)
	}
	return out, nil
}
