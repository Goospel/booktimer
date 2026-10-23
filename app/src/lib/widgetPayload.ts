// 위젯 값(v1 REQ-34 · 범위 보충 REQ-106). 위젯 런타임은 스스로 값을 못 구해 앱이 밀어 넣는다.
// 날짜를 항상 싣는다 — 자정을 넘기면 「어제 날짜 · 32분」(참이지만 낡음)이 「오늘 · 0분」(틀릴 수 있음)보다 낫다(v1 §4.10).
// 목표 · 연속은 싣지 않는다(REQ-106). 공부 모드는 PR-S1이 더한다.
import type { ReadingWidgetProps } from '../../widgets/ReadingWidget';
import { dateLabel } from './format';

export interface WidgetSource {
  hasActiveSession: boolean;
  activeStartedAt: string | null;
  activeBookTitle: string | null;
  todayReadSeconds: number;
}

export function widgetPayload(d: WidgetSource, now: Date): ReadingWidgetProps {
  const p: ReadingWidgetProps = { dateLabel: dateLabel(now), todayMinutes: Math.floor(d.todayReadSeconds / 60), mode: 'reading' };
  if (d.hasActiveSession && d.activeStartedAt) {
    p.activeStartedAt = Date.parse(d.activeStartedAt);
    if (d.activeBookTitle) p.bookTitle = d.activeBookTitle;
  }
  return p;
}
