package com.booktimer.session;

/**
 * 독서 잔디 + 목표 연속 — 같은 일자별 집계·같은 {@link com.booktimer.timer.GoalSchedule}에서 한 번에 만든다(조회 중복 없음).
 *
 * <p>목표 연속은 {@link ContributionGraph} record에 넣지 않는다 — 공부 잔디도 그 record를 쓰는데 목표 자리에
 * 고정 농도값을 넘겨 목표 연속이 뜻이 없다(설계 2026-10-22 §3.4).
 *
 * @param graph      독서 잔디(1초 연속 {@code currentStreak} 포함 — 값·뜻 그대로)
 * @param goalStreak 목표를 채운 날(잔디 4단계)이 오늘(유예 — 오늘 아직이면 어제)부터 이어진 수
 */
public record ReadingGraph(ContributionGraph graph, int goalStreak) {
}
