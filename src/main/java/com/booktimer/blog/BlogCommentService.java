package com.booktimer.blog;

import com.booktimer.blog.BlogViews.CommentView;
import com.booktimer.block.BlockService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 문장 댓글(B2a · 계획 2026-11-16-blog-b2 §4.1 · §6.3 · J39 — 평평한 목록 · 고치기 없음 · 쓴 사람만 지우기). WEB1도 이 서비스를
 * 그대로 부른다(허용 목록 · 첫 설정 · 상한 · 걸러내기가 한 곳).
 */
@Service
@Transactional
public class BlogCommentService {

    /** 시트에 싣는 최근 댓글 수. shortcut: 한 문장에 300개를 넘기면 오래된 것이 안 보인다 — 넘는 날 커서를 붙인다. */
    static final int SHEET_MAX = 300;

    private static final String NO_SENTENCE = "문장을 찾을 수 없어요";

    private final BlogPostRepository postRepository;
    private final BlogSentenceRepository sentenceRepository;
    private final BlogCommentRepository commentRepository;
    private final BlogPostService postService;
    private final BlogAccess access;
    private final BlogIdentityService identity;
    private final BlockService blockService;
    private final RateLimitService rateLimitService;

    public BlogCommentService(BlogPostRepository postRepository, BlogSentenceRepository sentenceRepository,
                              BlogCommentRepository commentRepository, BlogPostService postService, BlogAccess access,
                              BlogIdentityService identity, BlockService blockService, RateLimitService rateLimitService) {
        this.postRepository = postRepository;
        this.sentenceRepository = sentenceRepository;
        this.commentRepository = commentRepository;
        this.postService = postService;
        this.access = access;
        this.identity = identity;
        this.blockService = blockService;
        this.rateLimitService = rateLimitService;
    }

    /** 한 문장의 댓글 묶음(§6.3) — 글을 볼 수 있어야(404) · 그 글의 문장 행(산 것 · 죽은 것)이어야(404). 오래된 순. */
    @Transactional(readOnly = true)
    public List<CommentView> list(User viewer, long postId, String sid) {
        BlogPost post = postRepository.findById(postId).filter(p -> postService.readable(viewer, p))
                .orElseThrow(() -> notFound(BlogPostService.NOT_FOUND));
        PostSentences sentences = sentencesOf(post);
        if (!Sid.valid(sid) || sentences.get(sid).isEmpty()) {
            throw notFound(NO_SENTENCE);
        }
        String target = sentences.end(sid);
        List<BlogComment> rows = new ArrayList<>(commentRepository.findForSheet(post.getId(), sentences.group(target),
                viewer.getId(), PageRequest.of(0, SHEET_MAX)));
        Collections.reverse(rows);
        return rows.stream().map(c -> view(c, post, viewer, target)).toList();
    }

    /**
     * 댓글 쓰기(§4.1 순서) — 허용 목록 403 → 첫 설정 409 → 상한 429(실패한 시도도 센다) → 올림 ∧ 가리지 않음 404(주인도) →
     * 글쓴이와 차단 관계 403 → 산 문장 404 → 본문 400(빈 · 1,000자 · 걸러내기).
     */
    public CommentView create(User user, long postId, String sid, String body) {
        access.check(user);
        identity.requireReady(user);
        if (!rateLimitService.allow(RateLimitAction.BLOG_COMMENT, user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "댓글을 너무 자주 달았어요 — 잠시 뒤 다시 해 주세요");
        }
        BlogPost post = postRepository.findById(postId).filter(BlogPost::quotable)
                .orElseThrow(() -> notFound(BlogPostService.NOT_FOUND));
        if (!isAuthor(user, post) && blockService.isBlockedBetween(user, post.getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 글에는 댓글을 달 수 없어요");
        }
        PostSentences sentences = sentencesOf(post);
        boolean live = Sid.valid(sid) && sentences.layout().sidsInOrder().contains(sid)
                && sentences.get(sid).map(BlogSentence::live).orElse(false);
        if (!live) {
            throw notFound(NO_SENTENCE);
        }
        String text = body == null ? "" : ContentFilter.strip(body);
        if (!ContentFilter.hasVisible(text)) {
            throw new IllegalArgumentException("댓글을 적어 주세요");
        }
        if (text.length() > BlogComment.BODY_MAX) {
            throw new IllegalArgumentException("댓글은 1,000자까지 쓸 수 있어요");
        }
        Optional<String> violation = ContentFilter.violation(text, ContentFilter.Kind.COMMENT);
        if (violation.isPresent()) {
            throw new IllegalArgumentException(violation.get());
        }
        BlogComment saved = commentRepository.saveAndFlush(BlogComment.of(post, sid, user, text));
        return view(saved, post, user, sid);
    }

    /** 지우기 — 쓴 사람만(C6 — 글쓴이도 남의 댓글은 못 지운다). 허용 목록과 무관(내 데이터를 지우는 길). */
    public void delete(User user, long commentId) {
        BlogComment c = commentRepository.findById(commentId).orElseThrow(() -> notFound("댓글을 찾을 수 없어요"));
        if (!Objects.equals(c.getUser().getId(), user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 쓴 댓글만 지울 수 있어요");
        }
        commentRepository.delete(c);
    }

    private static CommentView view(BlogComment c, BlogPost post, User viewer, String target) {
        return new CommentView(c.getId(), c.getSid(), c.getUser().getNickname(), isAuthor(c.getUser(), post),
                Objects.equals(c.getUser().getId(), viewer.getId()), c.getBody(), c.getCreatedAt(), !c.getSid().equals(target));
    }

    private static boolean isAuthor(User user, BlogPost post) {
        return Objects.equals(user.getId(), post.getUser().getId());
    }

    private PostSentences sentencesOf(BlogPost post) {
        return new PostSentences(Layout.parse(post.getLayoutJson()), sentenceRepository.findByPostId(post.getId()));
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
