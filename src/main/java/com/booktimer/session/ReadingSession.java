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
 * 종료 시 {@code durationSeconds = endedAt - startedAt}을 계산한다. 완료된 세션은 날짜별로
 * 합산돼 7일 윈도우 부채(목표 − 그날 읽은 양)를 <b>유도</b>하는 원장이 된다(PR #217) —
 * 옛 모델처럼 타이머 잔여를 직접 차감하지 않는다.
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

    /**
     * 사용자가 측정을 깜빡한 독서를 <b>나중에 직접 기록</b>한 세션인지(=빠뜨린 날 채우기). 실시간 측정은 false.
     * 잔디에서 "직접 채운 날"을 테두리로 구분하는 데 쓴다 — 측정값과 손으로 채운 값을 시각적으로 구별.
     */
    @Column(nullable = false)
    private boolean manualEntry;

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
        this.manualEntry = false; // 실시간 측정이 기본; 수동 기록은 manual 팩토리가 true로 둔다
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
     * 측정을 깜빡한 독서를 나중에 직접 기록한 <b>수동 입력</b> 완료 세션을 만든다(시작~종료가 이미 정해짐).
     * {@code manualEntry=true}로 표시돼 잔디에서 "직접 채운 날"로 구분된다. 부채/집계 반영은 실시간 측정과 동일.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 읽기 시작 시각(필수)
     * @param endedAt   읽기 종료 시각(필수, startedAt 이상)
     * @param book      읽은 책(선택, null이면 미지정 — 운영 생성 경로는 서비스가 책을 필수로 강제)
     */
    public static ReadingSession manual(User user, Instant startedAt, Instant endedAt, Book book) {
        ReadingSession session = new ReadingSession(user, startedAt, book);
        session.manualEntry = true;
        session.end(endedAt); // endedAt >= startedAt 검증 + durationSeconds 계산
        return session;
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

    /**
     * 책 미지정 세션에 나중에 책을 연결한다 — <b>종료 후 태깅</b>(발견 1). 책 없이 시작한 측정을
     * "무슨 책이었나요?"로 되돌아보며 책을 붙이는 경로다. 이미 책이 지정된 세션은 재태깅하지 않는다
     * (측정 시작 시 고른 책을 사후에 바꾸는 건 다른 관심사).
     *
     * @param book 연결할 책(필수)
     * @throws IllegalArgumentException book 이 null 인 경우
     * @throws IllegalStateException    이미 책이 지정된 세션인 경우(재태깅 금지)
     */
    public void tagBook(Book book) {
        if (book == null) {
            throw new IllegalArgumentException("book must not be null");
        }
        if (this.book != null) {
            throw new IllegalStateException("session already has a book");
        }
        this.book = book;
    }

    /**
     * <b>진행 중</b> 세션의 측정 대상 책을 바꾼다 — 미니앱 시작 토스트의 「바꾸기」가 지나는 문.
     *
     * <p>{@link #tagBook}과 규칙이 정반대라 한 메서드로 합칠 수 없다. 태깅은 "빈 자리를 채우는" 것이라
     * 책이 이미 있으면 거부하고 {@code null}도 거부하지만, 이쪽은 "라벨을 바꾸는" 것이라 책이 있어도 되고
     * {@code null}이 유효한 목적지다(「책 없이」로 되돌리기). 대신 <b>진행 중일 때만</b> 연다 — 끝난 기록의
     * 책을 조용히 고치는 경로를 만들지 않는다.
     *
     * <p>잰 시간은 세션이 들고 책은 라벨일 뿐이라, 교체가 {@code startedAt}·{@code durationSeconds}를
     * 건드리지 않는다(그래서 "지금까지 잰 시간이 바꾼 책에 붙는다"가 공짜로 성립한다).
     *
     * @param book 새 측정 대상({@code null}이면 「책 없이」)
     * @throws IllegalStateException 이미 종료된 세션인 경우
     */
    public void changeBook(Book book) {
        if (!isActive()) {
            throw new IllegalStateException("cannot change the book of an ended session");
        }
        this.book = book;
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

    /** 사용자가 직접 기록한 수동 입력 세션이면 true(빠뜨린 날 채우기). 실시간 측정이면 false. */
    public boolean isManualEntry() {
        return manualEntry;
    }
}
