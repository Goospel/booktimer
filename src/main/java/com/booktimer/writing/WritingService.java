package com.booktimer.writing;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.session.ReadingSession;
import com.booktimer.session.ReadingSessionRepository;
import com.booktimer.story.Story;
import com.booktimer.story.StoryRepository;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 앱 글 원장 · 책 재료 · AI 전송 동의(v1 §5.1.3 PR-B).
 *
 * <p><b>재료는 본인 전용 조회다</b>(§3.3-1): 소셜 경로({@code /api/stories/of/…})는 ADMIN·핸들 없는 계정에게 본인이라도
 * 404를 주므로 쓰지 않는다. 책은 {@code bookRepository.findByIdAndUser}로 <b>독서 서재에서만</b> 찾는다 — 공부 서재 id는
 * 404이고, 이 클래스는 {@code study_note}·{@code study_recall}·{@code study_book}을 읽지 않는다(결정 5 · REQ-48).
 */
@Service
@Transactional
public class WritingService {

    /**
     * AI 질문 국외 전송이 열리는 날(KST) = 처리방침 6-3절 시행일(REQ-74 — WritingNoticeDateTest가 템플릿과 대조한다).
     * 503 문구의 월·일도 이 값에서 만든다 — 날짜 리터럴을 따로 두지 않는다.
     */
    public static final LocalDate AI_NOTICE_EFFECTIVE = LocalDate.of(2026, 11, 23);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 재료 화면에 싣는 여백 상한 — 최근순. ponytail: 한 책에 200개를 넘기면 「전체 N개」도 200에서 멈춘다. 넘는 사람이 나오면 count 쿼리로. */
    static final int MARGINS_MAX = 200;

    private final WritingDraftRepository draftRepository;
    private final BookRepository bookRepository;
    private final StoryRepository storyRepository;
    private final ReadingSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public WritingService(WritingDraftRepository draftRepository, BookRepository bookRepository,
                          StoryRepository storyRepository, ReadingSessionRepository sessionRepository,
                          UserRepository userRepository, Clock clock) {
        this.draftRepository = draftRepository;
        this.bookRepository = bookRepository;
        this.storyRepository = storyRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // ── 글 원장 ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<WritingDraft> recent(User user) {
        return draftRepository.findTop20ByUserOrderByUpdatedAtDesc(user);
    }

    public WritingDraft create(User user, Long bookId) {
        return draftRepository.save(WritingDraft.start(user, ownedBook(user, bookId)));
    }

    @Transactional(readOnly = true)
    public WritingDraft get(User user, Long id) {
        return owned(user, id);
    }

    /**
     * 사용자 저장 — 본문을 바꾸는 유일한 길(REQ-47). 소유 판정(404)이 검증(400)보다 먼저다(남의 글 존재를 캐지 못하게).
     *
     * @param exported {@code true}면 처음 한 번만 내보낸 시각을 박는다(REQ-16)
     * @throws ResponseStatusException  404 남의·없는 글 / 409 낡은 판(덮어쓰지 않는다)
     * @throws IllegalArgumentException 길이 위반(→ 400)
     */
    public WritingDraft save(User user, Long id, String title, String body, int revision, Boolean exported) {
        WritingDraft draft = owned(user, id);
        try {
            draft.edit(title, body, revision);
        } catch (WritingDraft.StaleDraftException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 기기에서 이 글이 바뀌었어요 — 그쪽을 불러와 주세요");
        }
        if (Boolean.TRUE.equals(exported)) {
            draft.markExported(clock.instant());
        }
        return draft;
    }

    public void delete(User user, Long id) {
        draftRepository.delete(owned(user, id));
    }

    // ── 책 재료 ─────────────────────────────────────────────────────────────

    /** 내 독서 서재 책 — {@code null}이면 400, 남의 것·없는 것·공부 서재 id면 404. */
    @Transactional(readOnly = true)
    public Book ownedBook(User user, Long bookId) {
        if (bookId == null) {
            throw new IllegalArgumentException("책을 골라 주세요");
        }
        return bookRepository.findByIdAndUser(bookId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "책을 찾을 수 없어요"));
    }

    /** 그 책·그 사용자의 여백 — 최근순 {@value #MARGINS_MAX}개까지. */
    @Transactional(readOnly = true)
    public List<Story> margins(User user, Book book) {
        return storyRepository.findByUserAndBookOrderByCreatedAtDescIdDesc(user, book, PageRequest.of(0, MARGINS_MAX));
    }

    /** 여백(최근순)으로 AI 재료를 고른다 — 작성일은 사용자 타임존. */
    public WritingMaterial material(User user, List<Story> recentFirst) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        return WritingMaterial.select(recentFirst.stream()
                .map(s -> new MaterialItem(LocalDate.ofInstant(s.getCreatedAt(), zone), s.getQuote(), s.getText()))
                .toList(), WritingMaterial.MAX_CHARS);
    }

    /** 화면 머리의 책 한 줄 — 읽은 시간·횟수는 끝난 측정만 센다(재료 블록에는 싣지 않는다). */
    @Transactional(readOnly = true)
    public BookLine bookLine(User user, Book book) {
        List<ReadingSession> ended = sessionRepository.findByUserAndBook(user, book).stream()
                .filter(s -> s.getEndedAt() != null).toList();
        long seconds = ended.stream().mapToLong(ReadingSession::getDurationSeconds).sum();
        return new BookLine(book.getId(), book.getTitle(), book.getAuthor(), book.getCoverUrl(), book.getStatus(),
                seconds, ended.size(), book.isPublic());
    }

    @Transactional(readOnly = true)
    public List<WritingDraft> draftsOf(User user, Book book) {
        return draftRepository.findByUserAndBookOrderByUpdatedAtDesc(user, book);
    }

    // ── AI 전송 동의 ────────────────────────────────────────────────────────

    /** 유효한 동의인가 — 시행일(KST 0시)보다 이른 시각은 동의 없음이다(REQ-15). */
    public boolean aiConsent(User user) {
        Instant at = user.getAiConsentAt();
        return at != null && !at.isBefore(effectiveInstant());
    }

    /**
     * 동의를 박거나 지운다. 시행일 전이면 둘 다 하지 않고 503이다.
     *
     * @throws ResponseStatusException 503 「AI 질문은 M월 D일부터 열려요」
     */
    public User consent(User user, boolean agree) {
        Instant now = clock.instant();
        if (now.isBefore(effectiveInstant())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 질문은 "
                    + AI_NOTICE_EFFECTIVE.getMonthValue() + "월 " + AI_NOTICE_EFFECTIVE.getDayOfMonth() + "일부터 열려요");
        }
        if (agree) {
            user.consentToAi(now);
        } else {
            user.withdrawAiConsent();
        }
        return userRepository.save(user);
    }

    private static Instant effectiveInstant() {
        return AI_NOTICE_EFFECTIVE.atStartOfDay(KST).toInstant();
    }

    private WritingDraft owned(User user, Long id) {
        return draftRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "글을 찾을 수 없어요"));
    }
}
