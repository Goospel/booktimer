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
 * <p>측정은 "어떤 책"을 읽었는지({@link #book})에 연결된다 — 책별 누적 시간 집계의 토대.
 * <b>새 측정은 책이 필수</b>이며 그 강제는 유스케이스 경계({@code ReadingSessionService}·컨트롤러)가 한다.
 * 다만 과거엔 책 없는 측정이 가능했어서 그 레거시 행을 읽어들이려면 컬럼·필드는 nullable로 둔다
 * — 즉 {@code book == null}은 "신규 생성 금지"이되 "레거시 표현은 허용"을 뜻한다.
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
     * 책 없이 측정 세션을 만든다 — <b>레거시/테스트 표현 전용</b>이다.
     * 운영 생성 경로(서비스)는 책을 필수로 요구하므로 이 팩토리로 만든 세션은 신규 측정 흐름에선 나오지 않는다.
     * 과거 데이터(책 미지정 세션) 재현이나, 책과 무관한 타이머 계산 단위 테스트에만 쓴다.
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
