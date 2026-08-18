import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { FollowListType, MarginBook, UserRow } from '../api';
import {
  changeHandle,
  createHandle,
  fetchFollowList,
  searchUsers,
  validateHandleChange,
  validateHandleFormat,
} from '../api';
import { useBackClose } from '../back';
import { ErrorMessage, Loading, Screen, Sheet } from '../ui';
import { Profile } from './Profile';
import { BookMargin, StoryComposer } from './Story';

/** 열린 여백 — 「누구의 + 어느 책」 두 축이 곧 서버 계약이다(홈 소식 점프도 이 모양으로 온다). */
export interface MarginTarget {
  loginId: string;
  bookId: number;
}

/**
 * 책방 탭 — 탭을 누르면 곧장 <b>내 책방</b>이다. 옛 소셜 중간 화면(「내 책방 보기」 버튼 · 팔로잉/팔로워
 * 토글 목록 · 차단 목록)은 사라졌다: 내 책방까지 두 탭이었고, 책방에 이미 있던 팔로워/팔로잉 카운트는
 * 눌러도 아무 일이 없어 "누가 팔로우하는지" 보려면 다시 이 화면으로 돌아와야 했다.
 *
 * <p>그래서 이건 <b>얇은 셸</b>이다 — 본문은 {@link Profile}이 그리고, 여기서는 전체 화면 전환(여백·
 * 글 남기기·남의 책방)과 시트 셋(친구 찾기·팔로우 목록·핸들 만들기)의 열림만 든다. 시트가 스스로
 * fetch하지 않고 상태를 셸이 드는 것은 하니스 사정이기도 하다: 정적 렌더가 시트 안 분기에 못 닿는다.
 *
 * <p>여백을 열면 {@link Profile}이 언마운트됐다가 돌아올 때 다시 받는다 — 방금 남긴 글로 격자 발광이
 * 바뀌는 것이 그 재조회에 그냥 딸려 온다(별도 갱신 배선이 필요 없다).
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
  /** 열린 남의 책방 — 검색 결과·팔로우 목록에서 사람을 고르면 여기로 온다. */
  const [open, setOpen] = useState<string | null>(null);
  /** 열린 여백 — 이 셸 안의 격자에서 연 것만 여기 담긴다(홈·서재에서 여는 여백은 App이 전체 화면으로 든다). */
  const [margin, setMargin] = useState<MarginTarget | null>(null);
  /** 글을 남기는 중인 책 — 여백 화면이 이미 받아 둔 라벨을 그대로 물려준다(다시 조회하지 않는다). */
  const [composing, setComposing] = useState<MarginBook | null>(null);
  /** 여백을 다시 받게 하는 표식 — 글을 남기거나 지운 뒤 `BookMargin`을 새 key로 재마운트한다. */
  const [marginEpoch, setMarginEpoch] = useState(0);
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
   * 하드웨어 뒤로가기 — 열린 서브뷰를 하나씩 닫는다(중첩이면 최상단만). 이게 없으면 여백 뷰어에서
   * 누른 back이 미니앱 자체를 종료시킨다. 스택은 등록 순서가 아니라 **열린 순서**로 쌓이므로, 여섯 개로
   * 늘어도 중첩 규칙은 그대로다.
   */
  useBackClose(creatingHandle, () => setCreatingHandle(false));
  useBackClose(composing !== null, () => setComposing(null));
  useBackClose(margin !== null, () => setMargin(null));
  useBackClose(searchOpen, closeSearch);
  useBackClose(followList !== null, closeFollowList);
  useBackClose(open !== null, () => setOpen(null));

  if (composing !== null) {
    return (
      <StoryComposer
        book={composing}
        onDone={() => {
          setComposing(null);
          setMarginEpoch((n) => n + 1); // 방금 남긴 글이 목록 맨 위에 바로 보여야 한다
        }}
        onCancel={() => setComposing(null)}
        onError={onError}
      />
    );
  }

  if (margin !== null) {
    return (
      <BookMargin
        key={marginEpoch}
        loginId={margin.loginId}
        bookId={margin.bookId}
        onBack={() => setMargin(null)}
        onCompose={setComposing}
        onError={onError}
      />
    );
  }

  // 남의 책방 — header도 카운트 핸들러도 주지 않는다(서버 follow-list는 본인 것만 준다). 지금과 동일한 화면.
  if (open !== null) {
    return (
      <Profile
        loginId={open}
        onBack={() => setOpen(null)}
        onError={onError}
        onOpenMargin={(bookId) => setMargin({ loginId: open, bookId })}
      />
    );
  }

  const search = () => {
    setBusy(true);
    setError(null);
    searchUsers(query.trim())
      .then((page) => setResults(page.results))
      .catch(fail)
      .finally(() => setBusy(false));
  };

  // 검색은 화면 제목보다 위에 얹힌다 — 책방을 그리는 건 Profile이라 `above` 슬롯으로 건넨다.
  const header = <BookshopHeader onSearch={() => setSearchOpen(true)} />;

  return (
    <>
      {handle === null ? (
        /*
         * 핸들이 없으면 내 책방 자체가 없다 — 서버 소셜 API가 대상을 loginId로만 찾으므로 자기 책방조차
         * 열리지 않는다(설계 §5-1). 예전엔 "웹에서 아이디를 정하라"고 했지만 토스로 시작한 계정은
         * 비밀번호가 없어 웹 로그인 자체가 불가능했다 — 실행 불가능한 죽은 안내였다. 그래서 여기서 만든다.
         * 검색은 그대로 준다: 남의 책방 구경은 핸들 없이도 된다.
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
        <Profile
          loginId={handle}
          onError={onError}
          header={header}
          onOpenFollowList={setFollowList}
          onOpenMargin={(bookId) => setMargin({ loginId: handle, bookId })}
        />
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

/** 진입바 높이 — 손가락 최소치(44px)를 정확히 채운다. */
const SEARCH_BAR_HEIGHT = 44;

/**
 * 책방 상단 도구 — <b>전폭 검색바 하나</b>다. 왼쪽에 있던 여백 스트립은 그 전에 사라졌고(2026-08-16:
 * 「새 글」 신호가 사람 단위(링) → <b>책 단위(격자 발광)</b>), 그러자 남은 아이콘이 <b>오른쪽 끝에
 * 정렬됐는데 왼쪽은 빈</b> 줄이 됐다 — 짝을 잃은 `justify-content: flex-end`가 이질감의 정체였다.
 * 전폭은 한 줄을 다 쓰므로 그 정렬 문제 자체가 없다.
 *
 * <p>2026-08-16엔 정반대로 판단했었다 — 전폭 알약이 "그 줄만큼 공개 책을 아래로 민다"고 보고 아이콘으로
 * 줄였다. 그 논거는 지금 형태엔 안 맞는다: 캡션이 붙으면서 <b>아이콘 쪽이 세로로 더 먹었다</b>
 * (원 56 + 여백 4 + 캡션 ≈ 80px 대 바 44px). 되돌리는 쪽이 오히려 책에 자리를 돌려준다.
 *
 * <p><b>입력창이 아니다</b> — 생김새만 검색바고 누르면 「친구 찾기」 시트가 열린다. 인라인 form이면
 * 결과 패널이 내 책방 본문을 통째로 갈아끼워야 하는데, 그게 애초에 검색을 시트로 뺀 이유다.
 * 문구가 본문에 있어 "무엇을 아이디로 찾는지"가 눌러 보기 전에 선다.
 *
 * <p>⚠️ <b>돋보기 이모지를 두지 않는다</b>(2026-08-18) — 흔한 기본 이모지는 「AI가 만든 화면」이라는
 * 인상을 주고 사람들은 그 인상을 불쾌하게 여긴다. 문구가 이미 의미를 다 말하므로 아이콘 자체가 없어도
 * 잃는 게 없어, 가운데 정렬로 문구만 세운다. 규칙 전체는 `no-emoji.test.ts`가 소스째 지킨다.
 *
 * <p>셸의 지역 변수가 아니라 컴포넌트로 남긴 것은 계측 때문이다 — 상단 도구의 유무·모양을 셸의 네 분기
 * (핸들 유무 × 로딩)와 무관하게 한 곳에서 잰다. 남의 책방은 이걸 아예 안 받는다(셸이 안 넘긴다).
 */
export function BookshopHeader({ onSearch }: { onSearch: () => void }) {
  return (
    <button
      type="button"
      aria-label="아이디로 친구 찾기"
      onClick={onSearch}
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: '100%',
        height: SEARCH_BAR_HEIGHT,
        marginBottom: 16,
        padding: '0 14px',
        borderRadius: 10,
        // 캔버스가 종이톤 크림(#F3EEE4)이라 카드지 fill + 테두리라야 눌리는 자리로 뜬다.
        background: 'var(--adaptiveGrey100, #FCFAF5)',
        border: '1px solid var(--adaptiveGrey200, #E4DDD0)',
        color: 'var(--adaptiveGrey600, #6F6A5E)',
        fontSize: 14,
        cursor: 'pointer',
      }}
    >
      아이디로 친구 찾기
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
