// history.html 첫 프레임 클래스 (node env).
// 기록 카드의 stacked↔split 판단(chooseLayout)은 「빠뜨린 날」을 걷으며 한 칸이 되어 지웠다(책생글 PR-R · REQ-119).
// 940 상한(history-wide)은 그대로 둔다 — 넓은 창에서 한 칸 읽기 폭으로도 무해하다(U-54에서 실측).
import { describe, test, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HISTORY_HTML = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'src', 'main', 'resources', 'templates', 'history.html');

describe('history.html 첫 프레임', () => {
    // 통과 = 첫 HTML의 <body>에 history-wide가 있어 CSS가 첫 레이아웃부터 940 상한을 건다.
    // 실패 = 템플릿에서 빠져 마운트 뒤 1160→940 이동(넓은 창 CLS 0.05)이 돌아온 상태. 실제 CLS 값은 못 잰다.
    // history-page·has-rails를 함께 요구하는 건 정규식이 엉뚱한 속성을 잡지 않았다는 양성 대조다.
    test('REQ-01 · <body>가 처음부터 history-wide를 단다', () => {
        const cls = /<body\b[^>]*\bclass="([^"]*)"/.exec(readFileSync(HISTORY_HTML, 'utf8'))?.[1] ?? '';
        expect(cls.split(/\s+/)).toEqual(expect.arrayContaining(['history-page', 'has-rails', 'history-wide']));
    });
});
