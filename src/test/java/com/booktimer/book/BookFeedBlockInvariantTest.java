package com.booktimer.book;

import com.booktimer.block.BlockService;
import com.booktimer.follow.FollowService;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 홈 소식 피드의 차단 불변식 행동 회귀 (실제 빈 + H2) — {@code StoryBlockInvariantTest} 미러.
 *
 * <p>{@link BookRepository#feedFinished}·{@link BookRepository#feedStarted}는 "팔로우 존재 → 차단 없음"
 * write-시점 불변식(차단 시 팔로우 양방향 해제)에 기대어 차단 필터를 쿼리에 두지 않는다. 이 불변식이
 * 깨지면(차단이 팔로우를 안 끊게 리팩터되면) 차단당한 사람에게 독서 활동이 계속 노출되는 프라이버시
 * 사고가 된다 — 그래서 행동으로 고정한다.
 */
@SpringBootTest
@Transactional
class BookFeedBlockInvariantTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowService followService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private Clock clock;

    private User user(String email, String loginId) {
        User u = User.of(email, "$2a$10$abcdefghijklmnopqrstuv", "책벌레", "Asia/Seoul", Role.USER);
        u.assignLoginId(loginId);
        return userRepository.save(u);
    }

    private Instant cutoff() {
        return clock.instant().minus(Duration.ofDays(14));
    }

    /** 팔로우한 사람의 PUBLIC 책 — 시작·완독 두 이벤트를 다 만든다. */
    private void publicReadBook(User owner) {
        Book b = Book.register(owner, "차단 전 책", null, null, null, null, null, BookStatus.WANT_TO_READ);
        b.changeVisibility(BookVisibility.PUBLIC);
        b.changeStatus(BookStatus.READING, clock.instant().minus(Duration.ofHours(2)));
        b.changeStatus(BookStatus.FINISHED, clock.instant().minus(Duration.ofHours(1)));
        bookRepository.save(b);
    }

    @Test
    @DisplayName("책 주인이 열람자를 차단하면 팔로우가 끊겨 소식 피드에서 사라진다")
    void ownerBlockingViewer_removesEventsFromFeed() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User owner = user("owner@booktimer.com", "owner");
        followService.follow(viewer, owner);
        publicReadBook(owner);
        assertThat(bookRepository.feedFinished(viewer, cutoff())).hasSize(1);
        assertThat(bookRepository.feedStarted(viewer, cutoff())).hasSize(1);

        blockService.block(owner, viewer);

        assertThat(bookRepository.feedFinished(viewer, cutoff())).isEmpty();
        assertThat(bookRepository.feedStarted(viewer, cutoff())).isEmpty();
    }

    @Test
    @DisplayName("열람자가 책 주인을 차단해도 팔로우가 끊겨 소식 피드에서 사라진다")
    void viewerBlockingOwner_removesEventsFromFeed() {
        User viewer = user("viewer@booktimer.com", "viewer");
        User owner = user("owner@booktimer.com", "owner");
        followService.follow(viewer, owner);
        publicReadBook(owner);
        assertThat(bookRepository.feedFinished(viewer, cutoff())).hasSize(1);

        blockService.block(viewer, owner);

        assertThat(bookRepository.feedFinished(viewer, cutoff())).isEmpty();
        assertThat(bookRepository.feedStarted(viewer, cutoff())).isEmpty();
    }
}
