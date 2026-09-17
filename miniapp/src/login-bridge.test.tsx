import { readFileSync } from 'node:fs';

import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { login, register } from './api';
import type { LoginSource } from './screens/GuestHome';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge, beginLogin } from './screens/LoginBridge';
import { userAgent } from './test-fixtures';
import { trackEvent } from './toss';

/**
 * 로그인 <b>진행</b> 화면 — 인가 왕복(`checking`)과 그 결말(`choice`·`failed`)만 남은 화면이다.
 * 체험(인트로·재는 중·끝남)은 2026-09-11 게스트 홈으로 옮겼고, 「진입 즉시 로그인 유도」 반려 1의
 * 계측기도 그리로 따라갔다(`guest-home.test.tsx`) — 앱 첫 렌더가 `GuestShell`인지를 거기서 잰다.
 *
 * <p>하니스가 `renderToStaticMarkup`이라 effect도 클릭도 돌지 않는다. 그래서 여기서 재는 것은 둘이다:
 * 이 화면이 <b>마운트 즉시 인가로 들어가는가</b>(첫 렌더 = 「토스로 로그인하는 중」)와, 클릭 배선의
 * 사각을 대신하는 흐름 함수 {@link beginLogin}(어디서 눌렀는지가 `login_started`에 실리는가).
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
const registerMock = vi.mocked(register);
const trackEventMock = vi.mocked(trackEvent);

beforeEach(() => {
  loginMock.mockReset();
  registerMock.mockReset();
  trackEventMock.mockReset();
});

const bridge = (source: LoginSource) =>
  renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <LoginBridge source={source} onAuthenticated={() => {}} onNewAccount={() => {}} onLinkAccount={() => {}} />
    </TDSMobileProvider>,
  );

describe('로그인 진행 화면', () => {
  it('마운트하자마자 인가를 시작한다 — 이 화면은 사용자가 시작 버튼을 누른 뒤에만 선다', () => {
    // 첫 렌더가 곧 「토스로 로그인하는 중」이다(초기 phase = checking). 앱 <b>진입</b> 직후가 아니라
    // 게스트 홈의 손잡이를 누른 뒤라, 「서비스 설명 없이 즉시 로그인 유도」 반려와는 다른 자리다 —
    // 그 규칙의 계측기는 `guest-home.test.tsx`의 「첫 화면이 곧 타이머다」로 옮겼다.
    expect(bridge('trial')).toContain('토스로 로그인하는 중');
  });
});

describe('로그인 시작 (beginLogin)', () => {
  /**
   * 「새로 시작 / 기존 계정 연결」 선택 화면을 없앴다(2026-09-17). 손잡이 여섯은 처음부터 멱등
   * find-or-create인 `register`를 부른다 — 인가(appLogin) <b>1회</b>로 계정까지 간다. 옛 흐름은
   * `login`(조회) → 미등록이면 선택 화면 → `register`(두 번째 appLogin)였다.
   */
  it('기본 손잡이는 register를 부른다 — 인가 1회로 계정까지 간다', async () => {
    registerMock.mockResolvedValue({ registered: true, token: 'tok', loginId: null, created: true });

    await expect(beginLogin('header')).resolves.toBe('created');
    expect(registerMock).toHaveBeenCalledTimes(1);
    expect(loginMock).toHaveBeenCalledTimes(0);
  });

  it('이미 있는 계정이면 홈으로 보낼 신호를 준다', async () => {
    registerMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel', created: false });

    await expect(beginLogin('trial')).resolves.toBe('authenticated');
  });

  /**
   * 「이미 booktimer.app 계정이 있나요?」 — 웹 계정 보유자의 문이다. 여기서 register를 부르면 토스 신원이
   * 새 계정에 once-set으로 박혀 <b>영영 웹 계정에 못 붙는다</b>. 그래서 조회(`login`)만 하고 미등록이면
   * 연결 화면으로 곧장 간다.
   */
  it('web_link는 조회만 한다 — 미등록이면 연결 화면으로', async () => {
    loginMock.mockResolvedValue({ registered: false, token: null, loginId: null, created: false });

    await expect(beginLogin('web_link')).resolves.toBe('link');
    expect(loginMock).toHaveBeenCalledTimes(1);
    expect(registerMock).toHaveBeenCalledTimes(0);
  });

  it('web_link인데 이미 등록돼 있으면 그냥 홈이다 — 이미 연결된 사람이 누른 것이다', async () => {
    loginMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel', created: false });

    await expect(beginLogin('web_link')).resolves.toBe('authenticated');
  });

  it('실패는 그대로 올려보낸다 — 화면이 실패 문구를 그려야 한다', async () => {
    registerMock.mockRejectedValue(new Error('인가 취소'));

    await expect(beginLogin('book_card')).rejects.toThrow('인가 취소');
  });

  /**
   * 「토스로 시작하기」를 눌렀는가 — 진입(토스 자동 로그)과 첫 화면 사이의 가장 큰 미지수다.
   *
   * <p>안 눌렀으면 소개문 문제, 눌렀는데 홈·목표가 없으면 토스 인가·약관 단계 문제로 <b>처방이 완전히
   * 다르다</b>. 하니스는 클릭을 못 돌린다 — 그래서 이 흐름 함수가 유일한 계측 지점이다.
   */
  it('누른 사실을 먼저 남긴다 — 인가 결과와 무관하게 「눌렀다」가 퍼널의 한 칸이다', async () => {
    registerMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel', created: false });

    await beginLogin('header');

    expect(trackEventMock).toHaveBeenCalledWith('login_started', { source: 'header' });
  });

  it('인가가 실패해도 눌렀다는 사실은 남는다 — 「눌렀는데 안 온 사람」을 가르는 유일한 점이다', async () => {
    registerMock.mockRejectedValue(new Error('인가 취소'));

    await expect(beginLogin('trial')).rejects.toThrow('인가 취소');
    expect(trackEventMock).toHaveBeenCalledWith('login_started', { source: 'trial' });
  });

  /**
   * 게스트 홈은 로그인 손잡이가 <b>일곱</b>이다(헤더 · 책 카드 · 체험 결과 · 잠긴 탭 셋 · 기존 계정 연결).
   * 값 하나가 오타로 뭉개져도 이벤트는 멀쩡히 찍혀(문자열이므로) <b>조용히 한 층이 사라진다</b>.
   */
  it('손잡이 종류가 값 그대로 실린다 — 뭉개져도 이벤트는 찍히므로 값마다 본다', async () => {
    registerMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel', created: false });
    loginMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel', created: false });

    for (const source of ['header', 'book_card', 'locked_library', 'web_link'] as const) {
      await beginLogin(source);

      expect(trackEventMock).toHaveBeenCalledWith('login_started', { source });
    }
  });

  /**
   * 「눌러서 계정까지 간」 사람 — 변경 전엔 목표 화면(`screen_goal`)이 프록시였다. 손잡이별로 나눠 봐야
   * 어느 자리가 가입을 부르는지 안다.
   */
  it('계정이 새로 생겼을 때만 account_created를 source와 함께 남긴다', async () => {
    registerMock.mockResolvedValue({ registered: true, token: 'tok', loginId: null, created: true });
    await beginLogin('header');
    expect(trackEventMock.mock.calls).toEqual([
      ['login_started', { source: 'header' }],
      ['account_created', { source: 'header' }],
    ]);

    trackEventMock.mockReset();
    registerMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel', created: false });
    await beginLogin('header');
    // 기존 계정 — 호출 목록 전체로 단언한다(「안 불렀다」만 보는 부정 단언은 판별력이 없다, T-149).
    expect(trackEventMock.mock.calls).toEqual([['login_started', { source: 'header' }]]);
  });

  /** 선택 화면 삭제 가드 — 첫 렌더는 늘 「로그인하는 중」이라 마크업으로는 못 잰다. 소스로 잰다. */
  it('선택 화면이 되살아나지 않는다 — 「새로 시작」 버튼이 소스에 없다', () => {
    const src = readFileSync(new URL('./screens/LoginBridge.tsx', import.meta.url), 'utf8');

    expect(src.match(/새로 시작/g)).toBeNull();
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
