// 내보내기(v1 REQ-37) — 제목 + 본문뿐. 내보낸 표시는 공유 시트가 sharedAction을 돌려줄 때만.
import { Share } from 'react-native';

import { bookSourceLine, buildExport, buildNoteExport, shouldMarkExported } from './exportText';

describe('REQ-227 · 쪽지 내보내기 글자(C8 · J78)', () => {
  const question = '이 문장을 다시 읽었을 때 무엇이 먼저 떠올랐나요?';
  const full = {
    quote: '새는 알에서 나오려고 투쟁한다.', quoteSource: '『데미안』 · 헤르만 헤세', text: '알을 깨는 건 매일의 일이다.',
    bookTitle: '데미안', questions: { rounds: [{ step: 'ASK', questions: [{ text: question, anchor: null }] }] },
  };

  test('REQ-227 · 옮긴 문장 + 『책』 줄 + 빈 줄 + 생각 · AI 질문 글자 없음', () => {
    const out = buildNoteExport(full);
    expect(out).toBe('「새는 알에서 나오려고 투쟁한다.」\n『데미안』 · 헤르만 헤세\n\n알을 깨는 건 매일의 일이다.');
    expect(out).not.toContain(question);
  });

  test('REQ-227 · 책 없는 쪽지(옮긴 문장 없음)는 책 줄 없이 생각만', () => {
    expect(buildNoteExport({ quote: null, quoteSource: null, text: '빈 공간에 끄적인 글' })).toBe('빈 공간에 끄적인 글');
  });

  test('REQ-227 · 생각이 빈 쪽지는 옮긴 문장 · 책 줄만(끝 빈 줄 없음)', () => {
    expect(buildNoteExport({ ...full, text: '  ' })).toBe('「새는 알에서 나오려고 투쟁한다.」\n『데미안』 · 헤르만 헤세');
  });

  test('REQ-227 · 책 쪽지의 책 줄 = 서버 Story.sourceLine과 같은 꼴(저자가 없으면 『제목』만)', () => {
    expect(bookSourceLine({ title: '데미안', author: '헤르만 헤세' })).toBe('『데미안』 · 헤르만 헤세');
    expect(bookSourceLine({ title: '손으로 넣은 책', author: null })).toBe('『손으로 넣은 책』');
    expect(bookSourceLine({ title: '빈 저자', author: '' })).toBe('『빈 저자』');
  });
});

test('REQ-37 · 내보내는 글은 제목과 본문뿐이고 질문 기록의 글자가 없다', () => {
  const question = '익숙한 길을 벗어나 본 날, 가장 먼저 들린 것은 무엇이었나요?';
  const draft = { title: '익숙한 길에서 벗어나기', body: '퇴근길을 바꿔 본 건\n이 문장 때문이었다.', questions: { rounds: [{ questions: [{ text: question }] }] } };
  const out = buildExport(draft);
  expect(out).toBe('익숙한 길에서 벗어나기\n\n퇴근길을 바꿔 본 건\n이 문장 때문이었다.'); // 양성 대조 — 제목 · 본문은 있다
  expect(out).not.toContain(question);
  expect(buildExport({ title: null, body: '본문만' })).toBe('본문만');
  expect(buildExport({ title: '   ', body: '본문만' })).toBe('본문만');
});

test('REQ-37 · sharedAction일 때만 exported를 보낸다', () => {
  expect(shouldMarkExported({ action: Share.sharedAction })).toBe(true);
});

test('REQ-37 · dismissedAction이면 보내지 않는다', () => {
  expect(shouldMarkExported({ action: Share.dismissedAction })).toBe(false);
});
