// 쪽지 화면(책생글 「여백 + 블로그」 A1 — 설계 §5.1 · §5.5 · F1 사양 §4.4 · 그림 F-MARGIN-NOTE-*). 라우트 `margin/<책 id | none>?note=<id>`.
// 틀은 v1 글쓰기 화면 그대로: 머리 → 질문 띠(고정) → (알림) → 본문(옮긴 문장 칸 · C2 띠 · 내 생각) → 아래 막대. 바깥 ScrollView는 없다(1b 실측).
// 저장은 자동이다(1.5초 · 판 대조 · 기기 칸 `margin:` — J80) — [남기기]가 없다. 쪽지 행은 첫 저장 때 생긴다(J6). 그 전 글은 새 쪽지 칸에 있다.
// 입력은 비제어다(한글 조합 보호 — value를 넘기지 않는다). AI는 질문만 한다 — 질문은 띠와 시트에만 있고 생각 칸으로 옮기는 길이 없다(REQ-221).
// 들어올 때 키보드를 올리지 않는다 · 시트는 누른 뒤에만 뜬다(진입 직후 덮는 것 0 — C2 띠도 흐름 안).
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

import { ApiError, failText, library, margins, NetworkError, session, writing } from '../../api/client';
import type { MarginView, Question, Step } from '../../api/types';
import { ConsentSheet } from '../../components/ConsentSheet';
import { QuestionBand } from '../../components/QuestionBand';
import { QuestionSheet } from '../../components/QuestionSheet';
import { QuoteOnlyBand } from '../../components/QuoteOnlyBand';
import { Button, Notice, readColumn, Screen, Txt, useStacked } from '../../components/ui';
import { aiButton, ROUNDS_MAX, sendAiStep } from '../../lib/aiStep';
import {
  adoptNewNote,
  AUTOSAVE_MS,
  draftSync,
  newNoteKey,
  type NewNoteSlot,
  revisionOrThrow,
  type SaveOutcome,
  shouldDiscard,
  startNewNote,
  type SyncDoc,
} from '../../lib/draftSync';
import { bookSourceLine, buildNoteExport, shouldMarkExported } from '../../lib/exportText';
import { canSaveMargin, hasQuoteField, noteEditBody, noteHead, noteMaxLength, noteRoute, savedNotice } from '../../lib/marginSave';
import { againArgs, bandHint, waitingText } from '../../lib/questions';
import { quoteOnlyBand } from '../../lib/quoteOnlyBand';
import { color, layout, line, space, type } from '../../theme/tokens';

type NoticeState = { message: string; tone?: 'info' | 'error'; action?: { label: string; onPress: () => void } } | null;
type Sync = ReturnType<typeof draftSync>;
/** 머리 · 옮긴 문장 칸 · 내보내기 책 줄 — 새 쪽지는 첫 저장 전까지 서버 쪽지(note)가 없다. */
type Head = { bookTitle: string | null; bookLine: string | null; quoteSource: string | null; field: boolean };

const OFFLINE_SAVE = '인터넷에 연결되지 않았어요 — 쓴 쪽지는 이 기기에 남아 있고, 연결되면 저장해요';
const MISMATCH = '서버에 저장된 쪽지가 이 기기 것과 달라요 — 이 기기 것은 지우지 않았어요';
const SAVE_FAIL = '쪽지를 저장하지 못했어요 — 잠시 뒤 다시 해 주세요';
const nextTick = () => new Promise<void>((r) => setTimeout(r, 0));
/** 칸(draftSync)은 제목 · 본문 꼴이다 — 쪽지는 title = 옮긴 문장 · body = 생각으로 넣는다. */
const toDoc = (v: MarginView): SyncDoc => ({ id: v.id, title: v.quote ?? '', body: v.text, revision: v.revision });

export default function MarginNote() {
  const params = useLocalSearchParams<{ bookId: string; note?: string }>();
  const [route] = useState(() => noteRoute(params)); // 첫 저장 뒤에도 라우트를 바꾸지 않는다(다시 읽지 않게)
  const headerHeight = useHeaderHeight(); // 유리 머리(headerTransparent) — 상태바 포함 높이
  const stacked = useStacked();
  const [note, setNote] = useState<MarginView | null>(null); // 서버 쪽 사실(질문 기록 · 남은 몫 · 동의) — 글은 편집기가 쥔다
  const [head, setHead] = useState<Head | null>(null);
  const [materials, setMaterials] = useState<{ included: number; excluded: number } | null>(null);
  const [loadError, setLoadError] = useState<{ message: string; notFound: boolean } | null>(null);
  const [initial, setInitial] = useState({ quote: '', text: '' });
  const [maxText, setMaxText] = useState(noteMaxLength(''));
  const [editorKey, setEditorKey] = useState(0);
  const [quoteText, setQuoteText] = useState('');
  const [thoughtText, setThoughtText] = useState('');
  const [focus, setFocus] = useState<string | null>(null);
  const [waiting, setWaiting] = useState<Step | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [sheet, setSheet] = useState<'questions' | 'consent' | null>(null);
  const [consentBusy, setConsentBusy] = useState(false);
  const [keyboard, setKeyboard] = useState(false);

  const sync = useRef<Sync | null>(null);
  const userIdRef = useRef(0);
  const noteId = useRef<number | null>(route.noteId);
  const serverRef = useRef<MarginView | null>(null);
  const fieldRef = useRef(false);
  const loadedQuote = useRef<string | null>(null);
  const quoteRef = useRef('');
  const textRef = useRef('');
  const focusRef = useRef<string | null>(null);
  const roundsRef = useRef(0);
  const caret = useRef(0);
  const restoreCaret = useRef<number | null>(null);
  const quoteInput = useRef<TextInputInstance>(null);
  const textInput = useRef<TextInputInstance>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const offlineShown = useRef(false);
  const conflictOpen = useRef(false);
  const pending = useRef<{ step: Step; focus: string | null } | null>(null);
  const waitingRef = useRef(false); // 보내는 중에 떠나면 빈 쪽지여도 지우지 않는다 — 서버가 이미 몫을 깎았다
  const edited = useRef(false); // 이 방문에서 무엇을 썼는가 — 돌아간 화면의 「여백을 남겼어요」
  const localId = useRef<string | null>(null); // 새 쪽지 칸(첫 저장 전)
  const newSlotChain = useRef<Promise<unknown>>(Promise.resolve());
  const creating = useRef<Promise<SaveOutcome> | null>(null);

  function applyText(t: { quote: string; text: string }) {
    quoteRef.current = t.quote;
    textRef.current = t.text;
    setQuoteText(t.quote);
    setThoughtText(t.text);
    setInitial(t);
  }

  function takeServer(v: MarginView) {
    serverRef.current = v;
    loadedQuote.current = v.quote;
    roundsRef.current = v.questions.rounds.length;
    setNote(v);
    setHead((h) => (h ? { ...h, quoteSource: v.quoteSource } : h));
  }

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const userId = (await session.userId()) ?? 0;
      userIdRef.current = userId;
      const s = draftSync({
        kv: AsyncStorage,
        userId,
        prefix: 'margin',
        put: async (id, b) => {
          const v = await margins.edit(
            id,
            noteEditBody({ field: fieldRef.current, quote: b.title, text: b.body, revision: b.revision, loadedQuote: loadedQuote.current, exported: b.exported }),
          );
          serverRef.current = v;
          loadedQuote.current = v.quote;
          // 서버는 생각 앞뒤 공백을 걷는다(Story.edit strip) — 그 차이만이면 보낸 글을 그대로 둬 「서버와 달라요」를 띄우지 않는다
          return { ...toDoc(v), body: v.text === b.body.trim() ? b.body : v.text };
        },
      });
      sync.current = s;
      if (route.noteId !== null) {
        const id = route.noteId;
        const v = await margins.get(id).catch(async (e) => {
          if (e instanceof ApiError && e.status === 404) await s.drop(id); // 지워진 쪽지 — 기기 칸도 버린다
          throw e;
        });
        fieldRef.current = hasQuoteField(v);
        const slot = await s.open(toDoc(v));
        takeServer(v);
        setHead({ bookTitle: v.bookTitle, bookLine: null, quoteSource: v.quoteSource, field: fieldRef.current });
        setFocus(slot.focus);
        focusRef.current = slot.focus;
        setMaxText(noteMaxLength(v.text));
        applyText({ quote: slot.title, text: slot.body });
        if (v.bookId !== null) {
          // 재료 줄(「재료 N개 중 M개」)과 내보내기 책 줄 — 못 읽어도 쪽지는 쓴다
          void library.materials(v.bookId).then(
            (m) => {
              setMaterials({ included: m.aiIncluded, excluded: m.aiExcluded });
              setHead((h) => (h ? { ...h, bookLine: bookSourceLine(m.book) } : h));
            },
            () => undefined,
          );
        }
        if (slot.dirty) void save(); // 저장 안 된 기기 글이 서버보다 앞이다 — 바로 한 번 보낸다
      } else if (route.bookId !== null) {
        const m = await library.materials(route.bookId);
        fieldRef.current = true;
        setMaterials({ included: m.aiIncluded, excluded: m.aiExcluded });
        setHead({ bookTitle: m.book.title, bookLine: bookSourceLine(m.book), quoteSource: null, field: true });
      } else {
        fieldRef.current = false; // 책 없는 새 쪽지 — 옮긴 문장 칸이 없다(J84)
        setHead({ bookTitle: null, bookLine: null, quoteSource: null, field: false });
      }
    } catch (e) {
      setLoadError({ message: e instanceof Error ? e.message : String(e), notFound: e instanceof ApiError && e.status === 404 });
    }
  }, [route]); // save는 ref만 읽는다

  useEffect(() => {
    void load();
  }, [load]);

  // 키보드가 있을 때만 「키보드 내리기」
  useEffect(() => {
    const show = Keyboard.addListener('keyboardWillShow', () => setKeyboard(true));
    const hide = Keyboard.addListener('keyboardWillHide', () => setKeyboard(false));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  // 앱이 뒤로 가거나 돌아오면 저장 · 떠날 때 남은 저장을 보내고(기다리지 않음) 빈 쪽지는 지운다(새 쪽지면 칸만 버린다 — F1 §4.4-7)
  useEffect(() => {
    const sub = AppState.addEventListener('change', (st) => (st === 'background' || st === 'active') && void save());
    return () => {
      sub.remove();
      if (timer.current) clearTimeout(timer.current);
      const s = sync.current;
      if (!s) return;
      const empty = shouldDiscard({ title: quoteRef.current, body: textRef.current, rounds: roundsRef.current }) && !waitingRef.current;
      const id = noteId.current;
      if (id === null) {
        if (empty) void dropNewSlot();
        else void createFirst(); // 실패하면 새 쪽지 칸이 남아 다음 실행에 보낸다(J100)
      } else if (empty) {
        void margins.delete(id).then(() => s.drop(id), () => undefined);
      } else {
        void s.flush(id);
      }
      if (!empty && edited.current) savedNotice.mark();
    };
  }, []);

  // 「그쪽 불러오기」로 다시 마운트한 뒤 쓰던 자리로
  useEffect(() => {
    const c = restoreCaret.current;
    if (c === null) return;
    restoreCaret.current = null;
    requestAnimationFrame(() => {
      textInput.current?.focus();
      textInput.current?.setSelection(c, c);
    });
  }, [editorKey]);

  // ── 새 쪽지 칸(J80 · J99 ③) — 첫 글자부터 기기에 둔다. 칸 쓰기는 한 줄로 세운다(칸이 둘 생기지 않게) ──
  const newSlot = (): NewNoteSlot & { focus: string | null } => ({
    bookId: route.bookId,
    quote: fieldRef.current && quoteRef.current.trim() !== '' ? quoteRef.current : null,
    text: textRef.current,
    dirty: true,
    focus: focusRef.current,
  });
  function queueNewSlot() {
    newSlotChain.current = newSlotChain.current
      .then(async () => {
        if (noteId.current !== null) return;
        if (localId.current === null) localId.current = await startNewNote(AsyncStorage, userIdRef.current, newSlot());
        else await AsyncStorage.setItem(newNoteKey(userIdRef.current, localId.current), JSON.stringify(newSlot()));
      })
      .catch(() => undefined);
  }
  async function dropNewSlot() {
    await newSlotChain.current;
    if (localId.current !== null) await AsyncStorage.removeItem(newNoteKey(userIdRef.current, localId.current));
    localId.current = null;
  }

  /** 첫 저장 = 쪽지 만들기(J6). 한 번에 하나 — 만드는 중에 또 부르면 같은 결과를 기다린다. */
  function createFirst(): Promise<SaveOutcome> {
    if (creating.current) return creating.current;
    const run = (async (): Promise<SaveOutcome> => {
      const sent = { quote: quoteRef.current, text: textRef.current };
      let v: MarginView;
      try {
        v = await margins.add({ bookId: route.bookId, quote: newSlot().quote, text: sent.text });
      } catch (e) {
        if (e instanceof NetworkError) return { kind: 'offline' };
        return { kind: 'error', message: failText(e, SAVE_FAIL), cause: e };
      }
      await newSlotChain.current;
      if (localId.current !== null) await adoptNewNote(AsyncStorage, userIdRef.current, localId.current, v.id);
      localId.current = null;
      noteId.current = v.id;
      const s = sync.current!;
      await s.takeTheirs(v.id, toDoc(v)); // 칸 = 서버 글(새 쪽지 칸의 띠 질문은 남는다)
      takeServer(v);
      if (quoteRef.current !== sent.quote || textRef.current !== sent.text) {
        // 만드는 동안 더 쓴 것 — 그 쪽지 칸에 얹고 다음 저장에 보낸다
        await s.edit(v.id, { title: quoteRef.current, body: textRef.current });
        scheduleSave();
      }
      return { kind: 'saved', revision: v.revision };
    })();
    creating.current = run;
    void run.finally(() => {
      creating.current = null;
    });
    return run;
  }

  function onOutcome(r: SaveOutcome) {
    if (r.kind === 'saved') offlineShown.current = false;
    else if (r.kind === 'mismatch') setNotice({ message: MISMATCH });
    else if (r.kind === 'offline' && !offlineShown.current) {
      offlineShown.current = true;
      setNotice({ message: OFFLINE_SAVE });
    } else if (r.kind === 'conflict') askConflict();
    else if (r.kind === 'gone') setNotice({ message: '쪽지를 찾을 수 없어요', tone: 'error', action: { label: '돌아가기', onPress: () => router.back() } });
    else if (r.kind === 'error') setNotice({ message: r.message, tone: 'error' });
  }

  async function save(opts?: { exported?: boolean }): Promise<SaveOutcome | null> {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    const s = sync.current;
    if (!s) return null;
    // 빈 쪽지는 보내지 않는다(서버 400) — 떠날 때 지운다. 받은 질문이 있으면 원가가 든 쪽지라 빈 채로도 남는다
    if (shouldDiscard({ title: quoteRef.current, body: textRef.current, rounds: roundsRef.current })) return null;
    const id = noteId.current;
    const r = id === null ? await createFirst() : await s.flush(id, opts);
    onOutcome(r);
    return r;
  }

  function scheduleSave() {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => void save(), AUTOSAVE_MS);
  }

  function edit(patch: { quote: string } | { text: string }) {
    edited.current = true;
    const id = noteId.current;
    if (id === null) queueNewSlot();
    else void sync.current?.edit(id, 'quote' in patch ? { title: patch.quote } : { body: patch.text }); // 기기 칸에는 즉시
    scheduleSave(); // 서버에는 1.5초 뒤
  }

  function askConflict() {
    if (conflictOpen.current) return;
    conflictOpen.current = true;
    Keyboard.dismiss();
    Alert.alert('다른 기기에서 이 쪽지가 바뀌었어요', '한쪽만 남길 수 있어요 — 남기지 않은 쪽 내용은 사라져요', [
      { text: '그쪽 불러오기', onPress: () => void resolveConflict('theirs') },
      { text: '이 기기 것 유지', isPreferred: true, onPress: () => void resolveConflict('mine') },
    ]);
  }

  async function resolveConflict(which: 'theirs' | 'mine') {
    conflictOpen.current = false;
    const s = sync.current;
    const id = noteId.current;
    if (!s || id === null) return;
    try {
      const v = await margins.get(id);
      takeServer(v);
      if (which === 'mine') {
        onOutcome(await s.keepMine(id, toDoc(v)));
        return;
      }
      const slot = await s.takeTheirs(id, toDoc(v));
      applyText({ quote: slot.title, text: slot.body });
      restoreCaret.current = Math.min(caret.current, slot.body.length);
      setEditorKey((k) => k + 1);
    } catch (e) {
      setNotice({ message: failText(e, '쪽지를 불러오지 못했어요 — 잠시 뒤 다시 해 주세요'), tone: 'error' });
    }
  }

  async function runAi(step: Step, f: string | null, consented = false) {
    if (waiting) return;
    let v = serverRef.current;
    if (!v) {
      // 새 쪽지 — 먼저 만들어야 동의 · 남은 몫을 안다(저장 실패는 save가 알린다)
      await save();
      v = serverRef.current;
      if (!v) return;
    }
    if (!v.aiConsent && !consented) {
      pending.current = { step, focus: f };
      Keyboard.dismiss();
      setSheet('consent');
      return;
    }
    const id = v.id;
    setNotice(null);
    setSheet(null);
    setWaiting(step);
    waitingRef.current = true;
    let gone = false;
    const r = await sendAiStep({
      blur: () => {
        quoteInput.current?.blur();
        textInput.current?.blur();
      },
      flush: async () => {
        const o = await save();
        if (o?.kind === 'gone') gone = true;
        return revisionOrThrow(o);
      },
      post: (revision) => margins.ai(id, { step, revision, focus: step === 'ASK' ? null : f }),
      reload: () => margins.get(id),
      roundsBefore: roundsRef.current,
    });
    setWaiting(null);
    waitingRef.current = false;
    if (gone) return; // save()가 띄운 「쪽지를 찾을 수 없어요 + 돌아가기」를 남긴다
    if (r.kind === 'done') {
      roundsRef.current = r.questions.rounds.length;
      const cur = serverRef.current;
      if (cur) {
        const next = { ...cur, aiConsent: true, questions: r.questions, aiUpdatedAt: r.aiUpdatedAt, aiRemainingToday: r.aiRemainingToday };
        serverRef.current = next;
        setNote(next);
      }
      setSheet('questions'); // 누른 뒤의 결과라 진입 직후 덮기가 아니다
    } else if (r.kind === 'consent') {
      setConsent(false);
      pending.current = { step, focus: f };
      setSheet('consent');
    } else if (r.kind === 'retry') {
      setNotice({ message: r.message, tone: 'error', action: { label: '다시 시도', onPress: () => void runAi(step, f, consented) } });
    } else {
      setNotice({ message: r.message, tone: 'error' });
    }
  }

  function setConsent(aiConsent: boolean) {
    const cur = serverRef.current;
    if (!cur) return;
    serverRef.current = { ...cur, aiConsent };
    setNote(serverRef.current);
  }

  async function agree() {
    setConsentBusy(true);
    try {
      const c = await writing.consent(true);
      setConsent(c.aiConsent);
      setSheet(null);
      const next = pending.current;
      pending.current = null;
      if (c.aiConsent && next) void runAi(next.step, next.focus, true); // 누른 단계를 이어서 — 다시 누르게 하지 않는다
    } catch (e) {
      setSheet(null);
      // 시행일 전(11/23)이면 503 — 서버 문구가 날짜를 낸다
      if (e instanceof ApiError && e.status === 503) setNotice({ message: e.message });
      else setNotice({ message: failText(e, '동의를 저장하지 못했어요 — 잠시 뒤 다시 눌러 주세요'), tone: 'error' });
    }
    setConsentBusy(false);
  }

  function pick(q: Question) {
    setFocus(q.text);
    focusRef.current = q.text;
    const id = noteId.current;
    if (id !== null) void sync.current?.edit(id, { focus: q.text }); // 띠의 질문은 기기 칸에만 — 생각 칸은 그대로(REQ-221)
    setSheet(null);
    // 시트가 내려간 뒤 생각 칸 끝에 커서
    setTimeout(() => {
      const n = textRef.current.length;
      textInput.current?.focus();
      textInput.current?.setSelection(n, n);
    }, 350);
  }

  async function exportNote() {
    Keyboard.dismiss();
    await nextTick(); // 조합 중이던 마지막 음절 확정
    const saved = await save();
    // 책 줄 = 서버 스냅샷(책을 지운 쪽지 포함) — 아직 저장 전이면 책 쪽지는 책 줄을 만든다
    const source = serverRef.current?.quoteSource ?? head?.quoteSource ?? head?.bookLine ?? null;
    const r = await Share.share({ message: buildNoteExport({ quote: quoteRef.current, quoteSource: source, text: textRef.current }) });
    // ponytail: 내보낸 표시는 지표뿐이라 실패해도 조용히 넘긴다 — onOutcome을 거치지 않는다
    const id = noteId.current;
    if (shouldMarkExported(r) && id !== null && (saved?.kind === 'saved' || saved?.kind === 'mismatch')) void sync.current?.flush(id, { exported: true });
  }

  const canExport = canSaveMargin({ quote: quoteText, thought: thoughtText });
  const headerRight = () => (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel="내보내기"
      accessibilityState={{ disabled: !canExport }}
      disabled={!canExport}
      onPress={() => void exportNote()}
      style={({ pressed }) => [{ minHeight: layout.hit, minWidth: layout.hit, paddingHorizontal: space[2], justifyContent: 'center' }, pressed && { opacity: 0.7 }]}
    >
      <Txt v="L2" style={{ color: canExport ? color.ink : color.muted }}>
        내보내기
      </Txt>
    </Pressable>
  );

  if (!head) {
    return (
      <Screen stacked>
        <Stack.Screen options={{ title: '' }} />
        {loadError ? (
          <Notice
            tone="error"
            message={loadError.notFound && route.noteId !== null ? '쪽지를 찾을 수 없어요' : loadError.message}
            action={loadError.notFound ? { label: '돌아가기', onPress: () => router.back() } : { label: '다시 불러오기', onPress: () => void load() }}
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

  const rounds = note?.questions.rounds.length ?? 0;
  const aiInput = { rounds, quote: quoteText, thought: thoughtText, boxes: 0 }; // 건 블로그 문장 박스는 B1부터
  const ai = aiButton(aiInput);
  const again = note ? againArgs(note.questions) : null;
  const { title, placeholder } = noteHead({ bookTitle: head.bookTitle, field: head.field });
  const openQuestions = () => {
    Keyboard.dismiss();
    setSheet('questions');
  };
  const input = { ...type.E1.style, color: color.ink, padding: 0 } as const;

  return (
    <SafeAreaView edges={['bottom']} style={{ flex: 1, backgroundColor: color.paper }}>
      <Stack.Screen options={{ title, headerRight }} />
      {/* KAV는 화면 y=0에서 시작한다(머리가 투명이라 위로 깔린다) — 오프셋 0이 키보드 틈 0이다(U-55 · U-61) */}
      <KeyboardAvoidingView behavior="padding" keyboardVerticalOffset={0} style={{ flex: 1 }}>
        <View style={{ paddingTop: headerHeight + space[3] }}>
          <QuestionBand question={focus} hint={bandHint(aiInput)} waiting={waitingText(waiting)} onPress={rounds > 0 ? openQuestions : undefined} />
        </View>
        {notice ? (
          <View style={[readColumn, { paddingHorizontal: layout.gutter, paddingTop: space[3] }]}>
            <Notice message={notice.message} tone={notice.tone} action={notice.action} />
          </View>
        ) : null}
        <View key={editorKey} style={[readColumn, { flex: 1, paddingHorizontal: layout.gutter, paddingTop: space[3], gap: space[3] }]}>
          {head.field ? (
            <View style={{ gap: space[1] }}>
              <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: space[2] }}>
                <Txt v="L1" style={{ color: color.text, flex: 1 }} accessible={false} importantForAccessibility="no">
                  책에서 옮긴 문장
                </Txt>
                <Txt v="T2" style={{ color: color.muted, fontVariant: ['tabular-nums'] }}>
                  {`${quoteText.length}/200`}
                </Txt>
              </View>
              {/* 왼쪽 선 인용 모양 · 한 줄 44 · 최대 3줄(96) — 넘으면 칸이 스스로 스크롤(F1 §7-9) */}
              <TextInput
                ref={quoteInput}
                accessibilityLabel="책에서 옮긴 문장"
                placeholder="밑줄 그은 문장을 옮겨 보세요"
                placeholderTextColor={color.muted}
                maxFontSizeMultiplier={2}
                maxLength={200}
                multiline
                scrollEnabled
                textAlignVertical="top"
                defaultValue={initial.quote}
                onChangeText={(t) => {
                  quoteRef.current = t;
                  setQuoteText(t);
                  edit({ quote: t });
                }}
                onFocus={() => setNotice(null)}
                style={[input, { minHeight: layout.hit, maxHeight: 96, paddingVertical: 9, paddingLeft: space[3], borderLeftWidth: line.bold, borderLeftColor: color.text }]}
              />
              {head.bookTitle === null && head.quoteSource ? (
                <Txt v="T2" style={{ color: color.muted }}>
                  {head.quoteSource}
                </Txt>
              ) : null}
              {quoteText.trim() === '' ? (
                <Txt v="L1" style={{ color: color.muted }}>
                  카메라로 옮길 땐 밑줄 친 문장만 틀 안에 들어오게 가까이 비춰 주세요
                </Txt>
              ) : null}
            </View>
          ) : null}
          {quoteOnlyBand(aiInput) ? <QuoteOnlyBand busy={waiting !== null} onPress={() => void runAi('ASK', null)} /> : null}
          <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: space[2] }}>
            <Txt v="L1" style={{ color: color.text, flex: 1 }} accessible={false} importantForAccessibility="no">
              내 생각
            </Txt>
            <Txt v="T2" style={{ color: color.muted }}>
              여백은 나만 봐요
            </Txt>
          </View>
          <TextInput
            ref={textInput}
            accessibilityLabel="내 생각"
            accessibilityHint="여백은 나만 봐요"
            placeholder={placeholder}
            placeholderTextColor={color.muted}
            maxFontSizeMultiplier={2}
            maxLength={maxText}
            multiline
            scrollEnabled
            textAlignVertical="top"
            defaultValue={initial.text}
            onChangeText={(t) => {
              textRef.current = t;
              setThoughtText(t);
              edit({ text: t });
            }}
            onSelectionChange={(e) => {
              caret.current = e.nativeEvent.selection.end;
            }}
            onFocus={() => setNotice(null)}
            style={[input, { flex: 1 }]}
          />
        </View>
        {/* 아래 막대 — KeyboardAvoidingView 안의 보통 View(REQ-136 가드). 면(선 · 바탕)은 끝에서 끝, 단추 줄은 안쪽 단(REQ-104) */}
        <View style={{ backgroundColor: color.paper, borderTopWidth: line.thin, borderTopColor: color.line, paddingVertical: space[3], marginTop: space[3] }}>
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

      {note ? (
        <QuestionSheet
          open={sheet === 'questions'}
          onClose={() => setSheet(null)}
          log={note.questions}
          focus={focus}
          remainingToday={note.aiRemainingToday}
          materials={materials}
          busy={waiting !== null}
          onPick={pick}
          onAgain={() => again && void runAi(again.step, again.focus)}
        />
      ) : null}
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
