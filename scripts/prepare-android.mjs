/**
 * Copia recursos pré-gerados para android/app/src/main/res
 * e injeta permissões necessárias para notificações com tela bloqueada.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'assets', 'android');
const resDir = path.join(root, 'android', 'app', 'src', 'main', 'res');
const manifestPath = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');

if (!fs.existsSync(resDir)) {
  console.error('android/ não encontrado. Rode: npx cap add android');
  process.exit(1);
}

for (const folder of fs.readdirSync(assets)) {
  const srcDir = path.join(assets, folder);
  if (!fs.statSync(srcDir).isDirectory()) continue;
  const dstDir = path.join(resDir, folder);
  fs.mkdirSync(dstDir, { recursive: true });
  for (const file of fs.readdirSync(srcDir)) {
    fs.copyFileSync(path.join(srcDir, file), path.join(dstDir, file));
  }
  console.log('copiado', folder);
}

const PERMS = [
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.USE_EXACT_ALARM',
  'android.permission.VIBRATE',
  'android.permission.WAKE_LOCK',
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.INTERNET',
];

if (!fs.existsSync(manifestPath)) {
  console.warn('AndroidManifest.xml não encontrado:', manifestPath);
  process.exit(0);
}

let xml = fs.readFileSync(manifestPath, 'utf8');

// Remove uses-permission existentes que vamos reescrever (mantém outras)
for (const perm of PERMS) {
  xml = xml.replace(new RegExp(`\\s*<uses-permission android:name="${perm}"\\s*/>\\s*`, 'g'), '\n');
}

const block = PERMS.map((p) => `    <uses-permission android:name="${p}"/>`).join('\n');

if (xml.includes('<application')) {
  xml = xml.replace(/(\s*)(<application)/, `\n${block}\n\n    <application`);
} else {
  xml = xml.replace('</manifest>', `${block}\n</manifest>`);
}

// Normaliza linhas em branco e remove comentário residual
xml = xml.replace(/\n{3,}/g, '\n\n');
xml = xml.replace(/\s*<!-- Permissions -->\s*/g, '\n');

// versionName do package.json + versionCode fixo (incrementar ao publicar)
const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
if (xml.includes('android:versionCode')) {
  xml = xml.replace(/android:versionCode="\d+"/, 'android:versionCode="2"');
  xml = xml.replace(/android:versionName="[^"]*"/, `android:versionName="${pkg.version}"`);
} else {
  xml = xml.replace('<manifest', `<manifest android:versionCode="2" android:versionName="${pkg.version}"`);
}

fs.writeFileSync(manifestPath, xml);
console.log('AndroidManifest.xml: permissões e versão atualizadas');
console.log('prepare-android concluído');
