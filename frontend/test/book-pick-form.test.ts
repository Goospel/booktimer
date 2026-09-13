// @vitest-environment jsdom
// BookPickForm — 표지 칩 + '바꾸기' + '책 없이'(발견 1, §6.5). 드롭다운을 걷어내고
// 기본 책(최근 읽은 책=이어 읽기)을 칩으로 보여준다. '측정 시작'은 그 책으로 1탭 시작,
// '바꾸기'는 책 고르기 시트를 열고(openSheet), '책 없이 시작'은 start(null). 시작을 막지 않는다.
import { describe, test, expect, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';
import BookPickForm from '../src/dashboard/BookPickForm.vue';
import type { BookOption } from '../src/dashboard/types';

let wrapper: VueWrapper | null = null;
afterEach(() => { wrapper?.unmount(); wrapper = null; });

const reading: BookOption[] = [{ id: 1, title: '데미안' }];

function make(over: Record<string, unknown> = {}) {
    wrapper = mount(BookPickForm, {
        props: { readingBooks: reading, finishedBooks: [], wantToReadBooks: [], recentBookId: 1, ...over },
        attachTo: document.body,
    });
    return wrapper;
}

describe('BookPickForm — 표지 칩 + 바꾸기 + 책 없이 (발견 1)', () => {
    test('기본(책 있음): 칩에 기본 책 제목이 보이고, 측정 시작 → recentBookId로 start', async () => {
        const w = make();
        expect(w.text()).toContain('데미안');
        const btn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([1]);
    });

    test('recentBookId가 없으면 첫 책이 기본 — 측정 시작이 그 책으로 start', async () => {
        const w = make({ recentBookId: null, readingBooks: [{ id: 5, title: '토지' }] });
        const btn = w.findAll('button').find(b => b.text().includes('측정 시작'))!;
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([5]);
    });

    test('바꾸기 → openSheet 발생(책 고르기 시트 열기)', async () => {
        const w = make();
        const btn = w.findAll('button').find(b => b.text().includes('바꾸기'))!;
        await btn.trigger('click');
        expect(w.emitted('openSheet')).toBeTruthy();
    });

    test('책 없이 시작 → start(null)', async () => {
        const w = make();
        const btn = w.findAll('button').find(b => b.text().includes('책 없이'))!;
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([null]);
    });

    test('책 0권: "책 없이 측정 시작" → start(null) — 시작을 막지 않음', async () => {
        const w = make({ readingBooks: [], finishedBooks: [], wantToReadBooks: [], recentBookId: null });
        const btn = w.findAll('button').find(b => b.text().includes('책 없이'))!;
        expect(btn).toBeTruthy();
        await btn.trigger('click');
        expect(w.emitted('start')![0]).toEqual([null]);
    });

    test('책 0권: "책 고르기" → openSheet(검색·담기 시트)', async () => {
        const w = make({ readingBooks: [], finishedBooks: [], wantToReadBooks: [], recentBookId: null });
        const btn = w.findAll('button').find(b => b.text().includes('고르기'))!;
        await btn.trigger('click');
        expect(w.emitted('openSheet')).toBeTruthy();
    });
});

// 표지 — 서버(`/api/dashboard`의 BookOption)는 coverUrl을 **처음부터 보내고 있었다**. 클라이언트가
// 타입에서 그 필드를 빼 놓아 색 박스만 그렸다. 미니앱은 같은 자리에 실표지를 띄운다(사용자 지적 2026-09-07).
//
// 폴백을 함께 재는 이유: 표지 없는 책(직접 추가·알라딘 이미지 없음)이 실제로 있어서, 「img가 뜬다」만
// 재면 폴백을 지워도 초록이다. 두 단언이 한 쌍이어야 「가진 것만 img」가 잠긴다.
describe('BookPickForm — 표지', () => {
    const withCover: BookOption[] = [{ id: 1, title: '데미안', coverUrl: 'https://img.example/demian.jpg' }];

    test('표지가 있으면 실제 이미지를 그린다', () => {
        const w = make({ readingBooks: withCover });
        const img = w.find('img.dash-book-chip-cover');
        expect(img.exists()).toBe(true);
        expect(img.attributes('src')).toBe('https://img.example/demian.jpg');
        // 외부 이미지 호스트에 우리 주소를 흘리지 않는다 — 책장·책방과 같은 관례다.
        expect(img.attributes('referrerpolicy')).toBe('no-referrer');
        expect(w.find('span.dash-book-chip-cover').exists()).toBe(false);
    });

    test('표지가 없으면 지금 색 박스가 그대로 남는다 (양성 대조군)', () => {
        const w = make({ readingBooks: [{ id: 1, title: '데미안', coverUrl: null }] });
        expect(w.find('img.dash-book-chip-cover').exists()).toBe(false);
        expect(w.find('span.dash-book-chip-cover').text()).toBe('데');
    });

    test('빈 문자열·공백은 표지가 아니다 — 깨진 이미지 아이콘을 띄우지 않는다', () => {
        expect(make({ readingBooks: [{ id: 1, title: '데미안', coverUrl: '   ' }] })
            .find('img.dash-book-chip-cover').exists()).toBe(false);
    });

    // 옛 서버·옛 픽스처는 이 필드를 아예 안 보낸다(undefined) — 그때도 색 박스로 떨어져야 한다.
    test('coverUrl 필드가 없어도 깨지지 않는다', () => {
        const w = make({ readingBooks: [{ id: 1, title: '데미안' }] });
        expect(w.find('span.dash-book-chip-cover').exists()).toBe(true);
    });
});
