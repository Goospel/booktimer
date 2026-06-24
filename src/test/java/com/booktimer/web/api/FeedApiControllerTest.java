package com.booktimer.web.api;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.garden.FeedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/garden/feed 컨트롤러 통합 테스트.
 *
 * <p>인증·CSRF·미보유/먹이없음 400 반환을 검증한다.
 * 200 경로(보유 작가+독서 기록 픽스처)는 FeedingService 단위 테스트가 커버한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRegistrationService registrationService;
    @Autowired private Clock clock;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpObjectMapper() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("POST /api/garden/feed 미인증 → 302 로그인 리다이렉트")
    void feed_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/garden/feed").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterCode\":\"han_gang\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/garden/feed CSRF 없으면 403")
    void feed_withoutCsrf_returns403() throws Exception {
        registrationService.register("feed-csrf@booktimer.com", "pass1234", "피드사용자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/feed")
                        .with(user("feed-csrf@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterCode\":\"han_gang\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("보유하지 않은 작가 → 400 (신규 유저는 보유 캐릭터 없음)")
    void feed_unownedCharacter_returns400() throws Exception {
        registrationService.register("feed-unowned@booktimer.com", "pass1234", "미보유자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/feed")
                        .with(user("feed-unowned@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterCode\":\"han_gang\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("FeedResult 직렬화에 level·leveledUp·title 포함 (affection 진화)")
    void feedResult_serialization_hasLevelAndLeveledUp() throws Exception {
        var result = new FeedResult(5, "han_gang", 3, 2, "친해지는 중", true);
        String json = objectMapper.writeValueAsString(result);
        assertThat(json).contains("\"level\"").contains("\"leveledUp\"").contains("\"title\"");
    }

    @Test
    @DisplayName("인증 + 올바른 JSON 형태 → 200 or 400 (302·403은 아님)")
    void feed_authenticated_notRedirectOrForbidden() throws Exception {
        registrationService.register("feed-auth@booktimer.com", "pass1234", "인증자", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/garden/feed")
                        .with(user("feed-auth@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterCode\":\"han_gang\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 200 && status != 400) {
                        throw new AssertionError("Expected 200 or 400, got " + status);
                    }
                });
    }
}
