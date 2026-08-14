import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { BookOption, DashboardResponse } from './api';
import { TAB_BAR_Z_INDEX } from './App';
import { ApiError, waiveDebt } from './api';
import {
  BookCarousel,
  BookSheet,
  COVER_GAP,
  COVER_HEIGHT,
  COVER_WIDTH,
  EDGE_SPACE,
  FirstSessionBanner,
  Home,
  RemainingNote,
  TRACK_V_PAD,
  askNotificationAgreement,
  carouselIndexOf,
  centeredIndex,
  claimDebtWaiver,
  defaultBookId,
  goalHandleLabel,
  noBookSubtitle,
  recenterIndex,
  selectionAt,
  shouldShowNotificationCard,
  showWaiverButton,
  todayProgress,
  waiverErrorMessage,
} from './screens/Home';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';
import { coverColor } from './ui';
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
  waiveDebt: vi.fn(),
}));
vi.mock('./toss', () => ({
  REWARD_AD_GROUP_ID: 'test-ad-group',
  watchRewardAd: vi.fn(),
  GOAL_MET_TEMPLATE_CODE: 'test-template',
  notificationAgreementSupported: vi.fn(),
  requestNotificationAgreement: vi.fn(),
  trackEvent: vi.fn(),
  openExternal: vi.fn(), // 피드 박스가 뉴스 줄에서 쓴다(홈이 프롭으로 내려 준다)
}));

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
    emailVerified: true,
    ...overrides,
  };
}

/** 읽는 중 책 한 권 — 표지·저자는 안 준 곳에서 `null`(=자리 표지 경로)로 떨어진다. */
function book(id: number, title: string, extra: Partial<BookOption> = {}): BookOption {
  return { id, title, coverUrl: null, author: null, ...extra };
}

function renderHome(
  overrides: Partial<DashboardResponse> = {},
  props: { goalAdPending?: boolean } = {},
) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard(overrides)}
        onTimerChange={() => {}}
        onGraphChange={() => {}}
        onGoGoal={() => {}}
        goalAdPending={props.goalAdPending ?? false}
        onGoSettings={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

beforeEach(() => {
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
  it('첫 렌더에는 버튼이 없다 — 상자가 접힌 채로 시작하므로 「남은시간」을 눌러야 나온다', () => {
    expect(renderHome()).not.toContain(BUTTON_LABEL);
  });

  it('진행률 게이지가 브랜드 세이지로 찬다 — 다른 초록이 섞이면 화면에 색이 둘이 된다', () => {
    expect(renderHome()).toContain('#6E8A6A');
    expect(renderHome()).not.toContain('#2F8F6B');
  });
});

/**
 * 홈에서 기록(잔디)은 통째로 빠진다. 잔디 전체·연속일·읽은 날·총 시간을 기록 탭이 이미 다 보여 주고,
 * 그 탭은 하단 탭바에서 한 번에 닿는다 — 홈에 진입 손잡이를 또 두는 건 자리만 먹는 중복이었다
 * (사용자 결정 2026-08-14). 그 자리는 피드 박스가 통째로 쓴다.
 */
describe('홈에서 빠진 기록 흔적', () => {
  const CELL = 'width:100%;aspect-ratio:1 / 1;border-radius:2px'; // 잔디 칸 서명(ui.test와 같은 값)

  it('잔디 칸이 없다 — 기록 탭이 이미 전체를 그린다', () => {
    expect(renderHome()).not.toContain(CELL);
  });

  it('연속일 줄도 없다 — 기록 진입은 하단 탭바가 맡는다', () => {
    const markup = renderHome();

    expect(markup).not.toContain('data-streak-line');
    expect(markup).not.toContain('연속');
    expect(markup).not.toContain('기록 보기');
  });

  it('측정 시작 버튼 다음이 곧 피드 박스다 — 사이에 낀 카드가 없다', () => {
    const markup = renderHome();

    expect(markup.indexOf('측정 시작')).toBeLessThan(markup.indexOf('data-feed-tab'));
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
      remaining: 3600,
      overflow: 0,
      progress: 0,
      achieved: false,
    });
  });

  it('1초 남았으면 아직 달성이 아니다 — 경계에서 축하가 먼저 뜨면 거짓말이 된다', () => {
    const result = todayProgress({ ...timer, remainingSeconds: 1 }, 0);

    expect(result.todayRead).toBe(3599);
    expect(result.remaining).toBe(1);
    expect(result.achieved).toBe(false);
  });

  it('딱 0이 되는 순간 달성 — 초과분은 아직 0이다', () => {
    expect(todayProgress({ ...timer, remainingSeconds: 0 }, 0)).toEqual({
      todayRead: 3600,
      remaining: 0,
      overflow: 0,
      progress: 1,
      achieved: true,
    });
  });

  it('목표를 넘겨도 계속 센다 — 초과분이 따로 잡히고 진행바는 1에서 멈춘다', () => {
    expect(todayProgress({ ...timer, remainingSeconds: -600 }, 0)).toEqual({
      todayRead: 4200,
      remaining: 0,
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
    expect(before.remaining).toBe(600);
  });

  it('이월 모드면 밀린 시간은 오늘 몫에서 뺀다 — 어제 빚이 오늘 성취를 갉아먹지 않는다', () => {
    const carried = { remainingSeconds: 5400, carriedDebtSeconds: 1800, todayGoalSeconds: 3600, carryover: true };

    expect(todayProgress(carried, 0).todayRead).toBe(0); // 남은 5400 − 밀린 1800 = 오늘 몫 3600
    expect(todayProgress({ ...carried, remainingSeconds: 4200 }, 0).todayRead).toBe(1200);
  });

  it('이월 모드면 게이지 최대치가 목표 + 밀린 시간이다 — 오늘 실제로 채워야 할 양이 그거다', () => {
    // 목표 30분 + 밀린 10분 = 40분 중 25분 읽음.
    const carried = { remainingSeconds: 900, carriedDebtSeconds: 600, todayGoalSeconds: 1800, carryover: true };
    const result = todayProgress(carried, 0);

    expect(result.todayRead).toBe(1500);
    expect(result.remaining).toBe(900); // 남은시간 = 목표까지 5분 + 밀린 10분
    expect(result.progress).toBe(1500 / 2400);
  });

  it('이월이 꺼져 있으면 밀린 시간은 최대치에 안 들어간다 — 오늘 갚을 몫이 아니다', () => {
    const result = todayProgress({ ...timer, carriedDebtSeconds: 1800, remainingSeconds: 900 }, 0);

    expect(result.remaining).toBe(900);
    expect(result.progress).toBe(2700 / 3600); // 분모는 목표 3600 그대로
  });

  it('목표를 채웠어도 밀린 시간이 남으면 게이지는 안 찬다 — 달성 축하는 그대로 뜬다(결정 a)', () => {
    // 목표 30분은 다 채웠고(오늘 몫 0) 밀린 10분만 남은 상태.
    const result = todayProgress(
      { remainingSeconds: 600, carriedDebtSeconds: 600, todayGoalSeconds: 1800, carryover: true },
      0,
    );

    expect(result.achieved).toBe(true); // 오늘 목표는 달성 — 빚 때문에 축하를 미루지 않는다
    expect(result.remaining).toBe(600);
    expect(result.progress).toBe(1800 / 2400);
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
  it('제목 행이 없다 — 「구스펠님의 오늘」은 바로 아래 카드가 이미 하는 말이었다', () => {
    expect(renderHome()).not.toContain('님의 오늘');
  });

  it('대형 숫자를 "오늘 읽은 시간"으로 세운다 — 남은 시간 카운트다운은 사라진다', () => {
    const markup = renderHome({ remainingSeconds: 900, todayGoalSeconds: 3600 });

    expect(markup).toContain('오늘 읽은 시간');
    expect(markup).not.toContain('오늘 남은 시간');
    expect(markup).toContain('45:00'); // 3600 − 900
  });

  it('보조 줄은 「남은시간」이다 — 게이지가 목표+밀린을 재니 라벨도 그 총량을 말해야 한다', () => {
    const markup = renderHome({ remainingSeconds: 900, todayGoalSeconds: 3600 });

    expect(markup).toContain('남은시간 : 15:00');
    expect(markup).not.toContain('목표까지'); // 목표만 재던 옛 라벨
    expect(markup).not.toContain('오늘 목표 1시간'); // 목표 값은 우상단 손잡이 몫
  });

  it('달성하면 축하와 초과분을 보여준다 — 목표를 넘겨도 계속 센다', () => {
    // 알림 동의 카드 문구에도 "목표 달성"이 들어 있어 그것만으로는 판별이 안 된다 — 히어로 문구로 좁힌다.
    const markup = renderHome({ remainingSeconds: -600, todayGoalSeconds: 3600 });

    expect(markup).toContain('오늘 목표 달성');
    expect(markup).toContain('+10분 더 읽었어요');
    expect(markup).not.toContain('남은시간'); // 남은 게 없으면 그 줄도 없다
  });

  it('정확히 달성하면 보조 줄을 생략한다 — 히어로 한 줄이면 족하다', () => {
    const markup = renderHome({ remainingSeconds: 0, todayGoalSeconds: 3600 });

    expect(markup).toContain('오늘 목표 달성');
    expect(markup).not.toContain('더 읽었어요');
    expect(markup).not.toContain('남은시간');
  });

  it('응원 문구가 사라졌다 — 밀린 시간 안내는 「남은시간」을 눌러야 보이는 상자로 들어갔다', () => {
    const markup = renderHome({ carriedDebtSeconds: 1800, carryover: true });

    expect(markup).not.toContain('뒤처져도 괜찮아요');
    expect(markup).not.toContain('광고를 보고 하루씩'); // 상자는 접힌 채로 시작한다
  });
});

/**
 * 남은시간 설명 상자 — 「남은시간」이 목표와 밀린 시간의 합이라는 사실은 숫자만 봐서는 못 읽는다.
 * 탭하면 그 자리에서 펼쳐지는 상자가 내역을 밝히고, **목표 바꾸기·광고 버튼도 여기로 들어왔다**
 * (히어로 카드에 얹혀 있던 손잡이들 — 카드는 숫자와 게이지만 남는다, 사용자 결정 2026-08-14).
 *
 * <p>손잡이들은 `children`으로 받는다 — 상자는 표시만 하고 배선(광고·busy·전면광고 대기)은 홈이
 * 그대로 들고 있어야 조건 술어(`showWaiverButton`)와 상자가 서로를 모른 채 각자 계측된다.
 *
 * <p>여는 동작은 정적 렌더 하니스로 못 잡으므로(T-149) 내용은 컴포넌트를 직접 그려서 계측한다.
 */
describe('남은시간 설명 상자', () => {
  const note = (goal: number, debt: number, remaining: number, children?: ReactNode) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <RemainingNote goalSeconds={goal} debtSeconds={debt} remainingSeconds={remaining}>
          {children}
        </RemainingNote>
      </TDSMobileProvider>,
    );

  it('내역 세 줄로 합을 밝힌다 — 목표 + 밀린 = 남은시간', () => {
    const markup = note(1800, 600, 900);

    expect(markup).toContain('30분'); // 오늘 목표
    expect(markup).toContain('10분'); // 밀린 시간
    expect(markup).toContain('15:00'); // 남은시간
  });

  it('밀린 게 없으면 그 줄도 안내 문구도 없다 — 「밀린 시간 0분」은 없는 빚을 상기시키는 줄이다', () => {
    const markup = note(1800, 0, 900);

    expect(markup).not.toContain('밀린 시간');
    expect(markup).not.toContain('더 읽거나');
    expect(markup).toContain('15:00'); // 남은시간 합계는 그대로 밝힌다
  });

  it('지우는 수단을 함께 알린다 — 7일 자동 소멸이 폐지돼 "기다리면 사라진다"는 더 이상 사실이 아니다', () => {
    const markup = note(1800, 600, 900);

    expect(markup).toContain('더 읽거나');
    expect(markup).toContain('광고');
    expect(markup).not.toContain('자동으로 사라져요');
  });

  it('손잡이를 내역 아래에 담는다 — 목표 바꾸기·광고가 서는 자리가 이 상자다', () => {
    const markup = note(1800, 600, 900, <button type="button">목표 30분 ›</button>);

    expect(markup).toContain('목표 30분 ›');
    expect(markup.indexOf('남은시간')).toBeLessThan(markup.indexOf('목표 30분'));
  });
});

/**
 * 목표 손잡이 — 타이머 카드 우상단 칩이던 것이 **「남은시간」 확장 상자 안**으로 들어갔다
 * (카드에 손잡이가 얹혀 있는 게 지저분하다는 사용자 결정 2026-08-14). 아이콘 팩이 없어
 * (의존성은 `@toss/tds-mobile` 하나) 손잡이의 뜻은 그림이 아니라 **목표 값 자체**가 말한다 —
 * 「목표 30분 ›」은 무엇을 바꾸는 버튼인지 스스로 밝히므로 아이콘의 모호함이 성립하지 않는다.
 *
 * <p>단 **목표가 0이면 게이지 줄이 통째로 안 그려져** 상자로 갈 길도 없다 — 그 상태에서만
 * 카드 안에 「목표 정하기 ›」가 남는다(신규 사용자가 목표를 정하러 갈 유일한 길).
 *
 * <p>라벨을 순수 함수로 꺼낸 건 늘 같은 이유다 — 정적 렌더 하니스라 클릭을 못 잡는다(T-149).
 */
describe('목표 손잡이 (goalHandleLabel · 렌더)', () => {
  it('라벨이 동작을 말한다 — 값은 바로 위 「오늘 목표」 줄이 이미 말하므로 되풀이하지 않는다', () => {
    expect(goalHandleLabel(1800)).toBe('하루 목표 바꾸기');
    expect(goalHandleLabel(3600)).toBe('하루 목표 바꾸기');
  });

  it('목표가 0이면 「목표 정하기」— 바꿀 값이 없으니 정하러 가는 말이 맞다', () => {
    expect(goalHandleLabel(0)).toBe('목표 정하기');
  });

  // 전면광고 로드에 1~2초가 걸린다(2026-08-14: 광고를 먼저 띄우고 전환하도록 바꾼 뒤 생긴 대기).
  // 그동안 라벨이 그대로면 눌러도 아무 일 없는 것처럼 보여 사용자가 다시 누른다.
  it('광고를 기다리는 동안엔 「준비 중」— 목표값과 무관하게 대기 상태를 말한다', () => {
    expect(goalHandleLabel(1800, true)).toBe('준비 중…');
    expect(goalHandleLabel(0, true)).toBe('준비 중…');
  });

  it('기다리지 않을 때는 평소 라벨 그대로 — 대기 표시가 평상시를 건드리지 않는다', () => {
    expect(goalHandleLabel(1800, false)).toBe('하루 목표 바꾸기');
  });

  // ⚠️ 전체 마크업에 'disabled'가 있나로 재면 안 된다 — TDS 전역 CSS에 `:disabled` 셀렉터가 실려
  // 있어 그 단언은 항상 참이고, 반대 단언은 항상 거짓이다(공허한 테스트). 손잡이 버튼만 겨눈다.
  const goalHandleTag = (markup: string, label: string) => {
    const at = markup.indexOf(label);
    expect(at).toBeGreaterThan(-1); // 라벨을 못 찾으면 아래 단언이 빈 문자열을 검사하게 된다
    return markup.slice(markup.lastIndexOf('<button', at), at);
  };

  // 목표 0일 때만 손잡이가 카드에 남으므로(그 외엔 접힌 상자 안) 비활성 검사도 그 상태에서 한다.
  const noGoal = { todayGoalSeconds: 0, remainingSeconds: 0 };

  it('대기 중이면 손잡이가 비활성이다 — 연타로 광고가 두 장 쌓이지 않게', () => {
    expect(goalHandleTag(renderHome(noGoal, { goalAdPending: true }), '준비 중')).toContain('disabled=""');
  });

  it('평상시 손잡이는 눌린다 — 대기 표시가 평상시를 잠그지 않는다', () => {
    expect(goalHandleTag(renderHome(noGoal), '목표 정하기')).not.toContain('disabled=""');
  });

  it('타이머 카드에서 손잡이가 빠졌다 — 카드는 숫자와 게이지만 남는다', () => {
    const markup = renderHome({ todayGoalSeconds: 1800 });

    // 접힌 상자 안이라 첫 렌더에는 라벨 자체가 없다 — 카드에 남아 있으면 여기서 잡힌다.
    expect(markup).not.toContain('하루 목표 바꾸기');
  });

  // 실기기 제보(2026-08-14): 작은 칩이라 「딱 봐도 버튼」으로 안 읽혔다 — 같은 상자에 선 광고 버튼과
  // 같은 꼴(TDS Button · 풀폭 · weak)로 맞춘다. 인라인 칩 스타일이 되살아나면 여기서 잡힌다.
  it('광고 버튼과 같은 TDS 버튼이다 — 자작 칩이 아니라 버튼으로 읽혀야 한다', () => {
    const found = labelsOf(renderHome(noGoal));

    expect(found).toContain('목표 정하기');
  });

  it('목표 0이면 카드에 손잡이가 남는다 — 게이지 줄이 없어 상자로 갈 길이 없는 유일한 상태다', () => {
    const markup = renderHome(noGoal);

    expect(markup).toContain('목표 정하기');
    expect(markup.indexOf('목표 정하기')).toBeLessThan(markup.indexOf('측정 시작'));
    expect(markup).not.toContain('오늘 목표'); // 게이지 라벨은 목표 0이면 그리지 않는다
  });
});

/**
 * B1 — 측정 중 안심 문구. 이 앱의 핵심 계약은 "화면을 꺼도 서버가 센다"인데, 측정 중 화면엔 그 사실을
 * 알릴 자리가 없었다(운영 실측 2026-08-13: 신규 3명 전원 5분+ 독서 0건, 완료 세션 대부분 1분 미만).
 */
describe('측정 중 안심 문구 (B1)', () => {
  const RELIEF = '화면을 꺼도 측정은 계속돼요';
  const active = { hasActiveSession: true, activeStartedAt: '2026-08-11T09:00:00' };

  it('측정 중이면 화면을 꺼도 된다고 말한다 — 이 앱의 핵심 계약을 사용자가 알 방법이 없었다', () => {
    expect(renderHome(active)).toContain(RELIEF);
  });

  it('측정 중이 아니면 없다 — 시작도 안 한 사람에게 할 말이 아니다', () => {
    expect(renderHome()).not.toContain(RELIEF);
  });
});

/**
 * B2 — 첫 완료 축하 배너. 잔디는 1초만 읽어도 lv1로 점등되는데 홈에서 그걸 볼 자리가 없어졌으므로
 * (잔디 카드 → 피드 박스) 배너가 가리키는 곳도 **기록 탭**으로 옮겨진다. 하이라이트 테두리는 함께
 * 사라졌다 — 가리킬 카드가 없는데 테두리만 남으면 죽은 배선이다.
 *
 * <p>⚠️ 하니스 사각: 축하 상태는 stop 응답으로만 켜지는데 정적 렌더는 「측정 끝내기」를 누를 수 없다 —
 * `Home` 안의 배선(플래그→배너)은 여기서 못 잡고, 조각을 직접 렌더해 계측한다(`BookSheet`와 같은 처지).
 */
describe('첫 완료 축하 (B2)', () => {
  /** 배너 상자의 배경 — TDSMobileProvider가 늘 전역 style을 뿜어 "빈 마크업"으로는 부재를 가릴 수 없다. */
  const BANNER_BACKGROUND = '#EFF3EE';

  const renderBanner = (show: boolean) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <FirstSessionBanner show={show} />
      </TDSMobileProvider>,
    );

  it('첫 기록이면 기록 탭으로 시선을 보내는 배너를 띄운다', () => {
    const markup = renderBanner(true);

    expect(markup).toContain('첫 독서 기록이 심어졌어요');
    expect(markup).toContain('기록 탭'); // 어디를 보라는 말이 없으면 배너가 제 일을 못 한다
  });

  it('첫 기록이 아니면 배너가 통째로 없다 — 매번 뜨면 축하가 잡음이 된다', () => {
    const markup = renderBanner(false);

    expect(markup).not.toContain('첫 독서 기록이 심어졌어요');
    expect(markup).not.toContain(BANNER_BACKGROUND); // 문구만 지운 빈 상자도 남지 않는다
  });

  it('처음 그려진 홈엔 축하가 없다 — 상태는 stop 응답으로만 켜진다(새로고침하면 사라진다)', () => {
    expect(renderHome()).not.toContain('첫 독서 기록이 심어졌어요');
  });
});

/**
 * 책 0권 — 별도 빈 상태 섹션이 사라지고 **「책 없이」 카드 한 장짜리 캐러셀**이 그 자리를 맡는다.
 * 0권이어도 화면 구조가 같아야 "가운데 온 것으로 측정한다"는 문법을 첫 사용자도 그대로 배운다.
 */
describe('책 0권', () => {
  it('빈 상태 대신 「책 없이」 카드가 선다 — 0권이라고 다른 화면이 뜨지 않는다', () => {
    const markup = renderHome({ readingBooks: [] });

    expect(markup).toContain('data-no-book-card');
    expect(markup).toContain(noBookSubtitle(0));
    expect(markup).not.toContain('아직 책이 없어요');
    expect(labelsOf(markup)).not.toContain('첫 책 추가하기');
  });

  it('측정 중이면 고르는 자리가 통째로 사라진다 — 이미 시작한 뒤엔 바꿀 게 없다', () => {
    const markup = renderHome({
      readingBooks: [],
      hasActiveSession: true,
      activeStartedAt: '2026-08-11T09:00:00',
    });

    expect(markup).not.toContain('data-no-book-card');
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
 * 무엇으로 측정할지 고르는 자리 — **캐러셀 하나**다. 가운데 온 칸이 곧 측정 대상이고, 0번 칸은 언제나
 * 「책 없이」다. 시트는 측정 종료 후 태깅 자리로만 남았다.
 *
 * <p>계측은 네 겹이다: ① 기본 선택은 순수 함수 {@link defaultBookId} ② 칸↔선택값 산식은
 * {@link carouselIndexOf}·{@link selectionAt} ③ 홈 배선은 마크업 손잡이(`data-cover-title`·
 * `data-no-book-card`·`data-selected-book`·`data-dot`) ④ 하니스로 열 수 없는 시트는
 * {@link BookSheet}를 직접 렌더해서.
 *
 * <p>⚠️ 남는 사각지대(실측): `onClick`·스크롤은 마크업에 안 남는다 — 미는 대로 선택이 따라오는지,
 * 첫 진입에 고른 칸이 가운데 서는지, 주 버튼이 정말 그 값으로 시작하는지는 여기서 못 잡는다.
 * jsdom 미도입은 이 저장소의 기존 결정이라, 이 배선들은 실기기·프리뷰 확인을 게이트로 둔다.
 */

/** TDS Button이 인라인으로 박는 채움색 — variant를 가릴 class·속성이 없어 이 값이 유일한 표지다. */
const FILL_PRIMARY = '#3182f6';

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
  const books = [book(1, '데미안'), book(2, '노인과 바다')];

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

/**
 * 캐러셀 0번 칸은 언제나 「책 없이」다 — 선택값 `null`이 그 칸을 가리키고, 책은 한 칸씩 밀린다.
 *
 * <p>인덱스가 어긋나면 스냅 위치와 고른 책이 한 칸씩 틀어지므로(무성 오류) 산식 진입점을 이 두
 * 함수로만 좁히고 왕복 불변식으로 못 박는다.
 */
describe('캐러셀 칸 ↔ 선택값 (carouselIndexOf · selectionAt)', () => {
  const books = [book(1, '데미안'), book(2, '노인과 바다')];

  it('책 없이(null)면 0번 칸 — 목록 맨 앞이 「책 없이」 카드다', () => {
    expect(carouselIndexOf(null, books)).toBe(0);
  });

  it('책을 골랐으면 그 책 칸 — 카드 한 장만큼 밀린다', () => {
    expect(carouselIndexOf(1, books)).toBe(1);
    expect(carouselIndexOf(2, books)).toBe(2);
  });

  it('목록 밖 id면 0번 칸으로 떨어진다 — 다 읽은 책이 stale id로 남아도 화면 밖을 찌르지 않는다', () => {
    expect(carouselIndexOf(99, books)).toBe(0);
  });

  it('책이 0권이면 카드 한 장뿐이라 언제나 0번', () => {
    expect(carouselIndexOf(null, [])).toBe(0);
    expect(carouselIndexOf(7, [])).toBe(0);
  });

  it('0번 칸이 가운데면 책 없이(null)', () => {
    expect(selectionAt(0, books)).toBeNull();
    expect(selectionAt(0, [])).toBeNull();
  });

  it('1번 칸부터 책 — 오프셋이 어긋나면 고른 책이 한 칸씩 틀어진다', () => {
    expect(selectionAt(1, books)).toBe(1);
    expect(selectionAt(2, books)).toBe(2);
  });

  it('왕복해도 그대로다 — 선택 → 칸 → 선택이 어긋나면 스냅과 선택이 갈라진다', () => {
    for (const selection of [null, 1, 2]) {
      expect(selectionAt(carouselIndexOf(selection, books), books)).toBe(selection);
    }
  });
});

describe('책 없이 부제 (noBookSubtitle)', () => {
  it('책이 0권이면 서재로 안내한다 — 「첫 책 추가하기」가 사라진 자리의 유일한 길잡이다', () => {
    expect(noBookSubtitle(0)).toBe('서재에 책을 추가하면 여기서 골라요');
  });

  it('책이 있으면 기록에 책이 안 남는다고 알린다 — 고를 수 있는데 굳이 안 고르는 선택이다', () => {
    expect(noBookSubtitle(2)).toBe('기록에 책이 남지 않아요');
  });
});

/**
 * 스크롤 위치 → 가운데 온 책 — 캐러셀의 유일한 계산이다.
 *
 * <p>트랙 좌우에 `50% - 표지폭/2` 여백을 둬서 i번째 표지의 중앙 정렬 위치가 정확히
 * `i * (표지폭 + 간격)`이 된다 — 그래서 화면 폭이 계산에서 빠진다.
 */
describe('가운데 온 책 (centeredIndex)', () => {
  const stride = COVER_WIDTH + COVER_GAP;

  it('맨 앞이면 첫 책', () => {
    expect(centeredIndex(0, COVER_WIDTH, COVER_GAP, 3)).toBe(0);
  });

  it('한 칸 다 넘기면 다음 책', () => {
    expect(centeredIndex(stride, COVER_WIDTH, COVER_GAP, 3)).toBe(1);
  });

  it('절반을 넘겨야 넘어간다 — 손을 떼면 스냅이 가까운 쪽으로 붙는다', () => {
    expect(centeredIndex(stride * 0.49, COVER_WIDTH, COVER_GAP, 3)).toBe(0);
    expect(centeredIndex(stride * 0.51, COVER_WIDTH, COVER_GAP, 3)).toBe(1);
  });

  it('맨 뒤를 넘어가면 마지막 책으로 클램프 — 스크롤 끝의 관성이 목록 밖을 가리키면 안 된다', () => {
    expect(centeredIndex(stride * 99, COVER_WIDTH, COVER_GAP, 3)).toBe(2);
  });

  it('음수(바운스 스크롤)는 첫 책으로 클램프', () => {
    expect(centeredIndex(-stride, COVER_WIDTH, COVER_GAP, 3)).toBe(0);
  });

  it('책이 0권이면 0 — 인덱스로 목록을 찌르지 않는다', () => {
    expect(centeredIndex(0, COVER_WIDTH, COVER_GAP, 0)).toBe(0);
  });
});

/**
 * 밖에서 바뀐 선택을 트랙이 따라가는가 — 「펼쳐보기」 격자에서 고르면 선택 state만 바뀌어
 * 제목 줄은 그 책인데 표지는 옛 자리에 서 있었다(트랙을 옮기는 길이 마운트·표지 탭 둘뿐이었다).
 *
 * <p>손가락으로 밀어 바뀐 경우엔 이미 그 칸이 가운데라 `null`(재스크롤 없음)이어야 한다 —
 * 안 그러면 스냅이 끝난 자리에 또 스크롤을 걸어 손가락에서 트랙을 빼앗는다(SCROLL_SETTLE_MS 주석).
 */
describe('밖에서 바뀐 선택 따라가기 (recenterIndex)', () => {
  const stride = COVER_WIDTH + COVER_GAP;
  const books = [book(1, '데미안'), book(2, '노인과 바다'), book(3, '토지')];

  it('이미 그 책이 가운데면 null — 손가락으로 민 결과에 또 스크롤을 걸지 않는다', () => {
    expect(recenterIndex(stride, 1, books)).toBeNull();
  });

  it('격자에서 다른 책을 고르면 그 칸으로 — 이번 개선의 본체다', () => {
    expect(recenterIndex(0, 3, books, 0)).toBe(2);
  });

  it('홈은 0번이 「책 없이」라 책이 한 칸 밀린다 — offset이 어긋나면 옆 책으로 간다', () => {
    expect(recenterIndex(0, 1, books)).toBe(1);
  });

  it('책 없이(null)를 고르면 0번 칸으로 돌아온다', () => {
    expect(recenterIndex(stride * 2, null, books)).toBe(0);
  });

  it('목록 밖 id는 0번 칸 — 이미 0번이면 움직일 것이 없다', () => {
    expect(recenterIndex(stride * 2, 99, books)).toBe(0);
    expect(recenterIndex(0, 99, books)).toBeNull();
  });

  it('스크롤이 끝을 넘겨도 마지막 칸으로 클램프해 비교한다 — 관성 오버슈트가 헛스크롤을 부르지 않게', () => {
    expect(recenterIndex(stride * 99, 3, books, 0)).toBeNull();
  });

  it('책이 0권이면 언제나 0번뿐이라 null', () => {
    expect(recenterIndex(0, null, [])).toBeNull();
  });
});

/**
 * 「책 없이」 카드 버튼 조각 — 카드에만 걸린 속성(`aria-current`·margin)을 옆 표지 버튼과 섞지 않고 보려면
 * 그 버튼 하나로 좁혀야 한다(마크업 전체 검색이면 옆 책의 속성에 속는다).
 */
const noBookCardOf = (markup: string) =>
  markup.split('<button').find((chunk) => chunk.includes('data-no-book-card')) ?? '';

describe('표지 캐러셀', () => {
  const books = [
    book(1, '데미안', { author: '헤르만 헤세', coverUrl: 'https://img.example/demian.jpg' }),
    book(2, '노인과 바다', { author: '헤밍웨이' }),
  ];

  /** 캐러셀에 선 책 — 표지 버튼에 계측용 표지를 달아 두었다(TDS emotion 클래스 사이에서 집을 손잡이가 없다). */
  const coverTitlesOf = (markup: string) => [...markup.matchAll(/data-cover-title="([^"]*)"/g)].map((m) => m[1]);

  it('읽는 중 책을 전부 나열한다 — 접어 두던 칩과 달리 좌우로 넘겨 고른다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(coverTitlesOf(markup)).toEqual(['데미안', '노인과 바다']);
  });

  it('가운데 온 책의 제목·저자를 캐러셀 아래에 적는다 — 표지만으론 무슨 책인지 확정되지 않는다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('data-selected-book="노인과 바다"');
    expect(markup).toContain('헤밍웨이');
  });

  it('최근 읽은 책이 목록 밖이면 첫 책이 가운데 — defaultBookId 규칙 그대로다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 99 });

    expect(markup).toContain('data-selected-book="데미안"');
    expect(markup).toContain('헤르만 헤세');
  });

  it('표지가 있으면 그 이미지를 건다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('src="https://img.example/demian.jpg"');
  });

  it('표지가 없으면 첫 글자 + 제목색 상자로 떨어진다 — 빈 자리를 두면 캐러셀에 구멍이 난다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain(`background:${coverColor('노인과 바다')}`);
    expect(markup).toContain('>노</div>');
  });

  it('트랙 세로 여백이 선택 표지의 확대분을 담는다 — 안 담으면 커진 표지가 트랙 밖으로 삐져나간다', () => {
    expect(TRACK_V_PAD).toBeGreaterThanOrEqual((COVER_HEIGHT * 1.1 - COVER_HEIGHT) / 2);
  });

  it('표지 높이는 표지 컴포넌트와 같은 식 — 어긋나면 여백 계산이 실제 표지를 못 따라간다', () => {
    expect(COVER_HEIGHT).toBe(Math.round(COVER_WIDTH * 1.4));
  });

  it('트랙 세로 스크롤은 touch-action이 막는다 — overflow-y:hidden은 웹뷰에서 가로 터치 스크롤까지 함께 죽인다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('touch-action:pan-x');
    expect(markup).not.toContain('overflow-y:hidden');
  });

  it('가운데로 미끄러지는 애니메이션을 CSS가 맡는다 — 웹뷰가 scrollTo의 behavior 옵션을 무시해도 표지는 움직인다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('scroll-behavior:smooth');
  });

  it('세로 여백을 상수 그대로 트랙에 건다 — 상수만 늘리고 스타일이 옛 값이면 계산이 헛돈다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain(`padding:${TRACK_V_PAD}px 0`);
  });

  it('가운데 정렬 여백은 트랙 padding이 아니라 양끝 표지의 margin — WebKit은 스크롤 컨테이너의 끝 padding을 스크롤 영역에서 뺀다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain(`margin-left:${EDGE_SPACE}`);
    expect(markup).toContain(`margin-right:${EDGE_SPACE}`);
    // 표지 2장뿐이면 여지가 통째로 사라져 아예 안 밀렸다(실기기 실측) — 여백이 padding으로 돌아가면 안 된다.
    expect(markup).not.toContain(`padding:${TRACK_V_PAD}px calc(`);
  });

  it('양끝 여백은 첫·마지막 표지에만 — 표지마다 붙으면 사이 간격이 화면 폭만큼 벌어진다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect([...markup.matchAll(/margin-left:calc\(50%/g)]).toHaveLength(1);
    expect([...markup.matchAll(/margin-right:calc\(50%/g)]).toHaveLength(1);
  });

  it('캐러셀 표지는 즉시 로드 — 탭을 오갈 때마다 재마운트돼 lazy면 한 박자 늦게 뜬다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('loading="eager"');
  });

  it('현재 위치를 점으로 표시한다 — 「책 없이」 칸까지 세므로 책 수 + 1이다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect([...markup.matchAll(/data-dot="/g)]).toHaveLength(books.length + 1);
    expect([...markup.matchAll(/data-dot="active"/g)]).toHaveLength(1);
  });

  it('양끝 여백의 왼쪽 주인은 「책 없이」 카드다 — 목록 맨 앞이 카드로 바뀌었다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(noBookCardOf(markup)).toContain(`margin-left:${EDGE_SPACE}`);
  });

  it('「바꾸기」와 시트 진입은 사라진다 — 캐러셀 자체가 고르는 자리다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(labelsOf(markup)).not.toContain('바꾸기');
    expect(markup).not.toContain('어떤 책을 읽을까요?');
    expect(markup).not.toContain('무슨 책을 읽으셨나요?');
  });

  it('섹션 헤더는 상태와 무관한 중립 문구다 — 「책 없이」가 가운데면 "이 책으로"가 틀린 문장이 된다', () => {
    const markup = renderHome({ readingBooks: books, recentBookId: 2 });

    expect(markup).toContain('무엇으로 측정할까요?');
    // 상태별로 갈아끼우지 않는다 — 헤더가 나타났다 사라지면 캐러셀이 세로로 들썩인다(#742 부류).
    expect(renderHome({ readingBooks: [] })).toContain('무엇으로 측정할까요?');
    expect(markup).not.toContain('이 책으로 측정할까요?');
  });

  it('책이 0권이어도 캐러셀은 선다 — 「책 없이」 카드 한 장이 남는다', () => {
    const markup = renderHome({ readingBooks: [] });

    expect(markup).toContain('data-no-book-card');
    expect(coverTitlesOf(markup)).toEqual([]); // 실제 책은 0권
  });

  it('측정 중이면 고르는 자리가 사라진다 — 이미 시작한 뒤엔 바꿀 게 없다', () => {
    const markup = renderHome({
      readingBooks: books,
      hasActiveSession: true,
      activeStartedAt: '2026-08-11T09:00:00',
    });

    expect(markup).not.toContain('무엇으로 측정할까요?');
    expect(markup).not.toContain('data-no-book-card');
    expect(coverTitlesOf(markup)).toEqual([]);
  });
});

/**
 * 「책 없이」 카드 — 보조 버튼이던 「책 없이 시작」이 캐러셀 0번 칸으로 들어왔다. 이제 측정 대상은
 * "가운데 온 것" 하나의 문법으로 통일된다(버튼 갈래가 그 문법을 깨고 있었다).
 *
 * <p>홈 렌더로는 "책이 있는데 책 없이를 고른" 상태에 닿을 수 없어(초기 선택이 늘 책이다) 캐러셀을
 * 직접 렌더해 그 가지를 계측한다 — `BookSheet`·`FirstSessionBanner`와 같은 처지다.
 */
describe('「책 없이」 카드 (캐러셀 0번 칸)', () => {
  const books = [book(1, '데미안', { author: '헤르만 헤세' }), book(2, '노인과 바다')];

  const renderCarousel = (selectedId: number | null, list: BookOption[] = books) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookCarousel books={list} selectedId={selectedId} onSelect={() => {}} />
      </TDSMobileProvider>,
    );

  it('카드가 실제 책보다 앞에 선다 — 0번 칸이 「책 없이」라는 규약이 마크업 순서다', () => {
    const markup = renderCarousel(1);

    expect(markup).toContain('data-no-book-card');
    expect(markup.indexOf('data-no-book-card')).toBeLessThan(markup.indexOf('data-cover-title'));
  });

  it('표지와 같은 크기의 점선 상자다 — 색 상자면 실제 표지와 구분이 안 된다', () => {
    const card = noBookCardOf(renderCarousel(1));

    expect(card).toContain(`width:${COVER_WIDTH}px`);
    expect(card).toContain(`height:${COVER_HEIGHT}px`);
    expect(card).toContain('border:2px dashed');
    // 테두리가 크기를 불리면 스냅 위치(i × stride)가 카드부터 어긋난다.
    expect(card).toContain('box-sizing:border-box');
  });

  it('스크린리더에는 「책 없이 측정」으로 읽힌다', () => {
    expect(noBookCardOf(renderCarousel(1))).toContain('aria-label="책 없이 측정"');
  });

  it('책 없이를 고르면 카드가 현재 칸이고 제목 자리가 그 말을 한다', () => {
    const markup = renderCarousel(null);

    expect(noBookCardOf(markup)).toContain('aria-current="true"');
    expect(markup).toContain('data-selected-book="책 없이 측정"');
    expect(markup).toContain(noBookSubtitle(books.length));
  });

  it('책을 고르면 카드는 현재 칸이 아니다 — 강조가 둘이면 무엇으로 측정하는지 어긋난다', () => {
    const markup = renderCarousel(1);

    expect(noBookCardOf(markup)).not.toContain('aria-current');
    expect(markup).toContain('data-selected-book="데미안"');
    expect(markup).not.toContain(noBookSubtitle(books.length));
  });

  it('책이 0권이어도 카드 한 장으로 캐러셀이 선다 — 「첫 책 추가하기」 빈 상태를 대신한다', () => {
    const markup = renderCarousel(null, []);

    expect(markup).toContain('data-no-book-card');
    expect(markup).toContain(noBookSubtitle(0)); // 서재로 가는 유일한 안내
    expect([...markup.matchAll(/data-dot="/g)]).toHaveLength(1);
  });

  it('0권이면 양끝 여백이 카드 한 장에 둘 다 붙는다 — 처음이자 마지막 칸이다(T-160의 margin 규약)', () => {
    const card = noBookCardOf(renderCarousel(null, []));

    expect(card).toContain(`margin-left:${EDGE_SPACE}`);
    expect(card).toContain(`margin-right:${EDGE_SPACE}`);
  });
});

/**
 * 측정 종료로 열리는 태깅 시트 — 정적 렌더로는 열린 상태에 못 가므로 직접 렌더해 계측한다.
 *
 * <p>한때 시작 자리(`start` 모드)를 겸했지만 고르기는 캐러셀이 가져갔다 — 이제 자리가 하나라 모드도 없다.
 */
describe('태깅 시트 (BookSheet)', () => {
  const books = [book(1, '데미안'), book(2, '노인과 바다')];

  const renderSheet = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookSheet books={books} disabled={false} onPick={() => {}} onSkip={() => {}} onClose={() => {}} />
      </TDSMobileProvider>,
    );

  /** 시트의 책 행 — TDS Button이 아니라 표지+제목 한 줄이라 제목만 뽑는다. */
  const rowTitlesOf = (markup: string) => [...markup.matchAll(/data-book-title="([^"]*)"/g)].map((m) => m[1]);

  it('종료 후 문구와 "건너뛰기" CTA — 시트가 서는 자리는 태깅 하나뿐이다', () => {
    const markup = renderSheet();

    expect(markup).toContain('무슨 책을 읽으셨나요?');
    expect(labelsOf(markup)).toContain('건너뛰기');
    expect(markup).not.toContain('어떤 책을 읽을까요?');
    expect(labelsOf(markup)).not.toContain('책 없이 시작');
  });

  it('책을 전부 한 행씩 나열하고 표지 자리를 세운다 — 버튼 나열엔 없던 시각 위계다', () => {
    const markup = renderSheet();

    expect(rowTitlesOf(markup)).toEqual(['데미안', '노인과 바다']);
    expect(markup).toContain(`background:${coverColor('데미안')}`);
    expect(markup).toContain('>데</div>');
  });

  it('딤·패널이 탭바(zIndex 100) 위에 뜬다 — 시트 아래로 탭바가 비치면 시트가 아니다', () => {
    const layers = [...renderSheet().matchAll(/z-index:(\d+)/g)].map((m) => Number(m[1]));

    expect(layers.length).toBe(2); // 딤 + 패널
    expect(Math.min(...layers)).toBeGreaterThan(TAB_BAR_Z_INDEX);
    expect(layers[1]).toBeGreaterThan(layers[0]); // 패널이 자기 딤에 가리지 않는다
  });

  it('홈 인디케이터를 피해 하단 여백을 둔다', () => {
    expect(renderSheet()).toContain('env(safe-area-inset-bottom)');
  });
});

/**
 * 시작 버튼 — 갈래가 하나로 합쳐졌다. 보조 「책 없이 시작」이 캐러셀 0번 칸으로 흡수돼,
 * **무엇으로** 측정할지는 캐러셀이, **시작**은 주 버튼 하나가 맡는다.
 */
describe('시작 버튼', () => {
  const books = [book(1, '데미안'), book(2, '노인과 바다')];

  it('책이 있어도 주 버튼 하나뿐 — 보조 갈래는 캐러셀 카드가 대신한다', () => {
    const markup = renderHome({ readingBooks: books });

    expect(fillOf(markup, '측정 시작')).toBe(FILL_PRIMARY);
    expect(labelsOf(markup)).not.toContain('책 없이 시작');
  });

  it('책이 0권이어도 "측정 시작" 하나', () => {
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
  });

  it('「책 없이 시작」이라는 말이 홈 어디에도 없다 — 라벨 소멸이 이번 변경의 요구다', () => {
    expect(renderHome({ readingBooks: books })).not.toContain('책 없이 시작');
    expect(renderHome({ readingBooks: [] })).not.toContain('책 없이 시작');
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
 * 계정 진입점 — 홈 하단은 이제 **설정 화면으로 가는 손잡이**다.
 *
 * <p>예전엔 "계정 관리·상세 설정은 booktimer.app에서" + 로그아웃이었는데, 토스로 가입한 계정은 비밀번호가
 * 없어 그 웹에 로그인 자체가 불가능하다 — 실행할 수 없는 죽은 안내였다(핸들 배너가 앓던 것과 같은 병).
 * 로그아웃 2단 확인은 설정 화면으로 이사했고(`settings.test.tsx`), 여기엔 진입만 남는다.
 */
describe('계정 진입점', () => {
  it('설정 화면으로 가는 손잡이를 그린다', () => {
    expect(renderHome()).toContain('프로필·설정');
  });

  it('실행할 수 없는 안내는 지웠다 — 토스로 가입한 계정은 booktimer.app에 로그인할 수 없다', () => {
    expect(renderHome()).not.toContain('booktimer.app');
  });

  it('로그아웃은 홈에서 사라졌다 — 두 자리에 두면 확인 단계가 갈라진다', () => {
    expect(renderHome()).not.toContain('로그아웃');
  });

  it('홈 맨 위에 둔다 — 피드 박스 뒤에 두면 스크롤 끝이라 눈에 안 띈다(사용자 결정 2026-08-14)', () => {
    const markup = renderHome();

    expect(markup.indexOf('프로필·설정')).toBeGreaterThan(0);
    // 히어로 카드보다도 앞 — 카드 위 한 줄이 이 손잡이의 자리다.
    expect(markup.indexOf('프로필·설정')).toBeLessThan(markup.indexOf('오늘 읽은 시간'));
  });
});

/**
 * 전환 이벤트 배선 — 콘솔 「핵심 지표」의 대표 전환을 `reading_session_completed`로 갈아끼우려면
 * 그 이벤트가 **실제로 발생한 적**이 있어야 선택기에 뜬다. 즉 이 두 호출이 없으면 지표 교체 자체가 막힌다.
 *
 * <p>소스로 계측하는 이유는 잔디 `scrollLeft`와 같다 — 정적 렌더 하니스라 「측정 시작」·「측정 끝내기」를
 * 눌러 성공 콜백에 도달할 수 없다. 래퍼 자체의 행동은 `toss.test.ts`가 맡는다.
 */
describe('전환 이벤트 — Home 배선', () => {
  const source = readFileSync(new URL('./screens/Home.tsx', import.meta.url), 'utf8');

  it('측정 시작 성공 경로에서 reading_session_started를 쏜다', () => {
    expect(source).toContain("trackEvent('reading_session_started'");
  });

  it('측정 종료 성공 경로에서 reading_session_completed를 읽은 초와 함께 쏜다', () => {
    expect(source).toContain("trackEvent('reading_session_completed'");
    expect(source).toContain('duration_seconds');
  });
});
