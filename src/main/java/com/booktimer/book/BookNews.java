package com.booktimer.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 책(ISBN) 관련 뉴스 기사 캐시 — 새벽 배치({@link BookNewsCollector})가 채우고 홈 「책 뉴스」가 읽는다.
 *
 * <p><b>스코프가 책 카탈로그(isbn)지 사용자 책 행이 아니다</b> — 같은 책을 여러 사람이 완독해도 기사는
 * 하나면 되고(외부 호출·저장 절약), 그래서 {@code book} 테이블로의 FK를 두지 않는다. 책 삭제 경로에
 * FK 정리 부담을 만들지 않는 것이 덤(T-023 클래스 원천 회피). 노출은 조회 시점에 "내 완독 책의 isbn"과
 * 조인해 거른다.
 *
 * <p>{@code (isbn13, link)} 유니크가 재수집 중복을 DB에서 막는다. 갱신은 delete-then-insert.
 * {@link com.booktimer.common.BaseTimeEntity}를 쓰지 않는 이유: 이 행은 수정되지 않고 통째로 교체되므로
 * {@code updated_at}이 의미가 없다 — {@code created_at}만 둔다.
 */
@Entity
@Table(name = "book_news",
        uniqueConstraints = @UniqueConstraint(name = "uk_book_news_isbn_link", columnNames = {"isbn13", "link"}))
@EntityListeners(AuditingEntityListener.class)
public class BookNews {

    /** 저장 상한 — 기사 제목은 대개 짧지만 외부 데이터라 방어적으로 자른다(길이 초과로 배치가 죽지 않게). */
    private static final int MAX_TITLE_LENGTH = 300;

    /** 출처명 상한 — 같은 이유로 방어적으로 자른다. */
    private static final int MAX_SOURCE_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 기사가 붙는 책의 ISBN-13(정규화된 표기). 조회 조인 키라 인덱스가 있다(V65). */
    @Column(nullable = false, length = 20)
    private String isbn13;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    /** 기사 링크. 유니크 키의 일부라 MySQL 인덱스 길이 상한(3072바이트) 안에 들도록 500자로 제한한다. */
    @Column(nullable = false, length = 500)
    private String link;

    /** 발행 시각. RSS pubDate를 못 읽으면 null(정렬에서 뒤로 밀릴 뿐 줄은 산다). */
    @Column
    private Instant publishedAt;

    /** 매체명(구글 뉴스 RSS의 {@code <source>}). 응답에 없으면 null — 미니앱이 라벨을 생략한다. */
    @Column(length = MAX_SOURCE_LENGTH)
    private String source;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected BookNews() {
        // JPA
    }

    private BookNews(String isbn13, String title, String link, Instant publishedAt, String source) {
        this.isbn13 = isbn13;
        this.title = truncate(title, MAX_TITLE_LENGTH);
        this.link = link;
        this.publishedAt = publishedAt;
        this.source = truncate(source, MAX_SOURCE_LENGTH);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    /** 수집된 기사 한 건을 만든다. isbn13·제목·링크는 필수(수집기가 빈 항목을 걸러 넘긴다), 출처는 선택. */
    public static BookNews of(String isbn13, String title, String link, Instant publishedAt, String source) {
        if (isbn13 == null || isbn13.isBlank()) {
            throw new IllegalArgumentException("isbn13 must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (link == null || link.isBlank()) {
            throw new IllegalArgumentException("link must not be blank");
        }
        return new BookNews(isbn13, title, link, publishedAt, source);
    }

    public Long getId() {
        return id;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public String getTitle() {
        return title;
    }

    public String getLink() {
        return link;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
