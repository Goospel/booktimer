import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';

import type { AuthorStories, FollowListType, StoryFeedResponse, UserRow } from '../api';
import { fetchBlocks, fetchFollowList, fetchStoryFeed, searchUsers, unblockUser } from '../api';
import { ErrorMessage, Loading, Screen, sectionStyle } from '../ui';
import { Profile } from './Profile';
import { StoryComposer, StoryStrip, StoryViewer } from './Story';

/**
 * 소셜 탭 — 스토리 스트립 · 유저 검색 · 팔로우 목록 · 차단 목록, 그리고 남의 책방 진입.
 *
 * <p>책방에서 팔로우·차단이 일어나므로, 책방에서 돌아올 때마다 목록을 다시 받는다 —
 * 차단한 사람은 검색·목록에서 사라져야 하는데 캐시가 남으면 이미 없는 사람이 계속 보인다.
 * 스토리 피드도 같이 받는다(팔로우가 곧 피드 대상이라 한 쪽만 갱신하면 어긋난다).
 */
export function Social({ myLoginId, onError }: { myLoginId: string | null; onError: (error: Error) => void }) {
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

      <TextField
        variant="box"
        label="아이디로 찾기"
        placeholder="예: goospel"
        value={query}
        disabled={busy}
        onChange={(e) => setQuery(e.target.value)}
      />
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

      <ErrorMessage message={error} />

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
          <MyShelfEntry myLoginId={myLoginId} onOpen={setOpen} />

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
    </Screen>
  );
}

/**
 * 내 책방 진입 — 핸들(login_id)이 있어야만 열 수 있다(설계 §5-1).
 *
 * <p>서버 소셜 API는 대상을 loginId로만 찾으므로, 토스로 새로 가입한 계정(login_id=null)은
 * 자기 책방조차 열 수 없다(파라미터 없는 요청 → 400). 눌러도 실패할 버튼 대신 웹 안내를 그린다.
 */
export function MyShelfEntry({ myLoginId, onOpen }: { myLoginId: string | null; onOpen: (loginId: string) => void }) {
  if (myLoginId === null) {
    return (
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 20 }}>
        웹(booktimer.app)에서 아이디를 정하면 내 책방이 생기고, 다른 사람이 나를 찾을 수 있어요.
      </Text>
    );
  }
  return (
    <Button display="block" variant="weak" style={{ marginTop: 20 }} onClick={() => onOpen(myLoginId)}>
      내 책방 보기
    </Button>
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
            background: 'var(--adaptiveGrey100, #f2f4f6)',
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
