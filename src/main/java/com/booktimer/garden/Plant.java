package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 독서 정원의 식물 카탈로그 한 종 (읽기 전용·정적 시드).
 *
 * <p>트랙 A(타이머 할당량) 1단계 설계의 핵심: <b>보유 식물을 저장하지 않는다</b>(부채 모델 N-058·
 * Lazy accrual N-001과 같은 철학). 어떤 식물을 가졌는지는 순수하게 독서 실적의 함수 —
 * {@code 누적 목표 달성일 ≥ unlockThresholdDays}면 보유로 <b>유도</b>한다. 따라서 보유 이력
 * 테이블({@code UserPlant})은 없고, 데이터 모델은 이 카탈로그(시드 V35) 하나뿐이다.
 *
 * <p>해금은 임계 오름차순으로 순차적이라(다음 식물 예측 동기), 조회는
 * {@code unlockThresholdDays} 오름차순으로 정렬해 쓴다. 비주얼은 1차엔 이모지(SVG 후속).
 * 세터가 없어 시드로만 채워지는 불변 카탈로그다.
 */
@Entity
@Table(name = "plant")
public class Plant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드(시드·후속 참조용). 유니크. */
    @Column(nullable = false, unique = true)
    private String code;

    /** 표시 이름(한글). */
    @Column(nullable = false)
    private String name;

    /** 비주얼 이모지(utf8mb4). */
    @Column(nullable = false)
    private String emoji;

    /** 도감 진열 순서(임계가 같을 때의 보조 정렬). */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 해금 임계 — 누적 목표 달성일이 이 값 이상이면 보유. */
    @Column(name = "unlock_threshold_days", nullable = false)
    private long unlockThresholdDays;

    /**
     * 코드 벡터 SVG 스프라이트 식별자(A2). null이면 이모지로 폴백한다 — 정상 상태다(아직 SVG화 안 된 종).
     * 뷰는 {@code #sprite-{spriteId}} 심볼을 {@code <use>}로 참조한다. 시드(V40)에서만 채워지는 불변 메타.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    protected Plant() {
        // JPA
    }

    private Plant(String code, String name, String emoji, int displayOrder, long unlockThresholdDays, String spriteId) {
        this.code = code;
        this.name = name;
        this.emoji = emoji;
        this.displayOrder = displayOrder;
        this.unlockThresholdDays = unlockThresholdDays;
        this.spriteId = spriteId;
    }

    /**
     * 카탈로그 한 종을 코드로 만든다 — 운영은 시드(V35·V40)로 채우므로 주 용도는 순수 도메인 테스트다
     * (패키지 안으로 노출 제한). 보유는 저장하지 않고 유도하므로 식물 인스턴스 자체는 불변 메타다.
     * {@code spriteId}는 nullable(이모지 폴백) — SVG 미적용 종은 null로 전달한다(A2).
     */
    static Plant of(String code, String name, String emoji, int displayOrder, long unlockThresholdDays, String spriteId) {
        return new Plant(code, name, emoji, displayOrder, unlockThresholdDays, spriteId);
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

    public int getDisplayOrder() {
        return displayOrder;
    }

    public long getUnlockThresholdDays() {
        return unlockThresholdDays;
    }

    /** 코드 벡터 SVG 스프라이트 식별자 — null이면 이모지 폴백(A2). */
    public String getSpriteId() {
        return spriteId;
    }
}
