import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { App, BottomTabBar, MainTabs, TAB_BAR_HEIGHT, TABS, tabChangeHandler } from './App';
import type { DashboardResponse } from './api';
import { graph, userAgent } from './test-fixtures';

/**
 * 탭 재편 안전망 — 탭 값과 화면의 대응, 그리고 재편 전부터 있던 오케스트레이션(토큰 유무 → 로그인/로딩)을
 * 못 박는다. 하니스가 `renderToStaticMarkup`이라 클릭은 못 잡으므로, 탭바와 본문을 같은 `TABS` 목록에서
 * 그리게 하고 "선택된 탭 = 그려진 화면"을 단언해 index↔화면 어긋남을 잡는다.
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
  graph,
  quotes: [],
  emailVerified: true,
};

function renderTab(tab: (typeof TABS)[number]['key']) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <MainTabs
        tab={tab}
        onTabChange={() => {}}
        dashboard={dashboard}
        onTimerChange={() => {}}
        onGraphChange={() => {}}
        onGoGoal={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

describe('탭 구조', () => {
  it('탭마다 그 탭의 화면을 그린다 — 탭 선택 ↔ 화면 대응', () => {
    expect(renderTab('home')).toContain('구스펠님의 오늘');
    expect(renderTab('library')).toContain('내 서재');
    expect(renderTab('social')).toContain('책방 둘러보기');
    expect(renderTab('history')).toContain('내 기록');
  });

  it('다른 탭의 화면은 함께 그리지 않는다 — 한 번에 한 화면', () => {
    expect(renderTab('home')).not.toContain('내 기록');
    expect(renderTab('library')).not.toContain('구스펠님의 오늘');
    expect(renderTab('home')).not.toContain('책방 둘러보기');
  });

  it('탭바에서 선택 표시되는 항목은 지금 그려진 탭 자신이다 — index 어긋남 방지', () => {
    expect(TABS.map((t) => t.label)).toEqual(['홈', '서재', '소셜', '기록']);

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

    expect(picked).toEqual(['home', 'library', 'social', 'history']);
  });

  it('소셜 탭은 서재와 기록 사이에 온다 — 탭바 순서가 곧 TABS 순서다', () => {
    expect(TABS.map((t) => t.key)).toEqual(['home', 'library', 'social', 'history']);
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

  it('홈 인디케이터만큼 아래 여백을 둔다 — safe-area가 없으면 마지막 탭이 인디케이터에 깔린다', () => {
    expect(bar('home')).toContain('padding-bottom:env(safe-area-inset-bottom)');
  });

  it('본문 아래 여백이 탭바 높이 + safe-area를 덮는다 — 마지막 요소(격언·목표 바꾸기)가 바에 가리지 않는다', () => {
    expect(renderTab('home')).toContain(`padding-bottom:calc(${TAB_BAR_HEIGHT}px + env(safe-area-inset-bottom)`);
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

  it('토큰이 없으면 로그인 브릿지부터 — 탭바는 아직 없다', () => {
    const markup = renderApp();

    expect(markup).toContain('토스로 로그인하는 중');
    expect(markup).not.toContain('서재');
  });

  it('토큰이 있으면 대시보드를 받는 동안 로딩 — 탭 화면은 데이터가 온 뒤', () => {
    localStorage.setItem('booktimer.token', 'tok');

    expect(renderApp()).toContain('불러오는 중');
  });
});
