package com.booktimer.web.api;

import com.booktimer.user.Role;
import com.booktimer.user.UserRegistrationService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /api/follow · /api/unfollow 컨트롤러 통합 테스트.
 *
 * <p>①인증 게이트(기본 잠김), ②CSRF 보호, ③팔로우 성공·following 반영,
 * ④자기 자신 팔로우 거부(400), ⑤없는 loginId 404, ⑥이미 팔로우 시 멱등.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

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
    @DisplayName("POST /api/follow 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void follow_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/follow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"someone\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/follow CSRF 없으면 403 (세션 쿠키 기반 CSRF 보호)")
    void follow_withoutCsrf_returns403() throws Exception {
        registrationService.register("fol-csrf@booktimer.com", "pw1234qwer!!", "folcsrfid", "CSRF팔로워", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/follow")
                        .with(user("fol-csrf@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"someone\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/follow 인증 + 유효 loginId → 200 {following:true}")
    void follow_authenticated_validTarget_returnsFollowingTrue() throws Exception {
        registrationService.register("fol-actor@booktimer.com", "pw1234qwer!!", "folactorid", "팔로워", SEOUL, Role.USER, today());
        registrationService.register("fol-target@booktimer.com", "pw1234qwer!!", "foltargetid", "팔로잉", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/follow")
                        .with(user("fol-actor@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"foltargetid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true));
    }

    @Test
    @DisplayName("POST /api/follow 자기 자신 팔로우 → 400 거부")
    void follow_self_returns400() throws Exception {
        registrationService.register("fol-self@booktimer.com", "pw1234qwer!!", "folselfid", "자기팔로워", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/follow")
                        .with(user("fol-self@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"folselfid\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/follow 없는 loginId → 404")
    void follow_nonexistentLoginId_returns404() throws Exception {
        registrationService.register("fol-nf@booktimer.com", "pw1234qwer!!", "folnfid", "존재안함테스터", SEOUL, Role.USER, today());

        mockMvc.perform(post("/api/follow")
                        .with(user("fol-nf@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"zz_nonexistent_xyz_9999\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/follow 이미 팔로우 중 → 멱등(200 {following:true})")
    void follow_alreadyFollowing_idempotent() throws Exception {
        registrationService.register("fol-idemp-a@booktimer.com", "pw1234qwer!!", "folidempaid", "멱등팔로워", SEOUL, Role.USER, today());
        registrationService.register("fol-idemp-b@booktimer.com", "pw1234qwer!!", "folidempbid", "멱등팔로잉", SEOUL, Role.USER, today());

        String body = "{\"loginId\":\"folidempbid\"}";

        // 첫 번째 팔로우
        mockMvc.perform(post("/api/follow")
                        .with(user("fol-idemp-a@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true));

        // 두 번째 팔로우 (멱등 — 예외 없이 following:true)
        mockMvc.perform(post("/api/follow")
                        .with(user("fol-idemp-a@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true));
    }

    @Test
    @DisplayName("POST /api/unfollow 팔로우 후 언팔로우 → following:false")
    void unfollow_afterFollow_returnsFollowingFalse() throws Exception {
        registrationService.register("unfol-a@booktimer.com", "pw1234qwer!!", "unfolaid", "언팔팔로워", SEOUL, Role.USER, today());
        registrationService.register("unfol-b@booktimer.com", "pw1234qwer!!", "unfolbid", "언팔팔로잉", SEOUL, Role.USER, today());

        // 팔로우
        mockMvc.perform(post("/api/follow")
                        .with(user("unfol-a@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"unfolbid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true));

        // 언팔로우
        mockMvc.perform(post("/api/unfollow")
                        .with(user("unfol-a@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"unfolbid\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false));
    }

    @Test
    @DisplayName("POST /api/unfollow 미인증 → 302 리다이렉트 (기본 잠김)")
    void unfollow_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/unfollow").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"someone\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
