// PWA 설치 유도 칩 — 순수 함수 단위 테스트 (RED → GREEN TDD)
//
// 검증 범위: isDismissalActive·detectPlatform·decideChip 세 함수
// DOM·beforeinstallprompt·display-mode·iOS 공유시트는 jsdom 불가(N-084) → 실 브라우저 게이트
import { describe, test, expect } from 'vitest';
import {
    isDismissalActive,
    detectPlatform,
    decideChip,
    DISMISS_MS,
} from '../../src/main/resources/static/pwa-install.js';

describe('pwa-install 순수 함수', () => {

    describe('isDismissalActive — 7일 침묵 여부', () => {

        test('저장값 null → false (침묵 없음, 칩 표시)', () => {
            expect(isDismissalActive(1_000_000_000_000, null)).toBe(false);
        });

        test('저장값 undefined → false (칩 표시)', () => {
            expect(isDismissalActive(1_000_000_000_000, undefined)).toBe(false);
        });

        test('7일 미만 경과(1초 남음) → true (아직 침묵, 칩 숨김)', () => {
            const now = 1_000_000_000_000;
            const stored = now - DISMISS_MS + 1_000; // 1초 여유
            expect(isDismissalActive(now, stored)).toBe(true);
        });

        test('정확히 7일 경과 → false (만료, 칩 표시)', () => {
            const now = 1_000_000_000_000;
            const stored = now - DISMISS_MS; // 정확히 7일 전
            expect(isDismissalActive(now, stored)).toBe(false);
        });

        test('7일 초과 경과(1초 더) → false (만료, 칩 표시)', () => {
            const now = 1_000_000_000_000;
            const stored = now - DISMISS_MS - 1_000;
            expect(isDismissalActive(now, stored)).toBe(false);
        });
    });

    describe('detectPlatform — UA·displayMode 기반 플랫폼 감지', () => {

        const BROWSER = 'browser';
        const STANDALONE = 'standalone';

        // iOS Safari UA (순정 Safari, CriOS/FxiOS 아님)
        const IOS_SAFARI_UA =
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ' +
            'AppleWebKit/605.1.15 (KHTML, like Gecko) ' +
            'Version/17.0 Mobile/15E148 Safari/604.1';

        // Android Chrome UA
        const ANDROID_CHROME_UA =
            'Mozilla/5.0 (Linux; Android 13; Pixel 7) ' +
            'AppleWebKit/537.36 (KHTML, like Gecko) ' +
            'Chrome/112.0.0.0 Mobile Safari/537.36';

        // Desktop Chrome UA
        const DESKTOP_CHROME_UA =
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
            'AppleWebKit/537.36 (KHTML, like Gecko) ' +
            'Chrome/112.0.0.0 Safari/537.36';

        // Desktop Edge UA (크로미움 기반, Chrome 포함)
        const DESKTOP_EDGE_UA =
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
            'AppleWebKit/537.36 (KHTML, like Gecko) ' +
            'Chrome/112.0.0.0 Safari/537.36 Edg/112.0.0.0';

        // Firefox UA (비크로미움)
        const FIREFOX_UA =
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) ' +
            'Gecko/20100101 Firefox/109.0';

        // Opera UA (OPR/ 포함)
        const OPERA_UA =
            'Mozilla/5.0 (Windows NT 10.0; Win64; x64) ' +
            'AppleWebKit/537.36 (KHTML, like Gecko) ' +
            'Chrome/112.0.0.0 Safari/537.36 OPR/98.0.0.0';

        // iOS Chrome (CriOS) — 설치 지원 불가
        const IOS_CHROME_UA =
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ' +
            'AppleWebKit/605.1.15 (KHTML, like Gecko) ' +
            'CriOS/112.0.5615.167 Mobile/15E148 Safari/604.1';

        test('displayMode=standalone → "standalone" (이미 설치됨)', () => {
            expect(detectPlatform(ANDROID_CHROME_UA, STANDALONE)).toBe('standalone');
        });

        test('displayMode=standalone은 UA 무관 → "standalone"', () => {
            expect(detectPlatform(IOS_SAFARI_UA, STANDALONE)).toBe('standalone');
        });

        test('iOS Safari UA + browser → "ios"', () => {
            expect(detectPlatform(IOS_SAFARI_UA, BROWSER)).toBe('ios');
        });

        test('Android Chrome UA + browser → "android"', () => {
            expect(detectPlatform(ANDROID_CHROME_UA, BROWSER)).toBe('android');
        });

        test('Desktop Chrome UA + browser → "desktop-chromium"', () => {
            expect(detectPlatform(DESKTOP_CHROME_UA, BROWSER)).toBe('desktop-chromium');
        });

        test('Desktop Edge UA + browser → "desktop-chromium" (Edge도 크로미움)', () => {
            expect(detectPlatform(DESKTOP_EDGE_UA, BROWSER)).toBe('desktop-chromium');
        });

        test('Firefox UA → "unsupported" (beforeinstallprompt 없음)', () => {
            expect(detectPlatform(FIREFOX_UA, BROWSER)).toBe('unsupported');
        });

        test('Opera UA → "unsupported" (OPR/ 제외)', () => {
            expect(detectPlatform(OPERA_UA, BROWSER)).toBe('unsupported');
        });

        test('iOS Chrome(CriOS) → "unsupported" (iOS에서 Chrome은 설치 지원 불가)', () => {
            expect(detectPlatform(IOS_CHROME_UA, BROWSER)).toBe('unsupported');
        });
    });

    describe('decideChip — 칩 표시 여부 결정', () => {

        test('platform=standalone → "hidden"', () => {
            expect(decideChip('standalone', false)).toBe('hidden');
        });

        test('dismissed=true → "hidden" (플랫폼 무관)', () => {
            expect(decideChip('android', true)).toBe('hidden');
        });

        test('dismissed=true, standalone → "hidden"', () => {
            expect(decideChip('standalone', true)).toBe('hidden');
        });

        test('ios + not dismissed → "ios-hint"', () => {
            expect(decideChip('ios', false)).toBe('ios-hint');
        });

        test('android + not dismissed → "prompt"', () => {
            expect(decideChip('android', false)).toBe('prompt');
        });

        test('desktop-chromium + not dismissed → "prompt"', () => {
            expect(decideChip('desktop-chromium', false)).toBe('prompt');
        });

        test('unsupported + not dismissed → "hidden"', () => {
            expect(decideChip('unsupported', false)).toBe('hidden');
        });
    });
});
