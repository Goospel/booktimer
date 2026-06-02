package com.booktimer.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User 도메인 엔티티 테스트 (DB 무관 — 객체 생성/검증만).
 *
 * <p>설계(domain-design.md): User(1) ↔ ReadingTimer(1), User(1) ↔ ReadingSession(N).
 * 이 증분은 User 자체의 팩토리·불변식만 다룬다. ReadingTimer 연관은 다음 증분.
 *
 * <p>필드: email / passwordHash(이미 해시된 값 저장) / nickname /
 * timezone(IANA, 예: "Asia/Seoul") / role(enum). 비밀번호 평문 해싱은 서비스 책임이라
 * 엔티티는 이미 해시된 문자열만 받는다.
 */
class UserTest {

    private static final String EMAIL = "reader@booktimer.com";
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv"; // BCrypt 형태(예시)
    private static final String NICK = "책벌레";
    private static final String TZ = "Asia/Seoul";

    @Test
    @DisplayName("유효한 값으로 생성하면 각 필드가 그대로 보관된다")
    void of_validArgs_keepsFields() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
        assertThat(user.getNickname()).isEqualTo(NICK);
        assertThat(user.getTimezone()).isEqualTo(TZ);
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("of(...)로 만든 사용자는 LOCAL 계정이다(비밀번호 보유)")
    void of_isLocalProvider() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.isLocalAccount()).isTrue();
    }

    @Test
    @DisplayName("이메일이 비어있으면 예외")
    void of_blankEmail_throws() {
        assertThatThrownBy(() -> User.of("  ", HASH, NICK, TZ, Role.USER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.of(null, HASH, NICK, TZ, Role.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일 형식이 아니면 예외")
    void of_malformedEmail_throws() {
        assertThatThrownBy(() -> User.of("not-an-email", HASH, NICK, TZ, Role.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비밀번호 해시가 비어있으면 예외")
    void of_blankPasswordHash_throws() {
        assertThatThrownBy(() -> User.of(EMAIL, "  ", NICK, TZ, Role.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("닉네임이 비어있으면 예외")
    void of_blankNickname_throws() {
        assertThatThrownBy(() -> User.of(EMAIL, HASH, " ", TZ, Role.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("타임존이 유효한 IANA ID가 아니면 예외")
    void of_invalidTimezone_throws() {
        assertThatThrownBy(() -> User.of(EMAIL, HASH, NICK, "Mars/Phobos", Role.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("role이 null이면 예외")
    void of_nullRole_throws() {
        assertThatThrownBy(() -> User.of(EMAIL, HASH, NICK, TZ, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateProfile: 설정 페이지에서 닉네임/타임존 변경 ---

    @Test
    @DisplayName("updateProfile: 닉네임과 타임존을 바꾸고 나머지 식별/인증 필드는 유지한다")
    void updateProfile_changesNicknameAndTimezone() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.updateProfile("새책벌레", "America/New_York");

        assertThat(user.getNickname()).isEqualTo("새책벌레");
        assertThat(user.getTimezone()).isEqualTo("America/New_York");
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo(HASH);
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("updateProfile: 닉네임이 비어있으면 예외")
    void updateProfile_blankNickname_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.updateProfile("  ", TZ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateProfile: 타임존이 유효한 IANA ID가 아니면 예외")
    void updateProfile_invalidTimezone_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.updateProfile(NICK, "Mars/Phobos"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- changePassword: 비밀번호 변경 (이미 해시된 새 값으로 교체) ---

    @Test
    @DisplayName("changePassword: 비밀번호 해시를 새 값으로 바꾸고 다른 필드는 유지한다")
    void changePassword_replacesHash() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.changePassword("$2a$10$NEWHASHvalue000000000");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$10$NEWHASHvalue000000000");
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getNickname()).isEqualTo(NICK);
    }

    @Test
    @DisplayName("changePassword: 새 해시가 비어있으면 예외 (평문 금지 불변식 유지)")
    void changePassword_blankHash_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.changePassword("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changePassword(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- ofOAuth: 소셜 로그인으로 만든 사용자 (비밀번호 없음) ---

    @Test
    @DisplayName("ofOAuth: 비밀번호 없이 provider 계정으로 생성 — 해시는 null, provider가 보관된다")
    void ofOAuth_createsPasswordlessProviderAccount() {
        User user = User.ofOAuth(EMAIL, NICK, TZ, Role.USER, AuthProvider.GOOGLE);

        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getNickname()).isEqualTo(NICK);
        assertThat(user.getTimezone()).isEqualTo(TZ);
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.isLocalAccount()).isFalse();
    }

    @Test
    @DisplayName("ofOAuth: provider가 LOCAL이면 예외 — 비밀번호 없는 LOCAL 계정은 모순")
    void ofOAuth_localProvider_throws() {
        assertThatThrownBy(() -> User.ofOAuth(EMAIL, NICK, TZ, Role.USER, AuthProvider.LOCAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ofOAuth: provider가 null이면 예외")
    void ofOAuth_nullProvider_throws() {
        assertThatThrownBy(() -> User.ofOAuth(EMAIL, NICK, TZ, Role.USER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ofOAuth: 이메일/닉네임/타임존도 LOCAL과 동일하게 검증한다")
    void ofOAuth_validatesCommonFields() {
        assertThatThrownBy(() -> User.ofOAuth("bad-email", NICK, TZ, Role.USER, AuthProvider.GOOGLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.ofOAuth(EMAIL, " ", TZ, Role.USER, AuthProvider.GOOGLE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> User.ofOAuth(EMAIL, NICK, "Mars/Phobos", Role.USER, AuthProvider.GOOGLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("changePassword: 소셜(비밀번호 없는) 계정에서는 호출 불가 — 예외")
    void changePassword_oauthAccount_throws() {
        User user = User.ofOAuth(EMAIL, NICK, TZ, Role.USER, AuthProvider.GOOGLE);

        assertThatThrownBy(() -> user.changePassword("$2a$10$NEWHASHvalue000000000"))
                .isInstanceOf(IllegalStateException.class);
    }
}
