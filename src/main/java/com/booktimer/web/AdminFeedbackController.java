package com.booktimer.web;

import com.booktimer.feedback.FeedbackService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 관리자 문의함 — 개발자(ADMIN)가 사용자 문의를 전부 읽고, 읽음/처리완료를 표시하거나 삭제한다.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 다시 검사하지 않음 — 경계는 필터에서 한 번만). 상태 표시(읽음/처리완료)·삭제는 POST라
 * CSRF 보호를 받는다. 상태 변경 결과는 그 문의를 쓴 작성자만 자기 화면에서 확인한다(노출 스코핑).
 */
@Controller
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/admin/feedback")
    public String list(Model model) {
        model.addAttribute("feedbackList", feedbackService.allFeedbackRows());
        return "admin-feedback";
    }

    @PostMapping("/admin/feedback/{id}/read")
    public String read(@PathVariable("id") Long id) {
        feedbackService.markRead(id);
        return "redirect:/admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/resolve")
    public String resolve(@PathVariable("id") Long id) {
        feedbackService.markResolved(id);
        return "redirect:/admin/feedback";
    }

    @PostMapping("/admin/feedback/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        feedbackService.deleteByAdmin(id);
        return "redirect:/admin/feedback";
    }
}
