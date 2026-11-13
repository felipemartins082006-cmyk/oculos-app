// Exporta o canal do Discord aberto nesta aba para um PDF (ou .txt) que o app Logbook importa.
// Só lê as mensagens que aparecem na tela (rolando até o começo do canal):
// não usa token, senha nem a API do Discord, e não envia nada para lugar nenhum.
//
// Como usar: abra o canal do logbook no Chrome, aperte F12, vá em "Console",
// cole este código e aperte Enter.
// - Ele tenta rolar sozinho até o começo do canal e baixa "logbook-discord.pdf"
//   (o app Logbook importa esse PDF pelo Compartilhar). Para .txt: logbookSalvarTxt()
// - Enquanto roda, ele lê tudo que passa pela tela: se a rolagem automática parar,
//   role para cima com o mouse até o começo do canal e depois digite logbookSalvar()
(() => {
  const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
  const doisDigitos = (n) => String(n).padStart(2, '0');
  const fmt = (d) => `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()}`;
  const msgs = new Map();

  // O id da mensagem (snowflake) já traz a data e a hora em que ela foi enviada.
  const quando = (id) => new Date(Number((BigInt(id) >> 22n) + 1420070400000n));

  // A lista principal do chat (não a de painéis laterais, buscas ou fixadas).
  const listaDoChat = () =>
    document.querySelector('ol[data-list-id="chat-messages"]') ||
    document.querySelector('[data-list-id="chat-messages"]') ||
    document.querySelector('main li[id^="chat-messages-"]')?.parentElement ||
    document.querySelector('li[id^="chat-messages-"]')?.parentElement;

  const mensagensNaTela = () => {
    const ol = listaDoChat();
    return ol ? [...ol.querySelectorAll('li[id^="chat-messages-"]')] : [];
  };

  const coletar = () => {
    mensagensNaTela().forEach((li) => {
      const id = li.id.split('-').pop();
      if (msgs.has(id) || !/^\d+$/.test(id)) return;
      const conteudo = li.querySelector(`[id="message-content-${id}"]`) || li.querySelector('[id^="message-content-"]');
      if (!conteudo) return;
      const limpo = conteudo.cloneNode(true);
      limpo.querySelectorAll('time, [class*="edited"], [class*="timestamp"]').forEach((e) => e.remove());
      // Mensagem com várias séries: cada quebra de linha vira uma linha no arquivo.
      limpo.querySelectorAll('br').forEach((e) => e.replaceWith('\n'));
      limpo.querySelectorAll('div, p, li').forEach((e) => e.append('\n'));
      const d = quando(id);
      msgs.set(id, {
        ts: d.getTime(),
        dia: `${d.getFullYear()}-${doisDigitos(d.getMonth() + 1)}-${doisDigitos(d.getDate())}`,
        texto: limpo.textContent.replace(/\n{2,}/g, '\n').trim(),
      });
    });
  };

  // O elemento que rola: o primeiro "pai" da lista com rolagem vertical.
  const acharRolagem = () => {
    let el = listaDoChat();
    while (el && el !== document.body) {
      const oy = getComputedStyle(el).overflowY;
      if (/(auto|scroll|overlay)/.test(oy) && el.scrollHeight > el.clientHeight) return el;
      el = el.parentElement;
    }
    el = listaDoChat();
    while (el && el !== document.body) {
      if (/scroller/i.test(el.className || '')) return el;
      el = el.parentElement;
    }
    return null;
  };

  const maisAntiga = () => {
    let m = null;
    msgs.forEach((v) => { if (!m || v.ts < m.ts) m = v; });
    return m ? fmt(new Date(m.ts)) : '-';
  };

  /**
   * Monta um PDF simples (só texto, fonte Helvetica, folha A4) sem biblioteca nenhuma:
   * o Discord não deixa carregar scripts de fora. O app lê o texto de dentro dele.
   */
  const fazerPdf = (linhasTexto) => {
    const W = 595, H = 842, M = 40, TAM = 9, ENTRE = 11.5, MAX = 110;
    const porPagina = Math.floor((H - 2 * M) / ENTRE);
    const linhas = [];
    linhasTexto.forEach((l) => {
      while (l.length > MAX) { linhas.push(l.slice(0, MAX)); l = l.slice(MAX); }
      linhas.push(l);
    });
    const paginas = [];
    for (let i = 0; i < linhas.length; i += porPagina) paginas.push(linhas.slice(i, i + porPagina));
    if (!paginas.length) paginas.push([]);
    // Acentos vão como Latin-1; travessão, aspas curvas etc. na tabela WinAnsi.
    const win = { '\u2014': 0x97, '\u2013': 0x96, '\u2019': 0x92, '\u2018': 0x91, '\u201C': 0x93,
      '\u201D': 0x94, '\u2022': 0x95, '\u2026': 0x85, '\u20AC': 0x80 };
    const esc = (t) => {
      let o = '';
      for (const ch of t) {
        let c = win[ch] || ch.codePointAt(0);
        if (!win[ch] && (c > 255 || (c >= 0x80 && c < 0xa0))) c = 63; // "?" para emoji e afins
        if (c === 40 || c === 41 || c === 92) o += '\\' + String.fromCharCode(c);
        else o += c < 32 ? ' ' : String.fromCharCode(c);
      }
      return o;
    };
    const objs = [];
    objs[0] = '<< /Type /Catalog /Pages 2 0 R >>';
    objs[2] = '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>';
    const kids = [];
    paginas.forEach((pg, i) => {
      const nPag = 4 + i * 2, nCont = 5 + i * 2;
      kids.push(`${nPag} 0 R`);
      const fluxo = `BT /F1 ${TAM} Tf ${ENTRE} TL ${M} ${H - M} Td\n` +
          pg.map((l) => `(${esc(l)}) Tj T*`).join('\n') + '\nET';
      objs[nPag - 1] = `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${W} ${H}] ` +
          `/Resources << /Font << /F1 3 0 R >> >> /Contents ${nCont} 0 R >>`;
      objs[nCont - 1] = `<< /Length ${fluxo.length} >>\nstream\n${fluxo}\nendstream`;
    });
    objs[1] = `<< /Type /Pages /Kids [${kids.join(' ')}] /Count ${kids.length} >>`;
    let pdf = '%PDF-1.4\n%\xE2\xE3\xCF\xD3\n';
    const posicoes = [];
    objs.forEach((o, i) => { posicoes.push(pdf.length); pdf += `${i + 1} 0 obj\n${o}\nendobj\n`; });
    const xref = pdf.length;
    pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n` +
        posicoes.map((p) => String(p).padStart(10, '0') + ' 00000 n \n').join('') +
        `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
    const bytes = new Uint8Array(pdf.length);
    for (let i = 0; i < pdf.length; i++) bytes[i] = pdf.charCodeAt(i) & 255;
    return bytes;
  };

  const baixar = (conteudo, tipo, nome) => {
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([conteudo], { type: tipo }));
    link.download = nome;
    document.body.appendChild(link);
    link.click();
    link.remove();
  };

  const salvar = (formato = 'pdf') => {
    coletar();
    const ordenadas = [...msgs.values()].filter((m) => m.texto).sort((a, b) => a.ts - b.ts);
    if (ordenadas.length === 0) {
      const ol = listaDoChat();
      console.log('Logbook: nada lido. Diagnóstico para mandar ao Claude:', JSON.stringify({
        lis: document.querySelectorAll('li[id^="chat-messages-"]').length,
        lista: ol && (ol.tagName + ' ' + (ol.getAttribute('data-list-id') || '') + ' ' + String(ol.className).slice(0, 60)),
        conteudos: document.querySelectorAll('[id^="message-content-"]').length,
      }));
      return;
    }
    const texto = ordenadas.map((m) => `${m.dia}\n${m.texto}`).join('\n\n') + '\n';
    const nome = `logbook-discord.${formato === 'txt' ? 'txt' : 'pdf'}`;
    if (formato === 'txt') baixar(texto, 'text/plain;charset=utf-8', nome);
    else baixar(fazerPdf(texto.split('\n')), 'application/pdf', nome);
    console.log(`Logbook: ${nome} baixado com ${ordenadas.length} mensagens ` +
        `(${fmt(new Date(ordenadas[0].ts))} a ${fmt(new Date(ordenadas[ordenadas.length - 1].ts))}).`);
  };

  if (mensagensNaTela().length === 0) {
    alert('Não achei mensagens. Abra o canal do logbook (clique nele) e rode de novo.');
    return;
  }

  // Lê o tempo todo, inclusive enquanto você rola com o mouse.
  clearInterval(window.__logbookIntervalo);
  window.__logbookIntervalo = setInterval(coletar, 200);
  window.logbookSalvar = () => salvar('pdf');
  window.logbookSalvarTxt = () => salvar('txt');
  coletar();

  // Tenta subir de vários jeitos, porque o Discord só carrega as mais antigas
  // quando percebe a rolagem chegando no topo.
  const subir = (rolagem) => {
    const primeira = mensagensNaTela()[0];
    if (rolagem) {
      rolagem.scrollTop = Math.max(0, rolagem.scrollTop - rolagem.clientHeight * 2);
      rolagem.dispatchEvent(new Event('scroll'));
      rolagem.dispatchEvent(new WheelEvent('wheel', { deltaY: -2000, deltaMode: 0, bubbles: true, cancelable: true }));
      if (rolagem.scrollTop <= 5) {
        rolagem.scrollTop = 0;
        rolagem.dispatchEvent(new Event('scroll'));
      }
    }
    primeira?.scrollIntoView({ block: 'start' });
    if (primeira) {
      primeira.dispatchEvent(new WheelEvent('wheel', { deltaY: -2000, deltaMode: 0, bubbles: true, cancelable: true }));
    }
  };

  (async () => {
    console.log('Logbook: rolando até o começo do canal. Não mexa nesta aba até terminar.');
    let ultimoTotal = msgs.size;
    let paradoDesde = Date.now();
    let ultimoAviso = 0;
    while (Date.now() - paradoDesde < 25000) {
      subir(acharRolagem());
      await esperar(700);
      if (msgs.size !== ultimoTotal) {
        ultimoTotal = msgs.size;
        paradoDesde = Date.now();
      }
      if (Date.now() - ultimoAviso > 3000) {
        console.log(`Logbook: ${msgs.size} mensagens lidas · mais antiga até agora: ${maisAntiga()}`);
        ultimoAviso = Date.now();
      }
    }
    salvar('pdf');
    console.log(
        '%cLogbook: a rolagem automática parou na mensagem de ' + maisAntiga() + '.\n' +
        'Se essa ainda NÃO é a primeira mensagem do canal: role para cima com o mouse até o começo ' +
        '(o script continua lendo tudo que passa na tela) e depois digite aqui:  logbookSalvar()\n' +
        'O app não duplica nada, então pode importar o arquivo mais completo por cima.',
        'font-weight:bold;color:#ff8a3d');
  })();
})();
