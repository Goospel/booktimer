package com.booktimer.session;

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
 * 하루치 공부 일정 판정 — <b>사용자가 직접 남긴</b> 「지켰다 / 못 지켰다」 한 칸.
 *
 * <p>3상태 중 셋째(무기록)는 <b>행이 없는 것</b>이다 — 컬럼을 nullable로 두는 대신 부재로 표현하면
 * 「아직 안 정한 날」이 원장에 아무 흔적도 남기지 않는다(달력이 희소해지는 것도 여기서 나온다).
 *
 * <p>{@code UNIQUE(user_id, check_date)}는 엔티티가 아니라 <b>마이그레이션(V80)에만</b> 있다 —
 * 이 레포는 DB를 제약의 단일 출처로 둔다({@code uk_users_login_id} 선례).
 */
@Entity
@Table(name = "study_daily_check")
public class StudyDailyCheck extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 판정 주체 (N:1). FK(user_id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** 판정 대상 날짜 — <b>유저 타임존의 달력 날짜</b>다(절대 시점이 아니다). */
    @Column(nullable = false)
    private LocalDate checkDate;

    /** 그날 일정을 지켰는가. */
    @Column(nullable = false)
    private boolean kept;

    protected StudyDailyCheck() {
        // JPA
    }

    private StudyDailyCheck(User user, LocalDate checkDate, boolean kept) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (checkDate == null) {
            throw new IllegalArgumentException("checkDate must not be null");
        }
        this.user = user;
        this.checkDate = checkDate;
        this.kept = kept;
    }

    public static StudyDailyCheck of(User user, LocalDate checkDate, boolean kept) {
        return new StudyDailyCheck(user, checkDate, kept);
    }

    /** 판정을 뒤집는다 — 같은 날에 행을 하나 더 만들지 않기 위한 자리다(UNIQUE 위반 방지). */
    public void mark(boolean kept) {
        this.kept = kept;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public LocalDate getCheckDate() {
        return checkDate;
    }

    public boolean isKept() {
        return kept;
    }
}
