package com.booktimer.study;

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

import java.time.LocalDate;

/**
 * 공부 일정 한 줄 — 「그날 이 과목으로 이걸 한다」.
 *
 * <p>{@code StudyDailyCheck}(V80)와 <b>다른 축</b>이다: 저쪽은 사후 판정 한 칸(하루 하나), 이쪽은 사전
 * 계획(하루 여럿). 그래서 UNIQUE가 없다.
 *
 * <p>{@link #subject}는 <b>제목 스냅샷</b>이다 — 책을 서재에서 지우면 {@link #book}이 풀리는데
 * ({@code StudyPlanItemRepository.unlinkBook}), 그때 「무슨 과목이었나」가 남지 않으면 화면이 빈 줄이 된다.
 * 자유 제목(서재에 없는 과목)으로 짜는 일정도 정당한 사용이라 {@code book}은 nullable이다.
 */
@Entity
@Table(name = "study_plan_item")
public class StudyPlanItem extends BaseTimeEntity {

    /** 과목·책 제목 상한 — {@code study_book.title}과 같은 300자(스냅샷이라 원본보다 짧을 이유가 없다). */
    public static final int SUBJECT_MAX = 300;

    /** 하루 할 일 한 줄의 상한. 「한 줄」이라는 형식이 곧 제약이다(문단을 담는 자리가 아니다). */
    public static final int TASK_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유 사용자 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 일정 날짜 — <b>유저 타임존의 달력 날짜</b>다(절대 시점이 아니다). */
    @Column(nullable = false)
    private LocalDate planDate;

    /** 대상 공부 책 — 자유 제목이거나 책이 삭제되면 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private StudyBook book;

    @Column(nullable = false, length = SUBJECT_MAX)
    private String subject;

    @Column(nullable = false, length = TASK_MAX)
    private String task;

    protected StudyPlanItem() {
        // JPA
    }

    private StudyPlanItem(User user, LocalDate planDate, StudyBook book, String subject, String task) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (planDate == null) {
            throw new IllegalArgumentException("날짜가 없어요");
        }
        this.user = user;
        this.planDate = planDate;
        this.book = book;
        this.subject = requireText(subject, SUBJECT_MAX, "과목");
        this.task = requireText(task, TASK_MAX, "할 일");
    }

    /**
     * 일정 한 줄을 만든다.
     *
     * @throws IllegalArgumentException 과목·할 일이 비었거나 길이를 넘는 경우 — 문구가 그대로 400 본문이 된다
     */
    public static StudyPlanItem of(User user, LocalDate planDate, StudyBook book, String subject, String task) {
        return new StudyPlanItem(user, planDate, book, subject, task);
    }

    /** 책 삭제 시 참조를 푼다 — 벌크 갱신({@code unlinkBook})과 같은 규칙의 엔티티 쪽 문. */
    public void unlinkBook() {
        this.book = null;
    }

    private static String requireText(String value, int max, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "을 입력해 주세요");
        }
        String trimmed = value.strip();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(label + "은 " + max + "자까지 쓸 수 있어요");
        }
        return trimmed;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public StudyBook getBook() {
        return book;
    }

    public String getSubject() {
        return subject;
    }

    public String getTask() {
        return task;
    }
}
