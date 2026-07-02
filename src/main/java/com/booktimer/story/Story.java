package com.booktimer.story;

import com.booktimer.book.Book;
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

import java.util.Set;

/**
 * 독서 스토리 — 팔로워에게 24시간만 보이는 텍스트 카드 (sns-design §13).
 *
 * <p>만료는 물리 삭제가 아니라 표시 필터다({@code createdAt} 기준 24h — §13.6). 책 첨부는
 * <b>본인 소유 + PUBLIC만</b> — 책 라벨(제목·표지)이 팔로워에게 보이므로, PRIVATE 첨부를 허용하면
 * 비공개 책장이 새는 유일 경로가 된다(§13.2, §3.5 불변식 예외 없이 유지).
 */
@Entity
@Table(name = "story")
public class Story extends BaseTimeEntity {

    /** 배경 팔레트 닫힌 코드 — 자유 문자열(hex 등) 금지: 스타일 주입 차단. 프론트 CSS 스와치와 코드 일치. */
    public static final Set<String> BG_CODES = Set.of("paper", "night", "forest", "sunset", "sea", "plum");

    private static final int MAX_TEXT_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작성자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 선택 첨부 책 — 책 삭제 시 첨부만 풀린다(book_id = null, 문장은 보존). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "bg_code", length = 20)
    private String bgCode;

    protected Story() {
        // JPA
    }

    private Story(User user, String text, Book book, String bgCode) {
        this.user = user;
        this.text = text;
        this.book = book;
        this.bgCode = bgCode;
    }

    /**
     * 스토리를 만든다. 문장은 1~500자 비공백, 책은 작성자 소유 + PUBLIC만, 배경은 닫힌 팔레트 코드만.
     *
     * @throws IllegalArgumentException author/문장이 없거나, 501자 초과, 남의 책·비공개 책, 팔레트 밖 bgCode
     */
    public static Story of(User author, String text, Book book, String bgCode) {
        if (author == null) {
            throw new IllegalArgumentException("author must not be null");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        String stripped = text.strip();
        if (stripped.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text must be at most " + MAX_TEXT_LENGTH + " chars");
        }
        if (book != null) {
            if (!isSameUser(book.getUser(), author)) {
                throw new IllegalArgumentException("book must be owned by the author");
            }
            if (!book.isPublic()) {
                throw new IllegalArgumentException("book must be public");
            }
        }
        if (bgCode != null && !BG_CODES.contains(bgCode)) {
            throw new IllegalArgumentException("unknown bgCode: " + bgCode);
        }
        return new Story(author, stripped, book, bgCode);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Book getBook() {
        return book;
    }

    public String getText() {
        return text;
    }

    public String getBgCode() {
        return bgCode;
    }
}
