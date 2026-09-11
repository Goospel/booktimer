import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { login } from './api';
import { LinkAccount } from './screens/LinkAccount';
import { LoginBridge, beginLogin } from './screens/LoginBridge';
import { stubLocalStorage, userAgent } from './test-fixtures';
import { trackEvent } from './toss';
import type { Trial } from './trial';
import { TRIAL_CAP_SECONDS, flushTrial, trialDurationSeconds, writeTrial } from './trial';

/**
 * 진입 첫 화면 — 심사 반려 1("서비스 설명 없이 즉시 토스 로그인을 유도")의 계측기이자, 2026-09-11부터는
 * **로그인 전 체험**(타이머 카드 → 「읽기 시작」)의 계측기다. 소개 화면 21명 중 4명만 버튼을 눌렀고,
 * 이탈은 토스 동의창 **이전**에 났다 — 첫 탭의 대가를 0으로 만드는 것이 이 화면의 일이다.
 *
 * <p>하니스가 `renderToStaticMarkup`이라 effect도 클릭도 돌지 않는다. 그래서 "자동 로그인 안 함"은
 * **호출 카운트로 재면 공허**하고(effect 자체가 안 도니 항상 0), 첫 렌더가 **체험 화면인지**로 잰다 —
 * 자동 로그인을 되살리면 초기 phase가 `checking`이 되어 로딩 화면이 나오므로 이 검사가 깨진다.
 * 세 상태(체험 전·재는 중·끝남)는 `trial` prop 주입으로 각각 그려 본다(관례: `Home`의 `celebrate`).
 * 클릭 → 로그인 배선은 정적 렌더의 사각이라 흐름만 {@link beginLogin}으로 꺼내 단독 계측한다.
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

const bridge = (trial: Trial | null) =>
  renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <LoginBridge trial={trial} onAuthenticated={() => {}} onNewAccount={() => {}} onLinkAccount={() => {}} />
    </TDSMobileProvider>,
  );

describe('첫 화면 — 로그인 없이 재 본다', () => {
  it('무엇을 하는 앱인지 한 줄로 읽힌다 — 인트로 서비스 설명은 심사 필수 항목이다', () => {
    expect(bridge(null)).toContain('책 읽는 시간을 타이머로 기록');
  });

  it('첫 화면이 곧 타이머다 — 소개문만 있던 화면에서 17/21이 떠났다', () => {
    const markup = bridge(null);

    expect(markup).toContain('00:00');
    expect(markup).toContain('읽기 시작');
  });

  it('로그인은 누를 때만 시작한다 — 진입 즉시 인가 화면이 뜨면 반려 사유 그대로다', () => {
    const markup = bridge(null);

    expect(markup).toContain('토스로 로그인');
    expect(markup).not.toContain('토스로 로그인하는 중'); // 초기 phase가 checking이면(=자동 로그인) 이게 뜬다
  });

  it('재는 중에는 그만둘 손잡이 하나뿐이다', () => {
    const markup = bridge({ startedAt: new Date(Date.now() - 90_000).toISOString(), endedAt: null });

    expect(markup).toContain('그만 읽기');
  });

  it('다 재고 나서야 로그인을 청한다 — 그때는 잃을 것이 생긴 뒤다', () => {
    const markup = bridge({ startedAt: '2026-09-11T01:00:00.000Z', endedAt: '2026-09-11T01:07:00.000Z' });

    expect(markup).toContain('7분');
    expect(markup).toContain('기록을 남기려면');
    expect(markup).toContain('토스로 시작하기');
  });

  it('거절할 길을 같은 화면에 둔다 — 남길지 묻는 화면에서 나갈 길이 없으면 그게 덮는 것이다', () => {
    const markup = bridge({ startedAt: '2026-09-11T01:00:00.000Z', endedAt: '2026-09-11T01:07:00.000Z' });

    expect(markup).toContain('기록 없이 둘게요');
  });

  it('로그인의 이유(알림·PC 연동)는 로그인을 청하는 자리에서 말한다', () => {
    const markup = bridge({ startedAt: '2026-09-11T01:00:00.000Z', endedAt: '2026-09-11T01:07:00.000Z' });

    expect(markup).toContain('토스 알림');
    expect(markup).toContain('booktimer.app');
  });
});

/**
 * 며칠 뒤 재진입 — 화면은 상한으로 접어 「끝남」을 그리는데 storage에 `endedAt:null`이 남으면,
 * 로그인 뒤 `flushTrial`이 「올릴 것 없음」으로 지나친다. 「기록을 남기려면 계정이 필요해요」라고
 * 청해 놓고 아무것도 안 남는 자리라, 접은 값이 storage에 박히는지를 합류 결과로 잰다.
 */
describe('상한을 넘겨 돌아온 체험', () => {
  it('접은 값을 storage에도 박는다 — 로그인 뒤 그대로 합류한다', async () => {
    stubLocalStorage();
    writeTrial({ startedAt: new Date(Date.now() - 7 * 3600_000).toISOString(), endedAt: null });

    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <LoginBridge onAuthenticated={() => {}} onNewAccount={() => {}} onLinkAccount={() => {}} />
      </TDSMobileProvider>,
    );
    const sent: Trial[] = [];
    const result = await flushTrial(async (t) => void sent.push(t));

    expect(markup).toContain('기록을 남기려면');
    expect(result).toBe('imported');
    expect(sent).toHaveLength(1);
    expect(trialDurationSeconds(sent[0])).toBe(TRIAL_CAP_SECONDS);
  });
});

describe('로그인 시작 (beginLogin)', () => {
  it('등록된 신원이면 홈으로 보낼 신호를 준다', async () => {
    loginMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel' });

    await expect(beginLogin('intro')).resolves.toBe('authenticated');
  });

  it('미등록이면 새로 시작 / 계정 연결 선택으로 보낸다', async () => {
    // 서버는 미등록일 때 토큰을 주지 않는다 — 그 응답 모양 그대로.
    loginMock.mockResolvedValue({ registered: false, token: null, loginId: null });

    await expect(beginLogin('trial')).resolves.toBe('choice');
  });

  it('실패는 그대로 올려보낸다 — 화면이 실패 문구를 그려야 한다', async () => {
    loginMock.mockRejectedValue(new Error('인가 취소'));

    await expect(beginLogin('intro')).rejects.toThrow('인가 취소');
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

    await beginLogin('intro');

    expect(trackEventMock).toHaveBeenCalledWith('login_started', { source: 'intro' });
  });

  /**
   * 체험을 마치고 누른 것과 그냥 누른 것은 <b>다른 사람</b>이다 — 가치를 본 뒤에도 동의를 거절하면
   * 그때가 「개인정보 경각심」 가설을 다시 볼 시점이고, 아예 안 눌렀으면 화면 문제다.
   */
  it('어느 화면에서 눌렀는지 함께 남긴다 — 두 층의 처방이 다르다', async () => {
    loginMock.mockResolvedValue({ registered: true, token: 'tok', loginId: 'goospel' });

    await beginLogin('trial');

    expect(trackEventMock).toHaveBeenCalledWith('login_started', { source: 'trial' });
  });

  it('인가가 실패해도 눌렀다는 사실은 남는다 — 「눌렀는데 안 온 사람」을 가르는 유일한 점이다', async () => {
    loginMock.mockRejectedValue(new Error('인가 취소'));

    await expect(beginLogin('trial')).rejects.toThrow('인가 취소');
    expect(trackEventMock).toHaveBeenCalledWith('login_started', { source: 'trial' });
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
