import { TDSMobileProvider } from '@toss/tds-mobile';
import { readFileSync } from 'node:fs';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  MODE_KEY,
  STUDY_CLASS,
  StartToast,
  effectiveMode,
  lampOn,
  readMode,
  startToastMessage,
  timerActionView,
  toastHasBookControls,
} from './App';
import type { DashboardResponse, StudyState } from './api';
import { IDLE_STUDY } from './api';
import { ACTIVE_STUDY_RELIEF, Home, ModeToggle, heroOverline } from './screens/Home';
import { graph, stubLocalStorage, userAgent } from './test-fixtures';

/**
 * 독서/공부 타이머 모드 분리 — 모드가 무엇인지 정하는 <b>순수 규칙</b>과, 그 규칙이 실제 화면에
 * 닿았는지를 잰다.
 *
 * <p>하니스가 정적 렌더라(effect·클릭 없음) 토글 클릭·body 클래스 부착은 여기서 관측할 수 없다 —
 * 그 둘은 목 모드 실브라우저가 게이트다(T-149: 못 도는 경로에 부정 단언을 두지 않는다).
 */

vi.mock('./toss', () => ({
  REWARD_AD_GROUP_ID: '',
  watchRewardAd: vi.fn(),
  GOAL_MET_TEMPLATE_CODE: 'test-template',
  notificationAgreementSupported: () => false,
  requestNotificationAgreement: vi.fn(),
  trackEvent: vi.fn(),
  openExternal: vi.fn(),
}));

beforeEach(() => {
  stubLocalStorage();
});

/**
 * 서버 진실이 저장값을 이긴다 — 이 함수가 「재진입하면 모드가 유지된다」와 「웹에서 시작한 독서가
 * 공부 화면에 가려지지 않는다」를 <b>동시에</b> 책임진다(상태 동기화 코드 0줄).
 */
describe('effectiveMode — 진행 중 측정이 저장값을 이긴다', () => {
  it('독서 세션이 살아 있으면 저장값이 study여도 reading이다(웹에서 시작했을 수 있다)', () => {
    expect(effectiveMode(true, false, 'study')).toBe('reading');
  });

  it('둘 다 살아 있는 극단 조합에서도 reading이 이긴다 — 표시 우선순위가 흔들리지 않는다', () => {
    expect(effectiveMode(true, true, 'study')).toBe('reading');
  });

  it('공부 세션이 살아 있으면 저장값이 reading이어도 study다', () => {
    expect(effectiveMode(false, true, 'reading')).toBe('study');
  });

  it('측정이 없으면 저장값을 따른다 — 재진입 유지가 여기서 나온다', () => {
    expect(effectiveMode(false, false, 'study')).toBe('study');
    expect(effectiveMode(false, false, 'reading')).toBe('reading');
  });
});

describe('readMode — 미지값·증발은 독서로 떨어진다', () => {
  it('저장된 적 없으면 reading', () => {
    expect(readMode()).toBe('reading');
  });

  it('저장값이 study면 study', () => {
    localStorage.setItem(MODE_KEY, 'study');
    expect(readMode()).toBe('study');
  });

  it('알 수 없는 값이면 reading으로 떨어진다(옛 값·손상된 값 방어)', () => {
    localStorage.setItem(MODE_KEY, 'pomodoro');
    expect(readMode()).toBe('reading');
  });
});

describe('독서등 — 공부 모드에선 켜지 않는다(1차 결정)', () => {
  it('측정 중 홈 + 독서 모드에서만 켠다', () => {
    expect(lampOn('home', true, 'reading')).toBe(true);
  });

  it('공부 모드면 측정 중 홈이어도 안 켠다 — 밤 세이지와 파랑의 명시도 싸움 자체를 없앤다', () => {
    expect(lampOn('home', true, 'study')).toBe(false);
  });

  it('홈이 아니면 어느 모드든 안 켠다', () => {
    expect(lampOn('library', true, 'reading')).toBe(false);
  });
});

describe('탭바 원 — 무엇을 재기 시작하는지 라벨이 말한다', () => {
  it('대기 중 라벨이 모드로 갈린다(시안 「독서 시작하기」·「공부 시작하기」의 실물 자리)', () => {
    expect(timerActionView(false, 'reading').label).toBe('독서 측정 시작');
    expect(timerActionView(false, 'study').label).toBe('공부 측정 시작');
  });

  it('끝내기는 모드와 무관하다 — 멈추는 동작은 하나다', () => {
    expect(timerActionView(true, 'reading').label).toBe('측정 끝내기');
    expect(timerActionView(true, 'study').label).toBe('측정 끝내기');
  });

  it('대기 링·배경은 토큰을 탄다 — 공부 모드 색 전환이 css 한 벌로 따라오게', () => {
    expect(timerActionView(false, 'study').ring).toContain('--accentRing');
    expect(timerActionView(false, 'study').background).toContain('--adaptiveBlue700');
  });
});

describe('시작 토스트 — 공부엔 책이 없다', () => {
  it('공부 시작은 책 은유 없이 말한다', () => {
    expect(startToastMessage({ book: null, changed: false, mode: 'study' })).toBe('공부 측정을 시작했어요');
  });

  it('독서 문구는 그대로다(회귀 가드)', () => {
    expect(startToastMessage({ book: null, changed: false })).toBe('책 없이 측정을 시작했어요');
  });

  /**
   * 표지 자리와 [바꾸기]는 <b>책이 있는 측정</b>의 장치다. 공부 토스트에 남겨 두면 [바꾸기]가
   * <b>죽은 컨트롤</b>이 된다 — 누르면 교체 시트가 열리고, 진행 중 독서 세션이 없어 서버가 409
   * 「진행 중인 측정이 없습니다」로 끝낸다(사용자에겐 이유 없는 에러다).
   */
  it('공부 토스트엔 표지·[바꾸기]가 없다', () => {
    expect(toastHasBookControls({ book: null, changed: false, mode: 'study' })).toBe(false);
  });

  it('독서 토스트엔 그대로 있다 — 「무슨 책인가」를 말하고 그 자리에서 바꾸는 것이 이 장치의 존재 이유다', () => {
    expect(toastHasBookControls({ book: null, changed: false })).toBe(true);
    expect(toastHasBookControls({ book: { id: 1, title: '데미안', coverUrl: null, author: null }, changed: true })).toBe(true);
  });

  it('그 판단이 렌더까지 닿는다 — 공부 토스트 마크업에 [바꾸기]도 표지 자리도 없다', () => {
    const study = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <StartToast toast={{ book: null, changed: false, mode: 'study' }} onChange={() => {}} />
      </TDSMobileProvider>,
    );
    const reading = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <StartToast toast={{ book: null, changed: false }} onChange={() => {}} />
      </TDSMobileProvider>,
    );

    expect(study).toContain('공부 측정을 시작했어요');
    expect(study).not.toContain('바꾸기');
    expect(study).not.toContain('dashed'); // 책 없음 점선 표지 자리
    // 독서 토스트는 불변 — 위 단언들이 「그냥 다 사라졌다」로 통과하지 않게 반대편을 함께 잰다.
    expect(reading).toContain('바꾸기');
    expect(reading).toContain('dashed');
  });

  it('공부 토스트에 세이지 리터럴이 남지 않는다 — [바꾸기] 배경이 공부 모드의 마지막 누출이었다', () => {
    const study = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <StartToast toast={{ book: null, changed: false, mode: 'study' }} onChange={() => {}} />
      </TDSMobileProvider>,
    );

    expect(study).not.toContain('110, 138, 106');
  });
});

describe('히어로 오버라인', () => {
  it('공부 모드는 「오늘 공부한 시간」 — 목표가 없어 달성 분기도 없다', () => {
    expect(heroOverline('study', false)).toBe('오늘 공부한 시간');
    expect(heroOverline('study', true)).toBe('오늘 공부한 시간');
  });

  it('독서 모드는 「오늘 읽은 시간」이고, 달성이면 새싹 머리말로 갈린다(null = 새싹 분기)', () => {
    expect(heroOverline('reading', false)).toBe('오늘 읽은 시간');
    expect(heroOverline('reading', true)).toBeNull();
  });
});

/**
 * js–css 매듭 — 클래스 이름과 토큰이 한쪽에만 있으면 기능이 <b>조용히</b> 죽는다(`LAMP_CLASS` 선례).
 */
describe('공부 모드 색 — css에 실재하는가', () => {
  const css = readFileSync(new URL('./global.css', import.meta.url), 'utf8');

  it('body.study-mode 블록이 있다 — App이 붙이는 클래스와 같은 이름이라야 색이 갈린다', () => {
    expect(css).toContain(`body.${STUDY_CLASS}`);
  });

  it('시안 accent·deep이 세이지 사다리 자리를 대신한다', () => {
    expect(css).toContain('--adaptiveBlue500: #5F7E96');
    expect(css).toContain('--adaptiveBlue700: #47657C');
  });

  it('낮 기본값 토큰 두 개가 html:root에 있다 — 없으면 fallback만 남아 공부 모드가 안 따라온다', () => {
    const root = css.slice(css.indexOf('html:root {'), css.indexOf('}', css.indexOf('html:root {')));
    expect(root).toContain('--accentRing:');
    expect(root).toContain('--accentPill:');
  });
});

/**
 * 「여백은 독서가 아니다」의 실제 내용은 <b>「여백은 측정이 아니다」</b>다 — 공부 측정도 끊어야 한다.
 *
 * <p>안 끊으면 둘이 동시에 깨진다: ① 남의 글을 읽는 동안 공부 시간이 계속 쌓이고 ② 여백 탭바
 * (`MarginShell`)는 「여기선 측정이 꺼져 있다」를 <b>전제로</b> 상태 없이 그려져, 그 원이 「독서 측정
 * 시작」인 채로 눌리면 <b>두 세션이 동시에</b> 돌아간다(화면 어디에도 안 보인 채 6시간 뒤 스윕이 닫는다).
 *
 * <p>하니스가 정적 렌더라 이 흐름은 못 돌린다(T-149) — 그래서 <b>게이트가 어느 조건을 보는지</b>를
 * 소스로 잰다(`app.test.tsx`가 여는 호출을 소스로 세는 것과 같은 방식).
 */
describe('여백 진입 게이트 — 공부 측정도 끊는다', () => {
  const code = readFileSync(new URL('./App.tsx', import.meta.url), 'utf8');
  const gate = code.slice(code.indexOf('const openMargin ='), code.indexOf('const screen ='));

  it('게이트가 독서만 보지 않는다 — 공부 활성도 「끊고 연다」 쪽으로 떨어진다', () => {
    expect(gate).toContain('!dashboard.hasActiveSession && !study.hasActiveSession');
  });

  it('공부만 돌 때 stopStudy를 거쳐 연다 — 끊긴 뒤에 열려야 「끝난 줄 알았는데 돌고 있었다」가 없다', () => {
    expect(gate).toContain('stopStudy()');
    expect(gate).toContain('timerStopped: true');
  });

  it('그 종료도 지표에 남는다 — 빠지면 이 경로의 공부 세션만 집계에서 증발한다', () => {
    expect(gate).toContain("trackEvent('study_session_completed'");
  });
});

// ── 화면 ────────────────────────────────────────────────────────────────────

function dashboard(overrides: Partial<DashboardResponse> = {}, study: StudyState = IDLE_STUDY): DashboardResponse {
  return {
    nickname: '공부하는사람',
    loginId: 'studyid',
    previousLoginId: null,
    profileCharacterCode: null,
    remainingSeconds: 900,
    carriedDebtSeconds: 0,
    todayGoalSeconds: 3600,
    carryover: true,
    hasActiveSession: false,
    activeStartedAt: null,
    activeBookTitle: null,
    activeBookTotalSeconds: 0,
    activeBook: null,
    readingBooks: [{ id: 1, title: '데미안', coverUrl: null, author: null }],
    finishedBooks: [],
    wantToReadBooks: [],
    recentBookId: 1,
    graph,
    emailVerified: true,
    debtWaiverAvailable: false,
    study,
    ...overrides,
  };
}

function renderHome(
  mode: 'reading' | 'study',
  overrides: Partial<DashboardResponse> = {},
  study = IDLE_STUDY,
  celebrate = false,
) {
  return renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <Home
        dashboard={dashboard(overrides, study)}
        mode={mode}
        study={study}
        onChangeMode={() => {}}
        onBlockedModeChange={() => {}}
        selectedBookId={undefined}
        onSelectBook={() => {}}
        onTimerChange={() => {}}
        celebrate={celebrate}
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

describe('홈 — 공부 모드 렌더', () => {
  it('오버라인이 「오늘 공부한 시간」이다', () => {
    expect(renderHome('study')).toContain('오늘 공부한 시간');
    expect(renderHome('study')).not.toContain('오늘 읽은 시간');
  });

  it('오늘 누적을 시계로 그린다 — 서버 todaySeconds가 화면에 실제로 닿는지 본다', () => {
    const markup = renderHome('study', {}, { hasActiveSession: false, activeStartedAt: null, todaySeconds: 3_725 });
    expect(markup).toContain('01:02:05');
  });

  it('책 캐러셀이 없다 — 공부엔 「무엇으로 측정할까요?」가 없다', () => {
    expect(renderHome('study')).not.toContain('무엇으로 측정할까요?');
  });

  it('목표·게이지·부채 손잡이가 없다 — 부재가 곧 광고 두 개의 자연 소멸이다', () => {
    const markup = renderHome('study');
    expect(markup).not.toContain('하루 목표');
    expect(markup).not.toContain('남은 시간');
    expect(markup).not.toContain('변경 ›');
  });

  it('공부 측정 중이면 안심 문구를 말한다 — 화면을 꺼도 서버가 센다는 계약', () => {
    const markup = renderHome('study', {}, {
      hasActiveSession: true,
      activeStartedAt: new Date().toISOString(),
      todaySeconds: 0,
    });
    expect(markup).toContain(ACTIVE_STUDY_RELIEF);
  });

  /**
   * 축하 배너는 <b>독서</b> 기록에 대한 말이다("기록 탭에 첫 칸이 생겼어요" — 공부는 그 탭에 안 남는다).
   * `celebrate`는 `MainTabs`가 들고 탭 전환에 살아남으므로, 켜진 채로 토글만 넘기면 공부 화면에 뜬다.
   */
  it('첫 독서 기록 축하 배너가 안 뜬다 — 공부는 잔디 밖이라 그 말이 거짓말이 된다', () => {
    expect(renderHome('study', {}, IDLE_STUDY, true)).not.toContain('첫 독서 기록이 심어졌어요');
  });

  it('홈 피드·계정 헤더는 그대로다 — 모드는 타이머의 모드지 화면의 모드가 아니다', () => {
    expect(renderHome('study')).toContain('공부하는사람');
  });
});

describe('홈 — 독서 모드 회귀 가드', () => {
  it('기존 화면이 그대로다(오버라인·캐러셀·목표 손잡이)', () => {
    const markup = renderHome('reading');
    expect(markup).toContain('오늘 읽은 시간');
    expect(markup).toContain('무엇으로 측정할까요?');
    expect(markup).toContain('하루 목표');
  });

  it('독서 모드에선 축하 배너가 그대로 뜬다 — 위 게이트가 배너를 통째로 죽이지 않았다', () => {
    expect(renderHome('reading', {}, IDLE_STUDY, true)).toContain('첫 독서 기록이 심어졌어요');
  });

  it('공부 문구가 새지 않는다', () => {
    expect(renderHome('reading')).not.toContain('오늘 공부한 시간');
  });
});

describe('모드 토글', () => {
  const toggle = (mode: 'reading' | 'study', locked = false) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <ModeToggle mode={mode} locked={locked} onChange={() => {}} onBlocked={() => {}} />
      </TDSMobileProvider>,
    );

  it('히어로 안에 산다 — 두 모드 다 계측 손잡이로 잡힌다', () => {
    expect(renderHome('reading')).toContain('data-mode-toggle');
    expect(renderHome('study')).toContain('data-mode-toggle');
  });

  it('두 라벨을 글자로만 그린다(UI 이모지 금지)', () => {
    const markup = toggle('reading');
    expect(markup).toContain('독서');
    expect(markup).toContain('공부');
  });

  it('고른 쪽만 aria-pressed=true다 — 스크린리더가 지금 모드를 읽는다', () => {
    const reading = toggle('reading');
    const study = toggle('study');
    // 「독서」 버튼이 먼저 온다 — 앞 버튼의 aria-pressed가 모드를 그대로 말한다.
    expect(reading.indexOf('aria-pressed="true"')).toBeLessThan(reading.indexOf('공부'));
    expect(study.indexOf('aria-pressed="true"')).toBeGreaterThan(study.indexOf('독서'));
  });

  it('선택 세그먼트는 토큰을 탄다 — 공부 모드에서 저절로 파랑이 된다', () => {
    expect(toggle('reading')).toContain('--accentPill');
  });

  it('측정 중이면 잠긴다 — 진짜 disabled가 아니라 aria-disabled라야 이유를 말할 기회가 남는다', () => {
    const locked = toggle('reading', true);
    expect(locked).toContain('aria-disabled="true"');
    // ⚠️ 「`<button type="button" disabled`이 없다」로 재던 줄을 걷었다 — React는 `disabled`를 그 위치에
    //    직렬화하지 않아 **어떤 구현에서도 통과하는** 공허한 단언이었다(T-149 계열). 대신 속성 자체의
    //    부재를 재고, 잠기지 않은 렌더와 대조해 이 단언이 실제로 갈리는지 함께 못 박는다.
    expect(locked).not.toMatch(/\sdisabled(=|\s|>)/);
    expect(toggle('reading')).not.toContain('aria-disabled');
  });

  it('히트영역은 44px 컨테이너가 든다 — 알약은 작아도 손가락은 닿아야 한다', () => {
    expect(toggle('reading')).toContain('height:44px');
  });
});
