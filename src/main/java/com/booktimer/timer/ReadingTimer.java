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

/**
 * 사용자별 독서 설정 — 하루 목표(daily goal)를 보관한다.
 *
 * <p>옛날엔 "단일 롤링 잔여(remainingSeconds) + 상한(capSeconds) + 매일 증가 Lazy accrual"(N-001)
 * 로 부채를 이 엔티티에 누적했지만, <b>7일 윈도우 per-day 부채 모델로 전환(PR #217)</b>하며 부채를
 * 더는 저장하지 않고 {@code reading_session}에서 유도(derive)한다. 그 잔재 컬럼
 * (remaining_seconds / cap_seconds / last_accrual_date)과 accrue/deduct/cap 로직은 이 정리 PR에서
 * 제거됐다(V20 마이그레이션으로 컬럼 drop). 이제 이 엔티티의 유일한 상태는 하루 목표다.
 *
 * <p>User 와는 1:1 — 이 엔티티가 FK(user_id)를 소유한다. 신규 사용자용 타이머는
 * {@link #startFor(User, long)} 로 생성한다.
 */
@Entity
public class ReadingTimer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 하루 목표(초). 기본 1시간, 사용자 설정 가능.
     * (컬럼명은 옛 이름 {@code daily_increment_seconds} 유지 — 의미만 "증가값"→"하루 목표"로 재해석.)
     */
    @Column(nullable = false)
    private long dailyIncrementSeconds;

    /**
     * 밀린 부채를 대시보드 헤드라인 타이머에 합산해 보여줄지 여부(표시 전용 설정). 기본 켜짐.
     *
     * <p>부채 모델 자체는 per-day(날짜별 독립)로 유지된다 — 이 플래그는 <b>표시만</b> 바꾼다. 켜지면
     * 헤드라인이 "오늘 부채 + 빠뜨린 날 합"(밀린 빚 포함)을, 꺼지면 "오늘 부채"만 보여준다. 어느 쪽이든
     * 밀린 빚은 <b>오늘 목표를 초과해 읽으면 갚아진다</b>(backward 재분배) — 선납만 불가하다.
     */
    @Column(nullable = false)
    private boolean debtCarryover = true;

    /** 소유 사용자 (1:1). FK(user_id)는 이 테이블이 소유한다. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    protected ReadingTimer() {
        // JPA
    }

    private ReadingTimer(long dailyIncrementSeconds) {
        if (dailyIncrementSeconds < 0) {
            throw new IllegalArgumentException("dailyIncrementSeconds must be >= 0");
        }
        this.dailyIncrementSeconds = dailyIncrementSeconds;
    }

    /**
     * 하루 목표를 직접 지정해 생성한다 (테스트 및 복원용).
     */
    public static ReadingTimer of(long dailyIncrementSeconds) {
        return new ReadingTimer(dailyIncrementSeconds);
    }

    /**
     * 신규 사용자를 위한 타이머를 만든다 — 하루 목표만 정한다.
     *
     * <p>부채는 세션에서 유도되므로(7일 윈도우 모델) 옛날처럼 "첫날치 증가값을 잔여로 시드"하거나
     * 누적 기준일을 잡을 필요가 없다. 가입 당일치 목표는 그날 부채 계산이 자연히 반영한다.
     *
     * @param user                  소유 사용자(필수)
     * @param dailyIncrementSeconds 하루 목표(초)
     * @throws IllegalArgumentException user 가 null 이거나 목표가 음수인 경우
     */
    public static ReadingTimer startFor(User user, long dailyIncrementSeconds) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        ReadingTimer timer = new ReadingTimer(dailyIncrementSeconds);
        timer.user = user;
        return timer;
    }

    /**
     * 사용자가 설정/온보딩에서 하루 목표를 변경한다.
     *
     * @param dailyIncrementSeconds 새 하루 목표(초, 0 이상)
     * @throws IllegalArgumentException 값이 음수인 경우
     */
    public void updateSettings(long dailyIncrementSeconds) {
        if (dailyIncrementSeconds < 0) {
            throw new IllegalArgumentException("dailyIncrementSeconds must be >= 0");
        }
        this.dailyIncrementSeconds = dailyIncrementSeconds;
    }

    /**
     * 하루 목표와 밀린 부채 합산 표시 토글을 함께 변경한다(설정 화면).
     *
     * @param dailyIncrementSeconds 새 하루 목표(초, 0 이상)
     * @param debtCarryover         밀린 부채를 헤드라인에 합산 표시할지
     * @throws IllegalArgumentException 목표가 음수인 경우
     */
    public void updateSettings(long dailyIncrementSeconds, boolean debtCarryover) {
        updateSettings(dailyIncrementSeconds);
        this.debtCarryover = debtCarryover;
    }

    public Long getId() {
        return id;
    }

    public long getDailyIncrementSeconds() {
        return dailyIncrementSeconds;
    }

    /** 밀린 부채를 헤드라인 타이머에 합산 표시할지(표시 전용 토글). */
    public boolean isDebtCarryover() {
        return debtCarryover;
    }

    public User getUser() {
        return user;
    }
}
