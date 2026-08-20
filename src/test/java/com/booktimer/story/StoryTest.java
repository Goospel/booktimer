package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryTest {

    private User author() {
        return User.of("author@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "작가", "Asia/Seoul", Role.USER);
    }

    private Book publicBookOf(User owner) {
        Book book = Book.register(owner, "내 책", null, null, null, null, null, BookStatus.READING);
        book.makePublic();
        return book;
    }

    @Test
    @DisplayName("of: 500자 문장 + 팔레트 배경 → 생성된다")
    void of_allowsMaxLengthText() {
        User me = author();
        Book mine = publicBookOf(me);

        Story story = Story.of(me, "가".repeat(500), mine, "night");

        assertThat(story.getText()).hasSize(500);
        assertThat(story.getBgCode()).isEqualTo("night");
        assertThat(story.getBook()).isSameAs(mine);
        assertThat(story.getUser()).isSameAs(me);
    }

    @Test
    @DisplayName("of: 공개 상태의 내 책은 첨부할 수 있다")
    void of_allowsOwnPublicBook() {
        User me = author();
        Book mine = publicBookOf(me);

        Story story = Story.of(me, "인상 깊은 문장", mine, null);

        assertThat(story.getBook()).isSameAs(mine);
    }

    @Test
    @DisplayName("of: bgCode null은 허용한다(기본 배경)")
    void of_allowsNullBgCode() {
        User me = author();

        Story story = Story.of(me, "문장", publicBookOf(me), null);

        assertThat(story.getBgCode()).isNull();
    }

    @Test
    @DisplayName("of: 책 없는 글은 거부한다 — 여백은 책에 귀속된다(2026-08-16 재설계)")
    void of_rejectsNullBook() {
        User me = author();

        assertThatThrownBy(() -> Story.of(me, "책 없는 문장", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: 501자는 거부한다")
    void of_rejects501Chars() {
        User me = author();

        assertThatThrownBy(() -> Story.of(me, "가".repeat(501), publicBookOf(me), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: null·공백만 문장은 거부한다")
    void of_rejectsBlankText() {
        User me = author();
        Book mine = publicBookOf(me);

        assertThatThrownBy(() -> Story.of(me, "   ", mine, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Story.of(me, null, mine, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: author null은 거부한다")
    void of_rejectsNullAuthor() {
        assertThatThrownBy(() -> Story.of(null, "문장", publicBookOf(author()), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * <b>기대 반전</b>(2026-08-16, 결정 2) — 옛 단언은 「내 책이라도 PRIVATE면 거부」였다.
     * 비공개 책의 여백은 나만 보는 메모이므로 <b>쓸 수 있어야</b> 한다. 남에게 안 보이게 막는 것은
     * 이제 엔티티가 아니라 읽기 게이트의 몫이다({@code StoryService.marginOf} · {@code feedRecent}).
     */
    @Test
    @DisplayName("of: 내 PRIVATE 책에도 글을 남길 수 있다 — 비공개 책 여백 = 나만의 메모(결정 2)")
    void of_allowsPrivateOwnBook() {
        User me = author();
        Book mine = Book.register(me, "비공개 책", null, null, null, null, null, BookStatus.READING); // 기본 PRIVATE

        Story story = Story.of(me, "나만 보는 메모", mine, null);

        assertThat(story.getBook()).isSameAs(mine);
        assertThat(story.getText()).isEqualTo("나만 보는 메모");
    }

    @Test
    @DisplayName("of: 남의 책은 PUBLIC이든 PRIVATE이든 거부한다 — 소유 불변식은 완화 대상이 아니다")
    void of_rejectsOthersBook() {
        User me = author();
        User other = User.of("other@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "타인", "Asia/Seoul", Role.USER);
        Book othersPublic = publicBookOf(other);
        Book othersPrivate = Book.register(other, "남의 비공개 책", null, null, null, null, null, BookStatus.READING);

        assertThatThrownBy(() -> Story.of(me, "문장", othersPublic, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Story.of(me, "문장", othersPrivate, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: 팔레트 밖 bgCode는 거부한다(자유 문자열·hex 주입 차단)")
    void of_rejectsUnknownBgCode() {
        User me = author();
        Book mine = publicBookOf(me);

        assertThatThrownBy(() -> Story.of(me, "문장", mine, "#ff0000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Story.of(me, "문장", mine, "neon"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── 인용문 (2026-08-20) — 「책의 문장 + 내 주석」 두 층 ────────────────────────────

    @Test
    @DisplayName("of: 인용은 선택 — 4-arg 오버로드로 남긴 글은 quote가 null")
    void of_quoteIsOptional() {
        User me = author();

        Story story = Story.of(me, "내 생각", publicBookOf(me), null);

        assertThat(story.getQuote()).isNull();
    }

    @Test
    @DisplayName("of: 인용 앞뒤 공백은 잘라 낸다(주석 text와 같은 정규화)")
    void of_stripsQuote() {
        User me = author();

        Story story = Story.of(me, "내 생각", publicBookOf(me), null, "  새는 알에서 나오려고 투쟁한다.  ");

        assertThat(story.getQuote()).isEqualTo("새는 알에서 나오려고 투쟁한다.");
    }

    @Test
    @DisplayName("of: 공백뿐인 인용은 null로 떨어뜨린다 — 빈 문자열이 남으면 카드에 빈 인용선이 그려진다")
    void of_blankQuoteBecomesNull() {
        User me = author();
        Book mine = publicBookOf(me);

        assertThat(Story.of(me, "내 생각", mine, null, "   ").getQuote()).isNull();
        assertThat(Story.of(me, "내 생각", mine, null, "").getQuote()).isNull();
    }

    @Test
    @DisplayName("of: 인용 200자는 허용, 201자는 거부 (경계)")
    void of_quoteLengthBoundary() {
        User me = author();
        Book mine = publicBookOf(me);

        assertThat(Story.of(me, "내 생각", mine, null, "가".repeat(200)).getQuote()).hasSize(200);
        assertThatThrownBy(() -> Story.of(me, "내 생각", mine, null, "가".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: 인용만 있고 주석이 비면 거부 — 주석은 여전히 필수다(2026-08-20 확정)")
    void of_rejectsQuoteWithoutText() {
        User me = author();
        Book mine = publicBookOf(me);

        assertThatThrownBy(() -> Story.of(me, "   ", mine, null, "새는 알에서 나오려고 투쟁한다."))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
