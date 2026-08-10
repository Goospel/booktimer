import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { AuthorStories, StoryCard, StoryFeedResponse, StoryViewerEntry } from './api';
import { ApiError } from './api';
import { StoryCardView, StoryStrip, createStoryMessage, nextStoryIndex, viewTargetId } from './screens/Story';
import { userAgent } from './test-fixtures';

/**
 * 스토리 화면 계측 — 정적 렌더로는 effect가 안 돌므로, 뷰어의 두 결정(다음/이전 전이 · 열람 기록 대상)은
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

describe('스토리 스트립 (소셜 탭 상단)', () => {
  it('미열람 링에만 "새 스토리" 표식을 단다 — 링 테두리 색만으로는 구분이 안 되는 사람이 있다', () => {
    const markup = strip({ mine: null, groups: [author('goospel'), author('reader', { allViewed: true })] });

    expect(markup).toContain('goospel님');
    expect(markup).toContain('reader님');
    expect(markup.match(/새 스토리/g)).toHaveLength(1); // 전부 열람한 reader에는 안 붙는다
  });

  it('피드가 비어도 작성 진입은 남기되, 팔로우 유도 문구는 스트립이 말하지 않는다(소셜 탭 빈 상태와 중복)', () => {
    const markup = strip({ mine: null, groups: [] });

    expect(markup).toContain('스토리');
    expect(markup).not.toContain('팔로우한 사람이 없');
    expect(markup).not.toContain('찾아');
  });

  it('아직 못 받은 피드(null)에는 스트립을 그리지 않는다 — 빈 껍데기가 깜빡이지 않게', () => {
    expect(renderToStaticMarkup(<StoryStrip feed={null} onOpen={() => {}} onCompose={() => {}} />)).toBe('');
  });

  it('내 스토리가 있으면 맨 앞에 내 링을 둔다 — 핸들 없는 계정(loginId=null)도 포함(설계 §5-1)', () => {
    const markup = strip({ mine: author(null, { stories: [card(9)] }), groups: [author('goospel')] });

    expect(markup.indexOf('내 스토리')).toBeGreaterThanOrEqual(0);
    expect(markup.indexOf('내 스토리')).toBeLessThan(markup.indexOf('goospel님'));
  });
});

describe('스토리 열람 카드', () => {
  it('문장·작성자·첨부 책 제목을 함께 그린다', () => {
    const markup = viewerCard({ card: card(1, { bookTitle: '자바 최적화' }) });

    expect(markup).toContain('문장 1');
    expect(markup).toContain('goospel님');
    expect(markup).toContain('자바 최적화');
  });

  it('남의 스토리에는 삭제·본 사람이 없고 책방 진입이 있다 — 서버가 404로 거절하는 동작이라 화면에서 먼저 막는다', () => {
    const markup = viewerCard({ mine: false });

    expect(markup).not.toContain('삭제');
    expect(markup).not.toContain('본 사람');
    expect(markup).toContain('책방');
  });

  it('내 스토리에는 삭제·본 사람이 있고 책방 진입은 없다', () => {
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
  it('남의 미열람 스토리는 그 id를 기록한다', () => {
    expect(viewTargetId(card(5), false)).toBe(5);
  });

  it('내 스토리는 기록하지 않는다 — 서버가 no-op이라 요청이 낭비다', () => {
    expect(viewTargetId(card(5), true)).toBeNull();
  });

  it('이미 열람한 스토리는 다시 기록하지 않는다 — 서버 멱등에 기대지 않고 요청을 아낀다', () => {
    expect(viewTargetId(card(5, { viewed: true }), false)).toBeNull();
  });
});

describe('작성 실패 안내 — createStoryMessage', () => {
  it('레이트리밋(429)·상한(400)·책 없음(404)을 각각 다르게 안내한다', () => {
    expect(createStoryMessage(new ApiError(429, '요청에 실패했어요 (429)'))).toContain('잠시');
    expect(createStoryMessage(new ApiError(400, '요청에 실패했어요 (400)'))).toContain('500자');
    expect(createStoryMessage(new ApiError(404, '요청에 실패했어요 (404)'))).toContain('책');
  });

  it('서버가 평문 메시지를 주면 그대로 쓴다 — 상태코드 추측보다 서버 문구가 정확하다', () => {
    expect(createStoryMessage(new ApiError(400, '활성 스토리는 최대 20장입니다'))).toBe('활성 스토리는 최대 20장입니다');
  });

  it('네트워크 실패 등 상태코드 없는 오류는 그 메시지를 그대로 쓴다', () => {
    expect(createStoryMessage(new Error('Load failed'))).toBe('Load failed');
  });
});
