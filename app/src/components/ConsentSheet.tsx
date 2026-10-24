// AI 안내 · 동의 시트(PR-H 사양 §5.2 — 피그마 R7 S-CONSENT · v1 §4.7 화면 6). 동의가 없을 때 첫 AI 단추를 누른 뒤에만 뜬다.
// 문구는 lib/consentCopy 한 곳(REQ-39 가드 대상). 항목 이름만 굵게 — 같은 글자를 안쪽 Txt로 나눠 그린다(한 글자도 바뀌지 않는다).
import { View } from 'react-native';

import { CONSENT_BUTTONS, CONSENT_ITEMS, CONSENT_TITLE, splitItem } from '../lib/consentCopy';
import { color, space } from '../theme/tokens';
import { Sheet } from './Sheet';
import { Button, Txt } from './ui';

export function ConsentSheet(p: { open: boolean; busy: boolean; onAgree: () => void; onLater: () => void; onPrivacy: () => void }) {
  return (
    <Sheet
      open={p.open}
      onClose={p.onLater}
      snap={['full']}
      footer={
        <>
          <Button kind="ink" label={CONSENT_BUTTONS.agree} busy={p.busy} onPress={p.onAgree} />
          <Button kind="text" label={CONSENT_BUTTONS.later} onPress={p.onLater} />
        </>
      }
    >
      <Txt v="H2">{CONSENT_TITLE}</Txt>
      {CONSENT_ITEMS.map((item) => {
        const { name, rest } = splitItem(item);
        return (
          <View key={item} style={{ flexDirection: 'row', gap: space[2] }}>
            <Txt v="L1" style={{ color: color.text }} accessible={false}>
              ·
            </Txt>
            <Txt v="L1" style={{ color: color.text, flex: 1 }}>
              {name ? <Txt v="L2" style={{ color: color.text }}>{name}</Txt> : null}
              {rest}
            </Txt>
          </View>
        );
      })}
      <View style={{ alignItems: 'flex-start' }}>
        <Button kind="text" label={CONSENT_BUTTONS.privacy} onPress={p.onPrivacy} />
      </View>
    </Sheet>
  );
}
