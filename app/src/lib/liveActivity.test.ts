import { activityTimes, liveActivityAction } from './liveActivity';

// v1 REQ-35 · §4.10 — 앱이 앞으로 올 때 · 시작/정지 때 서버 상태와 로컬 활동을 비교한다.
const S = Date.UTC(2026, 9, 23, 11, 30);
const server = { active: true, startedAt: S, bookTitle: '데미안' };
const local = { startedAt: S, bookTitle: '데미안' };

test('REQ-35 · 서버 측정 중 + 활동 없음 = start', () => {
  expect(liveActivityAction(server, null)).toBe('start');
});

test('REQ-35 · 서버 정지 + 활동 있음 = end', () => {
  expect(liveActivityAction({ active: false, startedAt: null, bookTitle: null }, local)).toBe('end');
  expect(liveActivityAction({ active: false, startedAt: null, bookTitle: null }, null)).toBe('none');
});

test('REQ-35 · 시작 시각이 바뀌면 restart', () => {
  expect(liveActivityAction(server, { ...local, startedAt: S - 60_000 })).toBe('restart');
  expect(liveActivityAction(server, { ...local, bookTitle: '미움받을 용기' })).toBe('restart'); // 책 교체도
  expect(liveActivityAction(server, local)).toBe('none');
});

test('REQ-35 · 표시 상한은 시작 + 6시간 10분이고 멈춤·stale이 같은 시각', () => {
  const t = activityTimes(S);
  expect(t.lowerMs).toBe(S);
  expect(t.upperMs).toBe(S + (6 * 60 + 10) * 60_000);
  expect(t.pauseMs).toBe(t.upperMs);
  expect(t.staleMs).toBe(t.upperMs);
});
