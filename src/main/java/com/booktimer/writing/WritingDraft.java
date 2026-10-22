package com.booktimer.writing;

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

/**
 * 앱 「책생글」에서 쓰는 글 한 편 — 글 원장(v1 §5.1.3 · V101).
 *
 * <p><b>{@code @DynamicUpdate}</b>(v1 §3.3-7): AI 결과 저장(PR-C)은 호출 뒤 다시 읽어 <b>AI 열만</b> 바꾼다. 전 열
 * UPDATE면 AI 호출 동안 사용자가 저장한 본문을 AI 쪽 저장이 옛 값으로 덮는다.
 *
 * <p>{@link #revision}은 자동저장의 낙관적 판 번호다 — {@code StudyNote}와 같은 규율이다. 대조와 증가가
 * {@link #edit} 한 몸이고, 본문을 바꾸는 메서드는 그것 하나다(REQ-47 — AI 글자가 글에 들어가는 길이 없다).
 *
 * <p>{@link #book}은 독서 서재({@code book})만 가리킨다 — 공부 서재({@code study_book})는 FK부터 다르다(결정 5).
 * 책을 지우면 연결만 풀린다({@code BookService.delete} — 글은 사용자 것이다, REQ-13).
 */
@Entity
@DynamicUpdate
@Table(name = "writing_draft")
public class WritingDraft extends BaseTimeEntity {

    public static final int BODY_MAX = 10_000;
    public static final int TITLE_MAX = 100;

    /** 질문 기록 JSON 상한 — 운영 열은 text(약 64KB)이고 이 값은 메인 스위트 H2 스키마의 크기다(V85 주석). */
    static final int QUESTIONS_JSON_MAX = 20_000;

    /** 낡은 판으로 덮어쓰려 했다 — 서비스가 409로 옮긴다({@code StudyNote.StaleNoteException}과 같은 이유로 런타임 예외). */
    public static class StaleDraftException extends RuntimeException {
        public StaleDraftException(int expected, int actual) {
            super("stale revision: expected=" + expected + " actual=" + actual);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(length = TITLE_MAX)
    private String title;

    // @Lob 금지 — 마이그레이션의 text를 H2가 CHARACTER VARYING으로 보고해 validate가 깨진다(V85·V89).
    @Column(nullable = false, length = BODY_MAX)
    private String body;

    @Column(nullable = false)
    private int revision;

    @Column(name = "questions_json", length = QUESTIONS_JSON_MAX)
    private String questionsJson;

    @Column(name = "ai_updated_at")
    private Instant aiUpdatedAt;

    @Column(name = "ai_cost_micro_usd", nullable = false)
    private long aiCostMicroUsd;

    @Column(name = "exported_at")
    private Instant exportedAt;

    protected WritingDraft() {
        // JPA
    }

    /** 빈 본문으로 시작한다 — 화면은 편집기를 열 때 이 행을 만든다. */
    public static WritingDraft start(User user, Book book) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        WritingDraft draft = new WritingDraft();
        draft.user = user;
        draft.book = book;
        draft.body = "";
        return draft;
    }

    /**
     * 제목·본문을 갈아 쓴다 — <b>기대 판이 맞을 때만</b>. 본문을 바꾸는 유일한 메서드다.
     *
     * @throws StaleDraftException      그 사이 다른 곳에서 고쳐진 경우 — 아무것도 바꾸지 않는다
     * @throws IllegalArgumentException 본문이 없거나 길이를 넘는 경우 — 아무것도 바꾸지 않는다
     */
    public void edit(String title, String body, int expectedRevision) {
        if (expectedRevision != this.revision) {
            throw new StaleDraftException(expectedRevision, this.revision);
        }
        String nextTitle = optionalTitle(title);
        if (body == null) {
            throw new IllegalArgumentException("본문이 없어요");
        }
        if (body.length() > BODY_MAX) {
            throw new IllegalArgumentException("글은 " + String.format("%,d", BODY_MAX) + "자까지 쓸 수 있어요");
        }
        this.title = nextTitle;
        this.body = body;
        this.revision++;
    }

    /** AI 한 라운드의 결과 — 질문 기록·AI 시각·원가만. 제목·본문·판은 건드리지 않는다(PR-C가 부른다). */
    public void recordRound(String questionsJson, long microUsd, Instant now) {
        this.questionsJson = questionsJson;
        this.aiUpdatedAt = now;
        this.aiCostMicroUsd += microUsd;
    }

    /** 결과 없는 실패(응답 못 받은 추정 · 가드가 비운 결과)의 원가. */
    public void addCost(long microUsd) {
        this.aiCostMicroUsd += microUsd;
    }

    /** 공유 시트로 처음 내보낸 때 — 두 번째부터는 그대로 둔다(REQ-16). */
    public void markExported(Instant now) {
        if (this.exportedAt == null) {
            this.exportedAt = now;
        }
    }

    /** 첫 비공백 줄 80자 — 목록 라벨의 재료(StudyNote.preview와 같은 규칙, 마크다운 표식은 화면 몫). */
    public String preview() {
        String line = body.lines().map(String::strip).filter(s -> !s.isEmpty()).findFirst().orElse("");
        return line.length() > 80 ? line.substring(0, 80) : line;
    }

    /** 빈 제목은 {@code null}, 개행은 공백으로 접는다(한 줄 라벨 — StudyNote와 같은 이유). */
    private static String optionalTitle(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip().replaceAll("[\\r\\n]+", " ");
        if (trimmed.length() > TITLE_MAX) {
            throw new IllegalArgumentException("제목은 " + TITLE_MAX + "자까지 쓸 수 있어요");
        }
        return trimmed;
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

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public int getRevision() {
        return revision;
    }

    public String getQuestionsJson() {
        return questionsJson;
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
}
