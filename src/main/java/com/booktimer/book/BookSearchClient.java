package com.booktimer.book;

import java.util.Optional;

/**
 * 도서 검색 제공자 추상화(포트). 구현(어댑터)은 알라딘 OpenAPI 등 외부 서비스를 호출한다.
 *
 * <p>이 포트 덕분에 서비스/컨트롤러는 외부 API·키 없이도 가짜 구현으로 테스트된다. 또 검색
 * 제공자를 교체(네이버/카카오)하거나 제휴 링크 정책을 바꿔도 호출부가 영향받지 않는다.
 */
public interface BookSearchClient {

    /** 검색 결과 단일 배치 상한(= 알라딘 MaxResults, ≤100). 페이저 폐지(스크롤 전환)로 "한 번에 보여줄 최대 수". */
    int PAGE_SIZE = 40;

    /**
     * 검색 활성 여부. API 키가 설정되지 않았으면 false — 화면은 검색 대신 수동 입력으로 폴백한다.
     */
    boolean isEnabled();

    /**
     * 질의어로 도서를 한 페이지 검색한다. 비활성이거나 결과가 없으면 빈 페이지.
     *
     * @param query 검색어
     * @param type  검색 기준(제목/저자) — 제공자의 검색 타입으로 매핑된다
     * @param page  1-based 페이지 번호
     */
    BookSearchPage search(String query, BookSearchType type, int page);

    /**
     * ISBN-13으로 책 한 건의 카탈로그 메타(장르·출간일 등)를 조회한다 — 기존 책 백필용(알라딘 ItemLookUp).
     *
     * <p>{@link #search}가 질의어로 여러 건을 찾는 것과 달리, 정확한 ISBN으로 단건을 집어와 책BTI 입력을
     * 채운다. 비활성(키 없음)·isbn 공백·결과 없음·외부 오류면 빈 {@link Optional}(호출자는 그 책을 건너뛴다).
     *
     * @param isbn13 조회할 ISBN-13
     */
    Optional<BookSearchResult> lookupByIsbn(String isbn13);
}
