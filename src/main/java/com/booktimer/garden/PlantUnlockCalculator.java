package com.booktimer.garden;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * 트랙 A 보유 식물 유도 — 순수 계산(DB·Spring·시간 무관).
 *
 * <p>설계 핵심: 보유를 저장하지 않고 <b>독서 실적의 함수로 유도</b>한다(부채 모델 N-058). 두 단계:
 * <ol>
 *   <li>{@link #achievedGoalDates} — 일자별 총 독서초와 "그날 유효했던 목표"({@code goalFor})로
 *       <b>그날 목표를 채운 날</b>들을 오름차순으로 추린다. 목표를 올려도 옛 목표를 채운 과거 날이
 *       소급 박탈되지 않게 날짜별 목표로 판정한다(N-059). baseline({@code earliest}) 이전 날은
 *       "가입 전"이라 제외한다.</li>
 *   <li>{@link #unlocked} — 달성 일수가 임계 이상인 카탈로그 식물을 보유로 친다.</li>
 * </ol>
 */
public final class PlantUnlockCalculator {

    private PlantUnlockCalculator() {
    }

    /**
     * 그날 목표를 채운 날짜들을 오름차순으로 돌려준다.
     *
     * @param dailySeconds   일자 → 그날 총 독서초
     * @param goalFor        그 날짜에 유효했던 하루 목표(초) — 보통 {@code GoalSchedule::goalFor}
     * @param earliestOrNull baseline(가장 이른 목표 설정일). 이보다 이른 날은 제외. null이면 컷오프 없음(레거시)
     * @return 달성한 날짜(오름차순). 없으면 빈 목록.
     */
    public static List<LocalDate> achievedGoalDates(Map<LocalDate, Long> dailySeconds,
                                                    ToLongFunction<LocalDate> goalFor,
                                                    LocalDate earliestOrNull) {
        return dailySeconds.entrySet().stream()
                .filter(e -> earliestOrNull == null || !e.getKey().isBefore(earliestOrNull))
                .filter(e -> e.getValue() >= goalFor.applyAsLong(e.getKey()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * 달성 일수가 임계 이상인 식물을 보유로 추린다.
     *
     * @param catalogSorted   임계 오름차순으로 정렬된 카탈로그
     * @param achievedDayCount 누적 목표 달성일 수
     * @return 보유 식물(입력 순서 유지). 빈 카탈로그면 빈 목록(N-055 — 미완성/빈 카탈로그 누수 가드).
     */
    public static List<Plant> unlocked(List<Plant> catalogSorted, int achievedDayCount) {
        return catalogSorted.stream()
                .filter(p -> p.getUnlockThresholdDays() <= achievedDayCount)
                .toList();
    }
}
