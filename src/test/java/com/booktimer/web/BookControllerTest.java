package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 내 책장 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>GET /books 셸, GET /books/readers, GET /books/{id} 상세, GET /books/{id}/buy* 유지 대상 테스트.
 * POST 뮤테이션은 BookApiControllerTest로 이관됨.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ReadingSessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    // ── GET /books 셸 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /books: 셸 뷰(books)를 그리고 myLoginId를 모델에 싣는다")
    void books_renders() throws Exception {
        User u = newUser("a@booktimer.com");
        u.assignLoginId("readera");
        userRepository.save(u);
        mockMvc.perform(get("/books").with(user("a@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("books"))
                .andExpect(model().attributeExists("myLoginId"));
    }

    @Test
    @DisplayName("GET /books: 내 책방(/u/{loginId}) 링크용 myLoginId를 모델에 싣는다")
    void books_includesMyLoginIdForBookstoreLink() throws Exception {
        User u = newUser("li@booktimer.com");
        u.assignLoginId("reader7");
        userRepository.save(u);

        mockMvc.perform(get("/books").with(user("reader7")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("myLoginId", "reader7"));
    }

    // ── GET /books/readers ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /books/readers: Vue 셸 렌더 — isbn을 모델에 싣고 book-readers 뷰 반환")
    void readers_rendersVueShell() throws Exception {
        newUser("rv@booktimer.com");
        mockMvc.perform(get("/books/readers").param("isbn", "9788900067890").with(user("rv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("book-readers"))
                .andExpect(model().attribute("isbn", "9788900067890"))
                .andExpect(model().attributeDoesNotExist("readers"));
    }

    @Test
    @DisplayName("GET /books/readers: isbn + title 모두 모델에 전달")
    void readers_passesIsbnAndTitle() throws Exception {
        newUser("re@booktimer.com");
        mockMvc.perform(get("/books/readers")
                        .param("isbn", "9780000000000")
                        .param("title", "테스트책")
                        .with(user("re@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isbn", "9780000000000"))
                .andExpect(model().attribute("title", "테스트책"));
    }

    // ── GET /books/{id} 상세 ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /books/{id}: 내 책이면 상세 화면을 그린다")
    void detail_rendersForOwner() throws Exception {
        User u = newUser("detail@booktimer.com");
        Book book = bookRepository.save(
                Book.register(u, "클린 코드", "로버트 마틴", null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}", book.getId()).with(user("detail@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("book-detail"))
                .andExpect(model().attributeExists("book", "months", "totalSeconds"))
                .andExpect(model().attributeDoesNotExist("graph", "history"));
    }

    @Test
    @DisplayName("GET /books/{id}: 누적 시간을 '시간/분'으로 적고 월별 스크롤 UI를 렌더한다")
    void detail_rendersIntegerTotalAndMonthlyBrowser_noGrass() throws Exception {
        User u = newUser("rt@booktimer.com");
        Book book = bookRepository.save(
                Book.register(u, "전쟁과 평화", null, null, null, null, null, BookStatus.READING));
        saveSession(u, book, "2026-06-01T01:00:00Z", 1800L);
        saveSession(u, book, "2026-06-02T01:00:00Z", 3600L);

        mockMvc.perform(get("/books/{id}", book.getId()).with(user("rt@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("누적 1시간 30분")))
                .andExpect(content().string(containsString("month-browser")))
                .andExpect(content().string(containsString("month-nav-label")))
                .andExpect(content().string(containsString("record-scroll")))
                .andExpect(content().string(not(containsString("독서 잔디"))))
                .andExpect(content().string(not(containsString("grass-grid"))));
    }

    @Test
    @DisplayName("GET /books/{id}: 남의 책이면 책장으로 돌려보낸다(IDOR 방지)")
    void detail_nonOwner_redirectsToBooks() throws Exception {
        User owner = newUser("downer@booktimer.com");
        User attacker = newUser("dattacker@booktimer.com");
        Book book = bookRepository.save(
                Book.register(owner, "도메인 주도 설계", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}", book.getId()).with(user("dattacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));
    }

    // ── 라우트 충돌 회귀: Vue 번들(/books/books-*.js)이 /books/{id}에 먹히면 안 됨 ──

    @Test
    @DisplayName("GET /books/books.js: 정적 Vue 번들이 book-detail에 가로채이지 않고 서빙된다")
    void bundlePath_notShadowedByDetail() throws Exception {
        newUser("rc@booktimer.com");
        mockMvc.perform(get("/books/books.js").with(user("rc@booktimer.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /books/{비숫자}: 숫자 아닌 id는 book-detail 매핑에 안 걸린다(404)")
    void detail_nonNumericId_notMatched() throws Exception {
        newUser("rc2@booktimer.com");
        mockMvc.perform(get("/books/not-a-number").with(user("rc2@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    // ── GET /books/{id}/buy ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /books/{id}/buy: 구매 클릭을 집계하고 제휴 링크로 리다이렉트")
    void buy_countsAndRedirectsToLink() throws Exception {
        User u = newUser("buyer@booktimer.com");
        Book book = bookRepository.save(Book.register(u, "클린 코드", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=x", BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy", book.getId()).with(user("buyer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://www.aladin.co.kr/buy?ttbkey=x"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /books/{id}/buy: 남의 책이면 집계 없이 책장으로(IDOR 방지)")
    void buy_nonOwner_noCountRedirectsToBooks() throws Exception {
        User owner = newUser("bowner@booktimer.com");
        User attacker = newUser("battacker@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "남의 책", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=x", BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy", book.getId()).with(user("battacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isZero();
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy: 공개책이면 구매 집계 후 링크로 리다이렉트")
    void buyFromProfile_publicBook_countsAndRedirectsToLink() throws Exception {
        User owner = newUser("shelfowner@booktimer.com");
        owner.assignLoginId("shelfowner");
        userRepository.save(owner);
        newUser("shelfviewer@booktimer.com");
        Book book = Book.register(owner, "공개 클린코드", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=z", BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy", "shelfowner", book.getId())
                        .with(user("shelfviewer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://www.aladin.co.kr/buy?ttbkey=z"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy: 비공개책이면 집계 없이 프로필로 복귀")
    void buyFromProfile_privateBook_noCountRedirectsToProfile() throws Exception {
        User owner = newUser("ppowner@booktimer.com");
        newUser("ppviewer@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "비공개 책", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=z", BookStatus.READING));

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy", "somehandle", book.getId())
                        .with(user("ppviewer@booktimer.com")))
                .andExpect(redirectedUrl("/u/somehandle"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isZero();
    }

    private void saveSession(User u, Book b, String startIso, long durationSec) {
        Instant start = Instant.parse(startIso);
        ReadingSession s = ReadingSession.start(u, start, b);
        s.end(start.plusSeconds(durationSec));
        sessionRepository.save(s);
    }
}
