package com.booktimer.garden;

import com.booktimer.timer.GoalSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트랙 A 보유 식물 유도 순수 로직 검증 (DB·시간 무관).
 *
 * <p>경계를 전수로 박는다(도메인 핵심): 미달/정확/초과 경계, 날짜별 목표 소급 방어(N-059),
 * baseline 컷오프(가입 전 누수), 빈 카탈로그 누수 가드(N-055).
 */
class PlantUnlockCalculatorTest {

    private static final long GOAL = 3600L; // 1시간
    private static final ToLongFunction<LocalDate> FLAT_GOAL = d -> GOAL;

    private static final LocalDate D1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 6, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 6, 3);

    private static Map<LocalDate, Long> seconds() {
        return new LinkedHashMap<>();
    }

    @Nested
    @DisplayName("achievedGoalDates — 그날 목표를 채운 날 추리기")
    class AchievedGoalDates {

        @Test
        @DisplayName("빈 입력 → 빈 목록")
        void empty() {
            assertThat(PlantUnlockCalculator.achievedGoalDates(seconds(), FLAT_GOAL, null)).isEmpty();
        }

        @Test
        @DisplayName("목표 미달(초 < 목표)인 날은 제외")
        void belowGoal_excluded() {
            Map<LocalDate, Long> s = seconds();
            s.put(D1, GOAL - 1);
            assertThat(PlantUnlockCalculator.achievedGoalDates(s, FLAT_GOAL, null)).isEmpty();
        }

        @Test
        @DisplayName("목표 정확히 채운 날 포함 (경계: 이상 포함)")
        void exactlyGoal_included() {
            Map<LocalDate, Long> s = seconds();
            s.put(D1, GOAL);
            assertThat(PlantUnlockCalculator.achievedGoalDates(s, FLAT_GOAL, null)).containsExactly(D1);
        }

        @Test
        @DisplayName("목표 초과한 날 포함")
        void aboveGoal_included() {
            Map<LocalDate, Long> s = seconds();
            s.put(D1, GOAL + 100);
            assertThat(PlantUnlockCalculator.achievedGoalDates(s, FLAT_GOAL, null)).containsExactly(D1);
        }

        @Test
        @DisplayName("여러 날 섞임 → 달성한 날만, 오름차순")
        void mixed_onlyAchieved_ascending() {
            Map<LocalDate, Long> s = seconds();
            s.put(D3, GOAL);          // 달성 (일부러 늦은 날 먼저 넣음)
            s.put(D2, GOAL - 1);      // 미달
            s.put(D1, GOAL + 1);      // 달성
            assertThat(PlantUnlockCalculator.achievedGoalDates(s, FLAT_GOAL, null))
                    .containsExactly(D1, D3);
        }

        @Test
        @DisplayName("날짜별 목표 변경: 옛 목표(작은 값) 채운 날은 소급 박탈 안 됨 (N-059)")
        void perDayGoal_noRetroactiveRevocation() {
            // D1엔 목표 30분, D2부터 60분으로 인상.
            Map<LocalDate, Long> changes = new LinkedHashMap<>();
            changes.put(D1, 1800L);
            changes.put(D2, 3600L);
            GoalSchedule schedule = GoalSchedule.of(changes, 3600L);

            Map<LocalDate, Long> s = seconds();
            s.put(D1, 1800L); // 그날 목표(30분) 정확히 채움 → 달성
            s.put(D2, 1800L); // 그날 목표(60분) 미달 → 제외

            assertThat(PlantUnlockCalculator.achievedGoalDates(s, schedule::goalFor, schedule.earliestEffectiveDate().orElse(null)))
                    .containsExactly(D1);
        }

        @Test
        @DisplayName("baseline 이전 날은 제외 (가입 전 '달성' 누수 가드)")
        void beforeBaseline_excluded() {
            Map<LocalDate, Long> s = seconds();
            s.put(D1, GOAL); // baseline 이전
            s.put(D2, GOAL); // baseline 당일
            s.put(D3, GOAL); // baseline 이후
            assertThat(PlantUnlockCalculator.achievedGoalDates(s, FLAT_GOAL, D2))
                    .containsExactly(D2, D3);
        }
    }

    @Nested
    @DisplayName("unlocked — 달성 일수로 보유 식물 추리기")
    class Unlocked {

        // 임계 오름차순 카탈로그
        private final List<Plant> catalog = List.of(
                Plant.of("sprout", "새싹", "🌱", 1, 1, null),
                Plant.of("herb", "허브", "🌿", 2, 3, null),
                Plant.of("clover", "클로버", "☘️", 3, 5, null));

        @Test
        @DisplayName("달성 0일 → 아무것도 안 보유")
        void zero_none() {
            assertThat(PlantUnlockCalculator.unlocked(catalog, 0)).isEmpty();
        }

        @Test
        @DisplayName("첫 임계 직전(임계-1) → 아무것도 안 보유")
        void justBelowFirst_none() {
            assertThat(PlantUnlockCalculator.unlocked(catalog, 0)).isEmpty();
        }

        @Test
        @DisplayName("첫 임계 정확히 → 첫 식물만")
        void exactlyFirst_one() {
            assertThat(PlantUnlockCalculator.unlocked(catalog, 1))
                    .extracting(Plant::getCode).containsExactly("sprout");
        }

        @Test
        @DisplayName("중간 값 → 임계 이하 전부 누적")
        void middle_cumulative() {
            assertThat(PlantUnlockCalculator.unlocked(catalog, 4))
                    .extracting(Plant::getCode).containsExactly("sprout", "herb");
        }

        @Test
        @DisplayName("최대 임계 초과 → 전부, 그 이상 없음")
        void aboveMax_all() {
            assertThat(PlantUnlockCalculator.unlocked(catalog, 999))
                    .extracting(Plant::getCode).containsExactly("sprout", "herb", "clover");
        }

        @Test
        @DisplayName("빈 카탈로그 → 빈 목록 (N-055 누수 가드)")
        void emptyCatalog_none() {
            assertThat(PlantUnlockCalculator.unlocked(List.of(), 999)).isEmpty();
        }
    }
}
