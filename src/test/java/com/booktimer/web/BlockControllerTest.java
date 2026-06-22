package com.booktimer.web;

import com.booktimer.block.BlockService;
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
 * 차단 목록 Vue 셸 컨트롤러 테스트 (GET /me/blocks) — SNS 5단계.
 *
 * <p>POST /block·/unblock은 프로필 SPA 전환 후 BlockApiController가 담당(이 파일에서 제거됨).
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
    @DisplayName("GET /me/blocks: Vue 셸 렌더 — myLoginId 주입, blocked 목록은 API(/api/blocks)에서 페치")
    void blocks_rendersVueShell() throws Exception {
        User me = newUser("lme@booktimer.com", "viewer", "뷰어");
        User target = newUser("lt@booktimer.com", "target", "타겟");
        blockService.block(me, target);

        mockMvc.perform(get("/me/blocks").with(user("lme@booktimer.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("block-list"))
                .andExpect(model().attributeExists("myLoginId"))
                .andExpect(model().attribute("myLoginId", "viewer"));
    }

    @Test
    @DisplayName("비로그인은 /me/blocks 접근 시 로그인으로 리다이렉트(default-deny)")
    void anonymous_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/me/blocks"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
