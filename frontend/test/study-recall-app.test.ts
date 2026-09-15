// @vitest-environment jsdom
// 백지노트 화면(/study/recall) — 오른쪽 바 「백지노트」가 여는 전용 화면(설계 2026-09-15-study-focus-lamp §2-3).
//
// 옛 홈 백지복습 카드(dashboard/RecallCard.vue)의 계측을 **이관**했다 — 필기와 백지노트를 한 탭 묶음에서
// 떼면서 오늘치 백지노트의 자리가 홈에서 이 화면으로 옮겨 왔다. 「패널이 도는가」는 RecallPanel.test.ts가
// 이미 잰다. 여기서 재는 것은 **이 화면이 그 패널에게 무엇을 먹이는가**다: 어느 날짜 · 그날 일정만 ·
// 공부 서재 · 어제 문제. 하나만 어긋나도 화면은 멀쩡한 채 엉뚱한 날의 글을 쓰게 된다.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, type VueWrapper } from '@vue/test-utils';

import StudyRecallApp from '../src/study/StudyRecallApp.vue';
import RecallPanel from '../src/study/RecallPanel.vue';

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
        { id: 3, date: '2026-09-12', bookId: 9, subject: '토익 12일', task: '12일 범위' },
    ],
    recalls: [
        { date: '2026-09-20', analyzed: true, hasQuestions: true },
        { date: '2026-09-11', analyzed: true, hasQuestions: true },
    ],
};

function okJson(body: object) {
    return { ok: true, status: 200, json: async () => body, text: async () => '' } as Response;
}

function notFound() {
    return { ok: false, status: 404, json: async () => ({}), text: async () => '' } as Response;
}

/** agenda·서재는 화면이, recall 조회는 그 안의 패널이 쏜다 — 다 받아야 화면이 확정된다. */
function routeFetch(agenda: object | null) {
    return vi.fn((url: string) => {
        if (url.includes('/api/study/agenda')) {
            return agenda === null
                ? Promise.resolve({ ok: false, status: 500, json: async () => ({}), text: async () => '' } as Response)
                : Promise.resolve(okJson(agenda));
        }
        if (url.includes('/api/study/books')) return Promise.resolve(okJson({ searchEnabled: false, books: BOOKS }));
        return Promise.resolve(notFound());   // 그날은 아직 쓴 글이 없다
    });
}

async function mountApp(agenda: object | null = AGENDA): Promise<VueWrapper> {
    vi.stubGlobal('fetch', routeFetch(agenda));
    const wrapper = mount(StudyRecallApp, { attachTo: document.body });
    await vi.waitFor(() => expect(wrapper.text()).not.toContain('불러오는 중'));
    return wrapper;
}

const calls = () => vi.mocked(fetch).mock.calls.map(c => String(c[0]));
const agendaCalls = () => calls().filter(u => u.includes('/api/study/agenda'));

beforeEach(() => {
    history.replaceState(null, '', '/study/recall');
    vi.stubGlobal('fetch', vi.fn());
});

afterEach(() => {
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
    history.replaceState(null, '', '/');
});

describe('백지노트 화면 — 머리', () => {
    test('pill은 「백지노트」다 — 필기와 섞이지 않는 이름', async () => {
        expect((await mountApp()).find('.dash-pill').text()).toBe('백지노트');
    });

    test('오늘이 며칠인지 말한다 — 기기 시계(9/7)가 아니라 서버가 준 날(9/21)이다', async () => {
        const text = (await mountApp()).text();
        expect(text).toContain('9월 21일 (월)');
        expect(text).not.toContain('9월 7일');
    });

    test('공부 잉크(is-study)를 쓴다', async () => {
        expect((await mountApp()).find('.dash-recall-card').classes()).toContain('is-study');
    });
});

describe('백지노트 화면 — 필기와 떨어져 있다', () => {
    // 탭이 되살아나면 필기와 백지노트가 다시 한 묶음이 된다(사용자 승인 목표 1).
    test('필기 탭·필기 패널이 없고, 백지노트 패널은 있다', async () => {
        const wrapper = await mountApp();
        expect(wrapper.find('[data-testid="tab-notes"]').exists()).toBe(false);
        expect(wrapper.find('[data-testid="notes-book"]').exists()).toBe(false);
        // 양성 대조 — 같은 마운트에서 백지노트 쪽 셀렉터는 잡힌다(없는 걸 못 찾는 조회와 구분).
        expect(wrapper.find('[data-testid="recall-book"]').exists()).toBe(true);
    });
});

describe('백지노트 화면 — 패널에 무엇을 먹이는가', () => {
    test('이번 달 일정을 한 번 부른다', async () => {
        await mountApp();
        expect(agendaCalls()).toHaveLength(1);
        expect(agendaCalls()[0]).toContain('/api/study/agenda?month=2026-09');
    });

    test('오늘 날짜로 연다 — 서버(유저 tz) 기준 today이지 기기 시계가 아니다', async () => {
        await mountApp();
        expect(calls().some(u => u.includes(`/api/study/recall/${TODAY}`))).toBe(true);
    });

    // 달력(StudyApp)과 같은 1회 조회 — 홈과 달리 이 화면엔 서재를 들고 있는 부모가 없다.
    test('공부 서재를 한 번 받아 책 선택지에 싣는다', async () => {
        const wrapper = await mountApp();
        await vi.waitFor(() => expect(wrapper.findAll('[data-testid="recall-book"] option')).toHaveLength(3));
        const options = wrapper.findAll('[data-testid="recall-book"] option').map(o => o.text());
        expect(options).toContain('정보처리기사 실기');
        expect(options).toContain('토익 보카');
        expect(calls().filter(u => u.includes('/api/study/books'))).toHaveLength(1);
    });

    // 경계 — items를 안 거르면 어제 일정의 과목·범위가 오늘 칸에 프리필된다(화면은 멀쩡하다).
    test('오늘 일정만 프리필로 간다 — 어제 것이 섞이지 않는다', async () => {
        const wrapper = await mountApp();
        const subject = wrapper.find('input[aria-label="주제"]').element as HTMLInputElement;
        const scope = wrapper.find('.study-recall-scope').element as HTMLTextAreaElement;
        expect(subject.value).toBe('정보처리기사');
        expect(scope.value).toBe('2과목 정리');
        expect(scope.value).not.toContain('어제 범위');
    });

    // 전날 복습에 문제가 붙어 있으면 오늘 칸 위에 그 문제가 뜬다 — 달력이 이미 아는 사실을 이 화면도 안다.
    test('어제의 복습문제를 이어 받는다', async () => {
        await mountApp();
        expect(calls().some(u => u.includes('/api/study/recall/2026-09-20'))).toBe(true);
    });
});

// 일정 화면 하루 패널의 링크(`?date=`)가 여는 날 — 지난 날짜 백지노트의 유일한 경로다(설계 §2-1 D2).
describe('백지노트 화면 — ?date=로 지난 날을 연다', () => {
    const TEN = { ...AGENDA, today: '2026-10-05' };   // 오늘은 10월인데 9월 12일을 연다

    test('그 달 일정을 한 번만 부르고, 그 날짜의 글을 연다', async () => {
        history.replaceState(null, '', '/study/recall?date=2026-09-12');
        const wrapper = await mountApp(TEN);

        // today(10월)와 달이 달라도 재조회하지 않는다 — 요청한 달은 기기 시계가 아니라 그 날짜가 정했다.
        expect(agendaCalls()).toEqual(['/api/study/agenda?month=2026-09']);
        expect(calls().some(u => u.includes('/api/study/recall/2026-09-12'))).toBe(true);
        expect(wrapper.text()).toContain('9월 12일 (토)');
        // 그날 일정이 프리필로 간다(오늘 일정이 아니라).
        expect((wrapper.find('input[aria-label="주제"]').element as HTMLInputElement).value).toBe('토익 12일');
    });

    test('어제 문제는 그 날짜의 전날(9/11)로 조회한다', async () => {
        history.replaceState(null, '', '/study/recall?date=2026-09-12');
        await mountApp(TEN);
        expect(calls().some(u => u.includes('/api/study/recall/2026-09-11'))).toBe(true);
    });

    // 리뷰 Minor-1(c) — 위 픽스처는 기기 달(9월)과 같은 달이라 「달을 날짜가 정했는가」와 「기기 시계가 정했는가」가 같은 값이었다.
    test('기기 시계(9월)와 다른 달의 date면 그 날짜의 달로 부른다', async () => {
        history.replaceState(null, '', '/study/recall?date=2026-08-12');
        await mountApp(TEN);
        expect(agendaCalls()).toEqual(['/api/study/agenda?month=2026-08']);
    });

    // 리뷰 Minor-1(b) — 오늘을 그 날짜로 착각하면 미래 날짜에도 편집기가 열린다(저장하면 서버가 거절한다).
    test('미래 date면 「아직 오지 않은 날」이고 편집기가 없다', async () => {
        history.replaceState(null, '', '/study/recall?date=2026-09-25');
        const wrapper = await mountApp();   // 서버 today = 9/21
        expect(wrapper.text()).toContain('아직 오지 않은 날이에요');
        expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(false);
        expect(wrapper.text()).toContain('9월 25일');   // 양성 대조 — 화면은 그 날짜로 떴다
    });

    test('형식이 틀린 date는 무시하고 오늘로 연다', async () => {
        history.replaceState(null, '', '/study/recall?date=2026-9-1');
        await mountApp();
        expect(calls().some(u => u.includes(`/api/study/recall/${TODAY}`))).toBe(true);
    });
});

describe('백지노트 화면 — 실패', () => {
    test('일정을 못 받으면 빈 패널이 아니라 실패라고 말한다', async () => {
        const wrapper = await mountApp(null);
        expect(wrapper.text()).toContain('불러오지 못했');
        expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(false);
    });
});

// 저장·분석 뒤엔 남은 몫과 어제 문제 표식이 달라진다 — 달력과 같은 재조회다.
describe('백지노트 화면 — 저장 뒤 재조회', () => {
    // 리뷰 Minor-1(a) — `@saved` 배선이 끊기면 남은 몫이 옛 값으로 남는다(화면은 멀쩡하다).
    test('패널이 저장을 알리면 일정을 한 번 더 부른다', async () => {
        const wrapper = await mountApp();
        const before = agendaCalls().length;

        wrapper.findComponent(RecallPanel).vm.$emit('saved', {});
        await vi.waitFor(() => expect(agendaCalls()).toHaveLength(before + 1));
    });

    // 리뷰 Minor-4 — 방금 받은 분석 결과가 「불러오지 못했어요」로 통째로 덮이면 안 된다(서버엔 저장됐다).
    test('저장 뒤 재조회가 실패해도 화면을 지우지 않고 작은 문구만 띄운다', async () => {
        const wrapper = await mountApp();
        vi.stubGlobal('fetch', routeFetch(null));   // 이제부터 agenda가 실패한다

        wrapper.findComponent(RecallPanel).vm.$emit('saved', {});
        await vi.waitFor(() => expect(wrapper.find('[data-testid="recall-refresh-failed"]').exists()).toBe(true));

        expect(wrapper.find('[data-testid="recall-body"]').exists()).toBe(true);
        expect(wrapper.text()).toContain('9월 21일 (월)');
    });
});

// 달은 기기 시계로 고르는데 today는 서버(유저 tz)다 — 시차로 달이 갈리는 날(월말·월초) 요청한 달이
// 오늘을 안 담으면 **오늘 일정이 빈 채로** 화면이 멀쩡하게 그려진다. 저장은 서버 today로 가므로 원장
// 오염은 없고, 프리필과 어제 문제만 조용히 사라진다 — 그래서 눈으로는 영영 안 걸린다.
describe('백지노트 화면 — 기기 달과 서버 오늘이 갈릴 때', () => {
    const NEXT = {
        ...AGENDA,
        today: '2026-10-05',
        items: [{ id: 3, date: '2026-10-05', bookId: 7, subject: '10월 과목', task: '10월 범위' }],
        recalls: [],
    };

    test('요청한 달이 오늘을 안 담으면 그 달로 한 번 더 부른다', async () => {
        const wrapper = await mountApp(NEXT);   // 9월 요청에도 10월 today가 온다

        expect(agendaCalls()).toHaveLength(2);
        expect(agendaCalls()[0]).toContain('month=2026-09');
        expect(agendaCalls()[1]).toContain('month=2026-10');

        // 다시 부른 달의 일정이 실제로 프리필까지 닿는다 — 재조회만 하고 안 쓰면 고친 게 아니다.
        const subject = wrapper.find('input[aria-label="주제"]').element as HTMLInputElement;
        expect(subject.value).toBe('10월 과목');
    });

    // 무한 되부름 방지 — 두 번째 응답도 어긋나면 거기서 멈춘다(못 맞추는 서버에 매달리지 않는다).
    test('두 번 이상은 안 부른다', async () => {
        const STUCK = { ...NEXT, today: '2026-11-01' };   // 몇 달을 불러도 today가 요청 달과 안 맞는다
        await mountApp(STUCK);
        await new Promise(r => setTimeout(r, 30));
        expect(agendaCalls()).toHaveLength(2);
    });
});
