package com.booktimer.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목표 변경 이력 → "그날 유효했던 목표" 해석 순수 로직 검증 (DB/시간 무관).
 *
 * <p>{@code goalFor(date)} = effective_date ≤ date 인 변경 중 가장 최근 값(floorEntry).
 * 그 날짜 이전 변경이 없으면(레거시·미온보딩) 폴백 목표를 쓴다. 이 한 줄이 "목표를 올려도
 * 과거 날은 그날 목표로 판정된다"는 핵심 불변식을 만든다.
 */
class GoalScheduleTest {

    private static final long FALLBACK = 3600L;
    private static final LocalDate JUN1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate JUN5 = LocalDate.of(2026, 6, 5);

    private static Map<LocalDate, Long> changes() {
        return new LinkedHashMap<>();
    }

    @Test
    @DisplayName("그날 이하 가장 최근 변경값을 쓴다 (floorEntry)")
    void goalFor_usesLatestChangeOnOrBeforeDate() {
        Map<LocalDate, Long> changes = changes();
        changes.put(JUN1, 3600L);  // 6/1부터 60분
        changes.put(JUN5, 3660L);  // 6/5부터 61분
        GoalSchedule schedule = GoalSchedule.of(changes, FALLBACK);

        assertThat(schedule.goalFor(JUN1.plusDays(2))).isEqualTo(3600L); // 6/3 → 아직 60분
        assertThat(schedule.goalFor(JUN5)).isEqualTo(3660L);            // 6/5 당일 → 61분
        assertThat(schedule.goalFor(JUN5.plusDays(1))).isEqualTo(3660L); // 6/6 → 61분 유지
    }

    @Test
    @DisplayName("첫 변경 이전 날짜는 폴백 목표를 쓴다")
    void goalFor_beforeFirstChange_usesFallback() {
        Map<LocalDate, Long> changes = changes();
        changes.put(JUN5, 3660L);
        GoalSchedule schedule = GoalSchedule.of(changes, FALLBACK);

        assertThat(schedule.goalFor(JUN1)).isEqualTo(FALLBACK); // 6/1은 첫 변경(6/5) 이전
    }

    @Test
    @DisplayName("변경 이력이 비면 모든 날짜가 폴백 목표")
    void goalFor_emptyHistory_alwaysFallback() {
        GoalSchedule schedule = GoalSchedule.of(changes(), FALLBACK);
        assertThat(schedule.goalFor(JUN5)).isEqualTo(FALLBACK);
    }

    @Test
    @DisplayName("입력 순서와 무관하게 날짜순으로 해석한다")
    void goalFor_ignoresInsertionOrder() {
        Map<LocalDate, Long> changes = changes();
        changes.put(JUN5, 3660L); // 일부러 늦은 날짜 먼저 넣음
        changes.put(JUN1, 3600L);
        GoalSchedule schedule = GoalSchedule.of(changes, FALLBACK);

        assertThat(schedule.goalFor(JUN1.plusDays(1))).isEqualTo(3600L); // 6/2 → 60분
        assertThat(schedule.goalFor(JUN5.plusDays(1))).isEqualTo(3660L); // 6/6 → 61분
    }
}
