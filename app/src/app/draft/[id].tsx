// 글쓰기(PR-H 사양 §5.1 · §5.3 · §5.4 · §5.6 · §5.9 — 피그마 R7 S-WRITE-0 · 1 · WAIT · ERR). 머리 → 질문 띠(고정) → (알림) → 제목 → 본문 → 아래 막대.
// AI는 질문만 한다 — 질문은 띠와 시트에만 있고, AI 글자를 본문으로 옮기는 길이 없다(v1 결정 2 · REQ-47).
// 입력은 비제어다(한글 조합 보호 — value를 넘기지 않는다). 다시 마운트(editorKey)는 409 「그쪽 불러오기」 때만.
// 들어올 때 키보드를 올리지 않는다 · 시트는 누른 뒤에만 뜬다(진입 직후 덮는 것 0).
import AsyncStorage from '@react-native-async-storage/async-storage';
import { router, Stack, useLocalSearchParams } from 'expo-router';
import { useHeaderHeight } from 'expo-router/react-navigation';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  AppState,
  Keyboard,
  KeyboardAvoidingView,
  Linking,
  Pressable,
  Share,
  TextInput,
  type TextInputInstance,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ApiError, failText, library, session, writing } from '../../api/client';
import type { DraftView, Question, Step } from '../../api/types';
import { ConsentSheet } from '../../components/ConsentSheet';
import { QuestionBand } from '../../components/QuestionBand';
import { QuestionSheet } from '../../components/QuestionSheet';
import { Button, Notice, readColumn, Screen, Txt, useStacked } from '../../components/ui';
import { aiButton, ROUNDS_MAX, sendAiStep } from '../../lib/aiStep';
import { AUTOSAVE_MS, draftSync, revisionOrThrow, type SaveOutcome, shouldDiscard } from '../../lib/draftSync';
import { buildExport, shouldMarkExported } from '../../lib/exportText';
import { againArgs, bandHint } from '../../lib/questions';
import { color, layout, line, space, type } from '../../theme/tokens';

type NoticeState = { message: string; tone?: 'info' | 'error'; action?: { label: string; onPress: () => void } } | null;
type Sync = ReturnType<typeof draftSync>;

const OFFLINE_SAVE = '인터넷에 연결되지 않았어요 — 쓴 글은 이 기기에 남아 있고, 연결되면 저장해요';
const MISMATCH = '서버에 저장된 글이 이 기기 것과 달라요 — 이 기기 것은 지우지 않았어요';
const nextTick = () => new Promise<void>((r) => setTimeout(r, 0));

export default function DraftWrite() {
  const id = Number(useLocalSearchParams<{ id: string }>().id);
  const headerHeight = useHeaderHeight(); // 유리 머리(headerTransparent) — 상태바 포함 높이
  const stacked = useStacked();
  const [draft, setDraft] = useState<DraftView | null>(null); // 서버 쪽 사실(책 · 질문 기록 · 남은 몫 · 동의) — 본문은 편집기가 쥔다
  const [materials, setMaterials] = useState<{ included: number; excluded: number } | null>(null);
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [initial, setInitial] = useState({ title: '', body: '' });
  const [editorKey, setEditorKey] = useState(0);
  const [titleText, setTitleText] = useState('');
  const [bodyText, setBodyText] = useState('');
  const [focus, setFocus] = useState<string | null>(null);
  const [waiting, setWaiting] = useState<Step | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [sheet, setSheet] = useState<'questions' | 'consent' | null>(null);
  const [consentBusy, setConsentBusy] = useState(false);
  const [keyboard, setKeyboard] = useState(false);

  const sync = useRef<Sync | null>(null);
  const titleRef = useRef('');
  const bodyRef = useRef('');
  const roundsRef = useRef(0);
  const caret = useRef(0);
  const restoreCaret = useRef<number | null>(null);
  const titleInput = useRef<TextInputInstance>(null);
  const bodyInput = useRef<TextInputInstance>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const offlineShown = useRef(false);
  const conflictOpen = useRef(false);
  const pending = useRef<{ step: Step; focus: string | null } | null>(null);
  const waitingRef = useRef(false); // 보내는 중에 떠나면 빈 글이어도 지우지 않는다 — 서버가 이미 몫을 깎았다(§8-17)

  function applyText(t: { title: string; body: string }) {
    titleRef.current = t.title;
    bodyRef.current = t.body;
    setTitleText(t.title);
    setBodyText(t.body);
    setInitial(t);
  }

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const userId = (await session.userId()) ?? 0;
      const s = draftSync({ kv: AsyncStorage, userId, put: writing.save });
      const d = await writing.get(id).catch(async (e) => {
        if (e instanceof ApiError && e.status === 404) await s.drop(id); // 지워진 글 — 기기 칸도 버린다
        throw e;
      });
      const m = d.bookId !== null ? await library.materials(d.bookId) : null;
      const slot = await s.open(d);
      sync.current = s;
      roundsRef.current = d.questions.rounds.length;
      setDraft(d);
      setMaterials(m ? { included: m.aiIncluded, excluded: m.aiExcluded } : null);
      setFocus(slot.focus);
      applyText(slot);
      if (slot.dirty) void save(); // 저장 안 된 기기 글이 서버보다 앞이다 — 바로 한 번 보낸다
    } catch (e) {
      setLoadError({ message: e instanceof Error ? e.message : String(e), notFound: e instanceof ApiError && e.status === 404 });
    }
  }, [id]); // save는 ref만 읽는다

  useEffect(() => {
    void load();
  }, [load]);

  // 키보드가 있을 때만 「키보드 내리기」(§8-6)
  useEffect(() => {
    const show = Keyboard.addListener('keyboardWillShow', () => setKeyboard(true));
    const hide = Keyboard.addListener('keyboardWillHide', () => setKeyboard(false));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  // 앱이 뒤로 가거나 돌아오면 저장(복귀 = 연결이 돌아온 뒤 dirty 칸 보내기 · §8-15) · 떠날 때 남은 저장을 보내고 빈 글은 지운다(최선 노력 — 기다리지 않는다, REQ-142)
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => (s === 'background' || s === 'active') && void save());
    return () => {
      sub.remove();
      if (timer.current) clearTimeout(timer.current);
      const s = sync.current;
      if (!s) return;
      if (shouldDiscard({ title: titleRef.current, body: bodyRef.current, rounds: roundsRef.current }) && !waitingRef.current) {
        void writing.remove(id).then(() => s.drop(id), () => undefined);
      } else {
        void s.flush(id);
      }
    };
  }, [id]);

  // 「그쪽 불러오기」로 다시 마운트한 뒤 쓰던 자리로(U-62가 실기기로 본다)
  useEffect(() => {
    const c = restoreCaret.current;
    if (c === null) return;
    restoreCaret.current = null;
    requestAnimationFrame(() => {
      bodyInput.current?.focus();
      bodyInput.current?.setSelection(c, c);
    });
  }, [editorKey]);

  function onOutcome(r: SaveOutcome) {
    if (r.kind === 'saved') offlineShown.current = false;
    else if (r.kind === 'mismatch') setNotice({ message: MISMATCH });
    else if (r.kind === 'offline' && !offlineShown.current) {
      offlineShown.current = true;
      setNotice({ message: OFFLINE_SAVE });
    } else if (r.kind === 'conflict') askConflict();
    else if (r.kind === 'gone') setNotice({ message: '글을 찾을 수 없어요', tone: 'error', action: { label: '돌아가기', onPress: () => router.back() } });
    else if (r.kind === 'error') setNotice({ message: r.message, tone: 'error' });
  }

  async function save(opts?: { exported?: boolean }): Promise<SaveOutcome | null> {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    const s = sync.current;
    if (!s) return null;
    const r = await s.flush(id, opts);
    onOutcome(r);
    return r;
  }

  function edit(patch: { title?: string; body?: string }) {
    void sync.current?.edit(id, patch); // 기기 칸에는 즉시, 서버에는 1.5초 뒤
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => void save(), AUTOSAVE_MS);
  }

  function askConflict() {
    if (conflictOpen.current) return;
    conflictOpen.current = true;
    Keyboard.dismiss();
    Alert.alert('다른 기기에서 이 글이 바뀌었어요', '한쪽만 남길 수 있어요 — 남기지 않은 쪽 내용은 사라져요', [
      { text: '그쪽 불러오기', onPress: () => void resolveConflict('theirs') },
      { text: '이 기기 것 유지', isPreferred: true, onPress: () => void resolveConflict('mine') },
    ]);
  }

  async function resolveConflict(which: 'theirs' | 'mine') {
    conflictOpen.current = false;
    const s = sync.current;
    if (!s) return;
    try {
      const d = await writing.get(id);
      setDraft(d);
      roundsRef.current = d.questions.rounds.length;
      if (which === 'mine') {
        onOutcome(await s.keepMine(id, d));
        return;
      }
      const slot = await s.takeTheirs(id, d);
      applyText(slot);
      restoreCaret.current = Math.min(caret.current, slot.body.length);
      setEditorKey((k) => k + 1);
    } catch (e) {
      setNotice({ message: failText(e, '글을 불러오지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
  }

  async function runAi(step: Step, f: string | null, consented = false) {
    if (!draft || waiting) return;
    if (!draft.aiConsent && !consented) {
      pending.current = { step, focus: f };
      Keyboard.dismiss();
      setSheet('consent');
      return;
    }
    setNotice(null);
    setSheet(null);
    setWaiting(step);
    waitingRef.current = true;
    let gone = false;
    const r = await sendAiStep({
      blur: () => {
        titleInput.current?.blur();
        bodyInput.current?.blur();
      },
      flush: async () => {
        const o = await save();
        if (o?.kind === 'gone') gone = true;
        return revisionOrThrow(o);
      },
      post: (revision) => writing.ai(id, { step, revision, focus: step === 'ASK' ? null : f }),
      reload: () => writing.get(id),
      roundsBefore: roundsRef.current,
    });
    setWaiting(null);
    waitingRef.current = false;
    if (gone) return; // save()가 띄운 「글을 찾을 수 없어요 + 돌아가기」를 남긴다
    if (r.kind === 'done') {
      roundsRef.current = r.questions.rounds.length;
      setDraft((d) => (d ? { ...d, aiConsent: true, questions: r.questions, aiUpdatedAt: r.aiUpdatedAt, aiRemainingToday: r.aiRemainingToday } : d));
      setSheet('questions'); // 누른 뒤의 결과라 진입 직후 덮기가 아니다(§8-8)
    } else if (r.kind === 'consent') {
      setDraft((d) => (d ? { ...d, aiConsent: false } : d));
      pending.current = { step, focus: f };
      setSheet('consent');
    } else if (r.kind === 'retry') {
      setNotice({ message: r.message, tone: 'error', action: { label: '다시 시도', onPress: () => void runAi(step, f, consented) } }); // 낡은 클로저의 aiConsent=false로 동의 시트를 다시 띄우지 않는다
    } else {
      setNotice({ message: r.message, tone: 'error' });
    }
  }

  async function agree() {
    setConsentBusy(true);
    try {
      const v = await writing.consent(true);
      setDraft((d) => (d ? { ...d, aiConsent: v.aiConsent } : d));
      setSheet(null);
      const next = pending.current;
      pending.current = null;
      if (v.aiConsent && next) void runAi(next.step, next.focus, true); // 누른 단계를 이어서 — 다시 누르게 하지 않는다
    } catch (e) {
      setSheet(null);
      // 시행일 전(11/23)이면 503 — 서버 문구가 날짜를 낸다(앱은 날짜를 들고 있지 않다 · §8-13)
      if (e instanceof ApiError && e.status === 503) setNotice({ message: e.message });
      else setNotice({ message: failText(e, '동의를 저장하지 못했어요 — 잠시 뒤 다시 눌러 주세요'), tone: 'error' });
    }
    setConsentBusy(false);
  }

  function pick(q: Question) {
    setFocus(q.text);
    void sync.current?.edit(id, { focus: q.text }); // 띠의 질문은 기기 칸에만(REQ-38) — 본문은 그대로
    setSheet(null);
    // 시트가 내려간 뒤 본문 끝에 커서(키보드가 올라온다 — U-65가 실기기로 본다)
    setTimeout(() => {
      const n = bodyRef.current.length;
      bodyInput.current?.focus();
      bodyInput.current?.setSelection(n, n);
    }, 350);
  }

  async function exportDraft() {
    Keyboard.dismiss();
    await nextTick(); // 조합 중이던 마지막 음절 확정
    const saved = await save();
    const r = await Share.share({ message: buildExport({ title: titleRef.current, body: bodyRef.current }) });
    // ponytail: 내보낸 표시는 지표뿐이라 실패해도 조용히 넘긴다 — onOutcome을 거치지 않는다(글은 이미 저장됐는데 저장 실패 알림이 뜨면 오도다 · §8-22)
    if (shouldMarkExported(r) && (saved?.kind === 'saved' || saved?.kind === 'mismatch')) void sync.current?.flush(id, { exported: true });
  }

  const canExport = titleText.trim() !== '' || bodyText.trim() !== '';
  const headerRight = () => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="내보내기"
      accessibilityState={{ disabled: !canExport }}
      disabled={!canExport}
      onPress={() => void exportDraft()}
      style={({ pressed }) => [{ minHeight: layout.hit, minWidth: layout.hit, paddingHorizontal: space[2], justifyContent: 'center' }, pressed && { opacity: 0.7 }]}
    >
      <Txt v="L2" style={{ color: canExport ? color.ink : color.muted }}>
        내보내기
      </Txt>
    </Pressable>
  );

  if (!draft) {
    return (
      <Screen stacked>
        <Stack.Screen options={{ title: '' }} />
        {loadError ? (
          <Notice
            tone="error"
            message={loadError.message}
            action={
              loadError.notFound
                ? { label: '돌아가기', onPress: () => router.back() }
                : { label: '다시 불러오기', onPress: () => void load() }
            }
          />
        ) : (
          <View style={{ flexDirection: 'row', gap: space[2], alignItems: 'center' }}>
            <ActivityIndicator color={color.ink} />
            <Txt v="L1" style={{ color: color.text }}>
              불러오는 중이에요
            </Txt>
          </View>
        )}
      </Screen>
    );
  }

  const rounds = draft.questions.rounds.length;
  const hasBook = draft.bookId !== null;
  const included = materials?.included ?? 0;
  const ai = aiButton({ rounds, body: bodyText, aiIncluded: included, hasBook });
  const again = againArgs(draft.questions);
  const waitingText = waiting === 'ASK' ? '여백을 읽고 질문을 고르는 중이에요' : waiting === 'FOLLOW' ? '내 글을 읽고 질문을 고르는 중이에요' : null;
  const openQuestions = () => {
    Keyboard.dismiss();
    setSheet('questions');
  };
  const input = { ...type.E1.style, color: color.ink, padding: 0 } as const;

  return (
    <SafeAreaView edges={['bottom']} style={{ flex: 1, backgroundColor: color.paper }}>
      <Stack.Screen options={{ title: draft.bookTitle ?? '', headerRight }} />
      {/* KAV는 화면 y=0에서 시작한다(머리가 투명이라 위로 깔린다) — 오프셋 0이 키보드 틈 0이다(U-55 · U-61) */}
      <KeyboardAvoidingView behavior="padding" keyboardVerticalOffset={0} style={{ flex: 1 }}>
        <View style={{ paddingTop: headerHeight + space[3] }}>
          <QuestionBand
            question={focus}
            hint={bandHint({ rounds, aiIncluded: included, hasBook })}
            waiting={waitingText}
            onPress={rounds > 0 ? openQuestions : undefined}
          />
        </View>
        {notice ? (
          <View style={[readColumn, { paddingHorizontal: layout.gutter, paddingTop: space[3] }]}>
            <Notice message={notice.message} tone={notice.tone} action={notice.action} />
          </View>
        ) : null}
        <View key={editorKey} style={[readColumn, { flex: 1, paddingHorizontal: layout.gutter, paddingTop: space[3] }]}>
          <TextInput
            ref={titleInput}
            accessibilityLabel="글 제목"
            placeholder="제목"
            placeholderTextColor={color.muted}
            maxFontSizeMultiplier={2}
            maxLength={100}
            returnKeyType="next"
            submitBehavior="submit"
            defaultValue={initial.title}
            onChangeText={(t) => {
              titleRef.current = t;
              setTitleText(t);
              edit({ title: t });
            }}
            onSubmitEditing={() => bodyInput.current?.focus()}
            onFocus={() => setNotice(null)}
            style={[input, { minHeight: layout.hit }]}
          />
          <View style={{ height: line.thin, backgroundColor: color.line }} />
          <TextInput
            ref={bodyInput}
            accessibilityLabel="글 본문"
            placeholder="여기에 내 글을 써요"
            placeholderTextColor={color.muted}
            maxFontSizeMultiplier={2}
            maxLength={10000}
            multiline
            scrollEnabled
            textAlignVertical="top"
            defaultValue={initial.body}
            onChangeText={(t) => {
              bodyRef.current = t;
              setBodyText(t);
              edit({ body: t });
            }}
            onSelectionChange={(e) => {
              caret.current = e.nativeEvent.selection.end;
            }}
            onFocus={() => setNotice(null)}
            style={[input, { flex: 1, marginTop: space[3] }]}
          />
        </View>
        {/* 아래 막대 — KeyboardAvoidingView 안의 보통 View(스파이크 통과 꼴 · REQ-136 가드). 면(선 · 바탕)은 끝에서 끝, 단추 줄은 안쪽 단(REQ-104) */}
        <View style={{ backgroundColor: color.paper, borderTopWidth: line.thin, borderTopColor: color.line, paddingVertical: space[3] }}>
          <View
            style={[
              readColumn,
              {
                flexDirection: 'row',
                flexWrap: stacked ? 'wrap' : 'nowrap', // 큰 글자면 글자 단추 둘이 폭을 다 써 알약이 찌그러진다 — 알약을 아래 줄로
                alignItems: 'center',
                gap: space[2],
                paddingHorizontal: layout.gutter,
              },
            ]}
          >
            {keyboard ? <Button kind="text" label="키보드 내리기" onPress={() => Keyboard.dismiss()} /> : null}
            {rounds > 0 ? <Button kind="text" label="질문 보기" onPress={openQuestions} /> : null}
            <View style={stacked ? { flexBasis: '100%' } : { flex: 1 }}>
              <Button
                kind="ink"
                label={ai.label}
                disabled={ai.disabled}
                busy={waiting !== null}
                accessibilityHint={ai.step === 'FOLLOW' && ai.disabled && rounds < ROUNDS_MAX ? '내 생각을 한두 문장 쓰면 켜져요' : undefined}
                onPress={() => void runAi(ai.step, focus)}
              />
            </View>
          </View>
        </View>
      </KeyboardAvoidingView>

      <QuestionSheet
        open={sheet === 'questions'}
        onClose={() => setSheet(null)}
        log={draft.questions}
        focus={focus}
        remainingToday={draft.aiRemainingToday}
        materials={materials}
        busy={waiting !== null}
        onPick={pick}
        onAgain={() => again && void runAi(again.step, again.focus)}
      />
      <ConsentSheet
        open={sheet === 'consent'}
        busy={consentBusy}
        onAgree={() => void agree()}
        onLater={() => {
          pending.current = null;
          setSheet(null);
        }}
        onPrivacy={() => void Linking.openURL('https://booktimer.app/privacy')}
      />
    </SafeAreaView>
  );
}
