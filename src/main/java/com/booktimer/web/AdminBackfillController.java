package com.booktimer.web;

import com.booktimer.book.BackfillResult;
import com.booktimer.book.BookCatalogBackfillService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 관리자 카탈로그 백필 — 기존 책의 장르·출간일을 알라딘 ItemLookUp으로 채운다(책BTI Phase 1b).
 *
 * <p>접근 제어는 {@link com.booktimer.config.SecurityConfig}가 {@code /admin/**} → {@code hasRole("ADMIN")}로
 * 강제한다(여기서 다시 검사하지 않음). 쓰기 액션이라 POST + CSRF. 외부 호출량을 통제하려 한 번에 {@code limit}권까지만
 * 처리하고, 결과(조회·채움·미발견·잔여)를 플래시로 보여준다 — 남은 권수가 있으면 관리자가 다시 눌러 이어서 채운다.
 */
@Controller
public class AdminBackfillController {

    /** 한 번에 처리할 기본 권수 — 외부 호출량·요청시간 통제(관리자가 param으로 조정 가능). */
    private static final int DEFAULT_LIMIT = 50;

    private final BookCatalogBackfillService backfillService;

    public AdminBackfillController(BookCatalogBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostMapping("/admin/books/backfill-catalog")
    public String backfill(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
                           RedirectAttributes redirectAttributes) {
        BackfillResult result = backfillService.backfill(limit);
        redirectAttributes.addFlashAttribute("message", String.format(
                "카탈로그 백필 — 조회 %d권, 채움 %d권, 미발견 %d권, 남은 %d권",
                result.scanned(), result.updated(), result.notFound(), result.remaining()));
        return "redirect:/admin";
    }

    /**
     * 제휴링크 백필 — TTBKey가 빠진 기존 구매링크를 알라딘 ItemLookUp(includeKey=1)으로 재조회해 교체한다.
     * {@code includeKey=1} 픽스 전 저장된 무추적 링크를 정식 제휴링크로 갱신(제휴 수익 복구). 카탈로그 백필과
     * 같은 인가 경계(/admin/** = ADMIN)·POST+CSRF·limit 배치·결과 플래시를 따른다.
     */
    @PostMapping("/admin/books/backfill-purchase-links")
    public String backfillPurchaseLinks(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
                                        RedirectAttributes redirectAttributes) {
        BackfillResult result = backfillService.backfillPurchaseLinks(limit);
        redirectAttributes.addFlashAttribute("message", String.format(
                "제휴링크 백필 — 조회 %d권, 갱신 %d권, 미발견 %d권, 남은 %d권",
                result.scanned(), result.updated(), result.notFound(), result.remaining()));
        return "redirect:/admin";
    }
}
