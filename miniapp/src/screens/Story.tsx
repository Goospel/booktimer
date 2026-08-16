import { Button, Text } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { MarginBook, MarginEntry, MarginResponse } from '../api';
import { ApiError, STORY_BG_CODES, createStory, deleteStory, fetchBookMargin } from '../api';
import { relativeTime } from '../format';
import { BookCover, CoverInitial, ErrorMessage, Loading, Screen } from '../ui';

/**
 * 여백 — <b>책에 딸린 자리</b>와 거기 쌓이는 글 (2026-08-16 재설계).
 *
 * <p>인스타를 베껴 스트립·전체화면 뷰어·진행바·열람 기록으로 시작했지만, 그 문법이 "24시간 뒤 사라지는
 * 남의 근황"을 전제해 책과 아무 관계가 없었다. 지금은 <b>책 → 그 책의 여백</b> 한 경로뿐이다: 책방 격자에서
 * 책을 누르거나(발광 = 24시간 안에 새 글), 홈 소식의 여백 줄에서 곧장 그 책으로 점프한다.
 *
 * <p>파일·타입 이름은 `Story`로 남아 있다 — 서버 경로가 `/api/stories`라 맞춰 둔 것(#814 결정).
 *
 * <p>노출 권한(차단·IDOR·PRIVATE·비팔로워)은 전부 서버가 판정한다 — 미니앱은 서버가 준
 * `self`·`following`·`entries`를 표시와 액션으로 옮길 뿐이다. 정적 렌더 하니스로는 effect가 안 도므로
 * 판정({@link hasFreshStory})과 표시({@link MarginView})를 상태에서 떼어 따로 계측한다.
 */

/** 배경 코드 → 색. 팔레트 밖 코드(옛 데이터·오타)는 기본으로 떨어뜨려 스타일 주입 자리를 안 만든다. */
function palette(bgCode: string | null) {
  return STORY_BG_CODES.find((bg) => bg.code === bgCode) ?? STORY_BG_CODES[0];
}

/** 발광이 지속되는 창 — 하루. 서버는 시각만 주고 이 판정은 클라가 한다(설계 §D3ⓐ). */
export const FRESH_WINDOW_MS = 86_400_000;

/**
 * 24시간 이내 새 글이 달린 책인가 — 책방 격자 발광의 유일한 근거.
 *
 * <p>경계는 <b>미만(&lt;)</b>이다: 정각 24시간은 이미 창 밖이다. `null`(글이 없거나 비팔로워라 서버가
 * 가린 경우)은 false — 발광은 "새 글이 있다"는 단언이라 모르는 상태를 참으로 올리지 않는다.
 */
export function hasFreshStory(lastStoryAt: string | null, now: number): boolean {
  return lastStoryAt !== null && now - Date.parse(lastStoryAt) < FRESH_WINDOW_MS;
}

/**
 * 작성·여백 화면의 가시성 안내 — <b>쓰는 순간</b>의 고지다(공개 전환 확인 시트는 「공개하는 순간」을 맡는다).
 *
 * <p>비공개 책에도 여백을 쓸 수 있게 되면서(설계 결정 2) 「팔로워에게 보여요」가 비공개 책에서는
 * 거짓말이 됐다. `undefined`(필드를 안 보내는 옛 서버)는 <b>공개로 간주</b>한다 — 보수적인 쪽은
 * 「나만 본다」가 아니다: 실제로는 새는 글을 안 샌다고 말하는 것이 더 위험한 거짓말이다.
 */
export function visibilityNotice(isPublic: boolean | undefined): string {
  return isPublic === false
    ? '비공개 책이에요. 이 글은 나만 봐요. 책을 공개로 바꾸면 팔로워에게 보여요.'
    : '팔로워에게 보여요.';
}

/**
 * 작성 실패 안내 — 서버의 한글 검증 메시지는 `GlobalExceptionHandler`가 HTML `error` 뷰로 렌더해
 * 미니앱까지 오지 못한다(api.ts의 HTML 가드가 상태코드 문구로 대체). 그래서 상태코드로 안내를 나눈다.
 * 서버가 평문 메시지를 주는 날엔 그게 더 정확하므로 그대로 쓴다.
 *
 * <p>어휘 규칙: <b>여백은 자리고, 남기는 것은 글이다</b> — "여백을 남겼다"가 아니라 "글을 남겼다".
 */
export function createStoryMessage(error: Error): string {
  if (!(error instanceof ApiError) || error.message !== `요청에 실패했어요 (${error.status})`) return error.message;
  if (error.status === 429) return '글을 너무 자주 남겼어요. 잠시 후 다시 시도해 주세요.';
  // 책 첨부 selector가 사라져 "다시 골라 달라"고 할 수가 없다 — 목록 자체가 낡은 상황이다.
  if (error.status === 404) return '책을 찾을 수 없어요. 책방을 새로고침해 주세요.';
  return '글을 남기지 못했어요. 문장은 1~500자까지 쓸 수 있어요.';
}

/**
 * 책 하나의 여백 화면 — 마운트 시 한 번 받아 그린다.
 *
 * <p>진입로가 둘이다(책방 격자 탭 · 홈 소식 점프). 어느 쪽으로 와도 응답 하나로 화면이 완성되게
 * 서버가 책 라벨·주인·관계를 동봉한다(`MarginResponse`가 자기완결) — 클라가 아는 것에 기대지 않는다.
 */
export function BookMargin({
  loginId,
  bookId,
  onBack,
  onCompose,
  onError,
}: {
  loginId: string;
  bookId: number;
  onBack: () => void;
  /** 「여백에 글 남기기」 — 작성 화면 전환은 셸이 든다(전체 화면 전이의 주인은 하나여야 한다). */
  onCompose: (book: MarginBook) => void;
  onError: (error: Error) => void;
}) {
  const [margin, setMargin] = useState<MarginResponse | null>(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  const load = useCallback(() => {
    setError(null); // 재시도가 성공했는데 지난 실패 문구가 남지 않게
    fetchBookMargin(loginId, bookId).then(setMargin).catch(fail);
  }, [loginId, bookId, fail]);

  useEffect(load, [load]);

  const remove = (id: number) => {
    setBusy(true);
    setError(null);
    deleteStory(id)
      .then(() => {
        setConfirmDeleteId(null);
        load(); // 서버가 준 목록이 진실 — 지운 카드를 손으로 빼지 않는다
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  if (margin === null) {
    return (
      <Screen title="여백" onBack={onBack}>
        {/* 못 받았을 때 나갈 길만 있으면 실패가 곧 막다른 길이다 — 그 자리에서 다시 받을 길도 함께 준다. */}
        <ErrorMessage message={error} onRetry={load} />
        {error === null && <Loading />}
      </Screen>
    );
  }

  return (
    <MarginView
      loginId={loginId}
      margin={margin}
      now={Date.now()}
      busy={busy}
      confirmDeleteId={confirmDeleteId}
      error={error}
      onCompose={() => onCompose(margin.book)}
      onConfirmDelete={setConfirmDeleteId}
      onDelete={remove}
      onBack={onBack}
    />
  );
}

/**
 * 여백 본문 — 순수 표시. 상태는 전부 밖에서 받는다(정적 렌더 하니스가 네 상태에 닿는 유일한 길).
 */
export function MarginView({
  loginId,
  margin,
  now,
  busy,
  confirmDeleteId,
  error,
  onCompose,
  onConfirmDelete,
  onDelete,
  onBack,
}: {
  loginId: string;
  margin: MarginResponse;
  /** 상대 시각의 기준 — 밖에서 받아야 테스트가 결정론이 된다. */
  now: number;
  busy: boolean;
  /** 지우기 확인이 열린 글 — 되돌릴 수 없는 동작이라 카드 하나씩만 연다. */
  confirmDeleteId: number | null;
  error: string | null;
  onCompose: () => void;
  onConfirmDelete: (id: number | null) => void;
  onDelete: (id: number) => void;
  onBack: () => void;
}) {
  const { book, ownerNickname, self, following, entries } = margin;

  return (
    <Screen title="여백" onBack={onBack}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        {book.coverUrl !== null ? (
          <BookCover url={book.coverUrl} width={48} />
        ) : (
          <CoverInitial title={book.title} width={48} />
        )}
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
            {book.title}
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
            {[book.author, `${ownerNickname} @${loginId}`].filter((s) => s !== null).join(' · ')}
          </Text>
        </div>
      </div>

      {/* 내 비공개 책이면 무엇이 안 새는지 여기서 말한다 — 공개 책엔 새로 알릴 것이 없어 적지 않는다. */}
      {self && book.isPublic === false && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12, wordBreak: 'keep-all' }}>
          {visibilityNotice(book.isPublic)}
        </Text>
      )}

      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 20 }}>
        여백에 남긴 글 {entries.length}
      </Text>

      {/* 작성 진입은 목록 위다 — 글이 쌓일수록 아래로 밀려나면 내 책에서 쓰기가 점점 멀어진다. */}
      {self && (
        <Button display="block" variant="weak" size="small" style={{ marginTop: 10 }} onClick={onCompose}>
          여백에 글 남기기
        </Button>
      )}

      <ErrorMessage message={error} />

      {entries.length === 0 ? (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 16, wordBreak: 'keep-all' }}>
          {self
            ? '아직 남긴 글이 없어요. 읽다가 마음에 걸린 문장을 남겨 보세요.'
            : following
              ? '아직 남긴 글이 없어요.'
              : // 서버가 비팔로워에게 빈 배열을 준다 — 글이 있는지 없는지도 여기서 말하지 않는다.
                '팔로우하면 이 책의 여백에 남긴 글을 볼 수 있어요.'}
        </Text>
      ) : (
        entries.map((e) => (
          <MarginCard
            key={e.id}
            entry={e}
            now={now}
            self={self}
            busy={busy}
            confirming={confirmDeleteId === e.id}
            onConfirmDelete={onConfirmDelete}
            onDelete={onDelete}
          />
        ))
      )}
    </Screen>
  );
}

/**
 * 글 한 장 — 배경은 서버 팔레트 색, 삭제는 본인 카드에만. 확인 단계는 밖에서 받는다(서재 관리 시트와 같다).
 *
 * <p>서재의 인라인 여백 박스가 미리보기로 이 카드를 그대로 재사용한다 — 거기서는 {@code self=false}라
 * 지우기 UI가 통째로 안 그려진다(글은 실제로 내 것이지만 삭제는 전체 화면의 몫).
 */
export function MarginCard({
  entry,
  now,
  self,
  busy,
  confirming,
  onConfirmDelete,
  onDelete,
}: {
  entry: MarginEntry;
  now: number;
  self: boolean;
  busy: boolean;
  confirming: boolean;
  onConfirmDelete: (id: number | null) => void;
  onDelete: (id: number) => void;
}) {
  const bg = palette(entry.bgCode);

  return (
    <div
      style={{
        marginTop: 12,
        padding: 16,
        borderRadius: 12,
        background: bg.background,
        color: bg.color,
      }}
    >
      <p style={{ margin: 0, fontSize: 16, lineHeight: 1.65, whiteSpace: 'pre-wrap', wordBreak: 'keep-all' }}>
        {entry.text}
      </p>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, fontSize: 12, opacity: 0.75 }}>
        <span style={{ flex: 1, minWidth: 0 }}>{relativeTime(entry.createdAt, now)}</span>
        {self &&
          (confirming ? (
            <>
              <span>이 글을 지울까요?</span>
              <button type="button" disabled={busy} onClick={() => onDelete(entry.id)} style={ghost(bg.color)}>
                정말 지우기
              </button>
              <button type="button" disabled={busy} onClick={() => onConfirmDelete(null)} style={ghost(bg.color)}>
                취소
              </button>
            </>
          ) : (
            <button type="button" disabled={busy} onClick={() => onConfirmDelete(entry.id)} style={ghost(bg.color)}>
              지우기
            </button>
          ))}
      </div>
    </div>
  );
}

/**
 * 글 남기기 — 진입점이 <b>이미 그 책</b>이라 책을 고를 것이 없다(옛 첨부 select와 `/api/books` 조회는 삭제).
 * 남는 것은 문장·배경·1~500자 카운터뿐이다.
 */
export function StoryComposer({
  book,
  onDone,
  onCancel,
  onError,
}: {
  book: MarginBook;
  onDone: () => void;
  onCancel: () => void;
  onError: (error: Error) => void;
}) {
  const [text, setText] = useState('');
  const [bgCode, setBgCode] = useState<string>(STORY_BG_CODES[0].code);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const trimmed = text.trim();
  const submit = () => {
    setBusy(true);
    setError(null);
    createStory(trimmed, book.id, bgCode)
      .then(onDone)
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else setError(createStoryMessage(e));
      })
      .finally(() => setBusy(false));
  };

  const bg = palette(bgCode);

  return (
    <Screen title="여백에 글 남기기" onBack={onCancel}>
      {/* 가시성 고지는 placeholder가 아니라 캡션이다 — placeholder는 첫 글자에 사라지는데, 정작
          "이게 누구에게 보이나"가 필요한 순간은 쓰는 도중이다. */}
      <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 12, wordBreak: 'keep-all' }}>
        『{book.title}』의 여백 · {visibilityNotice(book.isPublic)}
      </Text>
      <textarea
        value={text}
        disabled={busy}
        maxLength={500}
        placeholder="읽다가 마음에 걸린 문장이나 생각을 남겨 보세요."
        onChange={(e) => setText(e.target.value)}
        style={{
          width: '100%',
          minHeight: 140,
          padding: 16,
          borderRadius: 12,
          border: 'none',
          fontSize: 16,
          lineHeight: 1.6,
          resize: 'vertical',
          background: bg.background,
          color: bg.color,
        }}
      />
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 6 }}>
        {trimmed.length}/500
      </Text>

      <div style={{ display: 'flex', gap: 6, marginTop: 12 }}>
        {STORY_BG_CODES.map((option) => (
          <button
            key={option.code}
            type="button"
            aria-label={option.code}
            onClick={() => setBgCode(option.code)}
            style={{
              width: 32,
              height: 32,
              borderRadius: 999,
              background: option.background,
              border: option.code === bgCode ? '2px solid var(--adaptiveBlue500, #6E8A6A)' : '1px solid #E4DDD0',
              cursor: 'pointer',
            }}
          />
        ))}
      </div>

      <ErrorMessage message={error} />

      <div style={{ display: 'flex', gap: 8, marginTop: 24 }}>
        <Button style={{ flex: 1 }} loading={busy} disabled={trimmed === ''} onClick={submit}>
          남기기
        </Button>
        <Button variant="weak" disabled={busy} onClick={onCancel}>
          취소
        </Button>
      </div>
    </Screen>
  );
}

const ghost = (color: string) =>
  ({
    flex: '0 0 auto',
    padding: '6px 10px',
    borderRadius: 8,
    border: `1px solid ${color}`,
    background: 'transparent',
    color,
    fontSize: 12,
    cursor: 'pointer',
  }) as const;
