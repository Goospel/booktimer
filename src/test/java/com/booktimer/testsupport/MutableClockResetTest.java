package com.booktimer.testsupport;

import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메신저 세계의 리셋 확장이 매 테스트 뒤 공유 가변 시계를 시스템 시계로 되돌리고(REQ-06) 레이트리밋 창을 비우는지(REQ-09).
 * 메신저 세계를 그대로 써서 컨텍스트를 늘리지 않는다.
 *
 * <p>이 테스트가 단독으로 잡는 실패: ① 리셋 확장이 빠지거나 {@code @ExtendWith}가 {@link MessengerTest}에서 떨어져
 * 나간 것 — 그러면 시각을 안 고치는 클래스(운영 알림·완독 축하)가 앞 클래스의 과거 시각으로 조용히 돈다.
 * ② 확장이 {@link RateLimitService} 창을 안 비우는 것 — 그러면 같은 과거 시각으로 고정하는 채팅 두 클래스
 * ({@code ChatRoomServiceTest}·{@code ChatPushAfterCommitTest}) 사이에 다른 세계가 떠 사용자 id가 겹칠 때 첫
 * {@code openOrGet}·{@code send}가 429로 깨진다. 기본 클래스 순서에선 채팅 구간에 새 로드가 없어 안 드러난다.
 */
@MessengerTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MutableClockResetTest {

    private static final Instant PINNED = Instant.parse("2000-01-01T00:00:00Z");

    @Autowired MutableClock clock;

    private static final long USER_ID = 42L;

    @Autowired RateLimitService rateLimitService;

    @Test
    @Order(1)
    @DisplayName("REQ-06 · (준비) 앞 테스트가 시계를 2000-01-01로 고정한다")
    void pinsClock() {
        clock.set(PINNED);
        assertThat(clock.instant()).isEqualTo(PINNED);
    }

    @Test
    @Order(2)
    @DisplayName("REQ-06 · 앞 테스트가 고정한 시계가 다음 테스트로 새지 않는다")
    void nextTestSeesSystemTime() {
        assertThat(Duration.between(clock.instant(), Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @Order(3)
    @DisplayName("REQ-09 · (준비) 앞 테스트가 과거 시각에 한 사용자의 레이트리밋 창을 꽉 채운다")
    void fillsRateLimitWindow() {
        clock.set(PINNED);
        for (int i = 0; i < RateLimitAction.CHAT_ROOM_OPEN.limit(); i++) {
            rateLimitService.allow(RateLimitAction.CHAT_ROOM_OPEN, USER_ID);
        }
        assertThat(rateLimitService.allow(RateLimitAction.CHAT_ROOM_OPEN, USER_ID)).isFalse(); // 양성 대조 — 창이 실제로 찼다
    }

    @Test
    @Order(4)
    @DisplayName("REQ-09 · 앞 테스트가 채운 레이트리밋 창이 다음 테스트로 새지 않는다")
    void nextTestStartsWithEmptyRateLimitWindow() {
        clock.set(PINNED); // 같은 시각 — 「시간이 흘러 창이 끝났다」는 길을 막는다. true는 창이 비워졌을 때만 나온다
        assertThat(rateLimitService.allow(RateLimitAction.CHAT_ROOM_OPEN, USER_ID))
                .as("앞 테스트가 채운 창이 비워져 있어야 한다")
                .isTrue();
    }
}
