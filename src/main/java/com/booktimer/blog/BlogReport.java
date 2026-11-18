package com.booktimer.blog;

import com.booktimer.common.BaseTimeEntity;
import com.booktimer.report.ReportReason;
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
 * 블로그 신고 한 건(B2b · V109 · J50 — 사람 신고 {@code report}와 다른 표). 대상은 글 · 댓글 둘(J94) — 문장 · 박스 신고는 글 대상에
 * detail 접두({@code sid:} · {@code quote:})로. 대상 · 대상 글쓴이는 논리 참조(FK 없음 — 대상이 지워져도 신고자의 기록으로 남는다).
 * 유일 제약은 엔티티에도 같은 이름으로 둔다 — 메인 스위트는 엔티티 스키마라 마이그레이션에만 있으면 멱등 동시성 시험이 공허하다.
 */
@Entity
@Table(name = "blog_report", uniqueConstraints = @UniqueConstraint(name = "uq_blog_report_target",
        columnNames = {"reporter_id", "target_type", "target_id"}))
public class BlogReport extends BaseTimeEntity {

    public enum Target { POST, COMMENT }

    public enum Status { OPEN, RESOLVED }

    public static final int DETAIL_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private Target targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** 신고할 때의 대상 글쓴이 — 대상이 지워져도 글쓰기 막기를 걸 수 있게(§4.4). 그 사람이 탈퇴하면 null. */
    @Column(name = "target_user_id")
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = DETAIL_MAX)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    /** {@code HIDDEN} · {@code REMOVED} · {@code NONE} — 처리할 때. */
    @Column(length = 16)
    private String result;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected BlogReport() {
    }

    static BlogReport of(User reporter, Target type, long targetId, Long targetUserId, ReportReason reason, String detail) {
        BlogReport r = new BlogReport();
        r.reporter = reporter;
        r.targetType = type;
        r.targetId = targetId;
        r.targetUserId = targetUserId;
        r.reason = reason;
        r.detail = detail;
        r.status = Status.OPEN;
        return r;
    }

    void resolve(String result, Instant now) {
        this.status = Status.RESOLVED;
        this.result = result;
        this.resolvedAt = now;
    }

    public Long getId() {
        return id;
    }

    public User getReporter() {
        return reporter;
    }

    public Target getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
