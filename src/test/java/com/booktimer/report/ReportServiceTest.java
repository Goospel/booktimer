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

    /** loginId(공개 @핸들)까지 부여한 사용자 — 신고함 행이 loginId로 신고자·대상을 표시하므로. */
    private User userWithLogin(String email, String nick, String loginId) {
        User u = User.of(email, "$2a$10$x", nick, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
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

    @Test
    @DisplayName("관리자 조회: 신고자·대상·사유·상세를 행으로 담아 최신순으로 돌려준다")
    void reportRows_assemblesNewestFirst() {
        User r1 = userWithLogin("ra@booktimer.com", "신고자A", "reporteralpha");
        User t1 = userWithLogin("ta@booktimer.com", "대상A", "targetalpha");
        User r2 = userWithLogin("rb@booktimer.com", "신고자B", "reporterbravo");
        User t2 = userWithLogin("tb@booktimer.com", "대상B", "targetbravo");

        reportService.report(r1, t1, ReportReason.SPAM, "광고 도배");
        reportService.report(r2, t2, ReportReason.HARASSMENT, null);

        var rows = reportService.reportRows();

        assertThat(rows).hasSize(2);
        // 최신순 — 둘째로 들어온 r2→t2가 앞
        assertThat(rows.get(0).reporterLoginId()).isEqualTo("reporterbravo");
        assertThat(rows.get(0).reportedLoginId()).isEqualTo("targetbravo");
        assertThat(rows.get(0).reason()).isEqualTo(ReportReason.HARASSMENT);
        AdminReportRow first = rows.get(1);
        assertThat(first.reporterLoginId()).isEqualTo("reporteralpha");
        assertThat(first.reporterNickname()).isEqualTo("신고자A");
        assertThat(first.reportedLoginId()).isEqualTo("targetalpha");
        assertThat(first.reason()).isEqualTo(ReportReason.SPAM);
        assertThat(first.detail()).isEqualTo("광고 도배");
    }

    @Test
    @DisplayName("관리자 삭제: 처리 끝난 신고를 id로 지운다(없는 id는 무시)")
    void deleteByAdmin_removesRow() {
        User reporter = user("rd@booktimer.com", "신고자D");
        User target = user("td@booktimer.com", "대상D");
        reportService.report(reporter, target, ReportReason.SPAM, null);
        Long id = reportRepository.findAll().get(0).getId();

        reportService.deleteByAdmin(id);
        reportService.deleteByAdmin(999_999L); // 없는 id — 조용히 무시(예외 없음)

        assertThat(reportRepository.findById(id)).isEmpty();
    }
}
