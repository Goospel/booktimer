// 설정(PR-I · 피그마 R7 「PR-I 설정」 확정 — S-SET · S-SET-TIME · S-SET-PERM · S-SET-AI · S-SET-LOGOUT).
// 무리 넷 「알림 → AI 질문 → 안내 → 계정」 + 그림 출처 · 빌드 줄. 검정 알약 0 · 진입 직후 덮는 것 0 — 권한 창은 스위치를 켤 때만 뜬다.
import DateTimePicker from '@expo/ui/community/datetime-picker';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import { router, useFocusEffect } from 'expo-router';
import * as Updates from 'expo-updates';
import { type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { Alert, AppState, Linking, View } from 'react-native';

import { account, type AccountInfo, ApiError, failText, reading, session, writing } from '../../api/client';
import type { ConsentView } from '../../api/types';
import { base, Button, Notice, Row, Screen, SwitchRow, Txt, useStacked } from '../../components/ui';
import { buildInfoLine } from '../../lib/buildInfo';
import { deleteVia } from '../../lib/deleteVia';
import { draftSync } from '../../lib/draftSync';
import { dateLabel } from '../../lib/format';
import { logout } from '../../lib/logout';
import { askPermission, permission, type PermissionState, reminderStore, syncReminders } from '../../lib/notify';
import { clampTime, DEFAULT_REMINDER, type ReminderSetting } from '../../lib/reminders';
import { color, line, space } from '../../theme/tokens';

// 맨 아래 줄은 지금 뜬 JS가 무엇인지 기기에서 읽는 자리다(앞당김 보충 REQ-72 · U-40).
const BUILD_LINE = buildInfoLine({
  version: Constants.expoConfig?.version ?? '?',
  channel: Updates.channel,
  updateId: Updates.updateId,
  isEmbeddedLaunch: Updates.isEmbeddedLaunch,
  updatesEnabled: Updates.isEnabled,
});

/** 처리방침 11절 문의처와 같은 값 — Apple 앱 계정은 웹 로그인이 없어 웹 문의함을 못 쓴다 */
const CONTACT = 'kimsadol369785@gmail.com';
const PERM_TEXT = '알림을 허용해야 독서 알림을 받을 수 있어요 — iPhone 설정에서 책생글 알림을 켜 주세요';

type NoticeState = { at: 'ai' | 'info'; message: string; tone?: 'info' | 'error' } | null;

const todayAt = (h: number, m: number) => {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), h, m);
};

export default function Settings() {
  const stacked = useStacked();
  const [setting, setSetting] = useState<ReminderSetting>(DEFAULT_REMINDER);
  const [perm, setPerm] = useState<PermissionState | null>(null);
  const [tried, setTried] = useState(false);
  const [acc, setAcc] = useState<AccountInfo | null>(null);
  const [consent, setConsent] = useState<ConsentView | null>(null);
  const [consentError, setConsentError] = useState<string | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 권한 창이 닫히며 AppState active가 load를 부른다 — 켜기 · 시각 바꾸기보다 먼저 나간 load가 끝나며 옛 설정으로 덮지 않게 순번을 본다
  const loadSeq = useRef(0);

  const load = useCallback(async () => {
    const seq = ++loadSeq.current;
    const [s, p, a] = await Promise.all([reminderStore.get(), permission().catch(() => null), session.account()]);
    if (seq === loadSeq.current) {
      setSetting(s);
      setPerm(p);
    }
    setAcc(a);
    try {
      setConsent(await account.consent());
      setConsentError(null);
    } catch (e) {
      setConsentError(failText(e, '동의 상태를 불러오지 못했어요 — 잠시 뒤 다시 해 주세요'));
    }
  }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => s === 'active' && void load());
    return () => sub.remove();
  }, [load]);
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  async function toggle(next: boolean) {
    setNotice(null);
    if (!next) {
      const s = { ...setting, on: false };
      loadSeq.current++;
      await reminderStore.set(s);
      setSetting(s);
      await syncReminders(null); // 계획 0 → 우리 예약 전부 취소(목표 달성 포함 — REQ-92)
      return;
    }
    let p = await permission();
    if (p.status === 'undetermined') p = await askPermission();
    loadSeq.current++; // 권한 창 사이에 나간 load를 무효로
    setPerm(p);
    setTried(true);
    if (p.status !== 'granted') return; // 꺼진 채 — 안내는 렌더가 그린다
    const s = { ...setting, on: true };
    await reminderStore.set(s);
    setSetting(s);
    await syncReminders(await reading.dashboard().catch(() => null));
  }

  function pick(d: Date) {
    const s = { ...setting, ...clampTime({ hour: d.getHours(), minute: d.getMinutes() }) };
    loadSeq.current++;
    setSetting(s);
    void reminderStore.set(s);
    // 휠이 돌 때마다 30개를 다시 걸지 않는다 — 1초 뒤 한 번
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      void reading
        .dashboard()
        .catch(() => null)
        .then((dash) => syncReminders(dash));
    }, 1000);
  }

  function withdraw() {
    setNotice(null);
    Alert.alert('AI 질문 동의를 거둘까요?', '거두면 다음에 질문을 받을 때 다시 물어요. 받은 질문과 쓴 글은 그대로예요', [
      { text: '취소', style: 'cancel' },
      {
        text: '거두기',
        onPress: () => {
          writing.consent(false).then(
            (v) => {
              setConsent(v);
              setNotice({ at: 'ai', message: 'AI 질문 동의를 거뒀어요' });
            },
            (e) => {
              const early = e instanceof ApiError && e.status === 503; // 시행일 전 — 서버 문구 그대로(안내)
              setNotice({ at: 'ai', message: failText(e, '동의를 거두지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: early ? 'info' : 'error' });
            },
          );
        },
      },
    ]);
  }

  async function mail() {
    setNotice(null);
    try {
      // canOpenURL은 쓰지 않는다 — 선언하지 않은 스킴에 거짓이다. 바로 열고 실패를 잡는다
      await Linking.openURL(`mailto:${CONTACT}?subject=${encodeURIComponent('책생글 문의')}`);
    } catch {
      setNotice({ at: 'info', message: `메일 앱을 열지 못했어요 — ${CONTACT}로 보내 주세요` });
    }
  }

  async function onLogout() {
    setNotice(null);
    const userId = await session.userId();
    const drafts = userId === null ? null : draftSync({ kv: AsyncStorage, userId, put: writing.save });
    await logout({
      pending: async () => (drafts ? drafts.pending() : 0),
      confirm: () =>
        new Promise<boolean>((ok) =>
          Alert.alert(
            '로그아웃할까요?',
            '이 기기에만 있는 저장 안 된 글이 있어요 — 로그아웃하면 지워져요',
            [
              { text: '취소', style: 'cancel', onPress: () => ok(false) },
              { text: '그래도 로그아웃', style: 'destructive', onPress: () => ok(true) },
            ],
            { cancelable: false },
          ),
        ),
      revoke: () => account.logout(),
      clearDrafts: async () => {
        await drafts?.clear();
      },
      clearSession: () => session.clear(), // 루트 세션 구독이 로그인 화면 + 기기 표면 정리
    });
  }

  const permBlocked = perm !== null && perm.status !== 'granted' && (tried || setting.on);
  const consented = consent?.aiConsent === true;
  const at = (where: 'ai' | 'info') =>
    notice?.at === where ? <Notice message={notice.message} tone={notice.tone} /> : null;

  return (
    <Screen scroll>
      <Txt v="D2">설정</Txt>

      <Group
        title="알림"
        help="읽을 시간과 목표를 채운 순간을 알려 드려요"
        notice={permBlocked ? <Notice message={PERM_TEXT} action={{ label: '설정 열기', onPress: () => void Linking.openSettings() }} /> : null}
      >
        <View style={lined}>
          <SwitchRow label="독서 알림" value={setting.on} onChange={(v) => void toggle(v)} />
        </View>
        {setting.on ? (
          <View style={[base.switchRow, lined, stacked && stackedRow]}>
            <Txt v="B2" style={stacked ? undefined : { flex: 1 }}>
              알림 시각
            </Txt>
            <DateTimePicker
              mode="time"
              display="compact"
              locale="ko_KR"
              accentColor={color.ink}
              value={todayAt(setting.hour, setting.minute)}
              minimumDate={todayAt(8, 0)}
              maximumDate={todayAt(22, 0)}
              onValueChange={(_, d) => pick(d)}
            />
          </View>
        ) : null}
      </Group>

      <Group
        title="AI 질문"
        help={!consentError && consent && !consented ? '글쓰기에서 「질문 받기」를 누르면 물어요' : undefined}
        notice={consentError ? <Notice message={consentError} tone="error" action={{ label: '다시 불러오기', onPress: () => void load() }} /> : at('ai')}
      >
        {consentError ? null : (
          <View style={[base.switchRow, lined, stacked && stackedRow]}>
            {consent === null ? (
              <Txt v="B2" style={{ color: color.muted }}>
                불러오는 중이에요
              </Txt>
            ) : (
              <Txt v="B2" style={stacked ? undefined : { flex: 1 }}>
                {consented && consent.aiConsentAt ? `${dateLabel(new Date(consent.aiConsentAt))}에 동의했어요` : '아직 동의하지 않았어요'}
              </Txt>
            )}
            {consented ? <Button kind="text" label="동의 거두기" onPress={withdraw} /> : null}
          </View>
        )}
      </Group>

      <Group title="안내" notice={at('info')}>
        <Row title="처리방침" accessibilityHint="웹 페이지를 열어요" onPress={() => void Linking.openURL('https://booktimer.app/privacy')} />
        <Row title="이용약관" accessibilityHint="웹 페이지를 열어요" onPress={() => void Linking.openURL('https://booktimer.app/terms')} />
        <Row title="문의하기" accessibilityHint="메일 앱을 열어요" onPress={() => void mail()} />
      </Group>

      <Group
        title="계정"
        help={
          acc && deleteVia(acc) === 'APPLE'
            ? '웹에서 쓰던 계정이 있다면 — 이 계정을 지운 뒤 로그인 화면의 「웹에서 쓰던 계정이 있어요」로 연결할 수 있어요'
            : undefined
        }
      >
        <Row title="로그아웃" onPress={() => void onLogout()} />
        <Row title="계정 삭제" tone="danger" accessibilityHint="계정 삭제 화면을 열어요" onPress={() => router.push('/account-delete')} />
      </Group>

      <View style={{ marginTop: space[4], gap: space[1] }}>
        <Txt v="T2" style={{ color: color.muted }}>
          그림: Thomas Bewick, 1797(퍼블릭 도메인)
        </Txt>
        <Txt v="T2" selectable style={{ color: color.muted }}>
          {BUILD_LINE}
        </Txt>
      </View>
    </Screen>
  );
}

const lined = { borderBottomWidth: line.thin, borderBottomColor: color.line } as const;
/** 큰 글자 — 이름 위 · 단추 아래 · 왼쪽 맞춤 */
const stackedRow = { flexDirection: 'column', alignItems: 'flex-start', justifyContent: 'center', paddingVertical: space[2] } as const;

/** 무리 — H3 머리(머리말) · 줄들(사이 0, 줄마다 아래 가는 선) · 알림 · 무리 끝 도움말. ponytail: 셋째 화면이 쓰면 components/로 */
function Group(p: { title: string; help?: string; notice?: ReactNode; children: ReactNode }) {
  return (
    <View style={{ marginTop: space[4], gap: space[2] }}>
      <Txt v="H3" accessibilityRole="header">
        {p.title}
      </Txt>
      <View>{p.children}</View>
      {p.notice}
      {p.help ? (
        <Txt v="T2" style={{ color: color.muted }}>
          {p.help}
        </Txt>
      ) : null}
    </View>
  );
}
