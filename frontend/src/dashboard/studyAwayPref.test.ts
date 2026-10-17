// @vitest-environment jsdom
// 공부 홈 「자리 비우면 멈추기」 — 설정 체크박스와 같은 문법(진짜 input은 숨기고 잉크 테 상자 + 체크 획).
// 브라우저 기본 흰 상자는 종이 위에서 혼자 흰 면이었다(에디토리얼 PR-4 마감). CSS 짝은 editorialTheme.test.ts.
import { describe, test, expect } from 'vitest';
import { mount } from '@vue/test-utils';

import StudyTimerCard from './StudyTimerCard.vue';

const book = { id: 1, title: '형법 총론', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 };

function card(awayOn: boolean) {
    return mount(StudyTimerCard, {
        props: { todaySeconds: 0, hasActiveSession: false, activeStartedAt: null, books: [book], recentBookId: 1, awayAvailable: true, awayOn },
    });
}

describe('공부 홈 자리 비움 체크박스', () => {
    test('진짜 체크박스 뒤에 그림 상자(체크 획 svg)가 선다 — 이름·설명 연결은 그대로', () => {
        const w = card(false);
        const input = w.find('.dash-away-pref input[type="checkbox"]');
        expect(input.exists()).toBe(true);
        // 그림 상자는 input 바로 뒤 형제여야 `input:checked ~ .set-check-box`가 닿는다.
        const box = input.element.nextElementSibling as HTMLElement | null;
        expect(box?.classList.contains('set-check-box')).toBe(true);
        expect(box?.getAttribute('aria-hidden')).toBe('true');
        expect(box?.querySelector('svg path')).not.toBeNull();
        expect(input.attributes('aria-labelledby')).toBeTruthy();
        expect(input.attributes('aria-describedby')).toBeTruthy();
    });

    test('켜 둔 값이 진짜 체크박스에 실린다', () => {
        expect((card(true).find('.dash-away-pref input').element as HTMLInputElement).checked).toBe(true);
        expect((card(false).find('.dash-away-pref input').element as HTMLInputElement).checked).toBe(false);
    });
});
