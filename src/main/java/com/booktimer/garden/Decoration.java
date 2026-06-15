package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 독서 정원 꾸미기 <b>소품</b> 카탈로그 한 종 (읽기 전용·정적 시드 — 살아있는 정원 게임 Phase 3).
 *
 * <p>식물({@link Plant} 등 4축)이 독서 실적으로 <b>해금</b>하는 수집 대상이라면, 소품은 길·연못·울타리·벤치처럼
 * <b>보유 무관</b>한 장식 오브젝트다 — 누구나 쓰고, 같은 소품을 여러 개 놓는다(설계 §1). 그래서 해금 임계가 없고
 * (식물의 {@code unlockThresholdDays}/{@code thresholdCount} 대응물 없음), 배치도 별 테이블
 * ({@link GardenDecorationPlacement})에 중복 허용으로 저장한다.
 *
 * <p>비주얼은 {@link #spriteId}(코드 벡터 SVG) 우선, null이면 {@link #emoji}로 폴백한다 — 식물과 동일한 불변식.
 * {@link #category}는 팔레트 그룹화(미래용)를 위한 분류로, Phase 3은 평면 팔레트라 진열 순서({@link #displayOrder})만
 * 쓴다. 세터가 없어 시드(V44)로만 채워지는 불변 카탈로그다.
 */
@Entity
@Table(name = "decoration")
public class Decoration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드(시드·배치 참조용). 유니크. */
    @Column(nullable = false, unique = true)
    private String code;

    /** 표시 이름(한글). */
    @Column(nullable = false)
    private String name;

    /** 비주얼 이모지(utf8mb4) — SVG 미적용 종의 폴백. */
    @Column(nullable = false)
    private String emoji;

    /**
     * 코드 벡터 SVG 스프라이트 식별자. null이면 이모지로 폴백한다 — 식물과 동일 불변식.
     * 뷰는 {@code #sprite-{spriteId}} 심볼을 {@code <use>}로 참조한다(fragments/garden-decor-sprites). 시드(V44)에서만 채워지는 불변 메타.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    /** 소품 분류(PATH/WATER/FENCE/FURNITURE/NATURE) — 팔레트 그룹화(미래용). Phase 3은 평면 팔레트라 표시엔 미사용. */
    @Column(nullable = false, length = 20)
    private String category;

    /** 팔레트·도감 진열 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected Decoration() {
        // JPA
    }

    private Decoration(String code, String name, String emoji, String spriteId, String category, int displayOrder) {
        this.code = code;
        this.name = name;
        this.emoji = emoji;
        this.spriteId = spriteId;
        this.category = category;
        this.displayOrder = displayOrder;
    }

    /**
     * 소품 한 종을 만든다 — 운영은 시드(V44)로 채우므로 주 용도는 순수 도메인 테스트다(패키지 안으로 노출 제한).
     * 보유 무관이라 소품 인스턴스 자체는 불변 메타다. {@code spriteId}는 nullable(이모지 폴백).
     */
    static Decoration of(String code, String name, String emoji, String spriteId, String category, int displayOrder) {
        return new Decoration(code, name, emoji, spriteId, category, displayOrder);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getEmoji() {
        return emoji;
    }

    /** 코드 벡터 SVG 스프라이트 식별자 — null이면 이모지 폴백. */
    public String getSpriteId() {
        return spriteId;
    }

    public String getCategory() {
        return category;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
