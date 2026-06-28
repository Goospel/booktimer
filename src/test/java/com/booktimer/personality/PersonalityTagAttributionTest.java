package com.booktimer.personality;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 책BTI 태그 → 근거 책 귀속(순수) 단위 테스트 — Phase 6b 3단계.
 *
 * <p>입력은 <b>공개 책 전체</b>(상태 무관). 출력은 그 안에서 <b>완독</b>이면서 태그에 부합하는 책만.
 * 비공개 책은 호출자(서비스)가 미리 거른다 — 여기선 공개 입력만 받는다는 계약(누출 방지는 §4).
 */
class PersonalityTagAttributionTest {

    private static final User READER =
            User.of("reader@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER);

    private static Book book(String title, BookStatus status, String author, String category) {
        return Book.register(READER, title, author, null, null, null, null, category, null, status);
    }

    // ─── 장르 라벨 → 그 종족 대분류 책 ─────────────────────────────────────

    @Test
    @DisplayName("장르 태그 → 그 종족의 대분류 책만(다른 종족 제외)")
    void genreTag_filtersByTribe() {
        Book novel = book("소설1", BookStatus.FINISHED, "작가A", "국내도서>소설/시/희곡>한국소설");
        Book history = book("역사1", BookStatus.FINISHED, "작가B", "국내도서>역사>한국사");
        Book science = book("과학1", BookStatus.FINISHED, "작가C", "국내도서>과학>물리");

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(novel, history, science), "이야기파");

        assertThat(result).containsExactly(novel);
    }

    @Test
    @DisplayName("여러 대분류가 같은 종족이면 모두 포함(소설+만화→이야기파)")
    void genreTag_multipleCategoriesSameTribe() {
        Book novel = book("소설1", BookStatus.FINISHED, "A", "국내도서>소설/시/희곡>한국소설");
        Book comic = book("만화1", BookStatus.FINISHED, "B", "국내도서>만화>액션");
        Book light = book("라노벨1", BookStatus.FINISHED, "C", "국내도서>라이트노벨>판타지");

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(novel, comic, light), "이야기파");

        assertThat(result).containsExactlyInAnyOrder(novel, comic, light);
    }

    @Test
    @DisplayName("장르 태그 → 미완독(읽는중·읽고싶음) 책 제외")
    void genreTag_excludesNonFinished() {
        Book finished = book("완독소설", BookStatus.FINISHED, "A", "국내도서>소설/시/희곡>한국소설");
        Book reading = book("읽는중소설", BookStatus.READING, "B", "국내도서>소설/시/희곡>한국소설");
        Book want = book("읽고싶은소설", BookStatus.WANT_TO_READ, "C", "국내도서>소설/시/희곡>한국소설");

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(finished, reading, want), "이야기파");

        assertThat(result).containsExactly(finished);
    }

    @Test
    @DisplayName("미매핑 대분류 책은 종족 필터에서 안전 제외")
    void genreTag_excludesUnmappedCategories() {
        Book novel = book("소설1", BookStatus.FINISHED, "A", "국내도서>소설/시/희곡>한국소설");
        Book unknown = book("미매핑", BookStatus.FINISHED, "B", "국내도서>알 수 없는 분류>x");
        Book nullCat = book("카테고리없음", BookStatus.FINISHED, "C", null);

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(novel, unknown, nullCat), "이야기파");

        assertThat(result).containsExactly(novel);
    }

    // ─── 한우물형 → 최다독 저자 ─────────────────────────────────────────────

    @Test
    @DisplayName("한우물형 → 완독 책 중 최다독 저자 책만")
    void loyalTag_filtersByTopAuthor() {
        Book a1 = book("A1", BookStatus.FINISHED, "저자A", "국내도서>소설/시/희곡>한국소설");
        Book a2 = book("A2", BookStatus.FINISHED, "저자A", "국내도서>역사>한국사");
        Book b1 = book("B1", BookStatus.FINISHED, "저자B", "국내도서>소설/시/희곡>한국소설");

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(a1, a2, b1), "한우물형");

        assertThat(result).containsExactlyInAnyOrder(a1, a2);
    }

    @Test
    @DisplayName("한우물형 → 동률 시 결정적(저자명 오름차순으로 1명)")
    void loyalTag_tieBreaksByAuthorAsc() {
        // 저자Z 2권, 저자A 2권 → 동률, 가나다 오름차순으로 "저자A" 선택
        Book z1 = book("Z1", BookStatus.FINISHED, "저자Z", "국내도서>소설/시/희곡>x");
        Book z2 = book("Z2", BookStatus.FINISHED, "저자Z", "국내도서>소설/시/희곡>x");
        Book a1 = book("A1", BookStatus.FINISHED, "저자A", "국내도서>소설/시/희곡>x");
        Book a2 = book("A2", BookStatus.FINISHED, "저자A", "국내도서>소설/시/희곡>x");

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(z1, z2, a1, a2), "한우물형");

        assertThat(result).containsExactlyInAnyOrder(a1, a2);
    }

    @Test
    @DisplayName("한우물형 → 최다독 '글쓴이'(옮긴이 제외) 기준으로 책 선택")
    void loyalTag_topAuthorExcludesTranslator() {
        // 옮긴이가 달라 author 원문은 서로 다르지만, 글쓴이는 모두 '무라카미 하루키'
        Book h1 = book("H1", BookStatus.FINISHED, "무라카미 하루키 (지은이), 양윤옥 (옮긴이)", "국내도서>소설/시/희곡>일본소설");
        Book h2 = book("H2", BookStatus.FINISHED, "무라카미 하루키 (지은이), 홍은주 (옮긴이)", "국내도서>소설/시/희곡>일본소설");
        Book other = book("O1", BookStatus.FINISHED, "정유정 (지은이)", "국내도서>소설/시/희곡>한국소설");

        List<Book> result = PersonalityTagAttribution.booksForTag(List.of(h1, h2, other), "한우물형");

        assertThat(result).containsExactlyInAnyOrder(h1, h2); // 글쓴이 기준 최다독 = 하루키 2권
    }

    @Test
    @DisplayName("한우물형 → 완독 아닌 책 / null·blank 저자 제외")
    void loyalTag_excludesNonFinishedAndBlankAuthor() {
        Book finishedA = book("A1", BookStatus.FINISHED, "저자A", "국내도서>소설/시/희곡>x");
        Book readingA = book("A2", BookStatus.READING, "저자A", "국내도서>소설/시/희곡>x");
        Book finishedBlank = book("blank", BookStatus.FINISHED, "  ", "국내도서>소설/시/희곡>x");
        Book finishedNull = book("noAuthor", BookStatus.FINISHED, null, "국내도서>소설/시/희곡>x");

        List<Book> result = PersonalityTagAttribution.booksForTag(
                List.of(finishedA, readingA, finishedBlank, finishedNull), "한우물형");

        assertThat(result).containsExactly(finishedA);
    }

    // ─── 알 수 없는 태그 / 빈 입력 ─────────────────────────────────────────

    @Test
    @DisplayName("알 수 없는 태그(폭 라벨·오타) → 빈 목록")
    void unknownTag_returnsEmpty() {
        Book novel = book("소설1", BookStatus.FINISHED, "A", "국내도서>소설/시/희곡>한국소설");

        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), "외길형")).isEmpty();
        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), "잡식가")).isEmpty();
        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), "균형형")).isEmpty();
        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), "엉뚱한태그")).isEmpty();
    }

    @Test
    @DisplayName("null·blank 태그 → 빈 목록")
    void nullOrBlankTag_returnsEmpty() {
        Book novel = book("소설1", BookStatus.FINISHED, "A", "국내도서>소설/시/희곡>한국소설");

        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), null)).isEmpty();
        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), "")).isEmpty();
        assertThat(PersonalityTagAttribution.booksForTag(List.of(novel), "   ")).isEmpty();
    }

    @Test
    @DisplayName("빈 책 목록 / null → 빈 목록")
    void emptyOrNullBooks_returnsEmpty() {
        assertThat(PersonalityTagAttribution.booksForTag(List.of(), "이야기파")).isEmpty();
        assertThat(PersonalityTagAttribution.booksForTag(null, "이야기파")).isEmpty();
    }
}
