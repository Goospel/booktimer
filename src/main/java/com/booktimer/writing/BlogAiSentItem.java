package com.booktimer.writing;

/**
 * 블로그 글쓰기 AI가 Anthropic으로 보내는 것의 단일 목록(책생글 K2 REQ-202) — 처리방침 6-5절이 {@link WritingPrivacyGuard}로 따라온다.
 * 새 데이터를 보내려면 여기부터 더한다. 문서는 C12로 이 목록보다 넓게 적혀 있어도 된다(가드는 코드 ⊆ 문서만 본다).
 *
 * <p>K2는 라벨과 가드만 둔다 — 실제로 보내는 블로그 어댑터는 B3가 이 목록을 쓴다.
 */
public enum BlogAiSentItem {
    DRAFT("올리기 전 블로그 글(제목·본문)"),
    CLAIM("출처를 찾을 문장과 글 제목"),
    AUTHOR_URL("글쓴이가 넣은 출처 주소"),
    SOURCE_PAGE("확인할 출처 페이지의 내용");

    public final String privacyLabel;

    BlogAiSentItem(String privacyLabel) {
        this.privacyLabel = privacyLabel;
    }
}
