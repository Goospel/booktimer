package com.booktimer.book;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 책BTI Phase 1b — 기존 책 카탈로그(장르·출간일) 백필 서비스 테스트 (실제 레포 H2 + 가짜 검색 클라이언트).
 *
 * <p>핵심 불변식: (1) {@code category}가 null이고 isbn13이 있는 책만 대상 — 이미 채워진 책·isbn 없는 책은
 * 건드리지 않는다(멱등·null-state 제외, N-055 정신), (2) 외부 비활성/조회 실패면 안전하게 건너뛴다,
 * (3) limit으로 한 번에 처리량을 제한한다(외부 호출량·요청 시간 통제).
 */
@SpringBootTest
@Transactional
class BookCatalogBackfillServiceTest {

    @Autowired
    private BookCatalogBackfillService backfillService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private BookSearchClient searchClient;

    private User newUser(String email) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "독자", "Asia/Seoul", Role.USER));
    }

    private Book saveBook(User u, String isbn13) {
        return bookRepository.save(Book.register(u, "책", null, isbn13, null, null, null, BookStatus.WANT_TO_READ));
    }

    @Test
    @DisplayName("백필: category가 null이고 isbn 있는 책을 알라딘 ItemLookUp으로 채운다")
    void backfill_fillsNullCategoryBookWithIsbn() {
        User u = newUser("fill@booktimer.com");
        Book book = saveBook(u, "9788900000001");
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.lookupByIsbn("9788900000001")).thenReturn(Optional.of(
                new BookSearchResult("책", null, "9788900000001", null, null, null,
                        "국내도서>소설/시/희곡>한국소설", "2020-03-15")));

        BackfillResult result = backfillService.backfill(50);

        Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(reloaded.getCategory()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(reloaded.getPubDate()).isEqualTo("2020-03-15");
        assertThat(result.updated()).isEqualTo(1);
    }

    @Test
    @DisplayName("백필 멱등: 이미 category가 있는 책은 대상이 아니다(조회조차 안 함)")
    void backfill_skipsBooksAlreadyHavingCategory() {
        User u = newUser("idem@booktimer.com");
        Book filled = bookRepository.save(Book.register(u, "이미찬책", null, "9788900000009",
                null, null, null, "이미>있는>장르", "2019-01-01", BookStatus.READING));
        when(searchClient.isEnabled()).thenReturn(true);

        BackfillResult result = backfillService.backfill(50);

        // 이미 채워진 책은 후보가 아니므로 ItemLookUp 호출이 일어나지 않는다
        verify(searchClient, never()).lookupByIsbn(any());
        assertThat(bookRepository.findById(filled.getId()).orElseThrow().getCategory()).isEqualTo("이미>있는>장르");
        assertThat(result.scanned()).isZero();
    }

    @Test
    @DisplayName("백필: isbn13이 없는 책은 조회 대상에서 제외(임의 lookup 방지·null-state 제외)")
    void backfill_skipsBooksWithoutIsbn() {
        User u = newUser("noisbn@booktimer.com");
        Book noIsbn = saveBook(u, null); // category null + isbn null
        when(searchClient.isEnabled()).thenReturn(true);

        BackfillResult result = backfillService.backfill(50);

        verify(searchClient, never()).lookupByIsbn(any());
        assertThat(bookRepository.findById(noIsbn.getId()).orElseThrow().getCategory()).isNull();
        assertThat(result.scanned()).isZero();
    }

    @Test
    @DisplayName("백필: 조회 결과가 없으면 그 책은 null로 두고 notFound로 센다(헛 write 없음)")
    void backfill_lookupEmpty_leavesNullAndCountsNotFound() {
        User u = newUser("nf@booktimer.com");
        Book book = saveBook(u, "9788900000002");
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.lookupByIsbn("9788900000002")).thenReturn(Optional.empty());

        BackfillResult result = backfillService.backfill(50);

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getCategory()).isNull();
        assertThat(result.updated()).isZero();
        assertThat(result.notFound()).isEqualTo(1);
    }

    @Test
    @DisplayName("백필: 검색 클라이언트가 비활성이면 아무것도 하지 않는다(키 없음)")
    void backfill_disabledClient_noOp() {
        User u = newUser("dis@booktimer.com");
        saveBook(u, "9788900000003");
        when(searchClient.isEnabled()).thenReturn(false);

        BackfillResult result = backfillService.backfill(50);

        verify(searchClient, never()).lookupByIsbn(any());
        assertThat(result.scanned()).isZero();
    }

    @Test
    @DisplayName("백필: limit으로 한 번에 처리할 권수를 제한한다(외부 호출량·요청시간 통제)")
    void backfill_respectsLimit() {
        User u = newUser("lim@booktimer.com");
        saveBook(u, "9788900000011");
        saveBook(u, "9788900000012");
        saveBook(u, "9788900000013");
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.lookupByIsbn(any())).thenReturn(Optional.of(
                new BookSearchResult("책", null, "x", null, null, null, "장르", "2020")));

        BackfillResult result = backfillService.backfill(2);

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.remaining()).isEqualTo(1); // 3권 중 2권만 처리 → 1권 남음
    }
}
