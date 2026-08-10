import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { DashboardResponse } from './api';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { graph, userAgent } from './test-fixtures';

/**
 * 잔디 렌더 안전망 — 잔디 그리기를 History에서 `GrassGrid`(ui.tsx)로 추출하는 리팩터가
 * 같은 데이터에 대해 같은 마크업을 내는지 못 박는다(동작 보존 리팩터라 추출 전후 모두 통과해야 한다).
 */
describe('잔디 렌더', () => {
  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <History graph={graph} />
    </TDSMobileProvider>,
  );

  it('칸마다 level 색을 칠한다 — 0~4 단계가 웹 잔디와 같은 팔레트', () => {
    for (const color of ['#ebedf0', '#8fd694', '#2e7d32', '#c6e6c8', '#4caf50']) {
      expect(markup).toContain(`background:${color}`);
    }
  });

  it('날짜 없는 칸은 그리드 가장자리 placeholder라 투명하게 둔다', () => {
    expect(markup).toContain('background:transparent');
  });

  it('수동 기록 칸은 테두리로 구분한다', () => {
    expect(markup).toContain('outline:1px solid #9e9e9e');
  });

  it('주 × 일 수만큼 칸을 그린다', () => {
    expect(markup.match(/border-radius:2px/g)).toHaveLength(6);
  });
});

/** 홈은 시각 화면이라 단위테스트를 두지 않지만, 목표 0(0으로 나누기) 경계만은 계측한다. */
function home(overrides: Partial<DashboardResponse>) {
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
    ...overrides,
  };
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard}
        onTimerChange={() => {}}
        onGraphChange={() => {}}
        onGoHistory={() => {}}
        onGoGoal={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

describe('홈 오늘 진행률', () => {
  it('목표의 남은 시간만큼을 진행률로 환산한다', () => {
    expect(home({ todayGoalSeconds: 3600, remainingSeconds: 900 })).toMatch(/중 75%/);
  });

  it('목표를 초과해도 100%를 넘기지 않는다 — 남은 시간이 음수인 날', () => {
    expect(home({ todayGoalSeconds: 3600, remainingSeconds: -600 })).toMatch(/중 100%/);
  });

  it('목표가 0이면 게이지를 그리지 않는다 — 0으로 나누면 NaN·Infinity가 새어나온다', () => {
    const markup = home({ todayGoalSeconds: 0, remainingSeconds: 0 });

    expect(markup).not.toMatch(/중 \d+%/); // 게이지 라벨("오늘 목표 … 중 n%")이 아예 없어야 한다
    expect(markup).not.toMatch(/NaN|Infinity/);
  });

  it('격언이 없으면 카드를 띄우지 않는다 — 빈 인용부호만 남는 걸 막는다', () => {
    expect(home({ quotes: [] })).not.toContain('“');
    expect(home({ quotes: [{ text: '책은 도끼다', author: '카프카' }] })).toContain('카프카');
  });
});
