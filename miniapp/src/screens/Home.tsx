import { Button, ProgressBar, Text } from '@toss/tds-mobile';
import type { CSSProperties, ReactNode } from 'react';
import { useEffect, useRef, useState } from 'react';

import type { BookOption, DashboardResponse, TimerState, WaiveResponse } from '../api';
import { ApiError, waiveDebt } from '../api';
import { Coachmark } from '../coachmark';
import { elapsedSeconds, formatClock, formatDuration } from '../format';
import {
  GOAL_MET_TEMPLATE_CODE,
  REWARD_AD_GROUP_ID,
  notificationAgreementSupported,
  requestNotificationAgreement,
  watchRewardAd,
} from '../toss';
import {
  Avatar,
  BookCover,
  CoverInitial,
  ErrorMessage,
  PENCIL_FRAME,
  SERIF_VALUE,
  Screen,
  SectionTitle,
  Sheet,
  sectionStyle,
} from '../ui';
import { HomeFeedBox } from './HomeFeed';

/** 알림 동의 결과 캐시 — 값은 토스가 준 결과 문자열 그대로. 정본은 토스이고 이건 카드 노출 스위치일 뿐이다. */
const AGREEMENT_KEY = 'booktimer.notificationAgreement';

/**
 * 진행바 색 — `global.css`가 TDS `--adaptiveBlue500`을 이 세이지로 재테마한다. TDS ProgressBar는
 * 색을 prop으로만 받아 CSS 변수가 안 닿으므로 값을 직접 준다(다른 초록을 쓰면 화면에 초록이 둘이 된다).
 */
const SAGE = '#6E8A6A';

/**
 * 측정 중 안심 문구 — 이 앱의 핵심 계약은 "화면을 꺼도 서버가 센다"인데 측정 중 화면에 그 말이 없었다.
 * 첫 세션에 한정하지 않는다: 짧고 무해하며, 잊어버리는 건 신규 유저만이 아니다.
 */
export const ACTIVE_SESSION_RELIEF = '화면을 꺼도 측정은 계속돼요. 책 읽고 오세요 🌿';

/**
 * 독서등이 켜져도 <b>밝게 남는 자리</b>의 표식 — 히어로 카드 하나뿐이다.
 *
 * <p>이 문자열이 js와 `global.css`를 잇는 매듭이라 한쪽만 고치면 카드가 밤 속으로 사라진다
 * (`home.test.tsx`가 css에 셀렉터가 실재하는지 본다). 스위치는 `App`의 {@link LAMP_CLASS}.
 */
export const LAMP_PAGE_CLASS = 'lamp-page';

/**
 * 첫 완료 축하 배너 — 서버 `firstCompletedSession`이 참인 그 한 번만. 잔디는 1초만 읽어도 lv1로
 * 점등되는데(`ContributionGraphBuilder.levelFor`) 그걸 보여 줄 자리가 홈에 없다 — 잔디 미리보기가
 * 피드 박스에 자리를 내줬으므로 **기록 탭**을 가리킨다(하이라이트 테두리는 가리킬 카드와 함께 사라졌다).
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다 — 하니스가 정적 렌더라 「측정 끝내기」를 눌러 켜진 상태에
 * 도달할 수 없다(`BookSheet`와 같은 처지).
 */
export function FirstSessionBanner({ show }: { show: boolean }) {
  if (!show) return null;

  return (
    <div style={{ marginTop: 12, padding: 14, borderRadius: 12, background: '#EFF3EE', textAlign: 'center' }}>
      <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
        🌱 첫 독서 기록이 심어졌어요! 기록 탭에 첫 칸이 생겼어요.
      </Text>
    </div>
  );
}

/**
 * 처음 골라 둘 책 — 최근 읽은 책(=이어 읽기)이 읽는 중 목록에 있으면 그 책, 아니면 첫 책, 없으면 `null`.
 *
 * <p>웹 `BookPickForm`의 `defaultBook`과 같은 규칙이다. `recentBookId`가 목록 밖일 수 있는 건 그 책을
 * 다 읽었거나 뺐기 때문이다 — 그때 아무것도 안 고른 채로 두면 "측정 시작"이 죽은 버튼이 된다.
 */
export function defaultBookId(readingBooks: BookOption[], recentBookId: number | null): number | null {
  return readingBooks.find((b) => b.id === recentBookId)?.id ?? readingBooks[0]?.id ?? null;
}

/**
 * 홈 여백 문이 가리키는 책 — 없으면 `null`(문을 그리지 않는다).
 *
 * <p>「측정 시작」을 누르듯 <b>1탭에 작성 화면</b>으로 보내려면 지금 이 화면이 어느 책을 뜻하는지가
 * 먼저 정해져야 한다: 측정 중이면 그 책, 대기 중이면 캐러셀에서 고른 책이다. 활성 책 id는 대시보드에
 * 따로 없지만 `recentBookId`가 `startedAt desc`(활성 세션 포함)라 책을 걸고 측정 중이면 그게 활성 책이고,
 * 책 없이 측정 중인 경우는 `activeBookTitle === null`이 걸러 낸다.
 *
 * <p><b>공개 여부는 조건이 아니다</b> — 비공개 책의 여백은 나만 보는 메모다(설계 결정 2). 반면
 * 핸들(@아이디)이 없으면 그린다 해도 열리지 않는다: 서버가 여백 대상을 loginId로만 찾아 자기 여백에도
 * 닿을 수 없다(결정 A — 핸들 안내는 책방 탭 「아이디 만들기」가 이미 맡는다).
 */
export function marginDoorBook(dashboard: DashboardResponse, selectedBookId: number | null): BookOption | null {
  if (dashboard.loginId === null) return null;
  // 완독 책까지 보는 이유: 웹에선 다 읽은 책으로도 측정을 시작할 수 있어 활성 책이 그쪽에만 있을 수 있다.
  const pool = [...dashboard.readingBooks, ...dashboard.finishedBooks];
  const id = dashboard.hasActiveSession
    ? (dashboard.activeBookTitle === null ? null : dashboard.recentBookId)
    : selectedBookId;
  return pool.find((b) => b.id === id) ?? null;
}

/** 캐러셀 표지 한 장의 폭·간격 — 계산(가운데 인덱스)과 스타일이 같은 값을 봐야 스냅과 선택이 어긋나지 않는다. */
export const COVER_WIDTH = 84;
export const COVER_GAP = 16;

/** 표지 한 장의 높이 — `BookCover`·`CoverInitial`이 쓰는 식(폭 × 1.4) 그대로여야 여백 계산이 실제 표지를 따라간다. */
export const COVER_HEIGHT = Math.round(COVER_WIDTH * 1.4);

/**
 * 트랙 세로 여백 — 아래 선택 표지의 `scale(1.1)`이 위아래로 각각 높이의 **0.05**만큼 삐져나가므로
 * 그 몫(+ 그림자 여유 2px)을 여백으로 미리 확보한다. 여기가 모자라면 커진 표지가 트랙을 세로로 넘쳐
 * 손가락에 위아래로 들썩인다(그래서 아래 `overflowY: 'hidden'`과 한 쌍이다).
 */
export const TRACK_V_PAD = Math.ceil(COVER_HEIGHT * 0.05) + 2;

/**
 * 첫·마지막 표지를 가운데까지 올려 주는 여백 — **트랙의 padding이 아니라 양끝 표지의 margin으로 준다.**
 *
 * <p>WebKit(iOS 웹뷰)은 스크롤 컨테이너의 **끝쪽 padding을 스크롤 영역에서 뺀다**. 그래서 padding으로 주면
 * 표지가 적을수록 밀 여지가 통째로 사라진다 — 폭 390 화면에서 2권이면 여지가 0이라 아예 안 밀렸고(실기기
 * 실측) 4권이면 절반만 밀렸다. flex 아이템의 margin은 어느 엔진에서나 스크롤 영역에 들어간다.
 *
 * <p>값이 같으니 i번째 표지가 가운데 오는 위치는 그대로 `i * (표지폭 + 간격)`이다.
 */
export const EDGE_SPACE = `calc(50% - ${COVER_WIDTH / 2}px)`;

/**
 * 스크롤 위치 → 가운데 온 책의 인덱스.
 *
 * <p>트랙 좌우에 `50% - 표지폭/2`의 여백을 둬서 i번째 표지가 가운데 오는 위치가 정확히
 * `i * (표지폭 + 간격)`이 된다 — 그래서 화면 폭이 이 식에서 빠진다(여백이 이미 그 몫을 했다).
 *
 * <p>양끝은 클램프한다: iOS 바운스로 음수가, 관성 스크롤로 마지막 칸을 넘긴 값이 들어오는데
 * 그대로 인덱스로 쓰면 목록 밖(`undefined`)을 찌른다.
 */
export function centeredIndex(scrollLeft: number, itemWidth: number, gap: number, count: number): number {
  const index = Math.round(scrollLeft / (itemWidth + gap));
  return Math.min(Math.max(index, 0), Math.max(count - 1, 0));
}

/**
 * 미끄러지는 이동을 CSS에 맡긴다 — `scrollTo({behavior})`는 옵션 객체를 통째로 무시하는 웹뷰가 있어
 * 실기기에서 표지가 제자리에 머물렀다. `scrollLeft` 대입은 어디서나 먹고, 애니메이션은 이 값이 맡는다.
 */
const TRACK_SCROLL_BEHAVIOR = 'smooth';

/**
 * 표지 하나를 가운데로 — 가운데 정렬 여백 덕에 목표 위치가 곧 `index * stride`다.
 *
 * <p>`instant`면 CSS 애니메이션을 잠시 꺼서 즉시 옮긴다(첫 진입은 처음부터 거기 있었던 것처럼 보여야 한다).
 */
function scrollToIndex(track: HTMLDivElement | null, index: number, instant = false): void {
  if (track === null) return;
  if (instant) track.style.scrollBehavior = 'auto';
  track.scrollLeft = index * (COVER_WIDTH + COVER_GAP);
  if (instant) track.style.scrollBehavior = TRACK_SCROLL_BEHAVIOR;
}

/**
 * 선택값 → 캐러셀 칸 인덱스. **0번은 언제나 「책 없이」**라 책은 한 칸씩 밀린다.
 *
 * <p>목록 밖 id(다 읽었거나 뺀 책이 stale하게 남는 경우)는 0번으로 떨어진다 — 화면 밖을 찌르느니
 * 「책 없이」가 가운데 온 상태가 정직하다(탭바 액션도 그때 `startSession(null)`을 보낸다 — `timerStartBookId`).
 */
export function carouselIndexOf(selectedId: number | null, books: BookOption[], offset = 1): number {
  if (selectedId === null) return 0;
  const index = books.findIndex((b) => b.id === selectedId);
  return index >= 0 ? index + offset : 0;
}

/**
 * 캐러셀 칸 인덱스 → 선택값. 0번(또는 범위 밖 방어)이면 `null` = 책 없이.
 *
 * <p>`offset`은 책 앞에 붙는 칸 수다 — 홈은 「책 없이」가 0번이라 1, 「책 없이」가 없는 서재는 0.
 */
export function selectionAt(index: number, books: BookOption[], offset = 1): number | null {
  return books[index - offset]?.id ?? null;
}

/**
 * 밖에서 바뀐 선택을 따라갈 칸 — 옮길 필요가 없으면 `null`.
 *
 * <p>트랙을 옮기는 길이 마운트 1회와 표지 탭뿐이라, 「펼쳐보기」 격자처럼 **밖에서** 선택이 바뀌면
 * 제목 줄만 그 책으로 갈리고 표지는 옛 자리에 남았다. 그 갈림을 여기서 메운다.
 *
 * <p>이미 그 칸이 가운데면 `null`이다 — 손가락으로 민 결과(스냅이 끝난 자리)에 또 스크롤을 걸면
 * 재스냅이 손가락에서 트랙을 빼앗는다(`SCROLL_SETTLE_MS` 주석과 같은 사고).
 */
export function recenterIndex(
  scrollLeft: number,
  selectedId: number | null,
  books: BookOption[],
  offset = 1,
): number | null {
  const target = carouselIndexOf(selectedId, books, offset);
  const current = centeredIndex(scrollLeft, COVER_WIDTH, COVER_GAP, books.length + offset);
  return current === target ? null : target;
}

/**
 * 「책 없이」 카드 아래 부제 — 0권 사용자에게는 이 한 줄이 서재로 가는 유일한 안내다
 * (「첫 책 추가하기」 빈 상태가 캐러셀에 흡수되며 사라졌고, 진입은 하단 탭바가 맡는다).
 */
export function noBookSubtitle(bookCount: number): string {
  return bookCount > 0 ? '기록에 책이 남지 않아요' : '서재에 책을 추가하면 여기서 골라요';
}

/**
 * 스크롤이 멎었다고 보는 시간 — 미는 도중에 선택을 갱신하면 그 리렌더가 `scroll-snap: mandatory`의
 * 재스냅을 불러 손가락에서 스크롤을 빼앗는다(실기기에서 좌우로 밀리지 않던 원인). 손을 뗀 뒤 한 번만 고른다.
 */
export const SCROLL_SETTLE_MS = 120;

/**
 * 「읽는 중」 카드의 표지 폭 — 캐러셀(84)보다 작다. 그곳은 고르는 무대라 크게 서야 하지만,
 * 여기는 이미 정해진 한 권을 한 줄로 알려 주는 자리라 제목·저자와 높이가 맞아야 한다.
 */
export const READING_NOW_COVER = 52;

/**
 * 캐러셀 0번 칸의 「책 없이」 자리 표지 — 표지와 **같은 크기의 점선 상자**다.
 *
 * <p>`CoverInitial`을 쓰지 않는다: 색 상자 + 첫 글자라 실제 표지와 구분이 안 돼 "책 없이"가 책처럼 보인다.
 * `boxSizing`이 없으면 테두리 2px이 칸을 불려 스냅 위치(`i × stride`)가 이 카드부터 어긋난다.
 */
function NoBookCard({ width = COVER_WIDTH }: { width?: number } = {}) {
  return (
    <div
      style={{
        width,
        height: Math.round(width * 1.4),
        flex: '0 0 auto',
        boxSizing: 'border-box',
        // 토큰이라야 독서등(밤)에서 이 점선도 함께 어두워진다 — 리터럴이면 밤 종이 위에 낮의 선이 뜬다.
        border: '2px dashed var(--adaptiveGrey200, #E4DDD0)',
        borderRadius: 4,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 4,
      }}
    >
      {/* 점선 상자가 이미 「표지가 아님」을 말한다 — 시계 이모지는 그 위에 얹힌 군더더기였다(2026-08-18). */}
      <Text typography="st12" color="grey600">
        책 없이
      </Text>
    </div>
  );
}

/**
 * 읽는 중 책 표지 캐러셀 — **가운데 온 것이 곧 측정 대상**이다.
 *
 * <p>칩 + 「바꾸기」 시트를 대신한다: 고르는 일이 시트를 여닫는 2단계에서 좌우로 미는 1단계가 되고,
 * 표지가 그대로 나오니 무슨 책인지 글자보다 빨리 읽힌다. 표지가 없는 책도 같은 크기 자리 표지로
 * 서서 줄이 무너지지 않는다.
 *
 * <p>**0번 칸은 언제나 「책 없이」 카드**다 — 보조 CTA로 떠 있던 그 갈래가 여기로 들어왔다.
 * 버튼 갈래로 두면 "가운데 온 것이 측정 대상"이라는 단일 문법이 깨지고, 책 0권 사용자에게는
 * 캐러셀 자리가 통째로 다른 화면(빈 상태)이 됐다.
 *
 * <p>선택 상태는 밖(App)이 들고 있다 — 탭바의 측정 액션이 같은 값을 써야 캐러셀과 시작 대상이 어긋나지 않는다.
 *
 * <p>서재도 같은 캐러셀을 쓴다(세로로 길던 3섹션 목록을 대체) — 다만 거기선 고를 대상이 책뿐이라
 * `noBookCard`가 꺼지고, 아래 한 줄에 읽은 시간·공개 여부까지 실으므로 `metaOf`로 그 줄을 바꿔 끼운다.
 */
export function BookCarousel<T extends BookOption>({
  books,
  selectedId,
  onSelect,
  noBookCard = true,
  metaOf,
  chipsOf,
}: {
  books: T[];
  selectedId: number | null;
  onSelect: (bookId: number | null) => void;
  /** 0번 「책 없이」 칸을 세울지 — 측정 대상을 고르는 홈만 켠다. */
  noBookCard?: boolean;
  /** 제목 아래 한 줄 — 기본은 저자. */
  metaOf?: (book: T) => string;
  /**
   * 메타 아래 칩 줄 — 서재가 읽은 시간·공개 여부를 여기로 보낸다. 홈은 안 넘긴다(측정할 책을 <b>고르는</b>
   * 자리에서는 공개 여부가 군더더기다). 스타일까지 호출부가 정해 보내므로 캐러셀은 톤을 몰라도 된다.
   */
  chipsOf?: (book: T) => { label: string; style: CSSProperties }[];
}) {
  const trackRef = useRef<HTMLDivElement>(null);
  const settleRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const selected = books.find((b) => b.id === selectedId) ?? null;
  /** 홈은 0번이 「책 없이」(`null`)라 책이 한 칸 밀리고, 서재는 책이 곧 0번이다. */
  const offset = noBookCard ? 1 : 0;
  const items: (T | null)[] = noBookCard ? [null, ...books] : books;

  // 첫 진입 — 기본 선택(이어 읽기)이 처음부터 가운데였던 것처럼 즉시 이동한다(애니메이션은 거짓 움직임이다).
  useEffect(() => {
    scrollToIndex(trackRef.current, carouselIndexOf(selectedId, books, offset), true);
    // 언마운트되며 남은 타이머가 사라진 화면의 선택을 건드리지 않게 한다.
    return () => {
      if (settleRef.current !== null) clearTimeout(settleRef.current);
    };
    // 마운트 1회 — 이후 위치는 손가락(스크롤)과 탭, 그리고 아래 동기화가 정한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 밖에서 선택이 바뀌면(「펼쳐보기」 격자에서 고르기 등) 트랙도 그 책으로 옮긴다 —
  // 손가락으로 민 결과라면 이미 그 자리라 `recenterIndex`가 null을 줘 아무 일도 하지 않는다.
  useEffect(() => {
    const track = trackRef.current;
    if (track === null) return;
    const index = recenterIndex(track.scrollLeft, selectedId, books, offset);
    if (index !== null) scrollToIndex(track, index);
    // 위치를 정하는 건 선택값이다 — books 변동은 선택이 따라 바뀌며 함께 들어온다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId]);

  return (
    <>
      <div
        ref={trackRef}
        onScroll={(e) => {
          const { scrollLeft } = e.currentTarget;
          if (settleRef.current !== null) clearTimeout(settleRef.current);
          settleRef.current = setTimeout(() => {
            const picked = selectionAt(
              centeredIndex(scrollLeft, COVER_WIDTH, COVER_GAP, items.length),
              books,
              offset,
            );
            if (picked !== selectedId) onSelect(picked);
          }, SCROLL_SETTLE_MS);
        }}
        style={{
          display: 'flex',
          gap: COVER_GAP,
          overflowX: 'auto',
          // 세로는 `overflow-y: hidden`이 아니라 여기서 막는다 — 그 값을 만나면 웹뷰가 이 트랙의
          // 터치 스크롤을 통째로 죽여 좌우로도 밀리지 않았다. 세로로 넘칠 여지는 이미 패딩이 0으로 만들어 뒀다.
          touchAction: 'pan-x',
          scrollBehavior: TRACK_SCROLL_BEHAVIOR,
          scrollSnapType: 'x mandatory',
          // 세로 여백만 여기서 — 좌우(가운데 정렬) 여백은 양끝 표지의 margin이 맡는다(EDGE_SPACE 주석).
          padding: `${TRACK_V_PAD}px 0`,
          scrollbarWidth: 'none',
        }}
      >
        {items.map((item, index) => {
          const current = (item?.id ?? null) === selectedId;
          return (
            <button
              key={item?.id ?? 'no-book'}
              type="button"
              aria-label={item?.title ?? '책 없이 측정'}
              aria-current={current ? 'true' : undefined}
              // 계측용 표지 — TDS emotion 클래스 사이에서 "표지가 몇 장이고 어떤 책인가"를 집을 손잡이가 없다.
              // 「책 없이」 카드는 따로 표시해 `data-cover-title`이 계속 "실제 책 목록"만 뜻하게 둔다.
              data-cover-title={item?.title}
              data-no-book-card={item === null ? '' : undefined}
              onClick={() => {
                onSelect(item?.id ?? null);
                scrollToIndex(trackRef.current, index);
              }}
              style={{
                flex: '0 0 auto',
                scrollSnapAlign: 'center',
                // 양끝 칸만 화면 절반만큼 밀어 둔다 — 이 여백이 첫·마지막 칸을 가운데까지 데려온다.
                // (책 0권이면 카드 한 장이 처음이자 마지막이라 양쪽 다 붙는다.)
                marginLeft: index === 0 ? EDGE_SPACE : undefined,
                marginRight: index === items.length - 1 ? EDGE_SPACE : undefined,
                padding: 0,
                border: 'none',
                background: 'transparent',
                cursor: 'pointer',
                // 가운데 온 칸만 또렷하게 — 스냅 위치를 색·크기로도 말해 준다.
                transform: current ? 'scale(1.1)' : 'scale(1)',
                opacity: current ? 1 : 0.45,
                transition: 'transform 0.2s ease, opacity 0.2s ease',
              }}
            >
              {item === null ? (
                <NoBookCard />
              ) : (
                // 표지 없음·로드 실패 분기는 BookCover가 든다 — title을 주면 첫 글자 + 제목색으로 떨어진다.
                <BookCover url={item.coverUrl} title={item.title} width={COVER_WIDTH} eager />
              )}
            </button>
          );
        })}
      </div>

      {/* 표지만으론 무슨 책인지 확정되지 않는다(비슷한 표지·자리 표지) — 가운데 온 것을 글자로 못 박는다. */}
      <div
        data-selected-book={selected?.title ?? '책 없이 측정'}
        style={{ marginTop: 12, textAlign: 'center' }}
      >
        <Text typography="st10" fontWeight="bold" style={{ display: 'block', wordBreak: 'keep-all' }}>
          {selected?.title ?? '책 없이 측정'}
        </Text>
        {selected === null ? (
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
            {noBookSubtitle(books.length)}
          </Text>
        ) : (
          (metaOf?.(selected) ?? selected.author) !== null && (
            // 한 줄 = 저자다. 서재가 「읽은 시간 · 공개」를 줄바꿈으로 여기 얹던 시절엔 `pre-line`이
            // 필요했는데, 그 둘이 아래 칩 줄로 나가면서 이 자리는 다시 한 줄짜리가 됐다.
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {metaOf?.(selected) ?? selected.author}
            </Text>
          )
        )}
        {selected !== null && chipsOf !== undefined && (
          <div style={{ display: 'flex', justifyContent: 'center', flexWrap: 'wrap', gap: 5, marginTop: 6 }}>
            {chipsOf(selected).map(({ label, style }) => (
              <span key={label} data-book-chip="" style={style}>
                {label}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* 몇 칸 중 몇 번째인지 — 표지가 화면 밖으로 잘려 있으면 목록의 크기가 안 보인다. */}
      <div aria-hidden="true" style={{ display: 'flex', justifyContent: 'center', gap: 6, marginTop: 10 }}>
        {items.map((item) => {
          const current = (item?.id ?? null) === selectedId;
          return (
            <span
              key={item?.id ?? 'no-book'}
              data-dot={current ? 'active' : 'idle'}
              style={{
                width: 6,
                height: 6,
                borderRadius: '50%',
                background: current ? SAGE : '#E4DDD0',
              }}
            />
          );
        })}
      </div>
    </>
  );
}

/**
 * 히어로 파생값 — 웹 `frontend/src/dashboard/timerProgress.ts`의 `computeProgress`를 옮겼다.
 *
 * <p>웹은 UX 리뷰로 "오늘 남은 시간" 카운트다운을 **"오늘 읽은 시간" 카운트업**으로 뒤집었다(성취를
 * 세지, 빚을 세지 않는다). 남은 시간은 보조 메타로 강등된다. 미니앱도 같은 프레이밍을 쓴다.
 *
 * <p>서버는 스냅샷만 주므로 측정 중 라이브 값은 `remainingSeconds - elapsed`로 만든다 — 기존 elapsed
 * 인터벌이 그대로 카운트업의 동력이 되어 tick을 따로 두지 않는다. `carryover`면 밀린 시간은 오늘 몫이
 * 아니라 바닥(floor)이라 빼고 센다(그래야 어제 빚이 오늘 성취를 갉아먹지 않는다).
 *
 * <p>목표 미설정(0)이면 나눌 게 없어 `progress`는 `null`(게이지를 안 그린다)이고 달성이라 우기지도
 * 않는다 — 웹은 이 경우를 100% 달성으로 치지만, 미니앱은 목표 설정으로 유도하는 자리라 그대로 둔다.
 *
 * <p>**게이지의 최대치는 목표가 아니라 「목표 + 밀린 시간」**이다 — 이월 중이라면 오늘 실제로 채워야
 * 하는 양이 그거고, 목표만 분모로 쓰면 게이지가 꽉 찼는데도 남은 시간이 있는 상태가 생긴다.
 * 그래서 `remaining`은 곧 서버가 준 총 남은 시간(`remainingSeconds - elapsed`)이다 —
 * `목표 + 밀린 − 읽은` 을 다시 계산할 필요가 없다(항등식이라 값이 어긋날 자리도 없다).
 *
 * <p>반면 **`achieved`는 여전히 목표만 본다**(결정 2026-08-14). 밀린 시간은 벌이 아니라 이월이라,
 * 빚이 남았다고 오늘 목표 달성 축하를 미루면 톤이 차가워진다 — 그래서 「목표는 달성, 게이지는 아직」인
 * 구간이 의도적으로 존재한다. 그 구간에서 남은 몫은 아래 「남은시간」 줄이 말한다.
 */
export function todayProgress(
  timer: Pick<TimerState, 'remainingSeconds' | 'carriedDebtSeconds' | 'todayGoalSeconds' | 'carryover'>,
  elapsed: number,
): { todayRead: number; remaining: number; overflow: number; progress: number | null; achieved: boolean } {
  const { carriedDebtSeconds: floor, todayGoalSeconds: goal, carryover } = timer;
  const remainingNow = timer.remainingSeconds - elapsed;
  const todayDebt = carryover ? remainingNow - floor : remainingNow;
  // 스냅샷이 어긋나도 음수 시간이 화면에 뜨지 않게 바닥을 친다(표시용 값이라 여기서 자른다).
  const todayRead = Math.max(0, goal - todayDebt);
  const target = goal + (carryover ? floor : 0); // 오늘 채워야 할 총량 = 게이지 최대치
  return {
    todayRead,
    remaining: Math.max(0, remainingNow),
    overflow: Math.max(0, todayRead - goal),
    progress: target > 0 ? Math.min(1, todayRead / target) : null,
    achieved: goal > 0 && todayDebt <= 0,
  };
}

/**
 * 목표 손잡이의 라벨 — 목표 화면으로 가는 진입점이 달고 있는 말.
 *
 * <p>**라벨은 값이 아니라 동작을 말한다**(2026-08-14 실기기 제보로 수정). 한때 「목표 30분 ›」처럼 값을
 * 실어 무엇을 바꾸는 버튼인지 스스로 밝히게 했지만, 손잡이가 「남은시간」 상자 안으로 들어오면서 **바로
 * 위 「오늘 목표 30분」 줄이 같은 값을 이미 말하게 됐다** — 그 자리에서 값을 되풀이하면 중복이고,
 * 정작 "눌러서 바꾸는 것"이라는 사실은 아무도 말하지 않는다. 설정 화면의 같은 버튼과도 말이 맞는다.
 *
 * <p>목표 0이면 바꿀 값이 없으니 정하러 가는 말이 맞다 — 그 상태에선 게이지 줄이 통째로 안 그려져
 * (`todayProgress`의 `progress === null`) 이 버튼이 목표를 정하러 가는 화면 유일의 길이다.
 */
export function goalHandleLabel(goalSeconds: number, adPending = false): string {
  // 전면광고 로드에 1~2초가 걸린다(광고를 먼저 띄우고 전환하도록 바꾼 뒤 생긴 대기). 그동안 라벨이
  // 그대로면 눌러도 아무 일 없는 것처럼 보여 사용자가 다시 누른다 — 목표값보다 대기 사실이 우선이다.
  if (adPending) return '준비 중…';
  return goalSeconds > 0 ? '하루 목표 바꾸기' : '목표 정하기';
}

/**
 * 목표 손잡이 — 서는 자리가 둘이라 컴포넌트로 뽑았다: 평상시엔 **「남은시간」 상자 안**, 목표가 0이라
 * 그 상자로 갈 길이 없을 때만 **히어로 카드 안**. 두 자리가 같은 모양이어야 사용자가 한 번 배운 손잡이가
 * 상태에 따라 다른 물건으로 보이지 않는다.
 *
 * <p>**자작 칩이 아니라 TDS `Button`이다**(2026-08-14 실기기 제보). 작고 테두리 없는 인라인 칩이라
 * 같은 상자에 선 「광고 보고 밀린 하루 지우기」와 나란히 놓였을 때 **버튼으로 읽히지 않았다** — 광고 쪽과
 * 같은 꼴(풀폭 `weak`)로 맞춰 "눌러서 바꾸는 것"임을 모양이 말하게 한다.
 */
function GoalHandle({
  goalSeconds,
  pending,
  onGoGoal,
}: {
  goalSeconds: number;
  /** 전면광고 로드 대기 — 라벨과 비활성 둘 다로 "누른 건 먹혔다"를 말한다. */
  pending: boolean;
  onGoGoal: () => void;
}) {
  return (
    <Button display="block" variant="weak" size="small" style={{ marginTop: 10 }} disabled={pending} onClick={onGoGoal}>
      {goalHandleLabel(goalSeconds, pending)}
    </Button>
  );
}

/**
 * 남은시간 설명 상자 — 「남은시간」을 탭하면 그 자리에서 펼쳐진다.
 *
 * <p>남은시간이 **목표 + 밀린 시간**이라는 사실은 숫자 하나만 봐서는 못 읽는다. 내역으로 합을
 * 밝히고, 밀린 시간을 어떻게 없애는지도 여기서 알린다 — 7일 자동 소멸이 폐지돼(2026-08-14) "기다리면
 * 사라진다"가 더 이상 사실이 아니므로, 지우는 두 수단(더 읽기·광고)으로 문구를 바꿨다.
 *
 * <p>**손잡이(목표 바꾸기·광고 보기)도 여기 담긴다** — 히어로 카드에 칩으로 얹혀 있던 것들이
 * 카드를 어지럽혀 이 상자로 들어왔다(사용자 결정 2026-08-14). 상자는 표시만 맡고 배선은 `children`으로
 * 받는다: 광고의 busy·전면광고 대기 같은 상태를 상자가 알기 시작하면 표시와 배선이 한 덩어리가 된다.
 *
 * <p>밀린 게 0이면 그 줄과 안내 문구를 통째로 접는다 — 「밀린 시간 0분」은 없는 빚을 상기시키는 줄이고,
 * 지우는 방법 안내도 지울 게 없는 사람에겐 잡음이다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다 — 하니스가 정적 렌더라 탭해서 펼친 상태에 도달할 수 없다(T-149).
 */
export function RemainingNote({
  goalSeconds,
  debtSeconds,
  remainingSeconds,
  children,
}: {
  goalSeconds: number;
  debtSeconds: number;
  remainingSeconds: number;
  /** 내역 아래 손잡이 자리 — 목표 바꾸기·광고 버튼이 여기 선다. */
  children?: ReactNode;
}) {
  const row = (label: string, value: string, strong = false) => (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '2px 0' }}>
      <Text typography="st12" color={strong ? undefined : 'grey600'}>
        {label}
      </Text>
      <Text typography="st12" color={strong ? undefined : 'grey600'}>
        {value}
      </Text>
    </div>
  );

  return (
    <div
      style={{
        marginTop: 10,
        padding: '10px 12px',
        borderRadius: 10,
        background: 'var(--adaptiveGrey200, #E4DDD0)',
        textAlign: 'left',
      }}
    >
      {row('오늘 목표', formatDuration(goalSeconds))}
      {debtSeconds > 0 && row('밀린 시간', formatDuration(debtSeconds))}
      <div style={{ marginTop: 4, paddingTop: 4, borderTop: '0.5px solid var(--adaptiveGrey600, #6F6A5E)' }}>
        {row('남은시간', formatClock(remainingSeconds), true)}
      </div>
      {debtSeconds > 0 && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, wordBreak: 'keep-all' }}>
          밀린 시간은 목표보다 더 읽거나, 광고를 보고 하루씩 지울 수 있어요.
        </Text>
      )}
      {children}
    </div>
  );
}

/**
 * 리워드 광고 버튼을 노출할지 — 셋 다 참이어야 한다.
 *
 * <p>① 밀린 시간이 있다(=죄책감이 화면에 뜬 순간, 보상이 필요한 바로 그 지점) ② 서버가 지금 지급
 * 가능하다고 했다(=지울 밀린 날이 남았다 — 일일 1회 상한은 2026-08-14 폐지) ③ 광고 그룹 ID가 설정됐다
 * (config-gate). **부채가 없으면 광고의 존재 자체가 안 보인다** — 입문자에게 "광고 보는 앱" 인상을 주지
 * 않으려는 배치다(설계 §3). 지울 날이 남아 있는 한 버튼은 시청 후에도 계속 보인다.
 */
export function showWaiverButton(
  carriedDebtSeconds: number,
  debtWaiverAvailable: boolean,
  adGroupId: string,
): boolean {
  return carriedDebtSeconds > 0 && debtWaiverAvailable && adGroupId !== '';
}

/**
 * 광고 시청 → 지급. 끝까지 안 봤으면 **지급 API를 부르지 않고** `null`(조용히 원상태).
 *
 * <p>클릭 흐름을 화면에서 꺼내 둔 이유: 테스트 하니스가 정적 렌더라 클릭이 안 돌아,
 * "보상 없이 지급 요청을 보내지 않는다"는 이 기능의 신뢰 경계를 함수로만 계측할 수 있다.
 */
export async function claimDebtWaiver(adGroupId: string): Promise<WaiveResponse | null> {
  const rewarded = await watchRewardAd(adGroupId);
  return rewarded ? waiveDebt() : null;
}

/**
 * 알림 동의 카드를 띄울지 — 아직 한 번도 답하지 않았고(캐시 없음) 지원되는 토스앱(5.255.0+)일 때만.
 *
 * <p>거절(`agreementRejected`)도 캐시라 카드가 사라진다 — 거절한 사람을 다시 조르지 않는다.
 * 다른 기기에서 이미 동의했다면 캐시가 없어 카드가 한 번 더 보이지만, 누르면 `alreadyAgreed`가
 * 와서 캐시되고 사라진다(무해).
 */
export function shouldShowNotificationCard(cached: string | null, supported: boolean): boolean {
  return supported && cached === null;
}

/**
 * 동의 화면을 띄우고 결과를 캐시한다 — 이 캐시가 카드를 끄는 유일한 스위치다.
 *
 * <p>미지원 기기(`null`)에서는 **캐시를 남기지 않는다** — 남기면 나중에 최신 토스앱에서 열어도
 * 영영 안 묻는다. 클릭 흐름을 화면 밖으로 꺼낸 이유는 광고 쪽과 같다(정적 렌더 하니스라 클릭이 안 돈다).
 */
export async function askNotificationAgreement(): Promise<string | null> {
  const result = await requestNotificationAgreement(GOAL_MET_TEMPLATE_CODE);
  if (result !== null) localStorage.setItem(AGREEMENT_KEY, result);
  return result;
}

/**
 * 실패 문구 — 서버가 준 평문(409 "오늘은 이미 사용했어요" 등)은 그대로 쓰고, SDK가 준 광고 에러는
 * 영문·기술 문구라 그대로 띄우면 안 되므로 안내로 바꾼다.
 */
export function waiverErrorMessage(error: Error): string {
  return error instanceof ApiError ? error.message : '광고를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.';
}

/**
 * 측정 종료 후 태깅 바텀시트 — 딤 + 하단 패널. 행은 표지 자리 + 제목이라 버튼 나열에 없던 시각 위계가 생긴다.
 *
 * <p>한때 시작 자리(`start` 모드)를 겸했지만 고르기는 캐러셀이 가져갔다 — 지금 이 시트가 서는 자리는
 * "방금 끝낸 세션에 무슨 책이었는지 붙이기" 하나뿐이라 문구도 하나다.
 *
 * <p>딤·패널 껍데기는 `ui.Sheet`이 맡는다(서재의 「펼쳐보기」·「관리」와 같은 것) — 여기 남은 건 내용뿐이다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다: 하니스가 정적 렌더라 「바꾸기」를 눌러 열린 상태에 도달할 수 없어,
 * 시트 자체는 여기서 직접 렌더해야 계측된다.
 */
export function BookSheet({
  books,
  disabled,
  onPick,
  onSkip,
  onClose,
}: {
  books: BookOption[];
  disabled: boolean;
  onPick: (book: BookOption) => void;
  onSkip: () => void;
  onClose: () => void;
}) {
  return (
    <Sheet title="무슨 책을 읽으셨나요?" onClose={onClose}>
      {books.map((book) => (
          <button
            key={book.id}
            type="button"
            // 계측용 표지 — TDS가 뿜는 emotion 클래스 사이에서 "행이 몇 개고 어떤 책인가"를 집을 손잡이가 없다.
            data-book-title={book.title}
            disabled={disabled}
            onClick={() => onPick(book)}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 12,
              width: '100%',
              marginBottom: 8,
              padding: 10,
              border: 'none',
              borderRadius: 10,
              background: 'transparent',
              cursor: 'pointer',
            }}
          >
            <CoverInitial title={book.title} width={28} />
            {/* 한글 제목이 flex 자식이라 minWidth:0이 없으면 줄바꿈 대신 행을 밀어낸다. */}
            <Text typography="st11" style={{ flex: 1, minWidth: 0, textAlign: 'left', wordBreak: 'keep-all' }}>
              {book.title}
            </Text>
          </button>
        ))}
      <Button display="block" variant="weak" size="medium" style={{ marginTop: 8 }} disabled={disabled} onClick={onSkip}>
        건너뛰기
      </Button>
    </Sheet>
  );
}

/**
 * 계정 진입점 — **홈 맨 위**에서 프로필·설정 화면으로 간다.
 *
 * <p>예전엔 여기가 "계정 관리·상세 설정은 booktimer.app에서" + 로그아웃이었다. 그런데 <b>토스로 가입한
 * 계정은 비밀번호가 없어 그 웹에 로그인 자체가 불가능</b>하다 — 실행할 수 없는 죽은 안내였다(핸들 배너가
 * 앓던 것과 같은 병). 이제 그 자리에서 실제로 닿을 수 있는 곳(설정 화면)으로 보낸다. 로그아웃 2단 확인도
 * 거기로 이사했다 — 두 자리에 두면 확인 단계가 갈라진다.
 *
 * <p>자리가 맨 아래에서 맨 위로 올라왔다(사용자 결정 2026-08-14) — 피드 박스는 세로로 자라는 상자라
 * 그 뒤에 두면 스크롤 끝에 묻히는데, 소셜 기능이 늘수록 프로필의 무게는 반대로 커진다.
 *
 * <p>모양은 <b>인사말 + 아바타 원</b>이다(사용자 지적 2026-08-17). 앞서 쓰던 「흰 채움 + 테두리」 알약은
 * 버튼으로는 잘 읽혔지만 <b>화면에서 가장 밝은 것</b>이 되어, 가장 안 중요한 손잡이가 첫 시선을 받았다
 * (홈에 제목이 없어 그 알약이 사실상 헤더 노릇을 하고 있었다). 그래서 같은 행을 <b>헤더로</b> 만든다 —
 * 왼쪽에 내가 누구인지 적고, 손잡이는 대비가 낮은 아바타로 줄인다. 행 전체가 탭 대상이라 손가락 표적은 오히려 커졌다.
 *
 * <p>덤이 하나 있다: 기본 닉네임(「토스유저」)인 사람이 <b>매일 자기 이름을 보게 되어</b> 바꿀 이유가 생긴다 —
 * 이 화면이 존재하는 이유와 맞물린다. 아바타는 책방 프로필과 <b>같은 이니셜 원</b>이라 같은 사람이 같은 색으로 선다.
 */
export function AccountSection({
  nickname,
  loginId,
  onGoSettings,
}: {
  nickname: string;
  /** @아이디 — 온보딩 전이면 `null`이라 그 줄을 아예 그리지 않는다(「@」만 남는 줄이 생기지 않게). */
  loginId: string | null;
  onGoSettings: () => void;
}) {
  return (
    <button
      type="button"
      aria-label="프로필·설정"
      onClick={onGoSettings}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        width: '100%',
        marginBottom: 12,
        padding: '2px 2px 0',
        border: 'none',
        background: 'transparent',
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      <span style={{ flex: 1, minWidth: 0 }}>
        {/* 긴 닉네임이 아바타를 밀어내지 않도록 한 줄로 자른다(서버는 20자까지 받는다). */}
        <span
          style={{
            display: 'block',
            // 이름이다 — 책방의 닉네임(Profile.tsx)과 같은 단으로 맞춘다(한 사람이 두 화면에서 같은 체급).
            fontSize: 19,
            fontWeight: 700,
            color: 'var(--adaptiveGrey900, #3A362E)',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {nickname}님
        </span>
        {loginId !== null && (
          <span
            data-handle={loginId}
            style={{ display: 'block', marginTop: 1, fontSize: 13, color: 'var(--adaptiveGrey600, #6F6A5E)' }}
          >
            @{loginId}
          </span>
        )}
      </span>
      <Avatar nickname={nickname} size={38} />
    </button>
  );
}

/**
 * 측정 중 「읽는 중」 카드 — <b>캐러셀이 서 있던 자리</b>를 그대로 물려받는다.
 *
 * <p>예전엔 측정을 시작하면 그 섹션이 통째로 사라져 표지가 없어지고 히어로의 「측정 중 12분 · 데미안」
 * 한 줄만 남았다(사용자 지적 2026-08-19: 「책 사진이 사라지고 텍스트로 바뀐다」). 고를 게 없으니
 * <b>캐러셀일 이유가 없을 뿐</b>, 지금 읽는 책을 보여 줄 이유는 그대로였다.
 *
 * <p>책 없이 측정 중이면(`book === null`) 카드는 그대로 서고 표지 자리만 「책 없이」가 된다 — 미태깅
 * 측정은 정상 경로이고, 여기서 카드를 감추면 시작·종료 때마다 화면이 세로로 튄다.
 *
 * <p>{@link children}는 카드 바닥의 손잡이 자리다 — 캐러셀 카드가 이 카드로 바뀌어도 <b>「지금 이
 * 화면이 가리키는 책」에 딸린 손잡이는 따라와야</b> 하기 때문이다(지금은 여백 문 하나).
 */
export function ReadingNowCard({
  book,
  totalSeconds,
  children,
}: {
  book: BookOption | null;
  totalSeconds: number;
  children?: ReactNode;
}) {
  return (
    <section style={sectionStyle}>
      <SectionTitle style={{ marginBottom: 10 }}>읽는 중</SectionTitle>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {book === null ? (
          <NoBookCard width={READING_NOW_COVER} />
        ) : (
          <BookCover url={book.coverUrl} title={book.title} width={READING_NOW_COVER} eager />
        )}
        <div style={{ minWidth: 0 }}>
          <Text typography="t7" fontWeight="bold" style={{ wordBreak: 'keep-all' }}>
            {book === null ? '책 없이 측정 중' : book.title}
          </Text>
          {book?.author != null && (
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {book.author}
            </Text>
          )}
          {/* 누적은 이미 오는 값이라 공짜다 — 「이 책을 얼마나 읽었나」가 측정 중에 가장 궁금한 수다. */}
          {book !== null && totalSeconds > 0 && (
            <Text typography="st12" color="blue500" style={{ display: 'block', marginTop: 6 }}>
              이 책 누적 {formatDuration(totalSeconds)}
            </Text>
          )}
        </div>
      </div>
      {children}
    </section>
  );
}

/**
 * 타이머 홈 — `/api/dashboard` 렌더(계정 진입 · 오늘 진행률 · 읽는 중 책 · 피드 박스).
 * 서재 관리·검색·정원은 웹이 본진이라 미니앱에 두지 않는다(설계 §2.5).
 *
 * <p><b>시작·종료 버튼은 여기 없다</b> — 하단 탭바 가운데 원이 유일한 자리다(어느 탭에서든 눌러야 하므로
 * 액션은 `MainTabs`가 든다). 홈이 측정에 대해 말하는 건 히어로의 「측정 중 N분」과 안심 문구까지다.
 */
export function Home({
  dashboard,
  guide,
  selectedBookId: picked,
  onSelectBook,
  onTimerChange,
  celebrate,
  onGoGoal,
  goalAdPending,
  onGoSettings,
  onError,
  onOpenMargin,
  onComposeMargin,
}: {
  dashboard: DashboardResponse;
  /**
   * 첫 사용 안내로 들어오는 배너 — **자리만 여기가 정한다**(헤더 바로 아래). 만드는 쪽은 흐름을 든
   * `MainTabs`다: 안 본 길 안내가 있을 때만 노드가 오고, 없으면 `null`이라 빈 줄도 남지 않는다.
   */
  guide?: ReactNode;
  /**
   * 캐러셀에서 고른 책 — 상태는 **App이 든다**(홈은 여백·목표·설정이 열리면 언마운트된다).
   * `undefined`는 아직 고르지 않음(여기서 {@link defaultBookId}로 정한다), `null`은 「책 없이」를 고른 것.
   */
  selectedBookId: number | null | undefined;
  onSelectBook: (bookId: number | null) => void;
  onTimerChange: (timer: TimerState) => void;
  /** 첫 완료 축하가 떠 있는지 — 상태는 측정 액션과 함께 `MainTabs`가 든다(다른 탭에서 끝내도 여기 뜨도록). */
  celebrate: boolean;
  onGoGoal: () => void;
  /** 전면광고를 기다리는 중 — 손잡이를 「준비 중」으로 바꾸고 비활성화한다(연타 방지는 App도 함께 한다). */
  goalAdPending: boolean;
  /** 홈 맨 위의 계정 진입 — 닉네임·@아이디·목표·로그아웃은 전부 설정 화면이 맡는다. */
  onGoSettings: () => void;
  onError: (error: Error) => void;
  /** 소식의 여백 줄 탭 — 그 사람의 그 책 여백을 전체 화면으로 연다(전이는 App이 든다). */
  onOpenMargin: (loginId: string, bookId: number) => void;
  /** 여백 문 — 지금 이 화면이 가리키는 책의 **작성 화면으로 직행**한다(측정 시작과 같은 1탭). */
  onComposeMargin: (book: BookOption) => void;
}) {
  /** 측정할 책 — 아직 안 골랐으면 기본값(이어 읽기)으로 떨어진다. 고른 값은 App이 들어 화면을 나갔다 와도 남는다. */
  const selectedBookId = picked === undefined ? defaultBookId(dashboard.readingBooks, dashboard.recentBookId) : picked;
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  /** 방금 지운 부채(초) — 성공 직후 한 줄 안내용. */
  const [waived, setWaived] = useState<number | null>(null);
  /** 남은시간 설명 상자 — 접힌 채로 시작한다(궁금한 사람만 편다). */
  const [showNote, setShowNote] = useState(false);
  /** 알림 동의 캐시·지원 여부 — 렌더마다 다시 묻지 않게 초기값으로 한 번만 읽는다. */
  const [agreement, setAgreement] = useState(() => localStorage.getItem(AGREEMENT_KEY));
  const [agreementSupported] = useState(notificationAgreementSupported);

  useEffect(() => {
    if (!dashboard.hasActiveSession) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [dashboard.hasActiveSession]);

  /** 광고 보고 밀린 하루 지우기 — 중간 이탈(null)이면 아무 일도 없었던 것처럼 둔다. */
  const claimWaiver = () => {
    setBusy(true);
    setError(null);
    claimDebtWaiver(REWARD_AD_GROUP_ID)
      .then((result) => {
        if (result === null) return;
        onTimerChange(result.timer); // 부채·버튼 노출이 재조회 없이 갱신된다
        setWaived(result.waivedSeconds);
      })
      .catch((e: Error) => setError(waiverErrorMessage(e)))
      .finally(() => setBusy(false));
  };

  /** 알림 동의 요청 — 결과(동의·이미동의·거절)가 캐시되면 카드가 사라진다. 미지원(null)이면 그대로 둔다. */
  const askNotification = () => {
    setBusy(true);
    setError(null);
    askNotificationAgreement()
      .then(setAgreement)
      .catch(() => setError('알림 동의를 요청하지 못했어요. 잠시 후 다시 시도해 주세요.'))
      .finally(() => setBusy(false));
  };

  const goal = dashboard.todayGoalSeconds;
  const elapsed =
    dashboard.hasActiveSession && dashboard.activeStartedAt !== null
      ? elapsedSeconds(dashboard.activeStartedAt, now)
      : 0;
  // 측정 중이면 elapsed가 매초 늘어 todayRead도 매초 늘어난다 — 카운트업의 동력이 이 한 줄이다.
  const { todayRead, remaining, overflow, progress, achieved } = todayProgress(dashboard, elapsed);
  // 여백 문이 가리키는 책 — 측정 중이면 그 책, 대기 중이면 캐러셀에서 고른 책(없으면 문을 안 그린다).
  const doorBook = marginDoorBook(dashboard, selectedBookId);

  /**
   * 여백 문 — <b>지금 이 화면이 어느 책을 뜻하는지 말하는 카드 안</b>에 산다(대기 중이면 캐러셀 카드,
   * 측정 중이면 「읽는 중」 카드). 상태는 둘이지만 규칙은 하나라 노드도 하나다.
   *
   * <p>예전엔 카드 <b>밖</b>에 홀로 서 있었는데, 그 자리에선 보이지 않았다(사용자 지적 2026-08-21:
   * 「여백 버튼이 별로 눈에 안 띄네」). 이 앱의 `weak` 버튼은 css가 연필 테두리를 얹으므로, 연필
   * 테두리 카드들 사이에 홀로 두면 버튼이 아니라 <b>글자 한 줄만 든 빈 카드</b>로 읽힌다 — 위아래와
   * 테두리가 똑같으니 눈이 그냥 지나간다. 카드 안으로 들어오면 바깥 진한 선(카드) 안의 흐린 선(버튼)이
   * 되어 위계가 서고, 「책을 고른다 → 그 책의 여백에 적는다」가 한 흐름으로 읽힌다.
   *
   * <p>`weak`는 유지한다 — 화면의 주 동작(탭바의 초록 원)보다 낮은 무게가 맞다.
   */
  const marginDoor = doorBook !== null && (
    // 첫 방문 안내는 문이 실제로 선 자리에서만 뜬다 — 책 0권이면 문 자체가 없으므로, 이 안내는
    // 책을 담고 측정을 해 본 뒤에야 저절로 차례가 온다(순서를 제어하는 코드가 없다).
    <Coachmark
      name="margin"
      after="bookshop" // 탭바 투어를 마친 뒤에 — 딤 두 장이 겹치지 않게
      title="읽다가 떠오른 생각을 여백에"
      detail="문장·감상을 몇 줄 남겨 두는 자리예요"
    >
      <Button
        display="block"
        variant="weak"
        size="medium"
        style={{ marginTop: 16 }}
        onClick={() => onComposeMargin(doorBook)}
      >
        여백에 글 남기기
      </Button>
    </Coachmark>
  );

  return (
    <Screen>
      {/* 계정 진입은 화면 맨 위 — 카드 위 한 줄이 곧 이 화면의 헤더다(인사말 + 아바타). */}
      <AccountSection nickname={dashboard.nickname} loginId={dashboard.loginId} onGoSettings={onGoSettings} />

      {/* 첫 사용 안내로 들어오는 문 — 처음 온 사람이 히어로 카드보다 먼저 만나야 한다. */}
      {guide}

      {/* 독서등이 켜지면 이 카드만 스탠드 밑에 펼쳐진 페이지로 남는다 — 표식만 붙이고, 켤지 말지와
          색은 `body` 클래스와 css가 든다(그래서 측정 여부와 무관하게 늘 붙어 있다). */}
      <div
        className={LAMP_PAGE_CLASS}
        style={{
          padding: '28px 20px',
          borderRadius: 16,
          background: 'var(--adaptiveGrey100, #FCFAF5)',
          border: '1px solid transparent',
          borderImage: PENCIL_FRAME,
          textAlign: 'center',
        }}
      >
        {/* 라벨과 값은 각자 블록이어야 세로로 쌓인다 — 같은 줄에 붙으면 "오늘 읽은 시간45:00"으로 읽힌다. */}
        <div>
          <Text typography="st11" color="grey600">
            {achieved ? '🌿 오늘 목표 달성' : '오늘 읽은 시간'}
          </Text>
        </div>
        <div style={{ marginTop: 6 }}>
          {/* 세리프 + t2(44px) — 이 화면이 답하려는 유일한 수다. 개구 26px일 땐 화면 제목(22px)보다
              4px 큰 게 전부라 히어로로 읽히지 않았다. */}
          <Text typography="t2" fontWeight="bold" style={{ ...SERIF_VALUE }}>
            {formatClock(todayRead)}
          </Text>
        </div>
        {progress !== null ? (
          <div style={{ marginTop: 16 }}>
            <ProgressBar progress={progress} size="normal" color={SAGE} />
            {/* 남은 게 있으면 그 값이, 다 채웠으면 초과분이 온다. 정확히 다 채운 순간엔 히어로가 이미
                「🌿 오늘 목표 달성」이라 말했으니 이 줄을 통째로 생략한다. */}
            {remaining > 0 ? (
              // 남은 게 있으면 언제나 손잡이다 — 밝힐 내역(오늘 목표)이 늘 있고, 목표·광고 손잡이가
              // 이 상자 안으로 들어와 조건부로 잠그면 그 둘에 닿을 길까지 함께 사라진다.
              <button
                type="button"
                onClick={() => setShowNote((open) => !open)}
                style={{
                  marginTop: 8,
                  padding: 0,
                  border: 0,
                  background: 'transparent',
                  borderBottom: '1px dashed var(--adaptiveGrey600, #6F6A5E)',
                  color: 'var(--adaptiveGrey600, #6F6A5E)',
                  fontSize: 14,
                  cursor: 'pointer',
                }}
              >
                남은시간 : {formatClock(remaining)} ⓘ
              </button>
            ) : (
              overflow > 0 && (
                <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
                  +{formatDuration(overflow)} 더 읽었어요
                </Text>
              )
            )}
            {remaining > 0 && showNote && (
              <RemainingNote
                goalSeconds={goal}
                // 이월이 꺼져 있으면 밀린 시간은 남은시간 합계에 안 들어간다 — 그때 이 줄을 그리면 합이 어긋난다.
                debtSeconds={dashboard.carryover ? dashboard.carriedDebtSeconds : 0}
                remainingSeconds={remaining}
              >
                <GoalHandle goalSeconds={goal} pending={goalAdPending} onGoGoal={onGoGoal} />
                {/* 광고는 죄책감이 뜬 이 자리에만 나타난다. 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다. */}
                {showWaiverButton(dashboard.carriedDebtSeconds, dashboard.debtWaiverAvailable, REWARD_AD_GROUP_ID) && (
                  <Button
                    display="block"
                    variant="weak"
                    size="small"
                    style={{ marginTop: 8 }}
                    disabled={busy}
                    onClick={claimWaiver}
                  >
                    광고 보고 밀린 하루 지우기
                  </Button>
                )}
                {waived !== null && (
                  <Text typography="st12" color="blue500" style={{ display: 'block', marginTop: 8 }}>
                    밀린 {formatDuration(waived)}을 지웠어요. 잔디는 그대로예요.
                  </Text>
                )}
              </RemainingNote>
            )}
          </div>
        ) : (
          // 목표 0 — 게이지 줄이 통째로 없어 상자로 갈 길이 없다. 목표를 정하러 가는 유일한 손잡이가 여기 남는다.
          <div style={{ marginTop: 16 }}>
            <GoalHandle goalSeconds={goal} pending={goalAdPending} onGoGoal={onGoGoal} />
          </div>
        )}
        {dashboard.hasActiveSession && (
          <>
            <Text typography="t5" color="blue500" style={{ display: 'block', marginTop: 16 }}>
              측정 중 {formatDuration(elapsed)}
              {dashboard.activeBookTitle !== null && ` · ${dashboard.activeBookTitle}`}
            </Text>
            {/* 이 앱의 핵심 계약(측정은 서버 권위)을 측정 중 화면에서 말하지 않으면, 사용자는 화면을 켜 둬야
                하는 줄 알고 몇 초 만에 끈다 — 운영 실측에서 완료 세션 대부분이 1분 미만이었다. */}
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 6 }}>
              {ACTIVE_SESSION_RELIEF}
            </Text>
          </>
        )}
      </div>

      <FirstSessionBanner show={celebrate} />

      {/* 알림 동의 — 발송은 동의한 유저에게만 가능하고, 동의를 받는 주체는 미니앱이다(콘솔 심사 조건). */}
      {shouldShowNotificationCard(agreement, agreementSupported) && (
        <section style={sectionStyle}>
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            목표 달성과 완독 소식을 토스 알림으로 받아보세요
          </Text>
          <Button display="block" variant="weak" size="medium" disabled={busy} onClick={askNotification}>
            알림 받기
          </Button>
        </section>
      )}

      {dashboard.hasActiveSession ? (
        <ReadingNowCard book={dashboard.activeBook ?? null} totalSeconds={dashboard.activeBookTotalSeconds}>
          {marginDoor}
        </ReadingNowCard>
      ) : (
        <section style={sectionStyle}>
          {/* 상태와 무관한 고정 문구다 — 「책 없이」가 가운데면 "이 책으로"는 틀린 말이고, 상태별로
              갈아끼우면 헤더가 나타났다 사라지며 캐러셀이 세로로 들썩인다. */}
          <SectionTitle style={{ marginBottom: 10 }}>무엇으로 측정할까요?</SectionTitle>
          {/* 좌우로 밀어 고른다 — 가운데 온 칸이 곧 측정 대상이다(0번은 「책 없이」라 책 0권도 같은 화면). */}
          <BookCarousel books={dashboard.readingBooks} selectedId={selectedBookId} onSelect={onSelectBook} />
          {marginDoor}
        </section>
      )}

      <ErrorMessage message={error} />

      {/* 잔디 미리보기가 서 있던 자리는 피드 박스가 통째로 쓴다 — 기록(잔디·연속일·총 시간)은 기록 탭이
          이미 전부 그리고 그 탭은 하단 탭바에서 한 번에 닿으므로, 홈에 진입 손잡이를 또 두지 않는다. */}
      <HomeFeedBox onError={onError} onOpenMargin={onOpenMargin} />
    </Screen>
  );
}

