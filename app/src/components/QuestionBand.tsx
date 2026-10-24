// 질문 띠(PR-H 사양 §5.1 · §5.3 · §5.4 — 피그마 R7 S-WRITE-0 · 1 · WAIT). 머리 바로 아래 · 끝에서 끝 · 고정.
// 세 모양: 빈 띠(누르는 곳 아님 — 라운드 0) · 질문 띠(띠 전체가 시트를 여는 단추) · 기다림(맨 윗줄만 바뀐다 — 높이가 들썩이지 않는다).
// 질문 글자는 고를 수 없다(RN Text 기본 — 선택 · 복사 0, REQ-47 소스 가드).
import { ActivityIndicator, Pressable, View } from 'react-native';

import { color, layout, line, space } from '../theme/tokens';
import { AiBadge, Txt } from './ui';

export function QuestionBand(p: { question: string | null; hint: string; waiting: string | null; onPress?: () => void }) {
  const spinner = p.waiting ? <ActivityIndicator size="small" color={color.muted} /> : null;
  const chevron = p.onPress ? (
    <Txt v="L1" style={{ color: color.muted }} accessible={false}>
      ›
    </Txt>
  ) : null;
  const content = p.question ? (
    <View style={{ gap: space[1] }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space[2] }}>
        <AiBadge />
        {spinner}
        <Txt v="T2" style={{ color: color.muted, flex: 1 }}>
          {p.waiting ?? '지금 답하는 질문'}
        </Txt>
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space[3] }}>
        <Txt v="Q2" numberOfLines={2} style={{ flex: 1 }}>
          {p.question}
        </Txt>
        {chevron}
      </View>
    </View>
  ) : (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: space[2] }}>
      {spinner}
      <Txt v="L1" style={{ color: color.text, flex: 1 }}>
        {p.waiting ?? p.hint}
      </Txt>
      {chevron}
    </View>
  );
  const band = {
    backgroundColor: color.paper2,
    borderTopWidth: line.thin,
    borderBottomWidth: line.thin,
    borderColor: color.line,
    paddingVertical: space[3],
    paddingHorizontal: layout.gutter,
  } as const;
  if (!p.onPress) return <View style={band}>{content}</View>;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={p.question ? `AI 질문: ${p.question}. 지금 답하는 질문` : (p.waiting ?? p.hint)}
      accessibilityHint="받은 질문 목록을 열어요"
      onPress={p.onPress}
      style={({ pressed }) => [band, { minHeight: layout.hit }, pressed && { opacity: 0.7 }]}
    >
      {content}
    </Pressable>
  );
}
