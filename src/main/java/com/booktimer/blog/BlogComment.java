package com.booktimer.blog;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
