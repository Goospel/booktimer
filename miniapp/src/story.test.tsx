import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { MarginEntry, MarginResponse } from './api';
import { ApiError } from './api';
import { MarginView, StoryComposer, createStoryMessage, hasFreshStory } from './screens/Story';
import { userAgent } from './test-fixtures';

/**
 * 여백 화면 계측 — 정적 렌더로는 effect·클릭이 안 돌므로, 화면의 결정은 순수 함수({@link hasFreshStory})와
 * **프롭으로 데이터를 꽂는 표시 컴포넌트**({@link MarginView})로 갈라 계측한다(T-149: 부정 단언 금지).
 * 노출 게이트(차단·IDOR·PRIVATE·비팔로워)는 서버 몫이라 여기서 재검증하지 않는다 — 화면은 서버가 준
 * `self`·`following`·`entries`가 무엇을 켜고 끄는지만 진다.
 */

const NOW = Date.parse('2026-08-16T12:00:00Z');
const MINUTE = 60_000;
const HOUR = 3_600_000;

function render(node: React.ReactNode) {
  return renderToStaticMarkup(<TDSMobileProvider userAgent={userAgent}>{node}</TDSMobileProvider>);
}

function entry(id: number, extra: Partial<MarginEntry> = {}): MarginEntry {
  return { id, text: `문장 ${id}`, bgCode: 'paper', createdAt: new Date(NOW - HOUR).toISOString(), ...extra };
}

function margin(extra: Partial<MarginResponse> = {}): MarginResponse {
  return {
    book: { id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null },
    ownerNickname: '구스펠',
    self: false,
    following: true,
    entries: [entry(1)],
    ...extra,
  };
}

function view(data: MarginResponse, extra: { confirmDeleteId?: number | null; error?: string | null } = {}) {
  return render(
    <MarginView
      loginId="goospel"
      margin={data}
      now={NOW}
      busy={false}
      confirmDeleteId={extra.confirmDeleteId ?? null}
      error={extra.error ?? null}
      onCompose={() => {}}
      onConfirmDelete={() => {}}
      onDelete={() => {}}
      onBack={() => {}}
    />,
  );
}

/**
 * 격자 발광 판정 — 서버는 **원시 사실(최근 글 시각)만** 주고 24시간 창은 클라가 잰다(설계 §D3ⓐ).
 * 경계는 **미만(&lt;)**이다: 정각 24시간은 이미 지난 것으로 본다.
 */
describe('새 글 발광 — hasFreshStory (경계)', () => {
  it('23시간 59분 전 글은 새 글이다', () => {
    expect(hasFreshStory(new Date(NOW - 24 * HOUR + MINUTE).toISOString(), NOW)).toBe(true);
  });

  it('24시간 정각은 새 글이 아니다 — 경계는 미만', () => {
    expect(hasFreshStory(new Date(NOW - 24 * HOUR).toISOString(), NOW)).toBe(false);
  });

  it('24시간 1분 전은 새 글이 아니다', () => {
    expect(hasFreshStory(new Date(NOW - 24 * HOUR - MINUTE).toISOString(), NOW)).toBe(false);
  });

  it('글이 없거나 비팔로워면(null) 발광하지 않는다 — 서버가 null로 가려 준다', () => {
    expect(hasFreshStory(null, NOW)).toBe(false);
  });
});

describe('책 여백 화면 (MarginView)', () => {
  it('책 라벨과 남긴 글 수를 머리에 세운다 — 홈 소식에서 바로 들어와도 어느 책인지 알아야 한다', () => {
    const markup = view(margin({ entries: [entry(1), entry(2)] }));

    expect(markup).toContain('데미안');
    expect(markup).toContain('헤르만 헤세');
    expect(markup).toContain('@goospel');
    expect(markup).toContain('여백에 남긴 글 2');
  });

  it('글은 서버가 준 순서(최신순) 그대로 그린다', () => {
    const markup = view(margin({ entries: [entry(9, { text: '최신 문장' }), entry(1, { text: '옛 문장' })] }));

    expect(markup.indexOf('최신 문장')).toBeLessThan(markup.indexOf('옛 문장'));
  });

  it('배경 코드는 서버 팔레트 색으로만 칠한다 — 코드가 곧 스타일 화이트리스트다', () => {
    expect(view(margin({ entries: [entry(1, { bgCode: 'night' })] }))).toContain('#1f2233');
    expect(view(margin({ entries: [entry(1, { bgCode: 'javascript:evil' })] }))).not.toContain('javascript:evil');
  });

  it('내 책이면 「여백에 글 남기기」와 글마다 지우기가 선다', () => {
    const markup = view(margin({ self: true, following: false }));

    expect(markup).toContain('여백에 글 남기기');
    expect(markup).toContain('지우기');
  });

  it('지우기를 누른 글에만 확인 문구가 뜬다 — 되돌릴 수 없는 동작이라 한 탭 더 받는다', () => {
    const markup = view(margin({ self: true, entries: [entry(1), entry(2)] }), { confirmDeleteId: 2 });

    expect(markup).toContain('이 글을 지울까요?');
    expect(markup.match(/정말 지우기/g)).toHaveLength(1);
  });

  it('남의 책이면 작성·삭제 손잡이가 없다 — 서버가 404로 거절하는 동작을 화면에서 먼저 막는다', () => {
    const markup = view(margin({ self: false, following: true }));

    expect(markup).not.toContain('여백에 글 남기기');
    expect(markup).not.toContain('지우기');
  });

  it('비팔로워에게는 팔로우하면 볼 수 있다고 말한다 — 글 유무 자체가 새지 않는다(서버가 빈 배열)', () => {
    const markup = view(margin({ self: false, following: false, entries: [] }));

    expect(markup).toContain('팔로우하면');
    expect(markup).toContain('여백에 남긴 글 0');
  });

  it('내 책인데 글이 하나도 없으면 첫 문장을 권한다 — 팔로우 안내가 내 화면에 뜨면 오독이다', () => {
    const markup = view(margin({ self: true, following: false, entries: [] }));

    expect(markup).toContain('아직 남긴 글이 없어요');
    expect(markup).not.toContain('팔로우하면');
  });

  it('글마다 상대 시각을 적는다 — 만료가 없어진 뒤로 "언제 쓴 글인지"가 유일한 시간 단서다', () => {
    expect(view(margin({ entries: [entry(1, { createdAt: new Date(NOW - 3 * HOUR).toISOString() })] }))).toContain(
      '3시간 전',
    );
  });

  it('실패 문구는 화면 안에서 끝난다 — 목록을 통째로 에러로 갈아치우지 않는다', () => {
    const markup = view(margin(), { error: '요청에 실패했어요 (500)' });

    expect(markup).toContain('요청에 실패했어요 (500)');
    expect(markup).toContain('문장 1');
  });
});

describe('글 남기기 (StoryComposer)', () => {
  const composer = () =>
    render(
      <StoryComposer
        book={{ id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null }}
        onDone={() => {}}
        onCancel={() => {}}
        onError={() => {}}
      />,
    );

  it('제목은 「여백에 글 남기기」이고 어느 책의 여백인지 함께 적는다', () => {
    const markup = composer();

    expect(markup).toContain('여백에 글 남기기');
    expect(markup).toContain('데미안');
  });

  it('책 고르는 select가 없다 — 진입점이 이미 그 책이라 고를 것이 남지 않았다', () => {
    expect(composer()).not.toContain('<select');
  });

  it('팔레트 스와치와 500자 카운터는 그대로 남는다', () => {
    const markup = composer();

    expect(markup).toContain('0/500');
    expect(markup).toContain('aria-label="paper"');
  });
});

describe('작성 실패 안내 — createStoryMessage', () => {
  it('레이트리밋(429)·검증 실패(400)·책 없음(404)을 각각 다르게 안내한다', () => {
    expect(createStoryMessage(new ApiError(429, '요청에 실패했어요 (429)'))).toBe(
      '글을 너무 자주 남겼어요. 잠시 후 다시 시도해 주세요.',
    );
    expect(createStoryMessage(new ApiError(400, '요청에 실패했어요 (400)'))).toContain('500자');
    expect(createStoryMessage(new ApiError(404, '요청에 실패했어요 (404)'))).toContain('책방을 새로고침');
  });

  it('안내가 「여백」을 잃어버린 물건처럼 말하지 않는다 — 여백은 자리고, 남기는 것은 글이다', () => {
    expect(createStoryMessage(new ApiError(429, '요청에 실패했어요 (429)'))).not.toContain('여백을');
    expect(createStoryMessage(new ApiError(400, '요청에 실패했어요 (400)'))).not.toContain('여백을');
  });

  it('404는 첨부 selector가 아니라 책방 새로고침으로 안내한다 — 고를 selector가 사라졌다', () => {
    expect(createStoryMessage(new ApiError(404, '요청에 실패했어요 (404)'))).not.toContain('골라');
  });

  it('서버가 평문 메시지를 주면 그대로 쓴다 — 상태코드 추측보다 서버 문구가 정확하다', () => {
    expect(createStoryMessage(new ApiError(400, '글을 남길 수 없습니다'))).toBe('글을 남길 수 없습니다');
  });

  it('네트워크 실패 등 상태코드 없는 오류는 그 메시지를 그대로 쓴다', () => {
    expect(createStoryMessage(new Error('Load failed'))).toBe('Load failed');
  });
});
