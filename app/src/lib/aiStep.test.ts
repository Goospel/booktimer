// 글쓰기 AI 보내기 순서 · 끊김 안전망 · 단추 상태 · 실패 문구(v1 REQ-36 · 47 · 62 · PR-H 사양 REQ-138 · 140).
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { ApiError, NetworkError } from '../api/client';
import type { AiView, DraftView, QuestionLog } from '../api/types';
import { aiButton, messageFor, sendAiStep } from './aiStep';
import { revisionOrThrow } from './draftSync';

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
      roundsBefore: 0,
      wait: async (ms: number) => void waits.push(ms),
    },
  };
}

const SAVE_FAIL = '글이 아직 저장되지 않아 질문을 보내지 못했어요 — 잠시 뒤 다시 눌러 주세요';
const NET = '인터넷에 연결되지 않았어요 — 쓴 글은 이 기기에 남아 있어요. 연결되면 다시 눌러 주세요';
const NOT_YET = '질문을 아직 받지 못했어요';
const GENERIC = '질문을 보내지 못했어요 — 잠시 뒤 다시 눌러 주세요';

describe('REQ-36 · 보내기 순서 · 안전망', () => {
  test('REQ-36 · 안전망은 시각 문자열이 아니라 라운드 수로 판정한다 — 서버가 마이크로초로 자른 같은 순간은 새 결과가 아니다', async () => {
    const old = draft({ aiUpdatedAt: '2026-10-24T01:49:12.770543Z', questions: log('옛 질문인가요?') });
    const h = harness({ post: async () => { throw new NetworkError(); }, reloads: [old, old, old] });
    // POST 응답 시각은 나노초('…770543300Z'), 다시 읽은 글은 datetime(6)('…770543Z') — 문자열로는 달라도 새 라운드가 아니다
    h.p.roundsBefore = 1;
    expect(await sendAiStep(h.p)).toEqual({ kind: 'retry', message: NOT_YET });
  });

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

  test('REQ-62 · 알 수 없는 오류는 연결 실패 문구로 떨어지지 않고 일반 문구가 된다', async () => {
    expect(messageFor(new TypeError('Cannot read properties of undefined'), 'send')).toBe(GENERIC);
    expect(messageFor(new ApiError(500, '요청에 실패했어요 (500)'), 'send')).toBe(GENERIC);
    // POST가 NetworkError도 ApiError도 아닌 것으로 실패하면 안전망(약 50초 뒤 연결 문구)으로 가지 않는다
    const h = harness({ post: async () => { throw new TypeError('x'); } });
    expect(await sendAiStep(h.p)).toEqual({ kind: 'error', message: GENERIC });
    expect(h.waits).toEqual([]);
    expect(h.calls).not.toContain('reload');
  });

  test('REQ-36 · 화면의 저장 결과 → revision 어댑터: 서버 문구 없는 저장 실패는 저장 실패 문구가 된다', () => {
    const caught = (o: Parameters<typeof revisionOrThrow>[0]): unknown => {
      try {
        revisionOrThrow(o);
      } catch (e) {
        return e;
      }
      throw new Error('던지지 않았다');
    };
    const generic = '글을 저장하지 못했어요 — 잠시 뒤 다시 해 주세요';
    expect(messageFor(caught({ kind: 'error', message: generic, cause: new ApiError(500, '요청에 실패했어요 (500)') }), 'save')).toBe(SAVE_FAIL);
    expect(messageFor(caught({ kind: 'error', message: generic }), 'save')).toBe(SAVE_FAIL);
    expect(messageFor(caught({ kind: 'error', message: '서버 문구', cause: new ApiError(400, '서버 문구') }), 'save')).toBe('서버 문구');
    expect(revisionOrThrow({ kind: 'saved', revision: 4 })).toEqual({ revision: 4 });
    expect(revisionOrThrow({ kind: 'mismatch', revision: 5 })).toEqual({ revision: 5 });
    expect(caught({ kind: 'offline' })).toBeInstanceOf(NetworkError);
    expect((caught({ kind: 'conflict' }) as ApiError).status).toBe(409);
    expect(caught({ kind: 'gone' })).toBeInstanceOf(Error);
    expect(caught(null)).toBeInstanceOf(Error);
  });
});

test('REQ-140 · /ai가 403이면 알림 문구가 아니라 동의 상태를 낸다', async () => {
  const h = harness({ post: async () => { throw new ApiError(403, 'AI 질문을 받으려면 안내를 읽고 동의해 주세요'); } });
  expect(await sendAiStep(h.p)).toEqual({ kind: 'consent' });
});

describe('REQ-138 · 아래 막대 AI 단추', () => {
  const base = { rounds: 0, quote: null as string | null, thought: '남은 생각', boxes: 0 };

  test('REQ-138 · 라운드 0이면 질문 받기(ASK), 1 이상이면 꼬리 질문(FOLLOW)이다', () => {
    expect(aiButton(base)).toEqual({ step: 'ASK', label: '질문 받기', disabled: false });
    expect(aiButton({ ...base, rounds: 1, thought: 'ㄱ'.repeat(30) })).toEqual({ step: 'FOLLOW', label: '꼬리 질문', disabled: false });
  });

  test('REQ-138 · 꼬리 질문은 앞뒤 공백 뺀 본문 29자면 꺼지고 30자면 켜진다', () => {
    const at = (thought: string) => aiButton({ ...base, rounds: 1, thought }).disabled;
    expect(at('가'.repeat(29))).toBe(true);
    expect(at('가'.repeat(30))).toBe(false);
    expect(at(`     ${'가'.repeat(29)}     `)).toBe(true); // 29자 + 앞뒤 공백 10 — 서버 strip()과 같다
    expect(at(`     ${'가'.repeat(30)}     `)).toBe(false);
  });

  test('REQ-138 · 라운드 8이면 꺼지고 7이면 켜진다', () => {
    const thought = '가'.repeat(40);
    expect(aiButton({ ...base, rounds: 8, thought }).disabled).toBe(true);
    expect(aiButton({ ...base, rounds: 7, thought }).disabled).toBe(false);
  });

  // J98 — 묻는 대상은 지금 쪽지다: 책 유무 · 같은 책 다른 쪽지 수와 무관하게 지금 쪽지에 읽을 것이 있으면 켜진다.
  test('REQ-225 · 질문 받기는 지금 쪽지의 글만 본다 — 생각만 · 옮긴 문장만 · 박스만이어도 켜짐 · 빈 쪽지 · 문장부호뿐이면 꺼짐', () => {
    const off = (p: Partial<typeof base>) => aiButton({ ...base, ...p }).disabled;
    expect(off({ thought: '남은 생각' })).toBe(false); // 생각만(책 없는 쪽지도 같은 입력)
    expect(off({ thought: '', quote: '새는 알에서 나오려고 투쟁한다.' })).toBe(false); // 옮긴 문장만
    expect(off({ thought: '.', boxes: 1 })).toBe(false); // 건 블로그 문장 박스만(B1)
    expect(off({ thought: '' })).toBe(true); // 빈 쪽지
    expect(off({ thought: '  …?! ', quote: '   ' })).toBe(true); // 문장부호 · 공백뿐
  });
});
