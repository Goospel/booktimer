import type { Dashboard } from '../api/types';
import { type GoalInput, goalProgress } from './goalProgress';
import { clampTime, DEFAULT_REMINDER, goalMetNotice, planNotifications, REMINDER_DAYS, scheduleDiff } from './reminders';

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

// 달성 순간 흐름 안 알림(REQ-178) — 홈 effect처럼 관측을 차례로 접는다(직전 채움 = 앞 관측의 filled). 뜬 글만 모은다.
const obs = (todayRead: number, goal: number, o: Partial<GoalInput> = {}): GoalInput => ({ todayRead, goal, activeStartedAt: null, now: at(20, 0), nextMidnight: at(0, 0, 25), goalStreak: 4, ...o });
function notices(steps: GoalInput[]): string[] {
  let was: boolean | null = null;
  const out: string[] = [];
  for (const s of steps) {
    const p = goalProgress(s);
    const m = goalMetNotice(was, p, s.goal);
    if (m) out.push(m);
    was = p.filled;
  }
  return out;
}

test('REQ-178 · 목표 0엔 달성 알림이 없다 — 오늘 0초에서 읽기 시작 1초 뒤 · 30분 → 0으로 내린 순간', () => {
  expect(notices([obs(0, 0), obs(0, 0, { activeStartedAt: at(20, 0), now: at(20, 0) + 1000 })])).toEqual([]);
  expect(notices([obs(10 * 60, 30 * 60), obs(10 * 60, 0)])).toEqual([]);
});
test('REQ-178 · 목표를 바꾸는 것만으로 알림이 새로 생기지 않는다 — 「목표 없음」 → 30분(이미 40분) · 같은 날 30 → 0 → 30분(이미 채움)', () => {
  expect(notices([obs(40 * 60, 0), obs(40 * 60, 30 * 60)])).toEqual([]);
  expect(notices([obs(40 * 60, 30 * 60), obs(40 * 60, 0), obs(40 * 60, 30 * 60)])).toEqual([]);
});
test('REQ-178 · 목표가 있으면 미달 → 채움 순간 한 번이고 처음 읽은 값은 알리지 않는다 — 글은 목표 달성 알림과 같다', () => {
  const run = (m: number) => obs(20 * 60, 30 * 60, { activeStartedAt: at(20, 0), now: at(20, m) });
  expect(notices([run(9), run(10), run(11)])).toEqual(['오늘 목표를 채웠어요 · 5일째']); // 양성 대조 — 끝난 20분 + 재는 10분 = 30분에 닿는 순간
  expect(notices([run(10)])).toEqual([]); // 처음 읽은 값(낡은 날 다음 첫 값도 화면이 null로 되돌린다)이 이미 채움
  expect(notices([obs(40 * 60, 60 * 60), obs(40 * 60, 30 * 60, { goalStreak: 5 })])).toEqual(['오늘 목표를 채웠어요 · 5일째']); // 목표 > 0끼리 내려 바로 채우면 한 번 — 지금 동작 그대로
});

test('REQ-179 · 목표 없음이면 독서 알림 글은 「오늘 읽을 시간이에요」이고 id 끝에 -nogoal이 붙는다', () => {
  const list = reminders(plan({ dash: idle(0, 0) }));
  expect(list).toHaveLength(30);
  expect(new Set(list.map((x) => x.body))).toEqual(new Set(['오늘 읽을 시간이에요']));
  expect(list[0].id).toBe('reminder-202610242000-nogoal');
  // 양성 대조 — 목표가 있으면 지금 글 · 지금 id 그대로
  expect(reminders(plan({ dash: idle(0) }))[0]).toMatchObject({ id: 'reminder-202610242000', body: '오늘 읽을 시간이에요 — 목표를 채우면 연속이 이어져요' });
});
test('REQ-179 · 목표를 0으로 바꾸면 걸려 있던 독서 알림 30통을 새 글로 바꿔 끼운다', () => {
  const before = reminders(plan({ dash: idle(0) })).map((x) => x.id);
  const d = scheduleDiff(before, plan({ dash: idle(0, 0) }));
  expect(d.cancel).toHaveLength(30);
  expect(reminders(d.add)).toHaveLength(30);
  expect(scheduleDiff(before, plan({ dash: idle(0) })).cancel).toEqual([]); // 목표가 그대로면 0
});
test('REQ-179 · 목표 없음에서도 오늘 1초라도 읽었으면 오늘 독서 알림은 뺀다', () => {
  expect(plan({ dash: idle(0, 0) }).map((x) => x.id)).toContain('reminder-202610242000-nogoal'); // 양성 대조
  expect(plan({ dash: idle(5 * 60, 0) }).map((x) => x.id)).not.toContain('reminder-202610242000-nogoal');
});
