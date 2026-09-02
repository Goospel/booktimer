import { describe, expect, it, vi } from 'vitest';

import { createBackStack, handleNativeBack, nativeBack } from './back';

/**
 * 뒤로가기 스택 — 안드로이드 하드웨어/제스처 back이 미니앱을 종료시키지 않고 **열린 서브뷰 하나**를
 * 닫게 하는 규칙. 하니스에 jsdom이 없어 `history`를 어댑터로 주입해 전이만 계측한다(훅 자체는 배선).
 */

function stack() {
  const nav = { push: vi.fn(), back: vi.fn() };
  return { nav, back: createBackStack(nav) };
}

/**
 * 마이크로태스크 플러시 — `close()`의 엔트리 반납은 <b>같은 커밋의 `open()`이 물려받을 기회를 준 뒤</b>
 * 판정되므로(T-166), 반납 여부를 단언하려면 한 틱 흘려보내야 한다. 실제 브라우저에서도 popstate는
 * 태스크라 마이크로태스크가 먼저 끝난 뒤에 온다 — 순서가 하니스와 같다.
 */
const flush = () => Promise.resolve();

describe('뒤로가기 스택', () => {
  it('서브뷰가 열리면 히스토리 엔트리를 쌓는다 — 이게 없으면 back이 미니앱을 종료시킨다', () => {
    const { nav, back } = stack();

    back.open(() => {});

    expect(nav.push).toHaveBeenCalledTimes(1);
  });

  it('뒤로가기가 오면 그 서브뷰가 닫힌다', () => {
    const { back } = stack();
    const close = vi.fn();
    back.open(close);

    back.popped();

    expect(close).toHaveBeenCalledTimes(1);
  });

  it('버튼으로 닫으면 쌓아둔 엔트리를 회수한다 — 안 그러면 back 한 번이 아무 일도 안 하는 죽은 탭이 된다', async () => {
    const { nav, back } = stack();
    const close = vi.fn();
    const token = back.open(close);

    back.close(token);
    await flush();

    expect(nav.back).toHaveBeenCalledTimes(1);
    // 그 회수로 도착한 popstate는 우리 것이라 삼킨다 — 안 삼키면 아래 화면까지 연쇄로 닫힌다.
    back.popped();
    expect(close).not.toHaveBeenCalled();
  });

  it('뒤로가기로 닫힌 서브뷰의 정리는 회수하지 않는다 — 이중 회수는 뒤 화면까지 날린다', async () => {
    const { nav, back } = stack();
    const token = back.open(() => {});

    back.popped(); // 하드웨어 back이 이미 엔트리를 소비했다
    back.close(token); // 언마운트 정리
    await flush();

    expect(nav.back).not.toHaveBeenCalled();
  });

  it('중첩되면 최상단 하나만 닫힌다 — 여백 위 글 작성기에서 back은 작성기만 닫는다', () => {
    const { back } = stack();
    const closeProfile = vi.fn();
    const closeViewer = vi.fn();
    back.open(closeProfile);
    back.open(closeViewer);

    back.popped();

    expect(closeViewer).toHaveBeenCalledTimes(1);
    expect(closeProfile).not.toHaveBeenCalled();

    back.popped();
    expect(closeProfile).toHaveBeenCalledTimes(1);
  });

  /**
   * 「닫으면서 여는」 교체(시트 → 책방, 여백 → 책방) — 히스토리 <b>깊이</b>가 이 묶음의 주제다(T-166).
   *
   * <p>엔트리를 물려주기 전에는 새 화면이 열려 있는데 히스토리 인덱스만 앱 루트로 내려가, 그 화면에서
   * 누른 back·「돌아가기」가 미니앱을 통째로 종료시켰다. 화면 전환은 멀쩡해 보여서 눈으로는 안 잡힌다.
   */
  describe('닫으면서 여는 교체 — 엔트리를 물려준다', () => {
    it('교체에서는 push도 back도 하지 않는다 — 하나 나가고 하나 들어와 깊이가 그대로다', async () => {
      // 잡는 실패: 시트→책방 교체 뒤 히스토리가 한 칸 얕아져 그 화면의 「돌아가기」가 앱을 종료시키는 것.
      const { nav, back } = stack();
      const sheet = back.open(() => {});

      back.close(sheet); // 같은 커밋의 effect 정리
      back.open(() => {}); // 곧바로 다음 화면의 effect
      await flush();

      expect(nav.push).toHaveBeenCalledTimes(1); // 시트 것 하나뿐 — 책방은 그걸 물려받았다
      expect(nav.back).not.toHaveBeenCalled();
    });

    it('물려받은 엔트리도 정상 회수된다 — 그 popstate는 우리 것이라 삼킨다', async () => {
      // 잡는 실패: 물려받은 엔트리가 회수 불가가 돼, 책방에서 누른 back 한 번이 앱을 나가는 것.
      const { nav, back } = stack();
      const closeProfile = vi.fn();
      const sheet = back.open(() => {});
      back.close(sheet);
      const profile = back.open(closeProfile);
      await flush();

      back.close(profile);
      await flush();

      expect(nav.back).toHaveBeenCalledTimes(1);
      back.popped();
      expect(closeProfile).not.toHaveBeenCalled();
    });

    it('닫기 둘에 열기 하나면 남는 반납은 하나뿐이다 — 나머지를 흘리면 깊이가 남아돈다', async () => {
      // 잡는 실패: handoff 카운터가 한 건만 처리하고 나머지 반납을 통째로 잃는 것.
      const { nav, back } = stack();
      const a = back.open(() => {});
      const b = back.open(() => {});

      back.close(a);
      back.close(b);
      back.open(() => {}); // 둘 닫고 하나 여는 커밋
      await flush();

      expect(nav.push).toHaveBeenCalledTimes(2); // A·B 것뿐 — C는 물려받았다
      expect(nav.back).toHaveBeenCalledTimes(1); // 순 깊이 -1
    });

    it('열지 않고 그냥 닫으면 예전처럼 회수한다 — 물려주기가 평범한 닫기를 망가뜨리지 않는다', async () => {
      // 잡는 실패: handoff 도입이 단독 닫기의 회수를 삼켜, back 한 번이 아무 일도 안 하는 죽은 탭이 되는 것.
      const { nav, back } = stack();
      const token = back.open(() => {});

      back.close(token);
      await flush();

      expect(nav.push).toHaveBeenCalledTimes(1);
      expect(nav.back).toHaveBeenCalledTimes(1);
    });
  });

  it('열린 게 없을 때의 뒤로가기는 아무것도 닫지 않는다 — 미니앱 종료는 토스에 맡긴다', () => {
    const { nav, back } = stack();

    expect(() => back.popped()).not.toThrow();
    expect(nav.back).not.toHaveBeenCalled();
  });
});

/**
 * 토스 「<」 판정 — 실기기에서 **토스 상단 바의 「<」는 어느 화면에서든 미니앱을 통째로 닫는다**
 * (2026-09-02 아이폰 실측 — 히스토리 뒤로가기가 아니다). 그래서 `graniteEvent`의 `backEvent`를
 * 구독해(구독하면 기본 동작이 차단된다) **앱이 직접 정한다**: 서브뷰가 열려 있으면 그것만 닫고,
 * 첫 화면이면 미니앱을 닫는다(체크리스트 「최초 화면에서 뒤로가기를 누르면 미니앱이 종료돼요」).
 *
 * <p>닫는 수단이 `history.back()`인 것이 핵심이다 — 그러면 popstate → 위 `popped()`로 흘러
 * **기존 파이프라인과 같은 방식**으로 최상단 하나만 닫히고 히스토리 깊이도 맞는다(따로 세지 않는다).
 */
describe('네이티브 뒤로가기 판정 (handleNativeBack)', () => {
  const nav = (depth: number) => ({ depth: () => depth, back: vi.fn(), exit: vi.fn() });

  it('서브뷰가 열려 있으면 그것만 닫는다 — 앱을 종료하지 않는다', () => {
    const n = nav(1);

    expect(handleNativeBack(n)).toBe('subview');
    expect(n.back).toHaveBeenCalledTimes(1);
    expect(n.exit).not.toHaveBeenCalled();
  });

  it('첫 화면이면 미니앱을 닫는다 — 여기서 history.back()을 부르면 갈 곳이 없다', () => {
    const n = nav(0);

    expect(handleNativeBack(n)).toBe('exit');
    expect(n.exit).toHaveBeenCalledTimes(1);
    expect(n.back).not.toHaveBeenCalled();
  });

  it('중첩이어도 한 번에 하나만 닫는다 — 두 개가 열려 있어도 back은 1회다', () => {
    const n = nav(2);

    handleNativeBack(n);

    expect(n.back).toHaveBeenCalledTimes(1);
    expect(n.exit).not.toHaveBeenCalled();
  });

  it('depth가 열고 닫음을 따라간다 — 판정의 유일한 입력이라 어긋나면 앱이 엉뚱하게 죽는다', async () => {
    const { back } = stack();

    expect(back.depth()).toBe(0);

    const token = back.open(() => {});
    expect(back.depth()).toBe(1);

    back.close(token);
    await flush();
    expect(back.depth()).toBe(0);
  });
});

/**
 * 실물 배선 — 앱 밖(테스트·목 모드·일반 브라우저)에서 불려도 <b>던지지 않아야</b> 한다.
 *
 * <p>SDK의 `Screen.close`는 `callAsyncMethod('closeView')`이고, 그것이 Promise를 만들기 <b>전에</b>
 * `assertWebViewEnvironment()`를 동기로 부른다 — 브리지가 없으면 <b>동기 throw</b>라 `.catch()`가 못 받는다.
 * 이 호출은 `backEvent` 콜백 한가운데라, 새면 뒤로가기 핸들러가 통째로 죽는다.
 */
describe('네이티브 뒤로가기 실물 배선 (nativeBack)', () => {
  it('앱 밖에서 불려도 던지지 않는다 — Screen.close는 동기로도 던진다', () => {
    // 이 하니스엔 열린 서브뷰가 없다(depth 0) — 그래서 종료 갈래를 탄다.
    expect(() => nativeBack()).not.toThrow();
  });
});
