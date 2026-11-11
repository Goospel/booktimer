package com.booktimer.blog;

import com.booktimer.blog.BlogViews.HangBook;
import com.booktimer.blog.BlogViews.NoteSentence;
import com.booktimer.blog.BlogViews.NoteSentences;
import com.booktimer.blog.BlogViews.QuoteBoxView;
import com.booktimer.blog.BlogViews.SourceNote;
import com.booktimer.blog.QuoteBox.Kind;
import com.booktimer.blog.QuoteBox.Owner;
import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.story.StoryService;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 인용 박스 쓰기 · 보기(계획 2026-11-11-b1-blog-core §5.2 · §6.5~6.7 · §15.4).
 *
 * <p>쓰기 문(빼기 제외)은 허용 목록이 첫 줄이고, <b>소유 404가 종류 400 · 상태 409보다 먼저</b>다(§6.6 — 남의 박스 · 쪽지 · 글의
 * 종류나 원문 상태를 알아내는 신호가 되지 않게). 잠금(§4.11): 글 박스 만들기는 글 행, 반영 · 다시 고르기는 박스 행 — 둘 다 그
 * 트랜잭션의 첫 DB 문장. 빈 순환을 만들지 않게 {@code BlogPostService} · {@code BlogPublishService}를 주입받지 않는다(RK-01).
 */
@Service
@Transactional
public class QuoteBoxService {

    /** 표시 줄 {@code ⟦인용 N⟧} — 올리기는 줄 통째({@code matches}), 다음 label은 줄 어디서든({@code find}) 본다(§6.3 · §6.6). */
    static final Pattern MARKER = Pattern.compile("⟦인용 ([1-9][0-9]{0,3})⟧");
    static final int POST_MAX = 100;
    static final int NOTE_MAX = 10;
    static final int TEXT_MAX = 1_000;
    private static final int LABEL_MAX = 9_999;

    static final String NO_QUOTE = "인용을 찾을 수 없어요";
    private static final String BLOCKED = "이 글의 문장은 인용할 수 없어요";
    private static final String BAD_SIDS = "고른 문장을 찾을 수 없어요 — 글을 다시 불러와 주세요";
    private static final String NO_NOTE = "쪽지를 찾을 수 없어요";
    private static final String NOTE_CHANGED = "쪽지가 바뀌었어요 — 다시 골라 주세요";
    private static final String OUT_OF_RANGE = "고른 범위가 쪽지의 생각 밖이에요";
    private static final String TOO_LONG = "인용은 1,000자까지예요";
    private static final String GONE_USER = "탈퇴한 사용자";

    private final BlogPostRepository postRepository;
    private final BlogSentenceRepository sentenceRepository;
    private final QuoteBoxRepository repository;
    private final StoryService storyService;
    private final StoryRepository storyRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final BlogAccess access;
    private final Clock clock;

    public QuoteBoxService(BlogPostRepository postRepository, BlogSentenceRepository sentenceRepository,
                           QuoteBoxRepository repository, StoryService storyService, StoryRepository storyRepository,
                           BookRepository bookRepository, UserRepository userRepository, BlockService blockService,
                           BlogAccess access, Clock clock) {
        this.postRepository = postRepository;
        this.sentenceRepository = sentenceRepository;
        this.repository = repository;
        this.storyService = storyService;
        this.storyRepository = storyRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.blockService = blockService;
        this.access = access;
        this.clock = clock;
    }

    /**
     * 글 박스 만들기 몸 — kind마다 필드가 달라 전부 래퍼 형이다(primitive면 BLOG 몸에 없는 {@code revision}에서 Jackson이 400 ·
     * §15.4 ⑽). 빠진 필드는 그 갈래의 기존 문구로 답한다.
     */
    public record QuoteRequest(String kind, Long postId, String fromSid, String toSid, Long storyId, Integer revision,
                               Integer from, Integer to) {
    }

    /** 블로그 문장 구간(걸기 · 쪽지에 붙이기). */
    public record LinkRequest(Long postId, String fromSid, String toSid) {
    }

    // ── 글의 박스 ────────────────────────────────────────────────────────────

    /** {@code POST /api/app/blog/posts/{id}/quotes} — 허용 목록 → 내 글(잠금) → 100개 → 종류별 검사 → 다음 label(§6.6). */
    public QuoteBoxView createForPost(User user, long postId, QuoteRequest req) {
        access.check(user);
        BlogPost post = postRepository.findForUpdate(postId, user).orElseThrow(() -> notFound(BlogPostService.NOT_FOUND));
        List<QuoteBox> boxes = repository.findByOwnerTypeAndOwnerIdOrderByLabel(Owner.POST, postId);
        if (boxes.size() >= POST_MAX) {
            throw new IllegalArgumentException("한 글에 인용은 100개까지예요");
        }
        Kind kind = kindOf(req.kind());
        Instant now = now();
        QuoteBox box = switch (kind) {
            case BLOG -> {
                Source src = blogSource(user, req.postId(), req.fromSid(), req.toSid());
                yield src.box(Owner.POST, postId, nextLabel(boxes, post.getDraftBody()), now);
            }
            case MARGIN -> {
                Story note = ownNote(user, req.storyId());
                if (req.revision() == null || req.revision() != note.getRevision()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, NOTE_CHANGED);
                }
                String text = thought(note.getText(), req.from(), req.to());
                yield QuoteBox.margin(postId, nextLabel(boxes, post.getDraftBody()), note.getId(), text, now);
            }
            case BOOK -> {
                Story note = ownNote(user, req.storyId());
                if (note.getQuote() == null) {
                    throw new IllegalArgumentException("옮긴 문장이 없는 쪽지예요");
                }
                Book book = note.getBook();
                yield QuoteBox.book(postId, nextLabel(boxes, post.getDraftBody()), note.getId(), note.getQuote(),
                        note.getQuoteSource(), book == null ? null : book.getIsbn13(), now);
            }
        };
        return single(repository.save(box), user);
    }

    /** 최신으로 반영(§6.5 · §6.6) — 박스 행 잠금 → 주인 → 종류 → 차단 403 → 원문 지움 · 가림 409 → 걸침 · 1,000자 409. */
    public QuoteBoxView refresh(User user, long quoteId) {
        access.check(user);
        QuoteBox box = repository.findForUpdate(quoteId).orElseThrow(() -> notFound(NO_QUOTE));
        requireOwner(user, box);
        if (box.getKind() != Kind.BLOG) {
            throw new IllegalArgumentException("최신으로 반영은 블로그 문장 인용만 돼요");
        }
        if (box.getSourceUserId() != null) {
            User author = userRepository.findById(box.getSourceUserId()).orElse(null);
            if (author != null && blockService.isBlockedBetween(user, author)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, BLOCKED);
            }
        }
        BlogPost post = box.getSourcePostId() == null ? null : postRepository.findById(box.getSourcePostId()).orElse(null);
        PostSentences sentences = post == null ? null : sentencesOf(post);
        QuoteRange.Current cur = QuoteRange.current(box.members(), sentences, box.getSeenSentenceId());
        if (cur.allDeleted()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "원문이 지워졌어요");
        }
        if (!post.quotable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "원문을 지금 볼 수 없어요");
        }
        if (cur.crossesBox() || cur.text().length() > TEXT_MAX) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "원문이 바뀌어 박스에 다 담을 수 없어요 — 인용을 다시 골라 주세요");
        }
        box.refresh(cur.sids(), cur.text(), sentences.maxRowId(), post.getTitle(), now());
        return single(box, user);
    }

    /** 여백 박스 다시 고르기(§6.6) — 범위는 <b>생각 문장만</b> 가리킨다(옮긴 문장은 이 문으로 고를 수 없다 — J84). */
    public QuoteBoxView repick(User user, long quoteId, int revision, int from, int to) {
        access.check(user);
        QuoteBox box = repository.findForUpdate(quoteId).orElseThrow(() -> notFound(NO_QUOTE));
        requireOwner(user, box);
        if (box.getKind() != Kind.MARGIN) {
            throw new IllegalArgumentException("여백 인용만 다시 고를 수 있어요");
        }
        Story source = storyRepository.findWithBook(box.getSourceStoryId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "원문(여백)이 지워졌어요"));
        if (revision != source.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, NOTE_CHANGED);
        }
        box.repick(thought(source.getText(), from, to), now());
        return single(box, user);
    }

    // ── 쪽지의 박스 ──────────────────────────────────────────────────────────

    /**
     * 문장 걸기 = 새 쪽지 + 박스 label 1({@code POST /api/app/margins}에 {@code link}가 있을 때) — 허용 목록 → BLOG 검사 →
     * 쪽지 만들기(429 · 책 404 · 길이 400). 쪽지 행은 잠그지 않는다(§1 비목표).
     */
    public Story hang(User user, Long bookId, String quote, String text, LinkRequest link) {
        access.check(user);
        Source src = blogSource(user, link.postId(), link.fromSid(), link.toSid());
        Story note = storyService.createLinkedNote(user, bookId, quote, text);
        repository.save(src.box(Owner.NOTE, note.getId(), 1, now()));
        return note;
    }

    /** 쓰고 있는 쪽지에 붙이기 — 허용 목록 → 내 쪽지(404) → 10개 → BLOG 검사 → 다음 label. */
    public void addToNote(User user, long storyId, LinkRequest link) {
        access.check(user);
        Story note = storyService.note(user, storyId);
        List<QuoteBox> boxes = repository.findByOwnerTypeAndOwnerIdOrderByLabel(Owner.NOTE, note.getId());
        if (boxes.size() >= NOTE_MAX) {
            throw new IllegalArgumentException("한 쪽지에 인용은 10개까지예요");
        }
        Source src = blogSource(user, link.postId(), link.fromSid(), link.toSid());
        repository.save(src.box(Owner.NOTE, note.getId(), nextLabel(boxes, null), now()));
    }

    /**
     * 쪽지에서 빼기(허용 목록 없음 — 내 데이터를 지우는 길) — 내 쪽지(404) → 그 쪽지의 박스(404 — 남의 글 · 남의 쪽지 · 내 다른 쪽지의
     * 박스 id로는 지우지 못한다) → 빼고 나서 쪽지가 비면 400.
     */
    public void removeFromNote(User user, long storyId, long quoteId) {
        Story note = storyService.note(user, storyId);
        QuoteBox box = repository.findByIdAndOwnerTypeAndOwnerId(quoteId, Owner.NOTE, note.getId())
                .orElseThrow(() -> notFound(NO_QUOTE));
        boolean empty = note.getText().isEmpty() && note.getQuote() == null && note.getAiLog() == null
                && repository.countByOwnerTypeAndOwnerId(Owner.NOTE, note.getId()) <= 1;
        if (empty) {
            throw new IllegalArgumentException("쪽지가 비어요 — 쪽지를 지워 주세요");
        }
        repository.delete(box);
    }

    /** 쪽지 생각의 문장 나누기(내 여백 가져오기 — REQ-292). 본인만(404). */
    @Transactional(readOnly = true)
    public NoteSentences noteSentences(User user, long storyId) {
        Story note = storyService.note(user, storyId);
        List<Piece> pieces = pieces(note.getText());
        List<NoteSentence> out = new ArrayList<>(pieces.size());
        for (int n = 0; n < pieces.size(); n++) {
            Piece p = pieces.get(n);
            out.add(new NoteSentence(n, p.text(), p.br(), p.paraEnd()));
        }
        return new NoteSentences(note.getRevision(), out);
    }

    /** 쪽지 목록의 박스 수 — 한 번에(줄마다 세지 않는다 · 비평 D6). 목록이 비면 부르지 않는다. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> noteCounts(List<Long> storyIds) {
        if (storyIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> out = new HashMap<>();
        for (Object[] row : repository.countByOwners(Owner.NOTE, storyIds)) {
            out.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return out;
    }

    // ── 보기 ─────────────────────────────────────────────────────────────────

    /** 그 주인의 박스 — label 순(§6.7). 원 글 · 원 글쓴이는 묶어서 읽는다(원 글마다 문장 행 한 번 — RK-02). */
    @Transactional(readOnly = true)
    public List<QuoteBoxView> views(Owner owner, long ownerId, User viewer) {
        List<QuoteBox> boxes = repository.findByOwnerTypeAndOwnerIdOrderByLabel(owner, ownerId);
        if (boxes.isEmpty()) {
            return List.of();
        }
        Optional<Long> ownerUser = owner == Owner.POST
                ? postRepository.findById(ownerId).map(p -> p.getUser().getId())
                : storyRepository.findById(ownerId).map(s -> s.getUser().getId());
        return render(boxes, viewer, ownerUser.filter(id -> id.equals(viewer.getId())).isPresent());
    }

    /** 올린 배치의 책 박스를 순서대로 보며 그 ISBN이 보는 사람 독서 서재에 있는 첫 책(J75 — 관련 없는 최근 책 0). */
    @Transactional(readOnly = true)
    public HangBook hangBook(User viewer, BlogPost post, Layout layout) {
        Map<Integer, QuoteBox> books = repository.findByOwnerTypeAndOwnerIdOrderByLabel(Owner.POST, post.getId()).stream()
                .filter(q -> q.getKind() == Kind.BOOK && q.getSourceIsbn13() != null)
                .collect(Collectors.toMap(QuoteBox::getLabel, Function.identity()));
        for (Layout.Item item : layout.items()) {
            QuoteBox q = item.q() == null ? null : books.get(item.q().intValue());
            if (q == null) continue;
            Optional<Book> mine = bookRepository.findFirstByUserAndIsbn13(viewer, q.getSourceIsbn13());
            if (mine.isPresent()) {
                return new HangBook(mine.get().getId(), mine.get().getTitle());
            }
        }
        return null;
    }

    private QuoteBoxView single(QuoteBox box, User owner) {
        return render(List.of(box), owner, true).get(0);
    }

    private List<QuoteBoxView> render(List<QuoteBox> boxes, User viewer, boolean mine) {
        Map<Long, BlogPost> posts = byId(postRepository.findAllById(ids(boxes, Kind.BLOG, QuoteBox::getSourcePostId)), BlogPost::getId);
        Map<Long, User> users = byId(userRepository.findAllById(ids(boxes, Kind.BLOG, QuoteBox::getSourceUserId)), User::getId);
        Set<Long> storyIds = new HashSet<>(ids(boxes, Kind.MARGIN, QuoteBox::getSourceStoryId));
        storyIds.addAll(ids(boxes, Kind.BOOK, QuoteBox::getSourceStoryId));
        Map<Long, Story> stories = mine
                ? byId(storyRepository.findAllById(storyIds).stream().filter(s -> s.getUser().getId().equals(viewer.getId())).toList(), Story::getId)
                : Map.of();
        Map<Long, PostSentences> sentences = new HashMap<>();
        Map<Long, Boolean> blocked = new HashMap<>();
        List<QuoteBoxView> out = new ArrayList<>(boxes.size());
        for (QuoteBox q : boxes) {
            out.add(switch (q.getKind()) {
                case BLOG -> blogView(q, viewer, posts.get(q.getSourcePostId()), users.get(q.getSourceUserId()), sentences, blocked);
                case MARGIN -> {
                    Story s = stories.get(q.getSourceStoryId());
                    yield !mine ? view(q, null, null, null, null, null) : view(q, null, null, s == null,
                            s == null ? null : !SentenceAligner.norm(s.getText()).contains(SentenceAligner.norm(q.getText())),
                            s == null ? null : sourceNote(s));
                }
                case BOOK -> {
                    Story s = stories.get(q.getSourceStoryId());
                    yield view(q, q.getAttribution(), null, null, null, mine && s != null ? sourceNote(s) : null);
                }
            });
        }
        return out;
    }

    /** §6.7 BLOG 행 — 차단 관계면 판정 셋 null · 가린 원 글이면 null · false · 원 글 없음 · 전부 지움이면 null · true. */
    private QuoteBoxView blogView(QuoteBox q, User viewer, BlogPost post, User author, Map<Long, PostSentences> sentences,
                                  Map<Long, Boolean> blocked) {
        // 제목: 원 글이 있고 인용할 수 있으면 지금 올린 제목, 아니면 인용(반영) 때 제목 — 가린 글의 바뀐 제목을 내보내지 않는다
        String title = post != null && post.quotable() ? post.getTitle() : q.getAttribution();
        String attribution = (author == null ? GONE_USER : author.getNickname()) + " · 「" + title + "」";
        boolean cut = author != null && !author.getId().equals(viewer.getId())
                && blocked.computeIfAbsent(author.getId(), k -> blockService.isBlockedBetween(viewer, author));
        if (cut) {
            return view(q, attribution, null, null, null, null);
        }
        if (post != null && !post.quotable()) {
            return view(q, attribution, null, false, null, null);
        }
        QuoteRange.Current cur = post == null ? null
                : QuoteRange.current(q.members(), sentences.computeIfAbsent(post.getId(), k -> sentencesOf(post)), q.getSeenSentenceId());
        if (cur == null || cur.allDeleted()) {
            return view(q, attribution, null, true, null, null);
        }
        List<String> sids = cur.sids();
        String link = "/p/" + post.getId() + "/s/" + sids.get(0) + (sids.size() == 1 ? "" : "-" + sids.get(sids.size() - 1));
        return view(q, attribution, link, false, QuoteRange.updatable(cur, q.getText()), null);
    }

    private static QuoteBoxView view(QuoteBox q, String attribution, String link, Boolean deleted, Boolean updatable,
                                     SourceNote note) {
        return new QuoteBoxView(q.getId(), q.getLabel(), q.getKind().name(), q.getText(), q.getQuotedAt(), attribution, link,
                deleted, updatable, note);
    }

    private static SourceNote sourceNote(Story s) {
        return new SourceNote(s.getId(), s.getBook() == null ? null : s.getBook().getId());
    }

    // ── 검사 ─────────────────────────────────────────────────────────────────

    /** 블로그 구간 검사(§6.6 BLOG 행) — 원 글 인용 가능(404 · 주인도) → 차단 403 → 산 문장 400 → 박스 사이 400 → 1,000자 400. */
    private Source blogSource(User user, Long postId, String fromSid, String toSid) {
        BlogPost post = (postId == null ? Optional.<BlogPost>empty() : postRepository.findById(postId))
                .filter(BlogPost::quotable).orElseThrow(() -> notFound(BlogPostService.NOT_FOUND));
        if (!post.getUser().getId().equals(user.getId()) && blockService.isBlockedBetween(user, post.getUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, BLOCKED);
        }
        PostSentences sentences = sentencesOf(post);
        Set<String> laid = new HashSet<>(sentences.layout().sidsInOrder());
        if (!liveIn(sentences, laid, fromSid) || !liveIn(sentences, laid, toSid)) {
            throw new IllegalArgumentException(BAD_SIDS); // 이 판정이 먼저 막아 QuoteRange.between의 「배치에 없음」(500)에 닿지 않는다
        }
        List<String> sids = QuoteRange.between(sentences, fromSid, toSid)
                .orElseThrow(() -> new IllegalArgumentException("인용 박스를 사이에 둔 문장은 한 박스로 고를 수 없어요"));
        String text = QuoteRange.current(sids, sentences, null).text();
        if (text.length() > TEXT_MAX) {
            throw new IllegalArgumentException(TOO_LONG);
        }
        return new Source(post, sids, sentences.maxRowId(), text);
    }

    private record Source(BlogPost post, List<String> sids, long seen, String text) {
        QuoteBox box(Owner owner, long ownerId, int label, Instant now) {
            return QuoteBox.blog(owner, ownerId, label, post.getId(), post.getUser().getId(), sids, seen, text, post.getTitle(), now);
        }
    }

    private static boolean liveIn(PostSentences sentences, Set<String> laid, String sid) {
        return sid != null && laid.contains(sid) && sentences.get(sid).map(BlogSentence::live).orElse(false);
    }

    /** 박스 주인 판정 — 글 주인 · 쪽지 주인(아니면 404 · §6.6 반영 · 다시 고르기). */
    private void requireOwner(User user, QuoteBox box) {
        boolean owns = box.getOwnerType() == Owner.POST
                ? postRepository.findByIdAndUser(box.getOwnerId(), user).isPresent()
                : storyRepository.findWithBook(box.getOwnerId(), user).isPresent();
        if (!owns) {
            throw notFound(NO_QUOTE);
        }
    }

    private Story ownNote(User user, Long storyId) {
        if (storyId == null) {
            throw notFound(NO_NOTE);
        }
        return storyService.note(user, storyId);
    }

    private static Kind kindOf(String kind) {
        try {
            return Kind.valueOf(Objects.requireNonNull(kind));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("아직 쓸 수 없는 인용이에요");
        }
    }

    /** 다음 label = 1 + max(박스 label ∪ 초안에 적힌 표시 번호) — 고아 정리로 지운 번호의 옛 표시가 새 박스에 묶이지 않게(§6.6). */
    private static int nextLabel(List<QuoteBox> boxes, String draftBody) {
        int max = boxes.stream().mapToInt(QuoteBox::getLabel).max().orElse(0);
        if (draftBody != null) {
            Matcher m = MARKER.matcher(draftBody);
            while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        if (max >= LABEL_MAX) {
            throw new IllegalArgumentException("인용은 더 만들 수 없어요");
        }
        return max + 1;
    }

    /** 쪽지 생각의 {@code [from, to]} 문장(0부터 · 양 끝 포함)을 이음 규칙으로(§6.6 MARGIN). */
    private static String thought(String text, Integer from, Integer to) {
        List<Piece> pieces = pieces(text);
        if (from == null || to == null || from < 0 || to >= pieces.size() || from > to) {
            throw new IllegalArgumentException(OUT_OF_RANGE);
        }
        List<List<String>> lines = new ArrayList<>();
        int line = -1;
        for (Piece p : pieces.subList(from, to + 1)) {
            if (p.line() != line) lines.add(new ArrayList<>());
            line = p.line();
            lines.get(lines.size() - 1).add(p.text());
        }
        String joined = QuoteRange.join(lines);
        if (joined.length() > TEXT_MAX) {
            throw new IllegalArgumentException(TOO_LONG);
        }
        return joined;
    }

    /** 생각을 편 문장 — {@code line}은 글 전체에서 몇째 줄인지(줄 · 문단이 바뀌면 이음이 {@code \n}). */
    private record Piece(String text, int line, boolean br, boolean paraEnd) {
    }

    private static List<Piece> pieces(String text) {
        List<Piece> out = new ArrayList<>();
        int line = 0;
        for (SentenceSplitter.Paragraph p : SentenceSplitter.split(text)) {
            for (int l = 0; l < p.lines().size(); l++, line++) {
                List<String> ss = p.lines().get(l).sentences();
                for (int i = 0; i < ss.size(); i++) {
                    boolean last = i == ss.size() - 1;
                    out.add(new Piece(ss.get(i), line, last && l < p.lines().size() - 1, last && l == p.lines().size() - 1));
                }
            }
        }
        return out;
    }

    private PostSentences sentencesOf(BlogPost post) {
        return new PostSentences(Layout.parse(post.getLayoutJson()), sentenceRepository.findByPostId(post.getId()));
    }

    private static Set<Long> ids(List<QuoteBox> boxes, Kind kind, Function<QuoteBox, Long> field) {
        return boxes.stream().filter(q -> q.getKind() == kind).map(field).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> id) {
        return rows.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS); // MySQL datetime(6) — BlogPublishService와 같다
    }

    private static ResponseStatusException notFound(String reason) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
