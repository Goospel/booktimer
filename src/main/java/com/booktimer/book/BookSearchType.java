package com.booktimer.book;

/**
 * 도서 검색 기준 — 사용자가 "무엇으로" 찾을지 고른다.
 *
 * <p>기존엔 알라딘 {@code QueryType=Keyword}로 제목·저자를 한꺼번에 매칭해, "모기"를 검색하면
 * 저자 이름에 "모기"가 든 책이 먼저 떠 사용자 의도(제목)와 어긋났다. 그래서 기준을 분리한다 —
 * 기본은 제목, 필요하면 저자로 전환한다.
 *
 * <p>각 값은 카카오 책 검색의 {@code target} 파라미터로 매핑된다(저자 = {@code person} — 옮긴이도 맞춘다).
 * 2026-11-03 알라딘 OpenAPI 종료로 교체(T-278).
 */
public enum BookSearchType {

    TITLE("제목", "title"),
    AUTHOR("저자", "person"),
    PUBLISHER("출판사", "publisher");

    private final String label;
    private final String kakaoTarget;

    BookSearchType(String label, String kakaoTarget) {
        this.label = label;
        this.kakaoTarget = kakaoTarget;
    }

    public String getLabel() {
        return label;
    }

    public String getKakaoTarget() {
        return kakaoTarget;
    }

    /**
     * 화면 파라미터를 검색 기준으로 — 없거나 잘못된 값이면 기본(제목)으로 폴백한다.
     * 대소문자는 무시한다(예: {@code author} → AUTHOR).
     */
    public static BookSearchType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return TITLE;
        }
        try {
            return BookSearchType.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TITLE;
        }
    }
}
