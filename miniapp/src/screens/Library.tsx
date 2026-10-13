import { Button } from '@toss/tds-mobile';
import type { CSSProperties } from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';

import type { BookStatus, MarginResponse, MyBookSummary, Recommendation, SearchRow, ShelfResponse } from '../api';
import {
  addBook,
  changeBookStatus,
  deleteBook,
  fetchBookMargin,
  fetchRecommendation,
  fetchShelf,
  searchBooks,
  setBookVisibility,
} from '../api';
import { useBackClose } from '../back';
import { CACHE_SHELF, cacheGet, cacheKeyMargin, cachePut } from '../cache';
import { Coachmark } from '../coachmark';
import { formatDuration } from '../format';
import {
  ARCH,
  BookCover,
  ErrorMessage,
  Loading,
  OUTLINE_PILL,
  RULE_ROW,
  SECTION_RULE,
  Screen,
  SearchField,
  SectionTitle,
  SERIF_VALUE,
  Sheet,
  Text,
  Woodcut,
  sectionStyle,
} from '../ui';
import { openExternal } from '../toss';
import { CheckBadge, NoBookCard } from './Home';
import { MarginCard } from './Story';

/**
 * 탭 순서 = 읽는 흐름 순서(읽는 중 → 다 읽음 → 읽고 싶어요). 빈 탭도 라벨은 남는다(자리가 흔들리지 않게).
 *
 * <p>책방의 공개 책 상태 필터도 이 어휘를 그대로 쓴다(단일 출처) — 같은 앱 안에서 서재 탭과 필터가
 * 다른 말을 쓰면 같은 상태가 두 이름으로 보인다.
 */
export const SECTIONS: { status: BookStatus; title: string; empty: string }[] = [
  { status: 'READING', title: '읽는 중', empty: '읽는 중인 책이 없어요' },
  { status: 'FINISHED', title: '다 읽음', empty: '다 읽은 책이 없어요' },
  { status: 'WANT_TO_READ', title: '읽고 싶어요', empty: '읽고 싶은 책이 없어요' },
];

/**
 * 열린 시트 — 「펼쳐보기」(격자 — 어느 선반의 것인가를 든다)와 「관리」(액션) 둘뿐이고 **동시에 열리지 않는다**.
 *
 * <p>삭제 확인을 시트 밖 독립 state로 두면 열림과 수명이 어긋난다: 확인을 띄운 채 시트를 닫아도
 * 그 확인이 살아남아, 다시 열었을 때 「정말 삭제」가 곧바로 노출됐다(오삭제가 한 탭 거리).
 * 같은 객체에 묶어 두면 시트를 여는 순간 확인이 언제나 `false`로 새로 만들어진다.
 */
export type LibrarySheet =
  | { kind: 'grid'; status: BookStatus }
  | { kind: 'actions'; confirmDelete: boolean; confirmPublish: boolean }
  | null;

/** 책 한 권에 걸 수 있는 액션 — 상태 변경·공개 토글·삭제. 실행은 Library가 맡고 Shelf는 알림만 한다. */
export type BookAction =
  | { kind: 'status'; book: MyBookSummary; status: BookStatus }
  | { kind: 'visibility'; book: MyBookSummary }
  | { kind: 'delete'; book: MyBookSummary };

/** 캐러셀 아래 첫 줄 — <b>저자만</b>이다. 읽은 시간·공개 여부는 {@link bookStats}가 칩으로 맡는다. */
export function metaLine(book: MyBookSummary): string {
  return book.author ?? '저자 미상';
}

/**
 * 책 상태 칩 — 읽은 시간과 공개 여부.
 *
 * <p>전에는 저자 다음 줄에 「2시간 · 공개」로 붙어 있었다. 같은 회색 글자 두 줄이라 <b>「누가 썼나」와
 * 「내 상태」가 한 덩어리로 뭉쳤고</b>, 이 화면에서 가장 중요한 토글인 공개 여부가 저자 이름과 같은
 * 무게였다(바꾸는 자리는 「관리」 시트인데, 지금 어느 쪽인지가 안 보이면 열어 볼 이유도 안 생긴다).
 * 칩으로 떼면 저자는 글자, 상태는 형태로 갈려 층이 생긴다.
 *
 * <p>시간은 「2시간」이 아니라 「2시간 읽음」이다 — 칩 하나만 떼어 놓고 보면 무엇의 2시간인지 알 수 없다.
 * 0이면 칩 자체를 안 만든다(「0분 읽음」은 정보가 아니다).
 *
 * <p>공개는 세이지로 켜지고 비공개는 외곽선으로 눌린다 — <b>색이 곧 「누구에게 보이는가」</b>다.
 *
 * <p>저자를 줄바꿈으로 분리하던 옛 수법(`pre-line`)이 사라진 것도 이득이다 — 긴 알라딘 저자 문자열이
 * 되접혀도 칩은 <b>다른 요소</b>라 그 사이에 끼어 들 수가 없다(전에는 문자열 한 덩어리라 가능했다).
 */
export function bookStats(book: MyBookSummary): BookChip[] {
  const chips: BookChip[] = [];
  if (book.seconds > 0) {
    // `value`는 세리프로 그릴 앞부분이다 — 「2시간」이 값이고 「 읽음」은 그게 무엇인지 말하는 꼬리다.
    const read = formatDuration(book.seconds);
    chips.push({ label: `${read} 읽음`, value: read, tone: 'neutral' });
  }
  chips.push(book.isPublic ? { label: '공개', tone: 'sage' } : { label: '비공개', tone: 'outline' });
  return chips;
}

/** 칩 하나 — `value`가 있으면 라벨의 그 앞부분만 세리프로 그린다(시안 2c). */
export interface BookChip {
  label: string;
  tone: BookChipTone;
  value?: string;
}

type BookChipTone = 'neutral' | 'sage' | 'outline';

/** 칩 세 톤 — 소식 배지와 같은 값이다(새 색을 만들지 않는다). */
function bookChipStyle(tone: BookChipTone): CSSProperties {
  const base: CSSProperties = { display: 'inline-block', padding: '2px 9px', borderRadius: 20, fontSize: 14, lineHeight: 1.6 };
  if (tone === 'sage') {
    return { ...base, background: 'var(--adaptiveBlue50, #ECE5D8)', color: 'var(--adaptiveBlue700, #1D1B18)' };
  }
  if (tone === 'neutral') {
    return { ...base, background: 'var(--adaptiveGrey200, #D9D1C3)', color: 'var(--adaptiveGrey700, #4A453E)' };
  }
  return {
    ...base,
    background: 'transparent',
    color: 'var(--adaptiveGrey600, #645E54)',
    border: '1px solid var(--adaptiveGrey200, #D9D1C3)',
  };
}

/**
 * 공개 전환 확인이 필요한가 — <b>비공개→공개 방향 + 여백에 남긴 글이 있을 때만</b>.
 *
 * <p>비공개 책에 쌓아 둔 글은 나만 보는 메모인데, 책을 공개로 바꾸는 순간 <b>누구에게나</b> 실린다
 * (글에 자체 공개 필드가 없어 가시성은 언제나 책에서 파생된다). 되돌리는 방향은 `isPublic`이 걸러 낸다.
 * 고지가 「팔로워에게」였던 것은 2026-08-22에 고쳤다 — 팔로우 축이 사라진 뒤로 실제보다 좁은 고지였다.
 *
 * <p>`storyCount`가 없는 옛 서버는 0으로 떨어져 오늘과 같이 즉시 전환된다 — 보안 게이트가 아니라
 * 고지 UX라 fail-open을 택했다(명시적 결정).
 */
export function needsPublishConfirm(book: MyBookSummary): boolean {
  return !book.isPublic && (book.storyCount ?? 0) > 0;
}

/**
 * 지금 탭에서 실제로 고른 책 — 고른 id가 그 탭에 없으면 **첫 책으로 떨어진다**.
 *
 * <p>탭을 옮기거나(다른 상태로 이동) 지우면 id가 그 탭에서 사라지는데, 그대로 두면 캐러셀이
 * 아무것도 안 가리킨 채 「관리」만 서 있게 된다. 선택 state를 탭마다 따로 두는 대신 여기서 푼다.
 * 책방(태그 드릴다운으로 목록이 통째로 갈리는 자리)도 같은 이유로 이 함수를 쓴다.
 */
export function resolveSelected<T extends { id: number }>(rows: T[], selectedId: number | null): T | null {
  return rows.find((b) => b.id === selectedId) ?? rows[0] ?? null;
}

/** 박스가 든 스냅 — 어느 책의 응답인지 태그를 함께 든다. `margin: null` = 그 책 조회 실패. */
export interface MarginSnap {
  bookId: number;
  margin: MarginResponse | null;
}

/** 지연 fetch 간격 — 캐러셀을 한 칸씩 여러 번 멈추며 훑을 때 요청을 합친다(캐러셀 settle 뒤에 얹힌다). */
export const MARGIN_FETCH_DEBOUNCE_MS = 300;

/** 박스 미리보기 장수 — 나머지는 「전체 보기 ›」가 맡는다. */
export const MARGIN_PREVIEW_COUNT = 2;

/**
 * 지금 책에 대해 박스가 그릴 것 — 스냅이 없거나 <b>다른 책 것이면 로딩</b>이다(경합 렌더 가드).
 *
 * <p>책 B가 화면인데 책 A의 늦은 응답이 스냅에 남아 있어도 A의 글이 B 아래 그려질 수 없다.
 * 쓰기 가드(effect cleanup)와 한 쌍이다 — 쓰기 가드 단독은 "응답 도착 전까지 옛 글이 보이는" 구간을
 * 못 막고, 렌더 가드 단독은 늦은 응답이 마지막에 덮으면 재요청 트리거가 없어 로딩에 교착한다.
 */
export function marginBoxView(snap: MarginSnap | null, bookId: number): MarginResponse | 'loading' | 'error' {
  if (snap === null || snap.bookId !== bookId) return 'loading';
  return snap.margin ?? 'error';
}

/**
 * 인라인 여백 박스 — 고른 책의 글을 그 자리에서 몇 장 보여 준다(컨테이너: 조회·경합 가드).
 *
 * <p>지연 fetch({@link MARGIN_FETCH_DEBOUNCE_MS})가 캐러셀 훑기의 요청 폭주를 누른다 — 그 안에 다음
 * 책으로 넘어가면 cleanup이 타이머를 지워 <b>요청이 아예 안 나간다</b>. 늦게 도착한 응답은 쓰기 가드
 * (`stale`)가 막고, 그래도 남은 옛 스냅은 렌더 가드({@link marginBoxView})가 로딩으로 떨어뜨린다.
 *
 * <p>`key`를 주지 않는다 — `bookId`가 바뀌면 effect가 다시 돌고 렌더 가드가 로딩을 보장하므로
 * 리마운트할 이유가 없다.
 */
function MarginBox({
  loginId,
  bookId,
  onError,
  onOpenAll,
}: {
  loginId: string;
  bookId: number;
  /** 401만 App으로 올린다 — 그 외 실패는 박스 안 문구에 머문다(화면의 다른 fetch와 같은 규율). */
  onError: (error: Error) => void;
  onOpenAll: () => void;
}) {
  const [snap, setSnap] = useState<MarginSnap | null>(null);

  useEffect(() => {
    let stale = false; // cleanup 뒤 도착한 응답은 스냅을 건드리지 못한다
    const timer = setTimeout(() => {
      fetchBookMargin(loginId, bookId)
        .then((margin) => {
          // stale이어도 캐시에는 넣는다 — 키에 그 책의 id가 박혀 있어 남의 자리를 오염시킬 수 없고,
          // 이미 지나쳐 온 책으로 되돌아왔을 때 그게 곧 즉시 렌더다.
          cachePut(cacheKeyMargin(loginId, bookId), { bookId, margin } satisfies MarginSnap);
          if (!stale) setSnap({ bookId, margin });
        })
        .catch((e: Error) => {
          if (stale) return;
          if (e.name === 'UnauthorizedError') onError(e);
          else setSnap({ bookId, margin: null }); // 실패 스냅은 캐시에 안 넣는다 — 에러가 고착되면 안 된다
        });
    }, MARGIN_FETCH_DEBOUNCE_MS);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [loginId, bookId, onError]);

  /*
   * 캐시는 **렌더 중에** 고른다(effect 아님). `snap`은 「이 화면이 방금 받아온 응답」만 들고, 지금 책 것이
   * 아니면 무시하고 캐시를 본다 — 그래서 `bookId`가 바뀔 때 state를 갈아끼우는 배선이 아예 필요 없다.
   * effect로 갈아끼우면 페인트 뒤에 도는 탓에 캐러셀을 옮길 때마다 「불러오는 중…」이 한 프레임 깜빡인다.
   */
  const shown = snap?.bookId === bookId ? snap : (cacheGet<MarginSnap>(cacheKeyMargin(loginId, bookId)) ?? null);

  return <MarginBoxView view={marginBoxView(shown, bookId)} now={Date.now()} onOpenAll={onOpenAll} />;
}

/**
 * 인라인 여백 박스 — 순수 표시. 정적 렌더 하니스가 세 상태(로딩·실패·내용)에 닿는 유일한 길이다.
 *
 * <p>헤더는 세 상태 모두 서 있다 — 박스 뼈대가 고정이라야 캐러셀을 밀 때 화면이 들썩이지 않는다.
 * 실패에 재시도 버튼을 두지 않는 것은 의도다: 헤더 「전체 보기 ›」가 살아 있어 전체 화면(자체 재시도가
 * 있다)으로 갈 수 있고, 책을 옮기거나 돌아오면 그 자체가 재조회다. 글이 <b>0장</b>일 때만 카운트·문·
 * 아랫선을 접는다({@link empty}) — 그 문 너머엔 같은 빈 화면뿐이라 출구가 아니다.
 */
export function MarginBoxView({
  view,
  now,
  onOpenAll,
}: {
  view: MarginResponse | 'loading' | 'error';
  /** 상대 시각의 기준 — 밖에서 받아야 테스트가 결정론이 된다(여백 화면과 같은 규율). */
  now: number;
  onOpenAll: () => void;
}) {
  const entries = typeof view === 'string' ? [] : view.entries;
  /**
   * 아직 한 장도 없는 책 — 헤더가 <b>말만</b> 남는다. 「0」은 빈 상태를 숫자로 박제하고, 「전체 보기 ›」는
   * 같은 빈 화면으로 가는 문이라 눌러도 같은 사실을 다시 본다. 아랫선도 가를 내용이 없으면 머리와 안내를
   * 두 덩어리로 쪼갤 뿐이다. 로딩·실패는 <b>여기 아니다</b> — 그때는 전체 화면의 자체 재시도가 유일한 출구다.
   */
  const empty = typeof view !== 'string' && entries.length === 0;

  return (
    <div
      data-margin-box=""
      // 에디토리얼(PR-1) — 면 없음. 덩어리의 경계는 머리 줄 아래 잉크 선(SECTION_RULE)과 위 여백이다.
      style={{ marginTop: 24 }}
    >
      {/* 선은 제목이 아니라 이 줄에 건다 — 오른쪽 「전체 보기 ›」까지 지나야 머리 한 줄로 읽힌다(시안 2c). */}
      <div style={{ display: 'flex', alignItems: 'baseline', paddingBottom: 9, borderBottom: empty ? undefined : SECTION_RULE }}>
        {/* 에디토리얼 PR-3(계획 §4 L3) — 머리 H1 20/28 명조(SectionTitle 기본값), 카운트는 같은 크기 muted(안 1 §7.2).
            사양서 원값 D2 32는 이 머리만 유난히 커서 걷었다. */}
        <SectionTitle style={{ flex: 1 }}>
          여백
          {entries.length > 0 && (
            <b style={{ ...SERIF_VALUE, fontSize: 20, color: 'var(--adaptiveGrey600, #645E54)' }}>
              {' '}
              {entries.length}
            </b>
          )}
        </SectionTitle>
        {!empty && (
          <button
            type="button"
            onClick={onOpenAll}
            // 글자 링크 — 보이는 20, 누름 44(음수 여백으로 머리 줄 높이는 그대로).
            style={{
              minHeight: 44,
              margin: '-12px 0',
              padding: 0,
              border: 'none',
              background: 'transparent',
              color: 'var(--adaptiveGrey600, #645E54)',
              fontSize: 14,
              cursor: 'pointer',
            }}
          >
            전체 보기 ›
          </button>
        )}
      </div>
      {view === 'loading' && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 10 }}>
          불러오는 중…
        </Text>
      )}
      {view === 'error' && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 10 }}>
          여백을 불러오지 못했어요.
        </Text>
      )}
      {typeof view !== 'string' &&
        (entries.length === 0 ? (
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 10 }}>
            아직 남긴 글이 없어요. 위의 「여백에 글쓰기」로 첫 장을 남겨보세요.
          </Text>
        ) : (
          entries.slice(0, MARGIN_PREVIEW_COUNT).map((e) => (
            // 손잡이를 하나도 안 넘긴다 — 미리보기는 읽기 전용이고, 출구는 위의 「전체 보기」다.
            <MarginCard key={e.id} entry={e} now={now} />
          ))
        ))}
    </div>
  );
}

/**
 * 서재 탭 — 내 책장 조회·관리와 검색 추가.
 *
 * <p>화면 전환은 `mode` 하나로 한다(라우터 없음, 설계 §2). 뮤테이션 뒤에는 서버 응답으로 그 책만
 * 갈아끼우지 않고 책장을 다시 받는다 — 상태 변경은 선반 이동이라 목록 전체가 흔들리기 때문.
 */
export function Library({
  myLoginId,
  onError,
  onShelfChanged,
  onOpenMargin,
  onComposeMargin,
  onOpenBookMargin,
}: {
  /** 내 @아이디 — 없으면 서버가 여백 대상을 찾지 못하므로 여백 손잡이를 그리지 않는다(설계 결정 A). */
  myLoginId: string | null;
  onError: (error: Error) => void;
  /**
   * 책장을 바꾼 직후 부른다 — 홈이 쓰는 대시보드는 App이 들고 있어, 여기서 자기 책장만 다시 받으면
   * 홈 캐러셀은 옛 「읽는 중」 목록 그대로다(앱을 나갔다 와야 반영되던 문제).
   */
  onShelfChanged: () => void;
  /** 고른 책의 여백을 전체 화면으로 연다 — 인라인 박스의 「전체 보기 ›」가 이 문이다. */
  onOpenMargin: (loginId: string, bookId: number) => void;
  /**
   * 「여백에 글쓰기」 — 홈 문과 같은 작성 직행 경로. `MyBookSummary`가 `BookOption`을 구조적으로
   * 충족해 변환이 없다(오히려 비공개 여부가 실려 작성 화면 캡션이 더 정확하다).
   */
  onComposeMargin: (book: MyBookSummary) => void;
  /** 검색 행의 「여백 N」 배지 — 책축 화면은 탭 밖 전역 뷰라 셸(App)이 든다. */
  onOpenBookMargin: (isbn13: string) => void;
}) {
  /*
   * 첫 렌더의 출발점을 세션 캐시에서 집는다 — 탭을 오갈 때마다 화면이 통째로 `<Loading/>`이 되던 자리다.
   * 재검증(`useEffect(load)`)은 그대로 매번 나가므로 stale 노출은 최대 1 왕복이다.
   */
  const cachedShelf = cacheGet<ShelfResponse>(CACHE_SHELF);
  const [books, setBooks] = useState<MyBookSummary[] | null>(cachedShelf?.books ?? null);
  const [searchEnabled, setSearchEnabled] = useState(cachedShelf?.searchEnabled ?? false);
  const [mode, setMode] = useState<'shelf' | 'search'>('shelf');
  /**
   * 고른 책 — 책 id 또는 `undefined`(아직 안 고름 = 기본 선택, {@link shelfSelection}). 세 선반을 하나의 값이 가로지른다.
   *
   * <p>캐러셀 시절의 셋째 값 `null`(「책 추가」 칸을 고름)은 사라졌다 — 선반에선 칸을 누르면 곧장 검색이라 그 칸이 「고른 것」이
   * 되는 순간이 없다. 지워진 id·다른 선반으로 옮긴 책은 `shelfSelection`이 푼다(같은 id면 ✓가 새 선반으로 따라간다).
   */
  const [selectedId, setSelectedId] = useState<number | undefined>(undefined);
  const [sheet, setSheet] = useState<LibrarySheet>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fail = useCallback(
    (e: Error) => {
      // 401은 App이 재로그인으로 처리하고, 그 외(404·5xx)는 이 화면에 남긴다.
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  const load = useCallback(() => {
    setError(null); // 다시 받는 김에 지난 실패 문구도 지운다 — 안 그러면 재시도가 성공해도 빨간 줄이 남는다
    fetchShelf()
      .then((shelf) => {
        cachePut(CACHE_SHELF, shelf);
        setBooks(shelf.books);
        setSearchEnabled(shelf.searchEnabled);
      })
      .catch(fail);
  }, [fail]);

  useEffect(load, [load]);

  // 검색은 서재를 덮는 별도 화면이다 — 나가는 길은 네이티브 뒤로가기 하나이므로 여기가 유일한 출구다.
  useBackClose(mode === 'search', () => setMode('shelf'));
  // 열린 시트는 뒤로가기가 먼저 먹는다 — 시트가 열린 채로 미니앱이 꺼지지 않게(홈 태깅 시트와 같다).
  useBackClose(sheet !== null, () => setSheet(null));

  /** 뮤테이션 공통 — 실행 → 책장 재조회 → 홈 대시보드 갱신 → 열린 시트 닫기. */
  const run = (action: Promise<unknown>) => {
    setBusy(true);
    setError(null);
    action
      .then(() => {
        setSheet(null);
        load();
        onShelfChanged();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  const act = (action: BookAction) => {
    if (action.kind === 'status') {
      // 고른 값을 그 책으로 못 박는다 — 기본 선택(`undefined`)인 채로 옮기면 기본이 다음 책으로 넘어가 ✓가 따라가지 않는다(목 모드 실측).
      setSelectedId(action.book.id);
      run(changeBookStatus(action.book.id, action.status));
    } else if (action.kind === 'visibility')
      run(setBookVisibility(action.book.id, action.book.isPublic ? 'PRIVATE' : 'PUBLIC'));
    else run(deleteBook(action.book.id));
  };

  const add = (row: SearchRow, status: BookStatus) => {
    setBusy(true);
    setError(null);
    addBook(row, status)
      .then((added) => {
        setMode('shelf');
        // 방금 넣은 책을 고른다 — 그 책이 든 선반이 그 표지로 밀리고(`Shelf`의 reveal) 펼친 책·여백이 그 책이 된다.
        setSelectedId(added.id);
        load();
        onShelfChanged();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  if (mode === 'search') {
    return (
      <BookSearch
        busy={busy}
        error={error}
        onAdd={add}
        onFail={fail}
        onOpenBookMargin={onOpenBookMargin}
      />
    );
  }

  // 로딩 중에도 아치는 남긴다 — 탭을 옮길 때 화면이 통째로 비었다 다시 차는 깜빡임을 줄인다.
  return (
    <Screen>
      <LibraryArch />
      {/* 책장을 아예 못 받았을 때만 재시도 — 액션 실패(삭제 거절 등)는 다시 받을 게 아니라 문구만 남긴다. */}
      <ErrorMessage message={error} onRetry={books === null ? load : undefined} />
      {books === null ? (
        error === null && <Loading />
      ) : (
        <Shelf
          books={books}
          selectedId={selectedId}
          sheet={sheet}
          busy={busy}
          myLoginId={myLoginId}
          searchEnabled={searchEnabled}
          onSelect={setSelectedId}
          onSheet={setSheet}
          onAction={act}
          onOpenMargin={onOpenMargin}
          onComposeMargin={onComposeMargin}
          onError={onError}
          onAddBook={() => setMode('search')}
        />
      )}
    </Screen>
  );
}

/**
 * 서재 머리 — 폭 260 「작은 창」 아치 안에 판화 한 점과 「내 서재」(안 1 §7.2 + 계획 §4 L1 · D6 추천).
 *
 * <p>사양서 원값(폭 350 · 판화 80 · 제목 44)이면 머리가 첫 화면 위 1/4을 먹어 실기기(토스 내비 바 때문에 약 740)에서 셋째 선반이
 * 첫 화면 밖으로 걸린다. 폭 260 · 판화 64 · 제목 D2 32/40으로 줄여 높이 약 161 — 아치 + 선반 3단이 첫 화면 안에 든다.
 * 반원 그대로라 판화 윗끝(d=24)에서 아치 반폭 75 vs 판화 반폭 32로 옆선에 닿지 않는다(계산).
 */
function LibraryArch() {
  return (
    <div
      data-arch=""
      style={{
        ...ARCH,
        boxSizing: 'border-box',
        width: 260,
        margin: '0 auto 28px',
        padding: '24px 24px',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 8,
      }}
    >
      <Woodcut size={64} />
      <h1 style={{ margin: 0, ...SERIF_VALUE, fontSize: 32, lineHeight: '40px', color: 'var(--adaptiveGrey800, #1D1B18)' }}>
        내 서재
      </h1>
    </div>
  );
}

/** 선반 한 단 — 상태 + 이름·빈 문구({@link SECTIONS}) + 그 상태의 책(서버가 준 순서 그대로). */
export interface ShelfSection {
  status: BookStatus;
  title: string;
  empty: string;
  books: MyBookSummary[];
}

/** 선반 세 단 — <b>언제나 셋</b>이고 {@link SECTIONS} 순서다. 빈 단도 빠지지 않는다(나타났다 사라지면 아래가 들썩인다). */
export function shelfSections(books: MyBookSummary[]): ShelfSection[] {
  return SECTIONS.map((section) => ({ ...section, books: books.filter((b) => b.status === section.status) }));
}

/**
 * 실제로 고른 책 — 유효한 id면 그 책, 아니면(아직 안 고름 · 지워진 id) 선반 순서로 첫 책(읽는 중 → 다 읽음 → 읽고 싶어요), 0권이면 `null`.
 *
 * <p>탭 시절의 {@link resolveSelected}는 「그 탭 안에서」 풀었다. 여기선 세 선반 <b>전체</b>에서 푼다 — 관리 시트로 상태를 옮긴 책은
 * 같은 id라 그대로 고른 채 새 선반으로 따라간다(그 선반이 그 표지로 밀린다).
 */
export function shelfSelection(books: MyBookSummary[], selectedId: number | undefined): MyBookSummary | null {
  return books.find((b) => b.id === selectedId) ?? shelfSections(books).find((s) => s.books.length > 0)?.books[0] ?? null;
}

/**
 * 선반을 고른 표지로 밀 자리 — 표지가 이미 다 보이면 `null`(옮기지 않는다), 아니면 그 표지를 가운데로 데려오는 `scrollLeft`.
 *
 * <p>「이미 보이면 그대로」가 홈 `recenterIndex`와 같은 관례다 — 보이는 표지를 눌렀을 때나 손가락으로 민 뒤에 스크롤을 걸면 트랙을
 * 손가락에서 빼앗는다. 밖에서 고른 경우(추가 직후 · 격자 · 관리 시트로 옮긴 뒤)만 실제로 움직인다.
 */
export function revealScrollLeft(scrollLeft: number, viewport: number, cellLeft: number, cellWidth: number): number | null {
  if (cellLeft >= scrollLeft && cellLeft + cellWidth <= scrollLeft + viewport) return null;
  return Math.max(0, cellLeft - (viewport - cellWidth) / 2);
}

/** 선반 표지 — 80×112(안 1 `r5/cover-shelf`). 높이는 `BookCover`의 식(폭 × 1.4) 그대로다. */
const SHELF_COVER = 80;
const SHELF_COVER_HEIGHT = Math.round(SHELF_COVER * 1.4);
/** 표지 사이 — 걸친 알약(112)이 표지(80)보다 16씩 넘쳐도 이웃 표지와 4가 남는다(안 1 §7.3). */
const SHELF_GAP = 20;
/** 걸친 검정 알약 — 보이는 112×44 = 누름 영역(계획 §4 L5: 사양서 38 → 44). 세로 가운데가 판 높이라 절반(22)이 판 아래로 내려온다. */
const PILL_WIDTH = 112;
const PILL_HEIGHT = 44;
const PILL_SPILL = PILL_HEIGHT / 2;
/**
 * 선반 줄 높이 = 표지 + 알약 넘침 — <b>세 단 모두</b> 같은 값으로 잡는다. 고른 단에만 두면 고르기가 바뀔 때 아래 선반이 들썩이고,
 * 줄 밖으로 내려간 알약은 `overflow-x: auto`가 y도 auto로 만들어 잘리거나 줄이 세로로 스크롤된다.
 */
const SHELF_ROW_HEIGHT = SHELF_COVER_HEIGHT + PILL_SPILL;

/**
 * {@link revealScrollLeft}가 「다 보이는가」를 잴 범위 `[left, width]` — 알약이 있으면 <b>알약</b>이다. 알약은 표지보다 양옆 16씩 넓어,
 * 표지만 재면 끝까지 민 줄의 마지막 칸에서 표지는 다 보이는데 알약 글자가 잘린 채 멈춘다(리뷰 실측). 첫 칸은 렌더처럼 왼쪽 끝에 붙인다.
 */
export function revealSpan(cellLeft: number, withPill: boolean): [number, number] {
  if (!withPill) return [cellLeft, SHELF_COVER];
  return [cellLeft === 0 ? 0 : cellLeft - (PILL_WIDTH - SHELF_COVER) / 2, PILL_WIDTH];
}

/**
 * 줄을 고른 표지 칸으로 민다 — {@link Shelf}의 effect 본문. 정적 렌더에선 effect가 안 돌아 테스트가 이 함수를 직접 부른다
 * (줄은 `scrollLeft`·`clientWidth`만 있으면 된다). 이미 다 보이면 줄을 건드리지 않는다.
 */
export function revealCell(row: { scrollLeft: number; readonly clientWidth: number }, cellLeft: number, withPill: boolean): void {
  const left = revealScrollLeft(row.scrollLeft, row.clientWidth, ...revealSpan(cellLeft, withPill));
  if (left !== null) row.scrollLeft = left;
}

const INK = 'var(--adaptiveGrey800, #1D1B18)';
const MUTED = 'var(--adaptiveGrey600, #645E54)';

/**
 * 선반 한 단 — 머리(이름 · 권수 · 「펼쳐보기 ›」) + 판 위 표지 가로 줄. 훅이 없다 — 테스트가 함수로 불러 `onClick`을 트리에서 잰다.
 *
 * <p>⚠️ 줄에는 `overflowX: 'auto'`만 건다. `overflow-y`는 웹뷰가 그 값을 만나면 가로 터치까지 죽이고(T-159), `touch-action: pan-x`는
 * 선반 위에서 시작한 세로 스와이프로 페이지가 안 내려가게 만든다(선반 셋이 화면 절반을 덮는다). 세로로 넘칠 것이 애초에 없게
 * 넘침 자리를 줄 안에 잡았다({@link SHELF_ROW_HEIGHT}). 스냅도 없다 — 선반은 고르는 무대가 아니라 늘어놓은 책이다(고르기는 탭).
 */
export function ShelfRow({
  section,
  selectedId,
  lead,
  eager,
  busy,
  canWrite,
  rowRef,
  onSelect,
  onAddBook,
  onCompose,
  onOpenGrid,
}: {
  section: ShelfSection;
  /** 고른 책 — 이 단에 없으면 이 단엔 ✓도 알약도 없다. */
  selectedId: number | null;
  /** 맨 앞 「책 추가」 칸 — 읽는 중 단 + 서버가 검색을 켰을 때만(게이트와 안내가 한 몸). */
  lead: boolean;
  /** 첫 선반만 표지를 바로 싣는다 — 셋째 단까지 한꺼번에 받으면 첫 화면 이미지 요청이 몰린다. */
  eager: boolean;
  busy: boolean;
  /** 핸들이 없으면 알약을 안 그린다 — 눌러도 서버가 여백 대상을 못 찾는다(설계 결정 A). 자리(넘침)는 그대로다. */
  canWrite: boolean;
  rowRef?: (el: HTMLDivElement | null) => void;
  onSelect: (bookId: number) => void;
  onAddBook: () => void;
  onCompose: (book: MyBookSummary) => void;
  onOpenGrid: () => void;
}) {
  const { status, title, empty, books } = section;

  const row = (
    <div style={{ position: 'relative' }}>
      {/* 판 — 표지 바닥이 서는 잉크 선(안 1 `r5/board`). 줄보다 먼저 그려 표지·알약이 그 위에 얹힌다. 줄 밖에 두어 스크롤과 무관하게 내용 폭 전체다. */}
      <div
        data-board=""
        aria-hidden="true"
        style={{ position: 'absolute', left: 0, right: 0, top: SHELF_COVER_HEIGHT, borderTop: `1px solid ${INK}` }}
      />
      <div
        ref={rowRef}
        data-shelf-row=""
        style={{
          // 칸 위치(offsetLeft)를 줄 기준으로 재려고 positioned로 둔다(reveal).
          position: 'relative',
          display: 'flex',
          alignItems: 'flex-start',
          gap: SHELF_GAP,
          height: SHELF_ROW_HEIGHT,
          overflowX: 'auto',
          scrollbarWidth: 'none',
        }}
      >
        {lead && (
          // 누르면 곧장 검색 — 캐러셀 시절의 「칸 고르기 → 검색해서 담기」 2단계가 사라졌다(가운데 온 것이 없다).
          <button
            type="button"
            data-lead-card=""
            aria-label="책 추가"
            disabled={busy}
            onClick={onAddBook}
            // 종이 면 — 안내가 이 줄을 딤 위로 들어 올려도 투명한 칸엔 딤이 비쳐 가리키는 칸만 어두웠다(투어 실측). 평소엔 바탕과 같은 색이다.
            style={{ display: 'flex', flex: '0 0 auto', padding: 0, border: 'none', background: 'var(--adaptiveGrey100, #F5F0E6)', cursor: 'pointer' }}
          >
            <NoBookCard width={SHELF_COVER} label="책 추가" />
          </button>
        )}
        {books.length === 0 ? (
          // 빈 단 = 판 위에 그 상태의 빈 문구(판이 남아 단이 사라지지 않는다).
          <span
            style={{
              display: 'flex',
              alignItems: 'flex-end',
              height: SHELF_COVER_HEIGHT,
              paddingBottom: 12,
              boxSizing: 'border-box',
              fontSize: 14,
              lineHeight: '20px',
              color: MUTED,
              whiteSpace: 'nowrap',
            }}
          >
            {empty}
          </span>
        ) : (
          books.map((book, index) => {
            const current = book.id === selectedId;
            // 줄의 첫 칸이면 알약을 왼쪽 끝에 붙인다 — 가운데면 넘친 16을 스크롤 컨테이너가 잘라 낸다(사양서 §7.3 clamp).
            const firstCell = index === 0 && !lead;
            return (
              <div key={book.id} data-shelf-item={book.id} style={{ position: 'relative', flex: '0 0 auto', width: SHELF_COVER }}>
                <button
                  type="button"
                  aria-label={book.title}
                  aria-current={current ? 'true' : undefined}
                  data-cover-title={book.title}
                  onClick={() => onSelect(book.id)}
                  style={{ position: 'relative', display: 'flex', padding: 0, border: 'none', background: 'transparent', cursor: 'pointer' }}
                >
                  <BookCover url={book.coverUrl} title={book.title} width={SHELF_COVER} eager={eager} />
                  {current && <CheckBadge />}
                </button>
                {current && canWrite && (
                  // 고른 표지에 반 걸친 검정 알약(안 1 §6.2 #15) — 이 화면의 주 동작 하나. 표지 칸 안에 두어 가로 스크롤 때 표지를 따라간다.
                  // 맨 <button>인 이유: TDS Button은 `--button-min-height: 56px`를 박아 솟는다. z 1 — 첫 칸이면 이웃 표지에 12 걸치는데, 이웃 칸도
                  // positioned라 z가 없으면 DOM 순서상 이웃 표지가 알약 글자를 덮는다(목 모드 실측). 알약 안엔 안내 딤이 없어 가둘 것도 없다.
                  <button
                    type="button"
                    data-margin-pill=""
                    disabled={busy}
                    onClick={() => onCompose(book)}
                    style={{
                      position: 'absolute',
                      top: SHELF_COVER_HEIGHT - PILL_SPILL,
                      left: firstCell ? 0 : (SHELF_COVER - PILL_WIDTH) / 2,
                      zIndex: 1,
                      width: PILL_WIDTH,
                      height: PILL_HEIGHT,
                      padding: 0,
                      border: 'none',
                      borderRadius: 999,
                      background: 'var(--adaptiveBlue700, #1D1B18)',
                      color: 'var(--filledInk, #F5F0E6)',
                      fontSize: 15,
                      fontWeight: 500,
                      lineHeight: '20px',
                      whiteSpace: 'nowrap',
                      cursor: 'pointer',
                    }}
                  >
                    여백에 글쓰기
                  </button>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );

  return (
    <section data-shelf={status}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 26, marginBottom: 10 }}>
        {/* 이름 + 권수 — H3 18/26 명조, 권수만 muted(안 1 `titleGroup`). 0은 박제하지 않는다(빈 문구가 이미 말한다). */}
        <h2 style={{ margin: 0, display: 'flex', alignItems: 'baseline', gap: 6, ...SERIF_VALUE, fontSize: 18, lineHeight: '26px' }}>
          <span style={{ color: INK }}>{title}</span>
          {books.length > 0 && <span style={{ color: MUTED }}>{books.length}</span>}
        </h2>
        {books.length > 1 && (
          // 글자 링크 — 보이는 20, 누름 44(음수 여백으로 머리 높이는 그대로).
          <button
            type="button"
            onClick={onOpenGrid}
            style={{
              minHeight: 44,
              margin: '-9px 0',
              padding: 0,
              border: 'none',
              background: 'transparent',
              color: MUTED,
              fontSize: 14,
              lineHeight: '20px',
              cursor: 'pointer',
            }}
          >
            펼쳐보기 ›
          </button>
        )}
      </div>
      {lead ? (
        // 안내는 「책 추가」 칸이 든 줄을 감싼다 — 칸만 감싸면 말풍선이 가로 스크롤 줄 안의 flex 칸이 되어 잘린다.
        // 게이트(`lead`)가 없는 날엔 감싸지도 않아 없는 칸을 가리키는 안내가 원리상 생기지 않는다.
        <Coachmark
          name="add-book"
          after="library" // 바로 앞 걸음(서재 설명)을 본 뒤에 — 흐름이 이 화면으로 데려온 직후가 제 차례다
          title="읽을 책은 여기서 찾아 담아요"
          detail="맨 앞 점선 칸에서 검색해 담아요"
          enabled={lead}
          // 줄 전체를 감싸 기본(50%)이면 꼬리가 고른 표지의 알약을 가리킨다 — 맨 앞 「책 추가」 칸 가운데로.
          tailLeft={`${SHELF_COVER / 2}px`}
        >
          {row}
        </Coachmark>
      ) : (
        row
      )}
    </section>
  );
}

/**
 * 책장 — 선반 세 단 → 펼친 책 → 여백 박스(안 1 §7.2 순서 그대로 · D6 추천). 탭 세 칸 + 한 권씩 캐러셀을 대신한다(에디토리얼 PR-3).
 *
 * <p>세 상태가 늘 함께 서서 모든 책이 한눈에 들고, 선반은 가로로 넘쳐 권수가 늘어도 높이가 고정이다. 고르기는 표지를 눌러서 —
 * 고른 표지엔 ✓와 검정 알약이 걸리고, 그 책의 제목·칩·관리와 여백 글은 아래 「펼친 책」·「여백」에 이어진다. 선반 높이가 고정이라
 * 고르기가 바뀌어도 아래가 들썩이지 않는다. 액션 넷은 「관리」 시트 뒤로 접혀 있다.
 */
export function Shelf({
  books,
  selectedId,
  sheet,
  busy,
  myLoginId,
  searchEnabled,
  onSelect,
  onSheet,
  onAction,
  onOpenMargin,
  onComposeMargin,
  onError,
  onAddBook,
}: {
  books: MyBookSummary[];
  /** 책 id 또는 `undefined`(아직 안 고름 → 기본 선택). */
  selectedId: number | undefined;
  sheet: LibrarySheet;
  busy: boolean;
  /** 없으면 여백 손잡이·박스를 그리지 않는다 — 눌러도 서버가 대상을 못 찾는다(설계 결정 A). */
  myLoginId: string | null;
  /** 서버가 검색을 껐으면 「책 추가」 칸도 안내도 세우지 않는다 — 눌러도 못 가는 칸을 남기지 않는다. */
  searchEnabled: boolean;
  onSelect: (bookId: number) => void;
  onSheet: (sheet: LibrarySheet) => void;
  onAction: (action: BookAction) => void;
  onOpenMargin: (loginId: string, bookId: number) => void;
  onComposeMargin: (book: MyBookSummary) => void;
  onError: (error: Error) => void;
  onAddBook: () => void;
}) {
  const sections = shelfSections(books);
  const selected = shelfSelection(books, selectedId);
  const rows = useRef<Partial<Record<BookStatus, HTMLDivElement | null>>>({});

  // 고른 책이 선반 밖에 있으면 그 표지로 민다 — 추가 직후 · 격자에서 고름 · 관리 시트로 다른 선반에 옮긴 뒤. 이미 보이면 그대로다.
  useEffect(() => {
    if (selected === null) return;
    const row = rows.current[selected.status];
    const cell = row?.querySelector<HTMLElement>(`[data-shelf-item="${selected.id}"]`);
    if (row == null || cell == null) return;
    // 알약은 고른 표지에 붙는다 — 렌더 조건(`current && canWrite`)과 같은 `myLoginId !== null`로 범위를 고른다.
    revealCell(row, cell.offsetLeft, myLoginId !== null);
    // 위치를 정하는 건 고른 책과 그 책의 선반이다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.id, selected?.status]);

  const gridSection = sheet?.kind === 'grid' ? (sections.find((s) => s.status === sheet.status) ?? null) : null;

  return (
    <>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {sections.map((section, index) => (
          <ShelfRow
            key={section.status}
            section={section}
            selectedId={selected?.id ?? null}
            lead={section.status === 'READING' && searchEnabled}
            eager={index === 0}
            busy={busy}
            canWrite={myLoginId !== null}
            rowRef={(el) => {
              rows.current[section.status] = el;
            }}
            onSelect={onSelect}
            onAddBook={onAddBook}
            onCompose={onComposeMargin}
            onOpenGrid={() => onSheet({ kind: 'grid', status: section.status })}
          />
        ))}
      </div>

      {selected === null ? (
        // 0권 — 펼칠 책이 없다. 선반 셋은 그대로 서 있고(빈 문구), 여기는 다음 한 걸음을 말한다.
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28, wordBreak: 'keep-all' }}>
          아직 책이 없어요. 읽고 있는 책을 추가하면 측정할 때 고를 수 있어요.
        </Text>
      ) : (
        <>
          <OpenBook
            book={selected}
            busy={busy}
            onManage={() => onSheet({ kind: 'actions', confirmDelete: false, confirmPublish: false })}
          />
          {myLoginId !== null && (
            <MarginBox
              loginId={myLoginId}
              bookId={selected.id}
              onError={onError}
              onOpenAll={() => onOpenMargin(myLoginId, selected.id)}
            />
          )}
        </>
      )}

      {gridSection !== null && (
        <GridSheet
          title={`${gridSection.title} ${gridSection.books.length}권`}
          rows={gridSection.books}
          selectedId={selected?.id ?? null}
          onPick={(id) => {
            onSelect(id);
            onSheet(null);
          }}
          onClose={() => onSheet(null)}
        />
      )}
      {sheet?.kind === 'actions' && selected !== null && (
        <ActionSheet
          book={selected}
          busy={busy}
          confirmDelete={sheet.confirmDelete}
          confirmPublish={sheet.confirmPublish}
          onConfirmDelete={(confirm) => onSheet({ kind: 'actions', confirmDelete: confirm, confirmPublish: false })}
          onConfirmPublish={(confirm) => onSheet({ kind: 'actions', confirmDelete: false, confirmPublish: confirm })}
          onAction={onAction}
          onClose={() => onSheet(null)}
        />
      )}
    </>
  );
}

/**
 * 펼친 책 — 고른 책의 작은 표지(56×78) + ✓ · 제목 · 저자 · [칩 ↔ 「관리」](안 1 §7.2 `open-book`). 선반 바로 아래에 선다.
 *
 * <p>작은 표지의 ✓는 선반의 고른 표지와 <b>같은 배지</b>다 — 고른 책이 1단이고 펼친 책이 3단 아래여도 「어느 책 이야기인가」가
 * 이어진다(계획 §4 L2). 제목은 맨 요소에 line-clamp 2 — TDS `Text`는 `display`를 덮어 line-clamp가 죽는다(BookGrid 주석과 같다).
 */
function OpenBook({ book, busy, onManage }: { book: MyBookSummary; busy: boolean; onManage: () => void }) {
  return (
    <div data-open-book="" style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginTop: 28 }}>
      <span style={{ position: 'relative', display: 'inline-flex', flex: '0 0 auto' }}>
        <BookCover url={book.coverUrl} title={book.title} width={56} />
        <CheckBadge />
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            ...SERIF_VALUE,
            fontSize: 22,
            lineHeight: '30px',
            color: INK,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'keep-all',
          }}
        >
          {book.title}
        </div>
        <div style={{ marginTop: 2, fontSize: 14, lineHeight: '20px', color: MUTED }}>{metaLine(book)}</div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8, paddingTop: 8 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 5, minWidth: 0 }}>
            {bookStats(book).map(({ label, tone, value }) => (
              <span key={label} data-book-chip="" style={bookChipStyle(tone)}>
                {value === undefined ? (
                  label
                ) : (
                  <>
                    <span style={SERIF_VALUE}>{value}</span>
                    {label.slice(value.length)}
                  </>
                )}
              </span>
            ))}
          </div>
          {/* 공개 칩(바꾸는 대상) 바로 옆 — 외곽선 알약은 보이는 38, 누름은 바깥 버튼이 44로 받는다. */}
          <button
            type="button"
            disabled={busy}
            onClick={onManage}
            style={{ flex: '0 0 auto', height: 44, padding: 0, border: 'none', background: 'transparent', cursor: 'pointer' }}
          >
            <span
              style={{
                ...OUTLINE_PILL,
                display: 'inline-flex',
                alignItems: 'center',
                boxSizing: 'border-box',
                height: HANDLE_ROW_HEIGHT,
                padding: '0 20px',
                fontSize: 15,
                fontWeight: 500,
              }}
            >
              관리
            </span>
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * 펼쳐보기 — 목록의 책을 3열 격자로 한 번에. 고르면 그 책이 골라지고(선반이 그 표지로 밀린다) 시트는 닫힌다.
 * 서재(내 책장)와 책방(남의 공개 책)이 같은 시트를 쓴다 — 표지·제목만 필요해 타입도 그만큼만 받는다.
 */
export function GridSheet({
  title,
  rows,
  selectedId,
  onPick,
  onClose,
}: {
  title: string;
  rows: { id: number; title: string; coverUrl: string | null }[];
  selectedId: number | null;
  onPick: (bookId: number) => void;
  onClose: () => void;
}) {
  return (
    <Sheet title={title} onClose={onClose}>
      <BookGrid rows={rows} selectedId={selectedId} onPick={onPick} />
    </Sheet>
  );
}

/**
 * 격자 본체 — 시트 껍데기에서 꺼냈다. 책방은 이 격자를 <b>본문에 그대로</b> 펼쳐 공개 책 목록으로 쓰고,
 * 칸을 누르면 그 책의 여백이 열린다. 서재는 여전히 시트 안에서 「고르는 격자」로 쓴다.
 *
 * <p>{@code onPick}이 없으면 각 칸을 <b>버튼으로 만들지 않는다</b> — 눌러도 아무 일이 없으면 그게 곧
 * 죽은 UI다(카운트 손잡이 #788과 같은 규율).
 *
 * <p>{@code fresh}는 <b>책방 전용 선택 필드</b>다(24시간 안에 새 글 = 여백 발광). 서재는 안 넘기므로
 * 그쪽 마크업은 한 글자도 달라지지 않는다.
 */
export function BookGrid({
  rows,
  selectedId,
  onPick,
}: {
  rows: { id: number; title: string; coverUrl: string | null; fresh?: boolean }[];
  /** 테두리로 표시할 책 — 고르는 격자에서만 의미가 있다. */
  selectedId: number | null;
  onPick?: (bookId: number) => void;
}) {
  return (
    // `start`가 없으면 칸이 그 줄 최대 높이로 늘어나는데, 늘어난 `<button>`은 남는 높이만큼 내용을
    // 세로 가운데로 미는 UA 기본 동작이 있어 **긴 제목 옆 짧은 제목 칸의 표지만 아래로 내려앉는다**.
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,minmax(0,1fr))', gap: 12, alignItems: 'start' }}>
      {rows.map((book) => {
        const fresh = book.fresh === true;
        const cell = (
          <>
            <div
              style={{
                display: 'flex',
                justifyContent: 'center',
                // 지금 고른 책만 테두리로 — 격자에서 "내가 보던 그 책"을 잃지 않게.
                outline: book.id === selectedId ? '2px solid var(--adaptiveBlue500, #1D1B18)' : undefined,
                outlineOffset: 2,
                borderRadius: 4,
              }}
            >
              {/* 표지를 감싸는 칸 — 발광 테두리·점 배지가 셀 폭이 아니라 표지에 붙어야 한다. */}
              <span className={fresh ? 'margin-fresh' : undefined} style={{ position: 'relative', display: 'inline-flex' }}>
                <BookCover url={book.coverUrl} title={book.title} width={80} />
                {/* pulse는 움직임이라 `prefers-reduced-motion`에서 멈춘다 — 그때도 이 점은 남는다. */}
                {fresh && (
                  <span
                    data-fresh-dot=""
                    aria-hidden="true"
                    style={{
                      position: 'absolute',
                      top: -4,
                      right: -4,
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      background: 'var(--adaptiveBlue500, #1D1B18)',
                      border: '2px solid var(--adaptiveGrey100, #F5F0E6)',
                    }}
                  />
                )}
              </span>
            </div>
            {/* 긴 제목을 두 줄에서 끊지는 않는다 — TDS `Text`가 인라인 `display`를 자기 값으로 덮어써
                (`-webkit-box` → `inline-block`) line-clamp가 죽는다. 줄이 벌어져도 제목은 다 보인다. */}
            {/* 가운데 정렬은 아래 셀(`data-grid-title` div·button)의 `textAlign`을 상속받는다 — 여기 적어도
                TDS가 걸러내 죽은 키였다(T-216. 목 모드 실측: 상속만으로 이미 `center`라 래퍼는 불필요). */}
            <Text typography="st12" style={{ display: 'block', marginTop: 6, wordBreak: 'keep-all' }}>
              {book.title}
            </Text>
          </>
        );

        // 색·움직임만으로는 구분 못 하는 사람이 있다 — 발광을 이름에도 남긴다(옛 여백 링의 관례 계승).
        const label = fresh ? `${book.title} 새 글` : undefined;

        return onPick === undefined ? (
          <div key={book.id} data-grid-title={book.title} aria-label={label} style={{ textAlign: 'center' }}>
            {cell}
          </div>
        ) : (
          <button
            key={book.id}
            type="button"
            data-grid-title={book.title}
            aria-label={label}
            onClick={() => onPick(book.id)}
            style={{ padding: 0, border: 'none', background: 'transparent', cursor: 'pointer', textAlign: 'center' }}
          >
            {cell}
          </button>
        );
      })}
    </div>
  );
}

/**
 * 관리 시트 — 상태 이동 둘 · 공개 전환 · 삭제. 삭제와 <b>공개 전환</b>은 이 시트 안에서 한 번 더 확인한다.
 *
 * <p>확인 단계에서는 나머지 길을 **감춘다** — 확인 문구 옆에 다른 버튼이 남아 있으면 무엇을 확인하는
 * 중인지 흐려지고, 손가락이 옆 버튼으로 미끄러질 자리도 생긴다.
 *
 * <p>공개 전환 확인은 <b>글이 있는 비공개 책에만</b> 뜬다({@link needsPublishConfirm}) — 되돌리는
 * 방향이나 샐 글이 없는 책까지 물으면 확인이 습관적 탭이 되어 정작 새는 순간에도 안 읽힌다.
 */
function ActionSheet({
  book,
  busy,
  confirmDelete,
  confirmPublish,
  onConfirmDelete,
  onConfirmPublish,
  onAction,
  onClose,
}: {
  book: MyBookSummary;
  busy: boolean;
  confirmDelete: boolean;
  confirmPublish: boolean;
  onConfirmDelete: (confirm: boolean) => void;
  onConfirmPublish: (confirm: boolean) => void;
  onAction: (action: BookAction) => void;
  onClose: () => void;
}) {
  if (confirmPublish) {
    return (
      <Sheet title={book.title} onClose={onClose}>
        <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 12, wordBreak: 'keep-all' }}>
          여백에 남긴 글 {book.storyCount}개가 누구에게나 보여요.
        </Text>
        {/* 파괴가 아니라 노출이다 — danger 색은 쓰지 않는다. 행 라벨과 같은 말이라 무엇을 확정하는지 분명하다. */}
        <Button display="block" disabled={busy} onClick={() => onAction({ kind: 'visibility', book })}>
          공개로 바꾸기
        </Button>
        <Button
          display="block"
          variant="weak"
          style={{ marginTop: 8 }}
          disabled={busy}
          onClick={() => onConfirmPublish(false)}
        >
          취소
        </Button>
      </Sheet>
    );
  }

  if (confirmDelete) {
    return (
      <Sheet title={book.title} onClose={onClose}>
        <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 12, wordBreak: 'keep-all' }}>
          서재에서 빼면 이 책에 쌓인 기록도 함께 사라져요.
        </Text>
        <Button display="block" color="danger" disabled={busy} onClick={() => onAction({ kind: 'delete', book })}>
          정말 삭제
        </Button>
        <Button
          display="block"
          variant="weak"
          style={{ marginTop: 8 }}
          disabled={busy}
          onClick={() => onConfirmDelete(false)}
        >
          취소
        </Button>
      </Sheet>
    );
  }

  return (
    <Sheet title={book.title} onClose={onClose}>
      {SECTIONS.filter(({ status }) => status !== book.status).map(({ status, title }) => (
        <SheetRow
          key={status}
          label={`${title}(으)로 옮기기`}
          busy={busy}
          onClick={() => onAction({ kind: 'status', book, status })}
        />
      ))}
      <SheetRow
        label={book.isPublic ? '비공개로 바꾸기' : '공개로 바꾸기'}
        busy={busy}
        onClick={() => (needsPublishConfirm(book) ? onConfirmPublish(true) : onAction({ kind: 'visibility', book }))}
      />
      {/* 구매는 삭제 위다 — 위험한 것이 목록 끝에 남는 배치를 지킨다. */}
      <BuyRow link={book.purchaseLink} busy={busy} />
      <SheetRow label="서재에서 삭제" busy={busy} danger onClick={() => onConfirmDelete(true)} />
    </Sheet>
  );
}

/** 시트 안 액션 한 줄 — 손가락 몫(48px)을 확보한 넓은 버튼. 위험한 것만 색으로 구분한다. */
function SheetRow({
  label,
  busy,
  danger = false,
  onClick,
}: {
  label: string;
  busy: boolean;
  danger?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={busy}
      onClick={onClick}
      style={{
        // 시트 행 = 아래 가는 선(RULE_ROW) — 면 없이 줄이 버튼으로 읽힌다(에디토리얼).
        ...RULE_ROW,
        display: 'block',
        width: '100%',
        padding: '15px 0',
        color: danger ? '#A32D2D' : 'var(--adaptiveGrey800, #1D1B18)',
        fontSize: 15,
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      {label}
    </button>
  );
}

/**
 * 손잡이 높이 38 — 원래 「책 추가하기」(TDS `Button size="medium" display="block"`)의 <b>실측</b> 높이였다(목 모드 390px).
 * 서재는 에디토리얼 PR-3에서 손잡이 줄이 사라지고 펼친 책의 「관리」 외곽선 알약(보이는 높이)만 이 값을 쓴다.
 * **공부 서재(`StudyLibrary`)가 가져다 쓰는 export라 지우지 않는다** — 그쪽 손잡이 줄은 그대로다(D9).
 */
export const HANDLE_ROW_HEIGHT = 38;

/** 테두리만 있는 작은 손잡이(외곽선 알약) — 공부 서재의 제목 줄 「펼쳐보기」가 쓴다(서재는 PR-3에서 선반 머리 글자 링크로 갔다). */
export const handleStyle = {
  ...OUTLINE_PILL,
  flex: '0 0 auto',
  padding: '8px 14px',
  fontSize: 14,
  cursor: 'pointer',
} as const;

/** 책 검색 — 알라딘 1페이지. 탭하면 어디에 담을지 먼저 묻는다(`AddStatusSheet`). */
export function BookSearch({
  busy,
  error,
  onAdd,
  onFail,
  onOpenBookMargin,
}: {
  busy: boolean;
  error: string | null;
  onAdd: (row: SearchRow, status: BookStatus) => void;
  onFail: (error: Error) => void;
  /** 검색 행의 「여백 N」 배지 — 낯선 책의 책축 여백으로 가는 유일한 문(2026-08-22). */
  onOpenBookMargin: (isbn13: string) => void;
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchRow[] | null>(null);
  /** isbn13 → 함께 걸린 글 수. 서버가 페이지당 1쿼리로 세 준다(0인 책은 키 자체가 없다). */
  const [marginCounts, setMarginCounts] = useState<Record<string, number>>({});
  const [searching, setSearching] = useState(false);
  /** 담을 곳을 고르는 중인 책 — 없으면 시트도 없다(진입 직후 화면을 덮지 않는다, T-183). */
  const [picking, setPicking] = useState<SearchRow | null>(null);
  /** 추천 — 늦게 와도 검색을 막지 않는다. 실패는 「추천 없음」으로 흡수한다(있으면 좋은 것이다). */
  const [reco, setReco] = useState<Recommendation | null>(null);

  useEffect(() => {
    let alive = true;
    fetchRecommendation()
      .then((r) => {
        if (alive) setReco(r);
      })
      .catch(() => {
        // 조용히 접는다 — 추천이 없다고 「책 추가」가 실패한 것처럼 보이면 안 된다.
      });
    return () => {
      alive = false;
    };
  }, []);

  // 열린 시트는 뒤로가기가 먼저 먹는다 — 없으면 고르다 만 시트가 검색 화면째로 닫힌다(서재 시트와 같다).
  useBackClose(picking !== null, () => setPicking(null));

  const submit = () => {
    setSearching(true);
    searchBooks(query.trim())
      .then((page) => {
        setResults(page.results);
        setMarginCounts(page.marginCounts ?? {}); // 옛 서버는 맵을 안 준다 — 그러면 배지가 하나도 안 뜬다
      })
      .catch(onFail)
      .finally(() => setSearching(false));
  };

  return (
    <Screen title="책 추가">
      {/* 손잡이는 칸 안이다 — 아래 전폭 버튼은 엔터가 살아난 뒤로 자리만 먹었다(`SearchField` 주석). */}
      <SearchField
        label="책 제목"
        placeholder="책 이름을 적어주세요"
        value={query}
        disabled={busy}
        busy={searching}
        onChange={setQuery}
        onSubmit={submit}
      />

      <ErrorMessage message={error} />

      {/* 아직 검색하지 않았을 때만 추천을 세운다 — 둘이 같은 화면에 있으면 무엇이 결과인지 흐려진다. */}
      {results === null && reco !== null && (
        <RecommendCard data={reco} busy={busy} onPick={(row) => setPicking(row)} />
      )}

      {results !== null && results.length === 0 && (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 20 }}>
          검색 결과가 없어요. 제목을 조금 다르게 적어 보세요.
        </Text>
      )}

      {results?.map((row, index) => (
        <SearchResultRow
          key={row.isbn13 ?? `${row.title}-${index}`}
          row={row}
          busy={busy}
          onPick={() => setPicking(row)}
          marginCount={row.isbn13 === null ? undefined : marginCounts[row.isbn13]}
          onOpenMargin={row.isbn13 === null ? undefined : () => onOpenBookMargin(row.isbn13 as string)}
        />
      ))}

      {picking && (
        <AddStatusSheet
          row={picking}
          busy={busy}
          // 고른 즉시 닫는다 — 열어 둔 채 실패하면 딤이 에러 문구를 덮고, 성공하면 어차피 서재로 나간다.
          onPick={(status) => {
            setPicking(null);
            onAdd(picking, status);
          }}
          onClose={() => setPicking(null)}
        />
      )}
    </Screen>
  );
}

/**
 * 검색 결과 한 줄. 이미 서재에 있는 책은 <b>두 겹으로</b> 말한다 — 읽어야 아는 칩(「서재에 있어요」)과,
 * 읽지 않아도 보이는 형태(`.book-owned`: 눕힌 종이 · 표지 도장 · 접힌 모서리).
 *
 * <p>둘 다 필요한 이유는 이 행이 <b>`disabled`</b>이기 때문이다. 예전엔 저자 이름 뒤에
 * 「 · 이미 서재에 있어요」를 이어 붙였는데, 저자와 같은 `st12 · grey600` 한 벌이라 이름의 일부처럼
 * 읽혔다 — 그 한 줄을 놓치면 탭이 막힌 이유를 알 길이 없어 앱이 멈춘 것으로 보인다(사용자 제보
 * 2026-08-19). 문구는 훑는 사람을 지나치므로, 형태가 먼저 말하고 문구가 확인해 주는 순서로 뒤집었다.
 *
 * <p>`BookSearch` 안에 인라인으로 두지 않고 꺼낸 데는 계측 이유도 있다: 정적 렌더 하니스에서는
 * 검색 응답이 안 돌아 `results`가 영영 `null`이라, 행이 그 안에 있으면 어떤 단언도 세울 수 없다.
 */
export function SearchResultRow({
  row,
  busy,
  onPick,
  marginCount,
  onOpenMargin,
}: {
  row: SearchRow;
  busy: boolean;
  onPick: () => void;
  /**
   * 이 책에 함께 걸린 글 수 — 「여백 N」 배지(2026-08-22 책축 개방). <b>0·`undefined`면 배지가 없다</b>:
   * 서버가 0인 책은 키 자체를 안 주므로, 빈 상태를 숫자로 박제하지 않는 근거가 응답에 그대로 있다.
   */
  marginCount?: number;
  /** 배지 손잡이 — 있으면 눌러서 「이 책의 여백」으로 간다(`onToggleLike` 관례). */
  onOpenMargin?: () => void;
}) {
  // 배지는 행 <b>바깥</b>의 형제다 — 버튼 안에 버튼을 넣으면 마크업이 깨지고, 담긴 책은 행이
  // `disabled`라 안쪽에 있으면 눌리지도 않는다(담긴 책일수록 그 책의 여백을 보고 싶다).
  const badge =
    marginCount !== undefined && marginCount > 0 && onOpenMargin !== undefined ? (
      <button type="button" aria-label={`이 책의 여백 ${marginCount}개 보기`} onClick={onOpenMargin} style={marginBadgeStyle}>
        여백 {marginCount}
      </button>
    ) : null;

  const main = (
    <button
      type="button"
      className={row.owned ? 'book-owned' : undefined}
      disabled={busy || row.owned}
      onClick={onPick}
      style={{
        ...rowStyle,
        position: 'relative', // 접힌 모서리(`.book-owned::after`)가 이 박스를 기준으로 앉는다
        flex: 1,
        minWidth: 0,
        marginTop: badge === null ? 8 : 0,
        borderRadius: 12,
        // 담긴 책은 한 톤 가라앉힌다 — 면(`--adaptiveGrey100`)보다 어두워야 「지나간 칸」으로 읽힌다. Soft에서 그
        // 「가라앉은 바탕」은 눌린 면 색(`--softDent`)이다(옛 베이지 #EFE9DC 자리 — 공부 모드·밤도 토큰이 데려간다).
        background: row.owned ? 'var(--softDent, #ECE5D8)' : 'var(--adaptiveGrey100, #F5F0E6)',
      }}
    >
      {/* 표지만 흐리고 도장은 또렷해야 하므로 흐림을 안쪽 겹에 건다(바깥에 걸면 도장까지 바랜다). */}
      <span style={{ position: 'relative', flex: '0 0 auto', display: 'inline-flex' }}>
        <span style={{ display: 'inline-flex', opacity: row.owned ? 0.62 : 1 }}>
          <BookCover url={row.coverUrl} title={row.title} />
        </span>
        {row.owned && (
          <span style={ownedStampStyle}>
            <OwnedCheck color="var(--adaptiveGrey100, #F5F0E6)" />
          </span>
        )}
      </span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div>
          <Text typography="st11" style={clampLine}>
            {row.title}
          </Text>
        </div>
        <div style={{ marginTop: 4 }}>
          <Text typography="st12" color="grey600" style={clampLine}>
            {authorLine(row)}
          </Text>
        </div>
        {row.owned && (
          <span style={ownedChipStyle}>
            <OwnedCheck color="var(--adaptiveBlue700, #1D1B18)" />
            서재에 있어요
          </span>
        )}
      </div>
    </button>
  );

  // 배지가 없으면 감싸지 않는다 — 대다수 행이 그 경우라, 없는 자리에 div를 남기지 않는다.
  return badge === null ? (
    main
  ) : (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 8 }}>
      {main}
      {badge}
    </div>
  );
}

/** 「여백 N」 배지 — 행과 같은 카드지 위에 서지만 테두리로 「누를 수 있는 다른 것」임을 말한다. */
const marginBadgeStyle = {
  flex: '0 0 auto',
  padding: '8px 10px',
  borderRadius: 10,
  border: '1px solid var(--adaptiveGrey200, #D9D1C3)',
  background: 'var(--adaptiveGrey100, #F5F0E6)',
  color: 'var(--adaptiveBlue700, #1D1B18)',
  fontSize: 13,
  fontWeight: 500,
  cursor: 'pointer',
} as const;

/**
 * 추천 카드 — 검색 버튼을 걷고 남은 자리를 채운다(2026-08-21). 사용자 지적:
 * 「책 추가하기 버튼 누르면 이동하는 페이지가 너무 비어보여」(실측 460px · 화면의 55%가 빈 종이였다).
 *
 * <p>제목·근거 문장은 <b>서버가</b> 만든다 — 화면은 어느 전략(내 저자 / 베스트셀러)으로 뽑혔는지 모른 채
 * 라벨을 그대로 그린다. 그래서 서버가 전략을 늘려도 여기는 안 바뀐다.
 *
 * <p>⚠️ <b>안쪽 행은 카드를 쓰지 않는다.</b> 카드지(`#FCFAF5`)가 이미 명도 위쪽 끝이라 안쪽 행을 채우면
 * 어두워지는 방향밖에 없는데, 그 방향엔 <b>이미 「서재에 있어요」 톤(`#EFE9DC`)이 자리를 잡고 있다</b> —
 * 채운 행은 「지나간 칸」으로 읽힌다. 그래서 배경 없이 실선으로만 나눈다.
 *
 * <p>목록은 카드 <b>안에서</b> 굴러간다(`.reco-list`). 잘린 반 줄이 「더 있다」를 말하므로 페이드를
 * 얹지 않는다 — 페이드는 그 신호를 지우는 짓이다.
 */
export function RecommendCard({
  data,
  busy,
  onPick,
}: {
  data: Recommendation;
  busy: boolean;
  onPick: (row: SearchRow) => void;
}) {
  // 머리만 남은 카드를 세우지 않는다 — 빈 카드는 없느니만 못하다.
  if (data.title === null || data.results.length === 0) {
    return null;
  }
  return (
    <section style={sectionStyle}>
      <SectionTitle>{data.title}</SectionTitle>
      {data.reason !== null && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
          {data.reason}
        </Text>
      )}
      <div className="reco-list" style={{ marginTop: 4 }}>
        {data.results.map((row, index) => (
          <RecommendRow
            key={row.isbn13 ?? `${row.title}-${index}`}
            row={row}
            first={index === 0}
            busy={busy}
            onPick={() => onPick(row)}
          />
        ))}
      </div>
    </section>
  );
}

/** 추천 한 줄 — 탭하면 검색 결과와 <b>같은 시트</b>(어디에 담을지)가 열린다. 새 흐름을 만들지 않는다. */
function RecommendRow({
  row,
  first,
  busy,
  onPick,
}: {
  row: SearchRow;
  first: boolean;
  busy: boolean;
  onPick: () => void;
}) {
  return (
    <button
      type="button"
      disabled={busy || row.owned}
      onClick={onPick}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        width: '100%',
        padding: '12px 0',
        border: 0,
        borderTop: first ? 0 : '1px solid rgba(107,101,92,0.18)',
        background: 'transparent',
        textAlign: 'left',
        font: 'inherit',
        color: 'inherit',
        cursor: 'pointer',
      }}
    >
      <BookCover url={row.coverUrl} title={row.title} width={36} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div>
          <Text typography="st11" style={clampLine}>
            {row.title}
          </Text>
        </div>
        <div style={{ marginTop: 2 }}>
          <Text typography="st12" color="grey600" style={clampLine}>
            {authorLine(row)}
          </Text>
        </div>
      </div>
    </button>
  );
}

/**
 * 목록 한 줄의 제목·저자 — <b>각각 한 줄로 자른다</b>.
 *
 * <p>실기기 제보(2026-08-21): 저자 40명짜리 책(「노벨라33 세트 - 전33권」)이 추천 카드 한 줄을 세로
 * 900px로 부풀려 카드를 통째로 먹었다. 카드 창은 `calc(5 × --reco-row + --reco-peek)` = 「75px짜리 줄
 * 5개 + 반 줄」인데 <b>줄 높이가 가정일 뿐 강제가 아니었다</b> — 줄 하나가 창보다 커지면 그 계산도,
 * 반 줄이 말하던 「더 있다」 신호도 함께 죽는다. 서버가 세트를 걸러도 <b>제목·저자가 긴 다음 책</b>이
 * 언제든 오므로, 높이는 데이터가 아니라 화면이 정해야 한다.
 *
 * <p>⚠️ <b>`-webkit-box` line-clamp를 쓰지 않는다</b> — TDS `Text`가 인라인 `display`를 자기 값으로
 * 덮어써 죽는다(서재 격자 제목이 그래서 두 줄 자르기를 포기했다). `display: block` + `nowrap +
 * ellipsis`는 같은 `Text`에서 이미 세 번 검증됐다(둘러보기·기록·홈).
 */
const clampLine = {
  display: 'block',
  overflow: 'hidden',
  textOverflow: 'ellipsis',
  whiteSpace: 'nowrap',
} as const;

/**
 * 목록에 그릴 저자 한 줄 — 서버가 줄여 준 축약본이 우선이다.
 *
 * <p>원문(`author`)으로 떨어지는 갈래는 <b>배포 순서 방어</b>다: 미니앱이 서버보다 먼저 나가면 옛 서버는
 * `authorShort`를 안 준다. 그때 저자 줄이 빈칸이 되면 배포 순서에 화면이 의존하게 된다.
 * 축약본이 `null`인 경우(글쓴이 없이 옮긴이만 있는 책)도 원문이 「저자 미상」보다 정보가 많다.
 */
function authorLine(row: SearchRow): string {
  return row.authorShort ?? row.author ?? '저자 미상';
}

/** 담김 체크 — 기본 이모지를 쓰지 않기로 해서(2026-08-18) 선 하나로 그린다. 뜻은 옆 글자가 진다. */
function OwnedCheck({ color }: { color: string }) {
  // 색은 `style.color` → `currentColor`로 흘린다 — SVG 표현 속성(`stroke="var(--x)"`)은 CSS 변수를 못 푼다.
  return (
    <svg width="11" height="11" viewBox="0 0 12 12" fill="none" aria-hidden="true" style={{ color }}>
      <path d="M2 6.4 L4.6 9 L10 3" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** 표지 귀퉁이의 도장. 테두리가 행 바탕(눌린 면)과 같은 색이라 표지에 찍혀 파인 것처럼 보인다. */
const ownedStampStyle = {
  position: 'absolute',
  right: -5,
  bottom: -3,
  width: 20,
  height: 20,
  borderRadius: '50%',
  background: 'var(--adaptiveBlue500, #1D1B18)', // 모드 강조색(공부 모드면 파랑)
  border: '1.5px solid var(--softDent, #ECE5D8)', // 담긴 행 바탕과 같은 토큰
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
} as const;

/** 「서재에 있어요」 칩 — 모드색 옅은 채움 + 한 톤 진한 테두리 + 진한 잉크. 전부 토큰이라 공부 모드가 데려간다. */
const ownedChipStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 4,
  marginTop: 6,
  padding: '1px 8px 2px',
  borderRadius: 8,
  border: '1px solid var(--adaptiveBlue300, #9C9182)',
  background: 'var(--accentPill, rgba(29, 27, 24, .06))',
  color: 'var(--adaptiveBlue700, #1D1B18)', // 옅은 채움 위에서 읽히는 진한 잉크
  fontSize: 13,
  lineHeight: 1.5,
} as const;

/**
 * 제휴 구매 줄 — 링크가 없으면 아무것도 그리지 않는다(수동 등록 책·옛 데이터).
 *
 * <p><b>고지문구를 같은 컴포넌트 안에 묶은 이유</b>: 시트 두 곳(관리·담기)이 이걸 쓰는데 각자
 * 조립하게 두면 한쪽에서 고지를 빠뜨리기 쉽다. 살 곳 없이 「수수료를 받는다」만 남는 것도, 살 곳만
 * 있고 고지가 없는 것도 둘 다 사고다 — 한 몸이면 어느 쪽도 일어날 수 없다.
 *
 * <p>모바일에서도 제휴 귀속이 유지되는 것은 운영 링크로 실측했다(2026-08-22): 알라딘은 모바일 UA를
 * 만나면 `m/mproduct.aspx`로 3회 리다이렉트하지만 `ttbkey`·`partner`를 그대로 들고 가 `partner`
 * 쿠키를 심는다. Yes24가 목적지를 모바일 메인으로 갈아치워 추적을 잃는 것(T-128)과 정반대라,
 * 100% 모바일인 미니앱에 붙일 수 있는 유일한 제공자다.
 *
 * <p>{@link openExternal}은 앱 안에서는 기기 브라우저로, 목 모드에서는 새 창으로 떨어뜨린다 —
 * 어느 쪽이든 미니앱을 떠나므로 시트를 따로 닫지 않는다(돌아오면 그대로 열려 있다).
 */
function BuyRow({ link, busy }: { link: string | null; busy: boolean }) {
  if (link === null || link === '') {
    return null;
  }
  return (
    <>
      <SheetRow label="알라딘에서 구매" busy={busy} onClick={() => openExternal(link)} />
      {/*
        위아래 간격이 비대칭인 것은 의도다 — 관리 시트에서는 이 문구 <b>아래</b>에 「서재에서 삭제」가 온다.
        간격이 6px대 10px이던 첫 시안에서는 소속이 애매해 삭제 버튼의 설명처럼 읽혔다(목 모드 실측).
        위 4px·아래 18px로 벌려 이 문구가 어느 줄에 딸린 것인지를 거리만으로 알 수 있게 한다.
      */}
      <Text
        typography="st12"
        color="grey600"
        style={{ display: 'block', margin: '-4px 2px 18px', wordBreak: 'keep-all' }}
      >
        제휴 링크예요. 구매하시면 일부 수수료를 받을 수 있어요.
      </Text>
    </>
  );
}

/**
 * 담을 곳 고르기 — 서재 「관리」 시트와 <b>같은 껍데기·같은 어휘</b>다(`SECTIONS` 단일 출처).
 *
 * <p>예전엔 검색 결과를 탭하면 되묻지 않고 「읽는 중」으로 넣었다. 가장 잦은 의도라는 이유였지만,
 * 읽고 싶어 담은 책까지 읽는 중이 된 뒤 서재에서 다시 옮겨야 했다 — 한 탭을 아끼려다 세 탭을 물렸다.
 * 순서가 곧 자주 쓰는 순이라 「읽는 중」은 여전히 첫 줄, 손가락이 가장 먼저 닿는 자리다.
 */
export function AddStatusSheet({
  row,
  busy,
  onPick,
  onClose,
}: {
  row: SearchRow;
  busy: boolean;
  onPick: (status: BookStatus) => void;
  onClose: () => void;
}) {
  return (
    <Sheet title={row.title} onClose={onClose}>
      {SECTIONS.map(({ status, title }) => (
        <SheetRow key={status} label={`${title}(으)로 담기`} busy={busy} onClick={() => onPick(status)} />
      ))}
      {/* 담기가 이 시트의 본래 일이라 구매는 그 뒤다 — 다만 사러 갈 의향은 여기서 가장 높다. */}
      <BuyRow link={row.purchaseLink} busy={busy} />
    </Sheet>
  );
}

/**
 * 탭 가능한 줄 — button 기본 스타일을 지워 목록 행처럼 보이게 한다(접근성은 button이 맡는다).
 * 표지 + 텍스트의 가로 배치라 flex다. 제목·메타는 텍스트 쪽 안에서 각자 블록으로 쌓인다.
 */
const rowStyle = {
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  width: '100%',
  padding: 16,
  border: 'none',
  background: 'transparent',
  textAlign: 'left',
  cursor: 'pointer',
} as const;
