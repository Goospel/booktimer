package com.booktimer.garden;

/**
 * 마을 캔버스에 놓인 건물 오브젝트 한 건 — 렌더 뷰 모델.
 *
 * <p>소품(Decoration)은 마을 컨셉 전환으로 제거됨. 건물(BUILDING)만 배치 가능.
 *
 * @param kind     "plant" (건물 포함 — 기존 kind 호환 유지)
 * @param axis     배치 축(BUILDING)
 * @param code     건물 code
 * @param emoji    비주얼 이모지(SVG 미적용 종의 폴백)
 * @param name     표시 이름(한글)
 * @param spriteId SVG 스프라이트 식별자. null이면 이모지로 폴백
 * @param x        가로 위치(정규화 0~1)
 * @param y        세로 위치(정규화 0~1)
 * @param z        겹침 앞뒤 순서(zOrder)
 * @param rotation 사용자 회전(도, 0~360)
 * @param scale    사용자 크기 배율(0.5~2.0)
 */
public record PlacedItem(String kind, PlacementAxis axis, String code, String emoji, String name, String spriteId,
                         double x, double y, int z, double rotation, double scale) {

    public static final String KIND_PLANT = "plant";

    /** 배치된 건물을 렌더 아이템으로 — axis 보존(편집 export가 배열로 되돌릴 때 필요). */
    public static PlacedItem plant(PlacedPlant p) {
        return new PlacedItem(KIND_PLANT, p.axis(), p.code(), p.emoji(), p.name(), p.spriteId(),
                p.x(), p.y(), p.z(), p.rotation(), p.scale());
    }

    public double isoLeftPct() { return GardenIsoProjection.screenXPercent(x, y); }
    public double isoTopPct()  { return GardenIsoProjection.screenYPercent(x, y); }
    public int depthZ()        { return (int) Math.round(y() * 10000); }
}
