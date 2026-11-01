// 위젯 값(v1 REQ-34 · 범위 보충 REQ-106 · 위젯 확장 REQ-180 · 184). 위젯 런타임은 스스로 값을 못 구해 앱이 밀어 넣는다.
// 날짜를 항상 싣는다. 앱을 안 열어도 자정에 새 날로 넘어간다 — 앞으로 7번의 자정 항목(REQ-193~195). v1 §4.10은 「어제 날짜 · 32분」(참이지만 낡음)을 골랐는데
// 2026-10-31 사용자 요구가 뒤집었다: 새 날은 「그 자정에 앱을 열었다면」의 대시보드라 앱을 안 연 날 다른 곳(웹 · 다른 기기)에서 읽은 시간은 앱을 열 때까지 모른다.
// 목표 · 연속 글은 홈 목표 줄과 같은 조각(goalParts)이다(REQ-184 — REQ-106 「싣지 않는다」를 2026-10-28 사용자 요구가 바꿨다).
// 「무엇을 재는 중인가」는 이 함수 한 곳이 정한다 — surfaces.ts가 위젯과 Live Activity에 같이 쓴다.
// 독서 · 공부는 서버가 서로를 막아(409) 동시에 재지 않는다. 대기 값(오늘 분)은 독서 그대로다(공부 모드는 측정 중일 때만).
import type { ReadingWidgetProps, WidgetMonth } from '../../widgets/ReadingWidget';
import type { ContributionDay } from '../api/types';
import { dateLabel } from './format';
import { goalParts, goalProgress, nextMidnightOf, SESSION_CAP_MS } from './goalProgress';
import { WIDGET_URL } from './widgetIntent';

export interface WidgetSource {
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  todayReadSeconds: number;
  /** 오늘 목표(초) — 0 = 목표 없음 */
  todayGoalSeconds: number;
  /** goalStreak = 목표 연속(1초 연속 currentStreak가 아니다) · weeks = 잔디(큰 위젯 달력 — 옛 서버엔 없다) */
  graph?: { goalStreak?: number; weeks?: ContributionDay[][] };
  /** 옛 서버엔 없다 — 없으면 독서만 본다 */
  study?: { hasActiveSession: boolean; activeStartedAt: string | null; activeBook: { title: string } | null };
}

export function widgetPayload(d: WidgetSource, now: Date): ReadingWidgetProps {
  const p: ReadingWidgetProps = { dateLabel: dateLabel(now), todayMinutes: Math.floor(d.todayReadSeconds / 60), mode: 'reading' };
  const readingNow = d.hasActiveSession && !!d.activeStartedAt;
  const studyNow = !readingNow && !!d.study?.hasActiveSession && !!d.study.activeStartedAt;
  if (readingNow) {
    p.activeStartedAt = Date.parse(d.activeStartedAt!);
    if (d.activeBookTitle) p.bookTitle = d.activeBookTitle;
  } else if (studyNow) {
    p.mode = 'study';
    p.activeStartedAt = Date.parse(d.study!.activeStartedAt!);
    if (d.study!.activeBook?.title) p.bookTitle = d.study!.activeBook.title;
  }
  // 홈(index.tsx progress)과 같은 함수 · 같은 입력 — 진행 중 몫은 독서 측정만
  const progress = goalProgress({
    todayRead: d.todayReadSeconds,
    goal: d.todayGoalSeconds,
    activeStartedAt: readingNow ? p.activeStartedAt! : null,
    now: now.getTime(),
    nextMidnight: nextMidnightOf(now.getTime()),
    goalStreak: d.graph?.goalStreak ?? 0,
  });
  const s = goalParts({ progress, goal: d.todayGoalSeconds, measuring: readingNow });
  const note = [s.goal, s.streak].filter(Boolean).join(' · ');
  // 측정 중엔 「오늘 N분」을 싣지 않는다 — 값은 쓸 때 굳고 시계만 흐른다(설계 §4.4a-3). 공부는 runNote 없음(위젯이 「공부 중」)
  if (readingNow) p.runNote = note;
  else if (!studyNow) {
    if (s.today === undefined) p.idleTitle = s.goal;
    else Object.assign(p, { idleTitle: s.today, idleNote: note });
  }
  p.action = readingNow
    ? { label: '측정 종료', url: WIDGET_URL.stopReading }
    : studyNow
      ? { label: '측정 종료', url: WIDGET_URL.stopStudy }
      : { label: '읽기 시작', url: WIDGET_URL.start };
  // 큰 위젯 달력(REQ-189 · 190) — 잔디가 없으면(옛 서버) 싣지 않는다: 빈 달력은 「안 읽은 달」로 읽힌다
  if (d.graph?.weeks) {
    const book = p.bookTitle && `『${p.bookTitle}』`;
    const line = readingNow ? ['읽는 중', s.goal, book] : studyNow ? ['공부 중', book] : [p.dateLabel, s.today && `오늘 ${s.today}`, s.goal];
    p.month = { ...widgetMonth(d.graph.weeks, now, { seconds: progress.todaySeconds, filled: progress.filled }), line: line.filter(Boolean).join(' · ') };
    if (s.streak) p.month.streak = s.streak;
  }
  return p;
}

const dayKey = (y: number, m: number, d: number) => `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;

/**
 * 큰 위젯 달력(REQ-189) — now가 든 달 한 장. 일요일 시작 7칸 줄 · 1일 앞 빈칸.
 * 표식: 지난날 = 서버 잔디 단계(4 → 꽉 찬 점 · 1~3 → 빈 원) · 오늘 = 진행 중 몫을 얹은 판정 · 앞날 = 없음.
 * 날짜로 찾는다 — weeks[0] = 최신 주(서버가 뒤집는다)라는 순서를 가정하지 않는다.
 */
export function widgetMonth(weeks: ContributionDay[][], now: Date, today: { seconds: number; filled: boolean }): Omit<WidgetMonth, 'line' | 'streak'> {
  const y = now.getFullYear();
  const m = now.getMonth();
  const level = new Map<string, number>();
  for (const week of weeks) for (const c of week) if (c.date) level.set(c.date, c.level);
  const cells: WidgetMonth['rows'][number] = Array.from({ length: new Date(y, m, 1).getDay() }, () => ({ day: 0, mark: 0, today: false }));
  const last = new Date(y, m + 1, 0).getDate();
  for (let day = 1; day <= last; day++) {
    const lv = level.get(dayKey(y, m, day)) ?? 0;
    const isToday = day === now.getDate();
    const mark = isToday ? (today.filled ? 2 : today.seconds > 0 ? 1 : 0) : day > now.getDate() ? 0 : lv >= 4 ? 2 : lv > 0 ? 1 : 0;
    cells.push({ day, mark, today: isToday });
  }
  while (cells.length % 7) cells.push({ day: 0, mark: 0, today: false });
  const rows = Array.from({ length: cells.length / 7 }, (_, i) => cells.slice(i * 7, i * 7 + 7));
  const filled = cells.filter((c) => c.mark === 2).length;
  const read = cells.filter((c) => c.mark > 0).length;
  return { title: `${m + 1}월`, rows, a11y: `${m + 1}월, 목표 채운 날 ${filled}일, 읽은 날 ${read}일` };
}

/** 로그아웃 값(REQ-186) — 옛 꼴(날짜 · 0분)만. 단추 · 목표 칸이 없어 위젯이 옛 그림을 그린다(REQ-187). */
export function signedOutWidget(now: Date): ReadingWidgetProps {
  return { dateLabel: dateLabel(now), todayMinutes: 0, mode: 'reading' };
}

/** 자정 항목 수(REQ-193 — 조절 상수 · 사용자 결정 D1). 앱을 이만큼 안 열면 마지막 날 그림에 머문다 — 날짜가 보여 「그날 기준」임을 읽는다(v1 REQ-34). */
const WIDGET_DAYS_AHEAD = 7;

/** 위젯 타임라인(REQ-185 · 193) — 그날 항목 뒤에 앞으로 7번의 자정마다 새 날 항목(REQ-194 · 195). 시각 순: 그날 항목은 다음 자정 전에만 있다(goalProgress reachAt). */
export function widgetTimeline(d: WidgetSource, now: Date): { date: Date; props: ReadingWidgetProps }[] {
  const entries = dayEntries(d, now);
  // 서버의 오늘(계정 시간대 — 날짜가 있는 가장 늦은 칸)이 기기의 오늘과 다르면(여행 · 자정 직후 시계 어긋남) 새 날을 미리 그리지 않는다 —
  // 덧붙인 전날 칸이 서버의 참값 칸을 덮고 새 날 연속이 틀린 채 며칠 남는다. 잔디가 없는 옛 서버는 덮을 칸이 없어 가드 없이.
  const serverToday = d.graph?.weeks?.flat().reduce((m, c) => (c.date && c.date > m ? c.date : m), '');
  if (serverToday && serverToday !== dayKey(now.getFullYear(), now.getMonth(), now.getDate())) return entries;
  let src = d;
  let at = now.getTime();
  for (let i = 0; i < WIDGET_DAYS_AHEAD; i++) {
    at = nextMidnightOf(at); // 자정 정각이면 다음 날 자정 — 같은 시각 두 번 없음
    src = nextDaySource(src, at);
    // 독서가 자정을 넘겨 이어지면 1초 뒤 값 — 자정 정각엔 진행 중 몫이 0초라 큰 위젯 오늘 칸이 빈 채로 굳는다(그날 안 「지금」 항목처럼 빈 원 · 목표 없음이면 꽉 찬 점)
    entries.push(...dayEntries(src, new Date(src.hasActiveSession ? at + 1000 : at)));
  }
  return entries;
}

/** 그날 항목(REQ-185) — 그 시각 항목 + 독서 측정이 목표에 닿는 시각 항목(그 시각으로 다시 만든 값 — 연속 +1). */
function dayEntries(d: WidgetSource, now: Date): { date: Date; props: ReadingWidgetProps }[] {
  const entries = [{ date: now, props: widgetPayload(d, now) }];
  if (!d.hasActiveSession || !d.activeStartedAt) return entries;
  // reachAt은 목표 > 0 · 미달 · 다음 자정 전 · 상한 안일 때만 있다(goalProgress) — 그 시각의 값은 같은 함수가 채움 · 연속 +1로 낸다
  const { reachAt } = goalProgress({
    todayRead: d.todayReadSeconds,
    goal: d.todayGoalSeconds,
    activeStartedAt: Date.parse(d.activeStartedAt),
    now: now.getTime(),
    nextMidnight: nextMidnightOf(now.getTime()),
    goalStreak: d.graph?.goalStreak ?? 0,
  });
  if (reachAt !== null) entries.push({ date: new Date(reachAt), props: widgetPayload(d, new Date(reachAt)) });
  return entries;
}

/**
 * 그 자정에 앱이 읽었을 대시보드(REQ-194 · 195) — 끝난 몫 0 · 목표 그대로 · 측정은 상한(시작 + 6시간) 안이면 이어진다
 * (서버는 넘긴 세션을 10분 안에 상한 길이로 닫는다 — StaleSessionSweeper). 연속 = 전날 마지막 상태로 채웠으면 그날까지, 아니면 0
 * (서버 currentGoalStreak 유예 — 새 날 0시엔 전날부터 센다). 달력 전날 칸 = 전날 마지막 상태(잔디 칸은 끝난 몫만이라 진행 중 몫으로 채운 전날을 모른다).
 * ponytail: 앱을 안 연 날 다른 곳에서 읽은 시간은 모른다(0으로 본다) — 서버에서 직접 받는 C안(plan.md ⏸)이 다음 단계.
 */
function nextDaySource(d: WidgetSource, midnight: number): WidgetSource {
  const readingNow = d.hasActiveSession && !!d.activeStartedAt;
  // now = nextMidnight = 그 자정 → 전날 0시부터 min(자정, 상한)까지의 진행 중 몫을 얹은 전날 마지막 상태
  const last = goalProgress({
    todayRead: d.todayReadSeconds,
    goal: d.todayGoalSeconds,
    activeStartedAt: readingNow ? Date.parse(d.activeStartedAt!) : null,
    now: midnight,
    nextMidnight: midnight,
    goalStreak: d.graph?.goalStreak ?? 0,
  });
  const runs = (startedAt: string | null) => startedAt !== null && Date.parse(startedAt) + SESSION_CAP_MS > midnight;
  const reading = readingNow && runs(d.activeStartedAt);
  const prev = new Date(midnight - 1);
  // 단계는 4 · 1(1~3 대표) · 0만 쓴다 — widgetMonth가 그 셋만 가른다. 덧붙인 칸이 서버 칸을 덮는다(날짜로 찾고 뒤 칸이 이긴다)
  const cell: ContributionDay = { date: dayKey(prev.getFullYear(), prev.getMonth(), prev.getDate()), totalSeconds: last.todaySeconds, level: last.filled ? 4 : last.todaySeconds > 0 ? 1 : 0, manual: false };
  return {
    hasActiveSession: reading,
    activeStartedAt: reading ? d.activeStartedAt : null,
    activeBookTitle: reading ? d.activeBookTitle : null,
    todayReadSeconds: 0,
    todayGoalSeconds: d.todayGoalSeconds,
    graph: { goalStreak: last.filled ? last.streak : 0, weeks: d.graph?.weeks && [...d.graph.weeks, [cell]] },
    study: d.study && (d.study.hasActiveSession && runs(d.study.activeStartedAt) ? d.study : { hasActiveSession: false, activeStartedAt: null, activeBook: null }),
  };
}
