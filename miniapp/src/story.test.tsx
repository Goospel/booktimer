import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { MarginEntry, MarginResponse } from './api';
import { ApiError } from './api';
import {
  MarginCard,
  MarginView,
  StoryComposer,
  createStoryMessage,
  hasFreshStory,
  likable,
  visibilityNotice,
} from './screens/Story';
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
  return {
    id,
    text: `문장 ${id}`,
    bgCode: 'paper',
    createdAt: new Date(NOW - HOUR).toISOString(),
    likeCount: 0,
    liked: false,
    ...extra,
  };
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

function view(
  data: MarginResponse,
  extra: { confirmDeleteId?: number | null; error?: string | null; onToggleLike?: (e: MarginEntry) => void } = {},
) {
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
      onToggleLike={extra.onToggleLike ?? (() => {})}
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

/**
 * 가시성 고지 — 비공개 책에도 여백을 쓸 수 있게 된 뒤(설계 결정 2), 「팔로워에게 보여요」는 비공개
 * 책에서 <b>거짓말</b>이 됐다. 쓰는 순간 이 한 줄이 무엇이 새고 무엇이 안 새는지 말한다.
 */
describe('가시성 고지 (visibilityNotice)', () => {
  it('비공개 책이면 나만 본다고 말하고, 공개로 바꾸면 보인다는 것까지 알린다', () => {
    const notice = visibilityNotice(false);

    expect(notice).toContain('나만 봐요');
    expect(notice).toContain('공개로 바꾸면');
  });

  it('공개 책이면 팔로워에게 보인다고 말한다', () => {
    expect(visibilityNotice(true)).toBe('팔로워에게 보여요.');
  });

  it('필드가 없는 옛 서버 응답(undefined)은 공개로 간주한다 — 비공개라 단정하는 쪽이 더 위험한 거짓말이다', () => {
    expect(visibilityNotice(undefined)).toBe('팔로워에게 보여요.');
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

  /**
   * 비공개 책의 여백은 나만 보는 메모다 — 그 사실을 화면이 말해 주지 않으면, 남긴 글이 팔로워에게
   * 보인다고 오해한 채 쌓는다(또는 그 반대로 새는 줄 모른다). 공개 책엔 적지 않는다(잡음).
   */
  it('내 비공개 책 여백에는 나만 본다는 한 줄이 붙는다', () => {
    const markup = view(margin({ self: true, book: { id: 7, title: '메모책', author: null, coverUrl: null, isPublic: false } }));

    expect(markup).toContain('나만 봐요');
  });

  it('내 공개 책 여백에는 그 줄이 없다 — 책방에 이미 진열된 책이라 새로 알릴 것이 없다', () => {
    const markup = view(margin({ self: true, book: { id: 7, title: '공개책', author: null, coverUrl: null, isPublic: true } }));

    expect(markup).not.toContain('나만 봐요');
    expect(markup).toContain('여백에 글 남기기'); // 화면 자체는 그려졌다(부재 단언의 쌍)
  });

  it('실패 문구는 화면 안에서 끝난다 — 목록을 통째로 에러로 갈아치우지 않는다', () => {
    const markup = view(margin(), { error: '요청에 실패했어요 (500)' });

    expect(markup).toContain('요청에 실패했어요 (500)');
    expect(markup).toContain('문장 1');
  });
});

describe('글 남기기 (StoryComposer)', () => {
  const composer = (isPublic?: boolean) =>
    render(
      <StoryComposer
        book={{ id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null, isPublic }}
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

  /** 쓰는 순간의 고지 — 캡션이라 글을 쓰기 시작해도 남는다(placeholder는 첫 글자에 사라진다). */
  it('비공개 책이면 나만 본다고 캡션으로 알린다', () => {
    expect(composer(false)).toContain('나만 봐요');
  });

  it('공개 책이면 팔로워에게 보인다고 알린다 — placeholder가 아니라 입력 위 캡션이다(써도 남는다)', () => {
    const markup = composer(true);

    expect(markup.indexOf('팔로워에게 보여요')).toBeLessThan(markup.indexOf('<textarea'));
    expect(markup).not.toContain('나만 봐요');
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

/**
 * 나가는 길 — 두 화면이 다르다.
 *
 * <p>작성 화면은 「취소」가 곧 출구라 헤더의 뒤로가기가 중복이었다(토스 네비바의 `‹`까지 세면 화살표가
 * 셋이었다). 여백 상세는 반대로 헤더 손잡이가 <b>유일한</b> 출구다 — 탭 위에 전체 화면으로 서 탭바가
 * 가려지고 하단 버튼도 없다. 그래서 한쪽만 지운다. 부정 단언은 짝이 되는 긍정 단언과 함께 둔다(T-149).
 */
describe('나가는 길 — 헤더 뒤로가기', () => {
  it('작성 화면엔 없다 — 「취소」가 출구다', () => {
    const markup = render(
      <StoryComposer
        book={{ id: 7, title: '데미안', author: null, coverUrl: null, isPublic: true }}
        onDone={() => {}}
        onCancel={() => {}}
        onError={() => {}}
      />,
    );

    expect(markup).toContain('취소');
    expect(markup).not.toContain('돌아가기');
  });

  it('여백 상세엔 있다 — 지우면 나갈 길이 사라진다', () => {
    expect(view(margin())).toContain('돌아가기');
  });
});

/**
 * 좋아요 — <b>손잡이의 유무는 `self`가 아니라 핸들러의 유무가 가른다</b>.
 *
 * <p>이게 이 기능에서 가장 미끄러지기 쉬운 자리다: 서재의 인라인 여백 미리보기는 <b>내 글인데도</b>
 * `self={false}`를 넘긴다(거기서 그 프롭은 「삭제 UI를 감춰」라는 뜻이다). `!self`로 하트를 켰다면
 * 내 서재에서 내 글에 좋아요 버튼이 떴을 것이다. 아래 두 번째 테스트가 그 회귀를 막는다.
 */
describe('여백 좋아요', () => {
  it('남의 글에는 하트 손잡이가 그려진다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 3, liked: false })] }));

    expect(html).toContain('aria-label="좋아요"');
    expect(html).toContain('>3<');
  });

  it('핸들러를 안 넘기면 손잡이가 없다 — 서재 미리보기가 내 글에 self=false를 넘기는 자리', () => {
    const html = render(
      <MarginCard
        entry={entry(1, { likeCount: 3 })}
        now={NOW}
        self={false}
        busy={false}
        confirming={false}
        onConfirmDelete={() => {}}
        onDelete={() => {}}
      />,
    );

    expect(html).not.toContain('aria-label="좋아요"');
    expect(html).toContain('>3<'); // 개수는 여전히 보인다 — 데이터지 손잡이가 아니다
  });

  it('이미 누른 글은 취소 손잡이가 된다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 4, liked: true })] }));

    expect(html).toContain('aria-label="좋아요 취소"');
  });

  it('아무도 안 누른 글에는 0을 그리지 않는다 — 빈 상태를 숫자로 박제하지 않는다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 0 })] }));

    expect(html).toContain('aria-label="좋아요"'); // 손잡이는 있고
    expect(html).not.toContain('>0<'); // 숫자만 없다
  });

  it('내 글에도 개수는 보인다 — 남이 눌러 준 것을 주인이 알아야 한다', () => {
    const html = view(margin({ self: true, following: false, entries: [entry(1, { likeCount: 2 })] }));

    expect(html).toContain('>2<');
    expect(html).not.toContain('aria-label="좋아요"'); // 자기 좋아요는 없다
  });
});

/**
 * 하트를 손잡이로 그릴 수 있는가 — 서버 게이트({@code StoryService.assertLikable})를 화면에 옮긴 것.
 * 어긋나면 눌러도 404가 나는 죽은 버튼이 생긴다.
 */
describe('좋아요 가능 판정 (likable)', () => {
  it('내 글에는 못 누른다 — 여백은 내 노트라 전부 자기 좋아요가 된다', () => {
    expect(likable(true, true)).toBe(false);
  });

  it('비공개 책에는 자리를 만들지 않는다 — 남이 볼 수 없어 개수가 영원히 0이다', () => {
    expect(likable(false, false)).toBe(false);
  });

  it('남의 공개 책 글에는 누를 수 있다', () => {
    expect(likable(false, true)).toBe(true);
  });

  it('필드가 없는 옛 서버 응답(undefined)은 공개로 간주한다 — 가시성 고지와 같은 기준', () => {
    expect(likable(false, undefined)).toBe(true);
  });
});
