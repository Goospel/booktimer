import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { DashboardResponse } from './api';
import { logout } from './api';
import { LogoutSection, Settings, logoutAndLeave } from './screens/Settings';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api')>()),
  logout: vi.fn(),
  updateNickname: vi.fn(),
}));

const logoutMock = vi.mocked(logout);

beforeEach(() => {
  stubLocalStorage();
  logoutMock.mockReset();
});

/**
 * 프로필·설정 화면 — 미니앱에서 닉네임·@아이디·하루 목표·로그아웃에 닿는 유일한 자리.
 *
 * <p>토스로 가입한 계정은 비밀번호가 없어 웹 `/settings`에 영영 닿지 못한다 — 그래서 이 화면이 생기기 전엔
 * 닉네임이 전원 기본값("토스유저")에 묶여 있었다. 하니스가 정적 렌더라(T-149) 저장 뮤테이션은 api 계층이
 * 계측하고, 여기서는 **서버가 준 상태(loginId 유무)가 화면의 무엇을 켜고 끄는가**를 본다.
 */

const dashboard: DashboardResponse = {
  nickname: '구스펠',
  loginId: 'goospel',
  profileCharacterCode: null,
  remainingSeconds: 900,
  carriedDebtSeconds: 0,
  todayGoalSeconds: 3600,
  carryover: false,
  hasActiveSession: false,
  activeStartedAt: null,
  activeBookTitle: null,
  activeBookTotalSeconds: 0,
  readingBooks: [],
  finishedBooks: [],
  wantToReadBooks: [],
  recentBookId: null,
  debtWaiverAvailable: false,
  graph,
  quotes: [],
  emailVerified: true,
};

function renderSettings(extra: Partial<DashboardResponse> = {}) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Settings
        dashboard={{ ...dashboard, ...extra }}
        onBack={() => {}}
        onProfileChanged={() => {}}
        onGoGoal={() => {}}
        onLogout={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

describe('닉네임 섹션', () => {
  it('지금 닉네임이 입력칸에 실려 있다 — 빈 칸이면 뭘 바꾸는지 모른 채 새로 쳐야 한다', () => {
    expect(renderSettings()).toContain('value="구스펠"');
  });

  it('기본 닉네임("토스유저") 계정도 그 값이 실린다 — 이 화면이 존재하는 이유 자체다', () => {
    expect(renderSettings({ nickname: '토스유저' })).toContain('value="토스유저"');
  });
});

/**
 * @아이디 섹션 — **null-state 경계**(N-055 계열). 핸들 없는 계정은 소셜 전 경로에서 안 보이므로
 * 「만들기」가 떠야 하고, 있는 계정에는 바꿀 수 없다는 사실만 알려야 한다(만들기 버튼이 또 뜨면 409를 부른다).
 */
describe('@아이디 섹션', () => {
  it('핸들이 없으면 「아이디 만들기」를 띄우고 @표시는 하지 않는다', () => {
    const markup = renderSettings({ loginId: null });

    expect(markup).toContain('아이디 만들기');
    expect(markup).not.toContain('@goospel');
  });

  it('핸들이 있으면 @핸들과 불변 안내만 — 만들기 버튼은 사라진다', () => {
    const markup = renderSettings({ loginId: 'goospel' });

    expect(markup).toContain('@goospel');
    expect(markup).toContain('한 번 정하면 바꿀 수 없어요');
    expect(markup).not.toContain('아이디 만들기');
  });
});

describe('하루 목표 섹션', () => {
  it('목표를 바꾸러 가는 손잡이가 있다 — 홈에서 사라진 진입점을 여기가 대신한다', () => {
    expect(renderSettings()).toContain('하루 목표 바꾸기');
  });
});

/**
 * 로그아웃 — 홈 하단에 있던 2단 확인을 이 화면으로 옮겼다. 확인 단계를 프롭으로 받는 이유는 늘 같다:
 * 정적 렌더 하니스가 클릭을 못 잡아, 프롭이 아니면 「정말 로그아웃」 가지에 영영 닿지 못한다.
 */
describe('로그아웃 섹션', () => {
  const section = (confirm: boolean) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <LogoutSection confirm={confirm} onConfirm={() => {}} onLogout={() => {}} />
      </TDSMobileProvider>,
    );

  it('처음엔 「로그아웃」만 — 실수 한 탭에 세션이 날아가지 않게', () => {
    expect(section(false)).toContain('로그아웃');
    expect(section(false)).not.toContain('정말 로그아웃');
  });

  it('한 번 더 물어본 뒤 실행한다 — 물러설 길(취소)도 함께 준다', () => {
    expect(section(true)).toContain('정말 로그아웃');
    expect(section(true)).toContain('취소');
  });

  it('죽은 안내는 없다 — 토스로 가입한 계정은 booktimer.app에 로그인할 수 없다', () => {
    expect(renderSettings()).not.toContain('booktimer.app');
  });
});

/**
 * 로그아웃 흐름 — 서버 폐기가 실패해도 **반드시** 로그인 화면으로 넘긴다. 안 넘기면 토큰은 이미
 * 버려졌는데(api.logout의 finally) 화면은 설정에 남아, 무엇을 눌러도 401만 나는 막다른 길이 된다.
 */
describe('로그아웃 — logoutAndLeave', () => {
  it('서버 폐기가 성공하면 로그인 화면으로 넘긴다', async () => {
    logoutMock.mockResolvedValue(undefined);
    const onDone = vi.fn();

    await logoutAndLeave(onDone);

    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it('서버 폐기가 실패해도 로그인 화면으로 넘긴다 — 안 넘기면 401만 나는 화면에 갇힌다', async () => {
    logoutMock.mockRejectedValue(new Error('Failed to fetch'));
    const onDone = vi.fn();

    await logoutAndLeave(onDone);

    expect(onDone).toHaveBeenCalledTimes(1);
  });
});
