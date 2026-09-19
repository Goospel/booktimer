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
 * <p>접수된 신고는 관리자 신고함({@code /admin/reports})에서 운영자가 검토·후속 처리한다. {@code (reporter, reported)}
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

    /** 대화방 신고의 방(V96, FK chat_room). 방 엔티티가 아니라 id로 둔다 — 신고 목록이 방을 로딩할 이유가 없다. */
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "chat_last_message_id")
    private Long chatLastMessageId;

    @Column(name = "reported_at")
    private java.time.Instant reportedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(length = 16)
    private String resolution;

    @Column(name = "legal_hold", nullable = false)
    private boolean legalHold;

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

    /** 대화방에서 한 신고면 그 방 id(V96). 운영자 대본 열람의 유일한 열쇠다 — 없으면 대본을 못 연다. */
    public Long getChatRoomId() {
        return chatRoomId;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public boolean isLegalHold() {
        return legalHold;
    }

    /** 처리 때 고른 조치(NONE·WARN·SUSPEND_7D·BAN). 미처리면 {@code null}. */
    public String getResolution() {
        return resolution;
    }

    /**
     * 대화방과 대본의 끝(신고 시점 그 방의 마지막 메시지 id)을 붙인다. 쌍당 1건이라 같은 두 사람의 신고는 늘 같은
     * 방을 가리킨다(방도 쌍당 1개). 새로 접수될 때(첫 신고·처리 뒤 재신고)만 부른다 — 미처리 중복 신고는 끝을 안 민다.
     */
    public void attachChatRoom(Long roomId, Long lastMessageId) {
        this.chatRoomId = roomId;
        this.chatLastMessageId = lastMessageId;
    }

    /** 운영자 대본의 끝 — 신고 시점 그 방의 마지막 메시지 id. 이 id 뒤의 대화는 보이지 않는다. */
    public Long getChatLastMessageId() {
        return chatLastMessageId;
    }

    /** 마지막 접수 시각. 재신고면 그 시각이고, 대화방 신고가 아니었으면(null) 첫 신고 시각이다. */
    public java.time.Instant getReportedAt() {
        return reportedAt != null ? reportedAt : getCreatedAt();
    }

    /** 처리 끝난 신고를 다시 접수한다 — 사유·상세·접수 시각을 새 값으로(쌍당 1건이라 새 행이 없다). */
    public void resubmit(ReportReason reason, String detail, java.time.Instant at) {
        this.reason = reason;
        this.detail = detail;
        this.reportedAt = at;
        this.status = ReportStatus.OPEN;
    }

    public void markReportedAt(java.time.Instant at) {
        this.reportedAt = at;
    }

    /** 처리 완료로 닫고 조치를 기록한다. */
    public void resolve(String resolution) {
        this.status = ReportStatus.RESOLVED;
        this.resolution = resolution;
    }

    public void setLegalHold(boolean hold) {
        this.legalHold = hold;
    }

    /** 보존 삭제로 방이 사라질 때 — 처리 끝난 신고는 기록으로 남고 가리키던 방만 풀린다. */
    public void detachChatRoom() {
        this.chatRoomId = null;
    }
}
