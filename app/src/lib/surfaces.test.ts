const mockStart = jest.fn();
jest.mock('../../widgets/ReadingActivity', () => ({ __esModule: true, default: { getInstances: () => [], start: (...a: unknown[]) => mockStart(...a) } }));
// ↑ 화살표로 감싼다 — import가 const 선언보다 먼저 돌아 팩토리 시점엔 mockStart가 아직 없다
jest.mock('../../widgets/ReadingWidget', () => ({ __esModule: true, default: { updateSnapshot: jest.fn(), updateTimeline: jest.fn() } }));
jest.mock('@react-native-async-storage/async-storage', () => ({ getItem: async () => null, setItem: jest.fn(), removeItem: jest.fn() }));
// 로컬 알림 예약(notify)은 expo-notifications 네이티브 모듈을 끌고 온다 — 판정은 reminders.test가 본다
jest.mock('./notify', () => ({ syncReminders: jest.fn(async () => {}), cancelReminders: jest.fn(async () => {}) }));
jest.mock('../api/client', () => ({ session: { token: jest.fn() } }));

import { Platform } from 'react-native';

import ReadingWidget from '../../widgets/ReadingWidget';
import { session } from '../api/client';
import type { Dashboard } from '../api/types';
import { clearSurfaces, syncSurfaces } from './surfaces';

const token = session.token as jest.Mock;
beforeEach(() => {
  mockStart.mockClear();
  (ReadingWidget.updateSnapshot as jest.Mock).mockClear();
  (ReadingWidget.updateTimeline as jest.Mock).mockClear();
});

// 범위 보충 REQ-106 — Live Activity는 widgetPayload가 정한 값(공부 측정 포함)으로 뜬다.
test('REQ-106 · 독서는 쉬고 공부가 측정 중이면 공부 모드 · 그 책 · 그 시작 시각으로 Live Activity를 띄운다', async () => {
  token.mockResolvedValue('x');
  expect(Platform.OS).toBe('ios');
  const studyAt = '2026-10-23T11:40:00Z';
  const d = {
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    todayReadSeconds: 0,
    study: { hasActiveSession: true, activeStartedAt: studyAt, activeBook: { title: '혼공머신' } },
  } as unknown as Dashboard;
  await syncSurfaces(d, new Date(2026, 9, 23, 21, 0));
  expect(mockStart).toHaveBeenCalledTimes(1);
  expect(mockStart.mock.calls[0][0]).toMatchObject({ mode: 'study', bookTitle: '혼공머신', lowerMs: Date.parse(studyAt) });
});

// 로그아웃 직전에 나간 대시보드 GET이 clearSurfaces보다 늦게 와도, 로그아웃한 폰에 책 제목 · 시간을 다시 그리지 않는다.
test('로그인 토큰이 없으면 측정 중 대시보드가 와도 위젯 · Live Activity를 그리지 않는다(있으면 그린다)', async () => {
  const d = {
    hasActiveSession: true,
    activeStartedAt: '2026-10-23T11:40:00Z',
    activeBookTitle: '데미안',
    todayReadSeconds: 0,
  } as unknown as Dashboard;
  token.mockResolvedValue(null);
  await syncSurfaces(d, new Date(2026, 9, 23, 21, 0));
  expect(ReadingWidget.updateTimeline).toHaveBeenCalledTimes(0);
  expect(ReadingWidget.updateSnapshot).toHaveBeenCalledTimes(0);
  expect(mockStart).toHaveBeenCalledTimes(0);

  token.mockResolvedValue('x');
  await syncSurfaces(d, new Date(2026, 9, 23, 21, 0));
  expect(ReadingWidget.updateTimeline).toHaveBeenCalledTimes(1);
  expect(mockStart).toHaveBeenCalledTimes(1);
});

// 위젯 확장 REQ-185 — 목표에 닿는 순간은 타임라인 둘째 항목이 앱을 안 열어도 그린다. 항목은 한 번에 쓴다(따로 쓰면 뒤가 앞을 덮는다).
test('REQ-185 · 대시보드를 읽으면 updateTimeline에 항목을 한 번에 쓴다(독서 측정 중 · 목표 전이면 둘)', async () => {
  token.mockResolvedValue('x');
  const now = new Date(2026, 9, 23, 21, 0);
  const d = {
    hasActiveSession: true,
    activeStartedAt: new Date(2026, 9, 23, 20, 50).toISOString(),
    activeBookTitle: '데미안',
    todayReadSeconds: 10 * 60,
    todayGoalSeconds: 30 * 60,
    graph: { goalStreak: 5 },
  } as unknown as Dashboard;
  await syncSurfaces(d, now);
  expect(ReadingWidget.updateTimeline).toHaveBeenCalledTimes(1);
  const entries = (ReadingWidget.updateTimeline as jest.Mock).mock.calls[0][0];
  expect(entries.map((e: { date: Date }) => e.date)).toEqual([now, new Date(2026, 9, 23, 21, 10)]);
  expect(entries[1].props.runNote).toBe('목표 30분 · 6일째');
  expect(mockStart).toHaveBeenCalledTimes(1); // LA는 지금 값으로 — 항목 수와 무관하게 한 번
});

// 로그아웃한 폰에 측정 단추 · 목표가 남지 않게 — 옛 꼴(날짜 · 0분)만(REQ-186 · 187).
test('REQ-186 · 로그인 화면이 되면 단추 · 목표 칸 없는 값으로 덮는다', async () => {
  await clearSurfaces();
  expect(ReadingWidget.updateSnapshot).toHaveBeenCalledTimes(1);
  const p = (ReadingWidget.updateSnapshot as jest.Mock).mock.calls[0][0];
  expect(Object.keys(p).sort()).toEqual(['dateLabel', 'mode', 'todayMinutes']);
  expect(p).toMatchObject({ todayMinutes: 0, mode: 'reading' });
});
