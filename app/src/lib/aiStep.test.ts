// 글쓰기 AI 보내기 순서 · 끊김 안전망 · 단추 상태 · 실패 문구(v1 REQ-36 · 47 · 62 · PR-H 사양 REQ-138 · 140).
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { ApiError, NetworkError } from '../api/client';
import type { AiView, DraftView, QuestionLog } from '../api/types';
import { aiButton, messageFor, sendAiStep } from './aiStep';

const log = (text: string): QuestionLog => ({
  rounds: [{ step: 'ASK', at: '2026-10-24T00:00:00Z', focus: null, bodyChars: 0, questions: [{ text, anchor: null }] }],
});
const aiView = (text = '첫 질문인가요?', at = '2026-10-24T01:00:00Z'): AiView => ({ questions: log(text), aiUpdatedAt: at, aiRemainingToday: 19 });
const draft = (p: Partial<DraftView> = {}): DraftView => ({
  id: 7, bookId: 1, bookTitle: '데미안', title: '제목', body: '본문', revision: 3,
  questions: { rounds: [] }, aiUpdatedAt: null, aiRemainingToday: 20, aiConsent: true, ...p,
});

function harness(o: { flush?: () => Promise<{ revision: number }>; post?: (r: number) => Promise<AiView>; reloads?: (DraftView | Error)[] } = {}) {
  const calls: string[] = [];
  const waits: number[] = [];
  const reloads = [...(o.reloads ?? [])];
  return {
    calls,
    waits,
    p: {
      blur: () => void calls.push('blur'),
      flush: async () => {
        calls.push('flush');
        return o.flush ? o.flush() : { revision: 7 };
      },
      post: async (revision: number) => {
        calls.push(`post(${revision})`);
        return o.post ? o.post(revision) : aiView();
      },
      reload: async () => {
        calls.push('reload');
        const next = reloads.shift() ?? draft();
        if (next instanceof Error) throw next;
        return next;
      },
      since: null as string | null,
      wait: async (ms: number) => void waits.push(ms),
    },
  };
}

const SAVE_FAIL = '글이 아직 저장되지 않아 질문을 보내지 못했어요 — 잠시 뒤 다시 눌러 주세요';
const NET = '인터넷에 연결되지 않았어요 — 쓴 글은 이 기기에 남아 있어요. 연결되면 다시 눌러 주세요';
const NOT_YET = '질문을 아직 받지 못했어요';
const GENERIC = '질문을 보내지 못했어요 — 잠시 뒤 다시 눌러 주세요';

describe('REQ-36 · 보내기 순서 · 안전망', () => {
  test('REQ-36 · 보내기는 blur → 저장 비우기 → POST 순서이고 POST의 revision은 저장 응답의 revision이다', async () => {
    const h = harness();
    const r = await sendAiStep(h.p);
    expect(h.calls).toEqual(['blur', 'flush', 'post(7)']);
    expect(r.kind).toBe('done');
  });

  test('REQ-36 · 저장이 실패하면 POST하지 않는다', async () => {
    const h = harness({ flush: async () => { throw new Error('저장 실패'); } });
    const r = await sendAiStep(h.p);
    expect(h.calls).not.toContain('post(7)');
    expect(h.calls.some((c) => c.startsWith('post'))).toBe(false);
    expect(r).toEqual({ kind: 'error', message: SAVE_FAIL });
  });

  test('REQ-36 · 시간 초과 뒤 aiUpdatedAt이 바뀌면 결과를 보여 준다', async () => {
    const h = harness({
      post: async () => { throw new NetworkError(); },
      reloads: [draft(), draft({ aiUpdatedAt: '2026-10-24T02:00:00Z', questions: log('끊긴 뒤 받은 질문인가요?'), aiRemainingToday: 18 })],
    });
    const r = await sendAiStep(h.p);
    expect(r).toEqual({ kind: 'done', questions: log('끊긴 뒤 받은 질문인가요?'), aiUpdatedAt: '2026-10-24T02:00:00Z', aiRemainingToday: 18 });
    expect(h.waits).toEqual([5000, 15000]);
  });

  test('REQ-36 · 세 번 다 그대로면 다시 시도 상태', async () => {
    const same = draft({ aiUpdatedAt: '2026-10-24T00:00:00Z' });
    const h2 = harness({ post: async () => { throw new NetworkError(); }, reloads: [same, same, same] });
    h2.p.since = '2026-10-24T00:00:00Z';
    const r = await sendAiStep(h2.p);
    expect(r).toEqual({ kind: 'retry', message: NOT_YET });
    expect(h2.waits).toEqual([5000, 15000, 30000]);
    expect(h2.calls.filter((c) => c === 'reload')).toHaveLength(3);
  });

  test('REQ-36 · 409(판이 다름)면 저장을 한 번 더 비우고 한 번만 다시 보낸다', async () => {
    let n = 0;
    const once = harness({ post: async () => { if (n++ === 0) throw new ApiError(409, '글이 아직 저장되지 않았어요 — 저장 뒤 다시 눌러 주세요'); return aiView(); } });
    expect((await sendAiStep(once.p)).kind).toBe('done');
    expect(once.calls).toEqual(['blur', 'flush', 'post(7)', 'flush', 'post(7)']);

    const twice = harness({ post: async () => { throw new ApiError(409, '글이 아직 저장되지 않았어요 — 저장 뒤 다시 눌러 주세요'); } });
    const r = await sendAiStep(twice.p);
    expect(r).toEqual({ kind: 'error', message: '글이 아직 저장되지 않았어요 — 저장 뒤 다시 눌러 주세요' });
    expect(twice.calls.filter((c) => c.startsWith('post'))).toHaveLength(2);
  });
});

test('REQ-47 · 끊김 안전망은 다시 불러온 글의 본문을 내지 않고 AI 필드만 낸다', async () => {
  const h = harness({
    post: async () => { throw new NetworkError(); },
    reloads: [draft({ body: '본문표식', title: '제목표식', aiUpdatedAt: '2026-10-24T02:00:00Z', questions: log('질문표식인가요?') })],
  });
  const r = await sendAiStep(h.p);
  const json = JSON.stringify(r);
  expect(json).toContain('질문표식인가요?'); // 양성 대조 — 결과는 실제로 실렸다
  expect(json).not.toContain('본문표식');
  expect(json).not.toContain('제목표식');
  expect(Object.keys(r)).not.toContain('body');
  expect(Object.keys(r)).not.toContain('title');
  expect(Object.keys(r)).not.toContain('revision');
});

describe('REQ-62 · 실패 문구', () => {
  test('REQ-62 · 서버 문구가 있으면 그대로 쓴다(오류 종류와 무관)', () => {
    const texts = [
      [429, '오늘 받을 수 있는 AI 질문을 다 받았어요 — 한국 시간 오전 9시에 다시 채워져요'],
      [503, '이번 달 AI 질문이 모두 쓰였어요 — 다음 달에 다시 열려요'],
      [422, '이 내용으로는 AI가 질문을 만들기 어려워요 — 글은 그대로 저장돼 있어요'],
    ] as const;
    for (const [status, text] of texts) {
      expect(messageFor(new ApiError(status, text), 'send')).toBe(text);
      expect(messageFor(new ApiError(status, text), 'save')).toBe(text);
    }
  });

  test('REQ-62 · 서버 문구가 없으면 연결 실패 · 안전망 끝 · 저장 실패마다 정해 둔 문구를 준다', async () => {
    expect(messageFor(new NetworkError(), 'send')).toBe(NET);
    expect(messageFor(new ApiError(500, '요청에 실패했어요 (500)'), 'save')).toBe(SAVE_FAIL);
    const h = harness({ post: async () => { throw new NetworkError(); } });
    expect(await sendAiStep(h.p)).toEqual({ kind: 'retry', message: NOT_YET });
    // 다시 읽기도 셋 다 연결 실패면 연결 문구
    const off = harness({ post: async () => { throw new NetworkError(); }, reloads: [new NetworkError(), new NetworkError(), new NetworkError()] });
    expect(await sendAiStep(off.p)).toEqual({ kind: 'error', message: NET });
  });

  test('REQ-62 · 알 수 없는 오류는 연결 실패 문구로 떨어지지 않고 일반 문구가 된다', () => {
    expect(messageFor(new TypeError('Cannot read properties of undefined'), 'send')).toBe(GENERIC);
    expect(messageFor(new ApiError(500, '요청에 실패했어요 (500)'), 'send')).toBe(GENERIC);
  });
});

test('REQ-140 · /ai가 403이면 알림 문구가 아니라 동의 상태를 낸다', async () => {
  const h = harness({ post: async () => { throw new ApiError(403, 'AI 질문을 받으려면 안내를 읽고 동의해 주세요'); } });
  expect(await sendAiStep(h.p)).toEqual({ kind: 'consent' });
});

describe('REQ-138 · 아래 막대 AI 단추', () => {
  const base = { rounds: 0, body: '', aiIncluded: 3, hasBook: true };

  test('REQ-138 · 라운드 0이면 질문 받기(ASK), 1 이상이면 꼬리 질문(FOLLOW)이다', () => {
    expect(aiButton(base)).toEqual({ step: 'ASK', label: '질문 받기', disabled: false });
    expect(aiButton({ ...base, rounds: 1, body: 'ㄱ'.repeat(30) })).toEqual({ step: 'FOLLOW', label: '꼬리 질문', disabled: false });
  });

  test('REQ-138 · 꼬리 질문은 앞뒤 공백 뺀 본문 29자면 꺼지고 30자면 켜진다', () => {
    const at = (body: string) => aiButton({ ...base, rounds: 1, body }).disabled;
    expect(at('가'.repeat(29))).toBe(true);
    expect(at('가'.repeat(30))).toBe(false);
    expect(at(`     ${'가'.repeat(29)}     `)).toBe(true); // 29자 + 앞뒤 공백 10 — 서버 strip()과 같다
    expect(at(`     ${'가'.repeat(30)}     `)).toBe(false);
  });

  test('REQ-138 · 라운드 8이면 꺼지고 7이면 켜진다', () => {
    const body = '가'.repeat(40);
    expect(aiButton({ ...base, rounds: 8, body }).disabled).toBe(true);
    expect(aiButton({ ...base, rounds: 7, body }).disabled).toBe(false);
  });

  test('REQ-138 · 질문 받기는 보낼 여백이 0개이거나 책이 없으면 꺼진다', () => {
    expect(aiButton({ ...base, aiIncluded: 0 }).disabled).toBe(true);
    expect(aiButton({ ...base, hasBook: false }).disabled).toBe(true);
    expect(aiButton(base).disabled).toBe(false);
  });
});
