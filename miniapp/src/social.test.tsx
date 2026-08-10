import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { ProfileBook, ProfileResponse, UserRow } from './api';
import { ProfileCard } from './screens/Profile';
import { MyShelfEntry, Social, UserList } from './screens/Social';
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

function card(p: ProfileResponse, books: ProfileBook[] = [], activeTag: string | null = null) {
  return render(
    <ProfileCard
      profile={p}
      books={books}
      activeTag={activeTag}
      busy={false}
      onFollowToggle={() => {}}
      onSelectTag={() => {}}
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
    const markup = render(<MyShelfEntry myLoginId="goospel" onOpen={() => {}} />);

    expect(markup).toContain('내 책방');
    expect(markup).toContain('<button');
  });

  it('핸들이 없으면(미니앱 신규 가입) 버튼 대신 웹 안내를 그린다 — 서버가 loginId로만 책방을 찾는다', () => {
    const markup = render(<MyShelfEntry myLoginId={null} onOpen={() => {}} />);

    expect(markup).toContain('아이디');
    expect(markup).toContain('웹');
    expect(markup).not.toContain('<button'); // 눌러도 404가 나는 버튼을 내놓지 않는다
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

  it('공개 책도 표지를 그린다 — 없으면 자리 채움 상자로 대신한다', () => {
    expect(card(profile(), [{ ...book(1, '자바 최적화'), coverUrl: 'https://img/java.jpg' }])).toContain(
      'src="https://img/java.jpg"',
    );
    expect(card(profile(), [book(1, '자바 최적화')])).toContain('📚');
  });

  it('제목과 메타를 각자 다른 블록에 둔다 — 짧은 제목이면 "데미안저자"처럼 붙어 보였다', () => {
    const markup = card(profile(), [book(1, '데미안')]);
    const between = markup.slice(markup.indexOf('데미안') + 3, markup.indexOf('저자'));

    expect(between).toContain('</div>');
  });
});

describe('소셜 탭 검색', () => {
  it('검색 버튼에 이름이 붙어 있다 — 스크린리더에 빈 버튼으로 읽히면 안 된다', () => {
    expect(render(<Social myLoginId="goospel" onError={() => {}} />)).toContain('aria-label="검색"');
  });
});
