// 기록 카드 stacked↔split 전환 임계폭 판단 — 순수함수 경계 테스트 (node env) + history.html 첫 프레임 클래스.
// 좁으면 'stacked'(pill 탭 + 한 패널), 임계폭 이상이면 'split'(2단 grid).
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chooseLayout, SPLIT_MIN_WIDTH } from '../src/history/layout';

const HISTORY_HTML = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'src', 'main', 'resources', 'templates', 'history.html');

describe('chooseLayout', () => {
    test('임계폭 미만은 stacked', () => {
        expect(chooseLayout(SPLIT_MIN_WIDTH - 1)).toBe('stacked');
        expect(chooseLayout(460)).toBe('stacked');
        expect(chooseLayout(0)).toBe('stacked');
    });

    test('임계폭 정확히는 split (>=)', () => {
        expect(chooseLayout(SPLIT_MIN_WIDTH)).toBe('split');
    });

    test('임계폭 초과는 split', () => {
        expect(chooseLayout(SPLIT_MIN_WIDTH + 1)).toBe('split');
        expect(chooseLayout(1200)).toBe('split');
    });

    // 절대값 — 상대값 테스트만으론 경계가 옛 880이어도 통과한다. 바 여백 248을 뺀 852가 2단 최소 폭.
    test('태블릿 가로(1024)는 stacked, 데스크톱 경계 1100부터 split', () => {
        expect(chooseLayout(1024)).toBe('stacked');
        expect(chooseLayout(1100)).toBe('split');
    });

    test('SPLIT_MIN_WIDTH는 양수 상수', () => {
        expect(SPLIT_MIN_WIDTH).toBeGreaterThan(0);
    });
});

describe('history.html 첫 프레임', () => {
    // 통과 = 첫 HTML의 <body>에 history-wide가 있어 CSS가 첫 레이아웃부터 940 상한을 건다.
    // 실패 = 템플릿에서 빠져 마운트 뒤 1160→940 이동(넓은 창 CLS 0.05)이 돌아온 상태. 실제 CLS 값은 못 잰다.
    // history-page·has-rails를 함께 요구하는 건 정규식이 엉뚱한 속성을 잡지 않았다는 양성 대조다.
    test('REQ-01 · <body>가 처음부터 history-wide를 단다', () => {
        const cls = /<body\b[^>]*\bclass="([^"]*)"/.exec(readFileSync(HISTORY_HTML, 'utf8'))?.[1] ?? '';
        expect(cls.split(/\s+/)).toEqual(expect.arrayContaining(['history-page', 'has-rails', 'history-wide']));
    });
});
