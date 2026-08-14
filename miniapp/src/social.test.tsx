import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { PersonalityEntry, PersonalityMutation, PersonalityStatus, ProfileBook, ProfileResponse, UserRow } from './api';
import { ApiError, adRefreshPersonality, selectPersonality } from './api';
import {
  ProfileCard,
  SafetyPanel,
  analysisFailed,
  claimPersonality,
  newestEntry,
  personalityErrorMessage,
  runPersonalityRefresh,
  showPersonalityAdButton,
  toggleSafety,
} from './screens/Profile';
import { HandleSheet, MyShelfEntry, Social, UserList } from './screens/Social';
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
  REWARD_AD_GROUP_ID: 'test-ad-group',
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

function book(id: number, title: string): ProfileBook {
  return { id, title, author: '저자', coverUrl: null, status: '다 읽음', seconds: 600, purchaseLink: null };
}

function card(
  p: ProfileResponse,
  books: ProfileBook[] = [],
  activeTag: string | null = null,
  view: {
    selectedId?: number | null;
    gridOpen?: boolean;
    personalityStatus?: PersonalityStatus | null;
    earnedRetry?: boolean;
    personalityNotice?: string | null;
  } = {},
) {
  return render(
    <ProfileCard
      profile={p}
      books={books}
      activeTag={activeTag}
      selectedId={view.selectedId ?? null}
      gridOpen={view.gridOpen ?? false}
      busy={false}
      personalityStatus={view.personalityStatus ?? null}
      adBusy={false}
      earnedRetry={view.earnedRetry ?? false}
      personalityNotice={view.personalityNotice ?? null}
      onClaimPersonality={() => {}}
      onRetryPersonality={() => {}}
      onFollowToggle={() => {}}
      onSelectTag={() => {}}
      onSelect={() => {}}
      onGrid={() => {}}
      onMore={() => {}}
      safety={null}
      onBack={() => {}}
    />,
  );
}

/** 관문 통과 상태(첫 분석) — 각 테스트는 여기서 한 필드만 어긋내 그 필드의 책임을 잰다. */
function status(extra: Partial<PersonalityStatus> = {}): PersonalityStatus {
  return { coldStart: false, hasSelected: false, adRefreshRemaining: 10, adRefreshLimit: 10, ...extra };
}

function entry(id: number, generatedAt: string | null, selected = false): PersonalityEntry {
  return { id, generatedAt, selected };
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

describe('내 책방 진입 — login_id=null 경계 (설계 §5-1)', () => {
  it('핸들이 있으면 내 책방으로 들어갈 수 있다', () => {
    const markup = render(<MyShelfEntry myLoginId="goospel" onOpen={() => {}} onCreateHandle={() => {}} />);

    expect(markup).toContain('내 책방');
    expect(markup).toContain('<button');
  });

  it('핸들이 없으면 여기서 만들 길을 준다 — 옛 웹 안내는 토스 계정에게 실행 불가능한 죽은 안내였다', () => {
    const markup = render(<MyShelfEntry myLoginId={null} onOpen={() => {}} onCreateHandle={() => {}} />);

    expect(markup).toContain('아이디 만들기');
    expect(markup).toContain('<button'); // 눌러 만들 수 있다 — 안내만 하고 끝내지 않는다
    // 토스로 가입한 계정은 비밀번호가 없어 웹 로그인 자체가 불가능하다 — 그 안내로 되돌아가면 회귀다.
    expect(markup).not.toContain('booktimer.app');
  });
});

/** 핸들 시트 — 불변 경고가 여기 없으면 사용자가 되돌릴 수 없는 선택을 모르고 한다. */
describe('핸들 만들기 시트', () => {
  const sheet = () => render(<HandleSheet onClose={() => {}} onCreated={() => {}} onFail={() => {}} />);

  it('한 번 정하면 못 바꾼다고 미리 알린다', () => {
    expect(sheet()).toContain('바꿀 수 없어요');
  });

  it('입력과 만들기 버튼을 준다 — 시트 안에서 끝난다', () => {
    const markup = sheet();

    expect(markup).toContain('<input');
    expect(markup).toContain('만들기');
  });
});

describe('책방 (프로필)', () => {
  it('남의 책방에는 팔로우 버튼이 있고, 이미 팔로우 중이면 취소로 바뀐다', () => {
    expect(card(profile({ following: false }))).toContain('팔로우');
    expect(card(profile({ following: false }))).not.toContain('팔로우 취소');
    expect(card(profile({ following: true }))).toContain('팔로우 취소');
  });

  it('내 책방에는 팔로우·차단/신고 진입이 없다 — 서버가 400으로 거절하는 동작이라 화면에서 먼저 막는다', () => {
    const markup = card(profile({ self: true }));

    expect(markup).not.toContain('팔로우');
    expect(markup).not.toContain('더보기');
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

  it('공개 책은 제목과 상태 라벨을 함께 그린다', () => {
    const markup = card(profile(), [book(1, '자바 최적화')]);

    expect(markup).toContain('자바 최적화');
    expect(markup).toContain('다 읽음');
  });

  it('공개 책도 표지를 그린다 — 없으면 제목 첫 글자 자리 표지로 대신한다', () => {
    expect(card(profile(), [{ ...book(1, '자바 최적화'), coverUrl: 'https://img/java.jpg' }])).toContain(
      'src="https://img/java.jpg"',
    );
    expect(card(profile(), [book(1, '자바 최적화')])).not.toContain('<img');
  });
});

/**
 * 책방 책 목록 — 세로로 쭈루룩 나열하던 카드를 서재와 같은 표지 캐러셀로 바꿨다.
 * 책이 많으면 아래 「돌아가기」까지 한참 스크롤해야 했고, 한 화면에 몇 권인지도 안 보였다.
 */
describe('책방 책 목록 — 서재와 같은 캐러셀', () => {
  const books = [book(1, '자바 최적화'), book(2, '데미안')];

  it('세로 나열 대신 표지 캐러셀로 그린다 — 가운데 온 책을 글자로 못 박는다', () => {
    const markup = card(profile(), books, null, { selectedId: 2 });

    expect(markup).toContain('data-cover-title="자바 최적화"');
    expect(markup).toContain('data-cover-title="데미안"');
    expect(markup).toContain('data-selected-book="데미안"');
  });

  it('고른 책 아래에 저자 한 줄, 상태·읽은 시간 한 줄을 적는다', () => {
    const markup = card(profile(), [book(1, '자바 최적화')]);

    expect(markup).toContain('저자\n다 읽음 · 10분');
  });

  it('읽은 시간이 0이면 적지 않는다 — 「0초」는 정보가 아니다', () => {
    const markup = card(profile(), [{ ...book(1, '자바 최적화'), seconds: 0 }]);

    expect(markup).toContain('저자\n다 읽음');
    expect(markup).not.toContain('0초');
  });

  it('책이 두 권 이상일 때만 「펼쳐보기」를 준다 — 한 권은 캐러셀이 이미 다 보여 준다', () => {
    expect(card(profile(), books)).toContain('펼쳐보기');
    expect(card(profile(), [book(1, '자바 최적화')])).not.toContain('펼쳐보기');
  });

  /**
   * 「펼쳐보기」는 목록 바로 위 줄에 선다 — 서재는 제목 줄에 뒀지만 책방은 제목과 캐러셀 사이에
   * 핸들·성향·태그·팔로우가 깔려 손잡이와 그 대상(책)이 화면 절반쯤 떨어져 보였다(사용자 제보).
   */
  it('「펼쳐보기」를 화면 제목이 아니라 「공개한 책 N」 줄에 둔다', () => {
    const markup = card(profile(), books);

    expect(markup.indexOf('공개한 책')).toBeLessThan(markup.indexOf('펼쳐보기'));
    expect(markup.indexOf('펼쳐보기')).toBeLessThan(markup.indexOf('data-cover-title'));
  });

  it('펼쳐보기를 열면 그 책들을 격자로 한 번에 그린다', () => {
    const markup = card(profile(), books, null, { gridOpen: true });

    expect(markup).toContain('data-grid-title="자바 최적화"');
    expect(markup).toContain('data-grid-title="데미안"');
  });

  it('고른 책이 목록에서 사라지면 첫 책으로 떨어진다 — 태그 드릴다운이 목록을 통째로 간다', () => {
    const markup = card(profile(), books, '한우물형', { selectedId: 999 });

    expect(markup).toContain('data-selected-book="자바 최적화"');
  });
});

describe('소셜 탭 검색', () => {
  it('검색 버튼에 이름이 붙어 있다 — 스크린리더에 빈 버튼으로 읽히면 안 된다', () => {
    expect(render(<Social myLoginId="goospel" onHandleCreated={() => {}} onError={() => {}} />)).toContain('aria-label="검색"');
  });

  it('아이디 입력을 form으로 감싼다 — 키보드 완료(엔터)가 아무 일도 안 해 버튼을 따로 눌러야 했다', () => {
    expect(render(<Social myLoginId="goospel" onHandleCreated={() => {}} onError={() => {}} />)).toContain('<form');
  });
});

/**
 * 책방 상단 ← 제거 — 배경 없는 화살표 글자라 「버튼」으로 안 읽혔다. 원래 이유(긴 목록 끝까지
 * 스크롤해야 출구를 만난다)도 책 목록이 캐러셀로 바뀌며 사라졌다. 나갈 길은 하단 「돌아가기」와
 * 플로팅 탭바가 맡는다.
 */
describe('책방 상단 뒤로가기', () => {
  it('제목 옆에 ← 를 두지 않는다', () => {
    expect(card(profile())).not.toContain('aria-label="뒤로"');
  });

  it('하단 「돌아가기」는 남긴다 — ← 를 없앤 뒤 이게 화면 안의 유일한 출구다', () => {
    expect(card(profile())).toContain('돌아가기');
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
});
