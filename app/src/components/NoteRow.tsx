// 쪽지 한 줄(F1 사양 §4.3-3 · §4.2-4) — 책 화면 「여백」 단 · 서재 「책 없는 여백」 단이 같은 모양을 쓴다.
// 옮긴 문장(왼쪽 선 · 2줄까지) → 생각 미리보기(서버가 접은 앞 80자 · 3줄까지) → 날짜 + 받은 질문이 있으면 「AI 질문」. 줄 하나 = 접근성 요소 하나.
import { Pressable, View } from 'react-native';

import type { MarginSummary } from '../api/types';
import { noteRow } from '../lib/marginSave';
import { color, line, space } from '../theme/tokens';
import { AiBadge, Txt } from './ui';

export function NoteRow(p: { m: MarginSummary; onPress: () => void; onDelete?: () => void; source?: boolean }) {
  const r = noteRow(p.m);
  const del = p.onDelete;
  const source = p.source && r.quote && p.m.quoteSource ? p.m.quoteSource : null; // 책을 지운 쪽지의 출처 줄(J84)
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={source ? `${r.label}, 출처: ${source}` : r.label}
      accessibilityHint="쪽지를 열어요"
      accessibilityActions={del ? [{ name: 'delete', label: '지우기' }] : undefined}
      onAccessibilityAction={del ? (e) => e.nativeEvent.actionName === 'delete' && del() : undefined}
      onLongPress={del}
      onPress={p.onPress}
      style={({ pressed }) => [
        { paddingVertical: space[4], gap: space[2], borderBottomWidth: line.thin, borderBottomColor: color.line },
        pressed && { backgroundColor: color.pressed },
      ]}
    >
      {r.quote ? (
        <View style={{ gap: space[1] }}>
          <View style={{ borderLeftWidth: line.bold, borderLeftColor: color.text, paddingLeft: space[3] }}>
            <Txt v="Q2" numberOfLines={2} style={{ color: color.text }}>
              {r.quote}
            </Txt>
          </View>
          {source ? (
            <Txt v="T2" style={{ color: color.muted }}>
              {source}
            </Txt>
          ) : null}
        </View>
      ) : null}
      {r.thought ? (
        <Txt v="Q1" numberOfLines={3}>
          {r.thought}
        </Txt>
      ) : null}
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space[2] }}>
        <Txt v="T2" style={{ color: color.muted }}>
          {r.date}
        </Txt>
        {r.ai ? <AiBadge /> : null}
      </View>
    </Pressable>
  );
}
