import { Text } from '@toss/tds-mobile';

import type { ContributionGraph } from '../api';
import { formatDuration } from '../format';
import { GrassGrid, LEVEL_COLORS, MANUAL_OUTLINE, Screen, monthLabelPositions } from '../ui';

/** 기록 화면 잔디 칸 — `GrassGrid`의 기본값과 같아야 월 라벨이 그 열 위에 선다. */
const CELL_SIZE = 11;

/**
 * 기록 — 잔디 · 연속일 · 총 시간. stop 응답에 graph가 동봉되므로 이 화면은 다시 받아오지 않고
 * 홈이 넘겨준 최신 graph를 그대로 그린다(설계 §2.5).
 *
 * <p>탭 재편(PR-5) 전까지 있던 "돌아가기" 버튼은 탭 전환이 대신하므로 없앴다.
 */
export function History({ graph }: { graph: ContributionGraph }) {
  // 스크롤 위치는 손대지 않는다 — weeks[0]이 최신 주라 초기 위치(왼쪽 끝)가 이미 오늘이다(api.ts `weeks`).
  const months = monthLabelPositions(graph.monthLabels, CELL_SIZE);

  return (
    <Screen title="내 기록">
      <div style={{ display: 'flex', gap: 12 }}>
        <Stat label="연속" value={`${graph.currentStreak}일`} />
        <Stat label="읽은 날" value={`${graph.activeDays}일`} />
        <Stat label="총 시간" value={formatDuration(graph.totalSeconds)} />
      </div>

      <Text typography="st11" style={{ display: 'block', margin: '24px 0 10px' }}>
        {graph.growthStageEmoji} {graph.growthStageLabel}
      </Text>

      <div className="no-scrollbar" style={{ overflowX: 'auto', paddingBottom: 8 }}>
        {/* 라벨은 격자 폭 안에서 절대 배치된다 — inline-block이라 이 상자가 격자만큼만 넓어진다. */}
        <div style={{ position: 'relative', display: 'inline-block', paddingTop: 16 }}>
          <div aria-hidden="true" style={{ position: 'absolute', top: 0, left: 0, height: 14 }}>
            {months.map(({ label, left }) => (
              <span
                key={label}
                style={{ position: 'absolute', left, fontSize: 10, color: '#6F6A5E', whiteSpace: 'nowrap' }}
              >
                {label}
              </span>
            ))}
          </div>
          <GrassGrid weeks={graph.weeks} cellSize={CELL_SIZE} />
        </div>
      </div>

      <Legend />
    </Screen>
  );
}

/** 색 농도 범례 — 잔디가 무슨 뜻인지 화면 어디에도 없었다. 웹 `.grass-legend`와 같은 말을 쓴다. */
function Legend() {
  const swatch = { width: 10, height: 10, borderRadius: 2, flex: '0 0 auto' } as const;

  return (
    <div aria-hidden="true" style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 4, marginTop: 4 }}>
      <Text typography="st12" color="grey600">
        적게
      </Text>
      {LEVEL_COLORS.map((color) => (
        <span key={color} style={{ ...swatch, background: color }} />
      ))}
      <Text typography="st12" color="grey600">
        많이
      </Text>
      <span style={{ ...swatch, marginLeft: 10, background: LEVEL_COLORS[2], outline: MANUAL_OUTLINE }} />
      <Text typography="st12" color="grey600">
        직접 채움
      </Text>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        flex: 1,
        padding: '16px 12px',
        borderRadius: 12,
        background: 'var(--adaptiveGrey100, #FCFAF5)',
        textAlign: 'center',
      }}
    >
      <Text typography="st12" color="grey600" style={{ display: 'block' }}>
        {label}
      </Text>
      <Text typography="t6" fontWeight="bold" style={{ display: 'block', marginTop: 4 }}>
        {value}
      </Text>
    </div>
  );
}
