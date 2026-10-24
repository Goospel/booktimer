// 탭 골격(시각 보충 §1.3 · §3) — 시스템 NativeTabs. iOS 아이콘(sf)은 react-native-screens가 그려 expo-symbols가 필요 없다(§0.2-5 · U-37).
import { NativeTabs } from 'expo-router/native-tabs';

import { color } from '../../theme/tokens';

export default function TabsLayout() {
  return (
    <NativeTabs tintColor={color.ink}>
      <NativeTabs.Trigger name="index">
        <NativeTabs.Trigger.Icon sf="house" />
        <NativeTabs.Trigger.Label>홈</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="library">
        <NativeTabs.Trigger.Icon sf="books.vertical" />
        <NativeTabs.Trigger.Label>서재</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="study">
        <NativeTabs.Trigger.Icon sf="pencil.line" />
        <NativeTabs.Trigger.Label>공부</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
      <NativeTabs.Trigger name="settings">
        <NativeTabs.Trigger.Icon sf="gearshape" />
        <NativeTabs.Trigger.Label>설정</NativeTabs.Trigger.Label>
      </NativeTabs.Trigger>
    </NativeTabs>
  );
}
