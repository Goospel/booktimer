// 잠금화면 · 홈 위젯에 대시보드를 밀어 넣는 얇은 감싸개(v1 §4.10). 판정은 순수 함수(liveActivity · widgetPayload)가 하고,
// 여기는 expo-widgets 호출과 「이 기기가 띄운 활동의 값」 보관만 한다 — LiveActivity는 자기 props를 돌려주지 않는다.
// 부르는 때: 대시보드를 읽을 때마다(앱 진입 · 앞으로 올 때 · 시작 · 정지 뒤).
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

import ReadingActivity from '../../widgets/ReadingActivity';
import ReadingWidget from '../../widgets/ReadingWidget';
import type { Dashboard } from '../api/types';
import { activityTimes, knownActivity, liveActivityAction, type LocalActivity } from './liveActivity';
import { widgetPayload } from './widgetPayload';

const KEY = 'booktimer.liveActivity';

export async function syncSurfaces(d: Dashboard, now = new Date()): Promise<void> {
  if (Platform.OS !== 'ios') return;
  try {
    ReadingWidget.updateSnapshot(widgetPayload(d, now));
  } catch (e) {
    console.warn('widget', e);
  }
  try {
    const instances = ReadingActivity.getInstances();
    const local = knownActivity(instances.length > 0 ? await AsyncStorage.getItem(KEY) : null, instances.length);
    const startedAt = d.hasActiveSession && d.activeStartedAt ? Date.parse(d.activeStartedAt) : null;
    const action = liveActivityAction({ active: startedAt !== null, startedAt, bookTitle: d.activeBookTitle }, local);
    if (action === 'end' || action === 'restart') {
      await Promise.all(instances.map((a) => a.end('immediate')));
      await AsyncStorage.removeItem(KEY);
    }
    if ((action === 'start' || action === 'restart') && startedAt !== null) {
      const t = activityTimes(startedAt);
      ReadingActivity.start({ bookTitle: d.activeBookTitle ?? '', lowerMs: t.lowerMs, upperMs: t.upperMs }, 'booktimer://', new Date(t.staleMs));
      const saved: LocalActivity = { startedAt, bookTitle: d.activeBookTitle };
      await AsyncStorage.setItem(KEY, JSON.stringify(saved));
    }
  } catch (e) {
    console.warn('liveActivity', e);
  }
}
