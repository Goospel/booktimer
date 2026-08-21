package com.booktimer.web.api;

import com.booktimer.auth.TossLoginClient;
import com.booktimer.auth.TossLoginException;
import com.booktimer.auth.TossUserInfo;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/toss/login · register · link · logout — 미니앱 신원 흐름(설계 §2.2)의 HTTP 계약.
 *
 * <p>토스 API는 mock({@link TossLoginClient})으로 대체한다 — mTLS 실검증은 PR-0 샌드박스 게이트다.
 * <b>세션·CSRF 없이</b> 호출된다는 점이 핵심이다(Bearer 체인이 라우팅) — CSRF 토큰을 붙이지 않은
 * 이 테스트들이 통과한다는 사실 자체가 체인 분리의 증명이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TossAuthApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TossLinkCodeService linkCodeService;
    @Autowired RateLimitService rateLimitService;

    @MockitoBean TossLoginClient tossLoginClient;

    @BeforeEach
    void resetRateLimit() {
        rateLimitService.clearForTest(); // 인메모리 고정 윈도우 — 테스트 간 격리
    }

    private void tossReturns(String userKey, String email) {
        when(tossLoginClient.fetchUserInfo(any(), any())).thenReturn(new TossUserInfo(userKey, email));
    }

    /**
     * 매 호출마다 <b>다른</b> userKey를 돌려준다 — IP 상한을 격리해 재려면 필요하다. 같은 키를 반복하면
     * userKey를 키로 쓰는 {@link RateLimitAction#TOSS_AUTH}(20)가 먼저 터져 IP 상한(60)에 닿지 못한다.
     * 신원을 돌려가며 두드리는 건 공격자의 실제 행동이기도 하다 — IP를 키로 한 겹 더 두는 이유가 그것이다.
     */
    private void tossReturnsFreshIdentityEachCall() {
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger();
        when(tossLoginClient.fetchUserInfo(any(), any()))
                .thenAnswer(invocation -> new TossUserInfo("uk-rot-" + seq.incrementAndGet(), null));
    }

    private static String body(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    private org.springframework.test.web.servlet.ResultActions callToss(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private User localUser(String email, String loginId) {
        User u = User.of(email, "hash", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: 미등록 userKey → registered:false, 계정을 만들지 않는다")
    void login_unregistered_returnsFalseAndCreatesNothing() throws Exception {
        tossReturns("uk-unknown", "new@toss.im");
        long before = userRepository.count();

        callToss("/api/toss/login", body("authorizationCode", "c", "referrer", "r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(before); // 조회는 생성 부작용이 없다
    }

    @Test
    @DisplayName("login: 등록된 userKey → registered:true + Bearer 토큰 발급")
    void login_registered_returnsToken() throws Exception {
        tossReturns("uk-known", null);
        callToss("/api/toss/register", body("authorizationCode", "c", "referrer", "r"))
                .andExpect(status().isOk());

        callToss("/api/toss/login", body("authorizationCode", "c2", "referrer", "r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("login: 토스가 인가코드를 거부하면 401 (500으로 새지 않는다)")
    void login_tossRejects_401() throws Exception {
        when(tossLoginClient.fetchUserInfo(any(), any())).thenThrow(new TossLoginException("bad code"));

        callToss("/api/toss/login", body("authorizationCode", "bad", "referrer", "r"))
                .andExpect(status().isUnauthorized());
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: 신규 userKey → TOSS 계정 생성(login_id 없음·미검증 이메일) + 토큰")
    void register_createsTossAccount() throws Exception {
        tossReturns("uk-reg", "reg@toss.im");

        callToss("/api/toss/register", body("authorizationCode", "c", "referrer", "r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.token").isString());

        User created = userRepository.findByTossUserKey("uk-reg").orElseThrow();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.TOSS);
        assertThat(created.isEmailVerified()).isFalse();
        assertThat(created.getLoginId()).isNull();   // 미니앱 MVP는 핸들을 강요하지 않는다(§2.4)
        assertThat(created.isOnboarded()).isFalse(); // onboarded ⟹ login_id 불변식 유지
    }

    // ── link ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("link: 유효 코드 → 코드 발급자 User에 userKey가 붙고, 이후 login이 같은 User를 준다")
    void link_validCode_bindsIssuerAccount() throws Exception {
        User web = localUser("web-owner@booktimer.com", "webowner");
        String code = linkCodeService.issue(web);
        tossReturns("uk-link", null);

        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        assertThat(userRepository.findById(web.getId()).orElseThrow().getTossUserKey()).isEqualTo("uk-link");
        assertThat(userRepository.findByTossUserKey("uk-link").orElseThrow().getLoginId()).isEqualTo("webowner");
    }

    @Test
    @DisplayName("link: 존재하지 않는 코드 → 400, 계정 무변경")
    void link_unknownCode_rejected() throws Exception {
        tossReturns("uk-nolink", null);

        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", "ZZZZZZZZ"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByTossUserKey("uk-nolink")).isEmpty();
    }

    @Test
    @DisplayName("link: 이미 소비된 코드 → 400, 계정 무변경 (일회용)")
    void link_usedCode_rejected() throws Exception {
        User web = localUser("used-code@booktimer.com", "usedcode");
        String code = linkCodeService.issue(web);
        tossReturns("uk-first", null);
        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", code))
                .andExpect(status().isOk());

        tossReturns("uk-second", null);
        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", code))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByTossUserKey("uk-second")).isEmpty();
    }

    @Test
    @DisplayName("link: 이미 토스에 연결된 계정의 코드 → 409 (once-set 불변)")
    void link_alreadyLinkedAccount_conflict() throws Exception {
        User web = localUser("already@booktimer.com", "alreadyl");
        // 발급 → (다른 경로로) 연결 → 남아 있던 옛 코드로 다시 시도, 라는 실제 순서로 만든다.
        // 연결된 뒤에는 발급 자체가 거부되므로(TossLinkCodeService.issue) 코드를 먼저 받아 둔다.
        String code = linkCodeService.issue(web);
        web.linkTossUserKey("uk-existing");
        userRepository.save(web);
        tossReturns("uk-other", null);

        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", code))
                .andExpect(status().isConflict());

        assertThat(userRepository.findById(web.getId()).orElseThrow().getTossUserKey()).isEqualTo("uk-existing");
    }

    @Test
    @DisplayName("link: 이미 다른 계정에 붙은 userKey → 409 (UNIQUE 위반이 500으로 새기 전에 선확인)")
    void link_userKeyTakenByAnotherAccount_conflict() throws Exception {
        User owner = localUser("owner-uk@booktimer.com", "ownerukx");
        owner.linkTossUserKey("uk-taken");
        userRepository.save(owner);
        User other = localUser("other-uk@booktimer.com", "otherukx");
        String code = linkCodeService.issue(other);
        tossReturns("uk-taken", null);

        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", code))
                .andExpect(status().isConflict());

        assertThat(userRepository.findById(other.getId()).orElseThrow().getTossUserKey()).isNull();
    }

    @Test
    @DisplayName("link: 코드 브루트포스는 레이트리밋으로 막는다 → 한도 초과 시 429")
    void link_bruteForce_rateLimited() throws Exception {
        tossReturns("uk-brute", null);

        for (int i = 0; i < com.booktimer.security.RateLimitAction.TOSS_LINK.limit(); i++) {
            callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", "GUESS" + i))
                    .andExpect(status().isBadRequest());
        }

        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", "GUESSN"))
                .andExpect(status().isTooManyRequests());
    }

    // ── IP 레이트리밋 (verify 앞) ────────────────────────────────────────────

    @Test
    @DisplayName("IP 한도 초과 → 토스를 호출하기 '전에' 429 (미인증 요청이 아웃바운드를 무제한 유발하지 못한다)")
    void perIpLimit_shortCircuitsBeforeCallingToss() throws Exception {
        tossReturnsFreshIdentityEachCall();
        int limit = RateLimitAction.TOSS_VERIFY.limit();

        for (int i = 0; i < limit; i++) {
            callToss("/api/toss/login", body("authorizationCode", "c" + i, "referrer", "r"))
                    .andExpect(status().isOk());
        }

        callToss("/api/toss/login", body("authorizationCode", "over", "referrer", "r"))
                .andExpect(status().isTooManyRequests());

        // 핵심 단언 — 429가 난 요청은 토스를 부르지 않았다(호출 수가 한도에서 늘지 않음).
        verify(tossLoginClient, times(limit)).fetchUserInfo(any(), any());
    }

    @Test
    @DisplayName("IP 한도는 인가코드가 '거부되는' 요청에도 걸린다 (401 무한 반복이 아웃바운드를 태우지 못한다)")
    void perIpLimit_appliesToRejectedCodesToo() throws Exception {
        when(tossLoginClient.fetchUserInfo(any(), any())).thenThrow(new TossLoginException("bad code"));
        int limit = RateLimitAction.TOSS_VERIFY.limit();

        for (int i = 0; i < limit; i++) {
            callToss("/api/toss/login", body("authorizationCode", "bad" + i, "referrer", "r"))
                    .andExpect(status().isUnauthorized());
        }

        callToss("/api/toss/login", body("authorizationCode", "bad-over", "referrer", "r"))
                .andExpect(status().isTooManyRequests());
        verify(tossLoginClient, times(limit)).fetchUserInfo(any(), any());
    }

    @Test
    @DisplayName("IP 한도는 세 엔드포인트가 '함께' 센다 (경로를 갈아타며 우회할 수 없다)")
    void perIpLimit_isSharedAcrossEndpoints() throws Exception {
        tossReturnsFreshIdentityEachCall();
        int limit = RateLimitAction.TOSS_VERIFY.limit();

        for (int i = 0; i < limit; i++) {
            callToss("/api/toss/login", body("authorizationCode", "c" + i, "referrer", "r"))
                    .andExpect(status().isOk());
        }

        // login으로 한도를 채웠으니 register·link도 막혀야 한다.
        callToss("/api/toss/register", body("authorizationCode", "c", "referrer", "r"))
                .andExpect(status().isTooManyRequests());
        callToss("/api/toss/link", body("authorizationCode", "c", "referrer", "r", "linkCode", "ZZZZZZZZ"))
                .andExpect(status().isTooManyRequests());
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: 폐기한 토큰은 이후 보호된 API에서 401")
    void logout_revokesToken() throws Exception {
        tossReturns("uk-logout", null);
        String response = callToss("/api/toss/register", body("authorizationCode", "c", "referrer", "r"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = response.replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/toss/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
