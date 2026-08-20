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
 * 좋아요 도메인 불변식 — 「누가 어느 글에」 한 번뿐. 중복 방지는 DB 유니크가 최종 방어라
 * 여기서 남는 도메인 검증은 <b>null 거부</b>뿐이고, 노출 게이트(차단·비공개·비팔로워)는 관계를 봐야 해서
 * {@link StoryService} 몫이다.
 *
 * <p><b>자기 글 금지는 걷어냈다</b>(2026-08-20): 여백에 글을 쓴 사람이 아직 없어 좋아요를 확인할 길이
 * 없다는 실사용 요구였고, 막을 근거도 약했다(트위터·인스타 모두 자기 글에 눌린다). 그 결과 불변식이
 * 「받은 글은 전부 누를 수 있다」 하나로 줄어 클라이언트의 `likable()` 분기가 통째로 사라졌다.
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
    @DisplayName("자기 글에도 좋아요를 만든다 — 도메인은 더 이상 막지 않는다(2026-08-20)")
    void allowsSelfLike() {
        User author = userWithId(1L, "author");
        Story mine = storyOf(author);

        StoryLike like = StoryLike.of(author, mine);

        assertThat(like.getUser()).isSameAs(author);
        assertThat(like.getStory()).isSameAs(mine);
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
