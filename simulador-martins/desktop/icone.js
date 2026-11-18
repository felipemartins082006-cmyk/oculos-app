// Põe o ícone e os dados de versão no .exe sem precisar do Windows (biblioteca resedit, só JavaScript).
// Roda entre o empacotamento da pasta e a geração do instalador/zip.
const fs = require('fs');
const path = require('path');
const ResEdit = require('resedit');
const pkg = require('./package.json');
const exe = path.join(__dirname, 'dist', 'win-unpacked', 'Simulador Martins.exe');
const pe = ResEdit.NtExecutable.from(fs.readFileSync(exe), { ignoreCert: true });
const res = ResEdit.NtExecutableResource.from(pe);
const ico = ResEdit.Data.IconFile.from(fs.readFileSync(path.join(__dirname, 'build', 'icon.ico')));
ResEdit.Resource.IconGroupEntry.replaceIconsForResource(res.entries, 1, 1033, ico.icons.map((i) => i.data));
const vi = ResEdit.Resource.VersionInfo.fromEntries(res.entries)[0];
const [a, b, c] = pkg.version.split('.').map(Number);
vi.setFileVersion(a, b, c, 0, 1033); vi.setProductVersion(a, b, c, 0, 1033);
vi.setStringValues({ lang: 1033, codepage: 1200 }, { ProductName: 'Simulador Martins', FileDescription: 'Simulador Martins', CompanyName: 'Martins', OriginalFilename: 'Simulador Martins.exe' });
vi.outputToResourceEntries(res.entries);
res.outputResource(pe);
fs.writeFileSync(exe, Buffer.from(pe.generate()));
console.log('ícone e versão gravados em', exe);
