// 다크 테마 토글 — 순수 함수 단위 테스트 (RED → GREEN TDD)
//
// `theme.js`는 빌드를 타지 않는 정적 ESM이라(`pwa-install.js`와 같은 꼴) 번들러 없이 그대로 import 한다.
// jsdom이 없는 하니스(T-149)라 DOM은 가짜 객체로 계측한다 — 실제 렌더·클릭은 실 브라우저 게이트의 몫이다.
import { describe, expect, test } from 'vitest';

import {
    KEY,
    DARK,
    LIGHT,
    resolveTheme,
    nextTheme,
    applyTheme,
    persist,
    readStored,
    mountToggle,
} from '../../src/main/resources/static/js/theme.js';

/** 최소 엘리먼트 — setAttribute/addEventListener/appendChild만 있으면 mountToggle이 돈다. */
function fakeEl(tag: string) {
    return {
        tagName: tag,
        type: '',
        className: '',
        innerHTML: '',
        attrs: {} as Record<string, string>,
        children: [] as ReturnType<typeof fakeEl>[],
        listeners: {} as Record<string, Array<() => void>>,
        setAttribute(name: string, value: string) {
            this.attrs[name] = value;
        },
        getAttribute(name: string) {
            return this.attrs[name] ?? null;
        },
        addEventListener(type: string, fn: () => void) {
            (this.listeners[type] ??= []).push(fn);
        },
        appendChild(child: ReturnType<typeof fakeEl>) {
            this.children.push(child);
            return child;
        },
        click() {
            (this.listeners.click ?? []).forEach((fn) => fn());
        },
    };
}

/**
 * 최소 document — `querySelector`가 헤더와 이미 꽂힌 토글만 구분하면 된다.
 *
 * <p>`documentElement.dataset`은 진짜 DOM처럼 「속성 삭제 = 키 삭제」로 관찰한다. 라이트가
 * `dataset.theme = 'light'`로 남으면(속성이 안 지워지면) 아래 단언이 운다.
 */
function fakeDoc({ header = true }: { header?: boolean } = {}) {
    const root = fakeEl('html') as ReturnType<typeof fakeEl> & { dataset: Record<string, string> };
    root.dataset = {};
    const host = header ? fakeEl('header') : null;
    return {
        documentElement: root,
        host,
        createElement: (tag: string) => fakeEl(tag),
        querySelector(selector: string) {
            if (selector.includes('.theme-toggle')) {
                return host?.children.find((c) => c.className === 'theme-toggle') ?? null;
            }
            if (selector.includes('header')) return host;
            return null;
        },
    };
}

function mapStorage(initial: Record<string, string> = {}) {
    const map = { ...initial };
    return {
        map,
        getItem: (k: string) => map[k] ?? null,
        setItem: (k: string, v: string) => {
            map[k] = v;
        },
    };
}

/** 사파리 프라이빗 모드 등 — 접근 자체가 던진다. */
const throwingStorage = {
    getItem() {
        throw new Error('SecurityError');
    },
    setItem() {
        throw new Error('SecurityError');
    },
};

describe('resolveTheme — 저장값에서 테마를 정한다', () => {
    test("정확히 'dark'일 때만 다크", () => {
        expect(resolveTheme('dark')).toBe(DARK);
    });

    test.each([null, undefined, '', 'DARK', 'Dark', 'garbage', '{"a":1}', 'light'])(
        '나머지는 전부 라이트 — %j',
        (stored) => {
            expect(resolveTheme(stored as string | null)).toBe(LIGHT);
        },
    );
});

describe('nextTheme — 2단 토글', () => {
    test('라이트에서 누르면 다크', () => {
        expect(nextTheme(LIGHT)).toBe(DARK);
    });

    test('다크에서 누르면 라이트', () => {
        expect(nextTheme(DARK)).toBe(LIGHT);
    });

    test('두 번 누르면 원점 — 연속 클릭이 상태를 흘리지 않는다', () => {
        expect(nextTheme(nextTheme(LIGHT))).toBe(LIGHT);
        expect(nextTheme(nextTheme(DARK))).toBe(DARK);
    });
});

describe('applyTheme — documentElement 반영', () => {
    test("다크는 data-theme='dark'를 심는다", () => {
        const doc = fakeDoc();
        applyTheme(doc, DARK);
        expect(doc.documentElement.dataset.theme).toBe('dark');
    });

    test("라이트는 속성을 '삭제'한다 — 'light' 문자열이 남으면 안 된다", () => {
        const doc = fakeDoc();
        applyTheme(doc, DARK);
        applyTheme(doc, LIGHT);
        expect('theme' in doc.documentElement.dataset).toBe(false);
    });
});

describe('저장 — 접근 예외를 삼킨다', () => {
    test('persist 성공은 true, 값이 KEY에 들어간다', () => {
        const storage = mapStorage();
        expect(persist(storage, DARK)).toBe(true);
        expect(storage.map[KEY]).toBe('dark');
    });

    test('persist는 예외를 밖으로 던지지 않고 false', () => {
        expect(() => persist(throwingStorage, DARK)).not.toThrow();
        expect(persist(throwingStorage, DARK)).toBe(false);
    });

    test('readStored는 예외 시 null — 라이트로 떨어진다', () => {
        expect(readStored(throwingStorage)).toBe(null);
        expect(resolveTheme(readStored(throwingStorage))).toBe(LIGHT);
    });

    test('readStored는 저장값을 그대로 읽는다', () => {
        expect(readStored(mapStorage({ [KEY]: 'dark' }))).toBe('dark');
    });
});

describe('mountToggle — 헤더에 버튼을 꽂는다', () => {
    test('헤더가 없는 페이지에서는 null (아무것도 안 꽂는다)', () => {
        expect(mountToggle(fakeDoc({ header: false }), mapStorage())).toBe(null);
    });

    test("저장값이 'dark'면 aria-pressed 초기값이 true", () => {
        const btn = mountToggle(fakeDoc(), mapStorage({ [KEY]: 'dark' }));
        expect(btn?.getAttribute('aria-pressed')).toBe('true');
    });

    test('저장값이 없으면 aria-pressed 초기값이 false', () => {
        const btn = mountToggle(fakeDoc(), mapStorage());
        expect(btn?.getAttribute('aria-pressed')).toBe('false');
    });

    test('두 번 마운트해도 버튼은 하나 — 두 번째는 null', () => {
        const doc = fakeDoc();
        const storage = mapStorage();
        expect(mountToggle(doc, storage)).not.toBe(null);
        expect(mountToggle(doc, storage)).toBe(null);
        expect(doc.host?.children.filter((c) => c.className === 'theme-toggle').length).toBe(1);
    });

    test('클릭하면 테마가 뒤집히고 저장·aria-pressed가 따라온다', () => {
        const doc = fakeDoc();
        const storage = mapStorage();
        const btn = mountToggle(doc, storage)!;

        btn.click();
        expect(doc.documentElement.dataset.theme).toBe('dark');
        expect(storage.map[KEY]).toBe('dark');
        expect(btn.getAttribute('aria-pressed')).toBe('true');

        btn.click();
        expect('theme' in doc.documentElement.dataset).toBe(false);
        expect(storage.map[KEY]).toBe('light');
        expect(btn.getAttribute('aria-pressed')).toBe('false');
    });

    test('저장이 막혀도 화면 전환은 된다 — 세션 한정으로 동작', () => {
        const doc = fakeDoc();
        const btn = mountToggle(doc, throwingStorage)!;
        expect(() => btn.click()).not.toThrow();
        expect(doc.documentElement.dataset.theme).toBe('dark');
    });

    test('아이콘은 SVG다 — 이모지를 쓰지 않는다(noEmoji 규칙의 자리별 확인)', () => {
        const btn = mountToggle(fakeDoc(), mapStorage())!;
        expect(btn.innerHTML).toContain('<svg');
    });
});
