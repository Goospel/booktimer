package com.booktimer.web.api;

import com.booktimer.follow.FollowService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GET /api/search 컨트롤러 통합 테스트.
 *
 * <p>①인증 게이트(기본 잠김), ②JSON 구조(results·recommendations·myLoginId·rateLimited),
 * ③q 2글자 미만 가드, ④N-055 login_id=null 사용자 누출 차단.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private FollowService followService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private RateLimitService rateLimitService;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /api/search 미인증 → 302 로그인 리다이렉트 (default-deny)")
    void getSearch_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/search?q=ab"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/search?q=ab 인증 → 200 JSON (results·recommendations·myLoginId·rateLimited 키 존재)")
    void getSearch_authenticated_returnsJsonStructure() throws Exception {
        registrationService.register("search-api1@booktimer.com", "pw1234qwer!!", "srchid1a", "검색1", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/search?q=ab")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-api1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.q").value("ab"))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.myLoginId").exists())
                .andExpect(jsonPath("$.rateLimited").isBoolean());
    }

    @Test
    @DisplayName("GET /api/search q=1글자 → results 빈 배열 (서버 가드: 2글자 미만 차단)")
    void getSearch_queryTooShort_emptyResults() throws Exception {
        registrationService.register("search-api2@booktimer.com", "pw1234qwer!!", "srchid2a", "검색2", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/search?q=a")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-api2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    @DisplayName("N-055: login_id=null 사용자(온보딩 전)는 recommendations에 새지 않는다")
    void getSearch_nullLoginIdUser_notInRecommendations() throws Exception {
        // 검색자 (login_id 있음)
        registrationService.register("search-n055-s@booktimer.com", "pw1234qwer!!", "n055srchid", "검색자", SEOUL, Role.USER, today());
        // null-state 사용자 (login_id 없음 — 3-param register = 온보딩 전 상태)
        registrationService.register("search-n055-null@booktimer.com", "pw1234qwer!!", "널상태유저", SEOUL, Role.USER, today());
        // 정상 사용자 (추천 후보)
        registrationService.register("search-n055-v@booktimer.com", "pw1234qwer!!", "n055visid", "보이는유저", SEOUL, Role.USER, today());

        var result = mockMvc.perform(get("/api/search")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-n055-s@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // null loginId 가진 항목이 recommendations에 새면 \"loginId\":null 이 직렬화된다
        assertThat(body).doesNotContain("\"loginId\":null");
    }

    @Test
    @DisplayName("recommendations 항목에 reasons 칩이 실린다 (맞팔 후보 → '나를 팔로우함')")
    void recommendations_carryReasons() throws Exception {
        registrationService.register("rc-me@booktimer.com", "pw1234qwer!!", "rcmeid", "나", SEOUL, Role.USER, today());
        registrationService.register("rc-fb@booktimer.com", "pw1234qwer!!", "rcfbid", "맞팔", SEOUL, Role.USER, today());
        User me = userRepository.findByEmail("rc-me@booktimer.com").orElseThrow();
        User fb = userRepository.findByEmail("rc-fb@booktimer.com").orElseThrow();
        followService.follow(fb, me); // fb→me, me는 안 함 → 맞팔 후보

        var result = mockMvc.perform(get("/api/search")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("rc-me@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"reasons\"");        // 추천 DTO에 reasons 필드 존재
        assertThat(body).contains("나를 팔로우함");        // 맞팔 후보의 이유 칩
    }

    @Test
    @DisplayName("GET /api/search: myLoginId 필드는 현재 로그인 사용자의 login_id다")
    void getSearch_myLoginId_matchesCurrentUser() throws Exception {
        registrationService.register("search-myid@booktimer.com", "pw1234qwer!!", "myloginid1", "나야", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/search")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-myid@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myLoginId").value("myloginid1"));
    }

    // ── 추천 분리 + RECOMMEND 레이트리밋 (fix/recommend-skip-on-search) ────────────

    @Test
    @DisplayName("GET /api/search?q=ab(검색) 응답엔 recommendations가 빈 배열이다 — q 있을 땐 추천 미계산")
    void search_withQuery_returnsEmptyRecommendations() throws Exception {
        registrationService.register("search-skip1@booktimer.com", "pw1234qwer!!", "skipid1a", "건너뜀", SEOUL, Role.USER, today());
        registrationService.register("search-skip1fb@booktimer.com", "pw1234qwer!!", "skipid1b", "맞팔후보", SEOUL, Role.USER, today());
        User me = userRepository.findByEmail("search-skip1@booktimer.com").orElseThrow();
        User fb = userRepository.findByEmail("search-skip1fb@booktimer.com").orElseThrow();
        followService.follow(fb, me); // fb→me: 맞팔 후보 생성 → 원래 추천이 채워질 상황

        rateLimitService.clearForTest();

        mockMvc.perform(get("/api/search?q=ab")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-skip1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }

    @Test
    @DisplayName("GET /api/search(진입, q 없음) 응답엔 recommendations가 채워진다 — 회귀 가드")
    void search_withoutQuery_returnsFilledRecommendations() throws Exception {
        registrationService.register("search-entry1@booktimer.com", "pw1234qwer!!", "entryid1a", "진입자", SEOUL, Role.USER, today());
        registrationService.register("search-entry1fb@booktimer.com", "pw1234qwer!!", "entryid1b", "맞팔후보", SEOUL, Role.USER, today());
        User me = userRepository.findByEmail("search-entry1@booktimer.com").orElseThrow();
        User fb = userRepository.findByEmail("search-entry1fb@booktimer.com").orElseThrow();
        followService.follow(fb, me);

        rateLimitService.clearForTest();

        var result = mockMvc.perform(get("/api/search")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-entry1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("나를 팔로우함");
    }

    @Test
    @DisplayName("RECOMMEND 한도 초과 시 진입(q 없음) 응답의 recommendations가 빈 배열이다")
    void search_withoutQuery_recommendRateExhausted_returnsEmptyRecommendations() throws Exception {
        registrationService.register("search-rl1@booktimer.com", "pw1234qwer!!", "rlid1a", "한도자", SEOUL, Role.USER, today());
        registrationService.register("search-rl1fb@booktimer.com", "pw1234qwer!!", "rlid1b", "맞팔후보", SEOUL, Role.USER, today());
        User me = userRepository.findByEmail("search-rl1@booktimer.com").orElseThrow();
        User fb = userRepository.findByEmail("search-rl1fb@booktimer.com").orElseThrow();
        followService.follow(fb, me);

        rateLimitService.clearForTest();
        int limit = RateLimitAction.RECOMMEND.limit();
        for (int i = 0; i < limit; i++) {
            rateLimitService.allow(RateLimitAction.RECOMMEND, me.getId());
        }

        mockMvc.perform(get("/api/search")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("search-rl1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isEmpty());
    }
}
