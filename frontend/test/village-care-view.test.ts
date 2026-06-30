// CoC(Phaser) 가로 무대 은퇴 — 돌봄 뷰 단일화 회귀 가드.
//
// 배경: 데스크톱/가로에서 PortraitVillage(다마고치 돌봄 뷰) ↔ GardenGame(Phaser CoC)을
// 미디어쿼리로 분기하던 반응형 2모드를, 모든 화면에서 PortraitVillage 단독으로 단일화한다.
// GardenGame·scene.ts(Phaser)는 삭제 — 이 그물은 그 둘(Phaser 의존, GardenGame 마운트)이
// 다시 새어드는 것을 정적으로 차단한다. (CoC 제거는 시각·구조 변경이라 단위 TDD가 빈약 →
// 이 정적 가드 + 실 브라우저 게이트가 회귀를 막는다.)
import { describe, test, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..'); // frontend/test → 저장소 루트
const gardenDir = resolve(repoRoot, 'frontend/src/garden');

// 코드만 남기고 주석 제거(JS // /* */ 와 HTML <!-- -->) — 주석 속 "phaser"·"GardenGame" 언급 오탐 방지.
function stripComments(s: string): string {
    return s
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/\/\/.*$/gm, '');
}

// garden 소스(.ts·.vue) 전 파일에서 phaser import가 0이면 true. 부활 시 false(scene.ts 재유입 감지).
export function noPhaserImportIn(files: { name: string; content: string }[]): boolean {
    return files.every(f => !/\bfrom\s+['"]phaser['"]/.test(stripComments(f.content)));
}

// VillageApp이 GardenGame 컴포넌트를 import/마운트하지 않으면 true(돌봄 뷰 단독).
export function isSingleCareView(villageAppSrc: string): boolean {
    return !/GardenGame/.test(stripComments(villageAppSrc));
}

function gardenSourceFiles(): { name: string; content: string }[] {
    return readdirSync(gardenDir)
        .filter(n => n.endsWith('.ts') || n.endsWith('.vue'))
        .map(n => ({ name: n, content: readFileSync(resolve(gardenDir, n), 'utf8') }));
}

describe('CoC(Phaser) 무대 은퇴 — 돌봄 뷰 단일화 가드', () => {

    test('garden 소스 전체에 Phaser import가 없다 (scene.ts 은퇴)', () => {
        expect(noPhaserImportIn(gardenSourceFiles())).toBe(true);
    });

    test('VillageApp은 GardenGame을 마운트하지 않는다 (PortraitVillage 단독)', () => {
        const src = readFileSync(resolve(gardenDir, 'VillageApp.vue'), 'utf8');
        expect(isSingleCareView(src)).toBe(true);
    });

    // ── 픽스처: 재유입을 false로 잡는지 ──
    test('Phaser import 부활 픽스처 → false (재유입 감지)', () => {
        expect(noPhaserImportIn([{ name: 'scene.ts', content: `import Phaser from 'phaser';` }])).toBe(false);
    });

    test('주석 속 phaser 언급은 무시 (실제 import 아님)', () => {
        expect(noPhaserImportIn([{ name: 'x.ts', content: `// 예전엔 import Phaser from 'phaser' 였다` }])).toBe(true);
    });

    test('GardenGame 재마운트 픽스처 → false', () => {
        expect(isSingleCareView(`<template><GardenGame :data="d"/></template>`)).toBe(false);
    });
});
