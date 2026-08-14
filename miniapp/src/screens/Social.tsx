import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { AuthorStories, FollowListType, StoryFeedResponse, UserRow } from '../api';
import {
  createHandle,
  fetchBlocks,
  fetchFollowList,
  fetchStoryFeed,
  searchUsers,
  unblockUser,
  validateHandleFormat,
} from '../api';
import { useBackClose } from '../back';
import { ErrorMessage, Loading, Screen, Sheet, sectionStyle } from '../ui';
import { Profile } from './Profile';
import { StoryComposer, StoryStrip, StoryViewer } from './Story';

/**
 * 소셜 탭 — 스토리 스트립 · 유저 검색 · 팔로우 목록 · 차단 목록, 그리고 남의 책방 진입.
 *
 * <p>책방에서 팔로우·차단이 일어나므로, 책방에서 돌아올 때마다 목록을 다시 받는다 —
 * 차단한 사람은 검색·목록에서 사라져야 하는데 캐시가 남으면 이미 없는 사람이 계속 보인다.
 * 스토리 피드도 같이 받는다(팔로우가 곧 피드 대상이라 한 쪽만 갱신하면 어긋난다).
 */
export function Social({
  myLoginId,
  onHandleCreated,
  onError,
}: {
  myLoginId: string | null;
  /** 핸들을 만들면 대시보드를 다시 받아야 한다 — 서버가 준 값이 진실이고, 다른 탭도 그 값을 본다. */
  onHandleCreated: () => void;
  onError: (error: Error) => void;
}) {
  // 서버가 준 정규화 핸들을 즉시 반영해 배너를 「내 책방 보기」로 바꾼다(대시보드 재조회를 기다리지 않는다).
  const [handle, setHandle] = useState(myLoginId);
  const [creatingHandle, setCreatingHandle] = useState(false);
  const [listType, setListType] = useState<FollowListType>('following');
  const [users, setUsers] = useState<UserRow[] | null>(null);
  const [blocked, setBlocked] = useState<UserRow[]>([]);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<UserRow[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [feed, setFeed] = useState<StoryFeedResponse | null>(null);
  const [viewing, setViewing] = useState<{ author: AuthorStories; mine: boolean } | null>(null);
  const [composing, setComposing] = useState(false);
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
    setError(null); // 다시 받는 김에 지난 실패 문구도 지운다 — 안 그러면 재시도가 성공해도 빨간 줄이 남는다
    fetchFollowList(listType)
      .then((page) => setUsers(page.users))
      .catch(fail);
    fetchBlocks()
      .then((page) => setBlocked(page.blocked))
      .catch(fail);
    fetchStoryFeed().then(setFeed).catch(fail);
  }, [listType, fail]);

  // 책방이 닫힐 때(open → null)도 다시 받는다 — 거기서 한 팔로우·차단이 목록에 반영돼야 한다.
  useEffect(() => {
    if (open === null) load();
  }, [open, load]);

  /*
   * 하드웨어 뒤로가기 — 열린 서브뷰를 하나씩 닫는다(중첩이면 최상단만). 이게 없으면 스토리 뷰어에서
   * 누른 back이 미니앱 자체를 종료시킨다. 스토리 뷰어·작성기의 열림 상태가 여기 있으므로 배선도 여기서 한다.
   */
  useBackClose(creatingHandle, () => setCreatingHandle(false));
  useBackClose(composing, () => setComposing(false));
  useBackClose(viewing !== null, () => setViewing(null));
  useBackClose(open !== null, () => setOpen(null));

  if (composing) {
    return (
      <StoryComposer
        onDone={() => {
          setComposing(false);
          load(); // 방금 올린 스토리가 내 링에 바로 보여야 한다
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
        onDeleted={load}
        onError={onError}
      />
    );
  }

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

  const unblock = (loginId: string) => {
    setBusy(true);
    setError(null);
    unblockUser(loginId)
      .then(load)
      .catch(fail)
      .finally(() => setBusy(false));
  };

  return (
    <Screen title="책방 둘러보기">
      <StoryStrip
        feed={feed}
        onOpen={(author, mine) => setViewing({ author, mine })}
        onCompose={() => setComposing(true)}
      />

      {/*
       * 입력 하나짜리 form은 브라우저가 엔터(키보드 「완료」)를 곧 제출로 친다 — 제출 버튼이 없어도 된다.
       * 버튼들을 form 밖에 두는 게 그래서 중요하다: 안에 넣으면 「닫기」까지 제출로 동작한다.
       */}
      <form
        onSubmit={(e) => {
          e.preventDefault(); // 막지 않으면 페이지가 통째로 새로고침돼 미니앱이 처음으로 돌아간다
          if (!busy && query.trim() !== '') search();
        }}
      >
        <TextField
          variant="box"
          label="아이디로 찾기"
          placeholder="예: goospel"
          value={query}
          disabled={busy}
          onChange={(e) => setQuery(e.target.value)}
        />
      </form>
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        {/* aria-label을 명시한다 — loading 중에는 라벨이 스피너로 바뀌어 이름 없는 버튼이 된다. */}
        <Button aria-label="검색" style={{ flex: 1 }} loading={busy} disabled={query.trim() === ''} onClick={search}>
          검색
        </Button>
        {results !== null && (
          <Button
            variant="weak"
            onClick={() => {
              setResults(null);
              setQuery('');
            }}
          >
            닫기
          </Button>
        )}
      </div>

      {/* 목록·피드를 아예 못 받았으면 그 자리에서 다시 받는다 — 검색·차단해제 실패는 되받을 게 없다. */}
      <ErrorMessage message={error} onRetry={users === null || feed === null ? load : undefined} />

      {results !== null ? (
        <div style={{ marginTop: 20 }}>
          <UserList
            users={results}
            emptyMessage="그 아이디를 쓰는 사람이 없어요. 두 글자 이상으로 다시 찾아보세요."
            onSelect={setOpen}
          />
        </div>
      ) : (
        <>
          <MyShelfEntry myLoginId={handle} onOpen={setOpen} onCreateHandle={() => setCreatingHandle(true)} />

          <section style={sectionStyle}>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
              {(['following', 'followers'] as const).map((type) => (
                <Button
                  key={type}
                  size="small"
                  variant={type === listType ? 'fill' : 'weak'}
                  onClick={() => {
                    setUsers(null);
                    setListType(type);
                  }}
                >
                  {type === 'following' ? '팔로잉' : '팔로워'}
                </Button>
              ))}
            </div>

            {users === null ? (
              error === null && <Loading />
            ) : (
              <UserList
                users={users}
                emptyMessage={
                  listType === 'following'
                    ? '아직 팔로우한 사람이 없어요. 위에서 아이디로 찾아 책방을 구경해 보세요.'
                    : '아직 나를 팔로우한 사람이 없어요.'
                }
                onSelect={setOpen}
              />
            )}
          </section>

          {blocked.length > 0 && (
            <section style={sectionStyle}>
              <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
                차단한 사람 {blocked.length}
              </Text>
              {blocked.map((u) => (
                <div key={u.loginId} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
                  <Text typography="st11" style={{ flex: 1 }}>
                    {u.nickname} @{u.loginId}
                  </Text>
                  <Button size="small" variant="weak" disabled={busy} onClick={() => unblock(u.loginId)}>
                    차단 해제
                  </Button>
                </div>
              ))}
            </section>
          )}
        </>
      )}

      {creatingHandle && (
        <HandleSheet
          onClose={() => setCreatingHandle(false)}
          onCreated={(loginId) => {
            setHandle(loginId); // 서버가 정규화한 값 — 배너가 그 자리에서 「내 책방 보기」로 바뀐다
            setCreatingHandle(false);
            onHandleCreated(); // 대시보드 재조회로 진실을 서버와 맞춘다
          }}
          onFail={fail}
        />
      )}
    </Screen>
  );
}

/**
 * 내 책방 진입 — 핸들(login_id)이 있어야만 열 수 있다(설계 §5-1).
 *
 * <p>서버 소셜 API는 대상을 loginId로만 찾으므로, 토스로 새로 가입한 계정(login_id=null)은 자기 책방조차
 * 열 수 없고 남의 검색·팔로우 목록·스토리 피드에도 뜨지 않는다. 예전엔 "웹에서 아이디를 정하라"고 안내했지만
 * <b>토스로 시작한 계정은 비밀번호가 없어 웹 로그인 자체가 불가능</b>했다 — 실행 불가능한 죽은 안내였다.
 * 그래서 여기서 바로 만들게 한다.
 */
export function MyShelfEntry({
  myLoginId,
  onOpen,
  onCreateHandle,
}: {
  myLoginId: string | null;
  onOpen: (loginId: string) => void;
  onCreateHandle: () => void;
}) {
  if (myLoginId === null) {
    return (
      <div style={{ marginTop: 20 }}>
        <Text typography="st12" color="grey600" style={{ display: 'block' }}>
          @아이디를 만들면 친구가 나를 찾을 수 있고, 내 책방이 생겨요.
        </Text>
        <Button display="block" variant="weak" style={{ marginTop: 12 }} onClick={onCreateHandle}>
          아이디 만들기
        </Button>
      </div>
    );
  }
  return (
    <Button display="block" variant="weak" style={{ marginTop: 20 }} onClick={() => onOpen(myLoginId)}>
      내 책방 보기
    </Button>
  );
}

/**
 * 핸들(@아이디) 만들기 시트 — 토스로 가입한 계정이 소셜에 발견되게 하는 유일한 경로.
 *
 * <p>형식만 그 자리에서 검사해 즉시 피드백하고({@link validateHandleFormat}), <b>예약어·중복은 서버가
 * 판정</b>한다 — 서버가 준 평문 메시지를 그대로 띄운다. 핸들은 한 번 정하면 못 바꾸므로 만들기 전에
 * 그 사실을 알린다(되돌릴 수 없는 선택을 모르고 하지 않게).
 */
export function HandleSheet({
  onClose,
  onCreated,
  onFail,
}: {
  onClose: () => void;
  /** 서버가 정규화한(소문자) 핸들 — 클라이언트가 입력값을 그대로 믿지 않는다. */
  onCreated: (loginId: string) => void;
  onFail: (error: Error) => void;
}) {
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 빈 입력에까지 빨간 문구를 띄우진 않는다 — 아직 아무것도 안 쳤는데 혼내는 꼴이 된다.
  const formatError = value.trim() === '' ? null : validateHandleFormat(value);
  const submit = () => {
    setBusy(true);
    setError(null);
    createHandle(value.trim())
      .then((result) => onCreated(result.loginId))
      .catch((e: Error) => {
        if (e.name === 'UnauthorizedError') onFail(e);
        else setError(e.message); // 400/409 평문 — 문구가 곧 안내다
      })
      .finally(() => setBusy(false));
  };

  return (
    <Sheet title="@아이디 만들기" onClose={onClose}>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 12 }}>
        영문·숫자·밑줄(_) 3~20자. 대문자는 소문자로 저장돼요. <b>한 번 정하면 바꿀 수 없어요.</b>
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
          placeholder="예: goospel"
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
        만들기
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
