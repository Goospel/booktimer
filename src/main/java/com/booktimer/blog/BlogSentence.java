package com.booktimer.blog;

import com.booktimer.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 올린 글의 문장 하나 — 주소 {@code sid}(8자 base62 · 전역 유일)는 판 비교가 잇는 한 그대로다(REQ-232). 산 문장 =
 * {@code deleted_at} null. 합쳐지면 {@code merged_into}(그리고 죽는다) · 나뉜 조각은 {@code split_from} = 옛 sid.
 * 순서는 여기 없고 {@code blog_post.layout_json}이 단일 출처다.
 */
@Entity
@Table(name = "blog_sentence",
        uniqueConstraints = @UniqueConstraint(name = "uq_blog_sentence_sid", columnNames = "sid"))
public class BlogSentence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR) // char(8) ascii_bin(V106) — AiSpendMonth와 같은 선례
    @Column(nullable = false, length = 8)
    private String sid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private BlogPost post;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(nullable = false)
    private int rev;

    @JdbcTypeCode(SqlTypes.CHAR) // char(8) ascii_bin(V106) — AiSpendMonth와 같은 선례
    @Column(name = "merged_into", length = 8)
    private String mergedInto;

    @JdbcTypeCode(SqlTypes.CHAR) // char(8) ascii_bin(V106) — AiSpendMonth와 같은 선례
    @Column(name = "split_from", length = 8)
    private String splitFrom;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BlogSentence() {
    }

    public static BlogSentence create(BlogPost post, String sid, String text, String splitFrom) {
        BlogSentence s = new BlogSentence();
        s.post = post;
        s.sid = sid;
        s.text = text;
        s.rev = 1;
        s.splitFrom = splitFrom;
        return s;
    }

    /** 고친 문장 — 판+1(예전 판 행은 부르는 쪽이 남긴다). */
    public void edit(String text) {
        this.text = text;
        this.rev++;
    }

    /** 공백 무리만 다른 같은 문장 — 판 그대로(§4.10). */
    public void retext(String text) {
        this.text = text;
    }

    public void mergeInto(String sid, Instant now) {
        this.mergedInto = sid;
        this.deletedAt = now;
    }

    public void tombstone(Instant now) {
        this.deletedAt = now;
    }

    public boolean live() {
        return deletedAt == null;
    }

    public Long getId() {
        return id;
    }

    public String getSid() {
        return sid;
    }

    public BlogPost getPost() {
        return post;
    }

    public String getText() {
        return text;
    }

    public int getRev() {
        return rev;
    }

    public String getMergedInto() {
        return mergedInto;
    }

    public String getSplitFrom() {
        return splitFrom;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
