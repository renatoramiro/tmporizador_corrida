/**
 * Prepara o projeto Android gerado pelo Capacitor:
 * - copia ícones (assets/android → res)
 * - copia código Java nativo (android-src → app/src/main/java)
 * - injeta permissões e declara o foreground service no AndroidManifest
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const assets = path.join(root, 'assets', 'android');
const javaSrc = path.join(root, 'android-src', 'com', 'temporizadorciclico', 'app');
const resDir = path.join(root, 'android', 'app', 'src', 'main', 'res');
const javaDst = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'temporizadorciclico', 'app');
const manifestPath = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');

if (!fs.existsSync(resDir)) {
  console.error('android/ não encontrado. Rode: npx cap add android');
  process.exit(1);
}

// --- Ícones ---
for (const folder of fs.readdirSync(assets)) {
  const srcDir = path.join(assets, folder);
  if (!fs.statSync(srcDir).isDirectory()) continue;
  const dstDir = path.join(resDir, folder);
  fs.mkdirSync(dstDir, { recursive: true });
  for (const file of fs.readdirSync(srcDir)) {
    fs.copyFileSync(path.join(srcDir, file), path.join(dstDir, file));
  }
  console.log('copiado res/' + folder);
}

// --- Java nativo ---
fs.mkdirSync(javaDst, { recursive: true });
for (const file of fs.readdirSync(javaSrc)) {
  if (!file.endsWith('.java')) continue;
  fs.copyFileSync(path.join(javaSrc, file), path.join(javaDst, file));
  console.log('copiado java/' + file);
}

// --- Manifest ---
const PERMS = [
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SCHEDULE_EXACT_ALARM',
  'android.permission.USE_EXACT_ALARM',
  'android.permission.VIBRATE',
  'android.permission.WAKE_LOCK',
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.INTERNET',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
];

const SERVICE_XML = `
        <service
            android:name=".TimerService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <receiver
            android:name=".AlertReceiver"
            android:exported="false" />
`;

if (!fs.existsSync(manifestPath)) {
  console.warn('AndroidManifest.xml não encontrado:', manifestPath);
  process.exit(0);
}

let xml = fs.readFileSync(manifestPath, 'utf8');

for (const perm of PERMS) {
  xml = xml.replace(new RegExp(`\\s*<uses-permission android:name="${perm}"\\s*/>\\s*`, 'g'), '\n');
}
const block = PERMS.map((p) => `    <uses-permission android:name="${p}"/>`).join('\n');

if (xml.includes('<application')) {
  xml = xml.replace(/(\s*)(<application)/, `\n${block}\n\n    <application`);
} else {
  xml = xml.replace('</manifest>', `${block}\n</manifest>`);
}

// Injeta service/receiver antes de </application> (idempotente)
if (!xml.includes('TimerService')) {
  xml = xml.replace('</application>', `${SERVICE_XML}    </application>`);
}

xml = xml.replace(/\n{3,}/g, '\n\n');
xml = xml.replace(/\s*<!-- Permissions -->\s*/g, '\n');

const pkg = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
if (xml.includes('android:versionCode')) {
  xml = xml.replace(/android:versionCode="\d+"/, 'android:versionCode="3"');
  xml = xml.replace(/android:versionName="[^"]*"/, `android:versionName="${pkg.version}"`);
} else {
  xml = xml.replace('<manifest', `<manifest android:versionCode="3" android:versionName="${pkg.version}"`);
}

fs.writeFileSync(manifestPath, xml);
console.log('AndroidManifest.xml atualizado (permissões + TimerService + versão)');
console.log('prepare-android concluído');
