// 시트(시각 보충 §3 · REQ-57) — @expo/ui universal BottomSheet(iOS = SwiftUI sheet) 감싸개. 안은 RN 부품이라 RNHostView로 싣는다.
// ui.tsx와 따로 둔다 — ui.test.ts가 ui.tsx를 jest에서 import하는데 네이티브 시트 모듈을 끌고 오지 않게.
import { BottomSheet, RNHostView } from '@expo/ui';
import type { ReactNode } from 'react';
import { View } from 'react-native';

import { color, layout, space } from '../theme/tokens';

export function Sheet(p: { open: boolean; onClose: () => void; children: ReactNode }) {
  return (
    <BottomSheet isPresented={p.open} onDismiss={p.onClose} containerColor={color.paper} contentPadding={0}>
      <RNHostView matchContents>
        <View style={{ paddingHorizontal: layout.gutter, paddingTop: space[6], paddingBottom: space[8], gap: space[3] }}>{p.children}</View>
      </RNHostView>
    </BottomSheet>
  );
}
