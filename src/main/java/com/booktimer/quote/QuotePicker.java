package com.booktimer.quote;

import java.util.List;
import java.util.Random;

/**
 * 격언 랜덤 선택 순수 로직 (DB·스프링 무관).
 *
 * <p>선택의 비결정성을 한 곳에 가두어 결정적으로 테스트할 수 있게 한다 — {@link Random}을 주입받아
 * 가짜/시드 Random으로 어느 인덱스를 고르는지 검증한다(N-010 Clock 주입과 같은 결).
 */
public final class QuotePicker {

    private QuotePicker() {
    }

    /**
     * 목록에서 격언 하나를 랜덤으로 고른다.
     *
     * @param quotes 비어 있지 않은 격언 목록(빈 목록 처리는 호출부가 한다)
     * @param random 선택에 쓸 난수원
     * @return 고른 격언
     * @throws IllegalArgumentException 목록이 비어 있는 경우
     */
    public static Quote pick(List<Quote> quotes, Random random) {
        if (quotes.isEmpty()) {
            throw new IllegalArgumentException("quotes must not be empty");
        }
        return quotes.get(random.nextInt(quotes.size()));
    }
}
