import { fontReport } from './fontCheck';
import { font } from './tokens';

// D0 U-265 ①의 성공 신호는 이 함수에서만 나온다 — 개발 빌드 Metro 로그의 '[fonts] ok 4/4'.
test('REQ-470 · 글꼴 확인 줄 — 넷 다 있으면 [fonts] ok 4/4 · 하나 빠지면 missing과 그 이름 · 빈 목록이면 넷 다 missing', () => {
  const all = Object.values(font);
  expect(all).toHaveLength(4);
  expect(fontReport([...all, 'Helvetica'])).toBe('[fonts] ok 4/4');
  expect(fontReport(all.filter((n) => n !== font.sans600))).toBe(`[fonts] missing 1/4: ${font.sans600}`);
  expect(fontReport([])).toBe(`[fonts] missing 4/4: ${all.join(', ')}`);
});
