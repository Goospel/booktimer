import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  App,
  BottomTabBar,
  MainTabs,
  REFRESH_THROTTLE_MS,
  TAB_BAR_HEIGHT,
  TAB_BAR_MARGIN,
  TABS,
  shouldRefresh,
  tabChangeHandler,
} from './App';
import type { DashboardResponse } from './api';
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

function renderTab(tab: (typeof TABS)[number]['key']) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <MainTabs
        tab={tab}
        onTabChange={() => {}}
        dashboard={dashboard}
        homeBookId={undefined}
        onSelectHomeBook={() => {}}
        onOpenMargin={() => {}}
        onComposeMargin={() => {}}
        onTimerChange={() => {}}
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
      // TDS Tab.Item은 li에 aria-selected와 라벨 title을 함께 싣는다 — 둘의 짝을 본다.
      expect(markup.match(/aria-selected="true"/g)).toHaveLength(1);
      expect(markup).toContain(`aria-selected="true" title="${label}"`);
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
  const bar = (tab: (typeof TABS)[number]['key']) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <BottomTabBar tab={tab} onTabChange={() => {}} />
      </TDSMobileProvider>,
    );

  it('탭마다 아이콘과 라벨을 함께 그린다 — 라벨만 있는 밋밋한 바가 이질감의 주범이었다', () => {
    const markup = bar('home');

    expect(markup.match(/<svg/g)).toHaveLength(TABS.length);
    for (const { label } of TABS) expect(markup).toContain(label);
  });

  it('탭 하나의 터치 영역이 손가락 최소치(44px)를 넘는다', () => {
    const heights = bar('home').match(/min-height:(\d+)px/g) ?? [];

    expect(heights).toHaveLength(TABS.length);
    for (const h of heights) expect(Number(h.match(/\d+/)![0])).toBeGreaterThanOrEqual(44);
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
