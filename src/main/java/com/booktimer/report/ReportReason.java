package com.booktimer.report;

/**
 * 사용자 신고 사유 (SNS 5단계, sns-design §7.5). 화면 셀렉트에 label로 노출하고, DB에는 STRING으로 저장한다.
 */
public enum ReportReason {

    SPAM("스팸/광고"),
    HARASSMENT("괴롭힘/욕설"),
    INAPPROPRIATE("부적절한 콘텐츠"),
    OTHER("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 폼에서 넘어온 값을 안전하게 파싱한다 — null/빈/잘못된 값은 {@link #OTHER}로 폴백(절대 예외/ null 아님).
     * 신고가 사유 입력 실수로 막히지 않게 한다(사유는 보조 정보, 신고 행위 자체가 핵심).
     */
    public static ReportReason from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        try {
            return ReportReason.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
