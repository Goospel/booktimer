package com.booktimer.web;

import com.booktimer.quote.QuoteService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 격언 관리 — 대시보드 인사말 자리에 노출되는 작가 격언을 추가/조회/삭제한다.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 다시 검사하지 않음 — 보안 경계는 필터에서 한 번만). 쓰기(추가/삭제)는 POST라 CSRF 보호를 받는다.
 *
 * <p>공백 격언은 {@link QuoteService#add}가 거부한다 — 컨트롤러는 그 예외를 흡수해 플래시 에러로 안내하고
 * 목록으로 되돌린다(저장은 일어나지 않는다).
 */
@Controller
public class AdminQuoteController {

    private final QuoteService quoteService;

    public AdminQuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @GetMapping("/admin/quotes")
    public String list(Model model) {
        model.addAttribute("quotes", quoteService.all());
        return "admin-quotes";
    }

    @PostMapping("/admin/quotes")
    public String add(@RequestParam("text") String text,
                      @RequestParam("author") String author,
                      RedirectAttributes redirectAttributes) {
        try {
            quoteService.add(text, author);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "문장과 작가를 모두 입력하세요.");
        }
        return "redirect:/admin/quotes";
    }

    @PostMapping("/admin/quotes/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        quoteService.delete(id);
        return "redirect:/admin/quotes";
    }
}
