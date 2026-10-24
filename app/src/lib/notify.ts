// expo-notifications 얇은 감싸개(v1 REQ-33 · REQ-92 · REQ-153). 판정은 lib/reminders(순수 — 단위 테스트), 여기는 네이티브 호출만.
// 앱이 앞에 있을 때는 띄우지 않는다(setNotificationHandler를 두지 않는다 — 흐름 안 Notice가 맡는다). 권한은 스위치를 켤 때만 묻는다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Notifications from 'expo-notifications';

import { session } from '../api/client';
import type { Dashboard } from '../api/types';
import { DEFAULT_REMINDER, planNotifications, type ReminderSetting, scheduleDiff } from './reminders';

export type PermissionState = { status: 'granted' | 'denied' | 'undetermined'; canAsk: boolean };

const toState = (p: Notifications.NotificationPermissionsStatus): PermissionState => ({
  status: p.status === 'granted' ? 'granted' : p.status === 'denied' ? 'denied' : 'undetermined',
  canAsk: p.canAskAgain,
});

export const permission = async (): Promise<PermissionState> => toState(await Notifications.getPermissionsAsync());

/** 스위치를 켤 때만 — 진입 직후 묻지 않는다 */
export const askPermission = async (): Promise<PermissionState> =>
  toState(await Notifications.requestPermissionsAsync({ ios: { allowAlert: true, allowSound: true, allowBadge: false } }));

const KEY = 'booktimer.reminder';

/** 독서 알림 설정은 기기에 하나(로컬 알림이라 기기 것이다 — 서버에 저장하지 않는다). 로그아웃해도 남는다. */
export const reminderStore = {
  async get(): Promise<ReminderSetting> {
    try {
      const raw = await AsyncStorage.getItem(KEY);
      return raw === null ? DEFAULT_REMINDER : { ...DEFAULT_REMINDER, ...(JSON.parse(raw) as Partial<ReminderSetting>) };
    } catch {
      return DEFAULT_REMINDER;
    }
  },
  set: (s: ReminderSetting) => AsyncStorage.setItem(KEY, JSON.stringify(s)),
};

// 한 줄로 세운다 — 설정에서 끄는 동안 홈의 늦은 동기화가 다시 거는 경합을 막는다. 줄 안에서 설정 · 권한 · 로그인을 그 자리에서 다시 읽는다.
let tail: Promise<unknown> = Promise.resolve();
function serial(f: () => Promise<void>): Promise<void> {
  const r = tail.then(f, f);
  tail = r.catch(() => undefined);
  return r;
}

async function apply(plan: Parameters<typeof scheduleDiff>[1]) {
  const scheduled = (await Notifications.getAllScheduledNotificationsAsync()).map((n) => n.identifier);
  const { cancel, add } = scheduleDiff(scheduled, plan);
  for (const id of cancel) await Notifications.cancelScheduledNotificationAsync(id);
  for (const x of add) {
    await Notifications.scheduleNotificationAsync({
      identifier: x.id,
      content: { body: x.body, sound: true },
      trigger: { type: Notifications.SchedulableTriggerInputTypes.DATE, date: x.at },
    });
  }
}

/** 대시보드를 읽을 때마다(surfaces) · 설정의 켜기 · 시각 바꾸기 때. d가 null이면 오늘 채움을 모르니 오늘 것도 건다(다음 읽기가 고친다). */
export function syncReminders(d: Dashboard | null, now?: Date): Promise<void> {
  return serial(async () => {
    const [setting, perm, token] = await Promise.all([reminderStore.get(), permission(), session.token()]);
    await apply(planNotifications({ setting, permitted: perm.status === 'granted', signedIn: token !== null, now: (now ?? new Date()).getTime(), dash: d }));
  });
}

/** 우리 예약(reminder- · goal-) 전부 취소 — 로그인 화면이 될 때 */
export const cancelReminders = (): Promise<void> => serial(() => apply([]));
