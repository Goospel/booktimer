package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pre-hijacking 차단 통합 테스트 (실제 빈 + H2) — 미검증 LOCAL 선점 계정이 OAuth로 안전하게 대체되는지.
 *
 * <p>핵심 회귀: provision이 선점 계정을 폐기한 뒤 같은 이메일로 OAuth를 INSERT한다. 삭제를 먼저 flush하지
 * 않으면 Hibernate가 INSERT를 DELETE보다 먼저 실행해 {@code uk_users_email} 유니크 제약을 위반한다 —
 * mock 단위테스트는 못 잡고 실제 스키마에서만 드러난다(AccountDeletionIntegrationTest와 같은 정신).
 */
@SpringBootTest
@Transactional
class OAuthPreHijackingIntegrationTest {

    @Autowired
    private OAuthUserProvisioningService provisioningService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("미검증 LOCAL 선점 계정은 폐기되고, 같은 이메일이 OAuth 계정으로 대체된다 (FK·유니크 위반 없이)")
    void unverifiedLocalSquatter_isReplacedByOAuth() {
        String email = "victim@booktimer.com";
        // 공격자가 피해자 이메일로 선점한 미검증 LOCAL 계정(가입 직후 — login_id 보유, 미검증)
        User squatter = userRepository.saveAndFlush(
                localUnverified(email));
        Long squatterId = squatter.getId();

        // 피해자가 Google로 로그인 → provider가 이메일 소유 보증(verified=true)
        User result = provisioningService.provision(email, "진짜주인", true);

        // 새 계정은 OAuth(비밀번호 없음), 같은 이메일은 정확히 하나만 — 선점 계정은 사라짐
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getPasswordHash()).isNull();
        assertThat(result.getId()).isNotEqualTo(squatterId);
        assertThat(userRepository.findByEmail(email)).get()
                .extracting(User::getAuthProvider).isEqualTo(AuthProvider.GOOGLE);
        assertThat(userRepository.findById(squatterId)).isEmpty(); // 선점 계정 폐기됨
    }

    @Test
    @DisplayName("검증된 LOCAL 계정은 폐기되지 않고 그대로 연결된다(정당한 소유자)")
    void verifiedLocalAccount_isLinkedNotReplaced() {
        String email = "owner@booktimer.com";
        User owner = localUnverified(email);
        owner.verifyEmail(); // 이메일 검증 완료 = 소유 증명
        User saved = userRepository.saveAndFlush(owner);

        User result = provisioningService.provision(email, "주인", true);

        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.LOCAL); // 그대로 LOCAL
    }

    private User localUnverified(String email) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), "선점자", "Asia/Seoul", Role.USER);
        u.assignLoginId("squatter");
        return u;
    }
}
