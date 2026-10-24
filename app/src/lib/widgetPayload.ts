// 위젯 값(v1 REQ-34 · 범위 보충 REQ-106). 위젯 런타임은 스스로 값을 못 구해 앱이 밀어 넣는다.
// 날짜를 항상 싣는다 — 자정을 넘기면 「어제 날짜 · 32분」(참이지만 낡음)이 「오늘 · 0분」(틀릴 수 있음)보다 낫다(v1 §4.10).
// 목표 · 연속은 싣지 않는다(REQ-106). 「무엇을 재는 중인가」는 이 함수 한 곳이 정한다 — surfaces.ts가 위젯과 Live Activity에 같이 쓴다.
// 독서 · 공부는 서버가 서로를 막아(409) 동시에 재지 않는다. 대기 값(오늘 분)은 독서 그대로다(공부 모드는 측정 중일 때만).
import type { ReadingWidgetProps } from '../../widgets/ReadingWidget';
import { dateLabel } from './format';

export interface WidgetSource {
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  todayReadSeconds: number;
  /** 옛 서버엔 없다 — 없으면 독서만 본다 */
  study?: { hasActiveSession: boolean; activeStartedAt: string | null; activeBook: { title: string } | null };
}

export function widgetPayload(d: WidgetSource, now: Date): ReadingWidgetProps {
  const p: ReadingWidgetProps = { dateLabel: dateLabel(now), todayMinutes: Math.floor(d.todayReadSeconds / 60), mode: 'reading' };
  if (d.hasActiveSession && d.activeStartedAt) {
    p.activeStartedAt = Date.parse(d.activeStartedAt);
    if (d.activeBookTitle) p.bookTitle = d.activeBookTitle;
  } else if (d.study?.hasActiveSession && d.study.activeStartedAt) {
    p.mode = 'study';
    p.activeStartedAt = Date.parse(d.study.activeStartedAt);
    if (d.study.activeBook?.title) p.bookTitle = d.study.activeBook.title;
  }
  return p;
}
