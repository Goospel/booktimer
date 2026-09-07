// @vitest-environment jsdom
// 홈 백지복습 카드 — 공부 모드에서 잔디가 있던 자리.
//
// /study의 패널을 그대로 옮겨 놓는 것이라 「패널이 도는가」는 RecallPanel.test.ts가 이미 잰다. 여기서
// 재는 것은 **홈이 그 패널에게 무엇을 먹이는가**다: 오늘 날짜 · 오늘 일정만 · 부모가 이미 들고 있는
// 공부 서재. 셋 중 하나만 어긋나도 화면은 멀쩡한 채 엉뚱한 날의 글을 쓰게 된다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import RecallCard from '../src/dashboard/RecallCard.vue';

// 기기 시계를 못 박는다 — 픽스처의 today가 「실제 오늘」과 같으면 「서버 today를 쓰는가」 단언이
// 기기 시계 구현에도 초록이 된다(리뷰 실측: 돌연변이 생존). Date만 가짜로 두어 타이머는 살린다.
vi.useFakeTimers({ toFake: ['Date'] });
vi.setSystemTime(new Date('2026-09-07T09:00:00+09:00'));

/** 서버가 말하는 오늘 — 기기 시계(9/7)와 <b>다른 날</b>이어야 판별력이 산다. 같은 달이라 달 계산은 안 흔든다. */
const TODAY = '2026-09-21';
const BOOKS = [{ id: 7, title: '정보처리기사 실기' }, { id: 9, title: '토익 보카' }];

const AGENDA = {
    today: TODAY,
    aiAccess: 'APPROVED',
    aiAccessAt: null,
    aiEnabled: true,
    remaining: { plan: 1, transcribe: 2, analyze: 3 },
    items: [
        { id: 1, date: TODAY, bookId: 7, subject: '정보처리기사', task: '2과목 정리' },
        { id: 2, date: '2026-09-20', bookId: 9, subject: '토익', task: '어제 범위' },
    ],
    recalls: [{ date: '2026-09-20', analyzed: true, hasQuestions: true }],
};

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

function notFound() {
    return { ok: false, status: 404, json: async () => ({}), text: async () => '' } as Response;
}

/** agenda는 카드가, recall 조회는 그 안의 패널이 쏜다 — 둘 다 받아야 화면이 확정된다. */
function routeFetch(agenda: object | null) {
    return vi.fn((url: string) => {
        if (url.includes('/api/study/agenda')) {
            return agenda === null
                ? Promise.resolve({ ok: false, status: 500, json: async () => ({}), text: async () => '' } as Response)
                : Promise.resolve(okJson(agenda));
        }
        return Promise.resolve(notFound());   // 오늘은 아직 쓴 글이 없다
    });
}

async function mountCard(agenda: object | null = AGENDA): Promise<VueWrapper> {
    vi.stubGlobal('fetch', routeFetch(agenda));
    const wrapper = mount(RecallCard, { attachTo: document.body, props: { books: BOOKS } });
    await vi.waitFor(() => expect(wrapper.text()).not.toContain('불러오는 중'));
    return wrapper;
}

beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
});

describe('홈 백지복습 카드 — 머리', () => {
    test('pill은 「백지복습」이고 기록 링크가 공부 기록으로 간다', async () => {
        const wrapper = await mountCard();
        expect(wrapper.find('.dash-pill').text()).toBe('백지복습');
        expect(wrapper.find('a[href="/study/history"]').exists()).toBe(true);
    });

    test('오늘이 며칠인지 말한다 — 기기 시계(9/7)가 아니라 서버가 준 날(9/21)이다', async () => {
        const text = (await mountCard()).text();
        expect(text).toContain('9월 21일 (월)');
        expect(text).not.toContain('9월 7일');
    });

    test('공부 잉크(is-study)를 쓴다 — 독서 카드와 같은 색이면 모드가 안 보인다', async () => {
        expect((await mountCard()).find('.dash-recall-card').classes()).toContain('is-study');
    });
});

describe('홈 백지복습 카드 — 패널에 무엇을 먹이는가', () => {
    test('이번 달 일정을 한 번 부른다', async () => {
        await mountCard();
        const url = vi.mocked(fetch).mock.calls[0][0] as string;
        expect(url).toContain('/api/study/agenda?month=');
    });

    test('오늘 날짜로 연다 — 서버(유저 tz) 기준 today이지 기기 시계가 아니다', async () => {
        await mountCard();
        const calls = vi.mocked(fetch).mock.calls.map(c => c[0] as string);
        expect(calls.some(u => u.includes(`/api/study/recall/${TODAY}`))).toBe(true);
    });

    test('부모가 이미 들고 있는 공부 서재를 그대로 쓴다 — 서재를 따로 부르지 않는다', async () => {
        const wrapper = await mountCard();
        const options = wrapper.findAll('[data-testid="recall-book"] option').map(o => o.text());
        expect(options).toContain('정보처리기사 실기');
        expect(options).toContain('토익 보카');

        const calls = vi.mocked(fetch).mock.calls.map(c => c[0] as string);
        expect(calls.some(u => u.includes('/api/study/books'))).toBe(false);
    });

    // 경계 — items를 안 거르면 어제 일정의 과목·범위가 오늘 칸에 프리필된다(화면은 멀쩡하다).
    test('오늘 일정만 프리필로 간다 — 어제 것이 섞이지 않는다', async () => {
        const wrapper = await mountCard();
        const subject = wrapper.find('input[aria-label="과목"]').element as HTMLInputElement;
        const scope = wrapper.find('.study-recall-scope').element as HTMLTextAreaElement;
        expect(subject.value).toBe('정보처리기사');
        expect(scope.value).toBe('2과목 정리');
        expect(scope.value).not.toContain('어제 범위');
    });

    // 전날 복습에 문제가 붙어 있으면 오늘 칸 위에 그 문제가 뜬다 — 달력이 이미 아는 사실을 홈도 안다.
    test('어제의 복습문제를 홈에서도 이어 받는다', async () => {
        const wrapper = await mountCard();
        const calls = vi.mocked(fetch).mock.calls.map(c => c[0] as string);
        expect(calls.some(u => u.includes('/api/study/recall/2026-09-20'))).toBe(true);
    });
});

describe('홈 백지복습 카드 — 실패', () => {
    test('일정을 못 받으면 빈 패널이 아니라 실패라고 말한다', async () => {
        const wrapper = await mountCard(null);
        expect(wrapper.text()).toContain('불러오지 못했');
        expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(false);
    });
});

// 달은 기기 시계로 고르는데 today는 서버(유저 tz)다 — 시차로 달이 갈리는 날(월말·월초) 요청한 달이
// 오늘을 안 담으면 **오늘 일정이 빈 채로** 화면이 멀쩡하게 그려진다. 저장은 서버 today로 가므로 원장
// 오염은 없고, 프리필과 어제 문제만 조용히 사라진다 — 그래서 눈으로는 영영 안 걸린다.
describe('홈 백지복습 카드 — 기기 달과 서버 오늘이 갈릴 때', () => {
    const NEXT = {
        ...AGENDA,
        today: '2026-10-05',
        items: [{ id: 3, date: '2026-10-05', bookId: 7, subject: '10월 과목', task: '10월 범위' }],
        recalls: [],
    };

    test('요청한 달이 오늘을 안 담으면 그 달로 한 번 더 부른다', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => {
            if (url.includes('month=2026-10')) return Promise.resolve(okJson(NEXT));
            if (url.includes('/api/study/agenda')) return Promise.resolve(okJson(NEXT));   // 9월 요청에도 10월 today가 온다
            return Promise.resolve(notFound());
        }));
        const wrapper = mount(RecallCard, { attachTo: document.body, props: { books: BOOKS } });
        await vi.waitFor(() => expect(wrapper.text()).not.toContain('불러오는 중'));

        const months = vi.mocked(fetch).mock.calls
            .map(c => String(c[0])).filter(u => u.includes('/api/study/agenda'));
        expect(months).toHaveLength(2);
        expect(months[0]).toContain('month=2026-09');
        expect(months[1]).toContain('month=2026-10');

        // 다시 부른 달의 일정이 실제로 프리필까지 닿는다 — 재조회만 하고 안 쓰면 고친 게 아니다.
        const subject = wrapper.find('input[aria-label="과목"]').element as HTMLInputElement;
        expect(subject.value).toBe('10월 과목');
    });

    // 무한 되부름 방지 — 두 번째 응답도 어긋나면 거기서 멈춘다(못 맞추는 서버에 매달리지 않는다).
    test('두 번 이상은 안 부른다', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => {
            if (url.includes('/api/study/agenda')) return Promise.resolve(okJson(NEXT));
            return Promise.resolve(notFound());
        }));
        const wrapper = mount(RecallCard, { attachTo: document.body, props: { books: BOOKS } });
        await vi.waitFor(() => expect(wrapper.text()).not.toContain('불러오는 중'));
        await new Promise(r => setTimeout(r, 30));

        expect(vi.mocked(fetch).mock.calls.map(c => String(c[0]))
            .filter(u => u.includes('/api/study/agenda'))).toHaveLength(2);
    });
});
