// @vitest-environment jsdom
// StudyBookSheet — 공부 책 시트(시작 고르기 · 종료 후 태깅 · 측정 중 교체). 미니앱 BookSheet/ChangeBookSheet 계약:
// **목록만, 검색 없음, fetch 0**. 데이터는 /api/dashboard가 이미 실어 온 study.books라 props로 온다.
//
// 계측기 메모
//  · 통과가 확정하는 것: 세 모드가 **서로 다른 문구**를 낸다 · 고른 책의 **id가 pick 인자로** 나간다 ·
//    currentBookId가 그 행에만 aria-current를 붙인다 · 빈 서재면 /study/books로 보낸다 · fetch를 안 부른다.
//  · 실패가 배제하는 것: 모드 문구 뒤섞임(태깅 시트가 「고르세요」) · 인덱스를 id로 보내기 ·
//    aria-current 전 행 부착 · 시트가 자기 목록을 fetch로 다시 받기(독서 시트의 습관).
//
// fetch는 부르는 즉시 죽는 스텁 — 「fetch 0」은 음성 단언이라 아래 「props의 책을 그렸다」 양성과 쌍으로만 의미가 있다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyBookSheet from '../src/dashboard/StudyBookSheet.vue';

const BOOKS = [
    { id: 1, title: '헌법', author: '홍길동', coverUrl: null, isbn13: null, readCount: 2, purchaseLink: null, totalSeconds: 120 },
    { id: 2, title: '형법', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0 },
];

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => { throw new Error('sheet must not fetch'); }));
});
afterEach(() => { vi.unstubAllGlobals(); document.body.innerHTML = ''; });

function mountSheet(props: Record<string, unknown>) {
    return mount(StudyBookSheet, { props: { mode: 'start', books: BOOKS, ...props }, attachTo: document.body });
}

describe('StudyBookSheet — start', () => {
    test('props의 책을 그리고(회독 칩 포함) 고르면 그 책 id를 pick으로 낸다 — fetch는 0건', async () => {
        const w = mountSheet({ mode: 'start' });

        expect(w.find('.book-sheet-title').text()).toBe('공부할 책을 고르세요');
        const rows = w.findAll('.book-sheet-book');
        expect(rows).toHaveLength(2);
        expect(rows[0].text()).toContain('헌법');
        expect(rows[0].text()).toContain('2독');
        expect(rows[1].text()).toContain('0독');   // 0독도 그린다(부재가 아니라 상태)

        await rows[1].trigger('click');
        expect(w.emitted('pick')).toEqual([[2]]);   // 인덱스(1)가 아니라 id(2)
        expect(fetch).not.toHaveBeenCalled();
    });

    test('하단 CTA는 「책 없이 측정하기」 → none', async () => {
        const w = mountSheet({ mode: 'start' });
        expect(w.find('.book-sheet-cta').text()).toBe('책 없이 측정하기');
        await w.find('.book-sheet-cta').trigger('click');
        expect(w.emitted('none')).toHaveLength(1);
        expect(w.emitted('pick')).toBeUndefined();
    });
});

describe('StudyBookSheet — tag', () => {
    test('제목·CTA가 태깅 문구로 갈린다(시작 문구가 남아 있지 않다)', async () => {
        const w = mountSheet({ mode: 'tag' });

        expect(w.find('.book-sheet-title').text()).toBe('무슨 책을 공부하셨나요?');
        expect(w.find('.book-sheet-title').text()).not.toContain('고르세요');
        expect(w.find('.book-sheet-cta').text()).toContain('건너뛰기');

        await w.find('.book-sheet-cta').trigger('click');
        expect(w.emitted('none')).toHaveLength(1);
    });
});

describe('StudyBookSheet — change', () => {
    test('지금 그 책 행에만 aria-current가 붙고, CTA는 「책 없이 공부하기」', async () => {
        const w = mountSheet({ mode: 'change', currentBookId: 2 });

        expect(w.find('.book-sheet-title').text()).toBe('다른 책으로 바꿀까요?');
        const rows = w.findAll('.book-sheet-book');
        expect(rows[0].attributes('aria-current')).toBeUndefined();   // 양성 대조: 전 행 부착이면 여기서 죽는다
        expect(rows[1].attributes('aria-current')).toBe('true');
        expect(w.find('.book-sheet-cta').text()).toBe('책 없이 공부하기');
    });

    test('currentBookId가 없으면 어느 행에도 aria-current가 없다', () => {
        const w = mountSheet({ mode: 'change' });
        expect(w.findAll('.book-sheet-book').filter(r => r.attributes('aria-current') !== undefined)).toHaveLength(0);
    });
});

describe('StudyBookSheet — 빈 서재 · 닫기 · 표현', () => {
    test('책이 0권이면 목록 대신 공부 서재로 가는 링크', () => {
        const w = mountSheet({ books: [] });

        expect(w.findAll('.book-sheet-book')).toHaveLength(0);
        expect(w.find('.book-sheet-empty a').attributes('href')).toBe('/study/books');
        // 시트는 여전히 「책 없이」로 나갈 수 있다 — 책이 없다고 측정을 막지 않는다.
        expect(w.find('.book-sheet-cta').exists()).toBe(true);
    });

    test('오버레이 바깥 클릭 → close(패널 안 클릭은 아니다)', async () => {
        const w = mountSheet({});
        await w.find('.book-sheet-panel').trigger('click');
        expect(w.emitted('close')).toBeUndefined();   // 양성 대조: self가 아니면 닫히지 않는다

        await w.find('.book-sheet-overlay').trigger('click');
        expect(w.emitted('close')).toHaveLength(1);
    });

    test('pending이면 책 행·CTA가 잠긴다(왕복 중 이중 클릭 방지)', async () => {
        const w = mountSheet({ pending: true });
        expect(w.find('.book-sheet-book').attributes('disabled')).toBeDefined();
        expect(w.find('.book-sheet-cta').attributes('disabled')).toBeDefined();
    });

    test('기본 이모지를 쓰지 않는다', () => {
        expect(mountSheet({}).text()).not.toMatch(/[\u{1F300}-\u{1FAFF}]/u);
    });
});
