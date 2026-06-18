package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 독서 정원의 <b>출판사 건물</b> 카탈로그 한 종 (읽기 전용·정적 시드, V46).
 *
 * <p>트랙 A의 여섯 번째 수집축이다 — <b>특정 출판사의 책을 N권 완독</b>하면 그 출판사 건물을 보유로
 * <b>유도</b>한다(부채 모델 N-058 — 보유 이력 저장 안 함). 다양성축({@link DiversityPlant})의
 * "distinct 출판사 수 ≥ 임계"와 달리 이 축은 <b>특정 출판사를 몇 권 읽었는가</b>를 묻는다.
 *
 * <p>매칭은 부분 정규화(contains): {@link BuildingUnlockCalculator#normalize}로 정규화한
 * 완독책 출판사명 중 {@code matchName}을 contains하는 것의 권수 합이 {@code thresholdCount} 이상이면 보유.
 *
 * <p>세터가 없어 시드(V46)로만 채워지는 불변 카탈로그다.
 */
@Entity
@Table(name = "building")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드(시드·배치 참조용). 유니크. */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * 매칭 기준 출판사명(예: "민음사", "문학동네").
     * {@link BuildingUnlockCalculator#normalize}로 정규화한 뒤 완독책 출판사 정규화 결과에 contains로 검색한다.
     */
    @Column(name = "match_name", nullable = false, length = 200)
    private String matchName;

    /** 표시 이름(예: "민음사"). */
    @Column(nullable = false, length = 100)
    private String name;

    /** 비주얼 이모지(utf8mb4). */
    @Column(nullable = false, length = 20)
    private String emoji;

    /** 완독 권수 임계 — 이 수 이상 완독 시 보유. */
    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount;

    /** 도감 진열 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 코드 벡터 SVG 스프라이트 식별자(저폴리 아트 후속). null이면 이모지로 폴백한다.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    protected Building() {
        // JPA
    }

    private Building(String code, String matchName, String name, String emoji,
                     int thresholdCount, int displayOrder, String spriteId) {
        this.code = code;
        this.matchName = matchName;
        this.name = name;
        this.emoji = emoji;
        this.thresholdCount = thresholdCount;
        this.displayOrder = displayOrder;
        this.spriteId = spriteId;
    }

    /**
     * 건물 한 종을 만든다 — 운영은 시드(V46)로 채우므로 주 용도는 순수 도메인 테스트다
     * (패키지 안으로 노출 제한). {@code spriteId}는 nullable(이모지 폴백).
     */
    static Building of(String code, String matchName, String name, String emoji,
                       int thresholdCount, int displayOrder, String spriteId) {
        return new Building(code, matchName, name, emoji, thresholdCount, displayOrder, spriteId);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getMatchName() { return matchName; }
    public String getName() { return name; }
    public String getEmoji() { return emoji; }
    public int getThresholdCount() { return thresholdCount; }
    public int getDisplayOrder() { return displayOrder; }
    public String getSpriteId() { return spriteId; }
}
