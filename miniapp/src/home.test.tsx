import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { DashboardResponse } from './api';
import { ApiError, waiveDebt } from './api';
import { Home, claimDebtWaiver, showWaiverButton, waiverErrorMessage } from './screens/Home';
import { graph, userAgent } from './test-fixtures';
import { REWARD_AD_GROUP_ID, watchRewardAd } from './toss';

/**
 * 리워드 광고 진입점 — 노출 조건과 지급 흐름.
 *
 * <p>하니스가 `renderToStaticMarkup`이라 클릭은 못 잡으므로(jsdom 미도입) 노출 조건은
 * 순수 술어 {@link showWaiverButton}로, 클릭 흐름은 {@link claimDebtWaiver}로 꺼내 계측한다
 * (PR-7의 `nextStoryIndex`·`viewTargetId`와 같은 방식). 렌더 테스트는 그 술어가 실제로
 * 마크업에 연결됐는지만 확인한다.
 */

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api')>()),
  waiveDebt: vi.fn(),
}));
vi.mock('./toss', () => ({ REWARD_AD_GROUP_ID: 'test-ad-group', watchRewardAd: vi.fn() }));

const waiveDebtMock = vi.mocked(waiveDebt);
const watchRewardAdMock = vi.mocked(watchRewardAd);

const BUTTON_LABEL = '광고 보고 밀린 하루 지우기';

function dashboard(overrides: Partial<DashboardResponse> = {}): DashboardResponse {
  return {
    nickname: '구스펠',
    loginId: 'goospel',
    profileCharacterCode: null,
    remainingSeconds: 900,
    carriedDebtSeconds: 1800,
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
    debtWaiverAvailable: true,
    graph,
    quotes: [],
    emailVerified: true,
    ...overrides,
  };
}

function renderHome(overrides: Partial<DashboardResponse> = {}) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard(overrides)}
        onTimerChange={() => {}}
        onGraphChange={() => {}}
        onGoHistory={() => {}}
        onGoGoal={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

beforeEach(() => {
  waiveDebtMock.mockReset();
  watchRewardAdMock.mockReset();
});

describe('버튼 노출 조건 (showWaiverButton)', () => {
  it('부채 있음 + 서버 가용 + adGroupId 설정됨 → 노출', () => {
    expect(showWaiverButton(1800, true, 'ad-1')).toBe(true);
  });

  it('부채가 0이면 미노출 — 부채가 없으면 광고의 존재 자체가 안 보인다', () => {
    expect(showWaiverButton(0, true, 'ad-1')).toBe(false);
  });

  it('서버가 오늘 이미 썼다고 하면 미노출', () => {
    expect(showWaiverButton(1800, false, 'ad-1')).toBe(false);
  });

  it('adGroupId 미설정이면 미노출 — config-gate(광고 그룹 등록 전 빌드 안전)', () => {
    expect(showWaiverButton(1800, true, '')).toBe(false);
  });
});

describe('홈 렌더 배선', () => {
  it('조건이 맞으면 버튼이 그려진다', () => {
    expect(renderHome()).toContain(BUTTON_LABEL);
  });

  it('서버가 가용하지 않다고 하면 버튼이 없다', () => {
    expect(renderHome({ debtWaiverAvailable: false })).not.toContain(BUTTON_LABEL);
  });

  it('부채가 0이면 버튼이 없다 — 밀린 시간 문구 자체가 없는 자리다', () => {
    expect(renderHome({ carriedDebtSeconds: 0 })).not.toContain(BUTTON_LABEL);
  });

  it('버튼 문구에 "광고"를 명시한다 — 광고 위장 금지 조항', () => {
    expect(renderHome()).toContain('광고');
  });

  it('진행률 게이지가 웹 달성색(--ok)으로 찬다 — 세이지와 변별되는 초록', () => {
    expect(renderHome()).toContain('#2F8F6B');
    expect(renderHome()).not.toContain('#4caf50');
  });
});

describe('지급 흐름 (claimDebtWaiver)', () => {
  const waiveResult = {
    waivedDate: '2026-08-09',
    waivedSeconds: 1800,
    timer: dashboard({ carriedDebtSeconds: 0, debtWaiverAvailable: false }),
  };

  it('시청 완료면 지급 API를 부르고 결과를 돌려준다', async () => {
    watchRewardAdMock.mockResolvedValue(true);
    waiveDebtMock.mockResolvedValue(waiveResult);

    await expect(claimDebtWaiver('ad-1')).resolves.toEqual(waiveResult);
    expect(waiveDebtMock).toHaveBeenCalledTimes(1);
  });

  it('중간 이탈이면 지급 API를 부르지 않고 null — 조용히 원상태', async () => {
    watchRewardAdMock.mockResolvedValue(false);

    await expect(claimDebtWaiver('ad-1')).resolves.toBeNull();
    expect(waiveDebtMock).not.toHaveBeenCalled();
  });

  it('광고 로드 실패면 지급 API를 부르지 않고 에러가 올라간다', async () => {
    watchRewardAdMock.mockRejectedValue(new Error('no fill'));

    await expect(claimDebtWaiver('ad-1')).rejects.toThrow('no fill');
    expect(waiveDebtMock).not.toHaveBeenCalled();
  });

  it('설정된 adGroupId를 그대로 광고에 넘긴다', async () => {
    watchRewardAdMock.mockResolvedValue(false);

    await claimDebtWaiver(REWARD_AD_GROUP_ID);

    expect(watchRewardAdMock).toHaveBeenCalledWith('test-ad-group');
  });
});

describe('실패 문구 (waiverErrorMessage)', () => {
  it('서버 평문 메시지는 그대로 쓴다 — 409 안내가 곧 문구다', () => {
    expect(waiverErrorMessage(new ApiError(409, '오늘은 이미 사용했어요. 내일 다시 지울 수 있어요.'))).toBe(
      '오늘은 이미 사용했어요. 내일 다시 지울 수 있어요.',
    );
  });

  it('SDK 광고 에러는 영문·기술 문구라 안내로 바꾼다', () => {
    expect(waiverErrorMessage(new Error('no fill'))).not.toContain('no fill');
    expect(waiverErrorMessage(new Error('no fill'))).toContain('광고를 불러오지 못했어요');
  });
});
