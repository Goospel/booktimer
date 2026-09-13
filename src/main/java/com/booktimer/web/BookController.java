package com.booktimer.web;

import com.booktimer.book.BookService;
import com.booktimer.book.KyoboLinkBuilder;
import com.booktimer.book.Yes24LinkBuilder;
import com.booktimer.session.BookReadingDetail;
import com.booktimer.security.CurrentUserService;
import com.booktimer.profile.ProfileService;
import com.booktimer.session.BookContributionService;
import com.booktimer.user.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * 내 책장 — SSR 셸 + 유지 대상(책 상세·buy* 4종·readers).
 *
 * <p>단계 3 선별 SPA 전환으로 GET /books는 myLoginId만 싣는 얇은 셸이 되었다.
 * 6 뮤테이션(추가·상태·공개·삭제·검색)은 {@link com.booktimer.web.api.BookApiController} JSON API가 담당.
 */
@Controller
public class BookController {

    private final CurrentUserService currentUserService;
    private final BookService bookService;
    private final BookContributionService contributionService;
    private final ProfileService profileService;

    public BookController(CurrentUserService currentUserService, BookService bookService,
                          BookContributionService contributionService,
                          ProfileService profileService) {
        this.currentUserService = currentUserService;
        this.bookService = bookService;
        this.contributionService = contributionService;
        this.profileService = profileService;
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
     *
     * <p>{@code {id:\d+}} 로 숫자만 받는다 — 미제한 {@code {id}}는 같은 prefix의 Vue 번들 요청
     * {@code /books/books-<hash>.js}(2세그먼트)까지 가로채 Long 변환 실패→500을 냈다(books 섬 셸 무한 로딩).
     * 숫자 제한으로 비숫자 경로는 정적 리소스 핸들러로 폴백한다. (회귀: BookControllerTest)
     */
    @GetMapping("/books/{id:\\d+}")
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
                    // 구매 옵션 리스트 — Vue(BooksApp.buyOptions)와 같은 로직을 서버가 계산해 SSR에 실는다(조합 분기 폭발 방지).
                    model.addAttribute("buyOptions", ownedBuyOptions(book));
                    return "book-detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "책을 찾을 수 없습니다.");
                    return "redirect:/books";
                });
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
     * 남의 책방(공개 프로필)에서 "구매" 클릭 — 그 책방 주인의 공개(PUBLIC) 책이면 제휴 링크로
     * 리다이렉트하고 클릭을 책 주인 카운트에 집계한다.
     *
     * <p>게이트는 <b>프로필 조회와 같은 것</b>을 쓴다({@code resolveVisibleTarget} — 운영자·차단·없는
     * 아이디를 한 번에 거른다). 분기하면 두 경로의 보장이 갈리므로 여기서 따로 검사하지 않는다.
     * 조회는 그 주인으로 스코프되므로({@code recordPublic*}) 남의 공개책을 임의 책방 주소에 매달
     * 수 없다. 거부는 전부 그 프로필로 조용히 복귀 — 존재 여부를 드러내지 않는다.
     */
    @GetMapping("/u/{loginId}/books/{bookId}/buy")
    public String buyFromProfile(@PathVariable String loginId, @PathVariable Long bookId, Principal principal) {
        User viewer = currentUser(principal);
        String link = profileService.resolveVisibleTarget(viewer, loginId)
                .map(owner -> bookService.recordPublicPurchaseClick(owner, bookId))
                .orElse(null);
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
     * Yes24 "구매" 클릭 — 집계 후 Yes24 검색 링크로 리다이렉트.
     *
     * <p>모바일 UA면 Yes24 자체 제휴 게이트가 딥링크를 버려 모바일 메인으로 치환해버리므로(T-128),
     * User-Agent로 판별해 제휴 래퍼 없이 모바일 검색으로 직행시킨다.
     */
    @GetMapping("/books/{id}/buy/yes24")
    public String buyYes24(@PathVariable Long id, Principal principal,
                           @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        User user = currentUser(principal);
        boolean mobile = Yes24LinkBuilder.isMobileUserAgent(userAgent);
        try {
            String link = bookService.recordYes24Click(user, id, mobile);
            if (link != null) {
                return "redirect:" + link;
            }
        } catch (IllegalArgumentException ignored) {
            // IDOR 방지 — 존재 여부 노출 없이 책장으로.
        }
        return "redirect:/books";
    }

    /**
     * 교보문고 "구매" 클릭 — 집계 후 교보 검색 링크로 리다이렉트. 모바일 UA 분기는 {@link #buyYes24} 참조(T-128 대칭).
     */
    @GetMapping("/books/{id}/buy/kyobo")
    public String buyKyobo(@PathVariable Long id, Principal principal,
                           @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        User user = currentUser(principal);
        boolean mobile = KyoboLinkBuilder.isMobileUserAgent(userAgent);
        try {
            String link = bookService.recordKyoboClick(user, id, mobile);
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
        User viewer = currentUser(principal);
        String link = profileService.resolveVisibleTarget(viewer, loginId)
                .map(owner -> bookService.recordPublicCoupangClick(owner, bookId))
                .orElse(null);
        if (link != null) {
            return "redirect:" + link;
        }
        return "redirect:/u/" + loginId;
    }

    /**
     * 남의 책방(공개 프로필)에서 Yes24 "구매" 클릭. 모바일 UA 분기는 {@link #buyYes24} 참조(T-128).
     */
    @GetMapping("/u/{loginId}/books/{bookId}/buy/yes24")
    public String buyYes24FromProfile(@PathVariable String loginId, @PathVariable Long bookId, Principal principal,
                                      @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        User viewer = currentUser(principal);
        boolean mobile = Yes24LinkBuilder.isMobileUserAgent(userAgent);
        String link = profileService.resolveVisibleTarget(viewer, loginId)
                .map(owner -> bookService.recordPublicYes24Click(owner, bookId, mobile))
                .orElse(null);
        if (link != null) {
            return "redirect:" + link;
        }
        return "redirect:/u/" + loginId;
    }

    /**
     * 남의 책방(공개 프로필)에서 교보문고 "구매" 클릭. 모바일 UA 분기는 {@link #buyYes24} 참조(T-128 대칭).
     */
    @GetMapping("/u/{loginId}/books/{bookId}/buy/kyobo")
    public String buyKyoboFromProfile(@PathVariable String loginId, @PathVariable Long bookId, Principal principal,
                                      @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        User viewer = currentUser(principal);
        boolean mobile = KyoboLinkBuilder.isMobileUserAgent(userAgent);
        String link = profileService.resolveVisibleTarget(viewer, loginId)
                .map(owner -> bookService.recordPublicKyoboClick(owner, bookId, mobile))
                .orElse(null);
        if (link != null) {
            return "redirect:" + link;
        }
        return "redirect:/u/" + loginId;
    }

    /**
     * 내 책 상세의 구매 옵션 리스트(알라딘/쿠팡/Yes24) — 활성 제공자만 담는다(프론트 buyOptions와 동형).
     * 알라딘=구매링크 유무, 쿠팡·Yes24=제휴 활성 여부. 템플릿은 size로 드롭다운/단일 버튼을 가른다.
     */
    private List<BuyOption> ownedBuyOptions(com.booktimer.book.Book book) {
        List<BuyOption> opts = new ArrayList<>();
        String base = "/books/" + book.getId();
        if (book.getPurchaseLink() != null && !book.getPurchaseLink().isBlank()) {
            opts.add(new BuyOption("알라딘", base + "/buy"));
        }
        if (bookService.coupangEnabled()) {
            opts.add(new BuyOption("쿠팡", base + "/buy/coupang"));
        }
        if (bookService.yes24Enabled()) {
            opts.add(new BuyOption("Yes24", base + "/buy/yes24"));
        }
        if (bookService.kyoboEnabled()) {
            opts.add(new BuyOption("교보문고", base + "/buy/kyobo"));
        }
        return opts;
    }

    /** 구매 옵션 한 줄(제공자 라벨 + 이동 경로) — SSR 템플릿 th:each 렌더용. */
    public record BuyOption(String label, String url) {}

    private User currentUser(Principal principal) {
        return currentUserService.resolve(principal);
    }
}
