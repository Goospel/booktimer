package com.booktimer.garden;

import java.time.LocalDate;

/**
 * 도감의 식물 한 칸 상태 (읽기 전용 뷰 모델).
 *
 * @param plant      카탈로그 식물(메타)
 * @param owned      보유 여부(누적 달성일 ≥ 임계)
 * @param unlockedOn 보유 시 해금일(임계번째 달성 날짜), 미보유면 null
 * @param isNew      최근 7일 내 해금이면 true("NEW" 배지) — 보유 시에만 의미 있음
 */
public record PlantState(Plant plant, boolean owned, LocalDate unlockedOn, boolean isNew) {
}
