// 오늘 공부 시간(v1 REQ-98 · 범위 보충 §3.2). 끝난 몫은 서버(study.todaySeconds), 진행 중 몫은 홈과 같은 함수로 센다 —
// 자정 · 6시간 규칙이 홈과 갈라지지 않는다. 목표 0은 「목표 없음」이라 todaySeconds에 영향이 없다(공부엔 목표가 없다 — 사용자 결정).
import { goalProgress } from './goalProgress';

export function studyToday(p: { todaySeconds: number; activeStartedAt: number | null; now: number; nextMidnight: number }): number {
  return goalProgress({ todayRead: p.todaySeconds, goal: 0, activeStartedAt: p.activeStartedAt, now: p.now, nextMidnight: p.nextMidnight, goalStreak: 0 })
    .todaySeconds;
}
