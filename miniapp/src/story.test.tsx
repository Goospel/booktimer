import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { BookMarginAllResponse, MarginEntry, MarginResponse, SharedMarginEntry, UserRow } from './api';
import { ApiError } from './api';
import {
  BookMarginAllView,
  LikersSheet,
  MarginCard,
  MarginMenuSheet,
  MarginTabs,
  MarginView,
  StoryComposer,
  TIMER_STOPPED_NOTICE,
  createStoryMessage,
  hasFreshStory,
  marginTabLabel,
  shareNotice,
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
    quote: null,
    createdAt: new Date(NOW - HOUR).toISOString(),
    likeCount: 0,
    liked: false,
    ...extra,
  };
}

function margin(extra: Partial<MarginResponse> = {}): MarginResponse {
  return {
    book: { id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null, isbn13: '9791168340084' },
    ownerNickname: '구스펠',
    self: false,
    following: true,
    entries: [entry(1)],
    ...extra,
  };
}

function view(
  data: MarginResponse,
  extra: {
    error?: string | null;
    onToggleLike?: (e: MarginEntry) => void;
    onShowLikers?: (e: MarginEntry) => void;
    /** `undefined`를 명시하면 ⋯ 없는 화면이 된다 — 남의 여백·서재 미리보기가 그 자리다. */
    onOpenMenu?: (e: MarginEntry) => void;
    expanded?: ReadonlySet<number>;
    timerStopped?: boolean;
  } = {},
) {
  return render(
    <MarginView
      loginId="goospel"
      margin={data}
      now={NOW}
      error={extra.error ?? null}
      expanded={extra.expanded}
      onCompose={() => {}}
      onToggleLike={extra.onToggleLike ?? (() => {})}
      onShowLikers={extra.onShowLikers ?? (() => {})}
      onOpenMenu={'onOpenMenu' in extra ? extra.onOpenMenu : () => {}}
      onToggleExpand={() => {}}
      onBack={() => {}}
      timerStopped={extra.timerStopped ?? false}
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
    expect(markup).toContain('글 2');
  });

  /**
   * R2·R3 — 머리글의 주인 이름은 <b>남의 여백에서만</b> 선다. 내 여백은 탭줄이 서는 자리라 주인이
   * 언제나 나여서 이름이 잉여이지만, 남의 여백은 탭이 없어 그 이름이 「누구의 여백인가」의 유일한 좌표다.
   */
  it('내 여백 머리글에는 주인 이름이 없다 — 주인이 언제나 나다', () => {
    const markup = view(margin({ self: true }));

    expect(markup).not.toContain('@goospel');
    expect(markup).toContain('데미안'); // 머리글 자체는 그려졌다(부재 단언의 쌍)
    expect(markup).toContain('헤르만 헤세');
  });

  it('남의 여백 머리글에는 주인 이름이 선다 — 탭이 없어 이 줄이 유일한 좌표다', () => {
    const markup = view(margin({ self: false }));

    expect(markup).toContain('구스펠');
    expect(markup).toContain('@goospel');
  });

  it('저자가 없는 내 책이면 둘째 줄 자체가 없다 — 빈 줄만 남기지 않는다', () => {
    const markup = view(margin({ self: true, book: { id: 7, title: '수기', author: null, coverUrl: null } }));

    expect(markup).toContain('수기'); // 제목은 그려졌다(부재 단언의 쌍)
    expect(markup).not.toContain('@goospel');
  });

  it('글은 서버가 준 순서(최신순) 그대로 그린다', () => {
    const markup = view(margin({ entries: [entry(9, { text: '최신 문장' }), entry(1, { text: '옛 문장' })] }));

    expect(markup.indexOf('최신 문장')).toBeLessThan(markup.indexOf('옛 문장'));
  });

  it('배경 코드는 서버 팔레트 색으로만 칠한다 — 코드가 곧 스타일 화이트리스트다', () => {
    expect(view(margin({ entries: [entry(1, { bgCode: 'night' })] }))).toContain('#1f2233');
    expect(view(margin({ entries: [entry(1, { bgCode: 'javascript:evil' })] }))).not.toContain('javascript:evil');
  });

  /**
   * R4 — 작성 진입은 게시판 머리 <b>우상단</b>의 작은 버튼이다. 옛 전체폭 버튼이 남으면 한 화면에
   * 작성 문이 둘이 되므로, 새 버튼의 존재만이 아니라 <b>옛 버튼의 부재</b>까지 함께 못 박는다.
   */
  it('내 책이면 게시판 머리에 「글쓰기」가 선다 — 옛 전체폭 버튼은 없다', () => {
    const markup = view(margin({ self: true, following: false }));

    expect(markup).toContain('글쓰기');
    expect(markup).not.toContain('여백에 글 남기기');
  });

  it('남의 책이면 글쓰기가 없다 — 서버가 404로 거절하는 동작을 화면에서 먼저 막는다', () => {
    const markup = view(margin({ self: false, following: true }));

    expect(markup).toContain('문장 1'); // 목록은 그려졌다(부재 단언의 쌍)
    expect(markup).not.toContain('글쓰기');
  });

  /**
   * R7 — 행의 동작(올리기·내리기·지우기)은 ⋯ 시트로 접혔다. 목록 안에 지우기 글자가 남아 있으면
   * 그 자리에서 지울 수 있다는 뜻이 되어, 확인 단계가 없는 삭제 손잡이가 된다.
   */
  it('목록에는 ⋯ 손잡이만 있고 지우기 글자는 없다 — 동작은 시트로 접혔다', () => {
    const markup = view(margin({ self: true, following: false }));

    expect(markup).toContain('aria-label="이 글 관리"');
    expect(markup).not.toContain('지우기');
  });

  it('⋯ 손잡이를 안 넘기면 ⋯ 자체가 없다 — 남의 글은 내가 관리하지 않는다', () => {
    const markup = view(margin({ self: false, following: true }), { onOpenMenu: undefined });

    expect(markup).toContain('문장 1'); // 목록은 그려졌다(부재 단언의 쌍)
    expect(markup).not.toContain('aria-label="이 글 관리"');
  });

  /**
   * 컨테이너는 `onOpenMenu`를 <b>언제나</b> 넘긴다(내 여백인지 남의 여백인지 모르는 채로 배선된다) —
   * 그래서 남의 글에 ⋯ 가 안 뜨게 막는 것은 이 화면의 `self` 게이트 하나뿐이다. 손잡이를 안 넘긴
   * 위 케이스로는 이 게이트가 계측되지 않는다(돌연변이 실측에서 살아남았다).
   */
  it('남의 여백이면 손잡이를 받아도 ⋯ 를 안 그린다 — 서버 404를 화면에서 먼저 막는다', () => {
    const markup = view(margin({ self: false, following: true }), { onOpenMenu: () => {} });

    expect(markup).toContain('문장 1'); // 목록은 그려졌다(부재 단언의 쌍)
    expect(markup).not.toContain('aria-label="이 글 관리"');
  });

  it('비팔로워에게는 팔로우하면 볼 수 있다고 말한다 — 글 유무 자체가 새지 않는다(서버가 빈 배열)', () => {
    const markup = view(margin({ self: false, following: false, entries: [] }));

    expect(markup).toContain('팔로우하면');
    expect(markup).toContain('글 0');
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
    expect(markup).toContain('글쓰기'); // 화면 자체는 그려졌다(부재 단언의 쌍)
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
 *
 * <p>손잡이가 <b>둘로 갈라졌다</b>(2026-08-20): 하트는 누르기/취소만 지고, 개수는 「좋아요 N명」이라는
 * 별도 줄이 져서 명단을 연다. 한 버튼이 두 일을 하면 명단을 보려다 좋아요가 눌린다.
 */
describe('여백 좋아요', () => {
  it('하트 손잡이가 그려지고, 개수는 별도 줄이 진다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 3, liked: false })] }));

    expect(html).toContain('aria-label="좋아요"');
    expect(html).toContain('좋아요 3명');
  });

  it('핸들러를 안 넘기면 손잡이가 없다 — 서재 미리보기가 내 글에 self=false를 넘기는 자리', () => {
    const html = render(
      <MarginCard entry={entry(1, { likeCount: 3 })} now={NOW} />,
    );

    expect(html).not.toContain('aria-label="좋아요"');
    expect(html).toContain('좋아요 3명'); // 개수는 여전히 보인다 — 데이터지 손잡이가 아니다
  });

  it('이미 누른 글은 취소 손잡이가 된다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 4, liked: true })] }));

    expect(html).toContain('aria-label="좋아요 취소"');
  });

  it('아무도 안 누른 글에는 개수 줄이 아예 없다 — 빈 상태를 숫자로 박제하지 않는다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 0 })] }));

    expect(html).toContain('aria-label="좋아요"'); // 손잡이는 있고
    expect(html).not.toContain('좋아요 0명'); // 개수 줄만 없다
  });

  /**
   * 자기 좋아요를 허용하면서(2026-08-20) 내 여백에도 하트가 선다. 예전엔 여기가 「개수만」이었다 —
   * 그때는 여백에 글을 쓴 사람이 없으면 좋아요를 확인할 길 자체가 없었다.
   */
  it('내 글에도 하트가 그려진다 — 자기 좋아요 허용', () => {
    const html = view(margin({ self: true, following: false, entries: [entry(1, { likeCount: 2 })] }));

    expect(html).toContain('aria-label="좋아요"');
    expect(html).toContain('좋아요 2명');
  });

  it('내 비공개 책에도 하트가 그려진다 — 나만 보는 메모에도 표시를 남긴다', () => {
    const html = view(
      margin({
        self: true,
        following: false,
        book: { id: 7, title: '비밀 노트', author: null, coverUrl: null, isPublic: false },
        entries: [entry(1)],
      }),
    );

    expect(html).toContain('aria-label="좋아요"');
  });

  it('개수 줄은 손잡이다 — 눌러서 명단을 연다', () => {
    const html = view(margin({ entries: [entry(1, { likeCount: 3 })] }));

    expect(html).toContain('aria-label="좋아요 3명 보기"');
  });

  it('명단 핸들러가 없으면 개수는 글자로만 남는다 — 서재 미리보기엔 열 시트가 없다', () => {
    const html = render(
      <MarginCard entry={entry(1, { likeCount: 3 })} now={NOW} />,
    );

    expect(html).toContain('좋아요 3명');
    expect(html).not.toContain('aria-label="좋아요 3명 보기"');
  });
});

/**
 * 인용문 — 글이 「책에서 옮긴 문장 + 내 주석」 두 층이 된다(2026-08-20). 인용은 <b>선택</b>이라
 * 없는 글(옛 글 포함)이 지금까지와 똑같이 그려지는 것이 이 묶음의 핵심 단언이다.
 */
describe('인용문', () => {
  it('인용이 있으면 주석과 함께 카드에 실린다', () => {
    const html = view(
      margin({ entries: [entry(1, { quote: '새는 알에서 나오려고 투쟁한다.', text: '열아홉엔 몰랐다' })] }),
    );

    expect(html).toContain('새는 알에서 나오려고 투쟁한다.');
    expect(html).toContain('열아홉엔 몰랐다');
    expect(html).toContain('<blockquote');
  });

  it('인용이 없으면 인용 블록이 아예 없다 — 옛 글은 예전 그대로 그려진다', () => {
    const html = view(margin({ entries: [entry(1, { quote: null })] }));

    expect(html).toContain('문장 1');
    expect(html).not.toContain('<blockquote');
  });

  it('서재 인라인 미리보기(손잡이 없는 카드)도 인용을 그린다 — 같은 카드다', () => {
    const html = render(
      <MarginCard entry={entry(1, { quote: '옮긴 문장' })} now={NOW} />,
    );

    expect(html).toContain('옮긴 문장');
  });

  it('컴포저에 인용 칸이 있다 — 주석 칸과 나란히', () => {
    const html = render(
      <StoryComposer
        book={{ id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null }}
        onDone={() => {}}
        onCancel={() => {}}
        onError={() => {}}
      />,
    );

    expect(html).toContain('책에서 옮긴 문장');
    expect(html).toContain('내 생각');
  });

  /**
   * 어휘는 <b>한 벌</b>이다 — 쓸 때 「함께 걸기」, 고칠 때 「모두의 여백에 올리기」로 갈리면 같은 값이라는
   * 것이 안 읽힌다. 명사는 「모두의 여백」 하나, 동사는 「올리기/내리기」 하나(2026-08-22 게시판 개편).
   */
  it('작성 화면의 공개 스위치도 목록·시트와 같은 말을 쓴다', () => {
    const html = render(
      <StoryComposer
        book={{ id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null }}
        onDone={() => {}}
        onCancel={() => {}}
        onError={() => {}}
      />,
    );

    expect(html).toContain('모두의 여백에 올리기');
    expect(html).not.toContain('함께 걸기');
  });
});

/**
 * 좋아요 명단 시트 — 팔로워 시트와 같은 처지라 <b>데이터를 프롭으로</b> 받는다(정적 렌더 하니스가
 * 0명/N명 분기에 닿는 유일한 길). 실패는 그 자리에서 다시 받는 길을 준다.
 */
describe('좋아요 명단 시트 (LikersSheet)', () => {
  const someone: UserRow = { loginId: 'nabi', nickname: '나비독서', publicBookCount: 12, following: true, self: false };

  const sheet = (users: UserRow[] | null, error: string | null = null) =>
    render(
      <LikersSheet users={users} error={error} onSelect={() => {}} onClose={() => {}} onRetry={() => {}} />,
    );

  it('누른 사람의 닉네임과 핸들을 그린다', () => {
    const html = sheet([someone]);

    expect(html).toContain('나비독서');
    expect(html).toContain('@nabi');
  });

  it('받는 중(null)에는 「없어요」를 먼저 깜빡이지 않는다', () => {
    expect(sheet(null)).not.toContain('아직 아무도');
  });

  it('0명이면 안내가 선다 — 그 사이 취소돼 빈 명단이 올 수 있다', () => {
    expect(sheet([])).toContain('아직 아무도 누르지 않았어요.');
  });

  it('실패하면 그 자리에서 다시 받는 길을 준다 — 빈 시트가 막다른 길이 되지 않게', () => {
    expect(sheet(null, '불러오지 못했어요')).toContain('다시');
  });
});

/**
 * 여백에 들어오며 측정이 끝났다는 고지 — 「여백은 독서가 아니다」(사용자 결정 2026-08-22)라
 * <b>진입이 곧 종료</b>다. 말없이 끝나면 사용자에겐 시간이 사라진 것으로 읽히므로, 끝쳤으면 그 자리에서 밝힌다.
 *
 * <p>읽기({@link MarginView})·쓰기({@link StoryComposer}) <b>둘 다</b> 진입점이라 각자 고지를 진다 —
 * 홈 문은 여백 상세를 거치지 않고 작성으로 직행하므로, 한쪽에만 달면 그 경로가 조용히 비어 있다.
 */
describe('측정 종료 고지 (timerStopped)', () => {
  const composer = (timerStopped: boolean) =>
    render(
      <StoryComposer
        book={{ id: 7, title: '데미안', author: '헤르만 헤세', coverUrl: null, isPublic: true }}
        onDone={() => {}}
        onCancel={() => {}}
        onError={() => {}}
        timerStopped={timerStopped}
      />,
    );

  it('측정을 끝내고 들어온 여백에는 고지가 뜨다', () => {
    expect(view(margin(), { timerStopped: true })).toContain(TIMER_STOPPED_NOTICE);
  });

  it('측정 중이 아니었으면 고지가 없다 — 늘 뜨면 아무 뜻도 없는 문구가 된다', () => {
    expect(view(margin())).not.toContain(TIMER_STOPPED_NOTICE);
  });

  it('작성 화면도 같은 고지를 진다 — 홈 문은 여백 상세를 거치지 않고 여기로 직행한다', () => {
    expect(composer(true)).toContain(TIMER_STOPPED_NOTICE);
  });

  it('작성 화면도 측정 중이 아니었으면 고지가 없다', () => {
    expect(composer(false)).not.toContain(TIMER_STOPPED_NOTICE);
  });
});

/**
 * 「함께 걸기」 — 글 하나를 <b>같은 책을 보는 누구에게나</b> 여는 opt-in(2026-08-22 책축 개방).
 *
 * <p>노출 판정은 전부 서버다(책 PUBLIC AND (팔로워 OR shared)) — 화면이 재현하지 않는다.
 * 여기서 재는 것은 ① 고지 문구가 책 공개 여부로 갈리는가 ② 칩·손잡이가 상태를 말하는가뿐이다.
 */
describe('함께 걸기 — 고지 문구 (M-1)', () => {
  it('공개 책이면 지금 당장 모두에게 보인다고 말한다', () => {
    expect(shareNotice(true)).toBe('이 책을 보는 모두에게 보여요.');
  });

  it('비공개 책이면 「공개로 바꾸면」이라는 조건이 앞선다 — 켜도 지금은 아무에게도 안 보인다', () => {
    expect(shareNotice(false)).toBe('책을 공개로 바꾸면 이 책을 보는 모두에게 보여요.');
  });

  it('필드를 안 보내는 옛 서버는 공개로 간주한다 — 새는 글을 안 샌다고 말하는 쪽이 더 위험한 거짓말이다', () => {
    expect(shareNotice(undefined)).toBe(shareNotice(true));
  });
});

/**
 * 「모두의 여백」 칩 — <b>상태 표시 전용</b>이다(2026-08-22 게시판 개편). 켜고 끄는 일은 ⋯ 시트로
 * 옮겼으므로 칩은 더 이상 버튼이 아니다.
 *
 * <p>칩이 서는 조건에 <b>관리 손잡이의 유무</b>가 함께 들어가는 것이 핵심이다: 책축 목록은 전부 올라간
 * 글이라 모든 행에 같은 딱지가 붙으면 정보량이 0이 된다.
 */
describe('「모두의 여백」 칩 — 상태 표시 (M-2)', () => {
  const card = (shared: boolean | undefined, onOpenMenu?: (e: MarginEntry) => void) =>
    render(<MarginCard entry={entry(1, { shared })} now={NOW} onOpenMenu={onOpenMenu} />);

  it('올린 글에는 「모두의 여백」이라고 적힌다 — 상태가 보여야 내릴 생각도 든다', () => {
    expect(card(true, () => {})).toContain('모두의 여백');
  });

  it('안 올린 글에는 칩이 없다 — 꺼짐을 딱지로 박제하지 않는다', () => {
    const html = card(false, () => {});

    expect(html).toContain('문장 1'); // 카드 자체는 그려졌다(부재 단언의 쌍)
    expect(html).not.toContain('모두의 여백');
  });

  it('관리 손잡이가 없는 목록에는 칩이 아예 없다 — 전부 올라간 목록에서는 정보가 0이다', () => {
    const html = card(true);

    expect(html).toContain('문장 1');
    expect(html).not.toContain('모두의 여백');
  });

  it('필드를 안 보내는 옛 서버(undefined)는 꺼짐으로 읽는다 — 안 올린 글을 올렸다고 하는 쪽이 더 위험하다', () => {
    const html = card(undefined, () => {});

    expect(html).toContain('문장 1');
    expect(html).not.toContain('모두의 여백');
  });
});

/**
 * 글 관리 시트 — 행의 동작(올리기·내리기·지우기)이 ⋯ 하나로 접힌 자리. 자체 구현 `Sheet`를 쓰므로
 * 정적 렌더에서도 마크업이 나온다(TDS 포털 시트였다면 통째로 비었을 것이다).
 */
describe('글 관리 시트 (MarginMenuSheet)', () => {
  const sheet = (extra: Partial<MarginEntry>, o: { canShare?: boolean; confirming?: boolean } = {}) =>
    render(
      <MarginMenuSheet
        entry={entry(1, extra)}
        canShare={o.canShare ?? true}
        confirming={o.confirming ?? false}
        busy={false}
        onShare={() => {}}
        onConfirmDelete={() => {}}
        onDelete={() => {}}
        onClose={() => {}}
      />,
    );

  it('안 올린 글이면 올리는 길을 준다', () => {
    expect(sheet({ shared: false })).toContain('모두의 여백에 올리기');
  });

  it('올린 글이면 내리는 길로 바뀐다', () => {
    expect(sheet({ shared: true })).toContain('모두의 여백에서 내리기');
  });

  it('책축 좌표가 없는 책이면 올리기 줄 자체가 없다 — 올릴 자리가 없다', () => {
    const html = sheet({ shared: false }, { canShare: false });

    expect(html).toContain('지우기'); // 시트는 그려졌다(부재 단언의 쌍)
    expect(html).not.toContain('모두의 여백에');
  });

  it('평소엔 지우기 한 줄, 누르면 확인이 붙는다 — 되돌릴 수 없는 동작이다', () => {
    const idle = sheet({});
    const confirming = sheet({}, { confirming: true });

    expect(idle).toContain('지우기');
    expect(idle).not.toContain('이 글을 지울까요?');
    expect(confirming).toContain('이 글을 지울까요?');
    expect(confirming).toContain('정말 지우기');
  });
});

/**
 * 게시판 탭줄 — 두 좌표계(사람축·책축)를 한 화면에서 오간다. 알약이 아니라 밑줄 2분할이라
 * 「필터」가 아니라 「탭」으로 읽힌다.
 */
describe('게시판 탭줄 (MarginTabs)', () => {
  const tabs = (tab: 'mine' | 'all', mineCount: number | null, allCount: number | null) =>
    render(<MarginTabs tab={tab} mineCount={mineCount} allCount={allCount} onSelect={() => {}} />);

  it('두 탭의 이름은 「내가 쓴 여백」과 「모두의 여백」이다', () => {
    const html = tabs('mine', 3, 12);

    expect(html).toContain('내가 쓴 여백 3');
    expect(html).toContain('모두의 여백 12');
  });

  it('선택된 쪽만 눌린 상태다 — 둘 다 켜지면 어디 있는지 알 수 없다', () => {
    expect(tabs('all', 3, 12).match(/aria-pressed="true"/g)).toHaveLength(1);
    expect(tabs('mine', 3, 12).match(/aria-pressed="true"/g)).toHaveLength(1);
  });
});

/**
 * 긴 글 접기 — 게시판이 되려면 행이 짧아야 하는데 여백 글엔 제목이 없다. 넘침 <b>실측</b>은 정적 렌더
 * 하니스에서 안 돌아 계측이 불가능하므로, 글자 수 임계로 가른다(결정론이라 테스트가 붙는다).
 *
 * <p>⚠️ 아래 74·76은 구현 상수(`CLAMP_CHARS = 75`)와 <b>손으로 맞춘 값</b>이다. 상수를 테스트가 import하면
 * 임계를 0으로 바꿔도 두 단언이 함께 미끄러져 통과한다 — 그러면 계측기가 죽는다.
 */
describe('긴 글 접기 (더보기)', () => {
  const text = (n: number) => '가'.repeat(n);
  const card = (t: string, o: { expanded?: boolean; foldable?: boolean } = {}) =>
    render(
      <MarginCard
        entry={entry(1, { text: t })}
        now={NOW}
        expanded={o.expanded}
        onToggleExpand={o.foldable === false ? undefined : () => {}}
      />,
    );

  it('임계를 넘는 글은 세 줄에서 접히고 「더보기」가 붙는다', () => {
    const html = card(text(76));

    expect(html).toContain('-webkit-line-clamp:3');
    expect(html).toContain('더보기');
  });

  it('임계 이하의 글은 접지 않는다 — 한 줄 글에 「더보기」가 붙으면 잡음이다', () => {
    const html = card(text(74));

    expect(html).toContain(text(74)); // 본문은 그려졌다(부재 단언의 쌍)
    expect(html).not.toContain('-webkit-line-clamp');
    expect(html).not.toContain('더보기');
  });

  it('펼친 글은 클램프가 풀리고 「접기」로 바뀐다 — 되돌릴 길이 없으면 목록이 영영 길어진다', () => {
    const html = card(text(91), { expanded: true });

    expect(html).toContain('접기');
    expect(html).not.toContain('-webkit-line-clamp');
  });

  it('펼치기 손잡이가 없으면 접히기만 한다 — 서재 미리보기는 「전체 보기」가 출구다', () => {
    const html = card(text(91), { foldable: false });

    expect(html).toContain('-webkit-line-clamp:3');
    expect(html).not.toContain('더보기');
  });
});

describe('책축 탭 라벨 (M-6)', () => {
  it('개수를 알면 이름 뒤에 붙인다', () => {
    expect(marginTabLabel('내 여백', 3)).toBe('내 여백 3');
  });

  it('아직 안 받았으면 이름만 — 0을 먼저 그리면 「글이 없다」는 거짓말이 된다', () => {
    expect(marginTabLabel('모두', null)).toBe('모두');
  });

  it('진짜 0은 0으로 그린다 — 모르는 것과 없는 것은 다르다', () => {
    expect(marginTabLabel('모두', 0)).toBe('모두 0');
  });
});

/**
 * 「이 책의 여백」 — 사람 좌표 없이 isbn13 하나로 서는 화면. 상태는 전부 밖에서 받는다
 * (정적 렌더 하니스가 「담기 안내」·「빈 상태」 분기에 닿는 유일한 길).
 */
describe('이 책의 여백 — 책축 목록 (M-3)', () => {
  const shared = (id: number, extra: Partial<SharedMarginEntry> = {}): SharedMarginEntry => ({
    id,
    text: `함께 건 글 ${id}`,
    quote: null,
    bgCode: 'paper',
    createdAt: new Date(NOW - HOUR).toISOString(),
    likeCount: 0,
    liked: false,
    authorLoginId: 'reader',
    authorNickname: '옆자리 독자',
    ...extra,
  });

  const all = (extra: Partial<BookMarginAllResponse> = {}): BookMarginAllResponse => ({
    book: { isbn13: '9791168340084', title: '데미안', author: '헤르만 헤세', coverUrl: null },
    myBookId: null,
    totalCount: 1,
    entries: [shared(1)],
    ...extra,
  });

  const view = (data: BookMarginAllResponse) =>
    render(
      <BookMarginAllView
        data={data}
        now={NOW}
        error={null}
        onBack={() => {}}
        onToggleLike={() => {}}
        onOpenProfile={() => {}}
      />,
    );

  it('책이 주인공이다 — 헤더는 제목·저자뿐이고 주인 이름이 없다', () => {
    const html = view(all());

    expect(html).toContain('데미안');
    expect(html).toContain('헤르만 헤세');
  });

  it('카드마다 작성자 줄이 붙는다 — 여러 사람의 글이 한 목록에 섞이므로 누가 썼는지가 정보다', () => {
    const html = view(all());

    expect(html).toContain('옆자리 독자');
    expect(html).toContain('@reader');
  });

  it('개수는 상한이 아니라 서버가 센 진짜 값이다 — 100장에서 잘려도 헤더는 전부를 말한다', () => {
    expect(view(all({ totalCount: 137 }))).toContain('글 137');
  });

  it('아직 아무도 안 걸었으면 그렇게 말한다', () => {
    expect(view(all({ totalCount: 0, entries: [] }))).toContain('아직 함께 걸린 글이 없어요');
  });

  it('안 가진 책이면 담는 길을 안내한다 — 버튼이 아니라 문구다(검색으로 뒤로 가면 담기가 있다)', () => {
    expect(view(all())).toContain('내 서재에 담으면');
  });

  it('가진 책이면 그 안내가 없다 — 이미 담긴 책에 담으라고 하지 않는다', () => {
    const html = view(all({ myBookId: 42 }));

    expect(html).toContain('글 1'); // 목록은 그대로 그려진다(부재 단언의 쌍)
    expect(html).not.toContain('내 서재에 담으면');
  });

  /**
   * R10 — 책축 목록은 <b>전부 올라간 글</b>이라 칩이 모든 행에 붙으면 정보가 0이고, 남의 글이라
   * 관리 손잡이도 없다. 반대로 작성자 줄은 이 목록의 핵심 정보라 반드시 남는다.
   */
  it('칩도 ⋯ 도 없고 작성자 이름만 글마다 남는다', () => {
    const html = view(all());

    expect(html).toContain('@reader'); // 작성자 줄은 그대로다(부재 단언의 쌍)
    expect(html).not.toContain('모두의 여백');
    expect(html).not.toContain('aria-label="이 글 관리"');
  });

  /** R11 — 서재에 없는 책엔 글을 남길 수 없다. 손잡이(`onCompose`)가 없으면 버튼 자체가 안 선다. */
  it('글쓰기 손잡이가 없으면 게시판 머리가 비어 있다 — 담기 전에는 남길 수 없다', () => {
    const html = view(all());

    expect(html).toContain('글 1'); // 게시판 머리는 그려졌다(부재 단언의 쌍)
    expect(html).not.toContain('글쓰기');
  });

  it('손잡이를 넘기면 게시판 머리 우상단에 글쓰기가 선다 — 내 책의 「모두의 여백」 탭이 그 자리다', () => {
    const html = render(
      <BookMarginAllView
        data={all({ myBookId: 42 })}
        now={NOW}
        error={null}
        onBack={() => {}}
        onToggleLike={() => {}}
        onOpenProfile={() => {}}
        onCompose={() => {}}
      />,
    );

    expect(html).toContain('글쓰기');
  });
});
