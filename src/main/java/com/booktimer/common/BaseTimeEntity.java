package com.booktimer.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 생성/수정 시각 auditing 공통 베이스.
 *
 * <p>{@link AuditingEntityListener}가 persist 시 {@code createdAt}, persist/update 시
 * {@code updatedAt}을 자동으로 채운다. 동작하려면 설정에 {@code @EnableJpaAuditing}이 있어야 한다
 * ({@link com.booktimer.config.JpaConfig}). 시각은 타임존 무관한 {@link Instant}로 저장한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
