// 홈 · 공부 탭이 함께 쓰는 대시보드 읽기 · 앞으로 올 때 · 포커스 · 1초 틱 · 자정(타이머 오프라인 설계 2026-11-02 §6.7 — T0).
// 화면은 가게(measureSync)의 기준 값을 보이는 순간의 날로 굴린 값을 그린다 — 오프라인으로 켜도 마지막 홈이 그려진다(REQ-362).
// 판단은 순수 함수(rollToDay · goalNoticeStep)가 하고 여기는 연결만 한다(앱 하니스는 훅을 렌더하지 않는다).
import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import { AppState } from 'react-native';

import type { Dashboard } from '../api/types';
import { midnightRereadDelay, nextMidnightOf } from './goalProgress';
import { loadDashboard, measureStore } from './measureSync';
import { rollToDay } from './visibleDashboard';

const NONE = { d: null as Dashboard | null, rolled: false };

export function useMeasureDashboard(p: { mode: 'reading' | 'study' }) {
  const snap = useSyncExternalStore(measureStore.subscribe, measureStore.get);
  const base = snap?.base ?? null;
  const [clock, setNow] = useState(Date.now());
  const [loadError, setLoadError] = useState<string | null>(null);
  // 기준 값을 판정한 순간보다 이르게 그리지 않는다 — 다른 탭 · 루트가 가게를 바꿔도 이 탭의 시각은 묵어 있을 수 있다(시계가 음수가 되지 않게)
  const now = base ? Math.max(clock, base.at) : clock;
  const { d: dash, rolled } = useMemo(() => (base ? rollToDay(base, now) : NONE), [base, now]);
  // 이 실행에서 새로 읽은 값 — 디스크 값이면 null(위젯 REQ-182는 새로 읽은 값으로 판정 · 목표 띠는 디스크 값을 처음 값으로 삼지 않는다)
  const fresh = base && base.seq > 0 ? base.d : null;

  const inflight = useRef(0);
  const load = useCallback(async () => {
    inflight.current++;
    try {
      await loadDashboard();
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e)); // dash가 없을 때만 화면이 그린다(캐시가 있으면 마지막 홈)
    } finally {
      inflight.current--;
    }
    setNow(Date.now());
  }, []);

  // 가게가 열리는 순간 첫 읽기(차가운 시작). 이미 열린 채 마운트되면 아래 포커스가 읽는다 — 마운트 때 요청이 두 번 나가지 않게
  const open = snap !== null;
  const wasOpen = useRef(open);
  useEffect(() => {
    if (open && !wasOpen.current) void load();
    wasOpen.current = open;
  }, [open, load]);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => s === 'active' && void load());
    return () => sub.remove();
  }, [load]);

  // 포커스마다 다시 읽는다 — 쌓는 화면이나 다른 탭에서 돌아오면 옛 값이다(첫 마운트도 여기서 읽는다)
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const measuring = p.mode === 'reading' ? !!dash?.hasActiveSession && !!dash.activeStartedAt : !!dash?.study.hasActiveSession && !!dash.study.activeStartedAt;
  useEffect(() => {
    if (!measuring) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [measuring]);

  // 자정 — 측정 중엔 1초 틱이, 측정 전엔 이 예약이 시각을 넘긴다. 날이 바뀌어 굴린 값이 되면 한 번 다시 읽는다(옛 staleDay → 다시 읽기)
  const todayEnd = nextMidnightOf(now);
  const shown = dash !== null;
  useEffect(() => {
    if (measuring || !shown) return;
    const id = setTimeout(() => setNow(Date.now()), midnightRereadDelay(todayEnd, Date.now()));
    return () => clearTimeout(id);
  }, [measuring, shown, todayEnd]);
  useEffect(() => {
    if (rolled && inflight.current === 0) void load(); // 이미 나간 읽기가 있으면 그 응답이 오늘 값이다
  }, [rolled, todayEnd, load]);

  return { dash, now, rolled, loadError, load, fresh };
}
