package com.booktimer.timer;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReadingTimer 도메인 메서드 테스트 (DB 무관 — 객체만 생성해 검증).
 *
 * accrueUntil(today): lastAccrualDate ~ today 경과 일수만큼 소급 누적하고
 * lastAccrualDate 를 today 로 전진시킨다. (N-001 Lazy 계산)
 */
class ReadingTimerTest {

    private static final long HOUR = 3600L;
    private static final LocalDate DAY0 = LocalDate.of(2026, 5, 31);

    private ReadingTimer timerWith(long remaining, LocalDate last) {
        // increment 1h, cap 5h
        return ReadingTimer.of(HOUR, 5 * HOUR, remaining, last);
    }

    @Test
    @DisplayName("3일 경과: 잔여가 3시간 늘고 기준일이 today로 전진한다")
    void accrueUntil_threeDaysLater() {
        ReadingTimer timer = timerWith(0L, DAY0);

        timer.accrueUntil(DAY0.plusDays(3));

        assertThat(timer.getRemainingSeconds()).isEqualTo(3 * HOUR);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0.plusDays(3));
    }

    @Test
    @DisplayName("같은 날 재호출: 잔여·기준일 모두 그대로 (멱등)")
    void accrueUntil_sameDay_idempotent() {
        ReadingTimer timer = timerWith(1800L, DAY0);

        timer.accrueUntil(DAY0);

        assertThat(timer.getRemainingSeconds()).isEqualTo(1800L);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0);
    }

    @Test
    @DisplayName("누적이 cap을 넘으면 cap으로 클램프된다")
    void accrueUntil_clampedToCap() {
        ReadingTimer timer = timerWith(4 * HOUR, DAY0); // +3h(3일) = 7h → cap 5h

        timer.accrueUntil(DAY0.plusDays(3));

        assertThat(timer.getRemainingSeconds()).isEqualTo(5 * HOUR);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0.plusDays(3));
    }

    @Test
    @DisplayName("과거 날짜로 호출(시계 역행): 잔여·기준일 그대로")
    void accrueUntil_pastDate_noChange() {
        ReadingTimer timer = timerWith(1800L, DAY0);

        timer.accrueUntil(DAY0.minusDays(2));

        assertThat(timer.getRemainingSeconds()).isEqualTo(1800L);
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0);
    }

    // --- User 연관 (@OneToOne) 증분 ---

    private User sampleUser() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("startFor: 유저와 연결되고 잔여 0, 기준일=시작일, 설정값 반영")
    void startFor_linksUserAndInitializes() {
        User user = sampleUser();

        ReadingTimer timer = ReadingTimer.startFor(user, HOUR, 5 * HOUR, DAY0);

        assertThat(timer.getUser()).isSameAs(user);
        assertThat(timer.getRemainingSeconds()).isZero();
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0);
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(HOUR);
        assertThat(timer.getCapSeconds()).isEqualTo(5 * HOUR);
    }

    @Test
    @DisplayName("startFor: user가 null이면 예외")
    void startFor_nullUser_throws() {
        assertThatThrownBy(() -> ReadingTimer.startFor(null, HOUR, 5 * HOUR, DAY0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- 차감(deduct) 증분: 세션 측정량을 누적 잔여에서 갚는다 ---

    @Test
    @DisplayName("deduct: 잔여보다 적게 갚으면 그만큼 줄고, 차감된 양을 반환")
    void deduct_lessThanRemaining() {
        ReadingTimer timer = timerWith(2 * HOUR, DAY0);

        long applied = timer.deduct(1800L); // 30분

        assertThat(applied).isEqualTo(1800L);
        assertThat(timer.getRemainingSeconds()).isEqualTo(2 * HOUR - 1800L);
    }

    @Test
    @DisplayName("deduct: 잔여보다 많이 갚아도 0 밑으로 안 가고, 실제 차감분만 반환(floor)")
    void deduct_moreThanRemaining_floorsToZero() {
        ReadingTimer timer = timerWith(1800L, DAY0);

        long applied = timer.deduct(3 * HOUR); // 잔여(30분)보다 큼

        assertThat(applied).isEqualTo(1800L); // 있던 만큼만 갚아짐
        assertThat(timer.getRemainingSeconds()).isZero();
    }

    @Test
    @DisplayName("deduct: 잔여와 정확히 같으면 0이 된다")
    void deduct_exactlyRemaining() {
        ReadingTimer timer = timerWith(HOUR, DAY0);

        long applied = timer.deduct(HOUR);

        assertThat(applied).isEqualTo(HOUR);
        assertThat(timer.getRemainingSeconds()).isZero();
    }

    @Test
    @DisplayName("deduct: 0초는 변화 없음")
    void deduct_zero_noChange() {
        ReadingTimer timer = timerWith(HOUR, DAY0);

        long applied = timer.deduct(0L);

        assertThat(applied).isZero();
        assertThat(timer.getRemainingSeconds()).isEqualTo(HOUR);
    }

    @Test
    @DisplayName("deduct: 음수는 예외")
    void deduct_negative_throws() {
        ReadingTimer timer = timerWith(HOUR, DAY0);

        assertThatThrownBy(() -> timer.deduct(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
