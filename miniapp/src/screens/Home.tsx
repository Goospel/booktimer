import { Button, ProgressBar, Text } from '@toss/tds-mobile';
import { useEffect, useRef, useState } from 'react';

import type { BookOption, DashboardResponse, QuoteDto, TimerState, WaiveResponse } from '../api';
import { ApiError, startSession, stopSession, tagBook, waiveDebt } from '../api';
import { useBackClose } from '../back';
import { elapsedSeconds, formatClock, formatDuration } from '../format';
import {
  GOAL_MET_TEMPLATE_CODE,
  REWARD_AD_GROUP_ID,
  notificationAgreementSupported,
  requestNotificationAgreement,
  trackEvent,
  watchRewardAd,
} from '../toss';
import { BookCover, CoverInitial, ErrorMessage, Screen, Sheet, sectionStyle } from '../ui';
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
 * 「책 없이」가 가운데 온 상태가 정직하다(주 버튼도 그때 `start(null)`을 보낸다).
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
 * 캐러셀 0번 칸의 「책 없이」 자리 표지 — 표지와 **같은 크기의 점선 상자**다.
 *
 * <p>`CoverInitial`을 쓰지 않는다: 색 상자 + 첫 글자라 실제 표지와 구분이 안 돼 "책 없이"가 책처럼 보인다.
 * `boxSizing`이 없으면 테두리 2px이 칸을 불려 스냅 위치(`i × stride`)가 이 카드부터 어긋난다.
 */
function NoBookCard() {
  return (
    <div
      style={{
        width: COVER_WIDTH,
        height: COVER_HEIGHT,
        boxSizing: 'border-box',
        border: '2px dashed #E4DDD0',
        borderRadius: 4,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 4,
      }}
    >
      <span aria-hidden="true" style={{ fontSize: 22 }}>
        ⏱️
      </span>
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
 * <p>선택 상태는 밖(홈)이 들고 있다 — 「측정 시작」이 같은 값을 써야 캐러셀과 시작 대상이 어긋나지 않는다.
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
}: {
  books: T[];
  selectedId: number | null;
  onSelect: (bookId: number | null) => void;
  /** 0번 「책 없이」 칸을 세울지 — 측정 대상을 고르는 홈만 켠다. */
  noBookCard?: boolean;
  /** 제목 아래 한 줄 — 기본은 저자. */
  metaOf?: (book: T) => string;
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
    // 마운트 1회 — 이후 위치는 손가락(스크롤)과 탭이 정한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
              ) : item.coverUrl !== null ? (
                <BookCover url={item.coverUrl} width={COVER_WIDTH} eager />
              ) : (
                <CoverInitial title={item.title} width={COVER_WIDTH} />
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
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {metaOf?.(selected) ?? selected.author}
            </Text>
          )
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
 * 「남은시간」에 설명 손잡이를 달지 — 남은시간이 **목표와 밀린 시간의 합**일 때만 설명할 게 있다.
 *
 * <p>밀린 게 없거나 이월이 꺼져 있으면 남은시간은 그냥 목표까지 남은 시간이라 밝힐 내역이 없다.
 * 그때도 밑줄을 그어 두면 눌러보고 허탕 치는 손잡이가 된다.
 */
export function showRemainingHandle(carryover: boolean, carriedDebtSeconds: number): boolean {
  return carryover && carriedDebtSeconds > 0;
}

/**
 * 목표 손잡이의 라벨 — 목표 화면으로 가는 유일한 진입점이 달고 있는 말.
 *
 * <p>아이콘 팩이 없어(의존성은 `@toss/tds-mobile` 하나) 뜻은 그림이 아니라 **목표 값 자체**가 말한다.
 * 「목표 30분 ›」은 무엇을 바꾸는 버튼인지 스스로 밝히므로 아이콘 한 개짜리 손잡이의 모호함이
 * 아예 성립하지 않는다 — 덤으로 게이지 줄에서 목표 값을 빼 같은 카드의 중복도 없앴다.
 *
 * <p>목표 0이면 게이지 줄이 통째로 안 그려지므로(`todayProgress`의 `progress === null`) 이 칩이
 * 목표를 정하러 가는 화면 유일의 길이다 — 그래서 그때는 라벨이 권유가 된다.
 */
export function goalHandleLabel(goalSeconds: number): string {
  return goalSeconds > 0 ? `목표 ${formatDuration(goalSeconds)} ›` : '목표 정하기 ›';
}

/**
 * 남은시간 설명 상자 — 「남은시간」을 탭하면 그 자리에서 펼쳐진다.
 *
 * <p>남은시간이 **목표 + 밀린 시간**이라는 사실은 숫자 하나만 봐서는 못 읽는다. 내역 세 줄로 합을
 * 밝히고, 밀린 시간을 어떻게 없애는지도 여기서 알린다 — 7일 자동 소멸이 폐지돼(2026-08-14) "기다리면
 * 사라진다"가 더 이상 사실이 아니므로, 지우는 두 수단(더 읽기·광고)으로 문구를 바꿨다.
 *
 * <p>화면에서 꺼내 둔 이유는 늘 같다 — 하니스가 정적 렌더라 탭해서 펼친 상태에 도달할 수 없다(T-149).
 */
export function RemainingNote({
  goalSeconds,
  debtSeconds,
  remainingSeconds,
}: {
  goalSeconds: number;
  debtSeconds: number;
  remainingSeconds: number;
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
      {row('밀린 시간', formatDuration(debtSeconds))}
      <div style={{ marginTop: 4, paddingTop: 4, borderTop: '0.5px solid var(--adaptiveGrey600, #6F6A5E)' }}>
        {row('남은시간', formatClock(remainingSeconds), true)}
      </div>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, wordBreak: 'keep-all' }}>
        밀린 시간은 목표보다 더 읽거나, 광고를 보고 하루씩 지울 수 있어요.
      </Text>
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
 * 계정 진입점 — 홈 맨 아래에서 프로필·설정 화면으로 간다.
 *
 * <p>예전엔 여기가 "계정 관리·상세 설정은 booktimer.app에서" + 로그아웃이었다. 그런데 <b>토스로 가입한
 * 계정은 비밀번호가 없어 그 웹에 로그인 자체가 불가능</b>하다 — 실행할 수 없는 죽은 안내였다(핸들 배너가
 * 앓던 것과 같은 병). 이제 그 자리에서 실제로 닿을 수 있는 곳(설정 화면)으로 보낸다. 로그아웃 2단 확인도
 * 거기로 이사했다 — 두 자리에 두면 확인 단계가 갈라진다.
 */
export function AccountSection({ onGoSettings }: { onGoSettings: () => void }) {
  return (
    <div style={{ marginTop: 32, textAlign: 'center' }}>
      <Button size="small" variant="weak" onClick={onGoSettings}>
        프로필·설정
      </Button>
    </div>
  );
}

/** 종료 직후 태깅 대상 — 책 없이 측정한 세션에 나중에 책을 붙인다. */
interface Untagged {
  sessionId: number;
}

/**
 * 타이머 홈 — `/api/dashboard` 렌더(오늘 진행률 · 시작/정지 · 읽는 중 책 · 잔디 미리보기 · 격언).
 * 서재 관리·검색·정원은 웹이 본진이라 미니앱에 두지 않는다(설계 §2.5).
 */
export function Home({
  dashboard,
  onTimerChange,
  onGraphChange,
  onGoGoal,
  onGoSettings,
  onError,
}: {
  dashboard: DashboardResponse;
  onTimerChange: (timer: TimerState) => void;
  onGraphChange: (graph: DashboardResponse['graph']) => void;
  onGoGoal: () => void;
  /** 홈 하단의 계정 진입 — 닉네임·@아이디·목표·로그아웃은 전부 설정 화면이 맡는다. */
  onGoSettings: () => void;
  onError: (error: Error) => void;
}) {
  /** 태깅 시트 — `null`이면 닫힘. 열림 여부와 대상 세션이 늘 같이 움직여 상태 하나로 족하다. */
  const [tagging, setTagging] = useState<Untagged | null>(null);
  /** 측정할 책 — 칩에 뜨는 그 책이고, 시작은 아래 주 버튼이 맡는다(여러 책을 번갈아 읽는 사람). */
  const [selectedBookId, setSelectedBookId] = useState(() =>
    defaultBookId(dashboard.readingBooks, dashboard.recentBookId),
  );
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  /** 방금 지운 부채(초) — 성공 직후 한 줄 안내용. */
  const [waived, setWaived] = useState<number | null>(null);
  /** 남은시간 설명 상자 — 접힌 채로 시작한다(궁금한 사람만 편다). */
  const [showNote, setShowNote] = useState(false);
  /**
   * 첫 완료 축하가 떠 있는지 — 메모리에만 둔다. 새로고침·재조회로 사라지는 게 맞다(축하는 그 순간 1회로 족하고,
   * 서버는 두 번째 종료부터 `firstCompletedSession=false`를 주므로 다시 켜질 일도 없다).
   */
  const [celebrate, setCelebrate] = useState(false);
  /** 알림 동의 캐시·지원 여부 — 렌더마다 다시 묻지 않게 초기값으로 한 번만 읽는다. */
  const [agreement, setAgreement] = useState(() => localStorage.getItem(AGREEMENT_KEY));
  const [agreementSupported] = useState(notificationAgreementSupported);

  useEffect(() => {
    if (!dashboard.hasActiveSession) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [dashboard.hasActiveSession]);

  const fail = (e: Error) => {
    // 401은 App이 재로그인으로 처리하고, 그 외(409 중복 시작 등)만 화면에 남긴다.
    if (e.name === 'UnauthorizedError') onError(e);
    else setError(e.message);
  };

  const run = (action: Promise<void>) => {
    setBusy(true);
    setError(null);
    action.catch(fail).finally(() => setBusy(false));
  };

  // 다음 측정을 시작하면 축하는 접는다 — 지난 세션의 축하가 새 측정 화면에 남아 있으면 거짓말이 된다.
  const start = (bookId: number | null) => {
    setCelebrate(false);
    return run(
      startSession(bookId).then((timer) => {
        onTimerChange(timer);
        trackEvent('reading_session_started');
      }),
    );
  };

  const stop = () =>
    run(
      stopSession().then((result) => {
        onTimerChange(result.timer);
        onGraphChange(result.graph); // stop 응답에 잔디가 동봉돼 새로고침 없이 즉시 갱신된다.
        setCelebrate(result.firstCompletedSession); // 첫 기록이면 배너 + 잔디 하이라이트로 시선을 아래로 보낸다.
        // 이 앱의 핵심 전환 — 콘솔 대표 전환을 「토스로그인 완료」에서 여기로 갈아끼우기 위한 신호다.
        // 서버 응답엔 세션 길이가 없어 화면이 세던 elapsed를 그대로 쓴다(시작 시각도 서버가 준 값이다).
        trackEvent('reading_session_completed', { duration_seconds: elapsed });
        // 웹처럼 종료 직후 시트를 저절로 연다 — 태깅은 지금 기억이 가장 선명하다.
        // 붙일 책이 0권이면 열지 않는다 — 빈 시트는 닫는 것 말고 할 수 있는 게 없는 막다른 길이다.
        if (result.untagged && dashboard.readingBooks.length > 0) setTagging({ sessionId: result.sessionId });
      }),
    );

  const tag = (book: BookOption) => {
    if (tagging === null) return;
    run(tagBook(tagging.sessionId, book.id).then(() => setTagging(null)));
  };

  /** 시트 닫기 — 태깅 시트를 닫는 건 곧 「건너뛰기」다(다시 들어갈 자리를 만들지 않는다). */
  const closeSheet = () => setTagging(null);

  // 안드로이드 뒤로가기는 시트만 닫는다 — 시트가 열린 채로 미니앱이 꺼지지 않게.
  useBackClose(tagging !== null, closeSheet);

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
  // 남은시간에 설명 손잡이를 달지 — 목표와 밀린 시간의 합일 때만 밝힐 내역이 있다.
  const hasNote = showRemainingHandle(dashboard.carryover, dashboard.carriedDebtSeconds);
  const quotes = dashboard.quotes ?? [];
  // 캐러셀 가운데 온 책 — 시작 버튼도 이 값을 그대로 쓴다(고른 책과 시작 대상이 어긋날 자리를 없앤다).
  const selectedBook = dashboard.readingBooks.find((b) => b.id === selectedBookId) ?? null;

  return (
    <Screen>
      <div
        style={{
          position: 'relative', // 우상단 목표 손잡이의 기준 상자
          padding: '28px 20px',
          borderRadius: 16,
          background: 'var(--adaptiveGrey100, #FCFAF5)',
          textAlign: 'center',
        }}
      >
        {/* 목표 화면으로 가는 유일한 길 — 하단 풀폭 버튼이던 것을 여기로 흡수했다(「측정 시작」과 폭이
            같아 무게가 같아 보이던 문제). 시각적 칩은 작아도 padding으로 손가락 몫은 확보한다. */}
        <button
          type="button"
          onClick={onGoGoal}
          style={{
            position: 'absolute',
            top: 8,
            right: 8,
            padding: '7px 11px',
            border: 0,
            borderRadius: 20,
            background: 'var(--adaptiveBlue50, #E7EEE2)',
            color: 'var(--adaptiveBlue700, #4F6B4C)',
            fontSize: 12,
            cursor: 'pointer',
          }}
        >
          {goalHandleLabel(goal)}
        </button>
        {/* 라벨과 값은 각자 블록이어야 세로로 쌓인다 — 같은 줄에 붙으면 "오늘 읽은 시간45:00"으로 읽힌다. */}
        <div>
          <Text typography="st11" color="grey600">
            {achieved ? '🌿 오늘 목표 달성' : '오늘 읽은 시간'}
          </Text>
        </div>
        <div style={{ marginTop: 6 }}>
          <Text typography="t2" fontWeight="bold">
            {formatClock(todayRead)}
          </Text>
        </div>
        {progress !== null && (
          <div style={{ marginTop: 16 }}>
            <ProgressBar progress={progress} size="normal" color={SAGE} />
            {/* 남은 게 있으면 그 값이, 다 채웠으면 초과분이 온다. 정확히 다 채운 순간엔 히어로가 이미
                「🌿 오늘 목표 달성」이라 말했으니 이 줄을 통째로 생략한다. */}
            {remaining > 0 ? (
              // 설명할 내역이 있을 때만 버튼이다 — 없으면 눌러도 아무 일 없는 밑줄이 된다.
              hasNote ? (
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
                    fontSize: 13,
                    cursor: 'pointer',
                  }}
                >
                  남은시간 : {formatClock(remaining)} ⓘ
                </button>
              ) : (
                <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
                  남은시간 : {formatClock(remaining)}
                </Text>
              )
            ) : (
              overflow > 0 && (
                <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
                  +{formatDuration(overflow)} 더 읽었어요
                </Text>
              )
            )}
            {hasNote && showNote && (
              <RemainingNote
                goalSeconds={goal}
                debtSeconds={dashboard.carriedDebtSeconds}
                remainingSeconds={remaining}
              />
            )}
          </div>
        )}
        {/* 광고는 죄책감이 뜬 이 자리에만 나타난다. 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다. */}
        {showWaiverButton(dashboard.carriedDebtSeconds, dashboard.debtWaiverAvailable, REWARD_AD_GROUP_ID) && (
          <Button
            variant="weak"
            size="small"
            style={{ marginTop: 10 }}
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

      {!dashboard.hasActiveSession && (
        <section style={sectionStyle}>
          {/* 상태와 무관한 고정 문구다 — 「책 없이」가 가운데면 "이 책으로"는 틀린 말이고, 상태별로
              갈아끼우면 헤더가 나타났다 사라지며 캐러셀이 세로로 들썩인다. */}
          <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
            무엇으로 측정할까요?
          </Text>
          {/* 좌우로 밀어 고른다 — 가운데 온 칸이 곧 측정 대상이다(0번은 「책 없이」라 책 0권도 같은 화면). */}
          <BookCarousel books={dashboard.readingBooks} selectedId={selectedBookId} onSelect={setSelectedBookId} />
        </section>
      )}

      <ErrorMessage message={error} />

      {/* 시작은 이 버튼 하나 — 무엇으로 측정할지는 캐러셀이 이미 말했다(「책 없이」 칸이면 selectedBook이
          null이라 그대로 `start(null)`이 나간다). 보조 갈래를 두면 "가운데 온 것으로 측정한다"는 문법이 깨진다. */}
      <Button
        display="block"
        color={dashboard.hasActiveSession ? 'danger' : 'primary'}
        style={{ marginTop: 24 }}
        loading={busy}
        onClick={dashboard.hasActiveSession ? stop : () => start(selectedBook?.id ?? null)}
      >
        {dashboard.hasActiveSession ? '측정 끝내기' : '측정 시작'}
      </Button>

      {/* 잔디 미리보기가 서 있던 자리는 피드 박스가 통째로 쓴다 — 기록(잔디·연속일·총 시간)은 기록 탭이
          이미 전부 그리고 그 탭은 하단 탭바에서 한 번에 닿으므로, 홈에 진입 손잡이를 또 두지 않는다. */}
      <HomeFeedBox onError={onError} />

      {quotes.length > 0 && <QuoteCard quotes={quotes} />}

      <AccountSection onGoSettings={onGoSettings} />

      {/* 시트는 측정 종료 후 태깅 자리 하나다 — 고르기는 캐러셀이 맡는다. */}
      {tagging !== null && (
        <BookSheet
          books={dashboard.readingBooks}
          disabled={busy}
          onPick={tag}
          onSkip={closeSheet}
          onClose={closeSheet}
        />
      )}
    </Screen>
  );
}

/** 작가 격언 — 서버가 셔플해 준 목록에서 하나. 탭하면 다음 격언으로 넘어간다. */
function QuoteCard({ quotes }: { quotes: QuoteDto[] }) {
  const [index, setIndex] = useState(() => Math.floor(Math.random() * quotes.length));
  const quote = quotes[index % quotes.length];

  return (
    <button type="button" onClick={() => setIndex((i) => (i + 1) % quotes.length)} style={cardStyle}>
      <Text typography="st11" style={{ display: 'block', textAlign: 'left' }}>
        “{quote.text}”
      </Text>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, textAlign: 'left' }}>
        — {quote.author}
      </Text>
    </button>
  );
}

/** 탭 가능한 카드 공통 — button 기본 스타일을 지워 div처럼 보이게 한다(접근성은 button이 맡는다). */
const cardStyle = {
  display: 'block',
  width: '100%',
  marginTop: 12,
  padding: 16,
  border: 'none',
  borderRadius: 12,
  background: 'var(--adaptiveGrey100, #FCFAF5)',
  textAlign: 'left',
  cursor: 'pointer',
} as const;
