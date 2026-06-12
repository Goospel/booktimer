package com.booktimer.web;

import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchPage;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchType;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검색이 켜진(searchEnabled=true) 상태에서 검색 결과 행·추가 폼·검색기준 라디오가 실제로 렌더링되는지 본다.
 *
 * <p>BookControllerTest는 TTBKey가 없어 searchEnabled=false 경로만 타므로, 검색 결과가 그려지는 이 경로가
 * 통합테스트 사각지대였다(운영에서만 렌더). BookSearchClient를 mock으로 갈아끼워 검색을 켜고 그 사각지대를 메운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookSearchOnRenderingTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private BookSearchClient searchClient;

    private void login(String email) {
        userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), "독자", "Asia/Seoul", Role.USER));
    }

    /** title/author/publisher 모두 "test"를 품은 결과 한 건 — q="test"면 어떤 검색기준이든 후필터를 통과한다. */
    private BookSearchResult oneResult() {
        return new BookSearchResult("test title", "test author", "9788966260959",
                "http://example.com/cover.jpg", "test publisher", "http://example.com/buy", "국내도서>소설", "2020-01-01");
    }

    @Test
    @DisplayName("검색 ON + 출판사 기준: 검색 결과 화면이 200으로 렌더된다(500 아님)")
    void searchOn_publisherType_renders200() throws Exception {
        login("pub@booktimer.com");
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.search(any(), any(BookSearchType.class), anyInt()))
                .thenReturn(new BookSearchPage(List.of(oneResult()), 1, 40, 1));

        mockMvc.perform(get("/books").param("q", "test").param("type", "PUBLISHER")
                        .with(user("pub@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("book-add-form")))   // 추가 폼이 그려짐
                .andExpect(content().string(containsString("test publisher")));  // 출판사 표시
    }

    @Test
    @DisplayName("검색 ON + 제목/저자 기준: 각각 200으로 렌더된다")
    void searchOn_titleAndAuthor_render200() throws Exception {
        login("ta@booktimer.com");
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.search(any(), any(BookSearchType.class), anyInt()))
                .thenReturn(new BookSearchPage(List.of(oneResult()), 1, 40, 1));

        mockMvc.perform(get("/books").param("q", "test").param("type", "TITLE")
                        .with(user("ta@booktimer.com")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/books").param("q", "test").param("type", "AUTHOR")
                        .with(user("ta@booktimer.com")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("검색 ON: 검색기준 라디오가 제목·저자·출판사 셋 다 그려진다")
    void searchOn_rendersAllThreeRadios() throws Exception {
        login("radio@booktimer.com");
        when(searchClient.isEnabled()).thenReturn(true);
        when(searchClient.search(any(), any(BookSearchType.class), anyInt()))
                .thenReturn(BookSearchPage.empty(1, 40));

        mockMvc.perform(get("/books").param("q", "test").with(user("radio@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("제목")))
                .andExpect(content().string(containsString("저자")))
                .andExpect(content().string(containsString("출판사")));
    }
}
