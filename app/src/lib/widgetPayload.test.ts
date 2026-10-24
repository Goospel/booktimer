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

test('REQ-106 · 공부 측정 중이면 공부 모드와 그 시작 시각·책 제목을 싣는다', () => {
  const studyAt = '2026-10-23T11:40:00Z';
  const study = { hasActiveSession: true, activeStartedAt: studyAt, activeBook: { title: '혼자 공부하는 머신러닝+딥러닝' } };
  const p = widgetPayload({ ...idle, study }, now);
  expect(p.mode).toBe('study');
  expect(p.activeStartedAt).toBe(Date.parse(studyAt));
  expect(p.bookTitle).toBe('혼자 공부하는 머신러닝+딥러닝');
  expect(p.todayMinutes).toBe(32); // 대기 값은 독서 그대로
  // 책 없이 재는 공부 — 제목 키가 없다
  const bare = widgetPayload({ ...idle, study: { ...study, activeBook: null } }, now);
  expect(bare.mode).toBe('study');
  expect(bare).not.toHaveProperty('bookTitle');
  // 양성 대조 — 독서 측정 중이면 독서(공부가 끝난 상태)
  const r = widgetPayload({ ...idle, hasActiveSession: true, activeStartedAt: '2026-10-23T11:30:00Z', activeBookTitle: '데미안', study: { hasActiveSession: false, activeStartedAt: null, activeBook: null } }, now);
  expect(r.mode).toBe('reading');
  expect(r.bookTitle).toBe('데미안');
  // 둘 다 쉬면 독서 대기
  expect(widgetPayload({ ...idle, study: { hasActiveSession: false, activeStartedAt: null, activeBook: null } }, now)).toEqual({ dateLabel: '10월 23일', todayMinutes: 32, mode: 'reading' });
});
