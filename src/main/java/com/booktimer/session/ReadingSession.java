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
 * 한 번의 독서 측정 기록. User와 N:1.
 *
 * <p>{@link #start(User, Instant)}로 시작(진행 중)하고 {@link #end(Instant)}로 종료한다.
 * 종료 시 {@code durationSeconds = endedAt - startedAt}을 계산하며, 이 값이 추후
 * ReadingTimer 의 누적 잔여(remainingSeconds) 차감에 쓰인다(다음 증분).
 *
 * <p>설계상 책 단위 기록(@ManyToOne Book)은 추후 확장점이라 이 증분에서는 다루지 않는다.
 */
@Entity
@Table(name = "reading_session")
public class ReadingSession extends BaseTimeEntity {

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

    protected ReadingSession() {
        // JPA
    }

    private ReadingSession(User user, Instant startedAt) {
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
     * 새 측정 세션을 시작한다. 종료 전(진행 중) 상태로 생성된다.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 시작 시각(필수)
     * @throws IllegalArgumentException user/startedAt 이 null 인 경우
     */
    public static ReadingSession start(User user, Instant startedAt) {
        return new ReadingSession(user, startedAt);
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
