package com.booktimer.follow;

import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.book.BookVisibility;
import com.booktimer.search.UserSearchResult;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 팔로워/팔로잉 목록 서비스 통합 테스트 (실제 빈 + H2).
 *
 * <p>본인 전용 목록 — 나를 팔로우한 사람들(followers)·내가 팔로우한 사람들(following)을 사용자 행으로
 * 돌려준다. 행의 {@code following}(내가 그를 팔로우 중인지)·{@code publicBookCount} 매핑까지 본다.
 */
@SpringBootTest
@Transactional
class FollowListServiceTest {

    @Autowired
    private FollowListService followListService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FollowRepository followRepository;
    @Autowired
    private BookRepository bookRepository;

    private User user(String email, String loginId, String nick) {
        User u = User.of(email, "$2a$10$x", nick, "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.saveAndFlush(u);
    }

    private void follow(User follower, User followee) {
        followRepository.saveAndFlush(Follow.of(follower, followee));
    }

    private Book publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.changeVisibility(BookVisibility.PUBLIC);
        return b;
    }

    @Test
    @DisplayName("팔로워 목록 = 나를 팔로우한 사람들. 내가 그를 팔로우 중인지(following) 함께 표시")
    void followers() {
        User viewer = user("v@booktimer.com", "flsvc_v", "뷰어");
        User a = user("a@booktimer.com", "flsvc_a", "에이");
        User b = user("b@booktimer.com", "flsvc_b", "비");
        follow(a, viewer);  // A → 나
        follow(b, viewer);  // B → 나
        follow(viewer, a);  // 나 → A (맞팔)

        List<UserSearchResult> rows = followListService.followers(viewer);

        assertThat(rows).extracting(UserSearchResult::nickname)
                .containsExactlyInAnyOrder("에이", "비");
        assertThat(rows).extracting(UserSearchResult::nickname, UserSearchResult::following)
                .contains(tuple("에이", true), tuple("비", false)); // A는 맞팔, B는 내가 안 팔로우
        // 공개책이 한 권도 없는 대상은 배치 집계에서 행이 빠지므로 0으로 디폴트돼야 한다(0-디폴트 경로)
        assertThat(rows).allMatch(r -> r.publicBookCount() == 0);
    }

    @Test
    @DisplayName("팔로잉 목록 = 내가 팔로우한 사람들. 공개 책수 포함, following=true·self=false")
    void following() {
        User viewer = user("v2@booktimer.com", "flsvc_v2", "뷰어2");
        User c = user("c@booktimer.com", "flsvc_c", "씨");
        follow(viewer, c);
        bookRepository.saveAndFlush(publicBook(c, "공개1"));
        bookRepository.saveAndFlush(publicBook(c, "공개2"));
        bookRepository.saveAndFlush( // PRIVATE 기본 → 공개책수에서 제외
                Book.register(c, "비공개", null, null, null, null, null, BookStatus.READING));

        List<UserSearchResult> rows = followListService.following(viewer);

        assertThat(rows).extracting(UserSearchResult::nickname).containsExactly("씨");
        assertThat(rows.get(0).following()).isTrue();
        assertThat(rows.get(0).publicBookCount()).isEqualTo(2);
        assertThat(rows.get(0).self()).isFalse();
    }
}
