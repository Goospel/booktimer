import { clockLabel, dateLabel, durationLabel, objectParticle } from './format';

test('측정 시계는 H:MM:SS이고 1시간 전에는 M:SS다', () => {
  expect(clockLabel(5 * 60 + 7)).toBe('5:07');
  expect(clockLabel(3600 + 23 * 60 + 4)).toBe('1:23:04');
});

test('시간 글자는 분 단위로 내리고 60분부터 시간을 붙인다', () => {
  expect(durationLabel(59)).toBe('0분');
  expect(durationLabel(12 * 60 + 30)).toBe('12분');
  expect(durationLabel(3600)).toBe('1시간');
  expect(durationLabel(3600 + 5 * 60)).toBe('1시간 5분');
});

test('날짜 글자는 기기 날짜의 「M월 D일」이다', () => {
  expect(dateLabel(new Date(2026, 9, 3, 23, 59))).toBe('10월 3일');
});

test('목적격 조사는 제목 끝 글자의 받침을 따른다(괄호 · 문장부호는 떼고 본다)', () => {
  expect(objectParticle('데미안')).toBe('을');
  expect(objectParticle('미움받을 용기')).toBe('를');
  expect(objectParticle('1984')).toBe('를');
  expect(objectParticle('데미안 (큰글자책)')).toBe('을');
  expect(objectParticle('')).toBe('를');
});
