package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.Set;

/**
 * 여백의 쪽지 한 장 — 읽다가 옮긴 문장과 내 생각을 적어 두는 자리 (sns-design §13 · 책생글 「여백 + 블로그」 §5.1).
 *
 * <p><b>책은 선택이다</b>(2026-10-30 책생글 M1 — C9). 사용자 말로 「여백은 말 그대로 빈 공간에 독자가 마음껏 글을 끄적이는
 * 공간」이라, 책 없는 쪽지도 앱({@code /api/app/margins})에서 만든다. 책을 지우면 쪽지는 남고 연결만 풀린다
 * ({@code BookService.delete} → {@code StoryRepository.unlinkBook}). 웹 · 미니앱의 옛 경로({@code /api/stories})는 여전히
 * 책을 요구하고, 그쪽 목록 쿼리는 전부 {@code join s.book}이라 책 없는 쪽지가 실리지 않는다(웹 여백 화면은 책 단위 — J64).
 *
 * <p>2026-08-16까지는 사람에게 딸린 24시간짜리 「스토리」였다. 클래스·테이블 이름은 {@code Story}로 남았다 — 어휘만 바꾸고
 * 스키마는 두는 게 싸다. 앱의 「이 책으로 글쓰기」 글({@code writing_draft})은 M1에서 이 표로 합쳤다(V105 — J2 · J4).
 *
 * <p>글은 <b>두 층</b>이다(2026-08-20): 책에서 옮긴 {@code quote}(선택, 200자)와 내 생각 {@code text}(1만 200자 — J3).
 * 위계는 뒤집지 않는다 — 「인용은 從」. 옮긴 문장은 <b>책이나 출처 스냅샷({@code quoteSource})이 있을 때만</b> 둘 수 있다:
 * 출처 없는 책 문장이 블로그로 나가지 않게(저작권법 37조 — J84).
 *
 * <p><b>가시성 불변식</b>(2026-10-30 책생글 M1 — C3 · J9 · D1): <b>노출 = 본인만</b>. 여백은 각 사용자의 비공개 공간이고
 * 공개는 블로그가 맡는다. 남이 부르는 읽기 경로(사람축 · 책축 · 홈 소식 · 「여백」 탭 · 검색 배지 · 좋아요 · 명단 · 프로필 발광)는
 * 전부 0건 · 404다. 판정은 {@code StoryService.assertVisible}(단건 — 본인 검사만, 책을 읽지 않는다)과 각 목록의 「본인이
 * 아니면 빈 목록」이 진다. 옛 불변식(노출 = {@code book.isPublic()})보다 더 닫힌 쪽이다 — 남에게 다시 여는 코드는 블로그가
 * 맡을 공개를 여백으로 되돌리는 것이라 설계 위반이다.
 *
 * <p>{@code shared}(옛 「함께 걸기」)는 V104가 전부 꺼 두었고 다시 켜지지 않는다({@code StoryService.setShared} 400 ·
 * 만들기의 값은 무시 — 동결 미니앱 호환). 열은 미니앱 동결이 끝날 때까지 남긴다.
 *
 * <p><b>{@code @DynamicUpdate}</b>: AI 결과 저장은 호출 뒤 다시 읽어 <b>AI 열만</b> 바꾼다 — 그사이 사용자가 저장한 생각을
 * 옛 값으로 덮지 않게({@code WritingDraft}와 같은 이유 · v1 REQ-19).
 */
@Entity
@DynamicUpdate
@Table(name = "story")
public class Story extends BaseTimeEntity {

    /** 배경 팔레트 닫힌 코드 — 자유 문자열(hex 등) 금지: 스타일 주입 차단. 프론트 CSS 스와치와 코드 일치. */
    public static final Set<String> BG_CODES = Set.of("paper", "night", "forest", "sunset", "sea", "plum");

    /**
     * 생각 상한 — 옛 글의 제목 100 + 빈 줄 + 본문 10,000이 늘 들어가는 값(J3 — 기기 칸 옮기기가 10,102자를 보낸다).
     * 앱 입력칸은 1만 자다. {@code @Lob} 금지 — 마이그레이션의 text를 H2가 CHARACTER VARYING으로 보고한다(V85 · V89).
     */
    public static final int TEXT_MAX = 10_200;

    /** 질문 기록 JSON 상한 — v1 {@code WritingDraft.QUESTIONS_JSON_MAX}와 같다. */
    static final int AI_LOG_MAX = 20_000;

    /** 출처 스냅샷 상한 — 『제목 300』 · 저자 200 + 꾸밈 5자. */
    static final int QUOTE_SOURCE_MAX = 510;

    /** 인용은 주석보다 짧다 — 시각 위계이자 「인용은 從」이라는 규칙의 수치 표현. */
    private static final int MAX_QUOTE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작성자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 쪽지가 놓인 책 (선택 — C9). 책이 사라지면 연결만 풀린다({@code BookService.delete} → {@code unlinkBook}). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(nullable = false, length = TEXT_MAX)
    private String text;

    /**
     * 책에서 옮긴 문장 (선택) — 없으면 {@code null}이고, 그러면 카드는 2026-08-20 이전과 똑같이 그려진다.
     *
     * <p><b>빈 문자열은 저장하지 않는다</b>(공백뿐이면 {@code of}가 null로 떨어뜨린다) — 화면이
     * {@code quote != null}만 보고 인용 블록을 그리므로, 빈 문자열이 남으면 글 없는 인용선이 그려진다.
     *
     * <p>쪽수는 두지 않는다(2026-08-20 결정) — ebook은 글자 크기·기기마다 쪽이 달라져서 적어 봐야
     * 읽는 사람이 못 찾는 숫자이고, 종이책 판본까지 섞이면 더 어긋난다.
     */
    @Column(length = 200)
    private String quote;

    /** 옮긴 문장의 『제목』 · 저자 스냅샷 — 책을 지워도 출처가 남는다(J84). 옮긴 문장이 없으면 null. */
    @Column(name = "quote_source", length = QUOTE_SOURCE_MAX)
    private String quoteSource;

    @Column(name = "bg_code", length = 20)
    private String bgCode;

    /** 고치기 판 — 앱 PUT이 대조한다(낡은 판 409 · 아무것도 안 바꿈). AI 저장은 올리지 않는다. */
    @Column(nullable = false)
    private int revision;

    /** 이 쪽지의 AI 질문 기록(v1 {@code QuestionLog} JSON) — 없으면 null. */
    @Column(name = "ai_log", length = AI_LOG_MAX)
    private String aiLog;

    @Column(name = "ai_updated_at")
    private Instant aiUpdatedAt;

    @Column(name = "ai_cost_micro_usd", nullable = false)
    private long aiCostMicroUsd;

    /** 공유 시트로 처음 내보낸 시각(C8 · v1 REQ-37) — 내보낸 적 없으면 null. */
    @Column(name = "exported_at")
    private Instant exportedAt;

    /**
     * 「함께 걸기」 — 이 글을 <b>같은 책(isbn13)을 보는 누구에게나</b> 열지(팔로우 무관) 여부.
     *
     * <p><b>기본 꺼짐</b>이고 V77의 {@code default false}가 기존 글 전부에 그것을 보장한다. 다만
     * <b>「소급 노출 0」은 2026-08-22에 깨졌다</b> — 팔로우 축을 걷으면서 「팔로워에게 보여요」를 보고
     * 쓴 기존 글까지 함께 열었다(사용자 결정). 켜고 끄는 것은 여전히 명시적 행동이다.
     *
     * <p>이것만으로는 아무것도 열리지 않는다 — 클래스 javadoc의 불변식대로 <b>책 게이트가 상위</b>다.
     */
    @Column(nullable = false)
    private boolean shared = false;

    protected Story() {
        // JPA
    }

    private Story(User user, String text, Book book, String bgCode, String quote) {
        this.user = user;
        this.text = text;
        this.book = book;
        this.bgCode = bgCode;
        this.quote = quote;
        this.quoteSource = quote == null ? null : sourceLine(book);
    }

    /**
     * 인용 없이 남기는 글 — 2026-08-20 이전의 유일한 모양이자 지금도 기본이다.
     *
     * <p>오버로드로 남긴 이유는 호출부가 서른 곳 넘고 전부 「인용 없음」이라서다. 인자로 {@code null}을
     * 하나씩 붙이는 편집은 diff만 늘리고 정보를 주지 않는다.
     */
    public static Story of(User author, String text, Book book, String bgCode) {
        return of(author, text, book, bgCode, null);
    }

    /**
     * 쪽지를 남긴다. 생각은 최대 {@value #TEXT_MAX}자, 책은 <b>선택</b>이고 있으면 작성자 소유만(공개 여부 무관),
     * 배경은 닫힌 팔레트 코드만, 옮긴 문장은 선택이고 최대 200자이며 <b>책이 있을 때만</b>(J84).
     *
     * <p><b>생각({@code text})은 옮긴 문장이 있을 때만 비울 수 있다</b>(REQ-93) — 공백뿐이면 {@code ""}로 저장한다.
     *
     * @throws IllegalArgumentException author가 없거나, 생각·옮긴 문장이 둘 다 비었거나, 길이 초과, 남의 책,
     *                                  팔레트 밖 bgCode, 책 없는 옮긴 문장 — 문구는 앱 400 본문으로 그대로 나간다
     */
    public static Story of(User author, String text, Book book, String bgCode, String quote) {
        if (author == null) {
            throw new IllegalArgumentException("author must not be null");
        }
        if (book != null && !isSameUser(book.getUser(), author)) {
            throw new IllegalArgumentException("book must be owned by the author");
        }
        if (bgCode != null && !BG_CODES.contains(bgCode)) {
            throw new IllegalArgumentException("unknown bgCode: " + bgCode);
        }
        String strippedQuote = normalizeQuote(quote, book != null);
        String stripped = normalizeText(text);
        if (stripped.isEmpty() && strippedQuote == null) {
            throw new IllegalArgumentException("옮긴 문장이나 생각을 하나는 적어 주세요");
        }
        return new Story(author, stripped, book, bgCode, strippedQuote);
    }

    /**
     * 쪽지를 고친다(앱 PUT) — 판 대조가 먼저다(낡은 판이면 아무것도 바꾸지 않는다 — {@code WritingDraft.edit}와 같은 규칙).
     *
     * <p>생각·옮긴 문장이 둘 다 비어도 <b>AI 기록이 있으면</b> 받는다 — 질문 기록만 남은 옮긴 쪽지(원가가 든 글 — J4 · v1 REQ-142).
     * 옮긴 문장은 책이나 출처 스냅샷이 있을 때만 둘 수 있다(J84). 처음 옮긴 문장을 붙이면 지금 책으로 출처를 채운다.
     *
     * @throws StaleMarginException     낡은 판
     * @throws IllegalArgumentException 길이 · 빈 쪽지 · 출처 없는 옮긴 문장
     */
    public void edit(String quote, String text, int expectedRevision) {
        if (expectedRevision != this.revision) {
            throw new StaleMarginException(expectedRevision, this.revision);
        }
        String nextQuote = normalizeQuote(quote, book != null || quoteSource != null);
        String nextText = normalizeText(text);
        if (nextText.isEmpty() && nextQuote == null && aiLog == null) {
            throw new IllegalArgumentException("옮긴 문장이나 생각을 하나는 적어 주세요");
        }
        if (nextQuote != null && quoteSource == null) {
            quoteSource = sourceLine(book);
        }
        this.quote = nextQuote;
        this.text = nextText;
        this.revision++;
    }

    /** AI 한 라운드 저장 — AI 열만 바꾼다(생각·옮긴 문장·판은 그대로 — v1 REQ-47). */
    public void recordRound(String aiLogJson, long microUsd, Instant now) {
        this.aiLog = aiLogJson;
        this.aiUpdatedAt = now;
        this.aiCostMicroUsd += microUsd;
    }

    /** 결과 없는 AI 실패의 원가만 더한다. */
    public void addAiCost(long microUsd) {
        this.aiCostMicroUsd += microUsd;
    }

    /** 처음 한 번만 내보낸 시각을 박는다(C8). */
    public void markExported(Instant now) {
        if (this.exportedAt == null) {
            this.exportedAt = now;
        }
    }

    /**
     * 옮긴 문장의 출처 줄 — 『제목』 · 저자(저자가 없으면 『제목』). V104 백필 SQL과 {@code BookService.delete}(→
     * {@code StoryRepository.unlinkBook})가 같은 꼴을 쓴다 — 바꾸면 셋을 함께 고친다.
     */
    public static String sourceLine(Book book) {
        if (book == null) {
            return null;
        }
        String author = book.getAuthor();
        return "『" + book.getTitle() + "』" + (author == null || author.isEmpty() ? "" : " · " + author);
    }

    /** 낡은 판으로 고치려 했다 — 앱에는 409. */
    public static class StaleMarginException extends RuntimeException {
        public StaleMarginException(int expected, int actual) {
            super("stale revision: expected=" + expected + " actual=" + actual);
        }
    }

    // 공백뿐인 생각은 "" — 옛 남의 읽기 경로가 「생각 빈 여백」을 s.text <> '' 하나로 걸렀다(REQ-93)
    private static String normalizeText(String text) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.length() > TEXT_MAX) {
            throw new IllegalArgumentException("생각은 " + String.format("%,d", TEXT_MAX) + "자까지 쓸 수 있어요");
        }
        return stripped;
    }

    // 공백뿐인 인용은 「인용 없음」이다 — 빈 문자열을 저장하면 화면에 글 없는 인용선이 남는다
    private static String normalizeQuote(String quote, boolean sourced) {
        String stripped = (quote == null || quote.isBlank()) ? null : quote.strip();
        if (stripped == null) {
            return null;
        }
        if (stripped.length() > MAX_QUOTE_LENGTH) {
            throw new IllegalArgumentException("옮긴 문장은 " + MAX_QUOTE_LENGTH + "자까지 옮길 수 있어요");
        }
        if (!sourced) {
            throw new IllegalArgumentException("책 없는 쪽지엔 옮긴 문장을 둘 수 없어요 — 책을 골라 주세요");
        }
        return stripped;
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Book getBook() {
        return book;
    }

    public String getText() {
        return text;
    }

    /** 생각 빈 여백(인용만 남긴 글)인가 — 나만 보는 여백이다(REQ-93~95, 클래스 javadoc). */
    public boolean isQuoteOnly() {
        return text.isEmpty();
    }

    /** 책에서 옮긴 문장 — 인용 없이 남긴 글이면 {@code null}. */
    public String getQuote() {
        return quote;
    }

    public String getQuoteSource() {
        return quoteSource;
    }

    public int getRevision() {
        return revision;
    }

    public String getAiLog() {
        return aiLog;
    }

    public Instant getAiUpdatedAt() {
        return aiUpdatedAt;
    }

    public long getAiCostMicroUsd() {
        return aiCostMicroUsd;
    }

    public Instant getExportedAt() {
        return exportedAt;
    }

    public String getBgCode() {
        return bgCode;
    }

    /** 옛 「함께 걸기」 값 — M1부터 늘 false다(V104 · {@code StoryService.setShared} 400). 읽는 경로는 없다. */
    public boolean isShared() {
        return shared;
    }

    /** 「함께 걸기」 값을 바꾼다 — 켜는 문({@code StoryService.setShared(true)})은 M1부터 400이라 끄기만 지난다. */
    public void markShared(boolean shared) {
        this.shared = shared;
    }
}
