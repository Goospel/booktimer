package com.booktimer.web.api;

import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchUnavailableException;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색 제공자 장애는 「0건」이 아니라 503 + 평문이다(REQ-384, T-278).
 *
 * <p>앱({@code client.ts} {@code errorMessage})은 본문이 {@code <}로 시작하지 않으면 그 글을 그대로 알림으로 띄운다 —
 * 그래서 HTML 오류 페이지가 아니라 평문이어야 앱 코드 변경 없이 문구가 보인다.
 */
@MockedBoundaryTest
@Transactional
class BookSearchUnavailableApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String EMAIL = "searchdown@booktimer.com";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired Clock clock;
    @Autowired BookSearchClient searchClient;

    private void registerViewer() {
        registrationService.register(EMAIL, "pw1234qwer!!", "searchdown", "장애", SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
    }

    @Test
    @DisplayName("REQ-384 · 제목 검색이 장애면 503 평문이다")
    void titleSearchDown_is503PlainText() throws Exception {
        registerViewer();
        when(searchClient.search(any(), any(), anyInt())).thenThrow(new BookSearchUnavailableException("x"));

        String body = mockMvc.perform(get("/api/books/search").param("q", "데미안").with(user(EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo(BookApiController.SEARCH_UNAVAILABLE);
        assertThat(body).as("HTML 문서가 오면 앱이 이 문구를 못 읽는다").doesNotStartWith("<");
    }

    @Test
    @DisplayName("REQ-384 · 바코드 ISBN 조회가 장애면 같은 503 평문이다")
    void isbnLookupDown_is503PlainText() throws Exception {
        registerViewer();
        when(searchClient.lookupByIsbn(anyString())).thenThrow(new BookSearchUnavailableException("x"));

        String body = mockMvc.perform(get("/api/books/search").param("q", "9788937460449").param("type", "isbn")
                        .with(user(EMAIL)))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo(BookApiController.SEARCH_UNAVAILABLE);
        assertThat(body).doesNotStartWith("<");
    }
}
