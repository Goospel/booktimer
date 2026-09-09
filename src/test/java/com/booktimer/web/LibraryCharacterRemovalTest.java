package com.booktimer.web;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서재 캐릭터 기능(작가 도감·먹이주기·프로필 아바타) 폐기 확인 테스트.
 *
 * <p>삭제 작업의 계측기다 — 「기능이 없어졌음」을 못 박는다. 엔드포인트는 매핑이 사라져
 * 404(NoResourceFoundException → GlobalExceptionHandler)여야 하고, 화면 본문에는
 * 스프라이트 심볼 마커가 없어야 한다.
 *
 * <p>마커 {@code id="sprite-}는 삭제 대상 {@code fragments/garden-character-sprites.html}에
 * 따옴표째 실린 리터럴이라 판별력이 있다(템플릿 렌더 결과에 그대로 나온다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LibraryCharacterRemovalTest {

    private static final String SPRITE_MARKER = "id=\"sprite-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private Clock clock;

    /**
     * 실제 가입 경로로 만든다 — ReadingTimer가 함께 생겨야 /settings가 200이고,
     * 온보딩을 마쳐야 GET /가 /onboarding으로 튕기지 않는다.
     */
    private User register(String email, String loginId) {
        User u = registrationService.register(email, "rawpw1234", "독서가", "Asia/Seoul", Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul")));
        u.assignLoginId(loginId);
        u.completeOnboarding();
        return userRepository.save(u);
    }

    // ── 1. 엔드포인트 소멸 ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /village: 매핑이 사라져 404 (서재 셸 폐기)")
    void village_isGone() throws Exception {
        register("gone-village@booktimer.com", "gonevillage");

        mockMvc.perform(get("/village").with(user("gone-village@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /garden: 레거시 리다이렉트도 함께 사라져 404")
    void garden_isGone() throws Exception {
        register("gone-garden@booktimer.com", "gonegarden");

        mockMvc.perform(get("/garden").with(user("gone-garden@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/garden: 도감 JSON API가 사라져 404")
    void gardenApi_isGone() throws Exception {
        register("gone-api@booktimer.com", "goneapi");

        mockMvc.perform(get("/api/garden").with(user("gone-api@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/garden/feed: 먹이주기 API가 사라져 404")
    void feedApi_isGone() throws Exception {
        register("gone-feed@booktimer.com", "gonefeed");

        mockMvc.perform(post("/api/garden/feed")
                        .with(user("gone-feed@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterCode\":\"han_gang\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /settings/profile-character: 프로필 아바타 선택이 사라져 404")
    void profileCharacterPost_isGone() throws Exception {
        register("gone-profchar@booktimer.com", "goneprofchar");

        mockMvc.perform(post("/settings/profile-character")
                        .with(user("gone-profchar@booktimer.com")).with(csrf())
                        .param("characterCode", "han_gang"))
                .andExpect(status().isNotFound());
    }

    // ── 2. 화면 잔재 0 ─────────────────────────────────────────────────

    @Test
    @DisplayName("대시보드·설정·공개 프로필 본문에 작가 스프라이트 심볼이 없다")
    void spritesGoneFromAllScreens() throws Exception {
        register("gone-sprite@booktimer.com", "gonesprite");

        mockMvc.perform(get("/").with(user("gone-sprite@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(SPRITE_MARKER))));

        mockMvc.perform(get("/settings").with(user("gone-sprite@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(SPRITE_MARKER))));

        register("gone-sprite-viewer@booktimer.com", "gonespriteviewer");
        mockMvc.perform(get("/u/{loginId}", "gonesprite").with(user("gone-sprite-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(SPRITE_MARKER))));
    }

    @Test
    @DisplayName("랜딩(미인증)에 서재 소개 섹션 앵커(#village)가 없다")
    void landingHasNoVillageAnchor() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"village\""))))
                .andExpect(content().string(not(containsString("#village"))));
    }
}
