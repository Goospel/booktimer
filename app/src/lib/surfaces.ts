// 잠금화면 · 홈 위젯에 대시보드를 밀어 넣는 얇은 감싸개(v1 §4.10). 판정은 순수 함수(liveActivity · widgetPayload)가 하고,
// 여기는 expo-widgets 호출과 「이 기기가 띄운 활동의 값」 보관만 한다 — LiveActivity는 자기 props를 돌려주지 않는다.
// 부르는 때: 대시보드를 읽을 때마다(앱 진입 · 앞으로 올 때 · 시작 · 정지 뒤). 로컬 알림(독서 알림 · 목표 달성)도 여기서 다시 맞춘다(PR-I).
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

import ReadingActivity from '../../widgets/ReadingActivity';
import ReadingWidget from '../../widgets/ReadingWidget';
import { session } from '../api/client';
import type { Dashboard } from '../api/types';
import { activityTimes, knownActivity, liveActivityAction, type LocalActivity } from './liveActivity';
import { cancelReminders, syncReminders } from './notify';
import { widgetPayload } from './widgetPayload';

const KEY = 'booktimer.liveActivity';

export async function syncSurfaces(d: Dashboard, now = new Date()): Promise<void> {
  if (Platform.OS !== 'ios') return;
  // 로그아웃 직전에 나간 대시보드 GET이 clearSurfaces보다 늦게 와도 로그아웃한 폰에 책 제목 · 시간을 다시 그리지 않는다
  if ((await session.token()) === null) return;
  // 「무엇을 재는 중인가」(독서 · 공부)는 widgetPayload 한 곳이 정하고 위젯과 Live Activity가 같은 값을 쓴다(범위 보충 REQ-106).
  const p = widgetPayload(d, now);
  try {
    ReadingWidget.updateSnapshot(p);
  } catch (e) {
    console.warn('widget', e);
  }
  try {
    const instances = ReadingActivity.getInstances();
    const local = knownActivity(instances.length > 0 ? await AsyncStorage.getItem(KEY) : null, instances.length);
    const startedAt = p.activeStartedAt ?? null;
    const bookTitle = p.bookTitle ?? null;
    // 판정에 모드를 넣지 않는다 — 독서 · 공부는 서로를 막아 차례로만 생기므로 모드가 바뀌면 시작 시각이 늘 바뀐다(사양 §5.7)
    const action = liveActivityAction({ active: startedAt !== null, startedAt, bookTitle }, local);
    if (action === 'end' || action === 'restart') {
      await Promise.all(instances.map((a) => a.end('immediate')));
      await AsyncStorage.removeItem(KEY);
    }
    // 위 await(AsyncStorage · LA 끝) 사이에 로그아웃했을 수 있다 — 띄우기 직전에 한 번 더 본다
    if ((action === 'start' || action === 'restart') && startedAt !== null && (await session.token()) !== null) {
      const t = activityTimes(startedAt);
      ReadingActivity.start({ bookTitle: bookTitle ?? '', lowerMs: t.lowerMs, upperMs: t.upperMs, mode: p.mode }, 'booktimer://', new Date(t.staleMs));
      const saved: LocalActivity = { startedAt, bookTitle };
      await AsyncStorage.setItem(KEY, JSON.stringify(saved));
    }
  } catch (e) {
    console.warn('liveActivity', e);
  }
  try {
    await syncReminders(d, now);
  } catch (e) {
    console.warn('reminders', e);
  }
}

/** 로그인 화면이 되면(로그아웃 · 탈퇴 · 401) — 예약 알림 전부 취소 · LA 끝 · 위젯 0분. 로그아웃한 폰에 책 제목 · 시간이 남지 않게. */
export async function clearSurfaces(): Promise<void> {
  try {
    await cancelReminders();
  } catch (e) {
    console.warn('reminders', e);
  }
  if (Platform.OS !== 'ios') return;
  try {
    ReadingWidget.updateSnapshot(widgetPayload({ hasActiveSession: false, activeStartedAt: null, activeBookTitle: null, todayReadSeconds: 0 }, new Date()));
  } catch (e) {
    console.warn('widget', e);
  }
  try {
    await Promise.all(ReadingActivity.getInstances().map((a) => a.end('immediate')));
    await AsyncStorage.removeItem(KEY);
  } catch (e) {
    console.warn('liveActivity', e);
  }
}
