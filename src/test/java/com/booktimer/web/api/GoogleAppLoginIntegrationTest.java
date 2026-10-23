package com.booktimer.web.api;

import com.booktimer.auth.IdTokenVerifier;
import com.booktimer.auth.IdTokenVerifier.GoogleIdentity;
import com.booktimer.security.RateLimitService;
import com.booktimer.testsupport.MockedBoundaryTest;
import com.booktimer.user.AuthProvider;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/app/auth/google}(REQ-79) — 실 H2. 토큰 검증은 목({@link IdTokenVerifier} — 규칙은 {@code IdTokenVerifierTest}),
 * 판정 · 고정 · 정책 ①②는 실제 빈이다. 열 값은 JDBC로 읽는다(고정 UPDATE가 영속성 컨텍스트를 비워 엔티티가 낡을 수 있다).
 */
@MockedBoundaryTest
@Transactional
class GoogleAppLoginIntegrationTest {

    private static final String SUB = "109876543210000000001";
    private static final String MISMATCH = "이 이메일의 계정은 처음 로그인한 구글 계정으로만 열려요 — 그 구글 계정으로 다시 해 주세요";

    @Autowired MockMvc mockMvc;
    @Autowired IdTokenVerifier idTokens;
    @Autowired RateLimitService rateLimitService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

    @BeforeEach
    void setUp() {
        rateLimitService.clearForTest();
        when(idTokens.isGoogleConfigured()).thenReturn(true);
    }

    private void googleSays(String email, String hd) {
        when(idTokens.verifyGoogle(any())).thenReturn(Optional.of(new GoogleIdentity(SUB, email, "읽는사람", hd)));
    }

    private ResultActions call(String intent, Boolean age) throws Exception {
        String json = "{\"identityToken\":\"t\",\"intent\":\"" + intent + "\""
                + (age == null ? "" : ",\"ageConfirmed\":" + age) + "}";
        return mockMvc.perform(post("/api/app/auth/google").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private User verified(String email, AuthProvider provider) {
        User u = provider == AuthProvider.LOCAL
                ? User.of(email, "hash", "주인", "Asia/Seoul", Role.USER)
                : User.ofOAuth(email, "주인", "Asia/Seoul", Role.USER, provider);
        u.verifyEmail();
        return userRepository.saveAndFlush(u);
    }

    private User unverifiedLocal(String email) {
        return userRepository.saveAndFlush(User.of(email, "hash", "선점자", "Asia/Seoul", Role.USER));
    }

    private String googleSubOf(Long id) {
        return jdbcTemplate.queryForObject("select google_sub from users where id = ?", String.class, id);
    }

    private Map<String, Object> row(Long id) {
        return new HashMap<>(jdbcTemplate.queryForMap("select * from users where id = ?", id));
    }

    private Map<String, Object> rowWithoutGoogleSub(Long id) {
        Map<String, Object> r = row(id);
        r.keySet().removeIf(k -> k.equalsIgnoreCase("google_sub"));
        return r;
    }

    @Test
    @DisplayName("REQ-79 · LOGIN은 같은 이메일의 미검증 LOCAL 계정을 지우지도 돌려주지도 않고 그 사실만 싣는다")
    void loginReportsUnverifiedLocalWithoutTouchingIt() throws Exception {
        User squatter = unverifiedLocal("squat@gmail.com");
        Map<String, Object> before = row(squatter.getId());
        long count = userRepository.count();
        googleSays("squat@gmail.com", null);

        call("LOGIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.unverifiedWebAccount").value(true));

        assertThat(row(squatter.getId())).isEqualTo(before); // 행 그대로 · 고정 0
        assertThat(userRepository.count()).isEqualTo(count);

        // 대조 — 그 이메일의 계정이 없으면 unverifiedWebAccount는 거짓이다
        googleSays("nobody@gmail.com", null);
        call("LOGIN", null).andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.unverifiedWebAccount").value(false));
    }

    @Test
    @DisplayName("REQ-79 · 같은 이메일의 미검증 TOSS 계정은 LOGIN으로 열리지 않고, REGISTER는 그 이메일만 비키고 새 구글 계정을 만든다(정책 ②)")
    void unverifiedTossIsNeverOpenedByGoogle() throws Exception {
        User toss = User.ofOAuth("tosssq@gmail.com", "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        toss.linkTossUserKey("UKtoss-01");
        userRepository.saveAndFlush(toss); // 미검증 그대로
        Map<String, Object> before = row(toss.getId());
        long count = userRepository.count();
        googleSays("tosssq@gmail.com", null);

        call("LOGIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.unverifiedWebAccount").value(false));
        assertThat(row(toss.getId())).isEqualTo(before);
        assertThat(userRepository.count()).isEqualTo(count);

        // 양성 대조 — REGISTER는 토스 계정을 건네지 않고 새 GOOGLE 계정을 만든다
        call("REGISTER", true).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.googleLinked").value(true));
        String tossEmail = jdbcTemplate.queryForObject("select email from users where id = ?", String.class, toss.getId());
        assertThat(User.isSyntheticEmailAddress(tossEmail)).isTrue(); // 합성 주소로 비켜남
        assertThat(jdbcTemplate.queryForObject("select toss_user_key from users where id = ?", String.class, toss.getId()))
                .isEqualTo("UKtoss-01");
        assertThat(googleSubOf(toss.getId())).isNull();
        Long createdId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, "tosssq@gmail.com");
        assertThat(createdId).isNotEqualTo(toss.getId());
        assertThat(googleSubOf(createdId)).isEqualTo(SUB);
    }

    @Test
    @DisplayName("REQ-79 · LOGIN은 열 계정의 빈 google_sub만 채우고 다른 열은 바꾸지 않는다")
    void loginPinsOnlyGoogleSub() throws Exception {
        User google = verified("me@booktimer.com", AuthProvider.GOOGLE);
        Map<String, Object> before = rowWithoutGoogleSub(google.getId());
        googleSays("me@booktimer.com", null);

        call("LOGIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.userId").value(google.getId()))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.googleLinked").value(true))
                .andExpect(jsonPath("$.token").isString());
        assertThat(googleSubOf(google.getId())).isEqualTo(SUB);
        assertThat(rowWithoutGoogleSub(google.getId())).isEqualTo(before);
    }

    @Test
    @DisplayName("REQ-79 · gmail 주소로 이메일 가입한 검증된 LOCAL도 LOGIN으로 열리고 고정된다(§9-1 ②의 전제)")
    void loginPinsVerifiedGmailLocal() throws Exception {
        User local = verified("reader@gmail.com", AuthProvider.LOCAL);
        googleSays("reader@gmail.com", null);

        call("LOGIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(local.getId()))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.googleLinked").value(true));
        assertThat(googleSubOf(local.getId())).isEqualTo(SUB);
    }

    @Test
    @DisplayName("REQ-79 · 구글이 보증하지 않는 주소의 검증된 LOCAL은 LOGIN으로 열되 google_sub를 채우지 않는다(hd 토큰이면 채운다)")
    void loginOpensUnvouchedLocalWithoutPinning() throws Exception {
        User local = verified("me@example.com", AuthProvider.LOCAL);
        Map<String, Object> before = row(local.getId());
        googleSays("me@example.com", null);

        call("LOGIN", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.userId").value(local.getId()))
                .andExpect(jsonPath("$.googleLinked").value(false));
        assertThat(row(local.getId())).isEqualTo(before); // 행 그대로 · 고정 0

        googleSays("me@example.com", "example.com"); // 양성 대조 — 같은 계정에 hd가 실린 토큰이면 고정한다
        call("LOGIN", null).andExpect(status().isOk()).andExpect(jsonPath("$.googleLinked").value(true));
        assertThat(googleSubOf(local.getId())).isEqualTo(SUB);
    }

    @Test
    @DisplayName("REQ-79 · 다른 구글 계정이 고정된 이메일이면 LOGIN과 REGISTER가 409다")
    void pinnedToAnotherGoogleIs409() throws Exception {
        User google = verified("taken@booktimer.com", AuthProvider.GOOGLE);
        jdbcTemplate.update("update users set google_sub = ? where id = ?", "another-google-sub", google.getId());
        entityManager.clear(); // 낡은 인스턴스(google_sub=null)가 판정에 섞이면 「이메일로 고정 없음」으로 읽힌다
        Map<String, Object> before = row(google.getId());
        long count = userRepository.count();
        googleSays("taken@booktimer.com", null);

        call("LOGIN", null).andExpect(status().isConflict()).andExpect(content().string(MISMATCH));
        call("REGISTER", true).andExpect(status().isConflict()).andExpect(content().string(MISMATCH));

        assertThat(row(google.getId())).isEqualTo(before);
        assertThat(userRepository.count()).isEqualTo(count);
    }

    @Test
    @DisplayName("REQ-79 · REGISTER는 만 14세 확인 없이 400이고 있으면 웹 정책대로 만든다")
    void registerNeedsAgeThenFollowsWebPolicy() throws Exception {
        User squatter = unverifiedLocal("newbie@gmail.com");
        googleSays("newbie@gmail.com", null);

        call("REGISTER", null).andExpect(status().isBadRequest());
        call("REGISTER", false).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(squatter.getId())).isPresent(); // 400은 아무것도 안 바꾼다

        String first = call("REGISTER", true).andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.authProvider").value("GOOGLE"))
                .andExpect(jsonPath("$.googleLinked").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(userRepository.findById(squatter.getId())).isEmpty(); // 정책 ① — 미검증 LOCAL 폐기
        User created = userRepository.findByEmail("newbie@gmail.com").orElseThrow();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(googleSubOf(created.getId())).isEqualTo(SUB); // 새 계정은 sub 고정
        assertThat(first).contains("\"userId\":" + created.getId());

        long count = userRepository.count();
        call("REGISTER", true).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.userId").value(created.getId()));
        assertThat(userRepository.count()).isEqualTo(count); // 두 번 불러도 한 계정
    }

    @Test
    @DisplayName("REQ-79 · 구글 LINK는 400이다")
    void googleLink400() throws Exception {
        googleSays("link@gmail.com", null);
        call("LINK", null).andExpect(status().isBadRequest());
    }
}
