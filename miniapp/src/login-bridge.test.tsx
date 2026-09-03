import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { login } from './api';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge, beginLogin } from './screens/LoginBridge';
import { userAgent } from './test-fixtures';
import { trackEvent } from './toss';

/**
 * 진입 인트로 — 심사 반려 1("서비스 설명 없이 즉시 토스 로그인을 유도")의 계측기.
 *
 * <p>하니스가 `renderToStaticMarkup`이라 effect도 클릭도 돌지 않는다. 그래서 "자동 로그인 안 함"은
 * **호출 카운트로 재면 공허**하고(effect 자체가 안 도니 항상 0), 첫 렌더가 **인트로 화면인지**로 잰다 —
 * 자동 로그인을 되살리면 초기 phase가 `checking`이 되어 로딩 화면이 나오므로 이 검사가 깨진다.
 * 클릭 → 로그인 배선은 정적 렌더의 사각이라 흐름만 {@link beginLogin}으로 꺼내 단독 계측한다
 * (관례: `claimDebtWaiver`·`tabChangeHandler`).
 */

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api')>()),
  login: vi.fn(),
  register: vi.fn(),
}));

vi.mock('./toss', () => ({
  trackEvent: vi.fn(),
  tossLogin: vi.fn(), // api.ts가 로그인 때 쓴다 — 모듈을 통째로 대체하므로 여기도 채워야 한다
}));

const loginMock = vi.mocked(login);
const trackEventMock = vi.mocked(trackEvent);

beforeEach(() => {
  loginMock.mockReset();
  trackEventMock.mockReset();
});

const intro = () =>
  renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <LoginBridge onAuthenticated={() => {}} onNewAccount={() => {}} onLinkAccount={() => {}} />
    </TDSMobileProvider>,
  );

describe('진입 인트로', () => {
  it('첫 화면은 서비스 소개다 — 무엇을 하는 앱인지 읽고 나서 로그인한다', () => {
    const markup = intro();

    expect(markup).toContain('책 읽는 시간을 타이머로 기록');
    expect(markup).toContain('토스 알림');
    expect(markup).toContain('booktimer.app');
  });

  it('로그인은 버튼을 눌러야 시작한다 — 진입 즉시 인가 화면이 뜨면 반려 사유 그대로다', () => {
    const markup = intro();

    expect(markup).toContain('토스로 시작하기');
    expect(markup).not.toContain('토스로 로그인하는 중'); // 초기 phase가 checking이면(=자동 로그인) 이게 뜬다
  });
});

describe('로그인 시작 (beginLogin)', () => {
  it('등록된 신원이면 홈으로 보낼 신호를 준다', async () => {
    loginMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel' });

    await expect(beginLogin()).resolves.toBe('authenticated');
  });

  it('미등록이면 새로 시작 / 계정 연결 선택으로 보낸다', async () => {
    // 서버는 미등록일 때 토큰을 주지 않는다 — 그 응답 모양 그대로.
    loginMock.mockResolvedValue({ registered: false, token: null, loginId: null });

    await expect(beginLogin()).resolves.toBe('choice');
  });

  it('실패는 그대로 올려보낸다 — 화면이 실패 문구를 그려야 한다', async () => {
    loginMock.mockRejectedValue(new Error('인가 취소'));

    await expect(beginLogin()).rejects.toThrow('인가 취소');
  });

  /**
   * 「토스로 시작하기」를 눌렀는가 — 진입(토스 자동 로그)과 첫 화면 사이의 가장 큰 미지수다.
   *
   * <p>안 눌렀으면 소개문 문제, 눌렀는데 홈·목표가 없으면 토스 인가·약관 단계 문제로 <b>처방이 완전히
   * 다르다</b>. 이 화면의 단계(`intro → checking`)는 컴포넌트 내부 상태라 App 수준 화면 로그에 안 잡히고,
   * 하니스는 클릭을 못 돌린다 — 그래서 이 흐름 함수가 유일한 계측 지점이다.
   */
  it('누른 사실을 먼저 남긴다 — 인가 결과와 무관하게 「눌렀다」가 퍼널의 한 칸이다', async () => {
    loginMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel' });

    await beginLogin();

    expect(trackEventMock).toHaveBeenCalledWith('login_started');
  });

  it('인가가 실패해도 눌렀다는 사실은 남는다 — 「눌렀는데 안 온 사람」을 가르는 유일한 점이다', async () => {
    loginMock.mockRejectedValue(new Error('인가 취소'));

    await expect(beginLogin()).rejects.toThrow('인가 취소');
    expect(trackEventMock).toHaveBeenCalledWith('login_started');
  });
});

/** 엔터 제출 — 연결 코드는 짧아서 입력 직후 곧바로 완료를 누른다. */
describe('계정 연결 엔터 제출', () => {
  it('연결 코드 입력을 form으로 감싼다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <LinkAccount onLinked={() => {}} />
      </TDSMobileProvider>,
    );

    expect(markup).toContain('<form');
  });
});

/**
 * 계정 연결의 나가는 길 — 책 추가·책방과 같은 규칙(**네이티브 뒤로가기 하나**, 2026-09-02 T-220).
 * 여기서는 `useBackClose(view === 'link', …)`가 받는다.
 */
describe('계정 연결 — 나가는 길', () => {
  const link = (extra: Record<string, unknown> = {}) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <LinkAccount onLinked={() => {}} {...extra} />
      </TDSMobileProvider>,
    );

  it('자체 「돌아가기」가 0건이다 — 제목은 그대로 선다', () => {
    const markup = link();

    expect(markup).toContain('기존 계정 연결');
    expect(markup.match(/돌아가기/g)).toBeNull();
  });
});
