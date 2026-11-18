package com.booktimer.blog;

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
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/**
 * 블로그 글 한 편(계획 2026-11-11-b1-blog-core §5.1) — 초안 글자({@code draft_*} — 자동저장)와 올린 판({@code title} ·
 * {@code layout_json} — 올릴 때만 바뀐다 · J11 · J89)이 한 행에 산다. 문장은 {@link BlogSentence}, 순서는 배치({@link Layout}).
 *
 * <p>{@code hidden_at} · {@code hide_kind} · {@code hide_note}는 운영자 가림(B2b · {@link Moderation}) — 남에게서 뺀다.
 *
 * <p><b>{@code @DynamicUpdate}</b>(§4.11): 잠그지 않는 초안 저장이 올리기와 겹치면 전 열 UPDATE가 올리기가 쓴 제목 · 배치 · 올린
 * 시각을 옛 스냅샷으로 오류 없이 덮는다 — 바뀐 열만 쓰게 해 저장({@code draft_*})과 올리기가 서로 덮지 않는다
 * ({@code ChatRoom} · {@code Story} · {@code WritingDraft} · {@code User}와 같은 까닭 · REQ-230 BlogPostRaceTest).
 */
@Entity
@DynamicUpdate
@Table(name = "blog_post")
public class BlogPost extends BaseTimeEntity {

    public static final int TITLE_MAX = 100;
    public static final int BODY_MAX = 10_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 올린 제목 — 처음 올리기 전엔 null(초안 제목과 다르다 — REQ-245). */
    @Column(length = TITLE_MAX)
    private String title;

    @Column(name = "draft_title", length = TITLE_MAX)
    private String draftTitle;

    // @Lob 금지 — 마이그레이션의 text를 H2가 CHARACTER VARYING으로 보고한다(V85 · V89)
    @Column(name = "draft_body", nullable = false, length = BODY_MAX)
    private String draftBody;

    @Column(name = "draft_revision", nullable = false)
    private int draftRevision;

    @Column(name = "layout_json", length = 65_535)
    private String layoutJson;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "republished_at")
    private Instant republishedAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "hide_kind", length = 10)
    private Moderation.Kind hideKind;

    @Column(name = "hide_note", length = Moderation.NOTE_MAX)
    private String hideNote;

    /** 글쓴이가 새 댓글을 마지막으로 확인한 시각(B2a · V108) — 엔티티로 쓰지 않는다: {@link BlogPostRepository#markSeen}(감사 0). */
    @Column(name = "author_seen_comments_at") // 바꾸는 메서드 0 + @DynamicUpdate — 엔티티 저장이 이 열을 쓰지 않는다
    private Instant authorSeenCommentsAt;

    protected BlogPost() {
    }

    /**
     * 첫 저장 때 행을 만든다(J6 — 빈 행 0). 본문 {@code null}은 빈 글자(draft_body NOT NULL).
     *
     * @throws IllegalArgumentException 둘 다 빔 · 길이
     */
    public static BlogPost draft(User user, String title, String body) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        String b = body == null ? "" : body;
        if ((title == null || title.isBlank()) && b.isBlank()) {
            throw new IllegalArgumentException("제목이나 본문을 적어 주세요");
        }
        BlogPost p = new BlogPost();
        p.user = user;
        p.setDraft(title, b);
        return p;
    }

    /**
     * 초안 저장 — 판 대조가 먼저다(낡은 판이면 아무것도 바꾸지 않는다 — {@code Story.edit}와 같은 규칙).
     *
     * @throws StaleBlogDraftException 낡은 판
     * @throws IllegalArgumentException 길이
     */
    public void saveDraft(String title, String body, int expectedRevision) {
        if (expectedRevision != draftRevision) {
            throw new StaleBlogDraftException(expectedRevision, draftRevision);
        }
        setDraft(title, body == null ? "" : body);
        draftRevision++;
    }

    private void setDraft(String title, String body) {
        if (title != null && title.length() > TITLE_MAX) {
            throw new IllegalArgumentException("제목은 100자까지 쓸 수 있어요");
        }
        if (body.length() > BODY_MAX) {
            throw new IllegalArgumentException("본문은 10,000자까지 쓸 수 있어요");
        }
        this.draftTitle = title;
        this.draftBody = body;
    }

    /**
     * 올린 판을 바꾼다. 처음이면 {@code publishedAt}, 다시 올리기면 제목 · 배치 · 문장 가운데 하나라도 바뀐 때만
     * {@code republishedAt}(§4.10 — 「고쳐 올린 날짜」는 공개 항목이다).
     *
     * @return 무엇이든 바뀌었나(첫 올리기는 참)
     */
    public boolean publish(String title, String layoutJson, boolean sentencesChanged, Instant now) {
        boolean first = publishedAt == null;
        boolean changed = first || sentencesChanged || !title.equals(this.title) || !layoutJson.equals(this.layoutJson);
        if (first) {
            publishedAt = now;
        } else if (changed) {
            republishedAt = now;
        }
        this.title = title;
        this.layoutJson = layoutJson;
        return changed;
    }

    /** 인용 · 걸기 · 붙이기 · 반영의 원 글 판정(§6.4) — 올림 ∧ 가리지 않음. <b>주인도 예외 없다</b>(J52). */
    public boolean quotable() {
        return publishedAt != null && hiddenAt == null;
    }

    /** 운영자 가림 전이(§6.5 — {@link Moderation#next}가 정한 값을 그대로 쓴다). */
    void moderate(Moderation.Next next) {
        this.hiddenAt = next.hiddenAt();
        this.hideKind = next.kind();
        this.hideNote = next.note();
    }

    public boolean hidden() {
        return hiddenAt != null;
    }

    public boolean removed() {
        return hideKind == Moderation.Kind.REMOVED;
    }

    public Moderation.View moderation() {
        return Moderation.of(hiddenAt, hideKind, hideNote);
    }

    public Moderation.Kind getHideKind() {
        return hideKind;
    }

    public String getHideNote() {
        return hideNote;
    }

    /** 운영자 삭제(REMOVED — §4.5) — 제목 · 초안 · 배치를 비우고 행은 남긴다. 문장 · 판 · 박스 · 댓글은 부르는 쪽이 지운다(이 다음에). */
    void markRemoved() {
        this.title = null;
        this.draftTitle = null;
        this.draftBody = "";
        this.layoutJson = null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public String getDraftTitle() {
        return draftTitle;
    }

    public String getDraftBody() {
        return draftBody;
    }

    public int getDraftRevision() {
        return draftRevision;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getRepublishedAt() {
        return republishedAt;
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public Instant getAuthorSeenCommentsAt() {
        return authorSeenCommentsAt;
    }

    public static class StaleBlogDraftException extends RuntimeException {
        public StaleBlogDraftException(int expected, int actual) {
            super("stale revision: expected=" + expected + " actual=" + actual);
        }
    }
}
