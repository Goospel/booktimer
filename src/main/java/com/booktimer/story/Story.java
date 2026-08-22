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
 * <p>글은 <b>두 층</b>이다(2026-08-20): 책에서 옮긴 {@code quote}(선택)와 그에 다는 {@code text}(필수).
 * 위계는 뒤집지 않는다 — 인용이 主가 되면 여백이 「내 독서 기록」이 아니라 명언 카드가 되고, 정당한
 * 인용의 요건(내 글이 主·인용이 從)에서도 멀어진다. 그래서 인용만 있고 주석이 빈 글은 만들 수 없다.
 *
 * <p>책은 <b>본인 소유만</b> — 공개 여부는 묻지 않는다(2026-08-16 결정 2). 비공개 책의 여백은
 * 「나만 보는 메모」라서 쓰기를 막을 이유가 없다.
 *
 * <p><b>가시성 불변식</b>(2026-08-22 팔로우 축 제거로 재정의): 가시성은 <b>읽기 시점</b>에 판정하며,
 * <b>노출 = {@code book.isPublic()}. 그게 전부다.</b> 쓰기 시점엔 아무것도 검사하지 않는다 — 비공개
 * 책에도 글을 남길 수 있고(결정 2), 그 글은 책이 공개되는 순간부터 보인다.
 * <b>공개 검사를 지우거나 우회하는 코드는 이 불변식 위반이다</b> — 예전엔 팔로우 게이트가 한 겹 더
 * 받쳤지만 지금은 이것이 <b>유일한 방어</b>라, 뚫리면 곧바로 비공개 메모가 낯선 사람에게 샌다.
 *
 * <p><b>{@code shared}는 권한이 아니다</b>(같은 날 개정). 「모두의 여백」에 올릴지를 정하는 <b>배치
 * 값</b>이고, 안 올린 글도 공개 책이면 읽힌다 — 열람 여부가 아니라 <b>발견 경로</b>를 가른다.
 * 옛 문서는 노출을 {@code isPublic() AND (팔로워 OR shared)}라 적었는데 두 항 모두 게이트에서 빠졌다.
 *
 * <p>읽기 게이트가 사는 곳: 단건 {@code StoryService.assertVisible}(좋아요·명단이 공유) · 사람축 목록
 * {@code StoryService.marginOf} · 책축 목록 {@code StoryRepository.sharedByIsbn} · 홈 소식
 * {@code StoryRepository.feedRecent} · 프로필 격자(PUBLIC 책만 목록에 있음). 앞의 셋은 <b>미러가
 * 아니다</b> — 책축 목록만 {@code shared = true}를 추가로 진다(올린 글만 거기 실리므로 의도된 차이다).
 * <b>미러로 착각해 그 술어를 걷으면 올리지 않은 글이 통째로 「모두의 여백」에 실린다.</b>
 * 팔로우는 {@code feedRecent}(소식 구독)에만 남았고, 그것은 권한이 아니라 <b>피드 선택</b>이다.
 */
@Entity
@Table(name = "story")
public class Story extends BaseTimeEntity {

    /** 배경 팔레트 닫힌 코드 — 자유 문자열(hex 등) 금지: 스타일 주입 차단. 프론트 CSS 스와치와 코드 일치. */
    public static final Set<String> BG_CODES = Set.of("paper", "night", "forest", "sunset", "sea", "plum");

    private static final int MAX_TEXT_LENGTH = 500;

    /** 인용은 주석보다 짧다 — 시각 위계이자 「인용은 從」이라는 규칙의 수치 표현. */
    private static final int MAX_QUOTE_LENGTH = 200;

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

    /**
     * 책에서 옮긴 문장 (선택) — 없으면 {@code null}이고, 그러면 카드는 2026-08-20 이전과 똑같이 그려진다.
     *
     * <p><b>빈 문자열은 저장하지 않는다</b>(공백뿐이면 {@code of}가 null로 떨어뜨린다) — 화면이
     * {@code quote != null}만 보고 인용 블록을 그리므로, 빈 문자열이 남으면 글 없는 인용선이 그려진다.
     *
     * <p>쪽수는 두지 않는다(2026-08-20 결정) — ebook은 글자 크기·기기마다 쪽이 달라져서 적어 봐야
     * 읽는 사람이 못 찾는 숫자이고, 종이책 판본까지 섞이면 더 어긋난다.
     */
    @Column(length = 200)
    private String quote;

    @Column(name = "bg_code", length = 20)
    private String bgCode;

    /**
     * 「함께 걸기」 — 이 글을 <b>같은 책(isbn13)을 보는 누구에게나</b> 열지(팔로우 무관) 여부.
     *
     * <p><b>기본 꺼짐</b>이고 V77의 {@code default false}가 기존 글 전부에 그것을 보장한다. 다만
     * <b>「소급 노출 0」은 2026-08-22에 깨졌다</b> — 팔로우 축을 걷으면서 「팔로워에게 보여요」를 보고
     * 쓴 기존 글까지 함께 열었다(사용자 결정). 켜고 끄는 것은 여전히 명시적 행동이다.
     *
     * <p>이것만으로는 아무것도 열리지 않는다 — 클래스 javadoc의 불변식대로 <b>책 게이트가 상위</b>다.
     */
    @Column(nullable = false)
    private boolean shared = false;

    protected Story() {
        // JPA
    }

    private Story(User user, String text, Book book, String bgCode, String quote) {
        this.user = user;
        this.text = text;
        this.book = book;
        this.bgCode = bgCode;
        this.quote = quote;
    }

    /**
     * 인용 없이 남기는 글 — 2026-08-20 이전의 유일한 모양이자 지금도 기본이다.
     *
     * <p>오버로드로 남긴 이유는 호출부가 서른 곳 넘고 전부 「인용 없음」이라서다. 인자로 {@code null}을
     * 하나씩 붙이는 편집은 diff만 늘리고 정보를 주지 않는다.
     */
    public static Story of(User author, String text, Book book, String bgCode) {
        return of(author, text, book, bgCode, null);
    }

    /**
     * 여백에 글을 남긴다. 문장은 1~500자 비공백, 책은 <b>필수</b>이고 작성자 소유만(공개 여부 무관),
     * 배경은 닫힌 팔레트 코드만, 인용은 선택이고 최대 200자.
     *
     * <p><b>주석({@code text})은 인용이 있어도 필수</b>다 — 인용만 남기는 글은 이 검사에 걸린다.
     *
     * @throws IllegalArgumentException author/문장/책이 없거나, 501자 초과, 남의 책, 팔레트 밖 bgCode,
     *                                  201자 초과 인용
     */
    public static Story of(User author, String text, Book book, String bgCode, String quote) {
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
        // 공백뿐인 인용은 「인용 없음」이다 — 빈 문자열을 저장하면 화면에 글 없는 인용선이 남는다
        String strippedQuote = (quote == null || quote.isBlank()) ? null : quote.strip();
        if (strippedQuote != null && strippedQuote.length() > MAX_QUOTE_LENGTH) {
            throw new IllegalArgumentException("quote must be at most " + MAX_QUOTE_LENGTH + " chars");
        }
        return new Story(author, stripped, book, bgCode, strippedQuote);
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

    /** 책에서 옮긴 문장 — 인용 없이 남긴 글이면 {@code null}. */
    public String getQuote() {
        return quote;
    }

    public String getBgCode() {
        return bgCode;
    }

    /** 「함께 걸림」인가 — 책축 목록·좋아요 게이트가 읽는 두 번째 통행증(책 게이트는 여전히 상위). */
    public boolean isShared() {
        return shared;
    }

    /**
     * 「함께 걸기」를 켜거나 끈다. <b>책 공개 여부를 검사하지 않는다</b> — 비공개 책에서 미리 켜 두는
     * 것도 유효하고, 그 글은 책이 공개되는 순간부터만 보인다(불변식은 읽기 시점 판정이다).
     */
    public void markShared(boolean shared) {
        this.shared = shared;
    }
}
