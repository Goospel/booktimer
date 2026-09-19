package com.booktimer.chat;

import com.booktimer.block.BlockRepository;
import com.booktimer.block.Block;
import com.booktimer.chat.ChatEligibility.Verdict;
import com.booktimer.follow.Follow;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.Role;
import com.booktimer.user.User;
import com.booktimer.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM 자격 = 맞팔 ∧ 차단 없음 ∧ 상대 토스 연결 ∧ 둘 다 제재 아님(설계 §5-2). 실 리포지토리 + H2.
 *
 * <p>단독으로 잡는 실패: 한 방향 팔로우만 보는 것(「내가 팔로우하면 보낼 수 있다」 = 공개 프로필 기반 발송,
 * 만남 트랙 재분류 트리거), 차단을 한 방향만 보는 것, 웹 전용 상대에게 읽을 수 없는 편지함을 여는 것,
 * 제재를 한쪽만 보는 것.
 */
@SpringBootTest
@Transactional
class ChatEligibilityTest {

    @Autowired ChatEligibility eligibility;
    @Autowired UserRepository userRepository;
    @Autowired FollowRepository followRepository;
    @Autowired BlockRepository blockRepository;
    @Autowired Clock clock;

    private User toss(String name) {
        User u = User.of(name + "@elig.test", "$2a$10$abcdefghijklmnopqrstuv", name, "Asia/Seoul", Role.USER);
        u.linkTossUserKey("uk-" + name);
        return userRepository.save(u);
    }

    private void mutual(User a, User b) {
        followRepository.save(Follow.of(a, b));
        followRepository.save(Follow.of(b, a));
    }

    @Test
    void oneWayFollowIsNotMutual() {
        User me = toss("oneway-me");
        User other = toss("oneway-other");
        followRepository.save(Follow.of(me, other));

        assertThat(eligibility.check(me, other)).isEqualTo(Verdict.NOT_MUTUAL);
        assertThat(eligibility.check(other, me)).isEqualTo(Verdict.NOT_MUTUAL);
    }

    @Test
    void mutualFollowIsOk() {
        User me = toss("mutual-me");
        User other = toss("mutual-other");
        mutual(me, other);

        assertThat(eligibility.check(me, other)).isEqualTo(Verdict.OK);
        assertThat(eligibility.check(other, me)).isEqualTo(Verdict.OK);
    }

    @Test
    void blockInEitherDirectionWins() {
        User me = toss("blk-me");
        User other = toss("blk-other");
        mutual(me, other); // BlockService는 팔로우를 먼저 지우지만, 리포지토리로 바로 걸어 게이트 자체를 본다
        blockRepository.save(Block.of(other, me));

        assertThat(eligibility.check(me, other)).isEqualTo(Verdict.BLOCKED);
        assertThat(eligibility.check(other, me)).isEqualTo(Verdict.BLOCKED);
    }

    @Test
    void webOnlyPartnerIsUnreachable() {
        User me = toss("web-me");
        User webOnly = userRepository.save(
                User.of("webonly@elig.test", "$2a$10$abcdefghijklmnopqrstuv", "웹", "Asia/Seoul", Role.USER));
        mutual(me, webOnly);

        assertThat(eligibility.check(me, webOnly)).isEqualTo(Verdict.UNREACHABLE);
    }

    /** 대칭(PR-1 리뷰 사소 2) — 웹 전용인 내가 보내면 상대 답장이 읽을 수 없는 편지함에 쌓인다. */
    @Test
    void webOnlySenderIsUnreachableToo() {
        User webMe = userRepository.save(
                User.of("webme@elig.test", "$2a$10$abcdefghijklmnopqrstuv", "웹나", "Asia/Seoul", Role.USER));
        User other = toss("webme-other");
        mutual(webMe, other);

        assertThat(eligibility.check(webMe, other)).isEqualTo(Verdict.UNREACHABLE);
    }

    @Test
    void sanctionOnEitherSideRestricts() {
        User me = toss("sanc-me");
        User other = toss("sanc-other");
        mutual(me, other);
        other.restrictChatUntil(clock.instant().plus(Duration.ofDays(7)));

        assertThat(eligibility.check(me, other)).isEqualTo(Verdict.RESTRICTED);
        assertThat(eligibility.check(other, me)).isEqualTo(Verdict.RESTRICTED);
    }

    @Test
    void expiredRestrictionNoLongerRestricts() {
        User me = toss("exp-me");
        User other = toss("exp-other");
        mutual(me, other);
        other.restrictChatUntil(clock.instant().minusSeconds(1));

        assertThat(eligibility.check(me, other)).isEqualTo(Verdict.OK);
    }

    @Test
    void permanentBanRestricts() {
        User me = toss("ban-me");
        User other = toss("ban-other");
        mutual(me, other);
        me.banChat(clock.instant());

        assertThat(eligibility.check(me, other)).isEqualTo(Verdict.RESTRICTED);
    }

    @Test
    void selfIsNeverOk() {
        User me = toss("self-me");

        assertThat(eligibility.check(me, me)).isNotEqualTo(Verdict.OK);
    }
}
