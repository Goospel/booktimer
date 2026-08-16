import { Button, Text, TextField } from '@toss/tds-mobile';
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';

import type {
  BookStatus,
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
import { REWARD_AD_GROUP_ID, watchRewardAd } from '../toss';
import { COVER_FG, ErrorMessage, Loading, Screen, Sheet, coverColor, initialOf } from '../ui';
import { waiverErrorMessage } from './Home';
import { BookGrid, SECTIONS } from './Library';
import { hasFreshStory } from './Story';

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

/** 성향 관문 손잡이 — 한 줄에 나란히 설 것들. 순서가 곧 화면의 왼→오른쪽이다. */
export type PersonalityAction = 'ad' | 'archive';

/**
 * 그 줄에 무엇이 서는가 — 광고 버튼은 전폭, 보관함은 그 아래 오른쪽 끝 알약이라 <b>같은 층위의 동작
 * 둘이 서로 다른 물건처럼</b> 흩어져 있었다(사용자 지적). 한 줄에 나란히 세우려면 "무엇이 설 자격이
 * 되는가"가 한 곳에서 나와야 한다 — 두 술어를 화면에서 각각 부르면 행의 유무와 칸 수가 어긋난다.
 *
 * <p>빈 배열이면 행 자체를 그리지 않는다(빈 여백만 남는 유령 줄 방지). 조건은 각 술어가 그대로 지고
 * 여기서는 순서와 동석만 정한다.
 */
export function personalityActions(
  self: boolean,
  status: PersonalityStatus | null,
  adGroupId: string,
): PersonalityAction[] {
  const actions: PersonalityAction[] = [];
  if (showPersonalityAdButton(self, status, adGroupId)) actions.push('ad');
  if (showArchiveHandle(self, status)) actions.push('archive');
  return actions;
}

/**
 * 성향(bio)을 접기 시작하는 길이 — 3줄 ≈ 90자(13px·폭 350 기준). CSS `line-clamp`가 실제로 자르고,
 * 이 값은 <b>손잡이를 세울지</b>만 정한다. 정확한 줄 수는 폰트·폭에 달려 JS로는 못 재므로 근사치다.
 */
export const BIO_CLAMP_CHARS = 90;

/**
 * 「더보기」를 달지 — 접힐 만큼 길 때만. clamp는 넘칠 때만 자르므로, 짧은 서술에까지 손잡이를 달면
 * 눌러도 아무것도 안 펼쳐지는 죽은 버튼이 된다.
 */
export function needsBioToggle(personality: string | null): boolean {
  return personality !== null && personality.length > BIO_CLAMP_CHARS;
}

/**
 * 공개 책 소제목 — 태그 드릴다운 &gt; 상태 필터 &gt; 전체 순으로 이긴다(태그 중엔 필터가 null이라 실제로는 충돌 없음).
 *
 * <p>격자 칸에는 상태 배지를 안 붙이므로(표지 80px + 제목뿐이고 발광 점이 이미 그 칸의 신호를 쓴다)
 * <b>지금 무엇으로 좁혔는지</b>를 말하는 자리는 이 소제목 하나뿐이다.
 */
export function shelfTitle(activeTag: string | null, statusFilter: BookStatus | null, count: number): string {
  if (activeTag !== null) return `${activeTag} 근거 책 ${count}`;
  if (statusFilter !== null) return `${SECTIONS.find((s) => s.status === statusFilter)!.title} ${count}`;
  return `공개한 책 ${count}`;
}

export function Profile({
  loginId,
  onBack,
  onError,
  header,
  onOpenFollowList,
  onOpenMargin,
}: {
  loginId: string;
  /** 없으면 「돌아가기」를 그리지 않는다 — 탭 루트(내 책방)에는 돌아갈 곳이 없고 출구가 탭바다. */
  onBack?: () => void;
  onError: (error: Error) => void;
  /** 제목보다 **위**에 얹히는 슬롯 — 책방 셸이 검색 진입을 여기 끼운다. */
  header?: ReactNode;
  /** 카운트를 눌렀을 때 — 목록 시트는 셸이 연다(이 화면은 목록 상태를 들지 않는다). */
  onOpenFollowList?: (type: FollowListType) => void;
  /** 격자에서 책을 눌렀을 때 — 그 책의 여백 화면으로 간다(전체 화면 전이는 셸이 든다). */
  onOpenMargin?: (bookId: number) => void;
}) {
  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [books, setBooks] = useState<ProfileBook[]>([]);
  const [activeTag, setActiveTag] = useState<string | null>(null);
  /** 걸린 상태 필터 — `null`이 「전체」다. 기본이 전체라 진입 화면·발광·여백 문이 지금 그대로다. */
  const [statusFilter, setStatusFilter] = useState<BookStatus | null>(null);
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
    // 두 축은 배타다 — 태그 근거 책 API에는 status 파라미터가 없어 AND를 하려면 클라가 한글 라벨로
    // 역필터해야 한다(api.ts가 금한 짓). 태그로 들어가면 필터를 풀고, 나올 땐 이미 전체라 복원도 없다.
    if (tag !== null) setStatusFilter(null);
    const load = tag === null ? fetchProfileBooks(loginId) : fetchPersonalityTagBooks(loginId, tag);
    load.then((page) => setBooks(page.books)).catch(fail);
  };

  const selectStatus = (status: BookStatus | null) => {
    setStatusFilter(status);
    // 서버가 거르게 둔다 — `GET /api/profile/books`가 이미 status를 받는다(웹 책장과 같은 파라미터).
    fetchProfileBooks(loginId, status ?? undefined).then((page) => setBooks(page.books)).catch(fail);
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
    // 로딩 중에도 header를 그린다 — 여백·검색은 프로필 조회와 독립이라, 이 분기에서 빼면
    // 탭 진입 직후(응답 전) 상단이 통째로 비어 화면이 죽은 것처럼 보인다.
    return (
      <Screen title="책방" onBack={onBack} above={header}>
        {/* 못 받았을 때 나갈 길만 있으면 실패가 곧 막다른 길이다 — 그 자리에서 다시 받을 길도 함께 준다. */}
        <ErrorMessage message={error} onRetry={load} />
        {error === null && <Loading />}
      </Screen>
    );
  }

  return (
    <>
      <ProfileCard
        profile={profile}
        books={books}
        activeTag={activeTag}
        now={Date.now()}
        onOpenMargin={onOpenMargin}
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
        statusFilter={statusFilter}
        onSelectStatus={selectStatus}
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
  now,
  onOpenMargin,
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
  statusFilter,
  onSelectStatus,
  onMore,
  safety,
  header,
  onOpenFollowList,
  onBack,
}: {
  profile: ProfileResponse;
  books: ProfileBook[];
  activeTag: string | null;
  /** 발광 판정의 기준 시각 — 밖에서 받아야 테스트가 결정론이 된다. */
  now: number;
  /** 격자 칸을 눌러 여백을 여는 손잡이. 없으면 칸을 버튼으로도 만들지 않는다. */
  onOpenMargin?: (bookId: number) => void;
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
  /** 걸린 상태 필터 — `null`이 「전체」. */
  statusFilter: BookStatus | null;
  onSelectStatus: (status: BookStatus | null) => void;
  onMore: () => void;
  safety: ReactNode;
  /** 제목보다 **위**에 얹히는 슬롯 — 셸이 검색 진입바·여백 스트립을 끼운다. */
  header?: ReactNode;
  onOpenFollowList?: (type: FollowListType) => void;
  onBack?: () => void;
}) {
  const sectionTitle = shelfTitle(activeTag, statusFilter, books.length);
  const openable = followCountsOpenable(profile.self, onOpenFollowList !== undefined);
  const actions = personalityActions(profile.self, personalityStatus, REWARD_AD_GROUP_ID);

  /*
   * 나가는 길은 **상단 「돌아가기」 하나**다. 이 화면은 같은 문제를 두 번 겪었다 — 처음엔 제목 옆 `←`
   * 글리프였는데 배경이 없어 버튼으로 안 읽혀 지우고 하단 버튼만 남겼고, 여백 화면에서 같은 지적이 또
   * 나와 **글자가 붙은 알약**이 생겼다(2026-08-16). 회피 이유가 사라졌으므로 위로 되돌리고 하단 버튼을
   * 걷는다. 탭 루트(내 책방)는 `onBack`이 없어 아무것도 안 그려진다 — 출구는 플로팅 탭바다.
   */
  return (
    // 검색·여백는 화면 소속이 아니라 그 위에 얹히는 도구다 — 신원 블록보다 위로 올린다.
    <Screen above={header} onBack={onBack}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <Avatar nickname={profile.nickname} />
        <div style={{ flex: 1, display: 'flex', minWidth: 0 }}>
          {/* 공개 책은 손잡이가 아니다 — 목록은 바로 아래 이 화면 안에 이미 있다. */}
          <StatItem label="공개 책" count={profile.books.length} />
          {openable ? (
            <>
              <FollowCountButton
                label="팔로워"
                count={profile.followerCount}
                onClick={() => onOpenFollowList!('followers')}
              />
              <FollowCountButton
                label="팔로잉"
                count={profile.followingCount}
                onClick={() => onOpenFollowList!('following')}
              />
            </>
          ) : (
            // 남의 책방 — 서버가 남의 목록을 안 주므로 누를 수 없다. 누를 수 없는 것을 버튼처럼 보이게 하지 않는다(#788).
            <>
              <StatItem label="팔로워" count={profile.followerCount} />
              <StatItem label="팔로잉" count={profile.followingCount} />
            </>
          )}
        </div>
      </div>

      <div style={{ marginTop: 14, fontSize: 15, fontWeight: 500, color: 'var(--adaptiveGrey900, #3A362E)' }}>
        {profile.nickname}
      </div>
      {/* @아이디는 닉네임에 딸린 식별자라 이름에 밀착시킨다(카운트와 한 줄로 섞이면 카운트의 일부로 읽힌다). */}
      <Text typography="st12" color="grey600" style={{ display: 'block', marginTop: 1 }}>
        @{profile.loginId}
      </Text>

      {profile.personality !== null && <Bio text={profile.personality} />}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 10 }}>
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
          {/* 손잡이들은 한 줄에 나란히 — 폭을 반씩 나눠 쓰고, 하나만 설 자격이 되면 그게 전폭이다. */}
          {actions.length > 0 && (
            <div style={{ display: 'flex', gap: 8 }}>
              {actions.map((action) =>
                action === 'ad' ? (
                  // 문구에 "광고"를 명시해 광고 위장 금지 조항을 지킨다(홈의 부채 버튼과 같은 규율).
                  <Button
                    key="ad"
                    style={{ flex: 1 }}
                    variant="weak"
                    size="small"
                    disabled={adBusy}
                    onClick={onClaimPersonality}
                  >
                    {personalityStatus.hasSelected ? '광고 보고 다시 분석하기' : '광고 보고 성향 분석 받기'}
                  </Button>
                ) : (
                  // 보관함 — 비교 대상이 둘 이상일 때만. 「문구가 바뀌었나」를 확인할 유일한 자리다.
                  <Button
                    key="archive"
                    style={{ flex: 1 }}
                    variant="weak"
                    size="small"
                    disabled={adBusy}
                    onClick={() => onArchive(true)}
                  >
                    보관함에서 비교하기
                  </Button>
                ),
              )}
            </div>
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
          {/* 「더보기」였다 — 성향 bio의 접기 손잡이와 같은 이름이라 한 화면에 둘이 섰다. 하는 일로 부른다. */}
          <Button variant="weak" disabled={busy} onClick={onMore}>
            신고·차단
          </Button>
        </div>
      )}
      {safety}

      {/*
       * 공개 책 = 3열 격자(사용자 승인 B안). 캐러셀은 한 번에 한 권만 보여 주고 나머지는 「펼쳐보기」를
       * 눌러야 했다 — 책장을 훑는 화면인데 왕복이 끼어 있었다. 격자는 그 시트를 본문에 펼친 것이라
       * 새로 만든 물건이 없다. ⚠️ 대신 캐러셀 아래 있던 메타(상태·읽은 시간)는 사라진다(알고 한 교환).
       *
       * 이제 각 칸이 **그 책의 여백으로 가는 문**이다(2026-08-16 재설계) — 24시간 안에 새 글이 달린 책은
       * 발광한다. 판정 재료(`lastStoryAt`)는 서버가 주고 창 계산은 `hasFreshStory`가 한다.
       */}
      <section style={{ marginTop: 28 }}>
        <Text typography="st11" color="grey600" style={{ display: 'block', marginBottom: 10 }}>
          {sectionTitle}
        </Text>
        {activeTag !== null && (
          <Button size="small" variant="weak" style={{ marginBottom: 10 }} onClick={() => onSelectTag(null)}>
            전체 보기
          </Button>
        )}
        {/* 상태 필터 — 격자 바로 위(성향 태그 줄과 자리로 구분된다). 드릴다운 중엔 숨긴다: 태그와 배타라
            눌러 봐야 갈 곳이 없고, 칩이 없으면 "드릴다운 중 상태 클릭"이라는 경로 자체가 안 생긴다. */}
        {activeTag === null && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 10 }}>
            {[null, ...SECTIONS.map((s) => s.status)].map((status) => (
              <button
                key={status ?? 'ALL'}
                type="button"
                aria-pressed={statusFilter === status}
                onClick={() => onSelectStatus(status)}
                style={filterChipStyle(statusFilter === status)}
              >
                {status === null ? '전체' : SECTIONS.find((s) => s.status === status)!.title}
              </button>
            ))}
          </div>
        )}
        {books.length === 0 ? (
          <Text typography="st11" color="grey600" style={{ display: 'block' }}>
            {/* 좁혀서 빈 것과 애초에 없는 것은 다른 사실이다 — 필터를 건 채 "공개한 책이 없어요"는 거짓말이다. */}
            {statusFilter === null && activeTag === null ? '공개한 책이 없어요.' : '이 상태의 공개 책이 없어요.'}
          </Text>
        ) : (
          <BookGrid
            rows={books.map((b) => ({ ...b, fresh: hasFreshStory(b.lastStoryAt, now) }))}
            selectedId={null}
            onPick={onOpenMargin}
          />
        )}
      </section>

    </Screen>
  );
}

/**
 * 책방 주인 아바타 — 무표지 책과 같은 이니셜 원(같은 사람은 언제 그려도 같은 색).
 * 링은 안 두른다: 이 화면에서 발광은 **책 격자**가 지는 신호라, 신원 아바타까지 두르면 뜻이 흐려진다.
 */
function Avatar({ nickname }: { nickname: string }) {
  return (
    <div
      aria-hidden="true"
      style={{
        flex: '0 0 auto',
        width: 72,
        height: 72,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: '50%',
        fontSize: 27,
        background: coverColor(nickname),
        color: COVER_FG,
      }}
    >
      {initialOf(nickname)}
    </div>
  );
}

/** 카운트 한 칸의 알맹이 — 숫자 위, 라벨 아래(인스타 문법). 버튼이든 아니든 같은 모양이어야 줄이 안 흔들린다. */
function StatBody({ label, count }: { label: string; count: number }) {
  return (
    <>
      {/* data-stat은 계측 손잡이 — 숫자와 라벨이 각자 span이라 마크업에서 「팔로워 3」이 이어 붙지 않는다. */}
      <span
        style={{ display: 'block', fontSize: 17, fontWeight: 500, color: 'var(--adaptiveGrey900, #3A362E)' }}
        data-stat={label}
      >
        {count}
      </span>
      <span style={{ display: 'block', marginTop: 2, fontSize: 12, color: 'var(--adaptiveGrey600, #6F6A5E)' }}>
        {label}
      </span>
    </>
  );
}

/** 누를 수 없는 카운트 — 공개 책, 그리고 남의 책방의 팔로워·팔로잉(서버가 남의 목록을 안 준다). */
export function StatItem({ label, count }: { label: string; count: number }) {
  return (
    <div style={{ flex: 1, textAlign: 'center', minWidth: 0 }}>
      <StatBody label={label} count={count} />
    </div>
  );
}

/**
 * 팔로워·팔로잉 손잡이 — 아바타 오른쪽 카운트 줄의 한 칸(인스타 배치). 테두리 버튼 두 개가 전폭을
 * 차지하던 것을 접어 그 자리를 책에 돌려준다. 눌리는 것임은 이제 테두리가 아니라 <b>자리</b>가 말한다.
 */
export function FollowCountButton({
  label,
  count,
  onClick,
}: {
  label: string;
  count: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={`${label} ${count}명 보기`}
      onClick={onClick}
      style={{
        flex: 1,
        minWidth: 0,
        padding: 0,
        border: 'none',
        background: 'transparent',
        textAlign: 'center',
        cursor: 'pointer',
      }}
    >
      <StatBody label={label} count={count} />
    </button>
  );
}

/**
 * 성향 서술 = 인스타 bio — 3줄만 보이고 나머지는 「더보기」. 10줄 문단이 통째로 펼쳐져 있으면 그 아래
 * 책이 첫 화면 밖으로 밀린다(사용자 스크린샷에서 「공개한 책」이 탭바에 잘려 있었다).
 */
function Bio({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  const clamped = !expanded && needsBioToggle(text);

  return (
    <div style={{ marginTop: 8 }}>
      <span
        style={{
          fontSize: 13,
          lineHeight: 1.55,
          color: 'var(--adaptiveGrey900, #3A362E)',
          wordBreak: 'keep-all',
          // clamp는 넘칠 때만 자른다 — 짧은 서술엔 이 블록이 걸려도 아무 일도 일어나지 않는다.
          ...(clamped
            ? ({ display: '-webkit-box', WebkitLineClamp: 3, WebkitBoxOrient: 'vertical', overflow: 'hidden' } as const)
            : { display: 'block' }),
        }}
      >
        {text}
      </span>
      {/* 알약 손잡이가 아니라 회색 글자 — 서술의 꼬리로 읽혀야지, 성향과 나란한 또 하나의 버튼이 되면 안 된다. */}
      {needsBioToggle(text) && (
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          style={{
            display: 'block',
            marginTop: 2,
            padding: 0,
            border: 'none',
            background: 'transparent',
            fontSize: 13,
            color: 'var(--adaptiveGrey600, #6F6A5E)',
            cursor: 'pointer',
          }}
        >
          {expanded ? '접기' : '더보기'}
        </button>
      )}
    </div>
  );
}

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

/**
 * 상태 필터 칩 — 성향 태그 칩과 같은 문법(알약·13px)이되 <b>걸린 것만 배경이 반전</b>된다.
 * 지금 무엇으로 좁혔는지는 소제목도 말하지만, 줄 안에서 "어느 칩이 눌렸나"는 색이 져야 한 눈에 읽힌다.
 */
const filterChipStyle = (active: boolean) =>
  ({
    display: 'inline-block',
    padding: '6px 10px',
    borderRadius: 999,
    border: 'none',
    fontSize: 13,
    background: active ? 'var(--adaptiveBlue500, #6E8A6A)' : 'var(--adaptiveGrey100, #FCFAF5)',
    color: active ? '#FFFDF8' : 'var(--adaptiveGrey700, #57534A)',
    cursor: 'pointer',
  }) as const;

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
