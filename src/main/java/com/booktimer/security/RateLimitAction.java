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
    REPORT(10, Duration.ofHours(1)),
    STORY_CREATE(10, Duration.ofHours(1)),

    /**
     * 미니앱 토스 로그인·신규가입({@code /api/toss/login·register}). 정상 사용은 앱 진입당 1~2회라
     * 분당 20이면 넉넉하고, 자동화된 인가코드 대량 시도는 걸린다.
     */
    TOSS_AUTH(20, Duration.ofMinutes(1)),

    /**
     * 미니앱 계정 연결 코드 검증({@code /api/toss/link}). <b>브루트포스 방어의 핵심 층</b>이다 —
     * 연결 코드는 사람이 옮겨 적는 8자라 엔트로피가 낮아, 시도 횟수 상한이 없으면 TTL 5분 안에도
     * 의미 있는 추측이 가능해진다. 정상 사용자는 코드 하나를 한두 번 입력할 뿐이다.
     */
    TOSS_LINK(10, Duration.ofHours(1));

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
