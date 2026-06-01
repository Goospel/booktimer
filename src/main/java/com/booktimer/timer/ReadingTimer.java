package com.booktimer.timer;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 사용자별 독서 누적 상태 + 설정.
 *
 * <p>"매일 목표가 증가값만큼 늘고, 안 채운 잔여가 다음 날로 이월된다"(N-001)는
 * 핵심 규칙의 상태를 보관한다. 갱신은 배치가 아니라 접속 시 {@link #accrueUntil(LocalDate)}
 * 로 경과 일수만큼 소급 계산(Lazy)한다.
 *
 * <p>User 와는 1:1 — 이 엔티티가 FK(user_id)를 소유한다. 신규 사용자용 타이머는
 * {@link #startFor(User, long, long, LocalDate)} 로 생성한다.
 */
@Entity
public class ReadingTimer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 현재 갚아야 할 누적 잔여(초). */
    @Column(nullable = false)
    private long remainingSeconds;

    /** 하루 증가값(초). 기본 1시간, 사용자 설정 가능. */
    @Column(nullable = false)
    private long dailyIncrementSeconds;

    /** 누적 상한(초). 잔여 총합이 이 값을 넘지 않는다. */
    @Column(nullable = false)
    private long capSeconds;

    /** 마지막 누적 계산 기준일(사용자 타임존). */
    @Column(nullable = false)
    private LocalDate lastAccrualDate;

    /** 소유 사용자 (1:1). FK(user_id)는 이 테이블이 소유한다. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    protected ReadingTimer() {
        // JPA
    }

    private ReadingTimer(long dailyIncrementSeconds, long capSeconds,
                         long remainingSeconds, LocalDate lastAccrualDate) {
        if (dailyIncrementSeconds < 0) {
            throw new IllegalArgumentException("dailyIncrementSeconds must be >= 0");
        }
        if (capSeconds < 0) {
            throw new IllegalArgumentException("capSeconds must be >= 0");
        }
        if (remainingSeconds < 0) {
            throw new IllegalArgumentException("remainingSeconds must be >= 0");
        }
        if (lastAccrualDate == null) {
            throw new IllegalArgumentException("lastAccrualDate must not be null");
        }
        this.dailyIncrementSeconds = dailyIncrementSeconds;
        this.capSeconds = capSeconds;
        this.remainingSeconds = remainingSeconds;
        this.lastAccrualDate = lastAccrualDate;
    }

    /**
     * 설정/상태를 직접 지정해 생성한다 (테스트 및 복원용).
     */
    public static ReadingTimer of(long dailyIncrementSeconds, long capSeconds,
                                  long remainingSeconds, LocalDate lastAccrualDate) {
        return new ReadingTimer(dailyIncrementSeconds, capSeconds, remainingSeconds, lastAccrualDate);
    }

    /**
     * 신규 사용자를 위한 타이머를 생성한다. 잔여는 0에서 시작하고 기준일은 {@code startDate}.
     *
     * @param user                  소유 사용자(필수)
     * @param dailyIncrementSeconds 하루 증가값(초)
     * @param capSeconds            누적 상한(초)
     * @param startDate             누적 시작 기준일(사용자 타임존)
     * @throws IllegalArgumentException user 가 null 이거나 설정값이 음수인 경우
     */
    public static ReadingTimer startFor(User user, long dailyIncrementSeconds,
                                        long capSeconds, LocalDate startDate) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        ReadingTimer timer = new ReadingTimer(dailyIncrementSeconds, capSeconds, 0L, startDate);
        timer.user = user;
        return timer;
    }

    /**
     * {@code lastAccrualDate ~ today} 경과 일수만큼 잔여를 소급 누적하고
     * 기준일을 today 로 전진시킨다. 경과가 0 이하(같은 날/시계 역행)면 아무것도 하지 않는다.
     *
     * @param today 사용자 타임존 기준 오늘 날짜
     */
    public void accrueUntil(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today must not be null");
        }
        long daysElapsed = ChronoUnit.DAYS.between(lastAccrualDate, today);
        if (daysElapsed <= 0) {
            return;
        }
        this.remainingSeconds = AccrualCalculator.accrue(
                remainingSeconds, dailyIncrementSeconds, capSeconds, daysElapsed);
        this.lastAccrualDate = today;
    }

    /**
     * 사용자가 설정 페이지에서 하루 증가값/누적 상한(cap)을 변경한다.
     *
     * <p>cap을 현재 잔여보다 낮게 바꾸면 불변식({@code remainingSeconds <= capSeconds})을 지키기
     * 위해 잔여를 새 cap으로 즉시 클램프한다 — accrue가 항상 cap 이하로 유지하는 것과 같은 규칙.
     *
     * @param dailyIncrementSeconds 새 하루 증가값(초, 0 이상)
     * @param capSeconds            새 누적 상한(초, 0 이상)
     * @throws IllegalArgumentException 값이 음수인 경우
     */
    public void updateSettings(long dailyIncrementSeconds, long capSeconds) {
        if (dailyIncrementSeconds < 0) {
            throw new IllegalArgumentException("dailyIncrementSeconds must be >= 0");
        }
        if (capSeconds < 0) {
            throw new IllegalArgumentException("capSeconds must be >= 0");
        }
        this.dailyIncrementSeconds = dailyIncrementSeconds;
        this.capSeconds = capSeconds;
        if (this.remainingSeconds > capSeconds) {
            this.remainingSeconds = capSeconds;
        }
    }

    /**
     * 세션 측정량만큼 누적 잔여(부채)를 갚는다. 잔여는 0 밑으로 내려가지 않는다(floor).
     *
     * @param seconds 갚을 시간(초, 0 이상)
     * @return 실제로 차감된 양(초). 잔여보다 크게 요청하면 있던 잔여만큼만 반환된다.
     * @throws IllegalArgumentException seconds 가 음수인 경우
     */
    public long deduct(long seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("seconds must be >= 0");
        }
        long applied = Math.min(seconds, remainingSeconds);
        this.remainingSeconds -= applied;
        return applied;
    }

    public Long getId() {
        return id;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public long getDailyIncrementSeconds() {
        return dailyIncrementSeconds;
    }

    public long getCapSeconds() {
        return capSeconds;
    }

    public LocalDate getLastAccrualDate() {
        return lastAccrualDate;
    }

    public User getUser() {
        return user;
    }
}
