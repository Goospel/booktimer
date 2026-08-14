package com.booktimer.book;

/**
 * 책 뉴스 수집 1회분의 결과 — 관리자 수동 실행({@code POST /admin/books/collect-news})의 보고용.
 *
 * <p>새벽 배치는 로그만 남기면 되지만, 손으로 돌릴 때는 화면에 결과가 보여야 한다.
 * {@code enabled}를 따로 싣는 이유는 킬스위치가 내려간 상태의 "0종 0건"이
 * "완독한 책이 없음"과 구분되지 않기 때문이다 — 관리자가 둘을 헷갈리면 엉뚱한 곳을 뒤진다.
 *
 * @param enabled 킬스위치({@code booktimer.news.enabled})가 켜져 있었는지
 * @param targets 훑은 isbn 종수
 * @param saved   저장한 기사 건수
 */
public record NewsCollectionResult(boolean enabled, int targets, int saved) {

    /** 꺼져 있어 아무것도 하지 않은 결과. */
    static NewsCollectionResult disabled() {
        return new NewsCollectionResult(false, 0, 0);
    }
}
