import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it } from 'vitest';

import type { HomeFeedResponse, NewsItem, ReaderStatus, SocialEvent } from './api';
import { CACHE_FEED, cacheClear, cachePut } from './cache';
import type { FeedTab } from './screens/HomeFeed';
import {
  DEFAULT_TAB,
  EMPTY_MESSAGE,
  FeedBox,
  HomeFeedBox,
  PREVIEW_COUNT,
  ReaderRow,
  eventLine,
  eventBadge,
  previewOf,
  readerStatusLine,
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
    bookId: null,
    excerpt: null,
    count: 0,
    coverUrl: null,
  };
}

/** 여백 줄 — 사람+책 단위로 묶여 온다(서버가 묶는다). 세 필드는 이 종류에만 실린다. */
function story(nickname: string, bookTitle: string, hoursAgo: number, count: number, excerpt = '남긴 문장'): SocialEvent {
  return { ...event(nickname, bookTitle, 'STORY', hoursAgo), bookId: 7, excerpt, count };
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

/** 「함께 읽는 사람」 한 줄. 서버는 공개 책 독서만 담아 준다 — 비공개는 세 필드가 전부 null로 온다. */
function reader(overrides: Partial<ReaderStatus> = {}): ReaderStatus {
  return {
    loginId: 'nabi',
    nickname: '여느밤',
    mutual: false,
    readingBookTitle: null,
    readingSince: null,
    lastReadAt: null,
    lastReadBookTitle: null,
    ...overrides,
  };
}

function feed(overrides: Partial<HomeFeedResponse> = {}): HomeFeedResponse {
  return { social: [], newsEnabled: true, news: [], readers: [], ...overrides };
}

const renderBox = (
  data: HomeFeedResponse | null,
  tab: FeedTab = 'social',
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
        onOpenMargin={() => {}}
      />
    </TDSMobileProvider>,
  );

/** 그려진 탭 머리 — 죽은 탭이 서지 않는지는 결국 이 목록이 말한다. */
const tabsOf = (markup: string) => [...markup.matchAll(/data-feed-tab="([^"]*)"/g)].map((m) => m[1]);
const rowCountOf = (markup: string) => [...markup.matchAll(/data-feed-row/g)].length;
/** 줄을 그린 태그 — 「눌러서 갈 곳이 있는 줄」과 없는 줄을 마크업으로 가른다. */
const rowTagsOf = (markup: string) => [...markup.matchAll(/<(\w+)[^>]*data-feed-row/g)].map((m) => m[1]);

describe('탭 노출 (visibleTabs)', () => {
  it('뉴스가 꺼져 있으면 사람 탭 + 「소식」 — 눌러도 빈 뉴스 탭은 죽은 탭이다', () => {
    expect(visibleTabs(false)).toEqual(['readers', 'social']);
  });

  it('뉴스가 켜져 있으면 세 탭', () => {
    expect(visibleTabs(true)).toEqual(['readers', 'social', 'news']);
  });

  it('사람 탭이 맨 왼쪽이다 — 위치가 곧 이 탭이 다른 종류라는 표시다', () => {
    expect(visibleTabs(true)[0]).toBe('readers');
  });

  it('맨 왼쪽이지만 기본으로 열리는 탭은 「소식」이다 — 기존 홈 경험을 바꾸지 않는다', () => {
    // 팔로우 0명인 사람이 빈 목록을 먼저 만나는 일도 이 한 줄이 막는다.
    expect(DEFAULT_TAB).toBe('social');
  });
});

describe('사건 배지 (eventBadge)', () => {
  it('배지 문구는 서재가 이미 쓰는 말을 그대로 쓴다 — 화면마다 다른 이름으로 부르지 않는다', () => {
    expect(eventBadge(event('나비', '데미안', 'FINISHED', 1)).label).toBe('다 읽음');
    expect(eventBadge(event('나비', '데미안', 'STARTED', 1)).label).toBe('읽는 중');
  });

  it('완독만 채운 배지다 — 이 앱에서 가장 축하할 사건이 나머지와 같은 무게면 안 된다', () => {
    expect(eventBadge(event('나비', '데미안', 'FINISHED', 1)).tone).toBe('solid');
    expect(eventBadge(event('나비', '데미안', 'STARTED', 1)).tone).toBe('outline');
    expect(eventBadge(story('나비', '데미안', 1, 1)).tone).toBe('tint');
  });

  it('여백은 묶인 개수를 배지가 세고, 1장이면 안 센다(문장과 같은 규칙)', () => {
    expect(eventBadge(story('나비', '데미안', 1, 1)).label).toBe('여백');
    expect(eventBadge(story('나비', '데미안', 1, 3)).label).toBe('여백 3');
  });
});

describe('독서 상태 문구 (readerStatusLine)', () => {
  const NOW_ISO = (hoursAgo: number) => new Date(NOW - hoursAgo * HOUR).toISOString();

  it('읽는 중이면 책 제목이 문장에 든다', () => {
    const line = readerStatusLine(
      reader({ readingBookTitle: '데미안', readingSince: NOW_ISO(1) }),
      NOW,
    );

    expect(line).toBe('『데미안』 읽는 중');
  });

  it('마지막 기록에도 책 제목이 든다 — 읽는 중 줄과 같은 모양으로 서서 책이 열의 척추가 된다', () => {
    expect(readerStatusLine(reader({ lastReadBookTitle: '아버지의 해방일지', lastReadAt: NOW_ISO(72) }), NOW))
      .toBe('『아버지의 해방일지』 · 3일 전');
  });

  it('제목 없이 시각만 오면 옛 문구로 떨어진다 — 「사실만 적는다」는 약속은 그대로다', () => {
    // 새 쿼리는 시각과 제목을 같은 행에서 뽑아 이 조합이 안 생기지만, 타입이 허용하는 한 그려야 한다.
    expect(readerStatusLine(reader({ lastReadAt: NOW_ISO(72) }), NOW)).toBe('마지막 기록 3일 전');
    expect(readerStatusLine(reader({ lastReadAt: NOW_ISO(2) }), NOW)).toBe('마지막 기록 2시간 전');
  });

  it('공개 기록이 없으면 「공개된」이라고 밝힌다 — 비공개로 읽는 사람을 안 읽는 사람으로 만들지 않는다', () => {
    const line = readerStatusLine(reader(), NOW);

    expect(line).toBe('공개된 기록이 없어요');
    expect(line).not.toContain('안 읽');
  });

  it('읽는 중이면 과거 기록이 있어도 읽는 중이 이긴다', () => {
    const line = readerStatusLine(
      reader({
        readingBookTitle: '코스모스',
        readingSince: NOW_ISO(1),
        lastReadAt: NOW_ISO(500),
        lastReadBookTitle: '옛책',
      }),
      NOW,
    );

    expect(line).toBe('『코스모스』 읽는 중');
    expect(line).not.toContain('옛책');
  });
});

describe('사람 한 줄 (ReaderRow)', () => {
  const render = (r: ReaderStatus) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <ReaderRow reader={r} index={0} now={NOW} />
      </TDSMobileProvider>,
    );

  it('읽는 중이면 경과 시간이 붙는다', () => {
    const markup = render(
      reader({ readingBookTitle: '데미안', readingSince: new Date(NOW - 42 * 60_000).toISOString() }),
    );

    expect(markup).toContain('42분째');
    expect(markup).toContain('『데미안』 읽는 중');
  });

  it('읽는 중이 아니면 경과 시간을 안 붙인다 — 본문이 이미 시점을 말한다(같은 말 두 번 금지)', () => {
    const markup = render(reader({ lastReadAt: new Date(NOW - 72 * HOUR).toISOString() }));

    expect(markup).toContain('마지막 기록 3일 전');
    expect(markup).not.toContain('째');
  });

  it('맞팔이면 「서로」가 붙고 아니면 안 붙는다 — 친구 시스템 없이 친구감만 가져오는 자리', () => {
    expect(render(reader({ mutual: true }))).toContain('서로');
    expect(render(reader({ mutual: false }))).not.toContain('서로');
  });

  it('닉네임을 그린다', () => {
    expect(render(reader({ nickname: '물결' }))).toContain('물결');
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

  /**
   * 여백 줄은 「…의 여백에」 구조라 목적격 조사가 필요 없다 — 완독·시작 문장과 문법이 다르다.
   * 묶음이라 개수가 곧 문구를 가른다: 1장이면 「글을」, 2장 이상이면 「글 N개를」.
   */
  it('여백은 사람+책 묶음 한 줄로 말한다 — 1장이면 개수를 세지 않는다', () => {
    expect(eventLine(story('나비독서', '데미안', 1, 1))).toBe('나비독서님이 『데미안』의 여백에 글을 남겼어요');
  });

  it('2장 이상이면 개수를 적는다 — 묶은 이유가 문장에 드러나야 한다', () => {
    expect(eventLine(story('밑줄러', '사피엔스', 1, 3))).toBe('밑줄러님이 『사피엔스』의 여백에 글 3개를 남겼어요');
  });

  it('여백 문장에는 목적격 조사가 끼지 않는다 — 「『데미안』을의 여백에」가 되면 문장이 깨진다', () => {
    expect(eventLine(story('ㄱ', '데미안', 1, 2))).not.toContain('『데미안』을');
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

  /**
   * 여백 줄만 <b>갈 곳이 있다</b> — 탭하면 책방 탭의 그 책 여백으로 점프한다. 완독·시작은 예전처럼
   * 비클릭이다(열 화면이 없다). 마크업의 태그가 그 차이를 진다 — 클릭 자체는 정적 하니스로 못 잡는다.
   */
  it('여백 줄은 버튼, 완독·시작 줄은 아니다 — 갈 곳 없는 줄을 누를 수 있게 보이지 않는다', () => {
    const markup = renderBox(feed({ social: [story('나비독서', '데미안', 1, 2), event('밑줄러', '사피엔스', 'STARTED', 2)] }));

    expect(rowTagsOf(markup)).toEqual(['button', 'div']);
  });

  it('여백 줄에 발췌 한 줄을 함께 싣는다 — 무슨 글인지 보고 들어갈지 정한다', () => {
    const markup = renderBox(feed({ social: [story('나비독서', '데미안', 1, 1, '오늘은 한 시간을 넘겼다.')] }));

    expect(markup).toContain('오늘은 한 시간을 넘겼다.');
  });

  it('발췌는 한 줄로 자른다 — 500자 문장이 피드 박스를 통째로 먹지 않게', () => {
    // 말줄임 자체는 서버가 이미 했다(80자) — 여기서는 폭에 맞춘 CSS clamp만 얹는다.
    expect(renderBox(feed({ social: [story('나비독서', '데미안', 1, 1)] }))).toContain('-webkit-line-clamp:1');
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
  it('뉴스가 켜져 있으면 세 탭이 서고 지금 탭이 표시된다', () => {
    const markup = renderBox(feed(), 'news');

    expect(tabsOf(markup)).toEqual(['readers', 'social', 'news']);
    expect(markup).toContain('소식');
    expect(markup).toContain('책 뉴스');
  });

  it('뉴스가 꺼져 있으면 「책 뉴스」 머리 자체를 안 그린다 — 죽은 탭 금지', () => {
    expect(tabsOf(renderBox(feed({ newsEnabled: false })))).toEqual(['readers', 'social']);
  });

  it('사람 탭 뒤에 세로 구분선이 선다 — 같은 줄이지만 같은 종류가 아니라는 표시', () => {
    const markup = renderBox(feed());

    expect(markup).toContain('data-tab-split');
    // 구분선은 사람 탭과 「소식」 사이에 있어야 의미가 산다.
    expect(markup.indexOf('data-feed-tab="readers"')).toBeLessThan(markup.indexOf('data-tab-split'));
    expect(markup.indexOf('data-tab-split')).toBeLessThan(markup.indexOf('data-feed-tab="social"'));
  });

  it('사람 탭엔 글자가 없으므로 이름을 달아 준다 — 없으면 스크린리더에 무명 버튼이 된다', () => {
    expect(renderBox(feed())).toContain('aria-label="함께 읽는 사람"');
  });
});

describe('피드 박스 — 사람 탭', () => {
  it('팔로우한 사람들을 서버가 준 순서대로 그린다 (읽는 중 → 맞팔 → 최근순)', () => {
    const markup = renderBox(
      feed({
        readers: [
          reader({ loginId: 'a', nickname: '여느밤', readingBookTitle: '데미안', readingSince: new Date(NOW - HOUR).toISOString() }),
          reader({ loginId: 'b', nickname: '물결', lastReadAt: new Date(NOW - 72 * HOUR).toISOString() }),
        ],
      }),
      'readers',
    );

    expect(rowCountOf(markup)).toBe(2);
    expect(markup.indexOf('여느밤')).toBeLessThan(markup.indexOf('물결'));
  });

  it('아무도 없으면 여기가 무엇으로 채워지는 자리인지 말한다', () => {
    expect(renderBox(feed(), 'readers')).toContain(EMPTY_MESSAGE.readers);
  });

  it('미리보기는 3줄까지, 넘으면 「더 보기」 — 소식·뉴스와 같은 규칙', () => {
    const many = Array.from({ length: 5 }, (_, i) => reader({ loginId: `u${i}`, nickname: `사람${i}` }));

    expect(rowCountOf(renderBox(feed({ readers: many }), 'readers'))).toBe(PREVIEW_COUNT);
    expect(renderBox(feed({ readers: many }), 'readers')).toContain('더 보기');
    expect(rowCountOf(renderBox(feed({ readers: many }), 'readers', true))).toBe(5);
  });

  it('구버전 서버(readers 없음)에도 안 깨진다 — 서버·미니앱 배포 순서에 의존하지 않는다', () => {
    const legacy = { social: [], newsEnabled: true, news: [] } as unknown as HomeFeedResponse;

    expect(() => renderBox(legacy, 'readers')).not.toThrow();
    expect(renderBox(legacy, 'readers')).toContain(EMPTY_MESSAGE.readers);
  });
});

describe('피드 박스 — 실패·로딩', () => {
  it('실패는 박스 안 한 줄로 끝난다 — 홈 전체를 깨지 않는다', () => {
    const markup = renderBox(null, 'social', false, '요청에 실패했어요 (500)');

    expect(markup).toContain('요청에 실패했어요 (500)');
    expect(tabsOf(markup)).toEqual(['readers', 'social']); // 박스 자체는 그대로 서 있다
  });

  it('아직 못 받았으면 불러오는 중이라고 말한다', () => {
    expect(renderBox(null)).toContain('불러오는 중');
  });
});

/**
 * 홈 피드 세션 캐시 — 홈은 탭을 오갈 때마다 재마운트돼서, 히어로는 즉시 뜨는데 피드 박스만 매번
 * 빈 상태로 시작했다. 지난 성공 응답을 첫 렌더의 출발점으로 삼아 그 구간을 없앤다(재검증은 그대로).
 */
describe('홈 피드 세션 캐시 (HomeFeedBox)', () => {
  beforeEach(cacheClear);

  const renderMounted = () =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <HomeFeedBox onError={() => {}} onOpenMargin={() => {}} />
      </TDSMobileProvider>,
    );

  it('캐시가 비면 지금처럼 빈 박스다 — effect가 안 도는 정적 렌더의 기본값', () => {
    expect(renderMounted()).not.toContain('여느밤');
  });

  it('지난 피드가 캐시에 있으면 첫 렌더부터 줄이 선다', () => {
    // 진입 탭은 `DEFAULT_TAB`('소식')이라 그 탭에 실리는 종류로 채운다.
    cachePut(CACHE_FEED, feed({ social: [event('여느밤', '데미안', 'FINISHED', 2)] }));

    expect(renderMounted()).toContain('여느밤');
  });
});
