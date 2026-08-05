package com.booktimer.security;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.user.AuthProvider;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bearer 전용 필터체인(@Order(0)) 통합 테스트 — 미니앱 채널이 열리되 <b>웹 채널이 그대로 산다</b>는 증명.
 *
 * <p>라우팅 스위치는 {@code Authorization: Bearer} 헤더 유무다. 헤더가 있으면 stateless·CSRF 없는 새 체인,
 * 없으면 기존 세션+CSRF 체인 — 그래서 웹 Vue 섬의 {@code /api/**} 호출은 한 글자도 안 바뀐다(설계 §2.3).
 * 이 클래스의 "세션 경로 회귀 가드"가 그 무변경을 단독으로 잡는 계측기다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BearerApiChainTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired MockMvc mockMvc;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired ReadingSessionRepository sessionRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired Clock clock;

    private User webUser(String email, String loginId) {
        registrationService.register(email, "pw1234qwer!!", loginId, "닉_" + loginId, SEOUL, Role.USER,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)));
        return userRepository.findByLoginId(loginId).orElseThrow();
    }

    /** 미니앱 신규 가입자 — login_id가 없다(§2.4). principal은 email로 브리지된다. */
    private User tossUser(String email, String userKey) {
        User u = registrationService.registerOAuth(email, "토스유저", SEOUL, AuthProvider.TOSS,
                LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL)), false);
        u.linkTossUserKey(userKey);
        return userRepository.save(u);
    }

    // ── Bearer 경로 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("유효 Bearer로 GET /api/dashboard → 200 (세션·쿠키 없이)")
    void validBearer_getDashboard_200() throws Exception {
        String token = apiTokenService.issue(webUser("bearer-ok@booktimer.com", "bearerok"));

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("bearerok"));
    }

    @Test
    @DisplayName("Bearer 경로는 CSRF를 적용하지 않는다 — CSRF 토큰 없는 POST /api/sessions/start도 200")
    void validBearer_postWithoutCsrf_200() throws Exception {
        String token = apiTokenService.issue(webUser("bearer-csrf@booktimer.com", "bearercsrf"));

        mockMvc.perform(post("/api/sessions/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("무효 Bearer → 401 (기존 체인의 /login 302로 새지 않는다)")
    void invalidBearer_401() throws Exception {
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer 지어낸토큰"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("login_id가 없는 토스 유저도 email principal 브리지로 /api/dashboard 정상 응답")
    void tossUserWithoutLoginId_dashboardWorks() throws Exception {
        User toss = tossUser("toss-nologin@noreply.booktimer.app", "uk-nologin");
        assertThat(toss.getLoginId()).isNull();
        String token = apiTokenService.issue(toss);

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").doesNotExist())
                .andExpect(jsonPath("$.nickname").value("토스유저"));
    }

    // ── 기존(세션) 체인 회귀 가드 ────────────────────────────────────────────

    @Test
    @DisplayName("회귀 가드: Bearer 없는 미인증 /api/dashboard는 기존대로 /login 302")
    void noAuth_stillRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("회귀 가드: 세션 인증 + CSRF의 POST /api/sessions/start는 그대로 200 (웹 채널 유지)")
    void sessionPath_withCsrf_stillWorks() throws Exception {
        webUser("session-regress@booktimer.com", "sessregress");

        mockMvc.perform(post("/api/sessions/start").with(user("sessregress")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회귀 가드: 세션 인증인데 CSRF가 없으면 기존대로 403 (Bearer 체인이 세션 경로를 무장해제하지 않았다)")
    void sessionPath_withoutCsrf_stillForbidden() throws Exception {
        webUser("session-nocsrf@booktimer.com", "sessnocsrf");

        mockMvc.perform(post("/api/sessions/start").with(user("sessnocsrf"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── 채널 병행(핵심 인수 시나리오) ────────────────────────────────────────

    @Test
    @DisplayName("연결 계정 교차: Bearer로 시작한 측정이 세션(웹) 인증 조회에 같은 기록으로 보인다")
    void crossChannel_bearerSessionVisibleOnWeb() throws Exception {
        User linked = webUser("cross@booktimer.com", "crossuser");
        linked.linkTossUserKey("uk-cross");
        userRepository.save(linked);
        String token = apiTokenService.issue(linked);

        // 미니앱(Bearer)에서 측정 시작 → 종료
        mockMvc.perform(post("/api/sessions/start").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/sessions/stop").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 웹(세션+CSRF)에서 보면 같은 User의 기록이다 — DB 공유가 성립한다는 단언.
        mockMvc.perform(get("/api/dashboard").with(user("crossuser")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveSession").value(false));
        assertThat(sessionRepository.findAll())
                .anySatisfy(s -> assertThat(s.getUser().getId()).isEqualTo(linked.getId()));
    }
}
