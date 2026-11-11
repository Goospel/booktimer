package com.booktimer.blog;

import com.booktimer.blog.BlogViews.PostView;
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

/**
 * 올리기(계획 2026-11-11-b1-blog-core §6.3) — 서버 초안을 문장으로 나누고 옛 판과 견줘 주소를 잇는다(D4 · D5 · J11~J14).
 * 앞에서 막히면 아무것도 쓰지 않는다. 표시 줄 → 박스 · 고아 박스 정리(5 · 9단계)는 B1b.
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
    private final Clock clock;

    public BlogPublishService(BlogAccess access, BlogIdentityService identity, BlogPostRepository postRepository,
                              BlogSentenceRepository sentenceRepository, BlogSentenceRevRepository revRepository,
                              BlogPostService postService, Clock clock) {
        this.access = access;
        this.identity = identity;
        this.postRepository = postRepository;
        this.sentenceRepository = sentenceRepository;
        this.revRepository = revRepository;
        this.postService = postService;
        this.clock = clock;
    }

    public PostView publish(User user, long id, int revision) {
        access.check(user);                       // 1. 403
        identity.requireReady(user);              // 2. 409 — 둘 다 이미 읽힌 User 필드만 본다(§4.11)
        BlogPost post = postRepository.findForUpdate(id, user) // 3. 첫 DB 문장 = 글 행 잠금
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, BlogPostService.NOT_FOUND));
        if (post.getDraftRevision() != revision) { // 4.
            throw new ResponseStatusException(HttpStatus.CONFLICT, BlogPostService.STALE);
        }
        String title = post.getDraftTitle() == null ? "" : SentenceSplitter.strip(post.getDraftTitle()); // 5.
        if (title.isEmpty()) {
            throw new IllegalArgumentException("제목을 적어 주세요");
        }
        List<SentenceSplitter.Paragraph> paragraphs = SentenceSplitter.split(post.getDraftBody());
        List<String> texts = new ArrayList<>();
        paragraphs.forEach(p -> p.lines().forEach(l -> texts.addAll(l.sentences())));
        if (texts.isEmpty()) { // 6.
            throw new IllegalArgumentException("본문을 한 문장 이상 적어 주세요");
        }
        if (texts.size() > LAYOUT_MAX) {
            throw new IllegalArgumentException("문장이 너무 많아요 — 1,500개까지 올릴 수 있어요");
        }

        // 7. 옛 문장 = 지금 배치의 산 문장
        PostSentences current = new PostSentences(Layout.parse(post.getLayoutJson()), sentenceRepository.findByPostId(id));
        List<SentenceAligner.Old> old = current.live().stream()
                .map(s -> new SentenceAligner.Old(s.getSid(), s.getText())).toList();
        SentenceAligner.Alignment a = SentenceAligner.align(old, texts);

        // 8. 적용
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS); // MySQL datetime(6)은 반올림한다 — 커서 같음 비교가 어긋나지 않게
        boolean changed = false;
        Set<String> drawn = new HashSet<>();
        List<String> sidOf = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            String sid = a.sidOfNew().get(i);
            if (sid == null) {
                sid = Sid.fresh(s -> drawn.contains(s) || sentenceRepository.existsBySid(s), Sid::random);
                drawn.add(sid);
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

        // 10. 새 배치 = 문단 → 줄 → sid
        List<Layout.Item> items = new ArrayList<>();
        int k = 0;
        for (SentenceSplitter.Paragraph p : paragraphs) {
            List<List<String>> lines = new ArrayList<>();
            for (SentenceSplitter.Line l : p.lines()) {
                lines.add(List.copyOf(sidOf.subList(k, k + l.sentences().size())));
                k += l.sentences().size();
            }
            items.add(new Layout.Item(lines, null));
        }
        post.publish(title, new Layout(items).toJson(), changed, now);
        postRepository.flush();
        return postService.view(user, post, null); // 11.
    }
}
