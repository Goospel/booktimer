package com.booktimer.security;

import java.time.Duration;

/**
 * 레이트리밋 대상 액션과 액션별 한도(고정 윈도우) — 스팸 팔로우·검색 크롤링·신고 남용 방어 (sns-design §7.5·§9).
 *
 * <p>한도는 "정상 사용자가 한 윈도우에 이만큼 쓸 일은 없다" 수준으로 넉넉히 잡아 일반 사용은 막지 않고
 * 자동화/스팸만 거른다. 검색은 열거·크롤링 완화가 목적이라 분당으로, 신고는 빈도가 낮아 시간당으로 둔다.
 */
public enum RateLimitAction {

    FOLLOW(30, Duration.ofMinutes(1)),
    SEARCH(20, Duration.ofMinutes(1)),
    RECOMMEND(20, Duration.ofMinutes(1)),
    REPORT(10, Duration.ofHours(1));

    private final int limit;
    private final Duration window;

    RateLimitAction(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    /** 한 윈도우에 허용하는 호출 수. */
    public int limit() {
        return limit;
    }

    /** 카운터가 리셋되는 윈도우 길이. */
    public Duration window() {
        return window;
    }
}
