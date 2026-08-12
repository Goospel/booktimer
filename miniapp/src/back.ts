import { useEffect, useRef } from 'react';

/**
 * 안드로이드 하드웨어/제스처 뒤로가기 — 열린 서브뷰를 닫는다.
 *
 * <p>미니앱은 라우터가 없어 히스토리 엔트리가 하나뿐이었다: 스토리 뷰어·책방·검색 어디서 back을 눌러도
 * 그 한 칸을 소비해 **미니앱 자체가 종료**됐다. 서브뷰가 열릴 때 엔트리를 하나 쌓아 back이 그걸 먼저
 * 소비하게 하고, 버튼으로 닫힐 땐 쌓아둔 엔트리를 회수해 죽은 back 탭이 남지 않게 한다.
 */

/** 히스토리 어댑터 — jsdom 없는 하니스에서 전이만 계측하려고 주입 가능하게 뺐다. */
export interface BackNav {
  push(): void;
  back(): void;
}

/**
 * 열린 서브뷰 스택. 규칙 두 개가 전부다:
 * ① popstate는 **최상단 하나만** 닫는다(중첩에서 아래 화면까지 연쇄로 닫히지 않게).
 * ② 우리가 부른 `back()`이 만든 popstate는 삼킨다 — 버튼으로 닫으며 회수한 엔트리가 그 다음 화면을
 *    (닫으면서 여는 교체 경로에서) 저 혼자 닫아버리는 걸 막는다.
 */
export function createBackStack(nav: BackNav) {
  const entries: { token: number; onClose: () => void }[] = [];
  let selfPops = 0;
  let nextToken = 1;

  return {
    /** 서브뷰가 열렸다 — 엔트리를 쌓고 정리에 쓸 토큰을 준다. */
    open(onClose: () => void): number {
      const token = nextToken++;
      entries.push({ token, onClose });
      nav.push();
      return token;
    },

    /** 서브뷰가 스스로 닫혔다(버튼·언마운트) — 아직 살아 있을 때만 엔트리를 회수한다. */
    close(token: number): void {
      const index = entries.findIndex((entry) => entry.token === token);
      if (index < 0) return; // 이미 뒤로가기가 소비했다 — 또 회수하면 뒤 화면까지 날아간다
      entries.splice(index, 1);
      selfPops++;
      nav.back();
    },

    /** popstate 도착. */
    popped(): void {
      if (selfPops > 0) {
        selfPops--;
        return;
      }
      entries.pop()?.onClose();
    },
  };
}

const backStack = createBackStack({
  push: () => history.pushState({ booktimer: 'subview' }, ''),
  back: () => history.back(),
});

// 리스너는 모듈당 하나 — 훅마다 달면 한 번의 back이 열린 서브뷰 전부를 닫는다(중첩 규칙이 깨진다).
if (typeof window !== 'undefined') window.addEventListener('popstate', () => backStack.popped());

/**
 * 서브뷰가 열려 있는 동안 뒤로가기를 `onClose`로 돌린다.
 *
 * <p>`active`가 true가 되는 순간 엔트리를 쌓고, false가 되거나 언마운트되면 회수한다.
 */
export function useBackClose(active: boolean, onClose: () => void): void {
  const latest = useRef(onClose);
  latest.current = onClose; // 콜백이 매 렌더 새로 만들어져도 엔트리를 다시 쌓지 않는다

  useEffect(() => {
    if (!active) return;
    const token = backStack.open(() => latest.current());
    return () => backStack.close(token);
  }, [active]);
}
