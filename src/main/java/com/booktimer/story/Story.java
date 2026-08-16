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
 * 여백에 남긴 글 한 장 — 읽다가 인상 깊은 문장·생각을 적어 두는 텍스트 카드 (sns-design §13).
 *
 * <p>「여백」은 <b>책에 딸린 자리</b>고, 이 클래스는 거기 쌓이는 <b>글</b>이다. 그래서 {@code book}은
 * 선택이 아니라 <b>필수</b>다(2026-08-16 재설계 — V71이 DB에도 NOT NULL로 못 박았다). 도달 경로가
 * 「책방 격자 → 책 → 그 책의 글」뿐이라, 책 없는 글은 표시 표면이 0인 유령 행이 된다.
 *
 * <p>2026-08-16까지는 사람에게 딸린 24시간짜리 「스토리」였다 — 시간 만료도, 사람 단위 스트립도 함께
 * 폐기됐다. 클래스·테이블 이름은 {@code Story}로 남았다: 어휘만 바꾸고 스키마는 두는 게 싸다.
 *
 * <p>책은 <b>본인 소유만</b> — 공개 여부는 묻지 않는다(2026-08-16 결정 2). 비공개 책의 여백은
 * 「나만 보는 메모」라서 쓰기를 막을 이유가 없다. 대신 <b>가시성은 언제나 읽기 시점에 책에서
 * 파생</b>한다 — 글에 자체 공개 필드가 없으므로 남에게 새지 않게 하는 책임은 전적으로 읽기
 * 게이트에 있다: {@code StoryService.marginOf}(소유자 아니면 PRIVATE 책은 404) ·
 * {@code StoryRepository.feedRecent}(쿼리가 PUBLIC만) · 프로필 격자(PUBLIC 책만 목록에 있음).
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

    /** 글이 놓인 여백의 책 (필수) — 책이 사라지면 그 자리도 사라진다({@code BookService.delete}가 함께 지운다). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
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
     * 여백에 글을 남긴다. 문장은 1~500자 비공백, 책은 <b>필수</b>이고 작성자 소유만(공개 여부 무관),
     * 배경은 닫힌 팔레트 코드만.
     *
     * @throws IllegalArgumentException author/문장/책이 없거나, 501자 초과, 남의 책, 팔레트 밖 bgCode
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
        if (book == null) {
            throw new IllegalArgumentException("book must not be null"); // 여백은 책에 귀속된다
        }
        if (!isSameUser(book.getUser(), author)) {
            throw new IllegalArgumentException("book must be owned by the author");
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
