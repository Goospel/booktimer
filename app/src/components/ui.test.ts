import { color, layout } from '../theme/tokens';
import { base } from './ui';

// 시각 보충 §3 · §4.3 — 누름 크기는 레이아웃으로 확보한다(hitSlop으로 채우지 않는다). REQ-56.

test('REQ-56 · 누름 부품 기본 스타일(Button ink·outline·text · Row · SwitchRow)의 최소 높이는 layout.hit 이상이고, 버튼 세 종류는 최소 폭도 layout.hit 이상이다', () => {
  for (const s of [base.ink, base.outline, base.text, base.row, base.switchRow]) {
    expect(s.minHeight).toBeGreaterThanOrEqual(layout.hit);
  }
  for (const s of [base.ink, base.outline, base.text]) {
    expect(s.minWidth).toBeGreaterThanOrEqual(layout.hit);
  }
});

test('REQ-56 · 입력칸 경계 색은 muted이고 가는 선(line)이 아니다', () => {
  expect(base.field.borderColor).toBe(color.muted);
  expect(base.field.borderColor).not.toBe(color.line);
  expect(base.field.borderWidth).toBeGreaterThan(0);
});
