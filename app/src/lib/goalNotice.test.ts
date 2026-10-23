import { goalNotice } from './goalNotice';

// 범위 보충 §3.4 · REQ-92 — 목표 달성 알림 계획(독서 알림 켜짐 · 권한은 입력값). 실제 예약은 PR-I가 붙인다.
const at = (h: number, m = 0) => new Date(2026, 9, 23, h, m).getTime();
const MIN = 60;
const base = {
  todayRead: 10 * MIN,
  goal: 30 * MIN,
  activeStartedAt: at(20) as number | null,
  now: at(20, 5),
  nextMidnight: new Date(2026, 9, 24).getTime(),
  goalStreak: 3,
};

test('REQ-92 · 독서 알림이 켜져 있을 때만 목표 달성 알림이 조건에 맞게 하나 예약된다', () => {
  expect(goalNotice({ ...base, remindersOn: true, permitted: true })).toEqual({ id: 'goal-20261023', at: at(20, 20) });
  expect(goalNotice({ ...base, remindersOn: false, permitted: true })).toBeNull();
  expect(goalNotice({ ...base, remindersOn: true, permitted: false })).toBeNull();
  // 이미 채웠으면 예약할 것이 없다
  expect(goalNotice({ ...base, todayRead: 40 * MIN, remindersOn: true, permitted: true })).toBeNull();
});

test('REQ-92 · 정지하면 예약을 지운다', () => {
  expect(goalNotice({ ...base, activeStartedAt: null, remindersOn: true, permitted: true })).toBeNull();
});
