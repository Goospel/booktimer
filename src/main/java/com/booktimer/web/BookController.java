package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.BookContributionService;
import com.booktimer.session.BookReadingDetail;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * 내 책장 — SSR 셸 + 유지 대상(책 상세·buy* 4종·readers).
 *
 * <p>단계 3 선별 SPA 전환으로 GET /books는 myLoginId만 싣는 얇은 셸이 되었다.
 * 6 뮤테이션(추가·상태·공개·삭제·검색)은 {@link BookApiController} JSON API가 담당.
 * add/changeStatus/delete SSR POST 폼 핸들러는 htmx 분기를 제거하고 PRG-only로 유지(§6 백로그까지 보존).
 */
@Controller
public class BookController {

    private final CurrentUserService currentUserService;
    private final BookService bookService;
    private final BookContributionService contributionService;

    public BookController(CurrentUserService currentUserService, BookService bookService,
                          BookContributionService contributionService) {
        this.currentUserService = currentUserService;
        this.bookService = bookService;
        this.contributionService = contributionService;
    }

    /** 얇은 셸 — myLoginId만 싣고 BooksApp.vue에 위임한다. */
    @GetMapping("/books")
    public String books(Principal principal, Model model) {
        User user = currentUser(principal);
        model.addAttribute("myLoginId", user.getLoginId());
        return "books";
    }

    /**
     * 인기 카운트 drill-down 진입점 — Vue 섬이 {@code /api/book-readers}로 데이터를 직접 로드한다.
     * 셸은 isbn·title만 data-*로 전달하고 뷰로 돌아간다(인증 트리거 유지).
     */
    @GetMapping("/books/readers")
    public String readers(@RequestParam String isbn,
                          @RequestParam(required = false) String title,
                          Principal principal, Model model) {
        currentUser(principal);
        model.addAttribute("isbn", isbn);
        model.addAttribute("title", title);
        return "book-readers";
    }

    /**
     * 책 상세 — 그 책의 월별 일자 기록 + 누적 시간. 내 책일 때만(IDOR 방지),
     * 아니면 존재 여부 노출 없이 책장으로 돌려보낸다(PRG).
     */
    @GetMapping("/books/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model,
                         RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        return bookService.findMyBook(user, id)
                .map(book -> {
                    BookReadingDetail detail = contributionService.detail(user, book);
                    model.addAttribute("nickname", user.getNickname());
                    model.addAttribute("book", book);
                    model.addAttribute("months", detail.monthlyHistory());
                    model.addAttribute("totalSeconds", detail.totalSeconds());
                    return "book-detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "책을 찾을 수 없습니다.");
                    return "redirect:/books";
                });
    }

    @PostMapping("/books/add")
    public String add(@RequestParam String title,
                      @RequestParam(required = false) String author,
                      @RequestParam(required = false) String isbn13,
                      @RequestParam(required = false) String coverUrl,
                      @RequestParam(required = false) String publisher,
                      @RequestParam(required = false) String purchaseLink,
                      @RequestParam(required = false) String category,
                      @RequestParam(required = false) String pubDate,
                      @RequestParam(required = false, defaultValue = "WANT_TO_READ") BookStatus status,
                      Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            BookSearchResult result = new BookSearchResult(title, author, isbn13, coverUrl, publisher,
                    purchaseLink, category, pubDate);
            Book saved = bookService.addFromSearch(user, result, status);
            redirectAttributes.addFlashAttribute("message", "'" + saved.getTitle() + "'을(를) 책장에 추가했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "책을 추가할 수 없습니다. 제목을 확인해 주세요.");
        }
        return "redirect:/books";
    }

    @PostMapping("/books/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam BookStatus status,
                               Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            bookService.changeStatus(user, id, status);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "상태를 변경할 수 없습니다.");
        }
        return "redirect:/books";
    }

    /**
     * "구매" 클릭 — 집계 후 제휴 구매링크로 리다이렉트(링크가 없거나 내 책이 아니면 책장으로).
     */
    @GetMapping("/books/{id}/buy")
    public String buy(@PathVariable Long id, Principal principal) {
        User user = currentUser(principal);
        try {
            String link = bookService.recordPurchaseClick(user, id);
            if (link != null) {
                return "redirect:" + link;
            }
        } catch (IllegalArgumentException ignored) {
            // IDOR 방지 — 존재 여부 노출 없이 책장으로.
        }
        return "redirect:/books";
    }

    /**
     * 남의 책방(공개 프로필)에서 "구매" 클릭 — 공개(PUBLIC) 책이면 그 책의 제휴 링크로 리다이렉트하고
     * 클릭을 책 주인 카운트에 집계한다.
     */
    @GetMapping("/u/{loginId}/books/{bookId}/buy")
    public String buyFromProfile(@PathVariable String loginId, @PathVariable Long bookId, Principal principal) {
        currentUser(principal);
        String link = bookService.recordPublicPurchaseClick(bookId);
        if (link != null) {
            return "redirect:" + link;
        }
        return "redirect:/u/" + loginId;
    }

    /**
     * 쿠팡 "구매" 클릭 — 집계 후 쿠팡 검색 링크로 리다이렉트.
     */
    @GetMapping("/books/{id}/buy/coupang")
    public String buyCoupang(@PathVariable Long id, Principal principal) {
        User user = currentUser(principal);
        try {
            String link = bookService.recordCoupangClick(user, id);
            if (link != null) {
                return "redirect:" + link;
            }
        } catch (IllegalArgumentException ignored) {
            // IDOR 방지 — 존재 여부 노출 없이 책장으로.
        }
        return "redirect:/books";
    }

    /**
     * 남의 책방(공개 프로필)에서 쿠팡 "구매" 클릭.
     */
    @GetMapping("/u/{loginId}/books/{bookId}/buy/coupang")
    public String buyCoupangFromProfile(@PathVariable String loginId, @PathVariable Long bookId, Principal principal) {
        currentUser(principal);
        String link = bookService.recordPublicCoupangClick(bookId);
        if (link != null) {
            return "redirect:" + link;
        }
        return "redirect:/u/" + loginId;
    }

    /** htmx 분기 제거 — PRG-only(셸 템플릿이 더는 htmx로 호출하지 않음). 완전 제거는 §6 백로그. */
    @PostMapping("/books/{id}/visibility")
    public String setVisibility(@PathVariable Long id, @RequestParam BookVisibility visibility,
                                Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            bookService.setVisibility(user, id, visibility);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "공개 설정을 바꿀 수 없습니다.");
        }
        return "redirect:/books";
    }

    @PostMapping("/books/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            bookService.delete(user, id);
            redirectAttributes.addFlashAttribute("message", "책을 삭제했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "삭제할 수 없습니다.");
        }
        return "redirect:/books";
    }

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
