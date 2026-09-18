package com.booktimer.chat;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 두 사람 사이의 대화방 — 쌍당 1개, {@code userA.id < userB.id}로 정규화(V95).
 *
 * <p>저장되는 상태는 {@link Status#CLOSED}(차단)뿐이다. 언팔·제재로 인한 「잠김」은 {@link ChatEligibility}가
 * 요청마다 파생한다. 참여자별 몫(숨김·마지막 읽음·마지막 푸시)은 a/b 컬럼 쌍이고, 어느 쪽인지는
 * {@link #isA(User)}가 id로 가른다.
 */
@Entity
@Table(name = "chat_room", uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_room_pair", columnNames = {"user_a_id", "user_b_id"})
})
public class ChatRoom extends BaseTimeEntity {

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_a_id")
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_b_id")
    private User userB;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.OPEN;

    @Column(name = "a_hidden", nullable = false)
    private boolean hiddenA;

    @Column(name = "b_hidden", nullable = false)
    private boolean hiddenB;

    @Column(name = "a_last_read_id", nullable = false)
    private long lastReadIdA;

    @Column(name = "b_last_read_id", nullable = false)
    private long lastReadIdB;

    @Column(name = "a_last_push_at")
    private Instant lastPushAtA;

    @Column(name = "b_last_push_at")
    private Instant lastPushAtB;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected ChatRoom() {
        // JPA
    }

    private ChatRoom(User a, User b) {
        this.userA = a;
        this.userB = b;
    }

    /** 두 사람의 방 — 인자 순서와 무관하게 id가 작은 쪽이 a다. */
    public static ChatRoom of(User x, User y) {
        if (x == null || y == null || x.getId() == null || y.getId() == null) {
            throw new IllegalArgumentException("persisted users required");
        }
        if (x.getId().equals(y.getId())) {
            throw new IllegalArgumentException("cannot chat with self");
        }
        return x.getId() < y.getId() ? new ChatRoom(x, y) : new ChatRoom(y, x);
    }

    public boolean has(User u) {
        return u.getId().equals(userA.getId()) || u.getId().equals(userB.getId());
    }

    public User partnerOf(User me) {
        return isA(me) ? userB : userA;
    }

    public void hide(User me) {
        if (isA(me)) {
            hiddenA = true;
        } else {
            hiddenB = true;
        }
    }

    public void unhideAll() {
        hiddenA = false;
        hiddenB = false;
    }

    public boolean isHiddenFor(User me) {
        return isA(me) ? hiddenA : hiddenB;
    }

    /** 읽음은 뒤로 가지 않는다. 앞지르기 상한은 호출자(서비스)가 방의 마지막 메시지 id로 자른다. */
    public void markRead(User me, long messageId) {
        if (isA(me)) {
            lastReadIdA = Math.max(lastReadIdA, messageId);
        } else {
            lastReadIdB = Math.max(lastReadIdB, messageId);
        }
    }

    public long lastReadIdOf(User u) {
        return isA(u) ? lastReadIdA : lastReadIdB;
    }

    public Instant lastPushAtOf(User u) {
        return isA(u) ? lastPushAtA : lastPushAtB;
    }

    public void markPushed(User recipient, Instant at) {
        if (isA(recipient)) {
            lastPushAtA = at;
        } else {
            lastPushAtB = at;
        }
    }

    public void close(Instant now) {
        status = Status.CLOSED;
        closedAt = now;
    }

    public void reopen() {
        status = Status.OPEN;
        closedAt = null;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    private boolean isA(User u) {
        return u.getId().equals(userA.getId());
    }

    public Long getId() {
        return id;
    }

    public User getUserA() {
        return userA;
    }

    public User getUserB() {
        return userB;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
