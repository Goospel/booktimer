// @vitest-environment jsdom
// StudyTimerCard — 공부 히어로(카운트업 + 세션 경과 + 시작/종료 + 책 칩 + 책별 회당 시간).
// 하루 목표 게이지·인라인 편집은 2026-09-13 컨셉 전환으로 걷었다(회당 시간 테스트는 파일 끝).
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import StudyTimerCard from '../src/dashboard/StudyTimerCard.vue';

afterEach(() => { vi.useRealTimers(); document.body.innerHTML = ''; });

function mountCard(props: Record<string, unknown>) {
    return mount(StudyTimerCard, {
        props: { todaySeconds: 0, hasActiveSession: false, activeStartedAt: null, ...props },
        attachTo: document.body,
    });
}

describe('StudyTimerCard — idle', () => {
    test('오늘 공부한 시간을 세고, 시작 버튼만 있다(책 칩·바꾸기·종료 없음)', () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 3600 });

        expect(w.find('.dash-timer-hero').classes()).toContain('is-study');
        expect(w.find('.dash-timer-num').text()).toBe('01:00:00');
        const texts = w.findAll('button').map(b => b.text());
        expect(texts.some(t => t.includes('공부 측정 시작'))).toBe(true);
        expect(texts.some(t => t.includes('측정 종료'))).toBe(false);
        expect(texts.some(t => t.includes('바꾸기'))).toBe(false);
        expect(w.find('.dash-book-chip').exists()).toBe(false);
    });

    test('시작 버튼 클릭 → start emit, starting이면 비활성 + "시작하는 중…"', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0 });
        await w.findAll('button').find(b => b.text().includes('공부 측정 시작'))!.trigger('click');
        expect(w.emitted('start')).toHaveLength(1);

        await w.setProps({ starting: true });
        const btn = w.findAll('button').find(b => b.text().includes('시작하는 중'))!;
        expect(btn.attributes('disabled')).toBeDefined();
    });
});

describe('StudyTimerCard — 측정 중', () => {
    test('세션 경과와 오늘 누적(완료 합 + 경과)을 동시에 보여주고, 종료를 emit한다', async () => {
        vi.useFakeTimers();
        const startedAt = new Date(Date.now() - 65_000).toISOString();
        const w = mountCard({ todaySeconds: 3600, hasActiveSession: true, activeStartedAt: startedAt });

        expect(w.find('.dash-session-time').text()).toBe('01:05');
        expect(w.find('.dash-timer-num').text()).toBe('01:01:05');

        await w.findAll('button').find(b => b.text().includes('측정 종료'))!.trigger('click');
        expect(w.emitted('stop')).toHaveLength(1);
    });
});

describe('StudyTimerCard — 표현 규칙', () => {
    test('기본 이모지를 쓰지 않는다(독서 카드의 🌿·🌱 문법을 물려받지 않는다)', () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 120, hasActiveSession: true, activeStartedAt: new Date().toISOString() });
        expect(w.text()).not.toMatch(/[\u{1F300}-\u{1FAFF}]/u);
    });
});

// ── 책 걸기 — idle 기본 칩 · 측정 중 책 (2단계 PR-C) ─────────────────────────────
//
// 계측기 메모
//  · 통과가 확정하는 것: 기본 책이 recentBookId → 없으면 첫 책 순으로 골라진다 · 시작 emit에
//    **그 책의 id**가 실린다(「책 없이」는 null) · 측정 중 kv가 activeBook을 보여준다.
//  · 실패가 배제하는 것: books/recentBookId 기본값 뒤집힘 · 시작이 언제나 null로 나가기(칩만 장식) ·
//    첫 책 폴백 소실 · activeBook null을 「책 없이」가 아니라 빈칸으로 그리기.
const btn = (w: ReturnType<typeof mountCard>, text: string) =>
    w.findAll('button').find(b => b.text().includes(text));
const STUDY_BOOK = (id: number, title: string, sessionGoalSeconds: number | null = null) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0,
    sessionGoalSeconds,
});

describe('StudyTimerCard — idle 책 칩', () => {
    test('recentBookId의 책이 칩에 뜨고, 시작이 그 책 id로 나간다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법'), STUDY_BOOK(6, '형법')], recentBookId: 6 });

        expect(w.find('.dash-book-chip-title').text()).toBe('형법');
        await btn(w, '공부 측정 시작')!.trigger('click');
        expect(w.emitted('start')).toEqual([[6]]);
    });

    test('recentBookId가 없는 id면 첫 책으로 떨어진다(폴백 양성 대조군)', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법'), STUDY_BOOK(6, '형법')], recentBookId: 99 });

        expect(w.find('.dash-book-chip-title').text()).toBe('헌법');
        await btn(w, '공부 측정 시작')!.trigger('click');
        expect(w.emitted('start')).toEqual([[5]]);
    });

    test('「책 없이 시작」은 null을, 「바꾸기」는 openSheet를 낸다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법')], recentBookId: 5 });

        await btn(w, '책 없이 시작')!.trigger('click');
        expect(w.emitted('start')).toEqual([[null]]);

        await btn(w, '바꾸기')!.trigger('click');
        expect(w.emitted('openSheet')).toHaveLength(1);
    });

    test('books 기본값(미지정)은 빈 서재 — 칩 대신 공부 서재로 가는 링크', () => {
        // 기본값 양성 대조군: books 기본값이 뒤집히면(예: 픽스처 주입) 이 링크가 사라진다.
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0 });

        expect(w.find('.dash-book-chip').exists()).toBe(false);
        expect(w.find('a[href="/study/books"]').exists()).toBe(true);
    });
});

describe('StudyTimerCard — 측정 중 책', () => {
    test('지금 공부하는 책을 kv로 보여주고, 「책 바꾸기」를 emit한다', async () => {
        vi.useFakeTimers();
        const w = mountCard({
            hasActiveSession: true, activeStartedAt: new Date().toISOString(),
            activeBook: STUDY_BOOK(5, '헌법'),
        });

        expect(w.find('.dash-kv-k').text()).toBe('지금 공부하는 책');
        expect(w.find('.dash-kv-v').text()).toBe('헌법');

        await btn(w, '책 바꾸기')!.trigger('click');
        expect(w.emitted('changeBook')).toHaveLength(1);
    });

    test('책 없이 재는 중이면 「책 없이」 — 빈칸이 아니다', () => {
        vi.useFakeTimers();
        const w = mountCard({ hasActiveSession: true, activeStartedAt: new Date().toISOString() });

        expect(w.find('.dash-kv-v').text()).toBe('책 없이');
        expect(btn(w, '책 바꾸기')).toBeDefined();
    });

    test('idle엔 kv·「책 바꾸기」가 없다(측정 중 전용 — 음성 단언의 양성 쌍은 위 두 테스트)', () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법')], recentBookId: 5 });

        expect(w.find('.dash-kv').exists()).toBe(false);
        expect(btn(w, '책 바꾸기')).toBeUndefined();
    });
});

// 공부 칩의 표지 — StudyBookRow엔 coverUrl이 **타입에도 이미 있었는데** 화면이 안 썼다.
// 독서 칩과 같은 규칙이어야 한다(두 칩이 다르면 모드를 바꿀 때마다 표지가 나타났다 사라진다).
describe('StudyTimerCard — 책 칩 표지', () => {
    const row = (over: Record<string, unknown> = {}) => ({
        id: 7, title: '정보보안기사', author: null, coverUrl: null, isbn13: null,
        readCount: 0, purchaseLink: null, totalSeconds: 0, ...over,
    });

    test('표지가 있으면 실제 이미지를 그린다', () => {
        const w = mountCard({ books: [row({ coverUrl: 'https://img.example/sec.jpg' })], recentBookId: 7 });
        const img = w.find('img.dash-book-chip-cover');
        expect(img.exists()).toBe(true);
        expect(img.attributes('src')).toBe('https://img.example/sec.jpg');
        expect(img.attributes('referrerpolicy')).toBe('no-referrer');
    });

    test('표지가 없으면 색 박스가 그대로 (양성 대조군)', () => {
        const w = mountCard({ books: [row()], recentBookId: 7 });
        expect(w.find('img.dash-book-chip-cover').exists()).toBe(false);
        expect(w.find('span.dash-book-chip-cover').text()).toBe('정');
    });
});

// ── 책별 회당 시간 (2026-09-13 컨셉 전환 — 하루 목표 게이지를 대체) ─────────────────
//
// 계측기 메모 — 값이 틀려도 화면이 그럴듯한 자리라 「emit 인자·계산된 문구」로 잰다.
//  · 통과가 확정하는 것: 손잡이가 **칩에 선 그 책**의 값을 읽는다(첫 책·고른 책의 낡은 사본이 아니다) ·
//    저장 emit이 [그 책 id, 초]로 나간다 · 빈칸은 null(해제) · 측정 중엔 activeBook 기준 남은/초과 시간 ·
//    달성 동안만 탭 제목 접두가 붙고 종료·언마운트에 떨어진다.
//  · 실패가 배제하는 것: books[0] 고정 · 분 그대로 보내기 · 대상 id 누락 · 스톱워치인데 줄 그리기 ·
//    제목 원복 누락(탭에 「달성」이 눌어붙음).
const TITLE_PREFIX = '[회당 시간 달성] ';

describe('StudyTimerCard — 회당 시간 손잡이(idle)', () => {
    test('칩 책에 회당 50분 → 「회당 50분」 + 「변경」, 「정하기」는 없다', () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000), STUDY_BOOK(6, '형법')], recentBookId: 5 });

        expect(w.find('.dash-session-goal').text()).toContain('회당 50분');
        expect(btn(w, '변경')).toBeDefined();
        expect(btn(w, '회당 시간 정하기')).toBeUndefined();
    });

    test('칩 책(형법)에 회당 시간이 없으면 「회당 시간 정하기」 — 첫 책(헌법)의 값을 읽지 않는다', () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000), STUDY_BOOK(6, '형법')], recentBookId: 6 });

        expect(btn(w, '회당 시간 정하기')).toBeDefined();
        expect(w.text()).not.toContain('회당 50분');
    });

    test('시트에서 고른 책은 서버 최신 값으로 읽는다 — 저장 뒤 고른 책의 낡은 사본이 칩에 남지 않는다', () => {
        vi.useFakeTimers();
        const w = mountCard({
            books: [STUDY_BOOK(5, '헌법'), STUDY_BOOK(6, '형법', 2700)], recentBookId: 5,
            pickedBook: STUDY_BOOK(6, '형법'),   // 시트에서 고를 때 붙잡은 사본 — 회당 시간 null
        });

        expect(w.find('.dash-book-chip-title').text()).toBe('형법');
        expect(w.find('.dash-session-goal').text()).toContain('회당 45분');
    });

    test('서재 0권이면 설정 줄 자체가 없다', () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [] });

        expect(w.find('.dash-session-goal').exists()).toBe(false);
        expect(w.text()).not.toContain('회당');
    });

    test('하루 목표 흔적이 없다 — 게이지·「하루 목표 정하기」 0건(오늘 공부한 시간 숫자는 그대로)', () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 1800, books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });

        expect(w.find('.dash-progress-track').exists()).toBe(false);
        expect(w.text()).not.toContain('하루 목표');
        expect(w.find('.dash-timer-num').text()).toBe('30:00');
    });
});

describe('StudyTimerCard — 회당 시간 인라인 폼', () => {
    test('「변경」 → 50 채움 → 45 저장이 [5, 2700]으로 나가고, 폼은 부모가 닫을 때까지 열려 있다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });

        await btn(w, '변경')!.trigger('click');
        const input = w.find('form.dash-goal-edit input');
        expect((input.element as HTMLInputElement).value).toBe('50');

        await input.setValue(45);
        await w.find('form.dash-goal-edit').trigger('submit');

        expect(w.emitted('setSessionGoal')).toEqual([[5, 2700]]);
        expect(w.find('form.dash-goal-edit').exists()).toBe(true);

        // 부모가 왕복 중이면 저장이 잠기고, 성공을 알리면(closeEdit) 그때 닫힌다.
        await w.setProps({ savingSessionGoal: true });
        expect(btn(w, '저장하는 중')!.attributes('disabled')).toBeDefined();
        (w.vm as unknown as { closeEdit: () => void }).closeEdit();
        await w.vm.$nextTick();
        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
    });

    test('빈칸 저장은 [id, null] — 0초가 아니라 해제다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });
        await btn(w, '변경')!.trigger('click');

        await w.find('form.dash-goal-edit input').setValue('');
        await w.find('form.dash-goal-edit').trigger('submit');

        expect(w.emitted('setSessionGoal')).toEqual([[5, null]]);
    });

    test('「회당 시간 없이」는 값이 있을 때만 — 누르면 [id, null]', async () => {
        vi.useFakeTimers();
        const none = mountCard({ books: [STUDY_BOOK(6, '형법')], recentBookId: 6 });
        await btn(none, '회당 시간 정하기')!.trigger('click');
        expect(none.find('form.dash-goal-edit').exists()).toBe(true);
        expect(btn(none, '회당 시간 없이')).toBeUndefined();

        const set = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });
        await btn(set, '변경')!.trigger('click');
        await btn(set, '회당 시간 없이')!.trigger('click');
        expect(set.emitted('setSessionGoal')).toEqual([[5, null]]);
    });

    // 폼은 좌열(숫자 아래)에 서고 손잡이는 우측 칩 아래라, 좁은 폭에선 폼이 화면 밖일 수 있다 —
    // 입력칸에 포커스를 주면 브라우저가 스크롤해 오고 바로 타이핑할 수 있다.
    test('「변경」·「회당 시간 정하기」가 폼을 열면 포커스가 분 입력칸으로 간다', async () => {
        for (const [goal, label] of [[3000, '변경'], [null, '회당 시간 정하기']] as const) {
            const w = mountCard({ books: [STUDY_BOOK(5, '헌법', goal)], recentBookId: 5 });
            await btn(w, label)!.trigger('click');
            await flushPromises();

            expect(document.activeElement, label).toBe(w.find('form.dash-goal-edit input').element);
            w.unmount();
        }
    });

    test('취소는 폼만 닫고 아무것도 보내지 않는다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });
        await btn(w, '변경')!.trigger('click');

        await btn(w, '취소')!.trigger('click');

        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
        expect(w.emitted('setSessionGoal')).toBeUndefined();
    });

    test('폼이 열린 채 칩 책이 바뀌면 폼이 닫힌다 — 다른 책에 저장되지 않게', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000), STUDY_BOOK(6, '형법')], recentBookId: 5 });
        await btn(w, '변경')!.trigger('click');

        await w.setProps({ pickedBook: STUDY_BOOK(6, '형법') });

        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
    });

    // 입력 제약은 브라우저 네이티브 검증이 submit을 막는 자리다 — trigger('submit')는 그걸 건너뛰니 validity를 직접 잰다.
    test('10~360분 정수만 유효하다(min=10 · max=360 · step=1) — 17분처럼 5의 배수가 아닌 값도 통과', async () => {
        vi.useFakeTimers();
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });
        await btn(w, '변경')!.trigger('click');
        const input = w.find('form.dash-goal-edit input').element as HTMLInputElement;

        for (const v of ['10', '17', '360']) {
            input.value = v;
            expect(input.checkValidity(), `${v}분이 무효`).toBe(true);
        }
        for (const v of ['9', '1', '0']) {
            input.value = v;
            expect(input.validity.rangeUnderflow, `${v}분이 통과`).toBe(true);
        }
        input.value = '361';
        expect(input.validity.rangeOverflow).toBe(true);
        input.value = '17.5';
        expect(input.validity.stepMismatch).toBe(true);
    });
});

// 폼 위치 — 실브라우저 실측(2026-09-13): 폼이 좌열에 열려 넓은 화면에선 손잡이와 ≈650px 떨어지고,
// 좁은 화면에선 한 열로 쌓이며 폼이 손잡이 **위**에 끼어들어 방금 누른 손잡이를 113px 밀어냈다.
// 그래서 「손잡이 바로 뒤 형제」라는 구조로 잠근다(좌표는 jsdom이 못 잰다).
describe('StudyTimerCard — 회당 시간 폼 위치', () => {
    test('대기: 폼은 우측 패널 안, 칩 아래 손잡이 줄 바로 뒤에 열린다 — 좌열엔 없다', async () => {
        const w = mountCard({ books: [STUDY_BOOK(5, '헌법', 3000)], recentBookId: 5 });
        await btn(w, '변경')!.trigger('click');

        const form = w.find('form.dash-goal-edit');
        expect(form.exists()).toBe(true);
        expect(w.find('.dash-timer-right form.dash-goal-edit').exists()).toBe(true);
        expect(w.find('.dash-timer-left form.dash-goal-edit').exists()).toBe(false);
        expect(form.element.previousElementSibling).toBe(w.find('.dash-session-goal').element);
    });

    test('측정 중: 폼은 「회당 시간 변경」 버튼 바로 뒤에 열린다 — 좌열엔 없다', async () => {
        const w = mountCard({
            hasActiveSession: true, activeStartedAt: new Date(Date.now() - 600_000).toISOString(),
            books: [STUDY_BOOK(5, '헌법', 3000)], activeBook: STUDY_BOOK(5, '헌법', 3000),
        });
        const handle = btn(w, '회당 시간 변경')!;
        await handle.trigger('click');

        const form = w.find('form.dash-goal-edit');
        expect(form.exists()).toBe(true);
        expect(w.find('.dash-timer-left form.dash-goal-edit').exists()).toBe(false);
        expect(form.element.previousElementSibling).toBe(handle.element);
        // 손잡이는 폼을 여는 동안에도 제자리에 남는다(숨기면 그 자리가 빠지며 레이아웃이 튄다).
        expect(btn(w, '회당 시간 변경')).toBeDefined();
    });
});

describe('StudyTimerCard — 회당 시간(측정 중)', () => {
    const measuring = (minutesAgo: number, activeBook: unknown) => mountCard({
        hasActiveSession: true, activeStartedAt: new Date(Date.now() - minutesAgo * 60_000).toISOString(),
        books: [STUDY_BOOK(5, '헌법', 3000)], activeBook,
    });

    test('회당 50분 책을 30분째 → 「남은 시간 20:00 · 회당 50분」', () => {
        vi.useFakeTimers();
        const w = measuring(30, STUDY_BOOK(5, '헌법', 3000));

        const line = w.find('.dash-session-line').text();
        expect(line).toContain('남은 시간');
        expect(line).toContain('20:00');
        expect(line).toContain('회당 50분');
        expect(line).not.toContain('달성');
    });

    test('60분째 → 「회당 50분 달성 · +10:00」, 측정 종료는 그대로 살아 있다', () => {
        vi.useFakeTimers();
        const w = measuring(60, STUDY_BOOK(5, '헌법', 3000));

        const line = w.find('.dash-session-line').text();
        expect(line).toContain('회당 50분 달성');
        expect(line).toContain('+10:00');
        expect(btn(w, '측정 종료')).toBeDefined();
    });

    test('회당 시간 없는 책 · 책 없이 → 스톱워치(줄 0건)', () => {
        vi.useFakeTimers();
        expect(measuring(60, STUDY_BOOK(6, '형법')).find('.dash-session-line').exists()).toBe(false);
        expect(measuring(60, null).find('.dash-session-line').exists()).toBe(false);
    });

    test('「회당 시간 변경」은 activeBook을 겨눈다 — 책 없이면 링크가 없다', async () => {
        vi.useFakeTimers();
        const w = measuring(10, STUDY_BOOK(6, '형법'));
        await btn(w, '회당 시간 변경')!.trigger('click');
        await w.find('form.dash-goal-edit input').setValue(25);
        await w.find('form.dash-goal-edit').trigger('submit');
        expect(w.emitted('setSessionGoal')).toEqual([[6, 1500]]);

        expect(btn(measuring(10, null), '회당 시간 변경')).toBeUndefined();
    });
});

describe('StudyTimerCard — 탭 제목 알림', () => {
    afterEach(() => { document.title = '북타이머'; });

    test('달성 동안 접두가 붙고, 측정 종료에 떨어진다', async () => {
        vi.useFakeTimers();
        document.title = '북타이머';
        const w = mountCard({
            hasActiveSession: true, activeStartedAt: new Date(Date.now() - 49 * 60_000).toISOString(),
            activeBook: STUDY_BOOK(5, '헌법', 3000),
        });
        await w.vm.$nextTick();
        expect(document.title).toBe('북타이머');   // 아직(49분) — 음성 쌍

        await vi.advanceTimersByTimeAsync(61_000);   // 50분을 넘긴다
        expect(document.title).toBe(TITLE_PREFIX + '북타이머');

        await w.setProps({ hasActiveSession: false, activeStartedAt: null });
        expect(document.title).toBe('북타이머');
    });

    test('달성 상태로 떠난 화면(언마운트)은 제목을 되돌린다', async () => {
        vi.useFakeTimers();
        document.title = '북타이머';
        const w = mountCard({
            hasActiveSession: true, activeStartedAt: new Date(Date.now() - 60 * 60_000).toISOString(),
            activeBook: STUDY_BOOK(5, '헌법', 3000),
        });
        await vi.advanceTimersByTimeAsync(3_000);
        expect(document.title).toBe(TITLE_PREFIX + '북타이머');

        w.unmount();
        expect(document.title).toBe('북타이머');
    });
});
