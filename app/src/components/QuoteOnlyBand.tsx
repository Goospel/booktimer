// 인용만 있는 쪽지의 띠(C2 · REQ-225 — F1 사양 §4.4-3 · 그림 F-MARGIN-NOTE-QUOTE 안A). 옮긴 문장 칸과 생각 칸 사이 흐름 안에 있다(덮지 않음).
// 띠 전체가 누르는 곳 하나 — 누를 때만 ASK를 부른다(보이는 조건은 lib/quoteOnlyBand가 정한다). 애니메이션 없음.
import { Pressable } from 'react-native';

import { color, layout, space } from '../theme/tokens';
import { Txt } from './ui';

export function QuoteOnlyBand(p: { onPress: () => void; busy: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="이 문장에서 무엇이 남았나요? 질문 받기"
      accessibilityHint="AI에게 질문을 받아요"
      accessibilityState={{ disabled: p.busy, busy: p.busy }}
      disabled={p.busy}
      onPress={p.onPress}
      style={({ pressed }) => [
        { minHeight: Math.max(layout.hit, 46), backgroundColor: color.paper2, padding: space[3], flexDirection: 'row', alignItems: 'center', gap: space[2] },
        pressed && { opacity: 0.7 },
      ]}
    >
      <Txt v="Q2" style={{ flex: 1 }}>
        이 문장에서 무엇이 남았나요?
      </Txt>
      <Txt v="L2">질문 받기</Txt>
      <Txt v="L1" style={{ color: color.muted }} accessible={false}>
        ›
      </Txt>
    </Pressable>
  );
}
