// 표지(시각 보충 §2.4) — 사진이 없으면 천 표지(제목 해시 색 + 첫 글자). 모서리 0(홈 카드 모양 그대로). 꾸밈이라 읽히지 않는다(U-33).
import { Image, View } from 'react-native';

import { coverColor, initialOf } from '../theme/cover';
import { cover } from '../theme/tokens';
import { Txt } from './ui';

const INITIAL = { row: 'Q1', card: 'H2', head: 'D2' } as const;
const hidden = { accessible: false, accessibilityElementsHidden: true, importantForAccessibility: 'no-hide-descendants' as const };

export function Cover(p: { title: string; coverUrl: string | null; size: 'row' | 'card' | 'head' }) {
  const box = cover[p.size];
  const style = { width: box.width, height: box.height };
  if (p.coverUrl) return <Image source={{ uri: p.coverUrl }} style={style} resizeMode="cover" {...hidden} />;
  return (
    <View style={[style, { backgroundColor: coverColor(p.title), alignItems: 'center', justifyContent: 'center' }]} {...hidden}>
      {/* 고정 크기 상자라 큰 글자를 따르면 넘친다(사양 §11-7) */}
      <Txt v={INITIAL[p.size]} allowFontScaling={false} style={{ color: cover.fg }}>
        {initialOf(p.title)}
      </Txt>
    </View>
  );
}
