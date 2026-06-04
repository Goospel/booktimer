package com.booktimer.report;

import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 신고(report) 서비스 통합 테스트 (실제 빈 + H2) — SNS 5단계, sns-design §7.5.
 *
 * <p>신고는 저장만 한다(관리자 검토는 추후). (reporter, reported) 쌍당 1건 — 중복 신고는 멱등.
 * 자기 신고는 거부. block 기능과 같은 패턴.
 */
@SpringBootTest
@Transactional
class ReportServiceTest {

    @Autowired
    private ReportService reportService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private UserRepository userRepository;

    private User user(String email, String nick) {
        return userRepository.saveAndFlush(User.of(email, "$2a$10$x", nick, "Asia/Seoul", Role.USER));
    }

    @Test
    @DisplayName("신고하면 1행이 저장되고 사유·상세가 보존된다")
    void report_savesRow() {
        User reporter = user("r@booktimer.com", "신고자");
        User target = user("t@booktimer.com", "대상");

        reportService.report(reporter, target, ReportReason.SPAM, "광고 도배");

        assertThat(reportRepository.existsByReporterAndReported(reporter, target)).isTrue();
        var reports = reportRepository.findAll();
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(reports.get(0).getDetail()).isEqualTo("광고 도배");
    }

    @Test
    @DisplayName("같은 대상 중복 신고는 멱등(1행 유지, 첫 신고 보존)")
    void report_idempotent() {
        User reporter = user("r2@booktimer.com", "신고자2");
        User target = user("t2@booktimer.com", "대상2");

        reportService.report(reporter, target, ReportReason.SPAM, null);
        reportService.report(reporter, target, ReportReason.HARASSMENT, "또 신고");

        var reports = reportRepository.findAll();
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getReason()).isEqualTo(ReportReason.SPAM); // 첫 신고 유지
    }

    @Test
    @DisplayName("자기 자신 신고는 거부된다")
    void report_self_rejected() {
        User me = user("self@booktimer.com", "본인");
        assertThatThrownBy(() -> reportService.report(me, me, ReportReason.OTHER, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
