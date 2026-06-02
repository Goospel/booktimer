package com.booktimer.book;

/**
 * 책 읽기 상태. 사용자가 책장에서 단계별로 바꾼다(읽고싶음 → 읽는중 → 완독).
 */
public enum BookStatus {

    WANT_TO_READ("읽고 싶음"),
    READING("읽는 중"),
    FINISHED("완독");

    private final String label;

    BookStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
