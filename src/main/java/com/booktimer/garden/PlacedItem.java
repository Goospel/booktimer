package com.booktimer.garden;

/**
 * 정원 캔버스에 놓인 오브젝트 한 건 — <b>식물·소품 통합</b> 렌더 뷰 모델 (살아있는 정원 게임 Phase 3).
 *
 * <p>식물({@link PlacedPlant})과 소품({@link GardenDecorationPlacement})은 별 테이블에 저장되지만, 화면에선 한
 * 캔버스에 섞여 그려지고 {@code zOrder}도 통합 단일 스케일이라(연못은 식물 뒤·벤치는 식물 앞) <b>병합 정렬</b>이
 * 필요하다(설계 §2 결정 ③). {@code GardenLayoutService.layoutItemsOf}가 둘을 이 형태로 평탄화해 z 오름차순으로
 * 내주고, 보기 뷰·부트스트랩 JSON이 단일 루프로 렌더한다. 결과는 z 오름차순이라 먼저 그린 것이 뒤, 나중이 앞이다.
 *
 * <p>{@link #kind}로 식물/소품을 구분한다 — 보기 뷰는 식물에 sway 애니메이션({@code .canvas-plant}), 소품엔
 * 정적({@code .canvas-decor})을 입히고, 편집 캔버스는 export 시 종별 배열로 나눈다. 소품은 축이 없어 {@link #axis}가 null이다.
 *
 * @param kind     "plant" 또는 "decor"
 * @param axis     식물의 수집축. 소품이면 null(축 없음)
 * @param code     식물·소품 code
 * @param emoji    비주얼 이모지(SVG 미적용 종의 폴백)
 * @param name     표시 이름(한글)
 * @param spriteId 코드 벡터 SVG 스프라이트 식별자. null이면 이모지로 폴백
 * @param x        가로 위치(정규화 0~1)
 * @param y        세로 위치(정규화 0~1)
 * @param z        겹침 앞뒤 순서(zOrder, 식물·소품 통합)
 * @param rotation 사용자 회전(도, 0~360)
 * @param scale    사용자 크기 배율(0.5~2.0)
 */
public record PlacedItem(String kind, PlacementAxis axis, String code, String emoji, String name, String spriteId,
                         double x, double y, int z, double rotation, double scale) {

    public static final String KIND_PLANT = "plant";
    public static final String KIND_DECOR = "decor";

    /** 배치된 식물을 통합 아이템으로 — axis 보존(편집 export가 식물 배열로 되돌릴 때 필요). */
    public static PlacedItem plant(PlacedPlant p) {
        return new PlacedItem(KIND_PLANT, p.axis(), p.code(), p.emoji(), p.name(), p.spriteId(),
                p.x(), p.y(), p.z(), p.rotation(), p.scale());
    }

    /** 배치된 소품을 통합 아이템으로 — 카탈로그 메타(이모지·이름·스프라이트)를 배치 좌표·변형과 결합. axis는 null(소품은 축 없음). */
    public static PlacedItem decor(Decoration meta, GardenDecorationPlacement gd) {
        return new PlacedItem(KIND_DECOR, null, meta.getCode(), meta.getEmoji(), meta.getName(), meta.getSpriteId(),
                gd.getPosX(), gd.getPosY(), gd.getZOrder(), gd.getRotation(), gd.getScale());
    }

    public double isoLeftPct() { return GardenIsoProjection.screenXPercent(x, y); }
    public double isoTopPct()  { return GardenIsoProjection.screenYPercent(x, y); }
    public int depthZ()        { return (int) Math.round(y() * 10000); }
}
