import { color, layout } from '../theme/tokens';
import { base } from './ui';

// 언어 1.4 · 1.7 — 누름 크기는 레이아웃으로 확보한다(hitSlop으로 채우지 않는다). REQ-56.
// 색은 toEqual로 견준다 — jest에서 color.*는 DynamicColorIOS 객체라 not.toBe는 객체 동일성만 봐서 늘 통과다.

test('REQ-56 · 키 높이는 하우징 안 44 · 폭 가득 48이고, 링크 · 행 · 스위치 행 · 분할 칸도 44 이상이다', () => {
  for (const s of [base.ink, base.outline]) {
    expect(s.minHeight).toBe(layout.hit);
    expect(s.minWidth).toBeGreaterThanOrEqual(layout.hit);
  }
  expect(layout.hit).toBe(44);
  expect(base.full.minHeight).toBe(layout.keyFull);
  expect(layout.keyFull).toBe(48);
  expect(base.text.minHeight).toBeGreaterThanOrEqual(layout.hit);
  expect(base.text.minWidth).toBeGreaterThanOrEqual(layout.hit);
  expect(base.row.minHeight).toBeGreaterThanOrEqual(layout.hit);
  expect(base.switchRow.minHeight).toBe(layout.keyFull);
  expect(base.segment.minHeight).toBeGreaterThanOrEqual(layout.hit);
  expect(base.segment.minWidth).toBeGreaterThanOrEqual(layout.hit);
});

test('REQ-56 · 입력칸은 높이 48 · 경계 1 mark이고 line이 아니다', () => {
  expect(base.field.minHeight).toBe(layout.keyFull);
  expect(base.field.borderWidth).toBe(1);
  expect(base.field.borderColor).toEqual(color.mark);
  expect(base.field.borderColor).not.toEqual(color.line);
});

test('REQ-56 · 글 행은 선을 그리지 않는다 — 선은 RowGroup이 행 사이에만(언어 1.7-12)', () => {
  expect(base.row).not.toHaveProperty('borderBottomWidth');
});
