import { TDSMobileProvider } from '@toss/tds-mobile';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import type { DashboardResponse, MarginResponse, MonthlySection, SocialEvent } from './api';
import { IDLE_STUDY } from './api';
import { History, MonthlyRecords } from './screens/History';
import { Home, ReadingNowCard } from './screens/Home';
import { FeedBox } from './screens/HomeFeed';
import { MarginBoxView } from './screens/Library';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

/**
 * 섹션 머리 아래 실선 — 핸드오프 「타이포 스케일」이 섹션 제목에 딸려 지정한 선이다
 * (`padding-bottom: 9` + `border-bottom: 1px solid rgba(44,42,36,.12)`).
 *
 * <p>③(전면 재테마)에서 <b>크기만 옮겨 왔다</b> — 16.5→17은 설계 md의 계단표에 실렸는데 같은 줄에 있던
 * 이 선은 한 글자도 안 넘어갔고, 그래서 구현도 ABANDON 기록도 없이 사라졌다(T-213). 원장(§7)이 못 잡은
 * 이유는 원장이 「해봐야 아는 것」을 담기 때문이다 — <b>명세에서 통째로 빠뜨린 것</b>은 그 그물에 안 걸린다.
 *
 * <p><b>시안(턴2 `#t2`)이 선을 그은 자리는 넷</b>이다: 홈 캐러셀 머리(2a) · 피드 탭 머리(2b) ·
 * 서재 여백 헤더(2c) · 기록 월 헤더(2d). 홈 「읽는 중」은 <b>시안에 없고</b> 이쪽 판단으로 더한 것이다 —
 * `Home`이 삼항으로 캐러셀↔`ReadingNowCard`를 <b>같은 슬롯에서</b> 갈아끼우므로, 한쪽만 선이 있으면
 * 측정을 시작하는 순간 카드 생김새가 변한다. 「읽은 날짜」는 시안도 민무늬라 그대로 뒀다.
 *
 * <p>선은 <b>제목이 아니라 줄</b>에 걸린다. 여백 헤더·월 헤더·탭 머리는 제목 옆에 카운트·손잡이·합계가
 * 서므로, 제목({@link SectionTitle})에 걸면 선이 줄 한가운데서 끊긴다. 홈 두 자리는 제목이 곧 줄이라
 * 반대로 제목에 건다. 그래서 공용 컴포넌트가 아니라 자리마다 준다.
 *
 * <p>⚠️ 계측이 <b>개수만 세면 안 된다</b> — 독립 리뷰가 실제로 그 구멍을 뚫었다. 여백 헤더의 선을 줄에서
 * `SectionTitle`로 옮기자 선이 `flex: 1`인 제목에서 끝나 「전체 보기 ›」가 선 밖으로 밀렸는데(브라우저
 * 실측 321px → 259px) <b>개수는 그대로 1이라 전 스위트가 초록이었다.</b> 이 파일이 막겠다고 적어 둔
 * 바로 그 그림이다. 그래서 아래는 선을 <b>인 태그를 꺼내</b> 그 정체까지 본다.
 */

function render(node: ReactNode): string {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

beforeEach(() => {
  stubLocalStorage(); // 홈은 렌더 중에 알림 동의 캐시를 읽는다(`home.test.tsx`와 같은 처방)
});

/**
 * 실선을 인 여는 태그들.
 *
 * <p>`border-bottom` + grey200으로 함께 좁히는 이유는 <b>둘 다 필요해서</b>다. 같은 화면에 이미 다른
 * 실선이 둘 있다 — 기록 목록의 행 구분선(`border-bottom` + <b>grey100</b>)과 월 목록 맨 위의
 * 시작선(<b>`border-top`</b> + grey200). 한쪽 조건만 쓰면 그 둘이 섞여 들어와 개수가 거짓이 된다.
 * 피드의 외곽선 배지는 축약 `border:`라 `border-bottom:`이라는 부분 문자열 자체가 없어 안 걸린다.
 */
const ruleTags = (markup: string) => [
  ...markup.matchAll(/<[a-z]+[^>]*border-bottom:1px solid var\(--adaptiveGrey200[^>]*>/g),
].map((m) => m[0]);

/** 그 자리의 선 하나를 꺼낸다 — 없거나 둘이면 그 자리에서 죽는다(0개가 조용히 통과하지 않게). */
function theRule(markup: string): string {
  const tags = ruleTags(markup);
  expect(tags).toHaveLength(1);
  return tags[0];
}

const event = (): SocialEvent => ({
  nickname: '나비독서',
  loginId: 'nabi',
  bookId: 7,
  bookTitle: '데미안',
  coverUrl: null,
  type: 'FINISHED',
  occurredAt: new Date().toISOString(),
  excerpt: null,
  count: 1,
});

/** ⚠️ 글이 <b>있어야</b> 한다 — 0장이면 여백 헤더가 카운트·「전체 보기」와 함께 아랫선까지 접는다. */
const margin = (): MarginResponse => ({
  book: { id: 1, title: '데미안', author: '헤세', coverUrl: null, isPublic: true },
  ownerNickname: '구스펠',
  self: true,
  entries: [
    { id: 1, text: '첫 문장', quote: null, bgCode: 'paper', createdAt: '2026-08-24T00:00:00Z', likeCount: 0, liked: false },
  ],
});

const month = (): MonthlySection => ({ month: '2026-08', totalSeconds: 7_200, days: [] });

const dashboard = (): DashboardResponse => ({
  nickname: '구스펠',
  loginId: 'goospel',
  previousLoginId: null,
  profileCharacterCode: null,
  remainingSeconds: 900,
  carriedDebtSeconds: 1_800,
  todayGoalSeconds: 3_600,
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
});

/**
 * 홈 카드 한 장을 잘라 낸다 — `home.test.tsx`의 `card()`와 같은 수법이다(카드는 서로 안 중첩된다).
 * 홈 한 화면에 선이 둘(캐러셀 머리 · 피드 탭 머리)이라, 자리를 가르려면 카드로 좁혀야 한다.
 */
function card(markup: string, header: string): string {
  const open = markup.lastIndexOf('<section', markup.indexOf(header));
  return markup.slice(open, markup.indexOf('</section>', open));
}

/** `SectionTitle`이 남기는 지문 — 선이 제목에 걸렸는지 「감싼 무엇」에 걸렸는지를 가른다. */
const TITLE_MARK = '--tds-t-st10-text-fontSize';

describe('제목이 곧 줄인 자리 — 선은 제목에 건다', () => {
  it('홈 캐러셀 머리 (시안 2a)', () => {
    const markup = render(<Home
      dashboard={dashboard()}
      mode="reading"
      study={IDLE_STUDY}
      onChangeMode={() => {}}
      onBlockedModeChange={() => {}}
      selectedBookId={null}
      onSelectBook={() => {}}
      onTimerChange={() => {}}
      celebrate={false}
      onGoGoal={() => {}}
      goalAdPending={false}
      onGoSettings={() => {}}
      onError={() => {}}
      onOpenMargin={() => {}}
      onComposeMargin={() => {}}
    />);
    const tag = theRule(card(markup, '무엇으로 측정할까요?'));

    expect(tag).toContain(TITLE_MARK);
    expect(tag).toContain('padding-bottom:9px');
  });

  /** 시안에 없는 자리다 — 캐러셀과 <b>같은 슬롯</b>이라 한쪽만 그으면 측정 시작에 카드가 변한다. */
  it('홈 「읽는 중」 — 캐러셀과 같은 슬롯이므로 같은 대접을 받는다', () => {
    const tag = theRule(render(<ReadingNowCard book={null} totalSeconds={0} />));

    expect(tag).toContain(TITLE_MARK);
    expect(tag).toContain('padding-bottom:9px');
  });
});

describe('제목 옆에 무언가 서는 자리 — 선은 줄에 건다', () => {
  it('피드 탭 머리 (시안 2b) — 사람·소식·책 뉴스가 한 줄이다', () => {
    const markup = render(
      <FeedBox
        feed={{ social: [event()], newsEnabled: false, news: [], readers: [] }}
        tab="social"
        expanded={false}
        error={null}
        now={Date.parse('2026-08-24T12:00:00Z')}
        onTab={() => {}}
        onToggle={() => {}}
        onOpenNews={() => {}}
        onOpenMargin={() => {}}
      />,
    );
    const tag = theRule(markup);

    expect(markup).toContain('소식');
    expect(tag).toContain('display:flex');
    expect(tag).not.toContain(TITLE_MARK);
    expect(tag).toContain('padding-bottom:11px');
  });

  it('서재 여백 헤더 (시안 2c) — 제목에 걸면 「전체 보기 ›」가 선 밖으로 밀린다', () => {
    const markup = render(
      <MarginBoxView view={margin()} now={Date.parse('2026-08-24T12:00:00Z')} onOpenAll={() => {}} />,
    );
    const tag = theRule(markup);

    expect(markup).toContain('전체 보기');
    expect(tag).toContain('display:flex');
    expect(tag).not.toContain(TITLE_MARK);
    expect(tag).toContain('padding-bottom:9px');
  });

  it('기록 월 헤더 (시안 2d) — 달 이름과 그 달 합계가 한 줄이다', () => {
    const markup = render(<MonthlyRecords months={[month()]} />);
    const tag = theRule(markup);

    expect(markup).toContain('2026년 8월');
    expect(tag).toContain('display:flex');
    expect(tag).toContain('padding-bottom:10px');
  });
});

describe('선을 긋지 않는 자리', () => {
  /**
   * 「읽은 날짜」는 시안에서도 민무늬다(세리프 21, 선 없음). 카드 밖 화면 제목이라 아래에 이끄는
   * 목록이 곧바로 붙지 않는다 — 여기에 선을 그으면 잔디와 목록이 한 덩어리로 묶인다.
   *
   * <p>부정 단언이라 <b>먼저 「그 글자를 찾았다」를 못 박는다</b> — 못 찾으면 `toHaveLength(0)`은 그냥
   * 통과해 버린다(T-149·T-212).
   */
  it('기록 화면 제목 「읽은 날짜」에는 안 긋는다 — 시안도 민무늬다', () => {
    const markup = render(<History graph={graph} />);

    expect(markup).toContain('읽은 날짜');
    expect(ruleTags(markup)).toHaveLength(0);
  });
});
