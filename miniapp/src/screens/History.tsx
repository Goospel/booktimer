import { Text } from '@toss/tds-mobile';

import type { ContributionGraph } from '../api';
import { formatDuration } from '../format';
import { GrassGrid, Screen } from '../ui';

/**
 * 기록 — 잔디 · 연속일 · 총 시간. stop 응답에 graph가 동봉되므로 이 화면은 다시 받아오지 않고
 * 홈이 넘겨준 최신 graph를 그대로 그린다(설계 §2.5).
 *
 * <p>탭 재편(PR-5) 전까지 있던 "돌아가기" 버튼은 탭 전환이 대신하므로 없앴다.
 */
export function History({ graph }: { graph: ContributionGraph }) {
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
        <GrassGrid weeks={graph.weeks} />
      </div>
    </Screen>
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
