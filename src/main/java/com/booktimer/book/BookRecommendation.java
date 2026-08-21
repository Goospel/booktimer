package com.booktimer.book;

import java.util.List;

/**
 * 추천 한 묶음 — <b>어느 전략으로 골랐는지는 서버가 정하고, 화면은 라벨을 그대로 그린다.</b>
 *
 * <p>그래서 화면은 바보로 둘 수 있다: 「저자 추천인지 베스트셀러인지」를 알 필요가 없고, 전략이 늘어도
 * 클라이언트는 안 바뀐다. 뽑을 것이 없으면 {@link #empty()} — 화면은 카드를 아예 안 그린다.
 *
 * @param title 카드 제목(예 "김호연의 다른 책" · "요즘 많이 읽는 책"). 추천이 없으면 null
 * @param reason 한 줄 근거(예 "『불편한 편의점』을 읽으셨네요"). 없으면 null
 * @param books  추천 책들. 호출부가 「이미 내 서재에 있는 책」을 걸러 낸 뒤의 목록
 */
public record BookRecommendation(String title, String reason, List<BookSearchResult> books) {

    public static BookRecommendation empty() {
        return new BookRecommendation(null, null, List.of());
    }
}
