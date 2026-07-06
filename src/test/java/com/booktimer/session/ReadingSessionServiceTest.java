package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
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
 * <p>레포지토리를 mock으로 주입해 서비스의 조립 로직만 격리 검증한다: start의 중복 거부,
 * stop의 종료·저장, recordManual의 완료 세션 생성·저장. <b>부채 차감 검증은 없다</b> — 부채는
 * 저장된 카운터가 아니라 완료 세션에서 유도되므로(7일 윈도우, ReadingDebtService),
 * "세션을 저장했는가"가 곧 "부채가 줄었는가"다. 부채 계산은 {@link WeeklyDebtCalculatorTest}·
 * {@link ReadingDebtServiceTest}가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ReadingSessionServiceTest {

    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");

    @Mock
    private ReadingSessionRepository sessionRepository;

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
    @DisplayName("start: 책 없이(null) 시작하면 책 미지정 세션을 만들어 저장한다 — 시작을 책 선택으로 막지 않음(발견 1)")
    void start_nullBook_createsSessionWithoutBook() {
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.start(user, T0, null);

        assertThat(result.getBook()).isNull();
        assertThat(result.isActive()).isTrue();
        assertThat(result.getStartedAt()).isEqualTo(T0);
        verify(sessionRepository).save(any(ReadingSession.class));
        verify(bookRepository, never()).save(any(Book.class)); // 책이 없으니 전환·저장 없음
    }

    @Test
    @DisplayName("start: 책 없이 시작해도 이미 진행 중 세션이 있으면 거부한다(중복 가드는 그대로)")
    void start_nullBook_activeExists_throws() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.start(user, T0.plusSeconds(10), null))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    // --- stop ---

    @Test
    @DisplayName("stop: 진행 중 세션을 종료하고 저장한다 (부채 차감 없음 — 종료 세션이 그날 부채에 자동 반영)")
    void stop_active_endsAndSaves() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, T0.plusSeconds(1800)); // 30분

        assertThat(result.isActive()).isFalse();
        assertThat(result.getDurationSeconds()).isEqualTo(1800L);
        verify(sessionRepository).save(active);
    }

    @Test
    @DisplayName("stop: 진행 중 세션이 없으면 예외, 아무것도 저장하지 않는다")
    void stop_noActive_throws() {
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.stop(user, T0))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    // --- recordManual (사후 수동 입력) ---
    // 측정 깜빡한 독서를 완료 세션 한 건으로 기록. 부채는 세션 저장으로 그 날짜에 자동 반영된다(차감 로직 없음).
    // 윈도우(최근 7일) 제한은 컨트롤러 책임이라 여기선 안 본다.

    @Test
    @DisplayName("recordManual: 완료 세션(시작~종료)을 만들어 저장한다")
    void recordManual_createsCompletedSessionAndSaves() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        Instant started = T0;
        Instant ended = T0.plusSeconds(1800); // 30분
        ReadingSession result = service.recordManual(user, started, ended, book);

        assertThat(result.isActive()).isFalse();
        assertThat(result.getStartedAt()).isEqualTo(started);
        assertThat(result.getEndedAt()).isEqualTo(ended);
        assertThat(result.getDurationSeconds()).isEqualTo(1800L);
        assertThat(result.getBook()).isSameAs(book);
        verify(sessionRepository).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("recordManual: 책 없이(null) 기록하면 거부(IllegalArgumentException)하고 저장하지 않는다")
    void recordManual_nullBook_throwsAndDoesNotSave() {
        assertThatThrownBy(() -> service.recordManual(user, T0, T0.plusSeconds(60), null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("recordManual: 종료가 시작보다 이르면 거부하고 저장하지 않는다")
    void recordManual_endedBeforeStarted_throwsAndDoesNotSave() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);

        assertThatThrownBy(() -> service.recordManual(user, T0, T0.minusSeconds(60), book))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("recordManual: 읽고싶음 책으로 기록하면 그 책을 읽는중으로 자동 전환하고 저장한다")
    void recordManual_withWantToReadBook_marksReadingAndSaves() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ);
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        service.recordManual(user, T0, T0.plusSeconds(1800), book);

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        verify(bookRepository).save(book);
    }

    // --- tagBook (종료 후 태깅, 발견 1) ---

    @Test
    @DisplayName("tagBook: 책 미지정 세션을 찾아 책을 연결하고 저장한다")
    void tagBook_untaggedSession_linksBookAndSaves() {
        ReadingSession session = ReadingSession.start(user, T0); // book=null
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.tagBook(user, 1L, book);

        assertThat(result.getBook()).isSameAs(book);
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("tagBook: 읽고싶음 책으로 태깅하면 그 책을 읽는중으로 자동 전환한다(측정 시작과 동일 시맨틱)")
    void tagBook_withWantToReadBook_marksReading() {
        ReadingSession session = ReadingSession.start(user, T0);
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ);
        when(sessionRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        service.tagBook(user, 1L, book);

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        verify(bookRepository).save(book);
    }

    @Test
    @DisplayName("tagBook: 그 사용자의 세션이 없으면 거부한다(IDOR — IllegalArgumentException, 저장 없음)")
    void tagBook_sessionNotFound_throwsAndDoesNotSave() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tagBook(user, 99L, book))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("tagBook: 이미 책이 지정된 세션이면 거부한다(IllegalStateException, 저장 없음)")
    void tagBook_alreadyTagged_throwsAndDoesNotSave() {
        Book existing = Book.register(user, "기존 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, existing);
        Book other = Book.register(user, "다른 책", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.tagBook(user, 1L, other))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }
}
