package com.booktimer.story;

import com.booktimer.book.Book;
import com.booktimer.book.BookStatus;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 좋아요 도메인 불변식 — 「누가 어느 글에」 한 번뿐이고, 자기 글에는 못 누른다.
 *
 * <p>중복 방지는 DB 유니크가 최종 방어이고, 여기서는 <b>자기 글 금지</b>만 도메인으로 못 박는다 —
 * 노출 게이트(차단·비공개·비팔로워)는 관계를 봐야 해서 {@link StoryService} 몫이다.
 */
class StoryLikeTest {

    private User userWithId(long id, String nickname) {
        User u = User.of(nickname + "@booktimer.com", "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Story storyOf(User author) {
        Book book = Book.register(author, "책", null, null, null, null, null, BookStatus.READING);
        return Story.of(author, "문장", book, null);
    }

    @Test
    @DisplayName("남의 글에 좋아요를 만든다")
    void createsLikeOnOthersStory() {
        User author = userWithId(1L, "author");
        User liker = userWithId(2L, "liker");
        Story story = storyOf(author);

        StoryLike like = StoryLike.of(liker, story);

        assertThat(like.getUser()).isSameAs(liker);
        assertThat(like.getStory()).isSameAs(story);
    }

    @Test
    @DisplayName("자기 글에는 좋아요를 못 만든다 — 여백은 내 노트라 전부 자기 좋아요가 된다")
    void rejectsSelfLike() {
        User author = userWithId(1L, "author");

        assertThatThrownBy(() -> StoryLike.of(author, storyOf(author)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null은 거부한다")
    void rejectsNull() {
        User liker = userWithId(2L, "liker");
        Story story = storyOf(userWithId(1L, "author"));

        assertThatThrownBy(() -> StoryLike.of(null, story)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StoryLike.of(liker, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
