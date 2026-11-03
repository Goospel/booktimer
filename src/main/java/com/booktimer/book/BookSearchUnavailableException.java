package com.booktimer.book;

/**
 * 검색 제공자 장애 — 키 없음 · HTTP 오류 · 시간 초과 · 이상한 응답(REQ-383).
 *
 * <p>「결과 없음(0건)」과 다른 신호다. 알라딘 OpenAPI가 끝났을 때 어댑터가 장애를 빈 목록으로 삼켜
 * 4일간 「찾은 책이 없어요」로만 보였다(T-278). 메시지는 로그용이고, 사용자 문구는 컨트롤러가 정한다.
 */
public class BookSearchUnavailableException extends RuntimeException {

    public BookSearchUnavailableException(String message) {
        super(message);
    }

    public BookSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
