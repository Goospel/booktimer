package com.booktimer.writing;

import com.booktimer.writing.ClaudeWritingAssistant.Usage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 단가 함수(v1 REQ-23) — 순수. 월 예산 계량기의 눈금이다.
 *
 * <p>단가는 claude-api 스킬의 현재 가격표(2026-10-06 캐시)를 따른다. Sonnet 5.5 캐시 읽기는 설계(2026-10-17)의 $0.10이 아니라
 * 스킬의 $0.20(0.1×)이다 — 다르면 스킬 쪽 현재 값을 쓴다(PR 본문에 적었다).
 */
class AiPriceTest {

    @Test
    @DisplayName("REQ-23 · Sonnet 5.5 입력 1,000 · 출력 500 토큰은 7,000 micro-USD")
    void sonnetInputOutput() {
        assertThat(AiPrice.microUsd("claude-sonnet-5-5", new Usage(1_000, 0, 0, 500))).isEqualTo(7_000);
    }

    @Test
    @DisplayName("REQ-23 · Sonnet 5.5 캐시 읽기 1,000토큰은 200 micro-USD")
    void sonnetCacheRead() {
        assertThat(AiPrice.microUsd("claude-sonnet-5-5", new Usage(0, 0, 1_000, 0))).isEqualTo(200);
    }

    @Test
    @DisplayName("REQ-23 · Haiku 5.5 입력 1,000 · 출력 500 토큰은 350 micro-USD")
    void haikuInputOutput() {
        assertThat(AiPrice.microUsd("claude-haiku-5-5", new Usage(1_000, 0, 0, 500))).isEqualTo(350);
    }

    @Test
    @DisplayName("REQ-23 · Haiku 5.5 캐시 읽기 1,000토큰은 10 micro-USD")
    void haikuCacheRead() {
        assertThat(AiPrice.microUsd("claude-haiku-5-5", new Usage(0, 0, 1_000, 0))).isEqualTo(10);
    }

    @Test
    @DisplayName("REQ-23 · 캐시 쓰기는 1시간 단가로 센다(Usage가 TTL을 나누지 않는다 — 보수)")
    void cacheWriteAtOneHourRate() {
        assertThat(AiPrice.microUsd("claude-haiku-5-5", new Usage(0, 1_000, 0, 0))).isEqualTo(200);
        assertThat(AiPrice.microUsd("claude-sonnet-5-5", new Usage(0, 1_000, 0, 0))).isEqualTo(4_000);
    }

    @Test
    @DisplayName("REQ-23 · 모르는 모델은 Fable 5.1 단가로 센다")
    void unknownModelIsMostExpensive() {
        assertThat(AiPrice.microUsd("claude-mystery-9", new Usage(1_000, 1_000, 1_000, 1_000)))
                .isEqualTo(10_000 + 20_000 + 250 + 50_000);
        assertThat(AiPrice.microUsd(null, new Usage(1_000, 0, 0, 0))).isEqualTo(10_000);
    }

    @Test
    @DisplayName("REQ-23 · Sonnet 추정치는 글자 수 × 2 + max_tokens × 10 micro-USD")
    void estimate() {
        assertThat(AiPrice.estimateMicroUsd("claude-sonnet-5-5", 1_000, 4_000)).isEqualTo(2_000 + 40_000);
        assertThat(AiPrice.estimateMicroUsd("claude-haiku-5-5", 1_000, 4_000)).isEqualTo(100 + 2_000);
    }

    @Test
    @DisplayName("REQ-23 · nano 합계를 micro로 올림한다(1토큰도 0원이 되지 않는다)")
    void roundsUp() {
        assertThat(AiPrice.microUsd("claude-haiku-5-5", new Usage(1, 0, 0, 0))).isEqualTo(1);
        assertThat(AiPrice.microUsd("claude-haiku-5-5", new Usage(0, 0, 0, 0))).isZero();
    }
}
