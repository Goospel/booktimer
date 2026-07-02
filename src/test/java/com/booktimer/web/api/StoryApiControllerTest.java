package com.booktimer.web.api;

import com.booktimer.block.Block;
import com.booktimer.block.BlockRepository;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.security.RateLimitService;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRegistrationService;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoryApiControllerTest {

    private static final String SEOUL = "Asia/Seoul";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private Clock clock;

    @BeforeEach
    void clearRateLimits() {
        rateLimitService.clearForTest();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of(SEOUL));
    }

    private User register(String email, String loginId, String nickname) {
        registrationService.register(email, "pw1234qwer!!", loginId, nickname, SEOUL, Role.USER, today());
        return userRepository.findByEmail(email).orElseThrow();
    }

    @Test
    @DisplayName("POST /api/stories 미인증 → 302 로그인 리다이렉트 (기본 잠김)")
    void create_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/stories").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/stories CSRF 없으면 403")
    void create_withoutCsrf_returns403() throws Exception {
        register("story-csrf@booktimer.com", "storycsrf", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-csrf@booktimer.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/stories 인증+csrf → 200, 작성된 카드 반환")
    void create_authenticated_returnsCard() throws Exception {
        register("story-author@booktimer.com", "storyauthor", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-author@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"인상 깊은 문장\",\"bgCode\":\"night\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("인상 깊은 문장"))
                .andExpect(jsonPath("$.bgCode").value("night"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/stories 도메인 검증 실패(팔레트 밖 bgCode) → 400")
    void create_invalidBgCode_returns400() throws Exception {
        register("story-bad@booktimer.com", "storybad", "작성자");

        mockMvc.perform(post("/api/stories")
                        .with(user("story-bad@booktimer.com")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"문장\",\"bgCode\":\"#ff0000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/stories/{id} 타인 스토리 → 404 (IDOR)")
    void delete_othersStory_returns404() throws Exception {
        User author = register("del-author@booktimer.com", "delauthor", "작성자");
        register("del-actor@booktimer.com", "delactor", "삭제시도자");
        Story story = storyRepository.save(Story.of(author, "남의 문장", null, null));

        mockMvc.perform(delete("/api/stories/" + story.getId())
                        .with(user("del-actor@booktimer.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stories/feed → mine(내 스토리)과 groups(팔로잉 작성자 그룹) 형태")
    void feed_returnsMineAndGroups() throws Exception {
        User me = register("feed-me@booktimer.com", "feedme", "나");
        User followed = register("feed-author@booktimer.com", "feedauthor", "작가");
        followRepository.save(Follow.of(me, followed));
        storyRepository.save(Story.of(me, "내 문장", null, null));
        storyRepository.save(Story.of(followed, "작가 문장", null, "sea"));

        mockMvc.perform(get("/api/stories/feed").with(user("feed-me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine.loginId").value("feedme"))
                .andExpect(jsonPath("$.mine.stories[0].text").value("내 문장"))
                .andExpect(jsonPath("$.groups[0].loginId").value("feedauthor"))
                .andExpect(jsonPath("$.groups[0].allViewed").value(false))
                .andExpect(jsonPath("$.groups[0].stories[0].text").value("작가 문장"))
                .andExpect(jsonPath("$.groups[0].stories[0].bgCode").value("sea"));
    }

    @Test
    @DisplayName("GET /api/stories/of/{loginId} 차단 관계 → 404 (존재 누설 금지)")
    void storiesOf_blocked_returns404() throws Exception {
        register("of-viewer@booktimer.com", "ofviewer", "열람자");
        User target = register("of-target@booktimer.com", "oftarget", "대상");
        User viewer = userRepository.findByEmail("of-viewer@booktimer.com").orElseThrow();
        blockRepository.save(Block.of(target, viewer));

        mockMvc.perform(get("/api/stories/of/oftarget").with(user("of-viewer@booktimer.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/stories/of/{loginId} 비팔로워 → 200 + 빈 배열 (스토리 유무도 안 샘)")
    void storiesOf_nonFollower_returnsEmptyArray() throws Exception {
        register("nf-viewer@booktimer.com", "nfviewer", "열람자");
        User target = register("nf-target@booktimer.com", "nftarget", "대상");
        storyRepository.save(Story.of(target, "비팔로워에겐 안 보일 문장", null, null));

        mockMvc.perform(get("/api/stories/of/nftarget").with(user("nf-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("GET /api/stories/of/{loginId} 팔로워 → 활성 스토리 배열")
    void storiesOf_follower_returnsStories() throws Exception {
        User viewer = register("fw-viewer@booktimer.com", "fwviewer", "열람자");
        User target = register("fw-target@booktimer.com", "fwtarget", "대상");
        followRepository.save(Follow.of(viewer, target));
        storyRepository.save(Story.of(target, "팔로워에겐 보일 문장", null, null));

        mockMvc.perform(get("/api/stories/of/fwtarget").with(user("fw-viewer@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("팔로워에겐 보일 문장"))
                .andExpect(jsonPath("$[0].viewed").value(false));
    }
}
