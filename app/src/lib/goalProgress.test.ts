import { goalChoices, goalProgress, type GoalProgress, goalStatus, isStaleDay, nextMidnightOf } from './goalProgress';

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

test('REQ-175 · 목표 시트 맨 위는 「목표 없음」(0)이고 그 아래 10분~2시간이다 — 「0분」 줄은 없다', () => {
  expect(goalChoices(30 * MIN).map((r) => [r.seconds, r.title])).toEqual([
    [0, '목표 없음'], [600, '10분'], [1200, '20분'], [1800, '30분'], [2700, '45분'], [3600, '1시간'], [5400, '1시간 30분'], [7200, '2시간'],
  ]);
});
test('REQ-175 · 지금 값인 줄만 고른 줄이다 — 분 줄 꼬리는 「지금 목표」, 「목표 없음」 줄은 꼬리 없이', () => {
  expect(goalChoices(0).filter((r) => r.selected).map((r) => [r.title, r.meta])).toEqual([['목표 없음', undefined]]);
  expect(goalChoices(30 * MIN).filter((r) => r.selected).map((r) => [r.title, r.meta])).toEqual([['30분', '지금 목표']]);
  expect(goalChoices(25 * MIN).some((r) => r.selected)).toBe(false); // 웹에서 넣은 목록 밖 값 — 고른 줄 없음
});

const P = (o: Partial<GoalProgress>): GoalProgress => ({ todaySeconds: 0, filled: false, streak: 5, reachAt: null, ...o });

test('REQ-176 · 측정 전 오늘 0분이면 「오늘 0분」도 연속도 없이 「하루 목표 M분」이다', () => {
  expect(goalStatus({ progress: P({}), goal: 30 * MIN, measuring: false })).toBe('하루 목표 30분');
  expect(goalStatus({ progress: P({ todaySeconds: 30 }), goal: 30 * MIN, measuring: false })).toBe('하루 목표 30분'); // 1분 미만 = 0분
  expect(goalStatus({ progress: P({ todaySeconds: 60 }), goal: 30 * MIN, measuring: false })).toBe('오늘 1분 · 목표 30분 · 5일째'); // 경계 — 1분부터는 읽은 날 글
});
test('REQ-176 · 읽은 날 · 채운 날 · 측정 중은 「오늘 N분 · 목표 M분 · K일째」 그대로다', () => {
  expect(goalStatus({ progress: P({ todaySeconds: 12 * MIN }), goal: 30 * MIN, measuring: false })).toBe('오늘 12분 · 목표 30분 · 5일째');
  expect(goalStatus({ progress: P({ todaySeconds: 40 * MIN, filled: true, streak: 6 }), goal: 30 * MIN, measuring: false })).toBe('오늘 40분 · 목표 30분 · 6일째');
  expect(goalStatus({ progress: P({ todaySeconds: 20 }), goal: 30 * MIN, measuring: true })).toBe('오늘 0분 · 목표 30분 · 5일째'); // 큰 시계 아래 — 재기 시작하면 연속이 보인다
  expect(goalStatus({ progress: P({ todaySeconds: 12 * MIN, streak: 0 }), goal: 30 * MIN, measuring: false })).toBe('오늘 12분 · 목표 30분');
});
test('REQ-177 · 목표 없음이면 「하루 목표 없음」 · 「오늘 N분 · 목표 없음」이고 연속을 보이지 않는다 — 시트 줄 이름과 같은 말', () => {
  const none = goalChoices(0).find((r) => r.seconds === 0)!.title;
  expect(none).toBe('목표 없음');
  expect(goalStatus({ progress: P({ streak: 3 }), goal: 0, measuring: false })).toBe(`하루 ${none}`);
  expect(goalStatus({ progress: P({ todaySeconds: 12 * MIN, filled: true, streak: 4 }), goal: 0, measuring: false })).toBe('오늘 12분 · 목표 없음');
  expect(goalStatus({ progress: P({ todaySeconds: 5 * MIN, filled: true, streak: 4 }), goal: 0, measuring: true })).toBe('오늘 5분 · 목표 없음');
});
