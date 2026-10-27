import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * 웹 독서 화면의 연속 · 수동 기록 진입 가드(책생글 PR-R — 설계 2026-10-22 범위 보충 §3.4 「웹」).
 *
 * <p>소스를 읽어 <b>행위 구문</b>을 찾는다 — 토큰이 아니라 속성 읽기(`.currentStreak`)와 링크(`href` 뒤의
 * `/sessions/manual`). 판정은 <b>주석을 걷은</b> 소스에서 한다 — 주석이 옛 이름을 인용하면 단언이 공허해진다(T-205,
 * `editorialTheme.test.ts`와 같은 규율). 각 패턴은 심은 픽스처를 먼저 잡아 보인다(양성 대조 — 패턴이 죽어 있으면
 * 「0건」은 아무것도 증명하지 않는다).
 */

const HERE = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');

/** 블록 · HTML · 줄 주석을 걷는다. 줄 주석은 `https://`의 `//`를 건드리지 않게 `:` 바로 뒤는 비켜 간다. */
const strip = (src: string) => src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1');

/** `dir` 아래 .vue · .ts 소스(테스트 제외)의 상대 경로. */
function sources(dir: string): string[] {
    return (readdirSync(join(HERE, dir), { recursive: true }) as string[])
        .map((f) => join(dir, f))
        .filter((f) => /\.(vue|ts)$/.test(f) && !/\.test\.ts$/.test(f));
}

/** 주석을 걷은 소스 하나에서 패턴이 잡힌 횟수. */
const hits = (src: string, pattern: RegExp) => (strip(src).match(new RegExp(pattern.source, 'g')) ?? []).length;

/** 여러 파일에서 잡힌 자리 — 실패 메시지가 파일을 가리키게. */
function offenders(files: string[], pattern: RegExp): string[] {
    return files.filter((f) => hits(readFileSync(join(HERE, f), 'utf8'), pattern) > 0);
}

/** 1초 연속 속성 읽기 — 타입 선언 `currentStreak: number`는 점이 없어 걸리지 않는다. */
const READS_CURRENT_STREAK = /\.currentStreak\b/;
/** 수동 기록 폼으로 가는 링크 — `href="/sessions/manual…"` · `:href="'/sessions/manual?date=' + d"` 둘 다. */
const LINKS_MANUAL_ENTRY = /href[^>]*?\/sessions\/manual/;

describe('REQ-118 · 독서 화면(dashboard·history)에 currentStreak 읽기가 없다', () => {
    it('양성 대조 — 심은 읽기는 1건 잡히고, 주석 · 타입 선언은 잡히지 않는다', () => {
        expect(hits('<X :streak="data.graph.currentStreak" />', READS_CURRENT_STREAK)).toBe(1);
        expect(hits('// data.graph.currentStreak\n<!-- g.currentStreak -->\n/* x.currentStreak */', READS_CURRENT_STREAK)).toBe(0);
        expect(hits('interface G { currentStreak: number }', READS_CURRENT_STREAK)).toBe(0);
    });

    it('dashboard · history 소스에 .currentStreak 읽기 0건', () => {
        const files = [...sources('dashboard'), ...sources('history')];
        expect(files.length).toBeGreaterThan(10); // 범위가 비면 「0건」이 공허하다
        expect(offenders(files, READS_CURRENT_STREAK)).toEqual([]);
    });
});

describe('REQ-119 · 웹 섬 소스에 수동 기록으로 가는 링크가 없다', () => {
    it('양성 대조 — 심은 링크 두 꼴은 1건씩 잡히고, 주석 속 링크는 잡히지 않는다', () => {
        expect(hits(`<a :href="'/sessions/manual?date=' + d.date">채우기</a>`, LINKS_MANUAL_ENTRY)).toBe(1);
        expect(hits('<a href="/sessions/manual">직접 기록</a>', LINKS_MANUAL_ENTRY)).toBe(1);
        expect(hits('<!-- <a href="/sessions/manual"> -->', LINKS_MANUAL_ENTRY)).toBe(0);
    });

    it('frontend/src 소스에 /sessions/manual 링크 0건', () => {
        const files = readdirSync(HERE, { withFileTypes: true })
            .filter((d) => d.isDirectory())
            .flatMap((d) => sources(d.name));
        expect(files.length).toBeGreaterThan(50);
        expect(offenders(files, LINKS_MANUAL_ENTRY)).toEqual([]);
    });
});
