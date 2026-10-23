// 홈(시각 보충 §1.4 · 범위 보충 §3.4). 세 모양 — 새 계정 빈 홈 · 측정 전(홈-안A/B) · 측정 중(낮 팔레트 — REQ-107).
// 측정 전의 표제는 「읽는 책」이고 시계가 아니다(REQ-61). 진입 직후 화면을 덮는 것은 0이다 — 시트는 목표 줄을 눌러야 열린다.
// 밀린 시간(부채)은 읽지도 그리지도 않는다(REQ-91).
import { router } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, Image, Pressable, View } from 'react-native';

import { reading } from '../../api/client';
import type { BookOption, Dashboard } from '../../api/types';
import { Sheet } from '../../components/Sheet';
import { Button, Notice, Row, Screen, Txt } from '../../components/ui';
import { clockLabel, dateLabel, durationLabel } from '../../lib/format';
import { goalProgress, isStaleDay, nextMidnightOf, SESSION_CAP_MS } from '../../lib/goalProgress';
import { HOME_VARIANT, homeButtons } from '../../lib/homeVariant';
import { startReading } from '../../lib/measureStart';
import { type DraftRow, recentDrafts } from '../../lib/recentDrafts';
import { syncSurfaces } from '../../lib/surfaces';
import { coverColor, initialOf } from '../../theme/cover';
import { color, cover, layout, space } from '../../theme/tokens';

const GOAL_CHOICES_MIN = [10, 20, 30, 45, 60, 90, 120]; // 0은 내놓지 않는다(범위 보충 §3.4)

type NoticeState = { message: string; tone?: 'info' | 'error' } | null;

export default function Home() {
  const [dash, setDash] = useState<Dashboard | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<NoticeState>(null);
  const [busy, setBusy] = useState(false);
  const [goalOpen, setGoalOpen] = useState(false);
  const [pickedId, setPickedId] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const [loadedMidnight, setLoadedMidnight] = useState(0);
  const [drafts, setDrafts] = useState<DraftRow[] | null>(null);

  const load = useCallback(async () => {
    // 글 목록은 따로 읽는다 — 실패해도 홈이 불러오기 오류로 막히지 않는다(못 읽으면 빈 상태 문구도 띄우지 않는다).
    void reading.drafts().then((list) => setDrafts(recentDrafts(list)), () => undefined);
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
    void load();
    const sub = AppState.addEventListener('change', (s) => s === 'active' && void load());
    return () => sub.remove();
  }, [load]);

  const measuring = !!dash?.hasActiveSession && !!dash.activeStartedAt;
  useEffect(() => {
    if (!measuring) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [measuring]);

  // 자정을 넘기면 다시 읽는다 — 끝난 몫 · 연속이 전날 값이다. 측정 중엔 1초 틱이, 측정 전엔 자정 예약이 깨운다.
  const staleDay = dash !== null && isStaleDay(loadedMidnight, now);
  useEffect(() => {
    if (staleDay) void load();
  }, [staleDay, load]);
  useEffect(() => {
    if (measuring || !dash) return;
    const id = setTimeout(() => void load(), nextMidnightOf(Date.now()) - Date.now() + 1000);
    return () => clearTimeout(id);
  }, [measuring, dash, load]);

  const startedAt = measuring && dash?.activeStartedAt ? Date.parse(dash.activeStartedAt) : null;
  const progress = dash
    ? goalProgress({
        todayRead: dash.todayReadSeconds,
        goal: dash.todayGoalSeconds,
        activeStartedAt: startedAt,
        now,
        nextMidnight: nextMidnightOf(now),
        goalStreak: dash.graph.goalStreak ?? 0,
      })
    : null;

  // 달성 순간 — 앱이 앞에 있을 때 흐름 안 Notice(모달 · 축하 효과 없음). 처음 읽은 값이 이미 채움이면 알리지 않는다.
  // 낡은 날(전날 끝난 몫 + 자정부터의 몫)로는 판정하지 않는다 — 새날 첫 값은 다시 「처음 읽은 값」이다.
  const wasFilled = useRef<boolean | null>(null);
  useEffect(() => {
    if (!progress) return;
    if (staleDay) {
      wasFilled.current = null;
      return;
    }
    if (wasFilled.current === false && progress.filled) setNotice({ message: `오늘 목표를 채웠어요 · ${progress.streak}일째` });
    wasFilled.current = progress.filled;
  }, [progress?.filled, progress?.streak, staleDay]);

  async function start(bookId: number | null) {
    if (busy) return;
    setBusy(true);
    setNotice(null);
    const r = await startReading({ start: reading.start, reload: load }, bookId);
    if (r.error) setNotice({ message: r.error, tone: 'error' });
    setBusy(false);
  }

  async function stop() {
    if (busy) return;
    setBusy(true);
    try {
      await reading.stop();
    } catch (e) {
      setNotice({ message: e instanceof Error ? e.message : String(e), tone: 'error' });
    }
    await load();
    setBusy(false);
  }

  async function chooseGoal(minutes: number) {
    setGoalOpen(false);
    try {
      await reading.setGoal(minutes * 60);
    } catch (e) {
      setNotice({ message: e instanceof Error ? e.message : String(e), tone: 'error' });
    }
    await load();
  }

  if (!dash || !progress) {
    return (
      <Screen>
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

  const status = `오늘 ${durationLabel(progress.todaySeconds)} · ${
    dash.todayGoalSeconds > 0 ? `목표 ${durationLabel(dash.todayGoalSeconds)}` : '목표 없음'
  }${progress.streak > 0 ? ` · ${progress.streak}일째` : ''}`;
  const goalLine = (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={status}
      accessibilityHint="하루 목표를 바꿔요"
      onPress={() => setGoalOpen(true)}
      style={({ pressed }) => [{ minHeight: layout.hit, justifyContent: 'center' }, pressed && { backgroundColor: color.pressed }]}
    >
      <Txt v="Q2" style={{ color: color.text }}>
        {status}
      </Txt>
    </Pressable>
  );
  const goalSheet = (
    <Sheet open={goalOpen} onClose={() => setGoalOpen(false)}>
      <Txt v="H2">하루 목표</Txt>
      <Txt v="L1" style={{ color: color.text }}>
        오늘부터 바뀌어요 — 오늘 채웠는지도 새 목표로 봐요
      </Txt>
      <View>
        {GOAL_CHOICES_MIN.map((m) => {
          const selected = dash.todayGoalSeconds === m * 60;
          return (
            <Row key={m} title={durationLabel(m * 60)} meta={selected ? '지금 목표' : undefined} selected={selected} onPress={() => void chooseGoal(m)} />
          );
        })}
      </View>
    </Sheet>
  );
  const noticeBand = notice ? <Notice message={notice.message} tone={notice.tone} /> : null;

  // ── 측정 중 — 큰 시계는 이 화면에만(§1.4-1) ──────────────────────────────
  if (measuring && startedAt !== null) {
    const elapsed = Math.min(now - startedAt, SESSION_CAP_MS) / 1000;
    return (
      <Screen>
        <Txt v="L2" style={{ color: color.muted }}>
          읽는 중
        </Txt>
        {dash.activeBookTitle ? (
          <Txt v="H2" numberOfLines={2}>
            {dash.activeBookTitle}
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
        {goalLine}
        {noticeBand}
        <Button kind="ink" label="측정 종료" busy={busy} onPress={() => void stop()} />
        {goalSheet}
      </Screen>
    );
  }

  // ── 새 계정 빈 홈 — 심사자가 보는 화면(§1.4-4). 타이머 숫자는 없다 ─────────
  const books = dash.readingBooks.length > 0 ? dash.readingBooks : dash.wantToReadBooks;
  if (books.length === 0) {
    return (
      <Screen scroll>
        <Txt v="D2">읽은 것을 내 글로</Txt>
        <Txt v="Q1" style={{ color: color.text }}>
          읽다가 마음에 남은 문장을 여백에 옮겨 두면, AI가 그 여백을 읽고 질문을 건네요. 글은 내가 써요
        </Txt>
        <View style={{ gap: space[2], marginVertical: space[2] }}>
          <Txt v="B2">1 읽는 책을 서재에 담아요</Txt>
          <Txt v="B2">2 남은 문장을 여백에 옮겨요</Txt>
          <Txt v="B2">3 질문을 받아 내 글을 써요</Txt>
        </View>
        {noticeBand}
        {/* 담기 · 바코드는 서재 화면(PR-G)이 맡는다 — 지금은 서재 탭으로 간다 */}
        <Button kind="ink" label="첫 책 담기" onPress={() => router.navigate('/library')} />
        <Button kind="outline" label="바코드로 담기" onPress={() => router.navigate('/library')} />
        <View style={{ height: layout.sectionGap }} />
        <Button kind="text" label="책 없이 시간만 재기" busy={busy} onPress={() => void start(null)} />
      </Screen>
    );
  }

  // ── 측정 전 — 표제는 읽는 책(홈-안A/B) ──────────────────────────────────
  const book = books.find((b) => b.id === pickedId) ?? books.find((b) => b.id === dash.recentBookId) ?? books[0];
  const others = books.filter((b) => b.id !== book.id);
  const kinds = homeButtons(HOME_VARIANT);
  const write = (
    // 여백 쓰기는 책 화면(PR-G)이 맡는다 — 지금은 서재 탭으로 간다
    <Button key="write" kind={kinds.write} label="여백 남기기" onPress={() => router.navigate('/library')} />
  );
  const begin = <Button key="start" kind={kinds.start} label="읽기 시작" busy={busy} onPress={() => void start(book.id)} />;
  return (
    <Screen scroll>
      <Txt v="L1" style={{ color: color.muted }}>
        {dateLabel(new Date(now))}
      </Txt>
      <View style={{ flexDirection: 'row', gap: space[4], alignItems: 'flex-start' }}>
        <Cover book={book} />
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
      {goalLine}
      {noticeBand}
      <View style={{ gap: space[3] }}>{kinds.start === 'ink' ? [begin, write] : [write, begin]}</View>
      <View style={{ gap: space[2], marginTop: space[4] }}>
        <Txt v={kinds.writingStage} accessibilityRole="header">
          이어 쓰기
        </Txt>
        {drafts && drafts.length > 0 ? (
          <View>
            {/* 글쓰기 화면(draft/[id])은 PR-H가 맡는다 — 지금은 서재 탭으로 간다 */}
            {drafts.map((d) => (
              <Row key={d.id} title={d.title} meta={d.meta} onPress={() => router.navigate('/library')} />
            ))}
          </View>
        ) : drafts ? (
          <Txt v="L1" style={{ color: color.text }}>
            아직 쓴 글이 없어요 — 여백이 있는 책에서 「이 책으로 글쓰기」를 눌러 보세요
          </Txt>
        ) : null}
      </View>
      {others.length > 0 ? (
        <View style={{ marginTop: space[4] }}>
          <Txt v="H3" accessibilityRole="header">
            다른 책
          </Txt>
          {others.map((b) => (
            <Row key={b.id} title={b.title} meta={b.author ?? undefined} accessibilityHint="이 책으로 바꿔요" onPress={() => setPickedId(b.id)} />
          ))}
        </View>
      ) : null}
      {goalSheet}
    </Screen>
  );
}

/** 카드 표지 64×90 — 사진이 없으면 천 표지(제목 해시 색 + 첫 글자). 표지는 꾸밈이라 읽히지 않는다(U-33). */
function Cover({ book }: { book: BookOption }) {
  const size = { width: cover.card.width, height: cover.card.height };
  const hidden = { accessible: false, accessibilityElementsHidden: true, importantForAccessibility: 'no-hide-descendants' as const };
  if (book.coverUrl) return <Image source={{ uri: book.coverUrl }} style={size} resizeMode="cover" {...hidden} />;
  return (
    <View style={[size, { backgroundColor: coverColor(book.title), alignItems: 'center', justifyContent: 'center' }]} {...hidden}>
      <Txt v="H2" style={{ color: cover.fg }}>
        {initialOf(book.title)}
      </Txt>
    </View>
  );
}
