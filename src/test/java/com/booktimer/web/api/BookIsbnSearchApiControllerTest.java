package com.booktimer.web.api;

import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchPage;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchType;
import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.testsupport.MockedBoundaryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/books/search?type=isbn — 앱 바코드 스캔이 부르는 ISBN 한 건 조회(v1 REQ-17).
 *
 * <p>{@code BookSearchClient}는 {@link MockedBoundaryTest} 세계의 목이다 — 운영 알라딘 키를 부르지 않는다.
 * 세 테스트가 나눠 재는 것: ① isbn이면 ItemLookUp 한 건(검색은 안 부른다) ② 형식이 틀리면 부르기 전에 빈 목록
 * ③ {@code type} 없는 기존 호출(미니앱)은 제목 검색 그대로.
 */
@MockedBoundaryTest
@Transactional
class BookIsbnSearchApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final String EMAIL = "isbnsearch@booktimer.com";
    private static final String ISBN = "9788931467507";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired Clock clock;
    @Autowired BookSearchClient searchClient;

    private void registerViewer() {
        registrationService.register(EMAIL, "pw1234qwer!!", "isbnsearch", "바코드", SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
    }

    @Test
    @DisplayName("REQ-17 · type=isbn은 ItemLookUp 한 건")
    void isbnType_looksUpOneBook() throws Exception {
        registerViewer();
        when(searchClient.lookupByIsbn(ISBN)).thenReturn(Optional.of(
                new BookSearchResult("바코드로 찾은 책", "저자", ISBN, null, "출판사", null)));

        mockMvc.perform(get("/api/books/search").param("q", "978-89-3146-750 7").param("type", "isbn")
                        .with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].isbn13").value(ISBN))
                .andExpect(jsonPath("$.results[0].title").value("바코드로 찾은 책"));

        verify(searchClient).lookupByIsbn(eq(ISBN));
        verify(searchClient, never()).search(any(), any(), anyInt());
    }

    @Test
    @DisplayName("REQ-17 · 형식이 틀린 ISBN은 빈 목록(500 아님)")
    void malformedIsbn_returnsEmptyWithoutLookup() throws Exception {
        registerViewer();

        for (String bad : List.of("abc", "978893146750", "97889314675070", "   ")) {
            mockMvc.perform(get("/api/books/search").param("q", bad).param("type", "isbn")
                            .with(user(EMAIL)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results", hasSize(0)));
        }

        verify(searchClient, never()).lookupByIsbn(anyString());
        verify(searchClient, never()).search(any(), any(), anyInt());
    }

    @Test
    @DisplayName("REQ-17 · type 없는 검색은 기존 제목 검색 그대로")
    void noType_keepsTitleSearch() throws Exception {
        registerViewer();
        when(searchClient.search(any(), any(BookSearchType.class), anyInt())).thenReturn(new BookSearchPage(
                List.of(new BookSearchResult("9788931467507 이야기", "저자", ISBN, null, "출판사", null)), 1, 1, 1));

        mockMvc.perform(get("/api/books/search").param("q", ISBN).with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)));

        verify(searchClient).search(eq(ISBN), eq(BookSearchType.TITLE), eq(1));
        verify(searchClient, never()).lookupByIsbn(anyString());
    }
}
