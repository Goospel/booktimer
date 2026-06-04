package com.booktimer.book;

import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 책장 유스케이스 테스트 — 실제 레포(H2) + 가짜 검색 클라이언트(mock).
 *
 * <p>검색은 포트에 위임하므로 외부 API 없이 mock으로 검증하고, 등록/조회/상태변경/삭제는
 * 소유권(IDOR 방지)과 유저 격리를 본다.
 */
@SpringBootTest
@Transactional
class BookServiceTest {

    @Autowired
    private BookService bookService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ReadingSessionRepository sessionRepository;

    @MockitoBean
    private BookSearchClient searchClient;

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    private static BookSearchResult cleanCode() {
        return new BookSearchResult("클린 코드", "로버트 마틴", "9788966260959",
                "http://cover/clean.jpg", "인사이트", "http://aladin/buy?ttbkey=x");
    }

    @Test
    @DisplayName("검색 결과로 책을 등록하면 메타데이터가 모두 저장된다")
    void addFromSearch_savesMetadata() {
        User u = newUser("a@booktimer.com");

        Book saved = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("클린 코드");
        assertThat(saved.getAuthor()).isEqualTo("로버트 마틴");
        assertThat(saved.getIsbn13()).isEqualTo("9788966260959");
        assertThat(saved.getCoverUrl()).isEqualTo("http://cover/clean.jpg");
        assertThat(saved.getPurchaseLink()).isEqualTo("http://aladin/buy?ttbkey=x");
        assertThat(saved.getStatus()).isEqualTo(BookStatus.WANT_TO_READ);
    }

    @Test
    @DisplayName("수동 입력으로도 책을 등록할 수 있다(제목/저자/상태)")
    void addManual_savesMinimal() {
        User u = newUser("b@booktimer.com");

        Book saved = bookService.addManual(u, "이펙티브 자바", "조슈아 블로크", BookStatus.READING);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("이펙티브 자바");
        assertThat(saved.getAuthor()).isEqualTo("조슈아 블로크");
        assertThat(saved.getStatus()).isEqualTo(BookStatus.READING);
        assertThat(saved.getIsbn13()).isNull();
    }

    @Test
    @DisplayName("내 책장은 내 책만 보인다(유저 격리)")
    void myBooks_isolatedPerUser() {
        User me = newUser("me@booktimer.com");
        User other = newUser("other@booktimer.com");
        bookService.addManual(me, "내 책", null, BookStatus.READING);
        bookService.addManual(other, "남의 책", null, BookStatus.READING);

        List<Book> mine = bookService.myBooks(me);

        assertThat(mine).extracting(Book::getTitle).containsExactly("내 책");
    }

    @Test
    @DisplayName("상태 변경은 소유자만 가능하다")
    void changeStatus_byOwner() {
        User u = newUser("c@booktimer.com");
        Book book = bookService.addManual(u, "리팩터링", null, BookStatus.WANT_TO_READ);

        Book updated = bookService.changeStatus(u, book.getId(), BookStatus.FINISHED);

        assertThat(updated.getStatus()).isEqualTo(BookStatus.FINISHED);
    }

    @Test
    @DisplayName("남의 책 상태 변경은 거부된다(IDOR 방지)")
    void changeStatus_rejectsNonOwner() {
        User owner = newUser("owner@booktimer.com");
        User attacker = newUser("attacker@booktimer.com");
        Book book = bookService.addManual(owner, "도메인 주도 설계", null, BookStatus.READING);

        assertThatThrownBy(() -> bookService.changeStatus(attacker, book.getId(), BookStatus.FINISHED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("삭제는 소유자만 가능하고, 남의 책 삭제는 거부된다")
    void delete_ownershipEnforced() {
        User owner = newUser("o2@booktimer.com");
        User attacker = newUser("a2@booktimer.com");
        Book book = bookService.addManual(owner, "TCP/IP", null, BookStatus.READING);

        assertThatThrownBy(() -> bookService.delete(attacker, book.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        bookService.delete(owner, book.getId());
        assertThat(bookService.myBooks(owner)).isEmpty();
    }

    @Test
    @DisplayName("읽은 적 있는 책도 삭제된다 — 측정 세션은 '책 미지정'으로 남아 독서 기록이 보존된다")
    void delete_unlinksReadingSessions_keepsHistory() {
        User u = newUser("read@booktimer.com");
        Book book = bookService.addManual(u, "리눅스 커널 내부구조", null, BookStatus.READING);
        // 타이머로 30분 읽은 세션을 이 책에 연결한다 → reading_session.book_id 가 이 책을 가리킨다.
        ReadingSession session = ReadingSession.start(u, Instant.parse("2026-06-01T10:00:00Z"), book);
        session.end(Instant.parse("2026-06-01T10:30:00Z"));
        sessionRepository.save(session);

        bookService.delete(u, book.getId()); // FK 위반 없이 삭제되어야 한다

        assertThat(bookService.myBooks(u)).isEmpty();
        ReadingSession reloaded = sessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getBook()).isNull(); // 책 연결만 풀린다(세션은 남는다)
        assertThat(reloaded.getDurationSeconds()).isEqualTo(1800L); // 읽은 시간은 보존
    }

    @Test
    @DisplayName("구매 클릭: 내 책이고 링크가 있으면 카운트를 올리고 구매링크를 돌려준다")
    void recordPurchaseClick_ownedWithLink_countsAndReturnsLink() {
        User u = newUser("buy@booktimer.com");
        Book book = bookService.addFromSearch(u, cleanCode(), BookStatus.WANT_TO_READ);

        String link = bookService.recordPurchaseClick(u, book.getId());

        assertThat(link).isEqualTo("http://aladin/buy?ttbkey=x");
        assertThat(bookService.myBooks(u).get(0).getClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("구매 클릭: 남의 책이면 거부된다(IDOR 방지)")
    void recordPurchaseClick_nonOwner_rejected() {
        User owner = newUser("o3@booktimer.com");
        User attacker = newUser("a3@booktimer.com");
        Book book = bookService.addFromSearch(owner, cleanCode(), BookStatus.WANT_TO_READ);

        assertThatThrownBy(() -> bookService.recordPurchaseClick(attacker, book.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(bookService.myBooks(owner).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("구매 클릭: 구매링크가 없으면 null을 돌려주고 카운트를 올리지 않는다")
    void recordPurchaseClick_noLink_returnsNullNoCount() {
        User u = newUser("nolink@booktimer.com");
        Book book = bookService.addManual(u, "수동 책", null, BookStatus.READING); // 링크 없음

        String link = bookService.recordPurchaseClick(u, book.getId());

        assertThat(link).isNull();
        assertThat(bookService.myBooks(u).get(0).getClickCount()).isZero();
    }

    @Test
    @DisplayName("공개 설정: 새 책은 비공개 기본이고, 소유자는 공개/비공개를 바꿀 수 있다")
    void setVisibility_byOwner() {
        User u = newUser("vis@booktimer.com");
        Book book = bookService.addManual(u, "공개 토글 책", null, BookStatus.READING);
        assertThat(book.getVisibility()).isEqualTo(BookVisibility.PRIVATE); // 기본 비공개

        Book published = bookService.setVisibility(u, book.getId(), BookVisibility.PUBLIC);
        assertThat(published.getVisibility()).isEqualTo(BookVisibility.PUBLIC);

        Book hidden = bookService.setVisibility(u, book.getId(), BookVisibility.PRIVATE);
        assertThat(hidden.getVisibility()).isEqualTo(BookVisibility.PRIVATE);
    }

    @Test
    @DisplayName("공개 설정: 남의 책 공개 변경은 거부된다(IDOR 방지)")
    void setVisibility_rejectsNonOwner() {
        User owner = newUser("vo@booktimer.com");
        User attacker = newUser("va@booktimer.com");
        Book book = bookService.addManual(owner, "남의 책", null, BookStatus.READING);

        assertThatThrownBy(() -> bookService.setVisibility(attacker, book.getId(), BookVisibility.PUBLIC))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(bookService.myBooks(owner).get(0).getVisibility()).isEqualTo(BookVisibility.PRIVATE);
    }

    @Test
    @DisplayName("검색은 검색 클라이언트(포트)에 페이지와 함께 위임한다")
    void search_delegatesToClient() {
        when(searchClient.search("clean", 2))
                .thenReturn(new BookSearchPage(List.of(cleanCode()), 2, 10, 15));

        BookSearchPage page = bookService.search("clean", 2);

        assertThat(page.results()).hasSize(1);
        assertThat(page.results().get(0).title()).isEqualTo("클린 코드");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.hasNext()).isFalse(); // 15건/10 = 2페이지, 현재 2페이지 = 마지막
    }

    @Test
    @DisplayName("검색 활성 여부는 클라이언트 설정을 따른다")
    void searchEnabled_reflectsClient() {
        when(searchClient.isEnabled()).thenReturn(true);
        assertThat(bookService.searchEnabled()).isTrue();
    }
}
