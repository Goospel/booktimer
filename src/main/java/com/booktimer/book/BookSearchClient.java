package com.booktimer.book;

import java.util.List;
import java.util.Optional;

/**
 * 도서 검색 제공자 추상화(포트). 지금 구현 = {@link KakaoBookSearchClient}(2026-11-03 알라딘 OpenAPI 종료로 교체 — T-278).
 *
 * <p>이 포트 덕분에 서비스/컨트롤러는 외부 API·키 없이도 가짜 구현으로 테스트된다. 또 검색
 * 제공자를 교체(네이버/카카오)하거나 제휴 링크 정책을 바꿔도 호출부가 영향받지 않는다.
 */
public interface BookSearchClient {

    /** 검색 결과 단일 배치 상한(= 카카오 size, ≤50). 페이저 폐지(스크롤 전환)로 "한 번에 보여줄 최대 수". */
    int PAGE_SIZE = 40;

    /**
     * 검색 활성 여부. API 키가 설정되지 않았으면 false — 화면은 검색 대신 수동 입력으로 폴백한다.
     */
    boolean isEnabled();

    /**
     * 질의어로 도서를 한 페이지 검색한다. 결과가 없으면 빈 페이지.
     *
     * <p>장애 · 키 없음 · 이상한 응답이면 {@link BookSearchUnavailableException} — 0건으로 삼키지 않는다(T-278).
     *
     * @param query 검색어
     * @param type  검색 기준(제목/저자) — 제공자의 검색 타입으로 매핑된다
     * @param page  1-based 페이지 번호
     */
    BookSearchPage search(String query, BookSearchType type, int page);

    /**
     * 지금 많이 읽히는 책 한 묶음 — 「책 추가」 화면 추천의 <b>콜드스타트</b>용.
     *
     * <p>{@link #search}가 "이 사람이 읽은 저자"를 근거로 삼는 것과 달리 여기엔 근거가 없다. 서재가 비었거나
     * 저자를 못 고른 사람에게도 화면이 비지 않게 하는 것이 유일한 목적이라, 질의어를 받지 않는다.
     *
     * <p>있으면 좋은 것이라 못 주면 빈 목록이고 던지지 않는다 — 호출자는 추천을 포기하고 화면은 카드를 안 그린다.
     * 카카오엔 목록이 없어 지금은 늘 빈 목록이다(REQ-385).
     */
    List<BookSearchResult> bestsellers();

    /**
     * ISBN-13으로 책 한 건을 조회한다 — 앱 서재의 「바코드 추가」용.
     *
     * <p>isbn 공백 · 결과 없음이면 빈 {@link Optional}. 장애 · 키 없음이면 {@link BookSearchUnavailableException}.
     *
     * @param isbn13 조회할 ISBN-13
     */
    Optional<BookSearchResult> lookupByIsbn(String isbn13);
}
