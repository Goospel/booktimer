import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import type {
  FollowListType,
  PersonalityEntry,
  PersonalityMutation,
  PersonalityStatus,
  ProfileBook,
  ProfileResponse,
  ReportReason,
} from '../api';
import {
  ApiError,
  REPORT_REASONS,
  adRefreshPersonality,
  blockUser,
  fetchPersonalityStatus,
  fetchPersonalityTagBooks,
  fetchProfile,
  fetchProfileBooks,
  follow,
  reportUser,
  selectPersonality,
  unfollow,
} from '../api';
import { useBackClose } from '../back';
import { formatDuration } from '../format';
import { REWARD_AD_GROUP_ID, watchRewardAd } from '../toss';
import { ErrorMessage, Loading, Screen, Sheet } from '../ui';
import { BookCarousel, waiverErrorMessage } from './Home';
import { GridSheet, handleStyle, resolveSelected } from './Library';

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

/**
 * 캐러셀 아래 메타 — 저자 한 줄, 「상태 · 읽은 시간」 한 줄(서재 `metaLine`과 같은 문법).
 * 읽은 시간이 0이면 적지 않는다 — 「0초」는 정보가 아니다. 줄바꿈은 캐러셀의 `pre-line`이 살린다.
 */
export function profileMetaLine(book: ProfileBook): string {
  const stats = [book.status];
  if (book.seconds > 0) stats.push(formatDuration(book.seconds));
  return `${book.author ?? '저자 미상'}\n${stats.join(' · ')}`;
}

/**
 * 성향 광고 버튼을 노출할지 — 넷 다 참이어야 한다.
 *
 * <p>① 내 책방이다(남의 성향을 내가 뽑을 수는 없다) ② 서버가 준 관문 상태를 받았다 ③ 콜드스타트가 아니다
 * ④ 오늘 총량이 남았다 ⑤ 광고 그룹 ID가 설정됐다(config-gate). ②가 특히 중요하다 — status를 못 받은
 * 상태(조회 실패·서버 미배포)에서 버튼을 그리면 "광고만 보고 보상 없음" 사고가 난다(fail-closed).
 */
export function showPersonalityAdButton(
  self: boolean,
  status: PersonalityStatus | null,
  adGroupId: string,
): boolean {
  return self && status !== null && !status.coldStart && status.adRefreshRemaining > 0 && adGroupId !== '';
}

/**
 * 보관함 손잡이를 노출할지 — <b>비교 대상이 2건 이상</b>일 때만 선다.
 *
 * <p>1건은 책방에 이미 걸려 있는 그 문구 하나뿐이라 보관함을 열어도 새 정보가 없다(빈 보관함과 같다).
 * 0건·`entries` 부재(필드 추가 전 옛 서버)·status 미수신도 자연히 숨는다 — 광고 버튼과 같은 fail-closed.
 */
export function showArchiveHandle(self: boolean, status: PersonalityStatus | null): boolean {
  return self && status !== null && (status.entries ?? []).length >= 2;
}

/**
 * 분석 뒤 안내 문구 — 성공했는데 <b>비교 대상이 생겼으면</b> 보관함까지 안내한다.
 *
 * <p>이 기능의 성패는 "바뀐 걸 확인했는가"이고, 서술은 책이 크게 안 바뀌면 미묘하게만 달라진다.
 * 손잡이만 두면 그 자리를 안 보므로, 새 결과가 도착한 그 순간에 비교할 길을 말해 준다.
 */
export function personalityNoticeText(failed: boolean, entryCount: number): string {
  if (failed) {
    return '지금은 분석이 어려워요. 잠시 후 다시 시도해 주세요.';
  }
  return entryCount >= 2
    ? '새 성향이 도착했어요. 보관함에서 이전 성향과 비교해 보세요.'
    : '새 성향이 도착했어요.';
}

/**
 * 히스토리에서 가장 최근 행 — 대표 승격(select 체이닝) 대상. 서버가 최신순으로 주지만 그 순서에 기대지 않고
 * 생성 시각(ISO 문자열은 사전순=시간순)으로 직접 고른다. {@code generatedAt}이 없는 행은 후보가 아니다 —
 * null이 비교를 이기면 엉뚱한 행이 대표가 된다.
 */
export function newestEntry(entries: PersonalityEntry[]): PersonalityEntry | null {
  return entries.reduce<PersonalityEntry | null>((best, e) => {
    if (e.generatedAt === null) return best;
    return best === null || e.generatedAt > best.generatedAt! ? e : best;
  }, null);
}

/**
 * 새 분석 생성에 실패했는가 — 서버는 LLM이 죽어도 200으로 응답하므로(사실만 폴백·stale 제공) 상태코드로는
 * 못 가른다. 서술이 안 실려 오면 실패다. 이때 <b>광고와 카운트는 이미 소비됐으니</b> 화면은 무광고 재시도를 연다.
 */
export function analysisFailed(result: PersonalityMutation): boolean {
  return result.view.narrative === null || result.view.state !== 'READY';
}

/** 실패 문구 — 429 본문만 JSON(구조체)이라 그대로 띄울 수 없다. 나머지는 홈의 광고 문구 규칙을 그대로 쓴다. */
export function personalityErrorMessage(error: Error): string {
  if (error instanceof ApiError && error.status === 429) {
    return '오늘 분석 횟수를 모두 썼어요. 내일 다시 시도해 주세요.';
  }
  return waiverErrorMessage(error);
}

/**
 * 분석 본체 — 광고 없는 재시도와 공유한다. 서버 재분석은 새 행을 <b>후보로만</b> 추가하므로(대표 불변),
 * 히스토리·선택 UI가 없는 미니앱에서는 최신 행이 미선택이면 select를 이어 대표로 올린다.
 * 안 그러면 광고를 보고도 책방 표시가 그대로다.
 */
export async function runPersonalityRefresh(): Promise<PersonalityMutation> {
  const result = await adRefreshPersonality();
  const newest = newestEntry(result.view.entries);
  if (newest !== null && !newest.selected) await selectPersonality(newest.id);
  return result;
}

/**
 * 광고 시청 → 분석. 끝까지 안 봤으면 <b>분석 API를 부르지 않고</b> `null`(조용히 원상태).
 *
 * <p>서버는 광고 시청을 검증할 수 없으므로(SDK에 서버사이드 보상 검증 없음) 이 클라 측 규율이 관문의
 * 실체다. 클릭 흐름을 화면에서 꺼내 둔 이유도 홈의 `claimDebtWaiver`와 같다 — 하니스가 정적 렌더라
 * 이 신뢰 경계를 함수로만 계측할 수 있다.
 */
export async function claimPersonality(adGroupId: string): Promise<PersonalityMutation | null> {
  const rewarded = await watchRewardAd(adGroupId);
  return rewarded ? runPersonalityRefresh() : null;
}

/**
 * 팔로워/팔로잉 카운트를 눌러 목록을 열 수 있는가 — 서버 `GET /api/follow-list`가 `Principal` 본인 목록만
 * 주므로 <b>내 책방 + 열어 줄 핸들러가 있을 때만</b> 참이다. 남의 책방에서 버튼으로 만들면 눌러도 열 목록이
 * 없는 죽은 UI가 된다(남의 팔로우 목록 열람은 프라이버시 판단이 필요한 별개 결정이라 아직 없다).
 */
export function followCountsOpenable(self: boolean, hasHandler: boolean): boolean {
  return self && hasHandler;
}

export function Profile({
  loginId,
  onBack,
  onError,
  header,
  onOpenFollowList,
}: {
  loginId: string;
  /** 없으면 「돌아가기」를 그리지 않는다 — 탭 루트(내 책방)에는 돌아갈 곳이 없고 출구가 탭바다. */
  onBack?: () => void;
  onError: (error: Error) => void;
  /** 제목과 카운트 줄 사이 슬롯 — 책방 셸이 스토리 스트립·검색 진입바를 여기 끼운다. */
  header?: ReactNode;
  /** 카운트를 눌렀을 때 — 목록 시트는 셸이 연다(이 화면은 목록 상태를 들지 않는다). */
  onOpenFollowList?: (type: FollowListType) => void;
}) {
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [books, setBooks] = useState<ProfileBook[]>([]);
  const [activeTag, setActiveTag] = useState<string | null>(null);
  /** 캐러셀에서 가운데 온 책 — 목록이 갈려 사라지면 `resolveSelected`가 첫 책으로 되돌린다. */
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [gridOpen, setGridOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [more, setMore] = useState<SafetyState | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  /** 성향 광고 관문 상태 — `null`이면 아직 못 받았거나 실패한 것이라 관문 자리를 통째로 접는다(fail-closed). */
  const [personalityStatus, setPersonalityStatus] = useState<PersonalityStatus | null>(null);
  const [adBusy, setAdBusy] = useState(false);
  /** 광고는 이미 봤는데 분석이 실패한 상태 — 재시도는 광고 없이 간다(앱 재시작 시 소실은 감수). */
  const [earnedRetry, setEarnedRetry] = useState(false);
  const [personalityNotice, setPersonalityNotice] = useState<string | null>(null);

  const fail = useCallback(
    (e: Error) => {
      if (e.name === 'UnauthorizedError') onError(e);
      else setError(e.message);
    },
    [onError],
  );

  /** 관문 상태 조회 — 실패는 화면 에러가 아니라 "버튼을 안 그린다"로 접는다(서버 미배포 구간 포함). */
  const loadPersonalityStatus = useCallback(() => {
    fetchPersonalityStatus()
      .then(setPersonalityStatus)
      .catch(() => setPersonalityStatus(null));
  }, []);

  const load = useCallback(() => {
    setError(null); // 재시도가 성공했는데 지난 실패 문구가 남지 않게
    fetchProfile(loginId)
      .then((page) => {
        setProfile(page);
        // 관문은 내 책방에만 선다 — 남의 책방에서 부르면 내 잔여를 남의 화면에서 소모하는 꼴이다.
        if (page.self) loadPersonalityStatus();
      })
      .catch(fail);
    // 헤더와 책 목록을 따로 받는다 — 태그 드릴다운이 책 목록만 갈아끼우므로 목록의 출처를 하나로 둔다.
    fetchProfileBooks(loginId).then((page) => setBooks(page.books)).catch(fail);
  }, [loginId, fail, loadPersonalityStatus]);

  useEffect(load, [load]);

  // 열린 시트는 뒤로가기가 먼저 먹는다 — 시트가 열린 채로 책방이 통째로 닫히지 않게(서재와 같다).
  useBackClose(gridOpen, () => setGridOpen(false));
  useBackClose(archiveOpen, () => setArchiveOpen(false));

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
  // 차단 진입은 남의 책방에만 있고 남의 책방엔 늘 onBack이 있다 — 없으면 그 자리에 머무는 게 최선이다.
  const block = () => run(blockUser(loginId), () => onBack?.());

  /**
   * 성향 분석 실행 — 광고 경로와 무광고 재시도가 공유한다. 중간 이탈(`null`)이면 아무 일도 없었던 것처럼 둔다.
   * 성공해도 응답 view를 손수 매핑하지 않고 `/api/profile`을 다시 받는다 — 태그 칩 모양이 달라 매핑이 낭비다.
   */
  const runPersonality = (call: () => Promise<PersonalityMutation | null>) => {
    setAdBusy(true);
    setError(null);
    setPersonalityNotice(null);
    call()
      .then((result) => {
        if (result === null) return;
        const failed = analysisFailed(result);
        setEarnedRetry(failed); // 광고는 이미 소비됐다 — 재시도는 광고 없이
        setPersonalityNotice(personalityNoticeText(failed, result.view.entries.length));
        if (!failed) fetchProfile(loginId).then(setProfile).catch(fail);
        loadPersonalityStatus(); // 잔여가 줄었다(실패해도 카운트는 선소비된다)
      })
      .catch((e: Error) => setError(personalityErrorMessage(e)))
      .finally(() => setAdBusy(false));
  };

  /**
   * 보관함에서 대표를 바꾼다 — 광고도 카운트도 쓰지 않는 순수 되돌리기(기존 select 엔드포인트 그대로).
   * 시트는 열어 둔 채 책방·관문 상태만 다시 받는다: 배지가 옮겨가는 걸 보는 게 곧 "바뀌었다"의 확인이다.
   */
  const selectArchived = (id: number) => {
    setAdBusy(true);
    setError(null);
    setPersonalityNotice(null);
    selectPersonality(id)
      .then(() => {
        fetchProfile(loginId).then(setProfile).catch(fail);
        loadPersonalityStatus();
      })
      .catch((e: Error) => setError(personalityErrorMessage(e)))
      .finally(() => setAdBusy(false));
  };

  const report = (reason: ReportReason, detail: string) =>
    run(reportUser(loginId, reason, detail), () => {
      setMore(null);
      setNotice('신고가 접수됐어요. 검토 후 조치할게요.');
    });

  if (profile === null) {
    return (
      <Screen title="책방" onBack={onBack}>
        {/* 로딩 중에도 header를 그린다 — 스토리·검색은 프로필 조회와 독립이라, 이 분기에서 빼면
            탭 진입 직후(응답 전) 상단이 통째로 비어 화면이 죽은 것처럼 보인다. */}
        {header}
        {/* 못 받았을 때 나갈 길만 있으면 실패가 곧 막다른 길이다 — 그 자리에서 다시 받을 길도 함께 준다. */}
        <ErrorMessage message={error} onRetry={load} />
        {error === null ? (
          <Loading />
        ) : (
          onBack !== undefined && (
            <Button display="block" variant="weak" style={{ marginTop: 24 }} onClick={onBack}>
              돌아가기
            </Button>
          )
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
        selectedId={selectedId}
        gridOpen={gridOpen}
        busy={busy}
        personalityStatus={personalityStatus}
        adBusy={adBusy}
        earnedRetry={earnedRetry}
        personalityNotice={personalityNotice}
        archiveOpen={archiveOpen}
        onArchive={setArchiveOpen}
        onSelectPersonality={selectArchived}
        onClaimPersonality={() => runPersonality(() => claimPersonality(REWARD_AD_GROUP_ID))}
        onRetryPersonality={() => runPersonality(runPersonalityRefresh)}
        onFollowToggle={toggleFollow}
        onSelectTag={selectTag}
        onSelect={setSelectedId}
        onGrid={setGridOpen}
        onMore={() => setMore(toggleSafety)}
        header={header}
        onOpenFollowList={onOpenFollowList}
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
  selectedId,
  gridOpen,
  busy,
  personalityStatus,
  adBusy,
  earnedRetry,
  personalityNotice,
  archiveOpen,
  onArchive,
  onSelectPersonality,
  onClaimPersonality,
  onRetryPersonality,
  onFollowToggle,
  onSelectTag,
  onSelect,
  onGrid,
  onMore,
  safety,
  header,
  onOpenFollowList,
  onBack,
}: {
  profile: ProfileResponse;
  books: ProfileBook[];
  activeTag: string | null;
  selectedId: number | null;
  gridOpen: boolean;
  busy: boolean;
  personalityStatus: PersonalityStatus | null;
  adBusy: boolean;
  earnedRetry: boolean;
  personalityNotice: string | null;
  archiveOpen: boolean;
  onArchive: (open: boolean) => void;
  onSelectPersonality: (id: number) => void;
  onClaimPersonality: () => void;
  onRetryPersonality: () => void;
  onFollowToggle: () => void;
  onSelectTag: (tag: string | null) => void;
  onSelect: (bookId: number | null) => void;
  onGrid: (open: boolean) => void;
  onMore: () => void;
  safety: ReactNode;
  /** 제목과 카운트 줄 사이 슬롯 — 셸이 스토리 스트립·검색 진입바를 끼운다. */
  header?: ReactNode;
  onOpenFollowList?: (type: FollowListType) => void;
  onBack?: () => void;
}) {
  const selected = resolveSelected(books, selectedId);
  const sectionTitle = activeTag === null ? `공개한 책 ${books.length}` : `${activeTag} 근거 책 ${books.length}`;
  const openable = followCountsOpenable(profile.self, onOpenFollowList !== undefined);

  // 제목 옆 ← 는 안 둔다 — 배경 없는 화살표 글자라 버튼으로 안 읽혔다. 출구는 아래 「돌아가기」와 탭바.
  return (
    <Screen title={`${profile.nickname}님의 책방`}>
      {header}
      <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 4 }}>
        <Text typography="st12" color="grey600">
          @{profile.loginId} ·
        </Text>
        {openable ? (
          <>
            <button type="button" onClick={() => onOpenFollowList!('followers')} style={countButtonStyle}>
              팔로워 {profile.followerCount}
            </button>
            <Text typography="st12" color="grey600">
              ·
            </Text>
            <button type="button" onClick={() => onOpenFollowList!('following')} style={countButtonStyle}>
              팔로잉 {profile.followingCount}
            </button>
          </>
        ) : (
          <Text typography="st12" color="grey600">
            팔로워 {profile.followerCount} · 팔로잉 {profile.followingCount}
          </Text>
        )}
      </div>

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

      {/* 성향 추출 관문 — 결과(서술·태그)가 보이는 바로 그 자리에 손잡이를 둔다. status를 못 받았으면
          아무것도 그리지 않는다(fail-closed): 서버 미배포 구간에도 번들이 먼저 나갈 수 있는 근거다. */}
      {profile.self && personalityStatus !== null && (
        <div style={{ marginTop: 16 }}>
          {showPersonalityAdButton(profile.self, personalityStatus, REWARD_AD_GROUP_ID) && (
            // 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다(홈의 부채 버튼과 같은 규율).
            <Button display="block" variant="weak" size="small" disabled={adBusy} onClick={onClaimPersonality}>
              {personalityStatus.hasSelected ? '광고 보고 다시 분석하기' : '광고 보고 성향 분석 받기'}
            </Button>
          )}
          {personalityStatus.coldStart && (
            <Text typography="st12" color="grey600" style={{ display: 'block' }}>
              책을 1권 완독하면 성향을 분석할 수 있어요.
            </Text>
          )}
          {!personalityStatus.coldStart && personalityStatus.adRefreshRemaining === 0 && (
            <Text typography="st12" color="grey600" style={{ display: 'block' }}>
              오늘 분석 횟수를 모두 썼어요. 내일 다시 시도해 주세요.
            </Text>
          )}
          {/* 광고를 이미 본 뒤 LLM이 실패한 자리 — 같은 사람에게 광고를 두 번 보게 하지 않는다. */}
          {earnedRetry && (
            <Button display="block" variant="weak" size="small" style={{ marginTop: 8 }} disabled={adBusy} onClick={onRetryPersonality}>
              다시 시도하기
            </Button>
          )}
          {personalityNotice !== null && (
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 8 }}>
              {personalityNotice}
            </Text>
          )}
          {/* 보관함 — 비교 대상이 둘 이상일 때만. 「문구가 바뀌었나」를 확인할 수 있는 유일한 자리다. */}
          {showArchiveHandle(profile.self, personalityStatus) && (
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
              <button type="button" onClick={() => onArchive(true)} style={handleStyle}>
                보관함에서 비교하기
              </button>
            </div>
          )}
        </div>
      )}

      {archiveOpen && (
        <ArchiveSheet
          entries={personalityStatus?.entries ?? []}
          busy={adBusy}
          onSelect={onSelectPersonality}
          // 고른 뒤에도 시트를 닫지 않는다 — 배지가 옮겨가는 걸 그 자리에서 보는 게 "바뀌었다"의 확인이다.
          onClose={() => onArchive(false)}
        />
      )}

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
        {/* 손잡이는 목록 바로 위 줄에 — 서재는 제목 줄에 뒀지만 책방은 제목과 캐러셀 사이에 핸들·성향·태그·
            팔로우가 깔려, 제목 줄에 두면 손잡이와 그 대상(책)이 화면 절반쯤 떨어진다. */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
          <Text typography="st11" color="grey600" style={{ flex: 1, minWidth: 0 }}>
            {sectionTitle}
          </Text>
          {books.length > 1 && (
            <button type="button" onClick={() => onGrid(true)} style={handleStyle}>
              펼쳐보기
            </button>
          )}
        </div>
        {activeTag !== null && (
          <Button size="small" variant="weak" style={{ marginBottom: 10 }} onClick={() => onSelectTag(null)}>
            전체 보기
          </Button>
        )}
        {selected === null ? (
          <Text typography="st11" color="grey600" style={{ display: 'block' }}>
            공개한 책이 없어요.
          </Text>
        ) : (
          // 태그를 고르면 목록이 통째로 갈리므로 다시 마운트한다 — 안 그러면 트랙이 옛 목록의 스크롤 자리에 머문다.
          <BookCarousel
            key={activeTag ?? 'all'}
            books={books}
            selectedId={selected.id}
            onSelect={onSelect}
            noBookCard={false}
            metaOf={profileMetaLine}
          />
        )}
      </section>

      {gridOpen && (
        <GridSheet
          title={sectionTitle}
          rows={books}
          selectedId={selected?.id ?? null}
          onPick={(id) => {
            onSelect(id);
            onGrid(false);
          }}
          onClose={() => onGrid(false)}
        />
      )}

      {/* 탭 루트(내 책방)에는 돌아갈 곳이 없다 — 아무 데도 안 가는 손잡이를 남기지 않는다. */}
      {onBack !== undefined && (
        <Button display="block" variant="weak" style={{ marginTop: 24 }} onClick={onBack}>
          돌아가기
        </Button>
      )}
    </Screen>
  );
}

/**
 * 카운트 텍스트 버튼 — 배경·보더 없이 글자만 두되 <b>밑줄</b>을 남긴다.
 * 배경 없는 글자는 버튼으로 안 읽힌다(#788에서 제목 옆 ← 를 없앤 이유)이고, 밑줄이 최소한의 어포던스다.
 */
const countButtonStyle = {
  padding: 0,
  border: 'none',
  background: 'transparent',
  color: 'var(--adaptiveGrey600, #6F6A5E)',
  fontSize: 13,
  textDecoration: 'underline',
  cursor: 'pointer',
} as const;

/**
 * 독서 성향 보관함 — 최근 분석(최대 3)을 <b>세로로 나란히</b> 놓아 서술의 차이를 눈으로 보게 한다.
 *
 * <p>이 화면이 있는 이유: 책방은 대표 서술 하나만 보여주므로, 분석을 새로 해도 문구가 미묘하게만 바뀌면
 * "바뀐 건지 아닌지" 알 수가 없다. 그래서 <b>서술을 요약하지 않고 전문 그대로</b> 싣는다 — 줄여 놓으면
 * 정작 달라진 대목이 잘려 비교라는 목적이 사라진다. 가로 캐러셀도 같은 이유로 안 쓴다(한 번에 한 장).
 *
 * <p>순수 표시다 — 선택 실행·닫기는 프롭. 시트 껍데기는 서재·홈과 같은 자체 구현 `Sheet`(TDS `BottomSheet`는
 * 포털이라 정적 렌더 하니스에서 마크업이 통째로 빈다).
 */
export function ArchiveSheet({
  entries,
  busy,
  onSelect,
  onClose,
}: {
  entries: PersonalityEntry[];
  busy: boolean;
  onSelect: (id: number) => void;
  onClose: () => void;
}) {
  return (
    <Sheet title="독서 성향 보관함" onClose={onClose}>
      <Text typography="st12" color="grey600" style={{ display: 'block', marginBottom: 12, wordBreak: 'keep-all' }}>
        분석할 때마다 문구가 조금씩 달라져요. 마음에 드는 성향을 책방에 걸어 둘 수 있어요.
      </Text>
      {entries.map((e) => (
        <div
          key={e.id}
          style={{
            marginBottom: 10,
            padding: 14,
            borderRadius: 12,
            // 대표 카드만 테두리 색으로 — 여러 장을 훑을 때 "지금 걸린 것"을 잃지 않게(격자 시트와 같은 문법).
            border: `1px solid ${e.selected ? '#6E8A6A' : '#E4DDD0'}`,
            background: '#FFFDF8',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Text typography="st12" color="grey600" style={{ flex: 1, minWidth: 0 }}>
              {e.generatedAtLabel}
            </Text>
            {e.selected && (
              <span
                style={{
                  flex: '0 0 auto',
                  padding: '3px 8px',
                  borderRadius: 999,
                  fontSize: 11,
                  background: '#6E8A6A',
                  color: '#FFFDF8',
                }}
              >
                지금 대표
              </span>
            )}
          </div>
          <Text typography="st11" style={{ display: 'block', marginTop: 8, wordBreak: 'keep-all' }}>
            {e.narrative}
          </Text>
          {e.stale && (
            <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 6 }}>
              지난 책장 기준 분석이에요.
            </Text>
          )}
          {/* 대표 카드엔 버튼을 안 둔다 — 이미 걸린 문구를 다시 고를 일이 없고, 버튼이 둘이면 대표가 흐려진다. */}
          {!e.selected && (
            <Button
              display="block"
              variant="weak"
              size="small"
              style={{ marginTop: 10 }}
              disabled={busy}
              onClick={() => onSelect(e.id)}
            >
              이 성향으로 바꾸기
            </Button>
          )}
        </div>
      ))}
    </Sheet>
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
