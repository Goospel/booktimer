package com.booktimer.timer;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReadingTimerService 단위 테스트 (Mockito + 고정 Clock — DB/컨텍스트 무관).
 *
 * <p>접속 시 누적(accrue) 적용의 책임: "오늘"을 <b>유저 타임존</b>으로 계산해
 * {@link ReadingTimer#accrueUntil(LocalDate)} 에 위임하고 저장한다. 고정 Clock으로
 * 시간 의존을 결정적으로 만든다.
 */
@ExtendWith(MockitoExtension.class)
class ReadingTimerServiceTest {

    private static final long HOUR = 3600L;

    @Mock
    private ReadingTimerRepository timerRepository;

    private User userInZone(String timezone) {
        return User.of("reader@booktimer.com", "$2a$10$hashedpw", "책벌레", timezone, Role.USER);
    }

    private ReadingTimerService serviceAt(Instant now) {
        // Clock 자체 zone은 무관 — 서비스는 clock.instant()와 유저 TZ로 오늘을 계산한다.
        return new ReadingTimerService(timerRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("accrueToToday: '오늘'을 유저 타임존으로 계산해 누적한다 (UTC 자정 경계)")
    void accrueToToday_usesUserTimezone() {
        // 2026-06-01T16:00Z = 서울(+9) 2026-06-02 01:00 → 서울 오늘 = 06-02
        // (서버 UTC 기준이면 오늘 06-01 이라 누적 0 이 되어 구분된다)
        ReadingTimerService service = serviceAt(Instant.parse("2026-06-01T16:00:00Z"));
        User user = userInZone("Asia/Seoul");
        ReadingTimer timer = ReadingTimer.of(HOUR, 5 * HOUR, 0L, LocalDate.of(2026, 6, 1));
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any(ReadingTimer.class))).thenAnswer(returnsFirstArg());

        ReadingTimer result = service.accrueToToday(user);

        assertThat(result.getLastAccrualDate()).isEqualTo(LocalDate.of(2026, 6, 2)); // 서울 날짜
        assertThat(result.getRemainingSeconds()).isEqualTo(HOUR);                    // 1일치 누적
        verify(timerRepository).save(timer);
    }

    @Test
    @DisplayName("accrueToToday: 유저 타임존 오늘이 기준일과 같으면 변화 없음 (멱등)")
    void accrueToToday_sameDay_noChange() {
        // 2026-06-01T03:00Z = 서울 12:00 06-01 → 서울 오늘 = 06-01 = 기준일
        ReadingTimerService service = serviceAt(Instant.parse("2026-06-01T03:00:00Z"));
        User user = userInZone("Asia/Seoul");
        ReadingTimer timer = ReadingTimer.of(HOUR, 5 * HOUR, 0L, LocalDate.of(2026, 6, 1));
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any(ReadingTimer.class))).thenAnswer(returnsFirstArg());

        ReadingTimer result = service.accrueToToday(user);

        assertThat(result.getLastAccrualDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.getRemainingSeconds()).isZero();
    }

    @Test
    @DisplayName("accrueToToday: 유저 타이머가 없으면 예외")
    void accrueToToday_noTimer_throws() {
        ReadingTimerService service = serviceAt(Instant.parse("2026-06-01T16:00:00Z"));
        User user = userInZone("Asia/Seoul");
        when(timerRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accrueToToday(user))
                .isInstanceOf(IllegalStateException.class);
    }
}
