import { TDSMobileProvider } from '@toss/tds-mobile';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import { History } from './screens/History';
import { graph, userAgent } from './test-fixtures';
import { monthLabelPositions } from './ui';

/**
 * 월 라벨 배치 — 서버가 준 `monthLabels`(주 인덱스 + "M월")를 잔디 격자 위 픽셀 자리로 옮긴다.
 * 웹 `ContributionGraph.vue`는 CSS 그리드의 `gridColumnStart`가 열을 맞춰 주지만, 미니앱 격자는
 * flex + 고정 칸이라 열 자리를 직접 계산해야 한다 — 그 계산만 꺼내 계측한다.
 */
describe('월 라벨 배치', () => {
  const at = (...weekIndexes: number[]) => weekIndexes.map((weekIndex) => ({ weekIndex, label: `${weekIndex}월` }));

  it('라벨이 없으면 아무것도 안 그린다 — 빈 그래프(가입 직후)가 여기로 온다', () => {
    expect(monthLabelPositions([], 11)).toEqual([]);
  });

  it('주 인덱스를 칸 폭 + 간격으로 환산한다 — 격자와 같은 산수를 써야 라벨이 그 열 위에 선다', () => {
    // 칸 11 + gap 3 = 주당 14px. 0주는 왼쪽 끝, 4주는 56px.
    expect(monthLabelPositions(at(0, 4), 11)).toEqual([
      { label: '0월', left: 0 },
      { label: '4월', left: 56 },
    ]);
  });

  it('직전 라벨과 너무 가까우면 버린다 — 그래프가 월말에서 시작하면 첫 두 라벨이 한 칸 차라 겹쳐 읽힌다', () => {
    expect(monthLabelPositions(at(0, 1), 11)).toEqual([{ label: '0월', left: 0 }]);
  });

  it('간격은 버린 라벨이 아니라 **남긴 라벨** 기준으로 잰다 — 입력 기준으로 재면 촘촘한 구간이 통째로 사라진다', () => {
    // left = 0·14·28·42·56. 남긴 것 기준이면 0·28·56이 살아남는다(버린 것 기준이면 0 하나만 남는다).
    expect(monthLabelPositions(at(0, 1, 2, 3, 4), 11).map((m) => m.left)).toEqual([0, 28, 56]);
  });
});

/** 기록 화면 — 잔디 위 월 라벨과 아래 범례가 실제로 그려지는지(계산만 맞고 배선이 없으면 화면은 그대로다). */
describe('기록 화면', () => {
  const markup = renderToStaticMarkup(
    <TDSMobileProvider userAgent={userAgent}>
      <History graph={graph} />
    </TDSMobileProvider>,
  );

  it('격자 위에 월 라벨을 그린다 — 서버가 실어 주는데 안 그려서 잔디가 언제인지 알 수 없었다', () => {
    expect(markup).toContain('8월');
  });

  it('격자 아래에 색 범례를 둔다 — 색 농도가 무슨 뜻인지 화면 어디에도 없었다', () => {
    expect(markup).toContain('적게');
    expect(markup).toContain('많이');
  });

  it('수동 기록(테두리 칸) 설명도 함께 둔다 — 범례 없이는 테두리가 오류처럼 보인다', () => {
    expect(markup).toContain('직접 채움');
  });
});
