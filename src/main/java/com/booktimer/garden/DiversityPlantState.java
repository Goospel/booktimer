package com.booktimer.garden;

/**
 * 작가·출판사 다양성 도감의 식물 한 칸 상태 (읽기 전용 뷰 모델).
 *
 * <p>장르축 {@link GenrePlantState}와 같이 해금일·NEW가 없다 — 다양성축은 "완독 distinct 수가 임계를
 * 넘었는가"의 boolean일 뿐 날짜 누적이 아니라서다(MVP). 식물의 {@code kind}로 작가/출판사 그리드를 가른다.
 *
 * @param plant 카탈로그 식물(메타 — kind·임계·이름·이모지)
 * @param owned 보유 여부(그 kind의 완독 distinct 수 ≥ thresholdCount)
 */
public record DiversityPlantState(DiversityPlant plant, boolean owned) {
}
