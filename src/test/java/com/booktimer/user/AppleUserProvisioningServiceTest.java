package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Apple 신원 프로비저닝(v1 REQ-02·03) — 실제 빈 + H2.
 *
 * <p>토스와 같은 「먼저 묻고 만든다」다: {@code login}은 계정을 만들지 않고, {@code register}는 멱등이고,
 * 기존 계정에 붙이는 길은 웹이 발급한 코드({@code link})뿐이다. 이메일은 늘 합성 주소라 발송·충돌이 자동으로 빠진다.
 */
@SpringBootTest
@Transactional
class AppleUserProvisioningServiceTest {

    @Autowired AppleUserProvisioningService service;
    @Autowired UserRepository userRepository;
    @Autowired UserRegistrationService registrationService;

    private static final String SUB = "001234.ABCdef0123.0456";

    private User webUser(String email) {
        return registrationService.registerOAuth(email, "구글러", "Asia/Seoul", AuthProvider.GOOGLE,
                java.time.LocalDate.of(2026, 10, 21));
    }

    @Test
    @DisplayName("REQ-02 · LOGIN은 모르는 Apple 신원에 계정을 만들지 않는다(users 행 수 불변)")
    void loginUnknownCreatesNothing() {
        long before = userRepository.count();
        assertThat(service.login("never-seen.1")).isEmpty();
        assertThat(userRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("REQ-02 · REGISTER는 같은 신원으로 두 번 불러도 계정이 하나다")
    void registerIsIdempotent() {
        long before = userRepository.count();
        User first = service.register(SUB);
        User second = service.register(SUB);
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.count()).isEqualTo(before + 1);
        assertThat(service.login(SUB)).map(User::getId).contains(first.getId());
    }

    @Test
    @DisplayName("REQ-02 · Apple 계정의 이메일은 apple-{sub 소문자}@noreply.booktimer.app이고 isSyntheticEmail이 참이다")
    void registerUsesSyntheticEmail() {
        User u = service.register(SUB);
        assertThat(u.getEmail()).isEqualTo("apple-001234.abcdef0123.0456@noreply.booktimer.app");
        assertThat(u.hasSyntheticEmail()).isTrue();
        assertThat(u.isEmailVerified()).isFalse();
        assertThat(u.getAuthProvider()).isEqualTo(AuthProvider.APPLE);
        assertThat(u.getAppleSub()).isEqualTo(SUB);
        assertThat(u.getLoginId()).isNull();
        assertThat(u.isWebAccount()).isFalse();
    }

    @Test
    @DisplayName("REQ-02 · LINK는 웹 계정에 sub를 붙이고 원 가입 경로는 그대로다(webAccount=true)")
    void linkKeepsProvider() {
        User web = webUser("link-me@booktimer.com");
        service.link(web, SUB);
        User reloaded = userRepository.findById(web.getId()).orElseThrow();
        assertThat(reloaded.getAppleSub()).isEqualTo(SUB);
        assertThat(reloaded.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(reloaded.isWebAccount()).isTrue();
        assertThat(service.login(SUB)).map(User::getId).contains(web.getId());
    }

    @Test
    @DisplayName("REQ-03 · 이미 다른 계정에 붙은 Apple 신원은 409 — 대상 계정은 그대로")
    void linkSubOwnedElsewhereConflicts() {
        service.register(SUB);
        User web = webUser("victim@booktimer.com");
        assertThatThrownBy(() -> service.link(web, SUB))
                .isInstanceOf(AppleLinkConflictException.class)
                .hasMessage("이 Apple ID는 이미 다른 책생글 계정에 연결돼 있어요");
        assertThat(userRepository.findById(web.getId()).orElseThrow().getAppleSub()).isNull();
    }

    @Test
    @DisplayName("REQ-03 · 이미 Apple이 붙은 계정에 다른 신원은 409 — 붙어 있던 신원은 그대로")
    void linkOntoLinkedAccountConflicts() {
        User web = webUser("already@booktimer.com");
        service.link(web, SUB);
        assertThatThrownBy(() -> service.link(web, "other.sub.9"))
                .isInstanceOf(AppleLinkConflictException.class)
                .hasMessage("이 웹 계정에는 이미 다른 Apple ID가 연결돼 있어요 — 웹 설정에서 연결을 해제한 뒤 다시 해 주세요");
        assertThat(userRepository.findById(web.getId()).orElseThrow().getAppleSub()).isEqualTo(SUB);
    }
}
