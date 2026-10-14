package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.TossLoginClient;
import com.booktimer.auth.TossUserInfo;
import com.booktimer.security.RateLimitService;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/toss/unlink-callback — 토스 로그인 연결 끊기 콜백의 HTTP 계약(비게임 체크리스트 82·83).
 *
 * <p>토스 서버가 부르는 서버 간 호출이라 <b>세션·CSRF·Bearer 없이</b> 닿아야 한다. 이 클래스의 어떤 요청도
 * {@code csrf()}를 붙이지 않는다 — 200이 나오는 테스트가 permitAll의 양성 대조군이다(401 테스트는 permitAll이
 * 빠져도 체인 엔트리포인트가 401을 내서 통과하므로 음성 판정 전용이다).
 */
@MockedBoundaryTest
@Transactional
class TossUnlinkCallbackControllerTest {

    private static final String PATH = "/api/toss/unlink-callback";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired RateLimitService rateLimitService;
    @Autowired TossLoginClient tossLoginClient;

    @Value("${booktimer.toss.unlink-callback.basic-auth}")
    String secret;

    @BeforeEach
    void resetRateLimit() {
        rateLimitService.clearForTest(); // /api/toss/register를 부르므로 IP 상한이 다른 테스트와 섞인다
    }

    private static String b64(String v) {
        return Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));
    }

    private static String basic(String v) {
        return "Basic " + b64(v);
    }

    private ResultActions callback(String authorization, String body) throws Exception {
        MockHttpServletRequestBuilder req = post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
        if (authorization != null) {
            req.header("Authorization", authorization);
        }
        return mockMvc.perform(req);
    }

    /** 토스 전용 계정을 실제 가입 경로로 만들고 Bearer 토큰을 돌려준다(실 클라이언트도 숫자 userKey를 이 문자열로 만든다). */
    private String registerTossOnly(String userKey) throws Exception {
        when(tossLoginClient.fetchUserInfo(any(), any())).thenReturn(new TossUserInfo(userKey, null));
        String json = mockMvc.perform(post("/api/toss/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"c\",\"referrer\":\"r\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }

    /** 웹(LOCAL) 계정 + 토스 연결 + 미니앱 토큰 — {@code TossAuthApiControllerTest}의 localUser와 같은 모양. */
    private User webUserLinked(String email, String loginId, String userKey) {
        User u = User.of(email, "hash", "웹유저", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        u.linkTossUserKey(userKey);
        return userRepository.save(u);
    }

    private ResultActions dashboard(String token) throws Exception {
        return mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token));
    }

    @Test
    @DisplayName("REQ-02 · 숫자로 온 userKey로 토스 전용 계정을 찾아 지우고 200 — 세션·CSRF 없이 닿는다")
    void numericUserKey_purgesTossOnlyAccount() throws Exception {
        registerTossOnly("443731103");
        assertThat(userRepository.findByTossUserKey("443731103")).isPresent();

        callback(basic(secret), "{\"userKey\": 443731103, \"referrer\": \"UNLINK\"}")
                .andExpect(status().isOk());

        assertThat(userRepository.findByTossUserKey("443731103")).isEmpty();
    }

    @Test
    @DisplayName("REQ-05 · 처리 뒤 그 계정의 옛 Bearer 토큰은 401이다")
    void afterUnlink_oldBearerTokenIs401() throws Exception {
        String token = registerTossOnly("443731103");
        dashboard(token).andExpect(status().isOk()); // 양성 대조 — 끊기 전에는 통한다

        callback(basic(secret), "{\"userKey\": 443731103, \"referrer\": \"UNLINK\"}").andExpect(status().isOk());

        dashboard(token).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REQ-06 · 처리 뒤 같은 토스 신원의 로그인은 registered:false — 옛 기록이 되살아나지 않는다")
    void afterUnlink_sameTossIdentityIsUnregistered() throws Exception {
        // 토스 전용 경로
        registerTossOnly("443731103");
        callback(basic(secret), "{\"userKey\": 443731103, \"referrer\": \"UNLINK\"}").andExpect(status().isOk());
        mockMvc.perform(post("/api/toss/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"c2\",\"referrer\":\"r\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false));

        // 웹 계정 경로 — referrer가 달라도 같은 처리(REQ-07)
        User web = webUserLinked("web-unlink-cb@booktimer.com", "webunlinkcb", "443731104");
        String webToken = apiTokenService.issue(web);
        callback(basic(secret), "{\"userKey\": 443731104, \"referrer\": \"WITHDRAWAL_TERMS\"}")
                .andExpect(status().isOk());

        User kept = userRepository.findById(web.getId()).orElseThrow();
        assertThat(kept.getTossUserKey()).isNull();
        dashboard(webToken).andExpect(status().isUnauthorized());
        when(tossLoginClient.fetchUserInfo(any(), any())).thenReturn(new TossUserInfo("443731104", null));
        mockMvc.perform(post("/api/toss/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizationCode\":\"c3\",\"referrer\":\"r\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false));
    }

    @Test
    @DisplayName("REQ-07 · 모르는 userKey는 200이고 아무것도 바꾸지 않는다 — 같은 콜백이 다시 와도 안전하다")
    void unknownUserKey_isOkAndChangesNothing() throws Exception {
        registerTossOnly("443731103");
        long before = userRepository.count();

        callback(basic(secret), "{\"userKey\": 999000111, \"referrer\": \"UNLINK\"}").andExpect(status().isOk());
        callback(basic(secret), "{\"userKey\": 999000111}").andExpect(status().isOk()); // referrer 없음도 같다

        assertThat(userRepository.count()).isEqualTo(before);
        assertThat(userRepository.findByTossUserKey("443731103")).isPresent();
    }

    @Test
    @DisplayName("REQ-01 · Basic 인증이 없거나 틀리거나 Bearer면 401이고 계정은 그대로다")
    void missingOrWrongAuth_is401AndKeepsAccount() throws Exception {
        // ⚠️ 음성 판정 전용 — permitAll이 빠져도 체인 엔트리포인트가 401을 내서 통과한다. permitAll은 REQ-02의 200이 증명한다.
        String token = registerTossOnly("443731103");
        String body = "{\"userKey\": 443731103, \"referrer\": \"UNLINK\"}";

        callback(null, body).andExpect(status().isUnauthorized());
        callback(basic("wrong"), body).andExpect(status().isUnauthorized());
        callback("Bearer " + token, body).andExpect(status().isUnauthorized());

        assertThat(userRepository.findByTossUserKey("443731103")).isPresent();
    }

    @Test
    @DisplayName("REQ-08 · 인증은 통과했지만 본문이 깨졌거나 userKey가 없으면 400이고 아무것도 바꾸지 않는다")
    void badBody_is400AndChangesNothing() throws Exception {
        registerTossOnly("443731103");

        for (String body : new String[]{"", "not json", "{}", "{\"userKey\":null}", "{\"userKey\":{\"x\":1}}",
                // 최상위에 없고 안쪽에만 있는 실제 키 — 안쪽까지 뒤지면 그 계정을 지우고 200을 냈을 것이다
                "{\"x\":{\"userKey\":443731103}}", "[{\"userKey\":443731103}]"}) {
            callback(basic(secret), body).andExpect(status().isBadRequest());
        }

        assertThat(userRepository.findByTossUserKey("443731103")).isPresent();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    @DisplayName("REQ-11 · 로그에는 userKey 원문도 Authorization 헤더 원문도 남지 않는다 — 인증 실패·NOT_FOUND 모두")
    void logsNeverCarryRawUserKeyOrHeader(CapturedOutput output) throws Exception {
        String body = "{\"userKey\": 987654321012, \"referrer\": \"UNLINK\"}";
        callback(b64("leak-probe-7f3a"), body).andExpect(status().isUnauthorized()); // 스킴 없는 base64 = 원문이 곧 시크릿
        callback(basic("wrong-probe-9c1e"), body).andExpect(status().isUnauthorized());
        callback(basic(secret), body).andExpect(status().isOk());

        // 양성 대조 먼저 — 캡처가 실제로 이 로그를 받고 있다는 증거
        assertThat(output.getAll())
                .contains("toss-unlink auth-failed scheme=no-scheme")
                .contains("toss-unlink auth-failed scheme=Basic")
                .contains("toss-unlink outcome=NOT_FOUND referrer=UNLINK keyShape=digits:12");
        assertThat(output.getAll())
                .doesNotContain(b64("leak-probe-7f3a"))
                .doesNotContain(b64("wrong-probe-9c1e"))
                .doesNotContain("987654321012")
                .doesNotContain(secret)
                .doesNotContain(b64(secret));
    }

    @Test
    @DisplayName("REQ-01 · 서버 시크릿이 비어 있으면 어떤 헤더도 통과하지 못한다 (fail-closed)")
    void authorized_failsClosed() {
        assertThat(TossUnlinkCallbackController.authorized(basic(""), "")).isFalse();
        assertThat(TossUnlinkCallbackController.authorized(basic("x"), " ")).isFalse();
        // 판별 사례 — 서버 값이 공백뿐이면 같은 공백을 보낸 헤더와 바이트가 같다. 비어 있음 판정만이 이것을 막는다
        // (위 두 줄은 길이 검사·바이트 불일치로도 false라, 판정을 지워도 통과한다 — 돌연변이 확인 2026-10-14).
        assertThat(TossUnlinkCallbackController.authorized(basic(" "), " ")).isFalse();
        assertThat(TossUnlinkCallbackController.authorized(null, "x")).isFalse();
        assertThat(TossUnlinkCallbackController.authorized("basic " + b64("x"), "x")).isTrue(); // 스킴 대소문자 무시
        assertThat(TossUnlinkCallbackController.authorized("Basic !!!", "x")).isFalse();
    }
}
