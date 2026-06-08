package com.booktimer.timer;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReadingTimer 도메인 메서드 테스트 (DB 무관 — 객체만 생성해 검증).
 *
 * <p>7일 윈도우 부채 모델 전환(PR #217) + 잔재 컬럼 제거(PR #218) 이후 이 엔티티의 상태는
 * 하루 목표 하나뿐이다. 옛 accrue/deduct/cap/initialSetup 로직은 사라졌으므로, 여기선 남은
 * 불변식만 못 박는다 — (1) 타이머는 반드시 소유자가 있어야 한다, (2) 하루 목표는 음수일 수 없다.
 */
class ReadingTimerTest {

    private static final long HOUR = 3600L;

    private User sampleUser() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("startFor: 유저와 연결되고 하루 목표를 설정한다")
    void startFor_linksUserAndSetsGoal() {
        User user = sampleUser();

        ReadingTimer timer = ReadingTimer.startFor(user, HOUR);

        assertThat(timer.getUser()).isSameAs(user);
        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(HOUR);
    }

    @Test
    @DisplayName("startFor: user가 null이면 예외 (타이머는 반드시 소유자가 있다)")
    void startFor_nullUser_throws() {
        assertThatThrownBy(() -> ReadingTimer.startFor(null, HOUR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("startFor: 하루 목표가 음수면 예외")
    void startFor_negativeGoal_throws() {
        assertThatThrownBy(() -> ReadingTimer.startFor(sampleUser(), -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateSettings: 하루 목표를 바꾼다")
    void updateSettings_changesGoal() {
        ReadingTimer timer = ReadingTimer.of(HOUR);

        timer.updateSettings(2 * HOUR);

        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(2 * HOUR);
    }

    @Test
    @DisplayName("updateSettings: 하루 목표가 음수면 예외")
    void updateSettings_negativeGoal_throws() {
        ReadingTimer timer = ReadingTimer.of(HOUR);

        assertThatThrownBy(() -> timer.updateSettings(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기본값: 밀린 부채 합산 표시(debtCarryover)가 켜져 있다")
    void debtCarryover_defaultsOn() {
        ReadingTimer timer = ReadingTimer.startFor(sampleUser(), HOUR);

        assertThat(timer.isDebtCarryover()).isTrue();
    }

    @Test
    @DisplayName("updateSettings(목표, 토글): 밀린 부채 합산 표시를 끌 수 있다 (목표도 함께 적용)")
    void updateSettings_togglesDebtCarryover() {
        ReadingTimer timer = ReadingTimer.of(HOUR);

        timer.updateSettings(2 * HOUR, false);

        assertThat(timer.getDailyIncrementSeconds()).isEqualTo(2 * HOUR);
        assertThat(timer.isDebtCarryover()).isFalse();
    }
}
