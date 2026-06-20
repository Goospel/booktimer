// notification-settings.js 순수 함수 단위 테스트 (RED → GREEN 핸드오프)
//
// 대상: detectPushSupport / vapidConfigured / urlBase64ToUint8Array
// 실 브라우저 게이트(로드순서·권한 흐름)는 Chrome 확장으로 별도 검증.
import { describe, test, expect } from 'vitest';
import {
    detectPushSupport,
    vapidConfigured,
    urlBase64ToUint8Array,
} from '../../src/main/resources/static/notification-settings.js';

describe('detectPushSupport', () => {
    test('iOS + 非standalone → {supported:false, iosInstallHint:true}', () => {
        const result = detectPushSupport({
            ua: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15',
            standalone: false,
            hasServiceWorker: true,
            hasPushManager: true,
        });
        expect(result).toEqual({ supported: false, iosInstallHint: true });
    });

    test('iOS + standalone → {supported:true, iosInstallHint:false}', () => {
        const result = detectPushSupport({
            ua: 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15',
            standalone: true,
            hasServiceWorker: true,
            hasPushManager: true,
        });
        expect(result).toEqual({ supported: true, iosInstallHint: false });
    });

    test('ServiceWorker 없음 → {supported:false, iosInstallHint:false}', () => {
        const result = detectPushSupport({
            ua: 'Mozilla/5.0 (Windows NT 10.0) Chrome/120.0',
            standalone: false,
            hasServiceWorker: false,
            hasPushManager: true,
        });
        expect(result).toEqual({ supported: false, iosInstallHint: false });
    });

    test('PushManager 없음 → {supported:false, iosInstallHint:false}', () => {
        const result = detectPushSupport({
            ua: 'Mozilla/5.0 (Windows NT 10.0) Chrome/120.0',
            standalone: false,
            hasServiceWorker: true,
            hasPushManager: false,
        });
        expect(result).toEqual({ supported: false, iosInstallHint: false });
    });

    test('데스크톱 Chrome + SW + PushManager → {supported:true, iosInstallHint:false}', () => {
        const result = detectPushSupport({
            ua: 'Mozilla/5.0 (Windows NT 10.0) Chrome/120.0',
            standalone: false,
            hasServiceWorker: true,
            hasPushManager: true,
        });
        expect(result).toEqual({ supported: true, iosInstallHint: false });
    });
});

describe('vapidConfigured', () => {
    test('"not-configured" → false', () => {
        expect(vapidConfigured('not-configured')).toBe(false);
    });

    test('빈 문자열 → false', () => {
        expect(vapidConfigured('')).toBe(false);
    });

    test('null → false', () => {
        expect(vapidConfigured(null)).toBe(false);
    });

    test('undefined → false', () => {
        expect(vapidConfigured(undefined)).toBe(false);
    });

    test('정상 키 문자열 → true', () => {
        expect(vapidConfigured('BNcRdreALRFXTkEOTIr1MnDP6M_ARxqEFMBs2StRPBqZSaxE_GRq_4Z9cTY')).toBe(true);
    });
});

describe('urlBase64ToUint8Array', () => {
    test('URL-safe base64(-_)를 표준(+/)으로 변환해 Uint8Array 반환', () => {
        // "hello" → base64 = "aGVsbG8="
        const result = urlBase64ToUint8Array('aGVsbG8=');
        expect(result).toBeInstanceOf(Uint8Array);
        expect(result[0]).toBe(104); // 'h'
        expect(result[1]).toBe(101); // 'e'
    });

    test('패딩 없는 URL-safe base64도 처리', () => {
        // URL-safe base64는 =패딩 생략 가능
        const withPadding = urlBase64ToUint8Array('aGVsbG8=');
        const withoutPadding = urlBase64ToUint8Array('aGVsbG8');
        expect(withPadding).toEqual(withoutPadding);
    });

    test('-와 _ 문자를 +와 /로 변환', () => {
        // "~?" in base64 = "fn8=" → URL-safe = "fn8="(이미 같음). 대신 강제 포함 테스트
        // base64url에서 - → +, _ → /
        // "þÿ" → base64 = "\/8=" → url-safe "\/8=" (슬래시 없는 예: "fn8=")
        // 직접: 0xFB 0xFF → base64 "+/8=" → url-safe "-_8="
        const result = urlBase64ToUint8Array('-_8=');
        expect(result[0]).toBe(0xfb);
        expect(result[1]).toBe(0xff);
    });
});
