// @vitest-environment jsdom
// 양옆 세로 바 동작 — 빌드를 안 타는 정적 ESM(`rail.js`)을 그대로 import 한다(theme.test.ts와 같은 꼴).
// 설계 claude-docs/plans/2026-09-15-web-side-rails.md §4-④ · §7 T-4.
// jsdom엔 matchMedia가 없어 가짜 win을 주입한다(입력 장치 판별 = matches).
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

import { bindRails, bindModeSwitch, rememberMode, MODE_KEY, ENTER_DELAY_MS, LEAVE_DELAY_MS, HOVER_QUERY } from '../../src/main/resources/static/js/rail.js';

function fixture() {
    document.body.innerHTML = `
        <div id="side-rails" data-mode="reading">
            <aside class="rail">
                <nav class="rail-nav-reading"><a id="a1" href="#r1">내 책장</a></nav>
                <nav class="rail-nav-study"><a id="a2" href="#r2">일정</a></nav>
            </aside>
        </div>
        <button id="outside">바깥</button>`;
    return {
        rail: document.querySelector<HTMLElement>('.rail')!,
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
    test('접힌 바 링크 탭 → 이동을 막고 바를 펼친다', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        const ev = click(f.a1);
        expect(ev.defaultPrevented).toBe(true);
        expect(f.rail.classList.contains('is-open')).toBe(true);
    });

    test('펼친 바 링크 탭 → 이동한다(막지 않음)', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        click(f.a1);
        const ev = click(f.a1);
        expect(ev.defaultPrevented).toBe(false);
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
        f.rail.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(149);
        expect(f.rail.classList.contains('is-open')).toBe(false);
        vi.advanceTimersByTime(1);
        expect(f.rail.classList.contains('is-open')).toBe(true);

        f.rail.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(99);
        expect(f.rail.classList.contains('is-open')).toBe(true);
        vi.advanceTimersByTime(1);
        expect(f.rail.classList.contains('is-open')).toBe(false);
    });

    test('스침(진입 60ms 뒤 이탈) → 이후 1초 동안 한 번도 안 펼친다', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.rail.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(60);
        f.rail.dispatchEvent(new MouseEvent('mouseleave'));
        for (let t = 0; t < 1000; t += 10) {
            vi.advanceTimersByTime(10);
            expect(openCount()).toBe(0);
        }
    });

    test('바 안 링크에 포커스가 남은 채 mouseleave → 접힐 때 포커스도 바 밖으로', () => {
        const f = fixture();
        bindRails(document, fakeWin(true));
        f.rail.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(150);
        f.a1.focus();
        f.rail.dispatchEvent(new MouseEvent('mouseleave'));
        vi.advanceTimersByTime(100);
        expect(document.getElementById('side-rails')!.contains(document.activeElement)).toBe(false);
    });

    test('터치 기기의 흉내 mouseenter는 무시한다', () => {
        const f = fixture();
        bindRails(document, fakeWin(false));
        f.rail.dispatchEvent(new MouseEvent('mouseenter'));
        vi.advanceTimersByTime(1000);
        expect(openCount()).toBe(0);
    });
});

// 두 바 전제였던 「다른 바 포커스 보존」·「반대쪽 동시 펼침」은 바가 하나가 되며 대상이 사라져 걷었다(설계 2026-09-17 T4).
describe('bindRails — 리뷰 반영(#1137): 입력 장치 전환·옛 Safari', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

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
describe('rememberMode — 서버가 실은 값만 저장값에 적는다(지금 서버는 공부 바 페이지에서만 내보낸다)', () => {
    // data-mode(서버가 경로로 그린 지금 모드)와 data-remember(기억할 모드)는 다른 값이다.
    // 음성 케이스에서 둘을 같게 두면 「안 썼다」와 「같은 값을 썼다」가 구분되지 않는다 —
    // `dataset.remember ?? dataset.mode` 폴백 돌연변이가 그대로 살아남는다(#1147 리뷰 I-1).
    function root(remember?: string, mode = 'study') {
        const attr = remember === undefined ? '' : ` data-remember="${remember}"`;
        document.body.innerHTML = `<div id="side-rails" data-mode="${mode}"${attr}></div>`;
        return document.getElementById('side-rails') as HTMLElement;
    }
    function fakeStorage(seed?: string) {
        const m: Record<string, string> = {};
        const writes: Array<[string, string]> = [];
        if (seed) m[MODE_KEY] = seed;
        return { m, writes, s: { setItem: (k: string, v: string) => { writes.push([k, v]); m[k] = v; } } as unknown as Storage };
    }

    test('data-remember="study" → 저장값이 study가 된다', () => {
        const { m, s } = fakeStorage('reading');
        rememberMode(root('study'), s);
        expect(m[MODE_KEY]).toBe('study');
    });

    // rail.js는 속성에 실린 값을 그대로 쓴다 — 지금 서버는 study만 내보내지만(RailNav.rememberMode,
    // 2026-09-16 「공부 쪽만 기억」 개정) 이 계약은 값에 중립이다. 이 케이스가 그 중립성을 잠근다.
    test('data-remember="reading" → 저장값이 reading이 된다(서버가 그 값을 내보낼 때만)', () => {
        const { m, s } = fakeStorage('study');
        rememberMode(root('reading'), s);
        expect(m[MODE_KEY]).toBe('reading');
    });

    test('중립 페이지(속성 없음·data-mode="reading") → 공부 저장값이 그대로 — 「공부 모드 → 설정 → 로고 → 독서」 방지', () => {
        // 실제 시나리오: 공부 모드로 쓰다 /settings에 들어가면 서버는 경로대로 data-mode="reading"을 그린다.
        // 저장값(study)과 화면 모드(reading)가 다른 이 상태가 판별력의 전부다 — 같게 두면 공허해진다.
        const { m, writes, s } = fakeStorage('study');
        rememberMode(root(undefined, 'reading'), s);
        expect(writes).toHaveLength(0); // 「안 썼다」를 직접 단언 — 값 비교보다 강하다
        expect(m[MODE_KEY]).toBe('study');
    });

    test('홈(속성 없음·data-mode="study") → 저장소를 아예 안 건드린다(홈은 Vue 토글이 주인)', () => {
        const { writes, s } = fakeStorage('reading');
        rememberMode(root(undefined, 'study'), s);
        expect(writes).toHaveLength(0);
    });

    test('data-remember="garbage" → 미변경(미지값을 그대로 쓰지 않는다)', () => {
        const { m, writes, s } = fakeStorage('reading');
        rememberMode(root('garbage', 'study'), s);
        expect(writes).toHaveLength(0);
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
                <aside class="rail"><nav><a id="a2" href="#r2">일정</a></nav></aside>
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
                <aside class="rail"><nav><a id="a2" href="#r2">일정</a></nav></aside>
            </div>`;
        const boom = { setItem: () => { throw new Error('QuotaExceeded'); } };
        const win = { ...fakeWin(false), localStorage: boom } as unknown as Window;
        expect(() => bindRails(document, win)).not.toThrow();
        click(document.getElementById('a2')!);
        expect(openCount()).toBe(1);
    });
});

// 비홈 스위치(fragments/side-rails SSR 알약) — 누른 모드를 저장하고 홈으로. 지금 모드는 무동작(설계 2026-09-17 D1·D3).
describe('bindModeSwitch — 비홈 「독서 | 공부」 알약', () => {
    const PILL = `
        <div class="dash-mode-toggle-wrap">
            <div class="dash-mode-toggle" role="group" aria-label="독서·공부 모드">
                <button type="button" data-pick="reading">독서</button>
                <button type="button" data-pick="study">공부</button>
            </div>
        </div>`;
    function page(mode?: string, withPill = true) {
        const attr = mode === undefined ? '' : ` data-mode="${mode}"`;
        document.body.innerHTML = `
            <div id="side-rails"${attr}>
                <aside class="rail"><nav class="rail-nav-reading"><a id="a1" href="#r1">내 책장</a></nav></aside>
                ${withPill ? PILL : ''}
            </div>`;
        return document.getElementById('side-rails') as HTMLElement;
    }
    const pick = (m: string) => document.querySelector<HTMLButtonElement>(`button[data-pick="${m}"]`)!;
    function fakeSwitchWin(storage?: Partial<Storage>) {
        const writes: Array<[string, string]> = [];
        const localStorage = storage ?? { setItem: (k: string, v: string) => { writes.push([k, v]); } };
        const assign = vi.fn();
        const win = { ...fakeWin(false), localStorage, location: { assign } } as unknown as Window;
        return { win, writes, assign };
    }

    test('지금 reading에서 「공부」 → study 저장 + 홈 이동 1회', () => {
        const root = page('reading');
        const { win, writes, assign } = fakeSwitchWin();
        bindModeSwitch(root, win);
        click(pick('study'));
        expect(writes).toEqual([[MODE_KEY, 'study']]);
        expect(assign).toHaveBeenCalledTimes(1);
        expect(assign).toHaveBeenCalledWith('/');
    });

    test('지금 모드(「독서」) 클릭 → 저장·이동 둘 다 0회', () => {
        const root = page('reading');
        const { win, writes, assign } = fakeSwitchWin();
        bindModeSwitch(root, win);
        click(pick('reading'));
        expect(writes).toHaveLength(0);
        expect(assign).not.toHaveBeenCalled();
    });

    test('속성 없음(부재 = 독서)에서 「공부」 → 이동, 「독서」 → 무동작', () => {
        const root = page(undefined);
        const { win, writes, assign } = fakeSwitchWin();
        bindModeSwitch(root, win);
        click(pick('reading'));
        expect(assign).not.toHaveBeenCalled();
        click(pick('study'));
        expect(writes).toEqual([[MODE_KEY, 'study']]);
        expect(assign).toHaveBeenCalledTimes(1);
    });

    test('study 페이지에서 「독서」 → reading 저장 + 이동', () => {
        const root = page('study');
        const { win, writes, assign } = fakeSwitchWin();
        bindModeSwitch(root, win);
        click(pick('reading'));
        expect(writes).toEqual([[MODE_KEY, 'reading']]);
        expect(assign).toHaveBeenCalledWith('/');
    });

    test('바인딩 직후 aria-pressed는 지금 모드 버튼만 "true"', () => {
        const root = page('study');
        bindModeSwitch(root, fakeSwitchWin().win);
        expect(pick('study').getAttribute('aria-pressed')).toBe('true');
        expect(pick('reading').getAttribute('aria-pressed')).toBe('false');
    });

    test('저장소가 throw해도 홈으로는 간다(홈이 서버 진실로 연다)', () => {
        const root = page('reading');
        const { win, assign } = fakeSwitchWin({ setItem: () => { throw new Error('QuotaExceeded'); } });
        bindModeSwitch(root, win);
        expect(() => click(pick('study'))).not.toThrow();
        expect(assign).toHaveBeenCalledTimes(1);
    });

    test('알약이 없는 페이지(홈 — Vue가 그린다) → null, 예외 없음', () => {
        const root = page(undefined, false);
        expect(bindModeSwitch(root, fakeSwitchWin().win)).toBeNull();
    });

    test('bindRails가 배선한다 — 알약 클릭은 바 펼침을 건드리지 않고 이동한다', () => {
        page('reading');
        const { win, writes, assign } = fakeSwitchWin();
        bindRails(document, win);
        click(pick('study'));
        expect(writes).toEqual([[MODE_KEY, 'study']]);
        expect(assign).toHaveBeenCalledTimes(1);
        expect(openCount()).toBe(0);
    });

    // 알약은 #side-rails 안에 있지만 바(.rail)는 아니다 — 닫기·바깥 판정이 root 기준이면 알약을 바 안으로 친다(리뷰 사소-2).
    test('알약 버튼에 키보드 포커스를 둔 채 Esc → 포커스를 그대로 둔다(바 안 포커스만 푼다)', () => {
        page('reading');
        bindRails(document, fakeSwitchWin().win);
        pick('study').focus();
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
        expect(document.activeElement).toBe(pick('study'));
    });

    test('터치: 펼친 바가 있을 때 알약(지금 모드) 탭 → 바를 접는다(바깥 탭과 같다)', () => {
        page('reading');
        bindRails(document, fakeSwitchWin().win);
        click(document.getElementById('a1')!);
        expect(openCount()).toBe(1);
        click(pick('reading'));
        expect(openCount()).toBe(0);
    });
});

// 홈 인라인 부트(fragments/side-rails) — 첫 페인트 전 힌트. 문자열을 눈으로 훑는 대신 **그대로 실행**한다:
// 형식 검사는 조건이 뒤집혀도 통과하지만, 실행은 결과를 본다(#1147 리뷰 M-2).
describe('side-rails 인라인 부트 — 램프 힌트가 켜졌으면 모드도 study로 세운다', () => {
    // jsdom 환경에선 import.meta.url이 file: URL이 아니라(문서 URL) timer-mode.test.ts의 경로 계산을
    // 그대로 못 쓴다 — cwd에서 위로 훑고, 못 찾으면 조용히 통과하지 않게 던진다.
    function repoFile(...parts: string[]): string {
        let dir = process.cwd();
        for (let i = 0; i < 5; i++) {
            const p = join(dir, ...parts);
            if (existsSync(p)) return p;
            dir = join(dir, '..');
        }
        throw new Error(`레포 파일을 못 찾았다: ${parts.join('/')} (cwd=${process.cwd()})`);
    }
    const fragment = readFileSync(
        repoFile('src', 'main', 'resources', 'templates', 'fragments', 'side-rails.html'), 'utf8');
    const boot = fragment.match(/<script th:if[^>]*>([\s\S]*?)<\/script>/)?.[1] ?? '';
    const LAMP_KEY = 'booktimer.studyLamp';

    const HOME = 'dashboard-page'; // dashboard.html의 body 클래스 — 독서등은 홈 전용이라 이걸로만 켠다

    /**
     * 서버가 속성을 비운(홈·중립) 또는 경로로 그린(mode) 바에 부트를 실행한다.
     * 설계 2026-09-17 D4: 부트는 속성이 비었을 때만 채우고, 독서등 클래스는 홈에서만 붙인다.
     */
    function runBoot(seed: Record<string, string>, opts: { mode?: string; bodyClass?: string } = {}) {
        localStorage.clear();
        for (const [k, v] of Object.entries(seed)) localStorage.setItem(k, v);
        const attr = opts.mode === undefined ? '' : ` data-mode="${opts.mode}"`;
        document.body.innerHTML = `<div id="side-rails"${attr}></div>`;
        document.body.className = opts.bodyClass ?? '';
        new Function(boot)();
        return document.getElementById('side-rails') as HTMLElement;
    }

    test('추출한 부트가 비어 있지 않다(공허 방지 — 정규식이 빗나가면 전부 통과한다)', () => {
        expect(boot).toContain('side-rails');
        expect(boot).toContain(LAMP_KEY);
    });

    describe('홈(속성 없음 · body.dashboard-page)', () => {
        test('램프 힌트만 있고 모드 힌트가 없어도 study — 밤 배경에 독서 바가 선명한 구간 방지', () => {
            const root = runBoot({ [LAMP_KEY]: '1' }, { bodyClass: HOME });
            expect(root.dataset.mode).toBe('study');
            expect(document.body.classList.contains('study-lamp')).toBe(true);
        });

        test('모드 힌트가 reading이어도 램프가 켜져 있으면 study가 이긴다(공부 측정 중 내 책장을 들른 경우)', () => {
            const root = runBoot({ [MODE_KEY]: 'reading', [LAMP_KEY]: '1' }, { bodyClass: HOME });
            expect(root.dataset.mode).toBe('study');
        });

        test('모드 힌트만 study면 study이고 독서등은 안 켠다(기존 동작 회귀)', () => {
            const root = runBoot({ [MODE_KEY]: 'study' }, { bodyClass: HOME });
            expect(root.dataset.mode).toBe('study');
            expect(document.body.classList.contains('study-lamp')).toBe(false);
        });

        test('힌트가 둘 다 없으면 속성을 안 쓴다 — 부재 = 독서(음성 대조군)', () => {
            const root = runBoot({}, { bodyClass: HOME });
            expect(root.hasAttribute('data-mode')).toBe(false);
            expect(document.body.classList.contains('study-lamp')).toBe(false);
        });
    });

    describe('중립 화면(속성 없음 · 홈 클래스 없음)과 경로 페이지', () => {
        test('중립 + 저장값 study → study, 독서등 없음', () => {
            const root = runBoot({ [MODE_KEY]: 'study' });
            expect(root.dataset.mode).toBe('study');
            expect(document.body.classList.contains('study-lamp')).toBe(false);
        });

        test('중립 + 램프 힌트만 → 모드는 study지만 독서등은 안 켠다(독서등은 홈 전용)', () => {
            const root = runBoot({ [LAMP_KEY]: '1' });
            expect(root.dataset.mode).toBe('study');
            expect(document.body.classList.contains('study-lamp')).toBe(false);
        });

        test('중립 + 힌트 없음 → 속성 여전히 없음(reading을 명시하지 않는다)', () => {
            const root = runBoot({});
            expect(root.hasAttribute('data-mode')).toBe(false);
        });

        test('경로 페이지(서버가 data-mode="reading") + 저장값 study → reading 그대로(경로가 이긴다)', () => {
            const root = runBoot({ [MODE_KEY]: 'study' }, { mode: 'reading' });
            expect(root.dataset.mode).toBe('reading');
        });
    });
});
