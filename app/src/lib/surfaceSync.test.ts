// 타이머 오프라인 T0(설계 2026-11-02 §6.8) — 위젯 · Live Activity · 알림은 루트 한 곳이 가게 값으로, 한 번에 하나씩 맞춘다(REQ-363).
// Live Activity는 상태 있는 가짜다 — start가 인스턴스를 남기고 getInstances가 그것을 돌려준다(겹친 두 판이 둘 다 「없음」을 보는지 잰다).
const mockInstances: { end: () => Promise<void> }[] = [];
const mockStart = jest.fn(() => void mockInstances.push({ end: async () => void mockInstances.splice(0) }));
jest.mock('../../widgets/ReadingActivity', () => ({ __esModule: true, default: { getInstances: () => [...mockInstances], start: () => mockStart() } }));
jest.mock('../../widgets/ReadingWidget', () => ({ __esModule: true, default: { updateSnapshot: jest.fn(), updateTimeline: jest.fn() } }));
jest.mock('@react-native-async-storage/async-storage', () => ({ getItem: async () => null, setItem: jest.fn(async () => {}), removeItem: jest.fn(async () => {}) }));
jest.mock('./notify', () => ({ syncReminders: jest.fn(async () => {}), cancelReminders: jest.fn(async () => {}) }));
jest.mock('../api/client', () => ({ session: { token: jest.fn() } }));

import ReadingWidget from '../../widgets/ReadingWidget';
import { session } from '../api/client';
import type { Dashboard } from '../api/types';
import type { KV } from './draftSync';
import { measureStore } from './measureSync';
import { syncReminders } from './notify';
import { startSurfaceSync, stopSurfaceSync, syncSurfacesNow } from './surfaceSync';
import { syncSurfaces } from './surfaces';
import type { Base } from './visibleDashboard';

const token = session.token as jest.Mock;
const timeline = ReadingWidget.updateTimeline as jest.Mock;
const reminders = syncReminders as jest.Mock;
const kv: KV = { getItem: async () => null, setItem: async () => {}, removeItem: async () => {}, getAllKeys: async () => [] };
function deferred<T>() {
  let resolve!: (v: T) => void;
  const promise = new Promise<T>((ok) => (resolve = ok));
  return { promise, resolve };
}
const tick = () => new Promise((r) => setImmediate(r));

const dash = (todayReadSeconds: number, activeStartedAt: string | null = null) =>
  ({
    todayReadSeconds,
    todayGoalSeconds: 30 * 60,
    hasActiveSession: activeStartedAt !== null,
    activeStartedAt,
    activeBookTitle: activeStartedAt ? '데미안' : null,
    graph: { goalStreak: 5 },
    study: { hasActiveSession: false, activeStartedAt: null, activeBook: null },
  }) as unknown as Dashboard;
const baseOf = (d: Dashboard, at = Date.now()): Base => ({ d, at, sentAt: at, seq: 1 });
const setBase = (b: Base) => measureStore.update((s) => ({ ...s, base: b }), 1);
const minutesOf = (call: unknown[]) => (call[0] as { props: { todayMinutes: number } }[])[0].props.todayMinutes;

beforeEach(async () => {
  token.mockReset().mockResolvedValue('x');
  timeline.mockClear();
  reminders.mockReset().mockResolvedValue(undefined);
  mockStart.mockClear();
  mockInstances.splice(0);
  await measureStore.open(kv, 1);
});
afterEach(async () => {
  stopSurfaceSync();
  jest.restoreAllMocks();
  await measureStore.close();
});

test('REQ-363 · 맞추는 중에 두 번 바뀌면 끝난 뒤 마지막 값으로 한 번만 더 맞춘다', async () => {
  await setBase(baseOf(dash(10 * 60)));
  const hold = deferred<string>();
  token.mockReturnValueOnce(hold.promise); // 첫 판이 토큰 확인에서 매달린다
  startSurfaceSync();
  await tick();
  await setBase(baseOf(dash(20 * 60)));
  await setBase(baseOf(dash(30 * 60)));
  hold.resolve('x');
  await syncSurfacesNow(); // 줄에 선 판에 합류한다 — 새 판을 더 만들지 않는다
  expect(timeline).toHaveBeenCalledTimes(2);
  expect(minutesOf(timeline.mock.calls[0])).toBe(10);
  expect(minutesOf(timeline.mock.calls[1])).toBe(30);
});

test('REQ-363 · 겹쳐 불려도 Live Activity를 둘 띄우지 않는다', async () => {
  const d = dash(0, new Date(Date.now() - 60_000).toISOString());
  // 양성 대조 — 줄 없이 겹쳐 부르면 둘 다 「활동 없음」을 보고 둘을 띄운다(가짜가 겹침을 잡는다)
  await Promise.all([syncSurfaces(d), syncSurfaces(d)]);
  expect(mockStart).toHaveBeenCalledTimes(2);

  mockStart.mockClear();
  mockInstances.splice(0);
  await setBase(baseOf(d));
  await Promise.all([syncSurfacesNow(), syncSurfacesNow(), syncSurfacesNow()]);
  expect(mockStart).toHaveBeenCalledTimes(1);
  expect(mockInstances).toHaveLength(1);
});

test('REQ-363 · 맞추는 값은 맞추는 순간의 시각으로 굴린다(어제 받은 값 · 오늘 맞춤 → 오늘 값)', async () => {
  const yesterday = new Date(2026, 9, 30, 21, 0).getTime();
  const today = new Date(2026, 9, 31, 10, 0).getTime();
  await setBase(baseOf(dash(34 * 60), yesterday));
  jest.spyOn(Date, 'now').mockReturnValue(today);
  await syncSurfacesNow();
  const entries = timeline.mock.calls[0][0] as { date: Date; props: { dateLabel: string; todayMinutes: number } }[];
  expect(entries[0].date.getTime()).toBe(today);
  expect(entries[0].props).toMatchObject({ dateLabel: '10월 31일', todayMinutes: 0 });
  expect(reminders).toHaveBeenCalledTimes(1);
  expect(reminders.mock.calls[0][0].todayReadSeconds).toBe(0);
  expect(reminders.mock.calls[0][1].getTime()).toBe(today);
});

test('REQ-363 · 기준 값이 없으면 알림을 null로 맞춘다 · syncSurfacesNow는 그 판이 끝나야 돌아온다', async () => {
  const hold = deferred<void>();
  reminders.mockReturnValueOnce(hold.promise);
  let done = false;
  const p = syncSurfacesNow().then(() => (done = true));
  await tick();
  expect(reminders).toHaveBeenCalledWith(null);
  expect(timeline).toHaveBeenCalledTimes(0);
  expect(done).toBe(false);
  hold.resolve();
  await p;
  expect(done).toBe(true);
});
