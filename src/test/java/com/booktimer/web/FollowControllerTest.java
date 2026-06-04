package com.booktimer.web;

import com.booktimer.follow.FollowService;
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

/**
 * 팔로우/언팔로우 컨트롤러 통합 테스트 (SNS 3단계, sns-design §7.3).
 *
 * <p>닉네임은 리다이렉트 URL 인코딩 모호성을 피하려 ASCII로 둔다(동작은 동일). 도메인 규칙은
 * {@link FollowService}가 강제하고, 여기선 와이어링·리다이렉트·오픈리다이렉트 방어를 본다.
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

    private User newUser(String email, String nickname) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("POST /follow: 관계를 만들고 대상 프로필로 리다이렉트한다")
    void follow_createsAndRedirects() throws Exception {
        User me = newUser("me@booktimer.com", "alice");
        User target = newUser("t@booktimer.com", "bob");

        mockMvc.perform(post("/follow").param("nickname", "bob")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bob"));

        assertThat(followService.isFollowing(me, target)).isTrue();
    }

    @Test
    @DisplayName("POST /follow: 자기 자신 팔로우는 조용히 무시된다(관계 없음)")
    void follow_self_ignored() throws Exception {
        User me = newUser("me@booktimer.com", "alice");

        mockMvc.perform(post("/follow").param("nickname", "alice")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/alice"));

        assertThat(followService.isFollowing(me, me)).isFalse();
    }

    @Test
    @DisplayName("POST /unfollow: 관계를 지운다")
    void unfollow_removes() throws Exception {
        User me = newUser("me@booktimer.com", "alice");
        User target = newUser("t@booktimer.com", "bob");
        followService.follow(me, target);

        mockMvc.perform(post("/unfollow").param("nickname", "bob")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bob"));

        assertThat(followService.isFollowing(me, target)).isFalse();
    }

    @Test
    @DisplayName("POST /follow: 내부 redirect 경로는 그대로 따른다(검색 화면 복귀)")
    void follow_internalRedirect_honored() throws Exception {
        newUser("me@booktimer.com", "alice");
        newUser("t@booktimer.com", "bob");

        mockMvc.perform(post("/follow").param("nickname", "bob").param("redirect", "/search?q=bo")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/search?q=bo"));
    }

    @Test
    @DisplayName("POST /follow: 외부 redirect(//evil)는 무시하고 프로필로(오픈 리다이렉트 방어)")
    void follow_externalRedirect_ignored() throws Exception {
        newUser("me@booktimer.com", "alice");
        newUser("t@booktimer.com", "bob");

        mockMvc.perform(post("/follow").param("nickname", "bob").param("redirect", "//evil.com")
                        .with(user("me@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/u/bob"));
    }
}
