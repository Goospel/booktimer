package com.booktimer.session;

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

import java.time.Duration;
import java.time.Instant;

/**
 * 한 번의 <b>공부</b> 측정 기록. User와 N:1.
 *
 * <p>{@link ReadingSession}과 불변식은 같지만(시작 → 종료 시 {@code durationSeconds} 계산) 테이블이
 * 다르다 — 그것이 이 기능의 요구 그 자체다. 공부 시간은 잔디·부채·기록·홈피드·책 통계 어디에도 섞이면
 * 안 되는데, 독서 집계 쿼리가 이 테이블을 <b>아예 모르므로</b> 섞일 경로가 구조적으로 없다.
 *
 * <p>독서에 있는 {@code book}·{@code manualEntry}는 없다 — 공부는 책이 없고, 수동 기록은 범위 밖이다.
 *
 * <p><b>한 행은 유저 타임존 하루 안에 있다</b>(신규 저장분 한정). 종료 시각을 확정하는 유스케이스가
 * 자정 경계로 구간을 잘라 조각마다 한 행씩 저장하기 때문이다({@code StudySessionService}) — 날짜 귀속이
 * {@code startedAt}의 날짜라, 자정을 걸친 공부를 한 행으로 두면 통째로 시작일에 잡힌다. 이 엔티티는
 * 그 규칙을 <b>모른다</b>: 조각도 그냥 완료 세션 하나이고 불변식은 조각마다 그대로 성립한다. 분할 도입
 * 전에 저장된 레거시 행은 여전히 자정을 걸칠 수 있다(소급 재분할 없음).
 */
@Entity
@Table(name = "study_session")
public class StudySession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 측정 주체 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 측정 시작 시각(절대 시점). */
    @Column(nullable = false)
    private Instant startedAt;

    /** 측정 종료 시각. 진행 중이면 null. */
    @Column
    private Instant endedAt;

    /** 종료 시 계산된 측정 길이(초). 진행 중이면 0. */
    @Column(nullable = false)
    private long durationSeconds;

    protected StudySession() {
        // JPA
    }

    private StudySession(User user, Instant startedAt) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        this.user = user;
        this.startedAt = startedAt;
        this.endedAt = null;
        this.durationSeconds = 0L;
    }

    /**
     * 새 공부 측정 세션을 시작한다.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 시작 시각(필수)
     * @throws IllegalArgumentException user/startedAt 이 null 인 경우
     */
    public static StudySession start(User user, Instant startedAt) {
        return new StudySession(user, startedAt);
    }

    /**
     * 세션을 종료하고 측정 길이를 계산한다.
     *
     * @param endedAt 종료 시각(필수, startedAt 이상)
     * @throws IllegalArgumentException endedAt 이 null 이거나 startedAt 보다 이른 경우
     * @throws IllegalStateException    이미 종료된 세션인 경우
     */
    public void end(Instant endedAt) {
        if (this.endedAt != null) {
            throw new IllegalStateException("session already ended at " + this.endedAt);
        }
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt must not be null");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        this.endedAt = endedAt;
        this.durationSeconds = Duration.between(startedAt, endedAt).toSeconds();
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
