// 홈(책생글 A2 — F1 §4.1 안A · §4.6). 한 틀: 맨 위 얇은 종이 박스(표지 · 제목 · 「›」 · 외곽선 [읽기 시작]/[측정 종료] · 작은 글 — 빈 곳은 서재 탭)
// → 알림 → 검정 「여백에 글쓰기」(박스 책 · 없으면 책 없는 쪽지) → 블로그 최신 3편 + 「더 보기」. 책이 하나도 없으면 박스 대신 소개 · 담기 단추(측정 중이면 박스가 위).
// 날짜 줄 · 목표 줄 · 목표 시트 · 「다른 책」 · 큰 시계는 없다(목표 줄 · 시트는 서재 맨 위 — J66). 진입 직후 화면을 덮는 것은 0이다. 밀린 시간(부채)은 읽지도 그리지도 않는다(REQ-91).
import { router, useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { blog, reading } from '../../api/client';
import type { BlogPostRow } from '../../api/types';
import { Cover } from '../../components/Cover';
import { Button, Notice, Row, Screen, Txt, useStacked } from '../../components/ui';
import { clockLabel, durationLabel } from '../../lib/format';
import { boxGoalText, progressOf, SESSION_CAP_MS, textChunks } from '../../lib/goalProgress';
import { blogSection, type HomeBox, homeBox } from '../../lib/home';
import { noteHref, savedNotice } from '../../lib/marginSave';
import { ALREADY_STOPPED, startMeasure, stopMeasure } from '../../lib/measureStart';
import { measureStore } from '../../lib/measureSync';
import { SPACE } from '../../lib/names';
import { goalNoticeStep } from '../../lib/reminders';
import { useMeasureDashboard } from '../../lib/useMeasureDashboard';
import { useMeasureShortcut } from '../../lib/useMeasureShortcut';
import { useWidgetIntent } from '../../lib/useWidgetIntent';
import { color, cover, layout, space } from '../../theme/tokens';

type NoticeState = { message: string; tone?: 'info' | 'error' } | null;

export default function Home() {
  // 읽기 · 앞으로 올 때 · 포커스 · 1초 틱 · 자정 · 보내기 알림은 훅이 한다(타이머 오프라인 — 캐시 + 기기 원장으로 오프라인에서도 그리고 잰다).
  // 위젯 · LA · 알림은 루트가 맞춘다
  const [notice, setNotice] = useState<NoticeState>(null);
  const { dash, now, rolled, cached, loadError, load, gate } = useMeasureDashboard({ mode: 'reading', onNotice: (message) => setNotice({ message }) });
  const [busy, setBusy] = useState(false);
  const [posts, setPosts] = useState<BlogPostRow[] | null>(null); // null = 아직 못 읽음(줄 · 빈 문구 없음 — F1 §4.1-3)
  const stacked = useStacked();

  // 여백 쓰기에서 남기고 돌아오면 맨 위 흐름 안 알림(REQ-135) · 블로그 최신 3편을 포커스마다 다시 읽는다(읽는 동안은 앞 값 그대로 — D8)
  useFocusEffect(
    useCallback(() => {
      if (savedNotice.take()) setNotice({ message: '여백을 남겼어요' });
      void blog.posts(3).then((r) => setPosts(r.posts), () => setPosts(null)); // 못 읽으면 null = 머리 + 「더 보기」만(F1 §4.1-3 · D8) — 기다리지 않아 홈을 막지 않는다
    }, []),
  );

  const measuring = !!dash?.hasActiveSession && !!dash.activeStartedAt;
  const startedAt = measuring && dash?.activeStartedAt ? Date.parse(dash.activeStartedAt) : null;
  const progress = dash ? progressOf(dash, now) : null;

  // 달성 순간 — 앱이 앞에 있을 때 흐름 안 Notice(모달 · 축하 효과 없음). 처음 읽은 값이 이미 채움이면 알리지 않는다.
  // 굴린 값(지난날 기준 값)과 디스크 값으로는 판정하지 않는다 — 그다음 새 값이 다시 「처음 읽은 값」이다(goalNoticeStep — REQ-371).
  // 판정은 goalMetNotice 하나(REQ-178 — 목표 0엔 달성이 없다). deps에 목표가 없어도 된다: 판정 축은 filled 전환이고 그 렌더의 목표를 읽는다.
  const wasFilled = useRef<boolean | null>(null);
  useEffect(() => {
    if (!dash || !progress) return;
    const step = goalNoticeStep(wasFilled.current, { rolled, cached, progress, goal: dash.todayGoalSeconds });
    if (step.message) setNotice({ message: step.message });
    wasFilled.current = step.prev;
  }, [progress?.filled, progress?.streak, rolled, cached]);

  // 시작 · 정지는 기기 원장에 먼저 적는다(서버를 기다리지 않는다 — REQ-357). busy는 서버에 열린 측정(웹 시작)의 정지만 실제로 기다린다.
  // 기기 쪽은 몇 ms라 busy가 두 번째 탭을 막지 못해 시간 문(gate — 700ms)으로 막는다(REQ-372). load는 훅의 load — 실패를 loadError로 바꾼다
  const deps = { store: measureStore, load };
  async function start(bookId: number | null, bookTitle: string | null) {
    if (busy || !gate()) return;
    setBusy(true);
    setNotice(null);
    const r = await startMeasure(deps, { mode: 'reading', bookId, bookTitle, blocked: !!dash?.study.hasActiveSession });
    if (r.error) setNotice({ message: r.error, tone: 'error' });
    setBusy(false);
  }

  async function stop() {
    if (busy || !gate()) return;
    setBusy(true);
    setNotice(null); // 앞 실패 띠(오프라인)가 이번 정지 성공 뒤에 남지 않게 — 공부 탭과 같다
    const r = await stopMeasure({ ...deps, serverStop: reading.stop }, 'reading');
    if (r.error) setNotice({ message: r.error, tone: 'error' });
    else if (r.info) setNotice({ message: r.info });
    setBusy(false);
  }

  // 박스 — 이른 반환보다 위(아래 단축키 훅이 쓴다)
  const rest = dash ? homeBox(dash, false) : null; // 측정 전 박스 — null이면 책이 하나도 없다(빈 홈 소개 — F1 §7-2 · §4.6)
  const box = dash && measuring ? homeBox(dash, true) : rest;
  // Option+P · 위젯 = 지금 보이는 측정 버튼(REQ-105 · REQ-274): 측정 중 박스 [측정 종료] · 빈 홈 「책 없이 시간만 재기」 · 측정 전 박스 [읽기 시작]
  const measureButton = !dash || !progress ? null : measuring ? stop : rest === null ? () => start(null, null) : () => start(rest.bookId, rest.title);
  useMeasureShortcut({ button: measureButton, busy, covered: false }); // 홈엔 시트가 없다(목표 시트는 서재로 — J66)
  // 위젯 [읽기 시작] · [측정 종료](위젯 확장 REQ-183 · 타이머 오프라인 REQ-370) — 보이는 대시보드로 판정해 같은 버튼을 누른다
  useWidgetIntent({ mode: 'reading', ready: dash !== null, measuring, button: measureButton, onAlreadyStopped: () => setNotice({ message: ALREADY_STOPPED }) });

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

  const text = boxGoalText({ progress, goal: dash.todayGoalSeconds, measuring });
  const elapsed = startedAt === null ? null : Math.min(now - startedAt, SESSION_CAP_MS) / 1000;
  const noticeBand = notice ? <Notice message={notice.message} tone={notice.tone} /> : null;
  // 한 return — 갈래마다 다른 틀이면 fiber 재사용 함정(T-256). 알림 칸은 하나이고 자리만 둘이다(박스가 있으면 박스 바로 아래 — F1 §4.1-1)
  return (
    <Screen scroll>
      {box ? <Box box={box} text={text} elapsed={elapsed} busy={busy} stacked={stacked} onMeasure={() => void measureButton?.()} /> : null}
      {box ? noticeBand : null}
      {rest === null ? <Intro /> : null}
      {box ? null : noticeBand}
      <Button kind="ink" label="여백에 글쓰기" onPress={() => router.push(noteHref(box?.bookId ?? null))} />
      {rest === null ? <AddRow stacked={stacked} /> : null}
      {rest === null && !measuring ? (
        <>
          <View style={{ height: layout.sectionGap }} />
          <Button kind="text" label="책 없이 시간만 재기" busy={busy} onPress={() => void start(null, null)} />
        </>
      ) : null}
      <Blog posts={posts} />
    </Screen>
  );
}

const toLibrary = () => router.navigate('/library');

/**
 * 홈 맨 위 박스(F1 §4.1 안A — paper2 · 모서리 0 · 안쪽 12 · 사이 16 · 높이 114). 면 전체 = 서재 탭, 안의 단추는 따로.
 * VoiceOver: 바깥 면은 요소가 아니고 제목 줄(제목 · 작은 글) · 단추 · (측정 중) 시계가 요소다(D7). 단추 둘레 8은 박스 누르기에서 뺀다(빗나간 탭 — hitSlop 금지).
 */
function Box(p: { box: HomeBox; text: string; elapsed: number | null; busy: boolean; stacked: boolean; onMeasure: () => void }) {
  const { box, text, elapsed } = p;
  return (
    <Pressable accessible={false} onPress={toLibrary} style={{ backgroundColor: color.paper2 }}>
      {({ pressed }) => (
        <View style={[{ flexDirection: 'row', gap: space[4], padding: space[3] }, pressed && { backgroundColor: color.pressed }]}>
          {box.cover ? <Cover title={box.title} coverUrl={box.coverUrl} size="card" /> : null}
          <View style={{ flex: 1, minHeight: cover.card.height, justifyContent: 'space-between', gap: space[3] }}>
            <View
              accessible
              accessibilityRole="button"
              accessibilityLabel={`${box.title}, ${text}`}
              accessibilityHint="서재를 열어요"
              onAccessibilityTap={toLibrary}
              style={{ flexDirection: 'row', alignItems: 'center', gap: space[2] }}
            >
              <Txt v="H3" numberOfLines={1} style={{ flex: 1 }}>
                {box.title}
              </Txt>
              <Txt v="L1" style={{ color: color.muted }} accessible={false}>
                ›
              </Txt>
            </View>
            <View style={p.stacked ? { gap: space[3], alignItems: 'flex-start' } : { flexDirection: 'row', alignItems: 'center', gap: space[3] }}>
              <View onStartShouldSetResponder={() => true} style={{ padding: space[2], margin: -space[2] }}>
                <Button kind="outline" label={elapsed === null ? '읽기 시작' : '측정 종료'} busy={p.busy} onPress={p.onMeasure} />
              </View>
              {elapsed === null ? (
                <View style={[{ flexDirection: 'row', flexWrap: 'wrap', columnGap: space[1] }, !p.stacked && { flex: 1 }]}>
                  {textChunks(text).map((s) => (
                    <Txt key={s} v="T2" style={{ color: color.muted }} accessible={false}>
                      {s}
                    </Txt>
                  ))}
                </View>
              ) : (
                <View style={p.stacked ? undefined : { flex: 1 }}>
                  <Txt v="H3" accessibilityLabel={`측정 중, ${durationLabel(elapsed)}`} style={{ fontVariant: ['tabular-nums'] }}>
                    {clockLabel(elapsed)}
                  </Txt>
                  <Txt v="T2" style={{ color: color.muted }} accessible={false}>
                    {text}
                  </Txt>
                </View>
              )}
            </View>
          </View>
        </View>
      )}
    </Pressable>
  );
}

/** 새 계정 빈 홈 소개(결정 라 안A — F1 §4.1 「새 계정 빈 홈」) */
function Intro() {
  return (
    <>
      <Txt v="D2">읽은 것을 내 글로</Txt>
      <Txt v="Q1" style={{ color: color.text }}>
        {`마음에 남은 문장과 생각을 여백에 적어 두세요. AI가 질문을 건네고, 다듬은 글은 ${SPACE}에 올려요`}
      </Txt>
      <View style={{ gap: space[2], marginVertical: space[2] }}>
        <Txt v="B2">1 읽는 책을 서재에 담아요</Txt>
        <Txt v="B2">2 남은 문장과 생각을 여백에 적어요</Txt>
        <Txt v="B2">{`3 다듬은 글을 ${SPACE}에 올려요`}</Txt>
      </View>
    </>
  );
}

/** 빈 홈 담기 입구 — 외곽선 둘 나란히(큰 글자면 세로 — 서재 담기 입구와 같은 꼴) */
function AddRow({ stacked }: { stacked: boolean }) {
  return (
    <View style={{ flexDirection: stacked ? 'column' : 'row', gap: stacked ? space[3] : space[4] }}>
      <View style={stacked ? undefined : { flex: 1 }}>
        <Button kind="outline" label="첫 책 담기" onPress={() => router.push('/add')} />
      </View>
      <View style={stacked ? undefined : { flex: 1 }}>
        <Button kind="outline" label="바코드로 담기" onPress={() => router.push('/add?scan=1')} />
      </View>
    </View>
  );
}

/** 블로그 칸(D14 · REQ-273 — F1 §4.1-3). 「더 보기」는 0편 · 못 읽음에도 조건 없이 단다(블로그 페이지가 글쓰기 입구다) */
function Blog({ posts }: { posts: BlogPostRow[] | null }) {
  const s = blogSection(posts);
  return (
    <View style={{ gap: space[2] }}>
      <Txt v="H3" accessibilityRole="header">
        {SPACE}
      </Txt>
      {s.rows.length > 0 ? (
        <View>
          {s.rows.map((r) => (
            <Row key={r.id} title={r.title} meta={r.meta} onPress={() => router.push(`/blog/${r.id}`)} />
          ))}
        </View>
      ) : null}
      {s.empty ? (
        <Txt v="L1" style={{ color: color.text }}>
          {s.empty}
        </Txt>
      ) : null}
      <View style={{ alignItems: 'flex-start' }}>
        <Button kind="text" label="더 보기" onPress={() => router.push('/blog')} />
      </View>
    </View>
  );
}
