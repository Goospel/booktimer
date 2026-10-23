// 여백 쓰기(PR-G 사양 §5.6 · §5.7 — 피그마 R7 S-MARGIN 안① · S-PUBLIC). 두 칸 중 하나만 있어도 남긴다(v1 REQ-96).
// 입력은 비제어다(한글 조합 보호 — value를 넘기지 않는다). [남기기]는 아래 막대의 검정 알약이고 키보드 위로 따라 올라온다(결정 C ①).
// 공개 여부는 이 화면이 직접 읽는다 — 웹에서 비공개로 바꾸고 돌아오면(AppState active) 다시 읽는다(§11-10).
import { router, useLocalSearchParams, usePreventRemove } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, AppState, Keyboard, KeyboardAvoidingView, Linking, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, library, margins as marginApi, NetworkError } from '../../api/client';
import type { MaterialsView } from '../../api/types';
import { Sheet } from '../../components/Sheet';
import { Button, Field, Notice, Txt } from '../../components/ui';
import { canSaveMargin, marginSaveStep, savedNotice } from '../../lib/marginSave';
import { color, layout, line, space } from '../../theme/tokens';

function saveFailText(e: unknown): string {
  if (e instanceof NetworkError) return e.message;
  if (e instanceof ApiError && e.status === 429) return '여백을 너무 자주 남겼어요 — 잠시 뒤 다시 남겨 주세요';
  return '여백을 남기지 못했어요 — 잠시 뒤 다시 눌러 주세요';
}

export default function MarginWrite() {
  const bookId = Number(useLocalSearchParams<{ bookId: string }>().bookId);
  const headerHeight = useHeaderHeight(); // 유리 머리(headerTransparent) — 상태바 포함 높이
  const [data, setData] = useState<MaterialsView | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [quote, setQuote] = useState('');
  const [thought, setThought] = useState('');
  const latest = useRef({ quote: '', thought: '' });
  const [busy, setBusy] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setData(await library.materials(bookId));
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e));
    }
  }, [bookId]);

  useEffect(() => {
    void load();
    const sub = AppState.addEventListener('change', (s) => s === 'active' && void load());
    return () => sub.remove();
  }, [load]);

  // 쓴 것을 두고 나가면 묻는다(뒤로 단추 · 가장자리 밀기 — U-57). 남긴 직후 돌아가기는 막지 않는다.
  const dirty = canSaveMargin({ quote, thought });
  const allowLeave = usePreventRemove(dirty && !leaving, ({ repeat }) => {
    Alert.alert('쓰던 여백을 지울까요?', undefined, [
      { text: '계속 쓰기', style: 'cancel' },
      {
        text: '지우기',
        style: 'destructive',
        onPress: () => {
          setLeaving(true);
          repeat();
        },
      },
    ]);
  });

  function onSave() {
    Keyboard.dismiss();
    // 다음 틱에 읽는다 — 조합 중이던 마지막 음절이 확정된 뒤의 값(v1 REQ-36 · U-05a)
    setTimeout(() => {
      if (!data || loadError) return; // 다시 읽기가 실패했으면 옛 공개 여부로 판정하지 않는다(§5.6)
      const v = latest.current;
      if (!canSaveMargin(v)) return;
      if (marginSaveStep({ isPublic: data.book.isPublic, thought: v.thought, margins: data.margins }) === 'confirm') setSheetOpen(true);
      else void save();
    }, 0);
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      await marginApi.create({ bookId, ...latest.current });
      savedNotice.mark();
      setLeaving(true);
      allowLeave();
      router.back();
    } catch (e) {
      setError(saveFailText(e)); // 쓴 글은 그대로 둔다
      setBusy(false);
    }
  }

  return (
    <SafeAreaView edges={['bottom']} style={{ flex: 1, backgroundColor: color.paper }}>
      {/* KAV는 화면 y=0에서 시작한다(머리가 투명이라 위로 깔린다) — 오프셋 0이 키보드 틈 0이다(U-55) */}
      <KeyboardAvoidingView behavior="padding" keyboardVerticalOffset={0} style={{ flex: 1 }}>
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={{
            paddingHorizontal: layout.gutter,
            paddingTop: headerHeight + space[6],
            paddingBottom: space[6],
            gap: space[4],
          }}
          keyboardShouldPersistTaps="handled"
        >
          {data ? (
            <View style={{ gap: space[1] }}>
              <Txt v="H2" numberOfLines={2}>
                {data.book.title}
              </Txt>
              {/* 다시 읽기가 실패하면 공개 여부를 모른다 — 옛 안내를 두지 않는다 */}
              {loadError ? null : (
                <Txt v="L1" style={{ color: color.text }}>
                  {data.book.isPublic
                    ? '공개 책이라 생각을 적은 여백은 웹·미니앱의 내 책방에서 다른 사람도 봐요'
                    : '비공개 책이라 이 여백은 나만 봐요'}
                </Txt>
              )}
            </View>
          ) : null}
          {loadError ? <Notice tone="error" message={loadError} action={{ label: '다시 불러오기', onPress: () => void load() }} /> : null}
          <View style={{ gap: space[2] }}>
            <Field
              label="책에서 옮긴 문장"
              aside={`${quote.length}/200`}
              placeholder="밑줄 그은 문장을 옮겨 보세요"
              multiline
              maxLength={200}
              defaultValue=""
              onChangeText={(t) => {
                latest.current.quote = t;
                setQuote(t);
              }}
              style={{ minHeight: 72 + space[3] * 2, maxHeight: 120 + space[3] * 2, paddingVertical: space[3], textAlignVertical: 'top' }}
            />
            <Txt v="L1" style={{ color: color.muted }}>
              카메라로 옮길 땐 밑줄 친 문장만 틀 안에 들어오게 가까이 비춰 주세요
            </Txt>
          </View>
          <View style={{ gap: space[2] }}>
            <Field
              label="내 생각"
              aside={`${thought.length}/500`}
              placeholder="그 문장에 대해 든 생각을 남겨 보세요"
              multiline
              maxLength={500}
              defaultValue=""
              onChangeText={(t) => {
                latest.current.thought = t;
                setThought(t);
              }}
              style={{ minHeight: 144 + space[3] * 2, maxHeight: 192 + space[3] * 2, paddingVertical: space[3], textAlignVertical: 'top' }}
            />
            <Txt v="T2" style={{ color: color.muted }}>
              옮긴 문장만 남겨도 돼요
            </Txt>
          </View>
          {error ? <Notice tone="error" message={error} /> : null}
        </ScrollView>
        {/* 아래 막대 — InputAccessoryView가 아니라 KeyboardAvoidingView 안의 보통 View(스파이크 통과 꼴) */}
        <View
          style={{
            backgroundColor: color.paper,
            borderTopWidth: line.thin,
            borderTopColor: color.line,
            paddingVertical: space[3],
            paddingHorizontal: layout.gutter,
          }}
        >
          {/* 책 정보를 못 읽으면(다시 읽기 실패 포함) 꺼 둔다 — 공개 여부를 모르면 확인 판정을 못 한다 */}
          <Button kind="ink" label="남기기" disabled={!dirty || !data || !!loadError} busy={busy} onPress={onSave} />
        </View>
      </KeyboardAvoidingView>

      {/* 공개 책 확인 — [남기기]를 누른 뒤에만 뜬다(진입 직후 덮지 않는다) */}
      <Sheet open={sheetOpen} onClose={() => setSheetOpen(false)}>
        <Txt v="H2">공개 책이에요</Txt>
        <Txt v="B2" style={{ color: color.text }}>
          이 책은 공개라 여기 쓴 여백이 웹·미니앱의 내 책방에서 다른 사람에게 보여요
        </Txt>
        <Txt v="T2" style={{ color: color.muted }}>
          생각을 비우고 옮긴 문장만 남기면 나만 봐요
        </Txt>
        <Button
          kind="ink"
          label="그래도 남기기"
          onPress={() => {
            setSheetOpen(false);
            void save();
          }}
        />
        <Button
          kind="outline"
          label="웹에서 비공개로 바꾸기"
          onPress={() => {
            // 시트를 먼저 닫는다 — 쓴 글은 지우지 않는다(돌아오면 AppState로 공개 여부를 다시 읽는다)
            setSheetOpen(false);
            void Linking.openURL('https://booktimer.app/books');
          }}
        />
        <Button kind="text" label="취소" onPress={() => setSheetOpen(false)} />
      </Sheet>
    </SafeAreaView>
  );
}
