package com.booktimer.email;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 이메일 토큰 한 건(테이블 {@code email_token}) — 가입 인증·비밀번호 재설정 링크에 실리는 일회용 토큰.
 *
 * <p><b>평문 미저장</b>: 평문 토큰은 메일 링크({@code ?token=})에만 실리고, DB엔 그 SHA-256 해시만 둔다
 * ({@link #tokenHash}). DB가 유출돼도 평문 토큰을 역산할 수 없어 링크를 위조하지 못한다(비밀번호 해시와 같은 정신).
 *
 * <p>검증 통과 조건(모두 만족해야): 해시 일치 · {@code type} 일치 · {@code expiresAt} 미경과 · {@code usedAt == null}
 * (일회용). 소비 시 {@link #markUsed}로 {@code usedAt}을 찍어 재사용을 막는다.
 */
@Entity
@Table(name = "email_token")
public class EmailToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailTokenType type;

    /** 평문 토큰의 SHA-256 해시(hex 64자). 평문은 저장하지 않는다. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** 만료 시각(절대 시점). 이 시각 이후면 소비 거부. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 소비(사용) 시각. null이면 미사용 — 한 번 소비되면 찍혀 재사용을 막는다(일회용). */
    @Column(name = "used_at")
    private Instant usedAt;

    protected EmailToken() {
        // JPA
    }

    private EmailToken(User user, EmailTokenType type, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.type = type;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /**
     * 토큰을 발급한다 — 해시·만료를 받아 미사용 상태로 만든다(평문 생성·해싱은 {@link EmailTokenService} 책임).
     */
    public static EmailToken issue(User user, EmailTokenType type, String tokenHash, Instant expiresAt) {
        return new EmailToken(user, type, tokenHash, expiresAt);
    }

    /** 이 토큰이 {@code now} 기준으로 소비 가능한가 — 미사용 + 미경과. (type/해시 일치는 조회 단계에서 본다.) */
    public boolean isConsumableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** 소비 처리(일회용) — {@code usedAt}을 찍는다. 멱등이 아니라 한 번만 의미 있다(이미 찍혔으면 소비 불가였음). */
    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public EmailTokenType getType() {
        return type;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
