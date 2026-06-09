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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
    @DisplayName("GET /books: 내 책방(/u/{loginId}) 링크용 loginId를 모델에 싣는다")
    void books_includesLoginIdForBookstoreLink() throws Exception {
        User u = newUser("li@booktimer.com");
        u.assignLoginId("reader7");
        userRepository.save(u);

        mockMvc.perform(get("/books").with(user("reader7")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("loginId", "reader7"));
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
    @DisplayName("GET /books: 내 책장의 책 isbn이 myShelfIsbns에 담긴다(검색 결과 '이미 있음' 배지 시드)")
    void books_exposesOwnedIsbns() throws Exception {
        User me = newUser("owner@booktimer.com");
        bookRepository.save(Book.register(me,
                "클린 코드", null, "9788966260959", null, null, null, BookStatus.WANT_TO_READ));
        mockMvc.perform(get("/books").with(user("owner@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("myShelfIsbns", hasItem("9788966260959")));
    }

    @Test
    @DisplayName("GET /books: isbn 없는 수동 책은 myShelfIsbns에 null로 새지 않는다")
    void books_excludesNullIsbn() throws Exception {
        User me = newUser("manual@booktimer.com");
        bookRepository.save(Book.register(me,
                "직접 추가", null, null, null, null, null, BookStatus.READING)); // isbn null
        mockMvc.perform(get("/books").with(user("manual@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("myShelfIsbns", not(hasItem(nullValue()))));
    }

    @Test
    @DisplayName("GET /books?status=READING: 다른 상태로 가진 책의 isbn도 myShelfIsbns에 포함(소유는 필터 무관)")
    void books_ownedIsbns_ignoreStatusFilter() throws Exception {
        User me = newUser("filter@booktimer.com");
        bookRepository.save(Book.register(me,
                "원하는 책", null, "9788900000077", null, null, null, BookStatus.WANT_TO_READ));
        mockMvc.perform(get("/books").param("status", "READING").with(user("filter@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("myShelfIsbns", hasItem("9788900000077")));
    }

    @Test
    @DisplayName("GET /books/readers: 인기 카운트 drill-down — 팔로우한 PUBLIC 독자 명단을 버킷별로 싣는다")
    @SuppressWarnings("unchecked")
    void readers_listsFollowScopeReaders() throws Exception {
        User viewer = newUser("rv@booktimer.com");
        User followee = userRepository.save(
                User.of("rf@booktimer.com", passwordEncoder.encode("rawpw1234"), "팔로이", "Asia/Seoul", Role.USER));
        User stranger = userRepository.save(
                User.of("rs@booktimer.com", passwordEncoder.encode("rawpw1234"), "모르는이", "Asia/Seoul", Role.USER));
        followRepository.save(Follow.of(viewer, followee));

        String isbn = "9788900067890";
        Book followeeBook = Book.register(followee, "같은 책", null, isbn, null, null, null, BookStatus.READING);
        followeeBook.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(followeeBook);
        // 팔로우 안 한 사람도 같은 책을 PUBLIC으로 가졌지만 — 명단엔 안 나와야 한다(스코프 경계).
        Book strangerBook = Book.register(stranger, "같은 책", null, isbn, null, null, null, BookStatus.READING);
        strangerBook.changeVisibility(BookVisibility.PUBLIC);
        bookRepository.save(strangerBook);

        mockMvc.perform(get("/books/readers").param("isbn", isbn).with(user("rv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("book-readers"))
                .andExpect(model().attributeExists("readers"))
                .andExpect(result -> {
                    var readers = (com.booktimer.popularity.FollowScopeReaders)
                            result.getModelAndView().getModel().get("readers");
                    assertThat(readers.reading()).extracting("nickname").containsExactly("팔로이");
                    assertThat(readers.wanting()).isEmpty();
                });
    }

    @Test
    @DisplayName("GET /books/readers: 모르는 isbn이면 빈 명단으로 정상 렌더(존재 누설 없음)")
    void readers_unknownIsbn_emptyButOk() throws Exception {
        newUser("re@booktimer.com");

        mockMvc.perform(get("/books/readers").param("isbn", "9780000000000").with(user("re@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("book-readers"))
                .andExpect(result -> {
                    var readers = (com.booktimer.popularity.FollowScopeReaders)
                            result.getModelAndView().getModel().get("readers");
                    assertThat(readers.isEmpty()).isTrue();
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
    @DisplayName("GET /books: HX-Request면 shelfPanel 프래그먼트만 반환(필터 클릭 부분 swap)하고 책 목록·hx-get을 담는다")
    void books_htmx_returnsFragment() throws Exception {
        User u = newUser("htmxshelf@booktimer.com");
        bookRepository.save(Book.register(u, "프래그먼트책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books").header("HX-Request", "true").with(user("htmxshelf@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("books :: shelfPanel"))
                // 프래그먼트가 필터 칩(hx-get)과 책 목록을 함께 담아야 부분 swap이 성립(전체 문서가 아님)
                .andExpect(content().string(containsString("hx-get")))
                .andExpect(content().string(containsString("프래그먼트책")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<html"))));
    }

    @Test
    @DisplayName("GET /books: 일반 요청(HX-Request 없음)은 전체 books 뷰를 반환한다(no-JS 폴백)")
    void books_nonHtmx_returnsFullView() throws Exception {
        newUser("plainshelf@booktimer.com");

        mockMvc.perform(get("/books").with(user("plainshelf@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("books"));
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
    @DisplayName("POST /books/add: 검색 결과의 장르(category)·출간일(pubDate)도 함께 적재된다 — 책BTI 입력 사슬")
    void add_persistsCatalogMetadata() throws Exception {
        User u = newUser("meta@booktimer.com");

        mockMvc.perform(post("/books/add")
                        .param("title", "한국소설책")
                        .param("isbn13", "9788900000001")
                        .param("category", "국내도서>소설/시/희곡>한국소설")
                        .param("pubDate", "2020-03-15")
                        .param("status", "WANT_TO_READ")
                        .with(user("meta@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/books"));

        Book saved = bookRepository.findByUserOrderByCreatedAtDesc(u).get(0);
        assertThat(saved.getCategory()).isEqualTo("국내도서>소설/시/희곡>한국소설");
        assertThat(saved.getPubDate()).isEqualTo("2020-03-15");
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
                .andExpect(redirectedUrl("/books"))
                // 토글 디자인이 상태를 이미 보여주므로 성공 플래시 메시지는 띄우지 않는다(중복 알림 제거).
                .andExpect(flash().attributeCount(0));

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
    @DisplayName("POST /books/{id}/visibility: HX-Request면 redirect 아닌 200 조각을 반환하고 DB도 바뀐다")
    void setVisibility_htmx_returnsFragment_not_redirect() throws Exception {
        User u = newUser("htmxvc@booktimer.com");
        Book book = bookRepository.save(
                Book.register(u, "htmx 테스트 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/books/{id}/visibility", book.getId())
                        .param("visibility", "PUBLIC")
                        .header("HX-Request", "true")
                        .with(user("htmxvc@booktimer.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vis-toggle-form")))
                .andExpect(content().string(containsString("aria-pressed=\"true\"")));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().isPublic()).isTrue();
    }

    @Test
    @DisplayName("POST /books/{id}/visibility: HX-Request + 남의 책이면 422를 반환하고 DB 불변")
    void setVisibility_htmx_idor_blocked() throws Exception {
        User owner = newUser("htmxowner@booktimer.com");
        User attacker = newUser("htmxattacker@booktimer.com");
        Book book = bookRepository.save(
                Book.register(owner, "남의 책", null, null, null, null, null, BookStatus.READING));

        mockMvc.perform(post("/books/{id}/visibility", book.getId())
                        .param("visibility", "PUBLIC")
                        .header("HX-Request", "true")
                        .with(user("htmxattacker@booktimer.com")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());

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

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy: 남의 책방 공개책이면 구매 클릭을 책 주인 카운트에 집계하고 제휴 링크로 리다이렉트한다")
    void buyFromProfile_publicBook_countsAndRedirectsToLink() throws Exception {
        User owner = newUser("shelfowner@booktimer.com");
        owner.assignLoginId("shelfowner");
        userRepository.save(owner);
        newUser("shelfviewer@booktimer.com"); // 다른 사람이 그 책방에서 구매를 누른다
        Book book = Book.register(owner, "공개 클린코드", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=z", BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy", "shelfowner", book.getId())
                        .with(user("shelfviewer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://www.aladin.co.kr/buy?ttbkey=z"));

        // 클릭은 누른 사람이 아니라 책 주인 행에 쌓인다
        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy: 비공개 책이면 집계 없이 그 프로필로 돌려보낸다(프라이버시 게이트)")
    void buyFromProfile_privateBook_noCountRedirectsToProfile() throws Exception {
        User owner = newUser("ppowner@booktimer.com");
        newUser("ppviewer@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "비공개 책", null, null, null, null,
                "http://www.aladin.co.kr/buy?ttbkey=z", BookStatus.READING)); // 기본 PRIVATE

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy", "somehandle", book.getId())
                        .with(user("ppviewer@booktimer.com")))
                .andExpect(redirectedUrl("/u/somehandle"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getClickCount()).isZero();
    }
}
