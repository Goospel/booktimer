import { widgetPayload } from './widgetPayload';

// v1 REQ-34 · 범위 보충 REQ-106 — 위젯은 스스로 갱신하지 못해 앱이 값을 밀어 넣는다. 날짜를 항상 싣는다.
const now = new Date(2026, 9, 23, 21, 0);
const idle = {
  hasActiveSession: false,
  activeStartedAt: null,
  activeBookTitle: null,
  todayReadSeconds: 32 * 60 + 59,
  todayGoalSeconds: 30 * 60,
  graph: { goalStreak: 5, currentStreak: 9 },
};

test('REQ-34 · 측정 중이 아니어도 dateLabel이 있다', () => {
  expect(widgetPayload(idle, now)).toEqual({ dateLabel: '10월 23일', todayMinutes: 32, mode: 'reading' });
});

test('REQ-34 · 측정 중이면 activeStartedAt과 책 제목을 싣는다', () => {
  const started = '2026-10-23T11:30:00Z';
  const p = widgetPayload({ ...idle, hasActiveSession: true, activeStartedAt: started, activeBookTitle: '데미안' }, now);
  expect(p.dateLabel).toBe('10월 23일');
  expect(p.activeStartedAt).toBe(Date.parse(started));
  expect(p.bookTitle).toBe('데미안');
});

test('REQ-106 · 위젯 값은 v1 값에 측정 모드를 싣고 목표·연속은 싣지 않는다', () => {
  const p = widgetPayload({ ...idle, hasActiveSession: true, activeStartedAt: '2026-10-23T11:30:00Z', activeBookTitle: '데미안' }, now);
  expect(p.mode).toBe('reading');
  expect(Object.keys(p).sort()).toEqual(['activeStartedAt', 'bookTitle', 'dateLabel', 'mode', 'todayMinutes']);
  expect(Object.values(p)).not.toContain(5); // goalStreak
  expect(Object.values(p)).not.toContain(30); // 목표 분
});
