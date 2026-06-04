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

    private User newUser(String email, String nick) {
        return userRepository.save(
                User.of(email, passwordEncoder.encode("rawpw1234"), nick, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("POST /block: 대상을 차단하고 차단 목록으로 리다이렉트한다")
    void block_createsAndRedirects() throws Exception {
        User me = newUser("bme@booktimer.com", "뷰어");
        User target = newUser("bt@booktimer.com", "타겟");

        mockMvc.perform(post("/block").param("nickname", "타겟")
                        .with(user("bme@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/me/blocks"));

        assertThat(blockService.isBlockedBetween(me, target)).isTrue();
    }

    @Test
    @DisplayName("POST /unblock: 차단을 해제한다")
    void unblock_removes() throws Exception {
        User me = newUser("ume@booktimer.com", "뷰어");
        User target = newUser("ut@booktimer.com", "타겟");
        blockService.block(me, target);

        mockMvc.perform(post("/unblock").param("nickname", "타겟")
                        .with(user("ume@booktimer.com")).with(csrf()))
                .andExpect(redirectedUrl("/me/blocks"));

        assertThat(blockService.isBlockedBetween(me, target)).isFalse();
    }

    @Test
    @DisplayName("GET /me/blocks: 내가 차단한 사용자 목록을 그린다")
    @SuppressWarnings("unchecked")
    void blocks_listsBlocked() throws Exception {
        User me = newUser("lme@booktimer.com", "뷰어");
        User target = newUser("lt@booktimer.com", "타겟");
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
