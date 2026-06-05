package com.booktimer.quote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 격언 랜덤 선택 순수 로직 단위 테스트 (DB 무관).
 *
 * <p>랜덤은 비결정적이라 그대로 두면 테스트가 어렵다 — {@code Clock} 주입(N-010)과 같은 결로
 * {@link Random}을 주입받아, 가짜 Random으로 "어느 인덱스를 고르는가"를 결정적으로 검증한다.
 */
class QuotePickerTest {

    @Test
    @DisplayName("pick: 주입된 Random이 고른 인덱스의 격언을 반환한다")
    void pick_returnsQuoteAtIndexChosenByRandom() {
        Quote q0 = Quote.of("첫 문장", "작가0");
        Quote q1 = Quote.of("둘째 문장", "작가1");
        Quote q2 = Quote.of("셋째 문장", "작가2");

        Random alwaysSecond = new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        };

        assertThat(QuotePicker.pick(List.of(q0, q1, q2), alwaysSecond)).isSameAs(q1);
    }

    @Test
    @DisplayName("pick: 격언이 하나뿐이면 그 하나를 반환한다 (nextInt(1)=0)")
    void pick_singleQuote_returnsIt() {
        Quote only = Quote.of("유일한 문장", "유일 작가");

        assertThat(QuotePicker.pick(List.of(only), new Random())).isSameAs(only);
    }
}
