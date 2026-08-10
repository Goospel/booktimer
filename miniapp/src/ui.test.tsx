import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { DashboardResponse } from './api';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { coverSource } from './ui';
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

  it('가로 스크롤 컨테이너가 스크롤바 숨김 클래스를 쓴다 — 규칙만 있고 안 붙이면 아무 일도 안 난다', () => {
    expect(markup).toContain('class="no-scrollbar"');
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

  it('게이지 라벨과 값을 각자 다른 블록에 둔다 — "오늘 남은 시간15분"으로 붙어 보였다', () => {
    const markup = home({ remainingSeconds: 900 });
    const between = markup.slice(markup.indexOf('오늘 남은 시간') + 8, markup.indexOf('15분'));

    expect(between).toContain('</div>');
  });
});

/**
 * 라이트 캔버스 고정 — 앱이 배경을 안 칠하면 다크 모드 WebView에서 캔버스가 검정이 되는데 본문 글자는
 * 어두운 색 그대로라 제목·섹션 헤더가 안 보인다. v2는 라이트 고정이라(다크 대응은 범위 밖) 강제한다.
 */
describe('배경·color-scheme', () => {
  const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

  it('color-scheme을 라이트로 못 박는다 — 브라우저가 캔버스를 어둡게 뒤집지 못하게', () => {
    expect(read('../index.html')).toContain('name="color-scheme" content="light"');
  });

  it('html·body에 흰 배경을 칠한다 — 투명이면 기기 다크 캔버스가 그대로 비친다', () => {
    expect(read('./global.css')).toMatch(/html,\s*body\s*\{[^}]*background:\s*#fff/);
  });

  it('TDS Text를 블록으로 되돌린다 — TDS가 호출부의 display:block을 inline-block으로 덮어써 줄이 붙는다', () => {
    expect(read('./global.css')).toMatch(/Paragraph\.Text[^}]*display:\s*block\s*!important/);
  });

  it('가로 스크롤 영역의 스크롤바를 숨긴다 — 파이어폭스·웹킷 양쪽 다', () => {
    const css = read('./global.css');

    expect(css).toMatch(/\.no-scrollbar[^}]*scrollbar-width:\s*none/);
    expect(css).toMatch(/\.no-scrollbar::-webkit-scrollbar[^}]*display:\s*none/);
  });

});

/**
 * 표지 로드 실패 — `coverUrl`이 있어도 원격 이미지(알라딘 등)는 실패할 수 있고, 그러면 브라우저의
 * 깨진 이미지 아이콘이 그대로 노출된다. 실패는 "표지가 없는 것"과 같이 취급한다.
 *
 * <p>정적 렌더 하니스로는 `onError`가 돌지 않으므로 판단만 순수 함수로 꺼내 계측한다 —
 * `nextStoryIndex`·`tabChangeHandler`와 같은 방식.
 */
describe('표지 출처 결정', () => {
  it('표지가 없으면 자리 채움', () => {
    expect(coverSource(null, null)).toBeNull();
  });

  it('멀쩡한 표지는 그대로 그린다', () => {
    expect(coverSource('https://img/a.jpg', null)).toBe('https://img/a.jpg');
  });

  it('로드에 실패한 표지는 없는 것으로 친다 — 깨진 이미지 아이콘 대신 자리 채움', () => {
    expect(coverSource('https://img/a.jpg', 'https://img/a.jpg')).toBeNull();
  });

  it('다른 책이 실패한 것은 이 책에 옮지 않는다 — 목록 재사용으로 실패가 번지면 멀쩡한 표지가 사라진다', () => {
    expect(coverSource('https://img/a.jpg', 'https://img/b.jpg')).toBe('https://img/a.jpg');
  });
});
