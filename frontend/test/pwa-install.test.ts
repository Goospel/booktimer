// PWA 설치 유도 칩 — 순수 함수 단위 테스트 (RED → GREEN TDD)
//
// 검증 범위: isDismissalActive·detectPlatform·decideChip·miniappTarget 네 함수
// DOM·beforeinstallprompt·display-mode는 jsdom 불가(N-084) → 실 브라우저 게이트
import { describe, test, expect } from 'vitest';
import {
    isDismissalActive,
    detectPlatform,
    decideChip,
    miniappTarget,
    isLoggedInFromMeta,
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

        // iOS Chrome (CriOS)
        const IOS_CHROME_UA =
            'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ' +
            'AppleWebKit/605.1.15 (KHTML, like Gecko) ' +
            'CriOS/112.0.5615.167 Mobile/15E148 Safari/604.1';

        // Android Firefox (비크로미움 모바일)
        const ANDROID_FIREFOX_UA =
            'Mozilla/5.0 (Android 13; Mobile; rv:109.0) Gecko/119.0 Firefox/119.0';

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

        // ⚠️ 모바일 판별의 기준이 「PWA를 설치할 수 있는가」에서 「모바일인가」로 바뀌었다.
        //    모바일에는 이제 설치 칩이 아니라 미니앱 유도를 띄우고, 그 수단은 딥링크라
        //    beforeinstallprompt를 지원하지 않는 브라우저에서도 똑같이 동작한다.
        test('iOS Chrome(CriOS) → "ios" (미니앱 유도는 설치 지원과 무관하다)', () => {
            expect(detectPlatform(IOS_CHROME_UA, BROWSER)).toBe('ios');
        });

        test('Android Firefox → "android" (같은 이유 — 크로미움이 아니어도 딥링크는 열린다)', () => {
            expect(detectPlatform(ANDROID_FIREFOX_UA, BROWSER)).toBe('android');
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

        // 모바일 두 갈래는 PWA 설치가 아니라 미니앱으로 보낸다 — 웹의 주 사용처를
        // 데스크톱·태블릿에 두는 방침(모바일은 미니앱)의 집행 지점이 여기다.
        test('ios + not dismissed → "miniapp"', () => {
            expect(decideChip('ios', false)).toBe('miniapp');
        });

        test('android + not dismissed → "miniapp"', () => {
            expect(decideChip('android', false)).toBe('miniapp');
        });

        test('desktop-chromium + not dismissed → "prompt"', () => {
            expect(decideChip('desktop-chromium', false)).toBe('prompt');
        });

        test('unsupported + not dismissed → "hidden"', () => {
            expect(decideChip('unsupported', false)).toBe('hidden');
        });
    });

    // 이 함수가 막는 것은 UI 취향이 아니라 **계정이 갈리는 사고**다.
    // 로그인한 사람을 딥링크로 바로 보내면 미니앱에서 토스로 로그인해 새 계정이 만들어지고,
    // toss_user_key는 once-set 불변이라 그 뒤엔 두 기록을 되돌려 합칠 수 없다.
    // 그래서 로그인 상태에서는 연결 코드를 먼저 받게 하는 설정 카드로 보낸다.
    describe('miniappTarget — 미니앱 칩을 눌렀을 때 갈 곳', () => {

        test('비로그인 → 딥링크로 바로 (계정이 없으니 갈릴 것도 없다)', () => {
            expect(miniappTarget(false)).toBe('intoss://booktimer/');
        });

        test('로그인 → 설정의 연결 카드로 (딥링크로 바로 가면 새 계정이 생겨 기록이 갈린다)', () => {
            expect(miniappTarget(true)).toBe('/settings#toss-link');
        });
    });

    // 위 판단의 입력을 만드는 자리. 두 목적지의 실패 대가가 **대칭이 아니다** —
    // 로그인인데 비로그인으로 읽으면 딥링크로 직행해 계정이 갈리고(되돌릴 수 없다),
    // 비로그인인데 로그인으로 읽으면 설정을 거쳐 로그인 화면을 볼 뿐이다(되돌릴 수 있다).
    // 그래서 「모르면 로그인」으로 기운다 — meta가 없거나 값이 낯설면 안전한 쪽이 기본이다.
    describe('isLoggedInFromMeta — 모르면 안전한 쪽으로', () => {

        test('content="anon" → false (서버가 비로그인이라고 명시한 유일한 경우)', () => {
            expect(isLoggedInFromMeta('anon')).toBe(false);
        });

        test('content="user" → true', () => {
            expect(isLoggedInFromMeta('user')).toBe(true);
        });

        test('meta 없음(null) → true (렌더 실패로 계정이 갈리지 않게)', () => {
            expect(isLoggedInFromMeta(null)).toBe(true);
        });

        test('낯선 값 → true (오타·옛 값도 안전한 쪽으로)', () => {
            expect(isLoggedInFromMeta('anonymous')).toBe(true);
        });
    });
});
