import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { SocialEvent } from './api';
import { FeedBox, quotedParts } from './screens/HomeFeed';
import { userAgent } from './test-fixtures';

/**
 * 「또렷한 연필」 B — 홈 히어로 + 소식 피드의 시안 반영(2a·2b).
 *
 * <p>A(토대)가 서체 축과 계단을 깔았고, 여기서는 <b>그 위에 위계를 그린다</b>: 값은 세리프로,
 * 오버라인은 자간으로, 배지는 톤으로. 순수 시각 변경이 대부분이라 <b>단위로 잴 수 있는 것만</b>
 * 잰다 — 나머지(간격·색감·균형)는 목 모드에서 시안과 나란히 놓고 보는 것이 게이트다(설계 §6).
 *
 * <p>⚠️ 실기기 게이트는 또 다르다: 고운돋움이 400 단일 웨이트라 화면의 700은 전부 합성 볼드이고
 * iOS WebView가 그걸 데스크톱보다 굵게 그린다(원장 U-1). 여기 단언은 「무엇이 700인가」를 잠글 뿐
 * 「그게 어떻게 보이는가」는 폰에서만 안다.
 */

function render(node: React.ReactNode): string {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

const event = (extra: Partial<SocialEvent> = {}): SocialEvent => ({
  nickname: '나비독서',
  loginId: 'nabi',
  bookId: 7,
  bookTitle: '데미안',
  coverUrl: null,
  type: 'FINISHED',
  occurredAt: new Date().toISOString(),
  excerpt: null,
  count: 1,
  ...extra,
});

/**
 * 피드 문장에서 『책 제목』만 떼어낸다 — 그 조각에만 세리프를 입히기 위해서다.
 *
 * <p>문장은 `eventLine`이 한 덩어리 문자열로 만든다(조사 처리가 그 안에 있다). 그걸 그대로 두고
 * <b>렌더 직전에</b> 쪼개는 쪽이, 문장 생성 함수를 조각 배열로 바꾸는 것보다 훨씬 싸다 —
 * 조사 로직과 그 테스트가 한 줄도 안 움직인다.
 */
describe('제목 조각 분리 (quotedParts)', () => {
  it('『』로 감싼 부분만 따로 떼어낸다', () => {
    expect(quotedParts('나비독서님이 『데미안』을 완독했어요')).toEqual([
      { text: '나비독서님이 ', quoted: false },
      { text: '『데미안』', quoted: true },
      { text: '을 완독했어요', quoted: false },
    ]);
  });

  it('제목이 여럿이어도 각각 떼어낸다', () => {
    expect(
      quotedParts('『A』와 『B』')
        .filter((p) => p.quoted)
        .map((p) => p.text),
    ).toEqual(['『A』', '『B』']);
  });

  it('제목이 없으면 통째로 한 조각이다 — 『』이 없는 문장도 그대로 지난다', () => {
    expect(quotedParts('오늘의 책 소식')).toEqual([{ text: '오늘의 책 소식', quoted: false }]);
  });

  it('빈 문장도 안 깨진다', () => {
    expect(quotedParts('')).toEqual([]);
  });

  /**
   * ⚠️ 제목 안에 『』이 들어 있는 책이 실제로 있다(예: 『『책』을 읽는 법』). 욕심내지 않고
   * <b>가장 짧게</b> 끊는다 — 잘못 끊어도 문장은 그대로 읽히지만, 탐욕적으로 끊으면 문장 절반이
   * 세리프가 된다. 그쪽이 훨씬 눈에 띄는 고장이다.
   */
  it('중첩 『』은 가장 짧게 끊는다 — 탐욕적으로 끊으면 문장 절반이 세리프가 된다', () => {
    const quoted = quotedParts('『『책』을 읽는 법』 완독').filter((p) => p.quoted);

    expect(quoted[0].text).toBe('『『책』');
  });

  /**
   * ⚠️ `.`은 개행에 안 붙으므로 문자클래스를 넓혀 둔다. 서버 `Book.title`은 `strip()`만 해서
   * <b>양끝만 걷고 안쪽 개행은 통과</b>시킨다 — 즉 이 입력이 도메인에서 막혀 있지 않다.
   * 좁게 두면 그 제목만 조용히 세리프를 잃는데, HTML에선 개행이 공백으로 렌더돼
   * <b>원인이 화면에 보이지도 않는다</b>.
   */
  it('제목 안에 개행이 있어도 떼어낸다 — 서버가 안쪽 개행을 걸러 주지 않는다', () => {
    const quoted = quotedParts('『줄\n바꿈 제목』 완독').filter((p) => p.quoted);

    expect(quoted).toHaveLength(1);
    expect(quoted[0].text).toBe('『줄\n바꿈 제목』');
  });
});

describe('소식 피드 (2b)', () => {
  const feed = (e: SocialEvent) =>
    render(
      <FeedBox
        feed={{ social: [e], newsEnabled: false, news: [], readers: [] }}
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

  /** 그 텍스트를 담은 여는 태그 — 세리프·굵기는 인라인 스타일이라 태그만 잘라 보면 판정된다. */
  const tagOf = (markup: string, text: string) => {
    const at = markup.indexOf('>' + text + '<');
    return at < 0 ? '' : markup.slice(markup.lastIndexOf('<', at), at);
  };

  it('책 제목이 세리프 700이다 — 문장 안에서 「무슨 책인가」가 값이다', () => {
    const tag = tagOf(feed(event()), '『데미안』');

    expect(tag).not.toBe('');
    expect(tag).toContain('Gowun Batang');
    expect(tag).toContain('700');
  });

  /**
   * ⚠️ `tagOf`는 못 찾으면 `''`을 돌려주고 `''.not.toContain(...)`은 <b>항상 통과</b>한다 —
   * 부정 단언에는 「찾았다」를 먼저 못 박아야 계측이 산다(픽스처에 `&`가 섞이면 HTML
   * 이스케이프로 원문 검색이 0건이 되어 이 단언이 조용히 죽는다).
   */
  it('제목 아닌 부분은 세리프가 아니다 — 문장 전체가 세리프면 강조가 사라진다', () => {
    const tag = tagOf(feed(event()), '나비독서님이 ');

    expect(tag).not.toBe('');
    expect(tag).not.toContain('Gowun Batang');
  });

  /**
   * 「여백 3」의 숫자는 값이다 — 배지 안에서 그 수 하나가 말하려는 전부다.
   * 라벨과 같은 서체면 「여백」과 「3」이 한 덩어리로 뭉개진다.
   */
  it('「여백 N」 배지의 숫자가 세리프다', () => {
    expect(tagOf(feed(event({ type: 'STORY', count: 3 })), '3')).toContain('Gowun Batang');
  });

  /** 시안 2b는 배지를 11로 둔다 — A가 계단의 `st13`을 12→11로 다시 재단한 그 칸이다. */
  it('배지는 11이다 — 본문(14)과 같은 칸이면 배지가 문장처럼 읽힌다', () => {
    const tag = tagOf(feed(event({ type: 'STORY', count: 3 })), '여백 ');

    expect(tag).not.toBe('');
    expect(tag).toContain('font-size:11px');
  });

  /**
   * 시안 2b의 색 두 자리 — <b>서사로만 두지 않는다</b>. 채움은 세이지 800(시안 `#40573E`가 이 램프의
   * 한 칸), 인용 보더는 200(시안 `rgba(110,138,106,.5)`가 크림 위에서 내려앉는 칸)이다. 회갈색으로
   * 되돌아가면 「남의 말」과 「카드 경계」가 같은 선이 된다.
   */
  it('채움 배지는 세이지 800, 여백 인용 보더는 세이지 200이다', () => {
    expect(tagOf(feed(event()), '다 읽음')).toContain('--adaptiveBlue800');
    expect(feed(event({ type: 'STORY', excerpt: '밑줄 그은 문장' }))).toContain(
      '2px solid var(--adaptiveBlue200',
    );
  });

  it('여백이 1장이면 숫자를 안 센다 — 셀 것이 없으면 배지도 「여백」 한 마디다', () => {
    const markup = feed(event({ type: 'STORY', count: 1 }));

    expect(markup).toContain('여백');
    expect(tagOf(markup, '1')).toBe('');
  });
});
