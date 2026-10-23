// TDS를 대신할 공통 부품(시각 보충 §3). PR-E는 로그인이 쓰는 것만 둔다 — 뒤 PR이 자기 부품을 더한다.
// 색은 토큰만 쓴다(REQ-54). 누름 크기는 레이아웃으로 확보한다 — hitSlop으로 채우지 않는다(§4.3 · REQ-56).
import { type ReactNode, useEffect, useSyncExternalStore } from 'react';
import {
  AccessibilityInfo,
  ActivityIndicator,
  type LayoutChangeEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  type TextInputProps,
  type TextProps,
  type TextStyle,
  View,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { color, layout, line, radius, space, type Stage, stageStyle, type } from '../theme/tokens';

// ── 굵은 텍스트 — 구독은 첫 Txt 렌더 때 한 번만(모듈 import에는 부작용이 없다 — ui.test가 base를 읽는다) ──
let boldOn = false;
let started = false;
const listeners = new Set<() => void>();
function setBold(v: boolean) {
  if (v === boldOn) return;
  boldOn = v;
  listeners.forEach((l) => l());
}
function subscribeBold(cb: () => void) {
  if (!started) {
    started = true;
    AccessibilityInfo.isBoldTextEnabled().then(setBold, () => {});
    AccessibilityInfo.addEventListener('boldTextChanged', setBold);
  }
  listeners.add(cb);
  return () => listeners.delete(cb);
}
const useBold = () => useSyncExternalStore(subscribeBold, () => boldOn);

/** 개발 모드에서 누름 영역이 44 미만이면 알린다 — U-33 ④가 Metro 로그에서 센다(출시 번들엔 없다). */
function hitCheck(label: string) {
  if (!__DEV__) return undefined;
  return (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width < layout.hit || height < layout.hit) console.warn('hit<44', label, width, height);
  };
}

/** 누름 부품·입력칸의 기본 스타일 — ui.test.ts가 최소 크기·경계 색을 읽는다(REQ-56). */
export const base = StyleSheet.create({
  ink: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    borderRadius: radius.pill,
    backgroundColor: color.ink,
    paddingHorizontal: space[5],
    alignItems: 'center',
    justifyContent: 'center',
  },
  outline: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    borderRadius: radius.pill,
    borderWidth: line.thin,
    borderColor: color.ink,
    paddingHorizontal: space[5],
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    paddingHorizontal: space[2],
    alignItems: 'center',
    justifyContent: 'center',
  },
  row: {
    minHeight: layout.hit,
    borderBottomWidth: line.thin,
    borderBottomColor: color.line,
    justifyContent: 'center',
  },
  switchRow: {
    minHeight: layout.hit,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
  },
  field: {
    minHeight: layout.hit,
    borderWidth: line.thin,
    borderColor: color.muted,
    borderRadius: radius.sm,
    paddingHorizontal: space[3],
    color: color.ink,
    ...type.B2.style,
  },
}) satisfies Record<string, ViewStyle | TextStyle>;

// ── 글자 ──────────────────────────────────────────────────────────────────
export function Txt({ v, style, ...rest }: TextProps & { v: Stage }) {
  const bold = useBold();
  const header = v === 'D2' || v === 'M1' || v === 'H1';
  return (
    <Text
      accessibilityRole={header ? 'header' : undefined}
      maxFontSizeMultiplier={type[v].maxScale}
      lineBreakStrategyIOS="hangul-word"
      {...rest}
      style={[{ color: color.ink }, stageStyle(v, { bold }), style]}
    />
  );
}

// ── 화면 틀 ───────────────────────────────────────────────────────────────
export function Screen({ children, scroll }: { children: ReactNode; scroll?: boolean }) {
  const pad = { paddingHorizontal: layout.gutter, paddingVertical: space[6], gap: space[4] };
  return (
    <SafeAreaView style={{ flex: 1, backgroundColor: color.paper }}>
      {scroll ? (
        <ScrollView contentContainerStyle={pad} keyboardShouldPersistTaps="handled">
          {children}
        </ScrollView>
      ) : (
        <View style={[{ flex: 1 }, pad]}>{children}</View>
      )}
    </SafeAreaView>
  );
}

// ── 버튼 ──────────────────────────────────────────────────────────────────
export function Button(p: {
  label: string;
  onPress: () => void;
  kind?: 'ink' | 'outline' | 'text';
  disabled?: boolean;
  busy?: boolean;
  accessibilityHint?: string;
}) {
  const kind = p.kind ?? 'outline';
  const off = p.disabled || p.busy;
  const ink = kind === 'ink';
  const fg = ink ? (off ? color.muted : color.onInk) : color.ink;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={p.label}
      accessibilityHint={p.accessibilityHint}
      accessibilityState={{ disabled: !!off, busy: !!p.busy }}
      disabled={off}
      onPress={p.onPress}
      onLayout={hitCheck(p.label)}
      style={({ pressed }) => [base[kind], ink && off && { backgroundColor: color.paper2 }, pressed && { opacity: 0.7 }]}
    >
      {p.busy ? (
        <ActivityIndicator color={fg} />
      ) : (
        <Txt v={kind === 'text' ? 'L2' : 'BT'} style={{ color: fg }}>
          {p.label}
        </Txt>
      )}
    </Pressable>
  );
}

// ── 입력칸 — 시스템 글꼴 · 경계 muted · 오류는 라벨 뒤에 붙여 읽힌다(§4.2-13) ──
export function Field({ label, error, style, ...rest }: TextInputProps & { label: string; error?: string }) {
  useEffect(() => {
    if (error) AccessibilityInfo.announceForAccessibility(error);
  }, [error]);
  return (
    <View style={{ gap: space[1] }}>
      <Txt v="L1" style={{ color: color.text }} accessible={false} importantForAccessibility="no">
        {label}
      </Txt>
      <TextInput
        accessibilityLabel={error ? `${label}, ${error}` : label}
        placeholderTextColor={color.muted}
        maxFontSizeMultiplier={2}
        {...rest}
        style={[base.field, error ? { borderColor: color.danger } : null, style]}
      />
      {error ? (
        <Txt v="L1" style={{ color: color.danger }}>
          {error}
        </Txt>
      ) : null}
    </View>
  );
}

// ── 목록 행 — 행 하나 = 접근성 요소 하나(§4.2). 고른 칸은 「선택됨」으로 읽힌다 ──
export function Row(p: { title: string; meta?: string; onPress: () => void; selected?: boolean; accessibilityHint?: string }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={p.meta ? `${p.title}, ${p.meta}` : p.title}
      accessibilityHint={p.accessibilityHint}
      accessibilityState={{ selected: !!p.selected }}
      onPress={p.onPress}
      onLayout={hitCheck(p.title)}
      style={({ pressed }) => [base.row, { paddingVertical: space[2] }, pressed && { backgroundColor: color.pressed }]}
    >
      <Txt v={p.selected ? 'B1' : 'B2'}>{p.title}</Txt>
      {p.meta ? (
        <Txt v="T2" style={{ color: color.muted }}>
          {p.meta}
        </Txt>
      ) : null}
    </Pressable>
  );
}

// ── 알림 — 흐름 안의 띠(화면을 덮는 토스트 대신) ───────────────────────────
export function Notice(p: { message: string; tone?: 'info' | 'error'; action?: { label: string; onPress: () => void } }) {
  useEffect(() => {
    AccessibilityInfo.announceForAccessibility(p.message);
  }, [p.message]);
  return (
    <View style={{ backgroundColor: color.paper2, padding: space[3], gap: space[1] }}>
      <Txt v="L1" style={{ color: p.tone === 'error' ? color.danger : color.text }}>
        {p.message}
      </Txt>
      {p.action ? <Button kind="text" label={p.action.label} onPress={p.action.onPress} /> : null}
    </View>
  );
}

// ── 스위치 행 — 행 전체가 누름 요소 하나(§4.2-12) ──────────────────────────
export function SwitchRow(p: { label: string; value: boolean; onChange: (v: boolean) => void; help?: string }) {
  return (
    <View style={{ gap: space[1] }}>
      <Pressable
        accessibilityRole="switch"
        accessibilityLabel={p.label}
        accessibilityState={{ checked: p.value }}
        onPress={() => p.onChange(!p.value)}
        onLayout={hitCheck(p.label)}
        style={({ pressed }) => [base.switchRow, pressed && { backgroundColor: color.pressed }]}
      >
        <Txt v="B2" style={{ flex: 1 }}>
          {p.label}
        </Txt>
        <View pointerEvents="none">
          <Switch value={p.value} trackColor={{ true: color.ink }} />
        </View>
      </Pressable>
      {p.help ? (
        <Txt v="T2" style={{ color: color.muted }}>
          {p.help}
        </Txt>
      ) : null}
    </View>
  );
}
