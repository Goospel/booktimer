package com.booktimer.web;

import com.booktimer.feedback.FeedbackService;
import com.booktimer.feedback.FeedbackType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String list(HttpServletRequest request, @RequestParam(value = "type", required = false) String type, Model model) {
        CsrfTokenUtil.precommit(request); // 폼 렌더 전 세션 선확정 — commit-후-500 방어(T-049)
        FeedbackType filter = FeedbackType.parse(type); // 없음/빈/잘못된 값 → null(전체)
        model.addAttribute("feedbackList", feedbackService.feedbackRows(filter));
        model.addAttribute("types", FeedbackType.values());
        model.addAttribute("activeType", filter); // 탭 강조용
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

    @PostMapping("/admin/feedback/{id}/reply")
    public String reply(@PathVariable("id") Long id, @RequestParam("reply") String reply,
                        RedirectAttributes redirectAttributes) {
        try {
            feedbackService.reply(id, reply); // 답장 = 자동 읽음(서비스/엔티티가 처리)
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "답장 내용을 입력하세요.");
        }
        return "redirect:/admin/feedback";
    }
}
