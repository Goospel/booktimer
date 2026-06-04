package com.booktimer.search;

import com.booktimer.block.BlockService;
import com.booktimer.book.Book;
import com.booktimer.book.BookRepository;
import com.booktimer.book.BookStatus;
import com.booktimer.follow.FollowService;
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

/**
 * UserSearchService 통합 테스트 (실제 빈 + H2) — 닉네임 검색 (sns-design §7.3).
 *
 * <p>핵심: ① 부분일치, ② 최소 2글자 미만이면 빈 결과, ③ 결과 상한 20, ④ 공개 책 수는 PUBLIC만,
 * ⑤ 본인은 self·내가 팔로우 중인 사람은 following 플래그.
 */
@SpringBootTest
@Transactional
class UserSearchServiceTest {

    @Autowired
    private UserSearchService searchService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private FollowService followService;
    @Autowired
    private BlockService blockService;

    private User newUser(String email, String nickname) {
        return userRepository.save(User.of(email, "$2a$10$abcdefghijklmnopqrstuv", nickname, "Asia/Seoul", Role.USER));
    }

    private void publicBook(User owner, String title) {
        Book b = Book.register(owner, title, null, null, null, null, null, BookStatus.READING);
        b.makePublic();
        bookRepository.save(b);
    }

    private void privateBook(User owner, String title) {
        bookRepository.save(Book.register(owner, title, null, null, null, null, null, BookStatus.READING));
    }

    @Test
    @DisplayName("부분일치로 닉네임을 찾고, 공개 책 수·팔로우 여부·본인 플래그를 채운다")
    void search_partialMatch_withFlags() {
        User viewer = newUser("viewer@booktimer.com", "검색가");
        User target = newUser("t@booktimer.com", "독서왕");
        publicBook(target, "공개책1");
        publicBook(target, "공개책2");
        privateBook(target, "비공개책"); // 공개 책 수에 안 잡혀야 함
        newUser("other@booktimer.com", "관계자"); // "독서" 미포함 → 결과 제외
        followService.follow(viewer, target);

        List<UserSearchResult> results = searchService.search(viewer, "독서");

        assertThat(results).hasSize(1);
        UserSearchResult r = results.get(0);
        assertThat(r.nickname()).isEqualTo("독서왕");
        assertThat(r.publicBookCount()).isEqualTo(2L); // PUBLIC만
        assertThat(r.following()).isTrue();
        assertThat(r.self()).isFalse();
    }

    @Test
    @DisplayName("검색어가 2글자 미만이면 빈 결과(열거 완화)")
    void search_tooShort_empty() {
        User viewer = newUser("viewer@booktimer.com", "검색가");
        newUser("t@booktimer.com", "독서왕");

        assertThat(searchService.search(viewer, "독")).isEmpty();
        assertThat(searchService.search(viewer, " ")).isEmpty();
        assertThat(searchService.search(viewer, null)).isEmpty();
    }

    @Test
    @DisplayName("본인이 검색 결과에 걸리면 self=true (팔로우 버튼 대신 '나' 표시용)")
    void search_self_flagged() {
        User viewer = newUser("viewer@booktimer.com", "독서가");

        List<UserSearchResult> results = searchService.search(viewer, "독서");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).self()).isTrue();
        assertThat(results.get(0).following()).isFalse();
    }

    @Test
    @DisplayName("차단 관계(양방향)인 사용자는 검색 결과에서 제외된다")
    void search_excludesBlocked() {
        User viewer = newUser("viewer@booktimer.com", "검색가");
        User blocked = newUser("b@booktimer.com", "독서왕");   // viewer가 차단
        User blocker = newUser("c@booktimer.com", "독서광");   // viewer를 차단(역방향)
        newUser("v@booktimer.com", "독서가");                  // 차단 무관 — 남아야 함
        blockService.block(viewer, blocked);
        blockService.block(blocker, viewer);

        List<UserSearchResult> results = searchService.search(viewer, "독서");

        assertThat(results).extracting(UserSearchResult::nickname)
                .containsExactly("독서가")               // 차단 무관만 남음
                .doesNotContain("독서왕", "독서광");      // 양방향 모두 숨김
    }

    @Test
    @DisplayName("결과는 최대 20명으로 제한된다(상한 가드)")
    void search_cappedAt20() {
        User viewer = newUser("viewer@booktimer.com", "검색가");
        for (int i = 1; i <= 22; i++) {
            newUser("u" + i + "@booktimer.com", String.format("북클럽%02d", i));
        }

        assertThat(searchService.search(viewer, "북클럽")).hasSize(20);
    }
}
