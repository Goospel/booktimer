// 시트(시각 보충 §3 · REQ-57) — @expo/ui universal BottomSheet(iOS = SwiftUI sheet) 감싸개. 안은 RN 부품이라 RNHostView로 싣는다.
// ui.tsx와 따로 둔다 — ui.test.ts가 ui.tsx를 jest에서 import하는데 네이티브 시트 모듈을 끌고 오지 않게.
// snap이 없으면 내용 맞춤(목표 · 공개 책 확인). 있으면 고정 높이 + 안 스크롤 + (footer) 아래 고정 단추 — 질문 시트 · 동의 시트(PR-H §9).
import { BottomSheet, RNHostView } from '@expo/ui';
import type { ReactNode } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { color, layout, line, space } from '../theme/tokens';

const inner = { paddingHorizontal: layout.gutter, paddingTop: space[6], paddingBottom: space[8], gap: space[3] } as const;

export function Sheet(p: { open: boolean; onClose: () => void; children: ReactNode; snap?: ('half' | 'full')[]; footer?: ReactNode }) {
  const bottom = useSafeAreaInsets().bottom;
  if (!p.snap) {
    return (
      <BottomSheet isPresented={p.open} onDismiss={p.onClose} containerColor={color.paper} contentPadding={0}>
        <RNHostView matchContents>
          <View style={inner}>{p.children}</View>
        </RNHostView>
      </BottomSheet>
    );
  }
  return (
    <BottomSheet isPresented={p.open} onDismiss={p.onClose} containerColor={color.paper} contentPadding={0} snapPoints={p.snap}>
      <RNHostView matchContents={false}>
        {/* flexGrow 1 + height 0 — 스크롤 자식의 내용 높이를 물려받지 않고 시트 높이를 채운다(@expo/ui 커뮤니티 시트 꼴 · U-36 · U-65) */}
        <View style={{ flexGrow: 1, height: 0 }}>
          <ScrollView style={{ flex: 1 }} contentContainerStyle={inner}>
            {p.children}
          </ScrollView>
          {p.footer ? (
            <View
              style={{
                borderTopWidth: line.thin,
                borderTopColor: color.line,
                paddingTop: space[3],
                paddingHorizontal: layout.gutter,
                paddingBottom: space[3] + bottom,
                gap: space[2],
              }}
            >
              {p.footer}
            </View>
          ) : null}
        </View>
      </RNHostView>
    </BottomSheet>
  );
}
