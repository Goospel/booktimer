package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ReadingSession 도메인 메서드 테스트 (DB 무관 — 객체만 생성해 검증).
 *
 * <p>세션은 start로 시작(진행 중)하고 end로 종료한다. 종료 시
 * durationSeconds = endedAt - startedAt 을 계산한다. (이 값이 추후 ReadingTimer
 * remainingSeconds 차감에 쓰인다 — 다음 증분.)
 */
class ReadingSessionTest {

    private static final Instant T0 = Instant.parse("2026-06-01T09:00:00Z");

    private User sampleUser() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
    }

    @Test
    @DisplayName("start: 유저와 연결되고 시작시각 설정, 진행 중(종료 전) 상태")
    void start_initializesActiveSession() {
        User user = sampleUser();

        ReadingSession session = ReadingSession.start(user, T0);

        assertThat(session.getUser()).isSameAs(user);
        assertThat(session.getStartedAt()).isEqualTo(T0);
        assertThat(session.getEndedAt()).isNull();
        assertThat(session.getDurationSeconds()).isZero();
        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("start: user가 null이면 예외")
    void start_nullUser_throws() {
        assertThatThrownBy(() -> ReadingSession.start(null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start: startedAt이 null이면 예외")
    void start_nullStartedAt_throws() {
        assertThatThrownBy(() -> ReadingSession.start(sampleUser(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end: 종료시각 설정 + duration(초) 계산, 진행 중 아님")
    void end_setsDurationAndDeactivates() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        session.end(T0.plusSeconds(5400)); // 90분

        assertThat(session.getEndedAt()).isEqualTo(T0.plusSeconds(5400));
        assertThat(session.getDurationSeconds()).isEqualTo(5400L);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("end: endedAt이 startedAt보다 이르면(시계 역행) 예외")
    void end_beforeStart_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThatThrownBy(() -> session.end(T0.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end: endedAt이 null이면 예외")
    void end_nullEndedAt_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThatThrownBy(() -> session.end(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("end: 이미 종료된 세션을 다시 종료하면 예외")
    void end_alreadyEnded_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);
        session.end(T0.plusSeconds(60));

        assertThatThrownBy(() -> session.end(T0.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("end: startedAt == endedAt(0초)도 허용 — duration 0")
    void end_zeroDuration_allowed() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        session.end(T0);

        assertThat(session.getDurationSeconds()).isZero();
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("manual: 수동 입력 완료 세션을 만든다 — manualEntry=true, 이미 종료됨")
    void manual_createsCompletedManualEntry() {
        ReadingSession session = ReadingSession.manual(sampleUser(), T0, T0.plusSeconds(3600), null);

        assertThat(session.isManualEntry()).isTrue();
        assertThat(session.isActive()).isFalse();
        assertThat(session.getDurationSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("start: 실시간 측정은 수동 입력이 아니다 (manualEntry=false)")
    void start_isNotManualEntry() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThat(session.isManualEntry()).isFalse();
    }

    // --- tagBook (종료 후 태깅, 발견 1) ---

    @Test
    @DisplayName("tagBook: 책 미지정 세션에 나중에 책을 연결한다")
    void tagBook_linksBookToUntaggedSession() {
        User user = sampleUser();
        ReadingSession session = ReadingSession.start(user, T0); // book=null
        Book book = Book.register(user, "클린 코드", null, null, null, null, null, BookStatus.READING);

        session.tagBook(book);

        assertThat(session.getBook()).isSameAs(book);
    }

    @Test
    @DisplayName("tagBook: 이미 책이 지정된 세션에 태깅하면 예외(재태깅 금지)")
    void tagBook_alreadyTagged_throws() {
        User user = sampleUser();
        Book existing = Book.register(user, "기존 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, existing);
        Book other = Book.register(user, "다른 책", null, null, null, null, null, BookStatus.READING);

        assertThatThrownBy(() -> session.tagBook(other))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getBook()).isSameAs(existing); // 원래 책 보존
    }

    @Test
    @DisplayName("tagBook: null 책으로 태깅하면 예외")
    void tagBook_nullBook_throws() {
        ReadingSession session = ReadingSession.start(sampleUser(), T0);

        assertThatThrownBy(() -> session.tagBook(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
    // --- changeBook (진행 중 세션의 대상 교체, 핸드오프 3f) ---
    //
    // tagBook과 관심사가 달라 가드도 반대다: 종료된 세션은 거부하고(진행 중일 때만 바꾼다),
    // null(책 없이)은 허용한다. 두 메서드가 한 필드를 쓰지만 규칙을 섞지 않는다.

    @Test
    @DisplayName("changeBook: 진행 중 세션의 책을 다른 책으로 바꾼다")
    void changeBook_replacesBookOfActiveSession() {
        User user = sampleUser();
        Book started = Book.register(user, "시작한 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, started);
        Book other = Book.register(user, "바꾼 책", null, null, null, null, null, BookStatus.READING);

        session.changeBook(other);

        assertThat(session.getBook()).isSameAs(other);
    }

    @Test
    @DisplayName("changeBook: null 이면 「책 없이」로 되돌린다 — tagBook과 달리 허용한다")
    void changeBook_nullClearsBook() {
        User user = sampleUser();
        Book started = Book.register(user, "시작한 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, started);

        session.changeBook(null);

        assertThat(session.getBook()).isNull();
    }

    @Test
    @DisplayName("changeBook: 책 없이 시작한 세션에도 붙는다 — 양방향 전이")
    void changeBook_attachesToUntaggedActiveSession() {
        User user = sampleUser();
        ReadingSession session = ReadingSession.start(user, T0); // book=null
        Book book = Book.register(user, "고른 책", null, null, null, null, null, BookStatus.READING);

        session.changeBook(book);

        assertThat(session.getBook()).isSameAs(book);
    }

    @Test
    @DisplayName("changeBook: 이미 종료된 세션은 거부한다 — 끝난 기록의 대상을 바꾸는 문이 아니다")
    void changeBook_endedSession_throws() {
        User user = sampleUser();
        Book started = Book.register(user, "시작한 책", null, null, null, null, null, BookStatus.READING);
        ReadingSession session = ReadingSession.start(user, T0, started);
        session.end(T0.plusSeconds(600));
        Book other = Book.register(user, "다른 책", null, null, null, null, null, BookStatus.READING);

        assertThatThrownBy(() -> session.changeBook(other))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getBook()).isSameAs(started); // 원래 책 보존
    }
}
