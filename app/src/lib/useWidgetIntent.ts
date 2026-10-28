// 위젯 단추가 걸어 둔 요청을 그 화면의 측정 처리기에 잇는다(위젯 확장 REQ-182 · 183). 판정은 widgetIntent.ts(테스트), 여기는 연결만 — useMeasureShortcut과 같은 꼴.
import { useEffect, useRef } from 'react';

import { widgetDecision, widgetIntent, type WidgetMode } from './widgetIntent';

/** 이른 반환보다 위에서 부른다(훅 규칙). fresh = 새로 읽은 대시보드 — 바뀔 때마다(첫 값 포함) 한 번 본다. */
export function useWidgetIntent(p: { mode: WidgetMode; fresh: unknown; measuring: boolean; button: (() => unknown) | null; onAlreadyStopped: () => void }) {
  const latest = useRef(p);
  latest.current = p; // 렌더마다 최신 처리기 · 상태
  useEffect(() => {
    if (p.fresh == null) return; // 걸린 요청이 없으면 take가 null
    const i = widgetIntent.take(p.mode, Date.now());
    if (!i) return;
    const d = widgetDecision(i.action, latest.current.measuring);
    if (d === 'press') void latest.current.button?.();
    else if (d === 'already-stopped') latest.current.onAlreadyStopped();
  }, [p.fresh]);
}
