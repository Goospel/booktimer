package com.booktimer.report;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 신고 유스케이스 (SNS 5단계, sns-design §7.5). 접수(저장) + 관리자 검토(조회·삭제)를 담당한다.
 *
 * <p>{@code (reporter, reported)} 쌍당 1건 — 이미 신고했으면 멱등(아무것도 안 함, 첫 신고 보존).
 * 자기 신고·null은 {@link Report#of}가 거부한다. block 기능과 같은 패턴. 관리자 신고함은 문의함
 * ({@link com.booktimer.feedback.FeedbackService})과 같은 구조(트랜잭션 안에서 행 DTO로 조립 → OSIV 비의존).
 */
@Service
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * reporter가 reported를 신고한다. 이미 신고했으면 멱등(중복 행 안 만듦).
     *
     * @throws IllegalArgumentException 같은 사용자거나 null인 경우
     */
    public void report(User reporter, User reported, ReportReason reason, String detail) {
        if (reporter != null && reported != null
                && reportRepository.existsByReporterAndReported(reporter, reported)) {
            return; // 멱등 — 이미 신고함
        }
        reportRepository.save(Report.of(reporter, reported, reason, detail)); // null/자기 신고 검증
    }

    /**
     * 관리자 신고함 조회 — 최신순. 트랜잭션 안에서 행 DTO로 조립해 신고자·대상(LAZY)을 미리 추출한다
     * (뷰에서 detached 엔티티의 LAZY 연관을 안 건드림 — OSIV 비의존).
     */
    @Transactional(readOnly = true)
    public List<AdminReportRow> reportRows() {
        return reportRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(AdminReportRow::from)
                .toList();
    }

    /**
     * 관리자 삭제 — 처리 끝난 신고를 id로 지운다(없는 id면 무시).
     *
     * <p><b>법적 보존 신고는 지우지 않는다</b>(리뷰 #1169 사소 8) — 지우면 보존 표시가 조용히 사라져 다음 04:10 보존
     * 배치가 그 대화방을 지운다. 보존을 먼저 풀어야 지울 수 있다.
     *
     * @return 법적 보존이라 거부했으면 false
     */
    public boolean deleteByAdmin(Long id) {
        Report report = reportRepository.findById(id).orElse(null);
        if (report == null) {
            return true;
        }
        if (report.isLegalHold()) {
            return false;
        }
        reportRepository.delete(report);
        return true;
    }
}
