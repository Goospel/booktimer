import { describe, expect, it, vi } from 'vitest';

import { createBackStack } from './back';

/**
 * 뒤로가기 스택 — 안드로이드 하드웨어/제스처 back이 미니앱을 종료시키지 않고 **열린 서브뷰 하나**를
 * 닫게 하는 규칙. 하니스에 jsdom이 없어 `history`를 어댑터로 주입해 전이만 계측한다(훅 자체는 배선).
 */

function stack() {
  const nav = { push: vi.fn(), back: vi.fn() };
  return { nav, back: createBackStack(nav) };
}

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

  it('버튼으로 닫으면 쌓아둔 엔트리를 회수한다 — 안 그러면 back 한 번이 아무 일도 안 하는 죽은 탭이 된다', () => {
    const { nav, back } = stack();
    const close = vi.fn();
    const token = back.open(close);

    back.close(token);

    expect(nav.back).toHaveBeenCalledTimes(1);
    // 그 회수로 도착한 popstate는 우리 것이라 삼킨다 — 안 삼키면 아래 화면까지 연쇄로 닫힌다.
    back.popped();
    expect(close).not.toHaveBeenCalled();
  });

  it('뒤로가기로 닫힌 서브뷰의 정리는 회수하지 않는다 — 이중 회수는 뒤 화면까지 날린다', () => {
    const { nav, back } = stack();
    const token = back.open(() => {});

    back.popped(); // 하드웨어 back이 이미 엔트리를 소비했다
    back.close(token); // 언마운트 정리

    expect(nav.back).not.toHaveBeenCalled();
  });

  it('중첩되면 최상단 하나만 닫힌다 — 프로필 위 스토리 뷰어에서 back은 뷰어만 닫는다', () => {
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

  it('닫으면서 여는 교체(뷰어 → 책방)에서 새 화면이 저 혼자 닫히지 않는다', () => {
    const { back } = stack();
    const closeViewer = vi.fn();
    const closeProfile = vi.fn();
    const viewer = back.open(closeViewer);

    back.close(viewer); // 같은 커밋에서 정리 → 회수 요청
    back.open(closeProfile); // 곧바로 다음 화면이 엔트리를 쌓는다
    back.popped(); // 회수가 뒤늦게 도착한다

    expect(closeProfile).not.toHaveBeenCalled();
    expect(closeViewer).not.toHaveBeenCalled();
  });

  it('열린 게 없을 때의 뒤로가기는 아무것도 닫지 않는다 — 미니앱 종료는 토스에 맡긴다', () => {
    const { nav, back } = stack();

    expect(() => back.popped()).not.toThrow();
    expect(nav.back).not.toHaveBeenCalled();
  });
});
