// 위젯 값(v1 REQ-34 · 범위 보충 REQ-106 · 위젯 확장 REQ-180 · 184). 위젯 런타임은 스스로 값을 못 구해 앱이 밀어 넣는다.
// 날짜를 항상 싣는다 — 자정을 넘기면 「어제 날짜 · 32분」(참이지만 낡음)이 「오늘 · 0분」(틀릴 수 있음)보다 낫다(v1 §4.10).
// 목표 · 연속 글은 홈 목표 줄과 같은 조각(goalParts)이다(REQ-184 — REQ-106 「싣지 않는다」를 2026-10-28 사용자 요구가 바꿨다).
// 「무엇을 재는 중인가」는 이 함수 한 곳이 정한다 — surfaces.ts가 위젯과 Live Activity에 같이 쓴다.
// 독서 · 공부는 서버가 서로를 막아(409) 동시에 재지 않는다. 대기 값(오늘 분)은 독서 그대로다(공부 모드는 측정 중일 때만).
import type { ReadingWidgetProps } from '../../widgets/ReadingWidget';
import { dateLabel } from './format';
import { goalParts, goalProgress, nextMidnightOf } from './goalProgress';
import { WIDGET_URL } from './widgetIntent';

export interface WidgetSource {
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  todayReadSeconds: number;
  /** 오늘 목표(초) — 0 = 목표 없음 */
  todayGoalSeconds: number;
  /** goalStreak = 목표 연속(1초 연속 currentStreak가 아니다) */
  graph?: { goalStreak?: number };
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
  return p;
}

/** 로그아웃 값(REQ-186) — 옛 꼴(날짜 · 0분)만. 단추 · 목표 칸이 없어 위젯이 옛 그림을 그린다(REQ-187). */
export function signedOutWidget(now: Date): ReadingWidgetProps {
  return { dateLabel: dateLabel(now), todayMinutes: 0, mode: 'reading' };
}

/** 위젯 타임라인(REQ-185) — 지금 항목 + 독서 측정이 목표에 닿는 시각 항목(그 시각으로 다시 만든 값 — 연속 +1). 자정 항목은 두지 않는다(v1 §4.10). */
export function widgetTimeline(d: WidgetSource, now: Date): { date: Date; props: ReadingWidgetProps }[] {
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
