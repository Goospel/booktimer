package com.booktimer.web.api;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.book.CoupangLinkBuilder;
import com.booktimer.book.KyoboLinkBuilder;
import com.booktimer.book.Yes24LinkBuilder;
import com.booktimer.follow.FollowRepository;
import com.booktimer.profile.ProfileService;
import com.booktimer.profile.ProfileTag;
import com.booktimer.profile.ProfileView;
import com.booktimer.security.CurrentUserService;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 프로필 조회 JSON API (선별 SPA 단계 2d).
 *
 * <p>GET /api/profile(헤더+책BTI+전체책), /api/profile/books(상태필터), /api/profile/personality-tag(태그 드릴다운).
 * 모두 {@link ProfileService} 가드({@code resolveVisibleTarget} 3중)를 통과 — 차단·ADMIN·미존재 → 404.
 *
 * <p>⚠️ {@code @ModelAttribute}(AffiliateModelAdvice)는 {@code @RestController}에 무시됨 →
 * {@link CoupangLinkBuilder#isEnabled()}·{@link Yes24LinkBuilder#isEnabled()}를 직접 주입해
 * {@code coupangEnabled}·{@code yes24Enabled}를 계산한다(회귀 방지).
 */
@RestController
public class ProfileApiController {

    /** 공통 친구 줄에 이름으로 적는 인원 — 나머지는 「외 N명」으로 접는다(인스타와 같은 접기). */
    private static final int MUTUAL_NAMES = 2;

    private final ProfileService profileService;
    private final CurrentUserService currentUserService;
    private final CoupangLinkBuilder coupangLinkBuilder;
    private final Yes24LinkBuilder yes24LinkBuilder;
    private final KyoboLinkBuilder kyoboLinkBuilder;
    private final StoryRepository storyRepository;
    private final FollowRepository followRepository;

    public ProfileApiController(ProfileService profileService,
                                CurrentUserService currentUserService,
                                CoupangLinkBuilder coupangLinkBuilder,
                                Yes24LinkBuilder yes24LinkBuilder,
                                KyoboLinkBuilder kyoboLinkBuilder,
                                StoryRepository storyRepository,
                                FollowRepository followRepository) {
        this.profileService = profileService;
        this.currentUserService = currentUserService;
        this.coupangLinkBuilder = coupangLinkBuilder;
        this.yes24LinkBuilder = yes24LinkBuilder;
        this.kyoboLinkBuilder = kyoboLinkBuilder;
        this.storyRepository = storyRepository;
        this.followRepository = followRepository;
    }

    /** 프로필 헤더 + 책BTI 서술/태그칩 + 전체 PUBLIC 책 목록(상태필터 없음). */
    @GetMapping("/api/profile")
    public ProfileResponse profile(@RequestParam String loginId, Principal principal) {
        User viewer = currentUserService.resolve(principal);
        ProfileView v = profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다"));
        boolean coupangEnabled = coupangLinkBuilder.isEnabled();
        boolean yes24Enabled = yes24LinkBuilder.isEnabled();
        boolean kyoboEnabled = kyoboLinkBuilder.isEnabled();
        return ProfileResponse.from(v, recencyOf(v), coupangEnabled, yes24Enabled, kyoboEnabled, mutualOf(v, viewer));
    }

    /**
     * 관계 신호 — 공통 친구와 「나를 팔로우함」. <b>내 책방에서는 셋 다 뜻이 없어</b> 쿼리 없이 비운다
     * (내가 나를 팔로우할 수 없고, 내 팔로워는 이미 카운트로 보인다).
     */
    private MutualInfo mutualOf(ProfileView v, User viewer) {
        if (v.self()) {
            return MutualInfo.NONE;
        }
        List<UserBrief> names = followRepository
                .findMutualFollowers(viewer.getId(), v.loginId(), PageRequest.of(0, MUTUAL_NAMES)).stream()
                .map(u -> new UserBrief(u.getLoginId(), u.getNickname()))
                .toList();
        long total = names.isEmpty() ? 0 : followRepository.countMutualFollowers(viewer.getId(), v.loginId());
        boolean followsMe = followRepository.existsByFollower_LoginIdAndFollowee_Id(v.loginId(), viewer.getId());
        return new MutualInfo(names, total, followsMe);
    }

    /**
     * 상태필터·정렬 적용된 PUBLIC 책 목록. status/sort 없거나 잘못되면 전체/이름순(관대 파싱).
     * 기본 정렬은 이름순(리포지토리가 title asc로 공급). sort=finished_desc·finished_asc는 완독 시각
     * 정렬 — UI는 완독(FINISHED) 필터에서만 노출하지만 서버는 조합을 강제하지 않는다(완독 시각 없는
     * 책은 뒤로 — null-state가 앞을 오염하지 않게, N-055 정신).
     */
    @GetMapping("/api/profile/books")
    public BooksResponse books(@RequestParam String loginId,
                               @RequestParam(required = false) String status,
                               @RequestParam(required = false) String sort,
                               Principal principal) {
        User viewer = currentUserService.resolve(principal);
        ProfileView v = profileService.profileOf(viewer, loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다"));
        BookStatus filter = parseStatus(status);
        var books = v.books().stream()
                .filter(b -> filter == null || b.getStatus() == filter);
        Comparator<Book> order = parseSort(sort);
        if (order != null) {
            books = books.sorted(order);
        }
        Map<Long, Instant> recency = recencyOf(v);
        List<BookSummary> rows = books
                .map(b -> BookSummary.from(b, v.bookTimes(), recency))
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
        // 드릴다운 패널은 성향 근거 확인용 임시 목록이라 발광을 얹지 않는다(YAGNI) — recency 빈 맵.
        List<BookSummary> rows = books.stream()
                .map(b -> BookSummary.from(b, Map.of(), Map.of()))
                .toList();
        return new BooksResponse(rows);
    }

    /**
     * 책 id → 그 책 여백의 마지막 글 시각.
     *
     * <p><b>팔로우 게이트를 걷었다</b>(2026-08-22). 예전엔 본인·팔로워에게만 채웠는데, 여백 목록
     * 자체가 공개 책이면 누구에게나 열린 지금은 발광만 막아 두면 「글은 보이는데 격자는 안 빛나는」
     * 어긋남이 된다. 방어는 <b>책 가시성</b>이 진다 — {@code v.books()}가 언제나 PUBLIC만
     * 담으므로({@link com.booktimer.profile.ProfileService}) 비공개 책은 여기 닿지 않는다.
     *
     * <p>실은 그 필터를 걷어도 <b>한 겹이 더 있다</b>: 이 맵은 {@code recency.get(b.getId())}로만
     * 읽히고 그 {@code b}는 다시 {@code v.books()}를 순회한 것이라, PUBLIC 집합 밖의 키는 응답에
     * 닿는 경로가 없다. 그래도 쿼리를 좁혀 두는 이유는 <b>안 쓸 행을 읽지 않기 위해서</b>다 —
     * 우연한 이중 방어에 기대 필터를 지우지 말 것.
     */
    private Map<Long, Instant> recencyOf(ProfileView v) {
        List<Long> bookIds = v.books().stream().map(Book::getId).toList();
        if (bookIds.isEmpty()) {
            return Map.of(); // 공개 책이 없으면 물어볼 것도 없다 (null-state — 빈 in 절 회피)
        }
        Map<Long, Instant> recency = new LinkedHashMap<>();
        for (StoryRepository.BookStoryRecency row : storyRepository.recencyByBook(bookIds)) {
            recency.put(row.getBookId(), row.getLastAt());
        }
        return recency;
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

    /**
     * 관대 파싱: finished_desc(완독 최신순)·finished_asc(완독 오래된순)만 인식, 그 외/미지정 → null
     * (= 리포지토리 기본 이름순 유지). 완독 시각이 null인 책은 방향과 무관하게 뒤로 보내고 이름순 tie-break.
     */
    private static Comparator<Book> parseSort(String raw) {
        if (raw == null) return null;
        Comparator<Instant> time = switch (raw.strip().toLowerCase()) {
            case "finished_desc" -> Comparator.reverseOrder();
            case "finished_asc" -> Comparator.naturalOrder();
            default -> null;
        };
        if (time == null) return null;
        return Comparator.comparing(Book::getFinishedAt, Comparator.nullsLast(time))
                .thenComparing(Book::getTitle);
    }

    // ── DTO (Book 엔티티 직렬화 금지 — 평탄 record로 화이트리스트) ──────────

    /**
     * @param lastStoryAt 그 책 여백의 <b>마지막 글 시각</b> — 격자·리스트 발광용. 글이 없거나
     *                    {@code null}이다(팔로우와 무관 — 2026-08-22). 24시간 판정은 클라 순수 함수의 몫이다:
     *                    서버 Clock과 클라 표시가 이중으로 시간을 갖지 않고, 판정이 테스트 가능해진다.
     */
    public record BookSummary(Long id, String title, String author, String coverUrl,
                              String status, long seconds, String purchaseLink,
                              Instant lastStoryAt) {
        static BookSummary from(Book b, Map<Long, Long> times, Map<Long, Instant> recency) {
            return new BookSummary(b.getId(), b.getTitle(), b.getAuthor(), b.getCoverUrl(),
                    b.getStatus().getLabel(), times.getOrDefault(b.getId(), 0L), b.getPurchaseLink(),
                    recency.get(b.getId()));
        }
    }

    public record TagChip(String label, boolean clickable) {}

    public record BooksResponse(List<BookSummary> books) {}

    /** 공통 친구 한 명 — 이름을 그리는 데 필요한 것만(핸들·표시이름). */
    public record UserBrief(String loginId, String nickname) {}

    /**
     * 관계 신호 묶음 — 「○○님 외 N명이 팔로우합니다」와 「나를 팔로우함」.
     *
     * @param mutualFollowers 이름을 보여줄 공통 친구(상한 {@value #MUTUAL_NAMES}명)
     * @param mutualFollowerCount 공통 친구 <b>전체</b> 수 — 「외 N명」은 이 값에서 나온다
     * @param followsMe 이 책방 주인이 나를 팔로우하는가({@code following}과 방향이 반대)
     */
    public record MutualInfo(List<UserBrief> mutualFollowers, long mutualFollowerCount, boolean followsMe) {
        static final MutualInfo NONE = new MutualInfo(List.of(), 0, false);
    }

    public record ProfileResponse(
            String loginId, String nickname, String profileCharacterCode,
            long followerCount, long followingCount,
            boolean following, boolean self,
            String personality, List<TagChip> personalityTags,
            List<BookSummary> books, boolean coupangEnabled, boolean yes24Enabled, boolean kyoboEnabled,
            List<UserBrief> mutualFollowers, long mutualFollowerCount, boolean followsMe) {

        /** ⚠️ coupangEnabled·yes24Enabled·kyoboEnabled는 각 빌더의 isEnabled()로 계산해 전달 — 여기서 false 하드코딩 금지. */
        static ProfileResponse from(ProfileView v, Map<Long, Instant> recency,
                                    boolean coupangEnabled, boolean yes24Enabled, boolean kyoboEnabled,
                                    MutualInfo mutual) {
            List<BookSummary> books = v.books().stream()
                    .map(b -> BookSummary.from(b, v.bookTimes(), recency))
                    .toList();
            List<TagChip> tags = v.personalityTags().stream()
                    .map(t -> new TagChip(t.label(), t.clickable()))
                    .toList();
            return new ProfileResponse(v.loginId(), v.nickname(), v.profileCharacterCode(),
                    v.followerCount(), v.followingCount(),
                    v.following(), v.self(),
                    v.personality(), tags, books, coupangEnabled, yes24Enabled, kyoboEnabled,
                    mutual.mutualFollowers(), mutual.mutualFollowerCount(), mutual.followsMe());
        }
    }
}
