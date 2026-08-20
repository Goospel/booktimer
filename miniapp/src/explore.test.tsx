import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { ExploreUser, UserRow } from './api';
import { Explore, ExploreList } from './screens/Explore';
import { MutualFollowers, mutualFollowerText } from './screens/Profile';
import { userAgent } from './test-fixtures';

/**
 * 둘러보기 화면과 「공통 친구」 줄 — 아이디를 몰라도 사람을 만나는 경로(2026-08-20).
 *
 * <p>하니스가 정적 렌더라 effect·클릭이 돌지 않는다. 그래서 ① 문구 조립은 순수 함수
 * {@link mutualFollowerText}로 꺼내 계측하고 ② 목록은 데이터를 프롭으로 받는 순수 컴포넌트
 * ({@link ExploreList})로 그려 본다. 「호출하지 않는다」류 부정 단언은 항상 통과라 쓰지 않는다(T-149).
 */

const render = (ui: React.ReactNode) =>
  renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{ui}</TDSMobileProvider>);

const userWith = (loginId: string, titles: string[]): ExploreUser => ({
  loginId,
  nickname: loginId + '님',
  publicBookCount: titles.length,
  following: false,
  self: false,
  books: titles.map((title) => ({ title, coverUrl: null })),
});

describe('둘러보기 목록', () => {
  it('사람과 그 사람의 책을 함께 그린다', () => {
    const html = render(
      <ExploreList users={[userWith('jieun', ['아몬드', '달러구트 꿈'])]} rateLimited={false} onSelect={() => {}} />,
    );

    expect(html).toContain('jieun');
    expect(html).toContain('아몬드');
    expect(html).toContain('달러구트 꿈');
  });

  it('책이 4권 미만이면 있는 만큼만 그린다(빈 자리를 만들지 않는다)', () => {
    const html = render(
      <ExploreList users={[userWith('hyerin', ['불안의 서', '사랑의 기술'])]} rateLimited={false} onSelect={() => {}} />,
    );

    expect(html).toContain('불안의 서');
    expect(html).toContain('사랑의 기술');
  });

  it('시점 문구를 그리지 않는다 — 「언제 읽었는가」는 이 화면에 없다', () => {
    const html = render(
      <ExploreList users={[userWith('minsoo', ['사피엔스'])]} rateLimited={false} onSelect={() => {}} />,
    );

    for (const word of ['어제', '오늘', '일 전', '시간 읽', '분 읽', '마지막']) {
      expect(html).not.toContain(word);
    }
  });

  it('아직 못 받았으면(null) 안내도 목록도 아닌 로딩 자리를 지킨다', () => {
    const html = render(<ExploreList users={null} rateLimited={false} onSelect={() => {}} />);

    expect(html).not.toContain('아직 볼 사람이 없어요');
  });

  it('0명이면 빈 화면 대신 이유를 말한다', () => {
    const html = render(<ExploreList users={[]} rateLimited={false} onSelect={() => {}} />);

    expect(html).toContain('아직 볼 사람이 없어요');
  });

  it('한도에 닿았으면 조용한 0건과 다른 안내를 낸다', () => {
    const html = render(<ExploreList users={[]} rateLimited onSelect={() => {}} />);

    expect(html).toContain('잠시');
    expect(html).not.toContain('아직 볼 사람이 없어요');
  });

  it('추천 이유 칩을 그리지 않는다 — 칩은 전부 걷어냈다', () => {
    const html = render(
      <ExploreList users={[userWith('doyun', ['데미안'])]} rateLimited={false} onSelect={() => {}} />,
    );

    for (const chip of ['같이 읽은 책', '공통 친구', '나를 팔로우함', '요즘 꾸준히']) {
      expect(html).not.toContain(chip);
    }
  });
});

/**
 * 검색 자체는 시트 시절과 한 글자도 안 바뀌었다 — 그때 지키던 것들을 화면으로 옮겨 그대로 계측한다
 * (2026-08-20 SearchSheet → Explore 승격).
 */
describe('둘러보기 화면의 검색', () => {
  const screen = (results: UserRow[] | null, query = 'goo') =>
    render(
      <Explore
        query={query}
        results={results}
        busy={false}
        error={null}
        onQueryChange={() => {}}
        onSearch={() => {}}
        onSelect={() => {}}
        onClose={() => {}}
        onError={() => {}}
      />,
    );

  it('아이디 입력을 form으로 감싼다 — 키보드 완료(엔터)가 아무 일도 안 하면 버튼을 따로 눌러야 한다', () => {
    expect(screen(null)).toContain('<form');
  });

  it('검색 버튼에 이름이 붙어 있다 — 로딩 중엔 라벨이 스피너로 바뀌어 이름 없는 버튼이 된다', () => {
    expect(screen(null)).toContain('aria-label="검색"');
  });

  it('0건이면 두 글자 이상으로 다시 찾으라고 알린다 — 서버가 1글자를 빈 결과로 주므로 문구가 유일한 안내다', () => {
    expect(screen([])).toContain('두 글자 이상');
  });

  it('결과가 있으면 목록으로 그린다', () => {
    expect(
      screen([{ loginId: 'goospel', nickname: '구스펠', publicBookCount: 3, following: false, self: false }]),
    ).toContain('@goospel');
  });

  it('검색어를 안 넣었으면 검색 결과가 아니라 둘러보기를 그린다', () => {
    const html = screen(null);

    expect(html).toContain('둘러보기');
    expect(html).not.toContain('두 글자 이상');
  });
});

describe('공통 친구 문구', () => {
  it('0명이면 문구 자체가 없다(줄을 그리지 않는 근거)', () => {
    expect(mutualFollowerText([], 0)).toBeNull();
  });

  it('1명이면 이름 하나', () => {
    expect(mutualFollowerText(['민수'], 1)).toBe('민수님이 팔로우합니다');
  });

  it('2명이면 두 이름을 나란히', () => {
    expect(mutualFollowerText(['민수', '혜린'], 2)).toBe('민수님, 혜린님이 팔로우합니다');
  });

  it('3명 이상이면 이름 둘 + 나머지는 수로 접는다', () => {
    expect(mutualFollowerText(['민수', '혜린'], 6)).toBe('민수님, 혜린님 외 4명이 팔로우합니다');
  });

  it('총 수가 이름 수보다 크면 이름이 하나여도 접는다', () => {
    expect(mutualFollowerText(['민수'], 3)).toBe('민수님 외 2명이 팔로우합니다');
  });

  it('총 수를 못 받았으면(옛 서버) 받은 이름 수로 말한다 — 「외 -N명」을 만들지 않는다', () => {
    expect(mutualFollowerText(['민수', '혜린'], 0)).toBe('민수님, 혜린님이 팔로우합니다');
  });
});

describe('공통 친구 줄', () => {
  it('이름과 아바타를 함께 그린다', () => {
    const html = render(
      <MutualFollowers
        users={[
          { loginId: 'minsoo', nickname: '민수' },
          { loginId: 'hyerin', nickname: '혜린' },
        ]}
        total={4}
      />,
    );

    expect(html).toContain('민수님, 혜린님 외 2명이 팔로우합니다');
  });

  it('공통 친구가 없으면 아무것도 그리지 않는다(빈 자리도 남기지 않는다)', () => {
    expect(render(<MutualFollowers users={[]} total={0} />)).not.toContain('팔로우합니다');
  });

  it('옛 서버라 필드가 없으면 아무것도 그리지 않는다', () => {
    expect(render(<MutualFollowers users={undefined} total={undefined} />)).not.toContain('팔로우합니다');
  });
});
