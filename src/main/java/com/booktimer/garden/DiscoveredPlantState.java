package com.booktimer.garden;

/**
 * 트랙 B 레시피 도감의 식물 한 칸 상태 (읽기 전용 뷰 모델).
 *
 * <p>{@code discovered}가 false면 화면은 조건을 숨긴 "???" 미스터리 슬롯으로 렌더한다(발견 자체가 재미라
 * 어떤 조합인지 안내하지 않는다, 설계 §2.5). true면 이모지·이름·스토리를 드러낸다.
 *
 * @param recipePlant 카탈로그 식물(메타)
 * @param discovered  발견(보유) 여부 — 한 번 발견하면 책장이 바뀌어도 유지된다(§2.2)
 */
public record DiscoveredPlantState(RecipePlant recipePlant, boolean discovered) {
}
