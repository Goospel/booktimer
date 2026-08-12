import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { DashboardResponse } from './api';
import { TAB_BAR_Z_INDEX } from './App';
import { ApiError, logout, waiveDebt } from './api';
import {
  AccountSection,
  BookSheet,
  Home,
  SHEET_COPY,
  askNotificationAgreement,
  claimDebtWaiver,
  defaultBookId,
  forgiveMinutes,
  logoutAndLeave,
  openSheetMode,
  pickHandler,
  shouldShowNotificationCard,
  showWaiverButton,
  todayProgress,
  waiverErrorMessage,
} from './screens/Home';
import { day, graph, stubLocalStorage, userAgent } from './test-fixtures';
import { LEVEL_COLORS, coverColor } from './ui';
import { REWARD_AD_GROUP_ID, notificationAgreementSupported, requestNotificationAgreement, watchRewardAd } from './toss';

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
  logout: vi.fn(),
  waiveDebt: vi.fn(),
}));
vi.mock('./toss', () => ({
  REWARD_AD_GROUP_ID: 'test-ad-group',
  watchRewardAd: vi.fn(),
  GOAL_MET_TEMPLATE_CODE: 'test-template',
  notificationAgreementSupported: vi.fn(),
  requestNotificationAgreement: vi.fn(),
}));

const logoutMock = vi.mocked(logout);
const waiveDebtMock = vi.mocked(waiveDebt);
const watchRewardAdMock = vi.mocked(watchRewardAd);
const supportedMock = vi.mocked(notificationAgreementSupported);
const requestAgreementMock = vi.mocked(requestNotificationAgreement);

const BUTTON_LABEL = '광고 보고 밀린 하루 지우기';
const NOTIFICATION_LABEL = '알림 받기';
const AGREEMENT_KEY = 'booktimer.notificationAgreement';

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
        onGoLibrary={() => {}}
        onGoGoal={() => {}}
        onLogout={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

beforeEach(() => {
  logoutMock.mockReset();
  waiveDebtMock.mockReset();
  watchRewardAdMock.mockReset();
  supportedMock.mockReset();
  requestAgreementMock.mockReset();
  supportedMock.mockReturnValue(true);
  stubLocalStorage(); // 홈이 렌더 중에 동의 캐시를 읽는다
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

  it('진행률 게이지가 브랜드 세이지로 찬다 — 다른 초록이 섞이면 화면에 색이 둘이 된다', () => {
    expect(renderHome()).toContain('#6E8A6A');
    expect(renderHome()).not.toContain('#2F8F6B');
  });
});

/**
 * 잔디 미리보기 방향 — 서버는 **weeks[0]이 최신 주**가 되도록 열을 뒤집어 보낸다
 * (`ContributionGraphBuilder.build`). oldest-first로 착각해 뒤에서 자르면 미리보기가 1년 전
 * 빈 구간만 보여준다 — 실기기에서 기록이 있는데도 홈 잔디가 늘 빈 칸이었다.
 */
describe('잔디 미리보기 방향', () => {
  const week = (level: number) => Array.from({ length: 7 }, () => day(level));
  // 미리보기 15주보다 길어야 어느 쪽을 잘랐는지가 드러난다 — 최신 주만 초록, 나머지 과거는 전부 0.
  const directional: DashboardResponse['graph'] = {
    ...graph,
    weeks: [week(4), ...Array.from({ length: 15 }, () => week(0))],
  };
  const CELL = 'width:100%;aspect-ratio:1 / 1;border-radius:2px'; // 잔디 칸 서명(ui.test와 같은 값)

  it('최신 주(weeks[0])를 보여준다 — 뒤에서 자르면 가장 옛날 15주가 잡혀 늘 빈 칸이었다', () => {
    expect(renderHome({ graph: directional })).toContain(`${CELL};background:${LEVEL_COLORS[4]}`);
  });

  it('15주까지만 자른다 — 카드 폭이 정해진 자리라 주 수가 늘면 칸이 무한히 작아진다', () => {
    expect(renderHome({ graph: directional }).split(CELL).length - 1).toBe(15 * 7);
  });
});

/**
 * 히어로 파생값 — 웹 대시보드가 UX 리뷰로 뒤집은 **카운트업 프레이밍**을 그대로 옮겼다
 * (`frontend/src/dashboard/timerProgress.ts`의 `computeProgress`). 대형 숫자는 "오늘 남은 시간"이
 * 아니라 **오늘 읽은 시간**이고, 남은 시간은 보조 메타로 강등된다.
 *
 * <p>서버는 스냅샷만 주므로 측정 중 라이브 값은 `remainingSeconds - elapsed`로 만든다 —
 * 그래서 별도 tick 없이 기존 elapsed 인터벌만으로 읽은 시간이 매초 늘어난다.
 */
describe('오늘 읽은 시간 (todayProgress)', () => {
  const timer = { remainingSeconds: 3600, carriedDebtSeconds: 0, todayGoalSeconds: 3600, carryover: false };

  it('아직 0초 읽었으면 0 — 진행바도 0이고 목표까지는 목표 전부다', () => {
    expect(todayProgress(timer, 0)).toEqual({
      todayRead: 0,
      remainingToGoal: 3600,
      overflow: 0,
      progress: 0,
      achieved: false,
    });
  });

  it('1초 남았으면 아직 달성이 아니다 — 경계에서 축하가 먼저 뜨면 거짓말이 된다', () => {
    const result = todayProgress({ ...timer, remainingSeconds: 1 }, 0);

    expect(result.todayRead).toBe(3599);
    expect(result.remainingToGoal).toBe(1);
    expect(result.achieved).toBe(false);
  });

  it('딱 0이 되는 순간 달성 — 초과분은 아직 0이다', () => {
    expect(todayProgress({ ...timer, remainingSeconds: 0 }, 0)).toEqual({
      todayRead: 3600,
      remainingToGoal: 0,
      overflow: 0,
      progress: 1,
      achieved: true,
    });
  });

  it('목표를 넘겨도 계속 센다 — 초과분이 따로 잡히고 진행바는 1에서 멈춘다', () => {
    expect(todayProgress({ ...timer, remainingSeconds: -600 }, 0)).toEqual({
      todayRead: 4200,
      remainingToGoal: 0,
      overflow: 600,
      progress: 1,
      achieved: true,
    });
  });

  it('측정 중이면 경과한 만큼 더 읽은 것으로 센다 — 매초 tick이 그대로 카운트업이 된다', () => {
    const before = todayProgress({ ...timer, remainingSeconds: 900 }, 300);
    const oneSecondLater = todayProgress({ ...timer, remainingSeconds: 900 }, 301);

    expect(before.todayRead).toBe(3000); // 목표 3600 − (남은 900 − 경과 300)
    expect(oneSecondLater.todayRead).toBe(3001);
    expect(before.remainingToGoal).toBe(600);
  });

  it('이월 모드면 밀린 시간은 오늘 몫에서 뺀다 — 어제 빚이 오늘 성취를 갉아먹지 않는다', () => {
    const carried = { remainingSeconds: 5400, carriedDebtSeconds: 1800, todayGoalSeconds: 3600, carryover: true };

    expect(todayProgress(carried, 0).todayRead).toBe(0); // 남은 5400 − 밀린 1800 = 오늘 몫 3600
    expect(todayProgress({ ...carried, remainingSeconds: 4200 }, 0).todayRead).toBe(1200);
  });

  it('읽은 시간은 음수로 내려가지 않는다 — 서버 스냅샷이 어긋나도 "-30분"이 뜨지 않는다', () => {
    expect(todayProgress({ ...timer, remainingSeconds: 7200 }, 0).todayRead).toBe(0);
  });

  it('목표 미설정(0)이면 진행바가 없다 — 나눌 게 없고 달성이라 우길 수도 없다', () => {
    const result = todayProgress({ ...timer, todayGoalSeconds: 0, remainingSeconds: 0 }, 120);

    expect(result.progress).toBeNull();
    expect(result.achieved).toBe(false);
    expect(result.todayRead).toBe(120); // 목표가 없어도 측정한 만큼은 센다
  });
});

/** 히어로 문구 — 프레이밍이 뒤집혔는지는 결국 화면에 뜬 말이 정한다. */
describe('히어로 프레이밍 (렌더)', () => {
  it('대형 숫자를 "오늘 읽은 시간"으로 세운다 — 남은 시간 카운트다운은 사라진다', () => {
    const markup = renderHome({ remainingSeconds: 900, todayGoalSeconds: 3600 });

    expect(markup).toContain('오늘 읽은 시간');
    expect(markup).not.toContain('오늘 남은 시간');
    expect(markup).toContain('45:00'); // 3600 − 900
  });

  it('남은 시간은 목표와 함께 보조 메타로 내려간다', () => {
    const markup = renderHome({ remainingSeconds: 900, todayGoalSeconds: 3600 });

    expect(markup).toContain('오늘 목표 1시간');
    expect(markup).toContain('목표까지 15:00');
  });

  it('달성하면 축하와 초과분을 보여준다 — 목표를 넘겨도 계속 센다', () => {
    // 알림 동의 카드 문구에도 "목표 달성"이 들어 있어 그것만으로는 판별이 안 된다 — 히어로 문구로 좁힌다.
    const markup = renderHome({ remainingSeconds: -600, todayGoalSeconds: 3600 });

    expect(markup).toContain('오늘 목표 달성');
    expect(markup).toContain('+10분');
    expect(markup).not.toContain('목표까지');
  });

  it('밀린 시간이 있으면 7일 자동 용서를 안내한다 — 빚을 위협이 아니라 "괜찮다"로 말한다', () => {
    expect(renderHome({ carriedDebtSeconds: 1800 })).toContain('밀린 30분은 최근 7일이 지나면 자동으로 사라져요');
  });

  it('1분 미만 부채도 "1분"으로 말한다 — "45초은"처럼 조사가 깨지지 않게 분으로 고정한다', () => {
    expect(forgiveMinutes(45)).toBe(1);
    expect(forgiveMinutes(1800)).toBe(30);
    expect(renderHome({ carriedDebtSeconds: 45 })).toContain('밀린 1분은');
  });

  it('밀린 시간이 없으면 용서 문구도 없다 — 없는 빚을 상기시키지 않는다', () => {
    expect(renderHome({ carriedDebtSeconds: 0 })).not.toContain('자동으로 사라져요');
  });
});

describe('책 0권 빈 상태', () => {
  it('책이 없으면 안내와 서재 진입 버튼을 그린다 — 칩 자리가 통째로 비어 막다른 길이었다', () => {
    const markup = renderHome({ readingBooks: [] });

    expect(markup).toContain('아직 책이 없어요');
    expect(labelsOf(markup)).toContain('첫 책 추가하기');
  });

  it('책이 있으면 빈 상태를 그리지 않는다', () => {
    const markup = renderHome({ readingBooks: [{ id: 1, title: '데미안' }] });

    expect(markup).not.toContain('아직 책이 없어요');
    expect(labelsOf(markup)).not.toContain('첫 책 추가하기');
  });

  it('측정 중이면 빈 상태도 사라진다 — 이미 시작한 사람에게 책 추가를 조르지 않는다', () => {
    const markup = renderHome({
      readingBooks: [],
      hasActiveSession: true,
      activeStartedAt: '2026-08-11T09:00:00',
    });

    expect(markup).not.toContain('아직 책이 없어요');
  });
});

/**
 * 알림 동의 카드 — 노출 조건은 순수 술어로, 배선은 마크업으로 계측한다(광고 버튼과 같은 방식).
 *
 * <p>동의 상태의 정본은 토스이고 우리는 캐시만 본다: 캐시가 있으면(동의든 거절이든) 다시 조르지 않고,
 * 구 토스앱(5.255.0 미만)에는 누를 수 없는 버튼을 띄우지 않는다.
 */
describe('알림 동의 카드 노출 조건 (shouldShowNotificationCard)', () => {
  it('캐시 없음 + 지원되는 토스앱 → 노출', () => {
    expect(shouldShowNotificationCard(null, true)).toBe(true);
  });

  it('이미 동의했으면 미노출', () => {
    expect(shouldShowNotificationCard('alreadyAgreed', true)).toBe(false);
    expect(shouldShowNotificationCard('newAgreement', true)).toBe(false);
  });

  it('거절했으면 미노출 — 거절한 사용자를 다시 조르지 않는다', () => {
    expect(shouldShowNotificationCard('agreementRejected', true)).toBe(false);
  });

  it('미지원 토스앱이면 미노출 — 눌러도 아무 일이 없는 버튼을 띄우지 않는다', () => {
    expect(shouldShowNotificationCard(null, false)).toBe(false);
  });
});

describe('알림 동의 카드 렌더 배선', () => {
  it('조건이 맞으면 카드와 버튼이 그려진다', () => {
    const markup = renderHome();

    expect(markup).toContain('토스 알림');
    expect(labelsOf(markup)).toContain(NOTIFICATION_LABEL);
  });

  it('캐시가 있으면 카드가 없다 — 한 번 답한 사용자에게 다시 뜨지 않는다', () => {
    localStorage.setItem(AGREEMENT_KEY, 'agreementRejected');

    expect(labelsOf(renderHome())).not.toContain(NOTIFICATION_LABEL);
  });

  it('미지원 토스앱이면 카드가 없다', () => {
    supportedMock.mockReturnValue(false);

    expect(labelsOf(renderHome())).not.toContain(NOTIFICATION_LABEL);
  });
});

describe('동의 요청 흐름 (askNotificationAgreement)', () => {
  it('결과를 캐시에 그대로 적어 두고 돌려준다 — 이 값이 카드를 끈다', async () => {
    requestAgreementMock.mockResolvedValue('newAgreement');

    await expect(askNotificationAgreement()).resolves.toBe('newAgreement');
    expect(localStorage.getItem(AGREEMENT_KEY)).toBe('newAgreement');
  });

  it('거절도 캐시한다 — 거절한 사용자를 다시 조르지 않는다', async () => {
    requestAgreementMock.mockResolvedValue('agreementRejected');

    await askNotificationAgreement();

    expect(localStorage.getItem(AGREEMENT_KEY)).toBe('agreementRejected');
  });

  it('미지원(null)이면 캐시를 건드리지 않는다 — 지원 기기에선 다시 물어야 한다', async () => {
    requestAgreementMock.mockResolvedValue(null);

    await expect(askNotificationAgreement()).resolves.toBeNull();
    expect(localStorage.getItem(AGREEMENT_KEY)).toBeNull();
  });

  it('설정된 템플릿 코드를 그대로 넘긴다 — 코드가 어긋나면 다른 동의문이 뜬다', async () => {
    requestAgreementMock.mockResolvedValue(null);

    await askNotificationAgreement();

    expect(requestAgreementMock).toHaveBeenCalledWith('test-template');
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

/**
 * 책 고르기 — 웹 `BookPickSheet`의 의미론을 옮겼다. 홈엔 **고른 책 칩 하나**만 두고(목록 상시 노출 아님),
 * 「바꾸기」로 **바텀시트**를 연다. 같은 시트가 종료 후 태깅(`tag`)도 겸한다. 시작은 아래 주 버튼이 맡는다.
 *
 * <p>계측은 네 겹이다: ① 기본값은 순수 함수 {@link defaultBookId} ② 열림 가드는 {@link openSheetMode}
 * ③ 고른 책의 행선지는 {@link pickHandler} ④ **접힌 상태**는 홈 마크업, **열린 시트**는 하니스로
 * 「바꾸기」를 누를 수 없으니 {@link BookSheet}를 직접 렌더해서.
 *
 * <p>⚠️ 남는 사각지대(실측): `onClick`은 마크업에 안 남는다 — 「바꾸기」가 시트를 여는지, 고르면
 * 닫히는지, 주 버튼이 정말 고른 책으로 시작하는지는 여기서 못 잡는다(핸들러를 통째로 바꿔도 통과).
 * jsdom 미도입은 이 저장소의 기존 결정이라, 이 배선들은 실기기·프리뷰 확인을 게이트로 둔다.
 */

/** TDS Button이 인라인으로 박는 채움색 — variant를 가릴 class·속성이 없어 이 값이 유일한 표지다. */
const FILL_PRIMARY = '#3182f6';
const FILL_WEAK = 'rgba(100, 168, 255, 0.15)';

function tdsButtons(markup: string): { label: string; fill: string }[] {
  return markup
    .split('<button')
    .slice(1)
    .flatMap((chunk) => {
      const fill = chunk.match(/--button-background-color:([^;"]*)/)?.[1];
      const label = chunk.match(/tds-mobile-button__content[^>]*>([^<]*)</)?.[1];
      return fill === undefined || label === undefined ? [] : [{ label, fill }];
    });
}

const labelsOf = (markup: string) => tdsButtons(markup).map((b) => b.label);
const fillOf = (markup: string, label: string) => tdsButtons(markup).find((b) => b.label === label)?.fill;

describe('측정할 책 기본값 (defaultBookId)', () => {
  const books = [
    { id: 1, title: '데미안' },
    { id: 2, title: '노인과 바다' },
  ];

  it('최근 읽은 책이 목록에 있으면 그 책 — 이어 읽기가 기본값이다', () => {
    expect(defaultBookId(books, 2)).toBe(2);
  });

  it('최근 읽은 책이 없으면 첫 책', () => {
    expect(defaultBookId(books, null)).toBe(1);
  });

  it('최근 읽은 책이 읽는 중 목록에 없으면 첫 책 — 다 읽은 책이 recentBookId로 남는다', () => {
    expect(defaultBookId(books, 99)).toBe(1);
  });

  it('읽는 중인 책이 0권이면 null — 고를 게 없다', () => {
    expect(defaultBookId([], 7)).toBeNull();
  });
});

describe('책 칩 (접힌 상태)', () => {
  const books = [
    { id: 1, title: '데미안' },
    { id: 2, title: '노인과 바다' },
  ];

  it('고른 책 하나만 칩으로 띄우고 목록은 접어 둔다 — 상시 노출하면 책이 늘수록 홈이 목록 화면이 된다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('이 책으로 측정할까요?');
    expect(markup).toContain('노인과 바다');
    expect(markup).not.toContain('데미안'); // 접혀 있으니 다른 책은 자취가 없어야 한다
    expect(labelsOf(markup)).toContain('바꾸기');
  });

  it('시트는 닫힌 채로 그려진다 — 인라인으로 펴 두면 홈이 목록 화면이 된다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).not.toContain('어떤 책을 읽을까요?');
    expect(markup).not.toContain('무슨 책을 읽으셨나요?');
    expect(labelsOf(markup)).not.toContain('건너뛰기');
  });

  it('최근 읽은 책이 없으면 첫 책이 칩에 뜬다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: null });

    expect(markup).toContain('데미안');
    expect(markup).not.toContain('노인과 바다');
  });

  it('칩은 제목 첫 글자를 자리 표지로 세운다 — BookOption엔 표지 주소가 없다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain(`background:${coverColor('노인과 바다')}`);
    expect(markup).toContain('>노</div>');
  });

  it('책이 0권이면 라벨·칩·바꾸기가 통째로 없다', () => {
    const markup = renderHome({ readingBooks: [] });

    expect(markup).not.toContain('이 책으로 측정할까요?');
    expect(labelsOf(markup)).not.toContain('바꾸기');
  });

  it('측정 중이면 고르는 자리가 사라진다 — 이미 시작한 뒤엔 바꿀 게 없다', () => {
    const markup = renderHome({
      readingBooks: books,
      hasActiveSession: true,
      activeStartedAt: '2026-08-11T09:00:00',
    });

    expect(markup).not.toContain('이 책으로 측정할까요?');
    expect(labelsOf(markup)).not.toContain('바꾸기');
  });
});

describe('시트 열림 가드 (openSheetMode)', () => {
  it('책이 있으면 요청한 모드로 연다', () => {
    expect(openSheetMode('start', [{ id: 1, title: '데미안' }])).toBe('start');
    expect(openSheetMode('tag', [{ id: 1, title: '데미안' }])).toBe('tag');
  });

  it('책이 0권이면 열지 않는다 — 빈 시트는 막다른 길이고 홈의 「첫 책 추가하기」가 그 자리를 맡는다', () => {
    expect(openSheetMode('start', [])).toBeNull();
    expect(openSheetMode('tag', [])).toBeNull();
  });
});

describe('고른 책의 행선지 (pickHandler)', () => {
  const select = vi.fn();
  const tag = vi.fn();
  const book = { id: 1, title: '데미안' };

  beforeEach(() => {
    select.mockReset();
    tag.mockReset();
  });

  it('start면 칩을 갈아끼운다 — 측정 전이라 붙일 세션이 없다', () => {
    pickHandler('start', { select, tag })(book);

    expect(select).toHaveBeenCalledWith(book);
    expect(tag).not.toHaveBeenCalled();
  });

  it('tag면 방금 세션에 붙인다 — 여기서 칩을 갈아끼우면 기록이 영영 안 붙는다', () => {
    pickHandler('tag', { select, tag })(book);

    expect(tag).toHaveBeenCalledWith(book);
    expect(select).not.toHaveBeenCalled();
  });
});

/** 「바꾸기」·측정 종료로 열리는 시트 — 정적 렌더로는 열린 상태에 못 가므로 직접 렌더해 계측한다. */
describe('책 고르기 시트 (BookSheet)', () => {
  const books = [
    { id: 1, title: '데미안' },
    { id: 2, title: '노인과 바다' },
  ];

  const renderSheet = (mode: 'start' | 'tag', selectedId: number | null = null) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSheet
          mode={mode}
          books={books}
          selectedId={selectedId}
          disabled={false}
          onPick={() => {}}
          onSkip={() => {}}
          onClose={() => {}}
        />
      </TDSMobileProvider>,
    );

  /** 시트의 책 행 — TDS Button이 아니라 표지+제목 한 줄이라 제목만 뽑는다. */
  const rowTitlesOf = (markup: string) => [...markup.matchAll(/data-book-title="([^"]*)"/g)].map((m) => m[1]);

  it('start면 측정 전 문구와 "책 없이 시작" CTA — 시작 자리의 시트다', () => {
    const markup = renderSheet('start');

    expect(markup).toContain(SHEET_COPY.start.title);
    expect(markup).toContain('어떤 책을 읽을까요?');
    expect(labelsOf(markup)).toContain('책 없이 시작');
    expect(markup).not.toContain('무슨 책을 읽으셨나요?');
  });

  it('tag면 종료 후 문구와 "건너뛰기" CTA — 같은 시트가 두 자리를 겸한다', () => {
    const markup = renderSheet('tag');

    expect(markup).toContain('무슨 책을 읽으셨나요?');
    expect(labelsOf(markup)).toContain('건너뛰기');
    expect(labelsOf(markup)).not.toContain('책 없이 시작');
  });

  it('책을 전부 한 행씩 나열하고 표지 자리를 세운다 — 버튼 나열엔 없던 시각 위계다', () => {
    const markup = renderSheet('start');

    expect(rowTitlesOf(markup)).toEqual(['데미안', '노인과 바다']);
    expect(markup).toContain(`background:${coverColor('데미안')}`);
    expect(markup).toContain('>데</div>');
  });

  it('고른 책만 표시가 남는다 — 태깅(선택 없음)엔 아무 행도 강조되지 않는다', () => {
    expect(renderSheet('start', 2)).toContain('aria-current="true"');
    expect(renderSheet('tag', null)).not.toContain('aria-current="true"');
  });

  it('딤·패널이 탭바(zIndex 100) 위에 뜬다 — 시트 아래로 탭바가 비치면 시트가 아니다', () => {
    const layers = [...renderSheet('start').matchAll(/z-index:(\d+)/g)].map((m) => Number(m[1]));

    expect(layers.length).toBe(2); // 딤 + 패널
    expect(Math.min(...layers)).toBeGreaterThan(TAB_BAR_Z_INDEX);
    expect(layers[1]).toBeGreaterThan(layers[0]); // 패널이 자기 딤에 가리지 않는다
  });

  it('홈 인디케이터를 피해 하단 여백을 둔다', () => {
    expect(renderSheet('start')).toContain('env(safe-area-inset-bottom)');
  });
});

describe('시작 갈래', () => {
  const books = [
    { id: 1, title: '데미안' },
    { id: 2, title: '노인과 바다' },
  ];

  it('책이 있으면 "측정 시작"(주) + "책 없이 시작"(보조) 두 갈래로 나뉜다', () => {
    const markup = renderHome({ readingBooks: books });

    expect(fillOf(markup, '측정 시작')).toBe(FILL_PRIMARY);
    expect(fillOf(markup, '책 없이 시작')).toBe(FILL_WEAK);
  });

  it('책이 0권이면 "측정 시작" 하나뿐 — 고를 책이 없는데 "책 없이"를 되묻는 건 군더더기다', () => {
    const found = labelsOf(renderHome({ readingBooks: [] }));

    expect(found).toContain('측정 시작');
    expect(found).not.toContain('책 없이 시작');
  });

  it('측정 중이면 끝내기만 남는다 — 시작 갈래는 사라진다', () => {
    const found = labelsOf(
      renderHome({ readingBooks: books, hasActiveSession: true, activeStartedAt: '2026-08-11T09:00:00' }),
    );

    expect(found).toContain('측정 끝내기');
    expect(found).not.toContain('측정 시작');
    expect(found).not.toContain('책 없이 시작');
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

/**
 * 계정 진입점 — 미니앱엔 설정 화면이 없어 로그아웃도 계정 관리도 길이 없었다(api의 `logout()`은
 * 구현돼 있는데 부르는 UI가 없었다). 상세 설정은 웹이 본진이라 여기서는 안내 한 줄 + 로그아웃만 둔다.
 */
describe('계정 진입점', () => {
  const account = (confirm: boolean) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <AccountSection confirm={confirm} onConfirm={() => {}} onLogout={() => {}} />
      </TDSMobileProvider>,
    );

  it('계정 관리는 웹이 본진임을 한 줄로 알린다 — 미니앱만 쓰는 사람에게는 길이 아예 안 보였다', () => {
    expect(account(false)).toContain('booktimer.app');
  });

  it('처음엔 「로그아웃」만 — 실수 한 탭에 세션이 날아가지 않게', () => {
    expect(account(false)).toContain('로그아웃');
    expect(account(false)).not.toContain('정말 로그아웃');
  });

  it('한 번 더 물어본 뒤 실행한다 — 물러설 길(취소)도 함께 준다', () => {
    expect(account(true)).toContain('정말 로그아웃');
    expect(account(true)).toContain('취소');
  });

  it('홈 맨 아래에 둔다 — 「목표 바꾸기」보다 뒤여야 자주 쓰는 것이 위로 온다', () => {
    const markup = renderHome();

    expect(markup.indexOf('목표 바꾸기')).toBeGreaterThan(0);
    expect(markup.indexOf('목표 바꾸기')).toBeLessThan(markup.indexOf('로그아웃'));
  });
});

/**
 * 로그아웃 흐름 — 서버 폐기가 실패해도 **반드시** 로그인 화면으로 넘긴다. 안 넘기면 토큰은 이미
 * 버려졌는데(api.logout의 finally) 화면은 홈에 남아, 무엇을 눌러도 401만 나는 막다른 길이 된다.
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
