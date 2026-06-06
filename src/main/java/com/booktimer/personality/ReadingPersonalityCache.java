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
 * 캐시된 책BTI 결과(책BTI Phase 4) — LLM이 만든 성향 서술 + 태그를, 그것을 만든 입력 시그니처와 함께 저장한다.
 *
 * <p>사용자당 하나(user_id unique). 매 조회마다 LLM을 부르지 않기 위한 <b>파생 캐시</b>다 — 책장이 의미있게 변하면
 * ({@code inputSignature} 불일치) 또는 "다시 분석" 요청 시에만 재생성한다(비용=호출 빈도를 바닥으로). 사실(프로필)
 * 자체는 싸게 재집계하므로 저장하지 않고, 비싼 LLM 산출물만 저장한다. 결과 일관성(캡처·공유)도 캐시가 보장한다.
 *
 * <p>{@code tags}는 구분자(개행)로 이어 붙여 한 컬럼에 담는다(소수의 짧은 태그라 별도 테이블은 과함).
 */
@Entity
@Table(name = "reading_personality")
public class ReadingPersonalityCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1, 사용자당 1행 — user_id unique). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** MBTI 설명문체 서술(한 문단). */
    @Column(nullable = false, length = 4000)
    private String narrative;

    /** 태그 — 개행으로 이어 붙인 문자열(없으면 null/빈 문자열). */
    @Column(length = 500)
    private String tags;

    /** 이 서술을 만든 책장 상태의 시그니처(SHA-256 hex). 현재 프로필 시그니처와 다르면 재생성. */
    @Column(name = "input_signature", nullable = false, length = 64)
    private String inputSignature;

    /** 서술 생성 시각(절대 시점) — "분석 시각" 표시·재생성 판단의 기록. */
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ReadingPersonalityCache() {
        // JPA
    }

    private ReadingPersonalityCache(User user, String narrative, String tags,
                                    String inputSignature, Instant generatedAt) {
        this.user = user;
        this.narrative = narrative;
        this.tags = tags;
        this.inputSignature = inputSignature;
        this.generatedAt = generatedAt;
    }

    /** 새 캐시 항목을 만든다. */
    public static ReadingPersonalityCache create(User user, String narrative, String tags,
                                                 String inputSignature, Instant generatedAt) {
        return new ReadingPersonalityCache(user, narrative, tags, inputSignature, generatedAt);
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
