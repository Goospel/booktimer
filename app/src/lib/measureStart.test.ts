jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));

import { ApiError, NetworkError } from '../api/client';
import type { Dashboard } from '../api/types';
import type { KV } from './draftSync';
import { type Ledger } from './measureLedger';
import { ALREADY_STOPPED, BUSY_ELSEWHERE, startFromBook, startMeasure, STOP_FAILED, stopMeasure } from './measureStart';
import { measureStore } from './measureSync';

// 타이머 오프라인 T2(설계 2026-11-02 §6.6) — 앱 측정의 시작 · 정지는 기기 원장에 먼저 적고 서버를 기다리지 않는다(REQ-357 · Q1).
// 웹에서 시작해 서버에 열린 측정의 정지만 지금처럼 서버 정지다(REQ-376 · REQ-151 유지).

const H = 60 * 60 * 1000;
const T = Date.UTC(2026, 10, 3, 1, 0);
const fetchMock = jest.fn();
global.fetch = fetchMock as unknown as typeof fetch;
const tick = () => new Promise((r) => setImmediate(r));
const never = () => new Promise<never>(() => undefined);

function memoryKV(): KV & { map: Map<string, string> } {
  const map = new Map<string, string>();
  return {
    map,
    getItem: async (k) => map.get(k) ?? null,
    setItem: async (k, v) => void map.set(k, v),
    removeItem: async (k) => void map.delete(k),
    getAllKeys: async () => [...map.keys()],
  };
}
/** d가 있으면 기준 값(디스크 — 확인된 값)도 둔다 */
async function openWith(ledger?: Ledger, d?: Dashboard) {
  const kv = memoryKV();
  if (ledger || d) kv.map.set('timer:1:state', JSON.stringify({ ledger, base: d && { d, at: T, sentAt: T - 300 } }));
  await measureStore.open(kv, 1);
  return kv;
}
const reading = (startedAt: number): Ledger => ({ active: { mode: 'reading', startedAt: new Date(startedAt).toISOString(), bookId: 1, bookTitle: '데미안' }, done: [] });

beforeEach(() => fetchMock.mockReset());
afterEach(async () => {
  jest.restoreAllMocks();
  await measureStore.close();
});

test('REQ-357 · 시작은 서버를 부르지 않고 원장에 진행 중으로 적힌 뒤 돌아온다(보내기 · 읽기가 영원히 매달려도)', async () => {
  await openWith();
  const load = jest.fn(never); // 보내기 → 다시 읽기가 끝나지 않는다(지하 「응답 없음」)
  let out: unknown = 'pending';
  void startMeasure({ store: measureStore, load, now: () => T }, { mode: 'reading', bookId: 1, bookTitle: '데미안', blocked: false }).then((r) => (out = r));
  await tick(); // 다음 매크로태스크 안 — 디스크 쓰기(몇 ms)만 기다린다
  expect(out).toEqual({});
  expect(measureStore.get()!.ledger.active).toEqual({ mode: 'reading', startedAt: new Date(T).toISOString(), bookId: 1, bookTitle: '데미안' });
  expect(load).toHaveBeenCalledTimes(1); // 대기 마감을 보낼 기회 — 기다리지 않는다
  expect(fetchMock).not.toHaveBeenCalled(); // 서버 시작 문이 없다(Q1 · REQ-377)
});

test('REQ-357 · 정지는 원장의 진행 중을 끝난 칸으로 옮긴 뒤 바로 돌아온다(서버 정지를 부르지 않는다)', async () => {
  await openWith({ active: { mode: 'study', startedAt: new Date(T).toISOString(), bookId: 9, bookTitle: '선형대수학' }, done: [] });
  const serverStop = jest.fn(async () => undefined);
  const load = jest.fn(never);
  let out: unknown = 'pending';
  void stopMeasure({ store: measureStore, load, serverStop, now: () => T + 90_000 }, 'study').then((r) => (out = r));
  await tick();
  expect(out).toEqual({});
  expect(serverStop).toHaveBeenCalledTimes(0);
  expect(load).toHaveBeenCalledTimes(1);
  expect(measureStore.get()!.ledger).toEqual({
    active: null,
    done: [{ mode: 'study', startedAt: new Date(T).toISOString(), endedAt: new Date(T + 90_000).toISOString(), bookId: 9, failedSince: null, settledAt: null }],
  });
});

test('REQ-357 · 보이는 대시보드에 다른 모드가 재는 중이면 원장에 적지 않고 BUSY_ELSEWHERE(REQ-114 문구) · 다시 읽기를 부른다', async () => {
  await openWith();
  const update = jest.spyOn(measureStore, 'update');
  const load = jest.fn(async () => undefined);
  expect(await startMeasure({ store: measureStore, load }, { mode: 'study', bookId: 3, bookTitle: '해석학', blocked: true })).toEqual({ error: BUSY_ELSEWHERE });
  expect(BUSY_ELSEWHERE).toBe('다른 곳에서 재는 측정이 있어요 — 그 측정을 멈추면 시작할 수 있어요');
  expect(update).toHaveBeenCalledTimes(0);
  expect(load).toHaveBeenCalledTimes(1); // 그 측정이 아직 있는지 다시 확인한다(지하면 그 읽기가 실패해 확인 안 된 값이 된다 — REQ-378)
  expect(measureStore.get()!.ledger.active).toBeNull();
  expect(fetchMock).not.toHaveBeenCalled();
});

/**
 * 거부하는 load — 돌려준 약속마다 거부 처리기(.catch · then의 둘째 인자 · await)가 붙었는지 잰다.
 * process 'unhandledRejection' 감시는 이 하니스(jest-expo)에서 양성 대조(.catch 뺀 꼴)도 0건이라 판정에 못 쓴다(2026-11-03 실측).
 */
function rejectingLoad() {
  const given: { handled: () => boolean }[] = [];
  const load = jest.fn(() => {
    const p = Promise.reject(new NetworkError()); // 오프라인 — 보내기 · 읽기 실패
    const then = jest.spyOn(p, 'then');
    given.push({ handled: () => then.mock.calls.some((c) => typeof c[1] === 'function') });
    return p;
  });
  return { load, unhandled: () => given.filter((g) => !g.handled()).length, count: () => given.length };
}

test('REQ-357 · 다시 읽기가 거부해도 시작 · 정지 결과는 같다', async () => {
  // 양성 대조 — 처리기 없이 버린 거부(void load())는 이 계측기가 잡는다
  const probe = rejectingLoad();
  const dropped = probe.load();
  expect(probe.unhandled()).toBe(1);
  dropped.catch(() => undefined); // 계측 뒤 정리(처리기가 붙어 0이 된다)
  expect(probe.unhandled()).toBe(0);

  await openWith();
  const r = rejectingLoad();
  expect(await startMeasure({ store: measureStore, load: r.load, now: () => T }, { mode: 'reading', bookId: null, bookTitle: null, blocked: false })).toEqual({});
  expect(measureStore.get()!.ledger.active).not.toBeNull();
  expect(await stopMeasure({ store: measureStore, load: r.load, serverStop: jest.fn(), now: () => T + 5_000 }, 'reading')).toEqual({});
  expect(measureStore.get()!.ledger).toMatchObject({ active: null, done: [{ mode: 'reading', bookId: null }] });
  expect(await startMeasure({ store: measureStore, load: r.load }, { mode: 'reading', bookId: null, bookTitle: null, blocked: true })).toEqual({ error: BUSY_ELSEWHERE });
  // 서버 정지 길(웹 시작 측정)도 다시 읽기가 거부해도 결과가 같다
  expect(await stopMeasure({ store: measureStore, load: r.load, serverStop: async () => undefined }, 'reading')).toEqual({});
  await tick();
  expect(r.count()).toBe(4);
  expect(r.unhandled()).toBe(0);
});

// PR-S1 사양 REQ-151 — 공부는 여러 기기에서 잰다. 다른 곳에서 끝낸 뒤 켜 둔 화면의 [측정 종료]는 오류가 아니라 안내다. 이제 웹 시작 측정의 정지에만 남는다.
test('REQ-376 · 이 기기 원장의 측정이 아니면 서버 정지 — 409면 「이미 끝난 측정이에요」 안내를 보이고 다시 읽는다', async () => {
  await openWith(); // 빈 원장 — 화면의 측정 중은 서버(웹)에서 시작한 측정이다
  const reload = jest.fn(async () => {});
  const stop = async () => {
    throw new ApiError(409, '요청에 실패했어요 (409)');
  };
  expect(await stopMeasure({ store: measureStore, load: reload, serverStop: stop }, 'reading')).toEqual({ info: '이미 끝난 측정이에요' });
  expect(ALREADY_STOPPED).toBe('이미 끝난 측정이에요');
  expect(reload).toHaveBeenCalledTimes(1);
  // 양성 대조 — 원장의 진행 중이 그 모드면 서버 정지를 부르지 않는다
  await measureStore.update((s) => ({ ...s, ledger: reading(Date.now() - 60_000) }), 1);
  const serverStop = jest.fn(async () => undefined);
  expect(await stopMeasure({ store: measureStore, load: reload, serverStop }, 'reading')).toEqual({});
  expect(serverStop).toHaveBeenCalledTimes(0);
});

test('REQ-376 · 그 밖의 서버 정지 실패는 서버·연결 문구(없으면 정해 둔 문구)를 보이고 다시 읽는다(REQ-151 유지)', async () => {
  await openWith();
  const reload = jest.fn(async () => {});
  const offline = async () => {
    throw new NetworkError();
  };
  expect(await stopMeasure({ store: measureStore, load: reload, serverStop: offline }, 'reading')).toEqual({ error: new NetworkError().message });
  const html500 = async () => {
    throw new ApiError(500, '요청에 실패했어요 (500)');
  };
  expect(await stopMeasure({ store: measureStore, load: reload, serverStop: html500 }, 'study')).toEqual({ error: STOP_FAILED });
  expect(STOP_FAILED).toBe('측정을 끝내지 못했어요 — 잠시 뒤 다시 눌러 주세요');
  expect(reload).toHaveBeenCalledTimes(2);
  // 성공도 다시 읽고 알림이 없다 · 다시 읽기가 거부해도 결과는 같다
  expect(await stopMeasure({ store: measureStore, load: reload, serverStop: async () => {} }, 'reading')).toEqual({});
  expect(reload).toHaveBeenCalledTimes(3);
  const failing = jest.fn(async () => {
    throw new NetworkError();
  });
  expect(await stopMeasure({ store: measureStore, load: failing, serverStop: async () => {} }, 'reading')).toEqual({});
});

test('REQ-376 · 6시간 지난 기기 진행 중은 같은 모드 정지를 기기 길로 잡지 않는다(서버 정지를 부른다)', async () => {
  const now = T + 6 * H + 1000;
  await openWith(reading(T)); // 6시간 1초 전 시작 — 보이는 값은 이미 끝난 몫이고, 보이는 [측정 종료]는 서버 측정의 것이다
  const serverStop = jest.fn(async () => undefined);
  expect(await stopMeasure({ store: measureStore, load: async () => undefined, serverStop, now: () => now }, 'reading')).toEqual({});
  expect(serverStop).toHaveBeenCalledTimes(1);
});

test('REQ-357 · 6시간 지난 기기 진행 중이 있어도 시작하면 그것을 먼저 끝내고 새 측정을 연다', async () => {
  await openWith(reading(T));
  const now = T + 6 * H + 1000;
  expect(await startMeasure({ store: measureStore, load: async () => undefined, now: () => now }, { mode: 'study', bookId: null, bookTitle: null, blocked: false })).toEqual({});
  const l = measureStore.get()!.ledger;
  expect(l.active).toMatchObject({ mode: 'study', startedAt: new Date(now).toISOString() });
  expect(l.done).toMatchObject([{ mode: 'reading', endedAt: new Date(T + 6 * H).toISOString() }]);
});

test('REQ-369 · 가게가 닫혀 있으면 시작 · 정지가 아무것도 적지 않고 오류 없이 끝난다', async () => {
  const kv = await openWith(reading(T));
  const before = kv.map.get('timer:1:state');
  await measureStore.close();
  const load = jest.fn(async () => undefined);
  const serverStop = jest.fn(async () => undefined);
  expect(await startMeasure({ store: measureStore, load }, { mode: 'reading', bookId: 1, bookTitle: '데미안', blocked: false })).toEqual({});
  expect(await stopMeasure({ store: measureStore, load, serverStop }, 'reading')).toEqual({});
  expect(serverStop).not.toHaveBeenCalled();
  expect(kv.map.get('timer:1:state')).toBe(before);
  // 닫히는 사이에 들어온 쓰기(get은 열림 · update는 닫힘)도 조용히 끝난다
  await measureStore.open(kv, 1);
  const store = { get: measureStore.get, update: () => measureStore.close().then(() => measureStore.update((s) => s, 1)) };
  expect(await startMeasure({ store, load }, { mode: 'study', bookId: null, bookTitle: null, blocked: false })).toEqual({});
});

// ── 책생글 A2 — 책 화면 [읽기 시작](REQ-275) ──
const book = (id: number, title: string) => ({ id, title, coverUrl: null, author: null });
const tenMinAgo = new Date(T - 10 * 60_000).toISOString(); // 표시 상한(6시간 10분) 안 — 서버가 아직 열어 둔 측정
function dash(p: Partial<Dashboard> = {}): Dashboard {
  return {
    todayGoalSeconds: 1800,
    todayReadSeconds: 0,
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    activeBook: null,
    readingBooks: [book(1, '데미안')],
    wantToReadBooks: [book(7, '싯다르타')],
    finishedBooks: [book(3, '수레바퀴 아래서')],
    recentBookId: 1,
    graph: { goalStreak: 0, weeks: [] },
    study: { hasActiveSession: false, activeStartedAt: null, activeBook: null, todaySeconds: 0, recentBookId: null, books: [] },
    ...p,
  };
}
const sid = { id: 7, title: '싯다르타' };

test('REQ-275 · 책 화면 읽기 시작 = 그 책으로 원장에 진행 중(서버 0) · started', async () => {
  await openWith(undefined, dash());
  const load = jest.fn(async () => undefined);
  expect(await startFromBook({ store: measureStore, load, now: () => T }, sid)).toEqual({ started: true });
  expect(measureStore.get()!.ledger.active).toEqual({ mode: 'reading', startedAt: new Date(T).toISOString(), bookId: 7, bookTitle: '싯다르타' });
  expect(fetchMock).not.toHaveBeenCalled();
  expect(load).toHaveBeenCalledTimes(1);
});

test('REQ-275 · 이 기기에서 독서(다른 책) · 공부를 재는 중이면 BUSY_ELSEWHERE · 원장 그대로 · started 거짓', async () => {
  const load = jest.fn(async () => undefined);
  // 기준 값 없음 — 원장 갈래
  await openWith(reading(T - 60_000));
  const before = measureStore.get()!.ledger;
  expect(await startFromBook({ store: measureStore, load, now: () => T }, sid)).toEqual({ error: BUSY_ELSEWHERE, started: false });
  expect(measureStore.get()!.ledger).toBe(before);
  await measureStore.close();
  // 기준 값 있음 — 보이는 대시보드 갈래(overlay가 공부 진행 중을 세운다)
  const studying: Ledger = { active: { mode: 'study', startedAt: new Date(T - 60_000).toISOString(), bookId: null, bookTitle: null }, done: [] };
  await openWith(studying, dash());
  expect(await startFromBook({ store: measureStore, load, now: () => T }, sid)).toEqual({ error: BUSY_ELSEWHERE, started: false });
  expect(measureStore.get()!.ledger.active).toMatchObject({ mode: 'study' });
});

test('REQ-275 · 확인된 서버 측정(웹에서 시작 — 독서 · 공부)도 막는다 · 확인 안 된 기준 값의 서버 측정은 막지 않는다(홈과 같은 판정 — REQ-378)', async () => {
  const load = jest.fn(async () => undefined);
  const deps = { store: measureStore, load, now: () => T };
  await openWith(undefined, dash({ hasActiveSession: true, activeStartedAt: tenMinAgo, activeBookTitle: '데미안', activeBook: book(1, '데미안') }));
  expect(await startFromBook(deps, sid)).toEqual({ error: BUSY_ELSEWHERE, started: false });
  await measureStore.close();
  await openWith(undefined, dash({ study: { hasActiveSession: true, activeStartedAt: tenMinAgo, activeBook: null, todaySeconds: 0, recentBookId: null, books: [] } }));
  expect(await startFromBook(deps, sid)).toEqual({ error: BUSY_ELSEWHERE, started: false });
  await measureStore.close();
  // 같은 독서 기준 값인데 이 실행의 읽기가 실패했다(확인 안 됨) — 서버 측정은 멈춘 것으로 보여 막지 않는다
  await openWith(undefined, dash({ hasActiveSession: true, activeStartedAt: tenMinAgo, activeBookTitle: '데미안', activeBook: book(1, '데미안') }));
  await measureStore.update((s) => ({ ...s, failedSeq: 1 }), 1);
  expect(await startFromBook(deps, sid)).toEqual({ started: true });
  expect(measureStore.get()!.ledger.active).toMatchObject({ mode: 'reading', bookId: 7 });
});

test('REQ-275 · 가게가 닫혔으면 아무것도 하지 않는다(started 거짓 · 오류 없음 — 홈으로 가지 않는다)', async () => {
  const load = jest.fn(async () => undefined);
  expect(await startFromBook({ store: measureStore, load, now: () => T }, sid)).toEqual({ started: false });
  expect(load).not.toHaveBeenCalled();
});
