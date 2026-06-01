package com.booktimer.user;

import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserRegistrationService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>신규 가입 시 User 저장과 ReadingTimer 부트스트랩이 한 트랜잭션에서 함께 일어나는지,
 * 타이머가 올바른 기본값·시작일·소유자로 만들어지는지를 격리 검증한다.
 * (비밀번호 해싱/"오늘" 계산은 상위 계층 책임 — 여기선 해시된 비번과 startDate를 받는다.)
 */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    private static final LocalDate DAY0 = LocalDate.of(2026, 6, 1);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReadingTimerRepository timerRepository;

    @InjectMocks
    private UserRegistrationService service;

    @Test
    @DisplayName("register: User를 저장하고 기본 설정으로 ReadingTimer를 부트스트랩한다")
    void register_savesUserAndBootstrapsTimer() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        ArgumentCaptor<ReadingTimer> timerCaptor = ArgumentCaptor.forClass(ReadingTimer.class);

        User result = service.register("a@booktimer.com", "$2a$10$hashedpw", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        assertThat(result.getEmail()).isEqualTo("a@booktimer.com");
        verify(userRepository).save(any(User.class));
        verify(timerRepository).save(timerCaptor.capture());

        ReadingTimer timer = timerCaptor.getValue();
        assertThat(timer.getUser()).isSameAs(result);
        assertThat(timer.getRemainingSeconds()).isZero();
        assertThat(timer.getLastAccrualDate()).isEqualTo(DAY0);
        assertThat(timer.getDailyIncrementSeconds())
                .isEqualTo(UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS);
        assertThat(timer.getCapSeconds())
                .isEqualTo(UserRegistrationService.DEFAULT_CAP_SECONDS);
    }

    @Test
    @DisplayName("register: 타이머는 User 저장 뒤에 저장된다 (FK 충족 순서)")
    void register_savesUserBeforeTimer() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());

        service.register("b@booktimer.com", "$2a$10$hashedpw", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        InOrder inOrder = inOrder(userRepository, timerRepository);
        inOrder.verify(userRepository).save(any(User.class));
        inOrder.verify(timerRepository).save(any(ReadingTimer.class));
    }
}
