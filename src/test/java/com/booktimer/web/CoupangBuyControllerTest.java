package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 쿠팡 구매 엔드포인트 통합 테스트 (MockMvc + 실제 빈·H2) — 알라딘 buy({@link BookControllerTest})와 대칭.
 *
 * <p>쿠팡 링크 생성은 {@link CoupangLinkBuilder}에 위임하므로 빌더를 mock해 활성 시나리오(링크 반환)를 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoupangBuyControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CoupangLinkBuilder coupangLinkBuilder;

    private static final String COUPANG_LINK = "https://www.coupang.com/np/search?q=x&lptag=AF1234567";

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("GET /books/{id}/buy/coupang: 쿠팡 클릭을 집계하고 쿠팡 검색 링크로 리다이렉트한다")
    void buyCoupang_countsAndRedirectsToCoupang() throws Exception {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User u = newUser("cbuyer@booktimer.com");
        Book book = bookRepository.save(Book.register(u, "클린 코드", null, "9788966260959",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy/coupang", book.getId()).with(user("cbuyer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(COUPANG_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getCoupangClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /books/{id}/buy/coupang: 남의 책이면 집계 없이 책장으로 돌려보낸다(IDOR 방지)")
    void buyCoupang_nonOwner_redirectsToBooks() throws Exception {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User owner = newUser("cbowner@booktimer.com");
        newUser("cbattacker@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "남의 책", null, "9788900000001",
                null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}/buy/coupang", book.getId()).with(user("cbattacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getCoupangClickCount()).isZero();
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/coupang: 공개책이면 책 주인 카운트에 집계하고 쿠팡 링크로 리다이렉트한다")
    void buyCoupangFromProfile_publicBook_countsAndRedirects() throws Exception {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User owner = newUser("cshelfowner@booktimer.com");
        owner.assignLoginId("cshelfowner");
        userRepository.save(owner);
        newUser("cshelfviewer@booktimer.com");
        Book book = Book.register(owner, "공개 클린코드", null, "9788900000002",
                null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/coupang", "cshelfowner", book.getId())
                        .with(user("cshelfviewer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(COUPANG_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getCoupangClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/coupang: 비공개 책이면 집계 없이 그 프로필로 돌려보낸다(프라이버시 게이트)")
    void buyCoupangFromProfile_privateBook_redirectsToProfile() throws Exception {
        when(coupangLinkBuilder.buildSearchLink(any())).thenReturn(COUPANG_LINK);
        User owner = newUser("cppowner@booktimer.com");
        newUser("cppviewer@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "비공개 책", null, "9788900000003",
                null, null, null, BookStatus.READING)); // 기본 PRIVATE

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/coupang", "somehandle", book.getId())
                        .with(user("cppviewer@booktimer.com")))
                .andExpect(redirectedUrl("/u/somehandle"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getCoupangClickCount()).isZero();
    }

    @Test
    @DisplayName("책장 뷰: 쿠팡 활성이면 쿠팡 버튼·공정위 고지문구가 렌더된다(coupangEnabled 게이트)")
    void booksView_coupangEnabled_showsButtonAndDisclosure() throws Exception {
        when(coupangLinkBuilder.isEnabled()).thenReturn(true);
        User u = newUser("cview@booktimer.com");
        bookRepository.save(Book.register(u, "클린 코드", null, "9788966260959",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books").with(user("cview@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/buy/coupang")))
                .andExpect(content().string(containsString("쿠팡 파트너스 활동")));
    }

    @Test
    @DisplayName("책장 뷰: 알라딘 링크+쿠팡 활성이면 '구매' 토글(buy-menu)로 묶여 알라딘·쿠팡 링크가 모두 펼쳐진다")
    void booksView_bothAvailable_rendersBuyMenuWithBothLinks() throws Exception {
        when(coupangLinkBuilder.isEnabled()).thenReturn(true);
        User u = newUser("cboth@booktimer.com");
        bookRepository.save(Book.register(u, "클린 코드", null, "9788966260959",
                null, null, "https://www.aladin.co.kr/clean", BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books").with(user("cboth@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("buy-menu")))
                // 토글 안에 알라딘 경로(/buy")와 쿠팡 경로(/buy/coupang) 둘 다 존재
                .andExpect(content().string(containsString("/buy\"")))
                .andExpect(content().string(containsString("/buy/coupang")));
    }

    @Test
    @DisplayName("책장 뷰: 쿠팡만 가능(알라딘 링크 없음)하면 토글 없이 단일 쿠팡 버튼만 — 불필요한 클릭 제거")
    void booksView_onlyCoupang_rendersSingleButtonNoMenu() throws Exception {
        when(coupangLinkBuilder.isEnabled()).thenReturn(true);
        User u = newUser("conly@booktimer.com");
        bookRepository.save(Book.register(u, "수동 책", null, null,
                null, null, null, BookStatus.WANT_TO_READ)); // purchaseLink 없음 → 쿠팡 1개뿐

        mockMvc.perform(get("/books").with(user("conly@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/buy/coupang")))
                .andExpect(content().string(not(containsString("buy-menu"))));
    }

    @Test
    @DisplayName("책장 뷰: 쿠팡 비활성(추적코드 미설정)이면 쿠팡 버튼·고지문구가 숨겨진다 — 알라딘만(점진 출시)")
    void booksView_coupangDisabled_hidesButtonAndDisclosure() throws Exception {
        // 빌더 stub 없음 → isEnabled()=false(비활성)
        User u = newUser("cviewoff@booktimer.com");
        bookRepository.save(Book.register(u, "클린 코드", null, "9788966260959",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books").with(user("cviewoff@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/buy/coupang"))))
                .andExpect(content().string(not(containsString("쿠팡 파트너스 활동"))));
    }
}
