package com.booktimer.personality;

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
 * 책방 노출용 <b>공개 책BTI</b> 캐시(책BTI Phase 6) — 본인용 캐시({@link ReadingPersonalityCache})와 구조는 같되
 * <b>입력 책 범위가 다르다</b>(공개+완독 책만)서 별도 테이블(reading_personality_public)에 따로 저장한다.
 *
 * <p>왜 별도 테이블인가: 본인용은 전체 책 기반, 공개용은 PUBLIC 책 기반 — 같은 사용자라도 두 결과가 공존하며 서로
 * 다르다(reading-personality-design §7). 한 테이블에 scope 컬럼으로 합치면 V19의 user_id unique 제약을 (user_id,
 * scope) 복합으로 바꿔야 하는데, 그 제약 드롭 SQL이 MySQL(DROP INDEX)·H2(DROP CONSTRAINT)에서 갈려 이식이
 * 위험하다. 별도 테이블은 CREATE만 하므로(V19와 동일) 양쪽에서 안전하고, 본인용 경로·테스트를 건드리지 않는다.
 *
 * <p>방문자 조회는 이 캐시를 <b>읽기만</b> 한다 — 생성/갱신(LLM 호출)은 소유자 행동(토글 ON·"다시 분석")에서만.
 */
@Entity
@Table(name = "reading_personality_public")
public class PublicReadingPersonalityCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1, 사용자당 1행 — user_id unique). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** MBTI 설명문체 서술(한 문단) — 공개 책 기준. */
    @Column(nullable = false, length = 4000)
    private String narrative;

    /** 태그 — 개행으로 이어 붙인 문자열(없으면 null/빈 문자열). */
    @Column(length = 500)
    private String tags;

    /** 이 서술을 만든 <b>공개 책장</b> 상태의 시그니처(SHA-256 hex). 현재와 다르면 재생성. */
    @Column(name = "input_signature", nullable = false, length = 64)
    private String inputSignature;

    /** 서술 생성 시각(절대 시점). */
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected PublicReadingPersonalityCache() {
        // JPA
    }

    private PublicReadingPersonalityCache(User user, String narrative, String tags,
                                          String inputSignature, Instant generatedAt) {
        this.user = user;
        this.narrative = narrative;
        this.tags = tags;
        this.inputSignature = inputSignature;
        this.generatedAt = generatedAt;
    }

    /** 새 공개 캐시 항목을 만든다. */
    public static PublicReadingPersonalityCache create(User user, String narrative, String tags,
                                                       String inputSignature, Instant generatedAt) {
        return new PublicReadingPersonalityCache(user, narrative, tags, inputSignature, generatedAt);
    }

    /** 재생성된 서술로 기존 캐시를 갱신한다(같은 행 유지 — user_id unique 보존). */
    public void refresh(String narrative, String tags, String inputSignature, Instant generatedAt) {
        this.narrative = narrative;
        this.tags = tags;
        this.inputSignature = inputSignature;
        this.generatedAt = generatedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getNarrative() {
        return narrative;
    }

    public String getTags() {
        return tags;
    }

    public String getInputSignature() {
        return inputSignature;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
