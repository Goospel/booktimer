package com.booktimer.web;

import com.booktimer.book.BookNewsCollector;
import com.booktimer.book.NewsCollectionResult;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 책 뉴스 수동 수집 — 새벽 04:30 배치를 기다리지 않고 지금 한 번 돌린다.
 *
 * <p>수집원이 하루 1회 배치라 새로 완독한 책의 뉴스는 다음 새벽에야 붙는다. 배포 직후 첫 점등을
 * 확인할 때도, 완독 처리하고 바로 화면을 보고 싶을 때도 손으로 돌릴 길이 필요하다.
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} →
 * {@code hasRole("ADMIN")}로 강제한다(여기서 다시 검사하지 않음). 쓰기 액션이라 POST + CSRF —
 * {@link AdminBackfillController}와 같은 경계·같은 결과 플래시 방식이다.
 *
 * <p>수집 자체가 멱등(isbn당 delete-then-insert)이라 여러 번 눌러도 중복이 쌓이지 않는다.
 * 다만 isbn 종수만큼 외부 호출이 나가므로 연타할 이유는 없다.
 */
@Controller
public class AdminBookNewsController {

    private final BookNewsCollector collector;

    public AdminBookNewsController(BookNewsCollector collector) {
        this.collector = collector;
    }

    @PostMapping("/admin/books/collect-news")
    public String collect(RedirectAttributes redirectAttributes) {
        NewsCollectionResult result = collector.collect();
        redirectAttributes.addFlashAttribute("message", result.enabled()
                ? String.format("책 뉴스 수집 — 대상 %d종, 저장 %d건", result.targets(), result.saved())
                : "책 뉴스 수집이 꺼져 있습니다 — booktimer.news.enabled 를 켜야 실행됩니다.");
        return "redirect:/admin";
    }
}
