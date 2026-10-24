// expo-notifications는 import만 해도 「앞에서도 배너 · 소리」 기본 핸들러를 구독한다(58.x NotificationsHandler) —
// 앞에 있을 때 띄우지 않으려면 핸들러를 명시로 덮어야 한다(흐름 안 Notice가 맡는다).
jest.mock('expo-notifications', () => ({ setNotificationHandler: jest.fn(), SchedulableTriggerInputTypes: { DATE: 'date' } }));
jest.mock('expo-secure-store', () => ({ getItemAsync: jest.fn(), setItemAsync: jest.fn(), deleteItemAsync: jest.fn() }));
jest.mock('@react-native-async-storage/async-storage', () => ({ getItem: async () => null, setItem: jest.fn(), removeItem: jest.fn() }));

import * as Notifications from 'expo-notifications';

import './notify';

test('앱이 앞에 있을 때 알림을 띄우지 않는 핸들러를 한 번 둔다(배너 · 목록 · 소리 · 배지 0)', async () => {
  const set = Notifications.setNotificationHandler as jest.Mock;
  expect(set).toHaveBeenCalledTimes(1);
  const handler = set.mock.calls[0][0] as { handleNotification: () => Promise<unknown> };
  await expect(handler.handleNotification()).resolves.toEqual({
    shouldShowBanner: false,
    shouldShowList: false,
    shouldPlaySound: false,
    shouldSetBadge: false,
  });
});
