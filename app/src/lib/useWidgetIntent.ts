// 위젯 단추가 걸어 둔 요청을 그 화면의 측정 처리기에 잇는다(위젯 확장 REQ-182 · 183). 판정은 widgetIntent.ts(테스트), 여기는 연결만 — useMeasureShortcut과 같은 꼴.
import { useIsFocused } from 'expo-router';
import { useEffect, useRef } from 'react';

import { widgetDecision, widgetIntent, type WidgetMode } from './widgetIntent';

/** 이른 반환보다 위에서 부른다(훅 규칙). fresh = 새로 읽은 대시보드 — 바뀔 때마다(첫 값 포함) 한 번 본다. */
export function useWidgetIntent(p: { mode: WidgetMode; fresh: unknown; measuring: boolean; button: (() => unknown) | null; onAlreadyStopped: () => void }) {
  // 포커스일 때만 가져간다(Option+P와 같다) — 쌓인 화면(여백 쓰기의 「쓰던 여백을 지울까요?」) 아래 홈이 AppState로 다시 읽어도
  // 확인 창 뒤에서 누르지 않는다. 포커스되면 useFocusEffect가 다시 읽어 새 대시보드로 그때 가져간다(20초 안이면 — 설계 §8-5 · U-109)
  const focused = useIsFocused();
  const latest = useRef({ ...p, focused });
  latest.current = { ...p, focused }; // 렌더마다 최신 처리기 · 상태
  useEffect(() => {
    if (p.fresh == null || !latest.current.focused) return; // 걸린 요청이 없으면 take가 null
    const i = widgetIntent.take(p.mode, Date.now());
    if (!i) return;
    const d = widgetDecision(i.action, latest.current.measuring);
    if (d === 'press') void latest.current.button?.();
    else if (d === 'already-stopped') latest.current.onAlreadyStopped();
  }, [p.fresh]);
}
