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

/**
 * ads.txt 공개 서빙 검증 — AdSense 소유권 확인(및 수익 보호)에 쓰이는 {@code /ads.txt}가
 * <b>비인증</b>으로 누구나(=Google 크롤러) 접근 가능해야 한다.
 *
 * <p>이 앱은 default-deny라 권한 매처에 명시하지 않으면 {@code /ads.txt}도 로그인으로 리다이렉트된다
 * → 크롤러가 파일을 못 봐 소유권 검증 실패. 그래서 "공개 정적 파일로 200이 떠야 한다"를 못 박는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdsTxtTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /ads.txt: 미인증으로 200 + AdSense 게시자 라인을 공개 서빙한다")
    void adsTxt_isPubliclyServed() throws Exception {
        mockMvc.perform(get("/ads.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pub-9272639329788723")))
                .andExpect(content().string(containsString("DIRECT")));
    }
}
