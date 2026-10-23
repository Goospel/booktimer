package com.booktimer.writing;

import com.booktimer.writing.ClaudeWritingAssistant.Usage;

import java.util.Map;

/**
 * 글쓰기 AI 단가(v1 REQ-23 · §4.5) — 순수. 정수 연산은 nano-USD/토큰으로 하고 합계를 micro-USD로 올림한다.
 *
 * <p>단가 출처: claude-api 스킬 가격표(2026-10-06 캐시 — 설계의 2026-10-17 표와 Sonnet 5.5 캐시 읽기만 다르다: $0.20).
 * 캐시 쓰기는 1시간 TTL 단가(입력 2×)로 센다 — {@link Usage}가 TTL을 나누지 않고, v1은 캐시 표시가 없어 이 열은 0이어야 한다
 * (0이 아니면 보수적으로 센다).
 */
final class AiPrice {

    /** nano-USD/토큰: 입력 · 캐시 쓰기(1시간) · 캐시 읽기 · 출력. */
    private record Rate(long input, long cacheWrite, long cacheRead, long output) {
    }

    // ponytail: Haiku 5.5는 프롬프트 10만 토큰 이하 단가다(넘으면 5배). 입력 상한(재료 24,000자 + 본문 10,000자 + 질문 기록 +
    // 시스템 ≈ 4.1만 토큰)상 닿지 않는다 — 상한을 올리면 여기에 분기를 넣는다.
    private static final Map<String, Rate> RATES = Map.of(
            "claude-sonnet-5-5", new Rate(2_000, 4_000, 200, 10_000),
            "claude-sonnet-5", new Rate(2_000, 4_000, 200, 10_000),
            "claude-haiku-5-5", new Rate(100, 200, 10, 500),
            "claude-opus-5-5", new Rate(4_000, 8_000, 200, 20_000));

    /** 모르는 모델 = 가격표 최고가(Fable 5.1) — 계량기가 덜 세는 쪽으로 틀리지 않게. */
    private static final Rate MOST_EXPENSIVE = new Rate(10_000, 20_000, 250, 50_000);

    private AiPrice() {
    }

    /** 응답을 받은 호출의 실제 원가. */
    static long microUsd(String model, Usage u) {
        Rate r = rate(model);
        return toMicro(u.input() * r.input() + u.cacheWrite() * r.cacheWrite()
                + u.cacheRead() * r.cacheRead() + u.output() * r.output());
    }

    /**
     * 응답을 못 받은 호출(NO_RESPONSE)의 보수 추정치 — 입력 1자 = 1토큰 + 출력 {@code maxTokens} 전부.
     *
     * @param inputChars 시스템 + user 블록 글자 수 합(보낸 그대로)
     */
    static long estimateMicroUsd(String model, int inputChars, int maxTokens) {
        Rate r = rate(model);
        return toMicro((long) inputChars * r.input() + (long) maxTokens * r.output());
    }

    private static Rate rate(String model) {
        return model == null ? MOST_EXPENSIVE : RATES.getOrDefault(model, MOST_EXPENSIVE);
    }

    private static long toMicro(long nano) {
        return Math.ceilDiv(nano, 1_000L);
    }
}
