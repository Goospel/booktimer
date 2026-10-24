// AI 안내 · 동의 시트 문구(v1 REQ-39 · R-07 · R-11) + 질문을 그리는 컴포넌트의 소스 가드(REQ-39 배지 · REQ-47 선택 · 복사 금지).
import { readFileSync } from 'fs';
import { resolve } from 'path';

import { CONSENT_ITEMS, CONSENT_LITERALS, CONSENT_TITLE, missingLiterals } from './consentCopy';

const sheetText = [CONSENT_TITLE, ...CONSENT_ITEMS].join('\n');

test('REQ-39 · 동의 시트 문구에 리터럴 11개가 모두 있다', () => {
  expect(CONSENT_LITERALS).toHaveLength(11);
  expect(missingLiterals(sheetText)).toEqual([]);
  expect(CONSENT_ITEMS).toHaveLength(7);
});

test('REQ-39 · 하나를 뺀 픽스처는 그 리터럴을 돌려준다(양성 대조)', () => {
  expect(missingLiterals(sheetText.replace('최대 2년', '오래'))).toEqual(['최대 2년']);
  expect(missingLiterals(sheetText.replace('질문만 해요', '질문해요'))).toEqual(['질문만 해요']);
});

// 질문을 그리는 두 파일만 본다 — 설정 탭 빌드 줄은 정당하게 selectable이다(사양 §3).
const COMPONENTS = ['QuestionSheet', 'QuestionBand'].map((n) => ({
  name: n,
  text: readFileSync(resolve(__dirname, '..', 'components', `${n}.tsx`), 'utf8'),
}));
const BADGE = /<AiBadge\b/;
const COPY = /<\w+[^>]*\sselectable\b|\bClipboard\.\w|['"]expo-clipboard['"]/;

test('REQ-39 · AI 결과를 그리는 컴포넌트(QuestionSheet·QuestionBand)는 AiBadge를 포함한다', () => {
  for (const c of COMPONENTS) expect([c.name, BADGE.test(c.text)]).toEqual([c.name, true]);
  expect(BADGE.test(COMPONENTS[0].text.replace(/<AiBadge\b/g, '<Badge'))).toBe(false); // 양성 대조
});

test('REQ-47 · 질문을 그리는 컴포넌트(QuestionSheet·QuestionBand)에 글자 선택·복사 길이 없다(심은 픽스처는 잡힌다)', () => {
  for (const c of COMPONENTS) expect([c.name, COPY.test(c.text)]).toEqual([c.name, false]);
  expect(COPY.test('<Txt v="Q1" selectable>{q.text}</Txt>')).toBe(true);
  expect(COPY.test("import * as Clipboard from 'expo-clipboard';")).toBe(true);
  expect(COPY.test('Clipboard.setStringAsync(q.text)')).toBe(true);
});
