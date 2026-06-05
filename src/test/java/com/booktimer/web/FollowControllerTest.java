package com.booktimer.web;

import com.booktimer.follow.FollowService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 팔로우/언팔로우 컨트롤러 통합 테스트 (SNS 3단계 · login-id-design §7 PR-3).
 *
 * <p>대상은 <b>login_id(공개 @핸들)</b>로 식별한다 — 닉네임은 중복될 수 있어 1:1 식별에 못 쓴다.
 * 도메인 규칙은 {@link FollowService}가 강제하고, 여기선 와이어링·리다이렉트·오픈리다이렉트 방어를 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FollowService followService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email, String loginId, String nickname) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("POST /follow: login_id로 관계를 만들고 대상 프로필로 리다이렉트한다")
    void follow_createsAndRedirects() throws Exception {
        User me = newUser("me@booktimer.com", "alice", "앨리스");
        User target = newUser("t@booktimer.com", "bob", "밥");

        mockMvc.perform(post("/follow").param("loginId", "bob")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bob"));

        assertThat(followService.isFollowing(me, target)).isTrue();
    }

    @Test
    @DisplayName("POST /follow: 닉네임이 같은 두 사람이 있어도 login_id로 정확한 대상만 팔로우한다")
    void follow_duplicateNickname_targetsByLoginId() throws Exception {
        User me = newUser("me@booktimer.com", "alice", "앨리스");
        User a = newUser("a@booktimer.com", "alpha", "동명이인");
        User b = newUser("b@booktimer.com", "bravo", "동명이인"); // 같은 닉네임, 다른 아이디

        mockMvc.perform(post("/follow").param("loginId", "bravo")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bravo"));

        assertThat(followService.isFollowing(me, b)).isTrue();   // 정확히 bravo만
        assertThat(followService.isFollowing(me, a)).isFalse();  // alpha는 아님
    }

    @Test
    @DisplayName("POST /follow: 자기 자신 팔로우는 조용히 무시된다(관계 없음)")
    void follow_self_ignored() throws Exception {
        User me = newUser("me@booktimer.com", "alice", "앨리스");

        mockMvc.perform(post("/follow").param("loginId", "alice")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/alice"));

        assertThat(followService.isFollowing(me, me)).isFalse();
    }

    @Test
    @DisplayName("POST /unfollow: login_id로 관계를 지운다")
    void unfollow_removes() throws Exception {
        User me = newUser("me@booktimer.com", "alice", "앨리스");
        User target = newUser("t@booktimer.com", "bob", "밥");
        followService.follow(me, target);

        mockMvc.perform(post("/unfollow").param("loginId", "bob")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bob"));

        assertThat(followService.isFollowing(me, target)).isFalse();
    }

    @Test
    @DisplayName("POST /follow: 내부 redirect 경로는 그대로 따른다(검색 화면 복귀)")
    void follow_internalRedirect_honored() throws Exception {
        newUser("me@booktimer.com", "alice", "앨리스");
        newUser("t@booktimer.com", "bob", "밥");

        mockMvc.perform(post("/follow").param("loginId", "bob").param("redirect", "/search?q=bo")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/search?q=bo"));
    }

    @Test
    @DisplayName("POST /follow: 외부 redirect(//evil)는 무시하고 프로필로(오픈 리다이렉트 방어)")
    void follow_externalRedirect_ignored() throws Exception {
        newUser("me@booktimer.com", "alice", "앨리스");
        newUser("t@booktimer.com", "bob", "밥");

        mockMvc.perform(post("/follow").param("loginId", "bob").param("redirect", "//evil.com")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bob"));
    }

    @Test
    @DisplayName("팔로우를 한도 이상 반복하면 초과분은 무시된다(레이트리밋, 사용자별)")
    void follow_rateLimited_dropsOverLimit() throws Exception {
        User me = newUser("rl@booktimer.com", "limiter", "리미터");
        User t1 = newUser("t1@booktimer.com", "target1", "타겟1");
        User t2 = newUser("t2@booktimer.com", "target2", "타겟2");

        int limit = RateLimitAction.FOLLOW.limit();
        for (int i = 0; i < limit; i++) { // 한도 소진(첫 호출은 target1 팔로우, 이후는 멱등이지만 카운트됨)
            mockMvc.perform(post("/follow").param("loginId", "target1")
                    .with(user("rl@booktimer.com")).with(csrf()));
        }

        // 한도 초과 후 새 대상 팔로우 시도 → 무시되어 관계가 안 생긴다
        mockMvc.perform(post("/follow").param("loginId", "target2")
                        .with(user("rl@booktimer.com")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(followService.isFollowing(me, t1)).isTrue();  // 한도 내 첫 호출은 성공
        assertThat(followService.isFollowing(me, t2)).isFalse(); // 초과분은 차단
    }
}
