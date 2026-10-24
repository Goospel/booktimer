// 공부 탭(PR-S1 사양 §5.1~§5.5 — 피그마 R7 S-STUDY · S-STUDY-BOOK · S-STUDY-RUN · S-STUDY-409 · S-STUDY-EMPTY).
// 위 = 공부 막대(지금 책 · 오늘 공부 · [공부 시작]), 아래 = 공부 서재. 측정 중엔 홈 측정 중과 같은 한 화면이고 공부 서재를 숨긴다(결정 B ①).
// 하루 목표 · 연속 · 회당 시간 · 회독은 없다(사용자 결정). 공부 책에는 글쓰기 · 여백 진입을 두지 않는다(v1 §4.1). 진입 직후 덮는 것 0.
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, AppState, Pressable, View } from 'react-native';

import { reading, study } from '../../api/client';
import type { Dashboard, StudyBook } from '../../api/types';
import { Cover } from '../../components/Cover';
import { Badge, Button, Notice, Screen, Txt, useStacked } from '../../components/ui';
import { clockLabel, durationLabel, objectParticle } from '../../lib/format';
import { isStaleDay, nextMidnightOf, SESSION_CAP_MS } from '../../lib/goalProgress';
import { startMeasure, stopMeasure } from '../../lib/measureStart';
import { defaultStudyBook, removeStudyBook, studyMeta } from '../../lib/studyShelf';
import { studyToday } from '../../lib/studyTimer';
import { syncSurfaces } from '../../lib/surfaces';
import { color, layout, line, space } from '../../theme/tokens';

/** 알림 자리는 둘이고 상태는 하나다 — 시작 · 정지 실패는 막대 안, 빼기 실패는 공부 서재 단 맨 위(사양 §8-20). */
type NoticeState = { message: string; tone?: 'info' | 'error'; at: 'bar' | 'shelf' } | null;

export default function Study() {
  const [dash, setDash] = useState<Dashboard | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [busy, setBusy] = useState(false);
  const [pickedId, setPickedId] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const [loadedMidnight, setLoadedMidnight] = useState(0);
  const stacked = useStacked();

  // 대시보드 한 번으로 그린다(study 필드) — 위젯 · Live Activity도 같은 값으로 맞춘다(사양 §8-12)
  const load = useCallback(async () => {
    try {
      const d = await reading.dashboard();
      setDash(d);
      setLoadError(null);
      setNow(Date.now());
      setLoadedMidnight(nextMidnightOf(Date.now()));
      void syncSurfaces(d);
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => s === 'active' && void load());
    return () => sub.remove();
  }, [load]);

  // 포커스마다 다시 읽는다 — 공부 책 담기 · 홈 탭에서 돌아오면 옛 값이다(첫 마운트도 여기서 읽는다)
  useFocusEffect(
    useCallback(() => {
      void load();
    }, [load]),
  );

  const s = dash?.study;
  const measuring = !!s?.hasActiveSession && !!s.activeStartedAt;
  useEffect(() => {
    if (!measuring) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [measuring]);

  // 자정을 넘기면 다시 읽는다 — 끝난 몫이 전날 값이다. 측정 중엔 1초 틱이, 측정 전엔 자정 예약이 깨운다(홈과 같다).
  const staleDay = dash !== null && isStaleDay(loadedMidnight, now);
  useEffect(() => {
    if (staleDay) void load();
  }, [staleDay, load]);
  useEffect(() => {
    if (measuring || !dash) return;
    const id = setTimeout(() => void load(), nextMidnightOf(Date.now()) - Date.now() + 1000);
    return () => clearTimeout(id);
  }, [measuring, dash, load]);

  // Option+P(PR-P)가 그대로 부를 처리기 둘(사양 §5.1)
  async function start(bookId: number | null) {
    if (busy) return;
    setBusy(true);
    setNotice(null);
    const r = await startMeasure({ start: study.start, reload: load }, bookId);
    if (r.error) setNotice({ message: r.error, tone: 'error', at: 'bar' });
    setBusy(false);
  }

  async function stop() {
    if (busy) return;
    setBusy(true);
    setNotice(null);
    const r = await stopMeasure({ stop: study.stop, reload: load });
    if (r.error) setNotice({ message: r.error, tone: 'error', at: 'bar' });
    else if (r.info) setNotice({ message: r.info, at: 'bar' });
    setBusy(false);
  }

  function pick(id: number) {
    setNotice(null);
    setPickedId(id);
  }

  function confirmRemove(b: StudyBook) {
    Alert.alert(`『${b.title}』${objectParticle(b.title)} 공부 서재에서 뺄까요?`, '그 책으로 잰 공부 시간은 지워지지 않아요', [
      { text: '취소', style: 'cancel' },
      { text: '빼기', style: 'destructive', onPress: () => void remove(b.id) },
    ]);
  }

  async function remove(id: number) {
    setNotice(null);
    const r = await removeStudyBook({ remove: study.remove, reload: load }, id);
    if (r.error) setNotice({ message: r.error, tone: 'error', at: 'shelf' });
  }

  if (!dash || !s) {
    return (
      <Screen>
        <Txt v="D2">공부</Txt>
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

  const startedAt = measuring && s.activeStartedAt ? Date.parse(s.activeStartedAt) : null;
  const today = studyToday({ todaySeconds: s.todaySeconds, activeStartedAt: startedAt, now, nextMidnight: nextMidnightOf(now) });
  // 누르는 곳이 아니다 — 공부엔 목표가 없다(홈 목표 줄과 다르다 — 사양 §8-2)
  const todayLine = (
    <Txt v="Q2" style={{ color: color.text }}>
      오늘 공부 {durationLabel(today)}
    </Txt>
  );
  const band = (at: 'bar' | 'shelf' | 'any') =>
    notice && (at === 'any' || notice.at === at) ? <Notice message={notice.message} tone={notice.tone} /> : null;

  // ── 측정 중 — 홈 측정 중과 같은 위계. 큰 시계 = 이번 측정(결정 C ①), 오늘 합계는 아래 줄 ─────────
  if (measuring && startedAt !== null) {
    const elapsed = Math.min(now - startedAt, SESSION_CAP_MS) / 1000;
    return (
      <Screen>
        <Txt v="L2" style={{ color: color.muted }}>
          공부 중
        </Txt>
        {s.activeBook ? (
          <Txt v="H2" numberOfLines={2}>
            {s.activeBook.title}
          </Txt>
        ) : null}
        <Txt
          v="D1"
          numberOfLines={1}
          adjustsFontSizeToFit
          accessibilityLabel={`측정 중, ${durationLabel(elapsed)}`}
          style={{ fontVariant: ['tabular-nums'], marginVertical: space[4] }}
        >
          {clockLabel(elapsed)}
        </Txt>
        {todayLine}
        {band('any')}
        <Button kind="ink" label="측정 종료" busy={busy} onPress={() => void stop()} />
      </Screen>
    );
  }

  const toAdd = () => router.push('/add?shelf=study');
  const toScan = () => router.push('/add?shelf=study&scan=1');

  // ── 빈 공부 서재 — 「책 없이 시작」은 여기서만(사양 §8-6) ─────────────────────
  const book = defaultStudyBook(s.books, s.recentBookId, pickedId);
  if (!book) {
    return (
      <Screen scroll>
        <Txt v="D2">공부</Txt>
        {today > 0 ? todayLine : null}
        <View style={{ marginTop: space[12] - space[4] }}>
          <Txt v="Q1">아직 담은 공부 책이 없어요</Txt>
          <Txt v="L1" style={{ color: color.text, marginTop: space[2] }}>
            공부할 책을 담으면 여기서 책마다 공부 시간을 잴 수 있어요
          </Txt>
          <View style={{ marginTop: space[6], gap: space[3] }}>
            {band('any')}
            <Button kind="ink" label="제목으로 찾기" onPress={toAdd} />
            <Button kind="outline" label="바코드로 담기" onPress={toScan} />
          </View>
          <View style={{ height: layout.sectionGap }} />
          <Button kind="text" label="책 없이 시간만 재기" busy={busy} onPress={() => void start(null)} />
        </View>
      </Screen>
    );
  }

  // ── 측정 전 — 표제는 지금 책(홈 측정 전과 같은 꼴). 검정 알약은 [공부 시작] 하나 ───────────────
  return (
    <Screen scroll>
      <Txt v="D2">공부</Txt>
      <View style={{ flexDirection: 'row', gap: space[4], alignItems: 'flex-start' }}>
        <Cover title={book.title} coverUrl={book.coverUrl} size="card" />
        <View style={{ flex: 1, gap: space[1] }}>
          <Txt v="H2" numberOfLines={3}>
            {book.title}
          </Txt>
          {book.author ? (
            <Txt v="L1" style={{ color: color.text }}>
              {book.author}
            </Txt>
          ) : null}
        </View>
      </View>
      {todayLine}
      {band('bar')}
      <Button kind="ink" label="공부 시작" busy={busy} onPress={() => void start(book.id)} />
      <View style={{ marginTop: space[4], gap: space[3] }}>
        <View
          accessible
          accessibilityRole="header"
          accessibilityLabel={`공부 서재, ${s.books.length}권`}
          style={{ flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between', gap: space[2] }}
        >
          <Txt v="H1">공부 서재</Txt>
          <Txt v="L1" style={{ color: color.muted }}>
            {s.books.length}권
          </Txt>
        </View>
        {band('shelf')}
        <View style={{ flexDirection: stacked ? 'column' : 'row', gap: stacked ? space[3] : space[4] }}>
          <View style={stacked ? undefined : { flex: 1 }}>
            <Button kind="outline" label="제목으로 찾기" onPress={toAdd} />
          </View>
          <View style={stacked ? undefined : { flex: 1 }}>
            <Button kind="outline" label="바코드로 담기" onPress={toScan} />
          </View>
        </View>
        <View>
          {s.books.map((b) => (
            <StudyRow key={b.id} book={b} current={b.id === book.id} stacked={stacked} onPress={() => pick(b.id)} onRemove={() => confirmRemove(b)} />
          ))}
        </View>
      </View>
    </Screen>
  );
}

/** 공부 서재 행 — 서재 ShelfRow와 같은 꼴 + 「지금 책」 배지 · 길게 눌러 빼기(ponytail: 셋째 쓰임에서 components/로 합친다). */
function StudyRow(p: { book: StudyBook; current: boolean; stacked: boolean; onPress: () => void; onRemove: () => void }) {
  const { book } = p;
  const meta = studyMeta(book.totalSeconds);
  const label = [book.title, book.author, meta].filter(Boolean).join(', ');
  const badge = p.current ? <Badge label="지금 책" /> : null;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={p.current ? undefined : '지금 책으로 골라요'}
      accessibilityState={{ selected: p.current }}
      accessibilityActions={[{ name: 'remove', label: '서재에서 빼기' }]}
      onAccessibilityAction={(e) => e.nativeEvent.actionName === 'remove' && p.onRemove()}
      onLongPress={p.onRemove}
      onPress={p.onPress}
      style={({ pressed }) => [
        {
          flexDirection: 'row',
          alignItems: 'center',
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
        {p.stacked ? badge : null}
      </View>
      {/* 감싼다 — Badge의 alignSelf flex-start가 행의 alignItems center를 이겨 맨 위로 붙는다(확정 그림은 세로 가운데) */}
      {p.stacked || !badge ? null : <View>{badge}</View>}
    </Pressable>
  );
}
