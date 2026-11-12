package com.booktimer.blog;

import com.booktimer.blog.BlogViews.Block;
import com.booktimer.blog.BlogViews.Draft;
import com.booktimer.blog.BlogViews.HangBook;
import com.booktimer.blog.BlogViews.Highlight;
import com.booktimer.blog.BlogViews.QuoteBoxView;
import com.booktimer.blog.BlogViews.MyPostRow;
import com.booktimer.blog.BlogViews.Page;
import com.booktimer.blog.BlogViews.PostRow;
import com.booktimer.blog.BlogViews.PostView;
import com.booktimer.blog.BlogViews.RevView;
import com.booktimer.blog.BlogViews.SentenceView;
import com.booktimer.block.BlockService;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 블로그 글 쓰기 · 지우기 · 읽기(계획 2026-11-11-b1-blog-core §5.1 · §6.4). 올리기는 {@link BlogPublishService}.
 *
 * <p>쓰기 문(만들기 · 저장)은 {@link BlogAccess#check}가 첫 줄이다(§4.8). 읽기 · 지우기는 막지 않는다. 읽기 판정은
 * {@link #readable} — 남에게 보이지 않는 글은 전부 404(존재 비노출).
 */
@Service
@Transactional
public class BlogPostService {

    static final String NOT_FOUND = "글을 찾을 수 없어요";
    static final String STALE = "다른 기기에서 이 글이 바뀌었어요 — 그쪽을 불러와 주세요";
    private static final String BAD_CURSOR = "목록 위치가 올바르지 않아요";

    private final BlogPostRepository postRepository;
    private final BlogSentenceRepository sentenceRepository;
    private final BlogSentenceRevRepository revRepository;
    private final BlogAccess access;
    private final BlockService blockService;
    private final RateLimitService rateLimitService;
    private final QuoteBoxService quoteBoxService;
    private final QuoteBoxRepository quoteBoxRepository;

    public BlogPostService(BlogPostRepository postRepository, BlogSentenceRepository sentenceRepository,
                           BlogSentenceRevRepository revRepository, BlogAccess access, BlockService blockService,
                           RateLimitService rateLimitService, QuoteBoxService quoteBoxService,
                           QuoteBoxRepository quoteBoxRepository) {
        this.postRepository = postRepository;
        this.sentenceRepository = sentenceRepository;
        this.revRepository = revRepository;
        this.access = access;
        this.blockService = blockService;
        this.rateLimitService = rateLimitService;
        this.quoteBoxService = quoteBoxService;
        this.quoteBoxRepository = quoteBoxRepository;
    }

    // ── 쓰기 ─────────────────────────────────────────────────────────────────

    /** 첫 저장 때 행(J6 · §4.10). 허용 목록 → 상한(429) → 빈 글 · 길이(400). */
    public BlogPost create(User user, String title, String body) {
        access.check(user);
        if (!rateLimitService.allow(RateLimitAction.BLOG_DRAFT_CREATE, user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "글을 너무 자주 만들었어요 — 잠시 뒤 다시 해 주세요");
        }
        return postRepository.save(BlogPost.draft(user, title, body));
    }

    /** 초안 저장 — 잠그지 않는다(§4.11). 소유 404가 검증 400 · 판 대조 409보다 먼저. */
    public BlogPost save(User user, long id, String title, String body, int revision) {
        access.check(user);
        BlogPost post = postRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        try {
            post.saveDraft(title, body, revision);
        } catch (BlogPost.StaleBlogDraftException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STALE);
        }
        postRepository.flush(); // 응답의 updatedAt
        return post;
    }

    /** 글 지우기 — 허용 목록과 무관(내 데이터를 지우는 길은 늘 연다 · §4.8). 글 행 잠금이 첫 DB 문장(§4.11). */
    public void delete(User user, long id) {
        BlogPost post = postRepository.findForUpdate(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        quoteBoxRepository.deleteByOwner(QuoteBox.Owner.POST, id); // 이 글이 가진 박스(FK 없음 — 안 지우면 고아) · 남의 박스는 그대로
        revRepository.deleteBySentencePost(post);  // blog_sentence_rev.sentence_id FK — 문장보다 앞
        sentenceRepository.deleteByPost(post);     // blog_sentence.post_id FK — 글보다 앞
        postRepository.delete(post);
    }

    // ── 읽기 ─────────────────────────────────────────────────────────────────

    /** 전체 목록 — 키셋 커서 {@code "{publishedAt ISO-8601}_{id}"}(불투명 · 깨지면 400). */
    @Transactional(readOnly = true)
    public Page list(User viewer, String cursor, Integer limit) {
        int n = limit == null ? 20 : limit;
        if (n < 1 || n > 50) {
            throw new IllegalArgumentException(BAD_CURSOR);
        }
        PageRequest page = PageRequest.of(0, n + 1);
        List<BlogPost> rows;
        if (cursor == null || cursor.isEmpty()) {
            rows = postRepository.findFeed(viewer.getId(), page);
        } else {
            int cut = cursor.lastIndexOf('_');
            try {
                rows = postRepository.findFeedAfter(viewer.getId(), Instant.parse(cursor.substring(0, cut)),
                        Long.parseLong(cursor.substring(cut + 1)), page);
            } catch (DateTimeParseException | NumberFormatException | StringIndexOutOfBoundsException e) {
                throw new IllegalArgumentException(BAD_CURSOR);
            }
        }
        List<BlogPost> shown = rows.subList(0, Math.min(n, rows.size()));
        List<PostRow> posts = shown.stream().map(p -> new PostRow(p.getId(), p.getTitle(), p.getUser().getNickname(),
                p.getPublishedAt(), isOwner(viewer, p))).toList();
        String next = null;
        if (rows.size() > n) {
            BlogPost last = shown.get(shown.size() - 1);
            next = last.getPublishedAt() + "_" + last.getId();
        }
        return new Page(posts, next);
    }

    @Transactional(readOnly = true)
    public List<MyPostRow> mine(User user) {
        return postRepository.findTop200ByUserOrderByUpdatedAtDescIdDesc(user).stream()
                .map(p -> new MyPostRow(p.getId(), p.getTitle(), p.getDraftTitle(), p.getPublishedAt(),
                        p.getRepublishedAt(), p.getUpdatedAt()))
                .toList();
    }

    /** 글 하나 — {@code s}는 한 문장 주소(§6.4 — 이 글의 문장이 아니거나 꼴이 틀리면 강조 없음). */
    @Transactional(readOnly = true)
    public PostView view(User viewer, long id, String s) {
        BlogPost post = postRepository.findById(id).filter(p -> readable(viewer, p))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND));
        return view(viewer, post, s);
    }

    /** 「예전 문장 보기」 — 산 문장만 · 지금 판보다 작은 판 · 최근 판 먼저(J38). */
    @Transactional(readOnly = true)
    public List<RevView> revs(User viewer, String sid) {
        BlogSentence sentence = (Sid.valid(sid) ? sentenceRepository.findBySid(sid) : Optional.<BlogSentence>empty())
                .filter(BlogSentence::live)
                .filter(x -> readable(viewer, x.getPost()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문장을 찾을 수 없어요"));
        return revRepository.findBySentenceAndRevLessThanOrderByRevDesc(sentence, sentence.getRev()).stream()
                .map(r -> new RevView(r.getRev(), r.getText(), r.getCreatedAt())).toList();
    }

    /** 읽기 판정(§6.4) — 주인은 늘(초안 · 가림 포함). 남은 올림 ∧ 가리지 않음 ∧ 차단 관계 아님. */
    boolean readable(User viewer, BlogPost post) {
        if (isOwner(viewer, post)) {
            return true;
        }
        return post.getPublishedAt() != null && post.getHiddenAt() == null
                && !blockService.isBlockedBetween(viewer, post.getUser());
    }

    PostView view(User viewer, BlogPost post, String s) {
        boolean mine = isOwner(viewer, post);
        PostSentences sentences = new PostSentences(Layout.parse(post.getLayoutJson()), sentenceRepository.findByPostId(post.getId()));
        List<QuoteBoxView> quotes = quoteBoxService.views(QuoteBox.Owner.POST, post.getId(), viewer);
        Map<Integer, QuoteBoxView> byLabel = quotes.stream().collect(Collectors.toMap(QuoteBoxView::label, v -> v));
        List<Block> blocks = new ArrayList<>();
        for (Layout.Item item : sentences.layout().items()) {
            if (item.p() == null) { // 배치의 박스 라벨에 박스가 없으면 불변식 위반 — 500(덮지 않는다 · DF-03)
                blocks.add(new Block("Q", List.of(), Optional.ofNullable(byLabel.get(item.q().intValue())).orElseThrow()));
                continue;
            }
            List<SentenceView> views = new ArrayList<>();
            for (int l = 0; l < item.p().size(); l++) {
                List<String> line = item.p().get(l);
                for (int i = 0; i < line.size(); i++) {
                    BlogSentence row = sentences.get(line.get(i)).orElseThrow();
                    boolean br = i == line.size() - 1 && l < item.p().size() - 1;
                    views.add(new SentenceView(row.getSid(), row.getText(), row.getRev() > 1, br));
                }
            }
            blocks.add(new Block("P", views, null));
        }
        Draft draft = mine ? new Draft(post.getDraftTitle(), post.getDraftBody(), post.getDraftRevision(), post.getUpdatedAt(),
                quotes) : null;
        HangBook hangBook = post.getPublishedAt() == null ? null : quoteBoxService.hangBook(viewer, post, sentences.layout());
        return new PostView(post.getId(), post.getTitle(), post.getUser().getNickname(), mine, post.getPublishedAt(),
                post.getRepublishedAt(), blocks, highlight(sentences, s), hangBook, draft);
    }

    private static final Pattern RANGE = Pattern.compile("^([0-9A-Za-z]{8})-([0-9A-Za-z]{8})$");

    /** 문장 주소(§6.4) — 한 문장은 나뉜 조각을 더하지 않는다(J72) · 구간 {@code a-b}는 둘 다 이 글의 행일 때만(§15.4 ⑷). */
    private static Highlight highlight(PostSentences sentences, String s) {
        Matcher range = s == null ? null : RANGE.matcher(s);
        if (range != null && range.matches()) return range(sentences, range.group(1), range.group(2));
        if (!Sid.valid(s)) return null;
        BlogSentence row = sentences.get(s).orElse(null);
        if (row == null) return null;
        if (row.live()) return new Highlight(row.getRev() > 1 ? "EDITED" : "LIVE", List.of(s));
        return sentences.resolve(s).map(t -> new Highlight("MERGED", List.of(t.getSid())))
                .orElseGet(() -> new Highlight("DELETED", List.of()));
    }

    private static Highlight range(PostSentences sentences, String a, String b) {
        if (sentences.get(a).isEmpty() || sentences.get(b).isEmpty()) return null;
        QuoteRange.Current cur = QuoteRange.current(List.of(a, b), sentences, null);
        if (cur.allDeleted()) return new Highlight("DELETED", List.of());
        if (cur.viaMerge()) return new Highlight("MERGED", cur.sids());
        boolean edited = cur.sids().stream().anyMatch(x -> sentences.get(x).orElseThrow().getRev() > 1);
        return new Highlight(edited ? "EDITED" : "LIVE", cur.sids());
    }

    private static boolean isOwner(User viewer, BlogPost post) {
        return Objects.equals(viewer.getId(), post.getUser().getId());
    }
}
