package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.KyoboLinkBuilder;
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
 * 교보문고 구매 엔드포인트 통합 테스트 (MockMvc + 실제 빈·H2) — Yes24 buy({@link Yes24BuyControllerTest})와 대칭.
 *
 * <p>교보 링크 생성은 {@link KyoboLinkBuilder}에 위임하므로 빌더를 mock해 활성 시나리오(링크 반환)를 만든다.
 *
 * <p>모바일 UA 분기(T-128 대칭)는 컨트롤러가 {@code User-Agent} 헤더를 읽어 {@link KyoboLinkBuilder#isMobileUserAgent}로
 * 판별한 뒤 서비스에 넘기는 배선만 본다 — UA 판별 경계값 자체는 {@link com.booktimer.book.KyoboLinkBuilderTest}가
 * 이미 커버하므로 여기서는 iPhone UA(모바일 대표) 1개 + 헤더 없음(데스크톱 기본 경로) 조합으로 최소화한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KyoboBuyControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private KyoboLinkBuilder kyoboLinkBuilder;

    private static final String KYOBO_LINK = "https://search.kyobobook.co.kr/search?keyword=x&a=LP1234567";
    private static final String KYOBO_MOBILE_LINK = "https://search.kyobobook.co.kr/search?keyword=x";
    private static final String IPHONE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";

    private User newUser(String email) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("GET /books/{id}/buy/kyobo: 교보 클릭을 집계하고 교보 검색 링크로 리다이렉트한다(UA 헤더 없음 = 데스크톱)")
    void buyKyobo_countsAndRedirectsToKyobo() throws Exception {
        when(kyoboLinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(KYOBO_LINK);
        User u = newUser("kbuyer@booktimer.com");
        Book book = bookRepository.save(Book.register(u, "클린 코드", null, "9788911110001",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy/kyobo", book.getId()).with(user("kbuyer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(KYOBO_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getKyoboClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /books/{id}/buy/kyobo: iPhone User-Agent면 모바일 링크로 리다이렉트한다(T-128 대칭)")
    void buyKyobo_mobileUserAgent_redirectsToMobileLink() throws Exception {
        when(kyoboLinkBuilder.buildSearchLink(any(), eq(true))).thenReturn(KYOBO_MOBILE_LINK);
        User u = newUser("kbuyermobile@booktimer.com");
        Book book = bookRepository.save(Book.register(u, "클린 코드", null, "9788911110002",
                null, null, null, BookStatus.WANT_TO_READ));

        mockMvc.perform(get("/books/{id}/buy/kyobo", book.getId())
                        .header("User-Agent", IPHONE_UA)
                        .with(user("kbuyermobile@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(KYOBO_MOBILE_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getKyoboClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /books/{id}/buy/kyobo: 남의 책이면 집계 없이 책장으로 돌려보낸다(IDOR 방지)")
    void buyKyobo_nonOwner_redirectsToBooks() throws Exception {
        when(kyoboLinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(KYOBO_LINK);
        User owner = newUser("kbowner@booktimer.com");
        newUser("kbattacker@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "남의 책", null, "9788911110003",
                null, null, null, BookStatus.READING));

        mockMvc.perform(get("/books/{id}/buy/kyobo", book.getId()).with(user("kbattacker@booktimer.com")))
                .andExpect(redirectedUrl("/books"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getKyoboClickCount()).isZero();
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/kyobo: 공개책이면 책 주인 카운트에 집계하고 교보 링크로 리다이렉트한다")
    void buyKyoboFromProfile_publicBook_countsAndRedirects() throws Exception {
        when(kyoboLinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(KYOBO_LINK);
        User owner = newUser("kshelfowner@booktimer.com");
        owner.assignLoginId("kshelfowner");
        userRepository.save(owner);
        newUser("kshelfviewer@booktimer.com");
        Book book = Book.register(owner, "공개 클린코드", null, "9788911110004",
                null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/kyobo", "kshelfowner", book.getId())
                        .with(user("kshelfviewer@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(KYOBO_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getKyoboClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/kyobo: iPhone User-Agent면 모바일 링크로 리다이렉트한다(T-128 대칭)")
    void buyKyoboFromProfile_mobileUserAgent_redirectsToMobileLink() throws Exception {
        when(kyoboLinkBuilder.buildSearchLink(any(), eq(true))).thenReturn(KYOBO_MOBILE_LINK);
        User owner = newUser("kshelfownermobile@booktimer.com");
        owner.assignLoginId("kshelfownermobile");
        userRepository.save(owner);
        newUser("kshelfviewermobile@booktimer.com");
        Book book = Book.register(owner, "공개 클린코드", null, "9788911110006",
                null, null, null, BookStatus.READING);
        book.makePublic();
        bookRepository.save(book);

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/kyobo", "kshelfownermobile", book.getId())
                        .header("User-Agent", IPHONE_UA)
                        .with(user("kshelfviewermobile@booktimer.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(KYOBO_MOBILE_LINK));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getKyoboClickCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /u/{loginId}/books/{id}/buy/kyobo: 비공개 책이면 집계 없이 그 프로필로 돌려보낸다(프라이버시 게이트)")
    void buyKyoboFromProfile_privateBook_redirectsToProfile() throws Exception {
        when(kyoboLinkBuilder.buildSearchLink(any(), anyBoolean())).thenReturn(KYOBO_LINK);
        User owner = newUser("kppowner@booktimer.com");
        newUser("kppviewer@booktimer.com");
        Book book = bookRepository.save(Book.register(owner, "비공개 책", null, "9788911110005",
                null, null, null, BookStatus.READING)); // 기본 PRIVATE

        mockMvc.perform(get("/u/{loginId}/books/{id}/buy/kyobo", "somehandle", book.getId())
                        .with(user("kppviewer@booktimer.com")))
                .andExpect(redirectedUrl("/u/somehandle"));

        assertThat(bookRepository.findById(book.getId()).orElseThrow().getKyoboClickCount()).isZero();
    }

}
