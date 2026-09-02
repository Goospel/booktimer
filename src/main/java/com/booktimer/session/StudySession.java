package com.booktimer.session;

import com.booktimer.book.StudyBook;
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
 * 한 번의 <b>공부</b> 측정 기록. User와 N:1.
 *
 * <p>{@link ReadingSession}과 불변식은 같지만(시작 → 종료 시 {@code durationSeconds} 계산) 테이블이
 * 다르다 — 그것이 이 기능의 요구 그 자체다. 공부 시간은 잔디·부채·기록·홈피드·책 통계 어디에도 섞이면
 * 안 되는데, 독서 집계 쿼리가 이 테이블을 <b>아예 모르므로</b> 섞일 경로가 구조적으로 없다.
 *
 * <p>독서에 있는 {@code manualEntry}는 없다(수동 기록은 범위 밖). {@code book}은 <b>공부 서재</b>의
 * 책({@link StudyBook})을 가리킨다 — 독서 책장({@code book} 테이블)이 아니라 별도 원장이라, 두 서재가
 * 섞이지 않는 격리가 여기서도 테이블 경계로 유지된다.
 *
 * <p><b>한 행은 유저 타임존 하루 안에 있다</b>(신규 저장분 한정). 종료 시각을 확정하는 유스케이스가
 * 자정 경계로 구간을 잘라 조각마다 한 행씩 저장하기 때문이다({@code StudySessionService}) — 날짜 귀속이
 * {@code startedAt}의 날짜라, 자정을 걸친 공부를 한 행으로 두면 통째로 시작일에 잡힌다. 이 엔티티는
 * 그 규칙을 <b>모른다</b>: 조각도 그냥 완료 세션 하나이고 불변식은 조각마다 그대로 성립한다. 분할 도입
 * 전에 저장된 레거시 행은 여전히 자정을 걸칠 수 있다(소급 재분할 없음). <b>조각들은 같은 책을 든다</b> —
 * 분할은 시간의 문제라 라벨은 조각마다 그대로 이어진다({@code StudySessionService.endSplitAndSave}).
 */
@Entity
@Table(name = "study_session")
public class StudySession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 측정 주체 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

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
     * 무슨 공부 책으로 쟀는지 (N:1, nullable). FK(book_id → study_book).
     *
     * <p>null이 정당한 사용이다 — 책 없이 재는 것이 막히면 안 되고, 책을 지워도 시간은 남는다
     * ({@code StudyBookService.delete}가 {@code unlinkBook}으로 이 컬럼을 푼다).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private StudyBook book;

    protected StudySession() {
        // JPA
    }

    private StudySession(User user, Instant startedAt, StudyBook book) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        this.user = user;
        this.startedAt = startedAt;
        this.endedAt = null;
        this.durationSeconds = 0L;
        this.book = book;
    }

    /**
     * 새 공부 측정 세션을 시작한다 — <b>책 없이</b>.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 시작 시각(필수)
     * @throws IllegalArgumentException user/startedAt 이 null 인 경우
     */
    public static StudySession start(User user, Instant startedAt) {
        return start(user, startedAt, null);
    }

    /**
     * 새 공부 측정 세션을 시작한다 — 대상 책을 함께 정한다.
     *
     * @param user      측정 주체(필수)
     * @param startedAt 시작 시각(필수)
     * @param book      대상 공부 책(null = 책 없이 — 시작을 책 선택으로 가로막지 않는다)
     * @throws IllegalArgumentException user/startedAt 이 null 인 경우
     */
    public static StudySession start(User user, Instant startedAt, StudyBook book) {
        return new StudySession(user, startedAt, book);
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
     * 책 없이 잰 <b>종료된</b> 세션에 나중에 책을 붙인다 — 종료 후 태깅("무슨 책을 공부하셨나요?").
     *
     * <p>독서 {@code ReadingSession#tagBook}에 <b>진행 중 거부</b>를 더했다: 재는 도중에 대상을 정하는
     * 것은 {@link #changeBook}의 문이라, 두 문이 같은 상태를 서로 다른 규칙으로 건드리지 않게 한다.
     *
     * @param book 연결할 책(필수)
     * @throws IllegalArgumentException book 이 null 인 경우(「책 없이」로 되돌리는 문은 changeBook)
     * @throws IllegalStateException    진행 중 세션이거나, 이미 책이 지정된 경우(재태깅 금지)
     */
    public void tagBook(StudyBook book) {
        if (book == null) {
            throw new IllegalArgumentException("book must not be null");
        }
        if (endedAt == null) {
            throw new IllegalStateException("cannot tag an active session — use changeBook");
        }
        if (this.book != null) {
            throw new IllegalStateException("session already has a book");
        }
        this.book = book;
    }

    /**
     * <b>진행 중</b> 세션의 측정 대상을 바꾼다 — 세션은 멈추지 않으므로 지금까지 잰 시간이 통째로
     * 새 책에 붙는다(세션이 시간의 원장이고 book은 그 원장의 라벨이다).
     *
     * @param book 새 대상(null = 「책 없이」로 되돌리기)
     * @throws IllegalStateException 이미 종료된 세션인 경우(끝난 기록의 대상을 바꾸는 문이 아니다)
     */
    public void changeBook(StudyBook book) {
        if (this.endedAt != null) {
            throw new IllegalStateException("cannot change book of an ended session");
        }
        this.book = book;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    /** 대상 공부 책(없으면 null). */
    public StudyBook getBook() {
        return book;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
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
