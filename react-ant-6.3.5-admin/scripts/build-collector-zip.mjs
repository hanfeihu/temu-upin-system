import fs from 'fs';
import path from 'path';
import { execFileSync } from 'child_process';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(frontendRoot, '..');
const extensionParentDir = path.resolve(repoRoot, 'browser-extensions');
const extensionDirName = '1688-collector';
const extensionDir = path.resolve(extensionParentDir, extensionDirName);
const outputDir = path.resolve(frontendRoot, 'public', 'downloads');
const outputZip = path.resolve(outputDir, 'tminos-1688-collector-latest.zip');
const manifestPath = path.resolve(extensionDir, 'manifest.json');

if (!fs.existsSync(manifestPath)) {
  throw new Error(`Collector extension manifest not found: ${manifestPath}`);
}

fs.mkdirSync(outputDir, { recursive: true });
fs.rmSync(outputZip, { force: true });

execFileSync('zip', ['-qr', outputZip, extensionDirName], {
  cwd: extensionParentDir,
  stdio: 'inherit',
});

console.log(`Collector package generated: ${path.relative(frontendRoot, outputZip)}`);
