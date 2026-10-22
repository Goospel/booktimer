import { loginGate } from './loginGate';

// 시각 보충 §1.6 — Apple 창을 열기 전 관문(REQ-65).

test('REQ-65 · 시작 모드에서 나이 스위치가 꺼져 있으면 needAge다(Apple 창을 열지 않는다)', () => {
  expect(loginGate({ mode: 'start', ageOn: false, code: '' })).toBe('needAge');
  expect(loginGate({ mode: 'start', ageOn: false, code: 'ABCD1234' })).toBe('needAge');
});

test('REQ-65 · 웹 계정 모드에서 코드가 8자가 아니면 needCode이고, 나이 스위치는 보지 않는다', () => {
  expect(loginGate({ mode: 'link', ageOn: true, code: '' })).toBe('needCode');
  expect(loginGate({ mode: 'link', ageOn: true, code: 'ABCD123' })).toBe('needCode');
  expect(loginGate({ mode: 'link', ageOn: true, code: 'ABCD12345' })).toBe('needCode');
  expect(loginGate({ mode: 'link', ageOn: false, code: 'ABCD1234' })).toBe('ok');
});

test('REQ-65 · 시작 모드 스위치 켬 · 웹 계정 모드 코드 8자면 ok다', () => {
  expect(loginGate({ mode: 'start', ageOn: true, code: '' })).toBe('ok');
  expect(loginGate({ mode: 'link', ageOn: false, code: ' ABCD1234 ' })).toBe('ok'); // 앞뒤 공백은 붙여넣기 흔적
});
