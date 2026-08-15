import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { AuthorStories, FollowListType, StoryFeedResponse, UserRow } from '../api';
import {
  changeHandle,
  createHandle,
  fetchFollowList,
  fetchStoryFeed,
  searchUsers,
  validateHandleChange,
  validateHandleFormat,
} from '../api';
import { useBackClose } from '../back';
import { ErrorMessage, Loading, Screen, Sheet } from '../ui';
import { Profile } from './Profile';
import { StoryComposer, StoryStrip, StoryViewer } from './Story';

/**
 * 책방 탭 — 탭을 누르면 곧장 <b>내 책방</b>이다. 옛 소셜 중간 화면(「내 책방 보기」 버튼 · 팔로잉/팔로워
 * 토글 목록 · 차단 목록)은 사라졌다: 내 책방까지 두 탭이었고, 책방에 이미 있던 팔로워/팔로잉 카운트는
 * 눌러도 아무 일이 없어 "누가 팔로우하는지" 보려면 다시 이 화면으로 돌아와야 했다.
 *
 * <p>그래서 이건 <b>얇은 셸</b>이다 — 본문은 {@link Profile}이 그리고, 여기서는 전체 화면 전환(스토리
 * 뷰어·작성기·남의 책방)과 시트 셋(친구 찾기·팔로우 목록·핸들 만들기)의 열림만 든다. 시트가 스스로
 * fetch하지 않고 상태를 셸이 드는 것은 하니스 사정이기도 하다: 정적 렌더가 시트 안 분기에 못 닿는다.
 */
export function Bookshop({
  myLoginId,
  onHandleCreated,
  onError,
}: {
  myLoginId: string | null;
  /** 핸들을 만들면 대시보드를 다시 받아야 한다 — 서버가 준 값이 진실이고, 다른 탭도 그 값을 본다. */
  onHandleCreated: () => void;
  onError: (error: Error) => void;
}) {
  // 서버가 준 정규화 핸들을 즉시 반영해 이 탭을 그 자리에서 내 책방으로 바꾼다(대시보드 재조회를 안 기다린다).
  const [handle, setHandle] = useState(myLoginId);
  const [creatingHandle, setCreatingHandle] = useState(false);
  /** 열린 남의 책방 — 검색 결과·팔로우 목록·스토리 뷰어에서 사람을 고르면 여기로 온다. */
  const [open, setOpen] = useState<string | null>(null);
  const [feed, setFeed] = useState<StoryFeedResponse | null>(null);
  const [viewing, setViewing] = useState<{ author: AuthorStories; mine: boolean } | null>(null);
  const [composing, setComposing] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserRow[] | null>(null);
  const [followList, setFollowList] = useState<FollowListType | null>(null);
  const [followUsers, setFollowUsers] = useState<UserRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  /*
   * 셸이 받는 건 스토리 피드뿐이다 — 팔로우 목록은 시트가 열릴 때, 차단 목록은 설정으로 갔다.
   * 책방이 닫힐 때(open → null) 다시 받는 건 남긴다: 거기서 한 팔로우가 곧 피드 대상을 바꾼다.
   * 피드 실패는 스트립을 안 그리는 것으로 접는다(`StoryStrip`은 feed=null이면 null) — 스토리 하나 때문에
   * 책방 본문을 빨간 줄로 덮을 일이 아니다.
   */
  const loadFeed = useCallback(() => {
    fetchStoryFeed().then(setFeed).catch(fail);
  }, [fail]);

  useEffect(() => {
    if (open === null) loadFeed();
  }, [open, loadFeed]);

  /** 팔로우 목록은 시트가 열릴 때만 받는다. 실패해도 시트 안에서 다시 받을 길을 준다. */
  const loadFollowList = useCallback(
    (type: FollowListType) => {
      setFollowUsers(null);
      setError(null);
      fetchFollowList(type)
        .then((page) => setFollowUsers(page.users))
        .catch(fail);
    },
    [fail],
  );

  useEffect(() => {
    if (followList !== null) loadFollowList(followList);
  }, [followList, loadFollowList]);

  // 시트를 닫으면 그 안의 상태를 비운다 — 안 그러면 다음에 열 때 옛 결과·옛 목록이 한 프레임 번쩍인다.
  const closeSearch = () => {
    setSearchOpen(false);
    setQuery('');
    setResults(null);
  };

  const closeFollowList = () => {
    setFollowList(null);
    setFollowUsers(null);
  };

  /*
   * 하드웨어 뒤로가기 — 열린 서브뷰를 하나씩 닫는다(중첩이면 최상단만). 이게 없으면 스토리 뷰어에서
   * 누른 back이 미니앱 자체를 종료시킨다. 스택은 등록 순서가 아니라 **열린 순서**로 쌓이므로, 여섯 개로
   * 늘어도 중첩 규칙은 그대로다.
   */
  useBackClose(creatingHandle, () => setCreatingHandle(false));
  useBackClose(composing, () => setComposing(false));
  useBackClose(viewing !== null, () => setViewing(null));
  useBackClose(searchOpen, closeSearch);
  useBackClose(followList !== null, closeFollowList);
  useBackClose(open !== null, () => setOpen(null));

  if (composing) {
    return (
      <StoryComposer
        onDone={() => {
          setComposing(false);
          loadFeed(); // 방금 올린 스토리가 내 링에 바로 보여야 한다
        }}
        onCancel={() => setComposing(false)}
        onError={onError}
      />
    );
  }

  if (viewing !== null) {
    return (
      <StoryViewer
        author={viewing.author}
        mine={viewing.mine}
        onClose={() => setViewing(null)}
        onOpenProfile={(loginId) => {
          setViewing(null);
          setOpen(loginId);
        }}
        onDeleted={loadFeed}
        onError={onError}
      />
    );
  }

  // 남의 책방 — header도 카운트 핸들러도 주지 않는다(서버 follow-list는 본인 것만 준다). 지금과 동일한 화면.
  if (open !== null) {
    return <Profile loginId={open} onBack={() => setOpen(null)} onError={onError} />;
  }

  const search = () => {
    setBusy(true);
    setError(null);
    searchUsers(query.trim())
      .then((page) => setResults(page.results))
      .catch(fail)
      .finally(() => setBusy(false));
  };

  // 검색·스토리는 화면 제목보다 위에 얹힌다 — 책방을 그리는 건 Profile이라 `above` 슬롯으로 건넨다.
  const header = (
    <BookshopHeader
      feed={feed}
      onOpenStory={(author, mine) => setViewing({ author, mine })}
      onCompose={() => setComposing(true)}
      onSearch={() => setSearchOpen(true)}
    />
  );

  return (
    <>
      {handle === null ? (
        /*
         * 핸들이 없으면 내 책방 자체가 없다 — 서버 소셜 API가 대상을 loginId로만 찾으므로 자기 책방조차
         * 열리지 않는다(설계 §5-1). 예전엔 "웹에서 아이디를 정하라"고 했지만 토스로 시작한 계정은
         * 비밀번호가 없어 웹 로그인 자체가 불가능했다 — 실행 불가능한 죽은 안내였다. 그래서 여기서 만든다.
         * 검색·스토리는 그대로 준다: 남의 책방 구경은 핸들 없이도 된다.
         */
        <Screen title="책방" above={header}>
          <Text typography="st12" color="grey600" style={{ display: 'block' }}>
            @아이디를 만들면 친구가 나를 찾을 수 있고, 내 책방이 생겨요.
          </Text>
          <Button display="block" variant="weak" style={{ marginTop: 12 }} onClick={() => setCreatingHandle(true)}>
            아이디 만들기
          </Button>
          <ErrorMessage message={error} />
        </Screen>
      ) : (
        // onBack을 주지 않는다 — 탭 루트라 「돌아가기」가 갈 곳이 없다(출구는 플로팅 탭바).
        <Profile loginId={handle} onError={onError} header={header} onOpenFollowList={setFollowList} />
      )}

      {searchOpen && (
        <SearchSheet
          query={query}
          results={results}
          busy={busy}
          error={error}
          onQueryChange={setQuery}
          onSearch={search}
          // 닫으면서 여는 교체 경로 — `back.ts`의 엔트리 물려주기(규칙 ③)가 히스토리 깊이를 보존한다(T-166).
          onSelect={(loginId) => {
            closeSearch();
            setOpen(loginId);
          }}
          onClose={closeSearch}
        />
      )}

      {followList !== null && (
        <FollowListSheet
          type={followList}
          users={followUsers}
          error={error}
          onSelect={(loginId) => {
            closeFollowList();
            setOpen(loginId);
          }}
          onClose={closeFollowList}
          onRetry={() => loadFollowList(followList)}
        />
      )}

      {creatingHandle && (
        <HandleSheet
          onClose={() => setCreatingHandle(false)}
          onCreated={(loginId) => {
            setHandle(loginId); // 서버가 정규화한 값 — 이 탭이 그 자리에서 내 책방으로 바뀐다
            setCreatingHandle(false);
            onHandleCreated(); // 대시보드 재조회로 진실을 서버와 맞춘다
          }}
          onFail={fail}
        />
      )}
    </>
  );
}

/**
 * 책방 상단 도구 — <b>스토리 스트립 한 줄</b>이고, 그 오른쪽 끝에 검색 아이콘이 고정된다.
 * 스토리도 검색도 "사람"을 다루니 한 줄에 이웃해도 층위가 어긋나지 않는다.
 *
 * <p>전에는 검색이 <b>전폭 알약</b>으로 최상단 한 줄을 통째로 먹었다 — 그만큼 이 화면의 본체인 공개 책이
 * 아래로 밀렸다(인스타 배치로 상단을 접은 흐름의 마지막 조각). 토스 셸 헤더는 앱 소유라 인스타처럼
 * 헤더 우측에 얹을 수 없어, 화면 안에서 가장 가까운 줄을 그 자리로 삼았다.
 *
 * <p>스트립만 스크롤하고 아이콘은 자리를 지킨다 — 친구가 많아 스트립이 길어져도 검색이 밀려 나가지 않게.
 * 셸의 지역 변수가 아니라 컴포넌트로 꺼낸 것은 <b>순서를 계측하기 위해서</b>다: 스트립은 피드를 받기
 * 전(`feed === null`)에 아무것도 안 그리므로, 셸을 그대로 렌더하면 앞뒤를 잴 방법이 없다.
 */
export function BookshopHeader({
  feed,
  onOpenStory,
  onCompose,
  onSearch,
}: {
  feed: StoryFeedResponse | null;
  onOpenStory: (author: AuthorStories, mine: boolean) => void;
  onCompose: () => void;
  onSearch: () => void;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
      {/* 스트립이 없는 첫 렌더에도 이 칸이 폭을 지켜 아이콘이 왼쪽으로 흘러내리지 않는다. */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <StoryStrip feed={feed} onOpen={onOpenStory} onCompose={onCompose} />
      </div>
      <SearchEntryButton onOpen={onSearch} />
    </div>
  );
}

/** 스토리 링(`Story.tsx`의 `RING_SIZE`)과 같은 지름 — 두 원의 크기가 어긋나면 한 줄이 들쭉날쭉해진다. */
const SEARCH_ICON_SIZE = 56;

/**
 * 검색 진입 아이콘 — 스토리 링과 <b>같은 지름·같은 캡션 문법</b>의 원형 버튼. 실제 입력·결과는
 * 「친구 찾기」 시트가 맡는다(인라인 form이면 결과 패널이 내 책방 본문을 통째로 갈아끼워야 했다).
 *
 * <p>캡션 「친구 찾기」를 지우지 않는다 — 돋보기 하나만으로는 "무엇을 찾는지"가 안 서고, 옆이 사람 링이라
 * 책 검색으로도 읽힌다. 스크린리더용 이름은 옛 진입바 문구를 그대로 물려받아(`아이디로 친구 찾기`)
 * "아이디로 찾는다"는 유일한 사용법을 잃지 않는다.
 */
function SearchEntryButton({ onOpen }: { onOpen: () => void }) {
  return (
    <button
      type="button"
      aria-label="아이디로 친구 찾기"
      onClick={onOpen}
      style={{
        flex: '0 0 auto',
        // 스트립 링의 위쪽 여백(4)과 맞춰야 두 원이 한 줄에 나란히 선다.
        marginTop: 4,
        padding: '0 0 0 10px',
        // 스크롤되는 스트립과 고정된 이 칸의 경계 — 없으면 스트립의 마지막 링처럼 읽힌다.
        borderLeft: '1px solid var(--adaptiveGrey200, #E4DDD0)',
        borderTop: 'none',
        borderRight: 'none',
        borderBottom: 'none',
        background: 'transparent',
        cursor: 'pointer',
      }}
    >
      <span
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: SEARCH_ICON_SIZE,
          height: SEARCH_ICON_SIZE,
          borderRadius: '50%',
          fontSize: 20,
          background: 'var(--adaptiveGrey100, #FCFAF5)',
          border: '1px solid var(--adaptiveGrey200, #E4DDD0)',
        }}
      >
        🔍
      </span>
      <span style={{ display: 'block', marginTop: 6, fontSize: 11, color: 'var(--adaptiveGrey600, #6F6A5E)' }}>
        친구 찾기
      </span>
    </button>
  );
}

/**
 * 친구 찾기 시트 — 옛 소셜 화면의 인라인 검색을 그대로 옮겼다. 순수 표시다(상태는 셸이 든다).
 *
 * <p>「닫기」 버튼은 없앴다 — 시트 ✕ 가 그 일을 한다. 결과가 0건이어도 안내 문구가 서야 한다:
 * 서버는 두 글자 미만이면 <b>실패가 아니라 빈 결과</b>를 주므로(열거 방지), 그 문구가 유일한 안내다.
 */
export function SearchSheet({
  query,
  results,
  busy,
  error,
  onQueryChange,
  onSearch,
  onSelect,
  onClose,
}: {
  query: string;
  /** `null`이면 아직 안 찾았다 — 빈 배열(0건)과 구분해야 열자마자 "없어요"가 뜨지 않는다. */
  results: UserRow[] | null;
  busy: boolean;
  error: string | null;
  onQueryChange: (query: string) => void;
  onSearch: () => void;
  onSelect: (loginId: string) => void;
  onClose: () => void;
}) {
  return (
    <Sheet title="친구 찾기" onClose={onClose}>
      {/* 입력 하나짜리 form은 브라우저가 엔터(키보드 「완료」)를 곧 제출로 친다 — 버튼은 밖에 둔다. */}
      <form
        onSubmit={(e) => {
          e.preventDefault(); // 막지 않으면 페이지가 통째로 새로고침돼 미니앱이 처음으로 돌아간다
          if (!busy && query.trim() !== '') onSearch();
        }}
      >
        <TextField
          variant="box"
          label="아이디로 찾기"
          placeholder="아이디 입력"
          value={query}
          disabled={busy}
          onChange={(e) => onQueryChange(e.target.value)}
        />
      </form>

      {/* aria-label을 명시한다 — loading 중에는 라벨이 스피너로 바뀌어 이름 없는 버튼이 된다. */}
      <Button
        aria-label="검색"
        display="block"
        style={{ marginTop: 12 }}
        loading={busy}
        disabled={query.trim() === ''}
        onClick={onSearch}
      >
        검색
      </Button>

      <ErrorMessage message={error} />

      {results !== null && (
        <div style={{ marginTop: 20 }}>
          <UserList
            users={results}
            emptyMessage="그 아이디를 쓰는 사람이 없어요. 두 글자 이상으로 다시 찾아보세요."
            onSelect={onSelect}
          />
        </div>
      )}
    </Sheet>
  );
}

/**
 * 팔로워/팔로잉 시트 — 책방 헤더의 카운트를 누르면 열린다. 순수 표시다(목록은 셸이 받아 프롭으로 준다).
 *
 * <p>내부 fetch를 두지 않는 이유는 {@link Profile}의 `ArchiveSheet`와 같다: 정적 렌더 하니스가
 * 0명/N명 분기에 닿으려면 데이터가 프롭이어야 한다.
 */
export function FollowListSheet({
  type,
  users,
  error,
  onSelect,
  onClose,
  onRetry,
}: {
  type: FollowListType;
  /** `null`이면 아직 받는 중 — 빈 배열(0명)과 구분해야 "없어요"를 먼저 깜빡이지 않는다. */
  users: UserRow[] | null;
  error: string | null;
  onSelect: (loginId: string) => void;
  onClose: () => void;
  onRetry: () => void;
}) {
  return (
    <Sheet title={type === 'following' ? '팔로잉' : '팔로워'} onClose={onClose}>
      {/* 못 받았으면 그 자리에서 다시 받는다 — 실패가 곧 빈 시트(막다른 길)가 되지 않게. */}
      <ErrorMessage message={error} onRetry={onRetry} />
      {users === null ? (
        error === null && <Loading />
      ) : (
        <UserList
          users={users}
          emptyMessage={
            type === 'following'
              ? '아직 팔로우한 사람이 없어요. 「친구 찾기」로 아이디를 검색해 책방을 구경해 보세요.'
              : '아직 나를 팔로우한 사람이 없어요.'
          }
          onSelect={onSelect}
        />
      )}
    </Sheet>
  );
}

/**
 * 핸들(@아이디) 만들기·바꾸기 시트 — 토스로 가입한 계정이 소셜에 발견되게 하는 유일한 경로이자,
 * 웹에 로그인할 수 없는 그 계정이 아이디를 바꾸는 유일한 경로.
 *
 * <p>형식만 그 자리에서 검사해 즉시 피드백하고({@link validateHandleFormat}), <b>예약어·중복은 서버가
 * 판정</b>한다 — 서버가 준 평문 메시지를 그대로 띄운다. 변경은 평생 1번뿐이라 누르기 전에 그 사실을 알린다
 * (되돌릴 수 없는 선택을 모르고 하지 않게).
 *
 * <p>변경을 별도 컴포넌트로 쪼개지 않은 이유: 입력·프리검증·에러 에코·제출 구조가 같아 60줄이 그대로
 * 복제된다. 다른 것은 문구·프리검증 기준·호출할 API 넷뿐이라 {@code change} 하나로 가른다.
 */
export function HandleSheet({
  onClose,
  onCreated,
  onFail,
  change = null,
}: {
  onClose: () => void;
  /** 서버가 정규화한(소문자) 핸들 — 클라이언트가 입력값을 그대로 믿지 않는다. */
  onCreated: (loginId: string) => void;
  onFail: (error: Error) => void;
  /** 지금 핸들을 주면 「바꾸기」 시트가 된다(제목·경고·프리검증·API가 함께 전환). `null`이면 만들기. */
  change?: string | null;
}) {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 빈 입력에까지 빨간 문구를 띄우진 않는다 — 아직 아무것도 안 쳤는데 혼내는 꼴이 된다.
  const formatError =
    value.trim() === ''
      ? null
      : change !== null
        ? validateHandleChange(value, change)
        : validateHandleFormat(value);
  const submit = () => {
    setBusy(true);
    setError(null);
    (change !== null ? changeHandle(value.trim()) : createHandle(value.trim()))
      .then((result) => onCreated(result.loginId))
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onFail(e);
        else setError(e.message); // 400/409 평문 — 문구가 곧 안내다
      })
      .finally(() => setBusy(false));
  };

  return (
    <Sheet title={change !== null ? '@아이디 바꾸기' : '@아이디 만들기'} onClose={onClose}>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 12 }}>
        영문·숫자·밑줄(_) 3~20자. 대문자는 소문자로 저장돼요.{' '}
        {change !== null ? (
          <b>아이디 변경은 평생 1번뿐이에요. 바꾸면 되돌릴 수 없고, 지금 아이디는 다시 쓸 수 없어요.</b>
        ) : (
          <b>바꾸는 건 평생 1번만 할 수 있어요.</b>
        )}
      </Text>

      {/* 입력 하나짜리 form — 키보드 「완료」가 곧 제출이다(검색 입력과 같은 이유로 버튼은 밖에 둔다). */}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy && value.trim() !== '' && formatError === null) submit();
        }}
      >
        <TextField
          variant="box"
          label="아이디"
          placeholder="아이디 입력"
          value={value}
          disabled={busy}
          onChange={(e) => setValue(e.target.value)}
        />
      </form>

      <ErrorMessage message={formatError ?? error} />

      <Button
        display="block"
        style={{ marginTop: 16 }}
        loading={busy}
        disabled={value.trim() === '' || formatError !== null}
        onClick={submit}
      >
        {change !== null ? '평생 1번, 바꾸기' : '만들기'}
      </Button>
    </Sheet>
  );
}

/** 사용자 목록 — 검색 결과·팔로우 목록이 같은 줄 모양을 쓴다(서버도 같은 행 DTO를 준다). */
export function UserList({
  users,
  emptyMessage,
  onSelect,
}: {
  users: UserRow[];
  emptyMessage: string;
  onSelect: (loginId: string) => void;
}) {
  if (users.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        {emptyMessage}
      </Text>
    );
  }

  return (
    <>
      {users.map((u) => (
        <button
          key={u.loginId}
          type="button"
          onClick={() => onSelect(u.loginId)}
          style={{
            display: 'block',
            width: '100%',
            padding: 16,
            marginBottom: 8,
            border: 'none',
            borderRadius: 12,
            background: 'var(--adaptiveGrey100, #FCFAF5)',
            textAlign: 'left',
            cursor: 'pointer',
          }}
        >
          <Text typography="st11" style={{ display: 'block' }}>
            {u.nickname}
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 4 }}>
            @{u.loginId} · 공개 책 {u.publicBookCount}권{u.following && ' · 팔로잉'}
          </Text>
        </button>
      ))}
    </>
  );
}
