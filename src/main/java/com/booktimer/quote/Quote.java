package com.booktimer.quote;

import com.booktimer.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 작가 격언 한 줄 — 대시보드 인사말 자리에 랜덤 노출하는 단위.
 *
 * <p>초기엔 정적 JSON이었으나, 운영자가 런타임에 추가/삭제할 수 있도록 DB(`quote` 테이블)로 옮겼다
 * (plan.md "③ DB 테이블" 진화). 출처 표기를 위해 문장({@link #text})과 작가({@link #author})를 한 쌍으로 둔다.
 */
@Entity
@Table(name = "quote")
public class Quote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false, length = 100)
    private String author;

    protected Quote() {
        // JPA
    }

    private Quote(String text, String author) {
        this.text = text;
        this.author = author;
    }

    /**
     * 격언을 만든다 — 문장·작가는 공백일 수 없다(앞뒤 공백은 다듬는다).
     *
     * @throws IllegalArgumentException 문장 또는 작가가 null·공백인 경우
     */
    public static Quote of(String text, String author) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("author must not be blank");
        }
        return new Quote(text.trim(), author.trim());
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public String getAuthor() {
        return author;
    }
}
