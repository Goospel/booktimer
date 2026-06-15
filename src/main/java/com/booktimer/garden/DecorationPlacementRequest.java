package com.booktimer.garden;

/**
 * 소품 배치 저장 요청 한 건 (클라이언트 → 서버 입력 DTO).
 *
 * <p>편집 모드에서 "저장"을 누르면 캔버스의 소품 배치 전체가 {@link LayoutSaveRequest#decorations()}로
 * {@code POST /garden/layout}에 실린다. {@code GardenLayoutService.saveDecorations}가 각 항목을 검증한다 —
 * {@code code}가 카탈로그에 있는지(미지 코드 거부), {@code x·y}가 정규화 범위(0~1)인지,
 * {@code rotation}(0~360)·{@code scale}(0.5~2.0) 범위인지다. 식물과 달리 <b>보유 검증·중복 거부가 없다</b>
 * (소품은 보유 무관·중복 허용, 설계 §1). 검증 실패 시 저장 전체를 거부한다.
 *
 * @param code     소품 code({@link Decoration#getCode()})
 * @param x        가로 위치(정규화 0~1, 월드 폭 비율)
 * @param y        세로 위치(정규화 0~1, 월드 높이 비율)
 * @param z        겹침 앞뒤 순서(zOrder, 클수록 앞 — 식물과 통합 단일 스케일)
 * @param rotation 사용자 회전(도, 0~360)
 * @param scale    사용자 크기 배율(0.5~2.0)
 */
public record DecorationPlacementRequest(String code, double x, double y, int z, double rotation, double scale) {
}
