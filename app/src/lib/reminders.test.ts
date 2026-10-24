import type { Dashboard } from '../api/types';
import { clampTime, DEFAULT_REMINDER, planNotifications, REMINDER_DAYS, scheduleDiff } from './reminders';

// 독서 알림 · 목표 달성 알림 계획(v1 REQ-33 · REQ-92 · REQ-153). 시계는 2026-10-24 기기 시간대로 고정한다.
const at = (h: number, m: number, day = 24) => new Date(2026, 9, day, h, m).getTime();
const ON = { on: true, hour: 20, minute: 0 };
type Dash = Pick<Dashboard, 'todayReadSeconds' | 'todayGoalSeconds' | 'hasActiveSession' | 'activeStartedAt' | 'graph'>;
// 두 출처(끝난 몫 · 목표)가 다른 값이 되게 둔다 — 어느 쪽을 읽었는지 가려진다
const idle = (read: number, goal = 30 * 60): Dash => ({ todayReadSeconds: read, todayGoalSeconds: goal, hasActiveSession: false, activeStartedAt: null, graph: { goalStreak: 4 } });
const reading = (startedAt: number): Dash => ({ ...idle(5 * 60), hasActiveSession: true, activeStartedAt: new Date(startedAt).toISOString() });
const plan = (p: Partial<Parameters<typeof planNotifications>[0]>) =>
  planNotifications({ setting: ON, permitted: true, signedIn: true, now: at(19, 0), dash: null, ...p });
const reminders = (list: ReturnType<typeof planNotifications>) => list.filter((x) => x.id.startsWith('reminder-'));

test('REQ-33 · 30개를 넘지 않는다', () => {
  const list = plan({});
  expect(REMINDER_DAYS).toBe(30);
  expect(list).toHaveLength(30);
  expect(new Set(list.map((x) => x.id)).size).toBe(30);
  for (const x of list) {
    const d = new Date(x.at);
    expect([d.getHours(), d.getMinutes()]).toEqual([20, 0]);
  }
  expect(list[0].id).toBe('reminder-202610242000');
  expect(list[0].at).toBe(at(20, 0));
});

test('REQ-33 · 이미 지난 오늘 시각은 내일부터', () => {
  const late = plan({ now: at(21, 0) });
  expect(late).toHaveLength(30);
  expect(late[0].id).toBe('reminder-202610252000');
  expect(late[0].at).toBe(at(20, 0, 25));
  // 양성 대조 — 아직 안 지났으면 오늘부터
  expect(plan({ now: at(19, 59) })[0].id).toBe('reminder-202610242000');
});

test('REQ-92 · 오늘 목표를 채웠으면 오늘 독서 알림만 뺀다', () => {
  const filled = plan({ dash: idle(40 * 60) });
  const ids = filled.map((x) => x.id);
  expect(ids).not.toContain('reminder-202610242000');
  expect(ids).toContain('reminder-202610252000');
  // 대조 — 읽었지만 못 채웠으면(10분 / 30분) 오늘 것이 있다(v1 원래 기준 「오늘 읽었으면」과 다르다)
  expect(plan({ dash: idle(10 * 60) }).map((x) => x.id)).toContain('reminder-202610242000');
});

test('REQ-92 · 독서 알림을 끄면 목표 달성 예약도 지운다', () => {
  const dash = reading(at(18, 50)); // 재는 중 · 미달 — goalNotice 조건이 맞는다
  expect(plan({ setting: { ...ON, on: false }, dash })).toEqual([]);
  expect(scheduleDiff(['reminder-202610252000', 'goal-20261024'], []).cancel.sort()).toEqual(['goal-20261024', 'reminder-202610252000']);
  // 대조 — 켜져 있으면 목표 달성 예약이 계획에 있다
  expect(plan({ dash }).map((x) => x.id)).toContain('goal-20261024');
});

test('REQ-153 · 지금 읽는 중이면 오늘 독서 알림도 뺀다', () => {
  const list = plan({ dash: reading(at(18, 50)) });
  const ids = list.map((x) => x.id);
  expect(ids).not.toContain('reminder-202610242000');
  expect(reminders(list)).toHaveLength(30);
  const goal = list.filter((x) => x.id.startsWith('goal-'));
  expect(goal).toHaveLength(1);
  expect(goal[0].at).toBe(at(18, 50) + (30 - 5) * 60 * 1000);
  expect(goal[0].body).toBe('오늘 목표를 채웠어요 · 5일째');
});

test('REQ-153 · 권한이 없거나 로그인하지 않았으면 아무것도 예약하지 않는다', () => {
  const dash = reading(at(18, 50));
  expect(plan({ permitted: false, dash })).toEqual([]);
  expect(plan({ signedIn: false, dash })).toEqual([]);
  expect(plan({ setting: DEFAULT_REMINDER, dash })).toEqual([]); // 기본은 꺼짐
  expect(plan({ dash }).length).toBeGreaterThan(0); // 양성 대조
});

test('REQ-153 · 알림 시각은 8:00~22:00 안으로 자른다', () => {
  expect(clampTime({ hour: 7, minute: 30 })).toEqual({ hour: 8, minute: 0 });
  expect(clampTime({ hour: 22, minute: 30 })).toEqual({ hour: 22, minute: 0 });
  expect(clampTime({ hour: 22, minute: 0 })).toEqual({ hour: 22, minute: 0 });
  expect(clampTime({ hour: 13, minute: 45 })).toEqual({ hour: 13, minute: 45 });
  // 저장값이 범위 밖이어도 계획은 자른 시각으로 건다
  expect(plan({ setting: { on: true, hour: 6, minute: 0 }, now: at(5, 0) })[0].id).toBe('reminder-202610240800');
});

test('REQ-153 · 예약 차이는 id로 고르고 목표 달성 알림은 늘 다시 건다', () => {
  const next = [
    { id: 'reminder-202610252000', at: at(20, 0, 25), body: 'r' },
    { id: 'reminder-202610262000', at: at(20, 0, 26), body: 'r' },
    { id: 'goal-20261024', at: at(19, 15), body: 'g' },
  ];
  const d = scheduleDiff(['reminder-202610252000', 'reminder-202610252130', 'goal-20261024', 'other-1'], next);
  expect(d.cancel).toEqual(['reminder-202610252130']); // 다른 시각 id만 · 우리 것이 아닌 id는 건드리지 않는다
  expect(d.add.map((x) => x.id)).toEqual(['reminder-202610262000', 'goal-20261024']); // 같은 독서 id는 그대로 · 목표 달성은 이미 있어도 다시
});
