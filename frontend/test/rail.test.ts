// @vitest-environment jsdom
// 양옆 세로 바 동작 — 빌드를 안 타는 정적 ESM(`rail.js`)을 그대로 import 한다(theme.test.ts와 같은 꼴).
// 설계 claude-docs/plans/2026-09-15-web-side-rails.md §4-④ · §7 T-4.
// jsdom엔 matchMedia가 없어 가짜 win을 주입한다(입력 장치 판별 = matches).
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

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

    test('data-remember="reading" → 저장값이 reading이 된다(대칭)', () => {
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

    function runBoot(seed: Record<string, string>) {
        localStorage.clear();
        document.body.className = '';
        for (const [k, v] of Object.entries(seed)) localStorage.setItem(k, v);
        document.body.innerHTML = '<div id="side-rails" data-mode="reading"></div>'; // 서버가 경로로 그린 값
        new Function(boot)();
        return document.getElementById('side-rails') as HTMLElement;
    }

    test('추출한 부트가 비어 있지 않다(공허 방지 — 정규식이 빗나가면 전부 통과한다)', () => {
        expect(boot).toContain('side-rails');
        expect(boot).toContain(LAMP_KEY);
    });

    test('램프 힌트만 있고 모드 힌트가 없어도 study — 밤 배경에 독서 바가 선명한 구간 방지', () => {
        const root = runBoot({ [LAMP_KEY]: '1' });
        expect(root.dataset.mode).toBe('study');
        expect(document.body.classList.contains('study-lamp')).toBe(true);
    });

    test('모드 힌트가 reading이어도 램프가 켜져 있으면 study가 이긴다(공부 측정 중 내 책장을 들른 경우)', () => {
        const root = runBoot({ [MODE_KEY]: 'reading', [LAMP_KEY]: '1' });
        expect(root.dataset.mode).toBe('study');
    });

    test('모드 힌트만 study면 study이고 독서등은 안 켠다(기존 동작 회귀)', () => {
        const root = runBoot({ [MODE_KEY]: 'study' });
        expect(root.dataset.mode).toBe('study');
        expect(document.body.classList.contains('study-lamp')).toBe(false);
    });

    test('힌트가 둘 다 없으면 서버가 그린 reading 그대로(음성 대조군)', () => {
        const root = runBoot({});
        expect(root.dataset.mode).toBe('reading');
        expect(document.body.classList.contains('study-lamp')).toBe(false);
    });
});
