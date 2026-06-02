package com.booktimer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoginAttemptService 단위 테스트 — 무차별 대입(brute-force) 방어 코어.
 *
 * <p>핵심 규칙: 같은 키(클라이언트 IP)로 인증이 {@link LoginAttemptService#MAX_ATTEMPTS}회
 * 연속 실패하면 {@link LoginAttemptService#LOCKOUT} 동안 잠긴다. 성공하면 카운터가 초기화되고,
 * 잠금 시간이 지나면 자동으로 풀린다. 시간 의존을 결정적으로 만들기 위해 가변 {@link Clock}을 주입한다.
 */
class LoginAttemptServiceTest {

    private static final String KEY = "1.2.3.4";
    private static final Instant START = Instant.parse("2026-06-02T00:00:00Z");

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = new LoginAttemptService(clock);
    }

    private void failTimes(int n) {
        for (int i = 0; i < n; i++) {
            service.recordFailure(KEY);
        }
    }

    @Test
    @DisplayName("임계치 미만(MAX-1회) 실패면 잠기지 않는다")
    void belowThreshold_notBlocked() {
        failTimes(LoginAttemptService.MAX_ATTEMPTS - 1);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("임계치(MAX회) 실패면 잠긴다")
    void atThreshold_blocked() {
        failTimes(LoginAttemptService.MAX_ATTEMPTS);

        assertThat(service.isBlocked(KEY)).isTrue();
    }

    @Test
    @DisplayName("성공하면 실패 카운터가 초기화되어 잠기지 않는다")
    void success_resetsCounter() {
        failTimes(LoginAttemptService.MAX_ATTEMPTS - 1);
        service.recordSuccess(KEY);
        service.recordFailure(KEY);

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("잠금 시간이 지나면 자동으로 풀린다")
    void lockoutExpires() {
        failTimes(LoginAttemptService.MAX_ATTEMPTS);
        assertThat(service.isBlocked(KEY)).isTrue();

        clock.advance(LoginAttemptService.LOCKOUT.plusSeconds(1));

        assertThat(service.isBlocked(KEY)).isFalse();
    }

    @Test
    @DisplayName("키(IP)가 다르면 서로 영향을 주지 않는다")
    void differentKeys_independent() {
        failTimes(LoginAttemptService.MAX_ATTEMPTS);

        assertThat(service.isBlocked(KEY)).isTrue();
        assertThat(service.isBlocked("9.9.9.9")).isFalse();
    }

    @Test
    @DisplayName("아무 실패도 없는 키는 잠기지 않는다")
    void unknownKey_notBlocked() {
        assertThat(service.isBlocked("never@seen")).isFalse();
    }

    /** 테스트용 가변 시계 — {@link #advance(Duration)}로 "지금"을 앞당긴다. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
