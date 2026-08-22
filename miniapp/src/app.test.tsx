import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  App,
  BottomTabBar,
  MainTabs,
  MarginShell,
  REFRESH_THROTTLE_MS,
  TAB_BAR_HEIGHT,
  TAB_BAR_MARGIN,
  TAB_BAR_Z_INDEX,
  COACHMARK_FLOW,
  LAMP_CLASS,
  TABS,
  TIMER_ACTION_SLOT,
  TabBarCoachmark,
  closeCompose,
  flowStepsOnAbandon,
  lampOn,
  flowTabChange,
  nextFlowStep,
  shouldRefresh,
  shouldShowGuideBanner,
  tabLocked,
  slotCenter,
  tabChangeHandler,
  tabSlot,
  timerActionView,
  timerStartBookId,
} from './App';
import type { BookOption, DashboardResponse } from './api';
import { coachmarkSeen, dismissCoachmark, resetCoachmarks } from './coachmark';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

beforeEach(stubLocalStorage); // 홈 탭이 렌더 중에 알림 동의 캐시를 읽는다

/**
 * 탭 재편 안전망 — 탭 값과 화면의 대응, 그리고 재편 전부터 있던 오케스트레이션(토큰 유무 → 로그인/로딩)을
 * 못 박는다. 하니스가 `renderToStaticMarkup`이라 클릭은 못 잡으므로, 탭바와 본문을 같은 `TABS` 목록에서
 * 그리게 하고 "선택된 탭 = 그려진 화면"을 단언해 index↔화면 어긋남을 잡는다.
 */

const dashboard: DashboardResponse = {
  nickname: '구스펠',
  loginId: 'goospel',
  previousLoginId: null,
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
  emailVerified: true,
};

function renderTab(tab: (typeof TABS)[number]['key'], overrides: Partial<DashboardResponse> = {}) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <MainTabs
        tab={tab}
        onTabChange={() => {}}
        dashboard={{ ...dashboard, ...overrides }}
        homeBookId={undefined}
        onSelectHomeBook={() => {}}
        onOpenMargin={() => {}}
        onComposeMargin={() => {}}
        onTimerChange={() => {}}
        onStartTimer={() => Promise.resolve()}
        onGraphChange={() => {}}
        onGoGoal={() => {}}
        goalAdPending={false}
        onShelfChanged={() => {}}
        onHandleCreated={() => {}}
        onGoSettings={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

describe('탭 구조', () => {
  it('탭마다 그 탭의 화면을 그린다 — 탭 선택 ↔ 화면 대응', () => {
    // 홈은 제목 행이 없다(첫 카드가 곧 히어로) — 그 카드의 문구로 식별한다.
    expect(renderTab('home')).toContain('오늘 읽은 시간');
    expect(renderTab('library')).toContain('내 서재');
    // 책방 탭은 중간 화면 없이 곧장 내 책방이다 — 그 상단의 검색 진입바로 식별한다.
    expect(renderTab('bookshop')).toContain('아이디로 친구 찾기');
    expect(renderTab('history')).toContain('내 기록');
  });

  it('다른 탭의 화면은 함께 그리지 않는다 — 한 번에 한 화면', () => {
    expect(renderTab('home')).not.toContain('내 기록');
    expect(renderTab('library')).not.toContain('오늘 읽은 시간');
    expect(renderTab('home')).not.toContain('아이디로 친구 찾기');
  });

  it('탭바에서 선택 표시되는 항목은 지금 그려진 탭 자신이다 — index 어긋남 방지', () => {
    expect(TABS.map((t) => t.label)).toEqual(['홈', '서재', '책방', '기록']);

    for (const { key, label } of TABS) {
      const markup = renderTab(key);
      // 탭 role을 버리고 하단 내비게이션 표준(`aria-current`)으로 갔다 — 현재 위치 표식과 라벨의 짝을 본다.
      expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
      expect(markup).toContain(`aria-current="page" title="${label}"`);
    }
  });

  it('탭바가 준 index를 그 자리의 탭으로 옮긴다 — 눌린 탭과 바뀌는 탭이 같다', () => {
    const picked: string[] = [];
    const change = tabChangeHandler((tab) => picked.push(tab));

    TABS.forEach((_, index) => change(index));

    expect(picked).toEqual(['home', 'library', 'bookshop', 'history']);
  });

  it('책방 탭은 서재와 기록 사이에 온다 — 탭바 순서가 곧 TABS 순서다', () => {
    expect(TABS.map((t) => t.key)).toEqual(['home', 'library', 'bookshop', 'history']);
  });

  /**
   * 여백은 더 이상 책방 탭으로 점프해 열리지 않는다 — App이 <b>탭 위 전체 화면</b>으로 든다(설계 §3.1).
   * 뒤로 가면 열었던 탭이 그대로 남아야 하는데, 탭을 갈아끼우던 옛 배선은 홈에서 들어온 사람을
   * 책방(남에게 보여지는 전시장)으로 밀어냈다. 그 전달 수단(`marginTarget`)이 통째로 사라졌음을 못 박는다.
   */
  it('책방 탭은 언제나 내 책방으로 열린다 — 여백 점프가 이 탭을 가로채지 않는다', () => {
    const markup = renderTab('bookshop');

    expect(markup).toContain('아이디로 친구 찾기');
  });

  it('탭 셸은 여백 점프 대상을 나르지 않는다 — 전이는 App 전역 뷰가 진다', () => {
    const source = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8');

    expect(source).not.toContain('marginTarget');
    expect(source).toContain('<BookMargin'); // 대신 App이 직접 든다(부재 단언의 쌍)
  });
});

/**
 * 하단 탭바 — 네이티브 탭바의 최소 조건(아이콘+라벨 · 손가락 크기 · 홈 인디케이터 회피)을 못 박는다.
 * 색·굵기 같은 순수 시각값은 단언하지 않는다 — 구조와 가림 방지만 회귀 대상이다.
 */
describe('하단 탭바', () => {
  const bar = (
    tab: (typeof TABS)[number]['key'],
    action: { active?: boolean; busy?: boolean } = {},
  ) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BottomTabBar
          tab={tab}
          onTabChange={() => {}}
          action={{ active: action.active ?? false, busy: action.busy ?? false, onPress: () => {} }}
        />
      </TDSMobileProvider>,
    );

  /** 액션 버튼 한 칸만 — 알약 전체를 보면 탭들의 색·크기가 섞여 단언이 공허해진다. */
  const actionCell = (markup: string, label: string) => {
    const at = markup.indexOf(`aria-label="${label}"`);
    expect(at).toBeGreaterThan(-1); // 라벨을 못 찾으면 아래 단언이 빈 문자열을 검사하게 된다
    return markup.slice(at, markup.indexOf('</button>', at));
  };

  it('탭마다 아이콘과 라벨을 함께 그린다 — 라벨만 있는 밋밋한 바가 이질감의 주범이었다', () => {
    const markup = bar('home');

    // 탭 4개 + 가운데 액션 = 아이콘 5개(액션은 라벨 없이 아이콘만 선다).
    expect(markup.match(/<svg/g)).toHaveLength(TABS.length + 1);
    for (const { label } of TABS) expect(markup).toContain(label);
  });

  it('탭 하나의 터치 영역이 손가락 최소치(44px)를 넘는다', () => {
    const heights = bar('home').match(/min-height:(\d+)px/g) ?? [];

    expect(heights).toHaveLength(TABS.length + 1); // 액션 칸도 같은 터치 높이를 가진다
    for (const h of heights) expect(Number(h.match(/\d+/)![0])).toBeGreaterThanOrEqual(44);
  });

  it('5칸이다 — 탭 4개 사이에 측정 액션이 한 칸으로 낀다', () => {
    expect(bar('home').match(/<button/g)).toHaveLength(TABS.length + 1);
  });

  it('액션은 서재와 책방 사이 가운데 자리다 — 마크업 순서가 곧 시각 순서다', () => {
    const markup = bar('home');

    expect(markup.indexOf('title="서재"')).toBeLessThan(markup.indexOf('aria-label="측정 시작"'));
    expect(markup.indexOf('aria-label="측정 시작"')).toBeLessThan(markup.indexOf('title="책방"'));
    expect(TIMER_ACTION_SLOT).toBe(2);
  });

  it('탭 role을 쓰지 않는다 — tabpanel 없는 tablist는 규약 위반이고, 액션은 애초에 탭이 아니다', () => {
    const markup = bar('home');

    expect(markup).not.toContain('role="tab');
    expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
    expect(markup).toContain('aria-current="page" title="홈"');
  });

  it('액션은 채운 원이다 — 탭 아이콘들 사이에서 동작으로 읽히는 유일한 형태다', () => {
    const cell = actionCell(bar('home'), '측정 시작');

    expect(cell).toContain('width:44px;height:44px');
    expect(cell).toContain('border-radius:50%');
    expect(cell).toContain('#6E8A6A'); // 시작 = 브랜드 세이지
  });

  it('측정 중이면 빨간 ■로 바뀐다 — 시작과 끝내기가 한 자리에서 갈린다', () => {
    const markup = bar('home', { active: true });

    expect(markup).toContain('aria-label="측정 끝내기"');
    expect(markup).not.toContain('aria-label="측정 시작"');
    expect(actionCell(markup, '측정 끝내기')).toContain('#F04452');
  });

  it('처리 중이면 원이 흐려진다 — 연타로 세션이 두 번 시작되지 않게', () => {
    expect(actionCell(bar('home', { busy: true }), '측정 시작')).toContain('opacity:0.6');
  });

  it('선택 탭 색 폴백이 웹 세이지다 — 변수가 안 잡히는 순간 토스 블루로 되돌아가는 걸 막는다', () => {
    expect(bar('home')).toContain('var(--adaptiveBlue500, #6E8A6A)');
    expect(bar('home')).not.toContain('#3182f6');
  });

  it('바닥에 붙지 않고 떠 있다 — 토스 브랜딩 가이드가 요구하는 플로팅 형태(심사 반려 2)', () => {
    // T-144: 단일 속성 한 조각은 TDS 주입 CSS와 겹쳐 공허해진다 — 인접 속성을 이어 붙인 조합을 키로 쓴다.
    expect(bar('home')).toContain(`left:${TAB_BAR_MARGIN}px;right:${TAB_BAR_MARGIN}px;bottom:calc(12px + env(safe-area-inset-bottom))`);
    expect(bar('home')).toMatch(/border-radius:28px;box-shadow:0 4px 16px/);
    expect(bar('home')).not.toContain('border-top'); // 전폭 부착 형태의 서명
  });

  it('홈 인디케이터만큼 아래를 띄운다 — safe-area가 없으면 바가 인디케이터에 깔린다', () => {
    expect(bar('home')).toContain('env(safe-area-inset-bottom)');
  });

  it('본문 아래 여백이 떠 있는 바 전체를 덮는다 — 마지막 요소(격언·계정)가 바에 가리지 않는다', () => {
    expect(renderTab('home')).toContain(
      `padding-bottom:calc(${TAB_BAR_HEIGHT}px + 12px + env(safe-area-inset-bottom) + 16px)`,
    );
  });
});

/**
 * 가운데 액션이 시작할 책 — 홈 「측정 시작」이 쓰던 규칙 그대로다(단일 출처).
 *
 * <p>어느 탭에서 눌러도 "홈 캐러셀에 지금 가운데 와 있는 그 책"으로 시작한다. 어떤 조합에서도 `null`까지는
 * 떨어지므로 **죽은 버튼이 될 수 없다**는 것이 이 함수의 핵심 계약이다.
 */
describe('액션이 시작할 책 (timerStartBookId)', () => {
  const book = (id: number): BookOption => ({ id, title: `책${id}`, author: null, coverUrl: null });
  const books = [book(1), book(2)];

  it('아직 안 골랐으면 이어 읽기 책 — 홈 기본값과 같은 규칙이다', () => {
    expect(timerStartBookId(books, 2, undefined)).toBe(2);
  });

  it('이어 읽기가 목록 밖이면 첫 책 — 다 읽었거나 뺀 책으로 시작하지 않는다', () => {
    expect(timerStartBookId(books, 99, undefined)).toBe(1);
  });

  it('책이 0권이면 책 없이 시작한다 — 죽은 버튼이 되지 않는 마지막 폴백', () => {
    expect(timerStartBookId([], null, undefined)).toBeNull();
  });

  it('「책 없이」를 고른 사람은 그대로 존중한다 — null은 undefined와 다른 값이다', () => {
    expect(timerStartBookId(books, 1, null)).toBeNull();
  });

  it('고른 책이 있으면 그 책이다', () => {
    expect(timerStartBookId(books, 1, 2)).toBe(2);
  });

  it('고른 책이 서재에서 빠졌으면 책 없이로 강등 — 홈 배선과 같은 처리다', () => {
    expect(timerStartBookId(books, 1, 99)).toBeNull();
  });
});

/** 액션 버튼의 두 얼굴 — 상태에 따라 라벨·색·아이콘이 통째로 갈린다. */
describe('액션 버튼 시각 (timerActionView)', () => {
  it('대기 중이면 세이지 ▶ 「측정 시작」', () => {
    const view = timerActionView(false);

    expect(view.label).toBe('측정 시작');
    expect(view.background).toContain('#6E8A6A');
  });

  it('측정 중이면 빨강 ■ 「측정 끝내기」 — 옛 홈 danger 버튼의 색 연속성', () => {
    const view = timerActionView(true);

    expect(view.label).toBe('측정 끝내기');
    expect(view.background).toContain('#F04452');
  });

  it('두 상태의 색과 아이콘이 서로 다르다 — 전환이 눈에 보여야 한다', () => {
    expect(timerActionView(true).background).not.toBe(timerActionView(false).background);
    expect(timerActionView(true).icon).not.toBe(timerActionView(false).icon);
  });
});

/**
 * 측정 액션은 `MainTabs`가 든다 — 홈이 언마운트되는 다른 탭에서도 시작·종료할 수 있다는 것이
 * 이 변경의 존재 이유다. 정적 렌더라 클릭은 못 잡으므로 마크업 손잡이와 소스로 계측한다.
 */
describe('어느 탭에서든 측정 (MainTabs)', () => {
  it('네 탭 어디서든 시작 버튼이 서 있다', () => {
    for (const { key } of TABS) expect(renderTab(key)).toContain('aria-label="측정 시작"');
  });

  it('측정 중이면 네 탭 어디서든 끝낼 수 있다 — 홈으로 돌아가지 않아도 된다', () => {
    for (const { key } of TABS) {
      const markup = renderTab(key, { hasActiveSession: true, activeStartedAt: '2026-08-17T09:00:00' });

      expect(markup).toContain('aria-label="측정 끝내기"');
      expect(markup).not.toContain('aria-label="측정 시작"');
    }
  });

  /**
   * 전환 이벤트 — 콘솔 「핵심 지표」의 대표 전환이 `reading_session_completed`라, 이 두 호출이 빠지면
   * 지표 자체가 죽는다. 소스로 계측하는 이유는 정적 렌더라 성공 콜백에 도달할 수 없기 때문이다
   * (Home에 있던 같은 단언이 액션과 함께 여기로 이사했다).
   */
  describe('전환 이벤트 배선', () => {
    const source = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8');

    it('측정 시작 성공 경로에서 reading_session_started를 쏜다', () => {
      expect(source).toContain("trackEvent('reading_session_started'");
    });

    it('측정 종료 성공 경로에서 reading_session_completed를 읽은 초와 함께 쏜다', () => {
      expect(source).toContain("trackEvent('reading_session_completed'");
      expect(source).toContain('duration_seconds');
    });
  });
});

/**
 * 첫 진입 투어 — 탭바만 가리키는 세 걸음(측정 원 → 서재 → 책방)으로 <b>지도</b>를 가르친다.
 * 행동(책 담기·여백)은 그 화면에서 인라인 코치마크가 맡는다(`coachmark.test.tsx`).
 *
 * <p>스포트라이트는 마스크가 아니라 <b>층 순서</b>로 성립한다: 덮개가 탭바보다 한 층 아래라야
 * 탭바만 밝게 남고 가리킨 칸이 그대로 눌린다. 그 부등식이 이 장치의 유일한 성립 조건이라 계측한다
 * (덮개를 탭바 위로 올리면 안내가 가리키는 버튼 자체를 가리는 자기모순이 된다).
 */
describe('첫 진입 투어', () => {
  const TIMER_GUIDE = '여기를 눌러 독서 시간을 재요';
  const LIBRARY_GUIDE = '서재는 내가 읽는 책을 담는 곳';
  const BOOKSHOP_GUIDE = '책방은 남에게 보여주는 곳';

  it('다섯 걸음이 측정 → 서재 → 책 담기 → 책방 → 여백 순서다 — 배열 순서가 곧 흐름이다', () => {
    expect(COACHMARK_FLOW.map((s) => s.name)).toEqual(['timer', 'library', 'add-book', 'bookshop', 'margin']);
  });

  it('걸음마다 설명할 화면이 정해져 있다 — 서재 설명 다음에 그 화면의 책 담기가 이어진다', () => {
    expect(COACHMARK_FLOW.map((s) => s.tab)).toEqual(['home', 'library', 'library', 'bookshop', 'home']);
  });

  it('인라인 걸음(책 담기·여백)은 탭바 말풍선을 갖지 않는다 — 그 화면 안의 안내가 맡는다', () => {
    expect(COACHMARK_FLOW.filter((s) => s.bubble === undefined).map((s) => s.name)).toEqual([
      'add-book',
      'margin',
    ]);
  });

  /*
   * ⚠️ 말풍선 계측은 `MainTabs` 렌더가 아니라 **`TabBarCoachmark` 직접 렌더**로 한다 — 수동 시작이 된 뒤
   * 정적 렌더 하니스로는 「걷는 중」 상태를 만들 수 없다(배너 클릭이 안 돈다). 옛 테스트를 그대로 두면
   * 부재 단언들이 **영영 참**이 되어 공허하게 통과했다(T-149·T-180·T-181 계열). 걸음 선택은 아래
   * `nextFlowStep()` 단언이 맡고, 생김새는 이 렌더가 맡는다.
   */
  function bubbleOf(index: number) {
    const bubble = COACHMARK_FLOW[index].bubble;
    if (bubble === undefined) throw new Error(`탭바 걸음이 아니다: ${COACHMARK_FLOW[index].name}`);
    return bubble;
  }

  const renderStep = (index: number) =>
    renderToStaticMarkup(<TabBarCoachmark bubble={bubbleOf(index)} index={index} onNext={() => {}} />);

  it('처음 온 사람의 커서는 첫 걸음(측정)을 가리킨다', () => {
    expect(nextFlowStep()).toBe(0);
    expect(renderStep(0)).toContain(TIMER_GUIDE);
  });

  it('#833로 측정 안내를 이미 본 사람은 서재 걸음부터 본다 — 마이그레이션 없이 이어진다', () => {
    dismissCoachmark('timer');

    expect(nextFlowStep()).toBe(1);
    expect(renderStep(1)).toContain(LIBRARY_GUIDE);
  });

  it('앞 세 걸음을 봤으면 책방 걸음이 뜬다 — 인라인 걸음(책 담기)도 순서에 든다', () => {
    ['timer', 'library', 'add-book'].forEach(dismissCoachmark);

    expect(nextFlowStep()).toBe(3);
    expect(renderStep(3)).toContain(BOOKSHOP_GUIDE);
  });

  it('인라인 걸음 차례에는 탭바 말풍선을 그릴 것이 없다 — 두 안내가 겹치지 않는다', () => {
    dismissCoachmark('timer');
    dismissCoachmark('library');

    expect(nextFlowStep()).toBe(2); // add-book
    expect(() => renderStep(2)).toThrow(); // 그릴 bubble이 없다 = 탭바엔 아무것도 안 뜬다
  });

  it('다 본 사람의 커서는 끝을 가리킨다', () => {
    COACHMARK_FLOW.forEach((step) => dismissCoachmark(step.name));

    expect(nextFlowStep()).toBe(-1);
  });

  it('안내는 탭바 위에 고정으로 선다 — 어느 탭에서 걷든 자리가 같다', () => {
    expect(renderStep(0)).toContain('position:fixed');
  });

  it('덮개는 탭바보다 아래, 말풍선은 위 — 탭바만 밝게 남고 원은 그대로 눌린다', () => {
    const markup = renderStep(0);

    expect(markup).toContain(`z-index:${TAB_BAR_Z_INDEX - 1}`); // 덮개
    expect(markup).toContain(`z-index:${TAB_BAR_Z_INDEX + 1}`); // 말풍선
  });

  it('진행 표시는 흐름 전체(인라인 걸음까지) 길이다 — 「3걸음 중 2」로 줄여 세지 않는다', () => {
    expect(renderStep(0).match(/data-tour-dot/g)).toHaveLength(COACHMARK_FLOW.length);
  });

  it('마지막 걸음의 덮개는 「닫기」, 그전은 「다음」 — 끝이 어디인지 손잡이가 말한다', () => {
    expect(renderStep(0)).toContain('다음 안내 보기');
    expect(renderToStaticMarkup(
      <TabBarCoachmark bubble={bubbleOf(3)} index={COACHMARK_FLOW.length - 1} onNext={() => {}} />,
    )).toContain('안내 닫기');
  });
});

/**
 * 걸음이 스스로 화면을 옮긴다 — 설명하는 화면을 <b>딤 뒤에 보여주면서</b> 읽게 한다(사용자 결정).
 *
 * <p>이동 자체는 effect라 정적 렌더로 계측할 수 없으니, 「어느 탭으로 가야 하나」 판정만 순수 함수로
 * 떼어 여기서 박는다. 실제 연쇄 이동은 실 브라우저 게이트가 맡는다(T-182와 같은 분담).
 */
/**
 * 안내 배너 — 첫 사용 안내를 <b>사용자가 눌러서</b> 시작한다.
 *
 * <p>2026-08-17 토스 심사 반려: 「미니앱 접속 직후 바텀시트가 바로 노출돼요」. 우리가 만든 것은 툴팁이었지만
 * 렌더 결과가 **전체 딤 + 하단 좌우 24px 와이드 패널**이라 시트와 구별되지 않았다(2026-08-12 「즉시 로그인 유도」
 * 반려와 같은 계열 — 심사는 진입 직후 들이대는 것을 막는다). 그래서 안내 자체는 그대로 두고 **시작 트리거만**
 * 자동 → 수동으로 뒤집었다.
 */
describe('안내 배너 — 수동 시작', () => {
  it('안 본 길 안내가 있으면 배너를 띄운다 — 진입 직후 오버레이 대신 이것이 뜬다', () => {
    expect(shouldShowGuideBanner(false, false)).toBe(true);
    expect(renderTab('home')).toContain('앱 사용법 보기');
  });

  it('걷는 중에는 배너를 감춘다 — 딤 아래에 배너가 남아 있으면 두 겹이 된다', () => {
    expect(shouldShowGuideBanner(true, false)).toBe(false);
  });

  it('길 안내를 다 본 사람에게는 안 뜬다 — 여백 걸음이 남아 있어도 그렇다', () => {
    // 신규 사용자는 책이 0권이라 홈 여백 문이 없어 마지막 걸음 키가 영영 안 남는다 —
    // 「전 걸음 완료」로 판정하면 그 사람에겐 배너가 평생 뜬다. 그래서 기준은 길 안내(탭바 걸음)뿐이다.
    ['timer', 'library', 'bookshop'].forEach(dismissCoachmark);

    expect(coachmarkSeen('margin')).toBe(false); // 여백은 아직 안 봤다(이 단언이 위 주석의 전제다)
    expect(shouldShowGuideBanner(false, false)).toBe(false);
  });

  it('#833 안내만 본 옛 사용자에게는 뜬다 — 서재·책방을 아직 모른다', () => {
    dismissCoachmark('timer');

    expect(shouldShowGuideBanner(false, false)).toBe(true);
  });

  it('닫는 손잡이가 있다 — 「들이대지 않는다」가 배너에도 있어야 한다', () => {
    expect(renderTab('home')).toContain('안내 배너 닫기');
  });

  it('✕로 닫으면 그 자리에서 사라진다 — 기록만으로는 화면이 안 갱신된다(실 브라우저 실측)', () => {
    // 배너만 보고 있을 때 커서는 이미 -1이라 `setFlowIndex(-1)`이 같은 값이고, React가 리렌더를 건너뛴다.
    // 그래서 닫힘을 별도 상태로 들어야 즉시 사라진다 — 기록(키 3개)은 다음 진입의 판정만 맡는다.
    expect(shouldShowGuideBanner(false, true)).toBe(false);
    expect(shouldShowGuideBanner(false, false)).toBe(true); // 짝 단언 — 위가 공허하지 않다
  });

  it('홈에만 둔다 — 진입 화면이 아닌 탭에서는 소음이다', () => {
    expect(renderTab('library')).not.toContain('앱 사용법 보기');
  });
});

describe('흐름의 자동 이동', () => {
  it('그 걸음의 화면이 아니면 그 화면으로 데려간다', () => {
    expect(flowTabChange(COACHMARK_FLOW[1], 'home')).toBe('library'); // 서재 걸음
    expect(flowTabChange(COACHMARK_FLOW[3], 'library')).toBe('bookshop'); // 책방 걸음
    expect(flowTabChange(COACHMARK_FLOW[4], 'bookshop')).toBe('home'); // 여백 걸음 = 출발한 자리로 복귀
  });

  it('이미 그 화면이면 옮기지 않는다 — 같은 탭으로 다시 보내는 헛 전환이 없다', () => {
    expect(flowTabChange(COACHMARK_FLOW[1], 'library')).toBeNull();
    expect(flowTabChange(COACHMARK_FLOW[2], 'library')).toBeNull(); // 서재 설명 → 책 담기: 제자리
  });

  it('흐름이 끝났으면 아무 데도 안 데려간다', () => {
    expect(flowTabChange(undefined, 'bookshop')).toBeNull();
  });

  it('걷는 중 스스로 탭을 옮기면 길 안내만 접고 인라인 걸음은 남긴다', () => {
    // 길 안내(탭바)는 사용자가 이미 스스로 하고 있으니 물러나지만, 책 담기·여백은 그 화면에 갔을 때가 제 차례다.
    expect(flowStepsOnAbandon()).toEqual(['timer', 'library', 'bookshop']);
  });

  it('시작하지 않으면 커서가 아무 걸음도 가리키지 않는다 — 진입 직후 자동 노출이 없다는 뜻이다', () => {
    // 「안 본 걸음이 있다」와 「지금 걷고 있다」는 다른 것이다 — 옛 배선은 이 둘을 붙여 놓아 진입 즉시 걸었다.
    expect(nextFlowStep()).toBe(0); // 안 본 걸음은 있다
    expect(renderTab('home')).not.toContain('data-tour-dot'); // 그런데 화면엔 안내가 없다
  });

  it('안내를 다시 보기로 지우면 첫 걸음부터 다시 걷는다 — 설정에서 돌아올 때 탭 셸이 새로 마운트된다', () => {
    COACHMARK_FLOW.forEach((step) => dismissCoachmark(step.name));
    expect(nextFlowStep()).toBe(-1); // 다 걸은 상태 — 이 전제가 없으면 아래 단언이 공허하다

    resetCoachmarks();

    expect(nextFlowStep()).toBe(0);
  });
});

/**
 * 가리킬 칸의 좌표 — 측정 0(ref·`getBoundingClientRect`·리사이즈 관측이 없다). 탭바가 좌우 같은
 * 여백의 `fixed`고 칸이 균등 분할이라 <b>칸 중앙이 순수 CSS 식</b>으로 떨어진다.
 */
describe('탭바 칸 지목', () => {
  it('가운데 액션이 끼어들어 탭의 슬롯이 TABS index와 어긋난다 — 그 어긋남을 계산이 흡수한다', () => {
    expect(tabSlot('home')).toBe(0);
    expect(tabSlot('library')).toBe(1);
    expect(tabSlot('bookshop')).toBe(3); // 액션이 2번 자리를 먹었다
    expect(tabSlot('history')).toBe(4);
  });

  it('칸 중앙은 좌우 여백을 뺀 폭의 균등 분할이다 — 다섯 칸 중 가운데가 화면 정중앙이 된다', () => {
    expect(slotCenter(TIMER_ACTION_SLOT)).toBe(
      `calc(${TAB_BAR_MARGIN}px + (100vw - ${TAB_BAR_MARGIN * 2}px) * 0.5)`,
    );
  });

  it('서재 걸음의 꼬리가 서재 칸을 가리킨다 — 말풍선만 그리고 엉뚱한 칸을 가리키는 사고를 막는다', () => {
    const bubble = COACHMARK_FLOW[1].bubble;
    if (bubble === undefined) throw new Error('서재 걸음엔 탭바 말풍선이 있어야 한다');

    const markup = renderToStaticMarkup(<TabBarCoachmark bubble={bubble} index={1} onNext={() => {}} />);

    expect(markup).toContain(slotCenter(tabSlot('library')));
  });
});

describe('탭 밖 오케스트레이션 (재편 전 동작 보존)', () => {
  beforeEach(() => {
    const store = new Map<string, string>();
    vi.stubGlobal('localStorage', {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => void store.set(k, String(v)),
      removeItem: (k: string) => void store.delete(k),
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 200, ok: true, text: async () => '{}' }));
  });

  const renderApp = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <App />
      </TDSMobileProvider>,
    );

  it('토큰이 없으면 로그인 브릿지의 인트로부터 — 탭바는 아직 없다', () => {
    const markup = renderApp();

    expect(markup).toContain('토스로 시작하기');
    expect(markup).not.toContain('서재');
  });

  it('토큰이 있으면 대시보드를 받는 동안 로딩 — 탭 화면은 데이터가 온 뒤', () => {
    localStorage.setItem('booktimer.token', 'tok');

    expect(renderApp()).toContain('불러오는 중');
  });
});

/**
 * 포커스 복귀 재조회 스로틀 — 웹·다른 기기에서 바꾼 값이 재진입 전까지 안 보이던 문제를 고치되,
 * 앱 전환이 잦은 미니앱에서 서버를 두들기지 않게 최소 간격을 둔다. 배선(visibilitychange)은
 * 하니스가 effect를 안 돌려 못 잡으므로 판정만 순수 함수로 계측한다.
 */
describe('포커스 복귀 재조회 (shouldRefresh)', () => {
  it('마지막 재조회에서 60초가 안 지났으면 건너뛴다', () => {
    expect(shouldRefresh(1_000, 1_000 + REFRESH_THROTTLE_MS - 1)).toBe(false);
  });

  it('60초가 지났으면 다시 받는다 — 경계에서도 받는다', () => {
    expect(shouldRefresh(1_000, 1_000 + REFRESH_THROTTLE_MS)).toBe(true);
    expect(shouldRefresh(1_000, 1_000 + REFRESH_THROTTLE_MS * 10)).toBe(true);
  });

  it('간격이 1분이다 — 값이 0이 되면 스로틀 자체가 사라진다', () => {
    expect(REFRESH_THROTTLE_MS).toBe(60_000);
  });

  it('force면 스로틀을 무시한다 — 서재에서 방금 바꾼 책이 홈에 1분 뒤에 뜨면 안 반영된 것과 같다', () => {
    expect(shouldRefresh(1_000, 1_000, true)).toBe(true);
    expect(shouldRefresh(1_000, 1_000 + REFRESH_THROTTLE_MS - 1, true)).toBe(true);
  });
});

/**
 * 작성 화면을 닫으면 어디로 가나 — 출발지로 돌아가야 한다.
 *
 * <p>예전엔 작성 직행(홈·서재의 「여백에 글쓰기」)도 여백 상세를 **밑에 깔고** 그 위에 섰다. 그래서
 * 쓰고 나오면 출발한 탭이 아니라 한 번도 요청한 적 없는 여백 상세에 떨어졌다. 이제 밑에 깔 화면이
 * 없으면(`bookId === null`) 곧장 탭으로 돌아간다. 배선은 effect·클릭이라 판정만 순수하게 계측한다.
 */
describe('작성 화면 닫기 (closeCompose)', () => {
  const book = { id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null, isPublic: true };

  it('작성 직행으로 열었으면(bookId=null) 전부 닫아 출발한 탭으로 돌아간다', () => {
    expect(closeCompose({ loginId: 'goospel', bookId: null, composeBook: book })).toBeNull();
  });

  it('여백 상세에서 열었으면 그 상세만 남긴다 — 왔던 목록으로 돌아가는 게 맞다', () => {
    expect(closeCompose({ loginId: 'goospel', bookId: 7, composeBook: book })).toEqual({
      loginId: 'goospel',
      bookId: 7,
      composeBook: null,
    });
  });
});

/**
 * 독서등 — 측정 중 홈이 밤이 된다(시안 C 「스탠드」). 캔버스가 어두운 나무색으로 내려앉고 히어로
 * 카드만 스탠드 밑에 펼쳐진 페이지로 남는다.
 *
 * <p>배선은 <b>`document.body`의 클래스 한 장</b>이고 색은 전부 css가 든다 — 그 토글은 effect라
 * 정적 렌더 하니스로 못 잡으므로, <b>판단</b>만 순수 함수로 꺼내 계측한다(효과 자체는 브라우저 실측).
 */
describe('독서등 (lampOn)', () => {
  it('홈에서 측정 중이면 켜진다', () => {
    expect(lampOn('home', true)).toBe(true);
  });

  it('홈이어도 측정 중이 아니면 안 켜진다', () => {
    expect(lampOn('home', false)).toBe(false);
  });

  /**
   * 적용 범위 「홈만」(사용자 결정 2026-08-19)을 못 박는 자리다. 서재·책방·기록까지 어두워지면
   * 표지·잔디·격자를 전부 밤 종이 위에서 다시 봐야 하는데, 그 색은 만든 적이 없다.
   */
  it('다른 탭에서는 측정 중이어도 안 켜진다 — 범위는 홈만이다', () => {
    expect(lampOn('library', true)).toBe(false);
    expect(lampOn('bookshop', true)).toBe(false);
    expect(lampOn('history', true)).toBe(false);
  });

  /**
   * 재진입이 공짜인 근거 — 켜짐 조건이 「지금 어느 탭인가 · 지금 측정 중인가」 둘뿐이라 상태를 안 든다.
   * 「방금 눌렀는지」를 기억하는 인자가 생기면 이 시그니처가 먼저 깨진다.
   */
  it('판단에 드는 것은 탭과 측정 여부뿐이다 — 나갔다 와도 같은 답이 나온다', () => {
    expect(lampOn.length).toBe(2);
  });

  it('js가 붙이는 클래스가 css에 셀렉터로 실재한다 — 한쪽만 고치면 기능이 조용히 죽는다', () => {
    expect(readFileSync(new URL('./global.css', import.meta.url), 'utf8')).toContain(`body.${LAMP_CLASS}`);
  });

  /**
   * 켤 때만 부드럽고 끌 때는 뚝 밝아지던 자리(사용자 보고 2026-08-22). 원인은 색이 아니라 <b>전환 규칙이
   * 사는 곳</b>이었다 — 셀렉터에 `.reading-lamp`가 들어가면 클래스가 걷히는 순간 `transition` 선언까지
   * 함께 사라져, 낮으로 되돌아가는 값이 보간 없이 즉시 점프한다(켤 때는 선언이 먼저 붙어 멀쩡하다).
   *
   * <p>그래서 전환은 <b>양쪽 상태가 다 보는 자리</b>(클래스 밖)에 산다. 육안으로는 「끌 때만」이라
   * 리뷰에서 절반이 정상으로 보이는 종류의 회귀라 css 문자열로 못 박는다.
   */
  it('전환 규칙은 lamp 클래스 밖에 산다 — 클래스와 함께 사라지면 끌 때만 뚝 밝아진다', () => {
    // 주석을 먼저 걷는다 — 셀렉터 앞 주석에는 중괄호가 없어 정규식이 통째로 빨아들인다(이 규칙을 설명하는 주석이 곧 거짓 음성이 된다).
    const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

    const rules = css.match(/[^{}]+lamp-page[^{}]*\{[^}]*transition[^}]*\}/g) ?? [];

    expect(rules.length).toBeGreaterThan(0); // 평시 + prefers-reduced-motion
    rules.forEach((rule) => expect(rule.split('{')[0]).not.toContain(LAMP_CLASS));
  });

  /**
   * 이 장치의 유일한 성립 조건 — 어두운 토큰을 `<main>`에만 얹으므로, 탭바가 그 <b>밖</b>에 서야
   * 측정을 끝내는 원이 어둠에 잠기지 않는다. 코치마크 스포트라이트가 층 순서로 성립하는 것과 같은
   * 수법이고(덮개가 탭바 아래), 여기선 <b>DOM 바깥</b>이 그 역할을 한다.
   *
   * <p>누군가 탭바를 `Screen` 안으로 옮기면 끄는 법을 잃는데, 색은 css에 있어 코드 리뷰로는 안 보인다.
   */
  it('탭바는 화면(<main>) 밖에 선다 — 어두운 토큰이 끄는 버튼에 닿지 않는다', () => {
    const markup = renderTab('home', { hasActiveSession: true });

    expect(markup.indexOf('<nav aria-label="메인 탭"')).toBeGreaterThan(markup.indexOf('</main>'));
  });
});

/**
 * 측정 중 탭 잠금 — 「읽는 도중엔 다른 화면으로 몸 가게」(사용자 지시 2026-08-19, 범위는 탭만).
 *
 * <p><b>홈은 안 잠그는 것이 이 설계의 요점이다.</b> 넷 다 잠그면 탈출구가 사라진다 — 코치마크 투어는
 * `flowTabChange` effect가 `onTabChange`를 <b>직접</b> 부르므로 이 잠금을 안 탄다. 투어가 사용자를 서재에
 * 데려다 놓은 상태에서 넷 다 잠겨 있으면 <b>측정을 끊기 전엔 홈으로 못 돌아온다</b>.
 */
describe('측정 중 탭 잠금 (tabLocked)', () => {
  it('측정 중이면 서재·책방·기록이 잠긴다', () => {
    expect(tabLocked('library', true)).toBe(true);
    expect(tabLocked('bookshop', true)).toBe(true);
    expect(tabLocked('history', true)).toBe(true);
  });

  it('홈은 측정 중에도 안 잠긴다 — 돌아올 길이 남아야 한다', () => {
    expect(tabLocked('home', true)).toBe(false);
  });

  it('측정 중이 아니면 아무것도 안 잠긴다', () => {
    for (const { key } of TABS) expect(tabLocked(key, false)).toBe(false);
  });
});

/**
 * 잠긴 탭의 마크업 — 잠김은 <b>보이고 읽혀야</b> 한다(안 눌리기만 하면 고장으로 읽힌다).
 *
 * <p>가운데 액션은 절대 잠기지 않는다 — 측정을 끝내는 유일한 버튼이라 잠기면 끄는 법을 잃는다.
 */
describe('하단 탭바 — 측정 중 잠금', () => {
  const locked = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BottomTabBar
          tab="home"
          onTabChange={() => {}}
          locked
          onBlocked={() => {}}
          action={{ active: true, busy: false, onPress: () => {} }}
        />
      </TDSMobileProvider>,
    );

  /** 버튼 한 칸만 — 알약 전체를 보면 다른 칸의 속성이 섞여 단언이 공허해진다. */
  const cell = (markup: string, marker: string) => {
    const at = markup.indexOf(marker);
    expect(at).toBeGreaterThan(-1);
    const from = markup.lastIndexOf('<button', at);
    return markup.slice(from, markup.indexOf('</button>', at));
  };

  it('서재·책방·기록 칸이 잠겼다고 말한다', () => {
    const markup = locked();

    for (const label of ['서재', '책방', '기록']) {
      expect(cell(markup, `title="${label}"`)).toContain('aria-disabled="true"');
    }
  });

  it('홈 칸과 가운데 액션은 잠기지 않는다', () => {
    const markup = locked();

    expect(cell(markup, 'title="홈"')).not.toContain('aria-disabled="true"');
    expect(cell(markup, 'aria-label="측정 끝내기"')).not.toContain('aria-disabled="true"');
  });

  it('측정 중이 아니면 아무 칸도 잠기지 않는다 — 위 부정 단언의 짝', () => {
    const open = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BottomTabBar
          tab="home"
          onTabChange={() => {}}
          action={{ active: false, busy: false, onPress: () => {} }}
        />
      </TDSMobileProvider>,
    );

    expect(open).not.toContain('aria-disabled="true"');
  });
});

/**
 * 여백을 여는 문은 하나다 — 측정 중이면 먼저 끝내야 하므로(「여백은 독서가 아니다」,
 * 사용자 결정 2026-08-22), 진입점 하나가 게이트를 우회하면 규칙이 <b>조용히</b> 샘다.
 * 경로가 셋(홈 여백 문·소식 피드·남의 책방)이라 사람 눈으로는 하나 빠진 것을 못 잡는다.
 *
 * <p>계측은 소스 문자열이다 — 여는 것은 `setMargin({…})`(객체 리터럴)이고 닫는 것은
 * `setMargin(null)`·`setMargin(closeCompose(…))`라, 정규식이 <b>여는 자리만</b> 갈라낸다. jsdom이 없어
 * 클릭을 못 돌리는 하니스에서 이 규칙을 지킬 수 있는 유일한 자리다.
 */
describe('여백 진입 게이트', () => {
  it('여백을 여는 자리는 openMargin 하나뿐이다 — 새 진입점이 게이트를 안 타면 측정이 여백에서 계속 돌다', () => {
    // 주석을 먼저 걷는다 — 규칙을 설명하는 주석에 그 호출 모양이 예시로 적힐 수밖에 없어, 안 걷으면
    // 설명이 곧 거짓 음성이 된다(같은 함정을 css 전환 규칙 계측에서 한 번 밟았다).
    const src = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*/g, '');

    const opens = [...src.matchAll(/setMargin\(\{/g)];

    expect(opens).toHaveLength(1);
    expect(opens[0].index).toBeGreaterThan(src.indexOf('const openMargin'));
  });
});

/**
 * 여백 위의 탭바 — 여백은 탭 밖 전체 화면이라 탭바가 함께 사라졌고, 나갈 길이 뒤로가기 하나뿐이었다.
 *
 * <p>여백에선 측정이 <b>항상 꺼져 있다</b>(여백 진입 게이트가 먼저 끝낸다) — 그래서 원은 언제나
 * 「시작」이고 잠긴 칸도 없다. 그 두 가정이 깨지면 여백에서 측정을 「끝내기」로 불러야 하는 모순이 된다.
 */
describe('여백 위의 탭바 (MarginShell)', () => {
  const markup = renderToStaticMarkup(
    <MarginShell tab="home" onGo={() => {}} onStart={() => {}}>
      <p>여백 본문</p>
    </MarginShell>,
  );

  it('본문 위에 탭바가 함께 선다', () => {
    expect(markup).toContain('여백 본문');
    expect(markup).toContain('aria-label="메인 탭"');
  });

  it('원은 언제나 「시작」이다 — 여백에선 측정이 돌지 않는다', () => {
    expect(markup).toContain('aria-label="측정 시작"');
    expect(markup).not.toContain('aria-label="측정 끝내기"');
  });

  it('잠긴 칸이 없다 — 어느 탭으로든 곷장 나간다', () => {
    expect(markup).not.toContain('aria-disabled');
  });

  it('본문 끝이 떠 있는 알약에 가려지 않게 그만큼 비운다', () => {
    expect(markup).toContain(`padding-bottom:calc(${TAB_BAR_HEIGHT}px`);
  });
});
