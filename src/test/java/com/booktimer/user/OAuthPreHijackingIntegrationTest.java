package com.booktimer.user;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
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
    private BookRepository bookRepository;
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
    @DisplayName("토스를 연결한 미검증 LOCAL 계정은 폐기되지 않고 이메일만 재배정되며, 그 사용자의 기록이 남는다")
    void unverifiedLocalLinkedToToss_isReassigned_recordsSurvive() {
        String email = "linked@booktimer.com";
        User linked = localUnverified(email);
        linked.linkTossUserKey("UKlocal-01");
        User saved = userRepository.saveAndFlush(linked);
        Long savedId = saved.getId();
        bookRepository.saveAndFlush(
                Book.register(saved, "미니앱에서 읽던 책", null, null, null, null, null, BookStatus.READING));

        User result = provisioningService.provision(email, "진짜주인", true);

        // 새 구글 계정이 그 이메일을 가진다
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getId()).isNotEqualTo(savedId);
        assertThat(userRepository.findByEmail(email)).get()
                .extracting(User::getAuthProvider).isEqualTo(AuthProvider.GOOGLE);
        // 선점 계정은 살아 있고 웹 로그인 수단·토스 연결이 그대로다 — 이메일만 비켜났다
        User survivor = userRepository.findById(savedId).orElseThrow();
        assertThat(survivor.getEmail()).isEqualTo("toss-uklocal01@noreply.booktimer.app");
        assertThat(survivor.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(survivor.getPasswordHash()).isNotNull();
        assertThat(survivor.getLoginId()).isEqualTo("squatter");
        assertThat(survivor.getTossUserKey()).isEqualTo("UKlocal-01");
        assertThat(bookRepository.countByUser(survivor)).isEqualTo(1); // 기록 보존
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

    @Test
    @DisplayName("미검증 TOSS 계정은 폐기되지 않고 이메일만 합성 주소로 비켜나며 두 계정이 공존한다")
    void unverifiedTossAccount_isReassignedToSynthetic_andBothCoexist() {
        String email = "tossvictim@booktimer.com";
        // 토스 프로필에 남의 이메일을 적어 미니앱으로 먼저 가입한 계정(미검증 — 토스는 소유를 보증하지 않는다)
        User squatter = userRepository.saveAndFlush(tossUnverified(email, "UKsquat-01"));
        Long squatterId = squatter.getId();

        // 실소유자가 Google로 로그인 → provider가 이메일 소유 보증
        User result = provisioningService.provision(email, "진짜주인", true);

        // 새 구글 계정이 그 이메일을 가진다 — 토스 계정으로 흡수되지 않는다
        assertThat(result.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getId()).isNotEqualTo(squatterId);
        assertThat(userRepository.findByEmail(email)).get()
                .extracting(User::getAuthProvider).isEqualTo(AuthProvider.GOOGLE);
        // 토스 계정은 폐기되지 않고 남는다(기록·userKey 보존) — 이메일만 합성 주소로 비켜났다
        assertThat(userRepository.findById(squatterId)).get()
                .satisfies(toss -> {
                    assertThat(toss.getEmail()).isEqualTo("toss-uksquat01@noreply.booktimer.app");
                    assertThat(toss.getTossUserKey()).isEqualTo("UKsquat-01");
                    assertThat(toss.isEmailVerified()).isFalse();
                    assertThat(toss.getAuthProvider()).isEqualTo(AuthProvider.TOSS);
                });
    }

    private User tossUnverified(String email, String userKey) {
        User u = User.ofOAuth(email, "토스유저", "Asia/Seoul", Role.USER, AuthProvider.TOSS);
        u.linkTossUserKey(userKey);
        return u;
    }

    private User localUnverified(String email) {
        User u = User.of(email, passwordEncoder.encode("rawpw1234"), "선점자", "Asia/Seoul", Role.USER);
        u.assignLoginId("squatter");
        return u;
    }
}
