package com.booktimer.blog;

import com.booktimer.blog.BlogViews.PostView;
import com.booktimer.security.RateLimitAction;
import com.booktimer.security.RateLimitService;
import com.booktimer.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 올리기(계획 2026-11-11-b1-blog-core §6.3) — 서버 초안을 문장으로 나누고 옛 판과 견줘 주소를 잇는다(D4 · D5 · J11~J14).
 * 앞에서 막히면 아무것도 쓰지 않는다. 표시 줄 → 박스(5단계) · 고아 박스 정리(9단계)도 여기다(B1b · §15.4 ⑶).
 */
@Service
@Transactional
public class BlogPublishService {

    static final int LAYOUT_MAX = 1_500;

    private final BlogAccess access;
    private final BlogIdentityService identity;
    private final BlogPostRepository postRepository;
    private final BlogSentenceRepository sentenceRepository;
    private final BlogSentenceRevRepository revRepository;
    private final BlogPostService postService;
    private final QuoteBoxRepository quoteBoxRepository;
    private final RateLimitService rateLimitService;
    private final Clock clock;

    public BlogPublishService(BlogAccess access, BlogIdentityService identity, BlogPostRepository postRepository,
                              BlogSentenceRepository sentenceRepository, BlogSentenceRevRepository revRepository,
                              BlogPostService postService, QuoteBoxRepository quoteBoxRepository,
                              RateLimitService rateLimitService, Clock clock) {
        this.access = access;
        this.identity = identity;
        this.postRepository = postRepository;
        this.sentenceRepository = sentenceRepository;
        this.revRepository = revRepository;
        this.postService = postService;
        this.quoteBoxRepository = quoteBoxRepository;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    public PostView publish(User user, long id, int revision) {
        access.check(user);                       // 1. 403
        identity.requireReady(user);              // 2. 409 — 둘 다 이미 읽힌 User 필드만 본다(§4.11)
        BlogPost post = postRepository.findForUpdate(id, user) // 3. 첫 DB 문장 = 글 행 잠금
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, BlogPostService.NOT_FOUND));
        if (post.removed()) { // 3b. 운영자가 지운 글(B2b) — 새 글로
            throw new ResponseStatusException(HttpStatus.CONFLICT, BlogPostService.REMOVED);
        }
        if (post.getDraftRevision() != revision) { // 4.
            throw new ResponseStatusException(HttpStatus.CONFLICT, BlogPostService.STALE);
        }
        String title = post.getDraftTitle() == null ? "" : SentenceSplitter.strip(post.getDraftTitle()); // 5.
        if (!ContentFilter.hasVisible(title)) { // 보이지 않는 글자뿐인 제목도 빈 제목(B2a)
            throw new IllegalArgumentException("제목을 적어 주세요");
        }
        // 5. 표시 줄 → 박스: 문장 하나뿐인 줄이 통째로 ⟦인용 N⟧이고 그 label의 박스가 있고 처음 쓰는 번호면 Q 항목 —
        //    짓던 문단을 닫고 나머지 줄은 새 문단. 같은 번호 둘째 · 모르는 번호 · 깨진 표시는 평범한 글(REQ-241).
        List<QuoteBox> boxes = quoteBoxRepository.findByOwnerTypeAndOwnerIdOrderByLabel(QuoteBox.Owner.POST, id);
        Set<Integer> known = new HashSet<>();
        boxes.forEach(q -> known.add(q.getLabel()));
        Set<Integer> used = new HashSet<>();
        List<Piece> pieces = new ArrayList<>();
        for (SentenceSplitter.Paragraph p : SentenceSplitter.split(post.getDraftBody())) {
            List<SentenceSplitter.Line> run = new ArrayList<>();
            for (SentenceSplitter.Line l : p.lines()) {
                Matcher m = l.sentences().size() == 1 ? QuoteBoxService.MARKER.matcher(l.sentences().get(0)) : null;
                int n = m != null && m.matches() ? Integer.parseInt(m.group(1)) : 0;
                if (!known.contains(n) || !used.add(n)) {
                    run.add(l);
                    continue;
                }
                if (!run.isEmpty()) pieces.add(new Piece(run, null)); // 빈 P 항목은 만들지 않는다
                run = new ArrayList<>();
                pieces.add(new Piece(null, n));
            }
            if (!run.isEmpty()) pieces.add(new Piece(run, null));
        }
        List<String> texts = new ArrayList<>();
        pieces.stream().filter(x -> x.lines() != null).forEach(x -> x.lines().forEach(l -> texts.addAll(l.sentences())));
        if (texts.isEmpty()) { // 6.
            throw new IllegalArgumentException("본문을 한 문장 이상 적어 주세요");
        }
        if (texts.size() + used.size() > LAYOUT_MAX) { // 배치 항목 = 문장 + 박스
            throw new IllegalArgumentException("문장이 너무 많아요 — 1,500개까지 올릴 수 있어요");
        }
        // 6b. 걸러내기(B2a REQ-251) — 제목 + 문장 + 이번 배치에 쓰는 박스 글을 한 글로(가린 박스는 글이 보이지 않으니 뺀다 — B2b)
        StringBuilder whole = new StringBuilder(title);
        texts.forEach(t -> whole.append("\n").append(t));
        boxes.stream().filter(q -> used.contains(q.getLabel()) && !q.hidden()).forEach(q -> whole.append("\n").append(q.getText()));
        ContentFilter.violation(whole.toString(), ContentFilter.Kind.POST).ifPresent(m -> {
            throw new IllegalArgumentException(m);
        });
        // 책 문장 박스의 출처(사용자가 넣은 책 제목 · 저자)는 따로 — 실제 책 제목이 걸리면 사용자가 본문에서 이유를 못 찾는다(r3)
        boxes.stream().filter(q -> used.contains(q.getLabel()) && q.getKind() == QuoteBox.Kind.BOOK && q.getAttribution() != null)
                .filter(q -> ContentFilter.violation(q.getAttribution(), ContentFilter.Kind.POST).isPresent())
                .findFirst().ifPresent(q -> {
                    throw new IllegalArgumentException("⟦인용 " + q.getLabel() + "⟧ 책 제목 · 저자에 올릴 수 없는 표현이 있어요 — 그 인용을 빼 주세요");
                });

        // 7. 옛 문장 = 지금 배치의 산 문장
        PostSentences current = new PostSentences(Layout.parse(post.getLayoutJson()), sentenceRepository.findByPostId(id));
        List<SentenceAligner.Old> old = current.live().stream()
                .map(s -> new SentenceAligner.Old(s.getSid(), s.getText())).toList();
        SentenceAligner.Alignment a = SentenceAligner.align(old, texts);
        // 7b. 하루 상한(B2a REQ-256) — 검사를 다 지난 뒤 · 쓰기 직전(409 · 400으로 막힌 시도는 세지 않는다)
        if (!rateLimitService.allow(RateLimitAction.BLOG_PUBLISH, user.getId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "오늘은 글을 너무 많이 올렸어요 — 내일 다시 올려 주세요");
        }

        // 8. 적용
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // MySQL datetime(6)은 반올림한다 — 커서 같음 비교가 어긋나지 않게
        boolean changed = false;
        Set<String> drawn = new HashSet<>();
        List<String> sidOf = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String sid = a.sidOfNew().get(i);
            if (sid == null) {
                sid = Sid.fresh(drawn, sentenceRepository::existsBySid, Sid::random);
                BlogSentence created = sentenceRepository.save(BlogSentence.create(post, sid, text, a.splitFrom().get(i)));
                revRepository.save(BlogSentenceRev.of(created, 1, text));
                changed = true;
            } else {
                BlogSentence s = current.get(sid).orElseThrow();
                if (a.edited().contains(sid)) {
                    s.edit(text);
                    revRepository.save(BlogSentenceRev.of(s, s.getRev(), text));
                    changed = true;
                } else if (!s.getText().equals(text)) {
                    s.retext(text); // 공백 무리만 다름 — 판 그대로(§4.10)
                }
            }
            sidOf.add(sid);
        }
        for (Map.Entry<String, String> m : a.merged().entrySet()) {
            current.get(m.getKey()).orElseThrow().mergeInto(m.getValue(), now);
            changed = true;
        }
        for (String tomb : a.tombs()) {
            current.get(tomb).orElseThrow().tombstone(now);
            changed = true;
        }

        // 9. 고아 박스 정리 — 이번 배치에 없는 그 글의 박스(엔티티 지우기 — 많아야 100개 · 이번에 읽은 엔티티)
        quoteBoxRepository.deleteAll(boxes.stream().filter(q -> !used.contains(q.getLabel())).toList());

        // 10. 새 배치 = 항목 순서 그대로(문단 → 줄 → sid · 박스 = label)
        List<Layout.Item> items = new ArrayList<>();
        int k = 0;
        for (Piece piece : pieces) {
            if (piece.lines() == null) {
                items.add(new Layout.Item(null, (long) piece.q()));
                continue;
            }
            List<List<String>> lines = new ArrayList<>();
            for (SentenceSplitter.Line l : piece.lines()) {
                lines.add(List.copyOf(sidOf.subList(k, k + l.sentences().size())));
                k += l.sentences().size();
            }
            items.add(new Layout.Item(lines, null));
        }
        post.publish(title, new Layout(items).toJson(), changed, now);
        postRepository.flush();
        return postService.view(user, post, null); // 11.
    }

    /** 배치 항목 하나 — 문단(줄 목록)이거나 박스(label). */
    private record Piece(List<SentenceSplitter.Line> lines, Integer q) {
    }
}
