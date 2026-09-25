package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /history 셸 컨트롤러 통합 테스트 (선별 SPA 단계 1b 이후 슬림화).
 *
 * <p>데이터 단언(months·graph·weeklyShortfall)은 HistoryApiControllerTest로 이관.
 * 이 테스트는 ①인증·뷰 배선, ②닉네임 모델, ③광고 불변식(스캐폴드 안전), ④<b>CSRF 메타</b>를 검증한다.
 * ④가 빠지면 기록 화면의 [책 붙이기]/[바꾸기](POST /api/sessions/{id}/book)가 통째로 403이 된다 —
 * 화면은 멀쩡히 뜨는데 아무것도 저장되지 않는 조용한 고장이다(R2 PR-3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryControllerTest {

    private static final String SEOUL = "Asia/Seoul";
    private static final Pattern CSRF_META = Pattern.compile("name=\"_csrf\"[^>]*content=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /history: 로그인 사용자에게 셸 화면(뷰 'history')을 반환하고 nickname을 모델에 싣는다")
    void history_rendersShellForLoggedInUser() throws Exception {
        registrationService.register("hist@booktimer.com", "rawpw1234", "기록가", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("hist@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("history"))
                .andExpect(model().attribute("nickname", "기록가"));
    }

    @Test
    @DisplayName("GET /history: 광고 게시자 ID가 없으면(기본) 광고 스크립트가 새지 않는다 (스캐폴드 안전 불변식)")
    void history_noAdsWhenDisabled() throws Exception {
        registrationService.register("noads@booktimer.com", "rawpw1234", "무광고", SEOUL, Role.USER, today());

        mockMvc.perform(get("/history").with(user("noads@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("adsbygoogle"))));
    }

    @Test
    @DisplayName("GET /history: CSRF 메타를 내고, 그 토큰으로 정정 문 POST가 CSRF를 통과한다(403이 아니라 404)")
    void history_csrfMetaUnlocksAssignPost() throws Exception {
        registrationService.register("csrf@booktimer.com", "rawpw1234", "토큰가", SEOUL, Role.USER, today());

        MvcResult shell = mockMvc.perform(get("/history").with(user("csrf@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();
        Matcher m = CSRF_META.matcher(shell.getResponse().getContentAsString());
        assertThat(m.find()).as("/history의 _csrf 메타(content 비어 있지 않음)").isTrue();
        String token = m.group(1);
        // 세션은 Spring Session(SESSION 쿠키) 저장소에 있다 — MockHttpSession 객체가 아니라 그 쿠키를 되돌려 보낸다.
        Cookie session = shell.getResponse().getCookie("SESSION");
        assertThat(session).as("셸이 CSRF 토큰을 담은 세션 쿠키를 낸다").isNotNull();

        // 없는 측정 id → CSRF를 통과해야만 닿는 404(세션 IDOR 마스킹). 토큰이 틀리면 필터가 403으로 먼저 막는다.
        mockMvc.perform(post("/api/sessions/987654321/book").cookie(session).with(user("csrf@booktimer.com"))
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":null}"))
                .andExpect(status().isNotFound());
        // 대조군 — 같은 세션에 엉뚱한 토큰이면 403. 이게 403이 아니면 위 404는 CSRF를 잰 게 아니다.
        mockMvc.perform(post("/api/sessions/987654321/book").cookie(session).with(user("csrf@booktimer.com"))
                        .header("X-CSRF-TOKEN", "not-the-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bookId\":null}"))
                .andExpect(status().isForbidden());
    }
}
