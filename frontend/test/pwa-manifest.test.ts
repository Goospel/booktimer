// PWA 자산 정적 가드 — manifest.json·pwa-head 프래그먼트 회귀 가드.
//
// 배경: SecurityConfig default-deny 환경에서 manifest.json·아이콘이
// 공개 허용됐는지, head 프래그먼트가 필수 메타를 갖추는지를
// 파일 시스템 읽기로 사전에 확인한다(런타임 오기 전 정적 가드).
// 대응 JUnit 테스트(PwaStaticAccessTest)가 "미인증 200" 회귀를 잡는다.
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');

describe('PWA 자산 정적 가드', () => {

    describe('manifest.json — 유효 JSON + 필수 필드', () => {
        const manifest = () =>
            JSON.parse(
                readFileSync(resolve(repoRoot, 'src/main/resources/static/manifest.json'), 'utf8')
            ) as Record<string, unknown>;

        test('manifest.json 이 유효 JSON 이다', () => {
            expect(() => manifest()).not.toThrow();
        });

        test('name 필드가 있다', () => {
            expect(manifest().name).toBeTruthy();
        });

        test('start_url 이 / 이다 (콜드 런치 시작점 = 실제 라우트; /dashboard 는 매핑 없음 → 404)', () => {
            expect(manifest().start_url).toBe('/');
        });

        test('display 가 standalone 이다', () => {
            expect(manifest().display).toBe('standalone');
        });

        test('icons 에 192×192 가 있다', () => {
            const icons = manifest().icons as Array<{ sizes: string }>;
            const sizes = icons.map(i => i.sizes);
            expect(sizes).toContain('192x192');
        });

        test('icons 에 512×512 가 있다', () => {
            const icons = manifest().icons as Array<{ sizes: string }>;
            const sizes = icons.map(i => i.sizes);
            expect(sizes).toContain('512x512');
        });
    });

    describe('pwa-head.html 프래그먼트 — 필수 메타', () => {
        const fragment = () =>
            readFileSync(
                resolve(repoRoot, 'src/main/resources/templates/fragments/pwa-head.html'),
                'utf8'
            );

        test('rel="manifest" 링크가 있다', () => {
            expect(fragment()).toMatch(/rel="manifest"/);
        });

        test('apple-mobile-web-app-capable 메타가 있다', () => {
            expect(fragment()).toMatch(/apple-mobile-web-app-capable/);
        });
    });
});
