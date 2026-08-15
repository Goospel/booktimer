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
     * 미니앱 신원 엔드포인트({@code /api/toss/login·register·link})의 <b>클라이언트 IP</b>별 상한.
     *
     * <p>위 {@link #TOSS_AUTH}·{@link #TOSS_LINK}는 토스 {@code userKey}를 키로 쓰는데, 그 키는
     * <b>인가코드를 검증한 뒤에야</b> 나온다 — 즉 인가코드가 쓰레기면 검증이 401로 끝나 그 제한기에
     * 닿지도 못한다. 이 경로는 {@code permitAll}이라, 그대로 두면 미인증 요청 하나하나가 토스로 나가는
     * mTLS 아웃바운드 호출을 <b>무제한</b>으로 유발한다(우리 API 쿼터·요청 스레드 소모). 그래서 검증
     * <b>앞</b>에서 IP로 한 겹 더 센다 — 신원을 몰라도 셀 수 있는 유일한 키다.
     *
     * <p><b>한도를 분당 60으로 넉넉히 잡은 이유</b>: 국내 이동통신은 CGNAT라 다수 사용자가 한 공인 IP를
     * 공유한다. 촘촘하게 잡으면 공격자가 아니라 같은 NAT 뒤의 정상 사용자가 로그인을 못 한다. 목적은
     * 정밀 차단이 아니라 <b>폭주의 상한</b>이고, 고정 윈도우라 1분이면 저절로 풀린다.
     *
     * <p>분산 출처에는 약하다 — {@link RateLimitService} 클래스 주석의 인메모리 한계와 같다.
     */
    TOSS_VERIFY(60, Duration.ofMinutes(1)),

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
