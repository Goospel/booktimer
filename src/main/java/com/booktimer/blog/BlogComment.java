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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 문장 댓글 하나(B2a · V108 · J39 — 평평한 목록 · 고치기 없음 · 지우기만). {@code sid}는 그 글의 문장 주소(논리 참조) — 합쳐진 옛
 * 문장의 댓글은 합침 사슬의 끝이 같은 묶음으로 함께 보인다({@link PostSentences#group}). 검증은 서비스가 한다.
 */
@Entity
@Table(name = "blog_comment")
public class BlogComment extends BaseTimeEntity {

    public static final int BODY_MAX = 1_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private BlogPost post;

    @JdbcTypeCode(SqlTypes.CHAR) // char(8) ascii_bin(V108) — BlogSentence와 같은 꼴
    @Column(nullable = false, length = 8)
    private String sid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = BODY_MAX)
    private String body;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "hide_kind", length = 10)
    private Moderation.Kind hideKind;

    @Column(name = "hide_note", length = Moderation.NOTE_MAX)
    private String hideNote;

    protected BlogComment() {
    }

    public static BlogComment of(BlogPost post, String sid, User user, String body) {
        BlogComment c = new BlogComment();
        c.post = post;
        c.sid = sid;
        c.user = user;
        c.body = body;
        return c;
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

    /** 운영자 삭제 — 본문을 비우고 자리는 남긴다(§4.5). */
    void markRemoved() {
        this.body = "";
    }

    public Instant getHiddenAt() {
        return hiddenAt;
    }

    public Long getId() {
        return id;
    }

    public BlogPost getPost() {
        return post;
    }

    public String getSid() {
        return sid;
    }

    public User getUser() {
        return user;
    }

    public String getBody() {
        return body;
    }
}
