// 목표 달성 알림 계획(범위 보충 §3.4 · REQ-92). 앱이 뒤에 있을 때 채우는 순간 한 통 — 독서 알림이 켜져 있고 권한이 있을 때만.
// 이 함수는 「무엇을 예약해야 하나」만 낸다. null = goal-* 예약이 없어야 한다(정지 · 끔 · 이미 채움).
// 실제 예약 · 취소(expo-notifications)와 「독서 알림」 설정은 PR-I가 붙인다 — 그때까지는 흐름 안 Notice만 있다.
import { type GoalInput, goalProgress } from './goalProgress';

export interface GoalNoticePlan {
  /** `goal-YYYYMMDD` — 하루에 하나 */
  id: string;
  at: number;
}

export function goalNotice(p: GoalInput & { remindersOn: boolean; permitted: boolean }): GoalNoticePlan | null {
  if (!p.remindersOn || !p.permitted) return null;
  const { reachAt } = goalProgress(p);
  if (reachAt === null) return null;
  const d = new Date(reachAt); // reachAt은 다음 자정 전이라 오늘 날짜다
  const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
  return { id: `goal-${ymd}`, at: reachAt };
}
