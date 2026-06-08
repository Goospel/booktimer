package com.booktimer.web;

import com.booktimer.report.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 관리자 신고함 — 개발자(ADMIN)가 사용자 신고를 전부 읽고(누가 누구를 왜 신고했는지) 후속 처리한 뒤 삭제한다.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 다시 검사하지 않음 — 경계는 필터에서 한 번만). 삭제는 POST라 CSRF 보호를 받는다.
 * 문의함({@link AdminFeedbackController})과 같은 구조.
 */
@Controller
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/admin/reports")
    public String list(Model model) {
        model.addAttribute("reportList", reportService.reportRows());
        return "admin-reports";
    }

    @PostMapping("/admin/reports/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        reportService.deleteByAdmin(id);
        return "redirect:/admin/reports";
    }
}
