// 띠 · 질문 시트의 보기 모양(PR-H 사양 REQ-137 · 139).
import type { QuestionLog, Round, Step } from '../api/types';
import { againArgs, bandHint, isPicked, roundTitle, sheetRounds } from './questions';

const round = (step: Step, n: number, focus: string | null = null): Round => ({
  step, at: `2026-10-24T0${n}:00:00Z`, focus, bodyChars: n * 10,
  questions: [{ text: `${step} ${n}번째 질문인가요?`, anchor: null }],
});

test('REQ-137 · 띠 빈 문구는 책이 없으면 책 없음 · 라운드 0에 보낼 여백이 0이면 여백 안내 · 그 밖은 「질문을 고르거나, 그냥 써도 돼요」다', () => {
  expect(bandHint({ rounds: 0, aiIncluded: 3, hasBook: false })).toBe('책이 지워진 글이라 첫 질문은 받을 수 없어요');
  expect(bandHint({ rounds: 0, aiIncluded: 0, hasBook: true })).toBe('이 책에 여백을 하나 남기면 질문을 받을 수 있어요');
  expect(bandHint({ rounds: 0, aiIncluded: 3, hasBook: true })).toBe('질문을 고르거나, 그냥 써도 돼요');
  // 라운드를 받은 뒤에는 책이 풀려도 · 여백이 0이어도 꼬리 질문은 된다
  expect(bandHint({ rounds: 2, aiIncluded: 0, hasBook: false })).toBe('질문을 고르거나, 그냥 써도 돼요');
});

test('REQ-139 · 펼치는 것은 마지막 라운드이고 지난 라운드는 최근 것부터 하나로 접는다', () => {
  const log: QuestionLog = { rounds: [round('ASK', 1), round('FOLLOW', 2), round('FOLLOW', 3)] };
  const { latest, past } = sheetRounds(log);
  expect(latest).toBe(log.rounds[2]);
  expect(past.map((r) => r.at)).toEqual([log.rounds[1].at, log.rounds[0].at]);
  expect(log.rounds.map((r) => r.bodyChars)).toEqual([10, 20, 30]); // 원본은 그대로(뒤집어 쓰지 않는다)
  expect(sheetRounds({ rounds: [] })).toEqual({ latest: null, past: [] });
  expect(roundTitle('ASK')).toBe('여백에서 받은 질문');
  expect(roundTitle('FOLLOW')).toBe('내 글에서 받은 꼬리 질문');
});

test('REQ-139 · 지금 답하는 질문(띠)과 글자가 같은 질문만 고른 표시를 받는다', () => {
  const q = { text: '한 정거장 앞에서 내렸을 때, 처음 눈에 들어온 것은 무엇이었나요?', anchor: '한 정거장 앞에서 내려 걸었다' };
  expect(isPicked(q, q.text)).toBe(true);
  expect(isPicked(q, '다른 질문인가요?')).toBe(false);
  expect(isPicked(q, null)).toBe(false);
});

test('REQ-139 · 질문 다시 받기는 마지막 라운드의 단계와 그 라운드의 focus를 쓴다', () => {
  expect(againArgs({ rounds: [round('ASK', 1), round('FOLLOW', 2, '띠에 있던 질문인가요?')] })).toEqual({ step: 'FOLLOW', focus: '띠에 있던 질문인가요?' });
  expect(againArgs({ rounds: [round('ASK', 1)] })).toEqual({ step: 'ASK', focus: null });
  expect(againArgs({ rounds: [] })).toBeNull();
});
