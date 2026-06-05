package com.booktimer.web;

import com.booktimer.block.BlockService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 차단 컨트롤러 통합 테스트 (MockMvc + 실제 빈·H2) — SNS 5단계.
 *
 * <p>차단 후엔 상대 프로필이 404가 되므로 차단·언차단은 본인 차단 목록(/me/blocks)으로 돌아온다.
 * 비로그인은 default-deny로 /login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BlockService blockService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User newUser(String email, String loginId, String nick) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), nick, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("POST /block: login_id로 대상을 차단하고 차단 목록으로 리다이렉트한다")
    void block_createsAndRedirects() throws Exception {
        User me = newUser("bme@booktimer.com", "viewer", "뷰어");
        User target = newUser("bt@booktimer.com", "target", "타겟");

        mockMvc.perform(post("/block").param("loginId", "target")
                        .with(user("bme@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/me/blocks"));

        assertThat(blockService.isBlockedBetween(me, target)).isTrue();
    }

    @Test
    @DisplayName("POST /block: 닉네임이 같아도 login_id로 정확한 대상만 차단한다")
    void block_duplicateNickname_targetsByLoginId() throws Exception {
        User me = newUser("dme@booktimer.com", "viewer", "뷰어");
        User a = newUser("da@booktimer.com", "alpha", "동명이인");
        User b = newUser("db@booktimer.com", "bravo", "동명이인"); // 같은 닉네임

        mockMvc.perform(post("/block").param("loginId", "bravo")
                        .with(user("dme@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/me/blocks"));

        assertThat(blockService.isBlockedBetween(me, b)).isTrue();   // 정확히 bravo만
        assertThat(blockService.isBlockedBetween(me, a)).isFalse();  // alpha는 아님
    }

    @Test
    @DisplayName("POST /unblock: login_id로 차단을 해제한다")
    void unblock_removes() throws Exception {
        User me = newUser("ume@booktimer.com", "viewer", "뷰어");
        User target = newUser("ut@booktimer.com", "target", "타겟");
        blockService.block(me, target);

        mockMvc.perform(post("/unblock").param("loginId", "target")
                        .with(user("ume@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/me/blocks"));

        assertThat(blockService.isBlockedBetween(me, target)).isFalse();
    }

    @Test
    @DisplayName("GET /me/blocks: 내가 차단한 사용자 목록을 그린다(표시는 닉네임)")
    @SuppressWarnings("unchecked")
    void blocks_listsBlocked() throws Exception {
        User me = newUser("lme@booktimer.com", "viewer", "뷰어");
        User target = newUser("lt@booktimer.com", "target", "타겟");
        blockService.block(me, target);

        mockMvc.perform(get("/me/blocks").with(user("lme@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("block-list"))
                .andExpect(model().attributeExists("blocked"))
                .andExpect(result -> {
                    var blocked = (List<UserSearchResult>) result.getModelAndView().getModel().get("blocked");
                    assertThat(blocked).extracting(UserSearchResult::nickname).containsExactly("타겟");
                });
    }

    @Test
    @DisplayName("비로그인은 /me/blocks 접근 시 로그인으로 리다이렉트(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/me/blocks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
