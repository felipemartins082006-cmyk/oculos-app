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

  const coletar = () => {
    document.querySelectorAll('li[id^="chat-messages-"]').forEach((li) => {
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

  const primeira = document.querySelector('li[id^="chat-messages-"]');
  if (!primeira) {
    alert('Abra o canal do logbook antes de rodar o script.');
    return;
  }
  // A lista de mensagens fica dentro de um elemento que rola: sobe até achar ele.
  let lista = primeira.parentElement;
  while (lista && lista.scrollHeight <= lista.clientHeight + 5) lista = lista.parentElement;
  if (!lista) {
    alert('Não achei a lista de mensagens.');
    return;
  }

  console.log('Logbook: rolando até o começo do canal. Não mexa nesta aba até terminar.');
  let semNovidade = 0;
  while (semNovidade < 8) {
    const antes = msgs.size;
    coletar();
    lista.scrollTop = 0;
    await esperar(1500);
    coletar();
    semNovidade = msgs.size === antes ? semNovidade + 1 : 0;
    console.log(`Logbook: ${msgs.size} mensagens lidas...`);
  }

  const ordenadas = [...msgs.values()].filter((m) => m.texto).sort((a, b) => a.ts - b.ts);
  const texto = ordenadas.map((m) => `${m.dia}\n${m.texto}`).join('\n\n') + '\n';
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([texto], { type: 'text/plain;charset=utf-8' }));
  link.download = 'logbook-discord.txt';
  document.body.appendChild(link);
  link.click();
  link.remove();
  const p = ordenadas[0], u = ordenadas[ordenadas.length - 1];
  console.log(`Logbook: pronto! ${ordenadas.length} mensagens (${p ? p.dia : '-'} a ${u ? u.dia : '-'}) em logbook-discord.txt`);
})();
