// 내보내기(v1 REQ-37) — 제목 + 본문뿐. 내보낸 표시는 공유 시트가 sharedAction을 돌려줄 때만.
import { Share } from 'react-native';

import { buildExport, shouldMarkExported } from './exportText';

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
