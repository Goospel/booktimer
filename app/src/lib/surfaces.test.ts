const mockStart = jest.fn();
jest.mock('../../widgets/ReadingActivity', () => ({ __esModule: true, default: { getInstances: () => [], start: (...a: unknown[]) => mockStart(...a) } }));
// ↑ 화살표로 감싼다 — import가 const 선언보다 먼저 돌아 팩토리 시점엔 mockStart가 아직 없다
jest.mock('../../widgets/ReadingWidget', () => ({ __esModule: true, default: { updateSnapshot: jest.fn() } }));
jest.mock('@react-native-async-storage/async-storage', () => ({ getItem: async () => null, setItem: jest.fn(), removeItem: jest.fn() }));
// 로컬 알림 예약(notify)은 expo-notifications 네이티브 모듈을 끌고 온다 — 판정은 reminders.test가 본다
jest.mock('./notify', () => ({ syncReminders: jest.fn(async () => {}), cancelReminders: jest.fn(async () => {}) }));

import { Platform } from 'react-native';

import type { Dashboard } from '../api/types';
import { syncSurfaces } from './surfaces';

// 범위 보충 REQ-106 — Live Activity는 widgetPayload가 정한 값(공부 측정 포함)으로 뜬다.
test('REQ-106 · 독서는 쉬고 공부가 측정 중이면 공부 모드 · 그 책 · 그 시작 시각으로 Live Activity를 띄운다', async () => {
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
