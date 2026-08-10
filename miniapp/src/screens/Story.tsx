import { Button, Text } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { AuthorStories, MyBookSummary, StoryCard, StoryFeedResponse, StoryViewerEntry } from '../api';
import {
  ApiError,
  STORY_BG_CODES,
  createStory,
  deleteStory,
  fetchShelf,
  fetchStoryViewers,
  markStoryViewed,
} from '../api';
import { ErrorMessage, Screen } from '../ui';

/**
 * 독서 스토리 — 소셜 탭 상단 스트립 · 전체화면 열람 · 작성 (설계 §4 PR-7).
 *
 * <p>24h 만료·작성 자격·열람 권한은 전부 서버가 판정한다(웹과 같은 `StoryService` 게이트) — 미니앱은
 * 표시와 액션 배선만 한다. 정적 렌더 하니스로는 effect가 안 도므로 뷰어의 두 결정(전이·열람 기록 대상)은
 * {@link nextStoryIndex}·{@link viewTargetId} 순수 함수로 뽑아 따로 계측한다.
 */

/** 배경 코드 → 색. 팔레트 밖 코드(옛 데이터·오타)는 기본으로 떨어뜨려 스타일 주입 자리를 안 만든다. */
function palette(bgCode: string | null) {
  return STORY_BG_CODES.find((bg) => bg.code === bgCode) ?? STORY_BG_CODES[0];
}

/**
 * 뷰어 전이 — 다음(+1)·이전(-1). 마지막에서 다음은 `null`(닫기)이고, 첫 카드에서 이전은 제자리다.
 * 다음 작성자로 자동으로 넘어가지 않는다 — 실수 탭에 남의 스토리가 열람 처리되면 되돌릴 수 없다.
 */
export function nextStoryIndex(current: number, direction: 1 | -1, total: number): number | null {
  if (direction === -1) return Math.max(0, current - 1);
  return current + 1 >= total ? null : current + 1;
}

/**
 * 이 카드를 보여줄 때 열람 기록(POST view)할 id — 없으면 `null`.
 * 내 스토리는 서버가 no-op이고 이미 열람한 카드는 멱등이라, 둘 다 요청 자체를 아낀다.
 */
export function viewTargetId(card: StoryCard, mine: boolean): number | null {
  return mine || card.viewed ? null : card.id;
}

/**
 * 작성 실패 안내 — 서버의 한글 검증 메시지는 `GlobalExceptionHandler`가 HTML `error` 뷰로 렌더해
 * 미니앱까지 오지 못한다(api.ts의 HTML 가드가 상태코드 문구로 대체). 그래서 상태코드로 안내를 나눈다.
 * 서버가 평문 메시지를 주는 날엔 그게 더 정확하므로 그대로 쓴다.
 */
export function createStoryMessage(error: Error): string {
  if (!(error instanceof ApiError) || error.message !== `요청에 실패했어요 (${error.status})`) return error.message;
  if (error.status === 429) return '스토리를 너무 자주 올렸어요. 잠시 후 다시 시도해 주세요.';
  if (error.status === 404) return '첨부한 책을 찾을 수 없어요. 다시 골라 주세요.';
  return '스토리를 올리지 못했어요. 문장은 1~500자, 활성 스토리는 최대 20장이에요.';
}

/**
 * 소셜 탭 상단 스트립 — 내 링(있으면) + 팔로잉 작성자 링.
 *
 * <p>피드가 비어도 작성 진입은 남기되 팔로우 유도 문구는 말하지 않는다 — 소셜 탭의 빈 상태 문구가
 * 이미 "아이디로 찾아 책방을 구경해 보세요"라고 안내하므로, 같은 말을 두 번 하면 화면만 시끄럽다.
 */
export function StoryStrip({
  feed,
  onOpen,
  onCompose,
}: {
  feed: StoryFeedResponse | null;
  onOpen: (author: AuthorStories, mine: boolean) => void;
  onCompose: () => void;
}) {
  if (feed === null) return null; // 아직 못 받음 — 빈 껍데기를 깜빡이지 않는다

  return (
    <div style={{ display: 'flex', gap: 10, overflowX: 'auto', padding: '4px 0 12px' }}>
      {feed.mine !== null && <Ring label="내 스토리" fresh={false} onClick={() => onOpen(feed.mine!, true)} />}
      {feed.groups.map((group) => (
        <Ring
          key={group.loginId ?? group.nickname}
          label={group.nickname}
          fresh={!group.allViewed}
          onClick={() => onOpen(group, false)}
        />
      ))}
      <Ring label="+ 스토리 쓰기" fresh={false} onClick={onCompose} />
    </div>
  );
}

function Ring({ label, fresh, onClick }: { label: string; fresh: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        flex: '0 0 auto',
        padding: '10px 14px',
        border: fresh ? '2px solid var(--adaptiveBlue500, #3182f6)' : '1px solid var(--adaptiveGrey200, #e5e8eb)',
        borderRadius: 999,
        background: 'var(--adaptiveGrey100, #f2f4f6)',
        fontSize: 13,
        cursor: 'pointer',
      }}
    >
      {label}
      {fresh && <span style={{ marginLeft: 4, fontSize: 11, color: 'var(--adaptiveBlue500, #3182f6)' }}>새 스토리</span>}
    </button>
  );
}

/**
 * 전체화면 열람 — 한 작성자의 활성 스토리를 작성순으로 넘겨 본다.
 *
 * <p>카드가 바뀔 때마다 열람을 기록한다. 실패는 삼킨다 — 기록이 안 됐다고 읽던 사람을 막을 이유가 없고,
 * 만료·차단으로 404가 나는 정상 경로가 있다(그건 다음 새로고침에서 목록으로 드러난다).
 */
export function StoryViewer({
  author,
  mine,
  onClose,
  onOpenProfile,
  onDeleted,
  onError,
}: {
  author: AuthorStories;
  mine: boolean;
  onClose: () => void;
  onOpenProfile: (loginId: string) => void;
  onDeleted: () => void;
  onError: (error: Error) => void;
}) {
  const [index, setIndex] = useState(0);
  const [viewers, setViewers] = useState<StoryViewerEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const card = author.stories[index];

  useEffect(() => {
    const target = card === undefined ? null : viewTargetId(card, mine);
    if (target !== null) void markStoryViewed(target).catch(() => {});
  }, [card, mine]);

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  if (card === undefined) return null;

  const step = (direction: 1 | -1) => {
    const next = nextStoryIndex(index, direction, author.stories.length);
    if (next === null) {
      onClose();
      return;
    }
    setViewers(null); // 카드마다 열람자가 다르다 — 앞 카드 목록이 남아 있으면 오독한다
    setIndex(next);
  };

  const remove = () => {
    setBusy(true);
    deleteStory(card.id)
      .then(() => {
        onDeleted();
        onClose();
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  const showViewers = () => {
    setBusy(true);
    fetchStoryViewers(card.id)
      .then(setViewers)
      .catch(fail)
      .finally(() => setBusy(false));
  };

  return (
    <StoryCardView
      author={author}
      card={card}
      index={index}
      mine={mine}
      viewers={viewers}
      busy={busy}
      error={error}
      onStep={step}
      onClose={onClose}
      onDelete={remove}
      onViewers={showViewers}
      onOpenProfile={onOpenProfile}
    />
  );
}

/** 열람 카드 본문 — 순수 표시. mine 플래그가 삭제·열람자와 책방 진입을 가른다. */
export function StoryCardView({
  author,
  card,
  index,
  mine,
  viewers,
  busy,
  error = null,
  onStep,
  onClose,
  onDelete,
  onViewers,
  onOpenProfile,
}: {
  author: AuthorStories;
  card: StoryCard;
  index: number;
  mine: boolean;
  viewers: StoryViewerEntry[] | null;
  busy: boolean;
  error?: string | null;
  onStep: (direction: 1 | -1) => void;
  onClose: () => void;
  onDelete: () => void;
  onViewers: () => void;
  onOpenProfile: (loginId: string) => void;
}) {
  const bg = palette(card.bgCode);
  const total = author.stories.length;

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 10,
        display: 'flex',
        flexDirection: 'column',
        padding: '24px 20px',
        background: bg.background,
        color: bg.color,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ flex: 1, fontSize: 14, fontWeight: 600 }}>
          {mine ? '내 스토리' : author.nickname} · {index + 1}/{total}
        </span>
        <button type="button" onClick={onClose} style={ghost(bg.color)}>
          닫기
        </button>
      </div>

      {/* 좌우 탭으로 넘긴다 — 인스타와 같은 조작. 버튼도 함께 둬 탭이 안 먹는 환경을 막는다. */}
      <div style={{ flex: 1, display: 'flex', alignItems: 'center' }}>
        <button type="button" aria-label="이전" onClick={() => onStep(-1)} style={tapZone} />
        <p style={{ flex: 1, fontSize: 20, lineHeight: 1.6, whiteSpace: 'pre-wrap', textAlign: 'center' }}>
          {card.text}
        </p>
        <button type="button" aria-label="다음" onClick={() => onStep(1)} style={tapZone} />
      </div>

      {card.bookTitle !== null && (
        // 표지는 서버가 준 것만 — 없으면 옛 아이콘 그대로. 카드 주인공은 문장이라 썸네일은 작게 둔다.
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 6, fontSize: 13, opacity: 0.8 }}>
          {card.bookCoverUrl === null ? (
            <span>📖</span>
          ) : (
            <img
              src={card.bookCoverUrl}
              alt=""
              loading="lazy"
              style={{ width: 24, height: 34, borderRadius: 3, objectFit: 'cover' }}
            />
          )}
          <span>{card.bookTitle}</span>
        </div>
      )}

      {error !== null && <p style={{ fontSize: 13, textAlign: 'center' }}>{error}</p>}

      {viewers !== null && (
        <div style={{ maxHeight: 140, overflowY: 'auto', fontSize: 13, opacity: 0.9 }}>
          {viewers.length === 0 ? (
            <p>아직 본 사람이 없어요.</p>
          ) : (
            viewers.map((v) => <p key={v.loginId}>{v.nickname}</p>)
          )}
        </div>
      )}

      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <button type="button" onClick={() => onStep(-1)} style={ghost(bg.color)}>
          이전
        </button>
        <button type="button" onClick={() => onStep(1)} style={{ ...ghost(bg.color), flex: 1 }}>
          다음
        </button>
        {mine ? (
          <>
            <button type="button" disabled={busy} onClick={onViewers} style={ghost(bg.color)}>
              본 사람
            </button>
            <button type="button" disabled={busy} onClick={onDelete} style={ghost(bg.color)}>
              삭제
            </button>
          </>
        ) : (
          author.loginId !== null && (
            <button type="button" onClick={() => onOpenProfile(author.loginId!)} style={ghost(bg.color)}>
              책방 보기
            </button>
          )
        )}
      </div>
    </div>
  );
}

/**
 * 작성 — 오늘 읽은 책의 한 문장. 책 첨부는 **공개 책만**(서버 불변식: 비공개 책장이 새는 유일 경로라
 * 막혀 있다) 이라 서재에서 공개 책만 골라 보여준다.
 */
export function StoryComposer({ onDone, onCancel, onError }: { onDone: () => void; onCancel: () => void; onError: (error: Error) => void }) {
  const [books, setBooks] = useState<MyBookSummary[]>([]);
  const [text, setText] = useState('');
  const [bookId, setBookId] = useState('');
  const [bgCode, setBgCode] = useState<string>(STORY_BG_CODES[0].code);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchShelf()
      .then((page) => setBooks(page.books.filter((b) => b.isPublic)))
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        // 책 목록 실패는 치명적이지 않다 — 책 없이도 문장은 올릴 수 있다
      });
  }, [onError]);

  const trimmed = text.trim();
  const submit = () => {
    setBusy(true);
    setError(null);
    createStory(trimmed, bookId === '' ? null : Number(bookId), bgCode)
      .then(onDone)
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else setError(createStoryMessage(e));
      })
      .finally(() => setBusy(false));
  };

  const bg = palette(bgCode);

  return (
    <Screen title="스토리 쓰기">
      <textarea
        value={text}
        disabled={busy}
        maxLength={500}
        placeholder="오늘 읽은 문장을 남겨 보세요. 팔로워에게 24시간 동안 보여요."
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
              border: option.code === bgCode ? '2px solid var(--adaptiveBlue500, #3182f6)' : '1px solid #d1d6db',
              cursor: 'pointer',
            }}
          />
        ))}
      </div>

      {books.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 6 }}>
            책 첨부 (공개 책만)
          </Text>
          <select
            value={bookId}
            disabled={busy}
            onChange={(e) => setBookId(e.target.value)}
            style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid #d1d6db' }}
          >
            <option value="">첨부 안 함</option>
            {books.map((b) => (
              <option key={b.id} value={b.id}>
                {b.title}
              </option>
            ))}
          </select>
        </div>
      )}

      <ErrorMessage message={error} />

      <div style={{ display: 'flex', gap: 8, marginTop: 24 }}>
        <Button style={{ flex: 1 }} loading={busy} disabled={trimmed === ''} onClick={submit}>
          올리기
        </Button>
        <Button variant="weak" disabled={busy} onClick={onCancel}>
          취소
        </Button>
      </div>
    </Screen>
  );
}

const tapZone = { width: 56, alignSelf: 'stretch', border: 'none', background: 'transparent', cursor: 'pointer' } as const;

const ghost = (color: string) =>
  ({
    padding: '10px 14px',
    borderRadius: 10,
    border: `1px solid ${color}`,
    background: 'transparent',
    color,
    fontSize: 13,
    cursor: 'pointer',
  }) as const;
