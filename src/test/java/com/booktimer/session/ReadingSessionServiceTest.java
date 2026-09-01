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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // --- stop 상한 클램프 (한 세션 최대 인정 6시간) ---
    // 끝내기를 깜빡하면 21시간짜리 세션이 그대로 기록돼 통계·잔디를 왜곡한다(운영 실측).
    // 정책: 경과가 cap을 초과하면 startedAt + 6h 로 잘라 인정한다.

    @Test
    @DisplayName("stop: cap 미만(5시간 59분)이면 실제 경과 그대로 기록한다")
    void stop_justUnderCap_recordsActualElapsed() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        Instant now = T0.plusSeconds(6 * 3600 - 60); // 5시간 59분
        ReadingSession result = service.stop(user, now);

        assertThat(result.getEndedAt()).isEqualTo(now);
        assertThat(result.getDurationSeconds()).isEqualTo(6 * 3600 - 60);
    }

    @Test
    @DisplayName("stop: 정확히 cap(6시간)이면 클램프 없이 그대로 6시간이다(경계 — 초과 아님)")
    void stop_exactlyCap_recordsSixHours() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        Instant now = T0.plus(ReadingSessionService.MAX_SESSION_DURATION);
        ReadingSession result = service.stop(user, now);

        assertThat(result.getEndedAt()).isEqualTo(now);
        assertThat(result.getDurationSeconds()).isEqualTo(21600L);
    }

    @Test
    @DisplayName("stop: cap 1초 초과면 startedAt+6h 로 클램프한다")
    void stop_oneSecondOverCap_clampsToCap() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, T0.plusSeconds(6 * 3600 + 1));

        assertThat(result.getEndedAt()).isEqualTo(T0.plusSeconds(21600));
        assertThat(result.getDurationSeconds()).isEqualTo(21600L);
    }

    @Test
    @DisplayName("stop: 끝내기를 잊은 21시간 세션도 6시간까지만 인정한다(운영 실측 사례)")
    void stop_twentyOneHours_clampsToCap() {
        ReadingSession active = ReadingSession.start(user, T0);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, T0.plusSeconds(21 * 3600));

        assertThat(result.getEndedAt()).isEqualTo(T0.plusSeconds(21600));
        assertThat(result.getDurationSeconds()).isEqualTo(21600L);
    }

    // --- closeStaleSessions (방치 세션 자동 종료 — 스위퍼가 얇게 트리거) ---

    @Test
    @DisplayName("closeStaleSessions: cap 초과로 방치된 세션을 정확히 cap(6시간)으로 닫는다")
    void closeStaleSessions_staleSession_closedAtCap() {
        Instant now = T0.plusSeconds(21 * 3600);
        ReadingSession stale = ReadingSession.start(user, T0);
        when(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(now.minus(ReadingSessionService.MAX_SESSION_DURATION)))
                .thenReturn(List.of(stale));

        int closed = service.closeStaleSessions(now);

        assertThat(closed).isEqualTo(1);
        assertThat(stale.getEndedAt()).isEqualTo(T0.plusSeconds(21600));
        assertThat(stale.getDurationSeconds()).isEqualTo(21600L);
        verify(sessionRepository).save(stale);
    }

    @Test
    @DisplayName("closeStaleSessions: 조회 경계는 now - 6h — 딱 6시간·5시간 된 세션은 조회에서 빠져 안 닫힌다")
    void closeStaleSessions_notYetStale_queriesWithCapCutoff() {
        Instant now = T0.plusSeconds(21 * 3600);
        when(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of());

        int closed = service.closeStaleSessions(now);

        assertThat(closed).isZero();
        // startedAt < now-6h 인 것만 대상 — 경계(정확히 6시간 경과)는 포함되지 않는다
        verify(sessionRepository).findByEndedAtIsNullAndStartedAtBefore(now.minusSeconds(21600));
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    @Test
    @DisplayName("closeStaleSessions: 방치 세션 여러 건을 한 번에 처리한다")
    void closeStaleSessions_multiple_allClosed() {
        Instant now = T0.plusSeconds(21 * 3600);
        ReadingSession a = ReadingSession.start(user, T0);
        ReadingSession b = ReadingSession.start(user, T0.plusSeconds(3600));
        when(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of(a, b));

        int closed = service.closeStaleSessions(now);

        assertThat(closed).isEqualTo(2);
        // T0 = 18:00 KST 라 a의 cap 종료(+6h)가 정확히 자정 — 경계와 일치하면 자르지 않는다(0초 조각 금지).
        assertThat(a.getDurationSeconds()).isEqualTo(21600L);
        // b는 19:00 KST 시작이라 cap 종료가 익일 01:00 — 자정에서 갈려 이 행은 5시간만 갖는다.
        assertThat(b.getEndedAt()).isEqualTo(T0.plusSeconds(6 * 3600)); // = 익일 00:00 KST
        assertThat(b.getDurationSeconds()).isEqualTo(5 * 3600L);
        verify(sessionRepository).save(a);
        verify(sessionRepository).save(b);
        // a 1행 + b 2행 — cap 총량(6h)은 b의 조각 합으로 보존된다.
        org.mockito.ArgumentCaptor<ReadingSession> saved = org.mockito.ArgumentCaptor.forClass(ReadingSession.class);
        verify(sessionRepository, times(3)).save(saved.capture());
        assertThat(saved.getAllValues().get(2).getDurationSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("closeStaleSessions: 경합으로 이미 닫힌 세션이 섞여도 나머지는 정상 처리한다")
    void closeStaleSessions_alreadyEnded_skippedAndRestProcessed() {
        Instant now = T0.plusSeconds(21 * 3600);
        ReadingSession raced = ReadingSession.start(user, T0);
        raced.end(T0.plusSeconds(600)); // 조회 직후 사용자가 stop을 누른 상황
        ReadingSession stale = ReadingSession.start(user, T0.plusSeconds(3600));
        when(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of(raced, stale));

        int closed = service.closeStaleSessions(now);

        assertThat(closed).isEqualTo(1);
        assertThat(raced.getDurationSeconds()).isEqualTo(600L); // 사용자 종료값 보존
        // 19:00 KST 시작이라 cap 종료(익일 01:00)가 자정에서 갈린다 — 이 행은 첫 조각(5시간).
        assertThat(stale.getEndedAt()).isEqualTo(T0.plusSeconds(6 * 3600)); // = 익일 00:00 KST
        assertThat(stale.getDurationSeconds()).isEqualTo(5 * 3600L);
        // 경합 세션은 첫 조각 end()에서 걸러지므로 조각 저장이 하나도 일어나지 않는다(부분 분할 없음).
        verify(sessionRepository, never()).save(raced);
        verify(sessionRepository).save(stale);
        verify(sessionRepository, times(2)).save(any(ReadingSession.class)); // stale의 두 조각뿐
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

    // --- changeActiveBook (진행 중 세션의 대상 교체, 핸드오프 3f) ---
    //
    // tagBook과 달리 세션 id를 받지 않는다 — "내 진행 중 세션"을 서버가 찾으므로(stop과 같은 finder)
    // 요청에 세션 좌표가 아예 없고, 그래서 세션 IDOR이 구조적으로 성립하지 않는다.

    @Test
    @DisplayName("changeActiveBook: 진행 중 세션을 찾아 책을 갈고 저장한다(세션은 안 멈춘다)")
    void changeActiveBook_replacesBookAndSaves() {
        Book started = Book.register(user, "시작한 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, started);
        Book other = Book.register(user, "바꾼 책", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.changeActiveBook(user, other);

        assertThat(result.getBook()).isSameAs(other);
        assertThat(result.getStartedAt()).isEqualTo(T0); // 라벨만 갈렸다 — 잰 시간은 그대로 새 책에 붙는다
        assertThat(result.getEndedAt()).isNull();
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("changeActiveBook: null 이면 「책 없이」로 되돌린다 — 책 조회도 저장도 없다")
    void changeActiveBook_null_clearsBook() {
        Book started = Book.register(user, "시작한 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, started);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.changeActiveBook(user, null);

        assertThat(result.getBook()).isNull();
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    @DisplayName("changeActiveBook: 읽고싶음 책으로 바꾸면 읽는중으로 전환하고 시작 시각은 세션 시작 시각이다")
    void changeActiveBook_withWantToReadBook_marksReadingFromSessionStart() {
        ReadingSession session = ReadingSession.start(user, T0); // 책 없이 시작
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        service.changeActiveBook(user, book);

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        // 태깅 시점(지금)이 아니라 실제로 읽기 시작한 때 — tagBook과 같은 시맨틱이다.
        assertThat(book.getStartedReadingAt()).isEqualTo(T0);
        verify(bookRepository).save(book);
    }

    @Test
    @DisplayName("changeActiveBook: 진행 중 세션이 없으면 거부한다(IllegalStateException, 저장 없음)")
    void changeActiveBook_noActiveSession_throwsAndDoesNotSave() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeActiveBook(user, book))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any(ReadingSession.class));
    }

    // ==========================================================================
    // 자정 분할 — 순수 함수 splitByMidnight
    //
    // 저장 시점에 유저 TZ 자정으로 자르는 규칙의 계측기. "한 행이 유저 TZ 하루 안에 있다"는
    // 새 불변식이 여기서 나온다. 경계가 끝점과 일치하면 0초 조각을 만들지 않는 것이 핵심 규칙.
    // ==========================================================================

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 유저 TZ 로컬 시각 문자열("2026-06-01T23:50")을 Instant로 — 손으로 UTC를 환산하지 않는다. */
    private static Instant at(String localDateTime, ZoneId zone) {
        return LocalDateTime.parse(localDateTime).atZone(zone).toInstant();
    }

    private static Instant kst(String localDateTime) {
        return at(localDateTime, KST);
    }

    /** 조각들이 빈틈·겹침 없이 원본 구간을 정확히 덮는지 — 모든 분할 케이스의 공통 불변식. */
    private static void assertContiguous(List<ReadingSessionService.Segment> segments,
                                         Instant startedAt, Instant endedAt) {
        assertThat(segments.get(0).start()).isEqualTo(startedAt);
        assertThat(segments.get(segments.size() - 1).end()).isEqualTo(endedAt);
        for (int i = 1; i < segments.size(); i++) {
            assertThat(segments.get(i).start()).isEqualTo(segments.get(i - 1).end());
        }
    }

    @Test
    @DisplayName("splitByMidnight #1: 같은 날 10:00→11:00 이면 자르지 않는다(1조각, 원본 그대로)")
    void split_sameDay_singleSegment() {
        Instant s = kst("2026-06-01T10:00"), e = kst("2026-06-01T11:00");

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, KST);

        assertThat(segments).hasSize(1);
        assertContiguous(segments, s, e);
    }

    @Test
    @DisplayName("splitByMidnight #2: 23:50→익일 00:40 은 자정에서 2조각으로 갈리고 합은 50분이다")
    void split_acrossMidnight_twoSegments() {
        Instant s = kst("2026-06-01T23:50"), e = kst("2026-06-02T00:40");
        Instant midnight = LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant();

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, KST);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).end()).isEqualTo(midnight);
        assertThat(segments.get(1).start()).isEqualTo(midnight);
        assertContiguous(segments, s, e);
        assertThat(java.time.Duration.between(segments.get(0).start(), segments.get(0).end()).toSeconds()).isEqualTo(600L);
        assertThat(java.time.Duration.between(segments.get(1).start(), segments.get(1).end()).toSeconds()).isEqualTo(2400L);
    }

    @Test
    @DisplayName("splitByMidnight #3: 정확히 자정에 끝나면 자르지 않는다(0초 조각 금지)")
    void split_endsExactlyAtMidnight_singleSegment() {
        Instant s = kst("2026-06-01T23:00"), e = LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant();

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, KST);

        assertThat(segments).hasSize(1);
        assertContiguous(segments, s, e);
    }

    @Test
    @DisplayName("splitByMidnight #4: 정확히 자정에 시작해도 1조각이다(다음 경계는 24시간 뒤)")
    void split_startsExactlyAtMidnight_singleSegment() {
        Instant s = LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant();
        Instant e = s.plusSeconds(3600);

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, KST);

        assertThat(segments).hasSize(1);
        assertContiguous(segments, s, e);
    }

    @Test
    @DisplayName("splitByMidnight #5: 0초(시작 즉시 종료) 세션은 단일 [t,t] 조각이다")
    void split_zeroLength_singleSegment() {
        Instant t = kst("2026-06-01T10:00");

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(t, t, KST);

        assertThat(segments).hasSize(1);
        assertContiguous(segments, t, t);
    }

    @Test
    @DisplayName("splitByMidnight #6: 26시간(자정 2회 통과)이면 3조각으로 갈리고 빈틈이 없다")
    void split_twoMidnights_threeSegments() {
        Instant s = kst("2026-06-01T23:00"), e = kst("2026-06-03T01:00");

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, KST);

        assertThat(segments).hasSize(3);
        assertThat(segments.get(0).end()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant());
        assertThat(segments.get(1).end()).isEqualTo(LocalDate.of(2026, 6, 3).atStartOfDay(KST).toInstant());
        assertContiguous(segments, s, e);
    }

    @Test
    @DisplayName("splitByMidnight #7: DST 짧은 날(America/New_York 2026-03-08, 23시간)엔 24시간이 자정을 2회 넘어 3조각이다")
    void split_dstShortDay_threeSegments() {
        ZoneId ny = ZoneId.of("America/New_York");
        Instant s = at("2026-03-07T23:30", ny);
        Instant e = s.plus(java.time.Duration.ofHours(24));

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, ny);

        assertThat(segments).hasSize(3);
        // 경계는 손계산이 아니라 tzdata가 준 값이어야 한다(EST→EDT로 UTC 오프셋이 바뀐다).
        assertThat(segments.get(0).end()).isEqualTo(LocalDate.of(2026, 3, 8).atStartOfDay(ny).toInstant());
        assertThat(segments.get(1).end()).isEqualTo(LocalDate.of(2026, 3, 9).atStartOfDay(ny).toInstant());
        assertContiguous(segments, s, e);
    }

    @Test
    @DisplayName("splitByMidnight #8: 자정이 존재하지 않는 날(America/Santiago 2026-09-06)도 예외 없이 첫 유효 시각으로 자른다")
    void split_dstMissingMidnight_usesFirstValidInstant() {
        ZoneId santiago = ZoneId.of("America/Santiago");
        Instant s = at("2026-09-05T22:00", santiago);
        Instant e = at("2026-09-06T03:00", santiago);
        Instant boundary = LocalDate.of(2026, 9, 6).atStartOfDay(santiago).toInstant();

        List<ReadingSessionService.Segment> segments = ReadingSessionService.splitByMidnight(s, e, santiago);

        // 이 TZ의 그날은 00:00이 스킵돼 하루가 01:00에 시작한다 — 이 전제가 깨지면(tzdata 변경)
        // 이 테스트는 「자정 부재」를 더는 계측하지 않으므로 여기서 크게 실패해야 한다.
        assertThat(boundary.atZone(santiago).toLocalTime()).isEqualTo(java.time.LocalTime.of(1, 0));
        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).end()).isEqualTo(boundary);
        assertContiguous(segments, s, e);
    }

    @Test
    @DisplayName("splitByMidnight #9: 경계는 유저 TZ 자정이지 서버 UTC 자정이 아니다(Pacific/Auckland 대조)")
    void split_nonUtcZone_usesUserZoneMidnight() {
        ZoneId auckland = ZoneId.of("Pacific/Auckland");
        Instant s = at("2026-06-01T23:00", auckland);
        Instant e = at("2026-06-02T01:00", auckland);

        List<ReadingSessionService.Segment> byAuckland = ReadingSessionService.splitByMidnight(s, e, auckland);
        List<ReadingSessionService.Segment> byUtc = ReadingSessionService.splitByMidnight(s, e, ZoneOffset.UTC);

        assertThat(byAuckland).hasSize(2);
        assertThat(byAuckland.get(0).end()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay(auckland).toInstant());
        // 같은 구간이 UTC 기준으론 자정을 안 넘는다 — TZ를 무시하면 나오는 결과와 다름을 못 박는다.
        assertThat(byUtc).hasSize(1);
    }

    // ==========================================================================
    // 자정 분할 — 종료 3경로 배선 (stop / closeStaleSessions / recordManual)
    // ==========================================================================

    @Test
    @DisplayName("stop #10: 23:50 시작 → 익일 00:40 종료면 2행으로 저장하고 마지막 조각을 반환한다")
    void stop_acrossMidnight_savesTwoRowsReturnsLast() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        Instant started = kst("2026-06-01T23:50");
        Instant now = kst("2026-06-02T00:40");
        Instant midnight = LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant();
        ReadingSession active = ReadingSession.start(user, started, book);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, now);

        verify(sessionRepository, times(2)).save(any(ReadingSession.class));
        // 기존 행이 첫 조각 — startedAt 은 그대로, endedAt 만 자정으로 확정된다.
        assertThat(active.getStartedAt()).isEqualTo(started);
        assertThat(active.getEndedAt()).isEqualTo(midnight);
        assertThat(active.getDurationSeconds()).isEqualTo(600L);
        // 반환은 now 가 속한 마지막 조각(클라가 이 id로 태깅한다).
        assertThat(result).isNotSameAs(active);
        assertThat(result.getStartedAt()).isEqualTo(midnight);
        assertThat(result.getEndedAt()).isEqualTo(now);
        assertThat(result.getDurationSeconds()).isEqualTo(2400L);
        assertThat(result.getBook()).isSameAs(book);
        assertThat(result.isManualEntry()).isFalse();
        assertThat(result.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("stop #11: 클램프가 분할보다 먼저다 — 20:00 시작 + 9시간이면 6h cap 뒤 02:00까지만 두 조각")
    void stop_clampBeforeSplit() {
        Instant started = kst("2026-06-01T20:00");
        ReadingSession active = ReadingSession.start(user, started);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, started.plusSeconds(9 * 3600));

        verify(sessionRepository, times(2)).save(any(ReadingSession.class));
        // 분할을 먼저 하고 조각마다 클램프하면 마지막 조각이 05:00까지 살아남는다 — 그 꼴이 아님을 단언.
        assertThat(result.getEndedAt()).isEqualTo(kst("2026-06-02T02:00"));
        assertThat(active.getDurationSeconds() + result.getDurationSeconds()).isEqualTo(6 * 3600L);
    }

    @Test
    @DisplayName("stop #12: 자정을 안 넘기면 지금처럼 1행만 저장한다(핫패스 회귀 방지)")
    void stop_withinOneDay_savesOnce() {
        Instant started = kst("2026-06-01T10:00");
        ReadingSession active = ReadingSession.start(user, started);
        when(sessionRepository.findByUserAndEndedAtIsNull(user)).thenReturn(Optional.of(active));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.stop(user, started.plusSeconds(1800));

        verify(sessionRepository, times(1)).save(any(ReadingSession.class));
        assertThat(result).isSameAs(active);
        assertThat(result.getDurationSeconds()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("closeStaleSessions #13: 22:00 방치 세션은 익일 04:00(cap)으로 닫히며 2조각이 되고 closed는 여전히 1이다")
    void closeStaleSessions_acrossMidnight_countsOriginalSessions() {
        Instant started = kst("2026-06-01T22:00");
        ReadingSession stale = ReadingSession.start(user, started);
        when(sessionRepository.findByEndedAtIsNullAndStartedAtBefore(any(Instant.class)))
                .thenReturn(List.of(stale));
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        int closed = service.closeStaleSessions(started.plusSeconds(21 * 3600));

        assertThat(closed).isEqualTo(1); // 조각 수가 아니라 원본 세션 수
        verify(sessionRepository, times(2)).save(any(ReadingSession.class));
        assertThat(stale.getEndedAt()).isEqualTo(LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant());
        assertThat(stale.getDurationSeconds()).isEqualTo(2 * 3600L);
    }

    @Test
    @DisplayName("recordManual #14: 자정을 걸친 수동 기록은 조각마다 manualEntry=true·같은 책이고 마지막 조각을 반환한다")
    void recordManual_acrossMidnight_splitsKeepingManualFlag() {
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        Instant started = kst("2026-06-01T22:40");
        Instant ended = kst("2026-06-02T00:40");
        Instant midnight = LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant();
        org.mockito.ArgumentCaptor<ReadingSession> saved = org.mockito.ArgumentCaptor.forClass(ReadingSession.class);
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.recordManual(user, started, ended, book);

        verify(sessionRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(s -> {
            assertThat(s.isManualEntry()).isTrue();
            assertThat(s.getBook()).isSameAs(book);
        });
        assertThat(saved.getAllValues().get(0).getStartedAt()).isEqualTo(started);
        assertThat(saved.getAllValues().get(0).getEndedAt()).isEqualTo(midnight);
        assertThat(result.getStartedAt()).isEqualTo(midnight);
        assertThat(result.getEndedAt()).isEqualTo(ended);
    }

    // ==========================================================================
    // 자정 분할 — tagBook 체인 워크 (조각 링크 = 시각 인접성)
    // ==========================================================================

    @Test
    @DisplayName("tagBook #15: 마지막 조각 id로 태깅하면 인접한 앞 조각까지 함께 태깅되고 책 시작 스탬프는 최초 조각 시각이다")
    void tagBook_chainsToAdjacentEarlierPiece() {
        Instant firstStart = kst("2026-06-01T23:50");
        Instant midnight = LocalDate.of(2026, 6, 2).atStartOfDay(KST).toInstant();
        Instant end = kst("2026-06-02T00:40");
        ReadingSession first = ReadingSession.start(user, firstStart);
        first.end(midnight);
        ReadingSession last = ReadingSession.start(user, midnight);
        last.end(end);
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.WANT_TO_READ);

        when(sessionRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(last));
        when(sessionRepository.findByUserAndEndedAtAndBookIsNullAndManualEntryFalse(user, midnight))
                .thenReturn(Optional.of(first));
        when(sessionRepository.findByUserAndEndedAtAndBookIsNullAndManualEntryFalse(user, firstStart))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        ReadingSession result = service.tagBook(user, 2L, book);

        assertThat(result).isSameAs(last);
        assertThat(last.getBook()).isSameAs(book);
        assertThat(first.getBook()).isSameAs(book); // 자정 전 몫이 미태깅으로 새지 않는다
        verify(sessionRepository).save(first);
        verify(sessionRepository).save(last);
        // 스탬프는 마지막 조각의 자정이 아니라 실제로 읽기 시작한 최초 조각 시각.
        assertThat(book.getStartedReadingAt()).isEqualTo(firstStart);
    }

    @Test
    @DisplayName("tagBook #16: 인접 조각이 없으면 현행 그대로 1건만 태깅한다")
    void tagBook_noAdjacentPiece_tagsOnlyOne() {
        Instant started = kst("2026-06-01T10:00");
        ReadingSession session = ReadingSession.start(user, started);
        session.end(started.plusSeconds(1800));
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);
        when(sessionRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(session));
        when(sessionRepository.findByUserAndEndedAtAndBookIsNullAndManualEntryFalse(eq(user), any(Instant.class)))
                .thenReturn(Optional.empty());
        when(sessionRepository.save(any(ReadingSession.class))).thenAnswer(returnsFirstArg());

        service.tagBook(user, 1L, book);

        verify(sessionRepository, times(1)).save(any(ReadingSession.class));
    }
}
