package com.booktimer.report;

import com.booktimer.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 유스케이스 (SNS 5단계, sns-design §7.5). 저장만 한다(관리자 검토는 추후).
 *
 * <p>{@code (reporter, reported)} 쌍당 1건 — 이미 신고했으면 멱등(아무것도 안 함, 첫 신고 보존).
 * 자기 신고·null은 {@link Report#of}가 거부한다. block 기능과 같은 패턴.
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
}
