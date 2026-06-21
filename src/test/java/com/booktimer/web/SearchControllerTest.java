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
 * /search SSR 셸 컨트롤러 테스트.
 * 검색·추천·팔로우 동작은 SearchApiControllerTest·FollowApiControllerTest에서 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private void newUser(String email, String loginId, String nickname) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        userRepository.save(u);
    }

    @Test
    @DisplayName("GET /search: Vue 셸을 렌더링하고 myLoginId 를 모델에 싣는다")
    void search_rendersShellWithMyLoginId() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");

        mockMvc.perform(get("/search").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("myLoginId", "searcher"));
    }

    @Test
    @DisplayName("GET /search?q=abc: q 파라미터를 모델에 전달한다")
    void search_passesQueryParam() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");

        mockMvc.perform(get("/search").param("q", "abc").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("q", "abc"));
    }

    @Test
    @DisplayName("비로그인 사용자는 검색할 수 없다 — 로그인으로 리다이렉트")
    void search_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/search").param("q", "book"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
