package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
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
