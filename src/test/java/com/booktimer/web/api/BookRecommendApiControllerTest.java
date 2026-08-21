package com.booktimer.web.api;

import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchPage;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchType;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/books/recommend — 「책 추가」 화면 추천의 <b>응답 계약</b>.
 *
 * <p>어떤 책이 뽑히는지는 {@code BookRecommendationServiceTest}가 이미 판다. 여기서 재는 것은
 * ① 인증 경계 ② 화면이 의지하는 계약(제목·근거·행 모양) ③ <b>추천이 없을 때 화면이 카드를 안 그릴 근거</b>다.
 *
 * <p>{@code BookSearchClient}를 이 클래스에서만 mock으로 갈아 끼운다 — 다른 API 테스트의 검색 동작에
 * 손대지 않으려고 별도 클래스로 뒀다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookRecommendApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired BookService bookService;
    @Autowired Clock clock;

    @MockitoBean BookSearchClient searchClient;

    private User register(String email, String loginId) {
        registrationService.register(email, "pw1234qwer!!", loginId, "독자", SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    private static BookSearchResult book(String title, String author, String isbn) {
        return new BookSearchResult(title, author, isbn, null, "출판사", null);
    }

    @Test
    @DisplayName("미인증이면 /login으로 302 — 남의 추천 근거가 새면 곧 남의 책장이 새는 것이다")
    void anonymous_redirects() throws Exception {
        mockMvc.perform(get("/api/books/recommend"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("제목·근거·행을 함께 준다 — 화면은 전략을 모른 채 라벨을 그대로 그린다")
    void returnsTitleReasonAndRows() throws Exception {
        User me = register("rec@a.com", "recme");
        bookService.addFromSearch(me, book("불편한 편의점", "김호연", "9791168340084"), BookStatus.READING);
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.search(eq("김호연"), eq(BookSearchType.AUTHOR), anyInt()))
                .thenReturn(new BookSearchPage(
                        List.of(book("망원동 브라더스", "김호연", "9788994120720")),
                        1, BookSearchClient.PAGE_SIZE, 1));

        mockMvc.perform(get("/api/books/recommend").with(user("rec@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("김호연의 다른 책"))
                .andExpect(jsonPath("$.reason").value("『불편한 편의점』을 읽으셨네요"))
                .andExpect(jsonPath("$.results[0].title").value("망원동 브라더스"))
                // 검색 결과와 같은 행이라 owned가 실려야 한다 — 화면이 한 컴포넌트를 재사용한다.
                .andExpect(jsonPath("$.results[0].owned").value(false));
    }

    @Test
    @DisplayName("뽑을 것이 없으면 title이 null이고 결과가 빈다 — 화면이 빈 카드를 세우지 않을 근거")
    void nothingToRecommend_givesNullTitle() throws Exception {
        register("empty@a.com", "emptyme");
        when(searchClient.isEnabled()).thenReturn(false);
        when(searchClient.bestsellers()).thenReturn(List.of());

        mockMvc.perform(get("/api/books/recommend").with(user("empty@a.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.results").isEmpty());
    }
}
