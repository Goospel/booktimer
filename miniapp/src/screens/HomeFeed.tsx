import { Text } from '@toss/tds-mobile';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import type { HomeFeedResponse, NewsItem, SocialEvent } from '../api';
import { fetchHomeFeed } from '../api';
import { relativeTime } from '../format';
import { openExternal } from '../toss';
import { sectionStyle } from '../ui';

/**
 * 홈 피드 박스 — 「소식」·「책 뉴스」 두 탭. 잔디 미리보기가 서 있던 자리를 물려받았다.
 *
 * <p>홈(`Home.tsx`)이 1,000줄이라 여기로 뗐다. 데이터도 대시보드에 얹지 않고 자기가 받는다
 * (`Social`·`Library`의 자체 fetch 선례) — 히어로 렌더가 피드 쿼리에 인질로 잡히지 않는다.
 *
 * <p>정적 렌더 하니스(jsdom 미도입)라 탭 전환·「더 보기」 클릭에 도달할 수 없다. 그래서 상태를 든
 * {@link HomeFeedBox}와 그리기만 하는 {@link FeedBox}를 나눠, 테스트는 탭·펼침을 **prop으로 직접 꽂아**
 * 계측한다(`BookSheet`·`RemainingNote`와 같은 처지).
 */

export type FeedTab = 'social' | 'news';

const TAB_LABEL: Record<FeedTab, string> = { social: '소식', news: '책 뉴스' };

/** 빈 상태 문구 — 둘 다 "여기가 무엇으로 채워지는 자리인가"를 말한다(소식은 소셜 탭 유도를 겸한다). */
export const EMPTY_MESSAGE: Record<FeedTab, string> = {
  social: '팔로우한 사람의 소식이 여기에 떠요',
  news: '완독한 책의 뉴스가 여기에 떠요',
};

/** 접힌 상태의 줄 수 — 폴드 아래 카드라 이보다 길면 홈이 피드에 잡아먹힌다. */
export const PREVIEW_COUNT = 3;

/** 소식 종류를 한눈에 가르는 표지 — 문장을 읽기 전에 무슨 일인지 보인다. */
const EVENT_ICON: Record<SocialEvent['type'], string> = { FINISHED: '✅', STARTED: '📖', STORY: '✍️' };

/**
 * 그릴 탭 머리 — 뉴스가 꺼져 있으면(수집기 키 미설정) **「책 뉴스」 머리 자체를 안 그린다.**
 * 눌러도 늘 빈 탭은 죽은 탭이라, 있는 것보다 없는 게 낫다.
 */
export function visibleTabs(newsEnabled: boolean): FeedTab[] {
  return newsEnabled ? ['social', 'news'] : ['social'];
}

/**
 * 지금 그릴 줄과 「더 보기」 필요 여부 — 펼치면 서버가 준 전부(상한 30건)다.
 * 별도 화면 없이 홈 안에서 끝나므로 내비게이션 배선이 0이다.
 */
export function previewOf<T>(items: T[], expanded: boolean): { items: T[]; hasMore: boolean } {
  return expanded
    ? { items, hasMore: false }
    : { items: items.slice(0, PREVIEW_COUNT), hasMore: items.length > PREVIEW_COUNT };
}

/**
 * 기사 줄에 적을 출처 — **서버가 준 매체명이 먼저다.**
 *
 * <p>수집원이 구글 뉴스 RSS라 `link`가 전부 구글 리다이렉트(`news.google.com/rss/articles/...`)다.
 * 링크에서 파생하면 모든 기사의 출처가 「news.google.com」이 되어 버리므로, 서버가 RSS의
 * `<source>` 엘리먼트에서 뽑아 준 값을 쓴다. 값이 없는 옛 행·다른 수집원을 위해 호스트명 폴백을
 * 남긴다 — 둘 다 못 읽으면 출처만 비고 기사 줄 자체는 살아 있어야 한다.
 */
export function sourceLabel(item: NewsItem): string {
  return item.source?.trim() || sourceOf(item.link);
}

/** 링크에서 파생하는 폴백 출처. 주소로 못 읽으면 빈 문자열. */
export function sourceOf(link: string): string {
  try {
    return new URL(link).hostname;
  } catch {
    return '';
  }
}

/**
 * 목적격 조사 — 받침이 있으면 「을」, 없으면 「를」.
 *
 * <p>피드는 남의 책 제목을 그대로 싣는 자리라 조사를 하나로 고정할 수 없다
 * (「사피엔스을」이 목 픽스처 5건 중 3건에서 나왔다). 한글 음절은 유니코드
 * 자모 조합이라 `(코드 - 가) % 28`이 곧 종성 인덱스이고, 0이면 받침이 없다.
 * 숫자는 읽는 소리를 따르고(1984 → "사" → 를), 그 밖(영문·기호·빈 제목)은
 * 「를」로 떨어뜨린다 — 어느 쪽이든 문장이 깨지지 않는 게 우선이다.
 */
function objectParticle(word: string): string {
  const last = word.at(-1);
  if (last === undefined) return '를';

  const code = last.charCodeAt(0);
  if (code >= 0xac00 && code <= 0xd7a3) return (code - 0xac00) % 28 === 0 ? '를' : '을';

  // 영/일/이/삼/사/오/육/칠/팔/구 — 받침 있는 소리만 「을」.
  const DIGIT_HAS_FINAL = [true, true, false, true, false, false, true, true, true, false];
  if (last >= '0' && last <= '9') return DIGIT_HAS_FINAL[Number(last)] ? '을' : '를';

  return '를';
}

/**
 * 소식 한 줄의 문장.
 *
 * <p>완독·시작은 책이 목적어라 조사가 필요하지만, 여백은 「…의 여백에」라 조사가 끼면 문장이 깨진다
 * (「『데미안』을의 여백에」) — 그래서 문장 골격 자체가 갈린다. 여백은 사람+책 단위로 **묶여** 오므로
 * 개수도 문구를 가른다: 1장이면 세지 않고, 2장 이상이면 묶은 이유가 문장에 드러나야 한다.
 */
export function eventLine(event: SocialEvent): string {
  if (event.type === 'STORY') {
    const what = event.count > 1 ? `글 ${event.count}개를` : '글을';
    return `${event.nickname}님이 『${event.bookTitle}』의 여백에 ${what} 남겼어요`;
  }
  const predicate = event.type === 'FINISHED' ? '완독했어요' : '읽기 시작했어요';
  return `${event.nickname}님이 『${event.bookTitle}』${objectParticle(event.bookTitle)} ${predicate}`;
}

/** 탭 알약 — 선택된 쪽만 연세이지 배경(홈 우상단 목표 손잡이와 같은 값이라 화면에 색이 늘지 않는다). */
const pillStyle = (active: boolean) =>
  ({
    padding: '6px 14px',
    border: 0,
    borderRadius: 20,
    background: active ? 'var(--adaptiveBlue50, #E7EEE2)' : 'transparent',
    color: active ? 'var(--adaptiveBlue700, #4F6B4C)' : 'var(--adaptiveGrey600, #6F6A5E)',
    fontSize: 13,
    fontWeight: active ? 600 : 400,
    cursor: 'pointer',
  }) as const;

/** 줄 사이 경계 — 첫 줄에는 안 긋는다(박스 머리와 겹쳐 두 줄로 보인다). */
const rowStyle = (index: number) =>
  ({
    display: 'block',
    width: '100%',
    padding: '10px 0',
    border: 0,
    borderTop: index === 0 ? undefined : '0.5px solid var(--adaptiveGrey200, #E4DDD0)',
    background: 'transparent',
    textAlign: 'left',
    textDecoration: 'none',
    cursor: 'pointer',
  }) as const;

/**
 * 목록 한 벌 — 미리보기 3줄 + 「더 보기」 + 빈 상태. 두 탭이 같은 규칙을 쓰므로 여기 한 곳에 둔다
 * (제네릭이라 소식·뉴스 타입을 캐스트 없이 그대로 받는다).
 */
function FeedList<T>({
  items,
  expanded,
  empty,
  onToggle,
  row,
}: {
  items: T[];
  expanded: boolean;
  empty: string;
  onToggle: () => void;
  row: (item: T, index: number) => ReactNode;
}) {
  const { items: visible, hasMore } = previewOf(items, expanded);

  if (visible.length === 0) {
    return (
      <Text typography="st12" color="grey600" style={{ display: 'block', wordBreak: 'keep-all' }}>
        {empty}
      </Text>
    );
  }

  return (
    <>
      {visible.map(row)}
      {hasMore && (
        <div style={{ textAlign: 'center', marginTop: 4 }}>
          <button
            type="button"
            onClick={onToggle}
            style={{
              padding: '8px 12px',
              border: 0,
              background: 'transparent',
              color: 'var(--adaptiveGrey600, #6F6A5E)',
              fontSize: 13,
              cursor: 'pointer',
            }}
          >
            더 보기
          </button>
        </div>
      )}
    </>
  );
}

/**
 * 피드 박스(그리기 전용) — 상태는 전부 밖에서 받는다. 하니스가 클릭을 못 잡아, 이 분리가 아니면
 * 「책 뉴스」 탭·펼친 목록의 마크업에 영영 닿지 못한다.
 */
export function FeedBox({
  feed,
  tab,
  expanded,
  error,
  now,
  onTab,
  onToggle,
  onOpenNews,
  onOpenMargin,
}: {
  /** `null`이면 아직 못 받은 상태(실패는 `error`가 따로 말한다). */
  feed: HomeFeedResponse | null;
  tab: FeedTab;
  expanded: boolean;
  error: string | null;
  /** 상대 시각의 기준 — 밖에서 받아야 테스트가 결정론이 된다. */
  now: number;
  onTab: (tab: FeedTab) => void;
  onToggle: () => void;
  /** 기사 열기 — 실제 동작(토스 SDK)은 밖이 정하고, 여기는 "무엇을 열지"만 넘긴다. */
  onOpenNews: (link: string) => void;
  /** 여백 줄 탭 — 책방 탭으로 옮겨 그 책의 여백을 여는 일은 App이 한다(탭 전환의 주인은 하나다). */
  onOpenMargin: (loginId: string, bookId: number) => void;
}) {
  return (
    <section style={sectionStyle}>
      <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
        {visibleTabs(feed?.newsEnabled ?? false).map((key) => (
          <button
            key={key}
            type="button"
            data-feed-tab={key}
            aria-current={key === tab ? 'true' : undefined}
            onClick={() => onTab(key)}
            style={pillStyle(key === tab)}
          >
            {TAB_LABEL[key]}
          </button>
        ))}
      </div>

      {/* 실패는 이 한 줄로 끝난다 — 홈 전체를 에러 화면으로 바꾸지 않는다(폴드 아래 카드다). */}
      {error !== null ? (
        <Text typography="st12" color="red500" style={{ display: 'block', wordBreak: 'keep-all' }}>
          {error}
        </Text>
      ) : feed === null ? (
        <Text typography="st12" color="grey600" style={{ display: 'block' }}>
          불러오는 중…
        </Text>
      ) : tab === 'news' ? (
        <FeedList
          items={feed.news}
          expanded={expanded}
          empty={EMPTY_MESSAGE.news}
          onToggle={onToggle}
          row={(item: NewsItem, index) => (
            <a
              key={item.link}
              data-feed-row=""
              href={item.link}
              // 기본 이동을 막고 SDK로 넘긴다 — href는 남겨 둔다(무엇을 여는 줄인지가 마크업에 남는다).
              onClick={(e) => {
                e.preventDefault();
                onOpenNews(item.link);
              }}
              style={rowStyle(index)}
            >
              <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
                {item.title}
              </Text>
              <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
                {[sourceLabel(item), relativeTime(item.publishedAt, now)].filter((s) => s !== '').join(' · ')}
              </Text>
              <span
                style={{
                  display: 'inline-block',
                  marginTop: 6,
                  padding: '3px 8px',
                  borderRadius: 20,
                  background: 'var(--adaptiveGrey200, #E4DDD0)',
                  color: 'var(--adaptiveGrey700, #57534A)',
                  fontSize: 11,
                }}
              >
                내 책 · 『{item.bookTitle}』
              </span>
            </a>
          )}
        />
      ) : (
        <FeedList
          items={feed.social}
          expanded={expanded}
          empty={EMPTY_MESSAGE.social}
          onToggle={onToggle}
          row={(event: SocialEvent, index) => {
            const body = (
              <>
                <span
                  aria-hidden="true"
                  style={{
                    flex: '0 0 auto',
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 14,
                    background: 'var(--adaptiveGrey200, #E4DDD0)',
                  }}
                >
                  {EVENT_ICON[event.type]}
                </span>
                {/* 한글 문장이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 아이콘을 밀어낸다. */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
                    {eventLine(event)}
                  </Text>
                  {/* 말줄임은 서버가 이미 했다(80자) — 여기 clamp는 폭에 맞춘 마지막 한 겹이다. */}
                  {event.excerpt !== null && (
                    <Text
                      typography="st12"
                      color="grey600"
                      style={{
                        display: '-webkit-box',
                        WebkitLineClamp: 1,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        marginTop: 2,
                        wordBreak: 'keep-all',
                      }}
                    >
                      {event.excerpt}
                    </Text>
                  )}
                  <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
                    {relativeTime(event.occurredAt, now)}
                  </Text>
                </div>
              </>
            );
            const key = `${event.loginId}-${event.type}-${event.occurredAt}`;
            const rowLayout = { display: 'flex', gap: 10, alignItems: 'flex-start' } as const;

            // 여백 줄만 갈 곳이 있다 — 완독·시작은 열 화면이 없어 예전처럼 비클릭으로 남는다(죽은 UI 금지).
            return event.type === 'STORY' && event.bookId !== null ? (
              <button
                key={key}
                type="button"
                data-feed-row=""
                onClick={() => onOpenMargin(event.loginId, event.bookId!)}
                style={{ ...rowStyle(index), ...rowLayout }}
              >
                {body}
              </button>
            ) : (
              <div key={key} data-feed-row="" style={{ ...rowStyle(index), ...rowLayout, cursor: 'default' }}>
                {body}
              </div>
            );
          }}
        />
      )}
    </section>
  );
}

/**
 * 홈에 서는 피드 박스 — 자기 데이터를 자기가 받고 탭·펼침 상태를 든다.
 *
 * <p>실패는 박스 안 한 줄로 끝내고 **401만 밖으로 올린다**(`Social`의 `fail` 패턴) — 토큰이 폐기됐으면
 * 조용히 넘어갈 수 없고, 그 외 실패로 홈 전체를 깨뜨릴 이유도 없다.
 */
export function HomeFeedBox({
  onError,
  onOpenMargin,
}: {
  onError: (error: Error) => void;
  onOpenMargin: (loginId: string, bookId: number) => void;
}) {
  const [feed, setFeed] = useState<HomeFeedResponse | null>(null);
  const [tab, setTab] = useState<FeedTab>('social');
  const [expanded, setExpanded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchHomeFeed()
      .then(setFeed)
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else setError(e.message);
      });
    // 마운트 1회 — 홈은 탭을 오갈 때마다 재마운트되므로 그때 다시 받는다(별도 갱신 배선이 필요 없다).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <FeedBox
      feed={feed}
      tab={tab}
      expanded={expanded}
      error={error}
      now={Date.now()}
      onTab={(next) => {
        setTab(next);
        setExpanded(false); // 탭을 옮기면 다시 3줄부터 — 저쪽에서 펼친 상태가 이쪽에 옮아붙지 않는다
      }}
      onToggle={() => setExpanded(true)}
      onOpenNews={openExternal}
      onOpenMargin={onOpenMargin}
    />
  );
}
