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
 * RateLimitService 단위 테스트 — 사용자별·액션별 고정 윈도우 호출 제한(스팸 팔로우·검색 크롤링 방어).
 *
 * <p>핵심 규칙: 같은 (액션, userId) 키로 윈도우 안에서 한도까지는 허용(true), 한도를 넘으면 차단(false).
 * 윈도우가 지나면 카운터가 리셋된다. 액션·userId가 다르면 서로 독립이다. 시간 의존을 결정적으로 만들기
 * 위해 가변 {@link Clock}을 주입한다(LoginAttemptService와 같은 방식).
 */
class RateLimitServiceTest {

    private static final long USER = 42L;
    private static final Instant START = Instant.parse("2026-06-04T00:00:00Z");

    private MutableClock clock;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        service = new RateLimitService(clock);
    }

    @Test
    @DisplayName("한도까지는 허용한다(limit번째까지 true)")
    void allowsUpToLimit() {
        int limit = RateLimitAction.FOLLOW.limit();
        for (int i = 0; i < limit; i++) {
            assertThat(service.allow(RateLimitAction.FOLLOW, USER)).isTrue();
        }
    }

    @Test
    @DisplayName("한도를 넘으면 차단한다(limit+1번째 false)")
    void blocksOverLimit() {
        int limit = RateLimitAction.FOLLOW.limit();
        for (int i = 0; i < limit; i++) {
            service.allow(RateLimitAction.FOLLOW, USER);
        }
        assertThat(service.allow(RateLimitAction.FOLLOW, USER)).isFalse();
    }

    @Test
    @DisplayName("윈도우가 지나면 카운터가 리셋되어 다시 허용된다")
    void resetsAfterWindow() {
        int limit = RateLimitAction.FOLLOW.limit();
        for (int i = 0; i < limit; i++) {
            service.allow(RateLimitAction.FOLLOW, USER);
        }
        assertThat(service.allow(RateLimitAction.FOLLOW, USER)).isFalse();

        clock.advance(RateLimitAction.FOLLOW.window().plusSeconds(1));

        assertThat(service.allow(RateLimitAction.FOLLOW, USER)).isTrue();
    }

    @Test
    @DisplayName("액션이 다르면 카운터가 독립이다")
    void differentActions_independent() {
        int searchLimit = RateLimitAction.SEARCH.limit();
        for (int i = 0; i < searchLimit; i++) {
            service.allow(RateLimitAction.SEARCH, USER);
        }
        assertThat(service.allow(RateLimitAction.SEARCH, USER)).isFalse(); // SEARCH 소진
        assertThat(service.allow(RateLimitAction.FOLLOW, USER)).isTrue();  // FOLLOW는 영향 없음
    }

    @Test
    @DisplayName("userId가 다르면 서로 영향을 주지 않는다")
    void differentUsers_independent() {
        int limit = RateLimitAction.FOLLOW.limit();
        for (int i = 0; i < limit; i++) {
            service.allow(RateLimitAction.FOLLOW, USER);
        }
        assertThat(service.allow(RateLimitAction.FOLLOW, USER)).isFalse();
        assertThat(service.allow(RateLimitAction.FOLLOW, 99L)).isTrue(); // 다른 사용자
    }

    /** 테스트용 가변 시계 — advance로 "지금"을 앞당긴다(LoginAttemptServiceTest와 동일). */
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
