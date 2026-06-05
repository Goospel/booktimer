package com.booktimer.quote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 작가 격언 서비스 단위 테스트 — 큐레이션 목록 적재 + 랜덤 선택.
 *
 * <p>대시보드 인사말 자리에 페이지 로드마다 랜덤 격언을 띄우는 기능의 핵심부.
 * 랜덤은 비결정적이라 {@code Clock} 주입(N-010)과 같은 이음새로 {@link Random}을 주입받아
 * 테스트에서 선택을 결정적으로 검증한다.
 */
class QuoteServiceTest {

    @Test
    @DisplayName("기본 생성자는 classpath의 큐레이션 격언 목록을 적재한다 — 비어있지 않고 모든 항목에 문장·작가가 있다")
    void loadsCuratedQuotes() {
        QuoteService service = new QuoteService();

        List<Quote> all = service.all();

        assertThat(all).hasSizeGreaterThanOrEqualTo(10);
        assertThat(all).allSatisfy(q -> {
            assertThat(q.text()).isNotBlank();
            assertThat(q.author()).isNotBlank();
        });
    }

    @Test
    @DisplayName("random()은 주입된 Random이 고른 인덱스의 격언을 반환한다 (결정적 검증)")
    void random_picksQuoteChosenByInjectedRandom() {
        Quote q0 = new Quote("첫 문장", "작가0");
        Quote q1 = new Quote("둘째 문장", "작가1");
        Quote q2 = new Quote("셋째 문장", "작가2");

        // 항상 인덱스 1을 고르는 가짜 Random — 선택 로직을 결정적으로 고정한다.
        Random alwaysSecond = new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        };

        QuoteService service = new QuoteService(List.of(q0, q1, q2), alwaysSecond);

        assertThat(service.random()).isEqualTo(q1);
    }

    @Test
    @DisplayName("random()은 항상 목록 안의 격언을 반환한다 (실제 시드 Random으로 반복 호출)")
    void random_alwaysReturnsQuoteWithinList() {
        QuoteService service = new QuoteService();
        List<Quote> all = service.all();

        for (int i = 0; i < 50; i++) {
            assertThat(all).contains(service.random());
        }
    }
}
