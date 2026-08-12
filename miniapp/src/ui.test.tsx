import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { DashboardResponse } from './api';
import { History } from './screens/History';
import { Home } from './screens/Home';
import { COVER_PALETTE, ErrorMessage, GrassGrid, coverColor, coverSource, initialOf } from './ui';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

// 홈이 렌더 중에 알림 동의 캐시를 읽는다. 여기선 describe 본문에서도 홈을 그리므로(수집 시점) 모듈 최상단에서 심는다.
stubLocalStorage();

/**
 * 잔디 렌더 안전망 — 잔디 그리기를 History에서 `GrassGrid`(ui.tsx)로 추출하는 리팩터가
 * 같은 데이터에 대해 같은 마크업을 내는지 못 박는다(동작 보존 리팩터라 추출 전후 모두 통과해야 한다).
 */
describe('잔디 렌더', () => {
  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <History graph={graph} />
    </TDSMobileProvider>,
  );

  it('칸마다 level 색을 칠한다 — 0~4 단계가 웹 잔디와 같은 팔레트', () => {
    // 웹 app.css --grass-0..4 와 같은 값(잔디 색의 단일 출처는 웹 브랜드 팔레트다).
    for (const color of ['#EAE4D7', '#C3D9B0', '#94BE7F', '#5E9250', '#35662F']) {
      expect(markup).toContain(`background:${color}`);
    }
  });

  it('날짜 없는 칸은 그리드 가장자리 placeholder라 투명하게 둔다', () => {
    expect(markup).toContain('background:transparent');
  });

  it('수동 기록 칸은 테두리로 구분한다', () => {
    expect(markup).toContain('outline:1px solid #9A9486'); // 웹 --neutral-3
  });

  it('주 × 일 수만큼 칸을 그린다', () => {
    // 칸 크기(11px)까지 함께 봐야 잔디 칸이다 — 범례 스와치도 같은 2px 라운드를 쓴다.
    expect(markup.match(/width:11px;height:11px/g)).toHaveLength(6);
  });

  it('가로 스크롤 컨테이너가 스크롤바 숨김 클래스를 쓴다 — 규칙만 있고 안 붙이면 아무 일도 안 난다', () => {
    expect(markup).toContain('class="no-scrollbar"');
  });
});

/**
 * 잔디 가로 채움 — 홈 미리보기는 고정 칸(px)이라 카드 왼쪽에 좁게 붙어 있었다. `fill`이면 칸 크기를
 * 카드 폭이 정하도록 뒤집는다(주 컬럼이 폭을 나눠 갖고, 칸은 정사각 비율로 따라온다).
 * 기록 화면은 가로 스크롤이 전제라 이 모드를 안 쓴다 — 그래서 **기본 모드 무변경**도 함께 못 박는다.
 */
describe('잔디 가로 채움 (GrassGrid fill)', () => {
  const render = (fill: boolean) => renderToStaticMarkup(<GrassGrid weeks={graph.weeks} fill={fill} />);

  it('fill이면 주 컬럼이 폭을 나눠 갖고 칸은 정사각 비율로 따라온다', () => {
    const markup = render(true);

    expect(markup).toContain('gap:3px;width:100%'); // 컨테이너가 폭을 다 쓴다
    expect(markup).toContain('flex:1'); // 주 컬럼이 그 폭을 나눠 갖는다
    expect(markup).toContain('width:100%;aspect-ratio:1 / 1'); // 칸은 컬럼 폭 = 정사각
    expect(markup).not.toMatch(/width:\d+px/); // 고정 px가 남아 있으면 폭을 다 못 채운다
  });

  it('fill이 아니면 고정 칸 그대로 — 기록 화면(가로 스크롤)은 무변경이다', () => {
    const markup = render(false);

    expect(markup).toContain('width:11px');
    expect(markup).not.toContain('aspect-ratio');
    expect(markup).not.toContain('flex:1');
  });
});

/** 홈은 시각 화면이라 단위테스트를 두지 않지만, 목표 0(0으로 나누기) 경계만은 계측한다. */
function home(overrides: Partial<DashboardResponse>) {
  const dashboard: DashboardResponse = {
    nickname: '구스펠',
    loginId: 'goospel',
    profileCharacterCode: null,
    remainingSeconds: 900,
    carriedDebtSeconds: 0,
    todayGoalSeconds: 3600,
    carryover: false,
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    activeBookTotalSeconds: 0,
    readingBooks: [],
    finishedBooks: [],
    wantToReadBooks: [],
    recentBookId: null,
    debtWaiverAvailable: false,
    graph,
    quotes: [],
    emailVerified: true,
    ...overrides,
  };
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard}
        onTimerChange={() => {}}
        onGraphChange={() => {}}
        onGoHistory={() => {}}
        onGoLibrary={() => {}}
        onGoGoal={() => {}}
        onError={() => {}}
      />
    </TDSMobileProvider>,
  );
}

describe('홈 오늘 진행률', () => {
  it('오늘 읽은 만큼 차오른다 — 게이지도 카운트업과 같은 방향을 본다', () => {
    // 목표 1시간에 15분이 남았으면 45분을 읽은 것 = 75%. TDS ProgressBar는 이 값을 aria-valuetext로 적는다.
    expect(home({ todayGoalSeconds: 3600, remainingSeconds: 900 })).toContain('aria-valuetext="75%"');
  });

  it('목표를 초과해도 게이지는 가득 찬 채로 멈춘다 — TDS는 100%를 안 잘라 준다(실측: 116%가 그대로 샌다)', () => {
    const markup = home({ todayGoalSeconds: 3600, remainingSeconds: -600 });

    expect(markup).toContain('오늘 목표 달성');
    expect(markup).toContain('aria-valuetext="100%"');
  });

  it('목표가 0이면 게이지를 그리지 않는다 — 0으로 나누면 NaN·Infinity가 새어나온다', () => {
    const markup = home({ todayGoalSeconds: 0, remainingSeconds: 0 });

    expect(markup).not.toContain('오늘 목표'); // 게이지 라벨("오늘 목표 … · 목표까지 …")이 아예 없어야 한다
    expect(markup).not.toMatch(/NaN|Infinity/);
  });

  it('격언이 없으면 카드를 띄우지 않는다 — 빈 인용부호만 남는 걸 막는다', () => {
    expect(home({ quotes: [] })).not.toContain('“');
    expect(home({ quotes: [{ text: '책은 도끼다', author: '카프카' }] })).toContain('카프카');
  });

  it('게이지 라벨과 값을 각자 다른 블록에 둔다 — "오늘 읽은 시간45:00"으로 붙어 보였다', () => {
    const markup = home({ remainingSeconds: 900 });
    const between = markup.slice(markup.indexOf('오늘 읽은 시간') + 8, markup.indexOf('45:00'));

    expect(between).toContain('</div>');
  });
});

/** 카드·제목의 브랜드 문법 — 크림 캔버스 위 카드지(배경+보더)와 세리프 제목이 웹과 같은 위계를 만든다. */
describe('섹션 카드·화면 제목', () => {
  // 섹션은 "읽는 중인 책"이 있을 때만 그려진다 — 빈 서재로는 카드 자체가 안 나온다.
  const markup = home({ readingBooks: [{ id: 1, title: '데미안' }] });

  it('섹션은 크림 캔버스 위 카드지로 뜬다 — 배경만으로는 종이톤끼리 경계가 안 보인다', () => {
    expect(markup).toContain('background:#FCFAF5'); // 웹 --card-bg
    expect(markup).toContain('border:1px solid #E4DDD0'); // 웹 --border
  });

  it('화면 제목은 웹 브랜드와 같은 세리프(고운바탕)로 쓴다', () => {
    expect(markup).toMatch(/font-family:[^"]*Gowun Batang/);
  });

  it('홈 잔디 미리보기는 카드 폭을 채운다 — 고정 칸이면 카드 왼쪽에 좁게 붙는다', () => {
    // `border-radius:2px`까지 함께 봐야 잔디 칸이다 — TDS 버튼의 물결 효과도 `aspect-ratio:1/1`을 쓴다.
    expect(markup).toContain('width:100%;aspect-ratio:1 / 1;border-radius:2px');
  });
});

/**
 * 라이트 캔버스 고정 — 앱이 배경을 안 칠하면 다크 모드 WebView에서 캔버스가 검정이 되는데 본문 글자는
 * 어두운 색 그대로라 제목·섹션 헤더가 안 보인다. v2는 라이트 고정이라(다크 대응은 범위 밖) 강제한다.
 */
describe('배경·color-scheme', () => {
  const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

  it('color-scheme을 라이트로 못 박는다 — 브라우저가 캔버스를 어둡게 뒤집지 못하게', () => {
    expect(read('../index.html')).toContain('name="color-scheme" content="light"');
  });

  it('body에 웹 종이톤 캔버스를 칠한다 — 투명이면 기기 다크 캔버스가 그대로 비친다', () => {
    // `html body`(0-0-2)여야 한다 — TDS가 나중에 주입하는 `body`(0-0-1) 규칙과 동률이면 순서로 진다.
    expect(read('./global.css')).toMatch(/html\s+body\s*\{[^}]*background:\s*#F3EEE4/); // 웹 --bg
  });

  it('TDS Text를 블록으로 되돌린다 — TDS가 호출부의 display:block을 inline-block으로 덮어써 줄이 붙는다', () => {
    expect(read('./global.css')).toMatch(/Paragraph\.Text[^}]*display:\s*block\s*!important/);
  });

  it('가로 스크롤 영역의 스크롤바를 숨긴다 — 파이어폭스·웹킷 양쪽 다', () => {
    const css = read('./global.css');

    expect(css).toMatch(/\.no-scrollbar[^}]*scrollbar-width:\s*none/);
    expect(css).toMatch(/\.no-scrollbar::-webkit-scrollbar[^}]*display:\s*none/);
  });
});

/**
 * 웹 브랜드 테마 — TDS는 모든 색을 `--adaptive*` 변수로 소비하고 그 정의를 런타임에 `:root`로 주입한다.
 * 정적 css가 그 주입을 이기려면 명시도가 더 높은 `html:root`(0-1-1 > 0-1-0)여야 한다 — 여기서 무너지면
 * 앱 전체가 토스 블루로 되돌아가므로, 선택자와 값 둘 다 못 박는다.
 */
describe('웹 브랜드 재테마 (global.css)', () => {
  const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8');
  const override = css.match(/html:root\s*\{[^}]*\}/)?.[0] ?? '';

  it('TDS 변수 오버라이드를 html:root로 건다 — :root로 걸면 TDS 런타임 주입에 순서로 진다', () => {
    expect(override).not.toBe('');
  });

  it('블루 계열을 웹 세이지로 갈아끼운다 — 버튼·선택 탭이 여기서 결정된다', () => {
    expect(override).toMatch(/--adaptiveBlue500:\s*#6E8A6A/); // 웹 --accent
    expect(override).toMatch(/--adaptiveBlue700:\s*#4F6B4C/); // 웹 --accent-hover
  });

  it('표면·잉크를 웹 종이톤으로 갈아끼운다', () => {
    expect(override).toMatch(/--adaptiveBackground:\s*#FCFAF5/); // 웹 --card-bg
    expect(override).toMatch(/--adaptiveGrey100:\s*#FCFAF5/);
    expect(override).toMatch(/--adaptiveGrey600:\s*#6F6A5E/); // 웹 --muted
  });

  it('본문 폰트를 웹 고운돋움으로 바꾸고 실제로 받아온다 — 스택만 바꾸면 폰트가 없어 시스템 폰트로 떨어진다', () => {
    // TDS도 `body`에 폰트 스택을 주입하므로 여기도 `html body`(0-0-2)로 눌러야 한다.
    expect(css).toMatch(/html\s+body\s*\{[^}]*font-family:[^;]*'Gowun Dodum'/);
    expect(css).toContain('Gowun+Dodum');
  });
});

/**
 * TDS Button 재색칠 — Button만은 색을 CSS 변수로 안 읽는다. TDS가 JS 팔레트의 리터럴 hex를 렌더 시
 * **인라인 커스텀 프로퍼티**(`--button-background-color: #3182f6`)로 박아 넣어, `--adaptive*` 오버라이드가
 * 닿지 않는다(실측). 인라인을 이기려면 저자 규칙 + `!important`뿐이고, variant를 가릴 속성·클래스가 없어
 * (primary·danger·weak 전부 같은 class·data 속성) **인라인에 박힌 토스 블루 값 자체를 선택자 키로** 쓴다.
 * 이 방식의 부수 효과가 곧 안전장치다 — danger(빨강) 버튼은 값이 달라 애초에 매칭되지 않는다.
 */
describe('TDS Button 재색칠 (global.css)', () => {
  const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8');

  it('primary 버튼 채움을 세이지로 덮는다 — 인라인 커스텀 프로퍼티라 !important가 필요하다', () => {
    expect(css).toMatch(/--button-background-color:\s*#3182f6/); // 선택자 키(토스 블루 인라인 값)
    expect(css).toMatch(/--button-background-color:\s*#6E8A6A\s*!important/); // 웹 --accent
  });

  it('weak 버튼도 연세이지로 덮는다 — 앱 버튼 대부분이 weak다', () => {
    expect(css).toMatch(/--button-background-color:\s*#E7EEE2\s*!important/);
    expect(css).toMatch(/--button-color:\s*#4F6B4C\s*!important/); // 웹 --accent-hover
  });

  it('눌림·그라디언트·로더까지 같이 옮긴다 — 채움만 바꾸면 누를 때 파랑이 번쩍인다', () => {
    expect(css).toMatch(/--button-gradient-color:[^;]*!important/);
    expect(css).toMatch(/--button-loader-color:[^;]*!important/);
  });

  it('danger 버튼은 건드리지 않는다 — 세이지로 물들면 "빨강=위험" 신호가 죽는다', () => {
    // TDS danger 팔레트(solid #f04452 / weak fill rgba(251,136,144,.15) · 글자 #e42939)를
    // 선택자로도 값으로도 언급하지 않아야 한다 — 언급하는 순간 위험 버튼이 사정권에 든다.
    for (const dangerHex of ['#f04452', '#e42939', '251, 136, 144']) {
      expect(css).not.toContain(dangerHex);
    }
  });
});

/**
 * 표지 로드 실패 — `coverUrl`이 있어도 원격 이미지(알라딘 등)는 실패할 수 있고, 그러면 브라우저의
 * 깨진 이미지 아이콘이 그대로 노출된다. 실패는 "표지가 없는 것"과 같이 취급한다.
 *
 * <p>정적 렌더 하니스로는 `onError`가 돌지 않으므로 판단만 순수 함수로 꺼내 계측한다 —
 * `nextStoryIndex`·`tabChangeHandler`와 같은 방식.
 */
/**
 * 무표지 자리 표지 — `BookOption`엔 표지 주소가 없어 첫 글자 + 제목색 상자로 대신한다.
 * 웹 `books/pure.ts`의 `initialOf`·`coverColor`를 옮긴 것이라 같은 책이 웹·미니앱에서 같은 색이 된다.
 */
describe('제목 이니셜·표지색', () => {
  it('제목 첫 글자를 쓴다', () => {
    expect(initialOf('데미안')).toBe('데');
  });

  it('앞뒤 공백은 버린다 — 공백이 첫 글자로 뽑히면 빈 상자가 된다', () => {
    expect(initialOf('  노인과 바다 ')).toBe('노');
  });

  it('제목이 비었으면 물음표 — 상자는 남아야 줄 높이가 안 무너진다', () => {
    expect(initialOf('   ')).toBe('?');
  });

  it('같은 제목은 항상 같은 색 — 다시 그릴 때 색이 튀면 "다른 책"으로 읽힌다', () => {
    expect(coverColor('데미안')).toBe(coverColor('데미안'));
  });

  it('제목마다 갈리게 흩뿌린다 — 상수로 굳으면 모든 책이 한 색이 된다', () => {
    const colors = new Set(['데미안', '노인과 바다', '토지', '1984', '호밀밭의 파수꾼'].map(coverColor));

    expect(colors.size).toBeGreaterThan(1);
  });

  it('팔레트 밖 색은 안 나온다 — 해시가 범위를 넘으면 배경이 undefined로 샌다', () => {
    for (const title of ['', '데미안', '아주 긴 제목의 어떤 책 제목입니다']) {
      expect(COVER_PALETTE).toContain(coverColor(title));
    }
  });
});

describe('표지 출처 결정', () => {
  it('표지가 없으면 자리 채움', () => {
    expect(coverSource(null, null)).toBeNull();
  });

  it('멀쩡한 표지는 그대로 그린다', () => {
    expect(coverSource('https://img/a.jpg', null)).toBe('https://img/a.jpg');
  });

  it('로드에 실패한 표지는 없는 것으로 친다 — 깨진 이미지 아이콘 대신 자리 채움', () => {
    expect(coverSource('https://img/a.jpg', 'https://img/a.jpg')).toBeNull();
  });

  it('다른 책이 실패한 것은 이 책에 옮지 않는다 — 목록 재사용으로 실패가 번지면 멀쩡한 표지가 사라진다', () => {
    expect(coverSource('https://img/a.jpg', 'https://img/b.jpg')).toBe('https://img/a.jpg');
  });
});

/**
 * 실패 안내 — 초기 로드가 실패하면 빨간 글자만 남아 **막다른 길**이 됐다(다시 시도하려면 미니앱을 껐다 켜야 했다).
 * 재시도가 필요한 화면만 `onRetry`를 건네고, 액션 실패처럼 되돌릴 게 없는 자리는 문구만 그대로 둔다.
 */
describe('실패 안내', () => {
  const errorBox = (message: string | null, onRetry?: () => void) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <ErrorMessage message={message} onRetry={onRetry} />
      </TDSMobileProvider>,
    );

  it('실패하면 그 자리에서 다시 받을 길을 준다', () => {
    const markup = errorBox('네트워크 연결을 확인해 주세요', () => {});

    expect(markup).toContain('네트워크 연결을 확인해 주세요');
    expect(markup).toContain('다시 시도');
    expect(markup).toContain('<button');
  });

  it('재시도할 게 없으면 문구만 — 아무 데도 안 가는 버튼을 내놓지 않는다', () => {
    const markup = errorBox('스토리를 너무 자주 올렸어요.');

    expect(markup).toContain('스토리를 너무 자주 올렸어요.');
    expect(markup).not.toContain('다시 시도');
  });

  it('실패가 없으면 아무것도 그리지 않는다', () => {
    // 프로바이더는 전역 스타일을 뱉으므로 이 단언만 맨몸으로 그린다.
    expect(renderToStaticMarkup(<ErrorMessage message={null} onRetry={() => {}} />)).toBe('');
  });
});
