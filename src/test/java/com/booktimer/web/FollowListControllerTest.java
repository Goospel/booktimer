package com.booktimer.web;

import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.search.UserSearchResult;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 본인 팔로워/팔로잉 목록 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2).
 *
 * <p>{@code /me/**}는 항상 인증된 본인 기준 — 남의 목록을 볼 경로가 없다(보안 경계 자동).
 * 비로그인은 default-deny로 /login 리다이렉트.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowListControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email, String nick) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), nick, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("GET /me/followers: 나를 팔로우한 사람들을 목록으로 그린다")
    @SuppressWarnings("unchecked")
    void followers_rendersMyFollowers() throws Exception {
        User viewer = newUser("mfv@booktimer.com", "뷰어");
        User a = newUser("mfa@booktimer.com", "에이");
        followRepository.save(Follow.of(a, viewer)); // A → 나

        mockMvc.perform(get("/me/followers").with(user("mfv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("follow-list"))
                .andExpect(model().attribute("listType", "followers"))
                .andExpect(model().attributeExists("users"))
                .andExpect(result -> {
                    var users = (List<UserSearchResult>) result.getModelAndView().getModel().get("users");
                    assertThat(users).extracting(UserSearchResult::nickname).containsExactly("에이");
                });
    }

    @Test
    @DisplayName("GET /me/following: 내가 팔로우한 사람들을 목록으로 그린다")
    @SuppressWarnings("unchecked")
    void following_rendersMyFollowing() throws Exception {
        User viewer = newUser("mgv@booktimer.com", "뷰어");
        User c = newUser("mgc@booktimer.com", "씨");
        followRepository.save(Follow.of(viewer, c)); // 나 → C

        mockMvc.perform(get("/me/following").with(user("mgv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("follow-list"))
                .andExpect(model().attribute("listType", "following"))
                .andExpect(result -> {
                    var users = (List<UserSearchResult>) result.getModelAndView().getModel().get("users");
                    assertThat(users).extracting(UserSearchResult::nickname).containsExactly("씨");
                });
    }

    @Test
    @DisplayName("비로그인은 /me/followers 접근 시 로그인으로 리다이렉트된다(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/me/followers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
