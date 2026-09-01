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
    @DisplayName("updateProfile: 닉네임이 상한(30자)을 넘으면 예외 — 웹 폼·미니앱 API가 공유하는 단일 출처")
    void updateProfile_tooLongNickname_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.updateProfile("가".repeat(User.NICKNAME_MAX_LENGTH + 1), TZ))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getNickname()).isEqualTo(NICK); // 거부됐으면 옛 값 그대로다
    }

    @Test
    @DisplayName("updateProfile: 정확히 상한 길이(30자)는 통과한다 — 경계에서 1자 어긋나면 못 바꾸는 사람이 생긴다")
    void updateProfile_maxLengthNickname_ok() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        String exactly = "가".repeat(User.NICKNAME_MAX_LENGTH);

        user.updateProfile(exactly, TZ);

        assertThat(user.getNickname()).isEqualTo(exactly);
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

    // --- onboarded: 첫 진입 시 초기 설정(온보딩) 완료 여부 ---

    @Test
    @DisplayName("새로 만든 LOCAL 사용자는 아직 온보딩 전이다 (isOnboarded=false)")
    void of_isNotOnboardedYet() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("새로 만든 소셜 사용자도 아직 온보딩 전이다 (isOnboarded=false)")
    void ofOAuth_isNotOnboardedYet() {
        User user = User.ofOAuth(EMAIL, NICK, TZ, Role.USER, AuthProvider.GOOGLE);

        assertThat(user.isOnboarded()).isFalse();
    }

    @Test
    @DisplayName("completeOnboarding: 온보딩 완료 표시 — isOnboarded=true (멱등)")
    void completeOnboarding_marksOnboarded() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.completeOnboarding();
        assertThat(user.isOnboarded()).isTrue();

        user.completeOnboarding(); // 다시 호출해도 true 유지(멱등)
        assertThat(user.isOnboarded()).isTrue();
    }

    // --- emailVerified: 가입 이메일 인증 상태 (이메일 인프라 1단계 PR-B) ---

    @Test
    @DisplayName("새로 만든 LOCAL 사용자는 이메일 미검증이다 (isEmailVerified=false)")
    void of_isNotEmailVerifiedYet() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("새로 만든 소셜 사용자도 기본은 이메일 미검증이다 (검증은 provider 보증과 별개 플래그)")
    void ofOAuth_isNotEmailVerifiedYet() {
        User user = User.ofOAuth(EMAIL, NICK, TZ, Role.USER, AuthProvider.GOOGLE);

        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("verifyEmail: 이메일 검증 완료 표시 — isEmailVerified=true (멱등)")
    void verifyEmail_marksVerified() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.verifyEmail();
        assertThat(user.isEmailVerified()).isTrue();

        user.verifyEmail(); // 다시 호출해도 true 유지(멱등)
        assertThat(user.isEmailVerified()).isTrue();
    }

    // --- 마케팅 수신동의 / 재참여 넛지 (이메일 인프라 2단계 PR-1) ---

    private static final java.time.Clock CLK =
            java.time.Clock.fixed(java.time.Instant.parse("2026-06-11T01:00:00Z"), java.time.ZoneOffset.UTC);

    @Test
    @DisplayName("새 LOCAL 사용자는 마케팅 미동의(기본 OFF)이고 동의시각·넛지시각이 없다 (끼워팔기 금지·기본 OFF 불변식)")
    void of_marketingDefaultsOff() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.isMarketingEmailConsent()).isFalse();
        assertThat(user.getMarketingConsentAt()).isNull();
        assertThat(user.getLastNudgeSentAt()).isNull();
    }

    @Test
    @DisplayName("새 소셜 사용자도 마케팅 미동의(기본 OFF)다")
    void ofOAuth_marketingDefaultsOff() {
        User user = User.ofOAuth(EMAIL, NICK, TZ, Role.USER, AuthProvider.GOOGLE);

        assertThat(user.isMarketingEmailConsent()).isFalse();
    }

    @Test
    @DisplayName("consentToMarketing: 동의 true 전환 + 동의시각 기록 (2년 재동의·감사 근거)")
    void consentToMarketing_setsTrueAndTimestamp() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.consentToMarketing(CLK);

        assertThat(user.isMarketingEmailConsent()).isTrue();
        assertThat(user.getMarketingConsentAt()).isEqualTo(java.time.Instant.parse("2026-06-11T01:00:00Z"));
    }

    @Test
    @DisplayName("withdrawMarketingConsent: 동의 false 전환하되 동의시각은 감사용으로 보존한다")
    void withdrawMarketingConsent_clearsConsentButKeepsTimestamp() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        user.consentToMarketing(CLK);

        user.withdrawMarketingConsent();

        assertThat(user.isMarketingEmailConsent()).isFalse();
        assertThat(user.getMarketingConsentAt())
                .as("철회해도 동의 이력(시각)은 감사 근거로 남긴다")
                .isEqualTo(java.time.Instant.parse("2026-06-11T01:00:00Z"));
    }

    @Test
    @DisplayName("recordNudgeSent: 마지막 넛지 발송 시각을 기록한다 (이 비활동 구간 1회 보장·멱등)")
    void recordNudgeSent_storesTimestamp() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        java.time.Instant when = java.time.Instant.parse("2026-06-11T01:00:00Z");

        user.recordNudgeSent(when);

        assertThat(user.getLastNudgeSentAt()).isEqualTo(when);
    }

    // --- 책BTI "다시 분석" 일일 횟수 제한 (악의적 반복 클릭 → LLM 남용 방어) ---

    private static final java.time.LocalDate D1 = java.time.LocalDate.of(2026, 6, 8);
    private static final java.time.LocalDate D2 = java.time.LocalDate.of(2026, 6, 9);

    @Test
    @DisplayName("tryConsumePersonalityRefresh: 같은 날 한도(3)까지 허용하고 4번째는 거부한다")
    void tryConsumeRefresh_allowsUpToLimitThenDenies() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.tryConsumePersonalityRefresh(D1)).isTrue();  // 1
        assertThat(user.tryConsumePersonalityRefresh(D1)).isTrue();  // 2
        assertThat(user.tryConsumePersonalityRefresh(D1)).isTrue();  // 3
        assertThat(user.tryConsumePersonalityRefresh(D1)).isFalse(); // 4 — 한도 초과
        assertThat(user.tryConsumePersonalityRefresh(D1)).isFalse(); // 반복 거부(상태 안정)
    }

    @Test
    @DisplayName("tryConsumePersonalityRefresh: 날짜가 바뀌면 카운트가 리셋돼 다시 허용된다 (자정 경계·일일 이월)")
    void tryConsumeRefresh_resetsOnNewDay() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        user.tryConsumePersonalityRefresh(D1);
        user.tryConsumePersonalityRefresh(D1);
        user.tryConsumePersonalityRefresh(D1);
        assertThat(user.tryConsumePersonalityRefresh(D1)).isFalse(); // D1 소진

        // 다음 날 — 리셋되어 다시 3번 가능
        assertThat(user.tryConsumePersonalityRefresh(D2)).isTrue();
        assertThat(user.tryConsumePersonalityRefresh(D2)).isTrue();
        assertThat(user.tryConsumePersonalityRefresh(D2)).isTrue();
        assertThat(user.tryConsumePersonalityRefresh(D2)).isFalse();
    }

    @Test
    @DisplayName("remainingPersonalityRefreshes: 신규는 한도 전부, 소비할수록 줄고, 다음 날은 다시 가득 (상태 불변 읽기)")
    void remainingRefreshes_reflectsConsumptionAndRollover() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.remainingPersonalityRefreshes(D1)).isEqualTo(User.DAILY_PERSONALITY_REFRESH_LIMIT);
        // 읽기만으로는 줄지 않는다(부수효과 없음)
        assertThat(user.remainingPersonalityRefreshes(D1)).isEqualTo(User.DAILY_PERSONALITY_REFRESH_LIMIT);

        user.tryConsumePersonalityRefresh(D1);
        assertThat(user.remainingPersonalityRefreshes(D1)).isEqualTo(User.DAILY_PERSONALITY_REFRESH_LIMIT - 1);

        user.tryConsumePersonalityRefresh(D1);
        user.tryConsumePersonalityRefresh(D1);
        assertThat(user.remainingPersonalityRefreshes(D1)).isZero(); // 다 씀

        assertThat(user.remainingPersonalityRefreshes(D2)).isEqualTo(User.DAILY_PERSONALITY_REFRESH_LIMIT); // 다음 날 가득
    }

    // --- 광고 경로 총량 상한: 카운터 하나에 천장 둘 (미니앱 리워드 광고 관문, 설계 §3.2) ---

    @Test
    @DisplayName("천장 파라미터: 웹(3)이 소진돼도 총량(10)까지는 계속 허용하고 11회째에 거부한다")
    void tryConsumeRefresh_totalLimit_continuesAfterWebLimitExhausted() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        for (int i = 0; i < User.DAILY_PERSONALITY_REFRESH_LIMIT; i++) {
            assertThat(user.tryConsumePersonalityRefresh(D1)).isTrue();
        }
        assertThat(user.tryConsumePersonalityRefresh(D1)).isFalse(); // 웹 천장 소진

        // 같은 카운터인데 천장만 총량 — 4~10회째는 통과
        for (int i = User.DAILY_PERSONALITY_REFRESH_LIMIT; i < User.DAILY_PERSONALITY_TOTAL_LIMIT; i++) {
            assertThat(user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT)).isTrue();
        }
        assertThat(user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT)).isFalse(); // 11회째
        assertThat(user.remainingPersonalityRefreshes(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT)).isZero();
    }

    @Test
    @DisplayName("카운터 공유(의도된 비대칭): 광고 경로 2회 소비가 웹 무광고 칸도 함께 깎는다")
    void tryConsumeRefresh_adPathConsumesWebQuotaToo() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT);
        user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT);

        assertThat(user.remainingPersonalityRefreshes(D1)).isEqualTo(1); // 웹 기준 잔여도 깎였다
        assertThat(user.tryConsumePersonalityRefresh(D1)).isTrue();      // 웹 마지막 칸
        assertThat(user.tryConsumePersonalityRefresh(D1)).isFalse();     // 웹은 여기서 끝
        assertThat(user.remainingPersonalityRefreshes(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT)).isEqualTo(7);
    }

    @Test
    @DisplayName("천장 파라미터: 날짜가 바뀌면 총량도 함께 리셋된다 (자정 경계)")
    void tryConsumeRefresh_totalLimit_resetsOnNewDay() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        for (int i = 0; i < User.DAILY_PERSONALITY_TOTAL_LIMIT; i++) {
            user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT);
        }
        assertThat(user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT)).isFalse();

        assertThat(user.tryConsumePersonalityRefresh(D2, User.DAILY_PERSONALITY_TOTAL_LIMIT)).isTrue();
        assertThat(user.remainingPersonalityRefreshes(D2)).isEqualTo(User.DAILY_PERSONALITY_REFRESH_LIMIT - 1);
    }

    @Test
    @DisplayName("remainingPersonalityRefreshes(today, limit): 미소비=총량, 읽기는 상태 불변, 다음 날은 가득")
    void remainingRefreshes_withLimit_isPureRead() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.remainingPersonalityRefreshes(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT))
                .isEqualTo(User.DAILY_PERSONALITY_TOTAL_LIMIT);
        // 읽기가 소비하지 않는다 — 두 번 읽어도 같은 값
        assertThat(user.remainingPersonalityRefreshes(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT))
                .isEqualTo(User.DAILY_PERSONALITY_TOTAL_LIMIT);

        user.tryConsumePersonalityRefresh(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT);
        assertThat(user.remainingPersonalityRefreshes(D1, User.DAILY_PERSONALITY_TOTAL_LIMIT))
                .isEqualTo(User.DAILY_PERSONALITY_TOTAL_LIMIT - 1);
        assertThat(user.remainingPersonalityRefreshes(D2, User.DAILY_PERSONALITY_TOTAL_LIMIT))
                .isEqualTo(User.DAILY_PERSONALITY_TOTAL_LIMIT);
    }

    // --- promoteToAdmin: 운영자(ADMIN) 승격 (멱등) ---

    @Test
    @DisplayName("promoteToAdmin: USER를 ADMIN으로 올리고 실제 변경이면 true를 반환한다")
    void promoteToAdmin_fromUser_promotesAndReturnsTrue() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        boolean changed = user.promoteToAdmin();

        assertThat(changed).isTrue();
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("promoteToAdmin: 이미 ADMIN이면 변경 없이 false (멱등)")
    void promoteToAdmin_alreadyAdmin_isIdempotent() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.ADMIN);

        boolean changed = user.promoteToAdmin();

        assertThat(changed).isFalse();
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    // --- assignLoginId: 로그인 아이디(login_id) 설정·검증 (a-z0-9_, 3~20, 소문자 정규화, 예약어 차단) ---

    @Test
    @DisplayName("새로 만든 사용자는 아직 login_id가 없다 (null)")
    void of_hasNoLoginIdYet() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.getLoginId()).isNull();
    }

    @Test
    @DisplayName("assignLoginId: 유효한 아이디를 그대로 보관한다")
    void assignLoginId_valid_stored() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.assignLoginId("goospel_01");

        assertThat(user.getLoginId()).isEqualTo("goospel_01");
    }

    @Test
    @DisplayName("assignLoginId: 대문자는 소문자로 정규화해 저장한다(대소문자 구분 안 함)")
    void assignLoginId_normalizesToLowercase() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.assignLoginId("Goospel");

        assertThat(user.getLoginId()).isEqualTo("goospel");
    }

    @Test
    @DisplayName("assignLoginId: 앞뒤 공백은 제거한다")
    void assignLoginId_stripsWhitespace() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        user.assignLoginId("  reader7  ");

        assertThat(user.getLoginId()).isEqualTo("reader7");
    }

    @Test
    @DisplayName("assignLoginId: 3자 미만이면 예외")
    void assignLoginId_tooShort_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.assignLoginId("ab"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignLoginId: 20자 초과면 예외")
    void assignLoginId_tooLong_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.assignLoginId("a".repeat(21)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignLoginId: 허용되지 않은 문자(하이픈/점/공백/한글)면 예외")
    void assignLoginId_invalidChars_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.assignLoginId("goo-spel")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.assignLoginId("goo.spel")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.assignLoginId("goo spel")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.assignLoginId("구스펠")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignLoginId: 예약어(admin 등)는 대소문자 무관하게 예외")
    void assignLoginId_reserved_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.assignLoginId("admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.assignLoginId("ADMIN")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.assignLoginId("root")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignLoginId: null/공백이면 예외")
    void assignLoginId_blank_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.assignLoginId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.assignLoginId("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignLoginId: 한번 정해지면 영원히 불변 — 재설정 시도는 IllegalStateException")
    void assignLoginId_immutable_onceSet() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        user.assignLoginId("goospel_01");

        // 다른 값으로도, 같은 값으로도 재설정 불가 — 공개 핸들은 영구 식별자다.
        assertThatThrownBy(() -> user.assignLoginId("goospel_02"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> user.assignLoginId("goospel_01"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getLoginId()).isEqualTo("goospel_01"); // 원래 값 유지
    }

    // ── 토스 신원 연결 (앱인토스 미니앱 — 설계 §2.2) ─────────────────────────

    @Test
    @DisplayName("linkTossUserKey: 처음 연결하면 userKey가 붙는다 (기본값은 null = 미연결)")
    void linkTossUserKey_firstTime_sets() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        assertThat(user.getTossUserKey()).isNull();

        user.linkTossUserKey("toss-user-key-1");

        assertThat(user.getTossUserKey()).isEqualTo("toss-user-key-1");
    }

    @Test
    @DisplayName("linkTossUserKey: 이미 연결된 계정에 다시 연결하면 IllegalStateException (once-set 불변)")
    void linkTossUserKey_immutable_onceSet() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        user.linkTossUserKey("toss-user-key-1");

        // 다른 값으로도, 같은 값으로도 재연결 불가 — 한 계정에 토스 신원은 하나다(assignLoginId와 같은 정신).
        assertThatThrownBy(() -> user.linkTossUserKey("toss-user-key-2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> user.linkTossUserKey("toss-user-key-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getTossUserKey()).isEqualTo("toss-user-key-1"); // 원래 값 유지
    }

    @Test
    @DisplayName("linkTossUserKey: null/공백은 거부한다 (빈 신원으로 연결 표시가 켜지지 않게)")
    void linkTossUserKey_blank_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThatThrownBy(() -> user.linkTossUserKey(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.linkTossUserKey("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getTossUserKey()).isNull();
    }

    // --- changeLoginId: 아이디 평생 1회 변경 (옛 아이디는 previous_login_id로 영구 잠금) ---

    private User userWithHandle(String loginId) {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        user.assignLoginId(loginId);
        return user;
    }

    @Test
    @DisplayName("changeLoginId: 새 아이디로 바꾸고 옛 아이디를 previousLoginId에 보존한다")
    void changeLoginId_movesOldHandleToPrevious() {
        User user = userWithHandle("oldhandle");

        user.changeLoginId("newhandle");

        assertThat(user.getLoginId()).isEqualTo("newhandle");
        assertThat(user.getPreviousLoginId()).isEqualTo("oldhandle");
    }

    @Test
    @DisplayName("changeLoginId: 대문자·공백 입력도 정규화해 저장한다(normalizeLoginId 단일 출처 재사용)")
    void changeLoginId_normalizesInput() {
        User user = userWithHandle("oldhandle");

        user.changeLoginId("  NewID  ");

        assertThat(user.getLoginId()).isEqualTo("newid");
    }

    @Test
    @DisplayName("changeLoginId: login_id가 아직 없는 계정(OAuth 온보딩 전)은 변경할 수 없다 — ISE")
    void changeLoginId_beforeAssigned_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER); // login_id=null (null-state 경계)

        assertThatThrownBy(() -> user.changeLoginId("newhandle"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getLoginId()).isNull();
        assertThat(user.getPreviousLoginId()).isNull();
    }

    @Test
    @DisplayName("changeLoginId: 평생 1회 — 이미 바꿨으면 다른 값으로도, 옛 아이디로 되돌리기도 ISE")
    void changeLoginId_alreadyUsed_throws() {
        User user = userWithHandle("oldhandle");
        user.changeLoginId("newhandle");

        assertThatThrownBy(() -> user.changeLoginId("thirdhandle"))
                .isInstanceOf(IllegalStateException.class);
        // 되돌리기 특례 없음 — 열어주면 A↔B 왕복(핸들 세탁)으로 1회 제한이 무의미해진다.
        assertThatThrownBy(() -> user.changeLoginId("oldhandle"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getLoginId()).isEqualTo("newhandle");
        assertThat(user.getPreviousLoginId()).isEqualTo("oldhandle");
    }

    @Test
    @DisplayName("changeLoginId: 형식 위반·예약어는 IAE — 상태는 그대로(변경권도 소진되지 않는다)")
    void changeLoginId_invalidFormat_throws() {
        User user = userWithHandle("oldhandle");

        assertThatThrownBy(() -> user.changeLoginId("ab")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changeLoginId("new-handle")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changeLoginId("admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changeLoginId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getLoginId()).isEqualTo("oldhandle");
        assertThat(user.getPreviousLoginId()).isNull();
    }

    @Test
    @DisplayName("changeLoginId: 현재 아이디와 같으면(대소문자만 다른 입력 포함) IAE — 상태 불변")
    void changeLoginId_sameAsCurrent_throws() {
        User user = userWithHandle("oldhandle");

        assertThatThrownBy(() -> user.changeLoginId("oldhandle")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.changeLoginId("OldHandle")).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getLoginId()).isEqualTo("oldhandle");
        assertThat(user.getPreviousLoginId()).isNull();
    }

    /**
     * 공부 하루 목표 — 독서 목표({@link com.booktimer.timer.ReadingTimer#updateSettings})와 <b>같은 규칙</b>이다:
     * 0은 「목표 없음」으로 허용하고 음수만 거부한다. 두 모드가 다른 규칙을 가지면 그건 결정이 아니라 표류다.
     */
    @Test
    @DisplayName("updateStudyDailyGoal: 기본은 0(목표 없음)이고 값을 그대로 반영한다")
    void updateStudyDailyGoal_setsValue() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);

        assertThat(user.getStudyDailyGoalSeconds()).isZero();

        user.updateStudyDailyGoal(3600);
        assertThat(user.getStudyDailyGoalSeconds()).isEqualTo(3600);

        // 0 = 목표 지우기 — 허용해야 「목표 없음」으로 되돌아갈 길이 있다.
        user.updateStudyDailyGoal(0);
        assertThat(user.getStudyDailyGoalSeconds()).isZero();
    }

    @Test
    @DisplayName("updateStudyDailyGoal: 음수는 IAE — 값은 그대로다")
    void updateStudyDailyGoal_negative_throws() {
        User user = User.of(EMAIL, HASH, NICK, TZ, Role.USER);
        user.updateStudyDailyGoal(1800);

        assertThatThrownBy(() -> user.updateStudyDailyGoal(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getStudyDailyGoalSeconds()).isEqualTo(1800);
    }
}
