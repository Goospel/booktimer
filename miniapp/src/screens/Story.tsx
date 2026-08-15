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
import { BookCover, COVER_FG, ErrorMessage, Screen, coverColor, initialOf } from '../ui';

/**
 * 여백 — 소셜 탭 상단 스트립 · 전체화면 열람 · 작성 (설계 §4 PR-7).
 *
 * <p>인스타를 베껴 「스토리」로 시작했지만 24시간 뒤 사라지는 게 의도와 어긋나 2026-08-16에 어휘와
 * 수명을 함께 바꿨다 — **여백은 지우기 전까지 남는다**. 파일·타입 이름은 `Story`로 남아 있다(서버 API가
 * `/api/stories`라 맞춰 둔 것).
 *
 * <p>작성 자격·열람 권한은 전부 서버가 판정한다(웹과 같은 `StoryService` 게이트) — 미니앱은
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
 * 내 여백은 서버가 no-op이고 이미 열람한 카드는 멱등이라, 둘 다 요청 자체를 아낀다.
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
  if (error.status === 429) return '여백을 너무 자주 남겼어요. 잠시 후 다시 시도해 주세요.';
  if (error.status === 404) return '첨부한 책을 찾을 수 없어요. 다시 골라 주세요.';
  return '여백을 남기지 못했어요. 문장은 1~500자까지 쓸 수 있어요.';
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
    <div className="no-scrollbar" style={{ display: 'flex', gap: 10, overflowX: 'auto', padding: '4px 0 12px' }}>
      {feed.mine !== null && (
        <Ring seed={feed.mine.nickname} caption="내 여백" onClick={() => onOpen(feed.mine!, true)} />
      )}
      {feed.groups.map((group) => (
        <Ring
          key={group.loginId ?? group.nickname}
          seed={group.nickname}
          caption={group.nickname}
          fresh={!group.allViewed}
          onClick={() => onOpen(group, false)}
        />
      ))}
      <Ring seed={null} caption="여백 적기" onClick={onCompose} />
    </div>
  );
}

/** 링 하나의 지름 — 손가락 최소치(44px)를 넘기고, 캡션까지 합쳐도 스트립이 한 줄에 들어간다. */
const RING_SIZE = 56;

/**
 * 여백 링 — 원형 이니셜 아바타 + 바깥 링 + 닉네임 캡션(인스타 관습).
 *
 * <p>텍스트 알약이던 시절엔 그 어휘가 화면에 서지 않아 그냥 버튼 줄로 읽혔다. 아바타 색은
 * 무표지 책과 같은 `coverColor`라 **같은 사람은 언제 그려도 같은 색**이다(닉네임이 곧 씨앗).
 *
 * <p>`seed`가 `null`이면 작성 타일(+ 아이콘) — 사람 링과 같은 크기·자리를 쓰되 이니셜을 세우지 않는다.
 * 미열람 표식은 링 색이 맡지만 **색만으로는 구분 못 하는 사람이 있어** 이름에도 남긴다(aria-label).
 */
function Ring({
  seed,
  caption,
  fresh = false,
  onClick,
}: {
  seed: string | null;
  caption: string;
  fresh?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={fresh ? `${caption} 새 여백` : caption}
      style={{
        flex: '0 0 auto',
        width: RING_SIZE + 12,
        padding: 0,
        border: 'none',
        background: 'transparent',
        cursor: 'pointer',
      }}
    >
      <div
        style={{
          width: RING_SIZE,
          height: RING_SIZE,
          margin: '0 auto',
          padding: 2,
          borderRadius: '50%',
          background: fresh ? 'var(--adaptiveBlue500, #6E8A6A)' : 'var(--adaptiveGrey200, #E4DDD0)',
        }}
      >
        <div
          style={{
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: '50%',
            // 링과 아바타 사이 종이색 틈 — 없으면 링이 아니라 그냥 두꺼운 테두리로 읽힌다.
            border: '2px solid #FCFAF5',
            fontSize: 22,
            background: seed === null ? 'var(--adaptiveGrey100, #FCFAF5)' : coverColor(seed),
            color: seed === null ? 'var(--adaptiveBlue500, #6E8A6A)' : COVER_FG,
          }}
        >
          {seed === null ? '+' : initialOf(seed)}
        </div>
      </div>
      <span
        style={{
          display: 'block',
          marginTop: 6,
          fontSize: 11,
          color: 'var(--adaptiveGrey600, #6F6A5E)',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {caption}
      </span>
    </button>
  );
}

/**
 * 전체화면 열람 — 한 작성자의 여백을 작성순으로 넘겨 본다.
 *
 * <p>카드가 바뀔 때마다 열람을 기록한다. 실패는 삼킨다 — 기록이 안 됐다고 읽던 사람을 막을 이유가 없고,
 * 삭제·차단으로 404가 나는 정상 경로가 있다(그건 다음 새로고침에서 목록으로 드러난다).
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
        // 플로팅 탭바(App.BottomTabBar, zIndex 100)보다 위 — 낮으면 전체화면 뷰어 위로 탭바가 뚫고 올라온다.
        zIndex: 200,
        display: 'flex',
        flexDirection: 'column',
        // 노치 아래로 세그먼트가 깔리지 않게 — 전체화면이라 상태바를 앱이 직접 피해야 한다.
        padding: 'calc(24px + env(safe-area-inset-top)) 20px 24px',
        background: bg.background,
        color: bg.color,
      }}
    >
      {/* 인스타식 진행 세그먼트 — 카드 수만큼 나누고 현재 인덱스까지 채운다. 수치는 aria로 남긴다. */}
      <div
        role="progressbar"
        aria-label="여백 진행"
        aria-valuemin={1}
        aria-valuenow={index + 1}
        aria-valuemax={total}
        style={{ display: 'flex', gap: 3, marginBottom: 12 }}
      >
        {author.stories.map((story, i) => (
          <span
            key={story.id}
            style={{ flex: 1, height: 2, borderRadius: 1, background: bg.color, opacity: i <= index ? 1 : 0.3 }}
          />
        ))}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ flex: 1, fontSize: 14, fontWeight: 600 }}>{mine ? '내 여백' : author.nickname}</span>
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
          {/* 표지가 없으면 옛 아이콘 그대로, 있으면 공용 썸네일 — 로드 실패 폴백을 같이 물려받는다. */}
          {card.bookCoverUrl === null ? <span>📖</span> : <BookCover url={card.bookCoverUrl} width={24} />}
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
    <Screen title="여백 적기">
      <textarea
        value={text}
        disabled={busy}
        maxLength={500}
        placeholder="읽다가 마음에 걸린 문장이나 생각을 적어 보세요. 팔로워에게 보여요."
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

      {books.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 6 }}>
            책 첨부 (공개 책만)
          </Text>
          <select
            value={bookId}
            disabled={busy}
            onChange={(e) => setBookId(e.target.value)}
            style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid #E4DDD0' }}
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
