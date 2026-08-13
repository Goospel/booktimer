// 토스 미니앱 아이콘 PNG 생성 — app-icon.svg → app-icon-1024.png
// 사용: node miniapp/icon/gen-icon.mjs   (sharp는 frontend/node_modules에만 있어 절대경로로 끌어온다)
import { readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath, pathToFileURL } from 'url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const sharp = (await import(pathToFileURL(resolve(repoRoot, 'frontend/node_modules/sharp/dist/index.cjs')).href)).default;

const svg = readFileSync(resolve(here, 'app-icon.svg'));
const out = resolve(here, 'app-icon-1024.png');
await sharp(svg).resize(1024, 1024).png().toFile(out);
console.log('✓', out);
