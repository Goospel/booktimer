// Option+P를 그 화면의 측정 버튼에 잇는다(REQ-105). 판정은 measureShortcut.ts(테스트), 여기는 연결만.
import { useFocusEffect } from 'expo-router';
import { useCallback, useRef } from 'react';

import { onMeasureShortcut, setMeasureShortcutEnabled } from '../../modules/key-commands';
import { measureShortcut, repeatGate, shortcutHolders } from './measureShortcut';

const hold = shortcutHolders(setMeasureShortcutEnabled); // 앱에 하나(ponytail: Fast Refresh 뒤 셈이 어긋나면 다시 포커스하면 맞는다 — 개발에서만)

/** 그 화면의 측정 버튼을 Option+P에 잇는다(REQ-105). 이른 반환보다 위에서 부른다(훅 규칙). */
export function useMeasureShortcut(p: { button: (() => unknown) | null; busy: boolean; covered: boolean }) {
  const s = measureShortcut(p);
  const target = useRef(s.target);
  target.current = s.target; // 렌더마다 최신 처리기 — 1초 틱마다 다시 구독하지 않는다
  useFocusEffect(
    useCallback(() => {
      if (!s.on) return;
      const release = hold();
      const fresh = repeatGate();
      const off = onMeasureShortcut(() => {
        if (fresh(Date.now())) void target.current?.();
      });
      return () => {
        off();
        release();
      };
    }, [s.on]),
  );
}
