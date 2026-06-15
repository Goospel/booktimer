package com.booktimer.garden;

/**
 * 배치 저장 요청 한 건 (클라이언트 → 서버 입력 DTO).
 *
 * <p>편집 모드에서 "저장"을 누르면 캔버스의 현재 배치 전체가 이 리스트로 {@code POST /garden/layout}에 실린다.
 * {@code GardenLayoutService.save}가 각 항목을 검증한다 — {@code (axis, code)}가 현재 보유 집합에 있는지(위조 방어),
 * {@code x·y}가 정규화 범위(0~1)인지, 같은 식물을 두 번 놓지 않았는지(한 식물 한 번)이다(설계 §4). 검증 실패 시
 * 저장 전체를 거부한다. 자유 위치 전환(Phase 1)으로 격자 셀 번호 대신 정규화 좌표를 받는다 — 같은 좌표 겹침은 허용한다.
 *
 * @param axis 식물의 수집축 (JSON 문자열 "TIME"/"GENRE"/"DIVERSITY"/"RECIPE"로 들어옴)
 * @param code 식물 code
 * @param x    가로 위치(정규화 0~1, 월드 폭 비율)
 * @param y    세로 위치(정규화 0~1, 월드 높이 비율)
 * @param z    겹침 앞뒤 순서(zOrder, 클수록 앞)
 */
public record PlacementRequest(PlacementAxis axis, String code, double x, double y, int z) {
}
