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
} from './App';
import type { DashboardResponse, StudyBookRow, StudyState } from './api';
import { IDLE_STUDY } from './api';
import { ACTIVE_STUDY_RELIEF, HERO_CARD_BG_VAR, Home, ModeToggle, heroOverline } from './screens/Home';
import { SessionGoalSheet } from './screens/SessionGoalSheet';
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
  hapticOnce: vi.fn(),
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

describe('시작 토스트 — 공부에도 책이 생겼다', () => {
  /**
   * ⚠️ 2026-09-02에 <b>계측 대상이 바뀌었다</b>: 공부 측정에도 책을 고를 수 있게 되면서 「책 없이」가
   * 거짓말이 아니라 정보가 됐다(고를 수 있는데 안 고른 것이다). 옛 고정 문구 「공부 측정을 시작했어요」는
   * 이제 무엇을 재는지 말하지 않는 쪽이라 여기서 갱신한다.
   */
  it('책을 안 고른 공부 시작은 「책 없이」라고 말한다', () => {
    expect(startToastMessage({ book: null, changed: false, mode: 'study' })).toBe('책 없이 공부 측정을 시작했어요');
  });

  it('독서 문구는 그대로다(회귀 가드)', () => {
    expect(startToastMessage({ book: null, changed: false })).toBe('책 없이 측정을 시작했어요');
  });

  /**
   * ⚠️ 2026-09-02에 <b>계측 대상이 사라졌다</b>: 옛 게이트 `toastHasBookControls`는 「공부 토스트엔
   * 표지·[바꾸기]가 없다」를 잰 함수인데, 공부 측정에도 교체 문(`/api/study/active/book`)이 생기면서
   * 항상 참이 되어 삭제됐다. 그 자리를 잇는 단언(공부 토스트에도 [바꾸기]가 서고 색은 `--accentPill`을
   * 탄다)은 <b>`study-timer-book.test.tsx`</b>에 있다.
   */
  it('두 모드 모두 표지 자리와 [바꾸기]를 단다 — 공부에도 고를 책과 바꿀 문이 생겼다', () => {
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

    expect(study).toContain('책 없이 공부 측정을 시작했어요');
    for (const markup of [study, reading]) {
      expect(markup).toContain('바꾸기');
      expect(markup).toContain('dashed'); // 책 없음 점선 표지 자리
    }
  });
});

describe('히어로 오버라인', () => {
  it('공부 모드는 「오늘 공부한 시간」이다(하루 목표 폐기로 공부엔 새싹 분기가 오지 않는다 — 렌더 테스트가 잰다)', () => {
    expect(heroOverline('study', false)).toBe('오늘 공부한 시간');
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

  /**
   * 히어로 카드 틴트(2차 §7-B) — 「토글했는데 색이 안 바뀐다」의 처방이다. 값이 <b>양쪽에</b> 있어야
   * 의미가 있다: 낮 기본값이 빠지면 인라인 fallback만 남아 공부 모드에서도 종이색 그대로다.
   */
  it('히어로 카드 배경 토큰이 낮·공부 양쪽에 있다 — 한쪽만 있으면 틴트가 조용히 죽는다', () => {
    const root = css.slice(css.indexOf('html:root {'), css.indexOf('}', css.indexOf('html:root {')));
    const study = css.slice(css.indexOf(`body.${STUDY_CLASS} {`), css.indexOf('}', css.indexOf(`body.${STUDY_CLASS} {`)));

    expect(root).toContain(`${HERO_CARD_BG_VAR}: #F9FBF7`); // 독서 = Soft 부푼 면(시안 .puff)
    expect(study).toContain(`${HERO_CARD_BG_VAR}: #EFF3F6`); // 공부 = 명도 유지·색상만 한랭한 「푸른 종이」
  });

  it('히어로 카드가 그 토큰을 실제로 소비한다 — 선언만 있고 안 쓰면 화면은 안 바뀐다', () => {
    expect(renderHome('study')).toContain(`background:var(${HERO_CARD_BG_VAR}`);
  });

  /**
   * 강조 알약(`--accentPill`) — 교체 시트의 현재 행과 시작 토스트 [바꾸기]가 이 토큰 하나를 본다.
   *
   * <p>공부 블록의 선언이 빠지면 <b>인라인 fallback(세이지 리터럴)만 남아</b> 공부 모드에 독서 색이
   * 그대로 뜬다. 옛 계측기(「공부 토스트에 세이지 리터럴이 없다」)로는 이걸 못 잡는다 — 폴백을 쓰는
   * 이상 마크업엔 리터럴이 늘 실린다. 그래서 <b>선언 자체</b>를 잰다(히어로 카드 틴트와 같은 처방).
   */
  /**
   * TDS 버튼은 공부 모드 전용 규칙을 두지 않는다 — 버튼 재색칠이 전부 토큰 경유라 `body.study-mode`의
   * 토큰 스왑이 저절로 닿는다(Soft 재테마 2026-09-23). 옛날엔 리터럴 규칙 3개가 따로 있었고, 그중
   * primary 규칙이 명시도로 채움 버튼까지 덮어 「저장」이 연한 파랑 위 종이색 글자가 됐었다(2026-09-13).
   * 리터럴 규칙이 되살아나면 토큰 스왑을 가려 같은 사고가 재발하므로 <b>0개</b>를 잰다.
   */
  it('공부 모드 전용 TDS 버튼 규칙이 없다 — 리터럴 규칙은 토큰 스왑을 가린다', () => {
    const live = css.replace(/\/\*[\s\S]*?\*\//g, '');
    expect(live).toContain('.tds-mobile-button['); // 버튼 규칙 자체는 있다(추출이 공허하지 않음)
    expect(live).not.toMatch(/body\.study-mode\s+\.tds-mobile-button/);
  });

  it('공부 블록이 Soft 그림자·옅은 타일 토큰을 파랑으로 갈아 끼운다', () => {
    const study = css.slice(css.indexOf(`body.${STUDY_CLASS} {`), css.indexOf('}', css.indexOf(`body.${STUDY_CLASS} {`)));
    expect(study).toMatch(/--puffShadow:[^;]*rgba\(80,\s*105,\s*125/);
    expect(study).toMatch(/--dentShadow:[^;]*rgba\(80,\s*105,\s*125/);
    expect(study).toContain('--adaptiveBlue50: #D6E0E7');
  });

  it('강조 알약 토큰이 공부 블록에도 있다 — 빠지면 fallback 세이지가 그대로 뜬다', () => {
    const study = css.slice(css.indexOf(`body.${STUDY_CLASS} {`), css.indexOf('}', css.indexOf(`body.${STUDY_CLASS} {`)));

    expect(study).toContain('--accentPill:');
  });

  /**
   * 잔디 팔레트(`--grass0..4`) — 기록 화면의 잔디·범례·하루 막대가 이 토큰 하나를 본다.
   *
   * <p>`--accentPill`과 같은 처방이다: 값이 <b>양쪽에</b> 있어야 뜻이 산다. 낮 기본값이 빠지면
   * 인라인 fallback(세이지 리터럴)만 남아 공부 모드에서도 초록 잔디가 그대로 뜨고, 공부 블록이
   * 빠지면 갈아 끼울 것이 아예 없다.
   */
  it('잔디 낮 기본값 5단이 html:root에 있다 — 없으면 fallback만 남아 공부 모드가 안 따라온다', () => {
    const root = css.slice(css.indexOf('html:root {'), css.indexOf('}', css.indexOf('html:root {')));

    for (const decl of [
      '--grass0: #E3E9E0',
      '--grass1: #C9DAC4',
      '--grass2: #A3C09B',
      '--grass3: #6E9565',
      '--grass4: #3F5A3C',
    ]) {
      expect(root).toContain(decl);
    }
  });

  it('공부 블록이 잔디 1~4단을 파랑 사다리로 덮는다 — 0단(빈 칸)은 종이색 그대로 둔다', () => {
    const study = css.slice(css.indexOf(`body.${STUDY_CLASS} {`), css.indexOf('}', css.indexOf(`body.${STUDY_CLASS} {`)));

    // 세이지 4단과 같은 명도의 파랑 짝수 칸(200·400·600·800) — 새 색을 만들지 않는다.
    for (const decl of ['--grass1: #AFC4D2', '--grass2: #83A0B4', '--grass3: #536F85', '--grass4: #3A5266']) {
      expect(study).toContain(decl);
    }
    // 0단은 「기록 없음」의 종이색이라 모드가 갈려도 같다 — 덮으면 빈 칸이 파래진다.
    expect(study).not.toContain('--grass0:');
  });

  /** 큰 시계 잉크(2차 §7-C) — 화면 최대 활자가 「파란 펜」이 된다. 토큰 경유라 값은 css가 정한다. */
  it('공부 모드의 큰 시계는 파란 잉크다 — 독서 모드 시계는 그대로 잉크색', () => {
    // 큰 시계만 집는다 — 머리말(오버라인)은 두 모드 다 ACCENT라 그걸로 재면 늘 파랑이 잡힌다.
    const clockTag = (markup: string, clock: string) =>
      markup.slice(markup.lastIndexOf('<span', markup.indexOf(clock)), markup.indexOf(clock));

    expect(clockTag(renderHome('study', {}, { ...IDLE_STUDY, todaySeconds: 3_725 }), '01:02:05'))
      .toContain('--adaptiveBlue700');
    // 독서 픽스처의 오늘 읽은 시간 = 목표 1시간 − 남은 15분 = 45:00
    expect(clockTag(renderHome('reading'), '45:00')).not.toContain('--adaptiveBlue700');
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

/**
 * 회당 시간 배선 — 정적 렌더로는 클릭·effect가 안 도니(T-149) <b>건수</b>로 잰다(존재 단언은 뒤바뀜을 못 잡는다, T-218).
 *
 * <p>2026-09-13 하루 목표 폐기: 공부 목표 화면(`'studyGoal'`)과 그 진입의 전면광고가 통째로 사라졌다.
 * 회당 시간 설정에는 광고를 붙이지 않는다(사용자 결정 Q1) — 광고 진입점이 조용히 되살아나지 않게 0건으로 잠근다.
 */
describe('회당 시간 배선 (소스)', () => {
  const strip = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
  const app = strip(readFileSync(new URL('./App.tsx', import.meta.url), 'utf8'));
  const home = strip(readFileSync(new URL('./screens/Home.tsx', import.meta.url), 'utf8'));
  const sheet = strip(readFileSync(new URL('./screens/SessionGoalSheet.tsx', import.meta.url), 'utf8'));
  const count = (code: string, needle: string) => code.split(needle).length - 1;

  it('공부 목표 화면이 남아 있지 않다', () => {
    expect(count(app, "'studyGoal'")).toBe(0);
    expect(count(app, 'goToStudyGoal')).toBe(0);
  });

  it('홈 손잡이는 독서 목표로만 간다 — 모드 분기가 없다', () => {
    expect(count(app, 'onGoGoal={goToGoal}')).toBe(2); // 설정 화면 + 메인 탭
  });

  /**
   * 저장 순서 — 응답이 <b>성공한 뒤에</b> 반영하고 닫는다(`tag()`와 같은 규약). 성공이면 지난 실패 스트립도 지운다.
   * 실패는 시트 <b>안</b>에서 말한다: 액션 스트립은 탭바 층(z 100)이라 불투명 시트 패널(z 201)에 가린다(`changeBook` 주석의 실측).
   */
  it('회당 시간은 한 문으로 저장되고, 성공한 응답만 공부 상태에 반영하며 지난 실패 스트립을 지운다', () => {
    const at = app.indexOf('setStudySessionGoal(id, s).then((next) => {');
    expect(count(app, 'setStudySessionGoal(')).toBe(1);
    expect(at).toBeGreaterThan(-1);
    const body = app.slice(at, app.indexOf('})', at));
    expect(body).toContain('onStudyChange(next)');
    expect(body).toContain('setActionError(null)');
  });

  it('시트는 저장이 성공한 뒤에 닫힌다 — 저장을 부르는 자리에서 곧바로 닫지 않는다', () => {
    expect(count(home, '.then(() => setGoalSheetBook(null))')).toBe(1);
    // 닫는 호출은 성공 뒤·✕·뒤로가기 셋뿐이다 — onPick 안에서 먼저 닫으면 네 번째가 생긴다.
    expect(count(home, 'setGoalSheetBook(null)')).toBe(3);
  });

  it('달성 햅틱은 한 자리에서만, 전환 판정(shouldHaptic)을 거쳐 부른다', () => {
    expect(count(home, 'hapticOnce(')).toBe(1);
    expect(count(home, 'if (shouldHaptic(wasReached.current, studyReached)) hapticOnce()')).toBe(1);
  });

  it('회당 시간 설정에는 전면광고가 없다(Q1)', () => {
    expect(count(home, 'showInterstitialAd(')).toBe(0);
    expect(count(sheet, 'showInterstitialAd(')).toBe(0);
    // 저장 배선은 App에 산다 — App의 광고 호출은 독서 「목표 바꾸기」(goToGoal) 한 자리뿐이어야 한다.
    expect(count(app, 'showInterstitialAd(')).toBe(1);
    const goToGoal = app.slice(app.indexOf('const goToGoal ='), app.indexOf('switch (view)'));
    expect(goToGoal).toContain('showInterstitialAd(');
  });

  it('시트의 저장 버튼은 휠 판정을 본다 — 0시간 0분·6시간 초과에서 잠긴다', () => {
    expect(count(sheet, 'disabled={!wheel.valid}')).toBe(1);
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
    todayReadSeconds: 2700,
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

  /**
   * 2026-09-02에 <b>계측 대상이 바뀌었다</b>: 공부에도 캐러셀이 섰다. 재는 것은 「캐러셀이 있느냐」가
   * 아니라 <b>독서 헤더가 아니라 공부 헤더가 선다</b>는 것 — 두 목록이 섞이지 않는다는 뜻이다.
   * (공부 캐러셀의 내용물은 `study-timer-book.test.tsx`가 잰다.)
   */
  it('공부 캐러셀은 자기 헤더로 선다 — 독서 헤더가 새지 않는다', () => {
    expect(renderHome('study')).toContain('무엇을 공부할까요?');
    expect(renderHome('study')).not.toContain('무엇으로 측정할까요?');
  });

  /**
   * ⚠️ 이 단언은 <b>부채가 있는 픽스처</b>로 재야 한다 — 빚 0으로 재면 독서 렌더에서도 ⓘ가 서지만
   * 판별의 근거가 「그 모드엔 원래 없다」로 흐려진다. 그리고 「밀린 시간」·「광고 보고」 문자열로 재던
   * 앞 판(2026-09-01)은 <b>공허했다</b>: 그 둘은 `RemainingNote` 안이라 ⓘ를 <b>탭해야</b> 열리는데
   * 하니스가 정적 렌더라 어느 모드에서도 렌더되지 않는다(T-149 — 도달 불가 경로엔 부정 단언 금지).
   * 실제로 갈리는 것은 <b>ⓘ 손잡이 자체</b>(`aria-expanded`)라 그걸 짝으로 대조한다.
   */
  it('부채 장치는 여전히 없다 — 밀린 시간이 있어도 ⓘ 툴팁 손잡이가 서지 않는다', () => {
    const debt = { carriedDebtSeconds: 1_200, debtWaiverAvailable: true };

    // 짝: 같은 빚을 가진 독서 렌더엔 ⓘ가 선다 — 아래 부재 단언이 공허하지 않다는 증거다.
    expect(renderHome('reading', debt)).toContain('aria-expanded');
    expect(renderHome('study', debt, { ...IDLE_STUDY, todaySeconds: 600 })).not.toContain('aria-expanded');
  });

  /**
   * 하루 목표 폐기(2026-09-13, Q6) — 게이지·「목표 정하기」·「변경 ›」·새싹이 공부 히어로에서 전부 빠진다.
   *
   * <p>픽스처가 <b>옛 서버 필드(`goalSeconds`)를 일부러 싣는다</b> — 서버는 2026-09-14(#1132)에 이 필드를
   * 걷었지만, 화면이 같은 이름을 다시 읽기 시작하면(달성 = 새싹) 여기서 죽어야 한다. 짝(양성 대조)은 같은 부품을
   * 그리는 독서 렌더(아래 「독서 모드 회귀 가드」의 「하루 목표」)와 독서 새싹 테스트(`home.test.tsx`)다.
   */
  it('공부 히어로엔 하루 목표 흔적이 없다 — 게이지·목표 정하기·변경 ›·새싹 0건, 오늘 공부한 시간은 그대로', () => {
    const legacy = { ...IDLE_STUDY, todaySeconds: 1_800, goalSeconds: 1_800 } as StudyState;
    for (const study of [legacy, { ...IDLE_STUDY, todaySeconds: 0, goalSeconds: 0 } as StudyState]) {
      const markup = renderHome('study', {}, study);
      expect(markup).not.toContain('하루 목표');
      expect(markup).not.toContain('목표 정하기');
      expect(markup).not.toContain('변경 ›');
      expect(markup).not.toContain('남은 시간');
      expect(markup).not.toContain('data-sprout');
      expect(markup).not.toContain('오늘 목표 달성');
      expect(markup).toContain('오늘 공부한 시간');
    }
    expect(renderHome('study', {}, legacy)).toContain('30:00');
  });

  /** 새싹은 독서 원장의 것이다 — 독서 목표를 채운 날 공부로 토글해도 공부 히어로에 새싹이 새지 않는다. */
  it('독서 목표를 채운 날에도 공부 히어로엔 새싹이 없다(양성 쌍: 같은 대시보드의 독서 렌더엔 선다)', () => {
    const readingDone = { remainingSeconds: 0, todayReadSeconds: 3_600, carriedDebtSeconds: 0 };
    expect(renderHome('reading', readingDone)).toContain('data-sprout');
    expect(renderHome('study', readingDone)).not.toContain('data-sprout');
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

/**
 * 책별 「회당 시간」 — 대기 중엔 캐러셀 아래 손잡이, 측정 중엔 측정 줄 아래 남은 시간/달성 한 줄.
 * 문구 자체는 `sessionGoal.test.ts`가 잰다 — 여기서는 <b>그 문구가 화면에 실제로 닿는가</b>와 가드(책 없이·스톱워치)를 잰다.
 */
describe('홈 — 공부 회당 시간', () => {
  const book = (id: number, sessionGoalSeconds: number | null): StudyBookRow => ({
    id,
    title: `공부책${id}`,
    author: null,
    coverUrl: null,
    isbn13: null,
    readCount: 0,
    purchaseLink: null,
    sessionGoalSeconds,
  });
  const books = [book(7, 3_000), book(8, null)];

  const renderStudy = (study: StudyState, selectedStudyBookId?: number | null) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Home
          dashboard={dashboard({}, study)}
          mode="study"
          study={study}
          onChangeMode={() => {}}
          onBlockedModeChange={() => {}}
          selectedBookId={undefined}
          onSelectBook={() => {}}
          selectedStudyBookId={selectedStudyBookId}
          onSelectStudyBook={() => {}}
          onTimerChange={() => {}}
          celebrate={false}
          onGoGoal={() => {}}
          goalAdPending={false}
          onGoSettings={() => {}}
          onError={() => {}}
          onOpenMargin={() => {}}
          onComposeMargin={() => {}}
          onSetSessionGoal={() => Promise.resolve()}
        />
      </TDSMobileProvider>,
    );

  const idle = { ...IDLE_STUDY, books };
  /** `seconds`초 전에 시작한 측정 — 반 초를 더 얹어 렌더 시각이 초 경계를 넘어도 같은 정수 경과가 나오게 한다. */
  const measuring = (activeBook: StudyBookRow | null, seconds: number): StudyState => ({
    ...IDLE_STUDY,
    books,
    hasActiveSession: true,
    activeStartedAt: new Date(Date.now() - seconds * 1000 - 500).toISOString(),
    activeBook,
  });

  it('대기 — 고른 책에 회당 시간이 있으면 「회당 50분 · 바꾸기」', () => {
    expect(renderStudy(idle, 7)).toContain('회당 50분 · 바꾸기');
  });

  it('대기 — 없으면 「회당 시간 정하기」', () => {
    const markup = renderStudy(idle, 8);
    expect(markup).toContain('회당 시간 정하기');
    expect(markup).not.toContain('회당 50분');
  });

  it('대기 — 「책 없이」를 고르면 손잡이가 없다(책이 없으면 회당 시간도 없다)', () => {
    expect(renderStudy(idle, null)).not.toContain('회당');
    // 짝: 같은 픽스처에서 책을 고르면 선다 — 위 부재가 공허하지 않다.
    expect(renderStudy(idle, 8)).toContain('회당');
  });

  it('측정 중 — 닿기 전엔 남은 시간을 말하고, 손잡이는 값을 되풀이하지 않는다', () => {
    const markup = renderStudy(measuring(books[0], 750));
    expect(markup).toContain('회당 50분 · 37분 남음');
    expect(markup).toContain('회당 시간 바꾸기');
    expect(markup).not.toContain('회당 50분 · 바꾸기');
  });

  it('측정 중 — 닿으면 달성과 더 한 시간을 말한다', () => {
    const markup = renderStudy(measuring(books[0], 3_330));
    expect(markup).toContain('회당 50분 달성 · 5분 더 공부했어요');
    expect(markup).not.toContain('남음');
  });

  it('측정 중 — 회당 시간이 없는 책은 스톱워치라 남은 시간 줄이 없고, 정하는 손잡이만 선다', () => {
    const markup = renderStudy(measuring(books[1], 750));
    expect(markup).not.toContain('남음');
    expect(markup).not.toContain('달성');
    expect(markup).toContain('회당 시간 정하기');
  });

  it('측정 중 — 책 없이 재면 회당 줄도 손잡이도 없다', () => {
    const markup = renderStudy(measuring(null, 750));
    expect(markup).not.toContain('회당');
    expect(markup).toContain(ACTIVE_STUDY_RELIEF);
  });

  it('홈을 처음 그릴 때 시트는 닫혀 있다 — 진입 직후 화면을 덮는 층이 없다(심사 규칙)', () => {
    expect(renderStudy(idle, 7)).not.toContain('이 책 회당 시간');
    expect(renderStudy(measuring(books[0], 3_330))).not.toContain('role="dialog"');
  });
});

describe('회당 시간 시트 (SessionGoalSheet)', () => {
  const row = (sessionGoalSeconds: number | null): StudyBookRow => ({
    id: 7,
    title: '정보처리기사 필기',
    author: null,
    coverUrl: null,
    isbn13: null,
    readCount: 0,
    purchaseLink: null,
    sessionGoalSeconds,
  });
  const render = (sessionGoalSeconds: number | null) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <SessionGoalSheet book={row(sessionGoalSeconds)} onPick={() => {}} onClose={() => {}} />
      </TDSMobileProvider>,
    );
  const buttons = (markup: string, label: string) =>
    markup
      .split('<button')
      .slice(1)
      .filter((chunk) => chunk.includes(label))
      .map((chunk) => chunk.slice(0, chunk.indexOf('>')));

  it('제목과 책 제목을 말한다 — 어느 책의 값을 고치는지 시트가 스스로 말한다', () => {
    const markup = render(3_000);
    expect(markup).toContain('이 책 회당 시간');
    expect(markup).toContain('정보처리기사 필기');
  });

  it('시·분 휠 두 열이 높이 박힌 컨테이너 안에 선택 밴드와 함께 선다(Goal.tsx와 같은 부품)', () => {
    const markup = render(3_000);
    expect(markup).toContain('aria-label="시간 선택"');
    expect(markup).toContain('aria-label="분 선택"');
    expect(markup).toContain('data-wheel-band');
    expect(markup).toContain('height:180px');
    expect(markup).toContain('goal-wheels');
  });

  it('처음 칸은 저장할 수 있는 값이다 — 현재 값(50분)이든 기본값(30분)이든 저장이 잠기지 않는다', () => {
    expect(buttons(render(3_000), '저장')[0]).not.toContain('disabled');
    expect(buttons(render(null), '저장')[0]).not.toContain('disabled');
  });

  it('저장이 실패하면 시트 안에서 말한다 — 액션 스트립은 시트에 가려 안 보인다', () => {
    const markup = renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <SessionGoalSheet book={row(3_000)} error="회당 시간은 1분 이상 6시간 이하로 정해 주세요" onPick={() => {}} onClose={() => {}} />
      </TDSMobileProvider>,
    );
    expect(markup).toContain('회당 시간은 1분 이상 6시간 이하로 정해 주세요');
  });

  it('「회당 시간 없이」는 지울 값이 있을 때만 선다', () => {
    expect(buttons(render(3_000), '회당 시간 없이')).toHaveLength(1);
    expect(buttons(render(null), '회당 시간 없이')).toHaveLength(0);
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
    expect(toggle('reading')).toContain('background:var(--adaptiveBlue700');
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

  it('세그먼트는 눌린 트랙 위에 선다(시안 Soft-Home) — 절대위치가 아니라 머리 줄의 흐름 안이다', () => {
    const markup = toggle('reading');
    expect(markup).toContain('background:var(--softDent');
    expect(markup).not.toContain('position:absolute');
  });

  /**
   * 세로 정렬 — 정적 렌더라 좌표는 못 재니 <b>정렬을 만드는 스타일의 존재</b>로 계측한다.
   *
   * <p>버튼이 inline 흐름이면 안의 알약이 버튼 baseline에 앉고, 상속 폰트(16px)의 line box strut이
   * 알약을 아래로 민다 — 실측 위 4.5px / 아래 −0.5px(테두리를 삐져나갔다). flex 컨테이너로 바꾸면
   * strut이 사라져 알약이 정확히 가운데 선다. 실좌표 판정은 목 모드가 게이트다.
   */
  const button = (mode: 'reading' | 'study') => {
    const markup = toggle(mode);
    return markup.slice(markup.indexOf('<button'), markup.indexOf('</button>'));
  };

  it('세그먼트 버튼은 flex 정렬이다 — inline strut이 알약을 아래로 못 민다', () => {
    expect(button('reading')).toContain('display:flex');
    expect(button('reading')).toContain('align-items:center');
  });

  it('strut을 걷어내도 손가락 몫은 그대로다 — 버튼 자체가 44px를 든다', () => {
    // strut이 벌어주던 7px이 사라지므로, 44는 이제 버튼이 명시로 들어야 한다.
    expect(button('reading')).toContain('min-height:44px');
  });
});
