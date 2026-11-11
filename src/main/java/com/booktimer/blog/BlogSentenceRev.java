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

/** 문장 하나의 판 기록 — 판 1부터 덧붙이기만(「예전 문장 보기」 = 지금 판보다 작은 판 — J38). */
@Entity
@Table(name = "blog_sentence_rev",
        uniqueConstraints = @UniqueConstraint(name = "uq_blog_sentence_rev", columnNames = {"sentence_id", "rev"}))
public class BlogSentenceRev extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sentence_id")
    private BlogSentence sentence;

    @Column(nullable = false)
    private int rev;

    @Column(nullable = false, length = 1000)
    private String text;

    protected BlogSentenceRev() {
    }

    public static BlogSentenceRev of(BlogSentence sentence, int rev, String text) {
        BlogSentenceRev r = new BlogSentenceRev();
        r.sentence = sentence;
        r.rev = rev;
        r.text = text;
        return r;
    }

    public int getRev() {
        return rev;
    }

    public String getText() {
        return text;
    }
}
