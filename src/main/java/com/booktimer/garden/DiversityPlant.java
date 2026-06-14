package com.booktimer.garden;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 독서 정원의 <b>작가·출판사 다양성</b> 식물 카탈로그 한 종 (읽기 전용·정적 시드).
 *
 * <p>트랙 A의 세 번째 수집축이다 — 시간축 {@link Plant}(누적 목표 달성일)·장르축 {@link GenrePlant}
 * (장르 라벨 1:1 매칭)과 또 다른 메커닉으로, <b>완독한 서로 다른(distinct) 작가/출판사 수</b>가
 * {@link #thresholdCount} 이상이면 그 식물을 보유로 <b>유도</b>한다(부채 모델 N-058 — 보유 이력 저장 안 함).
 *
 * <p>작가·출판사를 별도 테이블로 나누지 않고 {@link #kind}로 구분하는 건 둘이 같은 카운트 메커닉이기
 * 때문이다(설계 §2.2). 장르축이 별도였던 건 라벨 매칭이라 메커닉이 달라서였고, 이건 시간축처럼 "누적 수 ≥ 임계"라
 * 동형이다. 작가 표기가 미정규화라 1:1 라벨 시드가 불가·불신(설계 §2.1)이라 "다양성 카운트"를 택했다.
 *
 * <p>해금은 임계 오름차순으로 순차적이라(다음 식물 예측 동기), 같은 {@code kind} 안에서 임계 오름차순으로
 * 진열한다. 세터가 없어 시드(V38)로만 채워지는 불변 카탈로그다.
 */
@Entity
@Table(name = "diversity_plant")
public class DiversityPlant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 안정 식별 코드(시드·후속 참조용). 유니크. */
    @Column(nullable = false, unique = true)
    private String code;

    /** 무엇의 다양성인가(작가/출판사) — 판정 시 이 종류의 카운트로 임계를 본다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiversityKind kind;

    /** 해금 임계 — 완독한 distinct 작가/출판사 수가 이 값 이상이면 보유. */
    @Column(name = "threshold_count", nullable = false)
    private int thresholdCount;

    /** 표시 이름(한글). */
    @Column(nullable = false)
    private String name;

    /** 비주얼 이모지(utf8mb4). */
    @Column(nullable = false)
    private String emoji;

    /** 도감 진열 순서. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 코드 벡터 SVG 스프라이트 식별자(A2 후속). null이면 이모지로 폴백한다 — 정상 상태다(아직 SVG화 안 된 종).
     * 뷰는 {@code #sprite-{spriteId}} 심볼을 {@code <use>}로 참조한다. 시드(V41)에서만 채워지는 불변 메타.
     */
    @Column(name = "sprite_id", length = 50)
    private String spriteId;

    protected DiversityPlant() {
        // JPA
    }

    private DiversityPlant(String code, DiversityKind kind, int thresholdCount,
                           String name, String emoji, int displayOrder, String spriteId) {
        this.code = code;
        this.kind = kind;
        this.thresholdCount = thresholdCount;
        this.name = name;
        this.emoji = emoji;
        this.displayOrder = displayOrder;
        this.spriteId = spriteId;
    }

    /**
     * 다양성 식물 한 종을 만든다 — 운영은 시드(V38)로 채우므로 주 용도는 순수 도메인 테스트다
     * (패키지 안으로 노출 제한). 보유는 저장하지 않고 유도하므로 식물 인스턴스 자체는 불변 메타다.
     * {@code spriteId}는 nullable(이모지 폴백) — SVG 미적용 종은 null로 전달한다(A2 후속).
     */
    static DiversityPlant of(String code, DiversityKind kind, int thresholdCount,
                             String name, String emoji, int displayOrder, String spriteId) {
        return new DiversityPlant(code, kind, thresholdCount, name, emoji, displayOrder, spriteId);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public DiversityKind getKind() {
        return kind;
    }

    public int getThresholdCount() {
        return thresholdCount;
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

    /** 코드 벡터 SVG 스프라이트 식별자 — null이면 이모지 폴백(A2 후속). */
    public String getSpriteId() {
        return spriteId;
    }
}
