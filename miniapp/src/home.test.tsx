import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { DashboardResponse } from './api';
import { ApiError, waiveDebt } from './api';
import { BookList, Home, claimDebtWaiver, defaultBookId, showWaiverButton, waiverErrorMessage } from './screens/Home';
import { graph, userAgent } from './test-fixtures';
import { coverColor } from './ui';
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

/**
 * 책 고르기 — 웹 `BookPickForm`의 의미론을 옮겼다. 홈엔 **고른 책 칩 하나**만 두고(목록 상시 노출 아님),
 * 「바꾸기」로 그 아래 목록을 펴서 바꾼다. 시작은 아래 주 버튼이 맡는다.
 *
 * <p>계측은 세 겹이다: ① 기본값은 순수 함수 {@link defaultBookId} ② **접힌 상태**는 홈 마크업
 * (칩의 책만 보이고 나머지 책 제목은 아예 없다) ③ **편 목록**은 하니스로 「바꾸기」를 누를 수 없으니
 * {@link BookList}를 직접 렌더해서.
 *
 * <p>⚠️ 남는 사각지대(실측): `onClick`은 마크업에 안 남는다 — 「바꾸기」가 목록을 펴는지, 목록에서 고르면
 * 접히는지, 주 버튼이 정말 고른 책으로 시작하는지는 여기서 못 잡는다(핸들러를 통째로 바꿔도 통과).
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

/** 「바꾸기」로 펴는 목록 — 정적 렌더로는 편 상태에 못 가므로 직접 렌더해 계측한다. */
describe('고르기 목록 (BookList)', () => {
  const books = [
    { id: 1, title: '데미안' },
    { id: 2, title: '노인과 바다' },
  ];

  const renderList = (selectedId: number | null) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BookList books={books} selectedId={selectedId} disabled={false} onPick={() => {}} />
      </TDSMobileProvider>,
    );

  it('책을 전부 나열하고 고른 책만 채움으로 구분한다', () => {
    const markup = renderList(2);

    expect(labelsOf(markup)).toEqual(['데미안', '노인과 바다']);
    expect(fillOf(markup, '노인과 바다')).toBe(FILL_PRIMARY);
    expect(fillOf(markup, '데미안')).toBe(FILL_WEAK);
  });

  it('고른 책이 없으면 전부 흐리다 — 종료 후 태깅 목록이 이 모드다', () => {
    const markup = renderList(null);

    expect(fillOf(markup, '데미안')).toBe(FILL_WEAK);
    expect(fillOf(markup, '노인과 바다')).toBe(FILL_WEAK);
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
