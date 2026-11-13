// 서재(PR-G 사양 §5.1 · §5.2 — 피그마 R7 S-LIB · S-LIB-EMPTY). 독서 서재 책만 세 단(읽는 중 → 다 읽음 → 읽고 싶어요).
// 책이 있는 서재의 주 동작은 「책 열기」라 검정 알약이 없다 — 빈 서재에서만 「제목으로 찾기」가 검정(§8-4).
// 맨 아래 「책 없는 여백」(책생글 A1 — J75 · C9 · F1 §4.2-4) — 0개면 단째 숨김. 빈 서재에도 단다(새 계정이 책 없이 쓴 쪽지 — U-148).
// 맨 위(「서재」 바로 아래) 하루 목표 줄 · 목표 시트(책생글 A2 — J66 · F1 §4.2-2 · §4.2-5) — 빈 서재에도 둔다(D6). 시트는 줄을 눌러야 열린다.
// 목표 · 연속은 측정 가게를 구독해 보이는 대시보드로 그린다(새 요청 0 — 홈 박스와 같은 값).
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useState, useSyncExternalStore } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { library, margins, reading } from '../../api/client';
import type { MarginSummary, MyBookSummary } from '../../api/types';
import { Cover } from '../../components/Cover';
import { confirmRemoveNote, NoteRow } from '../../components/NoteRow';
import { Sheet } from '../../components/Sheet';
import { Button, Notice, Row, Screen, Txt, useStacked } from '../../components/ui';
import { goalChoices, libraryGoalLine, progressOf } from '../../lib/goalProgress';
import { booklessSection, noteHref, pendingLeave, savedNotice } from '../../lib/marginSave';
import { confirmedOf, loadDashboard, measureStore } from '../../lib/measureSync';
import { bookMeta, shelfSections } from '../../lib/shelf';
import { effectiveDashboard } from '../../lib/visibleDashboard';
import { color, layout, line, space } from '../../theme/tokens';

export default function Library() {
  const [books, setBooks] = useState<MyBookSummary[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [bookless, setBookless] = useState<MarginSummary[]>([]);
  const stacked = useStacked();
  const snap = useSyncExternalStore(measureStore.subscribe, measureStore.get);
  const now = Date.now(); // shortcut: 보이는 순간의 날로 굴린 값 — 서재에 머문 채 자정을 넘기면 다음 렌더(포커스 · 가게 변화)까지 앞날 글이다
  const dash = snap?.base ? effectiveDashboard(snap.base, snap.ledger, now, confirmedOf(snap)).d : null;
  const [goalOpen, setGoalOpen] = useState(false);
  const [goalError, setGoalError] = useState<string | null>(null);

  const load = useCallback(async () => {
    // 책 없는 쪽지는 따로 읽는다 — 실패해도 서재가 막히지 않는다(못 읽으면 단을 숨긴다). 방금 떠난 쪽지의 저장은 상한까지 기다린다
    void pendingLeave.wait().then(() => margins.bookless()).then(setBookless, () => setBookless([]));
    try {
      const r = await library.shelf();
      setBooks(r.books);
      setLoadError(null);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  // 책 화면에서 상태를 바꾸고 돌아오면 단이 바뀌어야 한다
  useFocusEffect(
    useCallback(() => {
      savedNotice.take(); // 서재에서 연 쪽지의 「여백을 남겼어요」 — 소비만 한다(알림은 홈 · 책 — F1 §4.4-7). 안 하면 나중에 엉뚱한 화면에 뜬다
      setGoalError(null); // 목표 저장 실패 띠는 탭을 다시 열면 걷는다
      void load();
    }, [load]),
  );

  const removeBookless = (id: number) => setBookless((l) => l.filter((x) => x.id !== id));
  const toAdd = () => router.push('/add');
  const toScan = () => router.push('/add?scan=1');

  async function chooseGoal(seconds: number) {
    setGoalOpen(false);
    try {
      await reading.setGoal(seconds);
    } catch (e) {
      setGoalError(e instanceof Error ? e.message : String(e));
    }
    await loadDashboard().catch(() => undefined); // 가게가 바뀌어 홈 박스 작은 글도 곧바로 따라온다
  }

  const line = dash ? libraryGoalLine({ progress: progressOf(dash, now), goal: dash.todayGoalSeconds, measuring: !!dash.hasActiveSession && !!dash.activeStartedAt }) : null;
  // 줄 · 시트는 기준 값(가게)이 있을 때만 — 옛 홈 목표 줄 · 시트 그대로 자리만 서재(F1 §4.2-2 · §4.2-5)
  const goalLine =
    line === null ? null : (
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${line}, 바꾸기`} // 보이는 단서를 이름에도 — 음성 제어가 「바꾸기」로 찾는다
        accessibilityHint="하루 목표를 바꿔요"
        onPress={() => setGoalOpen(true)}
        style={({ pressed }) => [
          { minHeight: layout.hit, justifyContent: 'center', gap: stacked ? space[1] : space[3] },
          !stacked && { flexDirection: 'row', alignItems: 'center' },
          pressed && { backgroundColor: color.pressed },
        ]}
      >
        {/* 줄이 글처럼만 보여 목표 바꾸는 곳을 못 찾았다(2026-10-25 실기기) — 오른쪽 「바꾸기 ›」 단서. 큰 글자면 아래로 쌓는다 */}
        <Txt v="Q2" style={[{ color: color.text }, !stacked && { flex: 1 }]}>
          {line}
        </Txt>
        <Txt v="L1" style={{ color: color.muted }} accessible={false}>
          바꾸기 ›
        </Txt>
      </Pressable>
    );
  const goalBand = goalError ? <Notice tone="error" message={goalError} /> : null;
  const goalSheet = dash ? (
    <Sheet open={goalOpen} onClose={() => setGoalOpen(false)}>
      <Txt v="H2">하루 목표</Txt>
      <Txt v="L1" style={{ color: color.text }}>
        오늘부터 바뀌어요 — 오늘 채웠는지도 새 목표로 봐요
      </Txt>
      <View>
        {goalChoices(dash.todayGoalSeconds).map((c) => (
          <Row key={c.seconds} title={c.title} meta={c.meta} selected={c.selected} onPress={() => void chooseGoal(c.seconds)} />
        ))}
      </View>
    </Sheet>
  ) : null;

  if (!books) {
    return (
      <Screen>
        <Txt v="D2">서재</Txt>
        {loadError ? (
          <Notice tone="error" message={loadError} action={{ label: '다시 불러오기', onPress: () => void load() }} />
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

  if (books.length === 0) {
    return (
      <Screen scroll>
        <Txt v="D2">서재</Txt>
        {goalLine}
        {goalBand}
        <View style={{ marginTop: space[12] - space[4] }}>
          <Txt v="Q1">아직 담은 책이 없어요</Txt>
          <Txt v="L1" style={{ color: color.text, marginTop: space[2] }}>
            제목으로 찾거나, 책 뒤표지의 바코드를 비춰 담을 수 있어요
          </Txt>
          <View style={{ marginTop: space[6], gap: space[3] }}>
            <Button kind="ink" label="제목으로 찾기" onPress={toAdd} />
            <Button kind="outline" label="바코드로 담기" onPress={toScan} />
          </View>
        </View>
        <Bookless list={bookless} onRemoved={removeBookless} />
        {goalSheet}
      </Screen>
    );
  }

  return (
    <Screen scroll>
      <Txt v="D2">서재</Txt>
      {goalLine}
      {goalBand}
      <View style={{ flexDirection: stacked ? 'column' : 'row', gap: stacked ? space[3] : space[4] }}>
        <View style={stacked ? undefined : { flex: 1 }}>
          <Button kind="outline" label="제목으로 찾기" onPress={toAdd} />
        </View>
        <View style={stacked ? undefined : { flex: 1 }}>
          <Button kind="outline" label="바코드로 담기" onPress={toScan} />
        </View>
      </View>
      <View style={{ marginTop: space[4], gap: layout.sectionGap }}>
        {shelfSections(books).map((s) => (
          <View key={s.status} style={{ gap: space[3] }}>
            <View
              accessible
              accessibilityRole="header"
              accessibilityLabel={`${s.title}, ${s.books.length}권`}
              style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
            >
              <Txt v="H1">{s.title}</Txt>
              <Txt v="L1" style={{ color: color.muted }}>
                {s.books.length}권
              </Txt>
            </View>
            {s.books.length === 0 ? (
              <Txt v="L1" style={{ color: color.text }}>
                {s.empty}
              </Txt>
            ) : (
              <View>
                {s.books.map((b) => (
                  <ShelfRow key={b.id} book={b} />
                ))}
              </View>
            )}
          </View>
        ))}
      </View>
      <Bookless list={bookless} onRemoved={removeBookless} />
      {goalSheet}
    </Screen>
  );
}

/** 「책 없는 여백」 단 — 쪽지 줄은 책 화면과 같은 모양(길게 누르거나 VoiceOver 「지우기」로 지운다) · 옮긴 문장이 있으면 그 아래 출처 줄(J84). */
function Bookless({ list, onRemoved }: { list: MarginSummary[]; onRemoved: (id: number) => void }) {
  const [error, setError] = useState<string | null>(null);
  useFocusEffect(useCallback(() => setError(null), [])); // 지우기 실패 알림은 탭을 다시 열면 걷는다
  const section = booklessSection(list);
  if (!section) return null;
  return (
    <View style={{ marginTop: layout.sectionGap - space[4], gap: space[3] }}>{/* 화면 틀 사이 16 + 16 = 단 사이 32 */}
      <View
        accessible
        accessibilityRole="header"
        accessibilityLabel={`책 없는 여백, ${section.count}`}
        style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
      >
        <Txt v="H1">책 없는 여백</Txt>
        <Txt v="L1" style={{ color: color.muted }}>
          {section.count}
        </Txt>
      </View>
      {error ? <Notice tone="error" message={error} /> : null}
      <View>
        {list.map((m) => (
          <NoteRow
            key={m.id}
            m={m}
            source
            onPress={() => router.push(noteHref(null, m.id))}
            onDelete={() => confirmRemoveNote(m.id, {
                  removed: () => {
                    setError(null);
                    onRemoved(m.id);
                  },
                  failed: setError,
                })}
          />
        ))}
      </View>
    </View>
  );
}

/** 서재 행 — 표지가 있어 Row가 아니다. 행 하나 = 접근성 요소 하나(REQ-122). */
function ShelfRow({ book }: { book: MyBookSummary }) {
  const meta = bookMeta(book);
  const label = [book.title, book.author, meta].filter(Boolean).join(', ');
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint="책 화면을 열어요"
      onPress={() => router.push(`/book/${book.id}`)}
      style={({ pressed }) => [
        {
          flexDirection: 'row',
          gap: space[3],
          paddingVertical: space[3],
          borderBottomWidth: line.thin,
          borderBottomColor: color.line,
        },
        pressed && { backgroundColor: color.pressed },
      ]}
    >
      <Cover title={book.title} coverUrl={book.coverUrl} size="row" />
      <View style={{ flex: 1, gap: 2 }}>
        <Txt v="H3" numberOfLines={2}>
          {book.title}
        </Txt>
        {book.author ? (
          <Txt v="L1" style={{ color: color.text }}>
            {book.author}
          </Txt>
        ) : null}
        {meta ? (
          <Txt v="T2" style={{ color: color.muted }}>
            {meta}
          </Txt>
        ) : null}
      </View>
    </Pressable>
  );
}
