import { Text } from '@toss/tds-mobile';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import type { HomeFeedResponse, NewsItem, ReaderStatus, SocialEvent } from '../api';
import { fetchHomeFeed } from '../api';
import { elapsedSeconds, formatDuration, objectParticle, relativeTime } from '../format';
import { openExternal } from '../toss';
import { BookCover, sectionStyle } from '../ui';

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

export type FeedTab = 'readers' | 'social' | 'news';

/** 글자 탭의 라벨. `readers`는 <b>이름표가 없다</b> — 사람 그림 하나로 선다. */
const TAB_LABEL: Record<Exclude<FeedTab, 'readers'>, string> = { social: '소식', news: '책 뉴스' };

/**
 * 처음 열리는 탭 — <b>맨 왼쪽이지만 기본은 「소식」이다.</b>
 *
 * <p>위치가 곧 기본값이 되면 팔로우 0명인 사람이 홈에 들어오자마자 빈 목록을 만나고, 기존 사용자의
 * 홈 경험도 예고 없이 바뀐다. 사람 탭은 왼쪽에 서서 <i>보이되</i>, 열리는 건 눌렀을 때다.
 */
export const DEFAULT_TAB: FeedTab = 'social';

/** 빈 상태 문구 — 셋 다 "여기가 무엇으로 채워지는 자리인가"를 말한다(소식은 소셜 탭 유도를 겸한다). */
export const EMPTY_MESSAGE: Record<FeedTab, string> = {
  readers: '팔로우한 사람의 독서가 여기에 보여요',
  social: '팔로우한 사람의 소식이 여기에 떠요',
  news: '완독한 책의 뉴스가 여기에 떠요',
};

/** 접힌 상태의 줄 수 — 폴드 아래 카드라 이보다 길면 홈이 피드에 잡아먹힌다. */
export const PREVIEW_COUNT = 3;

/**
 * 소식 종류를 한눈에 가르는 표지 — 문장을 읽기 전에 무슨 일인지 보인다.
 *
 * <p>이모지(`✅📖✍️`)를 쓰다 <b>자체 팔레트의 색 점</b>으로 바꿨다(2026-08-18). 흔한 기본 이모지는
 * 「AI가 만든 화면」이라는 인상을 줘서다. 다만 여기는 여러 줄이 세로로 쌓여 <b>훑어보는</b> 자리라
 * 아이콘이 제 값을 하던 유일한 곳이라, 지우는 대신 이 앱 색으로 대체했다 — 세이지(완독)·베이지(시작)·
 * 연보라(글)는 표지 색 팔레트와 같은 계열이다.
 */
/**
 * 사건 배지 — 소식 한 줄이 무슨 종류인지를 <b>글자로</b> 말한다.
 *
 * <p>전에는 7px 색점이 이 일을 했는데, 색만으로는 ① 완독과 읽기 시작이 <b>같은 무게</b>로 보이고
 * ② 색이 무슨 뜻인지 화면 어디에도 안 적혀 있었다. 이 앱에서 가장 축하할 사건이 나머지와 구별되지
 * 않는 게 문제의 핵심이라, <b>완독만 채운 배지</b>로 올리고 나머지는 눌러 둔다.
 *
 * <p>문구는 <b>서재가 이미 쓰는 말</b>(「다 읽음」·「읽는 중」)을 그대로 가져온다 — 같은 상태를 화면마다
 * 다른 이름으로 부르면 두 화면이 서로 다른 앱처럼 읽힌다. 여백은 문장과 같은 규칙으로 개수를 센다
 * (1장이면 안 세고, 2장 이상이면 묶인 이유가 드러나야 한다).
 */
export function eventBadge(event: SocialEvent): { label: string; tone: 'solid' | 'tint' | 'outline' } {
  if (event.type === 'STORY') {
    return { label: event.count > 1 ? `여백 ${event.count}` : '여백', tone: 'tint' };
  }
  return event.type === 'FINISHED'
    ? { label: '다 읽음', tone: 'solid' }
    : { label: '읽는 중', tone: 'outline' };
}

/**
 * 배지 세 톤 — <b>새 색을 하나도 만들지 않는다</b>. 채움은 세이지 700, 연한 채움은 이 파일이
 * 「내 책」 칩에 이미 쓰는 회갈색 짝(grey200/700), 외곽선은 카드 경계와 같은 선이다.
 */
const badgeStyle = (tone: 'solid' | 'tint' | 'outline') =>
  ({
    display: 'inline-block',
    padding: '1px 7px',
    borderRadius: 5,
    fontSize: 12,
    lineHeight: 1.6,
    ...(tone === 'solid'
      ? { background: 'var(--adaptiveBlue700, #4F6B4C)', color: 'var(--adaptiveGrey100, #FCFAF5)' }
      : tone === 'tint'
        ? { background: 'var(--adaptiveGrey200, #E4DDD0)', color: 'var(--adaptiveGrey700, #57534A)' }
        : {
            background: 'transparent',
            color: 'var(--adaptiveGrey600, #6F6A5E)',
            border: '1px solid var(--adaptiveGrey200, #E4DDD0)',
          }),
  }) as const;

/**
 * 그릴 탭 머리 — 뉴스가 꺼져 있으면(수집기 키 미설정) **「책 뉴스」 머리 자체를 안 그린다.**
 * 눌러도 늘 빈 탭은 죽은 탭이라, 있는 것보다 없는 게 낫다.
 *
 * <p>사람 탭은 **맨 왼쪽에 항상** 선다. 「소식」·「책 뉴스」가 *읽을 거리*라면 이쪽은 *사람*이라,
 * 위치와 구분선이 그 다름을 말한다. 팔로우가 0명이어도 그린다 — 빈 상태 문구가 "여기가 무엇으로
 * 채워지는 자리인지"를 알려 주는 진입점이라, 뉴스 탭과 달리 <b>죽은 탭이 아니다</b>.
 */
export function visibleTabs(newsEnabled: boolean): FeedTab[] {
  return newsEnabled ? ['readers', 'social', 'news'] : ['readers', 'social'];
}

/**
 * 「함께 읽는 사람」 한 줄의 문장.
 *
 * <p>읽는 중이 다른 무엇보다 앞선다(과거 기록이 있어도). 그 밖에는 <b>사실만 적는다</b> —
 * 「3일째 안 읽었어요」가 아니라 「『책』 · 3일 전」이다. 판단을 담지 않으려는 것이기도 하지만,
 * 무엇보다 <b>비공개 책만 읽는 사람에겐 「안 읽었다」가 거짓</b>이기 때문이다. 같은 이유로 기록이
 * 없을 땐 「공개된」을 밝혀, 조용한 사람을 안 읽는 사람으로 만들지 않는다.
 *
 * <p>과거 기록도 <b>책 제목으로 시작한다</b> — 읽는 중 줄과 같은 자리에 서서 목록을 훑을 때
 * 이름 아래로 책이 쭉 읽힌다. 제목 없이 시각만 오면 옛 문구로 떨어진다(서버가 둘을 한 행으로
 * 뽑아 그 조합은 안 생기지만, 계약상 null이 가능한 한 그릴 수는 있어야 한다).
 */
export function readerStatusLine(reader: ReaderStatus, now: number): string {
  if (reader.readingBookTitle !== null) {
    return `『${reader.readingBookTitle}』 읽는 중`;
  }
  if (reader.lastReadAt !== null) {
    const when = relativeTime(reader.lastReadAt, now);
    return reader.lastReadBookTitle !== null ? `『${reader.lastReadBookTitle}』 · ${when}` : `마지막 기록 ${when}`;
  }
  return '공개된 기록이 없어요';
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
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  }) as const;

/**
 * 사람 탭 알약 — 그림 하나뿐이라 좌우 여백만 좁힌다. 세로 크기는 글자 탭과 같게 맞춘다
 * (혼자 커지면 「탭이 아니라 다른 버튼」으로 읽혀, 구분선이 하려던 말과 겹쳐 과해진다).
 */
const iconPillStyle = (active: boolean) =>
  ({ ...pillStyle(active), padding: '6px 11px', display: 'inline-flex', alignItems: 'center' }) as const;

/** 사람 탭과 글자 탭 사이의 세로 한 획 — 「같은 줄이지만 같은 종류가 아니다」. */
const tabSplitStyle = {
  flex: '0 0 auto',
  width: 1,
  height: 17,
  margin: '0 5px',
  background: 'var(--adaptiveGrey200, #E4DDD0)',
} as const;

/**
 * 사람 그림 — 채운 실루엣이 아니라 <b>획</b>이다(머리 원 + 어깨 곡선).
 * 이 앱은 종이와 연필이 컨셉이라 연필 테두리·손글씨체와 같은 굵기로 읽혀야 한다.
 */
function PersonMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 20 20" fill="none" aria-hidden="true">
      <circle cx="10" cy="7" r="3.2" stroke="currentColor" strokeWidth="1.6" />
      <path d="M4.2 16.6c0-3.2 2.6-5.3 5.8-5.3s5.8 2.1 5.8 5.3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

/** 읽는 중 표식 — 아바타 모서리의 <b>정지된</b> 점. 깜빡이지 않는다(T-176: 아바타를 감싼 애니메이션 금지). */
const liveDotStyle = {
  position: 'absolute',
  right: -1,
  bottom: -1,
  width: 10,
  height: 10,
  borderRadius: '50%',
  background: 'var(--adaptiveBlue500, #6E8A6A)',
  border: '2px solid var(--adaptiveGrey100, #FCFAF5)',
} as const;

/** 맞팔 배지 — 친구 시스템을 새로 만들지 않고 「친구감」만 가져오는 자리. */
const mutualBadgeStyle = {
  flex: '0 0 auto',
  padding: '0 5px 1px',
  border: '0.5px solid var(--adaptiveGrey200, #E4DDD0)',
  borderRadius: 6,
  color: 'var(--adaptiveGrey600, #6F6A5E)',
  fontSize: 12,
  lineHeight: 1.5,
} as const;

/**
 * 「함께 읽는 사람」 한 줄 — 아바타 · 닉네임(+맞팔) · 상태 문장 · 경과.
 *
 * <p>정적 렌더 하니스가 닿도록 밖으로 뺐다(`Library.SearchResultRow` 선례). 갈 곳이 없어
 * 비클릭 `div`다 — 남의 책방으로 보내려면 탭 전환 배선이 필요한데, 그건 별건이다(죽은 링크 금지).
 */
export function ReaderRow({ reader, index, now }: { reader: ReaderStatus; index: number; now: number }) {
  const reading = reader.readingSince !== null;

  return (
    <div data-feed-row="" style={{ ...rowStyle(index), display: 'flex', gap: 10, alignItems: 'flex-start' }}>
      <span
        style={{
          position: 'relative',
          flex: '0 0 auto',
          width: 34,
          height: 34,
          borderRadius: '50%',
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: reading ? 'var(--adaptiveBlue50, #E7EEE2)' : 'var(--adaptiveGrey200, #E4DDD0)',
          color: reading ? 'var(--adaptiveBlue700, #4F6B4C)' : 'var(--adaptiveGrey600, #6F6A5E)',
          fontSize: 15,
          // 기록이 없는 사람은 문구로 한 번, 형태로 한 번 — 훑어도 읽힌다.
          opacity: !reading && reader.lastReadAt === null ? 0.45 : 1,
        }}
      >
        {reader.nickname.charAt(0)}
        {reading && <span style={liveDotStyle} />}
      </span>

      {/* 한글 문장이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 아바타를 밀어낸다. */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          <Text typography="st11">{reader.nickname}</Text>
          {reader.mutual && <span style={mutualBadgeStyle}>서로</span>}
        </span>
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 1, wordBreak: 'keep-all' }}>
          {readerStatusLine(reader, now)}
        </Text>
      </div>

      {/* 경과는 읽는 중일 때만 — 그 밖에는 본문이 이미 시점을 말해 같은 말이 두 번 된다. */}
      {reading && (
        <span
          style={{
            flex: '0 0 auto',
            alignSelf: 'center',
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--adaptiveBlue700, #4F6B4C)',
          }}
        >
          {formatDuration(elapsedSeconds(reader.readingSince!, now))}째
        </span>
      )}
    </div>
  );
}

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
              fontSize: 14,
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
      <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 10 }}>
        {visibleTabs(feed?.newsEnabled ?? false).map((key) => (
          <Fragment key={key}>
            <button
              type="button"
              data-feed-tab={key}
              // 그림뿐인 탭은 접근성 이름이 없으면 스크린리더에 무명 버튼이 된다.
              aria-label={key === 'readers' ? '함께 읽는 사람' : undefined}
              aria-current={key === tab ? 'true' : undefined}
              onClick={() => onTab(key)}
              style={key === 'readers' ? iconPillStyle(key === tab) : pillStyle(key === tab)}
            >
              {key === 'readers' ? <PersonMark /> : TAB_LABEL[key]}
            </button>
            {key === 'readers' && <span data-tab-split="" aria-hidden="true" style={tabSplitStyle} />}
          </Fragment>
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
      ) : tab === 'readers' ? (
        <FeedList
          // `?? []` — 이 필드를 아직 안 내려주는 서버와도 붙는다(배포 순서에 화면이 의존하지 않는다).
          items={feed.readers ?? []}
          expanded={expanded}
          empty={EMPTY_MESSAGE.readers}
          onToggle={onToggle}
          row={(reader: ReaderStatus, index) => (
            <ReaderRow key={reader.loginId} reader={reader} index={index} now={now} />
          )}
        />
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
                  fontSize: 12,
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
                {/* 표지가 이 줄의 첫 신호다 — 문장을 읽기 전에 「무슨 책」이 먼저 보인다.
                    표지가 없는 책은 BookCover가 첫 글자 자리 표지로 떨어뜨린다(제목색이라 책마다 다르다). */}
                <BookCover url={event.coverUrl} title={event.bookTitle} width={30} />
                {/* 한글 문장이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 표지를 밀어낸다. */}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <span style={badgeStyle(eventBadge(event).tone)}>{eventBadge(event).label}</span>
                  <Text typography="st11" style={{ display: 'block', marginTop: 3, wordBreak: 'keep-all' }}>
                    {eventLine(event)}
                  </Text>
                  {/* 말줄임은 서버가 이미 했다(80자) — 여기 clamp는 폭에 맞춘 마지막 한 겹이다. */}
                  {/* 남의 글은 세로선 안으로 들여 「인용」임을 형태로 말한다 — 그 전에는 문장·발췌·시각
                      세 줄이 같은 들여쓰기로 쌓여 어디까지가 한 덩어리인지 안 보였다. 여백 카드의
                      인용선(Story.tsx)과 같은 문법이라 화면에 새 규칙이 늘지 않는다. */}
                  {event.excerpt !== null && (
                    <Text
                      typography="st12"
                      color="grey600"
                      style={{
                        display: '-webkit-box',
                        WebkitLineClamp: 1,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        marginTop: 4,
                        paddingLeft: 9,
                        borderLeft: '2px solid var(--adaptiveGrey200, #E4DDD0)',
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
  const [tab, setTab] = useState<FeedTab>(DEFAULT_TAB);
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
