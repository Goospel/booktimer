package com.booktimer.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 개인정보처리방침 페이지 테스트 (MockMvc).
 *
 * <p>Google OAuth 동의 화면 게시(Production) 전제이자 개인정보보호법 고지 의무를 위해,
 * <b>인증 없이</b> 접근 가능한 정적 고지 페이지({@code privacy.html})를 서빙하는지 본다.
 * 미인증 접근이 로그인으로 리다이렉트되지 않고 200으로 떠야 한다(공개 링크라야 Google 동의 화면에 걸 수 있음).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PrivacyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /privacy: 인증 없이 200으로 처리방침 뷰를 렌더한다")
    void getPrivacy_isPublicAndRendersView() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacy"));
    }

    @Test
    @DisplayName("GET /privacy: 수집하는 개인정보 항목(이메일)을 고지한다")
    void getPrivacy_disclosesCollectedItems() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이메일")));
    }

    @Test
    @DisplayName("GET /terms: 인증 없이 200으로 이용약관 뷰를 렌더한다")
    void getTerms_isPublicAndRendersView() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("terms"))
                // 뷰 이름만 보면 템플릿이 깨져도(fragment 참조 오류 등) 통과할 수 있어, 실제 렌더 결과까지 본다.
                .andExpect(content().string(containsString("제1조 (목적)")));
    }
}
