import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { BookStatus, MyBookSummary, SearchRow } from '../api';
import { addBook, changeBookStatus, deleteBook, fetchShelf, searchBooks, setBookVisibility } from '../api';
import { formatDuration } from '../format';
import { BookCover, ErrorMessage, Loading, Screen } from '../ui';

/** 섹션 순서 = 읽는 흐름 순서(읽는 중 → 다 읽음 → 읽고 싶어요). 빈 섹션은 아예 그리지 않는다. */
const SECTIONS: { status: BookStatus; title: string }[] = [
  { status: 'READING', title: '읽는 중' },
  { status: 'FINISHED', title: '다 읽음' },
  { status: 'WANT_TO_READ', title: '읽고 싶어요' },
];

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
  const [openId, setOpenId] = useState<number | null>(null);
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
    fetchShelf()
      .then((shelf) => {
        setBooks(shelf.books);
        setSearchEnabled(shelf.searchEnabled);
      })
      .catch(fail);
  }, [fail]);

  useEffect(load, [load]);

  /** 뮤테이션 공통 — 실행 → 책장 재조회 → 열린 액션 접기. */
  const run = (action: Promise<unknown>) => {
    setBusy(true);
    setError(null);
    action
      .then(() => {
        setOpenId(null);
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
      <ErrorMessage message={error} />
      {books === null ? (
        error === null && <Loading />
      ) : (
        <Shelf books={books} busy={busy} openId={openId} onOpen={setOpenId} onAction={act} />
      )}
    </Screen>
  );
}

/** 책장 목록 — 순수 표시. 상태를 안 들고 있어 정적 렌더로 섹션 분류를 계측할 수 있다. */
export function Shelf({
  books,
  busy,
  openId,
  onOpen,
  onAction,
}: {
  books: MyBookSummary[];
  busy: boolean;
  openId: number | null;
  onOpen: (id: number | null) => void;
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
                open={openId === book.id}
                onToggle={() => onOpen(openId === book.id ? null : book.id)}
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
  onToggle,
  onAction,
}: {
  book: MyBookSummary;
  busy: boolean;
  open: boolean;
  onToggle: () => void;
  onAction: (action: BookAction) => void;
}) {
  const [confirmDelete, setConfirmDelete] = useState(false);

  return (
    <div style={{ marginBottom: 8, borderRadius: 12, background: 'var(--adaptiveGrey100, #f2f4f6)' }}>
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
              <Button variant="weak" size="small" disabled={busy} onClick={() => setConfirmDelete(false)}>
                취소
              </Button>
            </>
          ) : (
            <Button variant="weak" size="small" disabled={busy} onClick={() => setConfirmDelete(true)}>
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
          style={{ ...rowStyle, marginTop: 8, borderRadius: 12, background: 'var(--adaptiveGrey100, #f2f4f6)' }}
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
