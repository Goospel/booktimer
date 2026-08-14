import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { ProfileBook, ProfileResponse, UserRow } from './api';
import { ProfileCard, SafetyPanel, toggleSafety } from './screens/Profile';
import { HandleSheet, MyShelfEntry, Social, UserList } from './screens/Social';
import { userAgent } from './test-fixtures';

/**
 * 소셜 화면의 분기를 정적 렌더로 계측한다 — 뮤테이션(팔로우·차단·신고) 자체는 api 계층에서 이미 계측했고,
 * 여기서는 "서버가 준 플래그(self·following)가 화면의 무엇을 켜고 끄는가"를 본다.
 * 특히 self 분기는 서버가 400으로 거절하는 동작(자기 팔로우·자기 차단)이라 화면에서 애초에 막아야 한다.
 */

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
  view: { selectedId?: number | null; gridOpen?: boolean } = {},
) {
  return render(
    <ProfileCard
      profile={p}
      books={books}
      activeTag={activeTag}
      selectedId={view.selectedId ?? null}
      gridOpen={view.gridOpen ?? false}
      busy={false}
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

/** 책방 상단 뒤로가기 — 나가려면 긴 책 목록 끝까지 스크롤해 「돌아가기」를 찾아야 했다. */
describe('책방 상단 뒤로가기', () => {
  it('제목 옆에 ← 를 둔다', () => {
    const markup = card(profile());

    expect(markup).toContain('aria-label="뒤로"');
    expect(markup.indexOf('aria-label="뒤로"')).toBeLessThan(markup.indexOf('돌아가기'));
  });

  it('하단 「돌아가기」는 그대로 둔다 — 목록 끝에서 위로 되돌아가게 만들지 않는다', () => {
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
