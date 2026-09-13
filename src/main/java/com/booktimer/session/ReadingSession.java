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
 * <p><b>한 행은 유저 타임존 하루 안에 있다</b>(신규 저장분 한정). 종료 시각을 확정하는 유스케이스가
 * 자정 경계로 구간을 잘라 조각마다 한 행씩 저장하기 때문이다({@code ReadingSessionService.splitByMidnight}) —
 * 날짜 귀속이 {@code startedAt}의 날짜라, 자정을 걸친 독서를 한 행으로 두면 통째로 시작일에 잡힌다.
 * 이 엔티티는 그 규칙을 <b>모른다</b>: 조각도 그냥 완료 세션 하나이고 불변식
 * ({@code durationSeconds = endedAt - startedAt})은 조각마다 그대로 성립한다. 분할 도입 전에 저장된
 * 레거시 행은 여전히 자정을 걸칠 수 있다(소급 재분할 없음).
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
     * <b>진행 중</b> 세션의 측정 대상을 바꾼다 — 시작 시 고른 책을 재는 도중 교체하는 경로.
     * 위 {@link #tagBook} 의 javadoc이 "다른 관심사"라고 선을 그어 둔 바로 그 자리다.
     *
     * <p>관심사가 다르니 <b>가드도 반대다</b>: tagBook은 끝난 세션에 책을 <i>붙이는</i> 1회성 문이라
     * 재태깅을 막고 null을 거부하지만, 이쪽은 <i>재는 동안</i> 라벨을 고쳐 다는 문이라 여러 번 허용하고
     * null(=「책 없이」로 되돌리기)도 받는다. 대신 <b>종료된 세션은 거부</b>한다 — 끝난 기록의 대상을
     * 바꾸는 문이 아니고, 그쪽은 tagBook의 1회 규칙이 지킨다.
     *
     * <p>세션은 멈추지 않는다. 세션이 시간의 원장이고 book은 그 원장의 <b>라벨</b>이라, 라벨만 갈면
     * 지금까지 잰 시간이 통째로 새 책에 붙는다.
     *
     * @param book 새 대상(null = 책 없이)
     * @throws IllegalStateException 이미 종료된 세션인 경우
     */
    public void changeBook(Book book) {
        if (this.endedAt != null) {
            throw new IllegalStateException("cannot change book of an ended session");
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
