import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { DashboardResponse } from './api';
import { logout } from './api';
import { DeleteAccountSection, LogoutSection, Settings, logoutAndLeave } from './screens/Settings';
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
        goalAdPending={false}
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
 * 약관·처리방침 — 개인정보 고지 의무이자 심사 요건. 미니앱 안에는 이 링크가 **하나도 없었다**:
 * 토스로 가입한 계정은 웹에 로그인할 수 없어 웹 푸터로도 닿지 못한다.
 */
describe('약관·처리방침 링크', () => {
  it('개인정보 처리방침과 이용약관에 닿는 손잡이가 있다', () => {
    const markup = renderSettings();

    expect(markup).toContain('개인정보 처리방침');
    expect(markup).toContain('이용약관');
  });
});

/**
 * 회원 탈퇴 — 미니앱 전용 계정의 **유일한** 탈퇴 경로(웹 `/settings/delete`는 비밀번호가 없어 못 닿는다).
 *
 * <p>되돌릴 수 없는 동작이라 UI가 2단이다: 진입 버튼 → 시트(경고 + danger 버튼). 시트 상태를 프롭으로
 * 받는 이유는 로그아웃과 같다 — 정적 렌더 하니스(T-149)가 클릭을 못 잡아, 프롭이 아니면 열린 시트에
 * 영영 닿지 못한다. TDS `BottomSheet`이 아니라 자체 `Sheet`를 쓰는 것도 그래서다(포털은 마크업이 통째로 빈다).
 */
describe('회원 탈퇴 섹션', () => {
  const section = (open: boolean, error: string | null = null) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <DeleteAccountSection
          open={open}
          busy={false}
          error={error}
          onOpen={() => {}}
          onClose={() => {}}
          onDelete={() => {}}
        />
      </TDSMobileProvider>,
    );

  it('닫힌 상태에선 진입 버튼만 — 경고도 실행 버튼도 아직 없다', () => {
    const markup = section(false);

    expect(markup).toContain('회원 탈퇴');
    expect(markup).not.toContain('모두 삭제하고 탈퇴');
  });

  it('열면 무엇이 사라지는지 먼저 말한다 — 영구 삭제·복구 불가를 읽고 나서 누르게', () => {
    const markup = section(true);

    expect(markup).toContain('영구히 삭제');
    expect(markup).toContain('되돌릴 수 없어요');
    expect(markup).toContain('모두 삭제하고 탈퇴');
    expect(markup).toContain('취소');
  });

  it('웹 계정과 연결된 사람에게 웹 기록까지 지워진다고 상시 고지한다 — 반쪽 탈퇴는 존재하지 않는다', () => {
    // 클라이언트는 authProvider를 모르므로 조건 노출이 아니라 상시 문구다(서버에 필드를 새로 파지 않는다).
    expect(section(true)).toContain('웹(booktimer.app)에서 쓰던 계정이라면 웹 기록까지 모두 삭제됩니다');
  });

  it('실패하면 서버 평문을 시트 안에 띄운다 — 화면을 닫아 버리면 왜 안 됐는지 알 길이 없다', () => {
    expect(section(true, '본인 확인에 실패해 탈퇴를 진행하지 않았어요.')).toContain(
      '본인 확인에 실패해 탈퇴를 진행하지 않았어요.',
    );
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
