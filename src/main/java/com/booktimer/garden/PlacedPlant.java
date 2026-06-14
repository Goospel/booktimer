package com.booktimer.garden;

/**
 * 정원 캔버스에 놓인 식물 한 칸 (렌더용 뷰 모델).
 *
 * <p>{@link OwnedPlant}(축·code·이모지·이름)에 격자 셀 위치를 더한 것이다. {@code GardenLayoutService.layoutOf}가
 * 저장된 배치 ∩ 현재 보유만 추려(유령 식물 방지, 설계 §5 리스크 2) 이 형태로 내주고, 화면은 셀 인덱스대로 캔버스에 놓는다.
 *
 * @param axis  소속 수집축
 * @param code  식물 code
 * @param emoji 비주얼 이모지
 * @param name  표시 이름(한글)
 * @param cell  격자 셀 인덱스(0-based)
 */
public record PlacedPlant(PlacementAxis axis, String code, String emoji, String name, int cell) {
}
