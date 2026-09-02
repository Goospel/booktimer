import { useEffect, useState } from 'react';

import type { ExploreUser, UserRow } from '../api';
import { fetchExplore } from '../api';
import { Avatar, BookCover, ErrorMessage, Loading, Screen, SearchField, Text, UserList } from '../ui';

/**
 * 둘러보기 — <b>아이디를 몰라도 사람을 만나는 화면</b>(2026-08-20).
 *
 * <p>인스타 탐색에서 디자인이 아니라 <b>구조만</b> 빌렸다: 사람과 그 사람의 책을 무작위로 늘어놓고,
 * 화면에 들어올 때마다 얼굴이 바뀐다(서버가 매 호출 섞는다). 그래서 「다른 사람 보기」 같은 새로고침
 * 손잡이를 두지 않는다 — <b>재진입이 곧 새로고침</b>이고, 버튼을 두면 그 사실이 오히려 가려진다.
 *
 * <p>전에는 이 자리가 「친구 찾기」 <b>시트</b>였다. 시트는 아이디를 아는 사람만 쓸 수 있는 도구였는데,
 * 이제 볼 것이 생겨 화면 하나를 다 쓴다. 검색 자체는 하나도 안 바뀌었다 — 입력이 있으면 그 결과가
 * 같은 자리에 그대로 나온다.
 */
export function Explore({
  query,
  results,
  busy,
  error,
  onQueryChange,
  onSearch,
  onSelect,
  onError,
}: {
  query: string;
  /** `null`이면 아직 안 찾았다 — 그때 이 화면은 둘러보기를 그린다(빈 배열=0건과 구분). */
  results: UserRow[] | null;
  busy: boolean;
  error: string | null;
  onQueryChange: (query: string) => void;
  onSearch: () => void;
  onSelect: (loginId: string) => void;
  onError: (error: Error) => void;
}) {
  const [users, setUsers] = useState<ExploreUser[] | null>(null);
  const [rateLimited, setRateLimited] = useState(false);
  const [exploreError, setExploreError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    fetchExplore()
      .then((page) => {
        if (!alive) return;
        setUsers(page.users);
        setRateLimited(page.rateLimited);
      })
      .catch((e: Error) => {
        if (!alive) return;
        // 인증 만료는 셸이 로그인으로 돌려보내는 사고다. 나머지는 이 화면 안에서 말한다.
        if (e.name === 'UnauthorizedError') onError(e);
        else setExploreError(e.message);
      });
    return () => {
      alive = false;
    };
    // 마운트 1회 — 재진입이 곧 새로고침이라 이 화면이 다시 열릴 때 자연히 다시 받는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 손잡이는 칸 안이다 — 「책 추가」와 같은 `SearchField`를 쓴다(두 화면이 다른 모양이면 안 된다).
  const search = (
    <div style={{ marginBottom: 16 }}>
      <SearchField
        label="아이디로 찾기"
        placeholder="아이디 입력"
        value={query}
        busy={busy}
        onChange={onQueryChange}
        onSubmit={onSearch}
      />
    </div>
  );

  return (
    <Screen title={results !== null ? '검색 결과' : '둘러보기'} above={search}>
      <ErrorMessage message={error ?? exploreError} />
      {results !== null ? (
        <UserList
          users={results}
          emptyMessage="그 아이디를 쓰는 사람이 없어요. 두 글자 이상으로 다시 찾아보세요."
          onSelect={onSelect}
        />
      ) : (
        <ExploreList users={users} rateLimited={rateLimited} onSelect={onSelect} />
      )}
    </Screen>
  );
}

/**
 * 둘러보기 목록 — 순수 표시다(데이터는 셸이 받아 프롭으로 준다).
 *
 * <p>정적 렌더 하니스가 이 분기들(못 받음 / 0명 / 한도 / 목록)에 닿으려면 내부 fetch가 없어야 한다 —
 * `FollowListSheet`·`ArchiveSheet`와 같은 이유의 같은 모양이다.
 */
export function ExploreList({
  users,
  rateLimited,
  onSelect,
}: {
  /** `null`이면 아직 받는 중 — 빈 배열(0명)과 구분해야 "없어요"를 먼저 깜빡이지 않는다. */
  users: ExploreUser[] | null;
  rateLimited: boolean;
  onSelect: (loginId: string) => void;
}) {
  // 한도는 조용한 0건과 다른 사건이다 — 같은 문구로 말하면 "볼 사람이 없다"는 거짓말이 된다.
  if (rateLimited) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        너무 자주 둘러봤어요. 잠시 후 다시 열어 주세요.
      </Text>
    );
  }
  if (users === null) {
    return <Loading />;
  }
  if (users.length === 0) {
    return (
      <Text typography="st11" color="grey600" style={{ display: 'block' }}>
        아직 볼 사람이 없어요. 아이디를 알면 위에서 바로 찾을 수 있어요.
      </Text>
    );
  }
  return (
    <>
      {users.map((u) => (
        <ExploreCard key={u.loginId} user={u} onSelect={onSelect} />
      ))}
    </>
  );
}

/**
 * 카드 한 장에 세우는 표지 폭 — 4권이 <b>한 줄에</b> 서야 한다(줄바꿈되면 카드 높이가 사람마다 튄다).
 *
 * <p>필요 폭은 `4×56 + 3×8 = 248`. 화면 좌우 여백(20씩)과 카드 안쪽 여백(12씩)을 빼면 표지가 쓸 수 있는
 * 폭은 「기기 폭 − 64」라, 320px 기기에서도 256이 남아 들어간다. 64px로 잡았을 땐 필요 폭이 280이라
 * <b>360px 기기에서 여유가 0</b>이었다(목 모드에서 카드 폭을 320·296·280으로 강제해 실측).
 */
const COVER_WIDTH = 56;

/**
 * 사람 한 명 = 카드 한 장. 누르면 그 사람의 책방으로 간다(팔로우는 거기서 한다 — 여기 버튼을 두면
 * 같은 동작이 두 곳에 살게 된다).
 *
 * <p>⚠️ <b>이유 칩도, 시점 문구도 없다.</b> 「같이 읽은 책 N권」은 겹친 책이 앞자리에 서는 것으로 이미
 * 말해지고(서버가 그 순서로 준다), 「공통 친구」·「나를 팔로우함」은 책방으로 옮겼다. 「언제 읽었는가」는
 * 애초에 서버가 안 보낸다 — 낯선 사람에게 보여줄 것이 아니라는 결정이다.
 */
function ExploreCard({ user, onSelect }: { user: ExploreUser; onSelect: (loginId: string) => void }) {
  return (
    <button
      type="button"
      aria-label={`${user.nickname}님 책방 열기`}
      onClick={() => onSelect(user.loginId)}
      style={{
        display: 'block',
        width: '100%',
        padding: 12,
        marginBottom: 10,
        borderRadius: 12,
        border: '1px solid var(--adaptiveGrey200, #E4DDD0)',
        background: 'var(--adaptiveGrey100, #FCFAF5)',
        textAlign: 'left',
        cursor: 'pointer',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 11 }}>
        <Avatar nickname={user.nickname} size={30} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <Text typography="st11" style={{ display: 'block' }}>
            {user.nickname}
          </Text>
          <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 1 }}>
            @{user.loginId} · 공개 책 {user.publicBookCount}권{user.following && ' · 팔로잉'}
          </Text>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8 }}>
        {user.books.map((b, i) => (
          // 같은 제목의 다른 책이 있을 수 있어 자리(index)를 키로 쓴다 — 목록은 한 번 받아 그대로 그린다.
          <div key={i} style={{ width: COVER_WIDTH, minWidth: 0 }}>
            <BookCover url={b.coverUrl} title={b.title} width={COVER_WIDTH} />
            <Text
              typography="st12"
              color="grey600"
              style={{
                display: 'block',
                marginTop: 4,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {b.title}
            </Text>
          </div>
        ))}
      </div>
    </button>
  );
}
