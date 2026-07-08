package com.booktimer.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 억제된 이메일 주소 한 건 — SES 반송·불만 피드백으로 등록된다(발송 전 조회로 재발송 차단).
 *
 * <p>{@code email}은 정규화(trim+소문자)된 값으로 저장되며 UNIQUE 제약을 받는다(멱등의 DB 근거).
 */
@Entity
@Table(name = "email_suppression")
public class EmailSuppression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuppressionReason reason;

    @Column(length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected EmailSuppression() {
        // JPA
    }

    public EmailSuppression(String email, SuppressionReason reason, String detail, Instant createdAt) {
        this.email = email;
        this.reason = reason;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public SuppressionReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
