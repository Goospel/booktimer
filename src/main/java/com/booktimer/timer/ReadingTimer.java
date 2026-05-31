package com.booktimer.timer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 사용자별 독서 누적 상태 + 설정.
 *
 * <p>"매일 목표가 증가값만큼 늘고, 안 채운 잔여가 다음 날로 이월된다"(N-001)는
 * 핵심 규칙의 상태를 보관한다. 갱신은 배치가 아니라 접속 시 {@link #accrueUntil(LocalDate)}
 * 로 경과 일수만큼 소급 계산(Lazy)한다.
 *
 * <p>TODO: User 와의 @OneToOne 관계는 User 엔티티 작업 증분에서 연결한다.
 */
@Entity
public class ReadingTimer {

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
}
