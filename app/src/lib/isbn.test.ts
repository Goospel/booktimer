import { toIsbn13 } from './isbn';

// 바코드 → ISBN(v1 REQ-32). 978·979로 시작하고 체크 숫자가 맞는 EAN-13만 받는다.

test('REQ-32 · 체크 숫자가 틀린 EAN-13은 null', () => {
  expect(toIsbn13('9788936433598')).toBe('9788936433598'); // 양성 대조 — 맞는 체크 숫자
  expect(toIsbn13('9788936433597')).toBeNull();
});

test('REQ-32 · 880으로 시작하는 상품 바코드는 null', () => {
  expect(toIsbn13('8801234567893')).toBeNull(); // 체크 숫자는 맞다 — 접두사만으로 거른다
  expect(toIsbn13('978893643359')).toBeNull(); // 12자리
  expect(toIsbn13('97889364335X8')).toBeNull();
});

test('REQ-32 · 979 ISBN은 통과', () => {
  expect(toIsbn13('9791190090018')).toBe('9791190090018');
});
