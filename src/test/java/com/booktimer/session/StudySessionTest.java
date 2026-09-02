package com.booktimer.session;

import com.booktimer.book.StudyBook;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StudySession 엔티티 단위 테스트 — 책 라벨의 두 문({@code tagBook}·{@code changeBook}) 가드.
 *
 * <p>두 문은 <b>가드가 서로 반대</b>다: tagBook은 <b>끝난</b> 세션에 책을 1회 붙이는 문이라 진행 중과
 * 재태깅을 거부하고 null도 거부하며, changeBook은 <b>재는 동안</b> 라벨을 고쳐 다는 문이라 여러 번
 * 허용하고 null(=「책 없이」로 되돌리기)도 받되 종료된 세션을 거부한다. 이 대칭이 깨지면 끝난 기록을
 * 사후에 조작할 수 있게 되므로 여기서 못 박는다(독서 {@code ReadingSession}과 같은 규율 + 진행 중 거부).
 */
class StudySessionTest {

    private static final Instant T0 = Instant.parse("2026-09-02T09:00:00Z");

    private User user;
    private StudyBook book;
    private StudyBook other;

    @BeforeEach
    void setUp() {
        user = User.of("student@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "공부벌레", "Asia/Seoul", Role.USER);
        book = StudyBook.register(user, "정보처리기사 실기", "저자", null, null, null, null);
        other = StudyBook.register(user, "토익 RC", "저자", null, null, null, null);
    }

    private StudySession ended() {
        StudySession session = StudySession.start(user, T0);
        session.end(T0.plusSeconds(1800));
        return session;
    }

    // ── tagBook (종료 후 태깅) ────────────────────────────────────────────────

    @Test
    @DisplayName("tagBook: 종료된 미태깅 세션에 책을 붙인다")
    void tagBook_attachesToEndedSession() {
        StudySession session = ended();

        session.tagBook(book);

        assertThat(session.getBook()).isSameAs(book);
    }

    @Test
    @DisplayName("tagBook: null은 거부한다 — 「책 없이」로 되돌리는 문은 changeBook이다")
    void tagBook_rejectsNull() {
        StudySession session = ended();

        assertThatThrownBy(() -> session.tagBook(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("tagBook: 진행 중 세션은 거부한다 — 재는 도중은 changeBook의 문이다")
    void tagBook_rejectsActiveSession() {
        StudySession active = StudySession.start(user, T0);

        assertThatThrownBy(() -> active.tagBook(book))
                .isInstanceOf(IllegalStateException.class);
        assertThat(active.getBook()).isNull();
    }

    @Test
    @DisplayName("tagBook: 이미 책이 있는 종료 세션의 재태깅은 거부한다(1회성)")
    void tagBook_rejectsRetagging() {
        StudySession session = ended();
        session.tagBook(book);

        assertThatThrownBy(() -> session.tagBook(other))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getBook()).isSameAs(book);
    }

    // ── changeBook (측정 중 교체) ─────────────────────────────────────────────

    @Test
    @DisplayName("changeBook: 진행 중 세션의 대상을 바꾼다 — 잰 시간이 통째로 새 책에 붙는다")
    void changeBook_swapsTargetOfActiveSession() {
        StudySession active = StudySession.start(user, T0, book);

        active.changeBook(other);

        assertThat(active.getBook()).isSameAs(other);
        assertThat(active.isActive()).isTrue();
    }

    @Test
    @DisplayName("changeBook: null이면 「책 없이」로 되돌린다")
    void changeBook_acceptsNull() {
        StudySession active = StudySession.start(user, T0, book);

        active.changeBook(null);

        assertThat(active.getBook()).isNull();
    }

    @Test
    @DisplayName("changeBook: 종료된 세션은 거부한다 — 끝난 기록의 대상을 바꾸는 문이 아니다")
    void changeBook_rejectsEndedSession() {
        StudySession session = ended();
        session.tagBook(book);

        assertThatThrownBy(() -> session.changeBook(other))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getBook()).isSameAs(book);
    }

    // ── start ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("start: 책을 지정하면 그 책으로, 2-인자면 책 없이 시작한다")
    void start_withAndWithoutBook() {
        assertThat(StudySession.start(user, T0, book).getBook()).isSameAs(book);
        assertThat(StudySession.start(user, T0).getBook()).isNull();
    }
}
