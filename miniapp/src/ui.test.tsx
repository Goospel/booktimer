import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import type { DashboardResponse } from './api';
import { History } from './screens/History';
import { Home } from './screens/Home';
import {
  BookCover,
  COVER_PALETTE,
  ErrorMessage,
  GrassGrid,
  PENCIL_FRAME,
  Screen,
  coverColor,
  coverSource,
  initialOf,
  sectionStyle,
} from './ui';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

// 홈이 렌더 중에 알림 동의 캐시를 읽는다. 여기선 describe 본문에서도 홈을 그리므로(수집 시점) 모듈 최상단에서 심는다.
stubLocalStorage();

/**
 * 화면 껍데기 — 제목은 이제 **선택**이다. 홈처럼 첫 카드가 곧 히어로인 화면에서 제목 행은
 * 정보를 하나도 안 보태면서 자리만 먹는다(「오늘」은 바로 아래 「오늘 읽은 시간」의 중복이었다).
 */
describe('화면 껍데기 (Screen)', () => {
  const screen = (title?: string) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Screen title={title}>본문</Screen>
      </TDSMobileProvider>,
    );

  it('제목을 주면 그린다 — 목록형 화면은 여전히 이름을 단다', () => {
    expect(screen('내 서재')).toContain('내 서재');
  });

  it('제목이 없으면 헤더 행 자체를 안 그린다 — 빈 행이 남으면 제거한 값이 사라진다', () => {
    const markup = screen();

    expect(markup).toContain('본문');
    expect(markup).not.toContain('margin-bottom:20px'); // 제목 행의 서명
  });
});

/**
 * 뒤로가기 손잡이 — <b>글자가 붙은 알약을 제목 위 줄</b>에 세운다.
 *
 * <p>옛 모양은 배경 없는 `←` 글리프를 제목 옆에 둔 것이었는데 사용자 제보로 두 번 실패했다: 배경이
 * 없어 버튼으로 안 보이고, 직선 화살표는 「이전 화면」보다 「왼쪽 이동」으로 읽힌다. 아이콘을 아무리
 * 다듬어도 뜻은 추론에 맡겨지므로 <b>글자</b>를 붙였다 — 인식률을 아이콘 디자인에 걸지 않는다.
 */
describe('화면 껍데기 — 뒤로가기 손잡이', () => {
  const withBack = (title?: string, onBack?: () => void) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Screen title={title} onBack={onBack}>
          본문
        </Screen>
      </TDSMobileProvider>,
    );

  it('글자가 붙어 있다 — 아이콘만이면 뜻을 추론해야 한다', () => {
    expect(withBack('여백', () => {})).toContain('돌아가기');
  });

  it('제목보다 위에 온다 — 제목 옆에 끼면 제목 행의 장식으로 읽힌다', () => {
    const markup = withBack('여백', () => {});

    // 둘 다 있는지 먼저 본다 — 없으면 indexOf가 -1이라 순서 단언이 저절로 참이 된다(공허한 계측).
    expect(markup).toContain('돌아가기');
    expect(markup.indexOf('돌아가기')).toBeLessThan(markup.indexOf('여백'));
  });

  it('onBack이 없으면 안 그린다 — 탭 루트는 갈 곳이 없다', () => {
    const markup = withBack('여백');

    expect(markup).toContain('여백');
    expect(markup).not.toContain('돌아가기');
  });

  it('제목이 없어도 그린다 — 나갈 길은 제목 유무와 무관하다', () => {
    expect(withBack(undefined, () => {})).toContain('돌아가기');
  });
});

/**
 * 제목 위 슬롯(`above`) — 화면 소속이 아니라 그 위에 얹히는 도구(책방의 검색) 자리다.
 * 본문에 끼우면 「…님의 책방」 아래에 검색창이 오는 어색한 순서가 된다(사용자 제보).
 */
describe('화면 껍데기 — 제목 위 슬롯 (above)', () => {
  const withAbove = (title?: string) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Screen title={title} above={<i>도구줄</i>}>
          본문
        </Screen>
      </TDSMobileProvider>,
    );

  it('제목보다 위에 그린다 — 슬롯만 만들고 자리를 안 옮기면 아무것도 안 바뀐다', () => {
    const markup = withAbove('내 책방');

    expect(markup).toContain('도구줄');
    expect(markup).toContain('내 책방');
    expect(markup.indexOf('도구줄')).toBeLessThan(markup.indexOf('내 책방'));
  });

  it('제목이 없는 화면에서도 그린다 — 제목 유무는 위에 얹힌 도구와 무관하다', () => {
    const markup = withAbove();

    expect(markup).toContain('도구줄');
    expect(markup.indexOf('도구줄')).toBeLessThan(markup.indexOf('본문'));
  });
});

/**
 * 제목 밑줄 슬롯(`subtitle`) — 제목에 딸린 식별자(@핸들)라 제목과 떨어지면 다른 정보처럼 읽힌다.
 * 그래서 있으면 제목 행의 아래 여백을 20 → 3으로 좁혀 밀착시킨다.
 */
describe('화면 껍데기 — 제목 밑줄 슬롯 (subtitle)', () => {
  const withSubtitle = (subtitle?: ReactNode) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Screen title="내 책방" subtitle={subtitle}>
          본문
        </Screen>
      </TDSMobileProvider>,
    );

  it('제목 다음·본문보다 앞에 온다', () => {
    const markup = withSubtitle(<i>@goospel</i>);

    expect(markup.indexOf('내 책방')).toBeLessThan(markup.indexOf('@goospel'));
    expect(markup.indexOf('@goospel')).toBeLessThan(markup.indexOf('본문'));
  });

  it('있으면 제목 행에 밀착시킨다 — 20px이 그대로면 @핸들이 제목과 떨어져 다른 정보로 읽힌다', () => {
    const markup = withSubtitle(<i>@goospel</i>);

    expect(markup).toContain('margin-bottom:3px');
    expect(markup).not.toContain('margin-bottom:20px');
  });

  it('없으면 지금 그대로 — subtitle 도입이 다른 화면의 제목 간격을 건드리면 안 된다', () => {
    const markup = withSubtitle();

    expect(markup).toContain('margin-bottom:20px');
    expect(markup).not.toContain('margin-bottom:3px');
  });
});

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
    previousLoginId: null,
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
    emailVerified: true,
    ...overrides,
  };
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard}
        selectedBookId={undefined}
        onSelectBook={() => {}}
        onTimerChange={() => {}}
        celebrate={false}
        onGoGoal={() => {}}
        goalAdPending={false}
        onGoSettings={() => {}}
        onError={() => {}}
        onOpenMargin={() => {}}
        onComposeMargin={() => {}}
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

  it('격언 카드는 홈에서 사라졌다 — 인용부호가 남으면 빈 카드가 되살아난 것이다', () => {
    expect(home({})).not.toContain('“');
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
  const markup = home({ readingBooks: [{ id: 1, title: '데미안', coverUrl: null, author: null }] });

  it('섹션은 크림 캔버스 위 카드지로 뜬다 — 배경만으로는 종이톤끼리 경계가 안 보인다', () => {
    expect(markup).toContain('background:#FCFAF5'); // 웹 --card-bg
    // 경계를 긋는 주체가 1px 실선에서 연필선(border-image)으로 바뀌었다. 테두리가 통째로 빠지면
    // 종이톤 카드가 종이톤 캔버스에 녹아 사라지므로, 그리는 수단이 실재하는지를 못 박는다.
    expect(markup).toContain('border:1px solid transparent');
    expect(markup).toMatch(/border-image:url\(&quot;data:image\/svg\+xml/);
  });

  it('그림 폭을 border-width와 분리해 레이아웃을 밀지 않는다', () => {
    // `8 / 8px`의 뒷값이 화면에 그릴 폭이고, 요소의 border는 1px 그대로다. 이 분리가 깨지면
    // 테두리가 두꺼워진 만큼 카드가 커져 화면 전체가 밀린다.
    expect(PENCIL_FRAME).toMatch(/\s8\s\/\s8px\sstretch$/);
    expect(sectionStyle.border).toBe('1px solid transparent');
  });

  it('타일 반복(round)이 아니라 stretch다 — 농도 얼룩이 이음매마다 끊긴다', () => {
    expect(PENCIL_FRAME).not.toMatch(/\sround$/);
  });

  it('선을 휘게 하는 필터를 쓰지 않는다 — 굴곡은 stretch 압축을 만나면 지글거린다', () => {
    // border-image는 300px 타일을 요소 폭에 맞춰 늘리고 줄인다. 좁은 버튼에서는 3배 넘게 압축되는데,
    // 굴곡(feDisplacementMap)이 있으면 파장도 같은 배율로 짧아져 변위가 선 두께를 넘어선다 —
    // 그 순간 선은 휘는 게 아니라 가장자리가 깎여 「픽셀이 깨진 선」으로 보인다(실측 반려).
    expect(PENCIL_FRAME).not.toContain('feDisplacementMap');
  });

  it('연필의 정체는 흔들림이 아니라 농도다 — 진하기를 얼룩지게 하는 필터가 있다', () => {
    // 고주파 노이즈를 선의 알파에 곱해, 흑연이 종이 결에 걸려 생기는 농도 변화를 만든다.
    // 고주파 입자는 압축돼도 고와질 뿐이라 굴곡과 달리 지글거리지 않는다 — 위 테스트와 한 쌍이다.
    expect(PENCIL_FRAME).toContain('feComposite');
    expect(PENCIL_FRAME).toContain("operator='in'");
  });

  it('화면 제목은 웹 브랜드와 같은 세리프(고운바탕)로 쓴다', () => {
    // 홈은 제목 행이 없으므로(첫 카드가 곧 히어로) 제목을 다는 화면으로 본다.
    const titled = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Screen title="내 서재">본문</Screen>
      </TDSMobileProvider>,
    );

    expect(titled).toMatch(/font-family:[^"]*Gowun Batang/);
  });

  // 「홈 잔디 미리보기는 카드 폭을 채운다」는 홈에서 잔디 카드가 빠지며 함께 사라졌다(그 자리는 피드 박스다).
  // `GrassGrid`의 fill 변형 자체는 위 「잔디 가로 채움」이 컴포넌트를 직접 렌더해 계속 계측한다.
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

  it('민 버튼이 글자색을 물려받는다 — iOS UA 기본이 시스템 블루라 안 누르면 책 제목이 파랗게 뜬다', () => {
    // 선택자가 딱 `button`이어야 한다: 클래스를 얹으면(0-0-1 → 0-0-2) TDS Button까지 사정권에 든다.
    expect(read('./global.css')).toMatch(/(?:^|\n)button\s*\{[^}]*color:\s*inherit/);
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

  it('본문 폰트를 연필 손글씨로 바꾸고 실제로 받아온다 — 스택만 바꾸면 폰트가 없어 시스템 폰트로 떨어진다', () => {
    // TDS도 `body`에 폰트 스택을 주입하므로 여기도 `html body`(0-0-2)로 눌러야 한다.
    // Gaegu가 스택 **맨 앞**이어야 한다 — 뒤에 두면 고운돋움이 먼저 잡혀 손글씨가 영영 안 뜬다.
    expect(css).toMatch(/html\s+body\s*\{[^}]*font-family:\s*'Gaegu'/);
    expect(css).toContain('family=Gaegu');
    expect(css).toMatch(/html\s+body\s*\{[^}]*font-family:[^;]*'Gowun Dodum'/); // 폴백은 남긴다
  });
});

/**
 * 종이 결 — 화면 전체를 덮는 고정 레이어라, 여기서 잘못 고르면 **표지 사진이 통째로 바랜다**.
 *
 * 한때 회색 노이즈(`saturate 0`으로 무채색화한 불투명 rect)를 `opacity: .30`으로 깔았다. 종이 배경
 * 위에서는 결로 보이지만, 회색을 30% 섞는 것은 곧 **검정을 회색으로 들어올리는** 일이라 사진 위에서는
 * 그냥 안개다 — 책 표지가 한 겹 벗겨진 것처럼 반투명하게 바래 보였다(사용자 반려 2026-08-18).
 * 글자는 대비가 커서 버텨 웹·목 모드 검증을 모두 통과했고, 표지에서만 티가 났다.
 *
 * 그래서 노이즈를 **색이 아니라 알파**로 옮긴다(연필선을 농도 얼룩으로 고친 것과 같은 수법).
 * 대부분의 픽셀은 완전투명이고 드문 입자만 어둡게 얹혀, 결은 남되 밑에 깔린 색을 들어올리지 않는다.
 */
describe('종이 결 (global.css)', () => {
  const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8');
  // 여는 중괄호까지 붙여 규칙 블록을 잡는다 — 두 이름 다 위쪽 주석에서 먼저 언급돼(파일 8행의
  // `html:root`) 중괄호 없이 자르면 구간이 통째로 비고, 그러면 not.* 단언이 공허하게 통과한다.
  const veil = css.slice(css.indexOf('body::before {'), css.indexOf('html:root {'));

  it('노이즈를 색이 아니라 알파로 얹는다 — 무채색 회색을 깔면 표지의 검정이 들려 뿌옇게 바랜다', () => {
    expect(veil).toMatch(/feColorMatrix type='matrix'/); // 알파 행을 직접 쓴다
    expect(veil).not.toContain("type='saturate'"); // 회색 장막
  });

  it('레이어 전체를 반투명으로 깔지 않는다 — opacity는 밑의 모든 픽셀을 함께 들어올린다', () => {
    expect(veil).not.toMatch(/opacity:\s*0?\.\d/);
  });

  it('결이 종이와 함께 스크롤한다 — fixed면 뷰포트에 못 박혀 결만 제자리에 남는다', () => {
    expect(veil).toMatch(/position:\s*absolute/);
    expect(veil).not.toMatch(/position:\s*fixed/);
  });

  it('absolute의 기준을 body로 잡는다 — 기준이 없으면 초기 컨테이닝 블록(첫 화면 높이)만 덮는다', () => {
    expect(css).toMatch(/body\s*\{[^}]*position:\s*relative/);
  });

  it('떠 있는 것 밑에 깔린다 — 결이 고정 요소 위를 미끄러지면 같은 어색함이 뒤집혀 재발한다', () => {
    // 미니앱의 고정 요소: 탭바 100 · 시트 딤 200 · 시트 201. 결은 그 아래, 본문 위.
    const z = Number(/z-index:\s*(\d+)/.exec(veil)?.[1]);
    expect(z).toBeGreaterThan(0);
    expect(z).toBeLessThan(100);
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

  it('primary 버튼의 토스 블루 채움을 눕히고 연한 세이지로 바꾼다 — 인라인 프로퍼티라 !important가 필요하다', () => {
    expect(css).toMatch(/--button-background-color:\s*#3182f6/); // 선택자 키(토스 블루 인라인 값)
    // 채움을 투명으로 눕히지 않으면 TDS 내부 레이어가 테두리를 통째로 덮는다(실측).
    expect(css).toMatch(/--button-background-color:\s*transparent\s*!important/);
    expect(css).toMatch(/background-color:\s*rgba\(110,\s*138,\s*106,\s*0\.2\)\s*!important/); // 연한 세이지
  });

  it('빗살무늬로 채우지 않는다 — 사선이 글자를 가로질러 지저분하다(사용자 반려)', () => {
    expect(css).not.toContain('repeating-linear-gradient');
  });

  it('primary 글자를 잉크색으로 되돌린다 — 연한 채움 위에서 흰 글자는 읽히지 않는다', () => {
    expect(css).toMatch(/--button-color:\s*#4F6B4C\s*!important/); // 웹 --accent-hover
  });

  it('weak 버튼은 채움 없이 흐린 연필선만 — 이게 primary(연한 채움)와의 위계를 만든다', () => {
    expect(css).toMatch(/--button-background-color:\s*rgba\(100,\s*168,\s*255,\s*0\.15\)/); // 선택자 키
    expect(css).toMatch(/border-image:\s*var\(--pencil-frame-soft\)/);
  });

  it('연필 프레임 두 종이 정의돼 있다 — 변수가 비면 border-image가 조용히 사라진다', () => {
    expect(css).toMatch(/--pencil-frame:\s*url\("data:image\/svg\+xml/);
    expect(css).toMatch(/--pencil-frame-soft:\s*url\("data:image\/svg\+xml/);
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
 * 표지 로드 시점 — 목록(서재·프로필·여백)은 접힌 아래까지 길어 lazy가 맞지만, 홈 캐러셀은
 * 첫 화면 한가운데에 있고 탭을 오갈 때마다 재마운트돼 lazy면 표지가 한 박자 늦게 뜬다.
 */
describe('표지 로드 시점', () => {
  const cover = (eager?: boolean) => renderToStaticMarkup(<BookCover url="https://img/a.jpg" eager={eager} />);

  it('기본은 lazy — 접힌 아래 목록의 표지까지 미리 받을 이유가 없다', () => {
    expect(cover()).toContain('loading="lazy"');
  });

  it('eager를 켠 자리만 즉시 로드 — 첫 화면 한가운데 표지는 기다리게 두지 않는다', () => {
    expect(cover(true)).toContain('loading="eager"');
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
    const markup = errorBox('글을 너무 자주 남겼어요.');

    expect(markup).toContain('글을 너무 자주 남겼어요.');
    expect(markup).not.toContain('다시 시도');
  });

  it('실패가 없으면 아무것도 그리지 않는다', () => {
    // 프로바이더는 전역 스타일을 뱉으므로 이 단언만 맨몸으로 그린다.
    expect(renderToStaticMarkup(<ErrorMessage message={null} onRetry={() => {}} />)).toBe('');
  });
});

/**
 * 뒤로가기 잠금 — 요청이 도는 중에는 나가지 못하게 한다. 하단 「돌아가기」 버튼이 `disabled={busy}`로
 * 하던 일을, 그 버튼을 걷고 상단 손잡이로 통일하면서 그대로 옮겨 온 것이다(책 추가·계정 연결).
 * 잠금을 「손잡이를 감추기」로 하면 34px 줄이 사라졌다 나타나 화면이 튄다 — 그래서 disabled다.
 */
describe('화면 껍데기 — 뒤로가기 잠금', () => {
  const backAt = (markup: string) => markup.slice(0, markup.indexOf('돌아가기'));
  const withBack = (backDisabled?: boolean) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Screen title="책 추가" onBack={() => {}} backDisabled={backDisabled}>
          본문
        </Screen>
      </TDSMobileProvider>,
    );

  it('잠그면 disabled가 붙는다', () => {
    expect(backAt(withBack(true))).toContain('disabled');
  });

  it('기본은 안 잠근다 — 대부분의 화면은 언제든 나갈 수 있어야 한다', () => {
    const markup = withBack();

    expect(markup).toContain('돌아가기');
    expect(backAt(markup)).not.toContain('disabled');
  });
});
