// Exporta o canal do Discord aberto nesta aba para um .txt que o app Logbook importa.
// Só lê as mensagens que aparecem na tela (rolando até o começo do canal):
// não usa token, senha nem a API do Discord, e não envia nada para lugar nenhum.
//
// Como usar: abra o canal do logbook no Chrome, aperte F12, vá em "Console",
// cole este código e aperte Enter. No fim, baixa "logbook-discord.txt".
(async () => {
  const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
  const doisDigitos = (n) => String(n).padStart(2, '0');
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

  if (mensagensNaTela().length === 0) {
    alert('Não achei mensagens. Abra o canal do logbook (clique nele) e rode de novo.');
    return;
  }

  const rolagem = acharRolagem();
  console.log('Logbook: rolando até o começo do canal. Não mexa nesta aba até terminar.',
      rolagem ? '' : '(usando rolagem pela primeira mensagem)');

  let semNovidade = 0;
  while (semNovidade < 8) {
    const antes = msgs.size;
    coletar();
    // Sobe até o topo: pelo elemento que rola e, de reserva, levando a mensagem mais antiga à tela.
    if (rolagem) rolagem.scrollTop = 0;
    mensagensNaTela()[0]?.scrollIntoView({ block: 'start' });
    await esperar(1500);
    coletar();
    semNovidade = msgs.size === antes ? semNovidade + 1 : 0;
    console.log(`Logbook: ${msgs.size} mensagens lidas...`);
  }

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
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([texto], { type: 'text/plain;charset=utf-8' }));
  link.download = 'logbook-discord.txt';
  document.body.appendChild(link);
  link.click();
  link.remove();
  const p = ordenadas[0], u = ordenadas[ordenadas.length - 1];
  console.log(`Logbook: pronto! ${ordenadas.length} mensagens (${p.dia} a ${u.dia}) em logbook-discord.txt`);
})();
