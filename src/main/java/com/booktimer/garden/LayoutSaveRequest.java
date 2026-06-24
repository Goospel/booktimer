package com.booktimer.garden;

import java.util.List;

/**
 * 마을 꾸미기 "저장" 요청 래퍼 DTO (클라이언트 → 서버).
 *
 * <p>건물(BUILDING) 배치만 저장한다. 소품(decorations) 필드는 역직렬화 호환을 위해
 * 보존하되 서버 측에서 무시한다(마을 컨셉 전환 — 소프트 제거).
 *
 * @param plants      건물 배치 전체(보유 검증·한 건물 한 번)
 * @param decorations 무시됨(소프트 제거 — 역직렬화 호환 보존)
 */
public record LayoutSaveRequest(List<PlacementRequest> plants, List<Object> decorations) {

    /** null 방어 — 누락된 종은 빈 리스트(그 종 비우기)로 본다. */
    public List<PlacementRequest> plantsOrEmpty() {
        return plants == null ? List.of() : plants;
    }
}
