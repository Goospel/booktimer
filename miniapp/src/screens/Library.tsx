import { Button } from '@toss/tds-mobile';
import type { CSSProperties } from 'react';
import { useCallback, useEffect, useState } from 'react';

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
  BookCover,
  ErrorMessage,
  Loading,
  PENCIL_FRAME,
  Screen,
  SearchField,
  SectionTitle,
  Sheet,
  Text,
  sectionStyle,
} from '../ui';
import { openExternal } from '../toss';
import { BookCarousel, type LeadCard } from './Home';
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
 * 열린 시트 — 「펼쳐보기」(격자)와 「관리」(액션) 둘뿐이고 **동시에 열리지 않는다**.
 *
 * <p>삭제 확인을 시트 밖 독립 state로 두면 열림과 수명이 어긋난다: 확인을 띄운 채 시트를 닫아도
 * 그 확인이 살아남아, 다시 열었을 때 「정말 삭제」가 곧바로 노출됐다(오삭제가 한 탭 거리).
 * 같은 객체에 묶어 두면 시트를 여는 순간 확인이 언제나 `false`로 새로 만들어진다.
 */
export type LibrarySheet =
  | { kind: 'grid' }
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
export function bookStats(book: MyBookSummary): { label: string; tone: BookChipTone }[] {
  const chips: { label: string; tone: BookChipTone }[] = [];
  if (book.seconds > 0) chips.push({ label: `${formatDuration(book.seconds)} 읽음`, tone: 'neutral' });
  chips.push(book.isPublic ? { label: '공개', tone: 'sage' } : { label: '비공개', tone: 'outline' });
  return chips;
}

type BookChipTone = 'neutral' | 'sage' | 'outline';

/** 칩 세 톤 — 소식 배지와 같은 값이다(새 색을 만들지 않는다). */
function bookChipStyle(tone: BookChipTone): CSSProperties {
  const base: CSSProperties = { display: 'inline-block', padding: '2px 9px', borderRadius: 20, fontSize: 12, lineHeight: 1.6 };
  if (tone === 'sage') {
    return { ...base, background: 'var(--adaptiveBlue50, #E7EEE2)', color: 'var(--adaptiveBlue700, #4F6B4C)' };
  }
  if (tone === 'neutral') {
    return { ...base, background: 'var(--adaptiveGrey200, #E4DDD0)', color: 'var(--adaptiveGrey700, #57534A)' };
  }
  return {
    ...base,
    background: 'transparent',
    color: 'var(--adaptiveGrey600, #6F6A5E)',
    border: '1px solid var(--adaptiveGrey200, #E4DDD0)',
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
 * 있다)으로 갈 수 있고, 책을 옮기거나 돌아오면 그 자체가 재조회다.
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

  return (
    <div
      data-margin-box=""
      style={{ marginTop: 16, padding: 14, border: '1px solid transparent',
 borderImage: PENCIL_FRAME, borderRadius: 16, background: '#FFFDF8' }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline' }}>
        <SectionTitle style={{ flex: 1 }}>
          여백{typeof view !== 'string' && <b style={{ color: '#4E6B4A' }}> {entries.length}</b>}
        </SectionTitle>
        <button
          type="button"
          onClick={onOpenAll}
          style={{
            padding: 0,
            border: 'none',
            background: 'transparent',
            color: 'var(--adaptiveGrey600, #6F6A5E)',
            fontSize: 14,
            cursor: 'pointer',
          }}
        >
          전체 보기 ›
        </button>
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
            아직 남긴 글이 없어요
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
 * 갈아끼우지 않고 책장을 다시 받는다 — 상태 변경은 섹션 이동이라 목록 전체가 흔들리기 때문.
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
  const [tab, setTab] = useState<BookStatus>('READING');
  /**
   * 캐러셀에서 가운데 온 것 — 그 탭에 없어지면 `resolveSelected`가 첫 책으로 되돌린다.
   *
   * <p><b>세 값이다</b>: 책 id · `null`(「책 추가」 칸을 고름) · `undefined`(아직 안 고름).
   * 뒤의 둘을 하나로 합치면 서재를 열 때마다 「책 추가」가 가운데인 채로 시작한다 — 홈이 먼저
   * 밟은 구분이다(`App.tsx`의 `timerStartBookId`).
   */
  const [selectedId, setSelectedId] = useState<number | null | undefined>(undefined);
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

  // 검색은 서재를 덮는 별도 화면이다 — 뒤로가기를 「돌아가기」와 같은 자리로 돌린다.
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
    if (action.kind === 'status') run(changeBookStatus(action.book.id, action.status));
    else if (action.kind === 'visibility')
      run(setBookVisibility(action.book.id, action.book.isPublic ? 'PRIVATE' : 'PUBLIC'));
    else run(deleteBook(action.book.id));
  };

  const add = (row: SearchRow, status: BookStatus) => {
    setBusy(true);
    setError(null);
    addBook(row, status)
      .then(() => {
        setMode('shelf');
        setTab(status); // 방금 넣은 책이 있는 탭으로 — 다른 탭에 서 있으면 추가가 아무 일도 안 한 것처럼 보인다
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
        onBack={() => setMode('shelf')}
        onOpenBookMargin={onOpenBookMargin}
      />
    );
  }

  const rows = books?.filter((b) => b.status === tab) ?? [];

  // 로딩 중에도 제목은 남긴다 — 탭을 옮길 때 화면이 통째로 비었다 다시 차는 깜빡임을 줄인다.
  return (
    <Screen
      title="내 서재"
      right={
        // 캐러셀은 한 번에 한 권이라 권수가 늘면 훑기 답답하다 — 격자로 한 번에 보는 길을 제목 줄에 둔다.
        rows.length > 1 ? (
          <button type="button" onClick={() => setSheet({ kind: 'grid' })} style={handleStyle}>
            펼쳐보기
          </button>
        ) : undefined
      }
    >
      {/* 책장을 아예 못 받았을 때만 재시도 — 액션 실패(삭제 거절 등)는 다시 받을 게 아니라 문구만 남긴다. */}
      <ErrorMessage message={error} onRetry={books === null ? load : undefined} />
      {books === null ? (
        error === null && <Loading />
      ) : (
        <>
          <Shelf
            books={books}
            tab={tab}
            selectedId={selectedId}
            sheet={sheet}
            busy={busy}
            myLoginId={myLoginId}
            searchEnabled={searchEnabled}
            onTab={(status) => {
              setTab(status);
              // 「책 추가」 칸을 고른 채 탭을 옮기면 새 탭도 그 칸이 가운데다 — 탭을 바꾼 사람이
              // 보려는 건 그 탭의 책이므로 「아직 안 고름」으로 되돌려 첫 책이 서게 한다.
              setSelectedId(undefined);
            }}
            onSelect={setSelectedId}
            onSheet={setSheet}
            onAction={act}
            onOpenMargin={onOpenMargin}
            onComposeMargin={onComposeMargin}
            onError={onError}
            onAddBook={() => setMode('search')}
          />
        </>
      )}
    </Screen>
  );
}

/**
 * 서재 캐러셀의 0번 칸 — 화면 맨 아래 전폭 「책 추가하기」 버튼이던 것이 여기로 들어왔다(2026-08-21).
 *
 * <p>홈의 「책 없이」 칸과 <b>같은 부품·같은 규약</b>이다(0번은 언제나 특수 칸). 버튼 갈래로 두면
 * 「가운데 온 것이 곧 대상」이라는 단일 문법이 화면마다 갈리고, 그 버튼은 실제로 여백 박스 아래
 * <b>스크롤 끝</b>에 있어 책을 훑다가 닿기까지 멀었다.
 *
 * <p>홈과 뜻이 하나 다르다: 홈의 0번은 <b>측정 대상의 한 갈래</b>(그 자체가 값)인데 여기 0번은
 * <b>검색 화면으로 가는 문</b>이다. 그래서 이 칸이 가운데 오면 아래 손잡이·여백 박스가 통째로 갈린다.
 */
export const ADD_BOOK_CARD: LeadCard = {
  label: '책 추가',
  title: '책 추가',
  subtitle: () => '제목으로 검색해 서재에 담아요',
};

/**
 * 책장 — 상태 탭 + 표지 캐러셀 + 「관리」. 순수 표시라 정적 렌더로 분류·시트를 계측할 수 있다.
 *
 * <p>세로로 3섹션을 전부 나열하던 목록을 대체한다: 세 상태를 **탭으로 접어** 한 판에 담고, 고르기는
 * 홈과 같은 캐러셀 문법(가운데 온 것이 대상)을 그대로 쓴다. 액션 넷은 상시 노출하지 않고 시트 뒤로 접었다 —
 * 늘 보이던 「삭제」가 손가락 한 탭 거리에 있던 것도 이 참에 사라진다.
 */
export function Shelf({
  books,
  tab,
  selectedId,
  sheet,
  busy,
  myLoginId,
  searchEnabled,
  onTab,
  onSelect,
  onSheet,
  onAction,
  onOpenMargin,
  onComposeMargin,
  onError,
  onAddBook,
}: {
  books: MyBookSummary[];
  tab: BookStatus;
  /** 책 id · `null`(「책 추가」 칸을 고름) · `undefined`(아직 안 고름) — 뒤의 둘은 다른 값이다. */
  selectedId: number | null | undefined;
  sheet: LibrarySheet;
  busy: boolean;
  /** 없으면 여백 손잡이·박스를 그리지 않는다 — 눌러도 서버가 대상을 못 찾는다(설계 결정 A). */
  myLoginId: string | null;
  /** 서버가 검색을 껐으면 「책 추가」 칸도 안내도 세우지 않는다 — 눌러도 못 가는 칸을 남기지 않는다. */
  searchEnabled: boolean;
  onTab: (status: BookStatus) => void;
  onSelect: (bookId: number | null) => void;
  onSheet: (sheet: LibrarySheet) => void;
  onAction: (action: BookAction) => void;
  onOpenMargin: (loginId: string, bookId: number) => void;
  onComposeMargin: (book: MyBookSummary) => void;
  onError: (error: Error) => void;
  onAddBook: () => void;
}) {
  /** 게이트와 안내가 한 몸이다 — 이 값 하나가 0번 칸과 코치마크를 함께 켜고 끈다. */
  const leadCard = searchEnabled ? ADD_BOOK_CARD : null;

  // 검색이 꺼진 날의 마지막 폴백 — 캐러셀에 세울 것이 하나도 없으면 옛 문장이 화면을 지킨다.
  if (books.length === 0 && leadCard === null) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        아직 책이 없어요. 읽고 있는 책을 추가하면 측정할 때 고를 수 있어요.
      </Text>
    );
  }

  const section = SECTIONS.find((s) => s.status === tab) ?? SECTIONS[0];
  const rows = books.filter((b) => b.status === tab);
  const selected = resolveSelected(rows, selectedId ?? null);
  /**
   * 「책 추가」 칸이 가운데인가 — 고른 값이 `null`이거나(칸을 골랐다), 이 탭에 세울 책이 아예 없을 때다.
   * `resolveSelected`는 `null`을 「첫 책으로 떨어져라」로 읽으므로 그 값을 여기서 따로 본다.
   */
  const onLeadCard = leadCard !== null && (selectedId === null || selected === null);

  return (
    <>
      {/* 세 탭은 늘 서 있다(권수 0이어도) — 나타났다 사라지면 누르려던 자리가 옮겨 다닌다. */}
      <div style={{ display: 'flex', gap: 6, padding: 3, borderRadius: 10, background: 'var(--adaptiveGrey200, #E4DDD0)' }}>
        {SECTIONS.map(({ status, title }) => {
          const current = status === tab;
          return (
            <button
              key={status}
              type="button"
              aria-current={current ? 'true' : undefined}
              onClick={() => onTab(status)}
              style={{
                flex: 1,
                padding: '9px 0',
                border: 'none',
                borderRadius: 8,
                fontSize: 14,
                cursor: 'pointer',
                background: current ? '#FCFAF5' : 'transparent',
                color: current ? '#2C2C2A' : 'var(--adaptiveGrey700, #57534A)',
              }}
            >
              {title} {books.filter((b) => b.status === status).length}
            </button>
          );
        })}
      </div>

      {selected === null && leadCard === null ? (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28, textAlign: 'center' }}>
          {section.empty}
        </Text>
      ) : (
        <div style={{ marginTop: 20 }}>
          {/* 「이 탭이 비었다」와 「책 추가 칸이 섰다」는 다른 말이다 — 칸의 부제가 탭 사정까지 대신하지 못한다. */}
          {selected === null && (
            <Text typography="st11" color="grey600" style={{ display: 'block', textAlign: 'center' }}>
              {section.empty}
            </Text>
          )}
          {/*
            탭이 바뀌면 목록이 통째로 갈리므로 다시 마운트한다 — 안 그러면 트랙이 옛 탭의 스크롤 자리에 머문다.
            안내는 캐러셀을 감싼다: 「책 추가하기」 버튼이 서 있던 자리를 이 칸이 물려받았고, 게이트(`leadCard`)가
            없는 날엔 감싸지도 않아 없는 칸을 가리키는 안내가 원리상 생기지 않는다.
          */}
          <Coachmark
            name="add-book"
            after="library" // 바로 앞 걸음(서재 설명)을 본 뒤에 — 흐름이 이 화면으로 데려온 직후가 제 차례다
            title="읽을 책은 여기서 찾아 담아요"
            detail="맨 앞 점선 칸에서 검색해 담아요"
            enabled={leadCard !== null}
          >
            <BookCarousel
              key={tab}
              books={rows}
              selectedId={onLeadCard ? null : (selected?.id ?? null)}
              onSelect={onSelect}
              leadCard={leadCard}
              metaOf={metaLine}
              chipsOf={(b) => bookStats(b).map(({ label, tone }) => ({ label, style: bookChipStyle(tone) }))}
            />
          </Coachmark>
          {onLeadCard ? (
            // 칸이 가운데면 할 수 있는 일은 하나뿐이다 — 손잡이도 하나로 갈린다(합폭은 아래 줄과 같다).
            <div style={{ display: 'flex', marginTop: 16 }}>
              <button
                type="button"
                disabled={busy}
                onClick={onAddBook}
                style={{
                  flex: 1,
                  height: HANDLE_ROW_HEIGHT,
                  border: 'none',
                  borderRadius: 14,
                  background: 'rgba(110,138,106,.14)',
                  color: '#4E6B4A',
                  fontSize: 16,
                  fontWeight: 700,
                  cursor: 'pointer',
                }}
              >
                검색해서 담기
              </button>
            </div>
          ) : (
            selected !== null && (
              <>
                {/* 전폭 손잡이 줄 — 비율은 2:1(쓰기가 주, 관리가 보조). */}
                <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
                  {/* 들춰보기는 아래 박스가 맡으므로 손잡이는 작성 직행이다 — 홈 여백 문과 같은 경로. */}
                  {myLoginId !== null && (
                    <button
                      type="button"
                      disabled={busy}
                      onClick={() => onComposeMargin(selected)}
                      style={{
                        flex: 2,
                        height: HANDLE_ROW_HEIGHT,
                        border: 'none',
                        borderRadius: 14,
                        background: 'rgba(110,138,106,.14)',
                        color: '#4E6B4A',
                        fontSize: 16,
                        fontWeight: 700,
                        cursor: 'pointer',
                      }}
                    >
                      여백에 글쓰기
                    </button>
                  )}
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => onSheet({ kind: 'actions', confirmDelete: false, confirmPublish: false })}
                    style={{
                      flex: 1,
                      height: HANDLE_ROW_HEIGHT,
                      border: '1px solid transparent',
                      borderImage: PENCIL_FRAME,
                      borderRadius: 14,
                      background: '#FCFAF5',
                      color: '#2C2C2A',
                      fontSize: 16,
                      fontWeight: 700,
                      cursor: 'pointer',
                    }}
                  >
                    관리
                  </button>
                </div>
                {myLoginId !== null && (
                  <MarginBox
                    loginId={myLoginId}
                    bookId={selected.id}
                    onError={onError}
                    onOpenAll={() => onOpenMargin(myLoginId, selected.id)}
                  />
                )}
              </>
            )
          )}
        </div>
      )}

      {sheet?.kind === 'grid' && (
        <GridSheet
          title={`${section.title} ${rows.length}권`}
          rows={rows}
          selectedId={onLeadCard ? null : (selected?.id ?? null)}
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
 * 펼쳐보기 — 목록의 책을 3열 격자로 한 번에. 고르면 캐러셀이 그 책으로 옮겨가고 시트는 닫힌다.
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
                outline: book.id === selectedId ? '2px solid #6E8A6A' : undefined,
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
                      background: 'var(--adaptiveBlue500, #6E8A6A)',
                      border: '2px solid #FCFAF5',
                    }}
                  />
                )}
              </span>
            </div>
            {/* 긴 제목을 두 줄에서 끊지는 않는다 — TDS `Text`가 인라인 `display`를 자기 값으로 덮어써
                (`-webkit-box` → `inline-block`) line-clamp가 죽는다. 줄이 벌어져도 제목은 다 보인다. */}
            <Text
              typography="st12"
              style={{ display: 'block', marginTop: 6, wordBreak: 'keep-all', textAlign: 'center' }}
            >
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
        display: 'block',
        width: '100%',
        marginBottom: 8,
        padding: '15px 14px',
        // 시트 바닥과 같은 크림색이라 배경만으론 경계가 안 보인다 — 테두리가 있어야 줄이 버튼으로 읽힌다.
        border: '1px solid transparent',
 borderImage: PENCIL_FRAME,
        borderRadius: 10,
        background: '#FFFDF8',
        color: danger ? '#A32D2D' : '#2C2C2A',
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
 * 손잡이 줄 높이 — 원래 아래 「책 추가하기」(TDS `Button size="medium" display="block"`)의 <b>실측</b>
 * 높이였다(목 모드 390px 뷰포트에서 38px). 그 버튼은 2026-08-21에 캐러셀 0번 칸으로 들어가며 사라졌지만
 * 값은 그대로 둔다 — 이제 기준은 <b>「여백에 글쓰기·관리」와 「검색해서 담기」가 같은 높이</b>라는 것이다.
 * 두 줄이 어긋나면 칸을 오갈 때 손잡이가 위아래로 튄다.
 */
const HANDLE_ROW_HEIGHT = 38;

/** 테두리만 있는 작은 손잡이 — 제목 줄 「펼쳐보기」가 쓴다(캐러셀 아래 줄은 전폭 손잡이로 갈렸다). */
export const handleStyle = {
  flex: '0 0 auto',
  padding: '8px 14px',
  border: '1px solid transparent',
  borderImage: PENCIL_FRAME,
  borderRadius: 10,
  background: '#FCFAF5',
  color: '#2C2C2A',
  fontSize: 14,
  cursor: 'pointer',
} as const;

/** 책 검색 — 알라딘 1페이지. 탭하면 어디에 담을지 먼저 묻는다(`AddStatusSheet`). */
export function BookSearch({
  busy,
  error,
  onAdd,
  onFail,
  onBack,
  onOpenBookMargin,
}: {
  busy: boolean;
  error: string | null;
  onAdd: (row: SearchRow, status: BookStatus) => void;
  onFail: (error: Error) => void;
  onBack: () => void;
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
    <Screen title="책 추가" onBack={onBack} backDisabled={busy}>
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
        // 담긴 책은 캔버스보다 한 톤 가라앉힌다 — 카드지(#FCFAF5)보다 어두워야 「지나간 칸」으로 읽힌다.
        background: row.owned ? '#EFE9DC' : 'var(--adaptiveGrey100, #FCFAF5)',
      }}
    >
      {/* 표지만 흐리고 도장은 또렷해야 하므로 흐림을 안쪽 겹에 건다(바깥에 걸면 도장까지 바랜다). */}
      <span style={{ position: 'relative', flex: '0 0 auto', display: 'inline-flex' }}>
        <span style={{ display: 'inline-flex', opacity: row.owned ? 0.62 : 1 }}>
          <BookCover url={row.coverUrl} title={row.title} />
        </span>
        {row.owned && (
          <span style={ownedStampStyle}>
            <OwnedCheck color="#FCFAF5" />
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
            <OwnedCheck color="#4F6B4C" />
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
  border: '1px solid var(--adaptiveGrey200, #E4DDD0)',
  background: 'var(--adaptiveGrey100, #FCFAF5)',
  color: 'var(--adaptiveBlue700, #4F6B4C)',
  fontSize: 13,
  fontWeight: 700,
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
  return (
    <svg width="11" height="11" viewBox="0 0 12 12" fill="none" aria-hidden="true">
      <path d="M2 6.4 L4.6 9 L10 3" stroke={color} strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

/** 표지 귀퉁이의 도장. 테두리가 눕힌 종이색이라 표지에 찍혀 파인 것처럼 보인다. */
const ownedStampStyle = {
  position: 'absolute',
  right: -5,
  bottom: -3,
  width: 20,
  height: 20,
  borderRadius: '50%',
  background: '#6E8A6A', // 웹 --accent
  border: '1.5px solid #EFE9DC',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
} as const;

/** 「서재에 있어요」 칩 — 세이지 연한 채움. 연필 프레임은 쓰지 않는다(좁은 요소에서 그림이 깨진다). */
const ownedChipStyle = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 4,
  marginTop: 6,
  padding: '1px 8px 2px',
  borderRadius: 8,
  border: '1px solid rgba(110, 138, 106, 0.5)',
  background: 'rgba(110, 138, 106, 0.18)',
  color: '#4F6B4C', // 웹 --accent-hover. 연한 채움 위에서 읽히는 유일한 톤
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
