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

        boolean changed = book.startReading(T1);

        assertThat(changed).isTrue();
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("startReading: 이미 읽는중이면 그대로 두고 false를 반환한다(멱등)")
    void startReading_fromReading_noop() {
        Book book = bookWith(BookStatus.READING);

        boolean changed = book.startReading(T1);

        assertThat(changed).isFalse();
        assertThat(book.getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("startReading: 완독한 책은 되돌리지 않는다(false, 완독 유지)")
    void startReading_fromFinished_noop() {
        Book book = bookWith(BookStatus.FINISHED);

        boolean changed = book.startReading(T1);

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

    // ── 첫 완독 시각(firstFinishedAt) — 홈 소식 피드 "완독했어요" 이벤트의 데이터 소스 ──────
    // finishedAt과 달리 책당 영구 1회: 완독 이탈에도 안 지우고, 재완독에도 안 민다(피드 재부상 방지).

    @Test
    @DisplayName("changeStatus: 첫 완독에 첫 완독 시각을 기록하고, 재완독해도 밀지 않는다 (피드 재부상 방지)")
    void changeStatus_refinish_keepsFirstFinishedAt() {
        Book book = bookWith(BookStatus.READING);
        book.changeStatus(BookStatus.FINISHED, T1);
        assertThat(book.getFirstFinishedAt()).isEqualTo(T1);

        book.changeStatus(BookStatus.READING, T1);
        book.changeStatus(BookStatus.FINISHED, T2);

        assertThat(book.getFirstFinishedAt()).isEqualTo(T1);
        assertThat(book.getFinishedAt()).isEqualTo(T2); // 책방 정렬용 finishedAt은 종전대로 재스탬프
    }

    @Test
    @DisplayName("changeStatus: 완독에서 이탈해도 첫 완독 시각은 지우지 않는다 (finishedAt만 클리어)")
    void changeStatus_leavingFinished_keepsFirstFinishedAt() {
        Book book = bookWith(BookStatus.READING);
        book.changeStatus(BookStatus.FINISHED, T1);

        book.changeStatus(BookStatus.READING, T2);

        assertThat(book.getFinishedAt()).isNull();
        assertThat(book.getFirstFinishedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("changeStatus: 완독 상태로 등록된 책(아카이빙)의 서비스 스탬프 경로도 첫 완독 시각을 채운다")
    void changeStatus_finishedWithoutStamp_stampsFirstFinishedAt() {
        // BookService.stampIfFinished → changeStatus(FINISHED, now) 힐링 분기. 스탬프를
        // "READING→FINISHED 전이"에만 걸면 아카이빙 책이 피드에서 통째로 사라진다.
        Book book = bookWith(BookStatus.FINISHED);

        book.changeStatus(BookStatus.FINISHED, T1);

        assertThat(book.getFirstFinishedAt()).isEqualTo(T1);
    }

    // ── 읽기 시작 시각(startedReadingAt) — 홈 소식 피드 "읽기 시작했어요" 이벤트의 데이터 소스 ──
    // finishedAt의 미러이되 한 곳이 다르다: 완독으로 넘어가도 지우지 않는다(시작·완독 두 이벤트 공존).

    @Test
    @DisplayName("changeStatus: 읽는중으로 진입하면 읽기 시작 시각을 기록한다")
    void changeStatus_toReading_stampsStartedReadingAt() {
        Book book = bookWith(BookStatus.WANT_TO_READ);

        book.changeStatus(BookStatus.READING, T1);

        assertThat(book.getStartedReadingAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("changeStatus: 읽는중→읽는중 재저장은 기존 시작 시각을 유지한다(멱등 — 시각이 밀리지 않음)")
    void changeStatus_readingToReading_keepsStamp() {
        Book book = bookWith(BookStatus.WANT_TO_READ);
        book.changeStatus(BookStatus.READING, T1);

        book.changeStatus(BookStatus.READING, T2);

        assertThat(book.getStartedReadingAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("changeStatus: 완독으로 넘어가도 시작 시각은 지우지 않는다(시작·완독 두 이벤트가 시간순 공존)")
    void changeStatus_toFinished_keepsStartedReadingAt() {
        Book book = bookWith(BookStatus.WANT_TO_READ);
        book.changeStatus(BookStatus.READING, T1);

        book.changeStatus(BookStatus.FINISHED, T2);

        assertThat(book.getStartedReadingAt()).isEqualTo(T1);
        assertThat(book.getFinishedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("changeStatus: 재독(완독→읽는중)도 첫 시작 시각을 유지한다 — 시작 이벤트는 책당 1회(피드 재부상 방지)")
    void changeStatus_reread_keepsFirstStamp() {
        Book book = bookWith(BookStatus.WANT_TO_READ);
        book.changeStatus(BookStatus.READING, T1);
        book.changeStatus(BookStatus.FINISHED, T1);

        book.changeStatus(BookStatus.READING, T2);

        assertThat(book.getStartedReadingAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("changeStatus: 시작 시각 없는 레거시 READING 책의 no-op 재저장은 스탬프하지 않는다 (가짜 「방금 시작」 금지)")
    void changeStatus_legacyReadingNoOp_doesNotStamp() {
        // V64 이전 책 = READING인데 startedReadingAt이 null. 상태 UI에서 같은 값을 다시 제출해도
        // now로 스탬프되면 수년 전 시작한 책이 "방금 읽기 시작했어요"로 피드에 뜬다.
        // 「startedReadingAt == null」 단독 조건으로 구현하는 변이를 죽이는 핀.
        Book legacy = bookWith(BookStatus.READING);

        legacy.changeStatus(BookStatus.READING, T2);

        assertThat(legacy.getStartedReadingAt()).isNull();
    }

    @Test
    @DisplayName("startReading(now): 전환이 일어나면 시작 시각을 스탬프하고, no-op이면 건드리지 않는다")
    void startReading_stampsOnlyOnTransition() {
        Book transitioned = bookWith(BookStatus.WANT_TO_READ);
        assertThat(transitioned.startReading(T1)).isTrue();
        assertThat(transitioned.getStartedReadingAt()).isEqualTo(T1);

        // 이미 읽는중/완독인 책은 전환이 없으므로 스탬프도 없다(기존 값을 밀지 않음)
        Book alreadyReading = bookWith(BookStatus.READING);
        assertThat(alreadyReading.startReading(T2)).isFalse();
        assertThat(alreadyReading.getStartedReadingAt()).isNull();
    }

    @Test
    @DisplayName("register: 엔티티 단독 등록은 시작 시각이 null이다 — 백필 안 한 기존 책과 같은 상태")
    void register_leavesStartedReadingAtNull() {
        assertThat(bookWith(BookStatus.READING).getStartedReadingAt()).isNull();
    }
}
