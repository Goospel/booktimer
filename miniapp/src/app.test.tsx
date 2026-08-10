import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { App, MainTabs, TABS, tabChangeHandler } from './App';
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
    expect(renderTab('history')).toContain('내 기록');
  });

  it('다른 탭의 화면은 함께 그리지 않는다 — 한 번에 한 화면', () => {
    expect(renderTab('home')).not.toContain('내 기록');
    expect(renderTab('library')).not.toContain('구스펠님의 오늘');
  });

  it('탭바에서 선택 표시되는 항목은 지금 그려진 탭 자신이다 — index 어긋남 방지', () => {
    expect(TABS.map((t) => t.label)).toEqual(['홈', '서재', '기록']);

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

    expect(picked).toEqual(['home', 'library', 'history']);
  });

  it('소셜 탭은 아직 없다 — 화면 없는 빈 탭을 내보내지 않는다(PR-6)', () => {
    expect(TABS.map((t) => t.key)).not.toContain('social');
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
