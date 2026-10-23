package com.booktimer.writing;

import com.booktimer.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 글쓰기 AI 월 원가 한 줄 — 「이 UTC 달에 서비스 전체가 쓴 micro-USD」(V103).
 *
 * <p>{@code StudyAiDailyTotal}과 같은 규율이다: 가산은 조건부 UPDATE(원자적)가 맡고, UNIQUE를 <b>엔티티에도</b> 선언한다
 * (메인 테스트 스위트는 Hibernate가 H2 스키마를 만들어 마이그레이션의 제약을 모른다).
 */
@Entity
@Table(name = "ai_spend_month",
        uniqueConstraints = @UniqueConstraint(name = "uq_ai_spend_month", columnNames = {"spend_month"}))
public class AiSpendMonth extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code yyyy-MM}(UTC). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "spend_month", nullable = false, length = 7)
    private String spendMonth;

    @Column(name = "micro_usd", nullable = false)
    private long microUsd;

    protected AiSpendMonth() {
        // JPA
    }

    static AiSpendMonth of(String spendMonth) {
        AiSpendMonth row = new AiSpendMonth();
        row.spendMonth = spendMonth;
        return row;
    }

    public String getSpendMonth() {
        return spendMonth;
    }

    public long getMicroUsd() {
        return microUsd;
    }
}
