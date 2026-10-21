package com.booktimer.web.api;

import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.email.AppLinkNotificationService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeRepository;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/app/auth/apple} — 앱 신원의 세 갈래(LOGIN · REGISTER · LINK) HTTP 계약(v1 REQ-02~04 · 09 · 42).
 *
 * <p>Apple 검증은 목({@link AppleIdentityVerifier})이다 — 서명·iss·aud·exp 규칙은 {@code AppleIdentityVerifierTest}가 잰다.
 * 세션·CSRF 없이 부른다(API 체인 — {@code AppSecurityChainTest}).
 */
@MockedBoundaryTest
@Transactional
class AppAuthApiControllerTest {

    private static final String SUB = "000999.feedface01.0001";
    private static final String CODE_INVALID = "코드가 맞지 않거나 시간이 지났어요 — 웹 설정에서 새 코드를 받아 주세요";

    @Autowired MockMvc mockMvc;
    @Autowired AppleIdentityVerifier verifier;
    @Autowired AppLinkNotificationService linkNotifier;
    @Autowired RateLimitService rateLimitService;
    @Autowired UserRepository userRepository;
    @Autowired UserRegistrationService registrationService;
    @Autowired TossLinkCodeService linkCodeService;
    @Autowired TossLinkCodeRepository linkCodeRepository;

    @BeforeEach
    void setUp() {
        rateLimitService.clearForTest();
        when(verifier.isConfigured()).thenReturn(true);
    }

    private void appleSays(String sub) {
        when(verifier.verify(any())).thenReturn(Optional.of(sub));
    }

    private ResultActions call(String json) throws Exception {
        return mockMvc.perform(post("/api/app/auth/apple").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String login() {
        return "{\"identityToken\":\"t\",\"intent\":\"LOGIN\"}";
    }

    private static String register(Boolean age) {
        return "{\"identityToken\":\"t\",\"intent\":\"REGISTER\"" + (age == null ? "" : ",\"ageConfirmed\":" + age) + "}";
    }

    private static String link(String code) {
        return "{\"identityToken\":\"t\",\"intent\":\"LINK\",\"linkCode\":" + (code == null ? "null" : "\"" + code + "\"") + "}";
    }

    private User webUser(String email, boolean verified) {
        return registrationService.registerOAuth(email, "구글러", "Asia/Seoul", AuthProvider.GOOGLE,
                LocalDate.of(2026, 10, 21), verified);
    }

    // ── LOGIN ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-02 · LOGIN: 모르는 신원이면 registered:false이고 토큰도 계정도 없다")
    void loginUnknown() throws Exception {
        appleSays(SUB);
        long before = userRepository.count();
        call(login()).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Apple이 거부한 토큰은 401(앱은 Apple 창을 다시 띄운다)")
    void invalidToken401() throws Exception {
        when(verifier.verify(any())).thenReturn(Optional.empty());
        call(login()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("번들 ID가 아직 없으면(not-configured) 503 「앱 로그인이 아직 준비되지 않았어요」 — 검증기를 부르지 않는다")
    void notConfigured503() throws Exception {
        when(verifier.isConfigured()).thenReturn(false);
        call(login()).andExpect(status().isServiceUnavailable())
                .andExpect(content().string("앱 로그인이 아직 준비되지 않았어요"));
        verify(verifier, never()).verify(any());
    }

    // ── REGISTER ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-02 · REGISTER: 만 14세 확인이 없거나 false면 400이고 계정을 만들지 않는다")
    void registerNeedsAge() throws Exception {
        appleSays(SUB);
        long before = userRepository.count();
        call(register(null)).andExpect(status().isBadRequest());
        call(register(false)).andExpect(status().isBadRequest());
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("REQ-02 · REGISTER: 확인하면 APPLE 계정 + 토큰, created·webAccount=false·userId — 두 번째는 created=false")
    void registerCreates() throws Exception {
        appleSays(SUB);
        call(register(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.webAccount").value(false))
                .andExpect(jsonPath("$.token").isString());
        User created = userRepository.findByAppleSub(SUB).orElseThrow();

        call(register(true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.userId").value(created.getId()));
    }

    @Test
    @DisplayName("REQ-09 · Apple 로그인 토큰으로 /api/dashboard·/api/books가 200")
    void appleTokenOpensExistingApis() throws Exception {
        appleSays(SUB);
        String body = call(register(true)).andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/books").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    // ── LINK ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-02 · 웹에서 만든 계정에 LINK하면 webAccount=true, REGISTER는 false")
    void linkWebAccount() throws Exception {
        User web = webUser("link-ok@booktimer.com", true);
        String code = linkCodeService.issueAppLink(web);
        appleSays(SUB);

        call(link(code)).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.webAccount").value(true))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.userId").value(web.getId()))
                .andExpect(jsonPath("$.token").isString());
        assertThat(userRepository.findById(web.getId()).orElseThrow().getAppleSub()).isEqualTo(SUB);
    }

    @Test
    @DisplayName("REQ-03 · 만료·사용됨·없음·LINK_TOSS 코드는 모두 같은 400이다")
    void badCodesAreOneAnswer() throws Exception {
        appleSays(SUB);
        User web = webUser("codes@booktimer.com", true);

        call(link(null)).andExpect(status().isBadRequest()).andExpect(content().string(CODE_INVALID));
        call(link("ZZZZZZZZ")).andExpect(status().isBadRequest()).andExpect(content().string(CODE_INVALID));

        String toss = linkCodeService.issue(web); // LINK_TOSS — 목적이 다르다
        call(link(toss)).andExpect(status().isBadRequest()).andExpect(content().string(CODE_INVALID));

        String used = linkCodeService.issueAppLink(web);
        linkCodeService.consume(used, TossLinkCode.Purpose.LINK_APPLE);
        call(link(used)).andExpect(status().isBadRequest()).andExpect(content().string(CODE_INVALID));

        String expired = linkCodeService.issueAppLink(web);
        TossLinkCode row = linkCodeRepository.findByUserAndUsedAtIsNull(web).getFirst();
        ReflectionTestUtils.setField(row, "expiresAt", Instant.now().minusSeconds(1));
        linkCodeRepository.saveAndFlush(row);
        call(link(expired)).andExpect(status().isBadRequest()).andExpect(content().string(CODE_INVALID));

        assertThat(userRepository.findById(web.getId()).orElseThrow().getAppleSub()).isNull();
    }

    @Test
    @DisplayName("REQ-03 · 이미 다른 계정에 붙은 Apple 신원은 409")
    void linkSubTaken409() throws Exception {
        appleSays(SUB);
        call(register(true)).andExpect(status().isOk()); // 이 Apple ID로 앱 계정이 이미 있다
        String code = linkCodeService.issueAppLink(webUser("taken@booktimer.com", true));

        call(link(code)).andExpect(status().isConflict())
                .andExpect(content().string("이 Apple ID는 이미 다른 책생글 계정에 연결돼 있어요"));
    }

    @Test
    @DisplayName("REQ-03 · 이미 Apple이 붙은 계정에 다른 신원은 409")
    void linkOntoLinked409() throws Exception {
        User web = webUser("double@booktimer.com", true);
        String code = linkCodeService.issueAppLink(web); // 코드를 받은 뒤 다른 경로로 Apple이 먼저 붙은 경합
        web.linkAppleSub(SUB);
        userRepository.saveAndFlush(web);

        appleSays("another.sub.2");
        call(link(code)).andExpect(status().isConflict())
                .andExpect(content().string("이 웹 계정에는 이미 다른 Apple ID가 연결돼 있어요 — 웹 설정에서 연결을 해제한 뒤 다시 해 주세요"));
    }

    // ── 남용 방지 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-04 · IP 상한은 Apple 검증보다 먼저 센다(검증기 호출 0)")
    void ipLimitBeforeVerify() throws Exception {
        for (int i = 0; i < RateLimitAction.APP_AUTH.limit(); i++) {
            rateLimitService.allow(RateLimitAction.APP_AUTH, "127.0.0.1");
        }
        call(login()).andExpect(status().isTooManyRequests());
        verify(verifier, never()).verify(any());
    }

    @Test
    @DisplayName("REQ-04 · 연결 상한은 코드 소비보다 먼저 센다")
    void linkLimitBeforeConsume() throws Exception {
        User web = webUser("brute@booktimer.com", true);
        String code = linkCodeService.issueAppLink(web);
        appleSays(SUB);
        for (int i = 0; i < RateLimitAction.APP_LINK.limit(); i++) {
            rateLimitService.allow(RateLimitAction.APP_LINK, SUB);
        }
        call(link(code)).andExpect(status().isTooManyRequests());
        // 코드는 그대로 살아 있다 — 상한에 걸린 시도가 코드를 소비하지 않았다(양성 대조: 지금 소비하면 발급자가 나온다)
        assertThat(linkCodeService.consume(code, TossLinkCode.Purpose.LINK_APPLE)).map(User::getId).contains(web.getId());
    }

    // ── 알림 메일 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQ-42 · LINK에 성공하면 그 계정으로 알림을 한 번 부르고, 실패한 LINK는 부르지 않는다")
    void linkNotifiesOnce() throws Exception {
        User web = webUser("notify@booktimer.com", true);
        appleSays(SUB);
        call(link("ZZZZZZZZ")).andExpect(status().isBadRequest());
        verify(linkNotifier, never()).notifyLinked(any());

        call(link(linkCodeService.issueAppLink(web))).andExpect(status().isOk());
        verify(linkNotifier).notifyLinked(org.mockito.ArgumentMatchers.argThat(u -> u.getId().equals(web.getId())));
    }

    @Test
    @DisplayName("REQ-42 · 발송이 실패해도 연결은 유지된다")
    void notifyFailureKeepsLink() throws Exception {
        User web = webUser("mailfail@booktimer.com", true);
        appleSays(SUB);
        doThrow(new RuntimeException("smtp down")).when(linkNotifier).notifyLinked(any());

        call(link(linkCodeService.issueAppLink(web))).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
        assertThat(userRepository.findById(web.getId()).orElseThrow().getAppleSub()).isEqualTo(SUB);
    }

    @Test
    @DisplayName("intent가 없으면 400")
    void missingIntent400() throws Exception {
        appleSays(SUB);
        call("{\"identityToken\":\"t\"}").andExpect(status().isBadRequest());
    }
}
