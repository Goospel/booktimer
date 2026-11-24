// 콘텐츠 아이콘 = SF Symbols(계획 D7 — 맥락 메뉴 · 탭과 같은 그림). SF 기본값(크기 16 · 굵기 · 꾸밈 그림)을 한 곳에 둔다.
// 따로 둔 이유는 기본값 한 곳뿐이다 — jest 격리 효과는 없다(ui.tsx가 import한다).
// tintColor가 DynamicColorIOS를 따르지 않으면(기기 D1 U-266) 이 자리만 palette[scheme] hex로 물러난다.
import { type SFSymbol, SymbolView } from 'expo-symbols';
import type { ColorValue } from 'react-native';

import { color } from '../theme/tokens';

export type { SFSymbol };

/** 16pt · 선 굵기 medium(시안 Lucide 선 2에 맞춘 값) · 꾸밈 그림이라 VoiceOver는 건너뛴다(뜻은 옆 글자가 진다) */
export function Icon(p: { name: SFSymbol; size?: number; tint?: ColorValue }) {
  const size = p.size ?? 16;
  return <SymbolView name={p.name} size={size} tintColor={p.tint ?? color.ink} weight="medium" accessible={false} style={{ width: size, height: size }} />;
}
