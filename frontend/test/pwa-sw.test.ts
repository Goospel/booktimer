// PWA L2 Service Worker 정적 가드
//
// sw.js 파일 존재·핸들러·CACHE 버전 상수·SW 등록 코드를 파일 시스템 읽기로 검증한다.
// SW 캐싱 로직은 jsdom 환경에서 검증 불가(N-084) → 정적 가드 + 실 브라우저 게이트로 커버.
// 대응 JUnit 테스트(PwaSwAccessTest)가 "미인증 /sw.js 200" 회귀를 잡는다.
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');

describe('PWA L2 Service Worker 정적 가드', () => {

    const swPath = resolve(repoRoot, 'src/main/resources/static/sw.js');
    const swContent = () => readFileSync(swPath, 'utf8');

    describe('sw.js — 파일 존재 + 핵심 구조', () => {
        test('sw.js 파일이 존재한다', () => {
            expect(existsSync(swPath)).toBe(true);
        });

        test('CACHE 버전 상수가 있다 (const CACHE = ...)', () => {
            expect(swContent()).toMatch(/const\s+CACHE\s*=/);
        });

        test('install 이벤트 핸들러가 있다', () => {
            expect(swContent()).toMatch(/addEventListener\s*\(\s*['"]install['"]/);
        });

        test('activate 이벤트 핸들러가 있다', () => {
            expect(swContent()).toMatch(/addEventListener\s*\(\s*['"]activate['"]/);
        });

        test('fetch 이벤트 핸들러가 있다', () => {
            expect(swContent()).toMatch(/addEventListener\s*\(\s*['"]fetch['"]/);
        });

        test('clients.claim() 호출이 있다 (activate에서 즉시 제어)', () => {
            expect(swContent()).toMatch(/clients\.claim\(\)/);
        });

        test('/api/ 경로 캐시 제외 — 인증 데이터 보안', () => {
            expect(swContent()).toMatch(/pathname\.startsWith\s*\(\s*['"]\/api\//);
        });
    });

    describe('콘텐츠 해시 전환 — NETWORK_FIRST 졸업', () => {
        test('NETWORK_FIRST 배열이 제거됐다 — 해시 URL은 cache-first 안전', () => {
            // 해시 URL은 내용이 바뀌면 URL이 달라지므로 cache-first에서도 stale 불가
            // NETWORK_FIRST 임시방편 배열은 더 이상 필요 없다
            expect(swContent()).not.toMatch(/const\s+NETWORK_FIRST/);
        });

        test('PRECACHE_URLS에 app.css·garden.js가 없다 — 해시 URL이라 고정 경로 프리캐시 불필요', () => {
            const content = swContent();
            const match = content.match(/const\s+PRECACHE_URLS\s*=\s*\[([\s\S]*?)\]/);
            expect(match).not.toBeNull();
            const precacheBlock = match![1];
            expect(precacheBlock).not.toMatch(/app\.css/);
            expect(precacheBlock).not.toMatch(/garden\.js/);
            expect(precacheBlock).not.toMatch(/pwa-install\.js/);
        });

        test('CACHE 버전이 shell-v5다 — 기존 캐시 무효화', () => {
            expect(swContent()).toMatch(/const\s+CACHE\s*=\s*['"]shell-v5['"]/);
        });

        test('navigate 요청은 여전히 network-first다 — SSR 응답 최신 유지', () => {
            expect(swContent()).toMatch(/request\.mode.*['"]navigate['"]/);
        });
    });

    describe('pwa-head.html 프래그먼트 — SW 등록 코드', () => {
        const fragmentPath = resolve(
            repoRoot,
            'src/main/resources/templates/fragments/pwa-head.html'
        );
        const fragment = () => readFileSync(fragmentPath, 'utf8');

        test("serviceWorker.register('/sw.js') 가 있다", () => {
            expect(fragment()).toMatch(/serviceWorker\.register\s*\(\s*['"]\/sw\.js['"]/);
        });

        test('pwa-install.js가 th:src로 참조된다 — 해시 URL 자동 변환', () => {
            // @{/pwa-install.js}를 th:src로 쓰면 ResourceUrlProvider가 해시 URL로 변환
            expect(fragment()).toMatch(/th:src\s*=\s*["']@\{\/pwa-install\.js\}["']/);
        });
    });
});
