import { boxGoalLabel, boxGoalText, goalChoices, goalProgress, type GoalProgress, goalStatus, isStaleDay, libraryGoalLine, midnightRereadDelay, nextMidnightOf, progressOf, REREAD_MS, shouldReread, textChunks, todayDashboard } from './goalProgress';
import type { ContributionDay, Dashboard } from '../api/types';

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

// ── 책생글 A2 — 홈 박스 작은 글 · 서재 목표 줄 · 보이는 대시보드의 진행(F1 §4.1 · §4.2) ──
test('REQ-270 · 박스 작은 글(2026-11-13 피그마 187:12225 확정) — 읽은 날 「1시간 12분 / 1시간 30분」 · 채운 날 「40분 / 30분」 · 안 읽은 날 「하루 목표 30분」 · 목표 없음 「오늘 12분」 · 「오늘 0분」', () => {
  expect(boxGoalText({ progress: P({}), goal: 30 * MIN, measuring: false })).toBe('하루 목표 30분');
  expect(boxGoalText({ progress: P({ todaySeconds: 30 }), goal: 30 * MIN, measuring: false })).toBe('하루 목표 30분'); // 1분 미만 = 안 읽은 날 — 0/30 꼴은 쓰지 않는다(10-28)
  expect(boxGoalText({ progress: P({ todaySeconds: 60 }), goal: 30 * MIN, measuring: false })).toBe('1분 / 30분'); // 1분부터 읽은 날 · 연속 5가 있어도 없음
  expect(boxGoalText({ progress: P({ todaySeconds: 72 * MIN }), goal: 90 * MIN, measuring: false })).toBe('1시간 12분 / 1시간 30분');
  expect(boxGoalText({ progress: P({ todaySeconds: 40 * MIN, filled: true }), goal: 30 * MIN, measuring: false })).toBe('40분 / 30분');
  expect(boxGoalText({ progress: P({}), goal: 0, measuring: false })).toBe('오늘 0분');
  expect(boxGoalText({ progress: P({ todaySeconds: 30 }), goal: 0, measuring: false })).toBe('오늘 0분');
  expect(boxGoalText({ progress: P({ todaySeconds: 12 * MIN, filled: true }), goal: 0, measuring: false })).toBe('오늘 12분');
  expect(boxGoalText({ progress: P({ todaySeconds: 40 * MIN, filled: true, streak: 6 }), goal: 30 * MIN, measuring: false })).not.toContain('일째');
});
test('REQ-270 · 박스 VoiceOver 라벨은 「오늘 N, 목표 M」 — 「/」 없음 · 목표 없음 「오늘 12분, 목표 없음」 · 안 읽은 날은 화면 글 그대로', () => {
  expect(boxGoalLabel({ progress: P({ todaySeconds: 72 * MIN }), goal: 90 * MIN, measuring: false })).toBe('오늘 1시간 12분, 목표 1시간 30분');
  expect(boxGoalLabel({ progress: P({ todaySeconds: 40 * MIN, filled: true }), goal: 30 * MIN, measuring: false })).toBe('오늘 40분, 목표 30분');
  expect(boxGoalLabel({ progress: P({}), goal: 30 * MIN, measuring: false })).toBe('하루 목표 30분');
  expect(boxGoalLabel({ progress: P({ todaySeconds: 12 * MIN }), goal: 0, measuring: false })).toBe('오늘 12분, 목표 없음');
  expect(boxGoalLabel({ progress: P({}), goal: 0, measuring: false })).toBe('오늘 0분, 목표 없음');
  expect(boxGoalLabel({ progress: P({ todaySeconds: 72 * MIN }), goal: 90 * MIN, measuring: true })).toBe('오늘 1시간 12분'); // 측정 중 A안 — 지금 그대로
});
test('REQ-270 · 측정 중 작은 글 = 「오늘 1시간 12분」(today 조각만 — 목표 1시간 30분 · 목표 없음에서도 목표 조각 없음)', () => {
  expect(boxGoalText({ progress: P({ todaySeconds: 72 * MIN }), goal: 90 * MIN, measuring: true })).toBe('오늘 1시간 12분');
  expect(boxGoalText({ progress: P({ todaySeconds: 72 * MIN }), goal: 0, measuring: true })).toBe('오늘 1시간 12분');
  expect(boxGoalText({ progress: P({ todaySeconds: 20 }), goal: 30 * MIN, measuring: true })).toBe('오늘 0분'); // 측정 첫 1분 — 측정 중 0분은 지금 규칙 그대로
});
test('REQ-270 · 박스 작은 글은 「 / 」 조각째 접힌다 — 「/」는 앞 조각 끝 · 조각마다 따로 그린다(2026-11-13 기기 「목표 1 / 시간 30분」)', () => {
  expect(textChunks('1시간 12분 / 1시간 30분')).toEqual(['1시간 12분 /', '1시간 30분']);
  expect(textChunks('하루 목표 30분')).toEqual(['하루 목표 30분']);
  expect(textChunks('오늘 12분')).toEqual(['오늘 12분']);
});
test('REQ-271 · 서재 목표 줄 — 읽은 날 「하루 목표 30분 · 5일째」 · 안 읽은 날 연속 없음 · 목표 없음 「하루 목표 없음」 · 연속 0이면 연속 없음', () => {
  const lines = [
    libraryGoalLine({ progress: P({ todaySeconds: 12 * MIN }), goal: 30 * MIN, measuring: false }),
    libraryGoalLine({ progress: P({}), goal: 30 * MIN, measuring: false }),
    libraryGoalLine({ progress: P({ todaySeconds: 12 * MIN, filled: true, streak: 4 }), goal: 0, measuring: false }),
    libraryGoalLine({ progress: P({ todaySeconds: 12 * MIN, streak: 0 }), goal: 30 * MIN, measuring: false }),
    libraryGoalLine({ progress: P({ todaySeconds: 20 }), goal: 30 * MIN, measuring: true }),
    libraryGoalLine({ progress: P({}), goal: 0, measuring: false }),
  ];
  expect(lines).toEqual(['하루 목표 30분 · 5일째', '하루 목표 30분', '하루 목표 없음', '하루 목표 30분', '하루 목표 30분 · 5일째', '하루 목표 없음']);
  for (const l of lines) expect(l).not.toContain('하루 하루');
});
test('REQ-90 · progressOf — 진행 중 몫은 독서 측정 중일 때만 얹는다(홈 · 서재 같은 입력)', () => {
  const d = (o: Partial<Dashboard>) =>
    ({ todayReadSeconds: 20 * MIN, todayGoalSeconds: 30 * MIN, hasActiveSession: true, activeStartedAt: new Date(at(20)).toISOString(), graph: { goalStreak: 4 }, ...o }) as unknown as Dashboard;
  const running = progressOf(d({}), at(20, 15));
  expect(running.todaySeconds).toBe(35 * MIN);
  expect(running.streak).toBe(5); // 진행 중 몫으로 처음 채움 — 서버 연속 4에서 하나 더
  expect(progressOf(d({ hasActiveSession: false }), at(20, 15)).todaySeconds).toBe(20 * MIN); // 시작 시각이 남아 있어도 얹지 않는다
  expect(progressOf(d({ graph: {} }), at(20, 15)).streak).toBe(1); // 옛 서버(연속 없음) = 0에서
});

// ── 자정을 걸친 늦은 대시보드 응답(REQ-346~349 · 2026-11-02) — 서버가 아직 전날로 센 응답은 쓰지 않고 1초 뒤 한 번 다시 읽는다 · 판정한 순간으로 쓴다 ──
// 응답 픽스처의 서버 날짜는 가장 늦은 칸에만 둔다 — 앞 칸(10-18) · 앞날 칸(null) · 옛 주(10-11)와 갈리게(첫 칸을 읽으면 아래 「이미 새 날이면 다시 읽지 않는다」가 깨진다).
const when = (day: number, h = 0, m = 0, s = 0, ms = 0) => new Date(2026, 9, day, h, m, s, ms).getTime();
const cell = (date: string | null): ContributionDay => ({ date, totalSeconds: 0, level: 0, manual: false });
const reply = (server: string) => ({ graph: { goalStreak: 1, weeks: [[cell('2026-10-18'), cell(server), cell(null)], [cell('2026-10-11')]] } }) as unknown as Dashboard;

test('REQ-346 · 자정 전에 보낸 요청의 응답이 자정 뒤에 왔는데 서버가 아직 전날(10-23)이면 다시 읽는다 — 서버가 새 날(10-24)이면 그대로 쓴다', () => {
  expect(shouldReread('2026-10-23', when(23, 23, 59, 59, 700), when(24, 0, 0, 0, 200))).toBe(true);
  expect(shouldReread('2026-10-24', when(23, 23, 59, 59, 700), when(24, 0, 0, 0, 200))).toBe(false);
});

test('REQ-346 · 자정 뒤 10초 안에 보낸 요청에 서버가 전날이면 다시 읽는다(기기 시계가 서버보다 앞섬) — 9.999초까지 · 10초부터는 그대로 쓴다(창 끝)', () => {
  expect(shouldReread('2026-10-23', when(24, 0, 0, 1), when(24, 0, 0, 1, 300))).toBe(true);
  expect(shouldReread('2026-10-23', when(24, 0, 0, 9, 999), when(24, 0, 0, 10, 300))).toBe(true);
  expect(shouldReread('2026-10-23', when(24, 0, 0, 10), when(24, 0, 0, 10, 300))).toBe(false);
});

test('REQ-347 · 다시 읽지 않는다 — 같은 날 · 서버가 하루 앞(해외 서쪽 · 자정 직후여도) · 잔디 없음(옛 서버 · 자정을 걸쳐도) · 낮 시간의 전날(해외 동쪽)', () => {
  expect(shouldReread('2026-10-24', when(24, 12), when(24, 12, 0, 0, 300))).toBe(false);
  expect(shouldReread('2026-10-25', when(24, 0, 0, 1), when(24, 0, 0, 1, 300))).toBe(false);
  expect(shouldReread(undefined, when(23, 23, 59, 59, 700), when(24, 0, 0, 0, 200))).toBe(false);
  expect(shouldReread('2026-10-23', when(24, 3), when(24, 3, 0, 0, 300))).toBe(false);
});

test('REQ-346 · 몇 시간 늦게 온 응답(23:59:30 보냄 → 08:00 옴 — 앱이 잠든 사이)도 다시 읽는다 · 그 다시 읽기(08:00:01 보냄)는 서버가 아직 전날이어도 그대로 쓴다(창 밖)', () => {
  expect(shouldReread('2026-10-23', when(23, 23, 59, 30), when(24, 8))).toBe(true);
  expect(shouldReread('2026-10-23', when(24, 8, 0, 1), when(24, 8, 0, 1, 300))).toBe(false);
});

test('REQ-346 · todayDashboard — 자정 전(23:59:59.700)에 보내 0.5초 걸린 응답이 서버 전날(10-23)이면 버리고 1초 뒤 다시 읽어 새 날(10-24) 응답과 그 받은 시각을 돌려준다', async () => {
  jest.useFakeTimers();
  try {
    jest.setSystemTime(when(23, 23, 59, 59, 700));
    const stale = reply('2026-10-23');
    const fresh = reply('2026-10-24');
    const queue = [stale, fresh];
    const get = jest.fn(async () => {
      jest.setSystemTime(Date.now() + 500); // 응답이 오는 데 걸린 시간
      return queue.length > 1 ? queue.shift()! : queue[0];
    });
    const done = jest.fn();
    void todayDashboard(get).then(done);
    await jest.advanceTimersByTimeAsync(REREAD_MS - 1);
    expect(get).toHaveBeenCalledTimes(1); // 1초를 기다린다
    expect(done).not.toHaveBeenCalled(); // 버린 응답은 돌려주지 않는다
    await jest.advanceTimersByTimeAsync(1);
    expect(done).toHaveBeenCalledWith({ d: fresh, at: when(24, 0, 0, 1, 700) }); // 받아들인 응답을 받은 시각(REQ-349)
    expect(get).toHaveBeenCalledTimes(2);
  } finally {
    jest.useRealTimers();
  }
});

test('REQ-347 · todayDashboard — 서버가 계속 전날이면(해외 동쪽 · 기기 시계가 크게 앞섬) 한 번만 다시 읽고 그 응답을 쓴다 — 00:00:01에 보내 0.3초씩 걸리면 2번 읽고 남은 타이머가 없다', async () => {
  jest.useFakeTimers();
  try {
    jest.setSystemTime(when(24, 0, 0, 1));
    const stale1 = reply('2026-10-23');
    const stale2 = { ...reply('2026-10-23'), hasActiveSession: true } as Dashboard; // 둘 다 전날 — 다른 객체라 어느 응답을 썼는지 가른다
    const queue = [stale1, stale2];
    const get = jest.fn(async () => {
      jest.setSystemTime(Date.now() + 300);
      return queue.shift()!;
    });
    const done = jest.fn();
    void todayDashboard(get).then(done);
    await jest.advanceTimersByTimeAsync(REREAD_MS);
    // 손 유도: 1.0초 보냄 → 1.3초 받음(창 안 · 전날 → 다시 읽기) → 1초 뒤 2.3초 보냄 → 2.6초 받음 → 판정 없이 쓴다
    expect(done).toHaveBeenCalledWith({ d: stale2, at: when(24, 0, 0, 2, 600) }); // 다시 읽은 응답(버린 첫 응답이 아니라)을 판정 없이 쓴다 — 지금 동작
    expect(get).toHaveBeenCalledTimes(2);
    expect(jest.getTimerCount()).toBe(0); // 더 기다리는 타이머가 없다 — 끝남이 구조다
  } finally {
    jest.useRealTimers();
  }
});

test('REQ-347 · todayDashboard — 자정을 걸쳐 온 응답(23:59:59.700 보냄 · 00:00:00.200 받음)이 이미 새 날(10-24)이면 다시 읽지 않는다', async () => {
  jest.useFakeTimers();
  try {
    jest.setSystemTime(when(23, 23, 59, 59, 700));
    const fresh = reply('2026-10-24');
    const get = jest.fn(async () => {
      jest.setSystemTime(Date.now() + 500);
      return fresh;
    });
    const done = jest.fn();
    void todayDashboard(get).then(done);
    await jest.advanceTimersByTimeAsync(0); // 마이크로태스크만 — 1초 기다림이 끼면 done이 아직 없다
    expect(done).toHaveBeenCalledWith({ d: fresh, at: when(24, 0, 0, 0, 200) });
    expect(get).toHaveBeenCalledTimes(1);
  } finally {
    jest.useRealTimers();
  }
});

test('REQ-347 · todayDashboard — 같은 날 응답(서버 10-24 · 낮 12시)은 한 번 읽고 기다리지 않는다', async () => {
  jest.useFakeTimers();
  try {
    jest.setSystemTime(when(24, 12));
    const fresh = reply('2026-10-24');
    const get = jest.fn(async () => fresh);
    const done = jest.fn();
    void todayDashboard(get).then(done);
    await jest.advanceTimersByTimeAsync(0); // 마이크로태스크만 — 타이머는 나아가지 않는다
    expect(done.mock.calls[0][0].d).toBe(fresh);
    expect(get).toHaveBeenCalledTimes(1);
  } finally {
    jest.useRealTimers();
  }
});

test('REQ-347 · todayDashboard — 다시 읽다 실패하면 그 오류를 낸다(버린 응답을 돌려주지 않는다 — 날짜에 묶인 값은 자정 전 값이 낫다)', async () => {
  jest.useFakeTimers();
  try {
    jest.setSystemTime(when(23, 23, 59, 59, 700));
    const get = jest
      .fn()
      .mockImplementationOnce(async () => {
        jest.setSystemTime(Date.now() + 500);
        return reply('2026-10-23');
      })
      .mockImplementationOnce(async () => {
        throw new Error('끊김');
      });
    const r = todayDashboard(get).catch((e: unknown) => e);
    await jest.advanceTimersByTimeAsync(REREAD_MS);
    expect(await r).toEqual(new Error('끊김'));
    expect(get).toHaveBeenCalledTimes(2);
  } finally {
    jest.useRealTimers();
  }
});

test('REQ-349 · todayDashboard는 받아들인 응답을 받은 시각을 함께 돌려준다 — 자정 직전(23:59:59.700 보냄 · 23:59:59.950 받음)의 같은 날 응답은 23:59:59.950이다(보낸 시각이 아니다)', async () => {
  jest.useFakeTimers();
  try {
    jest.setSystemTime(when(23, 23, 59, 59, 700));
    const same = reply('2026-10-23');
    const get = jest.fn(async () => {
      jest.setSystemTime(Date.now() + 250);
      return same;
    });
    await expect(todayDashboard(get)).resolves.toEqual({ d: same, at: when(23, 23, 59, 59, 950) }); // 쓰는 곳이 자정 뒤에 돌아도 이 값의 날은 10/23이다
    expect(get).toHaveBeenCalledTimes(1);
  } finally {
    jest.useRealTimers();
  }
});

test('REQ-349 · 측정 전 자정 다시 읽기는 읽은 날의 자정 +1초다 — 자정 직전(23:59:59.990)에 받은 값의 예약을 자정 뒤(00:00:00.010)에 걸어도 다음 날로 밀리지 않고 1초 뒤 · 평소(21:00)엔 3시간 +1초', () => {
  expect(midnightRereadDelay(nextMidnightOf(when(23, 23, 59, 59, 990)), when(24, 0, 0, 0, 10))).toBe(1000);
  expect(midnightRereadDelay(nextMidnightOf(when(23, 21)), when(23, 21))).toBe(3 * 60 * 60 * 1000 + 1000);
  expect(midnightRereadDelay(when(24), when(24))).toBe(1000);
});
