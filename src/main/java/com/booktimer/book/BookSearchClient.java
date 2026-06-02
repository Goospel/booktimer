package com.booktimer.book;

import java.util.List;

/**
 * 도서 검색 제공자 추상화(포트). 구현(어댑터)은 알라딘 OpenAPI 등 외부 서비스를 호출한다.
 *
 * <p>이 포트 덕분에 서비스/컨트롤러는 외부 API·키 없이도 가짜 구현으로 테스트된다. 또 검색
 * 제공자를 교체(네이버/카카오)하거나 제휴 링크 정책을 바꿔도 호출부가 영향받지 않는다.
 */
public interface BookSearchClient {

    /**
     * 검색 활성 여부. API 키가 설정되지 않았으면 false — 화면은 검색 대신 수동 입력으로 폴백한다.
     */
    boolean isEnabled();

    /**
     * 질의어로 도서를 검색한다. 비활성이거나 결과가 없으면 빈 목록.
     *
     * @param query 검색어(제목/저자 등)
     */
    List<BookSearchResult> search(String query);
}
