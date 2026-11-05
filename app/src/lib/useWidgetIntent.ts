// 위젯 단추가 걸어 둔 요청을 그 화면의 측정 처리기에 잇는다(위젯 확장 REQ-183 · 타이머 오프라인 REQ-370). 판정은 widgetIntent.ts(테스트), 여기는 연결만 — useMeasureShortcut과 같은 꼴.
import { useIsFocused } from 'expo-router';
import { useEffect, useRef, useSyncExternalStore } from 'react';

import { widgetDecision, widgetIntent, type WidgetMode } from './widgetIntent';

/**
 * 이른 반환보다 위에서 부른다(훅 규칙). ready = 보이는 대시보드가 있다(캐시 + 원장). 판정은 요청이 걸리는 순간 · 보이는 대시보드가 처음 준비되는 순간 ·
 * 탭이 포커스되는 순간에 한다 — 대시보드 읽기를 기다리지 않는다(오프라인에서 읽기가 실패해도 된다 — REQ-182를 대체).
 */
export function useWidgetIntent(p: { mode: WidgetMode; ready: boolean; measuring: boolean; button: (() => unknown) | null; onAlreadyStopped: () => void }) {
  // 포커스일 때만 가져간다(Option+P와 같다) — 쌓인 화면(여백 쓰기의 「쓰던 여백을 지울까요?」) 아래 홈이 확인 창 뒤에서 누르지 않는다.
  // 포커스되면 그때 가져간다(20초 안이면 — 위젯 확장 설계 §8-5 · U-109)
  const focused = useIsFocused();
  const version = useSyncExternalStore(widgetIntent.subscribe, widgetIntent.version);
  const latest = useRef(p);
  latest.current = p; // 렌더마다 최신 처리기 · 상태
  useEffect(() => {
    if (!p.ready || !focused) return; // 걸린 요청이 없으면 take가 null
    const i = widgetIntent.take(p.mode, Date.now());
    if (!i) return;
    const d = widgetDecision(i.action, latest.current.measuring);
    if (d === 'press') void latest.current.button?.();
    else if (d === 'already-stopped') latest.current.onAlreadyStopped();
  }, [version, p.ready, focused]);
}
