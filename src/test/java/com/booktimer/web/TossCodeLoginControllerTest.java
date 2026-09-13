package com.booktimer.web;

import com.booktimer.security.RateLimitService;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.TossLinkCode;
import com.booktimer.user.TossLinkCodeRepository;
import com.booktimer.user.TossLinkCodeService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토스 → 웹 코드 로그인 — {@code POST /login/toss-code}.
 *
 * <p>토스 미니앱에서 시작한 계정은 <b>비밀번호가 없어</b>(password_hash=null) 폼 로그인이 원리상 불가하고,
 * {@code login_id}도 null일 수 있어 아이디 입력조차 성립하지 않는다. 미니앱이 발급한 일회용 코드를 PC 웹
 * 로그인 화면에서 소비해 세션을 여는 이 경로가 유일한 웹 진입로다(웹→토스 연결 코드의 거울상).
 *
 * <p><b>이 테스트의 급소는 셋</b>이다 — ① 컨트롤러가 손으로 만든 세션이 <b>다음 요청에서도 살아 있는가</b>
 * (폼 로그인 필터가 공짜로 해 주던 일을 직접 하므로 여기가 유일한 미검증 지점), ② <b>웹→토스 연결 코드가
 * 이 소비 지점에 먹지 않는가</b>(먹으면 어깨너머로 본 연결 코드가 즉시 로그인 토큰이 된다), ③ 브루트포스
 * 상한이 <b>코드 검증 전에</b> 세는가(검증 후에 세면 틀린 추측이 카운트되지 않아 상한이 무력해진다).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TossCodeLoginControllerTest {

    private static final long THIRTY_DAYS_SECONDS = 30L * 24 * 3600;

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired TossLinkCodeService linkCodeService;
    @Autowired TossLinkCodeRepository linkCodeRepository;
    @Autowired RateLimitService rateLimitService;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRateLimit() {
        rateLimitService.clearForTest(); // 인메모리 고정 윈도우 — 테스트 간 격리
    }

    @AfterEach
    void cleanUp() {
        try {
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION_ATTRIBUTES");
            jdbcTemplate.execute("DELETE FROM SPRING_SESSION");
        } catch (Exception ignored) {
            // 세션 테이블 미존재 시 무시
        }
        linkCodeRepository.deleteAll(); // 자식 먼저 — FK(toss_link_code.user_id)가 users 삭제를 막는다
        userRepository.deleteAll();
    }

    /** 토스로 시작한 계정 — login_id 없음·비밀번호 없음·toss_user_key만 있다. */
    private User tossUser(String email, String userKey) {
        User u = User.ofOAuth(email, "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        u.linkTossUserKey(userKey);
        return userRepository.save(u);
    }

    /**
     * 실제 브라우저 흐름 그대로: GET /login에서 세션(CSRF 토큰 보관)이 먼저 생기고, 그 쿠키를 들고 POST 한다.
     * 세션 없이 CSRF만 우회하면 세션 고정 방어(changeSessionId) 경로를 재현하지 못한다.
     */
    private Cookie loginPageSession() throws Exception {
        MvcResult page = mockMvc.perform(get("/login")).andReturn();
        Cookie cookie = page.getResponse().getCookie("SESSION");
        assertThat(cookie).as("GET /login에서 익명 세션 쿠키가 발급되어야 한다").isNotNull();
        return cookie;
    }

    private ResultActions submit(String code, Cookie session) throws Exception {
        return mockMvc.perform(post("/login/toss-code")
                .param("code", code)
                .cookie(session)
                .with(csrf()));
    }

    @Test
    @DisplayName("유효한 코드로 세션이 열리고, 그 쿠키로 낸 다음 요청이 온보딩으로 간다 — 수동 세션 저장·email principal 브리지가 실제로 동작한다")
    void validCode_createsSession_andDashboardRedirectsToOnboarding() throws Exception {
        User u = tossUser("toss-weblogin@noreply.booktimer.app", "uk-weblogin-1");
        String code = linkCodeService.issueWebLogin(u);

        MvcResult result = submit(code, loginPageSession())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        // 급소 — 저장이 실패하면 다음 요청은 익명이라 "/"가 landing 200을 준다(302 /onboarding이 아니다).
        Cookie authed = result.getResponse().getCookie("SESSION");
        assertThat(authed).as("로그인 성공 응답이 세션 쿠키를 실어야 한다").isNotNull();
        mockMvc.perform(get("/").cookie(authed))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/onboarding"));
    }

    @Test
    @DisplayName("login_id가 있는 계정은 principal이 login_id다 — 온보딩 전 OAuth 첫 세션만 email을 쓴다(같은 규칙)")
    void validCode_withLoginId_principalIsLoginId() throws Exception {
        User u = tossUser("toss-handle@noreply.booktimer.app", "uk-weblogin-2");
        u.assignLoginId("tossreader");
        userRepository.save(u);
        String code = linkCodeService.issueWebLogin(u);

        submit(code, loginPageSession())
                .andExpect(authenticated().withUsername("tossreader"));
    }

    @Test
    @DisplayName("로그인 성공 시 세션 ID가 바뀐다 — 세션 고정 공격 방어(폼 로그인과 동일)")
    void validCode_rotatesSessionId() throws Exception {
        User u = tossUser("toss-fixation@noreply.booktimer.app", "uk-weblogin-3");
        String code = linkCodeService.issueWebLogin(u);
        Cookie before = loginPageSession();

        MvcResult result = submit(code, before).andReturn();

        Cookie after = result.getResponse().getCookie("SESSION");
        assertThat(after).isNotNull();
        assertThat(after.getValue())
                .as("공격자가 심어 둔 세션 ID가 그대로 인증되면 안 된다")
                .isNotEqualTo(before.getValue());
    }

    @Test
    @DisplayName("이 경로로 연 세션도 30일로 연장된다 — 수동 발행한 인증 성공 이벤트가 SessionLifetimeListener에 닿는다")
    void validCode_extendsSessionToThirtyDays() throws Exception {
        User u = tossUser("toss-30d@noreply.booktimer.app", "uk-weblogin-4");
        String code = linkCodeService.issueWebLogin(u);

        submit(code, loginPageSession()).andExpect(status().is3xxRedirection());

        // 이벤트를 안 쏘면 익명 기본값 24시간(86400)에 머문다.
        Integer maxInactive = jdbcTemplate.queryForObject(
                "SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Integer.class, "toss-30d@noreply.booktimer.app");
        assertThat(maxInactive).isEqualTo((int) THIRTY_DAYS_SECONDS);
    }

    @Test
    @DisplayName("같은 코드는 두 번 먹지 않는다 (일회용)")
    void validCode_isSingleUse() throws Exception {
        User u = tossUser("toss-once@noreply.booktimer.app", "uk-weblogin-5");
        String code = linkCodeService.issueWebLogin(u);

        submit(code, loginPageSession()).andExpect(redirectedUrl("/"));

        submit(code, loginPageSession())
                .andExpect(redirectedUrl("/login?codeError"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("만료된 코드·없는 코드는 같은 문구로 거절한다 (구분은 추측 단서가 된다)")
    void expiredOrUnknownCode_rejected() throws Exception {
        submit("ZZZZZZZZ", loginPageSession())
                .andExpect(redirectedUrl("/login?codeError"))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("웹 설정에서 발급한 '연결 코드'로는 로그인되지 않는다 — 그리고 그 거절이 코드를 소모하지도 않는다 (교차 오용 차단의 급소)")
    void linkPurposeCode_rejected() throws Exception {
        User u = userRepository.save(User.of("link-not-login@booktimer.com", "hash", "책벌레", "Asia/Seoul", Role.USER));
        String linkCode = linkCodeService.issue(u); // 웹→토스 연결용(LINK_TOSS)

        submit(linkCode, loginPageSession())
                .andExpect(redirectedUrl("/login?codeError"))
                .andExpect(unauthenticated());

        // 남의 소비 지점에 들이민 것이 내 연결 코드를 태우면 안 된다.
        assertThat(linkCodeService.consume(linkCode, TossLinkCode.Purpose.LINK_TOSS))
                .map(User::getId).contains(u.getId());
    }

    @Test
    @DisplayName("이미 로그인돼 있으면 코드를 소비하지 않고 홈으로 보낸다 — 세션 속성이 다른 계정으로 새는 계정 전환을 만들지 않는다")
    void alreadyAuthenticated_redirectsWithoutConsuming() throws Exception {
        User u = tossUser("toss-switch@noreply.booktimer.app", "uk-weblogin-6");
        String code = linkCodeService.issueWebLogin(u);

        mockMvc.perform(post("/login/toss-code").param("code", code).with(user("someone")).with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertThat(linkCodeService.consume(code, TossLinkCode.Purpose.WEB_LOGIN))
                .as("코드가 소비되지 않고 살아 있어야 한다").isPresent();
    }

    @Test
    @DisplayName("한도(10회/10분)를 넘기면 유효한 코드도 거절되고 소비되지 않는다 — 상한이 검증 '전에' 세지 않으면 브루트포스 방어가 무력해진다")
    void rateLimit_blocksEleventhAttempt_evenIfValid() throws Exception {
        User u = tossUser("toss-brute@noreply.booktimer.app", "uk-weblogin-7");
        String code = linkCodeService.issueWebLogin(u);

        for (int i = 0; i < 10; i++) {
            submit("WRONG" + i, loginPageSession()).andExpect(redirectedUrl("/login?codeError"));
        }

        submit(code, loginPageSession())
                .andExpect(redirectedUrl("/login?codeLimited"))
                .andExpect(unauthenticated());
        assertThat(linkCodeService.consume(code, TossLinkCode.Purpose.WEB_LOGIN))
                .as("한도 초과 시 코드는 소비되지 않아야 한다").isPresent();
    }

    @Test
    @DisplayName("양성 대조군: 레이트리밋 키가 실제로 X-Forwarded-For의 IP다 — ForwardedHeaderFilter가 getRemoteAddr()를 바꾸지 않으면 모든 클라이언트가 ALB의 같은 IP 하나로 묶여, 한 명이 10회 틀리면 그 뒤 전원이 차단된다(반대로 공격자는 XFF를 바꿔가며 무제한 시도)")
    void rateLimitKey_comesFromForwardedForHeader() throws Exception {
        // 첫 번째 IP로 10회 소진 → 11번째는 막힌다.
        // (XFF가 붙으면 ForwardedHeaderFilter가 리다이렉트 Location을 절대 URL로 바꾼다 — 필터가 이 요청을
        //  감싸고 있다는 부수 증거라, 상대 경로로 단언하면 그것부터 깨진다.)
        for (int i = 0; i < 10; i++) {
            forwardedSubmit("WRONG" + i, "203.0.113.9").andExpect(redirectedUrlPattern("**/login?codeError"));
        }
        forwardedSubmit("WRONG-11", "203.0.113.9").andExpect(redirectedUrlPattern("**/login?codeLimited"));

        // 다른 XFF IP는 자기 몫이 남아 있다 — XFF가 무시되면 둘이 같은 키(127.0.0.1)를 써서 여기도 codeLimited가 된다.
        forwardedSubmit("WRONG-OTHER", "203.0.113.10").andExpect(redirectedUrlPattern("**/login?codeError"));
    }

    private ResultActions forwardedSubmit(String code, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/login/toss-code")
                .param("code", code)
                .header("X-Forwarded-For", forwardedFor)
                .cookie(loginPageSession())
                .with(csrf()));
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 403 — 다른 사이트가 사용자의 브라우저로 코드를 대신 제출할 수 없다")
    void missingCsrf_403() throws Exception {
        mockMvc.perform(post("/login/toss-code").param("code", "ZZZZZZZZ").cookie(loginPageSession()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("미인증 POST가 로그인으로 튕기지 않는다 — permitAll이 빠지면 코드를 넣을 방법 자체가 사라진다")
    void endpoint_isPublic() throws Exception {
        submit("ZZZZZZZZ", loginPageSession())
                .andExpect(redirectedUrl("/login?codeError")); // permitAll 누락이면 "/login"(파라미터 없음)
    }
}
