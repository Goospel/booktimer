package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.popularity.FollowScopePopularity;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 내 책장 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>검색 자체는 서비스/어댑터 테스트가 보고, 여기선 화면 렌더·등록·삭제·소유권 와이어링을 본다.
 * 테스트 프로필엔 TTBKey가 없어 searchEnabled=false(검색이 네트워크를 타지 않음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("GET /books: 책장 화면을 그리고 모델을 싣는다")
    void books_renders() throws Exception {
        newUser("a@booktimer.com");

        mockMvc.perform(get("/books").with(user("a@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("books"))
                .andExpect(model().attributeExists("books", "statuses", "searchEnabled"))
                .andExpect(model().attribute("searchEnabled", false));
    }

    @Test
    @DisplayName("GET /books: 팔로우한 사용자의 PUBLIC 책 인기 카운트를 popularity 모델에 싣는다(§7.4)")
    @SuppressWarnings("unchecked")
    void books_includesFollowScopePopularity() throws Exception {
        User viewer = newUser("pv@booktimer.com");
        User followee = newUser("pf@booktimer.com");
        followRepository.save(Follow.of(viewer, followee));

        String isbn = "9788900012345";
        // 같은 책이 뷰어 책장에 있어야 그 isbn이 페이지에서 집계 대상으로 모인다.
        bookRepository.save(Book.register(viewer, "같은 책", null, isbn, null, null, null, BookStatus.WANT_TO_READ));
        Book followeeBook = Book.register(followee, "같은 책", null, isbn, null, null, null, BookStatus.READING);
        followeeBook.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(followeeBook);

        mockMvc.perform(get("/books").with(user("pv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("popularity"))
                .andExpect(result -> {
                    var popularity = (java.util.Map<String, FollowScopePopularity>)
                            result.getModelAndView().getModel().get("popularity");
                    assertThat(popularity.get(isbn).readCount()).isEqualTo(1); // 팔로이 1명 읽음
                    assertThat(popularity.get(isbn).wantCount()).isEqualTo(0);
                });
    }

    @Test
    @DisplayName("GET /books: 검색 기준 기본값은 제목(TITLE), ?type=AUTHOR면 저자")
    void books_searchType() throws Exception {
        newUser("st@booktimer.com");

        mockMvc.perform(get("/books").with(user("st@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchType", com.booktimer.book.BookSearchType.TITLE));

        mockMvc.perform(get("/books").param("type", "AUTHOR").with(user("st@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchType", com.booktimer.book.BookSearchType.AUTHOR));
    }

    @Test
    @DisplayName("GET /books?status=READING: 그 상태의 책만 책장에 싣는다(필터)")
    @SuppressWarnings("unchecked")
    void books_filteredByStatus() throws Exception {
        User u = newUser("filter@booktimer.com");
        bookRepository.save(Book.register(u, "읽는중책", null, null, null, null, null, BookStatus.READING));
        bookRepository.save(Book.register(u, "완독책", null, null, null, null, null, BookStatus.FINISHED));
        bookRepository.save(Book.register(u, "또읽는중", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books").param("status", "READING").with(user("filter@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfFilter", BookStatus.READING))
                .andExpect(result -> {
                    var books = (List<Book>) result.getModelAndView().getModel().get("books");
                    assertThat(books).extracting(Book::getStatus).containsOnly(BookStatus.READING);
                    assertThat(books).hasSize(2);
                });
    }

    @Test
    @DisplayName("GET /books: status가 없으면 전체를 싣고 shelfFilter는 null")
    @SuppressWarnings("unchecked")
    void books_noFilter_showsAll() throws Exception {
        User u = newUser("nofilter@booktimer.com");
        bookRepository.save(Book.register(u, "a", null, null, null, null, null, BookStatus.READING));
        bookRepository.save(Book.register(u, "b", null, null, null, null, null, BookStatus.FINISHED));

        mockMvc.perform(get("/books").with(user("nofilter@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shelfFilter", org.hamcrest.Matchers.nullValue()))
                .andExpect(result -> {
                    var books = (List<Book>) result.getModelAndView().getModel().get("books");
                    assertThat(books).hasSize(2);
                });
    }

    @Test
    @DisplayName("POST /books/add: 책을 책장에 추가한다")
    void add_savesBook() throws Exception {
        User u = newUser("b@booktimer.com");

        mockMvc.perform(post("/books/add")
                        .param("title", "클린 코드")
                        .param("author", "로버트 마틴")
                        .param("status", "READING")
                        .with(user("b@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/books"));

        List<Book> books = bookRepository.findByUserOrderByCreatedAtDesc(u);
        assertThat(books).extracting(Book::getTitle).containsExactly("클린 코드");
        assertThat(books.get(0).getStatus()).isEqualTo(BookStatus.READING);
    }

    @Test
    @DisplayName("POST /books/{id}/delete: 소유자는 삭제할 수 있다")
    void delete_byOwner() throws Exception {
        User u = newUser("c@booktimer.com");
        Book book = bookRepository.save(
                Book.register(u, "리팩터링", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/books/{id}/delete", book.getId())
                        .with(user("c@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findByUserOrderByCreatedAtDesc(u)).isEmpty();
    }

    @Test
    @DisplayName("남의 책 삭제 시도는 책을 지우지 못한다(IDOR 방지)")
    void delete_nonOwner_keepsBook() throws Exception {
        User owner = newUser("owner@booktimer.com");
        User attacker = newUser("attacker@booktimer.com");
        Book book = bookRepository.save(
                Book.register(owner, "도메인 주도 설계", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/books/{id}/delete", book.getId())
                        .with(user("attacker@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findByUserOrderByCreatedAtDesc(owner)).hasSize(1);
    }

    @Test
    @DisplayName("POST /books/{id}/visibility: 소유자는 책을 공개로 바꿀 수 있다")
    void setVisibility_byOwner() throws Exception {
        User u = newUser("vc@booktimer.com");
        Book book = bookRepository.save(
                Book.register(u, "공개할 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/books/{id}/visibility", book.getId())
                        .param("visibility", "PUBLIC")
                        .with(user("vc@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().isPublic()).isTrue();
    }

    @Test
    @DisplayName("POST /books/{id}/visibility: 남의 책 공개 변경은 막힌다(IDOR 방지)")
    void setVisibility_nonOwner_unchanged() throws Exception {
        User owner = newUser("vcowner@booktimer.com");
        User attacker = newUser("vcattacker@booktimer.com");
        Book book = bookRepository.save(
                Book.register(owner, "남의 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/books/{id}/visibility", book.getId())
                        .param("visibility", "PUBLIC")
                        .with(user("vcattacker@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().isPublic()).isFalse();
    }

    @Test
    @DisplayName("GET /books/{id}: 내 책이면 상세(책별 잔디·기록) 화면을 그린다")
    void detail_rendersForOwner() throws Exception {
        User u = newUser("detail@booktimer.com");
        Book book = bookRepository.save(
                Book.register(u, "클린 코드", "로버트 마틴", null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}", book.getId()).with(user("detail@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("book-detail"))
                .andExpect(model().attributeExists("book", "graph", "history"));
    }

    @Test
    @DisplayName("GET /books/{id}: 남의 책이면 상세를 안 보여주고 책장으로 돌려보낸다(IDOR 방지)")
    void detail_nonOwner_redirectsToBooks() throws Exception {
        User owner = newUser("downer@booktimer.com");
        User attacker = newUser("dattacker@booktimer.com");
        Book book = bookRepository.save(
                Book.register(owner, "도메인 주도 설계", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}", book.getId()).with(user("dattacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));
    }

    @Test
    @DisplayName("GET /books/{id}/buy: 구매 클릭을 집계하고 제휴 구매링크로 리다이렉트한다")
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
    @DisplayName("GET /books/{id}/buy: 남의 책이면 집계 없이 책장으로 돌려보낸다(IDOR 방지)")
    void buy_nonOwner_noCountRedirectsToBooks() throws Exception {
        User owner = newUser("bowner@booktimer.com");
        User attacker = newUser("battacker@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "남의 책", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=x", BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy", book.getId()).with(user("battacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isZero();
    }
}
