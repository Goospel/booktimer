package com.booktimer.web;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.user.Role;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 웹 설정 「아이폰 앱 연결」 카드 — 코드 발급(REQ-06) · 연결 해제(REQ-41) · 허용 목록(REQ-73). 실제 빈 + H2.
 *
 * <p>허용 목록 값은 컨트롤러 필드를 테스트마다 바꿔 끼우고 되돌린다 — 프로퍼티를 바꾸면 컨텍스트가 하나 더 뜬다(T-235).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsControllerAppLinkTest {

    private static final String WARNING = "이 코드를 누구에게도 알려 주지 마세요 — 코드를 넣은 iPhone이 이 계정을 열 수 있어요";

    @Autowired MockMvc mockMvc;
    @Autowired SettingsController controller;
    @Autowired UserRegistrationService registrationService;
    @Autowired UserRepository userRepository;
    @Autowired TossLinkCodeRepository linkCodeRepository;
    @Autowired ApiTokenService apiTokenService;

    @AfterEach
    void restoreAllowList() {
        ReflectionTestUtils.setField(controller, "linkCardUsers", "none"); // 테스트 설정엔 값이 없어 기본값 none이다
    }

    private void allow(String value) {
        ReflectionTestUtils.setField(controller, "linkCardUsers", value);
    }

    private User web(String email) {
        return registrationService.register(email, "rawpw1234", "독서가", "Asia/Seoul", Role.USER, LocalDate.of(2026, 10, 21));
    }

    private long appCodes(User u) {
        return linkCodeRepository.findByUserAndUsedAtIsNull(u).stream()
                .filter(c -> c.getPurpose() == TossLinkCode.Purpose.LINK_APPLE).count();
    }

    private String settingsHtml(String email) throws Exception {
        return mockMvc.perform(get("/settings").with(user(email))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── 코드 발급 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-06 · 로그인한 사용자만 앱 연결 코드를 받고 CSRF 없는 POST는 403")
    void issueNeedsLoginAndCsrf() throws Exception {
        User u = web("issue@booktimer.com");
        allow("*");

        mockMvc.perform(post("/settings/app-link-code").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/settings/app-link-code").with(user("issue@booktimer.com")))
                .andExpect(status().isForbidden());
        assertThat(appCodes(u)).isZero();

        mockMvc.perform(post("/settings/app-link-code").with(user("issue@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attributeExists("appLinkCode"));
        assertThat(appCodes(u)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-06 · 이미 Apple이 붙은 계정은 코드 대신 오류 플래시")
    void linkedAccountGetsErrorNotCode() throws Exception {
        User u = web("linked@booktimer.com");
        u.linkAppleSub("sub.linked.1");
        userRepository.saveAndFlush(u);

        mockMvc.perform(post("/settings/app-link-code").with(user("linked@booktimer.com")).with(csrf()))
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attribute("error", "이미 아이폰 앱에 연결된 계정이에요."));
        assertThat(appCodes(u)).isZero();
    }

    @Test
    @DisplayName("REQ-06 · 코드와 함께 「누구에게도 알려 주지 마세요」가 렌더된다")
    void codeRenderedWithWarning() throws Exception {
        web("render@booktimer.com");
        allow("*");
        MvcResult issued = mockMvc.perform(post("/settings/app-link-code").with(user("render@booktimer.com")).with(csrf()))
                .andReturn();
        String code = (String) issued.getFlashMap().get("appLinkCode");

        // 브라우저처럼 POST가 준 세션 쿠키로 리다이렉트 GET을 연다 — 플래시는 JDBC 세션(spring-session)에 있어
        // MockMvc flashAttrs로는 안 닿는다.
        assertThat(issued.getResponse().getCookies()).isNotEmpty();
        String html = mockMvc.perform(get("/settings").with(user("render@booktimer.com")).cookie(issued.getResponse().getCookies()))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains(code).contains(WARNING);
        assertThat(settingsHtml("render@booktimer.com")).doesNotContain(code); // 평문 코드는 한 번만 보인다
    }

    // ── 연결 해제 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-41 · 연결 해제는 apple_sub를 비우고 그 계정 토큰을 모두 지운다(다음 Bearer 401)")
    void unlinkClearsSubAndTokens() throws Exception {
        User u = web("unlink@booktimer.com");
        u.linkAppleSub("sub.unlink.1");
        userRepository.saveAndFlush(u);
        String appToken = apiTokenService.issue(u);
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + appToken)).andExpect(status().isOk());

        mockMvc.perform(post("/settings/app-unlink").with(user("unlink@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/settings"))
                .andExpect(flash().attribute("message", "아이폰 앱 「책생글」 연결을 끊었어요 — 그 기기는 다시 로그인해야 해요"));

        assertThat(userRepository.findById(u.getId()).orElseThrow().getAppleSub()).isNull();
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + appToken)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQ-41 · CSRF 없는 해제 POST는 403")
    void unlinkNeedsCsrf() throws Exception {
        User u = web("unlink-csrf@booktimer.com");
        u.linkAppleSub("sub.csrf.1");
        userRepository.saveAndFlush(u);

        mockMvc.perform(post("/settings/app-unlink").with(user("unlink-csrf@booktimer.com"))).andExpect(status().isForbidden());
        assertThat(userRepository.findById(u.getId()).orElseThrow().getAppleSub()).isEqualTo("sub.csrf.1");
    }

    @Test
    @DisplayName("REQ-41 · 연결이 없으면 바꾸는 것 없이 안내")
    void unlinkWithoutLinkChangesNothing() throws Exception {
        User u = web("nolink@booktimer.com");
        String miniappToken = apiTokenService.issue(u); // 미니앱 토큰 — 지우면 안 된다

        mockMvc.perform(post("/settings/app-unlink").with(user("nolink@booktimer.com")).with(csrf()))
                .andExpect(flash().attribute("message", "연결된 아이폰 앱이 없어요."));
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + miniappToken)).andExpect(status().isOk());
    }

    // ── 허용 목록 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-73 · 목록 밖 사용자의 설정 화면에는 앱 연결 카드가 없다")
    void unlistedUserSeesNoCard() throws Exception {
        User listed = web("listed@booktimer.com");
        web("unlisted@booktimer.com");
        allow(String.valueOf(listed.getId()));

        assertThat(settingsHtml("unlisted@booktimer.com")).doesNotContain("id=\"app-link\"").doesNotContain("/settings/app-link-code");
        assertThat(settingsHtml("listed@booktimer.com")).contains("id=\"app-link\"").contains("/settings/app-link-code"); // 양성 대조
    }

    @Test
    @DisplayName("REQ-73 · 목록 밖 사용자의 코드 발급 POST는 코드를 만들지 않는다(목록 안 사용자는 1개 — 양성 대조)")
    void unlistedUserCannotIssue() throws Exception {
        User listed = web("listed2@booktimer.com");
        User unlisted = web("unlisted2@booktimer.com");
        allow(String.valueOf(listed.getId()));

        mockMvc.perform(post("/settings/app-link-code").with(user("unlisted2@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/settings")).andExpect(flash().attributeCount(0));
        mockMvc.perform(post("/settings/app-link-code").with(user("listed2@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/settings"));

        assertThat(appCodes(unlisted)).isZero();
        assertThat(appCodes(listed)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-73 · Apple이 연결된 목록 밖 사용자도 연결 해제 POST가 된다")
    void linkedUnlistedCanUnlink() throws Exception {
        User u = web("linked-unlisted@booktimer.com");
        u.linkAppleSub("sub.unlisted.1");
        userRepository.saveAndFlush(u);
        allow("none");

        assertThat(settingsHtml("linked-unlisted@booktimer.com")).contains("/settings/app-unlink"); // 해제 버튼을 숨기지 않는다
        mockMvc.perform(post("/settings/app-unlink").with(user("linked-unlisted@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/settings"));
        assertThat(userRepository.findById(u.getId()).orElseThrow().getAppleSub()).isNull();
    }

    @Test
    @DisplayName("앱이 웹 계정 삭제로 보내는 직접 링크 /settings#delete — 탈퇴 절에 id=\"delete\"가 있다")
    void deleteAnchorExists() throws Exception {
        web("anchor@booktimer.com");
        assertThat(settingsHtml("anchor@booktimer.com")).contains("id=\"delete\"");
    }
}
