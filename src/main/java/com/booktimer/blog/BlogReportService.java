package com.booktimer.blog;

import com.booktimer.blog.BlogReport.Status;
import com.booktimer.blog.BlogReport.Target;
import com.booktimer.blog.BlogViews.MyReportRow;
import com.booktimer.report.ReportReason;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 블로그 신고(B2b · 계획 2026-11-16-blog-b2 §4.4 · J50 · J94 · C7). 대상은 글 · 댓글 둘 — 문장 · 박스는 글 대상에 detail 접두로.
 * 신고하면 그 대상이 신고자 화면에서 곧바로 빠진다(목록 · 글 · 댓글 술어 — {@code BlogPostRepository} · {@code BlogPostService} ·
 * {@code BlogCommentRepository}). WEB1도 이 서비스를 그대로 부른다.
 */
@Service
@Transactional
public class BlogReportService {

    static final String BAD_PREFIX = "신고할 문장이나 인용을 찾을 수 없어요 — 글을 다시 불러와 주세요";
    private static final Pattern SID_PREFIX = Pattern.compile("sid:([0-9A-Za-z]{8})");
    private static final Pattern QUOTE_PREFIX = Pattern.compile("quote:([1-9][0-9]{0,17})");

    private final BlogAccess access;
    private final RateLimitService rateLimitService;
    private final BlogReportRepository reportRepository;
    private final BlogTargets targets;
    private final BlogSentenceRepository sentenceRepository;
    private final QuoteBoxRepository quoteBoxRepository;
    private final BlogNotifier notifier;

    BlogReportService(BlogAccess access, RateLimitService rateLimitService, BlogReportRepository reportRepository,
                      BlogTargets targets, BlogSentenceRepository sentenceRepository, QuoteBoxRepository quoteBoxRepository,
                      BlogNotifier notifier) {
        this.access = access;
        this.rateLimitService = rateLimitService;
        this.reportRepository = reportRepository;
        this.targets = targets;
        this.sentenceRepository = sentenceRepository;
        this.quoteBoxRepository = quoteBoxRepository;
        this.notifier = notifier;
    }

    /**
     * §4.4 순서 — 허용 목록 403(막힌 사람도 신고는 된다) → 상한 429 → 종류 · detail 길이 400 → <b>이미 신고했으면 그대로 끝(멱등 —
     * 대상 판정보다 먼저: 신고한 대상은 신고자에게서 빠지므로 뒤에 두면 두 번째가 404)</b> → 공개 대상 404 → 내 것 400 → 접두 400 →
     * 저장 → 열린 신고가 0 → 1이면 운영자 메일.
     *
     * @param reason {@link ReportReason} 이름 — 틀리면 OTHER(사유 입력 실수로 신고가 막히지 않게)
     */
    public void report(User user, Target type, long targetId, String reason, String detail) {
        access.checkListed(user);
        if (!rateLimitService.allow(RateLimitAction.REPORT, user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "신고를 너무 자주 했어요 — 잠시 뒤 다시 해 주세요");
        }
        if (type == null) {
            throw new IllegalArgumentException("신고할 대상을 골라 주세요");
        }
        String d = detail == null || detail.isBlank() ? null : detail.strip();
        if (d != null && d.length() > BlogReport.DETAIL_MAX) {
            throw new IllegalArgumentException("부가 설명은 500자까지 쓸 수 있어요");
        }
        if (reportRepository.existsByReporterAndTargetTypeAndTargetId(user, type, targetId)) {
            return;
        }
        User author = targets.publicAuthor(type, targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고할 대상을 찾을 수 없어요"));
        if (Objects.equals(author.getId(), user.getId())) {
            throw new IllegalArgumentException("내 글이나 댓글은 신고할 수 없어요");
        }
        Optional<Prefix> prefix = prefix(d);
        if (prefix.isPresent() && (type != Target.POST || !belongs(prefix.get(), targetId))) {
            throw new IllegalArgumentException(BAD_PREFIX);
        }
        reportRepository.saveAndFlush(BlogReport.of(user, type, targetId, author.getId(), ReportReason.from(reason), d));
        if (reportRepository.countByStatus(Status.OPEN) == 1) {
            notifier.newReport(1);
        }
    }

    /** 내 신고(신청인 통지 — U-154 ②) — 남의 신고 0 · 부가 설명은 싣지 않는다. */
    @Transactional(readOnly = true)
    public List<MyReportRow> mine(User user) {
        return reportRepository.findTop200ByReporterOrderByCreatedAtDescIdDesc(user).stream()
                .map(r -> new MyReportRow(r.getId(), r.getTargetType().name(), r.getTargetId(), r.getReason().name(),
                        r.getStatus().name(), r.getResult(), r.getCreatedAt(), r.getResolvedAt()))
                .toList();
    }

    /** 접두가 그 글의 것인가 — 문장은 그 글의 행(산 것 · 묘비 모두) · 박스는 그 글이 가진 박스(남의 쪽지 박스 글이 관리자 화면에 실리는 길 0). */
    private boolean belongs(Prefix p, long postId) {
        if (p.sid() != null) {
            return sentenceRepository.findBySid(p.sid()).filter(s -> s.getPost().getId() == postId).isPresent();
        }
        return quoteBoxRepository.findById(p.quoteId())
                .filter(q -> q.getOwnerType() == QuoteBox.Owner.POST && q.getOwnerId() == postId).isPresent();
    }

    /** detail 첫 줄의 접두 — 문장 sid 또는 박스 id 하나. */
    record Prefix(String sid, Long quoteId) {
    }

    /**
     * detail 문법(§4.4): {@code 접두 [ "\n" 사용자 글 ] | 사용자 글}. {@code sid:} · {@code quote:}로 시작하면 첫 줄 끝까지가 접두다.
     *
     * @throws IllegalArgumentException 접두로 시작하는데 꼴이 틀림
     */
    static Optional<Prefix> prefix(String detail) {
        if (detail == null || !(detail.startsWith("sid:") || detail.startsWith("quote:"))) {
            return Optional.empty();
        }
        int nl = detail.indexOf('\n');
        String head = nl < 0 ? detail : detail.substring(0, nl);
        Matcher sid = SID_PREFIX.matcher(head);
        if (sid.matches()) {
            return Optional.of(new Prefix(sid.group(1), null));
        }
        Matcher quote = QUOTE_PREFIX.matcher(head);
        if (quote.matches()) {
            return Optional.of(new Prefix(null, Long.parseLong(quote.group(1))));
        }
        throw new IllegalArgumentException(BAD_PREFIX);
    }
}
