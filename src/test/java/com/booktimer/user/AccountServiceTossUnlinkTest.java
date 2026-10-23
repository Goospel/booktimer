package com.booktimer.user;

import com.booktimer.auth.ApiTokenService;
import com.booktimer.auth.AppleIdentityVerifier;
import com.booktimer.testsupport.MockedBoundaryTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토스 연결 끊기 콜백과 Apple(v1 REQ-08) — 토스로 가입한 계정이라도 Apple이 붙어 있으면 지우지 않는다.
 * 지우면 앱에서 쓴 기록이 토스 쪽 동작 하나로 사라진다(§3.3-4).
 */
@MockedBoundaryTest
@Transactional
class AccountServiceTossUnlinkTest {

    @Autowired AccountService accountService;
    @Autowired TossUserProvisioningService tossUsers;
    @Autowired UserRepository userRepository;
    @Autowired ApiTokenService apiTokenService;
    @Autowired AppleIdentityVerifier verifier;
    @Autowired MockMvc mockMvc;
    @Autowired com.booktimer.security.RateLimitService rateLimitService;

    private static final String SUB = "000555.cafe.0005";

    @Test
    @DisplayName("REQ-08 · 토스 연결 끊기는 Apple이 붙은 토스 가입 계정을 지우지 않는다")
    void appleLinkedTossAccountIsSevered() {
        User withApple = tossUsers.register("uk-apple", null);
        withApple.linkAppleSub(SUB);
        userRepository.saveAndFlush(withApple);
        tossUsers.register("uk-plain", null); // 대조군 — Apple이 없으면 예전대로 지워진다

        assertThat(accountService.unlinkToss("uk-apple")).isEqualTo(AccountService.TossUnlinkOutcome.SEVERED);
        assertThat(accountService.unlinkToss("uk-plain")).isEqualTo(AccountService.TossUnlinkOutcome.PURGED);

        User kept = userRepository.findByAppleSub(SUB).orElseThrow();
        assertThat(kept.getTossUserKey()).isNull();
    }

    @Test
    @DisplayName("REQ-84 · 카카오가 붙은 계정은 토스 끊기에 지워지지 않는다")
    void kakaoLinkedTossAccountIsSevered() {
        User withKakao = tossUsers.register("uk-kakao", null);
        withKakao.linkKakaoSub("4012345678");
        userRepository.saveAndFlush(withKakao);
        tossUsers.register("uk-plain-2", null); // 대조군 — 카카오가 없으면 예전대로 지워진다

        assertThat(accountService.unlinkToss("uk-kakao")).isEqualTo(AccountService.TossUnlinkOutcome.SEVERED);
        assertThat(accountService.unlinkToss("uk-plain-2")).isEqualTo(AccountService.TossUnlinkOutcome.PURGED);
        assertThat(userRepository.findByKakaoSub("4012345678")).isPresent();
    }

    @Test
    @DisplayName("REQ-08 · SEVERED 뒤 앱 토큰은 401이고 Apple LOGIN이 같은 계정을 돌려준다")
    void afterSeverAppTokenDiesAndAppleLoginReturnsSameAccount() throws Exception {
        User u = tossUsers.register("uk-apple-2", null);
        u.linkAppleSub(SUB);
        userRepository.saveAndFlush(u);
        String appToken = apiTokenService.issue(u);

        accountService.unlinkToss("uk-apple-2");
        rateLimitService.clearForTest(); // 같은 세계의 앞 클래스가 IP 상한을 채웠을 수 있다

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + appToken))
                .andExpect(status().isUnauthorized());
        when(verifier.isConfigured()).thenReturn(true);
        when(verifier.verify(any())).thenReturn(Optional.of(SUB));
        mockMvc.perform(post("/api/app/auth/apple").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identityToken\":\"t\",\"intent\":\"LOGIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.userId").value(u.getId()));
    }
}
