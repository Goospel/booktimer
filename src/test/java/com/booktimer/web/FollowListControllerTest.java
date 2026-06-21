package com.booktimer.web;

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
    private PasswordEncoder passwordEncoder;

    private User newUser(String email, String loginId, String nick) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nick, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("GET /me/followers: Vue 셸 — myLoginId·initialTab=followers 모델, follow-list 뷰")
    void followers_rendersShell() throws Exception {
        User viewer = newUser("mfv@booktimer.com", "mfviewer1", "뷰어");
        // 목록 데이터는 API에서 오므로 users·listType은 model에 없음
        mockMvc.perform(get("/me/followers").with(user("mfv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("follow-list"))
                .andExpect(model().attribute("myLoginId", "mfviewer1"))
                .andExpect(model().attribute("initialTab", "followers"));
    }

    @Test
    @DisplayName("GET /me/following: Vue 셸 — myLoginId·initialTab=following 모델, follow-list 뷰")
    void following_rendersShell() throws Exception {
        User viewer = newUser("mgv@booktimer.com", "mgviewer1", "뷰어");
        mockMvc.perform(get("/me/following").with(user("mgv@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("follow-list"))
                .andExpect(model().attribute("myLoginId", "mgviewer1"))
                .andExpect(model().attribute("initialTab", "following"));
    }

    @Test
    @DisplayName("비로그인은 /me/followers 접근 시 로그인으로 리다이렉트된다(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/me/followers"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
