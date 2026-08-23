import { Button } from '@toss/tds-mobile';
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
import { BookCover, ErrorMessage, HANDWRITING, Loading, Screen, Sheet, Text, UserList } from '../ui';

/**
 * 여백 — <b>책에 딸린 자리</b>와 거기 쌓이는 글 (2026-08-16 재설계).
 *
 * <p>인스타를 베껴 스트립·전체화면 뷰어·진행바·열람 기록으로 시작했지만, 그 문법이 "24시간 뒤 사라지는
 * 남의 근황"을 전제해 책과 아무 관계가 없었다. 지금은 <b>책 → 그 책의 여백</b> 한 경로뿐이다: 책방 격자에서
 * 책을 누르거나(발광 = 24시간 안에 새 글), 홈 소식의 여백 줄에서 곧장 그 책으로 점프한다.
 *
 * <p>파일·타입 이름은 `Story`로 남아 있다 — 서버 경로가 `/api/stories`라 맞춰 둔 것(#814 결정).
 *
 * <p>노출 권한(차단·IDOR·PRIVATE)은 전부 서버가 판정한다 — 미니앱은 서버가 준
 * `self`·`entries`를 표시와 액션으로 옮길 뿐이다. 정적 렌더 하니스로는 effect가 안 도므로
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
 * <p>경계는 <b>미만(&lt;)</b>이다: 정각 24시간은 이미 창 밖이다. `null`(그 책에 글이 없어 서버가
 * 가린 경우)은 false — 발광은 "새 글이 있다"는 단언이라 모르는 상태를 참으로 올리지 않는다.
 */
export function hasFreshStory(lastStoryAt: string | null, now: number): boolean {
  return lastStoryAt !== null && now - Date.parse(lastStoryAt) < FRESH_WINDOW_MS;
}

/**
 * 작성·여백 화면의 가시성 안내 — <b>쓰는 순간</b>의 고지다(공개 전환 확인 시트는 「공개하는 순간」을 맡는다).
 *
 * <p>2026-08-22에 「팔로워에게 보여요」를 걷었다. 팔로우가 열람 권한에서 빠져(서버 {@code marginOf})
 * 공개 책의 글은 <b>누구에게나</b> 보이는데, 옛 문구는 노출 범위를 실제보다 좁게 말하고 있었다.
 * `undefined`(필드를 안 보내는 옛 서버)는 <b>공개로 간주</b>한다 — 보수적인 쪽은 「나만 본다」가
 * 아니다: 실제로는 새는 글을 안 샌다고 말하는 것이 더 위험한 거짓말이다.
 */
export function visibilityNotice(isPublic: boolean | undefined): string {
  return isPublic === false
    ? '비공개 책이에요. 이 글은 나만 봐요. 책을 공개로 바꾸면 누구나 볼 수 있어요.'
    : '공개 책이라 누구나 볼 수 있어요.';
}

/**
 * 「모두의 여백에 올리기」 고지 — <b>노출이 아니라 게재</b>를 말한다(2026-08-22).
 *
 * <p>팔로우 축이 사라지기 전까지 이 체크박스는 권한 스위치였고({@link visibilityNotice}의 팔로워 축과
 * AND), 그래서 「모두에게 보여요」라고 말했다. 지금은 공개 책의 글이 올리든 안 올리든 누구에게나
 * 보이므로, 이 값이 정하는 것은 <b>책축 목록에 실려 그 책을 보는 사람에게 발견되는가</b>뿐이다.
 * 「보여요」로 두면 「안 올리면 안 보인다」는 반대 오해를 만든다 — 그래서 「올라가요」다.
 *
 * <p>비공개 책이면 <b>조건이 앞선다</b> — 켜 두는 것 자체는 유효하지만 지금은 아무 데도 안 실린다
 * (책 게이트가 상위다). `undefined`는 {@link visibilityNotice}와 같은 이유로 공개로 간주한다.
 */
export function shareNotice(isPublic: boolean | undefined): string {
  return isPublic === false
    ? '책을 공개로 바꾸면 「모두의 여백」에 올라가요.'
    : '이 책의 「모두의 여백」에 함께 올라가요.';
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
  /** ⋯ 가 열린 글 — `null`이면 닫힘. 시트는 한 번에 하나다(좋아요 명단과 같은 규칙). */
  const [menuOf, setMenuOf] = useState<number | null>(null);
  /** 펼쳐 둔 글 — 접기가 기본이라 여기 담긴 것만 전문이 보인다. */
  const [expanded, setExpanded] = useState<ReadonlySet<number>>(new Set());

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  const likes = useMarginLikes(fail, onError);

  /**
   * 시트를 닫을 땐 지우기 확인도 함께 접는다 — 안 접으면 다음에 ⋯ 를 열었을 때 「정말 지우기」가 이미
   * 서 있어, 확인 단계를 <b>건너뛴 삭제 버튼</b>이 첫 화면이 된다.
   */
  const closeMenu = useCallback(() => {
    setMenuOf(null);
    setConfirmDeleteId(null);
  }, []);

  useBackClose(menuOf !== null, closeMenu);

  const toggleExpand = (id: number) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });

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
    setAll(null); // 목록이 달라졌다 — 다음에 「모두의 여백」을 열면 새로 받는다
    closeMenu(); // 눌렀으면 끝난 동작이다 — 시트를 남기면 결과(칩)가 시트에 가려 안 보인다
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
        closeMenu(); // 지운 글의 시트가 남아 있으면 없는 글을 관리하는 화면이 된다
        setAll(null); // 지운 글이 「모두의 여백」 탭의 옛 스냅으로 되살아나지 않게
        load(); // 서버가 준 목록이 진실 — 지운 행을 손으로 빼지 않는다
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

  const tabs = showMarginTabs(margin.self, isbn13) ? (
      <MarginTabs
        tab={tab}
        mineCount={margin.entries.length}
        allCount={all?.totalCount ?? null}
        onSelect={setTab}
      />
    ) : undefined;

  const menuEntry = entries.find((e) => e.id === menuOf) ?? null;

  return (
    <>
      {tab === 'all' && tabs !== undefined ? (
        all === null ? (
          <Screen title="여백" onBack={onBack}>
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
            expanded={expanded}
            onToggleExpand={toggleExpand}
          />
        )
      ) : (
        <MarginView
          loginId={loginId}
          margin={merged}
          now={Date.now()}
          error={error}
          tabs={tabs}
          expanded={expanded}
          onCompose={() => onCompose(margin.book)}
          onToggleLike={likes.toggleLike}
          onShowLikers={likes.showLikers}
          onOpenMenu={(e) => setMenuOf(e.id)}
          onToggleExpand={toggleExpand}
          onBack={onBack}
          timerStopped={timerStopped}
        />
      )}
      {menuEntry !== null && (
        <MarginMenuSheet
          entry={menuEntry}
          canShare={isbn13 !== null}
          confirming={confirmDeleteId === menuEntry.id}
          busy={busy}
          onShare={toggleShare}
          onConfirmDelete={setConfirmDeleteId}
          onDelete={remove}
          onClose={closeMenu}
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
/**
 * 탭줄이 서는가 — <b>내 책 + isbn13</b>. 남의 여백에서 「모두의 여백」을 열 수 있게 하는 것은 진입점
 * 확장이라 v1 범위 밖이고(설계 §2-④ 3순위), isbn 없는 책은 책축 좌표 자체가 없다.
 *
 * <p>컨테이너 JSX에서 <b>꺼내 둔</b> 이유는 계측이다. 조건이 JSX 안에만 있으면 정적 렌더 하니스가 못 닿아
 * 이 게이트를 지워도 전건이 통과한다(리뷰 실측). 게이트가 무너지면 남의 여백에 탭줄이 서고, 「모두의
 * 여백」을 누른 화면이 <b>남의 책 id로 여는 글쓰기 버튼</b>을 세운다 — 그 경로는 {@link MarginView}의
 * `self` 가드가 덮지 못한다(그 화면은 {@link BookMarginAllView}다).
 */
export const showMarginTabs = (self: boolean, isbn13: string | null): boolean => self && isbn13 !== null;

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
    { key: 'mine' as const, label: marginTabLabel('내가 쓴 여백', mineCount) },
    { key: 'all' as const, label: marginTabLabel('모두의 여백', allCount) },
  ];

  return (
    <div style={{ display: 'flex', marginTop: 16, borderBottom: '1px solid #E2DACA' }}>
      {items.map(({ key, label }) => (
        <button
          key={key}
          type="button"
          aria-pressed={tab === key}
          onClick={() => onSelect(key)}
          style={tabStyle(tab === key)}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

/**
 * 탭 한 칸 — <b>밑줄 2분할</b>이다(2026-08-22 게시판 개편). 옛 알약은 목록 위에 뜬 「필터」로 읽혀서,
 * 두 좌표계를 오가는 탭이라는 것이 전달되지 않았다. 화면 폭을 반씩 먹고 아래 목록과 선으로 이어져야
 * 게시판 상단 탭으로 읽힌다.
 *
 * <p>`marginBottom: -1`은 컨테이너의 1px 밑줄을 <b>덮어</b> 선택된 칸만 두꺼운 선으로 잇는 값이다.
 */
const tabStyle = (active: boolean) =>
  ({
    flex: 1,
    padding: '9px 0',
    border: 0,
    borderBottom: active ? '2px solid var(--adaptiveBlue700, #4F6B4C)' : '2px solid transparent',
    marginBottom: -1,
    background: 'transparent',
    color: active ? '#3E5A3B' : 'var(--adaptiveGrey600, #8C877B)',
    fontSize: 14,
    fontWeight: 700,
    textAlign: 'center',
    cursor: 'pointer',
  }) as const;

/**
 * 게시판 껍데기 — 흰 카드 + 머리줄(왼쪽 개수 · 오른쪽 「글쓰기」). 목록을 이 카드로 감싸는 것이
 * 「딱 봐도 게시판」의 본체다(탭줄이 아니라 이쪽이 성격을 정한다 — 탭이 없는 낯선 책 화면도 게시판으로 읽힌다).
 *
 * <p>`onCompose`가 없으면 우상단이 빈다 — 서재에 없는 책엔 글을 남길 수 없다(손잡이 관례).
 */
function MarginBoard({ count, onCompose, children }: { count: number; onCompose?: () => void; children: ReactNode }) {
  return (
    <div
      style={{
        marginTop: 12,
        borderRadius: 12,
        border: '0.5px solid #E6DFCF',
        background: 'var(--adaptiveBackground, #FCFAF5)',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          padding: '10px 12px',
        }}
      >
        <Text typography="st12" color="grey600">
          글 {count}
        </Text>
        {onCompose !== undefined && (
          <Button display="inline" variant="weak" size="small" onClick={onCompose}>
            글쓰기
          </Button>
        )}
      </div>
      {children}
    </div>
  );
}

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
  expanded,
  onToggleExpand,
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
  /** 「내가 쓴 여백 / 모두의 여백」 탭 줄 — 내 책일 때만 셸이 넘긴다. */
  tabs?: ReactNode;
  expanded?: ReadonlySet<number>;
  onToggleExpand?: (id: number) => void;
  timerStopped?: boolean;
}) {
  const { book, myBookId, totalCount, entries } = data;

  return (
    <Screen title="여백" onBack={onBack}>
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

      <ErrorMessage message={error} />

      {/* 글쓰기 손잡이를 <b>아예 안 준다</b> — 「모두의 여백」은 조회 전용이다(2026-08-22 사용자 결정).
          여러 사람의 글이 섞인 목록에 글쓰기를 두면 「그 목록에 바로 쓴다」로 읽히는데, 실제로는 내 여백에
          남고 「올리기」를 켜야 여기 실린다. 문이 둘이면 그 차이를 화면이 설명할 자리가 없다.
          프롭 자체를 없앤 것이 게이트다 — 조건으로 거르면 다음 사람이 조건만 지우고 되살릴 수 있다. */}
      <MarginBoard count={totalCount}>
        {entries.length === 0 ? (
          <Text
            typography="st12"
            color="grey600"
            style={{ display: 'block', padding: '34px 16px', textAlign: 'center', wordBreak: 'keep-all' }}
          >
            아직 올라온 글이 없어요. 이 책을 읽은 누군가가 올리면 여기에 쌓여요.
          </Text>
        ) : (
          entries.map((e) => (
            <MarginCard
              key={e.id}
              entry={e}
              now={now}
              author={{ loginId: e.authorLoginId, nickname: e.authorNickname }}
              expanded={expanded?.has(e.id)}
              onOpenAuthor={onOpenProfile}
              onToggleLike={onToggleLike}
              onShowLikers={onShowLikers}
              onToggleExpand={onToggleExpand}
            />
          ))
        )}
      </MarginBoard>

      {/* 안 가진 책에는 「담기」 안내만 둔다(v1) — 낯선 책에 닿는 유일한 길이 검색이라, 뒤로 한 번
          가면 담기 버튼이 거기 있다. 화면 안에서 끝내는 전용 경로는 진입점이 늘어날 때 붙인다. */}
      {myBookId === null && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12, wordBreak: 'keep-all' }}>
          내 서재에 담으면 여기에 글을 남길 수 있어요.
        </Text>
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
  /** 펼쳐 둔 글 — 남의 글만 있는 화면이라 ⋯ 는 없고 이 상태만 든다. */
  const [expanded, setExpanded] = useState<ReadonlySet<number>>(new Set());

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  const likes = useMarginLikes(fail, onError);

  const toggleExpand = (id: number) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const load = useCallback(() => {
    setError(null); // 재시도가 성공했는데 지난 실패 문구가 남지 않게
    fetchBookMarginAll(isbn13).then(setData).catch(fail);
  }, [isbn13, fail]);

  useEffect(load, [load]);

  if (data === null) {
    return (
      <Screen title="여백" onBack={onBack}>
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
        expanded={expanded}
        onToggleExpand={toggleExpand}
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
  error,
  tabs,
  expanded,
  onCompose,
  onToggleLike,
  onShowLikers,
  onOpenMenu,
  onToggleExpand,
  onBack,
  timerStopped = false,
}: {
  loginId: string;
  margin: MarginResponse;
  /** 상대 시각의 기준 — 밖에서 받아야 테스트가 결정론이 된다. */
  now: number;
  error: string | null;
  onCompose: () => void;
  onToggleLike: (entry: MarginEntry) => void;
  onShowLikers: (entry: MarginEntry) => void;
  /** 행의 ⋯ — 내 글에만 넘어간다(남의 글은 서버가 404로 거절한다). 없으면 ⋯ 자체가 안 그려진다. */
  onOpenMenu?: (entry: MarginEntry) => void;
  /** 펼쳐 둔 글 id — 상태는 컨테이너가 든다. */
  expanded?: ReadonlySet<number>;
  onToggleExpand?: (id: number) => void;
  /** 「내가 쓴 여백 / 모두의 여백」 탭 줄 — 내 책이고 isbn13이 있을 때만 셸이 넘긴다. */
  tabs?: ReactNode;
  onBack: () => void;
  /** 여기 들어오느라 측정을 끝냈는가 — {@link TIMER_STOPPED_NOTICE}를 여는 스위치다. */
  timerStopped?: boolean;
}) {
  const { book, ownerNickname, self, entries } = margin;
  /**
   * 머리글 둘째 줄 — 주인 이름은 <b>남의 여백에서만</b> 선다. 내 여백은 탭줄이 서는 자리라 주인이
   * 언제나 나여서 이름이 잉여이지만, 남의 여백은 탭이 없어 이 줄이 「누구의 여백인가」의 유일한 좌표다.
   */
  const subtitle = [book.author, self ? null : `${ownerNickname} @${loginId}`].filter((s) => s !== null).join(' · ');

  return (
    <Screen title="여백" onBack={onBack}>
      {timerStopped && <TimerStoppedNotice />}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <BookCover url={book.coverUrl} title={book.title} width={48} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text typography="st11" style={{ display: 'block', wordBreak: 'keep-all' }}>
            {book.title}
          </Text>
          {subtitle !== '' && (
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 2 }}>
              {subtitle}
            </Text>
          )}
        </div>
      </div>

      {tabs}

      {/* 내 비공개 책이면 무엇이 안 새는지 여기서 말한다 — 공개 책엔 새로 알릴 것이 없어 적지 않는다. */}
      {self && book.isPublic === false && (
        <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 12, wordBreak: 'keep-all' }}>
          {visibilityNotice(book.isPublic)}
        </Text>
      )}

      <ErrorMessage message={error} />

      <MarginBoard count={entries.length} onCompose={self ? onCompose : undefined}>
        {entries.length === 0 ? (
          <Text
            typography="st12"
            color="grey600"
            style={{ display: 'block', padding: '34px 16px', textAlign: 'center', wordBreak: 'keep-all' }}
          >
            {/* 남의 여백이 비면 그냥 비었다고 말한다 — 예전의 「팔로우하면 볼 수 있어요」는 2026-08-22에
                걷었다. 「모두의 여백」에서 그 사람 글을 읽고 넘어온 사람에게 "팔로우해야 읽을 수 있다"고
                말하는 자리였다(팔로우는 이제 열람 권한이 아니다). */}
            {self
              ? '아직 남긴 글이 없어요. 읽다가 마음에 걸린 문장을 남겨 보세요.'
              : '아직 남긴 글이 없어요.'}
          </Text>
        ) : (
          entries.map((e) => (
            <MarginCard
              key={e.id}
              entry={e}
              now={now}
              expanded={expanded?.has(e.id)}
              onToggleLike={onToggleLike}
              onShowLikers={onShowLikers}
              onOpenMenu={self ? onOpenMenu : undefined}
              onToggleExpand={onToggleExpand}
            />
          ))
        )}
      </MarginBoard>
    </Screen>
  );
}

/**
 * 글 한 장 — <b>게시판 행</b>이다(2026-08-22 개편). 옛 모습은 팔레트 6색이 통째로 배경인 카드였는데,
 * 목록으로 쌓이면 색 덩어리가 줄줄이 늘어서 「글이 몇 개인가」조차 안 읽혔다. 색은 <b>왼쪽 3px 막대</b>로
 * 남는다 — 쓸 때 고른 값이라 통째로 버리면 작성 화면의 색 선택이 무의미해진다.
 *
 * <p>행의 <b>동작</b>(올리기·내리기·지우기)은 여기 없다 — ⋯ 로 여는 {@link MarginMenuSheet}가 진다.
 * 그래서 옛 프롭 여섯(`self`·`busy`·`confirming`·`onConfirmDelete`·`onDelete`·`onToggleShare`)이 통째로
 * 빠졌다: 게시판 행이 옛 카드보다 <b>단순해졌다</b>.
 *
 * <p>서재의 인라인 여백 박스가 미리보기로 이 행을 그대로 재사용한다 — 손잡이를 하나도 안 넘기므로
 * 하트·⋯·「더보기」 없이 본문만 그려진다.
 */
export function MarginCard({
  entry,
  now,
  author,
  expanded,
  onToggleLike,
  onShowLikers,
  onOpenAuthor,
  onOpenMenu,
  onToggleExpand,
}: {
  entry: MarginEntry;
  now: number;
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
   * 행 오른쪽 끝 ⋯ — <b>있으면 버튼, 없으면 ⋯ 자체가 없다</b>({@link onToggleLike} 관례). 내 글에만
   * 넘긴다: 남의 글은 내가 올리거나 지울 수 없다(서버도 404로 거절한다).
   *
   * <p><b>「모두의 여백」 칩의 게이트이기도 하다.</b> 칩은 행마다 켜짐/꺼짐이 갈리는 목록에서만 뜻이
   * 있는데, 그런 목록은 곧 내가 관리할 수 있는 목록이다 — 책축 목록은 전부 올라간 글이라 모든 행에
   * 같은 딱지가 붙어 정보량이 0이 된다.
   */
  onOpenMenu?: (entry: MarginEntry) => void;
  /** 본문이 펼쳐졌는가 — 상태는 밖에서 받는다(정적 렌더 하니스가 두 상태에 닿는 유일한 길). */
  expanded?: boolean;
  /** 「더보기」 손잡이 — 없으면 접히기만 한다(서재 미리보기는 박스의 「전체 보기」가 출구다). */
  onToggleExpand?: (id: number) => void;
}) {
  const bg = palette(entry.bgCode);
  const shared = entry.shared === true;
  const foldable = needsFold(entry.text);
  const clamped = foldable && expanded !== true;

  return (
    <div style={{ display: 'flex', gap: 9, padding: '11px 12px', borderTop: '1px solid #EFE8D9' }}>
      {/* 팔레트 색이 사는 유일한 자리 — 배경으로 깔면 목록이 색 덩어리가 되고, 아예 버리면 작성 화면의
          색 선택이 뜻을 잃는다. 3px 막대가 그 사이의 값이다. */}
      <div style={{ flex: '0 0 auto', width: 3, background: bg.background }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        {/* 작성자가 행 머리에 선다 — 책축 목록에서 「누가 썼나」는 본문보다 먼저 읽히는 정보다. */}
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
              style={{ ...authorLine, border: 'none', background: 'transparent', cursor: 'pointer' }}
            >
              {author.nickname} @{author.loginId}
            </button>
          ))}
        {/* 인용은 主가 아니라 從이다 — 작게·옅게 두고 세로선으로만 가른다. 크게 뽑으면 행의 주인공이
            남의 문장이 되어 「내 독서 기록」이 명언 카드가 된다. 세리프로 가르지 않는 이유: 폰에 한글
            세리프가 없어 `serif`가 안드로이드는 명조·iOS는 고딕으로 갈린다(폰마다 다른 화면). */}
        {entry.quote !== null && (
          <blockquote
            style={{
              margin: '0 0 5px',
              paddingLeft: 7,
              borderLeft: '2px solid #DCD4C2',
              color: ROW_SUB,
              fontSize: 13,
              lineHeight: 1.55,
              whiteSpace: 'pre-wrap',
              wordBreak: 'keep-all',
              // 장식 — 여백은 「연필로 적어 둔 것」이라 손글씨로 남는다(기능 글자는 고운돋움).
              // 본문이 손글씨이던 시절엔 상속으로 그랬고, 축이 뒤집힌 뒤로는 여기서 명시한다.
              ...HANDWRITING,
            }}
          >
            {entry.quote}
          </blockquote>
        )}
        <p
          style={{
            margin: 0,
            color: ROW_TEXT,
            fontSize: 15,
            lineHeight: 1.55,
            whiteSpace: 'pre-wrap',
            wordBreak: 'keep-all',
            ...HANDWRITING, // 위와 같은 이유 — 사용자가 손으로 적은 글이다
            ...(clamped
              ? ({
                  display: '-webkit-box',
                  WebkitLineClamp: CLAMP_LINES,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                } as const)
              : {}),
          }}
        >
          {entry.text}
        </p>
        {/* 펼침은 손잡이가 있을 때만 — 없으면 접힌 채로 둔다(자를 수는 있어도 못 펴는 자리가 있다). */}
        {onToggleExpand !== undefined && foldable && (
          <button type="button" onClick={() => onToggleExpand(entry.id)} style={moreLine}>
            {clamped ? '더보기' : '접기'}
          </button>
        )}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 7, fontSize: 11, color: ROW_SUB }}>
          <span>{relativeTime(entry.createdAt, now)}</span>
          {/* 칩은 이제 <b>상태 표시 전용</b>이다 — 켜고 끄는 일은 ⋯ 시트로 옮겼다(2026-08-22). */}
          {shared && onOpenMenu !== undefined && <span style={sharedChip}>모두의 여백</span>}
          <span style={{ flex: 1, minWidth: 0 }} />
          {/* 하트는 누르기/취소만 진다 — 개수를 같은 버튼에 넣으면 명단을 보려다 좋아요가 눌린다. */}
          {onToggleLike !== undefined && (
            <button
              type="button"
              aria-label={entry.liked ? '좋아요 취소' : '좋아요'}
              aria-pressed={entry.liked}
              onClick={() => onToggleLike(entry)}
              style={{ ...rowGhost(ROW_SUB), display: 'inline-flex', alignItems: 'center' }}
            >
              <Heart filled={entry.liked} />
            </button>
          )}
          {onOpenMenu !== undefined && (
            <button
              type="button"
              aria-label="이 글 관리"
              onClick={() => onOpenMenu(entry)}
              style={{ ...rowGhost(ROW_TEXT), fontSize: 17, lineHeight: 1 }}
            >
              ⋯
            </button>
          )}
        </div>

        {/* 개수는 데이터라 손잡이와 무관하게 보이고(주인도 남이 눌러 준 걸 안다), 여는 손잡이만 조건부다.
            0은 줄 자체를 안 만든다 — 빈 상태를 숫자로 박제하면 「아무도 안 눌렀다」가 행마다 반복된다. */}
        {entry.likeCount > 0 &&
          (onShowLikers === undefined ? (
            <p style={likesLine(ROW_SUB)}>좋아요 {entry.likeCount}명</p>
          ) : (
            <button
              type="button"
              aria-label={`좋아요 ${entry.likeCount}명 보기`}
              onClick={() => onShowLikers(entry)}
              style={{ ...likesLine(ROW_SUB), border: 'none', background: 'transparent', cursor: 'pointer' }}
            >
              좋아요 {entry.likeCount}명
            </button>
          ))}
      </div>
    </div>
  );
}

/**
 * 이 글이 세 줄을 넘기는가 — 접기와 「더보기」가 <b>같은 판정</b>을 쓴다(둘이 갈리면 펼 수 없는 접힘이나
 * 아무것도 안 하는 손잡이가 생긴다).
 *
 * <p>축이 <b>둘</b>인 이유: 본문은 `pre-wrap`이라 줄바꿈이 그대로 산다. 글자 수만 세면 「오늘 읽은 곳 /
 * 인상 깊은 대목 / 내일 이어서」처럼 <b>짧지만 여러 문단</b>인 글이 30자짜리로 계산돼 안 접히고, 게시판
 * 행이 7줄로 늘어난다. 문단을 나눠 쓰는 것은 여백 글에서 예외가 아니라 기본이다.
 *
 * <p>넘침 <b>실측</b>(`scrollHeight`)이 정확하지만 정적 렌더 하니스에서 안 돌아 테스트가 통째로
 * 불가능해진다 — 이 둘은 결정론이라 계측이 붙는다.
 *
 * ponytail: 휴리스틱 — 라틴 문자 비중이 높거나 어절이 유난히 긴 글에선 글자 수 축이 어긋난다. 눈에 띄면
 * `ResizeObserver` 실측으로 올린다(그때는 이 판정을 컨테이너로 올려 테스트를 유지한다).
 */
const needsFold = (text: string): boolean => text.length > CLAMP_CHARS || text.split('\n').length > CLAMP_LINES;

/**
 * 접는 글자 수 임계 — 목 모드 실측이다(390×844, 15px/line-height 23.25px, `wordBreak: keep-all`):
 * 98자 한글 문단이 <b>4줄</b>이었으므로 ≈ 24.5자/줄, 3줄 ≈ 73자. 여유 2자를 더해 75.
 *
 * <p>설계가 처음 잡았던 90은 실측 전 추정치(28~30자/줄)였고 <b>틀렸다</b> — 그 값이면 74~90자 글이
 * 4줄인 채로 안 접힌다.
 *
 * <p>여유를 <b>위쪽</b>으로 두는 이유: 두 오차의 무게가 다르다. 4줄이 안 접히면 행이 조금 길어질 뿐이지만,
 * 3줄 글에 「더보기」가 붙으면 눌러도 아무것도 안 변하는 <b>가짜 손잡이</b>가 된다.
 */
const CLAMP_CHARS = 75;

/** 접기 후 남기는 줄 수 — `WebkitLineClamp` 값과 같아야 한다(다르면 판정과 그림이 어긋난다). */
const CLAMP_LINES = 3;

/** 행의 본문 색 — 배경이 흰 게시판이라 팔레트 색을 상속하지 않는다(막대만 색을 진다). */
const ROW_TEXT = 'var(--adaptiveGrey900, #2C2A24)';
const ROW_SUB = 'var(--adaptiveGrey600, #8A8578)';

/**
 * 행 푸터의 아이콘 버튼 — <b>테두리가 없다</b>. 옛 ghost 버튼은 1px 테두리를 둘렀는데, 색 카드 위에서는
 * 옅은 윤곽이던 것이 흰 게시판 위에서는 상자로 읽혀 하트 하나가 행을 지배했다(그래서 그 스타일은
 * 호출처가 사라져 함께 지웠다). 손가락 자리는 테두리가 아니라 padding이 만든다.
 */
const rowGhost = (color: string) =>
  ({
    flex: '0 0 auto',
    padding: '4px 6px',
    border: 'none',
    background: 'transparent',
    color,
    fontSize: 13,
    cursor: 'pointer',
  }) as const;

/** 「더보기」/「접기」 — 본문 바로 아래 왼쪽 정렬. 세로 여백이 손가락 자리를 만든다. */
const moreLine = {
  display: 'block',
  width: 'auto',
  margin: '4px 0 0',
  padding: '2px 0',
  border: 'none',
  background: 'transparent',
  color: 'var(--adaptiveBlue700, #4F6B4C)',
  fontSize: 12,
  fontWeight: 700,
  textAlign: 'left',
  cursor: 'pointer',
} as const;

/** 「모두의 여백」 딱지 — 눌리지 않는다(동작은 ⋯ 시트가 진다). */
const sharedChip = {
  flex: '0 0 auto',
  padding: '1px 7px',
  borderRadius: 999,
  background: 'var(--adaptiveBlue50, #E7EEE2)',
  color: 'var(--adaptiveBlue700, #4F6B4C)',
} as const;

/**
 * 글 관리 시트 — 행의 ⋯ 가 여는 자리. 「올리기/내리기」와 「지우기」 <b>둘</b>이 들어가서 ⋯ 가 값을 한다
 * (지우기 하나뿐이면 한 겹 더 누르게 만든 손해였다).
 *
 * <p>자체 구현 {@link Sheet}를 쓴다 — TDS `BottomSheet`는 포털이라 정적 렌더에서 마크업이 통째로 빈다.
 * ⚠️ 진입 직후 자동으로 뜨지 않는다(T-183): 여는 것은 언제나 사용자의 ⋯ 탭이다.
 */
export function MarginMenuSheet({
  entry,
  canShare,
  confirming,
  busy,
  onShare,
  onConfirmDelete,
  onDelete,
  onClose,
}: {
  entry: MarginEntry;
  /** 책축 좌표(isbn13)가 있는가 — 없으면 올릴 자리 자체가 없어 그 줄을 안 그린다. */
  canShare: boolean;
  confirming: boolean;
  busy: boolean;
  onShare: (entry: MarginEntry) => void;
  onConfirmDelete: (id: number | null) => void;
  onDelete: (id: number) => void;
  onClose: () => void;
}) {
  const shared = entry.shared === true;

  return (
    <Sheet title="글 관리" onClose={onClose}>
      {canShare && (
        <button
          type="button"
          aria-pressed={shared}
          disabled={busy}
          onClick={() => onShare(entry)}
          style={menuRow(false)}
        >
          {shared ? '모두의 여백에서 내리기' : '모두의 여백에 올리기'}
        </button>
      )}
      {confirming ? (
        <>
          <p style={{ margin: '10px 2px 6px', color: ROW_SUB, fontSize: 13 }}>이 글을 지울까요?</p>
          <button type="button" disabled={busy} onClick={() => onDelete(entry.id)} style={menuRow(true)}>
            정말 지우기
          </button>
          <button type="button" disabled={busy} onClick={() => onConfirmDelete(null)} style={menuRow(false)}>
            취소
          </button>
        </>
      ) : (
        <button type="button" disabled={busy} onClick={() => onConfirmDelete(entry.id)} style={menuRow(true)}>
          지우기
        </button>
      )}
    </Sheet>
  );
}

/** 시트의 한 줄 — 손가락 높이(44px)를 쓰는 전체폭 버튼. 되돌릴 수 없는 줄만 붉다. */
const menuRow = (danger: boolean) =>
  ({
    display: 'block',
    width: '100%',
    height: 44,
    padding: '0 4px',
    border: 'none',
    background: 'transparent',
    color: danger ? '#A32D2D' : ROW_TEXT,
    fontSize: 15,
    fontWeight: 700,
    textAlign: 'left',
    cursor: 'pointer',
  }) as const;

/** 작성자 줄 — 게시판 행의 머리. 세이지로 눌러 본문보다 뒤로 물린다(이름이 본문을 이기면 안 된다). */
const authorLine = {
  display: 'block',
  width: 'auto',
  margin: '0 0 4px',
  padding: 0,
  color: 'var(--adaptiveBlue700, #4F6B4C)',
  fontSize: 12,
  fontWeight: 700,
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

      {/* 공개 스위치는 배경 팔레트 아래·버튼 위다 — 쓰기가 끝난 뒤 마지막으로 정하는 값이고,
          체크를 카드 안에 넣으면 여섯 배경마다 대비를 다시 맞춰야 한다.

          문구는 목록의 ⋯ 시트와 <b>같은 말</b>이어야 한다(2026-08-22): 쓸 때 「함께 걸기」, 고칠 때
          「모두의 여백에 올리기」로 갈리면 같은 값이라는 것이 안 읽힌다. 명사는 「모두의 여백」 하나. */}
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
            모두의 여백에 올리기
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
    // ⚠️ 손글씨를 **명시**한다. 이 두 칸은 「쓰는 동안 보이는 것이 곧 카드」인 미리보기라(위 주석),
    //    저장 뒤 `MarginCard`가 손글씨로 그리는데 여기만 기능 서체면 그 계약이 깨진다.
    //    한때 `fontFamily: 'inherit'`로 body를 따랐고 그때는 body가 손글씨라 우연히 맞았다 —
    //    서체 축이 뒤집히며(기능=고운돋움) 그 우연이 사라진 자리다.
    ...HANDWRITING,
    lineHeight: 1.6,
    resize: 'vertical',
  }) as const;

