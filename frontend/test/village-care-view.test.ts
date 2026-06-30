// CoC(Phaser) 가로 무대 은퇴 — 돌봄 뷰 단일화 회귀 가드.
//
// 배경: 데스크톱/가로에서 PortraitVillage(다마고치 돌봄 뷰) ↔ GardenGame(Phaser CoC)을
// 미디어쿼리로 분기하던 반응형 2모드를, 모든 화면에서 PortraitVillage 단독으로 단일화한다.
// GardenGame·scene.ts(Phaser)는 삭제 — 이 그물은 그 둘(Phaser 의존, GardenGame 마운트)이
// 다시 새어드는 것을 정적으로 차단한다. (CoC 제거는 시각·구조 변경이라 단위 TDD가 빈약 →
// 이 정적 가드 + 실 브라우저 게이트가 회귀를 막는다.)
//
// 주석을 통째로 벗기는 정규식 sanitization(.replace(/<!--…-->/))은 쓰지 않는다 —
// 그 자체가 "불완전 멀티문자 위생처리"(CodeQL js/incomplete-multi-character-sanitization)
// 대상이고, 여기 목적은 "출력 안전화"가 아니라 import 구문·컴포넌트 태그 탐지다. 그래서
// 주석에 섞일 산문("Phaser 무대"·"GardenGame 은퇴")이 아니라 import/태그 패턴만 직접 겨냥한다.
import { describe, test, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..'); // frontend/test → 저장소 루트
const gardenDir = resolve(repoRoot, 'frontend/src/garden');

// garden 소스(.ts·.vue)에 phaser import/require가 0이면 true. 부활 시 false(scene.ts 재유입 감지).
// `from 'phaser'` / `require('phaser')` 구문만 매칭 — 주석 산문 언급("Phaser 무대")은 잡지 않는다.
export function noPhaserImportIn(files: { name: string; content: string }[]): boolean {
    const importsPhaser = (src: string) =>
        /\bfrom\s+['"]phaser['"]/.test(src) || /\brequire\(\s*['"]phaser['"]\s*\)/.test(src);
    return files.every(f => !importsPhaser(f.content));
}

// VillageApp이 GardenGame을 import하거나 마운트하지 않으면 true(돌봄 뷰 단독).
// `import … GardenGame` 구문 / `<GardenGame …>` 태그만 매칭 — 주석 속 'GardenGame' 언급(은퇴 설명)은 무관.
export function isSingleCareView(villageAppSrc: string): boolean {
    const importsGardenGame = /\bimport\b[^;\n]*\bGardenGame\b/.test(villageAppSrc);
    const mountsGardenGame = /<GardenGame[\s/>]/.test(villageAppSrc);
    return !importsGardenGame && !mountsGardenGame;
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

    // ── 픽스처: 재유입을 false로 잡고, 주석 산문은 무시하는지 ──
    test('Phaser import 부활 픽스처 → false (재유입 감지)', () => {
        expect(noPhaserImportIn([{ name: 'scene.ts', content: `import Phaser from 'phaser';` }])).toBe(false);
    });

    test('require(phaser) 부활 픽스처 → false', () => {
        expect(noPhaserImportIn([{ name: 'scene.ts', content: `const P = require('phaser');` }])).toBe(false);
    });

    test('주석 속 phaser 산문 언급은 무시 (import 구문 아님)', () => {
        expect(noPhaserImportIn([{ name: 'x.ts', content: `// 예전엔 Phaser 가로 무대를 썼다(scene.ts)` }])).toBe(true);
    });

    test('GardenGame 재마운트 픽스처 → false', () => {
        expect(isSingleCareView(`<template><GardenGame :data="d"/></template>`)).toBe(false);
    });

    test('GardenGame import 부활 픽스처 → false', () => {
        expect(isSingleCareView(`import GardenGame from './GardenGame.vue';`)).toBe(false);
    });

    test('주석 속 GardenGame 언급은 무시 (마운트/import 아님)', () => {
        expect(isSingleCareView(`<!-- CoC 가로 무대(GardenGame)는 은퇴 -->`)).toBe(true);
    });
});
