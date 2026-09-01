import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  combineWheel,
  FIRST_RUN_GOAL_SECONDS,
  Goal,
  initialGoalSelection,
  saveGoal,
  weeklyLine,
  wheelIndices,
} from './screens/Goal';
import { userAgent } from './test-fixtures';

/**
 * B3 — firstRun 초기 선택 하향(운영 실측 2026-08-13).
 *
 * <p>신규 계정 기본 목표는 서버가 1시간(`UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS`)으로
 * 주는데, 첫 실행 화면이 그 값을 그대로 골라 두면 첫 세션에서 "오늘 목표 달성"(히어로 문구 + lv4 잔디)을
 * 경험할 확률이 사실상 0이 된다. 서버 기본값은 웹 신규 가입과 공유하므로 건드리지 않고,
 * **firstRun 화면의 초기 선택만** 10분으로 내린다.
 */
describe('첫 실행 초기 선택 (initialGoalSelection)', () => {
  it('firstRun이면 서버 기본(1시간)이 아니라 10분에서 시작한다 — 첫날 달성 경험이 가능한 값', () => {
    expect(initialGoalSelection(true, 3600)).toBe(600);
    expect(FIRST_RUN_GOAL_SECONDS).toBe(600);
  });

  it('기존 사용자는 지금 목표 그대로 — 목표 바꾸러 들어온 사람의 설정을 몰래 내리지 않는다', () => {
    expect(initialGoalSelection(false, 3600)).toBe(3600);
    expect(initialGoalSelection(false, 600)).toBe(600);
    expect(initialGoalSelection(false, 7200)).toBe(7200);
  });

  it('firstRun이면 서버가 뭘 주든 10분 — 기본값이 바뀌어도 첫 화면은 낮게 유지된다', () => {
    expect(initialGoalSelection(true, 7200)).toBe(600);
    expect(initialGoalSelection(true, 0)).toBe(600);
  });
});

/**
 * 프리셋 칩 → 휠 피커(시/분) 전환. 휠은 초(단일 소스)를 시/분으로 풀고 다시 합치는 변환이 전부라,
 * 상호작용을 못 돌리는 하니스에서도 이 변환만은 경계 전수로 못 박는다.
 */
describe('휠 표시값 변환 (wheelIndices)', () => {
  it('초를 시/분으로 푼다 — 프리셋이 없어져도 기존 값들이 그대로 재현된다', () => {
    expect(wheelIndices(600)).toEqual({ hours: 0, minutes: 10 });
    expect(wheelIndices(3600)).toEqual({ hours: 1, minutes: 0 });
    expect(wheelIndices(5400)).toEqual({ hours: 1, minutes: 30 });
    expect(wheelIndices(0)).toEqual({ hours: 0, minutes: 0 });
  });

  it('휠 상한(12시간)을 넘는 값은 12시간 59분으로 붙인다 — 휠에 없는 칸을 가리키지 않게', () => {
    expect(wheelIndices(46800)).toEqual({ hours: 12, minutes: 59 });
    expect(wheelIndices(360000)).toEqual({ hours: 12, minutes: 59 });
  });

  it('분 미만 자투리는 버린다 — 휠은 분 단위라 표시할 칸이 없다', () => {
    expect(wheelIndices(3661)).toEqual({ hours: 1, minutes: 1 });
    expect(wheelIndices(59)).toEqual({ hours: 0, minutes: 0 });
  });

  it('음수는 0시간 0분 — 서버가 이상한 값을 줘도 휠이 깨지지 않는다', () => {
    expect(wheelIndices(-1)).toEqual({ hours: 0, minutes: 0 });
    expect(wheelIndices(-7200)).toEqual({ hours: 0, minutes: 0 });
  });
});

describe('휠 표시값 → 초 (combineWheel)', () => {
  it('시/분을 초로 합친다', () => {
    expect(combineWheel(0, 10)).toBe(600);
    expect(combineWheel(2, 0)).toBe(7200);
    expect(combineWheel(12, 59)).toBe(46740);
    expect(combineWheel(0, 0)).toBe(0);
  });

  it('분 단위·상한 이하 값은 왕복해도 그대로다 — 목표를 열었다 닫으면 값이 달라지면 안 된다', () => {
    for (const seconds of [0, 600, 1800, 3600, 5400, 7200, 46740]) {
      const { hours, minutes } = wheelIndices(seconds);
      expect(combineWheel(hours, minutes)).toBe(seconds);
    }
  });
});

/**
 * 환산 줄 — 고른 값이 일주일이면 얼마가 되는지. 숫자 하나(하루치)로는 크기가 안 잡히지만
 * 7배는 잡힌다("10분"은 하찮아 보여도 "1시간 10분"은 쌓인 것처럼 읽힌다).
 */
describe('일주일 환산 줄 (weeklyLine)', () => {
  it('고른 값의 7배를 문장으로 만든다', () => {
    expect(weeklyLine(1800)).toBe('일주일이면 3시간 30분씩 쌓여요');
    expect(weeklyLine(600)).toBe('일주일이면 1시간 10분씩 쌓여요');
  });

  /** 「쌓여요」는 <b>이월 어휘</b>다 — 공부는 못 채운 시간이 다음 날로 넘어가지 않아 그 말이 거짓이 된다. */
  it('공부는 쌓인다고 말하지 않는다 — 같은 숫자, 다른 서술', () => {
    expect(weeklyLine(1800, 'study')).toBe('일주일이면 3시간 30분을 공부하는 셈이에요');
    expect(weeklyLine(1800, 'study')).not.toContain('쌓여요');
    // 독서 문구는 그대로 — 분기가 한쪽을 지운 게 아니다.
    expect(weeklyLine(1800, 'reading')).toBe('일주일이면 3시간 30분씩 쌓여요');
  });

  it('0이면 두 모드 다 할 말이 없다', () => {
    expect(weeklyLine(0, 'study')).toBeNull();
  });

  it('0이면 할 말이 없다 — 목표 없음에 「0초씩 쌓여요」는 조롱이다', () => {
    expect(weeklyLine(0)).toBeNull();
  });

  it('음수도 없다 — 서버가 이상한 값을 줘도 문장이 깨지지 않는다', () => {
    expect(weeklyLine(-600)).toBeNull();
  });
});

describe('목표 화면 렌더', () => {
  const render = (firstRun: boolean, current: number) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Goal current={current} firstRun={firstRun} onSaved={() => {}} onSkip={() => {}} />
      </TDSMobileProvider>,
    );

  /**
   * TDS Wheel은 정적 렌더에서 껍데기(`role="radiogroup"` + aria-label)만 내고 옵션 항목은
   * 클라이언트에서 채운다 — 그래서 "무엇이 골라져 있나"는 렌더로 못 본다(초기값은 위 변환 단위
   * 테스트가 담당). 여기서는 휠 두 열이 실제로 붙어 있는지만 aria-label로 확인한다.
   */
  it('시간·분 두 휠이 붙어 있다 — 프리셋 칩 대신 자유롭게 고를 수 있어야 한다', () => {
    const markup = render(false, 3600);
    expect(markup).toContain('aria-label="시간 선택"');
    expect(markup).toContain('aria-label="분 선택"');
  });

  /**
   * Wheel은 스스로 높이를 만들지 않는다 — 루트가 `height:100%`(항목 한 칸은 그 16%)라 부모 높이의
   * %로만 산다. 높이 없는 flex 컨테이너에 넣었더니 컨테이너가 0이 되어 항목이 전부 한 줄에 겹쳤다
   * (브라우저 실측 2026-08-13). 레이아웃 결과는 정적 렌더로 못 보므로 높이를 준 사실만 못 박는다.
   */
  it('휠 컨테이너에 높이가 박혀 있다 — 높이가 없으면 항목이 한 줄에 겹친다', () => {
    expect(render(false, 3600)).toContain('height:180px');
  });

  /**
   * 안개(위아래 그라데이션) 색을 크림 캔버스로 덮는 global.css 규칙이 `.goal-wheels`를 훅으로 잡는다
   * — 클래스가 빠지면 TDS 기본 흰색 안개가 그대로 나와 흰 박스 두 개로 도드라진다(실측 2026-08-13).
   * css 적용 결과는 브라우저 소관이라, 훅이 붙어 있다는 사실만 못 박는다.
   */
  it('휠 컨테이너에 안개 색을 덮을 훅 클래스가 붙어 있다', () => {
    expect(render(false, 3600)).toContain('goal-wheels');
  });

  it('첫 실행 휠은 10분에서 시작한다 — 1시간에서 시작하면 첫날 달성이 불가능하다', () => {
    expect(wheelIndices(initialGoalSelection(true, 3600))).toEqual({ hours: 0, minutes: 10 });
  });

  /**
   * ⚠️ 가운데 정렬은 **우리 div**가 해야 한다 — TDS `Text`는 넘긴 style에서 `textAlign`을 걸러내
   * (`display`도 자기 값으로 덮는다) 인라인 스타일에 `margin-top`만 남긴다(목 모드 실측 2026-08-29:
   * computed `text-align: start`로 왼쪽에 붙어 있었다). 문자열 포함 단언만 두면 이 실패가 안 보인다.
   */
  it('고른 값의 일주일 환산을 가운데 한 줄로 보여준다 — 하루치 숫자만으론 크기가 안 잡힌다', () => {
    const markup = render(false, 1800);
    expect(markup).toContain('일주일이면 3시간 30분씩 쌓여요');
    expect(markup).toContain('text-align:center');
  });

  it('첫 실행이면 왜 낮은 값인지 한 줄로 말한다 — 목표가 작다고 실망하지 않게', () => {
    expect(render(true, 3600)).toContain('언제든 늘릴 수 있어요');
  });

  /**
   * 안내 문구는 모드마다 할 말이 다르다 — 「시작해 보세요」는 처음 정하는 사람에게만 맞는 말이라,
   * 목표를 바꾸러 온 사람에겐 짧은 사실 두 문장만 남긴다. 부정 단언만 두면 문구가 통째로
   * 빠져도 통과하므로 새 문장의 존재를 함께 못 박는다.
   */
  it('바꾸기 모드 안내는 사실만 — 「시작해 보세요」는 처음 정하는 사람에게 할 말이다', () => {
    const markup = render(false, 3600);
    expect(markup).toContain('못 채운 시간은 다음 날로 넘어가요');
    expect(markup).not.toContain('시작해 보세요');
  });

  it('첫 실행 안내엔 이모지를 쓰지 않는다 — 문구는 그대로 남긴다', () => {
    const markup = render(true, 3600);
    expect(markup).toContain('가볍게 시작');
    expect(markup).not.toContain('🌱');
  });

  it('기존 사용자에겐 그 안내가 없다 — 이미 정해 놓은 사람에게 할 말이 아니다', () => {
    expect(render(false, 3600)).not.toContain('언제든 늘릴 수 있어요');
  });

  /** 버튼 여는 태그의 속성만 — 휠이 뱉는 다른 button들과 섞이지 않게 라벨이 든 조각만 고른다. */
  const buttonAttrs = (markup: string, label: string) =>
    markup
      .split('<button')
      .slice(1)
      .filter((chunk) => chunk.includes(label))
      .map((chunk) => chunk.slice(0, chunk.indexOf('>')));

  it('0시간 0분이면 저장할 수 없다 — 휠을 끝까지 내려 목표를 지우는 건 실수일 가능성이 높다', () => {
    expect(buttonAttrs(render(false, 0), '저장')[0]).toContain('disabled');
  });

  it('값이 있으면 저장할 수 있다 — 위 disabled가 항상 켜져 있는 게 아님을 못 박는다', () => {
    expect(buttonAttrs(render(false, 3600), '저장')[0]).not.toContain('disabled');
  });
});

/**
 * 공부 목표 — 같은 화면을 `variant`로 갈아 쓴다(휠·밴드·버튼 전부 공유). 갈리는 건 <b>문구와 저장 함수</b>뿐이고,
 * 파랑은 공짜다: 밴드·주간 줄이 토큰이라 `body.study-mode`가 알아서 칠한다.
 */
describe('공부 목표 렌더 (variant)', () => {
  const render = (variant: 'reading' | 'study') =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Goal current={1_800} firstRun={false} variant={variant} onSaved={() => {}} onSkip={() => {}} />
      </TDSMobileProvider>,
    );

  it('제목이 「공부 하루 목표」다 — 어느 목표를 고치는지 화면이 스스로 말한다', () => {
    expect(render('study')).toContain('공부 하루 목표');
    expect(render('reading')).not.toContain('공부 하루 목표');
  });

  /** 공부엔 이월이 없다 — 독서 문구를 그대로 쓰면 <b>거짓말</b>이 된다. */
  it('이월 문구가 없다 — 대신 지킨 날은 직접 체크한다고 말한다', () => {
    const study = render('study');
    expect(study).not.toContain('다음 날로 넘어가요');
    expect(study).toContain('목표로 해요');
    // 독서 문구는 그대로 — 분기가 한쪽을 지운 게 아니다.
    expect(render('reading')).toContain('다음 날로 넘어가요');
  });

  it('휠·밴드·주간 환산은 그대로 공유한다 — 재사용이 이 옵션의 이유다(문구만 모드를 탄다)', () => {
    const study = render('study');
    expect(study).toContain('aria-label="시간 선택"');
    expect(study).toContain('data-wheel-band');
    expect(study).toContain('일주일이면 3시간 30분을 공부하는 셈이에요');
    expect(render('reading')).toContain('일주일이면 3시간 30분씩 쌓여요');
  });
});

/**
 * 저장 분기 — <b>variant가 어느 문을 두드리는가</b>. 화면 밖으로 꺼내 둔 이유는 늘 같다(정적 렌더라
 * 「저장」 클릭이 안 돈다, T-149): 클로저 안에 두면 이 분기를 겨눌 계측기가 소스 grep밖에 안 남는다.
 *
 * <p>실패하면 조용하다 — 공부 목표 저장이 <b>독서 목표를 덮어쓰고</b> `ReadingGoalChange` 원장까지
 * 오염시킨다(서버는 정상 200을 준다). 그래서 URL·본문 키까지 함께 잠근다.
 */
describe('목표 저장 분기 (saveGoal)', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => {}, removeItem: () => {} });
    vi.stubGlobal('fetch', vi.fn(async () => ({ status: 200, ok: true, text: async () => '{}' })));
  });

  afterEach(() => vi.unstubAllGlobals());

  const lastRequest = () =>
    vi.mocked(globalThis.fetch).mock.calls.at(-1) as unknown as [string, RequestInit];

  it('공부는 /api/study/goal로 간다 — 독서 문을 두드리면 독서 목표가 덮어써진다', async () => {
    await saveGoal('study', 5400);

    const [url, init] = lastRequest();
    expect(url).toBe('http://localhost:8080/api/study/goal');
    expect(init.body).toBe(JSON.stringify({ dailyGoalSeconds: 5400 }));
  });

  it('독서는 그대로 /api/miniapp/goal — 분기가 한쪽으로 쏠리지 않았다', async () => {
    await saveGoal('reading', 5400);

    const [url, init] = lastRequest();
    expect(url).toBe('http://localhost:8080/api/miniapp/goal');
    expect(init.body).toBe(JSON.stringify({ dailyIncrementSeconds: 5400 }));
  });
});

/**
 * 시안 2e — 휠 뒤 밴드 + 선택 행 세리프 + 채움 저장 버튼.
 *
 * <p>휠 항목은 TDS 내부라 정적 렌더로 안 보인다. 설계 §7 U-3의 CHECK를 목 모드에서 돌려
 * <b>안정된 selector가 있음을 실측했다</b> — 항목이 `role="radio"` + `aria-checked`를 달고 있어
 * emotion 해시(`css-a9zorm`)에 의존하지 않고 선택 행만 집힌다(73개 중 `true`가 휠당 1개).
 * 그래서 설계의 1차안(전 행 세리프)으로 후퇴하지 않았다. 그 css 규칙 자체는 `typography.test.tsx`가
 * `readFileSync`로 잠근다(vite `?raw`는 CSS에 안 통한다 — 빈 문자열이 온다, 실측).
 */
describe('목표 위계 (시안 2e)', () => {
  const goalScreen = (current: number) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Goal current={current} firstRun={false} onSaved={() => {}} onSkip={() => {}} />
      </TDSMobileProvider>,
    );

  it('휠 뒤에 선택 밴드를 깐다 — 어느 칸이 골라졌는지 색으로 말한다', () => {
    const markup = goalScreen(1_800);
    const at = markup.indexOf('data-wheel-band');

    expect(at).toBeGreaterThan(-1);
    // ⚠️ 존재만 단언하면 높이·색·모서리가 통째로 서사로 남는다 — 자가 리뷰 돌연변이에서
    // 44->30 · 세이지->회갈색 · r10->r0이 <b>전부 생존</b>했다. 「어떤 밴드인가」까지 잠근다.
    const tag = markup.slice(markup.lastIndexOf('<', at), markup.indexOf('>', at));
    expect(tag).toContain('height:44px');
    expect(tag).toContain('--adaptiveBlue50');
    expect(tag).toContain('border-radius:10px');
  });

  it('저장은 채움 주 버튼이다 — 이 화면의 주 동작 하나', () => {
    expect(goalScreen(1_800)).toContain('--btn-filled');
  });
});
