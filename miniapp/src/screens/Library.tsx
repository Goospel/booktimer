import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { BookStatus, MarginResponse, MyBookSummary, SearchRow } from '../api';
import {
  addBook,
  changeBookStatus,
  deleteBook,
  fetchBookMargin,
  fetchShelf,
  searchBooks,
  setBookVisibility,
} from '../api';
import { useBackClose } from '../back';
import { Coachmark } from '../coachmark';
import { formatDuration } from '../format';
import { BookCover, ErrorMessage, Loading, PENCIL_FRAME, Screen, Sheet } from '../ui';
import { BookCarousel } from './Home';
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

/**
 * 캐러셀 아래 메타 — 표지와 제목만으론 안 보이는 것들. 읽은 시간이 0이면 적지 않는다(「0분」은 정보가 아니다).
 *
 * <p>**저자는 첫 줄, 읽은 시간·공개는 다음 줄**로 나눈다. 알라딘 저자 문자열은 「레프 니콜라예비치 톨스토이
 * (지은이), 연진희 (옮긴이)」처럼 길어서 한 줄로 이으면 폰 폭에서 되접히고, 그때 읽은 시간과 공개 여부가
 * 저자 이름 사이에 끼어 든다(실기기 실측). 줄을 나누면 「누가 썼나」와 「얼마나 읽었나」가 섞이지 않는다.
 * 줄바꿈을 실제로 살리는 건 렌더 쪽 `white-space: pre-line`이다 — 한 쌍으로 움직인다.
 */
export function metaLine(book: MyBookSummary): string {
  const stats = [];
  if (book.seconds > 0) stats.push(formatDuration(book.seconds));
  stats.push(book.isPublic ? '공개' : '비공개');
  return `${book.author ?? '저자 미상'}\n${stats.join(' · ')}`;
}

/**
 * 공개 전환 확인이 필요한가 — <b>비공개→공개 방향 + 여백에 남긴 글이 있을 때만</b>.
 *
 * <p>비공개 책에 쌓아 둔 글은 나만 보는 메모인데, 책을 공개로 바꾸는 순간 팔로워에게 실린다
 * (글에 자체 공개 필드가 없어 가시성은 언제나 책에서 파생된다). 되돌리는 방향은 `isPublic`이 걸러 낸다.
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
          if (!stale) setSnap({ bookId, margin });
        })
        .catch((e: Error) => {
          if (stale) return;
          if (e.name === 'UnauthorizedError') onError(e);
          else setSnap({ bookId, margin: null });
        });
    }, MARGIN_FETCH_DEBOUNCE_MS);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [loginId, bookId, onError]);

  return <MarginBoxView view={marginBoxView(snap, bookId)} now={Date.now()} onOpenAll={onOpenAll} />;
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
        <Text typography="st11" style={{ flex: 1 }}>
          여백{typeof view !== 'string' && <b style={{ color: '#4E6B4A' }}> {entries.length}</b>}
        </Text>
        <button
          type="button"
          onClick={onOpenAll}
          style={{
            padding: 0,
            border: 'none',
            background: 'transparent',
            color: 'var(--adaptiveGrey600, #6F6A5E)',
            fontSize: 13,
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
            <MarginCard
              key={e.id}
              entry={e}
              now={now}
              self={false}
              busy={false}
              confirming={false}
              onConfirmDelete={() => {}}
              onDelete={() => {}}
            />
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
}) {
  const [books, setBooks] = useState<MyBookSummary[] | null>(null);
  const [searchEnabled, setSearchEnabled] = useState(false);
  const [mode, setMode] = useState<'shelf' | 'search'>('shelf');
  const [tab, setTab] = useState<BookStatus>('READING');
  /** 캐러셀에서 가운데 온 책 — 그 탭에 없어지면 `resolveSelected`가 첫 책으로 되돌린다. */
  const [selectedId, setSelectedId] = useState<number | null>(null);
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
    return <BookSearch busy={busy} error={error} onAdd={add} onFail={fail} onBack={() => setMode('shelf')} />;
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
            onTab={setTab}
            onSelect={setSelectedId}
            onSheet={setSheet}
            onAction={act}
            onOpenMargin={onOpenMargin}
            onComposeMargin={onComposeMargin}
            onError={onError}
          />
          <AddBookButton enabled={searchEnabled} onPress={() => setMode('search')} />
        </>
      )}
    </Screen>
  );
}

/**
 * 「책 추가하기」 + 첫 방문 코치마크 — 게이트와 안내가 <b>한 몸</b>이다.
 *
 * <p>버튼이 서버 플래그(`searchEnabled`)에 달려 있어, 안내를 따로 두면 플래그가 꺼진 날
 * 아무것도 없는 자리를 가리키는 안내만 남는다. 둘을 같은 컴포넌트가 들면 그 사고가 원리상 불가능하다.
 */
export function AddBookButton({ enabled, onPress }: { enabled: boolean; onPress: () => void }) {
  if (!enabled) return null;

  return (
    <Coachmark
      name="add-book"
      after="library" // 바로 앞 걸음(서재 설명)을 본 뒤에 — 흐름이 이 화면으로 데려온 직후가 제 차례다
      title="읽을 책은 여기서 찾아 담아요"
      detail="제목으로 검색하면 서재에 들어와요"
    >
      <Button display="block" size="medium" style={{ marginTop: 24 }} onClick={onPress}>
        책 추가하기
      </Button>
    </Coachmark>
  );
}

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
  onTab,
  onSelect,
  onSheet,
  onAction,
  onOpenMargin,
  onComposeMargin,
  onError,
}: {
  books: MyBookSummary[];
  tab: BookStatus;
  selectedId: number | null;
  sheet: LibrarySheet;
  busy: boolean;
  /** 없으면 여백 손잡이·박스를 그리지 않는다 — 눌러도 서버가 대상을 못 찾는다(설계 결정 A). */
  myLoginId: string | null;
  onTab: (status: BookStatus) => void;
  onSelect: (bookId: number | null) => void;
  onSheet: (sheet: LibrarySheet) => void;
  onAction: (action: BookAction) => void;
  onOpenMargin: (loginId: string, bookId: number) => void;
  onComposeMargin: (book: MyBookSummary) => void;
  onError: (error: Error) => void;
}) {
  if (books.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        아직 책이 없어요. 읽고 있는 책을 추가하면 측정할 때 고를 수 있어요.
      </Text>
    );
  }

  const section = SECTIONS.find((s) => s.status === tab) ?? SECTIONS[0];
  const rows = books.filter((b) => b.status === tab);
  const selected = resolveSelected(rows, selectedId);

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
                fontSize: 13,
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

      {selected === null ? (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 28, textAlign: 'center' }}>
          {section.empty}
        </Text>
      ) : (
        <div style={{ marginTop: 20 }}>
          {/* 탭이 바뀌면 목록이 통째로 갈리므로 다시 마운트한다 — 안 그러면 트랙이 옛 탭의 스크롤 자리에 머문다. */}
          <BookCarousel
            key={tab}
            books={rows}
            selectedId={selected.id}
            onSelect={onSelect}
            noBookCard={false}
            metaOf={metaLine}
          />
          {/* 전폭 손잡이 줄 — 합폭은 「책 추가하기」와 같고, 비율은 2:1(쓰기가 주, 관리가 보조). */}
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
                  fontSize: 15,
                  fontWeight: 600,
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
                fontSize: 15,
                fontWeight: 600,
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
        </div>
      )}

      {sheet?.kind === 'grid' && (
        <GridSheet
          title={`${section.title} ${rows.length}권`}
          rows={rows}
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
          여백에 남긴 글 {book.storyCount}개가 팔로워에게 보여요.
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
        fontSize: 14,
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      {label}
    </button>
  );
}

/**
 * 손잡이 줄 높이 — 아래 「책 추가하기」(TDS `Button size="medium" display="block"`)의 <b>실측</b> 높이다
 * (목 모드 390px 뷰포트에서 38px). TDS 내부 값이라 코드로 유도할 수 없어 재서 박았다 — 두 줄이 같은
 * 높이라야 캐러셀 아래가 한 벌의 손잡이로 읽힌다.
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
  fontSize: 13,
  cursor: 'pointer',
} as const;

/** 책 검색 — 알라딘 1페이지. 탭하면 "읽는 중"으로 추가한다(가장 잦은 의도). */
export function BookSearch({
  busy,
  error,
  onAdd,
  onFail,
  onBack,
}: {
  busy: boolean;
  error: string | null;
  onAdd: (row: SearchRow, status: BookStatus) => void;
  onFail: (error: Error) => void;
  onBack: () => void;
}) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchRow[] | null>(null);
  const [searching, setSearching] = useState(false);

  const submit = () => {
    setSearching(true);
    searchBooks(query.trim())
      .then((page) => setResults(page.results))
      .catch(onFail)
      .finally(() => setSearching(false));
  };

  return (
    <Screen title="책 추가" onBack={onBack} backDisabled={busy}>
      {/* 입력 하나짜리 form이라 엔터(키보드 「완료」)가 곧 제출이다 — 버튼은 밖에 둔다(Social과 같은 배선). */}
      <form
        onSubmit={(e) => {
          e.preventDefault(); // 막지 않으면 페이지가 새로고침돼 미니앱이 처음으로 돌아간다
          if (!busy && !searching && query.trim() !== '') submit();
        }}
      >
        <TextField
          variant="box"
          label="책 제목"
          placeholder="예: 자바 최적화"
          value={query}
          disabled={busy || searching}
          onChange={(e) => setQuery(e.target.value)}
        />
      </form>
      <Button
        display="block"
        style={{ marginTop: 16 }}
        loading={searching}
        disabled={query.trim() === '' || busy}
        onClick={submit}
      >
        검색
      </Button>

      <ErrorMessage message={error} />

      {results !== null && results.length === 0 && (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 20 }}>
          검색 결과가 없어요. 제목을 조금 다르게 적어 보세요.
        </Text>
      )}

      {results?.map((row, index) => (
        <button
          key={row.isbn13 ?? `${row.title}-${index}`}
          type="button"
          disabled={busy || row.owned}
          onClick={() => onAdd(row, 'READING')}
          style={{ ...rowStyle, marginTop: 8, borderRadius: 12, background: 'var(--adaptiveGrey100, #FCFAF5)' }}
        >
          <BookCover url={row.coverUrl} title={row.title} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div>
              <Text typography="st11">{row.title}</Text>
            </div>
            <div style={{ marginTop: 4 }}>
              <Text typography="st12" color="grey600">
                {row.author ?? '저자 미상'}
                {row.owned && ' · 이미 서재에 있어요'}
              </Text>
            </div>
          </div>
        </button>
      ))}

    </Screen>
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
