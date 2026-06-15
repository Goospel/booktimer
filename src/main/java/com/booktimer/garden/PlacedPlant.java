package com.booktimer.garden;

/**
 * 정원 캔버스에 놓인 식물 한 건 (렌더용 뷰 모델).
 *
 * <p>{@link OwnedPlant}(축·code·이모지·이름)에 정규화 좌표(0~1)와 겹침 순서를 더한 것이다.
 * {@code GardenLayoutService.layoutOf}가 저장된 배치 ∩ 현재 보유만 추려(유령 식물 방지, 설계 §5 리스크 2) 이 형태로
 * 내주고, 화면은 좌표 × 월드픽셀로 식물을 자유 위치에 놓는다(자유 위치 전환 Phase 1). 결과는 zOrder 오름차순이라
 * 먼저 그린 것이 뒤, 나중이 앞이다.
 *
 * @param axis     소속 수집축
 * @param code     식물 code
 * @param emoji    비주얼 이모지(SVG 미적용 종의 폴백)
 * @param name     표시 이름(한글)
 * @param spriteId 코드 벡터 SVG 스프라이트 식별자(A2). null이면 이모지로 폴백
 * @param x        가로 위치(정규화 0~1)
 * @param y        세로 위치(정규화 0~1)
 * @param z        겹침 앞뒤 순서(zOrder)
 */
public record PlacedPlant(PlacementAxis axis, String code, String emoji, String name, String spriteId,
                          double x, double y, int z) {
}
