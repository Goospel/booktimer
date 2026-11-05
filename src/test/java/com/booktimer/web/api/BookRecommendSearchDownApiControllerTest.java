package com.booktimer.web.api;

import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchUnavailableException;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AccountService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 장애에도 추천 문은 200 빈 추천이다(REQ-385) — <b>테스트 트랜잭션 밖에서</b> 잰다.
 *
 * <p>클래스에 {@code @Transactional}을 달면 이 경계를 못 본다: 추천 서비스의 트랜잭션 안에서 {@code BookService.search}가
 * 장애 예외를 던지면 공유 트랜잭션이 rollback-only로 찍혀, 서비스가 예외를 삼켜도 커밋 때
 * {@code UnexpectedRollbackException} → 500 HTML이 된다. 바깥 테스트 트랜잭션이 있으면 커밋이 일어나지 않아 초록으로 보인다.
 * 그래서 여기서는 행이 실제로 커밋되고, 끝에 계정 삭제로 치운다(공유 컨텍스트에 행을 남기지 않는다).
 */
@MockedBoundaryTest
class BookRecommendSearchDownApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String EMAIL = "recdown-notx@booktimer.com";
    private static final String PASSWORD = "pw1234qwer!!";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired AccountService accountService;
    @Autowired BookService bookService;
    @Autowired Clock clock;
    @Autowired BookSearchClient searchClient;

    @Test
    @DisplayName("REQ-385 · 저자 검색이 장애여도 GET /api/books/recommend는 200 빈 추천이다(트랜잭션 커밋 포함)")
    void recommend_searchDown_is200Empty() throws Exception {
        registrationService.register(EMAIL, PASSWORD, "recdownnotx", "독자", SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        try {
            User me = userRepository.findByLoginId("recdownnotx").orElseThrow();
            bookService.addFromSearch(me, new BookSearchResult("불편한 편의점", "김호연", "9791168340084",
                    null, "출판사", null), BookStatus.READING);
            when(searchClient.isEnabled()).thenReturn(true);
            when(searchClient.search(any(), any(), anyInt())).thenThrow(new BookSearchUnavailableException("x"));

            mockMvc.perform(get("/api/books/recommend").with(user(EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").doesNotExist())
                    .andExpect(jsonPath("$.results").isEmpty());
        } finally {
            accountService.deleteAccount(EMAIL, PASSWORD);
        }
    }
}
