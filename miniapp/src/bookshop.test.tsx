import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  BookStatus,
  FollowListType,
  PersonalityEntry,
  PersonalityMutation,
  PersonalityStatus,
  ProfileBook,
  ProfileResponse,
  UserRow,
} from './api';
import { ApiError, adRefreshPersonality, selectPersonality } from './api';
import { cacheClear, cacheKeyProfile, cacheKeyProfileBooks, cachePut } from './cache';
import { Bookshop, BookshopHeader, FollowListSheet, HandleSheet } from './screens/Bookshop';
import { UserList } from './ui';
import {
  ArchiveSheet,
  Profile,
  BIO_CLAMP_CHARS,
  FollowCountButton,
  ProfileCard,
  SafetyPanel,
  analysisFailed,
  claimPersonality,
  followCountsOpenable,
  needsBioToggle,
  newestEntry,
  personalityActions,
  personalityErrorMessage,
  personalityNoticeText,
  runPersonalityRefresh,
  shelfTitle,
  showArchiveHandle,
  showPersonalityAdButton,
  toggleSafety,
} from './screens/Profile';
import { userAgent } from './test-fixtures';
import { watchRewardAd } from './toss';

/**
 * 소셜 화면의 분기를 정적 렌더로 계측한다 — 뮤테이션(팔로우·차단·신고) 자체는 api 계층에서 이미 계측했고,
 * 여기서는 "서버가 준 플래그(self·following)가 화면의 무엇을 켜고 끄는가"를 본다.
 * 특히 self 분기는 서버가 400으로 거절하는 동작(자기 팔로우·자기 차단)이라 화면에서 애초에 막아야 한다.
 *
 * <p>성향 광고 관문도 같은 방식이다: 노출 조건은 순수 술어 {@link showPersonalityAdButton}, 클릭 흐름은
 * {@link claimPersonality}로 꺼내 계측하고(하니스가 정적 렌더라 클릭이 안 돈다 — 홈의 `claimDebtWaiver` 선례),
 * 렌더 테스트는 그 술어가 실제로 마크업에 연결됐는지만 본다.
 */

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api')>()),
  adRefreshPersonality: vi.fn(),
  selectPersonality: vi.fn(),
}));
vi.mock('./toss', () => ({
  // 지면 분리 가드 — 책방이 쓰는 건 성향 그룹뿐이다. 부채 지우개 그룹을 빈 값으로 둬서, 책방이 그쪽으로
  // 되돌아가면 config-gate(`adGroupId !== ''`)에 걸려 아래 광고 버튼 렌더 단언이 깨지게 한다.
  REWARD_AD_GROUP_ID: '',
  PERSONALITY_AD_GROUP_ID: 'test-ad-group',
  watchRewardAd: vi.fn(),
  GOAL_MET_TEMPLATE_CODE: 'test-template',
  notificationAgreementSupported: vi.fn(),
  requestNotificationAgreement: vi.fn(),
  trackEvent: vi.fn(),
  openExternal: vi.fn(),
  tossLogin: vi.fn(),
}));

const adRefreshMock = vi.mocked(adRefreshPersonality);
const selectMock = vi.mocked(selectPersonality);
const watchRewardAdMock = vi.mocked(watchRewardAd);

beforeEach(() => {
  vi.clearAllMocks();
});

function render(node: React.ReactNode) {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

function user(loginId: string, extra: Partial<UserRow> = {}): UserRow {
  return { loginId, nickname: `${loginId}님`, publicBookCount: 2, following: false, self: false, ...extra };
}

function profile(extra: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    loginId: 'goospel',
    nickname: '구스펠',
    profileCharacterCode: null,
    followerCount: 3,
    followingCount: 5,
    following: false,
    self: false,
    personality: '한 작가를 깊게 파는 독자',
    personalityTags: [
      { label: '한우물형', clickable: true },
      { label: '완독률 80%', clickable: false },
    ],
    books: [],
    ...extra,
  };
}

const NOW = Date.parse('2026-08-16T12:00:00Z');

function book(id: number, title: string, lastStoryAt: string | null = null): ProfileBook {
  return { id, title, author: '저자', coverUrl: null, status: '다 읽음', seconds: 600, purchaseLink: null, lastStoryAt };
}

function card(
  p: ProfileResponse,
  books: ProfileBook[] = [],
  activeTag: string | null = null,
  view: {
    personalityStatus?: PersonalityStatus | null;
    earnedRetry?: boolean;
    personalityNotice?: string | null;
    archiveOpen?: boolean;
    /** 카운트 클릭 핸들러를 건넸는가 — 내 책방(탭 루트)에서만 셸이 준다. */
    openFollowList?: boolean;
    /** 「돌아가기」의 대상 — 탭 루트에는 없다(출구가 탭바다). */
    back?: boolean;
    header?: React.ReactNode;
    /** 격자 탭으로 여백을 여는 손잡이 — 책방 셸이 준다. */
    openMargin?: boolean;
    /** 걸린 상태 필터 — 기본은 「전체」(null). 클릭이 안 도는 하니스라 결과 상태를 밖에서 준다. */
    statusFilter?: BookStatus | null;
  } = {},
) {
  return render(
    <ProfileCard
      profile={p}
      books={books}
      activeTag={activeTag}
      now={NOW}
      onOpenMargin={view.openMargin === false ? undefined : () => {}}
      busy={false}
      personalityStatus={view.personalityStatus ?? null}
      adBusy={false}
      earnedRetry={view.earnedRetry ?? false}
      personalityNotice={view.personalityNotice ?? null}
      archiveOpen={view.archiveOpen ?? false}
      onArchive={() => {}}
      onSelectPersonality={() => {}}
      onClaimPersonality={() => {}}
      onRetryPersonality={() => {}}
      onFollowToggle={() => {}}
      onSelectTag={() => {}}
      statusFilter={view.statusFilter ?? null}
      onSelectStatus={() => {}}
      onMore={() => {}}
      safety={null}
      header={view.header}
      onOpenFollowList={view.openFollowList === true ? () => {} : undefined}
      onBack={view.back === false ? undefined : () => {}}
    />,
  );
}

/** 관문 통과 상태(첫 분석) — 각 테스트는 여기서 한 필드만 어긋내 그 필드의 책임을 잰다. */
function status(extra: Partial<PersonalityStatus> = {}): PersonalityStatus {
  return { coldStart: false, hasSelected: false, adRefreshRemaining: 10, adRefreshLimit: 10, ...extra };
}

function entry(
  id: number,
  generatedAt: string | null,
  selected = false,
  extra: Partial<PersonalityEntry> = {},
): PersonalityEntry {
  return {
    id,
    narrative: `${id}번 서술`,
    generatedAt,
    generatedAtLabel: '2026-08-15 10:00',
    selected,
    stale: false,
    ...extra,
  };
}

function mutation(entries: PersonalityEntry[], narrative: string | null = '새 성향'): PersonalityMutation {
  return {
    view: { state: narrative === null ? 'FALLBACK' : 'READY', narrative, entries },
    refreshRemaining: 9,
    refreshLimit: 10,
  };
}

describe('사용자 목록', () => {
  it('한 줄에 닉네임·핸들·공개 책 수를 함께 그린다 — 누구인지 감을 잡고 책방으로 들어간다', () => {
    const markup = render(<UserList users={[user('goospel')]} emptyMessage="없어요" onSelect={() => {}} />);

    expect(markup).toContain('goospel님');
    expect(markup).toContain('@goospel');
    expect(markup).toContain('공개 책 2권');
  });

  it('목록이 비면 빈 상태 문구를 그린다 — 팔로우 0명이 막다른 길이 되지 않게', () => {
    const markup = render(<UserList users={[]} emptyMessage="아직 팔로우한 사람이 없어요" onSelect={() => {}} />);

    expect(markup).toContain('아직 팔로우한 사람이 없어요');
  });
});

/**
 * 책방 탭 루트 — 탭을 누르면 곧장 내 책방이다(옛 소셜 중간 화면은 사라졌다).
 * `login_id=null` 경계(설계 §5-1)는 여기가 물려받았다: 그 계정은 자기 책방 자체가 없어 만들 길이 있어야 한다.
 */
describe('책방 탭 루트', () => {
  const shop = (myLoginId: string | null) =>
    render(<Bookshop myLoginId={myLoginId} onHandleCreated={() => {}} onError={() => {}} />);

  it('핸들이 있으면 내 책방(Profile)을 그리고 상단에 검색 진입바를 얹는다', () => {
    const markup = shop('goospel');

    // Profile은 아직 로딩 분기다(정적 렌더라 fetch가 안 돈다) — 그 분기에서도 header가 서야
    // 탭 진입 직후 상단이 텅 비지 않는다.
    expect(markup).toContain('아이디로 친구 찾기');
  });

  it('핸들이 없으면 만들 길과 검색 진입바를 함께 준다 — 남의 책방 구경은 핸들 없이도 된다', () => {
    const markup = shop(null);

    expect(markup).toContain('아이디 만들기');
    expect(markup).toContain('아이디로 친구 찾기');
    // 토스로 가입한 계정은 비밀번호가 없어 웹 로그인 자체가 불가능하다 — 그 안내로 되돌아가면 회귀다.
    expect(markup).not.toContain('booktimer.app');
  });
});

/**
 * 팔로워/팔로잉 시트 — 카운트를 누르면 열린다. 데이터는 프롭이다(셸이 받는다): 내부 fetch면 정적 렌더
 * 하니스가 0명/N명 분기에 영영 못 닿는다(`ArchiveSheet`와 같은 이유).
 */
describe('팔로우 목록 시트 (FollowListSheet)', () => {
  const sheet = (type: FollowListType, users: UserRow[] | null) =>
    render(
      <FollowListSheet
        type={type}
        users={users}
        error={null}
        onSelect={() => {}}
        onClose={() => {}}
        onRetry={() => {}}
      />,
    );

  it('팔로워 0명이면 그 타입의 빈 문구를 준다 — 흰 화면이 막다른 길이 되지 않게', () => {
    const markup = sheet('followers', []);

    expect(markup).toContain('아직 나를 팔로우한 사람이 없어요.');
  });

  it('팔로잉 0명이면 찾아보라는 문구로 갈린다 — 타입별 문구가 뒤바뀌면 안내가 거짓말이 된다', () => {
    const markup = sheet('following', []);

    expect(markup).toContain('아직 팔로우한 사람이 없어요');
    expect(markup).not.toContain('아직 나를 팔로우한 사람이 없어요.');
  });

  /**
   * 지시어는 지금 화면에 있는 것을 가리켜야 한다 — 검색이 별도 시트로 빠지면서 옛 문구의 「위에서」가
   * 가리키던 인라인 검색바는 이 시트에 덮여 보이지 않는다. 갈 곳을 이름으로 부른다.
   */
  it('찾는 길을 「친구 찾기」라는 이름으로 알려준다 — 시트에 덮인 「위에서」는 가리킬 대상이 없다', () => {
    const markup = sheet('following', []);

    expect(markup).toContain('「친구 찾기」');
    expect(markup).not.toContain('위에서');
  });

  it('사람이 있으면 목록 줄로 그린다 — 눌러서 그 책방으로 들어간다', () => {
    const markup = sheet('followers', [user('goospel')]);

    expect(markup).toContain('@goospel');
    expect(markup).toContain('<button');
  });

  it('아직 못 받았으면 로딩 — 빈 목록 문구를 먼저 깜빡이지 않는다', () => {
    expect(sheet('followers', null)).toContain('불러오는 중');
  });
});

/** 핸들 시트 — 경고가 여기 없으면 사용자가 평생 1번뿐인 선택을 모르고 한다. */
describe('핸들 만들기 시트', () => {
  const sheet = () => render(<HandleSheet onClose={() => {}} onCreated={() => {}} onFail={() => {}} />);

  it('평생 1번만 바꿀 수 있다고 미리 알린다', () => {
    expect(sheet()).toContain('평생 1번');
  });

  it('입력과 만들기 버튼을 준다 — 시트 안에서 끝난다', () => {
    const markup = sheet();

    expect(markup).toContain('<input');
    expect(markup).toContain('만들기');
  });
});

/**
 * 핸들 바꾸기 시트 — 같은 컴포넌트의 변형(`change`에 지금 핸들을 주면 전환된다). 시트 열림 가지에
 * 프롭으로 닿는 이유는 이 파일의 다른 시트들과 같다: 정적 렌더 하니스가 클릭을 못 잡는다(T-149).
 */
describe('핸들 바꾸기 시트', () => {
  const sheet = () =>
    render(<HandleSheet change="goospel" onClose={() => {}} onCreated={() => {}} onFail={() => {}} />);

  it('만들기가 아니라 바꾸기라고 말한다 — 제목·버튼이 함께 전환된다', () => {
    const markup = sheet();

    expect(markup).toContain('@아이디 바꾸기');
    expect(markup).toContain('평생 1번, 바꾸기');
  });

  it('되돌릴 수 없다는 것과 옛 아이디를 다시 못 쓴다는 것을 먼저 말한다', () => {
    const markup = sheet();

    expect(markup).toContain('되돌릴 수 없');
    expect(markup).toContain('다시 쓸 수 없');
  });
});

describe('책방 (프로필)', () => {
  it('남의 책방에는 팔로우 버튼이 있고, 이미 팔로우 중이면 취소로 바뀐다', () => {
    expect(card(profile({ following: false }))).toContain('팔로우');
    expect(card(profile({ following: false }))).not.toContain('팔로우 취소');
    expect(card(profile({ following: true }))).toContain('팔로우 취소');
  });

  /**
   * 신고·차단은 <b>드문 안전장치</b>다 — 팔로우와 나란한 큰 글자 버튼이면 첫인상이 방어적이다.
   * 여백 카드의 ⋯ 문법으로 강등하고, 무엇을 여는지는 `aria-label`이 진다(스크린리더가 유일한 독자다).
   */
  it('남의 책방의 신고·차단은 ⋯ 버튼이다 — 글자 버튼으로 서지 않는다', () => {
    const markup = card(profile());

    expect(markup).toContain('aria-label="신고·차단"');
    expect(markup).not.toContain('>신고·차단<');
  });

  it('내 책방에는 팔로우·차단/신고 진입이 없다 — 서버가 400으로 거절하는 동작이라 화면에서 먼저 막는다', () => {
    const markup = card(profile({ self: true }));

    expect(markup).not.toContain('팔로우');
    expect(markup).not.toContain('신고·차단');
    expect(markup).toContain('구스펠'); // 헤더 자체는 그대로 보인다
  });

  it('책BTI 태그는 clickable인 것만 누를 수 있다 — 서버가 근거 책을 주는 태그만 드릴다운된다', () => {
    const markup = card(profile());

    expect(markup).toContain('한우물형');
    expect(markup).toContain('완독률 80%');
    expect(markup.match(/<button[^>]*>한우물형/)).not.toBeNull();
    expect(markup.match(/<button[^>]*>완독률 80%/)).toBeNull();
  });

  it('태그를 고르면 그 태그의 근거 책만 남고 전체로 돌아갈 길을 준다', () => {
    const markup = card(profile(), [book(1, '자바 최적화')], '한우물형');

    expect(markup).toContain('자바 최적화');
    expect(markup).toContain('전체 보기');
  });

  it('공개한 책이 없으면 빈 목록 대신 안내를 그린다', () => {
    expect(card(profile(), [])).toContain('공개한 책이 없어요');
  });

  /**
   * 격자 칸은 표지와 제목만 진다 — 캐러셀 아래 있던 「상태 · 읽은 시간」 자리가 없어졌다(승인된 교환).
   * 상태 구분은 <b>필터 칩이 전담</b>하므로 「다 읽음」이라는 말은 화면에 칩 하나로만 존재해야 한다:
   * 다 읽은 책을 2권 넣어도 등장이 1회면 칸에는 안 실린 것이고, 칸마다 실렸다면 3회가 된다.
   */
  it('공개 책은 격자에 제목만 그린다 — 상태는 칸이 아니라 필터 칩이 진다', () => {
    const markup = card(profile(), [book(1, '자바 최적화'), book(2, '데미안')]);

    expect(markup).toContain('자바 최적화');
    expect(markup).toContain('데미안');
    expect(markup.split('다 읽음').length - 1).toBe(1);
  });

  it('공개 책도 표지를 그린다 — 없으면 제목 첫 글자 자리 표지로 대신한다', () => {
    expect(card(profile(), [{ ...book(1, '자바 최적화'), coverUrl: 'https://img/java.jpg' }])).toContain(
      'src="https://img/java.jpg"',
    );
    expect(card(profile(), [book(1, '자바 최적화')])).not.toContain('<img');
  });
});

/**
 * 책방 책 목록 — 캐러셀 + 「펼쳐보기」 시트를 <b>3열 격자 인라인</b>으로 바꿨다(사용자 승인 B안).
 *
 * <p>캐러셀은 한 번에 한 권만 보여 주고 나머지는 「펼쳐보기」를 눌러야 했다 — 책장을 훑는 화면인데
 * 왕복이 끼어 있었다. 격자는 그 시트를 본문에 그대로 펼친 것이라 새로 만든 물건이 없다.
 *
 * <p>⚠️ <b>상태·읽은 시간은 사라진다</b>(캐러셀 아래 메타 자리가 격자엔 없다). 알고 한 교환이다.
 */
describe('책방 책 목록 — 3열 격자', () => {
  const books = [book(1, '자바 최적화'), book(2, '데미안')];

  it('책들을 격자로 한 번에 그린다 — 「펼쳐보기」를 누르지 않아도 다 보인다', () => {
    const markup = card(profile(), books);

    expect(markup).toContain('data-grid-title="자바 최적화"');
    expect(markup).toContain('data-grid-title="데미안"');
  });

  it('캐러셀은 남지 않는다 — 두 목록이 같은 화면에 겹쳐 서면 어느 쪽이 진짜인지 흐려진다', () => {
    expect(card(profile(), books)).not.toContain('data-selected-book');
  });

  it('「펼쳐보기」 손잡이가 사라진다 — 격자가 곧 그 시트라 열 곳이 없다', () => {
    expect(card(profile(), books)).not.toContain('펼쳐보기');
  });

  it('한 권도 없으면 격자 대신 안내를 그린다', () => {
    const markup = card(profile(), []);

    expect(markup).toContain('공개한 책이 없어요');
    expect(markup).not.toContain('data-grid-title');
  });

  it('태그를 고르면 그 근거 책만 격자에 남는다 — 목록의 출처는 하나다', () => {
    const markup = card(profile(), [book(1, '자바 최적화')], '한우물형');

    expect(markup).toContain('한우물형 근거 책 1');
    expect(markup).toContain('data-grid-title="자바 최적화"');
    expect(markup).not.toContain('data-grid-title="데미안"');
  });

  /**
   * 격자 칸이 <b>그 책의 여백</b>으로 들어가는 문이 됐다 — 「보기만 하는 목록」이던 시절의 규율은
   * 그대로다: 열어 줄 손잡이가 없으면 버튼으로도 만들지 않는다.
   */
  it('여백을 열 손잡이를 받으면 칸이 버튼이 된다', () => {
    expect(card(profile(), [book(1, '자바 최적화')])).toContain('<button');
  });

  it('24시간 안에 새 글이 달린 책만 발광한다 — 판정은 서버가 준 시각으로 클라가 잰다', () => {
    const markup = card(profile(), [
      book(1, '자바 최적화', new Date(NOW - 3_600_000).toISOString()),
      book(2, '데미안', new Date(NOW - 30 * 3_600_000).toISOString()),
      book(3, '코스모스'),
    ]);

    expect(markup.match(/data-fresh-dot/g)).toHaveLength(1);
    expect(markup).toContain('aria-label="자바 최적화 새 글"');
  });
});

/**
 * 공개 책 상태 필터 — 웹 책장의 <b>필터 축만</b> 이식했다(배지·정렬은 안 온다).
 *
 * <p>기본이 「전체」라 지금 화면·발광·여백 문이 그대로 보존되고, 구분이 필요한 순간에만 사용자가 좁힌다.
 * 어휘는 서재 탭 `SECTIONS`를 그대로 재사용한다 — 같은 앱 안에서 서재와 필터가 다른 말을 쓰면 안 된다.
 *
 * <p>필터를 건 뒤의 fetch 배선은 정적 렌더 하니스로 못 잡는다(클릭·effect가 안 돈다 — 태그 드릴다운과 같다).
 * 여기서는 순수 함수 {@link shelfTitle}과 걸린 상태를 프롭으로 넣었을 때의 마크업만 계측한다.
 */
describe('공개 책 상태 필터', () => {
  describe('소제목 (shelfTitle)', () => {
    // 상단 스탯이 「공개 책 N」을 이미 말한다 — 바로 아래 소제목이 같은 숫자를 또 말하면 잉여다.
    it('아무것도 안 걸렸으면 숫자를 접는다 — 상단 스탯이 이미 그 수를 말했다', () => {
      expect(shelfTitle(null, null, 3)).toBe('공개한 책');
      expect(shelfTitle(null, null, 0)).toBe('공개한 책');
    });

    it('상태가 걸리면 그 상태의 이름과 권수를 말한다 — 배지가 없으니 소제목이 상태를 진다', () => {
      expect(shelfTitle(null, 'FINISHED', 2)).toBe('다 읽음 2');
      expect(shelfTitle(null, 'WANT_TO_READ', 0)).toBe('읽고 싶어요 0');
    });

    it('태그 드릴다운이면 근거 책 문구다', () => {
      expect(shelfTitle('한우물형', null, 1)).toBe('한우물형 근거 책 1');
    });

    // 상태셸이 태그 진입 때 필터를 리셋하므로 실제로는 도달하지 않는다 — 함수 단독 계약을 못 박아 둔다.
    it('둘 다 걸리면 태그가 이긴다', () => {
      expect(shelfTitle('한우물형', 'FINISHED', 1)).toBe('한우물형 근거 책 1');
    });
  });

  it('칩 넷을 그리고 기본 활성은 「전체」다 — 진입 화면이 지금과 같아야 발광·여백 문이 안 사라진다', () => {
    const markup = card(profile(), [book(1, '자바 최적화')]);

    expect(markup).toContain('전체');
    expect(markup).toContain('읽는 중');
    expect(markup).toContain('다 읽음');
    expect(markup).toContain('읽고 싶어요');
    expect(markup.match(/aria-pressed="true"/g)).toHaveLength(1);
  });

  it('상태를 고르면 그 칩만 눌린 상태다', () => {
    const markup = card(profile(), [book(1, '자바 최적화')], null, { statusFilter: 'FINISHED' });

    expect(markup.match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(markup).toContain('다 읽음 1'); // 소제목이 상태를 말한다
  });

  it('태그 드릴다운 중에는 칩 줄을 숨긴다 — 두 축은 배타다(출구는 「전체 보기」 하나)', () => {
    const markup = card(profile(), [book(1, '자바 최적화')], '한우물형');

    expect(markup).not.toContain('읽고 싶어요');
    expect(markup).not.toContain('aria-pressed');
  });

  it('필터가 걸린 채 비면 상태용 빈 문구다 — 「공개한 책이 없어요」는 거짓말이 된다', () => {
    const markup = card(profile(), [], null, { statusFilter: 'WANT_TO_READ' });

    expect(markup).toContain('이 상태의 공개 책이 없어요');
    expect(markup).not.toContain('공개한 책이 없어요');
  });
});

/**
 * 팔로워/팔로잉 카운트 클릭 — 서버 `GET /api/follow-list`는 `Principal` 본인 목록만 준다.
 * 남의 책방에서 카운트를 버튼으로 만들면 눌러도 (서버가 안 주는) 목록을 못 여는 죽은 UI가 된다.
 */
describe('카운트 클릭 가능 판정 (followCountsOpenable)', () => {
  it('내 책방 + 열어 줄 핸들러가 있으면 누를 수 있다', () => {
    expect(followCountsOpenable(true, true)).toBe(true);
  });

  it('남의 책방이면 못 누른다 — 서버가 남의 팔로우 목록을 주지 않는다', () => {
    expect(followCountsOpenable(false, true)).toBe(false);
  });

  it('핸들러가 없으면 못 누른다 — 열 곳이 없는 버튼은 죽은 UI다', () => {
    expect(followCountsOpenable(true, false)).toBe(false);
  });
});

/** 술어가 마크업에 실제로 배선됐는지 — 함수만 맞고 배선이 빠지면 화면엔 아무 일도 안 일어난다. */
describe('책방 헤더 — 카운트 줄', () => {
  // 버튼임의 표식은 이제 `aria-label`이다 — 라벨과 숫자가 각자 span으로 갈려(테두리 버튼) 글자가 이어 붙지 않는다.
  it('내 책방(핸들러 있음)이면 팔로워·팔로잉이 버튼이다', () => {
    const markup = card(profile({ self: true }), [], null, { openFollowList: true });

    expect(markup).toContain('aria-label="팔로워 3명 보기"');
    expect(markup).toContain('aria-label="팔로잉 5명 보기"');
  });

  it('남의 책방이면 누를 수 없는 숫자로 남는다 — 눌러도 열 목록이 없다', () => {
    const markup = card(profile({ self: false }), [], null, { openFollowList: true });

    expect(markup).toContain('data-stat="팔로워">3<');
    expect(markup).not.toContain('명 보기');
  });

  it('내 책방이어도 핸들러를 안 받았으면 못 누른다 — 남의 책방 렌더 경로가 그대로 살아 있다', () => {
    const markup = card(profile({ self: true }), [], null, { openFollowList: false });

    expect(markup).toContain('data-stat="팔로워">3<');
    expect(markup).not.toContain('명 보기');
  });

  it('셸이 준 header(여백·검색)를 제목 아래 카운트 줄 위에 끼운다', () => {
    const markup = card(profile(), [], null, { header: <i>헤더슬롯</i> });

    expect(markup).toContain('헤더슬롯');
    expect(markup.indexOf('헤더슬롯')).toBeLessThan(markup.indexOf('@goospel'));
  });
});

/**
 * 팔로워·팔로잉 손잡이 — 밑줄 친 작은 글자라 눌리는 것으로 안 읽혔고(#788에서 지운 ← 와 같은 종류),
 * 손가락 표적도 작았다. 테두리 있는 큰 버튼으로 세운다.
 */
describe('팔로우 카운트 버튼 (FollowCountButton)', () => {
  it('스크린리더가 읽을 이름을 단다 — 「팔로워」와 숫자가 따로 놓여 이름 없는 버튼이 되기 쉽다', () => {
    const markup = render(<FollowCountButton label="팔로워" count={4} onClick={() => {}} />);

    expect(markup).toContain('<button');
    expect(markup).toContain('aria-label="팔로워 4명 보기"');
    expect(markup).toContain('4');
  });
});

/**
 * 상단 재배치 — @아이디는 <b>제목 바로 아래</b>로 내려 닉네임에 붙이고, 카운트는 그 아래 버튼 두 개다.
 * 셋이 한 줄에 섞여 있으면 @아이디가 카운트의 일부처럼 읽힌다(사용자 제보).
 */
describe('책방 헤더 — @아이디와 카운트 버튼', () => {
  it('내 책방이면 @아이디가 카운트 줄 아래 이름 옆에 따로 선다(인스타 배치)', () => {
    const markup = card(profile({ self: true }), [], null, { openFollowList: true });

    expect(markup).toContain('aria-label="팔로워 3명 보기"');
    expect(markup).toContain('aria-label="팔로잉 5명 보기"');
    expect(markup.indexOf('팔로워 3명 보기')).toBeLessThan(markup.indexOf('@goospel'));
    // 「@goospel ·」로 이어 붙던 옛 한 줄 — 되살아나면 아이디가 카운트에 섞여 다시 안 보인다.
    expect(markup).not.toContain('@goospel ·');
  });

  it('남의 책방은 같은 줄을 쓰되 버튼이 아니다 — 서버가 남의 목록을 안 주므로 누를 수 없다(#788)', () => {
    const markup = card(profile({ self: false }), [], null, { openFollowList: true });

    expect(markup).toContain('data-stat="팔로워">3<');
    expect(markup).toContain('data-stat="팔로잉">5<');
    expect(markup).not.toContain('명 보기');
  });
});

/**
 * 인스타식 상단(사용자 시안) — 큰 제목 「…님의 책방」을 지우고 <b>아바타 + 카운트 3개를 한 줄</b>에,
 * 그 아래 닉네임·@아이디·성향(bio)을 놓는다.
 *
 * <p>이유는 자리다: 제목·검색바·여백·카운트 버튼·10줄 성향 문단이 화면 위를 다 먹어, 정작 이 화면의
 * 본체인 「공개한 책」이 탭바 아래로 밀려 잘렸다(사용자 스크린샷). 위를 눌러 책을 첫 화면으로 끌어올린다.
 */
describe('책방 신원 블록 — 인스타식 상단', () => {
  it('큰 제목을 그리지 않는다 — 닉네임은 이름 줄로 내려온다', () => {
    const markup = card(profile());

    expect(markup).not.toContain('님의 책방');
    expect(markup).toContain('구스펠');
  });

  it('공개 책 수는 태그로 걸러진 목록이 아니라 profile.books를 센다', () => {
    // 태그를 고르면 `books`는 근거 책만 남는다 — 그걸 세면 상단 카운트가 필터마다 요동친다.
    const markup = card(profile({ books: [book(1, '가'), book(2, '나'), book(3, '다')] }), [book(1, '가')], '한우물형');

    expect(markup).toContain('data-stat="공개 책">3<');
  });

  it('공개 책 카운트는 버튼이 아니다 — 눌러도 열 목록이 없다', () => {
    const markup = card(profile({ self: true, books: [book(1, '가')] }), [], null, { openFollowList: true });

    expect(markup).toContain('data-stat="공개 책">1<');
    expect(markup).not.toContain('공개 책 1명 보기');
  });

  it('긴 성향에는 「더보기」를 단다 — 3줄만 남기고 접어야 책이 첫 화면에 남는다', () => {
    const markup = card(profile({ personality: '가'.repeat(BIO_CLAMP_CHARS + 1) }));

    expect(markup).toContain('더보기');
  });

  it('짧은 성향에는 안 단다 — 눌러도 아무것도 안 펼쳐지는 죽은 버튼이 된다', () => {
    expect(card(profile({ personality: '한 작가를 깊게 파는 독자' }))).not.toContain('더보기');
  });

  it('태그가 서술보다 먼저 온다 — 그 사람을 한눈에 요약하는 세 단어가 다섯 줄 뒤에 있으면 안 읽힌다', () => {
    const markup = card(profile({ personality: '밑줄을 아끼지 않는 완독형이에요.' }));

    expect(markup.indexOf('한우물형')).toBeGreaterThan(-1);
    expect(markup.indexOf('한우물형')).toBeLessThan(markup.indexOf('밑줄을 아끼지 않는'));
  });

  it('서술은 카드에 담긴다 — 문단이 화면에 그냥 떠 있으면 벽처럼 읽힌다', () => {
    const markup = card(profile({ personality: '밑줄을 아끼지 않는 완독형이에요.' }));

    expect(markup).toContain('data-bio-card=""');
  });

  it('성향이 없으면 빈 카드를 세우지 않는다 — 테두리만 남은 상자가 생긴다', () => {
    expect(card(profile({ personality: null }))).not.toContain('data-bio-card=""');
  });
});

/** 「더보기」 노출 판정 — CSS clamp는 넘칠 때만 자르므로, 손잡이도 넘칠 때만 서야 짝이 맞는다. */
describe('성향 접기 판정 (needsBioToggle)', () => {
  it('성향이 없으면 손잡이도 없다', () => {
    expect(needsBioToggle(null)).toBe(false);
  });

  it('임계 길이 딱 맞으면 안 단다 — 3줄에 들어간다', () => {
    expect(needsBioToggle('가'.repeat(BIO_CLAMP_CHARS))).toBe(false);
  });

  it('임계를 한 글자라도 넘기면 단다', () => {
    expect(needsBioToggle('가'.repeat(BIO_CLAMP_CHARS + 1))).toBe(true);
  });
});

/**
 * 상단 도구 — <b>전폭 검색바 하나</b>다(2026-08-18). 여백 스트립이 서 있던 왼쪽 자리는 그 전에
 * 사라졌고(「새 글」 신호가 사람 단위(링) → <b>책 단위(격자 발광)</b>, 2026-08-16), 그 뒤로 아이콘이
 * <b>오른쪽 끝에 정렬됐는데 왼쪽은 빈</b> 줄로 홀로 남아 이질적이었다.
 *
 * <p>2026-08-16엔 반대로 판단했었다 — 전폭 알약이 "그 줄만큼 책을 아래로 민다"고 보고 아이콘으로 줄였다.
 * 그 논거는 지금 형태엔 안 맞는다: 캡션이 붙으면서 <b>아이콘 쪽이 세로로 더 먹는다</b>(원 56 + 여백 4 +
 * 캡션 ≈ 80px 대 바 44px). 그래서 전폭으로 되돌리되 세로는 오히려 줄어든다.
 *
 * <p>단 <b>입력창이 아니다</b> — 생김새만 검색바고 누르면 시트가 열린다. 인라인 입력으로 만들면 결과
 * 패널이 내 책방 본문을 통째로 갈아끼워야 하는데, 그게 애초에 검색을 시트로 뺀 이유다.
 */
describe('책방 상단 도구 (BookshopHeader)', () => {
  const header = () => render(<BookshopHeader onSearch={() => {}} />);

  it('검색 진입이 선다', () => {
    expect(header()).toContain('aria-label="아이디로 친구 찾기"');
  });

  it('문구를 본문에 그린다 — 무엇을 찾는 자리인지가 눌러 보기 전에 서야 한다', () => {
    // 아이콘 시절엔 이 문구가 aria-label 안에만 살고, 화면엔 11px 캡션 「친구 찾기」만 있었다.
    expect(header()).toContain('>아이디로 친구 찾기<');
  });

  it('입력창이 아니라 시트를 여는 버튼이다 — 인라인 입력은 결과 패널이 본문을 갈아끼운다', () => {
    expect(header()).toContain('<button');
    expect(header()).not.toContain('<input');
  });

  it('여백 스트립은 남지 않는다 — 링(사람 단위 새 글)은 격자 발광으로 대체됐다', () => {
    expect(header()).not.toContain('여백 적기');
    expect(header()).not.toContain('내 여백');
  });
});

/**
 * 남의 책방에는 검색 진입이 없다 — 남의 전시장은 친구를 찾는 자리가 아니다(2026-08-18 사용자 결정).
 *
 * <p>실현 수단은 셸이다: 남의 책방 분기에 `header`를 아예 안 넘긴다. 그러니 이 화면이 스스로 검색바를
 * 그리기 시작하면 그 배선이 조용히 무력해진다 — 전폭으로 커진 지금은 「상단이 허전하니 여기 두자」는
 * 유혹이 실제로 생기는 자리라 더 그렇다. 부정 단언 홀로면 항상 참이라, 짝이 되는 긍정을 함께 세운다.
 */
describe('남의 책방 — 검색 진입 없음', () => {
  it('header를 안 받으면 검색 진입을 그리지 않는다', () => {
    expect(card(profile())).not.toContain('아이디로 친구 찾기');
  });

  it('header를 받으면 그린다 — 위 단언이 늘 참인 빈 껍데기가 아님을 보인다', () => {
    const markup = card(profile({ self: true }), [], null, {
      header: <BookshopHeader onSearch={() => {}} />,
    });

    expect(markup).toContain('아이디로 친구 찾기');
  });
});

/** 상단 도구는 화면 제목보다 위다 — 「…님의 책방」 아래에 검색창이 오는 순서가 어색했다. */
describe('책방 탭 루트 — 상단 도구가 제목보다 위', () => {
  const shop = (myLoginId: string | null) =>
    render(<Bookshop myLoginId={myLoginId} onHandleCreated={() => {}} onError={() => {}} />);

  it('내 책방(로딩 분기)에서도 검색 진입바가 「책방」 제목보다 앞에 온다', () => {
    const markup = shop('me');

    expect(markup).toContain('아이디로 친구 찾기');
    expect(markup.indexOf('아이디로 친구 찾기')).toBeLessThan(markup.indexOf('책방'));
  });

  it('핸들 없는 계정 화면도 같은 순서다 — 여기만 옛 배치로 남기 쉽다', () => {
    const markup = shop(null);

    expect(markup).toContain('아이디 만들기');
    expect(markup.indexOf('아이디로 친구 찾기')).toBeLessThan(markup.indexOf('책방'));
  });
});

/**
 * 남의 책방의 나가는 길 — **상단 「돌아가기」 하나**(2026-08-16).
 *
 * <p>이 화면은 같은 문제를 두 번 겪었다. 처음엔 제목 옆 `←` 글리프였는데 배경이 없어 버튼으로 안
 * 읽혀 **지우고 하단 버튼만** 남겼고, 그 뒤 여백 화면에서 같은 지적이 또 나와 **글자가 붙은 알약**이
 * 생겼다. 회피 이유가 사라졌으므로 위로 되돌리고 하단 버튼을 걷는다 — 앱 전체가 한 자리에서 나간다.
 *
 * <p>개수를 세는 것이 핵심이다: 위아래 둘이면 「통일」이 아니라 중복인데, 존재 단언만으로는 못 잡는다.
 * 탭 루트(내 책방)는 돌아갈 곳이 없어 아무것도 그리지 않는다.
 */
describe('책방 — 나가는 길', () => {
  it('「돌아가기」가 정확히 하나다 (남의 책방)', () => {
    expect(card(profile()).match(/돌아가기/g)).toHaveLength(1);
  });

  it('신원 블록보다 위에 온다 — 아래로 내려가서 찾지 않는다', () => {
    const markup = card(profile());

    expect(markup).toContain('돌아가기');
    expect(markup.indexOf('돌아가기')).toBeLessThan(markup.indexOf('구스펠'));
  });

  it('onBack이 없으면 그리지 않는다 (탭 루트) — 아무 데도 안 가는 손잡이는 남기지 않는다', () => {
    expect(card(profile({ self: true }), [], null, { back: false })).not.toContain('돌아가기');
  });
});

/**
 * 차단 2단계 확인 — 「차단하기」가 1탭 즉시 실행이었다. 차단은 되돌리기 비싸다(그 순간 상대 책방이
 * 404가 되고 이 화면도 닫힌다). 서재 삭제와 같은 패턴으로 한 탭 더 받는다.
 */
describe('차단 2단계 확인', () => {
  const safety = (confirmBlock: boolean) =>
    render(<SafetyPanel busy={false} confirmBlock={confirmBlock} onConfirmBlock={() => {}} onBlock={() => {}} onReport={() => {}} />);

  it('처음엔 「차단하기」만 — 확인 문구는 아직 없다', () => {
    const markup = safety(false);

    expect(markup).toContain('차단하기');
    expect(markup).not.toContain('정말 차단');
  });

  it('한 번 더 물어본 뒤 실행한다 — 물러설 길(취소)도 함께 준다', () => {
    const markup = safety(true);

    expect(markup).toContain('정말 차단');
    expect(markup).toContain('취소');
  });

  it('닫혀 있으면 열고, 열려 있으면 닫는다', () => {
    expect(toggleSafety(null)).toEqual({ confirmBlock: false });
    expect(toggleSafety({ confirmBlock: false })).toBeNull();
  });

  it('접었다 다시 펴면 확인이 풀려 있다 — 살아남으면 「정말 차단」이 한 탭 거리다(서재 `toggleOpen`과 같은 이유)', () => {
    expect(toggleSafety(toggleSafety({ confirmBlock: true }))).toEqual({ confirmBlock: false });
  });
});

/**
 * 성향 추출 광고 관문 — 내 책방에만 선다. 노출 조건은 넷의 AND라, 하나라도 어긋나면 광고가 안 보여야 한다.
 * 특히 `status === null`(조회 실패·서버 미배포)에서 숨는 것이 fail-closed의 핵심이다.
 */
describe('성향 광고 버튼 노출 (showPersonalityAdButton)', () => {
  it('내 책방 + 콜드스타트 아님 + 잔여 있음 + 광고 그룹 설정됨 → 보인다', () => {
    expect(showPersonalityAdButton(true, status(), 'ad-1')).toBe(true);
  });

  it('남의 책방에는 안 보인다 — 남의 성향을 내가 뽑을 수는 없다', () => {
    expect(showPersonalityAdButton(false, status(), 'ad-1')).toBe(false);
  });

  it('status를 못 받았으면 숨는다 — 조회 실패·서버 미배포에서 광고만 보고 보상 없는 사고를 막는다', () => {
    expect(showPersonalityAdButton(true, null, 'ad-1')).toBe(false);
  });

  it('콜드스타트(완독 0권)면 숨는다 — 분석이 보류되는 상태라 광고가 헛수고가 된다', () => {
    expect(showPersonalityAdButton(true, status({ coldStart: true }), 'ad-1')).toBe(false);
  });

  it('오늘 총량을 다 썼으면 숨는다 — 광고를 봐도 429가 돌아온다', () => {
    expect(showPersonalityAdButton(true, status({ adRefreshRemaining: 0 }), 'ad-1')).toBe(false);
  });

  it('광고 그룹 ID가 비면 숨는다 — 구글 등록 대기 중 빌드의 config-gate', () => {
    expect(showPersonalityAdButton(true, status(), '')).toBe(false);
  });
});

/**
 * 보관함 — 이 기능의 존재 이유는 "문구가 바뀌었는지 눈으로 확인"이다. 그래서 손잡이는 **비교 대상이 둘 이상일
 * 때만** 서고(1건이면 인라인 서술과 같은 정보라 열 이유가 없다), 시트는 서술을 **전문 그대로 나란히** 싣는다.
 */
describe('보관함 손잡이 노출 (showArchiveHandle)', () => {
  it('저장된 분석이 2건이면 보인다 — 비교할 짝이 생긴 시점', () => {
    expect(showArchiveHandle(true, status({ entries: [entry(1, '2026-08-15T10:00:00Z'), entry(2, '2026-08-14T10:00:00Z')] }))).toBe(true);
  });

  it('1건뿐이면 숨는다 — 열어도 책방에 이미 걸린 그 문구 하나뿐이라 빈 보관함과 같다', () => {
    expect(showArchiveHandle(true, status({ entries: [entry(1, '2026-08-15T10:00:00Z', true)] }))).toBe(false);
  });

  it('0건이면 숨는다 — 콜드스타트·첫 분석 전', () => {
    expect(showArchiveHandle(true, status({ entries: [] }))).toBe(false);
  });

  it('entries가 아예 없으면(옛 서버) 숨는다 — 필드 추가 전 서버와 새 번들이 만나는 구간의 fail-closed', () => {
    expect(showArchiveHandle(true, status())).toBe(false);
  });

  it('status를 못 받았으면 숨는다', () => {
    expect(showArchiveHandle(true, null)).toBe(false);
  });

  it('남의 책방에는 안 보인다 — 보관함은 내 분석 히스토리다', () => {
    expect(showArchiveHandle(false, status({ entries: [entry(1, '2026-08-15T10:00:00Z'), entry(2, '2026-08-14T10:00:00Z')] }))).toBe(false);
  });
});

/**
 * 시트 렌더 — "두 서술이 한 화면에 함께 있는가"가 이 기능의 성패다. 하나만 보이면 기능이 동작해도 실패다.
 */
describe('보관함 시트 (ArchiveSheet)', () => {
  const two = [
    entry(9, '2026-08-15T10:00:00Z', true, { narrative: '밑줄을 아끼지 않는 완독형', generatedAtLabel: '2026-08-15 19:00' }),
    entry(4, '2026-08-01T10:00:00Z', false, { narrative: '밤에 소설만 파는 몰입형', generatedAtLabel: '2026-08-01 19:00', stale: true }),
  ];

  function sheet(entries: PersonalityEntry[]) {
    return render(<ArchiveSheet entries={entries} busy={false} onSelect={() => {}} onClose={() => {}} />);
  }

  it('서술 전문을 둘 다 싣는다 — 나란히 놓고 차이를 보는 것이 이 화면의 목적', () => {
    const markup = sheet(two);

    expect(markup).toContain('밑줄을 아끼지 않는 완독형');
    expect(markup).toContain('밤에 소설만 파는 몰입형');
  });

  it('생성 시각 라벨로 어느 게 방금 것인지 가른다 — 비교의 축', () => {
    const markup = sheet(two);

    expect(markup).toContain('2026-08-15 19:00');
    expect(markup).toContain('2026-08-01 19:00');
  });

  it('대표 카드에는 배지가 서고 바꾸기 버튼이 없다 — 지금 걸린 문구를 다시 고를 일은 없다', () => {
    const markup = sheet(two);

    expect(markup).toContain('지금 대표');
    // 미대표 카드 하나에만 버튼 — 둘 다 그리면 대표가 어느 쪽인지 화면에서 안 갈린다.
    expect(markup.match(/이 성향으로 바꾸기/g)).toHaveLength(1);
  });

  it('stale 카드에 「지난 책장」 캡션 — 책이 안 바뀌면 문구도 안 바뀐다는 정신 모델에 직결', () => {
    expect(sheet(two)).toContain('지난 책장');
  });

  it('stale이 아닌 카드에는 그 캡션이 없다', () => {
    expect(sheet([two[0]])).not.toContain('지난 책장');
  });
});

/** 성공 안내 — 보관함이 생긴 뒤에는 "비교해 보라"까지 알려야 발견된다(손잡이만으론 안 보인다). */
describe('분석 성공 안내 문구 (personalityNoticeText)', () => {
  it('비교 대상이 2건 이상이면 보관함으로 안내한다', () => {
    expect(personalityNoticeText(false, 2)).toContain('보관함');
  });

  it('첫 분석(1건)이면 비교할 게 없으니 보관함을 말하지 않는다', () => {
    const text = personalityNoticeText(false, 1);

    expect(text).toContain('새 성향');
    expect(text).not.toContain('보관함');
  });

  it('실패면 실패 안내 — 보관함 이야기를 섞지 않는다', () => {
    const text = personalityNoticeText(true, 3);

    expect(text).toContain('다시 시도');
    expect(text).not.toContain('보관함');
  });
});

describe('최신 분석 찾기 (newestEntry)', () => {
  it('generatedAt이 가장 늦은 행을 고른다 — 대표 승격 대상', () => {
    const rows = [entry(3, '2026-08-15T10:00:00Z'), entry(1, '2026-08-13T10:00:00Z'), entry(2, '2026-08-14T10:00:00Z')];

    expect(newestEntry(rows)?.id).toBe(3);
  });

  it('히스토리가 비면 null — 승격할 대상이 없다', () => {
    expect(newestEntry([])).toBeNull();
  });

  it('generatedAt이 없는 행은 후보가 아니다 — 시각을 모르는 행을 대표로 올리면 엉뚱한 분석이 책방에 걸린다', () => {
    expect(newestEntry([entry(1, null), entry(2, '2026-08-10T00:00:00Z')])?.id).toBe(2);
    // 전부 시각이 없으면 고를 수 있는 행이 없다 — null을 빈 문자열로 눙치면 여기서 엉뚱한 행이 뽑힌다.
    expect(newestEntry([entry(1, null), entry(2, null)])).toBeNull();
  });
});

/**
 * 신뢰 경계 — "광고를 끝까지 봤다"는 신호 없이는 분석 API를 부르지 않는다. 서버가 광고 시청을
 * 검증할 수 없으므로(SDK에 서버사이드 검증 없음) 이 클라 측 규율이 관문의 실체다.
 */
describe('성향 추출 흐름 (claimPersonality)', () => {
  it('시청 완료면 ad-refresh를 부르고 결과를 돌려준다', async () => {
    watchRewardAdMock.mockResolvedValue(true);
    const result = mutation([entry(1, '2026-08-15T10:00:00Z', true)]);
    adRefreshMock.mockResolvedValue(result);

    await expect(claimPersonality('ad-1')).resolves.toEqual(result);
    expect(adRefreshMock).toHaveBeenCalledTimes(1);
  });

  it('중간 이탈이면 분석 API를 부르지 않고 null — 조용히 원상태', async () => {
    watchRewardAdMock.mockResolvedValue(false);

    await expect(claimPersonality('ad-1')).resolves.toBeNull();
    expect(adRefreshMock).not.toHaveBeenCalled();
  });

  it('광고 로드 실패면 분석 API를 부르지 않고 에러가 올라간다', async () => {
    watchRewardAdMock.mockRejectedValue(new Error('no fill'));

    await expect(claimPersonality('ad-1')).rejects.toThrow('no fill');
    expect(adRefreshMock).not.toHaveBeenCalled();
  });

  it('건네받은 adGroupId를 그대로 광고에 넘긴다', async () => {
    watchRewardAdMock.mockResolvedValue(false);

    await claimPersonality('ad-9');

    expect(watchRewardAdMock).toHaveBeenCalledWith('ad-9');
  });
});

/**
 * 대표 승격 체이닝 — 서버 `reanalyze`는 새 분석을 **후보로만** 추가한다(대표 불변). 미니앱엔 히스토리·선택
 * UI가 없어 그대로 두면 광고를 보고도 책방 표시가 안 바뀐다. 그래서 최신 행이 미선택이면 select를 잇는다.
 */
describe('대표 승격 체이닝 (runPersonalityRefresh)', () => {
  it('새 행이 미선택이면 그 id로 select를 잇는다 — 안 그러면 책방이 옛 성향 그대로다', async () => {
    adRefreshMock.mockResolvedValue(
      mutation([entry(7, '2026-08-15T10:00:00Z', false), entry(3, '2026-08-01T10:00:00Z', true)]),
    );

    await runPersonalityRefresh();

    expect(selectMock).toHaveBeenCalledWith(7);
  });

  it('첫 분석(새 행이 이미 대표)이면 select를 부르지 않는다 — 서버가 이미 대표로 뒀다', async () => {
    adRefreshMock.mockResolvedValue(mutation([entry(7, '2026-08-15T10:00:00Z', true)]));

    await runPersonalityRefresh();

    expect(selectMock).not.toHaveBeenCalled();
  });

  it('히스토리가 비어 오면 select 없이 결과만 돌려준다 (LLM 실패로 새 행이 안 생긴 경우)', async () => {
    const result = mutation([], null);
    adRefreshMock.mockResolvedValue(result);

    await expect(runPersonalityRefresh()).resolves.toEqual(result);
    expect(selectMock).not.toHaveBeenCalled();
  });
});

describe('분석 실패 판정과 문구', () => {
  it('narrative가 없으면 실패로 본다 — 광고는 이미 소비됐으니 화면이 무광고 재시도를 열어야 한다', () => {
    expect(analysisFailed(mutation([], null))).toBe(true);
  });

  it('서술이 오면 성공', () => {
    expect(analysisFailed(mutation([entry(1, '2026-08-15T10:00:00Z', true)]))).toBe(false);
  });

  it('429는 JSON 본문 대신 한글 안내로 바꾼다 — 서버가 준 평문이 아니라 구조체라 그대로 띄우면 안 된다', () => {
    expect(personalityErrorMessage(new ApiError(429, '{"error":"REFRESH_LIMIT_EXCEEDED"}'))).toContain('내일');
  });

  it('그 밖의 서버 에러는 서버 문구를 그대로 쓴다 (홈의 waiverErrorMessage 재사용)', () => {
    expect(personalityErrorMessage(new ApiError(400, '지금은 어려워요'))).toBe('지금은 어려워요');
  });

  it('SDK 광고 에러는 영문 기술 문구라 안내로 바꾼다', () => {
    expect(personalityErrorMessage(new Error('AdLoadFailed'))).toContain('광고');
  });
});

/**
 * 한 줄에 무엇이 서는가 — 광고 버튼(전폭)과 보관함(오른쪽 끝 알약)이 서로 다른 물건처럼 흩어져
 * 있던 것을 한 줄에 나란히 세운다(사용자 지적). 행의 유무와 칸 수가 한 곳에서 나와야 어긋나지 않는다.
 */
describe('성향 관문 손잡이 줄 (personalityActions)', () => {
  const both = status({
    hasSelected: true,
    entries: [entry(1, '2026-08-16T10:00:00Z', true), entry(2, '2026-08-15T10:00:00Z')],
  });

  it('둘 다 자격이 되면 광고 → 보관함 순으로 두 칸', () => {
    expect(personalityActions(true, both, 'g')).toEqual(['ad', 'archive']);
  });

  it('비교 대상이 1건이면 광고만 — 그 칸이 전폭을 가진다', () => {
    expect(personalityActions(true, status({ hasSelected: true, entries: [entry(1, '2026-08-16T10:00:00Z', true)] }), 'g')).toEqual(['ad']);
  });

  it('오늘 총량을 다 썼으면 보관함만 남는다', () => {
    expect(personalityActions(true, { ...both, adRefreshRemaining: 0 }, 'g')).toEqual(['archive']);
  });

  it('광고 그룹 ID가 없으면(config-gate) 광고 칸이 빠진다 — 목 모드가 이 경로다', () => {
    expect(personalityActions(true, both, '')).toEqual(['archive']);
  });

  it('둘 다 아니면 빈 줄을 그리지 않는다 — 여백만 남는 유령 줄 방지', () => {
    expect(personalityActions(true, status({ coldStart: true }), 'g')).toEqual([]);
    expect(personalityActions(false, both, 'g')).toEqual([]); // 남의 책방
  });
});

/** 술어가 실제로 마크업에 연결됐는지 — 순수 함수만 맞고 배선이 빠지면 화면엔 아무 일도 안 일어난다. */
describe('내 책방의 성향 관문 렌더', () => {
  const me = (extra: Partial<ProfileResponse> = {}) => profile({ self: true, ...extra });

  it('첫 분석이면 「광고 보고 성향 분석 받기」 — 문구에 광고를 명시한다(광고 위장 금지)', () => {
    const markup = card(me({ personality: null }), [], null, { personalityStatus: status() });

    expect(markup).toContain('광고 보고 성향 분석 받기');
  });

  it('대표가 이미 있으면 「광고 보고 다시 분석하기」로 갈린다', () => {
    const markup = card(me(), [], null, { personalityStatus: status({ hasSelected: true }) });

    expect(markup).toContain('광고 보고 다시 분석하기');
  });

  /**
   * 관문 손잡이 둘은 <b>한 줄에 나란히</b> — 광고 버튼이 전폭, 보관함이 그 아래 오른쪽 끝 알약이라
   * 서로 다른 물건처럼 흩어져 있었다(사용자 지적). 둘 다 "성향을 다루는" 같은 층위의 동작이다.
   */
  it('광고와 보관함이 한 줄에 나란히 — 광고가 왼쪽이다', () => {
    const markup = card(me(), [], null, {
      personalityStatus: status({
        hasSelected: true,
        entries: [entry(1, '2026-08-16T10:00:00Z', true), entry(2, '2026-08-15T10:00:00Z')],
      }),
    });

    const ad = markup.indexOf('광고 보고 다시 분석하기');
    const archive = markup.indexOf('보관함에서 비교하기');
    expect(ad).toBeGreaterThan(-1);
    expect(archive).toBeGreaterThan(ad);
  });

  it('콜드스타트면 버튼 대신 안내만 — 보상 없는 광고 시청을 원천 차단한다', () => {
    const markup = card(me({ personality: null }), [], null, { personalityStatus: status({ coldStart: true }) });

    expect(markup).toContain('완독');
    expect(markup).not.toContain('광고 보고');
  });

  it('오늘 총량을 다 썼으면 버튼 대신 「내일」 안내', () => {
    const markup = card(me(), [], null, { personalityStatus: status({ adRefreshRemaining: 0 }) });

    expect(markup).toContain('내일');
    expect(markup).not.toContain('광고 보고');
  });

  it('status를 못 받았으면 관문 자리가 통째로 비어 있다 (fail-closed)', () => {
    const markup = card(me(), [], null, { personalityStatus: null });

    expect(markup).not.toContain('광고 보고');
    expect(markup).not.toContain('완독하면');
  });

  it('남의 책방에는 성향 관문이 없다', () => {
    expect(card(profile({ self: false }), [], null, { personalityStatus: status() })).not.toContain('광고 보고');
  });

  it('광고를 이미 본 뒤 실패했으면 광고 없는 재시도 손잡이를 준다 — 광고를 두 번 보게 하지 않는다', () => {
    const markup = card(me(), [], null, { personalityStatus: status(), earnedRetry: true });

    expect(markup).toContain('다시 시도');
  });

  it('결과 안내 문구를 그 자리에 띄운다', () => {
    const markup = card(me(), [], null, { personalityStatus: status(), personalityNotice: '새 성향이 도착했어요' });

    expect(markup).toContain('새 성향이 도착했어요');
  });

  it('분석이 2건 쌓이면 보관함 손잡이가 선다 — 술어가 실제로 마크업에 연결됐는지', () => {
    const entries = [entry(2, '2026-08-15T10:00:00Z', true), entry(1, '2026-08-01T10:00:00Z')];

    expect(card(me(), [], null, { personalityStatus: status({ entries }) })).toContain('보관함');
  });

  it('1건뿐이면 손잡이가 없다', () => {
    const entries = [entry(1, '2026-08-15T10:00:00Z', true)];

    expect(card(me(), [], null, { personalityStatus: status({ entries }) })).not.toContain('보관함');
  });

  it('손잡이를 누른 상태(archiveOpen)면 시트가 서술들과 함께 열린다', () => {
    const entries = [
      entry(2, '2026-08-15T10:00:00Z', true, { narrative: '새 문구다' }),
      entry(1, '2026-08-01T10:00:00Z', false, { narrative: '옛 문구다' }),
    ];

    const markup = card(me(), [], null, { personalityStatus: status({ entries }), archiveOpen: true });

    expect(markup).toContain('새 문구다');
    expect(markup).toContain('옛 문구다');
  });

  it('닫힌 상태면 시트 내용이 마크업에 없다 — 열림이 프롭으로만 결정된다', () => {
    const entries = [
      entry(2, '2026-08-15T10:00:00Z', true, { narrative: '새 문구다' }),
      entry(1, '2026-08-01T10:00:00Z', false, { narrative: '옛 문구다' }),
    ];

    expect(card(me(), [], null, { personalityStatus: status({ entries }) })).not.toContain('옛 문구다');
  });
});

/**
 * 목 픽스처 계약 — 목 모드(`npm run dev:mock`)는 이 기능의 **유일한 수동 검증 경로**다(광고 SDK와 무관한
 * 기능이라 실기기가 필요 없다). 그래서 "브라우저로 열면 비교 화면이 실제로 보이는가"를 여기서 못 박는다:
 * 시드가 2건 이상이고 서술이 서로 다르며, 재분석이 또 다른 서술을 낳아야 변화가 눈에 보인다.
 */
describe('dev-mock 보관함 계약', () => {
  it('status → ad-refresh → select 흐름에서 서로 다른 서술이 쌓이고 대표가 옮겨간다', async () => {
    const { mockRequest } = await import('./dev-mock');

    const before = await mockRequest<PersonalityStatus>('/api/personality/status');
    const seeded = before.entries ?? [];
    // 시드가 2건 미만이면 브라우저를 열어도 손잡이가 안 서서 비교 화면에 도달할 수 없다.
    expect(seeded.length).toBeGreaterThanOrEqual(2);
    // 문구가 같으면 "바뀐지 모르겠다"는 원래 문제를 목이 그대로 재현한다 — 시드부터 달라야 한다.
    expect(new Set(seeded.map((e) => e.narrative)).size).toBe(seeded.length);
    expect(seeded.every((e) => e.generatedAtLabel !== '')).toBe(true);

    const refreshed = await mockRequest<PersonalityMutation>('/api/personality/ad-refresh', { method: 'POST' });
    const after = await mockRequest<PersonalityStatus>('/api/personality/status');
    const entries = after.entries ?? [];
    // 새 분석의 서술이 직전 대표와 달라야 "광고를 봤더니 바뀌었다"가 눈에 보인다(순환 픽스처).
    expect(entries[0].narrative).not.toBe(seeded[0].narrative);
    expect(entries[0].id).toBe(newestEntry(refreshed.view.entries)?.id);
    // 서버 MAX_HISTORY=3 — 목이 무한히 쌓이면 화면이 서버와 다른 모양이 된다.
    expect(entries.length).toBeLessThanOrEqual(3);

    const target = entries.find((e) => !e.selected)!;
    await mockRequest('/api/personality/select/' + target.id, { method: 'POST' });
    const selected = (await mockRequest<PersonalityStatus>('/api/personality/status')).entries ?? [];

    expect(selected.filter((e) => e.selected).map((e) => e.id)).toEqual([target.id]);
  });
});

/**
 * 책방 세션 캐시 — 헤더·격자가 두 왕복(`fetchProfile` + `fetchProfileBooks`)을 기다리며 통째로 로딩이던 자리.
 * 사람마다 키가 달라(`profile:{loginId}`) 남의 책방 캐시가 내 화면에 설 수 없다.
 */
describe('책방 세션 캐시 (Profile)', () => {
  beforeEach(cacheClear);

  const renderMounted = (loginId = 'goospel') =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Profile loginId={loginId} onError={() => {}} />
      </TDSMobileProvider>,
    );

  it('캐시가 비면 지금처럼 로딩부터다 — 캐시는 없던 데이터를 지어내지 않는다', () => {
    const markup = renderMounted();

    expect(markup).toContain('불러오는 중');
    expect(markup).not.toContain('구스펠');
  });

  it('지난 책방이 캐시에 있으면 첫 렌더부터 헤더와 격자가 선다', () => {
    cachePut(cacheKeyProfile('goospel'), profile({ self: true }));
    cachePut(cacheKeyProfileBooks('goospel'), [book(1, '데미안')]);

    const markup = renderMounted();

    expect(markup).toContain('구스펠');
    expect(markup).toContain('데미안');
    expect(markup).not.toContain('불러오는 중');
  });

  it('남의 책방 캐시는 내 화면에 안 선다 — 키에 loginId가 박혀 있다', () => {
    cachePut(cacheKeyProfile('nabi'), profile({ nickname: '여느밤', loginId: 'nabi' }));

    const markup = renderMounted('goospel');

    expect(markup).not.toContain('여느밤');
    expect(markup).toContain('불러오는 중');
  });
});
