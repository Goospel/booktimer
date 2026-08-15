package com.booktimer.story;

import com.booktimer.block.BlockService;
import com.booktimer.follow.FollowService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 차단 불변식 행동 회귀 (실제 빈 + H2) — 피드 쿼리에 차단 필터가 없어도 안전한 근거를 못 박는다.
 *
 * <p>{@code feedOf}는 "팔로우 존재 → 차단 없음" write-시점 불변식(차단 시 팔로우 양방향 해제,
 * sns-design §7.5·§13.2)에 기대어 차단 필터를 쿼리에 두지 않는다. 이 불변식이 깨지면(차단이 팔로우를
 * 안 끊게 리팩터되면) 차단당한 사람에게 스토리가 계속 노출되는 프라이버시 사고가 된다 — 그래서 행동으로 고정.
 */
@SpringBootTest
@Transactional
class StoryBlockInvariantTest {

    private static final Pageable ALL = PageRequest.of(0, 200);

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowService followService;

    @Autowired
    private BlockService blockService;

    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    @Test
    @DisplayName("작성자가 열람자를 차단하면 팔로우가 끊겨 피드에서 여백이 사라진다")
    void authorBlockingViewer_removesStoriesFromFeed() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        followService.follow(viewer, author);
        storyRepository.save(Story.of(author, "차단 전 문장", null, null));
        assertThat(storyRepository.feedOf(viewer, ALL)).hasSize(1);

        blockService.block(author, viewer);

        assertThat(storyRepository.feedOf(viewer, ALL)).isEmpty();
    }

    @Test
    @DisplayName("열람자가 작성자를 차단해도 팔로우가 끊겨 피드에서 여백이 사라진다")
    void viewerBlockingAuthor_removesStoriesFromFeed() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User author = user("author@booktimer.com", "author");
        followService.follow(viewer, author);
        storyRepository.save(Story.of(author, "차단 전 문장", null, null));
        assertThat(storyRepository.feedOf(viewer, ALL)).hasSize(1);

        blockService.block(viewer, author);

        assertThat(storyRepository.feedOf(viewer, ALL)).isEmpty();
    }
}
