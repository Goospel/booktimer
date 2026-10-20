// @vitest-environment jsdom
// StudyTimerCard — 측정 시작·종료 단축키 Alt+P(설계 2026-10-20-study-timer-start-stop-shortcut §7 Task 0·1).
//
// 계측기 메모
//  · 이 파일은 afterEach에서 카드를 전부 언마운트한다 — study-timer-card.test.ts는 언마운트하지 않아 앞 카드의 창 리스너가
//    남고, 그러면 「조합을 삼킨다(defaultPrevented)」 단언이 남의 리스너로 공허하게 통과한다.
//  · jsdom은 글자 입력을 재현하지 못한다 — 「본문에 π·ㅔ가 안 들어갔다」는 여기서 단언하지 않는다(U1·U2).
import { describe, test, expect, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import StudyTimerCard from '../src/dashboard/StudyTimerCard.vue';

const mounted: { unmount: () => void }[] = [];
afterEach(() => { mounted.splice(0).forEach(w => w.unmount()); document.body.innerHTML = ''; });

function mountCard(props: Record<string, unknown>) {
    const w = mount(StudyTimerCard, {
        props: { todaySeconds: 0, hasActiveSession: false, activeStartedAt: null, ...props },
        attachTo: document.body,
    });
    mounted.push(w);
    return w;
}
const iso = (ms: number) => new Date(ms).toISOString();

describe('StudyTimerCard — 측정 시작·종료 단축키', () => {
    test('REQ-01 · 측정 중 막대엔 「일시정지」·「다시 시작」이 없고 중단 수단은 「측정 종료」 하나다', () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000) });
        const labels = w.findAll('.focus-actions button').map(b => b.text());
        expect(labels.some(t => t.includes('측정 종료'))).toBe(true);   // 양성 대조 — 막대를 실제로 읽고 있다
        expect(labels.some(t => t.includes('일시정지') || t.includes('다시 시작'))).toBe(false);
        expect(w.find('[data-testid="focus-pause"]').exists()).toBe(false);
    });
});

const altP = (o: KeyboardEventInit = {}, target: EventTarget = window) => {
    const e = new KeyboardEvent('keydown', { code: 'KeyP', key: 'p', altKey: true, bubbles: true, cancelable: true, ...o });
    target.dispatchEvent(e);
    return e;
};
const BOOK = (id: number, title: string) => ({
    id, title, author: null, coverUrl: null, isbn13: null, readCount: 0, purchaseLink: null, totalSeconds: 0, sessionGoalSeconds: null,
});
const HEONBEOP = BOOK(5, '헌법');
const HYEONGBEOP = BOOK(6, '형법');
const MINBEOP = BOOK(7, '민법');

describe('StudyTimerCard — Alt+P가 버튼과 같은 emit을 낸다', () => {
    test.each([
        ['고른 책', { books: [HEONBEOP, HYEONGBEOP, MINBEOP], recentBookId: 6, pickedBook: MINBEOP }, 7],
        ['최근 책', { books: [HEONBEOP, HYEONGBEOP, MINBEOP], recentBookId: 6 }, 6],
        ['첫 책', { books: [HEONBEOP, HYEONGBEOP] }, 5],
        ['서재가 빔', { books: [] }, null],
    ])('REQ-03 · 대기 중 Alt+P는 칩의 책으로 start를 낸다 — 고른 책 > 최근 책 > 첫 책, 서재가 비면 null: %s', (_, props, id) => {
        const w = mountCard(props);
        const e = altP();
        expect(w.emitted('start')).toEqual([[id]]);
        expect(w.emitted('stop')).toBeUndefined();
        expect(e.defaultPrevented).toBe(true);
    });

    test('REQ-04 · 측정 중 Alt+P는 stop을 낸다(start는 안 낸다)', () => {
        const w = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now() - 60_000), books: [HEONBEOP] });
        altP();
        expect(w.emitted('stop')).toHaveLength(1);
        expect(w.emitted('start')).toBeUndefined();
    });

    test('REQ-05 · 키 반복·시작/종료 왕복 중·잠금이면 내지 않지만 조합은 삼킨다 — 조합 중(229)이면 내고 다른 조합은 안 건드린다', async () => {
        const w = mountCard({ books: [HEONBEOP] });
        expect(altP({ repeat: true }).defaultPrevented).toBe(true);
        await w.setProps({ starting: true });
        expect(altP().defaultPrevented).toBe(true);
        await w.setProps({ starting: false, shortcutLocked: true });
        expect(altP().defaultPrevented).toBe(true);
        expect(w.emitted('start')).toBeUndefined();

        await w.setProps({ hasActiveSession: true, activeStartedAt: iso(Date.now()), shortcutLocked: false, stopping: true });
        expect(altP().defaultPrevented).toBe(true);
        expect(w.emitted('stop')).toBeUndefined();

        // 양성 대조 — 가드가 풀리면 조합 중(229)에도 낸다
        await w.setProps({ stopping: false });
        altP({ key: 'Process', keyCode: 229, isComposing: true } as KeyboardEventInit);
        expect(w.emitted('stop')).toHaveLength(1);
        // 다른 조합은 건드리지 않는다
        expect(altP({ shiftKey: true }).defaultPrevented).toBe(false);
        expect(altP({ code: 'KeyO' }).defaultPrevented).toBe(false);
        expect(w.emitted('stop')).toHaveLength(1);
    });

    test('REQ-05 · 편집 칸에 닿기 전에 삼킨다 — 다른 조합은 그대로 닿는다(양성 대조)', () => {
        mountCard({ books: [HEONBEOP] });
        const field = document.createElement('div');
        document.body.appendChild(field);
        const seen: string[] = [];
        field.addEventListener('keydown', e => seen.push(`${e.code}${e.shiftKey ? '+S' : ''}`));
        altP({}, field);
        altP({ shiftKey: true }, field);
        expect(seen).toEqual(['KeyP+S']);
    });

    test('REQ-05 · 언마운트하면 더는 듣지 않는다', () => {
        const w = mountCard({ books: [HEONBEOP] });
        expect(altP().defaultPrevented).toBe(true);   // 양성 대조 — 붙어 있을 땐 삼킨다
        w.unmount();
        mounted.splice(mounted.indexOf(w), 1);
        expect(altP().defaultPrevented).toBe(false);
    });

    test('REQ-07 · 시작(두 자리)·종료 버튼에 aria-keyshortcuts="Alt+P"·힌트 Alt+P(aria-hidden)가 있고 「책 없이 시작」엔 없다, 이모지 0', async () => {
        const check = (b: ReturnType<ReturnType<typeof mountCard>['find']>) => {
            expect(b.attributes('aria-keyshortcuts')).toBe('Alt+P');
            const kbd = b.find('kbd');
            expect(kbd.text()).toBe('Alt+P');
            expect(kbd.attributes('aria-hidden')).toBe('true');
        };
        const btn = (w: ReturnType<typeof mountCard>, t: string) => w.findAll('button').find(b => b.text().includes(t))!;
        const emoji = /\p{Extended_Pictographic}/u;

        const withBook = mountCard({ books: [HEONBEOP] });
        check(btn(withBook, '공부 측정 시작'));
        const bookless = btn(withBook, '책 없이 시작');
        expect(bookless.attributes('aria-keyshortcuts')).toBeUndefined();
        expect(bookless.find('kbd').exists()).toBe(false);
        expect(emoji.test(withBook.text())).toBe(false);

        const empty = mountCard({ books: [] });
        check(btn(empty, '공부 측정 시작'));

        const active = mountCard({ hasActiveSession: true, activeStartedAt: iso(Date.now()) });
        check(btn(active, '측정 종료'));
        expect(emoji.test(active.text())).toBe(false);
    });
});
