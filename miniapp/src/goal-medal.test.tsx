import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { crossedGoal } from './App';
import { GoalMedal } from './ui';

/**
 * 목표 달성 메달 — 「목표를 채운 <b>순간</b>」에 한 번 튀어 오른다(Soft 재테마 PR-2).
 *
 * <p>`todayProgress().achieved`는 매 렌더 다시 계산되는 파생값이라 「방금 넘었다」를 모른다 — 그걸로 메달을
 * 켜면 달성한 날엔 홈을 열 때마다 메달이 튄다. 그래서 측정 종료 응답 한 번을 <b>전후로</b> 비교한다.
 */
const at = (read: number, goal: number) => ({ todayReadSeconds: read, todayGoalSeconds: goal });

describe('목표를 넘는 순간 (crossedGoal)', () => {
  it('못 채운 채 재기 시작해 채우고 끝냈으면 참이다', () => {
    expect(crossedGoal(at(0, 1800), at(1800, 1800))).toBe(true);
    expect(crossedGoal(at(1500, 1800), at(2000, 1800))).toBe(true);
  });

  it('이미 채운 날 한 번 더 재면 거짓이다 — 메달이 또 튀면 축하가 잡음이 된다', () => {
    expect(crossedGoal(at(1800, 1800), at(1900, 1800))).toBe(false);
  });

  it('재고도 못 채웠으면 거짓이다', () => {
    expect(crossedGoal(at(0, 1800), at(1799, 1800))).toBe(false);
  });

  it('목표가 0이면 거짓이다 — 없는 목표를 채웠다고 축하하지 않는다', () => {
    expect(crossedGoal(at(0, 0), at(600, 0))).toBe(false);
    // 재는 사이 목표를 0으로 내렸다(웹에서 저장) — 전 조건만 보면 참이 되는 유일한 칸이다.
    expect(crossedGoal(at(0, 1800), at(600, 0))).toBe(false);
  });
});

describe('메달 그림 (GoalMedal)', () => {
  const markup = renderToStaticMarkup(<GoalMedal />);

  it('튀어 오르는 클래스와 계측 손잡이를 단다', () => {
    expect(markup).toContain('class="medal-pop"');
    expect(markup).toContain('data-medal=""');
    expect(markup).toContain('aria-hidden="true"');
  });

  /** 애니메이션이 걸린 노드가 글자·이미지를 품으면 튀는 동안 그것까지 다시 래스터화한다(T-176). */
  it('SVG 안에 글자·이미지가 없다', () => {
    expect(markup).toContain('<circle'); // 그림은 있다(아래 부재 단언이 공허하지 않음)
    expect(markup).not.toMatch(/<(text|image|foreignObject)\b/);
  });
});
