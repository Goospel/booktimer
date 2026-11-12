package com.booktimer.blog;

import com.booktimer.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 인용 박스 하나(계획 2026-11-11-b1-blog-core §5.2 · V107) — 글({@link Owner#POST})과 쪽지({@link Owner#NOTE})가 함께 쓴다.
 * 주인이 다형이라 FK가 없다: 주인을 지우는 길(글 지우기 · 쪽지 지우기 · 탈퇴)이 {@link QuoteBoxRepository}의 지우기를 반드시 부른다.
 * 원문 참조({@code source_*})는 논리 참조라 원문이 지워져도 박스 글은 남는다(D5).
 */
@Entity
@Table(name = "quote_box",
        uniqueConstraints = @UniqueConstraint(name = "uq_quote_box_owner_label", columnNames = {"owner_type", "owner_id", "label"}))
public class QuoteBox extends BaseTimeEntity {

    public enum Owner { POST, NOTE }

    public enum Kind { BLOG, MARGIN, BOOK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 10)
    private Owner ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private int label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(name = "source_post_id")
    private Long sourcePostId;

    @Column(name = "source_user_id")
    private Long sourceUserId;

    /** 구간 sid(쉼표) — 인용 · 반영 때(§4.5). @Lob 금지 — 마이그레이션의 text를 H2가 CHARACTER VARYING으로 보고한다(V85 · V89). */
    @Column(length = 4_600)
    private String members;

    @Column(name = "seen_sentence_id")
    private Long seenSentenceId;

    @Column(name = "source_story_id")
    private Long sourceStoryId;

    @Column(name = "source_isbn13", length = 20)
    private String sourceIsbn13;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(length = 510)
    private String attribution;

    @Column(name = "quoted_at", nullable = false)
    private Instant quotedAt;

    protected QuoteBox() {
    }

    private static QuoteBox of(Owner owner, long ownerId, int label, Kind kind, String text, Instant now) {
        QuoteBox q = new QuoteBox();
        q.ownerType = owner;
        q.ownerId = ownerId;
        q.label = label;
        q.kind = kind;
        q.text = text;
        q.quotedAt = now;
        return q;
    }

    /** 블로그 문장 구간 — 원 글 · 원 글쓴이 · 구성원 · 그때 원 글의 가장 큰 행 id · 원 글 제목 스냅샷. */
    public static QuoteBox blog(Owner owner, long ownerId, int label, long postId, long authorId, List<String> members,
                                long seen, String text, String title, Instant now) {
        QuoteBox q = of(owner, ownerId, label, Kind.BLOG, text, now);
        q.sourcePostId = postId;
        q.sourceUserId = authorId;
        q.members = String.join(",", members);
        q.seenSentenceId = seen;
        q.attribution = title;
        return q;
    }

    /** 내 여백(쪽지 생각)의 구간 — 글에만. */
    public static QuoteBox margin(long postId, int label, long storyId, String text, Instant now) {
        QuoteBox q = of(Owner.POST, postId, label, Kind.MARGIN, text, now);
        q.sourceStoryId = storyId;
        return q;
    }

    /** 쪽지의 옮긴 문장 — 출처 = 쪽지의 『제목』 · 저자 스냅샷(J84) · 책 ISBN(J75). 글에만. */
    public static QuoteBox book(long postId, int label, long storyId, String quote, String quoteSource, String isbn13,
                                Instant now) {
        QuoteBox q = of(Owner.POST, postId, label, Kind.BOOK, quote, now);
        q.sourceStoryId = storyId;
        q.attribution = quoteSource;
        q.sourceIsbn13 = isbn13;
        return q;
    }

    /** 최신으로 반영(§6.5) — 제목 스냅샷도 반영 때 것으로(§4.4 「지워졌으면 인용(반영) 때 제목」). */
    public void refresh(List<String> members, String text, long seen, String title, Instant now) {
        this.members = String.join(",", members);
        this.text = text;
        this.seenSentenceId = seen;
        this.attribution = title;
        this.quotedAt = now;
    }

    /** 여백 박스 다시 고르기. */
    public void repick(String text, Instant now) {
        this.text = text;
        this.quotedAt = now;
    }

    /** 구성원 — 원 글쓴이 탈퇴로 비었으면 빈 목록. */
    public List<String> members() {
        return members == null || members.isEmpty() ? List.of() : Arrays.asList(members.split(","));
    }

    public Long getId() {
        return id;
    }

    public Owner getOwnerType() {
        return ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public int getLabel() {
        return label;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getSourcePostId() {
        return sourcePostId;
    }

    public Long getSourceUserId() {
        return sourceUserId;
    }

    public Long getSeenSentenceId() {
        return seenSentenceId;
    }

    public Long getSourceStoryId() {
        return sourceStoryId;
    }

    public String getSourceIsbn13() {
        return sourceIsbn13;
    }

    public String getText() {
        return text;
    }

    public String getAttribution() {
        return attribution;
    }

    public Instant getQuotedAt() {
        return quotedAt;
    }
}
