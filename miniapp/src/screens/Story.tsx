import { Button, Text } from '@toss/tds-mobile';
import type { ReactNode } from 'react';
import { useCallback, useEffect, useState } from 'react';

import type {
  BookMarginAllResponse,
  MarginBook,
  MarginEntry,
  MarginResponse,
  SharedMarginEntry,
  UserRow,
} from '../api';
import {
  ApiError,
  STORY_BG_CODES,
  createStory,
  deleteStory,
  fetchBookMargin,
  fetchBookMarginAll,
  fetchStoryLikers,
  likeStory,
  shareStory,
  unlikeStory,
  unshareStory,
} from '../api';
import { useBackClose } from '../back';
import { relativeTime } from '../format';
import { BookCover, ErrorMessage, Loading, Screen, Sheet, UserList } from '../ui';

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
 * 「함께 걸기」 고지 — 켜면 누구에게 보이는지. {@link visibilityNotice}(팔로워 축)와 <b>나란히</b> 선다:
 * 노출은 둘의 AND라 두 문장이 함께 있어야 전체가 설명된다.
 *
 * <p>비공개 책이면 <b>조건이 앞선다</b> — 켜 두는 것 자체는 유효하지만 지금은 아무에게도 안 보인다
 * (가시성은 읽기 시점 판정이고 책 게이트가 상위다). `undefined`(필드를 안 보내는 옛 서버)는
 * {@link visibilityNotice}와 같은 이유로 <b>공개로 간주</b>한다.
 */
export function shareNotice(isPublic: boolean | undefined): string {
  return isPublic === false
    ? '책을 공개로 바꾸면 이 책을 보는 모두에게 보여요.'
    : '이 책을 보는 모두에게 보여요.';
}

/**
 * 책축 탭의 라벨 — 아직 안 받은 개수(`null`)는 <b>숫자를 안 적는다</b>. 0을 먼저 그리면 「글이 없다」는
 * 거짓말을 한 프레임 보여 주고, 값이 도착하는 순간 숫자가 튄다. 진짜 0은 0으로 적는다.
 */
export function marginTabLabel(name: string, count: number | null): string {
  return count === null ? name : `${name} ${count}`;
}

/**
 * 하트 — 색을 <b>`currentColor`로 상속</b>한다. 배경 팔레트가 6종이고 그중 sunset이 붉은 주황(#c96a4a)이라
 * 빨간 하트는 거기서 사라진다. 카드 본문 색을 그대로 쓰면 여섯 배경 전부에서 읽히는 것이 보장되고,
 * 상태 구분은 색이 아니라 <b>채움 여부</b>가 진다.
 */
function Heart({ filled }: { filled: boolean }) {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 24 24"
      aria-hidden="true"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M12 20.6 4.2 12.8a4.9 4.9 0 0 1 6.9-6.9l.9.9.9-.9a4.9 4.9 0 0 1 6.9 6.9Z" />
    </svg>
  );
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
 * 여백에 들어오면서 측정을 끝냈다는 고지 — 「여백은 독서가 아니다」(사용자 결정 2026-08-22)라
 * <b>진입이 곧 종료</b>다({@link App}의 `openMargin`이 문에서 끝낸다).
 *
 * <p>말없이 끝내면 사용자에겐 시간이 사라진 것으로 읽힐다. 홈으로 돌아가면 탭바 원이 이미
 * 「측정 시작」으로 돌아가 있어 <b>상태</b>는 보이지만, <b>왜</b> 끝났는지는 이 자리에서만 말할 수 있다.
 */
export const TIMER_STOPPED_NOTICE = '여백을 열면서 측정을 마쳤어요';

/** 그 고지 한 줄 — 읽기·쓰기 두 화면이 같은 것을 쓴다(문구가 갈리면 같은 일이 다르게 읽힌다). */
export function TimerStoppedNotice() {
  return (
    <Text typography="st11" color="grey800" style={{ display: 'block', marginBottom: 14, wordBreak: 'keep-all' }}>
      {TIMER_STOPPED_NOTICE}
    </Text>
  );
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
  onOpenProfile,
  onError,
  timerStopped = false,
}: {
  loginId: string;
  bookId: number;
  onBack: () => void;
  /** 「여백에 글 남기기」 — 작성 화면 전환은 셸이 든다(전체 화면 전이의 주인은 하나여야 한다). */
  onCompose: (book: MarginBook) => void;
  /** 좋아요 명단에서 그 사람을 눌렀을 때 — 그의 책방으로 간다(전체 화면 전이는 셸이 든다). */
  onOpenProfile: (loginId: string) => void;
  onError: (error: Error) => void;
  /** 여기 들어오느라 측정을 끝냈는가 — {@link TIMER_STOPPED_NOTICE}를 여는 스위치다. */
  timerStopped?: boolean;
}) {
  const [margin, setMargin] = useState<MarginResponse | null>(null);
  /**
   * 「모두」 탭의 목록 — 그 탭을 <b>누를 때</b> 받는다. 내 여백만 보는 사람에게 남의 글까지 미리 받아
   * 두면 진입 핫패스가 두 배가 되는데, 그 값은 탭을 안 누르면 영영 안 쓰인다.
   */
  const [all, setAll] = useState<BookMarginAllResponse | null>(null);
  const [tab, setTab] = useState<'mine' | 'all'>('mine');
  /** 낙관적 「함께 걸기」 — 좋아요와 같은 이유다(서버 왕복을 기다리면 칩이 늦게 바뀐다). */
  const [shares, setShares] = useState<Record<number, boolean>>({});
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

  const likes = useMarginLikes(fail, onError);

  const load = useCallback(() => {
    setError(null); // 재시도가 성공했는데 지난 실패 문구가 남지 않게
    fetchBookMargin(loginId, bookId).then(setMargin).catch(fail);
  }, [loginId, bookId, fail]);

  useEffect(load, [load]);

  const isbn13 = margin?.book.isbn13 ?? null;

  /**
   * 「모두」 탭을 처음 열 때 한 번 받는다. 「함께 걸기」를 켜고 끄면 목록이 달라지므로, 토글 뒤에는
   * 캐시를 버려({@code setAll(null)}) 다음 진입이 서버 진실로 가게 한다.
   */
  useEffect(() => {
    if (tab !== 'all' || all !== null || isbn13 === null) return;
    fetchBookMarginAll(isbn13).then(setAll).catch(fail);
  }, [tab, all, isbn13, fail]);

  /**
   * 「함께 걸기」를 켜고 끈다 — <b>확인 시트가 없다</b>(즉시 되돌릴 수 있는 동작이라 한 탭 더 받을 이유가
   * 없다). 실패하면 칩을 원래대로 돌린다: 틀린 상태를 남기면 「걸었는데 안 보인다」가 된다.
   */
  const toggleShare = (entry: MarginEntry) => {
    const before = shares[entry.id] ?? entry.shared === true;
    setShares((m) => ({ ...m, [entry.id]: !before }));
    setAll(null); // 목록이 달라졌다 — 다음에 「모두」를 열면 새로 받는다
    (before ? unshareStory(entry.id) : shareStory(entry.id))
      .then((state) => setShares((m) => ({ ...m, [entry.id]: state.shared })))
      .catch((e: Error) => {
        setShares((m) => ({ ...m, [entry.id]: before }));
        fail(e);
      });
  };

  const remove = (id: number) => {
    setBusy(true);
    setError(null);
    deleteStory(id)
      .then(() => {
        setConfirmDeleteId(null);
        setAll(null); // 지운 글이 「모두」 탭의 옛 스냅으로 되살아나지 않게
        load(); // 서버가 준 목록이 진실 — 지운 카드를 손으로 빼지 않는다
      })
      .catch(fail)
      .finally(() => setBusy(false));
  };

  if (margin === null) {
    return (
      <Screen title="여백" onBack={onBack}>
        {timerStopped && <TimerStoppedNotice />}
        {/* 못 받았을 때 나갈 길만 있으면 실패가 곧 막다른 길이다 — 그 자리에서 다시 받을 길도 함께 준다. */}
        <ErrorMessage message={error} onRetry={load} />
        {error === null && <Loading />}
      </Screen>
    );
  }

  // 낙관적 값을 서버 목록 위에 얹는다 — 손대지 않은 글은 `undefined` 전개라 그대로 남는다.
  const entries = likes.merge(margin.entries).map((e) => ({ ...e, shared: shares[e.id] ?? e.shared }));
  const merged = { ...margin, entries };

  /**
   * 탭은 <b>내 책 + isbn13이 있을 때만</b> 선다. 남의 여백에서 「모두」를 열 수 있게 하는 것은 진입점
   * 확장이라 v1 범위 밖이고(설계 §2-④ 3순위), isbn 없는 책은 책축 좌표 자체가 없다.
   */
  const tabs =
    margin.self && isbn13 !== null ? (
      <MarginTabs
        tab={tab}
        mineCount={margin.entries.length}
        allCount={all?.totalCount ?? null}
        onSelect={setTab}
      />
    ) : undefined;

  return (
    <>
      {tab === 'all' && tabs !== undefined ? (
        all === null ? (
          <Screen title="이 책의 여백" onBack={onBack}>
            {tabs}
            <ErrorMessage message={error} />
            {error === null && <Loading />}
          </Screen>
        ) : (
          <BookMarginAllView
            data={{ ...all, entries: likes.merge(all.entries) }}
            now={Date.now()}
            error={error}
            onBack={onBack}
            onToggleLike={likes.toggleLike}
            onShowLikers={likes.showLikers}
            onOpenProfile={onOpenProfile}
            tabs={tabs}
          />
        )
      ) : (
        <MarginView
          loginId={loginId}
          margin={merged}
          now={Date.now()}
          busy={busy}
          confirmDeleteId={confirmDeleteId}
          error={error}
          tabs={tabs}
          onCompose={() => onCompose(margin.book)}
          onConfirmDelete={setConfirmDeleteId}
          onDelete={remove}
          onToggleLike={likes.toggleLike}
          onToggleShare={toggleShare}
          onShowLikers={likes.showLikers}
          onBack={onBack}
          timerStopped={timerStopped}
        />
      )}
      {likes.likersOf !== null && (
        <LikersSheet
          users={likes.likers}
          error={likes.likersError}
          // 닫으면서 여는 교체 경로 — 셸이 그 사람의 책방을 세운다(전체 화면 전이의 주인은 하나여야 한다).
          onSelect={(picked) => {
            likes.closeLikers();
            onOpenProfile(picked);
          }}
          onClose={likes.closeLikers}
          onRetry={() => likes.loadLikers(likes.likersOf as number)}
        />
      )}
    </>
  );
}


/** 좋아요가 붙는 최소 모양 — 사람축 {@link MarginEntry}와 책축 {@link SharedMarginEntry}가 둘 다 만족한다. */
export type Likeable = { id: number; likeCount: number; liked: boolean };

/**
 * 좋아요·명단 machinery — 사람축({@link BookMargin})과 책축({@link BookMarginAll}) <b>두 화면이 공유</b>한다.
 *
 * <p>갈라 두면 한쪽만 고치는 날이 온다: 낙관적 갱신의 되돌리기, 시트를 닫을 때 상태 비우기,
 * 하드웨어 뒤로가기가 시트부터 닫기 — 셋 다 빠뜨리기 쉬운데 증상은 조용하다.
 */
function useMarginLikes(fail: (e: Error) => void, onError: (e: Error) => void) {
  /** 낙관적 좋아요 — 서버 왕복을 기다리면 하트가 늦게 켜져 「안 눌렸다」로 읽힌다. */
  const [likes, setLikes] = useState<Record<number, { likeCount: number; liked: boolean }>>({});
  /** 명단이 열린 글 — `null`이면 닫힘. 시트는 한 번에 하나다(글마다 따로 열 이유가 없다). */
  const [likersOf, setLikersOf] = useState<number | null>(null);
  /** `null`이면 아직 받는 중 — 빈 배열(0명)과 구분해야 "없어요"를 먼저 깜빡이지 않는다. */
  const [likers, setLikers] = useState<UserRow[] | null>(null);
  const [likersError, setLikersError] = useState<string | null>(null);

  /**
   * 명단은 <b>열 때 받는다</b> — 여백 한 장에 글이 100개까지 실리므로 목록 응답에 글마다 동봉하면
   * 열지도 않을 명단이 한꺼번에 날아온다.
   */
  const loadLikers = useCallback(
    (id: number) => {
      setLikers(null);
      setLikersError(null);
      fetchStoryLikers(id)
        .then(setLikers)
        .catch((e: Error) => {
          if (e.name === 'UnauthorizedError') onError(e);
          else setLikersError(e.message);
        });
    },
    [onError],
  );

  // 시트를 닫으면 그 안의 상태를 비운다 — 안 그러면 다음에 열 때 옛 명단이 한 프레임 번쩍인다.
  const closeLikers = () => {
    setLikersOf(null);
    setLikers(null);
    setLikersError(null);
  };

  // 하드웨어 뒤로가기는 시트부터 닫는다 — 없으면 명단에서 누른 back이 여백 화면을 통째로 닫는다.
  useBackClose(likersOf !== null, closeLikers);

  /**
   * 낙관적으로 먼저 칠하고, 서버 값으로 덮고, 실패하면 되돌린다.
   *
   * <p>서버 응답을 그대로 덮는 것이 핵심이다 — 그 사이 남이 누른 것까지 반영된 <b>진짜 개수</b>가 오므로
   * 클라가 ±1로 센 추정치가 오래 남지 않는다.
   */
  const toggleLike = (entry: Likeable) => {
    const before = likes[entry.id] ?? { likeCount: entry.likeCount, liked: entry.liked };
    const after = { likeCount: before.likeCount + (before.liked ? -1 : 1), liked: !before.liked };
    setLikes((m) => ({ ...m, [entry.id]: after }));
    (after.liked ? likeStory(entry.id) : unlikeStory(entry.id))
      .then((state) => setLikes((m) => ({ ...m, [entry.id]: state })))
      .catch((e: Error) => {
        setLikes((m) => ({ ...m, [entry.id]: before })); // 되돌린다 — 틀린 개수를 남기지 않는다
        fail(e);
      });
  };

  const showLikers = (entry: Likeable) => {
    setLikersOf(entry.id);
    loadLikers(entry.id);
  };

  /** 낙관적 값을 서버 목록 위에 얹는다 — 손대지 않은 글은 `undefined` 전개라 그대로 남는다. */
  const merge = <T extends Likeable>(items: T[]): T[] => items.map((e) => ({ ...e, ...likes[e.id] }));

  return { toggleLike, showLikers, closeLikers, loadLikers, likersOf, likers, likersError, merge };
}

/**
 * 여백 화면의 탭 줄 — 「내 여백 / 모두」. 내가 가진 책이고 isbn13이 있을 때만 선다
 * (isbn 없는 책은 책축 좌표 자체가 없어 「모두」가 가리킬 자리가 없다).
 *
 * <p>「모두」 개수는 그 탭을 눌러야 받으므로 그전엔 `null`이고, 라벨이 숫자를 안 적는다
 * ({@link marginTabLabel}).
 */
export function MarginTabs({
  tab,
  mineCount,
  allCount,
  onSelect,
}: {
  tab: 'mine' | 'all';
  mineCount: number | null;
  allCount: number | null;
  onSelect: (tab: 'mine' | 'all') => void;
}) {
  const items = [
    { key: 'mine' as const, label: marginTabLabel('내 여백', mineCount) },
    { key: 'all' as const, label: marginTabLabel('모두', allCount) },
  ];

  return (
    <div style={{ display: 'flex', gap: 6, marginTop: 16 }}>
      {items.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          aria-pressed={tab === key}
          onClick={() => onSelect(key)}
          style={tabPillStyle(tab === key)}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

/** 탭 알약 — 홈 피드 탭과 같은 값이라 화면에 새 색이 늘지 않는다. */
const tabPillStyle = (active: boolean) =>
  ({
    padding: '6px 14px',
    border: 0,
    borderRadius: 20,
    background: active ? 'var(--adaptiveBlue50, #E7EEE2)' : 'transparent',
    color: active ? 'var(--adaptiveBlue700, #4F6B4C)' : 'var(--adaptiveGrey600, #6F6A5E)',
    fontSize: 14,
    fontWeight: 700,
    cursor: 'pointer',
  }) as const;

/**
 * 「이 책의 여백」 — 책 하나(isbn13)에 함께 걸린 글 전부. 순수 표시라 상태는 전부 밖에서 받는다
 * (정적 렌더 하니스가 「담기 안내」·빈 상태 분기에 닿는 유일한 길).
 *
 * <p><b>주인 이름이 없다</b>: 이 화면의 주인공은 사람이 아니라 책이다. 대신 카드마다 작성자 줄이 붙는다
 * (여러 사람의 글이 한 목록에 섞이므로 누가 썼는지가 카드의 정보다).
 */
export function BookMarginAllView({
  data,
  now,
  error,
  onBack,
  onToggleLike,
  onShowLikers,
  onOpenProfile,
  tabs,
  timerStopped = false,
}: {
  data: BookMarginAllResponse;
  /** 상대 시각의 기준 — 밖에서 받아야 테스트가 결정론이 된다. */
  now: number;
  error: string | null;
  onBack: () => void;
  onToggleLike: (entry: Likeable) => void;
  onShowLikers?: (entry: Likeable) => void;
  onOpenProfile: (loginId: string) => void;
  /** 「내 여백 / 모두」 탭 줄 — 내 책일 때만 셸이 넘긴다. */
  tabs?: ReactNode;
  timerStopped?: boolean;
}) {
  const { book, myBookId, totalCount, entries } = data;

  return (
    <Screen title="이 책의 여백" onBack={onBack}>
      {timerStopped && <TimerStoppedNotice />}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <BookCover url={book.coverUrl} title={book.title} width={48} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
            {book.title}
          </Text>
          {book.author !== null && (
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {book.author}
            </Text>
          )}
        </div>
      </div>

      {tabs}

      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 20 }}>
        함께 걸린 글 {totalCount}
      </Text>

      {/* 안 가진 책에는 「담기」 안내만 둔다(v1) — 낯선 책에 닿는 유일한 길이 검색이라, 뒤로 한 번
          가면 담기 버튼이 거기 있다. 화면 안에서 끝내는 전용 경로는 진입점이 늘어날 때 붙인다. */}
      {myBookId === null && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8, wordBreak: 'keep-all' }}>
          내 서재에 담으면 이 책의 여백에 글을 남길 수 있어요.
        </Text>
      )}

      <ErrorMessage message={error} />

      {entries.length === 0 ? (
        <Text typography="st11" color="grey600" style={{ display: 'block', marginTop: 16, wordBreak: 'keep-all' }}>
          아직 함께 걸린 글이 없어요. 이 책을 읽은 누군가가 걸면 여기에 쌓여요.
        </Text>
      ) : (
        entries.map((e) => (
          <MarginCard
            key={e.id}
            entry={e}
            now={now}
            self={false}
            busy={false}
            confirming={false}
            author={{ loginId: e.authorLoginId, nickname: e.authorNickname }}
            onOpenAuthor={onOpenProfile}
            onConfirmDelete={() => {}}
            onDelete={() => {}}
            onToggleLike={onToggleLike}
            onShowLikers={onShowLikers}
          />
        ))
      )}
    </Screen>
  );
}

/**
 * 책축 여백 컨테이너 — 검색 배지에서 <b>낯선 책</b>으로 들어오는 자리(사람 좌표가 없다).
 *
 * <p>내가 가진 책이면 「내 여백」 탭이 서고, 그걸 누르면 <b>사람축 화면으로 갈아탄다</b>
 * ({@link BookMargin}) — 거기가 작성·삭제·「함께 걸기」 토글이 사는 자리다. 같은 데이터를 두 컨테이너가
 * 각자 들면 어긋나므로, 화면을 옮기고 데이터는 한 곳에서만 든다.
 */
export function BookMarginAll({
  isbn13,
  onBack,
  onOpenMine,
  onOpenProfile,
  onError,
  timerStopped = false,
}: {
  isbn13: string;
  onBack: () => void;
  /** 「내 여백」 탭 — 내가 가진 책이면 그 책 id로 사람축 화면을 연다. */
  onOpenMine: (bookId: number) => void;
  onOpenProfile: (loginId: string) => void;
  onError: (error: Error) => void;
  timerStopped?: boolean;
}) {
  const [data, setData] = useState<BookMarginAllResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  const likes = useMarginLikes(fail, onError);

  const load = useCallback(() => {
    setError(null); // 재시도가 성공했는데 지난 실패 문구가 남지 않게
    fetchBookMarginAll(isbn13).then(setData).catch(fail);
  }, [isbn13, fail]);

  useEffect(load, [load]);

  if (data === null) {
    return (
      <Screen title="이 책의 여백" onBack={onBack}>
        {timerStopped && <TimerStoppedNotice />}
        {/* 못 받았을 때 나갈 길만 있으면 실패가 곧 막다른 길이다 — 그 자리에서 다시 받을 길도 함께 준다. */}
        <ErrorMessage message={error} onRetry={load} />
        {error === null && <Loading />}
      </Screen>
    );
  }

  const myBookId = data.myBookId;
  const likersOf = likes.likersOf;

  return (
    <>
      <BookMarginAllView
        data={{ ...data, entries: likes.merge(data.entries) }}
        now={Date.now()}
        error={error}
        onBack={onBack}
        onToggleLike={likes.toggleLike}
        onShowLikers={likes.showLikers}
        onOpenProfile={onOpenProfile}
        timerStopped={timerStopped}
        tabs={
          myBookId === null ? undefined : (
            <MarginTabs
              tab="all"
              mineCount={null}
              allCount={data.totalCount}
              onSelect={(next) => {
                if (next === 'mine') onOpenMine(myBookId);
              }}
            />
          )
        }
      />
      {likersOf !== null && (
        <LikersSheet
          users={likes.likers}
          error={likes.likersError}
          // 닫으면서 여는 교체 경로 — 셸이 그 사람의 책방을 세운다(전체 화면 전이의 주인은 하나여야 한다).
          onSelect={(picked) => {
            likes.closeLikers();
            onOpenProfile(picked);
          }}
          onClose={likes.closeLikers}
          onRetry={() => likes.loadLikers(likersOf)}
        />
      )}
    </>
  );
}

/**
 * 좋아요 명단 시트 — 누가 눌렀는지. 팔로워 시트와 같은 처지라 <b>데이터를 프롭으로</b> 받는다
 * (정적 렌더 하니스가 0명/N명 분기에 닿는 유일한 길).
 *
 * <p>서버가 차단 관계·핸들 없는 사람을 이미 걸러 주므로 여기서 다시 거르지 않는다 — 화면이 게이트를
 * 흉내 내기 시작하면 서버와 어긋나는 날이 온다.
 */
export function LikersSheet({
  users,
  error,
  onSelect,
  onClose,
  onRetry,
}: {
  /** `null`이면 아직 받는 중 — 빈 배열(0명)과 구분해야 "없어요"를 먼저 깜빡이지 않는다. */
  users: UserRow[] | null;
  error: string | null;
  onSelect: (loginId: string) => void;
  onClose: () => void;
  onRetry: () => void;
}) {
  return (
    <Sheet title="좋아요" onClose={onClose}>
      {/* 못 받았으면 그 자리에서 다시 받는다 — 실패가 곧 빈 시트(막다른 길)가 되지 않게. */}
      <ErrorMessage message={error} onRetry={onRetry} />
      {users === null ? (
        error === null && <Loading />
      ) : (
        // 개수가 0인 글엔 애초에 손잡이가 없다 — 그 사이 누른 사람이 취소하면 여기 닿는다.
        <UserList users={users} emptyMessage="아직 아무도 누르지 않았어요." onSelect={onSelect} />
      )}
    </Sheet>
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
  tabs,
  onCompose,
  onConfirmDelete,
  onDelete,
  onToggleLike,
  onToggleShare,
  onShowLikers,
  onBack,
  timerStopped = false,
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
  onToggleLike: (entry: MarginEntry) => void;
  /** 내 글의 「함께 걸기」 토글 — 내 여백에서만 넘어온다(남의 글은 서버가 404로 거절한다). */
  onToggleShare?: (entry: MarginEntry) => void;
  onShowLikers: (entry: MarginEntry) => void;
  /** 「내 여백 / 모두」 탭 줄 — 내 책이고 isbn13이 있을 때만 셸이 넘긴다. */
  tabs?: ReactNode;
  onBack: () => void;
  /** 여기 들어오느라 측정을 끝냈는가 — {@link TIMER_STOPPED_NOTICE}를 여는 스위치다. */
  timerStopped?: boolean;
}) {
  const { book, ownerNickname, self, following, entries } = margin;

  return (
    <Screen title="여백" onBack={onBack}>
      {timerStopped && <TimerStoppedNotice />}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <BookCover url={book.coverUrl} title={book.title} width={48} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
            {book.title}
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
            {[book.author, `${ownerNickname} @${loginId}`].filter((s) => s !== null).join(' · ')}
          </Text>
        </div>
      </div>

      {tabs}

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
            onToggleLike={onToggleLike}
            onToggleShare={self ? onToggleShare : undefined}
            onShowLikers={onShowLikers}
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
  author,
  onConfirmDelete,
  onDelete,
  onToggleLike,
  onToggleShare,
  onShowLikers,
  onOpenAuthor,
}: {
  entry: MarginEntry;
  now: number;
  self: boolean;
  busy: boolean;
  confirming: boolean;
  onConfirmDelete: (id: number | null) => void;
  onDelete: (id: number) => void;
  /**
   * 좋아요 손잡이 — <b>있으면 하트 버튼, 없으면 하트 없음</b>. 이 프롭의 유무가 게이트다: `self`로
   * 갈랐다면 서재의 인라인 미리보기가 <b>내 글에 `self={false}`</b>를 넘기는 탓에 내 글에 하트가 떴을 것이다.
   *
   * <p>받은 글은 전부 누를 수 있다(자기 글 포함 — 2026-08-20). 그래서 화면에 옮길 게이트가 더는 없고,
   * 여기 남은 판단은 「이 자리에 손잡이를 둘 것인가」뿐이다.
   */
  onToggleLike?: (entry: MarginEntry) => void;
  /** 개수 줄의 손잡이 — 있으면 눌러서 명단을 연다. 미리보기(서재 박스)엔 열 시트가 없어 안 넘긴다. */
  onShowLikers?: (entry: MarginEntry) => void;
  /**
   * 작성자 줄 — <b>책축 목록에만</b> 붙는다. 사람축은 진입 자체가 「누구의 여백」이라 카드마다 같은
   * 이름을 반복할 이유가 없지만, 책축은 여러 사람의 글이 한 목록에 섞이므로 누가 썼는지가 정보다.
   */
  author?: { loginId: string; nickname: string };
  /** 작성자 줄의 손잡이 — 있으면 눌러서 그의 책방으로 간다(전체 화면 전이는 셸이 든다). */
  onOpenAuthor?: (loginId: string) => void;
  /**
   * 「함께 걸기」 손잡이 — <b>있으면 칩이 버튼, 없으면 사실값 글자</b>({@link onToggleLike} 관례).
   * 내 카드에만 넘긴다: 남의 글을 내가 걸거나 내릴 수는 없다(서버도 404로 거절한다).
   *
   * <p>손잡이가 있을 땐 <b>꺼진 글에도</b> 칩이 선다 — 사후에 켜는 길이 카드에 없으면 「함께 걸기」는
   * 작성 순간에만 정할 수 있는 값이 되어 버린다.
   */
  onToggleShare?: (entry: MarginEntry) => void;
}) {
  const bg = palette(entry.bgCode);
  const shared = entry.shared === true;

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
      {/* 인용은 主가 아니라 從이다 — 작게·옅게 두고 세로선으로만 가른다. 크게 뽑으면 카드의 주인공이
          남의 문장이 되어 「내 독서 기록」이 명언 카드가 된다. 세리프로 가르지 않는 이유: 폰에 한글
          세리프가 없어 `serif`가 안드로이드는 명조·iOS는 고딕으로 갈린다(폰마다 다른 카드). */}
      {/* 작성자가 카드 머리에 선다 — 책축 목록에서 「누가 썼나」는 본문보다 먼저 읽히는 정보다. */}
      {author !== undefined &&
        (onOpenAuthor === undefined ? (
          <p style={authorLine}>
            {author.nickname} @{author.loginId}
          </p>
        ) : (
          <button
            type="button"
            aria-label={`${author.nickname}님의 책방 보기`}
            onClick={() => onOpenAuthor(author.loginId)}
            style={{ ...authorLine, border: 'none', background: 'transparent', cursor: 'pointer', color: 'inherit' }}
          >
            {author.nickname} @{author.loginId}
          </button>
        ))}
      {entry.quote !== null && (
        <blockquote
          style={{
            margin: '0 0 14px',
            paddingLeft: 12,
            borderLeft: `2px solid ${bg.color}`,
            fontSize: 16,
            lineHeight: 1.6,
            opacity: 0.85,
            whiteSpace: 'pre-wrap',
            wordBreak: 'keep-all',
          }}
        >
          {entry.quote}
        </blockquote>
      )}
      <p style={{ margin: 0, fontSize: 17, lineHeight: 1.65, whiteSpace: 'pre-wrap', wordBreak: 'keep-all' }}>
        {entry.text}
      </p>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12, fontSize: 13, opacity: 0.75 }}>
        <span style={{ flex: 1, minWidth: 0 }}>{relativeTime(entry.createdAt, now)}</span>
        {/* 하트는 누르기/취소만 진다 — 개수를 같은 버튼에 넣으면 명단을 보려다 좋아요가 눌린다. */}
        {onToggleLike !== undefined && (
          <button
            type="button"
            aria-label={entry.liked ? '좋아요 취소' : '좋아요'}
            aria-pressed={entry.liked}
            onClick={() => onToggleLike(entry)}
            style={{ ...ghost(bg.color), display: 'inline-flex', alignItems: 'center' }}
          >
            <Heart filled={entry.liked} />
          </button>
        )}
        {/* 「함께 걸림」은 사실값이라 손잡이가 없어도 보인다(남의 카드에도 무해하다 — 그가 건 것이 맞다).
            손잡이가 있으면 꺼진 글에도 서서 사후에 켜는 길이 된다. */}
        {(shared || onToggleShare !== undefined) &&
          (onToggleShare === undefined ? (
            <span style={{ flex: '0 0 auto' }}>함께 걸림</span>
          ) : (
            <button
              type="button"
              aria-label={shared ? '함께 걸기 끄기' : '함께 걸기'}
              aria-pressed={shared}
              disabled={busy}
              onClick={() => onToggleShare(entry)}
              style={{ ...ghost(bg.color), opacity: shared ? 1 : 0.7 }}
            >
              {shared ? '함께 걸림' : '함께 걸기'}
            </button>
          ))}
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

      {/* 개수는 데이터라 손잡이와 무관하게 보이고(주인도 남이 눌러 준 걸 안다), 여는 손잡이만 조건부다.
          0은 줄 자체를 안 만든다 — 빈 상태를 숫자로 박제하면 「아무도 안 눌렀다」가 카드마다 반복된다.
          아래 줄로 내린 이유: 시각·하트·지우기가 있는 푸터에 넷째 칸을 우겨넣으면 좁은 폭에서 접힌다. */}
      {entry.likeCount > 0 &&
        (onShowLikers === undefined ? (
          <p style={likesLine(bg.color)}>좋아요 {entry.likeCount}명</p>
        ) : (
          <button
            type="button"
            aria-label={`좋아요 ${entry.likeCount}명 보기`}
            onClick={() => onShowLikers(entry)}
            style={{ ...likesLine(bg.color), border: 'none', background: 'transparent', cursor: 'pointer' }}
          >
            좋아요 {entry.likeCount}명
          </button>
        ))}
    </div>
  );
}

/** 작성자 줄 — 카드 색을 상속해 팔레트 6색 어디서나 읽힌다(하트와 같은 규칙). */
const authorLine = {
  display: 'block',
  width: 'auto',
  margin: '0 0 10px',
  padding: 0,
  fontSize: 13,
  fontWeight: 700,
  opacity: 0.8,
  textAlign: 'left',
} as const;

/**
 * 「좋아요 N명」 줄 — 손잡이일 때도 글자일 때도 같은 자리·같은 크기다. 세로 여백이 손가락 자리를
 * 만든다: 12px 글자 높이만으로는 손끝이 옆 카드에 닿는다.
 */
const likesLine = (color: string) =>
  ({
    display: 'block',
    width: 'auto',
    margin: 0,
    marginTop: 8,
    padding: '6px 0 0',
    color,
    fontSize: 13,
    opacity: 0.75,
    textAlign: 'left',
    textDecoration: 'underline',
  }) as const;

/**
 * 글 남기기 — 진입점이 <b>이미 그 책</b>이라 책을 고를 것이 없다(옛 첨부 select와 `/api/books` 조회는 삭제).
 * 남는 것은 문장·배경·1~500자 카운터뿐이다.
 *
 * <p>헤더에 뒤로가기를 두지 않는다 — 아래 「취소」가 이미 출구라 중복이었다(토스 네비바의 `‹`까지 세면
 * 한 화면에 나가는 화살표가 셋이었다). 안드로이드 하드웨어 뒤로가기는 셸의 `useBackClose`가 맡는다.
 */
export function StoryComposer({
  book,
  onDone,
  onCancel,
  onError,
  timerStopped = false,
}: {
  book: MarginBook;
  onDone: () => void;
  onCancel: () => void;
  onError: (error: Error) => void;
  /** 여기 들어오느라 측정을 끝냈는가 — 홈·서재 여백 문은 상세를 거치지 않고 여기로 직행한다. */
  timerStopped?: boolean;
}) {
  const [text, setText] = useState('');
  const [quote, setQuote] = useState('');
  const [bgCode, setBgCode] = useState<string>(STORY_BG_CODES[0].code);
  /** 「함께 걸기」 — <b>기본 꺼짐</b>. 켜는 것은 언제나 명시적 행동이다(서버의 `default false`와 같은 방향). */
  const [shared, setShared] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const trimmed = text.trim();
  const trimmedQuote = quote.trim();
  const submit = () => {
    setBusy(true);
    setError(null);
    createStory(trimmed, book.id, bgCode, trimmedQuote === '' ? null : trimmedQuote, shared)
      .then(onDone)
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onError(e);
        else setError(createStoryMessage(e));
      })
      .finally(() => setBusy(false));
  };

  const bg = palette(bgCode);

  return (
    <Screen title="여백에 글 남기기">
      {timerStopped && <TimerStoppedNotice />}
      {/* 가시성 고지는 placeholder가 아니라 캡션이다 — placeholder는 첫 글자에 사라지는데, 정작
          "이게 누구에게 보이나"가 필요한 순간은 쓰는 도중이다. */}
      <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 12, wordBreak: 'keep-all' }}>
        『{book.title}』의 여백 · {visibilityNotice(book.isPublic)}
      </Text>
      {/* 두 칸을 한 배경 안에 넣는다 — 쓰는 동안 보이는 것이 곧 카드여야 미리보기 값을 한다.
          라벨·카운터도 안에 두는 이유는 같다(밖으로 빼면 배경마다 대비가 어긋난다). */}
      <div style={{ background: bg.background, color: bg.color, borderRadius: 12, padding: 16 }}>
        <p style={composerLabel}>책에서 옮긴 문장 (선택)</p>
        <textarea
          value={quote}
          disabled={busy}
          maxLength={200}
          placeholder="밑줄 그은 문장을 옮겨 보세요."
          onChange={(e) => setQuote(e.target.value)}
          style={{ ...composerField(bg.color), minHeight: 56, fontSize: 16, borderLeft: `2px solid ${bg.color}`, paddingLeft: 12 }}
        />
        <p style={{ ...composerLabel, textAlign: 'right', marginTop: 4 }}>{trimmedQuote.length}/200</p>

        <div style={{ height: 1, background: bg.color, opacity: 0.15, margin: '14px 0' }} />

        <p style={composerLabel}>내 생각</p>
        <textarea
          value={text}
          disabled={busy}
          maxLength={500}
          placeholder="그 문장에 대해 든 생각을 남겨 보세요."
          onChange={(e) => setText(e.target.value)}
          style={{ ...composerField(bg.color), minHeight: 120, fontSize: 17 }}
        />
        <p style={{ ...composerLabel, textAlign: 'right', marginTop: 4 }}>{trimmed.length}/500</p>
      </div>

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

      {/* 「함께 걸기」는 배경 팔레트 아래·버튼 위다 — 쓰기가 끝난 뒤 마지막으로 정하는 값이고,
          체크를 카드 안에 넣으면 여섯 배경마다 대비를 다시 맞춰야 한다. */}
      <label style={shareRow}>
        <input
          type="checkbox"
          checked={shared}
          disabled={busy}
          onChange={(e) => setShared(e.target.checked)}
          style={{ width: 18, height: 18, flex: '0 0 auto', accentColor: '#6E8A6A' }}
        />
        <span style={{ minWidth: 0 }}>
          <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
            이 책의 여백에 함께 걸기
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2, wordBreak: 'keep-all' }}>
            {shareNotice(book.isPublic)}
          </Text>
        </span>
      </label>

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

/** 「함께 걸기」 줄 — 체크와 두 줄 설명이 한 손가락 자리에 든다(라벨 전체가 탭 영역이다). */
const shareRow = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 10,
  marginTop: 16,
  cursor: 'pointer',
} as const;

/** 컴포저 라벨·카운터 — 배경 위에 얹히므로 색은 상속받고 농도만 낮춘다(팔레트 6색 어디서나 읽힌다). */
const composerLabel = {
  margin: 0,
  fontSize: 12,
  opacity: 0.6,
  color: 'inherit',
} as const;

/** 배경 박스 안의 입력칸 — 테두리·배경을 지워 「박스 자체가 카드」로 보이게 한다. */
const composerField = (color: string) =>
  ({
    width: '100%',
    padding: 0,
    marginTop: 6,
    border: 'none',
    outline: 'none',
    background: 'transparent',
    color,
    fontFamily: 'inherit',
    lineHeight: 1.6,
    resize: 'vertical',
  }) as const;

const ghost = (color: string) =>
  ({
    flex: '0 0 auto',
    padding: '6px 10px',
    borderRadius: 8,
    border: `1px solid ${color}`,
    background: 'transparent',
    color,
    fontSize: 13,
    cursor: 'pointer',
  }) as const;
