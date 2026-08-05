import { Button, Text } from '@toss/tds-mobile';

import type { ContributionGraph } from '../api';
import { formatDuration } from '../format';
import { Screen } from '../ui';

/** 잔디 색 농도 0~4 — 웹 잔디와 같은 단계 체계(서버가 level을 계산해 준다). */
const LEVEL_COLORS = ['#ebedf0', '#c6e6c8', '#8fd694', '#4caf50', '#2e7d32'];

/**
 * 기록 — 잔디 · 연속일 · 총 시간. stop 응답에 graph가 동봉되므로 이 화면은 다시 받아오지 않고
 * 홈이 넘겨준 최신 graph를 그대로 그린다(설계 §2.5).
 */
export function History({ graph, onBack }: { graph: ContributionGraph; onBack: () => void }) {
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

      <div style={{ overflowX: 'auto', paddingBottom: 8 }}>
        <div style={{ display: 'flex', gap: 3 }}>
          {graph.weeks.map((week, weekIndex) => (
            <div key={weekIndex} style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              {week.map((day, dayIndex) => (
                <div
                  key={dayIndex}
                  title={day.date ?? ''}
                  style={{
                    width: 11,
                    height: 11,
                    borderRadius: 2,
                    // 날짜 없는 칸은 그리드 가장자리 placeholder라 빈 칸으로 둔다.
                    background: day.date === null ? 'transparent' : LEVEL_COLORS[day.level],
                    outline: day.manual ? '1px solid #9e9e9e' : undefined,
                  }}
                />
              ))}
            </div>
          ))}
        </div>
      </div>

      <Button display="block" variant="weak" style={{ marginTop: 28 }} onClick={onBack}>
        돌아가기
      </Button>
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
        background: 'var(--adaptiveGrey100, #f2f4f6)',
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
