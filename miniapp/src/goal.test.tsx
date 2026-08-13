import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { FIRST_RUN_GOAL_SECONDS, Goal, initialGoalSelection } from './screens/Goal';
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

describe('목표 화면 렌더', () => {
  const render = (firstRun: boolean, current: number) =>
    renderToStaticMarkup(
      <TDSMobileProvider userAgent={userAgent}>
        <Goal current={current} firstRun={firstRun} onSaved={() => {}} onSkip={() => {}} />
      </TDSMobileProvider>,
    );

  /** TDS Button이 인라인으로 박는 채움색 — 선택된 프리셋만 fill이라 이 값이 유일한 표지다(home.test와 같은 방식). */
  const FILL_PRIMARY = '#3182f6';

  const selectedLabel = (markup: string) =>
    markup
      .split('<button')
      .slice(1)
      .flatMap((chunk) => {
        const fill = chunk.match(/--button-background-color:([^;"]*)/)?.[1];
        const label = chunk.match(/tds-mobile-button__content[^>]*>([^<]*)</)?.[1];
        return fill === FILL_PRIMARY && label !== undefined ? [label] : [];
      });

  it('첫 실행이면 10분이 미리 골라져 있다 — 1시간이 골라져 있으면 첫날 달성이 불가능하다', () => {
    expect(selectedLabel(render(true, 3600))).toContain('10분');
    expect(selectedLabel(render(true, 3600))).not.toContain('1시간');
  });

  it('기존 사용자는 지금 목표가 골라져 있다', () => {
    expect(selectedLabel(render(false, 3600))).toContain('1시간');
  });

  it('첫 실행이면 왜 낮은 값인지 한 줄로 말한다 — 목표가 작다고 실망하지 않게', () => {
    expect(render(true, 3600)).toContain('언제든 늘릴 수 있어요');
  });

  it('기존 사용자에겐 그 안내가 없다 — 이미 정해 놓은 사람에게 할 말이 아니다', () => {
    expect(render(false, 3600)).not.toContain('언제든 늘릴 수 있어요');
  });
});
