package com.booktimer.garden;

/**
 * 장르 도감의 식물 한 칸 상태 (읽기 전용 뷰 모델).
 *
 * <p>시간축 {@link PlantState}와 달리 해금일·NEW가 없다 — 장르축은 "그 장르를 완독했는가"의 boolean일 뿐
 * 날짜 누적이 아니라서다(MVP). 성장 단계·획득일은 후속 확장 여지로 둔다.
 *
 * @param genrePlant 카탈로그 식물(메타)
 * @param owned      보유 여부(그 장르를 한 권 이상 완독, 폴백은 시드에 없는 장르를 완독)
 */
public record GenrePlantState(GenrePlant genrePlant, boolean owned) {
}
