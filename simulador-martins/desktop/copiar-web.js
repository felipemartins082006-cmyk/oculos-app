// Copia a versão web (pasta simulador-martins/) para dentro do programa.
const fs = require('fs');
const path = require('path');
const origem = path.resolve(__dirname, '..');
const destino = path.join(__dirname, 'web');
fs.rmSync(destino, { recursive: true, force: true });
fs.mkdirSync(path.join(destino, 'vendor'), { recursive: true });
for (const f of ['index.html', 'styles.css', 'app.js', 'data.js', 'icon.svg', 'icon-512.png']) fs.copyFileSync(path.join(origem, f), path.join(destino, f));
for (const f of fs.readdirSync(path.join(origem, 'vendor'))) fs.copyFileSync(path.join(origem, 'vendor', f), path.join(destino, 'vendor', f));
console.log('web copiada para', destino);
