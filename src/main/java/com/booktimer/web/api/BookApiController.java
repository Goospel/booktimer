package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookRecommendation;
import com.booktimer.book.BookRecommendationService;
import com.booktimer.book.BookSearchResult;
import com.booktimer.book.BookSearchType;
import com.booktimer.book.BookService;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.book.KyoboLinkBuilder;
import com.booktimer.book.Yes24LinkBuilder;
import com.booktimer.popularity.FollowScopePopularity;
import com.booktimer.popularity.FollowScopePopularityService;
import com.booktimer.security.CurrentUserService;
import com.booktimer.session.BookReadingStatsService;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 내 책장 JSON API (선별 SPA 단계 3).
 *
 * <p>GET /api/books(전체 조회) · GET /api/books/search(검색 1페이지) · POST /api/books(추가) ·
 * POST /api/books/{id}/status · /visibility · /delete.
 *
 * <p>⚠️ {@code @RestController}는 {@code @ModelAttribute}(AffiliateModelAdvice)를 무시 →
 * {@link CoupangLinkBuilder#isEnabled()}·{@link Yes24LinkBuilder#isEnabled()}를 직접 주입해
 * {@code coupangEnabled}·{@code yes24Enabled}를 계산.
 */
@RestController
public class BookApiController {

    private final CurrentUserService currentUserService;
    private final BookService bookService;
    private final BookRecommendationService recommendationService;
    private final BookReadingStatsService statsService;
    private final FollowScopePopularityService popularityService;
    private final StoryRepository storyRepository;
    private final CoupangLinkBuilder coupangLinkBuilder;
    private final Yes24LinkBuilder yes24LinkBuilder;
    private final KyoboLinkBuilder kyoboLinkBuilder;

    public BookApiController(CurrentUserService currentUserService, BookService bookService,
                             BookRecommendationService recommendationService,
                             BookReadingStatsService statsService,
                             FollowScopePopularityService popularityService,
                             StoryRepository storyRepository,
                             CoupangLinkBuilder coupangLinkBuilder,
                             Yes24LinkBuilder yes24LinkBuilder,
                             KyoboLinkBuilder kyoboLinkBuilder) {
        this.currentUserService = currentUserService;
        this.bookService = bookService;
        this.recommendationService = recommendationService;
        this.statsService = statsService;
        this.popularityService = popularityService;
        this.storyRepository = storyRepository;
        this.coupangLinkBuilder = coupangLinkBuilder;
        this.yes24LinkBuilder = yes24LinkBuilder;
        this.kyoboLinkBuilder = kyoboLinkBuilder;
    }

    // ── 조회: 책장 전체 + 메타 + popularity ──────────────────────────────────

    @GetMapping("/api/books")
    public ShelfResponse shelf(Principal principal) {
        User user = currentUserService.resolve(principal);
        List<Book> books = bookService.myBooks(user);
        Map<Long, Long> times = statsService.totalSecondsByBook(user);
        List<String> isbns = books.stream().map(Book::getIsbn13).toList(); // null 포함 — countByIsbn이 방어
        Map<String, FollowScopePopularity> pop = popularityService.countByIsbn(user, isbns);
        Map<Long, Long> storyCounts = storyCounts(books);
        List<MyBookSummary> rows = books.stream()
                .map(b -> MyBookSummary.from(b, times, storyCounts)).toList();
        return new ShelfResponse(user.getLoginId(), user.getNickname(),
                bookService.searchEnabled(), coupangLinkBuilder.isEnabled(), yes24LinkBuilder.isEnabled(),
                kyoboLinkBuilder.isEnabled(), rows, toPopularityMap(pop));
    }

    // ── 조회: 검색(1페이지) + 검색결과 isbn popularity ───────────────────────

    @GetMapping("/api/books/search")
    public SearchResponse search(@RequestParam String q,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false, defaultValue = "1") int page,
                                 Principal principal) {
        User user = currentUserService.resolve(principal);
        BookSearchType searchType = BookSearchType.from(type);
        var result = bookService.search(q, searchType, page);
        Set<String> myIsbns = bookService.myBooks(user).stream()
                .map(Book::getIsbn13).filter(Objects::nonNull).collect(Collectors.toSet());
        List<SearchRow> rows = result.results().stream()
                .map(r -> SearchRow.from(r, myIsbns)).toList();
        Map<String, FollowScopePopularity> pop = popularityService.countByIsbn(user,
                result.results().stream().map(BookSearchResult::isbn13).toList());
        return new SearchResponse(rows, toPopularityMap(pop));
    }

    /**
     * 「책 추가」 화면의 추천 — 검색 결과가 없을 때만 화면이 그린다.
     *
     * <p>어느 전략으로 뽑혔는지는 <b>알려주지 않는다</b>. 화면은 {@code title}·{@code reason}을 그대로
     * 그리므로 전략이 늘어도 클라이언트는 안 바뀐다. 뽑을 것이 없으면 title이 null이고 화면은 카드를 안 그린다.
     */
    @GetMapping("/api/books/recommend")
    public RecommendResponse recommend(Principal principal) {
        User user = currentUserService.resolve(principal);
        BookRecommendation rec = recommendationService.recommendFor(user);
        // 서버가 이미 내 책을 걸렀지만 owned를 계산해 준다 — 화면이 검색 결과와 **같은 행**을 쓰기 때문이다
        // (isbn 없는 책은 판정 불가라 걸러지지 않으므로, 그 경우 화면이 형태로 말할 길이 남아 있어야 한다).
        Set<String> myIsbns = bookService.myBooks(user).stream()
                .map(Book::getIsbn13).filter(Objects::nonNull).collect(Collectors.toSet());
        List<SearchRow> rows = rec.books().stream().map(r -> SearchRow.from(r, myIsbns)).toList();
        return new RecommendResponse(rec.title(), rec.reason(), rows);
    }

    // ── 뮤테이션: 추가(검색결과/수동 공용) ───────────────────────────────────

    @PostMapping("/api/books")
    public ResponseEntity<MyBookSummary> add(@RequestBody AddRequest req, Principal principal) {
        User user = currentUserService.resolve(principal);
        BookStatus status = req.status() != null ? req.status() : BookStatus.WANT_TO_READ;
        BookSearchResult result = new BookSearchResult(req.title(), req.author(), req.isbn13(),
                req.coverUrl(), req.publisher(), req.purchaseLink(), req.category(), req.pubDate());
        try {
            Book saved = bookService.addFromSearch(user, result, status);
            return ResponseEntity.ok(summaryOf(user, saved));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "책을 추가할 수 없습니다");
        }
    }

    @PostMapping("/api/books/{id}/status")
    public ResponseEntity<MyBookSummary> changeStatus(@PathVariable Long id,
                                                      @RequestBody StatusRequest req,
                                                      Principal principal) {
        User user = currentUserService.resolve(principal);
        Book updated = mutate(() -> bookService.changeStatus(user, id, req.status()));
        return ResponseEntity.ok(summaryOf(user, updated));
    }

    @PostMapping("/api/books/{id}/visibility")
    public ResponseEntity<MyBookSummary> setVisibility(@PathVariable Long id,
                                                       @RequestBody VisibilityRequest req,
                                                       Principal principal) {
        User user = currentUserService.resolve(principal);
        Book updated = mutate(() -> bookService.setVisibility(user, id, req.visibility()));
        return ResponseEntity.ok(summaryOf(user, updated));
    }

    @PostMapping("/api/books/{id}/delete")
    public ResponseEntity<DeleteResult> delete(@PathVariable Long id, Principal principal) {
        User user = currentUserService.resolve(principal);
        mutate(() -> { bookService.delete(user, id); return null; });
        return ResponseEntity.ok(new DeleteResult(true));
    }

    /** IDOR/없는 책 IAE → 404(존재 비노출). 403 아님 — SSR flash·422를 베끼지 말고 404로 통일. */
    private static <T> T mutate(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없습니다");
        }
    }

    /** 단건 뮤테이션 응답 — 목록 행과 <b>같은 모양</b>이어야 한다(클라가 응답을 목록에 되꽂는다). */
    private MyBookSummary summaryOf(User user, Book book) {
        return MyBookSummary.from(book, Map.of(book.getId(), statsService.secondsForBook(user, book)),
                storyCounts(List.of(book)));
    }

    /** 책 id → 여백 글 수. 글 없는 책은 키가 없다(호출부가 0으로 채운다). 빈 책장이면 쿼리를 안 친다. */
    private Map<Long, Long> storyCounts(List<Book> books) {
        if (books.isEmpty()) {
            return Map.of();
        }
        return storyRepository.countByBook(books.stream().map(Book::getId).toList()).stream()
                .collect(Collectors.toMap(StoryRepository.BookStoryCount::getBookId,
                        StoryRepository.BookStoryCount::getCount));
    }

    private static Map<String, Popularity> toPopularityMap(Map<String, FollowScopePopularity> src) {
        return src.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new Popularity(e.getValue().wantCount(), e.getValue().readCount())));
    }

    // ── DTO (Book 엔티티 직렬화 금지 — 평탄 record 화이트리스트) ─────────────
    // status/visibility 는 enum name(select·필터·전송용), *Label 은 한글(배지 표시용) 둘 다 내린다.

    /**
     * @param storyCount 그 책 여백에 쌓인 글 수. 서재 관리 시트가 공개 전환 <b>전에</b>
     *                   「여백에 남긴 글 N개가 팔로워에게 보여요」를 고지하는 근거다(2026-08-16 결정 B).
     *                   <b>뮤테이션 응답도 실제 값을 싣는다</b> — 클라가 응답 행을 목록에 되꽂는 구조라,
     *                   0으로 두면 상태 변경 한 번이 그 책의 고지를 조용히 꺼 버린다(fail-open)
     */
    public record MyBookSummary(Long id, String title, String author, String coverUrl, String isbn13,
                                String status, String statusLabel,
                                String visibility, String visibilityLabel, boolean isPublic,
                                long seconds, String purchaseLink, long storyCount) {
        static MyBookSummary from(Book b, Map<Long, Long> times, Map<Long, Long> storyCounts) {
            return new MyBookSummary(b.getId(), b.getTitle(), b.getAuthor(), b.getCoverUrl(), b.getIsbn13(),
                    b.getStatus().name(), b.getStatus().getLabel(),
                    b.getVisibility().name(), b.getVisibility().getLabel(), b.isPublic(),
                    times.getOrDefault(b.getId(), 0L), b.getPurchaseLink(),
                    storyCounts.getOrDefault(b.getId(), 0L));
        }
    }

    public record SearchRow(String title, String author, String isbn13, String coverUrl,
                            String publisher, String purchaseLink, String category, String pubDate,
                            boolean owned) {
        static SearchRow from(BookSearchResult r, Set<String> myIsbns) {
            boolean owned = r.isbn13() != null && myIsbns.contains(r.isbn13()); // N-055: null isbn은 owned 아님
            return new SearchRow(r.title(), r.author(), r.isbn13(), r.coverUrl(),
                    r.publisher(), r.purchaseLink(), r.category(), r.pubDate(), owned);
        }
    }

    public record Popularity(long wantCount, long readCount) {}

    public record ShelfResponse(String myLoginId, String nickname, boolean searchEnabled,
                                boolean coupangEnabled, boolean yes24Enabled, boolean kyoboEnabled,
                                List<MyBookSummary> books, Map<String, Popularity> popularity) {}

    public record SearchResponse(List<SearchRow> results, Map<String, Popularity> popularity) {}

    /** 추천 응답 — 제목·근거는 서버가 문장으로 만들어 준다(화면은 전략을 모른다). */
    public record RecommendResponse(String title, String reason, List<SearchRow> results) {}

    public record AddRequest(String title, String author, String isbn13, String coverUrl,
                             String publisher, String purchaseLink, String category, String pubDate,
                             BookStatus status) {}

    public record StatusRequest(BookStatus status) {}

    public record VisibilityRequest(BookVisibility visibility) {}

    public record DeleteResult(boolean deleted) {}
}
