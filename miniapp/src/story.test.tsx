import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { AuthorStories, StoryCard, StoryFeedResponse, StoryViewerEntry } from './api';
import { ApiError } from './api';
import { StoryCardView, StoryStrip, createStoryMessage, nextStoryIndex, viewTargetId } from './screens/Story';
import { userAgent } from './test-fixtures';
import { coverColor } from './ui';

/**
 * 여백 화면 계측 — 정적 렌더로는 effect가 안 돌므로, 뷰어의 두 결정(다음/이전 전이 · 열람 기록 대상)은
 * 순수 함수로 뽑아 경계까지 단위로 본다. 24h 만료·작성 자격은 서버 몫이라 여기서 재검증하지 않는다(설계 §4).
 */

function render(node: React.ReactNode) {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

function card(id: number, extra: Partial<StoryCard> = {}): StoryCard {
  return {
    id,
    text: `문장 ${id}`,
    bgCode: 'paper',
    bookTitle: null,
    bookCoverUrl: null,
    createdAt: '2026-08-11T00:00:00Z',
    viewed: false,
    ...extra,
  };
}

function author(loginId: string | null, extra: Partial<AuthorStories> = {}): AuthorStories {
  return {
    loginId,
    nickname: loginId === null ? '나' : `${loginId}님`,
    profileCharacterCode: null,
    allViewed: false,
    stories: [card(1)],
    ...extra,
  };
}

function strip(feed: StoryFeedResponse | null) {
  return render(<StoryStrip feed={feed} onOpen={() => {}} onCompose={() => {}} />);
}

function viewerCard(props: Partial<Parameters<typeof StoryCardView>[0]> = {}) {
  return render(
    <StoryCardView
      author={author('goospel')}
      card={card(1)}
      index={0}
      mine={false}
      viewers={null}
      busy={false}
      onStep={() => {}}
      onClose={() => {}}
      onDelete={() => {}}
      onViewers={() => {}}
      onOpenProfile={() => {}}
      {...props}
    />,
  );
}

describe('여백 스트립 (소셜 탭 상단)', () => {
  it('미열람 링에만 "새 여백" 표식을 단다 — 링 테두리 색만으로는 구분이 안 되는 사람이 있다', () => {
    const markup = strip({ mine: null, groups: [author('goospel'), author('reader', { allViewed: true })] });

    expect(markup).toContain('goospel님');
    expect(markup).toContain('reader님');
    expect(markup.match(/새 여백/g)).toHaveLength(1); // 전부 열람한 reader에는 안 붙는다
  });

  it('피드가 비어도 작성 진입은 남기되, 팔로우 유도 문구는 스트립이 말하지 않는다(소셜 탭 빈 상태와 중복)', () => {
    const markup = strip({ mine: null, groups: [] });

    expect(markup).toContain('여백');
    expect(markup).not.toContain('팔로우한 사람이 없');
    expect(markup).not.toContain('찾아');
  });

  it('아직 못 받은 피드(null)에는 스트립을 그리지 않는다 — 빈 껍데기가 깜빡이지 않게', () => {
    expect(renderToStaticMarkup(<StoryStrip feed={null} onOpen={() => {}} onCompose={() => {}} />)).toBe('');
  });

  it('가로 스크롤 스트립의 스크롤바를 숨긴다 — 링 아래로 두꺼운 회색 바가 그대로 보였다', () => {
    expect(strip({ mine: null, groups: [author('goospel')] })).toContain('class="no-scrollbar"');
  });

  it('내 여백이 있으면 맨 앞에 내 링을 둔다 — 핸들 없는 계정(loginId=null)도 포함(설계 §5-1)', () => {
    const markup = strip({ mine: author(null, { stories: [card(9)] }), groups: [author('goospel')] });

    expect(markup.indexOf('내 여백')).toBeGreaterThanOrEqual(0);
    expect(markup.indexOf('내 여백')).toBeLessThan(markup.indexOf('goospel님'));
  });
});

/**
 * 링을 인스타 관습(원형 이니셜 아바타 + 바깥 링 + 닉네임 캡션)으로 세운다 — 텍스트 알약은
 * 그 어휘를 화면에 만들어 주지 못했다. 링 색 분기의 출처는 서버 `AuthorStories.allViewed`다.
 */
describe('여백 링 — 원형 아바타', () => {
  it('닉네임 첫 글자를 원형으로 세우고 배경은 닉네임에서 결정적으로 고른다 — 같은 사람은 늘 같은 색', () => {
    const markup = strip({ mine: null, groups: [author('goospel')] });

    expect(markup).toContain('border-radius:50%');
    expect(markup).toContain(`background:${coverColor('goospel님')}`);
  });

  it('미열람이면 링이 세이지로 선다 — 다 본 링은 옅은 테두리로 가라앉는다', () => {
    const ring = (allViewed: boolean) => strip({ mine: null, groups: [author('goospel', { allViewed })] });

    expect(ring(false)).toContain('background:var(--adaptiveBlue500, #6E8A6A)');
    expect(ring(true)).not.toContain('background:var(--adaptiveBlue500, #6E8A6A)');
  });

  it('「여백 적기」는 이니셜이 아니라 + 아이콘 타일로 남는다 — 사람 링과 섞이면 남의 여백으로 오독한다', () => {
    const markup = strip({ mine: null, groups: [] });

    expect(markup).toContain('여백 적기');
    expect(markup).toContain('>+<');
  });
});

/**
 * 뷰어 진행 인디케이터 — "1/3" 텍스트는 이 화면의 어휘가 아니다. 인스타식 세그먼트 바로 바꾸고,
 * 수치는 스크린리더가 읽을 수 있게 `progressbar`로 남긴다(텍스트를 지운 자리를 비워두지 않는다).
 */
describe('여백 뷰어 진행 인디케이터', () => {
  const three = author('goospel', { stories: [card(1), card(2), card(3)] });

  it('카드 수만큼 세그먼트를 나누고 "N/M" 텍스트는 지운다', () => {
    const markup = viewerCard({ author: three, index: 1 });

    expect(markup).not.toContain('2/3');
    expect(markup.match(/height:2px/g)).toHaveLength(3);
  });

  it('현재 인덱스까지만 채운다 — 아직 안 본 카드만 옅게 남는다', () => {
    expect(viewerCard({ author: three, index: 1 }).match(/opacity:0\.3/g)).toHaveLength(1);
    expect(viewerCard({ author: three, index: 0 }).match(/opacity:0\.3/g)).toHaveLength(2);
  });

  it('진행 수치를 스크린리더에 남긴다 — 텍스트를 지운 만큼 여기서 갚는다', () => {
    const markup = viewerCard({ author: three, index: 1 });

    expect(markup).toContain('aria-valuenow="2"');
    expect(markup).toContain('aria-valuemax="3"');
  });

  it('노치를 피해 상단 여백을 준다 — 전체화면이라 세그먼트가 상태바 아래로 깔렸다', () => {
    expect(viewerCard()).toContain('env(safe-area-inset-top)');
  });
});

describe('여백 열람 카드', () => {
  it('문장·작성자·첨부 책 제목을 함께 그린다', () => {
    const markup = viewerCard({ card: card(1, { bookTitle: '자바 최적화' }) });

    expect(markup).toContain('문장 1');
    expect(markup).toContain('goospel님');
    expect(markup).toContain('자바 최적화');
  });

  it('첨부 책 표지가 있으면 함께 그린다 — 없으면 제목만(있는 데이터만 쓴다)', () => {
    expect(viewerCard({ card: card(1, { bookTitle: '자바 최적화', bookCoverUrl: 'https://img/java.jpg' }) })).toContain(
      'src="https://img/java.jpg"',
    );
    expect(viewerCard({ card: card(1, { bookTitle: '자바 최적화' }) })).not.toContain('<img');
  });

  it('남의 여백에는 삭제·본 사람이 없고 책방 진입이 있다 — 서버가 404로 거절하는 동작이라 화면에서 먼저 막는다', () => {
    const markup = viewerCard({ mine: false });

    expect(markup).not.toContain('삭제');
    expect(markup).not.toContain('본 사람');
    expect(markup).toContain('책방');
  });

  it('내 여백에는 삭제·본 사람이 있고 책방 진입은 없다', () => {
    const markup = viewerCard({ mine: true, author: author(null) });

    expect(markup).toContain('삭제');
    expect(markup).toContain('본 사람');
    expect(markup).not.toContain('책방');
  });

  it('본 사람 목록을 받으면 열람자 닉네임을 그리고, 0명이면 아직 없다고 말한다', () => {
    const viewers: StoryViewerEntry[] = [
      { loginId: 'a', nickname: '에이', profileCharacterCode: null, viewedAt: '2026-08-11T01:00:00Z' },
    ];

    expect(viewerCard({ mine: true, viewers })).toContain('에이');
    expect(viewerCard({ mine: true, viewers: [] })).toContain('아직 본 사람이 없어요');
  });

  it('배경 코드는 서버 팔레트 색으로 칠한다 — 코드가 곧 스타일 화이트리스트다', () => {
    expect(viewerCard({ card: card(1, { bgCode: 'night' }) })).toContain('#1f2233');
    // 팔레트 밖 코드(옛 데이터·오타)는 기본 배경으로 떨어진다 — 스타일 주입 자리를 안 만든다
    expect(viewerCard({ card: card(1, { bgCode: 'javascript:evil' }) })).not.toContain('javascript:evil');
  });
});

describe('뷰어 전이 — nextStoryIndex (경계)', () => {
  it('다음으로 넘기면 인덱스가 하나 오른다', () => {
    expect(nextStoryIndex(0, 1, 3)).toBe(1);
  });

  it('마지막에서 다음은 null — 뷰어를 닫는다(다음 작성자로 튀지 않는다)', () => {
    expect(nextStoryIndex(2, 1, 3)).toBeNull();
  });

  it('첫 카드에서 이전은 제자리 — 실수 탭에 뷰어가 닫히지 않는다', () => {
    expect(nextStoryIndex(0, -1, 3)).toBe(0);
  });

  it('한 장뿐이면 다음은 곧 닫힘, 이전은 제자리', () => {
    expect(nextStoryIndex(0, 1, 1)).toBeNull();
    expect(nextStoryIndex(0, -1, 1)).toBe(0);
  });
});

describe('열람 기록 대상 — viewTargetId', () => {
  it('남의 미열람 여백은 그 id를 기록한다', () => {
    expect(viewTargetId(card(5), false)).toBe(5);
  });

  it('내 여백은 기록하지 않는다 — 서버가 no-op이라 요청이 낭비다', () => {
    expect(viewTargetId(card(5), true)).toBeNull();
  });

  it('이미 열람한 여백은 다시 기록하지 않는다 — 서버 멱등에 기대지 않고 요청을 아낀다', () => {
    expect(viewTargetId(card(5, { viewed: true }), false)).toBeNull();
  });
});

describe('작성 실패 안내 — createStoryMessage', () => {
  it('레이트리밋(429)·검증 실패(400)·책 없음(404)을 각각 다르게 안내한다', () => {
    expect(createStoryMessage(new ApiError(429, '요청에 실패했어요 (429)'))).toContain('잠시');
    expect(createStoryMessage(new ApiError(400, '요청에 실패했어요 (400)'))).toContain('500자');
    expect(createStoryMessage(new ApiError(404, '요청에 실패했어요 (404)'))).toContain('책');
  });

  it('안내에 개수 상한을 말하지 않는다 — 만료가 사라지며 20장 게이트도 함께 없앴다', () => {
    expect(createStoryMessage(new ApiError(400, '요청에 실패했어요 (400)'))).not.toContain('20장');
  });

  it('서버가 평문 메시지를 주면 그대로 쓴다 — 상태코드 추측보다 서버 문구가 정확하다', () => {
    expect(createStoryMessage(new ApiError(400, '여백을 남길 수 없습니다'))).toBe('여백을 남길 수 없습니다');
  });

  it('네트워크 실패 등 상태코드 없는 오류는 그 메시지를 그대로 쓴다', () => {
    expect(createStoryMessage(new Error('Load failed'))).toBe('Load failed');
  });
});
