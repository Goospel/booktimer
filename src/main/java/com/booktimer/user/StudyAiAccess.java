package com.booktimer.user;

/**
 * 공부 화면의 AI 기능(일정 생성·사진 전사·백지복습 분석) 사용 권한 — 관리자 승인제.
 *
 * <p>기본값은 {@link #NONE}이라 <b>아무도 자동으로 켜지지 않는다</b>(관리자 자신도 포함). 전이 규칙과
 * 그 강제는 {@link User#requestStudyAi}·{@link User#approveStudyAi}·{@link User#rejectStudyAi}·
 * {@link User#revokeStudyAi}에 있다 — 여기엔 이름만 둔다.
 */
public enum StudyAiAccess {

    /** 신청한 적 없음(기본값). */
    NONE,

    /** 신청했고 관리자 판단을 기다리는 중. */
    PENDING,

    /** 승인 — AI 엔드포인트를 쓸 수 있다(키가 있고 상한이 남아 있다면). */
    APPROVED,

    /** 거절되었거나 승인이 회수됨. 재신청은 즉시 가능하다. */
    REJECTED
}
