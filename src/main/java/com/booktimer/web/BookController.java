package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.session.BookContributionService;
import com.booktimer.session.BookReadingDetail;
import com.booktimer.session.BookReadingStatsService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

/**
 * 내 책장 — 책 검색(알라딘)·등록·상태 변경·삭제.
 *
 * <p>검색은 {@link BookService}가 포트에 위임하며, API 키가 없으면 {@code searchEnabled=false}로
 * 화면이 수동 입력 폼으로 폴백한다. 등록/변경/삭제는 서비스가 소유권을 강제한다(IDOR 방지) —
 * 위반 시 {@link IllegalArgumentException}을 플래시 에러로 안내한다(PRG 패턴).
 */
@Controller
public class BookController {

    private final UserRepository userRepository;
    private final BookService bookService;
    private final BookReadingStatsService statsService;
    private final BookContributionService contributionService;

    public BookController(UserRepository userRepository, BookService bookService,
                          BookReadingStatsService statsService,
                          BookContributionService contributionService) {
        this.userRepository = userRepository;
        this.bookService = bookService;
        this.statsService = statsService;
        this.contributionService = contributionService;
    }

    @GetMapping("/books")
    public String books(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                        Principal principal, Model model) {
        User user = currentUser(principal);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("books", bookService.myBooks(user));
        model.addAttribute("bookTimes", statsService.totalSecondsByBook(user)); // 책 id → 누적 초
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("visibilities", BookVisibility.values());
        model.addAttribute("searchEnabled", bookService.searchEnabled());
        model.addAttribute("q", q);
        if (q != null && !q.isBlank()) {
            model.addAttribute("searchPage", bookService.search(q, page));
        }
        return "books";
    }

    /**
     * 책 상세 — 그 책의 독서 잔디 + 일자별 기록 + 누적 시간. 내 책일 때만(IDOR 방지),
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
                    model.addAttribute("graph", detail.graph());
                    model.addAttribute("history", detail.dailyHistory());
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
                      @RequestParam(required = false, defaultValue = "WANT_TO_READ") BookStatus status,
                      Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            BookSearchResult result = new BookSearchResult(title, author, isbn13, coverUrl, publisher, purchaseLink);
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
     *
     * <p>링크 클릭(GET)이라 CSRF 토큰을 붙이기 어려워 GET으로 둔다(상태 변경이지만 분석용 집계).
     * 리다이렉트 대상은 우리 DB에 저장된 알라딘 링크 — 클릭 시점에 사용자가 임의 URL을 넣지 못한다.
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
            // 내 책이 아니거나 없음 — 존재 여부 노출 없이 책장으로 되돌린다(IDOR 방지).
        }
        return "redirect:/books";
    }

    @PostMapping("/books/{id}/visibility")
    public String setVisibility(@PathVariable Long id, @RequestParam BookVisibility visibility,
                                Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            Book book = bookService.setVisibility(user, id, visibility);
            redirectAttributes.addFlashAttribute("message",
                    "'" + book.getTitle() + "'을(를) " + visibility.getLabel() + "로 바꿨습니다.");
        } catch (IllegalArgumentException e) {
            // 내 책이 아니거나 없음 — 존재 여부 노출 없이 책장으로(IDOR 방지).
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
        return userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException("authenticated user not found: " + principal.getName()));
    }
}
