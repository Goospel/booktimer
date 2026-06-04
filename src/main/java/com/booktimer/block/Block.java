package com.booktimer.block;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 차단 관계 — {@code blocker}가 {@code blocked}를 차단한다 (SNS 5단계, sns-design §7.5·§9).
 *
 * <p>저장은 단방향(blocker→blocked)이지만 <b>효과는 대칭</b>이다: 둘 사이에 차단이 한 방향이라도 있으면
 * 서로 팔로우할 수 없고 서로 프로필을 볼 수 없다(게이트는 {@code isBlockedBetween}로 양방향 검사).
 * {@code (blocker, blocked)} 유니크로 중복을 막고, 자기 차단은 생성 시 거부한다.
 */
@Entity
@Table(name = "block", uniqueConstraints = {
        @UniqueConstraint(name = "uk_block", columnNames = {"blocker_id", "blocked_id"})
})
public class Block extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 차단하는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id")
    private User blocker;

    /** 차단당하는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id")
    private User blocked;

    protected Block() {
        // JPA
    }

    private Block(User blocker, User blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }

    /**
     * 차단 관계를 만든다. 자기 자신은 차단할 수 없다(도메인 규칙).
     *
     * @throws IllegalArgumentException blocker/blocked가 null이거나 같은 사용자인 경우
     */
    public static Block of(User blocker, User blocked) {
        if (blocker == null || blocked == null) {
            throw new IllegalArgumentException("blocker/blocked must not be null");
        }
        if (isSameUser(blocker, blocked)) {
            throw new IllegalArgumentException("cannot block self");
        }
        return new Block(blocker, blocked);
    }

    private static boolean isSameUser(User a, User b) {
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }

    public Long getId() {
        return id;
    }

    public User getBlocker() {
        return blocker;
    }

    public User getBlocked() {
        return blocked;
    }
}
