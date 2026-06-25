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

    // ── pickList: 슬롯머신 로테이션용 셔플 N개 ────────────────────────────────────

    @Test
    @DisplayName("pickList: max개만 반환하고 모두 입력에서 온 서로 다른 격언이다")
    void pickList_returnsAtMostMax_subsetNoDupes() {
        Quote q0 = Quote.of("문장0", "작가0");
        Quote q1 = Quote.of("문장1", "작가1");
        Quote q2 = Quote.of("문장2", "작가2");

        List<Quote> result = QuotePicker.pickList(List.of(q0, q1, q2), new Random(1), 2);

        assertThat(result).hasSize(2);
        assertThat(result).isSubsetOf(List.of(q0, q1, q2));
        assertThat(result).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("pickList: max가 개수보다 크면 전체를 반환한다(셔플된 순서)")
    void pickList_fewerThanMax_returnsAll() {
        Quote q0 = Quote.of("문장0", "작가0");
        Quote q1 = Quote.of("문장1", "작가1");

        List<Quote> result = QuotePicker.pickList(List.of(q0, q1), new Random(1), 5);

        assertThat(result).containsExactlyInAnyOrder(q0, q1);
    }

    @Test
    @DisplayName("pickList: 같은 시드 Random이면 같은 결과(셔플 사용·결정적)")
    void pickList_deterministicForSameSeed() {
        List<Quote> input = List.of(
                Quote.of("a", "A"), Quote.of("b", "B"), Quote.of("c", "C"), Quote.of("d", "D"));

        List<Quote> r1 = QuotePicker.pickList(input, new Random(42), 3);
        List<Quote> r2 = QuotePicker.pickList(input, new Random(42), 3);

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    @DisplayName("pickList: 빈 목록 → 빈 목록(폴백은 호출부가)")
    void pickList_empty_returnsEmpty() {
        assertThat(QuotePicker.pickList(List.of(), new Random(), 5)).isEmpty();
    }

    @Test
    @DisplayName("pickList: max<=0 → 빈 목록(가드)")
    void pickList_nonPositiveMax_returnsEmpty() {
        List<Quote> input = List.of(Quote.of("a", "A"), Quote.of("b", "B"));
        assertThat(QuotePicker.pickList(input, new Random(), 0)).isEmpty();
    }
}
