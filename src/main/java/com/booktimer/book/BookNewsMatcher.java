package com.booktimer.book;

import java.util.Locale;

/**
 * 책 ↔ 뉴스 기사 매칭 규칙 (정적 순수 함수).
 *
 * <p><b>계약</b>: 기사 제목+본문에 <b>책 제목이 있고 AND 저자명도 있어야</b> 채택한다. 검색 쿼리만으로는
 * 『총, 균, 쇠』처럼 제목이 일반명사 조합인 책이 엉뚱한 기사를 무는데, 그 오탐을 이 AND가 막는다.
 * 재현율(놓침)을 조금 내주고 정밀도를 사는 의도된 트레이드오프다.
 *
 * <p>비교 전에 네이버 응답의 {@code <b>} 강조 태그·HTML 엔티티를 벗기고({@link #clean}), 대소문자와
 * 공백 변형("총, 균, 쇠" ↔ "총,균,쇠")을 흡수한다.
 *
 * <p>ponytail: 번역서 표기 흔들림("재레드/재러드")은 저자 문자열 완전 포함으로는 놓친다 — 알려진 천장.
 * 정확도 불만이 실측되면 성(마지막 토큰) 매칭으로 완화한다.
 */
public final class BookNewsMatcher {

    private BookNewsMatcher() {
    }

    /**
     * 기사가 이 책의 것인지 판정한다 — 제목 AND 저자.
     *
     * <p>책 제목·저자 중 하나라도 비어 있으면 AND를 만들 수 없어 항상 기각한다(수집 대상 선별에서도
     * 같은 이유로 제외되지만, 여기서도 방어한다).
     */
    public static boolean matches(String articleTitle, String articleDescription,
                                  String bookTitle, String bookAuthor) {
        if (isBlank(bookTitle) || isBlank(bookAuthor)) {
            return false;
        }
        String haystack = key(nullToEmpty(articleTitle) + " " + nullToEmpty(articleDescription));
        return haystack.contains(key(bookTitle)) && haystack.contains(key(bookAuthor));
    }

    /**
     * 표시용 정규화 — HTML 태그를 벗기고 엔티티를 해제한 뒤 공백을 하나로 줄인다.
     * 대소문자는 <b>보존</b>한다(저장·노출되는 기사 제목이 이 값이다). null은 null 그대로.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("<[^>]*>", "");
        // &amp;는 마지막에 — 먼저 풀면 "&amp;quot;"가 두 번 해제된다.
        s = s.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return s.replaceAll("\\s+", " ").strip();
    }

    /** 비교용 키 — {@link #clean} + 소문자 + 공백 제거(공백 유무 변형을 흡수). */
    private static String key(String raw) {
        return clean(raw).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
