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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserRegistrationService 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>신규 가입 시 <b>평문 비밀번호를 PasswordEncoder로 해싱</b>해 저장하고, User 저장과
 * ReadingTimer 부트스트랩이 한 트랜잭션에서 함께 일어나는지, 타이머가 올바른
 * 기본값·시작일·소유자로 만들어지는지를 격리 검증한다.
 * ("오늘"(유저 타임존) 계산은 여전히 상위 계층 책임 — startDate로 받는다.)
 */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    private static final LocalDate DAY0 = LocalDate.of(2026, 6, 1);

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReadingTimerRepository timerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserRegistrationService service;

    @Test
    @DisplayName("register: 평문 비밀번호를 해싱해 저장한다 (평문은 저장되지 않는다)")
    void register_hashesRawPassword() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        when(passwordEncoder.encode("rawpw1234")).thenReturn("$2a$10$ENCODED");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        User result = service.register("a@booktimer.com", "rawpw1234", "reader_a", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        verify(passwordEncoder).encode("rawpw1234");
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$ENCODED");
        assertThat(saved.getPasswordHash()).isNotEqualTo("rawpw1234");
        assertThat(result.getEmail()).isEqualTo("a@booktimer.com");
    }

    @Test
    @DisplayName("register: 기본 설정으로 ReadingTimer를 부트스트랩한다")
    void register_bootstrapsTimer() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$ENCODED");
        ArgumentCaptor<ReadingTimer> timerCaptor = ArgumentCaptor.forClass(ReadingTimer.class);

        User result = service.register("a@booktimer.com", "rawpw1234", "reader_a", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        verify(timerRepository).save(timerCaptor.capture());
        ReadingTimer timer = timerCaptor.getValue();
        assertThat(timer.getUser()).isSameAs(result);
        // 기본 하루 목표로 부트스트랩된다 (부채는 세션에서 유도 — 옛 시드 잔여·cap·기준일은 사라졌다)
        assertThat(timer.getDailyIncrementSeconds())
                .isEqualTo(UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS);
    }

    @Test
    @DisplayName("register: 타이머는 User 저장 뒤에 저장된다 (FK 충족 순서)")
    void register_savesUserBeforeTimer() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$ENCODED");

        service.register("b@booktimer.com", "rawpw1234", "reader_b", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        InOrder inOrder = inOrder(userRepository, timerRepository);
        inOrder.verify(userRepository).save(any(User.class));
        inOrder.verify(timerRepository).save(any(ReadingTimer.class));
    }

    @Test
    @DisplayName("register: 이미 가입된 이메일이면 EmailAlreadyExistsException을 던지고 아무것도 저장·해싱하지 않는다")
    void register_whenEmailExists_throwsAndDoesNotPersist() {
        when(userRepository.existsByEmail("dup@booktimer.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                "dup@booktimer.com", "rawpw1234", "reader_d", "책벌레", "Asia/Seoul", Role.USER, DAY0))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(timerRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register: 닉네임이 이미 쓰여도 가입을 허용한다 (nickname은 더 이상 유니크가 아님 — 중복확인조차 하지 않는다)")
    void register_allowsDuplicateNickname() {
        when(userRepository.existsByEmail("fresh@booktimer.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$ENCODED");

        User result = service.register(
                "fresh@booktimer.com", "rawpw1234", "reader_f", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        assertThat(result.getNickname()).isEqualTo("책벌레");
        verify(userRepository).save(any(User.class));
        // 표시 이름은 자유 중복 — UserRepository.existsByNickname 자체가 제거돼 사전 확인 경로가 없다.
    }

    @Test
    @DisplayName("register: 로컬 가입에서 login_id를 정규화해 불변으로 확정한다 (로그인 식별자 — PR-4)")
    void register_assignsNormalizedLoginId() {
        when(userRepository.existsByEmail("lid@booktimer.com")).thenReturn(false);
        when(userRepository.existsByLoginId("goospel")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$ENCODED");

        User result = service.register(
                "lid@booktimer.com", "rawpw1234", "Goospel", "책벌레", "Asia/Seoul", Role.USER, DAY0);

        assertThat(result.getLoginId()).isEqualTo("goospel"); // 소문자 정규화
    }

    @Test
    @DisplayName("register: 이미 쓰이는 login_id면 LoginIdAlreadyExistsException을 던지고 저장하지 않는다")
    void register_duplicateLoginId_throws() {
        // login_id를 email보다 먼저 검사한다(열거 완화: email 중복은 가장 마지막) → email 스텁 불필요.
        when(userRepository.existsByLoginId("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                "lid2@booktimer.com", "rawpw1234", "taken", "책벌레", "Asia/Seoul", Role.USER, DAY0))
                .isInstanceOf(LoginIdAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verify(timerRepository, never()).save(any());
    }

    // --- registerOAuth: 소셜 가입 (비밀번호 없음) ---

    @Test
    @DisplayName("registerOAuth: 비밀번호 없는 provider 사용자를 만들고 타이머를 부트스트랩한다 (해싱 호출 없음)")
    void registerOAuth_createsPasswordlessUserAndTimer() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<ReadingTimer> timerCaptor = ArgumentCaptor.forClass(ReadingTimer.class);

        User result = service.registerOAuth(
                "g@booktimer.com", "구글러", "Asia/Seoul", AuthProvider.GOOGLE, DAY0);

        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("g@booktimer.com");
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);

        verify(timerRepository).save(timerCaptor.capture());
        ReadingTimer timer = timerCaptor.getValue();
        assertThat(timer.getUser()).isSameAs(result);
        assertThat(timer.getDailyIncrementSeconds())
                .isEqualTo(UserRegistrationService.DEFAULT_DAILY_INCREMENT_SECONDS);

        // 소셜 가입은 평문 비밀번호가 없으므로 해싱하지 않는다
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("registerOAuth: User 저장 뒤 타이머 저장 (FK 충족 순서)")
    void registerOAuth_savesUserBeforeTimer() {
        when(userRepository.save(any(User.class))).thenAnswer(returnsFirstArg());

        service.registerOAuth("g2@booktimer.com", "구글러", "Asia/Seoul", AuthProvider.GOOGLE, DAY0);

        InOrder inOrder = inOrder(userRepository, timerRepository);
        inOrder.verify(userRepository).save(any(User.class));
        inOrder.verify(timerRepository).save(any(ReadingTimer.class));
    }
}
