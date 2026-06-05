package com.booktimer.web;

import com.booktimer.search.UserSearchResult;
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
import org.springframework.test.web.servlet.MvcResult;
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
 * 닉네임 검색 컨트롤러 통합 테스트 (SNS 3단계, sns-design §7.3).
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
        newUser(email, loginId, nickname, Role.USER);
    }

    private void newUser(String email, String loginId, String nickname, Role role) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nickname, "Asia/Seoul", role);
        u.assignLoginId(loginId);
        userRepository.save(u);
    }

    @SuppressWarnings("unchecked")
    private List<UserSearchResult> resultsInModel(MvcResult res) {
        return (List<UserSearchResult>) res.getModelAndView().getModel().get("results");
    }

    @SuppressWarnings("unchecked")
    private List<UserSearchResult> recommendationsInModel(MvcResult res) {
        return (List<UserSearchResult>) res.getModelAndView().getModel().get("recommendations");
    }

    @Test
    @DisplayName("GET /search?q=: login_id 부분일치 결과를 화면 모델에 싣는다(핸들로 검색)")
    void search_rendersResults() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");
        newUser("t@booktimer.com", "booklover", "책좋아");

        MvcResult res = mockMvc.perform(get("/search").param("q", "book").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andReturn();

        assertThat(resultsInModel(res)).extracting(UserSearchResult::loginId).containsExactly("booklover");
    }

    @Test
    @DisplayName("GET /search: 검색어 없으면 빈 결과로 화면만 그린다")
    void search_noQuery_empty() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");

        MvcResult res = mockMvc.perform(get("/search").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andReturn();

        assertThat(resultsInModel(res)).isEmpty();
    }

    @Test
    @DisplayName("검색을 한도 이상 반복하면 레이트리밋 안내가 뜨고 결과를 싣지 않는다")
    void search_rateLimited_showsNotice() throws Exception {
        newUser("rl@booktimer.com", "limiter", "리미터");
        newUser("bt@booktimer.com", "booklover", "책좋아");

        int limit = RateLimitAction.SEARCH.limit();
        for (int i = 0; i < limit; i++) {
            mockMvc.perform(get("/search").param("q", "book").with(user("rl@booktimer.com")))
                    .andExpect(status().isOk());
        }

        MvcResult res = mockMvc.perform(get("/search").param("q", "book").with(user("rl@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("rateLimited", true))
                .andReturn();

        assertThat(resultsInModel(res)).isEmpty();
    }

    @Test
    @DisplayName("GET /search: 친구 추천(무작위)을 모델에 싣는다 — 본인·운영자는 제외")
    void search_includesRecommendations_excludingSelfAndAdmin() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");
        newUser("a@booktimer.com", "alice", "앨리스");
        newUser("b@booktimer.com", "bob", "밥");
        newUser("admin@booktimer.com", "rootadmin", "운영자", Role.ADMIN);

        MvcResult res = mockMvc.perform(get("/search").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andReturn();

        assertThat(recommendationsInModel(res)).extracting(UserSearchResult::loginId)
                .contains("alice", "bob")
                .doesNotContain("searcher", "rootadmin");
    }

    @Test
    @DisplayName("GET /search: 추천은 최대 10명으로 제한한다")
    void search_recommendations_cappedAtTen() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");
        for (int i = 0; i < 15; i++) {
            newUser("u" + i + "@booktimer.com", "candidate" + i, "후보" + i);
        }

        MvcResult res = mockMvc.perform(get("/search").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(recommendationsInModel(res)).hasSize(10);
    }

    @Test
    @DisplayName("GET /search: 공개 책장 링크용 내 loginId를 모델에 싣는다")
    void search_exposesMyLoginId() throws Exception {
        newUser("me@booktimer.com", "searcher", "검색가");

        mockMvc.perform(get("/search").with(user("me@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("myLoginId", "searcher"));
    }

    @Test
    @DisplayName("비로그인 사용자는 검색할 수 없다 — 로그인으로 리다이렉트")
    void search_anonymous_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/search").param("q", "book"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
