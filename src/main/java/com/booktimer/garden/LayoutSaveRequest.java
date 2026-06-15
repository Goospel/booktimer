package com.booktimer.garden;

import java.util.List;

/**
 * 정원 꾸미기 "저장" 한 번에 실리는 전체 배치 요청 (클라이언트 → 서버 입력 래퍼 DTO).
 *
 * <p>편집 캔버스에는 식물과 소품이 한 화면에 섞여 놓이므로, "저장"은 둘을 함께 보내 <b>원자적 교체</b>한다(설계 §2 결정 ②).
 * {@code GardenLayoutService.saveLayout}이 한 트랜잭션에서 식물({@link PlacementRequest})과 소품
 * ({@link DecorationPlacementRequest})을 각각 검증·교체 저장한다 — 한쪽이 실패하면 전체가 무산된다.
 *
 * <p>{@code null} 리스트는 빈 리스트로 간주해 "그 종을 비움"으로 처리한다(부분 저장 안전성).
 *
 * @param plants      식물 배치 전체(보유 검증·한 식물 한 번)
 * @param decorations 소품 배치 전체(카탈로그 검증·중복 허용)
 */
public record LayoutSaveRequest(List<PlacementRequest> plants, List<DecorationPlacementRequest> decorations) {

    /** null 방어 — 누락된 종은 빈 리스트(그 종 비우기)로 본다. */
    public List<PlacementRequest> plantsOrEmpty() {
        return plants == null ? List.of() : plants;
    }

    public List<DecorationPlacementRequest> decorationsOrEmpty() {
        return decorations == null ? List.of() : decorations;
    }
}
