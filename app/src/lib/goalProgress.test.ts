import { goalProgress, isStaleDay, nextMidnightOf } from './goalProgress';

// 범위 보충 §3.4 · REQ-90 — 오늘 초 = 끝난 몫 + 진행 중 몫(기기 자정부터 · 6시간 상한).
// 시각은 기기 시간대의 날짜로 만든다(테스트가 어느 시간대에서 돌아도 같은 뜻).
const at = (h: number, m = 0, day = 23) => new Date(2026, 9, day, h, m).getTime();
const MIN = 60;
const midnight = at(0, 0, 24); // 10월 23일의 다음 자정

test('REQ-90 · 진행 중 몫이 목표를 넘기면 연속을 하나 더 보인다', () => {
  // 끝난 몫 20분 + 20시 시작 15분 경과 = 35분 ≥ 30분
  const p = goalProgress({ todayRead: 20 * MIN, goal: 30 * MIN, activeStartedAt: at(20), now: at(20, 15), nextMidnight: midnight, goalStreak: 4 });
  expect(p.todaySeconds).toBe(35 * MIN);
  expect(p.filled).toBe(true);
  expect(p.streak).toBe(5);
  expect(p.reachAt).toBeNull(); // 이미 채웠다
});

test('REQ-90 · 끝난 몫으로 이미 채웠으면 서버 연속을 그대로 보인다(하나 더 얹지 않는다)', () => {
  const p = goalProgress({ todayRead: 40 * MIN, goal: 30 * MIN, activeStartedAt: at(20), now: at(20, 5), nextMidnight: midnight, goalStreak: 5 });
  expect(p.filled).toBe(true);
  expect(p.streak).toBe(5);
});

test('REQ-90 · 아직 못 채웠으면 서버 연속 그대로이고 reachAt은 시작 + (목표 − 끝난 몫)이다', () => {
  const p = goalProgress({ todayRead: 10 * MIN, goal: 30 * MIN, activeStartedAt: at(20), now: at(20, 5), nextMidnight: midnight, goalStreak: 3 });
  expect(p.todaySeconds).toBe(15 * MIN);
  expect(p.filled).toBe(false);
  expect(p.streak).toBe(3);
  expect(p.reachAt).toBe(at(20, 20));
});

test('REQ-90 · 자정을 넘긴 진행 중 몫은 자정부터 센다', () => {
  // 어제 23:30 시작, 오늘 00:10 — 오늘 몫은 10분(어제 30분은 서버가 끝날 때 어제 행으로 나눈다)
  const p = goalProgress({ todayRead: 0, goal: 30 * MIN, activeStartedAt: at(23, 30, 22), now: at(0, 10), nextMidnight: midnight, goalStreak: 2 });
  expect(p.todaySeconds).toBe(10 * MIN);
  expect(p.reachAt).toBe(at(0, 30));
});

test('REQ-90 · 6시간 넘는 경과는 6시간에서 멈춘다', () => {
  const p = goalProgress({ todayRead: 0, goal: 8 * 60 * MIN, activeStartedAt: at(9), now: at(17), nextMidnight: midnight, goalStreak: 0 });
  expect(p.todaySeconds).toBe(6 * 60 * MIN);
  expect(p.reachAt).toBeNull(); // 8시간 목표는 시작 + 6시간 안에 닿지 않는다
});

test('REQ-90 · 다음 자정 뒤에야 닿는 reachAt은 없다', () => {
  const p = goalProgress({ todayRead: 0, goal: 60 * MIN, activeStartedAt: at(23, 30), now: at(23, 35), nextMidnight: midnight, goalStreak: 1 });
  expect(p.filled).toBe(false);
  expect(p.reachAt).toBeNull();
});

test('REQ-90 · 목표 0이면 읽은 날이 채운 날이고 reachAt은 없다', () => {
  const none = goalProgress({ todayRead: 0, goal: 0, activeStartedAt: null, now: at(9), nextMidnight: midnight, goalStreak: 2 });
  expect(none.filled).toBe(false);
  const read = goalProgress({ todayRead: 0, goal: 0, activeStartedAt: at(9), now: at(9, 1), nextMidnight: midnight, goalStreak: 2 });
  expect(read.filled).toBe(true);
  expect(read.streak).toBe(3);
  expect(read.reachAt).toBeNull();
});

test('REQ-90 · 전날 23:59에 읽은 대시보드는 00:00:01에 낡았다', () => {
  const loaded = nextMidnightOf(at(23, 59, 22));
  expect(isStaleDay(loaded, at(0, 0) + 1000)).toBe(true);
});

test('REQ-90 · 같은 날 안에서는 대시보드가 낡지 않았다', () => {
  const loaded = nextMidnightOf(at(0, 1));
  expect(isStaleDay(loaded, at(23, 59))).toBe(false);
});
