package com.booktimer.web;

import com.booktimer.feedback.FeedbackService;
import com.booktimer.feedback.FeedbackType;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * 사용자 피드백/문의 — 일반 사용자가 개발자에게 버그·제안을 보내고, <b>자기가 쓴 문의만</b> 처리 상태와 함께 본다.
 *
 * <p>인증은 {@link com.booktimer.config.SecurityConfig}의 {@code anyRequest().authenticated()}가 강제한다
 * (비로그인 → /login). 노출 스코핑은 {@link FeedbackService#myFeedback}(작성자 본인 쿼리)이, 삭제 IDOR 방어는
 * {@link FeedbackService#deleteOwn}(본인 글만)이 담당한다. 작성·삭제는 POST라 CSRF 보호를 받고 PRG로 돌아온다.
 */
@Controller
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUserService currentUserService;

    public FeedbackController(FeedbackService feedbackService, CurrentUserService currentUserService) {
        this.feedbackService = feedbackService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/feedback")
    public String page(HttpServletRequest request, Principal principal, Model model) {
        CsrfTokenUtil.precommit(request); // 폼 렌더 전 세션 선확정 — commit-후-500 방어(T-049)
        User me = currentUserService.resolve(principal);
        model.addAttribute("myFeedback", feedbackService.myFeedback(me));
        model.addAttribute("types", FeedbackType.values());
        return "feedback";
    }

    @PostMapping("/feedback")
    public String submit(@RequestParam("type") String type,
                         @RequestParam("title") String title,
                         @RequestParam("content") String content,
                         Principal principal,
                         RedirectAttributes redirectAttributes) {
        User me = currentUserService.resolve(principal);
        try {
            feedbackService.submit(me, FeedbackType.from(type), title, content);
            redirectAttributes.addFlashAttribute("message", "문의를 보냈어요. 확인하면 여기에서 처리 상태를 알려드릴게요.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "제목과 내용을 모두 입력하세요.");
        }
        return "redirect:/feedback";
    }

    @PostMapping("/feedback/{id}/delete")
    public String delete(@PathVariable("id") Long id, Principal principal) {
        User me = currentUserService.resolve(principal);
        feedbackService.deleteOwn(id, me); // 본인 글만 — 남의 글 id는 조용히 무시(IDOR 방어)
        return "redirect:/feedback";
    }
}
