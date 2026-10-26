// Option+P 판정(REQ-105). import 0 — 테스트가 그대로 읽는다.
export const REPEAT_GAP_MS = 1000; // 조절 상수 — U-99 ③(누르고 있기)이 실패하면 키 반복 지연에 맞춰 올린다

/** 화면에 측정 버튼이 없거나 시트가 덮으면 꺼짐(on=false) · 요청 중이면 켠 채 무시(target=null). target은 버튼의 처리기 그 함수다. */
export function measureShortcut(p: { button: (() => unknown) | null; busy: boolean; covered: boolean }) {
  const on = p.button !== null && !p.covered;
  return { on, target: on && !p.busy ? p.button : null };
}

/** 보유자 셈 — 네이티브 켜짐 플래그는 앱에 하나다. 첫 보유자가 켜고 마지막 보유자가 놓을 때만 끈다(두 탭의 포커스 · 블러 순서와 무관). */
export function shortcutHolders(setNative: (on: boolean) => void): () => () => void {
  let count = 0;
  return () => {
    if (count++ === 0) setNative(true);
    let held = true;
    return () => {
      if (!held) return; // 두 번 놓아도 한 번
      held = false;
      if (--count === 0) setNative(false);
    };
  };
}

/** 앞 이벤트(버린 것 포함)와 gap 안이면 거짓 — 키를 누르고 있어 이어 오는 반복을 버린다(반복 여부는 U-99). */
export function repeatGate(gap = REPEAT_GAP_MS): (now: number) => boolean {
  let prev: number | null = null;
  return (now) => {
    const ok = prev === null || now - prev >= gap;
    prev = now;
    return ok;
  };
}
