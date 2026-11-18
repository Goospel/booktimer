package com.booktimer.web;

import com.booktimer.blog.BlogModerationService;
import com.booktimer.blog.BlogModerationService.Unit;
import com.booktimer.blog.Moderation.Action;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 블로그 처리 문(B2b · 계획 §5.5) — 신고 처리 · 단위 가리기(임시조치 · 저작권 중단 · 정책 · 다시 보이기 · 삭제) · 글쓰기 막기.
 * 목록은 신고함({@link AdminReportController})이 그린다 — 처리 문은 {@code AdminChatController} 선례처럼 따로 둔다.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → ADMIN으로 강제하고, POST라 CSRF를 받는다(여기서
 * 다시 검사하지 않음). 입력 오류는 flash {@code error}로 신고함에 보인다.
 */
@Controller
public class AdminBlogController {

    private static final String BACK = "redirect:/admin/reports";
    private static final String SAME = "바뀐 것이 없어요";

    private final BlogModerationService moderation;

    public AdminBlogController(BlogModerationService moderation) {
        this.moderation = moderation;
    }

    /** @param action NONE(조치 없이 닫기) · TEMP · COPYRIGHT · POLICY · REMOVE — 단위는 신고 대상에 묶여야 한다(서비스가 확인) */
    @PostMapping("/admin/blog/reports/{id}/resolve")
    public String resolve(@PathVariable long id, @RequestParam String action, @RequestParam(required = false) String unit,
                          @RequestParam(required = false) Long unitId, @RequestParam(required = false) String note,
                          RedirectAttributes redirect) {
        try {
            moderation.resolve(id, "NONE".equals(action) ? null : Action.valueOf(action), unit == null ? null : Unit.valueOf(unit),
                    unitId, note);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return BACK;
    }

    /** 신고와 무관하게 단위 하나(「이 글을 인용한 박스」 · 기간 끝난 임시조치 · 문의로 받은 쪽지 박스). */
    @PostMapping("/admin/blog/moderate")
    public String moderate(@RequestParam String unit, @RequestParam long unitId, @RequestParam String action,
                           @RequestParam(required = false) String note, RedirectAttributes redirect) {
        try {
            if (!moderation.apply(Unit.valueOf(unit), unitId, Action.valueOf(action), note)) {
                redirect.addFlashAttribute("error", SAME);
            }
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return BACK;
    }

    @PostMapping("/admin/blog/users/{id}/restriction")
    public String restriction(@PathVariable long id, @RequestParam boolean on, RedirectAttributes redirect) {
        try {
            moderation.restrict(id, on);
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return BACK;
    }
}
