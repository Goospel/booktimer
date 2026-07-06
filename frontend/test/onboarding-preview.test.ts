// @vitest-environment jsdom
// 온보딩 아이디↔닉네임 실시간 미리보기 (발견 12).
// sanitizeLoginId는 서버 User.normalizeLoginId(strip→toLowerCase→^[a-z0-9_]{3,20}$)와 동일 규칙의
// 클라이언트 정제 — 미리보기·input 되쓰기·제출값이 삼자 일치하도록 단일 출처화(계획 리스크 §D).
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { sanitizeLoginId, isValidLoginId } from '../src/onboarding/loginIdPreview';
import OnboardingPreview from '../src/onboarding/OnboardingPreview.vue';

describe('sanitizeLoginId — 서버 normalizeLoginId 동치 규칙', () => {
    test('대문자를 소문자로 정규화한다', () => {
        expect(sanitizeLoginId('MyName')).toBe('myname');
    });
    test('허용 외 문자(공백·특수문자·한글)를 제거한다', () => {
        expect(sanitizeLoginId('a b-c!한글')).toBe('abc');
    });
    test('숫자·언더스코어는 보존한다', () => {
        expect(sanitizeLoginId('user_01')).toBe('user_01');
    });
    test('20자를 초과하면 잘라낸다(서버 상한 일치)', () => {
        expect(sanitizeLoginId('a'.repeat(30))).toBe('a'.repeat(20));
    });
    test('빈 문자열·앞뒤 공백을 안전 처리한다', () => {
        expect(sanitizeLoginId('')).toBe('');
        expect(sanitizeLoginId('  Ab_1  ')).toBe('ab_1');
    });
});

describe('isValidLoginId — 서버 패턴(3~20자 [a-z0-9_])', () => {
    test('3자 미만은 무효', () => expect(isValidLoginId('ab')).toBe(false));
    test('3~20자 소문자/숫자/_ 는 유효', () => expect(isValidLoginId('valid_1')).toBe(true));
    test('20자 초과는 무효', () => expect(isValidLoginId('a'.repeat(21))).toBe(false));
});

describe('OnboardingPreview 컴포넌트 (발견 12)', () => {
    test('소셜 경로: 아이디를 @핸들로 정제해 보여주고 닉네임도 표시한다', () => {
        const w = mount(OnboardingPreview, {
            props: { loginId: 'MyName', nickname: '홍길동', needsLoginId: true },
        });
        expect(w.text()).toContain('@myname');
        expect(w.text()).toContain('홍길동');
    });

    test('props 갱신 시 미리보기 핸들이 실시간 갱신된다', async () => {
        const w = mount(OnboardingPreview, {
            props: { loginId: 'ab', nickname: '닉', needsLoginId: true },
        });
        await w.setProps({ loginId: 'valid_id' });
        expect(w.text()).toContain('@valid_id');
    });

    test('로컬 경로(needsLoginId=false)는 아이디 핸들을 그리지 않고 닉네임만 미리보기한다', () => {
        const w = mount(OnboardingPreview, {
            props: { loginId: '', nickname: '로컬사용자', needsLoginId: false },
        });
        expect(w.find('.onb-preview-handle').exists()).toBe(false);
        expect(w.text()).toContain('로컬사용자');
    });

    test('닉네임이 비면 기본 표시("나")로 폴백한다', () => {
        const w = mount(OnboardingPreview, {
            props: { loginId: 'someid', nickname: '   ', needsLoginId: true },
        });
        expect(w.find('.onb-preview-name').text()).toBe('나');
    });
});
