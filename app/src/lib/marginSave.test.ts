// 여백 저장 단계(v1 REQ-44 · REQ-96 — PR-G 사양 §5.6 · §5.7 · §11-1).
// 확인 판정의 「생각」(trim)과 목록 그리기의 「생각」(showsThought)은 일부러 다르다.
import { canSaveMargin, marginSaveStep, savedNotice, showsThought } from './marginSave';

test('REQ-44 · 공개 책 첫 저장은 confirm', () => {
  expect(marginSaveStep({ isPublic: true, thought: '좋다', margins: [] })).toBe('confirm');
  // 인용만 남긴 여백(남에게 안 보임)만 있으면 아직 「첫」 저장이다
  expect(marginSaveStep({ isPublic: true, thought: '좋다', margins: [{ text: '' }, { text: '  ' }] })).toBe('confirm');
});

test('REQ-44 · 같은 책 두 번째부터는 save', () => {
  expect(marginSaveStep({ isPublic: true, thought: '또 좋다', margins: [{ text: '처음 남긴 생각' }] })).toBe('save');
});

test('REQ-44 · 비공개 책은 save', () => {
  expect(marginSaveStep({ isPublic: false, thought: '좋다', margins: [] })).toBe('save');
});

test('REQ-96 · 인용만으로 저장할 수 있다', () => {
  expect(canSaveMargin({ quote: '문장', thought: '   ' })).toBe(true);
  expect(canSaveMargin({ quote: '', thought: '생각' })).toBe(true);
  expect(canSaveMargin({ quote: '  ', thought: '\n ' })).toBe(false);
});

test('REQ-96 · 공개 책 확인은 생각이 있을 때만 뜬다', () => {
  expect(marginSaveStep({ isPublic: true, thought: '', margins: [] })).toBe('save');
  expect(marginSaveStep({ isPublic: true, thought: '  ', margins: [] })).toBe('save');
  // 「.」 하나도 서버에 남아 남에게 보인다 — 확인을 띄운다
  expect(marginSaveStep({ isPublic: true, thought: '.', margins: [] })).toBe('confirm');
});

test('REQ-96 · 문장부호뿐인 생각은 옮긴 문장만 그린다', () => {
  for (const t of ['', ' ', '.', '…', '?!']) expect(showsThought(t)).toBe(false);
  for (const t of ['ㅎ', '좋다.', '😊']) expect(showsThought(t)).toBe(true);
});

test('남긴 뒤 알림 표시는 한 번만 읽힌다', () => {
  expect(savedNotice.take()).toBe(false);
  savedNotice.mark();
  expect(savedNotice.take()).toBe(true);
  expect(savedNotice.take()).toBe(false);
});
