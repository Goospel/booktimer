package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 전역 예외 처리 통합 테스트 — 컨트롤러에서 터진 예외를 whitelabel 대신 친절한 'error' 뷰로 변환.
 *
 * <p>인증은 됐지만(principal 존재) DB에 그 사용자가 없으면 DashboardController가
 * {@code IllegalStateException}을 던진다. 이런 예기치 못한 예외가 500 whitelabel로 새지 않고
 * {@link GlobalExceptionHandler}가 잡아 'error' 뷰(+상태 500)로 렌더되는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("컨트롤러에서 예외가 터지면 친절한 error 뷰(500)로 렌더한다 (whitelabel 아님)")
    void unhandledException_rendersErrorView() throws Exception {
        // 인증 주체는 있으나 DB에 미등록 → DashboardController가 IllegalStateException
        mockMvc.perform(get("/").with(user("ghost@booktimer.com")))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error"))
                .andExpect(model().attributeExists("status"));
    }

    @Test
    @DisplayName("존재하지 않는 리소스(favicon 등)는 500이 아니라 404로 응답한다 (핸들러가 삼키지 않음)")
    void missingResource_returns404NotSwallowedAs500() throws Exception {
        // 핸들러 매핑·정적 리소스 어디에도 없는 경로 → NoResourceFoundException(404).
        // @ExceptionHandler(Exception.class)가 이걸 잡아 500으로 만들면 안 된다.
        mockMvc.perform(get("/this-path-does-not-exist.xyz").with(user("ghost@booktimer.com")))
                .andExpect(status().isNotFound());
    }
}
