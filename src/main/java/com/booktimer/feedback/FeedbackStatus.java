package com.booktimer.feedback;

/**
 * 문의/피드백 처리 상태 — 개발자가 진행시키고, 그 표시는 <b>작성자 본인만</b> 조회한다.
 *
 * <p>{@link #SUBMITTED}(미확인) → {@link #READ}(개발자 읽음) → {@link #RESOLVED}(처리완료)로 진행한다.
 * 개발자는 읽음을 건너뛰고 바로 처리완료할 수 있으나, 처리완료를 읽음으로 되돌리지는 않는다(단조 진행).
 */
public enum FeedbackStatus {

    SUBMITTED("미확인"),
    READ("읽음"),
    RESOLVED("처리완료");

    private final String label;

    FeedbackStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
