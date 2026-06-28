package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 독서 프로필 집계기 단위 테스트 — 영속성 없이 순수 집계 규칙만 본다(책BTI Phase 2).
 *
 * <p>경계값 전수가 핵심: 0권(콜드스타트) / 완독률 0 나눗셈 / 정독↔다독(평균 세션) / 진행 중 세션 제외 /
 * 저자 편향·다양성 / 장르 편식·잡식 / <b>null-state(저자·장르 없는 책)가 분포에 새지 않음</b>(N-055).
 */
class ReadingProfileAggregatorTest {

    private static final User READER =
            User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER);
    private static final Instant T0 = Instant.parse("2026-06-07T00:00:00Z");

    private static Book book(BookStatus status) {
        return Book.register(READER, "책", null, null, null, null, null, null, null, status);
    }

    private static Book book(BookStatus status, String author, String category, String pubDate) {
        return Book.register(READER, "책", author, null, null, null, null, category, pubDate, status);
    }

    /** 종료된(측정 길이 있는) 세션. */
    private static ReadingSession endedSession(long seconds) {
        ReadingSession s = ReadingSession.start(READER, T0);
        s.end(T0.plusSeconds(seconds));
        return s;
    }

    // ---- 0권 / 콜드스타트 경계 ----

    @Test
    @DisplayName("빈 책장: 모든 수가 0이고 완독률은 0.0(0 나눗셈 없음), 분포는 빈 목록")
    void emptyShelf_allZero() {
        ReadingProfile p = ReadingProfileAggregator.aggregate(List.of(), List.of());

        assertThat(p.totalBooks()).isZero();
        assertThat(p.finishedBooks()).isZero();
        assertThat(p.finishedRatio()).isEqualTo(0.0);
        assertThat(p.totalReadingSeconds()).isZero();
        assertThat(p.avgSessionSeconds()).isZero();
        assertThat(p.topAuthors()).isEmpty();
        assertThat(p.topGenres()).isEmpty();
    }

    @Test
    @DisplayName("null 입력도 빈 책장처럼 안전 처리한다")
    void nullInputs_treatedAsEmpty() {
        ReadingProfile p = ReadingProfileAggregator.aggregate(null, null);
        assertThat(p.totalBooks()).isZero();
        assertThat(p.totalReadingSeconds()).isZero();
    }

    // ---- 권수·상태 분포·완독률 ----

    @Test
    @DisplayName("상태 분포와 완독률: 완독/읽는중/읽고싶음을 세고 완독률을 소수 2자리로 낸다")
    void statusCountsAndFinishedRatio() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED),
                book(BookStatus.FINISHED),
                book(BookStatus.READING),
                book(BookStatus.WANT_TO_READ)));

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.totalBooks()).isEqualTo(4);
        assertThat(p.finishedBooks()).isEqualTo(2);
        assertThat(p.readingBooks()).isEqualTo(1);
        assertThat(p.wantToReadBooks()).isEqualTo(1);
        assertThat(p.finishedRatio()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("완독률 반올림: 1/3은 0.33으로 소수 2자리 반올림한다")
    void finishedRatio_roundsToTwoDecimals() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED),
                book(BookStatus.READING),
                book(BookStatus.READING)));

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.finishedRatio()).isEqualTo(0.33);
    }

    // ---- 시간·평균 세션(정독↔다독) ----

    @Test
    @DisplayName("시간 집계: 종료 세션 길이를 합하고 평균 세션 길이(정수 내림)를 낸다")
    void timeAndAvgSession() {
        List<ReadingSession> sessions = List.of(
                endedSession(1000), endedSession(500)); // 합 1500, 2세션

        ReadingProfile p = ReadingProfileAggregator.aggregate(List.of(), sessions);

        assertThat(p.totalReadingSeconds()).isEqualTo(1500);
        assertThat(p.finishedSessionCount()).isEqualTo(2);
        assertThat(p.avgSessionSeconds()).isEqualTo(750);
    }

    @Test
    @DisplayName("진행 중(미종료) 세션은 시간·세션 수 집계에서 제외한다")
    void activeSession_excluded() {
        ReadingSession active = ReadingSession.start(READER, T0); // 미종료 → duration 0, isActive
        List<ReadingSession> sessions = List.of(endedSession(600), active);

        ReadingProfile p = ReadingProfileAggregator.aggregate(List.of(), sessions);

        assertThat(p.totalReadingSeconds()).isEqualTo(600);
        assertThat(p.finishedSessionCount()).isEqualTo(1); // 진행 중 1건은 안 셈
        assertThat(p.avgSessionSeconds()).isEqualTo(600);
    }

    // ---- 저자 편향·다양성 (저자 없는 책은 제외 — N-055) ----

    @Test
    @DisplayName("저자 편향: 최다독 저자를 권수 내림차순으로, 다양성은 distinct 저자 수로 — 저자 없는 책은 제외")
    void topAuthorsAndDiversity_excludesBlankAuthor() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED, "김영하", null, null),
                book(BookStatus.READING, "김영하", null, null),
                book(BookStatus.FINISHED, "김영하", null, null),
                book(BookStatus.WANT_TO_READ, "정세랑", null, null),
                book(BookStatus.READING, "정세랑", null, null),
                book(BookStatus.FINISHED, "김초엽", null, null),
                book(BookStatus.WANT_TO_READ, null, null, null))); // 저자 없는 책 — 분포에 새면 안 됨

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.distinctAuthors()).isEqualTo(3); // null 저자 제외
        assertThat(p.topAuthors()).containsExactly(
                new LabeledCount("김영하", 3),
                new LabeledCount("정세랑", 2),
                new LabeledCount("김초엽", 1));
    }

    @Test
    @DisplayName("저자 동률·상한: 권수 같으면 이름 오름차순, 상위 N(3)만 — 결정적 정렬")
    void topAuthors_tieBreakByNameAndLimit() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED, "라작가", null, null),
                book(BookStatus.FINISHED, "나작가", null, null),
                book(BookStatus.FINISHED, "가작가", null, null),
                book(BookStatus.FINISHED, "다작가", null, null))); // 4명 각 1권

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.distinctAuthors()).isEqualTo(4);
        assertThat(p.topAuthors()).containsExactly( // 이름 오름차순 상위 3, 라작가 탈락
                new LabeledCount("가작가", 1),
                new LabeledCount("나작가", 1),
                new LabeledCount("다작가", 1));
    }

    @Test
    @DisplayName("옮긴이 제외: 글쓴이만 센다 — 같은 저자·다른 옮긴이 책은 합산, 옮긴이만 있는 책은 제외")
    void topAuthors_excludesTranslators() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED, "한강 (지은이), 김역자 (옮긴이)", null, null),
                book(BookStatus.FINISHED, "한강 (지은이), 박역자 (옮긴이)", null, null), // 다른 옮긴이 → 같은 글쓴이로 합산
                book(BookStatus.FINISHED, "연진희 (옮긴이)", null, null)));            // 옮긴이만 — 분포에 새면 안 됨

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.distinctAuthors()).isEqualTo(1); // 한강 하나(옮긴이만 책 제외)
        assertThat(p.topAuthors()).containsExactly(new LabeledCount("한강", 2));
    }

    // ---- 장르 편식·잡식 (장르 = categoryName 대분류, 장르 없는 책 제외) ----

    @Test
    @DisplayName("장르 분포: categoryName 대분류(2번째 세그먼트)로 묶고, 장르 없는 책은 제외")
    void topGenres_usesSecondSegment_excludesNull() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED, null, "국내도서>소설/시/희곡>한국소설", null),
                book(BookStatus.READING, null, "국내도서>소설/시/희곡>세계문학", null),
                book(BookStatus.WANT_TO_READ, null, "국내도서>경제경영>마케팅/세일즈", null),
                book(BookStatus.FINISHED, null, null, null))); // 장르 없는 책 — 제외

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.distinctGenres()).isEqualTo(2); // 소설/시/희곡, 경제경영 (null 제외)
        assertThat(p.topGenres()).containsExactly(
                new LabeledCount("소설/시/희곡", 2),
                new LabeledCount("경제경영", 1));
    }

    @Test
    @DisplayName("장르 추출 폴백: '>' 없는 단일 세그먼트 카테고리는 그 값을 장르로 쓴다")
    void primaryGenre_singleSegmentFallback() {
        List<Book> books = new ArrayList<>(List.of(
                book(BookStatus.FINISHED, null, "에세이", null)));

        ReadingProfile p = ReadingProfileAggregator.aggregate(books, List.of());

        assertThat(p.topGenres()).containsExactly(new LabeledCount("에세이", 1));
    }

}
