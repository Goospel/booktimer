package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.TossLoginClient;
import com.booktimer.auth.TossLoginException;
import com.booktimer.auth.TossUserInfo;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.Role;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.TossUserProvisioningService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 미니앱 회원 탈퇴 API — {@code POST /api/miniapp/delete-account}.
 *
 * <p>토스로 시작한 계정에는 <b>탈퇴 경로가 하나도 없었다</b>: 비밀번호가 없어 웹 {@code /settings/delete}에
 * 로그인조차 못 하고, {@code login_id}가 null일 수 있어 소셜 탈퇴의 @핸들 재확인도 성립하지 않는다.
 * 이 엔드포인트가 유일한 출구다.
 *
 * <p><b>이 테스트의 급소는 두 가지</b>다 — ① 남의 userKey로는 <b>아무것도 지워지지 않는다</b>(탈퇴는
 * 되돌릴 수 없어 게이트가 새면 복구가 없다), ② 실패가 <b>401이 아니다</b>. 401을 주면 `api.ts`가 토큰을
 * 버리고 로그인 화면으로 튕겨, 탈퇴하려던 사용자가 대신 로그아웃당한다 — 토스 인증 실패는 400, 신원
 * 불일치는 403이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MiniappAccountApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired TossUserProvisioningService provisioningService;
    @Autowired RateLimitService rateLimitService;
    @Autowired TossLinkCodeService linkCodeService;

    @MockitoBean TossLoginClient tossLoginClient;

    @BeforeEach
    void resetRateLimit() {
        rateLimitService.clearForTest(); // 인메모리 고정 윈도우 — 테스트 간 격리
    }

    /** 토스로 시작한 계정 — 비밀번호도 login_id도 없고 toss_user_key만 있다(미니앱 신규 가입 그대로). */
    private User tossUser(String userKey, String email) {
        return provisioningService.register(userKey, email);
    }

    /** 토스가 이 인가코드를 이 신원으로 확인해 준다고 둔다(실 mTLS 호출은 PR-0 샌드박스 게이트). */
    private void tossReturns(String userKey) {
        when(tossLoginClient.fetchUserInfo(any(), any())).thenReturn(new TossUserInfo(userKey, null));
    }

    private ResultActions deleteAccount(String token) throws Exception {
        return mockMvc.perform(post("/api/miniapp/delete-account")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authorizationCode\":\"fresh-code\",\"referrer\":\"SANDBOX\"}"));
    }

    @Test
    @DisplayName("토스 재인증이 본인이면 204 — 계정이 사라지고 그 Bearer 토큰도 즉시 죽는다")
    void deleteAccount_verified_deletesAccountAndKillsToken() throws Exception {
        User me = tossUser("uk-quit", "quit@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        tossReturns("uk-quit");

        deleteAccount(token).andExpect(status().isNoContent());

        userRepository.flush(); // FK 위반이면 여기서 터진다
        assertThat(userRepository.findByTossUserKey("uk-quit")).isEmpty();
        // 토큰 무효화를 "api_token을 지웠다"가 아니라 행동으로 단언한다 — 탈퇴 후 그 토큰은 아무것도 못 연다.
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토스가 인가코드를 거부하면 400 — 401이 아니다(401이면 탈퇴 시도가 로그아웃으로 둔갑한다)")
    void deleteAccount_tossRejects_400_not401() throws Exception {
        User me = tossUser("uk-expired", "expired@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        when(tossLoginClient.fetchUserInfo(any(), any())).thenThrow(new TossLoginException("expired code"));

        deleteAccount(token)
                .andExpect(status().isBadRequest())
                // 미니앱은 에러 본문 평문을 그대로 사용자에게 띄운다 — HTML이 오면 화면에 마크업이 쏟아진다.
                .andExpect(content().string("토스 인증에 실패했어요. 다시 시도해 주세요."));

        assertThat(userRepository.findByTossUserKey("uk-expired")).isPresent(); // 계정은 그대로
    }

    @Test
    @DisplayName("남의 userKey로는 403 — 계정도 토큰도 그대로 살아 있다(이 기능의 급소)")
    void deleteAccount_userKeyMismatch_403_deletesNothing() throws Exception {
        User me = tossUser("uk-owner", "owner@noreply.booktimer.app");
        String token = apiTokenService.issue(me);
        // 토큰은 훔쳤지만 토스 본인 인증은 남의 것 — 재인증이 막는 바로 그 시나리오다.
        tossReturns("uk-attacker");

        deleteAccount(token)
                .andExpect(status().isForbidden())
                .andExpect(content().string("본인 확인에 실패해 탈퇴를 진행하지 않았어요."));

        assertThat(userRepository.findByTossUserKey("uk-owner")).isPresent();
        // 토큰도 살아 있어야 한다 — 실패는 로그아웃이 아니다.
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("지어낸 토큰은 401 — 미니앱 체인 인증이 이 새 경로에도 걸린다(SecurityConfig 무변경 전제)")
    void deleteAccount_withoutValidToken_401() throws Exception {
        deleteAccount("지어낸토큰").andExpect(status().isUnauthorized());
    }

    // ── 웹 로그인 코드 발급 — POST /api/miniapp/web-login-code ─────────────────
    //
    // 토스로 시작한 계정은 비밀번호가 없어 PC 웹에 들어갈 방법이 아예 없었다. 미니앱에서 받은 일회용
    // 코드를 웹 로그인 화면에 옮겨 적는 것이 유일한 경로다(웹→토스 연결 코드의 거울상).

    private ResultActions issueWebLoginCode(String token) throws Exception {
        return mockMvc.perform(post("/api/miniapp/web-login-code")
                .header("Authorization", "Bearer " + token));
    }

    @Test
    @DisplayName("웹 로그인 코드를 발급하면 사람이 옮겨 적을 수 있는 8자 코드와 TTL이 오고, 그 코드가 실제로 웹 로그인 소비 지점에서 먹는다")
    void webLoginCode_issued() throws Exception {
        User me = tossUser("uk-weblogin", "weblogin@noreply.booktimer.app");

        String body = issueWebLoginCode(apiTokenService.issue(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                // 혼동 문자(0·O·1·I·L)가 없어야 손으로 옮겨 적어도 틀리지 않는다.
                .andExpect(jsonPath("$.code").value(matchesPattern("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$")))
                .andReturn().getResponse().getContentAsString();

        // 응답 형태가 아니라 '쓸 수 있는가'로 단언한다 — 목적이 잘못 붙으면 여기서만 드러난다.
        String code = com.jayway.jsonpath.JsonPath.read(body, "$.code");
        assertThat(linkCodeService.consume(code, TossLinkCode.Purpose.WEB_LOGIN))
                .map(User::getId).contains(me.getId());
    }

    @Test
    @DisplayName("지어낸 토큰으로는 401 — 이 발급 경로도 미니앱 체인 인증에 걸린다(SecurityConfig 무변경 전제)")
    void webLoginCode_withoutValidToken_401() throws Exception {
        // ⚠️ Bearer 헤더를 아예 빼면 미니앱 체인의 라우팅 조건(/api/** + Bearer)에 안 걸려 웹 체인이
        // 받는다 — 그쪽은 CSRF가 먼저 걸려 403이다. "인증이 없으면 못 쓴다"를 겨누려면 헤더는 달되
        // 토큰이 무효여야 한다(기존 탈퇴 테스트와 같은 방식).
        issueWebLoginCode("지어낸토큰").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토스에 연결되지 않은 계정에는 409 — 방어선이 화면이 아니라 코드에 있음을 증명한다")
    void webLoginCode_unlinkedUser_409() throws Exception {
        // 실 운용에선 Bearer 토큰이 토스 인증 경로에서만 나와 이 상태가 생기지 않는다. 테스트에서만
        // 만들 수 있는 상태로 방어선을 직접 겨눈다(발급 조건이 사라져도 여기서 잡힌다).
        User local = userRepository.save(
                User.of("web-only@booktimer.com", "hash", "책벌레", "Asia/Seoul", Role.USER));

        issueWebLoginCode(apiTokenService.issue(local)).andExpect(status().isConflict());
    }
}
