package com.booktimer.follow;

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
 * 팔로우 관계 — {@code follower}가 {@code followee}를 팔로우한다(단방향, 승인 없음). (sns-design §7.3·§4)
 *
 * <p>{@code (follower, followee)} 유니크로 중복 팔로우를 막고, 자기 자신 팔로우는 생성 시 거부한다.
 * 관계만 저장하며 독서 데이터는 건드리지 않는다(N-037).
 */
@Entity
@Table(name = "follow", uniqueConstraints = {
        @UniqueConstraint(name = "uk_follow", columnNames = {"follower_id", "followee_id"})
})
public class Follow extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 팔로우하는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id")
    private User follower;

    /** 팔로우당하는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followee_id")
    private User followee;

    protected Follow() {
        // JPA
    }

    private Follow(User follower, User followee) {
        this.follower = follower;
        this.followee = followee;
    }

    /**
     * 팔로우 관계를 만든다. 자기 자신은 팔로우할 수 없다(도메인 규칙).
     *
     * @throws IllegalArgumentException follower/followee가 null이거나 같은 사용자인 경우
     */
    public static Follow of(User follower, User followee) {
        if (follower == null || followee == null) {
            throw new IllegalArgumentException("follower/followee must not be null");
        }
        if (isSameUser(follower, followee)) {
            throw new IllegalArgumentException("cannot follow self");
        }
        return new Follow(follower, followee);
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

    public User getFollower() {
        return follower;
    }

    public User getFollowee() {
        return followee;
    }
}
