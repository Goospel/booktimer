import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { MarginResponse, MonthlySection, SocialEvent } from './api';
import { History, MonthlyRecords } from './screens/History';
import { ReadingNowCard } from './screens/Home';
import { FeedBox } from './screens/HomeFeed';
import { MarginBoxView } from './screens/Library';
import { graph, userAgent } from './test-fixtures';

/**
 * 섹션 머리 아래 실선 — 핸드오프 「타이포 스케일」이 섹션 제목에 딸려 지정한 선이다
 * (`padding-bottom: 9` + `border-bottom: 1px solid rgba(44,42,36,.12)`).
 *
 * <p>③(전면 재테마)에서 <b>크기만 옮겨 왔다</b> — 16.5→17은 설계 md의 계단표에 실렸는데 같은 줄에 있던
 * 이 선은 한 글자도 안 넘어갔고, 그래서 구현도 ABANDON 기록도 없이 사라졌다. 원장(§7)이 못 잡은 이유는
 * 원장이 「해봐야 아는 것」을 담기 때문이다 — <b>명세에서 통째로 빠뜨린 것</b>은 그 그물에 안 걸린다.
 *
 * <p>선은 <b>제목이 아니라 줄</b>에 걸린다. 여백 헤더·월 헤더·탭 머리는 제목 옆에 카운트·손잡이·합계가
 * 서므로, 제목({@link SectionTitle})에 걸면 선이 줄 한가운데서 끊긴다. 그래서 공용 컴포넌트가 아니라
 * 자리마다 준다 — 시안이 그은 자리가 정확히 이 다섯이고, 「읽은 날짜」는 시안에서도 민무늬다.
 *
 * <p>⚠️ 계측은 <b>실제 렌더</b>로 한다. 소스에 상수가 있다는 사실은 그것이 <b>어느 요소에</b> 걸렸는지
 * 말해 주지 않는다(T-211 — 이번 누락과 같은 뿌리다). 단언마다 머리 글자를 먼저 찾아 「계측기가 살아
 * 있다」를 못 박고(T-212 — 선택자가 죽어도 초록인 사고), 그 컴포넌트 안에서 선을 <b>세어</b> 확인한다.
 */

function render(node: ReactNode): string {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

/**
 * 렌더 결과에 실선이 몇 번 나오나.
 *
 * <p>`border-bottom` + grey200으로 함께 잡는 이유는 <b>둘 다 필요해서</b>다. 같은 화면에 이미 다른
 * 실선이 둘 있다 — 기록 목록의 행 구분선(`border-bottom` + <b>grey100</b>)과 월 목록 맨 위의
 * 시작선(<b>`border-top`</b> + grey200). 한쪽 조건만 쓰면 그 둘이 섞여 들어와 개수가 거짓이 된다.
 */
const ruleCount = (markup: string) =>
  markup.split('border-bottom:1px solid var(--adaptiveGrey200').length - 1;

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

const margin = (): MarginResponse => ({
  book: { id: 1, title: '데미안', author: '헤세', coverUrl: null, isPublic: true },
  ownerNickname: '구스펠',
  self: true,
  entries: [],
});

const month = (): MonthlySection => ({ month: '2026-08', totalSeconds: 7_200, days: [] });

describe('섹션 머리 아래 실선 — 시안이 그은 다섯 자리', () => {
  it('홈 「읽는 중」 머리 — 측정 중일 때의 카드', () => {
    const markup = render(<ReadingNowCard book={null} totalSeconds={0} />);

    expect(markup).toContain('읽는 중');
    expect(ruleCount(markup)).toBe(1);
  });

  it('피드 탭 머리 — 사람·소식·책 뉴스가 서는 줄', () => {
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

    expect(markup).toContain('소식');
    expect(ruleCount(markup)).toBe(1);
  });

  it('서재 여백 헤더 — 제목 옆에 「전체 보기 ›」가 서므로 선은 줄 전체를 지난다', () => {
    const markup = render(
      <MarginBoxView view={margin()} now={Date.parse('2026-08-24T12:00:00Z')} onOpenAll={() => {}} />,
    );

    expect(markup).toContain('여백');
    expect(ruleCount(markup)).toBe(1);
  });

  it('기록 월 헤더 — 달 이름과 그 달 합계가 한 줄이다', () => {
    const markup = render(<MonthlyRecords months={[month()]} />);

    expect(markup).toContain('2026년 8월');
    expect(ruleCount(markup)).toBe(1);
  });

  /**
   * 홈 캐러셀 머리(「무엇으로 측정할까요?」)는 `Home`의 본문에 인라인이라 떼어 렌더할 수 없다.
   * 대신 <b>그 줄이 실제로 상수를 쓰는지</b>를 소스에서 확인한다 — 위 넷보다 약한 계측임을 알고 쓴다.
   * 주석은 걷어낸다(T-205 — 주석이 상수를 인용하면 단언이 공허하게 통과한다).
   */
  it('홈 캐러셀 머리도 같은 상수를 쓴다 — 측정 중/대기 두 상태가 같은 자리에서 갈리므로', () => {
    const src = readFileSync(new URL('./screens/Home.tsx', import.meta.url), 'utf8')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*/g, '');

    const at = src.indexOf('무엇으로 측정할까요?');
    expect(at).toBeGreaterThan(-1);
    expect(src.slice(Math.max(0, at - 400), at)).toContain('SECTION_RULE');
  });
});

describe('선을 긋지 않는 자리', () => {
  /**
   * 「읽은 날짜」는 시안에서도 민무늬다(세리프 21, 선 없음). 카드 밖 화면 제목이라 아래에 이끄는
   * 목록이 곧바로 붙지 않는다 — 여기에 선을 그으면 잔디와 목록이 한 덩어리로 묶인다.
   *
   * <p>부정 단언이라 <b>먼저 「그 글자를 찾았다」를 못 박는다</b> — 못 찾으면 `toBe(0)`은 그냥
   * 통과해 버린다(T-149·T-212).
   */
  it('기록 화면 제목 「읽은 날짜」에는 안 긋는다 — 시안도 민무늬다', () => {
    const markup = render(<History graph={graph} />);

    expect(markup).toContain('읽은 날짜');
    expect(ruleCount(markup)).toBe(0);
  });
});
