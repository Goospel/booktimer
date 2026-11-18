package com.booktimer.blog;

import com.booktimer.blog.BlogReport.Status;
import com.booktimer.blog.BlogReport.Target;
import com.booktimer.blog.Moderation.Action;
import com.booktimer.blog.Moderation.Kind;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 운영자 처리(B2b · 계획 §4.5 · §6.5 · 약관 ⑥⑦⑧) — 관리자 화면(ADMIN · CSRF 폼)만 부른다. 가리기 단위 = 글 · 댓글 · 인용 박스 하나
 * (J77). 바뀐 전이마다 처리 기록 한 줄(같은 트랜잭션 — 처리방침 5절 「그 처리 기록 … 3년」) + 게시자 알림. 잠금은 단위 행(글 · 박스)이
 * 그 트랜잭션의 첫 DB 문장이다 · 댓글은 고치기가 없어 덮어쓰기 0이라 잠그지 않는다.
 *
 * <p>입력 오류(없는 단위 · 400 전이 · 신고와 무관한 단위)는 {@link IllegalArgumentException} — 관리자 컨트롤러가 flash로 보인다.
 */
@Service
@Transactional
public class BlogModerationService {

    public enum Unit { POST, COMMENT, QUOTE }

    static final String BAD_UNIT = "신고 대상과 다른 게시물이에요 — 그 게시물은 id로 처리 폼을 쓰세요";
    private static final String NO_UNIT = "게시물을 찾을 수 없어요";

    private final BlogPostRepository postRepository;
    private final BlogCommentRepository commentRepository;
    private final QuoteBoxRepository quoteBoxRepository;
    private final BlogReportRepository reportRepository;
    private final BlogModerationLogRepository logRepository;
    private final UserRepository userRepository;
    private final StoryRepository storyRepository;
    private final BlogSentenceRepository sentenceRepository;
    private final BlogPostService postService;
    private final BlogNotifier notifier;
    private final Clock clock;

    BlogModerationService(BlogPostRepository postRepository, BlogCommentRepository commentRepository,
                          QuoteBoxRepository quoteBoxRepository, BlogReportRepository reportRepository,
                          BlogModerationLogRepository logRepository, UserRepository userRepository, StoryRepository storyRepository,
                          BlogSentenceRepository sentenceRepository, BlogPostService postService, BlogNotifier notifier, Clock clock) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.quoteBoxRepository = quoteBoxRepository;
        this.reportRepository = reportRepository;
        this.logRepository = logRepository;
        this.userRepository = userRepository;
        this.storyRepository = storyRepository;
        this.sentenceRepository = sentenceRepository;
        this.postService = postService;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** 신고와 무관하게 단위 하나를 처리한다(「이 글을 인용한 박스」 · 문의로 받은 쪽지 박스 등). @return 바뀌었나 */
    public boolean apply(Unit unit, long id, Action action, String note) {
        return apply(unit, id, action, note, null);
    }

    private boolean apply(Unit unit, long id, Action action, String note, Long reportId) {
        Instant now = now();
        switch (unit) {
            case POST -> {
                BlogPost p = postRepository.findForUpdateById(id).orElseThrow(() -> new IllegalArgumentException(NO_UNIT));
                User poster = p.getUser();
                poster.getEmail(); // 아래 일괄 지우기의 문맥 비움 전에 읽어 둔다(지연 프록시)
                String title = p.getTitle(); // REMOVE 메일은 지우기 전 제목(REQ-334)
                Moderation.Next n = Moderation.next(p.getHiddenAt(), p.getHideKind(), p.getHideNote(), action, note, now);
                p.moderate(n);
                if (n.kind() == Kind.REMOVED) {
                    p.markRemoved(); // 일괄 지우기보다 먼저 — 뒤면 clearAutomatically에 버려진다(§4.5)
                    postService.wipeContent(p);
                }
                return done(n, unit, id, action, note, reportId, poster, "글", title);
            }
            case COMMENT -> {
                BlogComment c = commentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException(NO_UNIT));
                Moderation.Next n = Moderation.next(c.getHiddenAt(), c.getHideKind(), c.getHideNote(), action, note, now);
                c.moderate(n);
                if (n.kind() == Kind.REMOVED) c.markRemoved();
                return done(n, unit, id, action, note, reportId, c.getUser(), "댓글", c.getPost().getTitle());
            }
            default -> {
                QuoteBox q = quoteBoxRepository.findForUpdate(id).orElseThrow(() -> new IllegalArgumentException(NO_UNIT));
                User poster;
                String title = null;
                if (q.getOwnerType() == QuoteBox.Owner.POST) {
                    Optional<BlogPost> owner = postRepository.findById(q.getOwnerId());
                    poster = owner.map(BlogPost::getUser).orElse(null);
                    title = owner.map(BlogPost::getTitle).orElse(null);
                } else {
                    poster = storyRepository.findById(q.getOwnerId()).map(s -> s.getUser()).orElse(null);
                }
                Moderation.Next n = Moderation.next(q.getHiddenAt(), q.getHideKind(), q.getHideNote(), action, note, now);
                q.moderate(n);
                if (n.kind() == Kind.REMOVED) q.markRemoved();
                return done(n, unit, id, action, note, reportId, poster, "인용", title);
            }
        }
    }

    private boolean done(Moderation.Next n, Unit unit, long id, Action action, String note, Long reportId, User poster,
                         String label, String title) {
        if (!n.changed()) {
            return false;
        }
        // 처리 기록의 note = 그때의 hide_note(다시 보이기는 운영자가 적은 글 — 열에선 지워진다)
        logRepository.save(BlogModerationLog.of(unit.name(), id, action.name(), reportId, action == Action.RESTORE ? note : n.note()));
        if (poster != null) {
            Moderation.View v = Moderation.of(n.hiddenAt(), n.kind(), n.note());
            notifier.poster(poster, action, label, v == null ? null : v.until(), title, n.note());
        }
        return true;
    }

    /**
     * 신고 처리 — {@code action}이 null이면 조치 없이. 조치할 단위는 <b>신고 대상에 묶여야 한다</b>(글 대상: 그 글 · 그 글이 가진 박스 /
     * 댓글 대상: 그 댓글 — 신고자가 심은 id로 무관한 게시물을 가리며 신고를 닫는 길 0). 같은 대상의 OPEN 신고 전부 닫고 신고자마다 알린다.
     */
    public void resolve(long reportId, Action action, Unit unit, Long unitId, String note) {
        BlogReport r = reportRepository.findById(reportId).orElseThrow(() -> new IllegalArgumentException("신고를 찾을 수 없어요"));
        if (r.getStatus() != Status.OPEN) {
            throw new IllegalArgumentException("이미 처리한 신고예요");
        }
        boolean changed = false;
        String result = "NONE";
        if (action != null) {
            requireBound(r, unit, unitId);
            changed = apply(unit, unitId, action, note, r.getId());
            result = action == Action.REMOVE ? "REMOVED" : action == Action.RESTORE ? "NONE" : "HIDDEN";
        }
        if (!changed) {
            logRepository.save(BlogModerationLog.of(r.getTargetType().name(), r.getTargetId(), "NONE", r.getId(), null));
        }
        Instant now = now();
        for (BlogReport open : reportRepository.findByTargetTypeAndTargetIdAndStatus(r.getTargetType(), r.getTargetId(), Status.OPEN)) {
            open.resolve(result, now);
            notifier.reporter(open.getReporter(), open.getTargetType(), result);
        }
    }

    private void requireBound(BlogReport r, Unit unit, Long unitId) {
        boolean bound = unit != null && unitId != null && switch (r.getTargetType()) {
            case POST -> (unit == Unit.POST && unitId.equals(r.getTargetId()))
                    || (unit == Unit.QUOTE && quoteBoxRepository.findById(unitId)
                    .filter(q -> q.getOwnerType() == QuoteBox.Owner.POST && q.getOwnerId().equals(r.getTargetId())).isPresent());
            case COMMENT -> unit == Unit.COMMENT && unitId.equals(r.getTargetId());
        };
        if (!bound) {
            throw new IllegalArgumentException(BAD_UNIT);
        }
    }

    /** 글쓰기 · 댓글 막기(약관 ⑧) — 바뀐 때만 처리 기록 한 줄. */
    public void restrict(long userId, boolean on) {
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없어요"));
        boolean changed = on ? u.restrictBlog(now()) : u.liftBlogRestriction();
        if (changed) {
            logRepository.save(BlogModerationLog.of("USER", userId, on ? "RESTRICT" : "UNRESTRICT", null, null));
        }
    }

    /** 기간(30일) 끝난 임시조치 — 운영자가 확인해 지운다(사용자 결정 2 · 서버가 저절로 지우지 않는다). */
    @Transactional(readOnly = true)
    public long expiredCount(Instant now) {
        Instant cutoff = cutoff(now);
        return postRepository.countByHideKindAndHiddenAtLessThanEqual(Kind.TEMP, cutoff)
                + commentRepository.countByHideKindAndHiddenAtLessThanEqual(Kind.TEMP, cutoff)
                + quoteBoxRepository.countByHideKindAndHiddenAtLessThanEqual(Kind.TEMP, cutoff);
    }

    /** 관리자 화면 「기간 끝난 임시조치」 — 단위마다 오래된 순 100개(ponytail: 넘으면 페이지). */
    @Transactional(readOnly = true)
    public List<Expired> expired(Instant now) {
        Instant cutoff = cutoff(now);
        List<Expired> out = new ArrayList<>();
        postRepository.findTop100ByHideKindAndHiddenAtLessThanEqualOrderByHiddenAt(Kind.TEMP, cutoff)
                .forEach(p -> out.add(new Expired(Unit.POST.name(), p.getId(), p.getHiddenAt())));
        commentRepository.findTop100ByHideKindAndHiddenAtLessThanEqualOrderByHiddenAt(Kind.TEMP, cutoff)
                .forEach(c -> out.add(new Expired(Unit.COMMENT.name(), c.getId(), c.getHiddenAt())));
        quoteBoxRepository.findTop100ByHideKindAndHiddenAtLessThanEqualOrderByHiddenAt(Kind.TEMP, cutoff)
                .forEach(q -> out.add(new Expired(Unit.QUOTE.name(), q.getId(), q.getHiddenAt())));
        return out;
    }

    @Transactional(readOnly = true)
    public long openCount() {
        return reportRepository.countByStatus(Status.OPEN);
    }

    public record Expired(String unit, long id, Instant hiddenAt) {
    }

    /**
     * 관리자 신고함의 블로그 행 — 열린 것 먼저 · 최근 100개. 대상이 지워졌어도 대상 글쓴이(신고할 때 적은 id)와 막힘 여부를 보인다.
     * detail 접두는 서버가 그 글의 것인지 확인해 저장했으므로 믿고 풀어 보인다.
     */
    @Transactional(readOnly = true)
    public List<AdminRow> adminRows() {
        return reportRepository.findTop100ByOrderByStatusAscCreatedAtDescIdDesc().stream().map(this::row).toList();
    }

    public record AdminRow(long id, Instant createdAt, String reason, String status, String result, String targetType, long targetId,
                           boolean targetExists, String excerpt, String prefixText, String detail, Long authorId, String authorName,
                           boolean authorRestricted, String defaultUnit, long defaultUnitId, List<Long> quotingBoxIds) {
    }

    private AdminRow row(BlogReport r) {
        boolean exists;
        String excerpt;
        List<Long> quoting = List.of();
        if (r.getTargetType() == Target.POST) {
            Optional<BlogPost> p = postRepository.findById(r.getTargetId());
            exists = p.isPresent();
            excerpt = p.map(BlogPost::getTitle).orElse(null);
            quoting = quoteBoxRepository.findBySourcePostId(r.getTargetId()).stream().map(QuoteBox::getId).toList();
        } else {
            Optional<BlogComment> c = commentRepository.findById(r.getTargetId());
            exists = c.isPresent();
            excerpt = c.map(BlogComment::getBody).orElse(null);
        }
        String prefixText = null;
        String unit = r.getTargetType() == Target.POST ? Unit.POST.name() : Unit.COMMENT.name();
        long unitId = r.getTargetId();
        Optional<BlogReportService.Prefix> prefix = parsed(r.getDetail());
        if (prefix.isPresent() && prefix.get().quoteId() != null) {
            unit = Unit.QUOTE.name();
            unitId = prefix.get().quoteId();
            prefixText = "인용 #" + unitId + ": " + quoteBoxRepository.findById(unitId).map(QuoteBox::getText).orElse("대상 없음");
        } else if (prefix.isPresent()) {
            prefixText = "문장: " + sentenceRepository.findBySid(prefix.get().sid())
                    .filter(s -> s.getPost().getId().equals(r.getTargetId())).map(BlogSentence::getText).orElse("대상 없음");
        }
        Optional<User> author = r.getTargetUserId() == null ? Optional.empty() : userRepository.findById(r.getTargetUserId());
        return new AdminRow(r.getId(), r.getCreatedAt(), r.getReason().getLabel(), r.getStatus().name(), r.getResult(),
                r.getTargetType().name(), r.getTargetId(), exists, excerpt, prefixText, r.getDetail(),
                author.map(User::getId).orElse(null), author.map(User::getNickname).orElse("탈퇴한 사용자"),
                author.map(u -> u.getBlogRestrictedAt() != null).orElse(false), unit, unitId, quoting);
    }

    private static Optional<BlogReportService.Prefix> parsed(String detail) {
        try {
            return BlogReportService.prefix(detail);
        } catch (IllegalArgumentException e) {
            return Optional.empty(); // 저장된 접두는 확인을 지났다 — 꼴이 틀린 옛 행이 있어도 화면은 연다
        }
    }

    private static Instant cutoff(Instant now) {
        return now.minus(Duration.ofDays(Moderation.TEMP_DAYS));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
