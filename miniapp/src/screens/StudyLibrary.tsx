import type { CSSProperties } from 'react';
import { useCallback, useEffect, useState } from 'react';

import type { SearchRow, StudyBookRow, StudyShelfResponse } from '../api';
import { addStudyBook, deleteStudyBook, fetchStudyBooks, searchBooks, setStudyReadCount } from '../api';
import { useBackClose } from '../back';
import { CACHE_STUDY_SHELF, cacheGet, cachePut } from '../cache';
import { formatDuration } from '../format';
import { openExternal } from '../toss';
import { ErrorMessage, Loading, PENCIL_FRAME, Screen, SearchField, Sheet, Text } from '../ui';
import { BookCarousel, type LeadCard } from './Home';
import { GridSheet, HANDLE_ROW_HEIGHT, SearchResultRow, handleStyle, resolveSelected } from './Library';

/**
 * 공부 서재 — 공부 모드의 서재 탭. 독서 서재와 <b>다른 화면·다른 문</b>이다(설계 2026-09-01).
 *
 * <p>왜 별도 파일인가: 독서 서재(`Library.tsx`)에 `mode` 분기를 넣으면 1,500줄 파일 전체에 조건이 스미고,
 * 「독서 렌더가 한 바이트도 안 바뀐다」를 확인할 길이 사라진다. 파일이 갈리면 `git diff Library.tsx`의
 * 공백이 곧 그 증명이다. 부품은 <b>import만</b> 한다 — 저쪽 파일은 한 줄도 고치지 않는다.
 *
 * <p>독서와 뜻이 다른 곳은 셋이다. ① 분류 축이 상태(읽는중/완독)가 아니라 <b>회독 수</b>라 탭이 없다.
 * ② 담기가 짧다 — 회독은 언제나 0독에서 시작하므로 「어디에 담을지」를 묻지 않는다.
 * ③ 여백(글쓰기·박스)이 없다 — 공부 모드엔 책방 탭 자체가 없어 글이 갈 곳이 없다.
 */

/** 회독 수 칩 문구 — <b>0독도 그린다</b>. 「아직 한 번도 안 돌았다」는 빈칸이 아니라 정보다. */
export function readCountLabel(readCount: number): string {
  return `${readCount}독`;
}

/**
 * 이 검색 행이 <b>공부 서재</b>에 이미 있는가.
 *
 * <p>서버가 준 `row.owned`는 <b>독서 책장</b> 기준이라 여기서 쓰면 안 된다 — 독서 서재에 있는 책을
 * 공부 서재에 못 담게 막아 버린다(같은 책을 공부용으로도 두는 것이 이 기능의 전제다).
 *
 * <p>isbn이 없는 책은 <b>담김이 아니다</b>: 집합에 넣을 열쇠가 없어 「없다」가 유일하게 참인 답이다
 * (N-055 null-state 규율 — 서버의 멱등 가드도 같은 이유로 isbn null엔 안 걸린다).
 */
export function studyOwned(myIsbns: Set<string>, row: SearchRow): boolean {
  return row.isbn13 !== null && myIsbns.has(row.isbn13);
}

/** 「회독 -1」 행을 그릴 것인가 — 0독이면 되돌릴 것이 없다(눌러도 안 되는 죽은 행을 안 남긴다). */
export function canDecrement(readCount: number): boolean {
  return readCount > 0;
}

/**
 * 열린 시트 — 「펼쳐보기」(격자)와 「관리」(액션) 둘뿐이고 동시에 열리지 않는다.
 *
 * <p>삭제 확인을 시트 안에 묶는 이유는 독서 서재와 같다: 밖에 두면 확인을 띄운 채 시트를 닫아도
 * 그 확인이 살아남아, 다시 열었을 때 「정말 삭제」가 곧바로 노출된다(오삭제가 한 탭 거리).
 */
export type StudySheet = { kind: 'grid' } | { kind: 'actions'; confirmDelete: boolean } | null;

/** 캐러셀 0번 칸 — 독서 서재와 같은 규약(0번은 언제나 검색 화면으로 가는 문)이다. */
export const ADD_STUDY_BOOK_CARD: LeadCard = {
  label: '책 추가',
  title: '책 추가',
  subtitle: () => '제목으로 검색해 공부 서재에 담아요',
};

/**
 * 회독 칩 — 파랑 토큰을 쓴다. 공부 모드에서 `body.study-mode`가 이 토큰들을 파랑으로 갈아끼우므로
 * 화면은 색을 한 줄도 몰라도 된다(독서등·모드 스왑과 같은 수법).
 */
const readCountChipStyle: CSSProperties = {
  display: 'inline-block',
  padding: '2px 9px',
  borderRadius: 20,
  fontSize: 12,
  lineHeight: 1.6,
  background: 'var(--adaptiveBlue50, #E7EEE2)',
  color: 'var(--adaptiveBlue700, #4F6B4C)',
};

/**
 * 누적 공부 시간 칩 — 회독 칩과 <b>같은 박스, 다른 색</b>이다(회독은 파랑 토큰, 시간은 중립).
 *
 * <p>색값은 독서 서재 `bookChipStyle('neutral')`에서 <b>복사</b>했다: 그 함수는 export돼 있지 않고
 * `Library.tsx`는 이 작업에서 수정 금지라(독서 경로 diff 0), 값 복사가 가장 싼 길이다.
 */
const studyTimeChipStyle: CSSProperties = {
  display: 'inline-block',
  padding: '2px 9px',
  borderRadius: 20,
  fontSize: 12,
  lineHeight: 1.6,
  background: 'var(--adaptiveGrey200, #E4DDD0)',
  color: 'var(--adaptiveGrey700, #57534A)',
};

/**
 * 카드가 이 책에 대해 말하는 것 — 회독 수는 <b>언제나</b>, 잰 시간은 <b>있을 때만</b>.
 *
 * <p>0을 가르는 것이 두 칩의 유일한 차이다: 「0독」은 「아직 안 돌았다」는 상태지만 「0초 공부」는
 * 부재라 할 말이 아니다(독서 서재 `bookStats`와 같은 규약).
 *
 * <p>순수 함수로 꺼낸 이유는 늘 같다 — 하니스가 정적 렌더라 칩 규칙을 여기서만 계측할 수 있다(T-149).
 */
export function studyBookChips(book: StudyBookRow): { label: string; value: string; style: CSSProperties }[] {
  const chips = [{ label: readCountLabel(book.readCount), value: String(book.readCount), style: readCountChipStyle }];
  const seconds = book.totalSeconds ?? 0;
  if (seconds > 0) {
    const studied = formatDuration(seconds);
    // 숫자만 세리프로 그린다 — 「3시간 20분」이 값이고 「공부」는 그게 무엇인지 말하는 꼬리다.
    chips.push({ label: `${studied} 공부`, value: studied, style: studyTimeChipStyle });
  }
  return chips;
}

/**
 * 채움이 아닌 손잡이 — 「관리」(카드지)와 「검색해서 담기」(틴트)가 쓴다.
 *
 * <p>주 손잡이(「회독 +1」)는 여기 없다: 채움 레시피를 <b>호출부에 리터럴로</b> 편다. 헬퍼 삼항에
 * 숨기면 「채움 주 버튼은 화면당 하나」 불변식의 계측기(설계 D5 · `typography.test`의 소스 스캔)가
 * <b>그 채움을 못 본다</b> — 리뷰어 실측으로 확인된 사각이다. 계측기에 걸리는 자리에 두는 것이 요점이라
 * 이 스타일만 인라인인 것은 중복이 아니라 계약이다.
 */
function handleRowStyle(tone: 'tint' | 'card'): CSSProperties {
  return {
    flex: 1,
    height: HANDLE_ROW_HEIGHT,
    border: '1px solid transparent',
    borderImage: PENCIL_FRAME,
    borderRadius: 14,
    background: tone === 'tint' ? 'var(--adaptiveBlue50, #E7EEE2)' : '#FCFAF5',
    color: tone === 'tint' ? 'var(--adaptiveBlue700, #4F6B4C)' : '#2C2C2A',
    fontSize: 15,
    fontWeight: 700,
    cursor: 'pointer',
  };
}

/**
 * 공부 서재 탭 — 조회·회독 관리와 검색 추가. 화면 전환은 `mode` 하나로 한다(라우터 없음).
 *
 * <p>뮤테이션 뒤에 서버 응답으로 그 책만 갈아끼우지 않고 목록을 다시 받는다 — 삭제·추가는 목록
 * 자체가 흔들리고, 회독만 바뀐 경우에도 한 번의 왕복이 화면과 서버를 확실히 맞춘다.
 */
export function StudyLibrary({
  onError,
  onShelfChanged,
}: {
  onError: (error: Error) => void;
  /**
   * 서재에서 책이 늘거나 줄면 홈이 보는 목록(`StudyState.books`)도 낡는다 — 안 부르면 홈 캐러셀이
   * 옛 목록 그대로다(독서 서재의 같은 이름 프롭과 같은 역할).
   */
  onShelfChanged: () => void;
}) {
  /*
   * 첫 렌더의 출발점을 세션 캐시에서 집는다(독서 서재와 같은 SWR) — 탭을 오갈 때 통째로 로딩이 되지 않게.
   * 지연 초기화(`useState(() => …)`)라 캐시 조회가 <b>마운트 1회</b>다 — 매 렌더 읽어도 결과는 버려진다.
   */
  const [books, setBooks] = useState<StudyBookRow[] | null>(
    () => cacheGet<StudyShelfResponse>(CACHE_STUDY_SHELF)?.books ?? null,
  );
  const [searchEnabled, setSearchEnabled] = useState(
    () => cacheGet<StudyShelfResponse>(CACHE_STUDY_SHELF)?.searchEnabled ?? false,
  );
  const [mode, setMode] = useState<'shelf' | 'search'>('shelf');
  /** 책 id · `null`(「책 추가」 칸을 고름) · `undefined`(아직 안 고름) — 뒤의 둘은 다른 값이다. */
  const [selectedId, setSelectedId] = useState<number | null | undefined>(undefined);
  const [sheet, setSheet] = useState<StudySheet>(null);
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

  /** 재조회 — <b>프라미스를 돌려준다</b>. 뮤테이션이 그걸 기다려야 busy가 목록보다 먼저 안 풀린다(아래 `run`). */
  const load = useCallback(() => {
    setError(null); // 다시 받는 김에 지난 실패 문구도 지운다
    return fetchStudyBooks()
      .then((shelf) => {
        cachePut(CACHE_STUDY_SHELF, shelf);
        setBooks(shelf.books);
        setSearchEnabled(shelf.searchEnabled);
      })
      .catch(fail);
  }, [fail]);

  // 반환값을 삼킨다 — effect의 반환은 cleanup 자리라, 프라미스를 그대로 흘리면 React가 그걸 정리 함수로 읽는다.
  useEffect(() => {
    void load();
  }, [load]);

  // 검색은 서재를 덮는 별도 화면이다 — 나가는 길은 네이티브 뒤로가기 하나이므로 여기가 유일한 출구다.
  useBackClose(mode === 'search', () => setMode('shelf'));
  // 열린 시트는 뒤로가기가 먼저 먹는다 — 시트가 열린 채로 미니앱이 꺼지지 않게.
  useBackClose(sheet !== null, () => setSheet(null));

  const run = (action: Promise<unknown>) => {
    setBusy(true);
    setError(null);
    action
      .then(() => {
        setSheet(null);
        // 재조회까지 기다린 뒤에 busy를 푼다 — 안 기다리면 화면이 <b>옛 회독 수</b>를 들고 있는 채로
        // 손잡이가 살아나, 연타가 같은 절대값을 다시 보내고 그 한 번은 조용히 사라진다(멱등이라 티도 안 난다).
        return load();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  const add = (row: SearchRow) => {
    setBusy(true);
    setError(null);
    addStudyBook(row)
      .then((added) => {
        setMode('shelf');
        setSelectedId(added.id); // 방금 담은 책이 가운데 — 아니면 추가가 아무 일도 안 한 것처럼 보인다
        onShelfChanged(); // 홈 캐러셀도 이 책을 알아야 한다(방금 담은 책으로 바로 재는 것이 자연스러운 다음 동작이다)
        load();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  if (mode === 'search') {
    return (
      <StudyBookSearch
        myIsbns={new Set((books ?? []).map((b) => b.isbn13).filter((i): i is string => i !== null))}
        busy={busy}
        error={error}
        onAdd={add}
        onFail={fail}
      />
    );
  }

  return (
    <Screen
      title="공부 서재"
      right={
        // 캐러셀은 한 번에 한 권이라 권수가 늘면 훑기 답답하다 — 격자로 한 번에 보는 길을 제목 줄에 둔다.
        (books?.length ?? 0) > 1 ? (
          <button type="button" onClick={() => setSheet({ kind: 'grid' })} style={handleStyle}>
            펼쳐보기
          </button>
        ) : undefined
      }
    >
      {/* 목록을 아예 못 받았을 때만 재시도 — 액션 실패는 다시 받을 게 아니라 문구만 남긴다. */}
      <ErrorMessage message={error} onRetry={books === null ? load : undefined} />
      {books === null ? (
        error === null && <Loading />
      ) : (
        <StudyShelf
          books={books}
          selectedId={selectedId}
          sheet={sheet}
          busy={busy}
          searchEnabled={searchEnabled}
          onSelect={setSelectedId}
          onSheet={setSheet}
          onReadCount={(book, readCount) => run(setStudyReadCount(book.id, readCount))}
          // 지운 책은 홈 캐러셀에서도 사라져야 한다 — 남아 있으면 그 id로 시작해 404가 난다.
          onDelete={(book) => run(deleteStudyBook(book.id).then(() => onShelfChanged()))}
          onAddBook={() => setMode('search')}
        />
      )}
    </Screen>
  );
}

/**
 * 책장 — 표지 캐러셀 + 「N독」 칩 + 손잡이 두 개. 순수 표시라 정적 렌더로 칩·시트를 계측할 수 있다.
 *
 * <p>상태 탭이 <b>없다</b>: 공부 책의 분류 축은 회독 수 하나뿐이고, 회독수별 그룹 탭은 데이터에 따라
 * 탭 수가 변해 UI가 불안정하다(설계 §2-② 기각). 목록은 등록 최신순 한 줄이다.
 */
export function StudyShelf({
  books,
  selectedId,
  sheet,
  busy,
  searchEnabled,
  onSelect,
  onSheet,
  onReadCount,
  onDelete,
  onAddBook,
}: {
  books: StudyBookRow[];
  selectedId: number | null | undefined;
  sheet: StudySheet;
  busy: boolean;
  /** 서버가 검색을 껐으면 「책 추가」 칸을 세우지 않는다 — 눌러도 못 가는 칸을 남기지 않는다. */
  searchEnabled: boolean;
  onSelect: (bookId: number | null) => void;
  onSheet: (sheet: StudySheet) => void;
  /** 회독 수를 <b>절대값으로</b> 올린다/내린다 — 델타가 아니라 결과를 보낸다(멱등). */
  onReadCount: (book: StudyBookRow, readCount: number) => void;
  onDelete: (book: StudyBookRow) => void;
  onAddBook: () => void;
}) {
  const leadCard = searchEnabled ? ADD_STUDY_BOOK_CARD : null;

  // 검색이 꺼진 날의 마지막 폴백 — 캐러셀에 세울 것이 하나도 없으면 문장이 화면을 지킨다.
  if (books.length === 0 && leadCard === null) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        아직 공부 책이 없어요.
      </Text>
    );
  }

  const selected = resolveSelected(books, selectedId ?? null);
  /** 「책 추가」 칸이 가운데인가 — 칸을 골랐거나(`null`), 세울 책이 아예 없을 때다. */
  const onLeadCard = leadCard !== null && (selectedId === null || selected === null);

  return (
    <div style={{ marginTop: 20 }}>
      <BookCarousel
        books={books}
        selectedId={onLeadCard ? null : (selected?.id ?? null)}
        onSelect={onSelect}
        leadCard={leadCard}
        metaOf={(b) => b.author ?? '저자 미상'}
        // 숫자만 세리프로 그린다(「3」이 값이고 「독」은 그게 무엇인지 말하는 꼬리다 — 독서 서재의 「2시간 읽음」과 같은 규약).
        // 칩 규칙은 순수 함수가 든다 — 시간 칩의 0 경계를 정적 하니스로 재려면 렌더 밖에 있어야 한다.
        chipsOf={studyBookChips}
      />

      {onLeadCard ? (
        // 칸이 가운데면 할 수 있는 일은 하나뿐이다 — 손잡이도 하나로 갈린다.
        <div style={{ display: 'flex', marginTop: 16 }}>
          <button type="button" disabled={busy} onClick={onAddBook} style={handleRowStyle('tint')}>
            검색해서 담기
          </button>
        </div>
      ) : (
        selected !== null && (
          <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
            {/*
              * 한 회독을 끝냈을 때 누르는 자리 — 이 화면의 유일한 핵심 동작이라 <b>채움</b>이다.
              * 독서 서재의 구조적 대응물은 「검색해서 담기」(0번 칸 전용 틴트)가 아니라 같은 2:1 줄의
              * 「여백에 글쓰기」이고, 그쪽 레시피(채움 + 연필 프레임 + 크림 잉크)를 그대로 쓴다.
              * TDS `Button`이 아니라 맨 `<button>`인 것도 그쪽과 같은 이유다 — `--button-min-height: 56px`가
              * 박혀 있어 38px 손잡이 줄에서 혼자 솟는다.
              */}
            <button
              type="button"
              disabled={busy}
              onClick={() => onReadCount(selected, selected.readCount + 1)}
              style={{
                flex: 2,
                height: HANDLE_ROW_HEIGHT,
                border: '1px solid transparent',
                borderImage: PENCIL_FRAME,
                borderRadius: 14,
                background: 'var(--adaptiveBlue700, #4F6B4C)',
                color: '#F7F2E8',
                fontSize: 15,
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              회독 +1
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => onSheet({ kind: 'actions', confirmDelete: false })}
              style={handleRowStyle('card')}
            >
              관리
            </button>
          </div>
        )
      )}

      {sheet?.kind === 'grid' && (
        <GridSheet
          title={`공부 책 ${books.length}권`}
          rows={books}
          selectedId={onLeadCard ? null : (selected?.id ?? null)}
          onPick={(id) => {
            onSelect(id);
            onSheet(null);
          }}
          onClose={() => onSheet(null)}
        />
      )}
      {sheet?.kind === 'actions' && selected !== null && (
        <StudyActionSheet
          book={selected}
          busy={busy}
          confirmDelete={sheet.confirmDelete}
          onConfirmDelete={(confirm) => onSheet({ kind: 'actions', confirmDelete: confirm })}
          onReadCount={onReadCount}
          onDelete={onDelete}
          onClose={() => onSheet(null)}
        />
      )}
    </div>
  );
}

/**
 * 관리 시트 — 되돌리기(「회독 -1」) · 구매 · 삭제. 삭제만 확인 한 단계를 거친다.
 *
 * <p>「회독 +1」은 여기 없다 — 주 손잡이가 이미 그 자리다. 시트에 또 두면 같은 동작이 두 자리에서
 * 갈라져, 어느 쪽이 진짜인지 매번 고르게 만든다.
 */
export function StudyActionSheet({
  book,
  busy,
  confirmDelete,
  onConfirmDelete,
  onReadCount,
  onDelete,
  onClose,
}: {
  book: StudyBookRow;
  busy: boolean;
  confirmDelete: boolean;
  onConfirmDelete: (confirm: boolean) => void;
  onReadCount: (book: StudyBookRow, readCount: number) => void;
  onDelete: (book: StudyBookRow) => void;
  onClose: () => void;
}) {
  if (confirmDelete) {
    return (
      <Sheet title={book.title} onClose={onClose}>
        <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 12, wordBreak: 'keep-all' }}>
          공부 서재에서 빼면 이 책의 회독 수도 함께 사라져요.
        </Text>
        <StudySheetRow label="정말 삭제" busy={busy} danger onClick={() => onDelete(book)} />
        <StudySheetRow label="취소" busy={busy} onClick={() => onConfirmDelete(false)} />
      </Sheet>
    );
  }

  return (
    <Sheet title={book.title} onClose={onClose}>
      {/* 잘못 누른 「회독 +1」을 되돌리는 자리 — 0독이면 되돌릴 것이 없어 행 자체를 안 그린다. */}
      {canDecrement(book.readCount) && (
        <StudySheetRow
          label={`회독 -1 (지금 ${readCountLabel(book.readCount)})`}
          busy={busy}
          onClick={() => onReadCount(book, book.readCount - 1)}
        />
      )}
      {/* 구매는 삭제 위다 — 위험한 것이 목록 끝에 남는 배치를 지킨다(독서 관리 시트와 같다). */}
      <StudyBuyRow link={book.purchaseLink} busy={busy} />
      <StudySheetRow label="서재에서 삭제" busy={busy} danger onClick={() => onConfirmDelete(true)} />
    </Sheet>
  );
}

/**
 * 시트 안 액션 한 줄 — 손가락 몫을 확보한 넓은 버튼. 독서 서재의 같은 줄과 <b>같은 모양</b>이지만
 * 그쪽 `SheetRow`는 export되지 않았고, 꺼내려면 `Library.tsx`를 고쳐야 한다(독서 렌더 불변이 깨진다).
 * 그래서 이 파일이 자기 것을 든다 — 스타일 값이 겹치는 비용이 파일을 건드리는 비용보다 싸다.
 */
function StudySheetRow({
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
 * 제휴 구매 줄 — 링크가 없으면 아무것도 그리지 않는다.
 *
 * <p>고지문구를 같은 컴포넌트에 묶는 이유는 독서 쪽과 같다: 살 곳 없이 「수수료를 받는다」만 남는 것도,
 * 살 곳만 있고 고지가 없는 것도 둘 다 사고다 — 한 몸이면 어느 쪽도 일어날 수 없다.
 * 수험서는 구매 전환이 실제로 기대되는 자리라 공부 서재에도 이 줄을 둔다.
 */
function StudyBuyRow({ link, busy }: { link: string | null; busy: boolean }) {
  if (link === null || link === '') {
    return null;
  }
  return (
    <>
      <StudySheetRow label="알라딘에서 구매" busy={busy} onClick={() => openExternal(link)} />
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
 * 책 검색 — 알라딘 1페이지(`/api/books/search`는 도메인 중립이라 서버 무변경 재사용).
 *
 * <p>독서 검색보다 <b>한 단계 짧다</b>: 담을 곳을 묻는 시트가 없다(회독은 언제나 0독 시작). 행을 탭하면
 * 바로 담기고 목록으로 돌아간다. 추천 카드도 세우지 않는다 — 독서 취향 기반이라 공부엔 소비처가 없다.
 *
 * <p>행의 「서재에 있어요」 판정은 서버가 준 `owned`(독서 책장 기준)를 <b>버리고</b> 공부 서재의 isbn
 * 집합으로 다시 센다({@link studyOwned}) — 안 그러면 독서 서재에 있는 책을 공부용으로 못 담는다.
 */
export function StudyBookSearch({
  myIsbns,
  busy,
  error,
  onAdd,
  onFail,
}: {
  myIsbns: Set<string>;
  busy: boolean;
  error: string | null;
  onAdd: (row: SearchRow) => void;
  onFail: (error: Error) => void;
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
    <Screen title="공부 책 추가">
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

      {results !== null && results.length === 0 && (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 20 }}>
          검색 결과가 없어요. 제목을 조금 다르게 적어 보세요.
        </Text>
      )}

      {results?.map((row, index) => (
        <SearchResultRow
          key={row.isbn13 ?? `${row.title}-${index}`}
          // `owned`만 갈아끼운다 — 행 모양(도장·칩·잠금)은 독서 검색과 글자 하나까지 같다.
          row={{ ...row, owned: studyOwned(myIsbns, row) }}
          busy={busy}
          onPick={() => onAdd(row)}
        />
      ))}
    </Screen>
  );
}
