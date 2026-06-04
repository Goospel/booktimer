package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Book 도메인 규칙 단위 테스트 — 영속성 없이 상태 전이만 본다.
 *
 * <p>핵심: "읽기를 시작하면" 읽고싶음 → 읽는중으로 <b>자동</b> 전환하되, 이미 읽는중/완독인 책은
 * 건드리지 않는다(멱등). 완독한 책을 다시 펴서 측정해도 완독을 읽는중으로 되돌리지 않는다.
 */
class BookTest {

    private static User reader() {
        return User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER);
    }

    private static Book bookWith(BookStatus status) {
        return Book.register(reader(), "클린 코드", null, null, null, null, null, status);
    }

    @Test
    @DisplayName("startReading: 읽고싶음이면 읽는중으로 전환하고 true를 반환한다")
    void startReading_fromWantToRead_transitions() {
        Book book = bookWith(BookStatus.WANT_TO_READ);

        boolean changed = book.startReading();

        assertThat(changed).isTrue();
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("startReading: 이미 읽는중이면 그대로 두고 false를 반환한다(멱등)")
    void startReading_fromReading_noop() {
        Book book = bookWith(BookStatus.READING);

        boolean changed = book.startReading();

        assertThat(changed).isFalse();
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("startReading: 완독한 책은 되돌리지 않는다(false, 완독 유지)")
    void startReading_fromFinished_noop() {
        Book book = bookWith(BookStatus.FINISHED);

        boolean changed = book.startReading();

        assertThat(changed).isFalse();
        assertThat(book.getStatus()).isEqualTo(BookStatus.FINISHED);
    }

    @Test
    @DisplayName("새 책의 구매 클릭 수는 0에서 시작한다")
    void clickCount_startsAtZero() {
        assertThat(bookWith(BookStatus.WANT_TO_READ).getClickCount()).isZero();
    }

    @Test
    @DisplayName("새 책은 기본이 비공개(PRIVATE)다 — 공개는 사용자가 명시적으로 켠다")
    void visibility_defaultsToPrivate() {
        Book book = bookWith(BookStatus.WANT_TO_READ);
        assertThat(book.getVisibility()).isEqualTo(BookVisibility.PRIVATE);
        assertThat(book.isPublic()).isFalse();
    }

    @Test
    @DisplayName("makePublic/makePrivate: 책의 공개 여부를 토글한다")
    void visibility_toggle() {
        Book book = bookWith(BookStatus.READING);

        book.makePublic();
        assertThat(book.getVisibility()).isEqualTo(BookVisibility.PUBLIC);
        assertThat(book.isPublic()).isTrue();

        book.makePrivate();
        assertThat(book.getVisibility()).isEqualTo(BookVisibility.PRIVATE);
        assertThat(book.isPublic()).isFalse();
    }

    @Test
    @DisplayName("recordPurchaseClick: 호출할 때마다 구매 클릭 수가 1씩 증가한다")
    void recordPurchaseClick_increments() {
        Book book = bookWith(BookStatus.WANT_TO_READ);

        book.recordPurchaseClick();
        assertThat(book.getClickCount()).isEqualTo(1L);

        book.recordPurchaseClick();
        assertThat(book.getClickCount()).isEqualTo(2L);
    }
}
