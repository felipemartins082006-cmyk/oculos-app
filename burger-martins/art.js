// Ilustrações dos produtos, desenhadas em SVG (originais do app, sem fotos).
// Cada função devolve o texto de um <svg>.
const ART = (() => {
  const C = {
    bunTop: '#EFA458', bunHi: '#F8C98E', bunBot: '#DE8D3C', seed: '#FFF4DE',
    patty: '#5B321F', pattyHi: '#7B4A32', chicken: '#E0A04A', chickenHi: '#F0BE6C', veg: '#6F8F3A', vegHi: '#8DAE55',
    cheese: '#FFC233', lettuce: '#5DBB46', lettuceDk: '#3F9A32', tomato: '#E5412E', onion: '#F4E6F4',
    pickle: '#7FA83A', bacon: '#B6402C', baconHi: '#E58B6E', sauce: '#F7E8C9',
    brand: '#F26A1B', brandDk: '#C9500C', cream: '#FFF8EC', ink: '#2A211B'
  };
  const svg = (inner, vb = '0 0 200 160') =>
    `<svg viewBox="${vb}" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">${inner}</svg>`;
  const shadow = (cy = 148, rx = 74) => `<ellipse cx="100" cy="${cy}" rx="${rx}" ry="7" fill="#000" opacity=".10"/>`;

  function wave(x1, x2, yBase, amp, step) {
    let d = `M${x1} ${yBase}`;
    let up = true;
    for (let x = x1; x < x2; x += step) {
      const nx = Math.min(x + step, x2);
      d += ` Q${(x + nx) / 2} ${yBase + (up ? amp : -amp)} ${nx} ${yBase}`;
      up = !up;
    }
    return d;
  }

  function burger(layers) {
    const p = [shadow(146, 78)];
    let y = 140;
    p.push(`<path d="M30 ${y - 20}h140c6 0 9 4 8 9l-2 5c-2 5-6 7-11 7H35c-5 0-9-2-11-7l-2-5c-1-5 2-9 8-9z" fill="${C.bunBot}"/>`);
    y -= 20;
    for (const l of layers) {
      if (l === 'patty' || l === 'chicken' || l === 'veg') {
        const col = l === 'patty' ? C.patty : l === 'chicken' ? C.chicken : C.veg;
        const hi = l === 'patty' ? C.pattyHi : l === 'chicken' ? C.chickenHi : C.vegHi;
        p.push(`<rect x="24" y="${y - 18}" width="152" height="20" rx="10" fill="${col}"/>`);
        p.push(`<path d="M38 ${y - 12}h40M90 ${y - 13}h34M136 ${y - 12}h26" stroke="${hi}" stroke-width="3" stroke-linecap="round"/>`);
        y -= 16;
      } else if (l === 'cheese') {
        const t = y - 3;
        p.push(`<path d="M22 ${t}H178L170 ${t + 6}L163 ${t + 15}L155 ${t + 6}L141 ${t + 17}L129 ${t + 6}L113 ${t + 13}L101 ${t + 6}L87 ${t + 16}L75 ${t + 6}L61 ${t + 13}L49 ${t + 6}L37 ${t + 11}L29 ${t + 5}Z" fill="${C.cheese}" stroke="#F2A900" stroke-width="1" stroke-linejoin="round"/>`);
        y -= 5;
      } else if (l === 'lettuce') {
        p.push(`<path d="${wave(18, 182, y + 2, 5, 12)} L182 ${y - 6} L18 ${y - 6}Z" fill="${C.lettuce}"/>`);
        p.push(`<path d="${wave(26, 174, y - 2, 3, 14)}" stroke="${C.lettuceDk}" stroke-width="2" fill="none"/>`);
        y -= 8;
      } else if (l === 'tomato') {
        p.push(`<rect x="34" y="${y - 9}" width="62" height="11" rx="5.5" fill="${C.tomato}"/><rect x="104" y="${y - 9}" width="62" height="11" rx="5.5" fill="${C.tomato}"/>`);
        y -= 8;
      } else if (l === 'onion') {
        for (const x of [52, 84, 116, 148]) p.push(`<ellipse cx="${x}" cy="${y - 2}" rx="14" ry="3.2" fill="none" stroke="${C.onion}" stroke-width="3"/>`);
        y -= 4;
      } else if (l === 'pickle') {
        for (const x of [60, 100, 140]) p.push(`<ellipse cx="${x}" cy="${y - 2}" rx="13" ry="3.5" fill="${C.pickle}"/>`);
        y -= 3;
      } else if (l === 'bacon') {
        p.push(`<path d="${wave(22, 178, y - 3, 4, 13)}" stroke="${C.bacon}" stroke-width="7" fill="none" stroke-linecap="round"/>`);
        p.push(`<path d="${wave(22, 178, y - 3, 4, 13)}" stroke="${C.baconHi}" stroke-width="1.6" fill="none"/>`);
        y -= 8;
      } else if (l === 'sauce') {
        p.push(`<path d="${wave(26, 174, y - 1, 2.5, 10)} L174 ${y - 4} L26 ${y - 4}Z" fill="${C.sauce}"/>`);
        y -= 4;
      }
    }
    p.push(`<path d="M26 ${y + 2}c0-46 34-60 74-60s74 14 74 60c0 4-3 6-7 6H33c-4 0-7-2-7-6z" fill="${C.bunTop}"/>`);
    p.push(`<path d="M48 ${y - 26}c8-14 26-24 50-24" stroke="${C.bunHi}" stroke-width="6" stroke-linecap="round" fill="none" opacity=".8"/>`);
    const seeds = [[70, -30, -30], [92, -42, 10], [116, -36, 30], [138, -24, 50], [80, -14, -10], [104, -20, 20], [128, -10, 40], [58, -10, -40], [150, -6, 60]];
    for (const [sx, sy, r] of seeds) p.push(`<ellipse cx="${sx}" cy="${y + sy}" rx="4" ry="2" transform="rotate(${r} ${sx} ${y + sy})" fill="${C.seed}"/>`);
    const top = y - 50;
    const minY = Math.min(0, top - 4);
    return svg(p.join(''), `0 ${minY} 200 ${160 - minY}`);
  }

  function fries(scale = 1) {
    const s = scale;
    const sticks = [];
    const xs = [62, 72, 82, 92, 102, 112, 122, 132, 140];
    xs.forEach((x, i) => {
      const h = 52 + ((i * 37) % 22);
      const r = (i - 4) * 3;
      sticks.push(`<rect x="${x}" y="${78 - h}" width="10" height="${h + 20}" rx="3" fill="${i % 2 ? '#FFD45E' : '#F5BC3A'}" transform="rotate(${r} ${x + 5} 90)"/>`);
    });
    return svg(`<g transform="translate(${100 - 100 * s} ${150 - 150 * s}) scale(${s})">${shadow(148, 52)}${sticks.join('')}
      <path d="M52 72H148L138 146H62Z" fill="${C.brand}"/>
      <path d="M52 72H148L146 86H54Z" fill="${C.brandDk}"/>
      ${flameMark(100, 118, 0.9, C.cream)}</g>`);
  }

  function flameMark(cx, cy, k = 1, fill = '#fff') {
    return `<path transform="translate(${cx} ${cy}) scale(${k})" d="M0 -22C6 -12 16 -6 16 6C16 16 8 22 0 22C-8 22 -16 16 -16 6C-16 -2 -10 -6 -8 -12C-6 -6 -2 -4 0 -4C2 -10 -2 -16 0 -22Z" fill="${fill}"/>`;
  }

  const FLAVOR = { cola: '#3A2219', guarana: '#2E8B3E', laranja: '#F28C28', limao: '#9BC53D', zero: '#1D1D1D' };

  function drink(flavor = 'cola', scale = 1) {
    const s = scale, band = FLAVOR[flavor] || C.brand;
    return svg(`<g transform="translate(${100 - 100 * s} ${150 - 150 * s}) scale(${s})">${shadow(148, 42)}
      <rect x="104" y="6" width="8" height="52" rx="3" fill="${C.brand}" transform="rotate(12 108 50)"/>
      <path d="M62 52H138L129 146H71Z" fill="#fff" stroke="#E6E0DA" stroke-width="2"/>
      <path d="M65 82H135L131 120H69Z" fill="${C.brand}"/>
      <circle cx="100" cy="101" r="12" fill="${band}"/>
      <path d="M58 46H142V54H58Z" fill="#F1ECE6" />
      <path d="M64 46Q100 30 136 46Z" fill="#E9E3DC"/></g>`);
  }

  function cone(mix = false) {
    const swirl = mix ? '#8A5636' : C.cream;
    return svg(`${shadow(150, 26)}
      <defs><clipPath id="cc"><path d="M72 78H128L100 150Z"/></clipPath></defs>
      <path d="M72 78H128L100 150Z" fill="#E2A85A"/>
      <g clip-path="url(#cc)" stroke="#C78A3E" stroke-width="2.5">
        <path d="M60 80L120 150M76 76L136 146M44 86L104 156M140 80L80 150M124 76L64 146M156 86L96 156"/></g>
      <ellipse cx="100" cy="78" rx="36" ry="10" fill="#FFF8EC" stroke="#EADFCB" stroke-width="2"/>
      <path d="M68 76C64 56 80 50 100 50C120 50 136 56 132 76Z" fill="#FFF8EC" stroke="#EADFCB" stroke-width="2"/>
      ${mix ? '<path d="M100 50C120 50 136 56 132 76H100Z" fill="#8A5636"/>' : ''}
      <path d="M78 52C76 36 88 30 100 30C112 30 124 36 122 52Z" fill="#FFF8EC" stroke="#EADFCB" stroke-width="2"/>
      ${mix ? '<path d="M100 30C112 30 124 36 122 52H100Z" fill="#8A5636"/>' : ''}
      <path d="M90 32C90 18 98 12 108 10C104 16 106 24 110 30Z" fill="${swirl}" stroke="#EADFCB" stroke-width="2"/>`);
  }

  function sundae(sauce = '#5A2E1B') {
    return svg(`${shadow(146, 44)}
      <path d="M56 74H144L130 140H70Z" fill="#F4EFEA" stroke="#E2DBD3" stroke-width="2"/>
      <path d="M60 76C58 40 80 30 100 30C120 30 142 40 140 76Z" fill="#FFF8EC" stroke="#EADFCB" stroke-width="2"/>
      <path d="M66 50C76 36 124 36 134 50C132 58 126 52 122 62C118 54 112 60 108 70C104 58 96 60 92 66C88 56 80 60 76 64C72 54 68 58 66 50Z" fill="${sauce}"/>
      <rect x="118" y="8" width="8" height="46" rx="4" fill="#D9D3CC" transform="rotate(18 122 40)"/>
      <rect x="68" y="96" width="64" height="16" rx="3" fill="${C.brand}" opacity=".9"/>`);
  }

  function pie() {
    return svg(`${shadow(146, 50)}
      <g transform="rotate(-8 100 90)">
        <rect x="58" y="22" width="84" height="116" rx="14" fill="#E9A552"/>
        <path d="M72 40L84 52M92 40L104 52M112 40L124 52M72 66L84 78M92 66L104 78M112 66L124 78" stroke="#B8732E" stroke-width="4" stroke-linecap="round"/>
        <path d="M52 92H148V138C148 146 142 150 134 150H66C58 150 52 146 52 138Z" fill="${C.brand}"/>
        ${flameMark(100, 120, 0.7, C.cream)}
      </g>`);
  }

  function nuggets(n = 6) {
    const blobs = [[66, 74], [96, 66], [126, 74], [80, 92], [112, 90], [140, 92]].slice(0, Math.min(6, n === 10 ? 6 : 5));
    const nug = ([x, y], i) => `<path d="M${x - 16} ${y}c-2-10 8-16 18-15c10 1 16 6 14 15c-1 8-8 12-17 12c-8 0-14-4-15-12z" fill="${i % 2 ? '#E7AC54' : '#D9963F'}"/>
      <circle cx="${x - 4}" cy="${y - 4}" r="2" fill="#B8762E"/><circle cx="${x + 6}" cy="${y + 2}" r="1.6" fill="#B8762E"/>`;
    return svg(`${shadow(148, 60)}
      ${blobs.map(nug).join('')}
      <path d="M40 96H160L150 146H50Z" fill="${C.brand}"/>
      <path d="M40 96H160L158 106H42Z" fill="${C.brandDk}"/>
      ${flameMark(100, 126, 0.7, C.cream)}`);
  }

  function rings() {
    const r = [[70, 70, -10], [100, 60, 6], [130, 72, 14], [86, 88, -4], [118, 90, 8]];
    return svg(`${shadow(148, 56)}
      ${r.map(([x, y, a]) => `<ellipse cx="${x}" cy="${y}" rx="22" ry="17" transform="rotate(${a} ${x} ${y})" fill="none" stroke="#E2A04C" stroke-width="10"/>
        <ellipse cx="${x}" cy="${y}" rx="22" ry="17" transform="rotate(${a} ${x} ${y})" fill="none" stroke="#F3C477" stroke-width="3" stroke-dasharray="4 6"/>`).join('')}
      <path d="M44 100H156L146 146H54Z" fill="${C.brand}"/>
      ${flameMark(100, 124, 0.6, C.cream)}`);
  }

  function shake(color = '#F28DA8') {
    return svg(`${shadow(148, 36)}
      <rect x="106" y="2" width="8" height="56" rx="3" fill="${C.brand}" transform="rotate(10 110 50)"/>
      <path d="M68 52C68 24 132 24 132 52Z" fill="${color}" opacity=".9"/>
      <circle cx="100" cy="28" r="6" fill="#D7263D"/>
      <path d="M64 52H136L128 146H72Z" fill="#fff" stroke="#E6E0DA" stroke-width="2"/>
      <path d="M66 70H134L131 106H69Z" fill="${color}"/>
      <path d="M60 48H140V56H60Z" fill="#F1ECE6"/>`);
  }

  function coffee() {
    return svg(`${shadow(148, 34)}
      <path d="M86 26c-6-8 6-12 0-20M100 26c-6-8 6-12 0-20M114 26c-6-8 6-12 0-20" stroke="#CFC7BE" stroke-width="3" fill="none" stroke-linecap="round"/>
      <path d="M68 46H132L124 146H76Z" fill="#fff" stroke="#E6E0DA" stroke-width="2"/>
      <path d="M70 76H130L127 112H73Z" fill="#7A4A33"/>
      ${flameMark(100, 94, 0.55, C.cream)}
      <path d="M64 38H136V48H64Z" rx="3" fill="#5B3A29"/>`);
  }

  function salad() {
    const leaves = [[70, 80], [90, 70], [112, 72], [132, 82], [100, 84], [80, 90], [120, 90]];
    return svg(`${shadow(146, 66)}
      ${leaves.map(([x, y], i) => `<circle cx="${x}" cy="${y}" r="16" fill="${i % 2 ? C.lettuce : C.lettuceDk}"/>`).join('')}
      <circle cx="96" cy="74" r="7" fill="${C.tomato}"/><circle cx="122" cy="78" r="6" fill="${C.tomato}"/><circle cx="78" cy="80" r="6" fill="${C.tomato}"/>
      <path d="M36 92H164C164 124 136 144 100 144C64 144 36 124 36 92Z" fill="#fff" stroke="#E6E0DA" stroke-width="2"/>
      <path d="M48 112H152" stroke="${C.brand}" stroke-width="6" stroke-linecap="round"/>`);
  }

  function juice() {
    return svg(`${shadow(148, 34)}
      <circle cx="130" cy="42" r="18" fill="#F7A531"/><circle cx="130" cy="42" r="12" fill="#FFD37A"/>
      <path d="M130 30V54M118 42H142M122 34L138 50M138 34L122 50" stroke="#F7A531" stroke-width="1.5"/>
      <path d="M68 40H132L124 146H76Z" fill="#FDF7EF" stroke="#E6E0DA" stroke-width="2"/>
      <path d="M71 62H129L124 142H76Z" fill="#F7A531"/>`);
  }

  function water() {
    return svg(`${shadow(148, 26)}
      <rect x="90" y="12" width="20" height="14" rx="3" fill="#2E86DE"/>
      <path d="M86 28H114C114 40 126 44 126 58V138C126 144 122 148 116 148H84C78 148 74 144 74 138V58C74 44 86 40 86 28Z" fill="#D8ECFB" stroke="#B9D9F2" stroke-width="2"/>
      <rect x="74" y="78" width="52" height="30" fill="${C.brand}"/>`);
  }

  function gift() {
    return svg(`${shadow(148, 56)}
      <rect x="46" y="64" width="108" height="80" rx="8" fill="${C.brand}"/>
      <rect x="40" y="50" width="120" height="22" rx="6" fill="${C.brandDk}"/>
      <rect x="92" y="50" width="16" height="94" fill="${C.cream}"/>
      <path d="M100 50C88 26 64 30 72 44C78 52 100 50 100 50ZM100 50C112 26 136 30 128 44C122 52 100 50 100 50Z" fill="${C.cream}"/>`);
  }

  function ticket() {
    return svg(`${shadow(148, 60)}
      <path d="M36 52H164V80C156 80 152 86 152 92C152 98 156 104 164 104V132H36V104C44 104 48 98 48 92C48 86 44 80 36 80Z" fill="${C.brand}"/>
      <path d="M112 56V128" stroke="${C.cream}" stroke-width="3" stroke-dasharray="6 6"/>
      ${flameMark(76, 92, 0.9, C.cream)}
      <path d="M128 80h20M128 92h20M128 104h14" stroke="${C.cream}" stroke-width="4" stroke-linecap="round"/>`);
  }

  function double() {
    return svg(`${shadow(148, 50)}
      <circle cx="84" cy="86" r="42" fill="#FFC233" stroke="#E79F00" stroke-width="5"/>
      <circle cx="118" cy="96" r="42" fill="#FFD25E" stroke="#E79F00" stroke-width="5"/>
      ${flameMark(118, 96, 1, C.brand)}
      <text x="56" y="62" font-family="Jakarta, sans-serif" font-weight="800" font-size="30" fill="${C.brandDk}">2×</text>`);
  }

  return { burger, fries, drink, cone, sundae, pie, nuggets, rings, shake, coffee, salad, juice, water, gift, ticket, double, flameMark, svg };
})();

// Desenha a ilustração a partir da descrição guardada no produto.
function artOf(a) {
  if (!a) return '';
  const [kind, ...args] = a;
  return ART[kind](...args);
}
