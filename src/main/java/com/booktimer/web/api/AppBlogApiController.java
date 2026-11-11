package com.booktimer.web.api;

import com.booktimer.blog.BlogIdentityService;
import com.booktimer.blog.BlogPost;
import com.booktimer.blog.BlogPostService;
import com.booktimer.blog.BlogPublishService;
import com.booktimer.blog.BlogViews.MeView;
import com.booktimer.blog.BlogViews.MyPostRow;
import com.booktimer.blog.BlogViews.Page;
import com.booktimer.blog.BlogViews.PostView;
import com.booktimer.blog.BlogViews.QuoteBoxView;
import com.booktimer.blog.BlogViews.RevView;
import com.booktimer.blog.QuoteBoxService;
import com.booktimer.blog.QuoteBoxService.QuoteRequest;
import com.booktimer.security.CurrentUserService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

/**
 * 앱 「책생글」 블로그 API(책생글 B1a — 계획 2026-11-11-b1-blog-core §5.1) — {@code /api/app/**} Bearer 체인(토큰 없으면 401).
 *
 * <p>에러 계약은 {@link AppMarginApiController}와 같다: IAE 문구가 그대로 400 본문, {@link ResponseStatusException}의 사유는
 * 평문 본문(403 · 404 · 409 · 429). 쓰기 문(초안 만들기 · 저장 · 올리기 · blog/me)은 허용 목록이 맨 먼저다(§4.8 —
 * REQ-244가 경로 집합으로 잠근다). 경로에 {@code /share} 마디를 두지 않는다(REQ-40 가드).
 */
@RestController
public class AppBlogApiController {

    private final CurrentUserService currentUserService;
    private final BlogIdentityService identityService;
    private final BlogPostService postService;
    private final BlogPublishService publishService;
    private final QuoteBoxService quoteBoxService;

    public AppBlogApiController(CurrentUserService currentUserService, BlogIdentityService identityService,
                                BlogPostService postService, BlogPublishService publishService,
                                QuoteBoxService quoteBoxService) {
        this.currentUserService = currentUserService;
        this.identityService = identityService;
        this.postService = postService;
        this.publishService = publishService;
        this.quoteBoxService = quoteBoxService;
    }

    @GetMapping("/api/app/blog/me")
    public MeView me(Principal principal) {
        return identityService.me(currentUserService.resolve(principal));
    }

    /** @return 200 / 403 허용 목록 / 400 이름 · 동의 */
    @PutMapping("/api/app/blog/me")
    public MeView confirm(@RequestBody MeRequest request, Principal principal) {
        return identityService.confirm(currentUserService.resolve(principal), request.name(), request.agreeTerms());
    }

    /** 첫 저장 때 행(J6). @return 201 / 403 / 429 / 400 */
    @PostMapping("/api/app/blog/posts")
    public ResponseEntity<PostView> create(@RequestBody DraftRequest request, Principal principal) {
        User user = currentUserService.resolve(principal);
        BlogPost post = postService.create(user, request.draftTitle(), request.draftBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.view(user, post.getId(), null));
    }

    /** @return 200 / 400 깨진 커서 · limit 1~50 밖 */
    @GetMapping("/api/app/blog/posts")
    public Page list(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit,
                     Principal principal) {
        return postService.list(currentUserService.resolve(principal), cursor, limit);
    }

    @GetMapping("/api/app/blog/posts/mine")
    public List<MyPostRow> mine(Principal principal) {
        return postService.mine(currentUserService.resolve(principal));
    }

    /** @return 200 / 404 남의 초안 · 가린 글 · 차단 관계 · 없음 */
    @GetMapping("/api/app/blog/posts/{id}")
    public PostView get(@PathVariable long id, @RequestParam(required = false) String s, Principal principal) {
        return postService.view(currentUserService.resolve(principal), id, s);
    }

    /** @return 200 / 403 / 404 / 400 / 409 낡은 판 */
    @PutMapping("/api/app/blog/posts/{id}")
    public Saved save(@PathVariable long id, @RequestBody SaveRequest request, Principal principal) {
        BlogPost post = postService.save(currentUserService.resolve(principal), id, request.draftTitle(), request.draftBody(),
                request.revision());
        return new Saved(post.getDraftRevision(), post.getUpdatedAt());
    }

    /** @return 204 / 404 — 허용 목록과 무관 */
    @DeleteMapping("/api/app/blog/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, Principal principal) {
        postService.delete(currentUserService.resolve(principal), id);
        return ResponseEntity.noContent().build();
    }

    /** @return 200 / 400 / 403 / 404 / 409 낡은 판 · 첫 설정 전 */
    @PostMapping("/api/app/blog/posts/{id}/publish")
    public PostView publish(@PathVariable long id, @RequestBody PublishRequest request, Principal principal) {
        return publishService.publish(currentUserService.resolve(principal), id, request.revision());
    }

    /** 글에 인용 박스(BLOG · MARGIN · BOOK — §6.6). @return 201 / 403 허용 목록 · 차단 / 404 / 400 / 409 쪽지 판 */
    @PostMapping("/api/app/blog/posts/{id}/quotes")
    public ResponseEntity<QuoteBoxView> createQuote(@PathVariable long id, @RequestBody QuoteRequest request,
                                                    Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(quoteBoxService.createForPost(currentUserService.resolve(principal), id, request));
    }

    /** 최신으로 반영. @return 200 / 403 허용 목록 · 차단 / 404 남의 박스 / 400 종류 / 409 원문 지움 · 가림 · 걸침 · 1,000자 */
    @PostMapping("/api/app/blog/quotes/{id}/refresh")
    public QuoteBoxView refreshQuote(@PathVariable long id, Principal principal) {
        return quoteBoxService.refresh(currentUserService.resolve(principal), id);
    }

    /** 여백 박스 다시 고르기. @return 200 / 403 / 404 / 400 종류 · 범위 / 409 원 쪽지 지움 · 판 */
    @PutMapping("/api/app/blog/quotes/{id}/source")
    public QuoteBoxView repickQuote(@PathVariable long id, @RequestBody SourceRequest request, Principal principal) {
        return quoteBoxService.repick(currentUserService.resolve(principal), id, request.revision(), request.from(), request.to());
    }

    /** @return 200 / 404 없음 · 지움 · 합침 · 보이지 않는 글(J38) */
    @GetMapping("/api/app/blog/sentences/{sid}/revs")
    public List<RevView> revs(@PathVariable String sid, Principal principal) {
        return postService.revs(currentUserService.resolve(principal), sid);
    }

    /** IAE 문구가 그대로 400 본문(text/plain — 반사 XSS 방지, charset 명시는 한글 깨짐 방지). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> plainText(ResponseStatusException e) {
        return AppAuthApiController.plainTextError(e);
    }

    // ── 몸 (필드 이름 = 앱 types.ts의 단일 출처) ─────────────────────────────

    /** @param agreeTerms 래퍼 — 빠지면 400 「커뮤니티 규칙에 동의해 주세요」 */
    public record MeRequest(String name, Boolean agreeTerms) {}

    /** 둘 다 빠져도 된다(null = 빈 글자) — 둘 다 비면 400. */
    public record DraftRequest(String draftTitle, String draftBody) {}

    public record SaveRequest(String draftTitle, String draftBody, int revision) {}

    public record PublishRequest(int revision) {}

    /** @param revision 원래 쪽지의 판 — 다르면 409 */
    public record SourceRequest(int revision, int from, int to) {}

    public record Saved(int revision, Instant updatedAt) {}
}
