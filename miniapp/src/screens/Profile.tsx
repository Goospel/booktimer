import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import type { ProfileBook, ProfileResponse, ReportReason } from '../api';
import {
  REPORT_REASONS,
  blockUser,
  fetchPersonalityTagBooks,
  fetchProfile,
  fetchProfileBooks,
  follow,
  reportUser,
  unfollow,
} from '../api';
import { formatDuration } from '../format';
import { BookCover, ErrorMessage, Loading, Screen } from '../ui';

/**
 * 책방(프로필) 뷰 — 닉네임·책BTI·공개 책 목록 + 팔로우/언팔로우 + 차단·신고.
 *
 * <p>대상은 loginId로만 식별된다(서버 소셜 API 공통). 차단·ADMIN·없는 아이디는 모두 404로 오므로
 * 화면은 "찾을 수 없어요"를 그대로 보여준다 — 존재 여부를 추측해 다르게 말하지 않는다.
 */
/**
 * 열린 안전 패널 — 열림과 차단 확인이 **한 덩어리**다(서재 `OpenRow`와 같은 이유).
 * 확인을 따로 두면 확인을 띄운 채 패널을 접었다 다시 폈을 때 「정말 차단」이 곧바로 노출된다.
 */
export interface SafetyState {
  confirmBlock: boolean;
}

/** 더보기 여닫기 — 어느 쪽이든 **차단 확인은 풀린 채로 시작한다**(위 불변식). */
export function toggleSafety(open: SafetyState | null): SafetyState | null {
  return open === null ? { confirmBlock: false } : null;
}

export function Profile({
  loginId,
  onBack,
  onError,
}: {
  loginId: string;
  onBack: () => void;
  onError: (error: Error) => void;
}) {
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [books, setBooks] = useState<ProfileBook[]>([]);
  const [activeTag, setActiveTag] = useState<string | null>(null);
  const [more, setMore] = useState<SafetyState | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
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
    fetchProfile(loginId).then(setProfile).catch(fail);
    // 헤더와 책 목록을 따로 받는다 — 태그 드릴다운이 책 목록만 갈아끼우므로 목록의 출처를 하나로 둔다.
    fetchProfileBooks(loginId).then((page) => setBooks(page.books)).catch(fail);
  }, [loginId, fail]);

  useEffect(load, [load]);

  const run = (action: Promise<unknown>, after: () => void) => {
    setBusy(true);
    setError(null);
    action
      .then(after)
      .catch(fail)
      .finally(() => setBusy(false));
  };

  const toggleFollow = () => {
    if (profile === null) return;
    // 서버가 준 상태가 유일한 진실 — 레이트리밋·차단이면 눌러도 안 바뀐다. 팔로워 수까지 맞추려고 다시 받는다.
    const call = profile.following ? unfollow : follow;
    run(call(loginId), () => void fetchProfile(loginId).then(setProfile).catch(fail));
  };

  const selectTag = (tag: string | null) => {
    setActiveTag(tag);
    const load = tag === null ? fetchProfileBooks(loginId) : fetchPersonalityTagBooks(loginId, tag);
    load.then((page) => setBooks(page.books)).catch(fail);
  };

  // 차단하면 그 순간부터 이 책방이 404다(대칭 숨김) — 머무를 화면이 없으니 목록으로 돌려보낸다.
  const block = () => run(blockUser(loginId), onBack);

  const report = (reason: ReportReason, detail: string) =>
    run(reportUser(loginId, reason, detail), () => {
      setMore(null);
      setNotice('신고가 접수됐어요. 검토 후 조치할게요.');
    });

  if (profile === null) {
    return (
      <Screen title="책방" onBack={onBack}>
        {/* 못 받았을 때 나갈 길만 있으면 실패가 곧 막다른 길이다 — 그 자리에서 다시 받을 길도 함께 준다. */}
        <ErrorMessage message={error} onRetry={load} />
        {error === null ? (
          <Loading />
        ) : (
          <Button display="block" variant="weak" style={{ marginTop: 24 }} onClick={onBack}>
            돌아가기
          </Button>
        )}
      </Screen>
    );
  }

  return (
    <>
      <ProfileCard
        profile={profile}
        books={books}
        activeTag={activeTag}
        busy={busy}
        onFollowToggle={toggleFollow}
        onSelectTag={selectTag}
        onMore={() => setMore(toggleSafety)}
        safety={
          more === null ? null : (
            <SafetyPanel
              busy={busy}
              confirmBlock={more.confirmBlock}
              onConfirmBlock={(confirmBlock) => setMore({ confirmBlock })}
              onBlock={block}
              onReport={report}
            />
          )
        }
        onBack={onBack}
      />
      <div style={{ padding: '0 20px 40px' }}>
        {notice !== null && (
          <Text typography="st12" color="grey600" style={{ display: 'block' }}>
            {notice}
          </Text>
        )}
        <ErrorMessage message={error} />
      </div>
    </>
  );
}

/** 책방 본문 — 순수 표시. 서버가 준 self·following 플래그가 무엇을 켜고 끄는지가 여기 다 모인다. */
export function ProfileCard({
  profile,
  books,
  activeTag,
  busy,
  onFollowToggle,
  onSelectTag,
  onMore,
  safety,
  onBack,
}: {
  profile: ProfileResponse;
  books: ProfileBook[];
  activeTag: string | null;
  busy: boolean;
  onFollowToggle: () => void;
  onSelectTag: (tag: string | null) => void;
  onMore: () => void;
  safety: ReactNode;
  onBack: () => void;
}) {
  return (
    <Screen title={`${profile.nickname}님의 책방`} onBack={onBack}>
      <Text typography="st12" color="grey600" style={{ display: 'block' }}>
        @{profile.loginId} · 팔로워 {profile.followerCount} · 팔로잉 {profile.followingCount}
      </Text>

      {profile.personality !== null && (
        <Text typography="st11" style={{ display: 'block', marginTop: 12 }}>
          {profile.personality}
        </Text>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 12 }}>
        {profile.personalityTags.map((tag) =>
          // 클릭 가능한 태그만 버튼 — 서버가 근거 책을 주지 않는 태그는 눌러도 빈 목록이라 안 누르게 한다.
          tag.clickable ? (
            <button key={tag.label} type="button" onClick={() => onSelectTag(tag.label)} style={chipStyle(true)}>
              {tag.label}
            </button>
          ) : (
            <span key={tag.label} style={chipStyle(false)}>
              {tag.label}
            </span>
          ),
        )}
      </div>

      {!profile.self && (
        <div style={{ display: 'flex', gap: 8, marginTop: 20 }}>
          <Button style={{ flex: 1 }} variant={profile.following ? 'weak' : 'fill'} disabled={busy} onClick={onFollowToggle}>
            {profile.following ? '팔로우 취소' : '팔로우'}
          </Button>
          <Button variant="weak" disabled={busy} onClick={onMore}>
            더보기
          </Button>
        </div>
      )}
      {safety}

      <section style={{ marginTop: 28 }}>
        <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
          {activeTag === null ? `공개한 책 ${books.length}` : `${activeTag} 근거 책 ${books.length}`}
        </Text>
        {activeTag !== null && (
          <Button size="small" variant="weak" style={{ marginBottom: 10 }} onClick={() => onSelectTag(null)}>
            전체 보기
          </Button>
        )}
        {books.length === 0 ? (
          <Text typography="st11" color="grey600" style={{ display: 'block' }}>
            공개한 책이 없어요.
          </Text>
        ) : (
          books.map((book) => (
            <div
              key={book.id}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: 16,
                marginBottom: 8,
                borderRadius: 12,
                background: 'var(--adaptiveGrey100, #FCFAF5)',
              }}
            >
              <BookCover url={book.coverUrl} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div>
                  <Text typography="st11">{book.title}</Text>
                </div>
                <div style={{ marginTop: 4 }}>
                  <Text typography="st12" color="grey600">
                    {book.author ?? '저자 미상'} · {book.status}
                    {book.seconds > 0 && ` · ${formatDuration(book.seconds)}`}
                  </Text>
                </div>
              </div>
            </div>
          ))
        )}
      </section>

      <Button display="block" variant="weak" style={{ marginTop: 24 }} onClick={onBack}>
        돌아가기
      </Button>
    </Screen>
  );
}

/**
 * 차단·신고 — 소셜 노출과 짝인 안전장치(설계 §4·§5-5). 사유는 서버 enum 값 그대로 보낸다.
 *
 * <p>차단 확인은 이 안의 state가 아니라 **밖에서 받는다** — 패널을 접을 때 함께 풀려야 하고(위
 * `toggleSafety`), 정적 렌더 하니스가 확인 단계에 도달할 길도 그 프롭뿐이다(서재 `confirmDelete`와 같다).
 */
export function SafetyPanel({
  busy,
  confirmBlock,
  onConfirmBlock,
  onBlock,
  onReport,
}: {
  busy: boolean;
  confirmBlock: boolean;
  onConfirmBlock: (confirm: boolean) => void;
  onBlock: () => void;
  onReport: (reason: ReportReason, detail: string) => void;
}) {
  const [reason, setReason] = useState<ReportReason>('SPAM');
  const [detail, setDetail] = useState('');

  return (
    <div style={{ marginTop: 12, padding: 16, borderRadius: 12, background: 'var(--adaptiveGrey100, #FCFAF5)' }}>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
        신고 사유
      </Text>
      {/* 네이티브 select — WebView 기본 피커가 TDS 바텀시트보다 가볍고 접근성도 기본 제공된다. */}
      <select
        value={reason}
        disabled={busy}
        onChange={(e) => setReason(e.target.value as ReportReason)}
        style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid #E4DDD0' }}
      >
        {REPORT_REASONS.map((r) => (
          <option key={r.value} value={r.value}>
            {r.label}
          </option>
        ))}
      </select>
      <div style={{ marginTop: 10 }}>
        <TextField
          variant="box"
          label="자세한 내용 (선택)"
          value={detail}
          disabled={busy}
          onChange={(e) => setDetail(e.target.value)}
        />
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 12 }}>
        <Button style={{ flex: 1 }} size="small" disabled={busy} onClick={() => onReport(reason, detail.trim())}>
          신고하기
        </Button>
        {/* 차단은 되돌리기 비싸다 — 그 순간 상대 책방이 404가 되고 이 화면도 닫힌다. 한 탭 더 받는다. */}
        {confirmBlock ? (
          <>
            <Button size="small" color="danger" disabled={busy} onClick={onBlock}>
              정말 차단
            </Button>
            <Button size="small" variant="weak" disabled={busy} onClick={() => onConfirmBlock(false)}>
              취소
            </Button>
          </>
        ) : (
          <Button size="small" color="danger" disabled={busy} onClick={() => onConfirmBlock(true)}>
            차단하기
          </Button>
        )}
      </div>
    </div>
  );
}

const chipStyle = (clickable: boolean) =>
  ({
    display: 'inline-block',
    padding: '6px 10px',
    borderRadius: 999,
    border: 'none',
    fontSize: 13,
    background: 'var(--adaptiveGrey100, #FCFAF5)',
    color: clickable ? 'var(--adaptiveBlue500, #6E8A6A)' : 'var(--adaptiveGrey700, #57534A)',
    cursor: clickable ? 'pointer' : 'default',
  }) as const;
