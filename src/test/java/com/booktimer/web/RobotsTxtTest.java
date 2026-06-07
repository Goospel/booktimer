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
 * robots.txt 공개 서빙 검증 — 크롤러(검색·AdSense)가 비인증으로 읽을 수 있어야 한다.
 *
 * <p>이 앱은 default-deny라 권한 매처에 명시하지 않으면 {@code /robots.txt}도 로그인으로 리다이렉트된다.
 * robots.txt가 HTML 로그인 페이지로 302 튕기면 크롤러에게 모호한 신호라(특히 AdSense 콘텐츠 심사),
 * "공개 정적 파일로 200이 떠야 한다"를 못 박는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RobotsTxtTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /robots.txt: 미인증으로 200 + 크롤 허용 지시문을 공개 서빙한다")
    void robotsTxt_isPubliclyServed() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User-agent: *")))
                .andExpect(content().string(containsString("Allow: /")));
    }
}
