// @vitest-environment jsdom
// DashboardApp — 공부 측정 중 탭을 떠나면 멈추기(설계 2026-09-28-web-study-away-time §3 PR-2·§5).
//
// 계측기 메모
//  · 통과가 확정하는 것: 30초 넘게 떠났다 오면 카드 안 안내 + 「이번 측정」·「오늘」 표시가 떠날 때 값에서 이어진다 ·
//    stop 본문 awayIntervals에 그 구간(ISO from/to)이 실린다 · 되돌리기가 차감과 전송을 함께 푼다 ·
//    pagehide·터치 기기·독서 측정은 원장을 안 만든다 · 로드 시 남은 대기 구간(discard)을 닫는다.
//  · 실패가 배제하는 것: elapsed 소비처 일부만 치환(오늘 ≠ 이번 측정) · 본문 누락·Content-Type 누락 ·
//    되돌리기가 화면만 고치고 전송엔 남기기 · 네트워크 실패에 원장을 잃기.
// 시각은 fake timers로 고정한다 — 측정 시작 10분 뒤에 마운트.
import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import DashboardApp from '../src/dashboard/DashboardApp.vue';
import { AWAY_KEY, AWAY_PREF_KEY } from '../src/dashboard/studyAway';

const START = Date.parse('2026-09-28T01:00:00.000Z');
const MOUNT_AT = START + 600_000;
const iso = (ms: number) => new Date(ms).toISOString();
const GRAPH = {
    weeks: [[{ date: '2026-09-28', totalSeconds: 0, level: 0, manual: false }]],
    monthLabels: [], totalSeconds: 0, activeDays: 0, currentStreak: 0,
};
const STUDY_IDLE = {
    hasActiveSession: false, activeStartedAt: null, todaySeconds: 0, goalSeconds: 0,
    activeBook: null, recentBookId: null, books: [], untaggedSessionId: null,
};
const STUDY_ACTIVE = { ...STUDY_IDLE, hasActiveSession: true, activeStartedAt: iso(START) };
const READING = {
    remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, todayReadSeconds: 0, carryover: true,
    hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, activeBookTotalSeconds: 0,
    readingBooks: [{ id: 1, title: '데미안' }], finishedBooks: [], wantToReadBooks: [], recentBookId: 1,
};

let dash: Record<string, unknown>;
let stopMode: 'ok' | '409' | 'network' = 'ok';
// 있으면 stop 응답 상태를 앞에서부터 하나씩 쓴다(비면 stopMode) — 400 뒤 재시도 계측기.
const stopQueue: number[] = [];
const stops: RequestInit[] = [];
const ok = (json: unknown, status = 200) =>
    Promise.resolve({ ok: status < 400, status, statusText: 'x', json: async () => json });

function fetchImpl(url: string, init?: RequestInit) {
    if (url.includes('/api/study/stop')) {
        stops.push(init ?? {});
        const queued = stopQueue.shift();
        if (queued !== undefined) return queued === 200 ? ok(STUDY_IDLE) : ok({}, queued);
        if (stopMode === 'network') return Promise.reject(new TypeError('Failed to fetch'));
        return stopMode === '409' ? ok({}, 409) : ok(STUDY_IDLE);
    }
    if (url.includes('/api/study/start')) {
        // 서버 진실도 측정 중이 된다 — 안 바꾸면 복귀 재조회가 대기 상태를 실어 와 측정이 끝난 것처럼 보인다.
        // 같은 순간이라도 표기가 다르다(실 브라우저 실측 2026-09-29): 시작 응답은 JVM Instant라 100ns 9자리
        // ("…52.657934400Z"), 재조회는 DB에서 읽어 µs 6자리("…52.657934Z"). 두 출처에 같은 문자열을 두면
        // 「어느 쪽을 읽었나」를 물을 수 없어, 재조회가 첫 이탈 구간을 지우던 결함이 가려졌다.
        const ms = iso(Date.now()).slice(0, -1);   // "…T01:10:00.100"
        const s = { ...STUDY_ACTIVE, activeStartedAt: ms + '934400Z' };
        dash = { ...dash, study: { ...s, activeStartedAt: ms + '934Z' } };
        return ok(s);
    }
    if (url.includes('/api/study/notes?')) return ok({ notes: [] });
    if (url.includes('/api/stories/of/')) {
        return ok({ book: { id: 1, title: '데미안', author: null, coverUrl: null }, ownerNickname: '테스터', self: true, entries: [] });
    }
    if (url.includes('/api/dashboard')) return ok(dash);
    throw new Error('unexpected fetch: ' + url);
}

function setVisible(v: 'visible' | 'hidden') {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => v });
    document.dispatchEvent(new Event('visibilitychange'));
}
async function leaveFor(ms: number, opts: { pagehide?: boolean; pageshow?: boolean } = {}) {
    const leftAt = Date.now();   // 마운트의 waitFor가 가짜 시계를 조금 민다 — 떠난 시각은 여기서 잰다
    // 실제 순서(Chromium 153 실측): 같은 탭 이동·닫기·새로고침은 pagehide(아직 visible)가 hidden보다 **먼저**다.
    if (opts.pagehide) window.dispatchEvent(new Event('pagehide'));
    setVisible('hidden');
    vi.advanceTimersByTime(ms);
    if (opts.pageshow) window.dispatchEvent(new Event('pageshow'));   // bfcache 복귀
    setVisible('visible');
    await flushPromises();
    return leftAt;
}

beforeEach(() => {
    vi.useFakeTimers({ now: MOUNT_AT });
    dash = { nickname: '테스터', loginId: 'tester', ...READING, graph: GRAPH, quotes: [], emailVerified: true, study: STUDY_ACTIVE };
    stopMode = 'ok';
    stopQueue.length = 0;
    stops.length = 0;
    localStorage.clear();
    // 켬 전제의 명시(설계 §9.8 3-a) — 기본은 꺼짐이다. 이 파일의 기존 계측기는 켠 사람의 동작(REQ-04 회귀)을 잰다.
    localStorage.setItem(AWAY_PREF_KEY, 'on');
    vi.stubGlobal('fetch', vi.fn((u: string, i?: RequestInit) => fetchImpl(u, i)));
    vi.stubGlobal('matchMedia', () => ({ matches: true }));   // 마우스·트랙패드
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
});
// 언마운트하지 않으면 앞 인스턴스의 visibilitychange·pagehide 리스너가 남아 다음 테스트 원장을 오염시킨다.
const mounted: { unmount: () => void }[] = [];
afterEach(() => {
    mounted.splice(0).forEach(w => w.unmount());
    vi.useRealTimers(); vi.unstubAllGlobals(); localStorage.clear(); document.body.innerHTML = '';
});

async function mountDash() {
    const w = mount(DashboardApp, { attachTo: document.body });
    mounted.push(w);
    await vi.waitFor(() => expect(w.find('.dash-timer-hero').exists()).toBe(true));
    return w;
}
const session = (w: ReturnType<typeof mount>) => w.find('[data-testid="focus-session"] .dash-kv-v').text();
const today = (w: ReturnType<typeof mount>) => w.find('[data-testid="focus-today"]').text();
const notice = (w: ReturnType<typeof mount>) => w.find('[data-testid="away-notice"]');
const btnWith = (w: ReturnType<typeof mount>, text: string) =>
    w.findAll('.dash-timer-hero button').find(b => b.text().includes(text))!;
const sentIntervals = (i = 0) => JSON.parse(String(stops[i].body)).awayIntervals;
const awayLedger = () => JSON.parse(localStorage.getItem(AWAY_KEY)!) as { intervals: unknown[]; pendingFrom: string | null };

describe('DashboardApp — 공부 측정 중 탭을 떠나면 멈춘다', () => {
    test('(w1) 5분 떠났다 오면 카드 안에 안내가 뜨고, 이번 측정·오늘이 떠날 때 값(10:00)에서 이어진다', async () => {
        const w = await mountDash();
        expect(session(w)).toBe('10:00');
        await leaveFor(300_000);

        expect(notice(w).exists()).toBe(true);
        expect(notice(w).text()).toContain('5분 자리를 비워 타이머를 멈췄어요');
        expect(notice(w).text()).not.toMatch(/\p{Extended_Pictographic}/u);
        expect(session(w)).toBe('10:00');
        expect(today(w)).toBe('10:00');
        // 안내는 카드 안이다 — 오버레이·딤이 아니다.
        expect(w.find('.dash-timer-hero [data-testid="away-notice"]').exists()).toBe(true);
    });

    test('(w2) 종료 요청 본문에 그 구간이 awayIntervals로 실리고 JSON 헤더가 붙는다, 성공하면 원장을 지운다', async () => {
        const w = await mountDash();
        const leftAt = await leaveFor(300_000);
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        await flushPromises();

        expect(sentIntervals()).toEqual([{ from: iso(leftAt), to: iso(leftAt + 300_000) }]);
        expect((stops[0].headers as Record<string, string>)['Content-Type']).toBe('application/json');
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
    });

    test('(w3) 되돌리기 → 차감이 풀리고(15:00) 안내가 닫히며, 종료 본문에도 구간이 없다', async () => {
        const w = await mountDash();
        await leaveFor(300_000);
        await notice(w).findAll('button').find(b => b.text() === '되돌리기')!.trigger('click');

        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('15:00');
        expect(today(w)).toBe('15:00');
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toEqual([]);
    });

    test('(w4) 닫기는 안내만 닫고 차감은 남긴다', async () => {
        const w = await mountDash();
        await leaveFor(300_000);
        await notice(w).find('button[aria-label="안내 닫기"]').trigger('click');
        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('10:00');
    });

    test('(w5) 30초 미만 이탈은 멈추지 않는다', async () => {
        const w = await mountDash();
        await leaveFor(29_000);
        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('10:29');
    });

    test('(w6) 같은 탭 이동: pagehide → hidden 순서여도 대기 구간이 남지 않는다', async () => {
        const w = await mountDash();
        window.dispatchEvent(new Event('pagehide'));
        setVisible('hidden');
        expect(JSON.parse(localStorage.getItem(AWAY_KEY)!).pendingFrom).toBeNull();
        vi.advanceTimersByTime(300_000);
        setVisible('visible');
        await flushPromises();
        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('15:00');
    });

    test('(w6b) 같은 탭 이동 후 홈 재진입(새 로드) → 안내 없음·차감 없음 (U1 경로)', async () => {
        const a = await mountDash();
        window.dispatchEvent(new Event('pagehide'));
        setVisible('hidden');
        a.unmount(); mounted.splice(mounted.indexOf(a), 1);
        vi.advanceTimersByTime(40_000);
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' });
        const b = await mountDash();
        await flushPromises();
        expect(notice(b).exists()).toBe(false);
        expect(awayLedger().intervals).toEqual([]);
    });

    test('(w6c) bfcache 복귀(pageshow) 뒤에는 다시 추적한다', async () => {
        const w = await mountDash();
        await leaveFor(40_000, { pagehide: true, pageshow: true });
        expect(notice(w).exists()).toBe(false);
        await leaveFor(300_000);
        expect(notice(w).text()).toContain('5분 자리를 비워');
    });

    test('(w13) 뒤에서(hidden) 로드되면 떠남을 기록하지 않는다 — 로드 시각이 떠난 시각이 되지 않게', async () => {
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
        await mountDash();
        await flushPromises();
        expect(awayLedger().pendingFrom).toBeNull();
    });

    test('(w14) 안내 live region은 늘 있고 내용만 바뀐다 — 스크린리더가 삽입과 함께 놓치지 않게', async () => {
        const w = await mountDash();
        const live = w.find('.dash-timer-hero [role="status"]');
        expect(live.exists()).toBe(true);
        expect(live.text()).toBe('');
        await leaveFor(300_000);
        const after = w.find('.dash-timer-hero [role="status"]');
        expect(after.element).toBe(live.element);
        expect(after.text()).toContain('5분 자리를 비워');
    });

    test('(w7) 터치 기기(coarse pointer)는 추적하지 않는다', async () => {
        vi.stubGlobal('matchMedia', () => ({ matches: false }));
        const w = await mountDash();
        await leaveFor(300_000);
        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('15:00');
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
    });

    test('(w8) 독서 측정은 추적하지 않는다', async () => {
        dash = { ...dash, hasActiveSession: true, activeStartedAt: iso(START), study: STUDY_IDLE };
        const w = await mountDash();
        await leaveFor(300_000);
        expect(w.find('[data-testid="away-notice"]').exists()).toBe(false);
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
    });

    test('(w9) 탭이 버려졌다 다시 로드되면(discard) 남은 대기 구간을 닫는다', async () => {
        vi.setSystemTime(MOUNT_AT + 300_000);
        localStorage.setItem(AWAY_KEY, JSON.stringify({ startedAt: iso(START), intervals: [], pendingFrom: iso(MOUNT_AT) }));
        const w = await mountDash();
        await flushPromises();
        expect(notice(w).text()).toContain('5분 자리를 비워');
        expect(session(w)).toBe('10:00');
    });

    test('(w10) 다른 측정 세션의 원장은 버린다', async () => {
        localStorage.setItem(AWAY_KEY, JSON.stringify({
            startedAt: iso(START - 86_400_000), intervals: [{ from: iso(START - 86_000_000), to: iso(START - 85_000_000) }], pendingFrom: null,
        }));
        const w = await mountDash();
        expect(session(w)).toBe('10:00');
        expect(JSON.parse(localStorage.getItem(AWAY_KEY)!).startedAt).toBe(iso(START));
    });

    test('(w12) 회당 시간 판정도 멈춘 시간을 뺀 값을 본다 — 회당 15분, 15분 경과 중 5분 비움 → 남은 05:00(달성 아님)', async () => {
        const book = { id: 5, title: '헌법', author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0, sessionGoalSeconds: 900 };
        dash = { ...dash, study: { ...STUDY_ACTIVE, activeBook: book, books: [book], recentBookId: 5 } };
        const w = await mountDash();
        await leaveFor(300_000);
        expect(w.find('.dash-session-line').classes()).not.toContain('is-reached');
        expect(w.find('.dash-session-line .dash-kv-v').text()).toBe('05:00');
        expect(document.title).not.toContain('[회당 시간 달성]');
    });

    test('(w11) 종료가 네트워크 실패면 원장을 지키고, 409면 지운다', async () => {
        stopMode = 'network';
        const w = await mountDash();
        await leaveFor(300_000);
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        await flushPromises();
        expect(JSON.parse(localStorage.getItem(AWAY_KEY)!).intervals).toHaveLength(1);

        stopMode = '409';
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(2));
        expect(sentIntervals(1)).toHaveLength(1);   // 재시도 때도 같은 구간을 보낸다
        await flushPromises();
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
    });

    // 서버 상한 200개(초과면 400). 구간은 측정 범위 안이고 서로 다르다 — 가장 최근 200개가 가야 한다.
    const manyIntervals = (n: number) => Array.from({ length: n }, (_, i) =>
        ({ from: iso(START + i * 2_000), to: iso(START + i * 2_000 + 1_000) }));

    test('(w15) 원장이 201개면 가장 최근 200개만 보낸다', async () => {
        const ivs = manyIntervals(201);
        localStorage.setItem(AWAY_KEY, JSON.stringify({ startedAt: iso(START), intervals: ivs, pendingFrom: null }));
        const w = await mountDash();
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toHaveLength(200);
        expect(sentIntervals()).toEqual(ivs.slice(1));
    });

    test('(w16) 구간을 실은 stop이 400이면 원장을 비우고 본문 없이 1회 재시도해 측정을 끝낸다', async () => {
        stopQueue.push(400, 200);
        const w = await mountDash();
        await leaveFor(300_000);
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(2));
        await flushPromises();

        expect(sentIntervals(0)).toHaveLength(1);
        expect(stops[1].body).toBeUndefined();
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
        await vi.waitFor(() => expect(w.find('[data-testid="focus-bar"]').exists()).toBe(false));
        expect(w.find('.alert-error').exists()).toBe(false);
    });

    test('(w17) 구간 없이 400이면 재시도하지 않는다(무한 재시도 방지 대조군)', async () => {
        stopQueue.push(400);
        const w = await mountDash();
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(w.find('.alert-error').exists()).toBe(true));
        expect(stops).toHaveLength(1);
    });
});

// 자리 비움 선택(설계 §9) — 기기별 · 기본 꺼짐. 이 describe는 위 3-a의 켬 전제를 되돌려 「고른 적 없음」에서 출발한다.
//  · 통과가 확정하는 것: 끈(안 고른) 사람은 기능 이전과 같다(15:00 · 원장 0 · 본문 []) · 체크가 저장되고 곧바로 추적이 켜진다 ·
//    다른 탭의 변경을 따른다 · 측정 중 꺼지면 이번 측정 구간을 버린다.
//  · 실패가 배제하는 것: watch 조건에서 선택 누락(끈 사람도 추적) · 터치 기기에 체크박스 · storage 이벤트 무시.
const pickIdleStudy = () => {
    localStorage.setItem('booktimer.timerMode', 'study');
    dash = { ...dash, study: STUDY_IDLE };
};
const prefBox = (w: ReturnType<typeof mount>) => w.find('.dash-away-pref input[type="checkbox"]');
const otherTabSets = (v: 'on' | 'off') => {
    localStorage.setItem(AWAY_PREF_KEY, v);
    window.dispatchEvent(new StorageEvent('storage', { key: AWAY_PREF_KEY, newValue: v }));
};

describe('DashboardApp — 자리 비움 선택(기기별 · 기본 꺼짐)', () => {
    beforeEach(() => { localStorage.removeItem(AWAY_PREF_KEY); });

    test.each([null, 'off'])('REQ-03 · 고른 적 없으면(저장값 %j) 5분 떠나도 안 멈추고 종료 본문은 빈 목록이다', async v => {
        if (v) localStorage.setItem(AWAY_PREF_KEY, v);
        const w = await mountDash();
        await leaveFor(300_000);
        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('15:00');
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toEqual([]);
    });

    test('REQ-01 · 컴퓨터 대기 화면엔 체크박스가 있고, 터치 기기엔 저장값이 켬이어도 없다', async () => {
        pickIdleStudy();
        const desk = await mountDash();
        expect(prefBox(desk).exists()).toBe(true);
        desk.unmount(); mounted.splice(mounted.indexOf(desk), 1);

        localStorage.setItem(AWAY_PREF_KEY, 'on');
        vi.stubGlobal('matchMedia', () => ({ matches: false }));
        const touch = await mountDash();
        expect(touch.find('.dash-away-pref').exists()).toBe(false);
    });

    test("REQ-02 · 대기에서 체크하면 'on'이 저장되고, 측정을 시작해 5분 떠났다 오면 멈춘다", async () => {
        pickIdleStudy();
        const w = await mountDash();
        expect((prefBox(w).element as HTMLInputElement).checked).toBe(false);
        await prefBox(w).setValue(true);
        expect(localStorage.getItem(AWAY_PREF_KEY)).toBe('on');

        await btnWith(w, '공부 측정 시작').trigger('click');
        await vi.waitFor(() => expect(w.find('[data-testid="focus-bar"]').exists()).toBe(true));
        await leaveFor(300_000);
        expect(notice(w).text()).toContain('5분 자리를 비워');
    });

    test('REQ-05 · 다른 탭에서 켜면(storage 이벤트) 이 탭 체크 표시도 켜진다', async () => {
        pickIdleStudy();
        const w = await mountDash();
        expect((prefBox(w).element as HTMLInputElement).checked).toBe(false);
        otherTabSets('on');
        await flushPromises();
        expect((prefBox(w).element as HTMLInputElement).checked).toBe(true);
    });

    test('REQ-05 · 측정 중 다른 탭에서 끄면 이번 측정의 멈춘 구간을 버린다 — 15:00 · 안내 없음 · 원장 없음 · 종료 본문 []', async () => {
        localStorage.setItem(AWAY_PREF_KEY, 'on');
        const w = await mountDash();
        await leaveFor(300_000);
        expect(notice(w).exists()).toBe(true);   // 양성 대조 — 켠 동안은 멈췄다

        otherTabSets('off');
        await flushPromises();
        expect(notice(w).exists()).toBe(false);
        expect(session(w)).toBe('15:00');
        expect(localStorage.getItem(AWAY_KEY)).toBeNull();
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toEqual([]);
    });
});

// 화면 켜 두기(설계 §9.4-7) — 켠 사람 · 측정 중 · 보이는 동안만 Screen Wake Lock.
// 잡힌 잠금은 release()로 풀리며 'release' 리스너를 부른다(브라우저가 hidden에서 푸는 것과 같은 신호).
//  · 통과가 확정하는 것: 조건이 맞으면 'screen' 1회 · 떠나면 풀고 돌아오면 다시 · 종료·꺼짐·언마운트면 푼다 · 거절돼도 추적 유지.
//  · 실패가 배제하는 것: 끈 사람에게 화면 잡기 · 측정 뒤 누수(모니터가 영영 안 꺼짐) · 거절 rejection으로 추적 중단.
// 미지원 경로(navigator.wakeLock 없음)는 jsdom 기본이라 위의 기존 계측기가 잰다.
function installWakeLock(opts: { reject?: boolean } = {}) {
    const locks: { released: boolean; release: () => Promise<void> }[] = [];
    const request = vi.fn(async (_type: string) => {
        if (opts.reject) throw new DOMException('denied', 'NotAllowedError');
        const listeners: (() => void)[] = [];
        const lock = {
            released: false,
            addEventListener: (_: string, f: () => void) => { listeners.push(f); },
            release: vi.fn(async () => { if (!lock.released) { lock.released = true; listeners.forEach(f => f()); } }),
        };
        locks.push(lock);
        return lock;
    });
    Object.defineProperty(navigator, 'wakeLock', { configurable: true, value: { request } });
    return { request, locks };
}

describe('DashboardApp — 화면 켜 두기', () => {
    // 반드시 지운다 — 남으면 다른 테스트의 「미지원」 경로가 오염된다.
    afterEach(() => { delete (navigator as { wakeLock?: unknown }).wakeLock; });

    test("REQ-06 · 켬·측정 중·보이면 화면 잠금을 한 번 잡는다('screen')", async () => {
        const { request, locks } = installWakeLock();
        await mountDash();
        await flushPromises();
        expect(request).toHaveBeenCalledTimes(1);
        expect(request).toHaveBeenCalledWith('screen');
        expect(locks[0].released).toBe(false);
    });

    test('REQ-06 · 끔이면 잡지 않는다', async () => {
        localStorage.setItem(AWAY_PREF_KEY, 'off');
        const { request } = installWakeLock();
        await mountDash();
        await flushPromises();
        expect(request).not.toHaveBeenCalled();
    });

    test('REQ-06 · 떠나면 풀고 돌아오면 다시 잡는다 — 첫 잠금 released, request 2회', async () => {
        const { request, locks } = installWakeLock();
        await mountDash();
        await flushPromises();
        setVisible('hidden');
        await flushPromises();
        expect(locks[0].released).toBe(true);
        vi.advanceTimersByTime(40_000);
        setVisible('visible');
        await flushPromises();
        expect(request).toHaveBeenCalledTimes(2);
        expect(locks[1].released).toBe(false);
    });

    test('REQ-06 · 측정을 끝내면 잡은 잠금을 푼다', async () => {
        const { locks } = installWakeLock();
        const w = await mountDash();
        await flushPromises();
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        await flushPromises();
        expect(locks[0].released).toBe(true);
    });

    test('REQ-06 · 측정 중 다른 탭에서 끄면 푼다', async () => {
        const { locks } = installWakeLock();
        await mountDash();
        await flushPromises();
        otherTabSets('off');
        await flushPromises();
        expect(locks[0].released).toBe(true);
    });

    test('REQ-06 · 화면을 떠나면(언마운트) 푼다', async () => {
        const { locks } = installWakeLock();
        const w = await mountDash();
        await flushPromises();
        w.unmount(); mounted.splice(mounted.indexOf(w), 1);
        await flushPromises();
        expect(locks[0].released).toBe(true);
    });

    test('REQ-07 · 잡기가 거절돼도 5분 떠났다 오면 멈춤 안내가 뜨고 종료 본문에 구간이 실린다', async () => {
        const { request } = installWakeLock({ reject: true });
        const w = await mountDash();
        await flushPromises();
        await leaveFor(300_000);
        expect(request).toHaveBeenCalled();
        expect(notice(w).text()).toContain('5분 자리를 비워');
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toHaveLength(1);
    });
});

// 리뷰 보강(§9 2차) — 추적 탭 없이 끈 선택 · 다른 탭의 clear · OS가 푼 화면 잠금.
//  · 통과가 확정하는 것: 끄는 탭이 원장도 지운다(다시 켜도 옛 구간이 안 살아난다) · clear(key null)를 따른다 ·
//    보이는 채로 풀린 잠금을 다음 기회에 다시 잡는다.
//  · 실패가 배제하는 것: 버렸어야 할 구간의 부활·전송 · key null 분기 삭제 · release 리스너 삭제(풀린 객체를 붙든 채 영영 안 잡음).
describe('DashboardApp — 자리 비움 선택 · 리뷰 보강', () => {
    afterEach(() => { delete (navigator as { wakeLock?: unknown }).wakeLock; });

    test('Minor-1 · 추적 탭 없이 끈 뒤 다시 켜도 옛 멈춘 구간이 되살아나지 않는다 — 15:00 · 종료 본문 []', async () => {
        // ① 켬 · 5분 이탈 · 탭 닫음 — 원장에 구간 1개가 남는다
        const a = await mountDash();
        await leaveFor(300_000);
        expect(notice(a).exists()).toBe(true);
        a.unmount(); mounted.splice(mounted.indexOf(a), 1);
        expect(awayLedger().intervals).toHaveLength(1);

        // ② 아직 대기로 보이는 낡은 탭에서 끔(이 탭은 추적 중이 아니다)
        localStorage.setItem('booktimer.timerMode', 'study');
        dash = { ...dash, study: STUDY_IDLE };
        const b = await mountDash();
        await prefBox(b).setValue(false);
        expect(localStorage.getItem(AWAY_PREF_KEY)).toBe('off');
        b.unmount(); mounted.splice(mounted.indexOf(b), 1);

        // ③ 새 탭(끔) → 다른 탭에서 켬 — 그 순간부터 세야 한다
        dash = { ...dash, study: STUDY_ACTIVE };
        const c = await mountDash();
        otherTabSets('on');
        await flushPromises();
        expect(session(c)).toBe('15:00');
        await btnWith(c, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toEqual([]);
    });

    test('Minor-3 · 다른 탭에서 저장소를 비우면(storage key null) 체크가 풀린다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dash = { ...dash, study: STUDY_IDLE };
        const w = await mountDash();
        expect((prefBox(w).element as HTMLInputElement).checked).toBe(true);
        localStorage.clear();
        window.dispatchEvent(new StorageEvent('storage', { key: null }));
        await flushPromises();
        expect((prefBox(w).element as HTMLInputElement).checked).toBe(false);
    });

    test('실 브라우저 결함 · 시작 응답(100ns)과 복귀 재조회(µs)의 표기가 달라도 첫 이탈 구간·안내가 지워지지 않는다', async () => {
        localStorage.setItem('booktimer.timerMode', 'study');
        dash = { ...dash, study: STUDY_IDLE };
        const w = await mountDash();
        await btnWith(w, '공부 측정 시작').trigger('click');
        await vi.waitFor(() => expect(w.find('[data-testid="focus-bar"]').exists()).toBe(true));
        const dashCalls = () => (fetch as unknown as { mock: { calls: unknown[][] } }).mock.calls
            .filter(c => String(c[0]).includes('/api/dashboard')).length;
        const before = dashCalls();

        await leaveFor(300_000);
        await flushPromises();
        expect(dashCalls()).toBe(before + 1);   // 조건 성립 확인 — 복귀 재조회가 실제로 µs 표기를 얹었다

        expect(notice(w).text()).toContain('5분 자리를 비워');
        expect(awayLedger().intervals).toHaveLength(1);
        expect(session(w)).toBe('00:00');
        await btnWith(w, '측정 종료').trigger('click');
        await vi.waitFor(() => expect(stops).toHaveLength(1));
        expect(sentIntervals()).toHaveLength(1);
    });

    test('Minor-3 · 보이는 채로 OS가 화면 잠금을 풀면(release 이벤트) 다음 visible에서 다시 잡는다', async () => {
        const { request, locks } = installWakeLock();
        await mountDash();
        await flushPromises();
        expect(request).toHaveBeenCalledTimes(1);
        await locks[0].release();   // 절전 모드 등 — 탭은 여전히 visible
        await flushPromises();
        setVisible('visible');
        await flushPromises();
        expect(request).toHaveBeenCalledTimes(2);
    });
});
