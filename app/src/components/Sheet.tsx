// 시트(언어 1.7-18 · REQ-57) — @expo/ui swift-ui BottomSheet + Group 수정자를 직접 쓴다(universal 감싸개는 containerColor로 바탕을 덧칠한다).
// 바탕은 시스템 유리(presentationBackground 없음 — REQ-476) · 시트 안 RN 바탕은 투명 · 글자는 화면 규칙(언어 1.2.2 시트 예외).
// ui.tsx와 따로 둔다 — ui.test.ts가 ui.tsx를 jest에서 import하는데 네이티브 시트 모듈을 끌고 오지 않게.
// snap이 없으면 너비는 시트, 높이는 내용 맞춤(목표 · 공개 책 확인). 있으면 고정 높이 + 안 스크롤 + (footer) 아래 고정 단추 — 질문 시트 · 동의 시트(PR-H §9).
// 손으로 디텐트를 바꿀 때만 selection(언어 1.9 · U-239) — 디텐트 선택을 코드가 정하지 않으니 이 이벤트는 손으로 바꿀 때만 온다.
import { BottomSheet, Group, Host, RNHostView } from '@expo/ui/swift-ui';
import { frame, presentationDetents, presentationDragIndicator } from '@expo/ui/swift-ui/modifiers';
import type { ReactNode } from 'react';
import { ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { haptic, kindOf } from '../lib/haptics';
import { color, layout, space } from '../theme/tokens';

const inner = { paddingHorizontal: layout.gutter, paddingTop: space[6], paddingBottom: space[8], gap: space[3] } as const;
const DETENT = { half: 'medium', full: 'large' } as const;

export function Sheet(p: { open: boolean; onClose: () => void; children: ReactNode; snap?: ('half' | 'full')[]; footer?: ReactNode }) {
  const bottom = useSafeAreaInsets().bottom;
  const modifiers = [
    frame({ maxWidth: Infinity, alignment: 'topLeading' }),
    presentationDragIndicator('visible'),
    ...(p.snap ? [presentationDetents(p.snap.map((s) => DETENT[s]), { onSelectionChange: () => haptic(kindOf('detent')) })] : []),
  ];
  return (
    <Host style={{ position: 'absolute' }} pointerEvents="none">
      <BottomSheet
        isPresented={p.open}
        onIsPresentedChange={(v) => {
          if (!v) p.onClose();
        }}
        fitToContents={!p.snap}
      >
        <Group modifiers={modifiers}>
          {p.snap ? (
            <RNHostView matchContents={false}>
              {/* flexGrow 1 + height 0 — 스크롤 자식의 내용 높이를 물려받지 않고 시트 높이를 채운다(@expo/ui 커뮤니티 시트 꼴 · U-36 · U-65) */}
              <View style={{ flexGrow: 1, height: 0 }}>
                <ScrollView style={{ flex: 1 }} contentContainerStyle={inner}>
                  {p.children}
                </ScrollView>
                {p.footer ? (
                  <View
                    style={{
                      borderTopWidth: 0.5,
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
          ) : (
            // 너비는 시트에서, 높이는 내용에서 — 양 축이면 글이 줄바꿈되지 않아 잘린다(T-274)
            <RNHostView matchContents={{ vertical: true }}>
              <View style={inner}>{p.children}</View>
            </RNHostView>
          )}
        </Group>
      </BottomSheet>
    </Host>
  );
}
