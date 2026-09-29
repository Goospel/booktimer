package com.booktimer.testsupport;

import com.booktimer.security.RateLimitService;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트용 가변 시계 — {@link #set} 전에는 시스템 UTC 시계({@code TimeConfig}의 {@code Clock.systemUTC()}와 같다)를 따른다.
 * 메신저 세계({@link MessengerTest})에서 {@code @Primary Clock}으로 쓰이고, 매 테스트가 끝나면 {@link ResetExtension}이
 * {@link #reset}한다 — 공유 컨텍스트라 앞 테스트가 고정한 시각이 다음 클래스로 새면 날짜 계산이 조용히 틀어진다.
 * <p>전진이 필요한 테스트(자정을 넘겨 「다음 날 다시 발송」 등)용이다 — {@code Clock.fixed}는 못 움직인다.
 */
public final class MutableClock extends Clock {

    private volatile Instant pinned; // null이면 시스템 시계

    public void set(Instant instant) {
        this.pinned = instant;
    }

    public void reset() {
        this.pinned = null;
    }

    @Override
    public Instant instant() {
        Instant p = pinned;
        return p != null ? p : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /** 옛 테스트 4벌과 같이 자기 자신을 돌려준다 — 운영 코드는 {@code Clock.withZone}을 부르지 않는다. */
    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    /** {@code @TestConfiguration}이라 컴포넌트 스캔에서 빠진다(TestTypeExcludeFilter) — {@code @Import}로만 들어온다. */
    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    /**
     * 매 테스트 뒤 메신저 세계({@link MessengerTest})의 시간 의존 상태를 되돌린다 — 그 애너테이션이 {@code @ExtendWith}로 단다.
     * <ul>
     *   <li>가변 시계 → 시스템 시계.</li>
     *   <li>{@link RateLimitService}의 창 → 비움. 창은 만료를 바로 이 시계로 재서 시계만 되돌리면 끝나지 않는다
     *       (같은 과거 시각으로 고정하는 두 클래스끼리, 또는 시스템 시각에 만든 창을 과거 시각에서 볼 때). 키가 사용자 id라,
     *       그 사이 다른 세계가 떠 공유 H2를 다시 만들면(id가 1부터 다시 나온다) 뒤 클래스 사용자가 앞 클래스의 꽉 찬 창을
     *       물려받아 첫 호출부터 429가 난다.</li>
     * </ul>
     * 그래서 이 세계의 클래스는 다른 세계처럼 {@code @BeforeEach}에서 {@code clearForTest()}를 부르지 않는다.
     */
    public static final class ResetExtension implements AfterEachCallback {
        @Override
        public void afterEach(ExtensionContext context) {
            ApplicationContext spring = SpringExtension.getApplicationContext(context);
            spring.getBean(MutableClock.class).reset();
            spring.getBean(RateLimitService.class).clearForTest();
        }
    }
}
