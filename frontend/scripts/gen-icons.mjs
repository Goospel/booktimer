// PWA 아이콘 생성 스크립트 — SVG → PNG (sharp)
// 사용: node frontend/scripts/gen-icons.mjs
import sharp from 'sharp';
import { readFileSync, mkdirSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const outDir = resolve(repoRoot, 'src/main/resources/static/icons');

mkdirSync(outDir, { recursive: true });

const svgRegular = readFileSync(resolve(here, 'icon-regular.svg'));
const svgMaskable = readFileSync(resolve(here, 'icon-maskable.svg'));

await sharp(svgRegular).resize(192, 192).png().toFile(resolve(outDir, 'icon-192.png'));
console.log('✓ icon-192.png');

await sharp(svgRegular).resize(512, 512).png().toFile(resolve(outDir, 'icon-512.png'));
console.log('✓ icon-512.png');

await sharp(svgMaskable).resize(512, 512).png().toFile(resolve(outDir, 'maskable-512.png'));
console.log('✓ maskable-512.png');

await sharp(svgRegular).resize(180, 180).png().toFile(resolve(outDir, 'apple-touch-icon.png'));
console.log('✓ apple-touch-icon.png');

console.log('\n아이콘 생성 완료 →', outDir);
