// 편집기의 순수 로직 — 슬래시 메뉴 필터 · 본문 상한 · Tab 결정.
//
// 셋 다 Tiptap 없이 혼자 서는 규칙이라 여기서 잰다. 편집기 마운트가 필요한 것(왕복·툴바·키맵)은
// RecallEditor.test.ts가 맡는다.
import { describe, test, expect } from 'vitest';

import { BODY_MAX, SLASH_ITEMS, SLASH_WIDTH, bodyBudget, filterSlashItems, slashHeight, slashPosition, tabAction } from './pure';

describe('슬래시 메뉴 필터', () => {
    test('빈 질의는 전부 보여 준다 — `/`만 친 순간이 곧 「뭐가 있는지 보여 줘」다', () => {
        expect(filterSlashItems('')).toHaveLength(SLASH_ITEMS.length);
        expect(SLASH_ITEMS).toHaveLength(7);
    });

    test('「제목」은 큰·작은 제목 둘만 남긴다', () => {
        expect(filterSlashItems('제목').map((i) => i.id)).toEqual(['h1', 'h2']);
    });

    test('「체크」와 「할일」이 같은 항목을 찾는다 — 사용자가 부르는 이름이 하나가 아니다', () => {
        expect(filterSlashItems('체크').map((i) => i.id)).toEqual(['taskList']);
        expect(filterSlashItems('할일').map((i) => i.id)).toEqual(['taskList']);
    });

    test('안 맞으면 빈 배열 — 팝업을 안 그리는 근거다', () => {
        expect(filterSlashItems('zzz')).toEqual([]);
    });

    test('앞뒤 공백과 대소문자는 무시한다', () => {
        expect(filterSlashItems('  H1 ').map((i) => i.id)).toEqual(['h1']);
    });
});

describe('본문 상한', () => {
    test('빈 글은 8000자가 통째로 남는다', () => {
        expect(bodyBudget('')).toEqual({ length: 0, remaining: BODY_MAX, over: false });
    });

    test('딱 8000자는 아직 넘지 않았다 — 서버도 8000을 받는다', () => {
        expect(bodyBudget('a'.repeat(8000))).toEqual({ length: 8000, remaining: 0, over: false });
    });

    test('8001자부터 넘었다', () => {
        expect(bodyBudget('a'.repeat(8001))).toEqual({ length: 8001, remaining: -1, over: true });
    });

    test('한글 8000자도 넘지 않는다 — 서버 String.length()와 같은 UTF-16 코드유닛 셈이다', () => {
        expect(bodyBudget('가'.repeat(8000)).over).toBe(false);
        expect(bodyBudget('가'.repeat(8001)).over).toBe(true);
    });
});

describe('Tab 결정', () => {
    test('목록 밖에서 Tab은 글머리 목록을 만든다 — 빈 문단에서 들여쓰기는 의미가 없다', () => {
        expect(tabAction(false, false)).toBe('toBullet');
    });

    test('목록 안에서 Tab은 한 단 들어가고 Shift+Tab은 나온다', () => {
        expect(tabAction(true, false)).toBe('sink');
        expect(tabAction(true, true)).toBe('lift');
    });

    test('목록 밖 Shift+Tab은 아무것도 안 한다 — 브라우저 기본 포커스 이동을 뺏지 않는다', () => {
        expect(tabAction(false, true)).toBeNull();
    });
});

// 좌표는 실 브라우저에서 잰다(로컬 1280×800 · 폰 375×812). 그때 「캐럿이 화면 아래쪽에 있으면
// 팝업이 화면 밖(+82px)으로 나간다」가 실측돼 이 함수가 생겼다 — 그전 코드엔 세로 보정이 아예 없었다.
describe('슬래시 팝업 자리', () => {
    const VIEWPORT = { width: 375, height: 812 };
    const caret = (left: number, top: number) => ({ left, top, bottom: top + 20 });

    test('자리가 있으면 캐럿 바로 아래에 붙인다', () => {
        expect(slashPosition(caret(40, 100), VIEWPORT, 2).top).toBe(126);
    });

    test('아래가 모자라면 위로 뒤집는다 — 화면 밖으로 나가지 않는다', () => {
        const pos = slashPosition(caret(40, 760), VIEWPORT, 2);
        const height = slashHeight(2);

        expect(pos.top + height).toBeLessThanOrEqual(VIEWPORT.height);
        expect(pos.top + height).toBeLessThanOrEqual(760); // 캐럿 줄을 가리지 않는다
    });

    test('항목이 많아 팝업이 길어도 위아래 어느 쪽으로든 화면 안에 있다', () => {
        for (const top of [40, 300, 700, 790]) {
            const pos = slashPosition(caret(40, top), VIEWPORT, 7);
            expect(pos.top).toBeGreaterThanOrEqual(0);
            expect(pos.top + slashHeight(7)).toBeLessThanOrEqual(VIEWPORT.height);
        }
    });

    test('오른쪽 끝에서 열어도 팝업이 화면 오른쪽 밖으로 나가지 않는다', () => {
        const pos = slashPosition(caret(370, 100), VIEWPORT, 7);

        expect(pos.left).toBeGreaterThanOrEqual(0);
        expect(pos.left + SLASH_WIDTH).toBeLessThanOrEqual(VIEWPORT.width);
    });

    test('왼쪽 끝에서도 화면 밖으로 밀리지 않는다', () => {
        expect(slashPosition(caret(-30, 100), VIEWPORT, 2).left).toBeGreaterThanOrEqual(0);
    });
});
