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
    @DisplayName("of: 500자 문장 + 팔레트 배경 + 책 없음 → 생성된다")
    void of_allowsMaxLengthTextWithoutBook() {
        User me = author();

        Story story = Story.of(me, "가".repeat(500), null, "night");

        assertThat(story.getText()).hasSize(500);
        assertThat(story.getBgCode()).isEqualTo("night");
        assertThat(story.getBook()).isNull();
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
        Story story = Story.of(author(), "문장", null, null);

        assertThat(story.getBgCode()).isNull();
    }

    @Test
    @DisplayName("of: 501자는 거부한다")
    void of_rejects501Chars() {
        assertThatThrownBy(() -> Story.of(author(), "가".repeat(501), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: null·공백만 문장은 거부한다")
    void of_rejectsBlankText() {
        assertThatThrownBy(() -> Story.of(author(), "   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Story.of(author(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: author null은 거부한다")
    void of_rejectsNullAuthor() {
        assertThatThrownBy(() -> Story.of(null, "문장", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: 내 책이라도 PRIVATE면 거부한다 — 비공개 책 간접 누출 차단(sns-design §13.2)")
    void of_rejectsPrivateOwnBook() {
        User me = author();
        Book mine = Book.register(me, "비공개 책", null, null, null, null, null, BookStatus.READING); // 기본 PRIVATE

        assertThatThrownBy(() -> Story.of(me, "문장", mine, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: 남의 책은 PUBLIC이라도 거부한다")
    void of_rejectsOthersBook() {
        User me = author();
        User other = User.of("other@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", "타인", "Asia/Seoul", Role.USER);
        Book others = publicBookOf(other);

        assertThatThrownBy(() -> Story.of(me, "문장", others, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("of: 팔레트 밖 bgCode는 거부한다(자유 문자열·hex 주입 차단)")
    void of_rejectsUnknownBgCode() {
        assertThatThrownBy(() -> Story.of(author(), "문장", null, "#ff0000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Story.of(author(), "문장", null, "neon"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
