// @vitest-environment jsdom
// StudyTimerCard — 공부 히어로(카운트업 + 세션 경과 + 시작/종료 + 목표 게이지·인라인 편집).
// 책 선택·태깅은 아직 없다(2단계 PR-C 몫 — 부재가 규칙, 억제 코드 0줄).
import { describe, test, expect, vi, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
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

// ── 목표 게이지 · 히어로 인라인 편집 (2단계 PR-B) ───────────────────────────────
//
// 계측기 메모 — 값이 틀려도 화면은 그럴듯한 자리라 「계산된 수·emit 인자」로만 잰다.
//  · 통과가 확정하는 것: 게이지 폭이 (오늘/목표)에서 나온다 · 측정 중 경과가 그 분자에 들어간다 ·
//    goalSeconds 기본값이 0이라 「목표 없음」이 기본이다 · 저장 emit이 분→초로 나간다 ·
//    「목표 없이 지내기」가 목표 있을 때만 뜬다.
//  · 실패가 배제하는 것: goalSeconds 기본값 뒤집힘(0 아닌 값) · studyProgress 인자 순서 뒤바뀜 ·
//    분 그대로 보내기(90 → 90) · showClearGoal 조건 소실.
const btn = (w: ReturnType<typeof mountCard>, text: string) =>
    w.findAll('button').find(b => b.text().includes(text));

describe('StudyTimerCard — 목표 게이지', () => {
    test('목표 1시간 · 오늘 30분 → 게이지 폭 50%, 라벨과 남은 시간이 함께 맞는다', () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 1800, goalSeconds: 3600 });

        expect(w.find('.dash-progress-fill').attributes('style')).toContain('width: 50%');
        expect(w.find('.dash-progress-meta').text()).toContain('하루 목표 1시간');
        expect(w.find('.dash-progress-meta').text()).toContain('목표까지 30:00');
        expect(w.find('.dash-progress-meta').text()).not.toContain('목표 달성');
    });

    test('측정 중 경과가 게이지에 들어간다 — 완료 30분 + 진행 30분 = 목표 달성', () => {
        vi.useFakeTimers();
        const w = mountCard({
            todaySeconds: 1800, goalSeconds: 3600,
            hasActiveSession: true, activeStartedAt: new Date(Date.now() - 1_800_000).toISOString(),
        });

        expect(w.find('.dash-progress-fill').attributes('style')).toContain('width: 100%');
        expect(w.find('.dash-progress-meta').text()).toContain('목표 달성');
        // 달성해도 측정 흐름은 그대로 — 종료 버튼이 살아 있다(독서와 같은 결정, E19).
        expect(btn(w, '측정 종료')).toBeDefined();
    });

    test('goalSeconds 기본값(미지정)은 0 = 목표 없음 — 게이지 대신 「하루 목표 정하기」', () => {
        // 기본값 양성 대조군: 이 단언이 죽으면 새 prop 기본값이 뒤집힌 것이다.
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 1800 });

        expect(w.find('.dash-progress-track').exists()).toBe(false);
        expect(btn(w, '하루 목표 정하기')).toBeDefined();
    });
});

describe('StudyTimerCard — 목표 인라인 편집', () => {
    test('목표 0 → 「하루 목표 정하기」로 폼이 열리고, 지울 목표가 없어 「목표 없이 지내기」는 없다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0, goalSeconds: 0 });
        expect(w.find('form.dash-goal-edit').exists()).toBe(false);

        await btn(w, '하루 목표 정하기')!.trigger('click');

        expect(w.find('form.dash-goal-edit').exists()).toBe(true);
        expect((w.find('form.dash-goal-edit input').element as HTMLInputElement).value).toBe('0');
        expect(btn(w, '목표 없이 지내기')).toBeUndefined();
    });

    test('목표 1시간 → 「변경」이 현재값(분)을 채우고, 90분 저장이 5400초로 나간다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0, goalSeconds: 3600 });

        await btn(w, '변경')!.trigger('click');
        const input = w.find('form.dash-goal-edit input');
        expect((input.element as HTMLInputElement).value).toBe('60');

        await input.setValue(90);
        await w.find('form.dash-goal-edit').trigger('submit');

        expect(w.emitted('setGoal')).toEqual([[5400]]);
        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
    });

    test('목표가 있을 때만 「목표 없이 지내기」 — 누르면 0초를 보낸다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0, goalSeconds: 3600 });
        await btn(w, '변경')!.trigger('click');

        await btn(w, '목표 없이 지내기')!.trigger('click');

        expect(w.emitted('setGoal')).toEqual([[0]]);
        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
    });

    test('저장 중이면 저장 버튼이 비활성 + 「저장하는 중…」', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0, goalSeconds: 3600, savingGoal: true });
        await btn(w, '변경')!.trigger('click');

        const save = btn(w, '저장하는 중')!;
        expect(save.attributes('disabled')).toBeDefined();
    });

    // 실브라우저에서 잡은 결함의 회귀 가드(2026-09-05): step="5"였을 때 7·23처럼 5의 배수가 아닌 분은
    // 네이티브 제약검증의 stepMismatch가 되어 **submit이 조용히 안 나갔다**. vitest의 trigger('submit')는
    // 제약검증을 건너뛰므로 폼 이벤트만으로는 영영 못 잡는다 — 그래서 validity를 직접 잰다.
    test('5의 배수가 아닌 분도 유효하다 — 브라우저가 submit을 막지 않는다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0, goalSeconds: 3600 });
        await btn(w, '변경')!.trigger('click');
        const input = w.find('form.dash-goal-edit input').element as HTMLInputElement;

        for (const v of ['7', '23', '45']) {
            input.value = v;
            expect(input.validity.stepMismatch, `${v}분이 stepMismatch`).toBe(false);
            expect(input.checkValidity(), `${v}분이 무효`).toBe(true);
        }
        // 양성 대조: 음수는 여전히 막힌다(min="0") — 「전부 유효」 구현과 구분된다.
        input.value = '-1';
        expect(input.checkValidity()).toBe(false);
    });

    test('취소는 폼만 닫고 아무것도 보내지 않는다', async () => {
        vi.useFakeTimers();
        const w = mountCard({ todaySeconds: 0, goalSeconds: 3600 });
        await btn(w, '변경')!.trigger('click');

        await btn(w, '취소')!.trigger('click');

        expect(w.find('form.dash-goal-edit').exists()).toBe(false);
        expect(w.emitted('setGoal')).toBeUndefined();
    });
});
