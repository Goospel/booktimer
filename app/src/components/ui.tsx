// 공통 부품(언어 확정본 1.7 — 회색 금속 + 신호 주황 한 점). 색 · 글자는 새 토큰(tokens.ts)만 쓴다(REQ-54 · 470).
// 다리 파일(계획 D17) — 안 옮긴 화면이 옛 단계 키(D2 · B2 …)를 넘기는 동안 legacy의 type · stageStyle · Stage로 그린다(BRIDGE_NAMES가 잠근다 · U9에서 Stage만).
// 누름 크기는 레이아웃으로 확보한다 — hitSlop으로 채우지 않는다(REQ-56). 움직이는 값은 transform · opacity뿐(REQ-434).
import { useHeaderHeight } from 'expo-router/react-navigation';
import { Children, Fragment, type ReactNode, useEffect, useState } from 'react';
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
  useColorScheme,
  useWindowDimensions,
  View,
  type ViewStyle,
} from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue } from 'react-native-reanimated';
import { SafeAreaView } from 'react-native-safe-area-context';

import { a11yFlag } from '../lib/a11yFlag';
import { animateTo, useReduced } from '../lib/animate';
import { PRESS_FADE_MS, transitionFor } from '../lib/motion';
import { type Stage as LegacyStage, stageStyle, type } from '../theme/legacy';
import { fade, shadow } from '../theme/surfaces';
import { color, font, layout, radius, type Scheme, space, type Stage, stageStyle as plexStyle, type Weight, type as plexType } from '../theme/tokens';
import { Icon, type SFSymbol } from './Icon';

/** hairline(그림 치수 — 언어 1.4 · 토큰이 아니다) */
const HAIR = 0.5;

const useScheme = (): Scheme => (useColorScheme() === 'dark' ? 'dark' : 'light');

// ── 굵은 텍스트 — 구독은 첫 Txt 렌더 때 한 번만(a11yFlag) ──
const useBold = a11yFlag(() => AccessibilityInfo.isBoldTextEnabled(), 'boldTextChanged');

/** 개발 모드에서 누름 영역이 44 미만이면 알린다 — U-33 ④가 Metro 로그에서 센다(출시 번들엔 없다). */
function hitCheck(label: string) {
  if (!__DEV__) return undefined;
  return (e: LayoutChangeEvent) => {
    const { width, height } = e.nativeEvent.layout;
    if (width < layout.hit || height < layout.hit) console.warn('hit<44', label, width, height);
  };
}

/** 누름 부품 · 입력칸의 기본 스타일 — ui.test.ts가 최소 크기 · 경계 색을 읽는다(REQ-56). 그림자 문자열은 모드를 따라 렌더에서 얹는다 */
export const base = StyleSheet.create({
  /** 채움 키(언어 1.7-2) — 하우징 안 44 */
  ink: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    borderRadius: radius.m,
    paddingHorizontal: space[4],
    alignItems: 'center',
    justifyContent: 'center',
  },
  /** 보조 키(언어 1.7-3) — 면 · 테두리는 아래 face 층이 */
  outline: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    borderRadius: radius.m,
    paddingHorizontal: space[4],
    alignItems: 'center',
    justifyContent: 'center',
  },
  /** 폭 가득 키 높이 48 */
  full: { minHeight: layout.keyFull, alignSelf: 'stretch' },
  /** 링크(언어 1.7-4) — 높이 44 줄 */
  text: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    alignItems: 'center',
    justifyContent: 'center',
  },
  /** 글 행(언어 1.7-12) — 선은 그리지 않는다(RowGroup이 행 사이에만) */
  row: {
    minHeight: layout.hit,
    paddingVertical: space[3],
    justifyContent: 'center',
  },
  switchRow: {
    minHeight: layout.keyFull,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: space[3],
  },
  /** 상태 세 칸 한 칸(책 화면) — 고른 칸 표시는 화면이 얹는다 */
  segment: {
    minHeight: layout.hit,
    minWidth: layout.hit,
    alignItems: 'center',
    justifyContent: 'center',
  },
  /** 입력 칸(언어 1.7-19) — 오목: 면 bg · 그림자 2″ · 1 mark */
  field: {
    minHeight: layout.keyFull,
    borderWidth: 1,
    borderColor: color.mark,
    borderRadius: radius.m,
    backgroundColor: color.bg,
    paddingHorizontal: space[3],
    flexDirection: 'row',
    alignItems: 'center',
    gap: space[2],
  },
}) satisfies Record<string, ViewStyle | TextStyle>;

// ── 글자 ──────────────────────────────────────────────────────────────────
const isLegacy = (v: Stage | LegacyStage): v is LegacyStage => v in type;

/** w = 단계 안 굵기 갈래(보조 500 · 600 · 각주 500 …) — 굵기 키 없이 face를 고른다(REQ-470) */
export function Txt({ v, w, style, ...rest }: TextProps & { v: Stage | LegacyStage; w?: Weight }) {
  const bold = useBold();
  const old = isLegacy(v);
  const header = v === 'D2' || v === 'M1' || v === 'H1' || v === 'display' || v === 'title';
  return (
    <Text
      accessibilityRole={header ? 'header' : undefined}
      maxFontSizeMultiplier={old ? type[v].maxScale : plexType[v].maxScale}
      lineBreakStrategyIOS="hangul-word"
      {...rest}
      style={[{ color: color.ink }, old ? stageStyle(v, { bold }) : plexStyle(v, { bold, w }), style]}
    />
  );
}

/** 큰 글자(AX — 글자 배율 1.353 이상)면 가로 줄을 세로로 쌓는다(시각 보충 §4.1 배치 규칙). */
export const useStacked = () => useWindowDimensions().fontScale >= 1.353;

// ── 배지(언어 1.7-20) — 높이 20 · r2 · 1 mark · 면 없음 · 13/18 500 ink ──
/** kind는 안 옮긴 화면이 넘기는 옛 값 — 모양은 하나다(U5b · U6이 화면을 옮기며 지운다) */
export function Badge(p: { label: string; kind?: 'tint' | 'outline' | 'solid' }) {
  return (
    <View
      style={{
        alignSelf: 'flex-start',
        minHeight: 20,
        paddingHorizontal: 6,
        justifyContent: 'center',
        borderRadius: radius.s,
        borderWidth: 1,
        borderColor: color.mark,
      }}
    >
      <Txt v="foot" w={500}>
        {p.label}
      </Txt>
    </View>
  );
}

/** AI가 만든 질문 표시(v1 REQ-39 · R-12) — 질문을 그리는 자리마다 붙는다(소스 가드가 QuestionBand · QuestionSheet에서 찾는다). */
export const AiBadge = () => <Badge kind="outline" label="AI 질문" />;

// ── 화면 틀 ───────────────────────────────────────────────────────────────
// 쌓는 화면(stacked)은 유리 머리(headerTransparent) 밑 y=0부터 그려진다 — 위 안전 영역 대신 머리 높이(상태바 포함)만큼 띄운다(U-60).
// fade = 쌓는 화면 아래 가장자리 120(언어 1.7-26 — 책 화면이 쓴다 · 탭 화면 아래는 시스템)
export function Screen(p: { children: ReactNode; scroll?: boolean; stacked?: boolean; fade?: boolean }) {
  return p.stacked ? <StackedScreen {...p} /> : <ScreenBody {...p} />;
}

function StackedScreen(p: { children: ReactNode; scroll?: boolean; fade?: boolean }) {
  return <ScreenBody {...p} top={useHeaderHeight() + space[6]} />;
}

/** 본문 한 단(iPad — REQ-104). 최대 640을 가운데에 — iPhone 폭(≤ 440)에선 아무 일도 없다.
 *  늘 paddingHorizontal: layout.gutter와 같은 상자에 건다 — 띠 · 본문 · 막대의 글 왼쪽 끝이 한 줄에 선다. */
export const readColumn = { width: '100%', maxWidth: layout.readMax, alignSelf: 'center' } as const;

function ScreenBody({ children, scroll, top, fade: edge }: { children: ReactNode; scroll?: boolean; top?: number; fade?: boolean }) {
  const scheme = useScheme();
  const pad = { paddingHorizontal: layout.gutter, paddingVertical: space[6], paddingTop: top ?? space[6], gap: space[4] };
  return (
    <SafeAreaView edges={top === undefined ? undefined : ['bottom', 'left', 'right']} style={{ flex: 1, backgroundColor: color.bg }}>
      {scroll ? (
        <ScrollView contentContainerStyle={[pad, readColumn]} keyboardShouldPersistTaps="handled">
          {children}
        </ScrollView>
      ) : (
        <View style={[{ flex: 1 }, pad, readColumn]}>{children}</View>
      )}
      {edge ? <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: 120, backgroundImage: fade(scheme, 'bottom') }} /> : null}
    </SafeAreaView>
  );
}

// ── 버튼 ──────────────────────────────────────────────────────────────────
// kind 이름은 그대로(계획 D13 — 가드 bookButtons가 글자로 센다): ink = 채움 키 · outline = 보조 키 · text = 링크.
// 눌림 = translateY 1 · scale .975(채움) / .98(보조) + 덮개 opacity 120ms(언어 1.7-2 · 3). 움직임 줄이기면 변형 없이 덮개만.
// kind가 ink ↔ outline으로 바뀌면 두 겹(면 · 글)을 opacity로 교대한다(판 스프링 — 쪽지 AI 키 M9).
export function Button(p: {
  label: string;
  onPress: () => void;
  kind?: 'ink' | 'outline' | 'text';
  disabled?: boolean;
  busy?: boolean;
  accessibilityHint?: string;
  /** 폭 가득 48(하우징 밖) */
  full?: boolean;
  /** SF Symbol 16 + 8(언어 1.7-2) */
  icon?: SFSymbol;
  /** 측정을 시작하는 보조 키의 램프 6 + 8(책 화면 「읽기 시작」) */
  lamp?: 'off' | 'on';
  /** 이동 링크(text2 + 갈매기 — 「더 보기」 · 「바꾸기」). 없으면 동작 링크(ink 500) */
  nav?: boolean;
}) {
  const kind = p.kind ?? 'outline';
  const off = p.disabled || p.busy;
  const scheme = useScheme();
  const reduced = useReduced();
  const link = kind === 'text';
  const press = useSharedValue(0);
  const veil = useSharedValue(0);
  const fill = useSharedValue(kind === 'ink' ? 1 : 0);
  useEffect(() => {
    animateTo(fill, kind === 'ink' ? 1 : 0, transitionFor('plate', { reduced, prop: 'opacity' }));
  }, [kind, reduced, fill]);
  const shrink = kind === 'ink' ? 0.025 : 0.02;
  const lift = useAnimatedStyle(() => ({ transform: [{ translateY: press.value }, { scale: 1 - shrink * press.value }] }));
  const veilStyle = useAnimatedStyle(() => ({ opacity: veil.value }));
  const fillStyle = useAnimatedStyle(() => ({ opacity: fill.value }));
  const keyStyle = useAnimatedStyle(() => ({ opacity: 1 - fill.value }));

  const pressTo = (to: number) => {
    if (!link) animateTo(press, reduced ? 0 : to, transitionFor('press', { reduced, prop: 'transform' }));
    animateTo(veil, to, { type: 'timing', duration: reduced ? 150 : PRESS_FADE_MS, easing: 'linear' }); // 줄이기면 모든 전환이 opacity 150ms(언어 1.8)
  };

  const content = (fg: typeof color.ink, w?: Weight) => (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: space[2], opacity: p.busy ? 0 : 1 }}>
      {p.lamp ? (
        <View style={{ width: 6, height: 6, borderRadius: radius.round, backgroundColor: p.lamp === 'on' ? color.signal : color.lampOff, boxShadow: p.lamp === 'on' ? shadow(scheme, 'lamp') : undefined }} />
      ) : null}
      {p.icon ? <Icon name={p.icon} tint={fg} /> : null}
      <Txt v={link ? 'aux' : 'key'} w={w} style={{ color: fg }}>
        {p.label}
      </Txt>
      {link && p.nav ? <Icon name="chevron.right" tint={fg} /> : null}
    </View>
  );

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={p.label}
      accessibilityHint={p.accessibilityHint}
      accessibilityState={{ disabled: !!off, busy: !!p.busy }}
      disabled={off}
      onPress={p.onPress}
      onPressIn={() => pressTo(1)}
      onPressOut={() => pressTo(0)}
      onLayout={hitCheck(p.label)}
      style={[p.full && base.full, p.disabled && { opacity: 0.4 }]}
    >
      <Animated.View style={[base[kind], p.full && base.full, lift]}>
        {link ? null : (
          <>
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius.m, backgroundColor: color.bg2, borderWidth: HAIR, borderColor: color.line, boxShadow: shadow(scheme, 'key') }, keyStyle]} />
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius.m, backgroundColor: color.ink, boxShadow: shadow(scheme, 'fill') }, fillStyle]} />
          </>
        )}
        <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { borderRadius: radius.m, backgroundColor: kind === 'ink' ? color.pressFill : color.pressInk }, veilStyle]} />
        {link ? (
          content(p.nav ? color.text2 : color.ink, p.nav ? undefined : 500)
        ) : (
          <>
            <Animated.View style={keyStyle}>{content(color.ink)}</Animated.View>
            {/* absolute 겹은 키의 paddingHorizontal을 받지 않는다 — 흐름 겹과 줄바꿈 폭을 맞춘다 */}
            <Animated.View pointerEvents="none" style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center', paddingHorizontal: space[4] }, fillStyle]}>
              {content(color.onFill)}
            </Animated.View>
          </>
        )}
        {p.busy ? (
          <View pointerEvents="none" style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center' }]}>
            <ActivityIndicator color={kind === 'ink' ? color.onFill : color.ink} />
          </View>
        ) : null}
      </Animated.View>
    </Pressable>
  );
}

// ── 입력칸(언어 1.7-19) — 오목 · 경계 1 mark · 포커스 1 ink · 오류 1 danger · 오류는 라벨 뒤에 붙여 읽힌다(§4.2-13) ──
export function Field({
  label,
  error,
  aside,
  icon,
  style,
  onFocus,
  onBlur,
  ...rest
}: TextInputProps & { label: string; error?: string; aside?: string; icon?: SFSymbol }) {
  const scheme = useScheme();
  const [focused, setFocused] = useState(false);
  useEffect(() => {
    if (error) AccessibilityInfo.announceForAccessibility(error);
  }, [error]);
  return (
    <View style={{ gap: space[2] }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: space[2] }}>
        <Txt v="foot" w={500} style={{ color: color.text2, flex: 1 }} accessible={false} importantForAccessibility="no">
          {label}
        </Txt>
        {aside ? (
          <Txt v="tick" style={{ color: color.text2 }}>
            {aside}
          </Txt>
        ) : null}
      </View>
      <View style={[base.field, { boxShadow: shadow(scheme, 'pressed') }, focused && { borderColor: color.ink }, error ? { borderColor: color.danger } : null]}>
        {icon ? <Icon name={icon} tint={color.text2} /> : null}
        <TextInput
          accessibilityLabel={error ? `${label}, ${error}` : label}
          placeholderTextColor={color.text2}
          maxFontSizeMultiplier={2}
          {...rest}
          onFocus={(e) => {
            setFocused(true);
            onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            onBlur?.(e);
          }}
          // 한 줄 칸 — 줄 높이를 두지 않아야 iOS가 글을 칸 가운데에 둔다(17pt · 본문 face)
          style={[{ flex: 1, alignSelf: 'stretch', color: color.ink, fontFamily: font.sans400, fontSize: 17 }, style]}
        />
      </View>
      {error ? (
        <Txt v="foot" style={{ color: color.danger }}>
          {error}
        </Txt>
      ) : null}
    </View>
  );
}

// ── 목록 행(언어 1.7-12) — 행 하나 = 접근성 요소 하나(§4.2). 고른 칸은 「선택됨」으로 읽힌다. onDelete = 길게 누르기 + VoiceOver 동작 「지우기」 ──
export function Row(p: {
  title: string;
  meta?: string;
  onPress: () => void;
  selected?: boolean;
  accessibilityHint?: string;
  onDelete?: () => void;
  /** 제목 글자만 빨강(설정 「계정 삭제」 — 빨강은 오류·삭제에만) */
  tone?: 'danger';
}) {
  const del = p.onDelete;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={p.meta ? `${p.title}, ${p.meta}` : p.title}
      accessibilityHint={p.accessibilityHint}
      accessibilityState={{ selected: !!p.selected }}
      accessibilityActions={del ? [{ name: 'delete', label: '지우기' }] : undefined}
      onAccessibilityAction={del ? (e) => e.nativeEvent.actionName === 'delete' && del() : undefined}
      onLongPress={del}
      onPress={p.onPress}
      onLayout={hitCheck(p.title)}
      style={({ pressed }) => [base.row, pressed && { backgroundColor: color.pressInk }]}
    >
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: space[3] }}>
        <View style={{ flex: 1 }}>
          <Txt v="row" w={p.selected ? 600 : undefined} numberOfLines={2} style={p.tone === 'danger' ? { color: color.danger } : undefined}>
            {p.title}
          </Txt>
          {p.meta ? (
            <Txt v="foot" style={{ color: color.text2, marginTop: space[1] }}>
              {p.meta}
            </Txt>
          ) : null}
        </View>
        {/* 굵은 텍스트면 보통 행도 600이라 굵기만으로는 고른 행이 갈리지 않는다 — 체크 표시를 함께 */}
        {p.selected ? <Icon name="checkmark" /> : null}
      </View>
    </Pressable>
  );
}

/** 글 행 묶음(언어 1.7-12 「선」) — 행 사이에만 hairline. 묶음 위아래 · 마지막 줄 아래에는 없다 */
export function RowGroup({ children }: { children: ReactNode }) {
  return (
    <View>
      {Children.toArray(children).map((c, i) => (
        <Fragment key={i}>
          {i > 0 ? <View style={{ height: HAIR, backgroundColor: color.line }} /> : null}
          {c}
        </Fragment>
      ))}
    </View>
  );
}

/** 섹션 라벨(언어 1.7-14) — 높이 44 · 15/22 600 text2 · 오른쪽에 이동 링크 또는 개수 */
export function SectionLabel(p: { label: string; count?: number | string; link?: { label: string; onPress: () => void } }) {
  return (
    <View style={{ minHeight: layout.hit, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: space[2] }}>
      <Txt v="aux" w={600} accessibilityRole="header" style={{ color: color.text2, flexShrink: 1 }}>
        {p.label}
      </Txt>
      {p.link ? (
        <Button kind="text" nav label={p.link.label} onPress={p.link.onPress} />
      ) : p.count !== undefined ? (
        <Txt v="aux" style={{ color: color.text2, fontVariant: ['tabular-nums'] }}>
          {String(p.count)}
        </Txt>
      ) : null}
    </View>
  );
}

// ── 알림 띠(언어 1.7-13) — 흐름 안의 판(화면을 덮는 토스트 대신). 오류면 판 안쪽 왼쪽 막대 2 danger + 경고 원 16 ──
export function Notice(p: { message: string; tone?: 'info' | 'error'; action?: { label: string; onPress: () => void } }) {
  useEffect(() => {
    AccessibilityInfo.announceForAccessibility(p.message);
  }, [p.message]);
  const error = p.tone === 'error';
  return (
    <View
      style={{
        backgroundColor: color.bg2,
        borderRadius: radius.m,
        borderWidth: HAIR,
        borderColor: color.line,
        paddingVertical: space[3],
        paddingHorizontal: space[4],
        overflow: 'hidden',
        flexDirection: 'row',
        alignItems: 'center',
        gap: space[2],
      }}
    >
      {error ? <View style={{ position: 'absolute', left: 0, top: 0, bottom: 0, width: 2, backgroundColor: color.danger }} /> : null}
      {error ? <Icon name="exclamationmark.circle" tint={color.danger} /> : null}
      <Txt v="aux" style={{ flex: 1 }}>
        {p.message}
      </Txt>
      {p.action ? <Button kind="text" label={p.action.label} onPress={p.action.onPress} /> : null}
    </View>
  );
}

// ── 스위치 행(언어 1.7-28) — 행 전체가 누름 요소 하나(§4.2-12). 켜짐 트랙 ink · 다크 손잡이 bg(흰 손잡이는 밝은 트랙 위에서 사라진다) ──
export function SwitchRow(p: { label: string; value: boolean; onChange: (v: boolean) => void; help?: string }) {
  const dark = useScheme() === 'dark';
  return (
    <View style={{ gap: space[1] }}>
      <Pressable
        accessibilityRole="switch"
        accessibilityLabel={p.label}
        accessibilityState={{ checked: p.value }}
        onPress={() => p.onChange(!p.value)}
        onLayout={hitCheck(p.label)}
        style={({ pressed }) => [base.switchRow, pressed && { backgroundColor: color.pressInk }]}
      >
        <Txt v="row" w={400} style={{ flex: 1 }}>
          {p.label}
        </Txt>
        <View pointerEvents="none">
          <Switch value={p.value} trackColor={{ true: color.ink }} thumbColor={dark ? color.bg : undefined} />
        </View>
      </Pressable>
      {p.help ? (
        <Txt v="foot" style={{ color: color.text2 }}>
          {p.help}
        </Txt>
      ) : null}
    </View>
  );
}
