package com.booktimer.push;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PushApiController 보안·배선 통합 테스트 (SpringBootTest + H2).
 *
 * <p>검증 항목:
 * <ul>
 *   <li>미인증 접근 → 302(로그인 리다이렉트)</li>
 *   <li>인증 후 subscribe → 200 + DB 구독 행 생성</li>
 *   <li>인증 후 unsubscribe(IDOR: 남의 endpoint) → 200, 본인 구독은 삭제 안 됨</li>
 *   <li>인증 후 public-key → 200</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PushApiControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepo;
    @Autowired PushSubscriptionRepository subRepo;

    private static final String EP1 = "https://fcm.googleapis.com/ep-test-1";

    private User savedUser(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "테스터", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepo.save(u);
    }

    @Test
    @DisplayName("미인증 subscribe 요청은 4xx(CSRF 403 또는 인증 302)로 거부된다")
    void subscribe_unauthenticated_isRejected() throws Exception {
        // CSRF 토큰 없이 미인증 POST → 403(CSRF 검증 실패)이 먼저 발생.
        // CSRF 있어도 미인증이면 302(로그인 리다이렉트). 어느 쪽이든 200이 아니면 보안 게이트 통과.
        mvc.perform(post("/api/push/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + EP1 + "\",\"p256dh\":\"p256\",\"auth\":\"auth\"}"))
                .andExpect(status().is(403)); // CSRF 없는 미인증 POST → 403
    }

    @Test
    @DisplayName("CSRF 있지만 미인증 subscribe 는 302(로그인 리다이렉트)를 반환한다")
    void subscribe_csrfButUnauthenticated_redirectsToLogin() throws Exception {
        mvc.perform(post("/api/push/subscribe")
                        .with(csrf()) // CSRF 토큰은 있지만 인증 없음
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + EP1 + "\",\"p256dh\":\"p256\",\"auth\":\"auth\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("미인증 unsubscribe 요청은 4xx(CSRF 403)로 거부된다")
    void unsubscribe_unauthenticated_isRejected() throws Exception {
        mvc.perform(post("/api/push/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + EP1 + "\"}"))
                .andExpect(status().is(403));
    }

    @Test
    @DisplayName("인증 후 subscribe 요청은 200 + DB에 구독 행이 생성된다")
    void subscribe_authenticated_creates200AndDbRow() throws Exception {
        savedUser("owner@test.com", "ownerid");

        mvc.perform(post("/api/push/subscribe")
                        .with(user("ownerid").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + EP1 + "\",\"p256dh\":\"pk256\",\"auth\":\"authsec\"}"))
                .andExpect(status().isOk());

        Optional<PushSubscription> sub = subRepo.findByEndpoint(EP1);
        assertThat(sub).isPresent();
    }

    @Test
    @DisplayName("IDOR: 남의 endpoint 로 unsubscribe 해도 해당 구독은 삭제되지 않는다")
    void unsubscribe_idor_doesNotDeleteOthersSubscription() throws Exception {
        User owner = savedUser("owner2@test.com", "ownerid2");
        savedUser("attacker@test.com", "attackerid");
        subRepo.save(PushSubscription.of(owner, EP1, "p256", "auth"));

        mvc.perform(post("/api/push/unsubscribe")
                        .with(user("attackerid").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + EP1 + "\"}"))
                .andExpect(status().isOk());

        // 오너의 구독은 그대로
        assertThat(subRepo.findByEndpoint(EP1)).isPresent();
    }

    @Test
    @DisplayName("인증 후 public-key 요청은 200을 반환한다")
    void publicKey_authenticated_returns200() throws Exception {
        mvc.perform(get("/api/push/public-key")
                        .with(user("anyid").roles("USER")))
                .andExpect(status().isOk());
    }
}
