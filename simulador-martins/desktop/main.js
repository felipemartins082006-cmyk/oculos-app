// Simulador Martins para Windows: a mesma página do app, numa janela própria, sem internet.
// O cálculo do Monte Carlo usa todos os núcleos do processador (um Web Worker por núcleo).
const { app, BrowserWindow, shell, Menu } = require('electron');
const path = require('path');

// sem limite artificial de memória para simulações grandes
app.commandLine.appendSwitch('js-flags', '--max-old-space-size=8192');

function criarJanela() {
  const janela = new BrowserWindow({
    width: 1440, height: 920, minWidth: 380, minHeight: 600,
    title: 'Simulador Martins', backgroundColor: '#eef0f3', show: false,
    icon: path.join(__dirname, 'web', 'icon-512.png'),
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, backgroundThrottling: false },
  });
  Menu.setApplicationMenu(null);
  janela.once('ready-to-show', () => { janela.maximize(); janela.show(); });
  // links externos (fontes de dados, documentação) abrem no navegador
  janela.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  janela.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file://')) { e.preventDefault(); shell.openExternal(url); } });
  janela.loadFile(path.join(__dirname, 'web', 'index.html'));
}

app.whenReady().then(criarJanela);
app.on('window-all-closed', () => app.quit());
