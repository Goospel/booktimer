package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.timer.ReadingTimer;
import com.booktimer.timer.ReadingTimerRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReadingSessionService 오케스트레이션 단위 테스트 (Mockito — DB/컨텍스트 무관).
 *
 * <p>레포지토리를 mock으로 주입해 서비스의 조립 로직만 격리 검증한다:
 * start의 중복 거부, stop의 end→deduct 순서와 양쪽 저장. 실제 영속성/트랜잭션은
 * 슬라이스 테스트(Repository)와 도메인 테스트(ReadingTimer/Session)가 커버한다.
 */
@ExtendWith(MockitoExtension.class)
class ReadingSessionServiceTest {

    private static final long HOUR = 3600L;
    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");
    private static final LocalDate DAY0 = LocalDate.of(2026, 5, 31);

    @Mock
    private ReadingSessionRepository sessionRepository;

    @Mock
    private ReadingTimerRepository timerRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private ReadingSessionService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    // --- start ---

    @Test
    @DisplayName("start: 진행 중 세션이 없으면 새 세션을 만들어 저장한다")
    void start_noActive_createsAndSaves() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.start(user, T0, book);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getStartedAt()).isEqualTo(T0);
        assertThat(result.getBook()).isSameAs(book);
        assertThat(result.isActive()).isTrue();
        verify(sessionRepository).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("start: 이미 진행 중 세션이 있으면 거부(예외)하고 저장하지 않는다")
    void start_activeExists_throws() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        ReadingSession active = ReadingSession.start(user, T0, book);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.start(user, T0.plusSeconds(10), book))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("start: 읽고싶음 책으로 시작하면 그 책을 읽는중으로 자동 전환하고 저장한다")
    void start_withWantToReadBook_marksReadingAndSaves() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        service.start(user, T0, book);

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        verify(bookRepository).save(book);
    }

    @Test
    @DisplayName("start: 이미 읽는중인 책으로 시작하면 상태는 그대로, 책을 다시 저장하지 않는다")
    void start_withReadingBook_doesNotResave() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        service.start(user, T0, book);

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("start: 책 없이(null) 시작하면 거부(IllegalArgumentException)하고 아무것도 저장하지 않는다")
    void start_nullBook_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> service.start(user, T0, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sessionRepository, never()).save(any(ReadingSession.class));
        verify(bookRepository, never()).save(any(Book.class));
    }

    // --- stop ---

    @Test
    @DisplayName("stop: 진행 중 세션을 종료하고 측정량을 타이머에서 차감한 뒤 둘 다 저장한다")
    void stop_active_endsAndDeductsAndSaves() {
        ReadingSession active = ReadingSession.start(user, T0);
        ReadingTimer timer = ReadingTimer.of(HOUR, 5 * HOUR, 2 * HOUR, DAY0); // 잔여 2h
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(timerRepository.findByUser(user)).thenReturn(Optional.of(timer));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, T0.plusSeconds(1800)); // 30분

        assertThat(result.isActive()).isFalse();
        assertThat(result.getDurationSeconds()).isEqualTo(1800L);
        assertThat(timer.getRemainingSeconds()).isEqualTo(2 * HOUR - 1800L); // 차감됨
        verify(sessionRepository).save(active);
        verify(timerRepository).save(timer);
    }

    @Test
    @DisplayName("stop: 진행 중 세션이 없으면 예외, 아무것도 저장하지 않는다")
    void stop_noActive_throws() {
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(user, T0))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
        verify(timerRepository, never()).save(any(ReadingTimer.class));
    }

    @Test
    @DisplayName("stop: 유저 타이머가 없으면 예외")
    void stop_noTimer_throws() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(timerRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(user, T0.plusSeconds(60)))
                .isInstanceOf(IllegalStateException.class);
    }
}
