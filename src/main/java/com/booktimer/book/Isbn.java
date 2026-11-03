package com.booktimer.book;

/**
 * 책 동일성 키(isbn13)의 정규화. 적재 시점에 표기를 한 형태로 모은다.
 *
 * <p>isbn13은 인기 카운트의 <b>group by 키</b>다({@link BookRepository#followScopePopularity}).
 * 표기가 갈리면(하이픈 유무·공백) 같은 책이 쪼개져 카운트가 부정확해지고, 빈 값을 {@code ""}로
 * 저장하면 ISBN 없는 서로 다른 책들이 {@code ""} 하나로 뭉쳐 "같은 책"으로 집계된다.
 *
 * <p>그래서 ① 하이픈·공백을 제거해 숫자열로 모으고, ② 알맹이가 없으면(빈 값/구분자뿐) {@code null}로
 * 통일한다. {@code null} 키는 인기 카운트에서 자연 제외된다({@code isbn13 in :isbns}에 NULL 미매칭 +
 * 서비스가 blank 키 선제외). ISBN-10 → 13 변환은 {@link #toIsbn13}(카카오가 10자리만 줄 때 — REQ-381).
 * 개정판/세트 동일성은 별도 작업(plan.md 후속).
 */
public final class Isbn {

    private Isbn() {
        // 정적 유틸 — 인스턴스화 금지
    }

    /**
     * isbn13 원시값을 정규화한다.
     *
     * @param raw 알라딘 응답 또는 수동 입력의 원시 isbn13(널 허용)
     * @return 하이픈·공백을 제거한 값. 알맹이가 없으면(널/빈 값/구분자뿐) {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.replaceAll("[\\s-]", "");
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * ISBN-10을 978 ISBN-13으로 바꾼다 — 앞 9자리에 978을 붙이고 체크 숫자(가중치 1·3)를 다시 계산한다.
     *
     * @param isbn10 10자리(마지막은 숫자 또는 X)
     * @return ISBN-13. 10자리 꼴이 아니면 {@code null}
     */
    public static String toIsbn13(String isbn10) {
        if (isbn10 == null || !isbn10.matches("\\d{9}[\\dXx]")) {
            return null;
        }
        String body = "978" + isbn10.substring(0, 9);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return body + ((10 - sum % 10) % 10);
    }
}
