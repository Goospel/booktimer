package com.booktimer.garden;

/**
 * 배치 저장 요청 한 건 (클라이언트 → 서버 입력 DTO).
 *
 * <p>편집 모드에서 "저장"을 누르면 캔버스의 현재 배치 전체가 이 리스트로 {@code POST /garden/layout}에 실린다.
 * {@code GardenLayoutService.save}가 각 항목을 검증한다 — {@code (axis, code)}가 현재 보유 집합에 있는지(위조 방어),
 * {@code cell}이 격자 범위인지, 셀·식물 중복이 없는지(설계 §4). 검증 실패 시 저장 전체를 거부한다.
 *
 * @param axis 식물의 수집축 (JSON 문자열 "TIME"/"GENRE"/"DIVERSITY"/"RECIPE"로 들어옴)
 * @param code 식물 code
 * @param cell 놓을 격자 셀 인덱스(0-based)
 */
public record PlacementRequest(PlacementAxis axis, String code, int cell) {
}
