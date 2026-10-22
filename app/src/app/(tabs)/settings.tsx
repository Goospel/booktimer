import Constants from 'expo-constants';
import * as Updates from 'expo-updates';
import { View } from 'react-native';

import { Screen, Txt } from '../../components/ui';
import { buildInfoLine } from '../../lib/buildInfo';
import { color } from '../../theme/tokens';

// 설정 — 항목은 PR-I가 채운다. 맨 아래 줄은 지금 뜬 JS가 무엇인지 기기에서 읽는 자리다(앞당김 보충 REQ-72 · U-40).
const BUILD_LINE = buildInfoLine({
  version: Constants.expoConfig?.version ?? '?',
  channel: Updates.channel,
  updateId: Updates.updateId,
  isEmbeddedLaunch: Updates.isEmbeddedLaunch,
  updatesEnabled: Updates.isEnabled,
});

export default function Settings() {
  return (
    <Screen>
      <Txt v="D2">설정</Txt>
      <View style={{ flex: 1 }} />
      <Txt v="T2" selectable style={{ color: color.muted }}>
        {BUILD_LINE}
      </Txt>
    </Screen>
  );
}
