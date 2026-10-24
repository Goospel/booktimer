import { nextMidnightOf } from './goalProgress';
import { studyToday } from './studyTimer';

// v1 REQ-98(범위 보충 §3.2) — 오늘 공부 = 서버 끝난 몫(study.todaySeconds) + 진행 중 몫(기기 자정부터 · 시작 + 6시간에서 멈춤).
const MIN = 60 * 1000;
const at = (h: number, m: number, day = 24) => new Date(2026, 9, day, h, m).getTime();

test('REQ-98 · 오늘 공부 시간은 끝난 몫에 진행 중 몫을 6시간까지 더한다', () => {
  const now = at(15, 0);
  const nextMidnight = nextMidnightOf(now);
  expect(studyToday({ todaySeconds: 3000, activeStartedAt: now - 10 * MIN, now, nextMidnight })).toBe(3600);
  expect(studyToday({ todaySeconds: 3000, activeStartedAt: now - 7 * 60 * MIN, now, nextMidnight })).toBe(3000 + 21600);
  expect(studyToday({ todaySeconds: 3000, activeStartedAt: null, now, nextMidnight })).toBe(3000);
});

test('REQ-98 · 자정을 넘긴 진행 중 몫은 자정부터 센다', () => {
  const now = at(0, 20, 25);
  expect(studyToday({ todaySeconds: 0, activeStartedAt: at(23, 30), now, nextMidnight: nextMidnightOf(now) })).toBe(1200);
});
