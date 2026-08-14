import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { HomeFeedResponse, NewsItem, SocialEvent } from './api';
import {
  EMPTY_MESSAGE,
  FeedBox,
  PREVIEW_COUNT,
  eventLine,
  previewOf,
  sourceLabel,
  sourceOf,
  visibleTabs,
} from './screens/HomeFeed';
import { userAgent } from './test-fixtures';

/**
 * 홈 피드 박스 — 「소식」·「책 뉴스」 두 탭.
 *
 * <p>하니스가 `renderToStaticMarkup`이라 탭 전환·「더 보기」 클릭은 못 잡는다(jsdom 미도입) —
 * 그래서 탭·펼침 상태를 **prop으로 직접 꽂아** 각 상태의 마크업을 계측하고(`BookSheet`·`RemainingNote`와
 * 같은 방식), 산식은 순수 함수로 꺼내 따로 못 박는다. 전환 자체는 목 모드 브라우저 확인이 게이트다.
 */

const NOW = Date.parse('2026-08-14T12:00:00Z');
const HOUR = 3_600_000;

function event(nickname: string, bookTitle: string, type: SocialEvent['type'], hoursAgo: number): SocialEvent {
  return {
    loginId: 'nabi',
    nickname,
    bookTitle,
    type,
    occurredAt: new Date(NOW - hoursAgo * HOUR).toISOString(),
  };
}

function news(
  title: string,
  link: string,
  hoursAgo: number,
  bookTitle = '데미안',
  source: string | null = '불교신문',
): NewsItem {
  return { title, link, publishedAt: new Date(NOW - hoursAgo * HOUR).toISOString(), bookTitle, source };
}

function feed(overrides: Partial<HomeFeedResponse> = {}): HomeFeedResponse {
  return { social: [], newsEnabled: true, news: [], ...overrides };
}

const renderBox = (
  data: HomeFeedResponse | null,
  tab: 'social' | 'news' = 'social',
  expanded = false,
  error: string | null = null,
) =>
  renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <FeedBox
        feed={data}
        tab={tab}
        expanded={expanded}
        error={error}
        now={NOW}
        onTab={() => {}}
        onToggle={() => {}}
        onOpenNews={() => {}}
      />
    </TDSMobileProvider>,
  );

/** 그려진 탭 머리 — 죽은 탭이 서지 않는지는 결국 이 목록이 말한다. */
const tabsOf = (markup: string) => [...markup.matchAll(/data-feed-tab="([^"]*)"/g)].map((m) => m[1]);
const rowCountOf = (markup: string) => [...markup.matchAll(/data-feed-row/g)].length;

describe('탭 노출 (visibleTabs)', () => {
  it('뉴스가 꺼져 있으면 「소식」 하나 — 눌러도 빈 뉴스 탭은 죽은 탭이다', () => {
    expect(visibleTabs(false)).toEqual(['social']);
  });

  it('뉴스가 켜져 있으면 두 탭', () => {
    expect(visibleTabs(true)).toEqual(['social', 'news']);
  });
});

describe('미리보기 (previewOf)', () => {
  const items = [1, 2, 3, 4, 5];

  it('접힌 상태는 3줄 + 더 있음 표시', () => {
    expect(previewOf(items, false)).toEqual({ items: [1, 2, 3], hasMore: true });
    expect(PREVIEW_COUNT).toBe(3);
  });

  it('3줄 이하면 더 볼 게 없다 — 「더 보기」가 헛되이 서지 않는다', () => {
    expect(previewOf([1, 2, 3], false)).toEqual({ items: [1, 2, 3], hasMore: false });
    expect(previewOf([], false)).toEqual({ items: [], hasMore: false });
  });

  it('펼치면 전부 — 서버 상한(30건) 안에서 인라인으로 끝난다', () => {
    expect(previewOf(items, true)).toEqual({ items, hasMore: false });
  });
});

describe('뉴스 출처 (sourceLabel)', () => {
  const GOOGLE_LINK = 'https://news.google.com/rss/articles/CBMiW0FVX3lxTFBP?oc=5';

  /*
   * 수집원이 구글 뉴스 RSS라 링크가 전부 구글 리다이렉트다 — 호스트명으로 파생하면 모든 기사의
   * 출처가 「news.google.com」이 된다. 그래서 서버가 RSS의 <source> 엘리먼트에서 뽑은 매체명을 준다.
   */
  it('서버가 준 매체명을 쓴다 — 구글 리다이렉트 링크라 호스트명은 쓸모가 없다', () => {
    expect(sourceLabel(news('제목', GOOGLE_LINK, 1, '데미안', '불교신문'))).toBe('불교신문');
  });

  it('서버 값이 없으면 링크 호스트명으로 떨어진다 — 수집원이 또 바뀌어도 줄이 안 깨진다', () => {
    expect(sourceLabel(news('제목', 'https://n.news.naver.com/article/001/1', 1, '데미안', null))).toBe(
      'n.news.naver.com',
    );
  });

  it('서버 값이 공백뿐이면 없는 것으로 본다', () => {
    expect(sourceLabel(news('제목', 'https://www.hani.co.kr/arti/1', 1, '데미안', '   '))).toBe('www.hani.co.kr');
  });

  it('둘 다 못 읽으면 빈 문자열 — 출처만 비고 기사 줄은 살아 있어야 한다', () => {
    expect(sourceLabel(news('제목', '그냥 문자열', 1, '데미안', null))).toBe('');
  });
});

describe('링크 호스트명 파생 (sourceOf — 폴백)', () => {
  it('주소면 호스트명', () => {
    expect(sourceOf('https://n.news.naver.com/article/001/0012345')).toBe('n.news.naver.com');
  });

  it('주소가 아니면 빈 문자열', () => {
    expect(sourceOf('그냥 문자열')).toBe('');
    expect(sourceOf('')).toBe('');
  });
});

describe('소식 문구 (eventLine)', () => {
  it('완독과 읽기 시작을 다른 문장으로 말한다', () => {
    expect(eventLine(event('나비독서', '데미안', 'FINISHED', 1))).toBe('나비독서님이 『데미안』을 완독했어요');
    expect(eventLine(event('밑줄러', '사피엔스', 'STARTED', 1))).toBe('밑줄러님이 『사피엔스』를 읽기 시작했어요');
  });

  /*
   * 조사는 제목 마지막 글자의 받침이 정한다 — 「을」로 고정하면 받침 없는 제목이
   * 전부 「사피엔스을」로 나온다(피드는 남의 책 제목을 그대로 싣는 자리라 자주 걸린다).
   */
  it('받침이 있으면 「을」, 없으면 「를」', () => {
    expect(eventLine(event('ㄱ', '데미안', 'FINISHED', 1))).toContain('『데미안』을');
    expect(eventLine(event('ㄱ', '총, 균, 쇠', 'FINISHED', 1))).toContain('『총, 균, 쇠』를');
    expect(eventLine(event('ㄱ', '미움받을 용기', 'FINISHED', 1))).toContain('『미움받을 용기』를');
  });

  it('숫자로 끝나면 읽는 소리의 받침을 따른다 — 『1984』는 「사」라 「를」', () => {
    expect(eventLine(event('ㄱ', '1984', 'FINISHED', 1))).toContain('『1984』를');
    expect(eventLine(event('ㄱ', '드래곤볼 7', 'FINISHED', 1))).toContain('『드래곤볼 7』을');
  });

  it('한글도 숫자도 아니면 「를」로 떨어진다 — 문장이 깨지지 않는 게 우선', () => {
    expect(eventLine(event('ㄱ', 'Sapiens', 'FINISHED', 1))).toContain('『Sapiens』를');
    expect(eventLine(event('ㄱ', '', 'FINISHED', 1))).toContain('『』를');
  });
});

describe('피드 박스 렌더 — 소식 탭', () => {
  const social = [
    event('나비독서', '데미안', 'FINISHED', 1),
    event('밑줄러', '사피엔스', 'STARTED', 5),
    event('지은의서재', '코스모스', 'FINISHED', 30),
    event('나비독서', '총, 균, 쇠', 'STARTED', 80),
  ];

  it('소식 한 줄마다 문구와 상대 시간을 적는다', () => {
    const markup = renderBox(feed({ social }));

    expect(markup).toContain('나비독서님이 『데미안』을 완독했어요');
    expect(markup).toContain('1시간 전');
    expect(markup).toContain('밑줄러님이 『사피엔스』를 읽기 시작했어요');
  });

  it('접힌 상태는 3줄까지 + 「더 보기」', () => {
    const markup = renderBox(feed({ social }));

    expect(rowCountOf(markup)).toBe(3);
    expect(markup).toContain('더 보기');
  });

  it('펼치면 전부 그린다 — 별도 화면 없이 홈 안에서 끝난다', () => {
    const markup = renderBox(feed({ social }), 'social', true);

    expect(rowCountOf(markup)).toBe(social.length);
    expect(markup).toContain('나비독서님이 『총, 균, 쇠』를 읽기 시작했어요');
  });

  it('소식이 없으면 소셜 탭으로 유도하는 빈 상태를 띄운다', () => {
    expect(renderBox(feed())).toContain(EMPTY_MESSAGE.social);
  });
});

describe('피드 박스 렌더 — 책 뉴스 탭', () => {
  const items = [news('『데미안』 100년, 다시 읽는 성장소설', 'https://n.news.naver.com/article/001/1', 3)];

  it('기사 제목·출처·상대 시간과 내 책 라벨을 한 줄에 담는다', () => {
    const markup = renderBox(feed({ news: items }), 'news');

    expect(markup).toContain('『데미안』 100년, 다시 읽는 성장소설');
    expect(markup).toContain('불교신문'); // 서버가 준 매체명 — 링크 호스트명이 아니다
    expect(markup).toContain('3시간 전');
    expect(markup).toContain('내 책');
    expect(markup).toContain('데미안');
  });

  it('무엇을 열지는 마크업의 링크 주소가 말한다 — 여는 일 자체는 토스 SDK가 맡는다', () => {
    expect(renderBox(feed({ news: items }), 'news')).toContain('href="https://n.news.naver.com/article/001/1"');
  });

  it('뉴스가 없으면 완독한 책의 뉴스가 뜬다고 알린다', () => {
    expect(renderBox(feed(), 'news')).toContain(EMPTY_MESSAGE.news);
  });
});

describe('피드 박스 — 탭 머리', () => {
  it('뉴스가 켜져 있으면 두 탭이 서고 지금 탭이 표시된다', () => {
    const markup = renderBox(feed(), 'news');

    expect(tabsOf(markup)).toEqual(['social', 'news']);
    expect(markup).toContain('소식');
    expect(markup).toContain('책 뉴스');
  });

  it('뉴스가 꺼져 있으면 「책 뉴스」 머리 자체를 안 그린다 — 죽은 탭 금지', () => {
    expect(tabsOf(renderBox(feed({ newsEnabled: false })))).toEqual(['social']);
  });
});

describe('피드 박스 — 실패·로딩', () => {
  it('실패는 박스 안 한 줄로 끝난다 — 홈 전체를 깨지 않는다', () => {
    const markup = renderBox(null, 'social', false, '요청에 실패했어요 (500)');

    expect(markup).toContain('요청에 실패했어요 (500)');
    expect(tabsOf(markup)).toEqual(['social']); // 박스 자체는 그대로 서 있다
  });

  it('아직 못 받았으면 불러오는 중이라고 말한다', () => {
    expect(renderBox(null)).toContain('불러오는 중');
  });
});
