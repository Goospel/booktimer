package com.booktimer.web.api;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 기본 설정(스위치 OFF)에서 {@code /api/chat/**}는 전부 404 — 다크 머지의 근거(설계 §9).
 * 켜진 컨텍스트의 200은 {@link ChatApiControllerTest}가 양성 대조군이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatApiKillSwitchTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;

    @Test
    void everyChatPathIsNotFoundWhileSwitchedOff() throws Exception {
        User u = User.of("ks@chatapi.test", "$2a$10$abcdefghijklmnopqrstuv", "킬", "Asia/Seoul", Role.USER);
        u.linkTossUserKey("uk-ks");
        String token = apiTokenService.issue(userRepository.save(u));

        mockMvc.perform(get("/api/chat/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/chat/rooms").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chat/rooms").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"loginId\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chat/rooms/1/messages").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
