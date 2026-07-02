package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.Yes24LinkBuilder;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Yes24 구매 엔드포인트 통합 테스트 (MockMvc + 실제 빈·H2) — 쿠팡 buy({@link CoupangBuyControllerTest})와 대칭.
 *
 * <p>Yes24 링크 생성은 {@link Yes24LinkBuilder}에 위임하므로 빌더를 mock해 활성 시나리오(링크 반환)를 만든다.
 *
 * <p>모바일 UA 분기(T-128)는 컨트롤러가 {@code User-Agent} 헤더를 읽어 {@link Yes24LinkBuilder#isMobileUserAgent}로
 * 판별한 뒤 서비스에 넘기는 배선만 본다 — UA 판별 경계값 자체는 {@link Yes24LinkBuilderTest}가 이미 커버하므로
 * 여기서는 iPhone UA(모바일 대표) 1개 + 헤더 없음(데스크톱 기본 경로) 조합으로 최소화한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Yes24BuyControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private Yes24LinkBuilder yes24LinkBuilder;

    private static final String YES24_LINK = "https://www.yes24.com/product/search?query=x&pid=LP1234567";
    private static final String YES24_MOBILE_LINK = "https://m.yes24.com/search?query=x";
    private static final String IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("GET /books/{id}/buy/yes24: Yes24 클릭을 집계하고 Yes24 검색 링크로 리다이렉트한다(UA 헤더 없음 = 데스크톱)")
    void buyYes24_countsAndRedirectsToYes24() throws Exception {
        when(yes24LinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(YES24_LINK);
        User u = newUser("ybuyer@booktimer.com");
        Book book = bookRepository.save(Book.register(u, "클린 코드", null, "9788966260959",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy/yes24", book.getId()).with(user("ybuyer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(YES24_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /books/{id}/buy/yes24: iPhone User-Agent면 모바일 링크로 리다이렉트한다(T-128)")
    void buyYes24_mobileUserAgent_redirectsToMobileLink() throws Exception {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(true))).thenReturn(YES24_MOBILE_LINK);
        User u = newUser("ybuyermobile@booktimer.com");
        Book book = bookRepository.save(Book.register(u, "클린 코드", null, "9788966260959",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy/yes24", book.getId())
                        .header("User-Agent", IPHONE_UA)
                        .with(user("ybuyermobile@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(YES24_MOBILE_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /books/{id}/buy/yes24: 남의 책이면 집계 없이 책장으로 돌려보낸다(IDOR 방지)")
    void buyYes24_nonOwner_redirectsToBooks() throws Exception {
        when(yes24LinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(YES24_LINK);
        User owner = newUser("ybowner@booktimer.com");
        newUser("ybattacker@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "남의 책", null, "9788900000001",
                null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}/buy/yes24", book.getId()).with(user("ybattacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getYes24ClickCount()).isZero();
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/yes24: 공개책이면 책 주인 카운트에 집계하고 Yes24 링크로 리다이렉트한다")
    void buyYes24FromProfile_publicBook_countsAndRedirects() throws Exception {
        when(yes24LinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(YES24_LINK);
        User owner = newUser("yshelfowner@booktimer.com");
        owner.assignLoginId("yshelfowner");
        userRepository.save(owner);
        newUser("yshelfviewer@booktimer.com");
        Book book = Book.register(owner, "공개 클린코드", null, "9788900000002",
                null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/yes24", "yshelfowner", book.getId())
                        .with(user("yshelfviewer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(YES24_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/yes24: iPhone User-Agent면 모바일 링크로 리다이렉트한다(T-128)")
    void buyYes24FromProfile_mobileUserAgent_redirectsToMobileLink() throws Exception {
        when(yes24LinkBuilder.buildSearchLink(any(), eq(true))).thenReturn(YES24_MOBILE_LINK);
        User owner = newUser("yshelfownermobile@booktimer.com");
        owner.assignLoginId("yshelfownermobile");
        userRepository.save(owner);
        newUser("yshelfviewermobile@booktimer.com");
        Book book = Book.register(owner, "공개 클린코드", null, "9788900000004",
                null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/yes24", "yshelfownermobile", book.getId())
                        .header("User-Agent", IPHONE_UA)
                        .with(user("yshelfviewermobile@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(YES24_MOBILE_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getYes24ClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/yes24: 비공개 책이면 집계 없이 그 프로필로 돌려보낸다(프라이버시 게이트)")
    void buyYes24FromProfile_privateBook_redirectsToProfile() throws Exception {
        when(yes24LinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(YES24_LINK);
        User owner = newUser("yppowner@booktimer.com");
        newUser("yppviewer@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "비공개 책", null, "9788900000003",
                null, null, null, BookStatus.READING)); // 기본 PRIVATE

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/yes24", "somehandle", book.getId())
                        .with(user("yppviewer@booktimer.com")))
                .andExpect(redirectedUrl("/u/somehandle"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getYes24ClickCount()).isZero();
    }

}
