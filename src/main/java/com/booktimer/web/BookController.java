package com.booktimer.web;

import com.booktimer.book.Book;
import com.booktimer.book.BookSearchPage;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchType;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.popularity.FollowScopePopularityService;
import com.booktimer.popularity.FollowScopeReaders;
import com.booktimer.popularity.FollowScopeReadersService;
import com.booktimer.session.BookContributionService;
import com.booktimer.session.BookReadingDetail;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.BookReadingStatsService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
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

    private final CurrentUserService currentUserService;
    private final BookService bookService;
    private final BookReadingStatsService statsService;
    private final BookContributionService contributionService;
    private final FollowScopePopularityService popularityService;
    private final FollowScopeReadersService readersService;

    public BookController(CurrentUserService currentUserService, BookService bookService,
                          BookReadingStatsService statsService,
                          BookContributionService contributionService,
                          FollowScopePopularityService popularityService,
                          FollowScopeReadersService readersService) {
        this.currentUserService = currentUserService;
        this.bookService = bookService;
        this.statsService = statsService;
        this.contributionService = contributionService;
        this.popularityService = popularityService;
        this.readersService = readersService;
    }

    @GetMapping("/books")
    public String books(@RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                        @RequestParam(value = "status", required = false) String status,
                        @RequestParam(value = "type", required = false) String type,
                        Principal principal, Model model) {
        User user = currentUser(principal);

        // 검색 기준(제목/저자). 없거나 잘못되면 제목으로 폴백 — "모기"를 제목으로 찾는 게 기본 의도.
        BookSearchType searchType = BookSearchType.from(type);

        // 책장 상태 필터(읽고싶음/읽는중/완독). 값이 없거나 잘못되면 전체(null).
        BookStatus shelfFilter = parseStatus(status);
        List<Book> myBooks = bookService.myBooks(user);
        List<Book> shelfBooks = shelfFilter == null
                ? myBooks
                : myBooks.stream().filter(b -> b.getStatus() == shelfFilter).toList();

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("loginId", user.getLoginId()); // "내 책방"(/u/{loginId}) 링크용 — 공개 토글이 책방에 반영됨을 안내
        model.addAttribute("books", shelfBooks);
        model.addAttribute("shelfFilter", shelfFilter); // 필터 칩 활성 표시·빈 메시지 분기
        model.addAttribute("bookTimes", statsService.totalSecondsByBook(user)); // 책 id → 누적 초
        model.addAttribute("statuses", BookStatus.values());
        model.addAttribute("searchEnabled", bookService.searchEnabled());
        model.addAttribute("q", q);
        model.addAttribute("searchType", searchType);          // 검색 기준 셀렉트 활성 표시·페이징 링크 유지
        model.addAttribute("searchTypes", BookSearchType.values());

        // 화면에 보이는 책들(책장 + 검색결과)의 isbn을 모아 팔로우 스코프 인기 카운트를 한 번에 집계(§7.4, N+1 회피).
        List<String> isbns = new ArrayList<>(shelfBooks.stream().map(Book::getIsbn13).toList());
        if (q != null && !q.isBlank()) {
            BookSearchPage searchPage = bookService.search(q, searchType, page);
            model.addAttribute("searchPage", searchPage);
            searchPage.results().forEach(r -> isbns.add(r.isbn13()));
        }
        model.addAttribute("popularity", popularityService.countByIsbn(user, isbns)); // isbn → (원함, 읽음)
        return "books";
    }

    /** 책장 필터 파라미터를 BookStatus로 — 없거나 잘못된 값이면 null(전체). */
    private BookStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 인기 카운트 drill-down — "이 책을 원함/읽음인 내 팔로우"는 누구인지(닉네임·프로필 링크).
     *
     * <p>카운트 배지({@code §7.4})를 클릭해 진입한다. 스코프는 카운트와 동일(팔로우·PUBLIC·distinct)이라
     * 모르는 사람·PRIVATE은 안 보인다 — 임의 isbn을 넣어도 내 팔로우의 공개 책만 나오므로 IDOR 없음.
     * {@code title}은 머리말 표시용(없어도 무방, Thymeleaf가 이스케이프).
     */
    @GetMapping("/books/readers")
    public String readers(@RequestParam String isbn,
                          @RequestParam(required = false) String title,
                          Principal principal, Model model) {
        User user = currentUser(principal);
        FollowScopeReaders readers = readersService.readers(user, isbn);
        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("isbn", isbn);
        model.addAttribute("title", title);
        model.addAttribute("readers", readers);
        return "book-readers";
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

    /**
     * 남의 책방(공개 프로필)에서 "구매" 클릭 — 공개(PUBLIC) 책이면 그 책의 제휴 링크로 리다이렉트하고
     * 클릭을 <b>책 주인 카운트</b>에 집계한다. 비공개·없는 책이면 존재 누설 없이 그 프로필로 되돌린다.
     *
     * <p>{@code /books/{id}/buy}는 "내 책"만 허용(IDOR)이라 남의 책엔 못 쓴다. 이 경로는 소유권 대신
     * "공개 여부"를 게이트로 둔다({@link BookService#recordPublicPurchaseClick}) — 공개 책은 이미 프로필에서
     * 누구나 보는 것이라 새 노출이 없다. {@code loginId}는 라우팅·복귀 대상일 뿐(조회는 bookId로) —
     * 임의 책방 경로로 와도 클릭은 책의 진짜 주인에게 집계된다. 링크 클릭(GET)이라 CSRF는 붙이지 않는다.
     */
    @GetMapping("/u/{loginId}/books/{bookId}/buy")
    public String buyFromProfile(@PathVariable String loginId, @PathVariable Long bookId, Principal principal) {
        currentUser(principal); // 로그인 사용자만(시큐리티가 이미 강제 — 명시)
        String link = bookService.recordPublicPurchaseClick(bookId);
        if (link != null) {
            return "redirect:" + link;
        }
        return "redirect:/u/" + loginId;
    }

    @PostMapping("/books/{id}/visibility")
    public String setVisibility(@PathVariable Long id, @RequestParam BookVisibility visibility,
                                Principal principal, RedirectAttributes redirectAttributes) {
        User user = currentUser(principal);
        try {
            // 성공 시 플래시 메시지 없음 — 토글 스위치가 바뀐 상태(🌍/🔒)를 이미 보여줘 중복 알림이라 제거(#196).
            bookService.setVisibility(user, id, visibility);
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
        return currentUserService.resolve(principal);
    }
}
