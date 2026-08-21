package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 「책 추가」 화면 추천 — 실제 레포(H2) + 가짜 검색 클라이언트(mock).
 *
 * <p>이 서비스에는 <b>추천 알고리즘이 없다</b>. 이미 있는 조각(성향 집계기 · 알라딘 저자 검색 · owned 판정)을
 * 잇는 배선이라, 여기서 재는 것도 「무엇을 근거로 삼는가」와 「근거가 없을 때 무엇을 하는가」 둘이다.
 */
@SpringBootTest
@Transactional
class BookRecommendationServiceTest {

    @Autowired
    private BookRecommendationService recommendationService;
    @Autowired
    private BookService bookService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private BookSearchClient searchClient;

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    private static BookSearchResult book(String title, String author, String isbn) {
        return new BookSearchResult(title, author, isbn, null, "출판사", null);
    }

    /** 저자 검색이 그 저자의 책들을 돌려주게 한다. `BookService.search`가 저자 필드로 한 번 더 거른다. */
    private void authorSearchReturns(String author, BookSearchResult... results) {
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.search(eq(author), eq(BookSearchType.AUTHOR), anyInt()))
                .thenReturn(new BookSearchPage(List.of(results), 1, BookSearchClient.PAGE_SIZE, results.length));
    }

    private void bestsellersReturn(BookSearchResult... results) {
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.bestsellers()).thenReturn(List.of(results));
    }

    /**
     * ⚠️ <b>이 설계의 핵심 가드다.</b> 성향 집계({@code ReadingProfileService.profileOf})는 책BTI용이라
     * <b>완독 책만</b> 센다. 그걸 그대로 쓰면 「책은 담았는데 완독은 아직 0권」인 사람에게 추천이 0건이 되는데,
     * 하필 그 사람들이 이 화면을 가장 많이 본다. 추천은 성향 분석이 아니라 <b>이어 읽기</b>다.
     */
    @Test
    @DisplayName("완독이 0권이어도 읽는 중인 책의 저자로 추천이 선다")
    void recommendsFromReadingBook_evenWithNoFinishedBooks() {
        User u = newUser("reading@booktimer.com");
        bookService.addFromSearch(u, book("불편한 편의점", "김호연", "9791168340084"), BookStatus.READING);
        authorSearchReturns("김호연", book("망원동 브라더스", "김호연", "9788994120720"));

        BookRecommendation rec = recommendationService.recommendFor(u);

        assertThat(rec.title()).contains("김호연");
        assertThat(rec.books()).extracting(BookSearchResult::title).containsExactly("망원동 브라더스");
        // 이유는 근거가 된 그 책을 가리켜야 한다 — 안 그러면 「왜 이 책인가」가 화면에서 사라진다.
        assertThat(rec.reason()).contains("불편한 편의점");
    }

    /**
     * 담기만 한 책(WANT_TO_READ)은 근거가 아니다 — 이유 문구가 「읽으셨네요」라 <b>거짓말이 된다</b>.
     * 데이터와 문구는 같은 것을 가리켜야 한다.
     */
    @Test
    @DisplayName("읽고 싶어 담기만 한 책의 저자는 근거로 쓰지 않는다")
    void wantToReadBooks_areNotUsedAsBasis() {
        User u = newUser("want@booktimer.com");
        bookService.addFromSearch(u, book("역행자", "자청", "9791165341909"), BookStatus.WANT_TO_READ);
        bestsellersReturn(book("소년이 온다", "한강", "9788936434120"));

        BookRecommendation rec = recommendationService.recommendFor(u);

        assertThat(rec.title()).doesNotContain("자청");
        assertThat(rec.books()).extracting(BookSearchResult::title).containsExactly("소년이 온다");
    }

    @Test
    @DisplayName("서재가 비면 베스트셀러로 채운다 — 화면이 가장 빈 순간이 신규 사용자다")
    void emptyShelf_fallsBackToBestsellers() {
        User u = newUser("cold@booktimer.com");
        bestsellersReturn(book("소년이 온다", "한강", "9788936434120"));

        BookRecommendation rec = recommendationService.recommendFor(u);

        assertThat(rec.title()).isEqualTo("요즘 많이 읽는 책");
        assertThat(rec.books()).hasSize(1);
    }

    @Test
    @DisplayName("이미 서재에 있는 책은 추천에서 빠진다 — 담은 책을 또 담으라고 하지 않는다")
    void ownedBooks_areExcluded() {
        User u = newUser("owned@booktimer.com");
        bookService.addFromSearch(u, book("불편한 편의점", "김호연", "9791168340084"), BookStatus.FINISHED);
        bookService.addFromSearch(u, book("망원동 브라더스", "김호연", "9788994120720"), BookStatus.FINISHED);
        authorSearchReturns("김호연",
                book("불편한 편의점", "김호연", "9791168340084"),   // 이미 있음
                book("망원동 브라더스", "김호연", "9788994120720"), // 이미 있음
                book("파우스터", "김호연", "9788925565873"));

        BookRecommendation rec = recommendationService.recommendFor(u);

        assertThat(rec.books()).extracting(BookSearchResult::title).containsExactly("파우스터");
    }

    /**
     * 저자가 비어 있는 책만 가진 사람 — 완성된 픽스처만 쓰면 영영 안 잡히는 null-state다(N-055 계열).
     * 근거를 못 고르면 조용히 빈 카드를 내보내지 말고 폴백으로 간다.
     */
    @Test
    @DisplayName("저자가 없는 책만 가졌으면 폴백으로 간다")
    void booksWithoutAuthor_fallBackToBestsellers() {
        User u = newUser("noauthor@booktimer.com");
        bookService.addFromSearch(u, book("저자 없는 책", null, "9780000000001"), BookStatus.READING);
        bestsellersReturn(book("소년이 온다", "한강", "9788936434120"));

        BookRecommendation rec = recommendationService.recommendFor(u);

        assertThat(rec.title()).isEqualTo("요즘 많이 읽는 책");
        assertThat(rec.books()).hasSize(1);
    }

    @Test
    @DisplayName("검색이 꺼져 있으면 빈 추천을 준다 — 키 없는 환경에서 화면이 깨지지 않는다")
    void searchDisabled_returnsEmpty() {
        User u = newUser("nokey@booktimer.com");
        when(searchClient.isEnabled()).thenReturn(false);
        when(searchClient.search(any(), any(), anyInt()))
                .thenReturn(BookSearchPage.empty(1, BookSearchClient.PAGE_SIZE));
        when(searchClient.bestsellers()).thenReturn(List.of());

        BookRecommendation rec = recommendationService.recommendFor(u);

        assertThat(rec.title()).isNull();
        assertThat(rec.books()).isEmpty();
    }

    @Test
    @DisplayName("남의 책은 근거로 세지 않는다 — 추천이 남의 책장을 새어 나오면 안 된다")
    void otherUsersBooks_areNotCounted() {
        User me = newUser("me@booktimer.com");
        User other = newUser("other@booktimer.com");
        bookService.addFromSearch(other, book("불편한 편의점", "김호연", "9791168340084"), BookStatus.FINISHED);
        bestsellersReturn(book("소년이 온다", "한강", "9788936434120"));

        BookRecommendation rec = recommendationService.recommendFor(me);

        assertThat(rec.title()).isEqualTo("요즘 많이 읽는 책"); // 내 서재는 비었으니 폴백
    }
}
