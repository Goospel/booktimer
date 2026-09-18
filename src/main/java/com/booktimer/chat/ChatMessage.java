package com.booktimer.chat;

import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 대화 메시지 한 건 — 불변(수정·삭제 기능 없음, 증거 보존). 본문은 {@link EncryptedTextConverter}로 암호화 저장된다.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    /** 평문 상한. 암호문(UTF-8 최대 3바이트/자 + 28)이 varbinary(4096)에 들어가는 값이다. */
    public static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Convert(converter = EncryptedTextConverter.class)
    @Column(nullable = false, length = 4096)
    private String body;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // JPA
    }

    private ChatMessage(ChatRoom room, User sender, String body, boolean flagged, Instant createdAt) {
        this.room = room;
        this.sender = sender;
        this.body = body;
        this.flagged = flagged;
        this.createdAt = createdAt;
    }

    /**
     * @throws IllegalArgumentException 본문이 비었거나 {@value #MAX_LENGTH}자를 넘거나, 보낸 사람이 방 멤버가 아닐 때
     */
    public static ChatMessage of(ChatRoom room, User sender, String body, boolean flagged, Instant now) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        if (body.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("메시지는 " + MAX_LENGTH + "자까지 보낼 수 있어요.");
        }
        if (!room.has(sender)) {
            throw new IllegalArgumentException("sender is not a room member");
        }
        return new ChatMessage(room, sender, body, flagged, now);
    }

    public Long getId() {
        return id;
    }

    public ChatRoom getRoom() {
        return room;
    }

    public User getSender() {
        return sender;
    }

    public String getBody() {
        return body;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
