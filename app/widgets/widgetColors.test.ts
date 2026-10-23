import { readFileSync } from 'fs';
import { join } from 'path';

import { color, night } from '../src/theme/tokens';

// 시각 보충 §5.3 · REQ-60 — 위젯 함수는 import를 못 써 배경 색을 리터럴로 적는다. 그 값이 토큰 집합 안에 있는지 본다.
// LA는 배경을 칠하지 않으니 색 리터럴이 0개가 정상이다.

const HEX = /#[0-9A-Fa-f]{6}\b/g;
const TOKENS = new Set([...Object.values(color), ...Object.values(night)].map((v) => v.toUpperCase()));

const read = (name: string) => readFileSync(join(__dirname, name), 'utf8');
const literals = (text: string) => (text.match(HEX) ?? []).map((v) => v.toUpperCase());
const outside = (text: string) => literals(text).filter((v) => !TOKENS.has(v));

test('REQ-60 · 위젯·LA 파일의 색 리터럴은 토큰 값 집합 안에 있다', () => {
  expect(outside(read('ReadingWidget.tsx'))).toEqual([]);
  expect(literals(read('ReadingActivity.tsx'))).toEqual([]);
});

test("REQ-60 · 탐지기는 토큰 밖 색('#123456')을 심은 문자열을 잡고, 실제 위젯 파일에서 색 리터럴을 2개 이상 읽는다", () => {
  expect(outside(`containerBackground('#123456', 'widget')`)).toEqual(['#123456']);
  expect(literals(read('ReadingWidget.tsx')).length).toBeGreaterThanOrEqual(2);
});
