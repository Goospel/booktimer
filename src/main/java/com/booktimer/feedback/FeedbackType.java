package com.booktimer.feedback;

/**
 * 문의/피드백 유형 — 화면 셀렉트에 label로 노출하고, DB에는 STRING으로 저장한다(ReportReason 관례).
 */
public enum FeedbackType {

    BUG("버그"),
    SUGGESTION("제안"),
    ETC("기타");

    private final String label;

    FeedbackType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 폼에서 넘어온 값을 안전하게 파싱한다 — null/빈/잘못된 값은 {@link #ETC}로 폴백(절대 예외/null 아님).
     * 유형은 보조 분류일 뿐이라 입력 실수로 문의 접수가 막히지 않게 한다(ReportReason.from과 동일 정책).
     */
    public static FeedbackType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ETC;
        }
        try {
            return FeedbackType.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ETC;
        }
    }
}
