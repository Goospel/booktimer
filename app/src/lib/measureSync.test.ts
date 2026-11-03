// 타이머 오프라인 T0 · T2(설계 2026-11-02 §6.5) — 마지막 대시보드와 기기 원장을 사용자별 칸 하나에 두는 가게 · 대시보드 읽기 · 보내기 차례.
// KV는 AsyncStorage 꼴의 Map 가짜.
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { ApiError, NetworkError, reading, study, UnauthorizedError } from '../api/client';
import type { Dashboard } from '../api/types';
import { clearAll, type KV } from './draftSync';
import { type DoneMeasure, EMPTY_LEDGER, type Ledger } from './measureLedger';
import { confirmedOf, flushMeasures, loadDashboard, measureNotice, MeasureStoreClosed, measureStore } from './measureSync';
import { effectiveDashboard } from './visibleDashboard';

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
function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((ok, fail) => ((resolve = ok), (reject = fail)));
  return { promise, resolve, reject };
}
const tick = () => new Promise((r) => setImmediate(r));
// 잔디(weeks)가 없는 대시보드 — 자정 다시 읽기(REQ-346)가 끼지 않는다. 값마다 오늘 몫을 다르게 둔다(어느 응답이 남았는지 가려진다)
const dash = (todayReadSeconds: number) => ({ todayReadSeconds, todayGoalSeconds: 1800, hasActiveSession: false, graph: {}, study: {} }) as unknown as Dashboard;

afterEach(async () => {
  jest.restoreAllMocks();
  await measureStore.close();
});

test('REQ-362 · 마지막 대시보드는 사용자별 칸 하나(timer:{uid}:state)에 쓰고 다시 열면 첫 값이 된다 · 다른 사용자 칸은 읽지 않는다', async () => {
  const kv = memoryKV();
  await measureStore.open(kv, 1);
  expect(measureStore.get()).toEqual({ userId: 1, base: null, ledger: EMPTY_LEDGER, failedSeq: 0 });
  jest.spyOn(reading, 'dashboard').mockResolvedValueOnce(dash(600));
  await loadDashboard();
  expect([...kv.map.keys()]).toEqual(['timer:1:state']);
  expect(JSON.parse(kv.map.get('timer:1:state')!).base.d).toEqual(dash(600));

  await measureStore.close();
  await measureStore.open(kv, 1); // 앱을 다시 켬(오프라인) — 디스크 값이 첫 값이다. 순번은 0(이 실행에서 읽은 값이 아니다)
  expect(measureStore.get()!.base).toMatchObject({ d: dash(600), seq: 0 });

  await measureStore.close();
  await measureStore.open(kv, 2); // 다른 사용자는 앞 사람 칸을 열지 않는다
  expect(measureStore.get()).toEqual({ userId: 2, base: null, ledger: EMPTY_LEDGER, failedSeq: 0 });
});

test('REQ-362 · 이 실행에서 더 늦게 보낸 요청의 응답만 받는다 · 디스크 값은 어떤 새 응답에도 진다', async () => {
  const kv = memoryKV();
  kv.map.set('timer:1:state', JSON.stringify({ base: { d: dash(60), at: 9e12, sentAt: 9e12 } })); // 디스크 값 — 벽시계로는 아주 늦다
  await measureStore.open(kv, 1);
  const first = deferred<Dashboard>();
  const second = deferred<Dashboard>();
  const get = jest.spyOn(reading, 'dashboard').mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
  let clock = 2_000_000;
  jest.spyOn(Date, 'now').mockImplementation(() => clock);
  const a = loadDashboard();
  await tick();
  clock = 1_000_000; // 기기 시계가 뒤로 갔다 — 벽시계로 비교하면 먼저 보낸 a가 이긴다
  const b = loadDashboard();
  await tick();
  expect(get).toHaveBeenCalledTimes(2);
  second.resolve(dash(1200)); // 늦게 보낸 쪽이 먼저 온다
  await b;
  expect(measureStore.get()!.base!.d).toEqual(dash(1200));
  const won = measureStore.get();
  const notified = jest.fn();
  const unsub = measureStore.subscribe(notified);
  const setItem = jest.spyOn(kv, 'setItem');
  first.resolve(dash(600)); // 먼저 보낸 쪽이 늦게 와도 덮지 않는다
  await a;
  unsub();
  expect(measureStore.get()!.base!.d).toEqual(dash(1200));
  expect(JSON.parse(kv.map.get('timer:1:state')!).base.d).toEqual(dash(1200));
  // 진 응답은 아무것도 바꾸지 않는다 — 칸에 다시 쓰지도 알리지도 않는다(§6.5 「바뀐 때만」 · 리뷰 반영)
  expect(measureStore.get()).toBe(won);
  expect(setItem).not.toHaveBeenCalled();
  expect(notified).not.toHaveBeenCalled();
});

test('REQ-362 · 디스크 칸을 못 읽거나 깨져 있어도 빈 기준 값으로 연다(열리지 않으면 홈이 「불러오는 중」에 멈춘다)', async () => {
  const kv = memoryKV();
  kv.getItem = async () => {
    throw new Error('disk');
  };
  await measureStore.open(kv, 1);
  expect(measureStore.get()).toEqual({ userId: 1, base: null, ledger: EMPTY_LEDGER, failedSeq: 0 });
  await measureStore.close();
  const broken = memoryKV();
  broken.map.set('timer:1:state', '{깨짐');
  await measureStore.open(broken, 1);
  expect(measureStore.get()).toEqual({ userId: 1, base: null, ledger: EMPTY_LEDGER, failedSeq: 0 });
});

test('REQ-362 · 가게가 안 열렸으면 대시보드를 읽지 않는다', async () => {
  const get = jest.spyOn(reading, 'dashboard').mockResolvedValue(dash(600));
  await loadDashboard();
  expect(get).toHaveBeenCalledTimes(0);
  // 양성 대조 — 열리면 읽는다
  await measureStore.open(memoryKV(), 1);
  await loadDashboard();
  expect(get).toHaveBeenCalledTimes(1);
});

test('REQ-362 · 읽기가 실패하면 던지고 기준 값은 그대로다', async () => {
  const kv = memoryKV();
  kv.map.set('timer:1:state', JSON.stringify({ base: { d: dash(60), at: 1, sentAt: 1 } }));
  await measureStore.open(kv, 1);
  jest.spyOn(reading, 'dashboard').mockRejectedValueOnce(new NetworkError());
  await expect(loadDashboard()).rejects.toBeInstanceOf(NetworkError);
  expect(measureStore.get()!.base!.d).toEqual(dash(60));
});

test('REQ-369 · 가게가 열리기 전 · 닫힌 뒤엔 get이 null이고 update가 실패한다 · 다른 사용자로 열린 가게엔 앞 사용자의 쓰기가 실패한다', async () => {
  expect(measureStore.get()).toBeNull();
  await expect(measureStore.update((s) => s, 1)).rejects.toBeInstanceOf(MeasureStoreClosed);

  const kv = memoryKV();
  await measureStore.open(kv, 1);
  const before = measureStore.get();
  expect(before).not.toBeNull();
  await measureStore.close();
  expect(measureStore.get()).toBeNull();
  await expect(measureStore.update((s) => s, 1)).rejects.toBeInstanceOf(MeasureStoreClosed);

  // 로그아웃 → 다른 계정 로그인 사이에 늦게 온 앞 사용자의 응답 — 새 사용자 칸에 들어가지 않고, 읽기는 조용히 끝난다
  await measureStore.open(kv, 1);
  const late = deferred<Dashboard>();
  jest.spyOn(reading, 'dashboard').mockReturnValueOnce(late.promise);
  const load = loadDashboard();
  await tick();
  await measureStore.close();
  await measureStore.open(kv, 2);
  late.resolve(dash(600));
  await expect(load).resolves.toBeUndefined();
  expect(measureStore.get()).toEqual({ userId: 2, base: null, ledger: EMPTY_LEDGER, failedSeq: 0 });
  expect(kv.map.has('timer:2:state')).toBe(false);
  await expect(measureStore.update((s) => ({ ...s, base: null }), 1)).rejects.toBeInstanceOf(MeasureStoreClosed);
  // 양성 대조 — 지금 사용자의 쓰기는 된다
  await expect(measureStore.update((s) => ({ ...s, base: { d: dash(5), at: 1, sentAt: 1, seq: 0 } }), 2)).resolves.toMatchObject({ userId: 2 });
  expect(kv.map.has('timer:2:state')).toBe(true);
});

test('REQ-364 · close는 줄에 선 쓰기가 끝난 뒤 돌아온다 — 그 뒤 지운 칸이 되살아나지 않는다', async () => {
  const kv = memoryKV();
  const gate = deferred<void>();
  const setItem = kv.setItem;
  kv.setItem = async (k, v) => {
    await gate.promise; // 디스크 쓰기가 매달려 있다
    return setItem(k, v);
  };
  await measureStore.open(kv, 1);
  const write = measureStore.update((s) => ({ ...s, base: { d: dash(600), at: 1, sentAt: 1, seq: 1 } }), 1);
  await tick();
  // 로그아웃: 닫고(줄을 기다린다) → 칸 지우기
  let cleared = false;
  const logout = (async () => {
    await measureStore.close();
    await clearAll(kv, 1);
    cleared = true;
  })();
  await tick();
  expect(cleared).toBe(false); // 쓰기가 끝나기 전엔 지우지 않는다
  gate.resolve();
  await logout;
  await expect(write).rejects.toBeInstanceOf(MeasureStoreClosed); // 쓴 뒤 가게가 닫혀 있었다 — 메모리는 바꾸지 않는다
  expect([...kv.map.keys()]).toEqual([]);
});

// ── T2 — 원장 · 실패 순번 · 보내기 차례 · 알림 칸 ──────────────────────────────────────────────
const KEY = 'timer:1:state';
const MIN_MS = 60_000;
const T = Date.UTC(2026, 10, 3, 1, 0);
/** 끝난 칸 — T + min분에 시작한 1분 측정 */
const done = (min: number, p: Partial<DoneMeasure> = {}): DoneMeasure => ({
  mode: 'reading',
  startedAt: new Date(T + min * MIN_MS).toISOString(),
  endedAt: new Date(T + (min + 1) * MIN_MS).toISOString(),
  bookId: 1,
  failedSince: null,
  settledAt: null,
  ...p,
});
const body = (x: DoneMeasure) => ({ startedAt: x.startedAt, endedAt: x.endedAt, bookId: x.bookId });
const withLedger = (kv: ReturnType<typeof memoryKV>, ledger: Ledger, base?: unknown, key = KEY) => kv.map.set(key, JSON.stringify({ base, ledger }));
const slotOf = (s: { ledger: Ledger }, x: DoneMeasure) => s.ledger.done.find((d) => d.mode === x.mode && d.startedAt === x.startedAt);
afterEach(() => void measureNotice.take());

test('REQ-368 · 기준 값과 원장은 한 칸에 함께 쓴다 — 그 쓰기가 실패하면 다시 열었을 때 앞 쌍 그대로다(받은 칸이 걷히지 않는다)', async () => {
  const kv = memoryKV();
  // 받은 칸(60초) — 기준 값(sentAt 50)은 그 몫을 모른다. 같은 날(1970-01-01) 안의 시각으로 둔다
  const got: DoneMeasure = { mode: 'reading', startedAt: new Date(60).toISOString(), endedAt: new Date(60_060).toISOString(), bookId: 1, failedSince: null, settledAt: 100 };
  withLedger(kv, { active: null, done: [got] }, { d: dash(600), at: 50, sentAt: 50 });
  await measureStore.open(kv, 1);
  expect(measureStore.get()!.ledger.done).toEqual([got]);
  jest.spyOn(Date, 'now').mockReturnValue(200);
  jest.spyOn(reading, 'dashboard').mockResolvedValueOnce(dash(660)); // 받은 뒤 보낸 요청 — 서버는 그 몫을 반영했다
  jest.spyOn(console, 'warn').mockImplementation(() => undefined);
  const setItem = kv.setItem;
  kv.setItem = async () => {
    throw new Error('disk full');
  };
  await loadDashboard();
  // 메모리는 한 스냅숏으로 바뀐다 — 새 기준 값 + 받은 칸 걷힘
  expect(measureStore.get()!.base!.d).toEqual(dash(660));
  expect(measureStore.get()!.ledger.done).toEqual([]);
  kv.setItem = setItem;
  await measureStore.close(); // 앱이 죽었다 다시 켬
  await measureStore.open(kv, 1);
  const s = measureStore.get()!;
  expect(s.base!.d).toEqual(dash(600));
  expect(s.ledger.done).toEqual([got]);
  // 보이는 오늘 몫이 줄지 않는다(옛 기준 값 600 + 받은 칸 60)
  expect(effectiveDashboard(s.base!, s.ledger, 300, confirmedOf(s)).d.todayReadSeconds).toBe(660);
});

test('REQ-378 · 기준 값보다 늦게 보낸 읽기가 실패하면 확인 안 된 값이 되고, 그 뒤 읽기가 성공하면 다시 확인된다 · 더 일찍 보낸 읽기의 실패는 확인을 지우지 않는다 · 디스크 값은 실패 전까진 확인된 것이다', async () => {
  const kv = memoryKV();
  kv.map.set(KEY, JSON.stringify({ base: { d: dash(60), at: 1, sentAt: 1 } }));
  await measureStore.open(kv, 1);
  expect(confirmedOf(measureStore.get()!)).toBe(true); // 디스크 값(순번 0) — 실패 전까지 믿는다
  const a = deferred<Dashboard>();
  const b = deferred<Dashboard>();
  const get = jest.spyOn(reading, 'dashboard').mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise);
  const setItem = jest.spyOn(kv, 'setItem');
  const la = loadDashboard();
  await tick();
  const lb = loadDashboard();
  await tick();
  expect(get).toHaveBeenCalledTimes(2);
  b.reject(new NetworkError()); // 늦게 보낸 읽기가 실패(지하)
  await expect(lb).rejects.toBeInstanceOf(NetworkError);
  expect(confirmedOf(measureStore.get()!)).toBe(false);
  expect(setItem).not.toHaveBeenCalled(); // 실패 순번은 메모리만
  a.resolve(dash(600)); // 그보다 먼저 보낸 읽기의 성공은 확인이 아니다
  await la;
  expect(measureStore.get()!.base!.d).toEqual(dash(600));
  expect(confirmedOf(measureStore.get()!)).toBe(false);
  get.mockResolvedValueOnce(dash(900));
  await loadDashboard();
  expect(confirmedOf(measureStore.get()!)).toBe(true);

  // 더 일찍 보낸 읽기의 실패는 확인을 지우지 않는다
  const c = deferred<Dashboard>();
  const d = deferred<Dashboard>();
  get.mockReturnValueOnce(c.promise).mockReturnValueOnce(d.promise);
  const lc = loadDashboard();
  await tick();
  const ld = loadDashboard();
  await tick();
  d.resolve(dash(1000));
  await ld;
  c.reject(new NetworkError());
  await expect(lc).rejects.toBeInstanceOf(NetworkError);
  expect(measureStore.get()!.base!.d).toEqual(dash(1000));
  expect(confirmedOf(measureStore.get()!)).toBe(true);
});

test('REQ-359 · 받은 마감은 칸에 받은 시각을 적고 · 연결 실패면 남기고 뒤 칸을 부르지 않는다', async () => {
  const kv = memoryKV();
  const [a, b, c] = [done(0), done(10), done(20)];
  withLedger(kv, { active: null, done: [c, a, b] });
  await measureStore.open(kv, 1);
  jest.spyOn(Date, 'now').mockReturnValue(T + 60 * MIN_MS);
  const finish = jest.spyOn(reading, 'finish').mockResolvedValueOnce({ trimmedSeconds: 0 }).mockRejectedValueOnce(new NetworkError());
  await flushMeasures();
  expect(finish.mock.calls.map((x) => x[0])).toEqual([body(a), body(b)]); // 시작 시각 순 · c는 부르지 않는다
  const s = measureStore.get()!;
  expect(slotOf(s, a)!.settledAt).toBe(T + 60 * MIN_MS);
  expect(slotOf(s, b)).toEqual(b); // 연결 실패는 실패 시각도 적지 않는다
  expect(slotOf(s, c)).toEqual(c);
  expect(JSON.parse(kv.map.get(KEY)!).ledger.done.find((x: DoneMeasure) => x.startedAt === a.startedAt).settledAt).toBe(T + 60 * MIN_MS); // 디스크에도
});

test('REQ-359 · 422면 빼고 센다 · 400 · 409 · 404 · 429 · 5xx는 남기고 다음 칸을 보낸다 · 401은 남기고 멈춘다', async () => {
  const kv = memoryKV();
  const slots = [done(0), done(10), done(20, { mode: 'study', bookId: null }), done(30), done(40), done(50), done(60), done(70)];
  withLedger(kv, { active: null, done: slots });
  await measureStore.open(kv, 1);
  const now = T + 100 * MIN_MS;
  jest.spyOn(Date, 'now').mockReturnValue(now);
  const outcomes = [
    new ApiError(422, '올릴 수 없는 측정 구간입니다'),
    new ApiError(400, '요청에 실패했어요 (400)'), // 전역 처리기의 HTML 본문 — 본문 형식 결함은 지우지 않는다
    new ApiError(409, '아직 끝나지 않은 구간입니다'),
    new ApiError(404, '요청에 실패했어요 (404)'), // 옛 서버(T1 전)
    new ApiError(429, '요청에 실패했어요 (429)'),
    new ApiError(500, '요청에 실패했어요 (500)'),
    new UnauthorizedError(),
  ];
  const sent: string[] = [];
  const fail = async (x: { startedAt: string }) => {
    sent.push(x.startedAt);
    throw outcomes[sent.length - 1];
  };
  jest.spyOn(reading, 'finish').mockImplementation(fail);
  const studyFinish = jest.spyOn(study, 'finish').mockImplementation(fail);
  await flushMeasures();
  expect(sent).toEqual(slots.slice(0, 7).map((x) => x.startedAt)); // 401에서 멈춘다 — 마지막 칸은 부르지 않는다
  expect(studyFinish.mock.calls.map((x) => x[0])).toEqual([body(slots[2])]); // 공부 칸은 공부 문으로
  const s = measureStore.get()!;
  expect(slotOf(s, slots[0])).toBeUndefined(); // 422 — 뺐다
  for (const x of slots.slice(1, 6)) expect(slotOf(s, x)!.failedSince).toBe(now);
  expect(slotOf(s, slots[6])).toEqual(slots[6]); // 401 — 그대로
  expect(slotOf(s, slots[7])).toEqual(slots[7]);
  expect(measureNotice.take()).toBe('올릴 수 없는 측정 1개를 지웠어요 — 7일이 지났거나 기기 시계가 맞지 않았어요');
});

test('REQ-359 · 도는 중에 부르면 한 번 더 돌아 그사이 생긴 정지도 싣는다 · 동시에 두 차례는 없다', async () => {
  const kv = memoryKV();
  const [a, b] = [done(0), done(10)];
  withLedger(kv, { active: null, done: [a] });
  await measureStore.open(kv, 1);
  const first = deferred<{ trimmedSeconds: number }>();
  const sent: string[] = [];
  let inflight = 0;
  let most = 0;
  jest.spyOn(reading, 'finish').mockImplementation(async (x) => {
    sent.push(x.startedAt);
    most = Math.max(most, ++inflight);
    try {
      return sent.length === 1 ? await first.promise : { trimmedSeconds: 0 };
    } finally {
      inflight--;
    }
  });
  const p1 = flushMeasures();
  await tick();
  expect(sent).toEqual([a.startedAt]);
  await measureStore.update((s) => ({ ...s, ledger: { ...s.ledger, done: [...s.ledger.done, b] } }), 1); // 보내는 사이 정지
  const p2 = flushMeasures();
  const p3 = flushMeasures();
  expect(p3).toBe(p2); // 아직 시작하지 않은 다음 차례에 합류
  expect(p2).not.toBe(p1);
  first.resolve({ trimmedSeconds: 0 });
  await Promise.all([p1, p2]);
  expect(sent).toEqual([a.startedAt, b.startedAt]);
  expect(most).toBe(1);
});

test('REQ-359 · 차례 중 사용자가 바뀌면 남은 칸을 보내지 않는다', async () => {
  const kv = memoryKV();
  const [a, b] = [done(0), done(10)];
  withLedger(kv, { active: null, done: [a, b] });
  withLedger(kv, { active: null, done: [done(30)] }, undefined, 'timer:2:state');
  await measureStore.open(kv, 1);
  const first = deferred<{ trimmedSeconds: number }>();
  const finish = jest.spyOn(reading, 'finish').mockReturnValueOnce(first.promise).mockResolvedValue({ trimmedSeconds: 0 });
  const p = flushMeasures();
  await tick();
  await measureStore.close(); // 로그아웃 → 다른 계정 로그인
  await measureStore.open(kv, 2);
  first.resolve({ trimmedSeconds: 0 });
  await expect(p).rejects.toBeInstanceOf(MeasureStoreClosed);
  expect(finish).toHaveBeenCalledTimes(1); // 앞 사람의 b를 새 토큰으로 보내지 않는다
  expect(measureStore.get()!.ledger.done).toEqual([done(30)]); // 새 사용자 원장은 그대로
  expect(JSON.parse(kv.map.get(KEY)!).ledger.done).toEqual([a, b]); // 앞 사람 칸은 디스크에 남는다(다시 로그인하면 보낸다)
});

test('REQ-359 · 가게가 안 열렸으면 보내지 않는다 · 시작 시각 순서로 보낸다 · 대시보드를 읽기 전에 보낸다', async () => {
  const order: string[] = [];
  const finish = jest.spyOn(reading, 'finish').mockImplementation(async (x) => {
    order.push(`finish ${x.startedAt}`);
    return { trimmedSeconds: 0 };
  });
  jest.spyOn(reading, 'dashboard').mockImplementation(async () => {
    order.push('dashboard');
    return dash(600);
  });
  await flushMeasures();
  expect(finish).not.toHaveBeenCalled();
  const kv = memoryKV();
  const [early, late] = [done(0), done(30)];
  withLedger(kv, { active: null, done: [late, early] });
  await measureStore.open(kv, 1);
  await loadDashboard();
  expect(order).toEqual([`finish ${early.startedAt}`, `finish ${late.startedAt}`, 'dashboard']);
});

test('REQ-359 · 6시간 넘게 켜진 기기 측정은 차례가 시작 + 6시간으로 끝내고 보낸다', async () => {
  const kv = memoryKV();
  withLedger(kv, { active: { mode: 'study', startedAt: new Date(T).toISOString(), bookId: 9, bookTitle: '선형대수학' }, done: [] });
  await measureStore.open(kv, 1);
  jest.spyOn(Date, 'now').mockReturnValue(T + 7 * 60 * MIN_MS);
  const finish = jest.spyOn(study, 'finish').mockResolvedValue({ trimmedSeconds: 0 });
  await flushMeasures();
  expect(finish.mock.calls[0][0]).toEqual({ startedAt: new Date(T).toISOString(), endedAt: new Date(T + 6 * 60 * MIN_MS).toISOString(), bookId: 9 });
  expect(measureStore.get()!.ledger.active).toBeNull();
});

test('REQ-366 · 지운 측정 · 겹쳐 뺀 시간(1분 이상)을 한 번만 알린다 · 아무 일 없으면 알림 없음', async () => {
  const kv = memoryKV();
  withLedger(kv, { active: null, done: [done(0), done(10), done(20)] });
  await measureStore.open(kv, 1);
  const seen = jest.fn();
  const unsub = measureNotice.subscribe(seen);
  const v0 = measureNotice.version();
  jest
    .spyOn(reading, 'finish')
    .mockRejectedValueOnce(new ApiError(422, '올릴 수 없는 측정 구간입니다'))
    .mockResolvedValueOnce({ trimmedSeconds: 30 })
    .mockResolvedValueOnce({ trimmedSeconds: 40 });
  await flushMeasures();
  unsub();
  expect(measureNotice.version()).toBeGreaterThan(v0);
  expect(seen).toHaveBeenCalled();
  expect(measureNotice.take()).toBe('올릴 수 없는 측정 1개를 지웠어요 — 7일이 지났거나 기기 시계가 맞지 않았어요\n다른 곳에서 재던 측정과 겹친 1분은 빼고 올렸어요');
  expect(measureNotice.take()).toBeNull(); // 한 번만
  measureNotice.add({ trimmed: 59 }); // 1분 미만은 띠에 안 보인다
  expect(measureNotice.take()).toBeNull();
  // 겹침 없이 받았으면 알림 없음
  await measureStore.update((s) => ({ ...s, ledger: { active: null, done: [done(40)] } }), 1);
  jest.spyOn(reading, 'finish').mockResolvedValueOnce({ trimmedSeconds: 0 });
  await flushMeasures();
  expect(measureNotice.take()).toBeNull();
});

test('REQ-366 · 하루 넘게 실패한 칸이 있으면 실행마다 한 번 알린다', async () => {
  const DAY = 24 * 60 * MIN_MS;
  const now = T + 10 * DAY;
  const kv = memoryKV();
  withLedger(kv, { active: null, done: [done(0, { failedSince: now - DAY + 1 })] });
  await measureStore.open(kv, 1);
  const clock = jest.spyOn(Date, 'now').mockReturnValue(now);
  jest.spyOn(reading, 'finish').mockRejectedValue(new ApiError(500, '요청에 실패했어요 (500)'));
  await flushMeasures();
  expect(measureNotice.take()).toBeNull(); // 하루가 안 됐다(1ms 모자람)
  clock.mockReturnValue(now + 1);
  await flushMeasures();
  expect(measureNotice.take()).toBe('아직 올리지 못한 측정이 있어요 — 기기에 남겨 두고 다시 보낼게요');
  await flushMeasures();
  expect(measureNotice.take()).toBeNull(); // 이 실행에선 한 번
});
