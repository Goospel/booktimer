import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { BookStatus, MyBookSummary, SearchRow } from '../api';
import { addBook, changeBookStatus, deleteBook, fetchShelf, searchBooks, setBookVisibility } from '../api';
import { useBackClose } from '../back';
import { formatDuration } from '../format';
import { BookCover, ErrorMessage, Loading, Screen } from '../ui';

/** 섹션 순서 = 읽는 흐름 순서(읽는 중 → 다 읽음 → 읽고 싶어요). 빈 섹션은 아예 그리지 않는다. */
const SECTIONS: { status: BookStatus; title: string }[] = [
  { status: 'READING', title: '읽는 중' },
  { status: 'FINISHED', title: '다 읽음' },
  { status: 'WANT_TO_READ', title: '읽고 싶어요' },
];

/**
 * 펼쳐진 행 하나 — 열림과 삭제 확인이 **한 덩어리**다.
 *
 * <p>확인을 행 안의 독립 state로 두면 열림과 수명이 어긋난다: 확인을 띄운 채 다른 행이나 액션을
 * 건드려도 그 확인이 살아남아, 그 행을 다시 열었을 때 「정말 삭제」가 곧바로 노출됐다(오삭제가 한 탭 거리).
 * 같은 객체에 묶어 두면 행을 여는 순간 확인이 언제나 `false`로 새로 만들어져 그 자리가 사라진다.
 */
export interface OpenRow {
  id: number;
  confirmDelete: boolean;
}

/**
 * 행을 눌렀을 때의 다음 열림 상태 — 같은 행이면 접고, 아니면 그 행을 편다.
 * 어느 쪽이든 **삭제 확인은 풀린 채로 시작한다**(위 불변식). 하니스가 정적 렌더라 클릭을 못 잡으므로
 * 이 전이만 따로 꺼내 계측한다(`App.tabChangeHandler`와 같은 방식).
 */
export function toggleOpen(open: OpenRow | null, id: number): OpenRow | null {
  return open?.id === id ? null : { id, confirmDelete: false };
}

/** 책 한 권에 걸 수 있는 액션 — 상태 변경·공개 토글·삭제. 실행은 Library가 맡고 Shelf는 알림만 한다. */
export type BookAction =
  | { kind: 'status'; book: MyBookSummary; status: BookStatus }
  | { kind: 'visibility'; book: MyBookSummary }
  | { kind: 'delete'; book: MyBookSummary };

/**
 * 서재 탭 — 내 책장 조회·관리와 검색 추가.
 *
 * <p>화면 전환은 `mode` 하나로 한다(라우터 없음, 설계 §2). 뮤테이션 뒤에는 서버 응답으로 그 책만
 * 갈아끼우지 않고 책장을 다시 받는다 — 상태 변경은 섹션 이동이라 목록 전체가 흔들리기 때문.
 */
export function Library({ onError }: { onError: (error: Error) => void }) {
  const [books, setBooks] = useState<MyBookSummary[] | null>(null);
  const [searchEnabled, setSearchEnabled] = useState(false);
  const [mode, setMode] = useState<'shelf' | 'search'>('shelf');
  const [open, setOpen] = useState<OpenRow | null>(null);
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

  /** 뮤테이션 공통 — 실행 → 책장 재조회 → 열린 액션 접기. */
  const run = (action: Promise<unknown>) => {
    setBusy(true);
    setError(null);
    action
      .then(() => {
        setOpen(null);
        load();
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
        load();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  if (mode === 'search') {
    return <BookSearch busy={busy} error={error} onAdd={add} onFail={fail} onBack={() => setMode('shelf')} />;
  }

  // 로딩 중에도 제목은 남긴다 — 탭을 옮길 때 화면이 통째로 비었다 다시 차는 깜빡임을 줄인다.
  return (
    <Screen title="내 서재">
      {searchEnabled && (
        <Button display="block" size="medium" style={{ marginBottom: 20 }} onClick={() => setMode('search')}>
          책 추가하기
        </Button>
      )}
      {/* 책장을 아예 못 받았을 때만 재시도 — 액션 실패(삭제 거절 등)는 다시 받을 게 아니라 문구만 남긴다. */}
      <ErrorMessage message={error} onRetry={books === null ? load : undefined} />
      {books === null ? (
        error === null && <Loading />
      ) : (
        <Shelf books={books} busy={busy} open={open} onOpen={setOpen} onAction={act} />
      )}
    </Screen>
  );
}

/** 책장 목록 — 순수 표시. 상태를 안 들고 있어 정적 렌더로 섹션 분류를 계측할 수 있다. */
export function Shelf({
  books,
  busy,
  open,
  onOpen,
  onAction,
}: {
  books: MyBookSummary[];
  busy: boolean;
  open: OpenRow | null;
  onOpen: (row: OpenRow | null) => void;
  onAction: (action: BookAction) => void;
}) {
  if (books.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        아직 책이 없어요. 읽고 있는 책을 추가하면 측정할 때 고를 수 있어요.
      </Text>
    );
  }

  return (
    <>
      {SECTIONS.map(({ status, title }) => {
        const rows = books.filter((b) => b.status === status);
        if (rows.length === 0) return null; // 빈 섹션은 제목도 그리지 않는다
        return (
          <section key={status} style={{ marginBottom: 24 }}>
            <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
              {title} {rows.length}
            </Text>
            {rows.map((book) => (
              <BookRow
                key={book.id}
                book={book}
                busy={busy}
                open={open?.id === book.id}
                confirmDelete={open?.id === book.id && open.confirmDelete}
                onToggle={() => onOpen(toggleOpen(open, book.id))}
                onConfirmDelete={(confirm) => onOpen({ id: book.id, confirmDelete: confirm })}
                onAction={onAction}
              />
            ))}
          </section>
        );
      })}
    </>
  );
}

/** 책 한 줄 — 탭하면 액션이 아래로 펼쳐진다(BottomSheet 대신 인라인 — 화면 하나에 상태 하나). */
function BookRow({
  book,
  busy,
  open,
  confirmDelete,
  onToggle,
  onConfirmDelete,
  onAction,
}: {
  book: MyBookSummary;
  busy: boolean;
  open: boolean;
  confirmDelete: boolean;
  onToggle: () => void;
  onConfirmDelete: (confirm: boolean) => void;
  onAction: (action: BookAction) => void;
}) {
  return (
    <div style={{ marginBottom: 8, borderRadius: 12, background: 'var(--adaptiveGrey100, #FCFAF5)' }}>
      <button type="button" onClick={onToggle} style={rowStyle}>
        <BookCover url={book.coverUrl} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div>
            <Text typography="st11">{book.title}</Text>
          </div>
          <div style={{ marginTop: 4 }}>
            <Text typography="st12" color="grey600">
              {book.author ?? '저자 미상'}
              {book.seconds > 0 && ` · ${formatDuration(book.seconds)}`}
              {book.isPublic && ' · 공개'}
            </Text>
          </div>
        </div>
      </button>

      {open && (
        <div style={{ padding: '0 16px 16px', display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {SECTIONS.filter(({ status }) => status !== book.status).map(({ status, title }) => (
            <Button
              key={status}
              variant="weak"
              size="small"
              disabled={busy}
              onClick={() => onAction({ kind: 'status', book, status })}
            >
              {title}(으)로
            </Button>
          ))}
          <Button variant="weak" size="small" disabled={busy} onClick={() => onAction({ kind: 'visibility', book })}>
            {book.isPublic ? '비공개로' : '공개로'}
          </Button>
          {confirmDelete ? (
            <>
              <Button
                color="danger"
                size="small"
                disabled={busy}
                onClick={() => onAction({ kind: 'delete', book })}
              >
                정말 삭제
              </Button>
              <Button variant="weak" size="small" disabled={busy} onClick={() => onConfirmDelete(false)}>
                취소
              </Button>
            </>
          ) : (
            <Button variant="weak" size="small" disabled={busy} onClick={() => onConfirmDelete(true)}>
              삭제
            </Button>
          )}
        </div>
      )}
    </div>
  );
}

/** 책 검색 — 알라딘 1페이지. 탭하면 "읽는 중"으로 추가한다(가장 잦은 의도). */
function BookSearch({
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
    <Screen title="책 추가">
      <TextField
        variant="box"
        label="책 제목"
        placeholder="예: 자바 최적화"
        value={query}
        disabled={busy || searching}
        onChange={(e) => setQuery(e.target.value)}
      />
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
          <BookCover url={row.coverUrl} />
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

      <Button display="block" variant="weak" style={{ marginTop: 24 }} disabled={busy} onClick={onBack}>
        돌아가기
      </Button>
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
