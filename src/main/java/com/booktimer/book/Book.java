package com.booktimer.book;

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

/**
 * 사용자 책장에 등록된 책. User와 N:1.
 *
 * <p>제목 외 메타데이터(저자·ISBN·표지·출판사·구매링크)는 도서 검색 API(알라딘)에서 받아 채우거나,
 * 검색이 불가할 때 수동 입력한다. 구매링크는 추후 제휴(어필리에이트) 수익의 토대가 된다.
 * 책별 누적 독서 시간(세션 연결)은 다음 증분의 확장점이다.
 */
@Entity
@Table(name = "book")
public class Book extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 200)
    private String author;

    /** ISBN-13. 검색으로 등록 시 채워지고, 같은 책 식별/제휴에 쓰인다. */
    @Column(length = 20)
    private String isbn13;

    @Column(length = 500)
    private String coverUrl;

    @Column(length = 200)
    private String publisher;

    /** 구매 링크(제휴 태그 포함 가능). 검색 결과에서 받는다. */
    @Column(length = 1000)
    private String purchaseLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status;

    protected Book() {
        // JPA
    }

    private Book(User user, String title, String author, String isbn13,
                 String coverUrl, String publisher, String purchaseLink, BookStatus status) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.user = user;
        this.title = title.strip();
        this.author = author;
        this.isbn13 = isbn13;
        this.coverUrl = coverUrl;
        this.publisher = publisher;
        this.purchaseLink = purchaseLink;
        this.status = status;
    }

    /**
     * 책장에 새 책을 등록한다.
     *
     * @param user   소유자(필수)
     * @param title  제목(필수, 공백 불가)
     * @param status 초기 상태(필수)
     */
    public static Book register(User user, String title, String author, String isbn13,
                                String coverUrl, String publisher, String purchaseLink, BookStatus status) {
        return new Book(user, title, author, isbn13, coverUrl, publisher, purchaseLink, status);
    }

    /** 상태 변경(읽고싶음 → 읽는중 → 완독 등). */
    public void changeStatus(BookStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = newStatus;
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

    public String getAuthor() {
        return author;
    }

    public String getIsbn13() {
        return isbn13;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getPurchaseLink() {
        return purchaseLink;
    }

    public BookStatus getStatus() {
        return status;
    }
}
