package com.booktimer.push;

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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Web Push 구독 정보 — 사용자 1명이 여러 디바이스(멀티 디바이스)에 구독할 수 있어 1:N.
 *
 * <p>endpoint 는 브라우저가 푸시 서비스(FCM·Mozilla·Apple 등)에서 할당한 고유 URL이다.
 * 유니크 제약({@code uk_push_sub_endpoint})으로 같은 endpoint 중복 행을 막는다.
 * upsert(같은 endpoint 재구독) 시 기존 행의 p256dh·auth를 갱신한다.
 */
@Entity
@Table(name = "push_subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_push_sub_endpoint", columnNames = "endpoint")
})
public class PushSubscription extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 브라우저가 발급한 푸시 서비스 URL. 유니크. */
    @Column(nullable = false, length = 512)
    private String endpoint;

    /** 클라이언트 ECDH 공개키(URL-safe base64). AES-GCM 암호화에 쓰인다. */
    @Column(nullable = false)
    private String p256dh;

    /** 인증 시크릿(URL-safe base64). */
    @Column(nullable = false)
    private String auth;

    /** 마지막 발송 성공 시각. null이면 아직 발송 이력 없음. */
    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    protected PushSubscription() {
        // JPA
    }

    private PushSubscription(User user, String endpoint, String p256dh, String auth) {
        this.user = user;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public static PushSubscription of(User user, String endpoint, String p256dh, String auth) {
        if (user == null) throw new IllegalArgumentException("user must not be null");
        if (endpoint == null || endpoint.isBlank()) throw new IllegalArgumentException("endpoint must not be blank");
        if (p256dh == null || p256dh.isBlank()) throw new IllegalArgumentException("p256dh must not be blank");
        if (auth == null || auth.isBlank()) throw new IllegalArgumentException("auth must not be blank");
        return new PushSubscription(user, endpoint, p256dh, auth);
    }

    /** 재구독 시 키 정보를 갱신한다(endpoint는 동일, 키만 교체). */
    public void updateKeys(String p256dh, String auth) {
        this.p256dh = p256dh;
        this.auth = auth;
    }

    /** 발송 성공 시각을 기록한다. */
    public void recordSuccess(Instant when) {
        this.lastSuccessAt = when;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
}
