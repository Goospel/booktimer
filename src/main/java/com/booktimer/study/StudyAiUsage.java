package com.booktimer.study;

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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * AI 하루 상한 카운터 한 줄 — 「이 사람이 이 날 이 종류를 몇 번 썼나」.
 *
 * <p>호출 <b>로그</b>가 아니라 <b>가감 가능한 카운터</b>다. 외부 호출이 실패하면 되돌려야 하기 때문이다
 * (장애로 사용자가 오늘 몫을 잃으면 안 된다) — 로그였다면 「지운다」가 되는데, 그건 실패와 성공을 사후에
 * 구분할 수 없게 만든다.
 *
 * <p>UNIQUE를 <b>엔티티에도</b> 선언한 것이 중요하다: 메인 테스트 스위트는 Hibernate가 H2 스키마를 만들어
 * 마이그레이션의 제약을 모른다. 여기 없으면 경합 테스트가 「INSERT 두 개가 다 성공하는」 세계에서 돌아
 * 실제 배치를 재지 못한다.
 */
@Entity
@Table(name = "study_ai_usage",
        uniqueConstraints = @UniqueConstraint(name = "uq_study_ai_usage",
                columnNames = {"user_id", "usage_date", "kind"}))
public class StudyAiUsage extends BaseTimeEntity {

    /**
     * AI 호출 종류와 <b>하루 몫</b>.
     *
     * <p>상수이고 설정이 아니다 — 사용자별·환경별로 달라질 요구가 없고, 설정으로 빼면 「운영에서 몇으로
     * 켜져 있나」를 코드에서 못 읽는다. 분석이 1인 것은 「하루 한 장」이라는 백지복습의 형태 자체다.
     */
    public enum Kind {
        PLAN(3),
        TRANSCRIBE(3),
        ANALYZE(1);

        private final int max;

        Kind(int max) {
            this.max = max;
        }

        public int max() {
            return max;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 상한의 날짜 키 — <b>호출한 날</b>(유저 tz)이다. 대상 글의 날짜가 아니다. */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false)
    private int used;

    protected StudyAiUsage() {
        // JPA
    }

    private StudyAiUsage(User user, LocalDate usageDate, Kind kind) {
        this.user = user;
        this.usageDate = usageDate;
        this.kind = kind;
        this.used = 0;
    }

    /** 아직 아무것도 안 쓴 새 카운터 — 증가는 조건부 UPDATE(원자적)가 맡는다. */
    public static StudyAiUsage of(User user, LocalDate usageDate, Kind kind) {
        return new StudyAiUsage(user, usageDate, kind);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public Kind getKind() {
        return kind;
    }

    public int getUsed() {
        return used;
    }
}
