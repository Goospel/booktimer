package com.booktimer.web.api;

import com.booktimer.follow.FollowService;
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
 * GET /api/follow-list 컨트롤러 통합 테스트.
 *
 * <p>①인증 게이트(default-deny 302), ②JSON 구조(type·users·myLoginId),
 * ③following=true 보장, ④type 누락/잘못된 값→followers 기본,
 * ⑤N-055 login_id=null 사용자 누출 차단(followers/following 양방향), ⑥빈 목록.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowListApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowService followService;

    @Autowired
    private Clock clock;

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    @Test
    @DisplayName("GET /api/follow-list 미인증 → 302 /login (default-deny)")
    void getFollowList_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/follow-list?type=followers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("GET /api/follow-list?type=followers 인증 → 200 JSON 구조(type·users·myLoginId)")
    void getFollowList_followers_returnsJsonStructure() throws Exception {
        User me = registrationService.register("flist-s1@booktimer.com", "pw1234qwer!!", "flistid1", "목록테스터1", SEOUL, Role.USER, today());
        User follower = registrationService.register("flist-s1f@booktimer.com", "pw1234qwer!!", "flistfol1", "팔로워1", SEOUL, Role.USER, today());
        followService.follow(follower, me);

        mockMvc.perform(get("/api/follow-list?type=followers")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-s1@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("followers"))
                .andExpect(jsonPath("$.users").isArray())
                .andExpect(jsonPath("$.myLoginId").value("flistid1"))
                .andExpect(jsonPath("$.users[0].loginId").value("flistfol1"));
    }

    @Test
    @DisplayName("GET /api/follow-list?type=following → following 목록, 모든 행 following=true")
    void getFollowList_following_allRowsFollowingTrue() throws Exception {
        User me = registrationService.register("flist-s2@booktimer.com", "pw1234qwer!!", "flistid2", "목록테스터2", SEOUL, Role.USER, today());
        User target = registrationService.register("flist-s2t@booktimer.com", "pw1234qwer!!", "flisttgt2", "팔로잉2", SEOUL, Role.USER, today());
        followService.follow(me, target);

        mockMvc.perform(get("/api/follow-list?type=following")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-s2@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("following"))
                .andExpect(jsonPath("$.users[0].loginId").value("flisttgt2"))
                .andExpect(jsonPath("$.users[0].following").value(true));
    }

    @Test
    @DisplayName("type 누락 → followers 기본 반환")
    void getFollowList_typeMissing_defaultsToFollowers() throws Exception {
        registrationService.register("flist-s3@booktimer.com", "pw1234qwer!!", "flistid3", "목록테스터3", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/follow-list")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-s3@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("followers"));
    }

    @Test
    @DisplayName("type=garbage → followers 기본 반환 (관대 파싱)")
    void getFollowList_typeGarbage_defaultsToFollowers() throws Exception {
        registrationService.register("flist-s4@booktimer.com", "pw1234qwer!!", "flistid4", "목록테스터4", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/follow-list?type=garbage")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-s4@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("followers"));
    }

    @Test
    @DisplayName("N-055: login_id=null 팔로워(온보딩 전)는 followers 목록에서 제외")
    void getFollowList_nullLoginIdFollower_notInFollowers() throws Exception {
        User me = registrationService.register("flist-n055a@booktimer.com", "pw1234qwer!!", "flistn055a", "N055테스터", SEOUL, Role.USER, today());
        // 3-param register = login_id=null (온보딩 전 상태)
        User nullStateUser = registrationService.register("flist-n055null@booktimer.com", "pw1234qwer!!", "N055널상태", SEOUL, Role.USER, today());
        followService.follow(nullStateUser, me);

        var result = mockMvc.perform(get("/api/follow-list?type=followers")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-n055a@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"loginId\":null");
    }

    @Test
    @DisplayName("N-055: login_id=null 팔로잉(온보딩 전)은 following 목록에서 제외")
    void getFollowList_nullLoginIdFollowing_notInFollowing() throws Exception {
        User me = registrationService.register("flist-n055b@booktimer.com", "pw1234qwer!!", "flistn055b", "N055테스터B", SEOUL, Role.USER, today());
        User nullStateUser = registrationService.register("flist-n055null2@booktimer.com", "pw1234qwer!!", "N055널상태2", SEOUL, Role.USER, today());
        followService.follow(me, nullStateUser);

        var result = mockMvc.perform(get("/api/follow-list?type=following")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-n055b@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"loginId\":null");
    }

    @Test
    @DisplayName("팔로우 0인 사용자 → users 빈 배열")
    void getFollowList_noFollows_emptyArray() throws Exception {
        registrationService.register("flist-s5@booktimer.com", "pw1234qwer!!", "flistid5", "목록테스터5", SEOUL, Role.USER, today());

        mockMvc.perform(get("/api/follow-list?type=followers")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("flist-s5@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty());
    }
}
