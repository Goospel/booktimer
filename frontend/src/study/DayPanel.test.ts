// @vitest-environment jsdom
// 하루 패널의 백지노트 자리 — 편집기가 아니라 **링크**다(2026-09-15, 설계 2026-09-15-study-focus-lamp §2-1 D2).
//
// 필기와 백지노트를 한 탭 묶음에서 떼면서 백지노트는 오른쪽 바의 전용 화면(/study/recall)이 됐다. 일정 화면에
// 편집기를 남기면 같은 날의 백지노트를 **두 화면**에서 쓰게 돼 어느 쪽이 진짜인지 흐려진다. 그래서 여기엔
// 그 날짜를 여는 링크 한 줄만 둔다 — 지난 날짜의 백지노트를 보고 쓰는 길은 이 링크가 유일하다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';

import DayPanel from './DayPanel.vue';

const BOOKS = [{ id: 7, title: '정보처리기사 실기' }, { id: 9, title: '토익 보카' }];

async function mountDay(props: Partial<Record<string, unknown>> = {}): Promise<VueWrapper> {
    const wrapper = mount(DayPanel, {
        attachTo: document.body,
        props: {
            date: '2026-09-10',
            today: '2026-09-10',
            items: [{ id: 1, date: '2026-09-10', bookId: 9, subject: '토익', task: 'p.30-50' }],
            books: BOOKS,
            aiAccess: 'APPROVED',
            aiAccessAt: null,
            aiEnabled: true,
            aiBusy: false,
            remainingPlan: 1,
            ...props,
        },
    });
    await flushPromises();
    return wrapper;
}

const link = (w: VueWrapper) => w.find('a[data-testid="day-recall-link"]');

beforeEach(() => {
    document.body.innerHTML = '<div></div>';
    vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('하루 패널 — 백지노트는 링크다', () => {
    test('링크가 그 날짜의 백지노트 화면을 연다', async () => {
        expect(link(await mountDay()).attributes('href')).toBe('/study/recall?date=2026-09-10');
    });

    test('오늘이면 「오늘의 백지노트」', async () => {
        expect(link(await mountDay()).text()).toBe('오늘의 백지노트');
    });

    test('지난 날이면 「이 날의 백지노트」이고 href도 그 날짜다', async () => {
        const w = await mountDay({ date: '2026-09-03' });
        expect(link(w).text()).toBe('이 날의 백지노트');
        expect(link(w).attributes('href')).toBe('/study/recall?date=2026-09-03');
    });

    // 편집기가 남으면 같은 날 백지노트를 두 화면에서 쓴다 — 패널 자체를 싣지 않으므로 왕복도 0이다.
    test('편집기도 백지노트 조회도 없다', async () => {
        const w = await mountDay();
        expect(w.find('[data-testid="recall-body"]').exists()).toBe(false);
        expect(w.find('[data-testid="recall-book"]').exists()).toBe(false);
        expect(vi.mocked(fetch).mock.calls.filter(c => String(c[0]).includes('/api/study/recall'))).toHaveLength(0);
    });
});
