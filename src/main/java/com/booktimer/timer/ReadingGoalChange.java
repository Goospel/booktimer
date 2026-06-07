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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * 하루 목표 변경 이력 한 행 — "이 사용자의 하루 목표가 {@code effectiveDate}부터 {@code goalSeconds}였다".
 *
 * <p>부채는 {@code max(0, 하루목표 − 그날 읽은 초)}로 유도되는데, 하루 목표를 <b>현재 평면값 하나</b>
 * ({@link ReadingTimer#getDailyIncrementSeconds()})로만 잡으면 사용자가 목표를 올렸을 때 옛 목표를 채웠던
 * 과거 날이 새 목표로 재판정돼 빠뜨린 날로 둔갑한다(소급 함정 — N-059). 그걸 막으려 목표가 <b>설정·변경되는
 * 시점</b>마다 한 행을 남겨, 과거 날을 그날 유효했던 목표로 판정한다({@link GoalSchedule}).
 *
 * <p>{@code (user, effective_date)} 유니크 — 하루에 여러 번 바꾸면 그날 행의 목표만 갱신(upsert)한다.
 * {@link ReadingTimer}는 <b>현재 목표 캐시</b>로 그대로 유지되고(잔디 등 다른 화면이 씀), 변경 시 둘을 함께 갱신한다.
 */
@Entity
@Table(name = "reading_goal_change", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reading_goal_change_user_date", columnNames = {"user_id", "effective_date"})
})
public class ReadingGoalChange extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 목표를 가진 사용자. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 이 목표가 적용되기 시작한 날(유저 타임존 기준). */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** 그 시점부터의 하루 목표(초, 0 이상). */
    @Column(name = "goal_seconds", nullable = false)
    private long goalSeconds;

    protected ReadingGoalChange() {
        // JPA
    }

    private ReadingGoalChange(User user, LocalDate effectiveDate, long goalSeconds) {
        if (user == null || effectiveDate == null) {
            throw new IllegalArgumentException("user/effectiveDate must not be null");
        }
        if (goalSeconds < 0) {
            throw new IllegalArgumentException("goalSeconds must be >= 0");
        }
        this.user = user;
        this.effectiveDate = effectiveDate;
        this.goalSeconds = goalSeconds;
    }

    /**
     * 목표 변경 이력 한 행을 만든다.
     *
     * @throws IllegalArgumentException user/effectiveDate가 null이거나 goalSeconds가 음수인 경우
     */
    public static ReadingGoalChange of(User user, LocalDate effectiveDate, long goalSeconds) {
        return new ReadingGoalChange(user, effectiveDate, goalSeconds);
    }

    /** 같은 날 다시 바꿀 때 그날 행의 목표만 갱신한다(upsert — 중복 행을 만들지 않기 위함). */
    public void changeGoal(long goalSeconds) {
        if (goalSeconds < 0) {
            throw new IllegalArgumentException("goalSeconds must be >= 0");
        }
        this.goalSeconds = goalSeconds;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public long getGoalSeconds() {
        return goalSeconds;
    }
}
