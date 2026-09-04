package com.booktimer.user;

import com.booktimer.common.BaseTimeEntity;
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
 * 기존 계정 ↔ 토스 신원을 잇는 <b>일회용 연결 코드</b> 한 건(테이블 {@code toss_link_code}).
 *
 * <p>웹에 로그인해 있다는 사실 자체가 "이 계정의 주인" 증명이므로, 로그인 상태에서 발급한 코드를 미니앱에
 * 입력하면 그 계정에 토스 신원이 붙는다(설계 §2.2 ⓐ). 이메일 자동 매칭(탈취 벡터)과 미니앱 비밀번호 입력
 * (GOOGLE 계정 불가)을 모두 피하는 방식이다.
 *
 * <p>코드가 짧으므로 방어는 <b>짧은 TTL + 일회용 + 레이트리밋</b> 세 겹이다. 평문은 화면에만 노출하고
 * DB엔 SHA-256 해시만 둔다({@code EmailToken}과 같은 성질).
 */
@Entity
@Table(name = "toss_link_code")
public class TossLinkCode extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 코드를 발급한 사용자 — 연결의 대상 계정이다. FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 평문 코드의 SHA-256 해시(hex 64자). 평문은 저장하지 않는다. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    /** 만료 시각(절대 시점). 이 시각 이후면 소비 거부. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** 소비(사용) 시각. null이면 미사용 — 한 번 소비되면 찍혀 재사용을 막는다(일회용). */
    @Column(name = "used_at")
    private Instant usedAt;

    /**
     * 이 코드가 무엇에 쓰이는가 — 소비 지점이 <b>자기 목적만</b> 받는다(V86).
     *
     * <p>구분이 없으면 웹→토스 연결 코드를 웹 로그인 폼에 먹여 그 계정 세션을 열 수 있다. 코드를 본
     * 사람(어깨너머·스크린샷)이 얻는 힘이 "내 토스 신원을 붙임"에서 "즉시 웹 로그인"으로 넓어진다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 16)
    private Purpose purpose;

    /** 코드의 방향. 한 사용자가 두 목적의 코드를 동시에 가질 수 없다(발급 조건이 상호 배타 — 서비스 주석). */
    public enum Purpose {
        /** 웹 → 토스: 웹에 로그인한 사람이 발급 → 미니앱에 입력해 그 계정에 토스 신원을 붙인다. */
        LINK_TOSS,
        /** 토스 → 웹: 미니앱에서 발급 → PC 웹 로그인 화면에 입력해 그 계정의 세션을 연다. */
        WEB_LOGIN
    }

    protected TossLinkCode() {
        // JPA
    }

    private TossLinkCode(User user, String codeHash, Instant expiresAt, Purpose purpose) {
        this.user = user;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.purpose = purpose;
    }

    /** 코드를 발급한다 — 해시·만료·목적만 받는다(평문 생성·해싱은 {@link TossLinkCodeService} 책임). */
    public static TossLinkCode issue(User user, String codeHash, Instant expiresAt, Purpose purpose) {
        return new TossLinkCode(user, codeHash, expiresAt, purpose);
    }

    /** {@code now} 기준으로 소비 가능한가 — 미사용 + 미경과. */
    public boolean isConsumableAt(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    /** 소비 처리(일회용) — {@code usedAt}을 찍는다. */
    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Purpose getPurpose() {
        return purpose;
    }
}
