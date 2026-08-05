package com.booktimer.auth;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 미니앱(앱인토스)이 쓰는 <b>불투명 API 토큰</b> 한 건(테이블 {@code api_token}).
 *
 * <p>JWT가 아니다 — 새 의존성 없이 되고, 무엇보다 <b>즉시 폐기(logout)</b>가 가능하다(설계 §2.1).
 * 평문 토큰은 발급 응답에만 실리고 DB엔 SHA-256 해시만 둔다({@code token_hash}) — DB가 유출돼도
 * 평문을 역산해 남의 세션을 쓸 수 없다({@code EmailToken}과 같은 정신).
 *
 * <p>검증 통과 조건: 해시 일치 · {@code expiresAt} 미경과. 폐기는 행 삭제로 한다(만료 전 무효화의
 * 단일 수단 — {@code used_at} 같은 상태를 따로 두면 "살아있는 토큰"의 정의가 둘로 갈린다).
 */
@Entity
@Table(name = "api_token")
public class ApiToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1 — 기기별로 여러 토큰). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 평문 토큰의 SHA-256 해시(hex 64자). 평문은 저장하지 않는다. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** 만료 시각(절대 시점). 이 시각 이후면 인증 거부 — 재로그인은 토스 {@code appLogin()}이라 마찰이 거의 0이다. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 마지막 사용 시각. 유휴 토큰 식별·운영 관찰용이며 인증 판정에는 쓰지 않는다(슬라이딩 연장은 v2). */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiToken() {
        // JPA
    }

    private ApiToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** 토큰을 발급한다 — 해시·만료만 받는다(평문 생성·해싱은 {@link ApiTokenService} 책임). */
    public static ApiToken issue(User user, String tokenHash, Instant expiresAt) {
        return new ApiToken(user, tokenHash, expiresAt);
    }

    /** {@code now} 기준으로 아직 살아있는가(미경과). */
    public boolean isValidAt(Instant now) {
        return now.isBefore(expiresAt);
    }

    /** 사용 시각을 기록한다(인증 성공 시). */
    public void touch(Instant now) {
        this.lastUsedAt = now;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
