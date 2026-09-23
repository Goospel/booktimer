import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { DENT, PUFF, SOFT_OUTLINE, SOFT_ROW, sectionStyle } from './ui';

/**
 * Soft 표면 상수 — 부푼(PUFF)·눌린(DENT) 면의 그림자는 <b>CSS 변수 경유</b>라야 한다.
 *
 * <p>리터럴로 적으면 밤(독서등)·공부 모드가 인라인을 이기려고 자리마다 `!important` 규칙을 둬야 한다 —
 * 연필선이 겪은 고질의 재판이다. 그리고 낮의 흰 하이라이트(`rgba(255,255,255,.95)`)가 밤 카드에 그대로
 * 새어 어두운 화면에 흰 테가 뜬다. 변수면 `body` 클래스 한 벌이 값만 갈아 끼운다.
 */
describe('Soft 표면 상수', () => {
  const markup = renderToStaticMarkup(<section style={sectionStyle} />);

  it('섹션 카드가 부푼 그림자를 변수로 싣는다', () => {
    expect(markup).toContain('box-shadow:'); // 그림자 자체가 있다(아래 부정 단언이 공허하지 않음)
    expect(markup).toContain('box-shadow:var(--puffShadow');
  });

  it('섹션 카드에 연필선이 되살아나지 않는다', () => {
    expect(markup).toContain('box-shadow:');
    expect(markup).not.toContain('border-image');
  });

  it('섹션 카드는 26px 둥근 면이다', () => {
    expect(sectionStyle.borderRadius).toBe(26);
    expect(sectionStyle.padding).toBe(18);
  });

  it('PUFF 그림자는 변수 경유다 — 리터럴이면 밤에 흰 빛이 샌다', () => {
    expect(PUFF.boxShadow.startsWith('var(--puffShadow')).toBe(true);
    expect(PUFF.background).toContain('var(--adaptiveGrey100');
  });

  it('DENT는 안으로 눌린 그림자다 — 폴백 값이 inset으로 시작한다', () => {
    expect(DENT.boxShadow.startsWith('var(--dentShadow, inset')).toBe(true);
    expect(DENT.background).toContain('var(--softDent');
  });

  it('보조 손잡이·시트 행은 1.5px 실선이다(규칙 1 「보조 버튼은 1.5px 테두리」)', () => {
    expect(SOFT_OUTLINE.border).toMatch(/^1\.5px solid var\(--adaptiveBlue700/);
    expect(SOFT_ROW.border).toMatch(/^1\.5px solid var\(--adaptiveGrey200/);
  });
});
