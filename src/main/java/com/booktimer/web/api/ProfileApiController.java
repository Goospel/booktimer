package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.profile.ProfileService;
import com.booktimer.profile.ProfileTag;
import com.booktimer.profile.ProfileView;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 프로필 조회 JSON API (선별 SPA 단계 2d).
 *
 * <p>GET /api/profile(헤더+책BTI+전체책), /api/profile/books(상태필터), /api/profile/personality-tag(태그 드릴다운).
 * 모두 {@link ProfileService} 가드({@code resolveVisibleTarget} 3중)를 통과 — 차단·ADMIN·미존재 → 404.
 *
 * <p>⚠️ {@code @ModelAttribute}(CoupangModelAdvice)는 {@code @RestController}에 무시됨 →
 * {@link CoupangLinkBuilder#isEnabled()}를 직접 주입해 {@code coupangEnabled}를 계산한다(회귀 방지).
 */
@RestController
public class ProfileApiController {

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;
    private final CoupangLinkBuilder coupangLinkBuilder;

    public ProfileApiController(ProfileService profileService,
                                CurrentUserService currentUserService,
                                CoupangLinkBuilder coupangLinkBuilder) {
        this.profileService = profileService;
        this.currentUserService = currentUserService;
        this.coupangLinkBuilder = coupangLinkBuilder;
    }

    /** 프로필 헤더 + 책BTI 서술/태그칩 + 전체 PUBLIC 책 목록(상태필터 없음). */
    @GetMapping("/api/profile")
    public ProfileResponse profile(@RequestParam String loginId, Principal principal) {
        User viewer = currentUserService.resolve(principal);
        ProfileView v = profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다"));
        boolean coupangEnabled = coupangLinkBuilder.isEnabled();
        return ProfileResponse.from(v, coupangEnabled);
    }

    /** 상태필터 적용된 PUBLIC 책 목록. status 없거나 잘못되면 전체(관대 파싱). */
    @GetMapping("/api/profile/books")
    public BooksResponse books(@RequestParam String loginId,
                               @RequestParam(required = false) String status,
                               Principal principal) {
        User viewer = currentUserService.resolve(principal);
        ProfileView v = profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다"));
        BookStatus filter = parseStatus(status);
        List<BookSummary> rows = v.books().stream()
                .filter(b -> filter == null || b.getStatus() == filter)
                .map(b -> BookSummary.from(b, v.bookTimes()))
                .toList();
        return new BooksResponse(rows);
    }

    /** 태그 드릴다운 근거 책(PUBLIC ∩ FINISHED ∩ 해당 태그). 동일 가드 통과. */
    @GetMapping("/api/profile/personality-tag")
    public BooksResponse personalityTag(@RequestParam String loginId,
                                        @RequestParam String tag,
                                        Principal principal) {
        User viewer = currentUserService.resolve(principal);
        List<Book> books = profileService.booksForPersonalityTag(viewer, loginId, tag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다"));
        List<BookSummary> rows = books.stream()
                .map(b -> BookSummary.from(b, Map.of()))
                .toList();
        return new BooksResponse(rows);
    }

    /**
     * 관대 파싱: null/blank/잘못된 값 → null(=전체). 예외 안 냄.
     * BookStatus에 fromOrNull 헬퍼가 없어 컨트롤러 인라인 try/catch로 처리(ProfileController 동일 로직).
     */
    private static BookStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return BookStatus.valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── DTO (Book 엔티티 직렬화 금지 — 평탄 record로 화이트리스트) ──────────

    public record BookSummary(Long id, String title, String author, String coverUrl,
                              String status, long seconds, String purchaseLink) {
        static BookSummary from(Book b, Map<Long, Long> times) {
            return new BookSummary(b.getId(), b.getTitle(), b.getAuthor(), b.getCoverUrl(),
                    b.getStatus().getLabel(), times.getOrDefault(b.getId(), 0L), b.getPurchaseLink());
        }
    }

    public record TagChip(String label, boolean clickable) {}

    public record BooksResponse(List<BookSummary> books) {}

    public record ProfileResponse(
            String loginId, String nickname,
            long followerCount, long followingCount,
            boolean following, boolean self,
            String personality, List<TagChip> personalityTags,
            List<BookSummary> books, boolean coupangEnabled) {

        /** ⚠️ coupangEnabled는 CoupangLinkBuilder.isEnabled()로 계산해 전달 — 여기서 false 하드코딩 금지. */
        static ProfileResponse from(ProfileView v, boolean coupangEnabled) {
            List<BookSummary> books = v.books().stream()
                    .map(b -> BookSummary.from(b, v.bookTimes()))
                    .toList();
            List<TagChip> tags = v.personalityTags().stream()
                    .map(t -> new TagChip(t.label(), t.clickable()))
                    .toList();
            return new ProfileResponse(v.loginId(), v.nickname(),
                    v.followerCount(), v.followingCount(),
                    v.following(), v.self(),
                    v.personality(), tags, books, coupangEnabled);
        }
    }
}
