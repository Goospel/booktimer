import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import {
  combineWheel,
  FIRST_RUN_GOAL_SECONDS,
  Goal,
  initialGoalSelection,
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

  it('첫 실행 휠은 10분에서 시작한다 — 1시간에서 시작하면 첫날 달성이 불가능하다', () => {
    expect(wheelIndices(initialGoalSelection(true, 3600))).toEqual({ hours: 0, minutes: 10 });
  });

  it('첫 실행이면 왜 낮은 값인지 한 줄로 말한다 — 목표가 작다고 실망하지 않게', () => {
    expect(render(true, 3600)).toContain('언제든 늘릴 수 있어요');
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
