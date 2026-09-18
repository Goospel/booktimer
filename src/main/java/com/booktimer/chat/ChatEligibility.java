package com.booktimer.chat;

import com.booktimer.block.BlockRepository;
import com.booktimer.follow.FollowRepository;
import com.booktimer.user.User;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * DM 자격 판정 — <b>맞팔 ∧ 차단 없음 ∧ 상대 토스 연결 ∧ 둘 다 제재 아님</b>(설계 §4-1·§5-2).
 *
 * <p>이 판정은 저장하지 않는다. 방 열기·발송·조회가 <b>매번</b> 부른다 — 「잠김」은 요청 시점의 follow·block·users
 * 에서 파생되므로, 언팔하면 그 순간 잠기고 다시 맞팔하면 상태 전이 코드 없이 저절로 풀린다. 캐시를 두면 그
 * 성질이 깨진다(설계 §7-3).
 *
 * <p>맞팔 게이트가 채팅 서비스 트랙의 근거다 — 한 방향 팔로우로 열리면 「공개 프로필 기반 메시지 발송」이 돼
 * 만남·소개팅 트랙으로 재분류된다(설계 §3).
 */
@Component
public class ChatEligibility {

    public enum Verdict {
        OK, NOT_MUTUAL, BLOCKED, UNREACHABLE, RESTRICTED
    }

    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final Clock clock;

    public ChatEligibility(FollowRepository followRepository, BlockRepository blockRepository, Clock clock) {
        this.followRepository = followRepository;
        this.blockRepository = blockRepository;
        this.clock = clock;
    }

    public Verdict check(User me, User other) {
        if (me.getId().equals(other.getId())) {
            return Verdict.NOT_MUTUAL; // 자기 자신과의 대화는 없다(자기 팔로우도 불가)
        }
        if (blockRepository.existsBetween(me, other)) {
            return Verdict.BLOCKED;
        }
        if (!followRepository.existsByFollowerAndFollowee(me, other)
                || !followRepository.existsByFollowerAndFollowee(other, me)) {
            return Verdict.NOT_MUTUAL;
        }
        if (other.getTossUserKey() == null) {
            return Verdict.UNREACHABLE; // 웹 전용 상대 — 웹 UI가 없어 읽을 수 없는 편지함이 된다(§11 Q1)
        }
        if (me.isChatRestricted(clock.instant()) || other.isChatRestricted(clock.instant())) {
            return Verdict.RESTRICTED;
        }
        return Verdict.OK;
    }
}
