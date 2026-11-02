// 타이머 오프라인 T0(설계 2026-11-02 §6.5) — 마지막 대시보드를 사용자별 칸 하나에 두는 가게와 대시보드 읽기. KV는 AsyncStorage 꼴의 Map 가짜.
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async () => null),
  setItemAsync: jest.fn(async () => undefined),
  deleteItemAsync: jest.fn(async () => undefined),
}));

import { NetworkError, reading } from '../api/client';
import type { Dashboard } from '../api/types';
import { clearAll, type KV } from './draftSync';
import { loadDashboard, MeasureStoreClosed, measureStore } from './measureSync';

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
  expect(measureStore.get()).toEqual({ userId: 1, base: null });
  jest.spyOn(reading, 'dashboard').mockResolvedValueOnce(dash(600));
  await loadDashboard();
  expect([...kv.map.keys()]).toEqual(['timer:1:state']);
  expect(JSON.parse(kv.map.get('timer:1:state')!).base.d).toEqual(dash(600));

  await measureStore.close();
  await measureStore.open(kv, 1); // 앱을 다시 켬(오프라인) — 디스크 값이 첫 값이다. 순번은 0(이 실행에서 읽은 값이 아니다)
  expect(measureStore.get()!.base).toMatchObject({ d: dash(600), seq: 0 });

  await measureStore.close();
  await measureStore.open(kv, 2); // 다른 사용자는 앞 사람 칸을 열지 않는다
  expect(measureStore.get()).toEqual({ userId: 2, base: null });
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
  first.resolve(dash(600)); // 먼저 보낸 쪽이 늦게 와도 덮지 않는다
  await a;
  expect(measureStore.get()!.base!.d).toEqual(dash(1200));
  expect(JSON.parse(kv.map.get('timer:1:state')!).base.d).toEqual(dash(1200));
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
  expect(measureStore.get()).toEqual({ userId: 2, base: null });
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
