package com.booktimer.report;

import com.booktimer.user.User;

import java.time.Instant;

/**
 * 관리자 신고함 표시용 행 — 트랜잭션 안에서 엔티티(LAZY {@code reporter}/{@code reported} 포함)를 평면 값으로 추출한다.
 *
 * <p>뷰에서 detached 엔티티의 LAZY 연관을 건드리지 않게(OSIV 비의존) 서비스가 미리 조립한다.
 * 운영자가 누가 누구를 왜 신고했는지 맥락을 보고 후속 처리한다(loginId는 OAuth 온보딩 전이면 null일 수 있어
 * 닉네임 폴백). {@link com.booktimer.feedback.AdminFeedbackRow}와 같은 패턴.
 */
public record AdminReportRow(
        Long id,
        String reporterLoginId,
        String reporterNickname,
        String reportedLoginId,
        String reportedNickname,
        ReportReason reason,
        String detail,
        Instant createdAt) {

    static AdminReportRow from(Report report) {
        User reporter = report.getReporter();
        User reported = report.getReported();
        return new AdminReportRow(
                report.getId(),
                reporter.getLoginId(),
                reporter.getNickname(),
                reported.getLoginId(),
                reported.getNickname(),
                report.getReason(),
                report.getDetail(),
                report.getCreatedAt());
    }
}
