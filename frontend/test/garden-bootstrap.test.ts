// 정원 부트스트랩 로드순서 회귀 가드 (#393).
//
// 배경: #391(빌드 2차)에서 Alpine을 CDN `defer` <script>로, 번들(main.ts)을 type=module로 두자
// CDN defer Alpine이 자기 스크립트 직후 마이크로태스크 체크포인트에서 Alpine.start()를 돌려,
// 뒤이어 실행되는 번들이 alpine:init 리스너를 걸기 전에 init이 끝나버렸다 → x-data="myGarden"
// 평가 시 ReferenceError로 '내 정원' 전체가 죽음(운영 회귀). #393이 Alpine을 번들로 흡수해
// (main.ts가 Alpine.data 등록 후 직접 Alpine.start) 순서를 코드로 보장하며 구조적으로 해소.
//
// 이 그물의 일 = 그 '구조'가 다시 깨지는 것을 결정적으로 차단:
//   ① garden.html에 Alpine CDN <script>가 부활하면 RED
//   ② main.ts가 Alpine을 import해 data→start 순서로 소유하지 않으면 RED
// (vitest는 pure.ts 순수함수만 보던 사각이라 #393이 CI를 통과했다 — 그 사각을 메운다.)
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..'); // frontend/test → 저장소 루트
const gardenHtml = () =>
    readFileSync(resolve(repoRoot, 'src/main/resources/templates/garden.html'), 'utf8');
const mainTs = () =>
    readFileSync(resolve(repoRoot, 'frontend/src/garden/main.ts'), 'utf8');

// --- 가드 (순수 함수) ---

// HTML에 Alpine CDN <script src=...alpinejs...>가 없으면 true. 부활 시 false로 잡는다(#393 재발).
// 주석(<!-- -->) 안의 script는 실제 로드가 아니므로 제외한다.
export function htmlHasNoAlpineCdn(html: string): boolean {
    const withoutComments = html.replace(/<!--[\s\S]*?-->/g, '');
    return !/<script[^>]*\bsrc\s*=\s*["'][^"']*alpinejs[^"']*["']/i.test(withoutComments);
}

// 번들 진입(main.ts)이 Alpine을 import하고 Alpine.start()를 직접 호출하면 true.
// S2: myGarden이 Vue로 이전해 Alpine.data 호출이 없어도 됨(도감 x-data는 HTML inline).
// import가 없으면(CDN 전역 의존) 또는 start가 없으면 로드순서를 코드로 보장 못 해 false.
export function bundleOwnsAlpine(src: string): boolean {
    const code = src.replace(/\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, ''); // 주석 제거
    const importsAlpine = /import\s+Alpine\s+from\s+['"]alpinejs['"]/.test(code);
    const startIdx = code.indexOf('Alpine.start');
    return importsAlpine && startIdx >= 0;
}

describe('정원 부트스트랩 로드순서 회귀 가드 (#393)', () => {

    describe('htmlHasNoAlpineCdn — Alpine은 번들 소유, CDN 금지', () => {
        test('실제 garden.html엔 Alpine CDN script가 없다', () => {
            expect(htmlHasNoAlpineCdn(gardenHtml())).toBe(true);
        });
        test('Alpine CDN 부활 픽스처는 false로 잡힌다(#393 재발 차단)', () => {
            const broken =
                `<head><script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.14.1/dist/cdn.min.js"></script></head>`;
            expect(htmlHasNoAlpineCdn(broken)).toBe(false);
        });
        test('htmx CDN은 허용(Alpine만 차단)', () => {
            const htmxOnly =
                `<script defer src="https://cdn.jsdelivr.net/npm/htmx.org@2.0.4/dist/htmx.min.js"></script>`;
            expect(htmlHasNoAlpineCdn(htmxOnly)).toBe(true);
        });
        test('주석 처리된 Alpine CDN은 무시(실제 로드 아님)', () => {
            const commented =
                `<!-- <script src="https://cdn.jsdelivr.net/npm/alpinejs@3.14.1/dist/cdn.min.js"></script> -->`;
            expect(htmlHasNoAlpineCdn(commented)).toBe(true);
        });
    });

    describe('bundleOwnsAlpine — main.ts가 Alpine import + start로 번들 소유(S2~: data 없어도 됨)', () => {
        test('실제 main.ts는 Alpine import + start를 가진다', () => {
            expect(bundleOwnsAlpine(mainTs())).toBe(true);
        });
        test('Alpine import가 없으면 false(CDN 전역 의존 = race 복귀)', () => {
            const noImport = `Alpine.start();`;
            expect(bundleOwnsAlpine(noImport)).toBe(false);
        });
        test('Alpine.start 없으면 false(부트스트랩 누락)', () => {
            const noStart = `import Alpine from 'alpinejs';`;
            expect(bundleOwnsAlpine(noStart)).toBe(false);
        });
        test('import + start만 있어도 true(S2: myGarden→Vue로 이전, data 호출 불필요)', () => {
            const importAndStart = `import Alpine from 'alpinejs';\nAlpine.start();`;
            expect(bundleOwnsAlpine(importAndStart)).toBe(true);
        });
    });
});
