package com.booktimer.web.api;

import com.booktimer.book.BookSearchClient;
import com.booktimer.book.BookSearchProbe;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchUnavailableException;
import com.booktimer.testsupport.MockedBoundaryTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/public/status/book-search — uptime 워크플로가 보는 익명 상태 문(REQ-389).
 *
 * <p>상태는 스케줄러 시각에 기대지 않고 {@link BookSearchProbe#check()}를 직접 불러 만든다. 감시 빈은 공유
 * 컨텍스트의 싱글턴이라 끝에 성공 스텁으로 한 번 더 불러 UP으로 되돌린다 — 다른 테스트에 DOWN이 새지 않게.
 * 본문은 글자 그대로 비교한다 — {@code health-probe.sh}가 {@code "status":"UP"}를 글자로 찾고, 익명 문이라
 * 키 · 예외 메시지가 섞이면 안 된다.
 */
@MockedBoundaryTest
class BookSearchStatusApiControllerTest {

    private static final BookSearchResult DEMIAN = new BookSearchResult(
            "데미안", "헤르만 헤세", "9788937460449", null, "민음사", null, null, null);

    @Autowired MockMvc mockMvc;
    @Autowired BookSearchClient searchClient;
    @Autowired BookSearchProbe probe;

    private void stubFound() {
        when(searchClient.isEnabled()).thenReturn(true);
        doReturn(Optional.of(DEMIAN)).when(searchClient).lookupByIsbn(anyString());
    }

    @AfterEach
    void backToUp() {
        stubFound();
        probe.check();
    }

    @Test
    @DisplayName("REQ-389 · 로그인 없이 200 {\"status\":\"UP\"}")
    void upWithoutLogin() throws Exception {
        stubFound();
        probe.check();

        mockMvc.perform(get("/api/public/status/book-search"))
                .andExpect(status().isOk())
                .andExpect(content().string("{\"status\":\"UP\"}"));
    }

    @Test
    @DisplayName("REQ-389 · DOWN이면 503 {\"status\":\"DOWN\"} — 키 · 예외 메시지를 싣지 않는다")
    void downIs503WithoutDetails() throws Exception {
        when(searchClient.isEnabled()).thenReturn(true);
        doThrow(new BookSearchUnavailableException("http=401 KakaoAK SECRET-KEY-123"))
                .when(searchClient).lookupByIsbn(anyString());
        probe.check();
        probe.check();

        mockMvc.perform(get("/api/public/status/book-search"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("{\"status\":\"DOWN\"}"));
    }
}
