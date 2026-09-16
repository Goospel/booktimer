// @vitest-environment jsdom
// 양옆 세로 바 동작 — 빌드를 안 타는 정적 ESM(`rail.js`)을 그대로 import 한다(theme.test.ts와 같은 꼴).
// 설계 claude-docs/plans/2026-09-15-web-side-rails.md §4-④ · §7 T-4.
// jsdom엔 matchMedia가 없어 가짜 win을 주입한다(입력 장치 판별 = matches).
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

import { bindRails, rememberMode, MODE_KEY, ENTER_DELAY_MS, LEAVE_DELAY_MS, HOVER_QUERY } from '../../src/main/resources/static/js/rail.js';

function fixture() {
    document.body.innerHTML = `
        <div id="side-rails" data-mode="reading">
            <aside class="rail rail-reading"><nav><a id="a1" href="#r1">홈</a></nav></aside>
            <aside class="rail rail-study"><nav><a id="a2" href="#r2">일정</a></nav></aside>
        </div>
        <button id="outside">바깥</button>`;
    const [reading, study] = Array.from(document.querySelectorAll<HTMLElement>('.rail'));
    return {
        reading,
        study,
        a1: document.getElementById('a1') as HTMLAnchorElement,
        a2: document.getElementById('a2') as HTMLAnchorElement,
        outside: document.getElementById('outside') as HTMLButtonElement,
    };
}

function fakeWin(matches: boolean) {
    return {
        matchMedia: (q: string) => {
            expect(q).toBe(HOVER_QUERY);
            return { matches, addEventListener() {} };
        },
        setTimeout: (fn: () => void, ms: number) => setTimeout(fn, ms),
        clearTimeout: (id: ReturnType<typeof setTimeout>) => clearTimeout(id),
    } as unknown as Window;
}

function click(el: Element): MouseEvent {
    const ev = new MouseEvent('click', { bubbles: true, cancelable: true });
    el.dispatchEvent(ev);
    return ev;
}

const openCount = () => document.querySelectorAll('.rail.is-open').length;

describe('bindRails — 터치(hover 없음): 탭으로 펼치고 바깥 탭으로 접는다', () => {
    test('접힌 바 링크 탭 → 이동을 막고 그 바만 펼친다', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        const ev = click(f.a1);
        expect(ev.defaultPrevented).toBe(true);
        expect(f.reading.classList.contains('is-open')).toBe(true);
        expect(f.study.classList.contains('is-open')).toBe(false);
    });

    test('펼친 바 링크 탭 → 이동한다(막지 않음)', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        click(f.a1);
        const ev = click(f.a1);
        expect(ev.defaultPrevented).toBe(false);
    });

    test('다른 바 탭 → 먼저 것은 접히고 새 것만 펼친다', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        click(f.a1);
        click(f.a2);
        expect(f.reading.classList.contains('is-open')).toBe(false);
        expect(f.study.classList.contains('is-open')).toBe(true);
    });

    test('바깥 탭 → 전부 접히고 바 안 포커스를 푼다(:focus-within이 펼침을 붙잡지 않게)', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        click(f.a1);
        f.a1.focus();
        expect(document.activeElement).toBe(f.a1);
        click(f.outside);
        expect(openCount()).toBe(0);
        expect(document.getElementById('side-rails')!.contains(document.activeElement)).toBe(false);
    });

    test('Escape → 전부 접힌다', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        click(f.a2);
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        expect(openCount()).toBe(0);
    });
});

describe('bindRails — 데스크톱(hover): 머물면 펼치고 벗어나면 접는다', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    test('링크 클릭은 막지 않고 펼치지도 않는다', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        const ev = click(f.a1);
        expect(ev.defaultPrevented).toBe(false);
        expect(openCount()).toBe(0);
    });

    test(`mouseenter → ${ENTER_DELAY_MS}ms 머물러야 펼친다, mouseleave → ${LEAVE_DELAY_MS}ms 뒤 접힌다`, () => {
        expect(ENTER_DELAY_MS).toBe(150);
        expect(LEAVE_DELAY_MS).toBe(100);
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.reading.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(149);
        expect(f.reading.classList.contains('is-open')).toBe(false);
        vi.advanceTimersByTime(1);
        expect(f.reading.classList.contains('is-open')).toBe(true);

        f.reading.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(99);
        expect(f.reading.classList.contains('is-open')).toBe(true);
        vi.advanceTimersByTime(1);
        expect(f.reading.classList.contains('is-open')).toBe(false);
    });

    test('스침(진입 60ms 뒤 이탈) → 이후 1초 동안 한 번도 안 펼친다', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.reading.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(60);
        f.reading.dispatchEvent(new MouseEvent('mouseleave'));
        for (let t = 0; t < 1000; t += 10) {
            vi.advanceTimersByTime(10);
            expect(openCount()).toBe(0);
        }
    });

    test('바 안 링크에 포커스가 남은 채 mouseleave → 접힐 때 포커스도 바 밖으로', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.reading.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(150);
        f.a1.focus();
        f.reading.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(100);
        expect(document.getElementById('side-rails')!.contains(document.activeElement)).toBe(false);
    });

    test('터치 기기의 흉내 mouseenter는 무시한다', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        f.reading.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1000);
        expect(openCount()).toBe(0);
    });
});

describe('bindRails — 리뷰 반영(#1137): 포커스·동시 펼침·입력 장치 전환·옛 Safari', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    test('키보드 포커스가 한 바에 있는 채 마우스가 다른 바에 머물러도 포커스를 빼앗지 않는다', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.a1.focus();
        f.study.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(150);
        expect(f.study.classList.contains('is-open')).toBe(true);
        expect(document.activeElement).toBe(f.a1);
    });

    test('마우스가 다른 바를 떠날 때도 키보드 포커스는 그대로 — blur는 막 떠난 바 안의 포커스만', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.a1.focus();
        f.study.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(150);
        f.study.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(100);
        expect(f.study.classList.contains('is-open')).toBe(false);
        expect(document.activeElement).toBe(f.a1);
    });

    test('펼친 바를 떠나 100ms 안에 반대쪽 바에 머물면 먼저 것은 닫히고 새 것만 펼친다(동시 펼침 없음)', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.reading.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(150);
        f.reading.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(50);
        f.study.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(150);
        expect(f.reading.classList.contains('is-open')).toBe(false);
        expect(f.study.classList.contains('is-open')).toBe(true);
    });

    test('입력 장치가 바뀌면(matchMedia change) 열린 바를 접는다', () => {
        const f = fixture();
        const listeners: Array<() => void> = [];
        const win = {
            matchMedia: () => ({ matches: false, addEventListener: (_: string, fn: () => void) => listeners.push(fn) }),
            setTimeout, clearTimeout,
        } as unknown as Window;
        bindRails(document, win);
        click(f.a1);
        expect(openCount()).toBe(1);
        listeners.forEach((fn) => fn());
        expect(openCount()).toBe(0);
    });

    test('MediaQueryList.addEventListener가 없는 옛 Safari(<14)에서도 배선이 죽지 않는다', () => {
        const f = fixture();
        const win = { matchMedia: () => ({ matches: false }), setTimeout, clearTimeout } as unknown as Window;
        expect(() => bindRails(document, win)).not.toThrow();
        click(f.a1);
        expect(openCount()).toBe(1);
    });
});

test('#side-rails가 없는 페이지 → null, 예외 없음', () => {
    document.body.innerHTML = '<main></main>';
    expect(bindRails(document, fakeWin(true))).toBeNull();
});

// 「로고가 홈이고, 홈은 내가 있던 바의 타이머로 열린다」 — 설계 2026-09-16-rail-home-entry.md §5 T-2.
describe('rememberMode — 활성 키 있는 비홈 페이지만 홈이 열릴 모드를 기억한다', () => {
    function root(remember?: string) {
        const attr = remember === undefined ? '' : ` data-remember="${remember}"`;
        document.body.innerHTML = `<div id="side-rails" data-mode="study"${attr}></div>`;
        return document.getElementById('side-rails') as HTMLElement;
    }
    function fakeStorage(seed?: string) {
        const m: Record<string, string> = {};
        if (seed) m[MODE_KEY] = seed;
        return { m, s: { setItem: (k: string, v: string) => { m[k] = v; } } as unknown as Storage };
    }

    test('data-remember="study" → 저장값이 study가 된다', () => {
        const { m, s } = fakeStorage('reading');
        rememberMode(root('study'), s);
        expect(m[MODE_KEY]).toBe('study');
    });

    test('data-remember="reading" → 저장값이 reading이 된다(대칭)', () => {
        const { m, s } = fakeStorage('study');
        rememberMode(root('reading'), s);
        expect(m[MODE_KEY]).toBe('reading');
    });

    test('속성 없음(홈·중립 페이지) → 저장소 미변경 — 「공부 모드 → 설정 → 로고 → 독서」 방지', () => {
        const { m, s } = fakeStorage('study');
        rememberMode(root(), s);
        expect(m[MODE_KEY]).toBe('study');
    });

    test('data-remember="garbage" → 미변경(미지값을 그대로 쓰지 않는다)', () => {
        const { m, s } = fakeStorage('reading');
        rememberMode(root('garbage'), s);
        expect(m[MODE_KEY]).toBe('reading');
    });

    test('저장소가 throw해도 삼킨다(사파리 프라이빗)', () => {
        const boom = { setItem: () => { throw new Error('QuotaExceeded'); } } as unknown as Storage;
        expect(() => rememberMode(root('study'), boom)).not.toThrow();
    });

    test('저장소가 없어도(undefined) 터지지 않는다', () => {
        expect(() => rememberMode(root('study'), undefined)).not.toThrow();
    });

    test('bindRails가 배선한다 — 페이지 로드 1회 저장 + 펼침 동작은 그대로', () => {
        const { m, s } = fakeStorage('reading');
        document.body.innerHTML = `
            <div id="side-rails" data-mode="study" data-remember="study">
                <aside class="rail rail-study"><nav><a id="a2" href="#r2">일정</a></nav></aside>
            </div>`;
        const win = { ...fakeWin(false), localStorage: s } as unknown as Window;
        const api = bindRails(document, win);
        expect(m[MODE_KEY]).toBe('study');
        expect(api).not.toBeNull();
        click(document.getElementById('a2')!); // 접힌 바 첫 탭 = 펼치기(기존 동작 유지)
        expect(openCount()).toBe(1);
    });

    test('저장이 throw해도 bindRails는 정상 반환한다(펼침이 막히지 않는다)', () => {
        document.body.innerHTML = `
            <div id="side-rails" data-mode="study" data-remember="study">
                <aside class="rail rail-study"><nav><a id="a2" href="#r2">일정</a></nav></aside>
            </div>`;
        const boom = { setItem: () => { throw new Error('QuotaExceeded'); } };
        const win = { ...fakeWin(false), localStorage: boom } as unknown as Window;
        expect(() => bindRails(document, win)).not.toThrow();
        click(document.getElementById('a2')!);
        expect(openCount()).toBe(1);
    });
});
