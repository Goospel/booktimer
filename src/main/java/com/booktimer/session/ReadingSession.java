package com.booktimer.session;

import com.booktimer.book.Book;
import com.booktimer.common.BaseTimeEntity;
import com.booktimer.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * 한 번의 독서 측정 기록. User와 N:1.
 *
 * <p>{@link #start(User, Instant)}로 시작(진행 중)하고 {@link #end(Instant)}로 종료한다.
 * 종료 시 {@code durationSeconds = endedAt - startedAt}을 계산하며, 이 값이 추후
 * ReadingTimer 의 누적 잔여(remainingSeconds) 차감에 쓰인다(다음 증분).
 *
 * <p>측정이 "어떤 책"을 읽은 것인지 선택적으로 연결한다({@link #book}, nullable) — 책별 누적 시간 집계에 쓰인다.
 * 책 미지정 측정(그냥 읽기)도 허용하므로 null을 허용한다.
 */
@Entity
@Table(name = "reading_session")
public class ReadingSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 측정 주체 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 측정 대상 책 (N:1, 선택). null이면 책 미지정 측정. FK(book_id). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    /** 측정 시작 시각(절대 시점). */
    @Column(nullable = false)
    private Instant startedAt;

    /** 측정 종료 시각. 진행 중이면 null. */
    @Column
    private Instant endedAt;

    /** 종료 시 계산된 측정 길이(초). 진행 중이면 0. */
    @Column(nullable = false)
    private long durationSeconds;

    protected ReadingSession() {
        // JPA
    }

    private ReadingSession(User user, Instant startedAt, Book book) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        this.user = user;
        this.startedAt = startedAt;
        this.book = book; // nullable — 책 미지정 측정 허용
        this.endedAt = null;
        this.durationSeconds = 0L;
    }

    /**
     * 책 미지정으로 새 측정 세션을 시작한다. 종료 전(진행 중) 상태로 생성된다.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 시작 시각(필수)
     * @throws IllegalArgumentException user/startedAt 이 null 인 경우
     */
    public static ReadingSession start(User user, Instant startedAt) {
        return new ReadingSession(user, startedAt, null);
    }

    /**
     * 특정 책을 대상으로 새 측정 세션을 시작한다.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 시작 시각(필수)
     * @param book      측정 대상 책(선택, null이면 미지정)
     */
    public static ReadingSession start(User user, Instant startedAt, Book book) {
        return new ReadingSession(user, startedAt, book);
    }

    /**
     * 세션을 종료하고 측정 길이를 계산한다.
     *
     * @param endedAt 종료 시각(필수, startedAt 이상)
     * @throws IllegalArgumentException endedAt 이 null 이거나 startedAt 보다 이른 경우
     * @throws IllegalStateException    이미 종료된 세션인 경우
     */
    public void end(Instant endedAt) {
        if (this.endedAt != null) {
            throw new IllegalStateException("session already ended at " + this.endedAt);
        }
        if (endedAt == null) {
            throw new IllegalArgumentException("endedAt must not be null");
        }
        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
        this.endedAt = endedAt;
        this.durationSeconds = Duration.between(startedAt, endedAt).toSeconds();
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    /** 측정 대상 책. 미지정이면 null. */
    public Book getBook() {
        return book;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
