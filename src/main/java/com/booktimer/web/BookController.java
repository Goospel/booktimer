package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
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

    public BookController(UserRepository userRepository, BookService bookService,
                          BookReadingStatsService statsService) {
        this.userRepository = userRepository;
        this.bookService = bookService;
        this.statsService = statsService;
    }

    @GetMapping("/books")
    public String books(@RequestParam(value = "q", required = false) String q,
                        Principal principal, Model model) {
        User user = currentUser(principal);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("books", bookService.myBooks(user));
        model.addAttribute("bookTimes", statsService.totalSecondsByBook(user)); // 책 id → 누적 초
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("searchEnabled", bookService.searchEnabled());
        model.addAttribute("q", q);
        if (q != null && !q.isBlank()) {
            model.addAttribute("results", bookService.search(q));
        }
        return "books";
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
