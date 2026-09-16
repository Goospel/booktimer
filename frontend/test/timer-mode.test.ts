// timerMode — 순수 함수(node 환경). 복귀 재조회 스로틀(shouldRefresh) + 모드 저장·effectiveMode.
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { shouldRefresh, REFRESH_THROTTLE_MS, readMode, writeMode, effectiveMode, MODE_KEY, syncRailMode, studyFocusOn, syncStudyLamp, LAMP_KEY, LAMP_CLASS } from '../src/dashboard/timerMode';

describe('shouldRefresh — 60초 스로틀', () => {
    test('스로틀 창 안(59.999초)이면 재조회하지 않는다', () => {
        expect(shouldRefresh(0, 59_999)).toBe(false);
    });

    test('경계(60초)에 닿으면 재조회한다', () => {
        expect(shouldRefresh(0, 60_000)).toBe(true);
        expect(REFRESH_THROTTLE_MS).toBe(60_000);
    });

    test('force면 스로틀 창 안이어도 재조회한다(409 → 강제 재조회 경로)', () => {
        expect(shouldRefresh(0, 1, true)).toBe(true);
    });
});

describe('effectiveMode — 서버 진실이 저장값을 이긴다', () => {
    test('독서 진행 중이면 저장값이 study여도 독서', () => {
        expect(effectiveMode(true, true, 'study')).toBe('reading');
        expect(effectiveMode(true, false, 'study')).toBe('reading');
    });

    test('공부 진행 중(독서는 아님)이면 저장값이 reading이어도 공부', () => {
        expect(effectiveMode(false, true, 'reading')).toBe('study');
    });

    test('둘 다 진행 중이 아니면 저장값을 따른다', () => {
        expect(effectiveMode(false, false, 'study')).toBe('study');
        expect(effectiveMode(false, false, 'reading')).toBe('reading');
    });
});

describe('readMode / writeMode — 미지값·접근 불가는 reading으로 떨어진다', () => {
    test('저장값이 study면 study', () => {
        expect(readMode({ getItem: () => 'study' })).toBe('study');
    });

    test('미지값·null·저장소 없음·접근 예외는 모두 reading', () => {
        expect(readMode({ getItem: () => 'garbage' })).toBe('reading');
        expect(readMode({ getItem: () => null })).toBe('reading');
        expect(readMode(null)).toBe('reading');
        expect(readMode({ getItem: () => { throw new Error('SecurityError'); } })).toBe('reading');
    });

    test('writeMode는 같은 키에 쓰고, 저장소가 throw해도 삼킨다(사파리 프라이빗)', () => {
        const saved: Record<string, string> = {};
        writeMode('study', { setItem: (k: string, v: string) => { saved[k] = v; } });
        expect(saved[MODE_KEY]).toBe('study');
        expect(() => writeMode('reading', { setItem: () => { throw new Error('QuotaExceeded'); } })).not.toThrow();
    });
});

describe('syncRailMode — 홈 모드를 양옆 바 흐림 상태(#side-rails[data-mode])로', () => {
    test('바가 있으면 data-mode를 그 모드로 쓴다', () => {
        const attrs: Record<string, string> = { 'data-mode': 'reading' };
        const el = { setAttribute: (k: string, v: string) => { attrs[k] = v; } };
        syncRailMode({ getElementById: (id: string) => (id === 'side-rails' ? el : null) } as never, 'study');
        expect(attrs['data-mode']).toBe('study');
    });

    test('바가 없는 문서면 아무 일도 안 한다(예외 없음)', () => {
        expect(() => syncRailMode({ getElementById: () => null } as never, 'study')).not.toThrow();
    });

    test('리터럴 동기 — side-rails 인라인 부트가 MODE_KEY를 그대로 읽는다(모듈 import 불가, FOUC 방지)', () => {
        // 인라인 부트는 이 모듈을 import 못 해 키를 따로 적는다 — 한쪽만 바뀌면 홈 첫 페인트만 조용히 틀린다.
        const here = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
        const src = readFileSync(join(here, '..', '..', 'src', 'main', 'resources', 'templates', 'fragments', 'side-rails.html'), 'utf8');
        expect(src).toContain(`localStorage.getItem('${MODE_KEY}')==='study'`);
        expect(src).toContain(".dataset.mode='study'");
    });

    test('리터럴 동기 — rail.js도 MODE_KEY를 그대로 적는다(정적 ESM이라 이 모듈을 import 못 한다)', () => {
        // rail.js가 바 방문 시 이 키에 모드를 쓴다(홈이 열릴 모드). 한쪽만 바뀌면 기억이 조용히 죽는다.
        const here = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
        const src = readFileSync(join(here, '..', '..', 'src', 'main', 'resources', 'static', 'js', 'rail.js'), 'utf8');
        expect(src).toContain(`= '${MODE_KEY}'`);
    });
});

// 독서등(설계 2026-09-15-study-focus-lamp §2-5) — 합침 = 독서등 = 이 값 하나. 첫 페인트 힌트는 인라인 부트가 읽는다.
describe('studyFocusOn / syncStudyLamp — 공부 측정 중 홈의 독서등', () => {
    test('공부 모드에서 공부 측정 중일 때만 켠다(미니앱 lampOn과 같은 꼴)', () => {
        expect(studyFocusOn('study', true)).toBe(true);
        expect(studyFocusOn('study', false)).toBe(false);
        expect(studyFocusOn('reading', true)).toBe(false);
    });

    function fakeDoc() {
        const classes = new Set<string>();
        return { classes, doc: { body: { classList: { toggle: (c: string, on: boolean) => { if (on) classes.add(c); else classes.delete(c); } } } } as never };
    }
    function fakeStorage() {
        const m: Record<string, string> = {};
        return { m, s: { setItem: (k: string, v: string) => { m[k] = v; }, removeItem: (k: string) => { delete m[k]; } } };
    }

    test('켜면 body 클래스 + 힌트 1, 끄면 둘 다 걷는다', () => {
        const { classes, doc } = fakeDoc();
        const { m, s } = fakeStorage();
        syncStudyLamp(doc, true, s);
        expect(classes.has(LAMP_CLASS)).toBe(true);
        expect(m[LAMP_KEY]).toBe('1');

        syncStudyLamp(doc, false, s);
        expect(classes.has(LAMP_CLASS)).toBe(false);
        expect(LAMP_KEY in m).toBe(false);
    });

    test('저장소가 throw해도 클래스는 붙고 예외는 삼킨다(사파리 프라이빗)', () => {
        const { classes, doc } = fakeDoc();
        const boom = { setItem: () => { throw new Error('QuotaExceeded'); }, removeItem: () => { throw new Error('x'); } };
        expect(() => syncStudyLamp(doc, true, boom)).not.toThrow();
        expect(classes.has(LAMP_CLASS)).toBe(true);
        expect(() => syncStudyLamp(doc, false, boom)).not.toThrow();
    });

    test('리터럴 동기 — side-rails 인라인 부트가 LAMP_KEY·LAMP_CLASS를 그대로 적는다(첫 페인트 깜빡임 방지)', () => {
        const here = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
        const src = readFileSync(join(here, '..', '..', 'src', 'main', 'resources', 'templates', 'fragments', 'side-rails.html'), 'utf8');
        expect(src).toContain(`localStorage.getItem('${LAMP_KEY}')==='1'`);
        expect(src).toContain(`document.body.classList.add('${LAMP_CLASS}')`);
    });
});
