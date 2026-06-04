package com.booktimer.book;

/**
 * 책의 공개 범위. 사용자가 책장에서 책마다 켜고 끈다(SNS에서 남에게 보일지).
 *
 * <p>기본은 {@link #PRIVATE} — 공개는 사용자가 명시적으로 켜는 opt-in이다(갑작스런 공개 사고 방지).
 * 1차는 PRIVATE/PUBLIC 2-state. "팔로워에게만"(FOLLOWERS)이 필요해지면 값 추가로 확장(varchar 저장).
 * 설계: claude-docs/sns-design.md §3.1.
 */
public enum BookVisibility {

    PRIVATE("비공개"),
    PUBLIC("공개");

    private final String label;

    BookVisibility(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
