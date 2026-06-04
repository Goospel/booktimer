package com.booktimer.report;

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

/**
 * 신고 관계 — {@code reporter}가 {@code reported}를 신고한다 (SNS 5단계, sns-design §7.5·§9).
 *
 * <p>신고는 <b>저장만</b> 한다 — 관리자 검토 화면은 추후(별도 관리자 대시보드 계획). {@code (reporter, reported)}
 * 유니크로 <b>쌍당 1건</b>(중복 신고 스팸 방지), 자기 신고는 생성 시 거부한다. block 기능과 같은 구조.
 */
@Entity
@Table(name = "report", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report", columnNames = {"reporter_id", "reported_id"})
})
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 신고하는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    /** 신고당하는 사람. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_id")
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    /** 신고자가 적은 부가 설명(선택). */
    @Column(length = 500)
    private String detail;

    protected Report() {
        // JPA
    }

    private Report(User reporter, User reported, ReportReason reason, String detail) {
        this.reporter = reporter;
        this.reported = reported;
        this.reason = reason;
        this.detail = detail;
    }

    /**
     * 신고를 만든다. 자기 자신은 신고할 수 없다(도메인 규칙).
     *
     * @throws IllegalArgumentException reporter/reported/reason이 null이거나 같은 사용자인 경우
     */
    public static Report of(User reporter, User reported, ReportReason reason, String detail) {
        if (reporter == null || reported == null || reason == null) {
            throw new IllegalArgumentException("reporter/reported/reason must not be null");
        }
        if (isSameUser(reporter, reported)) {
            throw new IllegalArgumentException("cannot report self");
        }
        return new Report(reporter, reported, reason, detail);
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

    public User getReporter() {
        return reporter;
    }

    public User getReported() {
        return reported;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }
}
