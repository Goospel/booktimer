package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
    @DisplayName("register: isbn13을 적재 시점에 정규화한다 — 하이픈 제거, 빈 값은 null(동일성 키 통일)")
    void register_normalizesIsbn13() {
        Book hyphenated = Book.register(reader(), "클린 코드", null, "978-89-954321-0-7",
                null, null, null, BookStatus.WANT_TO_READ);
        assertThat(hyphenated.getIsbn13()).isEqualTo("9788995432107");

        Book blankIsbn = Book.register(reader(), "리팩터링", null, "  ",
                null, null, null, BookStatus.WANT_TO_READ);
        assertThat(blankIsbn.getIsbn13()).isNull();
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
    @DisplayName("register(카탈로그 메타): 장르(category)·출간일(pubDate)을 적재한다 — 빈 값은 null로 정규화")
    void register_storesCatalogMetadata() {
        Book book = Book.register(reader(), "한국소설책", "어떤작가", "9788900000001",
                null, "출판사", null,
                "국내도서>소설/시/희곡>한국소설", "2020-03-15", BookStatus.READING);

        assertThat(book.getCategory()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(book.getPubDate()).isEqualTo("2020-03-15");

        // 빈/공백 메타는 null로 — 집계·백필에서 "메타 없음"과 동일 취급(동일성)
        Book blankMeta = Book.register(reader(), "메타빈책", null, null, null, null, null,
                "  ", "", BookStatus.WANT_TO_READ);
        assertThat(blankMeta.getCategory()).isNull();
        assertThat(blankMeta.getPubDate()).isNull();
    }

    @Test
    @DisplayName("register(메타 없는 기존 8-인자): category·pubDate는 null이다 — 기존 적재 경로 불변")
    void register_withoutMetadata_leavesCatalogNull() {
        Book book = bookWith(BookStatus.WANT_TO_READ);
        assertThat(book.getCategory()).isNull();
        assertThat(book.getPubDate()).isNull();
    }

    @Test
    @DisplayName("applyCatalogMetadata: 백필로 장르·출간일을 채운다 — 빈 값은 null로 정규화")
    void applyCatalogMetadata_fillsAndNormalizes() {
        Book book = bookWith(BookStatus.WANT_TO_READ); // category·pubDate null로 시작

        book.applyCatalogMetadata("국내도서>소설/시/희곡>한국소설", "2020-03-15");
        assertThat(book.getCategory()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(book.getPubDate()).isEqualTo("2020-03-15");

        // 빈/공백은 null로(register와 동일 정규화 — "없음"을 한 표기로)
        book.applyCatalogMetadata("  ", "");
        assertThat(book.getCategory()).isNull();
        assertThat(book.getPubDate()).isNull();
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

    // ── 완독 시각(finishedAt) — 책방 완독 정렬의 데이터 소스 ────────────────
    // 불변식: 완독(FINISHED) 상태 ⇒ finishedAt 존재(엔티티 단독 register 직후만 예외 — 서비스가 스탬프).

    private static final Instant T1 = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    @DisplayName("changeStatus: 완독으로 진입하면 완독 시각을 기록한다")
    void changeStatus_toFinished_stampsFinishedAt() {
        Book book = bookWith(BookStatus.READING);

        book.changeStatus(BookStatus.FINISHED, T1);

        assertThat(book.getStatus()).isEqualTo(BookStatus.FINISHED);
        assertThat(book.getFinishedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("changeStatus: 완독에서 이탈하면 완독 시각을 지운다(완독 아닌 책에 시각 잔존 금지)")
    void changeStatus_leavingFinished_clearsFinishedAt() {
        Book book = bookWith(BookStatus.READING);
        book.changeStatus(BookStatus.FINISHED, T1);

        book.changeStatus(BookStatus.READING, T2);

        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
        assertThat(book.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("changeStatus: 완독→완독 재저장은 기존 완독 시각을 유지한다(멱등 — 시각이 밀리지 않음)")
    void changeStatus_finishedToFinished_keepsStamp() {
        Book book = bookWith(BookStatus.READING);
        book.changeStatus(BookStatus.FINISHED, T1);

        book.changeStatus(BookStatus.FINISHED, T2);

        assertThat(book.getFinishedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("changeStatus: 이탈 후 재완독하면 새 시각을 기록한다")
    void changeStatus_refinish_stampsNewTime() {
        Book book = bookWith(BookStatus.READING);
        book.changeStatus(BookStatus.FINISHED, T1);
        book.changeStatus(BookStatus.READING, T1);

        book.changeStatus(BookStatus.FINISHED, T2);

        assertThat(book.getFinishedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("changeStatus: 완독인데 시각이 없으면 채운다 — 완독 상태로 등록된 직후의 서비스 스탬프 경로")
    void changeStatus_finishedWithoutStamp_heals() {
        Book book = bookWith(BookStatus.FINISHED); // register는 시각을 모름 → null로 시작

        book.changeStatus(BookStatus.FINISHED, T1);

        assertThat(book.getFinishedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("register: 엔티티 단독 등록은 완독 시각이 null이다 — 스탬프는 서비스(Clock 보유) 책임")
    void register_leavesFinishedAtNull() {
        assertThat(bookWith(BookStatus.FINISHED).getFinishedAt()).isNull();
        assertThat(bookWith(BookStatus.READING).getFinishedAt()).isNull();
    }
}
