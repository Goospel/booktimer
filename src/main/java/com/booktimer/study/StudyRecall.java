package com.booktimer.study;

import com.booktimer.book.StudyBook;
import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 백지복습 한 장 — 「그날 빈 종이에 쏟아낸 글」과 그 분석 결과.
 *
 * <p>UNIQUE(user, recall_date)가 <b>하루 한 장</b>을 강제한다. 상한이 아니라 달력 대응이다 — 칸 하나에
 * 글 하나여야 「그날의 복습」이라는 표식이 성립한다. 같은 날 다시 저장하면 덮어쓴다.
 *
 * <p>분석 결과를 되돌릴 수 있게 만든 것이 요점이다({@link #rewrite}): 분석한 뒤 본문을 고치면 옛 정리·구멍·
 * 문제가 <b>새 글에 대한 거짓</b>이 된다. 그래서 본문이 바뀌면 셋을 비운다.
 */
@Entity
@Table(name = "study_recall",
        uniqueConstraints = @UniqueConstraint(name = "uq_study_recall",
                columnNames = {"user_id", "recall_date"}))
public class StudyRecall extends BaseTimeEntity {

    /** 본문 상한 — 백지복습 한 장의 현실적인 최대치이자 입력 토큰 비용의 울타리다. */
    public static final int BODY_MAX = 8000;

    /** 「범위」 상한 — 구멍 판정의 울타리라 길어질 수 있지만 무한하진 않다. */
    public static final int SCOPE_MAX = 4000;

    /** 과목 상한 — {@code study_book.title}과 같다(스냅샷이라 원본보다 길 이유가 없다). */
    public static final int SUBJECT_MAX = 300;

    /** 글이 어디서 왔나 — 타이핑인지, 사진을 읽어 사용자가 확인한 전사인지. */
    public enum Source {
        TEXT,
        PHOTO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 유저 타임존의 달력 날짜 — 달력 칸의 키다. */
    @Column(name = "recall_date", nullable = false)
    private LocalDate recallDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private StudyBook book;

    @Column(length = SUBJECT_MAX)
    private String subject;

    @Column(name = "scope_text", length = SCOPE_MAX)
    private String scopeText;

    // @Lob을 쓰지 않는다 — 마이그레이션의 text 컬럼은 H2가 CHARACTER VARYING으로 보고해 CLOB 기대와
    // 어긋나고, FlywayMigrationTest(ddl-auto=validate)가 컨텍스트 로딩째로 죽는다. 길이를 명시하는 쪽이
    // 메인 스위트(엔티티에서 만든 H2 스키마)의 상한과도 같아진다.
    @Column(nullable = false, length = BODY_MAX)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(length = BODY_MAX)
    private String summary;

    /** 구멍 — JSON 배열 문자열. 항목 안에 줄바꿈이 있어 개행 구분보다 안전하다. */
    @Column(name = "holes_json", length = SCOPE_MAX)
    private String holesJson;

    /** 다음날 복습문제 — JSON 배열 문자열. */
    @Column(name = "questions_json", length = SCOPE_MAX)
    private String questionsJson;

    @Column(length = 60)
    private String model;

    /** {@code null}이면 「저장만 함」이다 — 분석 여부의 단일 출처. */
    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    protected StudyRecall() {
        // JPA
    }

    private StudyRecall(User user, LocalDate recallDate) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (recallDate == null) {
            throw new IllegalArgumentException("날짜가 없어요");
        }
        this.user = user;
        this.recallDate = recallDate;
    }

    /**
     * 새 한 장.
     *
     * @throws IllegalArgumentException 본문이 비었거나 길이를 넘는 경우 — 문구가 그대로 400 본문이 된다
     */
    public static StudyRecall of(User user, LocalDate recallDate, StudyBook book,
                                 String subject, String scope, String body, Source source) {
        StudyRecall recall = new StudyRecall(user, recallDate);
        recall.rewrite(book, subject, scope, body, source);
        return recall;
    }

    /**
     * 본문을 갈아 쓴다 — <b>분석 결과는 함께 비운다</b>. 옛 정리가 새 글에 붙어 있으면 거짓이고,
     * 「이 정리는 어느 글의 것인가」를 화면이 구분할 방법이 없다.
     *
     * @throws IllegalArgumentException 본문이 비었거나 길이를 넘는 경우
     */
    public void rewrite(StudyBook book, String subject, String scope, String body, Source source) {
        this.book = book;
        this.subject = optionalText(subject, SUBJECT_MAX, "과목");
        this.scopeText = optionalText(scope, SCOPE_MAX, "범위");
        this.body = requireBody(body);
        this.source = source == null ? Source.TEXT : source;
        this.summary = null;
        this.holesJson = null;
        this.questionsJson = null;
        this.model = null;
        this.analyzedAt = null;
    }

    /** 분석 결과를 붙인다. {@code analyzedAt}이 채워지는 유일한 자리라 재분석은 호출부가 409로 막는다. */
    public void applyAnalysis(String summary, String holesJson, String questionsJson,
                              String model, Instant analyzedAt) {
        this.summary = summary;
        this.holesJson = holesJson;
        this.questionsJson = questionsJson;
        this.model = model;
        this.analyzedAt = analyzedAt;
    }

    /** 책 삭제 시 참조를 푼다 — 글 자체는 남는다(subject 스냅샷이 제목을 대신 든다). */
    public void unlinkBook() {
        this.book = null;
    }

    public boolean isAnalyzed() {
        return analyzedAt != null;
    }

    private static String requireBody(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("쓴 내용을 입력해 주세요");
        }
        String trimmed = value.strip();
        if (trimmed.length() > BODY_MAX) {
            throw new IllegalArgumentException("쓴 내용은 " + BODY_MAX + "자까지 쓸 수 있어요");
        }
        return trimmed;
    }

    private static String optionalText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + "은 " + max + "자까지 쓸 수 있어요");
        }
        return trimmed;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getRecallDate() {
        return recallDate;
    }

    public StudyBook getBook() {
        return book;
    }

    public String getSubject() {
        return subject;
    }

    public String getScopeText() {
        return scopeText;
    }

    public String getBody() {
        return body;
    }

    public Source getSource() {
        return source;
    }

    public String getSummary() {
        return summary;
    }

    public String getHolesJson() {
        return holesJson;
    }

    public String getQuestionsJson() {
        return questionsJson;
    }

    public String getModel() {
        return model;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
