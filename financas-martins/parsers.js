/* Leitura de extratos: OFX, CSV/TXT, Excel (XLS/XLSX) e PDF.
 * Tudo roda no navegador — nenhum arquivo sai do aparelho. */
const Parsers = (() => {
  // bibliotecas embutidas no app (funcionam sem internet)
  const PDFJS = 'vendor/pdf.min.js';
  const PDFJS_WORKER = 'vendor/pdf.worker.min.js';
  const XLSX_URL = 'vendor/xlsx.full.min.js';

  const loaded = {};
  function loadScript(src) {
    if (!loaded[src]) {
      loaded[src] = new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = src; s.onload = resolve;
        s.onerror = () => { delete loaded[src]; reject(new Error('Não consegui carregar o leitor deste tipo de arquivo.')); };
        document.head.appendChild(s);
      });
    }
    return loaded[src];
  }

  /* ---------- datas ---------- */
  const MONTHS = { jan: 1, fev: 2, feb: 2, mar: 3, abr: 4, apr: 4, mai: 5, may: 5, jun: 6, jul: 7, ago: 8, aug: 8, set: 9, sep: 9, out: 10, oct: 10, nov: 11, dez: 12, dec: 12 };
  const pad = n => String(n).padStart(2, '0');
  function mkDate(y, m, d) {
    if (y < 100) y += 2000;
    if (!(m >= 1 && m <= 12 && d >= 1 && d <= 31 && y >= 1990 && y <= 2100)) return null;
    const dt = new Date(y, m - 1, d);
    if (dt.getMonth() !== m - 1) return null;
    return `${y}-${pad(m)}-${pad(d)}`;
  }
  const DATE_RES = [
    [/^(\d{4})-(\d{2})-(\d{2})/, m => mkDate(+m[1], +m[2], +m[3])],
    [/^(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4}|\d{2})(?!\d)/, m => mkDate(+m[3], +m[2], +m[1])],
    [/^(\d{1,2})\s*(?:de\s+)?(jan|fev|feb|mar|abr|apr|mai|may|jun|jul|ago|aug|set|sep|out|oct|nov|dez|dec)[a-zç]*\.?(?:\s*(?:de\s+)?(\d{4}))?(?![a-zç])/i,
      (m, y) => mkDate(m[3] ? +m[3] : y, MONTHS[m[2].toLowerCase()], +m[1])],
    [/^(\d{1,2})\/(\d{1,2})(?![\/\d])/, (m, y) => mkDate(y, +m[2], +m[1])],
  ];
  function leadingDate(str, ctxYear) {
    const s = String(str).trimStart();
    for (const [re, fn] of DATE_RES) {
      const m = s.match(re);
      if (m) { const d = fn(m, ctxYear); if (d) return { date: d, rest: s.slice(m[0].length).trim() }; }
    }
    return null;
  }
  function parseDate(v, ctxYear) {
    if (v instanceof Date) return isNaN(v) ? null : mkDate(v.getFullYear(), v.getMonth() + 1, v.getDate());
    if (typeof v === 'number') {
      if (v > 25000 && v < 80000) { // data serial do Excel
        const d = new Date(Math.round((v - 25569) * 86400000));
        return mkDate(d.getUTCFullYear(), d.getUTCMonth() + 1, d.getUTCDate());
      }
      return null;
    }
    const r = leadingDate(v, ctxYear || new Date().getFullYear());
    return r ? r.date : null;
  }

  /* ---------- valores ---------- */
  function parseAmount(v) {
    if (typeof v === 'number') return isFinite(v) ? v : NaN;
    let s = String(v ?? '').trim();
    if (!s) return NaN;
    let neg = false;
    if (/^\(.*\)$/.test(s)) { neg = true; s = s.slice(1, -1).trim(); }
    s = s.replace(/[−–]/g, '-');
    const dc = s.match(/\s*\b([DC])$/i);
    if (dc && /\d/.test(s.slice(0, -dc[0].length))) { if (dc[1].toUpperCase() === 'D') neg = true; s = s.slice(0, -dc[0].length).trim(); }
    s = s.replace(/^r\$\s*/i, '');
    if (s.startsWith('-')) { neg = true; s = s.slice(1).trim(); } else if (s.startsWith('+')) s = s.slice(1).trim();
    s = s.replace(/^r\$\s*/i, '');
    if (s.startsWith('-')) { neg = true; s = s.slice(1).trim(); }
    if (s.endsWith('-')) { neg = true; s = s.slice(0, -1).trim(); }
    s = s.replace(/\s/g, '');
    if (!/^\d[\d.,]*$/.test(s)) return NaN;
    const lc = s.lastIndexOf(','), ld = s.lastIndexOf('.');
    if (lc > -1 && ld > -1) s = lc > ld ? s.replace(/\./g, '').replace(',', '.') : s.replace(/,/g, '');
    else if (lc > -1) s = (s.match(/,/g).length > 1) ? s.replace(/,/g, '') : s.replace(',', '.');
    else if (ld > -1 && /^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, '');
    const n = parseFloat(s);
    return isNaN(n) ? NaN : (neg ? -n : n);
  }
  // Valor em formato brasileiro dentro de uma linha de texto (ex.: "-1.234,56", "R$ 12,90", "150,00 D")
  const AMT_BR = /(?:^|\s)(\(?[-−+]?\s?(?:R\$\s?)?[-−]?(?:\d{1,3}(?:\.\d{3})+|\d+),\d{2}\)?(?:\s?-|\s[DC](?![a-zA-Z]))?)(?=\s|$)/g;
  const AMT_US = /(?:^|\s)(\(?[-−+]?\s?(?:R\$\s?)?[-−]?(?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2}\)?(?:\s?-)?)(?=\s|$)/g;

  /* ---------- sinal (entrada x saída) ---------- */
  const NEG_KW = /(compra|pagamento|pgto|pag\b|enviad|envio|saque|tarifa|d[eé]bito|debito|iof|juros|anuidade|boleto|transf.*enviad|pix\s*(env|emit|out)|aplica[cç][aã]o|seguro|parcela)/i;
  const POS_KW = /(recarga|disponibiliza|credito de beneficio|crédito de benefício|recebid|receb|sal[aá]rio|cr[eé]dito|deposito|dep[oó]sito|rendimento|estorno|reembolso|resgate|devolu|cashback|proventos|ted\s*rec|pix\s*rec)/i;
  const SKIP_RE = /\b(saldo|total|subtotal|resumo|limite dispon|valor m[ií]nimo|pagamento m[ií]nimo)\b/i;

  function guessYear(text) {
    const c = {};
    for (const m of text.matchAll(/\b(20\d{2})\b/g)) c[m[1]] = (c[m[1]] || 0) + 1;
    const best = Object.entries(c).sort((a, b) => b[1] - a[1])[0];
    return best ? +best[0] : new Date().getFullYear();
  }
  // dica do tipo de conta escolhido na importação: 'credito' força leitura de fatura; conta/vale desliga
  let cardHint = null;
  function isCardStatement(text) {
    if (cardHint !== null) return cardHint;
    const t = text.slice(0, 6000).toLowerCase();
    return /fatura/.test(t) && /(cart[aã]o|vencimento)/.test(t) && !/extrato de conta|conta corrente/.test(t);
  }

  /* ---------- texto em linhas (PDF e TXT sem colunas) ---------- */
  function parseLines(lines) {
    const all = lines.join('\n');
    const year = guessYear(all);
    let curDate = null;
    const out = [];
    for (const raw of lines) {
      const line = String(raw).replace(/ /g, ' ').replace(/\s+/g, ' ').trim();
      if (!line) continue;
      const ld = leadingDate(line, year);
      const rest = ld ? ld.rest : line;
      if (ld) curDate = ld.date;
      if (!curDate) continue;
      let amts = [...rest.matchAll(AMT_BR)];
      if (!amts.length) amts = [...rest.matchAll(AMT_US)];
      if (!amts.length) continue;
      if (SKIP_RE.test(rest)) continue;
      const first = amts[0];
      let desc = rest.slice(0, first.index).replace(/\s+\d{5,}$/, '').replace(/[|•·]+/g, ' ').trim();
      if (desc.replace(/[^a-zA-ZÀ-ú]/g, '').length < 2) continue;
      const amount = parseAmount(first[1]);
      if (isNaN(amount) || amount === 0) continue;
      const explicit = /[-−(]|\sD$|-$/.test(first[1].trim());
      out.push({
        date: curDate, description: desc, amount,
        balance: amts.length > 1 ? parseAmount(amts[amts.length - 1][1]) : null,
        explicitSign: explicit,
      });
    }
    return fixSigns(out, isCardStatement(all));
  }

  /* Decide entrada/saída quando o extrato não traz sinal */
  function fixSigns(txs, card) {
    if (!txs.length) return txs;
    if (card) {
      // fatura de cartão: quase sempre as compras vêm positivas → são saídas.
      // Se a maioria já vier negativa, o arquivo já está no formato certo.
      const pos = txs.filter(t => t.amount > 0).length;
      if (pos >= txs.length - pos) txs.forEach(t => { t.amount = -t.amount; });
      return txs;
    }
    const hasNeg = txs.some(t => t.amount < 0);
    if (hasNeg) return txs;
    // 1) pela variação do saldo, quando a linha traz saldo
    let bySaldo = 0;
    for (let i = 1; i < txs.length; i++) {
      const a = txs[i - 1].balance, b = txs[i].balance;
      if (a == null || b == null || isNaN(a) || isNaN(b)) continue;
      const delta = b - a;
      if (Math.abs(Math.abs(delta) - Math.abs(txs[i].amount)) < 0.011) {
        txs[i].amount = Math.sign(delta) * Math.abs(txs[i].amount); txs[i]._signed = true; bySaldo++;
      }
    }
    // 2) pelo texto da descrição
    txs.forEach(t => {
      if (t._signed) return;
      if (POS_KW.test(t.description)) t.amount = Math.abs(t.amount);
      else t.amount = -Math.abs(t.amount); // sem pista: a maioria dos lançamentos é gasto
    });
    return txs;
  }

  /* ---------- tabelas (CSV, Excel) ---------- */
  const H = {
    balance: /^(saldo|balance)/i,
    dc: /^(tipo|d\/c|c\/d|natureza|deb\/cred|débito\/crédito|debito\/credito|operação|operacao)$/i,
    date: /^(data|date|dt\b|dia\b)/i,
    amount: /^(valor|amount|montante|quantia|value|r\$|vlr)/i,
    credit: /(cr[eé]dito|entrada|receita|credit)/i,
    debit: /(d[eé]bito|sa[ií]da|despesa|debit)/i,
    descMain: /(descri|hist[oó]rico|title|t[ií]tulo|estabelecimento|memo)/i,
    descAlt: /(lan[cç]amento|detalhe|nome|transa[cç]|identifica|categoria)/i,
  };
  function findHeader(rows) {
    for (let i = 0; i < Math.min(rows.length, 25); i++) {
      const r = rows[i].map(c => String(c ?? '').trim());
      const map = {};
      r.forEach((c, j) => {
        if (!c || c.length > 40) return;
        if (H.balance.test(c)) { map.balance ??= j; return; }
        if (H.dc.test(c)) { map.dc ??= j; return; }
        if (H.date.test(c)) { map.date ??= j; return; }
        if (H.amount.test(c)) { map.amount ??= j; return; }
        if (H.credit.test(c)) { map.credit ??= j; return; }
        if (H.debit.test(c)) { map.debit ??= j; return; }
        if (H.descMain.test(c)) { (map.descs ||= []).push(j); return; }
        if (H.descAlt.test(c)) { map.descAlt ??= j; }
      });
      if (map.date != null && (map.amount != null || map.credit != null || map.debit != null)) {
        map.descs ||= map.descAlt != null ? [map.descAlt] : [];
        return { index: i, map, header: r };
      }
    }
    return null;
  }

  function parseRows(rows, fullText) {
    rows = rows.filter(r => r && r.some(c => String(c ?? '').trim() !== ''));
    const year = guessYear(fullText);
    const hdr = findHeader(rows);
    const out = [];
    if (hdr) {
      const { map } = hdr;
      for (const r of rows.slice(hdr.index + 1)) {
        const date = parseDate(r[map.date], year);
        if (!date) continue;
        let amount;
        if (map.amount != null) amount = parseAmount(r[map.amount]);
        else {
          const c = Math.abs(parseAmount(r[map.credit]) || 0), d = Math.abs(parseAmount(r[map.debit]) || 0);
          amount = c - d;
        }
        if (isNaN(amount) || amount === 0) continue;
        // coluna "Tipo" com Débito/Crédito: define o sinal
        if (map.dc != null) {
          const tp = String(r[map.dc] ?? '').trim().toLowerCase();
          if (/^(d\b|deb|déb|saída|saida|compra)/.test(tp)) amount = -Math.abs(amount);
          else if (/^(c\b|cred|créd|entrada|recarga)/.test(tp)) amount = Math.abs(amount);
        }
        let desc = map.descs.map(j => String(r[j] ?? '').trim()).filter(Boolean).join(' - ');
        if (!desc) desc = r.filter((c, j) => j !== map.date && j !== map.amount && j !== map.balance && isNaN(parseAmount(c)) && !parseDate(c)).join(' ').trim();
        if (SKIP_RE.test(desc) && /^saldo/i.test(desc)) continue;
        out.push({ date, description: desc || 'Sem descrição', amount, explicitSign: amount < 0,
          balance: map.balance != null ? parseAmount(r[map.balance]) : null });
      }
      // CSV de fatura (ex.: Nubank cartão: date,title,amount — compras positivas)
      const head = hdr.header.join(' ').toLowerCase();
      const cardLike = /title/.test(head) && /amount/.test(head) && !/descri/.test(head);
      return fixSigns(out, cardHint !== null ? cardHint : (cardLike || isCardStatement(fullText)));
    }
    // Sem cabeçalho: procura data, valor e texto em cada linha
    for (const r of rows) {
      const cells = r.map(c => (c instanceof Date || typeof c === 'number') ? c : String(c ?? '').trim());
      const di = cells.findIndex(c => parseDate(c, year));
      if (di < 0) continue;
      let ai = -1;
      for (let j = cells.length - 1; j > di; j--) if (cells[j] !== '' && !isNaN(parseAmount(cells[j]))) { ai = j; break; }
      if (ai < 0) continue;
      // se houver dois valores no fim, o penúltimo é o lançamento e o último o saldo
      let bal = null;
      const prev = cells[ai - 1];
      const looksMoney = c => typeof c === 'number' ? !Number.isInteger(c) : /[.,]\d{2}\)?\s*[-DC]?$/i.test(c) && !isNaN(parseAmount(c));
      if (ai - 1 > di && prev !== '' && looksMoney(prev)) {
        bal = parseAmount(cells[ai]); ai = ai - 1;
      }
      const amount = parseAmount(cells[ai]);
      if (isNaN(amount) || amount === 0) continue;
      const desc = cells.filter((c, j) => j !== di && j !== ai && typeof c === 'string' && c && isNaN(parseAmount(c))).sort((a, b) => b.length - a.length)[0] || 'Sem descrição';
      if (SKIP_RE.test(desc) && /^saldo/i.test(desc)) continue;
      out.push({ date: parseDate(cells[di], year), description: desc, amount, balance: bal, explicitSign: amount < 0 });
    }
    return fixSigns(out, isCardStatement(fullText));
  }

  /* ---------- CSV ---------- */
  function detectDelimiter(lines) {
    const sample = lines.slice(0, 30);
    let best = null, bestScore = 0;
    for (const d of [';', '\t', ',', '|']) {
      const counts = sample.map(l => splitCSVLine(l, d).length - 1).filter(n => n > 0);
      if (counts.length < Math.max(2, sample.length * 0.4)) continue;
      const mode = counts.sort((a, b) => a - b)[Math.floor(counts.length / 2)];
      const score = counts.filter(c => c === mode).length * (mode >= 1 ? 1 : 0);
      if (score > bestScore) { bestScore = score; best = d; }
    }
    return best;
  }
  function splitCSVLine(line, d) {
    const out = []; let cur = '', q = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (q) {
        if (ch === '"') { if (line[i + 1] === '"') { cur += '"'; i++; } else q = false; }
        else cur += ch;
      } else if (ch === '"') q = true;
      else if (ch === d) { out.push(cur); cur = ''; }
      else cur += ch;
    }
    out.push(cur);
    return out.map(s => s.trim());
  }

  /* ---------- OFX ---------- */
  function parseOFX(text) {
    const out = [];
    for (const b of text.split(/<STMTTRN>/i).slice(1)) {
      const get = tag => { const m = b.match(new RegExp('<' + tag + '>([^<\\r\\n]*)', 'i')); return m ? m[1].trim() : ''; };
      const d = get('DTPOSTED');
      const date = mkDate(+d.slice(0, 4), +d.slice(4, 6), +d.slice(6, 8));
      const amount = parseFloat(get('TRNAMT').replace(',', '.'));
      const description = [get('MEMO'), get('NAME')].filter(Boolean).filter((v, i, a) => a.indexOf(v) === i).join(' — ') || get('TRNTYPE');
      if (date && !isNaN(amount) && amount !== 0) out.push({ date, description, amount, explicitSign: true });
    }
    const bal = text.match(/<LEDGERBAL>[\s\S]*?<BALAMT>([^<\r\n]+)/i);
    return { transactions: out, balance: bal ? parseFloat(bal[1].replace(',', '.')) : null };
  }

  /* ---------- PDF ---------- */
  async function pdfLines(buf, askPassword, fileName) {
    await loadScript(PDFJS);
    window.pdfjsLib.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;
    let pdf, password;
    for (let attempt = 0; attempt < 4; attempt++) {
      try {
        pdf = await window.pdfjsLib.getDocument({ data: buf.slice(0), password }).promise;
        break;
      } catch (e) {
        if (e && e.name === 'PasswordException') {
          password = await askPassword(fileName, attempt > 0);
          if (password == null) throw new Error('PDF protegido por senha — leitura cancelada.');
        } else throw e;
      }
    }
    if (!pdf) throw new Error('Senha do PDF incorreta.');
    const lines = [];
    for (let p = 1; p <= pdf.numPages; p++) {
      const page = await pdf.getPage(p);
      const tc = await page.getTextContent();
      const rows = [];
      for (const it of tc.items) {
        if (!it.str) continue;
        const x = it.transform[4], y = it.transform[5];
        let row = rows.find(r => Math.abs(r.y - y) < 3);
        if (!row) { row = { y, items: [] }; rows.push(row); }
        row.items.push({ x, str: it.str, w: it.width });
      }
      rows.sort((a, b) => b.y - a.y);
      for (const r of rows) {
        r.items.sort((a, b) => a.x - b.x);
        let s = '', end = null, gap = false;
        for (const it of r.items) {
          if (!it.str.trim()) { gap = true; continue; } // item só de espaço = separação de coluna
          if (end !== null) s += (gap || it.x - end > 3) ? '  ' : (it.x - end > 0.8 || it.x - end < -2 ? ' ' : '');
          s += it.str; end = it.x + it.w; gap = false;
        }
        if (s.trim()) lines.push(s);
      }
    }
    return lines;
  }

  async function readText(file) {
    const buf = await file.arrayBuffer();
    let text = new TextDecoder('utf-8').decode(buf);
    if (text.includes('�')) text = new TextDecoder('windows-1252').decode(buf);
    return text;
  }

  /* ---------- entrada principal ---------- */
  async function parseFile(file, { askPassword, accountType } = {}) {
    cardHint = accountType === 'credito' ? true : (accountType ? false : null);
    try { return await parseFileInner(file, askPassword); } finally { cardHint = null; }
  }
  async function parseFileInner(file, askPassword) {
    const name = file.name.toLowerCase();
    const ext = name.split('.').pop();
    let result = { transactions: [], balance: null, kind: ext };
    if (ext === 'ofx' || ext === 'qfx') {
      result = { ...parseOFX(await readText(file)), kind: 'OFX' };
    } else if (ext === 'pdf') {
      const lines = await pdfLines(await file.arrayBuffer(), askPassword || (async () => prompt('Senha do PDF')), file.name);
      if (!lines.length) throw new Error('Este PDF não tem texto (parece ser uma imagem escaneada).');
      result = { transactions: parseLines(lines), balance: null, kind: 'PDF' };
    } else if (ext === 'xls' || ext === 'xlsx' || ext === 'ods') {
      await loadScript(XLSX_URL);
      const wb = window.XLSX.read(await file.arrayBuffer(), { type: 'array', cellDates: true });
      let txs = [];
      for (const sn of wb.SheetNames) {
        const rows = window.XLSX.utils.sheet_to_json(wb.Sheets[sn], { header: 1, raw: true, defval: '' });
        const text = rows.map(r => r.join(' ')).join('\n');
        txs = txs.concat(parseRows(rows, text));
      }
      result = { transactions: txs, balance: null, kind: 'Excel' };
    } else {
      const text = await readText(file);
      if (/<OFX>|<STMTTRN>/i.test(text)) result = { ...parseOFX(text), kind: 'OFX' };
      else {
        const lines = text.split(/\r?\n/);
        const d = detectDelimiter(lines.filter(l => l.trim()));
        let txs = d ? parseRows(lines.map(l => splitCSVLine(l, d)), text) : [];
        if (!txs.length) txs = parseLines(lines);
        result = { transactions: txs, balance: null, kind: d ? 'CSV' : 'Texto' };
      }
    }
    // saldo de abertura: quando o extrato traz a coluna de saldo, o saldo antes do 1º lançamento
    if (result.balance == null && cardHint !== true) {
      const withBal = result.transactions.filter(t => t.balance != null && !isNaN(t.balance));
      if (withBal.length >= 2) {
        const asc = withBal[0].date <= withBal[withBal.length - 1].date;
        const first = asc ? withBal[0] : withBal[withBal.length - 1];
        const second = asc ? withBal[1] : withBal[withBal.length - 2];
        // confere se o saldo bate com os valores antes de confiar nele
        if (Math.abs(second.balance - first.balance - second.amount) < 0.02) result.opening = Math.round((first.balance - first.amount) * 100) / 100;
      }
    }
    result.transactions = result.transactions
      .filter(t => t.date && isFinite(t.amount) && t.amount !== 0)
      .map(t => ({ date: t.date, description: cleanDesc(t.description), amount: Math.round(t.amount * 100) / 100 }));
    return result;
  }

  function cleanDesc(s) {
    return String(s || '').replace(/\s+/g, ' ').replace(/^[-–—:•\s]+|[-–—:•\s]+$/g, '').slice(0, 140) || 'Sem descrição';
  }

  const loadXLSX = () => loadScript(XLSX_URL);

  return { loadXLSX, parseFile, parseAmount, parseDate, parseLines, parseRows, splitCSVLine, detectDelimiter, parseOFX };
})();
