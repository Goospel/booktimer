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
 * 저장된 책BTI 분석 결과 한 건(책BTI Phase 4 → 히스토리 확장 2026-06-08) — LLM이 만든 성향 서술 + 태그를,
 * 그것을 만든 입력 시그니처와 함께 저장한다.
 *
 * <p><b>사용자당 최대 3행</b>(과거 분석 보존 — 사용자가 비교하고 직접 고를 수 있게). 그중 정확히 0~1행이
 * {@code selected}(=<b>대표</b>)다: 대표 서술만 공개 책방에 노출되고, 교체(eviction)에서 보호된다. "다시 분석"은
 * 새 행을 <b>후보(selected=false)</b>로 추가만 하고 대표를 바꾸지 않는다 — 4행이 되면 대표를 뺀 나머지 중 가장
 * 오래된 후보 1행을 버린다. 첫 분석만 부트스트랩으로 자동 대표가 된다.
 *
 * <p>여전히 <b>파생 캐시</b>다 — 매 조회마다 LLM을 부르지 않는다(GET 진입은 대표를 읽기만 하고, 생성은 "다시
 * 분석" 또는 최초 부트스트랩에서만). 사실(프로필)은 싸게 재집계하므로 저장하지 않고, 비싼 LLM 산출물만 저장한다.
 *
 * <p>{@code tags}는 구분자(개행)로 이어 붙여 한 컬럼에 담는다(소수의 짧은 태그라 별도 테이블은 과함).
 */
@Entity
@Table(name = "reading_personality")
public class ReadingPersonalityCache extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1, 사용자당 최대 3행 — 히스토리). FK(user_id). */
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

    /** 서술 생성 시각(절대 시점) — "분석 시각" 표시·교체(가장 오래된 후보) 판단의 기록. */
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /**
     * 이 행이 <b>대표(selected)</b>인가 — 공개 책방에 노출되고 교체에서 보호되는 단 하나의 행. 유저당 0~1행만 true.
     * 첫 분석은 부트스트랩으로 true가 되고, 이후엔 사용자가 직접 골라야 바뀐다("다시 분석"은 후보로만 추가).
     */
    @Column(nullable = false)
    private boolean selected;

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
        this.selected = false;
    }

    /** 새 분석 행을 만든다(후보 상태 — selected=false). 대표 지정은 {@link #select()}로 따로 한다. */
    public static ReadingPersonalityCache create(User user, String narrative, String tags,
                                                 String inputSignature, Instant generatedAt) {
        return new ReadingPersonalityCache(user, narrative, tags, inputSignature, generatedAt);
    }

    /** 이 행을 대표로 지정한다. 유저당 대표는 1개여야 하므로, 기존 대표 해제는 호출자(서비스)의 책임이다. */
    public void select() {
        this.selected = true;
    }

    /** 대표 지정을 해제한다(후보로 강등). */
    public void deselect() {
        this.selected = false;
    }

    public boolean isSelected() {
        return selected;
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
